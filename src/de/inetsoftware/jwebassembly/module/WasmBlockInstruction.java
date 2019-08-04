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

import de.inetsoftware.jwebassembly.wasm.AnyType;
import de.inetsoftware.jwebassembly.wasm.ValueType;
import de.inetsoftware.jwebassembly.wasm.WasmBlockOperator;

/**
 * WasmInstruction for block operation.
 * 
 * @author Volker Berlin
 *
 */
class WasmBlockInstruction extends WasmInstruction {

    private final WasmBlockOperator op;

    private Object                 data;

    /**
     * Create an instance of block operation.
     * 
     * @param op
     *            the operation
     * @param data
     *            extra data depending of the operator
     * @param javaCodePos
     *            the code position/offset in the Java method
     * @param lineNumber
     *            the line number in the Java source code
     */
    WasmBlockInstruction( @Nonnull WasmBlockOperator op, @Nullable Object data, int javaCodePos, int lineNumber ) {
        super( javaCodePos, lineNumber );
        this.op = op;
        this.data = data;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    Type getType() {
        return Type.Block;
    }

    /**
     * Get the operation
     * @return the op
     */
    WasmBlockOperator getOperation() {
        return op;
    }

    /**
     * Set a new value for the data
     * 
     * @param data
     *            the new value
     */
    void setData( Object data ) {
        this.data = data;
    }

    /**
     * {@inheritDoc}
     */
    public void writeTo( @Nonnull ModuleWriter writer ) throws IOException {
        writer.writeBlockCode( op, data );
    }

    /**
     * {@inheritDoc}
     */
    AnyType getPushValueType() {
        switch( op ) {
            case IF:
                return data != ValueType.empty ? (AnyType)data : null;
            case RETURN:
                return (AnyType)data;
            default:
                return null;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    int getPopCount() {
        switch( op ) {
            case IF:
            case BR_IF:
            case DROP:
                return 1;
            default:
                return 0;
        }
    }
}
