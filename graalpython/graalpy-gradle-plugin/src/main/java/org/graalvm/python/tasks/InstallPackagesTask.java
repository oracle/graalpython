package org.graalvm.python.tasks;

import org.graalvm.python.GradleLogger;
import org.graalvm.python.embedding.tools.exec.GraalPyRunner;
import org.graalvm.python.embedding.tools.vfs.VFSUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.GradleScriptException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.*;
import org.gradle.api.tasks.*;
import org.gradle.api.tasks.Optional;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.*;
import java.util.stream.Collectors;

import static org.graalvm.python.GraalPyGradlePlugin.getGraalPyDependency;
import static org.graalvm.python.GraalPyGradlePlugin.getGraalPyVersion;
import static org.graalvm.python.embedding.tools.vfs.VFSUtils.*;

public abstract class InstallPackagesTask extends DefaultTask {
    private static final String GRAALPY_GROUP_ID = "org.graalvm.python";

    private static final String GRAALPY_MAIN_CLASS = "com.oracle.graal.python.shell.GraalPythonMain";

    private static final boolean IS_WINDOWS = System.getProperty("os.name").startsWith("Windows");
    private static final String LAUNCHER = IS_WINDOWS ? "graalpy.exe" : "graalpy.sh";

    private static final String INCLUDE_PREFIX = "include:";

    private static final String EXCLUDE_PREFIX = "exclude:";

    private Set<String> launcherClassPath;

    @Input
    @Optional
    public abstract Property<Boolean> getIncludeVfsRoot();

    @Input
    public abstract ListProperty<String> getPackages();

    @Input
    public abstract ListProperty<String> getIncludes();

    @Input
    public abstract ListProperty<String> getExcludes();

    @OutputDirectory
    public abstract DirectoryProperty getOutput();

    @TaskAction
    public void exec() {
        manageHome();
        manageVenv();
        listGraalPyResources();
    }

    private void manageHome() {
        Path homeDirectory = getHomeDirectory();
        Path tagfile = homeDirectory.resolve("tagfile");
        String graalPyVersion = getGraalPyVersion(getProject());

        List<String> includes = new ArrayList<>(getIncludes().get());
        List<String> excludes = new ArrayList<>(getExcludes().get());

        trim(includes);
        trim(excludes);

        if (Files.isReadable(tagfile)) {
            List<String> lines;
            try {
                lines = Files.readAllLines(tagfile);
            } catch (IOException e) {
                throw new GradleScriptException(String.format("failed to read tag file %s", tagfile), e);
            }
            if (lines.isEmpty() || !graalPyVersion.equals(lines.get(0))) {
                getLogger().lifecycle(String.format("Stale GraalPy home, updating to %s", graalPyVersion));
                delete(homeDirectory);
            }
            if (pythonHomeChanged(includes, excludes, lines)) {
                getLogger().lifecycle(String.format("Deleting GraalPy home due to changed includes or excludes"));
                delete(homeDirectory);
            }
        }

        try {
            if (!Files.exists(homeDirectory)) {
                getLogger().lifecycle(String.format("Creating GraalPy %s home in %s", graalPyVersion, homeDirectory));
                Files.createDirectories(homeDirectory.getParent());
                VFSUtils.copyGraalPyHome(calculateLauncherClasspath(), homeDirectory, includes, excludes, GradleLogger.of(getLogger()));
            }
            Files.write(tagfile, List.of(graalPyVersion), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            write(tagfile, includes, INCLUDE_PREFIX);
            write(tagfile, excludes, EXCLUDE_PREFIX);
        } catch (IOException | InterruptedException e) {
            throw new GradleException(String.format("failed to copy graalpy home %s", homeDirectory), e);
        }
    }

    private void manageVenv() {
        generateLaunchers();

        Path venvDirectory = getVenvDirectory();

        Path venvContents = venvDirectory.resolve("contents");
        List<String> installedPackages = new ArrayList<>();
        String graalPyVersion = getGraalPyVersion(getProject());

        var packages = getPackages().getOrElse(null);

        if (packages != null && !packages.isEmpty()) {
            trim(packages);
        }

        if (packages == null || packages.isEmpty()) {
            getLogger().lifecycle(String.format("No venv packages declared, deleting %s", venvDirectory));
            delete(venvDirectory);
            return;
        }

        if (Files.isReadable(venvContents)) {
            List<String> lines = null;
            try {
                lines = Files.readAllLines(venvContents);
            } catch (IOException e) {
                throw new GradleScriptException(String.format("failed to read tag file %s", venvContents), e);
            }
            if (lines.isEmpty() || !graalPyVersion.equals(lines.get(0))) {
                getLogger().lifecycle(String.format("Stale GraalPy venv, updating to %s", graalPyVersion));
                delete(venvDirectory);
            } else {
                for (int i = 1; i < lines.size(); i++) {
                    installedPackages.add(lines.get(i));
                }
            }
        } else {
            getLogger().lifecycle(String.format("Creating GraalPy %s venv", graalPyVersion));
        }

        if (!Files.exists(venvDirectory)) {
            runLauncher(getLauncherPath().toString(), "-m", "venv", venvDirectory.toString(), "--without-pip");
            runVenvBin(venvDirectory, "graalpy", "-I", "-m", "ensurepip");
        }

        deleteUnwantedPackages(venvDirectory, installedPackages, packages);
        installWantedPackages(venvDirectory, installedPackages, packages);

        try {
            Files.write(venvContents, List.of(graalPyVersion), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            Files.write(venvContents, packages, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new GradleScriptException(String.format("failed to write tag file %s", venvContents), e);
        }
    }

    private void listGraalPyResources() {
        Path vfs = getHomeDirectory().getParent();
        if (Files.exists(vfs)) {
            try {
                VFSUtils.generateVFSFilesList(vfs);
            } catch (IOException e) {
                throw new GradleScriptException(String.format("Failed to generate files list in '%s'", vfs), e);
            }
        }
    }

    private Set<String> calculateLauncherClasspath() {
        if (launcherClassPath == null) {
            var addedPluginDependency = getProject().getConfigurations().getByName("runtimeClasspath").getAllDependencies().stream().filter(d -> d.getGroup().equals(GRAALPY_GROUP_ID) && d.getName().equals("python-launcher") && d.getVersion().equals(getGraalPyVersion(getProject()))).findFirst().orElseThrow();
            launcherClassPath = getProject().getConfigurations().getByName("runtimeClasspath").files(addedPluginDependency).stream().map(File::toString).collect(Collectors.toSet());
            launcherClassPath.addAll(getProject().getConfigurations().getByName("runtimeClasspath").files(getGraalPyDependency(getProject())).stream().map(File::toString).collect(Collectors.toSet()));
        }
        return launcherClassPath;
    }

    private boolean pythonHomeChanged(List<String> includes, List<String> excludes, List<String> lines)  {
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
        boolean includeDidNotChange = prevIncludes.size() == includes.size() &&  prevIncludes.containsAll(includes);
        boolean excludeDidNotChange = prevExcludes.size() == excludes.size() &&  prevExcludes.containsAll(excludes);
        return !(includeDidNotChange && excludeDidNotChange);
    }

    private void generateLaunchers() {
        getLogger().lifecycle("Generating GraalPy launchers");
        var launcher = getLauncherPath();
        if (!Files.exists(launcher)) {
            var java = Paths.get(System.getProperty("java.home"), "bin", "java");
            var classpath = calculateLauncherClasspath();
            if (!IS_WINDOWS) {
                var script = String.format("""
                                #!/usr/bin/env bash
                                %s -classpath %s %s --python.Executable="$0" "$@"
                                """,
                        java,
                        String.join(File.pathSeparator, classpath),
                        GRAALPY_MAIN_CLASS);
                try {
                    Files.createDirectories(launcher.getParent());
                    Files.writeString(launcher, script);
                    var perms = Files.getPosixFilePermissions(launcher);
                    perms.addAll(List.of(PosixFilePermission.OWNER_EXECUTE, PosixFilePermission.GROUP_EXECUTE, PosixFilePermission.OTHERS_EXECUTE));
                    Files.setPosixFilePermissions(launcher, perms);
                } catch (IOException e) {
                    throw new GradleException(String.format("failed to create launcher %s", launcher), e);
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
                        launcher,
                        java,
                        String.join(File.pathSeparator, classpath),
                        GRAALPY_MAIN_CLASS);
                File tmp;
                try {
                    tmp = File.createTempFile("create_launcher", ".py");
                } catch (IOException e) {
                    throw new GradleScriptException("failed to create tmp launcher", e);
                }
                tmp.deleteOnExit();
                try (var wr = new FileWriter(tmp)) {
                    wr.write(script);
                } catch (IOException e) {
                    throw new GradleScriptException(String.format("failed to write tmp launcher %s", tmp), e);
                }
                runGraalPy(tmp.getAbsolutePath());
            }
        }
    }

    private void runGraalPy(String... args) {
        var classpath = calculateLauncherClasspath();
        try {
            GraalPyRunner.run(classpath, GradleLogger.of(getLogger()), args);
        } catch (IOException | InterruptedException e) {
            throw new GradleScriptException("failed to run Graalpy launcher", e);
        }
    }

    private Path getLauncherPath() {
        return Paths.get(getProject().getBuildDir().getAbsolutePath(), LAUNCHER);
    }


    private static void write(Path tag, Collection<String> list, String prefix) throws IOException {
        if (list != null && !list.isEmpty()) {
            Files.write(tag, list.stream().map(l -> prefix + l).collect(Collectors.toList()), StandardOpenOption.APPEND);
        }
    }

    private Path getHomeDirectory() {
        return getResourceDirectory(VFS_HOME);
    }

    private Path getVenvDirectory() {
        return getResourceDirectory(VFS_VENV);
    }

    private Path getResourceDirectory(String type) {
        return Path.of(getOutput().get().getAsFile().toURI()).resolve(getIncludeVfsRoot().getOrElse(true) ? VFS_ROOT : "").resolve(type);
    }

    private static void delete(Path homeDirectory) {
        try {
            try (var s = Files.walk(homeDirectory)) {
                s.sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
            }
        } catch (IOException e) {
            throw new GradleScriptException(String.format("failed to delete %s", homeDirectory), e);
        }
    }

    private static void trim(List<String> l) {
        Iterator<String> it = l.iterator();
        while (it.hasNext()) {
            String p = it.next();
            if (p == null || p.trim().isEmpty()) {
                it.remove();
            }
        }
    }

    private void installWantedPackages(Path venvDirectory, List<String> installedPackages, List<String> wantedPackages) {
        var pkgsToInstall = new HashSet<>(wantedPackages);
        installedPackages.forEach(pkgsToInstall::remove);
        if (pkgsToInstall.isEmpty()) {
            return;
        }
        getLogger().lifecycle("Installing packages {}", pkgsToInstall);
        runPip(venvDirectory, "install", pkgsToInstall.toArray(new String[pkgsToInstall.size()]));
    }

    private void deleteUnwantedPackages(Path venvDirectory, List<String> installedPackages, List<String> wantedPackages) {
        List<String> args = new ArrayList<>(installedPackages);
        args.removeAll(wantedPackages);
        if (args.isEmpty()) {
            return;
        }
        args.add(0, "-y");
        getLogger().lifecycle("Removing packages {}", args);
        runPip(venvDirectory, "uninstall", args.toArray(new String[args.size()]));
    }


    private void runLauncher(String launcherPath, String... args) {
        try {
            GraalPyRunner.runLauncher(launcherPath, GradleLogger.of(getLogger()), args);
        } catch (IOException | InterruptedException e) {
            throw new GradleScriptException(String.format("failed to execute launcher command %s", List.of(args)), e);
        }
    }

    private void runPip(Path venvDirectory, String command, String... args) {
        try {
            GraalPyRunner.runPip(venvDirectory, command, GradleLogger.of(getLogger()), args);
        } catch (IOException | InterruptedException e) {
            throw new GradleScriptException(String.format("failed to execute pip", args), e);
        }
    }

    private void runVenvBin(Path venvDirectory, String bin, String... args) {
        try {
            GraalPyRunner.runVenvBin(venvDirectory, bin, GradleLogger.of(getLogger()), args);
        } catch (IOException | InterruptedException e) {
            throw new GradleScriptException(String.format("failed to execute venv", args), e);
        }
    }
}
