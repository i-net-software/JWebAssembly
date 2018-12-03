/*
 * Copyright 2018 Volker Berlin (i-net software)
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

/**
 * Parser for a Java signature. This can be a method signature or a signature of a field.
 * 
 * @author Volker Berlin
 */
public class ValueTypeParser {
    private final String sig;

    private int          idx;

    /**
     * Create a new parser.
     * 
     * @param javaSignature
     *            the Jav signature
     */
    public ValueTypeParser( String javaSignature ) {
        sig = javaSignature;
        if( javaSignature.startsWith( "(" ) ) {
            idx++;
        }
    }

    /**
     * Get the next value in the signature or null if the parameter are end or the signature is end.
     * 
     * @return next type or null
     */
    public ValueType next() {
        if( idx >= sig.length() ) {
            return null;
        }
        switch( sig.charAt( idx++ ) ) {
            case ')':
                return null;
            case '[': // array
                next();
                return ValueType.anyref;
            case 'L':
                idx = sig.indexOf( ';', idx ) + 1;
                return ValueType.anyref;
            case 'B': // byte
            case 'C': // char
            case 'S': // short
            case 'I': // int
                return ValueType.i32;
            case 'D': // double
                return ValueType.f64;
            case 'F': // float
                return ValueType.f32;
            case 'J': // long
                return ValueType.i64;
            case 'V': // void
                return ValueType.empty;
            default:
                throw new WasmException( "Not supported Java data type in method signature: " + sig.substring( idx - 1 ), -1 );
        }

    }
}
