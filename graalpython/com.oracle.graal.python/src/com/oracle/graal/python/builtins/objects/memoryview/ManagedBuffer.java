package com.oracle.graal.python.builtins.objects.memoryview;

import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Object for tracking lifetime of buffers inside memoryviews. The only purpose is to release the
 * underlying buffer when this object's export count goes to 0 or it gets garbage collected. Should
 * only be created for buffers that actually need to be released.
 *
 * Rough equivalent of CPython's {@code _PyManagedBuffer_Type}
 */
public class ManagedBuffer {
    // Buffer owner, null for native objects
    final WeakReference<Object> owner;
    // Pointer to native Py_buffer if any, null for managed objects
    final Object bufferStructPointer;

    final AtomicInteger exports = new AtomicInteger();

    private ManagedBuffer(WeakReference<Object> owner, Object bufferStructPointer) {
        this.owner = owner;
        this.bufferStructPointer = bufferStructPointer;
    }

    public static ManagedBuffer createForManaged(Object owner) {
        assert owner != null;
        return new ManagedBuffer(new WeakReference<>(owner), null);
    }

    public static ManagedBuffer createForNative(Object bufferStructPointer) {
        assert bufferStructPointer != null;
        return new ManagedBuffer(null, bufferStructPointer);
    }

    public boolean isForNative() {
        return bufferStructPointer != null;
    }

    public Object getOwner() {
        return owner.get();
    }

    public Object getBufferStructPointer() {
        return bufferStructPointer;
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
