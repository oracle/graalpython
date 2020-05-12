# Frequently Asked Questions

### Does module/package XYZ work on GraalPython?

It depends, but is currently unlikely. Our first goal with GraalPython was to
show that we can run NumPy and related packages using the managed GraalVM LLVM
implementation. Now that we have done so we are hard at work to improve the
number of passing CPython unittests. We are also beginning to track our
compatibility with popular PyPI packages and expect to increase our coverage
there soon.

### Can GraalPython replace my Jython use case?

We hope it can, but there are some caveats, like Python code subclassing Java
classes or use through the `javax.script.ScriptEngine` not being
supported. Please see our [migration document](./JYTHON) for details.

### Do I need to compile and run native modules as LLVM bitcode to use GraalPython?

If you want to run C extensions or use certain built-in features, yes, you need
to build the module with GraalPython and then it will run using on the GraalVM
LLVM runtime. However, many of the core features of Python, including large
parts of the `os` API, are implemented in Java so many standard library modules
and packages work without requiring running LLVM bitcode.

### Can I use GraalVM sandboxing features with GraalPython?

Yes! As an embedder, you can selectively disable features. As an example, you
can disable native code execution or filesystem access. If you are a user of
GraalVM Enterprise Edition, you will also find that the managed execution mode
for LLVM fully works for running extensions such as NumPy in a safer manner.

### Do all the GraalVM polyglot features work?

We are doing our best to ensure the polyglot features of GraalVM work as a
Python user would expect. There are still many cases where expectations are
unclear or where multiple behaviors are imaginable. We are actively looking at
use cases and are continuously evolving the implementation to provide the most
convenient and least surprising behavior.

### What is the performance I can expect from GraalPython?

For pure Python code, performance after warm-up can be expected to be around 5-6
times faster than CPython 3.8 (or 6-7x faster than Jython). For native
extensions running as LLVM bitcode, we are currently slower than CPython - you
can expect to see between 0.1x and 0.5x performance.

### I heard languages with JIT compilers have slow startup. Is that true for GraalPython?

It depends. When you use the GraalVM native image feature with GraalPython or
use the GraalPython launcher in GraalVM its startup is competitive with
CPython. In any case, both with native image or when running on JVM we first
need to warm up to reach peak performance. This is a complicated story in
itself, but in general it can take a while (a minute or two) after you have
reached and are running your core workload. We are continuously working on
improving this.

### Can I share warmed up code between multiple Python contexts?

Yes, this works, and you will find that starting up multiple contexts in the
same engine and running the same or similar code in them will get increasingly
faster, because the compiled code is shared across contexts. However, the peak
performance in this setup is currently lower than in the single context case.
