/*
 * Copyright 2021 - 2026 Volker Berlin (i-net software)
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
package de.inetsoftware.jwebassembly.binary;

import java.io.IOException;
import java.util.List;

import de.inetsoftware.jwebassembly.module.TypeManager.StructType;
import de.inetsoftware.jwebassembly.wasm.NamedStorageType;
import de.inetsoftware.jwebassembly.wasm.ValueType;

/**
 * An array type entry in the type section of the WebAssembly.
 * 
 * @author Volker Berlin
 */
class ArrayTypeEntry extends TypeEntry {

    private final StructType type;

    /**
     * Create a new instance.
     * 
     * @param type
     *            the array struct type
     */
    ArrayTypeEntry( StructType type ) {
        this.type = type;
    }

    /**
     * Get the array struct type.
     * 
     * @return the type
     */
    StructType getType() {
        return type;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    ValueType getTypeForm() {
        return ValueType.array;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    void writeSectionEntryDetails( WasmOutputStream stream ) throws IOException {
        List<NamedStorageType> fields = type.getFields();
        stream.writeRefValueType( fields.get( 0 ).getType() );
        stream.writeVarint( 1 ); // 0 - immutable; 1 - mutable 
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        List<NamedStorageType> fields = type.getFields();
        return fields != null ? fields.get( 0 ).hashCode() : 0;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals( Object obj ) {
        if( obj == this ) {
            return true;
        }
        if( obj == null || obj.getClass() != getClass() ) {
            return false;
        }
        ArrayTypeEntry other = (ArrayTypeEntry)obj;
        List<NamedStorageType> fields = type.getFields();
        List<NamedStorageType> otherFields = other.type.getFields();
        if( fields == null ) {
            return otherFields == null;
        }
        return fields.get( 0 ).equals( otherFields.get( 0 ) );
    }
}
