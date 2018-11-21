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
package de.inetsoftware.jwebassembly;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.annotation.Nonnull;

import de.inetsoftware.classparser.ClassFile;
import de.inetsoftware.jwebassembly.binary.BinaryModuleWriter;
import de.inetsoftware.jwebassembly.module.ModuleGenerator;
import de.inetsoftware.jwebassembly.module.ModuleWriter;
import de.inetsoftware.jwebassembly.text.TextModuleWriter;

/**
 * The main class of the compiler.
 * 
 * @author Volker Berlin
 */
public class JWebAssembly {

    private final List<URL>               classFiles = new ArrayList<>();

    private final HashMap<String, String> properties = new HashMap<>();

    private final List<URL>               libraries  = new ArrayList<>();

    /**
     * Property for adding debug names to the output if true.
     */
    public static final String DEBUG_NAMES = "DebugNames";

    /**
     * The name of the annotation for import functions.
     */
    public static final String IMPORT_ANNOTATION = "de.inetsoftware.jwebassembly.api.annotation.Import";

    /**
     * The name of the annotation for export functions.
     */
    public static final String EXPORT_ANNOTATION =  "de.inetsoftware.jwebassembly.api.annotation.Export";

    /**
     * The name of the annotation for native WASM code in text format. 
     */
    public static final String TEXTCODE_ANNOTATION =  "de.inetsoftware.jwebassembly.api.annotation.WasmTextCode";

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
        try {
            classFiles.add( classFile.toURI().toURL() );
        } catch( MalformedURLException ex ) {
            throw new IllegalArgumentException( ex );
        }
    }

    /**
     * Add a classFile to compile
     * 
     * @param classFile
     *            the file
     */
    public void addFile( @Nonnull URL classFile ) {
        classFiles.add( classFile );
    }

    /**
     * Set property to control the behavior of the compiler
     * 
     * @param key
     *            the key
     * @param value
     *            the new value
     */
    public void setProperty( String key, String value ) {
        properties.put( key, value );
    }

    /**
     * Get the value of a property.
     * 
     * @param key
     *            the key
     * @return the current value
     */
    public String getProperty( String key ) {
        return properties.get( key );
    }

    /**
     * Add a jar or zip file as library to the compiler. Methods from the library will be add to the wasm only when used.
     * 
     * @param library
     *            a archive file
     */
    public void addLibrary( @Nonnull File library ) {
        try {
            libraries.add( library.toURI().toURL() );
        } catch( MalformedURLException ex ) {
            throw new IllegalArgumentException( ex );
        }
    }

    /**
     * Add a jar or zip file as library to the compiler. Methods from the library will be add to the wasm only when used.
     * 
     * @param library
     *            a archive file
     */
    public void addLibrary( @Nonnull URL library ) {
        libraries.add( library );
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
     * @param file
     *            the target for the module data
     * @throws WasmException
     *             if any conversion error occurs
     */
    public void compileToText( File file ) throws WasmException {
        try (OutputStreamWriter output = new OutputStreamWriter( new FileOutputStream( file ), StandardCharsets.UTF_8 )) {
            compileToText( output );
        } catch( Exception ex ) {
            throw WasmException.create( ex );
        }
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
        try (TextModuleWriter writer = new TextModuleWriter( output, properties )) {
            compile( writer );
        } catch( Exception ex ) {
            throw WasmException.create( ex );
        }
    }

    /**
     * Convert the added files to a WebAssembly module in binary representation.
     * 
     * @return the module as string
     * @throws WasmException
     *             if any conversion error occurs
     */
    public byte[] compileToBinary() throws WasmException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        compileToBinary( output );
        return output.toByteArray();
    }

    /**
     * Convert the added files to a WebAssembly module in binary representation.
     * 
     * @param file
     *            the target for the module data
     * @throws WasmException
     *             if any conversion error occurs
     */
    public void compileToBinary( File file ) throws WasmException {
        try (FileOutputStream output = new FileOutputStream( file )) {
            compileToBinary( output );
        } catch( Exception ex ) {
            throw WasmException.create( ex );
        }
    }

    /**
     * Convert the added files to a WebAssembly module in binary representation.
     * 
     * @param output
     *            the target for the module data
     * @throws WasmException
     *             if any conversion error occurs
     */
    public void compileToBinary( OutputStream output ) throws WasmException {
        try (BinaryModuleWriter writer = new BinaryModuleWriter( output, properties )) {
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
        ModuleGenerator generator = new ModuleGenerator( writer, libraries );
        for( URL url : classFiles ) {
            ClassFile classFile = new ClassFile( new BufferedInputStream( url.openStream() ) );
            generator.prepare( classFile );
        }
        generator.prepareFinish();
        for( URL url : classFiles ) {
            ClassFile classFile = new ClassFile( new BufferedInputStream( url.openStream() ) );
            generator.write( classFile );
        }
    }
}
