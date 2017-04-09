/*
 * Copyright 2017 Volker Berlin (i-net software)
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
package de.inetsoftware.jwebassembly.module;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import de.inetsoftware.classparser.Annotations;
import de.inetsoftware.classparser.ClassFile;
import de.inetsoftware.classparser.Code;
import de.inetsoftware.classparser.CodeInputStream;
import de.inetsoftware.classparser.ConstantPool;
import de.inetsoftware.classparser.LineNumberTable;
import de.inetsoftware.classparser.MethodInfo;
import de.inetsoftware.jwebassembly.WasmException;

/**
 * Module Writer for text format with S-expressions.
 * 
 * @author Volker Berlin
 */
public abstract class ModuleWriter implements Closeable {

    private int                  paramCount;

    private ArrayList<ValueType> locals = new ArrayList<>();

    private String               sourceFile;

    /**
     * Write the content of the class to the
     * 
     * @param classFile
     *            the class file
     * @throws IOException
     *             if any I/O error occur
     * @throws WasmException
     *             if some Java code can't converted
     */
    public void write( ClassFile classFile ) throws IOException, WasmException {
        sourceFile = classFile.getSourceFile();
        if( sourceFile == null ) {
            sourceFile = classFile.getThisClass().getName();
        }
        MethodInfo[] methods = classFile.getMethods();
        for( MethodInfo method : methods ) {
            Code code = method.getCode();
            if( method.getName().equals( "<init>" ) && method.getDescription().equals( "()V" )
                            && code.isSuperInitReturn( classFile.getSuperClass() ) ) {
                continue; //default constructor
            }
            writeMethod( method );
        }
    }

    /**
     * Write the content of a method.
     * 
     * @param method
     *            the method
     * @throws IOException
     *             if any I/O error occur
     * @throws WasmException
     *             if some Java code can't converted
     */
    private void writeMethod( MethodInfo method ) throws IOException, WasmException {
        Code code = method.getCode();
        if( code != null ) { // abstract methods and interface methods does not have code
            String methodName = method.getName(); // TODO naming conversion rule
            writeExport( methodName, method );
            writeMethodStart( methodName );
            writeMethodSignature( method );
            locals.clear();
            LineNumberTable lineNumberTable = code.getLineNumberTable();
            if( lineNumberTable != null ) {
                int lineNumber;
                for( int i = 0; i < lineNumberTable.size(); i++ ) {
                    lineNumber = lineNumberTable.getLineNumber( i );
                    int offset = lineNumberTable.getStartOffset( i );
                    int nextOffset =
                                    i + 1 == lineNumberTable.size() ? code.getCodeSize()
                                                    : lineNumberTable.getStartOffset( i + 1 );
                    CodeInputStream byteCode = code.getByteCode( offset, nextOffset - offset );
                    writeCodeChunk( byteCode, lineNumber, method.getConstantPool() );
                }
            } else {
                CodeInputStream byteCode = code.getByteCode();
                writeCodeChunk( byteCode, -1, method.getConstantPool() );
            }
            for( int i = Math.min( paramCount, locals.size() ); i > 0; i-- ) {
                locals.remove( 0 );
            }
            writeMethodFinish( locals );
        }
    }

    /**
     * Look for a Export annotation and if there write an export directive.
     * @param methodName
     * @param method
     * @throws IOException
     */
    private void writeExport( String methodName, MethodInfo method ) throws IOException {
        Annotations annotations = method.getRuntimeInvisibleAnnotations();
        if( annotations != null ) {
            Map<String,Object> export = annotations.get( "org.webassembly.annotation.Export" );
            if( export != null ) {
                String exportName = (String)export.get( "name" );
                if( exportName == null ) {
                    exportName = methodName;
                }
                writeExport( methodName, exportName );
            }
        }
    }

    /**
     * Write an export directive
     * 
     * @param methodName
     *            the method name
     * @param exportName
     *            the export name, if null then the same like the method name
     * @throws IOException
     *             if any I/O error occur
     */
    protected abstract void writeExport( String methodName, String exportName ) throws IOException;

    /**
     * Write the method header.
     * 
     * @param name
     *            the method name
     * @throws IOException
     *             if any I/O error occur
     */
    protected abstract void writeMethodStart( String name ) throws IOException;

    /**
     * Write the parameter and return signatures
     * 
     * @param method
     *            the method
     * @throws IOException
     *             if any I/O error occur
     * @throws WasmException
     *             if some Java code can't converted
     */
    private void writeMethodSignature( MethodInfo method ) throws IOException, WasmException {
        String signature = method.getDescription();
        String kind = "param";
        int paramCount = 0;
        for( int i = 1; i < signature.length(); i++ ) {
            paramCount++;
            String javaType;
            switch( signature.charAt( i ) ) {
                case '[': // array
                    javaType = "array";
                    break;
                case 'L':
                    javaType = "object";
                    break;
                case 'B': // byte
                    javaType = "byte";
                    break;
                case 'C': // char
                    javaType = "char";
                    break;
                case 'D': // double
                    writeMethodParam( kind, ValueType.f64 );
                    continue;
                case 'F': // float
                    writeMethodParam( kind, ValueType.f32 );
                    continue;
                case 'I': // int
                    writeMethodParam( kind, ValueType.i32 );
                    continue;
                case 'J': // long
                    writeMethodParam( kind, ValueType.i64 );
                    continue;
                case 'V': // void
                    continue;
                case ')':
                    this.paramCount = paramCount - 1;
                    kind = "return";
                    continue;
                default:
                    javaType = signature.substring( i, i + 1 );
            }
            int lineNumber = method.getCode().getFirstLineNr();
            throw new WasmException( "Not supported Java data type in method signature: " + javaType, sourceFile, lineNumber );
        }
    }

    /**
     * Write a method parameter.
     * 
     * @param kind
     *            "param", "return" or "local"
     * @param valueType
     *            the data type of the parameter
     * @throws IOException
     *             if any I/O error occur
     */
    protected abstract void writeMethodParam( String kind, ValueType valueType ) throws IOException;

    /**
     * Complete the method
     * 
     * @param locals
     *            a list with types of local variables
     * 
     * @throws IOException
     *             if any I/O error occur
     */
    protected abstract void writeMethodFinish( List<ValueType> locals ) throws IOException;

    /**
     * Write a chunk of byte code.
     * 
     * @param byteCode
     *            a stream of byte code
     * @param lineNumber
     *            the current line number
     * @param constantPool
     *            the constant pool of the the current class
     * @throws WasmException
     *             if some Java code can't converted
     */
    private void writeCodeChunk( CodeInputStream byteCode, int lineNumber, ConstantPool constantPool  ) throws WasmException {
        try {
            while( byteCode.available() > 0 ) {
                int op = byteCode.readUnsignedByte();
                switch( op ) {
                    case 4: // iconst_1
                        writeConstInt( 1 );
                        break;
                    case 16: //bipush
                        writeConstInt( byteCode.readByte() );
                        break;
                    case 18: //ldc
                        writeConst( constantPool.get( byteCode.readUnsignedByte() ) );
                        break;
                    case 20: //ldc2_w
                        writeConst( constantPool.get( byteCode.readUnsignedShort() ) );
                        break;
                    case 26: // iload_0
                    case 27: // iload_1
                    case 28: // iload_2
                    case 29: // iload_3
                        writeLoadStore( true, ValueType.i32, op - 26 );
                        break;
                    case 30: // lload_0
                    case 31: // lload_1
                    case 32: // lload_2
                    case 33: // lload_3
                        writeLoadStore( true, ValueType.i64, op - 30 );
                        break;
                    case 34: // fload_0
                    case 35: // fload_1
                    case 36: // fload_2
                    case 37: // fload_3
                        writeLoadStore( true, ValueType.f32, op - 34 );
                        break;
                    case 38: // dload_0
                    case 39: // dload_1
                    case 40: // dload_2
                    case 41: // dload_3
                        writeLoadStore( true, ValueType.f64, op - 38 );
                        break;
                    case 59: // istore_0
                    case 60: // istore_1
                    case 61: // istore_2
                    case 62: // istore_3
                        writeLoadStore( false, ValueType.i32, op - 59 );
                        break;
                    case 96: // iadd
                        writeAdd( ValueType.i32);
                        break;
                    case 97: // ladd
                        writeAdd( ValueType.i64 );
                        break;
                    case 98: // fadd
                        writeAdd( ValueType.f32 );
                        break;
                    case 99: // dadd
                        writeAdd( ValueType.f64 );
                        break;
                    case 172: // ireturn
                    case 173: // lreturn
                    case 174: // freturn
                    case 175: // dreturn
                    case 177: // return void
                        writeReturn();
                        break;
                    default:
                        throw new WasmException( "Unimplemented byte code operation: " + op, sourceFile, lineNumber );
                }
            }
        } catch( Exception ex ) {
            throw WasmException.create( ex, sourceFile, lineNumber );
        }
    }

    /**
     * Write a constant value.
     * 
     * @param value
     *            the value
     * @throws IOException
     *             if any I/O error occur
     * @throws WasmException
     *             if the value type is not supported
     */
    private void writeConst( Object value ) throws IOException, WasmException {
        Class<?> clazz = value.getClass();
        if( clazz == Integer.class ) {
            writeConstInt( ((Integer)value).intValue() );
        } else if( clazz == Long.class ) {
            writeConstLong( ((Long)value).longValue() );
        } else if( clazz == Float.class ) {
            writeConstFloat( ((Float)value).floatValue() );
        } else if( clazz == Double.class ) {
            writeConstDouble( ((Double)value).doubleValue() );
        } else {
            throw new WasmException( "Not supported constant type: " + clazz, sourceFile, -1 );
        }
    }

    /**
     * Write a constant integer value
     * 
     * @param value
     *            the value
     * @throws IOException
     *             if any I/O error occur
     */
    protected abstract void writeConstInt( int value ) throws IOException;

    /**
     * Write a constant long value
     * 
     * @param value
     *            the value
     * @throws IOException
     *             if any I/O error occur
     */
    protected abstract void writeConstLong( long value ) throws IOException;

    /**
     * Write a constant float value
     * 
     * @param value
     *            the value
     * @throws IOException
     *             if any I/O error occur
     */
    protected abstract void writeConstFloat( float value ) throws IOException;

    /**
     * Write a constant double value
     * 
     * @param value
     *            the value
     * @throws IOException
     *             if any I/O error occur
     */
    protected abstract void writeConstDouble( double value ) throws IOException;

    /**
     * Write or Load a local variable.
     * 
     * @param load
     *            true: if load
     * @param valueType
     *            the type of the variable
     * @param idx
     *            the idx of the variable
     * @throws WasmException
     *             occur a if a variable was used for a different type
     * @throws IOException
     *             if any I/O error occur
     */
    private void writeLoadStore( boolean load, @Nonnull ValueType valueType, @Nonnegative int idx ) throws WasmException, IOException {
        while( locals.size() <= idx ) {
            locals.add( null );
        }
        ValueType oldType = locals.get( idx );
        if( oldType != null && oldType != valueType ) {
            throw new WasmException( "Redefine local variable type from " + oldType + " to " + valueType, sourceFile, -1 );
        }
        locals.set( idx, valueType );
        if( load ) {
            writeLoad( idx );
        } else {
            writeStore( idx );
        }
    }

    /**
     * Write a variable load.
     * 
     * @param idx
     *            the index of the parameter variable
     * @throws IOException
     *             if any I/O error occur
     */
    protected abstract void writeLoad( int idx ) throws IOException;

    /**
     * Write a variable store.
     * 
     * @param idx
     *            the index of the parameter variable
     * @throws IOException
     *             if any I/O error occur
     */
    protected abstract void writeStore( int idx ) throws IOException;

    /**
     * Write a add operator
     * 
     * @param valueType
     *            the type of the parameters
     * 
     * @throws IOException
     *             if any I/O error occur
     */
    protected abstract void writeAdd( @Nullable ValueType valueType ) throws IOException;

    /**
     * Write a return
     * 
     * @throws IOException
     *             if any I/O error occur
     */
    protected abstract void writeReturn() throws IOException;
}
