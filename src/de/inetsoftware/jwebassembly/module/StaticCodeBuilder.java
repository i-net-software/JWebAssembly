/*
 * Copyright 2020 - 2022 Volker Berlin (i-net software)
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
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map.Entry;

import javax.annotation.Nonnull;

import de.inetsoftware.classparser.ClassFile;
import de.inetsoftware.classparser.Code;
import de.inetsoftware.classparser.MethodInfo;
import de.inetsoftware.jwebassembly.WasmException;
import de.inetsoftware.jwebassembly.module.LocaleVariableManager.Variable;
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
    }

    /**
     * Create a start function for the static class constructors
     * 
     * @param writeLaterClinit iterator of all needed static constructors
     * @throws IOException
     *             if any I/O error occur
     * @return the synthetic function name
     */
    @Nonnull
    FunctionName createStartFunction( Iterator<FunctionName> writeLaterClinit ) throws IOException {
        // list all static constructors (class constructors)
        LinkedHashMap<String,FunctionName> constructors = new LinkedHashMap<>();
        while( writeLaterClinit.hasNext() ) {
            FunctionName name = writeLaterClinit.next();
            constructors.put( name.className, name );
        }

        // scan for recursions between the classes
        ArrayList<FunctionName> clinits = new ArrayList<>();
        LinkedHashMap<String,ScanState> scans = new LinkedHashMap<>();
        for( Iterator<Entry<String, FunctionName>> it = constructors.entrySet().iterator(); it.hasNext(); ) {
            Entry<String, FunctionName> entry = it.next();
            FunctionName name = entry.getValue();
            ScanState scan = scan( name, constructors );
            if( scan == null ) {
                // no dependency to any other static class constructor
                it.remove();
                clinits.add( name );
            } else {
                scans.put( name.className, scan );
            }
        }

        boolean scanAgain;
        do {
            scanAgain = false;
            for( Iterator<Entry<String, ScanState>> it = scans.entrySet().iterator(); it.hasNext(); ) {
                Entry<String, ScanState> entry = it.next();
                ScanState scan = entry.getValue();
                HashSet<String> dependenciesClasses = scan.dependenciesClasses;
                dependenciesClasses.retainAll( scans.keySet() );
                if( dependenciesClasses.isEmpty() ) {
                    clinits.add( scan.name );
                    it.remove();
                    scanAgain = true;
                }
            }
        } while( scanAgain);

        // scan for recursions between the classes
        for( Iterator<ScanState> it = scans.values().iterator(); it.hasNext(); ) {
            ScanState scan = it.next();
            it.remove();
            patch( scan, scans );
            clinits.add( scan.name );
        }

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

                for( FunctionName name : clinits ) {
                    watParser.addCallInstruction( name, false, 0, -1 );
                }
                return watParser;
            }
        };
    }

    /**
     * Scan for for references to other classes
     * 
     * @param name
     *            the name of the static constructor (class initializer)
     * @param constructors
     *            all static constructors which have references or was not scanned
     * @return the reference state
     */
    private ScanState scan( FunctionName name, LinkedHashMap<String, FunctionName> constructors ) {
        ScanState state = null;
        String className = name.className;
        String sourceFile = null;
        WasmInstruction instr = null;
        try {
            ClassFile classFile = classFileLoader.get( className );
            sourceFile = classFile.getSourceFile();
            MethodInfo method = classFile.getMethod( name.methodName, name.signature );
            method = options.functions.replace( name, method );

            Code code = method.getCode();
            javaCodeBuilder.buildCode( code, method );

            List<WasmInstruction> instructions = javaCodeBuilder.getInstructions();

            // search for references to other classes
            for( int i = 0; i < instructions.size(); i++ ) {
                instr = instructions.get( i );
                String otherClassName;
                switch( instr.getType() ) {
                    case Global:
                        WasmGlobalInstruction global = (WasmGlobalInstruction)instr;
                        otherClassName = global.getFieldName().className;
                        break;
                    case Call:
                        WasmCallInstruction call = (WasmCallInstruction)instr;
                        otherClassName = call.getFunctionName().className;
                        break;
                    default:
                        continue;
                }
                if( className.equals( otherClassName ) ) {
                    continue; // field or method in own class
                }
                // search if the other class has a static constructor
                FunctionName clinit = constructors.get( className );
                if( clinit != null ) {
                    if( state == null ) {
                        state = new ScanState();
                        state.name = name;
                        state.instructions = new ArrayList<>( instructions );
                        state.localVariables = javaCodeBuilder.getLocalVariables().getCopy();
                    }
                    state.dependenciesClasses.add( otherClassName );
                }
            }
        } catch( IOException ex ) {
            throw WasmException.create( ex, sourceFile, className, name.methodName, instr == null ? -1 : instr.getLineNumber() );
        }
        return state;
    }

    /**
     * Patch static constructor (class initializer)
     * 
     * @param scan
     *            the current scan
     * @param scans
     *            a list with all static constructors which was not called
     */
    private void patch( ScanState scan, LinkedHashMap<String, ScanState> scans ) {
        FunctionName name = scan.name;
        String className = name.className;

        boolean patched = false;
        List<WasmInstruction> instructions = scan.instructions;

        // search for references to other classes
        for( int i = 0; i < instructions.size(); i++ ) {
            WasmInstruction instr = instructions.get( i );
            String otherClassName;
            switch( instr.getType() ) {
                case Global:
                    WasmGlobalInstruction global = (WasmGlobalInstruction)instr;
                    otherClassName = global.getFieldName().className;
                    break;
                case Call:
                    WasmCallInstruction call = (WasmCallInstruction)instr;
                    otherClassName = call.getFunctionName().className;
                    break;
                default:
                    continue;
            }
            if( className.equals( otherClassName ) ) {
                continue; // field or method in own class
            }

            // search if the other class has a static constructor (class initializer)
            ScanState otherScan = scans.remove( otherClassName );
            //TODO if there references in other branches then it is not called because removed
            if( otherScan != null ) {
                // add a call to the other static constructor (class initializer)
                instructions.add( i, new WasmCallInstruction( otherScan.name, instr.getCodePosition(), instr.getLineNumber(), options.types, false ) );
                i++;

                if( !patched ) {
                    // create patched method
                    patched = true;

                    // create a patched version of the static constructor which call other constructors on specific points.
                    options.functions.markAsNeededAndReplaceIfExists( new SyntheticFunctionName( className, name.methodName, name.signature ) {
                        @Override
                        protected boolean hasWasmCode() {
                            return true;
                        }

                        @Override
                        protected WasmCodeBuilder getCodeBuilder( WatParser watParser ) {
                            WasmCodeBuilder codebuilder = watParser;
                            watParser.reset( null, null, null );
                            ((WasmCodeBuilder)watParser).getLocalVariables().setCopy( scan.localVariables );
                            codebuilder.getInstructions().addAll( instructions );
                            return watParser;
                        }
                    } );
                }

                // remove and scan it that we does not execute it two times
                patch( otherScan, scans );
                break;
            }
        }
    }

    private static class ScanState {
        private final HashSet<String> dependenciesClasses = new HashSet<>();
        private FunctionName name;
        private List<WasmInstruction> instructions;
        private Variable[] localVariables;
    }
}
