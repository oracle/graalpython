/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.python;

import org.graalvm.python.dsl.GraalPyExtension;
import org.graalvm.python.dsl.PythonHomeInfo;
import org.graalvm.python.tasks.MetaInfTask;
import org.graalvm.python.tasks.ResourcesTask;
import org.graalvm.python.tasks.VFSFilesListTask;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.internal.provider.DefaultSetProperty;
import org.gradle.api.logging.Logger;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.jvm.tasks.Jar;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.BiFunction;

import static org.graalvm.python.embedding.tools.vfs.VFSUtils.GRAALPY_GROUP_ID;

public abstract class GraalPyGradlePlugin implements Plugin<Project> {
    private static final String LAUNCHER_CONFIGURATION_NAME = "pythonLauncherClasspath";

    private static final String GRADLE_PLUGIN_PROPERTIES = "META-INF/gradle-plugins/org.graalvm.python.properties";
    private static final String PYTHON_LAUNCHER_ARTIFACT_ID = "python-launcher";
    private static final String PYTHON_EMBEDDING_ARTIFACT_ID = "python-embedding";
    private static final String POLYGLOT_GROUP_ID = "org.graalvm.polyglot";
    private static final String POLYGLOT_ARTIFACT_ID = "polyglot";
    private static final String PYTHON_COMMUNITY_ARTIFACT_ID = "python-community";
    private static final String PYTHON_ARTIFACT_ID = "python";
    private static final String GRAALPY_GRADLE_PLUGIN_TASK_GROUP = "graalPy";
    private static final String DEFAULT_RESOURCES_DIRECTORY = "generated" + File.separator + "graalpy" + File.separator + "resources";
    private static final String DEFAULT_FILESLIST_DIRECTORY = "generated" + File.separator + "graalpy" + File.separator + "fileslist";
    private static final String GRAALPY_META_INF_DIRECTORY = "generated" + File.separator + "graalpy" + File.separator + "META-INF";
    private static final String GRAALPY_RESOURCES_TASK = "graalPyResources";
    private static final String GRAALPY_META_INF_TASK_TASK = "graalPyMetaInf";
    private static final String GRAALPY_VFS_FILESLIST_TASK = "graalPyVFSFilesList";

    GraalPyExtension extension;
    Project project;

    private static String graalPyVersion;

    @Override
    public void apply(Project project) {
        this.project = project;
        project.getPluginManager().apply(JavaPlugin.class);

        createExtension();

        var launcherClasspath = createLauncherClasspath();
        var javaPluginExtension = project.getExtensions().getByType(JavaPluginExtension.class);
        var resourcesTask = registerResourcesTask(project, launcherClasspath, extension);
        registerMetaInfTask();

        var vfsFilesListTask = registerCreateVfsFilesListTask(resourcesTask, javaPluginExtension);
        var mainSourceSet = javaPluginExtension.getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME);
        mainSourceSet.getResources().srcDir(resourcesTask);
        addDependencies();

        project.afterEvaluate(proj -> {
            // Run the vfsFilesListTask conditionally only if 'gythonResourcesDirectory' is not set
            if (!extension.getPythonResourcesDirectory().isPresent()) {
                mainSourceSet.getResources().srcDir(vfsFilesListTask);
            }
        });
    }

    /**
     * Registers the VFS files list creation task.
     *
     * @param resourcesTask the resources task
     * @return the task provider
     */
    private TaskProvider<VFSFilesListTask> registerCreateVfsFilesListTask(TaskProvider<ResourcesTask> resourcesTask, JavaPluginExtension javaPluginExtension) {
        var srcDirs = javaPluginExtension.getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME).getResources().getSrcDirs();
        return project.getTasks().register(GRAALPY_VFS_FILESLIST_TASK, VFSFilesListTask.class, t -> {
            t.setGroup(GRAALPY_GRADLE_PLUGIN_TASK_GROUP);
            t.getVfsDirectories().from(resourcesTask.flatMap(ResourcesTask::getOutput));
            srcDirs.forEach(t.getVfsDirectories()::from);
            t.getVfsFilesListOutputDir().convention(project.getLayout().getBuildDirectory().dir(DEFAULT_FILESLIST_DIRECTORY));
        });
    }

    /**
     * Registers the task which generates META-INF metadata.
     */
    private void registerMetaInfTask() {
        var metaInfTask = project.getTasks().register(GRAALPY_META_INF_TASK_TASK, MetaInfTask.class, t -> {
            t.getManifestOutputDir().convention(project.getLayout().getBuildDirectory().dir(GRAALPY_META_INF_DIRECTORY));
            t.setGroup(GRAALPY_GRADLE_PLUGIN_TASK_GROUP);
        });
        project.getTasks().getByName(JavaPlugin.JAR_TASK_NAME, t -> ((Jar) t).getMetaInf().from(metaInfTask));
    }

    /**
     * Registers the task which prepares the Python dependencies.
     *
     * @param launcherClasspath the classpath of the Python launcher
     * @return the resources task provider
     */
    private TaskProvider<ResourcesTask> registerResourcesTask(Project project, Configuration launcherClasspath, GraalPyExtension extension) {
        return project.getTasks().register(GRAALPY_RESOURCES_TASK, ResourcesTask.class, t -> {
            t.getLauncherClasspath().from(launcherClasspath);
            t.getLauncherDirectory().convention(project.getLayout().getBuildDirectory().dir("python-launcher"));

            if(userPythonHome()) {
                t.getLogger().warn("The GraalPy plugin pythonHome configuration setting was deprecated and has no effect anymore.\n" +
                        "For execution in jvm mode, the python language home is always available.\n" +
                        "When building a native executable using GraalVM, then the full python language home is by default embedded into the native executable.\n" +
                        "For more details, please refer to native image options IncludeLanguageResources and CopyLanguageResources documentation.");
            }
            t.getPackages().set(extension.getPackages());

            t.getOutput().convention(extension.getPythonResourcesDirectory().orElse(project.getLayout().getBuildDirectory().dir(DEFAULT_RESOURCES_DIRECTORY)));
            t.getIncludeVfsRoot().convention(extension.getPythonResourcesDirectory().map(d -> false).orElse(true));

            t.setGroup(GRAALPY_GRADLE_PLUGIN_TASK_GROUP);
        });
    }

    private boolean userPythonHome() {
        return !(extension.getPythonHome().getIncludes().get().size() == 1 &&
                extension.getPythonHome().getExcludes().get().size() == 1 &&
                extension.getPythonHome().getIncludes().get().iterator().next().equals(EMPTY_LIST.get(0)) &&
                extension.getPythonHome().getExcludes().get().iterator().next().equals(EMPTY_LIST.get(0)));
    }

    /**
     * Creates the configuration which is used by the Python launcher.
     *
     * @return the launcher classpath configuration
     */
    private Configuration createLauncherClasspath() {
        return project.getConfigurations().create(LAUNCHER_CONFIGURATION_NAME, conf -> {
            conf.setCanBeConsumed(false);
            conf.setCanBeResolved(true);
        });
    }

    private static final List<String> EMPTY_LIST = List.of("--empty--");

    /**
     * Creates the GraalPy extension on the project
     */
    private void createExtension() {
        this.extension = project.getExtensions().create("graalPy", GraalPyExtension.class);
        extension.getPythonHome().getIncludes().convention(EMPTY_LIST);
        extension.getPythonHome().getExcludes().convention(EMPTY_LIST);
        extension.getPackages().convention(Collections.emptyList());
        extension.getCommunity().convention(false);
    }

    /**
     * Adds implicit dependencies to the project based on the extension configuration.
     */
    private void addDependencies() {
        var configurations = project.getConfigurations();
        var implementation = configurations.getByName(JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME);
        implementation.getDependencies().addAllLater(dependencyList(project, extension, (version, community) -> List.of(
                        dependency(POLYGLOT_GROUP_ID, POLYGLOT_ARTIFACT_ID, version),
                        dependency(GRAALPY_GROUP_ID, PYTHON_EMBEDDING_ARTIFACT_ID, version))));
        var runtimeOnly = configurations.getByName(JavaPlugin.RUNTIME_ONLY_CONFIGURATION_NAME);
        runtimeOnly.getDependencies().addAllLater(dependencyList(project, extension, (version, community) -> List.of(
                        dependency(GRAALPY_GROUP_ID, community ? PYTHON_COMMUNITY_ARTIFACT_ID : PYTHON_ARTIFACT_ID, version))));
        var launcher = configurations.getByName(LAUNCHER_CONFIGURATION_NAME);
        launcher.getDependencies().addAllLater(dependencyList(project, extension, (version, community) -> List.of(
                        dependency(GRAALPY_GROUP_ID, PYTHON_LAUNCHER_ARTIFACT_ID, version),
                        dependency(GRAALPY_GROUP_ID, community ? PYTHON_COMMUNITY_ARTIFACT_ID : PYTHON_ARTIFACT_ID, version))));
        makeSureBothEditionsAreNotOnClasspathSimultaneously(configurations);
    }

    private static void makeSureBothEditionsAreNotOnClasspathSimultaneously(ConfigurationContainer configurations) {
        configurations.getByName(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME).getIncoming().afterResolve(resolved -> {
            var deps = resolved.getDependencies();
            boolean hasCommunityEdition = false;
            boolean hasOracleEdition = false;
            for (Dependency dep : deps) {
                if (dep instanceof ExternalModuleDependency emd) {
                    if (GRAALPY_GROUP_ID.equals(emd.getModule().getGroup())) {
                        hasCommunityEdition |= PYTHON_COMMUNITY_ARTIFACT_ID.equals(emd.getModule().getName());
                        hasOracleEdition |= PYTHON_ARTIFACT_ID.equals(emd.getModule().getName());
                    }
                }
            }
            if (hasCommunityEdition && hasOracleEdition) {
                throw new GradleException(
                                "You have both 'org.graalvm.python:python' and 'org.graalvm.python:python-community' on the classpath. " +
                                                "This is likely due to an explicit dependency added, or duplicate dependencies. You may configure " +
                                                "the GraalPy plugin to inject the 'python-community' artifact by using the graalPy { community = true } " +
                                                "configuration block instead.");
            }
        });
    }

    private static String dependency(String groupId, String artifactId, String version) {
        return "%s:%s:%s".formatted(groupId, artifactId, version);
    }

    private Provider<? extends Iterable<Dependency>> dependencyList(Project project, GraalPyExtension extension, BiFunction<String, Boolean, List<String>> generator) {
        var dependencies = project.getDependencies();
        var version = determineGraalPyVersion();
        return project.getProviders().provider(() -> {
            boolean community = extension.getCommunity().convention(false).get();
            return generator.apply(version, community).stream().map(dependencies::create).toList();
        });
    }

    public static String determineGraalPyVersion() {
        String version = graalPyVersion;
        if (version == null) {
            try {
                InputStream propertiesStream = GraalPyGradlePlugin.class.getClassLoader().getResourceAsStream(GRADLE_PLUGIN_PROPERTIES);
                Properties properties = new Properties();
                properties.load(propertiesStream);
                graalPyVersion = version = properties.getProperty("version");
                if (version == null) {
                    throw new NullPointerException();
                }
            } catch (IOException | NullPointerException e) {
                throw new IllegalStateException("Failed to read the GraalPy version from the gradle-plugins/org.graalvm.python.properties file in resources", e);
            }
        }
        return version;
    }
}
