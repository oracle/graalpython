---
layout: docs-experimental
toc_group: python
link_title: Python Native Executables
permalink: /reference-manual/python/native-image/
---
# Native Executables with Python

Python is a large language.
"Batteries included" has long been a core tenet of CPython.
As a compatible replacement, GraalPy includes most of those "batteries" as well.
GraalPy binaries are much bigger than all CPython binaries combined, however.
The data-structures differ, there is extra metadata, and in general, more code is required to support the just-in-time (JIT) compiler for Python code.
GraalPy also includes many standard Python modules in the main binary that are external C modules for CPython.
All this means that the binary sizes for a GraalPy distribution are about 10 times larger than the CPython executable.

In embedding scenarios, the defaults for GraalPy builds may include much more than needed for any specific application.
Java embeddings usually have more limited use cases for the Python interpreter than the full GraalPy distribution, and often can know ahead of time whether certain features are needed or may even be undesired.
Thus, when embedding GraalPy in a Java application, the binary size can be reduced to some extent.

First and foremost, GraalPy accepts system properties on the `native-image` command line that exclude parts of the core runtime from the build.
The options currently provided can, when taken together, reduce the size of the executable by around 20%.
These are:

* `python.WithoutSSL=true` - This option removes the `ssl` module from the build.
  If no secure network access or certificate checking is required, this removes Java's SSL classes and the BouncyCastle library.
* `python.WithoutDigest=true` - This option removes the `_md5`, `_sha1`, `_sha256`, `_sha512`, `_sha3`, and `_hashlib` modules from the build.
  This removes the direct usages of `java.security.MessageDigest` and `javax.crypto.Mac` from GraalPy.
* `python.WithoutPlatformAccess=true` - This removes the `signal` and `subprocess` modules, removes accesses to process properties such as the Unix UID and GID, or setting the Java default time zone.
  This has no significant impact on binary size, but if these are unwanted capabilities that are dynamically disabled with context options anyway, they can also be removed ahead of time.
* `python.WithoutCompressionLibraries=true` - This removes the `zlib`, `lzma`, `bzip2`, and `zipimporter` modules and related classes.
  These modules have both native and pure Java implementations (the former for performance, the latter for better sandboxing); however, if they are not needed, they can be removed entirely.
* `python.WithoutNativePosix=true` - The default `os` module backend is a pure Java implementation when GraalPy is embedded rather than run via its launcher.
  The native POSIX backend of GraalPy is recommended only for 100% compatibility with CPython's POSIX interfaces, and if not used, can be removed from the build with this option.
* `python.WithoutNativeSha3=true` - The default `_sha3` module backend is a pure Java implementation when GraalPy is embedded rather than run via its launcher.
  The native sha3 backend of GraalPy is recommended only for 100% compatibility with CPython's _sha3 interfaces, and if not used, can be removed from the build with this option.
* `python.WithoutJavaInet=true` - The Java implementation of Python's `socket` module is based on Java's networking classes.
  If network access is denied for an embedding scenario anyway, this option can reduce the binary size further.
* `python.AutomaticAsyncActions=false` - Signal handling, Python weak reference callbacks, and cleaning up native resources is usually achieved automatically by spawning GraalPy daemon threads that submit safepoint actions to the Python main thread.
  This uses an `ExecutorService` with a thread pool.
  If embedders want to disallow such extra threads or avoid pulling in `ExecutorService` and related classes, then set this property to `false` and retrieve the `PollPythonAsyncActions` object from the context's polyglot bindings.
  This object is executable and can be used to trigger Python asynchronous actions at locations the embedder desires.
* `python.WithoutJNI=true` - This option removes any code from the build that uses JNI. As a consequence, you cannot use the HPy JNI backend and maybe other parts that rely on JNI.

Another useful option to reduce the size of the native executable is to omit a pre-initialized Python context from the executable.
By default, a default Python context is already pre-initialized and ready for immediate execution.
In embeddings that use a custom polyglot engine to allow context sharing, the pre-initialized context cannot be used, however.
It can be omitted by explicitly passing:

```bash
-Dimage-build-time.PreinitializeContexts=
```

If binary size is significantly more important than execution speed (which may be the case if all Python scripts are expected to be very short running and scripts are rarely executed more than once), it may make sense to disable JIT compilation entirely.
Be aware that this will significantly impact your Python performance, so be sure to test the runtime behavior of your actual use cases when choosing to use this option.
This can be achieved by passing the following options:

```bash
-Dtruffle.TruffleRuntime=com.oracle.truffle.api.impl.DefaultTruffleRuntime \
-Dpolyglot.engine.WarnInterpreterOnly=false
```

Using all of these combined can halve the size of the GraalPy binary.
Every embedding is different and the code pulled in by the rest of the Java code also matters, so combinations of these options should be tried to determine which effect they have in a specific instance.
