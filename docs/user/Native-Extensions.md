---
layout: docs-experimental
toc_group: python
link_title: Native Extensions Support
permalink: /reference-manual/python/Native-Extensions/
---

# Native Extensions Support

Python provides a native extensions API for writing Python extensions in C/C++. GraalPy's support for native extensions
is currently considered experimental, although many packages like NumPy and PyTorch work well for many use cases.
Native extensions built for CPython are not binary compatible with GraalPy, therefore it is not possible to use packages
installed with CPython from GraalPy. Packages have to be installed with GraalPy. Likewise, prebuilt wheels for CPython
from pypi.org cannot be used with GraalPy.
The version of *pip* shipped with GraalPy applies additional patches to packages upon installation to make native
extensions work, it is therefore crucial that you only use the *pip* preinstalled in GraalPy virtualenvs to install
packages. Don't update *pip* or use alternative tools such as *uv*. GraalPy's *pip* is also preconfigured to use an
extra repository from graalvm.org where we plan to publish prebuilt wheels for GraalPy for selected commonly used
packages.

## Embedding limitations

Python native extensions run by default as native binaries, with full access to the underlying system.
Native code is not sandboxed and can circumvent any protections Truffle or the JVM may provide, up to and including
aborting the process.
Native data structures are not subject to the Java GC and the combination of them with Java data structures may lead to
memory leaks.
Native libraries generally cannot be loaded multiple times into the same process, and they may contain global state that
cannot be safely reset. Thus, it's not possible to create multiple GraalPy contexts that access native modules within
the same JVM. This includes the case when you create a context, close it and then create another context. The second
context will not be able to access native extensions.
