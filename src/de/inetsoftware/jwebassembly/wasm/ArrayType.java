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

import de.inetsoftware.jwebassembly.module.TypeManager.StructType;

/**
 * A reference to an array type
 * 
 * @author Volker Berlin
 *
 */
public class ArrayType extends StructType {

    private AnyType arrayType;

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
        //TODO name
        super( "[", classIndex );
        this.arrayType = arrayType;
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

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        // until there is a real type definition we will define write it as externref
        return ValueType.externref.toString();
    }
}
