/*
   Copyright 2017 - 2021 Volker Berlin (i-net software)

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

import javax.annotation.Nonnull;

/**
 * @author Volker Berlin
 */
public class TryCatchFinally {

    private int                 start;

    private int                 end;

    private final int           handler;

    private final ConstantClass type;

    /**
     * http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.7.3
     * http://docs.oracle.com/javase/specs/jvms/se5.0/html/ClassFile.doc.html#1546
     *
     * @param input
     * @param constantPool
     * @throws IOException
     */
    TryCatchFinally( DataInputStream input, @Nonnull ConstantPool constantPool ) throws IOException {
        start = input.readUnsignedShort();
        end = input.readUnsignedShort();
        handler = input.readUnsignedShort();
        type = (ConstantClass)constantPool.get( input.readUnsignedShort() );
    }

    public int getStart() {
        return start;
    }

    public int getEnd() {
        return end;
    }

    public int getHandler() {
        return handler;
    }

    public ConstantClass getType() {
        return type;
    }

    public boolean isFinally() {
        return type == null;
    }

    void setStart( int start ) {
        this.start = start;
    }

    void setEnd( int end ) {
        this.end = end;
    }
}
