/*
   Copyright 2018 - 2019 Volker Berlin (i-net software)

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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import javax.annotation.Nonnull;

import de.inetsoftware.classparser.Code;
import de.inetsoftware.classparser.CodeInputStream;
import de.inetsoftware.classparser.TryCatchFinally;
import de.inetsoftware.jwebassembly.WasmException;
import de.inetsoftware.jwebassembly.wasm.AnyType;
import de.inetsoftware.jwebassembly.wasm.NumericOperator;
import de.inetsoftware.jwebassembly.wasm.ValueType;
import de.inetsoftware.jwebassembly.wasm.WasmBlockOperator;

/**
 * This calculate the goto offsets from Java back to block operations
 * 
 * @author Volker Berlin
 *
 */
class BranchManger {

    private final ArrayList<ParsedBlock>        allParsedOperations = new ArrayList<>();

    private final BranchNode                    root                = new BranchNode( 0, Integer.MAX_VALUE, null, null );

    private final HashMap<Integer, ParsedBlock> loops               = new HashMap<>();

    private final List<WasmInstruction>         instructions;

    private TryCatchFinally[]                   exceptionTable;

    /**
     * Create a branch manager.
     * 
     * @param instructions
     *            the target for instructions
     */
    public BranchManger( List<WasmInstruction> instructions ) {
        this.instructions = instructions;
    }

    /**
     * Remove all branch information for reusing the manager.
     * 
     * @param code
     *            the Java method code
     */
    void reset( @Nonnull Code code ) {
        allParsedOperations.clear();
        root.clear();
        loops.clear();
        root.endPos = code.getCodeSize();
        exceptionTable = code.getExceptionTable();
        for( TryCatchFinally ex : exceptionTable ) {
            allParsedOperations.add( new TryCatchParsedBlock( ex ) );
        }
    }

    /**
     * Add a new Java block operator to handle from this manager.
     * 
     * @param startPosition
     *            the byte position of the start position
     * @param offset
     *            the relative jump position
     * @param nextPosition
     *            the position of the next instruction
     * @param lineNumber
     *            the current line number
     */
    void addGotoOperator( int startPosition, int offset, int nextPosition, int lineNumber ) {
        allParsedOperations.add( new ParsedBlock( JavaBlockOperator.GOTO, startPosition, offset, nextPosition, lineNumber ) );
    }

    /**
     * Add a new IF operator.
     * 
     * @param startPosition
     *            the byte position of the start position
     * @param offset
     *            the relative jump position
     * @param lineNumber
     *            the current line number
     * @param instr
     *            the compare instruction
     */
    void addIfOperator( int startPosition, int offset, int lineNumber, WasmNumericInstruction instr ) {
        allParsedOperations.add( new IfParsedBlock( startPosition, offset, lineNumber, instr ) );
    }

    /**
     * Add a new switch block.
     * 
     * @param startPosition
     *            the byte position of the start position
     * @param offset
     *            the relative jump position
     * @param lineNumber
     *            the current line number
     * @param keys
     *            the values of the cases or null if a tableswitch
     * @param positions
     *            the code positions
     * @param defaultPosition
     *            the code position of the default block
     */
    void addSwitchOperator( int startPosition, int offset, int lineNumber, int[] keys, int[] positions, int defaultPosition ) {
        allParsedOperations.add( new SwitchParsedBlock( startPosition, offset, lineNumber, keys, positions, defaultPosition ) );
    }

    /**
     * Calculate all block operators from the parsed information.
     */
    void calculate() {
        addLoops();
        Collections.sort( allParsedOperations, (a,b) -> Integer.compare( a.startPosition, b.startPosition ) );
        calculate( root, allParsedOperations );
    }

    /**
     * In the compiled Java byte code there is no marker for the start of loop. But we need this marker. That we add a
     * virtual loop operator on the target position of GOTO operators with a negative offset.
     */
    private void addLoops() {
        for( int b = 0; b < allParsedOperations.size(); b++ ) {
            ParsedBlock parsedBlock = allParsedOperations.get( b );
            if( parsedBlock.startPosition > parsedBlock.endPosition ) {
                switch ( parsedBlock.op ) {
                    case GOTO: // do while(true) loop; Continue
                    case IF:   // do while(condition) loop
                        int start = parsedBlock.endPosition;
                        ParsedBlock loop = loops.get( start );
                        if( loop == null ) {
                            loop = new ParsedBlock( JavaBlockOperator.LOOP, start, 0, start, parsedBlock.lineNumber );
                            loops.put( start, loop );
                        }
                        loop.endPosition = parsedBlock.nextPosition;
                        break;
                    default:
                        throw new WasmException( "Unimplemented loop code operation: " + parsedBlock.op, null, null, parsedBlock.lineNumber );
                }
            } else {
                switch ( parsedBlock.op ) {
                    case GOTO:
                        // forward GOTO can be a while(condition) loop.
                        // while(condition) {
                        //    ....
                        // }
                        // will be compiled from Eclipse compiler 4.7 to:
                        // GOTO CONDITION:
                        // START:
                        // ....
                        // CONDITION:
                        // if<cond> START:
                        // we can not match this in WASM because a missing GOTO that we need to move the condition to the start of the loop
                        int nextPos = parsedBlock.nextPosition;
                        for( int n = b + 1; n < allParsedOperations.size(); n++ ) {
                            ParsedBlock nextBlock = allParsedOperations.get( n );
                            if( nextBlock.op == JavaBlockOperator.IF && nextBlock.endPosition == nextPos ) { // Eclipse loop with normal goto
                                int conditionStart = parsedBlock.endPosition;
                                int conditionEnd = nextBlock.nextPosition;
                                convertToLoop( parsedBlock, conditionStart, conditionEnd );
                                allParsedOperations.remove( n );
                                ((IfParsedBlock)nextBlock).negateCompare();
                                break;
                            }
                            if( nextBlock.op == JavaBlockOperator.GOTO && nextBlock.endPosition == nextPos && n > 1 ) { // Eclipse loop with wide goto_w
                                ParsedBlock prevBlock = allParsedOperations.get( n - 1 );
                                if( prevBlock.op == JavaBlockOperator.IF && prevBlock.endPosition == nextBlock.nextPosition ) {
                                    System.err.println( nextBlock );
                                    int conditionStart = parsedBlock.endPosition;
                                    int conditionEnd = prevBlock.nextPosition;
                                    convertToLoop( parsedBlock, conditionStart, conditionEnd );
                                    allParsedOperations.remove( n );
                                    allParsedOperations.remove( n - 1 );
                                    break;
                                }
                            }
                        }
                        break;
                    default:
                }
            }
        }

        allParsedOperations.addAll( loops.values() );
    }

    /**
     * Convert the GOTO block with condition at the end into a loop block and move the condition from the end to the
     * start like wasm it required.
     * 
     * @param gotoBlock
     *            the goto block
     * @param conditionStart
     *            the code position where condition code start
     * @param conditionEnd
     *            the end position
     */
    private void convertToLoop( ParsedBlock gotoBlock, int conditionStart, int conditionEnd ) {
        int conditionNew = gotoBlock.startPosition;
        int nextPos = gotoBlock.nextPosition;
        int conditionIdx = -1;

        int i;
        for( i = 0; i < instructions.size(); i++ ) {
            WasmInstruction instr = instructions.get( i );
            int codePos = instr.getCodePosition();
            if( codePos == nextPos ) {
                conditionIdx = i;
            }
            if( codePos >= conditionEnd ) {
                break;
            }
            if( codePos >= conditionStart ) {
                instr.setCodePosition( conditionNew );
                instructions.remove( i );
                instructions.add( conditionIdx++, instr );
            }
        }

        gotoBlock.op = JavaBlockOperator.LOOP;
        gotoBlock.endPosition = conditionEnd;
        instructions.add( i, new WasmBlockInstruction( WasmBlockOperator.BR, 0, conditionNew, gotoBlock.lineNumber ) );
        instructions.add( conditionIdx++, new WasmBlockInstruction( WasmBlockOperator.BR_IF, 1, conditionNew, gotoBlock.lineNumber  ) );
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
                    calculateIf( parent, (IfParsedBlock)parsedBlock, parsedOperations );
                    break;
                case SWITCH:
                    calculateSwitch( parent, (SwitchParsedBlock)parsedBlock, parsedOperations );
                    break;
                case GOTO:
                    calculateGoto( parent, parsedBlock, parsedOperations );
                    break;
                case LOOP:
                    calculateLoop( parent, parsedBlock, parsedOperations );
                    break;
                case TRY:
                    calculateTry( parent, (TryCatchParsedBlock)parsedBlock, parsedOperations );
                    break;
                default:
                    throw new WasmException( "Unimplemented block code operation: " + parsedBlock.op, null, null, parsedBlock.lineNumber );
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
    private void calculateIf( BranchNode parent, IfParsedBlock startBlock, List<ParsedBlock> parsedOperations ) {
        int i = 0;
        int endPos = Math.min( startBlock.endPosition, parent.endPos );
        int startPos = startBlock.nextPosition;
        if( startPos > endPos ) {
            // the condition in a do while(condition) loop
            parent.add( new BranchNode( startPos, startPos, WasmBlockOperator.BR_IF, null, 0 ) );
            return;
        }
        BranchNode branch = null;
        for( ; i < parsedOperations.size(); i++ ) {
            ParsedBlock parsedBlock = parsedOperations.get( i );
            if( parsedBlock.nextPosition == endPos && parsedBlock.op == JavaBlockOperator.GOTO && parsedBlock.startPosition < parsedBlock.endPosition ) {
                parsedOperations.remove( i );
                int elsePos = startBlock.endPosition;
                // end position can not be outside of the parent
                endPos = Math.min( parsedBlock.endPosition, parent.endPos );

                // special case if there is only one goto in the IF block. Occur with goto_w
                if( parsedBlock.startPosition == startPos ) {
                    int nextPos = Math.min( parsedBlock.endPosition, parent.endPos );
                    for( int j = i; j < parsedOperations.size(); j++ ) {
                        ParsedBlock parsedBlock2 = parsedOperations.get( j );
                        if( parsedBlock2.nextPosition == nextPos && parsedBlock2.op == JavaBlockOperator.GOTO && parsedBlock2.startPosition < parsedBlock2.endPosition ) {
                            parsedOperations.remove( j );
                            elsePos = nextPos;
                            endPos = parsedBlock2.endPosition;
                            startBlock.negateCompare();
                            i = j;
                        }
                    }
                }

                branch = new BranchNode( startPos, elsePos, WasmBlockOperator.IF, null );
                parent.add( branch );
                if( i > 0 ) {
                    calculate( branch, parsedOperations.subList( 0, i ) );
                    i = 0;
                }
                AnyType blockType = calculateBlockType( startPos, branch.endPos );
                branch.data = blockType;

                // if with block type signature must have an else block
                int breakDeep = blockType == ValueType.empty ? calculateBreakDeep( parent, endPos ) : -1;
                if( breakDeep >= 0 ) {
                    branch.endOp = WasmBlockOperator.END;
                    branch.add(  new BranchNode( elsePos, endPos, WasmBlockOperator.BR, null, breakDeep + 1 ) );
                    endPos = branch.endPos;
                } else {
                    branch = new BranchNode( elsePos, endPos, WasmBlockOperator.ELSE, WasmBlockOperator.END );
                    parent.add( branch );
                }
                break;
            }
            if( parsedBlock.nextPosition > endPos ) {
                break;
            }
        }

        if( branch == null ) {
            branch = new BranchNode( startPos, endPos, WasmBlockOperator.IF, WasmBlockOperator.END, ValueType.empty );
            parent.add( branch );
        }
        startBlock.negateCompare();

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
     * Calculate the deep of a break.
     * 
     * @param parent
     *            the current parent node.
     * @param endPos
     *            the end position of the jump
     * @return the deep level for "br" or -1 if there is no parent node with this end position
     */
    private int calculateBreakDeep( BranchNode parent, int endPos ) {
        int deep = -1;
        while( parent != null && parent.endPos == endPos && parent.data == null ) {
            deep++;
            parent = parent.parent;
        }
        return deep;
    }

    /**
     * Calculate the block type. The value type that is on the stack after the block.
     * 
     * @param startPos
     *            the start position of the block
     * @param endPos
     *            the end position of the block
     * @return the value type
     */
    @Nonnull
    private AnyType calculateBlockType( int startPos, int endPos ) {
        ArrayDeque<AnyType> stack = new ArrayDeque<>();
        stack.push( ValueType.empty );
        for( WasmInstruction instr : instructions ) {
            int codePos = instr.getCodePosition();
            if( codePos < startPos ) {
                continue;
            }
            if( codePos >= endPos ) {
                break;
            }
            int popCount = instr.getPopCount();
            for( int p = 0; p < popCount; p++ ) {
                stack.pop();
            }
            AnyType pushValue = instr.getPushValueType();
            if( pushValue != null ) {
                stack.push( pushValue );
            }
        }
        return stack.pop();
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
     * Should be converted to the follow Wasm code for tableswitch:
     *
     * <pre>
     *  block
     *    block
     *      block
     *        get_local 0
     *        i32.const 8
     *        i32.sub
     *        br_table 0 1 
     *      end
     *      i32.const 1
     *      set_local 1
     *      br 1
     *    end
     *    i32.const 9
     *    set_local 1
     *  end
     *  get_local 1
     *  return
     * </pre>
     * 
     * and for lookupswitch
     * 
     * <pre>
     *  block
     *    block
     *      block
     *        get_local 0
     *        tee_local $switch
     *        i32.const 8
     *        i32.eq
     *        br_if 0
     *        br 1 
     *      end
     *      i32.const 1
     *      set_local 1
     *      br 1
     *    end
     *    i32.const 9
     *    set_local 1
     *  end
     *  get_local 1
     *  return
     * </pre>
     *
     * @param parent
     *            the parent branch
     * @param switchBlock
     *            the start block of the if control structure
     * @param parsedOperations
     *            the not consumed operations in the parent branch
     */
    private void calculateSwitch( BranchNode parent, SwitchParsedBlock switchBlock, List<ParsedBlock> parsedOperations ) {
        int startPosition = ((ParsedBlock)switchBlock).startPosition;
        int posCount = switchBlock.positions.length;
        boolean isTable = switchBlock.keys == null;

        // create a helper structure 
        SwitchCase[] cases = new SwitchCase[posCount + 1];
        SwitchCase switchCase = cases[posCount] = new SwitchCase();
        switchCase.key = Long.MAX_VALUE;
        switchCase.position = switchBlock.defaultPosition;
        for( int i = 0; i < switchBlock.positions.length; i++ ) {
            switchCase = cases[i] = new SwitchCase();
            switchCase.key = isTable ? i : switchBlock.keys[i];
            switchCase.position = switchBlock.positions[i];
        }

        // calculate the block number for ever switch case depending its position order
        Arrays.sort( cases, ( a, b ) -> Integer.compare( a.position, b.position ) );
        int blockCount = -1;
        int lastPosition = -1;
        BranchNode brTableNode = null;
        BranchNode blockNode = null;
        for( int i = 0; i < cases.length; i++ ) {
            switchCase = cases[i];
            int currentPosition = switchCase.position;
            if( lastPosition != currentPosition ) {
                if( isTable && blockNode == null ) {
                    blockNode = brTableNode = new BranchNode( currentPosition, currentPosition, WasmBlockOperator.BR_TABLE, null );
                }
                lastPosition = currentPosition;
                blockCount++;
                BranchNode node = new BranchNode( startPosition, currentPosition, WasmBlockOperator.BLOCK, WasmBlockOperator.END );
                if( blockNode != null ) {
                    node.add( blockNode );
                }
                blockNode = node;
            }
            switchCase.block = blockCount;
        }

        // handle the GOTO (breaks) at the end of the CASE blocks. 
        blockCount = 0;
        BranchNode branch = blockNode;
        while( branch.size() > 0 ) {
            BranchNode node = branch.get( 0 );
            blockCount++;

            for( int p = 0; p < parsedOperations.size(); p++ ) {
                ParsedBlock parsedBlock = parsedOperations.get( p );
                if( parsedBlock.startPosition < node.endPos ) {
                    continue;
                }
                if( parsedBlock.startPosition < lastPosition ) {
                    if( parsedBlock.endPosition >= lastPosition && parsedBlock.op == JavaBlockOperator.GOTO ) {
                        parsedOperations.remove( p );
                        lastPosition = parsedBlock.endPosition;
                        branch.add( new BranchNode( parsedBlock.startPosition, parsedBlock.startPosition, WasmBlockOperator.BR, null, blockCount ) );
                        p--;
                    }
                } else {
                    break;
                }
            }
            branch = node;
        }

        // Create the main block around the switch
        BranchNode switchNode = new BranchNode( startPosition, lastPosition, WasmBlockOperator.BLOCK, WasmBlockOperator.END );
        switchNode.add( blockNode );
        parent.add( switchNode );

        // sort back in the natural order and create a br_table 
        Arrays.sort( cases, ( a, b ) -> Long.compare( a.key, b.key ) );
        int[] data = new int[cases.length];
        for( int i = 0; i < data.length; i++ ) {
            data[i] = cases[i].block;
        }
        if( brTableNode != null ) {
            brTableNode.data = data;
        }
    }

    /**
     * Helper structure for caculateSwitch
     */
    private static class SwitchCase {
        long key;
        int position;
        int block;
    }

    /**
     * The not consumed GOTO operators of IF THEN ELSE must be break or continue in a loop.
     * 
     * @param parent
     *            the parent branch
     * @param gotoBlock
     *            the GOTO operation
     * @param parsedOperations
     *            the not consumed operations in the parent branch
     */
    private void calculateGoto ( BranchNode parent, ParsedBlock gotoBlock, List<ParsedBlock> parsedOperations ) {
        int start = gotoBlock.endPosition;
        int end = gotoBlock.startPosition;
        if( end > start ) {
            int deep = 0;
            while( parent != null ) {
                if( parent.startOp == WasmBlockOperator.LOOP && parent.startPos == start ) {
                    parent.add( new BranchNode( end, end, WasmBlockOperator.BR, null, deep ) ); // continue to the start of the loop
                    return;
                }
                parent = parent.parent;
                deep++;
            }
        }
        throw new WasmException( "GOTO code without target loop/block", null, null, gotoBlock.lineNumber );

    }

    /**
     * Calculate the needed nodes for a loop. 
     * @param parent
     *            the parent branch
     * @param loopBlock
     *            the virtual LOOP operation
     * @param parsedOperations
     *            the not consumed operations in the parent branch
     */
    private void calculateLoop ( BranchNode parent, ParsedBlock loopBlock, List<ParsedBlock> parsedOperations ) {
        BranchNode blockNode = new BranchNode( loopBlock.startPosition, loopBlock.endPosition, WasmBlockOperator.BLOCK, WasmBlockOperator.END );
        parent.add( blockNode );
        BranchNode loopNode = new BranchNode( loopBlock.startPosition, loopBlock.endPosition, WasmBlockOperator.LOOP, WasmBlockOperator.END );
        blockNode.add( loopNode );

        int idx = 0;
        for( ; idx < parsedOperations.size(); idx++ ) {
            ParsedBlock parsedBlock = parsedOperations.get( idx );
            if( parsedBlock.startPosition >= loopBlock.endPosition ) {
                break;
            }
        }
        calculate( loopNode, parsedOperations.subList( 0, idx ) );
        // add the "br 0" as last child to receive the right order
//        loopNode.add( new BranchNode( loopBlock.endPosition, loopBlock.endPosition, WasmBlockOperator.BR, null, 0 ) ); // continue to the start of the loop
    }

    /**
     * Calculate the needed nodes for try/catch
     * Sample: The follow Java code:
     * 
     * <pre>
     * try {
     *   code1
     * catch(Exception ex)
     *   code2
     * }
     * code3
     * </pre>
     *
     * Should be converted to the follow Wasm code for tableswitch:
     * 
     * <pre>
     * block
     *   block (result anyref)
     *     try
     *       code1
     *     catch
     *       br_on_exn 1 0
     *       rethrow
     *     end
     *     br 1
     *   end
     *   local.set x
     *   code2
     * end
     * code3
     * </pre>
     *
     * @param parent
     *            the parent branch
     * @param tryBlock
     *            the virtual TRY operation
     * @param parsedOperations
     *            the not consumed operations in the parent branch
     */
    private void calculateTry( BranchNode parent, TryCatchParsedBlock tryBlock, List<ParsedBlock> parsedOperations ) {
        TryCatchFinally tryCatch = tryBlock.tryCatch;

        int gotoPos = tryCatch.getHandler()-3; //tryCatch.getEnd() points some time bevore and some time after the goto 
        int endPos = parent.endPos;
        int idx;
        for( idx = 0; idx < parsedOperations.size(); idx++ ) {
            ParsedBlock parsedBlock = parsedOperations.get( idx );
            if( parsedBlock.startPosition == gotoPos && parsedBlock.op == JavaBlockOperator.GOTO && parsedBlock.startPosition < parsedBlock.endPosition ) {
                parsedOperations.remove( idx );
                endPos = parsedBlock.endPosition;
                break;
            }
            if( parsedBlock.startPosition > gotoPos ) {
                break;
            }
        }
        int startPos = tryBlock.startPosition;
        int catchPos = tryCatch.getHandler();
        BranchNode node = new BranchNode( startPos, endPos, WasmBlockOperator.BLOCK, WasmBlockOperator.END );
        parent.add( node );
        parent = node;

        node = new BranchNode( startPos, catchPos, WasmBlockOperator.BLOCK, WasmBlockOperator.END, ValueType.anyref );
        parent.add( node );
        parent = node;

        BranchNode tryNode = new BranchNode( startPos, catchPos, WasmBlockOperator.TRY, null );
        parent.add( tryNode );
        calculate( tryNode, parsedOperations.subList( 0, idx ) );

        BranchNode catchNode = new BranchNode( catchPos, catchPos, WasmBlockOperator.CATCH, WasmBlockOperator.END );
        parent.add( catchNode );

        if( tryCatch.isFinally() ) {
            catchNode.add( new BranchNode( catchPos, catchPos, WasmBlockOperator.DROP, null ) );
        } else {
            catchNode.add( new BranchNode( catchPos, catchPos, WasmBlockOperator.BR_ON_EXN, null, 1 ) );
            catchNode.add( new BranchNode( catchPos, catchPos, WasmBlockOperator.RETHROW, null ) );
        }

        parent.add( new BranchNode( catchPos, catchPos, WasmBlockOperator.BR, null, 1 ) );
    }

    /**
     * Check if there are a start of a catch block on the code position.
     * 
     * @param codePosition
     *            the code position
     * @return true, if there is a catch block
     */
    boolean isCatch( int codePosition ) {
        for( TryCatchFinally tryCatch : exceptionTable ) {
            if( tryCatch.getHandler() == codePosition ) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check on every instruction position if there any branch is ending
     * 
     * @param byteCode
     *            the byte code stream
     */
    void handle( CodeInputStream byteCode ) {
        int codePosition = -1;
        for( int idx = 0; idx < instructions.size(); idx++ ) {
            WasmInstruction instr = instructions.get( idx );
            int lineNumber;
            int nextCodePosition = instr.getCodePosition();
            if( nextCodePosition <= codePosition ) {
                continue;
            } else {
                codePosition = nextCodePosition;
                lineNumber = instr.getLineNumber();
            }
            idx = root.handle( codePosition, instructions, idx, lineNumber );
        }
        root.handle( byteCode.getCodePosition(), instructions, instructions.size(), byteCode.getLineNumber() );
    }

    /**
     * Description of single block/branch from the parsed Java byte code. The parsed branches are plain.
     */
    private static class ParsedBlock {
        private JavaBlockOperator op;

        int                       startPosition;

        int                       endPosition;

        int                       nextPosition;

        int                       lineNumber;

        private ParsedBlock( JavaBlockOperator op, int startPosition, int offset, int nextPosition, int lineNumber ) {
            this.op = op;
            this.startPosition = startPosition;
            this.endPosition = startPosition + offset;
            this.nextPosition = nextPosition;
            this.lineNumber = lineNumber;
        }
    }

    /**
     * Description of a parsed IF operation.
     */
    private static class IfParsedBlock extends ParsedBlock {

        private WasmNumericInstruction instr;

        /**
         * Create new instance
         * 
         * @param startPosition
         *            the byte position of the start position
         * @param offset
         *            the relative jump position
         * @param lineNumber
         *            the Java line number for possible error messages
         * @param instr
         *            the compare instruction
         */
        private IfParsedBlock( int startPosition, int offset, int lineNumber, WasmNumericInstruction instr ) {
            super( JavaBlockOperator.IF, startPosition, offset, startPosition + 3, lineNumber );
            this.instr = instr;
        }

        /**
         * Negate the compare operation.
         */
        private void negateCompare() {
            NumericOperator newOp;
            switch( instr.numOp ) {
                case eq:
                    newOp = NumericOperator.ne;
                    break;
                case ne:
                    newOp = NumericOperator.eq;
                    break;
                case gt:
                    newOp = NumericOperator.le;
                    break;
                case lt:
                    newOp = NumericOperator.ge;
                    break;
                case le:
                    newOp = NumericOperator.gt;
                    break;
                case ge:
                    newOp = NumericOperator.lt;
                    break;
                case ifnull:
                    newOp = NumericOperator.ifnonnull;
                    break;
                case ifnonnull:
                    newOp = NumericOperator.ifnull;
                    break;
                case ref_eq:
                    newOp = NumericOperator.ref_ne;
                    break;
                case ref_ne:
                    newOp = NumericOperator.ref_eq;
                    break;
                default:
                    throw new WasmException( "Not a compare operation: " + instr.numOp, null, null, lineNumber );
            }
            instr.numOp = newOp;
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
            super( JavaBlockOperator.SWITCH, startPosition, offset, startPosition, lineNumber );
            this.keys = keys;
            this.positions = positions;
            this.defaultPosition = defaultPosition;
        }
    }

    /**
     * Description of a parsed try-Catch structure.
     */
    private static class TryCatchParsedBlock extends ParsedBlock {
        private final TryCatchFinally tryCatch;

        TryCatchParsedBlock( TryCatchFinally tryCatch ) {
            super( JavaBlockOperator.TRY, tryCatch.getStart(), 0, tryCatch.getStart(), -1 );
            this.tryCatch = tryCatch;
        }
    }

    /**
     * Described a code branch/block node in a tree structure.
     */
    private static class BranchNode extends ArrayList<BranchNode> {

        private final int               startPos;

        private       int               endPos;

        private final WasmBlockOperator startOp;

        private       WasmBlockOperator endOp;

        private       Object            data;

        private       BranchNode        parent;

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

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean add( BranchNode e ) {
            e.parent = this;
            return super.add( e );
        }

        /**
         * Handle branches on the current codePosition
         * 
         * @param codePosition
         *            current code position
         * @param instructions
         *            the target for instructions
         * @param idx
         *            index in the current instruction
         * @param lineNumber
         *            the line number in the Java source code
         * @return the new index in the instructions
         */
        int handle(  int codePosition, List<WasmInstruction> instructions, int idx, int lineNumber ) {
            if( codePosition < startPos || codePosition > endPos ) {
                return idx;
            }
            if( codePosition == startPos && startOp != null ) {
                instructions.add( idx++, new WasmBlockInstruction( startOp, data, codePosition, lineNumber ) );
            }
            for( BranchNode branch : this ) {
                idx = branch.handle( codePosition, instructions, idx, lineNumber );
            }
            if( codePosition == endPos && endOp != null ) {
                instructions.add( idx++, new WasmBlockInstruction( endOp, null, codePosition, lineNumber ) );
            }
            return idx;
        }
    }
}
