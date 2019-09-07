package de.inetsoftware.jwebassembly.javascript;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.function.Function;

import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import de.inetsoftware.jwebassembly.module.WasmTarget;

public class JavaScriptWriterTest {

    @ClassRule
    public static TemporaryFolder temp = new TemporaryFolder();

    Function<String,Object> f(Map<String,Object> map) {
        return (key) -> map.get( key );
    }

    @Test
    public void single() throws IOException {
        JavaScriptWriter writer = new JavaScriptWriter( new WasmTarget( temp.newFile() ) );
        writer.addImport( "Foo", "bar", f( Collections.singletonMap( JavaScriptWriter.JAVA_SCRIPT_CONTENT, "1 + 1" ) ) );
        StringBuilder builder = new StringBuilder();
        writer.finish( builder );
        assertEquals( "'use strict';var wasmImports = {\n" + 
                        "Foo:{\n" + 
                        "bar:1 + 1\n" + 
                        "}\n" + 
                        "};\n" + 
                        "if (typeof module !== 'undefined') module.exports = wasmImports;", builder.toString() );
    }

    @Test
    public void twoFunctions() throws IOException {
        JavaScriptWriter writer = new JavaScriptWriter( new WasmTarget( temp.newFile() ) );
        writer.addImport( "Foo", "bar", f( Collections.singletonMap( JavaScriptWriter.JAVA_SCRIPT_CONTENT, "1 + 1" ) ) );
        writer.addImport( "Foo", "xyz", f( Collections.singletonMap( JavaScriptWriter.JAVA_SCRIPT_CONTENT, "3" ) ) );
        StringBuilder builder = new StringBuilder();
        writer.finish( builder );
        assertEquals( "'use strict';var wasmImports = {\n" + 
                        "Foo:{\n" + 
                        "bar:1 + 1,\n" + 
                        "xyz:3\n" + 
                        "}\n" + 
                        "};\n" + 
                        "if (typeof module !== 'undefined') module.exports = wasmImports;", builder.toString() );
    }

    @Test
    public void twoModules() throws IOException {
        JavaScriptWriter writer = new JavaScriptWriter( new WasmTarget( temp.newFile() ) );
        writer.addImport( "Foo", "foo", f( Collections.singletonMap( JavaScriptWriter.JAVA_SCRIPT_CONTENT, "1 + 1" ) ) );
        writer.addImport( "Bar", "bar", f( Collections.singletonMap( JavaScriptWriter.JAVA_SCRIPT_CONTENT, "3" ) ) );
        StringBuilder builder = new StringBuilder();
        writer.finish( builder );
        assertEquals( "'use strict';var wasmImports = {\n" + 
                        "Bar:{\n" + 
                        "bar:3\n" + 
                        "},\n" + 
                        "Foo:{\n" + 
                        "foo:1 + 1\n" + 
                        "}\n" + 
                        "};\n" + 
                        "if (typeof module !== 'undefined') module.exports = wasmImports;", builder.toString() );
    }

    @Test
    public void rootModule() throws IOException {
        JavaScriptWriter writer = new JavaScriptWriter( new WasmTarget( temp.newFile() ) );
        writer.addImport( "Foo", "foo", f( Collections.emptyMap() ) );
        writer.addImport( "Foo", "bar", f( Collections.emptyMap() ) );
        StringBuilder builder = new StringBuilder();
        writer.finish( builder );
        assertEquals( "'use strict';var wasmImports = {\n" + 
                        "Foo:Foo\n" + 
                        "};\n" + 
                        "if (typeof module !== 'undefined') module.exports = wasmImports;", builder.toString() );
    }
}
