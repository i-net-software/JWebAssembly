/*
   Copyright 2019 Volker Berlin (i-net software)

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.

*/
package de.inetsoftware.jwebassembly.module;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;

import javax.annotation.Nonnegative;

/**
 * Handle all the constant strings. The constant strings will be write into the data section.
 * 
 * @author Volker Berlin
 */
class StringManager extends LinkedHashMap<String, Integer> {

    /**
     * Finish the prepare. Now no new strings should be added.
     * 
     * @param writer
     *            the targets for the strings
     * @throws IOException
     *             if any I/O error occur
     */
    void prepareFinish( ModuleWriter writer ) throws IOException {
        // inform the writer of string count that it can allocate a table of type anyref for the constant strings
        writer.setStringCount( size() );

        /* Write the strings to the data sections.
           first there is a index table, then follows the strings
           | .....                          |
           ├────────────────────────────────┤
           | start index string 1 (4 bytes) |
           ├────────────────────────────────┤
           | start index string 2 (4 bytes) |
           ├────────────────────────────────┤
           | start index string 3 (4 bytes) |
           ├────────────────────────────────┤
           | .....                          |
           ├────────────────────────────────┤
           | length string 1 (1-x bytes)    |
           ├────────────────────────────────┤
           | string 1        (UTF8 encoded) |
           ├────────────────────────────────┤
           | length string 2 (1-x bytes)    |
           ├────────────────────────────────┤
           | string 2        (UTF8 encoded) |
           ├────────────────────────────────┤
           | .....                          |
         */
        ByteArrayOutputStream stringOut = new ByteArrayOutputStream();
        ByteArrayOutputStream dataStream = writer.dataStream;

        int offset = dataStream.size() + size() * 4;

        for( String str : this.keySet() ) {
            // write the position where the string starts in the data section
            // little-endian byte order
            int position = offset + stringOut.size();
            dataStream.write( position >>> 0 );
            dataStream.write( position >>> 8 );
            dataStream.write( position >>> 16 );
            dataStream.write( position >>> 24 );

            byte[] bytes = str.getBytes( StandardCharsets.UTF_8 );
            writeVaruint32( bytes.length, stringOut );
            stringOut.write( bytes );
        }

        stringOut.writeTo( dataStream );
    }

    /**
     * Write an unsigned integer.
     * 
     * @param value
     *            the value
     * @param out
     *            target stream
     * @throws IOException
     *             if an I/O error occurs.
     */
    private static void writeVaruint32( @Nonnegative int value, OutputStream out ) throws IOException {
        if( value < 0 ) {
            throw new IOException( "Invalid negative value" );
        }
        do {
            int b = value & 0x7F; // low 7 bits
            value >>= 7;
            if( value != 0 ) { /* more bytes to come */
                b |= 0x80;
            }
            out.write( b );
        } while( value != 0 );
    }
}
