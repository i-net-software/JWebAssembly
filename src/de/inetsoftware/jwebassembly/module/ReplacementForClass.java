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
package de.inetsoftware.jwebassembly.module;

import de.inetsoftware.jwebassembly.api.annotation.Replace;
import de.inetsoftware.jwebassembly.api.annotation.WasmTextCode;

/**
 * Replacement for java.lang.Class
 *
 * @author Volker Berlin
 */
@Replace( "java/lang/Class" )
class ReplacementForClass {

    private final int vtable;

    /**
     * Create a instance
     * 
     * @param vtable
     *            the pointer in the memory for the class/type description.
     */
    private ReplacementForClass( int vtable ) {
        this.vtable = vtable;
    }

    /**
     * Replacement for {@link Class#getName()}
     * 
     * @return the name
     */
    String getName() {
        return StringManager.stringConstant( getIntFromMemory( vtable + TypeManager.TYPE_DESCRIPTION_TYPE_NAME ) );
    }

    /**
     * Replacement for {@link Object#getClass()}. The documentation of the memory of the type description is done in method:
     * {@link TypeManager.StructType#writeToStream(java.io.ByteArrayOutputStream, java.util.function.ToIntFunction)}
     * 
     * @param obj
     *            the instance
     * @return the class
     */
    @WasmTextCode( "local.get 0 " // THIS
                    + "struct.get java/lang/Object .vtable " // vtable is on index 0
                    + "local.tee 1 " // save the vtable location
                    + "i32.const " + TypeManager.TYPE_DESCRIPTION_INSTANCEOF_OFFSET + " " // vtable is on index 0
                    + "i32.add " //
                    + "call $java/lang/Class.getIntFromMemory(I)I " //
                    + "local.get 1 " // get the vtable location
                    + "i32.add " //
                    + "i32.const 4 " // length of instanceof
                    + "i32.add " //
                    + "call $java/lang/Class.getIntFromMemory(I)I " // first entry in instanceof is ever the id of the Class self
                    + "call $java/lang/Class.classConstant(I)Lde/inetsoftware/jwebassembly/module/ReplacementForClass; " //
                    + "return " //
    )
    @Replace( "java/lang/Object.getClass()Ljava/lang/Class;" )
    private static native ReplacementForClass getClassObject( Object obj );

    /**
     * WASM code
     * <p>
     * Get a constant Class from the table.
     * 
     * @param classIdx
     *            the id/index of the Class.
     * @return the string
     * @see #CLASS_CONSTANT_FUNCTION
     */
    private static ReplacementForClass classConstant( int classIdx ) {
        ReplacementForClass clazz = getClassFromTable( classIdx );
        if( clazz != null ) {
            return clazz;
        }

        /*
            The memory/data section has the follow content:
             ┌──────────────────────────────────┐
             | Type/Class descriptions (vtable) |
             ├──────────────────────────────────┤
             | Type/Class table                 |
             ├──────────────────────────────────┤
             | String table                     |
             └──────────────────────────────────┘
        */
        int vtable = getIntFromMemory( classIdx * 4 + typeTableMemoryOffset() );
        clazz = new ReplacementForClass( vtable );
        // save the string for future use
        setClassIntoTable( classIdx, clazz );
        return clazz;
    }

    /**
     * WASM code
     * <p>
     * Get a Class instance from the Class table. Should be inlined from the optimizer.
     * 
     * @param classIdx
     *            the id/index of the Class.
     * @return the string or null if not already set.
     */
    @WasmTextCode( "local.get 0 " + //
                    "table.get 2 " + // table 2 is used for classes
                    "return" )
    private static native ReplacementForClass getClassFromTable( int classIdx );

    /**
     * WASM code
     * <p>
     * Set a Class instance in the Class table. Should be inlined from the optimizer.
     * 
     * @param strIdx
     *            the id/index of the string.
     * @param clazz
     *            the Class instance
     */
    @WasmTextCode( "local.get 0 " + //
                    "local.get 1 " + //
                    "table.set 2 " + // table 2 is used for classes
                    "return" )
    private static native void setClassIntoTable( int strIdx, ReplacementForClass clazz );

    /**
     * WASM code
     * <p>
     * Placeholder for a synthetic function. Should be inlined from the optimizer.
     * 
     * @return the memory offset of the string data in the element section
     */
    private static native int typeTableMemoryOffset();

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
}
