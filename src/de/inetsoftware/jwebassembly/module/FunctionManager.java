/*
 * Copyright 2018 Volker Berlin (i-net software)
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

import java.util.HashSet;

import javax.annotation.Nullable;

/**
 * Manage the required function/methods
 * 
 * @author Volker Berlin
 */
public class FunctionManager {

    private HashSet<String>       writtenFunctions = new HashSet<>();

    private HashSet<FunctionName> toWriteLater     = new HashSet<>();

    /**
     * Mark the a function as written to the wasm file.
     * 
     * @param name
     *            the function name
     */
    void writeFunction( FunctionName name ) {
        toWriteLater.remove( name );
        writtenFunctions.add( name.signatureName );
    }

    /**
     * Mark a function as used/called.
     * 
     * @param name
     *            the function name
     */
    void functionCall( FunctionName name ) {
        if( !writtenFunctions.contains( name.signatureName ) ) {
            toWriteLater.add( name );
        }
    }

    /**
     * Get the first FunctionName that is required but was not written.
     * 
     * @return the FunctionName or null
     */
    @Nullable
    FunctionName nextWriteLater() {
        if( toWriteLater.isEmpty() ) {
            return null;
        }
        return toWriteLater.iterator().next();
    }

    /**
     * if the given function is required but was not written.
     * 
     * @param name
     *            the function name
     * @return true, if the function on the to do list
     */
    boolean isToWrite( FunctionName name ) {
        return toWriteLater.contains( name );
    }
}
