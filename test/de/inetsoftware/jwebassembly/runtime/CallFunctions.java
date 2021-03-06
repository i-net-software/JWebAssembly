/*
 * Copyright 2017 - 2019 Volker Berlin (i-net software)
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
import de.inetsoftware.jwebassembly.api.annotation.Import;
import de.inetsoftware.jwebassembly.api.annotation.WasmTextCode;

/**
 * @author Volker Berlin
 */
public class CallFunctions extends AbstractBaseTest {
    
    @ClassRule
    public static WasmRule rule = new WasmRule( TestClass.class ); 

    public CallFunctions( ScriptEngine script, String method, Object[] params ) {
        super( rule, script, method, params );
    }

    @Parameters(name="{0}-{1}")
    public static Collection<Object[]> data() {
        ArrayList<Object[]> list = new ArrayList<>();
        for( ScriptEngine script : ScriptEngine.testEngines() ) {
            addParam( list, script, "intCall" );
            addParam( list, script, "nativeCall" );
        }
        rule.setTestParameters( list );
        return list;
    }

    static class TestClass {

        @Export
        static int intCall() {
            intConst();
            doubleConst();
            emptyMethod();
            return abc( 4,5) + intConst() * 100;
        }

        static int intConst() {
            return -42;
        }

        static double doubleConst() {
            return 3.5;
        }

        static void emptyMethod() {

        }

        @Import( module = "Math", name = "max" )
        static int abc( int a, int b) {
            return Math.max( a, b );
        }

        @Export
        static float nativeCall() {
            return nativeMax( 4.5F,5.5F);
        }

        @WasmTextCode( "local.get 0 " //
                        + "local.get 1 " //
                        + "f32.max " //
                        + "return" )
        private static float nativeMax( float a, float b) {
            return Math.max( a, b );
        }
    }
}
