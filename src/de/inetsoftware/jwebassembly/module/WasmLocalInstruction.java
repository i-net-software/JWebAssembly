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

import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;

import de.inetsoftware.jwebassembly.wasm.AnyType;
import de.inetsoftware.jwebassembly.wasm.VariableOperator;

import static de.inetsoftware.jwebassembly.wasm.VariableOperator.*;

/**
 * WasmInstruction for load and store local variables.
 * 
 * @author Volker Berlin
 *
 */
class WasmLocalInstruction extends WasmInstruction {

    private VariableOperator      op;

    private int                   idx;

    /**
     * Create an instance of a load/store instruction
     * 
     * @param load
     *            true: if load
     * @param idx
     *            the memory/slot idx of the variable
     * @param javaCodePos
     *            the code position/offset in the Java method
     * @param lineNumber
     *            the line number in the Java source code
     */
    WasmLocalInstruction( boolean load, @Nonnegative int idx, int javaCodePos, int lineNumber ) {
        super( javaCodePos, lineNumber );
        this.op = load ? get : set;
        this.idx = idx;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    Type getType() {
        return Type.Local;
    }

    /**
     * Get the operator
     * 
     * @return the operator
     */
    VariableOperator getOperator() {
        return op;
    }

    /**
     * Set the operator
     * 
     * @param op
     *            the operator
     */
    void setOperator( VariableOperator op ) {
        this.op = op;
    }

    /**
     * Get the number of the locals
     * 
     * @return the index
     */
    @Nonnegative
    int getIndex() {
        return idx;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeTo( @Nonnull ModuleWriter writer ) throws IOException {
        int index = getIndex();
        writer.writeLocal( op, index );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    AnyType getPushValueType() {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    int getPopCount() {
        return op == get  ? 0 : 1;
    }
}
