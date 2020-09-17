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
package de.inetsoftware.jwebassembly.text;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import de.inetsoftware.jwebassembly.WasmException;
import de.inetsoftware.jwebassembly.module.FunctionName;
import de.inetsoftware.jwebassembly.module.ModuleWriter;
import de.inetsoftware.jwebassembly.module.TypeManager.StructType;
import de.inetsoftware.jwebassembly.module.ValueTypeConvertion;
import de.inetsoftware.jwebassembly.module.WasmOptions;
import de.inetsoftware.jwebassembly.module.WasmTarget;
import de.inetsoftware.jwebassembly.wasm.AnyType;
import de.inetsoftware.jwebassembly.wasm.ArrayOperator;
import de.inetsoftware.jwebassembly.wasm.ArrayType;
import de.inetsoftware.jwebassembly.wasm.FunctionType;
import de.inetsoftware.jwebassembly.wasm.MemoryOperator;
import de.inetsoftware.jwebassembly.wasm.NamedStorageType;
import de.inetsoftware.jwebassembly.wasm.NumericOperator;
import de.inetsoftware.jwebassembly.wasm.StructOperator;
import de.inetsoftware.jwebassembly.wasm.ValueType;
import de.inetsoftware.jwebassembly.wasm.VariableOperator;
import de.inetsoftware.jwebassembly.wasm.WasmBlockOperator;

/**
 * Module Writer for text format with S-expressions.
 * 
 * @author Volker Berlin
 *
 */
public class TextModuleWriter extends ModuleWriter {

    private final WasmTarget               target;

    private final StringBuilder            output           = new StringBuilder();

    private final ArrayList<String>        methodParamNames = new ArrayList<>();

    private final StringBuilder            typeOutput       = new StringBuilder();

    private final ArrayList<String>        types            = new ArrayList<>();

    private StringBuilder                  methodOutput;

    private final StringBuilder            imports          = new StringBuilder();

    private final Map<String, Function>    functions        = new LinkedHashMap<>();

    private final Map<String, Function>    abstracts        = new HashMap<>();

    private final Set<String>              functionNames    = new HashSet<>();

    private int                            inset;

    private boolean                        isImport;

    private final HashMap<String, AnyType> globals          = new HashMap<>();

    private boolean                        useExceptions;

    private boolean                        callIndirect;

    /**
     * Create a new instance.
     * 
     * @param target
     *            target for the result
     * @param options
     *            compiler properties
     * @throws IOException
     *             if any I/O error occur
     */
    public TextModuleWriter( WasmTarget target, WasmOptions options ) throws IOException {
        super( options );
        this.target = target;
        inset++;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() throws IOException {
        Appendable textOutput = target.getTextOutput();
        textOutput.append( "(module" );

        for( int i = 0; i < types.size(); i++ ) {
            newline( textOutput );
            textOutput.append( "(type $t" ).append( Integer.toString( i ) ).append( " (func" ).append( types.get( i ) ).append( "))" );
        }

        textOutput.append( imports );

        for( Entry<String, AnyType> entry : globals.entrySet() ) {
            textOutput.append( "\n  " );
            textOutput.append( "(global $" ).append( entry.getKey() ).append( " (mut " );
            writeTypeName( textOutput, entry.getValue() );
            textOutput.append( ')' );
            writeDefaultValue( textOutput, entry.getValue() );
            textOutput.append( ')' );
        }

        textOutput.append( output );

        for( Function func : functions.values() ) {
            textOutput.append( func.output );
        }

        if( callIndirect ) {
            int count = functions.size();
            String countStr = Integer.toString( count );
            newline( textOutput );
            textOutput.append( "(table $functions " ).append( countStr ).append( " funcref)" );
            newline( textOutput );
            textOutput.append( "(elem (i32.const 0) " );
            for( int i = 0; i < count; i++ ) {
                textOutput.append( Integer.toString( i ) ).append( ' ' );
            }
            textOutput.append( ')' );
        }

        // table for string constants
        int stringCount = options.strings.size();
        if( stringCount > 0 ) {
            if( !callIndirect ) {
                // we need to create a placeholder table with index 0 if not exists
                newline( textOutput );
                textOutput.append( "(table $functions 0 funcref)" );
            }
            newline( textOutput );
            textOutput.append( "(table $strings " ).append( Integer.toString( stringCount ) ).append( " externref)" );
        }

        // table with classes
        int typeCount = options.types.size();
        if( typeCount > 0 ) {
            newline( textOutput );
            textOutput.append( "(table $classes " ).append( Integer.toString( typeCount ) ).append( " externref)" );
        }

        int dataSize = dataStream.size();
        if( dataSize > 0 ) {
            int pages = (dataSize + 0xFFFF) / 0x10000;
            newline( textOutput );
            String pagesStr = Integer.toString( pages );
            textOutput.append( "(memory " ).append( pagesStr ).append( ')' );
            newline( textOutput );
            textOutput.append( "(data (i32.const 0) \"" );
            byte[] data = dataStream.toByteArray();
            for( byte b : data ) {
                if( b >= ' ' && b < 0x7F && b != '\"' && b != '\\' ) {
                    textOutput.append( (char)b );
                } else {
                    textOutput.append( '\\' ).append( Character.forDigit( (b >> 4) & 0xF, 16 ) ).append( Character.forDigit( b & 0xF, 16 ) );
                }
            }
            textOutput.append( "\")" );
        }

        inset--;
        newline( textOutput );
        textOutput.append( ')' );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected int writeStructType( StructType type ) throws IOException {
        type.writeToStream( dataStream, (funcName) -> getFunction( funcName ).id, options );

        if( !options.useGC() ) {
            return ValueType.externref.getCode();
        }

        int oldInset = inset;
        inset = 1;
        newline( output );
        String typeName = normalizeName( type.getName() );
        output.append( "(type $" ).append( typeName ).append( " (struct" );
        inset++;
        for( NamedStorageType field : type.getFields() ) {
            newline( output );
            output.append( "(field" );
            if( options.debugNames() && field.getName() != null ) {
                output.append( " $" ).append( typeName ).append(  '.' ).append( field.getName() );
            }
            output.append( " (mut " );
            writeTypeName( output, field.getType() );
            output.append( "))" );
        }
        inset--;
        newline( output );
        output.append( "))" );
        inset = oldInset;
        return 0;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void writeException() throws IOException {
        if( !useExceptions ) {
            useExceptions = true;
            int oldInset = inset;
            inset = 1;
            newline( output );
            output.append( "(event (param externref))" );
            inset = oldInset;

            options.setCatchType( types.size() );
            types.add( options.getCatchType().toString() );
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void prepareFinish() {
        // nothing
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void prepareImport( FunctionName name, String importModule, String importName ) throws IOException {
        if( importName != null ) {
            methodOutput = imports;
            newline( methodOutput );
            methodOutput.append( "(import \"" ).append( importModule ).append( "\" \"" ).append( importName ).append( "\" (func $" ).append( normalizeName( name ) );
            isImport = true;
        }
    }

    /**
     * Normalize the function name for the text format
     * 
     * @param name
     *            the name
     * @return the normalized name
     */
    @Nonnull
    private String normalizeName( FunctionName name ) {
        Function function = getFunction( name );
        if( function.name == null ) {
            String base;
            String str = base = normalizeName( name.fullName );
            for( int i = 1; functionNames.contains( str ); i++ ) {
                str = base + '.' + i;
            }
            functionNames.add( str );
            function.name = str;
        }
        return function.name;
    }

    /**
     * Normalize the function name for the text format of IDs.
     * https://webassembly.github.io/spec/core/text/values.html#text-id
     * 
     * @param name
     *            the name
     * @return the normalized name
     */
    @Nonnull
    private String normalizeName( String name ) {
        return name.replace( '[', '/' );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void writeExport( FunctionName name, String exportName ) throws IOException {
        newline( output );
        output.append( "(export \"" ).append( exportName ).append( "\" (func $" ).append( normalizeName( name ) ).append( "))" );
    }

    /**
     * Write the name of a type.
     * 
     * @param output
     *            the target
     * @param type
     *            the type
     * @throws IOException
     *             if any I/O error occur
     */
    private void writeTypeName( Appendable output, AnyType type ) throws IOException {
        if( !type.isRefType() ) {
            output.append( type.toString() );
        } else if( options.useGC() ) {
            //output.append( ValueType.eqref.toString() );
            output.append( "(ref null " ).append( normalizeName( type.toString() ) ).append( ')' );
        } else {
            output.append( ValueType.externref.toString() );
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void writeMethodParamStart( @Nonnull FunctionName name, FunctionType funcType ) throws IOException {
        switch( funcType ) {
            case Abstract:
                abstracts.put( name.signatureName, new Function() );
                break;
            case Start:
                newline( imports );
                imports.append( "(start $" ).append( normalizeName( name ) ).append( ")" );
                break;
        }
        typeOutput.setLength( 0 );
        methodParamNames.clear();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void writeMethodParam( String kind, AnyType valueType, @Nullable String name ) throws IOException {
        if( kind != "local" ) {
            typeOutput.append( '(' ).append( kind ).append( ' ' );
            writeTypeName( typeOutput, valueType );
            typeOutput.append( ')' );
        }
        if( methodOutput == null ) {
            return;
        }
        methodOutput.append( '(' ).append( kind );
        if( options.debugNames() ) { 
            if( name != null ) {
                methodOutput.append( " $" ).append( name );
            }
            if( kind != "result" ) {
                methodParamNames.add( name );
            }
        }
        methodOutput.append( ' ' );
        writeTypeName( methodOutput, valueType );
        methodOutput.append( ')' );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void writeMethodParamFinish( @Nonnull FunctionName name ) throws IOException {
        String typeStr = typeOutput.toString();
        int idx = types.indexOf( typeStr );
        if( idx < 0 ) {
            idx = types.size();
            types.add( typeStr );
        }
        getFunction( name ).typeId = idx;

        if( isImport ) {
            isImport = false;
            methodOutput.append( "))" );
            methodOutput = null;
        }
    }

    private Function getFunction( FunctionName name ) {
        String signatureName = name.signatureName;
        Function func = functions.get( signatureName );
        if( func == null ) {
            func = abstracts.get( signatureName );
            if( func == null ) {
                func = new Function();
                func.id = functions.size();
                functions.put( name.signatureName, func );
            }
        }
        return func;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void writeMethodStart( FunctionName name, String sourceFile ) throws IOException {
        methodOutput = getFunction( name ).output;

        newline( methodOutput );
        methodOutput.append( "(func $" );
        methodOutput.append( normalizeName( name ) );
        inset++;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void markSourceLine( int javaSourceLine ) {
        // nothing
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void writeMethodFinish() throws IOException {
        inset--;
        newline( methodOutput );
        methodOutput.append( ')' );
        methodOutput = null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void writeConst( Number value, ValueType valueType ) throws IOException {
        newline( methodOutput );
        methodOutput.append( valueType ).append( ".const " );
        switch( valueType ) {
            case f32:
                float floatValue = value.floatValue();
                if( floatValue == Double.POSITIVE_INFINITY ) {
                    methodOutput.append( "inf" );
                } else if( floatValue == Double.NEGATIVE_INFINITY ) { 
                    methodOutput.append( "-inf" );
                } else {
                    methodOutput.append( Float.toHexString( floatValue ).toLowerCase() ).append( " ;;" ).append( value );
                }
                break;
            case f64:
                double doubleValue = value.doubleValue();
                if( doubleValue == Double.POSITIVE_INFINITY ) {
                    methodOutput.append( "inf" );
                } else if( doubleValue == Double.NEGATIVE_INFINITY ) { 
                    methodOutput.append( "-inf" );
                } else {
                    methodOutput.append( Double.toHexString( doubleValue ).toLowerCase() ).append( " ;;" ).append( value );
                }
                break;
            default:
                methodOutput.append( value );
                break;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void writeLocal( VariableOperator op, int idx ) throws IOException {
        newline( methodOutput );
        methodOutput.append( "local." ).append( op ).append( ' ' );
        String name = idx < methodParamNames.size() ? methodParamNames.get( idx ) : null;
        if( name == null ) {
            methodOutput.append( Integer.toString( idx ) );
        } else {
            methodOutput.append( '$' ).append( name );
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void writeGlobalAccess( boolean load, FunctionName name, AnyType type ) throws IOException {
        String fullName = normalizeName( name.fullName );
        if( !globals.containsKey( fullName ) ) {
            // declare global variable if not already declared.
            globals.put( fullName, type );
        }
        newline( methodOutput );
        methodOutput.append( load ? "global.get $" : "global.set $" ).append( fullName );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void writeTable( boolean load, @Nonnegative int idx ) throws IOException {
        newline( methodOutput );
        methodOutput.append( load ? "table.get " : "table.set " ).append( idx );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void writeDefaultValue( AnyType type ) throws IOException {
        newline( methodOutput );
        writeDefaultValue( methodOutput, type );
    }

    /**
     * Write the default/initial value for type.
     * 
     * @param output
     *            the target
     * @param type
     *            the type
     * @throws IOException
     *             if an I/O error occurs.
     */
    private void writeDefaultValue( Appendable output, AnyType type ) throws IOException {
        if( type instanceof ValueType ) {
            ValueType valueType = (ValueType)type;
            switch( valueType ) {
                case i32:
                case i64:
                case f32:
                case f64:
                    output.append( type.toString() ).append( ".const 0" );
                    break;
                case i8:
                case i16:
                    writeDefaultValue( output, ValueType.i32 );
                    break;
                case externref:
                    output.append( "ref.null extern" );
                    break;
                default:
                    throw new WasmException( "Not supported storage type: " + type, -1 );
            }
        } else {
            output.append( "ref.null " ).append( options.useGC() ? normalizeName( type.toString() ) : "extern" );
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void writeNumericOperator( NumericOperator numOp, @Nullable ValueType valueType ) throws IOException {
        boolean negate = false;
        String op = valueType.toString() + '.' + numOp.toString();
        switch( valueType ) {
            case i32:
            case i64:
                switch( numOp ) {
                    case div:
                    case rem:
                    case gt:
                    case lt:
                    case le:
                    case ge:
                        op += "_s";
                        break;
                    case ifnonnull:
                        op = "ref.is_null";
                        negate = true;
                        break;
                    case ifnull:
                        op = "ref.is_null";
                        break;
                    case ref_ne:
                        op = options.useGC() ? "ref.eq" : null;
                        negate = true;
                        break;
                    case ref_eq:
                        op = options.useGC() ? "ref.eq" : null;
                        break;
                    default:
                }
                break;
            default:
        }
        if( op != null ) {
            newline( methodOutput );
            methodOutput.append( op );
        } else {
            writeFunctionCall( options.ref_eq, null );
        }
        if( negate ) {
            writeNumericOperator( NumericOperator.eqz, ValueType.i32 );
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void writeCast( ValueTypeConvertion cast ) throws IOException {
        String op;
        switch( cast ) {
            case i2l:
                op = "i64.extend_i32_s";
                break;
            case i2f:
                op = "f32.convert_i32_s";
                break;
            case i2d:
                op = "f64.convert_i32_s";
                break;
            case l2i:
                op = "i32.wrap_i64";
                break;
            case l2f:
                op = "f32.convert_i64_s";
                break;
            case l2d:
                op = "f64.convert_i64_s";
                break;
            case f2i:
                op = "i32.trunc_sat_f32_s";
                break;
            case f2l:
                op = "i64.trunc_sat_f32_s";
                break;
            case f2d:
                op = "f64.promote_f32";
                break;
            case d2i:
                op = "i32.trunc_sat_f64_s";
                break;
            case d2l:
                op = "i64.trunc_sat_f64_s";
                break;
            case d2f:
                op = "f32.demote_f64";
                break;
            case i2b:
                op = "i32.extend8_s";
                break;
            case i2s:
                op = "i32.extend16_s";
                break;
            case f2i_re:
                op = "i32.reinterpret_f32";
                break;
            case i2f_re:
                op = "f32.reinterpret_i32";
                break;
            case d2l_re:
                op = "i64.reinterpret_f64";
                break;
            case l2d_re:
                op = "f64.reinterpret_i64";
                break;
            default:
                throw new Error( "Unknown cast/type conversion: " + cast );
        }
        newline( methodOutput );
        methodOutput.append( op );
    }

    /**
     * Add a newline with the insets.
     * 
     * @param output
     *            the target
     * @throws IOException
     *             if any I/O error occur
     */
    private void newline( Appendable output ) throws IOException {
        output.append( '\n' );
        for( int i = 0; i < inset; i++ ) {
            output.append( ' ' );
            output.append( ' ' );
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void writeFunctionCall( FunctionName name, String comment ) throws IOException {
        newline( methodOutput );
        methodOutput.append( "call $" ).append( normalizeName( name ) );
        if( comment != null ) {
            methodOutput.append( "  ;; \"" ).append( comment.replace( "\n", "\\n" ).replace( "\r", "\\r" ) ).append( '"' );
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void writeVirtualFunctionCall( FunctionName name, AnyType type ) throws IOException {
        callIndirect = true;

        newline( methodOutput );
        methodOutput.append( "call_indirect (type $t" ).append( getFunction( name ).typeId ).append( ")  ;; " ).append( name.signatureName );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void writeBlockCode( @Nonnull WasmBlockOperator op, @Nullable Object data ) throws IOException {
        CharSequence name;
        int insetAfter = 0;
        switch( op ) {
            case RETURN:
                name = "return";
                break;
            case IF:
                name = blockWithResult( "if", (AnyType)data );
                insetAfter++;
                break;
            case ELSE:
                inset--;
                name = "else";
                insetAfter++;
                break;
            case END:
                inset--;
                name = "end";
                break;
            case DROP:
                name = "drop";
                break;
            case BLOCK:
                name = blockWithResult( "block", (AnyType)data );
                insetAfter++;
                break;
            case BR:
                name = "br " + data;
                break;
            case BR_IF:
                name = "br_if " + data;
                break;
            case BR_TABLE:
                StringBuilder builder = new StringBuilder( "br_table");
                for( int i : (int[])data ) {
                    builder.append( ' ' ).append( i );
                }
                name = builder.toString();
                break;
            case LOOP:
                name = "loop";
                insetAfter++;
                break;
            case UNREACHABLE:
                name = "unreachable";
                break;
            case TRY:
                name = options.useEH() ? "try" : "block";
                insetAfter++;
                break;
            case CATCH:
                inset--;
                name = options.useEH() ? "catch" : "br 0";
                insetAfter++;
                break;
            case THROW:
                name = options.useEH() ? "throw 0" : "unreachable"; // currently there is only one event/exception with externref
                break;
            case RETHROW:
                name = "rethrow";
                break;
            case BR_ON_EXN:
                name = options.useEH() ? "br_on_exn " + data + " 0" : "unreachable"; // br_on_exn, break depth, event; // currently there is only one event/exception with externref
                break;
            case MONITOR_ENTER:
            case MONITOR_EXIT:
                name = "drop";
                break;
            default:
                throw new Error( "Unknown block: " + op );
        }
        newline( methodOutput );
        methodOutput.append( name );
        inset += insetAfter;
    }

    /**
     * Create a the result type for a block instruction
     * 
     * @param blockName
     *            the name of the block for example "if" or "block"
     * @param result
     *            the result type of the block
     * @return the block with result type
     * @throws IOException
     *             if any I/O error occur
     */
    @Nonnull
    private CharSequence blockWithResult( String blockName, AnyType result ) throws IOException {
        if( result == null || result == ValueType.empty ) {
            return blockName;
        } else {
            StringBuilder builder = new StringBuilder( blockName );
            if( result.toString().contains( "(" ) ) {
                builder.append( result );
            } else {
                builder.append( "(result " );
                writeTypeName( builder, result );
                builder.append( ")" );
            }
            return builder;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void writeArrayOperator( @Nonnull ArrayOperator op, ArrayType type ) throws IOException {
        String operation;
        switch( op ) {
            case NEW:
                operation = "new";
                break;
            case GET:
                operation = "get";
                break;
            case SET:
                operation = "set";
                break;
            case LEN:
                operation = "len";
                break;
            default:
                throw new Error( "Unknown operator: " + op );
        }
        newline( methodOutput );
        methodOutput.append( "array." ).append( operation ).append( ' ' ).append( normalizeName( type.toString() ) );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void writeStructOperator( StructOperator op, AnyType type, NamedStorageType fieldName, int idx ) throws IOException {
        String operation;
        switch( op ) {
            case NEW:
            case NEW_DEFAULT:
                operation = "struct.new";
                break;
            case GET:
                operation = "struct.get";
                break;
            case SET:
                operation = "struct.set";
                break;
            case NULL:
                operation = options.useGC() ? "ref.null eq" : "ref.null extern";
                type = null;
                break;
            default:
                throw new Error( "Unknown operator: " + op );
        }
        newline( methodOutput );
        methodOutput.append( operation );
        if( type != null ) {
            methodOutput.append( ' ' ).append( normalizeName( type.toString() ) );
        }
        if( fieldName != null ) {
            methodOutput.append(  ' ' ).append( idx ).append( " ;; $" ).append( normalizeName( fieldName.getName() ) );
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void writeMemoryOperator( MemoryOperator memOp, ValueType valueType, int offset, int alignment ) throws IOException {
        newline( methodOutput );
        methodOutput.append( valueType ).append( '.' ).append( memOp )
        .append( " offset=" ).append( offset )
        .append( " align=" ).append( 1 << alignment );
    }
}
