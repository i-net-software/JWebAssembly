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
package de.inetsoftware.jwebassembly.sourcemap;

/**
 * Mapping for Source Map.
 */
public class SourceMapping {
    private int    generatedColumn;

    private int    sourceLine;

    private String sourceFileName;

    /**
     * Create a mapping between a Java code line and a WebAssembly code position
     * 
     * @param generatedColumn
     *            position in WebAssembly
     * @param sourceLine
     *            Java source line
     * @param sourceFileName
     *            Java source file
     */
    public SourceMapping( int generatedColumn, int sourceLine, String sourceFileName ) {
        this.generatedColumn = generatedColumn;
        this.sourceLine = sourceLine - 1; // The first line is coded as zero
        this.sourceFileName = sourceFileName;
    }

    /**
     * The generated column. This is equals to the binary offset in the *.wasm file
     * 
     * @return binary offset
     */
    int getGeneratedColumn() {
        return generatedColumn;
    }

    /**
     * The source line
     * 
     * @return the line number
     */
    int getSourceLine() {
        return sourceLine;
    }

    /**
     * Source file name
     * 
     * @return the name
     */
    String getSourceFileName() {
        return sourceFileName;
    }

    /**
     * Ad an offset to the generated column
     * @param offset the offset
     */
    public void addOffset( int offset ) {
        generatedColumn += offset;
    }
}
