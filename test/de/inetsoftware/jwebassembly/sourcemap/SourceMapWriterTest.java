package de.inetsoftware.jwebassembly.sourcemap;

import static org.junit.Assert.assertEquals;

import java.io.IOException;

import org.junit.Test;

public class SourceMapWriterTest {

    @Test
    public void simple() throws IOException {
        SourceMapWriter map = new SourceMapWriter( null );

        map.addMapping( new SourceMapping( 0, 1, "Test1.java" ) );
        map.addMapping( new SourceMapping( 5, 2, "Test1.java" ) );
        map.addMapping( new SourceMapping( 0, 4, "Test2.java" ) );
        map.addMapping( new SourceMapping( 5, 9, "Test2.java" ) );

        StringBuilder generate = new StringBuilder();
        map.generate( generate );
        String expected = "{\n" + 
                        "\"version\":3,\n" + 
                        "\"sources\":[\"Test1.java\",\"Test2.java\"],\n" + 
                        "\"names\":[],\n" + 
                        "\"mappings\":\"AAAA,KACA,LCEA,KAKA;\"\n" + 
                        "}";
        assertEquals( expected, generate.toString() );
    }
}
