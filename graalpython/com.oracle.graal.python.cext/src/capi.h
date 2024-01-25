/*
 * Copyright (c) 2017, 2024, Oracle and/or its affiliates. All rights reserved.
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
#ifndef CAPI_H
#define CAPI_H

#if defined(_MSC_VER) && !defined(__clang__)
#define MUST_INLINE inline
#define NO_INLINE __declspec(noinline)
#else
#define MUST_INLINE __attribute__((always_inline)) inline
#define NO_INLINE __attribute__((noinline))
#endif

#ifdef MS_WINDOWS
// define the below, otherwise windows' sdk defines complex to _complex and breaks us
#define _COMPLEX_DEFINED
#endif


#include "Python.h"
#include "datetime.h"
#include "structmember.h"
#include "frameobject.h"
#include "pycore_moduleobject.h"
#include "pycore_pymem.h"
#include "bytesobject.h"

#ifdef GRAALVM_PYTHON_LLVM_MANAGED
#include <graalvm/llvm/polyglot.h>
#endif

#define SRC_CS "utf-8"

/* Flags definitions representing global (debug) options. */
#define PY_TRUFFLE_TRACE_MEM 0x1
#define PY_TRUFFLE_LOG_INFO 0x2
#define PY_TRUFFLE_LOG_CONFIG 0x4
#define PY_TRUFFLE_LOG_FINE 0x8
#define PY_TRUFFLE_LOG_FINER 0x10
#define PY_TRUFFLE_LOG_FINEST 0x20
#define PY_TRUFFLE_DEBUG_CAPI 0x30

typedef struct mmap_object mmap_object;

/* Private types are defined here because we need to declare the type cast. */

/* Taken from CPython "Objects/descrobject.c".
 * This struct is actually private to 'descrobject.c' but we need to register
 * it to the managed property type. */
typedef struct {
    PyObject_HEAD
    PyObject *prop_get;
    PyObject *prop_set;
    PyObject *prop_del;
    PyObject *prop_doc;
    int getter_doc;
} propertyobject;


// {{start CAPI_BUILTINS}}
// GENERATED CODE - see CApiCodeGen
// This can be re-generated using the 'mx python-capi-forwards' command or
// by executing the main class CApiCodeGen

#define CAPI_BUILTINS \
    BUILTIN(PyByteArray_Resize, int, PyObject*, Py_ssize_t) \
    BUILTIN(PyBytes_AsString, char*, PyObject*) \
    BUILTIN(PyBytes_FromObject, PyObject*, PyObject*) \
    BUILTIN(PyBytes_Size, Py_ssize_t, PyObject*) \
    BUILTIN(PyCallIter_New, PyObject*, PyObject*, PyObject*) \
    BUILTIN(PyCallable_Check, int, PyObject*) \
    BUILTIN(PyCapsule_GetContext, void*, PyObject*) \
    BUILTIN(PyCapsule_GetDestructor, PyCapsule_Destructor, PyObject*) \
    BUILTIN(PyCapsule_GetName, const char*, PyObject*) \
    BUILTIN(PyCapsule_GetPointer, void*, PyObject*, const char*) \
    BUILTIN(PyCapsule_Import, void*, const char*, int) \
    BUILTIN(PyCapsule_IsValid, int, PyObject*, const char*) \
    BUILTIN(PyCapsule_New, PyObject*, void*, const char*, PyCapsule_Destructor) \
    BUILTIN(PyCapsule_SetContext, int, PyObject*, void*) \
    BUILTIN(PyCapsule_SetDestructor, int, PyObject*, PyCapsule_Destructor) \
    BUILTIN(PyCapsule_SetName, int, PyObject*, const char*) \
    BUILTIN(PyCapsule_SetPointer, int, PyObject*, void*) \
    BUILTIN(PyClassMethod_New, PyObject*, PyObject*) \
    BUILTIN(PyCode_Addr2Line, int, PyCodeObject*, int) \
    BUILTIN(PyCode_GetFileName, PyObject*, PyCodeObject*) \
    BUILTIN(PyCode_GetName, PyObject*, PyCodeObject*) \
    BUILTIN(PyCode_New, PyCodeObject*, int, int, int, int, int, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, int, PyObject*) \
    BUILTIN(PyCode_NewEmpty, PyCodeObject*, const char*, const char*, int) \
    BUILTIN(PyCode_NewWithPosOnlyArgs, PyCodeObject*, int, int, int, int, int, int, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, int, PyObject*) \
    BUILTIN(PyCodec_Decoder, PyObject*, const char*) \
    BUILTIN(PyCodec_Encoder, PyObject*, const char*) \
    BUILTIN(PyComplex_FromDoubles, PyObject*, double, double) \
    BUILTIN(PyComplex_ImagAsDouble, double, PyObject*) \
    BUILTIN(PyComplex_RealAsDouble, double, PyObject*) \
    BUILTIN(PyContextVar_New, PyObject*, const char*, PyObject*) \
    BUILTIN(PyContextVar_Set, PyObject*, PyObject*, PyObject*) \
    BUILTIN(PyDictProxy_New, PyObject*, PyObject*) \
    BUILTIN(PyDict_Clear, void, PyObject*) \
    BUILTIN(PyDict_Contains, int, PyObject*, PyObject*) \
    BUILTIN(PyDict_Copy, PyObject*, PyObject*) \
    BUILTIN(PyDict_DelItem, int, PyObject*, PyObject*) \
    BUILTIN(PyDict_GetItem, PyObject*, PyObject*, PyObject*) \
    BUILTIN(PyDict_GetItemWithError, PyObject*, PyObject*, PyObject*) \
    BUILTIN(PyDict_Items, PyObject*, PyObject*) \
    BUILTIN(PyDict_Keys, PyObject*, PyObject*) \
    BUILTIN(PyDict_Merge, int, PyObject*, PyObject*, int) \
    BUILTIN(PyDict_New, PyObject*) \
    BUILTIN(PyDict_SetDefault, PyObject*, PyObject*, PyObject*, PyObject*) \
    BUILTIN(PyDict_SetItem, int, PyObject*, PyObject*, PyObject*) \
    BUILTIN(PyDict_Size, Py_ssize_t, PyObject*) \
    BUILTIN(PyDict_Update, int, PyObject*, PyObject*) \
    BUILTIN(PyDict_Values, PyObject*, PyObject*) \
    BUILTIN(PyErr_Display, void, PyObject*, PyObject*, PyObject*) \
    BUILTIN(PyErr_GivenExceptionMatches, int, PyObject*, PyObject*) \
    BUILTIN(PyErr_NewException, PyObject*, const char*, PyObject*, PyObject*) \
    BUILTIN(PyErr_NewExceptionWithDoc, PyObject*, const char*, const char*, PyObject*, PyObject*) \
    BUILTIN(PyErr_Occurred, PyObject*) \
    BUILTIN(PyErr_PrintEx, void, int) \
    BUILTIN(PyErr_Restore, void, PyObject*, PyObject*, PyObject*) \
    BUILTIN(PyErr_SetExcInfo, void, PyObject*, PyObject*, PyObject*) \
    BUILTIN(PyEval_GetBuiltins, PyObject*) \
    BUILTIN(PyEval_GetFrame, PyFrameObject*) \
    BUILTIN(PyEval_RestoreThread, void, PyThreadState*) \
    BUILTIN(PyEval_SaveThread, PyThreadState*) \
    BUILTIN(PyException_GetCause, PyObject*, PyObject*) \
    BUILTIN(PyException_GetContext, PyObject*, PyObject*) \
    BUILTIN(PyException_GetTraceback, PyObject*, PyObject*) \
    BUILTIN(PyException_SetCause, void, PyObject*, PyObject*) \
    BUILTIN(PyException_SetContext, void, PyObject*, PyObject*) \
    BUILTIN(PyException_SetTraceback, int, PyObject*, PyObject*) \
    BUILTIN(PyFile_WriteObject, int, PyObject*, PyObject*, int) \
    BUILTIN(PyFloat_FromDouble, PyObject*, double) \
    BUILTIN(PyFloat_FromString, PyObject*, PyObject*) \
    BUILTIN(PyFrame_GetBack, PyFrameObject*, PyFrameObject*) \
    BUILTIN(PyFrame_GetBuiltins, PyObject*, PyFrameObject*) \
    BUILTIN(PyFrame_GetCode, PyCodeObject*, PyFrameObject*) \
    BUILTIN(PyFrame_GetGlobals, PyObject*, PyFrameObject*) \
    BUILTIN(PyFrame_GetLasti, int, PyFrameObject*) \
    BUILTIN(PyFrame_GetLineNumber, int, PyFrameObject*) \
    BUILTIN(PyFrame_GetLocals, PyObject*, PyFrameObject*) \
    BUILTIN(PyFrame_New, PyFrameObject*, PyThreadState*, PyCodeObject*, PyObject*, PyObject*) \
    BUILTIN(PyFrozenSet_New, PyObject*, PyObject*) \
    BUILTIN(PyGILState_Check, int) \
    BUILTIN(PyImport_GetModuleDict, PyObject*) \
    BUILTIN(PyImport_Import, PyObject*, PyObject*) \
    BUILTIN(PyImport_ImportModule, PyObject*, const char*) \
    BUILTIN(PyImport_ImportModuleLevelObject, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, int) \
    BUILTIN(PyImport_ImportModuleNoBlock, PyObject*, const char*) \
    BUILTIN(PyIndex_Check, int, PyObject*) \
    BUILTIN(PyInstanceMethod_New, PyObject*, PyObject*) \
    BUILTIN(PyIter_Check, int, PyObject*) \
    BUILTIN(PyIter_Next, PyObject*, PyObject*) \
    BUILTIN(PyList_Append, int, PyObject*, PyObject*) \
    BUILTIN(PyList_AsTuple, PyObject*, PyObject*) \
    BUILTIN(PyList_GetItem, PyObject*, PyObject*, Py_ssize_t) \
    BUILTIN(PyList_GetSlice, PyObject*, PyObject*, Py_ssize_t, Py_ssize_t) \
    BUILTIN(PyList_Insert, int, PyObject*, Py_ssize_t, PyObject*) \
    BUILTIN(PyList_New, PyObject*, Py_ssize_t) \
    BUILTIN(PyList_Reverse, int, PyObject*) \
    BUILTIN(PyList_SetItem, int, PyObject*, Py_ssize_t, PyObject*) \
    BUILTIN(PyList_SetSlice, int, PyObject*, Py_ssize_t, Py_ssize_t, PyObject*) \
    BUILTIN(PyList_Size, Py_ssize_t, PyObject*) \
    BUILTIN(PyList_Sort, int, PyObject*) \
    BUILTIN(PyLong_AsVoidPtr, void*, PyObject*) \
    BUILTIN(PyLong_FromDouble, PyObject*, double) \
    BUILTIN(PyLong_FromLong, PyObject*, long) \
    BUILTIN(PyLong_FromLongLong, PyObject*, long long) \
    BUILTIN(PyLong_FromSize_t, PyObject*, size_t) \
    BUILTIN(PyLong_FromSsize_t, PyObject*, Py_ssize_t) \
    BUILTIN(PyLong_FromUnicodeObject, PyObject*, PyObject*, int) \
    BUILTIN(PyLong_FromUnsignedLong, PyObject*, unsigned long) \
    BUILTIN(PyLong_FromUnsignedLongLong, PyObject*, unsigned long long) \
    BUILTIN(PyMapping_Check, int, PyObject*) \
    BUILTIN(PyMapping_Items, PyObject*, PyObject*) \
    BUILTIN(PyMapping_Keys, PyObject*, PyObject*) \
    BUILTIN(PyMapping_Size, Py_ssize_t, PyObject*) \
    BUILTIN(PyMapping_Values, PyObject*, PyObject*) \
    BUILTIN(PyMemoryView_FromObject, PyObject*, PyObject*) \
    BUILTIN(PyMemoryView_GetContiguous, PyObject*, PyObject*, int, char) \
    BUILTIN(PyMethod_New, PyObject*, PyObject*, PyObject*) \
    BUILTIN(PyModule_AddIntConstant, int, PyObject*, const char*, long) \
    BUILTIN(PyModule_AddObjectRef, int, PyObject*, const char*, PyObject*) \
    BUILTIN(PyModule_GetNameObject, PyObject*, PyObject*) \
    BUILTIN(PyModule_New, PyObject*, const char*) \
    BUILTIN(PyModule_NewObject, PyObject*, PyObject*) \
    BUILTIN(PyModule_SetDocString, int, PyObject*, const char*) \
    BUILTIN(PyNumber_Absolute, PyObject*, PyObject*) \
    BUILTIN(PyNumber_Check, int, PyObject*) \
    BUILTIN(PyNumber_Divmod, PyObject*, PyObject*, PyObject*) \
    BUILTIN(PyNumber_Float, PyObject*, PyObject*) \
    BUILTIN(PyNumber_InPlacePower, PyObject*, PyObject*, PyObject*, PyObject*) \
    BUILTIN(PyNumber_Index, PyObject*, PyObject*) \
    BUILTIN(PyNumber_Long, PyObject*, PyObject*) \
    BUILTIN(PyNumber_Power, PyObject*, PyObject*, PyObject*, PyObject*) \
    BUILTIN(PyNumber_ToBase, PyObject*, PyObject*, int) \
    BUILTIN(PyOS_FSPath, PyObject*, PyObject*) \
    BUILTIN(PyObject_ASCII, PyObject*, PyObject*) \
    BUILTIN(PyObject_AsFileDescriptor, int, PyObject*) \
    BUILTIN(PyObject_Bytes, PyObject*, PyObject*) \
    BUILTIN(PyObject_ClearWeakRefs, void, PyObject*) \
    BUILTIN(PyObject_DelItem, int, PyObject*, PyObject*) \
    BUILTIN(PyObject_Dir, PyObject*, PyObject*) \
    BUILTIN(PyObject_Format, PyObject*, PyObject*, PyObject*) \
    BUILTIN(PyObject_GetDoc, const char*, PyObject*) \
    BUILTIN(PyObject_GetItem, PyObject*, PyObject*, PyObject*) \
    BUILTIN(PyObject_GetIter, PyObject*, PyObject*) \
    BUILTIN(PyObject_HasAttr, int, PyObject*, PyObject*) \
    BUILTIN(PyObject_HasAttrString, int, PyObject*, const char*) \
    BUILTIN(PyObject_Hash, Py_hash_t, PyObject*) \
    BUILTIN(PyObject_HashNotImplemented, Py_hash_t, PyObject*) \
    BUILTIN(PyObject_IsInstance, int, PyObject*, PyObject*) \
    BUILTIN(PyObject_IsSubclass, int, PyObject*, PyObject*) \
    BUILTIN(PyObject_IsTrue, int, PyObject*) \
    BUILTIN(PyObject_LengthHint, Py_ssize_t, PyObject*, Py_ssize_t) \
    BUILTIN(PyObject_Repr, PyObject*, PyObject*) \
    BUILTIN(PyObject_RichCompare, PyObject*, PyObject*, PyObject*, int) \
    BUILTIN(PyObject_SetDoc, int, PyObject*, const char*) \
    BUILTIN(PyObject_SetItem, int, PyObject*, PyObject*, PyObject*) \
    BUILTIN(PyObject_Size, Py_ssize_t, PyObject*) \
    BUILTIN(PyObject_Str, PyObject*, PyObject*) \
    BUILTIN(PyObject_Type, PyObject*, PyObject*) \
    BUILTIN(PyRun_StringFlags, PyObject*, const char*, int, PyObject*, PyObject*, PyCompilerFlags*) \
    BUILTIN(PySeqIter_New, PyObject*, PyObject*) \
    BUILTIN(PySequence_Check, int, PyObject*) \
    BUILTIN(PySequence_Concat, PyObject*, PyObject*, PyObject*) \
    BUILTIN(PySequence_Contains, int, PyObject*, PyObject*) \
    BUILTIN(PySequence_Count, Py_ssize_t, PyObject*, PyObject*) \
    BUILTIN(PySequence_DelItem, int, PyObject*, Py_ssize_t) \
    BUILTIN(PySequence_DelSlice, int, PyObject*, Py_ssize_t, Py_ssize_t) \
    BUILTIN(PySequence_GetItem, PyObject*, PyObject*, Py_ssize_t) \
    BUILTIN(PySequence_GetSlice, PyObject*, PyObject*, Py_ssize_t, Py_ssize_t) \
    BUILTIN(PySequence_InPlaceConcat, PyObject*, PyObject*, PyObject*) \
    BUILTIN(PySequence_InPlaceRepeat, PyObject*, PyObject*, Py_ssize_t) \
    BUILTIN(PySequence_Index, Py_ssize_t, PyObject*, PyObject*) \
    BUILTIN(PySequence_Length, Py_ssize_t, PyObject*) \
    BUILTIN(PySequence_List, PyObject*, PyObject*) \
    BUILTIN(PySequence_Repeat, PyObject*, PyObject*, Py_ssize_t) \
    BUILTIN(PySequence_SetItem, int, PyObject*, Py_ssize_t, PyObject*) \
    BUILTIN(PySequence_SetSlice, int, PyObject*, Py_ssize_t, Py_ssize_t, PyObject*) \
    BUILTIN(PySequence_Size, Py_ssize_t, PyObject*) \
    BUILTIN(PySequence_Tuple, PyObject*, PyObject*) \
    BUILTIN(PySet_Add, int, PyObject*, PyObject*) \
    BUILTIN(PySet_Clear, int, PyObject*) \
    BUILTIN(PySet_Contains, int, PyObject*, PyObject*) \
    BUILTIN(PySet_Discard, int, PyObject*, PyObject*) \
    BUILTIN(PySet_New, PyObject*, PyObject*) \
    BUILTIN(PySet_Pop, PyObject*, PyObject*) \
    BUILTIN(PySet_Size, Py_ssize_t, PyObject*) \
    BUILTIN(PySlice_New, PyObject*, PyObject*, PyObject*, PyObject*) \
    BUILTIN(PyStaticMethod_New, PyObject*, PyObject*) \
    BUILTIN(PyStructSequence_New, PyObject*, PyTypeObject*) \
    BUILTIN(PySys_GetObject, PyObject*, const char*) \
    BUILTIN(PyThreadState_Get, PyThreadState*) \
    BUILTIN(PyThreadState_GetDict, PyObject*) \
    BUILTIN(PyThread_acquire_lock, int, PyThread_type_lock, int) \
    BUILTIN(PyThread_allocate_lock, PyThread_type_lock) \
    BUILTIN(PyThread_get_thread_ident, unsigned long) \
    BUILTIN(PyThread_release_lock, void, PyThread_type_lock) \
    BUILTIN(PyTraceBack_Here, int, PyFrameObject*) \
    BUILTIN(PyTraceMalloc_Track, int, unsigned int, uintptr_t, size_t) \
    BUILTIN(PyTraceMalloc_Untrack, int, unsigned int, uintptr_t) \
    BUILTIN(PyTruffleByteArray_FromStringAndSize, PyObject*, const char*, Py_ssize_t) \
    BUILTIN(PyTruffleBytes_Concat, PyObject*, PyObject*, PyObject*) \
    BUILTIN(PyTruffleBytes_FromFormat, PyObject*, const char*, PyObject*) \
    BUILTIN(PyTruffleBytes_FromStringAndSize, PyObject*, const char*, Py_ssize_t) \
    BUILTIN(PyTruffleCMethod_NewEx, PyObject*, PyMethodDef*, const char*, void*, int, int, PyObject*, PyObject*, PyTypeObject*, const char*) \
    BUILTIN(PyTruffleComplex_AsCComplex, PyObject*, PyObject*) \
    BUILTIN(PyTruffleContextVar_Get, PyObject*, PyObject*, PyObject*, void*) \
    BUILTIN(PyTruffleDateTimeCAPI_DateTime_FromDateAndTime, PyObject*, int, int, int, int, int, int, int, PyObject*, PyTypeObject*) \
    BUILTIN(PyTruffleDateTimeCAPI_DateTime_FromDateAndTimeAndFold, PyObject*, int, int, int, int, int, int, int, PyObject*, int, PyTypeObject*) \
    BUILTIN(PyTruffleDateTimeCAPI_DateTime_FromTimestamp, PyObject*, PyObject*, PyObject*, PyObject*) \
    BUILTIN(PyTruffleDateTimeCAPI_Date_FromDate, PyObject*, int, int, int, PyTypeObject*) \
    BUILTIN(PyTruffleDateTimeCAPI_Date_FromTimestamp, PyObject*, PyObject*, PyObject*) \
    BUILTIN(PyTruffleDateTimeCAPI_Delta_FromDelta, PyObject*, int, int, int, int, PyTypeObject*) \
    BUILTIN(PyTruffleDateTimeCAPI_TimeZone_FromTimeZone, PyObject*, PyObject*, PyObject*) \
    BUILTIN(PyTruffleDateTimeCAPI_Time_FromTime, PyObject*, int, int, int, int, PyObject*, PyTypeObject*) \
    BUILTIN(PyTruffleDateTimeCAPI_Time_FromTimeAndFold, PyObject*, int, int, int, int, PyObject*, int, PyTypeObject*) \
    BUILTIN(PyTruffleDescr_NewClassMethod, PyObject*, void*, const char*, const char*, int, int, void*, PyTypeObject*) \
    BUILTIN(PyTruffleDescr_NewGetSet, PyObject*, const char*, PyTypeObject*, void*, void*, const char*, void*) \
    BUILTIN(PyTruffleDict_Next, PyObject*, PyObject*, Py_ssize_t) \
    BUILTIN(PyTruffleErr_Fetch, PyObject*) \
    BUILTIN(PyTruffleErr_GetExcInfo, PyObject*) \
    BUILTIN(PyTruffleErr_WarnExplicit, PyObject*, PyObject*, PyObject*, PyObject*, int, PyObject*, PyObject*) \
    BUILTIN(PyTruffleFloat_AsDouble, double, PyObject*) \
    BUILTIN(PyTruffleGILState_Ensure, int) \
    BUILTIN(PyTruffleGILState_Release, void) \
    BUILTIN(PyTruffleHash_InitSecret, void, int8_t*) \
    BUILTIN(PyTruffleIter_Send, PyObject*, PyObject*, PyObject*) \
    BUILTIN(PyTruffleLong_AsPrimitive, long, PyObject*, int, long) \
    BUILTIN(PyTruffleLong_FromString, PyObject*, PyObject*, int, int) \
    BUILTIN(PyTruffleLong_One, PyObject*) \
    BUILTIN(PyTruffleLong_Zero, PyObject*) \
    BUILTIN(PyTruffleModule_AddFunctionToModule, int, void*, PyObject*, const char*, void*, int, int, const char*) \
    BUILTIN(PyTruffleNumber_BinOp, PyObject*, PyObject*, PyObject*, int) \
    BUILTIN(PyTruffleNumber_InPlaceBinOp, PyObject*, PyObject*, PyObject*, int) \
    BUILTIN(PyTruffleNumber_UnaryOp, PyObject*, PyObject*, int) \
    BUILTIN(PyTruffleObject_CallFunctionObjArgs, PyObject*, PyObject*, va_list*) \
    BUILTIN(PyTruffleObject_CallMethodObjArgs, PyObject*, PyObject*, PyObject*, va_list*) \
    BUILTIN(PyTruffleObject_GC_Track, void, void*) \
    BUILTIN(PyTruffleObject_GC_UnTrack, void, void*) \
    BUILTIN(PyTruffleObject_GenericGetAttr, PyObject*, PyObject*, PyObject*) \
    BUILTIN(PyTruffleObject_GenericSetAttr, int, PyObject*, PyObject*, PyObject*) \
    BUILTIN(PyTruffleObject_GetItemString, PyObject*, PyObject*, const char*) \
    BUILTIN(PyTruffleState_FindModule, PyObject*, Py_ssize_t) \
    BUILTIN(PyTruffleStructSequence_InitType2, int, PyTypeObject*, void*, int) \
    BUILTIN(PyTruffleStructSequence_NewType, PyTypeObject*, const char*, const char*, void*, int) \
    BUILTIN(PyTruffleType_AddFunctionToType, int, void*, PyTypeObject*, PyObject*, const char*, void*, int, int, const char*) \
    BUILTIN(PyTruffleType_AddGetSet, int, PyTypeObject*, PyObject*, const char*, void*, void*, const char*, void*) \
    BUILTIN(PyTruffleType_AddMember, int, PyTypeObject*, PyObject*, const char*, int, Py_ssize_t, int, const char*) \
    BUILTIN(PyTruffleType_AddSlot, int, PyTypeObject*, PyObject*, const char*, void*, int, int, const char*) \
    BUILTIN(PyTruffleType_IsSubtype, int, PyTypeObject*, PyTypeObject*) \
    BUILTIN(PyTruffleUnicode_Decode, PyObject*, PyObject*, const char*, const char*) \
    BUILTIN(PyTruffleUnicode_DecodeUTF16Stateful, PyObject*, void*, Py_ssize_t, const char*, int, int) \
    BUILTIN(PyTruffleUnicode_DecodeUTF32Stateful, PyObject*, void*, Py_ssize_t, const char*, int, int) \
    BUILTIN(PyTruffleUnicode_DecodeUTF8Stateful, PyObject*, void*, Py_ssize_t, const char*, int) \
    BUILTIN(PyTruffleUnicode_FromUCS, PyObject*, void*, Py_ssize_t, int) \
    BUILTIN(PyTruffleUnicode_FromUTF, PyObject*, void*, Py_ssize_t, int) \
    BUILTIN(PyTruffleUnicode_LookupAndIntern, PyObject*, PyObject*) \
    BUILTIN(PyTruffleUnicode_New, PyObject*, void*, Py_ssize_t, Py_ssize_t, Py_UCS4) \
    BUILTIN(PyTruffle_AddInheritedSlots, void, PyTypeObject*) \
    BUILTIN(PyTruffle_Arg_ParseArrayAndKeywords, int, void*, Py_ssize_t, PyObject*, const char*, void*, void*) \
    BUILTIN(PyTruffle_Arg_ParseTupleAndKeywords, int, PyObject*, PyObject*, const char*, void*, void*) \
    BUILTIN(PyTruffle_Array_getbuffer, int, PyObject*, Py_buffer*, int) \
    BUILTIN(PyTruffle_Array_releasebuffer, void, PyObject*, Py_buffer*) \
    BUILTIN(PyTruffle_BulkNotifyRefCount, void, void*, int) \
    BUILTIN(PyTruffle_ByteArray_EmptyWithCapacity, PyObject*, Py_ssize_t) \
    BUILTIN(PyTruffle_Bytes_CheckEmbeddedNull, int, PyObject*) \
    BUILTIN(PyTruffle_Bytes_EmptyWithCapacity, PyObject*, long) \
    BUILTIN(PyTruffle_Compute_Mro, PyObject*, PyTypeObject*, const char*) \
    BUILTIN(PyTruffle_Debug, int, void*) \
    BUILTIN(PyTruffle_DebugTrace, void) \
    BUILTIN(PyTruffle_Ellipsis, PyObject*) \
    BUILTIN(PyTruffle_False, PyObject*) \
    BUILTIN(PyTruffle_FatalErrorFunc, void, const char*, const char*, int) \
    BUILTIN(PyTruffle_FileSystemDefaultEncoding, PyObject*) \
    BUILTIN(PyTruffle_GetInitialNativeMemory, size_t) \
    BUILTIN(PyTruffle_GetMMapData, char*, PyObject*) \
    BUILTIN(PyTruffle_GetMaxNativeMemory, size_t) \
    BUILTIN(PyTruffle_HashConstant, long, int) \
    BUILTIN(PyTruffle_InitBuiltinTypesAndStructs, void, void*) \
    BUILTIN(PyTruffle_LogString, void, int, const char*) \
    BUILTIN(PyTruffle_MemoryViewFromBuffer, PyObject*, void*, PyObject*, Py_ssize_t, int, Py_ssize_t, const char*, int, void*, void*, void*, void*) \
    BUILTIN(PyTruffle_Native_Options, int) \
    BUILTIN(PyTruffle_NewTypeDict, PyObject*, PyTypeObject*) \
    BUILTIN(PyTruffle_NoValue, PyObject*) \
    BUILTIN(PyTruffle_None, PyObject*) \
    BUILTIN(PyTruffle_NotImplemented, PyObject*) \
    BUILTIN(PyTruffle_NotifyRefCount, void, PyObject*, Py_ssize_t) \
    BUILTIN(PyTruffle_Object_Free, void, void*) \
    BUILTIN(PyTruffle_PyDateTime_GET_TZINFO, PyObject*, PyObject*) \
    BUILTIN(PyTruffle_PyUnicode_Find, Py_ssize_t, PyObject*, PyObject*, Py_ssize_t, Py_ssize_t, int) \
    BUILTIN(PyTruffle_Register_NULL, void, void*) \
    BUILTIN(PyTruffle_Set_Native_Slots, int, PyTypeObject*, void*, void*) \
    BUILTIN(PyTruffle_ToNative, int, void*) \
    BUILTIN(PyTruffle_Trace_Type, int, void*, void*) \
    BUILTIN(PyTruffle_TriggerGC, void, size_t) \
    BUILTIN(PyTruffle_True, PyObject*) \
    BUILTIN(PyTruffle_Type, PyTypeObject*, const char*) \
    BUILTIN(PyTruffle_Type_Modified, int, PyTypeObject*, const char*, PyObject*) \
    BUILTIN(PyTruffle_Unicode_AsUTF8AndSize_CharPtr, const char*, PyObject*) \
    BUILTIN(PyTruffle_Unicode_AsUTF8AndSize_Size, Py_ssize_t, PyObject*) \
    BUILTIN(PyTruffle_Unicode_AsUnicodeAndSize_CharPtr, Py_UNICODE*, PyObject*) \
    BUILTIN(PyTruffle_Unicode_AsUnicodeAndSize_Size, Py_ssize_t, PyObject*) \
    BUILTIN(PyTruffle_Unicode_AsWideChar, PyObject*, PyObject*, int) \
    BUILTIN(PyTruffle_Unicode_FromFormat, PyObject*, const char*, va_list*) \
    BUILTIN(PyTruffle_tss_create, long) \
    BUILTIN(PyTruffle_tss_delete, void, long) \
    BUILTIN(PyTruffle_tss_get, void*, long) \
    BUILTIN(PyTruffle_tss_set, int, long, void*) \
    BUILTIN(PyTuple_GetItem, PyObject*, PyObject*, Py_ssize_t) \
    BUILTIN(PyTuple_GetSlice, PyObject*, PyObject*, Py_ssize_t, Py_ssize_t) \
    BUILTIN(PyTuple_New, PyObject*, Py_ssize_t) \
    BUILTIN(PyTuple_SetItem, int, PyObject*, Py_ssize_t, PyObject*) \
    BUILTIN(PyTuple_Size, Py_ssize_t, PyObject*) \
    BUILTIN(PyUnicodeDecodeError_Create, PyObject*, const char*, const char*, Py_ssize_t, Py_ssize_t, Py_ssize_t, const char*) \
    BUILTIN(PyUnicode_AsEncodedString, PyObject*, PyObject*, const char*, const char*) \
    BUILTIN(PyUnicode_AsUnicodeEscapeString, PyObject*, PyObject*) \
    BUILTIN(PyUnicode_Compare, int, PyObject*, PyObject*) \
    BUILTIN(PyUnicode_Concat, PyObject*, PyObject*, PyObject*) \
    BUILTIN(PyUnicode_Contains, int, PyObject*, PyObject*) \
    BUILTIN(PyUnicode_Count, Py_ssize_t, PyObject*, PyObject*, Py_ssize_t, Py_ssize_t) \
    BUILTIN(PyUnicode_DecodeFSDefault, PyObject*, const char*) \
    BUILTIN(PyUnicode_EncodeFSDefault, PyObject*, PyObject*) \
    BUILTIN(PyUnicode_FindChar, Py_ssize_t, PyObject*, Py_UCS4, Py_ssize_t, Py_ssize_t, int) \
    BUILTIN(PyUnicode_Format, PyObject*, PyObject*, PyObject*) \
    BUILTIN(PyUnicode_FromEncodedObject, PyObject*, PyObject*, const char*, const char*) \
    BUILTIN(PyUnicode_FromObject, PyObject*, PyObject*) \
    BUILTIN(PyUnicode_FromOrdinal, PyObject*, int) \
    BUILTIN(PyUnicode_FromString, PyObject*, const char*) \
    BUILTIN(PyUnicode_FromWideChar, PyObject*, const wchar_t*, Py_ssize_t) \
    BUILTIN(PyUnicode_Join, PyObject*, PyObject*, PyObject*) \
    BUILTIN(PyUnicode_ReadChar, Py_UCS4, PyObject*, Py_ssize_t) \
    BUILTIN(PyUnicode_Replace, PyObject*, PyObject*, PyObject*, PyObject*, Py_ssize_t) \
    BUILTIN(PyUnicode_Split, PyObject*, PyObject*, PyObject*, Py_ssize_t) \
    BUILTIN(PyUnicode_Substring, PyObject*, PyObject*, Py_ssize_t, Py_ssize_t) \
    BUILTIN(PyUnicode_Tailmatch, Py_ssize_t, PyObject*, PyObject*, Py_ssize_t, Py_ssize_t, int) \
    BUILTIN(PyWeakref_GetObject, PyObject*, PyObject*) \
    BUILTIN(PyWeakref_NewRef, PyObject*, PyObject*, PyObject*) \
    BUILTIN(Py_AtExit, int, void (*)(void)) \
    BUILTIN(Py_CompileString, PyObject*, const char*, const char*, int) \
    BUILTIN(Py_CompileStringExFlags, PyObject*, const char*, const char*, int, PyCompilerFlags*, int) \
    BUILTIN(Py_CompileStringObject, PyObject*, const char*, PyObject*, int, PyCompilerFlags*, int) \
    BUILTIN(Py_EnterRecursiveCall, int, const char*) \
    BUILTIN(Py_GenericAlias, PyObject*, PyObject*, PyObject*) \
    BUILTIN(Py_LeaveRecursiveCall, void) \
    BUILTIN(Py_get_PyASCIIObject_length, Py_ssize_t, PyASCIIObject*) \
    BUILTIN(Py_get_PyASCIIObject_state_ascii, unsigned int, PyASCIIObject*) \
    BUILTIN(Py_get_PyASCIIObject_state_compact, unsigned int, PyASCIIObject*) \
    BUILTIN(Py_get_PyASCIIObject_state_kind, unsigned int, PyASCIIObject*) \
    BUILTIN(Py_get_PyASCIIObject_state_ready, unsigned int, PyASCIIObject*) \
    BUILTIN(Py_get_PyASCIIObject_wstr, wchar_t*, PyASCIIObject*) \
    BUILTIN(Py_get_PyByteArrayObject_ob_exports, Py_ssize_t, PyByteArrayObject*) \
    BUILTIN(Py_get_PyByteArrayObject_ob_start, void*, PyByteArrayObject*) \
    BUILTIN(Py_get_PyCFunctionObject_m_ml, PyMethodDef*, PyCFunctionObject*) \
    BUILTIN(Py_get_PyCFunctionObject_m_module, PyObject*, PyCFunctionObject*) \
    BUILTIN(Py_get_PyCFunctionObject_m_self, PyObject*, PyCFunctionObject*) \
    BUILTIN(Py_get_PyCFunctionObject_m_weakreflist, PyObject*, PyCFunctionObject*) \
    BUILTIN(Py_get_PyCFunctionObject_vectorcall, vectorcallfunc, PyCFunctionObject*) \
    BUILTIN(Py_get_PyCMethodObject_mm_class, PyTypeObject*, PyCMethodObject*) \
    BUILTIN(Py_get_PyCompactUnicodeObject_wstr_length, Py_ssize_t, PyCompactUnicodeObject*) \
    BUILTIN(Py_get_PyDescrObject_d_name, PyObject*, PyDescrObject*) \
    BUILTIN(Py_get_PyDescrObject_d_type, PyTypeObject*, PyDescrObject*) \
    BUILTIN(Py_get_PyFrameObject_f_lineno, int, PyFrameObject*) \
    BUILTIN(Py_get_PyGetSetDef_closure, void*, PyGetSetDef*) \
    BUILTIN(Py_get_PyGetSetDef_doc, const char*, PyGetSetDef*) \
    BUILTIN(Py_get_PyGetSetDef_get, getter, PyGetSetDef*) \
    BUILTIN(Py_get_PyGetSetDef_name, const char*, PyGetSetDef*) \
    BUILTIN(Py_get_PyGetSetDef_set, setter, PyGetSetDef*) \
    BUILTIN(Py_get_PyInstanceMethodObject_func, PyObject*, PyInstanceMethodObject*) \
    BUILTIN(Py_get_PyListObject_ob_item, PyObject**, PyListObject*) \
    BUILTIN(Py_get_PyMethodDef_ml_doc, void*, PyMethodDef*) \
    BUILTIN(Py_get_PyMethodDef_ml_flags, int, PyMethodDef*) \
    BUILTIN(Py_get_PyMethodDef_ml_meth, PyCFunction, PyMethodDef*) \
    BUILTIN(Py_get_PyMethodDef_ml_name, void*, PyMethodDef*) \
    BUILTIN(Py_get_PyMethodDescrObject_d_method, PyMethodDef*, PyMethodDescrObject*) \
    BUILTIN(Py_get_PyMethodObject_im_func, PyObject*, PyMethodObject*) \
    BUILTIN(Py_get_PyMethodObject_im_self, PyObject*, PyMethodObject*) \
    BUILTIN(Py_get_PyModuleDef_m_doc, const char*, PyModuleDef*) \
    BUILTIN(Py_get_PyModuleDef_m_methods, PyMethodDef*, PyModuleDef*) \
    BUILTIN(Py_get_PyModuleDef_m_name, const char*, PyModuleDef*) \
    BUILTIN(Py_get_PyModuleDef_m_size, Py_ssize_t, PyModuleDef*) \
    BUILTIN(Py_get_PyModuleObject_md_def, PyModuleDef*, PyModuleObject*) \
    BUILTIN(Py_get_PyModuleObject_md_dict, PyObject*, PyModuleObject*) \
    BUILTIN(Py_get_PyModuleObject_md_state, void*, PyModuleObject*) \
    BUILTIN(Py_get_PyObject_ob_refcnt, Py_ssize_t, PyObject*) \
    BUILTIN(Py_get_PyObject_ob_type, PyTypeObject*, PyObject*) \
    BUILTIN(Py_get_PySetObject_used, Py_ssize_t, PySetObject*) \
    BUILTIN(Py_get_PySliceObject_start, PyObject*, PySliceObject*) \
    BUILTIN(Py_get_PySliceObject_step, PyObject*, PySliceObject*) \
    BUILTIN(Py_get_PySliceObject_stop, PyObject*, PySliceObject*) \
    BUILTIN(Py_get_PyTupleObject_ob_item, PyObject**, PyTupleObject*) \
    BUILTIN(Py_get_PyUnicodeObject_data, void*, PyUnicodeObject*) \
    BUILTIN(Py_get_PyVarObject_ob_size, Py_ssize_t, PyVarObject*) \
    BUILTIN(Py_get_dummy, void*, void*) \
    BUILTIN(Py_set_PyByteArrayObject_ob_exports, void, PyByteArrayObject*, int) \
    BUILTIN(Py_set_PyFrameObject_f_lineno, void, PyFrameObject*, int) \
    BUILTIN(Py_set_PyModuleObject_md_def, void, PyModuleObject*, PyModuleDef*) \
    BUILTIN(Py_set_PyModuleObject_md_state, void, PyModuleObject*, void*) \
    BUILTIN(Py_set_PyObject_ob_refcnt, void, PyObject*, Py_ssize_t) \
    BUILTIN(Py_set_PyTypeObject_tp_as_buffer, void, PyTypeObject*, PyBufferProcs*) \
    BUILTIN(Py_set_PyTypeObject_tp_base, void, PyTypeObject*, PyTypeObject*) \
    BUILTIN(Py_set_PyTypeObject_tp_bases, void, PyTypeObject*, PyObject*) \
    BUILTIN(Py_set_PyTypeObject_tp_clear, void, PyTypeObject*, inquiry) \
    BUILTIN(Py_set_PyTypeObject_tp_dict, void, PyTypeObject*, PyObject*) \
    BUILTIN(Py_set_PyTypeObject_tp_dictoffset, void, PyTypeObject*, Py_ssize_t) \
    BUILTIN(Py_set_PyTypeObject_tp_finalize, void, PyTypeObject*, destructor) \
    BUILTIN(Py_set_PyTypeObject_tp_getattr, void, PyTypeObject*, getattrfunc) \
    BUILTIN(Py_set_PyTypeObject_tp_getattro, void, PyTypeObject*, getattrofunc) \
    BUILTIN(Py_set_PyTypeObject_tp_iter, void, PyTypeObject*, getiterfunc) \
    BUILTIN(Py_set_PyTypeObject_tp_iternext, void, PyTypeObject*, iternextfunc) \
    BUILTIN(Py_set_PyTypeObject_tp_mro, void, PyTypeObject*, PyObject*) \
    BUILTIN(Py_set_PyTypeObject_tp_new, void, PyTypeObject*, newfunc) \
    BUILTIN(Py_set_PyTypeObject_tp_setattr, void, PyTypeObject*, setattrfunc) \
    BUILTIN(Py_set_PyTypeObject_tp_setattro, void, PyTypeObject*, setattrofunc) \
    BUILTIN(Py_set_PyTypeObject_tp_subclasses, void, PyTypeObject*, PyObject*) \
    BUILTIN(Py_set_PyTypeObject_tp_traverse, void, PyTypeObject*, traverseproc) \
    BUILTIN(Py_set_PyTypeObject_tp_weaklistoffset, void, PyTypeObject*, Py_ssize_t) \
    BUILTIN(Py_set_PyVarObject_ob_size, void, PyVarObject*, Py_ssize_t) \
    BUILTIN(_PyArray_Data, char*, PyObject*) \
    BUILTIN(_PyArray_Resize, int, PyObject*, Py_ssize_t) \
    BUILTIN(_PyBytes_Join, PyObject*, PyObject*, PyObject*) \
    BUILTIN(_PyDict_Pop, PyObject*, PyObject*, PyObject*, PyObject*) \
    BUILTIN(_PyDict_SetItem_KnownHash, int, PyObject*, PyObject*, PyObject*, Py_hash_t) \
    BUILTIN(_PyErr_BadInternalCall, void, const char*, int) \
    BUILTIN(_PyErr_ChainExceptions, void, PyObject*, PyObject*, PyObject*) \
    BUILTIN(_PyErr_Occurred, PyObject*, PyThreadState*) \
    BUILTIN(_PyErr_WriteUnraisableMsg, void, const char*, PyObject*) \
    BUILTIN(_PyList_Extend, PyObject*, PyListObject*, PyObject*) \
    BUILTIN(_PyList_SET_ITEM, void, PyObject*, Py_ssize_t, PyObject*) \
    BUILTIN(_PyLong_AsByteArray, int, PyLongObject*, unsigned char*, size_t, int, int) \
    BUILTIN(_PyLong_Sign, int, PyObject*) \
    BUILTIN(_PyNamespace_New, PyObject*, PyObject*) \
    BUILTIN(_PyNumber_Index, PyObject*, PyObject*) \
    BUILTIN(_PyObject_Dump, void, PyObject*) \
    BUILTIN(_PyObject_MakeTpCall, PyObject*, PyThreadState*, PyObject*, PyObject*const*, Py_ssize_t, PyObject*) \
    BUILTIN(_PyTraceMalloc_NewReference, int, PyObject*) \
    BUILTIN(_PyTraceback_Add, void, const char*, const char*, int) \
    BUILTIN(_PyTruffleBytes_Resize, int, PyObject*, Py_ssize_t) \
    BUILTIN(_PyTruffleErr_CreateAndSetException, void, PyObject*, PyObject*) \
    BUILTIN(_PyTruffleErr_Warn, PyObject*, PyObject*, PyObject*, Py_ssize_t, PyObject*) \
    BUILTIN(_PyTruffleEval_EvalCodeEx, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*const*, int, PyObject*const*, int, PyObject*const*, int, PyObject*, PyObject*) \
    BUILTIN(_PyTruffleModule_CreateInitialized_PyModule_New, PyModuleObject*, const char*) \
    BUILTIN(_PyTruffleModule_GetAndIncMaxModuleNumber, Py_ssize_t) \
    BUILTIN(_PyTruffleObject_Call1, PyObject*, PyObject*, PyObject*, PyObject*, int) \
    BUILTIN(_PyTruffleObject_CallMethod1, PyObject*, PyObject*, const char*, PyObject*, int) \
    BUILTIN(_PyTruffleSet_NextEntry, PyObject*, PyObject*, Py_ssize_t) \
    BUILTIN(_PyTuple_SET_ITEM, int, PyObject*, Py_ssize_t, PyObject*) \
    BUILTIN(_PyType_Lookup, PyObject*, PyTypeObject*, PyObject*) \
    BUILTIN(_PyUnicode_AsASCIIString, PyObject*, PyObject*, const char*) \
    BUILTIN(_PyUnicode_AsLatin1String, PyObject*, PyObject*, const char*) \
    BUILTIN(_PyUnicode_AsUTF8String, PyObject*, PyObject*, const char*) \
    BUILTIN(_PyUnicode_EqualToASCIIString, int, PyObject*, const char*) \
    BUILTIN(_Py_GetErrorHandler, _Py_error_handler, const char*) \
    BUILTIN(_Py_HashBytes, Py_hash_t, const void*, Py_ssize_t) \
    BUILTIN(_Py_HashDouble, Py_hash_t, PyObject*, double) \

#define PyASCIIObject_length(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyASCIIObject_length((PyASCIIObject*) (OBJ)) : ((PyASCIIObject*) (OBJ))->length )
#define PyASCIIObject_state_ascii(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyASCIIObject_state_ascii((PyASCIIObject*) (OBJ)) : ((PyASCIIObject*) (OBJ))->state_ascii )
#define PyASCIIObject_state_compact(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyASCIIObject_state_compact((PyASCIIObject*) (OBJ)) : ((PyASCIIObject*) (OBJ))->state_compact )
#define PyASCIIObject_state_kind(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyASCIIObject_state_kind((PyASCIIObject*) (OBJ)) : ((PyASCIIObject*) (OBJ))->state_kind )
#define PyASCIIObject_state_ready(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyASCIIObject_state_ready((PyASCIIObject*) (OBJ)) : ((PyASCIIObject*) (OBJ))->state_ready )
#define PyASCIIObject_wstr(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyASCIIObject_wstr((PyASCIIObject*) (OBJ)) : ((PyASCIIObject*) (OBJ))->wstr )
#define PyByteArrayObject_ob_exports(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyByteArrayObject_ob_exports((PyByteArrayObject*) (OBJ)) : ((PyByteArrayObject*) (OBJ))->ob_exports )
#define PyByteArrayObject_ob_start(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyByteArrayObject_ob_start((PyByteArrayObject*) (OBJ)) : ((PyByteArrayObject*) (OBJ))->ob_start )
#define PyCFunctionObject_m_ml(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyCFunctionObject_m_ml((PyCFunctionObject*) (OBJ)) : ((PyCFunctionObject*) (OBJ))->m_ml )
#define PyCFunctionObject_m_module(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyCFunctionObject_m_module((PyCFunctionObject*) (OBJ)) : ((PyCFunctionObject*) (OBJ))->m_module )
#define PyCFunctionObject_m_self(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyCFunctionObject_m_self((PyCFunctionObject*) (OBJ)) : ((PyCFunctionObject*) (OBJ))->m_self )
#define PyCFunctionObject_m_weakreflist(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyCFunctionObject_m_weakreflist((PyCFunctionObject*) (OBJ)) : ((PyCFunctionObject*) (OBJ))->m_weakreflist )
#define PyCFunctionObject_vectorcall(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyCFunctionObject_vectorcall((PyCFunctionObject*) (OBJ)) : ((PyCFunctionObject*) (OBJ))->vectorcall )
#define PyCMethodObject_mm_class(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyCMethodObject_mm_class((PyCMethodObject*) (OBJ)) : ((PyCMethodObject*) (OBJ))->mm_class )
#define PyCompactUnicodeObject_wstr_length(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyCompactUnicodeObject_wstr_length((PyCompactUnicodeObject*) (OBJ)) : ((PyCompactUnicodeObject*) (OBJ))->wstr_length )
#define PyDescrObject_d_name(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyDescrObject_d_name((PyDescrObject*) (OBJ)) : ((PyDescrObject*) (OBJ))->d_name )
#define PyDescrObject_d_type(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyDescrObject_d_type((PyDescrObject*) (OBJ)) : ((PyDescrObject*) (OBJ))->d_type )
#define PyFrameObject_f_lineno(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyFrameObject_f_lineno((PyFrameObject*) (OBJ)) : ((PyFrameObject*) (OBJ))->f_lineno )
#define PyGetSetDef_closure(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyGetSetDef_closure((PyGetSetDef*) (OBJ)) : ((PyGetSetDef*) (OBJ))->closure )
#define PyGetSetDef_doc(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyGetSetDef_doc((PyGetSetDef*) (OBJ)) : ((PyGetSetDef*) (OBJ))->doc )
#define PyGetSetDef_get(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyGetSetDef_get((PyGetSetDef*) (OBJ)) : ((PyGetSetDef*) (OBJ))->get )
#define PyGetSetDef_name(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyGetSetDef_name((PyGetSetDef*) (OBJ)) : ((PyGetSetDef*) (OBJ))->name )
#define PyGetSetDef_set(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyGetSetDef_set((PyGetSetDef*) (OBJ)) : ((PyGetSetDef*) (OBJ))->set )
#define PyInstanceMethodObject_func(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyInstanceMethodObject_func((PyInstanceMethodObject*) (OBJ)) : ((PyInstanceMethodObject*) (OBJ))->func )
#define PyListObject_ob_item(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyListObject_ob_item((PyListObject*) (OBJ)) : ((PyListObject*) (OBJ))->ob_item )
#define PyMethodDef_ml_doc(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyMethodDef_ml_doc((PyMethodDef*) (OBJ)) : ((PyMethodDef*) (OBJ))->ml_doc )
#define PyMethodDef_ml_flags(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyMethodDef_ml_flags((PyMethodDef*) (OBJ)) : ((PyMethodDef*) (OBJ))->ml_flags )
#define PyMethodDef_ml_meth(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyMethodDef_ml_meth((PyMethodDef*) (OBJ)) : ((PyMethodDef*) (OBJ))->ml_meth )
#define PyMethodDef_ml_name(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyMethodDef_ml_name((PyMethodDef*) (OBJ)) : ((PyMethodDef*) (OBJ))->ml_name )
#define PyMethodDescrObject_d_method(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyMethodDescrObject_d_method((PyMethodDescrObject*) (OBJ)) : ((PyMethodDescrObject*) (OBJ))->d_method )
#define PyMethodObject_im_func(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyMethodObject_im_func((PyMethodObject*) (OBJ)) : ((PyMethodObject*) (OBJ))->im_func )
#define PyMethodObject_im_self(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyMethodObject_im_self((PyMethodObject*) (OBJ)) : ((PyMethodObject*) (OBJ))->im_self )
#define PyModuleDef_m_doc(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyModuleDef_m_doc((PyModuleDef*) (OBJ)) : ((PyModuleDef*) (OBJ))->m_doc )
#define PyModuleDef_m_methods(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyModuleDef_m_methods((PyModuleDef*) (OBJ)) : ((PyModuleDef*) (OBJ))->m_methods )
#define PyModuleDef_m_name(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyModuleDef_m_name((PyModuleDef*) (OBJ)) : ((PyModuleDef*) (OBJ))->m_name )
#define PyModuleDef_m_size(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyModuleDef_m_size((PyModuleDef*) (OBJ)) : ((PyModuleDef*) (OBJ))->m_size )
#define PyModuleObject_md_def(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyModuleObject_md_def((PyModuleObject*) (OBJ)) : ((PyModuleObject*) (OBJ))->md_def )
#define PyModuleObject_md_dict(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyModuleObject_md_dict((PyModuleObject*) (OBJ)) : ((PyModuleObject*) (OBJ))->md_dict )
#define PyModuleObject_md_state(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyModuleObject_md_state((PyModuleObject*) (OBJ)) : ((PyModuleObject*) (OBJ))->md_state )
#define PyObject_ob_refcnt(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyObject_ob_refcnt((PyObject*) (OBJ)) : ((PyObject*) (OBJ))->ob_refcnt )
#define PyObject_ob_type(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyObject_ob_type((PyObject*) (OBJ)) : ((PyObject*) (OBJ))->ob_type )
#define PySetObject_used(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PySetObject_used((PySetObject*) (OBJ)) : ((PySetObject*) (OBJ))->used )
#define PySliceObject_start(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PySliceObject_start((PySliceObject*) (OBJ)) : ((PySliceObject*) (OBJ))->start )
#define PySliceObject_step(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PySliceObject_step((PySliceObject*) (OBJ)) : ((PySliceObject*) (OBJ))->step )
#define PySliceObject_stop(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PySliceObject_stop((PySliceObject*) (OBJ)) : ((PySliceObject*) (OBJ))->stop )
#define PyTupleObject_ob_item(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyTupleObject_ob_item((PyTupleObject*) (OBJ)) : ((PyTupleObject*) (OBJ))->ob_item )
#define PyUnicodeObject_data(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyUnicodeObject_data((PyUnicodeObject*) (OBJ)) : ((PyUnicodeObject*) (OBJ))->data )
#define PyVarObject_ob_size(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyVarObject_ob_size((PyVarObject*) (OBJ)) : ((PyVarObject*) (OBJ))->ob_size )
#define set_PyByteArrayObject_ob_exports(OBJ, VALUE) { if (points_to_py_handle_space(OBJ)) GraalPy_set_PyByteArrayObject_ob_exports((PyByteArrayObject*) (OBJ), (VALUE)); else  ((PyByteArrayObject*) (OBJ))->ob_exports = (VALUE); }
#define set_PyFrameObject_f_lineno(OBJ, VALUE) { if (points_to_py_handle_space(OBJ)) GraalPy_set_PyFrameObject_f_lineno((PyFrameObject*) (OBJ), (VALUE)); else  ((PyFrameObject*) (OBJ))->f_lineno = (VALUE); }
#define set_PyModuleObject_md_def(OBJ, VALUE) { if (points_to_py_handle_space(OBJ)) GraalPy_set_PyModuleObject_md_def((PyModuleObject*) (OBJ), (VALUE)); else  ((PyModuleObject*) (OBJ))->md_def = (VALUE); }
#define set_PyModuleObject_md_state(OBJ, VALUE) { if (points_to_py_handle_space(OBJ)) GraalPy_set_PyModuleObject_md_state((PyModuleObject*) (OBJ), (VALUE)); else  ((PyModuleObject*) (OBJ))->md_state = (VALUE); }
#define set_PyObject_ob_refcnt(OBJ, VALUE) { if (points_to_py_handle_space(OBJ)) GraalPy_set_PyObject_ob_refcnt((PyObject*) (OBJ), (VALUE)); else  ((PyObject*) (OBJ))->ob_refcnt = (VALUE); }
#define set_PyTypeObject_tp_as_buffer(OBJ, VALUE) { if (points_to_py_handle_space(OBJ)) GraalPy_set_PyTypeObject_tp_as_buffer((PyTypeObject*) (OBJ), (VALUE)); else  ((PyTypeObject*) (OBJ))->tp_as_buffer = (VALUE); }
#define set_PyTypeObject_tp_base(OBJ, VALUE) { if (points_to_py_handle_space(OBJ)) GraalPy_set_PyTypeObject_tp_base((PyTypeObject*) (OBJ), (VALUE)); else  ((PyTypeObject*) (OBJ))->tp_base = (VALUE); }
#define set_PyTypeObject_tp_bases(OBJ, VALUE) { if (points_to_py_handle_space(OBJ)) GraalPy_set_PyTypeObject_tp_bases((PyTypeObject*) (OBJ), (VALUE)); else  ((PyTypeObject*) (OBJ))->tp_bases = (VALUE); }
#define set_PyTypeObject_tp_clear(OBJ, VALUE) { if (points_to_py_handle_space(OBJ)) GraalPy_set_PyTypeObject_tp_clear((PyTypeObject*) (OBJ), (VALUE)); else  ((PyTypeObject*) (OBJ))->tp_clear = (VALUE); }
#define set_PyTypeObject_tp_dict(OBJ, VALUE) { if (points_to_py_handle_space(OBJ)) GraalPy_set_PyTypeObject_tp_dict((PyTypeObject*) (OBJ), (VALUE)); else  ((PyTypeObject*) (OBJ))->tp_dict = (VALUE); }
#define set_PyTypeObject_tp_dictoffset(OBJ, VALUE) { if (points_to_py_handle_space(OBJ)) GraalPy_set_PyTypeObject_tp_dictoffset((PyTypeObject*) (OBJ), (VALUE)); else  ((PyTypeObject*) (OBJ))->tp_dictoffset = (VALUE); }
#define set_PyTypeObject_tp_finalize(OBJ, VALUE) { if (points_to_py_handle_space(OBJ)) GraalPy_set_PyTypeObject_tp_finalize((PyTypeObject*) (OBJ), (VALUE)); else  ((PyTypeObject*) (OBJ))->tp_finalize = (VALUE); }
#define set_PyTypeObject_tp_getattr(OBJ, VALUE) { if (points_to_py_handle_space(OBJ)) GraalPy_set_PyTypeObject_tp_getattr((PyTypeObject*) (OBJ), (VALUE)); else  ((PyTypeObject*) (OBJ))->tp_getattr = (VALUE); }
#define set_PyTypeObject_tp_getattro(OBJ, VALUE) { if (points_to_py_handle_space(OBJ)) GraalPy_set_PyTypeObject_tp_getattro((PyTypeObject*) (OBJ), (VALUE)); else  ((PyTypeObject*) (OBJ))->tp_getattro = (VALUE); }
#define set_PyTypeObject_tp_iter(OBJ, VALUE) { if (points_to_py_handle_space(OBJ)) GraalPy_set_PyTypeObject_tp_iter((PyTypeObject*) (OBJ), (VALUE)); else  ((PyTypeObject*) (OBJ))->tp_iter = (VALUE); }
#define set_PyTypeObject_tp_iternext(OBJ, VALUE) { if (points_to_py_handle_space(OBJ)) GraalPy_set_PyTypeObject_tp_iternext((PyTypeObject*) (OBJ), (VALUE)); else  ((PyTypeObject*) (OBJ))->tp_iternext = (VALUE); }
#define set_PyTypeObject_tp_mro(OBJ, VALUE) { if (points_to_py_handle_space(OBJ)) GraalPy_set_PyTypeObject_tp_mro((PyTypeObject*) (OBJ), (VALUE)); else  ((PyTypeObject*) (OBJ))->tp_mro = (VALUE); }
#define set_PyTypeObject_tp_new(OBJ, VALUE) { if (points_to_py_handle_space(OBJ)) GraalPy_set_PyTypeObject_tp_new((PyTypeObject*) (OBJ), (VALUE)); else  ((PyTypeObject*) (OBJ))->tp_new = (VALUE); }
#define set_PyTypeObject_tp_setattr(OBJ, VALUE) { if (points_to_py_handle_space(OBJ)) GraalPy_set_PyTypeObject_tp_setattr((PyTypeObject*) (OBJ), (VALUE)); else  ((PyTypeObject*) (OBJ))->tp_setattr = (VALUE); }
#define set_PyTypeObject_tp_setattro(OBJ, VALUE) { if (points_to_py_handle_space(OBJ)) GraalPy_set_PyTypeObject_tp_setattro((PyTypeObject*) (OBJ), (VALUE)); else  ((PyTypeObject*) (OBJ))->tp_setattro = (VALUE); }
#define set_PyTypeObject_tp_subclasses(OBJ, VALUE) { if (points_to_py_handle_space(OBJ)) GraalPy_set_PyTypeObject_tp_subclasses((PyTypeObject*) (OBJ), (VALUE)); else  ((PyTypeObject*) (OBJ))->tp_subclasses = (VALUE); }
#define set_PyTypeObject_tp_traverse(OBJ, VALUE) { if (points_to_py_handle_space(OBJ)) GraalPy_set_PyTypeObject_tp_traverse((PyTypeObject*) (OBJ), (VALUE)); else  ((PyTypeObject*) (OBJ))->tp_traverse = (VALUE); }
#define set_PyTypeObject_tp_weaklistoffset(OBJ, VALUE) { if (points_to_py_handle_space(OBJ)) GraalPy_set_PyTypeObject_tp_weaklistoffset((PyTypeObject*) (OBJ), (VALUE)); else  ((PyTypeObject*) (OBJ))->tp_weaklistoffset = (VALUE); }
#define set_PyVarObject_ob_size(OBJ, VALUE) { if (points_to_py_handle_space(OBJ)) GraalPy_set_PyVarObject_ob_size((PyVarObject*) (OBJ), (VALUE)); else  ((PyVarObject*) (OBJ))->ob_size = (VALUE); }

#define NB_ADD 1
#define NB_SUBTRACT 2
#define NB_MULTIPLY 4
#define NB_REMAINDER 8
#define NB_DIVMOD 16
#define NB_POWER 32
#define NB_NEGATIVE 64
#define NB_POSITIVE 128
#define NB_ABSOLUTE 256
#define NB_BOOL 512
#define NB_INVERT 1024
#define NB_LSHIFT 2048
#define NB_RSHIFT 4096
#define NB_AND 8192
#define NB_XOR 16384
#define NB_OR 32768
#define NB_INT 65536
#define NB_FLOAT 262144
#define NB_INPLACE_ADD 524288
#define NB_INPLACE_SUBTRACT 1048576
#define NB_INPLACE_MULTIPLY 2097152
#define NB_INPLACE_REMAINDER 4194304
#define NB_INPLACE_POWER 8388608
#define NB_INPLACE_LSHIFT 16777216
#define NB_INPLACE_RSHIFT 33554432
#define NB_INPLACE_AND 67108864
#define NB_INPLACE_XOR 134217728
#define NB_INPLACE_OR 268435456
#define NB_FLOOR_DIVIDE 536870912
#define NB_TRUE_DIVIDE 1073741824
#define NB_INPLACE_FLOOR_DIVIDE 2147483648
#define NB_INPLACE_TRUE_DIVIDE 4294967296
#define NB_INDEX 8589934592
#define NB_MATRIX_MULTIPLY 17179869184
#define NB_INPLACE_MATRIX_MULTIPLY 34359738368
#define SQ_LENGTH 1099511627776
#define SQ_CONCAT 2199023255552
#define SQ_REPEAT 4398046511104
#define SQ_ITEM 8796093022208
#define SQ_ASS_ITEM 35184372088832
#define SQ_CONTAINS 140737488355328
#define SQ_INPLACE_CONCAT 281474976710656
#define SQ_INPLACE_REPEAT 562949953421312
#define MP_LENGTH 1125899906842624
#define MP_SUBSCRIPT 2251799813685248
#define MP_ASS_SUBSCRIPT 4503599627370496
#define AM_AWAIT 18014398509481984
#define AM_AITER 36028797018963968
#define AM_ANEXT 72057594037927936
#define AM_SEND 144115188075855872
// {{end CAPI_BUILTINS}}


#define BUILTIN(NAME, RET, ...) extern PyAPI_FUNC(RET) (*Graal##NAME)(__VA_ARGS__);
CAPI_BUILTINS
#undef BUILTIN

#define CALL_WITH_STRING(STRING, RESULT_TYPE, ERR_RESULT, NAME, ...) \
	PyObject* string = PyUnicode_FromString(STRING); \
	if (string == NULL) { \
		return ERR_RESULT; \
	} \
	RESULT_TYPE result = NAME(__VA_ARGS__); \
	Py_DECREF(string); \
	return result;

#define GET_SLOT_SPECIAL(OBJ, RECEIVER, NAME, SPECIAL) ( points_to_py_handle_space(OBJ) ? GraalPy_get_##RECEIVER##_##NAME((RECEIVER*) (OBJ)) : ((RECEIVER*) (OBJ))->SPECIAL )

PyAPI_DATA(uint32_t) Py_Truffle_Options;
PyAPI_DATA(PyObject*) _PyTruffle_Zero;
PyAPI_DATA(PyObject*) _PyTruffle_One;

/* Flags definitions representing global (debug) options. */
static MUST_INLINE int PyTruffle_Trace_Memory() {
	return Py_Truffle_Options & PY_TRUFFLE_TRACE_MEM;
}
static MUST_INLINE int PyTruffle_Log_Info() {
	return Py_Truffle_Options & PY_TRUFFLE_LOG_INFO;
}
static MUST_INLINE int PyTruffle_Log_Config() {
	return Py_Truffle_Options & PY_TRUFFLE_LOG_CONFIG;
}
static MUST_INLINE int PyTruffle_Log_Fine() {
	return Py_Truffle_Options & PY_TRUFFLE_LOG_FINE;
}
static MUST_INLINE int PyTruffle_Log_Finer() {
	return Py_Truffle_Options & PY_TRUFFLE_LOG_FINER;
}
static MUST_INLINE int PyTruffle_Log_Finest() {
	return Py_Truffle_Options & PY_TRUFFLE_LOG_FINEST;
}
static MUST_INLINE int PyTruffle_Debug_CAPI() {
	return Py_Truffle_Options & PY_TRUFFLE_DEBUG_CAPI;
}

static void PyTruffle_Log(int level, const char* format, ... ) {
	if (Py_Truffle_Options & level) {
		char buffer[1024];
		va_list args;
		va_start(args, format);
		vsprintf(buffer,format, args);
		GraalPyTruffle_LogString(level, buffer);
		va_end(args);
	}
}

Py_LOCAL_SYMBOL int is_builtin_type(PyTypeObject *tp);


#define JWRAPPER_DIRECT                      1
#define JWRAPPER_FASTCALL                    2
#define JWRAPPER_FASTCALL_WITH_KEYWORDS      3
#define JWRAPPER_KEYWORDS                    4
#define JWRAPPER_VARARGS                     5
#define JWRAPPER_NOARGS                      6
#define JWRAPPER_O                           7
#define JWRAPPER_METHOD                      8
#define JWRAPPER_UNSUPPORTED                 9
#define JWRAPPER_ALLOC                       10
#define JWRAPPER_SSIZE_ARG                   JWRAPPER_ALLOC
#define JWRAPPER_GETATTR                     11
#define JWRAPPER_SETATTR                     12
#define JWRAPPER_RICHCMP                     13
#define JWRAPPER_SETITEM                     14
#define JWRAPPER_UNARYFUNC                   15
#define JWRAPPER_BINARYFUNC                  16
#define JWRAPPER_BINARYFUNC_L                17
#define JWRAPPER_BINARYFUNC_R                18
#define JWRAPPER_TERNARYFUNC                 19
#define JWRAPPER_TERNARYFUNC_R               20
#define JWRAPPER_LT                          21
#define JWRAPPER_LE                          22
#define JWRAPPER_EQ                          23
#define JWRAPPER_NE                          24
#define JWRAPPER_GT                          25
#define JWRAPPER_GE                          26
#define JWRAPPER_ITERNEXT                    27
#define JWRAPPER_INQUIRY                     28
#define JWRAPPER_DELITEM                     29
#define JWRAPPER_GETITEM                     30
#define JWRAPPER_GETTER                      31
#define JWRAPPER_SETTER                      32
#define JWRAPPER_INITPROC                    33
#define JWRAPPER_HASHFUNC                    34
#define JWRAPPER_CALL                        35
#define JWRAPPER_SETATTRO                    36
#define JWRAPPER_DESCR_GET                   37
#define JWRAPPER_DESCR_SET                   38
#define JWRAPPER_LENFUNC                     39
#define JWRAPPER_OBJOBJPROC                  40
#define JWRAPPER_OBJOBJARGPROC               41
#define JWRAPPER_NEW                         42
#define JWRAPPER_MP_DELITEM                  43
#define JWRAPPER_STR                         44
#define JWRAPPER_REPR                        45


static inline int get_method_flags_wrapper(int flags) {
    if (flags < 0)
        return JWRAPPER_DIRECT;
    if ((flags & (METH_FASTCALL | METH_KEYWORDS | METH_METHOD)) == (METH_FASTCALL | METH_KEYWORDS | METH_METHOD))
        return JWRAPPER_METHOD;
    if ((flags & (METH_FASTCALL | METH_KEYWORDS)) == (METH_FASTCALL | METH_KEYWORDS))
        return JWRAPPER_FASTCALL_WITH_KEYWORDS;
    if (flags & METH_FASTCALL)
        return JWRAPPER_FASTCALL;
    if (flags & METH_KEYWORDS)
        return JWRAPPER_KEYWORDS;
    if (flags & METH_VARARGS)
        return JWRAPPER_VARARGS;
    if (flags & METH_NOARGS)
        return JWRAPPER_NOARGS;
    if (flags & METH_O)
        return JWRAPPER_O;
    return JWRAPPER_UNSUPPORTED;
}

#define MANAGED_REFCNT 10
#define HANDLE_BASE 0x8000000000000000ULL
#define IMMORTAL_REFCNT (INT64_MAX >> 1)

#ifdef GRAALVM_PYTHON_LLVM_MANAGED
#define points_to_py_handle_space(PTR) polyglot_is_value((PTR))
#else /* GRAALVM_PYTHON_LLVM_MANAGED */

#define points_to_py_handle_space(PTR) ((((uintptr_t) (PTR)) & HANDLE_BASE) != 0)

static MUST_INLINE PyObject *stub_to_pointer(PyObject *stub_ptr)
{
    return ((uintptr_t) stub_ptr) | HANDLE_BASE;
}

static MUST_INLINE PyObject *pointer_to_stub(PyObject *o)
{
    return ((uintptr_t) o) & ~HANDLE_BASE;
}
#endif /* GRAALVM_PYTHON_LLVM_MANAGED */

void register_native_slots(PyTypeObject* managed_class, PyGetSetDef* getsets, PyMemberDef* members);

// export the SizeT arg parse functions, because we use them in contrast to cpython on windows for core modules that we link dynamically
PyAPI_FUNC(int) _PyArg_Parse_SizeT(PyObject *, const char *, ...);
PyAPI_FUNC(int) _PyArg_ParseTuple_SizeT(PyObject *, const char *, ...);
PyAPI_FUNC(int) _PyArg_ParseTupleAndKeywords_SizeT(PyObject *, PyObject *,
                                                  const char *, char **, ...);
PyAPI_FUNC(int) _PyArg_VaParse_SizeT(PyObject *, const char *, va_list);
PyAPI_FUNC(int) _PyArg_VaParseTupleAndKeywords_SizeT(PyObject *, PyObject *,
                                                  const char *, char **, va_list);

extern size_t PyTruffle_AllocatedMemory;
extern size_t PyTruffle_MaxNativeMemory;
extern size_t PyTruffle_NativeMemoryGCBarrier;

/*
 * alphabetical but: according to CPython's '_PyTypes_Init': first
 * PyBaseObject_Type, then PyType_Type, ...
 */
#define PY_TYPE_OBJECTS \
PY_TRUFFLE_TYPE_WITH_ALLOC(PyBaseObject_Type, "object",			    &PyType_Type, sizeof(PyObject), PyType_GenericAlloc, object_dealloc, PyObject_Del) \
PY_TRUFFLE_TYPE_GENERIC(PyType_Type,    "type", 				    &PyType_Type, sizeof(PyHeapTypeObject), sizeof(PyMemberDef), PyType_GenericAlloc, object_dealloc, PyObject_GC_Del, 0) \
PY_TRUFFLE_TYPE(PyCFunction_Type, 		"builtin_function_or_method", &PyType_Type, sizeof(PyCFunctionObject)) \
PY_TRUFFLE_TYPE(_PyBytesIOBuffer_Type,	"_BytesIOBuffer", 		    &PyType_Type, 0) \
PY_TRUFFLE_TYPE(_PyExc_BaseException, 	"BaseException", 			&PyType_Type, sizeof(PyBaseExceptionObject)) \
PY_TRUFFLE_TYPE(_PyExc_Exception, 		"Exception", 				&PyType_Type, sizeof(PyBaseExceptionObject)) \
PY_TRUFFLE_TYPE(_PyExc_StopIteration, 	"StopIteration", 			&PyType_Type, sizeof(PyStopIterationObject)) \
PY_TRUFFLE_TYPE(_PyNamespace_Type, 		"SimpleNamespace", 		    &PyType_Type, sizeof(_PyNamespaceObject)) \
PY_TRUFFLE_TYPE(_PyNone_Type, 			"NoneType", 				&PyType_Type, 0) \
PY_TRUFFLE_TYPE(_PyNotImplemented_Type, "NotImplementedType", 	    &PyType_Type, 0) \
PY_TRUFFLE_TYPE(_PyWeakref_CallableProxyType, "_weakref.CallableProxyType", &PyType_Type, sizeof(PyWeakReference)) \
PY_TRUFFLE_TYPE(_PyWeakref_ProxyType, 	"_weakref.ProxyType",       &PyType_Type, sizeof(PyWeakReference)) \
PY_TRUFFLE_TYPE(_PyWeakref_RefType, 	"_weakref.ReferenceType",   &PyType_Type, sizeof(PyWeakReference)) \
PY_TRUFFLE_TYPE(Arraytype,			 	"array", 					&PyType_Type, sizeof(arrayobject)) \
PY_TRUFFLE_TYPE(mmap_object_type, 		"mmap.mmap", 				&PyType_Type, 0) \
PY_TRUFFLE_TYPE(PyArrayIter_Type, 		"arrayiterator", 			&PyType_Type, sizeof(arrayiterobject)) \
PY_TRUFFLE_TYPE(PyAsyncGen_Type, 		"async_generator", 	    	&PyType_Type, sizeof(PyAsyncGenObject)) \
PY_TRUFFLE_TYPE_WITH_ITEMSIZE(PyLong_Type, "int", 			    	&PyType_Type, offsetof(PyLongObject, ob_digit), sizeof(PyObject *)) \
PY_TRUFFLE_TYPE(PyBool_Type, 			"bool", 					&PyType_Type, sizeof(struct _longobject)) \
PY_TRUFFLE_TYPE(PyByteArray_Type, 		"bytearray", 				&PyType_Type, sizeof(PyByteArrayObject)) \
PY_TRUFFLE_TYPE_WITH_ITEMSIZE(PyBytes_Type, "bytes", 				&PyType_Type, PyBytesObject_SIZE, sizeof(char)) \
PY_TRUFFLE_TYPE(PyCapsule_Type, 		"capsule", 			    	&PyType_Type, sizeof(PyCapsule)) \
PY_TRUFFLE_TYPE(PyCell_Type, 			"cell", 					&PyType_Type, sizeof(PyCellObject)) \
PY_TRUFFLE_TYPE(PyCMethod_Type, 		"builtin_method", 	    	&PyCFunction_Type, sizeof(PyCFunctionObject)) \
PY_TRUFFLE_TYPE(PyCode_Type, 			"code", 					&PyType_Type, sizeof(PyTypeObject)) \
PY_TRUFFLE_TYPE(PyComplex_Type, 		"complex", 			    	&PyType_Type, sizeof(PyComplexObject)) \
PY_TRUFFLE_TYPE(PyDict_Type, 			"dict", 					&PyType_Type, sizeof(PyDictObject)) \
PY_TRUFFLE_TYPE(PyDictProxy_Type, 		"mappingproxy", 			&PyType_Type, sizeof(mappingproxyobject)) \
PY_TRUFFLE_TYPE(PyEllipsis_Type, 		"ellipsis", 				&PyType_Type, 0) \
PY_TRUFFLE_TYPE(PyFloat_Type, 			"float", 					&PyType_Type, sizeof(PyFloatObject)) \
PY_TRUFFLE_TYPE_WITH_ITEMSIZE(PyFrame_Type, "frame", 				&PyType_Type, sizeof(PyTypeObject), sizeof(PyObject *)) \
PY_TRUFFLE_TYPE(PyFrozenSet_Type, 		"frozenset", 				&PyType_Type, sizeof(PySetObject)) \
PY_TRUFFLE_TYPE(PyFunction_Type, 		"function", 				&PyType_Type, sizeof(PyFunctionObject)) \
PY_TRUFFLE_TYPE(PyGen_Type, 			"generator", 				&PyType_Type, sizeof(PyGenObject)) \
PY_TRUFFLE_TYPE(PyGetSetDescr_Type, 	"getset_descriptor", 		&PyType_Type, sizeof(PyGetSetDescrObject)) \
PY_TRUFFLE_TYPE(PyInstanceMethod_Type, 	"instancemethod", 	    	&PyType_Type, sizeof(PyInstanceMethodObject)) \
PY_TRUFFLE_TYPE(PyList_Type, 			"list", 					&PyType_Type, sizeof(PyListObject)) \
PY_TRUFFLE_TYPE(PyMap_Type, 			"map", 				    	&PyType_Type, sizeof(mapobject)) \
PY_TRUFFLE_TYPE(PyMemberDescr_Type, 	"member_descriptor", 		&PyType_Type, sizeof(PyMemberDescrObject)) \
PY_TRUFFLE_TYPE_WITH_ITEMSIZE(PyMemoryView_Type, "memoryview",      &PyType_Type, offsetof(PyMemoryViewObject, ob_array), sizeof(Py_ssize_t)) \
PY_TRUFFLE_TYPE(PyMethod_Type, 			"method", 				    &PyType_Type, sizeof(PyMethodObject)) \
PY_TRUFFLE_TYPE(PyMethodDescr_Type, 	"method_descriptor", 		&PyType_Type, sizeof(PyMethodDescrObject)) \
PY_TRUFFLE_TYPE(PyModule_Type, 			"module", 				    &PyType_Type, sizeof(PyModuleObject)) \
PY_TRUFFLE_TYPE(PyModuleDef_Type, 		"moduledef", 				&PyType_Type, sizeof(struct PyModuleDef)) \
PY_TRUFFLE_TYPE(PyProperty_Type, 		"property", 				&PyType_Type, sizeof(propertyobject)) \
PY_TRUFFLE_TYPE(PyRange_Type, 			"range", 					&PyType_Type, sizeof(rangeobject)) \
PY_TRUFFLE_TYPE(PySet_Type, 			"set", 					    &PyType_Type, sizeof(PySetObject)) \
PY_TRUFFLE_TYPE(PySlice_Type, 			"slice", 					&PyType_Type, sizeof(PySliceObject)) \
PY_TRUFFLE_TYPE(PyStaticMethod_Type, 	"staticmethod", 			&PyType_Type, sizeof(PyObject)) \
PY_TRUFFLE_TYPE(PySuper_Type, 			"super", 					&PyType_Type, sizeof(superobject)) \
PY_TRUFFLE_TYPE(PyTraceBack_Type, 		"traceback", 				&PyType_Type, sizeof(PyTypeObject)) \
PY_TRUFFLE_TYPE_GENERIC(PyTuple_Type, 	"tuple", 					&PyType_Type, sizeof(PyTupleObject) - sizeof(PyObject *), sizeof(PyObject *), PyTruffle_Tuple_Alloc, (destructor)PyTruffle_Tuple_Dealloc, 0, 0) \
PY_TRUFFLE_TYPE(PyUnicode_Type, 		"str", 					    &PyType_Type, sizeof(PyUnicodeObject)) \
/* NOTE: we use the same Python type (namely 'PBuiltinFunction') for 'wrapper_descriptor' as for 'method_descriptor'; so the flags must be the same! */ \
PY_TRUFFLE_TYPE(PyWrapperDescr_Type, 	"wrapper_descriptor",       &PyType_Type, sizeof(PyWrapperDescrObject)) \
PY_TRUFFLE_TYPE(PyZip_Type, 			"zip", 					    &PyType_Type, sizeof(zipobject)) \
PY_TRUFFLE_TYPE(PyReversed_Type, 		"reversed", 				&PyType_Type, sizeof(PyObject)) \
PY_TRUFFLE_TYPE(cycle_type, 			"cycle", 					&PyType_Type, sizeof(PyObject)) \
PY_TRUFFLE_TYPE(PySeqIter_Type, 		"iterator", 				&PyType_Type, sizeof(PyObject)) \
PY_TRUFFLE_TYPE(PyEnum_Type, 			"enumerate",				&PyType_Type, sizeof(PyObject)) \
PY_TRUFFLE_TYPE(PyCSimpleType, 			"PyCSimpleType",			&PyType_Type, sizeof(PyObject)) \
PY_TRUFFLE_TYPE(PyCData_Type, 			"_CData",					&PyType_Type, sizeof(PyObject)) \
PY_TRUFFLE_TYPE(Simple_Type, 			"_SimpleCData",			    &PyType_Type, sizeof(PyObject)) \
PY_TRUFFLE_TYPE(PyCStructType_Type, 	"PyCStructType",			&PyType_Type, sizeof(PyObject)) \
PY_TRUFFLE_TYPE(UnionType_Type, 		"_ctypes.UnionType",		&PyType_Type, sizeof(PyObject)) \
PY_TRUFFLE_TYPE(PyCPointerType_Type,	"PyCPointerType", 		    &PyType_Type, sizeof(PyObject)) \
PY_TRUFFLE_TYPE(PyCArrayType_Type,		"PyCArrayType", 			&PyType_Type, sizeof(PyObject)) \
PY_TRUFFLE_TYPE(PyCoro_Type, 			"coroutine", 				&PyType_Type, sizeof(PyCoroObject)) \
PY_TRUFFLE_TYPE_UNIMPLEMENTED(_PyAIterWrapper_Type) \
PY_TRUFFLE_TYPE_UNIMPLEMENTED(_PyAsyncGenASend_Type) \
PY_TRUFFLE_TYPE_UNIMPLEMENTED(_PyAsyncGenAThrow_Type) \
PY_TRUFFLE_TYPE_UNIMPLEMENTED(_PyAsyncGenWrappedValue_Type) \
PY_TRUFFLE_TYPE_UNIMPLEMENTED(_PyCoroWrapper_Type) \
PY_TRUFFLE_TYPE_UNIMPLEMENTED(_PyHamt_ArrayNode_Type) \
PY_TRUFFLE_TYPE_UNIMPLEMENTED(_PyHamt_BitmapNode_Type) \
PY_TRUFFLE_TYPE_UNIMPLEMENTED(_PyHamt_CollisionNode_Type) \
PY_TRUFFLE_TYPE_UNIMPLEMENTED(_PyHamt_Type) \
PY_TRUFFLE_TYPE_UNIMPLEMENTED(_PyHamtItems_Type) \
PY_TRUFFLE_TYPE_UNIMPLEMENTED(_PyHamtKeys_Type) \
PY_TRUFFLE_TYPE_UNIMPLEMENTED(_PyHamtValues_Type) \
PY_TRUFFLE_TYPE_UNIMPLEMENTED(_PyInterpreterID_Type) \
PY_TRUFFLE_TYPE_UNIMPLEMENTED(_PyManagedBuffer_Type) \
PY_TRUFFLE_TYPE_UNIMPLEMENTED(_PyMethodWrapper_Type) \
PY_TRUFFLE_TYPE_UNIMPLEMENTED(PyByteArrayIter_Type) \
PY_TRUFFLE_TYPE_UNIMPLEMENTED(PyBytesIter_Type) \
PY_TRUFFLE_TYPE_UNIMPLEMENTED(PyCallIter_Type) \
PY_TRUFFLE_TYPE_UNIMPLEMENTED(PyClassMethod_Type) \
PY_TRUFFLE_TYPE_UNIMPLEMENTED(PyClassMethodDescr_Type) \
PY_TRUFFLE_TYPE_UNIMPLEMENTED(PyCmpWrapper_Type) \
PY_TRUFFLE_TYPE_UNIMPLEMENTED(PyContext_Type) \
PY_TRUFFLE_TYPE_UNIMPLEMENTED(PyContextToken_Type) \
PY_TRUFFLE_TYPE_UNIMPLEMENTED(PyContextVar_Type) \
PY_TRUFFLE_TYPE_UNIMPLEMENTED(PyDictItems_Type) \
PY_TRUFFLE_TYPE_UNIMPLEMENTED(PyDictIterItem_Type) \
PY_TRUFFLE_TYPE_UNIMPLEMENTED(PyDictIterKey_Type) \
PY_TRUFFLE_TYPE_UNIMPLEMENTED(PyDictIterValue_Type) \
PY_TRUFFLE_TYPE_UNIMPLEMENTED(PyDictKeys_Type) \
PY_TRUFFLE_TYPE_UNIMPLEMENTED(PyDictRevIterItem_Type) \
PY_TRUFFLE_TYPE_UNIMPLEMENTED(PyDictRevIterKey_Type) \
PY_TRUFFLE_TYPE_UNIMPLEMENTED(PyDictRevIterValue_Type) \
PY_TRUFFLE_TYPE_UNIMPLEMENTED(PyDictValues_Type) \
PY_TRUFFLE_TYPE_UNIMPLEMENTED(PyFilter_Type) \
PY_TRUFFLE_TYPE_UNIMPLEMENTED(PyListIter_Type) \
PY_TRUFFLE_TYPE_UNIMPLEMENTED(PyListRevIter_Type) \
PY_TRUFFLE_TYPE_UNIMPLEMENTED(PyLongRangeIter_Type) \
PY_TRUFFLE_TYPE_UNIMPLEMENTED(PyNullImporter_Type) \
PY_TRUFFLE_TYPE_UNIMPLEMENTED(PyODict_Type) \
PY_TRUFFLE_TYPE_UNIMPLEMENTED(PyODictItems_Type) \
PY_TRUFFLE_TYPE_UNIMPLEMENTED(PyODictIter_Type) \
PY_TRUFFLE_TYPE_UNIMPLEMENTED(PyODictKeys_Type) \
PY_TRUFFLE_TYPE_UNIMPLEMENTED(PyODictValues_Type) \
PY_TRUFFLE_TYPE_UNIMPLEMENTED(PyPickleBuffer_Type) \
PY_TRUFFLE_TYPE_UNIMPLEMENTED(PyRangeIter_Type) \
PY_TRUFFLE_TYPE_UNIMPLEMENTED(PySetIter_Type) \
PY_TRUFFLE_TYPE_UNIMPLEMENTED(PySortWrapper_Type) \
PY_TRUFFLE_TYPE_UNIMPLEMENTED(PyStaticMethod_Type) \
PY_TRUFFLE_TYPE_UNIMPLEMENTED(PyStdPrinter_Type) \
PY_TRUFFLE_TYPE_UNIMPLEMENTED(PySTEntry_Type) \
PY_TRUFFLE_TYPE_UNIMPLEMENTED(PyTupleIter_Type) \
PY_TRUFFLE_TYPE_UNIMPLEMENTED(PyUnicodeIter_Type) \


#define PY_TRUFFLE_TYPE_WITH_ALLOC(GLOBAL_NAME, __TYPE_NAME__, __SUPER_TYPE__, __SIZE__, __ALLOC__, __DEALLOC__, __FREE__) PY_TRUFFLE_TYPE_GENERIC(GLOBAL_NAME, __TYPE_NAME__, __SUPER_TYPE__, __SIZE__, 0, __ALLOC__, __DEALLOC__, __FREE__, 0)
#define PY_TRUFFLE_TYPE(GLOBAL_NAME, __TYPE_NAME__, __SUPER_TYPE__, __SIZE__) PY_TRUFFLE_TYPE_GENERIC(GLOBAL_NAME, __TYPE_NAME__, __SUPER_TYPE__, __SIZE__, 0, 0, 0, 0, 0)
#define PY_TRUFFLE_TYPE_WITH_ITEMSIZE(GLOBAL_NAME, __TYPE_NAME__, __SUPER_TYPE__, __SIZE__, __ITEMSIZE__) PY_TRUFFLE_TYPE_GENERIC(GLOBAL_NAME, __TYPE_NAME__, __SUPER_TYPE__, __SIZE__, __ITEMSIZE__, 0, 0, 0, 0)


#define PY_EXCEPTIONS \
EXCEPTION(ArithmeticError) \
EXCEPTION(AssertionError) \
EXCEPTION(AttributeError) \
EXCEPTION(BaseException) \
EXCEPTION(BlockingIOError) \
EXCEPTION(BrokenPipeError) \
EXCEPTION(BufferError) \
EXCEPTION(BytesWarning) \
EXCEPTION(ChildProcessError) \
EXCEPTION(ConnectionAbortedError) \
EXCEPTION(ConnectionError) \
EXCEPTION(ConnectionRefusedError) \
EXCEPTION(ConnectionResetError) \
EXCEPTION(DeprecationWarning) \
EXCEPTION(EncodingWarning) \
EXCEPTION(EnvironmentError) \
EXCEPTION(EOFError) \
EXCEPTION(Exception) \
EXCEPTION(FileExistsError) \
EXCEPTION(FileNotFoundError) \
EXCEPTION(FloatingPointError) \
EXCEPTION(FutureWarning) \
EXCEPTION(GeneratorExit) \
EXCEPTION(ImportError) \
EXCEPTION(ImportWarning) \
EXCEPTION(IndentationError) \
EXCEPTION(IndexError) \
EXCEPTION(InterruptedError) \
EXCEPTION(IOError) \
EXCEPTION(IsADirectoryError) \
EXCEPTION(KeyboardInterrupt) \
EXCEPTION(KeyError) \
EXCEPTION(LookupError) \
EXCEPTION(MemoryError) \
EXCEPTION(ModuleNotFoundError) \
EXCEPTION(NameError) \
EXCEPTION(NotADirectoryError) \
EXCEPTION(NotImplementedError) \
EXCEPTION(OSError) \
EXCEPTION(OverflowError) \
EXCEPTION(PendingDeprecationWarning) \
EXCEPTION(PermissionError) \
EXCEPTION(ProcessLookupError) \
EXCEPTION(RecursionError) \
EXCEPTION(ReferenceError) \
EXCEPTION(ResourceWarning) \
EXCEPTION(RuntimeError) \
EXCEPTION(RuntimeWarning) \
EXCEPTION(StopAsyncIteration) \
EXCEPTION(StopIteration) \
EXCEPTION(SyntaxError) \
EXCEPTION(SyntaxWarning) \
EXCEPTION(SystemError) \
EXCEPTION(SystemExit) \
EXCEPTION(TabError) \
EXCEPTION(TimeoutError) \
EXCEPTION(TypeError) \
EXCEPTION(UnboundLocalError) \
EXCEPTION(UnicodeDecodeError) \
EXCEPTION(UnicodeEncodeError) \
EXCEPTION(UnicodeError) \
EXCEPTION(UnicodeTranslateError) \
EXCEPTION(UnicodeWarning) \
EXCEPTION(UserWarning) \
EXCEPTION(ValueError) \
EXCEPTION(Warning) \
EXCEPTION(ZeroDivisionError) \

#endif // CAPI_H
