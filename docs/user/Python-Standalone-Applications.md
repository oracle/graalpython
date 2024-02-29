---
layout: docs-experimental
toc_group: python
link_title: Python Standalone Applications
permalink: /reference-manual/python/standalone-binaries/
---

# Python Standalone Applications

GraalPy enables you to create a Python application or library as a native application or JAR file with no external dependencies.
The [Truffle framework](https://github.com/oracle/graal/tree/master/truffle) on which GraalPy is built, combined with the [Sulong LLVM runtime](https://github.com/oracle/graal/tree/master/sulong) that GraalPy leverages for managed execution of Python's native extensions, completely virtualizes all filesystem accesses, including those to the standard library and installed packages.

GraalPy includes a module named `standalone` to create a Python binary for Linux, macOS, and Windows. 
The modules bundles all your application's resources into a single file.

> Note: **Prerequisite** GraalPy distribution beginning with version 23.1.0. See [GraalPy releases](https://github.com/oracle/graalpython/releases).

For example, if you want to produce a native executable from a Python file named _my\_script.py_ along with packages you have installed in a virtual environment named _my\_venv_, run the following command:

```bash
graalpy -m standalone native \
      --module my_script.py \
      --output my_binary \
      --venv my_env
```

It produces a native _my_binary_ file which includes your Python code, the GraalPy runtime, and the Python standard library in a single, self-contained executable.
Use `graalpy -m standalone native --help` for further options.

## Security Considerations

Creating a native executable or a JAR file that includes Python code could be seen as a mild form of obfuscation, but it does not protect your source code.
Python source code is not stored verbatim into the executable (only the GraalPy bytecode is stored), but bytecode is easy to convert back into Python source code.
