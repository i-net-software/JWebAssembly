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
public class LocalVariable {

    private final int start_pc;

    private final int length;

    private final String name;

    private final String signature;

    private final int index;

    /**
     * http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.7.13
     * http://docs.oracle.com/javase/specs/jvms/se5.0/html/ClassFile.doc.html#5956
     *
     * @param input
     *            the stream of the class
     * @param position
     *            the position in the LocalVariableTable
     * @param constantPool
     *            Reference to the current ConstantPool
     * @throws IOException
     *             if any I/O error occurs.
     */
    LocalVariable( DataInputStream input, ConstantPool constantPool ) throws IOException {
        start_pc = input.readUnsignedShort();
        length = input.readUnsignedShort();
        name = (String)constantPool.get( input.readUnsignedShort() );
        signature = (String)constantPool.get( input.readUnsignedShort() );
        index = input.readUnsignedShort();
    }

    /**
     * Get the index in the local variable table (memory location/slot).
     * 
     * @return the index.
     */
    public int getIndex() {
        return index;
    }

    /**
     * Get the name of the variable
     * 
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * Get the type/signature of the variable
     * 
     * @return the signature
     */
    public String getSignature() {
        return signature;
    }

    /**
     * Get the code position within the local variable has a value. The first set operation to the variable will start
     * before this position.
     * 
     * @return the position.
     */
    public int getStartPosition() {
        return start_pc;
    }

    /**
     * Get the code position length within the local variable has a value.
     * 
     * @return the length
     */
    public int getLengthPosition() {
        return length;
    }
}
