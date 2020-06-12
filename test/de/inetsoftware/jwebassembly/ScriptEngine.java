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
package de.inetsoftware.jwebassembly;

import java.util.ArrayList;
import java.util.Collection;

/**
 * @author Volker Berlin
 */
public enum ScriptEngine {
    NodeJS,
    SpiderMonkey,
    NodeWat,
    SpiderMonkeyWat,
    Wat2Wasm,
    NodeJsGC(true),
    SpiderMonkeyGC(true),
    NodeWatGC(true),
    SpiderMonkeyWatGC(true),
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
                        NodeWat, //
                        SpiderMonkeyWat,//
                        //TODO Wat2Wasm, //
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

