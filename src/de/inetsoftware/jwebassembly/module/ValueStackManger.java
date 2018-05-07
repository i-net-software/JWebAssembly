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

import java.util.Arrays;

/**
 * This manager monitor the value stack of a method
 * 
 * @author Volker Berlin
 *
 */
class ValueStackManger {

    private StackElement[] elements;

    private int            size;

    /**
     * Create a new instance.
     */
    ValueStackManger() {
        // initialize with a initial capacity that should be enough for the most methods
        elements = new StackElement[8];
        for( int i = 0; i < elements.length; i++ ) {
            elements[i] = new StackElement();
        }
    }

    /**
     * Reset the manager to an inital state
     */
    void reset() {
        size = 0;
    }

    /**
     * Add an value to the stack.
     * 
     * @param valueType
     *            the type of the value on the stack
     * @param codePosition
     *            the code position on which this value was push to the stack
     */
    void add( ValueType valueType, int codePosition ) {
        ensureCapacity();
        StackElement el = elements[size++];
        el.valueType = valueType;
        el.codePosition = codePosition;
    }

    /**
     * Remove the the last value from the stack
     */
    void remove() {
        size--;
    }

    /**
     * Get the code position on which the value on the stack was define.
     * 
     * @param deep the position in the stack, 0 means the last position
     * @return the code position
     */
    int getCodePosition( int deep ) {
        return elements[size - deep - 1].codePosition;
    }

    /**
     * Ensure that there is enough capacity.
     */
    private void ensureCapacity() {
        if( size == elements.length ) {
            elements = Arrays.copyOf( elements, size + 1 );
            elements[size] = new StackElement();
        }
    }

    /**
     * The state of a single stack position.
     */
    private static class StackElement {
        private ValueType valueType;

        private int       codePosition;
    }
}
