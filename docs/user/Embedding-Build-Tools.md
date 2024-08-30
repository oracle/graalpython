---
layout: docs
toc_group: python
link_title: Embedding Build Tools
permalink: /reference-manual/python/Embedding-Build-Tools/
---

# Embedding Build Tools

The GraalPy **Maven** and **Gradle** plugins provide functionality to manage Python related resources typically
required for embedding Python code in Java-based applications:

- *Python application files* provided by the user, for example, Python sources which are part of the project.
- *Third-party Python packages*: installed by the plugin during the build according to the plugin configuration.
- *The Python standard library*, which is necessary to make Native Image generated executables self-contained.

Apart from physically managing and deploying those files, it is also necessary to make them available in Python
at runtime by configuring the **GraalPy Context** in your Java code accordingly. [GraalPyResources](https://github.com/oracle/graalpython/blob/master/graalpython/org.graalvm.python.embedding/src/org/graalvm/python/embedding/utils/GraalPyResources.java) provides
factory methods to create a Context preconfigured for accessing Python embedding relevant resources with
a **Virtual Filesystem** or from a dedicated **external directory**.

## Deployment

There are two modes how to deploy the resources: as Java resources using the Virtual Filesystem
to access them in Python, or as an external directory.

### Virtual Filesystem

The Python related resources are embedded in the application file, either in JAR or Native Image generated
executable, as standard Java resources. The GraalPy Virtual Filesystem accesses resource files as standard
Java resources and makes them available to Python code running in GraalPy. This is transparent to the Python code,
which can use standard Python IO to access those files.

Java resource files in a Maven or Gradle project are typically located in dedicated resources directories.
All resources subdirectories named `org.graalvm.python.vfs` are merged and mapped to a configurable
virtual filesystem mount point at the Python side, by default `/graalpy_vfs`. For example,
a Python file with the real filesystem path
`${project_resources_directory}/org.graalvm.python.vfs/src/foo/bar.py` will be accessible as
`/graalpy_vfs/src/foo/bar.py` in Python.

Use the following [GraalPyResources](https://github.com/oracle/graalpython/blob/master/graalpython/org.graalvm.python.embedding/src/org/graalvm/python/embedding/utils/GraalPyResources.java)
factory methods to create GraalPy Context preconfigured for the use of the Virtual Filesystem:

* `GraalPyResources.createContext()`
* `GraalPyResources.contextBuilder()`
* `GraalPyResources.contextBuilder(VirtualFileSystem)`

### External Directory

As an alternative to Java resources with the Virtual Filesystem, it is also possible to configure the Maven
or Gradle plugin to manage the contents of an external directory, which will **not be embedded** as Java resource
into the resulting application. The user is then responsible for the deployment of such directory. Python code
will access the files directly from the real filesystem.

Use the following [GraalPyResources](https://github.com/oracle/graalpython/blob/master/graalpython/org.graalvm.python.embedding/src/org/graalvm/python/embedding/utils/GraalPyResources.java)
factory methods to create GraalPy Context preconfigured for the use of an external directory:

* `GraalPyResources.createContextBuilder(Path)`

## Conventions

The factory methods in [GraalPyResources](https://github.com/oracle/graalpython/blob/master/graalpython/org.graalvm.python.embedding/src/org/graalvm/python/embedding/utils/GraalPyResources.java) rely on the following conventions, where the `${root}` is either
the external directory, or the Virtual System mount point on the Python side and
`${project_resources_directory}/org.graalvm.python.vfs` on the real filesystem:

- `${root}/src`: used for Python application files. This directory will be configured as the default
search path for Python module files (equivalent of `PYTHONPATH` environment variable).
- `${root}/venv`: used for the Python virtual environment holding installed third-party Python packages.
The Context will be configured as if it were executed from this virtual environment. Notably packages installed in this
virtual environment will be automatically available for importing.
- `${root}/home`: used for the Python standard library (equivalent of `PYTHONHOME` environment variable).

The Maven or Gradle plugin will fully manage the contents of the `vevn` and `home` subdirectories.
Any manual changes in these directories will be overridden by the plugin during the build.

- `${root}/venv`: the plugin creates virtual environment and installs required packages according
to the plugin configuration in `pom.xml` or `build.gradle`.
- `${root}/home`: the plugin copies the required (also configurable) parts of the Python standard library into this directory.
By default, the full standard library is used.

The `src` subdirectory is left to be manually populated by the user with custom Python scripts or modules.

## GraalPy Maven Plugin Configuration

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
- The **pythonHome** subsection declares what parts of the standard library should be deployed.

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
- If the **pythonResourcesDirectory** element is specified, then the given directory is used
as [external directory](#external-directory) and no Java resources are embedded.
Remember to use the appropriate `GraalPyResources` API to create the Context.
```xml
<configuration>
    <pythonResourcesDirectory>${basedir}/python-resources</pythonResourcesDirectory>
    ...
</configuration>
```

## GraalPy Gradle Plugin Configuration

Add the plugin configuration in the `GraalPy` block in the _build.gradle_ file:
The **packages** element declares a list of third-party Python packages to be downloaded and installed by the plugin.
- The Python packages and their versions are specified as if used with `pip`.
```
GraalPy {
  packages = ["termcolor==2.2"]
  ...
}
```
- the **pythonHome** subsection declares what parts of the standard library should be deployed.

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
- If the **pythonResourcesDirectory** element is specified, then the given directory is used
  as [external directory](#external-directory) and no Java resources are embedded.
  Remember to use the appropriate `GraalPyResources` API to create the Context.
```
GraalPy {
  pythonResourcesDirectory = file("$rootDir/python-resources")
  ...
}
```