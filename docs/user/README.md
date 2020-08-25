# Python Implementation for GraalVM

GraalVM provides an implementation of Python 3.8. A primary goal of the Python
implementation is to support SciPy and its constituent libraries as well as to work
with other data science and machine learning libraries from the rich Python
ecosystem. At this point, the Python implementation is made available for
experimentation and curious end-users.  See [FAQ](FAQ.md) for commonly asked
questions about this implementation.

## Installing Python

Python can be added to the GraalVM installation with the [GraalVM Updater](https://www.graalvm.org/docs/reference-manual/gu/), `gu`, tool:
```
$ gu install python
```
The above command will install a community version of a component from GitHub catalog.
For GraalVM Enterprise users, the [manual component installation](https://www.graalvm.org/docs/reference-manual/gu/#manual-installation) is required. See `bin/gu --help` for more information.

## Running Python

GraalVM implementation of Python targets Python 3.8 compatibility. While support
for the Python language is still limited, you can run simple Python scripts or
commands with the `graalpython` binary.
```
$ graalpython [options] [-c cmd | filename]
```

If no program file or command is given, you are dropped into a simple REPL.

GraalVM supports some of the same options as Python 3.8 and some additional
options to control the underlying Python implementation, the GraalVM tools
and the execution engine. These can be viewed using the following command:

```
$ graalpython --help --help:tools --help:languages
```

## Installing Supported Packages

Python comes with a tool called `ginstall` used to install a small list of
packages known to work to some extent with GraalVM implementation of Python.
It is recommended to always create a virtual environment first, using the
standard Python module `venv`. Creating such an environment avoids any
incompatible interaction with the local user's packages that may have been
installed using a system installation of CPython:

```shell
$ graalpython -m venv my_new_venv
$ source my_new_venv/bin/activate
```

To see the list of installable packages, run:

```shell
$ graalpython -m ginstall install --help
```

This will print a short help including a comma-separated list of packages you
can install. The installation works as described in that help:

```shell
$ graalpython -m ginstall install pandas
```

Note that when using the GraalVM implementation of Python from Java, the
polyglot shell, or another language, you should always evaluate the piece of
Python code first to make installed packages available:

```shell
import site
```

Continue reading to the [Installing Supported Packages](Packages.md) guide.

### Native Image and JVM Runtime

By default, GraalVM runs Python from an ahead-of-time compiled binary, yielding
faster startup time and lower footprint. However, the ahead-of-time compiled
binary only includes the Python and LLVM interpreters. To interoperate with
other languages, we had to supply the `--jvm` argument above. This instructs the
launcher to run on the JVM instead of in the Native Image mode -- you will notice a
longer startup time.
