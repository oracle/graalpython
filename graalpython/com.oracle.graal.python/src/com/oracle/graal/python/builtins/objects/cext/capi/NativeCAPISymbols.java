/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.cext.capi;

import java.lang.reflect.Field;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.ExplodeLoop.LoopExplosionKind;

@SuppressWarnings("unused")
public abstract class NativeCAPISymbols {

    public static final String FUN_NATIVE_LONG_TO_JAVA = "native_long_to_java";
    public static final String FUN_PY_TRUFFLE_STRING_TO_CSTR = "PyTruffle_StringToCstr";
    public static final String FUN_NATIVE_HANDLE_FOR_ARRAY = "NativeHandle_ForArray";
    public static final String FUN_PY_NONE_HANDLE = "PyNoneHandle";
    public static final String FUN_WHCAR_SIZE = "PyTruffle_Wchar_Size";
    public static final String FUN_PY_TRUFFLE_CSTR_TO_STRING = "PyTruffle_CstrToString";
    public static final String FUN_PY_TRUFFLE_ASCII_TO_STRING = "PyTruffle_AsciiToString";
    public static final String FUN_PY_FLOAT_AS_DOUBLE = "truffle_read_ob_fval";
    public static final String FUN_GET_OB_TYPE = "get_ob_type";
    public static final String FUN_GET_OB_REFCNT = "get_ob_refcnt";
    public static final String FUN_GET_TP_DICT = "get_tp_dict";
    public static final String FUN_GET_TP_BASE = "get_tp_base";
    public static final String FUN_GET_TP_BASES = "get_tp_bases";
    public static final String FUN_GET_TP_NAME = "get_tp_name";
    public static final String FUN_GET_TP_MRO = "get_tp_mro";
    public static final String FUN_GET_TP_ALLOC = "get_tp_alloc";
    public static final String FUN_GET_TP_DEALLOC = "get_tp_dealloc";
    public static final String FUN_GET_TP_FREE = "get_tp_free";
    public static final String FUN_GET_TP_FLAGS = "get_tp_flags";
    public static final String FUN_GET_TP_SUBCLASSES = "get_tp_subclasses";
    public static final String FUN_GET_TP_DICTOFFSET = "get_tp_dictoffset";
    public static final String FUN_GET_TP_BASICSIZE = "get_tp_basicsize";
    public static final String FUN_GET_TP_ITEMSIZE = "get_tp_itemsize";
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
    public static final String FUN_PY_OBJECT_NEW = "PyTruffle_Object_New";
    public static final String FUN_GET_THREAD_STATE_TYPE_ID = "get_thread_state_typeid";
    public static final String FUN_ADD_NATIVE_SLOTS = "PyTruffle_Type_AddSlots";
    public static final String FUN_PY_TRUFFLE_TUPLE_SET_ITEM = "PyTruffle_Tuple_SetItem";
    public static final String FUN_PY_TRUFFLE_TUPLE_GET_ITEM = "PyTruffle_Tuple_GetItem";
    public static final String FUN_PY_TRUFFLE_OBJECT_SIZE = "PyTruffle_Object_Size";
    public static final String FUN_PY_TYPE_READY = "PyType_Ready";
    public static final String FUN_GET_NEWFUNC_TYPE_ID = "get_newfunc_typeid";
    public static final String FUN_GET_BUFFER_R = "get_buffer_r";
    public static final String FUN_GET_BUFFER_RW = "get_buffer_rw";
    public static final String FUN_NATIVE_UNICODE_AS_STRING = "native_unicode_as_string";
    public static final String FUN_PY_UNICODE_GET_LENGTH = "PyUnicode_GetLength";
    public static final String FUN_GET_UINT32_ARRAY_TYPE_ID = "get_uint32_array_typeid";
    public static final String FUN_PY_TRUFFLE_FREE = "PyTruffle_Free";
    public static final String FUN_INCREF = "Py_IncRef";
    public static final String FUN_DECREF = "Py_DecRef";
    public static final String FUN_ADDREF = "PyTruffle_ADDREF";
    public static final String FUN_SUBREF = "PyTruffle_SUBREF";
    public static final String FUN_TRUFFLE_MANAGED_FROM_HANDLE = "truffle_managed_from_handle";
    public static final String FUN_TRUFFLE_CANNOT_BE_HANDLE = "truffle_cannot_be_handle";
    public static final String FUN_GET_LONG_BITS_PER_DIGIT = "get_long_bits_in_digit";
    public static final String FUN_BULK_SUBREF = "PyTruffle_bulk_SUBREF";
    private static final String FUN_GET_INT8_T_TYPEID = "get_int8_t_typeid";
    private static final String FUN_GET_INT16_T_TYPEID = "get_int16_t_typeid";
    private static final String FUN_GET_INT32_T_TYPEID = "get_int32_t_typeid";
    private static final String FUN_GET_INT64_T_TYPEID = "get_int64_t_typeid";
    private static final String FUN_GET_UINT8_T_TYPEID = "get_uint8_t_typeid";
    private static final String FUN_GET_UINT16_T_TYPEID = "get_uint16_t_typeid";
    private static final String FUN_GET_UINT32_T_TYPEID = "get_uint32_t_typeid";
    private static final String FUN_GET_UINT64_T_TYPEID = "get_uint64_t_typeid";
    private static final String FUN_GET_PY_COMPLEX_TYPEID = "get_Py_complex_typeid";
    private static final String FUN_GET_FLOAT_T_TYPEID = "get_float_t_typeid";
    private static final String FUN_GET_DOUBLE_T_TYPEID = "get_double_t_typeid";
    private static final String FUN_GET_PY_SSIZE_T_TYPEID = "get_Py_ssize_t_typeid";
    private static final String FUN_GET_PYOBJECT_PTR_T_TYPEID = "get_PyObject_ptr_t_typeid";
    private static final String FUN_GET_PYOBJECT_PTR_PTR_T_TYPEID = "get_PyObject_ptr_ptr_t_typeid";
    private static final String FUN_GET_CHAR_PTR_T_TYPEID = "get_char_ptr_t_typeid";
    private static final String FUN_GET_INT8_PTR_T_TYPEID = "get_int8_ptr_t_typeid";
    private static final String FUN_GET_INT16_PTR_T_TYPEID = "get_int16_ptr_t_typeid";
    private static final String FUN_GET_INT32_PTR_T_TYPEID = "get_int32_ptr_t_typeid";
    private static final String FUN_GET_INT64_PTR_T_TYPEID = "get_int64_ptr_t_typeid";
    private static final String FUN_GET_UINT8_PTR_T_TYPEID = "get_uint8_ptr_t_typeid";
    private static final String FUN_GET_UINT16_PTR_T_TYPEID = "get_uint16_ptr_t_typeid";
    private static final String FUN_GET_UINT32_PTR_T_TYPEID = "get_uint32_ptr_t_typeid";
    private static final String FUN_GET_UINT64_PTR_T_TYPEID = "get_uint64_ptr_t_typeid";
    private static final String FUN_GET_PY_COMPLEX_PTR_T_TYPEID = "get_Py_complex_ptr_t_typeid";
    private static final String FUN_GET_FLOAT_PTR_T_TYPEID = "get_float_ptr_t_typeid";
    private static final String FUN_GET_DOUBLE_PTR_T_TYPEID = "get_double_ptr_t_typeid";
    private static final String FUN_GET_PY_SSIZE_PTR_T_TYPEID = "get_Py_ssize_ptr_t_typeid";

    @CompilationFinal(dimensions = 1) private static final String[] values;
    static {
        Field[] declaredFields = NativeCAPISymbols.class.getDeclaredFields();
        values = new String[declaredFields.length - 1]; // omit the values field
        for (int i = 0; i < declaredFields.length; i++) {
            Field s = declaredFields[i];
            if (s.getType() == String.class) {
                try {
                    values[i] = (String) s.get(NativeCAPISymbols.class);
                } catch (IllegalArgumentException | IllegalAccessException e) {
                }
            }
        }
    }

    @ExplodeLoop(kind = LoopExplosionKind.FULL_UNROLL_UNTIL_RETURN)
    public static boolean isValid(String name) {
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(name)) {
                return true;
            }
        }
        return false;
    }
}
