---
layout: docs-experimental
toc_group: python
link_title: FAQ
permalink: /reference-manual/python/FAQ/
---
# Frequently Asked Questions

### Does module/package XYZ work on GraalPy?

It depends.
The first goal with GraalPy was to show that NumPy and related packages can run using the managed GraalVM LLVM runtime.
The GraalVM team continues to improve the number of passing CPython unit tests, and to track the compatibility with popular PyPI packages.
Of the top 500 PyPI packages, about 50% currently pass the majority of their tests on GraalPy.

### Can GraalPy replace my Jython use case?

It can, but there are some caveats, such as Python code subclassing Java classes or use through the `javax.script.ScriptEngine` not being supported.
See the [Jython Migration](Jython.md) guide for details.

### Do I need to compile and run native modules with LLVM to use GraalPy?

No.
Python C extension modules must be built from source to run on GraalPy, but the process is largely automatic when using `pip` and uses the system's standard compilers.
To extend the tooling and sandboxing features of GraalVM to Python C extension modules, they can be run using the GraalVM LLVM runtime.

### Can I use the GraalVM sandboxing features with GraalPy?

Yes, you can.
GraalPy provides two special launchers, `graalpy-lt` and `graalpy-managed`.
The former allows C extension libraries to call out to native system libraries, whereas the latter requires all libraries to be available as bitcode.
A `venv` environment created with these launchers will transparently produce such LLVM bitcode during the build process of native extensions when installed through `pip`.
Extensions installed in this manner work with the GraalVM tools for debugging, CPU and memory sampling, as well as sandboxing.
As an embedder, you can selectively disable system accesses, virtualize the filesystem even for the C extensions, or limit the amount of memory that is allocated.
The price to pay is in increased warm-up and footprint and sometimes lower peak performance, since all code, including the code for native libraries, is subject to JIT compilation.

### Do all the GraalVM polyglot features work with GraalPy?

The team is continuously working to ensure all polyglot features of GraalVM work as a Python user would expect.
There are still many cases where expectations are unclear or where multiple behaviors are imaginable.
The team is actively looking at use cases and continuously evolving GraalPy to provide the most convenient and least surprising behavior.

### What performance can I expect from GraalPy?

For pure Python code, performance after warm-up can be expected to be around 3-4 times faster than CPython 3.10 (or 4-5x faster than Jython).
Native extensions running in the default mode--with full native access--run at about the same speed as their CPython counterparts.
For native extensions running as LLVM bitcode to take advantage of our sandboxing features, GraalPy is usually slower--you should expect to reach at most half of CPython's performance.

### I heard languages with JIT compilers have slow startup. Is that true for GraalPy?

It depends.
When you use [Native Image](https://github.com/oracle/graal/blob/master/docs/reference-manual/native-image/README.md) with Python, or the `graalpy` launcher, startup is competitive with CPython.
Both with a native executable created by Native Image or when running on the JVM, you first need to warm up to reach peak performance.
For small workloads, GraalPy often surpasses CPython performance a few seconds after reaching the code loop.
That being said, the actual startup behavior depends very much on the actual workload.

### Can I share warmed-up code between multiple Python contexts?

Yes, this works, and you will find that starting up multiple contexts in the same engine, and running the same or similar code in them will get increasingly faster, because the compiled code is shared across contexts.
However, the peak performance in this setup is currently lower than in the single context case.
