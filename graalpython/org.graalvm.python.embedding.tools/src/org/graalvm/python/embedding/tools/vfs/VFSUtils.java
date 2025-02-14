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
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;

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

    public abstract static class Launcher {
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

            logDebug(log, packages, "VFSUtils venv packages after install %s:", installedFile);

            return packages;
        }
    }

    private static class VenvContents {
        private static final String CONTENTS_FILE_NAME = "contents";
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

    private static final String GRAALPY_VERSION_PREFIX = "# graalpy-version: ";
    private static final String INPUT_PACKAGES_PREFIX = "# input-packages: ";
    private static final String INPUT_PACKAGES_DELIMITER = ",";

    private static class LockFile {

        final Path path;
        final List<String> packages;
        final List<String> inputPackages;

        private LockFile(Path path, List<String> inputPackages, List<String> packages) {
            this.path = path;
            this.packages = packages;
            this.inputPackages = inputPackages;
        }

        static LockFile fromFile(Path file, BuildToolLog log) throws IOException {
            List<String> packages = new ArrayList<>();
            List<String> inputPackages = null;
            if (Files.isReadable(file)) {
                List<String> lines = Files.readAllLines(file);
                if (lines.isEmpty()) {
                    throw wrongFormat(file, lines, log);
                }
                // format:
                // 1.) a multiline header comment
                // 2.) graalpy version - 1 line (starting with comment #)
                // 2.) input packages - 1 line (starting with comment #)
                // 3.) locked packages - 1 line each (as input for pip install)
                // see also LockFile.write()
                Iterator<String> it = lines.iterator();
                try {
                    // graalpy version, we don't care about it for now, but with future versions the
                    // file format might change, and we will need to know to parse differently
                    String graalPyVersion = null;
                    while (it.hasNext()) {
                        String line = it.next();
                        if (line.startsWith(GRAALPY_VERSION_PREFIX)) {
                            graalPyVersion = line.substring(GRAALPY_VERSION_PREFIX.length()).trim();
                            if (graalPyVersion.isEmpty()) {
                                throw wrongFormat(file, lines, log);
                            }
                            break;
                        }
                    }
                    if (graalPyVersion == null) {
                        throw wrongFormat(file, lines, log);
                    }
                    // input packages
                    String line = it.next();
                    if (!line.startsWith(INPUT_PACKAGES_PREFIX)) {
                        throw wrongFormat(file, lines, log);
                    }
                    String pkgs = line.substring(INPUT_PACKAGES_PREFIX.length()).trim();
                    if (pkgs.isEmpty()) {
                        throw wrongFormat(file, lines, log);
                    }
                    inputPackages = Arrays.asList(pkgs.split(INPUT_PACKAGES_DELIMITER));
                    // locked packages
                    while (it.hasNext()) {
                        packages.add(it.next());
                    }
                } catch (NoSuchElementException e) {
                    throw wrongFormat(file, lines, log);
                }
            } else {
                throw new IOException("can't read lock file");
            }
            return new LockFile(file, inputPackages, packages);
        }

        private static IOException wrongFormat(Path file, List<String> lines, BuildToolLog log) {
            if (log.isDebugEnabled()) {
                log.debug("wrong format of lock file " + file);
                for (String l : lines) {
                    log.debug(l);
                }
                log.debug("");
            }
            return new IOException("invalid lock file format");
        }

        private static void write(Path venvDirectory, Path lockFile, String lockFileHeader, List<String> inputPackages, String graalPyVersion, BuildToolLog log) throws IOException {
            Objects.requireNonNull(venvDirectory);
            Objects.requireNonNull(lockFile);
            Objects.requireNonNull(lockFileHeader);
            Objects.requireNonNull(log);

            assert Files.exists(venvDirectory);

            InstalledPackages installedPackages = InstalledPackages.fromVenv(venvDirectory);
            List<String> header = getHeaderList(lockFileHeader);
            header.add(GRAALPY_VERSION_PREFIX + graalPyVersion);
            header.add(INPUT_PACKAGES_PREFIX + String.join(INPUT_PACKAGES_DELIMITER, inputPackages));
            Files.write(lockFile, header, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            Files.write(lockFile, installedPackages.packages, StandardOpenOption.APPEND);

            lifecycle(log, "Created GraalPy lock file: %s", lockFile);
            logDebug(log, installedPackages.packages, null);
        }

        private static List<String> getHeaderList(String lockFileHeader) {
            List<String> list = new ArrayList<>();
            String[] lines = lockFileHeader.split("\n");
            for (String l : lines) {
                list.add("# " + l);
            }
            return list;
        }
    }

    public static void createVenv(Path venvDirectory, List<String> packagesArgs, Launcher launcherArgs, String graalPyVersion, BuildToolLog log) throws IOException {
        createVenv(venvDirectory, packagesArgs, null, null, null, launcherArgs, graalPyVersion, log);
    }

    public static void createVenv(Path venvDirectory, List<String> packages, Path lockFilePath, String packagesChangedError, String missingLockFileWarning, Launcher launcher, String graalPyVersion,
                    BuildToolLog log) throws IOException {
        Objects.requireNonNull(venvDirectory);
        Objects.requireNonNull(packages);
        Objects.requireNonNull(launcher);
        Objects.requireNonNull(graalPyVersion);
        Objects.requireNonNull(log);
        if (lockFilePath != null) {
            Objects.requireNonNull(packagesChangedError);
        }

        logVenvArgs(venvDirectory, packages, lockFilePath, launcher, graalPyVersion, log);

        List<String> pluginPackages = trim(packages);
        LockFile lockFile = null;
        if (lockFilePath != null && Files.exists(lockFilePath)) {
            lockFile = LockFile.fromFile(lockFilePath, log);
        }

        if (!checkPackages(venvDirectory, pluginPackages, lockFile, packagesChangedError, log)) {
            return;
        }

        VenvContents venvContents = ensureVenv(venvDirectory, graalPyVersion, launcher, log);

        boolean installed;
        if (lockFile != null) {
            installed = install(venvDirectory, lockFile, log);
        } else {
            installed = install(venvDirectory, pluginPackages, venvContents, log);
            missingLockFileWarning(venvDirectory, pluginPackages, missingLockFileWarning, log);
        }
        if (installed) {
            venvContents.write(pluginPackages);
        }
    }

    private static boolean removedFromPluginPackages(Path venvDirectory, List<String> pluginPackages) throws IOException {
        if (Files.exists(venvDirectory)) {
            // comapre with contents from prev install if such already present
            VenvContents contents = VenvContents.fromVenv(venvDirectory);
            if (contents == null || contents.packages == null) {
                return false;
            }
            List<String> installedPackages = InstalledPackages.fromVenv(venvDirectory).packages;
            return removedFromPluginPackages(pluginPackages, contents.packages, installedPackages);
        }
        return false;
    }

    private static boolean removedFromPluginPackages(List<String> pluginPackages, List<String> contentsPackages, List<String> installedPackages) {
        for (String contentsPackage : contentsPackages) {
            if (!pluginPackages.contains(contentsPackage)) {
                // a previously installed package is missing
                // in the current plugin packages list
                if (!contentsPackage.contains("==")) {
                    // it had previously no version specified,
                    // check if it is requested with the same version as installed
                    String pkgAndVersion = getByName(contentsPackage, pluginPackages);
                    if (pkgAndVersion != null) {
                        // yes, a version was added to a package
                        if (installedPackages.contains(pkgAndVersion)) {
                            // and it happened to be already installed with the same version
                            continue;
                        }
                    }
                }
                return true;
            }
        }
        return false;
    }

    private static String getByName(String name, List<String> packages) {
        for (String p : packages) {
            int idx = p.indexOf("==");
            if (idx > -1) {
                String n = p.split("==")[0];
                if (n.equals(name)) {
                    return p;
                }
            }
        }
        return null;
    }

    public static void lockPackages(Path venvDirectory, List<String> packages, Path lockFile, String lockFileHeader, Launcher launcher,
                    String graalPyVersion, BuildToolLog log) throws IOException {
        Objects.requireNonNull(venvDirectory);
        Objects.requireNonNull(packages);
        Objects.requireNonNull(lockFile);
        Objects.requireNonNull(lockFileHeader);
        Objects.requireNonNull(graalPyVersion);
        Objects.requireNonNull(log);

        createVenv(venvDirectory, packages, launcher, graalPyVersion, log);

        if (Files.exists(venvDirectory)) {
            LockFile.write(venvDirectory, lockFile, lockFileHeader, packages, graalPyVersion, log);
        } else {
            // how comes?
            warning(log, "did not generate new python lock file due to missing python virtual environment");
        }
    }

    private static void logVenvArgs(Path venvDirectory, List<String> packages, Path lockFile, Launcher launcherArgs, String graalPyVersion, BuildToolLog log)
                    throws IOException {
        if (log.isDebugEnabled()) {
            // avoid computing classpath if not necessary
            Set<String> lcp = launcherArgs.computeClassPath();
            log.debug("VFSUtils.createVenv with:");
            log.debug("  graalPyVersion: " + graalPyVersion);
            log.debug("  venvDirectory: " + venvDirectory);
            log.debug("  packages: " + packages);
            log.debug("  lock file: " + lockFile);
            log.debug("  launcher: " + launcherArgs.launcherPath);
            log.debug("  launcher classpath: " + lcp);
        }
    }

    private static boolean checkPackages(Path venvDirectory, List<String> pluginPackages, LockFile lockFile, String packagesListChangedError, BuildToolLog log) throws IOException {
        if (lockFile != null) {
            checkPluginPackagesInLockFile(pluginPackages, lockFile, packagesListChangedError, log);
            logPackages(lockFile.packages, lockFile.path, log);
            return needVenv(venvDirectory, lockFile.packages, log);
        } else {
            if (removedFromPluginPackages(venvDirectory, pluginPackages)) {
                // a package was removed, and we do not know if it did not leave behind any
                // transitive dependencies - rather create whole venv again to avoid it growing
                info(log, "A package with transitive dependencies was removed since last install, setting up a clean venv");
                delete(venvDirectory);
            }
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
                debug(log, "VFSUtils skipping venv create - no package or lock file provided");
            }
            return false;
        }
        return true;
    }

    private static void logPackages(List<String> packages, Path lockFile, BuildToolLog log) {
        if (lockFile != null) {
            info(log, "Got %s python package(s) in lock file: %s", packages.size(), lockFile);
        } else {
            info(log, "Got %s python package(s) in GraalPy plugin configuration", packages.size());
        }
        if (log.isDebugEnabled()) {
            for (String pkg : packages) {
                log.debug("    " + pkg);
            }
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

    private static VenvContents ensureVenv(Path venvDirectory, String graalPyVersion, Launcher launcher, BuildToolLog log) throws IOException {
        Path launcherPath = ensureLauncher(launcher, log);
        VenvContents contents = null;
        if (Files.exists(venvDirectory)) {
            checkVenvLauncher(venvDirectory, launcherPath, log);
            contents = VenvContents.fromVenv(venvDirectory);
            if (contents == null) {
                warning(log, "Reinstalling GraalPy venv due to corrupt contents file");
                delete(venvDirectory);
            } else if (!graalPyVersion.equals(contents.graalPyVersion)) {
                contents = null;
                info(log, "Stale GraalPy virtual environment, updating to %s", graalPyVersion);
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

    private static boolean install(Path venvDirectory, LockFile lockFile, BuildToolLog log) throws IOException {
        InstalledPackages installedPackages = InstalledPackages.fromVenv(venvDirectory);
        if (installedPackages.packages.size() != lockFile.packages.size() || deleteUnwantedPackages(venvDirectory, lockFile.packages, installedPackages.packages, log)) {
            runPip(venvDirectory, "install", log, "-r", lockFile.path.toString());
            installedPackages.freeze(log);
            return true;
        } else {
            info(log, "Virtual environment is up to date with lock file, skipping install");
        }
        return false;
    }

    private static boolean install(Path venvDirectory, List<String> newPackages, VenvContents venvContents, BuildToolLog log) throws IOException {
        boolean needsUpdate = false;
        needsUpdate |= deleteUnwantedPackages(venvDirectory, newPackages, venvContents.packages, log);
        needsUpdate |= installWantedPackages(venvDirectory, newPackages, venvContents.packages, log);
        return needsUpdate;
    }

    private static void missingLockFileWarning(Path venvDirectory, List<String> newPackages, String missingLockFileWarning, BuildToolLog log) throws IOException {
        List<String> installedPackages = InstalledPackages.fromVenv(venvDirectory).freeze(log);
        if (missingLockFileWarning != null && !Boolean.getBoolean("graalpy.vfs.skipMissingLockFileWarning")) {
            if (!newPackages.containsAll(installedPackages)) {
                if (log.isWarningEnabled()) {
                    String txt = missingLockFileWarning + "\n";
                    for (String t : txt.split("\n")) {
                        log.warning(t);
                    }
                }
            }
        }
    }

    /**
     * check that there are no plugin packages missing in lock file
     */
    private static void checkPluginPackagesInLockFile(List<String> pluginPackages, LockFile lockFile, String packagesChangedError, BuildToolLog log)
                    throws IOException {
        checkPluginPackagesInLockFile(pluginPackages, lockFile.inputPackages, lockFile.path, packagesChangedError, log);
    }

    /**
     * Accessed from VFSUtilsTest
     */
    private static void checkPluginPackagesInLockFile(List<String> pluginPackages, List<String> lockFilePackages, Path lockFilePath, String packagesChangedError, BuildToolLog log)
                    throws IOException {

        if (pluginPackages.size() != lockFilePackages.size() || !pluginPackages.containsAll(lockFilePackages)) {
            String pluginPkgsString = pluginPackages.isEmpty() ? "None" : String.join(", ", pluginPackages);
            String lockFilePkgsString = lockFilePackages.isEmpty() ? "None" : String.join(", ", lockFilePackages);
            extendedError(log, String.format(packagesChangedError, lockFilePath, pluginPkgsString, lockFilePkgsString) + "\n");
            throw new IOException("inconsistent packages");
        }
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

    private static List<String> trim(List<String> l) {
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

    private static void lifecycle(BuildToolLog log, String txt, Object... args) {
        if (log.isLifecycleEnabled()) {
            log.lifecycle(String.format(txt, args));
        }
    }

    private static void debug(BuildToolLog log, String txt) {
        if (log.isDebugEnabled()) {
            log.debug(txt);
        }
    }

    private static void logDebug(BuildToolLog log, List<String> l, String msg, Object... args) {
        if (log.isDebugEnabled()) {
            if (msg != null) {
                log.debug(String.format(msg, args));
            }
            for (String p : l) {
                log.debug("  " + p);
            }
        }
    }

}
