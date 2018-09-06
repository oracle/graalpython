# Python Changelog

This changelog summarizes major changes between GraalVM versions of the Python
language runtime. The main focus is on user-observable behavior of the engine.

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
