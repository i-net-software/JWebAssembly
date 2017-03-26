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

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author Volker Berlin
 */
public class LocalVariableTable {

    private LocalVariable[] tablePosition;

    private LocalVariable[] table;

    private int count;

    /**
     * @param maxLocals the count of local variables in the memory
     */
    LocalVariableTable( int maxLocals ) {
        table = new LocalVariable[maxLocals];
        tablePosition = new LocalVariable[maxLocals];
    }

    /**
     * http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.7.13
     * http://docs.oracle.com/javase/specs/jvms/se5.0/html/ClassFile.doc.html#5956
     *
     * @param input
     *            the stream of the class
     * @param withPositions a hack if we find a better solution to map the positions LocalVariableTypeTable
     * @throws IOException
     */
    void read( DataInputStream input, boolean withPositions ) throws IOException {
        count = input.readUnsignedShort();
        if( count > table.length ) {
            table = new LocalVariable[count];
            tablePosition = new LocalVariable[count];
        }
        for( int i = 0; i < count; i++ ) {
            LocalVariable var = new LocalVariable( input );
            int idx = var.getIndex();
            if( withPositions ) {
                tablePosition[i] = var;
            }
            table[idx] = var;
        }
    }

    /**
     * Get the count of variables.
     * @return the count
     */
    public int getPositionSize() {
        return count;
    }

    /**
     * Get the count of storage places a 4 bytes for local variables. Double and long variables need 2 of this places.
     * 
     * @return the local stack size
     */
    public int getSize() {
        return table.length;
    }

    /**
     * Get the LocalVariable with it position. The position is continue also with double and long variables. Or if a variable is reused from a other block.
     *
     * @param pos
     *            the position
     */
    @Nonnull
    public LocalVariable getPosition( int pos ) {
        return tablePosition[pos];
    }

    /**
     * Get the LocalVariable with its memory location. The index has empty places with double and long variables.
     *
     * @param idx
     *            the index in the memory
     */
    @Nonnull
    public LocalVariable get( int idx ) {
        return table[idx];
    }

    /**
     * Find a LocalVariable with a given name.
     *
     * @param name
     *            needed for evaluate the name.
     * @param constantPool
     *            current constant pool
     * @return the LocalVariable or null
     */
    @Nullable
    public LocalVariable get( String name, ConstantPool constantPool ) {
        for( int i=0; i<table.length; i++ ){
            if( name.equals( table[i].getName( constantPool ) )) {
                return table[i];
            }
        }
        return null;
    }
}
