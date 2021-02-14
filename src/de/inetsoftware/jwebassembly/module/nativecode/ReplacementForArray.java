/*
   Copyright 2020 - 2021 Volker Berlin (i-net software)

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
                return ((boolean[])obj).length;
            case BYTE:
                return ((byte[])obj).length;
            case CHAR:
                return ((char[])obj).length;
            case DOUBLE:
                return ((double[])obj).length;
            case FLOAT:
                return ((float[])obj).length;
            case INT:
                return ((int[])obj).length;
            case LONG:
                return ((long[])obj).length;
            case SHORT:
                return ((short[])obj).length;
            default:
                return ((Object[])obj).length;
        }
    }

    /**
     * Replacement of the native Java methods Array.newInstance(c,l)
     * 
     * @param obj
     *            the object
     * @return the length of the object
     */
    @Replace( "java/lang/reflect/Array.newInstance(Ljava/lang/Class;I)Ljava/lang/Object;" )
    private static Object array_newInstance( ReplacementForClass componentClass, int length ) {
        int vtable = componentClass.vtable;
        int componentType = getIntFromMemory( vtable + TYPE_DESCRIPTION_INSTANCEOF_OFFSET );
        switch( componentType ) {
            case BOOLEAN:
                return new boolean[length];
            case BYTE:
                return new byte[length];
            case CHAR:
                return new char[length];
            case DOUBLE:
                return new double[length];
            case FLOAT:
                return new float[length];
            case INT:
                return new int[length];
            case LONG:
                return new long[length];
            case SHORT:
                return new short[length];
            default:
                //TODO it should return the right component array
                return new Object[length];
        }
    }
}
