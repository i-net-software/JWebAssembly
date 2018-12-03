/*
 * Copyright 2018 Volker Berlin (i-net software)
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
import java.util.List;

import javax.annotation.Nonnull;

import de.inetsoftware.classparser.Code;
import de.inetsoftware.classparser.CodeInputStream;
import de.inetsoftware.classparser.ConstantPool;
import de.inetsoftware.classparser.ConstantRef;
import de.inetsoftware.jwebassembly.WasmException;
import de.inetsoftware.jwebassembly.wasm.ArrayOperator;
import de.inetsoftware.jwebassembly.wasm.NumericOperator;
import de.inetsoftware.jwebassembly.wasm.ValueType;
import de.inetsoftware.jwebassembly.wasm.WasmBlockOperator;

/**
 * Convert Java Byte Code to a list of WasmInstruction.
 * 
 * @author Volker Berlin
 */
class JavaMethodWasmCodeBuilder extends WasmCodeBuilder {

    private BranchManger                branchManager  = new BranchManger( getInstructions() );

    /**
     * Build the wasm instructions
     * 
     * @param code
     *            the Java method code
     * @param hasReturn
     *            true, if the method has a return value; false, if it is void
     * @throws WasmException
     *             if some Java code can't converted
     */
    void buildCode( @Nonnull Code code, boolean hasReturn ) {
        CodeInputStream byteCode = null;
        try {
            reset();
            branchManager.reset( code.getExceptionTable() );

            byteCode = code.getByteCode();
            writeCode( byteCode, code.getConstantPool(), hasReturn );
            calculateVariables();
        } catch( Exception ioex ) {
            int lineNumber = byteCode == null ? -1 : byteCode.getLineNumber();
            throw WasmException.create( ioex, lineNumber );
        }
    }


    /**
     * Write the byte code of a method.
     * 
     * @param byteCode
     *            a stream of byte code
     * @param constantPool
     *            the constant pool of the the current class
     * @param hasReturn
     *            if the method has a return value
     * @throws WasmException
     *             if some Java code can't converted
     */
    private void writeCode( CodeInputStream byteCode, ConstantPool constantPool, boolean hasReturn  ) throws WasmException {
        boolean endWithReturn = false;
        try {
            while( byteCode.available() > 0 ) {
                int codePos = byteCode.getCodePosition();
                endWithReturn = false;
                int op = byteCode.readUnsignedByte();
                OP: switch( op ) {
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
                        addConstInstruction( Integer.valueOf( op - 3 ), ValueType.i32, codePos );
                        break;
                    case 9:  // lconst_0
                    case 10: // lconst_1
                        addConstInstruction( Long.valueOf( op - 9 ), ValueType.i64, codePos );
                        break;
                    case 11: // fconst_0
                    case 12: // fconst_1
                    case 13: // fconst_2
                        addConstInstruction( Float.valueOf( op - 11 ), ValueType.f32, codePos );
                        break;
                    case 14: // dconst_0
                    case 15: // dconst_1
                        addConstInstruction( Double.valueOf( op - 14 ), ValueType.f64, codePos );
                        break;
                    case 16: // bipush
                        addConstInstruction( Integer.valueOf( byteCode.readByte() ), ValueType.i32, codePos );
                        break;
                    case 17: // sipush
                        addConstInstruction( Integer.valueOf( byteCode.readShort() ), ValueType.i32, codePos );
                        break;
                    case 18: // ldc
                        addConstInstruction( (Number)constantPool.get( byteCode.readUnsignedByte() ), codePos );
                        break;
                    case 19: // ldc_w
                    case 20: // ldc2_w
                        addConstInstruction( (Number)constantPool.get( byteCode.readUnsignedShort() ), codePos );
                        break;
                    case 21: // iload
                        addLoadStoreInstruction( ValueType.i32, true, byteCode.readUnsignedByte(), codePos );
                        break;
                    case 22: // lload
                        addLoadStoreInstruction( ValueType.i64, true, byteCode.readUnsignedByte(), codePos );
                        break;
                    case 23: // fload
                        addLoadStoreInstruction( ValueType.f32, true, byteCode.readUnsignedByte(), codePos );
                        break;
                    case 24: // dload
                        addLoadStoreInstruction( ValueType.f64, true, byteCode.readUnsignedByte(), codePos );
                        break;
                    case 25: // aload
                        addLoadStoreInstruction( ValueType.anyref, true, byteCode.readUnsignedByte(), codePos );
                        break;
                    case 26: // iload_0
                    case 27: // iload_1
                    case 28: // iload_2
                    case 29: // iload_3
                        addLoadStoreInstruction( ValueType.i32, true, op - 26, codePos );
                        break;
                    case 30: // lload_0
                    case 31: // lload_1
                    case 32: // lload_2
                    case 33: // lload_3
                        addLoadStoreInstruction( ValueType.i64, true, op - 30, codePos );
                        break;
                    case 34: // fload_0
                    case 35: // fload_1
                    case 36: // fload_2
                    case 37: // fload_3
                        addLoadStoreInstruction( ValueType.f32, true, op - 34, codePos );
                        break;
                    case 38: // dload_0
                    case 39: // dload_1
                    case 40: // dload_2
                    case 41: // dload_3
                        addLoadStoreInstruction( ValueType.f64, true, op - 38, codePos );
                        break;
                    case 42: //aload_0
                    case 43: //aload_1
                    case 44: //aload_2
                    case 45: //aload_3
                        addLoadStoreInstruction( ValueType.anyref, true, op - 42, codePos );
                        break;
                    case 46: // iaload
                        addArrayInstruction( ArrayOperator.GET, ValueType.i32, codePos );
                        break;
                    //TODO case 47: // laload
                    //TODO case 48: // faload
                    //TODO case 49: // daload
                    //TODO case 50: // aaload
                    //TODO case 51: // baload
                    //TODO case 52: // caload
                    //TODO case 53: // saload
                    case 54: // istore
                        addLoadStoreInstruction( ValueType.i32, false, byteCode.readUnsignedByte(), codePos );
                        break;
                    case 55: // lstore
                        addLoadStoreInstruction( ValueType.i64, false, byteCode.readUnsignedByte(), codePos );
                        break;
                    case 56: // fstore
                        addLoadStoreInstruction( ValueType.f32, false, byteCode.readUnsignedByte(), codePos );
                        break;
                    case 57: // dstore
                        addLoadStoreInstruction( ValueType.f64, false, byteCode.readUnsignedByte(), codePos );
                        break;
                    case 58: // astore
                        addLoadStoreInstruction( ValueType.anyref, false, byteCode.readUnsignedByte(), codePos );
                        break;
                    case 59: // istore_0
                    case 60: // istore_1
                    case 61: // istore_2
                    case 62: // istore_3
                        addLoadStoreInstruction( ValueType.i32, false, op - 59, codePos );
                        break;
                    case 63: // lstore_0
                    case 64: // lstore_1
                    case 65: // lstore_2
                    case 66: // lstore_3
                        addLoadStoreInstruction( ValueType.i64, false, op - 63, codePos );
                        break;
                    case 67: // fstore_0
                    case 68: // fstore_1
                    case 69: // fstore_2
                    case 70: // fstore_3
                        addLoadStoreInstruction( ValueType.f32, false, op - 67, codePos );
                        break;
                    case 71: // dstore_0
                    case 72: // dstore_1
                    case 73: // dstore_2
                    case 74: // dstore_3
                        addLoadStoreInstruction( ValueType.f64, false, op - 71, codePos );
                        break;
                    case 75: // astore_0
                    case 76: // astore_1
                    case 77: // astore_2
                    case 78: // astore_3
                        addLoadStoreInstruction( ValueType.anyref, false, op - 75, codePos );
                        break;
                    case 79: // iastore
                        addArrayInstruction( ArrayOperator.SET, ValueType.i32, codePos );
                        break;
                        //TODO case 80: // lastore
                        //TODO case 81: // fastore
                        //TODO case 82: // dastore
                        //TODO case 83: // aastore
                        //TODO case 84: // bastore
                        //TODO case 85: // castore
                        //TODO case 86: // sastore
                    case 87: // pop
                    case 88: // pop2
                        addBlockInstruction( WasmBlockOperator.DROP, null, codePos );
                        break;
                    case 89: // dup: duplicate the value on top of the stack
                    case 92: // dup2
                        switch( findPreviousPushInstruction().getPushValueType() ) {
                            case i32:
                                addCallInstruction( new SyntheticMember( "de/inetsoftware/jwebassembly/module/NativeHelperCode", "dup_i32", "(I)II" ), codePos );
                                break OP;
                            case f32:
                                addCallInstruction( new SyntheticMember( "de/inetsoftware/jwebassembly/module/NativeHelperCode", "dup_f32", "(F)FF" ), codePos );
                                break OP;
                            case i64:
                                addCallInstruction( new SyntheticMember( "de/inetsoftware/jwebassembly/module/NativeHelperCode", "dup_i64", "(J)JJ" ), codePos );
                                break OP;
                            case f64:
                                addCallInstruction( new SyntheticMember( "de/inetsoftware/jwebassembly/module/NativeHelperCode", "dup_f64", "(D)DD" ), codePos );
                                break OP;
                            case anyref:
                                addCallInstruction( new SyntheticMember( "de/inetsoftware/jwebassembly/module/NativeHelperCode", "dup_anyref", "(Ljava.lang.Object;)Ljava.lang.Object;Ljava.lang.Object;" ), codePos );
                                break OP;
                        }
                        //$FALL-THROUGH$
                    case 90: // dup_x1
                    case 91: // dup_x2
                    case 93: // dup2_x1
                    case 94: // dup2_x2
                    case 95: // swap
                        // can be do with functions with more as one return value in future WASM standard
                        throw new WasmException( "Stack duplicate is not supported in current WASM. try to save immediate values in a local variable: " + op, byteCode.getLineNumber() );
                    case 96: // iadd
                        addNumericInstruction( NumericOperator.add, ValueType.i32, codePos);
                        break;
                    case 97: // ladd
                        addNumericInstruction( NumericOperator.add, ValueType.i64, codePos );
                        break;
                    case 98: // fadd
                        addNumericInstruction( NumericOperator.add, ValueType.f32, codePos );
                        break;
                    case 99: // dadd
                        addNumericInstruction( NumericOperator.add, ValueType.f64, codePos );
                        break;
                    case 100: // isub
                        addNumericInstruction( NumericOperator.sub, ValueType.i32, codePos );
                        break;
                    case 101: // lsub
                        addNumericInstruction( NumericOperator.sub, ValueType.i64, codePos );
                        break;
                    case 102: // fsub
                        addNumericInstruction( NumericOperator.sub, ValueType.f32, codePos );
                        break;
                    case 103: // dsub
                        addNumericInstruction( NumericOperator.sub, ValueType.f64, codePos );
                        break;
                    case 104: // imul;
                        addNumericInstruction( NumericOperator.mul, ValueType.i32, codePos );
                        break;
                    case 105: // lmul
                        addNumericInstruction( NumericOperator.mul, ValueType.i64, codePos );
                        break;
                    case 106: // fmul
                        addNumericInstruction( NumericOperator.mul, ValueType.f32, codePos );
                        break;
                    case 107: // dmul
                        addNumericInstruction( NumericOperator.mul, ValueType.f64, codePos );
                        break;
                    case 108: // idiv
                        addNumericInstruction( NumericOperator.div, ValueType.i32, codePos );
                        break;
                    case 109: // ldiv
                        addNumericInstruction( NumericOperator.div, ValueType.i64, codePos );
                        break;
                    case 110: // fdiv
                        addNumericInstruction( NumericOperator.div, ValueType.f32, codePos );
                        break;
                    case 111: // ddiv
                        addNumericInstruction( NumericOperator.div, ValueType.f64, codePos );
                        break;
                    case 112: // irem
                        addNumericInstruction( NumericOperator.rem, ValueType.i32, codePos );
                        break;
                    case 113: // lrem
                        addNumericInstruction( NumericOperator.rem, ValueType.i64, codePos );
                        break;
                    case 114: // frem
                    case 115: // drem
                        //TODO can be implemented with a helper function like: (a - (long)(a / b) * (double)b) 
                        throw new WasmException( "Modulo/Remainder for floating numbers is not supported in WASM. Use int or long data types." + op, byteCode.getLineNumber() );
                    case 116: // ineg
                        addConstInstruction( -1, ValueType.i32, codePos );
                        addNumericInstruction( NumericOperator.mul, ValueType.i32, codePos );
                        break;
                    case 117: // lneg
                        addConstInstruction( (long)-1, ValueType.i64, codePos );
                        addNumericInstruction( NumericOperator.mul, ValueType.i64, codePos );
                        break;
                    case 118: // fneg
                        addNumericInstruction( NumericOperator.neg, ValueType.f32, codePos );
                        break;
                    case 119: // dneg
                        addNumericInstruction( NumericOperator.neg, ValueType.f64, codePos );
                        break;
                    case 120: // ishl
                        addNumericInstruction( NumericOperator.shl, ValueType.i32, codePos );
                        break;
                    case 121: // lshl
                        addConvertInstruction( ValueTypeConvertion.i2l, codePos ); // the shift parameter must be of type long!!!
                        addNumericInstruction( NumericOperator.shl, ValueType.i64, codePos );
                        break;
                    case 122: // ishr
                        addNumericInstruction( NumericOperator.shr_s, ValueType.i32, codePos );
                        break;
                    case 123: // lshr
                        addConvertInstruction( ValueTypeConvertion.i2l, codePos ); // the shift parameter must be of type long!!!
                        addNumericInstruction( NumericOperator.shr_s, ValueType.i64, codePos );
                        break;
                    case 124: // iushr
                        addNumericInstruction( NumericOperator.shr_u, ValueType.i32, codePos );
                        break;
                    case 125: // lushr
                        addConvertInstruction( ValueTypeConvertion.i2l, codePos ); // the shift parameter must be of type long!!!
                        addNumericInstruction( NumericOperator.shr_u, ValueType.i64, codePos );
                        break;
                    case 126: // iand
                        addNumericInstruction( NumericOperator.and, ValueType.i32, codePos );
                        break;
                    case 127: // land
                        addNumericInstruction( NumericOperator.and, ValueType.i64, codePos );
                        break;
                    case 128: // ior
                        addNumericInstruction( NumericOperator.or, ValueType.i32, codePos );
                        break;
                    case 129: // lor
                        addNumericInstruction( NumericOperator.or, ValueType.i64, codePos );
                        break;
                    case 130: // ixor
                        addNumericInstruction( NumericOperator.xor, ValueType.i32, codePos );
                        break;
                    case 131: // lxor
                        addNumericInstruction( NumericOperator.xor, ValueType.i64, codePos );
                        break;
                    case 132: // iinc
                        int idx = byteCode.readUnsignedByte();
                        addLoadStoreInstruction( ValueType.i32, true, idx, codePos );
                        addConstInstruction( (int)byteCode.readByte(), ValueType.i32, codePos );
                        addNumericInstruction( NumericOperator.add, ValueType.i32, codePos);
                        addLoadStoreInstruction( ValueType.i32, false, idx, codePos );
                        break;
                    case 133: // i2l
                        addConvertInstruction( ValueTypeConvertion.i2l, codePos );
                        break;
                    case 134: // i2f
                        addConvertInstruction( ValueTypeConvertion.i2f, codePos );
                        break;
                    case 135: // i2d
                        addConvertInstruction( ValueTypeConvertion.i2d, codePos );
                        break;
                    case 136: // l2i
                        addConvertInstruction( ValueTypeConvertion.l2i, codePos );
                        break;
                    case 137: // l2f
                        addConvertInstruction( ValueTypeConvertion.l2f, codePos );
                        break;
                    case 138: // l2d
                        addConvertInstruction( ValueTypeConvertion.l2d, codePos );
                        break;
                    case 139: // f2i
                        addConvertInstruction( ValueTypeConvertion.f2i, codePos );
                        break;
                    case 140: // f2l
                        addConvertInstruction( ValueTypeConvertion.f2l, codePos );
                        break;
                    case 141: // f2d
                        addConvertInstruction( ValueTypeConvertion.f2d, codePos );
                        break;
                    case 142: // d2i
                        addConvertInstruction( ValueTypeConvertion.d2i, codePos );
                        break;
                    case 143: // d2l
                        addConvertInstruction( ValueTypeConvertion.d2l, codePos );
                        break;
                    case 144: // d2f
                        addConvertInstruction( ValueTypeConvertion.d2f, codePos );
                        break;
                    case 145: // i2b
                        addConvertInstruction( ValueTypeConvertion.i2b, codePos );
                        break;
                    case 146: // i2c
                        addConstInstruction( 0xFFFF, ValueType.i32, codePos );
                        addNumericInstruction( NumericOperator.and, ValueType.i32, codePos );
                        break;
                    case 147: // i2s
                        addConvertInstruction( ValueTypeConvertion.i2s, codePos );
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
                        addNopInstruction( codePos ); // marker of the line number for the branch manager
                        break;
                    //TODO case 168: // jsr
                    //TODO case 169: // ret
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
                        addBlockInstruction( WasmBlockOperator.RETURN, type, codePos );
                        endWithReturn = true;
                        break;
                    case 178: // getstatic
                        ConstantRef ref = (ConstantRef)constantPool.get( byteCode.readUnsignedShort() );
                        addGlobalInstruction( true, ref, codePos );
                        break;
                    case 179: // putstatic
                        ref = (ConstantRef)constantPool.get( byteCode.readUnsignedShort() );
                        addGlobalInstruction( false, ref, codePos );
                        break;
                    //TODO case 180: // getfield
                    //TODO case 181: // putfield
                    //TODO case 182: // invokevirtual
                    //TODO case 183: // invokespecial
                    case 184: // invokestatic
                        idx = byteCode.readUnsignedShort();
                        ref = (ConstantRef)constantPool.get( idx );
                        addCallInstruction( ref, codePos );
                        break;
                    //TODO case 185: // invokeinterface
                    //TODO case 186: // invokedynamic
                    //TODO case 187: // new
                    case 188: // newarray
                        int typeValue = byteCode.readByte();
                        switch( typeValue ) {
                            case 4: // boolean 
                            case 5: // char 
                                type = ValueType.i32; 
                                break;
                            case 6: //float
                                type = ValueType.f32;
                                break;
                            case 7: //double 
                                type = ValueType.f64;
                                break;
                            case 8: //byte 
                            case 9: //short 
                            case 10: //int 
                                type = ValueType.i32;
                                break;
                            case 11: //long 
                                type = ValueType.i64;
                                break;
                            default:
                                throw new WasmException( "Invalid Java byte code newarray: " + typeValue, byteCode.getLineNumber() );
                        }
                        addArrayInstruction( ArrayOperator.NEW, type, codePos );
                        break;
                    //TODO case 189: // anewarray
                    case 190: // arraylength
                        addArrayInstruction( ArrayOperator.LENGTH, ValueType.i32, codePos );
                        break;
                    //TODO case 191: // athrow
                    //TODO case 192: // checkcast
                    //TODO case 193: // instanceof
                    //TODO case 194: // monitorenter
                    //TODO case 195: // monitorexit
                    //TODO case 196: // wide
                    //TODO case 197: // multianewarray
                    //TODO case 198: // ifnull
                    //TODO case 199: // ifnonnull
                    //TODO case 200: // goto_w
                    //TODO case 201: // jsr_w
                    default:
                        throw new WasmException( "Unimplemented Java byte code operation: " + op, byteCode.getLineNumber() );
                }
            }
            branchManager.calculate();
            branchManager.handle( byteCode ); // add branch operations
            if( !endWithReturn && hasReturn ) {
                // if a method ends with a loop without a break then code after the loop is no reachable
                // Java does not need a return byte code in this case
                // But WebAssembly need the dead code to validate
                addBlockInstruction( WasmBlockOperator.UNREACHABLE, null, byteCode.getCodePosition() );
            }
        } catch( WasmException ex ) {
            throw ex;
        } catch( Exception ex ) {
            throw WasmException.create( ex, byteCode.getLineNumber() );
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
        int switchValuestartPosition = findPreviousPushInstruction().getCodePosition();

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
            int tempI32 = -1;
            int block = 0;
            int defaultBlock = -1;
            int currentPos = -1;
            addLoadStoreInstruction( ValueType.i32, false, tempI32, codePos );
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
                        addLoadStoreInstruction( ValueType.i32, true, tempI32, codePos );
                        addConstInstruction( keys[i], ValueType.i32, codePos );
                        addNumericInstruction( NumericOperator.eq, ValueType.i32, codePos );
                        addBlockInstruction( WasmBlockOperator.BR_IF, block, codePos );
                    }
                }
                block++;
            } while( true );
            if( defaultBlock < 0 ) {
                defaultBlock = block;
            }
            addBlockInstruction( WasmBlockOperator.BR, defaultBlock, codePos );
        } else {
            int low = byteCode.readInt();
            keys = null;
            int count = byteCode.readInt() - low + 1;
            positions = new int[count];
            for( int i = 0; i < count; i++ ) {
                positions[i] = startPosition + byteCode.readInt();
            }
            if( low != 0 ) { // the br_table starts ever with the value 0. That we need to subtract the start value if it different
                addConstInstruction( low, ValueType.i32, codePos );
                addNumericInstruction( NumericOperator.sub, ValueType.i32, codePos );
            }
        }
        branchManager.addSwitchOperator( switchValuestartPosition, 0, lineNumber, keys, positions, defaultPosition );
    }

    /**
     * We need one value from the stack inside of a block. We need to find the WasmInstruction on which the block can
     * start. If this a function call or numeric expression this can be complex to find the right point.
     * 
     * @return the WasmInstruction that push the last instruction
     */
    @Nonnull
    private WasmInstruction findPreviousPushInstruction() {
        int valueCount = 0;
        List<WasmInstruction> instructions = getInstructions();
        for( int i = instructions.size() - 1; i >= 0; i-- ) {
            WasmInstruction instr = instructions.get( i );
            ValueType valueType = instr.getPushValueType();
            if( valueType != null ) {
                valueCount++;
            }
            valueCount -= instr.getPopCount();
            if( valueCount == 1 ) {
                return instr;
            }
        }
        throw new WasmException( "Start position not found", -1 ); // should never occur
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
        addConstInstruction( 0, ValueType.i32, codePos );
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
        getInstructions().add( compare );
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
                throw new WasmException( "Unexpected compare sub operation: " + nextOp, -1 );
        }
        int offset = byteCode.readShort();
        WasmNumericInstruction compare = new WasmNumericInstruction( numOp, valueType, codePos );
        branchManager.addIfOperator( codePos, offset, byteCode.getLineNumber(), compare );
        getInstructions().add( compare );
    }

}
