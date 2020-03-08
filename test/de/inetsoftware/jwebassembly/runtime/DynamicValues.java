/*
 * Copyright 2019 Volker Berlin (i-net software)
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;

import java.util.Collection;

import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import de.inetsoftware.jwebassembly.ScriptEngine;
import de.inetsoftware.jwebassembly.WasmRule;
import de.inetsoftware.jwebassembly.api.annotation.Export;

/**
 * Run some tests which not return static/fix values likes times or random value and which are difficult to compare.
 * 
 * @author Volker Berlin
 *
 */
@RunWith( Parameterized.class )
public class DynamicValues {

    @ClassRule
    public static WasmRule rule = new WasmRule( TestClass.class );

    private final ScriptEngine script;

    public DynamicValues( ScriptEngine script ) {
        this.script = script;
    }

    @Parameters( name = "{0}" )
    public static Collection<ScriptEngine[]> data() {
        return ScriptEngine.testParams();
    }

    @Test
    public void currentTimeMillis() {
        long before = System.currentTimeMillis(); 
        String result = rule.evalWasm( script, "currentTimeMillis" );
        long after = System.currentTimeMillis(); 
        long val = Long.parseLong( result );
        assertTrue( before + "<=" + val + "<=" + after, before <= val && val <= after );
    }

    static class TestClass {

        @Export
        static long currentTimeMillis() {
            return System.currentTimeMillis();
        }

        @Export
        static int testHashCode() {
            Object obj1 = new Object();
            Object obj2 = new Object();
            int result = 0;
            result |= obj1.hashCode() != 0 ? 1 : 0;
            result |= obj1.hashCode() != obj2.hashCode() ? 2 : 0;
            result |= obj1.hashCode() == obj1.hashCode() ? 4 : 0;
            return result;
        }
    }
}
