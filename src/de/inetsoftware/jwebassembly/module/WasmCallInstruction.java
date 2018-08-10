/*
   Copyright 2018 Volker Berlin (i-net software)

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

import java.io.IOException;

import javax.annotation.Nonnull;

import de.inetsoftware.classparser.ConstantRef;

/**
 * WasmInstruction for a function call.
 * 
 * @author Volker Berlin
 *
 */
class WasmCallInstruction extends WasmInstruction {

    private final ConstantRef method;

    private final ValueType valueType;

    /**
     * Create an instance of a function call instruction
     * 
     * @param method
     *            the reference to the Java method
     * @param javaCodePos
     *            the code position/offset in the Java method
     */
    WasmCallInstruction( ConstantRef method, int javaCodePos ) {
        super( javaCodePos );
        this.method = method;
        String signature = method.getType();
        this.valueType = ModuleGenerator.getValueType(  signature, signature.indexOf( ')' ) + 1 );
    }

    /**
     * {@inheritDoc}
     */
    public void writeTo( @Nonnull ModuleWriter writer ) throws IOException {
        writer.writeFunctionCall( new FunctionName( method ).signatureName );
    }


    /**
     * {@inheritDoc}
     */
    ValueType getPushValueType() {
        return valueType;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    int getPopCount() {
        String signature = method.getType();
        int paramCount = 0;
        ValueType type = null;
        for( int i = 1; i < signature.length(); i++ ) {
            if( signature.charAt( i ) == ')' ) {
                return paramCount;
            }
            paramCount++;
            ModuleGenerator.getValueType(  signature, i );
        }
        throw new Error(); 
    }
}
