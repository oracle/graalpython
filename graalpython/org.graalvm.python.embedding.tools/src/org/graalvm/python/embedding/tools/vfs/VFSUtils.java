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
package org.graalvm.python.embedding.tools.vfs;

import org.graalvm.python.embedding.tools.exec.GraalPyRunner;
import org.graalvm.python.embedding.tools.exec.SubprocessLog;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class VFSUtils {

    public static final String VFS_ROOT = "org.graalvm.python.vfs";
    public static final String VFS_HOME = "home";
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
                    """.replace("$vfs", VFS_ROOT);

    private static final String NATIVE_IMAGE_ARGS = "Args = -H:-CopyLanguageResources";

    private static final String INCLUDE_PREFIX = "include:";

    private static final String EXCLUDE_PREFIX = "exclude:";

    private static final boolean IS_WINDOWS = System.getProperty("os.name").startsWith("Windows");

    public static final String LAUNCHER_NAME = IS_WINDOWS ? "graalpy.exe" : "graalpy.sh";

    private static final String GRAALPY_MAIN_CLASS = "com.oracle.graal.python.shell.GraalPythonMain";

    public static void writeNativeImageConfig(Path metaInfRoot, String pluginId) throws IOException {
        Path p = metaInfRoot.resolve(Path.of("native-image", GRAALPY_GROUP_ID, pluginId));
        write(p.resolve("resource-config.json"), NATIVE_IMAGE_RESOURCES_CONFIG);
        write(p.resolve("native-image.properties"), NATIVE_IMAGE_ARGS);
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

    public static void generateVFSFilesList(Path vfs) throws IOException {
        Path filesList = vfs.resolve(VFS_FILESLIST);
        if (!Files.isDirectory(vfs)) {
            throw new IOException(String.format("'%s' has to exist and be a directory.\n", vfs.toString()));
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
        }
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

    public static void createHome(Path homeDirectory, String graalPyVersion, List<String> includes, List<String> excludes, LauncherClassPath launcherClassPath, SubprocessLog subprocessLog, Log log)
                    throws IOException {

        trim(includes);
        trim(excludes);

        var tag = homeDirectory.resolve("tagfile");

        if (Files.isReadable(tag)) {
            List<String> lines = null;
            try {
                lines = Files.readAllLines(tag);
            } catch (IOException e) {
                throw new IOException(String.format("failed to read tag file %s", tag), e);
            }
            if (lines.isEmpty() || !graalPyVersion.equals(lines.get(0))) {
                log.info(String.format("Stale GraalPy home, updating to %s", graalPyVersion));
                delete(homeDirectory);
            }
            if (pythonHomeChanged(includes, excludes, lines)) {
                log.info(String.format("Deleting GraalPy home due to changed includes or excludes"));
                delete(homeDirectory);
            }
        }
        try {
            if (!Files.exists(homeDirectory)) {
                log.info(String.format("Creating GraalPy %s home in %s", graalPyVersion, homeDirectory));
                createParentDirectories(homeDirectory);
                VFSUtils.copyGraalPyHome(launcherClassPath.get(), homeDirectory, includes, excludes, subprocessLog);
            }
            Files.write(tag, List.of(graalPyVersion), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            write(tag, includes, INCLUDE_PREFIX);
            write(tag, excludes, EXCLUDE_PREFIX);
        } catch (IOException | InterruptedException e) {

            throw new IOException(String.format("failed to copy graalpy home %s", homeDirectory), e);
        }
    }

    private static boolean pythonHomeChanged(List<String> includes, List<String> excludes, List<String> lines) {
        Set<String> prevIncludes = new HashSet<>();
        Set<String> prevExcludes = new HashSet<>();
        for (int i = 1; i < lines.size(); i++) {
            String l = lines.get(i);
            if (l.startsWith(INCLUDE_PREFIX)) {
                prevIncludes.add(l.substring(INCLUDE_PREFIX.length()));
            } else if (l.startsWith(EXCLUDE_PREFIX)) {
                prevExcludes.add(l.substring(EXCLUDE_PREFIX.length()));
            }
        }
        boolean includeDidNotChange = prevIncludes.size() == includes.size() && prevIncludes.containsAll(includes);
        boolean excludeDidNotChange = prevExcludes.size() == excludes.size() && prevExcludes.containsAll(excludes);
        return !(includeDidNotChange && excludeDidNotChange);
    }

    private static void write(Path tag, List<String> list, String prefix) throws IOException {
        if (list != null) {
            Files.write(tag, list.stream().map(l -> prefix + l).collect(Collectors.toList()), StandardOpenOption.APPEND);
        }
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

    public static void copyGraalPyHome(Set<String> classpath, Path home, Collection<String> pythonHomeIncludes, Collection<String> pythonHomeExcludes, SubprocessLog log)
                    throws IOException, InterruptedException {
        log.log(String.format("Copying std lib to '%s'\n", home));
        // get stdlib and core home
        String stdlibHome = null;
        String coreHome = null;
        String pathsOutputPrefix = "<=outputpaths=>";

        CollectOutputLog outputLog = new CollectOutputLog();
        GraalPyRunner.run(classpath, outputLog, new String[]{"-c", "print('" + pathsOutputPrefix + "', __graalpython__.get_python_home_paths(), sep='')"});
        for (String l : outputLog.output) {
            if (l.startsWith(pathsOutputPrefix)) {
                String[] s = l.substring(pathsOutputPrefix.length()).split(File.pathSeparator);
                stdlibHome = s[0];
                coreHome = s[1];
            }
        }
        assert stdlibHome != null;
        assert coreHome != null;

        // copy core home
        Path target = home.resolve("lib-graalpython");
        if (!Files.exists(target)) {
            Files.createDirectories(target);
        }
        Path source = Paths.get(coreHome);
        Predicate<Path> filter = (f) -> {
            if (Files.isDirectory(f)) {
                if (f.getFileName().toString().equals("__pycache__") || f.getFileName().toString().equals("standalone")) {
                    return true;
                }
            } else {
                if (f.getFileName().endsWith(".py") || f.getFileName().endsWith(".txt") ||
                                f.getFileName().endsWith(".c") || f.getFileName().endsWith(".md") ||
                                f.getFileName().endsWith(".patch") || f.getFileName().endsWith(".toml") ||
                                f.getFileName().endsWith("PKG-INFO")) {
                    return true;
                }
                if (!isIncluded(f.toAbsolutePath().toString(), pythonHomeIncludes)) {
                    return true;
                }
            }
            return isExcluded(f.toAbsolutePath().toString(), pythonHomeExcludes);
        };
        copyFolder(source, source, target, filter);

        // copy stdlib home
        target = home.resolve("lib-python").resolve("3");
        if (!Files.exists(target)) {
            Files.createDirectories(target);
        }
        source = Paths.get(stdlibHome);
        filter = (f) -> {
            if (Files.isDirectory(f)) {
                if (f.getFileName().toString().equals("idlelib") || f.getFileName().toString().equals("ensurepip") ||
                                f.getFileName().toString().equals("tkinter") || f.getFileName().toString().equals("turtledemo") ||
                                f.getFileName().toString().equals("__pycache__")) {
                    return true;
                }
            } else {
                // libpythonvm.* in same folder as stdlib is a windows issue only
                if (f.getFileName().toString().equals("libpythonvm.dll")) {
                    return true;
                }
                if (!isIncluded(f.toAbsolutePath().toString(), pythonHomeIncludes)) {
                    return true;
                }
            }
            return isExcluded(f.toAbsolutePath().toString(), pythonHomeExcludes);
        };
        copyFolder(source, source, target, filter);
    }

    private static boolean isIncluded(String filePath, Collection<String> includes) {
        if (includes == null || includes.isEmpty()) {
            return true;
        }
        return pathMatches(filePath, includes);
    }

    private static boolean isExcluded(String filePath, Collection<String> excludes) {
        if (excludes == null || excludes.isEmpty()) {
            return false;
        }
        return pathMatches(filePath, excludes);
    }

    private static boolean pathMatches(String filePath, Collection<String> includes) {
        String path = filePath;
        if (File.separator.equals("\\")) {
            path = path.replaceAll("\\\\", "/");
        }
        for (String i : includes) {
            Pattern pattern = Pattern.compile(i);
            Matcher matcher = pattern.matcher(path);
            if (matcher.matches()) {
                return true;
            }
        }
        return false;
    }

    private static void copyFolder(Path sourceRoot, Path file, Path targetRoot, Predicate<Path> filter) throws IOException {
        Files.walkFileTree(file, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path f, BasicFileAttributes attrs) throws IOException {
                if (filter.test(f)) {
                    return FileVisitResult.CONTINUE;
                }
                if (Files.isDirectory(f)) {
                    copyFolder(sourceRoot, f, targetRoot, filter);
                } else {
                    Path relFile = sourceRoot.relativize(f);
                    Path targetPath = targetRoot.resolve(relFile.toString());
                    Path parent = targetPath.getParent();
                    if (parent == null) {
                        return FileVisitResult.CONTINUE;
                    }
                    if (!Files.exists(parent)) {
                        Files.createDirectories(parent);
                    }
                    if (Files.exists(targetPath)) {
                        Files.delete(targetPath);
                    }
                    Files.copy(f, targetPath);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static class CollectOutputLog implements SubprocessLog {

        private final List<String> output = new ArrayList<>();

        public void subProcessOut(CharSequence var1) {
            output.add(var1.toString());
        }

        public void subProcessErr(CharSequence var1) {
            System.err.println(var1);
        }

        public void log(CharSequence var1) {

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

        if (packages != null) {
            deleteUnwantedPackages(venvDirectory, packages, installedPackages, subprocessLog);
            installWantedPackages(venvDirectory, packages, installedPackages, subprocessLog);
        }

        try {
            Files.write(tag, List.of(graalPyVersion), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            Files.write(tag, packages, StandardOpenOption.APPEND);
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
                                %s -classpath %s %s --python.Executable="$0" "$@"
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
                                cmd = r'%s -classpath "%s" %s'
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

    private static void installWantedPackages(Path venvDirectory, List<String> packages, List<String> installedPackages, SubprocessLog subprocessLog) throws IOException {
        Set<String> pkgsToInstall = new HashSet<>(packages);
        pkgsToInstall.removeAll(installedPackages);
        if (pkgsToInstall.isEmpty()) {
            return;
        }
        runPip(venvDirectory, "install", subprocessLog, pkgsToInstall.toArray(new String[pkgsToInstall.size()]));
    }

    private static void deleteUnwantedPackages(Path venvDirectory, List<String> packages, List<String> installedPackages, SubprocessLog subprocessLog) throws IOException {
        List<String> args = new ArrayList<>(installedPackages);
        args.removeAll(packages);
        if (args.isEmpty()) {
            return;
        }
        args.add(0, "-y");
        runPip(venvDirectory, "uninstall", subprocessLog, args.toArray(new String[args.size()]));
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
