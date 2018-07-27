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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;

import de.inetsoftware.classparser.ClassFile;
import de.inetsoftware.classparser.Code;
import de.inetsoftware.classparser.CodeInputStream;
import de.inetsoftware.classparser.ConstantPool;
import de.inetsoftware.classparser.ConstantRef;
import de.inetsoftware.classparser.MethodInfo;
import de.inetsoftware.jwebassembly.WasmException;

/**
 * Generate the WebAssembly output.
 * 
 * @author Volker Berlin
 */
public class ModuleGenerator {

    private final ModuleWriter          writer;

    private int                         paramCount;

    private ValueType                   returnType;

    private LocaleVariableManager       localVariables = new LocaleVariableManager();

    private String                      sourceFile;

    private final List<WasmInstruction> instructions   = new ArrayList<>();

    private BranchManger                branchManager  = new BranchManger( instructions );

    private ValueStackManger            stackManager   = new ValueStackManger();

    /**
     * Create a new generator.
     * 
     * @param writer
     *            the target writer
     */
    public ModuleGenerator( @Nonnull ModuleWriter writer ) {
        this.writer = writer;
    }

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
     * Finish the prepare after all classes/methods are prepare. This must be call before we can start with write the
     * first method.
     */
    public void prepareFinish() {
        writer.prepareFinish();
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
        try {
            FunctionName name = new FunctionName( method );
            Map<String,Object> annotationValues = method.getAnnotation( "de.inetsoftware.jwebassembly.api.annotation.Import" );
            if( annotationValues != null ) {
                String impoarModule = (String)annotationValues.get( "module" );
                String importName = (String)annotationValues.get( "name" );
                writer.prepareImport( name, impoarModule, importName );
                writeMethodSignature( method );
            } else {
                writer.prepareFunction( name );
            }
        } catch( IOException ioex ) {
            throw WasmException.create( ioex, sourceFile, -1 );
        }
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
        CodeInputStream byteCode = null;
        try {
            Code code = method.getCode();
            if( code != null && method.getAnnotation( "de.inetsoftware.jwebassembly.api.annotation.Import" ) == null ) { // abstract methods and interface methods does not have code
                FunctionName name = new FunctionName( method );
                writeExport( name, method );
                writer.writeMethodStart( name );
                writeMethodSignature( method );

                localVariables.reset();
                stackManager.reset();
                branchManager.reset();

                byteCode = code.getByteCode();
                writeCode( byteCode, method.getConstantPool() );
                localVariables.calculate();

                for( WasmInstruction instruction : instructions ) {
                    instruction.writeTo( writer );
                }
                writer.writeMethodFinish( localVariables.getLocalTypes( paramCount ) );
            }
        } catch( Exception ioex ) {
            int lineNumber = byteCode == null ? -1 : byteCode.getLineNumber();
            throw WasmException.create( ioex, sourceFile, lineNumber );
        }
    }

    /**
     * Look for a Export annotation and if there write an export directive.
     * 
     * @param name
     *            the function name
     * @param method
     *            the method
     * 
     * @throws IOException
     *             if any IOException occur
     */
    private void writeExport( FunctionName name, MethodInfo method ) throws IOException {
        Map<String,Object> export = method.getAnnotation( "de.inetsoftware.jwebassembly.api.annotation.Export" );
        if( export != null ) {
            String exportName = (String)export.get( "name" );
            if( exportName == null ) {
                exportName = method.getName();  // TODO naming conversion rule if no name was set
            }
            writer.writeExport( name, exportName );
        }
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
        int paramCount = 0;
        ValueType type = null;
        for( int i = 1; i < signature.length(); i++ ) {
            paramCount++;
            if( signature.charAt( i ) == ')' ) {
                this.paramCount = paramCount - 1;
                kind = "result";
                continue;
            }
            type = getValueType( signature, i );
            if( type != null ) {
                writer.writeMethodParam( kind, type );
            }
        }
        this.returnType = type;
        writer.writeMethodParamFinish();
    }

    /**
     * Get the WebAssembly value type from a Java signature.
     * 
     * @param signature
     *            the signature
     * @param idx
     *            the index in the signature
     * @return the value type or null if void
     */
    private ValueType getValueType( String signature, int idx ) {
        String javaType;
        switch( signature.charAt( idx ) ) {
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
                return ValueType.i32;
            case 'D': // double
                return ValueType.f64;
            case 'F': // float
                return ValueType.f32;
            case 'J': // long
                return ValueType.i64;
            case 'V': // void
                return null;
            default:
                javaType = signature.substring( idx, idx + 1 );
        }
        throw new WasmException( "Not supported Java data type in method signature: " + javaType, sourceFile, -1 );
    }

    /**
     * Write the byte code of a method.
     * 
     * @param byteCode
     *            a stream of byte code
     * @param constantPool
     *            the constant pool of the the current class
     * @throws WasmException
     *             if some Java code can't converted
     */
    private void writeCode( CodeInputStream byteCode, ConstantPool constantPool  ) throws WasmException {
        instructions.clear();
        boolean endWithReturn = false;
        try {
            while( byteCode.available() > 0 ) {
                WasmInstruction instr = null;
                int codePos = byteCode.getCodePosition();
                endWithReturn = false;
                int op = byteCode.readUnsignedByte();
                switch( op ) {
                    case 0: // nop
                        break;
                    //TODO case 1: // aconst_null
                    case 2: // iconst_m1
                    case 3: // iconst_0
                    case 4: // iconst_1
                    case 5: // iconst_2
                    case 6: // iconst_3
                    case 7: // iconst_4
                    case 8: // iconst_5
                        stackManager.add( ValueType.i32, codePos );
                        instr = new WasmConstInstruction( Integer.valueOf( op - 3 ), codePos );
                        break;
                    case 9:  // lconst_0
                    case 10: // lconst_1
                        stackManager.add( ValueType.i64, codePos );
                        instr = new WasmConstInstruction( Long.valueOf( op - 9 ), codePos );
                        break;
                    case 11: // fconst_0
                    case 12: // fconst_1
                    case 13: // fconst_2
                        stackManager.add( ValueType.f32, codePos );
                        instr = new WasmConstInstruction( Float.valueOf( op - 11 ), codePos );
                        break;
                    case 14: // dconst_0
                    case 15: // dconst_1
                        stackManager.add( ValueType.f64, codePos );
                        instr = new WasmConstInstruction( Double.valueOf( op - 14 ), codePos );
                        break;
                    case 16: // bipush
                        stackManager.add( ValueType.i32, codePos );
                        instr = new WasmConstInstruction( Integer.valueOf( byteCode.readByte() ), codePos );
                        break;
                    case 17: // sipush
                        stackManager.add( ValueType.i32, codePos );
                        instr = new WasmConstInstruction( Integer.valueOf( byteCode.readShort() ), codePos );
                        break;
                    case 18: // ldc
                        stackManager.add( null, codePos );
                        instr = new WasmConstInstruction( (Number)constantPool.get( byteCode.readUnsignedByte() ), codePos );
                        break;
                    case 19: // ldc_w
                    case 20: // ldc2_w
                        stackManager.add( null, codePos );
                        instr = new WasmConstInstruction( (Number)constantPool.get( byteCode.readUnsignedShort() ), codePos );
                        break;
                    case 21: // iload
                        instr = loadStore( ValueType.i32, true, byteCode.readUnsignedByte(), codePos );
                        break;
                    case 22: // lload
                        instr = loadStore( ValueType.i64, true, byteCode.readUnsignedByte(), codePos );
                        break;
                    case 23: // fload
                        instr = loadStore( ValueType.f32, true, byteCode.readUnsignedByte(), codePos );
                        break;
                    case 24: // dload
                        instr = loadStore( ValueType.f64, true, byteCode.readUnsignedByte(), codePos );
                        break;
                    //TODO case 25: // aload
                    case 26: // iload_0
                    case 27: // iload_1
                    case 28: // iload_2
                    case 29: // iload_3
                        instr = loadStore( ValueType.i32, true, op - 26, codePos );
                        break;
                    case 30: // lload_0
                    case 31: // lload_1
                    case 32: // lload_2
                    case 33: // lload_3
                        instr = loadStore( ValueType.i64, true, op - 30, codePos );
                        break;
                    case 34: // fload_0
                    case 35: // fload_1
                    case 36: // fload_2
                    case 37: // fload_3
                        instr = loadStore( ValueType.f32, true, op - 34, codePos );
                        break;
                    case 38: // dload_0
                    case 39: // dload_1
                    case 40: // dload_2
                    case 41: // dload_3
                        instr = loadStore( ValueType.f64, true, op - 38, codePos );
                        break;
                    case 54: // istore
                        instr = loadStore( ValueType.i32, false, byteCode.readUnsignedByte(), codePos );
                        break;
                    case 55: // lstore
                        instr = loadStore( ValueType.i64, false, byteCode.readUnsignedByte(), codePos );
                        break;
                    case 56: // fstore
                        instr = loadStore( ValueType.f32, false, byteCode.readUnsignedByte(), codePos );
                        break;
                    case 57: // dstore
                        instr = loadStore( ValueType.f64, false, byteCode.readUnsignedByte(), codePos );
                        break;
                    //TODO case 58: // astore
                    case 59: // istore_0
                    case 60: // istore_1
                    case 61: // istore_2
                    case 62: // istore_3
                        instr = loadStore( ValueType.i32, false, op - 59, codePos );
                        break;
                    case 63: // lstore_0
                    case 64: // lstore_1
                    case 65: // lstore_2
                    case 66: // lstore_3
                        instr = loadStore( ValueType.i64, false, op - 63, codePos );
                        break;
                    case 67: // fstore_0
                    case 68: // fstore_1
                    case 69: // fstore_2
                    case 70: // fstore_3
                        instr = loadStore( ValueType.f32, false, op - 67, codePos );
                        break;
                    case 71: // dstore_0
                    case 72: // dstore_1
                    case 73: // dstore_2
                    case 74: // dstore_3
                        instr = loadStore( ValueType.f64, false, op - 71, codePos );
                        break;
                    case 87: // pop
                    case 88: // pop2
                        stackManager.remove();
                        instr = new WasmBlockInstruction( WasmBlockOperator.DROP, null, codePos );
                        break;
                    case 89: // dup: duplicate the value on top of the stack
                    case 90: // dup_x1
                    case 91: // dup_x2
                    case 92: // dup2
                    case 93: // dup2_x1
                    case 94: // dup2_x2
                    case 95: // swap
                        // can be do with functions with more as one return value in future WASM standard
                        throw new WasmException( "Stack duplicate is not supported in current WASM. try to save immediate values in a local variable: " + op, sourceFile, byteCode.getLineNumber() );
                    case 96: // iadd
                        instr = new WasmNumericInstruction( NumericOperator.add, ValueType.i32, codePos);
                        break;
                    case 97: // ladd
                        instr = new WasmNumericInstruction( NumericOperator.add, ValueType.i64, codePos );
                        break;
                    case 98: // fadd
                        instr = new WasmNumericInstruction( NumericOperator.add, ValueType.f32, codePos );
                        break;
                    case 99: // dadd
                        instr = new WasmNumericInstruction( NumericOperator.add, ValueType.f64, codePos );
                        break;
                    case 100: // isub
                        instr = new WasmNumericInstruction( NumericOperator.sub, ValueType.i32, codePos );
                        break;
                    case 101: // lsub
                        instr = new WasmNumericInstruction( NumericOperator.sub, ValueType.i64, codePos );
                        break;
                    case 102: // fsub
                        instr = new WasmNumericInstruction( NumericOperator.sub, ValueType.f32, codePos );
                        break;
                    case 103: // dsub
                        instr = new WasmNumericInstruction( NumericOperator.sub, ValueType.f64, codePos );
                        break;
                    case 104: // imul;
                        instr = new WasmNumericInstruction( NumericOperator.mul, ValueType.i32, codePos );
                        break;
                    case 105: // lmul
                        instr = new WasmNumericInstruction( NumericOperator.mul, ValueType.i64, codePos );
                        break;
                    case 106: // fmul
                        instr = new WasmNumericInstruction( NumericOperator.mul, ValueType.f32, codePos );
                        break;
                    case 107: // dmul
                        instr = new WasmNumericInstruction( NumericOperator.mul, ValueType.f64, codePos );
                        break;
                    case 108: // idiv
                        instr = new WasmNumericInstruction( NumericOperator.div, ValueType.i32, codePos );
                        break;
                    case 109: // ldiv
                        instr = new WasmNumericInstruction( NumericOperator.div, ValueType.i64, codePos );
                        break;
                    case 110: // fdiv
                        instr = new WasmNumericInstruction( NumericOperator.div, ValueType.f32, codePos );
                        break;
                    case 111: // ddiv
                        instr = new WasmNumericInstruction( NumericOperator.div, ValueType.f64, codePos );
                        break;
                    case 112: // irem
                        instr = new WasmNumericInstruction( NumericOperator.rem, ValueType.i32, codePos );
                        break;
                    case 113: // lrem
                        instr = new WasmNumericInstruction( NumericOperator.rem, ValueType.i64, codePos );
                        break;
                    case 114: // frem
                    case 115: // drem
                        //TODO can be implemented with a helper function like: (a - (long)(a / b) * (double)b) 
                        throw new WasmException( "Modulo/Remainder for floating numbers is not supported in WASM. Use int or long data types." + op, sourceFile, byteCode.getLineNumber() );
                    case 116: // ineg
                        instructions.add( new WasmConstInstruction( -1, codePos ) );
                        instr = new WasmNumericInstruction( NumericOperator.mul, ValueType.i32, codePos );
                        break;
                    case 117: // lneg
                        instructions.add( new WasmConstInstruction( (long)-1, codePos ) ) ;
                        instr = new WasmNumericInstruction( NumericOperator.mul, ValueType.i64, codePos );
                        break;
                    case 118: // fneg
                        instr = new WasmNumericInstruction( NumericOperator.neg, ValueType.f32, codePos );
                        break;
                    case 119: // dneg
                        instr = new WasmNumericInstruction( NumericOperator.neg, ValueType.f64, codePos );
                        break;
                    case 120: // ishl
                        instr = new WasmNumericInstruction( NumericOperator.shl, ValueType.i32, codePos );
                        break;
                    case 121: // lshl
                        instructions.add( new WasmConvertInstruction( ValueTypeConvertion.i2l, codePos ) ); // the shift parameter must be of type long!!!
                        instr = new WasmNumericInstruction( NumericOperator.shl, ValueType.i64, codePos );
                        break;
                    case 122: // ishr
                        instr = new WasmNumericInstruction( NumericOperator.shr_s, ValueType.i32, codePos );
                        break;
                    case 123: // lshr
                        instructions.add( new WasmConvertInstruction( ValueTypeConvertion.i2l, codePos ) ); // the shift parameter must be of type long!!!
                        instr = new WasmNumericInstruction( NumericOperator.shr_s, ValueType.i64, codePos );
                        break;
                    case 124: // iushr
                        instr = new WasmNumericInstruction( NumericOperator.shr_u, ValueType.i32, codePos );
                        break;
                    case 125: // lushr
                        instructions.add( new WasmConvertInstruction( ValueTypeConvertion.i2l, codePos ) ); // the shift parameter must be of type long!!!
                        instr = new WasmNumericInstruction( NumericOperator.shr_u, ValueType.i64, codePos );
                        break;
                    case 126: // iand
                        instr = new WasmNumericInstruction( NumericOperator.and, ValueType.i32, codePos );
                        break;
                    case 127: // land
                        instr = new WasmNumericInstruction( NumericOperator.and, ValueType.i64, codePos );
                        break;
                    case 128: // ior
                        instr = new WasmNumericInstruction( NumericOperator.or, ValueType.i32, codePos );
                        break;
                    case 129: // lor
                        instr = new WasmNumericInstruction( NumericOperator.or, ValueType.i64, codePos );
                        break;
                    case 130: // ixor
                        instr = new WasmNumericInstruction( NumericOperator.xor, ValueType.i32, codePos );
                        break;
                    case 131: // lxor
                        instr = new WasmNumericInstruction( NumericOperator.xor, ValueType.i64, codePos );
                        break;
                    case 132: // iinc
                        int idx = byteCode.readUnsignedByte();
                        instructions.add( new WasmLoadStoreInstruction( true, idx, localVariables, codePos ) );
                        instructions.add( new WasmConstInstruction( byteCode.readUnsignedByte(), codePos ) );
                        instructions.add( new WasmNumericInstruction( NumericOperator.add, ValueType.i32, codePos) );
                        instr = new WasmLoadStoreInstruction( false, idx, localVariables, codePos );
                        break;
                    case 133: // i2l
                        instr = new WasmConvertInstruction( ValueTypeConvertion.i2l, codePos );
                        break;
                    case 134: // i2f
                        instr = new WasmConvertInstruction( ValueTypeConvertion.i2f, codePos );
                        break;
                    case 135: // i2d
                        instr = new WasmConvertInstruction( ValueTypeConvertion.i2d, codePos );
                        break;
                    case 136: // l2i
                        instr = new WasmConvertInstruction( ValueTypeConvertion.l2i, codePos );
                        break;
                    case 137: // l2f
                        instr = new WasmConvertInstruction( ValueTypeConvertion.l2f, codePos );
                        break;
                    case 138: // l2d
                        instr = new WasmConvertInstruction( ValueTypeConvertion.l2d, codePos );
                        break;
                    case 139: // f2i
                        instr = new WasmConvertInstruction( ValueTypeConvertion.f2i, codePos );
                        break;
                    case 140: // f2l
                        instr = new WasmConvertInstruction( ValueTypeConvertion.f2l, codePos );
                        break;
                    case 141: // f2d
                        instr = new WasmConvertInstruction( ValueTypeConvertion.f2d, codePos );
                        break;
                    case 142: // d2i
                        instr = new WasmConvertInstruction( ValueTypeConvertion.d2i, codePos );
                        break;
                    case 143: // d2l
                        instr = new WasmConvertInstruction( ValueTypeConvertion.d2l, codePos );
                        break;
                    case 144: // d2f
                        instr = new WasmConvertInstruction( ValueTypeConvertion.d2f, codePos );
                        break;
                    case 145: // i2b
                        instructions.add( new WasmConstInstruction( 24, codePos ) );
                        instructions.add( new WasmNumericInstruction( NumericOperator.shl, ValueType.i32, codePos ) );
                        instructions.add( new WasmConstInstruction( 24, codePos ) );
                        instr = new WasmNumericInstruction( NumericOperator.shr_s, ValueType.i32, codePos );
                        break;
                    case 146: // i2c
                        instructions.add( new WasmConstInstruction( 0xFFFF, codePos ) );
                        instr = new WasmNumericInstruction( NumericOperator.and, ValueType.i32, codePos );
                        break;
                    case 147: // i2s
                        instructions.add( new WasmConstInstruction( 16, codePos ) );
                        instructions.add( new WasmNumericInstruction( NumericOperator.shl, ValueType.i32, codePos ) );
                        instructions.add( new WasmConstInstruction( 16, codePos ) );
                        instr = new WasmNumericInstruction( NumericOperator.shr_s, ValueType.i32, codePos );
                        break;
                    case 148: // lcmp
                        opCompare( ValueType.i64, byteCode, codePos );
                        break;
                    case 149: // fcmpl
                    case 150: // fcmpg
                        opCompare( ValueType.f32, byteCode, codePos );
                        break;
                    case 151: // dcmpl
                    case 152: // dcmpg
                        opCompare( ValueType.f64, byteCode, codePos );
                        break;
                    case 153: // ifeq
                        opIfCondition( NumericOperator.ne, NumericOperator.eq, byteCode, codePos );
                        break;
                    case 154: // ifne
                        opIfCondition( NumericOperator.eq, NumericOperator.ne, byteCode, codePos );
                        break;
                    case 155: // iflt
                        opIfCondition( NumericOperator.ge_s, NumericOperator.lt_s, byteCode, codePos );
                        break;
                    case 156: // ifge
                        opIfCondition( NumericOperator.lt_s, NumericOperator.ge_s, byteCode, codePos );
                        break;
                    case 157: // ifgt
                        opIfCondition( NumericOperator.le_s, NumericOperator.gt, byteCode, codePos );
                        break;
                    case 158: // ifle
                        opIfCondition( NumericOperator.gt, NumericOperator.le_s, byteCode, codePos );
                        break;
                    case 159: // if_icmpeq
                        opIfCompareCondition( NumericOperator.ne, NumericOperator.eq, byteCode, codePos );
                        break;
                    case 160: // if_icmpne
                        opIfCompareCondition( NumericOperator.eq, NumericOperator.ne, byteCode, codePos );
                        break;
                    case 161: // if_icmplt
                        opIfCompareCondition( NumericOperator.ge_s, NumericOperator.lt_s, byteCode, codePos );
                        break;
                    case 162: // if_icmpge
                        opIfCompareCondition( NumericOperator.lt_s, NumericOperator.ge_s, byteCode, codePos );
                        break;
                    case 163: // if_icmpgt
                        opIfCompareCondition( NumericOperator.le_s, NumericOperator.gt, byteCode, codePos );
                        break;
                    case 164: // if_icmple
                        opIfCompareCondition( NumericOperator.gt, NumericOperator.le_s, byteCode, codePos );
                        break;
                    //TODO case 165: // if_acmpeq
                    //TODO case 166: // if_acmpne
                    case 167: // goto
                        int offset = byteCode.readShort();
                        branchManager.start( JavaBlockOperator.GOTO, codePos, offset, byteCode.getLineNumber() );
                        instr = new WasmNopInstruction( codePos ); // marker of the line number for the branch manager
                        break;
                    case 170: // tableswitch
                    case 171: // lookupswitch
                        writeSwitchCode( byteCode, op == 171 );
                        break;
                    case 172: // ireturn
                    case 173: // lreturn
                    case 174: // freturn
                    case 175: // dreturn
                    case 177: // return void
                        instr = new WasmBlockInstruction( WasmBlockOperator.RETURN, null, codePos );
                        endWithReturn = true;
                        break;
                    case 184: // invokestatic
                        idx = byteCode.readUnsignedShort();
                        ConstantRef method = (ConstantRef)constantPool.get( idx );
                        String signature = method.getType();
                        ValueType type = getValueType(  signature, signature.indexOf( ')' ) + 1 );
                        if( type != null ) {
                            stackManager.add( type, codePos );
                        }
                        instr = new WasmCallInstruction( method.getConstantClass().getName() + '.' + method.getName() + method.getType(), codePos );
                        break;
                    default:
                        throw new WasmException( "Unimplemented Java byte code operation: " + op, sourceFile, byteCode.getLineNumber() );
                }
                if( instr != null ) {
                    instructions.add( instr );
                }
            }
            branchManager.calculate();
            branchManager.handle( byteCode ); // add branch operations
            if( !endWithReturn && returnType != null ) {
                // if a method ends with a loop without a break then code after the loop is no reachable
                // Java does not need a return byte code in this case
                // But WebAssembly need the dead code to validate
                instructions.add( new WasmBlockInstruction( WasmBlockOperator.UNREACHABLE, null, byteCode.getCodePosition() ) );
            }
        } catch( WasmException ex ) {
            throw ex;
        } catch( Exception ex ) {
            throw WasmException.create( ex, sourceFile, byteCode.getLineNumber() );
        }
    }

    /**
     * Write the both switch operation codes
     * 
     * @param byteCode
     *            the current stream with a position after the operation code
     * @param isLookupSwitch
     *            true, if the operation was a loopupswitch; false, if the operation was a tableswitch
     * @throws IOException
     *             if any I/O error occur
     */
    private void writeSwitchCode( CodeInputStream byteCode, boolean isLookupSwitch ) throws IOException {
        int lineNumber = byteCode.getLineNumber();
        int codePos = byteCode.getCodePosition();
        int startPosition = codePos;
        int padding = startPosition % 4;
        if( padding > 0 ) {
            byteCode.skip( 4 - padding );
        }
        startPosition--;

        int defaultPosition = startPosition + byteCode.readInt();
        int[] keys;
        int[] positions;
        if( isLookupSwitch ) { // lookupswitch
            localVariables.useTempI32();
            int count = byteCode.readInt();
            keys = new int[count];
            positions = new int[count];
            for( int i = 0; i < count; i++ ) {
                keys[i] = byteCode.readInt();
                positions[i] = startPosition + byteCode.readInt();
            }
            int tempI32 = localVariables.getTempI32();
            int block = 0;
            int defaultBlock = -1;
            int currentPos = -1;
            instructions.add( new WasmLoadStoreInstruction( false, tempI32, localVariables, codePos ) );
            do {
                int nextPos = findNext( currentPos, positions );
                if( nextPos == currentPos ) {
                    break;
                }
                currentPos = nextPos;
                if( defaultBlock < 0 ) {
                    if( defaultPosition <= currentPos ) {
                        defaultBlock = block;
                        if( defaultPosition < currentPos ) {
                            block++;
                        }
                    }
                }
                for( int i = 0; i < positions.length; i++ ) {
                    if( positions[i] == currentPos ) {
                        instructions.add( new WasmLoadStoreInstruction( true, tempI32, localVariables, codePos ) );
                        instructions.add( new WasmConstInstruction( keys[i], codePos ) );
                        instructions.add( new WasmNumericInstruction( NumericOperator.eq, ValueType.i32, codePos ) );
                        instructions.add( new WasmBlockInstruction( WasmBlockOperator.BR_IF, block, codePos ) );
                    }
                }
                block++;
            } while( true );
            if( defaultBlock < 0 ) {
                defaultBlock = block;
            }
            instructions.add( new WasmBlockInstruction( WasmBlockOperator.BR, defaultBlock, codePos ) );
        } else {
            int low = byteCode.readInt();
            keys = null;
            int count = byteCode.readInt() - low + 1;
            positions = new int[count];
            for( int i = 0; i < count; i++ ) {
                positions[i] = startPosition + byteCode.readInt();
            }
            if( low != 0 ) { // the br_table starts ever with the value 0. That we need to subtract the start value if it different
                instructions.add( new WasmConstInstruction( low, codePos ) );
                instructions.add( new WasmNumericInstruction( NumericOperator.sub, ValueType.i32, codePos ) );
            }
        }
        int switchValuestartPosition = stackManager.getCodePosition( 0 );
        branchManager.startSwitch( switchValuestartPosition, 0, lineNumber, keys, positions, defaultPosition );
    }

    /**
     * Find the next higher value.
     * 
     * @param current
     *            the current value
     * @param values
     *            the unordered list of values
     * @return the next value or current value if not found.
     */
    private static int findNext( int current, int[] values ) {
        boolean find = false;
        int next = Integer.MAX_VALUE;
        for( int val : values ) {
            if( val > current && val <= next ) {
                next = val;
                find = true;
            }
        }
        return find ? next : current;
    }

    /**
     * Create a WasmLoadStoreInstruction.
     * 
     * @param valueType
     *            the value type
     * @param load
     *            true: if load
     * @param idx
     *            the memory/slot idx of the variable
     * @param codePos
     *            the code position/offset in the Java method
     * @return the WasmLoadStoreInstruction
     */
    @Nonnull
    private WasmLoadStoreInstruction loadStore( ValueType valueType, boolean load, @Nonnegative int idx, int codePos ) {
        stackManager.add( valueType, codePos );
        localVariables.use( valueType, idx );
        return new WasmLoadStoreInstruction( load, idx, localVariables, codePos );
    }

    /**
     * Handle the if<condition> of the Java byte code. This Java instruction compare the first stack value with value 0.
     * Important: In the Java IF expression the condition for the jump to the else block is saved. In WebAssembler we
     * need to use condition for the if block. The caller of the method must already negate this
     * 
     * @param ifNumOp
     *            The condition for the if block.
     * @param continueNumOp
     *            The condition for the continue of a loop.
     * @param byteCode
     *            current byte code stream to read the target offset.
     * @param codePos
     *            the code position/offset in the Java method
     * @throws IOException
     *             if any I/O errors occur.
     */
    private void opIfCondition( NumericOperator ifNumOp, NumericOperator continueNumOp, CodeInputStream byteCode, int codePos ) throws IOException {
        instructions.add( new WasmConstInstruction( 0, codePos ) );
        opIfCompareCondition( ifNumOp, continueNumOp, byteCode, codePos );
    }

    /**
     * Handle the if<condition> of the Java byte code. This Java instruction compare 2 values from stack.
     * Important: In the Java IF expression the condition for the jump to the else block is saved. In WebAssembler we need to use
     * condition for the if block. The caller of the method must already negate this.
     * 
     * @param ifNumOp
     *            The condition for the if block.
     * @param continueNumOp
     *            The condition for the continue of a loop.
     * @param byteCode
     *            current byte code stream to read the target offset.
     * @param codePos
     *            the code position/offset in the Java method
     * @throws IOException
     *             if any I/O errors occur.
     */
    private void opIfCompareCondition( NumericOperator ifNumOp, NumericOperator continueNumOp, CodeInputStream byteCode, int codePos ) throws IOException {
        int offset = byteCode.readShort();
        branchManager.start( JavaBlockOperator.IF, codePos, offset, byteCode.getLineNumber() );
        instructions.add( new WasmNumericInstruction( offset > 0 ? ifNumOp : continueNumOp, ValueType.i32, codePos ) );
    }

    /**
     * Handle the different compare operator. The compare operator returns the integer values -1, 0 or 1. There is no
     * equivalent in WebAssembly. That we need to read the next operation to find an equivalent.
     * 
     * @param valueType
     *            the value type of the compared
     * @param byteCode
     *            current byte code stream to read the next operation.
     * @param codePos
     *            the code position/offset in the Java method
     * @throws IOException
     *             if any I/O errors occur.
     */
    private void opCompare( ValueType valueType, CodeInputStream byteCode, int codePos ) throws IOException {
        codePos = byteCode.getCodePosition();
        NumericOperator numOp;
        int nextOp = byteCode.read();
        switch( nextOp ){
            case 153: // ifeq
                numOp = NumericOperator.ne;
                break;
            case 154: // ifne
                numOp = NumericOperator.eq;
                break;
            case 155: // iflt
                numOp = NumericOperator.gt;
                break;
            case 156: // ifge
                numOp = NumericOperator.le_s;
                break;
            case 157: // ifgt
                numOp = NumericOperator.lt_s;
                break;
            case 158: // ifle
                numOp = NumericOperator.ge_s;
                break;
            default:
                throw new WasmException( "Unexpected compare sub operation: " + nextOp, null, -1 );
        }
        int offset = byteCode.readShort();
        branchManager.start( JavaBlockOperator.IF, codePos, offset, byteCode.getLineNumber() );
        instructions.add( new WasmNumericInstruction( numOp, valueType, codePos ) );
    }

}
