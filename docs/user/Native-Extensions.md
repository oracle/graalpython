---
layout: docs-experimental
toc_group: python
link_title: Native Extensions Support
permalink: /reference-manual/python/Native-Extensions/
---

# Native Extensions Support

CPython provides a [native extensions API](https://docs.python.org/3/c-api/index.html) for writing Python extensions in C/C++.
GraalPy provides experimental support for this API, which allows many packages like NumPy and PyTorch to work well for many use cases.
The support extends only to the API, not the binary interface (ABI), so extensions built for CPython are not binary compatible with GraalPy.
Packages that use the native API must be built and installed with GraalPy, and the prebuilt wheels for CPython from pypi.org cannot be used.
For best results, it is crucial that you only use the `pip` command that comes preinstalled in GraalPy virtualenvs to install packages.
The version of `pip` shipped with GraalPy applies additional patches to packages upon installation to fix known compatibility issues and it is preconfigured to use an additional repository from graalvm.org where we publish a selection of prebuilt wheels for GraalPy.
Please do not update `pip` or use alternative tools such as `uv`.

## Embedding limitations

Python native extensions run by default as native binaries, with full access to the underlying system.
This has a few implications:

1. Native code is entirely unrestricted and can circumvent any security protections Truffle or the JVM may provide.
2. Native data structures are not subject to the Java GC and the combination of them with Java data structures may lead to increased memory pressure or memory leaks.
3. Native libraries generally cannot be loaded multiple times into the same process, and they may contain global state that cannot be safely reset.

### Full Native Access

The Context API allows to set options such as `allowIO`, `allowHostAccess`, `allowThreads` and more on the created contexts.
To use Python native extensions on GraalPy, the `allowNativeAccess` option must be set to true, but this opens the door to full native access.
This means that while Python code may be denied access to the host file system, thread- or subprocess creation, and more, the native extension is under no such restriction.

### Memory Management

Python C extensions, like the CPython reference implementation, use reference counting for memory management.
This is fundamentally incompatible with JVM GCs.

Java objects may end up being referenced from native data structures which the JVM cannot trace, so to avoid crashing, GraalPy keeps such Java objects strongly referenced.
To avoid memory leaks, GraalPy implements a cycle detector that regularly traces references between Java objects and native objects that have crossed between the two worlds and cleans up strong references that are no longer needed.

On the other side, reference-counted native extension objects may end up being referenced from Java objects, and in this case GraalPy bumps their reference count to make them unreclaimable.
Any such references to native extension objects are registered with a `java.lang.ref.WeakReference` and when the JVM GC has collected the owning Java object, the reference count of the native object is reduced again.

Both of these mechanisms together mean there is additional delay between objects becoming unreachable and their memory being reclaimed when compared to the CPython implementation.
This can manifest in increased memory usage when running C extensions.
You can tweak the Context options `python.BackgroundGCTaskInterval`, `python.BackgroundGCTaskThreshold`, and `BackgroundGCTaskMinimum` to mitigate this.
They control the minimum interval between cycle detections, how much RSS memory must have increased since the last time to trigger the cycle detector, and the absolute minimum RSS under which no cycle detection should be done.
You can also manually trigger the detector with the Python `gc.collect()` call.

### Multi-Context and Native Libraries

To support creating multiple GraalPy contexts that access native modules within the same JVM or Native Image, we need to isolate them from each other.
The current strategy for this is to copy the libraries and modify them such that the dynamic library loader of the operating system will isolate them for us.
To do this, all GraalPy contexts in the same process (not just those in the same engine!) must set the `python.IsolateNativeModules` option to `true`.

For more details on this, see [our implementation details](https://github.com/oracle/graalpython/blob/master/docs/contributor/IMPLEMENTATION_DETAILS.md#c-extension-copying).
