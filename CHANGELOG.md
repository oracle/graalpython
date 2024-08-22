# Python Changelog

This changelog summarizes major changes between GraalVM versions of the Python
language runtime. The main focus is on user-observable behavior of the engine.

## Version 24.2.0
* Updated developer metadata of Maven artifacts.
* Added gradle plugin for polyglot embedding of Python packages into Java.

## Version 24.1.0
* GraalPy is now considered stable for pure Python workloads. While many workloads involving native extension modules work, we continue to consider them experimental. You can use the command-line option `--python.WarnExperimentalFeatures` to enable warnings for such modules at runtime. In Java embeddings the warnings are enabled by default and you can suppress them by setting the context option 'python.WarnExperimentalFeatures' to 'false'.
* Update to Python 3.11.7.
* We now provide intrinsified `_pickle` module also in the community version.
* `polyglot.eval` now raises more meaningful exceptions. Unavaliable languages raise `ValueError`. Exceptions from the polyglot language are raised directly as interop objects (typed as `polyglot.ForeignException`). The shortcut for executing python files without specifying language has been removed, use regular `eval` for executing Python code.
* In Jython emulation mode we now magically fall back to calling Java getters or setters when using Python attribute access for non-visible properties. This can help migrating away from Jython if you relied on this behavior. 
* The option `python.EmulateJython` to enable Jython emulation is now marked as stable, and can thus be relied upon in production.
* Fixed parsing of pyvenv.cfg according to PEP 405, which is required to use [uv](https://github.com/astral-sh/uv?tab=readme-ov-file#uv) generated venvs with GraalPy.
* Use https://www.graalvm.org/python/wheels/ as the default value for the `--extra-index-url` pip option. This will make it easy for users to install GraalPy binary wheels in the future.
* GraalPy can now be installed on Windows with [pyenv-win](https://github.com/pyenv-win/pyenv-win). This makes it as easy to get the latest GraalPy release on Windows as it already is on macOS and Linux using [pyenv](https://github.com/pyenv/pyenv).

## Version 24.0.0
* We now provide a collection of recipes in the form of GitHub Actions to build popular native extensions on GraalPy. These provide a reproducible way for the community to build native extensions for GraalPy with the correct dependencies. See scripts/wheelbuilder/README.md for details.
* Foreign big integers are now supported and work with all `Numeric` operators.
* Interop `null` values are now treated as *identical*, not only *equal* to Python's `None`. This means that something like `java.type("java.lang.Object[]")(1)[0] is None` will now return `True`.
* Update to Python 3.10.13. This inlines the security and bugfixes from 3.10.8 to 3.10.13.
* Include the GraalPy C API revision in the ABI tag for wheels. This avoids accidentally using incompatible binaries when using snapshots.
* Honor the `allowHostSocketAccess` configuration in embeddings. This means sockets can now be disabled independently of other IO.
* Avoid eager initialization of the Sulong LLVM runtime. This reduces footprint in the default configuration where C extensions are run natively.
* Expand support for the following modules: llvmlite, pydantic-core, catboost, ray, tensorflow, tensorflow-io, readme-renderer, safetensors, keras, pybind11, protbuf, grpcio, PyO3, cryptography, bcrypt, cramjam, libcst, orjson, rpds_py.
* Support installing some packages with native extensions on Windows. Simple packages like `ujson` or `kiwisolver` will now work when installed from a venv inside a Visual Studio command prompt.
* Raise `KeyboardInterrupt` exception when the process is interrupted. Enabled only when using the launcher.
* Add option `python.InitialLocale` to change the default locale. If not set, then Java Locale#getDefault is used.
* `multiprocessing` module now uses the `spawn` method (creates new processes) by default. The formerly default method that uses threads and multiple Truffle contexts can be selected using `multiprocessing.set_start_method('graalpy')`.
* `polyglot` module: add API to redefine Truffle interop messages for external / user defined types. For more details see [The Truffle Interoperability Extension API](docs/user/Interoperability.md).
*Adding integration with jBang (https://www.jbang.dev/)
** running example via `jbang hello@oracle/graalpython` or `jbang hello@oracle/graalpython "print(1*4)"`
** creating new script via: `jbang init --template=graalpy@oracle/graalpython myscript.java`
** creating new script with local maven repo for testing: `jbang init --template=graalpy_local_repo@oracle/graalpython -Dpath_to_local_repo=/absolute/path/to/local/maven/repository myscript.java'

## Version 23.1.0
* GraalPy distributions (previously known as GraalPy Enterprise) are now available under the [GFTC license](https://www.oracle.com/downloads/licenses/graal-free-license.html). The community builds published on Github have been renamed to `graalpy-community-<version>-<os>-<arch>.tar.gz`.
* Add support for the sqlite3 module. This allows many packages like `coverage` or `Flask-SQLAlchemy` to work on top of this embedded database.
* Provide Windows distributions of GraalPy. This is the first preview of Windows support for GraalPy, and there are limitations, but pure Python packages like Pygal can be installed with `python -m pip --no-cache install pygal`.
* The GraalPy standalone tool was updated. You can now build single-file executable Python binaries for Linux, Windows, and macOS. The tool can also generate a skeleton Maven project that sets up a polyglot embedding of Python packages into Java.

## Version 23.0.0
* Update `numpy` and `pandas` versions, add support for `scipy` and `scikit_learn` with `ginstall`. This automatically applies some fixes that make it possible to use these new versions with GraalPy.
* Update language version and standard library to 3.10.8, making it compatible with more recent modules and packages.
* Include GraalVM version in `sys.implementation.cache_tag` and `sysconfig`'s `EXT_SUFFIX` and `SOABI`, to ensure proper detection of compatible bytecode files and native extensions.
* Update the builtin venv module to create virtual environments with symlinks instead of generated shell scripts that delegated to the base GraalPy.
* Add GraalPy plugin for [Virtualenv](https://virtualenv.pypa.io) as a builtin module, such that creating virtual environments with `virtualenv` on GraalPy works out of the box.
* Allow excluding the Java-based SSL module that uses classes from `java.security.*`, `org.bouncycastle.*`, `javax.net.ssl.*` and any related classes from the native image by passing `-Dpython.java.ssl=false` to the native image build Java arguments.
* Allow excluding the Java UnixSystem classes from `com.sun.security.auth.*` by passing `-Dpython.java.auth=false` to the native image build Java arguments. This makes the POSIX calls `getpwuid`, `getpwname`, and `getuid` return less precise results in the Java-based POSIX backend.
* Allow excluding the use of `sun.misc.Signal` and `sun.misc.SignalHandler` from GraalPy by passing `-Dpython.java.signals=false` to the native image build Java arguments. This removes the `signal` module from the binary.
* We now run an publish benchmark results from the community's [pyperformance](https://pyperformance.readthedocs.io) benchmark suite on our [website](http://graalvm.org/python). This makes it easier to compare and reproduce our results.
* Allow building and running basic workloads on Windows. This enables Windows users to build and use GraalPy, especially for embedding into Java.
* Implement complete support for PEP 622 pattern matching. All features of Python's structural pattern matching should now work.
* Update the distribution layout of GraalPy to match CPython's. This reduces the number of patches we need for various build systems to discover GraalPy's library locations.
* Add an option for embedders of GraalPy to poll asynchronous actions explicitly. This prevents GraalPy from creating system threads for collecting Python-level references and instead provides a callback that the embedder calls to do this work at regular intervals. See docs/user/PythonNativeImages.md for details.
* Remove the intrinsified `zipimport` module in favor of the pure Python version. This fixes subtle incompatibilities with CPython when handling ZIP files.
* Use the JDK's `MessageDigest` for hashing instead of pure Python implementations. This improves performance and compatibility in Java embeddings.
* Add initial support for `asyncio`. While not complete, this already allows some async libraries like `aiofiles` to work.
* Add a new implementation of our Python C API interface that uses fully native execution by default. This improves performance and compatibility with some extensions that spend a lot of time in native code, but can have negative effects in workloads that cross often from Python to native code and back. There are new options to control how extensions are built and run: `python.NativeModules` and `python.UseSystemToolchain`. The new default is to use the host system's toolchain for building extensions rather than the LLVM toolchain that ships with GraalVM, and to run all modules natively.

## Version 22.3.0
* Rename GraalPython to GraalPy. This change also updates the launchers we ship to include symlinks from `python` and `python3` to `graalpy` for better integration with other tools.
* Switched to a new interpreter backend based on interpreting bytecode. This change brings better startup performance and memory footprint while retaining good JIT-compiled performance.
* Switched to a new parser generated from CPython's new PEG grammar definition. It brings better compatibility and enables us to implement the `ast` module.
* Added support for tracing API (`sys.settrace`), which makes `pdb` and related tools work on GraalPy.
* Added support for profiling API (`sys.setprofile`), which makes the `profile` package work.
* Updated our pip support to automatically choose the best version for known packages. You can use `pip install pandas`, and pip will select the versions of pandas and numpy that we test in the GraalPy CI.
* Added support for [Flask](https://pypi.org/project/Flask/).
* Implement PEP 405 for full support of virtual environments. This fixes issues with the virtualenv package and tox that are used to in PyCharm or in many projects' CI jobs.

## Version 22.2.0
* Updated to HPy version 0.0.4, which adds support for the finished HPy port of Kiwi, and the in-progress ports of Matplotlib and NumPy.
* Added support for aarch64 on both macOS and Linux.
* Added an experimental bytecode interpreter for faster startup and better interpreter performance. Using either the previous AST interpreter or the new bytecode interpreter can be switched using `--python.EnableBytecodeInterpreter`.

## Version 22.1.0
* String conversion (`__str__`) now calls `toString` for Java objects and `toDisplayString` interop message for foreign objects.
* Improved compatibility with PyPI packages `lxml`, `pytz`, `Pillow`, `urllib3`, `setuptools`, `pytest`, `twine`, `jinja2`, and `six`
* Introduced dependency on bouncycastle
* Added support for more private key formats (PKCS#1, password protected) in ssl module
* Added support for module freezing, which makes start to the Python REPL 30% faster and with 40% less memory usage.

## Version 22.0.0
* Added support for `pyexpat` module.
* Added partial support for `PYTHONHASHSEED` environment variable (also available via `HashSeed` context option), currently only affecting hashing in `pyexpat` module.
* Implement `_csv` module.
* Improved compatibility with PyPI packages `wheel` and `click`

## Version 21.3.0

* Remove `PYPY_VERSION` from our C extension emulation, enabling PyGame 2.0 and other extensions to work out of the box.
* Intrinsify and optimize more of the core language for better startup and reduced footprint.
* Implement a new binary compatible backend for HPy 0.0.3, which allows binary HPy wheels to run unmodified on CPython and GraalPy
* Support the `multiprocessing` module via in-process nested contexts, allowing execution on multiple cores within the same process using the Python multiprocessing API
* Add support for the `ctypes` module, enabling more native extensions to run that use the ctypes API
* Fix multiple REPL issues reported on Github, you can now paste blocks of code and use the numpad in the REPL.
* Make our marshal format compatible with CPython, so binary data can now be exchanged between CPython and GraalPy processes.
* Make most `socket` module tests pass in native mode by using a native extension, allowing usage of all POSIX socket APIs where before only those supported on Java could be used.
* Various compatibility fixes to make the `psutil` package work.

## Version 21.2.0

* Support the `dict` type properly in interop using the new hash interop messages.
* Implement `_pickle` as a faster version than the pure Python version for GraalVM Enterprise Edition.
* Support the newer multi-phase C extension module initialization.
* Make many more tests pass: `io`, `crypt`, more functions in `socket`, `OrderedDict`, `time`,
* Improve performance especially during warmup and in shared engine configurations by adding fast paths, intrinsifying functions, and adding optimized representations for common data structures
* Update the supported HPy version to 0.0.2
* Use the new Truffle safepoint mechanism for more efficient GIL releases, signal handlers, and weakref callbacks
* Initial support for the `psutil` and `PyGame` packages
* GraalPy not longer unconditionally creates `__pycache__` if the file name "sitecustomize.py" exists in the current working directory

## Version 21.1.0

* Support multi-threading with a global interpreter lock by default.
* Added SSL/TLS support (the `ssl` module)
* Added subclassing of Java classes in JVM mode
* Support iterating over Python objects from Java and other languages as well as iterating over foreign objects in Python
* Support catching exceptions from other languages or Java with catch-all except blocks
* Support isinstance and issubclass with instances and classes of other languages
* Use native posix functions in the GraalPy Launcher (see [Operating System Interfaces](docs/user/OsInterface.md) for details).

## Version 21.0.0

* Implement name mangling for private attributes
* Correctly raise an AttributeError when a class defines slots, but not dict
* Fix infinite continuation prompt in REPL when pasting snippets
* Add jarray module for compatibility with Jython
* Fix multiple memory leaks and crashes when running NumPy in a shared engine
* Improved support for Pandas
* Initial support for Matplotlib
* Many fixes to pass the unittests of standard library types and modules:
  abc, array, builtin, bzip2, decimal, descriptors, difflib, enum, fractions,
  gzip, memoryview, metaclass, pickle, platform, print, reprlib, statistics,
  strftime, strtod, sysconfig, userdict, userlist, userstring, zipfile,
  zipfile64, zlib
* Improve performance in multiple areas:
  array, memoryview, unzipping packages, dictionaries with dynamic string keys,
  string slicing

## Version 20.3.0

* Fix multiple memory leaks when running Python code in a shared engine.
* Update language support target and standard library to 3.8.5
* Hide internal catching frames from tracebacks
* Update HPy support to the latest version with support for piconumpy
* Many fixes to pass the unittests of standard library types and modules:
  complex, bytes, bytearray, subclassing of special descriptors, type layouts,
  float, generators, modules, argument passing corner cases, string literals and
  encodings, import and importlib, decimal, glob, the builtin module, json,
  math, operator, numeric tower, sys, warnings, random, f-strings, struct,
  itertools

## Version 20.2.0

* Escaping Unicode characters using the character names in strings like
  "\N{GREEK CAPITAL LETTER DELTA}".
* When a `*.py` file is imported, `*.pyc` file is created. It contains binary data to speed up parsing.
* Adding option `PyCachePrefix`, which is equivalent to PYTHONPYCACHEPREFIX environment variable, which is also accepted now. 
* Adding optin `DontWriteBytecodeFlag`. Equivalent to the Python -B flag. Don't write bytecode files.
* Command option -B works
* Implement better reference counting for native extensions to fix memory leaks
* Fix a warning in pandas about size of datetime objects
* Make many more CPython unittests pass for core types
* Support parse requests with arguments for embedding Python to light up GraalVM Insight support
* Support basic tox usage when forcing virtualenv to use venv
* No longer support iterables as arrays in interop
* Add initial support for HPy native extensions
* Support magic encoding comments in Python files
* Improve chaining of exception tracebacks - the tracebacks should now be much closer to CPython and more helpful

## Version 20.1.0

* Update language support target and standard library to 3.8.2
* Improve performance of tuples with primitive elements
* Improve performance of using Python sequences from other GraalVM languages
* Improve performance of dictionaries and sets
* Improve performance of allocations for list comprehensions with range iterators
* Support `cProfile` and `trace` modules through the GraalVM CPU sampler and coverage, respectively
* Support NumPy on macOS
* Support setuptools-scm and pytz.timezone
* Support new syntax for iterable unpacking from yield and return statements
* Fix issues with inspection and printing of non-Python numbers in the Chrome debugger
* Fix issues with AST sharing across different contexts, if these context run concurrently on multiple threads
* Fix code serialization and deserialization with pickle
* Fix DirEntry.stat
* Fix passing non-ASCII strings to `gethostbyname`
* Fix `help(numpy)` to work again in the interactive REPL
* Fix multi-line continuation in the REPL for opening parens
* Fix `select.select` for pipes
* Polyglot: Rethrow AttributeError as UnknownIdentifierException in invokeMember
* Jython mode: treat Java `null` as identical to Python `None` when comparing with the `is` operator
* Jython mode: `isinstance` now works with Java classes and objects
* Improve errno handling in `posix` module
* Move all GraalPy specific functions on `sys` or `builtins` to the `__graalpython__` module

## Version 20.0.0

* Jython Compatiblity: Implement `from JavaType import *` to import all static members of a Java class
* Jython Compatiblity: Implement importing Python code from inside JAR files by adding `path/to/jarfile.jar!path/inside/jar` to `sys.path`
* Added support for date and time interop.
* Added support for setting the time zone via `Context.Builder.timeZone`.
* PEP 570 - Python Positional-Only Parameters implemented

## Version 19.3.0

* Implement `gc.{enable,disable,isenabled}` as stubs
* Implement `charmap_build` function
* Implement `hexversion` in sys module
* Implement `_lzma` module
* Implement enough of `socket.socket` to run `graalpy -m http.server` and download non-encrypted http resources
* Fix printing of Pandas data frames
* Fix a bug in `bytes.startswith` for tuple arguments
* Fix destructuring assignments of arbitrary iterators
* Fix `dict.__contains__` for dictionaries with only `str` keys for subclasses of `str`
* Support NumPy 1.16.4 and Pandas 0.25.0
* Support `timeit` module
* Support basic usage of `pytest`
* Improve performance across many Python and C extension benchmarks
* Improve performance of our parser
* Improve performance of catching exceptions when the exception does not leave the handler block and the traceback is not accessed
* Improve performance of Java interop when Python objects are accessed from Java
* Add a new `--python.EmulateJython` flag to support importing Java classes using normal Python import syntax and to catch Java exceptions from Python code
* Update standard library to Python 3.7.4
* Initial implementatin of PEP 498 -- Literal String Interpolation

## Version 19.2.0

* Implement PyStructSequence_* C API functions
* Implement `_functools.partial` as a class instead of a function
* Implement `type.__base__`
* Implement reading C API type attributes `nb_inplace_add`, `nb_remainder`, `nb_subtract`, and `nb_floor_divide` for builtin types
* Implement C API functions `_PyObject_CallFunction_SizeT`, `PyEval_InitThreads`, and `PyEval_ThreadsInitialized`
* Implement writing to a function's `__dict__` field
* Implement the C API thread state fields `overflowed` and `recursion_depth`
* Fix printing of errors in the REPL
* Fix printing full paths in traceback
* Support the C API varargs functions with arbitrary numbers of arguments instead of imposing an upper limit
* Improve performance of attribute reads and reading closure variables

## Version 19.1.0

* Add `java.add_to_classpath` API to dynamically extend the host class path
* Allow write access to main module bindings for embedder
* Swap arguments for `polyglot.export_value` to use the more natural (name, value) order and deprecate the previous argument order.
* Update Python standard library files to Python 3.7.3
* Improve performance of exceptions that do not escape
* Fix str(None) to print "None" instead of an empty string
* Fix error messages on polyglot objects to not leak implementation class names of those objects
* Fix erroneously frozen package paths in pre-initialized python modules
* Fix caching of core sources in a native image with a preinitialized context for pre-built images and libpolyglot fast startup
* Implement pwd.getpwuid
* Implement os.exec, os.execv, and os.execl
* Add some missing C API headers needed for tensorflow compilation

## Version 19.0.0

* Fix an issue preventing use of encodings in the installable binary
* Fix return value of process when `os.exit` is called with a boolean
* Fix interpretation of foreign objects to prefer interpreting them as integer over double
* Fix performance regression when repeatedly creating a new function in a loop

## Version 1.0.0 RC16

* No user-facing changes

## Version 1.0.0 RC15

* Implement PEP 487 `__init_subclass__`
* Implement PEP 560 `__class_getitem__` and `__mro_entries__`
* Migrate to Truffle libraries for interop
* Support the buffer protocol for mmap
* Support importing java classes using normal Python import syntax
* Improve performance of literal dictionary creation when the first but not all keys are strings
* Improve performance of getting the length of a string
* Improve performance of accessing defaults, keyword-defaults, and code of a function
* Fix getting file separator from the Truffle filesystem rather than the operating system
* Fix constructing and calling methods with non-function callables
* Fix execution of subprocesses with non-default python homes on JVM

## Version 1.0.0 RC14

* Mark a subset of the Graal Python launcher options as "stable". All other options are subject to change and need to be unlocked explicitly on the commandline.
* Automatically install pip when creating a venv. The socket and ssl libraries are still not functional, so pip can only install from local sources or wheels.
* Update the standard library to Python 3.7.0 from 3.6.5.
* Support the `-I` flag to ignore the user environment and not add the working directory to `sys.path`
* Fix an error preventing usage of the memtracer tool. If an object raised an exception in it's `__repr__` method, it would abort the execution.
* Fix issues around not being able to modify function defaults, keyword defaults, or re-defining a function with a different closure.
* Fix continuation prompt in the interactive Python shell when an incomplete statement was typed. Before it raised and ignored a SyntaxError.
* Fix frame restarting of Python functions in the Chrome debugger. Before, functions with closures would have their cells accidentally cleared.

## Version 1.0.0 RC13

* Support marshal.dumps and marshal.loads for code objects and some other built-in objects
* Fix installation of NumPy in a venv
* Initial support for module mmap
* Support debugging with workspace files in the Chrome debugger
* Support the PEP 553 breakpoint() message
* Support running weak reference callbacks and signals on the main thread

## Version 1.0.0 RC12

* Support the `__class__` variable in the class scope
* Support module-level docstrings
* Initial support for the `venv` standard-library tool
* Initial support for the built-in `_bz2` module
* Initial support for the `pandas` package
* Initial support for OSError subclasses based on the `errno` of the exception
* Fix bytearray inplace add to return the same object
* Fix access to standard Python methods (`__repr__`, `__str__`, `__len__` and the like) for foreign objects

## Version 1.0.0 RC11

* Support running setuptools to build and install various packages
* Support running a source release version of NumPy out of the box
* Improve performance of member access to C API objects
* Improve performance of binary operations on C API objects
* Add support for `yield from`
* Support assignment to `object.__dict__` and ensure that managed subclasses of native types also have a `__dict__`
* Fix `[]` access with non-integer keys for array-like foreign objects
* Fix various performance regressions introduced in the last RC
* Implement more built-in methods on the `time` module
* Python no longer exposes internal languages through `polyglot.eval`
* Improve performance of `os.scandir` and functions that build on it (such as `glob`)
* More correct implementation of standard streams, including buffering
* Properly support the `-m` switch to run modules
* Support the standard `zipfile` module
* Add the built-in `_cvs` module
* Add support for `__slots__`
* Allow arbitrary callable objects in methods, not only functions
* Report that we have a TTY console if we are launched on a Terminal through our launcher
* Add the `ginstall` custom module to install known packages such as NumPy and setuptools

## Version 1.0.0 RC10

* Improve performance of C API upcalls
* Improve performance of classmethods, staticmethods, `globals()`, and `locals()`
* Improve performance of various string and bytes operations
* Initial support for the `_thread` builtin module (actual multi-threading is still disabled, the API defaults to a dummy implementation)
* Implement the `zipimporter` module
* Support assignment to `object.__class__`
* Use the new Truffle filesystem API to get/set the current working directory
* Attempt our best to report side-effects in KEY_INFO
* The KEYS message now responds with attributes and methods, never dict keys
* Support the `input` builtin
* Add DEBUG launcher options for performance debugging
* Ensure context isolation for file descriptors and child PIDs
* Fix passing custom locals and globals through `exec` and `eval`
* Fixes to builtin `help`

## Version 1.0.0 RC9

* Support `help` in the builtin Python shell
* Add `readline` to enable history and autocompletion in the Python shell
* Add support for the -q, -E, -s, and -S Python launcher flags
* Improve display of foreign array-like objects
* Improve support for string and bytes regular expressions using our TRegex engine
* Support loading site-packages installed with easy_install
* Initial support for the `binascii` module

## Version 1.0.0 RC8

* Report allocations when the `--memtracer` option is used
* Initial support for `pickle` module

## Version 1.0.0 RC7

* Enhance the `java` interop builtin module with introspection utility methods

## Version 1.0.0 RC6

* Support regular expression patterns built from bytes by using CPython's sre module as a fallback engine to our own
* Support LLVM 5+ for C extension modules
* Introduce native sequence storage so that e.g. Python bytes exposed to C can be mutated
* Introduce lazy string concatenation to significantly speed up benchmarks where strings are concatenated repeatedly
* C-API improvements to support more scikit-learn code
* Fix our distinction between builtin functions, functions, and methods to make the classes for builtin functions equivalent to CPython
* Improve set, frozenset, and dict support
* Attach Python exceptions as cause to ImportErrors raised for C extension modules
* Update standard library to CPython 3.6.5
* Support more code object attributes
* Support constant type ids for objects that are interned on CPython
* Add collections.deque
* Document how to contribute
* Improve efficiency of generators
* Enable re-use of ASTs in multiple Contexts in the same Engine

## Version 1.0.0 RC5

* Generator expressions now properly evaluate their first iterator in the definition scope at definition time
* Fixes for embedders to ensure top scopes are stable and local scopes always contain TruffleObjects
* C-API improvements to support simple Cython modules
* Support recognition of Python source files with the polyglot launcher

## Version 1.0.0 RC4

* No changes

## Version 1.0.0 RC3

* Support for more String encodings
* Implement buffered I/O
* Remove our random module substitute and use the standard library implementation
* Fix a potential thread-safety problem with cached parse trees when multiple Python contexts are used from multiple threads
* Complete support for math module builtins
* C-API improvements to run simple scikit-learn and NumPy examples
* Support the buffer protocol to wrap arbitrary Python sequences with no copy into NumPy arrays

## Version 1.0.0 RC2

* Updates to the polyglot and embedding APIs
* Many additions to the language core implementation
* Performance improvements to the parser
* C-API improvements to compile and run simple C extensions
* Support breakpoints on caught and uncaught exceptions in debugger

## Version 1.0.0 RC1

* LICENSE set to The Universal Permissive License (UPL), Version 1.0.
