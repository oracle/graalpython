package com.oracle.graal.python.builtins.objects.cpyobject;

public abstract class NativeMemberNames {
    public static final String OB_BASE = "ob_base";
    public static final String OB_REFCNT = "ob_refcnt";
    public static final String OB_TYPE = "ob_type";
    public static final String OB_SIZE = "ob_size";
    public static final String OB_SVAL = "ob_sval";
    public static final String TP_FLAGS = "tp_flags";
    public static final String TP_NAME = "tp_name";
    public static final String TP_BASE = "tp_base";

    public static boolean isValid(String key) {
        switch (key) {
            case OB_BASE:
            case OB_REFCNT:
            case OB_TYPE:
            case OB_SIZE:
            case OB_SVAL:
            case TP_FLAGS:
            case TP_NAME:
            case TP_BASE:
                return true;
        }
        return false;
    }
}
