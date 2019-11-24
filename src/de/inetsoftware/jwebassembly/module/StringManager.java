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
import javax.annotation.Nonnull;

import de.inetsoftware.jwebassembly.api.annotation.WasmTextCode;
import de.inetsoftware.jwebassembly.wasm.ValueType;

/**
 * Handle all the constant strings. The constant strings will be write into the data section.
 * 
 * @author Volker Berlin
 */
class StringManager extends LinkedHashMap<String, Integer> {

    /**
     * Signature of method stringConstant.
     * 
     * @see #stringConstant(int)
     */
    private static final FunctionName STRING_CONSTANT_FUNCTION =
                    new FunctionName( "de/inetsoftware/jwebassembly/module/StringManager.stringConstant(I)Ljava/lang/String;" );

    private FunctionManager           functions;

    private int                       stringMemoryOffset       = -1;

    /**
     * Initialize the string manager.
     * 
     * @param functions
     *            the function manager
     */
    void init( FunctionManager functions ) {
        this.functions = functions;
    }

    /**
     * Get the function name object for the {@link #stringConstant(int)}.
     * 
     * @see #stringConstant(int)
     * @return the name
     */
    @Nonnull
    FunctionName getStringConstantFunction() {
        if( stringMemoryOffset < 0 ) {
            // register the function stringsMemoryOffset() as synthetic function
            stringMemoryOffset = 0;
            FunctionName offsetFunction =
                            new WatCodeSyntheticFunctionName( "de/inetsoftware/jwebassembly/module/StringManager", "stringsMemoryOffset", "()I", "", null, ValueType.i32 ) {
                                protected String getCode() {
                                    return "i32.const " + stringMemoryOffset;
                                }
                            };
            functions.markAsNeeded( offsetFunction, true );
        }

        return STRING_CONSTANT_FUNCTION;
    }

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
        int size = size();
        writer.setStringCount( size );
        if( size == 0 ) {
            // no strings, nothing to do
            return;
        }

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

        // save the offset of the string data for later code inlining
        stringMemoryOffset = dataStream.size();
        int offset = stringMemoryOffset + size * 4;

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

    /**
     * WASM code<p>
     * Get a constant string from the table.
     * 
     * @param strIdx
     *            the id/index of the string.
     * @return the string
     * @see #STRING_CONSTANT_FUNCTION
     */
    private static String stringConstant( int strIdx ) {
        String str = getStringFromTable( strIdx );
        if( str != null ) {
            return str;
        }

        // read the compact string length
        int offset = getIntFromMemory( strIdx * 4 + stringsMemoryOffset() );
        int length = 0;
        int b;
        int shift = 0;
        do {
            b = getUnsignedByteFromMemory( offset++ );
            length += (b & 0x7F) << shift;
            shift += 7;
        } while( b >= 0x80 );

        // copy the bytes from the data section
        byte[] bytes = new byte[length];
        for( int i = 0; i < length; i++ ) {
            bytes[i] = getUnsignedByteFromMemory( i + offset );
        }
        str = new String( bytes );
        // save the string for future use
        setStringIntoTable( strIdx, str );
        return str;
    }

    /**
     * WASM code<p>
     * Get a string from the string table. Should be inlined from the optimizer.
     * 
     * @param strIdx
     *            the id/index of the string.
     * @return the string or null if not already set.
     */
    @WasmTextCode( "local.get 0 " + //
                    "table.get 1 " + //
                    "return" )
    private static native String getStringFromTable( int strIdx );

    /**
     * WASM code<p>
     * Set a string from the string table. Should be inlined from the optimizer.
     * 
     * @param strIdx
     *            the id/index of the string.
     * @param str
     *            the string
     */
    @WasmTextCode( "local.get 0 " + //
                    "local.get 1 " + //
                    "table.set 1 " + //
                    "return" )
    private static native void setStringIntoTable( int strIdx, String str );

    /**
     * WASM code<p>
     * Placeholder for a synthetic function. Should be inlined from the optimizer.
     * @return the memory offset of the string data in the element section
     */
    //TODO the annotation can be removed if ModuleGenerator.prepareFunctions() can detect Synthetic functions correctly
    @WasmTextCode( "i32.const 0" )
    private static native int stringsMemoryOffset();

    /**
     * WASM code<p>
     * Load an i32 from memory. The offset must be aligned. Should be inlined from the optimizer.
     * 
     * @param pos
     *            the memory position
     * @return the value from the memory
     */
    @WasmTextCode( "local.get 0 " + //
                    "i32.load offset=0 align=4 " + //
                    "return" )
    private static native int getIntFromMemory( int pos );

    /**
     * WASM code<p>
     * Load a byte from the memory. Should be inlined from the optimizer.
     * 
     * @param pos
     *            the memory position
     * @return the value from the memory
     */
    @WasmTextCode( "local.get 0 " + //
                    "i32.load8_u offset=0 " + //
                    "return" )
    private static native byte getUnsignedByteFromMemory( int pos );
}
