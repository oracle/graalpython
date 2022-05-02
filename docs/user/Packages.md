---
layout: docs-experimental
toc_group: python
link_title: Installing Supported Packages
permalink: /reference-manual/python/Packages/
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

The `pip` package installer is available and working when using a `venv`.

### Including packages in a Java application

When using Python from Java via the GraalVM embedder APIs, a bit of preparationis required to make packages available to the runtime.
After a venv is created and any desired packages are installed, this venv is made available to the Java embedded Python by setting a context option.
A good idea is to include the entire venv folder as a resource, and use Java's resource API:

```java
String venvExePath = getClass().
        getClassLoader().
        getResource(Paths.get("venv", "bin", "graalpython").toString()).
        getPath();

Context ctx = Context.newBuilder("python").
        allowIO(true).
        option("python.Executable", venvExePath).
        build();

ctx.eval("python", "import site");
```

The initial `import site` loads the Python standard library module `site`, which sets up the library paths.
To do so, it uses the path of the currently running Python executable.
For a language like Python, which is built around the filesystem, this makes sense, but in our Java embedding context, we do not have a Python executable running.
This is what the `python.Executable` option is for: it reports which executable _would be_ running if we were running Python directly inside our venv.
That is enough to make the machinery work and any packages inside the venv available to the embedded Python in Java.

A simple venv is already quite heavy, because it comes with the machinery to install more packages.
For a Java distribution, we can strip the venv down somewhat without much trouble.
Just run these inside the top-level venv directory:
```shell
find . -type d -name "__pycache__" -exec rm -rf "{}" ";"
rmdir include
rm bin/*
rmdir bin
rm lib/python3.*/site-packages/easy_install.py
rm -rf lib/python3.*/site-packages/pip*
```

Some packages may require the following, but most do not, so you can also remove these, but be aware that it _may_ break a few packages:
```shell
rm -rf lib/python3.*/site-packages/setuptools*
rm -rf lib/python3.*/site-packages/pkg_resources*
```
