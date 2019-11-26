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
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import de.inetsoftware.classparser.ClassFile;
import de.inetsoftware.classparser.LocalVariableTable;
import de.inetsoftware.classparser.Member;
import de.inetsoftware.classparser.MethodInfo;
import de.inetsoftware.jwebassembly.WasmException;
import de.inetsoftware.jwebassembly.javascript.NonGC;
import de.inetsoftware.jwebassembly.module.WasmInstruction.Type;
import de.inetsoftware.jwebassembly.wasm.AnyType;
import de.inetsoftware.jwebassembly.wasm.ArrayOperator;
import de.inetsoftware.jwebassembly.wasm.MemoryOperator;
import de.inetsoftware.jwebassembly.wasm.NamedStorageType;
import de.inetsoftware.jwebassembly.wasm.NumericOperator;
import de.inetsoftware.jwebassembly.wasm.StructOperator;
import de.inetsoftware.jwebassembly.wasm.ValueType;
import de.inetsoftware.jwebassembly.wasm.ValueTypeParser;
import de.inetsoftware.jwebassembly.wasm.VariableOperator;
import de.inetsoftware.jwebassembly.wasm.WasmBlockOperator;
import de.inetsoftware.jwebassembly.wasm.WasmOptions;

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

    /**
     * Initialize the code builder;
     * 
     * @param types
     *            the type manager
     * @param functions
     *            the function manager
     * @param strings
     *            the string manager
     * @param options
     *            compiler properties
     */
    void init( TypeManager types, FunctionManager functions, StringManager strings, WasmOptions options ) {
        this.localVariables.init( types );
        this.types = types;
        this.functions = functions;
        this.strings = strings; 
        this.options = options;
    }

    /**
     * Get the list of instructions
     * 
     * @return the list
     */
    List<WasmInstruction> getInstructions() {
        return instructions;
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
    int findPushInstructionCodePosition( int count ) {
        return findPushInstruction( count, true );
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
    private int findPushInstruction( int count, boolean codePosition ) {
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
     * @return the type of the last push value
     */
    @Nonnull
    AnyType findValueTypeFromStack( int count ) {
        int valueCount = 0;
        List<WasmInstruction> instructions = this.instructions;
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
     *            the method with signature as fallback for a missing variable table
     */
    protected void reset( LocalVariableTable variableTable, MethodInfo method ) {
        instructions.clear();
        localVariables.reset( variableTable, method );
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
        instructions.add( new WasmLoadStoreInstruction( load, javaIdx, localVariables, javaCodePos, lineNumber ) );
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
                AnyType valueType = findValueTypeFromStack( 1 );
                localVariables.useIndex( valueType, wasmIdx );
        }
        instructions.add( new WasmLocalInstruction( op, wasmIdx, javaCodePos, lineNumber ) );
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
        AnyType type = findValueTypeFromStack( 1 );
        int idx = getTempVariable( type, javaCodePos, javaCodePos + 1 );
        instructions.add( new WasmDupInstruction( idx, type, localVariables, javaCodePos, lineNumber ) );
        // an alternative solution can be a function call with multiple return values but this seems to be slower
        // new SyntheticFunctionName( "dup" + storeType, "local.get 0 local.get 0 return", storeType, null, storeType, storeType ), codePos, lineNumber )
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
    @Nonnull
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
            if( id == null ) {
                strings.put( (String)value, id = strings.size() );
            }
            FunctionName name = strings.getStringConstantFunction();
            instructions.add( new WasmConstInstruction( id, ValueType.i32, javaCodePos, lineNumber ) );
            addCallInstruction( name, javaCodePos, lineNumber );
        } else {
            instructions.add( new WasmConstInstruction( (Number)value, javaCodePos, lineNumber ) );
        }
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
        if( !options.useGC() && numOp == NumericOperator.ref_eq ) {
            functions.markAsNeeded( options.ref_eq = getNonGC( "ref_eq", lineNumber ), true );
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
        WasmCallInstruction instruction = new WasmCallInstruction( name, javaCodePos, lineNumber, types );

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
                            if( instr.getType() == Type.Dup ) {
                                instructions.remove( i ); // dup of the instance reference if it is later assign, missing if the object after the constructor is never assign
                            }
                            break;
                        }
                    }
                }
                // the new instruction
                instruction = new WasmCallInstruction( factoryName, javaCodePos, lineNumber, types );
            }
        }

        instructions.add( instruction );
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
        WasmCallIndirectInstruction virtualCall = new WasmCallIndirectInstruction( name, javaCodePos, lineNumber, types, options );
        int count = virtualCall.getPopCount();
        int idx = findPushInstruction( count, false );
        WasmInstruction instr = instructions.get( idx );
        int varIndex = -1; 
        if( instr.getType() == Type.Local ) {
            WasmLocalInstruction local1 = (WasmLocalInstruction)instr;
            if( local1.getOperator() == VariableOperator.get ) {
                varIndex = local1.getIndex();
            }
        }
        if( varIndex < 0 ) {
            varIndex = getTempVariable( virtualCall.getThisType(), instr.getCodePosition(), javaCodePos + 1 );
            idx = count == 1 ? instructions.size() : findPushInstruction( count - 1, false );
            instructions.add( idx, new DupThis( virtualCall, varIndex, javaCodePos ) );
        }
        virtualCall.setVariableIndexOfThis( varIndex );
        instructions.add( virtualCall );
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
     * Get a non GC polyfill function.
     * @param name the function name
     * @param lineNumber the line number for a possible error
     * @return the function name
     */
    private FunctionName getNonGC( String name, int lineNumber ) {
        try {
            ClassFile classFile = ClassFile.get( NonGC.class.getName().replace( '.', '/' ), getClass().getClassLoader() );
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
     *            the array type
     * @param javaCodePos
     *            the code position/offset in the Java method
     * @param lineNumber
     *            the line number in the Java source code
     */
    protected void addArrayInstruction( ArrayOperator op, AnyType type, int javaCodePos, int lineNumber ) {
        if( options.useGC() ) {
            instructions.add( new WasmArrayInstruction( op, type, types, javaCodePos, lineNumber ) );
        } else {
            if( type.getCode() >= 0 ) {
                type = ValueType.anyref;
            }
            String api = "array_" + op.toString().toLowerCase() + "_" + type;
            FunctionName name = getNonGC( api, lineNumber );
            addCallInstruction( name, javaCodePos, lineNumber );
        }
    }

    /**
     * Add an array operation to the instruction list as marker on the code position.
     * 
     * @param op
     *            the operation
     * @param typeName
     *            the type name
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
        if( !options.useGC() ) {
            SyntheticFunctionName name = structInst.createNonGcFunction();
            if( name != null ) {
                functions.markAsNeeded( name, true );
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
