/*
 * Copyright 2018 - 2021 Volker Berlin (i-net software)
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

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.zip.CRC32;

import org.junit.Assume;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runners.Parameterized.Parameters;
import org.w3c.dom.DOMStringList;

import de.inetsoftware.jwebassembly.ScriptEngine;
import de.inetsoftware.jwebassembly.WasmRule;
import de.inetsoftware.jwebassembly.api.annotation.Export;
import de.inetsoftware.jwebassembly.web.DOMString;
import de.inetsoftware.jwebassembly.web.JSObject;

public class ArrayOperations extends AbstractBaseTest {

    @ClassRule
    public static WasmRule rule = new WasmRule( TestClass.class );

    public ArrayOperations( ScriptEngine script, String method, Object[] params ) {
        super( rule, script, method, params );
    }

    @Parameters( name = "{0}-{1}" )
    public static Collection<Object[]> data() {
        ArrayList<Object[]> list = new ArrayList<>();

        ScriptEngine[] engines = ScriptEngine.testEngines();
        engines = Arrays.copyOf( engines, engines.length + 2 );
        engines[engines.length - 2] = ScriptEngine.SpiderMonkeyGC;
        engines[engines.length - 1] = ScriptEngine.SpiderMonkeyWatGC;
        for( ScriptEngine script : engines ) {
            addParam( list, script, "length" );
            addParam( list, script, "loopByte" );
            addParam( list, script, "loopShort" );
            addParam( list, script, "loopChar" );
            addParam( list, script, "loopInt" );
            addParam( list, script, "loopLong" );
            addParam( list, script, "loopFloat" );
            addParam( list, script, "loopDouble" );
            addParam( list, script, "loopObject" );
            addParam( list, script, "copyBack2Front" );
            addParam( list, script, "copyFront2Back" );
            addParam( list, script, "dup_x2" );
            addParam( list, script, "byteArrayClassName" );
            addParam( list, script, "shortArrayClassName" );
            addParam( list, script, "charArrayClassName" );
            addParam( list, script, "intArrayClassName" );
            addParam( list, script, "longArrayClassName" );
            addParam( list, script, "floatArrayClassName" );
            addParam( list, script, "doubleArrayClassName" );
            addParam( list, script, "booleanArrayClassName" );
            addParam( list, script, "objectArrayClassName" );
            addParam( list, script, "stringArrayClassName" );
            addParam( list, script, "stringMatrixClassName" );
            addParam( list, script, "byteArrayComponentTypeClassName" );
            addParam( list, script, "shortArrayComponentTypeClassName" );
            addParam( list, script, "charArrayComponentTypeClassName" );
            addParam( list, script, "intArrayComponentTypeClassName" );
            addParam( list, script, "longArrayComponentTypeClassName" );
            addParam( list, script, "floatArrayComponentTypeClassName" );
            addParam( list, script, "doubleArrayComponentTypeClassName" );
            addParam( list, script, "booleanArrayComponentTypeClassName" );
            addParam( list, script, "objectArrayComponentTypeClassName" );
            addParam( list, script, "arrayNewInstance_getLength" );
            addParam( list, script, "isArrayOfArray" );
            addParam( list, script, "isArrayOfObject" );
        }
        rule.setTestParameters( list );
        return list;
    }

    @Test
    @Override
    public void test() {
        Assume.assumeFalse( getScriptEngine().name().endsWith( "GC" ) );
        super.test();
    }

    static class TestClass {

        @Export
        static int length() {
            return new int[5].length;
        }

        @Export
        static byte loopByte() {
            byte[] data = {-1,2,3};
            byte sum = 0;
            for( byte i : data ) {
                sum += i;
            }
            return sum;
        }

        @Export
        static short loopShort() {
            short[] data = {-1,-2,-3};
            short sum = 0;
            for( short i : data ) {
                sum += i;
            }
            return sum;
        }

        @Export
        static char loopChar() {
            char[] data = {1,2,0x8000};
            char sum = 0;
            for( char i : data ) {
                sum += i;
            }
            return sum;
        }

        @Export
        static int loopInt() {
            int[] data = {1,2,3};
            int sum = 0;
            for( int i : data ) {
                sum += i;
            }
            return sum;
        }

        @Export
        static int loopLong() {
            long[] data = {1,2,3};
            long sum = 0;
            for( long i : data ) {
                sum += i;
            }
            return (int)sum;
        }

        @Export
        static float loopFloat() {
            float[] data = {1,2,3};
            float sum = 0;
            for( float i : data ) {
                sum += i;
            }
            return sum;
        }

        @Export
        static double loopDouble() {
            double[] data = {1,2,3};
            double sum = 0;
            for( double i : data ) {
                sum += i;
            }
            return sum;
        }

        @Export
        static int loopObject() {
            Object[] data = {null,null,null};
            int sum = 0;
            for( Object obj : data ) {
                sum++;
            }
            return sum;
        }

        @Export
        static double copyBack2Front() {
            byte[] a = new byte[50];
            for( int i = 0; i < a.length; i++ ) {
                a[i] = (byte)i;
            }

            System.arraycopy( a, 20, a, 15, 10 );
            CRC32 crc = new CRC32();
            crc.update( a );
            return crc.getValue();
        }

        @Export
        static double copyFront2Back() {
            byte[] a = new byte[50];
            for( int i = 0; i < a.length; i++ ) {
                a[i] = (byte)i;
            }

            System.arraycopy( a, 15, a, 20, 10 );
            CRC32 crc = new CRC32();
            crc.update( a );
            return crc.getValue();
        }

        @Export
        static int dup_x2() {
            Object[] data = {null,null,null};
            int index = 1;
            Object value = null;

            if ((data[index] = value) != null) { // test for instruction dup_x2
                return 1;
            } else {
                return 2;
            }
        }

        @Export
        static DOMString byteArrayClassName() {
            return JSObject.domString( new byte[0].getClass().getName() );
        }

        @Export
        static DOMString shortArrayClassName() {
            return JSObject.domString( new short[0].getClass().getName() );
        }

        @Export
        static DOMString charArrayClassName() {
            return JSObject.domString( new char[0].getClass().getName() );
        }

        @Export
        static DOMString intArrayClassName() {
            return JSObject.domString( new int[0].getClass().getName() );
        }

        @Export
        static DOMString longArrayClassName() {
            return JSObject.domString( new long[0].getClass().getName() );
        }

        @Export
        static DOMString floatArrayClassName() {
            return JSObject.domString( new float[0].getClass().getName() );
        }

        @Export
        static DOMString doubleArrayClassName() {
            return JSObject.domString( new double[0].getClass().getName() );
        }

        @Export
        static DOMString booleanArrayClassName() {
            return JSObject.domString( new boolean[0].getClass().getName() );
        }

        @Export
        static DOMString objectArrayClassName() {
            return JSObject.domString( new Object[0].getClass().getName() );
        }

        @Export
        static DOMString stringArrayClassName() {
            return JSObject.domString( new String[0].getClass().getName() );
        }

        @Export
        static DOMString stringMatrixClassName() {
            return JSObject.domString( new String[0][].getClass().getName() );
        }
        
        @Export
        static DOMString byteArrayComponentTypeClassName() {
            return JSObject.domString( new byte[0].getClass().getComponentType().getName() );
        }

        @Export
        static DOMString shortArrayComponentTypeClassName() {
            return JSObject.domString( new short[0].getClass().getComponentType().getName() );
        }

        @Export
        static DOMString charArrayComponentTypeClassName() {
            return JSObject.domString( new char[0].getClass().getComponentType().getName() );
        }

        @Export
        static DOMString intArrayComponentTypeClassName() {
            return JSObject.domString( new int[0].getClass().getComponentType().getName() );
        }

        @Export
        static DOMString longArrayComponentTypeClassName() {
            return JSObject.domString( new long[0].getClass().getComponentType().getName() );
        }

        @Export
        static DOMString floatArrayComponentTypeClassName() {
            return JSObject.domString( new float[0].getClass().getComponentType().getName() );
        }

        @Export
        static DOMString doubleArrayComponentTypeClassName() {
            return JSObject.domString( new double[0].getClass().getComponentType().getName() );
        }

        @Export
        static DOMString booleanArrayComponentTypeClassName() {
            return JSObject.domString( new boolean[0].getClass().getComponentType().getName() );
        }

        @Export
        static DOMString objectArrayComponentTypeClassName() {
            return JSObject.domString( new Object[0].getClass().getComponentType().getName() );
        }

        @Export
        static int arrayNewInstance_getLength() {
            Object obj = Array.newInstance( byte.class, 42 );
            return Array.getLength( obj );
        }

        @Export
        static boolean isArrayOfArray() {
            Object obj = new Object[42];
            Class<?> clazz = obj.getClass();
            return clazz.isArray();
        }

        @Export
        static boolean isArrayOfObject() {
            Object obj = new Object();
            Class<?> clazz = obj.getClass();
            return clazz.isArray();
        }
    }
}
