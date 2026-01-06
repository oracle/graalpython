# GraalPy Troubleshooting

This guide helps you resolve common issues when using GraalPy, whether running it standalone or embedded in Java applications.

## Virtual FileSystem Issues

### VirtualFileSystem Cannot Load Files

Some files may fail to load from the Virtual Filesystem even though they're included as resources. GraalPy automatically [extracts certain file types](Embedding-Build-Tools.md#extracting-files-from-virtual-filesystem) to the real filesystem, but you may still encounter errors.

Example error:

```bash
ImportError: cannot load /graalpy_vfs/venv/lib/python3.11/site-packages/_cffi_backend.graalpy250dev09433ef706-311-native-aarch64-darwin.so:
NFIUnsatisfiedLinkError: dlopen(/graalpy_vfs/venv/lib/python3.11/site-packages/_cffi_backend.graalpy250dev09433ef706-311-native-aarch64-darwin.so, 0x0002):
```

This indicates that the native extension requires access to the real filesystem.

**Solution**: Depending on how you [deploy Python resources](Embedding-Build-Tools.md#deployment), choose one of these approaches:

#### Option 1: Package Resources in JAR/Executable

If the problematic file is not automatically extracted (files other than `.so`, `.dylib`, `.pyd`, `.dll`, or `.ttf`), add it to the extraction filter:

```java
VirtualFileSystem vfs = VirtualFileSystem.newBuilder()
    .extractFilter(filename -> filename.endsWith(".your_extension"))
    .build();
```

If that doesn't resolve the issue, extract all resources to an external directory:

```java
// Extract the Python resources from the jar or native image into a directory
GraalPyResources.extractVirtualFileSystemResources(VirtualFileSystem.create(), externalResourceDirectoryPath);
// Create a GraalPy context configured with the external directory
Context context = GraalPyResources.contextBuilder(externalResourceDirectoryPath).build();
```

#### Option 2: Use External Directory

Configure your build tool to use an external directory by setting:

- **Maven**: `externalDirectory` tag in [Maven plugin](Embedding-Build-Tools.md#graalpy-maven-plugin)
- **Gradle**: `externalDirectory` field in [Gradle plugin](Embedding-Build-Tools.md#graalpy-gradle-plugin)

Then configure your context:

```java
// Create a context configured with an external Python resource directory
Context context = GraalPyResources.contextBuilder(externalResourceDirectoryPath).build();
```

> **Important**: When switching from Virtual FileSystem to external directory, move all user files from the previous Virtual FileSystem resource root to the new directory.

For more details about the Python resources in GraalPy Embedding please refer to the [Embedding Build Tools](Embedding-Build-Tools.md) documentation.

For more details about GraalPy context and Virtual FileSystem configuration please refer to [GraalPyResources](https://github.com/oracle/graalpython/blob/master/graalpython/org.graalvm.python.embedding/src/org/graalvm/python/embedding/GraalPyResources.java) and
[VirtualFileSystem](https://github.com/oracle/graalpython/blob/master/graalpython/org.graalvm.python.embedding/src/org/graalvm/python/embedding/VirtualFileSystem.java) javadoc.

## POSIX Backend Issues

### Issues with Java POSIX Backend

The Virtual FileSystem relies on GraalPy's Java POSIX backend. Some Python packages bypass Python's I/O and directly access files through native extensions.

Example error:

```bash
NotImplementedError: 'PyObject_AsFileDescriptor' not supported when using 'java' posix backend
```

This indicates that a Python package requires direct file descriptor access which is not supported by the Java POSIX backend.

**Solution**: Override the default Java backend by setting the native POSIX backend and extract resources from the Virtual FileSystem.

Depending on how you [deploy Python resources](Embedding-Build-Tools.md#deployment) in your application, you can do one of the following:

- if you need to package Python resources within your Jar or Native Image executable, then:

  ```java
  // Extract resources and configure native backend
  GraalPyResources.extractVirtualFileSystemResources(VirtualFileSystem.create(), externalResourceDirectoryPath);
  // create a Context.Builder configured with an external python resource directory
  Builder builder = GraalPyResources.contextBuilder(externalResourceDirectoryPath);
  // override the python.PosixModuleBackend option with "native"
  builder.option("python.PosixModuleBackend", "native");
  // create a context
  Context context = builder.build();
  ```

- or if you're able to ship Python resources in a separate directory, you have to set the `externalDirectory` tag in
  [Maven](Embedding-Build-Tools.md#graalpy-maven-plugin) or `externalDirectory` field in [Gradle](Embedding-Build-Tools.md#graalpy-gradle-plugin)
  and configure the GraalPy context accordingly:

  ```java
  // create a Context.Builder configured with an external python resource directory
  Builder builder = GraalPyResources.contextBuilder(externalResourceDirectoryPath);
  // override the python.PosixModuleBackend option with "native"
  builder.option("python.PosixModuleBackend", "native");
  // create a context
  Context context = builder.build();
  ```

> **Important**: When switching from Virtual FileSystem to external directory, move all user files from the previous Virtual FileSystem resource root to the new directory.

For more details about the Python resources in GraalPy Embedding please refer to the [Embedding Build Tools](Embedding-Build-Tools.md) documentation.

For more details about GraalPy context and Virtual FileSystem configuration please refer to [GraalPyResources](https://github.com/oracle/graalpython/blob/master/graalpython/org.graalvm.python.embedding/src/org/graalvm/python/embedding/GraalPyResources.java) and
[VirtualFileSystem](https://github.com/oracle/graalpython/blob/master/graalpython/org.graalvm.python.embedding/src/org/graalvm/python/embedding/VirtualFileSystem.java) javadoc.

## Import and Compatibility Issues

### ImportError Reports "Unknown Location"

An `ImportError` ending with `(unknown location)` typically occurs when an embedded Python package was built for a different operating system.

Example error:

```bash
ImportError: cannot import name 'exceptions' from 'cryptography.hazmat.bindings._rust' (unknown location)
```

This indicates that the Python package contains platform-specific native extensions that are incompatible with your current operating system.

**Solution**: Rebuild your project on the target operating system before running it.

## Dependency and Build Issues

### GraalVM JDK Compatibility

To enable runtime compilation when running GraalPy from a Maven or Gradle application, you must use compatible versions of GraalPy and Polyglot API dependencies with your GraalVM JDK version.

Example error:

```bash
Your Java runtime '23.0.1+11-jvmci-b01' with compiler version '24.1.1' is incompatible with polyglot version '24.1.0'.
```

This indicates version misalignment between your GraalVM JDK, compiler, and Polyglot dependencies.

**Solution**: Align the versions of your GraalPy and Polyglot dependencies according to the error message:

- Upgrade your JDK version, or 
- Update your Polyglot and GraalPy dependencies

Note: This can also apply when dependencies are transitively pulled in by other artifacts, for example, `micronaut-graalpy`.

### Deprecated Dependencies

You may encounter issues when using outdated or discontinued dependency artifacts in your project configuration.

Example error:

```bash
Could not find artifact org.graalvm.python:python-language-enterprise
```

This indicates you're trying to use a deprecated or discontinued dependency.

**Solution**: Replace `python-language-enterprise` with `org.graalvm.polyglot:python`.

### macOS Build System Issues (Meson/Cython)

On macOS, you may encounter build system errors when installing Python packages that use Meson or Cython.

Example error:

```bash
../meson.build:1:0: ERROR: Failed running 'cython', binary or interpreter not executable.
```

This is caused by the GraalPy launcher used internally by the Maven or Gradle GraalPy plugin for installing Python packages.

**Solution**: Set the Java system property `graalpy.vfs.venvLauncher` to a launcher from a downloaded [GraalPy](https://github.com/oracle/graalpython/releases/) distribution with a version matching your GraalPy Maven artifacts version.

Example command:

```bash
mvn package -Dgraalpy.vfs.venvLauncher={graalpy_directory}/Contents/Home/bin/graalpy
```

### Missing Language Dependencies

When running GraalPy applications, you may encounter runtime errors indicating missing language implementations.

Example error:

```bash
java.lang.IllegalStateException: No language and polyglot implementation was found on the module-path. Make sure at least one language is added to the module-path.
```

This indicates that the Python language dependency is missing from your Maven or Gradle configuration.

**Solution**: Add one of these dependencies to your project:

- `org.graalvm.polyglot:python` (Oracle GraalVM)
- `org.graalvm.polyglot:python-community` (Community Edition)
