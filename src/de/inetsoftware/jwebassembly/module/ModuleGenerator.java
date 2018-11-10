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
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import de.inetsoftware.classparser.ClassFile;
import de.inetsoftware.classparser.Code;
import de.inetsoftware.classparser.CodeInputStream;
import de.inetsoftware.classparser.LocalVariableTable;
import de.inetsoftware.classparser.MethodInfo;
import de.inetsoftware.jwebassembly.WasmException;

/**
 * Generate the WebAssembly output.
 * 
 * @author Volker Berlin
 */
public class ModuleGenerator {

    private final ModuleWriter              writer;

    private final JavaMethodWasmCodeBuilder codeBuilder = new JavaMethodWasmCodeBuilder();

    private String                          sourceFile;

    private String                          className;

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
        className = null;
        try {
            sourceFile = classFile.getSourceFile();
            className = classFile.getThisClass().getName();
            MethodInfo[] methods = classFile.getMethods();
            for( MethodInfo method : methods ) {
                Code code = method.getCode();
                if( method.getName().equals( "<init>" ) && method.getType().equals( "()V" )
                                && code.isSuperInitReturn( classFile.getSuperClass() ) ) {
                    continue; //default constructor
                }
                handler.accept( method );
            }
        } catch( IOException ioex ) {
            throw WasmException.create( ioex, sourceFile, className, -1 );
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
                writeMethodSignature( method, null, null );
            } else {
                writer.prepareFunction( name );
            }
        } catch( Exception ioex ) {
            throw WasmException.create( ioex, sourceFile, className, -1 );
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

                codeBuilder.buildCode( code, !method.getType().endsWith( ")V" ) );
                writeMethodSignature( method, code.getLocalVariableTable(), codeBuilder );

                for( WasmInstruction instruction : codeBuilder.getInstructions() ) {
                    instruction.writeTo( writer );
                }
                writer.writeMethodFinish();
            }
        } catch( Exception ioex ) {
            int lineNumber = byteCode == null ? -1 : byteCode.getLineNumber();
            throw WasmException.create( ioex, sourceFile, className, lineNumber );
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
     * @param variables
     *            Java variable table with names of the variables for debugging
     * @param codeBuilder
     *            the calculated variables 
     * @throws IOException
     *             if any I/O error occur
     * @throws WasmException
     *             if some Java code can't converted
     */
    private void writeMethodSignature( MethodInfo method, @Nullable LocalVariableTable variables, WasmCodeBuilder codeBuilder ) throws IOException, WasmException {
        String signature = method.getType();
        String kind = "param";
        int paramCount = 0;
        ValueType type = null;
        for( int i = 1; i < signature.length(); i++ ) {
            if( signature.charAt( i ) == ')' ) {
                kind = "result";
                continue;
            }
            String name = null;
            if( kind == "param" ) {
                if( variables != null ) {
                    name = variables.getPosition( paramCount ).getName( method.getConstantPool() );
                }
                paramCount++;
            }
            type = ValueType.getValueType( signature, i );
            if( type != null ) {
                writer.writeMethodParam( kind, type, name );
            }
        }
        if( codeBuilder != null ) {
            List<ValueType> localTypes = codeBuilder.getLocalTypes( paramCount );
            for( int i = 0; i < localTypes.size(); i++ ) {
                type = localTypes.get( i );
                String name = null;
                if( variables != null ) {
                    name = variables.getPosition( paramCount ).getName( method.getConstantPool() );
                }
                writer.writeMethodParam( "local", type, name );
            }
        }
        writer.writeMethodParamFinish( );
    }

}
