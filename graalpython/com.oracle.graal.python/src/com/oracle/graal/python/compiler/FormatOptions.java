package com.oracle.graal.python.compiler;

public abstract class FormatOptions {
    public static final int FVC_MASK = 0x3;
    public static final int FVC_NONE = 0x0;
    public static final int FVC_STR = 0x1;
    public static final int FVC_REPR = 0x2;
    public static final int FVC_ASCII = 0x3;
    public static final int FVS_MASK = 0x4;
    public static final int FVS_HAVE_SPEC = 0x4;
}
