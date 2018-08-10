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

import de.inetsoftware.jwebassembly.WasmException;

/**
 * WasmInstruction for constant values.
 * 
 * @author Volker Berlin
 *
 */
class WasmConstInstruction extends WasmInstruction {

    private final Number    value;

    private final ValueType valueType;

    /**
     * Create an instance of a constant instruction
     * 
     * @param value
     *            the constant value
     * @param valueType
     *            the data type of the number
     * @param javaCodePos
     *            the code position/offset in the Java method
     */
    WasmConstInstruction( Number value, ValueType valueType, int javaCodePos ) {
        super( javaCodePos );
        this.value = value;
        this.valueType = valueType;
    }

    /**
     * Create an instance of a constant instruction
     * 
     * @param value
     *            the constant value
     * @param javaCodePos
     *            the code position/offset in the Java method
     */
    WasmConstInstruction( Number value, int javaCodePos ) {
        this( value, getValueType( value ), javaCodePos );
    }

    /**
     * Find the matching ValueType for the given value.
     * 
     * @param value
     *            the constant value
     * @return the ValueType
     */
    @Nonnull
    private static ValueType getValueType( Number value ) {
        Class<?> clazz = value.getClass();
        if( clazz == Integer.class ) {
            return ValueType.i32;
        } else if( clazz == Long.class ) {
            return ValueType.i64;
        } else if( clazz == Float.class ) {
            return ValueType.f32;
        } else if( clazz == Double.class ) {
            return ValueType.f64;
        } else {
            throw new WasmException( "Not supported constant type: " + clazz, null, -1 );
        }

    }

    /**
     * {@inheritDoc}
     */
    public void writeTo( @Nonnull ModuleWriter writer ) throws IOException {
        writer.writeConst( value, valueType );
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
        return 0;
    }
}
