package com.oracle.graal.python.builtins.objects.ssl;

public enum SSLProtocolVersion {
    SSL2(0),
    SSL3(1),
    TLS(2),
    TLS1(3),
    TLS1_1(4),
    TLS1_2(5),
    TLS_CLIENT(0x10),
    TLS_SERVER(0x11);

    private int protocolId;

    SSLProtocolVersion(int protocolId) {
        this.protocolId = protocolId;
    }
}
