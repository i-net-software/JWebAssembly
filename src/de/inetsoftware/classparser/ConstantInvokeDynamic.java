/*
   Copyright 2019 Volker Berlin (i-net software)

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
public class ConstantInvokeDynamic implements Member {

    private final ConstantNameAndType nameAndType;

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
        this.nameAndType = nameAndType;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getName() {
        return nameAndType.getName();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getClassName() {
        return null;
    }

    /**
     * Get the type of the method. For example "(Ljava.lang.String;)I"
     */
    @Override
    public String getType() {
        return nameAndType.getType();
    }

}