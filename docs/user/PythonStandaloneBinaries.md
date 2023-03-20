---
layout: docs-experimental
toc_group: python
link_title: Python Standalone Applications
permalink: /reference-manual/python/standalone-binaries/
---
# Standalone Applications with Python

It can be desirable to distribute Python applications or libraries as standalone binaries or JAR files without any external dependencies.
The Truffle framework that GraalPy is built on and the Sulong LLVM runtime that GraalPy can leverage for managed execution of Python's native extensions allow us to completely virtualize all filesystem accesses of Python programs, including those to the standard library and installed packages.

GraalPy comes with a module, `py2bin`, that creates a Maven project skeleton to bundle and run self-contained JAR files with Python code.
The generated skeleton can also be used to generate a standalone binary that includes all the Python code using GraalVM Native Image.

## Usage

Suppose we have a simple Python script `my_script.py` that does some useful work when run directly.
If we wanted to distribute it as a standalone native image, we run the following command:

```
$ graalpy -m py2bin my_script_application/ my_script.py
```

The target folder `my_script_application` will now include a `pom.xml` that makes it easy to generate a native image with maven.
Make sure to set your `JAVA_HOME` to use a GraalVM distribution that includes the native-image binary:
```
$ mvn -Pnative package
```

Afterwards, the standalone binary can be found in the `target` directory.
It can be moved freely and launched directly, and does not require any additional resources.

The generated should be viewed as a starting point.
It includes the entire Python standard library, which can be manually pruned to reduce to the necessary amount.
The Java code demonstrates some useful default options for the Python context, but other settings may be desirable.
Review also our general documentation on Python native images to find out how to remove other unwanted components and reduce the binary size.

## Security considerations

Creating a native image that includes the Python code can be seen as a mild form of obfuscation.
The source code is not stored verbatim into the image, but the GraalPy bytecode is, which is easy to convert back into source code.
If stronger protection for the included Python sources is required, this needs to be handled by a means of e.g. encryption of the resources before building the native image, and adding approproate decryption into the generated virtual file system.
