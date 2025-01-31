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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.graalvm.python.embedding.tools.exec.BuildToolLog;
import org.graalvm.python.embedding.tools.exec.BuildToolLog.CollectOutputLog;
import org.graalvm.python.embedding.tools.exec.GraalPyRunner;

public final class VFSUtils {

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

    private static final String FOR_MORE_INFO_REFERENCE_MSG = "For more information, please refer to https://www.graalvm.org/latest/reference-manual/python/Embedding-Build-Tools";

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
            });
        }
    }

    private static String makeDirPath(Path p) {
        String ret = p.toString();
        if (!ret.endsWith(File.separator)) {
            ret += File.separator;
        }
        return ret;
    }

    private static void delete(Path dir) throws IOException {
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

    public static abstract class Launcher {
        private final Path launcherPath;

        protected Launcher(Path launcherPath) {
            Objects.requireNonNull(launcherPath);
            this.launcherPath = launcherPath;
        }

        protected abstract Set<String> computeClassPath() throws IOException;
    }

    private static class InstalledPackages {
        final Path venvDirectory;
        final Path installedFile;
        List<String> packages;

        private InstalledPackages(Path venvDirectory, Path installedFile, List<String> packages) {
            this.venvDirectory = venvDirectory;
            this.installedFile = installedFile;
            this.packages = packages;
        }

        static InstalledPackages fromVenv(Path venvDirectory) throws IOException {
            Path installed = venvDirectory.resolve("installed.txt");
            List<String> pkgs = Files.exists(installed) ? readPackagesFromFile(installed) : Collections.emptyList();
            return new InstalledPackages(venvDirectory, installed, pkgs);
        }

        List<String> freeze(BuildToolLog log) throws IOException {
            CollectOutputLog collectOutputLog = new CollectOutputLog(log);
            runPip(venvDirectory, "freeze", collectOutputLog, "--local");
            packages = new ArrayList<>(collectOutputLog.getOutput());

            String toWrite = "# Generated by GraalPy Maven or Gradle plugin using pip freeze\n" +
                            "# This file is used by GraalPy VirtualFileSystem\n" +
                            String.join("\n", packages);
            Files.write(installedFile, toWrite.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            logDebug(log, packages, "VFSUtils.createVenv installed python packages:");

            return packages;
        }
    }

    private static class VenvContents {
        private final static String CONTENTS_FILE_NAME = "contents";
        final Path contentsFile;
        List<String> packages;
        final String graalPyVersion;

        private VenvContents(Path contentsFile, List<String> packages, String graalPyVersion) {
            this.contentsFile = contentsFile;
            this.packages = packages;
            this.graalPyVersion = graalPyVersion;
        }

        static VenvContents create(Path venvDirectory, String graalPyVersion) {
            return new VenvContents(venvDirectory.resolve(CONTENTS_FILE_NAME), Collections.emptyList(), graalPyVersion);
        }

        static VenvContents fromVenv(Path venvDirectory) throws IOException {
            Path contentsFile = venvDirectory.resolve(CONTENTS_FILE_NAME);
            List<String> packages = new ArrayList<>();
            String graalPyVersion = null;
            if (Files.isReadable(contentsFile)) {
                List<String> lines = Files.readAllLines(contentsFile);
                if (lines.isEmpty()) {
                    return null;
                }
                Iterator<String> it = lines.iterator();
                graalPyVersion = it.next();
                while (it.hasNext()) {
                    packages.add(it.next());
                }
            }
            return new VenvContents(contentsFile, packages, graalPyVersion);
        }

        void write(List<String> pkgs) throws IOException {
            Files.write(contentsFile, List.of(graalPyVersion), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            if (pkgs != null) {
                Files.write(contentsFile, pkgs, StandardOpenOption.APPEND);
                this.packages = pkgs;
            }
        }
    }

    public static void createVenv(Path venvDirectory, List<String> packagesArgs, Launcher launcherArgs, String graalPyVersion, BuildToolLog log) throws IOException {
        createVenv(venvDirectory, packagesArgs, null, null, null, null, launcherArgs, graalPyVersion, log);
    }

    public static void createVenv(Path venvDirectory, List<String> packages, Path requirementsFile,
                    String inconsistentPackagesError, String wrongPackageVersionFormatError, String missingRequirementsFileWarning,
                    Launcher launcher, String graalPyVersion, BuildToolLog log) throws IOException {
        Objects.requireNonNull(venvDirectory);
        Objects.requireNonNull(packages);
        Objects.requireNonNull(launcher);
        Objects.requireNonNull(graalPyVersion);
        Objects.requireNonNull(log);

        logVenvArgs(venvDirectory, packages, requirementsFile, launcher, graalPyVersion, log);

        List<String> pluginPackages = trim(packages);
        List<String> requirementsPackages = requirementsFile != null && Files.exists(requirementsFile) ? readPackagesFromFile(requirementsFile) : null;
        if (!checkPackages(venvDirectory, pluginPackages, requirementsPackages, requirementsFile, inconsistentPackagesError, wrongPackageVersionFormatError, log)) {
            return;
        }

        VenvContents venvContents = ensureVenv(venvDirectory, graalPyVersion, log, ensureLauncher(launcher, log));

        boolean installed = requirementsPackages != null ? install(venvDirectory, requirementsFile, requirementsPackages, log)
                        : install(venvDirectory, pluginPackages, venvContents, missingRequirementsFileWarning, log);
        if (installed) {
            venvContents.write(pluginPackages);
        }
    }

    public static void freezePackages(Path venvDirectory, List<String> packages, Path requirementsFile, String requirementsHeader, String wrongPackageVersionFormatError, Launcher launcher,
                    String graalPyVersion, BuildToolLog log) throws IOException {
        checkVersionFormat(packages, wrongPackageVersionFormatError, log);

        createVenv(venvDirectory, packages, launcher, graalPyVersion, log);

        if (Files.exists(venvDirectory)) {
            createRequirementsFile(venvDirectory, requirementsFile, requirementsHeader, log);
        } else {
            // how comes?
            warning(log, "did not generate new python requirements file due to missing venv");
        }
    }

    private static void logVenvArgs(Path venvDirectory, List<String> packages, Path requirementsFile, Launcher launcherArgs, String graalPyVersion, BuildToolLog log)
                    throws IOException {
        if (log.isDebugEnabled()) {
            // avoid computing classpath if not necessary
            Set<String> lcp = launcherArgs.computeClassPath();
            log.debug("VFSUtils.createVenv():");
            log.debug("  graalPyVersion: " + graalPyVersion);
            log.debug("  venvDirectory: " + venvDirectory);
            log.debug("  packages: " + packages);
            log.debug("  requirements file: " + requirementsFile);
            log.debug("  launcher: " + launcherArgs.launcherPath);
            log.debug("  launcher classpath: " + lcp);
        }
    }

    private static boolean checkPackages(Path venvDirectory, List<String> pluginPackages, List<String> requirementsPackages, Path requirementsFile, String inconsistentPackagesError,
                    String wrongPackageVersionFormatError, BuildToolLog log) throws IOException {
        if (requirementsPackages != null) {
            checkPackagesConsistent(pluginPackages, requirementsPackages, inconsistentPackagesError, wrongPackageVersionFormatError, log);
            logPackages(requirementsPackages, requirementsFile, log);
            return needVenv(venvDirectory, requirementsPackages, log);
        } else {
            logPackages(pluginPackages, null, log);
            return needVenv(venvDirectory, pluginPackages, log);
        }
    }

    private static boolean needVenv(Path venvDirectory, List<String> packages, BuildToolLog log) throws IOException {
        if ((packages.isEmpty())) {
            if (Files.exists(venvDirectory)) {
                info(log, "No packages to install, deleting venv");
                delete(venvDirectory);
            } else {
                debug(log, "VFSUtils.createVenv: skipping - no package or requirements file provided");
            }
            return false;
        }
        return true;
    }

    private static void logPackages(List<String> packages, Path requirementsFile, BuildToolLog log) {
        if (requirementsFile != null) {
            info(log, "Installing %s python packages from requirements file: %s", packages.size(), requirementsFile);
        } else {
            info(log, "Installing %s python packages from GraalPy plugin configuration", packages.size());
        }
    }

    private static List<String> readPackagesFromFile(Path file) throws IOException {
        return Files.readAllLines(file).stream().filter((s) -> {
            if (s == null) {
                return false;
            }
            String l = s.trim();
            return !l.startsWith("#") && !s.isEmpty();
        }).toList();
    }

    private static VenvContents ensureVenv(Path venvDirectory, String graalPyVersion, BuildToolLog log, Path launcherPath) throws IOException {
        VenvContents contents = null;
        if (Files.exists(venvDirectory)) {
            checkVenvLauncher(venvDirectory, launcherPath, log);
            contents = VenvContents.fromVenv(venvDirectory);
            if (contents == null) {
                warning(log, "Reinstalling GraalPy venv due to corrupt contents file");
                delete(venvDirectory);
            } else if (!graalPyVersion.equals(contents.graalPyVersion)) {
                contents = null;
                info(log, "Stale GraalPy venv, updating to %s", graalPyVersion);
                delete(venvDirectory);
            }
        }

        if (!Files.exists(venvDirectory)) {
            info(log, "Creating GraalPy %s venv", graalPyVersion);
            runLauncher(launcherPath.toString(), log, "-m", "venv", venvDirectory.toString(), "--without-pip");
            runVenvBin(venvDirectory, "graalpy", log, "-I", "-m", "ensurepip");
        }

        if (contents == null) {
            contents = VenvContents.create(venvDirectory, graalPyVersion);
        }

        return contents;
    }

    private static boolean install(Path venvDirectory, Path requirementsFile, List<String> requiredPkgs, BuildToolLog log) throws IOException {
        InstalledPackages installedPackages = InstalledPackages.fromVenv(venvDirectory);
        if (installedPackages.packages.size() != requiredPkgs.size() || deleteUnwantedPackages(venvDirectory, requiredPkgs, installedPackages.packages, log)) {
            runPip(venvDirectory, "install", log, "-r", requirementsFile.toString());
            installedPackages.freeze(log);
            return true;
        } else {
            info(log, "Python packages up to date, skipping install");
        }
        return false;
    }

    private static boolean install(Path venvDirectory, List<String> newPackages, VenvContents venvContents, String missingRequirementsFileWarning, BuildToolLog log) throws IOException {
        boolean needsUpdate = false;
        needsUpdate |= deleteUnwantedPackages(venvDirectory, newPackages, venvContents.packages, log);
        needsUpdate |= installWantedPackages(venvDirectory, newPackages, venvContents.packages, log);
        if (needsUpdate) {
            List<String> installedPackages = InstalledPackages.fromVenv(venvDirectory).freeze(log);
            if (missingRequirementsFileWarning != null && !Boolean.getBoolean("graalpy.vfs.skipMissingRequirementsWarning")) {
                if (installedPackages.size() != newPackages.size()) {
                    missingRequirementsWarning(log, missingRequirementsFileWarning);
                }
            }
            return true;
        }
        return false;
    }

    private static void missingRequirementsWarning(BuildToolLog log, String missingRequirementsFileWarning) {
        if (log.isWarningEnabled()) {
            String txt = missingRequirementsFileWarning + "\n" + FOR_MORE_INFO_REFERENCE_MSG + "\n";
            for (String t : txt.split("\n")) {
                log.warning(t);
            }
        }
    }

    private static void createRequirementsFile(Path venvDirectory, Path requirementsFile, String requirementsFileHeader, BuildToolLog log) throws IOException {
        Objects.requireNonNull(venvDirectory);
        Objects.requireNonNull(requirementsFile);
        Objects.requireNonNull(requirementsFileHeader);
        Objects.requireNonNull(log);

        assert Files.exists(venvDirectory);

        info(log, "Creating %s", requirementsFile);

        InstalledPackages installedPackages = InstalledPackages.fromVenv(venvDirectory);
        List<String> header = getHeaderList(requirementsFileHeader);
        Files.write(requirementsFile, header, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        Files.write(requirementsFile, installedPackages.packages, StandardOpenOption.APPEND);

        logDebug(log, installedPackages.packages, "VFSUtils created requirements file: %s", installedPackages.installedFile);
    }

    private static List<String> getHeaderList(String requirementsFileHeader) {
        List<String> list = new ArrayList<>();
        String[] lines = requirementsFileHeader.split("\n");
        for (String l : lines) {
            list.add("# " + l);
        }
        list.add("");
        return list;
    }

    public static void checkVersionFormat(List<String> packages, String wrongPackageVersionFormatError, BuildToolLog log) throws IOException {
        Objects.requireNonNull(packages);
        Objects.requireNonNull(wrongPackageVersionFormatError);
        Objects.requireNonNull(log);

        StringBuilder sb = new StringBuilder();
        for (String pkg : packages) {
            if (!checkValidPackageVersion(pkg)) {
                sb.append(!sb.isEmpty() ? ", " : "").append("'").append(pkg).append("'");
            }
        }
        if (!sb.isEmpty()) {
            wrongPackageVersionError(log, wrongPackageVersionFormatError, sb.toString());
        }
    }

    private static boolean checkValidPackageVersion(String pkg) {
        int idx = pkg.indexOf("==");
        if (idx <= 0) {
            return false;
        } else {
            String version = pkg.substring(idx + 2).trim();
            if (version.isEmpty() || version.contains("*")) {
                return false;
            }
        }
        return true;
    }

    private static void wrongPackageVersionError(BuildToolLog log, String wrongPackageVersionFormatError, String pkgs) throws IOException {
        if (log.isErrorEnabled()) {
            extendedError(log, String.format(wrongPackageVersionFormatError, pkgs) + "\n" + FOR_MORE_INFO_REFERENCE_MSG);
        }
        throw new IOException("invalid package format: " + pkgs);
    }

    private static void checkPackagesConsistent(List<String> packages, List<String> requiredPackages, String inconsistentPackagesError,
                    String wrongPackageVersionFormatError, BuildToolLog log) throws IOException {
        if (packages.isEmpty()) {
            return;
        }

        checkVersionFormat(packages, wrongPackageVersionFormatError, log);

        Map<String, String> requiredPackagesMap = requiredPackages.stream().filter(p -> p.contains("==")).map(p -> p.split("==")).collect(Collectors.toMap(parts -> parts[0], parts -> parts[1]));
        StringBuilder sb = new StringBuilder();
        for (String pkg : packages) {
            String[] s = pkg.split("==");
            String pName = s[0];
            String pVersion = s[1];
            String rVersion = requiredPackagesMap.get(pName);
            if (rVersion != null && rVersion.startsWith(pVersion)) {
                continue;
            }
            sb.append(!sb.isEmpty() ? ", " : "").append("'").append(pkg).append("'");
        }

        if (!sb.isEmpty()) {
            inconsistentPackagesError(log, inconsistentPackagesError, sb.toString());
        }
    }

    private static void inconsistentPackagesError(BuildToolLog log, String inconsistentPackagesError, String packages) throws IOException {
        if (log.isErrorEnabled()) {
            extendedError(log, String.format(inconsistentPackagesError, packages) + "\n" + FOR_MORE_INFO_REFERENCE_MSG);
        }
        throw new IOException("inconsistent packages");
    }

    private static void checkVenvLauncher(Path venvDirectory, Path launcherPath, BuildToolLog log) throws IOException {
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
                                info(log, "Deleting GraalPy venv due to changed launcher path");
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
            info(log, "Missing venv config file: '%s'", cfg);
        }
    }

    private static Path ensureLauncher(Launcher launcherArgs, BuildToolLog log) throws IOException {
        String externalLauncher = System.getProperty("graalpy.vfs.venvLauncher");
        if (externalLauncher == null || externalLauncher.trim().isEmpty()) {
            generateLaunchers(launcherArgs, log);
            return launcherArgs.launcherPath;
        } else {
            return Path.of(externalLauncher);
        }
    }

    private static void generateLaunchers(Launcher launcherArgs, BuildToolLog log) throws IOException {
        if (!Files.exists(launcherArgs.launcherPath)) {
            info(log, "Generating GraalPy launchers");
            createParentDirectories(launcherArgs.launcherPath);
            Path java = Paths.get(System.getProperty("java.home"), "bin", "java");
            String classpath = String.join(File.pathSeparator, launcherArgs.computeClassPath());
            if (!IS_WINDOWS) {
                var script = String.format("""
                                #!/usr/bin/env bash
                                %s --enable-native-access=ALL-UNNAMED -classpath %s %s --python.Executable="$0" "$@"
                                """,
                                java,
                                String.join(File.pathSeparator, classpath),
                                GRAALPY_MAIN_CLASS);
                try {
                    Files.writeString(launcherArgs.launcherPath, script);
                    var perms = Files.getPosixFilePermissions(launcherArgs.launcherPath);
                    perms.addAll(List.of(PosixFilePermission.OWNER_EXECUTE, PosixFilePermission.GROUP_EXECUTE, PosixFilePermission.OTHERS_EXECUTE));
                    Files.setPosixFilePermissions(launcherArgs.launcherPath, perms);
                } catch (IOException e) {
                    throw new IOException(String.format("failed to create launcher %s", launcherArgs.launcherPath), e);
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
                                launcherArgs.launcherPath,
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
                    GraalPyRunner.run(classpath, log, tmp.getAbsolutePath());
                } catch (InterruptedException e) {
                    throw new IOException(String.format("failed to run Graalpy launcher"), e);
                }
            }
        }
    }

    private static boolean installWantedPackages(Path venvDirectory, List<String> packages, List<String> installedPackages, BuildToolLog log) throws IOException {
        Set<String> pkgsToInstall = new HashSet<>(packages);
        pkgsToInstall.removeAll(installedPackages);
        if (pkgsToInstall.isEmpty()) {
            return false;
        }
        runPip(venvDirectory, "install", log, pkgsToInstall.toArray(new String[pkgsToInstall.size()]));
        return true;
    }

    private static boolean deleteUnwantedPackages(Path venvDirectory, List<String> packages, List<String> installedPackages, BuildToolLog log) throws IOException {
        List<String> args = new ArrayList<>(installedPackages);
        args.removeAll(packages);
        if (args.isEmpty()) {
            return false;
        }
        args.add(0, "-y");

        runPip(venvDirectory, "uninstall", log, args.toArray(new String[args.size()]));
        return true;
    }

    private static void runLauncher(String launcherPath, BuildToolLog log, String... args) throws IOException {
        try {
            GraalPyRunner.runLauncher(launcherPath, log, args);
        } catch (IOException | InterruptedException e) {
            throw new IOException(String.format("failed to execute launcher command %s", List.of(args)));
        }
    }

    private static void runPip(Path venvDirectory, String command, BuildToolLog log, String... args) throws IOException {
        try {
            GraalPyRunner.runPip(venvDirectory, command, log, args);
        } catch (IOException | InterruptedException e) {
            throw new IOException(String.format("failed to execute pip %s", List.of(args)), e);
        }
    }

    private static void runVenvBin(Path venvDirectory, String bin, BuildToolLog log, String... args) throws IOException {
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

    private static void warning(BuildToolLog log, String txt) {
        if (log.isWarningEnabled()) {
            log.warning(txt);
        }
    }

    private static void extendedError(BuildToolLog log, String txt) {
        if (log.isErrorEnabled()) {
            log.error("");
            for (String t : txt.split("\n")) {
                log.error(t);
            }
            log.error("");
        }
    }

    private static void info(BuildToolLog log, String txt, Object... args) {
        if (log.isInfoEnabled()) {
            log.info(String.format(txt, args));
        }
    }

    private static void debug(BuildToolLog log, String txt) {
        if (log.isDebugEnabled()) {
            log.debug(txt);
        }
    }

    private static void logDebug(BuildToolLog log, List<String> l, String msg, Object... args) {
        if (log.isDebugEnabled()) {
            log.debug(String.format(msg, args));
            for (String p : l) {
                log.debug("  " + p);
            }
        }
    }

}
