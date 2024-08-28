---
layout: docs
toc_group: python
link_title: Embedding Build Tools
permalink: /reference-manual/python/Embedding-Build-Tools/
---

# Embedding Build Tools

The GraalPy Maven and Gradle plugins provide functionality to manage resources required for embedding Python in Java-based applications:

This embedding relevant resources can be one of the following:  
- Python application files, for example, Python sources which are part of the project.
- Third-party Python packages which can be accessed either from the projects Python sources or from Java.
- The Standard library which is necessary to make a generated native executable self-contained.

Besides physically managing and distributing this files by the plugins, it is also necessary to make them available at runtime by accordingly 
configuring the GraalPy context in your Java code. `GraalPyResources.java` TODO - how link to file/javadoc? provides factory methods 
to create a **GraalPy context** suitable for accessing Python embedding relevant resources with a **virtual filessystem** or from a dedicated **external directory**.

### Virtual filesystem

Resource files in a Maven or Gradle project are typically located in dedicated project resource directories, and then distributed in 
one application file (JAR or native image).

The GraalPy virtual filesystem can access resource files as standard Java resources and make them available to Python code running in GraalPy so
that it can use standard Python IO to access those files. 

Each kind of the resource files is held in a different project resource directory:
- `${project_resources_directory}/org.graalvm.python.vfs/src` - is used for Python application files. Contents of this directory are not directly managed by the plugin, 
only added to the final application file (JAR or native image).
- `${project_generated_resources_directory}/org.graalvm.python.vfs/venv` - this directory is used for the Python virtual environment holding third-party Python packages 
and its contents are generated and managed entirely by the plugin. Any manual changes may be overwritten. 
- `${project_generated_resources_directory}/org.graalvm.python.vfs/home` - this directory is used for the Standard library 
and its contents are generated and managed entirely by the plugin. Any manual changes may be overwritten.

`GraalPyResources.java` provides factory methods to create a GraalPy context with a default or custom virtual filesystem configuration. 
For more information on how to configure a virtual filesystem, refer to `VirtualFileSystem.Builder` TODO - how link to file/javadoc?.

### Resources in an external directory

As an alternative to the virtual filesystem, it is also possible to configure the plugin to hold the resource files in an external directory.
`GraalPyResources.java` provides methods to create a GraalPy context preconfigured to work with a dedicated resources directory as long at it has the following structure:
- `${resources_directory}/src` - is used for Python application files. Contents of this directory are not directly managed by the plugin,
only added to the final application file (JAR or native image). 
- `${resources_directory}/venv` - this directory is used for the Python virtual environment holding third-party Python packages
and its contents are generated and managed entirely by the plugin. Any manual changes may be overwritten. 
- `${resources_directory}/home` - this directory is used for the Standard library 
and its contents are generated and managed entirely by the plugin. Any manual changes may be overwritten.

Note, that by storing the resource files in an external directory, they are not bundled with the application file (JAR or native image) and must be provided separately.

### GraalPy Context

Regarding the particular resource paths, a GraalPy context instance created by factory methods in `GraalPyResources` is preconfigured in the following way:
- `${resources_root_directory}/home` - is reserved for the GraalPy Standard Library. GraalPy context will be configured to 
use this standard library as if set in `PYTHONHOME` environment variable.
- `${resources_root_directory}/venv` - is reserved for a Python virtual environment holding third-party packages. 
The context will be configured as if it were executed from this virtual environment. Notably packages installed in this 
virtual environment will be automatically available for importing.
- `${resources_root_directory}/src` - is reserved for python application files. GraalPy context will be configured to see those files as if set in `PYTHONPATH` environment variable.

where `${resources_root_directory}` is either the virtual filesystem resource root `/org.graalvm.python.vfs` or an external directory.

### GraalPy Maven Plugin Configuration

Add the plugin configuration in the `configuration` block of `graalpy-maven-plugin` in the _pom.xml_ file:
```xml
<plugin>
    <groupId>org.graalvm.python</groupId>
    <artifactId>graalpy-maven-plugin</artifactId>
    ...
    <configuration>
        ...
    </configuration>
    ...
```

The **packages** element declares a list of third-party Python packages to be downloaded and installed by the plugin.
- The Python packages and their versions are specified as if used with `pip`.
```xml
<configuration>
    <packages>
        <package>termcolor==2.2</package>
        ...
    </packages>
    ...
</configuration>
```
- The **pythonHome** subsection declares what parts of the standard library should be added to the final JAR file or native executable.
  
  Each `include` and `exclude` element is interpreted as a Java-like regular expression specifying which file paths should be included or excluded.

```xml
<configuration>
    <pythonHome>
        <includes>
            <include>.*</include>
            ...
        </includes>
        <excludes>
            <exclude></exclude>
            ...
        </excludes>
    </pythonHome>
    ...
</configuration>
```
- If the **pythonResourcesDirectory** element is specified, then the given directory is used for GraalPy resources instead of the standard Maven resource directories.
```xml
<configuration>
    <pythonResourcesDirectory>${basedir}/python-resources</pythonResourcesDirectory>
    ...
</configuration>
```

### GraalPy Gradle Plugin Configuration

Add the plugin configuration in the `GraalPy` block in the _build.gradle_ file:
The **packages** element declares a list of third-party Python packages to be downloaded and installed by the plugin.
- The Python packages and their versions are specified as if used with `pip`.
```
GraalPy {
  packages = ["termcolor==2.2"]
  ...
}
```
- the **pythonHome** subsection declares what parts of the standard library should be added to the final JAR file or native executable.
 
  Each element in the `includes` and `excludes` list is interpreted as a Java-like regular expression specifying which file paths should be included or excluded.
```
GraalPy {
  pythonHome {
    includes = [".*"]
    excludes = []
  }
  ...
}
```
- If the **pythonResourcesDirectory** element is specified, then the given directory is used for GraalPy resources instead of the standard Gradle resource directories.
```
GraalPy {
  pythonResourcesDirectory = file("$rootDir/python-resources")
  ...
}
```