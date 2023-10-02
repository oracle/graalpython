---
layout: docs-experimental
toc_group: python
link_title: Python Code Parsing and pyc Files
permalink: /reference-manual/python/ParserDetails/
---
# Python Code Parsing and pyc Files

This guide describes how Python files are parsed by GraalPy.

## Creating and Managing pyc Files

GraalPy automatically creates a _.pyc_ file when there is an invalid or absent _.pyc_ file that matches the corresponding _.py_ file.

When a Python source file (module) is imported during an execution for the first time, the appropriate _.pyc_ file is created automatically.
If the same module is imported again, then the existing _.pyc_ file is used.
That means that there are no _.pyc_ files for source files that were not executed (imported) yet.
The creation of _.pyc_ files is achieved entirely through the [FileSystem API](https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/io/FileSystem.html), so that embedders can manage file system access.

GraalPy never deletes a _.pyc_ file.

Every subsequent execution of a script will reuse existing _.pyc_ files, or will generate new ones.
A _.pyc_ file is regenerated if the timestamp or hashcode of the original source file is changed.
The hashcode is generated based only on the Python source file by calling `source.hashCode()`, which is the JDK hash code over the array of source file bytes, calculated with `java.util.Arrays.hashCode(byte[])`.

The _.pyc_ files are also regenerated if a magic number in the Python parser is changed.
The magic number is hard-coded in the Python source and can not be changed by the user (unless of course that user has access to the bytecode of Python).

The developers of GraalPy change the magic number when the bytecode format changes.
This is an implementation detail, so the magic number does not have to correspond to the version of GraalPy (as in CPython).
The magic number of `pyc` is a function of the actual Python runtime Java code that is running. Magic number changes will be communicated in the release notes so that embedders or system administrators can delete old _.pyc_ files when upgrading.

Note that if you use _.pyc_ files, you must allow write-access to GraalPy at least when switching versions or modifying the original source code file.
Otherwise, the regeneration of source code files will fail and every import will have the overhead of accessing each old _.pyc_ file, parsing the code, serializing it, and trying (and failing) to write out a new _.pyc_ file.

The directory structure created for _.pyc_ files is as follows:
```python
top_directory
  __pycache__
    sourceA.graalpy.pyc
    sourceB.graalpy.pyc
  sourceA.py
  sourceB.py
  sub_directory
    __pycache__
      sourceX.graalpy.pyc
    sourceX.py
```

By default, the _\_\_pycache\_\__ directory is created on the same directory level as a source code file and in this directory all _.pyc_ files from the same directory are stored.
This directory may store _.pyc_ files created with different versions of Python (including, for example, CPython), so the user may see files ending in _.cpython3-6.pyc_, for example.

_.pyc_ files are largely managed automatically by GraalPy in a manner compatible to CPython. GraalPy provides options similar to CPython to specify the location of the _.pyc_ files, and if they should be written at all, and both of these options can be changed by guest code.

The creation of _.pyc_ files can be controlled in the same way as CPython
(c.f. https://docs.python.org/3/using/cmdline.html):

  * The GraalPy launcher (`graalpy`) reads the `PYTHONDONTWRITEBYTECODE`
    environment variable. If this is set to a non-empty string, Python will not
    try to write _.pyc_ files when importing modules.
  * The launcher command line option `-B`, if given, has the same effect as the
    above.
  * A guest language code can change the attribute `dont_write_bytecode` of the
    `sys` built-in module at runtime to change the behavior for subsequent
    imports.
  * The launcher reads the `PYTHONPYCACHEPREFIX` environment variable. If set,
    the _\_\_pycache\_\__ directory will be created at the path specified by the
    prefix, and a mirror of the directory structure of the source tree will be
    created on-demand to store the _.pyc_ files.
  * A guest language code can change the attribute `pycache_prefix` of the `sys`
    module at runtime to change the location for subsequent imports.

Since the embedder cannot use environment variables or CPython options to
communicate these options to GraalPy, these options are made available as language options:

  * `python.DontWriteBytecodeFlag` - equivalent to `-B` or `PYTHONDONTWRITEBYTECODE`
  * `python.PyCachePrefix` - equivalent to `PYTHONPYCACHEPREFIX`


Note that a Python context will not enable writing _.pyc_ files by default.
The `graalpy` launcher enables it by default, but if this is desired in the embedding use case, care should be taken to ensure that the _\_\_pycache\_\__ location is properly managed and the files in that location are secured against manipulation in the same way as the source code files (_.py_) from which they were derived.

Note also that to upgrade the application sources to Oracle GraalPy, old _.pyc_
files must be removed by the embedder as required.

## Security Considerations

All file operations (obtaining the data, timestamps, and writing _.pyc_ files)
are achieved through the [FileSystem API](https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/io/FileSystem.html). Embedders can modify all of these operations by means of custom (for example, read-only) `FileSystem` implementations.
The embedder can also effectively disable the creation of _.pyc_ files by disabling I/O permissions for GraalPy.

If _.pyc_ files are not readable, their location is not writable.
If the _.pyc_ files' serialization data or magic numbers are corrupted in any way, the deserialization fails and GraalPy parses the _.py_ source code file again.
This comes with a minor performance hit *only* for the parsing of modules, which should not be significant for most applications (provided the application performs actual work in addition to loading Python code).
