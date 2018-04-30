package com.oracle.graal.python.builtins.objects.cpyobject;

public abstract class NativeCAPISymbols {

    public static final String FUNCTION_NATIVE_TO_JAVA = "native_to_java";
    public static final String FUN_PY_TRUFFLE_STRING_TO_CSTR = "PyTruffle_StringToCstr";
    public static final String FUN_PY_OBJECT_HANDLE_FOR_JAVA_OBJECT = "PyObjectHandle_ForJavaObject";
    public static final String FUN_PY_OBJECT_HANDLE_FOR_JAVA_TYPE = "PyObjectHandle_ForJavaType";

    public static final String FUN_PY_NONE_HANDLE = "PyNoneHandle";

}
