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
import de.inetsoftware.jwebassembly.wasm.ArrayOperator;
import de.inetsoftware.jwebassembly.wasm.NamedStorageType;
import de.inetsoftware.jwebassembly.wasm.NumericOperator;
import de.inetsoftware.jwebassembly.wasm.AnyType;
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

    private Appendable      output;

    private final boolean   debugNames;

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
        debugNames = Boolean.parseBoolean( properties.get( JWebAssembly.DEBUG_NAMES ) );
        output.append( "(module" );
        inset++;
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
        output.append( "(type $" ).append( typeName ).append( " (struct" );
        inset++;
        for( NamedStorageType field : fields ) {
            newline( output );
            output.append( "(field" );
            if( debugNames && field.name != null ) {
                output.append( " $" ).append( field.name );
            }
            output.append( " (mut " );
            AnyType type = field.type;
            if( type.getCode() < 0 ) {
                output.append( type.toString() );
            } else {
                output.append( "(ref " ).append( type.toString() ).append( ')' );
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
    protected void prepareImport( FunctionName name, String importModule, String importName ) throws IOException {
        if( importName != null ) {
            newline( methodOutput );
            methodOutput.append( "(import \"" ).append( importModule ).append( "\" \"" ).append( importName ).append( "\" (func $" ).append( name.fullName );
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
        newline( methodOutput );
        methodOutput.append( "(func $" );
        methodOutput.append( name.fullName );
        inset++;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void writeMethodParam( String kind, AnyType valueType, @Nullable String name ) throws IOException {
        methodOutput.append( " (" ).append( kind );
        if( debugNames && name != null ) {
            methodOutput.append( " $" ).append( name );
        }
        methodOutput.append( ' ' ).append( valueType.toString() ).append( ')' );
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
        methodOutput.append( valueType ).append( ".const " ).append( value );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void writeLocal( VariableOperator op, int idx ) throws IOException {
        newline( methodOutput );
        methodOutput.append( "local." ).append( op ).append( ' ' ).append( Integer.toString( idx ) );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void writeGlobalAccess( boolean load, FunctionName name, Member ref ) throws IOException {
        if( !globals.contains( name.fullName ) ) {
            // declare global variable if not already declared.
            output.append( "\n  " );
            String type = ValueType.getValueType( ref.getType() ).toString();
            output.append( "(global $" ).append( name.fullName ).append( " (mut " ).append( type ).append( ") " ).append( type ).append( ".const 0)" );
            globals.add( name.fullName );
        }
        newline( methodOutput );
        methodOutput.append( load ? "global.get $" : "global.set $" ).append( name.fullName );
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
                        methodOutput.append( "ref.isnull" );
                        writeNumericOperator( NumericOperator.eqz, ValueType.i32 );
                        return;
                    case ifnull:
                        methodOutput.append( "ref.isnull" );
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
        String signatureName = name.signatureName;
        signatureName = signatureName.substring( 0, signatureName.indexOf( '(' ) );
        methodOutput.append( "call $" ).append( signatureName );
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
            case TRY:
                name = "try";
                insetAfter++;
                break;
            case CATCH:
                inset--;
                name = "catch";
                insetAfter++;
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
    protected void writeStructOperator( StructOperator op, AnyType type, String fieldName ) throws IOException {
        String operation;
        switch( op ) {
            case NEW:
                operation = "struct.new";
                break;
            case NEW_DEFAULT:
                operation = "struct.new_default";
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
            methodOutput.append( ' ' ).append( type );
        }
        if( fieldName != null ) {
            methodOutput.append( " $" ).append( fieldName );
        }
    }
}
