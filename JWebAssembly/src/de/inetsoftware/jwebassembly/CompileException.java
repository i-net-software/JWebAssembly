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
package de.inetsoftware.jwebassembly;

/**
 * If there any error occur on converting a class file to a WebAssembly module.
 * 
 * @author Volker Berlin
 *
 */
public class CompileException extends Exception {

    /**
     * Create a new instance with a cause.
     * 
     * @param cause
     *            the cause
     */
    CompileException( Throwable cause ) {
        super( cause );
    }

    /**
     * Create a wrapped exception needed.
     * 
     * @param cause
     *            the wrapped cause
     * @return a new instance
     */
    public static CompileException create( Throwable cause ) {
        if( cause instanceof CompileException ) {
            return (CompileException)cause;
        }
        return new CompileException( cause );
    }
}
