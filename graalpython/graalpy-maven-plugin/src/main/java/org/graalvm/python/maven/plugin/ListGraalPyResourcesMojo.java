/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

@Mojo(name = "vfs-index", defaultPhase = LifecyclePhase.PROCESS_RESOURCES)
public class ListGraalPyResourcesMojo extends AbstractMojo {
    @Parameter(defaultValue = "${project}", required = true, readonly = true)
    MavenProject project;

    @Override
    public void execute() throws MojoExecutionException {
        Path vfs = ManageVenvMojo.getVenvDirectory(project).getParent();
        Path filesList = vfs.resolve("fileslist.txt");
        if (!Files.isDirectory(vfs)) {
            getLog().error(String.format("'%s' has to exist and be a directory.\n", vfs.toString()));
        }
        var ret = new HashSet<String>();
        String rootPath = makeDirPath(vfs.toAbsolutePath());
        int rootEndIdx = rootPath.lastIndexOf(File.separator, rootPath.lastIndexOf(File.separator) - 1);
        ret.add(rootPath.substring(rootEndIdx));
        try (var s = Files.walk(vfs)) {
            s.forEach(p -> {
                if (Files.isDirectory(p)) {
                    String dirPath = makeDirPath(p.toAbsolutePath());
                    ret.add(dirPath.substring(rootEndIdx));
                } else if (Files.isRegularFile(p)) {
                    ret.add(p.toAbsolutePath().toString().substring(rootEndIdx));
                }
            });
        } catch (IOException e) {
            throw new MojoExecutionException(String.format("Failed to access '%s'", vfs.toString()), e);
        }
        String[] a = ret.toArray(new String[ret.size()]);
        Arrays.sort(a);
        try (var wr = new FileWriter(filesList.toFile())) {
            for (String f : a) {
                if (f.charAt(0) == '\\') {
                    f = f.replace("\\", "/");
                }
                wr.write(f);
                wr.write("\n");
            }
        } catch (IOException e) {
            throw new MojoExecutionException(String.format("error while creating '%s'\n", filesList), e);
        }
    }

    private static String makeDirPath(Path p) {
        String ret = p.toString();
        if (!ret.endsWith(File.separator)) {
            ret += File.separator;
        }
        return ret;
    }
}
