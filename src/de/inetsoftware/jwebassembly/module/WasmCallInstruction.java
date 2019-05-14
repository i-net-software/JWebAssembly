/*
   Copyright 2018 - 2019 Volker Berlin (i-net software)

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
import java.util.Iterator;

import javax.annotation.Nonnull;

import de.inetsoftware.jwebassembly.wasm.AnyType;

/**
 * WasmInstruction for a function call.
 * 
 * @author Volker Berlin
 *
 */
class WasmCallInstruction extends WasmInstruction {

    private AnyType      valueType;

    private FunctionName name;

    private int          paramCount = -1;

    /**
     * Create an instance of a function call instruction
     * 
     * @param name
     *            the function name that should be called
     * @param javaCodePos
     *            the code position/offset in the Java method
     * @param lineNumber
     *            the line number in the Java source code
     */
    WasmCallInstruction( FunctionName name, int javaCodePos, int lineNumber ) {
        super( javaCodePos, lineNumber );
        this.name = name;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    Type getType() {
        return Type.Call;
    }

    /**
     * Get the function name that should be called
     * 
     * @return the name
     */
    @Nonnull
    FunctionName getFunctionName() {
        return name;
    }

    /**
     * Mark the function as needed in the functions manager and replace the function name with a possible super name.
     * 
     * @param functions
     *            the function manager
     */
    void markAsNeeded( @Nonnull FunctionManager functions ) {
        name = functions.markAsNeeded( name );
    }

    /**
     * {@inheritDoc}
     */
    public void writeTo( @Nonnull ModuleWriter writer ) throws IOException {
        writer.writeFunctionCall( name );
    }

    /**
     * {@inheritDoc}
     */
    AnyType getPushValueType() {
        countParams();
        return valueType;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    int getPopCount() {
        countParams();
        return paramCount;
    }

    /**
     * Count the parameters in the signature
     */
    private void countParams() {
        if( paramCount >= 0 ) {
            return;
        }
        Iterator<AnyType> parser = name.getSignature();
        paramCount = 0;
        while( parser.next() != null ) {
            paramCount++;
        }
        valueType = parser.next();
        while( parser.hasNext() ) {
            valueType = parser.next();
            paramCount--;
        }
    }
}
