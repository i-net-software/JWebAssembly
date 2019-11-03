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

import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;

import de.inetsoftware.jwebassembly.wasm.AnyType;
import de.inetsoftware.jwebassembly.wasm.ValueType;

/**
 * WasmInstruction for load and store a element in a table.
 * 
 * @author Volker Berlin
 *
 */
class WasmTableInstruction extends WasmInstruction {

    private boolean load;

    private int     idx;

    /**
     * Create an instance of a load/store instruction
     * 
     * @param load
     *            true: if "get" else "set"
     * @param idx
     *            the index of the table
     * @param javaCodePos
     *            the code position/offset in the Java method
     * @param lineNumber
     *            the line number in the Java source code
     */
    WasmTableInstruction( boolean load, @Nonnegative int idx, int javaCodePos, int lineNumber ) {
        super( javaCodePos, lineNumber );
        this.load = load;
        this.idx = idx;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    Type getType() {
        return Type.Table;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeTo( @Nonnull ModuleWriter writer ) throws IOException {
        writer.writeTable( load, idx );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    AnyType getPushValueType() {
        return load ? ValueType.anyref : null; 
    }

    /**
     * {@inheritDoc}
     */
    @Override
    int getPopCount() {
        return load  ? 0 : 1;
    }
}
