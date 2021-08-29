/*
   Copyright 2021 Volker Berlin (i-net software)

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
package de.inetsoftware.jwebassembly.module.nativecode;

import java.util.HashMap;
import java.util.Map;

/**
 * Replacement for java.lang.Enum
 *
 * @author Volker Berlin
 */
public enum ReplacementForEnums {
    ;
    private transient Map<String, ReplacementForEnums> enumConstantDirectory;

    /**
     * Replacement code for generated Enum.valueOf( String )
     * @param name the enum name
     * @return The singleton instance for the name
     */
    ReplacementForEnums valueOf_( String name ) {
        Map<String, ReplacementForEnums> map = enumConstantDirectory;
        if( map == null ) {
            ReplacementForEnums[] universe = values();
            map = new HashMap<>( 2 * universe.length );
            for( ReplacementForEnums constant : universe ) {
                map.put( constant.name(), constant );
            }
            enumConstantDirectory = map;
        }

        ReplacementForEnums result = map.get( name );
        if( result != null ) {
            return result;
        }
        if( name == null ) {
            throw new NullPointerException( "Name is null" );
        }
        throw new IllegalArgumentException( "No enum constant " + getClass().getCanonicalName() + "." + name );
    }
}
