/*
 * Copyright 2020 Volker Berlin (i-net software)
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
import java.util.Arrays;
import java.util.Collection;
import java.util.zip.CRC32;

import org.junit.Assume;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runners.Parameterized.Parameters;

import de.inetsoftware.jwebassembly.ScriptEngine;
import de.inetsoftware.jwebassembly.WasmRule;
import de.inetsoftware.jwebassembly.api.annotation.Export;

public class MultiArrayOperations extends AbstractBaseTest {

    @ClassRule
    public static WasmRule rule = new WasmRule( TestClass.class );

    public MultiArrayOperations( ScriptEngine script, String method, Object[] params ) {
        super( rule, script, method, params );
    }

    @Parameters( name = "{0}-{1}" )
    public static Collection<Object[]> data() {
        ArrayList<Object[]> list = new ArrayList<>();

        ScriptEngine[] engines = ScriptEngine.testEngines();
        for( ScriptEngine script : engines ) {
            addParam( list, script, "multi" );
        }
        rule.setTestParameters( list );
        return list;
    }

    @Test
    @Override
    public void test() {
        super.test();
    }

    static class TestClass {

        @Export
        static int multi() {
            int[][][] val = new int[1][2][3]; 
            return val.length;
        }
    }
}
