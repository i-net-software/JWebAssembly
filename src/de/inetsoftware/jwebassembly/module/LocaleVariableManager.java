/*
   Copyright 2018 Volker Berlin (i-net software)

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.

*/
package de.inetsoftware.jwebassembly.module;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import de.inetsoftware.jwebassembly.WasmException;

/**
 * This manager monitor the locale variables of a method to create a translation from the slot based index in Java to
 * the variable based index in WebAssembly. An 8-byte variable of type long and double consumes in Java 2 slots, but
 * only one index in WebAssmenbly.
 * 
 * @author Volker Berlin
 *
 */
class LocaleVariableManager {

    private LocaleVariable[]     variables;

    private int                  size;

    private ArrayList<ValueType> localTypes = new ArrayList<>();

    /**
     * Create a new instance.
     */
    LocaleVariableManager() {
        // initialize with a initial capacity that should be enough for the most methods
        variables = new LocaleVariable[8];
        for( int i = 0; i < variables.length; i++ ) {
            variables[i] = new LocaleVariable();
        }
    }

    /**
     * Reset the manager to an initial state
     */
    void reset() {
        for( int i = 0; i < size; i++ ) {
            LocaleVariable var = variables[i];
            var.valueType = null;
            var.idx = -1;
        }
        size = 0;
    }

    /**
     * Mark a variable slot as used with its type.
     * 
     * @param valueType
     *            the type of the local variable
     * @param slot
     *            the memory/slot index of the local variable
     */
    void use( ValueType valueType, int slot ) {
        ensureCapacity( slot );
        size = Math.max( size, slot + 1 );
        LocaleVariable var = variables[slot];
        if( var.valueType != null && var.valueType != valueType ) {
            throw new WasmException( "Redefine local variable type from " + var.valueType + " to "
                            + valueType, null, -1 );
        }
        var.valueType = valueType;
    }

    /**
     * Calculate the WebAssembly index position on the consumed data.
     */
    void calculate() {
        int idx = 0;
        for( int i = 0; i < size; i++ ) {
            LocaleVariable var = variables[i];
            if( var.valueType == null ) { // unused slot or extra slot for double and long values 
                continue;
            }
            var.idx = idx++;
        }
    }

    /**
     * Get the data types of the local variables. The value is only valid until the next call.
     * 
     * @param paramCount
     *            the count of method parameter which should be exclude
     * @return the reused list with fresh values
     */
    List<ValueType> getLocalTypes( int paramCount ) {
        localTypes.clear();
        for( int i = 0; i < size; i++ ) {
            LocaleVariable var = variables[i];
            if( var.idx >= paramCount ) {
                localTypes.add( var.valueType );
            }
        }
        return localTypes;
    }

    /**
     * Get the WebAssembly variable index of the given Java Slot.
     * 
     * @param slot
     *            the memory/slot index of the local variable in Java
     * @return the variable index in WebAssembly
     */
    int get( int slot ) {
        return variables[slot].idx;
    }

    /**
     * Ensure that there is enough capacity.
     * 
     * @param slot
     *            the needed slot
     */
    private void ensureCapacity( int slot ) {
        if( slot >= variables.length ) {
            int i = variables.length;
            variables = Arrays.copyOf( variables, slot + 1 );
            for( ; i < variables.length; i++ ) {
                variables[i] = new LocaleVariable();
            }
        }
    }

    /**
     * The state of a single local variable slot.
     */
    private static class LocaleVariable {
        private ValueType valueType;

        private int       idx = -1;
    }
}
