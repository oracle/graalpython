---
layout: docs-experimental
toc_group: python
link_title: Python Runtime
permalink: /reference-manual/python/Python-Runtime/
redirect_from: /reference-manual/python/Packages/
---

# GraalPy as a CPython Replacement

## Choosing the GraalPy Runtime

GraalPy provides a Python 3.10 compliant runtime. 
A primary goal is to support PyTorch, SciPy, and their constituent libraries, as well as to work with other data science and machine learning libraries from the rich Python ecosystem.
GraalPy is distributed as an ahead-of-time compiled native executable, compact in size.

GraalPy provides the following capabilities:

* Replacement for CPython. Use GraalPy as a replacement for CPython. It is the most compatible, since it most closely resembles the structure of CPython installation packages.
* Unique deployment mode for Python applications. Compile a Python application on GraalPy to [a single native binary](Python-Standalone-Applications.md) that embeds all needed resources.
* Access to GraalVM's language ecosystems and tools. GraalPy can run many standard Python tools as well as tools from the GraalVM ecosystem.

### GraalPy Distributions

GraalPy is available as **GraalPy built on Oracle GraalVM** and **GraalPy Community**.

* GraalPy built on top of Oracle GraalVM provides the best experience: it comes with additional optimizations, is significantly faster and more memory-efficient. It is licensed under the [GraalVM Free Terms and Conditions (GFTC)](https://www.oracle.com/downloads/licenses/graal-free-license.html) license, same as Oracle GraalVM, which permits use by any user including commercial and production use.
Redistribution is permitted as long as it is not for a fee.

* GraalPy Community is built on top of GraalVM Community Edition, and is fully open-source.

Two language runtime options are available for the Oracle and Community distributions of GraalPy:

* Native
  * GraalPy is compiled ahead-of-time to a native executable.
  * This means that you do not need a JVM to run GraalPy and it is compact in size.
* JVM
  * You can easily exploit Java interoperability.
  * Peak performance may be higher than the native option.

### GraalPy Identification

The four GraalPy runtimes are identified as follows, using the general pattern _graalpy(-community)(-jvm)-&lt;version&gt;-&lt;os&gt;-&lt;arch&gt;_:

<table>
  <tr>
    <th>&nbsp;</th>
    <th>Oracle</th>
    <th>Community</th>
  </tr>
  <tr>
    <th>Native</th>
    <td>graalpy-&lt;version&gt;-&lt;os&gt;-&lt;arch&gt;</td>
    <td>graalpy-community-&lt;version&gt;-&lt;os&gt;-&lt;arch&gt;</td>
  </tr>
  <tr>
    <th>JVM</th>
    <td>graalpy-jvm-&lt;version&gt;-&lt;os&gt;-&lt;arch&gt;</td>
    <td>graalpy-community-jvm-&lt;version&gt;-&lt;os&gt;-&lt;arch&gt;</td>
  </tr>
</table>


### Comparison

| Runtime | Native (default) | JVM |
|:-------|:-----------------|:----|
|Time to start | faster | slower |
| Time to reach peak performance | faster | slower |
| Peak performance (also considering GC) |good | best |
| Java interoperability | needs configuration | works |

## Installing GraalPy

### Linux

The easiest way to install GraalPy on Linux is to use [Pyenv](https://github.com/pyenv/pyenv) (the Python version manager).
To install version 24.0.0 using Pyenv, run the following commands:
```bash
pyenv install graalpy-24.0.0
```
```bash
pyenv shell graalpy-24.0.0
```
> Before running `pyenv install`, you may need to update `pyenv` to include the latest GraalPy versions.

Alternatively, you can download a compressed GraalPy installation file from [GitHub releases](https://github.com/oracle/graalpython/releases).

1. Find the download that matches the pattern _graalpy-XX.Y.Z-linux-amd64.tar.gz_ or _graalpy-XX.Y.Z-linux-aarch64.tar.gz_ (depending on your platform) and download.
2. Uncompress the file and update your `PATH` environment variable to include to the _graalpy-XX.Y.Z-linux-amd64/bin_ (or _graalpy-XX.Y.Z-linux-aarch64/bin_) directory.

### macOS

The easiest way to install GraalPy on macOS is to use [Pyenv](https://github.com/pyenv/pyenv) (the Python version manager).
To install version 24.0.0 using Pyenv, run the following commands:
```bash
pyenv install graalpy-24.0.0
```
```bash
pyenv shell graalpy-24.0.0
```
> Before running `pyenv install`, you may need to update `pyenv` to include the latest GraalPy versions.

Alternatively, you can download a compressed GraalPy installation file from [GitHub releases](https://github.com/oracle/graalpython/releases).

1. Find the download that matches the pattern _graalpy-XX.Y.Z-macos-amd64.tar.gz_ or _graalpy-XX.Y.Z-macos-aarch64.tar.gz_ (depending on your platform) and download. 
2. Remove the quarantine attribute.
    ```bash
    sudo xattr -r -d com.apple.quarantine /path/to/graalpy
    ```
    For example:
    ```bash
    sudo xattr -r -d com.apple.quarantine ~/.pyenv/versions/graalpy-24.0.0
    ```
3. Uncompress the file and update your `PATH` environment variable to include to the _graalpy-XX.Y.Z-macos-amd64/bin_ (or _graalpy-XX.Y.Z-macos-aarch64/bin_) directory.

### Windows

1. Find and download a compressed GraalPy installation file from [GitHub releases](https://github.com/oracle/graalpython/releases) that matches the pattern _graalpy-XX.Y.Z-windows-amd64.tar.gz_.  
2. Uncompress the file and update your `PATH` variable to include to the _graalpy-XX.Y.Z-windows-amd64/bin_ directory.

#### Windows Limitations

The Windows distribution of GraalPy has more limitations than its Linux or macOS counterpart, so not all features and packages may be available. 

It has the following known issues:
- JLine treats Windows as a dumb terminal, with no autocomplete and limited editing capabilities in the REPL
- Interactive `help()` in the REPL doesn't work
- Inside a virtual environment:
  - _graalpy.cmd_ and _graalpy.exe_ are broken
  - _pip.exe_ cannot be used directly
  - `pip` has trouble with cache file loading, use `--no-cache-dir`
  - Only pure Python binary wheels can be installed, no native extensions or source builds
  - To install a package, use `myvenv/Scripts/python.exe -m pip --no-cache-dir install <pkg>`
- Running from PowerShell works better than running from CMD, various scripts will fail on the latter

## Installing Packages

The best way of using GraalPy is from a [venv](https://docs.python.org/3/library/venv.html) virtual environment.
This generates wrapper scripts and makes the implementation usable from a shell as the standard Python interpreter. 

1. Create a virtual environment with GraalPy by running the following command:
    ```bash
    graalpy -m venv <venv-dir>
    ```
    For example:
    ```bash
    graalpy -m venv ~/.virtualenvs/graalpy-24.0.0
    ```

2. Activate the environment in your shell session:
    ```bash
    source <venv-dir>/bin/activate
    ```
    For example:
    ```bash
    source ~/.virtualenvs/graalpy-24.0.0/bin/activate
    ```

Multiple executables are available in the virtual environment, including: `python`, `python3`, and `graalpy`.

> Note: To deactivate the Python environment (and return to your shell), run `deactivate`.

The `pip` package installer is available when using a virtual environment.
The GraalPy implementation of `pip` may choose package versions other than the latest in cases where it ships patches to make these work better.
