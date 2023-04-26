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

import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.ConstCharPtr;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.ConstCharPtrAsTruffleString;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.INT64_T;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Int;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.LONG_LONG;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PY_SSIZE_T_PTR;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Pointer;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyTypeObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Py_ssize_t;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.SIZE_T;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.UINT64_T;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.UNSIGNED_INT;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.UNSIGNED_LONG;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.UNSIGNED_LONG_LONG;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Void;
import static com.oracle.graal.python.util.PythonUtils.toTruffleStringUncached;

import java.util.HashMap;

import com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor;
import com.oracle.graal.python.builtins.objects.cext.common.NativeCExtSymbol;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.strings.TruffleString;

public enum NativeCAPISymbol implements NativeCExtSymbol {

    FUN_VA_ARG_POINTER("truffle_va_arg_pointer", Pointer, Pointer),

    FUN_PYTRUFFLE_CONSTANTS("PyTruffle_constants", PY_SSIZE_T_PTR),
    FUN_PYTRUFFLE_STRUCT_OFFSETS("PyTruffle_struct_offsets", PY_SSIZE_T_PTR),
    FUN_PYTRUFFLE_STRUCT_SIZES("PyTruffle_struct_sizes", PY_SSIZE_T_PTR),
    FUN_PYTRUFFLE_ADD_OFFSET("PyTruffle_Add_Offset", Pointer, Pointer, ArgDescriptor.Long),

    /* C functions for reading native members by offset */

    FUN_READ_SHORT_MEMBER("ReadShortMember", Int, Pointer, Py_ssize_t),
    FUN_READ_INT_MEMBER("ReadIntMember", Int, Pointer, Py_ssize_t),
    FUN_READ_LONG_MEMBER("ReadLongMember", ArgDescriptor.Long, Pointer, Py_ssize_t),
    FUN_READ_FLOAT_MEMBER("ReadFloatMember", ArgDescriptor.Double, Pointer, Py_ssize_t),
    FUN_READ_DOUBLE_MEMBER("ReadDoubleMember", ArgDescriptor.Double, Pointer, Py_ssize_t),
    FUN_READ_STRING_MEMBER("ReadStringMember", ConstCharPtrAsTruffleString, Pointer, Py_ssize_t),
    FUN_READ_STRING_IN_PLACE_MEMBER("ReadStringInPlaceMember", ConstCharPtrAsTruffleString, Pointer, Py_ssize_t),
    FUN_READ_OBJECT_MEMBER("ReadObjectMember", Pointer, Pointer, Py_ssize_t),
    FUN_READ_POINTER_MEMBER("ReadPointerMember", Pointer, Pointer, Py_ssize_t),
    FUN_READ_OBJECT_EX_MEMBER("ReadObjectExMember", Pointer, Pointer, Py_ssize_t),
    FUN_READ_CHAR_MEMBER("ReadCharMember", Int, Pointer, Py_ssize_t),
    FUN_READ_UBYTE_MEMBER("ReadUByteMember", Int, Pointer, Py_ssize_t),
    FUN_READ_USHORT_MEMBER("ReadUShortMember", Int, Pointer, Py_ssize_t),
    FUN_READ_UINT_MEMBER("ReadUIntMember", ArgDescriptor.Long, Pointer, Py_ssize_t),
    FUN_READ_ULONG_MEMBER("ReadULongMember", ArgDescriptor.UNSIGNED_LONG, Pointer, Py_ssize_t),
    FUN_READ_LONGLONG_MEMBER("ReadLongLongMember", ArgDescriptor.LONG_LONG, Pointer, Py_ssize_t),
    FUN_READ_ULONGLONG_MEMBER("ReadULongLongMember", ArgDescriptor.UNSIGNED_LONG_LONG, Pointer, Py_ssize_t),
    FUN_READ_PYSSIZET_MEMBER("ReadPySSizeT", Py_ssize_t, Pointer, Py_ssize_t),

    /* C functions for writing native members by offset */

    FUN_WRITE_SHORT_MEMBER("WriteShortMember", Int, Pointer, Py_ssize_t, Int),
    FUN_WRITE_INT_MEMBER("WriteIntMember", Int, Pointer, Py_ssize_t, Int),
    FUN_WRITE_LONG_MEMBER("WriteLongMember", Int, Pointer, Py_ssize_t, ArgDescriptor.Long),
    FUN_WRITE_FLOAT_MEMBER("WriteFloatMember", Int, Pointer, Py_ssize_t, ArgDescriptor.Double),
    FUN_WRITE_DOUBLE_MEMBER("WriteDoubleMember", Int, Pointer, Py_ssize_t, ArgDescriptor.Double),
    FUN_WRITE_STRING_MEMBER("WriteStringMember", Int, Pointer, Py_ssize_t, ConstCharPtr),
    FUN_WRITE_STRING_IN_PLACE_MEMBER("WriteStringInPlaceMember", Int, Pointer, Py_ssize_t, ConstCharPtr),
    FUN_WRITE_OBJECT_MEMBER("WriteObjectMember", Int, Pointer, Py_ssize_t, Pointer),
    FUN_WRITE_OBJECT_EX_MEMBER("WriteObjectExMember", Int, Pointer, Py_ssize_t, Pointer),
    FUN_WRITE_CHAR_MEMBER("WriteCharMember", Int, Pointer, Py_ssize_t, Int),
    FUN_WRITE_UBYTE_MEMBER("WriteUByteMember", Int, Pointer, Py_ssize_t, Int),
    FUN_WRITE_USHORT_MEMBER("WriteUShortMember", Int, Pointer, Py_ssize_t, Int),
    FUN_WRITE_UINT_MEMBER("WriteUIntMember", Int, Pointer, Py_ssize_t, UNSIGNED_INT),
    FUN_WRITE_ULONG_MEMBER("WriteULongMember", Int, Pointer, Py_ssize_t, UNSIGNED_LONG),
    FUN_WRITE_LONGLONG_MEMBER("WriteLongLongMember", Int, Pointer, Py_ssize_t, LONG_LONG),
    FUN_WRITE_ULONGLONG_MEMBER("WriteULongLongMember", Int, Pointer, Py_ssize_t, UNSIGNED_LONG_LONG),
    FUN_WRITE_PYSSIZET_MEMBER("WritePySSizeT", Int, Pointer, Py_ssize_t, Py_ssize_t),

    /* Python C API functions */

    FUN_GET_METHODS_FLAGS("get_methods_flags", INT64_T, PyTypeObject),
    FUN_PTR_COMPARE("truffle_ptr_compare", Int, Pointer, Pointer, Int),
    FUN_PTR_ADD("truffle_ptr_add", Pointer, Pointer, Py_ssize_t),
    FUN_PY_TRUFFLE_OBJECT_ARRAY_RELEASE("PyTruffle_ObjectArrayRelease", ArgDescriptor.Void, Pointer, Int),
    FUN_PY_TRUFFLE_SET_STORAGE_ITEM("PyTruffle_SetStorageItem", ArgDescriptor.Void, Pointer, Int, PyObject),
    FUN_PY_TRUFFLE_INITIALIZE_STORAGE_ITEM("PyTruffle_InitializeStorageItem", ArgDescriptor.Void, Pointer, Int, PyObject),
    FUN_PY_OBJECT_GENERIC_GET_DICT("_PyObject_GenericGetDict", PyObject, PyObject),
    FUN_PY_OBJECT_GENERIC_SET_DICT("PyObject_GenericSetDict", Int, PyObject, PyObject, Pointer),
    FUN_PY_OBJECT_NEW("PyTruffle_Object_New", PyObject, PyTypeObject, PyTypeObject, PyObject, PyObject),
    FUN_ADD_NATIVE_SLOTS("PyTruffle_Type_AddSlots", ArgDescriptor.Void, PyTypeObject, Pointer, UINT64_T, Pointer, UINT64_T),
    FUN_PY_TYPE_READY("PyType_Ready", Int, PyTypeObject),
    FUN_GET_BUFFER_R("get_buffer_r", Int, PyObject, Pointer),
    FUN_GET_BUFFER_RW("get_buffer_rw", Int, PyObject, Pointer),
    FUN_CONVERTBUFFER("convertbuffer", Py_ssize_t, PyObject, Pointer),
    FUN_PY_UNICODE_GET_LENGTH("PyUnicode_GetLength", Py_ssize_t, PyObject),
    FUN_PY_TRUFFLE_FREE("PyTruffle_Free", ArgDescriptor.Void, Pointer),
    FUN_PYMEM_ALLOC("PyMem_Calloc", Pointer, SIZE_T, SIZE_T),
    FUN_INCREF("Py_IncRef", Void, Pointer),
    FUN_DECREF("Py_DecRef", Void, Pointer),
    FUN_ADDREF("PyTruffle_ADDREF", Py_ssize_t, Pointer, Py_ssize_t),
    FUN_SUBREF("PyTruffle_SUBREF", Py_ssize_t, Pointer, Py_ssize_t),
    FUN_BULK_DEALLOC("PyTruffle_bulk_DEALLOC", Py_ssize_t, Pointer, INT64_T),
    FUN_TRUFFLE_ADD_SUBOFFSET("truffle_add_suboffset", Pointer, Pointer, Py_ssize_t, Py_ssize_t),
    FUN_PY_TRUFFLE_PY_MAPPING_CHECK("PyTruffle_PyMapping_Check", Int, PyObject),
    FUN_PY_TRUFFLE_PY_MAPPING_SIZE("PyTruffle_PyMapping_Size", Py_ssize_t, PyObject),
    FUN_PY_TRUFFLE_MEMORYVIEW_FROM_OBJECT("PyTruffle_MemoryViewFromObject", PyObject, PyObject, Int),
    FUN_PY_TRUFFLE_PY_OBJECT_SIZE("PyTruffle_PyObject_Size", Py_ssize_t, PyObject),
    FUN_PY_TRUFFLE_RELEASE_BUFFER("PyTruffle_ReleaseBuffer", ArgDescriptor.Void, Pointer),
    FUN_PY_TRUFFLE_PY_SEQUENCE_CHECK("PyTruffle_PySequence_Check", Int, PyObject),
    FUN_PY_TRUFFLE_PY_SEQUENCE_SIZE("PyTruffle_PySequence_Size", Py_ssize_t, PyObject),
    FUN_PY_TRUFFLE_PY_SEQUENCE_GET_ITEM("PyTruffle_PySequence_GetItem", PyObject, PyObject, Py_ssize_t),
    FUN_TUPLE_SUBTYPE_NEW("tuple_subtype_new", PyObject, PyTypeObject, PyObject),
    FUN_BYTES_SUBTYPE_NEW("bytes_subtype_new", PyObject, PyTypeObject, Pointer, Py_ssize_t),
    FUN_FLOAT_SUBTYPE_NEW("float_subtype_new", PyObject, PyTypeObject, ArgDescriptor.Double),
    FUN_EXCEPTION_SUBTYPE_NEW("exception_subtype_new"),
    FUN_SUBCLASS_CHECK("truffle_subclass_check", Int, PyObject),
    FUN_BASETYPE_CHECK("truffle_BASETYPE_check", Int, PyObject),
    FUN_MEMCPY_BYTES("truffle_memcpy_bytes", ArgDescriptor.Void, Pointer, SIZE_T, Pointer, SIZE_T, SIZE_T),
    FUN_UNICODE_SUBTYPE_NEW("unicode_subtype_new", PyObject, PyTypeObject, PyObject),
    FUN_CHECK_BASESIZE_FOR_GETSTATE("tuffle_check_basesize_for_getstate", Int, PyTypeObject, Int),
    FUN_MMAP_INIT_BUFFERPROTOCOL("mmap_init_bufferprotocol", ArgDescriptor.Void, PyTypeObject),
    FUN_TRUFFLE_CHECK_TYPE_READY("truffle_check_type_ready", ArgDescriptor.Void, PyTypeObject),

    /* PyDateTime_CAPI */

    FUN_SET_PY_DATETIME_TYPES("set_PyDateTime_types", ArgDescriptor.Void),

    // ctypes
    FUN_STRLEN("strlen", SIZE_T, Pointer),
    FUN_MEMCPY("memcpy", Pointer, Pointer, Pointer, SIZE_T),
    FUN_FREE("free", ArgDescriptor.Void, Pointer),
    FUN_MEMMOVE("memmove", Pointer, Pointer, Pointer, SIZE_T),
    FUN_MEMSET("memset", Pointer, Pointer, Int, SIZE_T),
    FUN_STRING_AT("string_at"),
    FUN_CAST("cast"),
    FUN_WSTRING_AT("wstring_at");

    private final String name;
    private final TruffleString tsName;

    private final ArgDescriptor returnValue;
    private final ArgDescriptor[] arguments;
    private final String signature;

    @CompilationFinal(dimensions = 1) private static final NativeCAPISymbol[] VALUES = values();
    private static final HashMap<String, NativeCAPISymbol> MAP = new HashMap<>();

    private NativeCAPISymbol(String name, ArgDescriptor returnValue, ArgDescriptor... arguments) {
        this.name = name;
        this.tsName = toTruffleStringUncached(name);
        this.returnValue = returnValue;
        this.arguments = arguments;

        StringBuilder s = new StringBuilder("(");
        for (int i = 0; i < arguments.length; i++) {
            s.append(i == 0 ? "" : ",");
            s.append(arguments[i].getNFISignature());
        }
        s.append("):").append(returnValue.getNFISignature());
        this.signature = s.toString();
    }

    private NativeCAPISymbol(String name) {
        this.name = name;
        this.tsName = toTruffleStringUncached(name);
        this.returnValue = null;
        this.arguments = null;
        this.signature = null;
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

    public String getSignature() {
        assert signature != null : "no signature for " + this;
        return signature;
    }
}
