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
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import javax.annotation.Nullable;

import de.inetsoftware.classparser.Attributes.AttributeInfo;

/**
 * http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html
 * http://docs.oracle.com/javase/specs/jvms/se5.0/html/ClassFile.doc.html
 *
 * @author Volker Berlin
 */
public class ClassFile {

    private final int             minorVersion;

    private final int             majorVersion;

    private final ConstantPool    constantPool;

    private final int             accessFlags;

    private final ConstantClass   thisClass;

    private final ConstantClass   superClass;

    private final ConstantClass[] interfaces;

    private FieldInfo[]           fields;

    private MethodInfo[]          methods;

    private final Attributes      attributes;

    private String                thisSignature;

    private String                superSignature;

    private Map<String,Map<String,Object>> annotations;

    private BootstrapMethod[]     bootstrapMethods;

    /**
     * Load a class file and create a model of the class.
     *
     * @param stream
     *            The InputStream of the class file. Will be closed if finish.
     * @throws IOException
     *             if this input stream reaches the end before reading the class file.
     */
    public ClassFile( InputStream stream ) throws IOException {
        DataInputStream input = new DataInputStream( stream );
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
        fields = readFields( input );
        methods = readMethods( input );
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
     * Create a replaced instance.
     * 
     * @param className
     *            the class name that should be replaced
     * @param classFile
     *            the replacing class file data
     */
    public ClassFile( String className, ClassFile classFile ) {
        minorVersion = classFile.minorVersion;
        majorVersion = classFile.majorVersion;

        constantPool = classFile.constantPool;
        accessFlags = classFile.accessFlags;
        thisClass = new ConstantClass( className );
        superClass = classFile.superClass;
        interfaces = classFile.interfaces;
        fields = classFile.fields;
        methods = classFile.methods;
        attributes = classFile.attributes;

        patchConstantPool( classFile.thisClass.getName(), thisClass );
    }

    /**
     * Replace the reference to the Class in the the constant pool.
     * 
     * @param origClassName
     *            the class name that should be replaced.
     * @param thisClass
     *            the reference of the class that should be used.
     */
    private void patchConstantPool( String origClassName, ConstantClass thisClass ) {
        // patch constant pool
        for( int i = 0; i < constantPool.size(); i++ ) {
            Object obj = constantPool.get( i );
            if( obj instanceof ConstantClass ) {
                if( ((ConstantClass)obj).getName().equals( origClassName ) ) {
                    constantPool.set( i, thisClass );
                }
            } else if( obj instanceof ConstantRef ) {
                ConstantRef ref = (ConstantRef)obj;
                if( ref.getClassName().equals( origClassName ) ) {
                    ConstantNameAndType nameAndType = new ConstantNameAndType( ref.getName(), ref.getType() );
                    constantPool.set( i, new ConstantFieldRef( thisClass, nameAndType ) );
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

    /**
     * Get a single annotation or null
     * 
     * @param annotation
     *            the class name of the annotation
     * @return the value or null if not exists
     * @throws IOException
     *             if any I/O error occur
     */
    @Nullable
    public Map<String, Object> getAnnotation( String annotation ) throws IOException {
        if( annotations == null ) {
            AttributeInfo data = attributes.get( "RuntimeInvisibleAnnotations" );
            if( data != null ) {
                annotations =  Annotations.read( data.getDataInputStream(), constantPool );
            } else {
                annotations = Collections.emptyMap();
            }
        }
        return annotations.get( annotation );
    }

    /**
     * Get the x-the BootstrapMethod. Bootstrap methods are used for creating an lambda object.
     * 
     * @param methodIdx
     *            the index of the method
     * @return the method
     * @throws IOException
     *             if any error occur
     */
    public BootstrapMethod getBootstrapMethod( int methodIdx ) throws IOException {
        if( bootstrapMethods == null ) {
            AttributeInfo data = attributes.get( "BootstrapMethods" );
            if( data != null ) {
                DataInputStream input = data.getDataInputStream();
                int count = input.readUnsignedShort();
                bootstrapMethods = new BootstrapMethod[count];
                for( int i = 0; i < count; i++ ) {
                    bootstrapMethods[i] = new BootstrapMethod( input, constantPool );
                }
            }
        }
        return bootstrapMethods[methodIdx];
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

    /**
     * Find a method via name and signature.
     * 
     * @param name
     *            the name
     * @param signature
     *            the signature
     * @return the method or null if not found
     */
    public MethodInfo getMethod( String name, String signature ) {
        for( MethodInfo method : methods ) {
            if( name.equals( method.getName() ) && signature.equals( method.getType() ) ) {
                return method;
            }
        }
        return null;
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
     * Get the fields of the class.
     * 
     * @return the fields
     */
    public FieldInfo[] getFields() {
        return fields;
    }

    /**
     * The access flags of the class.
     * http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.1-200-E
     * http://docs.oracle.com/javase/specs/jvms/se5.0/html/ClassFile.doc.html#23242
     * @see java.lang.Class#isInterface()
     */
    public int getAccessFlags() {
        return accessFlags;
    }

    private FieldInfo[] readFields( DataInputStream input ) throws IOException {
        FieldInfo[] fields = new FieldInfo[input.readUnsignedShort()];
        for( int i = 0; i < fields.length; i++ ) {
            fields[i] = new FieldInfo( input, constantPool );
        }
        return fields;
    }

    private MethodInfo[] readMethods( DataInputStream input ) throws IOException {
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
     * 
     * @return the type of the class
     */
    public Type getType() {
        if( (accessFlags & 0x0200) > 0 ) {
            return Type.Interface;
        }
        if( superClass != null && superClass.getName().equals( "java/lang/Enum" ) ) {
            return Type.Enum;
        }
        return Type.Class;
    }

    public static enum Type {
        Class, Interface, Enum;
    }

    /**
     * Extends this class with the methods and fields of the partial class.
     * 
     * @param partialClassFile
     *            extension of the class
     */
    public void partial( ClassFile partialClassFile ) {
        ArrayList<MethodInfo> allMethods = new ArrayList<>( Arrays.asList( methods ) );
        for( MethodInfo m : partialClassFile.methods ) {
            if( getMethod( m.getName(), m.getType() ) == null ) {
                m.setDeclaringClassFile( this );
                allMethods.add( m );
            }
        }
        methods = allMethods.toArray( methods );

        ArrayList<FieldInfo> allFields = new ArrayList<>( Arrays.asList( fields ) );
        for( FieldInfo field : partialClassFile.fields ) {
            if( getField( field.getName() ) == null ) {
                allFields.add( field );
            }
        }
        fields = allFields.toArray( fields );

        partialClassFile.patchConstantPool( partialClassFile.thisClass.getName(), thisClass );
    }
}
