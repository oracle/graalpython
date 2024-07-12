package org.graalvm.python;

import org.graalvm.python.dsl.GraalPyExtension;
import org.graalvm.python.tasks.GenerateManifestTask;
import org.graalvm.python.tasks.InstallPackagesTask;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.DependencySet;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.jvm.tasks.Jar;
import org.gradle.language.jvm.tasks.ProcessResources;

import java.util.Collections;
import java.util.List;


public abstract class GraalPyGradlePlugin implements Plugin<Project> {
    private static final String PYTHON_LAUNCHER_ARTIFACT_ID = "python-launcher";
    private static final String PYTHON_EMBEDDING_ARTIFACT_ID = "python-embedding";
    public static final String GRAALPY_GROUP_ID = "org.graalvm.python";

    private static final String POLYGLOT_GROUP_ID = "org.graalvm.polyglot";
    private static final String PYTHON_COMMUNITY_ARTIFACT_ID = "python-community";
    private static final String PYTHON_ARTIFACT_ID = "python";
    private static final String GRAALPY_GRADLE_PLUGIN_TASK_GROUP = "graalpy";

    private static final String DEFAULT_WRAPPER_DIRECTORY = "python-generated";

    GraalPyExtension extension;
    Project project;

    @Override
    public void apply(Project project) {
        this.project = project;
        project.getPluginManager().apply(JavaPlugin.class);


        this.extension = project.getExtensions().create("graalPy", GraalPyExtension.class);
        extension.getPythonHome().getIncludes().convention(List.of(".*"));
        extension.getPythonHome().getExcludes().convention(Collections.emptyList());
        extension.getPackages().convention(Collections.emptyList());

        var installPackagesTask = project.getTasks().register("installPackages", InstallPackagesTask.class);


        final var generateManifestTask = project.getTasks().register("generateManifest", GenerateManifestTask.class);
        project.getTasks().getByName(JavaPlugin.JAR_TASK_NAME, t -> ((Jar) t).getMetaInf().from(generateManifestTask));
        generateManifestTask.configure(t -> {
            t.getManifestOutputDir().convention(project.getLayout().getBuildDirectory().dir("GRAAL-META-INF"));
            t.setGroup(GRAALPY_GRADLE_PLUGIN_TASK_GROUP);
        });

        installPackagesTask.configure(t -> {
            t.getIncludes().set(extension.getPythonHome().getIncludes());
            t.getExcludes().set(extension.getPythonHome().getExcludes());
            t.getPackages().set(extension.getPackages());
            t.getIncludeVfsRoot().set(extension.getIncludeVfsRootDir());

            t.getOutput().set(extension.getPythonResourcesDirectory());

            t.setGroup(GRAALPY_GRADLE_PLUGIN_TASK_GROUP);
        });

        project.afterEvaluate(p -> {
            checkAndAddDependencies();

            if (!extension.getPythonResourcesDirectory().isPresent())
                ((ProcessResources) project.getTasks().getByName(JavaPlugin.PROCESS_RESOURCES_TASK_NAME)).with(project.copySpec().from(installPackagesTask));

            // Provide the default value after the isPresent check, otherwise isPresent always returns true
            extension.getPythonResourcesDirectory().convention(project.getLayout().getBuildDirectory().dir(DEFAULT_WRAPPER_DIRECTORY));
        });

    }


    private void checkAndAddDependencies() {
        project.getDependencies().add(JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME, "%s:%s:%s".formatted(GRAALPY_GROUP_ID, PYTHON_LAUNCHER_ARTIFACT_ID, getGraalPyVersion(project)));
        project.getDependencies().add(JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME, "%s:%s:%s".formatted(GRAALPY_GROUP_ID, PYTHON_EMBEDDING_ARTIFACT_ID, getGraalPyVersion(project)));
    }


    public static String getGraalPyVersion(Project project) {
        return getGraalPyDependency(project).getVersion();
    }

    public static Dependency getGraalPyDependency(Project project) {
        return resolveProjectDependencies(project).stream().filter(GraalPyGradlePlugin::isPythonArtifact).findFirst().orElseThrow(() -> new GradleException("Missing GraalPy dependency. Please add to your build.gradle either %s:%s or %s:%s".formatted(POLYGLOT_GROUP_ID, PYTHON_COMMUNITY_ARTIFACT_ID, POLYGLOT_GROUP_ID, PYTHON_ARTIFACT_ID)));
    }

    private static boolean isPythonArtifact(Dependency dependency) {
        return (POLYGLOT_GROUP_ID.equals(dependency.getGroup()) || GRAALPY_GROUP_ID.equals(dependency.getGroup())) &&
                (PYTHON_COMMUNITY_ARTIFACT_ID.equals(dependency.getName()) || PYTHON_ARTIFACT_ID.equals(dependency.getName()));
    }

    private static DependencySet resolveProjectDependencies(Project project) {
        return project.getConfigurations().getByName("implementation").getAllDependencies();
    }


}