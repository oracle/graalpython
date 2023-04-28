---
layout: docs-experimental
toc_group: python
link_title: FAQ
permalink: /reference-manual/python/FAQ/
---
# Frequently Asked Questions

### Does module/package XYZ work on GraalPy?

It depends, but is currently unlikely.
The first goal with GraalPy was to show that NumPy and related packages can run using the managed GraalVM LLVM runtime.
The GraalVM team continues to improve the number of passing CPython unit tests, and to track the compatibility with popular PyPI packages.

### Can GraalPy replace my Jython use case?

It can, but there are some caveats, such as Python code subclassing Java classes or use through the `javax.script.ScriptEngine` not being supported.
See the [Jython Migration](Jython.md) guide for details.

### Do I need to compile and run native modules as LLVM bitcode to use on GraalPy?

On GraalVM, Python C extension modules run using the GraalVM LLVM runtime.
To use such modules, you cannot use binary distributions, but instead you must install them from source using GraalPy, which will transparently produce LLVM bitcode during the build process.
However, many of the core features of Python (including, for example, large parts of the `os` API) are implemented in pure Java and many standard library modules and packages work without running any LLVM bitcode.
So even though GraalPy depends on the GraalVM LLVM runtime, for many use cases you can disallow native modules entirely.

### Can I use the GraalVM sandboxing features with GraalPy?

Yes, you can.
As an embedder, you can selectively disable features.
For example, you can disable native code execution or filesystem access.
Also, GraalVM's managed execution mode for LLVM fully works for running extensions such as NumPy in a safer manner.

### Do all the GraalVM polyglot features work with GraalPy?

The team is continuously working to ensure all polyglot features of GraalVM work as a Python user would expect.
There are still many cases where expectations are unclear or where multiple behaviors are imaginable.
The team is actively looking at use cases and continuously evolving GraalPy to provide the most
convenient and least surprising behavior.

### What performance can I expect from GraalPy?

For the pure Python code, performance after warm-up can be expected to be around 5-6 times faster than CPython 3.8 (or 6-7x faster than Jython).
For native extensions running as LLVM bitcode, CPython is currently slower--you can expect to see between 0.1x and 0.5x performance.

### I heard languages with JIT compilers have slow startup. Is that true for GraalPy?

It depends.
When you use [Native Image](https://github.com/oracle/graal/blob/master/docs/reference-manual/native-image/README.md) with Python, or the `graalpy` launcher, startup is competitive with CPython.
In any case, both with a native executable created by Native Image or when running on the JVM, you first need to warm up to reach peak performance. This is a complicated story in itself, but, in general, it can take a while (a minute or two) after you have reached and are running your core workload.

### Can I share warmed-up code between multiple Python contexts?

Yes, this works, and you will find that starting up multiple contexts in the same engine, and running the same or similar code in them will get increasingly faster, because the compiled code is shared across contexts.
However, the peak performance in this setup is currently lower than in the single context case.
