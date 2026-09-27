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
     * Frame type enum as defined in JVM specification.
     */
    public enum FrameType {
        SAME_FRAME, SAME_LOCALS_1_STACK_ITEM_FRAME, SAME_LOCALS_1_STACK_ITEM_FRAME_EXTENDED, CHOP_FRAME, SAME_FRAME_EXTENDED, APPEND_FRAME, FULL_FRAME;
    }

    /**
     * A single stack map frame.
     */
    public static class StackMapFrame {

        @Nonnull
        private static final VerificationType[]   EMPTY_TYPES = new VerificationType[0];

        private final FrameType                   frameType;

        private final int                         offsetDelta;

        private final @Nonnull VerificationType[] locals;

        private final @Nonnull VerificationType[] stack;

        private int                               k;

        private StackMapFrame( @Nonnull FrameType frameType, int offsetDelta, @Nonnull VerificationType[] locals, @Nonnull VerificationType[] stack, int k ) {
            this.frameType = frameType;
            this.offsetDelta = offsetDelta;
            this.locals = locals;
            this.stack = stack;
            this.k = k;
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
                return new StackMapFrame( FrameType.SAME_FRAME, frameType, EMPTY_TYPES, EMPTY_TYPES, 0 );
            } else if( frameType <= 127 ) {
                // same_locals_1_stack_item_frame
                int offsetDelta = frameType - 64;
                VerificationType[] stack = { VerificationType.read( input, constantPool ) };
                return new StackMapFrame( FrameType.SAME_LOCALS_1_STACK_ITEM_FRAME, offsetDelta, EMPTY_TYPES, stack, 0 );
            } else if( frameType <= 246 ) {
                // unknown / reserved
            } else if( frameType == 247 ) {
                // same_locals_1_stack_item_frame_extended
                int offsetDelta = input.readUnsignedShort();
                VerificationType[] stack = { VerificationType.read( input, constantPool ) };
                return new StackMapFrame( FrameType.SAME_LOCALS_1_STACK_ITEM_FRAME_EXTENDED, offsetDelta, EMPTY_TYPES, stack, 0 );
            } else if( frameType <= 250 ) {
                // chop_frame
                int offsetDelta = input.readUnsignedShort();
                int k = 251 - frameType;
                return new StackMapFrame( FrameType.CHOP_FRAME, offsetDelta, EMPTY_TYPES, EMPTY_TYPES, k );
            } else if( frameType == 251 ) {
                // same_frame_extended
                int offsetDelta = input.readUnsignedShort();
                return new StackMapFrame( FrameType.SAME_FRAME_EXTENDED, offsetDelta, EMPTY_TYPES, EMPTY_TYPES, 0 );
            } else if( frameType <= 254 ) {
                // append_frame
                int offsetDelta = input.readUnsignedShort();
                int k = frameType - 251;
                VerificationType[] locals = new VerificationType[k];
                for( int i = 0; i < k; i++ ) {
                    locals[i] = VerificationType.read( input, constantPool );
                }
                return new StackMapFrame( FrameType.APPEND_FRAME, offsetDelta, locals, EMPTY_TYPES, 0 );
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
                return new StackMapFrame( FrameType.FULL_FRAME, offsetDelta, locals, stack, 0 );
            }
            throw new IOException( "Unknown frame type: " + frameType );
        }

        /**
         * Get the frame type.
         * 
         * @return the frame type
         */
        public FrameType getFrameType() {
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

        /**
         * Count of removed variables (not slots) for type CHOP_FRAME
         * @return count of removed slots
         */
        public int getK() {
            return k;
        }
    }

    /**
     * A verification type as used in stack map frames.
     */
    public static class VerificationType {

        private static final @Nonnull VerificationType TOP         = new VerificationType( 0, null );

        private static final @Nonnull VerificationType INTEGER     = new VerificationType( 1, null );

        private static final @Nonnull VerificationType FLOAT       = new VerificationType( 2, null );

        private static final @Nonnull VerificationType DOUBLE      = new VerificationType( 3, null );

        private static final @Nonnull VerificationType LONG        = new VerificationType( 4, null );

        private static final @Nonnull VerificationType NULL        = new VerificationType( 5, null );

        private static final @Nonnull VerificationType UNINIT_THIS = new VerificationType( 6, null );

        private static final @Nonnull VerificationType UNINIT      = new VerificationType( 8, null );

        private final int                              tag;

        private final String                           className;

        private VerificationType( int tag, String className ) {
            this.tag = tag;
            this.className = className;
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
        private static VerificationType read( DataInputStream input, ConstantPool constantPool ) throws IOException {
            int tag = input.readUnsignedByte();
            switch( tag ) {
                case 0: // Top
                    return TOP;
                case 1: // Integer
                    return INTEGER;
                case 2: // Float
                    return FLOAT;
                case 3: // Double
                    return DOUBLE;
                case 4: // Long
                    return LONG;
                case 5: // Null
                    return NULL;
                case 6: // UninitializedThis
                    return UNINIT_THIS;
                case 7: // Object
                    int classIdx = input.readUnsignedShort();
                    ConstantClass classConstant = (ConstantClass)constantPool.get( classIdx );
                    String className = classConstant.getName();
                    return new VerificationType( tag, className );
                case 8: // Uninitialized
                    int offset = input.readUnsignedShort();
                    return UNINIT;
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
    }
}