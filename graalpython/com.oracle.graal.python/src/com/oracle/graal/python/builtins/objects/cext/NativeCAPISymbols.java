/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.graal.python.builtins.objects.cext;

public abstract class NativeCAPISymbols {

    public static final String FUN_NATIVE_POINTER_TO_JAVA = "native_pointer_to_java";
    public static final String FUN_NATIVE_LONG_TO_JAVA = "native_long_to_java";
    public static final String FUN_NATIVE_TO_JAVA = "native_to_java_exported";
    public static final String FUN_PY_TRUFFLE_STRING_TO_CSTR = "PyTruffle_StringToCstr";
    public static final String FUN_PY_OBJECT_HANDLE_FOR_JAVA_OBJECT = "PyObjectHandle_ForJavaObject";
    public static final String FUN_PY_OBJECT_HANDLE_FOR_JAVA_TYPE = "PyObjectHandle_ForJavaType";
    public static final String FUN_NATIVE_HANDLE_FOR_ARRAY = "NativeHandle_ForArray";
    public static final String FUN_PY_NONE_HANDLE = "PyNoneHandle";
    public static final String FUN_WHCAR_SIZE = "PyTruffle_Wchar_Size";
    public static final String FUN_PY_TRUFFLE_CSTR_TO_STRING = "PyTruffle_CstrToString";
    public static final String FUN_PY_FLOAT_AS_DOUBLE = "PyFloat_AsDouble";
    public static final String FUN_GET_OB_TYPE = "get_ob_type";
    public static final String FUN_DEREF_HANDLE = "truffle_deref_handle_for_managed";
    public static final String FUN_GET_BYTE_ARRAY_TYPE_ID = "get_byte_array_typeid";
    public static final String FUN_GET_PTR_ARRAY_TYPE_ID = "get_ptr_array_typeid";
    public static final String FUN_PTR_COMPARE = "truffle_ptr_compare";
    public static final String FUN_PY_TRUFFLE_BYTE_ARRAY_TO_NATIVE = "PyTruffle_ByteArrayToNative";
    public static final String FUN_PY_TRUFFLE_INT_ARRAY_TO_NATIVE = "PyTruffle_IntArrayToNative";
    public static final String FUN_PY_TRUFFLE_LONG_ARRAY_TO_NATIVE = "PyTruffle_LongArrayToNative";
    public static final String FUN_PY_TRUFFLE_DOUBLE_ARRAY_TO_NATIVE = "PyTruffle_DoubleArrayToNative";
    public static final String FUN_PY_TRUFFLE_OBJECT_ARRAY_TO_NATIVE = "PyTruffle_ObjectArrayToNative";
    public static final String FUN_PY_OBJECT_GENERIC_GET_DICT = "_PyObject_GenericGetDict";
    public static final String FUN_PY_OBJECT_GENERIC_NEW = "PyType_GenericAlloc";
}
