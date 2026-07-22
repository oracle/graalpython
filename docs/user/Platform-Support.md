### Platform Support

GraalPy is mostly written in Java and Python, but the Python package ecosystem is rich in native packages that need platform specific support via native libraries that expose platform-specific APIs.
The main operating system is Oracle Linux, the CPU architectures are AMD64 and ARM, and the primary JDK is Oracle GraalVM.
**Linux is recommended for getting started with GraalPy.** Windows and macOS with GraalVM JDK are less well tested, and outside of those combinations only basic test coverage is provided.
As macOS and other platforms are not prioritized, some GraalPy features may not work on these platforms.
See [Test Tiers](../user/Test-Tiers.md) for a detailed breakdown.

## Windows

GraalPy standalone builds on Windows use a native OS backend by default.
The backend maps Python's OS interface to the Windows CRT, Win32, and Winsock APIs instead of relying on the Java-based backend.
It provides native or emulated Windows implementations for files and paths, sockets, pipes, `select`, `mmap`, subprocesses, multiprocessing semaphores, and Windows-specific modules such as `nt`, `msvcrt`, `_winapi`, and `_overlapped`.

Filesystem paths are passed to wide-character Windows APIs as UTF-16, while other native interfaces continue to use narrow strings where required.
Errors from the CRT, Win32, and Winsock APIs are translated to Python `OSError` instances using CPython-compatible error mappings, with the original Windows error retained as `OSError.winerror`.
