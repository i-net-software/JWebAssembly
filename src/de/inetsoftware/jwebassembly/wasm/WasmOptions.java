/*
 * Copyright 2017 - 2020 Volker Berlin (i-net software)
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

import javax.annotation.Nonnull;

import de.inetsoftware.jwebassembly.JWebAssembly;
import de.inetsoftware.jwebassembly.module.CodeOptimizer;
import de.inetsoftware.jwebassembly.module.FunctionManager;
import de.inetsoftware.jwebassembly.module.FunctionName;
import de.inetsoftware.jwebassembly.module.StringManager;
import de.inetsoftware.jwebassembly.module.TypeManager;

/**
 * The option/properties for the behavior of the compiler.
 * 
 * @author Volker Berlin
 */
public class WasmOptions {

    public final FunctionManager functions = new FunctionManager();

    public final TypeManager     types     = new TypeManager( this );

    public final StringManager   strings   = new StringManager( this );

    public final CodeOptimizer   optimizer = new CodeOptimizer();

    private final boolean debugNames;

    private final boolean useGC;

    private final boolean useEH;

    @Nonnull
    private final String  sourceMapBase;

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
        useEH = Boolean.parseBoolean( properties.getOrDefault( JWebAssembly.WASM_USE_EH, "false" ) );
        String base = properties.getOrDefault( JWebAssembly.SOURCE_MAP_BASE, "" );
        if( !base.isEmpty() && !base.endsWith( "/" ) ) {
            base += "/";
        }
        sourceMapBase = base;
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

    /**
     * If the exception handling feature of WASM should be use or an unreachable instruction.
     * 
     * @return true, use the EH instructions of WASM; false, generate an unreachable instruction
     */
    public boolean useEH() {
        return useEH;
    }

    /**
     * Get the relative path between the final wasm file location and the source files location.
     * If not empty it should end with a slash like "../../src/main/java/". 
     * @return the path
     */
    @Nonnull
    public String getSourceMapBase() {
        return sourceMapBase;
    }
}
