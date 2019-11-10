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
package de.inetsoftware.jwebassembly.module;

import java.io.ByteArrayOutputStream;
import java.util.LinkedHashMap;

/**
 * Handle all the constant strings
 * 
 * @author Volker Berlin
 */
class StringManager extends LinkedHashMap<String, Integer> {

    /**
     * Finish the prepare. Now no new function should be added. 
     * 
     * @param writer the targets for the strings
     */
    void prepareFinish( ModuleWriter writer ) {
        writer.setStringCount( size() );
        ByteArrayOutputStream data = writer.dataStream;
        for( String str : this.keySet() ) {
        }
    }

}
