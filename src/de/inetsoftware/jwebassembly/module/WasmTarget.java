/*
 * Copyright 2019 Volker Berlin (i-net software)
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

import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

import javax.annotation.Nonnull;

/**
 * The target for the different outputs
 */
public class WasmTarget implements Closeable {

    private File         file;

    private OutputStream output;

    private Appendable   textOutput;

    private Writer       sourceMap;

    private Writer       javaScript;


    /**
     * Create a target with a file.
     * 
     * @param file
     *            the wasm file
     */
    public WasmTarget( File file ) {
        this.file = file;
    }

    /**
     * Create a target with an OutputStream
     * 
     * @param output
     *            the stream for the wasm file
     */
    public WasmTarget( OutputStream output ) {
        this.output = output;
    }

    /**
     * Create a target with an Appendable for text export
     * 
     * @param output
     *            the stream for the wasm file
     */
    public WasmTarget( Appendable output ) {
        this.textOutput = output;
    }

    /**
     * Get the wasm OutputStream
     * 
     * @return the stream
     * @throws IOException
     *             if any I/O error occur
     */
    @Nonnull
    public OutputStream getWasmOutput() throws IOException {
        if( output == null ) {
            output = new BufferedOutputStream( new FileOutputStream( file ) );
        }
        return output;
    }

    /**
     * Get the output for the text format.
     * 
     * @return the target
     * @throws IOException
     *             if any I/O error occur
     */
    public Appendable getTextOutput() throws IOException {
        if( textOutput == null ) {
            textOutput = new OutputStreamWriter( new BufferedOutputStream( new FileOutputStream( file ) ), StandardCharsets.UTF_8 );
        }
        return textOutput;
    }

    /**
     * Get the URL for the source mapping that should be write into the assembly.
     * 
     * @return the URL string or null.
     */
    @Nonnull
    public String getSourceMappingURL() {
        if( file != null ) {
            return file.getName() + ".map";
        }
        return null;
    }

    /**
     * Get the source map OutputStream
     * 
     * @return the stream
     * @throws IOException
     *             if any I/O error occur
     */
    @Nonnull
    public Writer getSourceMapOutput() throws IOException {
        if( sourceMap == null && file != null ) {
            sourceMap = new OutputStreamWriter( new BufferedOutputStream( new FileOutputStream( getBaseWasmFile() + ".wasm.map" ) ), StandardCharsets.UTF_8 );
        }
        return sourceMap;
    }

    /**
     * Get the output for the JavaScript glue code.
     * 
     * @return the script output.
     * @throws IOException
     *             if any I/O error occur
     */
    public Writer getJavaScriptOutput() throws IOException {
        if( javaScript == null && file != null && file.isFile() ) {
            javaScript = new OutputStreamWriter( new BufferedOutputStream( new FileOutputStream( getBaseWasmFile() + ".wasm.js" ) ), StandardCharsets.UTF_8 );
        }
        return javaScript;
    }

    /**
     * Get the base name without extension.
     * 
     * @return the base file name
     */
    private String getBaseWasmFile() {
        String name = file.toString();
        int idx = name.lastIndexOf( '.' );
        if( idx > 0 ) {
            name = name.substring( 0, idx );
        }
        return name;
    }

    /**
     * Close all streams
     * 
     * @throws IOException
     *             if any I/O error occur
     */
    @Override
    public void close() throws IOException {
        if( output != null ) {
            output.close();
        }
        if( textOutput instanceof Closeable ) {
            ((Closeable)textOutput).close();
        }
        if( sourceMap != null ) {
            sourceMap.close();
        }
        if( javaScript != null ) {
            javaScript.close();
        }
    }
}
