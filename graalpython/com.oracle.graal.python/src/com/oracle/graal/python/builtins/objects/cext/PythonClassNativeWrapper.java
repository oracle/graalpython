package com.oracle.graal.python.builtins.objects.cext;

import com.oracle.graal.python.builtins.objects.cext.CArrayWrappers.CStringWrapper;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.builtins.objects.type.PythonManagedClass;

/**
 * Used to wrap {@link PythonClass} when used in native code. This wrapper mimics the correct
 * shape of the corresponding native type {@code struct _typeobject}.
 */
public class PythonClassNativeWrapper extends DynamicObjectNativeWrapper.PythonObjectNativeWrapper {
    private final CStringWrapper nameWrapper;
    private Object getBufferProc;
    private Object releaseBufferProc;

    private PythonClassNativeWrapper(PythonManagedClass object, String name) {
        super(object);
        this.nameWrapper = new CStringWrapper(name);
    }

    public CStringWrapper getNameWrapper() {
        return nameWrapper;
    }

    public Object getGetBufferProc() {
        return getBufferProc;
    }

    public void setGetBufferProc(Object getBufferProc) {
        this.getBufferProc = getBufferProc;
    }

    public Object getReleaseBufferProc() {
        return releaseBufferProc;
    }

    public void setReleaseBufferProc(Object releaseBufferProc) {
        this.releaseBufferProc = releaseBufferProc;
    }

    public static PythonClassNativeWrapper wrap(PythonManagedClass obj, String name) {
        // important: native wrappers are cached
        PythonClassNativeWrapper nativeWrapper = obj.getNativeWrapper();
        if (nativeWrapper == null) {
            nativeWrapper = new PythonClassNativeWrapper(obj, name);
            obj.setNativeWrapper(nativeWrapper);
        }
        return nativeWrapper;
    }

    @Override
    public String toString() {
        return String.format("PythonClassNativeWrapper(%s, isNative=%s)", getPythonObject(), isNative());
    }
}
