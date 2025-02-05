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

package org.graalvm.python.embedding.test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Set;

import org.graalvm.python.embedding.tools.exec.BuildToolLog;
import org.graalvm.python.embedding.tools.vfs.VFSUtils;
import org.graalvm.python.embedding.tools.vfs.VFSUtils.Launcher;

public final class EmbeddingTestUtils {
    private EmbeddingTestUtils() {
    }

    public static void createVenv(Path venvDir, String graalPyVersion, BuildToolLog log, String... packages) throws IOException {
        createVenv(venvDir, graalPyVersion, log, null, null, null, null, null, packages);
    }

    public static void createVenv(Path venvDir, String graalPyVersion, BuildToolLog log, Path requirements,
                    String inconsistentPackagesError, String wrongPackageVersionError, String missingRequirementsFileWarning, String packagesListChangedError,
                    String... packages)
                    throws IOException {
        try {
            info(log, "<<<<<< create test venv %s <<<<<<", venvDir);

            Launcher launcher = createLauncher(venvDir);
            if (requirements != null) {
                VFSUtils.createVenv(venvDir, Arrays.asList(packages), requirements,
                                inconsistentPackagesError, wrongPackageVersionError, missingRequirementsFileWarning, packagesListChangedError,
                                launcher, graalPyVersion, log);
            } else {
                VFSUtils.createVenv(venvDir, Arrays.asList(packages), launcher, graalPyVersion, log);
            }
        } catch (RuntimeException e) {
            System.err.println(getClasspath());
            throw e;
        } finally {
            info(log, ">>>>>> create test venv %s >>>>>>", venvDir);
        }
    }

    private static void info(BuildToolLog log, String txt, Object... args) {
        if (log.isInfoEnabled()) {
            log.info(String.format(txt, args));
        }
    }

    public static Launcher createLauncher(Path venvDir) {
        return new Launcher(venvDir.getParent().resolve(VFSUtils.LAUNCHER_NAME)) {
            @Override
            public Set<String> computeClassPath() {
                return getClasspath();
            }
        };
    }

    public static void deleteDirOnShutdown(Path tmpdir) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                delete(tmpdir);
            } catch (IOException e) {
            }
        }));
    }

    public static void delete(Path dir) throws IOException {
        try (var fs = Files.walk(dir)) {
            fs.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
        }
    }

    private static Set<String> getClasspath() {
        var sb = new ArrayList<String>();
        var modPath = System.getProperty("jdk.module.path");
        if (modPath != null) {
            sb.add(modPath);
        }
        var classPath = System.getProperty("java.class.path");
        if (classPath != null) {
            sb.add(classPath);
        }
        var cp = String.join(File.pathSeparator, sb);
        return Set.copyOf(Arrays.stream(cp.split(File.pathSeparator)).toList());
    }
}
