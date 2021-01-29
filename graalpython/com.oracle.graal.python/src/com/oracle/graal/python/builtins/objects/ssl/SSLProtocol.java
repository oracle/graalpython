package com.oracle.graal.python.builtins.objects.ssl;

public enum SSLProtocol {
    SSLv2("SSLv2", -1, SSLOptions.SSL_OP_NO_SSLv2),
    SSLv3("SSLv3", 0x0300, SSLOptions.SSL_OP_NO_SSLv3),
    TLSv1("TLSv1", 0x0301, SSLOptions.SSL_OP_NO_TLSv1),
    TLSv1_1("TLSv1.1", 0x0302, SSLOptions.SSL_OP_NO_TLSv1_1),
    TLSv1_2("TLSv1.2", 0x0303, SSLOptions.SSL_OP_NO_TLSv1_2),
    TLSv1_3("TLSv1.3", 0x0304, SSLOptions.SSL_OP_NO_TLSv1_3);

    private final String name;
    private final int id;
    private final long disableOption;

    SSLProtocol(String name, int id, long disableOption) {
        this.name = name;
        this.id = id;
        this.disableOption = disableOption;
    }

    public String getName() {
        return name;
    }

    public int getId() {
        return id;
    }

    public long getDisableOption() {
        return disableOption;
    }
}
