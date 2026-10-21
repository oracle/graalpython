/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.annotations.ArgDescriptor.CHAR_PTR;
import static com.oracle.graal.python.annotations.ArgDescriptor.ConstCharPtr;
import static com.oracle.graal.python.annotations.ArgDescriptor.ConstPyLongObject;
import static com.oracle.graal.python.annotations.ArgDescriptor.Double;
import static com.oracle.graal.python.annotations.ArgDescriptor.INT64_T;
import static com.oracle.graal.python.annotations.ArgDescriptor.INT8_T_PTR;
import static com.oracle.graal.python.annotations.ArgDescriptor.INTPTR_T_PTR;
import static com.oracle.graal.python.annotations.ArgDescriptor.Int;
import static com.oracle.graal.python.annotations.ArgDescriptor.Long;
import static com.oracle.graal.python.annotations.ArgDescriptor.PY_BUFFER_PTR;
import static com.oracle.graal.python.annotations.ArgDescriptor.PY_CAPSULE_DESTRUCTOR;
import static com.oracle.graal.python.annotations.ArgDescriptor.Pointer;
import static com.oracle.graal.python.annotations.ArgDescriptor.PyObject;
import static com.oracle.graal.python.annotations.ArgDescriptor.PyObjectPtr;
import static com.oracle.graal.python.annotations.ArgDescriptor.PyThreadState;
import static com.oracle.graal.python.annotations.ArgDescriptor.PyThreadStatePtr;
import static com.oracle.graal.python.annotations.ArgDescriptor.PyTypeObject;
import static com.oracle.graal.python.annotations.ArgDescriptor.Py_ssize_t;
import static com.oracle.graal.python.annotations.ArgDescriptor.SIZE_T;
import static com.oracle.graal.python.annotations.ArgDescriptor.UINTPTR_T;
import static com.oracle.graal.python.annotations.ArgDescriptor.VA_LIST;
import static com.oracle.graal.python.annotations.ArgDescriptor.Void;
import static com.oracle.graal.python.annotations.ArgDescriptor.visitproc;
import static com.oracle.graal.python.util.PythonUtils.toTruffleStringUncached;

import com.oracle.graal.python.annotations.ArgDescriptor;
import com.oracle.graal.python.annotations.CApiExternalFunctionSignatures;
import com.oracle.graal.python.builtins.objects.cext.common.NativeCExtSymbol;
import com.oracle.truffle.api.strings.TruffleString;

/**
 * Enum of well-known function and slot signatures. The integer values must stay in sync with the
 * definition in {code capi.h}.
 */
@CApiExternalFunctionSignatures
public enum ExternalFunctionSignature implements NativeCExtSymbol {
    // typedef PyObject *(*PyCFunction)(PyObject *, PyObject *);
    PYCFUNCTION(false, PyObject, PyObject, PyObject),
    // typedef PyObject *(*PyCFunctionWithKeywords)(PyObject *, PyObject *, PyObject *);
    PYCFUNCTION_WITH_KEYWORDS(false, PyObject, PyObject, PyObject, PyObject),
    // typedef PyObject *(*_PyCFunctionFast) (PyObject *, PyObject *const *, Py_ssize_t);
    PYCFUNCTION_FAST(false, PyObject, PyObject, Pointer, Py_ssize_t),
    // typedef PyObject *(*_PyCFunctionFastWithKeywords) (PyObject *, PyObject *const *, Py_ssize_t,
    // PyObject *);
    PYCFUNCTION_FAST_WITH_KEYWORDS(false, PyObject, PyObject, Pointer, Py_ssize_t, PyObject),
    // typedef PyObject *(*PyCMethod)(PyObject *, PyTypeObject *, PyObject *const *, size_t,
    // PyObject *);
    PYCMETHOD(false, PyObject, PyObject, PyTypeObject, Pointer, Py_ssize_t, PyObject),

    // typedef PyObject * (*unaryfunc)(PyObject *);
    UNARYFUNC(false, PyObject, PyObject),
    // typedef PyObject * (*binaryfunc)(PyObject *, PyObject *);
    BINARYFUNC(false, PyObject, PyObject, PyObject),
    // typedef PyObject * (*ternaryfunc)(PyObject *, PyObject *, PyObject *);
    TERNARYFUNC(false, PyObject, PyObject, PyObject, PyObject),
    // typedef int (*inquiry)(PyObject *);
    INQUIRY(false, Int, PyObject),
    // typedef Py_ssize_t (*lenfunc)(PyObject *);
    LENFUNC(false, Long, PyObject),
    // typedef PyObject *(*ssizeargfunc)(PyObject *, Py_ssize_t);
    SSIZEARGFUNC(false, PyObject, PyObject, Py_ssize_t),

    // typedef PyObject *(*ssizessizeargfunc)(PyObject *, Py_ssize_t, Py_ssize_t);
    SSIZESSIZEARGFUNC(false, PyObject, PyObject, Py_ssize_t, Py_ssize_t),
    // typedef int(*ssizeobjargproc)(PyObject *, Py_ssize_t, PyObject *);
    SSIZEOBJARGPROC(false, Int, PyObject, Py_ssize_t, PyObject),
    // typedef int(*ssizessizeobjargproc)(PyObject *, Py_ssize_t, Py_ssize_t, PyObject *);
    SSIZESSIZEOBJARGPROC(false, Int, PyObject, Py_ssize_t, Py_ssize_t, PyObject),
    // typedef int(*objobjargproc)(PyObject *, PyObject *, PyObject *);
    OBJOBJARGPROC(false, Int, PyObject, PyObject, PyObject),

    // typedef int (*objobjproc)(PyObject *, PyObject *);
    OBJOBJPROC(false, Int, PyObject, PyObject),
    // typedef int (*visitproc)(PyObject *, void *);
    VISITPROC(false, Int, PyObject, Pointer),
    // typedef int (*traverseproc)(PyObject *, visitproc, void *);
    TRAVERSEPROC(false, Int, PyObject, Pointer, Pointer),
    // PyObject *PyType_GenericAlloc(PyTypeObject *, Py_ssize_t);
    TYPE_GENERIC_ALLOC(false, PyObject, PyTypeObject, Py_ssize_t),
    // PyObject *PyType_GenericNew(PyTypeObject *, PyObject *, PyObject *);
    PY_TYPE_GENERIC_NEW(false, PyObject, PyTypeObject, PyObject, PyObject),
    // uintptr_t PyType_GenericNew(PyTypeObject *, uintptr_t, uintptr_t);
    PY_TYPE_GENERIC_NEW_RAW(true, UINTPTR_T, PyTypeObject, UINTPTR_T, UINTPTR_T),

    // typedef void (*freefunc)(void *);
    FREEFUNC(false, Void, Pointer),
    // typedef void (*destructor)(PyObject *);
    DESTRUCTOR(false, Void, PyObject),
    // void _Py_Dealloc(PyObject *);
    PY_DEALLOC(false, Void, PyObject),
    // typedef PyObject *(*getattrfunc)(PyObject *, char *);
    GETATTRFUNC(false, PyObject, PyObject, CHAR_PTR),
    // typedef PyObject *(*getattrofunc)(PyObject *, PyObject *);
    GETATTROFUNC(false, PyObject, PyObject, PyObject),
    // typedef int (*setattrfunc)(PyObject *, char *, PyObject *);
    SETATTRFUNC(false, Int, PyObject, CHAR_PTR, PyObject),
    // typedef int (*setattrofunc)(PyObject *, PyObject *, PyObject *);
    SETATTROFUNC(false, Int, PyObject, PyObject, PyObject),
    // typedef PyObject *(*reprfunc)(PyObject *);
    REPRFUNC(false, PyObject, PyObject),
    // typedef Py_hash_t (*hashfunc)(PyObject *);
    HASHFUNC(false, Py_ssize_t, PyObject),
    // typedef PyObject *(*richcmpfunc) (PyObject *, PyObject *, int);
    RICHCMPFUNC(false, PyObject, PyObject, PyObject, Int),
    // typedef PyObject *(*getiterfunc) (PyObject *);
    GETITERFUNC(false, PyObject, PyObject),
    // typedef PyObject *(*iternextfunc) (PyObject *);
    ITERNEXTFUNC(false, PyObject, PyObject),
    // typedef PyObject *(*descrgetfunc) (PyObject *, PyObject *, PyObject *);
    DESCRGETFUNC(false, PyObject, PyObject, PyObject, PyObject),
    // typedef int (*descrsetfunc) (PyObject *, PyObject *, PyObject *);
    DESCRSETFUNC(false, Int, PyObject, PyObject, PyObject),
    // typedef int (*initproc)(PyObject *, PyObject *, PyObject *);
    INITPROC(false, Int, PyObject, PyObject, PyObject),
    // typedef PyObject *(*newfunc)(PyTypeObject *, PyObject *, PyObject *);
    NEWFUNC(false, PyObject, PyTypeObject, PyObject, PyObject),

    // typedef PyObject *(*getter)(PyObject *, void *);
    GETTER(false, PyObject, PyObject, Pointer),
    // typedef int (*setter)(PyObject *, PyObject *, void *);
    SETTER(false, Int, PyObject, PyObject, Pointer),
    // typedef PyObject *(*Py_mod_create)(PyObject *, PyModuleDef *);
    MODCREATE(false, PyObject, Pointer, Pointer),
    // typedef int (*Py_mod_exec)(PyObject *);
    MODEXEC(false, Int, Pointer),
    // typedef PyObject *(*PyModInitFunction)(void);
    // typedef PySlot *(*PyModExportFunction)(void);
    MODINIT(false, Pointer),
    // typedef PThreadState** (*initialize_graal_capi)(void *, void *, void *, void *, void *);
    CAPIINIT(false, Pointer, Pointer, Pointer, Pointer, Pointer, Pointer),
    // PyThreadState **GraalPyPrivate_InitThreadStateCurrent(PyThreadState *tstate)
    INIT_THREAD_STATE_CURRENT(true, PyThreadStatePtr, PyThreadState),
    // void GraalPyPrivate_LogImpl(int, const char *, va_list);
    LOG_IMPL(true, Void, Int, ConstCharPtr, VA_LIST),
    // typedef void *(*GraalPyPrivate_GetFinalizeCApiPointer)(void);
    GETFINALIZECAPIPOINTER(false, Pointer),
    // int PyType_Ready(PyTypeObject *);
    PY_TYPE_READY(false, Int, PyTypeObject),
    // void GraalPyPrivate_CheckTypeReady(PyTypeObject *);
    TRUFFLE_CHECK_TYPE_READY(true, Void, PyTypeObject),
    // Py_ssize_t PyUnicode_GetLength(PyObject *);
    PY_UNICODE_GET_LENGTH(false, Long, PyObject),
    // void GraalPyPrivate_InitNativeDateTime(void);
    INIT_NATIVE_DATETIME(true, Void),
    // uintptr_t GraalPyPrivate_Long_lv_tag(const PyLongObject *);
    LONG_LV_TAG(true, UINTPTR_T, ConstPyLongObject),
    // void* PyMem_Calloc(size_t, size_t);
    PYMEM_ALLOC(true, Pointer, SIZE_T, SIZE_T),
    // int GraalPyPrivate_NoOpClear(PyObject *);
    NO_OP_CLEAR(true, Int, PyObject),
    // int GraalPyPrivate_NoOpTraverse(PyObject *, void *, void *);
    NO_OP_TRAVERSE(true, Int, PyObject, visitproc, Pointer),
    // int PyObject_GenericSetDict(PyObject *, PyObject *, void *);
    PY_OBJECT_GENERIC_SET_DICT(false, Int, PyObject, PyObject, Pointer),
    // PyObject *GraalPyPrivate_ObjectNew(PyTypeObject *);
    PY_OBJECT_NEW(false, PyObject, PyTypeObject),
    // void PyObject_Free(void *);
    PY_OBJECT_FREE(true, Void, Pointer),
    // void GraalPyPrivate_ObjectArrayRelease(PyObject **, int);
    OBJECT_ARRAY_RELEASE(true, Void, PyObjectPtr, Int),
    // void GraalPyPrivate_Object_GC_Del(void *);
    GRAALPY_OBJECT_GC_DEL(true, Void, Pointer),
    // void GraalPyPrivate_Capsule_CallDestructor(PyObject *, PyCapsule_Destructor);
    GRAALPY_CAPSULE_CALL_DESTRUCTOR(true, Void, PyObject, PY_CAPSULE_DESTRUCTOR),
    // Py_ssize_t GraalPyPrivate_BulkDealloc(intptr_t *, int64_t);
    BULK_DEALLOC(true, Py_ssize_t, INTPTR_T_PTR, INT64_T),
    // Py_ssize_t GraalPyPrivate_BulkDeallocOnShutdown(intptr_t *, int64_t);
    SHUTDOWN_BULK_DEALLOC(true, Py_ssize_t, INTPTR_T_PTR, INT64_T),
    // size_t GraalPyPrivate_GetCurrentRSS(void);
    GET_CURRENT_RSS(true, SIZE_T),
    // void GraalPyPrivate_ReleaseBuffer(Py_buffer *);
    GRAALPY_RELEASE_BUFFER(true, Void, PY_BUFFER_PTR),
    // void GraalPyPrivate_MMap_InitBufferProtocol(PyObject *);
    MMAP_INIT_BUFFERPROTOCOL(true, Void, PyObject),
    // PyObject *GraalPyPrivate_Exception_SubtypeNew(PyTypeObject *, PyObject *);
    EXCEPTION_SUBTYPE_NEW(false, PyObject, PyTypeObject, PyObject),
    // PyObject *GraalPyPrivate_Bytes_SubtypeNew(PyTypeObject *, int8_t *, Py_ssize_t);
    BYTES_SUBTYPE_NEW(false, PyObject, PyTypeObject, INT8_T_PTR, Py_ssize_t),
    // PyObject *GraalPyPrivate_Float_SubtypeNew(PyTypeObject *, double);
    FLOAT_SUBTYPE_NEW(false, PyObject, PyTypeObject, Double),
    // PyObject *GraalPyPrivate_Complex_SubtypeFromDoubles(PyTypeObject *, double, double);
    COMPLEX_SUBTYPE_FROM_DOUBLES(false, PyObject, PyTypeObject, Double, Double),
    // PyObject *GraalPyPrivate_Tuple_SubtypeNew(PyTypeObject *, PyObject *);
    TUPLE_SUBTYPE_NEW(false, PyObject, PyTypeObject, PyObject),
    // PyObject *GraalPyPrivate_Unicode_SubtypeNew(PyTypeObject *, PyObject *);
    UNICODE_SUBTYPE_NEW(false, PyObject, PyTypeObject, PyObject),
    // int GraalPyPrivate_CheckBasicsizeForGetstate(PyTypeObject *, int);
    CHECK_BASICSIZE_FOR_GETSTATE(true, Int, PyTypeObject, Int),
    // PyObject *GraalPyPrivate_Date_SubtypeNew(PyTypeObject *, int, int, int);
    DATE_SUBTYPE_NEW(false, PyObject, PyTypeObject, Int, Int, Int),
    // PyObject *GraalPyPrivate_Time_SubtypeNew(PyTypeObject *, int, int, int, int, PyObject *,
    // int);
    TIME_SUBTYPE_NEW(false, PyObject, PyTypeObject, Int, Int, Int, Int, PyObject, Int),
    // PyObject *GraalPyPrivate_TimeDelta_SubtypeNew(PyTypeObject *, int, int, int);
    TIMEDELTA_SUBTYPE_NEW(false, PyObject, PyTypeObject, Int, Int, Int),
    // PyObject *GraalPyPrivate_DateTime_SubtypeNew(PyTypeObject *, int, int, int, int, int, int,
    // int, PyObject *, int);
    DATETIME_SUBTYPE_NEW(false, PyObject, PyTypeObject, Int, Int, Int, Int, Int, Int, Int, PyObject, Int),
    // PyObject *GraalPyPrivate_MemoryViewFromObject(PyObject *, int);
    GRAALPY_MEMORYVIEW_FROM_OBJECT(false, PyObject, PyObject, Int),
    // Py_hash_t PyObject_HashNotImplemented(PyObject *);
    PYOBJECT_HASH_NOT_IMPLEMENTED(false, Py_ssize_t, PyObject),
    // void _PyGC_CollectNoFail(PyThreadState *);
    PY_GC_COLLECT_NO_FAIL(true, Void, PyThreadState),
    // PyObject *_PyObject_NextNotImplemented(PyObject *);
    PY_OBJECT_NEXT_NOT_IMPLEMENTED(false, PyObject, PyObject),
    // int GraalPyPrivate_SubtypeTraverse(PyObject *, void *, void *);
    SUBTYPE_TRAVERSE(true, Int, PyObject, visitproc, Pointer),

    // TODO(fa): should be an implicit signature
    GCCOLLECT(false, Py_ssize_t, Int),
    GETDICTPTRFUN(true, Pointer, PyObject),
    // typedef PyObject *(*vectorcallfunc)(PyObject *, PyObject *const *, size_t, PyObject *);
    VECTORCALL(false, PyObject, PyObject, Pointer, Py_ssize_t, PyObject);

    public final ArgDescriptor returnValue;
    public final ArgDescriptor[] arguments;

    /**
     * If {@code true}, the function will be called without a call boundary (see
     * {@link com.oracle.graal.python.runtime.ExecutionContext.BoundaryCallContext}). Hence, the
     * native function must not raise Python exception.
     */
    public final boolean cannotRaise;

    ExternalFunctionSignature(boolean cannotRaise, ArgDescriptor returnValue, ArgDescriptor... arguments) {
        this.cannotRaise = cannotRaise;
        this.returnValue = returnValue;
        this.arguments = arguments;
    }

    @Override
    public String getName() {
        return name();
    }

    public TruffleString getTsName() {
        return toTruffleStringUncached(name());
    }

    @Override
    public ArgDescriptor getReturnValue() {
        return returnValue;
    }

    @Override
    public ArgDescriptor[] getArguments() {
        return arguments;
    }
}
