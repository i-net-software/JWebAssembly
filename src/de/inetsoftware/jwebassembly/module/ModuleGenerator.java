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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import de.inetsoftware.classparser.ClassFile;
import de.inetsoftware.classparser.Code;
import de.inetsoftware.classparser.CodeInputStream;
import de.inetsoftware.classparser.FieldInfo;
import de.inetsoftware.classparser.LocalVariable;
import de.inetsoftware.classparser.LocalVariableTable;
import de.inetsoftware.classparser.MethodInfo;
import de.inetsoftware.jwebassembly.JWebAssembly;
import de.inetsoftware.jwebassembly.WasmException;
import de.inetsoftware.jwebassembly.module.TypeManager.StructType;
import de.inetsoftware.jwebassembly.wasm.AnyType;
import de.inetsoftware.jwebassembly.wasm.NamedStorageType;
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

    private TypeManager                     types = new TypeManager();

    private CodeOptimizer                   optimizer = new CodeOptimizer();

    /**
     * Create a new generator.
     * 
     * @param writer
     *            the target writer
     * @param libraries
     *            libraries 
     */
    public ModuleGenerator( @Nonnull ModuleWriter writer, @Nonnull List<URL> libraries ) {
        this.writer = writer;
        this.libraries = new URLClassLoader( libraries.toArray( new URL[libraries.size()] ) );
        javaCodeBuilder.init( types );
        ((WasmCodeBuilder)watParser).init( types );
        scanLibraries( libraries );
    }

    /**
     * Scan the libraries for annotated methods
     * 
     * @param libraries
     *            libraries
     */
    private void scanLibraries( @Nonnull List<URL> libraries ) {
        // search for replacement methods in the libraries
        for( URL url : libraries ) {
            try {
                File file = new File(url.toURI());
                if( file.isDirectory() ) {
                    for( Iterator<Path> iterator = Files.walk( file.toPath() ).iterator(); iterator.hasNext(); ) {
                        Path path = iterator.next();
                        if( path.toString().endsWith( ".class" ) ) {
                            ClassFile classFile = new ClassFile( new BufferedInputStream( Files.newInputStream( path ) ) );
                            prepare( classFile );
                        }
                    };
                }
            } catch( Exception e ) {
                e.printStackTrace();
            }

            try (ZipInputStream input = new ZipInputStream( url.openStream() )) {
                do {
                    ZipEntry entry = input.getNextEntry();
                    if( entry == null ) {
                        break;
                    }
                    if( entry.getName().endsWith( ".class" ) ) {
                        ClassFile classFile = new ClassFile( new BufferedInputStream( input ) {
                            @Override
                            public void close() {
                            } // does not close the zip stream
                        } );
                        prepare( classFile );
                    }
                } while( true );
            } catch( IOException e ) {
                e.printStackTrace();
            }
        }
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
                if( next instanceof SyntheticFunctionName ) {
                    watParser.parse( ((SyntheticFunctionName)next).getCode(), -1 );
                    writeMethodImpl( next, true, null, watParser );
                } else {
                    throw new WasmException( "Missing function: " + next.signatureName, -1 );
                }
            } else {
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
                            method = functions.replace( name, method );
                        }
                        if( functions.isToWrite( name ) ) {
                            writeMethod( name, method );
                        }
                    } catch (IOException ioex){
                        throw WasmException.create( ioex, sourceFile, className, -1 );
                    }
                } );
            }

            if( functions.isToWrite( next ) ) {
                throw new WasmException( "Missing function: " + next.signatureName, -1 );
            }
        }
    }

    /**
     * Set the StructType into the instruction and write the types/structs if needed.
     * 
     * @param instruction
     *            the struct instruction
     * @throws IOException
     *             if any I/O error occur
     */
    private void setStructType( WasmStructInstruction instruction ) throws IOException {
        StructType type = instruction.getStructType();
        if( type != null && type.getCode() == Integer.MAX_VALUE ) {
            writeStructType( type );
        }
    }

    /**
     * Write the struct type
     * 
     * @param type
     *            the type
     * @throws IOException
     *             if any I/O error occur
     */
    private void writeStructType( StructType type ) throws IOException {
        String className = type.getName();
        InputStream stream = libraries.getResourceAsStream( className + ".class" );
        if( stream == null ) {
            throw new WasmException( "Missing class: " + className, -1 );
        }
        ClassFile classFile = new ClassFile( stream );
        List<NamedStorageType> list = new ArrayList<>();
        FieldInfo[] fields = classFile.getFields();
        for( FieldInfo field : fields ) {
            if( field.isStatic() ) {
                continue;
            }
            AnyType fieldtype = new ValueTypeParser( field.getType(), types ).next();
            list.add( new NamedStorageType( fieldtype, field.getName() ) );
        }
        int id = writer.writeStruct( className, list );
        types.useType( type, id );
        for( NamedStorageType namedType : list ) {
            if( namedType.type.getCode() == Integer.MAX_VALUE ) {
                writeStructType( (StructType)namedType.type );
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
            Map<String,Object> annotationValues;
            if( (annotationValues = method.getAnnotation( JWebAssembly.IMPORT_ANNOTATION )) != null ) {
                if( !method.isStatic() ) {
                    throw new WasmException( "Import method must be static: " + name.fullName, -1 );
                }
                functions.writeFunction( name );
                String impoarModule = (String)annotationValues.get( "module" );
                String importName = (String)annotationValues.get( "name" );
                writer.prepareImport( name, impoarModule, importName );
                writeMethodSignature( name, true, null, null );
                return;
            }
            if( (annotationValues = method.getAnnotation( JWebAssembly.EXPORT_ANNOTATION )) != null ) {
                if( !method.isStatic() ) {
                    throw new WasmException( "Export method must be static: " + name.fullName, -1 );
                }
                functions.functionCall( name );
                return;
            }
            if( (annotationValues = method.getAnnotation( JWebAssembly.REPLACE_ANNOTATION )) != null ) {
                String signatureName = (String)annotationValues.get( "value" );
                name = new FunctionName( signatureName );
                functions.addReplacement( name, method );
                return;
            }
        } catch( Exception ioex ) {
            throw WasmException.create( ioex, sourceFile, className, -1 );
        }
    }

    /**
     * Write the content of a method.
     * 
     * @param name
     *            the function name that should be written. This can be differ from the value in the MethodInfo
     * @param method
     *            the method
     * @throws WasmException
     *             if some Java code can't converted
     */
    private void writeMethod( FunctionName name, MethodInfo method ) throws WasmException {
        CodeInputStream byteCode = null;
        try {
            if( method.getAnnotation( JWebAssembly.IMPORT_ANNOTATION  ) != null ) {
                return;
            }
            WasmCodeBuilder codeBuilder;
            Code code = method.getCode();
            LocalVariableTable localVariableTable;
            if( method.getAnnotation( JWebAssembly.TEXTCODE_ANNOTATION  ) != null ) {
                Map<String, Object> wat = method.getAnnotation( JWebAssembly.TEXTCODE_ANNOTATION  );
                String watCode = (String)wat.get( "value" );
                String signature = (String)wat.get( "signature" );
                if( signature == null ) {
                    signature = method.getType();
                }
                watParser.parse( watCode, code == null ? -1 : code.getFirstLineNr() );
                codeBuilder = watParser;
                localVariableTable = null;
            } else if( code != null ) { // abstract methods and interface methods does not have code
                javaCodeBuilder.buildCode( code, !method.getType().endsWith( ")V" ) );
                codeBuilder = javaCodeBuilder;
                localVariableTable = code.getLocalVariableTable();
            } else {
                return;
            }
            writeExport( name, method );
            writeMethodImpl( name, method.isStatic(), localVariableTable, codeBuilder );
        } catch( Exception ioex ) {
            int lineNumber = byteCode == null ? -1 : byteCode.getLineNumber();
            throw WasmException.create( ioex, sourceFile, className, lineNumber );
        }
    }

    private void writeMethodImpl( FunctionName name, boolean isStatic, LocalVariableTable localVariableTable, WasmCodeBuilder codeBuilder ) throws WasmException, IOException {
        writer.writeMethodStart( name );
        functions.writeFunction( name );
        writeMethodSignature( name, isStatic, localVariableTable, codeBuilder );

        List<WasmInstruction> instructions = codeBuilder.getInstructions();
        optimizer.optimze( instructions );
        for( WasmInstruction instruction : instructions ) {
            switch( instruction.getType() ) {
                case Block:
                    switch( ((WasmBlockInstruction)instruction).getOperation() ) {
                        case TRY:
                        case CATCH:
                            writer.writeException();
                            break;
                        default:
                    }
                    break;
                case Call:
                    functions.functionCall( ((WasmCallInstruction)instruction).getFunctionName() );
                    break;
                case Struct:
                    setStructType( (WasmStructInstruction)instruction );
                    break;
                default:
            }
            instruction.writeTo( writer );
        }
        writer.writeMethodFinish();
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
     * @param isStatic
     *            if method is static
     * @param variables
     *            Java variable table with names of the variables for debugging
     * @param codeBuilder
     *            the calculated variables 
     * @throws IOException
     *             if any I/O error occur
     * @throws WasmException
     *             if some Java code can't converted
     */
    private void writeMethodSignature( FunctionName name, boolean isStatic, @Nullable LocalVariableTable variables, WasmCodeBuilder codeBuilder ) throws IOException, WasmException {
        int paramCount = 0;
        if( !isStatic ) {
            writer.writeMethodParam( "param", ValueType.anyref, "this" );
        }
        Iterator<AnyType> parser = name.getSignature();
        AnyType type;
        for( String kind : new String[] {"param","result"}) {
            while( parser.hasNext() && (type = parser.next()) != null ) {
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
            if( !isStatic ) {
                paramCount++;
            }
            List<AnyType> localTypes = codeBuilder.getLocalTypes( paramCount );
            for( int i = 0; i < localTypes.size(); i++ ) {
                type = localTypes.get( i );
                String paramName = null;
                if( variables != null ) {
                    int idx = paramCount + i;
                    if( idx < variables.getPositionSize() ) {
                        LocalVariable variable = variables.getPosition( idx );
                        if( variable != null ) {
                            paramName = variable.getName();
                        }
                    }
                }
                writer.writeMethodParam( "local", type, paramName );
            }
        }
        writer.writeMethodParamFinish( );
    }

}
