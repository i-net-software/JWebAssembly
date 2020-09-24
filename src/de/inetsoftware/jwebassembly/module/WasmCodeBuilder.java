/*
 * Copyright 2018 - 2020 Volker Berlin (i-net software)
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
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;

import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import de.inetsoftware.classparser.ClassFile;
import de.inetsoftware.classparser.ConstantClass;
import de.inetsoftware.classparser.LocalVariableTable;
import de.inetsoftware.classparser.Member;
import de.inetsoftware.classparser.MethodInfo;
import de.inetsoftware.jwebassembly.WasmException;
import de.inetsoftware.jwebassembly.javascript.NonGC;
import de.inetsoftware.jwebassembly.module.StackInspector.StackValue;
import de.inetsoftware.jwebassembly.module.WasmInstruction.Type;
import de.inetsoftware.jwebassembly.wasm.AnyType;
import de.inetsoftware.jwebassembly.wasm.ArrayOperator;
import de.inetsoftware.jwebassembly.wasm.ArrayType;
import de.inetsoftware.jwebassembly.wasm.MemoryOperator;
import de.inetsoftware.jwebassembly.wasm.NamedStorageType;
import de.inetsoftware.jwebassembly.wasm.NumericOperator;
import de.inetsoftware.jwebassembly.wasm.StructOperator;
import de.inetsoftware.jwebassembly.wasm.ValueType;
import de.inetsoftware.jwebassembly.wasm.ValueTypeParser;
import de.inetsoftware.jwebassembly.wasm.VariableOperator;
import de.inetsoftware.jwebassembly.wasm.WasmBlockOperator;

/**
 * Base class for Code Building.
 *
 * @author Volker Berlin
 */
public abstract class WasmCodeBuilder {

    private final LocaleVariableManager localVariables = new LocaleVariableManager();

    private final List<WasmInstruction> instructions   = new ArrayList<>();

    private TypeManager                 types;

    private FunctionManager             functions;

    private WasmOptions                 options;

    private StringManager               strings;

    private ClassFileLoader             classFileLoader;

    /**
     * Initialize the code builder;
     * 
     * @param options
     *            compiler properties
     * @param classFileLoader
     *            for loading the class files
     */
    void init( WasmOptions options, ClassFileLoader classFileLoader ) {
        this.localVariables.init( options.types );
        this.types = options.types;
        this.functions = options.functions;
        this.strings = options.strings; 
        this.options = options;
        this.classFileLoader = classFileLoader;
    }

    /**
     * Get the list of instructions
     * 
     * @return the list
     */
    @Nonnull
    List<WasmInstruction> getInstructions() {
        return instructions;
    }

    /**
     * Get the manager of local variables
     * @return the manager
     */
    @Nonnull
    LocaleVariableManager getLocalVariables() {
        return localVariables;
    }

    /**
     * Get the compiler settings
     * 
     * @return the settings
     */
    @Nonnull
    WasmOptions getOptions() {
        return options;
    }

    /**
     * Check if the last instruction is a return instruction
     * 
     * @return true, if a return
     */
    boolean isEndsWithReturn() {
        WasmInstruction instr = instructions.get( instructions.size() - 1 );
        if( instr.getType() == Type.Block ) {
            return ((WasmBlockInstruction)instr).getOperation() == WasmBlockOperator.RETURN;
        }
        return false;
    }

    /**
     * We need one value from the stack inside of a block. We need to find the WasmInstruction on which the block can
     * start. If this a function call or numeric expression this can be complex to find the right point.
     * 
     * @param count
     *            the count of values on the stack back. 1 means the last value. 2 means the penultimate value.
     * @return the code position that push the last instruction
     */
    int findBlockStartCodePosition( int count ) {
        return findBlockStart( count, true );
    }

    /**
     * We need one value from the stack inside of a block. We need to find the WasmInstruction on which the block can
     * start. If this a function call or numeric expression this can be complex to find the right point.
     * 
     * @param count
     *            the count of values on the stack back. 1 means the last value. 2 means the penultimate value.
     * @param codePosition
     *            true, get the code position; false, get the index in the instructions
     * @return the code position that push the last instruction
     */
    private int findBlockStart( int count, boolean codePosition ) {
        int valueCount = 0;
        List<WasmInstruction> instructions = this.instructions;
        for( int i = instructions.size() - 1; i >= 0; i-- ) {
            WasmInstruction instr = instructions.get( i );
            AnyType valueType = instr.getPushValueType();
            if( valueType != null ) {
                valueCount++;
            }
            valueCount -= instr.getPopCount();
            if( valueCount == count ) {
                return codePosition ? instr.getCodePosition() : i;
            }
        }
        throw new WasmException( "Start position not found", -1 ); // should never occur
    }

    /**
     * We need the value type from the stack.
     * 
     * @param count
     *            the count of values on the stack back. 1 means the last value. 2 means the penultimate value.
     * @param javaCodePos
     *            current code position for which the stack is inspected
     * @return the type of the last push value
     */
    @Nonnull
    AnyType findValueTypeFromStack( int count, int javaCodePos ) {
        return StackInspector.findInstructionThatPushValue( instructions, count, javaCodePos ).instr.getPushValueType();
    }

    /**
     * Find the instruction that push the x-th value to the stack.
     * 
     * @param count
     *            the count of values on the stack back. 1 means the last value. 2 means the penultimate value.
     * @param javaCodePos
     *            current code position for which the stack is inspected
     * @return the instruction
     */
    private WasmInstruction findInstructionThatPushValue( int count, int javaCodePos ) {
        return StackInspector.findInstructionThatPushValue( instructions, count, javaCodePos ).instr;
    }

    /**
     * Get the data types of the local variables. The value is only valid until the next call.
     * 
     * @param paramCount
     *            the count of method parameter which should be exclude
     * @return the reused list with fresh values
     */
    List<AnyType> getLocalTypes( int paramCount ) {
        return localVariables.getLocalTypes( paramCount );
    }

    /**
     * Get the name of the variable or null if no name available
     * 
     * @param idx
     *            the wasm variable index
     * @return the name
     */
    String getLocalName( int idx ) {
        return localVariables.getLocalName( idx );
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
        return localVariables.getTempVariable( valueType, startCodePosition, endCodePosition );
    }

    /**
     * Get the type manager.
     * 
     * @return the type manager
     */
    protected TypeManager getTypeManager() {
        return types;
    }

    /**
     * Reset the code builder.
     * 
     * @param variableTable
     *            variable table of the Java method.
     * @param method
     *            the method with signature as fallback for a missing variable table. If null signature is used and the method must be static.
     * @param signature
     *            alternative for method signature, can be null if method is set
     */
    protected void reset( LocalVariableTable variableTable, MethodInfo method, Iterator<AnyType> signature ) {
        instructions.clear();
        localVariables.reset( variableTable, method, signature );
    }

    /**
     * Calculate the index of the variables
     */
    protected void calculateVariables() {
        localVariables.calculate();
    }

    /**
     * Create a WasmLoadStoreInstruction.
     * 
     * @param valueType
     *            the value type
     * @param load
     *            true: if load
     * @param javaIdx
     *            the memory/slot index of the variable in Java byte code
     * @param javaCodePos
     *            the code position/offset in the Java method
     * @param lineNumber
     *            the line number in the Java source code
     */
    protected void addLoadStoreInstruction( AnyType valueType, boolean load, @Nonnegative int javaIdx, int javaCodePos, int lineNumber ) {
        localVariables.use( valueType, javaIdx, javaCodePos );
        instructions.add( new WasmLoadStoreInstruction( load ? VariableOperator.get : VariableOperator.set, javaIdx, localVariables, javaCodePos, lineNumber ) );
    }

    /**
     * Create a WasmLoadStoreInstruction local.get/local.set.
     * 
     * @param op
     *            the operation
     * @param wasmIdx
     *            the index of the variable
     * @param javaCodePos
     *            the code position/offset in the Java method
     * @param lineNumber
     *            the line number in the Java source code
     */
    protected void addLocalInstruction( VariableOperator op, @Nonnegative int wasmIdx, int javaCodePos, int lineNumber ) {
        switch( op ) {
            case set:
            case tee:
                AnyType valueType = findValueTypeFromStack( 1, javaCodePos );
                localVariables.useIndex( valueType, wasmIdx );
        }
        instructions.add( new WasmLocalInstruction( op, wasmIdx, localVariables, javaCodePos, lineNumber ) );
    }

    /**
     * Create a WasmDupInstruction.
     * 
     * @param javaCodePos
     *            the code position/offset in the Java method
     * @param lineNumber
     *            the line number in the Java source code
     */
    protected void addDupInstruction( int javaCodePos, int lineNumber ) {
        WasmInstruction instr = findInstructionThatPushValue( 1, javaCodePos );
        AnyType type = instr.getPushValueType();
        int varIndex = -1;
        // if it is a GET to a local variable then we can use it
        if( instr.getType() == Type.Local ) {
            WasmLocalInstruction local1 = (WasmLocalInstruction)instr;
            if( local1.getOperator() == VariableOperator.get ) {
                varIndex = local1.getIndex();
            }
        }
        //alternate we need to create a new locale variable
        if( varIndex < 0 ) {
            varIndex = getTempVariable( type, javaCodePos, javaCodePos + 1 );
            instructions.add( new WasmLoadStoreInstruction( VariableOperator.tee, varIndex, localVariables, javaCodePos, lineNumber ) );
            instructions.add( new WasmLoadStoreInstruction( VariableOperator.get, varIndex, localVariables, javaCodePos, lineNumber ) );
            // an alternative solution can be a function call with multiple return values but this seems to be slower
            // new SyntheticFunctionName( "dup" + storeType, "local.get 0 local.get 0 return", storeType, null, storeType, storeType ), codePos, lineNumber )
        } else {
            instructions.add( new WasmLocalInstruction( VariableOperator.get, varIndex, localVariables, javaCodePos, lineNumber ) );
        }
    }

    /**
     * Simulate the dup_x1 Java byte code instruction.<p>
     * 
     * ..., value2, value1 → ..., value1, value2, value1
     * 
     * @param javaCodePos
     *            the code position/offset in the Java method
     * @param lineNumber
     *            the line number in the Java source code
     */
    protected void addDupX1Instruction( int javaCodePos, int lineNumber ) {
        AnyType type1 = findValueTypeFromStack( 1, javaCodePos );
        AnyType type2 = findValueTypeFromStack( 2, javaCodePos );

        int varIndex1 = getTempVariable( type1, javaCodePos, javaCodePos + 1 );
        int varIndex2 = getTempVariable( type2, javaCodePos, javaCodePos + 1 );

        // save in temp variables
        instructions.add( new WasmLocalInstruction( VariableOperator.set, varIndex1, localVariables, javaCodePos, lineNumber ) );
        instructions.add( new WasmLocalInstruction( VariableOperator.set, varIndex2, localVariables, javaCodePos, lineNumber ) );

        // and restore it in new order on the stack
        instructions.add( new WasmLocalInstruction( VariableOperator.get, varIndex1, localVariables, javaCodePos, lineNumber ) );
        instructions.add( new WasmLocalInstruction( VariableOperator.get, varIndex2, localVariables, javaCodePos, lineNumber ) );
        instructions.add( new WasmLocalInstruction( VariableOperator.get, varIndex1, localVariables, javaCodePos, lineNumber ) );
    }

    /**
     * Simulate the dup_x2 Java byte code instruction.<p>
     * 
     * ..., value3, value2, value1 → ..., value1, value3, value2, value1
     * 
     * @param javaCodePos
     *            the code position/offset in the Java method
     * @param lineNumber
     *            the line number in the Java source code
     */
    protected void addDupX2Instruction( int javaCodePos, int lineNumber ) {
        AnyType type1 = findValueTypeFromStack( 1, javaCodePos );
        AnyType type2 = findValueTypeFromStack( 2, javaCodePos );
        AnyType type3 = findValueTypeFromStack( 3, javaCodePos );

        int varIndex1 = getTempVariable( type1, javaCodePos, javaCodePos + 1 );
        int varIndex2 = getTempVariable( type2, javaCodePos, javaCodePos + 1 );
        int varIndex3 = getTempVariable( type3, javaCodePos, javaCodePos + 1 );

        // save in temp variables
        instructions.add( new WasmLocalInstruction( VariableOperator.set, varIndex1, localVariables, javaCodePos, lineNumber ) );
        instructions.add( new WasmLocalInstruction( VariableOperator.set, varIndex2, localVariables, javaCodePos, lineNumber ) );
        instructions.add( new WasmLocalInstruction( VariableOperator.set, varIndex3, localVariables, javaCodePos, lineNumber ) );

        // and restore it in new order on the stack
        instructions.add( new WasmLocalInstruction( VariableOperator.get, varIndex1, localVariables, javaCodePos, lineNumber ) );
        instructions.add( new WasmLocalInstruction( VariableOperator.get, varIndex3, localVariables, javaCodePos, lineNumber ) );
        instructions.add( new WasmLocalInstruction( VariableOperator.get, varIndex2, localVariables, javaCodePos, lineNumber ) );
        instructions.add( new WasmLocalInstruction( VariableOperator.get, varIndex1, localVariables, javaCodePos, lineNumber ) );
    }

    /**
     * Add a global instruction
     * 
     * @param load
     *            true: if load
     * @param ref
     *            reference to a static field
     * @param javaCodePos
     *            the code position/offset in the Java method
     * @param lineNumber
     *            the line number in the Java source code
     */
    protected void addGlobalInstruction( boolean load, Member ref, int javaCodePos, int lineNumber ) {
        FunctionName name = new FunctionName( ref );
        AnyType type = new ValueTypeParser( ref.getType(), types ).next();
        instructions.add( new WasmGlobalInstruction( load, name, type, javaCodePos, lineNumber ) );
        functions.markClassAsUsed( name.className );
    }

    /**
     * Add a WasmTableInstruction table.get/table.set.
     * 
     * @param load
     *            true: if load
     * @param idx
     *            the index of the table
     * @param javaCodePos
     *            the code position/offset in the Java method
     * @param lineNumber
     *            the line number in the Java source code
     */
    protected void addTableInstruction( boolean load, @Nonnegative int idx, int javaCodePos, int lineNumber ) {
        instructions.add( new WasmTableInstruction( load, idx, javaCodePos, lineNumber ) );
    }

    /**
     * Add a constant instruction.
     * 
     * @param value
     *            the value
     * @param valueType
     *            the value type
     * @param javaCodePos
     *            the code position/offset in the Java method
     * @param lineNumber
     *            the line number in the Java source code
     */
    protected void addConstInstruction( Number value, ValueType valueType, int javaCodePos, int lineNumber ) {
        instructions.add( new WasmConstInstruction( value, valueType, javaCodePos, lineNumber ) );
    }

    /**
     * Add a constant instruction with unknown value type.
     * 
     * @param value
     *            the value
     * @param javaCodePos
     *            the code position/offset in the Java method
     * @param lineNumber
     *            the line number in the Java source code
     */
    protected void addConstInstruction( Object value, int javaCodePos, int lineNumber ) {
        if( value.getClass() == String.class ) {
            Integer id = strings.get( value );
            FunctionName name = strings.getStringConstantFunction();
            instructions.add( new WasmConstInstruction( id, ValueType.i32, javaCodePos, lineNumber ) );
            String comment = (String)value;
            if( !isAscii( comment ) ) {
                comment = null;
            }
            instructions.add( new WasmCallInstruction( name, javaCodePos, lineNumber, types, false, comment ) );
        } else if( value instanceof Number ) {
            instructions.add( new WasmConstInstruction( (Number)value, javaCodePos, lineNumber ) );
        } else if( value instanceof ConstantClass ) {
            String className = ((ConstantClass)value).getName();
            Integer id = types.valueOf( className ).getClassIndex();
            FunctionName name = types.getClassConstantFunction();
            instructions.add( new WasmConstInstruction( id, ValueType.i32, javaCodePos, lineNumber ) );
            addCallInstruction( name, javaCodePos, lineNumber );
        } else {
            //TODO There can be ConstantClass, MethodType and MethodHandle
            throw new WasmException( "Class constants are not supported. : " + value, lineNumber );
        }
    }

    /**
     * if the string contains only ASCCI characters
     * 
     * @param str
     *            the staring
     * @return true, if only ASCII
     */
    private static boolean isAscii( String str ) {
        int length = str.length();
        for( int i = 0; i < length; i++ ) {
            int c = str.charAt( i );
            if( c >= 0x20 && c < 0x7F ) {
                continue;
            }
            return false;
        }
        return true;
    }

    /**
     * Add a numeric operation instruction
     * 
     * @param numOp
     *            the operation
     * @param valueType
     *            the value type
     * @param javaCodePos
     *            the code position/offset in the Java method
     * @param lineNumber
     *            the line number in the Java source code
     * @return the added instruction
     */
    protected WasmNumericInstruction addNumericInstruction( @Nullable NumericOperator numOp, @Nullable ValueType valueType, int javaCodePos, int lineNumber ) {
        WasmNumericInstruction numeric = new WasmNumericInstruction( numOp, valueType, javaCodePos, lineNumber );
        instructions.add( numeric );
        if( !options.useGC() && options.ref_eq == null && (numOp == NumericOperator.ref_eq || numOp == NumericOperator.ref_ne ) ) {
            functions.markAsNeeded( options.ref_eq = getNonGC( "ref_eq", lineNumber ) );
        }
        return numeric;
    }

    /**
     * Add a value convert/cast instruction.
     * 
     * @param conversion
     *            the conversion
     * @param javaCodePos
     *            the code position/offset in the Java method
     * @param lineNumber
     *            the line number in the Java source code
     */
    protected void addConvertInstruction( ValueTypeConvertion conversion, int javaCodePos, int lineNumber ) {
        instructions.add( new WasmConvertInstruction( conversion, javaCodePos, lineNumber ) );
    }

    /**
     * Add a static function call.
     * 
     * @param name
     *            the function name that should be called
     * @param javaCodePos
     *            the code position/offset in the Java method
     * @param lineNumber
     *            the line number in the Java source code
     */
    protected void addCallInstruction( FunctionName name, int javaCodePos, int lineNumber ) {
        name = functions.markAsNeeded( name );
        boolean needThisParameter = functions.needThisParameter( name );
        WasmCallInstruction instruction = new WasmCallInstruction( name, javaCodePos, lineNumber, types, needThisParameter );

        if( "<init>".equals( name.methodName ) ) {
            // check if there a factory for the constructor in JavaScript then we need to do some more complex patching
            Function<String, Object> importAnannotation = functions.getImportAnannotation( name );
            FunctionName factoryName = null;

            if( importAnannotation != null ) { // JavaScript replacement for a constructor via import
                // The new signature need a return value. The <init> of Java has ever a void return value
                String signature = name.signature;
                signature = signature.substring( 0, signature.length() - 1 ) + "Ljava/lang/Object;";
                factoryName = new ImportSyntheticFunctionName( "String", "init", signature, importAnannotation );
            } else {
                MethodInfo replace = functions.replace( name, null );
                if( replace != null && !"<init>".equals( replace.getName() ) ) {
                    // the constructor was replaced with a factory method. Typical this method called then a JavaScript replacement
                    factoryName = new FunctionName( replace );
                }
            }

            if( factoryName != null ) {
                // the constructor was replaced we need also replace the create instance instruction
                List<WasmInstruction> instructions = this.instructions;
                for( int i = instructions.size() - 1; i >= 0; i-- ) {
                    WasmInstruction instr = instructions.get( i );
                    if( instr.getType() == Type.Struct ) {
                        WasmStructInstruction struct = (WasmStructInstruction)instr;
                        if( struct.getOperator() == StructOperator.NEW_DEFAULT ) {
                            instructions.set( i, new WasmNopInstruction( struct.getCodePosition(), struct.getLineNumber() ) ); // replace NEW_DEFAULT with Nop, Nop because the code position can be needed for the branch manager
                            instr = instructions.get( ++i );
//                            if( instr.getType() == Type.Dup ) {
//                                instructions.remove( i ); // dup of the instance reference if it is later assign, missing if the object after the constructor is never assign
//                            }
                            break;
                        }
                    }
                }
                // the new instruction
                instruction = new WasmCallInstruction( factoryName, javaCodePos, lineNumber, types, false );
            }
        }

        instructions.add( instruction );
        functions.markClassAsUsed( name.className );
    }

    /**
     * Add indirect call to the instruction.
     * 
     * @param indirectCall
     *            the instruction
     */
    private void addCallIndirectInstruction( WasmCallIndirectInstruction indirectCall ) {
        //  For access to the vtable the THIS parameter must be duplicated on stack before the function parameters 

        // find the instruction that THIS push on the stack 
        int count = indirectCall.getPopCount();
        int javaCodePos = indirectCall.getCodePosition();
        StackValue stackValue = StackInspector.findInstructionThatPushValue( instructions, count, javaCodePos );
        WasmInstruction instr = stackValue.instr;
        int varIndex = -1;
        // if it is a GET to a local variable then we can use it
        if( instr.getType() == Type.Local ) {
            WasmLocalInstruction local1 = (WasmLocalInstruction)instr;
            if( local1.getOperator() == VariableOperator.get ) {
                varIndex = local1.getIndex();
            }
        }
        //alternate we need to create a new locale variable
        if( varIndex < 0 ) {
            varIndex = getTempVariable( indirectCall.getThisType(), instr.getCodePosition(), javaCodePos + 1 );
            int idx = count == 1 ? instructions.size() : stackValue.idx + 1;
            instructions.add( idx, new DupThis( indirectCall, varIndex, instr.getCodePosition() + 1 ) );
        }
        indirectCall.setVariableIndexOfThis( varIndex );
        instructions.add( indirectCall );
        options.registerGet_i32(); // for later access of the vtable
    }

    /**
     * Add a virtual/method function call.
     * 
     * @param name
     *            the function name that should be called
     * @param javaCodePos
     *            the code position/offset in the Java method
     * @param lineNumber
     *            the line number in the Java source code
     */
    protected void addCallVirtualInstruction( FunctionName name, int javaCodePos, int lineNumber ) {
        name = functions.markAsNeeded( name );
        addCallIndirectInstruction( new WasmCallVirtualInstruction( name, javaCodePos, lineNumber, types, options ) );
        options.getCallVirtual(); // mark the function as needed
        functions.markClassAsUsed( name.className );
    }

    /**
     * Add interface function call
     * @param name
     *            the function name that should be called
     * @param javaCodePos
     *            the code position/offset in the Java method
     * @param lineNumber
     *            the line number in the Java source code
     */
    protected void addCallInterfaceInstruction( FunctionName name, int javaCodePos, int lineNumber ) {
        name = functions.markAsNeeded( name );
        addCallIndirectInstruction( new WasmCallInterfaceInstruction( name, javaCodePos, lineNumber, types, options ) );
        options.getCallInterface(); // mark the function as needed
        functions.markClassAsUsed( name.className );
    }

    /**
     * Add a block operation.
     * 
     * @param op
     *            the operation
     * @param data
     *            extra data for some operations
     * @param javaCodePos
     *            the code position/offset in the Java method
     * @param lineNumber
     *            the line number in the Java source code
     */
    protected void addBlockInstruction( WasmBlockOperator op, @Nullable Object data, int javaCodePos, int lineNumber ) {
        instructions.add( new WasmBlockInstruction( op, data, javaCodePos, lineNumber ) );
    }

    /**
     * Add a no operation to the instruction list as marker on the code position. This instruction will not be write to
     * the output.
     * 
     * @param javaCodePos
     *            the code position/offset in the Java method
     * @param lineNumber
     *            the line number in the Java source code
     */
    protected void addNopInstruction( int javaCodePos, int lineNumber ) {
        instructions.add( new WasmNopInstruction( javaCodePos, lineNumber ) );
    }

    /**
     * Add a Jump instruction for later stack inspection
     * 
     * @param jumpPos
     *            the position of the jump
     * @param popCount
     *            the the count of values that are removed from the stack.
     * @param pushValueType
     *            optional type of a push value
     * @param javaCodePos
     *            the code position/offset in the Java method
     * @param lineNumber
     *            the line number in the Java source code
     */
    protected void addJumpPlaceholder( int jumpPos, int popCount, AnyType pushValueType, int javaCodePos, int lineNumber ) {
        instructions.add( new JumpInstruction( jumpPos, popCount, pushValueType, javaCodePos, lineNumber ) );
    }

    /**
     * Get a non GC polyfill function.
     * @param name the function name
     * @param lineNumber the line number for a possible error
     * @return the function name
     */
    private FunctionName getNonGC( String name, int lineNumber ) {
        try {
            ClassFile classFile = classFileLoader.get( NonGC.class.getName().replace( '.', '/' ) );
            for( MethodInfo method : classFile.getMethods() ) {
                if( name.equals( method.getName() ) ) {
                    return new FunctionName( method );
                }
            }
        } catch( IOException ex ) {
            throw WasmException.create( ex, lineNumber );
        }
        throw new WasmException( "Not implemented NonGC polyfill function: " + name, lineNumber );
    }

    /**
     * Add an array operation to the instruction list as marker on the code position.
     * 
     * @param op
     *            the operation
     * @param type
     *            the array/component type of the array
     * @param javaCodePos
     *            the code position/offset in the Java method
     * @param lineNumber
     *            the line number in the Java source code
     */
    protected void addArrayInstruction( ArrayOperator op, AnyType type, int javaCodePos, int lineNumber ) {
        WasmArrayInstruction arrayInst = new WasmArrayInstruction( op, type, types, javaCodePos, lineNumber );
        instructions.add( arrayInst );
        if( !options.useGC() ) {
            SyntheticFunctionName name = arrayInst.createNonGcFunction();
            if( name != null ) {
                functions.markAsNeeded( name );
                functions.markAsImport( name, name.getAnnotation() );
            }
        }
    }

    /**
     * Add a new multi dimensional array instruction
     * 
     * @param dim
     *            the dimension of the array &gt;= 2
     * @param typeName
     *            the full type name
     * @param javaCodePos
     *            the code position/offset in the Java method
     * @param lineNumber
     *            the line number in the Java source code
     */
    protected void addMultiNewArrayInstruction( int dim, String typeName, int javaCodePos, int lineNumber ) {
        ArrayType type = (ArrayType)new ValueTypeParser( typeName, types ).next();
        addMultiNewArrayInstruction( dim, type, javaCodePos, lineNumber );
    }

    /**
     * Add a new multi dimensional array instruction
     * 
     * @param dim
     *            the dimension of the array &gt;= 2
     * @param type
     *            the full type
     * @param javaCodePos
     *            the code position/offset in the Java method
     * @param lineNumber
     *            the line number in the Java source code
     */
    protected void addMultiNewArrayInstruction( int dim, ArrayType type, int javaCodePos, int lineNumber ) {
        MultiArrayFunctionName name = new MultiArrayFunctionName( dim, type );
        addCallInstruction( name, javaCodePos, lineNumber );
    }

    /**
     * Add a struct/object operation to the instruction list.
     * 
     * @param op
     *            the operation
     * @param typeName
     *            the type name like "java/lang/Object"
     * @param fieldName
     *            the name of field if needed for the operation
     * @param javaCodePos
     *            the code position/offset in the Java method
     * @param lineNumber
     *            the line number in the Java source code
     */
    protected void addStructInstruction( StructOperator op, @Nonnull String typeName, @Nullable NamedStorageType fieldName, int javaCodePos, int lineNumber ) {
        WasmStructInstruction structInst = new WasmStructInstruction( op, typeName, fieldName, javaCodePos, lineNumber, types );
        instructions.add( structInst );
        if( !options.useGC() || op == StructOperator.CAST || op == StructOperator.INSTANCEOF ) {
            SyntheticFunctionName name = structInst.createNonGcFunction();
            if( name != null ) {
                functions.markAsNeeded( name );
                functions.markAsImport( name, name.getAnnotation() );
            }
        }
    }

    /**
     * Create an instance of a load/store to the linear memory instruction
     * 
     * @param op
     *            the operation
     * @param type
     *            the type of the static field
     * @param offset
     *            the base offset which will be added to the offset value on the stack
     * @param alignment
     *            the alignment of the value on the linear memory (0: 8 Bit; 1: 16 Bit; 2: 32 Bit)
     * @param javaCodePos
     *            the code position/offset in the Java method
     * @param lineNumber
     *            the line number in the Java source code
     */
    protected void addMemoryInstruction( MemoryOperator op, ValueType type, int offset, int alignment, int javaCodePos, int lineNumber ) {
        instructions.add( new WasmMemoryInstruction( op, type, offset, alignment, javaCodePos, lineNumber ) );
    }
}
