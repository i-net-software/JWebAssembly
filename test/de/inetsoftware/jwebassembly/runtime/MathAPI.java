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

import java.util.ArrayList;
import java.util.Collection;

import org.junit.ClassRule;
import org.junit.runners.Parameterized.Parameters;

import de.inetsoftware.jwebassembly.ScriptEngine;
import de.inetsoftware.jwebassembly.WasmRule;
import de.inetsoftware.jwebassembly.api.annotation.Export;

/**
 * @author Volker Berlin
 */
public class MathAPI extends AbstractBaseTest {

    @ClassRule
    public static WasmRule rule = new WasmRule( TestClass.class ); 

    public MathAPI( ScriptEngine script, String method, Object[] params ) {
        super( rule, script, method, params );
    }

    @Parameters(name="{0}-{1}")
    public static Collection<Object[]> data() {
        ArrayList<Object[]> list = new ArrayList<>();
        for( ScriptEngine[] val : ScriptEngine.testParams() ) {
            ScriptEngine script = val[0];
            addParam( list, script, "floatToIntBits" );
            addParam( list, script, "intBitsToFloat" );
            addParam( list, script, "doubleToLongBits" );
            addParam( list, script, "longBitsToDouble" );
            addParam( list, script, "sin0" );
            addParam( list, script, "sinPI" );
            addParam( list, script, "cos0" );
            addParam( list, script, "tan0" );
            addParam( list, script, "asin1" );
            addParam( list, script, "acos1" );
            addParam( list, script, "atan1" );
            addParam( list, script, "toRadians90" );
            addParam( list, script, "toDegrees1" );
            addParam( list, script, "exp1" );
            addParam( list, script, "log1" );
            addParam( list, script, "log10" );
            addParam( list, script, "cbrt8" );
            addParam( list, script, "IEEEremainder" );
            addParam( list, script, "ceil8_5" );
            addParam( list, script, "floor8_5" );
            addParam( list, script, "rint8_5" );
            addParam( list, script, "atan2_5_5" );
            addParam( list, script, "pow3_4" );
            addParam( list, script, "roundF3_5" );
            addParam( list, script, "roundF_3_5" );
            addParam( list, script, "roundD3_8" );
            addParam( list, script, "roundD_3_8" );
            addParam( list, script, "random" );
            addParam( list, script, "absF" );
            addParam( list, script, "absD" );
            addParam( list, script, "maxF" );
            addParam( list, script, "maxD" );
            addParam( list, script, "minF" );
            addParam( list, script, "minD" );
        }
        rule.setTestParameters( list );
        return list;
    }

    static class TestClass {

        @Export
        static int floatToIntBits() {
            return Float.floatToIntBits( 7 );
        }

        @Export
        static float intBitsToFloat() {
            return Float.intBitsToFloat( 0x41f8_0000 ); // 31.0
        }

        @Export
        static int doubleToLongBits() {
            return (int)Double.doubleToLongBits( 7 );
        }

        @Export
        static double longBitsToDouble() {
            return Double.longBitsToDouble( 0x41f8_0000 ); // 31.0
        }

        @Export
        static double sin0() {
            return Math.sin( 0 );
        }

        @Export
        static double sinPI() {
            return Math.sin( Math.PI / 2 );
        }

        @Export
        static double cos0() {
            return Math.cos( 0 );
        }

        @Export
        static double tan0() {
            return Math.tan( 0 );
        }

        @Export
        static double asin1() {
            return Math.asin( 1 );
        }

        @Export
        static double acos1() {
            return Math.acos( 1 );
        }

        @Export
        static double atan1() {
            return Math.atan( 1 );
        }

        @Export
        static double toRadians90() {
            return Math.toRadians( 90 );
        }

        @Export
        static double toDegrees1() {
            return Math.toDegrees( 1 );
        }

        @Export
        static double exp1() {
            return Math.exp( 1 );
        }

        @Export
        static double log1() {
            return Math.log( 1 );
        }

        @Export
        static double log10() {
            return Math.log10( 1 );
        }

        @Export
        static double sqrt() {
            return Math.sqrt( 6.25 );
        }

        @Export
        static double cbrt8() {
            return Math.cbrt( 8 );
        }

        @Export
        static double IEEEremainder() {
            return Math.IEEEremainder( 11, 3 );
        }

        @Export
        static double ceil8_5() {
            return Math.ceil( 8.5 );
        }

        @Export
        static double floor8_5() {
            return Math.floor( 8.5 );
        }

        @Export
        static double rint8_5() {
            return Math.rint( 8.5 );
        }

        @Export
        static double atan2_5_5() {
            return Math.atan2( 5, 5 );
        }

        @Export
        static double pow3_4() {
            return Math.pow( 3, 4 );
        }

        @Export
        static int roundF3_5() {
            return Math.round( 3.5F );
        }

        @Export
        static int roundF_3_5() {
            return Math.round( -3.5F );
        }

        @Export
        static int roundD3_8() {
            // SpiderMonkey does not support BigInt currently
            return (int)Math.round( 3.8 );
        }

        @Export
        static int roundD_3_8() {
            // SpiderMonkey does not support BigInt currently
            return (int)Math.round( -3.8 );
        }

        @Export
        static int random() {
            if( Math.random() < 0 ) {
                return 1;
            } else if( Math.random() > 1 ) {
                return 2;
            } else if( Math.random() == Math.random() ) {
                return 3;
            } else {
                return 4;
            }
        }

        @Export
        static float absF() {
            return Math.abs( -3.8F );
        }

        @Export
        static double absD() {
            return Math.abs( -3.8 );
        }

        @Export
        static float maxF() {
            return Math.max( -3.8F, 7.5F );
        }

        @Export
        static double maxD() {
            return Math.max( -3.8, 7.5 );
        }

        @Export
        static float minF() {
            return Math.min( -3.8F, 7.5F );
        }

        @Export
        static double minD() {
            return Math.min( -3.8, 7.5 );
        }
    }
}
