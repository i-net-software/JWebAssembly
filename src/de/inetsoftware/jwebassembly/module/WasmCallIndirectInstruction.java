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

import java.io.IOException;

import javax.annotation.Nonnull;

/**
 * WasmInstruction for a function call.
 * 
 * @author Volker Berlin
 *
 */
class WasmCallIndirectInstruction extends WasmCallInstruction {

    private int virtualFunctionIdx = -1;

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
    WasmCallIndirectInstruction( FunctionName name, int javaCodePos, int lineNumber ) {
        super( name, javaCodePos, lineNumber );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    Type getType() {
        return Type.CallIndirect;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeTo( @Nonnull ModuleWriter writer ) throws IOException {
        if( virtualFunctionIdx < 0 ) {
            super.writeTo( writer );
        } else {
            writer.writeVirtualFunctionCall( getFunctionName(), virtualFunctionIdx );
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    int getPopCount() {
        return super.getPopCount() + 1; // this -> +1
    }
}
