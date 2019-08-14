/*
 * Copyright 2017 - 2019 Volker Berlin (i-net software)
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

/**
 * @author Volker Berlin
 */
public enum ValueType implements AnyType {
    i32(-0x01),
    i64(-0x02),
    f32(-0x03),
    f64(-0x04),
    i8(-0x05), //TODO dummy value for https://github.com/WebAssembly/gc
    i16(-0x06), //TODO dummy value for https://github.com/WebAssembly/gc
    anyfunc(-0x10),
    anyref(-0x11),
    ref_type(-0x12 ), // 0x6E https://github.com/lars-t-hansen/moz-gc-experiments/blob/master/version2.md
    except_ref(-0x18), // https://github.com/WebAssembly/exception-handling/blob/master/proposals/Exceptions.md
    func(-0x20),
    struct(-0x30),
    empty(-0x40), // empty block_type
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
    @Override
    public int getCode() {
        return code;
    }
}
