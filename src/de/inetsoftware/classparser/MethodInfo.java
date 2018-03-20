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

import de.inetsoftware.classparser.Attributes.AttributeInfo;

/**
 * @author Volker Berlin
 */
public class MethodInfo {

    private final int          accessFlags;

    private final String       name;

    private final String       description;

    private final Attributes   attributes;

    private final ConstantPool constantPool;

    private Code               code;

    private Exceptions         exceptions;

    private ClassFile          classFile;

    /**
     * Read the method_info structure
     * http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.6
     * http://docs.oracle.com/javase/specs/jvms/se5.0/html/ClassFile.doc.html#1513
     *
     * @param input
     * @param constantPool
     * @param classFile the declaring class file
     * @throws IOException
     */
    MethodInfo( DataInputStream input, ConstantPool constantPool, ClassFile classFile ) throws IOException {
        this.accessFlags = input.readUnsignedShort();
        this.name = (String)constantPool.get( input.readUnsignedShort() );
        this.description = (String)constantPool.get( input.readUnsignedShort() );
        this.constantPool = constantPool;
        this.attributes = new Attributes( input, constantPool );
        this.classFile = classFile;
    }

    public ClassFile getDeclaringClassFile() {
        return classFile;
    }

    /**
     * Get the access flags of the method.
     * http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.6-200-A
     * http://docs.oracle.com/javase/specs/jvms/se5.0/html/ClassFile.doc.html#1522
     *
     * @return the flags
     */
    public int getAccessFlags() {
        return accessFlags;
    }

    /**
     * If the method is a static method.
     * http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.6-200-A
     * http://docs.oracle.com/javase/specs/jvms/se5.0/html/ClassFile.doc.html#1522
     * @return true, if static
     * @see #getAccessFlags()
     */
    public boolean isStatic() {
        return (accessFlags & 0x0008) > 0;
    }

    /**
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * @return the attributes
     */
    public Attributes getAttributes() {
        return attributes;
    }

    public Code getCode() throws IOException {
        if( code != null ){
            return code;
        }
        AttributeInfo data = attributes.get( "Code" );
        if( data != null ) {
            code = new Code( data.getDataInputStream(), constantPool );
        }
        return code;
    }


    /**
     * Get the signature of the method without generic types.
     */
    public String getDescription() {
        return description;
    }

    /**
     * Get the signature of the method with generic types.
     */
    public String getSignature() throws IOException {
        AttributeInfo info = getAttributes().get( "Signature" );
        if( info != null ) {
            int idx = info.getDataInputStream().readShort();
            return (String)constantPool.get( idx );
        } else {
            return description;
        }
    }

    public Exceptions getExceptions() throws IOException {
        if( exceptions != null ) {
            return exceptions;
        }
        AttributeInfo data = attributes.get( "Exceptions" );
        if( data != null ) {
            exceptions = new Exceptions( data.getDataInputStream(), constantPool );
        }
        return exceptions;
    }

    /**
     * Get the annotations with @Retention(RetentionPolicy.CLASS)
     * @return the annotations if there any exists else null
     */
    public Annotations getRuntimeInvisibleAnnotations() throws IOException {
        AttributeInfo data = attributes.get( "RuntimeInvisibleAnnotations" );
        if( data != null ) {
            return new Annotations( data.getDataInputStream(), constantPool );
        }
        return null;
    }

    /**
     * Get the constant pool of the the current class.
     * @return the constant pool
     */
    public ConstantPool getConstantPool() {
        return constantPool;
    }
}
