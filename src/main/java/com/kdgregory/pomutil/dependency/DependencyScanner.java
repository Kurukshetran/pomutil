// Copyright Keith D Gregory
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.kdgregory.pomutil.dependency;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.w3c.dom.Element;

import org.apache.log4j.Logger;

import net.sf.kdgcommons.lang.StringUtil;
import net.sf.practicalxml.ParseUtil;

import com.kdgregory.pomutil.util.Artifact;
import com.kdgregory.pomutil.util.Artifact.Scope;
import com.kdgregory.pomutil.util.PomWrapper;
import com.kdgregory.pomutil.util.Utils;


/**
 *  Examines all direct dependencies referenced by the POM, to
 *  extract their contained classes.
 */
public class DependencyScanner
{

//----------------------------------------------------------------------------
//  Instance Variables and Constructors
//----------------------------------------------------------------------------

    private Logger logger = Logger.getLogger(getClass());

    private PomWrapper pom;

    private Set<Artifact> dependencies = new TreeSet<Artifact>();
    private Map<String,Artifact> dependencyLookup = new HashMap<String,Artifact>();


    public DependencyScanner(File pomFile)
    throws IOException
    {
        this.pom = new PomWrapper(ParseUtil.parse(pomFile));

        extractDependencies();
        buildDependencyLookup();
    }


//----------------------------------------------------------------------------
//  Public methods
//----------------------------------------------------------------------------

    /**
     *  Returns all dependencies.
     */
    public Collection<Artifact> getDependencies()
    {
        return dependencies;
    }


    /**
     *  Returns all dependencies in the specified scope(s).
     */
    public Collection<Artifact> getDependencies(Scope... scopes)
    {
        Set<Artifact> result = new TreeSet<Artifact>();
        for (Artifact artifact : dependencies)
        {
            for (Scope scope : scopes)
            {
                if (artifact.scope == scope)
                {
                    result.add(artifact);
                    break;
                }
            }
        }
        return result;
    }


    /**
     *  Returns the artifact ID of the dependency that provides the specified
     *  class and scope. Returns <code>null</code> if the class is not provided
     *  by a dependency of this POM.
     */
    public Artifact getDependency(String className, Scope scope)
    {
        Artifact dependency = dependencyLookup.get(className);
        if ((dependency == null) || (dependency.scope != scope))
            return null;
        return dependency;
    }


//----------------------------------------------------------------------------
//  Internals
//----------------------------------------------------------------------------

    private String lookupVersionProperty(String origVersion)
    {
        String version = origVersion.substring(2);
        version = version.substring(0, version.length() - 1);
        version = pom.getProperty(version);
        // FIXME - log if null; in future, check parent POM
        return version;
    }


    private void extractDependencies()
    throws IOException
    {
        for (Element dependency : pom.selectElements("/mvn:project/mvn:dependencies/mvn:dependency"))
        {
            String groupId = pom.selectValue(dependency, "mvn:groupId");
            String artifactId = pom.selectValue(dependency, "mvn:artifactId");
            String version = pom.selectValue(dependency, "mvn:version");
            String packaging = pom.selectValue(dependency, "mvn:packaging");
            String scope = pom.selectValue(dependency, "mvn:scope");

            if (!StringUtil.isBlank(packaging) && !packaging.equalsIgnoreCase("jar"))
                continue;

            if (version.startsWith("${"))
                version = lookupVersionProperty(version);
            if (version == null)
                continue;

            logger.debug("adding dependency: " + groupId + ":" + artifactId + ":" + version + ":" + scope);

            dependencies.add(new Artifact(groupId, artifactId, version, "", packaging, scope));
        }
    }


    private void buildDependencyLookup()
    throws IOException
    {
        for (Artifact dependency : dependencies)
        {
            File jarFile = Utils.getLocalRepositoryFile(dependency);
            if (!jarFile.exists())
                throw new IOException("dependency not in repository: " + dependency);
            logger.debug("processing " + dependency + " as " + jarFile);
            for (String className : Utils.extractClassesFromJar(jarFile))
            {
                dependencyLookup.put(className, dependency);
            }
        }
    }
}