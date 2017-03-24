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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;

import de.inetsoftware.classparser.ClassFile;
import de.inetsoftware.jwebassembly.module.ModuleWriter;
import de.inetsoftware.jwebassembly.text.TextModuleWriter;

/**
 * The main class of the compiler.
 * 
 * @author Volker Berlin
 */
public class JWebAssembly {

    private List<File> classFiles = new ArrayList<File>();

    /**
     * Create a instance.
     */
    public JWebAssembly() {
    }

    /**
     * Add a classFile to compile
     * 
     * @param classFile
     *            the file
     */
    public void addFile( @Nonnull File classFile ) {
        classFiles.add( classFile );
    }

    /**
     * Convert the added files to a WebAssembly module in text representation.
     * 
     * @return the module as string
     * @throws WasmException
     *             if any conversion error occurs
     */
    public String compileToText() throws WasmException {
        StringBuilder output = new StringBuilder();
        compileToText( output );
        return output.toString();
    }

    /**
     * Convert the added files to a WebAssembly module in text representation.
     * 
     * @param output
     *            the target for the module data
     * @throws WasmException
     *             if any conversion error occurs
     */
    public void compileToText( Appendable output ) throws WasmException {
        try (TextModuleWriter writer = new TextModuleWriter( output )) {
            compile( writer );
        } catch( Exception ex ) {
            throw WasmException.create( ex );
        }
    }

    /**
     * Convert the added files to a WebAssembly module.
     * 
     * @param writer
     *            the formatter
     * @throws IOException
     *             if any I/O error occur
     * @throws WasmException
     *             if any conversion error occurs
     */
    private void compile( ModuleWriter writer ) throws IOException, WasmException {
        for( File file : classFiles ) {
            ClassFile classFile = new ClassFile( new BufferedInputStream( new FileInputStream( file ) ) );
            writer.write( classFile );
        }
    }
}
