/*
 * Copyright (c) 2017, 2023, Oracle and/or its affiliates. All rights reserved.
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

#define MUST_INLINE __attribute__((always_inline)) inline
#define NO_INLINE __attribute__((noinline))

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

#ifndef EXCLUDE_POLYGLOT_API
#include <graalvm/llvm/polyglot.h>
#include <truffle.h>
#include <graalvm/llvm/handles.h>
#endif

#define SRC_CS "utf-8"

/* Flags definitions representing global (debug) options. */
#define PY_TRUFFLE_TRACE_MEM 0x1
#define PY_TRUFFLE_LOG_INFO 0x2
#define PY_TRUFFLE_LOG_CONFIG 0x4
#define PY_TRUFFLE_LOG_FINE 0x8
#define PY_TRUFFLE_LOG_FINER 0x10
#define PY_TRUFFLE_LOG_FINEST 0x20

typedef struct mmap_object mmap_object;

/* Private types are defined here because we need to declare the type cast. */

typedef struct {
    PyObject_VAR_HEAD
    int readonly;
    void *buf_delegate;
} PyBufferDecorator;

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
// GENERATED CODE - see PythonCextBuiltins
#define CAPI_BUILTINS \
    BUILTIN(0, PyByteArray_Resize, int, PyObject*, Py_ssize_t) \
    BUILTIN(1, PyBytes_FromObject, PyObject*, PyObject*) \
    BUILTIN(2, PyBytes_Size, Py_ssize_t, PyObject*) \
    BUILTIN(3, PyCallIter_New, PyObject*, PyObject*, PyObject*) \
    BUILTIN(4, PyCallable_Check, int, PyObject*) \
    BUILTIN(5, PyCapsule_GetContext, void*, PyObject*) \
    BUILTIN(6, PyCapsule_GetDestructor, PyCapsule_Destructor, PyObject*) \
    BUILTIN(7, PyCapsule_GetName, const char*, PyObject*) \
    BUILTIN(8, PyCapsule_GetPointer, void*, PyObject*, const char*) \
    BUILTIN(9, PyCapsule_Import, void*, const char*, int) \
    BUILTIN(10, PyCapsule_IsValid, int, PyObject*, const char*) \
    BUILTIN(11, PyCapsule_New, PyObject*, void*, const char*, PyCapsule_Destructor) \
    BUILTIN(12, PyCapsule_SetContext, int, PyObject*, void*) \
    BUILTIN(13, PyCapsule_SetDestructor, int, PyObject*, PyCapsule_Destructor) \
    BUILTIN(14, PyCapsule_SetName, int, PyObject*, const char*) \
    BUILTIN(15, PyCapsule_SetPointer, int, PyObject*, void*) \
    BUILTIN(16, PyClassMethod_New, PyObject*, PyObject*) \
    BUILTIN(17, PyCode_New, PyCodeObject*, int, int, int, int, int, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, int, PyObject*) \
    BUILTIN(18, PyCode_NewEmpty, PyCodeObject*, const char*, const char*, int) \
    BUILTIN(19, PyCode_NewWithPosOnlyArgs, PyCodeObject*, int, int, int, int, int, int, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, int, PyObject*) \
    BUILTIN(20, PyComplex_FromDoubles, PyObject*, double, double) \
    BUILTIN(21, PyComplex_ImagAsDouble, double, PyObject*) \
    BUILTIN(22, PyComplex_RealAsDouble, double, PyObject*) \
    BUILTIN(23, PyContextVar_New, PyObject*, const char*, PyObject*) \
    BUILTIN(24, PyContextVar_Set, PyObject*, PyObject*, PyObject*) \
    BUILTIN(25, PyDictProxy_New, PyObject*, PyObject*) \
    BUILTIN(26, PyDict_Clear, void, PyObject*) \
    BUILTIN(27, PyDict_Contains, int, PyObject*, PyObject*) \
    BUILTIN(28, PyDict_Copy, PyObject*, PyObject*) \
    BUILTIN(29, PyDict_DelItem, int, PyObject*, PyObject*) \
    BUILTIN(30, PyDict_GetItem, PyObject*, PyObject*, PyObject*) \
    BUILTIN(31, PyDict_GetItemWithError, PyObject*, PyObject*, PyObject*) \
    BUILTIN(32, PyDict_Keys, PyObject*, PyObject*) \
    BUILTIN(33, PyDict_Merge, int, PyObject*, PyObject*, int) \
    BUILTIN(34, PyDict_New, PyObject*) \
    BUILTIN(35, PyDict_SetDefault, PyObject*, PyObject*, PyObject*, PyObject*) \
    BUILTIN(36, PyDict_SetItem, int, PyObject*, PyObject*, PyObject*) \
    BUILTIN(37, PyDict_Size, Py_ssize_t, PyObject*) \
    BUILTIN(38, PyDict_Update, int, PyObject*, PyObject*) \
    BUILTIN(39, PyDict_Values, PyObject*, PyObject*) \
    BUILTIN(40, PyErr_Display, void, PyObject*, PyObject*, PyObject*) \
    BUILTIN(41, PyErr_GivenExceptionMatches, int, PyObject*, PyObject*) \
    BUILTIN(42, PyErr_NewException, PyObject*, const char*, PyObject*, PyObject*) \
    BUILTIN(43, PyErr_NewExceptionWithDoc, PyObject*, const char*, const char*, PyObject*, PyObject*) \
    BUILTIN(44, PyErr_Occurred, PyObject*) \
    BUILTIN(45, PyErr_PrintEx, void, int) \
    BUILTIN(46, PyErr_Restore, void, PyObject*, PyObject*, PyObject*) \
    BUILTIN(47, PyErr_SetExcInfo, void, PyObject*, PyObject*, PyObject*) \
    BUILTIN(48, PyEval_GetBuiltins, PyObject*) \
    BUILTIN(49, PyEval_RestoreThread, void, PyThreadState*) \
    BUILTIN(50, PyEval_SaveThread, PyThreadState*) \
    BUILTIN(51, PyException_GetContext, PyObject*, PyObject*) \
    BUILTIN(52, PyException_SetCause, void, PyObject*, PyObject*) \
    BUILTIN(53, PyException_SetContext, void, PyObject*, PyObject*) \
    BUILTIN(54, PyException_SetTraceback, int, PyObject*, PyObject*) \
    BUILTIN(55, PyFile_WriteObject, int, PyObject*, PyObject*, int) \
    BUILTIN(56, PyFloat_AsDouble, double, PyObject*) \
    BUILTIN(57, PyFloat_FromDouble, PyObject*, double) \
    BUILTIN(58, PyFrame_New, PyFrameObject*, PyThreadState*, PyCodeObject*, PyObject*, PyObject*) \
    BUILTIN(59, PyFrozenSet_New, PyObject*, PyObject*) \
    BUILTIN(60, PyGILState_Ensure, PyGILState_STATE) \
    BUILTIN(61, PyGILState_Release, void, PyGILState_STATE) \
    BUILTIN(62, PyImport_GetModuleDict, PyObject*) \
    BUILTIN(63, PyImport_Import, PyObject*, PyObject*) \
    BUILTIN(64, PyImport_ImportModule, PyObject*, const char*) \
    BUILTIN(65, PyImport_ImportModuleLevelObject, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, int) \
    BUILTIN(66, PyImport_ImportModuleNoBlock, PyObject*, const char*) \
    BUILTIN(67, PyIndex_Check, int, PyObject*) \
    BUILTIN(68, PyInstanceMethod_New, PyObject*, PyObject*) \
    BUILTIN(69, PyIter_Next, PyObject*, PyObject*) \
    BUILTIN(70, PyList_Append, int, PyObject*, PyObject*) \
    BUILTIN(71, PyList_AsTuple, PyObject*, PyObject*) \
    BUILTIN(72, PyList_GetItem, PyObject*, PyObject*, Py_ssize_t) \
    BUILTIN(73, PyList_GetSlice, PyObject*, PyObject*, Py_ssize_t, Py_ssize_t) \
    BUILTIN(74, PyList_Insert, int, PyObject*, Py_ssize_t, PyObject*) \
    BUILTIN(75, PyList_New, PyObject*, Py_ssize_t) \
    BUILTIN(76, PyList_Reverse, int, PyObject*) \
    BUILTIN(77, PyList_SetItem, int, PyObject*, Py_ssize_t, PyObject*) \
    BUILTIN(78, PyList_SetSlice, int, PyObject*, Py_ssize_t, Py_ssize_t, PyObject*) \
    BUILTIN(79, PyList_Size, Py_ssize_t, PyObject*) \
    BUILTIN(80, PyList_Sort, int, PyObject*) \
    BUILTIN(81, PyLong_AsVoidPtr, void*, PyObject*) \
    BUILTIN(82, PyLong_FromDouble, PyObject*, double) \
    BUILTIN(83, PyLong_FromLong, PyObject*, long) \
    BUILTIN(84, PyLong_FromLongLong, PyObject*, long long) \
    BUILTIN(85, PyLong_FromSize_t, PyObject*, size_t) \
    BUILTIN(86, PyLong_FromSsize_t, PyObject*, Py_ssize_t) \
    BUILTIN(87, PyLong_FromUnsignedLong, PyObject*, unsigned long) \
    BUILTIN(88, PyLong_FromUnsignedLongLong, PyObject*, unsigned long long) \
    BUILTIN(89, PyMapping_Check, int, PyObject*) \
    BUILTIN(90, PyMapping_Items, PyObject*, PyObject*) \
    BUILTIN(91, PyMapping_Keys, PyObject*, PyObject*) \
    BUILTIN(92, PyMapping_Size, Py_ssize_t, PyObject*) \
    BUILTIN(93, PyMapping_Values, PyObject*, PyObject*) \
    BUILTIN(94, PyMemoryView_FromObject, PyObject*, PyObject*) \
    BUILTIN(95, PyMemoryView_GetContiguous, PyObject*, PyObject*, int, char) \
    BUILTIN(96, PyMethod_New, PyObject*, PyObject*, PyObject*) \
    BUILTIN(97, PyModule_AddIntConstant, int, PyObject*, const char*, long) \
    BUILTIN(98, PyModule_AddObjectRef, int, PyObject*, const char*, PyObject*) \
    BUILTIN(99, PyModule_GetNameObject, PyObject*, PyObject*) \
    BUILTIN(100, PyModule_New, PyObject*, const char*) \
    BUILTIN(101, PyModule_NewObject, PyObject*, PyObject*) \
    BUILTIN(102, PyModule_SetDocString, int, PyObject*, const char*) \
    BUILTIN(103, PyNumber_Absolute, PyObject*, PyObject*) \
    BUILTIN(104, PyNumber_Check, int, PyObject*) \
    BUILTIN(105, PyNumber_Divmod, PyObject*, PyObject*, PyObject*) \
    BUILTIN(106, PyNumber_Float, PyObject*, PyObject*) \
    BUILTIN(107, PyNumber_InPlacePower, PyObject*, PyObject*, PyObject*, PyObject*) \
    BUILTIN(108, PyNumber_Index, PyObject*, PyObject*) \
    BUILTIN(109, PyNumber_Long, PyObject*, PyObject*) \
    BUILTIN(110, PyNumber_Power, PyObject*, PyObject*, PyObject*, PyObject*) \
    BUILTIN(111, PyNumber_ToBase, PyObject*, PyObject*, int) \
    BUILTIN(112, PyOS_FSPath, PyObject*, PyObject*) \
    BUILTIN(113, PyObject_ASCII, PyObject*, PyObject*) \
    BUILTIN(114, PyObject_AsFileDescriptor, int, PyObject*) \
    BUILTIN(115, PyObject_Bytes, PyObject*, PyObject*) \
    BUILTIN(116, PyObject_ClearWeakRefs, void, PyObject*) \
    BUILTIN(117, PyObject_DelItem, int, PyObject*, PyObject*) \
    BUILTIN(118, PyObject_Dir, PyObject*, PyObject*) \
    BUILTIN(119, PyObject_Format, PyObject*, PyObject*, PyObject*) \
    BUILTIN(120, PyObject_GC_Track, void, void*) \
    BUILTIN(121, PyObject_GC_UnTrack, void, void*) \
    BUILTIN(122, PyObject_GetDoc, const char*, PyObject*) \
    BUILTIN(123, PyObject_GetItem, PyObject*, PyObject*, PyObject*) \
    BUILTIN(124, PyObject_GetIter, PyObject*, PyObject*) \
    BUILTIN(125, PyObject_HasAttr, int, PyObject*, PyObject*) \
    BUILTIN(126, PyObject_HasAttrString, int, PyObject*, const char*) \
    BUILTIN(127, PyObject_Hash, Py_hash_t, PyObject*) \
    BUILTIN(128, PyObject_HashNotImplemented, Py_hash_t, PyObject*) \
    BUILTIN(129, PyObject_IsInstance, int, PyObject*, PyObject*) \
    BUILTIN(130, PyObject_IsSubclass, int, PyObject*, PyObject*) \
    BUILTIN(131, PyObject_IsTrue, int, PyObject*) \
    BUILTIN(132, PyObject_LengthHint, Py_ssize_t, PyObject*, Py_ssize_t) \
    BUILTIN(133, PyObject_Repr, PyObject*, PyObject*) \
    BUILTIN(134, PyObject_RichCompare, PyObject*, PyObject*, PyObject*, int) \
    BUILTIN(135, PyObject_SetDoc, int, PyObject*, const char*) \
    BUILTIN(136, PyObject_SetItem, int, PyObject*, PyObject*, PyObject*) \
    BUILTIN(137, PyObject_Size, Py_ssize_t, PyObject*) \
    BUILTIN(138, PyObject_Str, PyObject*, PyObject*) \
    BUILTIN(139, PyObject_Type, PyObject*, PyObject*) \
    BUILTIN(140, PyRun_StringFlags, PyObject*, const char*, int, PyObject*, PyObject*, PyCompilerFlags*) \
    BUILTIN(141, PySeqIter_New, PyObject*, PyObject*) \
    BUILTIN(142, PySequence_Check, int, PyObject*) \
    BUILTIN(143, PySequence_Concat, PyObject*, PyObject*, PyObject*) \
    BUILTIN(144, PySequence_Contains, int, PyObject*, PyObject*) \
    BUILTIN(145, PySequence_DelItem, int, PyObject*, Py_ssize_t) \
    BUILTIN(146, PySequence_GetItem, PyObject*, PyObject*, Py_ssize_t) \
    BUILTIN(147, PySequence_GetSlice, PyObject*, PyObject*, Py_ssize_t, Py_ssize_t) \
    BUILTIN(148, PySequence_InPlaceConcat, PyObject*, PyObject*, PyObject*) \
    BUILTIN(149, PySequence_InPlaceRepeat, PyObject*, PyObject*, Py_ssize_t) \
    BUILTIN(150, PySequence_Length, Py_ssize_t, PyObject*) \
    BUILTIN(151, PySequence_List, PyObject*, PyObject*) \
    BUILTIN(152, PySequence_Repeat, PyObject*, PyObject*, Py_ssize_t) \
    BUILTIN(153, PySequence_SetItem, int, PyObject*, Py_ssize_t, PyObject*) \
    BUILTIN(154, PySequence_Size, Py_ssize_t, PyObject*) \
    BUILTIN(155, PySequence_Tuple, PyObject*, PyObject*) \
    BUILTIN(156, PySet_Add, int, PyObject*, PyObject*) \
    BUILTIN(157, PySet_Clear, int, PyObject*) \
    BUILTIN(158, PySet_Contains, int, PyObject*, PyObject*) \
    BUILTIN(159, PySet_Discard, int, PyObject*, PyObject*) \
    BUILTIN(160, PySet_New, PyObject*, PyObject*) \
    BUILTIN(161, PySet_Pop, PyObject*, PyObject*) \
    BUILTIN(162, PySet_Size, Py_ssize_t, PyObject*) \
    BUILTIN(163, PySlice_New, PyObject*, PyObject*, PyObject*, PyObject*) \
    BUILTIN(164, PyStaticMethod_New, PyObject*, PyObject*) \
    BUILTIN(165, PyStructSequence_New, PyObject*, PyTypeObject*) \
    BUILTIN(166, PySys_GetObject, PyObject*, const char*) \
    BUILTIN(167, PyThreadState_Get, PyThreadState*) \
    BUILTIN(168, PyThread_acquire_lock, int, PyThread_type_lock, int) \
    BUILTIN(169, PyThread_allocate_lock, PyThread_type_lock) \
    BUILTIN(170, PyThread_release_lock, void, PyThread_type_lock) \
    BUILTIN(171, PyTraceBack_Here, int, PyFrameObject*) \
    BUILTIN(172, PyTraceMalloc_Track, int, unsigned int, uintptr_t, size_t) \
    BUILTIN(173, PyTraceMalloc_Untrack, int, unsigned int, uintptr_t) \
    BUILTIN(174, PyTruffleByteArray_FromStringAndSize, PyObject*, int8_t*, Py_ssize_t) \
    BUILTIN(175, PyTruffleBytes_Concat, PyObject*, PyObject*, PyObject*) \
    BUILTIN(176, PyTruffleBytes_FromFormat, PyObject*, const char*, PyObject*) \
    BUILTIN(177, PyTruffleBytes_FromStringAndSize, PyObject*, const char*, Py_ssize_t) \
    BUILTIN(178, PyTruffleCMethod_NewEx, PyObject*, PyMethodDef*, const char*, void*, int, int, PyObject*, PyObject*, PyTypeObject*, const char*) \
    BUILTIN(179, PyTruffleComplex_AsCComplex, PyObject*, PyObject*) \
    BUILTIN(180, PyTruffleContextVar_Get, PyObject*, PyObject*, PyObject*, void*) \
    BUILTIN(181, PyTruffleDescr_NewClassMethod, PyObject*, void*, const char*, const char*, int, int, void*, PyTypeObject*) \
    BUILTIN(182, PyTruffleDescr_NewGetSet, PyObject*, const char*, PyTypeObject*, void*, void*, const char*, void*) \
    BUILTIN(183, PyTruffleDict_Next, PyObject*, PyObject*, Py_ssize_t) \
    BUILTIN(184, PyTruffleErr_Fetch, PyObject*) \
    BUILTIN(185, PyTruffleErr_GetExcInfo, PyObject*) \
    BUILTIN(186, PyTruffleHash_InitSecret, void, void*) \
    BUILTIN(187, PyTruffleLong_AsPrimitive, long, PyObject*, int, long) \
    BUILTIN(188, PyTruffleLong_FromString, PyObject*, const char*, int, int) \
    BUILTIN(189, PyTruffleLong_One, PyObject*) \
    BUILTIN(190, PyTruffleLong_Zero, PyObject*) \
    BUILTIN(191, PyTruffleModule_AddFunctionToModule, int, void*, PyObject*, const char*, void*, int, int, void*) \
    BUILTIN(192, PyTruffleNumber_BinOp, PyObject*, PyObject*, PyObject*, int) \
    BUILTIN(193, PyTruffleNumber_InPlaceBinOp, PyObject*, PyObject*, PyObject*, int) \
    BUILTIN(194, PyTruffleNumber_UnaryOp, PyObject*, PyObject*, int) \
    BUILTIN(195, PyTruffleObject_CallFunctionObjArgs, PyObject*, PyObject*, va_list*) \
    BUILTIN(196, PyTruffleObject_CallMethodObjArgs, PyObject*, PyObject*, PyObject*, va_list*) \
    BUILTIN(197, PyTruffleObject_GenericGetAttr, PyObject*, PyObject*, PyObject*) \
    BUILTIN(198, PyTruffleObject_GenericSetAttr, int, PyObject*, PyObject*, PyObject*) \
    BUILTIN(199, PyTruffleObject_GetItemString, PyObject*, PyObject*, const char*) \
    BUILTIN(200, PyTruffleState_FindModule, PyObject*, struct PyModuleDef*) \
    BUILTIN(201, PyTruffleStructSequence_InitType2, int, PyTypeObject*, void*, void*, int) \
    BUILTIN(202, PyTruffleStructSequence_NewType, PyTypeObject*, const char*, const char*, void*, void*, int) \
    BUILTIN(203, PyTruffleToCharPointer, void*, PyObject*) \
    BUILTIN(204, PyTruffleType_AddFunctionToType, int, void*, PyTypeObject*, PyObject*, const char*, void*, int, int, void*) \
    BUILTIN(205, PyTruffleType_AddGetSet, int, PyTypeObject*, PyObject*, const char*, void*, void*, void*, void*) \
    BUILTIN(206, PyTruffleType_AddMember, int, PyTypeObject*, PyObject*, const char*, int, Py_ssize_t, int, void*) \
    BUILTIN(207, PyTruffleType_AddSlot, int, PyTypeObject*, PyObject*, const char*, void*, int, int, void*) \
    BUILTIN(208, PyTruffleUnicode_Decode, PyObject*, PyObject*, const char*, const char*) \
    BUILTIN(209, PyTruffleUnicode_DecodeUTF8Stateful, PyObject*, void*, const char*, int) \
    BUILTIN(210, PyTruffleUnicode_InternInPlace, PyObject*, PyObject*) \
    BUILTIN(211, PyTruffleUnicode_New, PyObject*, void*, Py_ssize_t, Py_UCS4) \
    BUILTIN(212, PyTruffle_Arg_ParseTupleAndKeywords, int, PyObject*, PyObject*, const char*, void*, void*) \
    BUILTIN(213, PyTruffle_ByteArray_EmptyWithCapacity, PyObject*, Py_ssize_t) \
    BUILTIN(214, PyTruffle_Bytes_AsString, void*, PyObject*) \
    BUILTIN(215, PyTruffle_Bytes_CheckEmbeddedNull, int, PyObject*) \
    BUILTIN(216, PyTruffle_Bytes_EmptyWithCapacity, PyObject*, long) \
    BUILTIN(217, PyTruffle_Compute_Mro, PyObject*, PyTypeObject*, const char*) \
    BUILTIN(218, PyTruffle_Debug, int, void*) \
    BUILTIN(219, PyTruffle_DebugTrace, void) \
    BUILTIN(220, PyTruffle_Ellipsis, PyObject*) \
    BUILTIN(221, PyTruffle_False, PyObject*) \
    BUILTIN(222, PyTruffle_FatalErrorFunc, void, const char*, const char*, int) \
    BUILTIN(223, PyTruffle_FileSystemDefaultEncoding, PyObject*) \
    BUILTIN(224, PyTruffle_Get_Inherited_Native_Slots, void*, PyTypeObject*, const char*) \
    BUILTIN(225, PyTruffle_HashConstant, long, int) \
    BUILTIN(226, PyTruffle_LogString, void, int, const char*) \
    BUILTIN(227, PyTruffle_MemoryViewFromBuffer, PyObject*, void*, PyObject*, Py_ssize_t, int, Py_ssize_t, const char*, int, void*, void*, void*, void*) \
    BUILTIN(228, PyTruffle_Native_Options, int) \
    BUILTIN(229, PyTruffle_NewTypeDict, PyObject*, PyTypeObject*) \
    BUILTIN(230, PyTruffle_NoValue, PyObject*) \
    BUILTIN(231, PyTruffle_None, PyObject*) \
    BUILTIN(232, PyTruffle_NotImplemented, PyObject*) \
    BUILTIN(233, PyTruffle_OS_DoubleToString, PyObject*, double, int, int, int) \
    BUILTIN(234, PyTruffle_OS_StringToDouble, PyObject*, const char*, int) \
    BUILTIN(235, PyTruffle_Object_Alloc, int, void*, long) \
    BUILTIN(236, PyTruffle_Object_Free, int, void*) \
    BUILTIN(237, PyTruffle_Register_NULL, void, void*) \
    BUILTIN(238, PyTruffle_Set_Native_Slots, int, PyTypeObject*, void*, void*) \
    BUILTIN(239, PyTruffle_Set_SulongType, void*, PyTypeObject*, void*) \
    BUILTIN(240, PyTruffle_ToNative, int, void*) \
    BUILTIN(241, PyTruffle_Trace_Type, int, void*, void*) \
    BUILTIN(242, PyTruffle_True, PyObject*) \
    BUILTIN(243, PyTruffle_Type, PyTypeObject*, const char*) \
    BUILTIN(244, PyTruffle_Type_Modified, int, PyTypeObject*, const char*, PyObject*) \
    BUILTIN(245, PyTruffle_Unicode_AsUnicodeAndSize, PyObject*, PyObject*) \
    BUILTIN(246, PyTruffle_Unicode_AsWideChar, PyObject*, PyObject*, int) \
    BUILTIN(247, PyTruffle_Unicode_DecodeUTF32, PyObject*, void*, Py_ssize_t, const char*, int) \
    BUILTIN(248, PyTruffle_Unicode_FromFormat, PyObject*, const char*, va_list*) \
    BUILTIN(249, PyTruffle_Unicode_FromWchar, PyObject*, void*, size_t) \
    BUILTIN(250, PyTruffle_tss_create, long) \
    BUILTIN(251, PyTruffle_tss_delete, void, long) \
    BUILTIN(252, PyTruffle_tss_get, void*, long) \
    BUILTIN(253, PyTruffle_tss_set, int, long, void*) \
    BUILTIN(254, PyTuple_GetItem, PyObject*, PyObject*, Py_ssize_t) \
    BUILTIN(255, PyTuple_GetSlice, PyObject*, PyObject*, Py_ssize_t, Py_ssize_t) \
    BUILTIN(256, PyTuple_New, PyObject*, Py_ssize_t) \
    BUILTIN(257, PyTuple_SetItem, int, PyObject*, Py_ssize_t, PyObject*) \
    BUILTIN(258, PyTuple_Size, Py_ssize_t, PyObject*) \
    BUILTIN(259, PyType_IsSubtype, int, PyTypeObject*, PyTypeObject*) \
    BUILTIN(260, PyUnicode_AsEncodedString, PyObject*, PyObject*, const char*, const char*) \
    BUILTIN(261, PyUnicode_AsUnicodeEscapeString, PyObject*, PyObject*) \
    BUILTIN(262, PyUnicode_Compare, int, PyObject*, PyObject*) \
    BUILTIN(263, PyUnicode_Concat, PyObject*, PyObject*, PyObject*) \
    BUILTIN(264, PyUnicode_Contains, int, PyObject*, PyObject*) \
    BUILTIN(265, PyUnicode_DecodeFSDefault, PyObject*, const char*) \
    BUILTIN(266, PyUnicode_EncodeFSDefault, PyObject*, PyObject*) \
    BUILTIN(267, PyUnicode_FindChar, Py_ssize_t, PyObject*, Py_UCS4, Py_ssize_t, Py_ssize_t, int) \
    BUILTIN(268, PyUnicode_Format, PyObject*, PyObject*, PyObject*) \
    BUILTIN(269, PyUnicode_FromEncodedObject, PyObject*, PyObject*, const char*, const char*) \
    BUILTIN(270, PyUnicode_FromObject, PyObject*, PyObject*) \
    BUILTIN(271, PyUnicode_FromOrdinal, PyObject*, int) \
    BUILTIN(272, PyUnicode_FromString, PyObject*, const char*) \
    BUILTIN(273, PyUnicode_GetLength, Py_ssize_t, PyObject*) \
    BUILTIN(274, PyUnicode_Join, PyObject*, PyObject*, PyObject*) \
    BUILTIN(275, PyUnicode_ReadChar, Py_UCS4, PyObject*, Py_ssize_t) \
    BUILTIN(276, PyUnicode_Replace, PyObject*, PyObject*, PyObject*, PyObject*, Py_ssize_t) \
    BUILTIN(277, PyUnicode_Split, PyObject*, PyObject*, PyObject*, Py_ssize_t) \
    BUILTIN(278, PyUnicode_Substring, PyObject*, PyObject*, Py_ssize_t, Py_ssize_t) \
    BUILTIN(279, PyUnicode_Tailmatch, Py_ssize_t, PyObject*, PyObject*, Py_ssize_t, Py_ssize_t, int) \
    BUILTIN(280, PyWeakref_GetObject, PyObject*, PyObject*) \
    BUILTIN(281, PyWeakref_NewRef, PyObject*, PyObject*, PyObject*) \
    BUILTIN(282, Py_AtExit, int, void (*)(void)) \
    BUILTIN(283, Py_GenericAlias, PyObject*, PyObject*, PyObject*) \
    BUILTIN(284, Py_get_PyASCIIObject_length, Py_ssize_t, PyASCIIObject*) \
    BUILTIN(285, Py_get_PyASCIIObject_state_ascii, unsigned int, PyASCIIObject*) \
    BUILTIN(286, Py_get_PyASCIIObject_state_compact, unsigned int, PyASCIIObject*) \
    BUILTIN(287, Py_get_PyASCIIObject_state_kind, unsigned int, PyASCIIObject*) \
    BUILTIN(288, Py_get_PyASCIIObject_state_ready, unsigned int, PyASCIIObject*) \
    BUILTIN(289, Py_get_PyASCIIObject_wstr, wchar_t*, PyASCIIObject*) \
    BUILTIN(290, Py_get_PyAsyncMethods_am_aiter, unaryfunc, PyAsyncMethods*) \
    BUILTIN(291, Py_get_PyAsyncMethods_am_anext, unaryfunc, PyAsyncMethods*) \
    BUILTIN(292, Py_get_PyAsyncMethods_am_await, unaryfunc, PyAsyncMethods*) \
    BUILTIN(293, Py_get_PyBufferProcs_bf_getbuffer, getbufferproc, PyBufferProcs*) \
    BUILTIN(294, Py_get_PyBufferProcs_bf_releasebuffer, releasebufferproc, PyBufferProcs*) \
    BUILTIN(295, Py_get_PyByteArrayObject_ob_exports, Py_ssize_t, PyByteArrayObject*) \
    BUILTIN(296, Py_get_PyByteArrayObject_ob_start, void*, PyByteArrayObject*) \
    BUILTIN(297, Py_get_PyCFunctionObject_m_ml, PyMethodDef*, PyCFunctionObject*) \
    BUILTIN(298, Py_get_PyCFunctionObject_m_module, PyObject*, PyCFunctionObject*) \
    BUILTIN(299, Py_get_PyCFunctionObject_m_self, PyObject*, PyCFunctionObject*) \
    BUILTIN(300, Py_get_PyCFunctionObject_m_weakreflist, PyObject*, PyCFunctionObject*) \
    BUILTIN(301, Py_get_PyCFunctionObject_vectorcall, vectorcallfunc, PyCFunctionObject*) \
    BUILTIN(302, Py_get_PyCMethodObject_mm_class, PyTypeObject*, PyCMethodObject*) \
    BUILTIN(303, Py_get_PyCompactUnicodeObject_wstr_length, Py_ssize_t, PyCompactUnicodeObject*) \
    BUILTIN(304, Py_get_PyDescrObject_d_name, PyObject*, PyDescrObject*) \
    BUILTIN(305, Py_get_PyDescrObject_d_type, PyTypeObject*, PyDescrObject*) \
    BUILTIN(306, Py_get_PyFrameObject_f_lineno, int, PyFrameObject*) \
    BUILTIN(307, Py_get_PyGetSetDef_closure, void*, PyGetSetDef*) \
    BUILTIN(308, Py_get_PyGetSetDef_doc, const char*, PyGetSetDef*) \
    BUILTIN(309, Py_get_PyGetSetDef_get, getter, PyGetSetDef*) \
    BUILTIN(310, Py_get_PyGetSetDef_name, const char*, PyGetSetDef*) \
    BUILTIN(311, Py_get_PyGetSetDef_set, setter, PyGetSetDef*) \
    BUILTIN(312, Py_get_PyInstanceMethodObject_func, PyObject*, PyInstanceMethodObject*) \
    BUILTIN(313, Py_get_PyListObject_ob_item, PyObject**, PyListObject*) \
    BUILTIN(314, Py_get_PyLongObject_ob_digit, void*, PyLongObject*) \
    BUILTIN(315, Py_get_PyMappingMethods_mp_ass_subscript, objobjargproc, PyMappingMethods*) \
    BUILTIN(316, Py_get_PyMappingMethods_mp_length, lenfunc, PyMappingMethods*) \
    BUILTIN(317, Py_get_PyMappingMethods_mp_subscript, binaryfunc, PyMappingMethods*) \
    BUILTIN(318, Py_get_PyMethodDef_ml_doc, void*, PyMethodDef*) \
    BUILTIN(319, Py_get_PyMethodDef_ml_flags, int, PyMethodDef*) \
    BUILTIN(320, Py_get_PyMethodDef_ml_meth, void*, PyMethodDef*) \
    BUILTIN(321, Py_get_PyMethodDef_ml_name, void*, PyMethodDef*) \
    BUILTIN(322, Py_get_PyMethodDescrObject_d_method, PyMethodDef*, PyMethodDescrObject*) \
    BUILTIN(323, Py_get_PyMethodObject_im_func, PyObject*, PyMethodObject*) \
    BUILTIN(324, Py_get_PyMethodObject_im_self, PyObject*, PyMethodObject*) \
    BUILTIN(325, Py_get_PyModuleDef_m_doc, const char*, PyModuleDef*) \
    BUILTIN(326, Py_get_PyModuleDef_m_methods, PyMethodDef*, PyModuleDef*) \
    BUILTIN(327, Py_get_PyModuleDef_m_name, const char*, PyModuleDef*) \
    BUILTIN(328, Py_get_PyModuleDef_m_size, Py_ssize_t, PyModuleDef*) \
    BUILTIN(329, Py_get_PyModuleObject_md_def, PyModuleDef*, PyModuleObject*) \
    BUILTIN(330, Py_get_PyModuleObject_md_dict, PyObject*, PyModuleObject*) \
    BUILTIN(331, Py_get_PyModuleObject_md_state, void*, PyModuleObject*) \
    BUILTIN(332, Py_get_PyNumberMethods_nb_absolute, unaryfunc, PyNumberMethods*) \
    BUILTIN(333, Py_get_PyNumberMethods_nb_add, binaryfunc, PyNumberMethods*) \
    BUILTIN(334, Py_get_PyNumberMethods_nb_and, binaryfunc, PyNumberMethods*) \
    BUILTIN(335, Py_get_PyNumberMethods_nb_bool, inquiry, PyNumberMethods*) \
    BUILTIN(336, Py_get_PyNumberMethods_nb_divmod, binaryfunc, PyNumberMethods*) \
    BUILTIN(337, Py_get_PyNumberMethods_nb_float, unaryfunc, PyNumberMethods*) \
    BUILTIN(338, Py_get_PyNumberMethods_nb_floor_divide, binaryfunc, PyNumberMethods*) \
    BUILTIN(339, Py_get_PyNumberMethods_nb_index, unaryfunc, PyNumberMethods*) \
    BUILTIN(340, Py_get_PyNumberMethods_nb_inplace_add, binaryfunc, PyNumberMethods*) \
    BUILTIN(341, Py_get_PyNumberMethods_nb_inplace_and, binaryfunc, PyNumberMethods*) \
    BUILTIN(342, Py_get_PyNumberMethods_nb_inplace_floor_divide, binaryfunc, PyNumberMethods*) \
    BUILTIN(343, Py_get_PyNumberMethods_nb_inplace_lshift, binaryfunc, PyNumberMethods*) \
    BUILTIN(344, Py_get_PyNumberMethods_nb_inplace_matrix_multiply, binaryfunc, PyNumberMethods*) \
    BUILTIN(345, Py_get_PyNumberMethods_nb_inplace_multiply, binaryfunc, PyNumberMethods*) \
    BUILTIN(346, Py_get_PyNumberMethods_nb_inplace_or, binaryfunc, PyNumberMethods*) \
    BUILTIN(347, Py_get_PyNumberMethods_nb_inplace_power, ternaryfunc, PyNumberMethods*) \
    BUILTIN(348, Py_get_PyNumberMethods_nb_inplace_remainder, binaryfunc, PyNumberMethods*) \
    BUILTIN(349, Py_get_PyNumberMethods_nb_inplace_rshift, binaryfunc, PyNumberMethods*) \
    BUILTIN(350, Py_get_PyNumberMethods_nb_inplace_subtract, binaryfunc, PyNumberMethods*) \
    BUILTIN(351, Py_get_PyNumberMethods_nb_inplace_true_divide, binaryfunc, PyNumberMethods*) \
    BUILTIN(352, Py_get_PyNumberMethods_nb_inplace_xor, binaryfunc, PyNumberMethods*) \
    BUILTIN(353, Py_get_PyNumberMethods_nb_int, unaryfunc, PyNumberMethods*) \
    BUILTIN(354, Py_get_PyNumberMethods_nb_invert, unaryfunc, PyNumberMethods*) \
    BUILTIN(355, Py_get_PyNumberMethods_nb_lshift, binaryfunc, PyNumberMethods*) \
    BUILTIN(356, Py_get_PyNumberMethods_nb_matrix_multiply, binaryfunc, PyNumberMethods*) \
    BUILTIN(357, Py_get_PyNumberMethods_nb_multiply, binaryfunc, PyNumberMethods*) \
    BUILTIN(358, Py_get_PyNumberMethods_nb_negative, unaryfunc, PyNumberMethods*) \
    BUILTIN(359, Py_get_PyNumberMethods_nb_or, binaryfunc, PyNumberMethods*) \
    BUILTIN(360, Py_get_PyNumberMethods_nb_positive, unaryfunc, PyNumberMethods*) \
    BUILTIN(361, Py_get_PyNumberMethods_nb_power, ternaryfunc, PyNumberMethods*) \
    BUILTIN(362, Py_get_PyNumberMethods_nb_remainder, binaryfunc, PyNumberMethods*) \
    BUILTIN(363, Py_get_PyNumberMethods_nb_rshift, binaryfunc, PyNumberMethods*) \
    BUILTIN(364, Py_get_PyNumberMethods_nb_subtract, binaryfunc, PyNumberMethods*) \
    BUILTIN(365, Py_get_PyNumberMethods_nb_true_divide, binaryfunc, PyNumberMethods*) \
    BUILTIN(366, Py_get_PyNumberMethods_nb_xor, binaryfunc, PyNumberMethods*) \
    BUILTIN(367, Py_get_PyObject_ob_refcnt, Py_ssize_t, PyObject*) \
    BUILTIN(368, Py_get_PyObject_ob_type, PyTypeObject*, PyObject*) \
    BUILTIN(369, Py_get_PySequenceMethods_sq_ass_item, ssizeobjargproc, PySequenceMethods*) \
    BUILTIN(370, Py_get_PySequenceMethods_sq_concat, binaryfunc, PySequenceMethods*) \
    BUILTIN(371, Py_get_PySequenceMethods_sq_contains, objobjproc, PySequenceMethods*) \
    BUILTIN(372, Py_get_PySequenceMethods_sq_inplace_concat, binaryfunc, PySequenceMethods*) \
    BUILTIN(373, Py_get_PySequenceMethods_sq_inplace_repeat, ssizeargfunc, PySequenceMethods*) \
    BUILTIN(374, Py_get_PySequenceMethods_sq_item, ssizeargfunc, PySequenceMethods*) \
    BUILTIN(375, Py_get_PySequenceMethods_sq_length, lenfunc, PySequenceMethods*) \
    BUILTIN(376, Py_get_PySequenceMethods_sq_repeat, ssizeargfunc, PySequenceMethods*) \
    BUILTIN(377, Py_get_PySetObject_used, Py_ssize_t, PySetObject*) \
    BUILTIN(378, Py_get_PySliceObject_start, PyObject*, PySliceObject*) \
    BUILTIN(379, Py_get_PySliceObject_step, PyObject*, PySliceObject*) \
    BUILTIN(380, Py_get_PySliceObject_stop, PyObject*, PySliceObject*) \
    BUILTIN(381, Py_get_PyTupleObject_ob_item, PyObject**, PyTupleObject*) \
    BUILTIN(382, Py_get_PyTypeObject_tp_alloc, allocfunc, PyTypeObject*) \
    BUILTIN(383, Py_get_PyTypeObject_tp_as_async, PyAsyncMethods*, PyTypeObject*) \
    BUILTIN(384, Py_get_PyTypeObject_tp_as_buffer, PyBufferProcs*, PyTypeObject*) \
    BUILTIN(385, Py_get_PyTypeObject_tp_as_mapping, PyMappingMethods*, PyTypeObject*) \
    BUILTIN(386, Py_get_PyTypeObject_tp_as_number, PyNumberMethods*, PyTypeObject*) \
    BUILTIN(387, Py_get_PyTypeObject_tp_as_sequence, PySequenceMethods*, PyTypeObject*) \
    BUILTIN(388, Py_get_PyTypeObject_tp_base, PyTypeObject*, PyTypeObject*) \
    BUILTIN(389, Py_get_PyTypeObject_tp_bases, PyObject*, PyTypeObject*) \
    BUILTIN(390, Py_get_PyTypeObject_tp_basicsize, Py_ssize_t, PyTypeObject*) \
    BUILTIN(391, Py_get_PyTypeObject_tp_cache, PyObject*, PyTypeObject*) \
    BUILTIN(392, Py_get_PyTypeObject_tp_call, ternaryfunc, PyTypeObject*) \
    BUILTIN(393, Py_get_PyTypeObject_tp_clear, inquiry, PyTypeObject*) \
    BUILTIN(394, Py_get_PyTypeObject_tp_dealloc, destructor, PyTypeObject*) \
    BUILTIN(395, Py_get_PyTypeObject_tp_del, destructor, PyTypeObject*) \
    BUILTIN(396, Py_get_PyTypeObject_tp_descr_get, descrgetfunc, PyTypeObject*) \
    BUILTIN(397, Py_get_PyTypeObject_tp_descr_set, descrsetfunc, PyTypeObject*) \
    BUILTIN(398, Py_get_PyTypeObject_tp_dict, PyObject*, PyTypeObject*) \
    BUILTIN(399, Py_get_PyTypeObject_tp_dictoffset, Py_ssize_t, PyTypeObject*) \
    BUILTIN(400, Py_get_PyTypeObject_tp_doc, const char*, PyTypeObject*) \
    BUILTIN(401, Py_get_PyTypeObject_tp_finalize, destructor, PyTypeObject*) \
    BUILTIN(402, Py_get_PyTypeObject_tp_flags, unsigned long, PyTypeObject*) \
    BUILTIN(403, Py_get_PyTypeObject_tp_free, freefunc, PyTypeObject*) \
    BUILTIN(404, Py_get_PyTypeObject_tp_getattr, getattrfunc, PyTypeObject*) \
    BUILTIN(405, Py_get_PyTypeObject_tp_getattro, getattrofunc, PyTypeObject*) \
    BUILTIN(406, Py_get_PyTypeObject_tp_getset, PyGetSetDef*, PyTypeObject*) \
    BUILTIN(407, Py_get_PyTypeObject_tp_hash, hashfunc, PyTypeObject*) \
    BUILTIN(408, Py_get_PyTypeObject_tp_init, initproc, PyTypeObject*) \
    BUILTIN(409, Py_get_PyTypeObject_tp_is_gc, inquiry, PyTypeObject*) \
    BUILTIN(410, Py_get_PyTypeObject_tp_itemsize, Py_ssize_t, PyTypeObject*) \
    BUILTIN(411, Py_get_PyTypeObject_tp_iter, getiterfunc, PyTypeObject*) \
    BUILTIN(412, Py_get_PyTypeObject_tp_iternext, iternextfunc, PyTypeObject*) \
    BUILTIN(413, Py_get_PyTypeObject_tp_members, struct PyMemberDef*, PyTypeObject*) \
    BUILTIN(414, Py_get_PyTypeObject_tp_methods, PyMethodDef*, PyTypeObject*) \
    BUILTIN(415, Py_get_PyTypeObject_tp_mro, PyObject*, PyTypeObject*) \
    BUILTIN(416, Py_get_PyTypeObject_tp_name, const char*, PyTypeObject*) \
    BUILTIN(417, Py_get_PyTypeObject_tp_new, newfunc, PyTypeObject*) \
    BUILTIN(418, Py_get_PyTypeObject_tp_repr, reprfunc, PyTypeObject*) \
    BUILTIN(419, Py_get_PyTypeObject_tp_richcompare, richcmpfunc, PyTypeObject*) \
    BUILTIN(420, Py_get_PyTypeObject_tp_setattr, setattrfunc, PyTypeObject*) \
    BUILTIN(421, Py_get_PyTypeObject_tp_setattro, setattrofunc, PyTypeObject*) \
    BUILTIN(422, Py_get_PyTypeObject_tp_str, reprfunc, PyTypeObject*) \
    BUILTIN(423, Py_get_PyTypeObject_tp_subclasses, PyObject*, PyTypeObject*) \
    BUILTIN(424, Py_get_PyTypeObject_tp_traverse, traverseproc, PyTypeObject*) \
    BUILTIN(425, Py_get_PyTypeObject_tp_vectorcall, vectorcallfunc, PyTypeObject*) \
    BUILTIN(426, Py_get_PyTypeObject_tp_vectorcall_offset, Py_ssize_t, PyTypeObject*) \
    BUILTIN(427, Py_get_PyTypeObject_tp_version_tag, unsigned int, PyTypeObject*) \
    BUILTIN(428, Py_get_PyTypeObject_tp_weaklist, PyObject*, PyTypeObject*) \
    BUILTIN(429, Py_get_PyTypeObject_tp_weaklistoffset, Py_ssize_t, PyTypeObject*) \
    BUILTIN(430, Py_get_PyUnicodeObject_data, void*, PyUnicodeObject*) \
    BUILTIN(431, Py_get_PyVarObject_ob_size, Py_ssize_t, PyVarObject*) \
    BUILTIN(432, Py_get_dummy, void*, void*) \
    BUILTIN(433, Py_get_mmap_object_data, char*, mmap_object*) \
    BUILTIN(434, Py_set_PyByteArrayObject_ob_exports, void, PyByteArrayObject*, int) \
    BUILTIN(435, Py_set_PyFrameObject_f_lineno, void, PyFrameObject*, int) \
    BUILTIN(436, Py_set_PyModuleObject_md_def, void, PyModuleObject*, PyModuleDef*) \
    BUILTIN(437, Py_set_PyModuleObject_md_state, void, PyModuleObject*, void*) \
    BUILTIN(438, Py_set_PyObject_ob_refcnt, void, PyObject*, Py_ssize_t) \
    BUILTIN(439, Py_set_PyTypeObject_tp_alloc, void, PyTypeObject*, allocfunc) \
    BUILTIN(440, Py_set_PyTypeObject_tp_as_buffer, void, PyTypeObject*, PyBufferProcs*) \
    BUILTIN(441, Py_set_PyTypeObject_tp_base, void, PyTypeObject*, PyTypeObject*) \
    BUILTIN(442, Py_set_PyTypeObject_tp_bases, void, PyTypeObject*, PyObject*) \
    BUILTIN(443, Py_set_PyTypeObject_tp_basicsize, void, PyTypeObject*, Py_ssize_t) \
    BUILTIN(444, Py_set_PyTypeObject_tp_clear, void, PyTypeObject*, inquiry) \
    BUILTIN(445, Py_set_PyTypeObject_tp_dealloc, void, PyTypeObject*, destructor) \
    BUILTIN(446, Py_set_PyTypeObject_tp_dict, void, PyTypeObject*, PyObject*) \
    BUILTIN(447, Py_set_PyTypeObject_tp_dictoffset, void, PyTypeObject*, Py_ssize_t) \
    BUILTIN(448, Py_set_PyTypeObject_tp_finalize, void, PyTypeObject*, destructor) \
    BUILTIN(449, Py_set_PyTypeObject_tp_flags, void, PyTypeObject*, unsigned long) \
    BUILTIN(450, Py_set_PyTypeObject_tp_free, void, PyTypeObject*, freefunc) \
    BUILTIN(451, Py_set_PyTypeObject_tp_getattr, void, PyTypeObject*, getattrfunc) \
    BUILTIN(452, Py_set_PyTypeObject_tp_getattro, void, PyTypeObject*, getattrofunc) \
    BUILTIN(453, Py_set_PyTypeObject_tp_itemsize, void, PyTypeObject*, Py_ssize_t) \
    BUILTIN(454, Py_set_PyTypeObject_tp_iter, void, PyTypeObject*, getiterfunc) \
    BUILTIN(455, Py_set_PyTypeObject_tp_iternext, void, PyTypeObject*, iternextfunc) \
    BUILTIN(456, Py_set_PyTypeObject_tp_mro, void, PyTypeObject*, PyObject*) \
    BUILTIN(457, Py_set_PyTypeObject_tp_new, void, PyTypeObject*, newfunc) \
    BUILTIN(458, Py_set_PyTypeObject_tp_setattr, void, PyTypeObject*, setattrfunc) \
    BUILTIN(459, Py_set_PyTypeObject_tp_setattro, void, PyTypeObject*, setattrofunc) \
    BUILTIN(460, Py_set_PyTypeObject_tp_subclasses, void, PyTypeObject*, PyObject*) \
    BUILTIN(461, Py_set_PyTypeObject_tp_traverse, void, PyTypeObject*, traverseproc) \
    BUILTIN(462, Py_set_PyTypeObject_tp_vectorcall_offset, void, PyTypeObject*, Py_ssize_t) \
    BUILTIN(463, Py_set_PyTypeObject_tp_weaklistoffset, void, PyTypeObject*, Py_ssize_t) \
    BUILTIN(464, Py_set_PyVarObject_ob_size, void, PyVarObject*, Py_ssize_t) \
    BUILTIN(465, _PyBytes_Join, PyObject*, PyObject*, PyObject*) \
    BUILTIN(466, _PyDict_Pop, PyObject*, PyObject*, PyObject*, PyObject*) \
    BUILTIN(467, _PyDict_SetItem_KnownHash, int, PyObject*, PyObject*, PyObject*, Py_hash_t) \
    BUILTIN(468, _PyErr_BadInternalCall, void, const char*, int) \
    BUILTIN(469, _PyErr_CreateAndSetException, void, PyObject*, PyObject*) \
    BUILTIN(470, _PyErr_WriteUnraisableMsg, void, const char*, PyObject*) \
    BUILTIN(471, _PyList_Extend, PyObject*, PyListObject*, PyObject*) \
    BUILTIN(472, _PyLong_Sign, int, PyObject*) \
    BUILTIN(473, _PyNamespace_New, PyObject*, PyObject*) \
    BUILTIN(474, _PyNumber_Index, PyObject*, PyObject*) \
    BUILTIN(475, _PyObject_Call1, PyObject*, PyObject*, PyObject*, PyObject*, int) \
    BUILTIN(476, _PyObject_CallMethod1, PyObject*, PyObject*, const char*, PyObject*, int) \
    BUILTIN(477, _PyObject_Dump, void, PyObject*) \
    BUILTIN(478, _PyTraceMalloc_NewReference, int, PyObject*) \
    BUILTIN(479, _PyTraceback_Add, void, const char*, const char*, int) \
    BUILTIN(480, _PyTruffleBytes_Resize, int, PyObject*, Py_ssize_t) \
    BUILTIN(481, _PyTruffleErr_Warn, PyObject*, PyObject*, PyObject*, Py_ssize_t, PyObject*) \
    BUILTIN(482, _PyTruffleEval_EvalCodeEx, PyObject*, PyObject*, PyObject*, PyObject*, void*, void*, void*, PyObject*, PyObject*) \
    BUILTIN(483, _PyTruffleModule_CreateInitialized_PyModule_New, PyModuleObject*, const char*) \
    BUILTIN(484, _PyTruffleModule_GetAndIncMaxModuleNumber, Py_ssize_t) \
    BUILTIN(485, _PyTruffleObject_MakeTpCall, PyObject*, PyObject*, void*, int, void*, void*) \
    BUILTIN(486, _PyTruffleSet_NextEntry, PyObject*, PyObject*, Py_ssize_t) \
    BUILTIN(487, _PyTruffle_HashBytes, Py_hash_t, const char*) \
    BUILTIN(488, _PyTruffle_Trace_Free, int, void*, Py_ssize_t) \
    BUILTIN(489, _PyType_Lookup, PyObject*, PyTypeObject*, PyObject*) \
    BUILTIN(490, _PyUnicode_AsASCIIString, PyObject*, PyObject*, const char*) \
    BUILTIN(491, _PyUnicode_AsLatin1String, PyObject*, PyObject*, const char*) \
    BUILTIN(492, _PyUnicode_AsUTF8String, PyObject*, PyObject*, const char*) \
    BUILTIN(493, _PyUnicode_EqualToASCIIString, int, PyObject*, const char*) \
    BUILTIN(494, _Py_HashDouble, Py_hash_t, PyObject*, double) \

#define PyASCIIObject_length(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyASCIIObject_length((PyASCIIObject*) (OBJ)) : ((PyASCIIObject*) (OBJ))->length )
#define PyASCIIObject_state_ascii(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyASCIIObject_state_ascii((PyASCIIObject*) (OBJ)) : ((PyASCIIObject*) (OBJ))->state_ascii )
#define PyASCIIObject_state_compact(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyASCIIObject_state_compact((PyASCIIObject*) (OBJ)) : ((PyASCIIObject*) (OBJ))->state_compact )
#define PyASCIIObject_state_kind(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyASCIIObject_state_kind((PyASCIIObject*) (OBJ)) : ((PyASCIIObject*) (OBJ))->state_kind )
#define PyASCIIObject_state_ready(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyASCIIObject_state_ready((PyASCIIObject*) (OBJ)) : ((PyASCIIObject*) (OBJ))->state_ready )
#define PyASCIIObject_wstr(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyASCIIObject_wstr((PyASCIIObject*) (OBJ)) : ((PyASCIIObject*) (OBJ))->wstr )
#define PyAsyncMethods_am_aiter(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyAsyncMethods_am_aiter((PyAsyncMethods*) (OBJ)) : ((PyAsyncMethods*) (OBJ))->am_aiter )
#define PyAsyncMethods_am_anext(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyAsyncMethods_am_anext((PyAsyncMethods*) (OBJ)) : ((PyAsyncMethods*) (OBJ))->am_anext )
#define PyAsyncMethods_am_await(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyAsyncMethods_am_await((PyAsyncMethods*) (OBJ)) : ((PyAsyncMethods*) (OBJ))->am_await )
#define PyBufferProcs_bf_getbuffer(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyBufferProcs_bf_getbuffer((PyBufferProcs*) (OBJ)) : ((PyBufferProcs*) (OBJ))->bf_getbuffer )
#define PyBufferProcs_bf_releasebuffer(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyBufferProcs_bf_releasebuffer((PyBufferProcs*) (OBJ)) : ((PyBufferProcs*) (OBJ))->bf_releasebuffer )
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
#define PyLongObject_ob_digit(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyLongObject_ob_digit((PyLongObject*) (OBJ)) : ((PyLongObject*) (OBJ))->ob_digit )
#define PyMappingMethods_mp_ass_subscript(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyMappingMethods_mp_ass_subscript((PyMappingMethods*) (OBJ)) : ((PyMappingMethods*) (OBJ))->mp_ass_subscript )
#define PyMappingMethods_mp_length(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyMappingMethods_mp_length((PyMappingMethods*) (OBJ)) : ((PyMappingMethods*) (OBJ))->mp_length )
#define PyMappingMethods_mp_subscript(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyMappingMethods_mp_subscript((PyMappingMethods*) (OBJ)) : ((PyMappingMethods*) (OBJ))->mp_subscript )
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
#define PyNumberMethods_nb_absolute(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyNumberMethods_nb_absolute((PyNumberMethods*) (OBJ)) : ((PyNumberMethods*) (OBJ))->nb_absolute )
#define PyNumberMethods_nb_add(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyNumberMethods_nb_add((PyNumberMethods*) (OBJ)) : ((PyNumberMethods*) (OBJ))->nb_add )
#define PyNumberMethods_nb_and(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyNumberMethods_nb_and((PyNumberMethods*) (OBJ)) : ((PyNumberMethods*) (OBJ))->nb_and )
#define PyNumberMethods_nb_bool(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyNumberMethods_nb_bool((PyNumberMethods*) (OBJ)) : ((PyNumberMethods*) (OBJ))->nb_bool )
#define PyNumberMethods_nb_divmod(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyNumberMethods_nb_divmod((PyNumberMethods*) (OBJ)) : ((PyNumberMethods*) (OBJ))->nb_divmod )
#define PyNumberMethods_nb_float(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyNumberMethods_nb_float((PyNumberMethods*) (OBJ)) : ((PyNumberMethods*) (OBJ))->nb_float )
#define PyNumberMethods_nb_floor_divide(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyNumberMethods_nb_floor_divide((PyNumberMethods*) (OBJ)) : ((PyNumberMethods*) (OBJ))->nb_floor_divide )
#define PyNumberMethods_nb_index(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyNumberMethods_nb_index((PyNumberMethods*) (OBJ)) : ((PyNumberMethods*) (OBJ))->nb_index )
#define PyNumberMethods_nb_inplace_add(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyNumberMethods_nb_inplace_add((PyNumberMethods*) (OBJ)) : ((PyNumberMethods*) (OBJ))->nb_inplace_add )
#define PyNumberMethods_nb_inplace_and(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyNumberMethods_nb_inplace_and((PyNumberMethods*) (OBJ)) : ((PyNumberMethods*) (OBJ))->nb_inplace_and )
#define PyNumberMethods_nb_inplace_floor_divide(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyNumberMethods_nb_inplace_floor_divide((PyNumberMethods*) (OBJ)) : ((PyNumberMethods*) (OBJ))->nb_inplace_floor_divide )
#define PyNumberMethods_nb_inplace_lshift(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyNumberMethods_nb_inplace_lshift((PyNumberMethods*) (OBJ)) : ((PyNumberMethods*) (OBJ))->nb_inplace_lshift )
#define PyNumberMethods_nb_inplace_matrix_multiply(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyNumberMethods_nb_inplace_matrix_multiply((PyNumberMethods*) (OBJ)) : ((PyNumberMethods*) (OBJ))->nb_inplace_matrix_multiply )
#define PyNumberMethods_nb_inplace_multiply(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyNumberMethods_nb_inplace_multiply((PyNumberMethods*) (OBJ)) : ((PyNumberMethods*) (OBJ))->nb_inplace_multiply )
#define PyNumberMethods_nb_inplace_or(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyNumberMethods_nb_inplace_or((PyNumberMethods*) (OBJ)) : ((PyNumberMethods*) (OBJ))->nb_inplace_or )
#define PyNumberMethods_nb_inplace_power(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyNumberMethods_nb_inplace_power((PyNumberMethods*) (OBJ)) : ((PyNumberMethods*) (OBJ))->nb_inplace_power )
#define PyNumberMethods_nb_inplace_remainder(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyNumberMethods_nb_inplace_remainder((PyNumberMethods*) (OBJ)) : ((PyNumberMethods*) (OBJ))->nb_inplace_remainder )
#define PyNumberMethods_nb_inplace_rshift(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyNumberMethods_nb_inplace_rshift((PyNumberMethods*) (OBJ)) : ((PyNumberMethods*) (OBJ))->nb_inplace_rshift )
#define PyNumberMethods_nb_inplace_subtract(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyNumberMethods_nb_inplace_subtract((PyNumberMethods*) (OBJ)) : ((PyNumberMethods*) (OBJ))->nb_inplace_subtract )
#define PyNumberMethods_nb_inplace_true_divide(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyNumberMethods_nb_inplace_true_divide((PyNumberMethods*) (OBJ)) : ((PyNumberMethods*) (OBJ))->nb_inplace_true_divide )
#define PyNumberMethods_nb_inplace_xor(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyNumberMethods_nb_inplace_xor((PyNumberMethods*) (OBJ)) : ((PyNumberMethods*) (OBJ))->nb_inplace_xor )
#define PyNumberMethods_nb_int(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyNumberMethods_nb_int((PyNumberMethods*) (OBJ)) : ((PyNumberMethods*) (OBJ))->nb_int )
#define PyNumberMethods_nb_invert(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyNumberMethods_nb_invert((PyNumberMethods*) (OBJ)) : ((PyNumberMethods*) (OBJ))->nb_invert )
#define PyNumberMethods_nb_lshift(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyNumberMethods_nb_lshift((PyNumberMethods*) (OBJ)) : ((PyNumberMethods*) (OBJ))->nb_lshift )
#define PyNumberMethods_nb_matrix_multiply(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyNumberMethods_nb_matrix_multiply((PyNumberMethods*) (OBJ)) : ((PyNumberMethods*) (OBJ))->nb_matrix_multiply )
#define PyNumberMethods_nb_multiply(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyNumberMethods_nb_multiply((PyNumberMethods*) (OBJ)) : ((PyNumberMethods*) (OBJ))->nb_multiply )
#define PyNumberMethods_nb_negative(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyNumberMethods_nb_negative((PyNumberMethods*) (OBJ)) : ((PyNumberMethods*) (OBJ))->nb_negative )
#define PyNumberMethods_nb_or(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyNumberMethods_nb_or((PyNumberMethods*) (OBJ)) : ((PyNumberMethods*) (OBJ))->nb_or )
#define PyNumberMethods_nb_positive(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyNumberMethods_nb_positive((PyNumberMethods*) (OBJ)) : ((PyNumberMethods*) (OBJ))->nb_positive )
#define PyNumberMethods_nb_power(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyNumberMethods_nb_power((PyNumberMethods*) (OBJ)) : ((PyNumberMethods*) (OBJ))->nb_power )
#define PyNumberMethods_nb_remainder(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyNumberMethods_nb_remainder((PyNumberMethods*) (OBJ)) : ((PyNumberMethods*) (OBJ))->nb_remainder )
#define PyNumberMethods_nb_rshift(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyNumberMethods_nb_rshift((PyNumberMethods*) (OBJ)) : ((PyNumberMethods*) (OBJ))->nb_rshift )
#define PyNumberMethods_nb_subtract(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyNumberMethods_nb_subtract((PyNumberMethods*) (OBJ)) : ((PyNumberMethods*) (OBJ))->nb_subtract )
#define PyNumberMethods_nb_true_divide(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyNumberMethods_nb_true_divide((PyNumberMethods*) (OBJ)) : ((PyNumberMethods*) (OBJ))->nb_true_divide )
#define PyNumberMethods_nb_xor(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyNumberMethods_nb_xor((PyNumberMethods*) (OBJ)) : ((PyNumberMethods*) (OBJ))->nb_xor )
#define PyObject_ob_refcnt(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyObject_ob_refcnt((PyObject*) (OBJ)) : ((PyObject*) (OBJ))->ob_refcnt )
#define PyObject_ob_type(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyObject_ob_type((PyObject*) (OBJ)) : ((PyObject*) (OBJ))->ob_type )
#define PySequenceMethods_sq_ass_item(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PySequenceMethods_sq_ass_item((PySequenceMethods*) (OBJ)) : ((PySequenceMethods*) (OBJ))->sq_ass_item )
#define PySequenceMethods_sq_concat(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PySequenceMethods_sq_concat((PySequenceMethods*) (OBJ)) : ((PySequenceMethods*) (OBJ))->sq_concat )
#define PySequenceMethods_sq_contains(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PySequenceMethods_sq_contains((PySequenceMethods*) (OBJ)) : ((PySequenceMethods*) (OBJ))->sq_contains )
#define PySequenceMethods_sq_inplace_concat(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PySequenceMethods_sq_inplace_concat((PySequenceMethods*) (OBJ)) : ((PySequenceMethods*) (OBJ))->sq_inplace_concat )
#define PySequenceMethods_sq_inplace_repeat(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PySequenceMethods_sq_inplace_repeat((PySequenceMethods*) (OBJ)) : ((PySequenceMethods*) (OBJ))->sq_inplace_repeat )
#define PySequenceMethods_sq_item(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PySequenceMethods_sq_item((PySequenceMethods*) (OBJ)) : ((PySequenceMethods*) (OBJ))->sq_item )
#define PySequenceMethods_sq_length(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PySequenceMethods_sq_length((PySequenceMethods*) (OBJ)) : ((PySequenceMethods*) (OBJ))->sq_length )
#define PySequenceMethods_sq_repeat(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PySequenceMethods_sq_repeat((PySequenceMethods*) (OBJ)) : ((PySequenceMethods*) (OBJ))->sq_repeat )
#define PySetObject_used(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PySetObject_used((PySetObject*) (OBJ)) : ((PySetObject*) (OBJ))->used )
#define PySliceObject_start(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PySliceObject_start((PySliceObject*) (OBJ)) : ((PySliceObject*) (OBJ))->start )
#define PySliceObject_step(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PySliceObject_step((PySliceObject*) (OBJ)) : ((PySliceObject*) (OBJ))->step )
#define PySliceObject_stop(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PySliceObject_stop((PySliceObject*) (OBJ)) : ((PySliceObject*) (OBJ))->stop )
#define PyTupleObject_ob_item(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyTupleObject_ob_item((PyTupleObject*) (OBJ)) : ((PyTupleObject*) (OBJ))->ob_item )
#define PyTypeObject_tp_alloc(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyTypeObject_tp_alloc((PyTypeObject*) (OBJ)) : ((PyTypeObject*) (OBJ))->tp_alloc )
#define PyTypeObject_tp_as_async(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyTypeObject_tp_as_async((PyTypeObject*) (OBJ)) : ((PyTypeObject*) (OBJ))->tp_as_async )
#define PyTypeObject_tp_as_buffer(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyTypeObject_tp_as_buffer((PyTypeObject*) (OBJ)) : ((PyTypeObject*) (OBJ))->tp_as_buffer )
#define PyTypeObject_tp_as_mapping(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyTypeObject_tp_as_mapping((PyTypeObject*) (OBJ)) : ((PyTypeObject*) (OBJ))->tp_as_mapping )
#define PyTypeObject_tp_as_number(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyTypeObject_tp_as_number((PyTypeObject*) (OBJ)) : ((PyTypeObject*) (OBJ))->tp_as_number )
#define PyTypeObject_tp_as_sequence(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyTypeObject_tp_as_sequence((PyTypeObject*) (OBJ)) : ((PyTypeObject*) (OBJ))->tp_as_sequence )
#define PyTypeObject_tp_base(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyTypeObject_tp_base((PyTypeObject*) (OBJ)) : ((PyTypeObject*) (OBJ))->tp_base )
#define PyTypeObject_tp_bases(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyTypeObject_tp_bases((PyTypeObject*) (OBJ)) : ((PyTypeObject*) (OBJ))->tp_bases )
#define PyTypeObject_tp_basicsize(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyTypeObject_tp_basicsize((PyTypeObject*) (OBJ)) : ((PyTypeObject*) (OBJ))->tp_basicsize )
#define PyTypeObject_tp_cache(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyTypeObject_tp_cache((PyTypeObject*) (OBJ)) : ((PyTypeObject*) (OBJ))->tp_cache )
#define PyTypeObject_tp_call(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyTypeObject_tp_call((PyTypeObject*) (OBJ)) : ((PyTypeObject*) (OBJ))->tp_call )
#define PyTypeObject_tp_clear(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyTypeObject_tp_clear((PyTypeObject*) (OBJ)) : ((PyTypeObject*) (OBJ))->tp_clear )
#define PyTypeObject_tp_dealloc(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyTypeObject_tp_dealloc((PyTypeObject*) (OBJ)) : ((PyTypeObject*) (OBJ))->tp_dealloc )
#define PyTypeObject_tp_del(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyTypeObject_tp_del((PyTypeObject*) (OBJ)) : ((PyTypeObject*) (OBJ))->tp_del )
#define PyTypeObject_tp_descr_get(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyTypeObject_tp_descr_get((PyTypeObject*) (OBJ)) : ((PyTypeObject*) (OBJ))->tp_descr_get )
#define PyTypeObject_tp_descr_set(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyTypeObject_tp_descr_set((PyTypeObject*) (OBJ)) : ((PyTypeObject*) (OBJ))->tp_descr_set )
#define PyTypeObject_tp_dict(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyTypeObject_tp_dict((PyTypeObject*) (OBJ)) : ((PyTypeObject*) (OBJ))->tp_dict )
#define PyTypeObject_tp_dictoffset(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyTypeObject_tp_dictoffset((PyTypeObject*) (OBJ)) : ((PyTypeObject*) (OBJ))->tp_dictoffset )
#define PyTypeObject_tp_doc(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyTypeObject_tp_doc((PyTypeObject*) (OBJ)) : ((PyTypeObject*) (OBJ))->tp_doc )
#define PyTypeObject_tp_finalize(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyTypeObject_tp_finalize((PyTypeObject*) (OBJ)) : ((PyTypeObject*) (OBJ))->tp_finalize )
#define PyTypeObject_tp_flags(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyTypeObject_tp_flags((PyTypeObject*) (OBJ)) : ((PyTypeObject*) (OBJ))->tp_flags )
#define PyTypeObject_tp_free(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyTypeObject_tp_free((PyTypeObject*) (OBJ)) : ((PyTypeObject*) (OBJ))->tp_free )
#define PyTypeObject_tp_getattr(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyTypeObject_tp_getattr((PyTypeObject*) (OBJ)) : ((PyTypeObject*) (OBJ))->tp_getattr )
#define PyTypeObject_tp_getattro(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyTypeObject_tp_getattro((PyTypeObject*) (OBJ)) : ((PyTypeObject*) (OBJ))->tp_getattro )
#define PyTypeObject_tp_getset(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyTypeObject_tp_getset((PyTypeObject*) (OBJ)) : ((PyTypeObject*) (OBJ))->tp_getset )
#define PyTypeObject_tp_hash(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyTypeObject_tp_hash((PyTypeObject*) (OBJ)) : ((PyTypeObject*) (OBJ))->tp_hash )
#define PyTypeObject_tp_init(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyTypeObject_tp_init((PyTypeObject*) (OBJ)) : ((PyTypeObject*) (OBJ))->tp_init )
#define PyTypeObject_tp_is_gc(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyTypeObject_tp_is_gc((PyTypeObject*) (OBJ)) : ((PyTypeObject*) (OBJ))->tp_is_gc )
#define PyTypeObject_tp_itemsize(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyTypeObject_tp_itemsize((PyTypeObject*) (OBJ)) : ((PyTypeObject*) (OBJ))->tp_itemsize )
#define PyTypeObject_tp_iter(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyTypeObject_tp_iter((PyTypeObject*) (OBJ)) : ((PyTypeObject*) (OBJ))->tp_iter )
#define PyTypeObject_tp_iternext(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyTypeObject_tp_iternext((PyTypeObject*) (OBJ)) : ((PyTypeObject*) (OBJ))->tp_iternext )
#define PyTypeObject_tp_members(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyTypeObject_tp_members((PyTypeObject*) (OBJ)) : ((PyTypeObject*) (OBJ))->tp_members )
#define PyTypeObject_tp_methods(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyTypeObject_tp_methods((PyTypeObject*) (OBJ)) : ((PyTypeObject*) (OBJ))->tp_methods )
#define PyTypeObject_tp_mro(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyTypeObject_tp_mro((PyTypeObject*) (OBJ)) : ((PyTypeObject*) (OBJ))->tp_mro )
#define PyTypeObject_tp_name(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyTypeObject_tp_name((PyTypeObject*) (OBJ)) : ((PyTypeObject*) (OBJ))->tp_name )
#define PyTypeObject_tp_new(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyTypeObject_tp_new((PyTypeObject*) (OBJ)) : ((PyTypeObject*) (OBJ))->tp_new )
#define PyTypeObject_tp_repr(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyTypeObject_tp_repr((PyTypeObject*) (OBJ)) : ((PyTypeObject*) (OBJ))->tp_repr )
#define PyTypeObject_tp_richcompare(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyTypeObject_tp_richcompare((PyTypeObject*) (OBJ)) : ((PyTypeObject*) (OBJ))->tp_richcompare )
#define PyTypeObject_tp_setattr(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyTypeObject_tp_setattr((PyTypeObject*) (OBJ)) : ((PyTypeObject*) (OBJ))->tp_setattr )
#define PyTypeObject_tp_setattro(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyTypeObject_tp_setattro((PyTypeObject*) (OBJ)) : ((PyTypeObject*) (OBJ))->tp_setattro )
#define PyTypeObject_tp_str(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyTypeObject_tp_str((PyTypeObject*) (OBJ)) : ((PyTypeObject*) (OBJ))->tp_str )
#define PyTypeObject_tp_subclasses(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyTypeObject_tp_subclasses((PyTypeObject*) (OBJ)) : ((PyTypeObject*) (OBJ))->tp_subclasses )
#define PyTypeObject_tp_traverse(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyTypeObject_tp_traverse((PyTypeObject*) (OBJ)) : ((PyTypeObject*) (OBJ))->tp_traverse )
#define PyTypeObject_tp_vectorcall(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyTypeObject_tp_vectorcall((PyTypeObject*) (OBJ)) : ((PyTypeObject*) (OBJ))->tp_vectorcall )
#define PyTypeObject_tp_vectorcall_offset(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyTypeObject_tp_vectorcall_offset((PyTypeObject*) (OBJ)) : ((PyTypeObject*) (OBJ))->tp_vectorcall_offset )
#define PyTypeObject_tp_version_tag(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyTypeObject_tp_version_tag((PyTypeObject*) (OBJ)) : ((PyTypeObject*) (OBJ))->tp_version_tag )
#define PyTypeObject_tp_weaklist(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyTypeObject_tp_weaklist((PyTypeObject*) (OBJ)) : ((PyTypeObject*) (OBJ))->tp_weaklist )
#define PyTypeObject_tp_weaklistoffset(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyTypeObject_tp_weaklistoffset((PyTypeObject*) (OBJ)) : ((PyTypeObject*) (OBJ))->tp_weaklistoffset )
#define PyUnicodeObject_data(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyUnicodeObject_data((PyUnicodeObject*) (OBJ)) : ((PyUnicodeObject*) (OBJ))->data )
#define PyVarObject_ob_size(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_PyVarObject_ob_size((PyVarObject*) (OBJ)) : ((PyVarObject*) (OBJ))->ob_size )
#define mmap_object_data(OBJ) ( points_to_py_handle_space(OBJ) ? GraalPy_get_mmap_object_data((mmap_object*) (OBJ)) : ((mmap_object*) (OBJ))->data )
#define set_PyByteArrayObject_ob_exports(OBJ, VALUE) { if (points_to_py_handle_space(OBJ)) GraalPy_set_PyByteArrayObject_ob_exports((PyByteArrayObject*) (OBJ), (VALUE)); else  ((PyByteArrayObject*) (OBJ))->ob_exports = (VALUE); }
#define set_PyFrameObject_f_lineno(OBJ, VALUE) { if (points_to_py_handle_space(OBJ)) GraalPy_set_PyFrameObject_f_lineno((PyFrameObject*) (OBJ), (VALUE)); else  ((PyFrameObject*) (OBJ))->f_lineno = (VALUE); }
#define set_PyModuleObject_md_def(OBJ, VALUE) { if (points_to_py_handle_space(OBJ)) GraalPy_set_PyModuleObject_md_def((PyModuleObject*) (OBJ), (VALUE)); else  ((PyModuleObject*) (OBJ))->md_def = (VALUE); }
#define set_PyModuleObject_md_state(OBJ, VALUE) { if (points_to_py_handle_space(OBJ)) GraalPy_set_PyModuleObject_md_state((PyModuleObject*) (OBJ), (VALUE)); else  ((PyModuleObject*) (OBJ))->md_state = (VALUE); }
#define set_PyObject_ob_refcnt(OBJ, VALUE) { if (points_to_py_handle_space(OBJ)) GraalPy_set_PyObject_ob_refcnt((PyObject*) (OBJ), (VALUE)); else  ((PyObject*) (OBJ))->ob_refcnt = (VALUE); }
#define set_PyTypeObject_tp_alloc(OBJ, VALUE) { if (points_to_py_handle_space(OBJ)) GraalPy_set_PyTypeObject_tp_alloc((PyTypeObject*) (OBJ), (VALUE)); else  ((PyTypeObject*) (OBJ))->tp_alloc = (VALUE); }
#define set_PyTypeObject_tp_as_buffer(OBJ, VALUE) { if (points_to_py_handle_space(OBJ)) GraalPy_set_PyTypeObject_tp_as_buffer((PyTypeObject*) (OBJ), (VALUE)); else  ((PyTypeObject*) (OBJ))->tp_as_buffer = (VALUE); }
#define set_PyTypeObject_tp_base(OBJ, VALUE) { if (points_to_py_handle_space(OBJ)) GraalPy_set_PyTypeObject_tp_base((PyTypeObject*) (OBJ), (VALUE)); else  ((PyTypeObject*) (OBJ))->tp_base = (VALUE); }
#define set_PyTypeObject_tp_bases(OBJ, VALUE) { if (points_to_py_handle_space(OBJ)) GraalPy_set_PyTypeObject_tp_bases((PyTypeObject*) (OBJ), (VALUE)); else  ((PyTypeObject*) (OBJ))->tp_bases = (VALUE); }
#define set_PyTypeObject_tp_basicsize(OBJ, VALUE) { if (points_to_py_handle_space(OBJ)) GraalPy_set_PyTypeObject_tp_basicsize((PyTypeObject*) (OBJ), (VALUE)); else  ((PyTypeObject*) (OBJ))->tp_basicsize = (VALUE); }
#define set_PyTypeObject_tp_clear(OBJ, VALUE) { if (points_to_py_handle_space(OBJ)) GraalPy_set_PyTypeObject_tp_clear((PyTypeObject*) (OBJ), (VALUE)); else  ((PyTypeObject*) (OBJ))->tp_clear = (VALUE); }
#define set_PyTypeObject_tp_dealloc(OBJ, VALUE) { if (points_to_py_handle_space(OBJ)) GraalPy_set_PyTypeObject_tp_dealloc((PyTypeObject*) (OBJ), (VALUE)); else  ((PyTypeObject*) (OBJ))->tp_dealloc = (VALUE); }
#define set_PyTypeObject_tp_dict(OBJ, VALUE) { if (points_to_py_handle_space(OBJ)) GraalPy_set_PyTypeObject_tp_dict((PyTypeObject*) (OBJ), (VALUE)); else  ((PyTypeObject*) (OBJ))->tp_dict = (VALUE); }
#define set_PyTypeObject_tp_dictoffset(OBJ, VALUE) { if (points_to_py_handle_space(OBJ)) GraalPy_set_PyTypeObject_tp_dictoffset((PyTypeObject*) (OBJ), (VALUE)); else  ((PyTypeObject*) (OBJ))->tp_dictoffset = (VALUE); }
#define set_PyTypeObject_tp_finalize(OBJ, VALUE) { if (points_to_py_handle_space(OBJ)) GraalPy_set_PyTypeObject_tp_finalize((PyTypeObject*) (OBJ), (VALUE)); else  ((PyTypeObject*) (OBJ))->tp_finalize = (VALUE); }
#define set_PyTypeObject_tp_flags(OBJ, VALUE) { if (points_to_py_handle_space(OBJ)) GraalPy_set_PyTypeObject_tp_flags((PyTypeObject*) (OBJ), (VALUE)); else  ((PyTypeObject*) (OBJ))->tp_flags = (VALUE); }
#define set_PyTypeObject_tp_free(OBJ, VALUE) { if (points_to_py_handle_space(OBJ)) GraalPy_set_PyTypeObject_tp_free((PyTypeObject*) (OBJ), (VALUE)); else  ((PyTypeObject*) (OBJ))->tp_free = (VALUE); }
#define set_PyTypeObject_tp_getattr(OBJ, VALUE) { if (points_to_py_handle_space(OBJ)) GraalPy_set_PyTypeObject_tp_getattr((PyTypeObject*) (OBJ), (VALUE)); else  ((PyTypeObject*) (OBJ))->tp_getattr = (VALUE); }
#define set_PyTypeObject_tp_getattro(OBJ, VALUE) { if (points_to_py_handle_space(OBJ)) GraalPy_set_PyTypeObject_tp_getattro((PyTypeObject*) (OBJ), (VALUE)); else  ((PyTypeObject*) (OBJ))->tp_getattro = (VALUE); }
#define set_PyTypeObject_tp_itemsize(OBJ, VALUE) { if (points_to_py_handle_space(OBJ)) GraalPy_set_PyTypeObject_tp_itemsize((PyTypeObject*) (OBJ), (VALUE)); else  ((PyTypeObject*) (OBJ))->tp_itemsize = (VALUE); }
#define set_PyTypeObject_tp_iter(OBJ, VALUE) { if (points_to_py_handle_space(OBJ)) GraalPy_set_PyTypeObject_tp_iter((PyTypeObject*) (OBJ), (VALUE)); else  ((PyTypeObject*) (OBJ))->tp_iter = (VALUE); }
#define set_PyTypeObject_tp_iternext(OBJ, VALUE) { if (points_to_py_handle_space(OBJ)) GraalPy_set_PyTypeObject_tp_iternext((PyTypeObject*) (OBJ), (VALUE)); else  ((PyTypeObject*) (OBJ))->tp_iternext = (VALUE); }
#define set_PyTypeObject_tp_mro(OBJ, VALUE) { if (points_to_py_handle_space(OBJ)) GraalPy_set_PyTypeObject_tp_mro((PyTypeObject*) (OBJ), (VALUE)); else  ((PyTypeObject*) (OBJ))->tp_mro = (VALUE); }
#define set_PyTypeObject_tp_new(OBJ, VALUE) { if (points_to_py_handle_space(OBJ)) GraalPy_set_PyTypeObject_tp_new((PyTypeObject*) (OBJ), (VALUE)); else  ((PyTypeObject*) (OBJ))->tp_new = (VALUE); }
#define set_PyTypeObject_tp_setattr(OBJ, VALUE) { if (points_to_py_handle_space(OBJ)) GraalPy_set_PyTypeObject_tp_setattr((PyTypeObject*) (OBJ), (VALUE)); else  ((PyTypeObject*) (OBJ))->tp_setattr = (VALUE); }
#define set_PyTypeObject_tp_setattro(OBJ, VALUE) { if (points_to_py_handle_space(OBJ)) GraalPy_set_PyTypeObject_tp_setattro((PyTypeObject*) (OBJ), (VALUE)); else  ((PyTypeObject*) (OBJ))->tp_setattro = (VALUE); }
#define set_PyTypeObject_tp_subclasses(OBJ, VALUE) { if (points_to_py_handle_space(OBJ)) GraalPy_set_PyTypeObject_tp_subclasses((PyTypeObject*) (OBJ), (VALUE)); else  ((PyTypeObject*) (OBJ))->tp_subclasses = (VALUE); }
#define set_PyTypeObject_tp_traverse(OBJ, VALUE) { if (points_to_py_handle_space(OBJ)) GraalPy_set_PyTypeObject_tp_traverse((PyTypeObject*) (OBJ), (VALUE)); else  ((PyTypeObject*) (OBJ))->tp_traverse = (VALUE); }
#define set_PyTypeObject_tp_vectorcall_offset(OBJ, VALUE) { if (points_to_py_handle_space(OBJ)) GraalPy_set_PyTypeObject_tp_vectorcall_offset((PyTypeObject*) (OBJ), (VALUE)); else  ((PyTypeObject*) (OBJ))->tp_vectorcall_offset = (VALUE); }
#define set_PyTypeObject_tp_weaklistoffset(OBJ, VALUE) { if (points_to_py_handle_space(OBJ)) GraalPy_set_PyTypeObject_tp_weaklistoffset((PyTypeObject*) (OBJ), (VALUE)); else  ((PyTypeObject*) (OBJ))->tp_weaklistoffset = (VALUE); }
#define set_PyVarObject_ob_size(OBJ, VALUE) { if (points_to_py_handle_space(OBJ)) GraalPy_set_PyVarObject_ob_size((PyVarObject*) (OBJ), (VALUE)); else  ((PyVarObject*) (OBJ))->ob_size = (VALUE); }
// {{end CAPI_BUILTINS}}


#define BUILTIN(ID, NAME, RET, ...) extern PyAPI_FUNC(RET) (*Graal##NAME)(__VA_ARGS__);
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

static void PyTruffle_Log(int level, const char* format, ... ) {
	if (Py_Truffle_Options & level) {
		char buffer[1024];
		va_list args;
		va_start(args, format);
		vsprintf(buffer,format, args);
#ifndef EXCLUDE_POLYGLOT_API
		GraalPyTruffle_LogString(level, polyglot_from_string(buffer, SRC_CS));
#else
		GraalPyTruffle_LogString(level, buffer);
#endif
		va_end(args);
	}
}

#ifndef EXCLUDE_POLYGLOT_API

typedef int (*cache_query_t)(uint64_t);
typedef PyObject* (*ptr_cache_t)(PyObject*);
typedef void* (*void_ptr_cache_t)(void*);
PyAPI_DATA(ptr_cache_t) ptr_cache;
PyAPI_DATA(cache_query_t) points_to_py_handle_space;
PyAPI_FUNC(void) initialize_type_structure(PyTypeObject* structure, PyTypeObject* ptype, polyglot_typeid tid);

void register_native_slots(PyTypeObject* managed_class, PyGetSetDef* getsets, PyMemberDef* members);

extern ptr_cache_t pythonToNative;
extern void_ptr_cache_t javaStringToTruffleString;

MUST_INLINE
void* truffleString(const char* string) {
	return string == NULL ? NULL : polyglot_from_string(string, SRC_CS);
}

MUST_INLINE
void* function_pointer_to_java(void* obj) {
    if (!polyglot_is_value(obj)) {
    	return resolve_function(obj);
    }
    return obj;
}

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

#define TDEBUG __builtin_debugtrap()

#define PY_TRUFFLE_TYPE_GENERIC(__TYPE_NAME__, __SUPER_TYPE__, __FLAGS__, __SIZE__, __ITEMSIZE__, __ALLOC__, __DEALLOC__, __FREE__, __VCALL_OFFSET__) {\
    PyVarObject_HEAD_INIT((__SUPER_TYPE__), 0)\
    __TYPE_NAME__,                              /* tp_name */\
    (__SIZE__),                                 /* tp_basicsize */\
    (__ITEMSIZE__),                             /* tp_itemsize */\
    (__DEALLOC__),                              /* tp_dealloc */\
    (__VCALL_OFFSET__),                         /* tp_vectorcall_offset */\
    0,                                          /* tp_getattr */\
    0,                                          /* tp_setattr */\
    0,                                          /* tp_reserved */\
    0,                                          /* tp_repr */\
    0,                                          /* tp_as_number */\
    0,                                          /* tp_as_sequence */\
    0,                                          /* tp_as_mapping */\
    0,                                          /* tp_hash */\
    0,                                          /* tp_call */\
    0,                                          /* tp_str */\
    0,                                          /* tp_getattro */\
    0,                                          /* tp_setattro */\
    0,                                          /* tp_as_buffer */\
    (__FLAGS__),                                /* tp_flags */\
    0,                                          /* tp_doc */\
    0,                                          /* tp_traverse */\
    0,                                          /* tp_clear */\
    0,                                          /* tp_richcompare */\
    0,                                          /* tp_weaklistoffset */\
    0,                                          /* tp_iter */\
    0,                                          /* tp_iternext */\
    0,                                          /* tp_methods */\
    0,                                          /* tp_members */\
    0,                                          /* tp_getset */\
    0,                                          /* tp_base */\
    0,                                          /* tp_dict */\
    0,                                          /* tp_descr_get */\
    0,                                          /* tp_descr_set */\
    0,                                          /* tp_dictoffset */\
    0,                                          /* tp_init */\
    (__ALLOC__),                                /* tp_alloc */\
    0,                                          /* tp_new */\
    (__FREE__),                                 /* tp_free */\
    0,                                          /* tp_is_gc */\
}

#define PY_TRUFFLE_TYPE_WITH_ALLOC(__TYPE_NAME__, __SUPER_TYPE__, __FLAGS__, __SIZE__, __ALLOC__, __DEALLOC__, __FREE__) PY_TRUFFLE_TYPE_GENERIC(__TYPE_NAME__, __SUPER_TYPE__, __FLAGS__, __SIZE__, 0, __ALLOC__, __DEALLOC__, __FREE__, 0)
#define PY_TRUFFLE_TYPE(__TYPE_NAME__, __SUPER_TYPE__, __FLAGS__, __SIZE__) PY_TRUFFLE_TYPE_GENERIC(__TYPE_NAME__, __SUPER_TYPE__, __FLAGS__, __SIZE__, 0, 0, 0, 0, 0)
#define PY_TRUFFLE_TYPE_WITH_ITEMSIZE(__TYPE_NAME__, __SUPER_TYPE__, __FLAGS__, __SIZE__, __ITEMSIZE__) PY_TRUFFLE_TYPE_GENERIC(__TYPE_NAME__, __SUPER_TYPE__, __FLAGS__, __SIZE__, __ITEMSIZE__, 0, 0, 0, 0)

typedef PyObject* PyObjectPtr;
POLYGLOT_DECLARE_TYPE(PyObjectPtr);

// export the SizeT arg parse functions, because we use them in contrast to cpython on windows for core modules that we link dynamically
PyAPI_FUNC(int) _PyArg_Parse_SizeT(PyObject *, const char *, ...);
PyAPI_FUNC(int) _PyArg_ParseTuple_SizeT(PyObject *, const char *, ...);
PyAPI_FUNC(int) _PyArg_ParseTupleAndKeywords_SizeT(PyObject *, PyObject *,
                                                  const char *, char **, ...);
PyAPI_FUNC(int) _PyArg_VaParse_SizeT(PyObject *, const char *, va_list);
PyAPI_FUNC(int) _PyArg_VaParseTupleAndKeywords_SizeT(PyObject *, PyObject *,
                                                  const char *, char **, va_list);

#endif // !EXCLUDE_POLYGLOT_API

#endif // CAPI_H
