package com.oracle.graal.python.builtins.objects.ssl;

import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.truffle.api.object.Shape;

public final class PSSLContext extends PythonBuiltinObject {
    private final SSLProtocolVersion version;

    public PSSLContext(Object cls, Shape instanceShape, SSLProtocolVersion version) {
        super(cls, instanceShape);
        assert version != null;
        this.version = version;
    }

    public SSLProtocolVersion getVersion() {
        return version;
    }
}
