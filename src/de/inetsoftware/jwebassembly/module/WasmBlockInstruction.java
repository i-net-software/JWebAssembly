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

/**
 * WasmInstruction for block operation.
 * 
 * @author Volker Berlin
 *
 */
class WasmBlockInstruction extends WasmInstruction {

    private final WasmBlockOperator op;

    private final Object            data;

    /**
     * Create an instance of block operation.
     * 
     * @param op
     *            the operation
     * @param data
     *            extra data depending of the operator
     * @param javaCodePos
     *            the code position/offset in the Java method
     */
    WasmBlockInstruction( @Nonnull WasmBlockOperator op, @Nullable Object data, int javaCodePos ) {
        super( javaCodePos );
        this.op = op;
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
    ValueType getPushValueType() {
        return op == WasmBlockOperator.IF && data != ValueType.empty ? (ValueType)data : null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    int getPopCount() {
        return 0;
    }
}
