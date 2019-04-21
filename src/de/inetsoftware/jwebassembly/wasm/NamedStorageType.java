/*
 * Copyright 2019 Volker Berlin (i-net software)
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
package de.inetsoftware.jwebassembly.wasm;

import de.inetsoftware.classparser.ConstantRef;
import de.inetsoftware.classparser.FieldInfo;

/**
 * A ValueType with a name for debug information.
 * 
 * @author Volker Berlin
 */
public class NamedStorageType {

    private final AnyType type;

    private final String  name;

    /**
     * Create a new instance
     * 
     * @param className
     *            the parent className of the field
     * @param field
     *            the FieldInfo
     */
    public NamedStorageType( String className, FieldInfo field ) {
        this( field.getType(), className + '.' + field.getName() );
    }

    /**
     * Create a new instance
     * 
     * @param ref
     *            the reference
     */
    public NamedStorageType( ConstantRef ref ) {
        this( ref.getType(), ref.getClassName() + '.' + ref.getName() );
    }

    /**
     * Create a new instance
     * 
     * @param type
     *            the type
     * @param name
     *            the name
     */
    private NamedStorageType( String type, String name ) {
        this.type = new ValueTypeParser( type ).next();
        this.name = name;
    }

    /**
     * Get the type.
     * 
     * @return the type
     */
    public AnyType getType() {
        return type;
    }

    /**
     * Get the global unique name of the field. See
     * https://github.com/lars-t-hansen/moz-gc-experiments/blob/master/version2.md#struct-and-ref-types
     * 
     * @return the name
     */
    public String getUniqueName() {
        return name;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return name.hashCode();
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
        if( getClass() != obj.getClass() ) {
            return false;
        }
        NamedStorageType other = (NamedStorageType)obj;
        return name.equals( other.name );
    }
}
