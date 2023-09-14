---
layout: docs-experimental
toc_group: python
link_title: Python Standalone Applications
permalink: /reference-manual/python/standalone-binaries/
---
# Standalone Applications with Python

With GraalPy, you can distribute Python applications or libraries as standalone binaries or JAR files without any external dependencies.
The [Truffle framework](https://github.com/oracle/graal/tree/master/truffle) on which GraalPy is built, and the [Sulong LLVM runtime](https://github.com/oracle/graal/tree/master/sulong) that GraalPy leverages for managed execution of Python's native extensions enables users to completely virtualize all filesystem accesses of Python programs, including those to the standard library and installed packages.

GraalPy comes with a module that can create Python binaries for Linux, Windows, and macOS. 
The binaries bundle everything into a single-file native executable.

The tool can also generate a skeleton Maven project that sets up a polyglot embedding of Python packages into Java.
The polyglot skeletons are set up with Maven to to generate a standalone binary for a simple Java-Python HelloWorld example and can be used as a starting point or inspiration for further Java-Python polyglot development.

## Creating GraalPy Native Binaries

Suppose there is a simple Python script, _my_script.py_, that does some useful work when run directly.
To distribute it as a standalone native binary, run the following command:

```bash
graalpy -m standalone native \
      --module my_script.py \
      --output my_binary
```

It generates a standalone _my_binary_ file which includes the Python code, the GraalPy runtime, and the Python standard library in a single, self-contained executable.
Use `graalpy -m standalone native --help` for further options.
 
## Embedding GraalPy in a Java Application

You can also generate a Java-Python polyglot project skeleton.
To achieve this, run the `polyglot_app` subcommand of GraalPy's `standalone` module:

```bash
graalpy -m standalone polyglot_app --output-directory MyJavaApplication
```

It creates a Java project _MyJavaApplication_. It includes a _pom.xml_ file that makes it easy to generate a GraalVM native executable with Maven.
You can open this Maven project with any Java IDE and edit the main class that was created to modify the Python embedding.
To build the application, either use `mvn -Pnative package` to create a GraalVM native executable.

Take a look at the generated _pom.xml_ file.
There are some options to tweak the performance and footprint trade-off.
Review the [Python Native Images documentation](PythonNativeImages.md) to find out how to remove other unwanted components and further reduce the binary size.

The generated project should be viewed as a starting point.
It includes the entire Python standard library, so the Python code can invoke all of the standard library code.
The resources can be manually pruned to reduce the included Python libraries to the necessary amount, reducing both the size of the package and the time to start up.
This Java example demonstrates some useful default options for the Python context, but other settings may be desirable to further control what the Python code is allowed to do.

## Security Considerations

Creating a native executable or a JAR file that includes the Python code could be seen as a mild form of obfuscation, but it does not protect your source code.
While the Python sources are not stored verbatim into the executable (only the GraalPy bytecode is stored), that bytecode is easy to convert back into Python sources.
If stronger protection for the included Python source code is required, consider, for example, encryption of the resources before building the native executable, and adding appropriate decryption into the generated virtual file system.
