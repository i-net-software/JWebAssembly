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
package de.inetsoftware.jwebassembly.math;

import java.util.ArrayList;
import java.util.Arrays;
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
public class MathOperations {
    
    @ClassRule
    public static WasmRule rule = new WasmRule( TestClass.class ); 

    private final ScriptEngine script;
    private final String method;
    private final Object[] params;

    public MathOperations( ScriptEngine script, String method, Object[] params ) {
        this.script = script;
        this.method = method;
        this.params = params;
    }

    @Parameters(name="{0}-{1}")
    public static Collection<Object[]> data() {
        ArrayList<Object[]> list = new ArrayList<>();
        for( Object[] val : ScriptEngine.testParams() ) {
            ScriptEngine script = (ScriptEngine)val[0];
            addParam( list, script, "intConst" );
            addParam( list, script, "floatConst" );
            addParam( list, script, "doubleConst" );
            addParam( list, script, "addInt", 1, 3 );
            addParam( list, script, "addLong" );
            addParam( list, script, "addFloat", 1F, 3.5F );
            addParam( list, script, "addDouble", 1.0, 3.5 );
        }
        return list;
    }

    private static void addParam( ArrayList<Object[]> list, ScriptEngine script, String method, Object ...params ) {
        list.add( new Object[]{script, method, params} );
    }

    @Test
    public void test() {
        rule.test( script, method, params );
    }

    static class TestClass {

        @Export
        static int intConst() {
            return 42;
        }

        @Export
        static float floatConst() {
            return 42.5F;
        }

        @Export
        static double doubleConst() {
            return 42.5;
        }

        @Export
        static int addInt( int a, int b ) {
            return a + b;
        }

        @Export
        static int addLong() {
            long a = 1L;
            long b = 3L;
            return (int)(a + b);
        }

        @Export
        static float addFloat( float a, float b ) {
            return a + b;
        }

        @Export
        static double addDouble( double a, double b ) {
            return a + b;
        }
    }
}
