package org.graalvm.python.dsl;

import org.gradle.api.provider.SetProperty;

public interface PythonHomeInfo {
    SetProperty<String> getIncludes();
    SetProperty<String> getExcludes();
}
