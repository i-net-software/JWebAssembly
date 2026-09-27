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
package de.inetsoftware.jwebassembly.wasm;

import java.util.Arrays;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import de.inetsoftware.classparser.StackMapTable;
import de.inetsoftware.classparser.StackMapTable.StackMapFrame;
import de.inetsoftware.classparser.StackMapTable.VerificationType;
import de.inetsoftware.jwebassembly.module.TypeManager;

/**
 * Check the StackMapTable for type information of local variables on a continue java code position (PC).
 *
 * @author Volker Berlin
 */
public class StackMapTableParser {

    private final TypeManager types;

    private StackMapFrame[]   frames;

    private int               offset;

    private int               framePos;

    private AnyType[]         locals = new AnyType[64];

    private int               localsCount;

    /**
     * Create an instance
     * 
     * @param types
     *            the type manager
     */
    public StackMapTableParser( @Nonnull TypeManager types ) {
        this.types = types;
    }

    /**
     * Reset the state on beginning of a new method
     * 
     * @param stackMapTable
     *            the parsed StackMapTable or null if it is a synthetic method
     * @param nextSlot
     *            the slots used from the signature
     */
    public void reset( StackMapTable stackMapTable, int nextSlot ) {
        this.frames = stackMapTable != null ? stackMapTable.getFrames() : null;
        this.offset = frames == null || frames.length == 0 ? Integer.MAX_VALUE : frames[0].getOffsetDelta();
        this.framePos = 0;

        if( nextSlot > this.locals.length ) {
            this.locals = Arrays.copyOf( this.locals, nextSlot );
        }
        for( int i = 0; i < nextSlot; i++ ) {
            locals[i] = null;
        }
        this.localsCount = nextSlot;
    }

    /**
     * Get a the type on a specific slot at the given java code position (PC) base on the StackMapTable.
     * 
     * @param slot
     *            the java slot index
     * @param javaCodePos
     *            code position in java byte code (0 - 65.535). Must be increment on recursive calls after a reset.
     * @return the type or null if the StackMapTable does not describe it
     */
    @Nullable
    public AnyType getType( int slot, int javaCodePos ) {
        while( true ) {
            if( javaCodePos < offset ) {
                if( slot < localsCount ) {
                    return locals[slot];
                }
                return null;
            }
            StackMapFrame frame = frames[framePos++];
            switch( frame.getFrameType() ) {
                default:
                case SAME_FRAME:
                case SAME_FRAME_EXTENDED:
                case SAME_LOCALS_1_STACK_ITEM_FRAME:
                case SAME_LOCALS_1_STACK_ITEM_FRAME_EXTENDED:
                    break;
                case FULL_FRAME:
                    localsCount = 0;
                    //$FALL-THROUGH$
                case APPEND_FRAME:
                    VerificationType[] newLocals = frame.getLocals();
                    int newCount = localsCount + newLocals.length;
                    if( newCount > this.locals.length ) {
                        this.locals = Arrays.copyOf( this.locals, newCount );
                    }
                    for( int i = 0; i < newLocals.length; i++ ) {
                        AnyType type = getAnyType( newLocals[i] );
                        locals[localsCount++] = type;
                        if( type == ValueType.f64 || type == ValueType.i64 ) {
                            // on 64bit types there are 2 slots, first the ValueType and then a null
                            newCount++;
                            if( newCount > this.locals.length ) {
                                this.locals = Arrays.copyOf( this.locals, newCount );
                            }
                            locals[localsCount++] = null;
                        }
                    }

                    break;
                case CHOP_FRAME:
                    for( int i = frame.getK() - 1; i >= 0; i-- ) {
                        localsCount--;
                        if( localsCount > 0 ) {
                            AnyType type = locals[localsCount - 1];
                            if( type == ValueType.f64 || type == ValueType.i64 ) {
                                // on 64bit types there are 2 slots, first the ValueType and then a null
                                localsCount--;
                            }
                        }
                    }
                    break;
            }
            offset = framePos < frames.length ? offset + frames[framePos].getOffsetDelta() + 1 : Integer.MAX_VALUE;
        }
    }

    /**
     * Convert a verification type to AnyType.
     * 
     * @param vt
     *            the verification type
     * @return the AnyType or null if Top (64bit)
     */
    @Nullable
    private AnyType getAnyType( @Nonnull StackMapTable.VerificationType vt ) {
        switch( vt.getTag() ) {
            case 0: // Top, (high part of 64bit value)
                return null;
            case 1: // Integer
                return ValueType.i32;
            case 2: // Float
                return ValueType.f32;
            case 3: // Double
                return ValueType.f64;
            case 4: // Long
                return ValueType.i64;
            case 5: // Null
                return ValueType.eqref;
            case 6: // UninitializedThis
                return types.valueOf( "java/lang/Object" );
            case 7: // Object
                return types.valueOf( vt.getClassName() );
            case 8: // Uninitialized
                return types.valueOf( "java/lang/Object" );
            default:
                throw new IllegalStateException();
        }
    }
}
