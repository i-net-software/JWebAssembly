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

import de.inetsoftware.classparser.Member;

/**
 * A generated member that come not from the Java class parser.
 * 
 * @author Volker Berlin
 */
public class SyntheticMember implements Member {

    
    private final String className;
    private final String name;
    private final String type;

    /**
     * Create a new instance
     * 
     * @param className
     *            the className
     * @param name
     *            the name
     * @param type
     *            the Java signature
     */
    SyntheticMember( String className, String name, String type ) {
        this.className = className;
        this.name = name;
        this.type = type;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getName() {
        return name;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getClassName() {
        return className;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getType() {
        return type;
    }
}
