/*
 * Copyright 2019 - 2020 Volker Berlin (i-net software)
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

import de.inetsoftware.jwebassembly.WasmException;
import de.inetsoftware.jwebassembly.module.TypeManager.StructType;

/**
 * A reference to an array type
 * 
 * @author Volker Berlin
 *
 */
public class ArrayType extends StructType {

    private AnyType arrayType;

    private int componentClassIndex;

    /**
     * Create a new array type
     * 
     * @param arrayType
     *            the type of the array
     * @param classIndex
     *            the running index of the main class/type
     * @param componentClassIndex
     *            the running index of the component/array class/type
     */
    public ArrayType( AnyType arrayType, int classIndex, int componentClassIndex ) {
        super( getJavaClassName( arrayType ), classIndex );
        this.arrayType = arrayType;
        this.componentClassIndex = componentClassIndex;
    }

    /**
     * Create class name for the array class.
     * 
     * @param arrayType
     *            the type of the array
     * @return the name
     */
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
                case externref:
                    return "[Ljava.lang.Object;";
                default:
                    throw new WasmException( "Not supported array type: " + arrayType, -1 );
            }
        }
        return "[" + ((StructType)arrayType).getName();
    }

    /**
     * The element type of the array
     * @return the type
     */
    public AnyType getArrayType() {
        return arrayType;
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
    public int getCode() {
        // until there is a real type definition we will define write it as externref
        return ValueType.externref.getCode();
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
