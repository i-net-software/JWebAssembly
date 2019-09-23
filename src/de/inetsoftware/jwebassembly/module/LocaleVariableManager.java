/*
   Copyright 2018 - 2019 Volker Berlin (i-net software)

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
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

import javax.annotation.Nullable;

import de.inetsoftware.classparser.LocalVariable;
import de.inetsoftware.classparser.LocalVariableTable;
import de.inetsoftware.classparser.MethodInfo;
import de.inetsoftware.jwebassembly.WasmException;
import de.inetsoftware.jwebassembly.wasm.AnyType;
import de.inetsoftware.jwebassembly.wasm.ValueType;
import de.inetsoftware.jwebassembly.wasm.ValueTypeParser;

/**
 * This manager monitor the locale variables of a method to create a translation from the slot based index in Java to
 * the variable based index in WebAssembly. An 8-byte variable of type long and double consumes in Java 2 slots, but
 * only one index in WebAssmenbly.
 * 
 * @author Volker Berlin
 *
 */
class LocaleVariableManager {

    private TypeManager              types;

    private Variable[]               variables;

    private int                      size;

    private final ArrayList<AnyType> localTypes = new ArrayList<>();

    private final HashSet<String>    names      = new HashSet<>();

    /**
     * Create a new instance.
     */
    LocaleVariableManager() {
        // initialize with a initial capacity that should be enough for the most methods
        variables = new Variable[8];
        for( int i = 0; i < variables.length; i++ ) {
            variables[i] = new Variable();
        }
    }

    /**
     * Initialize the variable manager;
     * 
     * @param types
     *            the type manager
     */
    void init( TypeManager types ) {
        this.types = types;
    }

    /**
     * Reset the manager to an initial state
     * 
     * @param variableTable
     *            variable table of the Java method.
     * @param method
     *            the method with signature as fallback for a missing variable table
     */
    void reset( LocalVariableTable variableTable, MethodInfo method ) {
        size = 0;

        if( variableTable == null ) {
            return;
        }

        /**
         * Java can use reuse a variable slot in a different block. The type can be different in the block. WebAssembly
         * does not support a type change for a local variable. That we need to create 2 variables. This try the follow
         * complex code.
         */

        LocalVariable[] vars = variableTable.getTable();
        ensureCapacity( vars.length );

        // transfer all declarations from the LocalVariableTable
        for( int i = 0; i < vars.length; i++ ) {
            LocalVariable local = vars[i];
            Variable var = variables[size];
            var.valueType = new ValueTypeParser( local.getSignature(), types ).next();
            var.name = local.getName();
            var.idx = local.getIndex();
            var.startPos = local.getStartPosition() - 2;
            var.endPos = local.getStartPosition() + local.getLengthPosition();
            size++;
        }

        // sort to make sure but it should already sorted
        Arrays.sort( variables, 0, size, (Comparator<Variable>)( v1, v2 ) -> {
            int comp = Integer.compare( v1.idx, v2.idx );
            if( comp != 0 ) {
                return comp;
            }
            return Integer.compare( v1.startPos, v2.startPos );
        } );

        // reduce all duplications if there are no conflicts and expands startPos and endPos
        for( int i = 0; i < size - 1; i++ ) {
            Variable var = variables[i];
            int j = i + 1;
            Variable var2 = variables[j];
            if( var.idx == var2.idx ) {
                if( var.valueType == var2.valueType ) {
                    var.endPos = var2.endPos;
                    size--;
                    int count = size - j;
                    if( count > 0 ) {
                        System.arraycopy( variables, j + 1, variables, j, count );
                        variables[size] = var2;
                    }
                    i--;
                    continue;
                }
            } else {
                var.endPos = Integer.MAX_VALUE;
                var2.startPos = 0;
            }
        }
        if( size > 0 ) {
            variables[0].startPos = 0;
            variables[size - 1].endPos = Integer.MAX_VALUE;
        }

        // make the names unique if there conflicts. Java can use the same variable name in different blocks. WebAssembly text output does not accept this. 
        names.clear();
        for( int i = 0; i < size; i++ ) {
            Variable var = variables[i];
            var.name = findUniqueVarName( var.name );
        }

        int maxLocals = variableTable.getMaxLocals();

        // add missing slots from signature
        if( maxLocals > 0 && vars.length == 0 && method != null ) {
            ValueTypeParser parser = new ValueTypeParser( method.getType(), types );
            if( !method.isStatic() ) {
                resetAddVar( ValueType.anyref, size );
            }
            while( size < maxLocals ) {
                AnyType type = parser.next();
                if( type == null ) {
                    break;
                }
                resetAddVar( type, size );
            }
        }

        // add all missing slots that we can add self temporary variables
        NEXT: for( int i = 0; i < maxLocals; i++ ) {
            for( int j = 0; j < size; j++ ) {
                Variable var = variables[j];
                if( var.idx == i ) {
                    continue NEXT;
                }
            }
            resetAddVar( null, i );
        }
    }

    /**
     * Add a variable in the reset with range.
     * 
     * @param type
     *            the type of the variable
     * @param slot
     *            the slot of the variable
     */
    private void resetAddVar( AnyType type, int slot ) {
        ensureCapacity( size + 1 );
        Variable var = variables[size];
        var.valueType = type;
        var.name = null;
        var.idx = slot;
        var.startPos = 0;
        var.endPos = Integer.MAX_VALUE;
        size++;
    }

    /**
     * Find a unique variable name.
     * 
     * @param name
     *            the suggested name
     * @return a name that not was used before
     */
    private String findUniqueVarName( String name ) {
        if( names.contains( name ) ) {
            // duplicate name for a variable in a different block
            int id = 1;
            do {
                String name2 = name + '_' + ++id;
                if( !names.contains( name2 ) ) {
                    name = name2;
                    break;
                }
            } while( true );
        }
        names.add( name );
        return name;
    }

    /**
     * Mark a variable slot as used with its type.
     * 
     * @param valueType
     *            the type of the local variable
     * @param slot
     *            the memory/slot index of the local variable
     * @param javaCodePos
     *            the code position/offset in the Java method
     */
    void use( AnyType valueType, int slot, int javaCodePos ) {
        int idx = get( slot, javaCodePos );
        Variable var = variables[idx];
        if( var.valueType != null && var.valueType != valueType ) {
            if( var.valueType.getCode() >= 0 && valueType == ValueType.anyref ) {
                return;
            }
            if( valueType.getCode() >= 0 && var.valueType == ValueType.anyref ) {
                // set the more specific type
            } else {
return;// TODO we need a better check
//                throw new WasmException( "Redefine local variable '" + var.name + "' type from " + var.valueType + " to " + valueType + " in slot "
//                                + slot + ". Compile the Java code with debug information to correct this problem.", null, null, -1 );

            }
        }
        var.valueType = valueType;
    }

    /**
     * Calculate the WebAssembly index position on the consumed data.
     */
    void calculate() {
        for( int i = 0; i < size; i++ ) {
            Variable var = variables[i];
            if( var.valueType == null ) {
                size--;
                System.arraycopy( variables, i + 1, variables, i, size - i );
                variables[size] = var;
                i--;
            }
        }
    }

    /**
     * Get the data types of the local variables. The value is only valid until the next call.
     * 
     * @param paramCount
     *            the count of method parameter which should be exclude
     * @return the reused list with fresh values
     */
    List<AnyType> getLocalTypes( int paramCount ) {
        localTypes.clear();
        for( int i = paramCount; i < size; i++ ) {
            Variable var = variables[i];
            localTypes.add( var.valueType );
        }
        return localTypes;
    }

    /**
     * Get the name of the variable or null if no name available
     * 
     * @param idx
     *            the wasm variable index
     * @return the name
     */
    @Nullable
    String getLocalName( int idx ) {
        return variables[idx].name;
    }

    /**
     * Get the slot of the temporary variable.
     * 
     * @param valueType
     *            the valueType for the variable
     * @param startCodePosition
     *            the start of the Java code position
     * @param endCodePosition
     *            the end of the Java code position
     * @return the slot
     */
    int getTempVariable( AnyType valueType, int startCodePosition, int endCodePosition ) {
        // can we reuse some other temporary local variables?
        for( int i = size-1; i >= 0 ; i-- ) {
            Variable var = variables[i];
            if( var.valueType != valueType ) {
                continue;
            }
            if( var.endPos < startCodePosition ) {
                var.endPos = endCodePosition;
                return var.idx;
            }
        }
        ensureCapacity( size + 1 );
        Variable var = variables[size];
        var.valueType = valueType;
        var.name = null;
        var.idx = size;
        var.startPos = startCodePosition;
        var.endPos = endCodePosition;
        size++;
        return var.idx;
    }

    /**
     * Get the WebAssembly variable index of the given Java Slot.
     * 
     * @param slot
     *            the memory/slot index of the local variable in Java
     * @param javaCodePos the current code position in the Java method
     * @return the variable index in WebAssembly
     */
    int get( int slot, int javaCodePos ) {
        for( int i = 0; i < size; i++ ) {
            Variable var = variables[i];
            if( slot != var.idx ) {
                continue;
            }
            if( var.matchCodePosition( javaCodePos ) ) {
                return i;
            }
        }

        throw new WasmException( "Can not find local variable for slot: " + slot + " on code position " + javaCodePos, -1 );
    }

    /**
     * Get the ValueType of the variable.
     * 
     * @param slot
     *            the memory/slot index of the local variable in Java
     * @return the ValueType
     */
    AnyType getValueType( int slot ) {
        return variables[slot].valueType;
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
                variables[i] = new Variable();
            }
        }
    }

    /**
     * The state of a single local variable slot.
     */
    private static class Variable {

        private AnyType valueType;

        private String  name;

        private int     idx = -1;

        private int     startPos;

        private int     endPos;

        /**
         * If the variable is valid at this position
         * 
         * @param codePosition
         *            the position to check
         * @return true, if this variable match
         */
        public boolean matchCodePosition( int codePosition ) {
            return startPos <= codePosition && codePosition <= endPos;
        }
    }
}
