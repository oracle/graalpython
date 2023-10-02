---
layout: docs-experimental
toc_group: python
link_title: Installing Packages
permalink: /reference-manual/python/Packages/
---
# Installing Packages

## Pip

The `pip` package installer is available and working when using a GraalPy virtual environment.

The GraalPy `pip` module ships some patches for packages that the project test internally, these will be applied automatically where necessary.
Support for as many extension modules as possible is a high priority for the project.
The project is actively adding support for the Python C API to make extensions such as NumPy, SciPy, Scikit-learn, Pandas, and TensorFlow work fully.
This means that some might already work, but the project is still working on compatibility, especially with native extensions.

## Including packages in a Java application

When using Python from Java via the GraalVM embedder APIs, some preparation is required to make packages available to the runtime.
After you have created a `venv` virtual environment and installed your required packages, the virtual environment is made available to the Python embedded in Java by setting a context option.
A good idea is to include the entire virtual environment directory as a resource, and use Java's resource API:

```java
String venvExePath = getClass().
        getClassLoader().
        getResource(Paths.get("venv", "bin", "graalpy").toString()).
        getPath();

Context ctx = Context.newBuilder("python").
        allowIO(true).
        option("python.Executable", venvExePath).
        build();

ctx.eval("python", "import site");
```

The initial `import site` instruction loads the Python standard library module `site`, which sets up the library paths.
To do so, it uses the path of the Python executable that is currently running.
For a language such as Python, which is built around the filesystem, this makes sense, but in a Java embedding context, there is no Python executable running.
This is what the `python.Executable` option is for: it reports which executable _would be_ running if we were running Python directly inside your virtual environment.
That is enough to make the machinery work and any packages inside the virtual environment available to the Python embedded in Java.

A simple virtual environment is already quite heavy, because it comes with the machinery to install more packages.
For a Java distribution, you can strip down the virtual environment.
Just run these inside the top-level virtual environment directory:

```bash
find . -type d -name "__pycache__" -exec rm -rf "{}" ";"
rmdir include
rm bin/*
rmdir bin
rm lib/python3.*/site-packages/easy_install.py
rm -rf lib/python3.*/site-packages/pip*
```

Some packages may require the following, but most do not, so you can also remove these, but be aware that it _may_ break a few packages:

```bash
rm -rf lib/python3.*/site-packages/setuptools*
rm -rf lib/python3.*/site-packages/pkg_resources*
```

The GraalPy `standalone` module can be used to generate a Maven project skeleton that includes all necessary setup for a venv embedding into  Java application.
Run `graalpy -m standalone polyglot_app --help` for more information.
