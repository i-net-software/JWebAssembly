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

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import de.inetsoftware.classparser.MethodInfo;
import de.inetsoftware.jwebassembly.WasmException;

/**
 * Manage the required function/methods
 * 
 * @author Volker Berlin
 */
public class FunctionManager {

    private final Map<FunctionName, FunctionState> states = new LinkedHashMap<>();

    private boolean isFinish;

    /**
     * Finish the prepare. Now no new function should be added. 
     */
    void prepareFinish() {
        isFinish = true;
    }

    /**
     * Get an existing state or create one.
     * 
     * @param name
     *            the FunctionName
     * @return the state
     */
    @Nonnull
    private FunctionState getOrCreate( FunctionName name ) {
        FunctionState state = states.get( name );
        if( state == null ) {
            states.put( name, state = new FunctionState() );
        }
        return state;
    }

    /**
     * Mark the a function as a import function. Only if the function is also needed then it will imported from
     * compiler.
     * 
     * @param name
     *            the function name
     * @param importAnannotation
     *            the annotation of the import
     */
    void markAsImport( FunctionName name, Map<String,Object> importAnannotation ) {
        markAsImport( name, (key) -> importAnannotation.get( key ) );
    }

    /**
     * Mark the a function as a import function. Only if the function is also needed then it will imported from
     * compiler.
     * 
     * @param name
     *            the function name
     * @param importAnannotation
     *            the annotation of the import
     */
    void markAsImport( FunctionName name, Function<String,Object> importAnannotation ) {
        getOrCreate( name ).importAnannotation = importAnannotation;
    }

    /**
     * Mark the a function as scanned in the prepare phase. This should only occur with needed functions.
     * 
     * @param name
     *            the function name
     */
    void markAsScanned( FunctionName name ) {
        getOrCreate( name ).state = State.Scanned;
    }

    /**
     * Mark the a function as written to the wasm file.
     * 
     * @param name
     *            the function name
     */
    void markAsWritten( FunctionName name ) {
        getOrCreate( name ).state = State.Written;
    }

    /**
     * Mark a function as used/called and return the real name if there is an alias.
     * 
     * @param name
     *            the function name
     * @param isStatic
     *            true, if the method is static
     * @return the real function name
     */
    FunctionName markAsNeeded( FunctionName name, boolean isStatic ) {
        FunctionState state = getOrCreate( name );
        if( state.state == State.None ) {
            if( isFinish ) {
                throw new WasmException( "Prepare was already finish: " + name.signatureName, -1 );
            }
            state.state = State.Needed;
        }
        state.isStatic = isStatic;
        return state.alias == null ? name : state.alias;
    }

    /**
     * Get all FunctionNames that need imported
     * 
     * @return an iterator
     */
    Iterator<FunctionName> getNeededImports() {
        return states.entrySet().stream().filter( entry -> {
            FunctionState state = entry.getValue();
            switch( state.state ) {
                case Needed:
                case Scanned:
                    return state.importAnannotation != null;
                default:
            }
            return false;
        } ).map( entry -> entry.getKey() ).iterator();
    }

    /**
     * Get the annotation of an import function
     * 
     * @param name
     *            the function name
     * @return the annotation or null
     */
    Function<String, Object> getImportAnannotation( FunctionName name ) {
        return getOrCreate( name ).importAnannotation;
    }

    /**
     * Get the first FunctionName that is required but was not scanned.
     * 
     * @return the FunctionName or null
     */
    @Nullable
    FunctionName nextScannLater() {
        for( Entry<FunctionName, FunctionState> entry : states.entrySet() ) {
            if( entry.getValue().state == State.Needed ) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * Get the first FunctionName that is required but was not written.
     * 
     * @return the FunctionName or null
     */
    @Nullable
    FunctionName nextWriteLater() {
        for( Entry<FunctionName, FunctionState> entry : states.entrySet() ) {
            switch( entry.getValue().state ) {
                case Needed:
                case Scanned:
                    return entry.getKey();
                default:
            }
        }
        return null;
    }

    /**
     * Get all FunctionNames that need imported
     * 
     * @return an iterator
     */
    Iterator<FunctionName> getNeededFunctions() {
        return states.entrySet().stream().filter( entry -> {
            FunctionState state = entry.getValue();
            switch( state.state ) {
                case Needed:
                case Scanned:
                    return true;
                default:
            }
            return false;
        } ).map( entry -> entry.getKey() ).iterator();
    }

    /**
     * if the given function is required but was not scanned.
     * 
     * @param name
     *            the function name
     * @return true, if the function on the to do list
     */
    boolean needToScan( FunctionName name ) {
        switch( getOrCreate( name ).state ) {
            case Needed:
                return true;
            default:
                return false;
        }
    }

    /**
     * if the given function is required but was not written.
     * 
     * @param name
     *            the function name
     * @return true, if the function on the to do list
     */
    boolean needToWrite( FunctionName name ) {
        switch( getOrCreate( name ).state ) {
            case Needed:
            case Scanned:
                return true;
            default:
                return false;
        }
    }

    /**
     * if the given function is static.
     * 
     * @param name
     *            the function name
     * @return true, if the function is static
     */
    boolean isStatic( FunctionName name ) {
        return getOrCreate( name ).isStatic;
    }

    /**
     * Add a replacement for a method
     * 
     * @param name
     *            the name of the method which should be replaced
     * @param method
     *            the new implementation
     */
    void addReplacement( FunctionName name, MethodInfo method ) {
        FunctionState state = getOrCreate( name );
        if( state.method == null ) { // ignore redefinition replacements and use the first instance in the library path
            state.method = method;
        }
    }

    /**
     * Set an alias for the method. If this method should be called then the alias method should be really called. This
     * is typical a virtual super method.
     * 
     * @param name
     *            the original name
     * @param alias
     *            the new name.
     */
    void setAlias( FunctionName name, FunctionName alias ) {
        FunctionState state = getOrCreate( name );
        state.alias = alias;
        state.state = State.Written;
    }

    /**
     * Check if there is a replacement method
     * 
     * @param name
     *            the name
     * @param method
     *            the current method
     * @return the method that should be write
     */
    @Nonnull
    MethodInfo replace( FunctionName name, MethodInfo method ) {
        MethodInfo newMethod = getOrCreate( name ).method;
        return newMethod != null ? newMethod : method;
    }

    /**
     * Set the index of a virtual function in a a type
     * 
     * @param name
     *            the name
     * @param functionIdx
     *            the index
     */
    void setFunctionIndex( FunctionName name, int functionIdx ) {
        getOrCreate( name ).functionIdx = functionIdx;
    }

    /**
     * Get the index of a virtual function in a a type
     * 
     * @param name
     *            the name
     * @return the index
     */
    int getFunctionIndex( FunctionName name ) {
        return getOrCreate( name ).functionIdx;
    }

    /**
     * State of a function/method
     */
    private static class FunctionState {
        private State                    state       = State.None;

        private MethodInfo               method;

        private FunctionName             alias;

        private Function<String, Object> importAnannotation;

        private int                      functionIdx = -1;

        private boolean                  isStatic;
    }

    private static enum State {
        None, Needed, Scanned, Written;
    }
}
