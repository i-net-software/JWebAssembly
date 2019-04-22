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
package de.inetsoftware.jwebassembly.text;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import de.inetsoftware.classparser.Member;
import de.inetsoftware.jwebassembly.JWebAssembly;
import de.inetsoftware.jwebassembly.module.FunctionName;
import de.inetsoftware.jwebassembly.module.ModuleWriter;
import de.inetsoftware.jwebassembly.module.ValueTypeConvertion;
import de.inetsoftware.jwebassembly.wasm.AnyType;
import de.inetsoftware.jwebassembly.wasm.ArrayOperator;
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

    private final boolean   spiderMonkey = Boolean.getBoolean( "SpiderMonkey" );

    private Appendable      output;

    private final boolean   debugNames;

    private final ArrayList<String> methodParamNames = new ArrayList<>();

    private StringBuilder   methodOutput = new StringBuilder();

    private int             inset;

    private boolean         isImport;

    private HashSet<String> globals      = new HashSet<>();

    private boolean         useExceptions;

    /**
     * Create a new instance.
     * 
     * @param output
     *            target for the result
     * @param properties
     *            compiler properties
     * @throws IOException
     *             if any I/O error occur
     */
    public TextModuleWriter( Appendable output, HashMap<String, String> properties ) throws IOException {
        this.output = output;
        debugNames = Boolean.parseBoolean( properties.get( JWebAssembly.DEBUG_NAMES ) );
        output.append( "(module" );
        inset++;
        if( spiderMonkey ) {
            output.append( " (gc_feature_opt_in 3)" ); // enable GcFeatureOptIn for SpiderMonkey https://github.com/lars-t-hansen/moz-gc-experiments/blob/master/version2.md
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() throws IOException {
        output.append( methodOutput );
        inset--;
        newline( output );
        output.append( ')' );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected int writeStruct( String typeName, List<NamedStorageType> fields ) throws IOException {
        int oldInset = inset;
        inset = 1;
        newline( output );
        typeName = normalizeName( typeName );
        output.append( "(type $" ).append( typeName ).append( " (struct" );
        inset++;
        for( NamedStorageType field : fields ) {
            newline( output );
            output.append( "(field" );
            if( debugNames && field.getName() != null ) {
                output.append( " $" ).append( typeName ).append(  '.' ).append( field.getName() );
            }
            output.append( " (mut " );
            AnyType type = field.getType();
            if( type.getCode() < 0 ) {
                output.append( type.toString() );
            } else {
                output.append( "(ref " ).append( normalizeName( type.toString() ) ).append( ')' );
            }
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
            output.append( "(event (param anyref))" );
            inset = oldInset;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void prepareImport( FunctionName name, String importModule, String importName ) throws IOException {
        if( importName != null ) {
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
        return normalizeName( name.fullName );
    }

    /**
     * Normalize the function name for the text format
     * 
     * @param name
     *            the name
     * @return the normalized name
     */
    @Nonnull
    private String normalizeName( String name ) {
        if( spiderMonkey ) {
            name = name.replace( '/', '.' ).replace( '<', '_' ).replace( '>', '_' ); // TODO HACK for https://bugzilla.mozilla.org/show_bug.cgi?id=1511485
        }
        return name;
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
     * {@inheritDoc}
     */
    @Override
    protected void writeMethodStart( FunctionName name, String sourceFile ) throws IOException {
        newline( methodOutput );
        methodOutput.append( "(func $" );
        methodOutput.append( normalizeName( name ) );
        inset++;
        methodParamNames.clear();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void writeMethodParam( String kind, AnyType valueType, @Nullable String name ) throws IOException {
        methodOutput.append( " (" ).append( kind );
        if( debugNames ) { 
            if( name != null ) {
                methodOutput.append( " $" ).append( name );
            }
            if( kind != "result" ) {
                methodParamNames.add( name );
            }
        }
        methodOutput.append( ' ' );
        if( valueType.getCode() < 0 ) {
            methodOutput.append( valueType );
        } else {
            methodOutput.append( "(ref " ).append( normalizeName( valueType.toString() ) ).append( ')' );
        }
        methodOutput.append( ')' );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void writeMethodParamFinish( ) throws IOException {
        if( isImport ) {
            isImport = false;
            methodOutput.append( "))" );
        }
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
                methodOutput.append( Float.toHexString( value.floatValue() ) ).append( " ;;" ).append( value );
                break;
            case f64:
                methodOutput.append( Double.toHexString( value.doubleValue() ) ).append( " ;;" ).append( value );
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
    protected void writeGlobalAccess( boolean load, FunctionName name, Member ref ) throws IOException {
        String fullName = normalizeName( name );
        if( !globals.contains( fullName ) ) {
            // declare global variable if not already declared.
            output.append( "\n  " );
            String type = ValueType.getValueType( ref.getType() ).toString();
            output.append( "(global $" ).append( fullName ).append( " (mut " ).append( type ).append( ") (" ).append( type ).append( ".const 0))" );
            globals.add( fullName );
        }
        newline( methodOutput );
        methodOutput.append( load ? "global.get $" : "global.set $" ).append( fullName );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void writeNumericOperator( NumericOperator numOp, @Nullable ValueType valueType ) throws IOException {
        newline( methodOutput );
        String op = numOp.toString();
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
                        methodOutput.append( "ref.is_null" );
                        writeNumericOperator( NumericOperator.eqz, ValueType.i32 );
                        return;
                    case ifnull:
                        methodOutput.append( "ref.is_null" );
                        return;
                    case ref_ne:
                        methodOutput.append( "ref.eq" );
                        writeNumericOperator( NumericOperator.eqz, ValueType.i32 );
                        return;
                    case ref_eq:
                        methodOutput.append( "ref.eq" );
                        return;
                    default:
                }
                break;
            default:
        }
        methodOutput.append( valueType ).append( '.' ).append( op );
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
            default:
                throw new Error( "Unknown cast: " + cast );
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
    protected void writeFunctionCall( FunctionName name ) throws IOException {
        newline( methodOutput );
        methodOutput.append( "call $" ).append( normalizeName( name ) );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void writeBlockCode( @Nonnull WasmBlockOperator op, @Nullable Object data ) throws IOException {
        String name;
        int insetAfter = 0;
        switch( op ) {
            case RETURN:
                name = "return";
                break;
            case IF:
                name = "if";
                if( data != ValueType.empty ) {
                    name += " (result " + data + ")";
                }
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
                name = "block";
                if( data != null ) {
                    name += " (result " + data + ")";
                }
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
                name = "try";
                insetAfter++;
                break;
            case CATCH:
                inset--;
                name = "catch";
                insetAfter++;
                break;
            case THROW:
                name = "throw 0"; // currently there is only one event/exception with anyref
                break;
            case RETHROW:
                name = "rethrow";
                break;
            case BR_ON_EXN:
                name = "br_on_exn " + data + " 0"; // br_on_exn, break depth, event; // currently there is only one event/exception with anyref
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
     * {@inheritDoc}
     */
    @Override
    protected void writeArrayOperator( @Nonnull ArrayOperator op, AnyType type ) throws IOException {
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
            case LENGTH:
                operation = "len";
                break;
            default:
                throw new Error( "Unknown operator: " + op );
        }
        newline( methodOutput );
        methodOutput.append( "array." ).append( operation ).append( ' ' ).append( type );
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
                operation = "ref.null";
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
}
