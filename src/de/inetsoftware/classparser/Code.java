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

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import de.inetsoftware.classparser.Attributes.AttributeInfo;

/**
 * @author Volker Berlin
 */
public class Code {

    private final ConstantPool      constantPool;

    private final int               maxStack;

    private final int               maxLocals;

    private final byte[]            codeData;

    private final TryCatchFinally[] exceptionTable;

    private final Attributes        attributes;

    private LineNumberTable         lineNumberTable;

    private LocalVariableTable      localVariableTable;

    /**
     * The code of a method attribute. http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.7.3
     * http://docs.oracle.com/javase/specs/jvms/se5.0/html/ClassFile.doc.html#1546
     *
     * @param input
     *            the stream of the code attribute
     * @param constantPool
     *            the ConstantPool of the class
     * @throws IOException
     *             if an I/O error occurs
     */
    Code( DataInputStream input, @Nonnull ConstantPool constantPool ) throws IOException {
        this.constantPool = constantPool;
        maxStack = input.readUnsignedShort(); //max_stack
        maxLocals = input.readUnsignedShort(); //max_locals;
        codeData = new byte[input.readInt()];
        input.readFully( codeData );

        exceptionTable = new TryCatchFinally[input.readUnsignedShort()];
        for( int i = 0; i < exceptionTable.length; i++ ) {
            exceptionTable[i] = new TryCatchFinally( input, constantPool );
        }
        attributes = new Attributes( input, constantPool );
    }

    /**
     * Get the constant pool of this code.
     * 
     * @return the ConstantPool of the class
     */
    @Nonnull
    public ConstantPool getConstantPool() {
        return constantPool;
    }

    /**
     * Get exception table of this code block.
     * 
     * @return the table, can be empty
     */
    @Nonnull
    public TryCatchFinally[] getExceptionTable() {
        return exceptionTable;
    }

    /**
     * Get the line number table. is null if the code was optimized.
     * 
     * @return the table or null
     * @throws IOException
     *             if any I/O error occur
     */
    @Nullable
    public LineNumberTable getLineNumberTable() throws IOException {
        if( lineNumberTable != null ) {
            return lineNumberTable;
        }
        AttributeInfo data = attributes.get( "LineNumberTable" );
        if( data != null ) {
            lineNumberTable = new LineNumberTable( data.getDataInputStream() );
        }
        return lineNumberTable;
    }

    /**
     * Get the local variable table of this method.
     * 
     * @return the variables
     * @throws IOException
     *             if any I/O error occur
     */
    @Nullable
    public LocalVariableTable getLocalVariableTable() throws IOException {
        if( localVariableTable != null ) {
            return localVariableTable;
        }
        AttributeInfo data = attributes.get( "LocalVariableTable" );
        if( data != null ) {
            localVariableTable = new LocalVariableTable( maxLocals, constantPool, data.getDataInputStream() );
            // we does not need any generics information
            // data = attributes.get( "LocalVariableTypeTable" );
        } else {
            // return only the maxLocals
            localVariableTable = new LocalVariableTable( maxLocals );
        }
        return localVariableTable;
    }

    public int getFirstLineNr() throws IOException {
        LineNumberTable table = getLineNumberTable();
        if( table == null ) {
            return -1;
        } else {
            return table.getMinLineNr();
        }
    }

    /**
     * Get the stream of Java Byte code instruction of this method.
     * 
     * @return the stream
     */
    public CodeInputStream getByteCode() {
        return new CodeInputStream( codeData, 0, codeData.length, this );
    }

    /**
     * Get the last position of the code.
     * 
     * @return the size.
     */
    public int getCodeSize() {
        return codeData.length;
    }
}
