package com.oracle.graal.python.builtins.objects.cpyobject;

import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.TruffleObject;

/**
 * Used to wrap {@link PythonObject} when used in native code. This wrapper mimics the correct shape
 * of the corresponding native type {@code struct _object}.
 */
public class PythonObjectNativeWrapper implements TruffleObject {
    private final PythonObject pythonObject;
    @CompilationFinal private Object nativePointer;

    public PythonObjectNativeWrapper(PythonClass delegate) {
        this.pythonObject = delegate;
    }

    public boolean isNative() {
        return nativePointer != null;
    }

    public Object getNativePointer() {
        return nativePointer;
    }

    public void setNativePointer(Object nativePointer) {
        // we should set the pointer just once
        assert this.nativePointer == null;
        CompilerDirectives.transferToInterpreterAndInvalidate();
        this.nativePointer = nativePointer;
    }

    public PythonObject getPythonObject() {
        return pythonObject;
    }

    public boolean isInstance(TruffleObject o) {
        return o instanceof PythonObjectNativeWrapper;
    }

    public ForeignAccess getForeignAccess() {
        return null;
    }

}
