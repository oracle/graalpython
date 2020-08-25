# Installing Supported Packages

### Create a virtual environment
The best way of using the GraalVM implementation of Python is out of a virtual
environment. This generates wrapper scripts and makes the implementation usable
from shell as standard Python interpreter. To do so execute the following from a
GraalVM installation:

```shell
graalpython -m venv <venv-dir>
```

To activate the environment in your shell session call:

```shell
source <venv-dir>/bin/activate
```

### Using ginstall
At the moment not enough of the standard library is implemented to run the
standard package installers for many packages. As a convenience, a
simple module to install packages is provided (including
potential patches required for those packages). Try the following to find out
more:

```shell
graalpython -m ginstall --help
```

As a slightly more exciting example, try:

```shell
graalpython -m ginstall install numpy
```

If all goes well (also consider native dependencies of NumPy), you should be
able to `import numpy` afterwards.

Support for more extensions is a high priority. The work is actively done
to enable support for the Python C API, to make extensions such as NumPy, SciPy,
Scikit-learn, Pandas, Tensorflow and the like work. Some other extensions might
also already work, but they are not actively tested. Note that to try extensions
with GraalVM Python implementation, you have to download, build and install them manually
for now.

### Using PIP
The pip package installer is available and working in a `venv`, but there is no
support for SSL yet. That means you can install packages from PyPI if you use an
HTTP mirror and you can install local packages.
