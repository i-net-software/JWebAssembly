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

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.annotation.Nonnull;

import de.inetsoftware.jwebassembly.wasm.StorageType;

/**
 * Manage the written and to write types (classes)
 * 
 * @author Volker Berlin
 */
class TypeManager {

    private final Map<String, StructType> map = new LinkedHashMap<>();

    /**
     * Use the type in the output.
     * 
     * @param storageType
     *            the reference to a type
     */
    void useType( StorageType storageType ) {
        if( storageType instanceof StructType ) {
            StructType type = (StructType)storageType;
            StructType existingType = map.get( type.name );
            if( existingType == null ) {
                type.code = map.size();
                map.put( type.name, type );
            } else {
                type.code = existingType.code;
            }
        }
    }

    /**
     * Get the registered types in numeric order.
     * 
     * @return the types
     */
    @Nonnull
    Collection<StructType> getTypes() {
        return map.values();
    }

    /**
     * A reference to a type.
     * 
     * @author Volker Berlin
     */
    static class StructType implements StorageType {

        private final String name;

        private int          code;

        /**
         * Create a reference to type
         * 
         * @param name
         *            the Java class name
         */
        StructType( String name ) {
            this.name = name;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int getCode() {
            return code;
        }

        /**
         * Get the name of the Java type
         * @return the name
         */
        public String getName() {
            return name;
        }
    }
}
