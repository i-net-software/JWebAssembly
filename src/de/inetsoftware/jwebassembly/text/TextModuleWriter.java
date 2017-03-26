/*
 * Copyright 2017 Volker Berlin (i-net software)
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

import de.inetsoftware.jwebassembly.module.ModuleWriter;
import de.inetsoftware.jwebassembly.module.ValueType;

/**
 * Module Writer for text format with S-expressions.
 * 
 * @author Volker Berlin
 *
 */
public class TextModuleWriter extends ModuleWriter {

    private Appendable output;

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
        newline();
        output.append( ')' );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void writeMethodStart( String name ) throws IOException {
        newline();
        output.append( "(func $" );
        output.append( name );
        inset++;
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
        inset--;
        newline();
        output.append( ')' );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void writeConstInt( int value ) throws IOException {
        newline();
        output.append( "i32.const " ).append( Integer.toString( value ) );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void writeLoad( int idx ) throws IOException {
        newline();
        output.append( "get_local " ).append( Integer.toString( idx ) );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void writeStore( int idx ) throws IOException {
        newline();
        output.append( "set_local " ).append( Integer.toString( idx ) );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void writeAddInt() throws IOException {
        newline();
        output.append( "i32.add" );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void writeReturn() throws IOException {
        newline();
        output.append( "return" );
    }

    /**
     * Add a newline the insets.
     * 
     * @throws IOException
     *             if any I/O error occur
     */
    private void newline() throws IOException {
        output.append( '\n' );
        for( int i = 0; i < inset; i++ ) {
            output.append( ' ' );
            output.append( ' ' );
        }
    }
}
