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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;

import de.inetsoftware.classparser.ClassFile;
import de.inetsoftware.classparser.ConstantClass;
import de.inetsoftware.classparser.FieldInfo;
import de.inetsoftware.classparser.MethodInfo;
import de.inetsoftware.jwebassembly.JWebAssembly;
import de.inetsoftware.jwebassembly.WasmException;
import de.inetsoftware.jwebassembly.wasm.AnyType;
import de.inetsoftware.jwebassembly.wasm.ArrayType;
import de.inetsoftware.jwebassembly.wasm.NamedStorageType;
import de.inetsoftware.jwebassembly.wasm.ValueType;

/**
 * Manage the written and to write types (classes)
 * 
 * @author Volker Berlin
 */
public class TypeManager {

    /** name of virtual function table, start with a point for an invalid Java identifier  */
    static final String             VTABLE = ".vtable";

    private Map<String, StructType> structTypes = new LinkedHashMap<>();

    private Map<AnyType, ArrayType> arrayTypes  = new LinkedHashMap<>();

    private boolean                 isFinish;

    private boolean                 useGC;

    /**
     * Initialize the type manager.
     * 
     * @param properties
     *            compiler properties
     */
    void init( HashMap<String, String> properties ) {
        this.useGC = Boolean.parseBoolean( properties.getOrDefault( JWebAssembly.WASM_USE_GC, "false" ) );
    }

    /**
     * Finish the prepare and write the types. Now no new types and functions should be added.
     * 
     * @param writer
     *            the targets for the types
     * @param functions
     *            the used functions for the vtables of the types
     * @param libraries
     *            for loading the class files if not in the cache
     * @throws IOException
     *             if any I/O error occur on loading or writing
     */
    void prepareFinish( ModuleWriter writer, FunctionManager functions, ClassLoader libraries ) throws IOException {
        isFinish = true;
        for( StructType type : structTypes.values() ) {
            type.writeStructType( writer, functions, this, libraries );
        }
    }

    /**
     * Use the type in the output.
     * 
     * @param type
     *            the reference to a type
     * @param id
     *            the id in the type section of the wasm
     * @param fields
     *            the fields of the type
     */
    void useType( StructType type, int id, List<NamedStorageType> fields ) {
        type.code = id;
        type.fields = fields;
    }

    /**
     * Get the registered types in numeric order.
     * 
     * @return the types
     */
    @Nonnull
    Collection<StructType> getTypes() {
        return structTypes.values();
    }

    /**
     * Get the StructType. If needed an instance is created.
     * 
     * @param name
     *            the type name
     * @return the struct type
     */
    public StructType valueOf( String name ) {
        StructType type = structTypes.get( name );
        if( type == null ) {
            JWebAssembly.LOGGER.fine( "\t\ttype: " + name );
            if( isFinish ) {
                throw new WasmException( "Register needed type after scanning: " + name, -1 );
            }
            type = new StructType( name );
            structTypes.put( name, type );
        }
        return type;
    }

    /**
     * Get the array type for the given component type.
     * 
     * @param arrayType
     *            the component type of the array
     * @return the array type
     */
    public ArrayType arrayType( AnyType arrayType ) {
        ArrayType type = arrayTypes.get( arrayType );
        if( type == null ) {
            JWebAssembly.LOGGER.fine( "\t\ttype: " + arrayType );
            if( isFinish ) {
                throw new WasmException( "Register needed array type after scanning: " + arrayType, -1 );
            }
            type = new ArrayType( arrayType );
            arrayTypes.put( arrayType, type );
        }
        return type;
    }

    /**
     * A reference to a type.
     * 
     * @author Volker Berlin
     */
    public static class StructType implements AnyType {

        private final String           name;

        private int                    code = Integer.MAX_VALUE;

        private HashSet<String>        neededFields = new HashSet<>();

        private List<NamedStorageType> fields;

        private List<FunctionName>     methods;

        /**
         * The offset to the vtable in the data section.
         */
        private int                    vtableOffset;

        /**
         * Create a reference to type
         * 
         * @param name
         *            the Java class name
         */
        StructType( String name ) {
            this.name = name;
        }

        void useFieldName( NamedStorageType fieldName ) {
            neededFields.add( fieldName.getName());
        }

        /**
         * Write this struct type and initialize internal structures
         * 
         * @param writer
         *            the targets for the types
         * @param functions
         *            the used functions for the vtables of the types
         * @param types
         *            for types of fields
         * @param libraries
         *            for loading the class files if not in the cache
         * @throws IOException
         *             if any I/O error occur on loading or writing
         */
        private void writeStructType( ModuleWriter writer, FunctionManager functions, TypeManager types, ClassLoader libraries ) throws IOException {
            JWebAssembly.LOGGER.fine( "write type: " + name );
            fields = new ArrayList<>();
            methods = new ArrayList<>();
            HashSet<String> allNeededFields = new HashSet<>();
            listStructFields( name, functions, types, libraries, allNeededFields );
            code = types.useGC ? writer.writeStructType( this ) : ValueType.anyref.getCode();
        }

        /**
         * List the non static fields of the class and its super classes.
         * 
         * @param className
         *            the className to list. because the recursion this must not the name of this class
         * @param functions
         *            the used functions for the vtables of the types
         * @param types
         *            for types of fields
         * @param libraries
         *            for loading the class files if not in the cache
         * @throws IOException
         *             if any I/O error occur on loading or writing
         */
        private void listStructFields( String className, FunctionManager functions, TypeManager types, ClassLoader libraries, HashSet<String> allNeededFields ) throws IOException {
            ClassFile classFile = ClassFile.get( className, libraries );
            if( classFile == null ) {
                throw new WasmException( "Missing class: " + className, -1 );
            }

            allNeededFields.addAll( neededFields );

            ConstantClass superClass = classFile.getSuperClass();
            if( superClass != null ) {
                String superClassName = superClass.getName();
                listStructFields( superClassName, functions, types, libraries, allNeededFields );
            } else {
                fields.add( new NamedStorageType( ValueType.i32, className, VTABLE ) );
            }

            for( FieldInfo field : classFile.getFields() ) {
                if( field.isStatic() ) {
                    continue;
                }
                if( !allNeededFields.contains( field.getName() ) ) {
                    continue;
                }
                fields.add( new NamedStorageType( className, field, types ) );
            }

            for( MethodInfo method : classFile.getMethods() ) {
                if( method.isStatic() || "<init>".equals( method.getName() ) ) {
                    continue;
                }
                FunctionName funcName = new FunctionName( method );

                int idx = 0;
                // search if the method is already in our list
                for( ; idx < methods.size(); idx++ ) {
                    FunctionName func = methods.get( idx );
                    if( func.methodName.equals( funcName.methodName ) && func.signature.equals( funcName.signature ) ) {
                        methods.set( idx, funcName ); // use the override method
                        functions.markAsNeeded( funcName, false ); // mark all overridden methods also as needed if the super method is used
                        break;
                    }
                }
                if( idx == methods.size() && functions.needToWrite( funcName ) ) {
                    // if a new needed method then add it
                    methods.add( funcName );
                }
                functions.setFunctionIndex( funcName, idx );
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int getCode() {
            return code;
        }

        /**
         * Get the name of the Java type
         * @return the name
         */
        public String getName() {
            return name;
        }

        /**
         * Get the fields of this struct
         * @return the fields
         */
        public List<NamedStorageType> getFields() {
            return fields;
        }

        /**
         * Get the virtual function/methods
         * 
         * @return the methods
         */
        public List<FunctionName> getMethods() {
            return methods;
        }

        /**
         * Set the offset of the vtable in the data section
         * 
         * @param vtableOffset
         *            the offset
         */
        public void setVTable( int vtableOffset ) {
            this.vtableOffset = vtableOffset;
        }

        /**
         * Get the vtable offset.
         * 
         * @return the offset
         */
        public int getVTable() {
            return this.vtableOffset;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString() {
            return "$" + name;
        }
    }
}
