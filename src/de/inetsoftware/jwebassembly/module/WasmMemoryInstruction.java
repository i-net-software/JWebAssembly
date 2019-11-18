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

import de.inetsoftware.jwebassembly.wasm.AnyType;
import de.inetsoftware.jwebassembly.wasm.MemoryOperator;
import de.inetsoftware.jwebassembly.wasm.ValueType;

/**
 * WasmInstruction for load and store to the linear memory.
 * 
 * @author Volker Berlin
 *
 */
class WasmMemoryInstruction extends WasmInstruction {

    private MemoryOperator op;

    private ValueType type;

    private int offset;

    private int aligment;

    /**
     * Create an instance of a load/store instruction
     * 
     * @param op
     *            the operation
     * @param type
     *            the type of the static field
     * @param offset
     *            the base offset which will be added to the offset value on the stack
     * @param alignment
     *            the alignment of the value on the linear memory (0: 8 Bit; 1: 16 Bit; 2: 32 Bit)
     * @param javaCodePos
     *            the code position/offset in the Java method
     * @param lineNumber
     *            the line number in the Java source code
     */
    WasmMemoryInstruction( MemoryOperator op, ValueType type, int offset, int aligment, int javaCodePos, int lineNumber ) {
        super( javaCodePos, lineNumber );
        this.op = op;
        this.type = type;
        this.offset = offset;
        this.aligment = aligment;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    Type getType() {
        return Type.Memory;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeTo( @Nonnull ModuleWriter writer ) throws IOException {
        writer.writeMemoryOperator( op, type, offset, aligment );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    AnyType getPushValueType() {
        return op.name().startsWith( "load" ) ? type : null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    int getPopCount() {
        return op.name().startsWith( "load" ) ? 0 : 1;
    }
}
