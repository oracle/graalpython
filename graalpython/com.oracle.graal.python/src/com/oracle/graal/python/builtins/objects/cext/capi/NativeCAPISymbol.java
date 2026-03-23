/*
 * Copyright (c) 2018, 2026, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor;
import com.oracle.graal.python.builtins.objects.cext.common.NativeCExtSymbol;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.strings.TruffleString;

public enum NativeCAPISymbol implements NativeCExtSymbol {

    FUN_NO_OP_CLEAR("GraalPyPrivate_NoOpClear", ExternalFunctionSignature.NO_OP_CLEAR),
    FUN_NO_OP_TRAVERSE("GraalPyPrivate_NoOpTraverse", ExternalFunctionSignature.NO_OP_TRAVERSE),

    FUN_PYTRUFFLE_CONSTANTS("GraalPyPrivate_Constants", ExternalFunctionSignature.PYTRUFFLE_CONSTANTS),
    FUN_PYTRUFFLE_STRUCT_OFFSETS("GraalPyPrivate_StructOffsets", ExternalFunctionSignature.PYTRUFFLE_STRUCT_OFFSETS),
    FUN_PYTRUFFLE_STRUCT_SIZES("GraalPyPrivate_StructSizes", ExternalFunctionSignature.PYTRUFFLE_STRUCT_SIZES),

    /* Python C API functions */

    FUN_PY_TYPE_READY("PyType_Ready", ExternalFunctionSignature.PY_TYPE_READY),
    FUN_PY_OBJECT_FREE("PyObject_Free", ExternalFunctionSignature.PY_OBJECT_FREE),
    FUN_PY_OBJECT_GENERIC_SET_DICT("PyObject_GenericSetDict", ExternalFunctionSignature.PY_OBJECT_GENERIC_SET_DICT),
    FUN_PY_TYPE_GENERIC_NEW("PyType_GenericNew", ExternalFunctionSignature.PY_TYPE_GENERIC_NEW),
    FUN_PY_TYPE_GENERIC_NEW_RAW("PyType_GenericNew", ExternalFunctionSignature.PY_TYPE_GENERIC_NEW_RAW),
    FUN_PY_TYPE_GENERIC_ALLOC("PyType_GenericAlloc", ExternalFunctionSignature.TYPE_GENERIC_ALLOC),
    FUN_PY_OBJECT_GET_DICT_PTR("_PyObject_GetDictPtr", ExternalFunctionSignature.GETDICTPTRFUN),
    FUN_PY_UNICODE_GET_LENGTH("PyUnicode_GetLength", ExternalFunctionSignature.PY_UNICODE_GET_LENGTH),
    FUN_PYMEM_ALLOC("PyMem_Calloc", ExternalFunctionSignature.PYMEM_ALLOC),
    FUN_PY_DEALLOC("_Py_Dealloc", ExternalFunctionSignature.PY_DEALLOC),
    FUN_PYOBJECT_HASH_NOT_IMPLEMENTED("PyObject_HashNotImplemented", ExternalFunctionSignature.PYOBJECT_HASH_NOT_IMPLEMENTED),
    FUN_PY_GC_COLLECT_NO_FAIL("_PyGC_CollectNoFail", ExternalFunctionSignature.PY_GC_COLLECT_NO_FAIL),
    FUN_PY_OBJECT_NEXT_NOT_IMPLEMENTED("_PyObject_NextNotImplemented", ExternalFunctionSignature.PY_OBJECT_NEXT_NOT_IMPLEMENTED),

    /* GraalPy-specific helper functions */
    FUN_OBJECT_ARRAY_RELEASE("GraalPyPrivate_ObjectArrayRelease", ExternalFunctionSignature.OBJECT_ARRAY_RELEASE),
    FUN_PY_OBJECT_NEW("GraalPyPrivate_ObjectNew", ExternalFunctionSignature.PY_OBJECT_NEW),
    FUN_GRAALPY_OBJECT_GC_DEL("GraalPyPrivate_Object_GC_Del", ExternalFunctionSignature.GRAALPY_OBJECT_GC_DEL),
    FUN_BULK_DEALLOC("GraalPyPrivate_BulkDealloc", ExternalFunctionSignature.BULK_DEALLOC),
    FUN_SHUTDOWN_BULK_DEALLOC("GraalPyPrivate_BulkDeallocOnShutdown", ExternalFunctionSignature.SHUTDOWN_BULK_DEALLOC),
    FUN_GET_CURRENT_RSS("GraalPyPrivate_GetCurrentRSS", ExternalFunctionSignature.GET_CURRENT_RSS),
    FUN_GRAALPY_MEMORYVIEW_FROM_OBJECT("GraalPyPrivate_MemoryViewFromObject", ExternalFunctionSignature.GRAALPY_MEMORYVIEW_FROM_OBJECT),
    FUN_GRAALPY_RELEASE_BUFFER("GraalPyPrivate_ReleaseBuffer", ExternalFunctionSignature.GRAALPY_RELEASE_BUFFER),
    FUN_GRAALPY_CAPSULE_CALL_DESTRUCTOR("GraalPyPrivate_Capsule_CallDestructor", ExternalFunctionSignature.GRAALPY_CAPSULE_CALL_DESTRUCTOR),
    FUN_TUPLE_SUBTYPE_NEW("GraalPyPrivate_Tuple_SubtypeNew", ExternalFunctionSignature.TUPLE_SUBTYPE_NEW),
    FUN_BYTES_SUBTYPE_NEW("GraalPyPrivate_Bytes_SubtypeNew", ExternalFunctionSignature.BYTES_SUBTYPE_NEW),
    FUN_FLOAT_SUBTYPE_NEW("GraalPyPrivate_Float_SubtypeNew", ExternalFunctionSignature.FLOAT_SUBTYPE_NEW),
    FUN_COMPLEX_SUBTYPE_FROM_DOUBLES("GraalPyPrivate_Complex_SubtypeFromDoubles", ExternalFunctionSignature.COMPLEX_SUBTYPE_FROM_DOUBLES),
    FUN_TIME_SUBTYPE_NEW("GraalPyPrivate_Time_SubtypeNew", ExternalFunctionSignature.TIME_SUBTYPE_NEW),
    FUN_DATE_SUBTYPE_NEW("GraalPyPrivate_Date_SubtypeNew", ExternalFunctionSignature.DATE_SUBTYPE_NEW),
    FUN_TIMEDELTA_SUBTYPE_NEW("GraalPyPrivate_TimeDelta_SubtypeNew", ExternalFunctionSignature.TIMEDELTA_SUBTYPE_NEW),
    FUN_DATETIME_SUBTYPE_NEW("GraalPyPrivate_DateTime_SubtypeNew", ExternalFunctionSignature.DATETIME_SUBTYPE_NEW),
    FUN_EXCEPTION_SUBTYPE_NEW("GraalPyPrivate_Exception_SubtypeNew", ExternalFunctionSignature.EXCEPTION_SUBTYPE_NEW),
    FUN_UNICODE_SUBTYPE_NEW("GraalPyPrivate_Unicode_SubtypeNew", ExternalFunctionSignature.UNICODE_SUBTYPE_NEW),
    FUN_CHECK_BASICSIZE_FOR_GETSTATE("GraalPyPrivate_CheckBasicsizeForGetstate", ExternalFunctionSignature.CHECK_BASICSIZE_FOR_GETSTATE),
    FUN_MMAP_INIT_BUFFERPROTOCOL("GraalPyPrivate_MMap_InitBufferProtocol", ExternalFunctionSignature.MMAP_INIT_BUFFERPROTOCOL),
    FUN_TRUFFLE_CHECK_TYPE_READY("GraalPyPrivate_CheckTypeReady", ExternalFunctionSignature.TRUFFLE_CHECK_TYPE_READY),
    FUN_GRAALPY_GC_COLLECT("GraalPyPrivate_GC_Collect", ExternalFunctionSignature.GCCOLLECT),
    FUN_SUBTYPE_TRAVERSE("GraalPyPrivate_SubtypeTraverse", ExternalFunctionSignature.SUBTYPE_TRAVERSE),
    FUN_INIT_THREAD_STATE_CURRENT("GraalPyPrivate_InitThreadStateCurrent", ExternalFunctionSignature.INIT_THREAD_STATE_CURRENT),

    /* PyDateTime_CAPI */

    FUN_INIT_NATIVE_DATETIME("GraalPyPrivate_InitNativeDateTime", ExternalFunctionSignature.INIT_NATIVE_DATETIME);

    private final String name;
    private final TruffleString tsName;
    private final ExternalFunctionSignature signature;

    @CompilationFinal(dimensions = 1) private static final NativeCAPISymbol[] VALUES = values();

    NativeCAPISymbol(String name, ExternalFunctionSignature signature) {
        this.name = name;
        this.tsName = toTruffleStringUncached(name);
        this.signature = signature;
    }

    NativeCAPISymbol(String name) {
        this(name, null);
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

    public ExternalFunctionSignature getSignature() {
        return signature;
    }

    public ArgDescriptor getReturnValue() {
        return signature != null ? signature.getReturnValue() : null;
    }

    @Override
    public ArgDescriptor[] getArguments() {
        assert signature != null : "no signature for " + this;
        return signature.getArguments();
    }
}
