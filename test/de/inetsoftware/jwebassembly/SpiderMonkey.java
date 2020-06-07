/*
 * Copyright 2017 - 2019 Volker Berlin (i-net software)
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

import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.zip.GZIPInputStream;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;

/**
 * Download the JavaScript engine SpiderMonkey.
 * 
 * @author Volker Berlin
 */
public class SpiderMonkey {

    private String command;

    /**
     * Check if there is a new version of the script engine
     * 
     * @throws IOException
     *             if any error occur
     */
    private void download() throws IOException {
        boolean is32 = "32".equals( System.getProperty( "sun.arch.data.model" ) );
        String fileName;
        final String os = System.getProperty( "os.name", "" ).toLowerCase();
        if( os.contains( "windows" ) ) {
            fileName = is32 ? "win32" : "win64";
        } else if( os.contains( "mac" ) ) {
            fileName = is32 ? "mac" : "mac64";
        } else if( os.contains( "linux" ) ) {
            fileName = is32 ? "linux-i686" : "linux-x86_64";
        } else {
            throw new IllegalStateException( "Unknown OS: " + os );
        }
        File target = new File( System.getProperty( "java.io.tmpdir" ) + "/SpiderMonkey" );
        URL url = new URL( "https://archive.mozilla.org/pub/firefox/nightly/latest-mozilla-central/jsshell-" + fileName + ".zip" );
        System.out.println( "\tDownload: " + url );
        command = target.getAbsolutePath() + "/js";
        HttpURLConnection conn = (HttpURLConnection)url.openConnection();
        conn.setConnectTimeout( 5000 );
        if( target.exists() ) {
            conn.setIfModifiedSince( target.lastModified() );
        }
        InputStream input;
        try {
            input = conn.getInputStream();
        } catch( IOException ex ) {
            if( target.exists() ) {
                System.err.println( ex );
                return;
            }
            throw ex;
        }
        if( conn.getResponseCode() == HttpURLConnection.HTTP_NOT_MODIFIED ) {
            System.out.println( "\tUP-TP-DATE, use version from " + Instant.ofEpochMilli( target.lastModified() ) );
            return;
        }
        long lastModfied = conn.getLastModified();
        extractStream( input, false, target );
        target.setLastModified( lastModfied );
        System.out.println( "\tUse Version from " + Instant.ofEpochMilli( lastModfied ) );
    }

    /**
     * Get the SpeiderMonkey command. If file not exists then download it.
     * 
     * @return the path to the executable
     * @throws IOException
     *             if any I/O error occur
     */
    public String getCommand() throws IOException {
        if( command == null ) {
            download();
        }
        return command;
    }

    /**
     * Extract a compressed stream
     * 
     * @param input
     *            the input stream
     * @param tarGz
     *            true: *.tar.gz else *.zip
     * @param target
     *            the location to extract
     * @throws IOException
     *             if any I/O error occur
     */
    static void extractStream( InputStream input, boolean tarGz, File target ) throws IOException {
        ArchiveInputStream archiv;
        if( tarGz ) {
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

            byte[] buffer = new byte[ (int)entry.getSize() ];
            new DataInputStream( archiv ).readFully( buffer );
            Files.write( file.toPath(), buffer, StandardOpenOption.CREATE );
            file.setLastModified( entry.getLastModifiedDate().getTime() );
            file.setExecutable( true );
        } while( true );
        archiv.close();
    }
}
