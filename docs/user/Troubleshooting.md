# GraalPy Troubleshooting

[[TOC]]

## GraalPy Embedding

#### VirtualFileSystem cannot load a file
There are situations where a file cannot be loaded even though it is part of the Virtual Filesystem resources.
GraalPy tries to prevent such situations by automatically [extracting](Embedding-Build-Tools.md#extracting-files-from-virtual-filesystem) 
some well known files to the real filesystem, but if you see an error like:
```
ImportError: cannot load /graalpy_vfs/venv/lib/python3.11/site-packages/_cffi_backend.graalpy250dev09433ef706-311-native-aarch64-darwin.so: 
NFIUnsatisfiedLinkError: dlopen(/graalpy_vfs/venv/lib/python3.11/site-packages/_cffi_backend.graalpy250dev09433ef706-311-native-aarch64-darwin.so, 0x0002): 
```
then the default behavior did not work as intended.

Depending on how you [deploy Python resources](Embedding-Build-Tools.md#deployment) in your application, you can try one of the following:
- if you need to package resources within your Jar or Native Image executable:
  - if the problematic file is not one of the following types: `.so`, `.dylib`, `.pyd`, `.dll`, or `.ttf`, which are extracted to 
  the real filesystem by default, you can simply attempt to add it to the extraction filter using: 
  ```java 
  VirtualFileSystem.Builder.extractFilter(filter);
  ```
  - if the previous does not help, it is also possible to extract all Python resources into a user-defined directory before creating a GraalPy
    context, and then configure the context to use that directory:
  ```java
  // extract the Python resources from the jar or native image into a given directory
  GraalPyResources.extractVirtualFileSystemResources(VirtualFileSystem.create(), externalResourceDirectoryPath);
  // create a GraalPy context configured with an external Python resource directory
  Context context = GraalPyResources.contextBuilder(externalResourceDirectoryPath).build();
  ```
- or if you're able to ship resources in a separate directory, you have to set the `externalDirectory` tag in  
  [Maven](Embedding-Build-Tools.md#graalpy-maven-plugin) or `externalDirectory` field in [Gradle](Embedding-Build-Tools.md#graalpy-gradle-plugin) 
  and also configure the GraalPy context to use that directory as well: 
  ```java
  // create a Context configured with an external Python resource directory
  Context context = GraalPyResources.contextBuilder(externalResourceDirectoryPath).build();
  ```
  Please **note**, that if switching from Virtual FileSystem to an external directory, also all **user files** from the previous
  Virtual FileSystem resource root have to be moved into that directory as well.

For more details about the Python resources in GraalPy Embedding please refer to the [Embedding Build Tools](Embedding-Build-Tools.md) documentation.

For more details about GraalPy context and Virtual FileSystem configuration please refer to [GraalPyResources](https://github.com/oracle/graalpython/blob/master/graalpython/org.graalvm.python.embedding/src/org/graalvm/python/embedding/GraalPyResources.java) and
[VirtualFileSystem](https://github.com/oracle/graalpython/blob/master/graalpython/org.graalvm.python.embedding/src/org/graalvm/python/embedding/VirtualFileSystem.java) javadoc.

#### Issues with GraalPy 'java' posix backend
The Virtual FileSystem is built on the Truffle filesystem and relies on the GraalPy Java POSIX backend. 
Unfortunately, certain Python packages bypass Python's I/O and directly access files through their 
native extensions. If you encounter an error like:
```
NotImplementedError: 'PyObject_AsFileDescriptor' not supported when using 'java' posix backend
```
then you have to override the default `java` GraalPy backend option byt setting the `native` POSIX backend
and completely omit the Virtual FileSystem at runtime. 

Depending on how you [deploy Python resources](Embedding-Build-Tools.md#deployment) in your application, 
you can do one of the following:

- if you need to package Python resources within your Jar or Native Image executable, then:
  ```java
  // extract the Python resources from the jar or native image into a given directory
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
  Please **note**, that if switching from Virtual FileSystem to an external directory, also all **user files** from the previous
  Virtual FileSystem resource root have to be moved into that directory as well.

For more details about the Python resources in GraalPy Embedding please refer to the [Embedding Build Tools](Embedding-Build-Tools.md) documentation.

For more details about GraalPy context and Virtual FileSystem configuration please refer to [GraalPyResources](https://github.com/oracle/graalpython/blob/master/graalpython/org.graalvm.python.embedding/src/org/graalvm/python/embedding/GraalPyResources.java) and
[VirtualFileSystem](https://github.com/oracle/graalpython/blob/master/graalpython/org.graalvm.python.embedding/src/org/graalvm/python/embedding/VirtualFileSystem.java) javadoc.

### Maven and Gradle applications

####  ImportError reports "unknown location"
A possible cause of an `ImportError` ending with `(unknown location)` when running a GraalPy Java Embedding project might be 
that an embedded Python package was built for a different operating system. If you see an error like the following:
```
ImportError: cannot import name 'exceptions' from 'cryptography.hazmat.bindings._rust' (unknown location)
``` 
You probably need to rebuild your project on the correct operating system before running it.

#### GraalVM JDK Compatibility
To enable runtime compilation when running GraalPy from a Maven or Gradle application, 
you must use versions of GraalPy and the Polyglot API dependencies that are compatible 
with the specified GraalVM JDK version. If you see errors like the following:

```
Your Java runtime '23.0.1+11-jvmci-b01' with compiler version '24.1.1' is incompatible with polyglot version '24.1.0'.
```
You need to keep the versions of your GraalPy and Polyglot dependencies according to the error message, 
so either upgrade the version of your JDK or your polyglot and GraalPy dependencies.  

Note, this can also apply to cases when your dependencies are transitively pulled in by another artifact, 
e.g. micronaut-graalpy.

#### The following artifacts could not be resolved: org.graalvm.python:python-language-enterprise
`python-language-enterprise` was discontinued, use `org.graalvm.polyglot:python` instead.

#### Issues with Meson build system when installing Python packages on OSX with Maven or Gradle GraalPy plugin
Errors like the following:
```
../meson.build:1:0: ERROR: Failed running 'cython', binary or interpreter not executable.
```

could be caused by the GraalPy launcher used internally by the Maven or Gradle GraalPy plugin 
for installing Python packages. Currently, there is no straightforward solution. However, 
a workaround is to set the Java system property graalpy.vfs.venvLauncher to a launcher from 
a downloaded [GraalPy](https://github.com/oracle/graalpython/releases/) distribution with a version
matching the GraalPy Maven artifacts version.

e.g.
```
mvn package -Dgraalpy.vfs.venvLauncher={graalpy_directroy}/Contents/Home/bin/graalpy
```

#### No language and polyglot implementation was found on the module-path.
If you see an error like:
```
java.lang.IllegalStateException: No language and polyglot implementation was found on the module-path. Make sure at last one language is added to the module-path. 
```
you are probably missing the python langauge dependency in your Maven of Gradle configuration file. 
You need to add either `org.graalvm.polyglot:python` or `org.graalvm.polyglot:python-community` to your dependencies.

