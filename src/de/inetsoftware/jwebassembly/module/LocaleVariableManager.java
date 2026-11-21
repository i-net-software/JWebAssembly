/*
   Copyright 2018 - 2026 Volker Berlin (i-net software)

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
package de.inetsoftware.jwebassembly.module;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import de.inetsoftware.classparser.Code;
import de.inetsoftware.classparser.CodeInputStream;
import de.inetsoftware.classparser.ConstantClass;
import de.inetsoftware.classparser.ConstantInvokeDynamic;
import de.inetsoftware.classparser.ConstantPool;
import de.inetsoftware.classparser.ConstantRef;
import de.inetsoftware.classparser.LocalVariable;
import de.inetsoftware.classparser.LocalVariableTable;
import de.inetsoftware.classparser.MethodInfo;
import de.inetsoftware.classparser.StackMapTable;
import de.inetsoftware.classparser.StackMapTable.StackMapFrame;
import de.inetsoftware.classparser.StackMapTable.VerificationType;
import de.inetsoftware.classparser.TryCatchFinally;
import de.inetsoftware.jwebassembly.WasmException;
import de.inetsoftware.jwebassembly.module.TypeManager.StructType;
import de.inetsoftware.jwebassembly.wasm.AnyType;
import de.inetsoftware.jwebassembly.wasm.ArrayType;
import de.inetsoftware.jwebassembly.wasm.ValueType;
import de.inetsoftware.jwebassembly.wasm.ValueTypeParser;

/**
 * This manager monitor the locale variables of a method to create a translation from the slot based index in Java to
 * the variable based index in WebAssembly. An 8-byte variable of type long and double consumes in Java 2 slots, but
 * only one index in WebAssmenbly.
 * <p>
 * The variables are calculated with a flow analysis of the byte code. The stack and the locals are tracked. The types
 * of the StackMapTable are dominant. A value on the stack knows its source variable. If the value is consumed by an
 * operation that requires a specific type then this is used as hint for the type of the variable.
 *
 * @author Volker Berlin
 *
 */
class LocaleVariableManager {

    /** the first slot of temporary variables, Java uses max 65535 slots */
    private static final int                 TEMP_SLOT    = 0x10000;

    private TypeManager                      types;

    private boolean                          debugNames;

    private Variable[]                       variables    = new Variable[0];

    private int                              size;

    private final ArrayList<AnyType>         localTypes   = new ArrayList<>();

    private final HashSet<String>            names        = new HashSet<>();

    // state of the flow analysis, only valid while reset()
    private final ArrayList<Variable>        allVariables = new ArrayList<>();

    /** the variable of the load/store instruction at a code position */
    private final HashMap<Integer, Variable> positions   = new HashMap<>();

    private final ArrayDeque<StackValue>     stack        = new ArrayDeque<>();

    /** slot based: the second slot of a long or double is null */
    private final ArrayList<Variable>        locals       = new ArrayList<>();

    /** the locals of all code positions that jump forward to a code position (branch target or catch handler) */
    private final HashMap<Integer, ArrayList<Variable[]>> incoming = new HashMap<>();

    /** the locals after applying the stack map frame at a code position, needed for backward jumps */
    private final HashMap<Integer, Variable[]> frameStates = new HashMap<>();

    /** if the previous instruction can continue with the next instruction */
    private boolean                          fallThrough;

    private Code                             code;

    private String                           className;

    /**
     * Create a new instance.
     */
    LocaleVariableManager() {

    }

    /**
     * Initialize the variable manager;
     *
     * @param options
     *            compiler properties
     */
    void init( @Nonnull WasmOptions options ) {
        this.types = options.types;
        this.debugNames = options.debugNames();
    }

    /**
     * Reset the manager to an initial state.
     *
     * @param code
     *            the Java method code, used to get LocalVariableTable and StackMapTable (can be null)
     * @param method
     *            the method with signature as fallback for a missing variable table. If null signature is used and the method must be static.
     * @param signature
     *            alternative for method signature, can be null if method is set
     */
    void reset( @Nullable Code code, MethodInfo method, Iterator<AnyType> signature ) {
        size = 0;
        allVariables.clear();
        positions.clear();
        stack.clear();
        locals.clear();
        incoming.clear();
        frameStates.clear();
        fallThrough = true;
        this.code = code;
        this.className = method != null ? method.getClassName() : null;

        // the implicit initial frame: THIS and the parameters of the method.
        // The entries are in the StackMapTable notation: long and double use one entry, null is Top
        ArrayList<AnyType> frameLocals = new ArrayList<>();
        Iterator<AnyType> parser = signature != null ? signature : method != null ? new ValueTypeParser( method.getType(), types ) : null;
        if( method != null && !method.isStatic() ) {
            frameLocals.add( types.valueOf( method.getClassName() ) );
        }
        if( parser != null ) {
            for( AnyType type; parser.hasNext() && (type = parser.next()) != null; ) {
                frameLocals.add( type );
            }
        }
        if( code == null ) {
            // synthetic function from the WatParser. The slot is equals to the WebAssembly index. The parameters are
            // the first variables, all other variables are added via useIndex()
            finish();
            for( AnyType type : frameLocals ) {
                addVariable( size, type, 0, Integer.MAX_VALUE );
            }
        } else {
            // the parameters are the first variables
            applyFrameLocals( frameLocals, 0 );

            AnyType returnType = method == null ? null : new ValueTypeParser( method.getType().substring( method.getType().lastIndexOf( ')' ) + 1 ), types ).next();

            int lineNumber = -1;
            try {
                ConstantPool constantPool = code.getConstantPool();
                CodeInputStream byteCode = code.getByteCode();
                StackMapTable stackMapTable = code.getStackMapTable();
                StackMapFrame[] frames = stackMapTable != null ? stackMapTable.getFrames() : null;
                int framePos = 0;
                int nextFrameCodePos = frames != null && frames.length > 0 ? frames[0].getOffsetDelta() : -1;
                boolean wide = false;

                // the code positions of the catch blocks
                TryCatchFinally[] exceptionTable = code.getExceptionTable();
                HashSet<Integer> handlers = new HashSet<>();
                for( TryCatchFinally tryCatch : exceptionTable ) {
                    handlers.add( tryCatch.getHandler() );
                }
                AnyType throwable = handlers.isEmpty() ? null : types.valueOf( "java/lang/Throwable" );

                while( byteCode.available() > 0 ) {
                    int codePos = byteCode.getCodePosition();
                    lineNumber = byteCode.getLineNumber();

                    if( codePos == nextFrameCodePos ) {
                        StackMapFrame frame = frames[framePos++];
                        switch( frame.getFrameType() ) {
                            case SAME_FRAME:
                            case SAME_FRAME_EXTENDED:
                            case SAME_LOCALS_1_STACK_ITEM_FRAME:
                            case SAME_LOCALS_1_STACK_ITEM_FRAME_EXTENDED:
                                break;
                            case CHOP_FRAME:
                                for( int i = frame.getK(); i > 0; i-- ) {
                                    frameLocals.remove( frameLocals.size() - 1 );
                                }
                                break;
                            case APPEND_FRAME:
                                for( VerificationType vt : frame.getLocals() ) {
                                    frameLocals.add( getAnyType( vt ) );
                                }
                                break;
                            case FULL_FRAME:
                                frameLocals.clear();
                                for( VerificationType vt : frame.getLocals() ) {
                                    frameLocals.add( getAnyType( vt ) );
                                }
                                break;
                            default:
                                throw new IllegalStateException( "Unknown frame type: " + frame.getFrameType() );
                        }
                        applyFrameLocals( frameLocals, codePos );

                        // the stack of all frame types is complete defined in the frame
                        stack.clear();
                        if( handlers.contains( codePos ) ) {
                            // The try_table in WebAssembly delivers ever a Throwable (common supertype of all catches).
                            // The StackMapTable contains the declared catch type, for example Exception. The local
                            // variable must be able to hold the Throwable. See BranchManager.getCatchType()
                            push( throwable );
                        } else {
                            for( VerificationType vt : frame.getStack() ) {
                                push( getAnyType( vt ) );
                            }
                        }

                        if( framePos < frames.length ) {
                            nextFrameCodePos += frames[framePos].getOffsetDelta() + 1;
                        }
                    }
                    fallThrough = true;

                    // every instruction in a try block can jump to the catch handler with the current locals
                    for( TryCatchFinally tryCatch : exceptionTable ) {
                        if( tryCatch.getStart() <= codePos && codePos < tryCatch.getEnd() ) {
                            addIncoming( tryCatch.getHandler(), codePos );
                        }
                    }

                    int op = byteCode.readUnsignedByte();
                    switch( op ) {
                        case 0: // nop
                            break;
                        case 1: // aconst_null
                            push( ValueType.eqref );
                            break;
                        case 2: // iconst_m1
                        case 3: // iconst_0
                        case 4: // iconst_1
                        case 5: // iconst_2
                        case 6: // iconst_3
                        case 7: // iconst_4
                        case 8: // iconst_5
                            push( ValueType.i32 );
                            break;
                        case 9: // lconst_0
                        case 10: // lconst_1
                            push( ValueType.i64 );
                            break;
                        case 11: // fconst_0
                        case 12: // fconst_1
                        case 13: // fconst_2
                            push( ValueType.f32 );
                            break;
                        case 14: // dconst_0
                        case 15: // dconst_1
                            push( ValueType.f64 );
                            break;
                        case 16: // bipush
                            byteCode.readByte();
                            push( ValueType.i32 );
                            break;
                        case 17: // sipush
                            byteCode.readShort();
                            push( ValueType.i32 );
                            break;
                        case 18: // ldc
                            push( getConstType( constantPool.get( byteCode.readUnsignedByte() ) ) );
                            break;
                        case 19: // ldc_w
                        case 20: // ldc2_w
                            push( getConstType( constantPool.get( byteCode.readUnsignedShort() ) ) );
                            break;
                        case 21: // iload
                            load( byteCode.readUnsignedIndex( wide ), ValueType.i32, codePos );
                            break;
                        case 22: // lload
                            load( byteCode.readUnsignedIndex( wide ), ValueType.i64, codePos );
                            break;
                        case 23: // fload
                            load( byteCode.readUnsignedIndex( wide ), ValueType.f32, codePos );
                            break;
                        case 24: // dload
                            load( byteCode.readUnsignedIndex( wide ), ValueType.f64, codePos );
                            break;
                        case 25: // aload
                            load( byteCode.readUnsignedIndex( wide ), null, codePos );
                            break;
                        case 26: // iload_0
                        case 27: // iload_1
                        case 28: // iload_2
                        case 29: // iload_3
                            load( op - 26, ValueType.i32, codePos );
                            break;
                        case 30: // lload_0
                        case 31: // lload_1
                        case 32: // lload_2
                        case 33: // lload_3
                            load( op - 30, ValueType.i64, codePos );
                            break;
                        case 34: // fload_0
                        case 35: // fload_1
                        case 36: // fload_2
                        case 37: // fload_3
                            load( op - 34, ValueType.f32, codePos );
                            break;
                        case 38: // dload_0
                        case 39: // dload_1
                        case 40: // dload_2
                        case 41: // dload_3
                            load( op - 38, ValueType.f64, codePos );
                            break;
                        case 42: // aload_0
                        case 43: // aload_1
                        case 44: // aload_2
                        case 45: // aload_3
                            load( op - 42, null, codePos );
                            break;
                        case 46: // iaload
                        case 51: // baload
                        case 52: // caload
                        case 53: // saload
                            stack.pop(); // index
                            stack.pop(); // array
                            push( ValueType.i32 );
                            break;
                        case 47: // laload
                            stack.pop();
                            stack.pop();
                            push( ValueType.i64 );
                            break;
                        case 48: // faload
                            stack.pop();
                            stack.pop();
                            push( ValueType.f32 );
                            break;
                        case 49: // daload
                            stack.pop();
                            stack.pop();
                            push( ValueType.f64 );
                            break;
                        case 50: // aaload
                            stack.pop(); // index
                            push( getComponentType( stack.pop().type ) );
                            break;
                        case 54: // istore
                        case 55: // lstore
                        case 56: // fstore
                        case 57: // dstore
                        case 58: // astore
                            store( byteCode.readUnsignedIndex( wide ), stack.pop(), codePos );
                            break;
                        case 59: // istore_0
                        case 60: // istore_1
                        case 61: // istore_2
                        case 62: // istore_3
                            store( op - 59, stack.pop(), codePos );
                            break;
                        case 63: // lstore_0
                        case 64: // lstore_1
                        case 65: // lstore_2
                        case 66: // lstore_3
                            store( op - 63, stack.pop(), codePos );
                            break;
                        case 67: // fstore_0
                        case 68: // fstore_1
                        case 69: // fstore_2
                        case 70: // fstore_3
                            store( op - 67, stack.pop(), codePos );
                            break;
                        case 71: // dstore_0
                        case 72: // dstore_1
                        case 73: // dstore_2
                        case 74: // dstore_3
                            store( op - 71, stack.pop(), codePos );
                            break;
                        case 75: // astore_0
                        case 76: // astore_1
                        case 77: // astore_2
                        case 78: // astore_3
                            store( op - 75, stack.pop(), codePos );
                            break;
                        case 79: // iastore
                        case 80: // lastore
                        case 81: // fastore
                        case 82: // dastore
                        case 84: // bastore
                        case 85: // castore
                        case 86: // sastore
                            stack.pop(); // value
                            stack.pop(); // index
                            stack.pop(); // array
                            break;
                        case 83: { // aastore
                            StackValue value = stack.pop();
                            stack.pop(); // index
                            StackValue array = stack.pop();
                            use( value, getComponentType( array.type ) );
                            break;
                        }
                        case 87: // pop
                            stack.pop();
                            break;
                        case 88: // pop2
                            if( !isCategory2( stack.pop().type ) ) {
                                stack.pop();
                            }
                            break;
                        case 89: // dup
                            stack.push( stack.peek() );
                            break;
                        case 90: { // dup_x1
                            StackValue v1 = stack.pop();
                            StackValue v2 = stack.pop();
                            push( v1, v2, v1 );
                            break;
                        }
                        case 91: { // dup_x2
                            StackValue v1 = stack.pop();
                            StackValue v2 = stack.pop();
                            if( isCategory2( v2.type ) ) {
                                push( v1, v2, v1 );
                            } else {
                                StackValue v3 = stack.pop();
                                push( v1, v3, v2, v1 );
                            }
                            break;
                        }
                        case 92: { // dup2
                            StackValue v1 = stack.pop();
                            if( isCategory2( v1.type ) ) {
                                push( v1, v1 );
                            } else {
                                StackValue v2 = stack.pop();
                                push( v2, v1, v2, v1 );
                            }
                            break;
                        }
                        case 93: { // dup2_x1
                            StackValue v1 = stack.pop();
                            StackValue v2 = stack.pop();
                            if( isCategory2( v1.type ) ) {
                                push( v1, v2, v1 );
                            } else {
                                StackValue v3 = stack.pop();
                                push( v2, v1, v3, v2, v1 );
                            }
                            break;
                        }
                        case 94: { // dup2_x2
                            StackValue v1 = stack.pop();
                            StackValue v2 = stack.pop();
                            if( isCategory2( v1.type ) ) {
                                if( isCategory2( v2.type ) ) {
                                    push( v1, v2, v1 );
                                } else {
                                    StackValue v3 = stack.pop();
                                    push( v1, v3, v2, v1 );
                                }
                            } else {
                                StackValue v3 = stack.pop();
                                if( isCategory2( v3.type ) ) {
                                    push( v2, v1, v3, v2, v1 );
                                } else {
                                    StackValue v4 = stack.pop();
                                    push( v2, v1, v4, v3, v2, v1 );
                                }
                            }
                            break;
                        }
                        case 95: { // swap
                            StackValue v1 = stack.pop();
                            StackValue v2 = stack.pop();
                            push( v1, v2 );
                            break;
                        }
                        case 96: // iadd
                        case 97: // ladd
                        case 98: // fadd
                        case 99: // dadd
                        case 100: // isub
                        case 101: // lsub
                        case 102: // fsub
                        case 103: // dsub
                        case 104: // imul
                        case 105: // lmul
                        case 106: // fmul
                        case 107: // dmul
                        case 108: // idiv
                        case 109: // ldiv
                        case 110: // fdiv
                        case 111: // ddiv
                        case 112: // irem
                        case 113: // lrem
                        case 114: // frem
                        case 115: // drem
                        case 120: // ishl
                        case 121: // lshl
                        case 122: // ishr
                        case 123: // lshr
                        case 124: // iushr
                        case 125: // lushr
                        case 126: // iand
                        case 127: // land
                        case 128: // ior
                        case 129: // lor
                        case 130: // ixor
                        case 131: // lxor
                            // binary operation, the result has the type of the first operand (the shift distance is ever an int)
                            stack.pop();
                            push( stack.pop().type );
                            break;
                        case 116: // ineg
                        case 117: // lneg
                        case 118: // fneg
                        case 119: // dneg
                            // unary operation, the type does not change but the value is not the variable anymore
                            push( stack.pop().type );
                            break;
                        case 132: // iinc
                            access( byteCode.readUnsignedIndex( wide ), ValueType.i32, codePos );
                            if( wide ) {
                                byteCode.readShort();
                            } else {
                                byteCode.readByte();
                            }
                            break;
                        case 133: // i2l
                        case 140: // f2l
                        case 143: // d2l
                            stack.pop();
                            push( ValueType.i64 );
                            break;
                        case 134: // i2f
                        case 137: // l2f
                        case 144: // d2f
                            stack.pop();
                            push( ValueType.f32 );
                            break;
                        case 135: // i2d
                        case 138: // l2d
                        case 141: // f2d
                            stack.pop();
                            push( ValueType.f64 );
                            break;
                        case 136: // l2i
                        case 139: // f2i
                        case 142: // d2i
                        case 145: // i2b
                        case 146: // i2c
                        case 147: // i2s
                            stack.pop();
                            push( ValueType.i32 );
                            break;
                        case 148: // lcmp
                        case 149: // fcmpl
                        case 150: // fcmpg
                        case 151: // dcmpl
                        case 152: // dcmpg
                            stack.pop();
                            stack.pop();
                            push( ValueType.i32 );
                            break;
                        case 153: // ifeq
                        case 154: // ifne
                        case 155: // iflt
                        case 156: // ifge
                        case 157: // ifgt
                        case 158: // ifle
                        case 198: // ifnull
                        case 199: // ifnonnull
                            addIncoming( codePos + byteCode.readShort(), codePos );
                            stack.pop();
                            break;
                        case 159: // if_icmpeq
                        case 160: // if_icmpne
                        case 161: // if_icmplt
                        case 162: // if_icmpge
                        case 163: // if_icmpgt
                        case 164: // if_icmple
                        case 165: // if_acmpeq
                        case 166: // if_acmpne
                            addIncoming( codePos + byteCode.readShort(), codePos );
                            stack.pop();
                            stack.pop();
                            break;
                        case 167: // goto
                            addIncoming( codePos + byteCode.readShort(), codePos );
                            // the following code has ever a frame
                            stack.clear();
                            fallThrough = false;
                            break;
                        case 200: // goto_w
                            addIncoming( codePos + byteCode.readInt(), codePos );
                            stack.clear();
                            fallThrough = false;
                            break;
                        case 168: // jsr
                        case 169: // ret
                        case 201: // jsr_w
                            throw new WasmException( "Finally block of Java 5 or older is not supported. Compile the sources with a Java SE 6 or newer: " + op, lineNumber );
                        case 170: // tableswitch
                        case 171: // lookupswitch
                            for( int target : readSwitchTargets( byteCode, op == 171, codePos ) ) {
                                addIncoming( target, codePos );
                            }
                            stack.clear();
                            fallThrough = false;
                            break;
                        case 176: // areturn
                            use( stack.pop(), returnType );
                            stack.clear();
                            fallThrough = false;
                            break;
                        case 172: // ireturn
                        case 173: // lreturn
                        case 174: // freturn
                        case 175: // dreturn
                        case 177: // return void
                            stack.clear();
                            fallThrough = false;
                            break;
                        case 191: // athrow
                            use( stack.pop(), types.valueOf( "java/lang/Throwable" ) );
                            stack.clear();
                            fallThrough = false;
                            break;
                        case 178: // getstatic
                            ConstantRef ref = (ConstantRef)constantPool.get( byteCode.readUnsignedShort() );
                            push( new ValueTypeParser( ref.getType(), types ).next() );
                            break;
                        case 179: // putstatic
                            ref = (ConstantRef)constantPool.get( byteCode.readUnsignedShort() );
                            use( stack.pop(), new ValueTypeParser( ref.getType(), types ).next() );
                            break;
                        case 180: // getfield
                            ref = (ConstantRef)constantPool.get( byteCode.readUnsignedShort() );
                            use( stack.pop(), types.valueOf( ref.getClassName() ) );
                            push( new ValueTypeParser( ref.getType(), types ).next() );
                            break;
                        case 181: // putfield
                            ref = (ConstantRef)constantPool.get( byteCode.readUnsignedShort() );
                            use( stack.pop(), new ValueTypeParser( ref.getType(), types ).next() );
                            use( stack.pop(), types.valueOf( ref.getClassName() ) );
                            break;
                        case 182: // invokevirtual
                        case 183: // invokespecial, invoke a constructor
                        case 184: // invokestatic
                        case 185: // invokeinterface
                            ref = (ConstantRef)constantPool.get( byteCode.readUnsignedShort() );
                            if( op == 185 ) {
                                byteCode.readUnsignedByte(); // count
                                byteCode.readUnsignedByte(); // 0
                            }
                            invoke( ref.getType(), op != 184 ? types.valueOf( ref.getClassName() ) : null );
                            break;
                        case 186: // invokedynamic
                            ConstantInvokeDynamic dynamic = (ConstantInvokeDynamic)constantPool.get( byteCode.readUnsignedShort() );
                            byteCode.readUnsignedShort(); // ever zero
                            invoke( dynamic.getType(), null );
                            break;
                        case 187: // new
                            String name = ((ConstantClass)constantPool.get( byteCode.readUnsignedShort() )).getName();
                            push( types.valueOf( name ) );
                            break;
                        case 188: // newarray
                            AnyType componentType;
                            int typeValue = byteCode.readByte();
                            switch( typeValue ) {
                                case 4: // boolean
                                    componentType = ValueType.bool;
                                    break;
                                case 5: // char
                                    componentType = ValueType.u16;
                                    break;
                                case 6: //float
                                    componentType = ValueType.f32;
                                    break;
                                case 7: //double
                                    componentType = ValueType.f64;
                                    break;
                                case 8: //byte
                                    componentType = ValueType.i8;
                                    break;
                                case 9: //short
                                    componentType = ValueType.i16;
                                    break;
                                case 10: //int
                                    componentType = ValueType.i32;
                                    break;
                                case 11: //long
                                    componentType = ValueType.i64;
                                    break;
                                default:
                                    throw new WasmException( "Invalid Java byte code newarray: " + typeValue, lineNumber );
                            }
                            stack.pop(); // count
                            push( types.arrayType( componentType ) );
                            break;
                        case 189: // anewarray
                            name = ((ConstantClass)constantPool.get( byteCode.readUnsignedShort() )).getName();
                            stack.pop(); // count
                            push( types.arrayType( types.valueOf( name ) ) );
                            break;
                        case 190: // arraylength
                            stack.pop();
                            push( ValueType.i32 );
                            break;
                        case 192: // checkcast
                            name = ((ConstantClass)constantPool.get( byteCode.readUnsignedShort() )).getName();
                            stack.pop();
                            push( types.valueOf( name ) );
                            break;
                        case 193: // instanceof
                            byteCode.readUnsignedShort();
                            stack.pop();
                            push( ValueType.i32 );
                            break;
                        case 194: // monitorenter
                        case 195: // monitorexit
                            stack.pop();
                            break;
                        case 196: // wide
                            // https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-6.html#jvms-6.5.wide
                            wide = true;
                            continue;
                        case 197: // multianewarray
                            name = ((ConstantClass)constantPool.get( byteCode.readUnsignedShort() )).getName();
                            for( int dimensions = byteCode.readUnsignedByte(); dimensions > 0; dimensions-- ) {
                                stack.pop();
                            }
                            push( types.valueOf( name ) );
                            break;
                        default:
                            throw new WasmException( "Unimplemented Java byte code operation: " + op, lineNumber );
                    }
                    wide = false;
                }

                finish();
                if( debugNames ) {
                    setNames( code.getLocalVariableTable() );
                }
            } catch( Throwable ex ) {
                throw WasmException.create( ex, lineNumber );
            }
        }
    }

    /**
     * Set the names of the variables from the LocalVariableTable. The names are only needed for debug output.
     *
     * @param variableTable
     *            the table, can be null if the class was compiled without debug information
     */
    private void setNames( @Nullable LocalVariableTable variableTable ) {
        if( variableTable != null ) {
            for( LocalVariable local : variableTable.getTable() ) {
                int slot = local.getIndex();
                int start = local.getStartPosition();
                int end = start + local.getLengthPosition();
                Variable var = null;
                int storePos = -1;
                for( Entry<Integer, Variable> entry : positions.entrySet() ) {
                    Variable v = find( entry.getValue() );
                    if( v.slot != slot ) {
                        continue;
                    }
                    int pos = entry.getKey();
                    if( start <= pos && pos <= end ) {
                        // inside the scope a slot is ever the same variable
                        var = v;
                        break;
                    }
                    if( pos < start && pos >= start - 4 && pos > storePos ) {
                        // the scope starts after the first store, a store instruction has a max length of 4 bytes (wide astore)
                        storePos = pos;
                        var = v;
                    }
                }
                if( var == null ) {
                    // an unused parameter
                    for( int i = 0; i < size; i++ ) {
                        Variable v = variables[i];
                        if( v.slot == slot && v.matchCodePosition( start ) ) {
                            var = v;
                            break;
                        }
                    }
                }
                if( var != null && var.name == null ) {
                    var.name = local.getName();
                }
            }
        }

        // make the names unique. Java can use the same variable name in different blocks. WebAssembly text output does not accept this.
        names.clear();
        for( int i = 0; i < size; i++ ) {
            Variable var = variables[i];
            if( var.name != null ) {
                var.name = findUniqueVarName( var.name );
            }
        }
    }

    /**
     * Find a unique variable name.
     *
     * @param name
     *            the suggested name
     * @return a name that not was used before
     */
    @Nonnull
    private String findUniqueVarName( @Nonnull String name ) {
        if( names.contains( name ) ) {
            // duplicate name for a variable in a different block
            int id = 1;
            do {
                String name2 = name + '_' + ++id;
                if( !names.contains( name2 ) ) {
                    name = name2;
                    break;
                }
            } while( true );
        }
        names.add( name );
        return name;
    }

    /**
     * Set the slot based locals from the entries of a stack map frame. The types of the frame are dominant. The
     * variables of a slot that reach this code position on different paths (fall through from the previous
     * instruction, forward jumps and exceptions of a try block) are merged to a single variable. Variables of backward
     * jumps are merged later in {@link #addIncoming(int, int)}.
     *
     * @param frameLocals
     *            the locals in StackMapTable notation, long and double use only one entry, null is Top
     * @param codePos
     *            the code position of the frame
     */
    private void applyFrameLocals( @Nonnull ArrayList<AnyType> frameLocals, int codePos ) {
        // collect the locals of all paths that reach this code position
        ArrayList<Variable[]> sources = new ArrayList<>();
        if( fallThrough ) {
            sources.add( locals.toArray( new Variable[locals.size()] ) );
        }
        ArrayList<Variable[]> jumps = incoming.remove( codePos );
        if( jumps != null ) {
            sources.addAll( jumps );
        }

        locals.clear();
        for( AnyType type : frameLocals ) {
            int slot = locals.size();
            if( type == null ) {
                locals.add( null );
                continue;
            }
            Variable var = null;
            for( Variable[] source : sources ) {
                Variable v = slot < source.length ? find( source[slot] ) : null;
                if( v != null && isCompatible( v, type ) ) {
                    var = var == null ? v : merge( var, v );
                }
            }
            if( var == null ) {
                var = createVariable( slot, codePos );
            }
            var.addLowerBound( type );
            var.touch( codePos );
            locals.add( var );
            if( isCategory2( type ) ) {
                locals.add( null );
            }
        }
        frameStates.put( codePos, locals.toArray( new Variable[locals.size()] ) );
    }

    /**
     * If the code between the two code positions is executed linear. That there is no jump target (stack map frame)
     * in the range (from, to].
     *
     * @param from
     *            the start code position (exclusive)
     * @param to
     *            the end code position (inclusive)
     * @return true, if there is no jump target in the range
     */
    boolean isLinearCode( int from, int to ) {
        for( Integer pos : frameStates.keySet() ) {
            if( pos > from && pos <= to ) {
                return false;
            }
        }
        return true;
    }

    /**
     * Register the current locals for a jump to the target code position. On a forward jump the locals are merged if
     * the stack map frame of the target is applied. On a backward jump the frame was already applied and the variables
     * are merged directly.
     *
     * @param target
     *            the code position of the jump target or catch handler
     * @param codePos
     *            the code position of the jump instruction
     */
    private void addIncoming( int target, int codePos ) {
        Variable[] current = locals.toArray( new Variable[locals.size()] );
        if( target > codePos ) {
            ArrayList<Variable[]> list = incoming.get( target );
            if( list == null ) {
                incoming.put( target, list = new ArrayList<>() );
            } else if( Arrays.equals( list.get( list.size() - 1 ), current ) ) {
                return; // no change of the locals, for example in a try block
            }
            list.add( current );
            return;
        }

        Variable[] frameState = frameStates.get( target );
        if( frameState == null ) {
            return; // should not occur with valid byte code
        }
        for( int slot = 0; slot < frameState.length && slot < current.length; slot++ ) {
            Variable frameVar = find( frameState[slot] );
            Variable var = find( current[slot] );
            if( frameVar != null && var != null && frameVar != var && isCompatible( frameVar, getType( var ) ) ) {
                frameVar = merge( frameVar, var );
                frameVar.touch( codePos );
            }
        }
    }

    /**
     * Load a local variable on the stack.
     *
     * @param slot
     *            the slot index in Java
     * @param expected
     *            the type of the load operation, null for any reference
     * @param codePos
     *            the code position of the load operation
     */
    private void load( int slot, @Nullable AnyType expected, int codePos ) {
        Variable var = access( slot, expected, codePos );
        stack.push( new StackValue( getType( var ), var ) );
    }

    /**
     * Get the variable of a slot for a load or iinc operation and register it for the code position.
     *
     * @param slot
     *            the slot index in Java
     * @param expected
     *            the type of the operation, null for any reference
     * @param codePos
     *            the code position of the operation
     * @return the variable
     */
    @Nonnull
    private Variable access( int slot, @Nullable AnyType expected, int codePos ) {
        Variable var = slot < locals.size() ? find( locals.get( slot ) ) : null;
        boolean valid = var != null && (expected == null ? isRef( getType( var ) ) : isCompatible( var, expected ));
        if( !valid ) {
            // should not occur with valid byte code
            var = createVariable( slot, codePos );
            var.addLowerBound( expected != null ? expected : types.valueOf( "java/lang/Object" ) );
            setLocal( slot, var, isCategory2( expected ) );
        }
        var.touch( codePos );
        positions.put( codePos, var );
        return var;
    }

    /**
     * Store a value in a local variable slot. As long as the type is compatible the current variable of the slot is
     * extended. Else a new variable is created.
     *
     * @param slot
     *            the slot index in Java
     * @param value
     *            the stored value
     * @param codePos
     *            the code position of the store operation
     */
    private void store( int slot, @Nonnull StackValue value, int codePos ) {
        AnyType type = value.type;
        Variable var = slot < locals.size() ? find( locals.get( slot ) ) : null;
        if( var == null || !isCompatible( var, type ) ) {
            var = createVariable( slot, codePos );
        }
        var.addLowerBound( type );
        var.touch( codePos );
        positions.put( codePos, var );
        setLocal( slot, var, isCategory2( type ) );
    }

    /**
     * Set the variable of a slot in the current locals.
     *
     * @param slot
     *            the slot index in Java
     * @param var
     *            the variable
     * @param category2
     *            true, if the variable use 2 slots
     */
    private void setLocal( int slot, @Nonnull Variable var, boolean category2 ) {
        int needed = category2 ? slot + 2 : slot + 1;
        while( locals.size() < needed ) {
            locals.add( null );
        }
        if( slot > 0 ) {
            Variable previous = find( locals.get( slot - 1 ) );
            if( previous != null && isCategory2( getType( previous ) ) ) {
                // overwrite the second half of a long or double
                locals.set( slot - 1, null );
            }
        }
        locals.set( slot, var );
        if( category2 ) {
            locals.set( slot + 1, null );
        }
    }

    /**
     * A value is consumed by an operation that requires the given type. If the value comes from a variable then the
     * type is a hint for the variable type.
     *
     * @param value
     *            the consumed value
     * @param required
     *            the required type of the operation or null
     */
    private void use( @Nonnull StackValue value, @Nullable AnyType required ) {
        if( value.var != null && required != null && required.isRefType() ) {
            find( value.var ).addHint( required );
        }
    }

    /**
     * Pop the parameters of a method call from the stack and push the return value.
     *
     * @param signature
     *            the Java method signature
     * @param receiver
     *            the type of the receiver object (THIS) or null if static
     */
    private void invoke( @Nonnull String signature, @Nullable AnyType receiver ) {
        ValueTypeParser parser = new ValueTypeParser( signature, types );
        ArrayList<AnyType> params = new ArrayList<>();
        for( AnyType type; (type = parser.next()) != null; ) {
            params.add( type );
        }
        for( int i = params.size() - 1; i >= 0; i-- ) {
            use( stack.pop(), params.get( i ) );
        }
        if( receiver != null ) {
            use( stack.pop(), receiver );
        }
        AnyType returnType = parser.next();
        if( returnType != null ) {
            push( returnType );
        }
    }

    /**
     * Push a value without a source variable on the stack.
     *
     * @param type
     *            the type of the value
     */
    private void push( @Nonnull AnyType type ) {
        stack.push( new StackValue( type, null ) );
    }

    /**
     * Push multiple values on the stack. The last value is on the top.
     *
     * @param values
     *            the values from bottom to top
     */
    private void push( @Nonnull StackValue... values ) {
        for( StackValue value : values ) {
            stack.push( value );
        }
    }

    /**
     * Create a new variable.
     *
     * @param slot
     *            the slot index in Java
     * @param codePos
     *            the first code position of the variable
     * @return the variable
     */
    @Nonnull
    private Variable createVariable( int slot, int codePos ) {
        Variable var = new Variable();
        var.slot = slot;
        var.order = allVariables.size();
        var.startPos = codePos;
        var.endPos = codePos;
        allVariables.add( var );
        return var;
    }

    /**
     * Merge two variables. The older variable survives so that the parameters keep the first indices.
     *
     * @param a
     *            first variable
     * @param b
     *            second variable
     * @return the merged variable
     */
    @Nonnull
    private static Variable merge( @Nonnull Variable a, @Nonnull Variable b ) {
        a = find( a );
        b = find( b );
        if( a == b ) {
            return a;
        }
        Variable target = a.order < b.order ? a : b;
        Variable source = target == a ? b : a;
        source.mergedInto = target;
        for( AnyType type : source.lowerBounds ) {
            target.addLowerBound( type );
        }
        for( AnyType type : source.hints ) {
            target.addHint( type );
        }
        target.startPos = Math.min( target.startPos, source.startPos );
        target.endPos = Math.max( target.endPos, source.endPos );
        return target;
    }

    /**
     * Resolve a variable that was merged into another variable.
     *
     * @param var
     *            the variable or null
     * @return the surviving variable or null
     */
    @Nullable
    private static Variable find( @Nullable Variable var ) {
        while( var != null && var.mergedInto != null ) {
            var = var.mergedInto;
        }
        return var;
    }

    /**
     * Check if a value of the type can be saved in the variable without a new variable. Primitive types must be equal.
     * References must be related.
     *
     * @param var
     *            the variable
     * @param type
     *            the type of the value
     * @return true, if compatible
     */
    private boolean isCompatible( @Nonnull Variable var, @Nonnull AnyType type ) {
        AnyType varType = getType( var );
        if( isRef( varType ) && isRef( type ) ) {
            if( varType == ValueType.eqref || type == ValueType.eqref ) {
                return true; // null
            }
            return type.isSubTypeOf( varType ) || varType.isSubTypeOf( type );
        }
        return varType == type;
    }

    /**
     * Get the current type of the variable.
     *
     * @param var
     *            the variable
     * @return the type
     */
    @Nonnull
    private AnyType getType( @Nonnull Variable var ) {
        if( var.dirty ) {
            var.valueType = calculateType( var );
            var.dirty = false;
        }
        return var.valueType;
    }

    /**
     * Calculate the type of a variable. It is the most specific type that is a super type of all stored values.
     * Candidates are the stored types, the types of the StackMapTable, the required types of the consumers (hints)
     * and the super classes.
     *
     * @param var
     *            the variable
     * @return the type
     */
    @Nonnull
    private AnyType calculateType( @Nonnull Variable var ) {
        ArrayList<AnyType> lowerBounds = var.lowerBounds;
        if( lowerBounds.isEmpty() ) {
            // only null was assigned
            return var.hints.isEmpty() ? ValueType.eqref : var.hints.get( 0 );
        }
        AnyType first = lowerBounds.get( 0 );
        if( !first.isRefType() ) {
            return first; // primitive types are ever equals
        }

        ArrayList<AnyType> candidates = new ArrayList<>( lowerBounds );
        candidates.addAll( var.hints );
        for( AnyType type : lowerBounds ) {
            if( type instanceof StructType ) {
                for( StructType parent = ((StructType)type).getParent(); parent != null; parent = parent.getParent() ) {
                    candidates.add( parent );
                }
            }
        }

        AnyType best = null;
        for( AnyType candidate : candidates ) {
            if( candidate == best || !isSuperTypeOfAll( candidate, lowerBounds ) ) {
                continue;
            }
            if( best == null || candidate.isSubTypeOf( best ) ) {
                best = candidate;
            }
        }
        //TODO if best is not a sub type of all hints then a cast is needed on the consumer
        return best != null ? best : types.valueOf( "java/lang/Object" );
    }

    /**
     * If the candidate is a super type of all types.
     *
     * @param candidate
     *            the candidate
     * @param types
     *            the types
     * @return true, if all types are a sub type of the candidate
     */
    private static boolean isSuperTypeOfAll( @Nonnull AnyType candidate, @Nonnull ArrayList<AnyType> types ) {
        for( AnyType type : types ) {
            if( !type.isSubTypeOf( candidate ) ) {
                return false;
            }
        }
        return true;
    }

    /**
     * Build the final list of variables after the flow analysis.
     */
    private void finish() {
        ensureCapacity( allVariables.size() );
        int idx = 0;
        for( Variable var : allVariables ) {
            if( var.mergedInto == null ) {
                variables[ idx ] = var;
                var.idx = idx++;
                getType( var );
            }
        }
        size = idx;
        code = null;
    }

    /**
     * Get the component type of an array type for aaload.
     *
     * @param arrayType
     *            the type of the array on the stack
     * @return the component type
     */
    @Nonnull
    private AnyType getComponentType( AnyType arrayType ) {
        if( arrayType instanceof ArrayType ) {
            return ((ArrayType)arrayType).getArrayType();
        }
        // a null constant or an unknown type
        return types.valueOf( "java/lang/Object" );
    }

    /**
     * Get the type of a constant from the constant pool for the ldc operations.
     *
     * @param value
     *            the value from the constant pool
     * @return the type
     */
    @Nonnull
    private AnyType getConstType( Object value ) {
        if( value instanceof Integer ) {
            return ValueType.i32;
        }
        if( value instanceof Float ) {
            return ValueType.f32;
        }
        if( value instanceof Long ) {
            return ValueType.i64;
        }
        if( value instanceof Double ) {
            return ValueType.f64;
        }
        if( value instanceof String ) {
            return types.valueOf( "java/lang/String" );
        }
        if( value instanceof ConstantClass ) {
            return types.valueOf( "java/lang/Class" );
        }
        if( value instanceof ConstantRef ) { // a MethodHandle
            return types.valueOf( "java/lang/invoke/MethodHandle" );
        }
        throw new WasmException( "Not supported constant type: " + value, -1 );
    }

    /**
     * Read the data of a tableswitch or lookupswitch operation and return the jump targets.
     *
     * @param byteCode
     *            the current stream with a position after the operation code
     * @param isLookupSwitch
     *            true, if the operation was a lookupswitch; false, if the operation was a tableswitch
     * @param codePos
     *            the code position of the switch operation
     * @return the code positions of the default and all case blocks
     * @throws IOException
     *             if any I/O error occur
     */
    @Nonnull
    private static ArrayList<Integer> readSwitchTargets( @Nonnull CodeInputStream byteCode, boolean isLookupSwitch, int codePos ) throws IOException {
        int padding = byteCode.getCodePosition() % 4;
        if( padding > 0 ) {
            byteCode.skip( 4 - padding );
        }
        ArrayList<Integer> targets = new ArrayList<>();
        targets.add( codePos + byteCode.readInt() ); // default
        if( isLookupSwitch ) {
            for( int count = byteCode.readInt(); count > 0; count-- ) {
                byteCode.readInt(); // key
                targets.add( codePos + byteCode.readInt() );
            }
        } else {
            int low = byteCode.readInt();
            for( int count = byteCode.readInt() - low + 1; count > 0; count-- ) {
                targets.add( codePos + byteCode.readInt() );
            }
        }
        return targets;
    }

    /**
     * If the type use 2 slots in Java (long and double).
     *
     * @param type
     *            the type
     * @return true, if category 2
     */
    private static boolean isCategory2( @Nullable AnyType type ) {
        return type == ValueType.i64 || type == ValueType.f64;
    }

    /**
     * If the type is a reference type including null.
     *
     * @param type
     *            the type
     * @return true, if reference
     */
    private static boolean isRef( @Nonnull AnyType type ) {
        return type == ValueType.eqref || type.isRefType();
    }

    /**
     * Convert a verification type to AnyType.
     *
     * @param vt
     *            the verification type
     * @return the AnyType or null if Top
     * @throws IOException
     *             if any I/O error occur
     */
    @Nullable
    private AnyType getAnyType( @Nonnull StackMapTable.VerificationType vt ) throws IOException {
        switch( vt.getTag() ) {
            case 0: // Top, unusable or not initialized slot
                return null;
            case 1: // Integer
                return ValueType.i32;
            case 2: // Float
                return ValueType.f32;
            case 3: // Double
                return ValueType.f64;
            case 4: // Long
                return ValueType.i64;
            case 5: // Null
                return ValueType.eqref;
            case 6: // UninitializedThis, only in a constructor before the call of the super constructor
                return types.valueOf( className != null ? className : "java/lang/Object" );
            case 7: // Object
                return types.valueOf( vt.getClassName() );
            case 8: // Uninitialized, between "new" and the call of the constructor
                return getNewType( vt.getOffset() );
            default:
                throw new IllegalStateException();
        }
    }

    /**
     * Get the type of the "new" operation at the given code position.
     *
     * @param offset
     *            the code position of the "new" operation
     * @return the type
     * @throws IOException
     *             if any I/O error occur
     */
    @Nonnull
    private AnyType getNewType( int offset ) throws IOException {
        CodeInputStream byteCode = code.getByteCode();
        byteCode.skip( offset );
        int op = byteCode.readUnsignedByte();
        if( op != 187 ) {
            throw new WasmException( "Uninitialized type does not point to a new operation: " + op + " at " + offset, -1 );
        }
        String name = ((ConstantClass)code.getConstantPool().get( byteCode.readUnsignedShort() )).getName();
        return types.valueOf( name );
    }

    /**
     * Mark a variable slot as used with its type.
     * 
     * @param valueType
     *            the type of the local variable
     * @param load
     *            true: if load
     * @param slot
     *            the memory/slot index of the local variable
     * @param javaCodePos
     *            the code position/offset in the Java method
     */
    void use( @Nonnull AnyType valueType, boolean load, int slot, int javaCodePos ) {
        //TODO legacy stub
    }

    /**
     * Mark a variable as used by its WebAssembly index. This is used for synthetic functions of the WatParser
     * (code == null). In this case the slot is equals to the index.
     *
     * @param valueType
     *            the type of the local variable
     * @param wasmIdx
     *            the index of the variable in WebAssembly
     */
    void useIndex( AnyType valueType, int wasmIdx ) {
        while( size <= wasmIdx ) {
            addVariable( size, null, 0, Integer.MAX_VALUE );
        }
        variables[wasmIdx].valueType = valueType;
    }

    /**
     * Add a variable after the flow analysis.
     *
     * @param slot
     *            the slot used in get()
     * @param valueType
     *            the type of the variable
     * @param startCodePosition
     *            the start of the Java code position
     * @param endCodePosition
     *            the end of the Java code position
     * @return the new variable
     */
    @Nonnull
    private Variable addVariable( int slot, AnyType valueType, int startCodePosition, int endCodePosition ) {
        ensureCapacity( size + 1 );
        Variable var = new Variable();
        var.slot = slot;
        var.idx = size;
        var.valueType = valueType;
        var.dirty = false;
        var.startPos = startCodePosition;
        var.endPos = endCodePosition;
        variables[size++] = var;
        return var;
    }

    /**
     * Calculate the WebAssembly index position on the consumed data.
     */
    void calculate() {
        //TODO legacy stub
    }

    /**
     * Get the data types of the local variables. The value is only valid until the next call.
     * 
     * @param paramCount
     *            the count of method parameter which should be exclude
     * @return the reused list with fresh values
     */
    List<AnyType> getLocalTypes( int paramCount ) {
        localTypes.clear();
        for( int i = paramCount; i < size; i++ ) {
            Variable var = variables[i];
            localTypes.add( var.valueType );
        }
        return localTypes;
    }

    /**
     * Get the name of the variable or null if no name available
     * 
     * @param idx
     *            the wasm variable index
     * @return the name
     */
    @Nullable
    String getLocalName( int idx ) {
        return variables[idx].name;
    }

    /**
     * Get the slot of the temporary variable.
     * 
     * @param valueType
     *            the valueType for the variable
     * @param startCodePosition
     *            the start of the Java code position
     * @param endCodePosition
     *            the end of the Java code position
     * @return the slot
     */
    int getTempVariable( AnyType valueType, int startCodePosition, int endCodePosition ) {
        // can we reuse some other temporary local variables?
        // Only temporary variables are reused. The range of the Java variables is not exact, for example in loops.
        for( int i = size - 1; i >= 0; i-- ) {
            Variable var = variables[i];
            if( var.slot >= TEMP_SLOT && var.valueType == valueType && var.endPos < startCodePosition ) {
                var.endPos = endCodePosition;
                return var.slot;
            }
        }
        // the slot of a temporary variable must not conflict with a Java slot
        return addVariable( TEMP_SLOT + size, valueType, startCodePosition, endCodePosition ).slot;
    }

    /**
     * Expand code range for which the variable is valid
     * 
     * @param slot
     *            the memory/slot index of the local variable
     * @param javaCodePos
     *            the new end code position in the Java method
     */
    void expandUse( int slot, int javaCodePos ) {
        Variable var = variables[get( slot, javaCodePos )];
        var.touch( javaCodePos );
    }

    /**
     * Get the WebAssembly variable index of the given Java Slot.
     *
     * @param slot
     *            the memory/slot index of the local variable in Java
     * @param javaCodePos the current code position in the Java method
     * @return the variable index in WebAssembly
     */
    int get( int slot, int javaCodePos ) {
        Variable var = find( positions.get( javaCodePos ) );
        if( var != null && var.slot == slot ) {
            return var.idx;
        }
        if( slot >= TEMP_SLOT ) {
            // the slot of a temporary variable is unique
            for( int i = 0; i < size; i++ ) {
                if( variables[i].slot == slot ) {
                    return i;
                }
            }
        } else {
            for( int i = 0; i < size; i++ ) {
                var = variables[i];
                if( var.slot == slot && var.matchCodePosition( javaCodePos ) ) {
                    return i;
                }
            }

            // there is no variable declared for the given java code position, for example the code position of a
            // dup instruction after a load. We use the variable with the smallest distance.
            int index = -1;
            int distance = Integer.MAX_VALUE;
            for( int i = 0; i < size; i++ ) {
                var = variables[i];
                if( var.slot != slot ) {
                    continue;
                }
                int distanceDiff = Math.min( Math.abs( javaCodePos - var.startPos ), Math.abs( javaCodePos - var.endPos ) );
                if( distanceDiff < distance ) {
                    index = i;
                    distance = distanceDiff;
                }
            }
            if( index >= 0 ) {
                return index;
            }
        }
        throw new WasmException( "No variable for slot " + slot + " at code position " + javaCodePos, -1 );
    }

    /**
     * Get the ValueType of the variable.
     * 
     * @param idx
     *            the index of the local variable in WASM
     * @return the ValueType
     */
    AnyType getValueType( int idx ) {
        return variables[idx].valueType;
    }

    /**
     * Ensure that there is enough capacity.
     * 
     * @param slot
     *            the needed slot
     */
    private void ensureCapacity( int slot ) {
        if( slot >= variables.length ) {
            int i = variables.length;
            variables = Arrays.copyOf( variables, slot + 1 );
            for( ; i < variables.length; i++ ) {
                variables[i] = new Variable();
            }
        }
    }

    /**
     * A value on the stack with its type and the variable from which it was loaded.
     */
    private static class StackValue {

        private final @Nonnull AnyType  type;

        private final @Nullable Variable var;

        /**
         * Create a new instance.
         *
         * @param type
         *            the type of the value
         * @param var
         *            the source variable or null
         */
        StackValue( @Nonnull AnyType type, @Nullable Variable var ) {
            this.type = type;
            this.var = var;
        }
    }

    /**
     * The state of a single local variable slot.
     */
    static class Variable implements Comparable<Variable> {

        private AnyType                   valueType;

        private String                    name;

        /** the index in WebAssembly */
        private int                       idx      = -1;

        /** the slot in Java */
        private int                       slot;

        private int                       startPos;

        private int                       endPos;

        /** the creation order */
        private int                       order;

        /** the variable was merged into this variable */
        private Variable                  mergedInto;

        /** the types of the stored values and the types from the StackMapTable */
        private final ArrayList<AnyType> lowerBounds = new ArrayList<>();

        /** the required types of the consumers */
        private final ArrayList<AnyType> hints       = new ArrayList<>();

        private boolean                   dirty    = true;

        /**
         * If the variable is valid at this position
         *
         * @param codePosition
         *            the position to check
         * @return true, if this variable match
         */
        private boolean matchCodePosition( int codePosition ) {
            return startPos <= codePosition && codePosition <= endPos;
        }

        /**
         * Extend the range of the variable to the code position.
         *
         * @param codePosition
         *            the code position
         */
        private void touch( int codePosition ) {
            startPos = Math.min( startPos, codePosition );
            endPos = Math.max( endPos, codePosition );
        }

        /**
         * Add the type of a stored value. Null values are ignored.
         *
         * @param type
         *            the type
         */
        private void addLowerBound( AnyType type ) {
            if( type != null && type != ValueType.eqref && !lowerBounds.contains( type ) ) {
                lowerBounds.add( type );
                dirty = true;
            }
        }

        /**
         * Add a type that is required by a consumer.
         *
         * @param type
         *            the type
         */
        private void addHint( AnyType type ) {
            if( !hints.contains( type ) ) {
                hints.add( type );
                dirty = true;
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int compareTo( Variable o ) {
            int compare = Integer.compare( idx, o.idx );
            return compare != 0 ? compare : Integer.compare( startPos, o.startPos );
        }
    }
}
