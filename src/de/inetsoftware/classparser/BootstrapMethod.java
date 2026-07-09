/*
   Copyright 2020 - 2026 Volker Berlin (i-net software)

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
public abstract class BootstrapMethod {

    private final String factoryClassName;

    /**
     * Create an instance via the appropriate subclass based on the bootstrap factory type.
     * 
     * @param input
     *            the data stream of the class file
     * @param constantPool
     *            the constant pool of the class file
     * @return a BootstrapMethod subclass instance
     * @throws IOException
     *             if any error occur
     */
    public static BootstrapMethod create( DataInputStream input, ConstantPool constantPool ) throws IOException {
        int ref = input.readUnsignedShort();
        ConstantMethodRef factoryMethod = (ConstantMethodRef)constantPool.get( ref );

        String factory = factoryMethod.getClassName() + "." + factoryMethod.getName() + factoryMethod.getType();
        switch( factory ) {
            case "java/lang/invoke/LambdaMetafactory.metafactory(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;":
            case "java/lang/invoke/LambdaMetafactory.altMetafactory(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;":
                return new LambdaMetaFactoryBootstrap( input, constantPool, factoryMethod );

            case "java/lang/invoke/StringConcatFactory.makeConcatWithConstants(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;":
                return new StringConcatFactoryBootstrap( input, constantPool, factoryMethod );

            default:
                throw new IOException( "Unknown invoke dynamic bootstrap factory: " + factory );
        }
    }

    /**
     * Create an instance.
     * @param factoryMethod the factory method
     */
    BootstrapMethod( ConstantMethodRef factoryMethod ) {
        factoryClassName = factoryMethod.getClassName();
    }

    /**
     * The name of the bootstrap factory class, e.g. {@code java/lang/invoke/LambdaMetafactory}.
     * 
     * @return the fully qualified class name
     */
    public final String getFactoryClassName() {
        return factoryClassName;
    }
}
