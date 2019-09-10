/*
 * Copyright 2017 - 2019 Volker Berlin (i-net software)
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
package de.inetsoftware.jwebassembly.wasm;

import java.util.HashMap;

import de.inetsoftware.jwebassembly.JWebAssembly;
import de.inetsoftware.jwebassembly.module.FunctionName;

/**
 * The option/properties for the behavior of the compiler.
 * 
 * @author Volker Berlin
 */
public class WasmOptions {

    private final boolean debugNames;

    private final boolean useGC;

    /**
     * NonGC function for ref_eq polyfill.
     */
    public FunctionName ref_eq;

    /**
     * Create a new instance of options
     * 
     * @param properties
     *            compiler properties
     */
    public WasmOptions( HashMap<String, String> properties ) {
        debugNames = Boolean.parseBoolean( properties.get( JWebAssembly.DEBUG_NAMES ) );
        useGC = Boolean.parseBoolean( properties.getOrDefault( JWebAssembly.WASM_USE_GC, "false" ) );
    }

    /**
     * Property for adding debug names to the output if true.
     * 
     * @return true, add debug information
     */
    public boolean debugNames() {
        return debugNames;
    }

    /**
     * If the GC feature of WASM should be use or the GC of the JavaScript host.
     * 
     * @return true, use the GC instructions of WASM.
     */
    public boolean useGC() {
        return useGC;
    }
}
