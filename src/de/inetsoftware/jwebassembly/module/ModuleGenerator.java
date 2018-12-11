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
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
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
import de.inetsoftware.jwebassembly.JWebAssembly;
import de.inetsoftware.jwebassembly.WasmException;
import de.inetsoftware.jwebassembly.wasm.ValueType;
import de.inetsoftware.jwebassembly.wasm.ValueTypeParser;
import de.inetsoftware.jwebassembly.watparser.WatParser;

/**
 * Generate the WebAssembly output.
 * 
 * @author Volker Berlin
 */
public class ModuleGenerator {

    private final ModuleWriter              writer;

    private final URLClassLoader            libraries;

    private final JavaMethodWasmCodeBuilder javaCodeBuilder = new JavaMethodWasmCodeBuilder();

    private final WatParser                 watParser = new WatParser();

    private String                          sourceFile;

    private String                          className;

    private FunctionManager                 functions = new FunctionManager();

    /**
     * Create a new generator.
     * 
     * @param writer
     *            the target writer
     * @param libraries
     *            libraries 
     */
    public ModuleGenerator( @Nonnull ModuleWriter writer, List<URL> libraries ) {
        this.writer = writer;
        this.libraries = new URLClassLoader( libraries.toArray( new URL[libraries.size()] ) );
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

    /**
     * Finish the code generation.
     * 
     * @throws IOException
     *             if any I/O error occur
     */
    public void finish() throws IOException {
        FunctionName next;
        while( (next = functions.nextWriteLater()) != null ) {
            InputStream stream = libraries.getResourceAsStream( next.className + ".class" );
            if( stream == null ) {
                throw new WasmException( "Missing function: " + next.signatureName, -1 );
            }
            ClassFile classFile = new ClassFile( stream );
            iterateMethods( classFile, method -> {
                try {
                    FunctionName name;
                    Map<String, Object> wat = method.getAnnotation( JWebAssembly.TEXTCODE_ANNOTATION  );
                    if( wat != null ) {
                        String signature = (String)wat.get( "signature" );
                        if( signature == null ) {
                            signature = method.getType();
                        }
                        name = new FunctionName( method, signature );
                    } else {
                        name = new FunctionName( method );
                    }
                    if( functions.isToWrite( name ) ) {
                        writeMethod( method );
                    }
                } catch (IOException ioex){
                    throw WasmException.create( ioex, sourceFile, className, -1 );
                }
            } );

            if( functions.isToWrite( next ) ) {
                throw new WasmException( "Missing function: " + next.signatureName, -1 );
            }
        }
    }

    /**
     * Iterate over all methods of the classFile and run the handler.
     * 
     * @param classFile
     *            the classFile
     * @param handler
     *            the handler
     * @throws WasmException
     *             if some Java code can't converted
     */
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
            Map<String,Object> annotationValues = method.getAnnotation( JWebAssembly.IMPORT_ANNOTATION );
            if( annotationValues != null ) {
                functions.writeFunction( name );
                String impoarModule = (String)annotationValues.get( "module" );
                String importName = (String)annotationValues.get( "name" );
                writer.prepareImport( name, impoarModule, importName );
                writeMethodSignature( name, null, null );
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
            if( method.getAnnotation( JWebAssembly.IMPORT_ANNOTATION  ) != null ) {
                return;
            }
            WasmCodeBuilder codeBuilder;
            Code code = method.getCode();
            FunctionName name;
            if( method.getAnnotation( JWebAssembly.TEXTCODE_ANNOTATION  ) != null ) {
                Map<String, Object> wat = method.getAnnotation( JWebAssembly.TEXTCODE_ANNOTATION  );
                String watCode = (String)wat.get( "value" );
                String signature = (String)wat.get( "signature" );
                if( signature == null ) {
                    signature = method.getType();
                }
                name = new FunctionName( method, signature );
                watParser.parse( watCode, code == null ? -1 : code.getFirstLineNr() );
                codeBuilder = watParser;
            } else if( code != null ) { // abstract methods and interface methods does not have code
                name = new FunctionName( method );
                javaCodeBuilder.buildCode( code, !method.getType().endsWith( ")V" ) );
                codeBuilder = javaCodeBuilder;
            } else {
                return;
            }
            writeExport( name, method );
            writer.writeMethodStart( name );
            functions.writeFunction( name );
            LocalVariableTable localVariableTable = code == null ? null : code.getLocalVariableTable();
            writeMethodSignature( name, localVariableTable, codeBuilder ); 

            for( WasmInstruction instruction : codeBuilder.getInstructions() ) {
                if( instruction instanceof WasmCallInstruction ) {
                    functions.functionCall( ((WasmCallInstruction)instruction).getFunctionName() );
                }
                instruction.writeTo( writer );
            }
            writer.writeMethodFinish();
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
        Map<String,Object> export = method.getAnnotation( JWebAssembly.EXPORT_ANNOTATION );
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
     * @param name
     *            the Java signature, typical method.getType();
     * @param variables
     *            Java variable table with names of the variables for debugging
     * @param codeBuilder
     *            the calculated variables 
     * @throws IOException
     *             if any I/O error occur
     * @throws WasmException
     *             if some Java code can't converted
     */
    private void writeMethodSignature( FunctionName name, @Nullable LocalVariableTable variables, WasmCodeBuilder codeBuilder ) throws IOException, WasmException {
        String signature = name.signature;
        int paramCount = 0;
        ValueTypeParser parser = new ValueTypeParser( signature );
        ValueType type;
        for( String kind : new String[] {"param","result"}) {
            while( (type = parser.next()) != null ) {
                String paramName = null;
                if( kind == "param" ) {
                    if( variables != null ) {
                        paramName = variables.getPosition( paramCount ).getName();
                    }
                    paramCount++;
                }
                if( type != ValueType.empty ) {
                    writer.writeMethodParam( kind, type, paramName );
                }
            }
        }
        if( codeBuilder != null ) {
            List<ValueType> localTypes = codeBuilder.getLocalTypes( paramCount );
            for( int i = 0; i < localTypes.size(); i++ ) {
                type = localTypes.get( i );
                String paramName = null;
                if( variables != null ) {
                    paramName = variables.getPosition( paramCount ).getName();
                }
                writer.writeMethodParam( "local", type, paramName );
            }
        }
        writer.writeMethodParamFinish( );
    }

}
