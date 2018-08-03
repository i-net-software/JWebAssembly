/*
 * Copyright 2017 - 2018 Volker Berlin (i-net software)
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
package de.inetsoftware.jwebassembly;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

import org.junit.rules.TemporaryFolder;

/**
 * A Junit Rule that compile the given classes and compare the results from node and Java.
 * 
 * @author Volker Berlin
 */
public class WasmRule extends TemporaryFolder {

    private static final SpiderMonkey spiderMonkey = new SpiderMonkey();

    private final Class<?>[]          classes;

    private File                      wasmFile;

    private File                      nodeScript;

    private File                      spiderMonkeyScript;

    private boolean                   failed;

    private String                    textCompiled;

    /**
     * Compile the given classes to a Wasm and save it to a file.
     * 
     * @param classes
     *            list of classes to compile
     */
    public WasmRule( Class<?>... classes ) {
        if( classes == null || classes.length == 0 ) {
            throw new IllegalArgumentException( "You need to set minimum one test class" );
        }
        this.classes = classes;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void before() throws Throwable {
        compile();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void after() {
        super.after();
        if( failed ) {
            System.out.println( textCompiled );
        }
    }

    public void compile() throws IOException, WasmException {
        JWebAssembly wasm = new JWebAssembly();
        for( Class<?> clazz : classes ) {
            URL url = clazz.getResource( '/' + clazz.getName().replace( '.', '/' ) + ".class" );
            wasm.addFile( url );
        }
        textCompiled = wasm.compileToText(); // smoke test
        try {
            create();  
            wasmFile = newFile( "test.wasm" );
            wasm.compileToBinary( wasmFile );
    
            nodeScript = createScript( "nodetest.js" );
            spiderMonkeyScript = createScript( "SpiderMonkeyTest.js" );
        } catch( Throwable ex ) {
            System.out.println( textCompiled );
            throwException( ex );
        }
    }

    /**
     * Load a script resource, patch it and save it
     * 
     * @param name
     *            the resource name
     * @return The saved file name
     * @throws IOException
     *             if any IO error occur
     */
    private File createScript( String name ) throws IOException {
        File file = newFile( name );
        URL scriptUrl = getClass().getResource( name );
        String expected = readStream( scriptUrl.openStream() );
        expected = expected.replace( "{test.wasm}", wasmFile.getName() );
        try (FileOutputStream scriptStream = new FileOutputStream( file )) {
            scriptStream.write( expected.getBytes( StandardCharsets.UTF_8 ) );
        }
        return file;
    }

    /**
     * Run a test single test. It run the method in Java and call it via node in the WenAssembly. If the result are
     * different it fire an error.
     * 
     * @param script
     *            The script engine
     * @param methodName
     *            the method name of the test.
     * @param params
     *            the parameters for the method
     */
    public void test( ScriptEngine script, String methodName, Object... params ) {
        Object expected;
        try {
            Class<?>[] types = new Class[params.length];
            for( int i = 0; i < types.length; i++ ) {
                Class<?> type = params[i].getClass();
                switch( type.getName() ) {
                    case "java.lang.Byte":
                        type = byte.class;
                        break;
                    case "java.lang.Short":
                        type = short.class;
                        break;
                    case "java.lang.Character":
                        type = char.class;
                        break;
                    case "java.lang.Integer":
                        type = int.class;
                        break;
                    case "java.lang.Long":
                        type = long.class;
                        break;
                    case "java.lang.Float":
                        type = float.class;
                        break;
                    case "java.lang.Double":
                        type = double.class;
                        break;
                }
                types[i] = type;
            }
            Method method = null;
            for( int i = 0; i < classes.length; i++ ) {
                try {
                    Class<?> clazz = classes[i];
                    method = clazz.getDeclaredMethod( methodName, types );
                } catch( NoSuchMethodException ex ) {
                    if( i == classes.length - 1 ) {
                        throw ex;
                    }
                }
            }
            method.setAccessible( true );
            expected = method.invoke( null, params );
            if( expected instanceof Character ) { // WASM does not support char that it is number
                expected = new Integer( ((Character)expected).charValue() );
            }

            for( int i = 0; i < params.length; i++ ) {
                if( params[i] instanceof Character ) {
                    params[i] = new Integer( ((Character)params[i]).charValue() );
                }
            }
            String actual = evalWasm( script, methodName, params );
            assertEquals( String.valueOf( expected ), actual );
        } catch( Throwable ex ) {
            failed = true;
            throwException( ex );
            return;
        }
    }

    /**
     * Evaluate the wasm exported function.
     * 
     * @param script
     *            The script engine
     * @param methodName
     *            the method name of the test.
     * @param params
     *            the parameters for the method
     * @return the output of the script
     */
    public String evalWasm( ScriptEngine script, String methodName, Object... params ) {
        try {
            ProcessBuilder processBuilder;
            switch( script ) {
                case SpiderMonkey:
                    processBuilder = spiderMonkeyCommand();
                    break;
                case NodeJS:
                    processBuilder = nodeJsCommand();
                    break;
                default:
                    throw new IllegalStateException( script.toString() );
            }
            processBuilder.command().add( methodName );
            for( int i = 0; i < params.length; i++ ) {
                processBuilder.command().add( String.valueOf( params[i] ) );
            }
            processBuilder.directory( getRoot() );
            Process process = processBuilder.start();
            int exitCode = process.waitFor();
            String result = readStream( process.getInputStream() ).trim();
            if( exitCode != 0 ) {
                String errorMessage = readStream( process.getErrorStream() );
                assertEquals( errorMessage, 0, exitCode );
            }
            return result;
        } catch( Throwable ex ) {
            failed = true;
            throwException( ex );
            return null;
        }
    }

    /**
     * Create a ProcessBuilder for spider monkey script shell.
     * 
     * @return the value from the script
     * @throws IOException
     *             if the download failed
     */
    private ProcessBuilder spiderMonkeyCommand() throws IOException {
        return new ProcessBuilder( spiderMonkey.getCommand(), spiderMonkeyScript.getAbsolutePath() );
    }

    /**
     * Create a ProcessBuilder for node.js
     * 
     * @return the value from the script
     */
    private ProcessBuilder nodeJsCommand() {
        String command = System.getProperty( "node.dir" );
        command = command == null ? "node" : command + "/node";
        return new ProcessBuilder( command, "--experimental-wasm-se", nodeScript.getAbsolutePath() );
    }

    /**
     * Reads a stream into a String.
     * 
     * @param input
     *            the InputStream
     * @return the string
     */
    public static String readStream( InputStream input ) {
        try (Scanner scanner = new Scanner( input ).useDelimiter( "\\A" )) {
            return scanner.hasNext() ? scanner.next() : "";
        }
    }

    /**
     * Throw any exception independent of signatures
     * 
     * @param exception
     *            the exception
     * @throws T
     *             a generic helper
     */
    public static <T extends Throwable> void throwException( Throwable exception ) throws T {
        throw (T)exception;
    }
}
