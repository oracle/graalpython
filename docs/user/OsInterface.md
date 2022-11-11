---
layout: docs-experimental
toc_group: python
link_title: Operating System Interfaces
permalink: /reference-manual/python/OSInterface/
---

# Operating System Interfaces

Truffle-based GraalVM languages usually implement the system related functions using the [Truffle abstraction layer](https://github.com/oracle/graal/blob/master/truffle/docs/README.md), which is OS independent and provides extension points for the users when embedding GraalVM Python or other Truffle based languages into Java applications.
See, for example, [Truffle FileSystem service-provider](https://www.graalvm.org/truffle/javadoc/org/graalvm/polyglot/io/FileSystem.html).

The Python standard library also provides OS abstraction, but exposes lower level interfaces, for instance, the OS module directly exposes some POSIX functions.
On non-POSIX platforms, this interface is emulated to a degree.

GraalVM Python runtime provides two alternative implementations of the system related functionality offered by the builtin Python modules such as `os`.
Which implementation is used can be controlled by the option `PosixModuleBackend`: valid values are `native` and `java`.

## Native Backend

The `native` backend calls directly the POSIX API in mostly the same way as CPython, the reference Python implementation, does.

This approach is the most compatible with CPython and provides bare access to the underlying OS interface without any emulation layer in between.

By default, this implementation bypasses the Truffle abstraction layer, therefore it is not sandboxed and does not support custom implementations of [Truffle FileSystem service-provider](https://www.graalvm.org/truffle/javadoc/org/graalvm/polyglot/io/FileSystem.html), and other Polyglot API providers related to system interfaces.

The native backend is chosen by default when GraalVM Python is started via the `graalpy` or any other Python related launcher inside GraalVM.
The exception are Python related launchers with `-managed` suffix available only in GraalVM Enterprise (for example, `graalpy-managed`), which by default use the `java` POSIX backend.

### Limitations of the Native Backend

Known limitations are:

* `os.fork` is not supported
* `_posixsubprocess.fork_exec` does not support the `preexec_fn` parameter

## Java Backend

The `java` backend uses the [Truffle abstraction layer](https://github.com/oracle/graal/blob/master/truffle/docs/README.md) and therefore supports custom Polyglot API providers related to system interfaces and sandboxing.
Since this abstraction is POSIX agnostic, it does not expose all the necessary functionality. Some functionality is emulated, and some functionality is not supported at all.

The Java backend is the default when GraalVM Python is run via the `Context` API, i.e., [embedded in Java applications](https://github.com/oracle/graal/blob/master/docs/reference-manual/embedding/embed-languages.md), or when it is launched using Python related launchers with `-managed` suffix available only in GraalVM Enterprise.

### Limitations of the Emulated Backend

GraalVM Python can log info about known incompatibility of functions executed at runtime, which includes the OS interface related functions.
To turn on this logging, use `--log.python.compatibility.level=FINE` or other desired logging level.

Known limitations of the emulated layer are:

* Its state is disconnected from the actual OS state, which applies especially to:
  * *file descriptors*: Python level file descriptors are not usable in native code
  * *current working directory*: gets initialized to what was the actual current working
    directory at the startup, but then it is maintained separately, i.e., `chdir` in Python
    does not change the actual current working directory of the process.
  * *umask*: similarly to current working directory, but it is always initialized
    to `0022` regardless of the actual system value at startup.
* Resolution of file access/modification times depends on the JDK.
  The best the emulated backend can guarantee is seconds resolution.
* `os.access` and any other functionality based on `faccessat` POSIX function does not support:
  * effective IDs
  * `follow_symlinks=False` unless the mode is only `F_OK`

## Relation to Python Native Extensions

Apart from operating system interfaces exposed as builtin Python level modules, Python native extensions executed via the GraalVM LLVM runtime may also access OS interfaces at the C level.
How such accesses are handled depends on the GraalVM LLVM runtime configuration.

At this point, the only combination where OS handles, such as file descriptors, can be shared between Python and the C code of Python extensions is with `native` PosixModuleBackend and `native` mode of GraalVM LLVM runtime.
This combination is the default for the `graalpy` launcher.
