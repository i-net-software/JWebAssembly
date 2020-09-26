/*
   Copyright 2020 Volker Berlin (i-net software)

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
package de.inetsoftware.jwebassembly.module.nativecode;

import de.inetsoftware.jwebassembly.api.annotation.WasmTextCode;

/**
 * The WASm string table to create String constant on the fly and hold it.
 *
 * @author Volker Berlin
 */
class StringTable {

    /**
     * WASM code<p>
     * Get a constant string from the table.
     * 
     * @param strIdx
     *            the id/index of the string.
     * @return the string
     * @see #STRING_CONSTANT_FUNCTION
     */
    static String stringConstant( int strIdx ) {
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
     * Set a string in the string table. Should be inlined from the optimizer.
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
     * @return the memory offset of the serialized string data in the element section
     */
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
