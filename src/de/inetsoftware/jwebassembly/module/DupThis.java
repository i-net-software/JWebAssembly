/*
   Copyright 2019 - 2022 Volker Berlin (i-net software)

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

import de.inetsoftware.jwebassembly.wasm.AnyType;
import de.inetsoftware.jwebassembly.wasm.VariableOperator;

/**
 * This class save a reference of THIS to a temporary variable for a later virtual caLL. The reference of THIS is used for accessing the vtable of the object.
 * 
 * @author Volker Berlin
 */
class DupThis extends WasmInstruction {

    private WasmCallIndirectInstruction virtualCall;

    private int                         tempVarSlot;

    private LocaleVariableManager       localVariables;

    /**
     * Create a instance.
     * 
     * @param virtualCall
     *            the related virtual function call.
     * @param tempVarSlot
     *            the slot of the temporary variable
     * @param localVariables
     *            the manager for local variables
     * @param javaCodePos
     *            the code position
     */
    DupThis( WasmCallIndirectInstruction virtualCall, int tempVarSlot, LocaleVariableManager localVariables, int javaCodePos ) {
        super( javaCodePos, virtualCall.getLineNumber() );
        this.virtualCall = virtualCall;
        this.tempVarSlot = tempVarSlot;
        this.localVariables = localVariables;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    Type getType() {
        return Type.DupThis;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    void writeTo( ModuleWriter writer ) throws IOException {
        if( virtualCall.isVirtual() ) {
            writer.writeLocal( VariableOperator.tee, localVariables.get( tempVarSlot, getCodePosition() ) );
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    AnyType getPushValueType() {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    int getPopCount() {
        return 0;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    AnyType[] getPopValueTypes() {
        return null;
    }
}
