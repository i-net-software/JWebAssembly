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
package de.inetsoftware.jwebassembly.runtime;

import java.util.ArrayList;
import java.util.Collection;

import org.junit.Assume;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runners.Parameterized.Parameters;

import de.inetsoftware.jwebassembly.ScriptEngine;
import de.inetsoftware.jwebassembly.WasmRule;
import de.inetsoftware.jwebassembly.api.annotation.Export;

public class Stacks extends AbstractBaseTest {

    @ClassRule
    public static WasmRule rule = new WasmRule( TestClass.class );

    public Stacks( ScriptEngine script, String method, Object[] params ) {
        super( rule, script, method, params );
    }

    @Parameters( name = "{0}-{1}" )
    public static Collection<Object[]> data() {
        ArrayList<Object[]> list = new ArrayList<>();
        for( ScriptEngine[] val : ScriptEngine.testParams() ) {
            ScriptEngine script = val[0];
            if( script == ScriptEngine.SpiderMonkey ) { //TODO SpiderMonkey does not support multiple return values
                continue;
            }
            addParam( list, script, "dupInt" );
            addParam( list, script, "dupFloat" );
            addParam( list, script, "dupDouble" );
            addParam( list, script, "dupLong" );
        }
        return list;
    }

    static class TestClass {

        @Export
        static int dupInt() {
            int a = 1;
            int b = 2;
            a = b = 3;
            return b;
        }

        @Export
        static float dupFloat() {
            float a = 1;
            float b = 2;
            a = b = 3.25F;
            return b;
        }

        @Export
        static double dupDouble() {
            double a = 1;
            double b = 2;
            a = b = 3.25;
            return b;
        }

        @Export
        static int dupLong() {
            long a = 1;
            long b = 2;
            a = b = 3;
            return (int)b;
        }
    }
}
