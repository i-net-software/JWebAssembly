/*
 * Copyright 2017 - 2021 Volker Berlin (i-net software)
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
package de.inetsoftware.jwebassembly;

import java.util.ArrayList;
import java.util.Collection;

/**
 * @author Volker Berlin
 */
public enum ScriptEngine {
    /** 
     * Test the binary output with a fix version nodejs JavaScript runtime. GC is disabled.
     */
    NodeJS,
    /** 
     * Test the binary output with the nightly SpiderMonkey JavaScript runtime. GC is disabled.
     */
    SpiderMonkey,
    /**
     * Convert the text output with the nightly wabt.js (https://github.com/AssemblyScript/wabt.js/) and test it
     * with nodejs. GC is disabled.
     */
    NodeWat,
    /** 
     * Convert the text output with wasmTextToBinary function form nightly SpiderMonkey. GC is disabled. 
     */
    SpiderMonkeyWat,
    /**
     * Convert the text output with wat2wasm https://github.com/WebAssembly/wabt and test it with nodejs. GC is
     * disabled.
     */
    Wat2Wasm,
    /** 
     * Test the binary output with a fix version nodejs JavaScript runtime. GC is enabled.
     */
    NodeJsGC( true ),
    /** 
     * Test the binary output with the nightly SpiderMonkey JavaScript runtime. GC is enabled. 
     */
    SpiderMonkeyGC( true ),
    /**
     * Convert the text output with the nightly wabt.js (https://github.com/AssemblyScript/wabt.js/) and tun the test
     * with nodejs. GC is enabled.
     */
    NodeWatGC( true ),
    /** 
     * Convert the text output with wasmTextToBinary function form nightly SpiderMonkey. GC is enabled.
     */
    SpiderMonkeyWatGC( true ),
    /**
     * Convert the text output with wat2wasm https://github.com/WebAssembly/wabt and test it with nodejs. GC is enabled.
     */
    Wat2WasmGC( true ),
    ;

    public final String useGC;

    private ScriptEngine() {
        this.useGC = null;
    }

    private ScriptEngine( boolean useGC ) {
        this.useGC = Boolean.toString( useGC );
    }

    public static ScriptEngine[] testEngines() {
        ScriptEngine[] val = { //
                        SpiderMonkey, //
                        NodeJS, //
                        // NodeWat, // disabled because https://github.com/AssemblyScript/wabt.js/issues/26
                        SpiderMonkeyWat,//
                        Wat2Wasm, //
        };
        return val;
    }

    public static Collection<ScriptEngine[]> testParams() {
        ArrayList<ScriptEngine[]> val = new ArrayList<>();
        for( ScriptEngine script : ScriptEngine.testEngines() ) {
            val.add( new ScriptEngine[] { script } );
        }
        return val;
    }
}

