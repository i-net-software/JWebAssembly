/*
 * Copyright 2019 - 2020 Volker Berlin (i-net software)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.inetsoftware.jwebassembly.module;

import java.util.List;

import de.inetsoftware.jwebassembly.module.WasmInstruction.Type;
import de.inetsoftware.jwebassembly.wasm.ValueType;
import de.inetsoftware.jwebassembly.wasm.VariableOperator;
import de.inetsoftware.jwebassembly.wasm.WasmBlockOperator;

/**
 * Optimize the code of a single method/function through using of WebAssembly
 * features without equivalent in Java.
 * 
 * @author Volker Berlin
 */
class CodeOptimizer {

    /**
     * Optimize the code before writing.
     * 
     * @param instructions the list of instructions
     */
    void optimize(List<WasmInstruction> instructions) {
		int maxPasses=instructions.size();
		int cnter=0;
        while (cnter<maxPasses) {
			cnter++;
            int oldSize = instructions.size();
            optimize0( instructions );
            int newSize = instructions.size();
            if (newSize == oldSize || newSize == 1) {
                break;
            }
        }
    }

    private void optimize0(List<WasmInstruction> instructions) {
        for (int i = instructions.size() - 1; i >= 0; i--) {
            if(i>=instructions.size()) {
                continue;
            }
            WasmInstruction instr = instructions.get( i );
            switch (instr.getType()) {
            case Local:
                localOptimization( instructions, i, instr );
                break;
            case Numeric:
                numericOptimization( instructions, i, instr );
                break;
            default:
            }
        }
    }

    private void numericOptimization(List<WasmInstruction> instructions, int i, WasmInstruction instr) {
        if (i == 0) {
            return;
        }
        WasmNumericInstruction wni = (WasmNumericInstruction) instr;
        switch (wni.getPopCount()) {
        case 1:
            optimizeUnaryOperator( i, instructions, wni );
            break;
        case 2:
            optimizeBinaryOperator( i, instructions, wni );
            break;
        default:
            break;
        }
    }

    /*-
     * Merge two type.const and one type.binop instruction.
     * For example:
     * i32.const 20
     * i32.const 50
     * i32.add
     * ==> i32.const 70
     * i32.const 40
     * i32.const 65
     * i32.lt
     * ==>i32.const 1
     * TODO:Cleanup
     */
    private void optimizeBinaryOperator(int i, List<WasmInstruction> instructions, WasmNumericInstruction wni) {
        if (i <= 1) {
            return;
        }
        int lineNo = wni.getLineNumber();
        int codePos = wni.getCodePosition();
        WasmInstruction firstOperand = instructions.get( i - 2 );
        WasmInstruction secondOperand = instructions.get( i - 1 );
        if (firstOperand.getType() != Type.Const || secondOperand.getType() != Type.Const) {
            return;
        }
        WasmConstInstruction first = (WasmConstInstruction) firstOperand;
        WasmConstInstruction second = (WasmConstInstruction) secondOperand;
        ValueType target = first.getValueType();
        Number firstValue = first.getValue();
        Number secondValue = second.getValue();
        switch (target) {
        case f32: {
            float v1 = firstValue.floatValue();
            float v2 = secondValue.floatValue();
            switch (wni.numOp) {
            case ne:
                instructions.remove( i );
                instructions.remove( i - 1 );
                instructions.set( i - 2,
                        new WasmConstInstruction( Integer.valueOf( fne( v1, v2 ) ), codePos, lineNo ) );
                break;
            case eq:
                instructions.remove( i );
                instructions.remove( i - 1 );
                instructions.set( i - 2,
                        new WasmConstInstruction( Integer.valueOf( feq( v1, v2 ) ), codePos, lineNo ) );
                break;
            case le:
                instructions.remove( i );
                instructions.remove( i - 1 );
                instructions.set( i - 2,
                        new WasmConstInstruction( Integer.valueOf( fle( v1, v2 ) ), codePos, lineNo ) );
                break;
            case lt:
                instructions.remove( i );
                instructions.remove( i - 1 );
                instructions.set( i - 2,
                        new WasmConstInstruction( Integer.valueOf( flt( v1, v2 ) ), codePos, lineNo ) );
                break;
            case ge:
                instructions.remove( i );
                instructions.remove( i - 1 );
                instructions.set( i - 2,
                        new WasmConstInstruction( Integer.valueOf( fge( v1, v2 ) ), codePos, lineNo ) );
                break;
            case gt:
                instructions.remove( i );
                instructions.remove( i - 1 );
                instructions.set( i - 2,
                        new WasmConstInstruction( Integer.valueOf( fgt( v1, v2 ) ), codePos, lineNo ) );
                break;
            case add:
                instructions.remove( i );
                instructions.remove( i - 1 );
                instructions.set( i - 2, new WasmConstInstruction( Float.valueOf( v1 + v2 ), codePos, lineNo ) );
                break;
            case sub:
                instructions.remove( i );
                instructions.remove( i - 1 );
                instructions.set( i - 2, new WasmConstInstruction( Float.valueOf( v1 - v2 ), codePos, lineNo ) );
                break;
            case mul:
                instructions.remove( i );
                instructions.remove( i - 1 );
                instructions.set( i - 2, new WasmConstInstruction( Float.valueOf( v1 * v2 ), codePos, lineNo ) );
                break;
            case div:
                instructions.remove( i );
                instructions.remove( i - 1 );
                instructions.set( i - 2, new WasmConstInstruction( Float.valueOf( v1 / v2 ), codePos, lineNo ) );
                break;
            case min:
                instructions.remove( i );
                instructions.remove( i - 1 );
                instructions.set( i - 2,
                        new WasmConstInstruction( Float.valueOf( Math.min( v1, v2 ) ), codePos, lineNo ) );
                break;
            case max:
                instructions.remove( i );
                instructions.remove( i - 1 );
                instructions.set( i - 2,
                        new WasmConstInstruction( Float.valueOf( Math.max( v1, v2 ) ), codePos, lineNo ) );
                break;
            case copysign:
                instructions.remove( i );
                instructions.remove( i - 1 );
                instructions.set( i - 2,
                        new WasmConstInstruction( Float.valueOf( Math.copySign( v1, v2 ) ), codePos, lineNo ) );
                break;
            default: {
                return;
            }
            }
            break;
        }
        case f64: {
            double v1 = firstValue.doubleValue();
            double v2 = secondValue.doubleValue();
            switch (wni.numOp) {
            case ne:
                instructions.remove( i );
                instructions.remove( i - 1 );
                instructions.set( i - 2,
                        new WasmConstInstruction( Integer.valueOf( fne( v1, v2 ) ), codePos, lineNo ) );
                break;
            case eq:
                instructions.remove( i );
                instructions.remove( i - 1 );
                instructions.set( i - 2,
                        new WasmConstInstruction( Integer.valueOf( feq( v1, v2 ) ), codePos, lineNo ) );
                break;
            case le:
                instructions.remove( i );
                instructions.remove( i - 1 );
                instructions.set( i - 2,
                        new WasmConstInstruction( Integer.valueOf( fle( v1, v2 ) ), codePos, lineNo ) );
                break;
            case lt:
                instructions.remove( i );
                instructions.remove( i - 1 );
                instructions.set( i - 2,
                        new WasmConstInstruction( Integer.valueOf( flt( v1, v2 ) ), codePos, lineNo ) );
                break;
            case ge:
                instructions.remove( i );
                instructions.remove( i - 1 );
                instructions.set( i - 2,
                        new WasmConstInstruction( Integer.valueOf( fge( v1, v2 ) ), codePos, lineNo ) );
                break;
            case gt:
                instructions.remove( i );
                instructions.remove( i - 1 );
                instructions.set( i - 2,
                        new WasmConstInstruction( Integer.valueOf( fgt( v1, v2 ) ), codePos, lineNo ) );
                break;
            case add:
                instructions.remove( i );
                instructions.remove( i - 1 );
                instructions.set( i - 2, new WasmConstInstruction( Double.valueOf( v1 + v2 ), codePos, lineNo ) );
                break;
            case sub:
                instructions.remove( i );
                instructions.remove( i - 1 );
                instructions.set( i - 2, new WasmConstInstruction( Double.valueOf( v1 - v2 ), codePos, lineNo ) );
                break;
            case mul:
                instructions.remove( i );
                instructions.remove( i - 1 );
                instructions.set( i - 2, new WasmConstInstruction( Double.valueOf( v1 * v2 ), codePos, lineNo ) );
                break;
            case div:
                instructions.remove( i );
                instructions.remove( i - 1 );
                instructions.set( i - 2, new WasmConstInstruction( Double.valueOf( v1 / v2 ), codePos, lineNo ) );
                break;
            case min:
                instructions.remove( i );
                instructions.remove( i - 1 );
                instructions.set( i - 2,
                        new WasmConstInstruction( Double.valueOf( Math.min( v1, v2 ) ), codePos, lineNo ) );
                break;
            case max:
                instructions.remove( i );
                instructions.remove( i - 1 );
                instructions.set( i - 2,
                        new WasmConstInstruction( Double.valueOf( Math.max( v1, v2 ) ), codePos, lineNo ) );
                break;
            case copysign:
                instructions.remove( i );
                instructions.remove( i - 1 );
                instructions.set( i - 2,
                        new WasmConstInstruction( Double.valueOf( Math.copySign( v1, v2 ) ), codePos, lineNo ) );
                break;
            default:
                break;
            }
            break;
        }
        case i32: {
            int v1 = firstValue.intValue();
            int v2 = secondValue.intValue();
            switch (wni.numOp) {
            case ne:
                instructions.remove( i );
                instructions.remove( i - 1 );
                instructions.set( i - 2,
                        new WasmConstInstruction( Integer.valueOf( v1 != v2 ? 1 : 0 ), codePos, lineNo ) );
                break;
            case eq:
                instructions.remove( i );
                instructions.remove( i - 1 );
                instructions.set( i - 2,
                        new WasmConstInstruction( Integer.valueOf( v1 == v2 ? 1 : 0 ), codePos, lineNo ) );
                break;
            case le:
                instructions.remove( i );
                instructions.remove( i - 1 );
                instructions.set( i - 2,
                        new WasmConstInstruction( Integer.valueOf( v1 <= v2 ? 1 : 0 ), codePos, lineNo ) );
                break;
            case lt:
                instructions.remove( i );
                instructions.remove( i - 1 );
                instructions.set( i - 2,
                        new WasmConstInstruction( Integer.valueOf( v1 < v2 ? 1 : 0 ), codePos, lineNo ) );
                break;
            case ge:
                instructions.remove( i );
                instructions.remove( i - 1 );
                instructions.set( i - 2,
                        new WasmConstInstruction( Integer.valueOf( v1 >= v2 ? 1 : 0 ), codePos, lineNo ) );
                break;
            case gt:
                instructions.remove( i );
                instructions.remove( i - 1 );
                instructions.set( i - 2,
                        new WasmConstInstruction( Integer.valueOf( v1 > v2 ? 1 : 0 ), codePos, lineNo ) );
                break;
            case add:
                instructions.remove( i );
                instructions.remove( i - 1 );
                instructions.set( i - 2, new WasmConstInstruction( Integer.valueOf( v1 + v2 ), codePos, lineNo ) );
                break;
            case sub:
                instructions.remove( i );
                instructions.remove( i - 1 );
                instructions.set( i - 2, new WasmConstInstruction( Integer.valueOf( v1 - v2 ), codePos, lineNo ) );
                break;
            case mul:
                instructions.remove( i );
                instructions.remove( i - 1 );
                instructions.set( i - 2, new WasmConstInstruction( Integer.valueOf( v1 * v2 ), codePos, lineNo ) );
                break;
            case div:
                if (v2 == 0) {
                    break;
                }
                instructions.remove( i );
                instructions.remove( i - 1 );
                instructions.set( i - 2, new WasmConstInstruction( Integer.valueOf( v1 / v2 ), codePos, lineNo ) );
                break;
            case rem:
                instructions.remove( i );
                instructions.remove( i - 1 );
                instructions.set( i - 2,
                        new WasmConstInstruction( Integer.valueOf( Math.floorMod( v1, v2 ) ), codePos, lineNo ) );
                break;
            case and:
                instructions.remove( i );
                instructions.remove( i - 1 );
                instructions.set( i - 2, new WasmConstInstruction( Integer.valueOf( v1 & v2 ), codePos, lineNo ) );
                break;
            case or:
                instructions.remove( i );
                instructions.remove( i - 1 );
                instructions.set( i - 2, new WasmConstInstruction( Integer.valueOf( v1 | v2 ), codePos, lineNo ) );
                break;
            case xor:
                instructions.remove( i );
                instructions.remove( i - 1 );
                instructions.set( i - 2, new WasmConstInstruction( Integer.valueOf( v1 ^ v2 ), codePos, lineNo ) );
                break;
            case shl:
                instructions.remove( i );
                instructions.remove( i - 1 );
                // https://webassembly.github.io/spec/core/exec/numerics.html#op-ishl
                instructions.set( i - 2, new WasmConstInstruction(
                        Integer.valueOf( (v1 << (v2 % 32)) % (Integer.MAX_VALUE) ), codePos, lineNo ) );
                break;
            case shr_s:
                instructions.remove( i );
                instructions.remove( i - 1 );
                instructions.set( i - 2,
                        new WasmConstInstruction( Integer.valueOf( (v1 >> (v2 % 32)) ), codePos, lineNo ) );
                break;
            case shr_u:
                instructions.remove( i );
                instructions.remove( i - 1 );
                instructions.set( i - 2,
                        new WasmConstInstruction( Integer.valueOf( (v1 >>> (v2 % 32)) ), codePos, lineNo ) );
                break;
            default: {
                return;
            }
            }
            break;
        }
        case i64: {
            long v1 = firstValue.longValue();
            long v2 = secondValue.longValue();
            switch (wni.numOp) {
            case ne:
                instructions.remove( i );
                instructions.remove( i - 1 );
                instructions.set( i - 2,
                        new WasmConstInstruction( Integer.valueOf( v1 != v2 ? 1 : 0 ), codePos, lineNo ) );
                break;
            case eq:
                instructions.remove( i );
                instructions.remove( i - 1 );
                instructions.set( i - 2,
                        new WasmConstInstruction( Integer.valueOf( v1 == v2 ? 1 : 0 ), codePos, lineNo ) );
                break;
            case le:
                instructions.remove( i );
                instructions.remove( i - 1 );
                instructions.set( i - 2,
                        new WasmConstInstruction( Integer.valueOf( v1 <= v2 ? 1 : 0 ), codePos, lineNo ) );
                break;
            case lt:
                instructions.remove( i );
                instructions.remove( i - 1 );
                instructions.set( i - 2,
                        new WasmConstInstruction( Integer.valueOf( v1 < v2 ? 1 : 0 ), codePos, lineNo ) );
                break;
            case ge:
                instructions.remove( i );
                instructions.remove( i - 1 );
                instructions.set( i - 2,
                        new WasmConstInstruction( Integer.valueOf( v1 >= v2 ? 1 : 0 ), codePos, lineNo ) );
                break;
            case gt:
                instructions.remove( i );
                instructions.remove( i - 1 );
                instructions.set( i - 2,
                        new WasmConstInstruction( Integer.valueOf( v1 > v2 ? 1 : 0 ), codePos, lineNo ) );
                break;
            case add:
                instructions.remove( i );
                instructions.remove( i - 1 );
                instructions.set( i - 2, new WasmConstInstruction( Long.valueOf( v1 + v2 ), codePos, lineNo ) );
                break;
            case sub:
                instructions.remove( i );
                instructions.remove( i - 1 );
                instructions.set( i - 2, new WasmConstInstruction( Long.valueOf( v1 - v2 ), codePos, lineNo ) );
                break;
            case mul:
                instructions.remove( i );
                instructions.remove( i - 1 );
                instructions.set( i - 2, new WasmConstInstruction( Long.valueOf( v1 * v2 ), codePos, lineNo ) );
                break;
            case div:
                if (v2 == 0) {
                    break;
                }
                instructions.remove( i );
                instructions.remove( i - 1 );
                instructions.set( i - 2, new WasmConstInstruction( Long.valueOf( v1 / v2 ), codePos, lineNo ) );
                break;
            case rem:
                instructions.remove( i );
                instructions.remove( i - 1 );
                instructions.set( i - 2,
                        new WasmConstInstruction( Long.valueOf( Math.floorMod( v1, v2 ) ), codePos, lineNo ) );
                break;
            case and:
                instructions.remove( i );
                instructions.remove( i - 1 );
                instructions.set( i - 2, new WasmConstInstruction( Long.valueOf( v1 & v2 ), codePos, lineNo ) );
                break;
            case or:
                instructions.remove( i );
                instructions.remove( i - 1 );
                instructions.set( i - 2, new WasmConstInstruction( Long.valueOf( v1 | v2 ), codePos, lineNo ) );
                break;
            case xor:
                instructions.remove( i );
                instructions.remove( i - 1 );
                instructions.set( i - 2, new WasmConstInstruction( Long.valueOf( v1 ^ v2 ), codePos, lineNo ) );
                break;
            case shl:
                instructions.remove( i );
                instructions.remove( i - 1 );
                // https://webassembly.github.io/spec/core/exec/numerics.html#op-ishl
                instructions.set( i - 2, new WasmConstInstruction( Long.valueOf( (v1 << (v2 % 64)) % (Long.MAX_VALUE) ),
                        codePos, lineNo ) );
                break;
            case shr_s:
                instructions.remove( i );
                instructions.remove( i - 1 );
                instructions.set( i - 2,
                        new WasmConstInstruction( Long.valueOf( (v1 >> (v2 % 64)) ), codePos, lineNo ) );
                break;
            case shr_u:
                instructions.remove( i );
                instructions.remove( i - 1 );
                instructions.set( i - 2,
                        new WasmConstInstruction( Long.valueOf( (v1 >>> (v2 % 64)) ), codePos, lineNo ) );
                break;
            default: {
                return;
            }
            }
            break;
        }
        default:
            break;
        }
    }

    private int fgt(float v1, float v2) {
        if (Float.isNaN( v1 ) || Float.isNaN( v2 )) {
            return 0;
        } else if (v1 == v2) {
            return 0;
        } else if (v1 == Float.POSITIVE_INFINITY) {
            return 1;
        } else if (v1 == Float.NEGATIVE_INFINITY) {
            return 0;
        } else if (v2 == Float.POSITIVE_INFINITY) {
            return 1;
        } else if (v2 == Float.NEGATIVE_INFINITY) {
            return 0;
        } else if (v1 == 0) {
            return 0;
        } else if (v1 > v2) {
            return 1;
        }
        return 0;
    }

    private int fgt(double v1, double v2) {
        if (Double.isNaN( v1 ) || Double.isNaN( v2 )) {
            return 0;
        } else if (v1 == v2) {
            return 0;
        } else if (v1 == Double.POSITIVE_INFINITY) {
            return 1;
        } else if (v1 == Double.NEGATIVE_INFINITY) {
            return 0;
        } else if (v2 == Double.POSITIVE_INFINITY) {
            return 1;
        } else if (v2 == Double.NEGATIVE_INFINITY) {
            return 0;
        } else if (v1 == 0) {
            return 0;
        } else if (v1 > v2) {
            return 1;
        }
        return 0;
    }

    private int fge(float v1, float v2) {
        if (Float.isNaN( v1 ) || Float.isNaN( v2 )) {
            return 0;
        } else if (v1 == v2) {
            return 0;
        } else if (v1 == Float.POSITIVE_INFINITY) {
            return 1;
        } else if (v1 == Float.NEGATIVE_INFINITY) {
            return 0;
        } else if (v2 == Float.POSITIVE_INFINITY) {
            return 1;
        } else if (v2 == Float.NEGATIVE_INFINITY) {
            return 0;
        } else if (v1 == 0) {
            return 0;
        } else if (v1 >= v2) {
            return 1;
        }
        return 0;
    }

    private int fge(double v1, double v2) {
        if (Double.isNaN( v1 ) || Double.isNaN( v2 )) {
            return 0;
        } else if (v1 == v2) {
            return 0;
        } else if (v1 == Double.POSITIVE_INFINITY) {
            return 1;
        } else if (v1 == Double.NEGATIVE_INFINITY) {
            return 0;
        } else if (v2 == Double.POSITIVE_INFINITY) {
            return 1;
        } else if (v2 == Double.NEGATIVE_INFINITY) {
            return 0;
        } else if (v1 == 0) {
            return 0;
        } else if (v1 >= v2) {
            return 1;
        }
        return 0;
    }

    private int flt(float v1, float v2) {
        if (Float.isNaN( v1 ) || Float.isNaN( v2 )) {
            return 0;
        } else if (v1 == v2) {
            return 0;
        } else if (v1 == Float.POSITIVE_INFINITY) {
            return 1;
        } else if (v1 == Float.NEGATIVE_INFINITY) {
            return 0;
        } else if (v2 == Float.POSITIVE_INFINITY) {
            return 1;
        } else if (v2 == Float.NEGATIVE_INFINITY) {
            return 0;
        } else if (v1 == 0) {
            return 0;
        } else if (v1 < v2) {
            return 1;
        }
        return 0;
    }

    private int flt(double v1, double v2) {
        if (Double.isNaN( v1 ) || Double.isNaN( v2 )) {
            return 0;
        } else if (v1 == v2) {
            return 0;
        } else if (v1 == Double.POSITIVE_INFINITY) {
            return 1;
        } else if (v1 == Double.NEGATIVE_INFINITY) {
            return 0;
        } else if (v2 == Double.POSITIVE_INFINITY) {
            return 1;
        } else if (v2 == Double.NEGATIVE_INFINITY) {
            return 0;
        } else if (v1 == 0) {
            return 0;
        } else if (v1 < v2) {
            return 1;
        }
        return 0;
    }

    private int fle(float v1, float v2) {
        if (Float.isNaN( v1 ) || Float.isNaN( v2 )) {
            return 0;
        } else if (v1 == v2) {
            return 0;
        } else if (v1 == Float.POSITIVE_INFINITY) {
            return 1;
        } else if (v1 == Float.NEGATIVE_INFINITY) {
            return 0;
        } else if (v2 == Float.POSITIVE_INFINITY) {
            return 1;
        } else if (v2 == Float.NEGATIVE_INFINITY) {
            return 0;
        } else if (v1 == 0) {
            return 0;
        } else if (v1 <= v2) {
            return 1;
        }
        return 0;
    }

    private int fle(double v1, double v2) {
        if (Double.isNaN( v1 ) || Double.isNaN( v2 )) {
            return 0;
        } else if (v1 == v2) {
            return 0;
        } else if (v1 == Double.POSITIVE_INFINITY) {
            return 1;
        } else if (v1 == Double.NEGATIVE_INFINITY) {
            return 0;
        } else if (v2 == Double.POSITIVE_INFINITY) {
            return 1;
        } else if (v2 == Double.NEGATIVE_INFINITY) {
            return 0;
        } else if (v1 == 0) {
            return 0;
        } else if (v1 <= v2) {
            return 1;
        }
        return 0;
    }

    private int feq(float v1, float v2) {
        if (Float.isNaN( v1 ) || Float.isNaN( v2 )) {
            return 0;
        } else if (v1 == v2) {
            return 1;
        }
        return 0;
    }

    private int feq(double v1, double v2) {
        if (Double.isNaN( v1 ) || Double.isNaN( v2 )) {
            return 0;
        } else if (v1 == v2) {
            return 1;
        }
        return 0;
    }

    private int fne(float v1, float v2) {
        if (Float.isNaN( v1 ) || Float.isNaN( v2 )) {
            return 0;
        } else if (v1 != v2) {
            return 1;
        }
        return 0;
    }

    private int fne(double v1, double v2) {
        if (Double.isNaN( v1 ) || Double.isNaN( v2 )) {
            return 0;
        } else if (v1 != v2) {
            return 1;
        }
        return 0;
    }

    /*-
     * Merge type.const and type.unop:
     * For example:
     * f32.const -450.01
     * f32.abs 
     * ==> f32.const 450.01
     * f64.const 2
     * f64.sqrt
     * ==> f64.const 1.4.....
     */
    private void optimizeUnaryOperator(int i, List<WasmInstruction> instructions, WasmNumericInstruction wni) {
        if (i == 0) {
            return;
        }
        int lineNo = wni.getLineNumber();
        int codePos = wni.getCodePosition();
        WasmInstruction wi = instructions.get( i - 1 );
        if (wi.getType() != Type.Const) {
            return;
        } else {
            WasmConstInstruction wci = (WasmConstInstruction) wi;
            ValueType target = wci.getValueType();
            Number number = wci.getValue();
            switch (target) {
            case f32: {
                float constValue = number.floatValue();
                switch (wni.numOp) {
                case abs:
                    instructions.remove( i );
                    instructions.set( i - 1,
                            new WasmConstInstruction( Float.valueOf( Math.abs( constValue ) ), lineNo, codePos ) );
                    break;
                case neg:
                    instructions.remove( i );
                    instructions.set( i - 1,
                            new WasmConstInstruction( Float.valueOf( -constValue ), lineNo, codePos ) );
                    break;
                case sqrt:
                    instructions.remove( i );
                    instructions.set( i - 1, new WasmConstInstruction( Float.valueOf( (float) Math.sqrt( constValue ) ),
                            lineNo, codePos ) );
                    break;
                case ceil:
                    instructions.remove( i );
                    instructions.set( i - 1, new WasmConstInstruction( Float.valueOf( (float) Math.ceil( constValue ) ),
                            lineNo, codePos ) );
                    break;
                case floor:
                    instructions.remove( i );
                    instructions.set( i - 1, new WasmConstInstruction(
                            Float.valueOf( (float) Math.floor( constValue ) ), lineNo, codePos ) );
                    break;
                case trunc:
                    instructions.remove( i );
                    instructions.set( i - 1,
                            new WasmConstInstruction( Float.valueOf( ftrunc( constValue ) ), lineNo, codePos ) );
                    break;
                case nearest:
                    instructions.remove( i );
                    instructions.set( i - 1,
                            new WasmConstInstruction( Float.valueOf( Math.round( constValue ) ), lineNo, codePos ) );
                    break;
                default: {
                    return;
                }
                }
                break;
            }
            case f64: {
                double constValue = number.doubleValue();
                switch (wni.numOp) {
                case abs:
                    instructions.remove( i );
                    instructions.set( i - 1,
                            new WasmConstInstruction( Double.valueOf( Math.abs( constValue ) ), lineNo, codePos ) );
                    break;
                case neg:
                    instructions.remove( i );
                    instructions.set( i - 1,
                            new WasmConstInstruction( Double.valueOf( -constValue ), lineNo, codePos ) );
                    break;
                case sqrt:
                    instructions.remove( i );
                    instructions.set( i - 1,
                            new WasmConstInstruction( Double.valueOf( Math.sqrt( constValue ) ), lineNo, codePos ) );
                    break;
                case ceil:
                    instructions.remove( i );
                    instructions.set( i - 1,
                            new WasmConstInstruction( Double.valueOf( Math.ceil( constValue ) ), lineNo, codePos ) );
                    break;
                case floor:
                    instructions.remove( i );
                    instructions.set( i - 1,
                            new WasmConstInstruction( Double.valueOf( Math.floor( constValue ) ), lineNo, codePos ) );
                    break;
                case trunc:
                    instructions.remove( i );
                    instructions.set( i - 1,
                            new WasmConstInstruction( Double.valueOf( ftrunc( constValue ) ), lineNo, codePos ) );
                    break;
                case nearest:
                    instructions.remove( i );
                    instructions.set( i - 1,
                            new WasmConstInstruction( Double.valueOf( Math.round( constValue ) ), lineNo, codePos ) );
                    break;
                default:
                    return;
                }
                break;
            }
            case i32: {
                int constValue = number.intValue();
                switch (wni.numOp) {
                case eqz:
                    instructions.remove( i );
                    instructions.set( i - 1,
                            new WasmConstInstruction( Integer.valueOf( constValue == 0 ? 1 : 0 ), lineNo, codePos ) );
                    break;
                default:
                    break;
                }
                break;
            }
            case i64: {
                long constValue = number.longValue();
                switch (wni.numOp) {
                case eqz:
                    instructions.remove( i );
                    instructions.set( i - 1,
                            new WasmConstInstruction( Long.valueOf( constValue == 0 ? 1 : 0 ), lineNo, codePos ) );
                    break;
                default:
                    break;
                }
                break;
            }
            default:
                return;
            }
        }
    }

    // https://webassembly.github.io/spec/core/exec/numerics.html#op-ftrunc
    private float ftrunc(float constValue) {
        if (Float.isNaN( constValue ))
            return Float.NaN;
        else if (Float.isInfinite( constValue ))
            return Float.POSITIVE_INFINITY;
        else if (constValue == 0f)
            return 0f;
        else if (constValue > 0 && constValue <= 0.5f)
            return +0.0f;
        else if (constValue < 0 && constValue >= -0.5f)
            return -0.0f;
        return (float) Math.rint( constValue );
    }

    private double ftrunc(double constValue) {
        if (Double.isNaN( constValue ))
            return Float.NaN;
        else if (Double.isInfinite( constValue ))
            return Double.POSITIVE_INFINITY;
        else if (constValue == 0d)
            return 0d;
        else if (constValue > 0 && constValue <= 0.5d)
            return +0.0d;
        else if (constValue < 0 && constValue >= -0.5d)
            return -0.0d;
        return Math.rint( constValue );
    }

    private void localOptimization(List<WasmInstruction> instructions, int i, WasmInstruction instr) {
        WasmLocalInstruction local2 = (WasmLocalInstruction) instr;
        if (local2.getOperator() == VariableOperator.set) {
            boolean canOptimize = true;
            /*-
             * Dead store elimination: v is a local variable index. 
             * 1. Test, whether instructions[i] is a "local.set  v" operation.<br>
             * 2. Look for a "local.get v " access
             * 2.1 If there is None, we can instead "drop".
             * 2.2 If there is another "local.set v" before a "local.get v", we can optimize this too. 
             * 2.3 Else we can't optimize.
             */
            for (int j = i; j < instructions.size(); j++) {
                if (instructions.get( j ).getType() == Type.Local) {
                    WasmLocalInstruction testedInstr = (WasmLocalInstruction) instructions.get( j );
                    if (testedInstr.getIndex() == local2.getIndex()) {
                        if (testedInstr.getOperator() == VariableOperator.get) {
                            canOptimize = false;
                            break;
                        } else if (testedInstr.getOperator() == VariableOperator.set) {
                            break;
                        }
                    }
                }
            }
            if (canOptimize) {
                instructions.set( i, new WasmBlockInstruction( WasmBlockOperator.DROP, null, local2.getCodePosition(),
                        local2.getLineNumber() ) );
            }
            // merge local.set, local.get --> local.tee
        } else if (i > 0 && (instructions.get( i - 1 ).getType() == Type.Local)
                && ((WasmLocalInstruction) instructions.get( i - 1 )).getOperator() == VariableOperator.set
                && local2.getOperator() == VariableOperator.get) {
            WasmLocalInstruction local1 = (WasmLocalInstruction) instructions.get( i - 1 );
            if (local1.getIndex() == local2.getIndex()) {
                local1.setOperator( VariableOperator.tee );
                instructions.remove( i );
            }
        }
    }
}
