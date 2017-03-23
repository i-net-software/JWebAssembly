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

    static final int RETURN    = 0x0F;

    static final int GET_LOCAL = 0x20;

    static final int SET_LOCAL = 0x21;

    static final int I32_CONST = 0x41;

    static final int I32_ADD   = 0x6A;

}
