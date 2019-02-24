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
    static Map<String,Map<String,Object>> read( DataInputStream input, ConstantPool constantPool ) throws IOException {
        Map<String,Map<String,Object>> annotations = new HashMap<>();
        int count = input.readUnsignedShort();
        for( int i = 0; i < count; i++ ) {
            String className = (String)constantPool.get( input.readUnsignedShort() );
            className = className.substring( 1, className.length() - 1 ).replace( '/', '.' ); // has the form: "Lcom/package/ClassName;"
            Map<String,Object> valuePairs = new HashMap<>();
            annotations.put( className, valuePairs );

            int valuePairCount = input.readUnsignedShort();
            for( int p = 0; p < valuePairCount; p++ ) {
                String key = (String)constantPool.get( input.readUnsignedShort() );
                int type = input.readUnsignedByte();
                Object value;
                switch( type ) {
                    case 'B':
                    case 'C':
                    case 'D':
                    case 'F':
                    case 'I':
                    case 'J':
                    case 'S':
                    case 'Z':
                    case 's':
                        value = constantPool.get( input.readUnsignedShort() );
                        break;
                    default:
                        // TODO other possible values for type: e c @ [
                        throw new IOException( "Unknown annotation value type pool type: " + type );
                }
                valuePairs.put( key, value );
            }
        }
        return annotations;
    }
}
