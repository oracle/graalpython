# Frequently Asked Questions

### Does module/package XYZ work on GraalVM's Python implementation?

It depends, but is currently unlikely. The first goal with the GraalVM Python implementation was to
show that NumPy and related packages can run using the managed GraalVM LLVM
implementation. Now the GraalVM team continue to improve the
number of passing CPython unittests and to track the compatibility with popular PyPI packages.

### Can the GraalVM Python implementation replace my Jython use case?

It can, but there are some caveats, like Python code subclassing Java
classes or use through the `javax.script.ScriptEngine` not being
supported. See the [Jython Compatibility](Jython.md) guide for details.

### Do I need to compile and run native modules as LLVM bitcode to use GraalVM's Python implementation?

If you want to run C extensions or use certain built-in features, yes, you need
to build the module with GraalVM's Python and then it will run using the GraalVM
LLVM runtime. However, many of the core features of Python (including e.g.,
large parts of the `os` API) are implemented in pure Java and many standard
library modules and packages work without running any LLVM bitcode. So even
though GraalVM's Python depends on the GraalVM LLVM runtime, for many use cases
you can disallow native modules entirely.

### Can I use GraalVM sandboxing features with GraalVM's Python implementation?

Yes, you can. As an embedder, you can selectively disable features. For example, you
can disable native code execution or filesystem access. If you are a user of
Oracle GraalVM Enterprise Edition, you will also find that the managed execution mode
for LLVM fully works for running extensions such as NumPy in a safer manner.

### Do all the GraalVM polyglot features work?

The team is continuously working to ensure all polyglot features of GraalVM work as a
Python user would expect. There are still many cases where expectations are
unclear or where multiple behaviours are imaginable. The team is actively looking at
use cases and are continuously evolving the Python implementation to provide the most
convenient and least surprising behaviour.

### What is the performance I can expect from GraalVM's Python implementation?

For pure Python code, performance after warm-up can be expected to be around 5-6
times faster than CPython 3.8 (or 6-7x faster than Jython). For native
extensions running as LLVM bitcode, CPython is currently slower -- you
can expect to see between 0.1x and 0.5x performance.

### I heard languages with JIT compilers have slow startup. Is that true for GraalVM's Python?

It depends. When you use the [GraalVM Native Image](https://www.graalvm.org/reference-manual/native-image/) feature with Python or
use the `graalpython` launcher of GraalVM, its startup is competitive with
CPython. In any case, both with Native Image or when running on JVM you first
need to warm up to reach peak performance. This is a complicated story in
itself, but in general it can take a while (a minute or two) after you have
reached and are running your core workload.

### Can I share warmed up code between multiple Python contexts?

Yes, this works, and you will find that starting up multiple contexts in the
same engine and running the same or similar code in them will get increasingly
faster, because the compiled code is shared across contexts. However, the peak
performance in this setup is currently lower than in the single context case.
