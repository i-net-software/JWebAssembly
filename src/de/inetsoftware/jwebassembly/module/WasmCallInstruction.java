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

import javax.annotation.Nonnull;

import de.inetsoftware.classparser.Member;
import de.inetsoftware.jwebassembly.wasm.ValueType;
import de.inetsoftware.jwebassembly.wasm.ValueTypeParser;

/**
 * WasmInstruction for a function call.
 * 
 * @author Volker Berlin
 *
 */
class WasmCallInstruction extends WasmInstruction {

    private final Member       method;

    private ValueType          valueType;

    private final FunctionName name;

    private int                paramCount = -1;

    /**
     * Create an instance of a function call instruction
     * 
     * @param method
     *            the reference to the Java method
     * @param javaCodePos
     *            the code position/offset in the Java method
     */
    WasmCallInstruction( Member method, int javaCodePos ) {
        super( javaCodePos );
        this.method = method;
        this.name = new FunctionName( method );
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
     * {@inheritDoc}
     */
    public void writeTo( @Nonnull ModuleWriter writer ) throws IOException {
        writer.writeFunctionCall( name );
    }

    /**
     * {@inheritDoc}
     */
    ValueType getPushValueType() {
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
        ValueTypeParser parser = new ValueTypeParser( method.getType() );
        paramCount = 0;
        while( parser.next() != null ) {
            paramCount++;
        }
        valueType = (ValueType)parser.next();
        ValueType type;
        while( (type = (ValueType)parser.next()) != null ) {
            valueType = type;
            paramCount--;
        }
    }
}
