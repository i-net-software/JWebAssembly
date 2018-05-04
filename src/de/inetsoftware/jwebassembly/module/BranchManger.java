/*
   Copyright 2018 Volker Berlin (i-net software)

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
package de.inetsoftware.jwebassembly.module;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import de.inetsoftware.classparser.CodeInputStream;
import de.inetsoftware.jwebassembly.WasmException;

/**
 * This calculate the goto offsets from Java back to block operations
 * 
 * @author Volker Berlin
 *
 */
class BranchManger {

    private final ArrayList<ParsedBlock> allParsedOperations = new ArrayList<>();

    private final BranchNode             root                = new BranchNode( 0, Integer.MAX_VALUE, null, null );

    /**
     * Remove all branch information for reusing the manager.
     */
    void reset() {
        allParsedOperations.clear();
        root.clear();
    }

    /**
     * Start a new block.
     * 
     * @param op
     *            the start operation
     * @param startPosition
     *            the byte position of the start position
     * @param offset
     *            the relative jump position
     * @param lineNumber
     *            the current line number
     */
    void start( JavaBlockOperator op, int startPosition, int offset, int lineNumber ) {
        allParsedOperations.add( new ParsedBlock( op, startPosition, offset, lineNumber ) );
    }

    /**
     * Start a new switch block.
     * 
     * @param startPosition
     *            the byte position of the start position
     * @param offset
     *            the relative jump position
     * @param lineNumber
     *            the current line number
     * @param keys
     *            the values of the cases
     * @param positions
     *            the code positions
     * @param the
     *            code position of the default block
     */
    void startSwitch( int startPosition, int offset, int lineNumber, int[] keys, int[] positions, int defaultPosition ) {
        allParsedOperations.add( new SwitchParsedBlock( startPosition, offset, lineNumber, keys, positions, defaultPosition ) );
    }

    /**
     * Calculate all block operators from the parsed information.
     */
    void calculate() {
        calculate( root, allParsedOperations );
    }

    /**
     * Calculate the branch tree for the given branch and parsed sub operations.
     * 
     * @param parent the parent branch/block in the hierarchy.
     * @param parsedOperations the not consumed parsed operations of the parent block. 
     */
    private void calculate( BranchNode parent, List<ParsedBlock> parsedOperations ) {
        while( !parsedOperations.isEmpty() ) {
            ParsedBlock parsedBlock = parsedOperations.remove( 0 );
            switch( parsedBlock.op ) {
                case IF:
                    caculateIf( parent, parsedBlock, parsedOperations );
                    break;
                case SWITCH:
                    caculateSwitch( parent, (SwitchParsedBlock)parsedBlock, parsedOperations );
                    break;
                default:
                    throw new WasmException( "Unimplemented block code operation: " + parsedBlock.op, null, parsedBlock.lineNumber );
            }
        }
    }

    /**
     * Calculate the ELSE and END position of an IF control structure.
     * 
     * @param parent
     *            the parent branch
     * @param startBlock
     *            the start block of the if control structure
     * @param parsedOperations
     *            the not consumed operations in the parent branch
     */
    private void caculateIf( BranchNode parent, ParsedBlock startBlock, List<ParsedBlock> parsedOperations ) {
        int i = 0;
        int endPos = Math.min( startBlock.endPosition, parent.endPos );
        int gotoPos = endPos - 3; // 3 - byte size of goto instruction
        BranchNode branch = null;
        for( ; i < parsedOperations.size(); i++ ) {
            ParsedBlock parsedBlock = parsedOperations.get( i );
            if( parsedBlock.startPosition == gotoPos && parsedBlock.op == JavaBlockOperator.GOTO ) {
                parsedOperations.remove( i );
                branch = new BranchNode( startBlock.startPosition, startBlock.endPosition, WasmBlockOperator.IF, null );
                parent.add( branch );
                if( i > 0 ) {
                    calculate( branch, parsedOperations.subList( 0, i ) );
                }
                endPos = parsedBlock.endPosition;
                branch = new BranchNode( startBlock.endPosition, endPos, WasmBlockOperator.ELSE, WasmBlockOperator.END );
                parent.add( branch );
                break;
            }
            if( parsedBlock.startPosition > gotoPos ) {
                break;
            }
        }

        if( branch == null ) {
            branch = new BranchNode( startBlock.startPosition, endPos, WasmBlockOperator.IF, WasmBlockOperator.END );
            parent.add( branch );
        }

        /**
         * Search the index in the stack to add the END operator
         */
        for( ; i < parsedOperations.size(); i++ ) {
            ParsedBlock parsedBlock = parsedOperations.get( i );
            if( parsedBlock.startPosition >= endPos ) {
                break;
            }
        }
        if( i > 0 ) {
            calculate( branch, parsedOperations.subList( 0, i ) );
        }
    }

    /**
     * Calculate the blocks of a switch.
     * 
     * Sample: The follow Java code:
     * 
     * <pre>
     * int b;
     * switch( a ) {
     *     case 8:
     *         b = 1;
     *         break;
     *     default:
     *         b = 9;
     * }
     * return b;
     * </pre>
     *
     * Should be converted to the follow Wasm code:
     *
     * <pre>
        block
          block
            block
              get_local 0
              i32.const 8
              i32.sub
              br_table 0 1 
            end
            i32.const 1
            set_local 1
            br 1
          end
          i32.const 9
          set_local 1
        end
        get_local 1
        return
     * </pre>
     *
     * @param parent
     *            the parent branch
     * @param switchBlock
     *            the start block of the if control structure
     * @param parsedOperations
     *            the not consumed operations in the parent branch
     */
    private void caculateSwitch( BranchNode parent, SwitchParsedBlock switchBlock, List<ParsedBlock> parsedOperations ) {
    }

    /**
     * Check on every instruction position if there any branch is ending
     * 
     * @param byteCode
     *            the byte code stream
     * @param writer
     *            the current module writer
     * @throws IOException
     *             if any I/O exception occur
     */
    void handle( CodeInputStream byteCode, ModuleWriter writer ) throws IOException {
        root.handle( byteCode.getCodePosition(), writer );
    }

    /**
     * Description of single block/branch from the parsed Java byte code. The parsed branches are plain.
     */
    private static class ParsedBlock {
        private JavaBlockOperator op;

        int                       startPosition;

        int                       endPosition;

        private int               lineNumber;

        private ParsedBlock( JavaBlockOperator op, int startPosition, int offset, int lineNumber ) {
            this.op = op;
            this.startPosition = startPosition;
            this.endPosition = startPosition + offset;
            this.lineNumber = lineNumber;
        }
    }

    /**
     * Description of a parsed switch structure.
     */
    private static class SwitchParsedBlock extends ParsedBlock {
        private int[] keys;

        private int[] positions;

        private int   defaultPosition;

        public SwitchParsedBlock( int startPosition, int offset, int lineNumber, int[] keys, int[] positions, int defaultPosition ) {
            super( JavaBlockOperator.SWITCH, startPosition, offset, lineNumber );
            this.keys = keys;
            this.positions = positions;
            this.defaultPosition = defaultPosition;
        }
    }

    /**
     * Described a code branch/block node in a tree structure.
     */
    private static class BranchNode extends ArrayList<BranchNode> {

        final int                       startPos;

        final int                       endPos;

        private final WasmBlockOperator startOp;

        private final WasmBlockOperator endOp;

        private final Object            data;

        /**
         * Create a new description.
         * 
         * @param startPos
         *            the start position in the Java code. Limit also the children.
         * @param endPos
         *            the end position in the Java code. Limit also the children.
         * @param startOp
         *            The WASM operation on the start position. Can be null if there is nothing in WASM.
         * @param endOp
         *            the WASM operation on the end position. Can be null if there is nothing in WASM.
         */
        BranchNode( int startPos, int endPos, WasmBlockOperator startOp, WasmBlockOperator endOp ) {
            this( startPos, endPos, startOp, endOp, null );
        }

        /**
         * Create a new description.
         * 
         * @param startPos
         *            the start position in the Java code. Limit also the children.
         * @param endPos
         *            the end position in the Java code. Limit also the children.
         * @param startOp
         *            The WASM operation on the start position. Can be null if there is nothing in WASM.
         * @param endOp
         *            the WASM operation on the end position. Can be null if there is nothing in WASM.
         * @param data
         *            extra data depending of the start operator
         */
        BranchNode( int startPos, int endPos, WasmBlockOperator startOp, WasmBlockOperator endOp, Object data ) {
            this.startPos = startPos;
            this.endPos = endPos;
            this.startOp = startOp;
            this.endOp = endOp;
            this.data = data;
        }

        void handle( int codePositions, ModuleWriter writer ) throws IOException {
            if( codePositions < startPos || codePositions > endPos ) {
                return;
            }
            if( codePositions == startPos && startOp != null ) {
                writer.writeBlockCode( startOp, data );
            }
            for( BranchNode branch : this ) {
                branch.handle( codePositions, writer );
            }
            if( codePositions == endPos && endOp != null ) {
                writer.writeBlockCode( endOp, null );
            }
        }
    }
}
