/*
 * Copyright 2018 Volker Berlin (i-net software)
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
package de.inetsoftware.jwebassembly.binary;

import java.io.IOException;

/**
 * Signature for an entry in a section
 * 
 * @author Volker Berlin
 */
abstract class SectionEntry {

    /**
     * Write this single entry to a section
     * 
     * @param stream
     *            the target
     * @throws IOException
     *             if any I/O error occur
     */
    abstract void writeSectionEntry( WasmOutputStream stream ) throws IOException;
}
