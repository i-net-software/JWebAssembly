/*
   Copyright 2011 - 2017 Volker Berlin (i-net software)

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

import java.io.DataInputStream;
import java.io.IOException;

/**
 * @author Volker Berlin
 */
public class LineNumberTable {

    private final int start_pc[];

    private final int line_number[];

    /**
     * http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.7.12
     * http://docs.oracle.com/javase/specs/jvms/se5.0/html/ClassFile.doc.html#22856
     *
     * @param input
     *            the stream of the class
     * @throws IOException
     */
    LineNumberTable( DataInputStream input ) throws IOException {
        int count = input.readUnsignedShort();
        start_pc = new int[count];
        line_number = new int[count];
        for( int i = 0; i < count; i++ ) {
            start_pc[i] = input.readUnsignedShort();
            line_number[i] = input.readUnsignedShort();
        }
    }

    /**
     * Count of entries
     *
     * @return the count
     */
    public int size() {
        return start_pc.length;
    }

    /**
     * Get the offset of the code
     *
     * @param idx
     *            the table position
     * @return the code offset
     */
    public int getStartOffset( int idx ) {
        return start_pc[idx];
    }

    /**
     * Get the line number
     *
     * @param idx
     *            the table position
     * @return the line number
     */
    public int getLineNumber( int idx ) {
        return line_number[idx];
    }

    /**
     * Get the line number of the last code block.
     * @return
     */
    public int getLastLineNr() {
        return line_number[line_number.length - 1];
    }

    public int getMinLineNr(){
        int min = 0xFFFF;
        for( int nr : line_number ){
            min = Math.min( min, nr );
        }
        return min;
    }

    public int getMaxLineNr(){
        int max = -1;
        for( int nr : line_number ){
            max = Math.max( max, nr );
        }
        return max;
    }

}
