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
package de.inetsoftware.jwebassembly.text;

import java.io.Closeable;
import java.io.IOException;

import de.inetsoftware.classparser.ClassFile;
import de.inetsoftware.classparser.Code;
import de.inetsoftware.classparser.MethodInfo;
import de.inetsoftware.jwebassembly.WasmException;

/**
 * Module Writer for text format with S-expressions.
 * 
 * @author Volker Berlin
 *
 */
public class TextModulWriter implements Closeable {

    private Appendable output;

    private int        inset;

    /**
     * Create a new instance.
     * 
     * @param output
     *            target for the result
     * @throws IOException
     *             if any I/O error occur
     */
    public TextModulWriter( Appendable output ) throws IOException {
        this.output = output;
        output.append( "(module" );
        inset++;
    }

    /**
     * 
     */
    @Override
    public void close() throws IOException {
        inset--;
        newline();
        output.append( ')' );
    }

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
        newline();
        output.append( "(func $" );
        output.append( method.getName() );
        writeMethodSignature( method );
        inset++;
        inset--;
        newline();
        output.append( ')' );
    }

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
                    output.append( " (" ).append( kind ).append( " f64)" );
                    continue;
                case 'F': // float
                    output.append( " (" ).append( kind ).append( " f32)" );
                    continue;
                case 'I': // int
                    output.append( " (" ).append( kind ).append( " i32)" );
                    continue;
                case 'J': // long
                    output.append( " (" ).append( kind ).append( " i64)" );
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
     * Add a newline the insets.
     * 
     * @throws IOException
     *             if any I/O error occur
     */
    private void newline() throws IOException {
        output.append( '\n' );
        for( int i = 0; i < inset; i++ ) {
            output.append( ' ' );
            output.append( ' ' );
        }
    }
}
