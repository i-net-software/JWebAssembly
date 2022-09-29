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

    @Nonnull
    private final FunctionManager                    functions;

    private final HashMap<FunctionName, UnsafeState> unsafes = new HashMap<>();

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
                    if( "sun/misc/Unsafe".equals( callInst.getFunctionName().className ) ) {
                        patch( instructions, i, callInst );
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
                patch_getUnsafe( instructions, idx );
                break;
            case "sun/misc/Unsafe.objectFieldOffset(Ljava/lang/reflect/Field;)J":
                patch_objectFieldOffset( instructions, idx, callInst );
                break;
            case "sun/misc/Unsafe.getAndAddInt(Ljava/lang/Object;JI)I":
            case "sun/misc/Unsafe.getAndSetInt(Ljava/lang/Object;JI)I":
            case "sun/misc/Unsafe.putOrderedInt(Ljava/lang/Object;JI)V":
                patchFunction( instructions, idx, callInst, name, 2 );
                break;
            case "sun/misc/Unsafe.compareAndSwapInt(Ljava/lang/Object;JII)Z":
                patchFunction( instructions, idx, callInst, name, 3 );
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

        FunctionName fieldName = ((WasmGlobalInstruction)instr).getFieldName();
        unsafes.putIfAbsent( fieldName, new UnsafeState() );

        nop( instructions, idx, idx + 2 );
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
    private void patch_objectFieldOffset( List<WasmInstruction> instructions, int idx, WasmCallInstruction callInst ) {
        // find the field on which the offset is assign: long FIELD_OFFSET = UNSAFE.objectFieldOffset(...
        WasmInstruction instr = instructions.get( idx + 1 );
        FunctionName fieldNameWithOffset = ((WasmGlobalInstruction)instr).getFieldName();
        UnsafeState state = unsafes.get( fieldNameWithOffset );
        if( state == null ) {
            unsafes.put( fieldNameWithOffset, state = new UnsafeState() );
        }

        // objectFieldOffset() has 2 parameters THIS(Unsafe) and a Field
        int from = StackInspector.findInstructionThatPushValue( instructions.subList( 0, idx ), 2, callInst.getCodePosition() ).idx;

        StackValue stackValue = StackInspector.findInstructionThatPushValue( instructions.subList( 0, idx ), 1, callInst.getCodePosition() );
        instr = stackValue.instr;
        WasmCallInstruction fieldInst = (WasmCallInstruction)instr;

        FunctionName fieldFuncName = fieldInst.getFunctionName();
        switch( fieldFuncName.signatureName ) {
            case "java/lang/Class.getDeclaredField(Ljava/lang/String;)Ljava/lang/reflect/Field;":
                stackValue = StackInspector.findInstructionThatPushValue( instructions.subList( 0, stackValue.idx ), 1, fieldInst.getCodePosition() );
                state.fieldName = ((WasmConstStringInstruction)stackValue.instr).getValue();

                // find the class value on which getDeclaredField is called
                stackValue = StackInspector.findInstructionThatPushValue( instructions.subList( 0, stackValue.idx ), 1, fieldInst.getCodePosition() );
                instr = stackValue.instr;
                switch( instr.getType() ) {
                    case Local:
                        int slot = ((WasmLocalInstruction)instr).getSlot();
                        for( int i = stackValue.idx - 1; i >= 0; i-- ) {
                            instr = instructions.get( i );
                            if( instr.getType() == Type.Local ) {
                                WasmLocalInstruction loadInstr = (WasmLocalInstruction)instr;
                                if( loadInstr.getSlot() == slot && loadInstr.getOperator() == VariableOperator.set ) {
                                    stackValue = StackInspector.findInstructionThatPushValue( instructions.subList( 0, i ), 1, fieldInst.getCodePosition() );
                                    instr = stackValue.instr;
                                }

                            }
                        }
                        break;
                }
                state.typeName = ((WasmConstClassInstruction)instr).getValue();
                break;

            default:
                throw new WasmException( "Unsupported Unsafe method to get target field: " + fieldFuncName.signatureName, -1 );
        }

        nop( instructions, from, idx + 2 );
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
    private void patchFunction( List<WasmInstruction> instructions, int idx, final WasmCallInstruction callInst, FunctionName name, int fieldNameParam ) {
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
