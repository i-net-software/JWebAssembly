/*
   Copyright 2019 Volker Berlin (i-net software)

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

import java.util.function.Function;

/**
 * Synthetic/dynamic method based on import annotation.
 * 
 * @author Volker Berlin
 */
class ImportSyntheticFunctionName extends SyntheticFunctionName {

    private final Function<String, Object> importAnannotation;

    /**
     * create a new instance
     * 
     * @param className
     *            the Java class name
     * @param name
     *            the function name
     * @param signature
     *            the method signature, first the parameters, then null and the the return types
     * @param importAnannotation
     *            the annotations
     */
    ImportSyntheticFunctionName( String className, String name, String signature, Function<String, Object> importAnannotation ) {
        super( className, name, signature );
        this.importAnannotation = importAnannotation;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected boolean hasWasmCode() {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Function<String, Object> getAnnotation() {
        return importAnannotation;
    }
}
