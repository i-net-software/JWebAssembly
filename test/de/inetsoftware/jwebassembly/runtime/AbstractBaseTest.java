/*
 * Copyright 2018 - 2019 Volker Berlin (i-net software)
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
package de.inetsoftware.jwebassembly.runtime;

import java.util.ArrayList;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import de.inetsoftware.jwebassembly.ScriptEngine;
import de.inetsoftware.jwebassembly.WasmRule;

/**
 * @author Volker Berlin
 */
@RunWith( Parameterized.class )
public abstract class AbstractBaseTest {

    private final WasmRule     wasm;

    private final ScriptEngine script;

    private final String       method;

    private final Object[]     params;

    protected AbstractBaseTest( WasmRule wasm, ScriptEngine script, String method, Object[] params ) {
        this.wasm = wasm;
        this.script = script;
        this.method = method;
        this.params = params;
    }

    protected static void addParam( ArrayList<Object[]> list, ScriptEngine script, String method, Object ...params ) {
        list.add( new Object[]{script, method, params} );
    }

    /**
     * Get the ScriptEngine with which the test is running.
     * 
     * @return the engine
     */
    protected ScriptEngine getScriptEngine() {
        return script;
    }

    /**
     * Get the name of the method that is currently tested
     * @return the name
     */
    protected String getMethod() {
        return method;
    }

    @Before
    public void before() throws Exception {
        wasm.before( script );
    }

    @Test
    public void test() {
        wasm.test( script, method, params );
    }
}
