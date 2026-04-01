### Platform Support

GraalPy is mostly written in Java and Python, but the Python package ecosystem is rich in native packages that need platform specific support via native libraries that expose platform-specific APIs.
The main operating system is Oracle Linux, the CPU architectures are AMD64 and ARM, and the primary JDK is Oracle GraalVM.
**Linux is recommended for getting started with GraalPy.** Windows and macOS with GraalVM JDK are less well tested, and outside of those combinations only basic test coverage is provided.
As macOS and other platforms are not prioritized, some GraalPy features may not work on these platforms.
See [Test Tiers](../user/Test-Tiers.md) for a detailed breakdown.