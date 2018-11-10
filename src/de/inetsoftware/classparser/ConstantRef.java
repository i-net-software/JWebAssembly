/*
   Copyright 2017 - 2018 Volker Berlin (i-net software)

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
 * A reference to an member that is described in the constant pool of a Java class.
 * 
 * @author Volker Berlin
 */
public abstract class ConstantRef implements Member {

    private final ConstantClass       constClass;

    private final ConstantNameAndType nameAndType;

    /**
     * Create a new instance.
     * 
     * @param constClass
     *            the class description
     * @param nameAndType
     *            the name and type description
     */
    ConstantRef( ConstantClass constClass, ConstantNameAndType nameAndType ) {
        this.constClass = constClass;
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
        return getConstantClass().getName();
    }

    /**
     * Get the type of the method. For example "(Ljava.lang.String;)I"
     */
    @Override
    public String getType() {
        return nameAndType.getType();
    }

    public ConstantClass getConstantClass() {
        return constClass;
    }
}
