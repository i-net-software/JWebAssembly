/*
   Copyright 2018 - 2022 Volker Berlin (i-net software)

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
import java.util.Iterator;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import de.inetsoftware.classparser.Code;
import de.inetsoftware.classparser.CodeInputStream;
import de.inetsoftware.classparser.ConstantClass;
import de.inetsoftware.classparser.TryCatchFinally;
import de.inetsoftware.jwebassembly.WasmException;
import de.inetsoftware.jwebassembly.module.TypeManager.StructType;
import de.inetsoftware.jwebassembly.module.WasmInstruction.Type;
import de.inetsoftware.jwebassembly.wasm.AnyType;
import de.inetsoftware.jwebassembly.wasm.NumericOperator;
import de.inetsoftware.jwebassembly.wasm.ValueType;
import de.inetsoftware.jwebassembly.wasm.VariableOperator;
import de.inetsoftware.jwebassembly.wasm.WasmBlockOperator;

/**
 * This calculate the goto offsets from Java back to block operations
 * 
 * @author Volker Berlin
 *
 */
class BranchManager {

    private final ArrayList<ParsedBlock>        allParsedOperations = new ArrayList<>();

    private final BranchNode                    root                = new BranchNode( 0, Integer.MAX_VALUE, null, null );

    private final HashMap<Integer, ParsedBlock> loops               = new HashMap<>();

    private final WasmOptions                   options;

    private final List<WasmInstruction>         instructions;

    private final LocaleVariableManager         localVariables;

    private TryCatchFinally[]                   exceptionTable;

    private final ArrayList<BreakBlock>         breakOperations = new ArrayList<>();


    /**
     * Create a branch manager.
     * 
     * @param options
     *            compiler option/properties
     * @param instructions
     *            the target for instructions
     * @param localVariables
     *            the local variables
     */
    public BranchManager( WasmOptions options, List<WasmInstruction> instructions, LocaleVariableManager localVariables ) {
        this.options = options;
        this.instructions = instructions;
        this.localVariables = localVariables;
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
        breakOperations.clear();
        root.endPos = root.elseEndPos = code.getCodeSize();
        exceptionTable = code.getExceptionTable();
        for( TryCatchFinally ex : exceptionTable ) {
            allParsedOperations.add( new TryCatchParsedBlock( ex ) );
        }
    }

    /**
     * Add a new GOTO operator to handle from this manager.
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
     * Add a new RETURN to help analyze structures.
     * 
     * @param startPosition
     *            the byte position of the start position
     * @param nextPosition
     *            the position of the next instruction
     * @param lineNumber
     *            the current line number
     */
    void addReturnOperator( int startPosition, int nextPosition, int lineNumber ) {
        allParsedOperations.add( new ParsedBlock( JavaBlockOperator.RETURN, startPosition, Integer.MAX_VALUE - startPosition, nextPosition, lineNumber ) );
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
        JumpInstruction jump = new JumpInstruction( startPosition + offset, 1, null, startPosition, lineNumber );
        instructions.add( jump );
        allParsedOperations.add( new IfParsedBlock( startPosition, offset, lineNumber, instr, jump ) );
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
        List<ParsedBlock> parsedOperations = allParsedOperations;
        addLoops( parsedOperations );
        Collections.sort( parsedOperations );
        normalizeEmptyThenBlocks( parsedOperations );
        calculate( root, parsedOperations );
        for( BreakBlock breakBlock : breakOperations ) {
            calculateBreak( breakBlock );
        }
    }

    /**
     * In the compiled Java byte code there is no marker for the start of loop. But we need this marker. That we add a
     * virtual loop operator on the target position of GOTO operators with a negative offset.
     * @param parsedOperations
     *            the parsed operations
     */
    private void addLoops( List<ParsedBlock> parsedOperations ) {
        MAIN:
        for( int b = 0; b < parsedOperations.size(); b++ ) {
            ParsedBlock parsedBlock = parsedOperations.get( b );
            if( parsedBlock.startPosition > parsedBlock.endPosition ) {
                switch ( parsedBlock.op ) {
                    case GOTO: // do while(true) loop; Continue
                    case IF:   // do while(condition) loop
                        int start = parsedBlock.endPosition;
                        ParsedBlock loop = loops.get( start );
                        if( loop == null ) {
                            loop = new ParsedBlock( JavaBlockOperator.LOOP, start, 0, start, parsedBlock.lineNumber );
                            loops.put( start, loop );
                            // if a condition form outside of the loop point inside the loop then it must be conditional return and a jump to the loop condition.
                            for( int n = b - 1; n >= 0; n-- ) {
                                ParsedBlock prevBlock = parsedOperations.get( n );
                                switch( prevBlock.op ) {
                                    case IF:
                                        if( prevBlock.startPosition < start && prevBlock.endPosition > start && prevBlock.endPosition < parsedBlock.startPosition ) {
                                            prevBlock.endPosition = start;
                                        }
                                        break;
                                    case LOOP:
                                        if( start == prevBlock.nextPosition ) {
                                            loop.startPosition = prevBlock.startPosition;
                                            parsedBlock.endPosition = prevBlock.startPosition;
                                        }
                                        break;
                                    default:
                                }
                            }
                        }
                        loop.nextPosition = parsedBlock.startPosition; // Jump position for Continue
                        loop.endPosition = parsedBlock.nextPosition;
                        break;
                    default:
                        throw new WasmException( "Unimplemented loop code operation: " + parsedBlock.op, parsedBlock.lineNumber );
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

                        for( int n = b - 1; n >= 0; n-- ) {
                            ParsedBlock block = parsedOperations.get( n );
                            if( block.op == JavaBlockOperator.IF && block.endPosition == nextPos ) {
                                // a normal GOTO before the ELSE
                                continue MAIN;
                            }
                        }

                        for( int n = b + 1; n < parsedOperations.size(); n++ ) {
                            ParsedBlock nextBlock = parsedOperations.get( n );
                            if( nextBlock.op == JavaBlockOperator.IF && nextBlock.endPosition == nextPos ) { // Eclipse loop with normal goto
                                parsedOperations.remove( n );
                                ((IfParsedBlock)nextBlock).negateCompare();

                                int conditionStart = parsedBlock.endPosition;
                                int conditionEnd = nextBlock.nextPosition;
                                if( conditionStart == conditionEnd ) {
                                    // WHILE loop in ELSE block. 
                                    // The GOTO from the ELSE and the jump to the end of the WHILE loop is merged to one GOTO.
                                    for( n = b - 1; n >= 0; n-- ) {
                                        ParsedBlock prevBlock = parsedOperations.get( n );
                                        if( prevBlock.endPosition > nextPos && prevBlock.endPosition < conditionStart ) {
                                            conditionStart = prevBlock.endPosition;
                                            prevBlock.endPosition = parsedBlock.nextPosition;
                                            // Create the second GOTO
                                            parsedOperations.add( b, new ParsedBlock( JavaBlockOperator.GOTO, parsedBlock.startPosition, parsedBlock.endPosition - parsedBlock.startPosition, parsedBlock.nextPosition, parsedBlock.lineNumber ) );
                                            // we have only 3 code positions but need 6 for two GOTO
                                            parsedBlock.startPosition = parsedBlock.nextPosition;
                                            break;
                                        }
                                    }
                                    if( conditionStart == conditionEnd ) {
                                        throw new WasmException( "Loop condition start not found. Jump from " + parsedBlock.startPosition + " to " + parsedBlock.endPosition, parsedBlock.lineNumber );
                                    }
                                }
                                convertToLoop( parsedBlock, conditionStart, conditionEnd );

                                // if conditions that point at the end of the loop for optimization must now point at start.
                                for( n = b - 1; n >= 0; n-- ) {
                                    ParsedBlock prevBlock = parsedOperations.get( n );
                                    if( prevBlock.op == JavaBlockOperator.IF && prevBlock.endPosition == conditionStart ) {
                                        prevBlock.endPosition = parsedBlock.startPosition;
                                    }
                                }
                                break;
                            }
                            if( nextBlock.op == JavaBlockOperator.GOTO && nextBlock.endPosition == nextPos ) { // Eclipse loop with wide goto_w
                                ParsedBlock prevBlock = parsedOperations.get( n - 1 );
                                if( prevBlock.op == JavaBlockOperator.IF && prevBlock.endPosition == nextBlock.nextPosition ) {
                                    int conditionStart = parsedBlock.endPosition;
                                    int conditionEnd = prevBlock.nextPosition;
                                    if( conditionStart >= conditionEnd ) {
                                        // WHILE loop in ELSE block.
                                        // occur with java.math.BigInteger.add(int[],int[]) in Java 8
                                        break;
                                    }
                                    convertToLoop( parsedBlock, conditionStart, conditionEnd );
                                    parsedOperations.remove( n );
                                    parsedOperations.remove( n - 1 );
                                    break;
                                }
                            }
                        }
                        break;
                    default:
                }
            }
        }

        parsedOperations.addAll( loops.values() );
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
            if( codePos == nextPos && conditionIdx < 0 ) {
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
        gotoBlock.nextPosition = conditionStart; // Jump position for Continue
        gotoBlock.endPosition = conditionEnd;
        instructions.add( i, new WasmBlockInstruction( WasmBlockOperator.BR, 0, conditionNew, gotoBlock.lineNumber ) );
        instructions.add( conditionIdx++, new WasmBlockInstruction( WasmBlockOperator.BR_IF, 1, conditionNew, gotoBlock.lineNumber  ) );
    }

    /**
     * Normalize all empty THEN blocks like:
     * 
     * <pre>
     * if (condition) {
     * } else {
     *      ....
     * }
     * </pre>
     * 
     * are changed to: if (!condition) { .... }
     * </pre>
     * The THEN block contains only a single GOTO operation. This operation is removed and the IF condition is negate.
     * The removing of the GOTO operation make it easer to convert it to a valid WebAssembly structure without GOTO.
     * 
     * @param parsedOperations
     *            the parsed operations
     */
    private static void normalizeEmptyThenBlocks( List<ParsedBlock> parsedOperations ) {
        // occur also with cascaded conditional operator like: int result = (a < 0 ? false : a == c ) && (b < 0 ? false : b == c ) ? 17 : 18;
        for( int i = 0; i < parsedOperations.size() - 1; i++ ) {
            ParsedBlock ifBlock = parsedOperations.get( i );
            if( ifBlock.op == JavaBlockOperator.IF ) {
                ParsedBlock nextBlock = parsedOperations.get( i + 1 );
                if( nextBlock.op == JavaBlockOperator.GOTO && nextBlock.startPosition == ifBlock.nextPosition
                                && ifBlock.endPosition == nextBlock.nextPosition ) {
                    ((IfParsedBlock)ifBlock).negateCompare();
                    ifBlock.endPosition = nextBlock.endPosition;
                    parsedOperations.remove( i + 1 );
                }
            }
        }
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
                case RETURN:
                    // noting, only use as alternative GOTO
                    break;
                default:
                    throw new WasmException( "Unimplemented block code operation: " + parsedBlock.op, parsedBlock.lineNumber );
            }
        }
    }

    /**
     * Calculate the operations which are inside the given branch node.
     * @param branch
     *            the parent branch/block in the hierarchy.
     * @param parsedOperations
     *            the not consumed parsed operations of the parent block.
     */
    private void calculateSubOperations( BranchNode branch, List<ParsedBlock> parsedOperations ) {
        int endPos = branch.endPos;
        int idx = 0;
        int size = parsedOperations.size();
        for( ; idx < size; idx++ ) {
            ParsedBlock parsedBlock = parsedOperations.get( idx );
            if( parsedBlock.startPosition >= endPos ) {
                break;
            }
        }
        if( idx > 0 ) {
            calculate( branch, parsedOperations.subList( 0, idx ) );
        }
    }

    /**
     * Add a break to the node if the block jump to the continue position of an outer loop.
     * 
     * @param parent
     *            the container for adding the break
     * @param startBlock
     *            an IF or GOTO block.
     * @return true, if the break was added
     */
    private boolean addBreakIfLoopContinue( BranchNode parent, ParsedBlock startBlock ) {
        BranchNode main = parent;
        int endPos = startBlock.endPosition;
        int breakDeep = 0;
        while( main != null ) {
            if( main.startOp == WasmBlockOperator.LOOP && main.continuePos == endPos ) {
                // possible operation values here are IF and GOTO
                WasmBlockOperator op = startBlock.op == JavaBlockOperator.IF ? WasmBlockOperator.BR_IF : WasmBlockOperator.BR;
                int startPos = startBlock.nextPosition;
                parent.add( new BranchNode( startPos, startPos, op, null, breakDeep ) );
                return true;
            }
            main = main.parent;
            breakDeep++;
        }
        return false;
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
        instructions.remove( startBlock.jump );
        IfPositions positions = searchElsePosition( startBlock, parsedOperations );

        if( addBreakIfLoopContinue( parent, startBlock ) ) {
            return;
        }

        BranchNode main = parent;
        for( int i = 0; i < positions.ifCount; i++ ) {
            IfParsedBlock parsedBlock = (IfParsedBlock)parsedOperations.remove( 0 );
            instructions.remove( parsedBlock.jump );
            if( startBlock.endPosition < positions.thenPos || startBlock.endPosition == positions.elsePos ) {
                // AND concatenation (&& operator)
                int pos = parsedBlock.startPosition + 1; 
                main.add( new BranchNode( startBlock.nextPosition, pos, WasmBlockOperator.IF, null ) );
                main.add( new BranchNode( pos, pos + 1, WasmBlockOperator.ELSE, WasmBlockOperator.END ) );
                startBlock.negateCompare();
                insertConstBeforePosition( 0, pos + 1, parsedBlock.lineNumber ); // 0 --> FALSE for the next if expression
            } else {
                // OR concatenation (|| operator)
                int pos = startBlock.startPosition + 2; // startBlock.startPosition is the position of the compare operation which need before this if construct
                main.add( new BranchNode( pos, startBlock.nextPosition, WasmBlockOperator.IF, null ) );
                BranchNode node = new BranchNode( startBlock.nextPosition, positions.thenPos - 1, WasmBlockOperator.ELSE, WasmBlockOperator.END );
                main.add( node );
                main = node;
                insertConstBeforePosition( 1, pos + 1, startBlock.lineNumber ); // 1 --> TRUE for the next if expression
            }
            startBlock = parsedBlock;
        }

        int i = 0;
        int endPos = Math.min( startBlock.endPosition, parent.endPos );
        int startPos = startBlock.nextPosition;
        if( startPos > endPos ) {
            // the condition in a do while(condition) loop
            int breakDeep = calculateContinueDeep( parent, endPos );
            instructions.add( findIdxOfCodePos( startPos ), new WasmBlockInstruction( WasmBlockOperator.BR_IF, breakDeep, startPos - 1, startBlock.lineNumber ) );
            return;
        }

        BranchNode branch = null;
        for( ; i < parsedOperations.size(); i++ ) {
            ParsedBlock parsedBlock = parsedOperations.get( i );
            if( parsedBlock.nextPosition == positions.elsePos ) {
                boolean endPosFound = false;
                if( parsedBlock.op == JavaBlockOperator.GOTO && parsedBlock.startPosition < parsedBlock.endPosition ) {
                    parsedOperations.remove( i );
                    endPos = parsedBlock.endPosition;
                    endPosFound = true;
                } else if( parsedBlock.op == JavaBlockOperator.RETURN ) {
                    for( int k = 0; k < i; k++ ) {
                        ParsedBlock block = parsedOperations.get( k );
                        if( block.op == JavaBlockOperator.IF ) {
                            endPos = Math.max( endPos, block.endPosition );
                        }
                    }
                    endPosFound = true;
                }
                if( endPosFound ) {
                    // end position can not be outside of the parent
                    endPos = Math.min( endPos, parent.endPos );

                    branch = new BranchNode( startPos, positions.elsePos, WasmBlockOperator.IF, null );
                    parent.add( branch );
                    if( i > 0 ) {
                        calculate( branch, parsedOperations.subList( 0, i ) );
                        i = 0;
                    }

                    if( addBreakIfLoopContinue( branch, parsedBlock ) ) {
                        branch.endOp = WasmBlockOperator.END;
                        endPos = branch.endPos;
                        break;
                    }
                    // if with block type signature must have an else block
                    int breakDeep = calculateBreakDeep( parent, endPos );
                    if( breakDeep > 0 ) {
                        branch.endOp = WasmBlockOperator.END;
                        branch.add( new BranchNode( positions.elsePos, endPos, WasmBlockOperator.BR, null, breakDeep + 1 ) );
                        endPos = branch.endPos;
                    } else {
                        branch.elseEndPos = endPos;
                        if( positions.elsePos == endPos ) {
                            // we does not need an empty else branch
                            branch.endOp = WasmBlockOperator.END;
                        } else {
                            branch = new BranchNode( positions.elsePos, endPos, WasmBlockOperator.ELSE, WasmBlockOperator.END );
                            parent.add( branch );
                        }
                    }
                    break;
                }
            }
            if( parsedBlock.nextPosition >= positions.elsePos ) {
                break;
            }
        }

        if( branch == null ) {
            if( startBlock.endPosition > parent.endPos ) {
                // we jump outside the parent. This is like a conditional break.
                breakOperations.add( new BreakBlock( WasmBlockOperator.BR_IF, parent, startPos, startBlock.endPosition ) );
                return;
            }
            branch = new BranchNode( startPos - 1, endPos, WasmBlockOperator.IF, WasmBlockOperator.END, ValueType.empty );
            parent.add( branch );
        }
        startBlock.negateCompare();

        calculateSubOperations( branch, parsedOperations );
    }

    /**
     * Search the start positions of the THEN and ELSE branch from an IF control structure.
     * 
     * @param startBlock
     *            the start block of the IF control structure
     * @param parsedOperations
     *            the not consumed operations in the parent branch
     * @return the calculated positions
     */
    private IfPositions searchElsePosition( IfParsedBlock startBlock, List<ParsedBlock> parsedOperations ) {
        int parsedOpCount = parsedOperations.size();

        // find the end position of the else block
        int endElse = startBlock.endPosition;
        boolean newElsePositionFound = true;
        LOOP:
        for( int i = 0; i < parsedOpCount; i++ ) {
            ParsedBlock parsedBlock;
            if( newElsePositionFound ) {
                newElsePositionFound = false;
                for( int j = i; j < parsedOpCount; j++ ) {
                    parsedBlock = parsedOperations.get( j );
                    switch( parsedBlock.op ) {
                        case IF:
                            break;
                        case GOTO:
                            if( parsedBlock.nextPosition == endElse ) {
                                break LOOP;
                            }
                            break;
                        default:
                            if( parsedBlock.nextPosition < endElse ) {
                                break LOOP;
                            }
                    }
                }
            }
            parsedBlock = parsedOperations.get( i );
            switch( parsedBlock.op ) {
                case IF:
                case GOTO:
                    if( parsedBlock.startPosition < endElse && endElse < parsedBlock.endPosition ) {
                        endElse = parsedBlock.endPosition;
                        newElsePositionFound = true;
                    }
                    break;
            }
            if( parsedBlock.startPosition > endElse ) {
                parsedOpCount = i;
                break;
            }
        }

        // first search for the first GOTO, any IF that point after the GOTO can not be part of the primary IF condition.
        // This occur with a second IF inside of the THEN. This can jump directly to the end of the ELSE.
        for( int i = 0; i < parsedOpCount; i++ ) {
            ParsedBlock parsedBlock = parsedOperations.get( i );
            if( parsedBlock.op == JavaBlockOperator.GOTO ) {
                // find the last IF that point to this GOTO
                int gotoNext = parsedBlock.nextPosition;
                for( ; i > 0; i-- ) {
                    parsedBlock = parsedOperations.get( i-1 );
                    if( parsedBlock.endPosition == gotoNext ) {
                        break;
                    }
                }
                parsedOpCount = i;
                break;
            }
        }

        int ifCount = 0;
        int thenPos = startBlock.nextPosition;
        int elsePos = startBlock.endPosition;
        for( ; ifCount < parsedOpCount; ifCount++ ) {
            ParsedBlock parsedBlock = parsedOperations.get( ifCount );
            if( parsedBlock.op != JavaBlockOperator.IF || parsedBlock.endPosition > endElse ) {
                // seems a second IF inside the THEN part.
                break;
            }
            if( parsedBlock.endPosition < elsePos ) {
                // The IF jumps not to ELSE part. This can be an inner IF or it is a combination of (||) and (&&) operation
                boolean isContinue = false;
                for( int i = ifCount + 1; i < parsedOpCount; i++ ) {
                    ParsedBlock op = parsedOperations.get( i );
                    if( op.endPosition >= elsePos ) {
                        isContinue = op.op == JavaBlockOperator.IF;
                        break;
                    }
                }
                if( !isContinue ) {
                    // really seems a second IF within the THEN part.
                    break;
                }
            }
            if( parsedBlock.nextPosition > elsePos ) {
                // occur if there are 2 IF blocks without ELSE behind the other, this IF is the second that we can cancel the analyze at this point
                break;
            }
            thenPos = Math.max( thenPos, parsedBlock.nextPosition );
            elsePos = Math.max( elsePos, parsedBlock.endPosition );
        }
        IfPositions pos = new IfPositions();
        pos.ifCount = ifCount;
        pos.thenPos = thenPos;
        pos.elsePos = elsePos;
        return pos;
    }

    /**
     * Insert a constant i32 operation before the given code position
     * 
     * @param constant
     *            the i32 value
     * @param pos
     *            the code position
     * @param lineNumber
     *            the line number for possible error messages
     */
    private void insertConstBeforePosition( int constant, int pos, int lineNumber ) {
        instructions.add( findIdxOfCodePos( pos ), new WasmConstInstruction( constant, pos - 1, lineNumber ) );
    }

    /**
     * Find the index of the instruction with the given code position.
     * 
     * @param codePosition
     *            the java position
     * @return the index
     */
    private int findIdxOfCodePos( int codePosition ) {
        int size = instructions.size();
        for( int i = 0; i < size; i++ ) {
            if( instructions.get( i ).getCodePosition() >= codePosition ) {
                return i;
            }
        }
        return size;
    }

    /**
     * Calculate the break deep for a continue in a do while(condition) loop.
     * 
     * @param parent
     *            current branch
     * @param startPos
     *            the start position of the loop
     * @return the deep count
     */
    private int calculateContinueDeep( BranchNode parent, int startPos ) {
        int deep = 0;
        while( parent != null && parent.startPos > startPos ) {
            deep++;
            parent = parent.parent;
        }
        return deep;
    }

    /**
     * Calculate the deep of a break. A GOTO or IF in Java can jump out multiple loops. We need to calculate how many levels we need to jump.
     * 
     * @param parent
     *            the current parent node.
     * @param endPos
     *            the end position of the jump
     * @return the deep level for "br" or -1 if there is no parent node with this end position
     */
    private int calculateBreakDeep( BranchNode parent, int endPos ) {
        int deep = -1;
        boolean wasLoop = false;
        while( parent != null && parent.elseEndPos <= endPos && parent.data == null ) {
            deep++;
            wasLoop |= parent.startOp == WasmBlockOperator.LOOP; // only in a loop we need to jump for exit
            parent = parent.parent;
        }
        return wasLoop ? deep : -1;
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

        // add extra blocks for forward GOTO jumps like in SWITCH of Strings 
        for( int p = 0; p < parsedOperations.size(); p++ ) {
            ParsedBlock parsedBlock = parsedOperations.get( p );
            int start = parsedBlock.startPosition;
            if( start >= lastPosition ) {
                break;
            }
            if( parsedBlock.op != JavaBlockOperator.IF ) {
                continue;
            }
            int end = parsedBlock.endPosition;
            if( start < end ) {
                BranchNode branch = blockNode;
                while( branch.size() > 0 ) {
                    BranchNode node = branch.get( 0 );
                    if( start > node.endPos ) {
                        if( end > branch.endPos ) {
                            BranchNode parentNode = branch;
                            while( parentNode != null && end > parentNode.endPos ) {
                                parentNode = parentNode.parent;
                            }
                            BranchNode middleNode = new BranchNode( startPosition, end, WasmBlockOperator.BLOCK, WasmBlockOperator.END );
                            if( parentNode != null ) {
                                BranchNode child = parentNode.remove( 0 );
                                parentNode.add( middleNode );
                                middleNode.add( child );
                                patchBrDeep( middleNode );
                            } else {
                                // occur if the default is not the last case in the switch
                                middleNode.add( blockNode );
                                blockNode = middleNode;
                                lastPosition = end;
                            }
                        }
                        break;
                    }
                    branch = node;
                }
            }
        }

        // handle the GOTO (breaks) at the end of the CASE blocks.
        int lastBlockPosition = lastPosition;
        for( Iterator<ParsedBlock> it = parsedOperations.iterator(); it.hasNext(); ) {
            ParsedBlock parsedBlock = it.next();
            int start = parsedBlock.startPosition;
            if( start >= lastBlockPosition ) {
                break;
            }
            switch( parsedBlock.op ) {
                case GOTO:
                case IF:
                    int end = parsedBlock.endPosition;
                    if( start < end ) {
                        BranchNode branch = blockNode;
                        while( branch.size() > 0 ) {
                            BranchNode node = branch.get( 0 );
                            if( start > node.endPos ) {
                                if( end >= branch.endPos ) {
                                    blockCount = 0;
                                    BranchNode parentNode = branch;
                                    while( parentNode != null && end > parentNode.endPos ) {
                                        parentNode = parentNode.parent;
                                        blockCount++;
                                    }
                                    WasmBlockOperator startOp;
                                    if( parsedBlock.op == JavaBlockOperator.GOTO ) {
                                        startOp = WasmBlockOperator.BR;
                                        lastPosition = Math.max( lastPosition, end );
                                    } else {
                                        startOp = WasmBlockOperator.BR_IF;
                                        instructions.remove( ((IfParsedBlock)parsedBlock).jump );
                                    }
                                    start++;
                                    branch.add( new BranchNode( start, start, startOp, null, blockCount ) );
                                    it.remove();
                                }
                                break;
                            }
                            branch = node;
                        }

                    }
            }
        }

        // Create the main block around the switch
        BranchNode switchNode = new BranchNode( startPosition, lastPosition, WasmBlockOperator.BLOCK, WasmBlockOperator.END );
        switchNode.add( blockNode );
        parent.add( switchNode );

        if( brTableNode != null ) {
            // sort back in the natural order and create a br_table 
            Arrays.sort( cases, ( a, b ) -> Long.compare( a.key, b.key ) );
            int[] data = new int[cases.length];
            for( int i = 0; i < data.length; i++ ) {
                data[i] = cases[i].block;
            }
            brTableNode.data = data;
        }
    }

    /**
     * Patch the existing BR instructions after a new BLOCK node was injected in the hierarchy. The break position must
     * only increment if the old break position is outside of the new BLOCK.
     * 
     * @param newNode
     *            the new node
     */
    private void patchBrDeep( BranchNode newNode ) {
        for( WasmInstruction instr : instructions ) {
            int codePos = instr.getCodePosition();
            if( codePos < newNode.startPos ) {
                continue;
            }
            if( codePos >= newNode.endPos ) {
                break;
            }
            if( instr.getType() != Type.Block ) {
                continue;
            }
            WasmBlockInstruction blockInstr = (WasmBlockInstruction)instr;
            if( blockInstr.getOperation() != WasmBlockOperator.BR ) {
                continue;
            }
            Integer data = (Integer)blockInstr.getData();
            int blockCount = 0;
            while( newNode.size() > 0 && newNode.endPos > codePos ) {
                newNode = newNode.get( 0 );
                blockCount++;
            }
            if( blockCount <= data ) { // old break position was outside of the new block
                blockInstr.setData( data + 1 );
            }
        }
        patchBrDeepInTree( newNode, 0 );

        // also change the parent for all break operations
        BranchNode parent = newNode.parent;
        for( BreakBlock breakBlock : breakOperations ) {
            if( breakBlock.branch == parent ) {
                int breakPos = breakBlock.breakPos;
                if( breakPos >= newNode.startPos && breakPos < newNode.endPos ) {
                    breakBlock.branch = newNode; 
                }
            }
        }
    }

    /**
     * Patch the existing BR instructions in the tree after a new BLOCK node was injected in the hierarchy. The break position must
     * only increment if the old break position is outside of the new BLOCK.
     * 
     * @param newNode
     *            the new node
     * @param deep
     *            the deep of recursion 
     */
    private void patchBrDeepInTree( BranchNode newNode, int deep ) {
        for( BranchNode node : newNode ) {
            if( node.startOp == WasmBlockOperator.BR || node.startOp == WasmBlockOperator.BR_IF ) {
                Integer data = (Integer)node.data;
                if( data >= deep ) {
                    node.data = data + 1;
                }
            } else {
                patchBrDeepInTree( node, deep + 1 );
            }
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
    private void calculateGoto ( @Nonnull BranchNode parent, @Nonnull ParsedBlock gotoBlock, List<ParsedBlock> parsedOperations ) {
        int jump = gotoBlock.endPosition; // jump position of the GOTO  
        int start = gotoBlock.startPosition;
        if( start > jump ) {
            // back jump to a previous position like in loops
            int deep = 0;
            BranchNode node = parent;
            do {
                if( node.startOp == WasmBlockOperator.LOOP && node.startPos == jump ) {
                    // the loop instruction itself doesn’t result in an iteration, instead a br 0 instruction causes the loop to repeat
                    parent.add( new BranchNode( start, start, WasmBlockOperator.BR, null, deep ) ); // continue to the start of the loop
                    return;
                }
                node = node.parent;
                deep++;
            } while( node != null );
            throw new WasmException( "GOTO code without target loop/block. Jump from " + start + " to " + jump, gotoBlock.lineNumber );
        } else {
            if( gotoBlock.nextPosition == jump ) {
                //A GOTO to the next position is like a NOP and can be ignored.
                //Occur in java.lang.Integer.getChars(int, int, char[]) of Java 8
                return;
            }
        }
        breakOperations.add( new BreakBlock( WasmBlockOperator.BR, parent, start, jump ) );
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
        loopNode.continuePos = loopBlock.nextPosition;
        blockNode.add( loopNode );

        calculateSubOperations( loopNode, parsedOperations );
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
     * Should be converted to the follow Wasm code for try/catch:
     * 
     * <pre>
     * try
     *   code1
     * catch
     *   block (param exnref)(result anyref)
     *     br_on_exn 0 0
     *     rethrow
     *   end
     *   local.tee $ex
     *   i32.const classIndex(Exception)
     *   call $.instanceof
     *   i32.eqz
     *   if
     *     local.get $ex
     *     throw 0
     *   end
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
    private void calculateTry( final BranchNode parent, TryCatchParsedBlock tryBlock, List<ParsedBlock> parsedOperations ) {
        TryCatchFinally tryCatch = tryBlock.tryCatch;

        ArrayList<TryCatchFinally> catches = new ArrayList<>();
        catches.add( tryCatch );

        int gotoPos = tryCatch.getHandler()-3; //tryCatch.getEnd() points some time before and some time after the goto 
        int endPos = parent.endPos;

        // find all try blocks and the end position of the last catch/finally handler 
        int idx;
        for( idx = 0; idx < parsedOperations.size(); idx++ ) {
            ParsedBlock parsedBlock = parsedOperations.get( idx );

            if( parsedBlock.op == JavaBlockOperator.TRY ) {
                TryCatchFinally tryCatch2 = ((TryCatchParsedBlock)parsedBlock).tryCatch;
                if( tryCatch.getStart() == tryCatch2.getStart() && tryCatch.getEnd() == tryCatch2.getEnd() ) {
                    catches.add( tryCatch2 );
                    parsedOperations.remove( idx-- );
                    continue;
                }
            }

            if( parsedBlock.startPosition == gotoPos && parsedBlock.op == JavaBlockOperator.GOTO && parsedBlock.startPosition < parsedBlock.endPosition ) {
                parsedOperations.remove( idx );
                endPos = parsedBlock.endPosition;
                break;
            }
            if( parsedBlock.startPosition > gotoPos ) {
                break;
            }
        }
 
        // define the try/catch/end block on the right position
        int startPos = tryBlock.startPosition;
        int catchPos = tryCatch.getHandler();

        BranchNode tryNode = new BranchNode( startPos, catchPos, WasmBlockOperator.TRY, null );
        parent.add( tryNode );
        calculate( tryNode, parsedOperations.subList( 0, idx ) );

        BranchNode catchNode = new BranchNode( catchPos, endPos, WasmBlockOperator.CATCH, WasmBlockOperator.END, 0 );
        parent.add( catchNode );

        // Create a block/end structure for every CATCH without the first CATCH
        BranchNode node = catchNode;
        for( int i = catches.size()-1; i > 0; i-- ) {
            TryCatchFinally tryCat = catches.get( i );

            if( tryCatch.getHandler() == tryCat.getHandler() ) {
                // multiple exception in catch block like "catch ( ArrayIndexOutOfBoundsException | IllegalArgumentException ex )"
                continue;
            }

            int blockGotoPos = tryCat.getHandler()-3;
            for( idx = 0; idx < parsedOperations.size(); idx++ ) {
                ParsedBlock parsedBlock = parsedOperations.get( idx );
                if( parsedBlock.startPosition == blockGotoPos && parsedBlock.op == JavaBlockOperator.GOTO && parsedBlock.startPosition < parsedBlock.endPosition ) {
                    parsedOperations.remove( idx );
                    break;
                }
                if( parsedBlock.startPosition > blockGotoPos ) {
                    break;
                }
            }

            BranchNode block = new BranchNode( catchPos + 1, tryCat.getHandler(), WasmBlockOperator.BLOCK, WasmBlockOperator.END );
            block.add( new BranchNode( tryCat.getHandler(), tryCat.getHandler(), WasmBlockOperator.BR, null, catches.size() - i ) );
            node.add( 0, block );
            node = block;

            int instrPos = findIdxOfCodePos( tryCat.getHandler() ) + 1;
            instructions.remove( instrPos ); // every catch block store the exception from the stack but in WASM we can do this only once for all exceptions
        }

        // calculate branch operations inside the CATCH/FINALLY blocks
        calculateTrySubOperations( catchNode, node, parsedOperations );

        // Create the unboxing and the type check of the exceptions from the catch blocks 
        if( tryCatch.isFinally() ) {
            int instrPos = findIdxOfCodePos( catchPos ) + 1;
            WasmInstruction instr = instructions.get( instrPos );
            if( instr.getType() == Type.Block && ((WasmBlockInstruction)instr).getOperation() == WasmBlockOperator.DROP ) {
                // occur with a RETURN in a finally block
                // We does not need to unbox if the value will be drop
            } else {
                addUnboxExnref( catchNode, tryCatch );
            }
        } else {
            addUnboxExnref( catchNode, tryCatch );

            // add a "if $exception instanceof type" check to the WASM code
            int instrPos = findIdxOfCodePos( catchPos ) + 1;
            WasmLoadStoreInstruction ex = (WasmLoadStoreInstruction)instructions.get( instrPos );

            node.add( new BranchNode( 0, 0, null, null ) {
                int handle(int codePosition, java.util.List<WasmInstruction> instructions, int idx, int lineNumber) {
                    if( codePosition == catchPos + 1 ) {
                        FunctionName instanceOf = options.getInstanceOf();

                        instructions.add( idx++, new WasmBlockInstruction( WasmBlockOperator.BLOCK, null, catchPos, lineNumber ) );
                        int brIf = -1;
                        int handler = -1;
                        for( int i = 0; i < catches.size(); i++ ) {
                            TryCatchFinally tryCat = catches.get( i );
                            String exceptionTypeName = tryCat.getType().getName();
                            StructType type = options.types.valueOf( exceptionTypeName );
                            instructions.add( idx++, new WasmLoadStoreInstruction( VariableOperator.get, ex.getSlot(), localVariables, catchPos, lineNumber ) );
                            instructions.add( idx++, new WasmConstInstruction( type.getClassIndex(), catchPos, lineNumber ) );
                            instructions.add( idx++, new WasmCallInstruction( instanceOf, catchPos, lineNumber, options.types, false, exceptionTypeName ) );
                            if( handler != tryCat.getHandler() ) {
                                // if not multiple exception in catch block like "catch ( ArrayIndexOutOfBoundsException | IllegalArgumentException ex )"
                                handler = tryCat.getHandler();
                                brIf++;
                            }
                            instructions.add( idx++, new WasmBlockInstruction( WasmBlockOperator.BR_IF, brIf, catchPos, lineNumber ) );
                        }
                        instructions.add( idx++, new WasmLoadStoreInstruction( VariableOperator.get, ex.getSlot(), localVariables, catchPos, lineNumber ) );
                        instructions.add( idx++, new WasmBlockInstruction( WasmBlockOperator.THROW, null, catchPos, lineNumber ) );
                        instructions.add( idx++, new WasmBlockInstruction( WasmBlockOperator.END, null, catchPos, lineNumber ) );
                    }
                    return idx;
                }
            } );
        }
    }

    /**
     * Calculate branch operations inside the CATCH/FINALLY blocks.
     * 
     * @param catchNode
     *            the parent node
     * @param node
     *            the most inner node
     * @param parsedOperations
     *            the not consumed operations in the parent branch
     */
    private void calculateTrySubOperations( BranchNode catchNode, BranchNode node, List<ParsedBlock> parsedOperations ) {
        do {
            calculateSubOperations( node, parsedOperations );
            if( node == catchNode ) {
                break;
            }
            node = node.parent;
        } while( node != catchNode );
    }

    /**
     * Add an unboxing of the WASm exnref on the stack
     * 
     * @param catchNode
     *            the catch node
     * @param tryCatch
     *            the catch or finally block
     */
    private void addUnboxExnref( BranchNode catchNode, TryCatchFinally tryCatch ) {
        // unboxing the exnref on the stack to a reference of the exception
        int catchPos = catchNode.startPos;
        if( !options.useEH() ) {
            BranchNode unBoxing = new BranchNode( catchPos, catchPos, WasmBlockOperator.UNREACHABLE, null );
            catchNode.add( 0, unBoxing );
            return;
        }
//        AnyType excepType = getCatchType( tryCatch );
//        BlockType blockType = options.types.blockType( Arrays.asList( ValueType.exnref ), Arrays.asList( excepType ) );
//        BranchNode unBoxing = new BranchNode( catchPos, catchPos, WasmBlockOperator.BLOCK, WasmBlockOperator.END, blockType );
//        catchNode.add( 0, unBoxing );
//
//        //TODO localVariables.getTempVariable( ValueType.exnref, catchPos, endPos ); https://github.com/WebAssembly/wabt/issues/1388
//        unBoxing.add( new BranchNode( catchPos, catchPos, WasmBlockOperator.BR_ON_EXN, null, 0 ) );
//        unBoxing.add( new BranchNode( catchPos, catchPos, WasmBlockOperator.RETHROW, null ) );
    }

    private AnyType getCatchType( TryCatchFinally tryCatch ) {
        if( options.useGC() ) {
            ConstantClass excepClass = tryCatch.getType();
            String excepName = excepClass != null ? excepClass.getName() : "java/lang/Throwable";
            return options.types.valueOf( excepName );
        }
        return ValueType.externref;
    }

    /**
     * Get the catch type if there are a start of a catch block on the code position.
     * 
     * @param codePosition
     *            the code position
     * @return the type or null
     */
    @Nullable
    AnyType getCatchType( int codePosition ) {
        for( TryCatchFinally tryCatch : exceptionTable ) {
            if( tryCatch.getHandler() == codePosition ) {
                return getCatchType( tryCatch );
            }
        }
        return null;
    }

    /**
     * Add the break to the block hierarchy. Add an extra block if needed and correct the break deep of other
     * instructions.
     * 
     * @param breakBlock
     *            the description of the break.
     */
    private void calculateBreak( BreakBlock breakBlock ) {
        int deep = -1;
        int gotoEndPos = breakBlock.endPosition;
        BranchNode branch = breakBlock.branch;
        BranchNode parent = branch;
        while( parent != null && parent.elseEndPos < gotoEndPos ) {
            deep++;
            parent = parent.parent;
        }

        if( parent != null && parent.startOp == WasmBlockOperator.LOOP ) {
            // a break in a LOOP is only a continue, we need to break to the outer block
            deep++;
            parent = parent.parent;
        }

        if( parent != null && parent.elseEndPos > gotoEndPos ) {

            // check if we break into an ELSE block which is possible in Java with a GOTO, occur with concatenated conditional operators
            for( int i = 1; i < parent.size(); i++ ) {
                BranchNode node = parent.get( i );
                if( gotoEndPos == node.startPos ) {
                    if( node.startOp == WasmBlockOperator.ELSE ) {
                        // we can't break into an else block that we break to the IF and push a zero to the stack
                        node = parent.get( i - 1 ); // should be the IF to the ELSE
                        breakBlock.endPosition = node.startPos;
                        breakBlock.breakToElseBlock = true;
                        calculateBreak( breakBlock );
                        return;
                    }
                    break;
                }
            }

            BranchNode middleNode = new BranchNode( parent.startPos, gotoEndPos, WasmBlockOperator.BLOCK, WasmBlockOperator.END );
            while( !parent.isEmpty() ) {
                BranchNode child = parent.get( 0 );
                if( child.endPos > gotoEndPos ) {
                    break;
                }
                middleNode.add( child );
                parent.remove( 0 );
            }
            parent.add( 0, middleNode );
            parent = middleNode;
            patchBrDeep( middleNode );
        }

        WasmBlockOperator op = breakBlock.op;
        if( breakBlock.breakToElseBlock ) {
            // push zero that we switch into the ELSE block
            if( op == WasmBlockOperator.BR_IF ) {
                // we need to split it in a separate IF and BR instruction
                BranchNode child = new BranchNode( breakBlock.breakPos - 1, breakBlock.breakPos, WasmBlockOperator.IF, WasmBlockOperator.END, ValueType.empty );
                branch.add( child );
                branch = child;
                op = WasmBlockOperator.BR;
            }
            insertConstBeforePosition( 0, breakBlock.breakPos, -1 );
        }
        BranchNode breakNode = new BranchNode( breakBlock.breakPos, breakBlock.breakPos, op, null, deep + 1 );
        branch.add( breakNode );
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
            }
            lineNumber = instr.getLineNumber();
            do {
                // call it for every position for the case that a Jump is call to a intermediate position
                codePosition++;
                idx = root.handle( codePosition, instructions, idx, lineNumber );
            } while( codePosition < nextCodePosition );
        }
        root.handle( byteCode.getCodePosition(), instructions, instructions.size(), byteCode.getLineNumber() );

        root.calculateBlockType( instructions );
    }

    /**
     * Description of single block/branch from the parsed Java byte code. The parsed branches are plain.
     */
    private static class ParsedBlock implements Comparable<ParsedBlock> {
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

        /**
         * {@inheritDoc}
         */
        @Override
        public int compareTo( ParsedBlock o ) {
            return startPosition < o.startPosition ? -1 : startPosition > o.startPosition ? 1 : // first order on the start position
                            -Integer.compare( endPosition, o.endPosition ); // then reverse on the end position that outer blocks occur first
        }
    }

    /**
     * Description of a parsed IF operation.
     */
    private static class IfParsedBlock extends ParsedBlock {

        private WasmNumericInstruction instr;

        private JumpInstruction        jump;

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
        private IfParsedBlock( int startPosition, int offset, int lineNumber, WasmNumericInstruction instr, JumpInstruction jump ) {
            super( JavaBlockOperator.IF, startPosition, offset, startPosition + 3, lineNumber );
            this.instr = instr;
            this.jump = jump;
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
                    throw new WasmException( "Not a compare operation: " + instr.numOp, lineNumber );
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
            super( JavaBlockOperator.TRY, tryCatch.getStart(), tryCatch.getEnd() - tryCatch.getStart(), tryCatch.getStart(), -1 );
            this.tryCatch = tryCatch;
        }
    }

    /**
     * Described a code branch/block node in a tree structure.
     */
    private static class BranchNode extends ArrayList<BranchNode> {

        private final int               startPos;

        private int                     endPos;

        private final WasmBlockOperator startOp;

        private WasmBlockOperator       endOp;

        /**
         * Extra data depending of the operator. For example the return type of a block.
         */
        private Object                  data;

        private BranchNode              parent;

        /**
         * A instruction for which the return type must be calculated.
         */
        private WasmBlockInstruction    startBlock;

        /**
         * The position of the startBlock in the instructions
         */
        private int                     startIdx;

        private int                     elseEndPos;

        /** jump position for a CONTINUE in a loop */
        private int                     continuePos;

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
            this.endPos = this.elseEndPos = endPos;
            this.startOp = startOp;
            this.endOp = endOp;
            this.data = data;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean add( BranchNode node ) {
            node.parent = this;
            return super.add( node );
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void add( int index, BranchNode node ) {
            node.parent = this;
            super.add( index, node );
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
        int handle( int codePosition, List<WasmInstruction> instructions, int idx, int lineNumber ) {
            if( codePosition < startPos || codePosition > endPos ) {
                return idx;
            }
            if( codePosition == startPos && startOp != null ) {
                startBlock = new WasmBlockInstruction( startOp, data, codePosition, lineNumber );
                instructions.add( idx++, startBlock );
                startIdx = idx;
            }
            for( BranchNode branch : this ) {
                idx = branch.handle( codePosition, instructions, idx, lineNumber );
            }
            if( codePosition == endPos && endOp != null ) {
                instructions.add( idx++, new WasmBlockInstruction( endOp, null, codePosition, lineNumber ) );
            }
            return idx;
        }

        /**
         * Calculate the block type (return type). The value type that is on the stack after the block.
         * 
         * @param instructions
         *            the instructions of the function
         */
        void calculateBlockType( List<WasmInstruction> instructions ) {
            for( int i = size() - 1; i >= 0; i-- ) {
                BranchNode branch = get( i );
                branch.calculateBlockType( instructions );
            }

            if( startBlock == null ) {
                return;
            }
            switch( startBlock.getOperation() ) {
                case IF:
                case BLOCK:
                try {
                    ArrayDeque<AnyType> stack = new ArrayDeque<>();
                    stack.push( ValueType.empty );
                    INSTRUCTIONS: for( int i = startIdx; i < instructions.size(); i++ ) {
                        WasmInstruction instr = instructions.get( i );
                        int codePos = instr.getCodePosition();
                        if( codePos > endPos ) {
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

                        if( instr.getType() == Type.Block ) {
                            WasmBlockInstruction blockInstr = (WasmBlockInstruction)instr;
                            switch( blockInstr.getOperation() ) {
                                case RETURN:
                                    // set "empty" block type
                                    while( stack.size() > 1 ) {
                                        stack.pop();
                                    }
                                    break INSTRUCTIONS;
                                case IF:
                                case BLOCK:
                                case LOOP:
                                case TRY:
                                    // skip the content of the block, important to not count ELSE blocks
                                    i = findEndInstruction( instructions, i );
                                    break;
                                case END:
                                case ELSE:
                                    break INSTRUCTIONS;
                                case BR:
                                    Integer data = (Integer)blockInstr.getData();
                                    if( data > 0 ) {
                                        // TODO we should check the ELSE block in this case if there is any
                                        // set "empty" block type
                                        while( stack.size() > 1 ) {
                                            stack.pop();
                                        }
                                    }
                                    break INSTRUCTIONS;
                                default:
                            }
                        }
                    }
                    startBlock.setData( stack.pop() );
                } catch( Throwable th ) {
                    throw WasmException.create( th, startBlock.getLineNumber() );
                }
            }
        }

        /**
         * Find the END instruction of the block.
         * 
         * @param instructions
         *            the list of instructions
         * @param idx
         *            the index of the block start
         * @return the END index
         */
        private int findEndInstruction( List<WasmInstruction> instructions, int idx ) {
            int count = 0;
            for( ; idx < instructions.size(); idx++ ) {
                WasmInstruction instr = instructions.get( idx );
                if( instr.getType() == Type.Block ) {
                    switch( ((WasmBlockInstruction)instr).getOperation() ) {
                        case IF:
                        case BLOCK:
                        case LOOP:
                        case TRY:
                            count++;
                            break;
                        case END:
                            count--;
                            break;
                    }
                }
                if( count == 0 ) {
                    break;
                }
            }
            return idx;
        }
    }

    /**
     * Positions inside a IF control structure.
     */
    private static class IfPositions {
        /** Count of boolean operations in the IF top level condition. This can be (&&) or (||) operations.  */
        private int ifCount;

        /** The position of the first instruction in the THEN part. */
        private int thenPos;

        /** The position of the first instruction in the ELSE part. */
        private int elsePos;
    }

    /**
     * Described a break to a block that will be added later.
     */
    private static class BreakBlock {

        private final WasmBlockOperator op;

        private final int               breakPos;

        private int                     endPosition;

        private BranchNode              branch;

        private boolean                 breakToElseBlock;

        /**
         * Create Break
         * 
         * @param op
         *            BR or BR_IF
         * @param branch
         *            the parent block which should contain the break
         * @param breakPos
         *            the position where the break should be inserted.
         * @param endPosition
         *            the Jump position
         */
        public BreakBlock( @Nonnull WasmBlockOperator op, @Nonnull BranchNode branch, int breakPos, int endPosition ) {
            this.op = op;
            this.breakPos = breakPos;
            this.endPosition = endPosition;
            this.branch = branch;
        }
    }
}
