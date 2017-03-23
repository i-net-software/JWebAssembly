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
package de.inetsoftware.jwebassembly.binary;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import javax.annotation.Nonnegative;

/**
 * @author Volker Berlin
 */
class WasmOutputStream extends ByteArrayOutputStream {

    /**
     * Write a integer little endian (ever 4 bytes)
     * 
     * @param value
     *            the value
     */
    void writeInt32( int value ) {
        write( (value >>> 0) & 0xFF );
        write( (value >>> 8) & 0xFF );
        write( (value >>> 16) & 0xFF );
        write( (value >>> 24) & 0xFF );
    }

    /**
     * Write an unsigned integer.
     * 
     * @param value
     *            the value
     */
    void writeVaruint32( @Nonnegative int value ) {
        do {
            int b = value & 0x7F; // low 7 bits
            value >>= 7;
            if( value != 0 ) { /* more bytes to come */
                b |= 0x80;
            }
            write( b );
        } while( value != 0 );
    }

    /**
     * Write an integer value.
     * 
     * @param value
     *            the value
     */
    void writeVarint32( int value ) {
        while( true ) {
            int b = value & 0x7F;
            value >>= 7;

            /* sign bit of byte is second high order bit (0x40) */
            if( (value == 0 && (b & 0x40) == 0) || (value == -1 && (b & 0x40) != 0) ) {
                write( b );
                return;
            } else {
                write( b | 0x80 );
            }
        }
    }

    /**
     * Write a section header.
     * 
     * @param type
     *            the name of the section
     * @param dataSize
     *            the size of the data
     * @param name
     *            the name, must be set if the id == 0
     * @throws IOException
     *             if any I/O error occur
     */
    void writeSectionHeader( SectionType type, int dataSize, String name ) throws IOException {
        writeVaruint32( type.ordinal() );
        writeVaruint32( dataSize );
        if( type == SectionType.Custom ) {
            byte[] bytes = name.getBytes( StandardCharsets.ISO_8859_1 );
            writeVaruint32( bytes.length );
            write( bytes );
        }
    }

}
