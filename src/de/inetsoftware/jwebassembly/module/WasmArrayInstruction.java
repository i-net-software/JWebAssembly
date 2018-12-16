/*
   Copyright 2018 Volker Berlin (i-net software)

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
import javax.annotation.Nullable;

import de.inetsoftware.jwebassembly.WasmException;
import de.inetsoftware.jwebassembly.module.WasmInstruction.Type;
import de.inetsoftware.jwebassembly.wasm.ArrayOperator;
import de.inetsoftware.jwebassembly.wasm.StorageType;
import de.inetsoftware.jwebassembly.wasm.ValueType;

/**
 * WasmInstruction for an array operation.
 * 
 * @author Volker Berlin
 *
 */
class WasmArrayInstruction extends WasmInstruction {

    private final ArrayOperator op;

    private final StorageType   type;

    /**
     * Create an instance of an array operation.
     * 
     * @param op
     *            the array operation
     * @param type
     *            the type of the parameters
     * @param javaCodePos
     *            the code position/offset in the Java method
     */
    WasmArrayInstruction( @Nullable ArrayOperator op, @Nullable StorageType type, int javaCodePos ) {
        super( javaCodePos );
        this.op = op;
        this.type = type;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    Type getType() {
        return Type.Array;
    }

    /**
     * {@inheritDoc}
     */
    public void writeTo( @Nonnull ModuleWriter writer ) throws IOException {
        writer.writeArrayOperator( op, type );
    }

    /**
     * {@inheritDoc}
     */
    ValueType getPushValueType() {
        switch( op ) {
            case NEW:
                return ValueType.anyref;
            case GET:
                return type instanceof ValueType ? (ValueType)type : ValueType.anyref;
            case SET:
                return null;
            case LENGTH:
                return ValueType.i32;
            default:
                throw new WasmException( "Unknown array operation: " + op, -1 );
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    int getPopCount() {
        switch( op ) {
            case NEW:
            case GET:
            case LENGTH:
                return 1;
            case SET:
                return 0;
            default:
                throw new WasmException( "Unknown array operation: " + op, -1 );
        }
    }
}
