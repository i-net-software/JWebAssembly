/*
 * Copyright 2018 - 2019 Volker Berlin (i-net software)
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
import de.inetsoftware.classparser.ConstantClass;
import de.inetsoftware.classparser.ConstantPool;
import de.inetsoftware.classparser.ConstantRef;
import de.inetsoftware.jwebassembly.WasmException;
import de.inetsoftware.jwebassembly.module.TypeManager.StructType;
import de.inetsoftware.jwebassembly.wasm.AnyType;
import de.inetsoftware.jwebassembly.wasm.ArrayOperator;
import de.inetsoftware.jwebassembly.wasm.NamedStorageType;
import de.inetsoftware.jwebassembly.wasm.NumericOperator;
import de.inetsoftware.jwebassembly.wasm.StructOperator;
import de.inetsoftware.jwebassembly.wasm.ValueType;
import de.inetsoftware.jwebassembly.wasm.WasmBlockOperator;

/**
 * Convert Java Byte Code to a list of WasmInstruction.
 * 
 * @author Volker Berlin
 */
class JavaMethodWasmCodeBuilder extends WasmCodeBuilder {

    private BranchManger branchManager = new BranchManger( getInstructions() );

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
            reset( code.getLocalVariableTable() );
            branchManager.reset( code );

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
    private void writeCode( CodeInputStream byteCode, ConstantPool constantPool, boolean hasReturn ) throws WasmException {
        int lineNumber = -1;
        try {
            boolean wide = false;
            while( byteCode.available() > 0 ) {
                int codePos = byteCode.getCodePosition();
                lineNumber = byteCode.getLineNumber();
                int op = byteCode.readUnsignedByte();
                switch( op ) {
                    case 0: // nop
                        break;
                    case 1: // aconst_null
                        addStructInstruction( StructOperator.NULL, null, null, codePos, lineNumber );
                        break;
                    case 2: // iconst_m1
                    case 3: // iconst_0
                    case 4: // iconst_1
                    case 5: // iconst_2
                    case 6: // iconst_3
                    case 7: // iconst_4
                    case 8: // iconst_5
                        addConstInstruction( Integer.valueOf( op - 3 ), ValueType.i32, codePos, lineNumber );
                        break;
                    case 9: // lconst_0
                    case 10: // lconst_1
                        addConstInstruction( Long.valueOf( op - 9 ), ValueType.i64, codePos, lineNumber );
                        break;
                    case 11: // fconst_0
                    case 12: // fconst_1
                    case 13: // fconst_2
                        addConstInstruction( Float.valueOf( op - 11 ), ValueType.f32, codePos, lineNumber );
                        break;
                    case 14: // dconst_0
                    case 15: // dconst_1
                        addConstInstruction( Double.valueOf( op - 14 ), ValueType.f64, codePos, lineNumber );
                        break;
                    case 16: // bipush
                        addConstInstruction( Integer.valueOf( byteCode.readByte() ), ValueType.i32, codePos, lineNumber );
                        break;
                    case 17: // sipush
                        addConstInstruction( Integer.valueOf( byteCode.readShort() ), ValueType.i32, codePos, lineNumber );
                        break;
                    case 18: // ldc
                        addConstInstruction( (Number)constantPool.get( byteCode.readUnsignedByte() ), codePos, lineNumber );
                        break;
                    case 19: // ldc_w
                    case 20: // ldc2_w
                        addConstInstruction( (Number)constantPool.get( byteCode.readUnsignedShort() ), codePos, lineNumber );
                        break;
                    case 21: // iload
                        addLoadStoreInstruction( ValueType.i32, true, byteCode.readUnsignedIndex( wide ), codePos, lineNumber );
                        break;
                    case 22: // lload
                        addLoadStoreInstruction( ValueType.i64, true, byteCode.readUnsignedIndex( wide ), codePos, lineNumber );
                        break;
                    case 23: // fload
                        addLoadStoreInstruction( ValueType.f32, true, byteCode.readUnsignedIndex( wide ), codePos, lineNumber );
                        break;
                    case 24: // dload
                        addLoadStoreInstruction( ValueType.f64, true, byteCode.readUnsignedIndex( wide ), codePos, lineNumber );
                        break;
                    case 25: // aload
                        addLoadStoreInstruction( ValueType.anyref, true, byteCode.readUnsignedIndex( wide ), codePos, lineNumber );
                        break;
                    case 26: // iload_0
                    case 27: // iload_1
                    case 28: // iload_2
                    case 29: // iload_3
                        addLoadStoreInstruction( ValueType.i32, true, op - 26, codePos, lineNumber );
                        break;
                    case 30: // lload_0
                    case 31: // lload_1
                    case 32: // lload_2
                    case 33: // lload_3
                        addLoadStoreInstruction( ValueType.i64, true, op - 30, codePos, lineNumber );
                        break;
                    case 34: // fload_0
                    case 35: // fload_1
                    case 36: // fload_2
                    case 37: // fload_3
                        addLoadStoreInstruction( ValueType.f32, true, op - 34, codePos, lineNumber );
                        break;
                    case 38: // dload_0
                    case 39: // dload_1
                    case 40: // dload_2
                    case 41: // dload_3
                        addLoadStoreInstruction( ValueType.f64, true, op - 38, codePos, lineNumber );
                        break;
                    case 42: //aload_0
                    case 43: //aload_1
                    case 44: //aload_2
                    case 45: //aload_3
                        addLoadStoreInstruction( ValueType.anyref, true, op - 42, codePos, lineNumber );
                        break;
                    case 46: // iaload
                        addArrayInstruction( ArrayOperator.GET, ValueType.i32, codePos, lineNumber );
                        break;
                    case 47: // laload
                        addArrayInstruction( ArrayOperator.GET, ValueType.i64, codePos, lineNumber );
                        break;
                    case 48: // faload
                        addArrayInstruction( ArrayOperator.GET, ValueType.f32, codePos, lineNumber );
                        break;
                    case 49: // daload
                        addArrayInstruction( ArrayOperator.GET, ValueType.f64, codePos, lineNumber );
                        break;
                    case 50: // aaload
                        AnyType storeType = findValueTypeFromStack( 2 );
                        addArrayInstruction( ArrayOperator.GET, storeType, codePos, lineNumber );
                        break;
                    case 51: // baload
                        addArrayInstruction( ArrayOperator.GET, ValueType.i8, codePos, lineNumber );
                        break;
                    case 52: // caload
                        addArrayInstruction( ArrayOperator.GET, ValueType.i16, codePos, lineNumber );
                        break;
                    case 53: // saload
                        addArrayInstruction( ArrayOperator.GET, ValueType.i16, codePos, lineNumber );
                        break;
                    case 54: // istore
                        addLoadStoreInstruction( ValueType.i32, false, byteCode.readUnsignedIndex( wide ), codePos, lineNumber );
                        break;
                    case 55: // lstore
                        addLoadStoreInstruction( ValueType.i64, false, byteCode.readUnsignedIndex( wide ), codePos, lineNumber );
                        break;
                    case 56: // fstore
                        addLoadStoreInstruction( ValueType.f32, false, byteCode.readUnsignedIndex( wide ), codePos, lineNumber );
                        break;
                    case 57: // dstore
                        addLoadStoreInstruction( ValueType.f64, false, byteCode.readUnsignedIndex( wide ), codePos, lineNumber );
                        break;
                    case 58: // astore
                        addLoadStoreInstruction( ValueType.anyref, false, byteCode.readUnsignedIndex( wide ), codePos, lineNumber );
                        break;
                    case 59: // istore_0
                    case 60: // istore_1
                    case 61: // istore_2
                    case 62: // istore_3
                        addLoadStoreInstruction( ValueType.i32, false, op - 59, codePos, lineNumber );
                        break;
                    case 63: // lstore_0
                    case 64: // lstore_1
                    case 65: // lstore_2
                    case 66: // lstore_3
                        addLoadStoreInstruction( ValueType.i64, false, op - 63, codePos, lineNumber );
                        break;
                    case 67: // fstore_0
                    case 68: // fstore_1
                    case 69: // fstore_2
                    case 70: // fstore_3
                        addLoadStoreInstruction( ValueType.f32, false, op - 67, codePos, lineNumber );
                        break;
                    case 71: // dstore_0
                    case 72: // dstore_1
                    case 73: // dstore_2
                    case 74: // dstore_3
                        addLoadStoreInstruction( ValueType.f64, false, op - 71, codePos, lineNumber );
                        break;
                    case 75: // astore_0
                    case 76: // astore_1
                    case 77: // astore_2
                    case 78: // astore_3
                        if( branchManager.isCatch( codePos ) ) {
                            storeType = ValueType.anyref; // for the catch there are no previous instructions
                        } else {
                            storeType = findValueTypeFromStack( 1 );
                        }
                        addLoadStoreInstruction( storeType, false, op - 75, codePos, lineNumber );
                        break;
                    case 79: // iastore
                        addArrayInstruction( ArrayOperator.SET, ValueType.i32, codePos, lineNumber );
                        break;
                    case 80: // lastore
                        addArrayInstruction( ArrayOperator.SET, ValueType.i64, codePos, lineNumber );
                        break;
                    case 81: // fastore
                        addArrayInstruction( ArrayOperator.SET, ValueType.f32, codePos, lineNumber );
                        break;
                    case 82: // dastore
                        addArrayInstruction( ArrayOperator.SET, ValueType.f64, codePos, lineNumber );
                        break;
                    case 83: // aastore
                        storeType = findValueTypeFromStack( 1 );
                        addArrayInstruction( ArrayOperator.SET, storeType, codePos, lineNumber );
                        break;
                    case 84: // bastore
                        addArrayInstruction( ArrayOperator.SET, ValueType.i8, codePos, lineNumber );
                        break;
                    case 85: // castore
                        addArrayInstruction( ArrayOperator.SET, ValueType.i16, codePos, lineNumber );
                        break;
                    case 86: // sastore
                        addArrayInstruction( ArrayOperator.SET, ValueType.i16, codePos, lineNumber );
                        break;
                    case 87: // pop
                    case 88: // pop2
                        addBlockInstruction( WasmBlockOperator.DROP, null, codePos, lineNumber );
                        break;
                    case 89: // dup: duplicate the value on top of the stack
                    case 92: // dup2
                        // save it in a temporary variable and load it 2 times; optimize will change it to TEE
                        storeType = findValueTypeFromStack( 1 );
                        int idx = getTempVariable( storeType, codePos, codePos + 1 );
                        addLoadStoreInstruction( storeType, false, idx, codePos, lineNumber );
                        addLoadStoreInstruction( storeType, true, idx, codePos, lineNumber );
                        addLoadStoreInstruction( storeType, true, idx, codePos, lineNumber );
//                        addCallInstruction( new SyntheticFunctionName( "dup"
//                                        + storeType, "local.get 0 local.get 0 return", storeType, null, storeType, storeType ), codePos, lineNumber );
                        break;
                    case 90: // dup_x1
                    case 91: // dup_x2
                    case 93: // dup2_x1
                    case 94: // dup2_x2
                    case 95: // swap
                        // can be do with functions with more as one return value in future WASM standard
                        throw new WasmException( "Stack duplicate is not supported in current WASM. try to save immediate values in a local variable: "
                                        + op, lineNumber );
                    case 96: // iadd
                        addNumericInstruction( NumericOperator.add, ValueType.i32, codePos, lineNumber );
                        break;
                    case 97: // ladd
                        addNumericInstruction( NumericOperator.add, ValueType.i64, codePos, lineNumber );
                        break;
                    case 98: // fadd
                        addNumericInstruction( NumericOperator.add, ValueType.f32, codePos, lineNumber );
                        break;
                    case 99: // dadd
                        addNumericInstruction( NumericOperator.add, ValueType.f64, codePos, lineNumber );
                        break;
                    case 100: // isub
                        addNumericInstruction( NumericOperator.sub, ValueType.i32, codePos, lineNumber );
                        break;
                    case 101: // lsub
                        addNumericInstruction( NumericOperator.sub, ValueType.i64, codePos, lineNumber );
                        break;
                    case 102: // fsub
                        addNumericInstruction( NumericOperator.sub, ValueType.f32, codePos, lineNumber );
                        break;
                    case 103: // dsub
                        addNumericInstruction( NumericOperator.sub, ValueType.f64, codePos, lineNumber );
                        break;
                    case 104: // imul;
                        addNumericInstruction( NumericOperator.mul, ValueType.i32, codePos, lineNumber );
                        break;
                    case 105: // lmul
                        addNumericInstruction( NumericOperator.mul, ValueType.i64, codePos, lineNumber );
                        break;
                    case 106: // fmul
                        addNumericInstruction( NumericOperator.mul, ValueType.f32, codePos, lineNumber );
                        break;
                    case 107: // dmul
                        addNumericInstruction( NumericOperator.mul, ValueType.f64, codePos, lineNumber );
                        break;
                    case 108: // idiv
                        addNumericInstruction( NumericOperator.div, ValueType.i32, codePos, lineNumber );
                        break;
                    case 109: // ldiv
                        addNumericInstruction( NumericOperator.div, ValueType.i64, codePos, lineNumber );
                        break;
                    case 110: // fdiv
                        addNumericInstruction( NumericOperator.div, ValueType.f32, codePos, lineNumber );
                        break;
                    case 111: // ddiv
                        addNumericInstruction( NumericOperator.div, ValueType.f64, codePos, lineNumber );
                        break;
                    case 112: // irem
                        addNumericInstruction( NumericOperator.rem, ValueType.i32, codePos, lineNumber );
                        break;
                    case 113: // lrem
                        addNumericInstruction( NumericOperator.rem, ValueType.i64, codePos, lineNumber );
                        break;
                    case 114: // frem
                        //helper function like: (a - (int)(a / b) * (float)b) 
                        addCallInstruction( new WatCodeSyntheticFunctionName( "frem", "local.get 0 local.get 0 local.get 1 f32.div i32.trunc_sat_f32_s f32.convert_i32_s local.get 1 f32.mul f32.sub return", ValueType.f32, ValueType.f32, null, ValueType.f32 ), codePos, lineNumber );
                        break;
                    case 115: // drem
                        //helper function like: (a - (long)(a / b) * (double)b) 
                        addCallInstruction( new WatCodeSyntheticFunctionName( "drem", "local.get 0 local.get 0 local.get 1 f64.div i64.trunc_sat_f64_s f64.convert_i64_s local.get 1 f64.mul f64.sub return", ValueType.f64, ValueType.f64, null, ValueType.f64 ), codePos, lineNumber );
                        break;
                    case 116: // ineg
                        addConstInstruction( -1, ValueType.i32, codePos, lineNumber );
                        addNumericInstruction( NumericOperator.mul, ValueType.i32, codePos, lineNumber );
                        break;
                    case 117: // lneg
                        addConstInstruction( (long)-1, ValueType.i64, codePos, lineNumber );
                        addNumericInstruction( NumericOperator.mul, ValueType.i64, codePos, lineNumber );
                        break;
                    case 118: // fneg
                        addNumericInstruction( NumericOperator.neg, ValueType.f32, codePos, lineNumber );
                        break;
                    case 119: // dneg
                        addNumericInstruction( NumericOperator.neg, ValueType.f64, codePos, lineNumber );
                        break;
                    case 120: // ishl
                        addNumericInstruction( NumericOperator.shl, ValueType.i32, codePos, lineNumber );
                        break;
                    case 121: // lshl
                        addConvertInstruction( ValueTypeConvertion.i2l, codePos, lineNumber ); // the shift parameter must be of type long!!!
                        addNumericInstruction( NumericOperator.shl, ValueType.i64, codePos, lineNumber );
                        break;
                    case 122: // ishr
                        addNumericInstruction( NumericOperator.shr_s, ValueType.i32, codePos, lineNumber );
                        break;
                    case 123: // lshr
                        addConvertInstruction( ValueTypeConvertion.i2l, codePos, lineNumber ); // the shift parameter must be of type long!!!
                        addNumericInstruction( NumericOperator.shr_s, ValueType.i64, codePos, lineNumber );
                        break;
                    case 124: // iushr
                        addNumericInstruction( NumericOperator.shr_u, ValueType.i32, codePos, lineNumber );
                        break;
                    case 125: // lushr
                        addConvertInstruction( ValueTypeConvertion.i2l, codePos, lineNumber ); // the shift parameter must be of type long!!!
                        addNumericInstruction( NumericOperator.shr_u, ValueType.i64, codePos, lineNumber );
                        break;
                    case 126: // iand
                        addNumericInstruction( NumericOperator.and, ValueType.i32, codePos, lineNumber );
                        break;
                    case 127: // land
                        addNumericInstruction( NumericOperator.and, ValueType.i64, codePos, lineNumber );
                        break;
                    case 128: // ior
                        addNumericInstruction( NumericOperator.or, ValueType.i32, codePos, lineNumber );
                        break;
                    case 129: // lor
                        addNumericInstruction( NumericOperator.or, ValueType.i64, codePos, lineNumber );
                        break;
                    case 130: // ixor
                        addNumericInstruction( NumericOperator.xor, ValueType.i32, codePos, lineNumber );
                        break;
                    case 131: // lxor
                        addNumericInstruction( NumericOperator.xor, ValueType.i64, codePos, lineNumber );
                        break;
                    case 132: // iinc
                        idx = byteCode.readUnsignedIndex( wide );
                        addLoadStoreInstruction( ValueType.i32, true, idx, codePos, lineNumber );
                        addConstInstruction( (int)(wide ? byteCode.readShort() : byteCode.readByte()), ValueType.i32, codePos, lineNumber );
                        addNumericInstruction( NumericOperator.add, ValueType.i32, codePos, lineNumber );
                        addLoadStoreInstruction( ValueType.i32, false, idx, codePos, lineNumber );
                        break;
                    case 133: // i2l
                        addConvertInstruction( ValueTypeConvertion.i2l, codePos, lineNumber );
                        break;
                    case 134: // i2f
                        addConvertInstruction( ValueTypeConvertion.i2f, codePos, lineNumber );
                        break;
                    case 135: // i2d
                        addConvertInstruction( ValueTypeConvertion.i2d, codePos, lineNumber );
                        break;
                    case 136: // l2i
                        addConvertInstruction( ValueTypeConvertion.l2i, codePos, lineNumber );
                        break;
                    case 137: // l2f
                        addConvertInstruction( ValueTypeConvertion.l2f, codePos, lineNumber );
                        break;
                    case 138: // l2d
                        addConvertInstruction( ValueTypeConvertion.l2d, codePos, lineNumber );
                        break;
                    case 139: // f2i
                        addConvertInstruction( ValueTypeConvertion.f2i, codePos, lineNumber );
                        break;
                    case 140: // f2l
                        addConvertInstruction( ValueTypeConvertion.f2l, codePos, lineNumber );
                        break;
                    case 141: // f2d
                        addConvertInstruction( ValueTypeConvertion.f2d, codePos, lineNumber );
                        break;
                    case 142: // d2i
                        addConvertInstruction( ValueTypeConvertion.d2i, codePos, lineNumber );
                        break;
                    case 143: // d2l
                        addConvertInstruction( ValueTypeConvertion.d2l, codePos, lineNumber );
                        break;
                    case 144: // d2f
                        addConvertInstruction( ValueTypeConvertion.d2f, codePos, lineNumber );
                        break;
                    case 145: // i2b
                        addConvertInstruction( ValueTypeConvertion.i2b, codePos, lineNumber );
                        break;
                    case 146: // i2c
                        addConstInstruction( 0xFFFF, ValueType.i32, codePos, lineNumber );
                        addNumericInstruction( NumericOperator.and, ValueType.i32, codePos, lineNumber );
                        break;
                    case 147: // i2s
                        addConvertInstruction( ValueTypeConvertion.i2s, codePos, lineNumber );
                        break;
                    case 148: // lcmp
                        opCompare( ValueType.i64, byteCode, codePos, lineNumber );
                        break;
                    case 149: // fcmpl
                    case 150: // fcmpg
                        opCompare( ValueType.f32, byteCode, codePos, lineNumber );
                        break;
                    case 151: // dcmpl
                    case 152: // dcmpg
                        opCompare( ValueType.f64, byteCode, codePos, lineNumber );
                        break;
                    case 153: // ifeq
                        opIfCondition( NumericOperator.eq, byteCode, codePos, lineNumber );
                        break;
                    case 154: // ifne
                        opIfCondition( NumericOperator.ne, byteCode, codePos, lineNumber );
                        break;
                    case 155: // iflt
                        opIfCondition( NumericOperator.lt, byteCode, codePos, lineNumber );
                        break;
                    case 156: // ifge
                        opIfCondition( NumericOperator.ge, byteCode, codePos, lineNumber );
                        break;
                    case 157: // ifgt
                        opIfCondition( NumericOperator.gt, byteCode, codePos, lineNumber );
                        break;
                    case 158: // ifle
                        opIfCondition( NumericOperator.le, byteCode, codePos, lineNumber );
                        break;
                    case 159: // if_icmpeq
                        opIfCompareCondition( NumericOperator.eq, byteCode, codePos, lineNumber );
                        break;
                    case 160: // if_icmpne
                        opIfCompareCondition( NumericOperator.ne, byteCode, codePos, lineNumber );
                        break;
                    case 161: // if_icmplt
                        opIfCompareCondition( NumericOperator.lt, byteCode, codePos, lineNumber );
                        break;
                    case 162: // if_icmpge
                        opIfCompareCondition( NumericOperator.ge, byteCode, codePos, lineNumber );
                        break;
                    case 163: // if_icmpgt
                        opIfCompareCondition( NumericOperator.gt, byteCode, codePos, lineNumber );
                        break;
                    case 164: // if_icmple
                        opIfCompareCondition( NumericOperator.le, byteCode, codePos, lineNumber );
                        break;
                    case 165: // if_acmpeq
                        opIfCompareCondition( NumericOperator.ref_eq, byteCode, codePos, lineNumber );
                        break;
                    case 166: // if_acmpne
                        opIfCompareCondition( NumericOperator.ref_ne, byteCode, codePos, lineNumber );
                        break;
                    case 167: // goto
                        int offset = byteCode.readShort();
                        branchManager.addGotoOperator( codePos, offset, byteCode.getCodePosition(), lineNumber );
                        addNopInstruction( codePos, lineNumber ); // marker of the line number for the branch manager
                        break;
                    case 168: // jsr
                    case 169: // ret
                    case 201: // jsr_w
                        throw new WasmException( "Finally block of Java 5 or older is not supported. Compile the sources with a Java SE 6 or newer: "
                                        + op, lineNumber );
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
                        switch( op ) {
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
                        addBlockInstruction( WasmBlockOperator.RETURN, type, codePos, lineNumber );
                        break;
                    case 178: // getstatic
                        ConstantRef ref = (ConstantRef)constantPool.get( byteCode.readUnsignedShort() );
                        addGlobalInstruction( true, ref, codePos, lineNumber );
                        break;
                    case 179: // putstatic
                        ref = (ConstantRef)constantPool.get( byteCode.readUnsignedShort() );
                        addGlobalInstruction( false, ref, codePos, lineNumber );
                        break;
                    case 180: // getfield
                        ref = (ConstantRef)constantPool.get( byteCode.readUnsignedShort() );
                        addStructInstruction( StructOperator.GET, ref.getClassName(), new NamedStorageType( ref, getTypeManager() ), codePos, lineNumber );
                        break;
                    case 181: // putfield
                        ref = (ConstantRef)constantPool.get( byteCode.readUnsignedShort() );
                        addStructInstruction( StructOperator.SET, ref.getClassName(), new NamedStorageType( ref, getTypeManager() ), codePos, lineNumber );
                        break;
                    case 182: // invokevirtual
                    case 183: // invokespecial, invoke a constructor
                    case 184: // invokestatic
                        idx = byteCode.readUnsignedShort();
                        ref = (ConstantRef)constantPool.get( idx );
                        FunctionName funcName = new FunctionName( ref );
                        switch( op ) {
                            case 182:
                                addCallVirtualInstruction( funcName, codePos, lineNumber );
                                break;
                            case 183:
                                getTypeManager().valueOf( funcName.className ); // TODO pass this as first parameter
                                addCallInstruction( funcName, codePos, lineNumber );
                                break;
                            case 184:
                                addCallInstruction( funcName, codePos, lineNumber );
                                break;
                        }
                        break;
                    //TODO case 185: // invokeinterface
                    //TODO case 186: // invokedynamic
                    case 187: // new
                        String name = ((ConstantClass)constantPool.get( byteCode.readUnsignedShort() )).getName();
                        addStructInstruction( StructOperator.NEW_DEFAULT, name, null, codePos, lineNumber );
                        break;
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
                                throw new WasmException( "Invalid Java byte code newarray: " + typeValue, lineNumber );
                        }
                        addArrayInstruction( ArrayOperator.NEW, type, codePos, lineNumber );
                        break;
                    case 189: // anewarray
                        name = ((ConstantClass)constantPool.get( byteCode.readUnsignedShort() )).getName();
                        type = ValueType.anyref; //TODO we need to use the right type from name
                        addArrayInstruction( ArrayOperator.NEW, type, codePos, lineNumber );
                        break;
                    case 190: // arraylength
                        addArrayInstruction( ArrayOperator.LEN, ValueType.i32, codePos, lineNumber );
                        break;
                    case 191: // athrow
                        addBlockInstruction( WasmBlockOperator.THROW, null, codePos, lineNumber );
                        break;
                    //TODO case 192: // checkcast
                    //TODO case 193: // instanceof
                    case 194: // monitorenter
                        addBlockInstruction( WasmBlockOperator.MONITOR_ENTER, null, codePos, lineNumber );
                        break;
                    case 195: // monitorexit
                        addBlockInstruction( WasmBlockOperator.MONITOR_EXIT, null, codePos, lineNumber );
                        break;
                    case 196: // wide
                        // https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-6.html#jvms-6.5.wide
                        wide = true;
                        continue;
                    //TODO case 197: // multianewarray
                    case 198: // ifnull
                        opIfCompareCondition( NumericOperator.ifnull, byteCode, codePos, lineNumber );
                        break;
                    case 199: // ifnonnull
                        opIfCompareCondition( NumericOperator.ifnonnull, byteCode, codePos, lineNumber );
                        break;
                    case 200: // goto_w
                        offset = byteCode.readInt();
                        branchManager.addGotoOperator( codePos, offset, byteCode.getCodePosition(), lineNumber );
                        addNopInstruction( codePos, lineNumber ); // marker of the line number for the branch manager
                        break;
                    default:
                        throw new WasmException( "Unimplemented Java byte code operation: " + op, lineNumber );
                }
                wide = false;
            }
            branchManager.calculate();
            branchManager.handle( byteCode ); // add branch operations
            if( hasReturn && !isEndsWithReturn() ) {
                // if a method ends with a loop or block without a break then code after the loop is no reachable
                // Java does not need a return byte code in this case
                // But WebAssembly need the dead code to validate
                addBlockInstruction( WasmBlockOperator.UNREACHABLE, null, byteCode.getCodePosition(), byteCode.getLineNumber() );
            }
        } catch( Exception ex ) {
            throw WasmException.create( ex, lineNumber );
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
        int switchValuestartPosition = findPreviousPushInstructionCodePosition();

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
            int tempI32 = getTempVariable( ValueType.i32, codePos, Integer.MAX_VALUE );
            int block = 0;
            int defaultBlock = -1;
            int currentPos = -1;
            addLoadStoreInstruction( ValueType.i32, false, tempI32, codePos, lineNumber );
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
                        addLoadStoreInstruction( ValueType.i32, true, tempI32, codePos, lineNumber );
                        addConstInstruction( keys[i], ValueType.i32, codePos, lineNumber );
                        addNumericInstruction( NumericOperator.eq, ValueType.i32, codePos, lineNumber );
                        addBlockInstruction( WasmBlockOperator.BR_IF, block, codePos, lineNumber );
                    }
                }
                block++;
            } while( true );
            if( defaultBlock < 0 ) {
                defaultBlock = block;
            }
            addBlockInstruction( WasmBlockOperator.BR, defaultBlock, codePos, lineNumber );
        } else {
            int low = byteCode.readInt();
            keys = null;
            int count = byteCode.readInt() - low + 1;
            positions = new int[count];
            for( int i = 0; i < count; i++ ) {
                positions[i] = startPosition + byteCode.readInt();
            }
            if( low != 0 ) { // the br_table starts ever with the value 0. That we need to subtract the start value if it different
                addConstInstruction( low, ValueType.i32, codePos, lineNumber );
                addNumericInstruction( NumericOperator.sub, ValueType.i32, codePos, lineNumber );
            }
        }
        branchManager.addSwitchOperator( switchValuestartPosition, 0, lineNumber, keys, positions, defaultPosition );
    }

    /**
     * We need one value from the stack inside of a block. We need to find the WasmInstruction on which the block can
     * start. If this a function call or numeric expression this can be complex to find the right point.
     * 
     * @return the code position that push the last instruction
     */
    @Nonnull
    private int findPreviousPushInstructionCodePosition() {
        int valueCount = 0;
        List<WasmInstruction> instructions = getInstructions();
        for( int i = instructions.size() - 1; i >= 0; i-- ) {
            WasmInstruction instr = instructions.get( i );
            AnyType valueType = instr.getPushValueType();
            if( valueType != null ) {
                valueCount++;
            }
            valueCount -= instr.getPopCount();
            if( valueCount == 1 ) {
                return instr.getCodePosition();
            }
        }
        throw new WasmException( "Start position not found", -1 ); // should never occur
    }

    /**
     * We need the value type from the stack.
     * 
     * @param count
     *            the count of values on the stack back. 1 means the last value. 2 means the penultimate value.
     * @return the type of the last push value
     */
    @Nonnull
    private AnyType findValueTypeFromStack( int count ) {
        int valueCount = 0;
        List<WasmInstruction> instructions = getInstructions();
        for( int i = instructions.size() - 1; i >= 0; i-- ) {
            WasmInstruction instr = instructions.get( i );
            AnyType valueType = instr.getPushValueType();
            if( valueType != null ) {
                if( ++valueCount == count ) {
                    return valueType;
                }
            }
            valueCount -= instr.getPopCount();
        }
        throw new WasmException( "Push Value not found", -1 ); // should never occur
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
     * 
     * @param compareOp
     *            The condition for the continue of a loop.
     * @param byteCode
     *            current byte code stream to read the target offset.
     * @param codePos
     *            the code position/offset in the Java method
     * @param lineNumber
     *            the line number in the Java source code
     * @throws IOException
     *             if any I/O errors occur.
     */
    private void opIfCondition( NumericOperator compareOp, CodeInputStream byteCode, int codePos, int lineNumber ) throws IOException {
        addConstInstruction( 0, ValueType.i32, codePos, lineNumber );
        opIfCompareCondition( compareOp, byteCode, codePos, lineNumber );
    }

    /**
     * Handle the if<condition> of the Java byte code. This Java instruction compare 2 values from stack. Important: In
     * the Java IF expression the condition for the jump to the else block is saved. In WebAssembler we need to use
     * condition for the if block. The caller of the method must already negate this.
     * 
     * @param compareOp
     *            The condition for the continue of a loop.
     * @param byteCode
     *            current byte code stream to read the target offset.
     * @param codePos
     *            the code position/offset in the Java method
     * @param lineNumber
     *            the line number in the Java source code
     * @throws IOException
     *             if any I/O errors occur.
     */
    private void opIfCompareCondition( NumericOperator compareOp, CodeInputStream byteCode, int codePos, int lineNumber ) throws IOException {
        int offset = byteCode.readShort();
        WasmNumericInstruction compare = new WasmNumericInstruction( compareOp, ValueType.i32, codePos, lineNumber );
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
     * @param lineNumber
     *            the line number in the Java source code
     * @throws IOException
     *             if any I/O errors occur.
     */
    private void opCompare( ValueType valueType, CodeInputStream byteCode, int codePos, int lineNumber ) throws IOException {
        codePos = byteCode.getCodePosition();
        NumericOperator numOp;
        int nextOp = byteCode.read();
        switch( nextOp ) {
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
        WasmNumericInstruction compare = new WasmNumericInstruction( numOp, valueType, codePos, lineNumber );
        branchManager.addIfOperator( codePos, offset, byteCode.getLineNumber(), compare );
        getInstructions().add( compare );
    }

}
