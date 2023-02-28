/*
 * Copyright 2023 Volker Berlin (i-net software)
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
public class ThreadTest extends AbstractBaseTest {

    @ClassRule
    public static WasmRule rule = new WasmRule( TestClass.class );

    public ThreadTest( ScriptEngine script, String method, Object[] params ) {
        super( rule, script, method, params );
    }

    @Parameters( name = "{0}-{1}" )
    public static Collection<Object[]> data() {
        ArrayList<Object[]> list = new ArrayList<>();
        for( ScriptEngine script : ScriptEngine.testEngines() ) {
            addParam( list, script, "currentThreadName" );
            addParam( list, script, "groupThreadName" );
        }
        rule.setTestParameters( list );
        return list;
    }

    static class TestClass {

        @Export
        static DOMString currentThreadName() {
            Thread thread = Thread.currentThread();
            String name = thread.getName();
            return JSObject.domString( name );
        }

        @Export
        static DOMString groupThreadName() {
            Thread thread = Thread.currentThread();
            ThreadGroup group = thread.getThreadGroup();
            String name;
            do {
                name = group.getName();
                group = group.getParent();
            } while( group != null );
            return JSObject.domString( name );
        }
    }
}
