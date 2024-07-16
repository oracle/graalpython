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
package org.graalvm.python.tasks;

import org.graalvm.python.GradleLogger;
import org.graalvm.python.embedding.tools.vfs.VFSUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.*;
import org.gradle.api.tasks.*;
import org.gradle.api.tasks.Optional;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

import static org.graalvm.python.GraalPyGradlePlugin.getGraalPyDependency;
import static org.graalvm.python.GraalPyGradlePlugin.getGraalPyVersion;
import static org.graalvm.python.embedding.tools.vfs.VFSUtils.GRAALPY_GROUP_ID;
import static org.graalvm.python.embedding.tools.vfs.VFSUtils.LAUNCHER_NAME;
import static org.graalvm.python.embedding.tools.vfs.VFSUtils.VFS_HOME;
import static org.graalvm.python.embedding.tools.vfs.VFSUtils.VFS_ROOT;
import static org.graalvm.python.embedding.tools.vfs.VFSUtils.VFS_VENV;


public abstract class ResourcesTask extends DefaultTask {

    private Set<String> launcherClassPath;

    @Input
    @Optional
    public abstract Property<Boolean> getIncludeVfsRoot();

    @Input
    public abstract ListProperty<String> getPackages();

    @Input
    public abstract ListProperty<String> getIncludes();

    @Input
    public abstract ListProperty<String> getExcludes();

    @OutputDirectory
    public abstract DirectoryProperty getOutput();

    @TaskAction
    public void exec() {
        manageHome();
        manageVenv();
    }

    private void manageHome() {
        Path homeDirectory = getHomeDirectory();

        List<String> includes = new ArrayList<>(getIncludes().get());
        List<String> excludes = new ArrayList<>(getExcludes().get());

        try {
            VFSUtils.createHome(homeDirectory, getGraalPyVersion(getProject()), includes, excludes, () -> calculateLauncherClasspath(), GradleLogger.of(getLogger()), (s) -> getLogger().lifecycle(s));
        } catch (IOException e) {
            throw new GradleException(String.format("failed to copy graalpy home %s", homeDirectory), e);
        }
    }

    private void manageVenv() {
        List<String> packages = getPackages().getOrElse(null);
        try {
            VFSUtils.createVenv(getVenvDirectory(), new ArrayList<String>(packages), getLauncherPath(),() ->  calculateLauncherClasspath(), getGraalPyVersion(getProject()), GradleLogger.of(getLogger()), (s) -> getLogger().lifecycle(s));
        } catch (IOException e) {
            throw new GradleException(String.format("failed to create venv %s", getVenvDirectory()), e);
        }
    }

    private Set<String> calculateLauncherClasspath() {
        if (launcherClassPath == null) {
            var addedPluginDependency = getProject().getConfigurations().getByName("runtimeClasspath").getAllDependencies().stream().filter(d -> d.getGroup().equals(GRAALPY_GROUP_ID) && d.getName().equals("python-launcher") && d.getVersion().equals(getGraalPyVersion(getProject()))).findFirst().orElseThrow();
            launcherClassPath = getProject().getConfigurations().getByName("runtimeClasspath").files(addedPluginDependency).stream().map(File::toString).collect(Collectors.toSet());
            launcherClassPath.addAll(getProject().getConfigurations().getByName("runtimeClasspath").files(getGraalPyDependency(getProject())).stream().map(File::toString).collect(Collectors.toSet()));
        }
        return launcherClassPath;
    }

    private Path getLauncherPath() {
        return Paths.get(getProject().getBuildDir().getAbsolutePath(), LAUNCHER_NAME);
    }

    private Path getHomeDirectory() {
        return getResourceDirectory(VFS_HOME);
    }

    private Path getVenvDirectory() {
        return getResourceDirectory(VFS_VENV);
    }

    private Path getResourceDirectory(String type) {
        return Path.of(getOutput().get().getAsFile().toURI()).resolve(getIncludeVfsRoot().getOrElse(true) ? VFS_ROOT : "").resolve(type);
    }

}
