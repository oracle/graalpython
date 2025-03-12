/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.python.dsl.GraalPyExtension;
import org.gradle.api.GradleException;
import org.gradle.api.tasks.*;

import java.io.IOException;
import java.nio.file.Path;

import org.graalvm.python.embedding.tools.vfs.VFSUtils;
import org.graalvm.python.embedding.tools.vfs.VFSUtils.PackagesChangedException;

/**
 * This task is responsible installing the dependencies which were requested by the user.
 * This is either done in generated resources folder or in external directory provided by the user
 * in {@link GraalPyExtension#getExternalDirectory()}.
 *
 * <p/>
 * In scope of this task:
 * <ol>
 *     <li>The GraalPy launcher is set up.</li>
 *     <li>A python venv is created.</li>
 *     <li>Python packages are installed into the venv.</li>
 * </ol>
 *
 */
@CacheableTask
public abstract class InstallPackagesTask extends AbstractPackagesTask {

    private static final String PACKAGES_CHANGED_ERROR = """
        Install of python packages is based on lock file %s,
        but packages and their version constraints in graalpy-gradle-plugin configuration are different then previously used to generate the lock file.
        
        Packages currently declared in graalpy-gradle-plugin configuration: %s
        Packages which were used to generate the lock file: %s
         
        The lock file has to be refreshed by running the gradle task 'graalPyLockPackages'.
        
        For more information, please refer to https://www.graalvm.org/latest/reference-manual/python/Embedding-Build-Tools#Python-Dependency-Management
               
        """;

    protected static final String MISSING_LOCK_FILE_WARNING = """
        
        WARNING: The list of installed Python packages does not match the packages specified in the graalpy-maven-plugin configuration.
        WARNING: This could indicate that either extra dependencies were installed or some packages were installed with a more specific versions than declared.
         
        WARNING: In such cases, it is strongly recommended to lock the Python dependencies by executing the Gradle task 'graalPyLockPackages'.
        
        For more details on managing Python dependencies, please refer to https://www.graalvm.org/latest/reference-manual/python/Embedding-Build-Tools#Python-Dependency-Management
        
        """;

    @TaskAction
    public void exec() throws GradleException {
        Path venvDirectory = getVenvDirectory().get().getAsFile().toPath();
        Path lockFilePath = getLockFilePath();
        try {
            VFSUtils.createVenv(venvDirectory, getPackages().get(), lockFilePath, MISSING_LOCK_FILE_WARNING, createLauncher(), getPolyglotVersion().get(), getLog());
        } catch(PackagesChangedException pce) {
            String pluginPkgsString = pce.getPluginPackages().isEmpty() ? "None" : String.join(", ", pce.getPluginPackages());
            String lockFilePkgsString = pce.getLockFilePackages().isEmpty() ? "None" : String.join(", ", pce.getLockFilePackages());
            throw new GradleException(String.format(PACKAGES_CHANGED_ERROR, lockFilePath, pluginPkgsString, lockFilePkgsString));
        } catch (IOException e) {
            throw new GradleException(String.format("failed to create python virtual environment in %s", venvDirectory), e);
        }
    }
}
