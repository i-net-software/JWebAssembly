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
public class LocalVariableTable {

    private final ConstantPool constantPool;

    private final int          maxLocals;

    private LocalVariable[]    table;

    /**
     * Create a new instance of the code attribute "LocalVariableTable".
     * 
     * @param maxLocals
     *            the count of local variables in the memory
     * @param constantPool
     *            Reference to the current ConstantPool
     */
    LocalVariableTable( int maxLocals, ConstantPool constantPool ) {
        this.maxLocals = maxLocals;
        this.constantPool = constantPool;
    }

    /**
     * http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.7.13
     * http://docs.oracle.com/javase/specs/jvms/se5.0/html/ClassFile.doc.html#5956
     *
     * @param input
     *            the stream of the class
     * @throws IOException
     *             if any I/O error occurs.
     */
    void read( DataInputStream input ) throws IOException {
        int count = input.readUnsignedShort();
        table = new LocalVariable[count];
        for( int i = 0; i < count; i++ ) {
            table[i] = new LocalVariable( input, constantPool );
        }
    }

    /**
     * Get the count of variables/slots. This is not the count of declared LocalVariable in this table. There can be
     * unnamed helper variables for the compiler which are not in the table. There can be reused slots for different
     * variables.
     * 
     * @return the count
     */
    public int getMaxLocals() {
        return maxLocals;
    }

    /**
     * Get the declared local variables
     * 
     * @return the variables
     */
    public LocalVariable[] getTable() {
        return table;
    }
}
