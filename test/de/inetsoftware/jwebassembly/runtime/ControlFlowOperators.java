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

import java.util.ArrayList;
import java.util.Collection;

import org.junit.ClassRule;
import org.junit.runners.Parameterized.Parameters;

import de.inetsoftware.jwebassembly.ScriptEngine;
import de.inetsoftware.jwebassembly.WasmRule;
import de.inetsoftware.jwebassembly.api.annotation.Export;

@SuppressWarnings( { "javadoc", "null", "rawtypes", "cast", "boxing", "unused" } )
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
            addParam( list, script, "doWhileLoopTwoConditions" );
            addParam( list, script, "doWhileLoopWithBreak" );
            addParam( list, script, "whileLoop" );
            addParam( list, script, "whileLoopWithContinue" );
            addParam( list, script, "whileLoopInElse_3" );
            addParam( list, script, "whileLoopInElse_13" );
            addParam( list, script, "whileLoopInElseAfterReturn" );
            addParam( list, script, "whileLoopAfterIfWithReturn" );
            addParam( list, script, "whileLoopInsideLoop" );
            addParam( list, script, "forLoop" );
            addParam( list, script, "conditionalOperator" );
            addParam( list, script, "conditionalOperator2" );
            addParam( list, script, "conditionalOperatorConcated" );
            addParam( list, script, "conditionalOperatorConcated2" );
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
            addParam( list, script, "ifAndOrComplex" );
            addParam( list, script, "ifWithoutElseAndLoop" );
            addParam( list, script, "ifOrWithMulti" );
            addParam( list, script, "ifMultipleInsideThen" );
            addParam( list, script, "ifWithConditionalInsideThen" );
            addParam( list, script, "conditionalInsideIf_1" );
            addParam( list, script, "conditionalInsideIf_2" );
            addParam( list, script, "conditionalInsideIf_3" );
            addParam( list, script, "conditionalInsideIf_4" );
            addParam( list, script, "ifWithReturn8" );
            addParam( list, script, "ifWithReturn17" );
            addParam( list, script, "ifWithReturn21" );
            addParam( list, script, "ifWithReturn27" );
            addParam( list, script, "ifWithReturn28" );
            addParam( list, script, "stringSwitchNormalFoo" );
            addParam( list, script, "stringSwitchNormalBar" );
            addParam( list, script, "stringSwitchNormalDefault" );
            addParam( list, script, "stringSwitchReverseFoo" );
            addParam( list, script, "stringSwitchReverseBar" );
            addParam( list, script, "stringSwitchReverseDefault" );
            addParam( list, script, "stringSwitchContinue1" );
            addParam( list, script, "stringSwitchContinue2" );
            addParam( list, script, "stringSwitchContinueDefault" );
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
        static int doWhileLoopTwoConditions() {
            int val = 42;
            int shift = 1;
            do {
                val >>>= shift;
            } while (val > 7 && shift > 0);

            return val;
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
        public static int whileLoopWithContinue() {
            int i = 0;
            int value = 0;
            while( i < 16 ) {
                i++;
                if( i > 8 ) {
                    continue;
                }
                value |= 1 << i;
            }
            return value;
        }

        private static int whileLoopInElse( int yIndex ) {
            int result = 0;
            if( yIndex == 3 ) {
                result = 42;
            } else {
                while( yIndex > 7 ) {
                    result++;
                    yIndex--;
                }
            }
            return result;
        }

        @Export
        public static int whileLoopInElse_3() {
            return whileLoopInElse( 3 );
        }

        @Export
        public static int whileLoopInElse_13() {
            return whileLoopInElse( 13 );
        }

        @Export
        public static int whileLoopInElseAfterReturn() {
            int yIndex = 13;
            int result = 0;
            if (yIndex == 3) {
                return 42;
            } else {
                while (yIndex > 7) {
                    result++;
                    yIndex--;
                }
            }
            return result;
        }

        @Export
        public static int whileLoopAfterIfWithReturn() {
            int yIndex = 13;
            int result = 0;
            if (yIndex < 3) {
                if( yIndex == 3 ) {
                    return 42;
                }
                while (yIndex > 7) {
                    result++;
                    yIndex--;
                }
            }
            return result;
        }

        @Export
        public static int whileLoopInsideLoop() {
            int i = 15;

            MAIN: while( true ) {
                while( i >= 9 ) {
                    i--;
                }
                int start = i;

                while( i > start ) {
                    if( true ) {
                        i--;
                        continue MAIN;
                    }
                }
                return start;
            }
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
        static int conditionalOperator2() {
            int val = 42;
            int result = 3 + (val == 1 ? 1 : (val == 2 ? 2 : (val == 3 ? 3 : 4)));
            return result;
        }

        @Export
        static int conditionalOperatorConcated () {
            int a = 7;
            int b = 13;
            int c = 42;
            int result = (a < 0 ? false : a == c ) && (b < 0 ? false : b == c ) ? 3 : 4;
            return result;
        }

        @Export
        static int conditionalOperatorConcated2() {
            int a = 7;
            int b = 13;
            int c = 42;
            return (a < 0 ? a != b : a != c ) && (b < 0 ? false : b == c ) ? 17 : 18;
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

        @Export
        private static int ifAndOrComplex() {
            int b1 = 0;
            int b2 = 0;
            int result;
            if( (b1 == 0xf0 && (b2  < 0x90 || b2 > 0xbf)) ||
                   (b1 == 0xf4 && (b2 & 0xf0) != 0x80) ||
                   (b2 & 0xc0) != 0x80) {
                result = 13;
            } else {
                result = 42;
            }
            return result;
        }

        private static int ifWithoutElseAndLoop;

        @Export
        static int ifWithoutElseAndLoop() {
            int n = 1;
            // because some compiler (Eclipse) move the loop condition to the end of the loop. Then there can be an optimization that the if jump to the end of the loop.
            if( ifWithoutElseAndLoop != 1 ) {
                ifWithoutElseAndLoop = 1;
            }
            while( n < 10 ) {
                ifWithoutElseAndLoop *= n++;
            }
            return ifWithoutElseAndLoop;
        }

        @Export
        static int ifOrWithMulti() {
            int len = 4;

            // the GOTO before the ELSE is not related to the main IF condition 
            if( (len == 4 || len == 9) ) {
                if( len == 9 ) {
                    len = 13;
                } else {
                    len = 42;
                }
            }
            return len;
        }

        @Export
        static int ifMultipleInsideThen() {
            int result = 0;
            if( (result == 7) || (result == 13) ) {
                // multiple IF inside the primary IF
                if( result == -1 ) {
                    result = 1;
                } else {
                    result = 2;
                }

                if( result > result ) {
                    result = 3;
                }
            } else {
                result = 4;
            }
            return result;
        }

        @Export
        static int ifWithConditionalInsideThen() {
            int val = 42;
            int result = 0;
            if( val > 20 ) {
                if( val > 21 ) {
                    result = val == 42 ? 4 : 5;
                }
            } else {
                result = 3;
            }
            return result + 13;
        }

        @Export
        static int conditionalInsideIf_1() {
            return conditionalInsideIf( null, null, null );
        }

        @Export
        static int conditionalInsideIf_2() {
            return conditionalInsideIf( null, null, "foo" );
        }

        @Export
        static int conditionalInsideIf_3() {
            return conditionalInsideIf( "foo", null, null );
        }

        @Export
        static int conditionalInsideIf_4() {
            return conditionalInsideIf( null, "foo", null );
        }

        static int conditionalInsideIf( Object a, Object b, Object c ) {
            if( (a == null ? b == null : a == b ) ) {
                return c == null ? 1 : 2;
            } else {
                return 3;
            }
        }

        @Export
        static int ifWithReturn8() {
            return ifWithReturn( 8, 0 );
        }

        @Export
        static int ifWithReturn17() {
            return ifWithReturn( 8, 13 );
        }

        @Export
        static int ifWithReturn21() {
            return ifWithReturn( 8, 5 );
        }

        @Export
        static int ifWithReturn27() {
            return ifWithReturn( 0, 0 );
        }

        @Export
        static int ifWithReturn28() {
            return ifWithReturn( 0, 1 );
        }

        private static int ifWithReturn( int a, int b ) {
            if( a > 7 ) {
                if( b > 1 ) {
                    if( b == 13 ) {
                        return 17;
                    } else {
                        return 21;
                    }
                }
            } else {
                if( a == b ) {
                    return 27;
                } else {
                    return 28;
                }
            }
            return a;
        }

        @Export
        static int stringSwitchNormalFoo() {
            return stringSwitchNormal( "foo" );
        }

        @Export
        static int stringSwitchNormalBar() {
            return stringSwitchNormal( "bar" );
        }

        @Export
        static int stringSwitchNormalDefault() {
            return stringSwitchNormal( "default" );
        }

        private static int stringSwitchNormal( String tagName ) {
            switch( tagName ) {
                case "foo":
                    return 1;
                case "bar":
                    return 2;
                default:
                    return 3;
            }
        }

        @Export
        static int stringSwitchReverseFoo() {
            return stringSwitchReverse( "foo" );
        }

        @Export
        static int stringSwitchReverseBar() {
            return stringSwitchReverse( "bar" );
        }

        @Export
        static int stringSwitchReverseDefault() {
            return stringSwitchReverse( "default" );
        }

        private static int stringSwitchReverse( String tagName ) {
            switch( tagName ) {
                default:
                    return 3;
                case "bar":
                    return 2;
                case "foo":
                    return 1;
            }
        }

        @Export
        static int stringSwitchContinue1() {
            return stringSwitchContinue( "1" );
        }

        @Export
        static int stringSwitchContinue2() {
            return stringSwitchContinue( "2" );
        }

        @Export
        static int stringSwitchContinueDefault() {
            return stringSwitchContinue( "8" );
        }

        /**
         * Strings have continue hash codes that a compiler could use a tableswitch.
         */
        private static int stringSwitchContinue( String tagName ) {
            switch( tagName ) {
                case "1":
                    return 1;
                case "2":
                    return 2;
                case "3":
                    return 3;
                case "4":
                    return 4;
                case "5":
                    return 5;
                case "6":
                    return 7;
                default:
                    return 8;
            }
        }
    }
}
