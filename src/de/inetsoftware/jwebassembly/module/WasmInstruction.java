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

/**
 * Base class of all WasmInstruction.
 * 
 * @author Volker Berlin
 *
 */
abstract class WasmInstruction {

    private int javaCodePos;

    /**
     * Create a new instance of an instruction
     * 
     * @param javaCodePos
     *            the code position/offset in the Java method
     */
    WasmInstruction( int javaCodePos ) {
        this.javaCodePos = javaCodePos;
    }

    /**
     * Write this instruction to the WASM module.
     * 
     * @param writer
     *            the target writer
     * @throws IOException
     *             if any I/O error occur
     */
    abstract void writeTo( @Nonnull ModuleWriter writer ) throws IOException;

    /**
     * Get current code position in Java method.
     * @return the position
     */
    int getCodePosition() {
        return javaCodePos;
    }
}
