package com.oracle.graal.python.builtins.objects.ssl;

import com.oracle.truffle.api.nodes.ExplodeLoop;

public enum SSLMethod {
    SSL2(0, SSLProtocol.SSLv2),
    SSL3(1, SSLProtocol.SSLv3),
    TLS(2),
    TLS1(3, SSLProtocol.TLSv1),
    TLS1_1(4, SSLProtocol.TLSv1_1),
    TLS1_2(5, SSLProtocol.TLSv1_2),
    TLS_CLIENT(0x10),
    TLS_SERVER(0x11);

    private final int pythonId;
    private final SSLProtocol singleVersion;

    SSLMethod(int pythonId, SSLProtocol singleVersion) {
        this.pythonId = pythonId;
        this.singleVersion = singleVersion;
    }

    SSLMethod(int pythonId) {
        this.pythonId = pythonId;
        this.singleVersion = null;
    }

    public int getPythonId() {
        return pythonId;
    }

    public boolean allowsProtocol(SSLProtocol protocol) {
        return singleVersion == null || singleVersion == protocol;
    }

    public boolean isSingleVersion() {
        return singleVersion != null;
    }

    @ExplodeLoop
    public static SSLMethod fromPythonId(int pythonId) {
        for (SSLMethod method : SSLMethod.values()) {
            if (method.getPythonId() == pythonId) {
                return method;
            }
        }
        return null;
    }
}
