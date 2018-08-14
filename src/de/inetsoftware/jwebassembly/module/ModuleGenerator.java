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

    private ValueType                   returnType;

    private LocaleVariableManager       localVariables = new LocaleVariableManager();

    private String                      sourceFile;

    private final List<WasmInstruction> instructions   = new ArrayList<>();

    private BranchManger                branchManager  = new BranchManger( instructions );

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
        } catch( Exception ioex ) {
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

                localVariables.reset();
                branchManager.reset();

                byteCode = code.getByteCode();
                writeCode( byteCode, method.getConstantPool() );
                localVariables.calculate();
                writeMethodSignature( method );

                for( WasmInstruction instruction : instructions ) {
                    instruction.writeTo( writer );
                }
                writer.writeMethodFinish();
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
            if( signature.charAt( i ) == ')' ) {
                kind = "result";
                continue;
            }
            if( kind == "param" ) {
                paramCount++;
            }
            type = ValueType.getValueType( signature, i );
            if( type != null ) {
                writer.writeMethodParam( kind, type );
            }
        }
        this.returnType = type;
        writer.writeMethodParamFinish( localVariables.getLocalTypes( paramCount ) );
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
                        instr = new WasmConstInstruction( Integer.valueOf( op - 3 ), ValueType.i32, codePos );
                        break;
                    case 9:  // lconst_0
                    case 10: // lconst_1
                        instr = new WasmConstInstruction( Long.valueOf( op - 9 ), ValueType.i64, codePos );
                        break;
                    case 11: // fconst_0
                    case 12: // fconst_1
                    case 13: // fconst_2
                        instr = new WasmConstInstruction( Float.valueOf( op - 11 ), ValueType.f32, codePos );
                        break;
                    case 14: // dconst_0
                    case 15: // dconst_1
                        instr = new WasmConstInstruction( Double.valueOf( op - 14 ), ValueType.f64, codePos );
                        break;
                    case 16: // bipush
                        instr = new WasmConstInstruction( Integer.valueOf( byteCode.readByte() ), ValueType.i32, codePos );
                        break;
                    case 17: // sipush
                        instr = new WasmConstInstruction( Integer.valueOf( byteCode.readShort() ), ValueType.i32, codePos );
                        break;
                    case 18: // ldc
                        instr = new WasmConstInstruction( (Number)constantPool.get( byteCode.readUnsignedByte() ), codePos );
                        break;
                    case 19: // ldc_w
                    case 20: // ldc2_w
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
                    //TODO case 42: //aload_0
                    //TODO case 43: //aload_1
                    //TODO case 44: //aload_2
                    //TODO case 45: //aload_3
                    //TODO case 46: // iaload
                    //TODO case 47: // laload
                    //TODO case 48: // faload
                    //TODO case 49: // daload
                    //TODO case 50: // aaload
                    //TODO case 51: // baload
                    //TODO case 52: // caload
                    //TODO case 53: // saload
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
                    //TODO case 75: // astore_0
                    //TODO case 76: // astore_1
                    //TODO case 77: // astore_2
                    //TODO case 78: // astore_3
                    //TODO case 79: // iastore
                    //TODO case 80: // lastore
                    //TODO case 81: // fastore
                    //TODO case 82: // dastore
                    //TODO case 83: // aastore
                    //TODO case 84: // bastore
                    //TODO case 85: // castore
                    //TODO case 86: // sastore
                    case 87: // pop
                    case 88: // pop2
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
                        instructions.add( new WasmConstInstruction( -1, ValueType.i32, codePos ) );
                        instr = new WasmNumericInstruction( NumericOperator.mul, ValueType.i32, codePos );
                        break;
                    case 117: // lneg
                        instructions.add( new WasmConstInstruction( (long)-1, ValueType.i64, codePos ) ) ;
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
                        instructions.add( new WasmConstInstruction( (int)byteCode.readByte(), ValueType.i32, codePos ) );
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
                        instr = new WasmConvertInstruction( ValueTypeConvertion.i2b, codePos );
                        break;
                    case 146: // i2c
                        instructions.add( new WasmConstInstruction( 0xFFFF, ValueType.i32, codePos ) );
                        instr = new WasmNumericInstruction( NumericOperator.and, ValueType.i32, codePos );
                        break;
                    case 147: // i2s
                        instr = new WasmConvertInstruction( ValueTypeConvertion.i2s, codePos );
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
                        opIfCondition( NumericOperator.eq, byteCode, codePos );
                        break;
                    case 154: // ifne
                        opIfCondition( NumericOperator.ne, byteCode, codePos );
                        break;
                    case 155: // iflt
                        opIfCondition( NumericOperator.lt, byteCode, codePos );
                        break;
                    case 156: // ifge
                        opIfCondition( NumericOperator.ge, byteCode, codePos );
                        break;
                    case 157: // ifgt
                        opIfCondition( NumericOperator.gt, byteCode, codePos );
                        break;
                    case 158: // ifle
                        opIfCondition( NumericOperator.le, byteCode, codePos );
                        break;
                    case 159: // if_icmpeq
                        opIfCompareCondition( NumericOperator.eq, byteCode, codePos );
                        break;
                    case 160: // if_icmpne
                        opIfCompareCondition( NumericOperator.ne, byteCode, codePos );
                        break;
                    case 161: // if_icmplt
                        opIfCompareCondition( NumericOperator.lt, byteCode, codePos );
                        break;
                    case 162: // if_icmpge
                        opIfCompareCondition( NumericOperator.ge, byteCode, codePos );
                        break;
                    case 163: // if_icmpgt
                        opIfCompareCondition( NumericOperator.gt, byteCode, codePos );
                        break;
                    case 164: // if_icmple
                        opIfCompareCondition( NumericOperator.le, byteCode, codePos );
                        break;
                    //TODO case 165: // if_acmpeq
                    //TODO case 166: // if_acmpne
                    case 167: // goto
                        int offset = byteCode.readShort();
                        branchManager.addGotoOperator( codePos, offset, byteCode.getLineNumber() );
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
                    case 176: // areturn
                    case 177: // return void
                        ValueType type = null;
                        switch ( op ) {
                            case 172: // ireturn
                                type = ValueType.i32;
                                break;
                            case 173: // lreturn
                                type = ValueType.i64;
                                break;
                            case 174: // freturn
                                type = ValueType.f32;
                                break;
                            case 175: // dreturn
                                type = ValueType.f64;
                                break;
                            case 176: // areturn
                                type = ValueType.anyref;
                                break;
                        }
                        instr = new WasmBlockInstruction( WasmBlockOperator.RETURN, type, codePos );
                        endWithReturn = true;
                        break;
                    case 178: // getstatic
                        ConstantRef ref = (ConstantRef)constantPool.get( byteCode.readUnsignedShort() );
                        instr = new WasmGlobalInstruction( true, ref, codePos );
                        break;
                    case 179: // putstatic
                        ref = (ConstantRef)constantPool.get( byteCode.readUnsignedShort() );
                        instr = new WasmGlobalInstruction( false, ref, codePos );
                        break;
                    //TODO case 180: // getfield
                    //TODO case 181: // putfield
                    //TODO case 182: // invokevirtual
                    //TODO case 183: // invokespecial
                    case 184: // invokestatic
                        idx = byteCode.readUnsignedShort();
                        ref = (ConstantRef)constantPool.get( idx );
                        instr = new WasmCallInstruction( ref, codePos );
                        break;
                    //TODO case 185: // invokeinterface
                    //TODO case 187: // new
                    //TODO case 188: // newarray
                    //TODO case 189: // anewarray
                    //TODO case 190: // arraylength
                    //TODO case 191: // athrow
                    //TODO case 197: // multianewarray
                    //TODO case 199: // ifnonnull
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
        int switchValuestartPosition = findPreviousPushCodePosition();

        int defaultPosition = startPosition + byteCode.readInt();
        int[] keys;
        int[] positions;
        if( isLookupSwitch ) { // lookupswitch
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
                        instructions.add( new WasmConstInstruction( keys[i], ValueType.i32, codePos ) );
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
                instructions.add( new WasmConstInstruction( low, ValueType.i32, codePos ) );
                instructions.add( new WasmNumericInstruction( NumericOperator.sub, ValueType.i32, codePos ) );
            }
        }
        branchManager.addSwitchOperator( switchValuestartPosition, 0, lineNumber, keys, positions, defaultPosition );
    }

    /**
     * We need one value from the stack inside of a block. We need to find the code position on which the block can
     * start. If this a function call or numeric expression this can be complex to find the right point.
     * 
     * @return the code position
     */
    private int findPreviousPushCodePosition() {
        int valueCount = 0;
        for( int i = instructions.size() - 1; i >= 0; i-- ) {
            WasmInstruction instr = instructions.get( i );
            ValueType valueType = instr.getPushValueType();
            if( valueType != null ) {
                valueCount++;
            }
            valueCount -= instr.getPopCount();
            if( valueCount == 1 ) {
                return instr.getCodePosition();
            }
        }
        throw new WasmException( "Switch start position not found", sourceFile, -1 ); // should never occur
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
        localVariables.use( valueType, idx );
        return new WasmLoadStoreInstruction( load, idx, localVariables, codePos );
    }

    /**
     * Handle the if<condition> of the Java byte code. This Java instruction compare the first stack value with value 0.
     * Important: In the Java IF expression the condition for the jump to the else block is saved. In WebAssembler we
     * need to use condition for the if block. The caller of the method must already negate this
     * @param compareOp
     *            The condition for the continue of a loop.
     * @param byteCode
     *            current byte code stream to read the target offset.
     * @param codePos
     *            the code position/offset in the Java method
     * 
     * @throws IOException
     *             if any I/O errors occur.
     */
    private void opIfCondition( NumericOperator compareOp, CodeInputStream byteCode, int codePos ) throws IOException {
        instructions.add( new WasmConstInstruction( 0, ValueType.i32, codePos ) );
        opIfCompareCondition( compareOp, byteCode, codePos );
    }

    /**
     * Handle the if<condition> of the Java byte code. This Java instruction compare 2 values from stack.
     * Important: In the Java IF expression the condition for the jump to the else block is saved. In WebAssembler we need to use
     * condition for the if block. The caller of the method must already negate this.
     * @param compareOp
     *            The condition for the continue of a loop.
     * @param byteCode
     *            current byte code stream to read the target offset.
     * @param codePos
     *            the code position/offset in the Java method
     * 
     * @throws IOException
     *             if any I/O errors occur.
     */
    private void opIfCompareCondition( NumericOperator compareOp, CodeInputStream byteCode, int codePos ) throws IOException {
        int offset = byteCode.readShort();
        WasmNumericInstruction compare = new WasmNumericInstruction( compareOp, ValueType.i32, codePos );
        branchManager.addIfOperator( codePos, offset, byteCode.getLineNumber(), compare );
        instructions.add( compare );
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
                numOp = NumericOperator.eq;
                break;
            case 154: // ifne
                numOp = NumericOperator.ne;
                break;
            case 155: // iflt
                numOp = NumericOperator.lt;
                break;
            case 156: // ifge
                numOp = NumericOperator.ge;
                break;
            case 157: // ifgt
                numOp = NumericOperator.gt;
                break;
            case 158: // ifle
                numOp = NumericOperator.le;
                break;
            default:
                throw new WasmException( "Unexpected compare sub operation: " + nextOp, null, -1 );
        }
        int offset = byteCode.readShort();
        WasmNumericInstruction compare = new WasmNumericInstruction( numOp, valueType, codePos );
        branchManager.addIfOperator( codePos, offset, byteCode.getLineNumber(), compare );
        instructions.add( compare );
    }

}
