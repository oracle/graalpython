/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.INT64_T;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Int;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.IterResult;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PY_SSIZE_T_PTR;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Pointer;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObjectTransfer;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyThreadState;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyTypeObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Py_ssize_t;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.SIZE_T;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Void;
import static com.oracle.graal.python.util.PythonUtils.toTruffleStringUncached;

import com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor;
import com.oracle.graal.python.builtins.objects.cext.common.NativeCExtSymbol;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.strings.TruffleString;

public enum NativeCAPISymbol implements NativeCExtSymbol {

    FUN_VA_ARG_POINTER("GraalPyPrivate_VaArgPointer", Pointer, Pointer),
    FUN_CONVERT_POINTER("GraalPyPrivate_ConvertPointer", Pointer, Py_ssize_t),
    FUN_NO_OP_CLEAR("GraalPyPrivate_NoOpClear", Int, PyObject),
    FUN_NO_OP_TRAVERSE("GraalPyPrivate_NoOpTraverse", Int, PyObject, Pointer, Pointer),

    FUN_PYTRUFFLE_CONSTANTS("GraalPyPrivate_Constants", PY_SSIZE_T_PTR),
    FUN_PYTRUFFLE_STRUCT_OFFSETS("GraalPyPrivate_StructOffsets", PY_SSIZE_T_PTR),
    FUN_PYTRUFFLE_STRUCT_SIZES("GraalPyPrivate_StructSizes", PY_SSIZE_T_PTR),

    /* C functions for reading native members by offset */

    FUN_READ_SHORT_MEMBER("GraalPyPrivate_ReadShortMember", Int, Pointer, Py_ssize_t),
    FUN_READ_INT_MEMBER("GraalPyPrivate_ReadIntMember", Int, Pointer, Py_ssize_t),
    FUN_READ_LONG_MEMBER("GraalPyPrivate_ReadLongMember", ArgDescriptor.Long, Pointer, Py_ssize_t),
    FUN_READ_FLOAT_MEMBER("GraalPyPrivate_ReadFloatMember", ArgDescriptor.Double, Pointer, Py_ssize_t),
    FUN_READ_DOUBLE_MEMBER("GraalPyPrivate_ReadDoubleMember", ArgDescriptor.Double, Pointer, Py_ssize_t),
    FUN_READ_POINTER_MEMBER("GraalPyPrivate_ReadPointerMember", Pointer, Pointer, Py_ssize_t),
    FUN_READ_CHAR_MEMBER("GraalPyPrivate_ReadCharMember", Int, Pointer, Py_ssize_t),

    /* C functions for writing native members by offset */

    FUN_WRITE_SHORT_MEMBER("GraalPyPrivate_WriteShortMember", Int, Pointer, Py_ssize_t, Int),
    FUN_WRITE_INT_MEMBER("GraalPyPrivate_WriteIntMember", Int, Pointer, Py_ssize_t, Int),
    FUN_WRITE_LONG_MEMBER("GraalPyPrivate_WriteLongMember", Int, Pointer, Py_ssize_t, ArgDescriptor.Long),
    FUN_WRITE_FLOAT_MEMBER("GraalPyPrivate_WriteFloatMember", Int, Pointer, Py_ssize_t, ArgDescriptor.Double),
    FUN_WRITE_DOUBLE_MEMBER("GraalPyPrivate_WriteDoubleMember", Int, Pointer, Py_ssize_t, ArgDescriptor.Double),
    FUN_WRITE_OBJECT_MEMBER("GraalPyPrivate_WriteObjectMember", Int, Pointer, Py_ssize_t, Pointer),
    FUN_WRITE_POINTER_MEMBER("GraalPyPrivate_WritePointerMember", Int, Pointer, Py_ssize_t, Pointer),
    FUN_WRITE_CHAR_MEMBER("GraalPyPrivate_WriteByteMember", Int, Pointer, Py_ssize_t, Int),

    /* Python C API functions */

    FUN_PY_TYPE_READY("PyType_Ready", Int, PyTypeObject),
    FUN_PY_OBJECT_FREE("PyObject_Free", Void, Pointer),
    FUN_PY_OBJECT_GENERIC_SET_DICT("PyObject_GenericSetDict", Int, PyObject, PyObject, Pointer),
    FUN_PY_TYPE_GENERIC_ALLOC("PyType_GenericAlloc", PyObjectTransfer, PyTypeObject, Py_ssize_t),
    FUN_PY_OBJECT_GET_DICT_PTR("_PyObject_GetDictPtr", Pointer, PyObject),
    FUN_PY_UNICODE_GET_LENGTH("PyUnicode_GetLength", Py_ssize_t, PyObject),
    FUN_PYMEM_ALLOC("PyMem_Calloc", Pointer, SIZE_T, SIZE_T),
    FUN_PY_DEALLOC("_Py_Dealloc", Void, Pointer),
    FUN_PYOBJECT_HASH_NOT_IMPLEMENTED("PyObject_HashNotImplemented", ArgDescriptor.Py_hash_t, PyObject),
    FUN_PY_GC_COLLECT_NO_FAIL("_PyGC_CollectNoFail", Py_ssize_t, PyThreadState),
    FUN_PY_OBJECT_NEXT_NOT_IMPLEMENTED("_PyObject_NextNotImplemented", IterResult, PyObject),

    /* GraalPy-specific helper functions */
    FUN_PTR_COMPARE("GraalPyPrivate_PointerCompare", Int, Pointer, Pointer, Int),
    FUN_PTR_ADD("GraalPyPrivate_PointerAddOffset", Pointer, Pointer, Py_ssize_t),
    FUN_OBJECT_ARRAY_RELEASE("GraalPyPrivate_ObjectArrayRelease", ArgDescriptor.Void, Pointer, Int),
    FUN_PY_OBJECT_NEW("GraalPyPrivate_ObjectNew", PyObjectTransfer, PyTypeObject),
    FUN_GRAALPY_OBJECT_GC_DEL("GraalPyPrivate_Object_GC_Del", Void, Pointer),
    FUN_BULK_DEALLOC("GraalPyPrivate_BulkDealloc", Py_ssize_t, Pointer, INT64_T),
    FUN_SHUTDOWN_BULK_DEALLOC("GraalPyPrivate_BulkDeallocOnShutdown", Py_ssize_t, Pointer, INT64_T),
    FUN_GET_CURRENT_RSS("GraalPyPrivate_GetCurrentRSS", SIZE_T),
    FUN_ADD_SUBOFFSET("GraalPyPrivate_AddSuboffset", Pointer, Pointer, Py_ssize_t, Py_ssize_t),
    FUN_GRAALPY_MEMORYVIEW_FROM_OBJECT("GraalPyPrivate_MemoryViewFromObject", PyObjectTransfer, PyObject, Int),
    FUN_GRAALPY_RELEASE_BUFFER("GraalPyPrivate_ReleaseBuffer", ArgDescriptor.Void, Pointer),
    FUN_GRAALPY_CAPSULE_CALL_DESTRUCTOR("GraalPyPrivate_Capsule_CallDestructor", ArgDescriptor.Void, PyObject, ArgDescriptor.PY_CAPSULE_DESTRUCTOR),
    FUN_TUPLE_SUBTYPE_NEW("GraalPyPrivate_Tuple_SubtypeNew", PyObjectTransfer, PyTypeObject, PyObject),
    FUN_BYTES_SUBTYPE_NEW("GraalPyPrivate_Bytes_SubtypeNew", PyObjectTransfer, PyTypeObject, Pointer, Py_ssize_t),
    FUN_FLOAT_SUBTYPE_NEW("GraalPyPrivate_Float_SubtypeNew", PyObjectTransfer, PyTypeObject, ArgDescriptor.Double),
    FUN_COMPLEX_SUBTYPE_FROM_DOUBLES("GraalPyPrivate_Complex_SubtypeFromDoubles", PyObjectTransfer, PyTypeObject, ArgDescriptor.Double, ArgDescriptor.Double),
    FUN_EXCEPTION_SUBTYPE_NEW("GraalPyPrivate_Exception_SubtypeNew", PyObjectTransfer, PyTypeObject, PyObject),
    FUN_SUBCLASS_CHECK("GraalPyPrivate_SubclassCheck", Int, PyObject),
    FUN_UNICODE_SUBTYPE_NEW("GraalPyPrivate_Unicode_SubtypeNew", PyObjectTransfer, PyTypeObject, PyObject),
    FUN_CHECK_BASICSIZE_FOR_GETSTATE("GraalPyPrivate_CheckBasicsizeForGetstate", Int, PyTypeObject, Int),
    FUN_MMAP_INIT_BUFFERPROTOCOL("GraalPyPrivate_MMap_InitBufferProtocol", ArgDescriptor.Void, PyTypeObject),
    FUN_PY_TRUFFLE_CDATA_INIT_BUFFER_PROTOCOL("GraalPyPrivate_CData_InitBufferProtocol", ArgDescriptor.Void, PyTypeObject),
    FUN_TRUFFLE_CHECK_TYPE_READY("GraalPyPrivate_CheckTypeReady", ArgDescriptor.Void, PyTypeObject),
    FUN_GRAALPY_GC_COLLECT("GraalPyPrivate_GC_Collect", Py_ssize_t, Int),
    FUN_SUBTYPE_TRAVERSE("GraalPyPrivate_SubtypeTraverse", Int, PyObject, Pointer, Pointer),

    /* PyDateTime_CAPI */

    FUN_INIT_NATIVE_DATETIME("GraalPyPrivate_InitNativeDateTime", ArgDescriptor.Void),

    // ctypes
    FUN_FREE("free", Void, Pointer),
    FUN_MEMMOVE("memmove", Pointer, Pointer, Pointer, SIZE_T),
    FUN_MEMSET("memset", Pointer, Pointer, Int, SIZE_T),
    FUN_CALLOC("calloc", Pointer, SIZE_T),
    FUN_STRING_AT("string_at"),
    FUN_CAST("cast"),
    FUN_WSTRING_AT("wstring_at");

    private final String name;
    private final TruffleString tsName;

    private final String signature;

    @CompilationFinal(dimensions = 1) private static final NativeCAPISymbol[] VALUES = values();

    NativeCAPISymbol(String name, ArgDescriptor returnValue, ArgDescriptor... arguments) {
        this.name = name;
        this.tsName = toTruffleStringUncached(name);

        StringBuilder s = new StringBuilder("(");
        for (int i = 0; i < arguments.length; i++) {
            s.append(i == 0 ? "" : ",");
            s.append(arguments[i].getNFISignature());
        }
        s.append("):").append(returnValue.getNFISignature());
        this.signature = s.toString();
    }

    NativeCAPISymbol(String name) {
        this.name = name;
        this.tsName = toTruffleStringUncached(name);
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

    public static NativeCAPISymbol[] getValues() {
        return VALUES;
    }

    public String getSignature() {
        assert signature != null : "no signature for " + this;
        return signature;
    }
}
