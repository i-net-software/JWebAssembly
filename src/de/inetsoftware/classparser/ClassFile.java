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
import java.io.InputStream;

import de.inetsoftware.classparser.Attributes.AttributeInfo;

/**
 * http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html
 * http://docs.oracle.com/javase/specs/jvms/se5.0/html/ClassFile.doc.html
 *
 * @author Volker Berlin
 */
public class ClassFile {

    private final DataInputStream input;

    private final int             minorVersion;

    private final int             majorVersion;

    private final ConstantPool    constantPool;

    private final int             accessFlags;

    private final ConstantClass   thisClass;

    private final ConstantClass   superClass;

    private final ConstantClass[] interfaces;

    private final FieldInfo[]     fields;

    private final MethodInfo[]    methods;

    private final Attributes      attributes;

    private String                thisSignature;

    private String                superSignature;

    /**
     * Load a class file and create a model of the class.
     *
     * @param stream
     *            The InputStream of the class file. Will be closed if finish.
     * @throws IOException
     *             if this input stream reaches the end before reading the class file.
     */
    public ClassFile( InputStream stream ) throws IOException {
        this.input = new DataInputStream( stream );
        int magic = input.readInt();
        if( magic != 0xCAFEBABE ) {
            throw new IOException( "Invalid class magic: " + Integer.toHexString( magic ) );
        }
        minorVersion = input.readUnsignedShort();
        majorVersion = input.readUnsignedShort();

        constantPool = new ConstantPool( input );
        accessFlags = input.readUnsignedShort();
        thisClass = (ConstantClass)constantPool.get( input.readUnsignedShort() );
        superClass = (ConstantClass)constantPool.get( input.readUnsignedShort() );
        interfaces = new ConstantClass[input.readUnsignedShort()];
        for( int i = 0; i < interfaces.length; i++ ) {
            interfaces[i] = (ConstantClass)constantPool.get( input.readUnsignedShort() );
        }
        fields = readFields();
        methods = readMethods();
        attributes = new Attributes( input, constantPool );

        stream.close();

        AttributeInfo info = attributes.get( "Signature" );
        if( info != null ) {
            int idx = info.getDataInputStream().readShort();
            String signature = (String)constantPool.get( idx );
            int count = 0;
            for( int i = 0; i < signature.length(); i++ ) {
                char ch = signature.charAt( i );
                switch( ch ) {
                    case '<':
                        count++;
                        continue;
                    case '>':
                        count--;
                        continue;
                }
                if( count == 0 ) {
                    thisSignature = signature.substring( 0, i );
                    superSignature = signature.substring( i );
                    break;
                }
            }
        }
    }

    /**
     * Get value of SourceFile if available.
     *
     * @return the source file name or null.
     * @throws IOException
     *             if an I/O error occurs.
     */
    public String getSourceFile() throws IOException {
        return attributes.getSourceFile();
    }

    public ConstantPool getConstantPool() {
        return constantPool;
    }

    public ConstantClass getThisClass() {
        return thisClass;
    }

    public ConstantClass getSuperClass() {
        return superClass;
    }

    public ConstantClass[] getInterfaces(){
        return interfaces;
    }

    public MethodInfo[] getMethods() {
        return methods;
    }

    public int getMethodCount( String name ) {
        int count = 0;
        for( MethodInfo method : methods ) {
            if( name.equals( method.getName() ) ) {
                count++;
            }
        }
        return count;
    }

    public FieldInfo getField( String name ) {
        for( FieldInfo field : fields ) {
            if( name.equals( field.getName() ) ) {
                return field;
            }
        }
        return null;
    }

    /**
     * The access flags of the class.
     * http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.1-200-E
     * http://docs.oracle.com/javase/specs/jvms/se5.0/html/ClassFile.doc.html#23242
     * @see #isInterface()
     */
    public int getAccessFlags() {
        return accessFlags;
    }

    private FieldInfo[] readFields() throws IOException {
        FieldInfo[] fields = new FieldInfo[input.readUnsignedShort()];
        for( int i = 0; i < fields.length; i++ ) {
            fields[i] = new FieldInfo( input, constantPool );
        }
        return fields;
    }

    private MethodInfo[] readMethods() throws IOException {
        MethodInfo[] methods = new MethodInfo[input.readUnsignedShort()];
        for( int i = 0; i < methods.length; i++ ) {
            methods[i] = new MethodInfo( input, constantPool, this );
        }
        return methods;
    }

    /**
     * Get the signature of the class with generic types.
     */
    public String getThisSignature() {
        return thisSignature;
    }

    /**
     * Get the signature of the super class with generic types.
     */
    public String getSuperSignature() {
        return superSignature;
    }

    /**
     * Get the type of class.
     */
    public Type getType() {
        if( (accessFlags & 0x0200) > 0 ) {
            return Type.Interface;
        }
        if( superClass.getName().equals( "java/lang/Enum" ) ) {
            return Type.Enum;
        }
        return Type.Class;
    }

    public static enum Type {
        Class, Interface, Enum;
    }
}
