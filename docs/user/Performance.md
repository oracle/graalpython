---
layout: docs-experimental
toc_group: python
link_title: Python Performance
permalink: /reference-manual/python/Performance/
redirect_from:
  - /reference-manual/python/ParserDetails/
---

# Python Performance

## Execution Performance

GraalPy uses the state-of-the-art just-in-time (JIT) compiler of GraalVM.
When JIT compiled, GraalPy runs Python code ~4x faster than CPython on the official [Python Performance Benchmark Suite](https://pyperformance.readthedocs.io/).
![](performance.svg)

These benchmarks can be run by installing the `pyperformance` package and calling `pyperformance run` on each of CPython and GraalPy.
To get the Jython numbers we adapted the harness and benchmarks because of missing Python 3 support in Jython.
The speedup was then calculated by taking the pair-wise intersection of working benchmarks and calculating the geomean.

Without a JIT compiler, GraalPy currently executes pure Python code around ~4x slower than CPython.
This means that very short running scripts or scripts running without the Graal compiler on Oracle JDK or OpenJDK are expected to be slower.

Many Python packages from the machine learning or data science ecosystems contain C extension code.
This code benefits little from GraalPy's JIT compilation and suffers from having to emulate CPython implementation details on GraalPy.
When many C extensions are involved, performance can vary a lot depending on the specific interactions of native and Python code.

## Code Loading Performance and Footprint

It takes time to parse Python code so when using GraalPy to embed another language in Python, observe the general advice for embedding Graal languages related to [code caching](https://www.graalvm.org/latest/reference-manual/embed-languages/#code-caching-across-multiple-contexts).
Furthermore, some Python libraries require loading a large amount of code on startup before they can do any work.
Due to the design of the Python language, incremental parsing is not possible and for some scripts, the parser may represent a significant fraction of runtime and memory.
To mitigate this, GraalPy can cache the bytecode generated during parsing in *.pyc* files, if appropriate file system access is configured.

### Creating and Managing _.pyc_ Files

GraalPy automatically creates a _.pyc_ file when there is an invalid or absent _.pyc_ file that matches a corresponding _.py_ file.

When GraalPy imports a Python source file (module) during an execution for the first time, it automatically creates a corresponding _.pyc_ file.
If GraalPy imports the same module again, then it uses the existing _.pyc_ file.
That means that there are no _.pyc_ files for source files that were not yet executed (imported).
GraalPy creates _.pyc_ files entirely through the [FileSystem API](https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/io/FileSystem.html), so that a Java application with embedded Python code can manage file system access.

> Note: GraalPy never deletes a _.pyc_ file.

Every time GraalPy subsequently executes a script, it reuses the existing _.pyc_ file, or creates a new one.
GraalPy recreates a _.pyc_ file if the timestamp or hashcode of the original source file is changed.
GraalPy generates the hashcode based only on the Python source file by calling `source.hashCode()`, which is the JDK hash code over the array of source file bytes, calculated with `java.util.Arrays.hashCode(byte[])`.

GraalPy also recreates _.pyc_ files if a magic number in the Python parser is changed.
The magic number is hard-coded in the source of Python and can not be changed by the user (unless of course that user has access to the bytecode of Python).

The developers of GraalPy change the magic number when the bytecode format changes.
This is an implementation detail, so the magic number does not have to correspond to the version of GraalPy (as in CPython).
The magic number of `pyc` is a function of the actual Python runtime Java code that is running. Changes to the magic number are communicated in the release notes so that developers or system administrators can delete old _.pyc_ files when upgrading.

Note that if you use _.pyc_ files, you must allow write-access to GraalPy at least when switching versions or modifying the original source code file.
Otherwise, the regeneration of source code files will fail and every import will have the overhead of accessing each old _.pyc_ file, parsing the code, serializing it, and trying (and failing) to write out a new _.pyc_ file.

GraalPy creates the following directory structure for _.pyc_ files:
```bash
top_directory/
  __pycache__/
    sourceA.graalpy.pyc
    sourceB.graalpy.pyc
  sourceA.py
  sourceB.py
  sub_directory/
    __pycache__/
      sourceX.graalpy.pyc
    sourceX.py
```

By default, GraalPy creates the _\_\_pycache\_\__ directory on the same directory level as a source code file and in this directory all _.pyc_ files from the same directory are stored.
This directory may store _.pyc_ files created with different versions of Python (including, for example, CPython), so the user may see files ending in _.cpython3-6.pyc_, for example.

_.pyc_ files are largely managed automatically by GraalPy in a manner compatible with CPython. GraalPy provides options similar to CPython to specify the location of t_.pyc_ files, and if they should be written at all, and both of these options can be changed by guest code.

The creation of _.pyc_ files can be controlled in the [same way as CPython](https://docs.python.org/3/using/cmdline.html):

  * The GraalPy launcher (`graalpy`) reads the `PYTHONDONTWRITEBYTECODE`
    environment variable. If this is set to a non-empty string, Python will not
    try to create a _.pyc_ file when importing a module.
  * The launcher command line option `-B`, if given, has the same effect as the
    above.
  * Guest language code can change the attribute `dont_write_bytecode` of the
    `sys` built-in module at runtime to change the behavior for subsequent
    imports.
  * The GraalPy launcher reads the `PYTHONPYCACHEPREFIX` environment variable. If set,
    it creates the _\_\_pycache\_\__ directory at the path specified by the
    prefix, and creates a mirror of the directory structure of the source tree 
    on-demand to store the _.pyc_ files.
  * A guest language code can change the attribute `pycache_prefix` of the `sys`
    module at runtime to change the location for subsequent imports.

Because the developer cannot use environment variables or CPython options to
communicate these options to GraalPy, these options are made available as language options:

  * `python.DontWriteBytecodeFlag` - equivalent to `-B` or `PYTHONDONTWRITEBYTECODE`
  * `python.PyCachePrefix` - equivalent to `PYTHONPYCACHEPREFIX`


Note that a Python context will not enable writing _.pyc_ files by default.
The GraalPy launcher enables it by default, but if this is desired in the embedding use case, care should be taken to ensure that the _\_\_pycache\_\__ location is properly managed and the files in that location are secured against manipulation in the same way as the source code files (_.py_) from which they were derived.

Note also that to upgrade the application sources to Oracle GraalPy, old _.pyc_
files must be removed by the developer as required.

### Security Considerations

GraalPy performs all file operations (obtaining the data, timestamps, and writing _.pyc_ files)
via the [FileSystem API](https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/io/FileSystem.html). Developers can modify all of these operations by means of custom (for example, read-only) `FileSystem` implementations.
The developer can also effectively disable the creation of _.pyc_ files by disabling I/O permissions for GraalPy.

If _.pyc_ files are not readable, their location is not writable.
If the _.pyc_ files' serialization data or magic numbers are corrupted in any way, the deserialization fails and GraalPy parses the _.py_ source code file again.
This comes with a minor performance hit *only* for the parsing of modules, which should not be significant for most applications (provided the application performs actual work in addition to loading Python code).

