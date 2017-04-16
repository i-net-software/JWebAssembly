/*
 * Copyright 2017 Volker Berlin (i-net software)
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
package de.inetsoftware.jwebassembly.binary;

/**
 * Instruction opcodes of the binary WebAssembly format.
 * 
 * @author Volker Berlin
 *
 */
interface InstructionOpcodes {

    // === Control flow operators ====

    static final int BLOCK     = 0x02;

    static final int LOOP      = 0x03;

    static final int IF        = 0x04;

    static final int ELSE      = 0x05;

    static final int END       = 0x0B;

    static final int RETURN    = 0x0F;

    // === Variable access ===========

    static final int GET_LOCAL = 0x20;

    static final int SET_LOCAL = 0x21;

    static final int I32_CONST = 0x41;

    static final int I64_CONST = 0x42;

    static final int F32_CONST = 0x43;

    static final int F64_CONST = 0x44;

    // === numerical operations ======

    static final int I32_ADD   = 0x6A;

    static final int I32_SUB   = 0x6B; 

    static final int I32_MUL   = 0x6C; 

    static final int I32_DIV_S = 0x6D; 

    static final int I32_REM_S = 0x6F;

    static final int I32_AND   = 0x71;

    static final int I32_OR    = 0x72;

    static final int I32_XOR   = 0x73;

    static final int I32_SHL   = 0x74;

    static final int I32_SHR_S = 0x75;

    static final int I32_SHR_U = 0x76;

    static final int I64_ADD   = 0x7C;

    static final int I64_SUB   = 0x7D;

    static final int I64_MUL   = 0x7E; 

    static final int I64_DIV_S = 0x7F; 

    static final int I64_REM_S = 0x81;

    static final int I64_AND   = 0x83; 

    static final int I64_OR    = 0x84; 

    static final int I64_XOR   = 0x85; 

    static final int I64_SHL   = 0x86; 

    static final int I64_SHR_S = 0x87; 

    static final int I64_SHR_U = 0x88; 

    static final int F32_ADD   = 0x92;

    static final int F32_SUB   = 0x93;

    static final int F32_MUL   = 0x94;

    static final int F32_DIV   = 0x95;

    static final int F64_ADD   = 0xA0;

    static final int F64_SUB   = 0xA1;

    static final int F64_MUL   = 0xA2;

    static final int F64_DIV   = 0xA3;

    // === data type conversions =====

    static final int I32_WRAP_I64           = 0xA7;

}
