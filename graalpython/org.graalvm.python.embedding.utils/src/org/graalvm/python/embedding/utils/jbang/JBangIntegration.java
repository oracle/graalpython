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

package org.graalvm.python.embedding.utils.jbang;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Proxy;
import java.net.Proxy.Type;
import java.net.ProxySelector;
import java.net.URI;
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
import java.util.stream.Collectors;

public class JBangIntegration {
    private static final String PIP = "//PIP";
    private static final String PIP_DROP = "//PIP_DROP";
    private static final String PYTHON_LANGUAGE = "python-language";
    private static final String PYTHON_RESOURCES = "python-resources";
    private static final String PYTHON_LAUNCHER = "python-launcher";
    private static final String GRAALPY_GROUP = String.join(File.separator, "org", "graalvm", "python");
    private static final boolean IS_WINDOWS = System.getProperty("os.name").startsWith("Windows");
    private static final String LAUNCHER = IS_WINDOWS ? "graalpy.exe" : "graalpy.sh";
    private static final String BIN_DIR = IS_WINDOWS ? "Scripts" : "bin";
    private static final String EXE_SUFFIX = IS_WINDOWS ? ".exe" : "";

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
                runPip(venv, "install", comment.substring(PIP.length()).trim());
            }
        }
        var dropFolders = new ArrayList<String>();
        dropFolders.add("pip");
        dropFolders.add("setuptools");
        for (String comment : comments) {
            if (comment.startsWith(PIP_DROP)) {
                dropFolders.add(comment.substring(PIP_DROP.length()).trim());
            }
        }
        if (Files.exists(venv)) {
            try {
                Path libFolder = Files.list(venv.resolve("lib")).filter(p -> p.getFileName().toString().startsWith("python3")).findFirst().get();
                if (libFolder != null) {
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
            runGraalPy(dependencies, "-c", String.format("__import__('shutil').copytree(__graalpython__.home, '%s', dirs_exist_ok=True)", home.toAbsolutePath().toString()));
            var niConfig = temporaryJar.resolve("META-INF").resolve("native-image");
            try {
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
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        generateFilelist(vfs);

        return new HashMap<>();
    }

    private static void generateFilelist(Path vfs) {
        Path filesList = vfs.resolve("fileslist.txt");
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
            throw new RuntimeException(e);
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
            throw new RuntimeException(e);
        }
    }

    private static String makeDirPath(Path p) {
        String ret = p.toString();
        if (!ret.endsWith(File.separator)) {
            ret += File.separator;
        }
        return ret;
    }

    private static Path getLauncherPath(String projectPath) {
        return Paths.get(projectPath, LAUNCHER);
    }

    private static void generateLaunchers(List<Map.Entry<String, Path>> dependencies, String projectPath) {
        System.out.println("Generating GraalPy launchers");
        var launcher = getLauncherPath(projectPath);
        if (!Files.exists(launcher)) {
            if (!IS_WINDOWS) {
                var classpath = calculateClasspath(dependencies);
                var java = Paths.get(System.getProperty("java.home"), "bin", "java");
                var script = String.format("""
                                #!/usr/bin/env bash
                                source="${BASH_SOURCE[0]}"
                                source="$(readlink "$source")";
                                location="$( cd -P "$( dirname "$source" )" && pwd )"
                                args="$(printf "\\v")--python.Executable=$0"
                                for var in "$@"; do args="${args}$(printf "\\v")${var}"; done
                                curdir=`pwd`
                                export GRAAL_PYTHON_ARGS="${args}$(printf "\\v")"
                                %s -classpath %s %s
                                """,
                                java,
                                String.join(File.pathSeparator, classpath),
                                "com.oracle.graal.python.shell.GraalPythonMain");
                try {
                    Files.createDirectories(launcher.getParent());
                    Files.writeString(launcher, script);
                    var perms = Files.getPosixFilePermissions(launcher);
                    perms.addAll(List.of(PosixFilePermission.OWNER_EXECUTE, PosixFilePermission.GROUP_EXECUTE, PosixFilePermission.OTHERS_EXECUTE));
                    Files.setPosixFilePermissions(launcher, perms);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            } else {
                // on windows, generate a venv launcher that executes our mvn target
                var script = String.format("""
                                import os, shutil, struct, venv
                                from pathlib import Path
                                vl = os.path.join(venv.__path__[0], 'scripts', 'nt', 'graalpy.exe')
                                tl = os.path.join(r'%s')
                                os.makedirs(Path(tl).parent.absolute())
                                shutil.copy(vl, tl)
                                cmd = r'mvn.cmd -f "%s" graalpy:exec "-Dexec.workingdir=%s"'
                                pyvenvcfg = os.path.join(os.path.dirname(tl), "pyvenv.cfg")
                                with open(pyvenvcfg, 'w', encoding='utf-8') as f:
                                    f.write('venvlauncher_command = ')
                                    f.write(cmd)
                                """,
                                launcher,
                                Paths.get(projectPath, "pom.xml").toString(),
                                projectPath);
                File tmp;
                try {
                    tmp = File.createTempFile("create_launcher", ".py");
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                tmp.deleteOnExit();
                try (var wr = new FileWriter(tmp)) {
                    wr.write(script);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                runGraalPy(dependencies, tmp.getAbsolutePath());
            }
        }
    }

    private static void ensureVenv(Path venv, List<Map.Entry<String, Path>> dependencies) {
        if (Files.exists(venv)) {
            return;
        }
        Path venvDirectory = venv.toAbsolutePath();
        generateLaunchers(dependencies, venvDirectory.getParent().toString());
        runLauncher(venvDirectory.getParent().toString(), "-m", "venv", venvDirectory.toString(), "--without-pip");
        runVenvBin(venvDirectory, "graalpy", List.of("-I", "-m", "ensurepip"));
    }

    private static void runLauncher(String projectPath, String... args) {
        var cmd = new ArrayList<String>();
        cmd.add(getLauncherPath(projectPath).toString());
        cmd.addAll(List.of(args));
        System.out.println(String.join(" ", cmd));
        var pb = new ProcessBuilder(cmd);
        runProcess(pb);
    }

    private static void runPip(Path venvDirectory, String command, String pkg) {
        var newArgs = new ArrayList<String>();
        newArgs.add("-m");
        newArgs.add("pip");
        // addProxy(newArgs);
        newArgs.add(command);
        newArgs.add(pkg);

        runVenvBin(venvDirectory, "graalpy", newArgs);
    }

    private static void runVenvBin(Path venvDirectory, String bin, Collection<String> args) {
        var cmd = new ArrayList<String>();
        cmd.add(venvDirectory.resolve(BIN_DIR).resolve(bin + EXE_SUFFIX).toString());
        cmd.addAll(args);
        System.out.println(String.join(" ", cmd));
        var pb = new ProcessBuilder(cmd);
        runProcess(pb);
    }

    private static void runGraalPy(List<Map.Entry<String, Path>> dependencies, String... args) {
        var classpath = calculateClasspath(dependencies);
        var workdir = System.getProperty("exec.workingdir");
        var java = Paths.get(System.getProperty("java.home"), "bin", "java");
        var cmd = new ArrayList<String>();
        cmd.add(java.toString());
        cmd.add("-classpath");
        cmd.add(String.join(File.pathSeparator, classpath));
        cmd.add("com.oracle.graal.python.shell.GraalPythonMain");
        cmd.addAll(List.of(args));
        var pb = new ProcessBuilder(cmd);
        if (workdir != null) {
            pb.directory(new File(workdir));
            throw new RuntimeException("Not satisfied pip");
        }
        System.out.println(String.format("Running GraalPy: %s", String.join(" ", cmd)));
        runProcess(pb);
    }

    private static void addProxy(ArrayList<String> args) {
        // pip takes environment variables http_proxy and https_proxy, if there set
        if (System.getenv("http_proxy") == null && System.getenv("https_proxy") == null) {
            // if not set, try the same way as jbang
            ProxySelector proxySelector = ProxySelector.getDefault();
            List<Proxy> proxies = proxySelector.select(URI.create("https://pypi.org"));

            String proxyAddr = null;
            for (Proxy proxy : proxies) {
                if (proxy.type() == Type.HTTP) {
                    proxyAddr = fixProtocol(proxy.address().toString(), "http");
                    break;
                }
            }
            if (proxyAddr != null) {
                args.add("--proxy");
                args.add(proxyAddr);
            }
        }
    }

    private static void runProcess(ProcessBuilder pb) {
        Process process;
        try {
            pb.redirectError();
            pb.redirectOutput();
            process = pb.start();
            Thread outputReader = new Thread(() -> {
                try (InputStream is = process.getInputStream();
                                BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        System.out.println(line); // Write the ouput line
                    }
                } catch (IOException e) {
                    // Do noting for now. Probably is not good idea to stop the
                    // execution in this moment
                }
            });

            outputReader.start(); // start of ouput reader

            process.waitFor(); // waiting for the build process
            outputReader.join(); // waiging to terminate for the ouputReaded process

            if (process.exitValue() != 0) {
                // if there are some errors, print the error outut
                printErrors(process);
                // and terminate the build process
                throw new RuntimeException(String.format("Running command: '%s' ended with code %d.See the error output above.",
                                String.join(" ", pb.command()),
                                process.exitValue()));
            }
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Ensures that the proxy url has a protocol.
     * 
     * @param proxyAddress proxy server address 
     * @param protocol  usually http or https
     * @return String representation of url of the proxy
     */
    private static String fixProtocol(String proxyAddress, String protocol) {
        return proxyAddress.startsWith(protocol) ? proxyAddress : protocol + "://" + proxyAddress;
    }

    private static Collection<Path> resolveProjectDependencies(List<Map.Entry<String, Path>> dependencies) {
        return dependencies.stream().map(e -> e.getValue()).collect(Collectors.toList());
    }

    private static void getGraalPyArtifact(List<Map.Entry<String, Path>> dependencies, String aid) {
        var projectArtifacts = resolveProjectDependencies(dependencies);
        for (var a : projectArtifacts) {
            if (a.getParent().toString().contains(GRAALPY_GROUP) && a.getFileName().toString().contains(aid)) {
                return;
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

    private static void printErrors(Process process) throws IOException {
        InputStream errorStream = process.getErrorStream();
        InputStreamReader errorStreamReader = new InputStreamReader(errorStream);
        BufferedReader errorBufferedReader = new BufferedReader(errorStreamReader);

        String line;
        System.out.println("========== Error Output: =========");
        while ((line = errorBufferedReader.readLine()) != null) {
            System.out.println(line);
        }
        System.out.println("==================================");
    }
}
