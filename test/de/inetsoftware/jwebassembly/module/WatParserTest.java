/*
 * Copyright 2018 - 2019 Volker Berlin (i-net software)
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
package de.inetsoftware.jwebassembly.module;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.HashMap;

import javax.annotation.Nullable;

import org.junit.Test;

import de.inetsoftware.jwebassembly.WasmException;
import de.inetsoftware.jwebassembly.text.TextModuleWriter;
import de.inetsoftware.jwebassembly.watparser.WatParser;

/**
 * @author Volker Berlin
 */
public class WatParserTest {

    private void test( String wat ) throws IOException {
        WatParser parser = new WatParser();
        parser.parse( wat, 100 );
        WasmCodeBuilder codeBuilder = parser;
        StringBuilder builder = new StringBuilder();
        ModuleWriter writer = new TextModuleWriter( new WasmTarget( builder ), new HashMap<>() );
        writer.writeMethodStart( new FunctionName( "A.a()V" ), null );
        for( WasmInstruction instruction : codeBuilder.getInstructions() ) {
            instruction.writeTo( writer );
        }
        writer.writeMethodFinish();
        writer.close();
        String expected = normalize( "(module (func $A.a " + wat + " ) )" );
        String actual = normalize( builder );
        assertEquals( expected, actual );
    }

    private String normalize( @Nullable CharSequence str ) {
        boolean wasSpace = false;
        StringBuilder builder = new StringBuilder();
        for( int i = 0; i < str.length(); i++ ) {
            char ch = str.charAt( i );
            switch( ch ) {
                case ' ':
                case '\n':
                case '\r':
                case '\t':
                    if( !wasSpace ) {
                        builder.append( ' ' );
                        wasSpace = true;
                    }
                    break;
                default:
                    builder.append( ch );
                    wasSpace = false;
            }
        }
        return builder.toString();
    }

    private void testError( String wat, String errorMessage ) throws IOException {
        try {
            test( wat );
            fail( "Exception expected with message: " + errorMessage );
        } catch( WasmException ex ) {
            String error = ex.getMessage();
            int newlineIdx = error.indexOf( '\n' );
            if( newlineIdx > 0 ) {
                error = error.substring( 0, newlineIdx );
            }
            assertEquals( errorMessage, error );
        }
    }

    @Test
    public void Local_get() throws IOException {
        test( "local.get 1" );
    }

    @Test
    public void Local_set() throws IOException {
        test( "local.set 2" );
    }

    @Test
    public void i32_add() throws IOException {
        test( "i32.add" );
    }

    @Test
    public void i32_const() throws IOException {
        test( " i32.const -7 " );
    }

    @Test
    public void i32_trunc_sat_f32_s() throws IOException {
        test( "i32.trunc_sat_f32_s" );
    }

    @Test
    public void i64_extend_i32_s() throws IOException {
        test( "i64.extend_i32_s" );
    }

    @Test
    public void i64_trunc_sat_f64_s() throws IOException {
        test( "i64.trunc_sat_f64_s" );
    }

    @Test
    public void f32_convert_i32_s() throws IOException {
        test( "f32.convert_i32_s" );
    }

    @Test
    public void f32_div() throws IOException {
        test( "f32.div" );
    }

    @Test
    public void f32_max() throws IOException {
        test( "f32.max" );
    }

    @Test
    public void f32_mul() throws IOException {
        test( "f32.mul" );
    }

    @Test
    public void f32_sub() throws IOException {
        test( "f32.sub" );
    }

    @Test
    public void f64_convert_i64_s() throws IOException {
        test( "f64.convert_i64_s" );
    }

    @Test
    public void f64_div() throws IOException {
        test( "f64.div" );
    }

    @Test
    public void f64_max() throws IOException {
        test( "f64.max" );
    }

    @Test
    public void f64_mul() throws IOException {
        test( "f64.mul" );
    }

    @Test
    public void f64_sub() throws IOException {
        test( "f64.sub" );
    }

    @Test
    public void return_() throws IOException {
        test( "return\n" );
    }

    @Test
    public void errorMissingToken() throws IOException {
        testError( "i32.const", "Missing Token in wasm text format after token: i32.const" );
    }

    @Test
    public void errorUnknownToken() throws IOException {
        testError( "foobar", "Unknown WASM token: foobar" );
    }
}
