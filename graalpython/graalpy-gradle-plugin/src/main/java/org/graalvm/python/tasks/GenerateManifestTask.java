package org.graalvm.python.tasks;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleScriptException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static org.graalvm.python.GraalPyGradlePlugin.GRAALPY_GROUP_ID;
import static org.graalvm.python.embedding.tools.vfs.VFSUtils.VFS_ROOT;


public abstract class GenerateManifestTask extends DefaultTask {

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
    private static final String GRAALPY_GRADLE_PLUGIN_ARTIFACT_ID = "graalpy-gradle-plugin";

    @OutputDirectory
    public abstract DirectoryProperty getManifestOutputDir();

    @TaskAction
    public void generateManifest() {
        Path metaInf = getMetaInfDirectory();
        Path resourceConfig = metaInf.resolve("resource-config.json");
        try {
            Files.createDirectories(resourceConfig.getParent());
            Files.writeString(resourceConfig, NATIVE_IMAGE_RESOURCES_CONFIG, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new GradleScriptException(String.format("failed to write %s", resourceConfig), e);
        }
        Path nativeImageProperties = metaInf.resolve("native-image.properties");
        try {
            Files.createDirectories(nativeImageProperties.getParent());
            Files.writeString(nativeImageProperties, NATIVE_IMAGE_ARGS, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new GradleScriptException(String.format("failed to write %s", nativeImageProperties), e);
        }
    }

    private Path getMetaInfDirectory() {
        return Path.of(getManifestOutputDir().get().getAsFile().getAbsolutePath(), "native-image", GRAALPY_GROUP_ID, GRAALPY_GRADLE_PLUGIN_ARTIFACT_ID);
    }
}
