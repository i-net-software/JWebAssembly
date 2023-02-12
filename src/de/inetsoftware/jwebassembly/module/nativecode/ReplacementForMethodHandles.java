/*
   Copyright 2023 Volker Berlin (i-net software)

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

import java.lang.invoke.MethodHandles.Lookup;

import de.inetsoftware.jwebassembly.api.annotation.Replace;

/**
 * Replacement for java.lang.invoke.MethodHandles
 *
 * @author Volker Berlin
 */
public class ReplacementForMethodHandles {

    /**
     * Replacement for static lookup().
     */
    @Replace( "java/lang/invoke/MethodHandles.lookup()Ljava/lang/invoke/MethodHandles$Lookup;" )
    static Lookup lookup() {
        return null;
    }

    /**
     * Replacement for static lookup().
     */
    @Replace( "java/lang/invoke/MethodHandles$Lookup.findVarHandle(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/Class;)Ljava/lang/invoke/VarHandle;" )
    Object findVarHandle(Class<?> recv, String name, Class<?> type) {
        return null;
    }

}
