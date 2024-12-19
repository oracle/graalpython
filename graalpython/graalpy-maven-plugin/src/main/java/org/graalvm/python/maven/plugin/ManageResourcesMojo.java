/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.graalvm.python.maven.plugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;

import org.apache.maven.plugins.annotations.*;
import org.apache.maven.project.*;
import org.eclipse.aether.graph.Dependency;
import org.graalvm.python.embedding.tools.vfs.VFSUtils;

import static org.graalvm.python.embedding.tools.vfs.VFSUtils.GRAALPY_GROUP_ID;
import static org.graalvm.python.embedding.tools.vfs.VFSUtils.LAUNCHER_NAME;
import static org.graalvm.python.embedding.tools.vfs.VFSUtils.VFS_ROOT;
import static org.graalvm.python.embedding.tools.vfs.VFSUtils.VFS_VENV;


@Mojo(name = "process-graalpy-resources", defaultPhase = LifecyclePhase.PROCESS_RESOURCES,
                requiresDependencyCollection = ResolutionScope.COMPILE_PLUS_RUNTIME,
                requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME)
public class ManageResourcesMojo extends AbstractMojo {

    private static final String PYTHON_LAUNCHER_ARTIFACT_ID = "python-launcher";

    private static final String POLYGLOT_GROUP_ID = "org.graalvm.polyglot";
    private static final String PYTHON_COMMUNITY_ARTIFACT_ID = "python-community";
    private static final String PYTHON_ARTIFACT_ID = "python";
    private static final String GRAALPY_MAVEN_PLUGIN_ARTIFACT_ID = "graalpy-maven-plugin";

    @Parameter(defaultValue = "${project}", required = true, readonly = true)
    MavenProject project;

    @Parameter
    String pythonResourcesDirectory;

    @Parameter
    List<String> packages;

    public static class PythonHome {
        private List<String> includes;
        private List<String> excludes;
    }

    @Parameter
    PythonHome pythonHome;

    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    private MavenSession session;

    @Component
    private ProjectBuilder projectBuilder;

    private Set<String> launcherClassPath;

    public void execute() throws MojoExecutionException {

        if(pythonResourcesDirectory != null) {
            if(pythonResourcesDirectory.trim().isEmpty()) {
                pythonResourcesDirectory = null;
            } else {
                pythonResourcesDirectory = pythonResourcesDirectory.trim();
            }
        }

        if(pythonHome != null) {
            getLog().warn("The GraalPy plugin <pythonHome> configuration setting was deprecated and has no effect anymore.\n" +
                "For execution in jvm mode, the python language home is always available.\n" +
                "When building a native executable using GraalVM, then the full python language home is by default embedded into the native executable.\n" +
                "For more details, please refer to native image options IncludeLanguageResources and CopyLanguageResources documentation.");
        }

        manageVenv();
        listGraalPyResources();
        manageNativeImageConfig();

        for(Resource r : project.getBuild().getResources()) {
            if (Files.exists(Path.of(r.getDirectory(), VFS_ROOT, "proj"))) {
                getLog().warn(String.format("usage of %s is deprecated, use %s instead", Path.of(VFS_ROOT, "proj"), Path.of(VFS_ROOT, "src")));
            }
            if (!Files.exists(Path.of(r.getDirectory(), VFS_ROOT)) && Files.exists(Path.of(r.getDirectory(), "vfs", "proj"))) {
                // there isn't the actual vfs resource root "org.graalvm.python.vfs" (VFS_ROOT), and there is only the outdated "vfs/proj"
                // => looks like a project created < 24.1.0
                throw new MojoExecutionException(String.format(
                        "Wrong virtual filesystem root!\n" +
                        "Since 24.1.0 the virtual filesystem root has to be '%s'.\n" +
                        "Please rename the resource directory '%s' to '%s'", VFS_ROOT, Path.of(r.getDirectory(), "vfs"), Path.of(r.getDirectory(), VFS_ROOT)));
            }
        }

    }

    private void manageNativeImageConfig() throws MojoExecutionException {
        try {
            VFSUtils.writeNativeImageConfig(Path.of(project.getBuild().getOutputDirectory(), "META-INF"), GRAALPY_MAVEN_PLUGIN_ARTIFACT_ID);
        } catch (IOException e) {
            throw new MojoExecutionException("failed to create native image configuration files", e);
        }
    }

    private void listGraalPyResources() throws MojoExecutionException {
        Path vfs = Path.of(project.getBuild().getOutputDirectory(), VFS_ROOT);
        if (Files.exists(vfs)) {
            try {
                VFSUtils.generateVFSFilesList(vfs);
            } catch (IOException e) {
                throw new MojoExecutionException(String.format("Failed to generate files list in '%s'", vfs.toString()), e);
            }
        }
    }

    private void manageVenv() throws MojoExecutionException {
        Path venvDirectory;
        if(pythonResourcesDirectory == null) {
            venvDirectory = Path.of(project.getBuild().getOutputDirectory(), VFS_ROOT, VFS_VENV);
        } else {
            venvDirectory = Path.of(pythonResourcesDirectory, VFS_VENV);
        }

        try {
            if (packages == null && pythonResourcesDirectory == null) {
                getLog().info(String.format("No venv packages declared, deleting %s", venvDirectory));
                delete(venvDirectory);
                return;
            }

            VFSUtils.createVenv(venvDirectory, new ArrayList<String>(packages), getLauncherPath(), () -> calculateLauncherClasspath(project), getGraalPyVersion(project), new MavenDelegateLog(getLog()), (s) -> getLog().info(s));
        } catch (IOException e) {
            throw new MojoExecutionException(String.format("failed to create venv %s", venvDirectory), e);
        }
    }

    private void delete(Path dir) throws MojoExecutionException {
        try {
            try (var s = Files.walk(dir)) {
                s.sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
            }
        } catch (IOException e) {
            new MojoExecutionException(String.format("failed to delete %s", dir),  e);
        }
    }

    private Path getLauncherPath() {
        return Paths.get(project.getBuild().getDirectory(), LAUNCHER_NAME);
    }

    private static String getGraalPyVersion(MavenProject project) throws IOException {
        DefaultArtifact a = (DefaultArtifact) getGraalPyArtifact(project);
        String version = a.getVersion();
        if(a.isSnapshot()) {
            // getVersion for a snapshot artefact returns base version + timestamp - e.g. 24.2.0-20240808.200816-1
            // and there might be snapshot artefacts with different timestamps in the repository.
            // We should use $baseVersion + "-SNAPSHOT" as maven is in such case
            // able to properly resolve all project artefacts.
            version = a.getBaseVersion();
            if(!version.endsWith("-SNAPSHOT")) {
                // getBaseVersion is expected to return a version without any additional metadata, e.g. 24.2.0-20240808.200816-1 -> 24.2.0,
                // but also saw getBaseVersion() already returning version with -SNAPSHOT suffix
                version = version + "-SNAPSHOT";
            }
        }
        return version;
    }

    private static Artifact getGraalPyArtifact(MavenProject project) throws IOException {
        var projectArtifacts = resolveProjectDependencies(project);
        Artifact graalPyArtifact = projectArtifacts.stream().
                filter(a -> isPythonArtifact(a))
                .findFirst()
                .orElse(null);
        return Optional.ofNullable(graalPyArtifact).orElseThrow(() -> new IOException("Missing GraalPy dependency. Please add to your pom either %s:%s or %s:%s".formatted(POLYGLOT_GROUP_ID, PYTHON_COMMUNITY_ARTIFACT_ID, POLYGLOT_GROUP_ID, PYTHON_ARTIFACT_ID)));
    }

    private static boolean isPythonArtifact(Artifact a) {
        return (POLYGLOT_GROUP_ID.equals(a.getGroupId()) || GRAALPY_GROUP_ID.equals(a.getGroupId())) &&
               (PYTHON_COMMUNITY_ARTIFACT_ID.equals(a.getArtifactId()) || PYTHON_ARTIFACT_ID.equals(a.getArtifactId()));
    }

    private static Collection<Artifact> resolveProjectDependencies(MavenProject project) {
        return project.getArtifacts()
                .stream()
                .filter(a -> !"test".equals(a.getScope()))
                .collect(Collectors.toList());
    }

    private Set<String> calculateLauncherClasspath(MavenProject project) throws IOException {
        if(launcherClassPath == null) {
            String version = getGraalPyVersion(project);
            launcherClassPath = new HashSet<String>();

            // 1.) python-launcher and transitive dependencies
            // get the artifact from its direct dependency in graalpy-maven-plugin
            getLog().debug("calculateLauncherClasspath based on " + GRAALPY_GROUP_ID + ":" + GRAALPY_MAVEN_PLUGIN_ARTIFACT_ID + ":" + version);
            DefaultArtifact mvnPlugin = new DefaultArtifact(GRAALPY_GROUP_ID, GRAALPY_MAVEN_PLUGIN_ARTIFACT_ID, version, "compile", "jar", null, new DefaultArtifactHandler("pom"));
            ProjectBuildingResult result = buildProjectFromArtifact(mvnPlugin);
            Artifact graalPyLauncherArtifact = result.getProject().getArtifacts().stream().filter(a -> GRAALPY_GROUP_ID.equals(a.getGroupId()) && PYTHON_LAUNCHER_ARTIFACT_ID.equals(a.getArtifactId()))
                    .findFirst()
                    .orElse(null);
            // python-launcher artifact
            launcherClassPath.add(graalPyLauncherArtifact.getFile().getAbsolutePath());
            // and transitively all its dependencies
            launcherClassPath.addAll(resolveDependencies(graalPyLauncherArtifact));

            // 2.) graalpy dependencies
            Artifact graalPyArtifact = getGraalPyArtifact(project);
            assert graalPyArtifact != null;
            launcherClassPath.addAll(resolveDependencies(graalPyArtifact));
        }
        return launcherClassPath;
    }

    private Set<String> resolveDependencies(Artifact artifact) throws IOException {
        Set<String> dependencies = new HashSet<>();
        ProjectBuildingResult result = buildProjectFromArtifact(artifact);
        for(Dependency d : result.getDependencyResolutionResult().getResolvedDependencies()) {
            addDependency(d, dependencies);
        }
        return dependencies;
    }

    private ProjectBuildingResult buildProjectFromArtifact(Artifact artifact) throws IOException{
        try{
            ProjectBuildingRequest buildingRequest = new DefaultProjectBuildingRequest(session.getProjectBuildingRequest());
            buildingRequest.setProject(null);
            buildingRequest.setResolveDependencies(true);
            buildingRequest.setPluginArtifactRepositories(project.getPluginArtifactRepositories());
            buildingRequest.setRemoteRepositories(project.getRemoteArtifactRepositories());

            return projectBuilder.build(artifact, buildingRequest);
        } catch (ProjectBuildingException e) {
            throw new IOException("Error while building project", e);
        }
    }

    private void addDependency(Dependency d, Set<String> dependencies) {
        File f = d.getArtifact().getFile();
        if(f != null) {
            dependencies.add(f.getAbsolutePath());
        } else {
            getLog().warn("could not retrieve local file for artifact " + d.getArtifact());
        }
    }
}


