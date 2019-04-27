/*
 * Copyright 2017 - 2019 Volker Berlin (i-net software)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.inetsoftware.jwebassembly.binary;

import java.io.ByteArrayOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;

import de.inetsoftware.jwebassembly.WasmException;
import de.inetsoftware.jwebassembly.wasm.AnyType;
import de.inetsoftware.jwebassembly.wasm.ValueType;

/**
 * @author Volker Berlin
 */
class WasmOutputStream extends FilterOutputStream {

    private int count;

    /**
     * Create a in memory stream.
     */
    WasmOutputStream() {
        super( new ByteArrayOutputStream() );
    }

    /**
     * Create a wrapped stream.
     * 
     * @param output
     *            the target of data
     */
    WasmOutputStream( OutputStream output ) {
        super( output );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void write( int b ) throws IOException {
        out.write( b );
        count++;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void write( byte[] b, int off, int len ) throws IOException {
        out.write( b, off, len );
        count += len;
    }

    /**
     * Write a binary operation code.
     * 
     * @param op
     *            a constant from {@link InstructionOpcodes}
     * @throws IOException
     *             if an I/O error occurs.
     */
    public void writeOpCode( int op ) throws IOException {
        if( op > 255 ) {
            write( op >> 8 );
        }
        write( op );
    }

    /**
     * Write a value type.
     * 
     * @param type
     *            a type constant
     * @throws IOException
     *             if an I/O error occurs.
     */
    public void writeValueType( AnyType type ) throws IOException {
        writeVarint( type.getCode() );
    }

    /**
     * Write the value type. If it is a struct type then as reference type.
     * 
     * @param type
     *            a type constant
     * @throws IOException
     *             if an I/O error occurs.
     */
    public void writeRefValueType( AnyType type ) throws IOException {
        if( type.getCode() >= 0 ) {
            writeValueType( ValueType.ref_type );
        }
        writeValueType( type );
    }

    /**
     * Write the default/initial value for a type.
     * 
     * @param type
     *            the type
     * @throws IOException
     *             if an I/O error occurs.
     */
    public void writeDefaultValue( AnyType type ) throws IOException {
        if( type.getCode() < 0 ) {
            ValueType valueType = (ValueType)type;
            switch( valueType ) {
                case i32:
                case i64:
                case f32:
                case f64:
                    writeConst( 0, valueType );
                    break;
                case i8:
                case i16:
                    writeConst( 0, ValueType.i32 );
                    break;
                case anyref:
                    writeOpCode( InstructionOpcodes.REF_NULL );
                    break;
                default:
                    throw new WasmException( "Not supported storage type: " + type, -1 );
            }
        } else {
            writeOpCode( InstructionOpcodes.REF_NULL );
        }
    }

    /**
     * Write a integer little endian (ever 4 bytes)
     * 
     * @param value
     *            the value
     * @throws IOException
     *             if an I/O error occurs.
     */
    void writeInt32( int value ) throws IOException {
        write( value >>> 0 );
        write( value >>> 8 );
        write( value >>> 16 );
        write( value >>> 24 );
    }

    /**
     * Write an unsigned integer.
     * 
     * @param value
     *            the value
     * @throws IOException
     *             if an I/O error occurs.
     */
    void writeVaruint32( @Nonnegative int value ) throws IOException {
        if( value < 0 ) {
            throw new IOException( "Invalid negative value" );
        }
        do {
            int b = value & 0x7F; // low 7 bits
            value >>= 7;
            if( value != 0 ) { /* more bytes to come */
                b |= 0x80;
            }
            write( b );
        } while( value != 0 );
    }

    /**
     * Write an integer value.
     * 
     * @param value
     *            the value
     * @throws IOException
     *             if an I/O error occurs.
     */
    void writeVarint( long value ) throws IOException {
        while( true ) {
            int b = (int)value & 0x7F;
            value >>= 7;

            /* sign bit of byte is second high order bit (0x40) */
            if( (value == 0 && (b & 0x40) == 0) || (value == -1 && (b & 0x40) != 0) ) {
                write( b );
                return;
            } else {
                write( b | 0x80 );
            }
        }
    }

    /**
     * Write an float value.
     * 
     * @param value
     *            the value
     * @throws IOException
     *             if an I/O error occurs.
     */
    void writeFloat( float value ) throws IOException {
        int i = Float.floatToIntBits( value );
        writeInt32( i );
    }

    /**
     * Write an double value.
     * 
     * @param value
     *            the value
     * @throws IOException
     *             if an I/O error occurs.
     */
    void writeDouble( double value ) throws IOException {
        long l = Double.doubleToLongBits(value);
        writeInt32( (int)l );
        writeInt32( (int)(l >>> 32) );
    }

    /**
     * Write a constant number value
     * 
     * @param value
     *            the value
     * @param valueType
     *            the data type of the number
     * @throws IOException
     *             if any I/O error occur
     */
    void writeConst( Number value, ValueType valueType ) throws IOException {
        switch( valueType ) {
            case i32:
                this.writeOpCode( InstructionOpcodes.I32_CONST );
                this.writeVarint( value.intValue() );
                break;
            case i64:
                this.writeOpCode( InstructionOpcodes.I64_CONST );
                this.writeVarint( value.longValue() );
                break;
            case f32:
                this.writeOpCode( InstructionOpcodes.F32_CONST );
                this.writeFloat( value.floatValue() );
                break;
            case f64:
                this.writeOpCode( InstructionOpcodes.F64_CONST );
                this.writeDouble( value.doubleValue() );
                break;
            default:
                throw new Error( valueType + " " + value );
        }
    }

    /**
     * Write a string as UTF8 encoded.
     * 
     * @param str
     *            the string
     * @throws IOException
     *             if any I/O error occur
     */
    void writeString( @Nonnull String str ) throws IOException {
        byte[] bytes = str.getBytes( StandardCharsets.UTF_8 );
        writeVaruint32( bytes.length );
        write( bytes );
    }

    /**
     * Write a section with header and data.
     * 
     * @param type
     *            the name of the section
     * @param data
     *            the data of the section
     * @throws IOException
     *             if any I/O error occur
     */
    void writeSection( SectionType type, WasmOutputStream data ) throws IOException {
        ByteArrayOutputStream baos = (ByteArrayOutputStream)data.out;
        int size = baos.size();
        if( size == 0 ) {
            return;
        }
        writeVaruint32( type.ordinal() );
        writeVaruint32( size );
        baos.writeTo( this );
    }

    /**
     * Write the data of this stream to the output. Work only for in memory stream.
     * 
     * @param output
     *            the target
     * @throws IOException
     *             if any I/O error occur
     */
    void writeTo( OutputStream output ) throws IOException {
        ByteArrayOutputStream baos = (ByteArrayOutputStream)out;
        baos.writeTo( output );
    }

    /**
     * The count of bytes in the stream.
     * 
     * @return the data size
     */
    int size() {
        return count;
    }

    /**
     * Reset the stream. Work only for in memory stream.
     */
    void reset() {
        ByteArrayOutputStream baos = (ByteArrayOutputStream)out;
        baos.reset();
        count = 0;
    }
}
