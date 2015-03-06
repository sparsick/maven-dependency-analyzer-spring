/**
 * Copyright 2010 Tobias Gierke <tobias.gierke@code-sourcery.de>
 * Copyright 2015 Sandra Parsick <mail@sandra-parsick.de>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.maven.shared.dependency.analyzer.spring;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.commons.io.FileUtils;

import org.apache.maven.model.Resource;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.components.io.fileselectors.FileInfo;
import org.codehaus.plexus.components.io.fileselectors.IncludeExcludeFileSelector;
import org.codehaus.plexus.logging.Logger;

public class DefaultSpringXmlFileLocator
    implements SpringXmlFileLocator
{

    private Logger log;

    @SuppressWarnings( "unchecked" )
    @Override
    public Set<File> locateSpringXmls( MavenProject project )
        throws IOException
    {
        final Set<File> result = new HashSet<File>();
        addSpringXmls( project.getResources(), result );
        addSpringXmls( project.getTestResources(), result );

        return result;
    }

    public void setLog( Logger log )
    {
        this.log = log;
    }

    private final boolean isLogDebug()
    {
        return log != null && log.isDebugEnabled();
    }

    private void addSpringXmls( List<Resource> resources, Set<File> springXmls )
        throws IOException
    {
        final boolean logDebug = isLogDebug();

        for ( Resource r : resources )
        {
            if ( logDebug )
            {
                log.debug( "Scanning " + r.getDirectory() );
            }

            final File dir = new File( r.getDirectory() );
            if ( dir.isDirectory() )
            {
                scanDirectoryForSpringXmls( dir, new ResourceFileFilter( r ), springXmls );
            }
            else if ( logDebug )
            {
                log.debug( "Skipping non-directory " + dir.getAbsolutePath() );
            }
        }
    }

    protected boolean isSpringXml( File file )
        throws IOException
    {

        if ( !file.isFile() )
        {
            return false;
        }

        List<String> readLines = FileUtils.readLines(file);
        
        for (String line : readLines) {
            if (line != null && line.contains("<beans")) {
                return true;
            }
        }
        
        return false;
    }

    private void scanDirectoryForSpringXmls( File directory, FileFilter selector, Set<File> springXmls )
        throws IOException
    {
        if ( isLogDebug() )
        {
            log.debug( "Scanning for Spring XMLs ... " + directory.getAbsolutePath() );
        }

        final File[] filesInDir = directory.listFiles( selector );
        if ( filesInDir == null )
        {
            return;
        }

        for ( File file : filesInDir )
        {
            if ( !file.isDirectory() )
            {
                if ( isSpringXml( file ) )
                {
                    springXmls.add( file );
                }
            }
            else
            {
                scanDirectoryForSpringXmls( file, selector, springXmls );
            }
        }
    }

    private interface FileSelector
    {
        public boolean isSelected( File file )
            throws IOException;
    }

    private static final class ResourceFileSelector
        implements FileSelector
    {

        private final IncludeExcludeFileSelector selector;

        @SuppressWarnings( "unchecked" )
        public ResourceFileSelector( Resource resource )
        {
            selector = new IncludeExcludeFileSelector();
            selector.setExcludes( toArrayOrNull( resource.getExcludes() ) );
            selector.setIncludes( toArrayOrNull( resource.getIncludes() ) );
        }
        
        private static String[] toArrayOrNull( Collection<String> strings )
        {
            return strings == null || strings.isEmpty() ? null : strings.toArray( new String[strings.size()] );
        }

        @Override
        public boolean isSelected( File file )
            throws IOException
        {
            return selector.isSelected( createFileInfo( file ) );
        }

        private FileInfo createFileInfo( final File file )
            throws IOException
        {
            return new FileInfo()
            {
                @Override
                public InputStream getContents()
                    throws IOException
                {
                    return new FileInputStream( file );
                }

                @Override
                public String getName()
                {
                    return file.getName();
                }

                @Override
                public boolean isDirectory()
                {
                    return file.isDirectory();
                }

                @Override
                public boolean isFile()
                {
                    return file.isFile();
                }
            };
        }
    }

    private static final class ResourceFileFilter
        implements FileFilter
    {
        private final FileSelector fileSelector;

        public ResourceFileFilter( Resource resource )
        {
            if ( resource == null )
            {
                throw new IllegalArgumentException( "selector cannot be NULL" );
            }
            this.fileSelector = new ResourceFileSelector( resource );
        }

        @Override
        public boolean accept( File pathname )
        {
            try
            {
                return fileSelector.isSelected( pathname );
            }
            catch ( IOException e )
            {
                throw new RuntimeException( e );
            }
        }

    }

}
