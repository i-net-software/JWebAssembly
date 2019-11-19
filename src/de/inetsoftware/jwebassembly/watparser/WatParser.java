/*
   Copyright 2018 - 2019 Volker Berlin (i-net software)

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
package de.inetsoftware.jwebassembly.watparser;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import de.inetsoftware.classparser.MethodInfo;
import de.inetsoftware.jwebassembly.WasmException;
import de.inetsoftware.jwebassembly.module.FunctionName;
import de.inetsoftware.jwebassembly.module.ValueTypeConvertion;
import de.inetsoftware.jwebassembly.module.WasmCodeBuilder;
import de.inetsoftware.jwebassembly.wasm.MemoryOperator;
import de.inetsoftware.jwebassembly.wasm.NumericOperator;
import de.inetsoftware.jwebassembly.wasm.ValueType;
import de.inetsoftware.jwebassembly.wasm.VariableOperator;
import de.inetsoftware.jwebassembly.wasm.WasmBlockOperator;

/**
 * Parser for text format of a function.
 * 
 * @author Volker Berlin
 */
public class WatParser extends WasmCodeBuilder {

    /**
     * Parse the given wasm text format and generate a list of WasmInstuctions
     * 
     * @param wat
     *            the text format content of a function
     * @param lineNumber
     *            the line number for an error message
     * @param method
     *            the method with signature as fallback for a missing variable table
     */
    public void parse( String wat, MethodInfo method, int lineNumber ) {
        try {
            reset( null, method );

            List<String> tokens = splitTokens( wat );
            for( int i = 0; i < tokens.size(); i++ ) {
                int javaCodePos = i;
                String tok = tokens.get( i );
                switch( tok ) {
                    case "local.get":
                        addLocalInstruction( VariableOperator.get, getInt( tokens, ++i), javaCodePos, lineNumber );
                        break;
                    case "local.set":
                        addLocalInstruction( VariableOperator.set, getInt( tokens, ++i), javaCodePos, lineNumber );
                        break;
                    case "local.tee":
                        addLocalInstruction( VariableOperator.tee, getInt( tokens, ++i), javaCodePos, lineNumber );
                        break;
//                    case "get_global":
//                        addGlobalInstruction( true, ref, javaCodePos );
//                        break;
                    case "i32.const":
                        addConstInstruction( getInt( tokens, ++i), ValueType.i32, javaCodePos, lineNumber );
                        break;
                    case "i32.add":
                        addNumericInstruction( NumericOperator.add, ValueType.i32, javaCodePos, lineNumber );
                        break;
                    case "i32.eqz":
                        addNumericInstruction( NumericOperator.eqz, ValueType.i32, javaCodePos, lineNumber );
                        break;
                    case "i32.mul":
                        addNumericInstruction( NumericOperator.mul, ValueType.i32, javaCodePos, lineNumber );
                        break;
                    case "i32.reinterpret_f32":
                        addConvertInstruction( ValueTypeConvertion.f2i_re, javaCodePos, lineNumber );
                        break;
                    case "i32.trunc_sat_f32_s":
                        addConvertInstruction( ValueTypeConvertion.f2i, javaCodePos, lineNumber );
                        break;
                    case "i64.const":
                        addConstInstruction( Long.parseLong( get( tokens, ++i ) ), ValueType.i64, javaCodePos, lineNumber );
                        break;
                    case "i64.extend_i32_s":
                        addConvertInstruction( ValueTypeConvertion.i2l, javaCodePos, lineNumber );
                        break;
                    case "i64.reinterpret_f64":
                        addConvertInstruction( ValueTypeConvertion.d2l_re, javaCodePos, lineNumber );
                        break;
                    case "i64.trunc_sat_f64_s":
                        addConvertInstruction( ValueTypeConvertion.d2l, javaCodePos, lineNumber );
                        break;
                    case "f32.abs":
                        addNumericInstruction( NumericOperator.abs, ValueType.f32, javaCodePos, lineNumber );
                        break;
                    case "f32.ceil":
                        addNumericInstruction( NumericOperator.ceil, ValueType.f32, javaCodePos, lineNumber );
                        break;
                    case "f32.const":
                        addConstInstruction( Float.parseFloat( get( tokens, ++i ) ), ValueType.f32, javaCodePos, lineNumber );
                        break;
                    case "f32.convert_i32_s":
                        addConvertInstruction( ValueTypeConvertion.i2f, javaCodePos, lineNumber );
                        break;
                    case "f32.div":
                        addNumericInstruction( NumericOperator.div, ValueType.f32, javaCodePos, lineNumber );
                        break;
                    case "f32.floor":
                        addNumericInstruction( NumericOperator.floor, ValueType.f32, javaCodePos, lineNumber );
                        break;
                    case "f32.max":
                        addNumericInstruction( NumericOperator.max, ValueType.f32, javaCodePos, lineNumber );
                        break;
                    case "f32.min":
                        addNumericInstruction( NumericOperator.min, ValueType.f32, javaCodePos, lineNumber );
                        break;
                    case "f32.mul":
                        addNumericInstruction( NumericOperator.mul, ValueType.f32, javaCodePos, lineNumber );
                        break;
                    case "f32.nearest":
                        addNumericInstruction( NumericOperator.nearest, ValueType.f32, javaCodePos, lineNumber );
                        break;
                    case "f32.reinterpret_i32":
                        addConvertInstruction( ValueTypeConvertion.i2f_re, javaCodePos, lineNumber );
                        break;
                    case "f32.copysign":
                        addNumericInstruction( NumericOperator.copysign, ValueType.f32, javaCodePos, lineNumber );
                        break;
                    case "f32.sqrt":
                        addNumericInstruction( NumericOperator.sqrt, ValueType.f32, javaCodePos, lineNumber );
                        break;
                    case "f32.sub":
                        addNumericInstruction( NumericOperator.sub, ValueType.f32, javaCodePos, lineNumber );
                        break;
                    case "f32.trunc":
                        addNumericInstruction( NumericOperator.trunc, ValueType.f32, javaCodePos, lineNumber );
                        break;
                    case "f64.abs":
                        addNumericInstruction( NumericOperator.abs, ValueType.f64, javaCodePos, lineNumber );
                        break;
                    case "f64.ceil":
                        addNumericInstruction( NumericOperator.ceil, ValueType.f64, javaCodePos, lineNumber );
                        break;
                    case "f64.const":
                        addConstInstruction( Double.parseDouble( get( tokens, ++i ) ), ValueType.f64, javaCodePos, lineNumber );
                        break;
                    case "f64.convert_i64_s":
                        addConvertInstruction( ValueTypeConvertion.l2d, javaCodePos, lineNumber );
                        break;
                    case "f64.div":
                        addNumericInstruction( NumericOperator.div, ValueType.f64, javaCodePos, lineNumber );
                        break;
                    case "f64.floor":
                        addNumericInstruction( NumericOperator.floor, ValueType.f64, javaCodePos, lineNumber );
                        break;
                    case "f64.max":
                        addNumericInstruction( NumericOperator.max, ValueType.f64, javaCodePos, lineNumber );
                        break;
                    case "f64.min":
                        addNumericInstruction( NumericOperator.min, ValueType.f64, javaCodePos, lineNumber );
                        break;
                    case "f64.mul":
                        addNumericInstruction( NumericOperator.mul, ValueType.f64, javaCodePos, lineNumber );
                        break;
                    case "f64.nearest":
                        addNumericInstruction( NumericOperator.nearest, ValueType.f64, javaCodePos, lineNumber );
                        break;
                    case "f64.reinterpret_i64":
                        addConvertInstruction( ValueTypeConvertion.l2d_re, javaCodePos, lineNumber );
                        break;
                    case "f64.copysign":
                        addNumericInstruction( NumericOperator.copysign, ValueType.f64, javaCodePos, lineNumber );
                        break;
                    case "f64.sqrt":
                        addNumericInstruction( NumericOperator.sqrt, ValueType.f64, javaCodePos, lineNumber );
                        break;
                    case "f64.sub":
                        addNumericInstruction( NumericOperator.sub, ValueType.f64, javaCodePos, lineNumber );
                        break;
                    case "f64.trunc":
                        addNumericInstruction( NumericOperator.trunc, ValueType.f64, javaCodePos, lineNumber );
                        break;
                    case "ref.is_null":
                        addNumericInstruction( NumericOperator.ifnull, ValueType.i32, javaCodePos, lineNumber );
                        break;
                    case "table.get":
                        addTableInstruction( true, getInt( tokens, ++i), javaCodePos, lineNumber );
                        break;
                    case "table.set":
                        addTableInstruction( false, getInt( tokens, ++i), javaCodePos, lineNumber );
                        break;
                    case "call":
                        StringBuilder builder = new StringBuilder( get( tokens, ++i ) );
                        String str;
                        do {
                            str = get( tokens, ++i );
                            builder.append( str );
                        } while ( !")".equals( str ) );
                        builder.append( get( tokens, ++i ) );
                        FunctionName name = new FunctionName( builder.substring( 1 ) );
                        addCallInstruction( name, javaCodePos, lineNumber );
                        break;
                    case "return":
                        addBlockInstruction( WasmBlockOperator.RETURN, null, javaCodePos, lineNumber );
                        break;
                    case "if":
                        Object data = ValueType.empty;
                        if( "(".equals( get( tokens, i+1 ) ) ) {
                            i++;
                            if( "result".equals( get( tokens, ++i ) ) && ")".equals( get( tokens, ++i + 1) ) ) {
                                data = ValueType.valueOf( get( tokens, i++ ) );
                            } else {
                                throw new WasmException( "Unknown WASM token: " + get( tokens, i-1 ), lineNumber );
                            }
                        }
                        addBlockInstruction( WasmBlockOperator.IF, data, javaCodePos, lineNumber );
                        break;
                    case "else":
                        addBlockInstruction( WasmBlockOperator.ELSE, null, javaCodePos, lineNumber );
                        break;
                    case "end":
                        addBlockInstruction( WasmBlockOperator.END, null, javaCodePos, lineNumber );
                        break;
                    case "drop":
                        addBlockInstruction( WasmBlockOperator.DROP, null, javaCodePos, lineNumber );
                        break;
                    case "i32.load":
                        i = addMemoryInstruction( MemoryOperator.load, ValueType.i32, tokens, i, lineNumber );
                        break;
                    case "i32.load8_u":
                        i = addMemoryInstruction( MemoryOperator.load8_u, ValueType.i32, tokens, i, lineNumber );
                        break;
                    default:
                        throw new WasmException( "Unknown WASM token: " + tok, lineNumber );
                }
            }
        } catch( Exception ex ) {
            throw WasmException.create( ex, lineNumber );
        }
    }

    /**
     * Get the token at given position as int.
     * 
     * @param tokens
     *            the token list
     * @param idx
     *            the position in the tokens
     * @return the int value
     */
    private int getInt( List<String> tokens, @Nonnegative int idx ) {
        return Integer.parseInt( get( tokens, idx ) );
    }

    /**
     * Get the token at given position
     * 
     * @param tokens
     *            the token list
     * @param idx
     *            the position in the tokens
     * @return the token
     */
    @Nonnull
    private String get( List<String> tokens, @Nonnegative int idx ) {
        if( idx >= tokens.size() ) {
            String previous = tokens.get( Math.min( idx, tokens.size() ) - 1 );
            throw new WasmException( "Missing Token in wasm text format after token: " + previous, -1 );
        }
        return tokens.get( idx );
    }

    /**
     * Split the string in tokens.
     * 
     * @param wat
     *            string with wasm text format
     * @return the token list.
     */
    private List<String> splitTokens( @Nullable String wat ) {
        ArrayList<String> tokens = new ArrayList<>();
        int count = wat.length();

        int off = 0;
        for( int i = 0; i < count; i++ ) {
            char ch = wat.charAt( i );
            switch( ch ) {
                case ' ':
                case '\n':
                case '\r':
                case '\t':
                case '(':
                case ')':
                    if( off < i ) {
                        tokens.add( wat.substring( off, i ) );
                    }
                    off = i + 1;
                    switch(ch) {
                        case '(':
                            tokens.add( "(" );
                            break;
                        case ')':
                            tokens.add( ")" );
                            break;
                    }
                    break;
            }
        }
        if( off < count ) {
            tokens.add( wat.substring( off, count ) );
        }
        return tokens;
    }

    /**
     * Parse the optional tokens of a load memory instruction and add it.
     * 
     * @param op
     *            the operation
     * @param type
     *            the type of the static field
     * @param tokens
     *            the token list
     * @param i
     *            the position in the tokens
     * @param lineNumber
     *            the line number in the Java source code
     * @return the current index to the tokens
     */
    private int addMemoryInstruction( MemoryOperator op, ValueType type, List<String> tokens, int i, int lineNumber ) {
        int offset = 0;
        int alignment = 0;
        if( i < tokens.size() ) {
            String str = tokens.get( i + 1 );
            if( str.startsWith( "offset=" ) ) {
                offset = Integer.parseInt( str.substring( 7 ) );
                i++;
            }
            str = tokens.get( i + 1 );
            if( str.startsWith( "align=" ) ) {
                int align = Integer.parseInt( str.substring( 6 ) );
                switch( align ) {
                    case 1:
                        alignment = 0;
                        break;
                    case 2:
                        alignment = 1;
                        break;
                    case 4:
                        alignment = 2;
                        break;
                    default:
                        throw new WasmException( "alignment must be power-of-two", lineNumber );
                }
                i++;
            }
        }
        addMemoryInstruction( op, type, offset, alignment, i, lineNumber );
        return i;
    }
}
