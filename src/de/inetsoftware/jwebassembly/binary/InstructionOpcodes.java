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
package de.inetsoftware.jwebassembly.binary;

/**
 * Instruction opcodes of the binary WebAssembly format.
 * 
 * Details can be found at: https://github.com/WebAssembly/design/blob/master/BinaryEncoding.md
 * 
 * @author Volker Berlin
 *
 */
interface InstructionOpcodes {

    static final int UNREACHABLE = 0x00;

    static final int NOP       = 0x01;

    // === Control flow operators ====

    static final int BLOCK     = 0x02;

    static final int LOOP      = 0x03;

    /**
     * If the value on the stack is true then the if block is executed. <br>
     * sequence: IF {return type} <br>
     * return type: can be void (0x40) <br>
     * stack: remove one i32 value and compare it with 0 <br>
     */
    static final int IF        = 0x04;

    static final int ELSE      = 0x05;

    static final int END       = 0x0B;

    /**
     * Break a block/loop <br>
     * sequence: BR {call deep} <br>
     * call deep: How many blocks are break. 0 means the current block<br>
     * stack: no change <br>
     */
    static final int BR        = 0x0C;

    static final int BR_IF     = 0x0D;

    /**
     * - br_table
     * - target_count   - count of entries in the table
     * - target_table   - levels of block breaks
     * - default_target - levels of block breaks for default value
     */
    static final int BR_TABLE  = 0x0E;

    static final int RETURN    = 0x0F;

    static final int CALL      = 0x10;

    static final int CALL_INDIRECT = 0x11;

    static final int DROP      = 0x1A;

    /**
     * select one of two values based on condition
     */
    static final int SELECT    = 0x1B;

    // === Variable access ===========

    static final int GET_LOCAL         = 0x20;

    static final int SET_LOCAL         = 0x21;

    static final int TEE_LOCAL         = 0x22;

    static final int GET_GLOBAL        = 0x23;

    static final int SET_GLOBAL        = 0x24;

    static final int I32_CONST         = 0x41;

    static final int I64_CONST         = 0x42;

    static final int F32_CONST         = 0x43;

    static final int F64_CONST         = 0x44;

    // === numerical operations ======

    static final int I32_EQZ   = 0x45;

    static final int I32_EQ    = 0x46;

    static final int I32_NE    = 0x47;

    static final int I32_LT_S  = 0x48;

    static final int I32_LT_U  = 0x49;

    static final int I32_GT_S  = 0x4A;

    static final int I32_GT_U  = 0x4B;

    static final int I32_LE_S  = 0x4C;

    static final int I32_LE_U  = 0x4D;

    static final int I32_GE_S  = 0x4E;

    static final int I32_GE_U  = 0x4F;

    static final int I64_EQZ   = 0x50;

    static final int I64_EQ    = 0x51;

    static final int I64_NE    = 0x52;

    static final int I64_LT_S  = 0x53;

    static final int I64_LT_U  = 0x54;

    static final int I64_GT_S  = 0x55;

    static final int I64_GT_U  = 0x56;

    static final int I64_LE_S  = 0x57;

    static final int I64_LE_U  = 0x58;

    static final int I64_GE_S  = 0x59;

    static final int I64_GE_U  = 0x5A;

    static final int F32_EQ    = 0x5B;

    static final int F32_NE    = 0x5C;

    static final int F32_LT    = 0x5D;

    static final int F32_GT    = 0x5E;

    static final int F32_LE    = 0x5F;

    static final int F32_GE    = 0x60;

    static final int F64_EQ    = 0x61;

    static final int F64_NE    = 0x62;

    static final int F64_LT    = 0x63;

    static final int F64_GT    = 0x64;

    static final int F64_LE    = 0x65;

    static final int F64_GE    = 0x66;

    static final int I32_CLZ   = 0x67;

    static final int I32_CTZ   = 0x68;

    static final int I32_POPCNT= 0x69;

    static final int I32_ADD   = 0x6A;

    static final int I32_SUB   = 0x6B; 

    static final int I32_MUL   = 0x6C; 

    static final int I32_DIV_S = 0x6D; 

    static final int I32_DIV_U = 0x6E; 

    static final int I32_REM_S = 0x6F;

    static final int I32_REM_U = 0x70;

    static final int I32_AND   = 0x71;

    static final int I32_OR    = 0x72;

    static final int I32_XOR   = 0x73;

    static final int I32_SHL   = 0x74;

    static final int I32_SHR_S = 0x75;

    static final int I32_SHR_U = 0x76;

    static final int I32_ROTL  = 0x77;

    static final int I32_ROTR  = 0x78;

    static final int I64_CLZ   = 0x79;

    static final int I64_CTZ   = 0x7A;

    static final int I64_POPCNT= 0x7B;

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

    static final int I64_ROTL  = 0x89; 

    static final int I64_ROTR  = 0x8A; 

    static final int F32_NEG   = 0x8C; 

    static final int F32_CEIL  = 0x8D; 

    static final int F32_FLOOR = 0x8E; 

    static final int F32_TRUNC = 0x8F; 

    static final int F32_NEAREST= 0x90; 

    static final int F32_SQRT  = 0x91; 

    static final int F32_ADD   = 0x92;

    static final int F32_SUB   = 0x93;

    static final int F32_MUL   = 0x94;

    static final int F32_DIV   = 0x95;

    static final int F32_MIN   = 0x96;

    static final int F32_MAX   = 0x97;

    static final int F32_COPYSIGN= 0x98;

    static final int F64_ABS   = 0x99;

    static final int F64_NEG   = 0x9A;

    static final int F64_CEIL  = 0x9B;

    static final int F64_FLOOR = 0x9C;

    static final int F64_TRUNC = 0x9D;

    static final int F64_NEAREST= 0x9E;

    static final int F64_SQRT  = 0x9F;

    static final int F64_ADD   = 0xA0;

    static final int F64_SUB   = 0xA1;

    static final int F64_MUL   = 0xA2;

    static final int F64_DIV   = 0xA3;

    static final int F64_MIN   = 0xA4;

    static final int F64_MAX   = 0xA5;

    static final int F64_COPYSIGN= 0xA6;

    // === data type conversions =====
    // TargetType_...._SourceType

    static final int I32_WRAP_I64           = 0xA7;

    static final int I32_TRUNC_S_F32        = 0xA8;

    static final int I32_TRUNC_U_F32        = 0xA9;

    static final int I32_TRUNC_S_F64        = 0xAA;

    static final int I32_TRUNC_U_F64        = 0xAB;

    static final int I64_EXTEND_S_I32       = 0xAC;

    static final int I64_EXTEND_U_I32       = 0xAD;

    static final int I64_TRUNC_S_F32        = 0xAE;

    static final int I64_TRUNC_U_F32        = 0xAF;

    static final int I64_TRUNC_S_F64        = 0xB0;

    static final int I64_TRUNC_U_F64        = 0xB1;

    static final int F32_CONVERT_S_I32      = 0xB2;

    static final int F32_CONVERT_U_I32      = 0xB3;

    static final int F32_CONVERT_S_I64      = 0xB4;

    static final int F32_CONVERT_U_I64      = 0xB5;

    static final int F32_DEMOTE_F64         = 0xB6;

    static final int F64_CONVERT_S_I32      = 0xB7;

    static final int F64_CONVERT_U_I32      = 0xB8;

    static final int F64_CONVERT_S_I64      = 0xB9;

    static final int F64_CONVERT_U_I64      = 0xBA;

    static final int F64_PROMOTE_F32        = 0xBB;

    // === numerical operations ======

    static final int REF_NULL               = 0xD0;

    static final int REF_ISNULL             = 0xD1;
}
