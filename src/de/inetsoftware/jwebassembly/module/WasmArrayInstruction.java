/*
   Copyright 2018 - 2020 Volker Berlin (i-net software)

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

import java.io.IOException;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import de.inetsoftware.jwebassembly.WasmException;
import de.inetsoftware.jwebassembly.javascript.JavaScriptSyntheticFunctionName;
import de.inetsoftware.jwebassembly.wasm.AnyType;
import de.inetsoftware.jwebassembly.wasm.ArrayOperator;
import de.inetsoftware.jwebassembly.wasm.ArrayType;
import de.inetsoftware.jwebassembly.wasm.ValueType;

/**
 * WasmInstruction for an array operation.
 * 
 * @author Volker Berlin
 *
 */
class WasmArrayInstruction extends WasmInstruction {

    private final ArrayOperator op;

    private final AnyType   type;

    private final TypeManager types;

    private SyntheticFunctionName functionName;

    /**
     * Create an instance of an array operation.
     * 
     * @param op
     *            the array operation
     * @param type
     *            the type of the parameters
     * @param javaCodePos
     *            the code position/offset in the Java method
     * @param lineNumber
     *            the line number in the Java source code
     */
    WasmArrayInstruction( @Nullable ArrayOperator op, @Nullable AnyType type, TypeManager types, int javaCodePos, int lineNumber ) {
        super( javaCodePos, lineNumber );
        this.op = op;
        this.type = type;
        this.types = types;
    }

    /**
     * Create the synthetic polyfill function of this instruction for nonGC mode.
     * 
     * @return the function or null if not needed
     */
    SyntheticFunctionName createNonGcFunction() {
        // i8 and i16 are not valid in function signatures
        AnyType functionType = type == ValueType.i8 || type == ValueType.i16 ? ValueType.i32 : type;
        switch( op ) {
            case NEW:
                String cmd;
                if( type.isRefType() ) {
                    cmd = "Object.seal(new Array(l).fill(null))";
                } else {
                    switch( (ValueType)type ) {
                        case i8:
                            cmd = "new Uint8Array(l)";
                            break;
                        case i16:
                            cmd = "new Int16Array(l)";
                            break;
                        case i32:
                            cmd = "new Int32Array(l)";
                            break;
                        case i64:
                            cmd = "new BigInt64Array(l)";
                            break;
                        case f32:
                            cmd = "new Float32Array(l)";
                            break;
                        case f64:
                            cmd = "new Float64Array(l)";
                            break;
                        default:
                            cmd = "Object.seal(new Array(l).fill(null))";
                    }
                }
                ArrayType arrayType = types.arrayType( type );
                functionName = new JavaScriptSyntheticFunctionName( "NonGC_", "array_new_" + validJsName( type ), () -> {
                    // create the default values of a new type
                    return new StringBuilder( "(l)=>Object.seal({0:" ) // fix count of elements
                                    .append( arrayType.getClassIndex() ) // .vtable
                                    .append( ",1:0,2:" ) // .hashCode
                                    .append( cmd ) // the array data
                                    .append( "})" ) //
                                    .toString();
                }, ValueType.i32, null, ValueType.externref );
                break;
            case GET:
                functionName = new JavaScriptSyntheticFunctionName( "NonGC_", "array_get_" + validJsName( functionType ), () -> "(a,i)=>a[2][i]", ValueType.externref, ValueType.i32, null, functionType );
                break;
            case SET:
                functionName = new JavaScriptSyntheticFunctionName( "NonGC_", "array_set_" + validJsName( functionType ), () -> "(a,i,v)=>a[2][i]=v", ValueType.externref, ValueType.i32, functionType, null, null );
                break;
            case LEN:
                functionName = new JavaScriptSyntheticFunctionName( "NonGC_", "array_len", () -> "(a)=>a[2].length", ValueType.externref, null, ValueType.i32 );
                break;
        }
        return functionName;
    }

    /**
     * Get a valid JavaScript name.
     * 
     * @param type
     *            the type
     * @return the identifier that is valid
     */
    private static String validJsName( AnyType type ) {
        return type.isRefType() ? "obj" : type.toString();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    Type getType() {
        return Type.Array;
    }

    /**
     * {@inheritDoc}
     */
    public void writeTo( @Nonnull ModuleWriter writer ) throws IOException {
        if( functionName != null ) { // nonGC
            writer.writeFunctionCall( functionName, null );
        } else {
            writer.writeArrayOperator( op, type );
        }
    }

    /**
     * {@inheritDoc}
     */
    AnyType getPushValueType() {
        switch( op ) {
            case NEW:
                return types.arrayType( type );
            case GET:
                return type instanceof ValueType ? (ValueType)type : ValueType.externref;
            case SET:
                return null;
            case LEN:
                return ValueType.i32;
            default:
                throw new WasmException( "Unknown array operation: " + op, -1 );
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    int getPopCount() {
        switch( op ) {
            case GET:
                return 2;
            case NEW:
            case LEN:
                return 1;
            case SET:
                return 3;
            default:
                throw new WasmException( "Unknown array operation: " + op, -1 );
        }
    }
}
