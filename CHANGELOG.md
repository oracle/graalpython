# Python Changelog

This changelog summarizes major changes between GraalVM versions of the Python
language runtime. The main focus is on user-observable behavior of the engine.

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
* Support creating ZIP files through the standard `zipfile` module

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
