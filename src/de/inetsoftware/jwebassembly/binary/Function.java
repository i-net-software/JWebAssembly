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

import de.inetsoftware.jwebassembly.sourcemap.SourceMapping;

/**
 * An entry in the function section of the WebAssembly.
 * 
 * @author Volker Berlin
 */
class Function extends SectionEntry {

    int                      id;

    int                      typeId;

    List<String>             paramNames;

    WasmOutputStream         functionsStream;

    ArrayList<SourceMapping> sourceMappings;

    /**
     * {@inheritDoc}
     */
    @Override
    void writeSectionEntry( WasmOutputStream stream ) throws IOException {
        stream.writeVaruint32( this.typeId );
    }

    /**
     * Add code position marker for a source map.
     * 
     * @param streamPosition
     *            the position in the function stream
     * @param javaSourceLine
     *            the position in the Java Source file
     * @param sourceFileName
     *            the name of the Java source file
     */
    void markCodePosition( int streamPosition, int javaSourceLine, String sourceFileName ) {
        if( sourceMappings == null ) {
            sourceMappings = new ArrayList<>();
        }
        sourceMappings.add( new SourceMapping( streamPosition, javaSourceLine, sourceFileName ) );
    }

    /**
     * Add an offset to the marked code position in the source map
     * 
     * @param offset
     *            the offset
     */
    void addCodeOffset( int offset ) {
        if( sourceMappings != null ) {
            for( SourceMapping mapping : sourceMappings ) {
                mapping.addOffset( offset );
            }
        }
    }
}
