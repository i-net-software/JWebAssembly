/*
 * Copyright 2022 Volker Berlin (i-net software)
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

import java.util.HashMap;
import java.util.List;

import javax.annotation.Nonnull;

import de.inetsoftware.jwebassembly.WasmException;
import de.inetsoftware.jwebassembly.module.StackInspector.StackValue;
import de.inetsoftware.jwebassembly.module.WasmInstruction.Type;
import de.inetsoftware.jwebassembly.wasm.AnyType;
import de.inetsoftware.jwebassembly.wasm.VariableOperator;

/**
 * Replace Unsafe operations with simpler WASM operations which does not need reflections.
 * 
 * In Java a typical Unsafe code look like:
 * 
 * <pre>
 * <code>
 * private static final Unsafe UNSAFE = Unsafe.getUnsafe();
 * private static final long FIELD_OFFSET = UNSAFE.objectFieldOffset(Foobar.class.getDeclaredField("field"));
 * 
 * ...
 * 
 * UNSAFE.compareAndSwapInt(this, FIELD_OFFSET, expect, update);
 * </code>
 * </pre>
 * 
 * Because WASM does not support reflection the native code of UNSAFE can't simple replaced. That this manager convert
 * this to the follow pseudo code in WASM:
 * 
 * <pre>
 * <code>
 * Foobar..compareAndSwapInt(this, FIELD_OFFSET, expect, update);
 * 
 * ...
 * 
 * boolean .compareAndSwapInt(Object obj, long fieldOffset, int expect, int update ) {
 *     if( obj.field == expect ) {
 *         obj.field = update;
 *         return true;
 *     }
 *     return false;
 * }
 * </code>
 * </pre>
 * 
 * @author Volker Berlin
 */
class UnsafeManager {

    /** Unsafe class bane in Java 8 */
    static final String                              UNSAFE_8  = "sun/misc/Unsafe";

    /** Unsafe class bane in Java 11 */
    static final String                              UNSAFE_11 = "jdk/internal/misc/Unsafe";

    @Nonnull
    private final FunctionManager                    functions;

    private final HashMap<FunctionName, UnsafeState> unsafes   = new HashMap<>();

    /**
     * Create an instance of the manager
     * 
     * @param functions
     *            The function manager to register the synthetic functions.
     */
    UnsafeManager( @Nonnull FunctionManager functions ) {
        this.functions = functions;
    }

    /**
     * Replace any Unsafe API call with direct field access.
     * 
     * @param instructions
     *            the instruction list of a function/method
     */
    void replaceUnsafe( List<WasmInstruction> instructions ) {
        // search for Unsafe function calls
        for( int i = 0; i < instructions.size(); i++ ) {
            WasmInstruction instr = instructions.get( i );
            switch( instr.getType() ) {
                case CallVirtual:
                case Call:
                    WasmCallInstruction callInst = (WasmCallInstruction)instr;
                    switch( callInst.getFunctionName().className ) {
                        case UNSAFE_8:
                        case UNSAFE_11:
                            patch( instructions, i, callInst );
                            break;
                    }
                    break;
                default:
            }
        }
    }

    /**
     * Patch in the instruction list an Unsafe method call. It does not change the count of instructions.
     * 
     * @param instructions
     *            the instruction list of a function/method
     * @param idx
     *            the index in the instructions
     * @param callInst
     *            the method call to Unsafe
     */
    private void patch( List<WasmInstruction> instructions, int idx, WasmCallInstruction callInst ) {
        FunctionName name = callInst.getFunctionName();
        switch( name.signatureName ) {
            case "sun/misc/Unsafe.getUnsafe()Lsun/misc/Unsafe;":
            case "jdk/internal/misc/Unsafe.getUnsafe()Ljdk/internal/misc/Unsafe;":
                patch_getUnsafe( instructions, idx );
                break;
            case "sun/misc/Unsafe.objectFieldOffset(Ljava/lang/reflect/Field;)J":
                patch_objectFieldOffset_Java8( instructions, idx, callInst );
                break;
            case "jdk/internal/misc/Unsafe.objectFieldOffset(Ljava/lang/Class;Ljava/lang/String;)J":
                patch_objectFieldOffset_Java11( instructions, idx, callInst );
                break;
            case "sun/misc/Unsafe.arrayBaseOffset(Ljava/lang/Class;)I":
            case "jdk/internal/misc/Unsafe.arrayBaseOffset(Ljava/lang/Class;)I":
                patch_arrayBaseOffset( instructions, idx, callInst );
                break;
            case "sun/misc/Unsafe.arrayIndexScale(Ljava/lang/Class;)I":
            case "jdk/internal/misc/Unsafe.arrayIndexScale(Ljava/lang/Class;)I":
                patch_arrayIndexScale( instructions, idx, callInst );
                break;
            case "sun/misc/Unsafe.getAndAddInt(Ljava/lang/Object;JI)I":
            case "sun/misc/Unsafe.getAndSetInt(Ljava/lang/Object;JI)I":
            case "sun/misc/Unsafe.putOrderedInt(Ljava/lang/Object;JI)V":
            case "sun/misc/Unsafe.getObjectVolatile(Ljava/lang/Object;J)Ljava/lang/Object;":
            case "jdk/internal/misc/Unsafe.getAndAddInt(Ljava/lang/Object;JI)I":
            case "jdk/internal/misc/Unsafe.getAndSetInt(Ljava/lang/Object;JI)I":
            case "jdk/internal/misc/Unsafe.putIntRelease(Ljava/lang/Object;JI)V":
                patchFieldFunction( instructions, idx, callInst, name, 2 );
                break;
            case "sun/misc/Unsafe.compareAndSwapInt(Ljava/lang/Object;JII)Z":
            case "jdk/internal/misc/Unsafe.compareAndSetInt(Ljava/lang/Object;JII)Z":
                patchFieldFunction( instructions, idx, callInst, name, 3 );
                break;
            case "jdk/internal/misc/Unsafe.getLongUnaligned(Ljava/lang/Object;J)J":
            case "jdk/internal/misc/Unsafe.getIntUnaligned(Ljava/lang/Object;J)I":
                patch_getLongUnaligned( instructions, idx, callInst, name );
                break;
            case "jdk/internal/misc/Unsafe.isBigEndian()Z":
                patch_isBigEndian( instructions, idx, callInst );
                break;
            case "jdk/internal/misc/Unsafe.storeFence()V":
                remove( instructions, idx, callInst );
                break;
            default:
                throw new WasmException( "Unsupported Unsafe method: " + name.signatureName, -1 );
        }
    }

    /**
     * Replace a call to Unsafe.getUnsafe() with a NOP operation.
     * 
     * @param instructions
     *            the instruction list of a function/method
     * @param idx
     *            the index in the instructions
     */
    private void patch_getUnsafe( List<WasmInstruction> instructions, int idx ) {
        WasmInstruction instr = instructions.get( idx + 1 );

        int to = idx + (instr.getType() == Type.Global ? 2 : 1);

        nop( instructions, idx, to );
    }

    /**
     * Find the field on which the offset is assign: long FIELD_OFFSET = UNSAFE.objectFieldOffset(...
     * 
     * @param instructions
     *            the instruction list of a function/method
     * @param idx
     *            the index in the instructions
     * @return the state
     */
    @Nonnull
    private UnsafeState findUnsafeState( List<WasmInstruction> instructions, int idx ) {
        // find the field on which the offset is assign: long FIELD_OFFSET = UNSAFE.objectFieldOffset(...
        WasmInstruction instr;
        INSTR: do {
            instr = instructions.get( idx + 1 );
            switch( instr.getType() ) {
                case Convert:
                    idx++;
                    continue INSTR;
                case Global:
                    break;
                default:
                    throw new WasmException( "Unsupported assign operation for Unsafe filed offsetd: " + instr.getType(), -1 );
            }
            break;
        } while( true );
        FunctionName fieldNameWithOffset = ((WasmGlobalInstruction)instr).getFieldName();
        UnsafeState state = unsafes.get( fieldNameWithOffset );
        if( state == null ) {
            unsafes.put( fieldNameWithOffset, state = new UnsafeState() );
        }
        return state;
    }

    /**
     * Get the class name from the stack value. It is searching a WasmConstClassInstruction that produce the value of
     * the stack value.
     * 
     * @param instructions
     *            the instruction list of a function/method
     * @param stackValue
     *            the stack value (instruction and position that produce an stack value)
     * @return the class name like: java/lang/String
     */
    @Nonnull
    private static String getClassConst( List<WasmInstruction> instructions, StackValue stackValue ) {
        WasmInstruction instr = stackValue.instr;
        switch( instr.getType() ) {
            case Local:
                int slot = ((WasmLocalInstruction)instr).getSlot();
                for( int i = stackValue.idx - 1; i >= 0; i-- ) {
                    instr = instructions.get( i );
                    if( instr.getType() == Type.Local ) {
                        WasmLocalInstruction loadInstr = (WasmLocalInstruction)instr;
                        if( loadInstr.getSlot() == slot && loadInstr.getOperator() == VariableOperator.set ) {
                            stackValue = StackInspector.findInstructionThatPushValue( instructions.subList( 0, i ), 1, instr.getCodePosition() );
                            instr = stackValue.instr;
                            break;
                        }

                    }
                }
                break;
            default:
        }
        return ((WasmConstClassInstruction)instr).getValue();
    }

    /**
     * Patch a method call to Unsafe.objectFieldOffset() and find the parameter for other patch operations.
     * 
     * @param instructions
     *            the instruction list
     * @param idx
     *            the index in the instructions
     * @param callInst
     *            the method call to Unsafe
     */
    private void patch_objectFieldOffset_Java8( List<WasmInstruction> instructions, int idx, WasmCallInstruction callInst ) {
        UnsafeState state = findUnsafeState( instructions, idx );

        // objectFieldOffset() has 2 parameters THIS(Unsafe) and a Field
        List<WasmInstruction> paramInstructions = instructions.subList( 0, idx );
        int from = StackInspector.findInstructionThatPushValue( paramInstructions, 2, callInst.getCodePosition() ).idx;

        StackValue stackValue = StackInspector.findInstructionThatPushValue( paramInstructions, 1, callInst.getCodePosition() );
        WasmInstruction instr = stackValue.instr;
        WasmCallInstruction fieldInst = (WasmCallInstruction)instr;

        FunctionName fieldFuncName = fieldInst.getFunctionName();
        switch( fieldFuncName.signatureName ) {
            case "java/lang/Class.getDeclaredField(Ljava/lang/String;)Ljava/lang/reflect/Field;":
                stackValue = StackInspector.findInstructionThatPushValue( instructions.subList( 0, stackValue.idx ), 1, fieldInst.getCodePosition() );
                state.fieldName = ((WasmConstStringInstruction)stackValue.instr).getValue();

                // find the class value on which getDeclaredField is called
                stackValue = StackInspector.findInstructionThatPushValue( instructions.subList( 0, stackValue.idx ), 1, fieldInst.getCodePosition() );
                state.typeName = getClassConst( instructions, stackValue );
                break;

            default:
                throw new WasmException( "Unsupported Unsafe method to get target field: " + fieldFuncName.signatureName, -1 );
        }

        nop( instructions, from, idx + 2 );
    }

    /**
     * Patch a method call to Unsafe.objectFieldOffset() and find the parameter for other patch operations.
     * 
     * @param instructions
     *            the instruction list
     * @param idx
     *            the index in the instructions
     * @param callInst
     *            the method call to Unsafe
     */
    private void patch_objectFieldOffset_Java11( List<WasmInstruction> instructions, int idx, WasmCallInstruction callInst ) {
        UnsafeState state = findUnsafeState( instructions, idx );

        // objectFieldOffset() has 3 parameters THIS(Unsafe), class and the fieldname
        List<WasmInstruction> paramInstructions = instructions.subList( 0, idx );
        int from = StackInspector.findInstructionThatPushValue( paramInstructions, 3, callInst.getCodePosition() ).idx;

        StackValue stackValue = StackInspector.findInstructionThatPushValue( paramInstructions, 1, callInst.getCodePosition() );
        state.fieldName = ((WasmConstStringInstruction)stackValue.instr).getValue();

        // find the class value on which getDeclaredField is called
        stackValue = StackInspector.findInstructionThatPushValue( paramInstructions, 2, callInst.getCodePosition() );
        state.typeName = getClassConst( instructions, stackValue );

        nop( instructions, from, idx + 2 );
    }

    /**
     * Patch a method call to Unsafe.arrayBaseOffset() and find the parameter for other patch operations.
     * 
     * @param instructions
     *            the instruction list
     * @param idx
     *            the index in the instructions
     * @param callInst
     *            the method call to Unsafe
     */
    private void patch_arrayBaseOffset( List<WasmInstruction> instructions, int idx, WasmCallInstruction callInst ) {
        UnsafeState state = findUnsafeState( instructions, idx );

        // objectFieldOffset() has 2 parameters THIS(Unsafe) and a Class from an array
        List<WasmInstruction> paramInstructions = instructions.subList( 0, idx );
        int from = StackInspector.findInstructionThatPushValue( paramInstructions, 2, callInst.getCodePosition() ).idx;

        StackValue stackValue = StackInspector.findInstructionThatPushValue( paramInstructions, 1, callInst.getCodePosition() );
        state.typeName = getClassConst( instructions, stackValue );

        nop( instructions, from, idx );
        // we put the constant value 0 on the stack, we does not need array base offset in WASM
        instructions.set( idx, new WasmConstNumberInstruction( 0, callInst.getCodePosition(), callInst.getLineNumber() ) );
    }

    /**
     * Patch method call to Unsafe.arrayIndexScale()
     * 
     * @param instructions
     *            the instruction list
     * @param idx
     *            the index in the instructions
     * @param callInst
     *            the method call to Unsafe
     */
    private void patch_arrayIndexScale( List<WasmInstruction> instructions, int idx, WasmCallInstruction callInst ) {
        int from = StackInspector.findInstructionThatPushValue( instructions.subList( 0, idx ), 2, callInst.getCodePosition() ).idx;

        nop( instructions, from, idx );
        // we put the constant value 1 on the stack because we does not want shift array positions
        instructions.set( idx, new WasmConstNumberInstruction( 1, callInst.getCodePosition(), callInst.getLineNumber() ) );
    }

    /**
     * Patch an unsafe function that access a field
     * 
     * @param instructions
     *            the instruction list
     * @param idx
     *            the index in the instructions
     * @param callInst
     *            the method call to Unsafe
     * @param name
     *            the calling function
     * @param fieldNameParam
     *            the function parameter with the field offset. This must be a long (Java signature "J"). The THIS
     *            parameter has the index 0.
     */
    private void patchFieldFunction( List<WasmInstruction> instructions, int idx, final WasmCallInstruction callInst, FunctionName name, int fieldNameParam ) {
        StackValue stackValue = StackInspector.findInstructionThatPushValue( instructions.subList( 0, idx ), fieldNameParam, callInst.getCodePosition() );
        FunctionName fieldNameWithOffset = ((WasmGlobalInstruction)stackValue.instr).getFieldName();
        WatCodeSyntheticFunctionName func =
                        new WatCodeSyntheticFunctionName( fieldNameWithOffset.className, '.' + name.methodName, name.signature, "", (AnyType[])null ) {
                            @Override
                            protected String getCode() {
                                UnsafeState state = unsafes.get( fieldNameWithOffset );
                                if( state == null ) {
                                    // we are in the scan phase. The static code was not scanned yet.
                                    return "";
                                }
                                switch( name.methodName ) {
                                    case "compareAndSwapInt":
                                        return "local.get 0" // THIS
                                                        + " struct.get " + state.typeName + ' ' + state.fieldName //
                                                        + " local.get 2" // expected
                                                        + " i32.eq" //
                                                        + " if" //
                                                        + "   local.get 0" // THIS
                                                        + "   local.get 3" // update
                                                        + "   struct.set " + state.typeName + ' ' + state.fieldName //
                                                        + "   i32.const 1" //
                                                        + "   return" //
                                                        + " end" //
                                                        + " i32.const 1" //
                                                        + " return";

                                    case "getAndAddInt":
                                        return "local.get 0" // THIS
                                                        + " local.get 0" // THIS
                                                        + " struct.get " + state.typeName + ' ' + state.fieldName //
                                                        + " local.tee 3" // temp
                                                        + " local.get 2" // delta
                                                        + " i32.add" //
                                                        + " struct.set " + state.typeName + ' ' + state.fieldName //
                                                        + " local.get 3" // temp
                                                        + " return";

                                    case "getAndSetInt":
                                        return "local.get 0" // THIS
                                                        + " struct.get " + state.typeName + ' ' + state.fieldName //
                                                        + " local.get 0" // THIS
                                                        + " local.get 2" // newValue
                                                        + " struct.set " + state.typeName + ' ' + state.fieldName //
                                                        + " return";

                                    case "putOrderedInt":
                                        return "local.get 0" // THIS
                                                        + " local.get 2" // x
                                                        + " struct.set " + state.typeName + ' ' + state.fieldName;
                                }

                                throw new RuntimeException( name.signatureName );
                            }
                        };
        functions.markAsNeeded( func, false );
        WasmCallInstruction call = new WasmCallInstruction( func, callInst.getCodePosition(), callInst.getLineNumber(), callInst.getTypeManager(), false );
        instructions.set( idx, call );

        // a virtual method call has also a DUP of this because we need for virtual method dispatch the parameter 2 times.
        for( int i = idx; i >= 0; i-- ) {
            WasmInstruction instr = instructions.get( i );
            if( instr.getType() == Type.DupThis && ((DupThis)instr).getValue() == callInst ) {
                nop( instructions, i, i + 1 );
                break;
            }
        }
    }

    /**
     * Patch an unsafe function that access a field
     * 
     * @param instructions
     *            the instruction list
     * @param idx
     *            the index in the instructions
     * @param callInst
     *            the method call to Unsafe
     */
    private void patch_getLongUnaligned( List<WasmInstruction> instructions, int idx, final WasmCallInstruction callInst, FunctionName name ) {
        WatCodeSyntheticFunctionName func = new WatCodeSyntheticFunctionName( "", name.methodName, name.signature, "unreachable", (AnyType[])null );
        functions.markAsNeeded( func, false );
        WasmCallInstruction call = new WasmCallInstruction( func, callInst.getCodePosition(), callInst.getLineNumber(), callInst.getTypeManager(), false );
        instructions.set( idx, call );
    }

    /**
     * Patch an unsafe function that access a field
     * 
     * @param instructions
     *            the instruction list
     * @param idx
     *            the index in the instructions
     * @param callInst
     *            the method call to Unsafe
     */
    private void patch_isBigEndian( List<WasmInstruction> instructions, int idx, final WasmCallInstruction callInst ) {
//        int from = StackInspector.findInstructionThatPushValue( instructions.subList( 0, idx ), 1, callInst.getCodePosition() ).idx;
//
//        nop( instructions, from, idx );

        // on x86 use little endian
        instructions.set( idx, new WasmConstNumberInstruction( 0, callInst.getCodePosition(), callInst.getLineNumber() ) );
    }

    /**
     * Patch an unsafe function that access a field
     * 
     * @param instructions
     *            the instruction list
     * @param idx
     *            the index in the instructions
     * @param callInst
     *            the method call to Unsafe
     */
    private void remove( List<WasmInstruction> instructions, int idx, final WasmCallInstruction callInst ) {
        int from = StackInspector.findInstructionThatPushValue( instructions.subList( 0, idx ), 1, callInst.getCodePosition() ).idx;

        nop( instructions, from, idx + 1 );
    }

    /**
     * Replace the instructions with NOP operations
     * 
     * @param instructions
     *            the instruction list
     * @param from
     *            starting index
     * @param to
     *            end index
     */
    private void nop( List<WasmInstruction> instructions, int from, int to ) {
        for( int i = from; i < to; i++ ) {
            WasmInstruction instr = instructions.get( i );
            instructions.set( i, new WasmNopInstruction( instr.getCodePosition(), instr.getLineNumber() ) );
        }
    }

    /**
     * Hold the state from declaring of Unsafe address
     */
    static class UnsafeState {
        String fieldName;

        String typeName;
    }
}
