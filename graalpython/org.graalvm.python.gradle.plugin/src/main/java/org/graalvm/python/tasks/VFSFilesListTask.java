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
package org.graalvm.python.tasks;

import org.graalvm.python.embedding.tools.vfs.VFSUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleScriptException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.TreeSet;

import static org.graalvm.python.embedding.tools.vfs.VFSUtils.VFS_ROOT;

@CacheableTask
public abstract class VFSFilesListTask extends DefaultTask {

    public static final String VFS_PREFIX = "org.graalvm.python.vfs";

    /**
     * Directories that will be used as an input for the virtual filesystem contents. The paths
     * should already point to directories that contain the directory with subdirectory named
     * {@code VFS_PREFIX}.
     */
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getVfsDirectories();

    @OutputDirectory
    public abstract DirectoryProperty getVfsFilesListOutputDir();

    @TaskAction
    public void exec() throws IOException {
        Path outputDir = getVfsFilesListOutputDir().get().getAsFile().toPath().resolve(VFS_ROOT);
        Files.createDirectories(outputDir);
        // Sort lines for reproducibility
        var sorted = new TreeSet<String>();
        getVfsDirectories().getElements().get().forEach(location -> {
            var vfsParentDir = location.getAsFile().toPath();
            if (Files.isDirectory(vfsParentDir)) {
                var vfsDir = vfsParentDir.resolve(VFS_PREFIX);
                if (Files.isDirectory(vfsDir)) {
                    try {
                        VFSUtils.generateVFSFilesList(vfsDir, sorted, duplicate -> {
                            this.getLogger().warn("Found duplicate file '{}' in multiple resource directories.", duplicate);
                        });
                    } catch (IOException e) {
                        throw new GradleScriptException(String.format("failed to list files in '%s'", vfsDir), e);
                    }
                }
            }
        });
        try {
            var fileslist = outputDir.resolve("fileslist.txt");
            Files.write(fileslist, sorted);
        } catch (IOException e) {
            throw new GradleScriptException(String.format("failed to generate files list in '%s'", outputDir), e);
        }
    }
}
