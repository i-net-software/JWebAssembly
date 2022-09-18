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

import de.inetsoftware.classparser.ConstantClass;
import de.inetsoftware.jwebassembly.wasm.AnyType;
import de.inetsoftware.jwebassembly.wasm.ValueType;

/**
 * WasmInstruction for constant class.
 * 
 * @author Volker Berlin
 *
 */
class WasmConstClassInstruction extends WasmInstruction {

    @Nonnull
    private final String       className;

    private final int          id;

    private final FunctionName function;

    private final AnyType      valueType;

    /**
     * Create an instance of a class constant instruction
     * 
     * @param value
     *            the constant class value
     * @param types
     *            the type manager
     * @param javaCodePos
     *            the code position/offset in the Java method
     * @param lineNumber
     *            the line number in the Java source code
     */
    WasmConstClassInstruction( @Nonnull ConstantClass value, TypeManager types, int javaCodePos, int lineNumber ) {
        super( javaCodePos, lineNumber );
        this.className = value.getName();
        id = types.valueOf( className ).getClassIndex();
        function = types.getClassConstantFunction();
        types.options.functions.markAsNeeded( function, false );
        valueType = types.valueOf( "java/lang/Class" );
    }

    /**
     * Get the className value
     * 
     * @return the value
     */
    @Nonnull
    String getValue() {
        return className;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    Type getType() {
        return Type.Clazz;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    void writeTo( @Nonnull ModuleWriter writer ) throws IOException {
        writer.writeConst( id, ValueType.i32 );
        writer.writeFunctionCall( function, className );
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
        return getClass().getSimpleName() + ": " + className;
    }
}
