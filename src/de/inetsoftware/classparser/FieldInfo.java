/*
   Copyright 2011 - 2017 Volker Berlin (i-net software)

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
 * Described a Field of a class.
 * http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.5
 * http://docs.oracle.com/javase/specs/jvms/se5.0/html/ClassFile.doc.html#2877
 * 
 * @author Volker Berlin
 */
public class FieldInfo {
    private final int        accessFlags;

    private final String     name;

    private final String     description;

    private final Attributes attributes;

    /**
     * Read a single FieldInfo.
     * http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.5
     * http://docs.oracle.com/javase/specs/jvms/se5.0/html/ClassFile.doc.html#2877
     * @param input
     * @param constantPool
     * @throws IOException
     */
    FieldInfo(DataInputStream input, ConstantPool constantPool) throws IOException {
        this.accessFlags = input.readUnsignedShort();
        this.name = (String)constantPool.get( input.readUnsignedShort() );
        this.description = (String)constantPool.get( input.readUnsignedShort() );
        this.attributes = new Attributes( input, constantPool );
    }

    /**
     * Get the access flags of the method.
     * http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.5-200-A
     * http://docs.oracle.com/javase/specs/jvms/se5.0/html/ClassFile.doc.html#87652
     *
     * @return the flags
     */
    public int getAccessFlags() {
        return accessFlags;
    }

    /**
     * If this field is static or not
     * @return true, if static
     */
    public boolean isStatic() {
        return (accessFlags & 0x0008) > 0;
    }

    /**
     * Get the name of the field
     * @return the name
     */
    public Object getName() {
        return name;
    }
}