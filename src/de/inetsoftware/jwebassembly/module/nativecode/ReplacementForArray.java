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

import de.inetsoftware.jwebassembly.api.annotation.Replace;
import de.inetsoftware.jwebassembly.api.annotation.WasmTextCode;

import static de.inetsoftware.jwebassembly.module.TypeManager.*;

/**
 * Replacement for java.lang.reflect.Array
 *
 * @author Volker Berlin
 */
public class ReplacementForArray {

    /**
     * get the vtable value
     * 
     * @param obj
     *            the instance
     * @return the vtable value
     */
    @WasmTextCode( "local.get 0 " // THIS
                    + "struct.get java/lang/Object .vtable " // vtable is on index 0
                    + "return " //
    )
    private static native int getVTable( Object obj );

    /**
     * WASM code
     * <p>
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
     * Replacement of the native Java methods Array.getLength(x)
     * 
     * @param obj
     *            the object
     * @return the length of the object
     */
    @Replace( "java/lang/reflect/Array.getLength(Ljava/lang/Object;)I" )
    private static int array_getLength( Object obj ) {
        int vtable = getVTable( obj );
        int componentType = getIntFromMemory( vtable + TYPE_DESCRIPTION_ARRAY_TYPE );
        switch( componentType ) {
            case -1:
                throw new java.lang.IllegalArgumentException( "Argument is not an array" );
            case BOOLEAN:
                return getLengthOfBooleans( obj );
            case BYTE:
                return getLengthOfBytes( obj );
            case CHAR:
                return getLengthOfChars( obj );
            case DOUBLE:
                return getLengthOfDoubles( obj );
            case FLOAT:
                return getLengthOfFloats( obj );
            case INT:
                return getLengthOfInts( obj );
            case LONG:
                return getLengthOfLongs( obj );
            case SHORT:
                return getLengthOfShorts( obj );
            default:
                return ((Object[])obj).length;
        }
    }

    @WasmTextCode( "local.get 0 " // THIS
                    + "array.len [Z " //
                    + "return " )
    private static native int getLengthOfBooleans( Object obj );

    @WasmTextCode( "local.get 0 " // THIS
                    + "array.len [B " //
                    + "return " )
    private static native int getLengthOfBytes( Object obj );

    @WasmTextCode( "local.get 0 " // THIS
                    + "array.len [C " //
                    + "return " )
    private static native int getLengthOfChars( Object obj );

    @WasmTextCode( "local.get 0 " // THIS
                    + "array.len [D " //
                    + "return " )
    private static native int getLengthOfDoubles( Object obj );

    @WasmTextCode( "local.get 0 " // THIS
                    + "array.len [F " //
                    + "return " )
    private static native int getLengthOfFloats( Object obj );

    @WasmTextCode( "local.get 0 " // THIS
                    + "array.len [I " //
                    + "return " )
    private static native int getLengthOfInts( Object obj );

    @WasmTextCode( "local.get 0 " // THIS
                    + "array.len [J " //
                    + "return " )
    private static native int getLengthOfLongs( Object obj );

    @WasmTextCode( "local.get 0 " // THIS
                    + "array.len [S " //
                    + "return " )
    private static native int getLengthOfShorts( Object obj );
}
