/*
   Copyright 2018 Volker Berlin (i-net software)

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

import de.inetsoftware.jwebassembly.WasmException;
import de.inetsoftware.jwebassembly.module.NumericOperator;
import de.inetsoftware.jwebassembly.module.ValueType;
import de.inetsoftware.jwebassembly.module.ValueTypeConvertion;
import de.inetsoftware.jwebassembly.module.WasmBlockOperator;
import de.inetsoftware.jwebassembly.module.WasmCodeBuilder;

/**
 * Parser for text format of a function.
 * 
 * @author Volker Berlin
 */
public class WatParser extends WasmCodeBuilder {

    public WatParser() {
    }

    /**
     * Parse the given wasm text format and generate a list of WasmInstuctions
     * 
     * @param wat
     *            the text format content of a function
     */
    public void parse( String wat, int lineNumber ) {
        try {
            reset();

            List<String> tokens = splitTokens( wat );
            for( int i = 0; i < tokens.size(); i++ ) {
                int javaCodePos = i;
                String tok = tokens.get( i );
                switch( tok ) {
                    case "get_local":
                        addLocalInstruction( true, getInt( tokens, ++i), javaCodePos );
                        break;
                    case "set_local":
                        addLocalInstruction( false, getInt( tokens, ++i), javaCodePos );
                        break;
//                    case "get_global":
//                        addGlobalInstruction( true, ref, javaCodePos );
//                        break;
                    case "i32.const":
                        addConstInstruction( getInt( tokens, ++i), ValueType.i32, javaCodePos );
                        break;
                    case "i32.add":
                        addNumericInstruction( NumericOperator.add, ValueType.i32, javaCodePos );
                        break;
                    case "f32.max":
                        addNumericInstruction( NumericOperator.max, ValueType.f32, javaCodePos );
                        break;
                    case "f64.max":
                        addNumericInstruction( NumericOperator.max, ValueType.f64, javaCodePos );
                        break;
                    case "i64.extend_s/i32":
                        addConvertInstruction( ValueTypeConvertion.i2l, javaCodePos );
                        break;
//                    case "call":
//                        addCallInstruction( method, javaCodePos );
//                        break;
                    case "return":
                        addBlockInstruction( WasmBlockOperator.RETURN, null, javaCodePos );
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
                    if( off < i ) {
                        tokens.add( wat.substring( off, i ) );
                    }
                    off = i + 1;
                    break;
            }
        }
        if( off < count ) {
            tokens.add( wat.substring( off, count ) );
        }
        return tokens;
    }
}
