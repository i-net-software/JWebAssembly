/*
   Copyright 2020 - 2021 Volker Berlin (i-net software)

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
package de.inetsoftware.classparser;

import java.io.DataInputStream;
import java.io.IOException;

/**
 * https://docs.oracle.com/javase/specs/jvms/se9/html/jvms-4.html#jvms-4.7.23
 * 
 * @author Volker Berlin
 */
public class BootstrapMethod {

    /**
     * Signature and return type of method to be implemented by the function object.
     */
    private String            samMethodType;

    /**
     * A direct method handle describing the implementation method which should be called
     */
    private ConstantMethodRef implMethod;

    /**
     * The signature and return type that should be enforced dynamically at invocation time. This may be the same as
     * {@code samMethodType}, or may be a specialization of it.
     */
    private String            instantiatedMethodType;

    /**
     * Create an instance.
     */
    BootstrapMethod( DataInputStream input, ConstantPool constantPool ) throws IOException {
        //TODO check that it is a known implementation type
        int ref = input.readUnsignedShort();
        //ConstantMethodRef method = (ConstantMethodRef)constantPool.get( ref ); // ever: java/lang/invoke/LambdaMetafactory.metafactory(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;

        int argCount = input.readUnsignedShort(); // ever: 3 parameters

        // the 3 values
        samMethodType = (String)constantPool.get( input.readUnsignedShort() );
        implMethod = (ConstantMethodRef)constantPool.get( input.readUnsignedShort() );
        instantiatedMethodType = (String)constantPool.get( input.readUnsignedShort() );
    }

    /**
     * The real method in the parent class that implements the lambda expression
     * 
     * @return the method
     */
    public ConstantMethodRef getImplMethod() {
        return implMethod;
    }
}
