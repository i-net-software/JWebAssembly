/*
   Copyright 2022 Volker Berlin (i-net software)

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

import de.inetsoftware.jwebassembly.wasm.AnyType;
import de.inetsoftware.jwebassembly.wasm.ValueType;

/**
 * WasmInstruction for constant Strings.
 * 
 * @author Volker Berlin
 *
 */
class WasmStringInstruction extends WasmInstruction {

    @Nonnull
    private final String       value;

    private final Integer      id;

    private final FunctionName function;

    private final AnyType      valueType;

    /**
     * Create an instance of a string constant instruction
     * 
     * @param value
     *            the constant string value
     * @param strings
     *            the string manager
     * @param types
     *            the type manager
     * @param javaCodePos
     *            the code position/offset in the Java method
     * @param lineNumber
     *            the line number in the Java source code
     */
    WasmStringInstruction( @Nonnull String value, StringManager strings, TypeManager types, int javaCodePos, int lineNumber ) {
        super( javaCodePos, lineNumber );
        this.value = value;
        id = strings.get( value );
        function = strings.getStringConstantFunction();
        valueType = types.valueOf( "java/lang/String" );
    }

    /**
     * Get the string value
     * @return the value
     */
    @Nonnull
    String getValue() {
        return value;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    Type getType() {
        return Type.String;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    void writeTo( @Nonnull ModuleWriter writer ) throws IOException {
        writer.writeConst( id, ValueType.i32 );
        String comment = isAscii( value ) ? value : null;
        writer.writeFunctionCall( function, comment );
    }

    /**
     * If the string contains only ASCCI characters
     * 
     * @param str
     *            the staring
     * @return true, if only ASCII
     */
    private static boolean isAscii( String str ) {
        int length = str.length();
        for( int i = 0; i < length; i++ ) {
            int c = str.charAt( i );
            if( c >= 0x20 && c < 0x7F ) {
                continue;
            }
            return false;
        }
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    AnyType getPushValueType() {
        return valueType;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    int getPopCount() {
        return 0;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    AnyType[] getPopValueTypes() {
        return null;
    }

    /**
     * Only used for debugging
     */
    @Override
    public String toString() {
        return getClass().getSimpleName() + ": " + value;
    }
}
