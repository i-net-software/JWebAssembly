/*
 * Copyright 2019 Volker Berlin (i-net software)
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
package de.inetsoftware.jwebassembly.javascript;

import de.inetsoftware.jwebassembly.api.annotation.Import;

/**
 * Workaround for the missing GC feature of WebAssembly. This call add import functions to allocate the objects in the JavaScript host.
 * 
 * @author Volker Berlin
 *
 */
public abstract class NonGC {

    @Import( js = "(l) => new Uint8Array(l)" )
    static byte[] array_new_i8( int length ) {
        return null; // for compiler
    }

    @Import( js = "(l) => new Int16Array(l)" )
    static short[] array_new_i16( int length ) {
        return null; // for compiler
    }

    @Import( js = "(l) => new Int32Array(l)" )
    static int[] array_new_i32( int length ) {
        return null; // for compiler
    }

    @Import( js = "(l) => Object.seal(new Array(l).fill(0n))" )
    static long[] array_new_i64( int length ) {
        return null; // for compiler
    }

    @Import( js = "(l) => new Float32Array(l)" )
    static float[] array_new_f32( int length ) {
        return null; // for compiler
    }

    @Import( js = "(l) => new Float64Array(l)" )
    static double[] array_new_f64( int length ) {
        return null; // for compiler
    }

    @Import( js = "(l) => Object.seal(new Array(l))" )
    static Object[] array_new_anyref( int length ) {
        return null; // for compiler
    }

    @Import( js = "(a) => a.length" )
    static int array_len_i8( Object array ) {
        return 0; // for compiler
    }

    @Import( js = "(a) => a.length" )
    static int array_len_i16( Object array ) {
        return 0; // for compiler
    }

    @Import( js = "(a) => a.length" )
    static int array_len_i32( Object array ) {
        return 0; // for compiler
    }

    @Import( js = "(a) => a.length" )
    static int array_len_i64( Object array ) {
        return 0; // for compiler
    }

    @Import( js = "(a) => a.length" )
    static int array_len_f32( Object array ) {
        return 0; // for compiler
    }

    @Import( js = "(a) => a.length" )
    static int array_len_f64( Object array ) {
        return 0; // for compiler
    }

    @Import( js = "(a) => a.length" )
    static int array_len_anyref( Object array ) {
        return 0; // for compiler
    }

    @Import( js = "(a,i,v) => a[i]=v" )
    static void array_set_i8( byte[] array, int idx, byte value ) {
    }

    @Import( js = "(a,i,v) => a[i]=v" )
    static void array_set_i16( short[] array, int idx, short value ) {
    }

    @Import( js = "(a,i,v) => a[i]=v" )
    static void array_set_i32( int[] array, int idx, int value ) {
    }

    @Import( js = "(a,i,v) => a[i]=v" )
    static void array_set_i64( long[] array, int idx, long value ) {
    }

    @Import( js = "(a,i,v) => a[i]=v" )
    static void array_set_f32( float[] array, int idx, float value ) {
    }

    @Import( js = "(a,i,v) => a[i]=v" )
    static void array_set_f64( double[] array, int idx, double value ) {
    }

    @Import( js = "(a,i,v) => a[i]=v" )
    static void array_set_anyref( Object[] array, int idx, Object value ) {
    }

    @Import( js = "(a,i,v) => a[i]" )
    static byte array_get_i8( byte[] array, int idx ) {
        return 0; // for compiler
    }

    @Import( js = "(a,i,v) => a[i]" )
    static short array_get_i16( short[] array, int idx ) {
        return 0; // for compiler
    }

    @Import( js = "(a,i,v) => a[i]" )
    static int array_get_i32( int[] array, int idx ) {
        return 0; // for compiler
    }

    @Import( js = "(a,i,v) => a[i]" )
    static long array_get_i64( long[] array, int idx ) {
        return 0; // for compiler
    }

    @Import( js = "(a,i,v) => a[i]" )
    static float array_get_f32( float[] array, int idx ) {
        return 0; // for compiler
    }

    @Import( js = "(a,i,v) => a[i]" )
    static double array_get_f64( double[] array, int idx ) {
        return 0; // for compiler
    }

    @Import( js = "(a,i,v) => a[i]" )
    static Object array_get_anyref( Object[] array, int idx ) {
        return 0; // for compiler
    }
}
