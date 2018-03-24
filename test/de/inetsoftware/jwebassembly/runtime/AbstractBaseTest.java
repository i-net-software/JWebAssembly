package de.inetsoftware.jwebassembly.runtime;

import java.util.ArrayList;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import de.inetsoftware.jwebassembly.ScriptEngine;
import de.inetsoftware.jwebassembly.WasmRule;

/**
 * @author Volker Berlin
 */
@RunWith( Parameterized.class )
public abstract class AbstractBaseTest {

    private final WasmRule     wasm;

    private final ScriptEngine script;

    private final String       method;

    private final Object[]     params;

    protected AbstractBaseTest( WasmRule wasm, ScriptEngine script, String method, Object[] params ) {
        this.wasm = wasm;
        this.script = script;
        this.method = method;
        this.params = params;
    }

    protected static void addParam( ArrayList<Object[]> list, ScriptEngine script, String method, Object ...params ) {
        list.add( new Object[]{script, method, params} );
    }

    @Test
    public void test() {
        wasm.test( script, method, params );
    }
}
