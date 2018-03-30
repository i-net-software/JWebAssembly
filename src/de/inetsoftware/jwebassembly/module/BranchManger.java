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
import java.util.List;

import de.inetsoftware.classparser.CodeInputStream;

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
     */
    void start( BlockOperator op, int startPosition, int offset ) {
        allParsedOperations.add( new ParsedBlock( op, startPosition, offset ) );
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
                default:
                    throw new IllegalStateException( "Unimplemented block code operation: " + parsedBlock.op );
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
            if( parsedBlock.startPosition == gotoPos && parsedBlock.op == BlockOperator.GOTO ) {
                parsedOperations.remove( i );
                branch = new BranchNode( startBlock.startPosition, startBlock.endPosition, BlockOperator.IF, null );
                parent.add( branch );
                if( i > 0 ) {
                    calculate( branch, parsedOperations.subList( 0, i ) );
                }
                endPos = parsedBlock.endPosition;
                branch = new BranchNode( startBlock.endPosition, endPos, BlockOperator.ELSE, BlockOperator.END );
                parent.add( branch );
                break;
            }
            if( parsedBlock.startPosition > gotoPos ) {
                break;
            }
        }

        if( branch == null ) {
            branch = new BranchNode( startBlock.startPosition, endPos, BlockOperator.IF, BlockOperator.END );
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
        private BlockOperator op;

        private int           startPosition;

        private int           endPosition;

        private ParsedBlock( BlockOperator op, int startPosition, int offset ) {
            this.op = op;
            this.startPosition = startPosition;
            this.endPosition = startPosition + offset;
        }
    }

    /**
     * Described a code branch/block node in a tree structure.
     */
    private static class BranchNode extends ArrayList<BranchNode> {

        final int                   startPos;

        final int                   endPos;

        private final BlockOperator startOp;

        private final BlockOperator endOp;

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
        BranchNode( int startPos, int endPos, BlockOperator startOp, BlockOperator endOp ) {
            this.startPos = startPos;
            this.endPos = endPos;
            this.startOp = startOp;
            this.endOp = endOp;
        }

        void handle( int codePositions, ModuleWriter writer ) throws IOException {
            if( codePositions < startPos || codePositions > endPos ) {
                return;
            }
            if( codePositions == startPos && startOp != null ) {
                writer.writeBlockCode( startOp );
            }
            for( BranchNode branch : this ) {
                branch.handle( codePositions, writer );
            }
            if( codePositions == endPos && endOp != null ) {
                writer.writeBlockCode( endOp );
            }
        }
    }
}
