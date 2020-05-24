/*
   Copyright 2011 - 2020 Volker Berlin (i-net software)

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
package de.inetsoftware.classparser;

import java.io.DataInputStream;
import java.io.IOException;

/**
 * @author Volker Berlin
 */
public class ConstantPool {

    private final Object[] constantPool;
    public static final int CONSTANT_Utf8 = 1;
    public static final int CONSTANT_Integer = 3;
    public static final int CONSTANT_Float = 4;
    public static final int CONSTANT_Long = 5;
    public static final int CONSTANT_Double = 6;
    public static final int CONSTANT_Class = 7;
    public static final int CONSTANT_String = 8;
    public static final int CONSTANT_Fieldref = 9;
    public static final int CONSTANT_Methodref = 10;
    public static final int CONSTANT_InterfaceMethodref = 11;
    public static final int CONSTANT_NameAndType = 12;
    public static final int CONSTANT_MethodHandle = 15;
    public static final int CONSTANT_MethodType = 16;
    public static final int CONSTANT_InvokeDynamic = 18;
    public static final int CONSTANT_Module = 19;
    public static final int CONSTANT_Package = 20;
    /**
     * https://docs.oracle.com/javase/specs/jvms/se9/html/jvms-4.html#jvms-4.4
     *
     * @param input
     *            the stream of the class
     * @throws IOException
     *             if any IO error occur
     */
    ConstantPool( DataInputStream input ) throws IOException {
        int count = input.readUnsignedShort();
        Object[] pool = constantPool = new Object[count];
        for( int i = 1; i < count; i++ ) {
            byte type = input.readByte();
            switch( type ) {
                case CONSTANT_Utf8:
                    pool[i] = input.readUTF();
                    break;
                case CONSTANT_Integer:
                    pool[i] = Integer.valueOf( input.readInt() );
                    break;
                case CONSTANT_Float:
                    pool[i] = Float.valueOf( input.readFloat() );
                    break;
                case CONSTANT_Long:
                    pool[i] = Long.valueOf( input.readLong() );
                    i++;
                    break;
                case CONSTANT_Double:
                    pool[i] = Double.valueOf( input.readDouble() );
                    i++;
                    break;
                case CONSTANT_Class:
                case CONSTANT_String:
                case CONSTANT_MethodType:
                case CONSTANT_Module:
                case CONSTANT_Package:
                    pool[i] = new int[] { type, input.readUnsignedShort() };
                    break;
                case CONSTANT_Fieldref:
                case CONSTANT_Methodref:
                case CONSTANT_InterfaceMethodref:
                case CONSTANT_NameAndType:
                case CONSTANT_InvokeDynamic:
                    pool[i] = new int[] { type, input.readUnsignedShort(), input.readUnsignedShort() };
                    break;
                case CONSTANT_MethodHandle:
                    pool[i] = new int[] { type, input.readUnsignedByte(), input.readUnsignedShort() };
                    break;
                default:
                    throw new IOException( "Unknown constant pool type: " + type );
            }
        }

        boolean repeat;
        do {
            repeat = false;
            for( int i = 0; i < count; i++ ) {
                if( pool[i] instanceof int[] ) {
                    int[] data = (int[])pool[i];
                    switch( data[0] ) {
                        case CONSTANT_Class:
                            pool[i] = new ConstantClass( (String)pool[data[1]] );
                            break;
                        case CONSTANT_String:
                        case CONSTANT_MethodType:
                        case CONSTANT_Module:
                        case CONSTANT_Package:
                            pool[i] = pool[data[1]];
                            break;
                        case CONSTANT_Fieldref:
                            if( pool[data[1]] instanceof int[] || pool[data[2]] instanceof int[] ) {
                                repeat = true;
                            } else {
                                pool[i] = new ConstantFieldRef( (ConstantClass)pool[data[1]], (ConstantNameAndType)pool[data[2]] );
                            }
                            break;
                        case CONSTANT_Methodref:
                            if( pool[data[1]] instanceof int[] || pool[data[2]] instanceof int[] ) {
                                repeat = true;
                            } else {
                                pool[i] = new ConstantMethodRef( (ConstantClass)pool[data[1]], (ConstantNameAndType)pool[data[2]] );
                            }
                            break;
                        case CONSTANT_InterfaceMethodref:
                            if( pool[data[1]] instanceof int[] || pool[data[2]] instanceof int[] ) {
                                repeat = true;
                            } else {
                                pool[i] = new ConstantInterfaceRef( (ConstantClass)pool[data[1]], (ConstantNameAndType)pool[data[2]] );
                            }
                            break;
                        case CONSTANT_NameAndType:
                            pool[i] = new ConstantNameAndType( (String)pool[data[1]], (String)pool[data[2]] );
                            break;
                        case CONSTANT_MethodHandle:
                            pool[i] = pool[data[2]];
                            break;
                        case CONSTANT_InvokeDynamic:
                            if( pool[data[2]] instanceof int[] ) {
                                repeat = true;
                            } else {
                                pool[i] = new ConstantInvokeDynamic( data[1], (ConstantNameAndType)pool[data[2]] );
                            }
                            break;
                        default:
                            throw new IOException( "Unknown constant pool type: " + data[0] );
                    }
                }
            }
        } while( repeat );
    }

    /**
     * Get a object from the pool at the given index.
     * 
     * @param index
     *            the index
     * @return the object
     */
    public Object get( int index ) {
        return constantPool[index];
    }

    /**
     * Set a value in the constant pool.
     * 
     * @param index
     *            the index
     * @param value
     *            the new value
     */
    void set( int index, Object value ) {
        constantPool[index] = value;
    }

    /**
     * Get the count of entries in the pool.
     * 
     * @return the count
     */
    int size() {
        return constantPool.length;
    }
}
