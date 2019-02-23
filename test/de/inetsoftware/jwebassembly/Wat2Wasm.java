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
package de.inetsoftware.jwebassembly;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.junit.Assert;

/**
 * Download the wat2wasm tool from github
 * 
 * @author Volker Berlin
 */
class Wat2Wasm {

    private String command;

    /**
     * Check if there is a new version of the script engine
     * 
     * @param target
     *            the target directory
     * @throws IOException
     *             if any error occur
     */
    private void download( File target ) throws IOException {
        boolean is32 = "32".equals( System.getProperty( "sun.arch.data.model" ) );
        String fileName;
        final String os = System.getProperty( "os.name", "" ).toLowerCase();
        if( os.contains( "windows" ) ) {
            fileName = is32 ? "win32.zip" : "win64.zip";
        } else if( os.contains( "mac" ) ) {
            fileName = "osx.tar.gz";
        } else if( os.contains( "linux" ) ) {
            fileName = "linux.tar.gz";
        } else {
            throw new IllegalStateException( "Unknown OS: " + os );
        }

        URL url = new URL( "https://github.com/WebAssembly/wabt/releases/latest" );
        HttpURLConnection conn = (HttpURLConnection)url.openConnection();
        InputStream input = conn.getInputStream();
        String data = WasmRule.readStream( input );

        Pattern pattern = Pattern.compile( "/WebAssembly/wabt/releases/download/[0-9.]*/wabt-[0-9.]*-" + fileName );
        Matcher matcher = pattern.matcher( data );
        Assert.assertTrue( matcher.find() );
        String downloadUrl = matcher.group();
        url = new URL( url, downloadUrl );
        System.out.println( "\tDownload: " + url );

        conn = (HttpURLConnection)url.openConnection();
        if( target.exists() ) {
            conn.setIfModifiedSince( target.lastModified() );
        }

        input = conn.getInputStream();
        if( conn.getResponseCode() == HttpURLConnection.HTTP_NOT_MODIFIED ) {
            System.out.println( "\tUP-TO-DATE, use version from " + Instant.ofEpochMilli( target.lastModified() ) );
            return;
        }

        long lastModfied = conn.getLastModified();

        ArchiveInputStream archiv;
        if( fileName.endsWith( ".tar.gz" ) ) {
            input = new GZIPInputStream( input );
            archiv = new TarArchiveInputStream( input );
        } else {
            archiv = new ZipArchiveInputStream( input );
        }

        do {
            ArchiveEntry entry = archiv.getNextEntry();
            if( entry == null ) {
                break;
            }
            if( entry.isDirectory() ) {
                continue;
            }
            File file = new File( target, entry.getName() );
            file.getParentFile().mkdirs();

            Files.copy( archiv, file.toPath(), StandardCopyOption.REPLACE_EXISTING );
            file.setLastModified( entry.getLastModifiedDate().getTime() );
            file.setExecutable( true );
        } while( true );

        target.setLastModified( lastModfied );
        System.out.println( "\tUse Version from " + Instant.ofEpochMilli( lastModfied ) );
    }

    /**
     * Search the tool in the directory recursively and set the command.
     * 
     * @param dir
     *            the directory
     */
    private void searchExecuteable( File dir ) {
        File[] list = dir.listFiles();
        if( list != null ) {
            for( File file : list ) {
                if( file.isDirectory() ) {
                    searchExecuteable( file );
                } else {
                    if( file.getName().contains( "wat2wasm" ) ) {
                        command = file.getAbsolutePath();
                    }
                }
                if( command != null ) {
                    return;
                }
            }
        }
    }

    /**
     * Get the executable command
     * 
     * @return the command
     * @throws IOException
     *             if any I/O error occur 
     */
    public String getCommand() throws IOException {
        if( command == null ) {
            File target = new File( System.getProperty( "java.io.tmpdir" ) + "/wabt" );
            download( target );
            searchExecuteable( target );
        }
        return command;
    }
}
