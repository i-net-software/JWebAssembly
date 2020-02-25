/*
 * Copyright 2017 - 2020 Volker Berlin (i-net software)
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
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.StreamHandler;

import javax.annotation.Nonnull;

import de.inetsoftware.classparser.ClassFile;
import de.inetsoftware.jwebassembly.binary.BinaryModuleWriter;
import de.inetsoftware.jwebassembly.module.ModuleGenerator;
import de.inetsoftware.jwebassembly.module.ModuleWriter;
import de.inetsoftware.jwebassembly.module.WasmOptions;
import de.inetsoftware.jwebassembly.module.WasmTarget;
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
     * Property for relative path between the final wasm file location and the source files location for the source map.
     * If not empty it should end with a slash like "../../src/main/java/".
     */
    public static final String SOURCE_MAP_BASE = "SourceMapBase";

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
     * The name of the annotation for replacing a single method of the Java runtime. 
     */
    public static final String REPLACE_ANNOTATION =  "de.inetsoftware.jwebassembly.api.annotation.Replace";

    /**
     * The name of the annotation for partial class another class of the Java runtime. 
     */
    public static final String PARTIAL_ANNOTATION =  "de.inetsoftware.jwebassembly.api.annotation.Partial";

    /**
     * If the GC feature of WASM should be use or the GC of the JavaScript host. If true use the GC instructions of WASM.
     */
    public static final String WASM_USE_GC = "wasm.use_gc";

    /**
     * If the exception handling feature of WASM should be use or an unreachable instruction. If true use the exception instructions of WASM.
     */
    public static final String WASM_USE_EH = "wasm.use_eh";

    /**
     * The logger instance
     */
    public static final Logger LOGGER = Logger.getAnonymousLogger( null );
    static {
        LOGGER.setUseParentHandlers( false );
        Formatter formatter = new Formatter() {
            @Override
            public String format( LogRecord record ) {
                String msg = record.getMessage() + '\n';
                if( record.getThrown() != null ) {
                    StringWriter sw = new StringWriter();
                    PrintWriter pw = new PrintWriter( sw );
                    record.getThrown().printStackTrace( pw );
                    pw.close();
                    msg += sw.toString();
                }
                return msg;
            }
        };
        StreamHandler handler = new StreamHandler( System.out, formatter ) {
            @Override
            public void publish(LogRecord record) {
                super.publish(record);
                flush();
            }
        };
        handler.setLevel( Level.ALL );
        LOGGER.addHandler( handler );
        //LOGGER.setLevel( Level.FINE );
    }

    /**
     * Create a instance.
     */
    public JWebAssembly() {
        ProtectionDomain protectionDomain = getClass().getProtectionDomain();
        if( protectionDomain != null ) {
            libraries.add( protectionDomain.getCodeSource().getLocation() ); // add the compiler self to the library path
        }
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
            addLibrary( library.toURI().toURL() );
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
        try {
            compileToText( output );
            return output.toString();
        } catch( Exception ex ) {
            System.err.println( output );
            throw ex;
        }
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
        try (WasmTarget target = new WasmTarget( file )) {
            compileToText( target );
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
        try (WasmTarget target = new WasmTarget( output )) {
            compileToText( target );
        } catch( Exception ex ) {
            throw WasmException.create( ex );
        }
    }

    /**
     * Convert the added files to a WebAssembly module in text representation.
     * 
     * @param target
     *            the target for the module data
     * @throws WasmException
     *             if any conversion error occurs
     */
    private void compileToText( WasmTarget target ) throws WasmException {
        try (TextModuleWriter writer = new TextModuleWriter( target, new WasmOptions( properties ) )) {
            compile( writer, target );
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
        try (WasmTarget target = new WasmTarget( file ) ) {
            compileToBinary( target );
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
        try (WasmTarget target = new WasmTarget( output )) {
            compileToBinary( target );
        } catch( Exception ex ) {
            throw WasmException.create( ex );
        }
    }

    /**
     * Convert the added files to a WebAssembly module in binary representation.
     * 
     * @param target
     *            the target for the module data
     * @throws WasmException
     *             if any conversion error occurs
     */
    private void compileToBinary( WasmTarget target ) throws WasmException {
        try (BinaryModuleWriter writer = new BinaryModuleWriter( target, new WasmOptions( properties ) )) {
            compile( writer, target );
        } catch( Exception ex ) {
            throw WasmException.create( ex );
        }
    }

    /**
     * Convert the added files to a WebAssembly module.
     * 
     * @param writer
     *            the formatter
     * @param target
     *            the target for the module data
     * @throws IOException
     *             if any I/O error occur
     * @throws WasmException
     *             if any conversion error occurs
     */
    private void compile( ModuleWriter writer, WasmTarget target ) throws IOException, WasmException {
        ModuleGenerator generator = new ModuleGenerator( writer, target, libraries );
        for( URL url : classFiles ) {
            try {
                ClassFile classFile = new ClassFile( new BufferedInputStream( url.openStream() ) );
                generator.prepare( classFile );
            } catch( IOException ex ) {
                throw WasmException.create( "Parsing of file " + url + " failed.", ex );
            }
        }
        generator.prepareFinish();
        generator.finish();
    }
}
