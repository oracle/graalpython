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
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

@Mojo(name = "process-graalpy-resources", defaultPhase = LifecyclePhase.PROCESS_RESOURCES,
                requiresDependencyCollection = ResolutionScope.COMPILE_PLUS_RUNTIME,
                requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME)
public class ManageResourcesMojo extends AbstractMojo {

    private static final boolean IS_WINDOWS = System.getProperty("os.name").startsWith("Windows");
    private static final String LAUNCHER = IS_WINDOWS ? "graalpy.exe" : "graalpy.sh";
    private static final String BIN_DIR = IS_WINDOWS ? "Scripts" : "bin";
    private static final String EXE_SUFFIX = IS_WINDOWS ? ".exe" : "";

    private static final String INCLUDE_PREFIX = "include:";

    private static final String EXCLUDE_PREFIX = "exclude:";

    @Parameter(defaultValue = "${project}", required = true, readonly = true)
    MavenProject project;

    @Parameter
    Set<String> packages;

    @Parameter
    PythonHome pythonHome;

    static Path getHomeDirectory(MavenProject project) {
        return Path.of(project.getBuild().getOutputDirectory(), "vfs", "home");
    }

    public void execute() throws MojoExecutionException {
        manageHome();
        manageVenv();
        listGraalPyResources();
    }

    public static class PythonHome {
        private List<String> includes;
        private List<String> excludes;
    }

    private void manageHome() throws MojoExecutionException {
        var homeDirectory = getHomeDirectory(project);
        if (pythonHome == null) {
            delete(homeDirectory);
            return;
        }
        var tag = homeDirectory.resolve("tagfile");
        var graalPyVersion = ExecGraalPyMojo.getGraalPyVersion(project);

        List<String> pythonHomeIncludes = toSortedArrayList(pythonHome.includes);
        List<String> pythonHomeExcludes = toSortedArrayList(pythonHome.excludes);

        if (Files.isReadable(tag)) {
            try {
                var lines = Files.readAllLines(tag);
                if (lines.isEmpty() || !graalPyVersion.equals(lines.get(0))) {
                    getLog().info(String.format("Stale GraalPy home, updating to %s", graalPyVersion));
                    delete(homeDirectory);
                }
                if (pythonHomeChanged(pythonHomeIncludes, pythonHomeExcludes, lines)) {
                    getLog().info(String.format("Deleting GraalPy home due to chenges includes or excludes"));
                    delete(homeDirectory);
                }
            } catch (IOException e) {
                throw new MojoExecutionException(e);
            }
        } else {
            getLog().info(String.format("Creating GraalPy %s home", graalPyVersion));
        }
        if (!Files.exists(homeDirectory)) {
            try {
                Files.createDirectories(homeDirectory.getParent());
            } catch (IOException e) {
                throw new MojoExecutionException(e);
            }
            copy(homeDirectory.toAbsolutePath().toString(), pythonHomeIncludes, pythonHomeExcludes);
        }
        try {
            Files.write(tag, List.of(graalPyVersion), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            write(tag, pythonHomeIncludes, INCLUDE_PREFIX);
            write(tag, pythonHomeExcludes, EXCLUDE_PREFIX);
        } catch (IOException e) {
            throw new MojoExecutionException(e);
        }
    }

    private boolean pythonHomeChanged(List<String> includes, List<String> excludes, List<String> lines) throws MojoExecutionException {
        List<String> prevIncludes = new ArrayList<>();
        List<String> prevExcludes = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            String l = lines.get(i);
            if (l.startsWith(INCLUDE_PREFIX)) {
                prevIncludes.add(l.substring(INCLUDE_PREFIX.length()));
            } else if (l.startsWith(EXCLUDE_PREFIX)) {
                prevExcludes.add(l.substring(EXCLUDE_PREFIX.length()));
            }
        }
        prevIncludes = toSortedArrayList(prevIncludes);
        prevExcludes = toSortedArrayList(prevExcludes);
        return !(prevIncludes.equals(includes) && prevExcludes.equals(excludes));
    }

    private void write(Path tag, List<String> list, String prefix) throws IOException {
        if(list != null) {
            Files.write(tag, list.stream().map(l -> prefix + l).collect(Collectors.toList()), StandardOpenOption.APPEND);
        }
    }

    private ArrayList<String> toSortedArrayList(List<String> l) {
        if(l != null) {
            Collections.sort(l);
            return new ArrayList<>(l);
        }
        return new ArrayList<>(0);
    }

    private void copy(String targetRootPath, List<String> pythonHomeIncludes, List<String> pythonHomeExcludes) throws MojoExecutionException {
        getLog().info(String.format("Copying std lib to '%s'\n", targetRootPath));
        try {
            // get stdlib and core home
            String stdlibHome = null;
            String coreHome = null;
            String pathsOutputPrefix = "<=outputpaths=>";
            List<String> homePathsOutput = new ArrayList<>();
            ExecGraalPyMojo.runGraalPy(project, getLog(), homePathsOutput, new String[]{"-c", "print('" + pathsOutputPrefix + "', __graalpython__.get_python_home_paths(), sep='')"});
            for (String l : homePathsOutput) {
                if(l.startsWith(pathsOutputPrefix)) {
                    String[] s = l.substring(pathsOutputPrefix.length()).split(File.pathSeparator);
                    stdlibHome = s[0];
                    coreHome = s[1];
                }
            }
            assert stdlibHome != null;
            assert coreHome != null;

            // copy core home
            File target = new File(targetRootPath + File.separator + "lib-graalpython");
            if(!target.exists()) {
                target.mkdirs();
            }
            Path source = Paths.get(coreHome);
            Predicate<Path> filter = (f) -> {
                if(Files.isDirectory(f)) {
                    if(f.getFileName().toString().equals("__pycache__") || f.getFileName().toString().equals("standalone")) {
                        return true;
                    }
                } else {
                    if(f.getFileName().endsWith(".py") || f.getFileName().endsWith(".txt") ||
                            f.getFileName().endsWith(".c") || f.getFileName().endsWith(".md") ||
                            f.getFileName().endsWith(".patch") || f.getFileName().endsWith(".toml") ||
                            f.getFileName().endsWith("PKG-INFO")) {
                        return true;
                    }
                    if(!isIncluded(f.toAbsolutePath().toString(), pythonHomeIncludes)) {
                        return true;
                    }
                }
                return isExcluded(f.toAbsolutePath().toString(), pythonHomeExcludes);
            };
            copyFolder(source, source, target, filter);

            // copy stdlib home
            target =  new File(targetRootPath + File.separator +  "lib-python"+ File.separator + "3");
            if(!target.exists()) {
                target.mkdirs();
            }
            source = Paths.get(stdlibHome);
            filter = (f) -> {
                if(Files.isDirectory(f)) {
                    if(f.getFileName().toString().equals("idlelib") || f.getFileName().toString().equals("ensurepip") ||
                            f.getFileName().toString().equals("tkinter") || f.getFileName().toString().equals("turtledemo") ||
                            f.getFileName().toString().equals("__pycache__")) {
                        return true;
                    }
                } else {
                    // libpythonvm.* in same folder as stdlib is a windows issue only
                    if(f.getFileName().toString().equals("libpythonvm.dll")) {
                        return true;
                    }
                    if(!isIncluded(f.toAbsolutePath().toString(), pythonHomeIncludes)) {
                        return true;
                    }
                }
                return isExcluded(f.toAbsolutePath().toString(), pythonHomeExcludes);
            };
            copyFolder(source, source, target, filter);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean isIncluded(String filePath, List<String> includes) {
        if(includes == null || includes.isEmpty()) {
            return true;
        }
        return pathMatches(filePath, includes);
    }

    private boolean isExcluded(String filePath, List<String> excludes) {
        if(excludes == null || excludes.isEmpty()) {
            return false;
        }
        return pathMatches(filePath, excludes);
    }

    private boolean pathMatches(String filePath, List<String> includes) {
        if(File.separator.equals("\\")) {
            filePath = filePath.replaceAll("\\\\", "/");
        }
        for (String i: includes) {
            Pattern pattern = Pattern.compile(i);
            Matcher matcher = pattern.matcher(filePath);
            if(matcher.matches()) {
                return true;
            }
        }
        return false;
    }

    private void copyFolder(Path sourceRoot, Path file, File targetRoot, Predicate<Path> filter) throws IOException {
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
                    Path targetPath = Paths.get(targetRoot + File.separator + relFile.toString());
                    Path parent = targetPath.getParent();
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

    private void delete(Path homeDirectory) throws MojoExecutionException {
        try {
            try (var s = Files.walk(homeDirectory)) {
                s.sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
            }
        } catch (IOException e) {
            new MojoExecutionException(e);
        }
    }

    private void listGraalPyResources() throws MojoExecutionException {
        Path vfs = getVenvDirectory(project).getParent();
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
    
    private void manageVenv() throws MojoExecutionException {
        generateLaunchers();

        var venvDirectory = getVenvDirectory(project);

        if (packages == null || packages.isEmpty()) {
            getLog().info(String.format("No venv packages declared, deleting %s", venvDirectory));
            delete(venvDirectory);
            return;
        }

        var tag = venvDirectory.resolve("contents");
        var installedPackages = new HashSet<String>();
        var graalPyVersion = ExecGraalPyMojo.getGraalPyVersion(project);

        if (Files.isReadable(tag)) {
            try {
                var lines = Files.readAllLines(tag);
                if (lines.isEmpty() || !graalPyVersion.equals(lines.get(0))) {
                    getLog().info(String.format("Stale GraalPy venv, updating to %s", graalPyVersion));
                    delete(venvDirectory);
                } else {
                    for (int i = 1; i < lines.size(); i++) {
                        installedPackages.add(lines.get(i));
                    }
                }
            } catch (IOException e) {
                throw new MojoExecutionException(e);
            }
        } else {
            getLog().info(String.format("Creating GraalPy %s venv", graalPyVersion));
        }

        if (!Files.exists(venvDirectory)) {
            runLauncher("-m", "venv", venvDirectory.toString(), "--without-pip");
            runVenvBin(venvDirectory, "graalpy", List.of("-I", "-m", "ensurepip"));
        }

        deleteUnwantedPackages(venvDirectory, installedPackages);
        installWantedPackages(venvDirectory, installedPackages);

        try {
            Files.write(tag, List.of(graalPyVersion), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            Files.write(tag, packages, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new MojoExecutionException(e);
        }
    }

    static Path getVenvDirectory(MavenProject project) {
        return Path.of(project.getBuild().getOutputDirectory(), "vfs", "venv");
    }

    private void runLauncher(String... args) throws MojoExecutionException {
        var cmd = new ArrayList<String>();
        cmd.add(getLauncherPath().toString());
        cmd.addAll(List.of(args));
        getLog().info(String.join(" ", cmd));
        var pb = new ProcessBuilder(cmd);
        pb.inheritIO();
        try {
            pb.start().waitFor();
        } catch (IOException | InterruptedException e) {
            throw new MojoExecutionException(e);
        }
    }

    private void runPip(Path venvDirectory, String command, Collection<String> args) throws MojoExecutionException {
        var newArgs = new ArrayList<String>(args);
        newArgs.add(0, command);
        newArgs.add(0, "pip");
        newArgs.add(0, "-m");
        runVenvBin(venvDirectory, "graalpy", newArgs);
    }

    private void runVenvBin(Path venvDirectory, String bin, Collection<String> args) throws MojoExecutionException {
        var cmd = new ArrayList<String>();
        cmd.add(venvDirectory.resolve(BIN_DIR).resolve(bin + EXE_SUFFIX).toString());
        cmd.addAll(args);
        getLog().info(String.join(" ", cmd));
        var pb = new ProcessBuilder(cmd);
        pb.inheritIO();
        try {
            pb.start().waitFor();
        } catch (IOException | InterruptedException e) {
            throw new MojoExecutionException(e);
        }
    }

    private void installWantedPackages(Path venvDirectory, HashSet<String> installedPackages) throws MojoExecutionException {
        var pkgsToInstall = new HashSet<String>(packages);
        pkgsToInstall.removeAll(installedPackages);
        if (pkgsToInstall.isEmpty()) {
            return;
        }
        runPip(venvDirectory, "install", pkgsToInstall);
    }

    private void deleteUnwantedPackages(Path venvDirectory, HashSet<String> installedPackages) throws MojoExecutionException {
        var pkgsToRemove = new HashSet<String>(installedPackages);
        pkgsToRemove.removeAll(packages);
        if (pkgsToRemove.isEmpty()) {
            return;
        }
        runPip(venvDirectory, "uninstall", pkgsToRemove);
    }

    private Path getLauncherPath() {
        return Paths.get(project.getBuild().getDirectory(), LAUNCHER);
    }

    private void generateLaunchers() throws MojoExecutionException {
        getLog().info("Generating GraalPy launchers");
        String projectPath = project.getBuild().getDirectory();
        var launcher = getLauncherPath();
        if (!Files.exists(launcher)) {
            if (!IS_WINDOWS) {
                // just write our bash launcher
                var is = ManageResourcesMojo.class.getResourceAsStream("/" + LAUNCHER);
                try {
                    Files.createDirectories(launcher.getParent());
                    Files.copy(is, launcher);
                    var perms = Files.getPosixFilePermissions(launcher);
                    perms.addAll(List.of(PosixFilePermission.OWNER_EXECUTE, PosixFilePermission.GROUP_EXECUTE, PosixFilePermission.OTHERS_EXECUTE));
                    Files.setPosixFilePermissions(launcher, perms);
                } catch (IOException e) {
                    throw new MojoExecutionException(e);
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
                                cmd = r'mvn.cmd -f "%s" graalpy:exec "-Dexec.workingdir=%s"'
                                with open(tl, 'ab') as f:
                                    sz = f.write(cmd.encode('utf-16le'))
                                    f.write(struct.pack("@I", sz)) == 4
                                """,
                        launcher,
                        Paths.get(projectPath, "pom.xml").toString(),
                        projectPath);
                File tmp;
                try {
                    tmp = File.createTempFile("create_launcher", ".py");
                } catch (IOException e) {
                    throw new MojoExecutionException(e);
                }
                tmp.deleteOnExit();
                try (var wr = new FileWriter(tmp)) {
                    wr.write(script);
                } catch (IOException e) {
                    throw new MojoExecutionException(e);
                }
                ExecGraalPyMojo.runGraalPy(project, getLog(), tmp.getAbsolutePath());
            }
        }
    }

}
