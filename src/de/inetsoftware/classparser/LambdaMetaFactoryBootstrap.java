/*
   Copyright 2026 Volker Berlin (i-net software)

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
 * Bootstrap for {@code java.lang.invoke.LambdaMetafactory}.
 * 
 * @author Volker Berlin
 */
public class LambdaMetaFactoryBootstrap extends BootstrapMethod {

    /**
     * Signature and return type of method to be implemented by the function object.
     */
    private final String      samMethodType;

    /**
     * A direct method handle describing the implementation method which should be called
     */
    private final ConstantRef implMethod;

    /**
     * The signature and return type that should be enforced dynamically at invocation time. This may be the same as
     * {@code samMethodType}, or may be a specialization of it.
     */
    private final String      instantiatedMethodType;

    /**
     * Create an instance.
     */
    LambdaMetaFactoryBootstrap( DataInputStream input, ConstantPool constantPool, ConstantMethodRef factoryMethod ) throws IOException {
        super( factoryMethod );
        int argCount = input.readUnsignedShort();

        samMethodType = (String)constantPool.get( input.readUnsignedShort() );
        implMethod = (ConstantRef)constantPool.get( input.readUnsignedShort() );
        instantiatedMethodType = (String)constantPool.get( input.readUnsignedShort() );

        // skip extra parameters
        // argCount is 5 if the method is LambdaMetafactory.altMetafactory
        // occur if the Lambda type has 2 types compound with "&" like in java.time.chrono.AbstractChronology
        for( int i = 3; i < argCount; i++ ) {
            constantPool.get( input.readUnsignedShort() );
        }
    }

    /**
     * Signature and return type of method to be implemented by the function object.
     * 
     * @see java.lang.invoke.LambdaMetafactory#metafactory parameter samMethodType
     * @return the signature
     */
    public String getSamMethodType() {
        return samMethodType;
    }

    /**
     * The real method in the parent class that implements the lambda expression
     * 
     * @return the method (ConstantMethodRef or ConstantInterfaceRef)
     */
    public ConstantRef getImplMethod() {
        return implMethod;
    }
}
