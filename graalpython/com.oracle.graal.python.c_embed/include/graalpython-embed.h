#include <stdio.h>
#include "Python.h"
// fix missing symbol PyExc_BaseException
#include "pyerrors.h"
#ifndef GRAALPYTHON_EMBED_H
#define GRAALPYTHON_EMBED_H





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
    BUILTIN(PyType_IsSubtype, int, PyTypeObject*, PyTypeObject*) \
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

// {{end CAPI_BUILTINS}}

#define STUB(NAME, RET, ...) extern RET NAME(__VA_ARGS__);
STUB_DEFS
#undef STUB

#endif //GRAALPYTHON_EMBED_H
