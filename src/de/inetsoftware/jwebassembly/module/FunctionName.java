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

import java.util.Iterator;

import javax.annotation.Nonnull;

import de.inetsoftware.classparser.Member;
import de.inetsoftware.jwebassembly.wasm.AnyType;
import de.inetsoftware.jwebassembly.wasm.ValueTypeParser;

/**
 * Described the name of WebAssembly function.
 * 
 * @author Volker Berlin
 *
 */
public class FunctionName {

    /**
     * The Java class name like "java/lang/String".
     */
    @Nonnull
    public final String className;

    /**
     * The method name like "hashCode".
     */
    @Nonnull
    public final String methodName;

    /**
     * The name in the WebAssembly. For example: "java/lang/String.hashCode"
     */
    @Nonnull
    public final String fullName;

    /**
     * The Java signature which is used in Java byte code to reference the method call. For example: "java/lang/String.hashCode()I"
     */
    @Nonnull
    public final String signatureName;

    /**
     * The signature part. For example: "()I"
     */
    @Nonnull
    public final String signature;

    /**
     * Create a new instance from the given reference in the ConstantPool or parsed method.
     * 
     * @param methodOrField
     *            the Java method
     */
    FunctionName( @Nonnull Member methodOrField ) {
        this( methodOrField, methodOrField.getType() );
    }

    /**
     * Create a new instance from the given reference in the ConstantPool and a special signature.
     * 
     * @param methodOrField
     *            the Java method
     * @param signature
     *            the Java signature
     */
    FunctionName( @Nonnull Member methodOrField, String signature ) {
        this( methodOrField.getClassName(), methodOrField.getName(), signature );
    }

    /**
     * Create a new instance from the given values
     * 
     * @param className
     *            the Java class name
     * @param methodName
     *            the Java method name
     * @param signature
     *            the Java signature
     */
    FunctionName( String className, String methodName, String signature ) {
        this.className = className;
        this.methodName = methodName;
        this.fullName = className + '.' + methodName;
        this.signatureName = fullName + signature;
        this.signature = signature;
    }

    /**
     * Create a new instance from the given values
     * 
     * @param signatureName
     *            the full Java method signature like "com/foo/Bar.method()V"
     */
    public FunctionName( String signatureName ) {
        int idx1 = signatureName.indexOf( '.' );
        this.className = signatureName.substring( 0, idx1 );
        int idx2 = signatureName.indexOf( '(', idx1 );
        this.methodName = signatureName.substring( idx1 + 1, idx2 );
        this.fullName = signatureName.substring( 0, idx2 );
        this.signatureName = signatureName;
        this.signature = signatureName.substring( idx2 );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return signatureName.hashCode();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals( Object obj ) {
        if( this == obj ) {
            return true;
        }
        if( obj == null ) {
            return false;
        }
        // synthetic functions should be replace/equals to real functions.
        if( !(obj instanceof FunctionName) ) {
            return false;
        }
        FunctionName other = (FunctionName)obj;
        return signatureName.equals( other.signatureName );
    }

    /**
     * Get the method signature iterator for parameter and return values.
     * 
     * @param types
     *            the type manager
     * @return the iterator
     */
    @Nonnull
    public Iterator<AnyType> getSignature( TypeManager types ) {
        return new ValueTypeParser( signature, types );
    }
}
