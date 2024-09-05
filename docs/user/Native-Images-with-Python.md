---
layout: docs
toc_group: python
link_title: Native Java-Python Applications
permalink: /reference-manual/python/native-applications/
redirect_from: /reference-manual/python/native-image/
---

# Native Executables with Python

GraalPy supports GraalVM Native Image to generate native binaries of Java applications that use GraalPy.

## Quickstart

If you started with the [Maven archetype](README.md), the generated _pom.xml_ makes it easy to generate a native executable using the [Maven plugin for Native Image building](https://graalvm.github.io/native-build-tools/latest/maven-plugin.html).

To build the application, run:
```bash
mvn -Pnative package
```
The command packages the project and creates a native executable.

Take a look at the generated files _pom.xml_ and _Main.java_.
They are documented to explain how Python resources are included in the resulting binary.
The generated project should be viewed as a starting point.
It includes the entire Python standard library, so the Python code can invoke all of the standard library code.
The resources can be manually pruned to reduce the included Python libraries to the necessary amount, reducing both the size of the package and the time to start up.
This Java example demonstrates some useful default options for the Python context, but other settings may be desirable to further control what the Python code is allowed to do.

## Reducing Binary Size

Python is a large language.
"Batteries included" has long been a core tenet of CPython.
As a compatible replacement, GraalPy includes most of those "batteries" as well.
This can result in significant increases in binary size when including GraalPy in a Java application.

Only you as the developer know your specific embedding scenario.
The defaults may include much more than needed for any specific application.
An embedded Python-in-Java application usually has more limited use cases for the Python interpreter than the full GraalPy distribution, and often you can know ahead of time whether certain features are needed.
Some features (for example, cryptographic algorithms or socket access) may even be undesirable to include in some cases.
Thus, when embedding GraalPy in a Java application, the binary size can be reduced and security improved by excluding components of the Python language.

### Excluding Python Components

GraalPy defines a few system properties that can be passed on the `native-image` command line to exclude aspects of the language.
The options can, when taken together, reduce the size of the executable by around 20%.
These are:

* `python.WithoutSSL=true` - This option removes the `ssl` module.
  If no secure network access or certificate checking is required, this removes Java's SSL classes and the BouncyCastle library.
* `python.WithoutDigest=true` - This option removes the `_md5`, `_sha1`, `_sha256`, `_sha512`, `_sha3`, and `_hashlib` modules.
  This removes the direct usages of `java.security.MessageDigest` and `javax.crypto.Mac` from GraalPy.
* `python.WithoutPlatformAccess=true` - This removes the `signal` and `subprocess` modules, removes accesses to process properties such as the Unix UID and GID, or setting the Java default time zone.
  This has no significant impact on binary size, but if these are unwanted capabilities that are dynamically disabled with context options anyway, they can also be removed ahead of time.
* `python.WithoutCompressionLibraries=true` - This removes the `zlib`, `lzma`, `bzip2`, and `zipimporter` modules and related classes.
  These modules have both native and pure Java implementations (the former for performance, the latter for better sandboxing); however, if they are not needed, they can be removed entirely.
* `python.WithoutNativePosix=true` - The default `os` module backend is a pure Java implementation when GraalPy is embedded rather than run via its launcher.
  The native POSIX backend of GraalPy is recommended only for 100% compatibility with CPython's POSIX interfaces, and if not used, can be removed from the build with this option.
* `python.WithoutJavaInet=true` - The Java implementation of Python's `socket` module is based on Java's networking classes.
  If network access is denied for an embedding scenario, this option can reduce the binary size further.
* `python.AutomaticAsyncActions=false` - Signal handling, Python weak reference callbacks, and cleaning up native resources is usually achieved automatically by spawning GraalPy daemon threads that submit safepoint actions to the Python main thread.
  This uses an `ExecutorService` with a thread pool.
  If you want to disallow such extra threads or avoid pulling in `ExecutorService` and related classes, then set this property to `false` and retrieve the `PollPythonAsyncActions` object from the context's polyglot bindings.
  This object is executable and can be used to trigger Python asynchronous actions at the locations you desire.
* `python.WithoutJNI=true` - This option removes any code that uses JNI. As a consequence, you cannot use the HPy JNI backend and maybe other parts that rely on JNI.


### Removing Pre-initialized Python Heap

Another useful option to reduce the size of the native executable is to omit a pre-initialized Python context from the executable.
By default, a default Python context is already pre-initialized and ready for immediate execution.
This can lead to slightly improved startup, at the cost of including a few thousand Python objects in the binary.
In embedded applications that use a custom polyglot engine to allow context sharing, the pre-initialized context cannot be used at all, and including those objects is wasted.
The pre-initialized heap can be omitted by passing the following to the `native-image` command:

```bash
-Dimage-build-time.PreinitializeContexts=
```

### Disabling Runtime Compilation of Python Code

If binary size is significantly more important than execution speed (which may be the case if all Python scripts are expected to be short running, perform a lot of I/O, or scripts are rarely executed more than once), it may make sense to disable JIT compilation entirely.
Be aware that this may significantly impact your Python performance, so be sure to test the runtime behavior of your actual use cases when choosing to use this option.
This can be achieved by passing the following options:

```bash
-Dtruffle.TruffleRuntime=com.oracle.truffle.api.impl.DefaultTruffleRuntime \
-Dpolyglot.engine.WarnInterpreterOnly=false
```

### Summary

Combining all of these approaches can halve the size of the GraalPy binary.
Every embedded application is different and the code pulled in by the rest of the Java code also matters, so combinations of these options should be tried to determine which effect they have in a specific instance.

## Shipping Python Packages

Our Maven archetype by default is set up to include all needed Python files in the native binary itself, so the image is self-contained.

In custom embeddings, the Python standard library is copied next to the native image.
When moving the native image, the standard library folder needs to be kept next to it.
