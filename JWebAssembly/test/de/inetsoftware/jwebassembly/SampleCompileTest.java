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

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayList;
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

    private final File classFile;

    public SampleCompileTest( String testname, File classFile ) {
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
                params.add( new Object[] { file.getPath().substring( baseLength ), file } );
            } else if( file.isDirectory() ) {
                params( params, file, baseLength );
            }
        }
    }

    @Test
    public void sExpressions() throws Exception {
        String basename = classFile.getName();
        basename = basename.substring( 0, basename.length() - 6 );
        String watname = basename + ".wat";
        File watFile = new File( classFile.getParent(), watname );
        String expected = new String( Files.readAllBytes( watFile.toPath() ) );
        JWebAssembly webAsm = new JWebAssembly();
        webAsm.addFile( classFile );
        String text = webAsm.compileToText();
        assertEquals( expected, text );
    }
}
