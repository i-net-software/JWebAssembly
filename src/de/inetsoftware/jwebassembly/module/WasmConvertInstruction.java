/*
 * Copyright 2018 Volker Berlin (i-net software)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.inetsoftware.jwebassembly.module;

import java.io.IOException;

/**
 * Cast operations for converting one data type to another
 * 
 * @author Volker Berlin
 *
 */
class WasmConvertInstruction extends WasmInstruction {

    private ValueTypeConvertion conversion;

    /**
     * Create an instance of a convert instruction
     * 
     * @param conversion
     *            the conversion type
     * @param javaCodePos
     *            the code position/offset in the Java method
     */
    WasmConvertInstruction( ValueTypeConvertion conversion, int javaCodePos ) {
        super( javaCodePos );
        this.conversion = conversion;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeTo( ModuleWriter writer ) throws IOException {
        writer.writeCast( conversion );
    }
}
