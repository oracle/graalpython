package com.oracle.graal.python.builtins.objects.cext;

import com.oracle.truffle.api.interop.TruffleObject;

public abstract class PythonNativeWrapper implements TruffleObject {

    private Object delegate;
    private Object nativePointer;

    public PythonNativeWrapper() {
    }

    public PythonNativeWrapper(Object delegate) {
        this.delegate = delegate;
    }

    public final Object getDelegate() {
        return delegate;
    }

    protected void setDelegate(Object delegate) {
        this.delegate = delegate;
    }

    public Object getNativePointer() {
        return nativePointer;
    }

    public void setNativePointer(Object nativePointer) {
        // we should set the pointer just once
        assert this.nativePointer == null || this.nativePointer.equals(nativePointer) || nativePointer == null;
        this.nativePointer = nativePointer;
    }

    public boolean isNative() {
        return nativePointer != null;
    }
}