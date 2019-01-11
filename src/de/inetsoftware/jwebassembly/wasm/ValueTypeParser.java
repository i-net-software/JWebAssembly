/*
 * Copyright 2018 - 2019 Volker Berlin (i-net software)
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
import de.inetsoftware.jwebassembly.module.TypeManager;

/**
 * Parser for a Java signature. This can be a method signature or a signature of a field.
 * 
 * @author Volker Berlin
 */
public class ValueTypeParser {
    private final String sig;

    private int          idx;

    private TypeManager  types;

    /**
     * Create a new parser.
     * 
     * @param javaSignature
     *            the Java signature
     */
    public ValueTypeParser( String javaSignature ) {
        this( javaSignature, null );
    }

    /**
     * Create a new parser.
     * 
     * @param javaSignature
     *            the Java signature
     * @param types
     *            the optional type manager
     */
    public ValueTypeParser( String javaSignature, TypeManager types ) {
        this.sig = javaSignature;
        this.types = types;
        if( javaSignature.startsWith( "(" ) ) {
            idx++;
        }
    }

    /**
     * Get the next value in the signature or null if the parameter are end or the signature is end.
     * 
     * @return next type or null
     */
    public StorageType next() {
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
                int idx2 = sig.indexOf( ';', idx );
                String name = sig.substring( idx, idx2 );
                idx = idx2 + 1;
                return types == null ? ValueType.anyref : types.valueOf( name );
            case 'Z': // boolean
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
