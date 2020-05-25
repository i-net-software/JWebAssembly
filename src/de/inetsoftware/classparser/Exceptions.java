/*
   Copyright 2017 Volker Berlin (i-net software)

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
 * Exceptions attribute of methods.
 *
 * @author Volker Berlin
 */
public class Exceptions {

    ConstantClass[] classes;

    /**
     * Read the Exceptions structure.
     * http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.7.5
     * http://docs.oracle.com/javase/specs/jvms/se5.0/html/ClassFile.doc.html#3129
     *
     * @param input
     * @param constantPool
     * @throws IOException
     */
    Exceptions( DataInputStream input, ConstantPool constantPool ) throws IOException {
        int count = input.readUnsignedShort();
        classes = new ConstantClass[count];
        for( int i = 0; i < count; i++ ) {
            int idx = input.readUnsignedShort();
            classes[i] = (ConstantClass)constantPool.get( idx );
        }
    }

    public ConstantClass[] getClasses() {
        return classes;
    }
}
