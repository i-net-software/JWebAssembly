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

import java.util.Iterator;
import java.util.NoSuchElementException;

import de.inetsoftware.jwebassembly.WasmException;
import de.inetsoftware.jwebassembly.module.TypeManager;

/**
 * Parser for a Java signature. This can be a method signature or a signature of a field.
 * 
 * @author Volker Berlin
 */
public class ValueTypeParser implements Iterator<AnyType> {
    private final String sig;

    private int               idx;

    private final TypeManager types;

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
     * {@inheritDoc}
     */
    public boolean hasNext() {
        return idx < sig.length();
    }

    /**
     * Get the next value in the signature or null if the parameter are end or the signature is end.
     * 
     * @return next type or null
     */
    public AnyType next() {
        return next( false );
    }

    /**
     * Get the next value in the signature or null if the parameter are end or the signature is end.
     * 
     * @param isArray
     *            true, if this is an element type of an array
     * @return next type or null
     */
    private AnyType next( boolean isArray ) {
        if( !hasNext() ) {
            throw new NoSuchElementException();
        }
        switch( sig.charAt( idx++ ) ) {
            case ')':
                return null;
            case '[': // array
                return types.arrayType( next( true ) );
            case 'L':
                int idx2 = sig.indexOf( ';', idx );
                String name = sig.substring( idx, idx2 );
                idx = idx2 + 1;
                return types.valueOf( name );
            case 'Z': // boolean
            case 'B': // byte
                return isArray ? ValueType.i8 : ValueType.i32;
            case 'C': // char
            case 'S': // short
                return isArray ? ValueType.i16 : ValueType.i32;
            case 'I': // int
                return ValueType.i32;
            case 'D': // double
                return ValueType.f64;
            case 'F': // float
                return ValueType.f32;
            case 'J': // long
                return ValueType.i64;
            case 'V': // void
                return null;
            default:
                throw new WasmException( "Not supported Java data type in method signature: " + sig.substring( idx - 1 ), -1 );
        }

    }
}
