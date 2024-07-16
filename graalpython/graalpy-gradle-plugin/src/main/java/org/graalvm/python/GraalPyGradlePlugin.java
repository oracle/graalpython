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
package org.graalvm.python;

import org.graalvm.python.dsl.GraalPyExtension;
import org.graalvm.python.tasks.GenerateManifestTask;
import org.graalvm.python.tasks.InstallPackagesTask;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.DependencySet;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.jvm.tasks.Jar;
import org.gradle.language.jvm.tasks.ProcessResources;

import java.util.Collections;
import java.util.List;


public abstract class GraalPyGradlePlugin implements Plugin<Project> {
    private static final String PYTHON_LAUNCHER_ARTIFACT_ID = "python-launcher";
    private static final String PYTHON_EMBEDDING_ARTIFACT_ID = "python-embedding";
    public static final String GRAALPY_GROUP_ID = "org.graalvm.python";

    private static final String POLYGLOT_GROUP_ID = "org.graalvm.polyglot";
    private static final String PYTHON_COMMUNITY_ARTIFACT_ID = "python-community";
    private static final String PYTHON_ARTIFACT_ID = "python";
    private static final String GRAALPY_GRADLE_PLUGIN_TASK_GROUP = "graalpy";

    private static final String DEFAULT_WRAPPER_DIRECTORY = "python-generated";

    GraalPyExtension extension;
    Project project;

    @Override
    public void apply(Project project) {
        this.project = project;
        project.getPluginManager().apply(JavaPlugin.class);


        this.extension = project.getExtensions().create("graalPy", GraalPyExtension.class);
        extension.getPythonHome().getIncludes().convention(List.of(".*"));
        extension.getPythonHome().getExcludes().convention(Collections.emptyList());
        extension.getPackages().convention(Collections.emptyList());

        var installPackagesTask = project.getTasks().register("installPackages", InstallPackagesTask.class);


        final var generateManifestTask = project.getTasks().register("generateManifest", GenerateManifestTask.class);
        project.getTasks().getByName(JavaPlugin.JAR_TASK_NAME, t -> ((Jar) t).getMetaInf().from(generateManifestTask));
        generateManifestTask.configure(t -> {
            t.getManifestOutputDir().convention(project.getLayout().getBuildDirectory().dir("GRAAL-META-INF"));
            t.setGroup(GRAALPY_GRADLE_PLUGIN_TASK_GROUP);
        });

        installPackagesTask.configure(t -> {
            t.getIncludes().set(extension.getPythonHome().getIncludes());
            t.getExcludes().set(extension.getPythonHome().getExcludes());
            t.getPackages().set(extension.getPackages());
            t.getIncludeVfsRoot().set(extension.getIncludeVfsRootDir());

            t.getOutput().set(extension.getPythonResourcesDirectory());

            t.setGroup(GRAALPY_GRADLE_PLUGIN_TASK_GROUP);
        });

        project.afterEvaluate(p -> {
            checkAndAddDependencies();

            if (!extension.getPythonResourcesDirectory().isPresent())
                ((ProcessResources) project.getTasks().getByName(JavaPlugin.PROCESS_RESOURCES_TASK_NAME)).with(project.copySpec().from(installPackagesTask));

            // Provide the default value after the isPresent check, otherwise isPresent always returns true
            extension.getPythonResourcesDirectory().convention(project.getLayout().getBuildDirectory().dir(DEFAULT_WRAPPER_DIRECTORY));
        });

    }


    private void checkAndAddDependencies() {
        project.getDependencies().add(JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME, "%s:%s:%s".formatted(GRAALPY_GROUP_ID, PYTHON_LAUNCHER_ARTIFACT_ID, getGraalPyVersion(project)));
        project.getDependencies().add(JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME, "%s:%s:%s".formatted(GRAALPY_GROUP_ID, PYTHON_EMBEDDING_ARTIFACT_ID, getGraalPyVersion(project)));
    }


    public static String getGraalPyVersion(Project project) {
        return getGraalPyDependency(project).getVersion();
    }

    public static Dependency getGraalPyDependency(Project project) {
        return resolveProjectDependencies(project).stream().filter(GraalPyGradlePlugin::isPythonArtifact).findFirst().orElseThrow(() -> new GradleException("Missing GraalPy dependency. Please add to your build.gradle either %s:%s or %s:%s".formatted(POLYGLOT_GROUP_ID, PYTHON_COMMUNITY_ARTIFACT_ID, POLYGLOT_GROUP_ID, PYTHON_ARTIFACT_ID)));
    }

    private static boolean isPythonArtifact(Dependency dependency) {
        return (POLYGLOT_GROUP_ID.equals(dependency.getGroup()) || GRAALPY_GROUP_ID.equals(dependency.getGroup())) &&
                (PYTHON_COMMUNITY_ARTIFACT_ID.equals(dependency.getName()) || PYTHON_ARTIFACT_ID.equals(dependency.getName()));
    }

    private static DependencySet resolveProjectDependencies(Project project) {
        return project.getConfigurations().getByName("implementation").getAllDependencies();
    }


}