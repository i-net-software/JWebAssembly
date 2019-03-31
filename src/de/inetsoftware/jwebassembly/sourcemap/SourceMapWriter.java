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

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map.Entry;

/**
 * Generates Source Map version 3.
 *
 * https://sourcemaps.info/spec.html
 */
public class SourceMapWriter {

    private List<SourceMapping>                  mappings        = new ArrayList<>();

    private LinkedHashMap<String, Integer> sourceFileNames = new LinkedHashMap<String, Integer>();

    private int                            nextSourceFileNameIndex;

    /**
     * Adds a mapping for the given node. Mappings must be added in order.
     * 
     * @param mapping
     *            the mapping
     */
    public void addMapping( SourceMapping mapping ) {
        if( !sourceFileNames.containsKey( mapping.getSourceFileName() ) ) {
            sourceFileNames.put( mapping.getSourceFileName(), nextSourceFileNameIndex );
            nextSourceFileNameIndex++;
        }

        mappings.add( mapping );
    }

    /**
     * https://sourcemaps.info/spec.html
     * 
     * @param out
     *            the target
     * @throws IOException
     *             if any I/O error occur
     */
    public void generate( Appendable out ) throws IOException {
        out.append( "{\n" );
        appendJsonField( out, "version", "3" );

        // the source file names
        out.append( ",\n" );
        appendJsonField( out, "sources", "[" );
        appendSourceFileNames( out );
        out.append( "]" );

        // WebAssembly does not have symbol names
        out.append( ",\n" );
        appendJsonField( out, "names", "[]" );

        // generate the mappings
        out.append( ",\n" );
        appendJsonField( out, "mappings", "" );
        (new Generator( out )).appendLineMappings();
        out.append( "\n}" );
    }

    /**
     * Write source file names.
     * 
     * @param out
     *            the target
     * @throws IOException
     *             if any I/O error occur
     */
    private void appendSourceFileNames( Appendable out ) throws IOException {
        boolean isFirst = true;
        for( Entry<String, Integer> entry : sourceFileNames.entrySet() ) {
            String key = entry.getKey();
            if( isFirst ) {
                isFirst = false;
            } else {
                out.append( ',' );
            }
            appendQuoteString( out, key );
        }
    }

    /**
     * Write the field name to JSON source map.
     * 
     * @param out
     *            the target
     * @param name
     *            the field name
     * @param value
     *            optional value
     * @throws IOException
     *             if any I/O error occur
     */
    private static void appendJsonField( Appendable out, String name, CharSequence value ) throws IOException {
        out.append( '\"' );
        out.append( name );
        out.append( "\":" );
        out.append( value );
    }

    /**
     * Write a quoted string to the JSON.
     * 
     * @param out
     *            the target
     * @param str
     *            the unquoted string
     * @throws IOException
     *             if any I/O error occur
     */
    private static void appendQuoteString( Appendable out, String str ) throws IOException {
        out.append( '"' );
        for( int i = 0; i < str.length(); i++ ) {
            char ch = str.charAt( i );
            switch( ch ) {
                case '\\':
                case '\"':
                    out.append( '\\' );
                    break;
                default:
                    if( ch <= 0x1f ) {
                        out.append( "\\u" );
                        out.append( Character.forDigit( (ch >> 12) & 0xF, 16 ) );
                        out.append( Character.forDigit( (ch >> 8) & 0xF, 16 ) );
                        out.append( Character.forDigit( (ch >> 4) & 0xF, 16 ) );
                        out.append( Character.forDigit( ch & 0xF, 16 ) );
                        continue;
                    }
            }
            out.append( ch );
        }
        out.append( '\"' );
    }

    /**
     * The generator of the source map
     */
    private class Generator {

        private final Appendable out;

        private int              previousLine = -1;

        private int              previousColumn;

        private int              previousSourceFileNameId;

        private int              previousSourceLine;

        private int              previousSourceColumn;

        /**
         * Create an instance.
         * 
         * @param out
         *            the target for the source map
         */
        Generator( Appendable out ) {
            this.out = out;
        }

        /**
         * Append the mappings to the source map.
         * 
         * @throws IOException
         *             if any I/O error occur
         */
        void appendLineMappings() throws IOException {
            out.append( '\"' );
            for( SourceMapping mapping : mappings ) {
                int generatedLine = 1; // ever 1 for WebAssembly
                int generatedColumn = mapping.getGeneratedColumn();

                if( generatedLine > 0 && previousLine != generatedLine ) {
                    int start = previousLine == -1 ? 0 : previousLine;
                    for( int i = start; i < generatedLine; i++ ) {
                        out.append( ';' );
                    }
                }

                if( previousLine != generatedLine ) {
                    previousColumn = 0;
                } else {
                    out.append( ',' );
                }

                writeEntry( mapping );
                previousLine = generatedLine;
                previousColumn = generatedColumn;
            }
            out.append( ";\"" );
        }

        /**
         * Write a single single mapping to the source map.
         * 
         * @param mapping
         *            the mapping
         * @throws IOException
         *             if any I/O error occur
         */
        void writeEntry( SourceMapping mapping ) throws IOException {
            int column = mapping.getGeneratedColumn();
            Base64VLQ.appendBase64VLQ( out, column - previousColumn );
            previousColumn = column;

            int sourceId = sourceFileNames.get( mapping.getSourceFileName() );
            Base64VLQ.appendBase64VLQ( out, sourceId - previousSourceFileNameId );
            previousSourceFileNameId = sourceId;

            int srcline = mapping.getSourceLine();
            int srcColumn = 0; // ever 0 for Java byte code because the line table does not support columns
            Base64VLQ.appendBase64VLQ( out, srcline - previousSourceLine );
            previousSourceLine = srcline;

            Base64VLQ.appendBase64VLQ( out, srcColumn - previousSourceColumn );
            previousSourceColumn = srcColumn;
        }
    }
}
