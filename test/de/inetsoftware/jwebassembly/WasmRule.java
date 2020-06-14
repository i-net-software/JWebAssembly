/*
 * Copyright 2017 - 2020 Volker Berlin (i-net software)
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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.ProcessBuilder.Redirect;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;

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

    private static final Node         node = new Node();

    private static final Wat2Wasm     wat2Wasm = new Wat2Wasm();

    private static boolean            npmWabtNightly;

    private static String             nodeModulePath;

    private final Class<?>[]          classes;

    private final JWebAssembly        compiler;

    private Map<ScriptEngine, File>   compiledFiles = new HashMap<>();

    private Map<ScriptEngine, File>   scriptFiles = new HashMap<>();

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
        compiler = new JWebAssembly();
        for( Class<?> clazz : classes ) {
            URL url = clazz.getResource( '/' + clazz.getName().replace( '.', '/' ) + ".class" );
            compiler.addFile( url );
        }

        // add the libraries that it can be scanned for annotations
        final String[] libraries = System.getProperty("java.class.path").split(File.pathSeparator);
        for( String lib : libraries ) {
            if( lib.endsWith( ".jar" ) || lib.toLowerCase().contains( "jwebassembly-api" ) ) {
                compiler.addLibrary( new File(lib) );
            }
        }
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
     * Set property to control the behavior of the compiler
     * 
     * @param key
     *            the key
     * @param value
     *            the new value
     */
    public void setProperty( String key, String value ) {
        compiler.setProperty( key, value );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void before() throws Throwable {
        super.before();
        if( testData != null ) {
            writeJsonTestData( testData );
        }
    }

    /**
     * Prepare the rule for the script engine
     * 
     * @param script
     *            the script engine
     * @throws Exception
     *             if any error occur
     */
    public void before( ScriptEngine script ) throws Exception {
        switch( script ) {
            case Wat2Wasm:
                // this is already part of execute and not only a compile
                return;
            default:
                createCommand( script );
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
        HashMap<String, Object[]> copy = new HashMap<>( data );
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
        if( failed ) {
            for( File wasmFile : compiledFiles.values() ) {
                if( wasmFile.getName().endsWith( ".wasm" ) ) {
                    File jsFile = new File( wasmFile.toString() + ".js" );
                    if( jsFile.isFile() ) {
                        try {
                            System.out.println( new String( Files.readAllBytes( jsFile.toPath() ), StandardCharsets.UTF_8 ) );
                            System.out.println();
                        } catch( IOException e ) {
                            e.printStackTrace();
                        }
                    }
                }
            }
            System.out.println( textCompiled );
        }
        super.after();
    }

    /**
     * Compile the classes of the test.
     * 
     * @throws WasmException
     *             if the compiling is failing
     */
    public void compile() throws WasmException {
        try {
            create();
        } catch( Throwable ex ) {
            throwException( ex );
        }
        compile( ScriptEngine.NodeJS );
    }

    /**
     * Compile the classes of the script engine if not already compiled.
     * 
     * @param script
     *            the script engine
     * @return the compiled main file
     * @throws WasmException
     *             if the compiling is failing
     */
    public File compile( ScriptEngine script ) throws WasmException {
        File file = compiledFiles.get( script );
        if( file != null ) {
            // compile only once
            return file;
        }

        compiler.setProperty( JWebAssembly.DEBUG_NAMES, "true" );
        assertEquals( "true", compiler.getProperty( JWebAssembly.DEBUG_NAMES ) );
        compiler.setProperty( JWebAssembly.WASM_USE_GC, script.useGC );

        if( textCompiled == null ) {
            textCompiled = compiler.compileToText();
        }
        try {
            String name = script.name();
            if( name.contains( "Wat" ) ) {
                file = newFile( name + ".wat" );
                compiler.compileToText( file );
            } else {
                file = newFile( name + ".wasm" );
                compiler.compileToBinary( file );
            }
            compiledFiles.put( script, file );
        } catch( Throwable ex ) {
            System.out.println( textCompiled );
            throwException( ex );
        }
        return file;
    }

    /**
     * Prepare the node node script.
     * 
     * @param script
     *            the script engine
     * @return the script file
     * @throws IOException
     *             if any error occur.
     */
    private File prepareNodeJs( ScriptEngine script ) throws IOException {
        File scriptFile = scriptFiles.get( script );
        if( scriptFile == null ) {
            compile( script );
            scriptFile = createScript( script, "nodetest.js", "{test}", script.name() );
            scriptFiles.put( script, scriptFile );
        }
        return scriptFile;
    }

    /**
     * Prepare the node wabt module.
     * 
     * @param script
     *            the script engine
     * @return the script file
     * @throws Exception
     *             if any error occur.
     */
    private File prepareNodeWat( ScriptEngine script ) throws Exception {
        File scriptFile = scriptFiles.get( script );
        if( scriptFile == null ) {
            compile( script );
            scriptFile = createScript( script, "WatTest.js", "{test}", script.name() );
            scriptFiles.put( script, scriptFile );

            if( !npmWabtNightly ) {
                npmWabtNightly = true;
                ProcessBuilder processBuilder = new ProcessBuilder( "npm", "install", "-g", "wabt@nightly" );
                if( IS_WINDOWS ) {
                    processBuilder.command().add( 0, "cmd" );
                    processBuilder.command().add( 1, "/C" );
                }
                execute( processBuilder );
            }
        }
        return scriptFile;
    }

    /**
     * Get the path of the global installed module pathes.
     * 
     * @return the path
     * @throws Exception
     *             if any error occur.
     */
    private static String getNodeModulePath() throws Exception {
        if( nodeModulePath == null ) {
            ProcessBuilder processBuilder = new ProcessBuilder( "npm", "root", "-g" );
            if( IS_WINDOWS ) {
                processBuilder.command().add( 0, "cmd" );
                processBuilder.command().add( 1, "/C" );
            }
            Process process = processBuilder.start();
            process.waitFor();
            nodeModulePath = readStream( process.getInputStream() ).trim(); // module install path
            System.out.println( "node global module path: " + nodeModulePath );

        }
        return nodeModulePath;
    }

    /**
     * Prepare the Wat2Wasm tool if not already do. Fire an JUnit fail if the process produce an error.
     * 
     * @param script
     *            the script engine
     * @return the script file
     * @throws Exception
     *             if any error occur.
     */
    private File prepareWat2Wasm( ScriptEngine script ) throws Exception {
        File scriptFile = scriptFiles.get( script );
        if( scriptFile == null ) {
            File watFile = compile( ScriptEngine.NodeJS );
            String cmd = wat2Wasm.getCommand();
            File wat2WasmFile = new File( getRoot(), "wat2Wasm.wasm" );
            // the wat2wasm tool
            ProcessBuilder processBuilder =
                            new ProcessBuilder( cmd, watFile.toString(), "-o", wat2WasmFile.toString(), "--debug-names", "--enable-all" );
            execute( processBuilder );

            // create the node script
            scriptFile = createScript( script, "nodetest.js", "{test}", script.name() );
        }
        return scriptFile;
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
            fail( readStream( process.getErrorStream() ) + "\nExit code: " + exitCode + " from: " + processBuilder.command().get( 0 ) );
        }
    }

    /**
     * Load a script resource, patch it and save it
     * 
     * @param script
     *            the script engine
     * @param name
     *            the template resource name
     * @param placeholder
     *            A placeholder that should be replaced.
     * @param value
     *            the replacing value.
     * @return The saved file name
     * @throws IOException
     *             if any IO error occur
     */
    private File createScript( ScriptEngine script, String name, String placeholder, String value ) throws IOException {
        File file = newFile( script.name() + "Test.js" );
        URL scriptUrl = getClass().getResource( name );
        String template = readStream( scriptUrl.openStream() );
        template = template.replace( placeholder, value );
        try (FileOutputStream scriptStream = new FileOutputStream( file )) {
            scriptStream.write( template.getBytes( StandardCharsets.UTF_8 ) );
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

            Object actual;
            String actualStr = evalWasm( script, methodName, params );
            if( expected instanceof Double ) { // handle different string formating of double values
                try {
                    actual = Double.valueOf( actualStr );
                } catch( NumberFormatException ex ) {
                    actual = actualStr;
                }
            } else if( expected instanceof Float ) { // handle different string formating of float values
                try {
                    actual = Float.valueOf( actualStr );
                } catch( NumberFormatException ex ) {
                    actual = actualStr;
                }
            } else {
                expected = String.valueOf( expected );
                actual = actualStr;
            }
            assertEquals( expected, actual );
        } catch( InvocationTargetException ex ) {
            failed = true;
            throwException( ex.getTargetException() );
        } catch( Throwable ex ) {
            failed = true;
            throwException( ex );
        }
    }

    /**
     * Compile the sources and create the ProcessBuilder
     * 
     * @param script
     *            The script engine
     * @return ProcessBuilder to execute the test
     * @throws Exception
     *             if any error occur
     */
    private ProcessBuilder createCommand( ScriptEngine script ) throws Exception {
        compiler.setProperty( JWebAssembly.WASM_USE_GC, script.useGC );
        switch( script ) {
            case SpiderMonkey:
                return spiderMonkeyCommand( true, script );
            case SpiderMonkeyWat:
                return spiderMonkeyCommand( false, script );
            case SpiderMonkeyGC:
                return spiderMonkeyCommand( true, script );
            case SpiderMonkeyWatGC:
                return spiderMonkeyCommand( false, script );
            case NodeJS:
            case NodeJsGC:
                return nodeJsCommand( prepareNodeJs( script ) );
            case NodeWat:
            case NodeWatGC:
                ProcessBuilder processBuilder = nodeJsCommand( prepareNodeWat( script ) );
                processBuilder.environment().put( "NODE_PATH", getNodeModulePath() );
                return processBuilder;
            case Wat2Wasm:
                return nodeJsCommand( prepareWat2Wasm( script ) );
            default:
                throw new IllegalStateException( script.toString() );
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

            processBuilder = createCommand( script );
            processBuilder.directory( getRoot() );
            Process process = processBuilder.start();

            String stdoutMessage = "";
            String errorMessage = "";
            do {
                if( process.getInputStream().available() > 0 ) {
                    stdoutMessage += readStream( process.getInputStream() );
                }
                if( process.getErrorStream().available() > 0 ) {
                    errorMessage += readStream( process.getErrorStream() );
                }
            }
            while( !process.waitFor( 10, TimeUnit.MILLISECONDS ) );
            stdoutMessage += readStream( process.getInputStream() );
            errorMessage += readStream( process.getErrorStream() );
            int exitCode = process.exitValue();
            if( exitCode != 0 || !stdoutMessage.isEmpty() || !errorMessage.isEmpty() ) {
                System.err.println( stdoutMessage );
                System.err.println( errorMessage );
                fail( stdoutMessage + '\n' + errorMessage + "\nExit code: " + exitCode );
            }

            // read the result from file
            try( InputStreamReader jsonData = new InputStreamReader( new FileInputStream( new File( getRoot(), "testresult.json" ) ), StandardCharsets.UTF_8 ) ) {
                @SuppressWarnings( "unchecked" )
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
     * @param binary
     *            true, if the WASM format should be test; false, if the WAT format should be tested.
     * @param script
     *            the script engine
     * @return the value from the script
     * @throws IOException
     *             if the download failed
     */
    private ProcessBuilder spiderMonkeyCommand( boolean binary, ScriptEngine script ) throws IOException {
        boolean gc = Boolean.valueOf( script.useGC );
        File scriptFile = scriptFiles.get( script );
        if( scriptFile == null ) {
            File file = compile( script );
            if( gc ) {
                if( binary ) {
                    scriptFile = createScript( script, "SpiderMonkeyTest.js", "{test.wasm}", file.getName() );
                } else {
                    scriptFile = createScript( script, "SpiderMonkeyWatTest.js", "{test}", script.name() );
                }
            } else {
                if( binary ) {
                    scriptFile = createScript( script, "SpiderMonkeyTest.js", "{test.wasm}", file.getName() );
                } else {
                    scriptFile = createScript( script, "SpiderMonkeyWatTest.js", "{test}", script.name() );
                }
            }
            scriptFiles.put( script, scriptFile );
        }

        ProcessBuilder process = new ProcessBuilder( spiderMonkey.getCommand(), scriptFile.getAbsolutePath() );
        if( gc ) {
            process.command().add( 1, "--wasm-gc" );
        }
        return process;
    }

    /**
     * The executable of the node command.
     * 
     * @return the node executable
     * @throws IOException
     *             if any I/O error occur
     */
    @Nonnull
    private static String nodeExecuable() throws IOException {
        String command = node.getNodeDir();
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
     * @param nodeScript
     *            the path to the script that should be executed
     * @return the value from the script
     * @throws IOException
     *             if any I/O error occur
     */
    private static ProcessBuilder nodeJsCommand( File nodeScript ) throws IOException {
        String command = nodeExecuable();
        // details see with command: node --v8-options
        ProcessBuilder processBuilder = new ProcessBuilder( command, //
                        "--experimental-wasm-mv", // multi value
                        "--experimental-wasm-eh", // exception handling
                        "--experimental-wasm-anyref", //
                        "--experimental-wasm-gc", //
                        "--experimental-wasm-bigint", //
                        "--experimental-wasm-bulk-memory", // bulk memory for WABT version 1.0.13, https://github.com/WebAssembly/wabt/issues/1311
                        nodeScript.getName() );
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
     * @throws IOException
     *             if an I/O error occurs.
     */
    @SuppressWarnings( "resource" )
    public static String readStream( InputStream input ) throws IOException {
        byte[] bytes = new byte[8192];
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        int count;
        while( (count = input.read( bytes )) > 0 ) {
            stream.write( bytes, 0, count );
        }
        return new String( stream.toByteArray() );
    }

    /**
     * Throw any exception independent of signatures
     * 
     * @param exception
     *            the exception
     * @throws T
     *             a generic helper
     */
    @SuppressWarnings( "unchecked" )
    public static <T extends Throwable> void throwException( Throwable exception ) throws T {
        throw (T)exception;
    }
}
