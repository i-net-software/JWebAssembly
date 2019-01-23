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
package de.inetsoftware.jwebassembly.binary;

/**
 * @author Volker Berlin
 */
enum SectionType {
    Custom, //   0
    Type, //     1   Function signature declarations
    Import, //   2   Import declarations
    Function, // 3   Function declarations
    Table, //    4   Indirect function table and other tables
    Memory, //   5   Memory attributes
    Global, //   6   Global declarations
    Export, //   7   Exports
    Start, //    8   Start function declaration
    Element, //  9   Elements section
    Code, //    10   Function bodies (code)
    Data, //    11   Data segments
    DataCount,//12   Count of data segments https://github.com/WebAssembly/bulk-memory-operations/blob/master/proposals/bulk-memory-operations/Overview.md
    Event, //   13   Event declarations, Exceptions
}
