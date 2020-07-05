package de.inetsoftware.jwebassembly.module;

import java.util.Arrays;
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
import java.util.function.Supplier;

import de.inetsoftware.jwebassembly.javascript.JavaScriptSyntheticFunctionName;
import de.inetsoftware.jwebassembly.wasm.AnyType;
import de.inetsoftware.jwebassembly.wasm.ArrayType;
import de.inetsoftware.jwebassembly.wasm.ValueType;

/**
 * Synthetic functions for creating multidimensional dimensional arrays
 * 
 * @author Volker Berlin
 */
public class MultiArrayFunctionName extends JavaScriptSyntheticFunctionName {

    /**
     * Create a new instance
     * 
     * @param dim
     *            the count of dimensions, should be &gt;= 2
     * @param type
     *            the full type of the allocated array
     */
    public MultiArrayFunctionName( int dim, ArrayType type ) {
        super( "NonGC", createName( dim, type ), createJS( dim, type ), createSignature( dim, type ) );
    }

    /**
     * The element type of the array
     * 
     * @param type
     *            the full type of the allocated array
     * @return the element type
     */
    private static ValueType getElementType( ArrayType type ) {
        do {
            AnyType arrayType = type.getArrayType();
            if( arrayType.getClass() != ArrayType.class ) {
                type = (ArrayType)arrayType;
                continue;
            }
            return arrayType.getClass() == ValueType.class ? (ValueType)arrayType : ValueType.externref;
        } while( true );
    }

    /**
     * Create the unique name depends on dimension and type
     * 
     * @param dim
     *            the dimension
     * @param type
     *            the full type of the allocated array
     * @return the name
     */
    private static String createName( int dim, ArrayType type ) {
        return "array_newmulti" + dim + "_" + getElementType( type );
    }

    /**
     * Create the signature of the function.
     * 
     * @param dim
     *            the dimension
     * @param type
     *            the full type of the allocated array
     * @return the signature
     */
    private static AnyType[] createSignature( int dim, ArrayType type ) {
        AnyType[] signature = new AnyType[dim + 2];
        Arrays.fill( signature, 0, dim, ValueType.i32 );
        signature[dim + 1] = type;
        return signature;
    }

    /**
     * Get the factory for the JavaScript method
     * 
     * @param dim
     *            the dimension
     * @param type
     *            the full type of the allocated array
     * @return the JavaScript factory
     */
    private static Supplier<String> createJS( int dim, ArrayType type ) {
        return () -> {
            // the dimention that must be array and not typed array
            int dimMulti = dim - 1;

            // create parameter signature
            StringBuilder js = new StringBuilder( "(" );
            for( int i = 0; i < dimMulti; i++ ) {
                js.append( 'd' ).append( i );
                js.append( ',' );
            }
            js.append( "l)=>" );

            createJsArray( js, 0, dimMulti, type );

            return js.toString();
        };
    }

    /**
     * Recursion for the allocation with default value
     * 
     * @param js
     *            the target
     * @param idx
     *            running dimension index
     * @param dimMulti
     *            the count of not typed dimensions
     * @param type
     *            the full type of the allocated array
     */
    private static void createJsArray( StringBuilder js, int idx, int dimMulti, ArrayType type ) {
        js.append( "Array.from({length:d" ).append( idx ).append( "}, ()=>" );
        idx++;
        if( idx < dimMulti ) {
            createJsArray( js, idx, dimMulti, type );
        } else {
            switch( getElementType( type ) ) {
                case i8:
                    js.append( "new Uint8Array(l)" );
                    break;
                case i16:
                    js.append( "new Int16Array(l)" );
                    break;
                case i32:
                    js.append( "new Int32Array(l)" );
                    break;
                case i64:
                    js.append( "new BigInt64Array(l)" );
                    break;
                case f32:
                    js.append( "new Float32Array(l)" );
                    break;
                case f64:
                    js.append( "new Float64Array(l)" );
                    break;
                default:
                    js.append( "Object.seal(new Array(l).fill(null))" );
            }
        }
        js.append( ')' );
    }
}
