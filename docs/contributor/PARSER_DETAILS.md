# Python Code Parsing and pyc Files

This document elaborates on various things to consider about how Python files in
the GraalPython implementation are parsed.

## Parser Performance

Creating the Truffle tree for a Python source has two phases. The first one
creates a simple syntax tree (SST) and a scope tree, the second phase transforms
the SST to the Truffle tree and for the transformation we need the scope
tree. The scope tree contains scope locations for variable and function
definitions and information about scopes. The simple syntax tree contains nodes
mirroring the source. Comparing the SST and Truffle tree, the SST is much
smaller. It contains just the nodes representing the source in a simple way. One
SST node is usually translated to many Truffle nodes.

The simple syntax tree can be created in two ways: with ANTLR parsing or
deserialization from an appropriate `*.pyc` file. If there is no appropriate
`.pyc` file for a source, then the source is parsed with ANTLR. If the Python
standard import logic finds an appropriate `.pyc` file, it will just trigger
deserialization of the SST and scope tree from it. The deserialization is much
faster than source parsing with ANTLR and needs only roughly 30% of the time
that ANTLR needs. Of course the first import of a new file is a little bit
slower - besides parsing with ANTLR, the Python standard library import logic
serializes the resulting code object to a `.pyc` file, which in our case means
the SST and scope tree are serialized such a file.

> *Summary*: Loading code from serialized `.pyc` files is faster than parsing
> the `.py` file using ANTLR.

## Creating and Managing pyc Files

When a python source file (module) is imported during an execution for the first
time, then the appropriate `.pyc` file is created automatically. If the same
module is imported again, then the already created `.pyc` file is used. That
means that there are no `.pyc` files for source files that were not executed
(imported) yet. The creation of `.pyc` files is done entirely through the
Truffle FileSystem API, so that embedders can manage the file system access.

Every subsequent execution of a script will reuse the already existing `.pyc`
files or will generate a new one. A `.pyc` file is regenerated if the timestamp
or hashcode of the original source file is changed.  The hashcode is generated
only based on the Python source by calling `source.hashCode()`, which is the JDK
hash code over the array of source file bytes, calculated with
`java.util.Arrays.hashCode(byte[])`. The `.pyc` files are also regenerated if a
magic number in the GraalPython parser is changed. The magic number is
hard-coded in the GraalPython source and can not be changed by the user (unless
that user has access to the bytecode of GraalPython, in which case they can do
anything they want already). The developers of GraalPython change the magic
number when the format of SST or scope tree binary data is altered. This is an
implementation detail, so the magic number does not have to correspond to the
version of GraalPython (just like in CPython). The magic number of pyc is a
function of the concrete GraalPython Java code that is running.

> *Summary*: `.pyc` files are created automatically by the GraalPython runtime
> when no or an invalid `.pyc` file is found matching the desired `.py` file.

> **Important**: If you use `.pyc` files, you will need to allow write-access to
> the GraalPython runtime at least when switching versions or changing the
> original source code - otherwise, the regeneration of source files will fail
> and every import will have the overhead of accessing the old `.pyc` file,
> parsing the code, serializing it, and trying (and failing) to write out a new
> `.pyc` file.

A `*.pyc` file is never deleted by GraalPython, only regenerated. It is
regenerated when the appropriate source file is changed (timestamp of last
modification or hashcode of the content) or the magic number of the GraalPython
parser changes. Magic number changes will be communicated in the release notes
so that embedders or system administrators can delete old `.pyc` files when
upgrading.

The folder structure created for `.pyc` files looks like this:

```
top_folder
    __pycache__
         sourceA.graalpython.pyc
         sourceB.graalpython.pyc
    sourceA.py
    sourceB.py
    sub_folder
        __pycache__
            sourceX.graalpython.pyc
        sourceX.py
```

By default the `__pycache__` directory is created on the same directory level of
a source code file and in this directory all `.pyc` files from the same
directory are stored. This folder may store `.pyc` files created with different
versions of Python (including e.g. CPython), so the user may see files ending in
`*.cpython3-6.pyc` for example.

The current implementation also includes a copy of the original source text in
the `.pyc' file. This is a minor performance optimization so we can create a
Truffle `Source` object with the path to the original source file, but we do not
need to read the original `*.py` file, which speeds up the process obtaining
Truffle tree (we read just one file). The structure of a `.graalpython.pyc` file
is this:

```
MAGIC_NUMBER
source text
binary data - scope tree
binary data - simple syntax tree
```

> **Important**: `.pyc` files are not an effective means to hide Python library
> source code from guest code, since the original source can still be recovered
> and even if the source were omitted, the syntax tree contains enough
> information to decompile into source code easily.

The serialized SST and scope tree is stored in a Python `code` object as well,
as the content of the attribute `co_code` (which contains bytecode on CPython).

For example:
```
>>> def add(x, y):
...   return x+y
...
>>> add.__code__.co_code
b'\x01\x00\x00\x02[]K\xbf\xd1\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00 ...'
```

The creation of `*.pyc` files can be controlled in the same ways as on CPython
(c.f. https://docs.python.org/3/using/cmdline.html):

  * The GraalPython launcher (`graalpython`) reads the `PYTHONDONTWRITEBYTECODE`
    environment variable - if this is set to a non-empty string, Python will not
    try to write `.pyc` files when importing modules.
  * The launcher command line option `-B`, if given, has the same effect as the
    above.
  * Guest language code can change the attribute `dont_write_bytecode` of the
    `sys` built-in module at runtime to change the behavior for subsequent
    imports.
  * The launcher reads the `PYTHONPYCACHEPREFIX` environment variable - if set,
    the `__pycache__` directory will be created at the path pointed to by the
    prefix, and a mirror of the directory structure of the source tree will be
    created on-demand to house the `.pyc` files.
  * Guest language code can change the attribute `pycache_prefix` of the `sys`
    module at runtime to change the location for subsequent imports.

Since the embedder cannot use environment variables or CPython options to
communicate these options to GraalPython, we make these options available as
these language options:

  * `python.DontWriteBytecodeFlag` - equivalent to `-B` or `PYTHONDONTWRITEBYTECODE`
  * `python.PyCachePrefix` - equivalent to `PYTHONPYCACHEPREFIX`

> *Summary*: `.pyc` files are largely managed automatically by the runtime in a
> manner compatible to CPython. Like on CPython there are options to specify
> their location and if they should be written at all and both of these options
> can be changed by guest code.

> **Important**: By default a GraalPython context will not enable writing `.pyc`
> files. The `graalpython` launcher enables it by default, but if this is
> desired in the embedding use case, care should be taken to ensure that the
> `__pycache__` location is properly managed and the files in that location are
> secured against manipulation just like the source `.py` files they were
> derived from.

> **Important**: When upgrading application sources or GraalPython, old `.pyc`
> files must be removed by the embedder as required.

## Security Considerations

The serialization of SST and scope tree is hand written and during
deserialization is not possible to load other classes than SSTNodes. We do not
use Java serialization or other frameworks to serialize Java objects. The main
reason was performance, but this has the effect that no class loading can be
forced by a maliciously crafted `.pyc` file.

All file operations (obtaining the data, timestamps, and writing `pyc` files)
are done through the Truffle FileSystem API. Embedders can modify all of these
operations by means of custom (e.g. read-only) FileSystem implementations. The
embedder can also effectively disable the creation of `.pyc` files by disabling
I/O permissions for GraalPython.

If the `.pyc` files are not readable, their location is not writable, or the
`.pyc` files' serialization data or magic numbers are corrupted in any way, the
deserialization fails and we just parse the `.py` file again. This comes with a
minor performance hit *only* for the parsing of modules, which should not be
significant for most applications (provided the application does actual work
besides loading Python code).
