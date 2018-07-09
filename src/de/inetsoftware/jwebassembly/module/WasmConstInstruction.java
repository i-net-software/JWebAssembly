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

    private final Number value;

    /**
     * Create an instance of a constant instruction
     * 
     * @param value
     *            the constant value
     * @param javaCodePos
     *            the code position/offset in the Java method
     */
    WasmConstInstruction( Number value, int javaCodePos ) {
        super( javaCodePos );
        this.value = value;
    }

    /**
     * {@inheritDoc}
     */
    public void writeTo( @Nonnull ModuleWriter writer ) throws IOException {
        Class<?> clazz = value.getClass();
        if( clazz == Integer.class ) {
            writer.writeConstInt( ((Integer)value).intValue() );
        } else if( clazz == Long.class ) {
            writer.writeConstLong( ((Long)value).longValue() );
        } else if( clazz == Float.class ) {
            writer.writeConstFloat( ((Float)value).floatValue() );
        } else if( clazz == Double.class ) {
            writer.writeConstDouble( ((Double)value).doubleValue() );
        } else {
            throw new WasmException( "Not supported constant type: " + clazz, null, -1 );
        }
    }
}
