---
layout: docs-experimental
toc_group: python
link_title: Python Code Parsing and pyc Files
permalink: /reference-manual/python/ParserDetails/
---
# Python Code Parsing and pyc Files

This guide elaborates on how Python files are parsed on GraalPy.

## Creating and Managing pyc Files

#### `.pyc` files are created automatically by GraalPy when no or an invalid `.pyc` file is found matching the desired `.py` file.

When a Python source file (module) is imported during an execution for the first time, the appropriate `.pyc` file is created automatically.
If the same module is imported again, then the already created `.pyc` file is used.
That means that there are no `.pyc` files for source files that were not executed (imported) yet.
The creation of `.pyc` files is done entirely through the [FileSystem API](https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/io/FileSystem.html), so that embedders can manage the file system access.

Every subsequent execution of a script will reuse the already existing `.pyc` files, or will generate a new one.
A `.pyc` file is regenerated if the timestamp or hashcode of the original source file is changed.
The hashcode is generated only based on the Python source by calling `source.hashCode()`, which is the JDK hash code over the array of source file bytes, calculated with `java.util.Arrays.hashCode(byte[])`.

The `.pyc` files are also regenerated if a magic number in the Python parser is changed.
The magic number is hard-coded in the Python source and can not be changed by the user (unless of course that user has access to the bytecode of Python).

The developers of GraalPy change the magic number when the bytecode format changes.
This is an implementation detail, so the magic number does not have to correspond to the version of GraalPy (just like in CPython).
The magic number of pyc is a function of the concrete Python runtime Java code that is running.

Note that if you use `.pyc` files, you will need to allow write-access to GraalPy at least when switching versions or changing the original source code.
Otherwise, the regeneration of source files will fail and every import will have the overhead of accessing the old `.pyc` file, parsing the code, serializing it, and trying (and failing) to write out a new `.pyc` file.

A `*.pyc` file is never deleted by GraalPy, only regenerated.
It is regenerated when the appropriate source file is changed (timestamp of last modification or hashcode of the content) or the magic number of the Python imnplementation parser changes.
Magic number changes will be communicated in the release notes so that embedders or system administrators can delete old `.pyc` files when upgrading.

The folder structure created for `.pyc` files looks like this:
```python
top_folder
    __pycache__
         sourceA.graalpy.pyc
         sourceB.graalpy.pyc
    sourceA.py
    sourceB.py
    sub_folder
        __pycache__
            sourceX.graalpy.pyc
        sourceX.py
```

By default the `__pycache__` directory is created on the same directory level as a source code file and in this directory all `.pyc` files from the same directory are stored.
This folder may store `.pyc` files created with different versions of Python (including, e.g., CPython), so the user may see files ending in `*.cpython3-6.pyc` for example.

`.pyc` files are largely managed automatically by the runtime in a manner compatible to CPython. Like on CPython there are options to specify their location, and if they should be written at all, and both of these options can be changed by guest code.

The creation of `*.pyc` files can be controlled in the same ways as on CPython
(c.f. https://docs.python.org/3/using/cmdline.html):

  * The GraalPy launcher (`graalpy`) reads the `PYTHONDONTWRITEBYTECODE`
    environment variable. If this is set to a non-empty string, Python will not
    try to write `.pyc` files when importing modules.
  * The launcher command line option `-B`, if given, has the same effect as the
    above.
  * A guest language code can change the attribute `dont_write_bytecode` of the
    `sys` built-in module at runtime to change the behavior for subsequent
    imports.
  * The launcher reads the `PYTHONPYCACHEPREFIX` environment variable. If set,
    the `__pycache__` directory will be created at the path pointed to by the
    prefix, and a mirror of the directory structure of the source tree will be
    created on-demand to house the `.pyc` files.
  * A guest language code can change the attribute `pycache_prefix` of the `sys`
    module at runtime to change the location for subsequent imports.

Since the embedder cannot use environment variables or CPython options to
communicate these options to GraalPy, these options are made available as
these language options:

  * `python.DontWriteBytecodeFlag` - equivalent to `-B` or `PYTHONDONTWRITEBYTECODE`
  * `python.PyCachePrefix` - equivalent to `PYTHONPYCACHEPREFIX`


Note that a Python context will not enable writing `.pyc` files by default.
The `graalpy` launcher enables it by default, but if this is desired in the embedding use case, care should be taken to ensure that the `__pycache__` location is properly managed and the files in that location are secured against manipulation just like the source `.py` files they were derived from.

Note also that to upgrade the application sources to Oracle GraalPy, old `.pyc`
files must be removed by the embedder as required.

## Security Considerations

All file operations (obtaining the data, timestamps, and writing `pyc` files)
are done through the [FileSystem API](https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/io/FileSystem.html). Embedders can modify all of these operations by means of custom (e.g., read-only) `FileSystem` implementations.
The embedder can also effectively disable the creation of `.pyc` files by disabling I/O permissions for GraalPy.

If the `.pyc` files are not readable, their location is not writable.
If the `.pyc` files' serialization data or magic numbers are corrupted in any way, the deserialization fails and we just parse the `.py` file again.
This comes with a minor performance hit *only* for the parsing of modules, which should not be significant for most applications (provided the application does actual work besides loading Python code).
