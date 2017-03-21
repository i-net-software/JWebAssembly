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
package de.inetsoftware.jwebassembly.modul;

import java.io.Closeable;
import java.io.IOException;

import de.inetsoftware.classparser.ClassFile;
import de.inetsoftware.classparser.Code;
import de.inetsoftware.classparser.CodeInputStream;
import de.inetsoftware.classparser.LineNumberTable;
import de.inetsoftware.classparser.MethodInfo;
import de.inetsoftware.jwebassembly.WasmException;

/**
 * Module Writer for text format with S-expressions.
 * 
 * @author Volker Berlin
 */
public abstract class ModulWriter implements Closeable {

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
        writeMethodStart( method.getName() );
        writeMethodSignature( method );
        Code code = method.getCode();
        if( code != null ) {
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
                    writeCodeChunk( byteCode, lineNumber );
                }
            } else {
                CodeInputStream byteCode = code.getByteCode();
                writeCodeChunk( byteCode, -1 );
            }
        }
        writeMethodFinish();
    }

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
        for( int i = 1; i < signature.length(); i++ ) {
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
                    kind = "return";
                    continue;
                default:
                    javaType = signature.substring( i, i + 1 );
            }
            Code code = method.getCode();
            int lineNumber = code == null ? -1 : code.getFirstLineNr();
            throw new WasmException( "Not supported Java data type in method signature: " + javaType, lineNumber );
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
     * @throws IOException
     *             if any I/O error occur
     */
    protected abstract void writeMethodFinish() throws IOException;

    /**
     * Write a chunk of byte code.
     * 
     * @param byteCode
     *            a stream of byte code
     * @param lineNumber
     *            the current line number
     * @throws WasmException
     *             if some Java code can't converted
     */
    private void writeCodeChunk( CodeInputStream byteCode, int lineNumber ) throws WasmException {
        try {
            while( byteCode.available() > 0 ) {
                int op = byteCode.readUnsignedByte();
                switch( op ) {
                    case 4: // iconst_1
                        writeConstInt( 1 );
                        break;
                    case 26: // iload_0
                        writeLoadInt( 0 );
                        break;
                    case 60: // istore_1
                        writeStoreInt( 1 );
                        break;
                    case 96: // iadd
                        writeAddInt();
                        break;
                    case 177: // return void
                        writeReturn();
                        break;
                    default:
                        throw new WasmException( "Unimplemented byte code operation: " + op, lineNumber );
                }
            }
        } catch( Exception ex ) {
            throw WasmException.create( ex, lineNumber );
        }
    }

    /**
     * Write a const integer value
     * 
     * @param value
     *            the value
     * @throws IOException
     *             if any I/O error occur
     */
    protected abstract void writeConstInt( int value ) throws IOException;

    /**
     * Write a load integer
     * 
     * @param idx
     *            the index of the parameter variable
     * @throws IOException
     *             if any I/O error occur
     */
    protected abstract void writeLoadInt( int idx ) throws IOException;

    /**
     * Write a store integer.
     * 
     * @param idx
     *            the index of the parameter variable
     * @throws IOException
     *             if any I/O error occur
     */
    protected abstract void writeStoreInt( int idx ) throws IOException;

    /**
     * Write a add operator
     * 
     * @throws IOException
     *             if any I/O error occur
     */
    protected abstract void writeAddInt() throws IOException;

    /**
     * Write a return
     * 
     * @throws IOException
     *             if any I/O error occur
     */
    protected abstract void writeReturn() throws IOException;
}
