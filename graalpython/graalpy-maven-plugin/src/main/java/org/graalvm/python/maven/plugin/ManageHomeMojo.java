package org.graalvm.python.maven.plugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

import org.apache.maven.project.MavenProject;

@Mojo(name = "prepare-embedded-home", defaultPhase = LifecyclePhase.GENERATE_RESOURCES,
                requiresDependencyCollection = ResolutionScope.COMPILE_PLUS_RUNTIME,
                requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME)
public class ManageHomeMojo extends AbstractMojo {
    @Parameter(defaultValue = "${project}", required = true, readonly = true)
    MavenProject project;

    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    private MavenSession session;

    static Path getHomeDirectory(MavenProject project) {
        return Path.of(project.getBuild().getOutputDirectory(), "vfs", "home");
    }

    public void execute() throws MojoExecutionException {
        var homeDirectory = getHomeDirectory(project);
        var tag = homeDirectory.resolve("tagfile");
        var graalPyVersion = ExecGraalPyMojo.getGraalPyVersion(project);

        if (Files.isReadable(tag)) {
            try {
                var lines = Files.readAllLines(tag);
                if (lines.isEmpty() || !graalPyVersion.equals(lines.get(0))) {
                    getLog().info(String.format("Stale GraalPy home, updating to %s", graalPyVersion));
                    try (var s = Files.walk(homeDirectory)) {
                        s.sorted(Comparator.reverseOrder())
                            .map(Path::toFile)
                            .forEach(File::delete);
                    }
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
            ExecGraalPyMojo.runGraalPy(project,
                            getLog(),
                            "-c",
                            String.format("__import__('shutil').copytree(__graalpython__.home, '%s', dirs_exist_ok=True)",
                                            homeDirectory.toAbsolutePath().toString()));
        }

        try {
            Files.write(tag, List.of(graalPyVersion), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new MojoExecutionException(e);
        }
    }
}
