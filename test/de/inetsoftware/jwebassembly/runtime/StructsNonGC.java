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
import de.inetsoftware.jwebassembly.runtime.StructsGC.Abc2;
import de.inetsoftware.jwebassembly.web.JSObject;

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
            addParam( list, script, "callAbstractMethod" );
            addParam( list, script, "instanceof1" );
            addParam( list, script, "instanceof2" );
            addParam( list, script, "instanceof3" );
            addParam( list, script, "cast" );
            addParam( list, script, "objectClassName" );
            addParam( list, script, "integerClassName" );
            addParam( list, script, "classClassName" );
            addParam( list, script, "classConst" );
            addParam( list, script, "branchWithObjectResult" );
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
                val = val.abc = new Abc2();
                val.a = i;
            }
            return val.a;
        }

        @Export
        static int getDefaultValue() {
            Abc2 val = new Abc2();
            return val.getDefault();
        }

        @Export
        static int callAbstractMethod() {
            Ab val = new Abc2();
            return val.abstractBar();
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

        @Export
        static int cast() {
            Object obj = new Integer(42);
            Integer val = (Integer)obj;
            return val.intValue();
        }

        @Export
        static String objectClassName() {
            Object obj = new Object();
            Class clazz = obj.getClass();
            return JSObject.domString( clazz.getName() );
        }

        @Export
        static String integerClassName() {
            Object obj = new Integer(42);
            Class clazz = obj.getClass();
            return JSObject.domString( clazz.getName() );
        }

        @Export
        static String classClassName() {
            Object obj = new Object();
            Class clazz = obj.getClass().getClass();
            return JSObject.domString( clazz.getName() );
        }

        @Export
        static String classConst() {
            Class clazz = Float.class;
            return JSObject.domString( clazz.getName() );
        }

        @Export
        static int branchWithObjectResult() {
            Integer val1 = 42;
            Integer val2 = 7;
            Integer val = val1 == val2 ? val1 : val2;
            return val;
        }

        /**
         * To find the instruction that push the object of the method call we need to consider an IF THEN ELSE when analyzing the stack. 
         */
        @Export
        static int callParameterFromCondition() {
            Abc abc = new Abc();
            return abc.add( 42, abc == null ? 7 : 13 );
        }
    }

    interface TestDefault {
        default int getDefault() {
            return 7;
        }
    }

    static abstract class Ab {
        abstract int abstractBar();
    }

    static class Abc extends Ab implements TestDefault {
        int  a;

        long b;
        
        final void foo() {
            a = 1;
        }

        void bar() {
            a = 2;
        }

        @Override
        int abstractBar() {
            return 2;
        }

        int add( int a, int b ) {
            return a + b;
        }
    }

    static class Abc2 extends Abc {
        Abc2  abc;

        void bar() {
            a = 3;
        }

        @Override
        int abstractBar() {
            return 3;
        }
    }
}
