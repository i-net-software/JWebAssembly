/*
   Copyright 2020 Volker Berlin (i-net software)

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.

*/
package de.inetsoftware.jwebassembly.module;

import de.inetsoftware.jwebassembly.api.annotation.WasmTextCode;

/**
 * Code that will directly embedded in the compiler output. The code can be written in Java or via Wat code. This is not
 * a replacement of runtime API. It emulate the behavior of the Java VM.
 * <p>
 * The class should not have any references to classes of the JWebAssembly compiler to prevent that parts of the
 * compiler will be embedded.
 *
 * @author Volker Berlin
 */
class WasmEmbbeddedCode {

    /**
     * Integer division that throw an ArithmeticException on a division by zero instead a trap of the WebAssembly
     * engine.
     * 
     * @param quotient
     *            the quotient of the operation
     * @param divisor
     *            the divisor of the operation
     * @return the result
     */
    @WasmTextCode( "local.get 1 " + //
                    "i32.eqz " + //
                    "if " + //
                    "call $de/inetsoftware/jwebassembly/module/WasmEmbbeddedCode.createDivByZero()Ljava/lang/ArithmeticException; " + //
                    "throw " + //
                    "end " + //
                    "local.get 0 " + //
                    "local.get 1 " + //
                    "i32.div_s " + //
                    "return" )
    static native int idiv( int quotient, int divisor );

    /**
     * Long division that throw an ArithmeticException on a division by zero instead a trap of the WebAssembly engine.
     * 
     * @param quotient
     *            the quotient
     * @param divisor
     *            the divisior
     * @return the result
     */
    @WasmTextCode( "local.get 1 " + //
                    "i64.eqz " + //
                    "if " + //
                    "call $de/inetsoftware/jwebassembly/module/WasmEmbbeddedCode.createDivByZero()Ljava/lang/ArithmeticException; " + //
                    "throw " + //
                    "end " + //
                    "local.get 0 " + //
                    "local.get 1 " + //
                    "i64.div_s " + //
                    "return" )
    static native long ldiv( long quotient, long divisor );

    /**
     * Create an ArithmeticException with message "/ by zero"
     * 
     * @return the exception
     */
    static ArithmeticException createDivByZero() {
        return new ArithmeticException( "/ by zero" );
    }
}
