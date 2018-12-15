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
package de.inetsoftware.jwebassembly.wasm;

/**
 * @author Volker Berlin
 */
public enum NumericOperator {
    add,
    sub,
    neg,
    mul,
    div,
    rem,
    and,
    or,
    xor,
    shl,
    shr_s,
    shr_u,
    eqz,
    eq,
    ne,
    gt,
    lt,
    le,
    ge,
    max,
    ifnull,
    ifnonnull,
}
