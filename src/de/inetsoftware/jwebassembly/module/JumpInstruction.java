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

import java.io.IOException;

import javax.annotation.Nonnull;

import de.inetsoftware.jwebassembly.wasm.AnyType;

/**
 * Placeholder for a jump to inspect the stack. It is like a nop operation.
 * 
 * @author Volker Berlin
 *
 */
class JumpInstruction extends WasmInstruction {

    private int jumpPos;

    private int popCount;

    /**
     * Create an instance of a nop instruction
     * 
     * @param jumpPos
     *            the position of the jump
     * @param popCount
     *            the the count of values that are removed from the stack.
     * @param javaCodePos
     *            the code position/offset in the Java method
     * @param lineNumber
     *            the line number in the Java source code
     */
    JumpInstruction( int jumpPos, int popCount, int javaCodePos, int lineNumber ) {
        super( javaCodePos, lineNumber );
        this.jumpPos = jumpPos;
        this.popCount = popCount;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    Type getType() {
        return Type.Jump;
    }

    /**
     * {@inheritDoc}
     */
    public void writeTo( @Nonnull ModuleWriter writer ) throws IOException {
        // nothing
    }

    /**
     * {@inheritDoc}
     */
    AnyType getPushValueType() {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    int getPopCount() {
        return popCount;
    }

    /**
     * Get the jump position
     * 
     * @return the position
     */
    int getJumpPosition() {
        return jumpPos;
    }
}
