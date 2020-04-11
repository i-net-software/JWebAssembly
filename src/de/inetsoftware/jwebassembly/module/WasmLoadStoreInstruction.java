/*
   Copyright 2018 - 2020 Volker Berlin (i-net software)

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

import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;

import de.inetsoftware.jwebassembly.wasm.VariableOperator;

/**
 * WasmInstruction for load and store local variables.
 * 
 * @author Volker Berlin
 *
 */
class WasmLoadStoreInstruction extends WasmLocalInstruction {

    /**
     * Create an instance of a load/store instruction
     * 
     * @param op
     *            the operation
     * @param idx
     *            the memory/slot idx of the variable
     * @param localVariables
     *            the manager for local variables
     * @param javaCodePos
     *            the code position/offset in the Java method
     * @param lineNumber
     *            the line number in the Java source code
     */
    WasmLoadStoreInstruction( VariableOperator op, @Nonnegative int idx, LocaleVariableManager localVariables, int javaCodePos, int lineNumber ) {
        super( op, idx, localVariables, javaCodePos, lineNumber );
    }

    /**
     * Create a derived instruction for the same slot
     * 
     * @param op
     *            the operation
     * @return the new instruction
     */
    @Nonnull
    WasmLoadStoreInstruction create( VariableOperator op ) {
        return new WasmLoadStoreInstruction( op, super.getIndex(), localVariables, getCodePosition(), getLineNumber() );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    int getIndex() {
        return localVariables.get( super.getIndex(), getCodePosition() ); // translate slot index to position index
    }
}
