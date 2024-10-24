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
enabled is that when using Python, another polyglot language or the Java host
interop can be available in the same context, and we cannot know if someone
may be using that to start additional threads that
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

## C Extensions and Memory Management

### High-level

C extensions assume reference counting, but on the managed side we want to leverage
Java tracing GC. This creates a mismatch. The approach is to do both, reference
counting and tracing GC, at the same time.

On the native side we use reference counting. The native code is responsible for doing
the counting, i.e., calling the `Py_IncRef` and `Py_DecRef` API functions. Inside those
functions we add special handling for the point when first reference from the native
code is created and when the last reference from the native code is destroyed.

On the managed side we rely on tracing GC, so managed references are not ref-counted.
For the ref-counting scheme on the native side, we approximate all the managed references
as a single reference, i.e., we increment the refcount when object is referenced from managed
code, and using a `PhantomReference` and reference queue we decrement the refcount when
there are no longer any managed references (but we do not clean the object as long as
`refcount > 0`, because that means that there are still native references to it).

### Details

There are two kinds of Python objects in GraalPy: managed and native.

#### Managed Objects

Managed objects are allocated in the interpreter. If there is no native code involved,
we do not do anything special and let the Java GC handle them. When a managed object
is passed to a native extension code:

* We wrap it in `PythonObjectNativeWrapper`. This is mostly in order to provide different
interop protocol: we do not want to expose `toNative` and `asPointer` on Python objects.

* When NFI calls `toNative`/`asPointer` we:
    * Allocate C memory that will represent the object on the native side (including the refcount field)
    * Add a mapping of that memory address to the `PythonObjectNativeWrapper` object to a hash map `CApiTransitions.nativeLookup`.
    * We initialize the refcount field to a constant `MANAGED_REFCNT` (larger number, because some
              extensions like to special case on some small refcount values)
    * Create `PythonObjectReference`: a weak reference to the `PythonObjectNativeWrapper`,
    when this reference is enqueued (i.e., no managed references exist), we decrement the refcount by
    `MANAGED_REFCNT` and if the recount falls back to `0`, we deallocate the native memory of the object,
    otherwise we need to wait for the native code to eventually call `Py_DecRef` and make it `0`.

* When extension code wants to create a new reference, it will call `Py_IncRef`.
In the C implementation of `Py_IncRef` we check if a managed object with
`refcount==MANAGED_REFCNT` wants to increment its refcount. In such case, the native code is
creating a first reference to the managed object, we must make sure to keep the object alive
as long as there are some native references. We set a field `PythonObjectReference.strongReference`,
which will keep the `PythonObjectNativeWrapper` alive even when all other managed references die.

* When extension code is done with the object, it will call `Py_DecRef`.
In the C implementation of `Py_DecRef` we check if a managed object with `refcount == MANAGED_REFCNT+1`
wants to decrement its refcount to MANAGED_REFCNT, which means that there are no native references
to that object anymore. In such case we clear the `PythonObjectReference.strongReference` field,
and the memory management is then again left solely to the Java tracing GC.

#### Native Objects

Native objects allocated using `PyObject_GC_New` in the native code are backed by native memory
and may never be passed to managed code (as a return value of extension function or as an argument
to some C API call). If a native object is not made available to managed code, it is just reference
counted as usual, where `Py_DecRef` call that reaches `0` will deallocate the object. If a native
object is passed to managed code:

* We increment the refcount of the native object by `MANAGED_REFCNT`
* We create:
  * `PythonAbstractNativeObject` Java object to mirror it on the managed side
  * `NativeObjectReference`, a weak reference to the `PythonAbstractNativeObject`.
* Add mapping: native object address => `NativeObjectReference` into hash map `CApiTransitions.nativeLookup`
  * Next time we just fetch the existing wrapper and don't do any of this
* When `NativeObjectReference` is enqueued, we decrement the refcount by `MANAGED_REFCNT`
  * If the refcount falls to `0`, it means that there are no references to the object even from
  native code, and we can destroy it. If it does not fall to `0`, we just wait for the native
  code to eventually call `Py_DecRef` that makes it fall to `0`.

#### Weak References

TODO

### Cycle GC

We leverage the CPython's GC module to detect cycles for objects that participate
in the reference counting scheme (native objects or managed objects that got passed
to native code).
See: https://devguide.python.org/internals/garbage-collector/index.html.

There are two issues:

* Objects that are referenced from the managed code have `refcount >= MANAGED_REFCNT` and
until Java GC runs we do not know if they are garbage or not.
* We cannot traverse the managed objects: since we don't do refcounting on the managed
side, we cannot traverse them and decrement refcounts to see if there is a cycle.

The high level solution is that when we see a "dead" cycle going through a managed object
(i.e., cycle not referenced by any native object from the "outside" of the collected set),
we fully replicate the object graphs (and the cycle) on the managed side (refcounts of native objects
in the cycle, which were not referenced from managed yet, will get new `NativeObjectReference`
created and refcount incremented by `MANAGED_REFCNT`). Managed objects already refer
to the `PythonAbstractNativeObject` wrappers of the native objects (e.g., some Python container
with managed storage), but we also make the native wrappers refer to whatever their referents
are on the Java side (we use `tp_traverse` to find their referents).

Then we make the managed objects in the cycle only weakly referenced on the Java side.
One can think about this as pushing the baseline reference count when the
object is eligible for being GC'ed and thus freed. Normally when the object has
`refcount > MANAGED_REFCNT` we keep it alive with a strong reference assuming that
there are some native references to it. In this case, we know that all the native
references to that object are part of potentially dead cycle, and we do not
count them into this limit. Let us call this limit *weak to strong limit*.

After this, if the managed objects are garbage, eventually Java GC will collect them
together with the whole cycle.

If some of the managed objects are not garbage, and they passed back to native code,
the native code can then access and resurrect the whole cycle. W.r.t. the refcounts
integrity this is fine, because we did not alter the refcounts. The native references
between the objects are still factored in their refcounts. What may seem like a problem
is that we pushed the *weak to strong limit* for some objects. Such an object may be
passed to native, get `Py_IncRef`'ed making it strong reference again. Since `Py_DecRef` is
checking the same `MANAGED_REFCNT` limit for all objects, the subsequent `Py_DecRef`
call for this object will not detect that the reference should be made weak again!
However, this is OK, it only prolongs the collection: we will make it weak again in
the next run of the cycle GC on the native side.
