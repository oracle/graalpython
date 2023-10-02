# Implementation Details

## Python Global Thread State

In CPython, each stack frame is allocated on the heap, and there's a global
thread state holding on to the chain of currently handled exceptions (e.g. if
you're nested inside `except:` blocks) as well as the currently flying exception
(e.g. we're just unwinding the stack).

In PyPy, this is done via their virtualizable frames and a global reference to
the current top frame. Each frame also has a "virtual reference" to its parent
frame, so code can just "force" these references to make the stack reachable if
necessary.

Unfortunately, the elegant solution of "virtual references" doesn't work for us,
mostly because we're not a tracing JIT: we want the reference to be "virtual"
even when there are multiple compilation units. With PyPy's solution, this also
isn't the case, but it only hurts them for nested loops when large stacks must
be forced to the heap.

In Graal Python, the implementation is thus a bit more involved. Here's how it
works.

### The PFrame.Reference

A `PFrame.Reference` is created when entering a Python function. By default it
only holds on to another reference, that of the Python caller. If there are
non-Python frames between the newly entered frame and the last Python frame,
those are ignored - our linked list only connects Python frames. The entry point
into the interpreter has a `PFrame.Reference` with no caller.

#### ExecutionContext.CallContext and ExecutionContext.CalleeContext

If we're only calling between Python, we pass our `PFrame.Reference` as implicit
argument to any callees. On entry, they will create their own `PFrame.Reference`
as the next link in this backwards-connected linked-list. As an optimization, we
use assumptions both on the calling node as well as on the callee root node to
avoid passing the reference (in the caller) and linking it (on the callee
side). This assumption is invalidated the first time the reference is actually
needed. But even then, often the `PFrame.Reference` doesn't hold on to anything
else, because it was only used for traversal, so this is pretty cheap even in
the not inlined case.

When an event forces the frame to materialize on the heap, the reference is
filled. This is usually only the case when someone uses `sys._getframe` or
accesses the traceback of an exception. If the stack is still live, we walk the
stack and insert the "calling node" and create a "PyFrame" object that mirrors
the locals in the Truffle frame. But we need to be able to do this also for
frames that are no longer live, e.g. when an exception was a few frames up. To
ensure this, we set a boolean flag on `PFrame.Reference` to mark it as "escaped"
when it is attached to an exception (or anything else), but not accessed,
yet. Whenever a Python call returns and its `PFrame.Reference` was marked such,
the "PyFrame" is also filled in by copying from the VirtualFrame. This way, the
stack is lazily forced to the heap as we return from functions. If we're lucky
and it is never actually accessed *and* the calls are all inlined, those fill-in
operations can be escape-analyzed away.

To implement all this, we use the ExecutionContext.CallContext and
ExecutionContext.CalleeContext classes. These also use profiling information to
eagerly fill in frame information if the callees actually access the stack, for
example, so that no further stack walks need to take place.

#### ExecutionContext.IndirectCallContext and ExecutionContext.IndirectCalleeContext

If we're mixing Python frames with non-Python frames, or if we are making calls
to methods and cannot pass the Truffle frame, we need to store the last
`PFrame.Reference` on the context so that, if we ever return back into a Python
function, it can properly link to the last frame. However, this is potentially
expensive, because it means storing a linked list of frames on the context. So
instead, we do it only lazily. When an "indirect" Python callee needs its
caller, it initially walks the stack to find it. But it will also tell the last
Python node that made a call to a "foreign" callee that it will have to store
its `PFrame.Reference` globally in the future for it to be available later.

### The current PException

Now that we have a mechanism to lazily make available only as much frame state
as needed, we use the same mechanism to also pass the currently handled
exception. Unlike CPython we do not use a stack of currently handled exceptions,
instead we utilize the call stack of Java by always passing the current exception
and holding on to the last (if any) in a local variable.

## Patching of Packages

Some PyPI packages contain code that is not compatible with GraalPy.
To overcome this limitation and support such packages, GraalPy contains
patches for some popular packages. The patches are applied to packages
installed via GraalPy specific utility `ginstall` and also to packages
installed via `pip`. This is achieved by patching `pip` code.

The patches are regular POSIX `patch` command compatible diffs located in
`lib-graalpython/patches`. Check out the directory structure and metadate.toml
files to get an idea of how rules are set for patches to be applied.

## The GIL

We always run with a GIL, because C extensions in CPython expect to do so and
are usually not written to be reentrant. The reason to always have the GIL
enabled is that when using Python, at least Sulong/LLVM is always available in
the same context and we cannot know if someone may be using that (or another
polyglot language or the Java host interop) to start additional threads that
could call back into Python. This could legitimately happen in C extensions when
the C extension authors use knowledge of how CPython works to do something
GIL-less in a C thread that is fine to do on CPython's data structures, but not
for ours.

Suppose we were running GIL-less until a second thread appears. There is now two
options: the second thread immediately takes the GIL, but both threads might be
executing in parallel for a little while before the first thread gets to the
next safepoint where they try to release and re-acquire the GIL. Option two is
that we block the second thread on some other semaphore until the first thread
has acquired the GIL. This scenario may deadlock if the first thread is
suspended in a blocking syscall. This could be a legitimate use case when
e.g. one thread is supposed to block on a `select` call that the other thread
would unblock by operating on the selected resource. Now the second thread
cannot start to run because it is waiting for the first thread to acquire the
GIL. To get around this potential deadlock, we would have to "remember" around
blocking calls that the first thread would have released the GIL before and
re-acquired it after that point. Since this is equivalent to just releasing and
re-acquiring the GIL, we might as well always do that.

The implication of this is that we may have to acquire and release the GIL
around all library messages that may be invoked without already holding the
GIL. The pattern here is, around every one of those messages:

```
@ExportMessage xxx(..., @Cached GilNode gil) {
    boolean mustRelease = gil.acquire();
    try {
        ...
    } finally {
        gil.release(mustRelease);
    }
}
```

The `GilNode` when used in this pattern ensures that we only release the GIL if
we acquired it before. The `GilNode` internally uses profiles to ensure that if
we are always running single threaded or always own the GIL already when we get
to a specific message we never emit a boundary call to lock and unlock the
GIL. This implies that we may deopt in some places if, after compiling some
code, we later start a second thread.

## Threading

As explained above, we use a GIL to prevent parallel execution of Python code. A
timer is running the interrupts threads periodically to relinquish the GIL and
give other threads a chance to run. This preemption is prohibited in most C
extension code, however, since the assumption in C extensions written for
CPython is that the GIL will not be relinquished while executing the C code and
many C extensions are not written to reentrant.

So, commonly at any given time there is only one Graal Python thread executing
and all the other threads are waiting to acquire the GIL. If the Python
interpreter shuts down, there are two sets of threads we need to deal with:
"well-behaved" threads created using the `threading` module are interrupted and
joined using the `threading` module's `shutdown` function. Daemon threads or
threads created using the internal Python `_thread` module cannot be joined in
this way. For those threads, we invalidate their `PythonThreadState` (a
thread-local data structure) and use the Java `Thread#interrupt` method to
interrupt their waiting on the GIL. This exception is handled by checking if the
thread state has been invalidated and if so, just exit the thread gracefully.

For embedders, it may be important to be able to interrupt Python threads by
other means. We use the TruffleSafepoint mechanism to mark our threads waiting
to acquire the GIL as blocked for the purpose of safepoints. The Truffle
safepoint action mechanism can thus be used to kill threads waiting on the GIL.
