/*
 * Copyright 2017 - 2019 Volker Berlin (i-net software)
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
import java.util.ArrayList;
import java.util.List;

import de.inetsoftware.jwebassembly.wasm.StorageType;
import de.inetsoftware.jwebassembly.wasm.ValueType;

/**
 * An entry in the type section of the WebAssembly.
 * 
 * @author Volker Berlin
 */
class FunctionTypeEntry extends TypeEntry {

    final List<StorageType> params = new ArrayList<>();

    final List<StorageType> results = new ArrayList<>();

    /**
     * {@inheritDoc}
     */
    @Override
    ValueType getTypeForm() {
        return ValueType.func;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    void writeSectionEntryDetails( WasmOutputStream stream ) throws IOException {
        stream.writeVaruint32( this.params.size() );
        for( StorageType valueType : this.params ) {
            stream.writeValueType( valueType );
        }
        stream.writeVaruint32( this.results.size() );
        for( StorageType valueType : this.results ) {
            stream.writeValueType( valueType );
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return params.hashCode() + 31 * results.hashCode();
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
        FunctionTypeEntry type = (FunctionTypeEntry)obj;
        return params.equals( type.params ) && results.equals( type.results );
    }
}
