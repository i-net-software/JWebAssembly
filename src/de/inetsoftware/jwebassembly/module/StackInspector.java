/*
   Copyright 2020 Volker Berlin (i-net software)

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.

*/
package de.inetsoftware.jwebassembly.module;

import java.util.ArrayDeque;
import java.util.List;
import java.util.NoSuchElementException;

import de.inetsoftware.jwebassembly.WasmException;
import de.inetsoftware.jwebassembly.module.WasmInstruction.Type;
import de.inetsoftware.jwebassembly.wasm.AnyType;

/**
 * Inspect the current parsed instructions to find details over specific stack information.
 *
 * @author Volker Berlin
 */
class StackInspector {

    /**
     * Inspect the instructions to find details over a specific stack position.
     * 
     * @param instructions
     *            the parsed instructions
     * @param count
     *            the count of values on the stack back. 1 means the last value. 2 means the penultimate value.
     * @param javaCodePos
     *            the current code position, important to follow jumps in the code
     * @return details of the stack position
     */
    static StackValue findInstructionThatPushValue( List<WasmInstruction> instructions, int count, int javaCodePos ) {
        // because there can be jumps (GOTO) we can analyze the stack only forward. If we iterate backward we will not see that we are in a jump.
        ArrayDeque<StackValue> stack = new ArrayDeque<>();
        int size = instructions.size();
        for( int i = 0; i < size; i++ ) {
            WasmInstruction instr = instructions.get( i );
            int popCount = instr.getPopCount();
            for( int p = 0; p < popCount; p++ ) {
                stack.pop();
            }
            AnyType pushValue = instr.getPushValueType();
            if( pushValue != null ) {
                StackValue el = new StackValue();
                el.idx = i;
                el.instr = instr;
                stack.push( el );
            }
            if( instr.getType() == Type.Jump ) {
                if( popCount == 0 ) { // GOTO, for example on the end of the THEN branch
                    JumpInstruction jump = (JumpInstruction)instr;
                    int jumpPos = jump.getJumpPosition();
                    if( jumpPos > javaCodePos ) {
                        // we need a stack position inside a branch, we can remove all outside
                        stack.clear();
                    } else if( jumpPos > instr.getCodePosition() ) {
                        while( ++i < size && jumpPos > instructions.get( i ).getCodePosition() ) {
                            //nothing
                        }
                        i--; // we are on the right position but the loop increment
                    }
                }
            }
        }

        try {
            StackValue stackValue = null;
            for( int p = 0; p < count; p++ ) {
                stackValue = stack.pop();
            }
            return stackValue;
        } catch( NoSuchElementException ex ) {
            throw new WasmException( "Push instruction not found", -1 ); // should never occur
        }
    }

    /**
     * Hold the state of the stack.
     */
    static class StackValue {
        /** the instruction index that push the stack value */
        int             idx;

        /** the instruction that push the stack value */
        WasmInstruction instr;
    }
}
