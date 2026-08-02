/*
 * Copyright 2026 Volker Berlin (i-net software)
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

import javax.annotation.Nonnull;

import de.inetsoftware.jwebassembly.wasm.ValueType;

/**
 * A recursive type group in the type section of WebAssembly. A StructType (Java class) that reference recursive must
 * declare in a group. For example:
 * <ul>
 * <li>a TreeNode which has children of TreeNnodes
 * <li>java.lang.Throwable which has a cause of java.lang.Throwable.
 * <li>a main class and its inner classes
 * </ul>
 * For a good performance on loading of the WebAssembly file the groups should be small as possible.
 * 
 * @author Volker Berlin
 */
class RecursiveGroupEntry extends TypeEntry {

    private final List<TypeEntry> entries = new ArrayList<>();

    /**
     * Add a type to the recursive group
     * 
     * @param type
     *            a type
     */
    void add( @Nonnull TypeEntry type ) {
        entries.add( type );
    }

    /**
     * Get the number of types in this group.
     * 
     * @return the count
     */
    int size() {
        return entries.size();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    ValueType getTypeForm() {
        return ValueType.rec;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    void writeSectionEntryDetails( WasmOutputStream stream ) throws IOException {
        stream.writeVaruint32( entries.size() );
        for( TypeEntry entry : entries ) {
            entry.writeSectionEntry( stream );
        }
    }
}
