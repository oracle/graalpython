# GraalPy, the GraalVM Implementation of Python

GraalPy is an implementation of the Python language on top of GraalVM.
A primary goal is to support PyTorch, SciPy, and their constituent libraries, as well as to work with other data science and machine learning libraries from the rich Python ecosystem.
GraalPy can usually execute pure Python code faster than CPython, and nearly match CPython performance when C extensions are involved.
GraalPy currently aims to be compatible with Python 3.10.
While many workloads run fine, any Python program that uses external packages could hit something unsupported.
At this point, the Python implementation is made available for experimentation and curious end-users.
We welcome issue reports of all kinds and are working hard to close our compatibility gaps.

## Installation

The easiest option to try GraalPy is [pyenv](https://github.com/pyenv/pyenv/), the Python version manager.
It allows you to easily install different GraalPy releases.
To install version 23.1.0, for example, just run `pyenv install graalpy-community-23.1.0`.

Another option is to use [Conda-Forge](https://conda-forge.org/).
To get an environment with the latest version of GraalPy, use the following command:

```bash
conda create -c conda-forge -n graalpy graalpy
```

To try GraalPy with a full GraalVM, including the support for Java embedding and interoperability with other languages, you can use the bundled releases from [www.graalvm.org](https://www.graalvm.org/downloads/).

>**Note:** There is currently no installer for Windows.

## Building from Source

#### Requirements

* [mx](https://github.com/graalvm/mx)

  There is a separate Python tool for GraalVM development. This tool must be downloaded and added to your PATH:
  
  ```shell
  git clone https://github.com/graalvm/mx.git
  export PATH=$PWD/mx:$PATH
  ```
* LabsJDK

  The following command will download and install JDKs upon which to build GraalVM. If successful, it will print the path for the value of your `JAVA_HOME` environment variable. 
  ```shell
  mx fetch-jdk
  ```
 
#### Building

Run `mx --dy /compiler python-gvm` in the root directory of the `graalpython` repository.
If the build succeeds, it will print the full path to the `graalpy` executable as the last line of output.
This builds a `bash` launcher that executes GraalPy on the JVM.
To build a native launcher for lower footprint and better startup, run `mx python-svm` instead.

For more information and some examples of what you can do with GraalPy, see the [reference documentation](https://www.graalvm.org/reference-manual/python/).

## Polyglot Usage

See the [documentation](docs/user/Interoperability.md) that describes how we implement
cross-language interoperability. 
This should give you an idea about how to use it.

## Jython Support

We are working on a mode that is "mostly compatible" with some of Jython's
features, minus of course that Jython implements Python 2.7 and we implement
Python 3.10+. 
We describe the current status of the compatibility mode [here](docs/user/Jython.md).

We are working on a mode that is "mostly compatible" with some of Jython's features, considering the fact that Jython implements Python 2.7 and we implement Python 3.10+. 
For more details about compatibility, see [here](docs/user/Jython.md).

## Contributing

If you're thinking about contributing something to this repository, you will need to sign the [Oracle Contributor Agreement](http://www.graalvm.org/community/contributors/) for us to able to merge your work.
Please also take note of our [code of conduct](http://www.graalvm.org/community/conduct/) for contributors.

This project welcomes contributions from the community. Before submitting a pull request, please [review our contribution guide](./CONTRIBUTING.md).

## Security

Please consult the [security guide](./SECURITY.md) for our responsible security vulnerability disclosure process.

## License

This GraalVM implementation of Python is Copyright (c) 2017, 2023 Oracle and/or its affiliates and is made available to you under the terms the Universal Permissive License v 1.0 as shown at [https://oss.oracle.com/licenses/upl/](https://oss.oracle.com/licenses/upl/).
This implementation is in part derived from and contains additional code from 3rd parties, the copyrights and licensing of which is detailed in the [LICENSE](./LICENSE.txt) and [THIRD_PARTY_LICENSE](THIRD_PARTY_LICENSE.txt) files.
