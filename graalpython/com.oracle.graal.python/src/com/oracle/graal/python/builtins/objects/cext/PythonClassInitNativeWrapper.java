package com.oracle.graal.python.builtins.objects.cext;

import com.oracle.graal.python.builtins.objects.cext.NativeWrappers.PythonObjectNativeWrapper;
import com.oracle.graal.python.builtins.objects.type.PythonClass;

/**
 * Used to wrap {@link PythonClass} just for the time when a natively defined type is processed
 * in {@code PyType_Ready} and we need to pass the mirroring managed class to native to marry
 * these two objects.
 */
public class PythonClassInitNativeWrapper extends PythonObjectNativeWrapper {

    public PythonClassInitNativeWrapper(PythonClass object) {
        super(object);
    }

    @Override
    public String toString() {
        return String.format("PythonClassNativeInitWrapper(%s, isNative=%s)", getPythonObject(), isNative());
    }
}