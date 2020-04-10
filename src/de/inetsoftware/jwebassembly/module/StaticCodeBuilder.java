/*
 * Copyright 2020 Volker Berlin (i-net software)
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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import de.inetsoftware.classparser.ClassFile;
import de.inetsoftware.classparser.Code;
import de.inetsoftware.classparser.MethodInfo;
import de.inetsoftware.jwebassembly.WasmException;
import de.inetsoftware.jwebassembly.watparser.WatParser;

/**
 * Write the static class initializer code.
 *
 * @author Volker Berlin
 */
class StaticCodeBuilder {
    private WasmOptions                    options;

    private ClassFileLoader                classFileLoader;

    private JavaMethodWasmCodeBuilder      javaCodeBuilder;

    private final ArrayDeque<FunctionName> clinits;

    /**
     * Create a instance with a snapshot of all static class initializer.
     * 
     * @param options
     *            the compiler options
     * @param classFileLoader
     *            for loading the class files
     * @param javaCodeBuilder
     *            global code builder
     */
    StaticCodeBuilder( WasmOptions options, ClassFileLoader classFileLoader, JavaMethodWasmCodeBuilder javaCodeBuilder ) {
        this.options = options;
        this.classFileLoader = classFileLoader;
        this.javaCodeBuilder = javaCodeBuilder;
        this.clinits = new ArrayDeque<>();
        options.functions.getWriteLaterClinit().forEachRemaining( clinits::push );
    }

    /**
     * Create a start function for the static class constructors
     * 
     * @throws IOException
     *             if any I/O error occur
     * @return the synthetic function name
     */
    FunctionName createStartFunction() throws IOException {
        return new SyntheticFunctionName( "", "<start>", "()V" ) {
            /**
             * {@inheritDoc}
             */
            @Override
            protected boolean hasWasmCode() {
                return true;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            protected WasmCodeBuilder getCodeBuilder( WatParser watParser ) {
                watParser.reset( null, null, getSignature( null ) );

                while( !clinits.isEmpty() ) {
                    FunctionName name = clinits.pop();
                    watParser.addCallInstruction( name, 0, -1 );
                    scanAndPatchIfNeeded( name );
                }
                return watParser;
            }
        };
    }

    private void scanAndPatchIfNeeded( FunctionName name ) {
        String className = name.className;
        String sourceFile = null;
        WasmInstruction instr = null;
        try {
            ClassFile classFile = classFileLoader.get( className );
            sourceFile = classFile.getSourceFile();
            MethodInfo method = classFile.getMethod( name.methodName, name.signature );

            Code code = method.getCode();
            javaCodeBuilder.buildCode( code, method );

            boolean patched = false;
            List<WasmInstruction> instructions = new ArrayList<>( javaCodeBuilder.getInstructions() );
            for( int i = 0; i < instructions.size(); i++ ) {
                instr = instructions.get( i );
                switch( instr.getType() ) {
                    case Global:
                        WasmGlobalInstruction global = (WasmGlobalInstruction)instr;
                        String fieldClassName = global.getFieldName().className;
                        if( className.equals( fieldClassName ) ) {
                            continue; // field in own class
                        }
                        for( Iterator<FunctionName> it = clinits.iterator(); it.hasNext(); ) {
                            FunctionName clinit = it.next();
                            if( fieldClassName.equals( clinit.className ) ) {
                                instructions.add( new WasmCallInstruction( clinit, instr.getCodePosition(), instr.getLineNumber(), options.types, false ) );
                                i++;
                                patched = true;
                                it.remove();
                                scanAndPatchIfNeeded( clinit );
                            }
                        }
                        break;
                    case Call:
                        //TODO
                }
            }

            if( patched ) {
                options.functions.markAsNeededAndReplaceIfExists( new SyntheticFunctionName( className, name.methodName, name.signature ) {

                    @Override
                    protected boolean hasWasmCode() {
                        // TODO Auto-generated method stub
                        return true;
                    }

                    protected WasmCodeBuilder getCodeBuilder( WatParser watParser ) {
                        WasmCodeBuilder codebuilder = watParser;
                        watParser.reset( null, null, null );
                        codebuilder.getInstructions().addAll( instructions );
                        return watParser;
                    }
                } );
            }
        } catch( IOException ex ) {
            throw WasmException.create( ex, sourceFile, className, name.methodName, instr == null ? -1 : instr.getLineNumber() );
        }

    }
}
