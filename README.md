# GraalVM Implementation of Python

This is an early-stage experimental implementation of Python. A primary goal is
to support SciPy and its constituent libraries. This Python implementation
currently aims to be compatible with Python 3.7, but it is a long way from
there, and it is very likely that any Python program that requires any packages
at all will hit something unsupported. At this point, the Python implementation
is made available for experimentation and curious end-users.

### Trying it

To try it, you can use the bundled releases from
[www.graalvm.org](https://www.graalvm.org/downloads/). For more information and
some examples of what you can do with it, check out the
[reference](https://www.graalvm.org/docs/reference-manual/languages/python/).

### Create a virtual environment

The best way of using graalpython is out of a virtual environment. This generates 
wrapper scripts and makes graalpython usable from shell as standard python interpreter. To do so
execute the following command in the graalpython project directory:

```
mx graalpython -m venv <dir-to-venv>
```

To activate the environment in your shell session call:

```
source <dir-to-venv>/bin/activate
```

In the venv multiple executables are available, like `python`, `python3` and `graalpython`. 
Also `pip`(or `pip3`) can be used out of here to install libraries from local sources or see installed modules by:

```
pip list
```

We recommend the installation commands mentioned in the next section. Should the intended package not be available, also pip can be used. 
Although we don't guarantee successful installations for packages installed without `ginstall`.
To install a new package download the source(e.g. the `.tar.gz` file, not the wheel file), unpack it and run the command in the libraries source directory:

```
pip install .
```



### Installing packages

At the moment not enough of the standard library is implemented to run the
standard package installers for many packages. As a convenience, we provide a
simple module to install packages that we know to be working (including
potential patches required for those packages). Try the following to find out
more:

```
graalpython -m ginstall --help
```

As a slightly more exciting example, try:

```
graalpython -m ginstall install numpy
```

If all goes well (you'll need to have `clang`, `llvm-link`, `llvm-extract`,
`llvm-nm`, and `opt` in your `PATH` in addition to the normal NumPy build
dependencies), you should be able to `import numpy` afterwards.

Support for more extension modules is high priority for us. We are actively
building out our support for the Python C API to make extensions such as NumPy,
SciPy, Scikit-learn, Pandas, Tensorflow and the like work. This work means that
some other extensions might also already work, but we're not actively testing
other extensions right now and cannot promise anything. Note that to try
extensions on this implementation, you have to download, build, and install them
manually for now. To do so, we recommend LLVM 6. Other versions might also work,
but this version is what we're testing with in our CI.

### Polyglot Usage

We have a [document](doc/POLYGLOT.md) describing how we implement the
cross-language interop. This will hopefully give you an idea how to use it.

### Contributing

I you're thinking about contributing something to this repository, you will need
to sign the [Oracle Contributor
Agreement](http://www.graalvm.org/community/contributors/) for us to able to
merge your work. Please also take note of our [code of
conduct](http://www.graalvm.org/community/conduct/) for contributors.

To get you started, we have [written a bit](doc/CONTRIBUTING.md) about the
structure of this interpreter that should show how to fix things or add
features.

#### IDE usage

Graalpython provides commands to initialize the project for usage in various IDE's. To do so call the corresponding 
command for your IDE in graalpython source directory:

```
mx intellijinit
mx eclipseinit
mx netbeansinit
```

This generates a clean source tree in the IDE and provides additional configurations for Graal.    

#### Debug options

Graalpython provides proper debug options. It is possible to either debug the python code, using Chrome debugger,   
or the java code, using your preferred IDE. 
The following commands should be executed in a virtualenv environment, which provides a graalpython executable.

For debug python side code call this:

```graalpython --inspect your_script.py ```

This will open a debug server, which can be accessed in Chrome Browser under URL `chrome://inspect`.

For debugging java implemented code execute:

```graalpython --experimental-options -debug-java your_script.py```

The command will also start a debug server, which can be used in an IDE. If the IDE was initialized properly 
by using the commands mentioned above, the existing `GraalDebug` run configuration can be used to debug.


### Licensing

This GraalVM implementation of Python is copyright (c) 2017, 2019 Oracle and/or
its affiliates and is made available to you under the terms the Universal
Permissive License v 1.0 as shown at
[http://oss.oracle.com/licenses/upl](http://oss.oracle.com/licenses/upl). This
implementation is in part derived from and contains additional code from 3rd
parties, the copyrights and licensing of which is detailed in the
[LICENSE](LICENSE) and [3rd_party_licenses.txt](3rd_party_licenses.txt) files.

