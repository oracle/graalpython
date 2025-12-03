# Native Executables with Python

GraalPy supports GraalVM Native Image to generate native binaries of Java applications that embed Python code.

## Quickstart

If you started with the [Maven archetype](README.md), the generated _pom.xml_ file already includes the necessary configuration for creating a native executable using the [Maven plugin for Native Image building](https://graalvm.github.io/native-build-tools/latest/maven-plugin.html).

To build the application, run:

```bash
mvn -Pnative package
```

This command packages the project and creates a native executable.

The generated _pom.xml_ and _Main.java_ files explain how Python resources are included in the resulting binary.
The generated project serves as a starting point and includes the entire Python standard library by default, allowing your Python code to use any standard library modules.
You can manually remove unused Python libraries to reduce both the executable size and startup time.
The created example demonstrates useful default options for the Python context, but you can adjust these settings to control what your Python code can access.

## Reducing Binary Size

Python is a feature-rich language with an extensive standard library.
This can result in large native executables when embedding GraalPy in Java applications.
You can significantly reduce the size by excluding components your application doesn't need by considering what your Python code actually uses.

### Removing Pre-initialized Python Heap

By default, GraalPy includes a pre-initialized Python context in the executable for faster startup.
Disabling this reduces the binary size by about 15MiB.
You should remove this if:
- You are creating more than one context
- Binary size is more important than a slight startup delay

To remove the pre-initialized heap, add this flag to your build configuration:

```bash
-Dpolyglot.image-build-time.PreinitializeContexts=
```

### Disabling Runtime Compilation of Python Code

If binary size is significantly more important than execution speed, you can disable JIT compilation entirely.
This will reduce binary size by around 40%.
You should use this if:

- Your Python scripts are very short-running
- Performance is not critical
- Your scripts spend most time on I/O operations

Be aware that this may significantly impact your Python performance, so be sure to test the runtime behavior of your actual use cases when choosing to use this option.

This can be achieved by passing the following options:

```bash
-Dtruffle.TruffleRuntime=com.oracle.truffle.api.impl.DefaultTruffleRuntime
-Dpolyglot.engine.WarnInterpreterOnly=false
```

### Summary

Combining these approaches can reduce the binary size by 50% or more.
Since every application is different, experiment with different combinations to find what works best for your specific use case.

## Shipping Python Packages

Our Maven archetype by default is set up to include all needed Python files in the native binary itself, so the image is self-contained.
