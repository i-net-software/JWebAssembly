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

import javax.annotation.Nonnull;

import de.inetsoftware.classparser.ConstantRef;
import de.inetsoftware.classparser.MethodInfo;

/**
 * Described the name of WebAssembly function.
 * 
 * @author Volker Berlin
 *
 */
public class FunctionName {

    /**
     * The name in the WebAssembly.
     */
    public final String fullName;

    /**
     * The Java signature which is used in Java byte code to reference the method call.
     */
    public final String signatureName;

    /**
     * Create a new instance from the given parsed Java method.
     * 
     * @param method
     *            the Java method
     */
    FunctionName( @Nonnull MethodInfo method ) {
        String methodName = method.getName();
        String className = method.getDeclaringClassFile().getThisClass().getName();
        fullName = className + '.' + methodName;
        signatureName = fullName + method.getDescription();
    }

    /**
     * Create a new instance from the given reference in the ConstantPool.
     * 
     * @param method
     *            the Java method
     */
    FunctionName( @Nonnull ConstantRef method ) {
        String methodName = method.getName();
        String className = method.getConstantClass().getName();
        fullName = className + '.' + methodName;
        signatureName = fullName + method.getType();
    }
}
