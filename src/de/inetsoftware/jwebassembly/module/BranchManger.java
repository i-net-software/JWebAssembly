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

import de.inetsoftware.classparser.CodeInputStream;

/**
 * This calculate the goto offsets from Java back to block operations
 * 
 * @author Volker Berlin
 *
 */
class BranchManger {

    private final ArrayList<Block> stack = new ArrayList<>();

    /**
     * Remove all branch information.
     */
    void reset() {
        stack.clear();
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
        stack.add( new Block( op, startPosition, offset ) );
    }

    /**
     * Calculate all block operators from the parsed information.
     */
    void calculate() {
        for( int i = 0; i < stack.size(); i++ ) {
            Block block = stack.get( i );
            switch( block.op ) {
                case IF:
                    caculateIf( i, block );
                    break;
            }
        }
    }

    /**
     * Calculate the ELSE and END position of an IF control structure.
     * 
     * @param i
     *            the index in the stack
     * @param startBlock
     *            the start block of the if control structure
     */
    private void caculateIf( int i, Block startBlock ) {
        i++;
        int gotoPos = startBlock.endPosition - 3; // 3 - byte size of goto instruction
        for( ; i < stack.size(); i++ ) {
            Block block = stack.get( i );
            if( block.startPosition == gotoPos && block.op == BlockOperator.GOTO ) {
                block.op = BlockOperator.ELSE;
                block.startPosition += 3;
                startBlock = block;
                i++;
                break;
            }
            if( block.startPosition > gotoPos ) {
                break;
            }
        }

        /**
         * Search the index in the stack to add the END operator
         */
        int endPos = startBlock.endPosition;
        for( ; i < stack.size(); i++ ) {
            Block block = stack.get( i );
            if( block.startPosition >= endPos ) {
                break;
            }
        }
        stack.add( i, new Block( BlockOperator.END, endPos, 0 ) );
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
        if( stack.isEmpty() ) {
            return;
        }
        int position = byteCode.getCodePosition();
        Block block = stack.get( 0 );
        if( block.startPosition == position ) {
            writer.writeBlockCode( block.op );
            stack.remove( 0 );
        }
    }

    /**
     * Description of single block/branch
     */
    private static class Block {
        private BlockOperator op;

        private int           startPosition;

        private int           endPosition;

        private Block( BlockOperator op, int startPosition, int offset ) {
            this.op = op;
            this.startPosition = startPosition;
            this.endPosition = startPosition + offset;
        }
    }
}
