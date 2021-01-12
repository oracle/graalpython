package com.oracle.graal.python.builtins.objects.ssl;

import com.oracle.truffle.api.nodes.ExplodeLoop;

public enum SSLProtocolVersion {
    SSL2(0, "SSLv2"),
    SSL3(1, "SSLv3"),
    TLS(2, "TLS"),
    TLS1(3, "TLSv1"),
    TLS1_1(4, "TLSv1.1"),
    TLS1_2(5, "TLSv1.2"),
    // TODO figure out what these mean
    TLS_CLIENT(0x10, "TLS"),
    TLS_SERVER(0x11, "TLS");

    private final int pythonId;
    private final String javaId;

    SSLProtocolVersion(int pythonId, String javaId) {
        this.pythonId = pythonId;
        this.javaId = javaId;
    }

    public int getPythonId() {
        return pythonId;
    }

    public String getJavaId() {
        return javaId;
    }

    @ExplodeLoop
    public static SSLProtocolVersion fromPythonId(int pythonId) {
        for (SSLProtocolVersion version : SSLProtocolVersion.values()) {
            if (version.getPythonId() == pythonId) {
                return version;
            }
        }
        return null;
    }
}
