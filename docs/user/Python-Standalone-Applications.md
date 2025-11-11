# Python Standalone Applications

GraalPy enables you to package your Python applications or libraries into native executables or JAR files with no external dependencies. This means users can run your application without installing Python or any packages.

GraalPy uses the [Truffle framework](https://github.com/oracle/graal/tree/master/truffle) to bundle your Python code, dependencies, and the Python runtime into standalone executables. Truffle's filesystem virtualization allows everything to work from a single file, though packages with native C extensions may have limitations.

GraalPy includes a module named `standalone` to create a Python binary for Linux, macOS, and Windows. The module bundles all your application's resources into a single file.

> **Prerequisite:** GraalPy 23.1.0 or later. [Download here](https://github.com/oracle/graalpython/releases) or verify your version with `graalpy --version`.

## Quickstart of Python Standalone Applications

To create an native executable from a Python file with its dependencies, use this command:

```bash
graalpy -m standalone native \
      --module my_script.py \
      --output my_binary \
      --venv my_env
```

Where:

* `--module my_script.py` states the main Python file that contains your application's entry point
* `--output my_binary` states the name for your standalone executable (no file extension needed)
* `--venv my_env` states the path to virtual environment with installed packages (you can omit this if there are no dependencies)

This produces a native _my_binary_ file which includes your Python code, the GraalPy runtime, and the Python standard library in a single, self-contained executable.
Use `graalpy -m standalone native --help` for further options.

### Security Considerations of Python Standalone Applications

Standalone executables do not protect your source code. Your Python code becomes bytecode, and bytecode can be easily decompiled back to readable Python code.
