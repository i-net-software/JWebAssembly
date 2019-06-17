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

public class Exceptions extends AbstractBaseTest {

    @ClassRule
    public static WasmRule rule = new WasmRule( TestClass.class );

    public Exceptions( ScriptEngine script, String method, Object[] params ) {
        super( rule, script, method, params );
    }

    @Parameters( name = "{0}-{1}" )
    public static Collection<Object[]> data() {
        ArrayList<Object[]> list = new ArrayList<>();
        for( ScriptEngine[] val : ScriptEngine.testParams() ) {
            ScriptEngine script = val[0];
            addParam( list, script, "simple" );
            addParam( list, script, "direct" );
            addParam( list, script, "rethrow" );
            addParam( list, script, "tryFinally" );
            addParam( list, script, "complex" );
        }
        rule.setTestParameters( list );
        return list;
    }

    @Ignore
    @Test
    public void test() {
        super.test();
    }

    static class TestClass {

        @Export
        static int simple() {
            int r;
            try {
                r = 5 / 0;
            } catch(Exception ex ) {
                r = 2;
            }
            return r;
        }

        @Export
        static int direct() {
            try {
                return 5 / 0;
            } catch(Exception ex ) {
                return 2;
            }
        }

        @Export
        static int rethrow() {
            try {
                return 5 / 0;
            } catch(Exception ex ) {
                throw ex;
            }
        }

        @Export
        static int tryFinally() {
            int v = 1;
            try {
                v++;
                v = 5 / 0;
            } finally {
                v++;
                return v;
            }
        }

        @Export
        static int complex() {
            int v = 1;
            try {
                if( v == 1 ) {
                    v++;
                    v = divNull();
                } else {
                    v += 2;
                }
            } finally {
                v++;
                return v;
            }
        }

        private static int divNull() {
            return 5 / 0;
        }

        // a synchronized also add an try/finally internally 
        @Export
        static int sync() {
            int v = 1;
            Object monitor = new Object();
            synchronized( monitor ) {
                v++;
            }
            return v;
        }

//        @Export
//        static int npe() {
//            Object obj = new NullPointerException();
//            return 3;
//        }
    }
}
