package com.oracle.graal.python.builtins.objects.ssl;

import javax.net.ssl.SSLContext;

import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.truffle.api.object.Shape;

public final class PSSLContext extends PythonBuiltinObject {
    private final SSLProtocolVersion version;
    private final SSLContext context;
    private boolean checkHostname;

    public PSSLContext(Object cls, Shape instanceShape, SSLProtocolVersion version, SSLContext context) {
        super(cls, instanceShape);
        assert version != null;
        this.version = version;
        this.context = context;
    }

    public SSLProtocolVersion getVersion() {
        return version;
    }

    public SSLContext getContext() {
        return context;
    }

    public boolean getCheckHostname() {
        return checkHostname;
    }

    public void setCheckHostname(boolean checkHostname) {
        this.checkHostname = checkHostname;
    }
}
