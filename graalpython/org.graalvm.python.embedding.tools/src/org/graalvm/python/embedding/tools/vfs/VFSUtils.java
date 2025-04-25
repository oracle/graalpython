/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.python.embedding.tools.vfs;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;

import org.graalvm.python.embedding.tools.exec.GraalPyRunner;
import org.graalvm.python.embedding.tools.exec.SubprocessLog;
import org.graalvm.python.embedding.tools.exec.SubprocessLog.CollectOutputLog;

public final class VFSUtils {

    /**
     * Patterns which should be excluded by default, like .gitignore or SCM files
     * <ul>
     * <li>Misc: &#42;&#42;/&#42;~, &#42;&#42;/#&#42;#, &#42;&#42;/.#&#42;, &#42;&#42;/%&#42;%,
     * &#42;&#42;/._&#42;</li>
     * <li>CVS: &#42;&#42;/CVS, &#42;&#42;/CVS/&#42;&#42;, &#42;&#42;/.cvsignore</li>
     * <li>RCS: &#42;&#42;/RCS, &#42;&#42;/RCS/&#42;&#42;</li>
     * <li>SCCS: &#42;&#42;/SCCS, &#42;&#42;/SCCS/&#42;&#42;</li>
     * <li>VSSercer: &#42;&#42;/vssver.scc</li>
     * <li>MKS: &#42;&#42;/project.pj</li>
     * <li>SVN: &#42;&#42;/.svn, &#42;&#42;/.svn/&#42;&#42;</li>
     * <li>GNU: &#42;&#42;/.arch-ids, &#42;&#42;/.arch-ids/&#42;&#42;</li>
     * <li>Bazaar: &#42;&#42;/.bzr, &#42;&#42;/.bzr/&#42;&#42;</li>
     * <li>SurroundSCM: &#42;&#42;/.MySCMServerInfo</li>
     * <li>Mac: &#42;&#42;/.DS_Store</li>
     * <li>Serena Dimension: &#42;&#42;/.metadata, &#42;&#42;/.metadata/&#42;&#42;</li>
     * <li>Mercurial: &#42;&#42;/.hg, &#42;&#42;/.hg/&#42;&#42;</li>
     * <li>Git: &#42;&#42;/.git, &#42;&#42;/.git/&#42;&#42;, &#42;&#42;/.gitignore</li>
     * <li>Bitkeeper: &#42;&#42;/BitKeeper, &#42;&#42;/BitKeeper/&#42;&#42;, &#42;&#42;/ChangeSet,
     * &#42;&#42;/ChangeSet/&#42;&#42;</li>
     * <li>Darcs: &#42;&#42;/_darcs, &#42;&#42;/_darcs/&#42;&#42;, &#42;&#42;/.darcsrepo,
     * &#42;&#42;/.darcsrepo/&#42;&#42;&#42;&#42;/-darcs-backup&#42;, &#42;&#42;/.darcs-temp-mail
     * </ul>
     *
     */
    private static final String[] DEFAULT_EXCLUDES = {
                    // Miscellaneous typical temporary files
                    "**/*~",
                    "**/#*#",
                    "**/.#*",
                    "**/%*%",
                    "**/._*",

                    // CVS
                    "**/CVS",
                    "**/CVS/**",
                    "**/.cvsignore",

                    // RCS
                    "**/RCS",
                    "**/RCS/**",

                    // SCCS
                    "**/SCCS",
                    "**/SCCS/**",

                    // Visual SourceSafe
                    "**/vssver.scc",

                    // MKS
                    "**/project.pj",

                    // Subversion
                    "**/.svn",
                    "**/.svn/**",

                    // Arch
                    "**/.arch-ids",
                    "**/.arch-ids/**",

                    // Bazaar
                    "**/.bzr",
                    "**/.bzr/**",

                    // SurroundSCM
                    "**/.MySCMServerInfo",

                    // Mac
                    "**/.DS_Store",

                    // Serena Dimensions Version 10
                    "**/.metadata",
                    "**/.metadata/**",

                    // Mercurial
                    "**/.hg",
                    "**/.hg/**",

                    // git
                    "**/.git",
                    "**/.git/**",
                    "**/.gitignore",

                    // BitKeeper
                    "**/BitKeeper",
                    "**/BitKeeper/**",
                    "**/ChangeSet",
                    "**/ChangeSet/**",

                    // darcs
                    "**/_darcs",
                    "**/_darcs/**",
                    "**/.darcsrepo",
                    "**/.darcsrepo/**",
                    "**/-darcs-backup*",
                    "**/.darcs-temp-mail"
    };

    public static final String VFS_ROOT = "org.graalvm.python.vfs";
    public static final String VFS_VENV = "venv";
    public static final String VFS_FILESLIST = "fileslist.txt";

    public static final String GRAALPY_GROUP_ID = "org.graalvm.python";

    private static final String NATIVE_IMAGE_RESOURCES_CONFIG = """
                    {
                      "resources": {
                        "includes": [
                          {"pattern": "$vfs/.*"}
                        ]
                      }
                    }
                    """;

    private static final boolean IS_WINDOWS = System.getProperty("os.name").startsWith("Windows");

    public static final String LAUNCHER_NAME = IS_WINDOWS ? "graalpy.exe" : "graalpy.sh";

    private static final String GRAALPY_MAIN_CLASS = "com.oracle.graal.python.shell.GraalPythonMain";

    public static void writeNativeImageConfig(Path metaInfRoot, String pluginId) throws IOException {
        writeNativeImageConfig(metaInfRoot, pluginId, VFS_ROOT);
    }

    public static void writeNativeImageConfig(Path metaInfRoot, String pluginId, String vfsRoot) throws IOException {
        Path p = metaInfRoot.resolve(Path.of("native-image", GRAALPY_GROUP_ID, pluginId));
        write(p.resolve("resource-config.json"), NATIVE_IMAGE_RESOURCES_CONFIG.replace("$vfs", vfsRoot));
    }

    private static void write(Path config, String txt) throws IOException {
        try {
            createParentDirectories(config);
            Files.writeString(config, txt, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new IOException(String.format("failed to write %s", config), e);
        }
    }

    private static void createParentDirectories(Path path) throws IOException {
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }

    public static void generateVFSFilesList(Path resourcesRoot, Path vfs) throws IOException {
        TreeSet<String> entriesSorted = new TreeSet<>();
        generateVFSFilesList(resourcesRoot, vfs, entriesSorted, null);
        Path filesList = vfs.resolve(VFS_FILESLIST);
        Files.write(filesList, entriesSorted);
    }

    public static void generateVFSFilesList(Path vfs) throws IOException {
        generateVFSFilesList(null, vfs);
    }

    // Note: forward slash is not valid file/dir name character on Windows,
    // but backslash is valid file/dir name character on UNIX
    private static final boolean REPLACE_BACKSLASHES = File.separatorChar == '\\';

    private static String normalizeResourcePath(String path) {
        return REPLACE_BACKSLASHES ? path.replace("\\", "/") : path;
    }

    /**
     * Adds the VFS filelist entries to given set. Caller may provide a non-empty set.
     */
    public static void generateVFSFilesList(Path vfs, Set<String> ret, Consumer<String> duplicateHandler) throws IOException {
        generateVFSFilesList(null, vfs, ret, duplicateHandler);
    }

    public static void generateVFSFilesList(Path resourcesRoot, Path vfs, Set<String> ret, Consumer<String> duplicateHandler) throws IOException {
        if (!Files.isDirectory(vfs)) {
            throw new IOException(String.format("'%s' has to exist and be a directory.\n", vfs));
        }
        String rootPath = makeDirPath(vfs.toAbsolutePath());
        int rootEndIdx;
        if (resourcesRoot == null) {
            // we assume the resources root is the parent
            rootEndIdx = rootPath.lastIndexOf(File.separator, rootPath.lastIndexOf(File.separator) - 1);
        } else {
            String resRootPath = makeDirPath(resourcesRoot);
            rootEndIdx = resRootPath.length() - 1;
        }
        try (var s = Files.walk(vfs)) {
            s.forEach(p -> {
                if (!shouldPathBeExcluded(p)) {
                    String entry = null;
                    if (Files.isDirectory(p)) {
                        String dirPath = makeDirPath(p.toAbsolutePath());
                        entry = dirPath.substring(rootEndIdx);
                    } else if (Files.isRegularFile(p)) {
                        entry = p.toAbsolutePath().toString().substring(rootEndIdx);
                    }
                    if (entry != null) {
                        entry = normalizeResourcePath(entry);
                        if (!ret.add(entry) && duplicateHandler != null) {
                            duplicateHandler.accept(entry);
                        }
                    }
                }
            });
        }
    }

    private static boolean shouldPathBeExcluded(Path path) {
        for (String glob : DEFAULT_EXCLUDES) {
            var matcher = FileSystems.getDefault().getPathMatcher("glob:" + glob);
            if (matcher.matches(path)) {
                return true;
            }
        }
        return false;
    }

    private static String makeDirPath(Path p) {
        String ret = p.toString();
        if (!ret.endsWith(File.separator)) {
            ret += File.separator;
        }
        return ret;
    }

    @FunctionalInterface
    public interface LauncherClassPath {
        Set<String> get() throws IOException;
    }

    public interface Log {
        void info(String s);
    }

    public static void delete(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        try {
            try (var s = Files.walk(dir)) {
                s.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
            }
        } catch (IOException e) {
            throw new IOException(String.format("failed to delete %s", dir), e);
        }
    }

    public static void createVenv(Path venvDirectory, List<String> packages, Path launcher, LauncherClassPath launcherClassPath, String graalPyVersion, SubprocessLog subprocessLog, Log log)
                    throws IOException {
        Path launcherPath = launcher;
        String externalLauncher = System.getProperty("graalpy.vfs.venvLauncher");
        if (externalLauncher == null || externalLauncher.trim().isEmpty()) {
            generateLaunchers(launcherPath, launcherClassPath, subprocessLog, log);
        } else {
            launcherPath = Path.of(externalLauncher);
        }

        if (packages != null) {
            trim(packages);
        }

        List<String> installedPackages = new ArrayList<>();
        var tag = venvDirectory.resolve("contents");

        if (Files.exists(venvDirectory)) {
            checkLauncher(venvDirectory, launcherPath, log);

            if (Files.isReadable(tag)) {
                List<String> lines = null;
                try {
                    lines = Files.readAllLines(tag);
                } catch (IOException e) {
                    throw new IOException(String.format("failed to read tag file %s", tag), e);
                }
                if (lines.isEmpty() || !graalPyVersion.equals(lines.get(0))) {
                    log.info(String.format("Stale GraalPy venv, updating to %s", graalPyVersion));
                    delete(venvDirectory);
                } else {
                    for (int i = 1; i < lines.size(); i++) {
                        installedPackages.add(lines.get(i));
                    }
                }
            }
        }

        if (!Files.exists(venvDirectory)) {
            log.info(String.format("Creating GraalPy %s venv", graalPyVersion));
            runLauncher(launcherPath.toString(), subprocessLog, "-m", "venv", venvDirectory.toString(), "--without-pip");
            runVenvBin(venvDirectory, "graalpy", subprocessLog, "-I", "-m", "ensurepip");
        }

        Iterable<String> frozenPkgs = null;
        if (packages != null) {
            boolean needsUpdate = false;
            needsUpdate |= deleteUnwantedPackages(venvDirectory, packages, installedPackages, subprocessLog);
            needsUpdate |= installWantedPackages(venvDirectory, packages, installedPackages, subprocessLog);
            if (needsUpdate) {
                var freezeLog = new CollectOutputLog();
                runPip(venvDirectory, "freeze", freezeLog, "--local");
                frozenPkgs = freezeLog.getOutput();
            }
        }

        try {
            Files.write(tag, List.of(graalPyVersion), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            Files.write(tag, packages, StandardOpenOption.APPEND);
            if (frozenPkgs != null) {
                String toWrite = "# Generated by GraalPy Maven or Gradle plugin using pip freeze\n" +
                                "# This file is used by GraalPy VirtualFileSystem\n" +
                                String.join("\n", frozenPkgs);
                Files.write(venvDirectory.resolve("installed.txt"), toWrite.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            }
        } catch (IOException e) {
            throw new IOException(String.format("failed to write tag file %s", tag), e);
        }
    }

    private static void checkLauncher(Path venvDirectory, Path launcherPath, Log log) throws IOException {
        if (!Files.exists(launcherPath)) {
            throw new IOException(String.format("Launcher file does not exist '%s'", launcherPath));
        }
        Path cfg = venvDirectory.resolve("pyvenv.cfg");
        if (Files.exists(cfg)) {
            try {
                List<String> lines = Files.readAllLines(cfg);
                for (String line : lines) {
                    int idx = line.indexOf("=");
                    if (idx > -1) {
                        String l = line.substring(0, idx).trim();
                        String r = line.substring(idx + 1).trim();
                        if (l.trim().equals("executable")) {
                            Path cfgLauncherPath = Path.of(r);
                            if (!Files.exists(cfgLauncherPath) || !Files.isSameFile(launcherPath, cfgLauncherPath)) {
                                log.info(String.format("Deleting GraalPy venv due to changed launcher path"));
                                delete(venvDirectory);
                            }
                            break;
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
                throw new IOException(String.format("failed to read config file %s", cfg), e);
            }
        } else {
            log.info(String.format("missing venv config file: '%s'", cfg));
        }
    }

    private static void generateLaunchers(Path laucherPath, LauncherClassPath launcherClassPath, SubprocessLog subprocessLog, Log log) throws IOException {
        if (!Files.exists(laucherPath)) {
            log.info("Generating GraalPy launchers");
            createParentDirectories(laucherPath);
            Path java = Paths.get(System.getProperty("java.home"), "bin", "java");
            String classpath = String.join(File.pathSeparator, launcherClassPath.get());
            if (!IS_WINDOWS) {
                var script = String.format("""
                                #!/usr/bin/env bash
                                %s --enable-native-access=ALL-UNNAMED -classpath %s %s --python.Executable="$0" "$@"
                                """,
                                java,
                                String.join(File.pathSeparator, classpath),
                                GRAALPY_MAIN_CLASS);
                try {
                    Files.writeString(laucherPath, script);
                    var perms = Files.getPosixFilePermissions(laucherPath);
                    perms.addAll(List.of(PosixFilePermission.OWNER_EXECUTE, PosixFilePermission.GROUP_EXECUTE, PosixFilePermission.OTHERS_EXECUTE));
                    Files.setPosixFilePermissions(laucherPath, perms);
                } catch (IOException e) {
                    throw new IOException(String.format("failed to create launcher %s", laucherPath), e);
                }
            } else {
                // on windows, generate a venv launcher that executes our mvn target
                var script = String.format("""
                                import os, shutil, struct, venv
                                from pathlib import Path
                                vl = os.path.join(venv.__path__[0], 'scripts', 'nt', 'graalpy.exe')
                                tl = os.path.join(r'%s')
                                os.makedirs(Path(tl).parent.absolute(), exist_ok=True)
                                shutil.copy(vl, tl)
                                cmd = r'%s --enable-native-access=ALL-UNNAMED -classpath "%s" %s'
                                pyvenvcfg = os.path.join(os.path.dirname(tl), "pyvenv.cfg")
                                with open(pyvenvcfg, 'w', encoding='utf-8') as f:
                                    f.write('venvlauncher_command = ')
                                    f.write(cmd)
                                """,
                                laucherPath,
                                java,
                                classpath,
                                GRAALPY_MAIN_CLASS);
                File tmp;
                try {
                    tmp = File.createTempFile("create_launcher", ".py");
                } catch (IOException e) {
                    throw new IOException("failed to create tmp launcher", e);
                }
                tmp.deleteOnExit();
                try (var wr = new FileWriter(tmp)) {
                    wr.write(script);
                } catch (IOException e) {
                    throw new IOException(String.format("failed to write tmp launcher %s", tmp), e);
                }

                try {
                    GraalPyRunner.run(classpath, subprocessLog, tmp.getAbsolutePath());
                } catch (InterruptedException e) {
                    throw new IOException(String.format("failed to run Graalpy launcher"), e);
                }
            }
        }
    }

    private static boolean installWantedPackages(Path venvDirectory, List<String> packages, List<String> installedPackages, SubprocessLog subprocessLog) throws IOException {
        Set<String> pkgsToInstall = new HashSet<>(packages);
        pkgsToInstall.removeAll(installedPackages);
        if (pkgsToInstall.isEmpty()) {
            return false;
        }
        runPip(venvDirectory, "install", subprocessLog, pkgsToInstall.toArray(new String[pkgsToInstall.size()]));
        return true;
    }

    private static boolean deleteUnwantedPackages(Path venvDirectory, List<String> packages, List<String> installedPackages, SubprocessLog subprocessLog) throws IOException {
        List<String> args = new ArrayList<>(installedPackages);
        args.removeAll(packages);
        if (args.isEmpty()) {
            return false;
        }
        args.add(0, "-y");
        runPip(venvDirectory, "uninstall", subprocessLog, args.toArray(new String[args.size()]));
        return true;
    }

    private static void runLauncher(String launcherPath, SubprocessLog log, String... args) throws IOException {
        try {
            GraalPyRunner.runLauncher(launcherPath, log, args);
        } catch (IOException | InterruptedException e) {
            throw new IOException(String.format("failed to execute launcher command %s", List.of(args)));
        }
    }

    private static void runPip(Path venvDirectory, String command, SubprocessLog log, String... args) throws IOException {
        try {
            GraalPyRunner.runPip(venvDirectory, command, log, args);
        } catch (IOException | InterruptedException e) {
            throw new IOException(String.format("failed to execute pip %s", List.of(args)), e);
        }
    }

    private static void runVenvBin(Path venvDirectory, String bin, SubprocessLog log, String... args) throws IOException {
        try {
            GraalPyRunner.runVenvBin(venvDirectory, bin, log, args);
        } catch (IOException | InterruptedException e) {
            throw new IOException(String.format("failed to execute venv %s", List.of(args)), e);
        }
    }

    public static List<String> trim(List<String> l) {
        Iterator<String> it = l.iterator();
        while (it.hasNext()) {
            String p = it.next();
            if (p == null || p.trim().isEmpty()) {
                it.remove();
            }
        }
        return l;
    }
}
