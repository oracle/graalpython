---
layout: docs-experimental
toc_group: python
link_title: Native Extensions Support
permalink: /reference-manual/python/Native-Extensions/
---

# Native Extensions Support

CPython provides a [native extensions API](https://docs.python.org/3/c-api/index.html){:target="_blank"} for writing Python extensions in C/C++.
GraalPy provides experimental support for this API, which allows many packages like NumPy and PyTorch to work well for many use cases.
The support extends only to the API, not the binary interface (ABI), so extensions built for CPython are not binary compatible with GraalPy.
Packages that use the native API must be built and installed with GraalPy, and the prebuilt wheels for CPython from pypi.org cannot be used.
For best results, it is crucial that you only use the `pip` command that comes preinstalled in GraalPy virtualenvs to install packages.
The version of `pip` shipped with GraalPy applies additional patches to packages upon installation to fix known compatibility issues and it is preconfigured to use an additional repository from graalvm.org where we publish a selection of prebuilt wheels for GraalPy.
Please do not update `pip` or use alternative tools such as `uv`.

## Embedding limitations

Python native extensions run by default as native binaries, with full access to the underlying system.
Native code is not sandboxed and can circumvent any protections Truffle or the JVM may provide, up to and including aborting the entire process.
Native data structures are not subject to the Java GC and the combination of them with Java data structures may lead to memory leaks.
Native libraries generally cannot be loaded multiple times into the same process, and they may contain global state that cannot be safely reset.
Thus, it is not possible to create multiple GraalPy contexts that access native modules within the same JVM.
This includes the case when you create a context, close it, and then create another context.
The second context will not be able to access native extensions.
