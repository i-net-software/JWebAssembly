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
import de.inetsoftware.jwebassembly.module.TypeManager.StructType;
import de.inetsoftware.jwebassembly.wasm.AnyType;
import de.inetsoftware.jwebassembly.wasm.NamedStorageType;
import de.inetsoftware.jwebassembly.wasm.StructOperator;
import de.inetsoftware.jwebassembly.wasm.ValueType;

/**
 * WasmInstruction for struct operation. A struct is like a Java class without methods.
 * 
 * @author Volker Berlin
 *
 */
class WasmStructInstruction extends WasmInstruction {

    private final StructOperator   op;

    private final StructType       type;

    private final NamedStorageType fieldName;

    /**
     * Create an instance of numeric operation.
     * 
     * @param op
     *            the struct operation
     * @param type
     *            the type of the parameters
     * @param fieldName
     *            the name of field if needed for the operation
     * @param javaCodePos
     *            the code position/offset in the Java method
     * @param lineNumber
     *            the line number in the Java source code
     */
    WasmStructInstruction( @Nullable StructOperator op, @Nullable StructType type, @Nullable NamedStorageType fieldName, int javaCodePos, int lineNumber ) {
        super( javaCodePos, lineNumber );
        this.op = op;
        this.type = type;
        this.fieldName = fieldName;
    }

    /**
     * Get the StructOperator
     * 
     * @return the operator
     */
    StructOperator getOperator() {
        return op;
    }

    /**
     * Get the struct type of this instruction.
     * 
     * @return the type
     */
    StructType getStructType() {
        return type;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    Type getType() {
        return Type.Struct;
    }

    /**
     * {@inheritDoc}
     */
    public void writeTo( @Nonnull ModuleWriter writer ) throws IOException {
        writer.writeStructOperator( op, type, fieldName );
    }

    /**
     * {@inheritDoc}
     */
    AnyType getPushValueType() {
        switch( op ) {
            case NULL:
                return ValueType.anyref;
            case NEW:
            case NEW_DEFAULT:
            case GET:
                return type;
            case SET:
                return null;
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
            case NEW_DEFAULT:
            case GET:
                return 1;
            case SET:
            case NULL:
                return 0;
            default:
                throw new WasmException( "Unknown array operation: " + op, -1 );
        }
    }
}
