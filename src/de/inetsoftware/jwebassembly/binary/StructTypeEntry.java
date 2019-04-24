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
package de.inetsoftware.jwebassembly.binary;

import java.io.IOException;
import java.util.List;

import de.inetsoftware.jwebassembly.wasm.NamedStorageType;
import de.inetsoftware.jwebassembly.wasm.ValueType;

/**
 * An struct type entry in the type section of the WebAssembly.
 * 
 * @author Volker Berlin
 */
class StructTypeEntry extends TypeEntry {

    private final List<NamedStorageType> fields;

    /**
     * Create a new instance.
     * 
     * @param fields
     *            the fields of the struct
     */
    StructTypeEntry( List<NamedStorageType> fields ) {
        this.fields = fields;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    ValueType getTypeForm() {
        return ValueType.struct;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    void writeSectionEntryDetails( WasmOutputStream stream ) throws IOException {
        stream.writeVaruint32( this.fields.size() );
        for( NamedStorageType field : this.fields ) {
            stream.writeVarint( 1 ); // 0 - immutable; 1 - mutable 
            stream.writeRefValueType( field.getType() );
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return fields.hashCode();
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
        StructTypeEntry type = (StructTypeEntry)obj;
        return fields.equals( type.fields );
    }
}
