# Operating System Interfaces

Truffle based GraalVM languages usually implement the system related
functions using the Truffle API abstraction, which is OS independent
and provides extension points for the users when embedding GraalPython
or other Truffle based languages into Java applications. See, for example,
[Truffle FileSystem service-provider](https://www.graalvm.org/truffle/javadoc/org/graalvm/polyglot/io/FileSystem.html).

Python also provide OS abstraction, but exposes lower level interfaces, for instance,
the OS module directly exposes some POSIX functions. On non-POSIX platforms,
this interface is emulated to a degree.

GraalPython provides two alternative implementations of the system related
functionality offered by builtin modules such as `os` or `pwd`.
Which implementation is used, can be controlled by option `PosixModuleBackend`:
valid values are `native` and `emulated`.

## GraalPython's native backend

The `native` backend calls directly the POSIX API in mostly the same way
as CPython (the reference Python implementation) does.

This approach is the most compatible with CPython and provides bare access
to the underlying OS interface without any emulation layer in between.

By default, this implementation bypasses the Truffle API, therefore it is
not sandboxed and does not support custom implementations
of [Truffle FileSystem service-provider](https://www.graalvm.org/truffle/javadoc/org/graalvm/polyglot/io/FileSystem.html)
and other Truffle providers related to system interfaces.

GraalPython's native backend is chosen by default when GraalPython
is started via the `graalpython` or any other GraalPython related
launcher inside GraalVM.

### Limitations of the native backend

Known limitations:

* `os.fork` is not supported

## GraalPython's emulated backend

The `emulated` backend uses the Truffle API abstraction and therefore supports
custom Truffle providers related to system interfaces and sandboxing.
Since the Truffle API abstraction is POSIX agnostic, it does not expose
all the functionality necessary. Some functionality is emulated, and some
functionality is not supported at all.

GraalPython's emulated backend is the default when GraalPython is run via
the `Context` API, i.e., [embedded in Java applications](https://www.graalvm.org/reference-manual/embed-languages).

### Limitations of the emulated backend

GraalPython can log info about known incompatibility of functions executed at runtime,
which includes the OS interface related functions. To turn on this logging use
`--log.python.compatibility=FINE` or other desired logging level.

Known limitations of the emulated layer are:

* Its state is disconnected from the actual OS state, which applies especially to:
  * *file descriptors*: Python level file descriptors are not usable in native code
  * *current working directory*: gets initialized to what was the actual current working
    directory at the startup, but then it is maintained separately, i.e., `setwd` in Python
    does not change the actual current working directory of the process.
  * *umask*: similarly to current working directory, but it is always initialized
    to `0022` regardless of the actual system value at startup.
* Resolution of file access/modification times depends on the JDK.
  The best the emulated backend can guarantee is seconds resolution.
* `os.access` and any other functionality based on `faccessat` POSIX function does not support:
  * effective IDs
  * `follow_symlinks=False` unless the mode is only `F_OK`