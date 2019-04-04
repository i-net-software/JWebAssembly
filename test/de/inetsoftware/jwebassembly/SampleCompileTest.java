/*
 * Copyright 2017 Volker Berlin (i-net software)
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
package de.inetsoftware.jwebassembly;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/**
 * Test to compile a list of single sample files.
 * 
 * @author Volker Berlin
 *
 */
@RunWith( Parameterized.class )
public class SampleCompileTest {

    private final String testName;
    private final File classFile;

    public SampleCompileTest( String testName, File classFile ) {
        this.testName = testName;
        this.classFile = classFile;
    }

    @Parameters( name = "{0}" )
    public static List<Object[]> params() throws Exception {
        ArrayList<Object[]> params = new ArrayList<>();
        URL url = SampleCompileTest.class.getResource( "samples" );
        File dir = new File( url.toURI() );
        int baseLength = dir.getPath().length() + 1;
        params( params, dir, baseLength );
        return params;
    }

    private static void params( ArrayList<Object[]> params, File dir, int baseLength ) {
        for( File file : dir.listFiles() ) {
            if( file.getName().endsWith( ".class" ) ) {
                String path = file.getPath();
                params.add( new Object[] { path.substring( baseLength, path.length() - 6 ), file } );
            } else if( file.isDirectory() ) {
                params( params, file, baseLength );
            }
        }
    }

    @Test
    public void compileToText() throws Exception {
        URL url = SampleCompileTest.class.getResource( "samples/" + testName + ".wat" );
        File watFile = new File( url.toURI() );
        String expected = new String( Files.readAllBytes( watFile.toPath() ) );
        JWebAssembly webAsm = new JWebAssembly();
        webAsm.addFile( classFile );
        String text = webAsm.compileToText();
        assertEquals( expected, text );
    }

    @Test
    public void compileToBinary() throws Exception {
        JWebAssembly webAsm = new JWebAssembly();
        webAsm.addFile( classFile );
        byte[] actual = webAsm.compileToBinary();
        byte[] expected = {0, 97, 115, 109, 1, 0, 0, 0};
        actual = Arrays.copyOf( actual, 8 );
        assertArrayEquals( expected, actual );
    }

    @Test
    public void npe() throws Exception {
        JWebAssembly webAsm = new JWebAssembly();
        webAsm.addFile( classFile );
        try {
            webAsm.compileToBinary( (OutputStream)null );
            fail();
        } catch( WasmException ex ) {
            // expected
            assertTrue( "" + ex.getCause(), ex.getCause() instanceof NullPointerException );
        }
    }

    @Test
    public void binaryIoError() throws Exception {
        JWebAssembly webAsm = new JWebAssembly();
        webAsm.addFile( classFile );
        File tempDir = Files.createTempDirectory( null ).toFile(); 
        try {
            webAsm.compileToBinary( tempDir );
            fail();
        } catch( WasmException ex ) {
            // expected
            tempDir.delete();
            assertTrue( "" + ex.getCause(), ex.getCause() instanceof IOException );
            assertEquals( -1, ex.getLineNumber() );
        }
    }

    @Test
    public void textIoError() throws Exception {
        JWebAssembly webAsm = new JWebAssembly();
        webAsm.addFile( classFile );
        File tempDir = Files.createTempDirectory( null ).toFile(); 
        try {
            webAsm.compileToText( tempDir );
            fail();
        } catch( WasmException ex ) {
            // expected
            tempDir.delete();
            assertTrue( "" + ex.getCause(), ex.getCause() instanceof IOException );
            assertEquals( -1, ex.getLineNumber() );
        }
    }
}
