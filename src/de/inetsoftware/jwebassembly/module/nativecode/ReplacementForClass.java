/*
   Copyright 2020 - 2022 Volker Berlin (i-net software)

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

import static de.inetsoftware.jwebassembly.module.TypeManager.BOOLEAN;
import static de.inetsoftware.jwebassembly.module.TypeManager.BYTE;
import static de.inetsoftware.jwebassembly.module.TypeManager.CHAR;
import static de.inetsoftware.jwebassembly.module.TypeManager.DOUBLE;
import static de.inetsoftware.jwebassembly.module.TypeManager.FLOAT;
import static de.inetsoftware.jwebassembly.module.TypeManager.INT;
import static de.inetsoftware.jwebassembly.module.TypeManager.LONG;
import static de.inetsoftware.jwebassembly.module.TypeManager.SHORT;
import static de.inetsoftware.jwebassembly.module.TypeManager.TYPE_DESCRIPTION_ARRAY_TYPE;
import static de.inetsoftware.jwebassembly.module.TypeManager.TYPE_DESCRIPTION_INSTANCEOF_OFFSET;
import static de.inetsoftware.jwebassembly.module.TypeManager.TYPE_DESCRIPTION_TYPE_NAME;
import static de.inetsoftware.jwebassembly.module.TypeManager.VOID;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Map;

import de.inetsoftware.jwebassembly.api.annotation.Replace;
import de.inetsoftware.jwebassembly.api.annotation.WasmTextCode;
import de.inetsoftware.jwebassembly.module.TypeManager;

/**
 * Replacement for java.lang.Class
 *
 * @param <T> the type of the class modeled by this {@code Class}
 * @author Volker Berlin
 */
@Replace( "java/lang/Class" )
class ReplacementForClass<T> {

    /**
     * The pointer in the memory for the class/type description.
     */
    final int vtable;

    final int classIdx;

    /**
     * Create a instance
     * 
     * @param vtable
     *            the pointer in the memory for the class/type description.
     * @param classIdx
     *            the class index, the ID of the class
     */
    private ReplacementForClass( int vtable, int classIdx ) {
        this.vtable = vtable;
        this.classIdx = classIdx;
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
                    + "i32.const " + TYPE_DESCRIPTION_INSTANCEOF_OFFSET + " " // vtable is on index 0
                    + "i32.add " //
                    + "call $java/lang/Class.getIntFromMemory(I)I " //
                    + "local.get 1 " // get the vtable location
                    + "i32.add " //
                    + "i32.const 4 " // length of instanceof
                    + "i32.add " //
                    + "call $java/lang/Class.getIntFromMemory(I)I " // first entry in instanceof is ever the id of the Class self
                    + "call $java/lang/Class.classConstant(I)Ljava/lang/Class; " //
                    + "return " //
    )
    @Replace( "java/lang/Object.getClass()Ljava/lang/Class;" )
    private static native ReplacementForClass<?> getClassObject( Object obj );

    /**
     * WASM code
     * <p>
     * Get a constant Class from the table.
     * 
     * @param classIdx
     *            the id/index of the Class.
     * @return the string
     * @see TypeManager#getClassConstantFunction()
     */
    private static ReplacementForClass<?> classConstant( int classIdx ) {
        ReplacementForClass<?> clazz = getClassFromTable( classIdx );
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
        clazz = new ReplacementForClass<>( vtable, classIdx );
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
    private static native ReplacementForClass<?> getClassFromTable( int classIdx );

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
    private static native void setClassIntoTable( int strIdx, ReplacementForClass<?> clazz );

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

    /**
     * Replacement of the Java method forName(String)
     * 
     * @param className
     *            the fully qualified name of the desired class.
     * @return the {@code Class} object for the class with the specified name.
     */
    public static Class<?> forName( String className ) throws ClassNotFoundException {
        throw new ClassNotFoundException( className ); // TODO
    }

    /**
     * Replacement of the Java method newInstance()
     * 
     * @return a newly allocated instance of the class represented by this object.
     */
    public T newInstance() throws InstantiationException, IllegalAccessException {
        throw new InstantiationException( getName() ); // TODO
    }

    /**
     * Replacement of the Java method isInstance()
     * 
     * @param obj
     *            the object to check
     * @return true if {@code obj} is an instance of this class
     */
    @WasmTextCode( "unreachable" ) // TODO
    public native boolean isInstance( Object obj );

    /**
     * Replacement of the Java method isAssignableFrom()
     * 
     * @param cls
     *            the class to check
     * @return true, if type {@code cls} can be assigned to objects of this class
     */
    @WasmTextCode( "unreachable" ) // TODO
    public native boolean isAssignableFrom( Class<?> cls );

    /**
     * Replacement of the Java method isInterface()
     *
     * @return  {@code true} if this object represents an interface;
     */
    @WasmTextCode( "unreachable" ) // TODO
    public native boolean isInterface();

    /**
     * Replacement of the Java method isArray()
     * @return {@code true} if this object represents an array class;
     */
    public boolean isArray() {
        int classIdx = getIntFromMemory( vtable + TYPE_DESCRIPTION_ARRAY_TYPE );
        return classIdx >= 0;
    }

    /**
     * Replacement of the Java method {@link Class#isPrimitive()}
     * 
     * @return true if and only if this class represents a primitive type
     */
    public boolean isPrimitive() {
        // the first 9 classes are primitive classes
        return classIdx <= VOID;
    }

    /**
     * Replacement for {@link Class#getName()}
     * 
     * @return the name
     */
    public String getName() {
        return StringTable.stringConstant( getIntFromMemory( vtable + TYPE_DESCRIPTION_TYPE_NAME ) );
    }

    /**
     * Replacement of the Java method getClassLoader()
     */
    public ClassLoader getClassLoader() {
        return null;
    }

    /**
     * Replacement of the Java method getClassLoader0()
     */
    ClassLoader getClassLoader0() {
        return null;
    }

    /**
     * Replacement of the Java methods getSuperclass()
     */
    @WasmTextCode( "unreachable" ) // TODO
    public native Class<? super T> getSuperclass();

    /**
     * Replacement of the Java method getInterfaces()
     * @return an array of interfaces implemented by this class.
     */
    public Class<?>[] getInterfaces() { //TODO
        return new Class<?>[0];
    }

    /**
     * Replacement of the Java method getGenericInterfaces()
     * @return an array of interfaces implemented by this class
     */
    public Type[] getGenericInterfaces() { // TODO
        return getInterfaces();
    }

    /**
     * Replacement of the native Java method getComponentType()
     */
    public ReplacementForClass<?> getComponentType() {
        int classIdx = getIntFromMemory( vtable + TYPE_DESCRIPTION_ARRAY_TYPE );
        return classIdx >= 0 ? classConstant( classIdx ) : null;
    }

    /**
     * Replacement of the Java method getSimpleName()
     * 
     * @return the simple name of the underlying class
     */
    public String getSimpleName() {
        if( isArray() )
            return getComponentType().getSimpleName() + "[]";

        String simpleName = getName();
        int index = simpleName.lastIndexOf( "$" ) + 1;
        if( index == 0 ) { // top level class
            return simpleName.substring( simpleName.lastIndexOf( "." ) + 1 ); // strip the package name
        }

        // Remove leading "\$[0-9]*" from the name
        int length = simpleName.length();
        while( index < length ) {
            char ch = simpleName.charAt( index );
            if( '0' <= ch && ch <= '9' ) {
                index++;
            } else  {
                break;
            }
        }
        // Eventually, this is the empty string iff this is an anonymous class
        return simpleName.substring( index );
    }

    /**
     * Replacement of the Java method getCanonicalName()
     * 
     * @return the canonical name of the underlying class
     */
    public String getCanonicalName() {
        String canonicalName;
        if( isArray() ) {
            canonicalName = getComponentType().getCanonicalName();
            if( canonicalName != null )
                return canonicalName + "[]";
            else
                return null;
        }
        canonicalName = getName();
        int idx = canonicalName.indexOf( '$' );
        if( idx >= 0 ) {
            idx++;
            if( idx < canonicalName.length() ) {
                char ch = canonicalName.charAt( idx );
                if( '0' <= ch && ch <= '9' ) {
                    return null;
                }
            }
            return canonicalName.replace( '$', '.' );
        }
        return canonicalName;
    }

    /**
     * Replacement of the Java method getMethod()
     */
    @WasmTextCode( "unreachable" ) // TODO
    public native Method getMethod(String name, Class<?>... parameterTypes);

    /**
     * Replacement of the Java method getDeclaredField()
     */
    @WasmTextCode( "unreachable" ) // TODO
    public native Field getDeclaredField(String name);

    /**
     * Replacement of the Java method getDeclaredMethod()
     */
    @WasmTextCode( "unreachable" ) // TODO
    public native Method getDeclaredMethod(String name, Class<?>... parameterTypes);

    /**
     * Replacement of the native Java method {@link Class#getPrimitiveClass}
     * 
     * @param name
     *            the class name
     * @return the class
     * @see TypeManager#PRIMITIVE_CLASSES
     */
    static ReplacementForClass<?> getPrimitiveClass( String name ) {
        switch( name ) {
            case "boolean":
                return classConstant( BOOLEAN );
            case "byte":
                return classConstant( BYTE );
            case "char":
                return classConstant( CHAR );
            case "double":
                return classConstant( DOUBLE );
            case "float":
                return classConstant( FLOAT );
            case "int":
                return classConstant( INT );
            case "long":
                return classConstant( LONG );
            case "short":
                return classConstant( SHORT );
            case "void":
                return classConstant( VOID );
        }
        return null;
    }

    /**
     * Replacement of the native Java method {@link Class#desiredAssertionStatus()}
     * 
     * @return the desired assertion status of the specified class.
     */
    public boolean desiredAssertionStatus() {
        return false;
    }

    /**
     * Replacement of the Java method enumConstantDirectory()
     *
     * Returns a map from simple name to enum constant.  This package-private
     * method is used internally by Enum to implement
     */
    @WasmTextCode( "unreachable" ) // TODO
    native Map<String, T> enumConstantDirectory();
}
