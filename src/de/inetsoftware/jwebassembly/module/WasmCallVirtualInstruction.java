/*
   Copyright 2019 - 2020 Volker Berlin (i-net software)

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
import de.inetsoftware.jwebassembly.wasm.MemoryOperator;
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
class WasmCallVirtualInstruction extends WasmCallIndirectInstruction {

    private int                         virtualFunctionIdx = -1;

    private final WasmOptions           options;

    /**
     * Create an instance of a function call instruction
     * 
     * @param name
     *            the function name that should be called
     * @param javaCodePos
     *            the code position/offset in the Java method
     * @param lineNumber
     *            the line number in the Java source code
     * @param types
     *            the type manager
     * @param options
     *            compiler properties
     */
    WasmCallVirtualInstruction( FunctionName name, int javaCodePos, int lineNumber, TypeManager types, WasmOptions options ) {
        super( name, javaCodePos, lineNumber, types );
        this.options = options;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    Type getType() {
        return Type.CallVirtual;
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
     * if this call is executed virtual or if is was optimized.
     * 
     * @return true, virtual call
     */
    boolean isVirtual() {
        return virtualFunctionIdx > 0;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeTo( @Nonnull ModuleWriter writer ) throws IOException {
        if( virtualFunctionIdx < 0 ) {
            super.writeTo( writer );
        } else {
            // duplicate this on the stack
            writer.writeLocal( VariableOperator.get, getVariableIndexOfThis() );

            StructType type = getThisType();
            if( options.useGC() ) {
                writer.writeStructOperator( StructOperator.GET, type, new NamedStorageType( type, "", "vtable" ), 0 ); // vtable is ever on position 0
            } else {
                writer.writeConst( 0, ValueType.i32 ); // vtable is ever on position 0
                writer.writeFunctionCall( WasmCodeBuilder.GET_I32 );
            }
            writer.writeMemoryOperator( MemoryOperator.load, ValueType.i32, virtualFunctionIdx * 4, 2 );
            writer.writeVirtualFunctionCall( getFunctionName(), type );
        }
    }
}
