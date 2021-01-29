package com.oracle.graal.python.builtins.objects.ssl;

import com.oracle.truffle.api.nodes.ExplodeLoop;

public enum SSLMethod {
    SSL2(0, SSLProtocol.SSLv2.getName(), true),
    SSL3(1, SSLProtocol.SSLv3.getName(), true),
    TLS(2, "TLS", false),
    TLS1(3, SSLProtocol.TLSv1.getName(), true),
    TLS1_1(4, SSLProtocol.TLSv1_1.getName(), true),
    TLS1_2(5, SSLProtocol.TLSv1_2.getName(), true),
    TLS_CLIENT(0x10, "TLS", false),
    TLS_SERVER(0x11, "TLS", false);

    private final int pythonId;
    private final String javaId;
    private final boolean singleVersion;

    SSLMethod(int pythonId, String javaId, boolean singleVersion) {
        this.pythonId = pythonId;
        this.javaId = javaId;
        this.singleVersion = singleVersion;
    }

    public int getPythonId() {
        return pythonId;
    }

    public String getJavaId() {
        return javaId;
    }

    public boolean isSingleVersion() {
        return singleVersion;
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
