package org.graalvm.python.dsl;

import org.gradle.api.Action;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Nested;

public interface GraalPyExtension {
    DirectoryProperty getPythonResourcesDirectory();

    ListProperty<String> getPackages();

    Property<Boolean> getIncludeVfsRootDir();

    @Nested
    PythonHomeInfo getPythonHome();

    default void pythonHome(Action<? super PythonHomeInfo> action) {
        action.execute(getPythonHome());
    }

}
