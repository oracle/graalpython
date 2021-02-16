package com.oracle.graal.python.builtins.objects.ssl;

import com.oracle.truffle.api.nodes.ExplodeLoop;

public enum SSLMethod {
    SSL2(0, SSLProtocol.SSLv2),
    SSL3(1, SSLProtocol.SSLv3),
    TLS(2, "TLS"),
    TLS1(3, SSLProtocol.TLSv1),
    TLS1_1(4, SSLProtocol.TLSv1_1),
    TLS1_2(5, SSLProtocol.TLSv1_2),
    TLS_CLIENT(0x10, "TLS"),
    TLS_SERVER(0x11, "TLS");

    private final int pythonId;
    private final String javaId;
    private final SSLProtocol singleVersion;

    SSLMethod(int pythonId, SSLProtocol singleVersion) {
        this.pythonId = pythonId;
        this.javaId = singleVersion.getName();
        this.singleVersion = singleVersion;
    }

    SSLMethod(int pythonId, String javaId) {
        this.pythonId = pythonId;
        this.javaId = javaId;
        this.singleVersion = null;
    }

    public int getPythonId() {
        return pythonId;
    }

    public String getJavaId() {
        return javaId;
    }

    public boolean allowsProtocol(SSLProtocol protocol) {
        return singleVersion == null || singleVersion == protocol;
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
