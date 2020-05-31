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

import java.io.IOException;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import de.inetsoftware.jwebassembly.WasmException;
import de.inetsoftware.jwebassembly.wasm.ArrayOperator;
import de.inetsoftware.jwebassembly.wasm.AnyType;
import de.inetsoftware.jwebassembly.wasm.ValueType;

/**
 * WasmInstruction for an array operation.
 * 
 * @author Volker Berlin
 *
 */
class WasmArrayInstruction extends WasmInstruction {

    private final ArrayOperator op;

    private final AnyType   type;

    private final TypeManager types;

    /**
     * Create an instance of an array operation.
     * 
     * @param op
     *            the array operation
     * @param type
     *            the type of the parameters
     * @param javaCodePos
     *            the code position/offset in the Java method
     * @param lineNumber
     *            the line number in the Java source code
     */
    WasmArrayInstruction( @Nullable ArrayOperator op, @Nullable AnyType type, TypeManager types, int javaCodePos, int lineNumber ) {
        super( javaCodePos, lineNumber );
        this.op = op;
        this.type = type;
        this.types = types;
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
    AnyType getPushValueType() {
        switch( op ) {
            case NEW:
                return types.arrayType( type );
            case GET:
                return type instanceof ValueType ? (ValueType)type : ValueType.externref;
            case SET:
                return null;
            case LEN:
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
            case GET:
                return 2;
            case NEW:
            case LEN:
                return 1;
            case SET:
                return 3;
            default:
                throw new WasmException( "Unknown array operation: " + op, -1 );
        }
    }
}
