/*
   Copyright 2020 Volker Berlin (i-net software)

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

import java.io.IOException;
import java.io.InputStream;

import javax.annotation.Nullable;

import de.inetsoftware.classparser.ClassFile;
import de.inetsoftware.classparser.WeakValueCache;

/**
 * Cache and manager for the loaded ClassFiles
 * 
 * @author Volker Berlin
 */
public class ClassFileLoader {

    private final WeakValueCache<String, ClassFile> CACHE = new WeakValueCache<>();

    private final ClassLoader                       loader;

    /**
     * Create a new instance
     * 
     * @param loader
     *            the classloader to find the *.class files
     */
    public ClassFileLoader( ClassLoader loader ) {
        this.loader = loader;
    }

    /**
     * Get the ClassFile from cache or load it.
     * 
     * @param className
     *            the class name
     * @return the ClassFile or null
     * @throws IOException
     *             If any I/O error occur
     */
    @Nullable
    public ClassFile get( String className ) throws IOException {
        ClassFile classFile = CACHE.get( className );
        if( classFile != null ) {
            return classFile;
        }
        InputStream stream = loader.getResourceAsStream( className + ".class" );
        if( stream != null ) {
            classFile = new ClassFile( stream );
            CACHE.put( className, classFile );
        }
        return classFile;
    }

}
