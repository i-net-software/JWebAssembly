package de.inetsoftware.jwebassembly.runtime;

import java.util.ArrayList;
import java.util.Collection;

import org.junit.ClassRule;
import org.junit.runners.Parameterized.Parameters;
import org.webassembly.annotation.Export;

import de.inetsoftware.jwebassembly.ScriptEngine;
import de.inetsoftware.jwebassembly.WasmRule;

public class ControlFlowOperators extends AbstractBaseTest {

    @ClassRule
    public static WasmRule rule = new WasmRule( TestClass.class );

    public ControlFlowOperators( ScriptEngine script, String method, Object[] params ) {
        super( rule, script, method, params );
    }

    @Parameters( name = "{0}-{1}" )
    public static Collection<Object[]> data() {
        ArrayList<Object[]> list = new ArrayList<>();
        for( ScriptEngine[] val : ScriptEngine.testParams() ) {
            ScriptEngine script = val[0];
            addParam( list, script, "ifThenElse_Int0" );
            addParam( list, script, "forLoop" );
        }
        return list;
    }

    static class TestClass {

        @Export
        static int ifThenElse_Int0() {
            int condition = 0;
            if( condition != 0 ) {
                return 13;
            } else {
                return 76;
            }
        }

        @Export
        static int forLoop() {
            int a = 0;
            //            for( int i=0; i<10;i++) {
            //                a += i;
            //            }
            return a;
        }
    }
}
