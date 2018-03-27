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
import org.junit.runners.Parameterized.Parameters;
import org.webassembly.annotation.Export;

import de.inetsoftware.jwebassembly.ScriptEngine;
import de.inetsoftware.jwebassembly.WasmRule;

public class ControlFlowOperators extends AbstractBaseTest {

    @ClassRule
    public static WasmRule rule = new WasmRule( TestClass.class );

    public ControlFlowOperators( ScriptEngine script, String method, Object[] params ) {
        super( rule, script, method, params );
    }

    @Parameters( name = "{0}-{1}" )
    public static Collection<Object[]> data() {
        ArrayList<Object[]> list = new ArrayList<>();
        for( ScriptEngine[] val : ScriptEngine.testParams() ) {
            ScriptEngine script = val[0];
            addParam( list, script, "ifeq" );
            addParam( list, script, "ifne" );
            addParam( list, script, "iflt" );
            addParam( list, script, "forLoop" );
        }
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
        static int forLoop() {
            int a = 0;
            //            for( int i=0; i<10;i++) {
            //                a += i;
            //            }
            return a;
        }
    }
}
