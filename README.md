# GraalVM Implementation of Python

This is GraalPy, an implementation of the Python language.
A primary goal is to support SciPy and its constituent libraries, as well as to work with other data science and machine learning libraries from the rich Python ecosystem.
GraalPy can usually execute pure Python code faster than CPython (but not when C extensions are involved).
GraalPy currently aims to be compatible with Python 3.10, but it is some way from there.
While your specific workload may function, any Python program that uses external packages could hit something unsupported.
At this point, the Python implementation is made available for experimentation and curious end-users.

### Trying It

The easiest option to try GraalPy is [Pyenv](https://github.com/pyenv/pyenv/), the Python version manager.
It allows you to easily install different GraalPy releases.
To get version 22.3.0, for example, just run `pyenv install graalpy-22.3.0`.

To try GraalPy with a full GraalVM, including the support for Java embedding and interoperability with other languages, you can use the bundled releases from [www.graalvm.org](https://www.graalvm.org/downloads/).

Another option is to use [Conda-Forge](https://conda-forge.org/).
To get an environment with the latest GraalPy, use `conda create -c conda-forge -n graalpy graalpy`.

### Building from Source

#### Requirements

* [mx](https://github.com/graalvm/mx) - a separate Python tool co-developed for GraalVM development. This tool must be
  downloaded and put onto your PATH:
  ```
  git clone https://github.com/graalvm/mx.git
  export PATH=$PWD/mx:$PATH
  ```
* LabsJDK

The following command will download and install JDKs upon which to build GraalVM. If successful, it will print the path for your JAVA_HOME. 
```shell
mx fetch-jdk
```
 
#### Building

Run `mx --dy /compiler python-gvm` in the root directory of the `graalpython` repository. If the build succeeds, it will print the full
path to the `graalpy` executable as the last line of output.

For more information and some examples of what you can do with GraalPy,
check out the [reference](https://www.graalvm.org/reference-manual/python/).

### Create a Virtual Environment

The best way of using GraalPy is from a virtual environment. This generates wrapper scripts and makes the implementation usable from a shell as the standard Python interpreter. To create a virtual environment with GraalPy, run the following command in your project directory:

```bash
graalpy -m venv <venv-dir>
```

To activate the environment in your shell session run:

```bash
source <venv-dir>/bin/activate
```

Multiple executables are available in the virtual environment, including `python`, `python3` and `graalpy`.

### Installing Packages

You should be able to use the `pip` command from a GraalPy virtual environment to install packages.
Our `pip` ships some patches for packages that we test internally--these will be applied automatically where necessary.
Support for as many extension modules as possible is a high priority for us.
We are actively building out our support for the Python C API to make extensions such as NumPy, SciPy, Scikit-learn, Pandas, Tensorflow and the like work fully.
This means that some might already work, but we're still actively working on compatibility especially with native extensions.

### Polyglot Usage

We have a [document](docs/user/Interoperability.md) describing how we implement
cross-language interoperability. This will hopefully give you an idea about how to use it.

### Jython Support

We are working on a mode that is "mostly compatible" with some of Jython's
features, minus of course that Jython implements Python 2.7 and we implement
Python 3.10+. We describe the current status of the compatibility mode
[here](docs/user/Jython.md).

### Contributing

If you're thinking about contributing something to this repository, you will need
to sign the [Oracle Contributor
Agreement](http://www.graalvm.org/community/contributors/) for us to able to
merge your work. Please also take note of our [code of
conduct](http://www.graalvm.org/community/conduct/) for contributors.

To get you started, we have [written a bit](docs/contributor/CONTRIBUTING.md) about the
structure of this interpreter that should show how to fix things or add
features.

### Licensing

This GraalVM implementation of Python is copyright (c) 2017, 2023 Oracle and/or
its affiliates and is made available to you under the terms the Universal
Permissive License v 1.0 as shown at
[https://oss.oracle.com/licenses/upl/](https://oss.oracle.com/licenses/upl/). This
implementation is in part derived from and contains additional code from 3rd
parties, the copyrights and licensing of which is detailed in the
[LICENSE](LICENSE) and [THIRD_PARTY_LICENSE](THIRD_PARTY_LICENSE.txt) files.

