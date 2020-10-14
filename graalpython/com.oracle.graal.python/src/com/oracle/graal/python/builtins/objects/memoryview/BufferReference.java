package com.oracle.graal.python.builtins.objects.memoryview;

import java.lang.ref.PhantomReference;
import java.lang.ref.ReferenceQueue;

class BufferReference extends PhantomReference<Object> {
    private final ManagedBuffer managedBuffer;
    private boolean released;

    public BufferReference(Object referent, ManagedBuffer managedBuffer, ReferenceQueue<? super Object> q) {
        super(referent, q);
        assert managedBuffer != null;
        managedBuffer.incrementExports();
        this.managedBuffer = managedBuffer;
    }

    public ManagedBuffer getManagedBuffer() {
        return managedBuffer;
    }

    public boolean isReleased() {
        return released;
    }

    public void markReleased() {
        this.released = true;
    }
}
