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

import java.util.HashMap;
import java.util.Map.Entry;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import de.inetsoftware.classparser.MethodInfo;

/**
 * Manage the required function/methods
 * 
 * @author Volker Berlin
 */
public class FunctionManager {

    private HashMap<FunctionName, FunctionState> states           = new HashMap<>();

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
     * Mark a function as used/called.
     * 
     * @param name
     *            the function name
     */
    void markAsNeeded( FunctionName name ) {
        FunctionState state = getOrCreate( name );
        if( state.state == State.None ) {
            state.state = State.Needed;
        }
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
            }
        }
        return null;
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
     * Add a replacement for a method
     * 
     * @param name
     *            the name of the method which should be replaced
     * @param method
     *            the new implementation
     */
    void addReplacement( FunctionName name, MethodInfo method ) {
        getOrCreate( name ).method = method;
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
     * State of a function/method
     */
    private static class FunctionState {
        private State    state = State.None;

        private MethodInfo method;
    }

    private static enum State {
        None, Needed, Scanned, Written;
    }
}
