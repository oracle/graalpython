package com.oracle.graal.python.builtins.objects.ssl;

import javax.net.ssl.SSLSocket;

import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.truffle.api.object.Shape;

public final class PSSLSocket extends PythonBuiltinObject {
    private final PSSLContext context;
    private final SSLSocket javaSocket;

    public PSSLSocket(Object cls, Shape instanceShape, PSSLContext context, SSLSocket javaSocket) {
        super(cls, instanceShape);
        this.context = context;
        this.javaSocket = javaSocket;
    }

    public PSSLContext getContext() {
        return context;
    }

    public SSLSocket getJavaSocket() {
        return javaSocket;
    }
}
