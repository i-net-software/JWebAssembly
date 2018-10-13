/*
 * Copyright 2017 - 2018 Volker Berlin (i-net software)
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
package de.inetsoftware.jwebassembly.module;

import de.inetsoftware.jwebassembly.WasmException;

/**
 * @author Volker Berlin
 */
public enum ValueType {
    i32(0x7f),
    i64(0x7e),
    f32(0x7d),
    f64(0x7c),
    anyfunc(0x70),
    anyref(0x6f),
    except_ref(0x68),
    func(0x60),
    empty(0x40), // empty block_type
    ;

    private final int code;

    /**
     * Create instance of the enum
     * 
     * @param code
     *            the operation code in WebAssembly
     */
    private ValueType( int code ) {
        this.code = code;
    }

    /**
     * The operation code in WebAssembly.
     * 
     * @return the code
     */
    public int getCode() {
        return code;
    }

    /**
     * Get the WebAssembly value type from a Java signature.
     * 
     * @param javaSignature
     *            the signature
     * @param idx
     *            the index in the signature
     * @return the value type or null if void
     */
    public static ValueType getValueType( String javaSignature, int idx ) {
        String javaType;
        switch( javaSignature.charAt( idx ) ) {
            case '[': // array
                javaType = "array";
                break;
            case 'L':
                javaType = "object";
                break;
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
                return null;
            default:
                javaType = javaSignature.substring( idx, idx + 1 );
        }
        throw new WasmException( "Not supported Java data type in method signature: " + javaType, null, -1 );
    }

}
