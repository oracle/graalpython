---
layout: docs
toc_group: python
link_title: Embedding Permissions
permalink: /reference-manual/python/Embedding-Permissions/
redirect_from: /reference-manual/python/OSInterface/
---

# Permissions for Python Embeddings

## Access Control and Security Limits for Python Scripts

Embedding GraalPy into Java works with the [GraalVM Polyglot Sandbox](https://www.graalvm.org/latest/reference-manual/embed-languages/#controlling-access-to-host-functions).

## Python's POSIX Interface

The way the operating system interface is exposed to Python scripts is GraalPy-specific.
By default all access is routed through Java interfaces, but some packages rely on details of POSIX APIs and require direct native access.

Graal languages (those implemented on the [Truffle framework](https://www.graalvm.org/latest/graalvm-as-a-platform/language-implementation-framework/)) usually implement system-related functions using the [Truffle abstraction layer](https://github.com/oracle/graal/blob/master/truffle/docs/README.md), which is OS independent and provides extension points for the users when embedding GraalPy or other Graal languages into Java applications.
See, for example, the [Truffle FileSystem service-provider](https://www.graalvm.org/truffle/javadoc/org/graalvm/polyglot/io/FileSystem.html).

The standard Python library also provides an OS abstraction, but exposes lower level interfaces. For example, the `os` module directly exposes some POSIX functions.
On non-POSIX platforms, this interface is emulated to a degree.

GraalPy provides two alternative implementations (each referred to as a "backend") of system-related functionality offered by built-in Python modules such as `os`.
The `PosixModuleBackend` option determines which backend is used: valid values are `native` and `java`.

### Native Backend

This backend directly calls the POSIX API in mostly the same way as CPython (the reference Python implementation).

This approach is the most compatible with CPython and provides bare access to the underlying OS interface without an intermediate  emulation layer.

By default, this implementation bypasses the Truffle abstraction layer, and therefore it is not sandboxed and does not support custom implementations of [Truffle FileSystem service-provider](https://www.graalvm.org/truffle/javadoc/org/graalvm/polyglot/io/FileSystem.html), and other Polyglot API providers related to system interfaces.

The native backend is chosen by default when GraalPy is started via the `graalpy` or any other Python related launcher.
The exceptions are Python related launchers with `-managed` suffix available only in Oracle GraalVM (for example, `graalpy-managed`), which by default use the `java` POSIX backend.

#### Limitations of the Native Backend

Known limitations are:

* `os.fork` is not supported
* `_posixsubprocess.fork_exec` does not support the `preexec_fn` parameter

### Java Backend

This backend uses the [Truffle abstraction layer](https://github.com/oracle/graal/blob/master/truffle/docs/README.md) and therefore supports custom Polyglot API providers related to system interfaces and sandboxing.
Because this abstraction is POSIX agnostic, it does not expose all the necessary functionality. Some functionality is emulated, and some functionality is unsupported.

The Java backend is the default when GraalPy is run via the `Context` API, that is, [embedded in Java applications](https://github.com/oracle/graal/blob/master/docs/reference-manual/embedding/embed-languages.md), or when it is launched using Python-related launchers with the `-managed` suffix (available only in Oracle GraalVM).

#### Limitations of the Java Backend

GraalPy can log information about known incompatibility of functions executed at runtime, which includes the OS interface-related functions.
To turn on this logging, use the command-line option `--log.python.compatibility.level=FINE` (or other desired logging level).

Known limitations of the Java backend are:

* Its state is disconnected from the actual OS state, which applies especially to:
  * *file descriptors*: Python-level file descriptors are not usable in native code.
  * *current working directory*: is initialized to the current working
    directory at the startup, but is then maintained separately. So, for example, `chdir` in Python
    does not change the actual current working directory of the process.
  * *umask*: has the same limitation as that of the current working directory, but it is always initialized
    to `0022` regardless of the actual system value at startup.
* Resolution of file access/modification times depends on the JDK.
  The best the Java backend can guarantee is seconds resolution.
* `os.access` and any other functionality based on `faccessat` POSIX function does not support:
  * effective IDs.
  * `follow_symlinks=False` unless the mode is only `F_OK`.

## Python Native Extensions

Python native extensions run by default as native binaries, with full access to the underlying system.
See [Embedding limitations](Native-Extensions.md#embedding-limitations)

The context permissions needed to run native extensions are:
```java
.allowIO(IOAccess.ALL)
.allowCreateThread(true)
.allowNativeAccess(true)
```
