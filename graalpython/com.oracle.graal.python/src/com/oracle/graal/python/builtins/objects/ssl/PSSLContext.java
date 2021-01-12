package com.oracle.graal.python.builtins.objects.ssl;

import com.oracle.graal.python.builtins.modules.SSLModuleBuiltins;
import javax.net.ssl.SSLContext;

import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.truffle.api.object.Shape;

public final class PSSLContext extends PythonBuiltinObject {
    private final SSLProtocolVersion version;
    private final SSLContext context;
    private boolean checkHostname;
    private int verifyMode;

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

    int getVerifyMode() {
        return verifyMode;
    }

    void setVerifyMode(int verifyMode) {
        assert verifyMode == SSLModuleBuiltins.SSL_CERT_NONE || verifyMode == SSLModuleBuiltins.SSL_CERT_OPTIONAL || verifyMode == SSLModuleBuiltins.SSL_CERT_REQUIRED;
        this.verifyMode = verifyMode;
    }

}
