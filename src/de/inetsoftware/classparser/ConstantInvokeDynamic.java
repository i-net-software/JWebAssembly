/*
   Copyright 2019 - 2020 Volker Berlin (i-net software)

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

/**
 * @author Volker Berlin
 */
public class ConstantInvokeDynamic {

    private final ConstantNameAndType nameAndType;

    private final int                 bootstrapMethodIndex;

    /**
     * Invoke dynamic info in the constant pool.
     * https://docs.oracle.com/javase/specs/jvms/se9/html/jvms-4.html#jvms-4.4.10
     * 
     * @param bootstrapMethodAttrIndex
     *            a valid index into the bootstrap_methods array of the bootstrap method table
     * @param nameAndType
     *            the name and type
     */
    ConstantInvokeDynamic( int bootstrapMethodAttrIndex, ConstantNameAndType nameAndType ) {
        this.bootstrapMethodIndex = bootstrapMethodAttrIndex;
        this.nameAndType = nameAndType;
    }

    /**
     * The simple name of the generated method of the single function interface.
     * 
     * @return the name
     */
    public String getName() {
        return nameAndType.getName();
    }

    /**
     * Get the signature of the factory method. For example "()Ljava.lang.Runnable;" for the lamba expression
     * "<code>Runnable run = () -&gt; foo();</code>"
     * 
     * @return the type
     */
    public String getType() {
        return nameAndType.getType();
    }

    /**
     * Get the index to the bootstrap methods.
     * 
     * @return the index
     */
    public int getBootstrapMethodIndex() {
        return bootstrapMethodIndex;
    }
}
