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
import java.util.function.Consumer;

import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import de.inetsoftware.classparser.Annotations;
import de.inetsoftware.classparser.ClassFile;
import de.inetsoftware.classparser.Code;
import de.inetsoftware.classparser.CodeInputStream;
import de.inetsoftware.classparser.ConstantPool;
import de.inetsoftware.classparser.ConstantRef;
import de.inetsoftware.classparser.LineNumberTable;
import de.inetsoftware.classparser.LocalVariableTable;
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

    private LocalVariableTable   localTable;

    private String               sourceFile;

    /**
     * Prepare the content of the class.
     * 
     * @param classFile
     *            the class file
     * @throws WasmException
     *             if some Java code can't converted
     */
    public void prepare( ClassFile classFile ) {
        iterateMethods( classFile, m -> prepareMethod( m ) );
    }

    /**
     * Write the content of the class to the writer.
     * 
     * @param classFile
     *            the class file
     * @throws WasmException
     *             if some Java code can't converted
     */
    public void write( ClassFile classFile ) throws WasmException {
        iterateMethods( classFile, m -> writeMethod( m ) );
    }

    private void iterateMethods( ClassFile classFile, Consumer<MethodInfo> handler ) throws WasmException {
        sourceFile = null; // clear previous value for the case an IO exception occur
        try {
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
                handler.accept( method );
            }
        } catch( IOException ioex ) {
            throw WasmException.create( ioex, sourceFile, -1 );
        }
    }

    /**
     * Prepare the method.
     * 
     * @param method
     *            the method
     * @throws WasmException
     *             if some Java code can't converted
     */
    private void prepareMethod( MethodInfo method ) throws WasmException {
        
    }

    /**
     * Write the content of a method.
     * 
     * @param method
     *            the method
     * @throws WasmException
     *             if some Java code can't converted
     */
    private void writeMethod( MethodInfo method ) throws WasmException {
        try {
            Code code = method.getCode();
            if( code != null ) { // abstract methods and interface methods does not have code
                String methodName = method.getName(); // TODO naming conversion rule
                writeExport( methodName, method );
                writeMethodStart( methodName );
                writeMethodSignature( method );
                locals.clear();
                localTable = code.getLocalVariableTable();
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
        } catch( IOException ioex ) {
            throw WasmException.create( ioex, sourceFile, -1 );
        }
    }

    /**
     * Look for a Export annotation and if there write an export directive.
     * 
     * @param methodName
     *            the normalized method name
     * @param method
     *            the moethod
     * @throws IOException
     *             if any IOException occur
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
                    case 2: // iconst_m1
                    case 3: // iconst_0
                    case 4: // iconst_1
                    case 5: // iconst_2
                    case 6: // iconst_3
                    case 7: // iconst_4
                    case 8: // iconst_5
                        writeConstInt( op - 3 );
                        break;
                    case 9:  // lconst_0
                    case 10: // lconst_1
                        writeConstLong( op - 9 );
                        break;
                    case 11: // fconst_0
                    case 12: // fconst_1
                    case 13: // fconst_2
                        writeConstFloat( op - 11 );
                        break;
                    case 14: // dconst_0
                    case 15: // dconst_1
                        writeConstDouble( op - 14 );
                        break;
                    case 16: // bipush
                        writeConstInt( byteCode.readByte() );
                        break;
                    case 17: // sipush
                        writeConstInt( byteCode.readShort() );
                        break;
                    case 18: // ldc
                        writeConst( constantPool.get( byteCode.readUnsignedByte() ) );
                        break;
                    case 20: // ldc2_w
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
                    case 63: // lstore_0
                    case 64: // lstore_1
                    case 65: // lstore_2
                    case 66: // lstore_3
                        writeLoadStore( false, ValueType.i64, op - 63 );
                        break;
                    case 67: // fstore_0
                    case 68: // fstore_1
                    case 69: // fstore_2
                    case 70: // fstore_3
                        writeLoadStore( false, ValueType.f32, op - 67 );
                        break;
                    case 71: // dstore_0
                    case 72: // dstore_1
                    case 73: // dstore_2
                    case 74: // dstore_3
                        writeLoadStore( false, ValueType.f64, op - 71 );
                        break;
                    case 96: // iadd
                        writeNumericOperator( NumericOperator.add, ValueType.i32);
                        break;
                    case 97: // ladd
                        writeNumericOperator( NumericOperator.add, ValueType.i64 );
                        break;
                    case 98: // fadd
                        writeNumericOperator( NumericOperator.add, ValueType.f32 );
                        break;
                    case 99: // dadd
                        writeNumericOperator( NumericOperator.add, ValueType.f64 );
                        break;
                    case 100: // isub
                        writeNumericOperator( NumericOperator.sub, ValueType.i32 );
                        break;
                    case 101: // lsub
                        writeNumericOperator( NumericOperator.sub, ValueType.i64 );
                        break;
                    case 102: // fsub
                        writeNumericOperator( NumericOperator.sub, ValueType.f32 );
                        break;
                    case 103: // dsub
                        writeNumericOperator( NumericOperator.sub, ValueType.f64 );
                        break;
                    case 104: // imul;
                        writeNumericOperator( NumericOperator.mul, ValueType.i32 );
                        break;
                    case 105: // lmul
                        writeNumericOperator( NumericOperator.mul, ValueType.i64 );
                        break;
                    case 106: // fmul
                        writeNumericOperator( NumericOperator.mul, ValueType.f32 );
                        break;
                    case 107: // dmul
                        writeNumericOperator( NumericOperator.mul, ValueType.f64 );
                        break;
                    case 108: // idiv
                        writeNumericOperator( NumericOperator.div, ValueType.i32 );
                        break;
                    case 109: // ldiv
                        writeNumericOperator( NumericOperator.div, ValueType.i64 );
                        break;
                    case 110: // fdiv
                        writeNumericOperator( NumericOperator.div, ValueType.f32 );
                        break;
                    case 111: // ddiv
                        writeNumericOperator( NumericOperator.div, ValueType.f64 );
                        break;
                    case 112: // irem
                        writeNumericOperator( NumericOperator.rem, ValueType.i32 );
                        break;
                    case 113: // lrem
                        writeNumericOperator( NumericOperator.rem, ValueType.i64 );
                        break;
                    case 114: // frem
                    case 115: // drem
                        throw new WasmException( "Modulo/Remainder for floating numbers is not supported in WASM. Use int or long data types." + op, sourceFile, lineNumber );
                    case 120: // ishl
                        writeNumericOperator( NumericOperator.shl, ValueType.i32 );
                        break;
                    case 121: // lshl
                        writeCast( ValueTypeConvertion.i2l ); // the shift parameter must be of type long!!!
                        writeNumericOperator( NumericOperator.shl, ValueType.i64 );
                        break;
                    case 122: // ishr
                        writeNumericOperator( NumericOperator.shr_s, ValueType.i32 );
                        break;
                    case 123: // lshr
                        writeCast( ValueTypeConvertion.i2l ); // the shift parameter must be of type long!!!
                        writeNumericOperator( NumericOperator.shr_s, ValueType.i64 );
                        break;
                    case 124: // iushr
                        writeNumericOperator( NumericOperator.shr_u, ValueType.i32 );
                        break;
                    case 125: // lushr
                        writeCast( ValueTypeConvertion.i2l ); // the shift parameter must be of type long!!!
                        writeNumericOperator( NumericOperator.shr_u, ValueType.i64 );
                        break;
                    case 126: // iand
                        writeNumericOperator( NumericOperator.and, ValueType.i32 );
                        break;
                    case 127: // land
                        writeNumericOperator( NumericOperator.and, ValueType.i64 );
                        break;
                    case 128: // ior
                        writeNumericOperator( NumericOperator.or, ValueType.i32 );
                        break;
                    case 129: // lor
                        writeNumericOperator( NumericOperator.or, ValueType.i64 );
                        break;
                    case 130: // ixor
                        writeNumericOperator( NumericOperator.xor, ValueType.i32 );
                        break;
                    case 131: // lxor
                        writeNumericOperator( NumericOperator.xor, ValueType.i64 );
                        break;
                    case 132: // iinc
                        int idx = byteCode.readUnsignedByte();
                        writeLoadStore( true, ValueType.i32, idx );
                        writeConstInt( byteCode.readUnsignedByte() );
                        writeNumericOperator( NumericOperator.add, ValueType.i32);
                        writeLoadStore( false, ValueType.i32, idx );
                        break;
                    case 136: // l2i
                        writeCast( ValueTypeConvertion.l2i );
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
     *            the memory/slot idx of the variable
     * @throws WasmException
     *             occur a if a variable was used for a different type
     * @throws IOException
     *             if any I/O error occur
     */
    private void writeLoadStore( boolean load, @Nonnull ValueType valueType, @Nonnegative int idx ) throws WasmException, IOException {
        idx = localTable.get( idx ).getPosition(); // translate slot index to position index
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
     * @param numOp TODO
     * @param valueType
     *            the type of the parameters
     * 
     * @throws IOException
     *             if any I/O error occur
     */
    protected abstract void writeNumericOperator( NumericOperator numOp, @Nullable ValueType valueType ) throws IOException;

    /**
     * Cast a value from one type to another
     * 
     * @param cast
     *            the operator
     * @throws IOException
     *             if any I/O error occur
     */
    protected abstract void writeCast( ValueTypeConvertion cast ) throws IOException;

    /**
     * Write a return
     * 
     * @throws IOException
     *             if any I/O error occur
     */
    protected abstract void writeReturn() throws IOException;
}
