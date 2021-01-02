/*
 * Copyright 2019 - 2021 Volker Berlin (i-net software)
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

import javax.annotation.Nonnull;

import de.inetsoftware.jwebassembly.WasmException;
import de.inetsoftware.jwebassembly.module.TypeManager;
import de.inetsoftware.jwebassembly.module.WasmOptions;
import de.inetsoftware.jwebassembly.module.TypeManager.StructType;
import de.inetsoftware.jwebassembly.module.TypeManager.StructTypeKind;

/**
 * A reference to an array type
 * 
 * @author Volker Berlin
 *
 */
public class ArrayType extends StructType {

    private AnyType arrayType;

    private AnyType nativeArrayType;

    private NamedStorageType nativeFieldName;

    private int componentClassIndex;

    /**
     * Create a new array type
     * 
     * @param arrayType
     *            the type of the array
     * @param manager
     *            the manager which hold all StructTypes
     * @param componentClassIndex
     *            the running index of the component/array class/type
     * @param options
     *            compiler properties
     */
    public ArrayType( AnyType arrayType, @Nonnull TypeManager manager, int componentClassIndex, WasmOptions options ) {
        this( getJavaClassName( arrayType ), StructTypeKind.array, manager, arrayType );
        this.componentClassIndex = componentClassIndex;
        if( options.useGC() ) {
            String nativeName = '_' + getName();
            this.nativeArrayType = new ArrayType( nativeName, StructTypeKind.array_native, manager, arrayType );
            //structTypes.put( name, nativeArrayType );
        } else {
            this.nativeArrayType = arrayType;
        }
        nativeFieldName = new NamedStorageType( this.nativeArrayType, getName(), TypeManager.FIELD_VALUE );
    }

    /**
     * Create a new instance
     * 
     * @param name
     *            the type name
     * @param kind
     *            the kind, array or array_native
     * @param manager
     *            the manager which hold all StructTypes
     * @param arrayType
     *            the type of the array
     */
    private ArrayType( @Nonnull String name, @Nonnull StructTypeKind kind, @Nonnull TypeManager manager, AnyType arrayType ) {
        super( name, kind, manager );
        this.arrayType = arrayType;
    }

    /**
     * Create class name for the array class.
     * 
     * @param arrayType
     *            the type of the array
     * @return the name
     */
    @Nonnull
    private static String getJavaClassName( AnyType arrayType ) {
        if( !arrayType.isRefType() ) {
            switch( (ValueType)arrayType ) {
                case bool:
                    return "[Z";
                case i8:
                    return "[B";
                case i16:
                    return "[S";
                case u16:
                    return "[C";
                case f64:
                    return "[D";
                case f32:
                    return "[F";
                case i32:
                    return "[I";
                case i64:
                    return "[J";
                case eqref:
                case externref:
                    return "[Ljava.lang.Object;";
                default:
                    throw new WasmException( "Not supported array type: " + arrayType, -1 );
            }
        }
        if( arrayType instanceof ArrayType ) {
            return "[" + getJavaClassName( ((ArrayType)arrayType).arrayType );
        }
        return "[L" + ((StructType)arrayType).getName() + ";";
    }

    /**
     * The element type of the array
     * @return the type
     */
    public AnyType getArrayType() {
        return arrayType;
    }

    /**
     * The native webassembly array type that we wrap
     * @return the type
     */
    public AnyType getNativeArrayType() {
        return nativeArrayType;
    }

    /**
     * The native field name
     * 
     * @return the field name
     */
    public NamedStorageType getNativeFieldName() {
        return nativeFieldName;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected int getComponentClassIndex() {
        return componentClassIndex;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isRefType() {
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isSubTypeOf( AnyType type ) {
        return type == this || type == ValueType.externref;
    }
}
