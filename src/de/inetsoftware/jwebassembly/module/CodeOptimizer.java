/*
 * Copyright 2019 - 2020 Volker Berlin (i-net software)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.inetsoftware.jwebassembly.module;

import java.util.List;

import de.inetsoftware.jwebassembly.module.WasmInstruction.Type;
import de.inetsoftware.jwebassembly.wasm.VariableOperator;

/**
 * Optimize the code of a single method/function through using of WebAssembly features without equivalent in Java.
 * 
 * @author Volker Berlin
 */
class CodeOptimizer {

    /**
     * Optimize the code before writing.
     * 
     * @param instructions
     *            the list of instructions
     */
    void optimize( List<WasmInstruction> instructions ) {
        for( int i = instructions.size()-1; i >= 0; i-- ) {
            WasmInstruction instr = instructions.get( i );
            switch( instr.getType() ) {
                case Local:
                    // merge local.set, local.get --> local.tee 
                    if( i == 0 ) {
                        continue;
                    }
                    if(  instructions.get( i-1 ).getType() != Type.Local ) {
                        continue;
                    }
                    WasmLocalInstruction local1 = (WasmLocalInstruction)instructions.get( i-1 );
                    WasmLocalInstruction local2 = (WasmLocalInstruction)instr;
                    if( local1.getIndex() != local2.getIndex() ) {
                        continue;
                    }
                    if( local1.getOperator() == VariableOperator.set && local2.getOperator() == VariableOperator.get ) {
                        local1.setOperator( VariableOperator.tee );
                        instructions.remove( i );
                    }
                    break;
                default:
            }
        }

    }

}
