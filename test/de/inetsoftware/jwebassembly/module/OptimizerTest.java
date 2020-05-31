package de.inetsoftware.jwebassembly.module;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import de.inetsoftware.jwebassembly.module.WasmInstruction.Type;
import de.inetsoftware.jwebassembly.wasm.NumericOperator;
import de.inetsoftware.jwebassembly.wasm.ValueType;
import de.inetsoftware.jwebassembly.wasm.VariableOperator;

public class OptimizerTest {
    /*-
     * i32.const 500
     * i32.const 100
     * i32.add 
     * ==>
     * i32.const 600
     */
    @Test
    public void testBinaryOpFolding() {
        List<WasmInstruction> wi = new ArrayList<>();
        wi.add( new WasmConstInstruction( Integer.valueOf( 500 ), -1, -1 ) );
        wi.add( new WasmConstInstruction( Integer.valueOf( 100 ), -1, -1 ) );
        wi.add( new WasmNumericInstruction( NumericOperator.add, ValueType.i32, -1, -1 ) );
        CodeOptimizer co = new CodeOptimizer();
        co.optimize( wi );
        assertEquals( 1, wi.size() );
        assertEquals( 600, ((WasmConstInstruction) wi.get( 0 )).getValue().intValue() );
    }

    /*-
     * i32.const 500
     * i32.const 500
     * i32.const 100
     * i32.add
     * i32.mul
     * 1.Pass:
     * i32.const 500
     * i32.const 600
     * i32.mul
     * 2.Pass:
     * i32.const (500*600)
     */
    @Test
    public void testBinaryOpFolding2() {
        List<WasmInstruction> wi = new ArrayList<>();
        wi.add( new WasmConstInstruction( Integer.valueOf( 500 ), -1, -1 ) );
        wi.add( new WasmConstInstruction( Integer.valueOf( 500 ), -1, -1 ) );
        wi.add( new WasmConstInstruction( Integer.valueOf( 100 ), -1, -1 ) );
        wi.add( new WasmNumericInstruction( NumericOperator.add, ValueType.i32, -1, -1 ) );
        wi.add( new WasmNumericInstruction( NumericOperator.mul, ValueType.i32, -1, -1 ) );
        CodeOptimizer co = new CodeOptimizer();
        co.optimize( wi );
        assertEquals( 1, wi.size() );
        assertEquals( 600 * 500, ((WasmConstInstruction) wi.get( 0 )).getValue().intValue() );
    }

    @Test
    public void testUnop() {
        List<WasmInstruction> wi = new ArrayList<>();
        wi.add( new WasmConstInstruction( Integer.valueOf( 0 ), -1, -1 ) );
        wi.add( new WasmNumericInstruction( NumericOperator.eqz, ValueType.i32, -1, -1 ) );
        wi.add( new WasmConstInstruction( Integer.valueOf( 1 ), -1, -1 ) );
        wi.add( new WasmNumericInstruction( NumericOperator.eqz, ValueType.i32, -1, -1 ) );
        CodeOptimizer co = new CodeOptimizer();
        co.optimize( wi );
        assertEquals( 2, wi.size() );
        assertEquals( Type.Const, wi.get( 0 ).getType() );
        assertEquals( Type.Const, wi.get( 1 ).getType() );
    }

    /*-
     * i32.const 500
     * i32.store 0
     * i32.const 100
     * i32.store 0
     * ==>
     * i32.const 500
     * drop
     * i32.const 100
     * drop
     * TODO: Optimize to:
     * <nothing>
     */
    @Test
    public void testDeadstore() {
        List<WasmInstruction> wi = new ArrayList<>();
        wi.add( new WasmConstInstruction( Integer.valueOf( 500 ), -1, -1 ) );
        wi.add( new WasmLocalInstruction( VariableOperator.set, 0, null, -1, -1 ) );
        wi.add( new WasmConstInstruction( Integer.valueOf( 100 ), -1, -1 ) );
        wi.add( new WasmLocalInstruction( VariableOperator.set, 0, null, -1, -1 ) );
        CodeOptimizer co = new CodeOptimizer();
        co.optimize( wi );
        assertEquals( 4, wi.size() );
        assertEquals( Type.Const, wi.get( 0 ).getType() );
        assertEquals( Type.Block, wi.get( 1 ).getType() );
        assertEquals( Type.Const, wi.get( 2 ).getType() );
        assertEquals( Type.Block, wi.get( 3 ).getType() );
    }

	/*-
	 * i32.const 500
	 * i32.set 0
	 * i32.get 0
	 * ==>
	 * i32.const 0
	 * i32.tee 0
	 */
    @Test
    public void testTee() {
        List<WasmInstruction> wi = new ArrayList<>();
        wi.add( new WasmConstInstruction( Integer.valueOf( 500 ), -1, -1 ) );
        wi.add( new WasmLocalInstruction( VariableOperator.set, 0, null, -1, -1 ) );
        wi.add( new WasmLocalInstruction( VariableOperator.get, 0, null, -1, -1 ) );
        CodeOptimizer co = new CodeOptimizer();
        co.optimize( wi );
        assertEquals( 2, wi.size() );
        assertEquals( Type.Const, wi.get( 0 ).getType() );
        assertEquals( Type.Local, wi.get( 1 ).getType() );
    }
}
