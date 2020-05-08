/*
   Copyright 2018 - 2020 Volker Berlin (i-net software)

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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.ToIntFunction;

import javax.annotation.Nonnull;

import de.inetsoftware.classparser.ClassFile;
import de.inetsoftware.classparser.ClassFile.Type;
import de.inetsoftware.classparser.ConstantClass;
import de.inetsoftware.classparser.FieldInfo;
import de.inetsoftware.classparser.MethodInfo;
import de.inetsoftware.jwebassembly.JWebAssembly;
import de.inetsoftware.jwebassembly.WasmException;
import de.inetsoftware.jwebassembly.wasm.AnyType;
import de.inetsoftware.jwebassembly.wasm.ArrayType;
import de.inetsoftware.jwebassembly.wasm.LittleEndianOutputStream;
import de.inetsoftware.jwebassembly.wasm.NamedStorageType;
import de.inetsoftware.jwebassembly.wasm.ValueType;

/**
 * Manage the written and to write types (classes)
 * 
 * @author Volker Berlin
 */
public class TypeManager {

    /** name of virtual function table, start with a point for an invalid Java identifier  */
    static final String             FIELD_VTABLE = ".vtable";

    /**
     * Name of field with system hash code, start with a point for an invalid Java identifier.
     */
    static final String             FIELD_HASHCODE = ".hashcode";

    /**
     * Byte position in the type description that contains the offset to the interfaces. Length 4 bytes.
     */
    static final int                TYPE_DESCRIPTION_INTERFACE_OFFSET = 0;

    /**
     * Byte position in the type description that contains the offset to the instanceof list. Length 4 bytes.
     */
    static final int                TYPE_DESCRIPTION_INSTANCEOF_OFFSET = 4;

    /**
     * Byte position in the type description that contains the offset to class name idx. Length 4 bytes.
     */
    static final int                TYPE_DESCRIPTION_TYPE_NAME = 8;

    /**
     * The reserved position on start of the vtable:
     * <li>offset of interface call table (itable)
     * <li>offset of instanceof list
     */
    private static final int        VTABLE_FIRST_FUNCTION_INDEX = 3;

    private static final FunctionName CLASS_CONSTANT_FUNCTION = new FunctionName( "java/lang/Class.classConstant(I)Ljava/lang/Class;" ); 

    /**
     * the list of primitive types. The order is important and must correlate with getPrimitiveClass.
     * 
     * @see ReplacementForClass#getPrimitiveClass(String)
     */
    private static final String[]     PRIMITIVE_CLASSES                  = { "boolean", "byte", "char", "double", "float", "int", "long", "short", "void" };

    private Map<String, StructType> structTypes = new LinkedHashMap<>();

    private Map<AnyType, ArrayType> arrayTypes  = new LinkedHashMap<>();

    private boolean                 isFinish;

    private final WasmOptions       options;

    private int                     typeTableOffset;

    /**
     * Initialize the type manager.
     * 
     * @param options
     *            compiler properties
     */
    TypeManager( WasmOptions options ) {
        this.options = options;
    }

    /**
     * Count of used types
     * 
     * @return the count
     */
    public int size() {
        return structTypes.size();
    }

    /**
     * If the scan phase is finish
     * 
     * @return true, if scan phase is finish
     */
    boolean isFinish() {
        return isFinish;
    }

    /**
     * Scan the hierarchy of the types.
     * 
     * @param classFileLoader
     *            for loading the class files
     * @throws IOException
     *             if any I/O error occur on loading or writing
     */
    void scanTypeHierarchy( ClassFileLoader classFileLoader ) throws IOException {
        for( StructType type : structTypes.values() ) {
            type.scanTypeHierarchy( options.functions, this, classFileLoader );
        }
    }

    /**
     * Finish the prepare and write the types. Now no new types and functions should be added.
     * 
     * @param writer
     *            the targets for the types
     * @param classFileLoader
     *            for loading the class files
     * @throws IOException
     *             if any I/O error occur on loading or writing
     */
    void prepareFinish( ModuleWriter writer, ClassFileLoader classFileLoader ) throws IOException {
        isFinish = true;
        for( StructType type : structTypes.values() ) {
            type.writeStructType( writer, options.functions, this, classFileLoader );
        }

        // write type table
        @SuppressWarnings( "resource" )
        LittleEndianOutputStream dataStream = new LittleEndianOutputStream( writer.dataStream );
        typeTableOffset = writer.dataStream.size();
        for( StructType type : structTypes.values() ) {
            dataStream.writeInt32( type.vtableOffset );
        }
    }

    /**
     * Create an accessor for typeTableOffset and mark it.
     * 
     * @return the function name
     */
    WatCodeSyntheticFunctionName getTypeTableMemoryOffsetFunctionName() {
        WatCodeSyntheticFunctionName offsetFunction =
                        new WatCodeSyntheticFunctionName( "java/lang/Class", "typeTableMemoryOffset", "()I", "", null, ValueType.i32 ) {
                            protected String getCode() {
                                return "i32.const " + typeTableOffset;
                            }
                        };
        options.functions.markAsNeeded( offsetFunction );
        return offsetFunction;
    }

    /**
     * Get the function name to get a constant class.
     * @return the function
     */
    @Nonnull
    FunctionName getClassConstantFunction() {
        return CLASS_CONSTANT_FUNCTION;
    }

    /**
     * Get the StructType. If needed an instance is created.
     * 
     * @param name
     *            the type name like java/lang/Object
     * @return the struct type
     */
    public StructType valueOf( String name ) {
        StructType type = structTypes.get( name );
        if( type == null ) {
            JWebAssembly.LOGGER.fine( "\t\ttype: " + name );
            if( isFinish ) {
                throw new WasmException( "Register needed type after scanning: " + name, -1 );
            }

            if( structTypes.size() == 0 ) {
                for( String primitiveTypeName : PRIMITIVE_CLASSES ) {
                    structTypes.put( primitiveTypeName, new StructType( primitiveTypeName, structTypes.size() ) );
                }
            }

            type = new StructType( name, structTypes.size() );
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
     * Create the FunctionName for a virtual call. The function has 2 parameters (THIS,
     * virtualfunctionIndex) and returns the index of the function.
     * 
     * @return the name
     */
    @Nonnull
    WatCodeSyntheticFunctionName createCallVirtual() {
        return new WatCodeSyntheticFunctionName( //
                        "callVirtual", "local.get 0 " // THIS
                                        + "struct.get java/lang/Object .vtable " // vtable is on index 0
                                        + "local.get 1 " // virtualFunctionIndex
                                        + "i32.add " //
                                        + "i32.load offset=0 align=4 " //
                                        + "return " //
                        , valueOf( "java/lang/Object" ), ValueType.i32, null, ValueType.i32 ); // THIS, virtualfunctionIndex, returns functionIndex
    }

    /**
     * Create the FunctionName for a interface call. The function has 3 parameters (THIS,classIndex,
     * virtualfunctionIndex) and returns the index of the function.
     * 
     * @return the name
     */
    @Nonnull
    WatCodeSyntheticFunctionName createCallInterface() {
        /*
        static int callInterface( OBJECT THIS, int classIndex, int virtualfunctionIndex ) {
            int table = THIS.vtable;
            table += i32_load[table];

            do {
                int nextClass = i32_load[table];
                if( nextClass == classIndex ) {
                    return i32_load[table + virtualfunctionIndex];
                }
                if( nextClass == 0 ) {
                    return -1;//throw new NoSuchMethodError();
                }
                table += i32_load[table + 4];
            } while( true );
        }
         */
        return new WatCodeSyntheticFunctionName( //
                        "callInterface", "local.get 0 " // $THIS
                                        + "struct.get java/lang/Object .vtable " // vtable is on index 0
                                        + "local.tee 3 " // save $table
                                        + "i32.load offset=" + TYPE_DESCRIPTION_INTERFACE_OFFSET + " align=4 " // get offset of itable (int position 0, byte position 0)
                                        + "local.get 3 " // save $table
                                        + "i32.add " // $table += i32_load[$table]
                                        + "local.set 3 " // save $table, the itable start location
                                        + "loop" //
                                        + "  local.get 3" // get $table
                                        + "  i32.load offset=0 align=4"
                                        + "  local.tee 4" // save $nextClass
                                        + "  local.get 1" // get $classIndex
                                        + "  i32.eq" //
                                        + "  if" // $nextClass == $classIndex
                                        + "    local.get 3" // get $table
                                        + "    local.get 2" // get $virtualfunctionIndex
                                        + "    i32.add" // $table + $virtualfunctionIndex
                                        + "    i32.load offset=0 align=4" // get the functionIndex
                                        + "    return" //
                                        + "  end" //
                                        + "  local.get 4" // save $nextClass
                                        + "  i32.eqz" //
                                        + "  if" // current offset == end offset
                                        + "    unreachable" // TODO throw a ClassCastException if exception handling is supported
                                        + "  end" //
                                        + "  local.get 3" // get $table
                                        + "  i32.const 4" //
                                        + "  i32.add" // $table + 4
                                        + "  i32.load offset=0 align=4" // get the functionIndex
                                        + "  local.get 3" // $table
                                        + "  i32.add" // $table += i32_load[table + 4];
                                        + "  local.set 3" // set $table
                                        + "  br 0 " //
                                        + "end " //
                                        + "unreachable" // should never reach
                        , valueOf( "java/lang/Object" ), ValueType.i32, ValueType.i32, null, ValueType.i32 ); // THIS, classIndex, virtualfunctionIndex, returns functionIndex
    }

    /**
     * Create the FunctionName for the INSTANCEOF operation and mark it as used. The function has 2 parameters (THIS,
     * classIndex) and returns true if there is a match.
     * 
     * @return the name
     */
    WatCodeSyntheticFunctionName createInstanceOf() {
        return new WatCodeSyntheticFunctionName( //
                        "instanceof", "local.get 0 " // THIS
                                        + "struct.get java/lang/Object .vtable " // vtable is on index 0
                                        + "local.tee 2 " // save the vtable location
                                        + "i32.load offset=" + TYPE_DESCRIPTION_INSTANCEOF_OFFSET + " align=4 " // get offset of instanceof inside vtable (int position 1, byte position 4)
                                        + "local.get 2 " // get the vtable location
                                        + "i32.add " //
                                        + "local.tee 2 " // save the instanceof location
                                        + "i32.load offset=0 align=4 " // count of instanceof entries
                                        + "i32.const 4 " //
                                        + "i32.mul " //
                                        + "local.get 2 " // get the instanceof location
                                        + "i32.add " //
                                        + "local.set 3 " // save end position
                                        + "loop" //
                                        + "  local.get 2 " // get the instanceof location pointer
                                        + "  local.get 3 " // get the end location
                                        + "  i32.eq" //
                                        + "  if" // current offset == end offset
                                        + "    i32.const 0" // not found
                                        + "    return" //
                                        + "  end" //
                                        + "  local.get 2" // get the instanceof location pointer
                                        + "  i32.const 4" //
                                        + "  i32.add" // increment offset
                                        + "  local.tee 2" // save the instanceof location pointer
                                        + "  i32.load offset=0 align=4" //
                                        + "  local.get 1" // the class index that we search
                                        + "  i32.ne" //
                                        + "  br_if 0 " //
                                        + "end " //
                                        + "i32.const 1 " // class/interface found
                                        + "return " //
                        , valueOf( "java/lang/Object" ), ValueType.i32, null, ValueType.i32 ); // THIS, classIndex, returns boolean
    }

    /**
     * Create the FunctionName for the CAST operation and mark it as used. The function has 2 parameters (THIS,
     * classIndex) and returns this if the type match else it throw an exception.
     * 
     * @return the name
     */
    WatCodeSyntheticFunctionName createCast() {
        return new WatCodeSyntheticFunctionName( //
                        "cast", "local.get 0 " // THIS
                                        + "local.get 1 " // the class index that we search
                                        + "call $.instanceof()V " // the synthetic signature of ArraySyntheticFunctionName
                                        + "i32.eqz " //
                                        + "local.get 0 " // THIS
                                        + "return " //
                                        + "unreachable " // TODO throw a ClassCastException if exception handling is supported
                        , valueOf( "java/lang/Object" ), ValueType.i32, null, valueOf( "java/lang/Object" ) );
    }

    /**
     * A reference to a type.
     * 
     * @author Volker Berlin
     */
    public class StructType implements AnyType {

        private final String           name;

        private final int              classIndex;

        private int                    code = Integer.MAX_VALUE;

        private HashSet<String>        neededFields = new HashSet<>();

        private List<NamedStorageType> fields;

        private List<FunctionName>     methods;

        private Set<StructType>        instanceOFs;

        private Map<StructType,List<FunctionName>> interfaceMethods;

        /**
         * The offset to the vtable in the data section.
         */
        private int                    vtableOffset;

        /**
         * Create a reference to type
         * 
         * @param name
         *            the Java class name
         * @param classIndex
         *            the running index of the class/type
         */
        StructType( String name, int classIndex ) {
            this.name = name;
            this.classIndex = classIndex;
        }

        /**
         * Mark that the field was used in any getter or setter.
         * 
         * @param fieldName
         *            the name of the field
         */
        void useFieldName( NamedStorageType fieldName ) {
            neededFields.add( fieldName.getName() );
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
         * @param classFileLoader
         *            for loading the class files
         * @throws IOException
         *             if any I/O error occur on loading or writing
         */
        private void scanTypeHierarchy( FunctionManager functions, TypeManager types, ClassFileLoader classFileLoader ) throws IOException {
            JWebAssembly.LOGGER.fine( "scan type hierachy: " + name );
            fields = new ArrayList<>();
            methods = new ArrayList<>();
            instanceOFs = new LinkedHashSet<>(); // remembers the order from bottom to top class.
            instanceOFs.add( this );
            interfaceMethods = new LinkedHashMap<>();
            if( classIndex >= PRIMITIVE_CLASSES.length ) {
                HashSet<String> allNeededFields = new HashSet<>();
                listStructFields( name, functions, types, classFileLoader, allNeededFields );
            }
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
         * @param classFileLoader
         *            for loading the class files
         * @throws IOException
         *             if any I/O error occur on loading or writing
         */
        private void writeStructType( ModuleWriter writer, FunctionManager functions, TypeManager types, ClassFileLoader classFileLoader ) throws IOException {
            JWebAssembly.LOGGER.fine( "write type: " + name );
            code = writer.writeStructType( this );
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
         * @param classFileLoader
         *            for loading the class files
         * @param allNeededFields
         *            for recursive call list this all used fields
         * @throws IOException
         *             if any I/O error occur on loading or writing
         */
        private void listStructFields( String className, FunctionManager functions, TypeManager types, ClassFileLoader classFileLoader, HashSet<String> allNeededFields ) throws IOException {
            ClassFile classFile = classFileLoader.get( className );
            if( classFile == null ) {
                throw new WasmException( "Missing class: " + className, -1 );
            }

            // interface does not need to resolve
            if( classFile.getType() == Type.Interface ) {
                return;
            }

            {
                // list all used fields
                StructType type = types.structTypes.get( className );
                if( type != null ) {
                    allNeededFields.addAll( type.neededFields );
                    instanceOFs.add( type );
                }
            }

            // add all interfaces to the instanceof set
            listInterface( classFile, functions, types, classFileLoader );

            // List stuff of super class
            ConstantClass superClass = classFile.getSuperClass();
            if( superClass != null ) {
                String superClassName = superClass.getName();
                listStructFields( superClassName, functions, types, classFileLoader, allNeededFields );
            } else {
                fields.add( new NamedStorageType( ValueType.i32, className, FIELD_VTABLE ) );
                fields.add( new NamedStorageType( ValueType.i32, className, FIELD_HASHCODE ) );
            }

            // list all fields
            for( FieldInfo field : classFile.getFields() ) {
                if( field.isStatic() ) {
                    continue;
                }
                if( !allNeededFields.contains( field.getName() ) ) {
                    continue;
                }
                fields.add( new NamedStorageType( className, field, types ) );
            }

            // calculate the vtable (the function indexes of the virtual methods)
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
                        functions.markAsNeeded( funcName ); // mark all overridden methods also as needed if the super method is used
                        break;
                    }
                }
                if( idx == methods.size() && functions.isUsed( funcName ) ) {
                    // if a new needed method then add it
                    methods.add( funcName );
                }
                if( idx < methods.size() ) {
                    functions.setFunctionIndex( funcName, idx + VTABLE_FIRST_FUNCTION_INDEX );
                }
            }
        }

        /**
         * List all interfaces and and mark all instance methods of used interfaces.
         * 
         * @param classFile
         *            the class file
         * @param functions
         *            the used functions for the vtables of the types
         * @param types
         *            for types of fields
         * @param classFileLoader
         *            for loading the class files
         * @throws IOException
         *             if any I/O error occur on loading or writing
         */
        private void listInterface( ClassFile classFile, FunctionManager functions, TypeManager types, ClassFileLoader classFileLoader ) throws IOException {
            for( ConstantClass interClass : classFile.getInterfaces() ) {
                String interName = interClass.getName();
                StructType type = types.structTypes.get( interName );
                if( type == null ) {
                    continue;
                }
                // add all used interfaces to the instanceof set
                instanceOFs.add( type );

                List<FunctionName> iMethods = interfaceMethods.get( type );
                ClassFile interClassFile = classFileLoader.get( interName );
                for( MethodInfo interMethod : interClassFile.getMethods() ) {
                    FunctionName funcName = new FunctionName( interMethod );
                    if( functions.isUsed( funcName ) ) {
                        MethodInfo method = classFile.getMethod( funcName.methodName, funcName.signature );
                        if( method != null ) {
                            FunctionName methodName = new FunctionName( method );
                            functions.markAsNeeded( methodName );
                            if( iMethods == null ) {
                                interfaceMethods.put( type, iMethods = new ArrayList<>() );
                            }
                            iMethods.add( methodName );
                        }
                    }
                }
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
         * {@inheritDoc}
         */
        @Override
        public boolean isRefType() {
            return true;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean isSubTypeOf( AnyType type ) {
            return type == this || type == ValueType.anyref;
        }

        /**
         * Get the name of the Java type
         * @return the name
         */
        public String getName() {
            return name;
        }

        /**
         * The running index of the class/type for class meta data, instanceof and interface calls.
         * 
         * @return the unique index
         */
        public int getClassIndex() {
            return classIndex;
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
         * Write the struct/class meta data to the datastream and set the offset position.
         * 
         * @param dataStream
         *            the target stream
         * @param getFunctionsID
         *            source for function IDs
         * @throws IOException
         *             should never occur
         * @see TypeManager#TYPE_DESCRIPTION_INTERFACE_OFFSET
         * @see TypeManager#TYPE_DESCRIPTION_INSTANCEOF_OFFSET
         * @see TypeManager#TYPE_DESCRIPTION_TYPE_NAME
         */
        public void writeToStream( ByteArrayOutputStream dataStream, ToIntFunction<FunctionName> getFunctionsID ) throws IOException {
            /*
                 ┌───────────────────────────────────────┐
                 | Offset to the interfaces    [4 bytes] |
                 ├───────────────────────────────────────┤
                 | Offset to the instanceof    [4 bytes] |
                 ├───────────────────────────────────────┤
                 | String id of the class name [4 bytes] |
                 ├───────────────────────────────────────┤
                 | first vtable entry          [4 bytes] |
                 ├───────────────────────────────────────┤
                 |     .....                             |
                 ├───────────────────────────────────────┤
                 | interface calls (itable)              |
                 ├───────────────────────────────────────┤
                 | list of instanceof    [4*(n+1) bytes] |
                 ├───────────────────────────────────────┤
                 |     count of entries        [4 bytes] |
                 ├───────────────────────────────────────┤
                 |     own class id            [4 bytes] |
                 ├───────────────────────────────────────┤
                 |     .....             [4*(n-1) bytes] |
                 └───────────────────────────────────────┘
             */
            this.vtableOffset = dataStream.size();

            LittleEndianOutputStream header = new LittleEndianOutputStream( dataStream );
            LittleEndianOutputStream data = new LittleEndianOutputStream();
            for( FunctionName funcName : methods ) {
                int functIdx = getFunctionsID.applyAsInt( funcName );
                data.writeInt32( functIdx );
            }

            // header position TYPE_DESCRIPTION_INTERFACE_OFFSET
            header.writeInt32( data.size() + VTABLE_FIRST_FUNCTION_INDEX * 4 ); // offset of interface calls
            for( Entry<StructType, List<FunctionName>> entry : interfaceMethods.entrySet() ) {
                data.writeInt32( entry.getKey().getClassIndex() );
                List<FunctionName> iMethods = entry.getValue();
                int nextClassPosition = data.size() + 4 * (1 + iMethods.size());
                data.writeInt32( nextClassPosition );
                for( FunctionName funcName : iMethods ) {
                    int functIdx = getFunctionsID.applyAsInt( funcName );
                    data.writeInt32( functIdx );
                }
            }
            data.writeInt32( 0 ); // no more interface in itable

            // header position TYPE_DESCRIPTION_INSTANCEOF_OFFSET
            header.writeInt32( data.size() + VTABLE_FIRST_FUNCTION_INDEX * 4 ); // offset of instanceeof list
            data.writeInt32( instanceOFs.size() );
            for( StructType type : instanceOFs ) {
                data.writeInt32( type.getClassIndex() );
            }

            int classNameIdx = options.strings.get( getName().replace( '/', '.' ) );
            // header position TYPE_DESCRIPTION_TYPE_NAME
            header.writeInt32( classNameIdx ); // string id of the className

            data.writeTo( dataStream );
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
