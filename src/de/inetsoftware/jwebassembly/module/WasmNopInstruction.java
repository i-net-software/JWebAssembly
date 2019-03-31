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

import de.inetsoftware.jwebassembly.wasm.AnyType;

/**
 * WasmInstruction for nop.
 * 
 * @author Volker Berlin
 *
 */
class WasmNopInstruction extends WasmInstruction {

    /**
     * Create an instance of a nop instruction
     * 
     * @param javaCodePos
     *            the code position/offset in the Java method
     * @param lineNumber
     *            the line number in the Java source code
     */
    WasmNopInstruction( int javaCodePos, int lineNumber ) {
        super( javaCodePos, lineNumber );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    Type getType() {
        return Type.Nop;
    }

    /**
     * {@inheritDoc}
     */
    public void writeTo( @Nonnull ModuleWriter writer ) throws IOException {
        // nothing
    }

    /**
     * {@inheritDoc}
     */
    AnyType getPushValueType() {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    int getPopCount() {
        return 0;
    }
}
