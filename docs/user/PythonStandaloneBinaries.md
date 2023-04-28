---
layout: docs-experimental
toc_group: python
link_title: Python Standalone Applications
permalink: /reference-manual/python/standalone-binaries/
---
# Standalone Applications with Python

With GraalPy, you can distribute Python applications or libraries as standalone binaries or JAR files without any external dependencies.
The [Truffle framework](https://github.com/oracle/graal/tree/master/truffle) that GraalPy is built on, and the [Sulong LLVM runtime](https://github.com/oracle/graal/tree/master/sulong) that GraalPy leverages for managed execution of Python's native extensions enables users to completely virtualize all filesystem accesses of Python programs, including those to the standard library and installed packages.

GraalPy comes with a module that can create standalone binaries or Java project skeletons.
The binaries bundle everything into one native executable.
The Java skeletons are set up with Maven to build and run self-contained JAR files.
They can also be used to generate a standalone binary from those JARs later, so Java skeletons offer more flexibility and control over the steps.

### Prerequisite

Set `JAVA_HOME` to use a GraalVM distribution.

## Creating GraalPy Binaries

Suppose there is a simple Python script, `my_script.py`, that does some useful work when run directly.
To distribute it as a standalone native binary, run the following command:

```
graalpy -m standalone binary --module my_script.py --output my_binary
```

It generates a standalone `my_binary` file which includes the Python code, the GraalPy runtime, and the Python standard library in a single, self-contained executable.
Use `graalpy -m standalone binary --help` for further options.

## Embedding GraalPy in a Java Application

You can distribute the Python script as a JAR file that runs on GraalVM and includes GraalPy.
To achieve this, run the `java` subcommand of GraalPy's `standalone` module:

```
graalpy -m standalone java --output-directory MyJavaApplication --module my_script.py
```

It creates a Java project _MyJavaApplication_. It includes a `pom.xml` that makes it easy to generate a JAR or a GraalVM native executable with Maven.
You can open this Maven project with any Java IDE and edit the main class that was created to modify the Python embedding.
To build the application, either `mvn -Pjar package` to create a JAR file, or `mvn -Pnative package` to create a GraalVM native executable.

Take a look at the generated `pom.xml`.
There are some options to tweak the performance and footprint trade-off.
Review the [Python Native Images documentation](PythonNativeImages.md) to find out how to remove other unwanted components and further reduce the binary size.

The generated project should be viewed as a starting point.
It includes the entire Python standard library, so the Python code can invoke all of the standard library code.
The resources can be manually pruned to reduce the included Python libraries to the necessary amount, reducing both the size of the package and the time to start up.
This Java example demonstrates some useful default options for the Python context, but other settings may be desirable to further control what the Python code is allowed to do.

## Security Considerations

Creating a native executable or a JAR that includes the Python code could be seen as a mild form of obfuscation, but it does not protect your source code.
While the Python sources are not stored verbatim into the image (only the GraalPy bytecode is), that bytecode is easy to convert back into Python sources.
If stronger protection for the included Python source code is required, consider, for example, encryption of the resources before building the native executable, and adding appropriate decryption into the generated virtual file system.
