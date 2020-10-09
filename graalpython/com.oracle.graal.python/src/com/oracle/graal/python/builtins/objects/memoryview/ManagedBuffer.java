package com.oracle.graal.python.builtins.objects.memoryview;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Object for tracking lifetime of buffers inside memoryviews. The only purpose is to release the
 * underlying buffer when this object's export count goes to 0 or it gets garbage collected. Should
 * only be created for buffers that actually need to be released.
 *
 * Rough equivalent of CPython's {@code _PyManagedBuffer_Type}
 */
public class ManagedBuffer {
    // Buffer owner, never null
    final Object owner;
    // Pointer to native Py_buffer if any, null for managed objects
    final Object bufferStructPointer;
    // Pointer to native bf_releasebuffer C function, null for managed objects
    final Object releasefn;

    final AtomicInteger exports = new AtomicInteger();

    public ManagedBuffer(Object owner, Object bufferStructPointer, Object releasefn) {
        assert owner != null : "Buffers without an owner object shouldn't create a ManagedBuffer";
        assert releasefn == null || bufferStructPointer != null;
        this.owner = owner;
        this.bufferStructPointer = bufferStructPointer;
        this.releasefn = releasefn;
    }

    public ManagedBuffer(Object owner) {
        this(owner, null, null);
    }

    public Object getOwner() {
        return owner;
    }

    public Object getBufferStructPointer() {
        return bufferStructPointer;
    }

    public Object getReleaseFunction() {
        return releasefn;
    }

    public AtomicInteger getExports() {
        return exports;
    }

    public int incrementExports() {
        return exports.incrementAndGet();
    }

    public int decrementExports() {
        return exports.decrementAndGet();
    }
}
