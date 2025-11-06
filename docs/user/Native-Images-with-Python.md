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
CPython's philosophy of "batteries included" means it ships with many built-in modules and libraries.
As a compatible Python implementation, GraalPy includes most of these same "batteries."
However, this can result in large native executables when embedding GraalPy in Java applications.
You can significantly reduce the size by excluding components your application doesn't need by considering what your Python code actually uses.

### Available Optimizations

GraalPy provides several system properties that exclude specific language components.
When used together, these can reduce executable size by approximately 20%.

| Property                                  | Removes                                                      | Size Impact | Use Case                              |
| ----------------------------------------- | ------------------------------------------------------------ | ----------- | ------------------------------------- |
| `python.WithoutSSL=true`                  | SSL/TLS support (`ssl` module)                              | **High**    | No HTTPS or certificates needed       |
| `python.WithoutDigest=true`               | Crypto hash modules (`_md5`, `_sha1`, `_sha256`, etc.)      | **Medium**  | No cryptographic hashing required     |
| `python.WithoutCompressionLibraries=true` | Compression modules (`zlib`, `lzma`, `bzip2`, `zipimporter`) | **Medium**  | No compression/decompression needed   |
| `python.WithoutJavaInet=true`             | Network socket support (`socket` module)                    | **Medium**  | No network access required            |
| `python.WithoutNativePosix=true`          | Native POSIX API backend                                    | **Low**     | Embedded Java-only scenarios          |
| `python.WithoutPlatformAccess=true`       | System process access (`signal`, `subprocess`)              | **Low**     | Security-focused deployments          |
| `python.AutomaticAsyncActions=false`      | Automatic async thread management                           | **Low**     | Manual async action control           |

## Build Configuration Examples

### Maven Configuration

To apply size optimizations in Maven, configure these build arguments to your _pom.xml_ file within the native plugin configuration:

```xml
<plugin>
    <groupId>org.graalvm.buildtools</groupId>
    <artifactId>native-maven-plugin</artifactId>
    <version>0.9.28</version>
    <configuration>
        <buildArgs>
            <!-- Remove unused Python components for smaller size -->
            <buildArg>-Dpython.WithoutSSL=true</buildArg>
            <buildArg>-Dpython.WithoutDigest=true</buildArg>
            <buildArg>-Dpython.WithoutCompressionLibraries=true</buildArg>
            <buildArg>-Dpython.WithoutJavaInet=true</buildArg>
            <buildArg>-Dpython.WithoutNativePosix=true</buildArg>
            <buildArg>-Dpython.WithoutPlatformAccess=true</buildArg>
            <buildArg>-Dpython.AutomaticAsyncActions=false</buildArg>
            
            <!-- Remove pre-initialized Python context -->
            <buildArg>-Dimage-build-time.PreinitializeContexts=</buildArg>
            
            <!-- Increase memory for the build process -->
            <buildArg>-J-Xmx8g</buildArg>
        </buildArgs>
    </configuration>
</plugin>
```

> Note: Remove any `'-Dpython.WithoutX=true'` entries for components your application needs.

## Removing Pre-initialized Python Heap

By default, GraalPy includes a pre-initialized Python context in the executable for faster startup.
However, this adds several thousand Python objects to your binary.
You should remove this if:
- Creating more than one context
- Binary size is more important than a slight startup delay

To remove the pre-initialized heap, add this flag to your build configuration:

```bash
-Dimage-build-time.PreinitializeContexts=
```

### Disabling Runtime Compilation of Python Code

If binary size is significantly more important than execution speed, you can disable JIT compilation entirely.

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

In custom embeddings, the Python standard library is copied next to the native image.
When moving the native image, the standard library folder needs to be kept next to it.
