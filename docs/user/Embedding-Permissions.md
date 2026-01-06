# Permissions for Python Embeddings

When embedding GraalPy in Java applications, you need to understand how Python scripts access system resources and what security limitations apply.

GraalPy integrates with the [GraalVM Polyglot Sandbox](https://www.graalvm.org/latest/reference-manual/embed-languages/#controlling-access-to-host-functions) to provide configurable access control for embedded Python code.

## Python's POSIX Interface

GraalPy exposes the operating system interface to Python scripts in a GraalPy-specific way.
By default, all access is routed through Java interfaces, but some packages rely on POSIX API details and require direct native access.

Graal languages (implemented on the [Truffle framework](https://www.graalvm.org/latest/graalvm-as-a-platform/language-implementation-framework/)) typically implement system-related functions using the [Truffle abstraction layer](https://github.com/oracle/graal/blob/master/truffle/docs/README.md). This layer is OS-independent and provides extension points when embedding GraalPy or other Graal languages into Java applications. For example, see the [Truffle FileSystem service-provider](https://www.graalvm.org/truffle/javadoc/org/graalvm/polyglot/io/FileSystem.html).

The standard Python library also provides an OS abstraction, but exposes lower level interfaces. For example, the `os` module directly exposes some POSIX functions.
On non-POSIX platforms, this interface is emulated to a degree.

GraalPy provides two alternative implementations ("backends") of system-related functionality for built-in Python modules such as `os`. The `PosixModuleBackend` option determines which backend is used: `native` or `java`.

### Native Backend

The native backend directly calls the POSIX API in mostly the same way as CPython (the reference Python implementation).

This approach is the most compatible with CPython and provides bare access to the underlying OS interface without an intermediate emulation layer.

However, this implementation bypasses the Truffle abstraction layer by default. This means:
- **No sandboxing** protection
- **No support** for custom [Truffle FileSystem service-provider](https://www.graalvm.org/truffle/javadoc/org/graalvm/polyglot/io/FileSystem.html) implementations
- **No support** for other Polyglot API providers related to system interfaces

The native backend is chosen by default when GraalPy is started via the `graalpy` launcher or any other Python-related launcher.

#### Limitations of the Native Backend

Known limitations:

- `os.fork` is not supported
- `_posixsubprocess.fork_exec` does not support the `preexec_fn` parameter

### Java Backend

The Java backend uses the [Truffle abstraction layer](https://github.com/oracle/graal/blob/master/truffle/docs/README.md), which provides:

- **Sandboxing support** for security
- **Custom Polyglot API providers** for system interfaces
- **Cross-platform compatibility**

Because this abstraction is POSIX-agnostic, it cannot expose all necessary functionality. Some functionality is emulated, and some is unsupported.

The Java backend is the default when [embedding GraalPy in Java applications](https://github.com/oracle/graal/blob/master/docs/reference-manual/embedding/embed-languages.md) using the `Context` API.

#### Limitations of the Java Backend

To help identify compatibility issues, GraalPy can log information about known incompatibilities of functions executed at runtime. To enable this logging, use: `--log.python.compatibility.level=FINE`

Known limitations of the Java backend are:

**State Isolation:**

The Java backend's state is disconnected from the actual OS state:

- **File descriptors:** Python-level file descriptors are not usable in native code
- **Current working directory:** Initialized to the startup directory but maintained separately. For example, `os.chdir()` in Python does not change the actual process working directory
- **umask:** Same limitation as working directory, but always initialized to `0022` regardless of the actual system value

**Other Limitations:**

- **File timestamps:** Resolution depends on the JDK. The Java backend can only guarantee seconds resolution
- **File access checks:** `os.access()` and functionality based on the `faccessat` POSIX function do not support:
  - Effective IDs
  - `follow_symlinks=False` unless the mode is only `F_OK`

## Python Native Extensions

Python native extensions run by default as native binaries with full access to the underlying system. This means they bypass the security controls described above.

For more information about limitations when embedding native extensions, see [Embedding limitations](Native-Extensions.md#embedding-limitations).