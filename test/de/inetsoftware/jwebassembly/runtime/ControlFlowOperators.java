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

import static org.junit.Assume.assumeFalse;

import java.util.ArrayList;
import java.util.Collection;

import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runners.Parameterized.Parameters;

import de.inetsoftware.jwebassembly.ScriptEngine;
import de.inetsoftware.jwebassembly.WasmRule;
import de.inetsoftware.jwebassembly.api.annotation.Export;

public class ControlFlowOperators extends AbstractBaseTest {

    @ClassRule
    public static WasmRule rule = new WasmRule( TestClass.class );

    public ControlFlowOperators( ScriptEngine script, String method, Object[] params ) {
        super( rule, script, method, params );
    }

    @Parameters( name = "{0}-{1}" )
    public static Collection<Object[]> data() {
        ArrayList<Object[]> list = new ArrayList<>();
        for( ScriptEngine script : ScriptEngine.testEngines() ) {
            addParam( list, script, "ifeq" );
            addParam( list, script, "ifne" );
            addParam( list, script, "iflt" );
            addParam( list, script, "ifMultiple" );
            addParam( list, script, "ifMultipleDouble" );
            addParam( list, script, "ifCompare" );
            addParam( list, script, "switchDirect" );
            addParam( list, script, "endlessLoop" );
            addParam( list, script, "doWhileLoop" );
            addParam( list, script, "doWhileLoopWithBreak" );
            addParam( list, script, "whileLoop" );
            addParam( list, script, "forLoop" );
            addParam( list, script, "conditionalOperator" );
            addParam( list, script, "redifineVariable" );
            addParam( list, script, "ifAnd_0" );
            addParam( list, script, "ifAnd_3" );
            addParam( list, script, "ifAnd_6" );
            addParam( list, script, "if4And_6" );
            addParam( list, script, "if4And_7" );
            addParam( list, script, "ifOr0" );
            addParam( list, script, "ifOr1" );
            addParam( list, script, "ifOr3" );
            addParam( list, script, "ifOr5" );
            addParam( list, script, "ifOr7" );
            addParam( list, script, "ifAndOr0" );
            addParam( list, script, "ifAndOr2" );
            addParam( list, script, "ifAndOr4" );
            addParam( list, script, "ifAndOr6" );
            addParam( list, script, "ifAndOr8" );
        }
        rule.setTestParameters( list );
        return list;
    }

    static class TestClass {

        @Export
        static int ifeq() {
            int condition = 0;
            if( condition != 0 ) {
                return 13;
            } else {
                return 76;
            }
        }

        @Export
        static int ifne() {
            int condition = 3;
            if( condition == 0 ) {
                return 13;
            } else {
                return 76;
            }
        }

        @Export
        static int iflt() {
            int condition = 3;
            if( condition >= 0 ) {
                condition = 13;
            } else {
                condition = 76;
            }
            return condition;
        }

        @Export
        static int ifMultiple() {
            int condition = 3;
            if( condition <= 0 ) {
                if( condition < 0 ) {
                    condition = 13;
                }
            } else {
                if( condition > 0 ) {
                    condition++;
                } else {
                    condition--;
                }
            }
            if( condition > 2 ) {
                condition++;
            } else {
                condition = 0;
            }
            if( condition >= 2 ) {
                condition++;
            } else {
                condition = 0;
            }
            if( condition <= 123 ) {
                condition++;
            } else {
                condition = 0;
            }
            if( condition < 123 ) {
                condition++;
            } else {
                condition = 0;
            }
            if( condition != 123 ) {
                condition++;
            } else {
                condition = 0;
            }
            return condition;
        }

        @Export
        static int ifMultipleDouble() {
            double condition = 3;
            if( condition <= 0 ) {
                if( condition < 0 ) {
                    condition = 13;
                }
            } else {
                if( condition > 0 ) {
                    condition++;
                } else {
                    condition--;
                }
            }
            if( condition > 2 ) {
                condition *= 2;
            } else {
                condition = 0;
            }
            if( condition >= 2 ) {
                condition *= 2;
            } else {
                condition = 0;
            }
            if( condition <= 123 ) {
                condition *= 2;
            } else {
                condition = 0;
            }
            if( condition < 123 ) {
                condition *= 2;
            } else {
                condition = 0;
            }
            if( condition != 123 ) {
                condition *= 2;
            } else {
                condition = 0;
            }
            if( condition == 123 ) {
                condition = 0;
            } else {
                condition *= 2;
            }
            int x = (int)(25 / condition); // prevent 0 as value
            return (int)condition;
        }

        @Export
        static int ifCompare() {
            double condition = 3.0;
            int result;
            if( condition >= 3.5 ) {
                result = 13;
            } else {
                result = 76;
            }
            return result;
        }

        @Export
        static int switchDirect() {
            return tableSwitch(10) + (tableSwitch( 9 ) * 10) + (tableSwitch( -1 ) * 100) + (lookupSwitch(Integer.MAX_VALUE) * 1000) + (lookupSwitch(0) * 10000 );
        }

        private static int tableSwitch( int a ) {
            int b;
            switch( 1 + a - 1 ){
                case 8:
                case 9:
                    b = 2;
                    break;
                case 10:
                case 11:
                    b = 1;
                    break;
                default:
                    b = 9;
            }
            return b;
        }

        private static int lookupSwitch( int a ) {
            int b;
            switch(a){
                case 1:
                    b = 1;
                    break;
                case 1000:
                case 1001:
                    if( a == 1000 ) {
                        b = 2;
                        break;
                    } else {
                        b = 0;
                    }
                    //$FALL-THROUGH$
                case Integer.MAX_VALUE:
                    b = 3;
                    break;
                default:
                    b = 9;
            }
            return b;
        }

        @Export
        static int endlessLoop() {
            int a = 0;
            int b = 0;
            do {
                if( a < 10 ) {
                    b++;
                } else {
                    return a;
                }
                a++;
            } while( true );
        }

        @Export
        static double doWhileLoop() {
            int a = 0;
            double d = 1.01;
            do {
                d *= 2;
                a++;
            } while( a < 10 );
            return d;
        }

        @Export
        static double doWhileLoopWithBreak() {
            int a = 0;
            double d = 1.01;
            do {
                a++;
                if( a == 5 ) {
                    break;
                }
                d *= 2;
            } while( a < 10 );
            return a * d;
        }

        @Export
        static int whileLoop() {
            float a = 0;
            int b = 1;
            while( a < 10 ) {
                b *= 2;
                a++;
            }
            return b;
        }

        @Export
        static int forLoop() {
            int a = 0;
            for( int i = 0; i < 10; i++ ) {
                a += i;
            }
            return a;
        }

        @Export
        static int conditionalOperator () {
            int condition = 4;
            return condition >= 4 ? condition < 4 ? 1 : 2 : condition == 4 ? 3 : 4;
        }

        @Export
        static double redifineVariable() {
            int x = 42;
            if( x > 0 ) {
                double a = 1;
                double b = 2.5;
                return a + b;
            } else {
                int a = 1;
                int b = 3;
                return a + b;
            }
        }

        @Export
        static int ifAnd_0() {
            return ifAnd( 0 );
        }

        @Export
        static int ifAnd_3() {
            return ifAnd( 3 );
        }

        @Export
        static int ifAnd_6() {
            return ifAnd( 6 );
        }

        private static int ifAnd( int condition ) {
            int result;
            if( condition > 0 && condition < 5 ) {
                result = 42;
            } else {
                result = 76;
            }
            return result;
        }

        @Export
        static int if4And_6() {
            return if4And( 6 );
        }

        @Export
        static int if4And_7() {
            return if4And( 7 );
        }

        private static int if4And( int condition ) {
            int result;
            if( condition > 1 && condition > 3  && condition > 5  && condition > 7 ) {
                result = 42;
            } else {
                result = 76;
            }
            return result;
        }

        @Export
        static int ifOr0() {
            return ifOr( 0 );
        }

        @Export
        static int ifOr1() {
            return ifOr( 1 );
        }

        @Export
        static int ifOr3() {
            return ifOr( 3 );
        }

        @Export
        static int ifOr5() {
            return ifOr( 5 );
        }

        @Export
        static int ifOr7() {
            return ifOr( 7 );
        }

        private static int ifOr( int condition ) {
            int result;
            if( condition == 1 || condition == 3 || condition == 5 || condition == 7 ) {
                result = 42;
            } else {
                result = 76;
            }
            return result;
        }

        @Export
        static int ifAndOr0() {
            return ifAndOr( 0 );
        }

        @Export
        static int ifAndOr2() {
            return ifAndOr( 2 );
        }

        @Export
        static int ifAndOr4() {
            return ifAndOr( 4 );
        }

        @Export
        static int ifAndOr6() {
            return ifAndOr( 6 );
        }

        @Export
        static int ifAndOr8() {
            return ifAndOr( 8 );
        }

        private static int ifAndOr( int condition ) {
            int result;
            if( (condition >= 1 && condition <= 3) || (condition >= 5 && condition <= 7) ) {
                result = 42;
            } else {
                result = 76;
            }
            return result;
        }
    }
}
