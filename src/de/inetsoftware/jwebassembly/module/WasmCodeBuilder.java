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
import de.inetsoftware.jwebassembly.wasm.NamedStorageType;
import de.inetsoftware.jwebassembly.wasm.NumericOperator;
import de.inetsoftware.jwebassembly.wasm.StructOperator;
import de.inetsoftware.jwebassembly.wasm.ValueType;
import de.inetsoftware.jwebassembly.wasm.ValueTypeParser;
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
     * Initialize the code builder;
     * 
     * @param types
     *            the type manager
     * @param functions
     *            the function manager
     * @param options
     *            compiler properties
     */
    void init( TypeManager types, FunctionManager functions, WasmOptions options ) {
        this.localVariables.init( types );
        this.types = types;
        this.functions = functions;
        this.options = options;
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
     */
    protected void reset( LocalVariableTable variableTable ) {
        instructions.clear();
        localVariables.reset( variableTable );
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
    @Nonnull
    protected void addLoadStoreInstruction( AnyType valueType, boolean load, @Nonnegative int javaIdx, int javaCodePos, int lineNumber ) {
        localVariables.use( valueType, javaIdx, javaCodePos );
        instructions.add( new WasmLoadStoreInstruction( load, javaIdx, localVariables, javaCodePos, lineNumber ) );
    }

    /**
     * Create a WasmLoadStoreInstruction get_local/set_local.
     * 
     * @param load
     *            true: if load
     * @param wasmIdx
     *            the index of the variable
     * @param javaCodePos
     *            the code position/offset in the Java method
     * @param lineNumber
     *            the line number in the Java source code
     */
    @Nonnull
    protected void addLocalInstruction( boolean load, @Nonnegative int wasmIdx, int javaCodePos, int lineNumber ) {
        instructions.add( new WasmLocalInstruction( load, wasmIdx, javaCodePos, lineNumber ) );
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
    protected void addConstInstruction( Number value, int javaCodePos, int lineNumber ) {
        instructions.add( new WasmConstInstruction( value, javaCodePos, lineNumber ) );
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
            functions.markAsNeeded( getNonGC( "ref_eq", lineNumber ), true );
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
        instructions.add( new WasmCallInstruction( name, javaCodePos, lineNumber, types ) );
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
        instructions.add( new WasmCallIndirectInstruction( name, localVariables, javaCodePos, lineNumber, types ) );
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
}
