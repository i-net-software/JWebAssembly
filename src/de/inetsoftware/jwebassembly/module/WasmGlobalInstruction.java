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

import de.inetsoftware.classparser.Member;
import de.inetsoftware.jwebassembly.wasm.AnyType;
import de.inetsoftware.jwebassembly.wasm.ValueType;

/**
 * WasmInstruction for set and get global variables.
 * 
 * @author Volker Berlin
 *
 */
class WasmGlobalInstruction extends WasmInstruction {

    private boolean load;

    private Member  ref;

    /**
     * Create an instance of a load/store instruction
     * 
     * @param load
     *            true: if load or GET
     * @param ref
     *            reference to a static field
     * @param javaCodePos
     *            the code position/offset in the Java method
     * @param lineNumber
     *            the line number in the Java source code
     */
    WasmGlobalInstruction( boolean load, Member ref, int javaCodePos, int lineNumber ) {
        super( javaCodePos, lineNumber );
        this.load = load;
        this.ref = ref;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    Type getType() {
        return Type.Global;
    }

    /**
     * {@inheritDoc}
     */
    public void writeTo( @Nonnull ModuleWriter writer ) throws IOException {
        FunctionName name = new FunctionName( ref );
        writer.writeGlobalAccess( load, name, ref );
    }

    /**
     * {@inheritDoc}
     */
    AnyType getPushValueType() {
        return load ? ValueType.getValueType( ref.getType() ) : null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    int getPopCount() {
        return load ? 0 : 1;
    }
}
