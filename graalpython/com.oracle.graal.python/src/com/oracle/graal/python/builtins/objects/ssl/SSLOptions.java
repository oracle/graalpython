package com.oracle.graal.python.builtins.objects.ssl;

public abstract class SSLOptions {

    public static final int DEFAULT_OPTIONS = 0;

    public static final int SSL_OP_NO_SSLv2 = 0;
    public static final int SSL_OP_NO_SSLv3 = 0x2000000;
    public static final int SSL_OP_NO_TLSv1 = 0x4000000;
    public static final int SSL_OP_NO_TLSv1_1 = 0x10000000;
    public static final int SSL_OP_NO_TLSv1_2 = 0x8000000;
    public static final int SSL_OP_NO_TLSv1_3 = 0x20000000;
}
