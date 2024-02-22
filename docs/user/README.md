---
layout: docs-experimental
toc_group: python
link_title: Python Reference
permalink: /reference-manual/python/
redirect_from: /docs/reference-manual/python/
---

# GraalVM Python Runtime

GraalPy provides a Python 3.10 compliant runtime.
A primary goal is to support PyTorch, SciPy, and their constituent libraries, as well as to work with other data science and machine learning libraries from the rich Python ecosystem.
GraalPy can usually execute pure Python code faster than CPython, and nearly match CPython performance when C extensions are involved.
While many workloads run fine, any Python program that uses external packages could hit something unsupported.
At this point, the Python implementation is made available for experimentation and curious end-users.
See the [FAQ](FAQ.md) for commonly asked questions about this implementation.

## GraalPy Distributions

As of GraalVM for JDK 21, the Python runtime (GraalPy) is available as a standalone distribution. 

A GraalPy standalone built on top of Oracle GraalVM is licensed under the [GraalVM Free Terms and Conditions (GFTC)](https://www.oracle.com/downloads/licenses/graal-free-license.html) license, which permits use by any user including commercial and production use. Redistribution is permitted as long as it is not for a fee.
This distribution comes with additional proprietary optimizations, is significantly faster and more memory-efficient.

A GraalPy standalone built on top of GraalVM Community Edition (GraalPy Community) is fully open-source.
To distinguish between them, GraalPy Community has the suffix `-community` in the name.

```bash
# GraalPy
graalpy-<version>-<os>-<arch>.tar.gz
# GraalPy Community Edition
graalpy-community-<version>-<os>-<arch>.tar.gz
```

Two language runtime options are available for both Oracle and Community GraalPy: Native and JVM.
In the Native configuration, GraalPy is ahead-of-time compiled to a standalone native executable. 
This means that you do not need a JVM installed on your system to use it and it is size-compact.
In the JVM configuration, you can use Java interoperability easily, and peak performance may be higher than the native configuration.
A JVM standalone that comes with a JVM has the `-jvm` suffix in the name: `graalpy-jvm-<version>-<os>-<arch>.tar.gz`.

| Configuration:     | Native (default) | JVM           |
| ------------------ | ---------------: | ------------: |
| Time to start | faster | slower |
| Time to reach peak performance | faster | slower |
| Peak performance (also considering GC) | good | best |
| Java host interoperability | needs configuration | works |

## Installing GraalPy

You can install GraalPy by downloading a compressed GraalPy tarball appropriate for your platform. 

Linux and macOS users can use [Pyenv](https://github.com/pyenv/pyenv) to install GraalPy. Also, for the **Linux x64** architecture it is possible to install via [Conda-Forge](https://conda-forge.org/) (Conda-Forge provides GraalPy Community only).

### Downloading

1. Navigate to [GitHub releases](https://github.com/oracle/graalpython/releases/) and select a desired standalone for your operating system. 
2. Uncompress the archive:

    > Note: If you are using macOS Catalina and later, first remove the quarantine attribute:
    ```shell
    sudo xattr -r -d com.apple.quarantine <archive>.tar.gz
    ```

    Now extract:
    ```shell
    tar -xzf <archive>.tar.gz
    ```
    Alternatively, open the file in the Finder.
3. Check the version to see if the runtime is active:
    ```shell
    ./path/to/bin/graalpy --version
    ```

### Using `pyenv` 

Linux and macOS users can install GraalPy using [Pyenv](https://github.com/pyenv/pyenv).
To install version 23.1.0, run the following command:

```bash
pyenv install graalpy-23.1.0
```

### Using Conda Forge (GraalPy Community only)

Another option is to use [Conda-Forge](https://conda-forge.org/) to install GraalPy on the **Linux x64** architecture (GraalPy Community only). 
To get an environment with the latest version, use the following command:

```bash
conda create -c conda-forge -n graalpy graalpy
```

## GraalPy for Windows

There is a GraalPy preview build for Windows that you can [download](https://github.com/oracle/graalpython/releases/).
It supports installation of pure Python packages via `pip`. Native extensions are a work in progress.

The Windows build has several known issues:

  - JLine treats Windows a dumb terminal, no autocomplete and limited editing capabilities in the REPL
  - Interactive help() in the REPL doesn't work
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

## Interoperability with Java

The best way to embed GraalPy is to use the [GraalVM SDK Polyglot API](https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/package-summary.html).

As of GraalVM for JDK 21, all necessary artifacts can be downloaded directly from Maven Central. 
All artifacts relevant to embedders can be found in the Maven dependency group [`org.graalvm.polyglot`](https://central.sonatype.com/namespace/org.graalvm.polyglot).

To embed GraalPy into a Java host application, add GraalPy as a Maven dependency or explicitly put the JAR on the module path. Below is the Maven configuration for a Python embedding:
```xml
<dependency>
    <groupId>org.graalvm.polyglot</groupId>
    <artifactId>polyglot</artifactId>
    <version>23.1.0</version>
</dependency>
<dependency>
    <groupId>org.graalvm.polyglot</groupId>
    <artifactId>python</artifactId>
    <version>23.1.0</version>
    <scope>runtime</scope>
    <type>pom</type>
</dependency>
```

The `<scope>runtime</scope>` parameter is only necessary if you need the runtime dependency.

Depending on which supported JDK you run embedded GraalPy, the level of optimizations varies, as described [here](https://www.graalvm.org/reference-manual/embed-languages/#runtime-optimization-support).

Learn more in a dedicated [GraalPy Interoperability guide](Interoperability.md). See also the [Embedding Languages documentation](https://www.graalvm.org/reference-manual/embed-languages/) on how a guest language like Python can possibly interract with Java.

## Related Documentation

* [Installing Supported Packages](Packages.md)
