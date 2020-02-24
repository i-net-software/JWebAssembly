/*
   Copyright 2019 - 2020 Volker Berlin (i-net software)

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

import de.inetsoftware.jwebassembly.module.TypeManager.StructType;

/**
 * WasmInstruction for a function call.
 * 
 * @author Volker Berlin
 *
 */
abstract class WasmCallIndirectInstruction extends WasmCallInstruction {

    private final StructType            type;

    private int                         tempVarIdx;

    /**
     * Create an instance of a function call instruction
     * 
     * @param name
     *            the function name that should be called
     * @param javaCodePos
     *            the code position/offset in the Java method
     * @param lineNumber
     *            the line number in the Java source code
     * @param types
     *            the type manager
     */
    WasmCallIndirectInstruction( FunctionName name, int javaCodePos, int lineNumber, TypeManager types ) {
        super( name, javaCodePos, lineNumber, types, true );
        this.type = types.valueOf( name.className );
    }

    /**
     * Get the type of this.
     * 
     * @return the type
     */
    StructType getThisType() {
        return type;
    }

    /**
     * Set the variable index on which this can be found.
     * @param tempVarIdx the index
     */
    void setVariableIndexOfThis( int tempVarIdx ) {
        this.tempVarIdx = tempVarIdx;
    }

    /**
     * Get the variable index on which this can be found.
     * 
     * @return the index of the variable
     */
    int getVariableIndexOfThis() {
        return tempVarIdx;
    }

    /**
     * if this call is executed virtual or if is was optimized.
     * 
     * @return true, virtual call
     */
    abstract boolean isVirtual();
}
