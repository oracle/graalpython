package org.graalvm.python.maven.plugin;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.List;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

import org.apache.maven.project.MavenProject;

@Mojo(name = "prepare-venv", defaultPhase = LifecyclePhase.GENERATE_RESOURCES,
                requiresDependencyCollection = ResolutionScope.COMPILE_PLUS_RUNTIME,
                requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME)
public class ManageVenvMojo extends AbstractMojo {
    private static final boolean IS_WINDOWS = System.getProperty("os.name").startsWith("Windows");
    private static final String LAUNCHER = IS_WINDOWS ? "graalpy.exe" : "graalpy.sh";
    private static final String BIN_DIR = IS_WINDOWS ? "Scripts" : "bin";
    private static final String EXE_SUFFIX = IS_WINDOWS ? ".exe" : "";

    @Parameter(defaultValue = "${project}", required = true, readonly = true)
    MavenProject project;

    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    private MavenSession session;

    @Parameter(property = "packages")
    Set<String> packages;

    static Path getVenvDirectory(MavenProject project) {
        return Path.of(project.getBuild().getOutputDirectory(), "vfs", "venv");
    }

    public void execute() throws MojoExecutionException {
        generateLaunchers();

        var venvDirectory = getVenvDirectory(project);
        var tag = venvDirectory.resolve("contents");
        var installedPackages = new HashSet<String>();
        var graalPyVersion = ExecGraalPyMojo.getGraalPyVersion(project);

        if (Files.isReadable(tag)) {
            try {
                var lines = Files.readAllLines(tag);
                if (lines.isEmpty() || !graalPyVersion.equals(lines.get(0))) {
                    getLog().info(String.format("Stale GraalPy venv, updating to %s", graalPyVersion));
                    try (var s = Files.walk(venvDirectory)) {
                        s.sorted(Comparator.reverseOrder())
                            .map(Path::toFile)
                            .forEach(File::delete);
                    }
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
                var is = ManageVenvMojo.class.getResourceAsStream("/" + LAUNCHER);
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
                                vl = os.path.join(venv.__path__[0], 'scripts', 'nt', 'graalpy.exe')
                                tl = os.path.join(r'%s')
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
