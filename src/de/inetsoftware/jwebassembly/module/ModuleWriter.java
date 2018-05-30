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
package de.inetsoftware.jwebassembly.module;

import java.io.Closeable;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import de.inetsoftware.classparser.ClassFile;
import de.inetsoftware.classparser.Code;
import de.inetsoftware.classparser.CodeInputStream;
import de.inetsoftware.classparser.ConstantPool;
import de.inetsoftware.classparser.ConstantRef;
import de.inetsoftware.classparser.MethodInfo;
import de.inetsoftware.jwebassembly.WasmException;

/**
 * Module Writer for text format with S-expressions.
 * 
 * @author Volker Berlin
 */
public abstract class ModuleWriter implements Closeable {

    private int                   paramCount;

    private ValueType             returnType;

    private LocaleVariableManager localVariables = new LocaleVariableManager();

    private String                sourceFile;

    private BranchManger          branchManager  = new BranchManger();

    private ValueStackManger      stackManager   = new ValueStackManger();

    /**
     * Prepare the content of the class.
     * 
     * @param classFile
     *            the class file
     * @throws WasmException
     *             if some Java code can't converted
     */
    public void prepare( ClassFile classFile ) {
        iterateMethods( classFile, m -> prepareMethod( m ) );
    }

    /**
     * Finish the prepare after all classes/methods are prepare. This must be call before we can start with write the
     * first method.
     */
    public void prepareFinish() {

    }

    /**
     * Write the content of the class to the writer.
     * 
     * @param classFile
     *            the class file
     * @throws WasmException
     *             if some Java code can't converted
     */
    public void write( ClassFile classFile ) throws WasmException {
        iterateMethods( classFile, m -> writeMethod( m ) );
    }

    private void iterateMethods( ClassFile classFile, Consumer<MethodInfo> handler ) throws WasmException {
        sourceFile = null; // clear previous value for the case an IO exception occur
        try {
            sourceFile = classFile.getSourceFile();
            if( sourceFile == null ) {
                sourceFile = classFile.getThisClass().getName();
            }
            MethodInfo[] methods = classFile.getMethods();
            for( MethodInfo method : methods ) {
                Code code = method.getCode();
                if( method.getName().equals( "<init>" ) && method.getDescription().equals( "()V" )
                                && code.isSuperInitReturn( classFile.getSuperClass() ) ) {
                    continue; //default constructor
                }
                handler.accept( method );
            }
        } catch( IOException ioex ) {
            throw WasmException.create( ioex, sourceFile, -1 );
        }
    }

    /**
     * Prepare the method.
     * 
     * @param method
     *            the method
     * @throws WasmException
     *             if some Java code can't converted
     */
    private void prepareMethod( MethodInfo method ) throws WasmException {
        try {
            String module = null;
            String name = null;
            Map<String,Object> annotationValues = method.getAnnotation( "org.webassembly.annotation.Import" );
            if( annotationValues != null ) {
                module = (String)annotationValues.get( "module" );
                name = (String)annotationValues.get( "name" );
            }
            prepareFunction( new FunctionName( method ), module, name );
        } catch( IOException ioex ) {
            throw WasmException.create( ioex, sourceFile, -1 );
        }
    }

    /**
     * Prepare a single function in the prepare phase.
     * 
     * @param name
     *            the function name
     * @param importModule
     *            the import module name if it is a import function
     * @param importName
     *            the import name if it is a import function
     * @throws IOException
     *             if any I/O error occur
     */
    protected abstract void prepareFunction( FunctionName name, String importModule, String importName ) throws IOException;

    /**
     * Write the content of a method.
     * 
     * @param method
     *            the method
     * @throws WasmException
     *             if some Java code can't converted
     */
    private void writeMethod( MethodInfo method ) throws WasmException {
        int lineNumber = -1;
        try {
            Code code = method.getCode();
            lineNumber = code.getFirstLineNr();
            if( code != null && method.getAnnotation( "org.webassembly.annotation.Import" ) == null ) { // abstract methods and interface methods does not have code
                FunctionName name = new FunctionName( method );
                writeExport( name, method );
                writeMethodStart( name );
                writeMethodSignature( method );

                localVariables.reset();
                stackManager.reset();
                branchManager.reset();
                for( CodeInputStream byteCode : code.getByteCodes() ) {
                    prepareCodeChunk( byteCode, lineNumber = byteCode.getLineNumber(), method.getConstantPool() );
                }
                branchManager.calculate();
                localVariables.calculate();

                CodeInputStream byteCode = null;
                boolean endWithReturn = false;
                Iterator<CodeInputStream> byteCodes = code.getByteCodes().iterator();
                while( byteCodes.hasNext() ) {
                    byteCode = byteCodes.next();
                    writeCodeChunk( byteCode, lineNumber = byteCode.getLineNumber(), method.getConstantPool() );
                }
                branchManager.handle( byteCode, this ); // write the last end operators
                if( !endWithReturn && returnType != null ) {
                    // if a method ends with a loop without a break then code after the loop is no reachable
                    // Java does not need a return byte code in this case
                    // But WebAssembly need the dead code to validate
                    switch( returnType ) {
                        case i32:
                            writeConstInt( 0 );
                            break;
                        case i64:
                            writeConstLong( 0 );
                            break;
                        case f32:
                            writeConstFloat( 0 );
                            break;
                        case f64:
                            writeConstDouble( 0 );
                            break;
                    }
                    writeBlockCode( WasmBlockOperator.RETURN, null );
                }
                writeMethodFinish( localVariables.getLocalTypes( paramCount ) );
            }
        } catch( Exception ioex ) {
            throw WasmException.create( ioex, sourceFile, lineNumber );
        }
    }

    /**
     * Look for a Export annotation and if there write an export directive.
     * 
     * @param name
     *            the function name
     * @param method
     *            the method
     * 
     * @throws IOException
     *             if any IOException occur
     */
    private void writeExport( FunctionName name, MethodInfo method ) throws IOException {
        Map<String,Object> export = method.getAnnotation( "org.webassembly.annotation.Export" );
        if( export != null ) {
            String exportName = (String)export.get( "name" );
            if( exportName == null ) {
                exportName = method.getName();  // TODO naming conversion rule if no name was set
            }
            writeExport( name, exportName );
        }
    }

    /**
     * Write an export directive
     * @param name
     *            the function name
     * @param exportName
     *            the export name, if null then the same like the method name
     * 
     * @throws IOException
     *             if any I/O error occur
     */
    protected abstract void writeExport( FunctionName name, String exportName ) throws IOException;

    /**
     * Write the method header.
     * @param name
     *            the function name
     * 
     * @throws IOException
     *             if any I/O error occur
     */
    protected abstract void writeMethodStart( FunctionName name ) throws IOException;

    /**
     * Write the parameter and return signatures
     * 
     * @param method
     *            the method
     * @throws IOException
     *             if any I/O error occur
     * @throws WasmException
     *             if some Java code can't converted
     */
    private void writeMethodSignature( MethodInfo method ) throws IOException, WasmException {
        String signature = method.getDescription();
        String kind = "param";
        int paramCount = 0;
        ValueType type = null;
        for( int i = 1; i < signature.length(); i++ ) {
            paramCount++;
            if( signature.charAt( i ) == ')' ) {
                this.paramCount = paramCount - 1;
                kind = "result";
                continue;
            }
            type = getValueType( signature, i );
            if( type != null ) {
                writeMethodParam( kind, type );
            }
        }
        this.returnType = type;
    }

    /**
     * Get the WebAssembly value type from a Java signature.
     * 
     * @param signature
     *            the signature
     * @param idx
     *            the index in the signature
     * @return the value type or null if void
     */
    private ValueType getValueType( String signature, int idx ) {
        String javaType;
        switch( signature.charAt( idx ) ) {
            case '[': // array
                javaType = "array";
                break;
            case 'L':
                javaType = "object";
                break;
            case 'B': // byte
            case 'C': // char
            case 'S': // short
            case 'I': // int
                return ValueType.i32;
            case 'D': // double
                return ValueType.f64;
            case 'F': // float
                return ValueType.f32;
            case 'J': // long
                return ValueType.i64;
            case 'V': // void
                return null;
            default:
                javaType = signature.substring( idx, idx + 1 );
        }
        throw new WasmException( "Not supported Java data type in method signature: " + javaType, sourceFile, -1 );
    }

    /**
     * Write a method parameter.
     * 
     * @param kind
     *            "param", "result" or "local"
     * @param valueType
     *            the data type of the parameter
     * @throws IOException
     *             if any I/O error occur
     */
    protected abstract void writeMethodParam( String kind, ValueType valueType ) throws IOException;

    /**
     * Complete the method
     * 
     * @param locals
     *            a list with types of local variables
     * 
     * @throws IOException
     *             if any I/O error occur
     */
    protected abstract void writeMethodFinish( List<ValueType> locals ) throws IOException;

    /**
     * Analyze and prepare a chunk of byte code.
     * 
     * @param byteCode
     *            a stream of byte code
     * @param lineNumber
     *            the current line number
     * @param constantPool
     *            the constant pool of the the current class
     * @throws WasmException
     *             if some Java code can't converted
     */
    private void prepareCodeChunk( CodeInputStream byteCode, int lineNumber, ConstantPool constantPool  ) throws WasmException {
        try {
            while( byteCode.available() > 0 ) {
                int codePosition = byteCode.getCodePosition();
                int op = byteCode.readUnsignedByte();
                switch( op ) {
                    case 2: // iconst_m1
                    case 3: // iconst_0
                    case 4: // iconst_1
                    case 5: // iconst_2
                    case 6: // iconst_3
                    case 7: // iconst_4
                    case 8: // iconst_5
                        stackManager.add( ValueType.i32, codePosition );
                        break;
                    case 9:  // lconst_0
                    case 10: // lconst_1
                        stackManager.add( ValueType.i64, codePosition );
                        break;
                    case 11: // fconst_0
                    case 12: // fconst_1
                    case 13: // fconst_2
                        stackManager.add( ValueType.f32, codePosition );
                        break;
                    case 14: // dconst_0
                    case 15: // dconst_1
                        stackManager.add( ValueType.f64, codePosition );
                        break;
                    case 16: // bipush
                        stackManager.add( ValueType.i32, codePosition );
                        byteCode.skip(1);
                        break;
                    case 17: // sipush
                        stackManager.add( ValueType.i32, codePosition );
                        byteCode.skip(2);
                        break;
                    case 18: // ldc
                        stackManager.add( null, codePosition );
                        byteCode.skip(1);
                        break;
                    case 19: // ldc_w
                    case 20: // ldc2_w
                        stackManager.add( null, codePosition );
                        byteCode.skip(2);
                        break;
                    case 21: // iload
                        stackManager.add( ValueType.i32, codePosition );
                        localVariables.use( ValueType.i32, byteCode.readUnsignedByte() );
                        break;
                    case 22: // lload
                        stackManager.add( ValueType.i64, codePosition );
                        localVariables.use( ValueType.i64, byteCode.readUnsignedByte() );
                        break;
                    case 23: // fload
                        stackManager.add( ValueType.f32, codePosition );
                        localVariables.use( ValueType.f32, byteCode.readUnsignedByte() );
                        break;
                    case 24: // dload
                        stackManager.add( ValueType.f64, codePosition );
                        localVariables.use( ValueType.f64, byteCode.readUnsignedByte() );
                        break;
                    case 26: // iload_0
                    case 27: // iload_1
                    case 28: // iload_2
                    case 29: // iload_3
                        stackManager.add( ValueType.i32, codePosition );
                        localVariables.use( ValueType.i32, op - 26 );
                        break;
                    case 30: // lload_0
                    case 31: // lload_1
                    case 32: // lload_2
                    case 33: // lload_3
                        stackManager.add( ValueType.i64, codePosition );
                        localVariables.use( ValueType.i64, op - 30 );
                        break;
                    case 34: // fload_0
                    case 35: // fload_1
                    case 36: // fload_2
                    case 37: // fload_3
                        stackManager.add( ValueType.f32, codePosition );
                        localVariables.use( ValueType.f32, op - 34 );
                        break;
                    case 38: // dload_0
                    case 39: // dload_1
                    case 40: // dload_2
                    case 41: // dload_3
                        stackManager.add( ValueType.f64, codePosition );
                        localVariables.use( ValueType.f64, op - 38 );
                        break;
                    case 54: // istore
                        stackManager.remove();
                        localVariables.use( ValueType.i32, byteCode.readUnsignedByte() );
                        break;
                    case 55: // lstore
                        stackManager.remove();
                        localVariables.use( ValueType.i64, byteCode.readUnsignedByte() );
                        break;
                    case 56: // fstore
                        stackManager.remove();
                        localVariables.use( ValueType.f32, byteCode.readUnsignedByte() );
                        break;
                    case 57: // dstore
                        stackManager.remove();
                        localVariables.use( ValueType.f64, byteCode.readUnsignedByte() );
                        break;
                    case 59: // istore_0
                    case 60: // istore_1
                    case 61: // istore_2
                    case 62: // istore_3
                        stackManager.remove();
                        localVariables.use( ValueType.i32, op - 59 );
                        break;
                    case 63: // lstore_0
                    case 64: // lstore_1
                    case 65: // lstore_2
                    case 66: // lstore_3
                        stackManager.remove();
                        localVariables.use( ValueType.i64, op - 63 );
                        break;
                    case 67: // fstore_0
                    case 68: // fstore_1
                    case 69: // fstore_2
                    case 70: // fstore_3
                        stackManager.remove();
                        localVariables.use( ValueType.f32, op - 67 );
                        break;
                    case 71: // dstore_0
                    case 72: // dstore_1
                    case 73: // dstore_2
                    case 74: // dstore_3
                        stackManager.remove();
                        localVariables.use( ValueType.f64, op - 71 );
                        break;
                    case 87: // pop
                    case 88: // pop2
                        stackManager.remove();
                        break;
                    case 25: //aload
                    case 58: // astore
                    case 179: // putstatic
                    case 181: // putfield
                        byteCode.skip(1);
                        break;
                    case 132: // iinc
                        byteCode.skip( 2);
                        break;
                    case 153: // ifeq
                    case 154: // ifne
                    case 155: // iflt
                    case 156: // ifge
                    case 157: // ifgt
                    case 158: // ifle
                    case 159: // if_icmpeq
                    case 160: // if_icmpne
                    case 161: // if_icmplt
                    case 162: // if_icmpge
                    case 163: // if_icmpgt
                    case 164: // if_icmple
                    case 165: // if_acmpeq
                    case 166: // if_acmpne
                        int offset = byteCode.readShort();
                        branchManager.start( JavaBlockOperator.IF, codePosition + 3, offset - 3, lineNumber );
                        break;
                    case 167: // goto
                        offset = byteCode.readShort();
                        branchManager.start( JavaBlockOperator.GOTO, codePosition, offset, lineNumber );
                        break;
                    case 170: // tableswitch
                    case 171: // lookupswitch
                        prepareSwitchCode( byteCode, op == 171, lineNumber );
                        break;
                    case 184: // invokestatic
                        ConstantRef method = (ConstantRef)constantPool.get( byteCode.readUnsignedShort() );
                        String signature = method.getType();
                        ValueType type = getValueType(  signature, signature.indexOf( ')' ) + 1 );
                        if( type != null ) {
                            stackManager.add( type, codePosition );
                        }
                        break;
                }
            }
        } catch( Exception ex ) {
            throw WasmException.create( ex, sourceFile, lineNumber );
        }
    }

    /**
     * Prepare the both switch operation codes.
     * 
     * @param byteCode
     *            the current stream with a position after the operation code
     * @param isLookupSwitch
     *            true, if the operation was a loopupswitch; false, if the operation was a tableswitch
     * @param lineNumber
     *            the current line number
     * @throws IOException
     *             if any I/O error occur
     */
    private void prepareSwitchCode( CodeInputStream byteCode, boolean isLookupSwitch, int lineNumber ) throws IOException {
        int startPosition = byteCode.getCodePosition();
        int padding = startPosition % 4;
        if( padding > 0 ) {
            byteCode.skip( 4 - padding );
        }
        startPosition--;

        int defaultPosition = startPosition + byteCode.readInt();
        int[] keys;
        int[] positions;
        if( isLookupSwitch ) { // lookupswitch
            localVariables.useTempI32();
            int nPairs = byteCode.readInt();
            keys = new int[nPairs];
            positions = new int[nPairs];
            for( int i = 0; i < nPairs; i++ ) {
                keys[i] = byteCode.readInt();
                positions[i] = startPosition + byteCode.readInt();
            }
        } else {
            int low = byteCode.readInt();
            keys = null;
            int count = byteCode.readInt() - low + 1;
            positions = new int[count];
            for( int i = 0; i < count; i++ ) {
                positions[i] = startPosition + byteCode.readInt();
            }
        }
        int switchValuestartPosition = stackManager.getCodePosition( 0 );
        branchManager.startSwitch( switchValuestartPosition, 0, lineNumber, keys, positions, defaultPosition );
    }

    /**
     * Write a chunk of byte code.
     * 
     * @param byteCode
     *            a stream of byte code
     * @param lineNumber
     *            the current line number
     * @param constantPool
     *            the constant pool of the the current class
     * @throws WasmException
     *             if some Java code can't converted
     * @return true, if the last operation was a return
     */
    private boolean writeCodeChunk( CodeInputStream byteCode, int lineNumber, ConstantPool constantPool  ) throws WasmException {
        boolean endWithReturn = false;
        try {
            while( byteCode.available() > 0 ) {
                branchManager.handle( byteCode, this );
                endWithReturn = false;
                int op = byteCode.readUnsignedByte();
                switch( op ) {
                    case 0: // nop
                        break;
                    //TODO case 1: // aconst_null
                    case 2: // iconst_m1
                    case 3: // iconst_0
                    case 4: // iconst_1
                    case 5: // iconst_2
                    case 6: // iconst_3
                    case 7: // iconst_4
                    case 8: // iconst_5
                        writeConstInt( op - 3 );
                        break;
                    case 9:  // lconst_0
                    case 10: // lconst_1
                        writeConstLong( op - 9 );
                        break;
                    case 11: // fconst_0
                    case 12: // fconst_1
                    case 13: // fconst_2
                        writeConstFloat( op - 11 );
                        break;
                    case 14: // dconst_0
                    case 15: // dconst_1
                        writeConstDouble( op - 14 );
                        break;
                    case 16: // bipush
                        writeConstInt( byteCode.readByte() );
                        break;
                    case 17: // sipush
                        writeConstInt( byteCode.readShort() );
                        break;
                    case 18: // ldc
                        writeConst( constantPool.get( byteCode.readUnsignedByte() ) );
                        break;
                    case 19: // ldc_w
                    case 20: // ldc2_w
                        writeConst( constantPool.get( byteCode.readUnsignedShort() ) );
                        break;
                    case 21: // iload
                        writeLoadStore( true, byteCode.readUnsignedByte() );
                        break;
                    case 22: // lload
                        writeLoadStore( true, byteCode.readUnsignedByte() );
                        break;
                    case 23: // fload
                        writeLoadStore( true, byteCode.readUnsignedByte() );
                        break;
                    case 24: // dload
                        writeLoadStore( true, byteCode.readUnsignedByte() );
                        break;
                    //TODO case 25: // aload
                    case 26: // iload_0
                    case 27: // iload_1
                    case 28: // iload_2
                    case 29: // iload_3
                        writeLoadStore( true, op - 26 );
                        break;
                    case 30: // lload_0
                    case 31: // lload_1
                    case 32: // lload_2
                    case 33: // lload_3
                        writeLoadStore( true, op - 30 );
                        break;
                    case 34: // fload_0
                    case 35: // fload_1
                    case 36: // fload_2
                    case 37: // fload_3
                        writeLoadStore( true, op - 34 );
                        break;
                    case 38: // dload_0
                    case 39: // dload_1
                    case 40: // dload_2
                    case 41: // dload_3
                        writeLoadStore( true, op - 38 );
                        break;
                    case 54: // istore
                        writeLoadStore( false, byteCode.readUnsignedByte() );
                        break;
                    case 55: // lstore
                        writeLoadStore( false, byteCode.readUnsignedByte() );
                        break;
                    case 56: // fstore
                        writeLoadStore( false, byteCode.readUnsignedByte() );
                        break;
                    case 57: // dstore
                        writeLoadStore( false, byteCode.readUnsignedByte() );
                        break;
                    //TODO case 58: // astore
                    case 59: // istore_0
                    case 60: // istore_1
                    case 61: // istore_2
                    case 62: // istore_3
                        writeLoadStore( false, op - 59 );
                        break;
                    case 63: // lstore_0
                    case 64: // lstore_1
                    case 65: // lstore_2
                    case 66: // lstore_3
                        writeLoadStore( false, op - 63 );
                        break;
                    case 67: // fstore_0
                    case 68: // fstore_1
                    case 69: // fstore_2
                    case 70: // fstore_3
                        writeLoadStore( false, op - 67 );
                        break;
                    case 71: // dstore_0
                    case 72: // dstore_1
                    case 73: // dstore_2
                    case 74: // dstore_3
                        writeLoadStore( false, op - 71 );
                        break;
                    case 87: // pop
                    case 88: // pop2
                        writeBlockCode( WasmBlockOperator.DROP, null );
                        break;
                    case 89: // dup: duplicate the value on top of the stack
                    case 90: // dup_x1
                    case 91: // dup_x2
                    case 92: // dup2
                    case 93: // dup2_x1
                    case 94: // dup2_x2
                    case 95: // swap
                        // can be do with functions with more as one return value in future WASM standard
                        throw new WasmException( "Stack duplicate is not supported in current WASM. try to save immediate values in a local variable: " + op, sourceFile, lineNumber );
                    case 96: // iadd
                        writeNumericOperator( NumericOperator.add, ValueType.i32);
                        break;
                    case 97: // ladd
                        writeNumericOperator( NumericOperator.add, ValueType.i64 );
                        break;
                    case 98: // fadd
                        writeNumericOperator( NumericOperator.add, ValueType.f32 );
                        break;
                    case 99: // dadd
                        writeNumericOperator( NumericOperator.add, ValueType.f64 );
                        break;
                    case 100: // isub
                        writeNumericOperator( NumericOperator.sub, ValueType.i32 );
                        break;
                    case 101: // lsub
                        writeNumericOperator( NumericOperator.sub, ValueType.i64 );
                        break;
                    case 102: // fsub
                        writeNumericOperator( NumericOperator.sub, ValueType.f32 );
                        break;
                    case 103: // dsub
                        writeNumericOperator( NumericOperator.sub, ValueType.f64 );
                        break;
                    case 104: // imul;
                        writeNumericOperator( NumericOperator.mul, ValueType.i32 );
                        break;
                    case 105: // lmul
                        writeNumericOperator( NumericOperator.mul, ValueType.i64 );
                        break;
                    case 106: // fmul
                        writeNumericOperator( NumericOperator.mul, ValueType.f32 );
                        break;
                    case 107: // dmul
                        writeNumericOperator( NumericOperator.mul, ValueType.f64 );
                        break;
                    case 108: // idiv
                        writeNumericOperator( NumericOperator.div, ValueType.i32 );
                        break;
                    case 109: // ldiv
                        writeNumericOperator( NumericOperator.div, ValueType.i64 );
                        break;
                    case 110: // fdiv
                        writeNumericOperator( NumericOperator.div, ValueType.f32 );
                        break;
                    case 111: // ddiv
                        writeNumericOperator( NumericOperator.div, ValueType.f64 );
                        break;
                    case 112: // irem
                        writeNumericOperator( NumericOperator.rem, ValueType.i32 );
                        break;
                    case 113: // lrem
                        writeNumericOperator( NumericOperator.rem, ValueType.i64 );
                        break;
                    case 114: // frem
                    case 115: // drem
                        //TODO can be implemented with a helper function like: (a - (long)(a / b) * (double)b) 
                        throw new WasmException( "Modulo/Remainder for floating numbers is not supported in WASM. Use int or long data types." + op, sourceFile, lineNumber );
                    case 116: // ineg
                        writeConstInt( -1 );
                        writeNumericOperator( NumericOperator.mul, ValueType.i32 );
                        break;
                    case 117: // lneg
                        writeConstLong( -1 );
                        writeNumericOperator( NumericOperator.mul, ValueType.i64 );
                        break;
                    case 118: // fneg
                        writeNumericOperator( NumericOperator.neg, ValueType.f32 );
                        break;
                    case 119: // dneg
                        writeNumericOperator( NumericOperator.neg, ValueType.f64 );
                        break;
                    case 120: // ishl
                        writeNumericOperator( NumericOperator.shl, ValueType.i32 );
                        break;
                    case 121: // lshl
                        writeCast( ValueTypeConvertion.i2l ); // the shift parameter must be of type long!!!
                        writeNumericOperator( NumericOperator.shl, ValueType.i64 );
                        break;
                    case 122: // ishr
                        writeNumericOperator( NumericOperator.shr_s, ValueType.i32 );
                        break;
                    case 123: // lshr
                        writeCast( ValueTypeConvertion.i2l ); // the shift parameter must be of type long!!!
                        writeNumericOperator( NumericOperator.shr_s, ValueType.i64 );
                        break;
                    case 124: // iushr
                        writeNumericOperator( NumericOperator.shr_u, ValueType.i32 );
                        break;
                    case 125: // lushr
                        writeCast( ValueTypeConvertion.i2l ); // the shift parameter must be of type long!!!
                        writeNumericOperator( NumericOperator.shr_u, ValueType.i64 );
                        break;
                    case 126: // iand
                        writeNumericOperator( NumericOperator.and, ValueType.i32 );
                        break;
                    case 127: // land
                        writeNumericOperator( NumericOperator.and, ValueType.i64 );
                        break;
                    case 128: // ior
                        writeNumericOperator( NumericOperator.or, ValueType.i32 );
                        break;
                    case 129: // lor
                        writeNumericOperator( NumericOperator.or, ValueType.i64 );
                        break;
                    case 130: // ixor
                        writeNumericOperator( NumericOperator.xor, ValueType.i32 );
                        break;
                    case 131: // lxor
                        writeNumericOperator( NumericOperator.xor, ValueType.i64 );
                        break;
                    case 132: // iinc
                        int idx = byteCode.readUnsignedByte();
                        writeLoadStore( true, idx );
                        writeConstInt( byteCode.readUnsignedByte() );
                        writeNumericOperator( NumericOperator.add, ValueType.i32);
                        writeLoadStore( false, idx );
                        break;
                    case 133: // i2l
                        writeCast( ValueTypeConvertion.i2l );
                        break;
                    case 134: // i2f
                        writeCast( ValueTypeConvertion.i2f );
                        break;
                    case 135: // i2d
                        writeCast( ValueTypeConvertion.i2d );
                        break;
                    case 136: // l2i
                        writeCast( ValueTypeConvertion.l2i );
                        break;
                    case 137: // l2f
                        writeCast( ValueTypeConvertion.l2f );
                        break;
                    case 138: // l2d
                        writeCast( ValueTypeConvertion.l2d );
                        break;
                    case 139: // f2i
                        writeCast( ValueTypeConvertion.f2i );
                        break;
                    case 140: // f2l
                        writeCast( ValueTypeConvertion.f2l );
                        break;
                    case 141: // f2d
                        writeCast( ValueTypeConvertion.f2d );
                        break;
                    case 142: // d2i
                        writeCast( ValueTypeConvertion.d2i );
                        break;
                    case 143: // d2l
                        writeCast( ValueTypeConvertion.d2l );
                        break;
                    case 144: // d2f
                        writeCast( ValueTypeConvertion.d2f );
                        break;
                    case 145: // i2b
                        writeConstInt( 24 );
                        writeNumericOperator( NumericOperator.shl, ValueType.i32 );
                        writeConstInt( 24 );
                        writeNumericOperator( NumericOperator.shr_s, ValueType.i32 );
                        break;
                    case 146: // i2c
                        writeConstInt( 0xFFFF );
                        writeNumericOperator( NumericOperator.and, ValueType.i32 );
                        break;
                    case 147: // i2s
                        writeConstInt( 16 );
                        writeNumericOperator( NumericOperator.shl, ValueType.i32 );
                        writeConstInt( 16 );
                        writeNumericOperator( NumericOperator.shr_s, ValueType.i32 );
                        break;
                    case 148: // lcmp
                        opCompare( ValueType.i64, byteCode );
                        break;
                    case 149: // fcmpl
                    case 150: // fcmpg
                        opCompare( ValueType.f32, byteCode );
                        break;
                    case 151: // dcmpl
                    case 152: // dcmpg
                        opCompare( ValueType.f64, byteCode );
                        break;
                    case 153: // ifeq
                        opIfCondition( NumericOperator.ne, byteCode );
                        break;
                    case 154: // ifne
                        opIfCondition( NumericOperator.eq, byteCode );
                        break;
                    case 155: // iflt
                        opIfCondition( NumericOperator.ge_s, byteCode );
                        break;
                    case 156: // ifge
                        opIfCondition( NumericOperator.lt_s, byteCode );
                        break;
                    case 157: // ifgt
                        opIfCondition( NumericOperator.le_s, byteCode );
                        break;
                    case 158: // ifle
                        opIfCondition( NumericOperator.gt, byteCode );
                        break;
                    case 159: // if_icmpeq
                        opIfCompareCondition( NumericOperator.ne, byteCode );
                        break;
                    case 160: // if_icmpne
                        opIfCompareCondition( NumericOperator.eq, byteCode );
                        break;
                    case 161: // if_icmplt
                        opIfCompareCondition( NumericOperator.ge_s, byteCode );
                        break;
                    case 162: // if_icmpge
                        opIfCompareCondition( NumericOperator.lt_s, byteCode );
                        break;
                    case 163: // if_icmpgt
                        opIfCompareCondition( NumericOperator.le_s, byteCode );
                        break;
                    case 164: // if_icmple
                        opIfCompareCondition( NumericOperator.gt, byteCode );
                        break;
                    //TODO case 165: // if_acmpeq
                    //TODO case 166: // if_acmpne
                    case 167: // goto
                        byteCode.skip(2); // handle in the branch manager
                        break;
                    case 170: // tableswitch
                    case 171: // lookupswitch
                        writeSwitchCode( byteCode, op == 171 );
                        break;
                    case 172: // ireturn
                    case 173: // lreturn
                    case 174: // freturn
                    case 175: // dreturn
                    case 177: // return void
                        writeBlockCode( WasmBlockOperator.RETURN, null );
                        endWithReturn = true;
                        break;
                    case 184: // invokestatic
                        idx = byteCode.readUnsignedShort();
                        ConstantRef method = (ConstantRef)constantPool.get( idx );
                        writeFunctionCall( method.getConstantClass().getName() + '.' + method.getName() + method.getType() );
                        break;
                    default:
                        throw new WasmException( "Unimplemented Java byte code operation: " + op, sourceFile, lineNumber );
                }
            }
        } catch( WasmException ex ) {
            throw ex;
        } catch( Exception ex ) {
            throw WasmException.create( ex, sourceFile, lineNumber );
        }
        return endWithReturn;
    }

    /**
     * Write the both switch operation codes
     * 
     * @param byteCode
     *            the current stream with a position after the operation code
     * @param isLookupSwitch
     *            true, if the operation was a loopupswitch; false, if the operation was a tableswitch
     * @throws IOException
     *             if any I/O error occur
     */
    private void writeSwitchCode( CodeInputStream byteCode, boolean isLookupSwitch ) throws IOException {
        int startPosition = byteCode.getCodePosition();
        int padding = startPosition % 4;
        if( padding > 0 ) {
            byteCode.skip( 4 - padding );
        }
        startPosition--;

        int defaultPosition = byteCode.readInt();
        if( isLookupSwitch ) { // lookupswitch
            int count = byteCode.readInt();
            int[] keys = new int[count];
            int[] positions = new int[count];
            for( int i = 0; i < count; i++ ) {
                keys[i] = byteCode.readInt();
                positions[i] = byteCode.readInt();
            }
            int tempI32 = localVariables.getTempI32();
            int block = 0;
            int defaultBlock = -1;
            int currentPos = -1;
            writeLoadStore( false, tempI32 );
            do {
                int nextPos = findNext( currentPos, positions );
                if( nextPos == currentPos ) {
                    break;
                }
                currentPos = nextPos;
                if( defaultBlock < 0 ) {
                    if( defaultPosition <= currentPos ) {
                        defaultBlock = block;
                        if( defaultPosition < currentPos ) {
                            block++;
                        }
                    }
                }
                for( int i = 0; i < positions.length; i++ ) {
                    if( positions[i] == currentPos ) {
                        writeLoadStore( true, tempI32 );
                        writeConstInt( keys[i] );
                        writeNumericOperator( NumericOperator.eq, ValueType.i32 );
                        writeBlockCode( WasmBlockOperator.BR_IF, block );
                    }
                }
                block++;
            } while( true );
            if( defaultBlock < 0 ) {
                defaultBlock = block;
            }
            writeBlockCode( WasmBlockOperator.BR, defaultBlock );
        } else {
            int low = byteCode.readInt();
            int count = byteCode.readInt() - low + 1;
            for( int i = 0; i < count; i++ ) {
                byteCode.readInt();
            }
            if( low != 0 ) { // the br_table starts ever with the value 0. That we need to subtract the start value if it different
                writeConstInt( low );
                writeNumericOperator( NumericOperator.sub, ValueType.i32 );
            }
        }
    }

    /**
     * Find the next higher value.
     * 
     * @param current
     *            the current value
     * @param values
     *            the unordered list of values
     * @return the next value or current value if not found.
     */
    private static int findNext( int current, int[] values ) {
        boolean find = false;
        int next = Integer.MAX_VALUE;
        for( int val : values ) {
            if( val > current && val <= next ) {
                next = val;
                find = true;
            }
        }
        return find ? next : current;
    }

    /**
     * Handle the if<condition> of the Java byte code. This Java instruction compare the first stack value with value 0.
     * Important: In Java the condition for the jump to the else block is saved. In WebAssembler we need to use
     * condition for the if block. The caller of the method must already negate this
     * 
     * @param numOp
     *            The condition for the if block.
     * @param byteCode
     *            current byte code stream to read the taget offset.
     * @throws IOException
     *             if any I/O errors occur.
     */
    private void opIfCondition( NumericOperator numOp, CodeInputStream byteCode ) throws IOException {
        byteCode.skip(2);
        writeConstInt( 0 );
        writeNumericOperator( numOp, ValueType.i32 );
    }

    /**
     * Handle the if<condition> of the Java byte code. This Java instruction compare the first stack value with value 0.
     * Important: In Java the condition for the jump to the else block is saved. In WebAssembler we need to use
     * condition for the if block. The caller of the method must already negate this
     * 
     * @param numOp
     *            The condition for the if block.
     * @param byteCode
     *            current byte code stream to read the target offset.
     * @throws IOException
     *             if any I/O errors occur.
     */
    private void opIfCompareCondition( NumericOperator numOp, CodeInputStream byteCode ) throws IOException {
        byteCode.skip(2);
        writeNumericOperator( numOp, ValueType.i32 );
    }

    /**
     * Handle the different compare operator. The compare operator returns the integer values -1, 0 or 1. There is no
     * equivalent in WebAssembly. That we need to read the next operation to find an equivalent.
     * 
     * @param valueType
     *            the value type of the compared
     * @param byteCode
     *            current byte code stream to read the next operation.
     * @throws IOException
     *             if any I/O errors occur.
     */
    private void opCompare( ValueType valueType, CodeInputStream byteCode ) throws IOException {
        NumericOperator numOp;
        int nextOp = byteCode.read();
        switch( nextOp ){
            case 153: // ifeq
                numOp = NumericOperator.ne;
                break;
            case 154: // ifne
                numOp = NumericOperator.eq;
                break;
            case 155: // iflt
                numOp = NumericOperator.gt;
                break;
            case 156: // ifge
                numOp = NumericOperator.le_s;
                break;
            case 157: // ifgt
                numOp = NumericOperator.lt_s;
                break;
            case 158: // ifle
                numOp = NumericOperator.ge_s;
                break;
            default:
                throw new WasmException( "Unexpected compare sub operation: " + nextOp, null, -1 );
        }
        byteCode.skip(2);
        writeNumericOperator( numOp, valueType );
    }

    /**
     * Write a constant value.
     * 
     * @param value
     *            the value
     * @throws IOException
     *             if any I/O error occur
     * @throws WasmException
     *             if the value type is not supported
     */
    private void writeConst( Object value ) throws IOException, WasmException {
        Class<?> clazz = value.getClass();
        if( clazz == Integer.class ) {
            writeConstInt( ((Integer)value).intValue() );
        } else if( clazz == Long.class ) {
            writeConstLong( ((Long)value).longValue() );
        } else if( clazz == Float.class ) {
            writeConstFloat( ((Float)value).floatValue() );
        } else if( clazz == Double.class ) {
            writeConstDouble( ((Double)value).doubleValue() );
        } else {
            throw new WasmException( "Not supported constant type: " + clazz, sourceFile, -1 );
        }
    }

    /**
     * Write a constant integer value
     * 
     * @param value
     *            the value
     * @throws IOException
     *             if any I/O error occur
     */
    protected abstract void writeConstInt( int value ) throws IOException;

    /**
     * Write a constant long value
     * 
     * @param value
     *            the value
     * @throws IOException
     *             if any I/O error occur
     */
    protected abstract void writeConstLong( long value ) throws IOException;

    /**
     * Write a constant float value
     * 
     * @param value
     *            the value
     * @throws IOException
     *             if any I/O error occur
     */
    protected abstract void writeConstFloat( float value ) throws IOException;

    /**
     * Write a constant double value
     * 
     * @param value
     *            the value
     * @throws IOException
     *             if any I/O error occur
     */
    protected abstract void writeConstDouble( double value ) throws IOException;

    /**
     * Write or Load a local variable.
     * 
     * @param load
     *            true: if load
     * @param idx
     *            the memory/slot idx of the variable
     * @throws WasmException
     *             occur a if a variable was used for a different type
     * @throws IOException
     *             if any I/O error occur
     */
    private void writeLoadStore( boolean load, @Nonnegative int idx ) throws WasmException, IOException {
        idx = localVariables.get( idx ); // translate slot index to position index
        if( load ) {
            writeLoad( idx );
        } else {
            writeStore( idx );
        }
    }

    /**
     * Write a variable load.
     * 
     * @param idx
     *            the index of the parameter variable
     * @throws IOException
     *             if any I/O error occur
     */
    protected abstract void writeLoad( int idx ) throws IOException;

    /**
     * Write a variable store.
     * 
     * @param idx
     *            the index of the parameter variable
     * @throws IOException
     *             if any I/O error occur
     */
    protected abstract void writeStore( int idx ) throws IOException;

    /**
     * Write a add operator
     * 
     * @param numOp
     *            the numeric operation
     * @param valueType
     *            the type of the parameters
     * 
     * @throws IOException
     *             if any I/O error occur
     */
    protected abstract void writeNumericOperator( NumericOperator numOp, @Nullable ValueType valueType ) throws IOException;

    /**
     * Cast a value from one type to another
     * 
     * @param cast
     *            the operator
     * @throws IOException
     *             if any I/O error occur
     */
    protected abstract void writeCast( ValueTypeConvertion cast ) throws IOException;

    /**
     * Write a call to a function.
     * 
     * @param name
     *            the full qualified method name
     * @throws IOException
     *             if any I/O error occur
     */
    protected abstract void writeFunctionCall( String name ) throws IOException;

    /**
     * Write a block/branch code
     * 
     * @param op
     *            the operation
     * @param data
     *            extra data depending of the operator
     * @throws IOException
     *             if any I/O error occur
     */
    protected abstract void writeBlockCode( @Nonnull WasmBlockOperator op, @Nullable Object data ) throws IOException;
}
