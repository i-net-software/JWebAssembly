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

import de.inetsoftware.jwebassembly.wasm.ValueType;

/**
 * An entry in the type section of the WebAssembly.
 * 
 * @author Volker Berlin
 */
abstract class TypeEntry extends SectionEntry {

    /**
     * {@inheritDoc}
     */
    @Override
    final void writeSectionEntry( WasmOutputStream stream ) throws IOException {
        stream.writeValueType( ValueType.func );
        writeSectionEntryDetails( stream );
    }

    /**
     * Get the form of the type.
     * @return the form
     */
    abstract ValueType getTypeForm();

    /**
     * Write this single entry to a section
     * 
     * @param stream
     *            the target
     * @throws IOException
     *             if any I/O error occur
     */
    abstract void writeSectionEntryDetails( WasmOutputStream stream ) throws IOException;

    /**
     * {@inheritDoc}
     */
    @Override
    public abstract int hashCode();

    /**
     * {@inheritDoc}
     */
    @Override
    public abstract boolean equals( Object obj );
}
