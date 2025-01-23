---
layout: docs
toc_group: python
link_title: Embedding Build Tools
permalink: /reference-manual/python/Embedding-Build-Tools/
---

# Embedding Build Tools

The GraalPy **Maven** and **Gradle** plugins provide functionality to manage Python related resources
required for embedding Python code in Java-based applications:
- *Python application files* provided by the user, for example, Python sources which are part of the project.
- *Third-party Python packages* installed by the plugin during the build according to the plugin configuration.

Apart from physically managing and deploying those files, it is also necessary to make them available in Python at runtime by configuring the **GraalPy Context** in your Java code accordingly.
The [GraalPyResources](https://github.com/oracle/graalpython/blob/master/graalpython/org.graalvm.python.embedding/src/org/graalvm/python/embedding/utils/GraalPyResources.java) API provides factory methods to create a Context preconfigured for accessing Python, embedding relevant resources with a **Virtual Filesystem** or from a dedicated **external directory**.

## Deployment

There are two modes how to deploy the resources: as Java resources using the Virtual Filesystem to access them in Python, or as an external directory.

### Virtual Filesystem

The Python related resources are embedded in the application file, either in JAR or Native Image generated
executable, as standard Java resources.
The GraalPy Virtual Filesystem internally accesses the resource files as standard Java resources and makes them available to Python code running in GraalPy.
This is transparent to the Python code, which can use standard Python IO to access those files.

Java resource files in a Maven or Gradle project are typically located in dedicated resources directories, such as `src/main/resources`.
Moreover, there can be multiple resources directories and Maven or Gradle usually merges them.

User can choose relative Java resources path that will be made accessible in Python through the virtual filesystem,
by default it is `org.graalvm.python.vfs`. All resources subdirectories with this path are merged during build and mapped to a configurable Virtual Filesystem mount point at the Python side, by default `/graalpy_vfs`.
For example, a Python file with the real filesystem path `${project_resources_directory}/org.graalvm.python.vfs/src/foo/bar.py` will be accessible as `/graalpy_vfs/src/foo/bar.py` in Python.

Use the following [GraalPyResources](https://github.com/oracle/graalpython/blob/master/graalpython/org.graalvm.python.embedding/src/org/graalvm/python/embedding/utils/GraalPyResources.java)
factory methods to create GraalPy Context preconfigured for the use of the Virtual Filesystem:
* `GraalPyResources.createContext()`
* `GraalPyResources.contextBuilder()`
* `GraalPyResources.contextBuilder(VirtualFileSystem)`

#### Java Resource Path
Particularly when developing reusable libraries, it is recommended to use custom unique Java resources path for your
virtual filesystem to avoid conflicts with other libraries on the classpath or modulepath that may also use the
Virtual Filesystem. The recommended path is:
  ```
  GRAALPY-VFS/${project.groupId}/${project.artifactId}
  ```

The Java resources path must be configured in the Maven and Gradle plugins and must be also set to the same value
at runtime using the `VirtualFileSystem$Builder#resourceDirectory` API.

*Note regarding Java module system: resources in named modules are subject to the encapsulation rules specified by
[Module.getResourceAsStream](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Module.html#getResourceAsStream(java.lang.String)).
This is also the case of the default virtual filesystem location. When a resources directory is not
a valid Java package name, such as the recommended "GRAALPY-VFS", the resources are not subject to
the encapsulation rules and do not require additional module system configuration.*

### External Directory

As an alternative to Java resources with the Virtual Filesystem, it is also possible to configure the Maven or Gradle plugin to manage the contents of an external directory, which will **not be embedded** as a Java resource into the resulting application.
A user is then responsible for the deployment of such directory.
Python code will access the files directly from the real filesystem.

Use the following [GraalPyResources](https://github.com/oracle/graalpython/blob/master/graalpython/org.graalvm.python.embedding/src/org/graalvm/python/embedding/utils/GraalPyResources.java) factory methods to create GraalPy Context preconfigured for the use of an external directory:
* `GraalPyResources.createContextBuilder(Path)`

## Conventions

The factory methods in [GraalPyResources](https://github.com/oracle/graalpython/blob/master/graalpython/org.graalvm.python.embedding/src/org/graalvm/python/embedding/utils/GraalPyResources.java) rely on the following conventions, where the `${root}` is either an external directory, or a Virtual System mount point on the Python side and Java resources directories, such as `${project_resources_directory}/org.graalvm.python.vfs`, on the real filesystem:
- `${root}/src`: used for Python application files. This directory will be configured as the default search path for Python module files (equivalent to `PYTHONPATH` environment variable).
- `${root}/venv`: used for the Python virtual environment holding installed third-party Python packages.
The Context will be configured as if it is executed from this virtual environment. Notably packages installed in this
virtual environment will be automatically available for importing.

The Maven or Gradle plugin will fully manage the contents of the `venv` subdirectory.
Any manual change will be overridden by the plugin during the build.
- `${root}/venv`: the plugin creates a virtual environment and installs required packages according to the plugin configuration in _pom.xml_ or _build.gradle_.

The _src_ subdirectory is left to be manually populated by the user with custom Python scripts or modules.

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
</plugin>
```
- The **packages** element declares a list of third-party Python packages to be downloaded and installed by the plugin.
The Python packages and their versions are specified as if used with `pip`:
  ```xml
  <configuration>
      <packages>
          <package>termcolor==2.2</package>
          ...
      </packages>
      ...
  </configuration>
  ```

- The **resourceDirectory** element can specify the relative [Java resource path](#java-resource-path).
  Remember to use `VirtualFileSystem$Builder#resourceDirectory` when configuring the `VirtualFileSystem` in Java.
  ```xml
  <resourceDirectory>GRAALPY-VFS/${project.groupId}/${project.artifactId}</resourceDirectory>
  ```

- If the **externalDirectory** element is specified, then the given directory is used as an [external directory](#external-directory) and no Java resources are embedded.
Remember to use the appropriate `GraalPyResources` API to create the Context. This element and **resourceDirectory** are mutually exclusive.
  ```xml
  <configuration>
      <externalDirectory>${basedir}/python-resources</externalDirectory>
      ...
  </configuration>
  ```

## GraalPy Gradle Plugin Configuration

The plugin must be added to the plugins section in the _build.gradle_ file.
The **version** property defines which version of GraalPy to use.
```
plugins {
    // other plugins ...
    id 'org.graalvm.python' version '24.2.0'
}
```

The plugin automatically injects these dependencies of the same version as the plugin version:
  - `org.graalvm.python:python`
  - `org.graalvm.python:python-embedding`

The plugin can be configured in the `graalPy` block:

- The **packages** element declares a list of third-party Python packages to be downloaded and installed by the plugin.
  The Python packages and their versions are specified as if used with `pip`.
  ```
  graalPy {
    packages = ["termcolor==2.2"]
    ...
  }
  ```

- The **resourceDirectory** element can specify the relative [Java resource path](#java-resource-path).
  Remember to use `VirtualFileSystem$Builder#resourceDirectory` when configuring the `VirtualFileSystem` in Java.
  ```
  resourceDirectory = "GRAALPY-VFS/my.group.id/artifact.id"
  ```

- If the **externalDirectory** element is specified, then the given directory is used as an [external directory](#external-directory) and no Java resources are embedded.
  Remember to use the appropriate `GraalPyResources` API to create the Context.
  ```
  graalPy {
    externalDirectory = file("$rootDir/python-resources")
    ...
  }
  ```
- Boolean flag **community** switches the automatically injected
dependency `org.graalvm.python:python` to the community build: `org.graalvm.python:python-community`.
  ```
  graalPy {
    community = true
    ...
  }
  ```

### Related Documentation

* [Embedding Graal languages in Java](https://www.graalvm.org/reference-manual/embed-languages/)
* [Permissions for Python Embeddings](Embedding-Permissions.md)
