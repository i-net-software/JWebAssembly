/*
 * Copyright 2018 - 2020 Volker Berlin (i-net software)
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
import java.util.LinkedList;
import java.util.List;

import org.junit.ClassRule;
import org.junit.runners.Parameterized.Parameters;

import de.inetsoftware.jwebassembly.ScriptEngine;
import de.inetsoftware.jwebassembly.WasmRule;
import de.inetsoftware.jwebassembly.api.annotation.Export;
import de.inetsoftware.jwebassembly.api.annotation.Import;

public class StructsNonGC extends AbstractBaseTest {

    @ClassRule
    public static WasmRule rule = new WasmRule( TestClass.class, Abc.class );

    public StructsNonGC( ScriptEngine script, String method, Object[] params ) {
        super( rule, script, method, params );
    }

    @Parameters( name = "{0}-{1}" )
    public static Collection<Object[]> data() {
        ArrayList<Object[]> list = new ArrayList<>();
        ScriptEngine[] engines = ScriptEngine.testEngines();
        for( ScriptEngine script : engines ) {
            addParam( list, script, "isNull" );
            addParam( list, script, "isNotNull" );
            addParam( list, script, "isSame" );
            addParam( list, script, "isNotSame" );
            addParam( list, script, "simple" );
            addParam( list, script, "callSuperMethod" );
            addParam( list, script, "callVirtualMethod" );
            addParam( list, script, "useGlobalObject" );
            addParam( list, script, "multipleAssign" );
            addParam( list, script, "getDefaultValue" );
            addParam( list, script, "instanceof1" );
            addParam( list, script, "instanceof2" );
            addParam( list, script, "instanceof3" );
        }
        rule.setTestParameters( list );
        return list;
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
        static boolean isSame() {
            Object val1 = null;
            Object val2 = null;
            return val1 == val2;
        }

        @Export
        static boolean isNotSame() {
            Object val1 = null;
            Object val2 = null;
            return val1 != val2;
        }

        @Export
        static int simple() {
            Abc val = new Abc2();
            val.a = 63;
            return val.a;
        }

        /**
         * Call a method that is declared in the super class of the instance 
         */
        @Export
        static int callSuperMethod() {
            Abc2 val = new Abc2();
            val.foo();
            return val.a;
        }
        @Import( js = "(i) => {console.log('log ' + i)}" )
        public static void print(int i) {
        }

        /**
         * Call an overridden method
         */
        @Export
        static int callVirtualMethod() {
            Abc val = new Abc2();
            val.bar();
            return val.a;
        }

        /**
         * Access a object in a global/static variable.
         */
        static Abc2 valGlobal;
        @Export
        static int useGlobalObject() {
            valGlobal = new Abc2();
            valGlobal.foo();
            return valGlobal.a;
        }

        /**
         * Assign multiple with a field. There are complex stack operation
         */
        @Export
        static int multipleAssign() {
            Abc2 val = new Abc2();
            for( int i = 0; i < 1_000; i++ ) {
                val.a = 42;
                // TODO
                //val = val.abc = new Abc2();
            }
            return val.a;
        }

        @Export
        static int getDefaultValue() {
            Abc2 val = new Abc2();
            return val.getDefault();
        }

        @Export
        static boolean instanceof1() {
            Object obj = new Object();
            return obj instanceof Integer;
        }

        @Export
        static boolean instanceof2() {
            Object obj = new Object();
            return obj instanceof Object;
        }

        @Export
        static boolean instanceof3() {
            Object obj = new LinkedList();
            return obj instanceof List;
        }
    }

    interface TestDefault {
        default int getDefault() {
            return 7;
        }
    }

    static class Abc implements TestDefault {
        int  a;

        long b;
        
        final void foo() {
            a = 1;
        }

        void bar() {
            a = 2;
        }
    }

    static class Abc2 extends Abc {
        Abc2  abc;

        void bar() {
            a = 3;
        }
    }
}
