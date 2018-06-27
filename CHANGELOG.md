# Python Changelog

This changelog summarizes major changes between GraalVM versions of the Python
language runtime. The main focus is on user-observable behavior of the engine.

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
