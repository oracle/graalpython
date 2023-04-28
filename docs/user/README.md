---
layout: docs-experimental
toc_group: python
link_title: Python Reference
permalink: /reference-manual/python/
redirect_from: /docs/reference-manual/python/
---

# GraalVM Python Runtime

GraalPy provides a Python 3.10 compliant runtime.
A primary goal of the GraalPy runtime is to support SciPy and its constituent libraries, as well as to work with other data science and machine learning libraries from the rich Python ecosystem.
At this point, GraalPy is made available for experimentation and curious end-users.
See the [FAQ](FAQ.md) for commonly asked questions about this implementation.

## Installing GraalPy

### Linux and macOS

The easiest way to install GraalPy on Linux and macOS platforms is to use [Pyenv](https://github.com/pyenv/pyenv/), the Python version manager.
For example, to install version 22.3.0, for example, run the following commands:

```bash
pyenv install graalpy-22.3.0
pyenv shell graalpy-22.3.0
```

Alternatively, [download](https://github.com/oracle/graalpython/releases) a compressed GraalPy installation file appropriate for your platform.
For example, for Linux, download a file that matches the pattern _graalpy-XX.Y.Z-linux-amd64.tar.gz_.
Uncompress the file and update your PATH variable as necessary.
If you are using macOS Catalina or later, you may need to remove the quarantine attribute.
To do this, run the following command:

```bash
sudo xattr -r -d com.apple.quarantine /path/to/GRAALPY_HOME
```

### Windows

There is currently no installer for Windows.
Instead, follow [these instructions](https://github.com/oracle/graalpython#user-content-building-from-source) to build GraalPy from source.

## Running Python

The best way of using GraalPy is from a virtual environment. This generates wrapper scripts and makes the implementation usable from a shell as the standard Python interpreter. To create a virtual environment with GraalPy, run the following command in your project directory:

```bash
graalpy -m venv <venv-dir>
```

To activate the environment in your shell session run:

```bash
source <venv-dir>/bin/activate
```

Several executables are available in the virtual environment, including `python`, `python3` and `graalpy`. 

You can run simple Python commands or programs with the `graalpy` launcher:

```bash
graalpy [options] [-c cmd | filename]
```

For example, start the Python interactive shell from the command line, using the command `graalpy`, then enter the following line at the Python shell prompt (identified by `>>>`), followed by **CR**.

```python
>>> print("Hello World!")
```

You should see the output displayed directly, followed by the Python interactive shell prompt

```bash
Hello World!
>>>
```

Alternatively, you can invoke a Python script. 
Copy the following content into a file named _helloworld.py_:

```python
print("Hello World!")
```

Start `graalpy` and pass the filename as an argument:

```bash
graalpy helloworld.py
```

You should see the following output

```bash
Hello World!
```

## Python Options

GraalPy supports some of the same options as Python 3.10 as well as some additional options to control the underlying Python runtime, GraalVM's tools, and the execution engine.
These can be viewed using the following command:

```bash
graalpy --help --help:tools --help:languages
```

## Native Image and JVM Runtime

By default, GraalVM runs GraalPy from a binary, compiled ahead-of-time with [Native Image](https://github.com/oracle/graal/blob/master/docs/reference-manual/native-image/README.md), yielding faster startup time and lower footprint.
Although the ahead-of-time compiled binary includes the Python and LLVM interpreters, to interoperate with
other languages you must supply the `--jvm` option.
This instructs the launcher to run on the JVM instead of in Native Image mode.
Thus, you will notice a longer startup time.

## Related Documentation
* [Installing Supported Packages](Packages.md)