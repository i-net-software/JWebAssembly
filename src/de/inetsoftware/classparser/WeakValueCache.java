/*
   Copyright 2019 Volker Berlin (i-net software)

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.

*/
package de.inetsoftware.classparser;

import java.lang.ref.WeakReference;
import java.util.HashMap;

/**
 * A simple cache for weak values.
 *
 * @param <K>
 *            the key type
 * @param <V>
 *            the value type
 * @author Volker Berlin
 */
public class WeakValueCache<K, V> {

    private final HashMap<K, WeakReference<V>> map = new HashMap<>();

    /**
     * Put a value
     * 
     * @param key
     *            the key
     * @param value
     *            the value
     */
    public void put( K key, V value ) {
        map.put( key, new WeakReference<V>( value ) );
    }

    /**
     * Get the value if in the cache
     * 
     * @param key
     *            the key
     * @return the value or null
     */
    public V get( K key ) {
        WeakReference<V> valueRef = map.get( key );
        return valueRef == null ? null : valueRef.get();
    }
}
