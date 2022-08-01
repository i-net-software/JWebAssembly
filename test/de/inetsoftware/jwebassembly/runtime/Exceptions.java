/*
 * Copyright 2018 - 2022 Volker Berlin (i-net software)
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

import static org.junit.Assume.assumeFalse;

import java.util.ArrayList;
import java.util.Collection;

import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runners.Parameterized.Parameters;

import de.inetsoftware.jwebassembly.JWebAssembly;
import de.inetsoftware.jwebassembly.ScriptEngine;
import de.inetsoftware.jwebassembly.WasmRule;
import de.inetsoftware.jwebassembly.api.annotation.Export;
import de.inetsoftware.jwebassembly.web.DOMString;
import de.inetsoftware.jwebassembly.web.JSObject;

public class Exceptions extends AbstractBaseTest {

    @ClassRule
    public static WasmRule rule = new WasmRule( TestClass.class );

    public Exceptions( ScriptEngine script, String method, Object[] params ) {
        super( rule, script, method, params );
    }

    @Parameters( name = "{0}-{1}" )
    public static Collection<Object[]> data() {
        ArrayList<Object[]> list = new ArrayList<>();
        for( ScriptEngine script : ScriptEngine.testEngines() ) {
            addParam( list, script, "simple" );
            addParam( list, script, "simpleLong" );
            addParam( list, script, "direct" );
            addParam( list, script, "rethrow" );
            addParam( list, script, "tryFinally" );
            addParam( list, script, "tryFinally2" );
            addParam( list, script, "complex" );
            addParam( list, script, "sync" );
            addParam( list, script, "syncWithInnerTryCatch" );
            addParam( list, script, "syncInsideCondition" );
            addParam( list, script, "emptyCatch" );
            addParam( list, script, "multiCatch" );
            addParam( list, script, "multiCatch2" );
            addParam( list, script, "multiCatch3" );
            addParam( list, script, "multiCatch4" );
            addParam( list, script, "serialCatch" );
            addParam( list, script, "tryReturn" );
            addParam( list, script, "whileTrueTryFinally" );
            addParam( list, script, "ifMultipleInFinally" );
            addParam( list, script, "catchWithContinue" );
        }
        rule.setTestParameters( list );
        rule.setProperty( JWebAssembly.WASM_USE_EH, "true" );
        return list;
    }

    @Test
    public void test() {
        assumeFalse( getScriptEngine().name().startsWith( "SpiderMonkey" ) ); //TODO https://bugzilla.mozilla.org/show_bug.cgi?id=1335652
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
        static long simpleLong() {
            long r;
            try {
                r = 5L / 0L;
            } catch(Exception ex ) {
                r = 2L;
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
        static DOMString rethrow() {
            String msg = "ok";
            try {
                rethrowImpl();
            } catch(Exception ex ) {
                msg = ex.getMessage();
            }
            return JSObject.domString( msg );
        }

        private static int rethrowImpl() {
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
        static int tryFinally2() {
            int v = 1;
            try {
                v++;
            } finally {
                v++;
            }
            return v;
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

        @Export
        static int syncWithInnerTryCatch() {
            int result;
            Object obj = new Object();
            synchronized( obj ) {
                try {
                    result = 13;
                } catch( Exception x ) {
                    result = 42;
                }
            }
            return result;
        }

        @Export
        static int syncInsideCondition() {
            int value = 0;
            Object obj = new Object();
            if( value == 0 ) {
                synchronized( obj ) {
                    value = 42;
                }
            }
            return value;
        }

        @Export
        static int emptyCatch() {
            int h = 127; // variable slot 0
            try {
                int i = h + 1; // variable slot 1
            } catch( NumberFormatException nfe ) { // reuse variable slot 1
                // nothing
            }
            return h;
        }

        @Export
        static int multiCatch() {
            int r;
            try {
                r = 5 / 0;
            } catch(RuntimeException ex ) {
                r = 1;
            } catch(Exception ex ) {
                r = 2;
            }
            return r;
        }

        @Export
        static int multiCatch2() {
            int r;
            try {
                r = 5 / 0;
            } catch(NumberFormatException | ArithmeticException ex ) {
                r = 1;
            }
            return r;
        }

        @Export
        public static int multiCatch3() throws Throwable {
            try {
                new Throwable();
                return 42;
            } catch( IllegalStateException x ) {
            } catch( IllegalArgumentException | NullPointerException x ) {
                throw new Throwable( x );
            }
            return 13;
        }

        @Export
        static int multiCatch4() throws Throwable {
            try {
              Object val = null;
              return 42;
            } catch( ArrayIndexOutOfBoundsException | IllegalArgumentException x ) {
                throw new Throwable( x );
            } catch( Throwable x ) {
                throw new Throwable( x );
            }
        }

        @Export
        static int serialCatch() {
            int r = 42;
            try {
                r = 5 / 0;
            } catch(RuntimeException ex ) {
                // nothing
            }
            try {
                r = 5 / 0;
            } catch(RuntimeException ex ) {
                // nothing
            }
            try {
                r = 5 / 0;
            } catch(RuntimeException ex ) {
                // nothing
            }
            return r;
        }

        @Export
        static public int tryReturn() {
            boolean flag = true;
            try {
                if( flag )
                    return 13;
            } finally {
                flag = true;
            }
            return 42;
        }

        @Export
        static int whileTrueTryFinally() {
            int sw = 1;
            LOOP: while( true ) {
                try {
                    if( sw == 1 ) {
                        sw = 2;
                        continue LOOP;
                    }
                } finally {
                    sw++;
                }
                break;
            }
            return sw;
        }

        @Export
        private static int ifMultipleInFinally() throws Throwable {
            int pos = 0;
            try {
                pos = 13;
            } catch( Throwable ex ) {
                pos = 42;
            } finally {
                if( pos < -13 || pos == 0 ) {
                    return 17;
                }
            }
            return pos;
        }

        @Export
        static private int catchWithContinue() {
            int val = 0;

            for( int i = 0; i < 10; i++ ) {
                try {
                    val = 42;
                } catch( Throwable ex ) {
                    continue;
                }
                val = 13;
            }
            return val;
        }

//        @Export
//        static int npe() {
//            Object obj = new NullPointerException();
//            return 3;
//        }
    }
}
