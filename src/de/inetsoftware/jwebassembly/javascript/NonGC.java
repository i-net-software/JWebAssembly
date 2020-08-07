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
 * Workaround/polyfill for the missing GC feature of WebAssembly. This call add import functions to allocate the objects in the JavaScript host.
 * 
 * @author Volker Berlin
 *
 */
public abstract class NonGC {


    @Import( js = "(a,b) => a === b" )
    native static int ref_eq( Object a, Object b );
}
