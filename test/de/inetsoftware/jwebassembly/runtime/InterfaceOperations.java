/*
 * Copyright 2020 - 2021 Volker Berlin (i-net software)
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

public class InterfaceOperations extends AbstractBaseTest {

    @ClassRule
    public static WasmRule rule = new WasmRule( TestClass.class );

    public InterfaceOperations( ScriptEngine script, String method, Object[] params ) {
        super( rule, script, method, params );
    }

    @Parameters( name = "{0}-{1}" )
    public static Collection<Object[]> data() {
        ArrayList<Object[]> list = new ArrayList<>();
        ScriptEngine[] engines = ScriptEngine.testEngines();
        for( ScriptEngine script : engines ) {
            addParam( list, script, "getDefaultValue" );
            addParam( list, script, "getDefaultValue2" );
            addParam( list, script, "getDefaultOverride" );
            addParam( list, script, "getDefaultOverride2" );
            addParam( list, script, "getDefaultExtends" );
            addParam( list, script, "getDefaultReimplement" );
            addParam( list, script, "getDefaultRedefinied" );
            addParam( list, script, "getDefaultMultiImpl1" );
            addParam( list, script, "getDefaultMultiImpl2" );
            addParam( list, script, "abstractParent" );
        }
        rule.setTestParameters( list );
        return list;
    }

    static class TestClass {

        @Export
        static int getDefaultValue() {
            BarImpl bar = new BarImpl();
            return bar.getDefaultValue();
        }

        @Export
        static int getDefaultValue2() {
            Bar bar = new BarImpl();
            return bar.getDefaultValue();
        }

        @Export
        static int getDefaultOverride() {
            BarOverride bar = new BarOverride();
            return bar.getDefaultValue();
        }

        @Export
        static int getDefaultOverride2() {
            Bar bar = new BarOverride();
            return bar.getDefaultValue();
        }

        @Export
        static int getDefaultExtends() {
            BarImpl bar = new BarExtends();
            return bar.getDefaultValue();
        }

        @Export
        static int getDefaultReimplement() {
            BarImpl bar = new BarReimplement();
            return bar.getDefaultValue();
        }

        @Export
        static int getDefaultRedefinied() {
            Bar bar = new Bar2() {};
            return bar.getDefaultValue();
        }

        interface Bar {
            default int getDefaultValue() {
                return 7;
            }
        }

        static class BarImpl implements Bar {

        }

        static class BarOverride implements Bar {
            public int getDefaultValue() {
                return 13;
            }
        }

        static class BarExtends extends BarImpl {
            public int getDefaultValue() {
                return 42;
            }
        }

        static class BarReimplement extends BarExtends implements Bar {
        }

        interface Bar2 extends Bar {
            default int getDefaultValue() {
                return 99;
            }
        }

        @Export
        static int getDefaultMultiImpl1() {
            Bar bar = new BarMultiImpl1() {};
            return bar.getDefaultValue();
        }

        @Export
        static int getDefaultMultiImpl2() {
            Bar bar = new BarMultiImpl2() {};
            return bar.getDefaultValue();
        }

        static class BarMultiImpl1 implements Bar2, Bar3 {
        }

        static class BarMultiImpl2 implements Bar3, Bar2 {
        }

        interface Bar3 extends Bar {
        }

        @Export
        static int abstractParent() {
            Foo foo = new FooAdder() {
                @Override
                public int val42() {
                    return 42;
                }
                @Override
                public int echo( int val ) {
                    return val;
                }
            };
            int val = foo.val42();
            val = foo.echo( val );
            val = foo.add( val, 7 );
            return val;
        }

        static interface Foo {
            int val42();

            int echo( int val );

            int add( int a, int b );
        }

        static abstract class FooAdder implements Foo {
            @Override
            public int add( int a, int b ) {
                return a + b;
            }
        }
    }
}
