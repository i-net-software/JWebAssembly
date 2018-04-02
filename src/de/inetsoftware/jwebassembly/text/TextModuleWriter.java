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
import java.util.List;

import javax.annotation.Nullable;

import de.inetsoftware.jwebassembly.module.BlockOperator;
import de.inetsoftware.jwebassembly.module.ModuleWriter;
import de.inetsoftware.jwebassembly.module.NumericOperator;
import de.inetsoftware.jwebassembly.module.ValueType;
import de.inetsoftware.jwebassembly.module.ValueTypeConvertion;

/**
 * Module Writer for text format with S-expressions.
 * 
 * @author Volker Berlin
 *
 */
public class TextModuleWriter extends ModuleWriter {

    private Appendable output;

    private StringBuilder methodOutput = new StringBuilder();

    private int        inset;

    /**
     * Create a new instance.
     * 
     * @param output
     *            target for the result
     * @throws IOException
     *             if any I/O error occur
     */
    public TextModuleWriter( Appendable output ) throws IOException {
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
    protected void writeExport( String signatureName, String methodName, String exportName ) throws IOException {
        newline( output );
        output.append( "(export \"" ).append( exportName ).append( "\" (func $" ).append( methodName ).append( "))" );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void writeMethodStart( String signatureName, String name ) throws IOException {
        newline( output );
        output.append( "(func $" );
        output.append( name );
        inset++;
        methodOutput.setLength( 0 );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void writeMethodParam( String kind, ValueType valueType ) throws IOException {
        output.append( " (" ).append( kind ).append( ' ' ).append( valueType.toString() ).append( ')' );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void writeMethodFinish( List<ValueType> locals ) throws IOException {
        for( ValueType valueType : locals ) {
            output.append( " (local " ).append( valueType.toString() ).append( ')' );
        }
        output.append( methodOutput );
        inset--;
        newline( output );
        output.append( ')' );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void writeConstInt( int value ) throws IOException {
        newline( methodOutput );
        methodOutput.append( "i32.const " ).append( Integer.toString( value ) );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void writeConstLong( long value ) throws IOException {
        newline( methodOutput );
        methodOutput.append( "i64.const " ).append( Long.toString( value ) );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void writeConstFloat( float value ) throws IOException {
        newline( methodOutput );
        methodOutput.append( "f32.const " ).append( Float.toString( value ) );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void writeConstDouble( double value ) throws IOException {
        newline( methodOutput );
        methodOutput.append( "f64.const " ).append( Double.toString( value ) );
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
    protected void writeNumericOperator( NumericOperator numOp, @Nullable ValueType valueType ) throws IOException {
        newline( methodOutput );
        methodOutput.append( valueType ).append( '.' ).append( numOp );
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
                op = "i32.trunc_s/f32";
                break;
            case f2l:
                op = "i64.trunc_s/f32";
                break;
            case f2d:
                op = "f64.promote/f32";
                break;
            case d2i:
                op = "i32.trunc_s/f64";
                break;
            case d2l:
                op = "i64.trunc_s/f64";
                break;
            case d2f:
                op = "f32.demote/f64";
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
    protected void writeBlockCode( BlockOperator op ) throws IOException {
        String name;
        int insetAfter = 0;
        switch( op ) {
            case RETURN:
                name = "return";
                break;
            case IF:
                name = "if";
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
            default:
                throw new Error( "Unknown block: " + op );
        }
        newline( methodOutput );
        methodOutput.append( name );
        inset += insetAfter;
    }
}
