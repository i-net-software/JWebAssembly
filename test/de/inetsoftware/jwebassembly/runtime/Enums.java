/*
 * Copyright 2022 Volker Berlin (i-net software)
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
import de.inetsoftware.jwebassembly.web.DOMString;
import de.inetsoftware.jwebassembly.web.JSObject;

/**
 * @author Volker Berlin
 */
public class Enums extends AbstractBaseTest {

    @ClassRule
    public static WasmRule rule = new WasmRule( TestClass.class );

    public Enums( ScriptEngine script, String method, Object[] params ) {
        super( rule, script, method, params );
    }

    @Parameters( name = "{0}-{1}" )
    public static Collection<Object[]> data() {
        ArrayList<Object[]> list = new ArrayList<>();
        for( ScriptEngine script : ScriptEngine.testEngines() ) {
            addParam( list, script, "ordinal" );
            addParam( list, script, "name" );
            addParam( list, script, "values" );
            addParam( list, script, "valueOf" );
        }
        rule.setTestParameters( list );
        return list;
    }

    static class TestClass {

        @Export
        static int ordinal() {
            return TestEnum.Foobar.ordinal();
        }

        @Export
        static DOMString name() {
            return JSObject.domString( TestEnum.Foobar.name() );
        }

        @Export
        static DOMString values() {
            StringBuilder builder = new StringBuilder();
            for( TestEnum testEnum : TestEnum.values() ) {
                builder.append( testEnum.name() );
            }
            return JSObject.domString( builder.toString() );
        }

        @Export
        static DOMString valueOf() {
            return JSObject.domString( TestEnum.valueOf( "Bar" ).toString() );
        }
    }

    static enum TestEnum {
        Foo, Bar, Foobar;
    }
}
