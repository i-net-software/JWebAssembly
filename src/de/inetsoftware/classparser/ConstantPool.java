/*
   Copyright 2011 - 2019 Volker Berlin (i-net software)

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
                case 1: //CONSTANT_Utf8
                    pool[i] = input.readUTF();
                    break;
                case 3: //CONSTANT_Integer
                    pool[i] = new Integer( input.readInt() );
                    break;
                case 4: //CONSTANT_Float
                    pool[i] = new Float( input.readFloat() );
                    break;
                case 5: //CONSTANT_Long
                    pool[i] = new Long( input.readLong() );
                    i++;
                    break;
                case 6: //CONSTANT_Double
                    pool[i] = new Double( input.readDouble() );
                    i++;
                    break;
                case 7: //CONSTANT_Class
                case 8: //CONSTANT_String
                case 16: // CONSTANT_MethodType
                case 19: // CONSTANT_Module
                case 20: // CONSTANT_Package
                    pool[i] = new int[] { type, input.readUnsignedShort() };
                    break;
                case 9: //CONSTANT_Fieldref
                case 10: //CONSTANT_Methodref
                case 11: //CONSTANT_InterfaceMethodref
                case 12: //CONSTANT_NameAndType
                case 18: // CONSTANT_InvokeDynamic
                    pool[i] = new int[] { type, input.readUnsignedShort(), input.readUnsignedShort() };
                    break;
                case 15: // CONSTANT_MethodHandle
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
                        case 7: //CONSTANT_Class
                            pool[i] = new ConstantClass( (String)pool[data[1]] );
                            break;
                        case 8: // CONSTANT_String
                        case 16: // CONSTANT_MethodType
                        case 19: // CONSTANT_Module
                        case 20: // CONSTANT_Package
                            pool[i] = pool[data[1]];
                            break;
                        case 9: //CONSTANT_Fieldref
                            if( pool[data[1]] instanceof int[] || pool[data[2]] instanceof int[] ) {
                                repeat = true;
                            } else {
                                pool[i] = new ConstantFieldRef( (ConstantClass)pool[data[1]], (ConstantNameAndType)pool[data[2]] );
                            }
                            break;
                        case 10: //CONSTANT_Methodref
                            if( pool[data[1]] instanceof int[] || pool[data[2]] instanceof int[] ) {
                                repeat = true;
                            } else {
                                pool[i] = new ConstantMethodRef( (ConstantClass)pool[data[1]], (ConstantNameAndType)pool[data[2]] );
                            }
                            break;
                        case 11: //CONSTANT_InterfaceMethodref
                            if( pool[data[1]] instanceof int[] || pool[data[2]] instanceof int[] ) {
                                repeat = true;
                            } else {
                                pool[i] = new ConstantInterfaceRef( (ConstantClass)pool[data[1]], (ConstantNameAndType)pool[data[2]] );
                            }
                            break;
                        case 12: //CONSTANT_NameAndType
                            pool[i] = new ConstantNameAndType( (String)pool[data[1]], (String)pool[data[2]] );
                            break;
                        case 15: // CONSTANT_MethodHandle
                            pool[i] = pool[data[2]];
                            break;
                        case 18: // CONSTANT_InvokeDynamic
                            pool[i] = new ConstantInvokeDynamic( data[1], (ConstantNameAndType)pool[data[2]] );
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
}
