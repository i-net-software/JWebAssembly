/*
 * Copyright 2017 - 2020 Volker Berlin (i-net software)
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
    v128(-0x05),
    bool(-0x06),
    i8(-0x06),
    i16(-0x07),
    u16(-0x07),
    funcref(-0x10),
    externref(-0x11),
    anyref(-0x12),
    eqref(-0x13),
    optref(-0x14),
    ref(-0x15),
    exnref(-0x18), // https://github.com/WebAssembly/exception-handling/blob/master/proposals/Exceptions.md
    func(-0x20),
    struct(-0x21),
    array(-0x22),
    empty(-0x40), // empty/void block_type
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

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isRefType() {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isSubTypeOf( AnyType type ) {
        return type == this;
    }
}
