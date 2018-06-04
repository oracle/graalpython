package com.oracle.graal.python.builtins.objects.cext;

import com.oracle.truffle.api.interop.TruffleObject;

abstract class PythonNativeWrapper implements TruffleObject {

    private Object nativePointer;

    public Object getNativePointer() {
        return nativePointer;
    }

    public void setNativePointer(Object nativePointer) {
        // we should set the pointer just once
        assert this.nativePointer == null || this.nativePointer.equals(nativePointer);
        this.nativePointer = nativePointer;
    }

    public boolean isNative() {
        return nativePointer != null;
    }

}
