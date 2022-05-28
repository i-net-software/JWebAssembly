/*
 * Copyright 2017 - 2022 Volker Berlin (i-net software)
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
package de.inetsoftware.classparser;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Volker Berlin
 */
public class Annotations {

    /**
     * Read the annotations structure. http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.7.17
     * 
     * @param input
     *            the stream of the RuntimeInvisibleAnnotations attribute
     * @param constantPool
     *            the ConstantPool of the class
     * @throws IOException
     *             if an I/O error occurs
     * @return the map of the annotation names to its attributes
     */
    static Map<String, Map<String, Object>> read( DataInputStream input, ConstantPool constantPool ) throws IOException {
        Map<String, Map<String, Object>> annotations = new HashMap<>();
        int count = input.readUnsignedShort();
        for( int i = 0; i < count; i++ ) {
            readAnnotation( input, constantPool, annotations );
        }
        return annotations;
    }

    /**
     * Read a single annotation and add it to the container
     * 
     * @param input
     *            the stream of the RuntimeInvisibleAnnotations attribute
     * @param constantPool
     *            the ConstantPool of the class
     * @param annotations
     *            container for the annotation
     * @throws IOException
     *             if an I/O error occurs
     */
    private static void readAnnotation( DataInputStream input, ConstantPool constantPool, Map<String, Map<String, Object>> annotations ) throws IOException {
        String className = (String)constantPool.get( input.readUnsignedShort() );
        className = className.substring( 1, className.length() - 1 ).replace( '/', '.' ); // has the form: "Lcom/package/ClassName;"
        Map<String, Object> valuePairs = new HashMap<>();
        annotations.put( className, valuePairs );

        int valuePairCount = input.readUnsignedShort();
        for( int p = 0; p < valuePairCount; p++ ) {
            String key = (String)constantPool.get( input.readUnsignedShort() );
            Object value = readElementValue( input, constantPool );
            valuePairs.put( key, value );
        }
    }

    /**
     * Read a single element value
     * 
     * @param input
     *            the stream of the RuntimeInvisibleAnnotations attribute
     * @param constantPool
     *            the ConstantPool of the class
     * @return the value
     * @throws IOException
     *             if an I/O error occurs
     */
    private static Object readElementValue( DataInputStream input, ConstantPool constantPool ) throws IOException {
        int type = input.readUnsignedByte();
        switch( type ) {
            case 'B':
            case 'C':
            case 'D':
            case 'F':
            case 'I':
            case 'J':
            case 'S':
            case 'Z':
            case 's': // String
            case 'c': // Class
                return constantPool.get( input.readUnsignedShort() );
            case 'e': // enum constant
                constantPool.get( input.readUnsignedShort() );        // enum type name
                return constantPool.get( input.readUnsignedShort() ); // enum constant name
            case '@': // annotation type
                Map<String, Map<String, Object>> annotations = new HashMap<>();
                readAnnotation( input, constantPool, annotations );
                return annotations;
            case '[':
                int count = input.readUnsignedShort();
                Object[] values = new Object[count];
                for( int i = 0; i < count; i++ ) {
                    values[i] = readElementValue( input, constantPool );
                }
                return values;
            default:
                throw new IOException( "Unknown annotation value type pool type: " + type );
        }
    }
}
