/*
 * Copyright 2017 - 2020 Volker Berlin (i-net software)
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

import javax.annotation.Nonnull;

import de.inetsoftware.jwebassembly.JWebAssembly;
import de.inetsoftware.jwebassembly.javascript.JavaScriptSyntheticFunctionName;
import de.inetsoftware.jwebassembly.wasm.AnyType;
import de.inetsoftware.jwebassembly.wasm.ValueType;

/**
 * The option/properties for the behavior of the compiler.
 * 
 * @author Volker Berlin
 */
public class WasmOptions {

    final FunctionManager         functions = new FunctionManager();

    public final TypeManager      types     = new TypeManager( this );

    public final StringManager    strings   = new StringManager( this );

    final CodeOptimizer           optimizer = new CodeOptimizer();

    private final boolean         debugNames;

    private final boolean         useGC;

    private final boolean         useEH;

    @Nonnull
    private final String          sourceMapBase;

    /**
     * NonGC function for ref_eq polyfill.
     */
    public FunctionName           ref_eq;

    private FunctionName          get_i32;

    private FunctionName          callVirtual;

    private FunctionName          callInterface;

    private SyntheticFunctionName instanceOf;

    private SyntheticFunctionName cast;

    private int                   catchTypeCode;

    private AnyType catchType = new AnyType() {
        @Override
        public int getCode() {
            return catchTypeCode;
        }

        @Override
        public boolean isRefType() {
            return false;
        }

        @Override
        public boolean isSubTypeOf( AnyType type ) {
            return type == this;
        }

        @Override
        public String toString() {
            return "(param exnref)(result anyref)";
        }
    };

    /**
     * Create a new instance of options
     * 
     * @param properties
     *            compiler properties
     */
    public WasmOptions( HashMap<String, String> properties ) {
        debugNames = Boolean.parseBoolean( properties.get( JWebAssembly.DEBUG_NAMES ) );
        useGC = Boolean.parseBoolean( properties.getOrDefault( JWebAssembly.WASM_USE_GC, "false" ) );
        useEH = Boolean.parseBoolean( properties.getOrDefault( JWebAssembly.WASM_USE_EH, "false" ) );
        String base = properties.getOrDefault( JWebAssembly.SOURCE_MAP_BASE, "" );
        if( !base.isEmpty() && !base.endsWith( "/" ) ) {
            base += "/";
        }
        sourceMapBase = base;
    }

    /**
     * Property for adding debug names to the output if true.
     * 
     * @return true, add debug information
     */
    public boolean debugNames() {
        return debugNames;
    }

    /**
     * If the GC feature of WASM should be use or the GC of the JavaScript host.
     * 
     * @return true, use the GC instructions of WASM.
     */
    public boolean useGC() {
        return useGC;
    }

    /**
     * If the exception handling feature of WASM should be use or an unreachable instruction.
     * 
     * @return true, use the EH instructions of WASM; false, generate an unreachable instruction
     */
    public boolean useEH() {
        return useEH;
    }

    /**
     * Get the relative path between the final wasm file location and the source files location.
     * If not empty it should end with a slash like "../../src/main/java/". 
     * @return the path
     */
    @Nonnull
    public String getSourceMapBase() {
        return sourceMapBase;
    }

    /**
     * Register FunctionName "NonGC.get_i32" for frequently access to vtable with non GC mode.
     */
    void registerGet_i32() {
        if( useGC ) {
            return;
        }
        if( get_i32 == null ) {
            SyntheticFunctionName name;
            get_i32 = name = new JavaScriptSyntheticFunctionName( "NonGC", "get_i32", () -> "(a,i) => a[i]", ValueType.externref, ValueType.i32, null, ValueType.i32 );
            functions.markAsNeeded( name );
            functions.markAsImport( name, name.getAnnotation() );
        }
    }

    /**
     * Get the FunctionName for a virtual call and mark it as used. The function has 2 parameters (THIS,
     * virtualfunctionIndex) and returns the index of the function.
     * 
     * @return the name
     */
    @Nonnull
    FunctionName getCallVirtual() {
        FunctionName name = callVirtual;
        if( name == null ) {
            callVirtual = name = types.createCallVirtual();
            functions.markAsNeeded( name );
            registerGet_i32();
        }
        return name;
    }

    /**
     * Get the FunctionName for a virtual call and mark it as used. The function has 2 parameters (THIS,
     * virtualfunctionIndex) and returns the index of the function.
     * 
     * @return the name
     */
    @Nonnull
    FunctionName getCallInterface() {
        FunctionName name = callInterface;
        if( name == null ) {
            callInterface = name = types.createCallInterface();
            functions.markAsNeeded( name );
            registerGet_i32();
        }
        return name;
    }

    /**
     * Get the FunctionName for an INSTANCEOF check and mark it as used. The function has 2 parameters (THIS,
     * classIndex) and returns true or false.
     * 
     * @return the name
     */
    @Nonnull
    SyntheticFunctionName getInstanceOf() {
        SyntheticFunctionName name = instanceOf;
        if( name == null ) {
            instanceOf = name = types.createInstanceOf();
            functions.markAsNeeded( name );
            registerGet_i32();
        }
        return name;
    }

    /**
     * Get the FunctionName for a CAST operation and mark it as used. The function has 2 parameters (THIS, classIndex)
     * and returns THIS or throw an exception.
     * 
     * @return the name
     */
    @Nonnull
    SyntheticFunctionName getCast() {
        SyntheticFunctionName name = cast;
        if( name == null ) {
            cast = name = types.createCast();
            functions.markAsNeeded( name );
            getInstanceOf();
        }
        return name;
    }

    /**
     * The type for a catch block to unboxing the exnref into a anyref
     * 
     * @return the type
     */
    public AnyType getCatchType() {
        return catchType;
    }

    /**
     * Set the dynamic type id for the catch type
     * 
     * @param catchTypeCode
     *            the positive id
     */
    public void setCatchType( int catchTypeCode ) {
        this.catchTypeCode = catchTypeCode;
    }
}
