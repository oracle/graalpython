/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.util.PythonUtils.toTruffleStringUncached;

import java.util.HashMap;

import com.oracle.graal.python.builtins.objects.cext.common.NativeCExtSymbol;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.strings.TruffleString;

public enum NativeCAPISymbol implements NativeCExtSymbol {

    /* Sulong intrinsics */

    FUN_POLYGLOT_FROM_TYPED("polyglot_from_typed"),
    FUN_POLYGLOT_ARRAY_TYPEID("polyglot_array_typeid"),
    FUN_POLYGLOT_FROM_STRING("polyglot_from_string"),
    GRAALVM_LLVM_VA_ARG("_graalvm_llvm_va_arg"),

    FUN_PYTRUFFLE_ALLOCATE_TYPE("PyTruffle_AllocateType"),
    FUN_PYTRUFFLE_POPULATE_TYPE("PyTruffle_PopulateType"),
    FUN_PYTRUFFLE_PATCH_TYPE("PyTruffle_PatchType"),
    FUN_PYTRUFFLE_ALLOCATE_METHOD_DEF("PyTruffle_AllocateMethodDef"),
    FUN_PYTRUFFLE_ALLOCATE_DATETIME_API("PyTruffle_AllocateDateTimeAPI"),
    FUN_PYTRUFFLE_ALLOCATE_MEMORY_VIEW("PyTruffle_AllocateMemoryView"),
    FUN_PYTRUFFLE_CONVERT_TO_POINTER("PyTruffle_ConvertToPointer"),

    /* C functions for reading native members by offset */

    FUN_READ_SHORT_MEMBER("ReadShortMember"),
    FUN_READ_INT_MEMBER("ReadIntMember"),
    FUN_READ_LONG_MEMBER("ReadLongMember"),
    FUN_READ_FLOAT_MEMBER("ReadFloatMember"),
    FUN_READ_DOUBLE_MEMBER("ReadDoubleMember"),
    FUN_READ_STRING_MEMBER("ReadStringMember"),
    FUN_READ_STRING_IN_PLACE_MEMBER("ReadStringInPlaceMember"),
    FUN_READ_OBJECT_MEMBER("ReadObjectMember"),
    FUN_READ_OBJECT_EX_MEMBER("ReadObjectExMember"),
    FUN_READ_CHAR_MEMBER("ReadCharMember"),
    FUN_READ_UBYTE_MEMBER("ReadUByteMember"),
    FUN_READ_USHORT_MEMBER("ReadUShortMember"),
    FUN_READ_UINT_MEMBER("ReadUIntMember"),
    FUN_READ_ULONG_MEMBER("ReadULongMember"),
    FUN_READ_LONGLONG_MEMBER("ReadLongLongMember"),
    FUN_READ_ULONGLONG_MEMBER("ReadULongLongMember"),
    FUN_READ_PYSSIZET_MEMBER("ReadPySSizeT"),

    /* C functions for writing native members by offset */

    FUN_WRITE_SHORT_MEMBER("WriteShortMember"),
    FUN_WRITE_INT_MEMBER("WriteIntMember"),
    FUN_WRITE_LONG_MEMBER("WriteLongMember"),
    FUN_WRITE_FLOAT_MEMBER("WriteFloatMember"),
    FUN_WRITE_DOUBLE_MEMBER("WriteDoubleMember"),
    FUN_WRITE_STRING_MEMBER("WriteStringMember"),
    FUN_WRITE_STRING_IN_PLACE_MEMBER("WriteStringInPlaceMember"),
    FUN_WRITE_OBJECT_MEMBER("WriteObjectMember"),
    FUN_WRITE_OBJECT_EX_MEMBER("WriteObjectExMember"),
    FUN_WRITE_CHAR_MEMBER("WriteCharMember"),
    FUN_WRITE_UBYTE_MEMBER("WriteUByteMember"),
    FUN_WRITE_USHORT_MEMBER("WriteUShortMember"),
    FUN_WRITE_UINT_MEMBER("WriteUIntMember"),
    FUN_WRITE_ULONG_MEMBER("WriteULongMember"),
    FUN_WRITE_LONGLONG_MEMBER("WriteLongLongMember"),
    FUN_WRITE_ULONGLONG_MEMBER("WriteULongLongMember"),
    FUN_WRITE_PYSSIZET_MEMBER("WritePySSizeT"),

    /* Python C API functions */

    FUN_PY_TRUFFLE_STRING_TO_CSTR("PyTruffle_StringToCstr"),
    FUN_WHCAR_SIZE("PyTruffle_Wchar_Size"),
    FUN_PY_TRUFFLE_CSTR_TO_STRING("PyTruffle_CstrToString"),
    FUN_PY_TRUFFLE_ASCII_TO_STRING("PyTruffle_AsciiToString"),
    FUN_PY_FLOAT_AS_DOUBLE("truffle_read_ob_fval"),
    FUN_GET_OB_TYPE("get_ob_type"),
    FUN_GET_OB_REFCNT("get_ob_refcnt"),
    FUN_GET_OB_SIZE("get_ob_size"),
    FUN_GET_TP_DICT("get_tp_dict"),
    FUN_GET_TP_BASE("get_tp_base"),
    FUN_GET_TP_BASES("get_tp_bases"),
    FUN_GET_TP_NAME("get_tp_name"),
    FUN_GET_TP_MRO("get_tp_mro"),
    FUN_GET_TP_ALLOC("get_tp_alloc"),
    FUN_GET_TP_DEALLOC("get_tp_dealloc"),
    FUN_GET_TP_FREE("get_tp_free"),
    FUN_GET_TP_FLAGS("get_tp_flags"),
    FUN_GET_TP_SUBCLASSES("get_tp_subclasses"),
    FUN_GET_TP_DICTOFFSET("get_tp_dictoffset"),
    FUN_GET_TP_WEAKLISTOFFSET("get_tp_weaklistoffset"),
    FUN_GET_TP_VECTORCALLOFFSET("get_tp_vectorcall_offset"),
    FUN_GET_TP_BASICSIZE("get_tp_basicsize"),
    FUN_GET_TP_ITEMSIZE("get_tp_itemsize"),
    FUN_GET_TP_AS_BUFFER("get_tp_as_buffer"),
    FUN_GET_OB_SVAL("get_ob_sval"),
    FUN_GET_METHODS_FLAGS("get_methods_flags"),
    FUN_GET_M_INDEX("get_m_index"),
    FUN_GET_M_SIZE("get_m_size"),
    FUN_GET_M_DOC("get_m_doc"),
    FUN_GET_TP_REPR("get_tp_repr"),
    FUN_GET_TP_STR("get_tp_str"),
    FUN_GET_TP_GETATTRO("get_tp_getattro"),
    FUN_GET_TP_SETATTRO("get_tp_setattro"),
    FUN_GET_TP_HASH("get_tp_hash"),
    FUN_GET_TP_CALL("get_tp_call"),
    FUN_GET_TP_ITER("get_tp_iter"),
    FUN_GET_TP_ITERNEXT("get_tp_iternext"),
    FUN_GET_TP_DESCR_GET("get_tp_descr_get"),
    FUN_GET_TP_DESCR_SET("get_tp_descr_set"),
    FUN_GET_TP_INIT("get_tp_init"),
    FUN_GET_TP_RICHCOMPARE("get_tp_richcompare"),
    FUN_GET_NB_ADD("get_nb_add"),
    FUN_GET_NB_SUBTRACT("get_nb_subtract"),
    FUN_GET_NB_MULTIPLY("get_nb_multiply"),
    FUN_GET_NB_REMAINDER("get_nb_remainder"),
    FUN_GET_NB_DIVMOD("get_nb_divmod"),
    FUN_GET_NB_POWER("get_nb_power"),
    FUN_GET_NB_NEGATIVE("get_nb_negative"),
    FUN_GET_NB_POSITIVE("get_nb_positive"),
    FUN_GET_NB_ABSOLUTE("get_nb_absolute"),
    FUN_GET_NB_BOOL("get_nb_bool"),
    FUN_GET_NB_INVERT("get_nb_invert"),
    FUN_GET_NB_LSHIFT("get_nb_lshift"),
    FUN_GET_NB_RSHIFT("get_nb_rshift"),
    FUN_GET_NB_AND("get_nb_and"),
    FUN_GET_NB_XOR("get_nb_xor"),
    FUN_GET_NB_OR("get_nb_or"),
    FUN_GET_NB_INT("get_nb_int"),
    FUN_GET_NB_FLOAT("get_nb_float"),
    FUN_GET_NB_INPLACE_ADD("get_nb_inplace_add"),
    FUN_GET_NB_INPLACE_SUBTRACT("get_nb_inplace_subtract"),
    FUN_GET_NB_INPLACE_MULTIPLY("get_nb_inplace_multiply"),
    FUN_GET_NB_INPLACE_REMAINDER("get_nb_inplace_remainder"),
    FUN_GET_NB_INPLACE_POWER("get_nb_inplace_power"),
    FUN_GET_NB_INPLACE_LSHIFT("get_nb_inplace_lshift"),
    FUN_GET_NB_INPLACE_RSHIFT("get_nb_inplace_rshift"),
    FUN_GET_NB_INPLACE_AND("get_nb_inplace_and"),
    FUN_GET_NB_INPLACE_XOR("get_nb_inplace_xor"),
    FUN_GET_NB_INPLACE_OR("get_nb_inplace_or"),
    FUN_GET_NB_FLOOR_DIVIDE("get_nb_floor_divide"),
    FUN_GET_NB_TRUE_DIVIDE("get_nb_true_divide"),
    FUN_GET_NB_INPLACE_FLOOR_DIVIDE("get_nb_inplace_floor_divide"),
    FUN_GET_NB_INPLACE_TRUE_DIVIDE("get_nb_inplace_true_divide"),
    FUN_GET_NB_INDEX("get_nb_index"),
    FUN_GET_NB_MATRIX_MULTIPLY("get_nb_matrix_multiply"),
    FUN_GET_NB_INPLACE_MATRIX_MULTIPLY("get_nb_inplace_matrix_multiply"),
    FUN_GET_SQ_LENGTH("get_sq_length"),
    FUN_GET_SQ_CONCAT("get_sq_concat"),
    FUN_GET_SQ_REPEAT("get_sq_repeat"),
    FUN_GET_SQ_ITEM("get_sq_item"),
    FUN_GET_SQ_ASS_ITEM("get_sq_ass_item"),
    FUN_GET_SQ_CONTAINS("get_sq_contains"),
    FUN_GET_SQ_INPLACE_CONCAT("get_sq_inplace_concat"),
    FUN_GET_SQ_INPLACE_REPEAT("get_sq_inplace_repeat"),
    FUN_GET_MP_LENGTH("get_mp_length"),
    FUN_GET_MP_SUBSCRIPT("get_mp_subscript"),
    FUN_GET_MP_ASS_SUBSCRIPT("get_mp_ass_subscript"),
    FUN_GET_PY_COMPLEX_CVAL_REAL("get_cval_real"),
    FUN_GET_PY_COMPLEX_CVAL_IMAG("get_cval_imag"),
    FUN_GET_EXCEPTION_ARGS("get_args"),
    FUN_GET_EXCEPTION_CAUSE("get_cause"),
    FUN_GET_EXCEPTION_CONTEXT("get_context"),
    FUN_GET_EXCEPTION_SUPPRESS_CONTEXT("get_suppress_context"),
    FUN_GET_EXCEPTION_TRACEBACK("get_traceback"),
    FUN_SET_EXCEPTION_ARGS("set_args"),
    FUN_SET_EXCEPTION_CAUSE("set_cause"),
    FUN_SET_EXCEPTION_CONTEXT("set_context"),
    FUN_SET_EXCEPTION_SUPPRESS_CONTEXT("set_suppress_context"),
    FUN_SET_EXCEPTION_TRACEBACK("set_traceback"),
    FUN_GET_PYMODULEDEF_M_METHODS("get_PyModuleDef_m_methods"),
    FUN_GET_PYMODULEDEF_M_SLOTS("get_PyModuleDef_m_slots"),
    FUN_GET_BYTE_ARRAY_TYPE_ID("get_byte_array_typeid"),
    FUN_GET_PTR_ARRAY_TYPE_ID("get_ptr_array_typeid"),
    FUN_PTR_COMPARE("truffle_ptr_compare"),
    FUN_PTR_ADD("truffle_ptr_add"),
    FUN_PTR_CONVERT("truffle_ptr_convert"),
    FUN_PY_TRUFFLE_BYTE_ARRAY_TO_NATIVE("PyTruffle_ByteArrayToNative"),
    FUN_PY_TRUFFLE_INT_ARRAY_TO_NATIVE("PyTruffle_IntArrayToNative"),
    FUN_PY_TRUFFLE_LONG_ARRAY_TO_NATIVE("PyTruffle_LongArrayToNative"),
    FUN_PY_TRUFFLE_DOUBLE_ARRAY_TO_NATIVE("PyTruffle_DoubleArrayToNative"),
    FUN_PY_TRUFFLE_OBJECT_ARRAY_TO_NATIVE("PyTruffle_ObjectArrayToNative"),
    FUN_PY_TRUFFLE_BYTE_ARRAY_REALLOC("PyTruffle_ByteArrayRealloc"),
    FUN_PY_TRUFFLE_INT_ARRAY_REALLOC("PyTruffle_IntArrayRealloc"),
    FUN_PY_TRUFFLE_LONG_ARRAY_REALLOC("PyTruffle_LongArrayRealloc"),
    FUN_PY_TRUFFLE_DOUBLE_ARRAY_REALLOC("PyTruffle_DoubleArrayRealloc"),
    FUN_PY_TRUFFLE_OBJECT_ARRAY_REALLOC("PyTruffle_ObjectArrayRealloc"),
    FUN_PY_TRUFFLE_PRIMITIVE_ARRAY_FREE("PyTruffle_PrimitiveArrayFree"),
    FUN_PY_TRUFFLE_OBJECT_ARRAY_FREE("PyTruffle_ObjectArrayFree"),
    FUN_PY_TRUFFLE_SET_STORAGE_ITEM("PyTruffle_SetStorageItem"),
    FUN_PY_TRUFFLE_INITIALIZE_STORAGE_ITEM("PyTruffle_InitializeStorageItem"),
    FUN_PY_TRUFFLE_NATIVE_LIST_ITEMS("PyTruffle_NativeListItems"),
    FUN_PY_TRUFFLE_NATIVE_TUPLE_ITEMS("PyTruffle_NativeTupleItems"),
    FUN_PY_TRUFFLE_NATIVE_BYTES_ITEMS("PyTruffle_NativeBytesItems"),
    FUN_PY_OBJECT_GENERIC_GET_DICT("_PyObject_GenericGetDict"),
    FUN_PY_OBJECT_GENERIC_SET_DICT("PyObject_GenericSetDict"),
    FUN_PY_OBJECT_NEW("PyTruffle_Object_New"),
    FUN_GET_THREAD_STATE_TYPE_ID("get_thread_state_typeid"),
    FUN_GET_PY_BUFFER_TYPEID("get_Py_buffer_typeid"),
    FUN_ADD_NATIVE_SLOTS("PyTruffle_Type_AddSlots"),
    FUN_PY_TRUFFLE_OBJECT_SIZE("PyTruffle_Object_Size"),
    FUN_PY_TYPE_READY("PyType_Ready"),
    FUN_GET_NEWFUNC_TYPE_ID("get_newfunc_typeid"),
    FUN_GET_BUFFER_R("get_buffer_r"),
    FUN_GET_BUFFER_RW("get_buffer_rw"),
    FUN_CONVERTBUFFER("convertbuffer"),
    FUN_NATIVE_UNICODE_AS_STRING("native_unicode_as_string"),
    FUN_PY_UNICODE_GET_LENGTH("PyUnicode_GetLength"),
    FUN_GET_UINT32_T_ARRAY_TYPE_ID("get_uint32_t_array_typeid"),
    FUN_PYMEM_RAWCALLOC("PyMem_RawCalloc"),
    FUN_PY_TRUFFLE_FREE("PyTruffle_Free"),
    FUN_INCREF("Py_IncRef"),
    FUN_DECREF("Py_DecRef"),
    FUN_ADDREF("PyTruffle_ADDREF"),
    FUN_SUBREF("PyTruffle_SUBREF"),
    FUN_GET_LONG_BITS_PER_DIGIT("get_long_bits_in_digit"),
    FUN_BULK_DEALLOC("PyTruffle_bulk_DEALLOC"),
    FUN_TRUFFLE_ADD_SUBOFFSET("truffle_add_suboffset"),
    FUN_PY_TRUFFLE_PY_MAPPING_CHECK("PyTruffle_PyMapping_Check"),
    FUN_PY_TRUFFLE_PY_MAPPING_SIZE("PyTruffle_PyMapping_Size"),
    FUN_PY_TRUFFLE_MEMORYVIEW_FROM_BUFFER("PyTruffle_MemoryViewFromBuffer"),
    FUN_PY_TRUFFLE_MEMORYVIEW_FROM_OBJECT("PyTruffle_MemoryViewFromObject"),
    FUN_PY_TRUFFLE_PY_OBJECT_SIZE("PyTruffle_PyObject_Size"),
    FUN_PY_TRUFFLE_RELEASE_BUFFER("PyTruffle_ReleaseBuffer"),
    FUN_PY_TRUFFLE_PY_SEQUENCE_CHECK("PyTruffle_PySequence_Check"),
    FUN_PY_TRUFFLE_PY_SEQUENCE_SIZE("PyTruffle_PySequence_Size"),
    FUN_PY_TRUFFLE_PY_SEQUENCE_GET_ITEM("PyTruffle_PySequence_GetItem"),
    FUN_GET_PY_METHOD_DEF_TYPEID("get_PyMethodDef_typeid"),
    FUN_GET_INT_T_TYPEID("get_int_t_typeid"),
    FUN_GET_INT8_T_TYPEID("get_int8_t_typeid"),
    FUN_GET_INT16_T_TYPEID("get_int16_t_typeid"),
    FUN_GET_INT32_T_TYPEID("get_int32_t_typeid"),
    FUN_GET_INT64_T_TYPEID("get_int64_t_typeid"),
    FUN_GET_UINT_T_TYPEID("get_uint_t_typeid"),
    FUN_GET_UINT8_T_TYPEID("get_uint8_t_typeid"),
    FUN_GET_UINT16_T_TYPEID("get_uint16_t_typeid"),
    FUN_GET_UINT32_T_TYPEID("get_uint32_t_typeid"),
    FUN_GET_UINT64_T_TYPEID("get_uint64_t_typeid"),
    FUN_GET_LONG_T_TYPEID("get_long_t_typeid"),
    FUN_GET_ULONG_T_TYPEID("get_ulong_t_typeid"),
    FUN_GET_LONGLONG_T_TYPEID("get_longlong_t_typeid"),
    FUN_GET_ULONGLONG_T_TYPEID("get_ulonglong_t_typeid"),
    FUN_GET_PY_COMPLEX_TYPEID("get_Py_complex_typeid"),
    FUN_GET_FLOAT_T_TYPEID("get_float_t_typeid"),
    FUN_GET_DOUBLE_T_TYPEID("get_double_t_typeid"),
    FUN_GET_SIZE_T_TYPEID("get_size_t_typeid"),
    FUN_GET_PY_SSIZE_T_TYPEID("get_Py_ssize_t_typeid"),
    FUN_GET_PYOBJECT_TYPEID("get_PyObject_typeid"),
    FUN_GET_PYTYPEOBJECT_TYPEID("get_PyTypeObject_typeid"),
    FUN_GET_PYOBJECT_PTR_T_TYPEID("get_PyObject_ptr_t_typeid"),
    FUN_GET_PYOBJECT_PTR_PTR_T_TYPEID("get_PyObject_ptr_ptr_t_typeid"),
    FUN_GET_CHAR_PTR_T_TYPEID("get_char_ptr_t_typeid"),
    FUN_GET_VOID_PTR_T_TYPEID("get_void_ptr_t_typeid"),
    FUN_GET_INT8_PTR_T_TYPEID("get_int8_ptr_t_typeid"),
    FUN_GET_INT16_PTR_T_TYPEID("get_int16_ptr_t_typeid"),
    FUN_GET_INT32_PTR_T_TYPEID("get_int32_ptr_t_typeid"),
    FUN_GET_INT64_PTR_T_TYPEID("get_int64_ptr_t_typeid"),
    FUN_GET_UINT8_PTR_T_TYPEID("get_uint8_ptr_t_typeid"),
    FUN_GET_UINT16_PTR_T_TYPEID("get_uint16_ptr_t_typeid"),
    FUN_GET_UINT32_PTR_T_TYPEID("get_uint32_ptr_t_typeid"),
    FUN_GET_UINT64_PTR_T_TYPEID("get_uint64_ptr_t_typeid"),
    FUN_GET_PY_COMPLEX_PTR_T_TYPEID("get_Py_complex_ptr_t_typeid"),
    FUN_GET_FLOAT_PTR_T_TYPEID("get_float_ptr_t_typeid"),
    FUN_GET_DOUBLE_PTR_T_TYPEID("get_double_ptr_t_typeid"),
    FUN_GET_PY_SSIZE_PTR_T_TYPEID("get_Py_ssize_ptr_t_typeid"),
    FUN_GET_PYTHREADSTATE_TYPEID("get_PyThreadState_typeid"),
    FUN_TUPLE_SUBTYPE_NEW("tuple_subtype_new"),
    FUN_BYTES_SUBTYPE_NEW("bytes_subtype_new"),
    FUN_FLOAT_SUBTYPE_NEW("float_subtype_new"),
    FUN_EXCEPTION_SUBTYPE_NEW("exception_subtype_new"),
    FUN_SUBCLASS_CHECK("truffle_subclass_check"),
    FUN_BASETYPE_CHECK("truffle_BASETYPE_check"),
    FUN_MEMCPY_BYTES("truffle_memcpy_bytes"),
    FUN_UNICODE_SUBTYPE_NEW("unicode_subtype_new"),
    FUN_CHECK_BASESIZE_FOR_GETSTATE("tuffle_check_basesize_for_getstate"),
    FUN_TRUFFLE_SET_TP_FLAGS("truffle_set_tp_flags"),
    FUN_TRUFFLE_CHECK_TYPE_READY("truffle_check_type_ready"),

    /* PyDateTime_CAPI */

    FUN_SET_PY_DATETIME_IDS("set_PyDateTime_typeids"),
    FUN_GET_DATETIME_DATE_BASICSIZE("get_PyDateTime_Date_basicsize"),
    FUN_GET_DATETIME_TIME_BASICSIZE("get_PyDateTime_Time_basicsize"),
    FUN_GET_DATETIME_DATETIME_BASICSIZE("get_PyDateTime_DateTime_basicsize"),
    FUN_GET_DATETIME_DELTA_BASICSIZE("get_PyDateTime_Delta_basicsize"),

    // ctypes
    FUN_STRLEN("strlen"),
    FUN_MEMCPY("memcpy"),
    FUN_MEMMOVE("memmove"),
    FUN_MEMSET("memset"),
    FUN_STRING_AT("string_at"),
    FUN_CAST("cast"),
    FUN_WSTRING_AT("wstring_at");

    private final String name;
    private final TruffleString tsName;

    @CompilationFinal(dimensions = 1) private static final NativeCAPISymbol[] VALUES = values();
    private static final HashMap<String, NativeCAPISymbol> MAP = new HashMap<>();

    private NativeCAPISymbol(String name) {
        this.name = name;
        this.tsName = toTruffleStringUncached(name);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public TruffleString getTsName() {
        return tsName;
    }

    public static NativeCAPISymbol getByName(String name) {
        CompilerAsserts.neverPartOfCompilation();
        return MAP.get(name);
    }

    public static NativeCAPISymbol[] getValues() {
        return VALUES;
    }

    static {
        for (var symbol : VALUES) {
            assert !MAP.containsKey(symbol.name);
            MAP.put(symbol.name, symbol);
        }
    }
}
