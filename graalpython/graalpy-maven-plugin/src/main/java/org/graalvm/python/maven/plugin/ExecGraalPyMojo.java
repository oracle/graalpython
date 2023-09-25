package org.graalvm.python.maven.plugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;


@Mojo(name = "exec", defaultPhase = LifecyclePhase.NONE,
                requiresDependencyCollection = ResolutionScope.COMPILE_PLUS_RUNTIME,
                requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME)
public class ExecGraalPyMojo extends AbstractMojo {
    private static final String PYTHON_LANGUAGE = "python-language";
    private static final String PYTHON_RESOURCES = "python-resources";
    private static final String PYTHON_LAUNCHER = "python-launcher";
    private static final String GRAALPY_GROUP = "org.graalvm.python";

    @Parameter(defaultValue = "${project}", required = true, readonly = true)
    MavenProject project;

    @Override
    public void execute() throws MojoExecutionException {
        String argcStr = System.getProperty("exec.argc");
        int argc = 0;
        if (argcStr != null) {
            try {
                argc = Integer.parseInt(argcStr);
            } catch (NumberFormatException e) {
                throw new MojoExecutionException("exec.argc must be an integer", e);
            }
        }
        var args = new String[argc];
        for (int i = 1; i <= argc; i++) {
            args[i - 1] = System.getProperty("exec.arg" + i);
            if (args[i - 1] == null) {
                throw new MojoExecutionException(String.format("exec.arg%d missing", i));
            }
        }
        runGraalPy(project, getLog(), args);
    }

    static void runGraalPy(MavenProject project, Log log, String... args) throws MojoExecutionException {
        var classpath = calculateClasspath(project, log);
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
        }
        log.debug(String.format("Running GraalPy: %s", String.join(" ", cmd)));
        pb.inheritIO();
        try {
            pb.start().waitFor();
        } catch (IOException | InterruptedException e) {
            throw new MojoExecutionException(e);
        }
    }

    static String getGraalPyVersion(MavenProject project) throws MojoExecutionException {
        return getGraalPyArtifact(project, PYTHON_LANGUAGE).getVersion();
    }

    private static Collection<Artifact> resolveProjectDependencies(MavenProject project) {
        return project.getArtifacts()
            .stream()
            .filter(a -> !"test".equals(a.getScope()))
            .collect(Collectors.toList());
    }

    private static Artifact getGraalPyArtifact(MavenProject project, String aid) throws MojoExecutionException {
        var projectArtifacts = resolveProjectDependencies(project);
        for (var a : projectArtifacts) {
            if (a.getGroupId().equals(GRAALPY_GROUP) && a.getArtifactId().equals(aid)) {
                return a;
            }
        }
        throw new MojoExecutionException(String.format("Missing GraalPy dependency %s:%s. Please add it to your pom", GRAALPY_GROUP, aid));
    }

    private static HashSet<String> calculateClasspath(MavenProject project, Log log) throws MojoExecutionException {
        var classpath = new HashSet<String>();
        getGraalPyArtifact(project, PYTHON_LANGUAGE);
        getGraalPyArtifact(project, PYTHON_LAUNCHER);
        getGraalPyArtifact(project, PYTHON_RESOURCES);
        for (var r : resolveProjectDependencies(project)) {
            classpath.add(r.getFile().getAbsolutePath());
        }
        return classpath;
    }

}
