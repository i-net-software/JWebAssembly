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

import java.util.Arrays;

import de.inetsoftware.jwebassembly.wasm.AnyType;
import de.inetsoftware.jwebassembly.wasm.ArrayOperator;
import de.inetsoftware.jwebassembly.wasm.ArrayType;
import de.inetsoftware.jwebassembly.wasm.NumericOperator;
import de.inetsoftware.jwebassembly.wasm.ValueType;
import de.inetsoftware.jwebassembly.wasm.VariableOperator;
import de.inetsoftware.jwebassembly.wasm.WasmBlockOperator;
import de.inetsoftware.jwebassembly.watparser.WatParser;

/**
 * Synthetic functions for creating multidimensional dimensional arrays
 * 
 * @author Volker Berlin
 */
public class MultiArrayFunctionName extends WatCodeSyntheticFunctionName {

    private int       dim;

    private ArrayType type;

    /**
     * Create a new instance
     * 
     * @param dim
     *            the count of dimensions, should be &gt;= 2
     * @param type
     *            the full type of the allocated array
     */
    MultiArrayFunctionName( int dim, ArrayType type ) {
        super( "NonGC", createName( dim, type ), "()V", null, createSignature( dim, type ) );
        this.dim = dim;
        this.type = type;
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
            if( arrayType.getClass() == ArrayType.class ) {
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
     * {@inheritDoc}
     */
    @Override
    protected WasmCodeBuilder getCodeBuilder( WatParser watParser ) {
        watParser.reset( null, null, getSignature( null ) );
        int javaCodePos = 0;

        // allocate the main array (top most) and save it in a local variable
        AnyType arrayType = type.getArrayType();
        watParser.addLocalInstruction( VariableOperator.get, 0, javaCodePos++, -1 );
        watParser.addArrayInstruction( ArrayOperator.NEW, arrayType, javaCodePos++, -1 );
        watParser.addLocalInstruction( VariableOperator.set, dim, javaCodePos++, -1 );

        watParser.addBlockInstruction( WasmBlockOperator.BLOCK, null, javaCodePos++, -1 );
        watParser.addBlockInstruction( WasmBlockOperator.LOOP, null, javaCodePos++, -1 );
        {
            // end condition
            watParser.addLocalInstruction( VariableOperator.get, 0, javaCodePos++, -1 );
            watParser.addNumericInstruction( NumericOperator.eqz, ValueType.i32, javaCodePos++, -1 );
            watParser.addBlockInstruction( WasmBlockOperator.BR_IF, 1, javaCodePos++, -1 );

            // get the reference to the top most array
            watParser.addLocalInstruction( VariableOperator.get, dim, javaCodePos++, -1 );

            // decrement the counter
            watParser.addLocalInstruction( VariableOperator.get, 0, javaCodePos++, -1 );
            watParser.addConstInstruction( 1, javaCodePos++, -1 );
            watParser.addNumericInstruction( NumericOperator.sub, ValueType.i32, javaCodePos++, -1 );
            watParser.addLocalInstruction( VariableOperator.tee, 0, javaCodePos++, -1 );

            // allocate a sub array
            for( int i = 1; i < dim; i++ ) {
                watParser.addLocalInstruction( VariableOperator.get, i, javaCodePos++, -1 );
            }
            if( dim > 2 ) {
                watParser.addMultiNewArrayInstruction( dim - 1, (ArrayType)arrayType, javaCodePos++, -1 );
            } else {
                watParser.addArrayInstruction( ArrayOperator.NEW, arrayType, javaCodePos++, -1 );
            }

            // set the sub array into the main array
            watParser.addArrayInstruction( ArrayOperator.SET, arrayType, javaCodePos++, -1 );

            // continue the loop
            watParser.addBlockInstruction( WasmBlockOperator.BR, 0, javaCodePos++, -1 );
        }
        watParser.addBlockInstruction( WasmBlockOperator.END, null, javaCodePos++, -1 );
        watParser.addBlockInstruction( WasmBlockOperator.END, null, javaCodePos++, -1 );

        //return the created array
        watParser.addLocalInstruction( VariableOperator.get, dim, javaCodePos++, -1 );
        watParser.addBlockInstruction( WasmBlockOperator.RETURN, null, javaCodePos++, -1 );

        return watParser;
    }
}
