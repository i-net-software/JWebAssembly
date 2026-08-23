/*
   Copyright 2026 Volker Berlin (i-net software)

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
import javax.annotation.Nullable;

/**
 * Parse the StackMapTable attribute.
 * http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.7.4
 * 
 * @author Volker Berlin
 */
public class StackMapTable {

    private final StackMapFrame[] frames;

    /**
     * Create a new instance of the code attribute "StackMapTable".
     * 
     * @param input
     *            the stream of the class
     * @param constantPool
     *            Reference to the current ConstantPool
     * @throws IOException
     *             if any I/O error occurs.
     */
    public StackMapTable( DataInputStream input, @Nonnull ConstantPool constantPool ) throws IOException {
        int count = input.readUnsignedShort();
        frames = new StackMapFrame[count];
        for( int i = 0; i < count; i++ ) {
            frames[i] = StackMapFrame.read( input, constantPool );
        }
    }

    /**
     * Get the stack map frames.
     * 
     * @return the frames
     */
    public StackMapFrame[] getFrames() {
        return frames;
    }

    /**
     * A single stack map frame.
     */
    public static class StackMapFrame {

        private final int             frameType;
        private final int             offsetDelta;
        private final VerificationType[] locals;
        private final VerificationType[] stack;

        private StackMapFrame( int frameType, int offsetDelta, VerificationType[] locals, VerificationType[] stack ) {
            this.frameType = frameType;
            this.offsetDelta = offsetDelta;
            this.locals = locals;
            this.stack = stack;
        }

        /**
         * Read a stack map frame from the input stream.
         * 
         * @param input
         *            the input stream
         * @param constantPool
         *            the constant pool
         * @return the frame
         * @throws IOException
         *             if an I/O error occurs
         */
        static StackMapFrame read( DataInputStream input, ConstantPool constantPool ) throws IOException {
            int frameType = input.readUnsignedByte();
            if( frameType <= 63 ) {
                // same_frame
                return new StackMapFrame( frameType, frameType, new VerificationType[0], new VerificationType[0] );
            } else if( frameType <= 127 ) {
                // same_locals_1_stack_item_frame
                int offsetDelta = frameType - 64;
                VerificationType stackType = VerificationType.read( input, constantPool );
                return new StackMapFrame( frameType, offsetDelta, new VerificationType[0], new VerificationType[]{ stackType } );
            } else if( frameType == 247 ) {
                // same_locals_1_stack_item_frame_extended
                int offsetDelta = input.readUnsignedShort();
                VerificationType stackType = VerificationType.read( input, constantPool );
                return new StackMapFrame( frameType, offsetDelta, new VerificationType[0], new VerificationType[]{ stackType } );
            } else if( frameType <= 250 ) {
                // chop_frame
                int offsetDelta = input.readUnsignedShort();
                int k = 251 - frameType;
                return new StackMapFrame( frameType, offsetDelta, new VerificationType[0], new VerificationType[0] );
            } else if( frameType == 251 ) {
                // same_frame_extended
                int offsetDelta = input.readUnsignedShort();
                return new StackMapFrame( frameType, offsetDelta, new VerificationType[0], new VerificationType[0] );
            } else if( frameType <= 254 ) {
                // append_frame
                int offsetDelta = input.readUnsignedShort();
                int k = frameType - 251;
                VerificationType[] locals = new VerificationType[k];
                for( int i = 0; i < k; i++ ) {
                    locals[i] = VerificationType.read( input, constantPool );
                }
                return new StackMapFrame( frameType, offsetDelta, locals, new VerificationType[0] );
            } else if( frameType == 255 ) {
                // full_frame
                int offsetDelta = input.readUnsignedShort();
                int numLocals = input.readUnsignedShort();
                VerificationType[] locals = new VerificationType[numLocals];
                for( int i = 0; i < numLocals; i++ ) {
                    locals[i] = VerificationType.read( input, constantPool );
                }
                int numStack = input.readUnsignedShort();
                VerificationType[] stack = new VerificationType[numStack];
                for( int i = 0; i < numStack; i++ ) {
                    stack[i] = VerificationType.read( input, constantPool );
                }
                return new StackMapFrame( frameType, offsetDelta, locals, stack );
            }
            throw new IOException( "Unknown frame type: " + frameType );
        }

        /**
         * Get the frame type.
         * 
         * @return the frame type
         */
        public int getFrameType() {
            return frameType;
        }

        /**
         * Get the offset delta from the previous frame.
         * 
         * @return the offset delta
         */
        public int getOffsetDelta() {
            return offsetDelta;
        }

        /**
         * Get the local variable types.
         * 
         * @return the local variable types
         */
        @Nonnull
        public VerificationType[] getLocals() {
            return locals;
        }

        /**
         * Get the stack types.
         * 
         * @return the stack types
         */
        @Nonnull
        public VerificationType[] getStack() {
            return stack;
        }
    }

    /**
     * A verification type as used in stack map frames.
     */
    public static class VerificationType {

        private final int    tag;
        private final String className;
        private final int    offset;

        private VerificationType( int tag, String className, int offset ) {
            this.tag = tag;
            this.className = className;
            this.offset = offset;
        }

        /**
         * Read a verification type from the input stream.
         * 
         * @param input
         *            the input stream
         * @param constantPool
         *            the constant pool
         * @return the verification type
         * @throws IOException
         *             if an I/O error occurs
         */
        static VerificationType read( DataInputStream input, ConstantPool constantPool ) throws IOException {
            int tag = input.readUnsignedByte();
            switch( tag ) {
                case 0: // Top
                case 1: // Integer
                case 2: // Float
                case 3: // Double
                case 4: // Long
                case 5: // Null
                case 6: // UninitializedThis
                    return new VerificationType( tag, null, -1 );
                case 7: // Object
                    int classIdx = input.readUnsignedShort();
                    ConstantClass classConstant = (ConstantClass)constantPool.get( classIdx );
                    String className = classConstant.getName();
                    return new VerificationType( tag, className, -1 );
                case 8: // Uninitialized
                    int offset = input.readUnsignedShort();
                    return new VerificationType( tag, null, offset );
                default:
                    throw new IOException( "Unknown verification type tag: " + tag );
            }
        }

        /**
         * Get the tag of the verification type.
         * 
         * @return the tag
         */
        public int getTag() {
            return tag;
        }

        /**
         * Get the class name for object types.
         * 
         * @return the class name or null
         */
        @Nullable
        public String getClassName() {
            return className;
        }

        /**
         * Get the offset for uninitialized types.
         * 
         * @return the offset or -1
         */
        public int getOffset() {
            return offset;
        }

        /**
         * Check if this is a 64-bit type (long or double).
         * 
         * @return true if 64-bit type
         */
        public boolean is64Bit() {
            return tag == 3 || tag == 4; // Double or Long
        }
    }
}