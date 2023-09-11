---
layout: docs-experimental
toc_group: python
link_title: Python Reference
permalink: /reference-manual/python/
redirect_from: /docs/reference-manual/python/
---

# GraalVM Python Runtime

GraalPy provides a Python 3.10 compliant runtime.
A primary goal is to support PyTorch, SciPy, and their constituent libraries, as well as to work with other data science and machine learning libraries from the rich Python ecosystem..
GraalPy can usually execute pure Python code faster than CPython, and nearly match CPython performance when C extensions are involved.
While many workloads run fine, any Python program that uses external packages could hit something unsupported.
At this point, the Python implementation is made available for experimentation and curious end-users.
See the [FAQ](FAQ.md) for commonly asked questions about this implementation.

## Installing GraalPy

### Linux and macOS

The easiest way to install GraalPy on Linux and macOS platforms is to use [pyenv](https://github.com/pyenv/pyenv/), the Python version manager.
For example, to install version 22.3.0, for example, run the following commands:

```bash
pyenv install graalpy-22.3.0
pyenv shell graalpy-22.3.0
```

Another option is to use [Conda-Forge](https://conda-forge.org/).
To get an environment with the latest version of GraalPy, use the following command:

```bash
conda create -c conda-forge -n graalpy graalpy
```

Alternatively, [download](https://github.com/oracle/graalpython/releases) a compressed GraalPy installation file appropriate for your platform.
For example, for Linux, download a file that matches the pattern _graalpy-XX.Y.Z-linux-amd64.tar.gz_.
Uncompress the file and update your PATH variable as necessary.
If you are using macOS Catalina or later, you may need to remove the quarantine attribute.
To do this, run the following command:

```bash
sudo xattr -r -d com.apple.quarantine /path/to/GRAALPY_HOME
```

To try GraalPy with a full GraalVM, including support for Java embedding and interoperability with other languages, you can use the bundled releases from [www.graalvm.org](https://www.graalvm.org/downloads/).

### Windows

There is a preview binary build of Windows that you can download via [www.graalvm.org](https://www.graalvm.org/downloads/).
The Windows build has several known issues:

  - JLine treats Windows a dumb terminal, no autocomplete and limited editing capabilities in the REPL
  - Interactive help() in the REPL doesn't work
  - Oracle GraalPy builds cannot create venvs or install packages, use community GraalPy builds to do those things.
  - Inside venvs:
      - graalpy.cmd and graalpy.exe are broken
      - pip.exe cannot be used directly
      - pip has trouble with cache file loading, use `--no-cache-dir`
      - Only pure Python binary wheels can be installed, no native extensions or source builds
      - To install a package, use `myvenv/Scripts/python.cmd -m pip --no-cache-dir install <pkg>`
  - Running from PowerShell works better than running from CMD, various scripts will fail on the latter

## Running Python

The best way of using GraalPy is from a `venv` virtual environment.
This generates wrapper scripts and makes the implementation usable from a shell as the standard Python interpreter.
To create a `venv` virtual environment with GraalPy, run the following command in your project directory:

```bash
graalpy -m venv <venv-dir>
```

To activate the environment in your shell session run:

```bash
source <venv-dir>/bin/activate
```

Several executables are available in the virtual environment, including `python`, `python3`, and `graalpy`. 

You can run simple Python commands or programs with the `graalpy` launcher:

```bash
graalpy [options] [-c cmd | filename]
```

For example, start the Python interactive shell from the command line using the command `graalpy`, then enter the following line at the Python shell prompt (identified by `>>>`), followed by **CR**.

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
