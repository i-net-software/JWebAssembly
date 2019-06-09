/*
   Copyright 2019 Volker Berlin (i-net software)

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

import de.inetsoftware.jwebassembly.module.TypeManager.StructType;

/**
 * WasmInstruction for a function call.
 * 
 * @author Volker Berlin
 *
 */
class WasmCallIndirectInstruction extends WasmCallInstruction {

    private int                         virtualFunctionIdx = -1;

    private final StructType            type;

    private final int                   tempVar;

    private final LocaleVariableManager localVariables;

    /**
     * Create an instance of a function call instruction
     * 
     * @param name
     *            the function name that should be called
     * @param type
     *            the type with the virtual method/function
     * @param tempVar
     *            the slot of a temporary variable of type "type" to duplicate "this"
     * @param localVariables
     *            the manager for local variables to translate the Java slot of the temporary variable into wasm local
     *            position
     * @param javaCodePos
     *            the code position/offset in the Java method
     * @param lineNumber
     *            the line number in the Java source code
     */
    WasmCallIndirectInstruction( FunctionName name, @Nonnull StructType type, int tempVar, LocaleVariableManager localVariables, int javaCodePos, int lineNumber ) {
        super( name, javaCodePos, lineNumber );
        this.type = type;
        this.tempVar = tempVar;
        this.localVariables = localVariables;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    Type getType() {
        return Type.CallIndirect;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    void markAsNeeded( FunctionManager functions ) {
        super.markAsNeeded( functions );
        virtualFunctionIdx = functions.getFunctionIndex( getFunctionName() );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeTo( @Nonnull ModuleWriter writer ) throws IOException {
        if( virtualFunctionIdx < 0 || true ) {
            super.writeTo( writer );
        } else {
            int tempVarIdx = localVariables.get( tempVar, getCodePosition() );
            writer.writeVirtualFunctionCall( getFunctionName(), type, virtualFunctionIdx, tempVarIdx );
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    int getPopCount() {
        return super.getPopCount() + 1; // this -> +1
    }
}
