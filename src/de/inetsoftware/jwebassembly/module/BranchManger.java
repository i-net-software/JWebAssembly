package de.inetsoftware.jwebassembly.module;

import java.io.IOException;
import java.util.ArrayDeque;

import de.inetsoftware.classparser.CodeInputStream;

/**
 * This calculate the goto offsets from Java back to block operations
 * 
 * @author Volker Berlin
 *
 */
class BranchManger {

    private final ArrayDeque<Block> stack = new ArrayDeque<>();

    /**
     * Start a new block.
     * 
     * @param op
     *            the start operation
     * @param basePosition
     *            the byte position of the start position
     * @param offset
     *            the relative jump position
     */
    void start( BlockOperator op, int basePosition, int offset ) {
        stack.push( new Block( op, basePosition, offset ) );
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
        int position = byteCode.getCodePosition();
        Block block = stack.peek();
        if( block != null ) {
            if( block.endPosition == position ) {
                writer.writeBlockCode( BlockOperator.END );
                stack.pop();
            }
        }
    }

    /**
     * Description of single block/branch
     */
    private static class Block {
        private BlockOperator op;

        private int           basePosition;

        private int           endPosition;

        private Block( BlockOperator op, int basePosition, int offset ) {
            this.op = op;
            this.basePosition = basePosition;
            this.endPosition = basePosition + offset;
        }
    }
}
