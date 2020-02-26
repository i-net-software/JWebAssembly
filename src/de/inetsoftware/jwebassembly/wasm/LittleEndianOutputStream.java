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
package de.inetsoftware.jwebassembly.wasm;

import java.io.ByteArrayOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * outputStream with little endian encoding like in Wasm.
 *
 * @author Volker Berlin
 */
public class LittleEndianOutputStream extends FilterOutputStream {

    private int count;

    /**
     * Create a in memory stream.
     */
    public LittleEndianOutputStream() {
        super( new ByteArrayOutputStream() );
    }

    /**
     * Create a wrapped stream.
     * 
     * @param output
     *            the target of data
     */
    public LittleEndianOutputStream( OutputStream output ) {
        super( output );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void write( int b ) throws IOException {
        out.write( b );
        count++;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void write( byte[] b, int off, int len ) throws IOException {
        out.write( b, off, len );
        count += len;
    }

    /**
     * Write a integer little endian (ever 4 bytes)
     * 
     * @param value
     *            the value
     * @throws IOException
     *             if an I/O error occurs.
     */
    public void writeInt32( int value ) throws IOException {
        write( value >>> 0 );
        write( value >>> 8 );
        write( value >>> 16 );
        write( value >>> 24 );
    }

    /**
     * The count of bytes in the stream.
     * 
     * @return the data size
     */
    public int size() {
        return count;
    }

    /**
     * Reset the stream. Work only for in memory stream.
     */
    public void reset() {
        ByteArrayOutputStream baos = (ByteArrayOutputStream)out;
        baos.reset();
        count = 0;
    }
}
