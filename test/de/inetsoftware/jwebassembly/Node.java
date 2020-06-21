/*
 * Copyright 2020 Volker Berlin (i-net software)
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

import static de.inetsoftware.jwebassembly.SpiderMonkey.extractStream;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.MessageFormat;

/**
 * Download the node tool.
 * 
 * @author Volker Berlin
 */
public class Node {

    private String              command;

    private static final String BASE_URL = "https://nodejs.org/download/v8-canary/";

    private static final String REVISION = "15.0.0-v8-canary202006192e28363093";

    /**
     * Check if there is a new version of the script engine
     * 
     * @throws IOException
     *             if any error occur
     */
    private void download() throws IOException {
        String fileName;
        String ext;
        final String os = System.getProperty( "os.name", "" ).toLowerCase();
        if( os.contains( "windows" ) ) {
            boolean is32 = "32".equals( System.getProperty( "sun.arch.data.model" ) );
            fileName = is32 ? "win-x86" : "win-x64";
            ext = "zip";
        } else if( os.contains( "mac" ) ) {
            fileName = "darwin-x64";
            ext = "tar.gz";
        } else if( os.contains( "linux" ) ) {
            fileName = "linux-x64";
            ext = "tar.gz";
        } else {
            throw new IllegalStateException( "Unknown OS: " + os );
        }
        String urlStr = MessageFormat.format( "{0}v{1}/node-v{1}-{2}.{3}", BASE_URL, REVISION, fileName, ext );

        File target = new File( System.getProperty( "java.io.tmpdir" ) + "/node" );
        File commandDir = new File( target.getAbsolutePath() + MessageFormat.format( "/node-v{1}-{2}", BASE_URL, REVISION, fileName, ext ) );

        if( commandDir.isDirectory() && commandDir.listFiles().length > 0 ) {
            // no download needed
            System.out.println( "\tUP-TP-DATE, use version from " + commandDir );
        } else {
            URL url = new URL( urlStr );
            System.out.println( "\tDownload: " + url );
            HttpURLConnection conn = (HttpURLConnection)url.openConnection();
            conn.setConnectTimeout( 5000 );

            InputStream input = conn.getInputStream();

            extractStream( input, "tar.gz".equals( ext ), target );
        }

        command = commandDir.getAbsolutePath();
    }

    /**
     * Get the node command. If file not exists then download it.
     * 
     * @return the path to the executable
     * @throws IOException
     *             if any I/O error occur
     */
    public String getNodeDir() throws IOException {
        if( command == null ) {
            download();
        }
        return command;
    }
}
