package com.oracle.graal.python.builtins.objects.ssl;

import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.truffle.api.object.Shape;

public final class PSSLSession extends PythonBuiltinObject {
    public PSSLSession(Object cls, Shape instanceShape) {
        super(cls, instanceShape);
    }
}
