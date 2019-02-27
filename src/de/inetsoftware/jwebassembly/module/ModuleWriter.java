/*
 * Copyright 2017 - 2019 Volker Berlin (i-net software)
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

import java.io.Closeable;
import java.io.IOException;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import de.inetsoftware.classparser.Member;
import de.inetsoftware.jwebassembly.wasm.ArrayOperator;
import de.inetsoftware.jwebassembly.wasm.NamedStorageType;
import de.inetsoftware.jwebassembly.wasm.NumericOperator;
import de.inetsoftware.jwebassembly.wasm.AnyType;
import de.inetsoftware.jwebassembly.wasm.StructOperator;
import de.inetsoftware.jwebassembly.wasm.ValueType;
import de.inetsoftware.jwebassembly.wasm.VariableOperator;
import de.inetsoftware.jwebassembly.wasm.WasmBlockOperator;

/**
 * Module Writer base class.
 * 
 * @author Volker Berlin
 */
public abstract class ModuleWriter implements Closeable {

    /**
     * Finish the prepare after all classes/methods are prepare. This must be call before we can start with write the
     * first method.
     */
    public void prepareFinish() {

    }

    /**
     * Write a type/struct.
     * 
     * @param typeName
     *            the name
     * @param fields
     *            the fields
     * @return type ID
     * @throws IOException
     *             if any I/O error occur
     */
    protected abstract int writeStruct( String typeName, List<NamedStorageType> fields ) throws IOException;

    /**
     * Mark to write exceptions
     * 
     * @throws IOException
     *             if any I/O error occur
     */
    protected abstract void writeException() throws IOException;

    /**
     * Prepare a imported single function in the prepare phase.
     * 
     * @param name
     *            the function name
     * @param importModule
     *            the import module name if it is a import function
     * @param importName
     *            the import name if it is a import function
     * @throws IOException
     *             if any I/O error occur
     */
    protected abstract void prepareImport( FunctionName name, String importModule, String importName ) throws IOException;

    /**
     * Write an export directive
     * @param name
     *            the function name
     * @param exportName
     *            the export name, if null then the same like the method name
     * 
     * @throws IOException
     *             if any I/O error occur
     */
    protected abstract void writeExport( FunctionName name, String exportName ) throws IOException;

    /**
     * Write the method header.
     * @param name
     *            the function name
     * 
     * @throws IOException
     *             if any I/O error occur
     */
    protected abstract void writeMethodStart( FunctionName name ) throws IOException;

    /**
     * Write a method parameter.
     * 
     * @param kind
     *            "param", "result" or "local"
     * @param valueType
     *            the data type of the parameter
     * @param name
     *            optional name of the parameter
     * @throws IOException
     *             if any I/O error occur
     */
    protected abstract void writeMethodParam( String kind, AnyType valueType, @Nullable String name ) throws IOException;

    /**
     * Finish the function parameter.
     * 
     * @throws IOException
     *             if any I/O error occur
     */
    protected abstract void writeMethodParamFinish() throws IOException;

    /**
     * Complete the method
     * 
     * @throws IOException
     *             if any I/O error occur
     */
    protected abstract void writeMethodFinish( ) throws IOException;

    /**
     * Write a constant number value
     * 
     * @param value
     *            the value
     * @param valueType
     *            the data type of the number
     * @throws IOException
     *             if any I/O error occur
     */
    protected abstract void writeConst( Number value, ValueType valueType ) throws IOException;

    /**
     * Write a local variable operation.
     * 
     * @param op
     *            the operation
     * @param idx
     *            the index of the parameter variable
     * @throws IOException
     *             if any I/O error occur
     */
    protected abstract void writeLocal( VariableOperator op, int idx ) throws IOException;

    /**
     * Write a set_global variable
     * @param load
     *            true: if load or GET
     * @param name
     *            the variable name
     * @param ref
     *            the definition of the variable
     * 
     * @throws IOException
     *             if any I/O error occur
     */
    protected abstract void writeGlobalAccess( boolean load, FunctionName name, Member ref ) throws IOException;


    /**
     * Write a add operator
     * 
     * @param numOp
     *            the numeric operation
     * @param valueType
     *            the type of the parameters
     * 
     * @throws IOException
     *             if any I/O error occur
     */
    protected abstract void writeNumericOperator( NumericOperator numOp, @Nullable ValueType valueType ) throws IOException;

    /**
     * Cast a value from one type to another
     * 
     * @param cast
     *            the operator
     * @throws IOException
     *             if any I/O error occur
     */
    protected abstract void writeCast( ValueTypeConvertion cast ) throws IOException;

    /**
     * Write a call to a function.
     * 
     * @param name
     *            the function name
     * @throws IOException
     *             if any I/O error occur
     */
    protected abstract void writeFunctionCall( FunctionName name ) throws IOException;

    /**
     * Write a block/branch code
     * 
     * @param op
     *            the operation
     * @param data
     *            extra data depending of the operator
     * @throws IOException
     *             if any I/O error occur
     */
    protected abstract void writeBlockCode( @Nonnull WasmBlockOperator op, @Nullable Object data ) throws IOException;

    /**
     * Write an array operation.
     * 
     * @param op
     *            the operation
     * @param type
     *            the resulting type
     * @throws IOException
     *             if any I/O error occur
     */
    protected abstract void writeArrayOperator( @Nonnull ArrayOperator op, AnyType type ) throws IOException;

    /**
     * Write a struct operation
     * 
     * @param op
     *            the operation
     * @param type
     *            the type of the struct
     * @param fieldName TODO
     * @throws IOException
     *             if any I/O error occur
     */
    protected abstract void writeStructOperator( StructOperator op, AnyType type, String fieldName ) throws IOException;
}
