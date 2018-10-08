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
package de.inetsoftware.jwebassembly.text;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import de.inetsoftware.classparser.ConstantRef;
import de.inetsoftware.jwebassembly.module.FunctionName;
import de.inetsoftware.jwebassembly.module.ModuleWriter;
import de.inetsoftware.jwebassembly.module.NumericOperator;
import de.inetsoftware.jwebassembly.module.ValueType;
import de.inetsoftware.jwebassembly.module.ValueTypeConvertion;
import de.inetsoftware.jwebassembly.module.WasmBlockOperator;

/**
 * Module Writer for text format with S-expressions.
 * 
 * @author Volker Berlin
 *
 */
public class TextModuleWriter extends ModuleWriter {

    private Appendable      output;

    private StringBuilder   methodOutput = new StringBuilder();

    private int             inset;

    private boolean         isImport;

    private HashSet<String> globals      = new HashSet<>();

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
        output.append( "(module" );
        inset++;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() throws IOException {
        inset--;
        newline( output );
        output.append( ')' );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void prepareImport( FunctionName name, String importModule, String importName ) throws IOException {
        if( importName != null ) {
            newline( output );
            output.append( "(import \"" ).append( importModule ).append( "\" \"" ).append( importName ).append( "\" (func $" ).append( name.fullName );
            isImport = true;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void writeExport( FunctionName name, String exportName ) throws IOException {
        newline( output );
        output.append( "(export \"" ).append( exportName ).append( "\" (func $" ).append( name.fullName ).append( "))" );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void writeMethodStart( FunctionName name ) throws IOException {
        methodOutput.setLength( 0 );
        newline( methodOutput );
        methodOutput.append( "(func $" );
        methodOutput.append( name.fullName );
        inset++;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void writeMethodParam( String kind, ValueType valueType ) throws IOException {
        methodOutput.append( " (" ).append( kind ).append( ' ' ).append( valueType.toString() ).append( ')' );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void writeMethodParamFinish( List<ValueType> locals ) throws IOException {
        if( isImport ) {
            isImport = false;
            output.append( "))" );
        } else {
            for( ValueType valueType : locals ) {
                methodOutput.append( " (local " ).append( valueType.toString() ).append( ')' );
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void writeMethodFinish() throws IOException {
        output.append( methodOutput );
        inset--;
        newline( output );
        output.append( ')' );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void writeConst( Number value, ValueType valueType ) throws IOException {
        newline( methodOutput );
        methodOutput.append( valueType ).append( ".const " ).append( value );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void writeLoad( int idx ) throws IOException {
        newline( methodOutput );
        methodOutput.append( "get_local " ).append( Integer.toString( idx ) );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void writeStore( int idx ) throws IOException {
        newline( methodOutput );
        methodOutput.append( "set_local " ).append( Integer.toString( idx ) );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void writeGlobalAccess( boolean load, FunctionName name, ConstantRef ref ) throws IOException {
        if( !globals.contains( name.fullName ) ) {
            // declare global variable if not already declared.
            output.append( "\n  " );
            String type = ValueType.getValueType( ref.getType(), 0 ).toString();
            output.append( "(global $" ).append( name.fullName ).append( " (mut " ).append( type ).append( ") " ).append( type ).append( ".const 0)" );
            globals.add( name.fullName );
        }
        newline( methodOutput );
        methodOutput.append( load ? "get_global $" : "set_global $" ).append( name.fullName );
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
                }
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
                op = "i64.extend_s/i32";
                break;
            case i2f:
                op = "f32.convert_s/i32";
                break;
            case i2d:
                op = "f64.convert_s/i32";
                break;
            case l2i:
                op = "i32.wrap/i64";
                break;
            case l2f:
                op = "f32.convert_s/i64";
                break;
            case l2d:
                op = "f64.convert_s/i64";
                break;
            case f2i:
                op = "i32.trunc_s:sat/f32";
                break;
            case f2l:
                op = "i64.trunc_s:sat/f32";
                break;
            case f2d:
                op = "f64.promote/f32";
                break;
            case d2i:
                op = "i32.trunc_s:sat/f64";
                break;
            case d2l:
                op = "i64.trunc_s:sat/f64";
                break;
            case d2f:
                op = "f32.demote/f64";
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
     * Add a newline the insets.
     * 
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
    protected void writeFunctionCall( String name ) throws IOException {
        newline( methodOutput );
        name = name.substring( 0, name.indexOf( '(' ) );
        methodOutput.append( "call $" ).append( name );
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
            default:
                throw new Error( "Unknown block: " + op );
        }
        newline( methodOutput );
        methodOutput.append( name );
        inset += insetAfter;
    }
}
