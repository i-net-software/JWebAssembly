/*
 * Copyright 2017 - 2018 Volker Berlin (i-net software)
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

    private BranchManger         branchManager = new BranchManger();

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
    protected void prepareMethod( MethodInfo method ) throws WasmException {
        // Nothing
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
                String methodName = method.getName();
                String className = method.getDeclaringClassFile().getThisClass().getName();
                String fullName = className + '.' + methodName;
                String signatureName = fullName + method.getDescription();
                writeExport( signatureName, fullName, method );
                writeMethodStart( signatureName, fullName );
                writeMethodSignature( method );
                locals.clear();
                localTable = code.getLocalVariableTable();

                branchManager.reset();
                for( CodeInputStream byteCode : code.getByteCodes() ) {
                    prepareBranchManager( byteCode, byteCode.getLineNumber() );
                }
                branchManager.calculate();

                for( CodeInputStream byteCode : code.getByteCodes() ) {
                    writeCodeChunk( byteCode, byteCode.getLineNumber(), method.getConstantPool() );
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
     * @param signatureName
     *            the full name with signature
     * @param methodName
     *            the normalized method name
     * @param method
     *            the moethod
     * 
     * @throws IOException
     *             if any IOException occur
     */
    private void writeExport( String signatureName, String methodName, MethodInfo method ) throws IOException {
        Annotations annotations = method.getRuntimeInvisibleAnnotations();
        if( annotations != null ) {
            Map<String,Object> export = annotations.get( "org.webassembly.annotation.Export" );
            if( export != null ) {
                String exportName = (String)export.get( "name" );
                if( exportName == null ) {
                    exportName = method.getName();  // TODO naming conversion rule if no name was set
                }
                writeExport( signatureName, methodName, exportName );
            }
        }
    }

    /**
     * Write an export directive
     * @param signatureName
     *            the full name with signature
     * @param methodName
     *            the method name
     * @param exportName
     *            the export name, if null then the same like the method name
     * 
     * @throws IOException
     *             if any I/O error occur
     */
    protected abstract void writeExport( String signatureName, String methodName, String exportName ) throws IOException;

    /**
     * Write the method header.
     * @param signatureName
     *            the full name with signature
     * @param name
     *            the method name
     * 
     * @throws IOException
     *             if any I/O error occur
     */
    protected abstract void writeMethodStart( String signatureName, String name ) throws IOException;

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
                case 'C': // char
                case 'S': // short
                case 'I': // int
                    writeMethodParam( kind, ValueType.i32 );
                    continue;
                case 'D': // double
                    writeMethodParam( kind, ValueType.f64 );
                    continue;
                case 'F': // float
                    writeMethodParam( kind, ValueType.f32 );
                    continue;
                case 'J': // long
                    writeMethodParam( kind, ValueType.i64 );
                    continue;
                case 'V': // void
                    continue;
                case ')':
                    this.paramCount = paramCount - 1;
                    kind = "result";
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
     *            "param", "result" or "local"
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
     * @throws WasmException
     *             if some Java code can't converted
     */
    private void prepareBranchManager( CodeInputStream byteCode, int lineNumber  ) throws WasmException {
        try {
            while( byteCode.available() > 0 ) {
                int op = byteCode.readUnsignedByte();
                switch( op ) {
                    case 16: // bipush
                    case 18: // ldc
                    case 21: //iload
                    case 22: //lload
                    case 23: //fload
                    case 24: //dload
                    case 25: //aload
                    case 54: // istore
                    case 55: // lstore
                    case 56: // fstore
                    case 57: // dstore
                    case 58: // astore
                    case 179: // putstatic
                    case 181: // putfield
                        byteCode.skip(1);
                        break;
                    case 17: // sipush
                    case 19: // ldc_w
                    case 20: // ldc2_w
                    case 132: // iinc
                    case 184: // invokestatic
                        byteCode.skip( 2);
                        break;
                    case 153: // ifeq
                    case 154: // ifne
                    case 155: // iflt
                    case 156: // ifge
                    case 157: // ifgt
                    case 158: // ifle
                    case 159: // if_icmpeq
                    case 160: // if_icmpne
                    case 161: // if_icmplt
                    case 162: // if_icmpge
                    case 163: // if_icmpgt
                    case 164: // if_icmple
                    case 165: // if_acmpeq
                    case 166: // if_acmpne
                        int startPosition = byteCode.getCodePosition() + 2;
                        int offset = byteCode.readShort();
                        branchManager.start( BlockOperator.IF, startPosition, offset - 3 );
                        break;
                    case 167: // goto
                        startPosition = byteCode.getCodePosition() - 1;
                        offset = byteCode.readShort();
                        branchManager.start( BlockOperator.GOTO, startPosition, offset );
                        break;
                }
            }
        } catch( Exception ex ) {
            throw WasmException.create( ex, sourceFile, lineNumber );
        }
    }
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
                branchManager.handle( byteCode, this );
                int op = byteCode.readUnsignedByte();
                switch( op ) {
                    case 0: // nop
                        return;
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
                    case 19: // ldc_w
                    case 20: // ldc2_w
                        writeConst( constantPool.get( byteCode.readUnsignedShort() ) );
                        break;
                    case 21: // iload
                        writeLoadStore( true, ValueType.i32, byteCode.readUnsignedByte() );
                        break;
                    case 22: // lload
                        writeLoadStore( true, ValueType.i64, byteCode.readUnsignedByte() );
                        break;
                    case 23: // fload
                        writeLoadStore( true, ValueType.f32, byteCode.readUnsignedByte() );
                        break;
                    case 24: // dload
                        writeLoadStore( true, ValueType.f64, byteCode.readUnsignedByte() );
                        break;
                    //TODO case 25: // aload
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
                    case 54: // istore
                        writeLoadStore( false, ValueType.i32, byteCode.readUnsignedByte() );
                        break;
                    case 55: // lstore
                        writeLoadStore( false, ValueType.i64, byteCode.readUnsignedByte() );
                        break;
                    case 56: // fstore
                        writeLoadStore( false, ValueType.f32, byteCode.readUnsignedByte() );
                        break;
                    case 57: // dstore
                        writeLoadStore( false, ValueType.f64, byteCode.readUnsignedByte() );
                        break;
                    //TODO case 58: // astore
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
                    case 133: // i2l
                        writeCast( ValueTypeConvertion.i2l );
                        break;
                    case 134: // i2f
                        writeCast( ValueTypeConvertion.i2f );
                        break;
                    case 135: // i2d
                        writeCast( ValueTypeConvertion.i2d );
                        break;
                    case 136: // l2i
                        writeCast( ValueTypeConvertion.l2i );
                        break;
                    case 137: // l2f
                        writeCast( ValueTypeConvertion.l2f );
                        break;
                    case 138: // l2d
                        writeCast( ValueTypeConvertion.l2d );
                        break;
                    case 139: // f2i
                        writeCast( ValueTypeConvertion.f2i );
                        break;
                    case 140: // f2l
                        writeCast( ValueTypeConvertion.f2l );
                        break;
                    case 141: // f2d
                        writeCast( ValueTypeConvertion.f2d );
                        break;
                    case 142: // d2i
                        writeCast( ValueTypeConvertion.d2i );
                        break;
                    case 143: // d2l
                        writeCast( ValueTypeConvertion.d2l );
                        break;
                    case 144: // d2f
                        writeCast( ValueTypeConvertion.d2f );
                        break;
                    case 145: // i2b
                        writeConstInt( 24 );
                        writeNumericOperator( NumericOperator.shl, ValueType.i32 );
                        writeConstInt( 24 );
                        writeNumericOperator( NumericOperator.shr_s, ValueType.i32 );
                        break;
                    case 146: // i2c
                        writeConstInt( 0xFFFF );
                        writeNumericOperator( NumericOperator.and, ValueType.i32 );
                        break;
                    case 147: // i2s
                        writeConstInt( 16 );
                        writeNumericOperator( NumericOperator.shl, ValueType.i32 );
                        writeConstInt( 16 );
                        writeNumericOperator( NumericOperator.shr_s, ValueType.i32 );
                        break;
                    case 153: // ifeq
                        opIfCondition( NumericOperator.ne, byteCode );
                        break;
                    case 154: // ifne
                        opIfCondition( NumericOperator.eq, byteCode );
                        break;
                    case 155: // iflt
                        opIfCondition( NumericOperator.gt, byteCode );
                        break;
                    case 156: // ifge
                        opIfCondition( NumericOperator.le_s, byteCode );
                        break;
                    case 157: // ifgt
                        opIfCondition( NumericOperator.lt_s, byteCode );
                        break;
                    case 158: // ifle
                        opIfCondition( NumericOperator.ge_s, byteCode );
                        break;
                    case 167: // goto
                        byteCode.skip(2);
                        break;
                    case 172: // ireturn
                    case 173: // lreturn
                    case 174: // freturn
                    case 175: // dreturn
                    case 177: // return void
                        writeReturn();
                        break;
                    case 184: // invokestatic
                        idx = byteCode.readUnsignedShort();
                        ConstantRef method = (ConstantRef)constantPool.get( idx );
                        writeFunctionCall( method.getConstantClass().getName() + '.' + method.getName() + method.getType() );
                        break;
                    default:
                        throw new WasmException( "Unimplemented Java byte code operation: " + op, sourceFile, lineNumber );
                }
            }
        } catch( Exception ex ) {
            throw WasmException.create( ex, sourceFile, lineNumber );
        }
    }

    /**
     * Handle the if<condition> of the Java byte code. This Java instruction compare the first stack value with value 0.
     * Important: In Java the condition for the jump to the else block is saved. In WebAssembler we need to use
     * condition for the if block. The caller of the method must already negate this
     * 
     * @param numOp
     *            The condition for the if block.
     * @param byteCode
     *            current byte code stream to read the taget offset.
     * @throws IOException
     *             if any I/O errors occur.
     */
    private void opIfCondition( NumericOperator numOp, CodeInputStream byteCode ) throws IOException {
        byteCode.skip(2);
        writeConstInt( 0 );
        writeNumericOperator( numOp, ValueType.i32 );
        //writeBlockCode( BlockOperator.IF );
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
     * 
     * @param numOp
     *            the numeric operation
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

    /**
     * Write a call to a function.
     * 
     * @param name
     *            the full qualified method name
     * @throws IOException
     *             if any I/O error occur
     */
    protected abstract void writeFunctionCall( String name ) throws IOException;

    /**
     * Write a block/branch code
     * 
     * @param op
     *            the operation
     * @throws IOException
     *             if any I/O error occur
     */
    protected abstract void writeBlockCode( BlockOperator op ) throws IOException;
}
