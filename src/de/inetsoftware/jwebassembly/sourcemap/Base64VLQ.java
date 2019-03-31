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
package de.inetsoftware.jwebassembly.sourcemap;

import java.io.IOException;

/**
 * Encode an integer value as Base64VLQ
 */
class Base64VLQ {
    private static final int    VLQ_BASE_SHIFT       = 5;

    private static final int    VLQ_BASE             = 1 << VLQ_BASE_SHIFT;

    private static final int    VLQ_BASE_MASK        = VLQ_BASE - 1;

    private static final int    VLQ_CONTINUATION_BIT = VLQ_BASE;

    private static final String BASE64_MAP           = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";

    /**
     * no instance
     */
    private Base64VLQ() {
        // nothing
    }

    /**
     * Move the signet bit from the first position (two-complement value) to the last bit position.
     * 
     * examples: 1 -> 2; -1 -> 3; 2 -> 4; -2 -> 5
     * 
     * @param value
     *            two-complement value
     * @return converted value
     */
    private static int toVLQSigned( int value ) {
        return (value < 0) ? (((-value) << 1) + 1) : ((value << 1) + 0);
    }

    /**
     * Writes a VLQ encoded value to the provide target.
     * 
     * @param out
     *            the target
     * @param value
     *            the value
     * @throws IOException
     *             if any I/O error occur
     */
    static void appendBase64VLQ( Appendable out, int value ) throws IOException {
        value = toVLQSigned( value );
        do {
            int digit = value & VLQ_BASE_MASK;
            value >>>= VLQ_BASE_SHIFT;
            if( value > 0 ) {
                digit |= VLQ_CONTINUATION_BIT;
            }
            out.append( BASE64_MAP.charAt( digit ) );
        } while( value > 0 );
    }
}
