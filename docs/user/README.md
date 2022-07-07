---
layout: docs-experimental
toc_group: python
link_title: Python Reference
permalink: /reference-manual/python/
redirect_from: /docs/reference-manual/python/
---

# GraalVM Python Runtime

GraalVM provides a Python 3.8 compliant runtime.
A primary goal of the GraalVM Python runtime is to support SciPy and its constituent libraries, as well as to work with other data science and machine learning libraries from the rich Python ecosystem.
At this point, the Python runtime is made available for experimentation and curious end-users.
See [FAQ](FAQ.md) for commonly asked questions about this implementation.

## Installing Python

The Python runtime is not provided by default, and can be added to GraalVM with the [GraalVM Updater](https://github.com/oracle/graal/blob/master/docs/reference-manual/graalvm-updater.md), `gu`, tool:
```shell
gu install python
```

The above command will install Python from the catalog.

## Running Python

GraalVM's Python support targets Python 3.8 compatibility.
While the support is still limited, you can run simple Python commands or programs with the `graalpy` launcher:
```shell
graalpy [options] [-c cmd | filename]
```

If no program file or command is given, you are dropped into a simple REPL.

GraalVM supports some of the same options as Python 3.8 as well as some additional options to control the underlying Python runtime, GraalVM's tools, and the execution engine.
These can be viewed using the following command:
```shell
graalpy --help --help:tools --help:languages
```

## Installing Supported Packages

GraalVM Python runtime comes with a tool called `ginstall` which may be used to install a small list of packages known to work to some extent with GraalVM's Python runtime.
It is recommended to always create a virtual environment first, using the standard Python module `venv`.
Creating such an environment avoids any incompatible interaction with the local user's packages that may have been
installed using a system installation of CPython:
```shell
graalpy -m venv my_new_venv
source my_new_venv/bin/activate
```

To see the list of installable packages, run:
```shell
graalpy -m ginstall install --help
```

This will print a short help document including a comma-separated list of packages you
can install. The installation works as described in that help document:
```shell
graalpy -m ginstall install pandas
```

Note that when calling Python from Java, the polyglot shell, or another language on GraalVM, you should always evaluate the piece of Python code first to make installed packages available:
```shell
import site
```

For more information, continue reading to the [Installing Supported Packages](Packages.md) guide.

## Native Image and JVM Runtime

By default, GraalVM runs Python from a binary, compiled ahead-of-time with [Native Image](https://github.com/oracle/graal/blob/master/docs/reference-manual/native-image/README.md), yielding faster startup time and lower footprint.
Although the ahead-of-time compiled binary includes the Python and LLVM interpreters, in order to interoperate with
other languages you have to supply the `--jvm` argument.
This instructs the launcher to run on the JVM instead of in Native Image mode.
Thus, you will notice a longer startup time.
