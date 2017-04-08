/*
 * Copyright 2017 Volker Berlin (i-net software)
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
package de.inetsoftware.jwebassembly.err;

import static org.junit.Assert.assertEquals;

import java.util.Collection;

import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.webassembly.annotation.Export;

import de.inetsoftware.jwebassembly.ScriptEngine;
import de.inetsoftware.jwebassembly.WasmRule;

/**
 * @author Volker Berlin
 */
@RunWith(Parameterized.class)
public class RuntimeErrors {

    @ClassRule
    public static WasmRule     rule = new WasmRule( TestClass.class );

    private final ScriptEngine script;

    public RuntimeErrors( ScriptEngine script ) {
        this.script = script;
    }

    @Parameters( name = "{0}" )
    public static Collection<Object[]> data() {
        return ScriptEngine.testParams();
    }

    @Test
    public void longReturn() {
        assertEquals( "TypeError: cannot pass i64 to or from JS", rule.evalWasm( script, "longReturn" ) );
    }

    static class TestClass {

        @Export
        static long longReturn() {
            return Long.MAX_VALUE;
        }
    }
}
