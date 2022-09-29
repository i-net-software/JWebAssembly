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
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.ClassRule;
import org.junit.runners.Parameterized.Parameters;

import de.inetsoftware.jwebassembly.ScriptEngine;
import de.inetsoftware.jwebassembly.WasmRule;
import de.inetsoftware.jwebassembly.api.annotation.Export;

/**
 * @author Volker Berlin
 */
public class UnsafeTest extends AbstractBaseTest {

    @ClassRule
    public static WasmRule rule = new WasmRule( TestClass.class );

    public UnsafeTest( ScriptEngine script, String method, Object[] params ) {
        super( rule, script, method, params );
    }

    @Parameters( name = "{0}-{1}" )
    public static Collection<Object[]> data() {
        ArrayList<Object[]> list = new ArrayList<>();
        for( ScriptEngine script : ScriptEngine.testEngines() ) {
            addParam( list, script, "compareAndSwapInt" );
            addParam( list, script, "getAndAddInt" );
            addParam( list, script, "getAndSetInt" );
            addParam( list, script, "lazySet" );
        }
        rule.setTestParameters( list );
        return list;
    }

    static class TestClass {

        @Export
        static int compareAndSwapInt() {
            AtomicInteger obj = new AtomicInteger();
            if( obj.compareAndSet( 0, 25 ) ) {
                return obj.get();
            } else {
                return 42;
            }
        }

        @Export
        static int getAndAddInt() {
            AtomicInteger obj = new AtomicInteger();
            obj.set( 13 );
            if( obj.getAndAdd( 25 ) == 13 ) {
                return obj.get();
            } else {
                return 42;
            }
        }

        @Export
        static int getAndSetInt() {
            AtomicInteger obj = new AtomicInteger();
            obj.set( 13 );
            if( obj.getAndSet( 25 ) == 13 ) {
                return obj.get();
            } else {
                return 42;
            }
        }

        @Export
        static int lazySet() {
            AtomicInteger obj = new AtomicInteger();
            obj.lazySet( 42 );
            return obj.get();
        }
    }
}
