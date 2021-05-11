---
layout: docs-experimental
toc_group: python
title: Installing Supported Packages
link_title: Installing Supported Packages
permalink: /reference-manual/python/Packages/
redirect_from: /docs/reference-manual/python/Packages/
next: /en/graalvm/enterprise/{{ site.version }}/docs/reference-manual/python/Interoperability/
previous: /en/graalvm/enterprise/{{ site.version }}/docs/reference-manual/python/
---
# Installing Supported Packages

## Create a Virtual Environment

The best way of using GraalVM's Python runtime is from a virtual environment.
This generates wrapper scripts and makes the implementation usable from shell as the standard Python interpreter.
To create the virtual environment with GraalVM:
```shell
graalpython -m venv <venv-dir>
```

To activate the environment in your shell session call:
```shell
source <venv-dir>/bin/activate
```

### Using `ginstall`
At the moment, there are not enough standard libraries implemented to run the standard package installers for many packages.
As a convenience, a simple module to install packages is provided (including potential patches required for those packages).
Try the following to find out more:
```shell
graalpython -m ginstall --help
```

As a slightly more exciting example, try:
```shell
graalpython -m ginstall install numpy
```

If all goes well (also consider native dependencies of NumPy), you should be able to `import numpy` afterwards.

The support for more extensions is a high priority.
The GraalVM team is actively working to enable support for the Python C API, as well as to make extensions such as NumPy, SciPy, Scikit-learn, Pandas, Tensorflow, and alike, work.
Other extensions might currently work, but they are not actively tested.
Note that to try extensions on GraalVM's Python runtime, you have to download, build, and install them manually for now.

### Using `pip`
The `pip` package installer is available and working in a `venv`, but there is no support for SSL yet.
This means you can install packages from PyPI if you use an HTTP mirror, and you can install local packages.
