/*
 * Copyright 2017 - 2019 Volker Berlin (i-net software)
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
import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.ProcessBuilder.Redirect;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Scanner;

import javax.annotation.Nonnull;

import org.junit.rules.TemporaryFolder;

import com.google.gson.Gson;

/**
 * A Junit Rule that compile the given classes and compare the results from node and Java.
 * 
 * @author Volker Berlin
 */
public class WasmRule extends TemporaryFolder {

    private static final boolean      IS_WINDOWS   = System.getProperty( "os.name" ).toLowerCase().indexOf( "win" ) >= 0;

    private static final SpiderMonkey spiderMonkey = new SpiderMonkey();

    private static final Wat2Wasm     wat2Wasm = new Wat2Wasm();

    private final Class<?>[]          classes;

    private JWebAssembly              compiler;

    private File                      wasmFile;

    private File                      watFile;

    private File                      nodeScript;

    private File                      spiderMonkeyScript;

    private File                      nodeWatScript;

    private File                      spiderMonkeyWatScript;

    private File                      wat2WasmScript;

    private boolean                   failed;

    private String                    textCompiled;

    private Map<String, Object[]>                  testData;

    private Map<ScriptEngine, Map<String, String>> testResults;

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
     * Set the parameter of a test.
     * 
     * @param params
     *            the parameters with [ScriptEngine,method name,method parameters]
     */
    public void setTestParameters( Collection<Object[]> params ) {
        testData = new HashMap<>();
        for( Object[] param : params ) {
            testData.put( (String)param[1], (Object[])param[2] );
        }
        testResults = new HashMap<>();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void before() throws Throwable {
        compile();
        if( testData != null ) {
            writeJsonTestData( testData );
        }
    }

    /**
     * Write the test data as JSON file.
     * 
     * @param data
     *            the data
     * @throws IOException
     *             if any IO error occur
     */
    private void writeJsonTestData( Map<String, Object[]> data ) throws IOException {
        // a character we need to convert an integer
        HashMap<String, Object[]> copy = new HashMap( data );
        for( Entry<String, Object[]> entry : copy.entrySet() ) {
            Object[] params = entry.getValue();
            for( int i = 0; i < params.length; i++ ) {
                if( params[i] instanceof Character ) {
                    params = Arrays.copyOf( params, params.length );
                    params[i] = new Integer( ((Character)params[i]).charValue() );
                    entry.setValue( params );
                }
            }
        }
        try (OutputStreamWriter jsonData = new OutputStreamWriter( new FileOutputStream( new File( getRoot(), "testdata.json" ) ), StandardCharsets.UTF_8 )) {
            new Gson().toJson( copy, jsonData );
        }
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

    /**
     * Compile the classes of the test.
     * 
     * @throws WasmException
     *             if the compiling is failing
     */
    public void compile() throws WasmException {
        compiler = new JWebAssembly();
        for( Class<?> clazz : classes ) {
            URL url = clazz.getResource( '/' + clazz.getName().replace( '.', '/' ) + ".class" );
            compiler.addFile( url );
        }
        compiler.setProperty( JWebAssembly.DEBUG_NAMES, "true" );
        assertEquals( "true", compiler.getProperty( JWebAssembly.DEBUG_NAMES ) );

        // add the libraries that it can be scanned for annotations
        final String[] libraries = System.getProperty("java.class.path").split(File.pathSeparator);
        for( String lib : libraries ) {
            if( lib.endsWith( ".jar" ) || lib.toLowerCase().contains( "jwebassembly-api" ) ) {
                compiler.addLibrary( new File(lib) );
            }
        }

        textCompiled = compiler.compileToText();
        try {
            create();

            watFile = newFile( "test.wat" );
            try( FileOutputStream stream = new FileOutputStream( watFile ) ) {
                stream.write( textCompiled.getBytes( StandardCharsets.UTF_8 ) );
            }

            wasmFile = newFile( "test.wasm" );
            compiler.compileToBinary( wasmFile );

            nodeScript = createScript( "nodetest.js", "{test.wasm}", wasmFile.getName() );
        } catch( Throwable ex ) {
            System.out.println( textCompiled );
            throwException( ex );
        }
    }

    /**
     * Prepare the node wabt module.
     * 
     * @throws Exception
     *             if any error occur.
     */
    private void prepareNodeWat() throws Exception {
        if( nodeWatScript == null ) {
            nodeWatScript = createScript( "WatTest.js", "{test.wat}", watFile.getName() );

            //create dummy files to prevent error messages
            FileOutputStream jsonPackage = new FileOutputStream( new File( getRoot(), "package.json" ) );
            jsonPackage.write( "{\"name\":\"test\",  \"description\": \"description\", \"license\": \"Apache-2.0\", \"repository\": {}}".getBytes() );
            jsonPackage.close();
            jsonPackage = new FileOutputStream( new File( getRoot(), "package-lock.json" ) );
            jsonPackage.write( "{\"lockfileVersion\": 1}".getBytes() );
            jsonPackage.close();

            ProcessBuilder processBuilder = new ProcessBuilder( "npm", "install", "wabt@nightly" );
            if( IS_WINDOWS ) {
                processBuilder.command().add( 0, "cmd" );
                processBuilder.command().add( 1, "/C" );
            }
            execute( processBuilder );
        }
    }

    /**
     * Prepare the Wat2Wasm tool if not already do. Fire an JUnit fail if the process produce an error.
     * 
     * @throws Exception
     *             if any error occur.
     */
    private void prepareWat2Wasm() throws Exception {
        if( wat2WasmScript == null ) {
            String cmd = wat2Wasm.getCommand();
            File wat2WasmFile = new File( getRoot(), "wat2Wasm.wasm" );
            // the wat2wasm tool
            ProcessBuilder processBuilder =
                            new ProcessBuilder( cmd, watFile.toString(), "-o", wat2WasmFile.toString(), "--enable-saturating-float-to-int", "--enable-sign-extension", "--enable-multi-value", "--enable-exceptions", "--enable-reference-types" );
            execute( processBuilder );

            // create the node script
            wat2WasmScript = createScript( "nodetest.js", "{test.wasm}", wat2WasmFile.getName() );
        }
    }

    /**
     * Execute a external process and redirect the output to the console. Fire an JUnit fail if the process produce an error.
     * 
     * @param processBuilder
     *            the process definition
     * @throws Exception
     *             if any error occur
     */
    private void execute( ProcessBuilder processBuilder ) throws Exception {
        processBuilder.directory( getRoot() );
        processBuilder.redirectOutput( Redirect.INHERIT );
        processBuilder.redirectError( Redirect.INHERIT );
        System.out.println( String.join( " ", processBuilder.command() ) );
        Process process = processBuilder.start();
        int exitCode = process.waitFor();
        if( exitCode != 0 ) {
            fail( readStream( process.getErrorStream() ) );
        }
    }

    /**
     * Load a script resource, patch it and save it
     * 
     * @param name
     *            the resource name
     * @param placeholder
     *            A placeholder that should be replaced.
     * @param value
     *            the replacing value.
     * @return The saved file name
     * @throws IOException
     *             if any IO error occur
     */
    private File createScript( String name, String placeholder, String value ) throws IOException {
        File file = File.createTempFile( "wasm", name, getRoot() );
        URL scriptUrl = getClass().getResource( name );
        String script = readStream( scriptUrl.openStream() );
        script = script.replace( placeholder, value );
        try (FileOutputStream scriptStream = new FileOutputStream( file )) {
            scriptStream.write( script.getBytes( StandardCharsets.UTF_8 ) );
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
                    break;
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
            if( expected instanceof Boolean ) { // WASM does not support boolean that it is number
                expected = new Integer( ((Boolean)expected) ? 1 : 0 );
            }

            String actual = evalWasm( script, methodName, params );
            if( expected instanceof Double ) { // handle different string formating of double values
                assertEquals( actual, expected, Double.valueOf( actual ) );
            } else if( expected instanceof Float ) { // handle different string formating of float values
                assertEquals( actual, expected, Float.valueOf( actual ) );
            } else {
                assertEquals( String.valueOf( expected ), actual );
            }
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
        ProcessBuilder processBuilder = null;
        try {
            if( testData != null ) {
                // data are available as block data
                Map<String, String> resultMap = testResults.get( script );
                if( resultMap != null ) {
                    return resultMap.get( methodName );
                }
            } else {
                // block data then write single test data
                writeJsonTestData( Collections.singletonMap( methodName, params ) );
            }

            switch( script ) {
                case SpiderMonkey:
                    processBuilder = spiderMonkeyCommand( true );
                    break;
                case SpiderMonkeyWat:
                    processBuilder = spiderMonkeyCommand( false );
                    break;
                case NodeJS:
                    processBuilder = nodeJsCommand( nodeScript );
                    break;
                case NodeWat:
                    prepareNodeWat();
                    processBuilder = nodeJsCommand( nodeWatScript );
                    break;
                case Wat2Wasm:
                    prepareWat2Wasm();
                    processBuilder = nodeJsCommand( wat2WasmScript );
                    break;
                default:
                    throw new IllegalStateException( script.toString() );
            }
            processBuilder.directory( getRoot() );
            Process process = processBuilder.start();
            String result = readStream( process.getInputStream() ).trim();
            int exitCode = process.waitFor();
            if( exitCode != 0 || !result.isEmpty() ) {
                String errorMessage = readStream( process.getErrorStream() );
                fail( result + '\n' + errorMessage + "\nExit code: " + exitCode );
            }

            // read the result from file
            try( InputStreamReader jsonData = new InputStreamReader( new FileInputStream( new File( getRoot(), "testresult.json" ) ), StandardCharsets.UTF_8 ) ) {
                Map<String, String> map = new Gson().fromJson( jsonData, Map.class );
                if( testData != null ) {
                    testResults.put( script, map );
                }
                return map.get( methodName );
            }
        } catch( Throwable ex ) {
            failed = true;
            ex.printStackTrace();

            if( processBuilder != null ) {
                String executable = processBuilder.command().get( 0 );
                System.err.println( executable );
                File exec = new File(executable);
                System.err.println( exec.exists() );
                exec = exec.getParentFile();
                if( exec != null ) {
                    System.err.println( Arrays.toString( exec.list() ) );
                }
            }

            throwException( ex );
            return null;
        }
    }

    /**
     * Create a ProcessBuilder for spider monkey script shell.
     * 
     * @param binary true, if the WASM format should be test; false, if the WAT format should be tested.
     * @return the value from the script
     * @throws IOException
     *             if the download failed
     */
    private ProcessBuilder spiderMonkeyCommand( boolean binary ) throws IOException {
        File script;
        try {
            System.setProperty( "SpiderMonkey", "true" );
            if( binary ) {
                if( spiderMonkeyScript == null ) {
                    File file = newFile( "spiderMonkey.wasm" );
                    compiler.compileToBinary( file );
                    spiderMonkeyScript = createScript( "SpiderMonkeyTest.js", "{test.wasm}", file.getName() );
                }
                script = spiderMonkeyScript;
            } else {
                if( spiderMonkeyWatScript == null ) {
                    File file = newFile( "spiderMonkey.wat" );
                    compiler.compileToText( file );
                    spiderMonkeyWatScript = createScript( "SpiderMonkeyWatTest.js", "{test.wat}", file.getName() );
                }
                script = spiderMonkeyWatScript;
            }
        } finally {
            System.clearProperty( "SpiderMonkey" );
        }
        return new ProcessBuilder( spiderMonkey.getCommand(), "--wasm-gc", script.getAbsolutePath() );
    }

    /**
     * The executable of the node command.
     * 
     * @return the node executable
     */
    @Nonnull
    private static String nodeExecuable() {
        String command = System.getProperty( "node.dir" );
        if( command == null ) {
            command = "node";
        } else {
            if( IS_WINDOWS ) {
                command += "/node";
            } else {
                command += "/bin/node";
            }
        }
        return command;
    }

    /**
     * Create a ProcessBuilder for node.js
     * 
     * @param script
     *            the path to the script that should be executed
     * @return the value from the script
     */
    private static ProcessBuilder nodeJsCommand( File script ) {
        String command = nodeExecuable();
        // details see with command: node --v8-options
        ProcessBuilder processBuilder = new ProcessBuilder( command, "--experimental-wasm-mv", "--experimental-wasm-se", "--experimental-wasm-sat-f2i-conversions", "--experimental-wasm-eh", "--experimental-wasm-anyref", "--experimental-wasm-bigint", script.getName() );
        if( IS_WINDOWS ) {
            processBuilder.command().add( 0, "cmd" );
            processBuilder.command().add( 1, "/C" );
        }
        return processBuilder;
    }

    /**
     * Reads a stream into a String.
     * 
     * @param input
     *            the InputStream
     * @return the string
     */
    @SuppressWarnings( "resource" )
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
