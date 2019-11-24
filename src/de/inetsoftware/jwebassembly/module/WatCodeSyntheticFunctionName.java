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

import javax.annotation.Nonnull;

import de.inetsoftware.jwebassembly.wasm.AnyType;
import de.inetsoftware.jwebassembly.watparser.WatParser;

/**
 * Synthetic/dynamic method based on WAT code (WASM in text form).
 * 
 * @author Volker Berlin
 */
class WatCodeSyntheticFunctionName extends ArraySyntheticFunctionName {

    private final String    code;

    /**
     * Create a new instance.
     * 
     * @param name
     *            the function name
     * @param code
     *            the WAT code (WASM in text form)
     * @param signatureTypes
     *            the method signature, first the parameters, then null and the the return types
     */
    public WatCodeSyntheticFunctionName( String name, String code, AnyType... signatureTypes ) {
        super( "", name, signatureTypes );
        this.code = code;
    }

    /**
     * Create a new instance.
     * 
     * @param className
     *            the Java class name
     * @param name
     *            the function name
     * @param code
     *            the WAT code (WASM in text form)
     * @param signature
     *            the string signature
     * @param signatureTypes
     *            the method signature, first the parameters, then null and the the return types
     */
    public WatCodeSyntheticFunctionName( String className, String name, String signature, @Nonnull String code, AnyType... signatureTypes ) {
        super( className, name, signature, signatureTypes );
        this.code = code;
    }

    /**
     * Get Wat code, can be overridden.
     * 
     * @return the code
     */
    @Nonnull
    protected String getCode() {
        return code;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected boolean hasWasmCode() {
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected WasmCodeBuilder getCodeBuilder( WatParser watParser ) {
        watParser.parse( getCode(), null, -1 );
        return watParser;
    }
}
