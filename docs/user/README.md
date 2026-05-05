# GraalPy User Guide

GraalPy is a Python 3.12 compliant runtime that provides better performance, native compilation capabilities, and seamless Java interoperability.

Here are the two main types of users and how they can benefit from GraalPy:

**For JVM developers** who need Python libraries in their applications or have legacy Jython code, GraalPy can be embedded directly into JVM projects using Maven or Gradle, see the [Embed Python in Java](#embedding-python-in-java) section.

**For Python developers** who want better performance and native compilation, GraalPy serves as a drop-in replacement for standard Python, see the [GraalPy as CPython Alternative](#using-graalpy-as-a-standalone-python-runtime) section.

## Embedding Python in Java

**For JVM developers who need to use Python libraries from their JVM applications or migrate from legacy Jython code.**

You do not need to install GraalPy separately - you can use GraalPy directly in Java with Maven or Gradle.
This lets you call Python libraries like NumPy, pandas, or any PyPI package from your Java application.
GraalPy also provides a migration path from Jython 2.x to Python 3.x with better performance and maintained Java integration capabilities.

These guides cover everything you need to know:

- **[Getting Started](Embedding-Getting-Started.md)** - Maven and Gradle setup
- **[Build Tools](Embedding-Build-Tools.md)** - Detailed plugin documentation
- **[Permissions](Embedding-Permissions.md)** - Configure security settings
- **[Interoperability](Interoperability.md)** - Java and Python integration patterns
- **[Native Images](Native-Images-with-Python.md)** - Compile to native executables
- **[Migration Guide](Python-on-JVM.md)** - Complete Jython to GraalPy migration

## Using GraalPy as a Standalone Python Runtime

**You want to use GraalPy instead of the standard Python from python.org.**

Install GraalPy on your machine and use it like any Python interpreter.
You get better performance, the ability to compile to native binaries, and access to the GraalVM ecosystem.

These guides cover everything you need to know:

- **[Getting Started](Standalone-Getting-Started.md)** - Installation and basic usage
- **[Standalone Applications](Python-Standalone-Applications.md)** - Compile Python to native binaries
- **[Native Extensions](Native-Extensions.md)** - Working with C extensions and native packages
- **[Interoperability](Interoperability.md)** - Use Java and other Graal languages from Python
- **[Performance](Performance.md)** - Optimization tips and benchmarks
- **[Tooling](Tooling.md)** - IDE integration and development tools

## General Information

- **[Test Tiers](Test-Tiers.md)** - Platform compatibility and testing information
- **[Troubleshooting](Troubleshooting.md)** - Common embedding issues and solutions

## Version Compatibility

The following table shows which Python versions are supported by each GraalPy release:

| GraalPy Version | Python Version | GraalVM Platform                                                |
| --------------- | -------------- | --------------------------------------------------------------- |
| 25.x            | Python 3.12.8  | Oracle GraalVM 25.x, GraalVM Community Edition 25.x             |
| 23.x            | Python 3.10.8  | Oracle GraalVM for JDK 21.x, Oracle GraalVM for JDK 17.x        |
| 22.x            | Python 3.8.5   | GraalVM Enterprise Edition 21.3.x                               |

### Platform Support

GraalPy is mostly written in Java and Python, but the Python package ecosystem is rich in native packages that need platform specific support via native libraries that expose platform-specific APIs.
The main operating system is Oracle Linux, the CPU architectures are AMD64 and ARM, and the primary JDK is Oracle GraalVM.
**Linux is recommended for getting started with GraalPy.** Windows and macOS with GraalVM JDK are less well tested, and outside of those combinations only basic test coverage is provided.
As macOS and other platforms are not prioritized, some GraalPy features may not work on these platforms.
See [Test Tiers](Test-Tiers.md) for a detailed breakdown.
