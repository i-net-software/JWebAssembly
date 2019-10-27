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
package de.inetsoftware.jwebassembly.javascript;

import java.util.function.Function;
import java.util.function.Supplier;

import de.inetsoftware.jwebassembly.module.ArraySyntheticFunctionName;
import de.inetsoftware.jwebassembly.wasm.AnyType;

/**
 * Synthetic JavaScript import function.
 * 
 * @author Volker Berlin
 *
 */
public class JavaScriptSyntheticFunctionName extends ArraySyntheticFunctionName {

    private final Supplier<String> js;

    /**
     * Create a synthetic function which based on imported, dynamic generated JavaScript.
     * 
     * @param module
     *            the module name
     * @param functionName
     *            the name of the function
     * @param js
     *            the dynamic JavaScript as a lambda expression
     * @param signature
     *            the types of the signature
     */
    public JavaScriptSyntheticFunctionName( String module, String functionName, Supplier<String> js, AnyType... signature ) {
        super( module, functionName, signature );
        this.js = js;
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
        return ( key ) -> {
            switch( key ) {
                case JavaScriptWriter.JAVA_SCRIPT_CONTENT:
                    return js.get();
                default:
            }
            return null;
        };
    }
}
