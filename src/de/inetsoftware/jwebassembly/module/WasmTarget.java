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

import javax.annotation.Nonnull;

/**
 * The target for the different outputs
 */
public class WasmTarget implements Closeable {

    private File         file;

    private OutputStream output;

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
     * Close all streams
     * 
     * @throws IOException
     *             if any I/O error occur
     */
    @Override
    public void close() throws IOException {
        output.close();
    }
}
