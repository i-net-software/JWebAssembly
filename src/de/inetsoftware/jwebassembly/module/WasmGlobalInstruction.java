/*
   Copyright 2018 - 2021 Volker Berlin (i-net software)

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

/**
 * WasmInstruction for set and get global variables.
 * 
 * @author Volker Berlin
 *
 */
class WasmGlobalInstruction extends WasmInstruction {

    private boolean load;

    private FunctionName name;

    private AnyType type;

    /**
     * Create an instance of a load/store instruction
     * 
     * @param load
     *            true: if load or GET
     * @param name
     *            the name of the static field
     * @param type
     *            the type of the static field
     * @param javaCodePos
     *            the code position/offset in the Java method
     * @param lineNumber
     *            the line number in the Java source code
     */
    WasmGlobalInstruction( boolean load, @Nonnull FunctionName name, AnyType type, int javaCodePos, int lineNumber ) {
        super( javaCodePos, lineNumber );
        this.load = load;
        this.name = name;
        this.type = type;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    Type getType() {
        return Type.Global;
    }

    /**
     * The name of the field
     * 
     * @return the field
     */
    @Nonnull
    FunctionName getFieldName() {
        return name;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeTo( @Nonnull ModuleWriter writer ) throws IOException {
        writer.writeGlobalAccess( load, name, type );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    AnyType getPushValueType() {
        return load ? type : null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    int getPopCount() {
        return load ? 0 : 1;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    AnyType[] getPopValueTypes() {
        return load ? null : new AnyType[] { type };
    }
}
