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

import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runners.Parameterized.Parameters;

import de.inetsoftware.jwebassembly.ScriptEngine;
import de.inetsoftware.jwebassembly.WasmRule;
import de.inetsoftware.jwebassembly.api.annotation.Export;

public class Structs extends AbstractBaseTest {

    @ClassRule
    public static WasmRule rule = new WasmRule( TestClass.class, Abc.class );

    public Structs( ScriptEngine script, String method, Object[] params ) {
        super( rule, script, method, params );
    }

    @Parameters( name = "{0}-{1}" )
    public static Collection<Object[]> data() {
        ArrayList<Object[]> list = new ArrayList<>();
        for( ScriptEngine[] val : ScriptEngine.testParams() ) {
            ScriptEngine script = val[0];
            addParam( list, script, "isNull" );
            addParam( list, script, "isNotNull" );
            addParam( list, script, "simple" );
        }
        return list;
    }

    @Ignore
    @Test
    public void test() {
        super.test();
    }

    static class TestClass {

        @Export
        static boolean isNull() {
            Object val = null;
            return val == null;
        }

        @Export
        static boolean isNotNull() {
            Object val = null;
            return val != null;
        }

        @Export
        static int simple() {
            Object val = new Abc();
            return 1;
        }

    }

    static class Abc {
        int  a;

        long b;
    }
}
