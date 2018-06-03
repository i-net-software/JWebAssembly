/*
   Copyright 2011 - 2018 Volker Berlin (i-net software)

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
package de.inetsoftware.classparser;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;

/**
 * Extends the DataInputStream with a code position.
 * 
 * @author Volker Berlin
 */
public class CodeInputStream extends DataInputStream {

    private Code code;

    /**
     * Create a new instance of CodeInputStream.
     * 
     * @param buf
     *            the buffer with the Java byte code
     * @param offset
     *            the offset in the array
     * @param length
     *            the length
     * @param code
     *            the calling code to get the line numbers
     */
    CodeInputStream( byte[] buf, int offset, int length, Code code ) {
        this( new ByteCodeArrayInputStream( buf, offset, length ) );
        this.code = code;
    }

    private CodeInputStream( ByteCodeArrayInputStream in ) {
        super( in );
    }

    /**
     * Get the code index of the current read position.
     * 
     * @return the position
     */
    public int getCodePosition() {
        return ((ByteCodeArrayInputStream)in).getCodePosition();
    }

    /**
     * Line number in the source code or -1 if not available
     * 
     * @return the line number
     */
    public int getLineNumber() {
        int lineNumber = -1;
        try {
            LineNumberTable lineNumberTable = code.getLineNumberTable();
            if( lineNumberTable != null ) {
                int codePos = getCodePosition();
                for( int i = 0; i < lineNumberTable.size(); i++ ) {
                    int offset = lineNumberTable.getStartOffset( i );
                    if( offset > codePos ) {
                        break;
                    }
                    lineNumber = lineNumberTable.getLineNumber( i );
                }
            }
        } catch( IOException e ) {
            // ignore, line naumber are only needed for debug information
        }
        return lineNumber;
    }

    private static class ByteCodeArrayInputStream extends ByteArrayInputStream {

        ByteCodeArrayInputStream( byte[] buf, int offset, int length ) {
            super( buf, offset, length );
        }

        int getCodePosition() {
            return pos;
        }
    }
}
