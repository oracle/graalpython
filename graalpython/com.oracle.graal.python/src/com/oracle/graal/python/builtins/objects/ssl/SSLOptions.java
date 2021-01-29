package com.oracle.graal.python.builtins.objects.ssl;

public abstract class SSLOptions {
    public static final int DEFAULT_OPTIONS = SSLOptions.SSL_OP_ALL & ~SSLOptions.SSL_OP_DONT_INSERT_EMPTY_FRAGMENTS;

    public static final int SSL_OP_CRYPTOPRO_TLSEXT_BUG = 0x80000000;
    public static final int SSL_OP_DONT_INSERT_EMPTY_FRAGMENTS = 0x00000800;
    public static final int SSL_OP_LEGACY_SERVER_CONNECT = 0x00000004;
    public static final int SSL_OP_TLSEXT_PADDING = 0x00000010;
    public static final int SSL_OP_SAFARI_ECDHE_ECDSA_BUG = 0x00000040;
    public static final int SSL_OP_ALL = (SSL_OP_CRYPTOPRO_TLSEXT_BUG | SSL_OP_DONT_INSERT_EMPTY_FRAGMENTS | SSL_OP_LEGACY_SERVER_CONNECT | SSL_OP_TLSEXT_PADDING | SSL_OP_SAFARI_ECDHE_ECDSA_BUG);
    public static final int SSL_OP_NO_SSLv2 = 0x1000000;
    public static final int SSL_OP_NO_SSLv3 = 0x2000000;
    public static final int SSL_OP_NO_TLSv1 = 0x4000000;
    public static final int SSL_OP_NO_TLSv1_1 = 0x10000000;
    public static final int SSL_OP_NO_TLSv1_2 = 0x8000000;
    public static final int SSL_OP_NO_TLSv1_3 = 0x20000000;
}
