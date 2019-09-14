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

import de.inetsoftware.jwebassembly.javascript.JavaScriptSyntheticFunctionName;
import de.inetsoftware.jwebassembly.module.TypeManager.StructType;
import de.inetsoftware.jwebassembly.wasm.NamedStorageType;
import de.inetsoftware.jwebassembly.wasm.StructOperator;
import de.inetsoftware.jwebassembly.wasm.ValueType;
import de.inetsoftware.jwebassembly.wasm.VariableOperator;
import de.inetsoftware.jwebassembly.wasm.WasmOptions;

/**
 * WasmInstruction for a function call.
 * 
 * @author Volker Berlin
 *
 */
class WasmCallIndirectInstruction extends WasmCallInstruction {

    private int                         virtualFunctionIdx = -1;

    private final StructType            type;

    /**
     * the slot of a temporary variable of type "type" to duplicate "this"
     */
    private final int                   tempVar;

    private final LocaleVariableManager localVariables;

    private final WasmOptions           options;

    /**
     * Create an instance of a function call instruction
     * 
     * @param name
     *            the function name that should be called
     * @param localVariables
     *            the manager for local variables to translate the Java slot of the temporary variable into wasm local
     *            position
     * @param javaCodePos
     *            the code position/offset in the Java method
     * @param lineNumber
     *            the line number in the Java source code
     * @param types
     *            the type manager
     */
    WasmCallIndirectInstruction( FunctionName name, LocaleVariableManager localVariables, int javaCodePos, int lineNumber, TypeManager types, WasmOptions options ) {
        super( name, javaCodePos, lineNumber, types );
        this.type = types.valueOf( name.className );
        this.tempVar = localVariables.getTempVariable( type, javaCodePos, javaCodePos + 1 );
        this.localVariables = localVariables;
        this.options = options;
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
    void markAsNeeded( FunctionManager functions, boolean isStatic ) {
        super.markAsNeeded( functions, isStatic );
        virtualFunctionIdx = functions.getFunctionIndex( getFunctionName() );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeTo( @Nonnull ModuleWriter writer ) throws IOException {
        if( virtualFunctionIdx < 0 ) {
            super.writeTo( writer );
        } else {
            int tempVarIdx = localVariables.get( tempVar, getCodePosition() );

            // duplicate this on the stack
            writer.writeLocal( VariableOperator.tee, tempVarIdx );
            writer.writeLocal( VariableOperator.get, tempVarIdx );

            if( options.useGC() ) {
                writer.writeStructOperator( StructOperator.GET, type, new NamedStorageType( type, "", "vtable" ), 0 ); // vtable is ever on position 0
            } else {
                writer.writeConst( 0, ValueType.i32 ); // vtable is ever on position 0
                writer.writeFunctionCall( new JavaScriptSyntheticFunctionName( "NonGC", "get_i32", () -> "(a,i) => a[i]", ValueType.anyref, ValueType.i32, null, ValueType.i32 ) );
            }
            writer.writeLoadI32( virtualFunctionIdx * 4 );
            writer.writeVirtualFunctionCall( getFunctionName(), type );
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
