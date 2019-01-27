/*
   Copyright 2018 - 2019 Volker Berlin (i-net software)

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

import de.inetsoftware.jwebassembly.wasm.AnyType;

/**
 * WasmInstruction for load and store local variables.
 * 
 * @author Volker Berlin
 *
 */
class WasmLoadStoreInstruction extends WasmLocalInstruction {

    private LocaleVariableManager localVariables;

    /**
     * Create an instance of a load/store instruction
     * 
     * @param load
     *            true: if load
     * @param idx
     *            the memory/slot idx of the variable
     * @param localVariables
     *            the manager for local variables
     * @param javaCodePos
     *            the code position/offset in the Java method
     */
    WasmLoadStoreInstruction( boolean load, @Nonnegative int idx, LocaleVariableManager localVariables, int javaCodePos ) {
        super( load, idx, javaCodePos );
        this.localVariables = localVariables;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    int getIndex() {
        return localVariables.get( super.getIndex() ); // translate slot index to position index
    }

    /**
     * {@inheritDoc}
     */
    AnyType getPushValueType() {
        return getPopCount() == 0 ? localVariables.getValueType( super.getIndex() ) : null;
    }
}
