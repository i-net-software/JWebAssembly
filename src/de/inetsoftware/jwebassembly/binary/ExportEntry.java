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

/**
 * An entry in the function section of the WebAssembly.
 * 
 * @author Volker Berlin
 */
class ExportEntry extends SectionEntry {

    private String       name;

    private ExternalKind kind;

    private int          id;

    /**
     * Create an entry for the export section. This section contains a mapping from the external index to the type
     * signature index.
     * 
     * @param name
     *            the exported name
     * @param kind
     *            the type of exported object
     * @param id
     *            the id inside the list of the related type
     */
    ExportEntry( String name, ExternalKind kind, int id ) {
        this.name = name;
        this.kind = kind;
        this.id = id;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    void writeSectionEntry( WasmOutputStream stream ) throws IOException {
        stream.writeString( name );
        stream.writeVaruint32( kind.ordinal() );
        stream.writeVaruint32( id );
    }
}
