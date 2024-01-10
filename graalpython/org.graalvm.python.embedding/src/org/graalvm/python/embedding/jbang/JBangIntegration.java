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

package org.graalvm.python.embedding.jbang;

import org.graalvm.python.embedding.utils.SubprocessLog;
import org.graalvm.python.embedding.utils.VFSUtils;
import org.graalvm.python.embedding.utils.GraalPyRunner;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class JBangIntegration {
    private static final String PIP = "//PIP";
    private static final String PIP_DROP = "//PIP_DROP";
    private static final String PYTHON_LANGUAGE = "python-language";
    private static final String PYTHON_RESOURCES = "python-resources";
    private static final String PYTHON_LAUNCHER = "python-launcher";
    private static final String GRAALPY_GROUP = String.join(File.separator, "org", "graalvm", "python");
    private static final boolean IS_WINDOWS = System.getProperty("os.name").startsWith("Windows");
    private static final String LAUNCHER = IS_WINDOWS ? "graalpy.exe" : "graalpy.sh";

    private static final SubprocessLog LOG = new SubprocessLog() {
    };

    /**
     *
     * @param temporaryJar temporary JAR file path
     * @param pomFile location of pom.xml representing the projects dependencies
     * @param repositories list of the used repositories
     * @param dependencies list of GAV to Path of artifact/classpath dependencies
     * @param comments comments from the source file
     * @param nativeImage true if --native been requested
     * @return Map<String, Object> map of returns; special keys are "native-image" which is a and
     *         "files" to return native-image to be run and list of files to get written to the
     *         output directory.
     *
     */
    public static Map<String, Object> postBuild(Path temporaryJar,
                    Path pomFile,
                    List<Map.Entry<String, String>> repositories,
                    List<Map.Entry<String, Path>> dependencies,
                    List<String> comments,
                    boolean nativeImage) {
        Path vfs = temporaryJar.resolve("vfs");
        Path venv = vfs.resolve("venv");
        Path home = vfs.resolve("home");

        try {
            Files.createDirectories(vfs);
        } catch (IOException e) {
            throw new Error(e);
        }

        for (String comment : comments) {
            if (comment.startsWith(PIP)) {
                ensureVenv(venv, dependencies);
                try {
                    String[] pkgs = Arrays.stream(comment.substring(PIP.length()).trim().split(" ")).filter(s -> !s.trim().isEmpty()).toArray(String[]::new);
                    GraalPyRunner.runPip(venv, "install", LOG, pkgs);
                } catch (IOException | InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        if (Files.exists(venv)) {
            try {
                Stream<Path> filter = Files.list(venv.resolve("lib")).filter(p -> p.getFileName().toString().startsWith("python3"));
                // on windows, there doesn't have to be python3xxxx folder.
                Optional<Path> libFolderOptional = filter.findFirst();
                Path libFolder = libFolderOptional.orElse(venv.resolve("lib"));
                if (libFolder != null) {
                    var dropFolders = new ArrayList<String>();
                    dropFolders.add("pip");
                    dropFolders.add("setuptools");
                    for (String comment : comments) {
                        if (comment.startsWith(PIP_DROP)) {
                            dropFolders.add(comment.substring(PIP_DROP.length()).trim());
                        }
                    }
                    for (var s : dropFolders) {
                        var folder = libFolder.resolve("site-packages").resolve(s);
                        if (Files.exists(folder)) {
                            try (var f = Files.walk(folder)) {
                                f.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
                            }
                        }
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        if (nativeImage) {
            // include python stdlib in image
            try {
                VFSUtils.copyGraalPyHome(calculateClasspath(dependencies), home, null, null, LOG);
                var niConfig = temporaryJar.resolve("META-INF").resolve("native-image");
                Files.createDirectories(niConfig);
                Files.writeString(niConfig.resolve("native-image.properties"), "Args = -H:-CopyLanguageResources");
                Files.writeString(niConfig.resolve("resource-config.json"), """
                                {
                                  "resources": {
                                    "includes": [
                                      {"pattern": "vfs/.*"}
                                    ]
                                  }
                                }
                                """);
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        try {
            VFSUtils.generateVFSFilesList(vfs);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return new HashMap<>();
    }

    private static Path getLauncherPath(String projectPath) {
        return Paths.get(projectPath, LAUNCHER);
    }

    private static void generateLaunchers(List<Map.Entry<String, Path>> dependencies, String projectPath) {
        System.out.println("Generating GraalPy launchers");
        var launcher = getLauncherPath(projectPath);
        if (!Files.exists(launcher)) {
            var classpath = calculateClasspath(dependencies);
            var java = Paths.get(System.getProperty("java.home"), "bin", "java");
            if (!IS_WINDOWS) {
                var script = String.format("""
                                #!/usr/bin/env bash
                                %s -classpath %s %s --python.Executable="$0" "$@"
                                """,
                                java,
                                String.join(File.pathSeparator, classpath),
                                "com.oracle.graal.python.shell.GraalPythonMain");
                try {
                    Path parent = launcher.getParent();
                    if (parent != null) {
                        Files.createDirectories(parent);
                    }
                    Files.writeString(launcher, script);
                    var perms = Files.getPosixFilePermissions(launcher);
                    perms.addAll(List.of(PosixFilePermission.OWNER_EXECUTE, PosixFilePermission.GROUP_EXECUTE, PosixFilePermission.OTHERS_EXECUTE));
                    Files.setPosixFilePermissions(launcher, perms);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            } else {
                // on windows, generate a venv launcher
                var script = String.format("""
                                import os, shutil, struct, venv
                                from pathlib import Path
                                vl = os.path.join(venv.__path__[0], 'scripts', 'nt', 'graalpy.exe')
                                tl = os.path.join(r'%s')
                                os.makedirs(Path(tl).parent.absolute(), exist_ok=True)
                                shutil.copy(vl, tl)
                                cmd = r'%s -classpath "%s" %s'
                                pyvenvcfg = os.path.join(os.path.dirname(tl), "pyvenv.cfg")
                                with open(pyvenvcfg, 'w', encoding='utf-8') as f:
                                    f.write('venvlauncher_command = ')
                                    f.write(cmd)
                                """,
                                launcher,
                                java,
                                String.join(File.pathSeparator, classpath),
                                "com.oracle.graal.python.shell.GraalPythonMain");
                File tmp;
                try {
                    tmp = File.createTempFile("create_launcher", ".py");
                    tmp.deleteOnExit();
                    try (var wr = new FileWriter(tmp)) {
                        wr.write(script);
                    }
                    GraalPyRunner.run(calculateClasspath(dependencies), LOG, tmp.getAbsolutePath());
                } catch (IOException | InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    private static void ensureVenv(Path venv, List<Map.Entry<String, Path>> dependencies) {
        if (Files.exists(venv)) {
            return;
        }
        Path venvDirectory = venv.toAbsolutePath();
        Path parent = venv.getParent();
        if (parent != null) {
            String parentString = parent.toString();
            generateLaunchers(dependencies, parentString);
            try {
                GraalPyRunner.runLauncher(getLauncherPath(parentString).toString(), LOG, "-m", "venv", venvDirectory.toString(), "--without-pip");
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }
            try {
                GraalPyRunner.runVenvBin(venvDirectory, "graalpy", LOG, "-I", "-m", "ensurepip");
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static Collection<Path> resolveProjectDependencies(List<Map.Entry<String, Path>> dependencies) {
        return dependencies.stream().map(e -> e.getValue()).collect(Collectors.toList());
    }

    private static void getGraalPyArtifact(List<Map.Entry<String, Path>> dependencies, String aid) {
        var projectArtifacts = resolveProjectDependencies(dependencies);
        Path parent;
        Path fileName;
        for (var a : projectArtifacts) {
            parent = a.getParent();
            if (parent != null) {
                fileName = a.getFileName();
                if (fileName != null && parent.toString().contains(GRAALPY_GROUP) && fileName.toString().contains(aid)) {
                    return;
                }
            }
        }
        throw new RuntimeException(String.format("Missing GraalPy dependency %s:%s. Please add it to your pom", GRAALPY_GROUP, aid));
    }

    private static HashSet<String> calculateClasspath(List<Map.Entry<String, Path>> dependencies) {
        var classpath = new HashSet<String>();
        getGraalPyArtifact(dependencies, PYTHON_LANGUAGE);
        getGraalPyArtifact(dependencies, PYTHON_LAUNCHER);
        getGraalPyArtifact(dependencies, PYTHON_RESOURCES);
        for (var r : resolveProjectDependencies(dependencies)) {
            classpath.add(r.toAbsolutePath().toString());
        }
        return classpath;
    }
}
