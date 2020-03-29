/*
   Copyright 2018 - 2020 Volker Berlin (i-net software)

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

    final LocaleVariableManager localVariables;

    /**
     * Create an instance of a load/store instruction for a local variable.
     * 
     * @param op
     *            the operation
     * @param idx
     *            the memory/slot idx of the variable
     * @param localVariables
     *            the manager for local variables
     * @param javaCodePos
     *            the code position/offset in the Java method
     * @param lineNumber
     *            the line number in the Java source code
     */
    WasmLocalInstruction( VariableOperator op, @Nonnegative int idx, LocaleVariableManager localVariables, int javaCodePos, int lineNumber ) {
        super( javaCodePos, lineNumber );
        this.op = op;
        this.idx = idx;
        this.localVariables = localVariables;
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
     * @return the index, mostly the Wasm Index
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
        return op != set ? localVariables.getValueType( getIndex() ) : null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    int getPopCount() {
        return op == get  ? 0 : 1;
    }
}
