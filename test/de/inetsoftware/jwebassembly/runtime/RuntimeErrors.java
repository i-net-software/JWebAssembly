/*
 * Copyright 2017 - 2018 Volker Berlin (i-net software)
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

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.Collection;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import de.inetsoftware.jwebassembly.ScriptEngine;
import de.inetsoftware.jwebassembly.WasmException;
import de.inetsoftware.jwebassembly.WasmRule;
import de.inetsoftware.jwebassembly.api.annotation.Export;
import de.inetsoftware.jwebassembly.api.annotation.Import;

/**
 * @author Volker Berlin
 */
@RunWith( Parameterized.class )
public class RuntimeErrors {

    private final ScriptEngine script;

    public RuntimeErrors( ScriptEngine script ) {
        this.script = script;
    }

    @Parameters( name = "{0}" )
    public static Collection<ScriptEngine[]> data() {
        return ScriptEngine.testParams();
    }

    private void compileErrorTest( String expectedMessge, Class<?> classes ) throws IOException {
        WasmRule wasm = new WasmRule( classes );
        try {
            wasm.compile();
            fail( "Exception expected with: " + expectedMessge );
        } catch( WasmException ex ) {
            assertTrue( "Wrong error message: " + ex.getMessage(), ex.getMessage().contains( expectedMessge ) );
        } finally {
            wasm.delete();
        }

    }

    @Test
    public void nonStaticExport() throws IOException {
        compileErrorTest( "Export method must be static:", NonStaticExport.class );
    }

    static class NonStaticExport {
        @Export
        float function() {
            return 1;
        }
    }


    @Test
    public void nonStaticImport() throws IOException {
        compileErrorTest( "Import method must be static:", NonStaticImport.class );
    }

    static class NonStaticImport {
        @Import( module = "m", name = "n" )
        float function() {
            return 1;
        }
    }

    @Test
    public void nativeMethod() throws IOException {
        compileErrorTest( "Abstract or native method can not be used:", NativeMethod.class );
    }

    static class NativeMethod {
        @Export
        native static float function();
    }
}
