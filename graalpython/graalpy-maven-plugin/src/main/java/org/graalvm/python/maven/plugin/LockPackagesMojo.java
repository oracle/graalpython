/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

import org.graalvm.python.embedding.tools.vfs.VFSUtils;

import java.io.IOException;
import java.nio.file.Path;

@Mojo(name = "lock-packages",
        requiresDependencyCollection = ResolutionScope.COMPILE_PLUS_RUNTIME,
        requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME)
public class LockPackagesMojo extends AbstractGraalPyMojo {

    public void execute() throws MojoExecutionException {
        preExec(false);
        checkEmptyPackages();
        
        manageVenv();
        listGraalPyResources();
        
        postExec();
    }

    protected void manageVenv() throws MojoExecutionException {
        Path venvDirectory = getVenvDirectory();
        MavenDelegateLog log = new MavenDelegateLog(getLog());
        Path requirements = getLockFile();

        try {
            VFSUtils.lockPackages(venvDirectory, packages, requirements, LOCK_FILE_HEADER, WRONG_PACKAGE_VERSION_FORMAT_ERROR, createLauncher(), getGraalPyVersion(project), log);
        } catch (IOException e) {
            throw new MojoExecutionException(String.format("failed to create venv %s", venvDirectory), e);
        }
    }

    private void checkEmptyPackages() throws MojoExecutionException {
        if((packages == null || packages.isEmpty())) {
            getLog().error("");
            getLog().error("In order to run the lock-packages goal there have to be python packages declared in the graalpy-maven-plugin configuration.");
            getLog().error("");
            getLog().error("NOTE that the <configuration> section has to be declared for the whole graalpy-maven-plugin");
            getLog().error("and not specifically for the process-graalpy-resources execution goal.");
            getLog().error("");
            getLog().error("Please add the <packages> section to your configuration as follows:");
            getLog().error("<plugin>");
            getLog().error("  <groupId>org.graalvm.python</groupId>");
            getLog().error("  <artifactId>graalpy-maven-plugin</artifactId>");
            getLog().error("  <configuration>");
            getLog().error("    <packages>");
            getLog().error("      <package>{package_name}=={package_version}</package>");
            getLog().error("    </packages>");
            getLog().error("    ...");
            getLog().error("  </configuration>");
            getLog().error("");

            getLog().error("For more information, please refer to https://github.com/oracle/graalpython/blob/master/docs/user/Embedding-Build-Tools.md");
            getLog().error("");

            throw new MojoExecutionException("missing python packages in plugin configuration");
        }
    }
}
