package org.graalvm.python.maven.plugin;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

@Mojo(name = "vfs-index", defaultPhase = LifecyclePhase.PROCESS_RESOURCES)
public class ListGraalPyResourcesMojo extends AbstractMojo {
    @Parameter(defaultValue = "${project}", required = true, readonly = true)
    MavenProject project;

    @Override
    public void execute() throws MojoExecutionException {
        Path vfs = ManageVenvMojo.getVenvDirectory(project).getParent();
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
}
