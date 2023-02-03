/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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

// {{start CAPI_BUILTINS}}
// GENERATED CODE - see PythonCextBuiltins
// generated from class com.oracle.graal.python.builtins.objects.cext.capi.CApiFunction

void unimplemented(const char* name) { printf("Function not implemented in GraalPy: %s\n", name); }
#ifdef STATS
long totalTime;
long totalCount;
#define FIRST_STATS_CONTAINER(NAME) CAPIStats __stats__##NAME = { NULL, #NAME, 0, 0};
#define STATS_CONTAINER(NAME, LAST) CAPIStats __stats__##NAME = { &__stats__##LAST, #NAME, 0, 0};
#define STATS_BEFORE(NAME) \
    totalCount++; \
    __stats__##NAME.count++;\
    if ((totalCount) % 100000 == 0)\
        printAllStats();\
    long t1 = t();
#define STATS_AFTER(NAME) \
    long delta = t() - t1;\
    __stats__##NAME.time += delta;\
    totalTime += delta;
#else
#define FIRST_STATS_CONTAINER(NAME)
#define STATS_CONTAINER(NAME, LAST)
#define STATS_BEFORE(NAME)
#define STATS_AFTER(NAME)
#endif
#define LOG_AFTER LOG("-> 0x%lx", (unsigned long) result);
#define LOG_AFTER_VOID LOGS("finished");

// explicit undef, some existing functions are redefined by macros:
#undef PyAIter_Check
#undef PyArg_Parse
#undef PyArg_ParseTuple
#undef PyArg_ParseTupleAndKeywords
#undef PyArg_UnpackTuple
#undef PyArg_VaParse
#undef PyArg_VaParseTupleAndKeywords
#undef PyArg_ValidateKeywordArguments
#undef PyAsyncGen_New
#undef PyBool_FromLong
#undef PyBuffer_FillContiguousStrides
#undef PyBuffer_FillInfo
#undef PyBuffer_FromContiguous
#undef PyBuffer_GetPointer
#undef PyBuffer_IsContiguous
#undef PyBuffer_Release
#undef PyBuffer_SizeFromFormat
#undef PyBuffer_ToContiguous
#undef PyByteArray_AsString
#undef PyByteArray_Concat
#undef PyByteArray_FromObject
#undef PyByteArray_FromStringAndSize
#undef PyByteArray_Resize
#undef PyByteArray_Size
#undef PyBytes_AsString
#undef PyBytes_AsStringAndSize
#undef PyBytes_Concat
#undef PyBytes_ConcatAndDel
#undef PyBytes_DecodeEscape
#undef PyBytes_FromFormat
#undef PyBytes_FromFormatV
#undef PyBytes_FromObject
#undef PyBytes_FromString
#undef PyBytes_FromStringAndSize
#undef PyBytes_Repr
#undef PyBytes_Size
#undef PyCFunction_Call
#undef PyCFunction_GetClass
#undef PyCFunction_GetFlags
#undef PyCFunction_GetFunction
#undef PyCFunction_GetSelf
#undef PyCFunction_New
#undef PyCFunction_NewEx
#undef PyCMethod_New
#undef PyCallIter_New
#undef PyCallable_Check
#undef PyCapsule_GetContext
#undef PyCapsule_GetDestructor
#undef PyCapsule_GetName
#undef PyCapsule_GetPointer
#undef PyCapsule_Import
#undef PyCapsule_IsValid
#undef PyCapsule_New
#undef PyCapsule_SetContext
#undef PyCapsule_SetDestructor
#undef PyCapsule_SetName
#undef PyCapsule_SetPointer
#undef PyCell_Get
#undef PyCell_New
#undef PyCell_Set
#undef PyClassMethod_New
#undef PyCode_Addr2Line
#undef PyCode_New
#undef PyCode_NewEmpty
#undef PyCode_NewWithPosOnlyArgs
#undef PyCode_Optimize
#undef PyCodec_BackslashReplaceErrors
#undef PyCodec_Decode
#undef PyCodec_Decoder
#undef PyCodec_Encode
#undef PyCodec_Encoder
#undef PyCodec_IgnoreErrors
#undef PyCodec_IncrementalDecoder
#undef PyCodec_IncrementalEncoder
#undef PyCodec_KnownEncoding
#undef PyCodec_LookupError
#undef PyCodec_NameReplaceErrors
#undef PyCodec_Register
#undef PyCodec_RegisterError
#undef PyCodec_ReplaceErrors
#undef PyCodec_StreamReader
#undef PyCodec_StreamWriter
#undef PyCodec_StrictErrors
#undef PyCodec_Unregister
#undef PyCodec_XMLCharRefReplaceErrors
#undef PyCompile_OpcodeStackEffect
#undef PyCompile_OpcodeStackEffectWithJump
#undef PyComplex_AsCComplex
#undef PyComplex_FromCComplex
#undef PyComplex_FromDoubles
#undef PyComplex_ImagAsDouble
#undef PyComplex_RealAsDouble
#undef PyConfig_Clear
#undef PyConfig_InitIsolatedConfig
#undef PyConfig_InitPythonConfig
#undef PyConfig_Read
#undef PyConfig_SetArgv
#undef PyConfig_SetBytesArgv
#undef PyConfig_SetBytesString
#undef PyConfig_SetString
#undef PyConfig_SetWideStringList
#undef PyContextVar_Get
#undef PyContextVar_New
#undef PyContextVar_Reset
#undef PyContextVar_Set
#undef PyContext_Copy
#undef PyContext_CopyCurrent
#undef PyContext_Enter
#undef PyContext_Exit
#undef PyContext_New
#undef PyCoro_New
#undef PyDescrObject_GetName
#undef PyDescrObject_GetType
#undef PyDescr_IsData
#undef PyDescr_NewClassMethod
#undef PyDescr_NewGetSet
#undef PyDescr_NewMember
#undef PyDescr_NewMethod
#undef PyDescr_NewWrapper
#undef PyDictProxy_New
#undef PyDict_Clear
#undef PyDict_Contains
#undef PyDict_Copy
#undef PyDict_DelItem
#undef PyDict_DelItemString
#undef PyDict_GetItem
#undef PyDict_GetItemString
#undef PyDict_GetItemWithError
#undef PyDict_Items
#undef PyDict_Keys
#undef PyDict_Merge
#undef PyDict_MergeFromSeq2
#undef PyDict_New
#undef PyDict_Next
#undef PyDict_SetDefault
#undef PyDict_SetItem
#undef PyDict_SetItemString
#undef PyDict_Size
#undef PyDict_Update
#undef PyDict_Values
#undef PyErr_BadArgument
#undef PyErr_BadInternalCall
#undef PyErr_CheckSignals
#undef PyErr_Clear
#undef PyErr_Display
#undef PyErr_ExceptionMatches
#undef PyErr_Fetch
#undef PyErr_Format
#undef PyErr_FormatV
#undef PyErr_GetExcInfo
#undef PyErr_GivenExceptionMatches
#undef PyErr_NewException
#undef PyErr_NewExceptionWithDoc
#undef PyErr_NoMemory
#undef PyErr_NormalizeException
#undef PyErr_Occurred
#undef PyErr_Print
#undef PyErr_PrintEx
#undef PyErr_ProgramText
#undef PyErr_ProgramTextObject
#undef PyErr_RangedSyntaxLocationObject
#undef PyErr_ResourceWarning
#undef PyErr_Restore
#undef PyErr_SetExcInfo
#undef PyErr_SetFromErrno
#undef PyErr_SetFromErrnoWithFilename
#undef PyErr_SetFromErrnoWithFilenameObject
#undef PyErr_SetFromErrnoWithFilenameObjects
#undef PyErr_SetImportError
#undef PyErr_SetImportErrorSubclass
#undef PyErr_SetInterrupt
#undef PyErr_SetInterruptEx
#undef PyErr_SetNone
#undef PyErr_SetObject
#undef PyErr_SetString
#undef PyErr_SyntaxLocation
#undef PyErr_SyntaxLocationEx
#undef PyErr_SyntaxLocationObject
#undef PyErr_WarnEx
#undef PyErr_WarnExplicit
#undef PyErr_WarnExplicitFormat
#undef PyErr_WarnExplicitObject
#undef PyErr_WarnFormat
#undef PyErr_WriteUnraisable
#undef PyEval_AcquireLock
#undef PyEval_AcquireThread
#undef PyEval_CallFunction
#undef PyEval_CallMethod
#undef PyEval_CallObjectWithKeywords
#undef PyEval_EvalCode
#undef PyEval_EvalCodeEx
#undef PyEval_EvalFrame
#undef PyEval_EvalFrameEx
#undef PyEval_GetBuiltins
#undef PyEval_GetFrame
#undef PyEval_GetFuncDesc
#undef PyEval_GetFuncName
#undef PyEval_GetGlobals
#undef PyEval_GetLocals
#undef PyEval_InitThreads
#undef PyEval_MergeCompilerFlags
#undef PyEval_ReleaseLock
#undef PyEval_ReleaseThread
#undef PyEval_RestoreThread
#undef PyEval_SaveThread
#undef PyEval_SetProfile
#undef PyEval_SetTrace
#undef PyEval_ThreadsInitialized
#undef PyExceptionClass_Name
#undef PyException_GetCause
#undef PyException_GetContext
#undef PyException_GetTraceback
#undef PyException_SetCause
#undef PyException_SetContext
#undef PyException_SetTraceback
#undef PyFile_FromFd
#undef PyFile_GetLine
#undef PyFile_NewStdPrinter
#undef PyFile_OpenCode
#undef PyFile_OpenCodeObject
#undef PyFile_SetOpenCodeHook
#undef PyFile_WriteObject
#undef PyFile_WriteString
#undef PyFloat_AsDouble
#undef PyFloat_FromDouble
#undef PyFloat_FromString
#undef PyFloat_GetInfo
#undef PyFloat_GetMax
#undef PyFloat_GetMin
#undef PyFrame_BlockPop
#undef PyFrame_BlockSetup
#undef PyFrame_FastToLocals
#undef PyFrame_FastToLocalsWithError
#undef PyFrame_GetBack
#undef PyFrame_GetCode
#undef PyFrame_GetLineNumber
#undef PyFrame_LocalsToFast
#undef PyFrame_New
#undef PyFrozenSet_New
#undef PyFunction_GetAnnotations
#undef PyFunction_GetClosure
#undef PyFunction_GetCode
#undef PyFunction_GetDefaults
#undef PyFunction_GetGlobals
#undef PyFunction_GetKwDefaults
#undef PyFunction_GetModule
#undef PyFunction_New
#undef PyFunction_NewWithQualName
#undef PyFunction_SetAnnotations
#undef PyFunction_SetClosure
#undef PyFunction_SetDefaults
#undef PyFunction_SetKwDefaults
#undef PyGC_Collect
#undef PyGC_Disable
#undef PyGC_Enable
#undef PyGC_IsEnabled
#undef PyGILState_Check
#undef PyGILState_Ensure
#undef PyGILState_GetThisThreadState
#undef PyGILState_Release
#undef PyGen_New
#undef PyGen_NewWithQualName
#undef PyHash_GetFuncDef
#undef PyImport_AddModule
#undef PyImport_AddModuleObject
#undef PyImport_AppendInittab
#undef PyImport_ExecCodeModule
#undef PyImport_ExecCodeModuleEx
#undef PyImport_ExecCodeModuleObject
#undef PyImport_ExecCodeModuleWithPathnames
#undef PyImport_ExtendInittab
#undef PyImport_GetImporter
#undef PyImport_GetMagicNumber
#undef PyImport_GetMagicTag
#undef PyImport_GetModule
#undef PyImport_GetModuleDict
#undef PyImport_Import
#undef PyImport_ImportFrozenModule
#undef PyImport_ImportFrozenModuleObject
#undef PyImport_ImportModule
#undef PyImport_ImportModuleLevel
#undef PyImport_ImportModuleLevelObject
#undef PyImport_ImportModuleNoBlock
#undef PyImport_ReloadModule
#undef PyIndex_Check
#undef PyInit__imp
#undef PyInstanceMethod_Function
#undef PyInstanceMethod_New
#undef PyInterpreterState_Clear
#undef PyInterpreterState_Delete
#undef PyInterpreterState_Get
#undef PyInterpreterState_GetDict
#undef PyInterpreterState_GetID
#undef PyInterpreterState_GetIDFromThreadState
#undef PyInterpreterState_Head
#undef PyInterpreterState_Main
#undef PyInterpreterState_New
#undef PyInterpreterState_Next
#undef PyInterpreterState_ThreadHead
#undef PyIter_Check
#undef PyIter_Next
#undef PyIter_Send
#undef PyLineTable_InitAddressRange
#undef PyLineTable_NextAddressRange
#undef PyLineTable_PreviousAddressRange
#undef PyList_Append
#undef PyList_AsTuple
#undef PyList_GetItem
#undef PyList_GetSlice
#undef PyList_Insert
#undef PyList_New
#undef PyList_Reverse
#undef PyList_SetItem
#undef PyList_SetSlice
#undef PyList_Size
#undef PyList_Sort
#undef PyLong_AsDouble
#undef PyLong_AsLong
#undef PyLong_AsLongAndOverflow
#undef PyLong_AsLongLong
#undef PyLong_AsLongLongAndOverflow
#undef PyLong_AsSize_t
#undef PyLong_AsSsize_t
#undef PyLong_AsUnsignedLong
#undef PyLong_AsUnsignedLongLong
#undef PyLong_AsUnsignedLongLongMask
#undef PyLong_AsUnsignedLongMask
#undef PyLong_AsVoidPtr
#undef PyLong_FromDouble
#undef PyLong_FromLong
#undef PyLong_FromLongLong
#undef PyLong_FromSize_t
#undef PyLong_FromSsize_t
#undef PyLong_FromString
#undef PyLong_FromUnicodeObject
#undef PyLong_FromUnsignedLong
#undef PyLong_FromUnsignedLongLong
#undef PyLong_FromVoidPtr
#undef PyLong_GetInfo
#undef PyMapping_Check
#undef PyMapping_GetItemString
#undef PyMapping_HasKey
#undef PyMapping_HasKeyString
#undef PyMapping_Items
#undef PyMapping_Keys
#undef PyMapping_Length
#undef PyMapping_SetItemString
#undef PyMapping_Size
#undef PyMapping_Values
#undef PyMem_Calloc
#undef PyMem_Free
#undef PyMem_GetAllocator
#undef PyMem_Malloc
#undef PyMem_RawCalloc
#undef PyMem_RawFree
#undef PyMem_RawMalloc
#undef PyMem_RawRealloc
#undef PyMem_Realloc
#undef PyMem_SetAllocator
#undef PyMem_SetupDebugHooks
#undef PyMember_GetOne
#undef PyMember_SetOne
#undef PyMemoryView_FromBuffer
#undef PyMemoryView_FromMemory
#undef PyMemoryView_FromObject
#undef PyMemoryView_GetContiguous
#undef PyMethodDescrObject_GetMethod
#undef PyMethod_Function
#undef PyMethod_New
#undef PyMethod_Self
#undef PyModuleDef_Init
#undef PyModule_AddFunctions
#undef PyModule_AddIntConstant
#undef PyModule_AddObject
#undef PyModule_AddObjectRef
#undef PyModule_AddStringConstant
#undef PyModule_AddType
#undef PyModule_Create2
#undef PyModule_ExecDef
#undef PyModule_FromDefAndSpec2
#undef PyModule_GetDef
#undef PyModule_GetDict
#undef PyModule_GetFilename
#undef PyModule_GetFilenameObject
#undef PyModule_GetName
#undef PyModule_GetNameObject
#undef PyModule_GetState
#undef PyModule_New
#undef PyModule_NewObject
#undef PyModule_SetDocString
#undef PyNumber_Absolute
#undef PyNumber_Add
#undef PyNumber_And
#undef PyNumber_AsSsize_t
#undef PyNumber_Check
#undef PyNumber_Divmod
#undef PyNumber_Float
#undef PyNumber_FloorDivide
#undef PyNumber_InPlaceAdd
#undef PyNumber_InPlaceAnd
#undef PyNumber_InPlaceFloorDivide
#undef PyNumber_InPlaceLshift
#undef PyNumber_InPlaceMatrixMultiply
#undef PyNumber_InPlaceMultiply
#undef PyNumber_InPlaceOr
#undef PyNumber_InPlacePower
#undef PyNumber_InPlaceRemainder
#undef PyNumber_InPlaceRshift
#undef PyNumber_InPlaceSubtract
#undef PyNumber_InPlaceTrueDivide
#undef PyNumber_InPlaceXor
#undef PyNumber_Index
#undef PyNumber_Invert
#undef PyNumber_Long
#undef PyNumber_Lshift
#undef PyNumber_MatrixMultiply
#undef PyNumber_Multiply
#undef PyNumber_Negative
#undef PyNumber_Or
#undef PyNumber_Positive
#undef PyNumber_Power
#undef PyNumber_Remainder
#undef PyNumber_Rshift
#undef PyNumber_Subtract
#undef PyNumber_ToBase
#undef PyNumber_TrueDivide
#undef PyNumber_Xor
#undef PyODict_DelItem
#undef PyODict_New
#undef PyODict_SetItem
#undef PyOS_AfterFork
#undef PyOS_AfterFork_Child
#undef PyOS_AfterFork_Parent
#undef PyOS_BeforeFork
#undef PyOS_FSPath
#undef PyOS_InterruptOccurred
#undef PyOS_Readline
#undef PyOS_double_to_string
#undef PyOS_getsig
#undef PyOS_mystricmp
#undef PyOS_mystrnicmp
#undef PyOS_setsig
#undef PyOS_snprintf
#undef PyOS_string_to_double
#undef PyOS_strtol
#undef PyOS_strtoul
#undef PyOS_vsnprintf
#undef PyObject_ASCII
#undef PyObject_AsCharBuffer
#undef PyObject_AsFileDescriptor
#undef PyObject_AsReadBuffer
#undef PyObject_AsWriteBuffer
#undef PyObject_Bytes
#undef PyObject_Call
#undef PyObject_CallFinalizer
#undef PyObject_CallFinalizerFromDealloc
#undef PyObject_CallFunction
#undef PyObject_CallFunctionObjArgs
#undef PyObject_CallMethod
#undef PyObject_CallMethodObjArgs
#undef PyObject_CallNoArgs
#undef PyObject_CallObject
#undef PyObject_Calloc
#undef PyObject_CheckBuffer
#undef PyObject_CheckReadBuffer
#undef PyObject_ClearWeakRefs
#undef PyObject_CopyData
#undef PyObject_DelItem
#undef PyObject_DelItemString
#undef PyObject_Dir
#undef PyObject_Format
#undef PyObject_Free
#undef PyObject_GC_Del
#undef PyObject_GC_IsFinalized
#undef PyObject_GC_IsTracked
#undef PyObject_GC_Track
#undef PyObject_GC_UnTrack
#undef PyObject_GET_WEAKREFS_LISTPTR
#undef PyObject_GenericGetAttr
#undef PyObject_GenericGetDict
#undef PyObject_GenericSetAttr
#undef PyObject_GenericSetDict
#undef PyObject_GetAIter
#undef PyObject_GetArenaAllocator
#undef PyObject_GetAttr
#undef PyObject_GetAttrString
#undef PyObject_GetBuffer
#undef PyObject_GetDoc
#undef PyObject_GetItem
#undef PyObject_GetIter
#undef PyObject_HasAttr
#undef PyObject_HasAttrString
#undef PyObject_Hash
#undef PyObject_HashNotImplemented
#undef PyObject_IS_GC
#undef PyObject_Init
#undef PyObject_InitVar
#undef PyObject_IsInstance
#undef PyObject_IsSubclass
#undef PyObject_IsTrue
#undef PyObject_Length
#undef PyObject_LengthHint
#undef PyObject_Malloc
#undef PyObject_Not
#undef PyObject_Print
#undef PyObject_Realloc
#undef PyObject_Repr
#undef PyObject_RichCompare
#undef PyObject_RichCompareBool
#undef PyObject_SelfIter
#undef PyObject_SetArenaAllocator
#undef PyObject_SetAttr
#undef PyObject_SetAttrString
#undef PyObject_SetDoc
#undef PyObject_SetItem
#undef PyObject_Size
#undef PyObject_Str
#undef PyObject_Type
#undef PyObject_VectorcallDict
#undef PyObject_VectorcallMethod
#undef PyPickleBuffer_FromObject
#undef PyPickleBuffer_GetBuffer
#undef PyPickleBuffer_Release
#undef PyPreConfig_InitIsolatedConfig
#undef PyPreConfig_InitPythonConfig
#undef PyRun_AnyFile
#undef PyRun_AnyFileEx
#undef PyRun_AnyFileExFlags
#undef PyRun_AnyFileFlags
#undef PyRun_File
#undef PyRun_FileEx
#undef PyRun_FileExFlags
#undef PyRun_FileFlags
#undef PyRun_InteractiveLoop
#undef PyRun_InteractiveLoopFlags
#undef PyRun_InteractiveOne
#undef PyRun_InteractiveOneFlags
#undef PyRun_InteractiveOneObject
#undef PyRun_SimpleFile
#undef PyRun_SimpleFileEx
#undef PyRun_SimpleFileExFlags
#undef PyRun_SimpleString
#undef PyRun_SimpleStringFlags
#undef PyRun_String
#undef PyRun_StringFlags
#undef PySeqIter_New
#undef PySequence_Check
#undef PySequence_Concat
#undef PySequence_Contains
#undef PySequence_Count
#undef PySequence_DelItem
#undef PySequence_DelSlice
#undef PySequence_Fast
#undef PySequence_GetItem
#undef PySequence_GetSlice
#undef PySequence_In
#undef PySequence_InPlaceConcat
#undef PySequence_InPlaceRepeat
#undef PySequence_Index
#undef PySequence_Length
#undef PySequence_List
#undef PySequence_Repeat
#undef PySequence_SetItem
#undef PySequence_SetSlice
#undef PySequence_Size
#undef PySequence_Tuple
#undef PySet_Add
#undef PySet_Clear
#undef PySet_Contains
#undef PySet_Discard
#undef PySet_New
#undef PySet_Pop
#undef PySet_Size
#undef PySignal_SetWakeupFd
#undef PySlice_AdjustIndices
#undef PySlice_GetIndices
#undef PySlice_GetIndicesEx
#undef PySlice_New
#undef PySlice_Start
#undef PySlice_Step
#undef PySlice_Stop
#undef PySlice_Unpack
#undef PyState_AddModule
#undef PyState_FindModule
#undef PyState_RemoveModule
#undef PyStaticMethod_New
#undef PyStatus_Error
#undef PyStatus_Exception
#undef PyStatus_Exit
#undef PyStatus_IsError
#undef PyStatus_IsExit
#undef PyStatus_NoMemory
#undef PyStatus_Ok
#undef PyStructSequence_GetItem
#undef PyStructSequence_InitType
#undef PyStructSequence_InitType2
#undef PyStructSequence_New
#undef PyStructSequence_NewType
#undef PyStructSequence_SetItem
#undef PySys_AddAuditHook
#undef PySys_AddWarnOption
#undef PySys_AddWarnOptionUnicode
#undef PySys_AddXOption
#undef PySys_Audit
#undef PySys_FormatStderr
#undef PySys_FormatStdout
#undef PySys_GetObject
#undef PySys_GetXOptions
#undef PySys_HasWarnOptions
#undef PySys_ResetWarnOptions
#undef PySys_SetArgv
#undef PySys_SetArgvEx
#undef PySys_SetObject
#undef PySys_SetPath
#undef PySys_WriteStderr
#undef PySys_WriteStdout
#undef PyThreadState_Clear
#undef PyThreadState_Delete
#undef PyThreadState_DeleteCurrent
#undef PyThreadState_Get
#undef PyThreadState_GetDict
#undef PyThreadState_GetFrame
#undef PyThreadState_GetID
#undef PyThreadState_GetInterpreter
#undef PyThreadState_New
#undef PyThreadState_Next
#undef PyThreadState_SetAsyncExc
#undef PyThreadState_Swap
#undef PyThread_GetInfo
#undef PyThread_ReInitTLS
#undef PyThread_acquire_lock
#undef PyThread_acquire_lock_timed
#undef PyThread_allocate_lock
#undef PyThread_create_key
#undef PyThread_delete_key
#undef PyThread_delete_key_value
#undef PyThread_exit_thread
#undef PyThread_free_lock
#undef PyThread_get_key_value
#undef PyThread_get_stacksize
#undef PyThread_get_thread_ident
#undef PyThread_get_thread_native_id
#undef PyThread_init_thread
#undef PyThread_release_lock
#undef PyThread_set_key_value
#undef PyThread_set_stacksize
#undef PyThread_start_new_thread
#undef PyThread_tss_alloc
#undef PyThread_tss_create
#undef PyThread_tss_delete
#undef PyThread_tss_free
#undef PyThread_tss_get
#undef PyThread_tss_is_created
#undef PyThread_tss_set
#undef PyTraceBack_Here
#undef PyTraceBack_Print
#undef PyTraceMalloc_Track
#undef PyTraceMalloc_Untrack
#undef PyTruffleByteArray_FromStringAndSize
#undef PyTruffleBytes_Concat
#undef PyTruffleBytes_FromFormat
#undef PyTruffleBytes_FromStringAndSize
#undef PyTruffleCMethod_NewEx
#undef PyTruffleComplex_AsCComplex
#undef PyTruffleContextVar_Get
#undef PyTruffleDescr_NewClassMethod
#undef PyTruffleDescr_NewGetSet
#undef PyTruffleDict_Next
#undef PyTruffleErr_Fetch
#undef PyTruffleErr_GetExcInfo
#undef PyTruffleFrame_New
#undef PyTruffleHash_InitSecret
#undef PyTruffleLong_AsPrimitive
#undef PyTruffleLong_FromString
#undef PyTruffleLong_One
#undef PyTruffleLong_Zero
#undef PyTruffleModule_AddFunctionToModule
#undef PyTruffleNumber_BinOp
#undef PyTruffleNumber_InPlaceBinOp
#undef PyTruffleNumber_UnaryOp
#undef PyTruffleObject_CallFunctionObjArgs
#undef PyTruffleObject_CallMethodObjArgs
#undef PyTruffleObject_GenericGetAttr
#undef PyTruffleObject_GenericSetAttr
#undef PyTruffleObject_GetItemString
#undef PyTruffleState_FindModule
#undef PyTruffleStructSequence_InitType2
#undef PyTruffleStructSequence_NewType
#undef PyTruffleToCharPointer
#undef PyTruffleType_AddFunctionToType
#undef PyTruffleType_AddGetSet
#undef PyTruffleType_AddMember
#undef PyTruffleType_AddSlot
#undef PyTruffleUnicode_Decode
#undef PyTruffleUnicode_DecodeUTF8Stateful
#undef PyTruffleUnicode_InternInPlace
#undef PyTruffleUnicode_New
#undef PyTruffle_Arg_ParseTupleAndKeywords
#undef PyTruffle_ByteArray_EmptyWithCapacity
#undef PyTruffle_Bytes_AsString
#undef PyTruffle_Bytes_CheckEmbeddedNull
#undef PyTruffle_Bytes_EmptyWithCapacity
#undef PyTruffle_Compute_Mro
#undef PyTruffle_Debug
#undef PyTruffle_DebugTrace
#undef PyTruffle_Ellipsis
#undef PyTruffle_False
#undef PyTruffle_FatalErrorFunc
#undef PyTruffle_FileSystemDefaultEncoding
#undef PyTruffle_Get_Inherited_Native_Slots
#undef PyTruffle_HashConstant
#undef PyTruffle_LogString
#undef PyTruffle_MemoryViewFromBuffer
#undef PyTruffle_Native_Options
#undef PyTruffle_NewTypeDict
#undef PyTruffle_NoValue
#undef PyTruffle_None
#undef PyTruffle_NotImplemented
#undef PyTruffle_OS_DoubleToString
#undef PyTruffle_OS_StringToDouble
#undef PyTruffle_Object_Alloc
#undef PyTruffle_Object_Free
#undef PyTruffle_Register_NULL
#undef PyTruffle_SeqIter_New
#undef PyTruffle_Set_Native_Slots
#undef PyTruffle_Set_SulongType
#undef PyTruffle_ToNative
#undef PyTruffle_Trace_Type
#undef PyTruffle_True
#undef PyTruffle_Type
#undef PyTruffle_Type_Modified
#undef PyTruffle_Unicode_AsUnicodeAndSize
#undef PyTruffle_Unicode_AsWideChar
#undef PyTruffle_Unicode_DecodeUTF32
#undef PyTruffle_Unicode_FromFormat
#undef PyTruffle_Unicode_FromWchar
#undef PyTruffle_tss_create
#undef PyTruffle_tss_delete
#undef PyTruffle_tss_get
#undef PyTruffle_tss_set
#undef PyTuple_GetItem
#undef PyTuple_GetSlice
#undef PyTuple_New
#undef PyTuple_Pack
#undef PyTuple_SetItem
#undef PyTuple_Size
#undef PyType_ClearCache
#undef PyType_FromModuleAndSpec
#undef PyType_FromSpec
#undef PyType_FromSpecWithBases
#undef PyType_FromSpecWithBasesAndMeta
#undef PyType_GenericAlloc
#undef PyType_GenericNew
#undef PyType_GetFlags
#undef PyType_GetModule
#undef PyType_GetModuleState
#undef PyType_GetSlot
#undef PyType_IsSubtype
#undef PyType_Modified
#undef PyType_Ready
#undef PyUnicodeDecodeError_Create
#undef PyUnicodeDecodeError_GetEncoding
#undef PyUnicodeDecodeError_GetEnd
#undef PyUnicodeDecodeError_GetObject
#undef PyUnicodeDecodeError_GetReason
#undef PyUnicodeDecodeError_GetStart
#undef PyUnicodeDecodeError_SetEnd
#undef PyUnicodeDecodeError_SetReason
#undef PyUnicodeDecodeError_SetStart
#undef PyUnicodeEncodeError_Create
#undef PyUnicodeEncodeError_GetEncoding
#undef PyUnicodeEncodeError_GetEnd
#undef PyUnicodeEncodeError_GetObject
#undef PyUnicodeEncodeError_GetReason
#undef PyUnicodeEncodeError_GetStart
#undef PyUnicodeEncodeError_SetEnd
#undef PyUnicodeEncodeError_SetReason
#undef PyUnicodeEncodeError_SetStart
#undef PyUnicodeTranslateError_Create
#undef PyUnicodeTranslateError_GetEnd
#undef PyUnicodeTranslateError_GetObject
#undef PyUnicodeTranslateError_GetReason
#undef PyUnicodeTranslateError_GetStart
#undef PyUnicodeTranslateError_SetEnd
#undef PyUnicodeTranslateError_SetReason
#undef PyUnicodeTranslateError_SetStart
#undef PyUnicode_Append
#undef PyUnicode_AppendAndDel
#undef PyUnicode_AsASCIIString
#undef PyUnicode_AsCharmapString
#undef PyUnicode_AsDecodedObject
#undef PyUnicode_AsDecodedUnicode
#undef PyUnicode_AsEncodedObject
#undef PyUnicode_AsEncodedString
#undef PyUnicode_AsEncodedUnicode
#undef PyUnicode_AsLatin1String
#undef PyUnicode_AsRawUnicodeEscapeString
#undef PyUnicode_AsUCS4
#undef PyUnicode_AsUCS4Copy
#undef PyUnicode_AsUTF16String
#undef PyUnicode_AsUTF32String
#undef PyUnicode_AsUTF8
#undef PyUnicode_AsUTF8AndSize
#undef PyUnicode_AsUTF8String
#undef PyUnicode_AsUnicode
#undef PyUnicode_AsUnicodeAndSize
#undef PyUnicode_AsUnicodeEscapeString
#undef PyUnicode_AsWideChar
#undef PyUnicode_AsWideCharString
#undef PyUnicode_BuildEncodingMap
#undef PyUnicode_Compare
#undef PyUnicode_CompareWithASCIIString
#undef PyUnicode_Concat
#undef PyUnicode_Contains
#undef PyUnicode_CopyCharacters
#undef PyUnicode_Count
#undef PyUnicode_Decode
#undef PyUnicode_DecodeASCII
#undef PyUnicode_DecodeCharmap
#undef PyUnicode_DecodeFSDefault
#undef PyUnicode_DecodeFSDefaultAndSize
#undef PyUnicode_DecodeLatin1
#undef PyUnicode_DecodeLocale
#undef PyUnicode_DecodeLocaleAndSize
#undef PyUnicode_DecodeRawUnicodeEscape
#undef PyUnicode_DecodeUTF16
#undef PyUnicode_DecodeUTF16Stateful
#undef PyUnicode_DecodeUTF32
#undef PyUnicode_DecodeUTF32Stateful
#undef PyUnicode_DecodeUTF7
#undef PyUnicode_DecodeUTF7Stateful
#undef PyUnicode_DecodeUTF8
#undef PyUnicode_DecodeUTF8Stateful
#undef PyUnicode_DecodeUnicodeEscape
#undef PyUnicode_Encode
#undef PyUnicode_EncodeASCII
#undef PyUnicode_EncodeCharmap
#undef PyUnicode_EncodeDecimal
#undef PyUnicode_EncodeFSDefault
#undef PyUnicode_EncodeLatin1
#undef PyUnicode_EncodeLocale
#undef PyUnicode_EncodeRawUnicodeEscape
#undef PyUnicode_EncodeUTF16
#undef PyUnicode_EncodeUTF32
#undef PyUnicode_EncodeUTF7
#undef PyUnicode_EncodeUTF8
#undef PyUnicode_EncodeUnicodeEscape
#undef PyUnicode_FSConverter
#undef PyUnicode_FSDecoder
#undef PyUnicode_Fill
#undef PyUnicode_Find
#undef PyUnicode_FindChar
#undef PyUnicode_Format
#undef PyUnicode_FromEncodedObject
#undef PyUnicode_FromFormat
#undef PyUnicode_FromFormatV
#undef PyUnicode_FromKindAndData
#undef PyUnicode_FromObject
#undef PyUnicode_FromOrdinal
#undef PyUnicode_FromString
#undef PyUnicode_FromStringAndSize
#undef PyUnicode_FromUnicode
#undef PyUnicode_FromWideChar
#undef PyUnicode_GetDefaultEncoding
#undef PyUnicode_GetLength
#undef PyUnicode_GetSize
#undef PyUnicode_InternFromString
#undef PyUnicode_InternImmortal
#undef PyUnicode_InternInPlace
#undef PyUnicode_IsIdentifier
#undef PyUnicode_Join
#undef PyUnicode_New
#undef PyUnicode_Partition
#undef PyUnicode_RPartition
#undef PyUnicode_RSplit
#undef PyUnicode_ReadChar
#undef PyUnicode_Replace
#undef PyUnicode_Resize
#undef PyUnicode_RichCompare
#undef PyUnicode_Split
#undef PyUnicode_Splitlines
#undef PyUnicode_Substring
#undef PyUnicode_Tailmatch
#undef PyUnicode_TransformDecimalToASCII
#undef PyUnicode_Translate
#undef PyUnicode_TranslateCharmap
#undef PyUnicode_WriteChar
#undef PyVectorcall_Call
#undef PyWeakref_GetObject
#undef PyWeakref_NewProxy
#undef PyWeakref_NewRef
#undef PyWideStringList_Append
#undef PyWideStringList_Insert
#undef PyWrapper_New
#undef Py_AddPendingCall
#undef Py_AtExit
#undef Py_BuildValue
#undef Py_BytesMain
#undef Py_CompileString
#undef Py_CompileStringExFlags
#undef Py_CompileStringObject
#undef Py_DecRef
#undef Py_DecodeLocale
#undef Py_EncodeLocale
#undef Py_EndInterpreter
#undef Py_EnterRecursiveCall
#undef Py_Exit
#undef Py_ExitStatusException
#undef Py_FatalError
#undef Py_FdIsInteractive
#undef Py_Finalize
#undef Py_FinalizeEx
#undef Py_FrozenMain
#undef Py_GenericAlias
#undef Py_GetArgcArgv
#undef Py_GetBuildInfo
#undef Py_GetCompiler
#undef Py_GetCopyright
#undef Py_GetExecPrefix
#undef Py_GetPath
#undef Py_GetPlatform
#undef Py_GetPrefix
#undef Py_GetProgramFullPath
#undef Py_GetProgramName
#undef Py_GetPythonHome
#undef Py_GetRecursionLimit
#undef Py_GetVersion
#undef Py_IncRef
#undef Py_Initialize
#undef Py_InitializeEx
#undef Py_InitializeFromConfig
#undef Py_Is
#undef Py_IsFalse
#undef Py_IsInitialized
#undef Py_IsNone
#undef Py_IsTrue
#undef Py_LeaveRecursiveCall
#undef Py_Main
#undef Py_MakePendingCalls
#undef Py_NewInterpreter
#undef Py_NewRef
#undef Py_PreInitialize
#undef Py_PreInitializeFromArgs
#undef Py_PreInitializeFromBytesArgs
#undef Py_ReprEnter
#undef Py_ReprLeave
#undef Py_RunMain
#undef Py_SetPath
#undef Py_SetProgramName
#undef Py_SetPythonHome
#undef Py_SetRecursionLimit
#undef Py_SetStandardStreamEncoding
#undef Py_UNICODE_strcat
#undef Py_UNICODE_strchr
#undef Py_UNICODE_strcmp
#undef Py_UNICODE_strcpy
#undef Py_UNICODE_strlen
#undef Py_UNICODE_strncmp
#undef Py_UNICODE_strncpy
#undef Py_UNICODE_strrchr
#undef Py_UniversalNewlineFgets
#undef Py_VaBuildValue
#undef Py_XNewRef
#undef Py_get_PyASCIIObject_length
#undef Py_get_PyASCIIObject_state_ascii
#undef Py_get_PyASCIIObject_state_compact
#undef Py_get_PyASCIIObject_state_kind
#undef Py_get_PyASCIIObject_state_ready
#undef Py_get_PyASCIIObject_wstr
#undef Py_get_PyAsyncMethods_am_aiter
#undef Py_get_PyAsyncMethods_am_anext
#undef Py_get_PyAsyncMethods_am_await
#undef Py_get_PyBufferProcs_bf_getbuffer
#undef Py_get_PyBufferProcs_bf_releasebuffer
#undef Py_get_PyByteArrayObject_ob_exports
#undef Py_get_PyByteArrayObject_ob_start
#undef Py_get_PyCFunctionObject_m_ml
#undef Py_get_PyCFunctionObject_m_module
#undef Py_get_PyCFunctionObject_m_self
#undef Py_get_PyCFunctionObject_m_weakreflist
#undef Py_get_PyCFunctionObject_vectorcall
#undef Py_get_PyCMethodObject_mm_class
#undef Py_get_PyCompactUnicodeObject_wstr_length
#undef Py_get_PyDescrObject_d_name
#undef Py_get_PyDescrObject_d_type
#undef Py_get_PyFrameObject_f_lineno
#undef Py_get_PyGetSetDef_closure
#undef Py_get_PyGetSetDef_doc
#undef Py_get_PyGetSetDef_get
#undef Py_get_PyGetSetDef_name
#undef Py_get_PyGetSetDef_set
#undef Py_get_PyInstanceMethodObject_func
#undef Py_get_PyListObject_ob_item
#undef Py_get_PyLongObject_ob_digit
#undef Py_get_PyMappingMethods_mp_ass_subscript
#undef Py_get_PyMappingMethods_mp_length
#undef Py_get_PyMappingMethods_mp_subscript
#undef Py_get_PyMethodDef_ml_doc
#undef Py_get_PyMethodDef_ml_flags
#undef Py_get_PyMethodDef_ml_meth
#undef Py_get_PyMethodDef_ml_name
#undef Py_get_PyMethodDescrObject_d_method
#undef Py_get_PyMethodObject_im_func
#undef Py_get_PyMethodObject_im_self
#undef Py_get_PyModuleDef_m_doc
#undef Py_get_PyModuleDef_m_methods
#undef Py_get_PyModuleDef_m_name
#undef Py_get_PyModuleDef_m_size
#undef Py_get_PyModuleObject_md_def
#undef Py_get_PyModuleObject_md_dict
#undef Py_get_PyModuleObject_md_state
#undef Py_get_PyNumberMethods_nb_absolute
#undef Py_get_PyNumberMethods_nb_add
#undef Py_get_PyNumberMethods_nb_and
#undef Py_get_PyNumberMethods_nb_bool
#undef Py_get_PyNumberMethods_nb_divmod
#undef Py_get_PyNumberMethods_nb_float
#undef Py_get_PyNumberMethods_nb_floor_divide
#undef Py_get_PyNumberMethods_nb_index
#undef Py_get_PyNumberMethods_nb_inplace_add
#undef Py_get_PyNumberMethods_nb_inplace_and
#undef Py_get_PyNumberMethods_nb_inplace_floor_divide
#undef Py_get_PyNumberMethods_nb_inplace_lshift
#undef Py_get_PyNumberMethods_nb_inplace_matrix_multiply
#undef Py_get_PyNumberMethods_nb_inplace_multiply
#undef Py_get_PyNumberMethods_nb_inplace_or
#undef Py_get_PyNumberMethods_nb_inplace_power
#undef Py_get_PyNumberMethods_nb_inplace_remainder
#undef Py_get_PyNumberMethods_nb_inplace_rshift
#undef Py_get_PyNumberMethods_nb_inplace_subtract
#undef Py_get_PyNumberMethods_nb_inplace_true_divide
#undef Py_get_PyNumberMethods_nb_inplace_xor
#undef Py_get_PyNumberMethods_nb_int
#undef Py_get_PyNumberMethods_nb_invert
#undef Py_get_PyNumberMethods_nb_lshift
#undef Py_get_PyNumberMethods_nb_matrix_multiply
#undef Py_get_PyNumberMethods_nb_multiply
#undef Py_get_PyNumberMethods_nb_negative
#undef Py_get_PyNumberMethods_nb_or
#undef Py_get_PyNumberMethods_nb_positive
#undef Py_get_PyNumberMethods_nb_power
#undef Py_get_PyNumberMethods_nb_remainder
#undef Py_get_PyNumberMethods_nb_rshift
#undef Py_get_PyNumberMethods_nb_subtract
#undef Py_get_PyNumberMethods_nb_true_divide
#undef Py_get_PyNumberMethods_nb_xor
#undef Py_get_PyObject_ob_refcnt
#undef Py_get_PyObject_ob_type
#undef Py_get_PySequenceMethods_sq_ass_item
#undef Py_get_PySequenceMethods_sq_concat
#undef Py_get_PySequenceMethods_sq_contains
#undef Py_get_PySequenceMethods_sq_inplace_concat
#undef Py_get_PySequenceMethods_sq_inplace_repeat
#undef Py_get_PySequenceMethods_sq_item
#undef Py_get_PySequenceMethods_sq_length
#undef Py_get_PySequenceMethods_sq_repeat
#undef Py_get_PySetObject_used
#undef Py_get_PySliceObject_start
#undef Py_get_PySliceObject_step
#undef Py_get_PySliceObject_stop
#undef Py_get_PyTupleObject_ob_item
#undef Py_get_PyTypeObject_tp_alloc
#undef Py_get_PyTypeObject_tp_as_async
#undef Py_get_PyTypeObject_tp_as_buffer
#undef Py_get_PyTypeObject_tp_as_mapping
#undef Py_get_PyTypeObject_tp_as_number
#undef Py_get_PyTypeObject_tp_as_sequence
#undef Py_get_PyTypeObject_tp_base
#undef Py_get_PyTypeObject_tp_bases
#undef Py_get_PyTypeObject_tp_basicsize
#undef Py_get_PyTypeObject_tp_cache
#undef Py_get_PyTypeObject_tp_call
#undef Py_get_PyTypeObject_tp_clear
#undef Py_get_PyTypeObject_tp_dealloc
#undef Py_get_PyTypeObject_tp_del
#undef Py_get_PyTypeObject_tp_descr_get
#undef Py_get_PyTypeObject_tp_descr_set
#undef Py_get_PyTypeObject_tp_dict
#undef Py_get_PyTypeObject_tp_dictoffset
#undef Py_get_PyTypeObject_tp_doc
#undef Py_get_PyTypeObject_tp_finalize
#undef Py_get_PyTypeObject_tp_flags
#undef Py_get_PyTypeObject_tp_free
#undef Py_get_PyTypeObject_tp_getattr
#undef Py_get_PyTypeObject_tp_getattro
#undef Py_get_PyTypeObject_tp_getset
#undef Py_get_PyTypeObject_tp_hash
#undef Py_get_PyTypeObject_tp_init
#undef Py_get_PyTypeObject_tp_is_gc
#undef Py_get_PyTypeObject_tp_itemsize
#undef Py_get_PyTypeObject_tp_iter
#undef Py_get_PyTypeObject_tp_iternext
#undef Py_get_PyTypeObject_tp_members
#undef Py_get_PyTypeObject_tp_methods
#undef Py_get_PyTypeObject_tp_mro
#undef Py_get_PyTypeObject_tp_name
#undef Py_get_PyTypeObject_tp_new
#undef Py_get_PyTypeObject_tp_repr
#undef Py_get_PyTypeObject_tp_richcompare
#undef Py_get_PyTypeObject_tp_setattr
#undef Py_get_PyTypeObject_tp_setattro
#undef Py_get_PyTypeObject_tp_str
#undef Py_get_PyTypeObject_tp_subclasses
#undef Py_get_PyTypeObject_tp_traverse
#undef Py_get_PyTypeObject_tp_vectorcall
#undef Py_get_PyTypeObject_tp_vectorcall_offset
#undef Py_get_PyTypeObject_tp_version_tag
#undef Py_get_PyTypeObject_tp_weaklist
#undef Py_get_PyTypeObject_tp_weaklistoffset
#undef Py_get_PyUnicodeObject_data
#undef Py_get_PyVarObject_ob_size
#undef Py_get_dummy
#undef Py_get_mmap_object_data
#undef Py_set_PyByteArrayObject_ob_exports
#undef Py_set_PyFrameObject_f_lineno
#undef Py_set_PyModuleObject_md_def
#undef Py_set_PyModuleObject_md_state
#undef Py_set_PyObject_ob_refcnt
#undef Py_set_PyTypeObject_tp_alloc
#undef Py_set_PyTypeObject_tp_as_buffer
#undef Py_set_PyTypeObject_tp_base
#undef Py_set_PyTypeObject_tp_bases
#undef Py_set_PyTypeObject_tp_basicsize
#undef Py_set_PyTypeObject_tp_clear
#undef Py_set_PyTypeObject_tp_dealloc
#undef Py_set_PyTypeObject_tp_dict
#undef Py_set_PyTypeObject_tp_dictoffset
#undef Py_set_PyTypeObject_tp_finalize
#undef Py_set_PyTypeObject_tp_flags
#undef Py_set_PyTypeObject_tp_free
#undef Py_set_PyTypeObject_tp_getattr
#undef Py_set_PyTypeObject_tp_getattro
#undef Py_set_PyTypeObject_tp_itemsize
#undef Py_set_PyTypeObject_tp_iter
#undef Py_set_PyTypeObject_tp_iternext
#undef Py_set_PyTypeObject_tp_mro
#undef Py_set_PyTypeObject_tp_new
#undef Py_set_PyTypeObject_tp_setattr
#undef Py_set_PyTypeObject_tp_setattro
#undef Py_set_PyTypeObject_tp_subclasses
#undef Py_set_PyTypeObject_tp_traverse
#undef Py_set_PyTypeObject_tp_vectorcall_offset
#undef Py_set_PyTypeObject_tp_weaklistoffset
#undef Py_set_PyVarObject_ob_size
#undef _PyASCIIObject_LENGTH
#undef _PyASCIIObject_STATE_ASCII
#undef _PyASCIIObject_STATE_COMPACT
#undef _PyASCIIObject_STATE_KIND
#undef _PyASCIIObject_STATE_READY
#undef _PyASCIIObject_WSTR
#undef _PyArg_BadArgument
#undef _PyArg_CheckPositional
#undef _PyArg_Fini
#undef _PyArg_NoKeywords
#undef _PyArg_NoKwnames
#undef _PyArg_NoPositional
#undef _PyArg_ParseStack
#undef _PyArg_ParseStackAndKeywords
#undef _PyArg_ParseStackAndKeywords_SizeT
#undef _PyArg_ParseStack_SizeT
#undef _PyArg_ParseTupleAndKeywordsFast
#undef _PyArg_ParseTupleAndKeywordsFast_SizeT
#undef _PyArg_ParseTupleAndKeywords_SizeT
#undef _PyArg_ParseTuple_SizeT
#undef _PyArg_Parse_SizeT
#undef _PyArg_UnpackKeywords
#undef _PyArg_UnpackStack
#undef _PyArg_VaParseTupleAndKeywordsFast
#undef _PyArg_VaParseTupleAndKeywordsFast_SizeT
#undef _PyArg_VaParseTupleAndKeywords_SizeT
#undef _PyArg_VaParse_SizeT
#undef _PyAsyncGenValueWrapperNew
#undef _PyByteArray_Start
#undef _PyBytesWriter_Alloc
#undef _PyBytesWriter_Dealloc
#undef _PyBytesWriter_Finish
#undef _PyBytesWriter_Init
#undef _PyBytesWriter_Prepare
#undef _PyBytesWriter_Resize
#undef _PyBytesWriter_WriteBytes
#undef _PyBytes_DecodeEscape
#undef _PyBytes_FormatEx
#undef _PyBytes_FromHex
#undef _PyBytes_Join
#undef _PyBytes_Resize
#undef _PyCFunction_DebugMallocStats
#undef _PyCFunction_FastCallDict
#undef _PyCode_CheckLineNumber
#undef _PyCode_ConstantKey
#undef _PyCode_GetExtra
#undef _PyCode_InitAddressRange
#undef _PyCode_SetExtra
#undef _PyCodecInfo_GetIncrementalDecoder
#undef _PyCodecInfo_GetIncrementalEncoder
#undef _PyCodec_DecodeText
#undef _PyCodec_EncodeText
#undef _PyCodec_Forget
#undef _PyCodec_Lookup
#undef _PyCodec_LookupTextEncoding
#undef _PyComplex_FormatAdvancedWriter
#undef _PyContext_NewHamtForTests
#undef _PyCoro_GetAwaitableIter
#undef _PyCrossInterpreterData_Lookup
#undef _PyCrossInterpreterData_NewObject
#undef _PyCrossInterpreterData_RegisterClass
#undef _PyCrossInterpreterData_Release
#undef _PyDebugAllocatorStats
#undef _PyDictView_Intersect
#undef _PyDictView_New
#undef _PyDict_ContainsId
#undef _PyDict_Contains_KnownHash
#undef _PyDict_DebugMallocStats
#undef _PyDict_DelItemId
#undef _PyDict_DelItemIf
#undef _PyDict_DelItem_KnownHash
#undef _PyDict_FromKeys
#undef _PyDict_GetItemHint
#undef _PyDict_GetItemId
#undef _PyDict_GetItemIdWithError
#undef _PyDict_GetItemStringWithError
#undef _PyDict_GetItem_KnownHash
#undef _PyDict_HasOnlyStringKeys
#undef _PyDict_KeysSize
#undef _PyDict_LoadGlobal
#undef _PyDict_MaybeUntrack
#undef _PyDict_MergeEx
#undef _PyDict_NewKeysForClass
#undef _PyDict_NewPresized
#undef _PyDict_Next
#undef _PyDict_Pop
#undef _PyDict_Pop_KnownHash
#undef _PyDict_SetItemId
#undef _PyDict_SetItem_KnownHash
#undef _PyDict_SizeOf
#undef _PyErr_BadInternalCall
#undef _PyErr_ChainExceptions
#undef _PyErr_CheckSignals
#undef _PyErr_CreateAndSetException
#undef _PyErr_FormatFromCause
#undef _PyErr_GetExcInfo
#undef _PyErr_GetTopmostException
#undef _PyErr_ProgramDecodedTextObject
#undef _PyErr_SetKeyError
#undef _PyErr_TrySetFromCause
#undef _PyErr_WarnUnawaitedCoroutine
#undef _PyErr_WriteUnraisableMsg
#undef _PyEval_CallTracing
#undef _PyEval_EvalFrameDefault
#undef _PyEval_GetAsyncGenFinalizer
#undef _PyEval_GetAsyncGenFirstiter
#undef _PyEval_GetBuiltinId
#undef _PyEval_GetCoroutineOriginTrackingDepth
#undef _PyEval_GetSwitchInterval
#undef _PyEval_RequestCodeExtraIndex
#undef _PyEval_SetAsyncGenFinalizer
#undef _PyEval_SetAsyncGenFirstiter
#undef _PyEval_SetCoroutineOriginTrackingDepth
#undef _PyEval_SetProfile
#undef _PyEval_SetSwitchInterval
#undef _PyEval_SetTrace
#undef _PyEval_SliceIndex
#undef _PyEval_SliceIndexNotNone
#undef _PyFloat_DebugMallocStats
#undef _PyFloat_FormatAdvancedWriter
#undef _PyFloat_Pack2
#undef _PyFloat_Pack4
#undef _PyFloat_Pack8
#undef _PyFloat_Unpack2
#undef _PyFloat_Unpack4
#undef _PyFloat_Unpack8
#undef _PyFrame_DebugMallocStats
#undef _PyFrame_New_NoTrack
#undef _PyFrame_SetLineNumber
#undef _PyFunction_FastCallDict
#undef _PyFunction_Vectorcall
#undef _PyGILState_GetInterpreterStateUnsafe
#undef _PyGen_FetchStopIterationValue
#undef _PyGen_Finalize
#undef _PyGen_Send
#undef _PyGen_SetStopIterationValue
#undef _PyGen_yf
#undef _PyImport_AcquireLock
#undef _PyImport_FindBuiltin
#undef _PyImport_FindExtensionObject
#undef _PyImport_FindExtensionObjectEx
#undef _PyImport_FixupBuiltin
#undef _PyImport_FixupExtensionObject
#undef _PyImport_GetModuleAttr
#undef _PyImport_GetModuleAttrString
#undef _PyImport_GetModuleId
#undef _PyImport_IsInitialized
#undef _PyImport_ReInitLock
#undef _PyImport_ReleaseLock
#undef _PyImport_SetModule
#undef _PyImport_SetModuleString
#undef _PyInterpreterState_Get
#undef _PyInterpreterState_GetConfig
#undef _PyInterpreterState_GetConfigCopy
#undef _PyInterpreterState_GetEvalFrameFunc
#undef _PyInterpreterState_GetMainModule
#undef _PyInterpreterState_RequireIDRef
#undef _PyInterpreterState_RequiresIDRef
#undef _PyInterpreterState_SetConfig
#undef _PyInterpreterState_SetEvalFrameFunc
#undef _PyList_DebugMallocStats
#undef _PyList_Extend
#undef _PyLong_AsByteArray
#undef _PyLong_AsInt
#undef _PyLong_AsTime_t
#undef _PyLong_Copy
#undef _PyLong_DivmodNear
#undef _PyLong_FileDescriptor_Converter
#undef _PyLong_Format
#undef _PyLong_FormatAdvancedWriter
#undef _PyLong_FormatBytesWriter
#undef _PyLong_FormatWriter
#undef _PyLong_Frexp
#undef _PyLong_FromByteArray
#undef _PyLong_FromBytes
#undef _PyLong_FromTime_t
#undef _PyLong_GCD
#undef _PyLong_Lshift
#undef _PyLong_New
#undef _PyLong_NumBits
#undef _PyLong_Rshift
#undef _PyLong_Sign
#undef _PyLong_Size_t_Converter
#undef _PyLong_UnsignedInt_Converter
#undef _PyLong_UnsignedLongLong_Converter
#undef _PyLong_UnsignedLong_Converter
#undef _PyLong_UnsignedShort_Converter
#undef _PyMem_GetCurrentAllocatorName
#undef _PyMem_RawStrdup
#undef _PyMem_RawWcsdup
#undef _PyMem_Strdup
#undef _PyMemoryView_GetBuffer
#undef _PyMethodDef_RawFastCallDict
#undef _PyMethodDef_RawFastCallKeywords
#undef _PyMethod_DebugMallocStats
#undef _PyModuleSpec_IsInitializing
#undef _PyModule_Clear
#undef _PyModule_ClearDict
#undef _PyModule_CreateInitialized
#undef _PyModule_GetDef
#undef _PyModule_GetDict
#undef _PyModule_GetState
#undef _PyNamespace_New
#undef _PyNumber_Index
#undef _PyOS_IsMainThread
#undef _PyOS_URandom
#undef _PyOS_URandomNonblock
#undef _PyObjectDict_SetItem
#undef _PyObject_AssertFailed
#undef _PyObject_Call1
#undef _PyObject_CallFunction_SizeT
#undef _PyObject_CallMethod1
#undef _PyObject_CallMethodId
#undef _PyObject_CallMethodIdObjArgs
#undef _PyObject_CallMethodId_SizeT
#undef _PyObject_CallMethod_SizeT
#undef _PyObject_Call_Prepend
#undef _PyObject_CheckConsistency
#undef _PyObject_CheckCrossInterpreterData
#undef _PyObject_DebugMallocStats
#undef _PyObject_DebugTypeStats
#undef _PyObject_Dump
#undef _PyObject_FastCallDict
#undef _PyObject_FastCall_Prepend
#undef _PyObject_FunctionStr
#undef _PyObject_GC_Calloc
#undef _PyObject_GC_Malloc
#undef _PyObject_GC_New
#undef _PyObject_GC_NewVar
#undef _PyObject_GC_Resize
#undef _PyObject_GenericGetAttrWithDict
#undef _PyObject_GenericSetAttrWithDict
#undef _PyObject_GetAttrId
#undef _PyObject_GetCrossInterpreterData
#undef _PyObject_GetDictPtr
#undef _PyObject_GetMethod
#undef _PyObject_HasAttrId
#undef _PyObject_HasLen
#undef _PyObject_IsAbstract
#undef _PyObject_IsFreed
#undef _PyObject_LookupAttr
#undef _PyObject_LookupAttrId
#undef _PyObject_LookupSpecial
#undef _PyObject_MakeTpCall
#undef _PyObject_New
#undef _PyObject_NewVar
#undef _PyObject_NextNotImplemented
#undef _PyObject_RealIsInstance
#undef _PyObject_RealIsSubclass
#undef _PyObject_SetAttrId
#undef _PyRun_AnyFileObject
#undef _PyRun_InteractiveLoopObject
#undef _PyRun_SimpleFileObject
#undef _PySequence_BytesToCharpArray
#undef _PySequence_Fast_ITEMS
#undef _PySequence_ITEM
#undef _PySequence_IterSearch
#undef _PySet_NextEntry
#undef _PySet_Update
#undef _PySignal_AfterFork
#undef _PySlice_FromIndices
#undef _PySlice_GetLongIndices
#undef _PyStack_AsDict
#undef _PyStack_UnpackDict
#undef _PyState_AddModule
#undef _PySys_GetObjectId
#undef _PySys_GetSizeOf
#undef _PySys_SetObjectId
#undef _PyThreadState_GetDict
#undef _PyThreadState_Prealloc
#undef _PyThreadState_UncheckedGet
#undef _PyThread_CurrentExceptions
#undef _PyThread_CurrentFrames
#undef _PyThread_at_fork_reinit
#undef _PyTime_AsMicroseconds
#undef _PyTime_AsMilliseconds
#undef _PyTime_AsNanosecondsObject
#undef _PyTime_AsSecondsDouble
#undef _PyTime_AsTimespec
#undef _PyTime_AsTimeval
#undef _PyTime_AsTimevalTime_t
#undef _PyTime_AsTimeval_noraise
#undef _PyTime_FromMillisecondsObject
#undef _PyTime_FromNanoseconds
#undef _PyTime_FromNanosecondsObject
#undef _PyTime_FromSeconds
#undef _PyTime_FromSecondsObject
#undef _PyTime_FromTimespec
#undef _PyTime_FromTimeval
#undef _PyTime_GetMonotonicClock
#undef _PyTime_GetMonotonicClockWithInfo
#undef _PyTime_GetPerfCounter
#undef _PyTime_GetPerfCounterWithInfo
#undef _PyTime_GetSystemClock
#undef _PyTime_GetSystemClockWithInfo
#undef _PyTime_MulDiv
#undef _PyTime_ObjectToTime_t
#undef _PyTime_ObjectToTimespec
#undef _PyTime_ObjectToTimeval
#undef _PyTime_gmtime
#undef _PyTime_localtime
#undef _PyTraceMalloc_GetTraceback
#undef _PyTraceMalloc_NewReference
#undef _PyTraceback_Add
#undef _PyTrash_begin
#undef _PyTrash_cond
#undef _PyTrash_deposit_object
#undef _PyTrash_destroy_chain
#undef _PyTrash_end
#undef _PyTrash_thread_deposit_object
#undef _PyTrash_thread_destroy_chain
#undef _PyTruffleBytes_Resize
#undef _PyTruffleErr_Warn
#undef _PyTruffleEval_EvalCodeEx
#undef _PyTruffleModule_CreateInitialized_PyModule_New
#undef _PyTruffleModule_GetAndIncMaxModuleNumber
#undef _PyTruffleObject_MakeTpCall
#undef _PyTruffleSet_NextEntry
#undef _PyTruffle_HashBytes
#undef _PyTruffle_Trace_Free
#undef _PyTuple_DebugMallocStats
#undef _PyTuple_MaybeUntrack
#undef _PyTuple_Resize
#undef _PyType_CalculateMetaclass
#undef _PyType_GetDocFromInternalDoc
#undef _PyType_GetModuleByDef
#undef _PyType_GetTextSignatureFromInternalDoc
#undef _PyType_Lookup
#undef _PyType_LookupId
#undef _PyType_Name
#undef _PyUnicodeObject_DATA
#undef _PyUnicodeTranslateError_Create
#undef _PyUnicodeWriter_Dealloc
#undef _PyUnicodeWriter_Finish
#undef _PyUnicodeWriter_Init
#undef _PyUnicodeWriter_PrepareInternal
#undef _PyUnicodeWriter_PrepareKindInternal
#undef _PyUnicodeWriter_WriteASCIIString
#undef _PyUnicodeWriter_WriteChar
#undef _PyUnicodeWriter_WriteLatin1String
#undef _PyUnicodeWriter_WriteStr
#undef _PyUnicodeWriter_WriteSubstring
#undef _PyUnicode_AsASCIIString
#undef _PyUnicode_AsKind
#undef _PyUnicode_AsLatin1String
#undef _PyUnicode_AsUTF8String
#undef _PyUnicode_AsUnicode
#undef _PyUnicode_CheckConsistency
#undef _PyUnicode_Copy
#undef _PyUnicode_DecodeRawUnicodeEscapeStateful
#undef _PyUnicode_DecodeUnicodeEscapeInternal
#undef _PyUnicode_DecodeUnicodeEscapeStateful
#undef _PyUnicode_EQ
#undef _PyUnicode_EncodeCharmap
#undef _PyUnicode_EncodeUTF16
#undef _PyUnicode_EncodeUTF32
#undef _PyUnicode_EncodeUTF7
#undef _PyUnicode_EqualToASCIIId
#undef _PyUnicode_EqualToASCIIString
#undef _PyUnicode_FastCopyCharacters
#undef _PyUnicode_FastFill
#undef _PyUnicode_FindMaxChar
#undef _PyUnicode_FormatAdvancedWriter
#undef _PyUnicode_FormatLong
#undef _PyUnicode_FromASCII
#undef _PyUnicode_FromId
#undef _PyUnicode_InsertThousandsGrouping
#undef _PyUnicode_IsAlpha
#undef _PyUnicode_IsCaseIgnorable
#undef _PyUnicode_IsCased
#undef _PyUnicode_IsDecimalDigit
#undef _PyUnicode_IsDigit
#undef _PyUnicode_IsLinebreak
#undef _PyUnicode_IsLowercase
#undef _PyUnicode_IsNumeric
#undef _PyUnicode_IsPrintable
#undef _PyUnicode_IsTitlecase
#undef _PyUnicode_IsUppercase
#undef _PyUnicode_IsWhitespace
#undef _PyUnicode_IsXidContinue
#undef _PyUnicode_IsXidStart
#undef _PyUnicode_JoinArray
#undef _PyUnicode_Ready
#undef _PyUnicode_ScanIdentifier
#undef _PyUnicode_ToDecimalDigit
#undef _PyUnicode_ToDigit
#undef _PyUnicode_ToFoldedFull
#undef _PyUnicode_ToLowerFull
#undef _PyUnicode_ToLowercase
#undef _PyUnicode_ToNumeric
#undef _PyUnicode_ToTitleFull
#undef _PyUnicode_ToTitlecase
#undef _PyUnicode_ToUpperFull
#undef _PyUnicode_ToUppercase
#undef _PyUnicode_TransformDecimalAndSpaceToASCII
#undef _PyUnicode_WideCharString_Converter
#undef _PyUnicode_WideCharString_Opt_Converter
#undef _PyUnicode_XStrip
#undef _PyUnicode_get_wstr_length
#undef _PyWarnings_Init
#undef _PyWeakref_ClearRef
#undef _PyWeakref_GetWeakrefCount
#undef _Py_BreakPoint
#undef _Py_BuildValue_SizeT
#undef _Py_CheckFunctionResult
#undef _Py_CheckRecursiveCall
#undef _Py_CoerceLegacyLocale
#undef _Py_Dealloc
#undef _Py_DecRef
#undef _Py_DecodeLocaleEx
#undef _Py_DisplaySourceLine
#undef _Py_EncodeLocaleEx
#undef _Py_EncodeLocaleRaw
#undef _Py_FatalErrorFormat
#undef _Py_FatalErrorFunc
#undef _Py_FdIsInteractive
#undef _Py_FreeCharPArray
#undef _Py_GetAllocatedBlocks
#undef _Py_GetConfig
#undef _Py_GetErrorHandler
#undef _Py_HashBytes
#undef _Py_HashDouble
#undef _Py_HashPointer
#undef _Py_HashPointerRaw
#undef _Py_IncRef
#undef _Py_InitializeMain
#undef _Py_IsCoreInitialized
#undef _Py_IsFinalizing
#undef _Py_LegacyLocaleDetected
#undef _Py_Mangle
#undef _Py_NewInterpreter
#undef _Py_NewReference
#undef _Py_REFCNT
#undef _Py_RestoreSignals
#undef _Py_SET_REFCNT
#undef _Py_SET_SIZE
#undef _Py_SET_TYPE
#undef _Py_SIZE
#undef _Py_SetLocaleFromEnv
#undef _Py_SetProgramFullPath
#undef _Py_SourceAsString
#undef _Py_TYPE
#undef _Py_VaBuildStack
#undef _Py_VaBuildStack_SizeT
#undef _Py_VaBuildValue_SizeT
#undef _Py_abspath
#undef _Py_add_one_to_index_C
#undef _Py_add_one_to_index_F
#undef _Py_c_abs
#undef _Py_c_diff
#undef _Py_c_neg
#undef _Py_c_pow
#undef _Py_c_prod
#undef _Py_c_quot
#undef _Py_c_sum
#undef _Py_convert_optional_to_ssize_t
#undef _Py_device_encoding
#undef _Py_dg_dtoa
#undef _Py_dg_freedtoa
#undef _Py_dg_infinity
#undef _Py_dg_stdnan
#undef _Py_dg_strtod
#undef _Py_dup
#undef _Py_fopen
#undef _Py_fopen_obj
#undef _Py_fstat
#undef _Py_fstat_noraise
#undef _Py_get_blocking
#undef _Py_get_inheritable
#undef _Py_gitidentifier
#undef _Py_gitversion
#undef _Py_isabs
#undef _Py_open
#undef _Py_open_noraise
#undef _Py_parse_inf_or_nan
#undef _Py_read
#undef _Py_set_blocking
#undef _Py_set_inheritable
#undef _Py_set_inheritable_async_safe
#undef _Py_stat
#undef _Py_strhex
#undef _Py_strhex_bytes
#undef _Py_strhex_bytes_with_sep
#undef _Py_strhex_with_sep
#undef _Py_string_to_number_with_underscores
#undef _Py_wfopen
#undef _Py_wgetcwd
#undef _Py_wreadlink
#undef _Py_wrealpath
#undef _Py_write
#undef _Py_write_noraise
PyAPI_FUNC(int) PyAIter_Check(PyObject* a) {
    unimplemented("PyAIter_Check"); exit(-1);
}
FIRST_STATS_CONTAINER(PyArg_VaParse)
int (*__target__PyArg_VaParse)(PyObject*, const char*, va_list) = NULL;
PyAPI_FUNC(int) PyArg_VaParse(PyObject* a, const char* b, va_list c) {
    LOG("0x%lx '%s'(0x%lx) 0x%lx", (unsigned long) a, b?b:"<null>", (unsigned long) b, (unsigned long) c);
    STATS_BEFORE(PyArg_VaParse)
    int result = (int) __target__PyArg_VaParse(a, b, c);
    STATS_AFTER(PyArg_VaParse)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyArg_VaParseTupleAndKeywords, PyArg_VaParse)
int (*__target__PyArg_VaParseTupleAndKeywords)(PyObject*, PyObject*, const char*, char**, va_list) = NULL;
PyAPI_FUNC(int) PyArg_VaParseTupleAndKeywords(PyObject* a, PyObject* b, const char* c, char** d, va_list e) {
    LOG("0x%lx 0x%lx '%s'(0x%lx) 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, c?c:"<null>", (unsigned long) c, (unsigned long) d, (unsigned long) e);
    STATS_BEFORE(PyArg_VaParseTupleAndKeywords)
    int result = (int) __target__PyArg_VaParseTupleAndKeywords(a, b, c, d, e);
    STATS_AFTER(PyArg_VaParseTupleAndKeywords)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(int) PyArg_ValidateKeywordArguments(PyObject* a) {
    unimplemented("PyArg_ValidateKeywordArguments"); exit(-1);
}
PyAPI_FUNC(PyObject*) PyAsyncGen_New(PyFrameObject* a, PyObject* b, PyObject* c) {
    unimplemented("PyAsyncGen_New"); exit(-1);
}
PyAPI_FUNC(void) PyBuffer_FillContiguousStrides(int a, Py_ssize_t* b, Py_ssize_t* c, int d, char e) {
    unimplemented("PyBuffer_FillContiguousStrides"); exit(-1);
}
STATS_CONTAINER(PyBuffer_FillInfo, PyArg_VaParseTupleAndKeywords)
int (*__target__PyBuffer_FillInfo)(Py_buffer*, PyObject*, void*, Py_ssize_t, int, int) = NULL;
PyAPI_FUNC(int) PyBuffer_FillInfo(Py_buffer* a, PyObject* b, void* c, Py_ssize_t d, int e, int f) {
    LOG("0x%lx 0x%lx 0x%lx 0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c, (unsigned long) d, (unsigned long) e, (unsigned long) f);
    STATS_BEFORE(PyBuffer_FillInfo)
    int result = (int) __target__PyBuffer_FillInfo(a, b, c, d, e, f);
    STATS_AFTER(PyBuffer_FillInfo)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(int) PyBuffer_FromContiguous(Py_buffer* a, void* b, Py_ssize_t c, char d) {
    unimplemented("PyBuffer_FromContiguous"); exit(-1);
}
PyAPI_FUNC(void*) PyBuffer_GetPointer(Py_buffer* a, Py_ssize_t* b) {
    unimplemented("PyBuffer_GetPointer"); exit(-1);
}
STATS_CONTAINER(PyBuffer_IsContiguous, PyBuffer_FillInfo)
int (*__target__PyBuffer_IsContiguous)(const Py_buffer*, char) = NULL;
PyAPI_FUNC(int) PyBuffer_IsContiguous(const Py_buffer* a, char b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(PyBuffer_IsContiguous)
    int result = (int) __target__PyBuffer_IsContiguous(a, b);
    STATS_AFTER(PyBuffer_IsContiguous)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyBuffer_Release, PyBuffer_IsContiguous)
void (*__target__PyBuffer_Release)(Py_buffer*) = NULL;
PyAPI_FUNC(void) PyBuffer_Release(Py_buffer* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyBuffer_Release)
    __target__PyBuffer_Release(a);
    STATS_AFTER(PyBuffer_Release)
    LOG_AFTER_VOID
}
PyAPI_FUNC(Py_ssize_t) PyBuffer_SizeFromFormat(const char* a) {
    unimplemented("PyBuffer_SizeFromFormat"); exit(-1);
}
PyAPI_FUNC(int) PyBuffer_ToContiguous(void* a, Py_buffer* b, Py_ssize_t c, char d) {
    unimplemented("PyBuffer_ToContiguous"); exit(-1);
}
PyAPI_FUNC(char*) PyByteArray_AsString(PyObject* a) {
    unimplemented("PyByteArray_AsString"); exit(-1);
}
PyAPI_FUNC(PyObject*) PyByteArray_Concat(PyObject* a, PyObject* b) {
    unimplemented("PyByteArray_Concat"); exit(-1);
}
PyAPI_FUNC(PyObject*) PyByteArray_FromObject(PyObject* a) {
    unimplemented("PyByteArray_FromObject"); exit(-1);
}
STATS_CONTAINER(PyByteArray_FromStringAndSize, PyBuffer_Release)
PyObject* (*__target__PyByteArray_FromStringAndSize)(const char*, Py_ssize_t) = NULL;
PyAPI_FUNC(PyObject*) PyByteArray_FromStringAndSize(const char* a, Py_ssize_t b) {
    LOG("'%s'(0x%lx) 0x%lx", a?a:"<null>", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(PyByteArray_FromStringAndSize)
    PyObject* result = (PyObject*) __target__PyByteArray_FromStringAndSize(a, b);
    STATS_AFTER(PyByteArray_FromStringAndSize)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyByteArray_Resize, PyByteArray_FromStringAndSize)
int (*__target__PyByteArray_Resize)(PyObject*, Py_ssize_t) = NULL;
PyAPI_FUNC(int) PyByteArray_Resize(PyObject* a, Py_ssize_t b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(PyByteArray_Resize)
    int result = (int) __target__PyByteArray_Resize(a, b);
    STATS_AFTER(PyByteArray_Resize)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(Py_ssize_t) PyByteArray_Size(PyObject* a) {
    unimplemented("PyByteArray_Size"); exit(-1);
}
STATS_CONTAINER(PyBytes_AsString, PyByteArray_Resize)
char* (*__target__PyBytes_AsString)(PyObject*) = NULL;
PyAPI_FUNC(char*) PyBytes_AsString(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyBytes_AsString)
    char* result = (char*) __target__PyBytes_AsString(a);
    STATS_AFTER(PyBytes_AsString)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyBytes_AsStringAndSize, PyBytes_AsString)
int (*__target__PyBytes_AsStringAndSize)(PyObject*, char**, Py_ssize_t*) = NULL;
PyAPI_FUNC(int) PyBytes_AsStringAndSize(PyObject* a, char** b, Py_ssize_t* c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    STATS_BEFORE(PyBytes_AsStringAndSize)
    int result = (int) __target__PyBytes_AsStringAndSize(a, b, c);
    STATS_AFTER(PyBytes_AsStringAndSize)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyBytes_Concat, PyBytes_AsStringAndSize)
void (*__target__PyBytes_Concat)(PyObject**, PyObject*) = NULL;
PyAPI_FUNC(void) PyBytes_Concat(PyObject** a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(PyBytes_Concat)
    __target__PyBytes_Concat(a, b);
    STATS_AFTER(PyBytes_Concat)
    LOG_AFTER_VOID
}
STATS_CONTAINER(PyBytes_ConcatAndDel, PyBytes_Concat)
void (*__target__PyBytes_ConcatAndDel)(PyObject**, PyObject*) = NULL;
PyAPI_FUNC(void) PyBytes_ConcatAndDel(PyObject** a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(PyBytes_ConcatAndDel)
    __target__PyBytes_ConcatAndDel(a, b);
    STATS_AFTER(PyBytes_ConcatAndDel)
    LOG_AFTER_VOID
}
PyAPI_FUNC(PyObject*) PyBytes_DecodeEscape(const char* a, Py_ssize_t b, const char* c, Py_ssize_t d, const char* e) {
    unimplemented("PyBytes_DecodeEscape"); exit(-1);
}
STATS_CONTAINER(PyBytes_FromFormatV, PyBytes_ConcatAndDel)
PyObject* (*__target__PyBytes_FromFormatV)(const char*, va_list) = NULL;
PyAPI_FUNC(PyObject*) PyBytes_FromFormatV(const char* a, va_list b) {
    LOG("'%s'(0x%lx) 0x%lx", a?a:"<null>", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(PyBytes_FromFormatV)
    PyObject* result = (PyObject*) __target__PyBytes_FromFormatV(a, b);
    STATS_AFTER(PyBytes_FromFormatV)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyBytes_FromObject, PyBytes_FromFormatV)
PyObject* (*__target__PyBytes_FromObject)(PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyBytes_FromObject(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyBytes_FromObject)
    PyObject* result = (PyObject*) __target__PyBytes_FromObject(a);
    STATS_AFTER(PyBytes_FromObject)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyBytes_FromString, PyBytes_FromObject)
PyObject* (*__target__PyBytes_FromString)(const char*) = NULL;
PyAPI_FUNC(PyObject*) PyBytes_FromString(const char* a) {
    LOG("'%s'(0x%lx)", a?a:"<null>", (unsigned long) a);
    STATS_BEFORE(PyBytes_FromString)
    PyObject* result = (PyObject*) __target__PyBytes_FromString(a);
    STATS_AFTER(PyBytes_FromString)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyBytes_FromStringAndSize, PyBytes_FromString)
PyObject* (*__target__PyBytes_FromStringAndSize)(const char*, Py_ssize_t) = NULL;
PyAPI_FUNC(PyObject*) PyBytes_FromStringAndSize(const char* a, Py_ssize_t b) {
    LOG("'%s'(0x%lx) 0x%lx", a?a:"<null>", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(PyBytes_FromStringAndSize)
    PyObject* result = (PyObject*) __target__PyBytes_FromStringAndSize(a, b);
    STATS_AFTER(PyBytes_FromStringAndSize)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(PyObject*) PyBytes_Repr(PyObject* a, int b) {
    unimplemented("PyBytes_Repr"); exit(-1);
}
STATS_CONTAINER(PyBytes_Size, PyBytes_FromStringAndSize)
Py_ssize_t (*__target__PyBytes_Size)(PyObject*) = NULL;
PyAPI_FUNC(Py_ssize_t) PyBytes_Size(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyBytes_Size)
    Py_ssize_t result = (Py_ssize_t) __target__PyBytes_Size(a);
    STATS_AFTER(PyBytes_Size)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(PyObject*) PyCFunction_Call(PyObject* a, PyObject* b, PyObject* c) {
    unimplemented("PyCFunction_Call"); exit(-1);
}
STATS_CONTAINER(PyCFunction_GetClass, PyBytes_Size)
PyTypeObject* (*__target__PyCFunction_GetClass)(PyObject*) = NULL;
PyAPI_FUNC(PyTypeObject*) PyCFunction_GetClass(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyCFunction_GetClass)
    PyTypeObject* result = (PyTypeObject*) __target__PyCFunction_GetClass(a);
    STATS_AFTER(PyCFunction_GetClass)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyCFunction_GetFlags, PyCFunction_GetClass)
int (*__target__PyCFunction_GetFlags)(PyObject*) = NULL;
PyAPI_FUNC(int) PyCFunction_GetFlags(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyCFunction_GetFlags)
    int result = (int) __target__PyCFunction_GetFlags(a);
    STATS_AFTER(PyCFunction_GetFlags)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyCFunction_GetFunction, PyCFunction_GetFlags)
PyCFunction (*__target__PyCFunction_GetFunction)(PyObject*) = NULL;
PyAPI_FUNC(PyCFunction) PyCFunction_GetFunction(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyCFunction_GetFunction)
    PyCFunction result = (PyCFunction) __target__PyCFunction_GetFunction(a);
    STATS_AFTER(PyCFunction_GetFunction)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyCFunction_GetSelf, PyCFunction_GetFunction)
PyObject* (*__target__PyCFunction_GetSelf)(PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyCFunction_GetSelf(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyCFunction_GetSelf)
    PyObject* result = (PyObject*) __target__PyCFunction_GetSelf(a);
    STATS_AFTER(PyCFunction_GetSelf)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyCFunction_New, PyCFunction_GetSelf)
PyObject* (*__target__PyCFunction_New)(PyMethodDef*, PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyCFunction_New(PyMethodDef* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(PyCFunction_New)
    PyObject* result = (PyObject*) __target__PyCFunction_New(a, b);
    STATS_AFTER(PyCFunction_New)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyCFunction_NewEx, PyCFunction_New)
PyObject* (*__target__PyCFunction_NewEx)(PyMethodDef*, PyObject*, PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyCFunction_NewEx(PyMethodDef* a, PyObject* b, PyObject* c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    STATS_BEFORE(PyCFunction_NewEx)
    PyObject* result = (PyObject*) __target__PyCFunction_NewEx(a, b, c);
    STATS_AFTER(PyCFunction_NewEx)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyCMethod_New, PyCFunction_NewEx)
PyObject* (*__target__PyCMethod_New)(PyMethodDef*, PyObject*, PyObject*, PyTypeObject*) = NULL;
PyAPI_FUNC(PyObject*) PyCMethod_New(PyMethodDef* a, PyObject* b, PyObject* c, PyTypeObject* d) {
    LOG("0x%lx 0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c, (unsigned long) d);
    STATS_BEFORE(PyCMethod_New)
    PyObject* result = (PyObject*) __target__PyCMethod_New(a, b, c, d);
    STATS_AFTER(PyCMethod_New)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyCallIter_New, PyCMethod_New)
PyObject* (*__target__PyCallIter_New)(PyObject*, PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyCallIter_New(PyObject* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(PyCallIter_New)
    PyObject* result = (PyObject*) __target__PyCallIter_New(a, b);
    STATS_AFTER(PyCallIter_New)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyCallable_Check, PyCallIter_New)
int (*__target__PyCallable_Check)(PyObject*) = NULL;
PyAPI_FUNC(int) PyCallable_Check(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyCallable_Check)
    int result = (int) __target__PyCallable_Check(a);
    STATS_AFTER(PyCallable_Check)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyCapsule_GetContext, PyCallable_Check)
void* (*__target__PyCapsule_GetContext)(PyObject*) = NULL;
PyAPI_FUNC(void*) PyCapsule_GetContext(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyCapsule_GetContext)
    void* result = (void*) __target__PyCapsule_GetContext(a);
    STATS_AFTER(PyCapsule_GetContext)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyCapsule_GetDestructor, PyCapsule_GetContext)
PyCapsule_Destructor (*__target__PyCapsule_GetDestructor)(PyObject*) = NULL;
PyAPI_FUNC(PyCapsule_Destructor) PyCapsule_GetDestructor(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyCapsule_GetDestructor)
    PyCapsule_Destructor result = (PyCapsule_Destructor) __target__PyCapsule_GetDestructor(a);
    STATS_AFTER(PyCapsule_GetDestructor)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyCapsule_GetName, PyCapsule_GetDestructor)
const char* (*__target__PyCapsule_GetName)(PyObject*) = NULL;
PyAPI_FUNC(const char*) PyCapsule_GetName(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyCapsule_GetName)
    const char* result = (const char*) __target__PyCapsule_GetName(a);
    STATS_AFTER(PyCapsule_GetName)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyCapsule_GetPointer, PyCapsule_GetName)
void* (*__target__PyCapsule_GetPointer)(PyObject*, const char*) = NULL;
PyAPI_FUNC(void*) PyCapsule_GetPointer(PyObject* a, const char* b) {
    LOG("0x%lx '%s'(0x%lx)", (unsigned long) a, b?b:"<null>", (unsigned long) b);
    STATS_BEFORE(PyCapsule_GetPointer)
    void* result = (void*) __target__PyCapsule_GetPointer(a, b);
    STATS_AFTER(PyCapsule_GetPointer)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyCapsule_Import, PyCapsule_GetPointer)
void* (*__target__PyCapsule_Import)(const char*, int) = NULL;
PyAPI_FUNC(void*) PyCapsule_Import(const char* a, int b) {
    LOG("'%s'(0x%lx) 0x%lx", a?a:"<null>", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(PyCapsule_Import)
    void* result = (void*) __target__PyCapsule_Import(a, b);
    STATS_AFTER(PyCapsule_Import)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyCapsule_IsValid, PyCapsule_Import)
int (*__target__PyCapsule_IsValid)(PyObject*, const char*) = NULL;
PyAPI_FUNC(int) PyCapsule_IsValid(PyObject* a, const char* b) {
    LOG("0x%lx '%s'(0x%lx)", (unsigned long) a, b?b:"<null>", (unsigned long) b);
    STATS_BEFORE(PyCapsule_IsValid)
    int result = (int) __target__PyCapsule_IsValid(a, b);
    STATS_AFTER(PyCapsule_IsValid)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyCapsule_New, PyCapsule_IsValid)
PyObject* (*__target__PyCapsule_New)(void*, const char*, PyCapsule_Destructor) = NULL;
PyAPI_FUNC(PyObject*) PyCapsule_New(void* a, const char* b, PyCapsule_Destructor c) {
    LOG("0x%lx '%s'(0x%lx) 0x%lx", (unsigned long) a, b?b:"<null>", (unsigned long) b, (unsigned long) c);
    STATS_BEFORE(PyCapsule_New)
    PyObject* result = (PyObject*) __target__PyCapsule_New(a, b, c);
    STATS_AFTER(PyCapsule_New)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyCapsule_SetContext, PyCapsule_New)
int (*__target__PyCapsule_SetContext)(PyObject*, void*) = NULL;
PyAPI_FUNC(int) PyCapsule_SetContext(PyObject* a, void* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(PyCapsule_SetContext)
    int result = (int) __target__PyCapsule_SetContext(a, b);
    STATS_AFTER(PyCapsule_SetContext)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyCapsule_SetDestructor, PyCapsule_SetContext)
int (*__target__PyCapsule_SetDestructor)(PyObject*, PyCapsule_Destructor) = NULL;
PyAPI_FUNC(int) PyCapsule_SetDestructor(PyObject* a, PyCapsule_Destructor b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(PyCapsule_SetDestructor)
    int result = (int) __target__PyCapsule_SetDestructor(a, b);
    STATS_AFTER(PyCapsule_SetDestructor)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyCapsule_SetName, PyCapsule_SetDestructor)
int (*__target__PyCapsule_SetName)(PyObject*, const char*) = NULL;
PyAPI_FUNC(int) PyCapsule_SetName(PyObject* a, const char* b) {
    LOG("0x%lx '%s'(0x%lx)", (unsigned long) a, b?b:"<null>", (unsigned long) b);
    STATS_BEFORE(PyCapsule_SetName)
    int result = (int) __target__PyCapsule_SetName(a, b);
    STATS_AFTER(PyCapsule_SetName)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyCapsule_SetPointer, PyCapsule_SetName)
int (*__target__PyCapsule_SetPointer)(PyObject*, void*) = NULL;
PyAPI_FUNC(int) PyCapsule_SetPointer(PyObject* a, void* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(PyCapsule_SetPointer)
    int result = (int) __target__PyCapsule_SetPointer(a, b);
    STATS_AFTER(PyCapsule_SetPointer)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(PyObject*) PyCell_Get(PyObject* a) {
    unimplemented("PyCell_Get"); exit(-1);
}
PyAPI_FUNC(PyObject*) PyCell_New(PyObject* a) {
    unimplemented("PyCell_New"); exit(-1);
}
PyAPI_FUNC(int) PyCell_Set(PyObject* a, PyObject* b) {
    unimplemented("PyCell_Set"); exit(-1);
}
STATS_CONTAINER(PyClassMethod_New, PyCapsule_SetPointer)
PyObject* (*__target__PyClassMethod_New)(PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyClassMethod_New(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyClassMethod_New)
    PyObject* result = (PyObject*) __target__PyClassMethod_New(a);
    STATS_AFTER(PyClassMethod_New)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(int) PyCode_Addr2Line(PyCodeObject* a, int b) {
    unimplemented("PyCode_Addr2Line"); exit(-1);
}
STATS_CONTAINER(PyCode_New, PyClassMethod_New)
PyCodeObject* (*__target__PyCode_New)(int, int, int, int, int, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, int, PyObject*) = NULL;
PyAPI_FUNC(PyCodeObject*) PyCode_New(int a, int b, int c, int d, int e, PyObject* f, PyObject* g, PyObject* h, PyObject* i, PyObject* j, PyObject* k, PyObject* l, PyObject* m, int n, PyObject* o) {
    LOG("0x%lx 0x%lx 0x%lx 0x%lx 0x%lx 0x%lx 0x%lx 0x%lx 0x%lx 0x%lx 0x%lx 0x%lx 0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c, (unsigned long) d, (unsigned long) e, (unsigned long) f, (unsigned long) g, (unsigned long) h, (unsigned long) i, (unsigned long) j, (unsigned long) k, (unsigned long) l, (unsigned long) m, (unsigned long) n, (unsigned long) o);
    STATS_BEFORE(PyCode_New)
    PyCodeObject* result = (PyCodeObject*) __target__PyCode_New(a, b, c, d, e, f, g, h, i, j, k, l, m, n, o);
    STATS_AFTER(PyCode_New)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyCode_NewEmpty, PyCode_New)
PyCodeObject* (*__target__PyCode_NewEmpty)(const char*, const char*, int) = NULL;
PyAPI_FUNC(PyCodeObject*) PyCode_NewEmpty(const char* a, const char* b, int c) {
    LOG("'%s'(0x%lx) '%s'(0x%lx) 0x%lx", a?a:"<null>", (unsigned long) a, b?b:"<null>", (unsigned long) b, (unsigned long) c);
    STATS_BEFORE(PyCode_NewEmpty)
    PyCodeObject* result = (PyCodeObject*) __target__PyCode_NewEmpty(a, b, c);
    STATS_AFTER(PyCode_NewEmpty)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyCode_NewWithPosOnlyArgs, PyCode_NewEmpty)
PyCodeObject* (*__target__PyCode_NewWithPosOnlyArgs)(int, int, int, int, int, int, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, int, PyObject*) = NULL;
PyAPI_FUNC(PyCodeObject*) PyCode_NewWithPosOnlyArgs(int a, int b, int c, int d, int e, int f, PyObject* g, PyObject* h, PyObject* i, PyObject* j, PyObject* k, PyObject* l, PyObject* m, PyObject* n, int o, PyObject* p) {
    LOG("0x%lx 0x%lx 0x%lx 0x%lx 0x%lx 0x%lx 0x%lx 0x%lx 0x%lx 0x%lx 0x%lx 0x%lx 0x%lx 0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c, (unsigned long) d, (unsigned long) e, (unsigned long) f, (unsigned long) g, (unsigned long) h, (unsigned long) i, (unsigned long) j, (unsigned long) k, (unsigned long) l, (unsigned long) m, (unsigned long) n, (unsigned long) o, (unsigned long) p);
    STATS_BEFORE(PyCode_NewWithPosOnlyArgs)
    PyCodeObject* result = (PyCodeObject*) __target__PyCode_NewWithPosOnlyArgs(a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p);
    STATS_AFTER(PyCode_NewWithPosOnlyArgs)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(PyObject*) PyCode_Optimize(PyObject* a, PyObject* b, PyObject* c, PyObject* d) {
    unimplemented("PyCode_Optimize"); exit(-1);
}
PyAPI_FUNC(PyObject*) PyCodec_BackslashReplaceErrors(PyObject* a) {
    unimplemented("PyCodec_BackslashReplaceErrors"); exit(-1);
}
PyAPI_FUNC(PyObject*) PyCodec_Decode(PyObject* a, const char* b, const char* c) {
    unimplemented("PyCodec_Decode"); exit(-1);
}
PyAPI_FUNC(PyObject*) PyCodec_Decoder(const char* a) {
    unimplemented("PyCodec_Decoder"); exit(-1);
}
PyAPI_FUNC(PyObject*) PyCodec_Encode(PyObject* a, const char* b, const char* c) {
    unimplemented("PyCodec_Encode"); exit(-1);
}
PyAPI_FUNC(PyObject*) PyCodec_Encoder(const char* a) {
    unimplemented("PyCodec_Encoder"); exit(-1);
}
PyAPI_FUNC(PyObject*) PyCodec_IgnoreErrors(PyObject* a) {
    unimplemented("PyCodec_IgnoreErrors"); exit(-1);
}
PyAPI_FUNC(PyObject*) PyCodec_IncrementalDecoder(const char* a, const char* b) {
    unimplemented("PyCodec_IncrementalDecoder"); exit(-1);
}
PyAPI_FUNC(PyObject*) PyCodec_IncrementalEncoder(const char* a, const char* b) {
    unimplemented("PyCodec_IncrementalEncoder"); exit(-1);
}
PyAPI_FUNC(int) PyCodec_KnownEncoding(const char* a) {
    unimplemented("PyCodec_KnownEncoding"); exit(-1);
}
PyAPI_FUNC(PyObject*) PyCodec_LookupError(const char* a) {
    unimplemented("PyCodec_LookupError"); exit(-1);
}
PyAPI_FUNC(PyObject*) PyCodec_NameReplaceErrors(PyObject* a) {
    unimplemented("PyCodec_NameReplaceErrors"); exit(-1);
}
PyAPI_FUNC(int) PyCodec_Register(PyObject* a) {
    unimplemented("PyCodec_Register"); exit(-1);
}
PyAPI_FUNC(int) PyCodec_RegisterError(const char* a, PyObject* b) {
    unimplemented("PyCodec_RegisterError"); exit(-1);
}
PyAPI_FUNC(PyObject*) PyCodec_ReplaceErrors(PyObject* a) {
    unimplemented("PyCodec_ReplaceErrors"); exit(-1);
}
PyAPI_FUNC(PyObject*) PyCodec_StreamReader(const char* a, PyObject* b, const char* c) {
    unimplemented("PyCodec_StreamReader"); exit(-1);
}
PyAPI_FUNC(PyObject*) PyCodec_StreamWriter(const char* a, PyObject* b, const char* c) {
    unimplemented("PyCodec_StreamWriter"); exit(-1);
}
PyAPI_FUNC(PyObject*) PyCodec_StrictErrors(PyObject* a) {
    unimplemented("PyCodec_StrictErrors"); exit(-1);
}
PyAPI_FUNC(int) PyCodec_Unregister(PyObject* a) {
    unimplemented("PyCodec_Unregister"); exit(-1);
}
PyAPI_FUNC(PyObject*) PyCodec_XMLCharRefReplaceErrors(PyObject* a) {
    unimplemented("PyCodec_XMLCharRefReplaceErrors"); exit(-1);
}
PyAPI_FUNC(int) PyCompile_OpcodeStackEffect(int a, int b) {
    unimplemented("PyCompile_OpcodeStackEffect"); exit(-1);
}
PyAPI_FUNC(int) PyCompile_OpcodeStackEffectWithJump(int a, int b, int c) {
    unimplemented("PyCompile_OpcodeStackEffectWithJump"); exit(-1);
}
STATS_CONTAINER(PyComplex_FromDoubles, PyCode_NewWithPosOnlyArgs)
PyObject* (*__target__PyComplex_FromDoubles)(double, double) = NULL;
PyAPI_FUNC(PyObject*) PyComplex_FromDoubles(double a, double b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(PyComplex_FromDoubles)
    PyObject* result = (PyObject*) __target__PyComplex_FromDoubles(a, b);
    STATS_AFTER(PyComplex_FromDoubles)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyComplex_ImagAsDouble, PyComplex_FromDoubles)
double (*__target__PyComplex_ImagAsDouble)(PyObject*) = NULL;
PyAPI_FUNC(double) PyComplex_ImagAsDouble(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyComplex_ImagAsDouble)
    double result = (double) __target__PyComplex_ImagAsDouble(a);
    STATS_AFTER(PyComplex_ImagAsDouble)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyComplex_RealAsDouble, PyComplex_ImagAsDouble)
double (*__target__PyComplex_RealAsDouble)(PyObject*) = NULL;
PyAPI_FUNC(double) PyComplex_RealAsDouble(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyComplex_RealAsDouble)
    double result = (double) __target__PyComplex_RealAsDouble(a);
    STATS_AFTER(PyComplex_RealAsDouble)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(void) PyConfig_Clear(PyConfig* a) {
    unimplemented("PyConfig_Clear"); exit(-1);
}
PyAPI_FUNC(void) PyConfig_InitIsolatedConfig(PyConfig* a) {
    unimplemented("PyConfig_InitIsolatedConfig"); exit(-1);
}
PyAPI_FUNC(void) PyConfig_InitPythonConfig(PyConfig* a) {
    unimplemented("PyConfig_InitPythonConfig"); exit(-1);
}
PyAPI_FUNC(PyStatus) PyConfig_Read(PyConfig* a) {
    unimplemented("PyConfig_Read"); exit(-1);
}
PyAPI_FUNC(PyStatus) PyConfig_SetArgv(PyConfig* a, Py_ssize_t b, wchar_t*const* c) {
    unimplemented("PyConfig_SetArgv"); exit(-1);
}
PyAPI_FUNC(PyStatus) PyConfig_SetBytesArgv(PyConfig* a, Py_ssize_t b, char*const* c) {
    unimplemented("PyConfig_SetBytesArgv"); exit(-1);
}
PyAPI_FUNC(PyStatus) PyConfig_SetBytesString(PyConfig* a, wchar_t** b, const char* c) {
    unimplemented("PyConfig_SetBytesString"); exit(-1);
}
PyAPI_FUNC(PyStatus) PyConfig_SetString(PyConfig* a, wchar_t** b, const wchar_t* c) {
    unimplemented("PyConfig_SetString"); exit(-1);
}
PyAPI_FUNC(PyStatus) PyConfig_SetWideStringList(PyConfig* a, PyWideStringList* b, Py_ssize_t c, wchar_t** d) {
    unimplemented("PyConfig_SetWideStringList"); exit(-1);
}
STATS_CONTAINER(PyContextVar_Get, PyComplex_RealAsDouble)
int (*__target__PyContextVar_Get)(PyObject*, PyObject*, PyObject**) = NULL;
PyAPI_FUNC(int) PyContextVar_Get(PyObject* a, PyObject* b, PyObject** c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    STATS_BEFORE(PyContextVar_Get)
    int result = (int) __target__PyContextVar_Get(a, b, c);
    STATS_AFTER(PyContextVar_Get)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyContextVar_New, PyContextVar_Get)
PyObject* (*__target__PyContextVar_New)(const char*, PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyContextVar_New(const char* a, PyObject* b) {
    LOG("'%s'(0x%lx) 0x%lx", a?a:"<null>", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(PyContextVar_New)
    PyObject* result = (PyObject*) __target__PyContextVar_New(a, b);
    STATS_AFTER(PyContextVar_New)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(int) PyContextVar_Reset(PyObject* a, PyObject* b) {
    unimplemented("PyContextVar_Reset"); exit(-1);
}
STATS_CONTAINER(PyContextVar_Set, PyContextVar_New)
PyObject* (*__target__PyContextVar_Set)(PyObject*, PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyContextVar_Set(PyObject* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(PyContextVar_Set)
    PyObject* result = (PyObject*) __target__PyContextVar_Set(a, b);
    STATS_AFTER(PyContextVar_Set)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(PyObject*) PyContext_Copy(PyObject* a) {
    unimplemented("PyContext_Copy"); exit(-1);
}
PyAPI_FUNC(PyObject*) PyContext_CopyCurrent() {
    unimplemented("PyContext_CopyCurrent"); exit(-1);
}
PyAPI_FUNC(int) PyContext_Enter(PyObject* a) {
    unimplemented("PyContext_Enter"); exit(-1);
}
PyAPI_FUNC(int) PyContext_Exit(PyObject* a) {
    unimplemented("PyContext_Exit"); exit(-1);
}
PyAPI_FUNC(PyObject*) PyContext_New() {
    unimplemented("PyContext_New"); exit(-1);
}
PyAPI_FUNC(PyObject*) PyCoro_New(PyFrameObject* a, PyObject* b, PyObject* c) {
    unimplemented("PyCoro_New"); exit(-1);
}
STATS_CONTAINER(PyDescrObject_GetName, PyContextVar_Set)
PyObject* (*__target__PyDescrObject_GetName)(PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyDescrObject_GetName(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyDescrObject_GetName)
    PyObject* result = (PyObject*) __target__PyDescrObject_GetName(a);
    STATS_AFTER(PyDescrObject_GetName)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyDescrObject_GetType, PyDescrObject_GetName)
PyTypeObject* (*__target__PyDescrObject_GetType)(PyObject*) = NULL;
PyAPI_FUNC(PyTypeObject*) PyDescrObject_GetType(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyDescrObject_GetType)
    PyTypeObject* result = (PyTypeObject*) __target__PyDescrObject_GetType(a);
    STATS_AFTER(PyDescrObject_GetType)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(int) PyDescr_IsData(PyObject* a) {
    unimplemented("PyDescr_IsData"); exit(-1);
}
STATS_CONTAINER(PyDescr_NewClassMethod, PyDescrObject_GetType)
PyObject* (*__target__PyDescr_NewClassMethod)(PyTypeObject*, PyMethodDef*) = NULL;
PyAPI_FUNC(PyObject*) PyDescr_NewClassMethod(PyTypeObject* a, PyMethodDef* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(PyDescr_NewClassMethod)
    PyObject* result = (PyObject*) __target__PyDescr_NewClassMethod(a, b);
    STATS_AFTER(PyDescr_NewClassMethod)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyDescr_NewGetSet, PyDescr_NewClassMethod)
PyObject* (*__target__PyDescr_NewGetSet)(PyTypeObject*, PyGetSetDef*) = NULL;
PyAPI_FUNC(PyObject*) PyDescr_NewGetSet(PyTypeObject* a, PyGetSetDef* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(PyDescr_NewGetSet)
    PyObject* result = (PyObject*) __target__PyDescr_NewGetSet(a, b);
    STATS_AFTER(PyDescr_NewGetSet)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(PyObject*) PyDescr_NewMember(PyTypeObject* a, struct PyMemberDef* b) {
    unimplemented("PyDescr_NewMember"); exit(-1);
}
PyAPI_FUNC(PyObject*) PyDescr_NewMethod(PyTypeObject* a, PyMethodDef* b) {
    unimplemented("PyDescr_NewMethod"); exit(-1);
}
PyAPI_FUNC(PyObject*) PyDescr_NewWrapper(PyTypeObject* a, struct wrapperbase* b, void* c) {
    unimplemented("PyDescr_NewWrapper"); exit(-1);
}
STATS_CONTAINER(PyDictProxy_New, PyDescr_NewGetSet)
PyObject* (*__target__PyDictProxy_New)(PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyDictProxy_New(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyDictProxy_New)
    PyObject* result = (PyObject*) __target__PyDictProxy_New(a);
    STATS_AFTER(PyDictProxy_New)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyDict_Clear, PyDictProxy_New)
void (*__target__PyDict_Clear)(PyObject*) = NULL;
PyAPI_FUNC(void) PyDict_Clear(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyDict_Clear)
    __target__PyDict_Clear(a);
    STATS_AFTER(PyDict_Clear)
    LOG_AFTER_VOID
}
STATS_CONTAINER(PyDict_Contains, PyDict_Clear)
int (*__target__PyDict_Contains)(PyObject*, PyObject*) = NULL;
PyAPI_FUNC(int) PyDict_Contains(PyObject* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(PyDict_Contains)
    int result = (int) __target__PyDict_Contains(a, b);
    STATS_AFTER(PyDict_Contains)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyDict_Copy, PyDict_Contains)
PyObject* (*__target__PyDict_Copy)(PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyDict_Copy(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyDict_Copy)
    PyObject* result = (PyObject*) __target__PyDict_Copy(a);
    STATS_AFTER(PyDict_Copy)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyDict_DelItem, PyDict_Copy)
int (*__target__PyDict_DelItem)(PyObject*, PyObject*) = NULL;
PyAPI_FUNC(int) PyDict_DelItem(PyObject* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(PyDict_DelItem)
    int result = (int) __target__PyDict_DelItem(a, b);
    STATS_AFTER(PyDict_DelItem)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyDict_GetItem, PyDict_DelItem)
PyObject* (*__target__PyDict_GetItem)(PyObject*, PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyDict_GetItem(PyObject* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(PyDict_GetItem)
    PyObject* result = (PyObject*) __target__PyDict_GetItem(a, b);
    STATS_AFTER(PyDict_GetItem)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyDict_GetItemWithError, PyDict_GetItem)
PyObject* (*__target__PyDict_GetItemWithError)(PyObject*, PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyDict_GetItemWithError(PyObject* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(PyDict_GetItemWithError)
    PyObject* result = (PyObject*) __target__PyDict_GetItemWithError(a, b);
    STATS_AFTER(PyDict_GetItemWithError)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(PyObject*) PyDict_Items(PyObject* a) {
    unimplemented("PyDict_Items"); exit(-1);
}
STATS_CONTAINER(PyDict_Keys, PyDict_GetItemWithError)
PyObject* (*__target__PyDict_Keys)(PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyDict_Keys(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyDict_Keys)
    PyObject* result = (PyObject*) __target__PyDict_Keys(a);
    STATS_AFTER(PyDict_Keys)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyDict_Merge, PyDict_Keys)
int (*__target__PyDict_Merge)(PyObject*, PyObject*, int) = NULL;
PyAPI_FUNC(int) PyDict_Merge(PyObject* a, PyObject* b, int c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    STATS_BEFORE(PyDict_Merge)
    int result = (int) __target__PyDict_Merge(a, b, c);
    STATS_AFTER(PyDict_Merge)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(int) PyDict_MergeFromSeq2(PyObject* a, PyObject* b, int c) {
    unimplemented("PyDict_MergeFromSeq2"); exit(-1);
}
STATS_CONTAINER(PyDict_New, PyDict_Merge)
PyObject* (*__target__PyDict_New)() = NULL;
PyAPI_FUNC(PyObject*) PyDict_New() {
    LOGS("");
    STATS_BEFORE(PyDict_New)
    PyObject* result = (PyObject*) __target__PyDict_New();
    STATS_AFTER(PyDict_New)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyDict_SetDefault, PyDict_New)
PyObject* (*__target__PyDict_SetDefault)(PyObject*, PyObject*, PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyDict_SetDefault(PyObject* a, PyObject* b, PyObject* c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    STATS_BEFORE(PyDict_SetDefault)
    PyObject* result = (PyObject*) __target__PyDict_SetDefault(a, b, c);
    STATS_AFTER(PyDict_SetDefault)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyDict_SetItem, PyDict_SetDefault)
int (*__target__PyDict_SetItem)(PyObject*, PyObject*, PyObject*) = NULL;
PyAPI_FUNC(int) PyDict_SetItem(PyObject* a, PyObject* b, PyObject* c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    STATS_BEFORE(PyDict_SetItem)
    int result = (int) __target__PyDict_SetItem(a, b, c);
    STATS_AFTER(PyDict_SetItem)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyDict_Size, PyDict_SetItem)
Py_ssize_t (*__target__PyDict_Size)(PyObject*) = NULL;
PyAPI_FUNC(Py_ssize_t) PyDict_Size(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyDict_Size)
    Py_ssize_t result = (Py_ssize_t) __target__PyDict_Size(a);
    STATS_AFTER(PyDict_Size)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyDict_Update, PyDict_Size)
int (*__target__PyDict_Update)(PyObject*, PyObject*) = NULL;
PyAPI_FUNC(int) PyDict_Update(PyObject* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(PyDict_Update)
    int result = (int) __target__PyDict_Update(a, b);
    STATS_AFTER(PyDict_Update)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyDict_Values, PyDict_Update)
PyObject* (*__target__PyDict_Values)(PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyDict_Values(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyDict_Values)
    PyObject* result = (PyObject*) __target__PyDict_Values(a);
    STATS_AFTER(PyDict_Values)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyErr_BadArgument, PyDict_Values)
int (*__target__PyErr_BadArgument)() = NULL;
PyAPI_FUNC(int) PyErr_BadArgument() {
    LOGS("");
    STATS_BEFORE(PyErr_BadArgument)
    int result = (int) __target__PyErr_BadArgument();
    STATS_AFTER(PyErr_BadArgument)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyErr_BadInternalCall, PyErr_BadArgument)
void (*__target__PyErr_BadInternalCall)() = NULL;
PyAPI_FUNC(void) PyErr_BadInternalCall() {
    LOGS("");
    STATS_BEFORE(PyErr_BadInternalCall)
    __target__PyErr_BadInternalCall();
    STATS_AFTER(PyErr_BadInternalCall)
    LOG_AFTER_VOID
}
STATS_CONTAINER(PyErr_CheckSignals, PyErr_BadInternalCall)
int (*__target__PyErr_CheckSignals)() = NULL;
PyAPI_FUNC(int) PyErr_CheckSignals() {
    LOGS("");
    STATS_BEFORE(PyErr_CheckSignals)
    int result = (int) __target__PyErr_CheckSignals();
    STATS_AFTER(PyErr_CheckSignals)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyErr_Clear, PyErr_CheckSignals)
void (*__target__PyErr_Clear)() = NULL;
PyAPI_FUNC(void) PyErr_Clear() {
    LOGS("");
    STATS_BEFORE(PyErr_Clear)
    __target__PyErr_Clear();
    STATS_AFTER(PyErr_Clear)
    LOG_AFTER_VOID
}
STATS_CONTAINER(PyErr_Display, PyErr_Clear)
void (*__target__PyErr_Display)(PyObject*, PyObject*, PyObject*) = NULL;
PyAPI_FUNC(void) PyErr_Display(PyObject* a, PyObject* b, PyObject* c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    STATS_BEFORE(PyErr_Display)
    __target__PyErr_Display(a, b, c);
    STATS_AFTER(PyErr_Display)
    LOG_AFTER_VOID
}
STATS_CONTAINER(PyErr_ExceptionMatches, PyErr_Display)
int (*__target__PyErr_ExceptionMatches)(PyObject*) = NULL;
PyAPI_FUNC(int) PyErr_ExceptionMatches(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyErr_ExceptionMatches)
    int result = (int) __target__PyErr_ExceptionMatches(a);
    STATS_AFTER(PyErr_ExceptionMatches)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyErr_Fetch, PyErr_ExceptionMatches)
void (*__target__PyErr_Fetch)(PyObject**, PyObject**, PyObject**) = NULL;
PyAPI_FUNC(void) PyErr_Fetch(PyObject** a, PyObject** b, PyObject** c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    STATS_BEFORE(PyErr_Fetch)
    __target__PyErr_Fetch(a, b, c);
    STATS_AFTER(PyErr_Fetch)
    LOG_AFTER_VOID
}
STATS_CONTAINER(PyErr_FormatV, PyErr_Fetch)
PyObject* (*__target__PyErr_FormatV)(PyObject*, const char*, va_list) = NULL;
PyAPI_FUNC(PyObject*) PyErr_FormatV(PyObject* a, const char* b, va_list c) {
    LOG("0x%lx '%s'(0x%lx) 0x%lx", (unsigned long) a, b?b:"<null>", (unsigned long) b, (unsigned long) c);
    STATS_BEFORE(PyErr_FormatV)
    PyObject* result = (PyObject*) __target__PyErr_FormatV(a, b, c);
    STATS_AFTER(PyErr_FormatV)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyErr_GetExcInfo, PyErr_FormatV)
void (*__target__PyErr_GetExcInfo)(PyObject**, PyObject**, PyObject**) = NULL;
PyAPI_FUNC(void) PyErr_GetExcInfo(PyObject** a, PyObject** b, PyObject** c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    STATS_BEFORE(PyErr_GetExcInfo)
    __target__PyErr_GetExcInfo(a, b, c);
    STATS_AFTER(PyErr_GetExcInfo)
    LOG_AFTER_VOID
}
STATS_CONTAINER(PyErr_GivenExceptionMatches, PyErr_GetExcInfo)
int (*__target__PyErr_GivenExceptionMatches)(PyObject*, PyObject*) = NULL;
PyAPI_FUNC(int) PyErr_GivenExceptionMatches(PyObject* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(PyErr_GivenExceptionMatches)
    int result = (int) __target__PyErr_GivenExceptionMatches(a, b);
    STATS_AFTER(PyErr_GivenExceptionMatches)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyErr_NewException, PyErr_GivenExceptionMatches)
PyObject* (*__target__PyErr_NewException)(const char*, PyObject*, PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyErr_NewException(const char* a, PyObject* b, PyObject* c) {
    LOG("'%s'(0x%lx) 0x%lx 0x%lx", a?a:"<null>", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    STATS_BEFORE(PyErr_NewException)
    PyObject* result = (PyObject*) __target__PyErr_NewException(a, b, c);
    STATS_AFTER(PyErr_NewException)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyErr_NewExceptionWithDoc, PyErr_NewException)
PyObject* (*__target__PyErr_NewExceptionWithDoc)(const char*, const char*, PyObject*, PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyErr_NewExceptionWithDoc(const char* a, const char* b, PyObject* c, PyObject* d) {
    LOG("'%s'(0x%lx) '%s'(0x%lx) 0x%lx 0x%lx", a?a:"<null>", (unsigned long) a, b?b:"<null>", (unsigned long) b, (unsigned long) c, (unsigned long) d);
    STATS_BEFORE(PyErr_NewExceptionWithDoc)
    PyObject* result = (PyObject*) __target__PyErr_NewExceptionWithDoc(a, b, c, d);
    STATS_AFTER(PyErr_NewExceptionWithDoc)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyErr_NoMemory, PyErr_NewExceptionWithDoc)
PyObject* (*__target__PyErr_NoMemory)() = NULL;
PyAPI_FUNC(PyObject*) PyErr_NoMemory() {
    LOGS("");
    STATS_BEFORE(PyErr_NoMemory)
    PyObject* result = (PyObject*) __target__PyErr_NoMemory();
    STATS_AFTER(PyErr_NoMemory)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyErr_NormalizeException, PyErr_NoMemory)
void (*__target__PyErr_NormalizeException)(PyObject**, PyObject**, PyObject**) = NULL;
PyAPI_FUNC(void) PyErr_NormalizeException(PyObject** a, PyObject** b, PyObject** c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    STATS_BEFORE(PyErr_NormalizeException)
    __target__PyErr_NormalizeException(a, b, c);
    STATS_AFTER(PyErr_NormalizeException)
    LOG_AFTER_VOID
}
STATS_CONTAINER(PyErr_Occurred, PyErr_NormalizeException)
PyObject* (*__target__PyErr_Occurred)() = NULL;
PyAPI_FUNC(PyObject*) PyErr_Occurred() {
    LOGS("");
    STATS_BEFORE(PyErr_Occurred)
    PyObject* result = (PyObject*) __target__PyErr_Occurred();
    STATS_AFTER(PyErr_Occurred)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyErr_Print, PyErr_Occurred)
void (*__target__PyErr_Print)() = NULL;
PyAPI_FUNC(void) PyErr_Print() {
    LOGS("");
    STATS_BEFORE(PyErr_Print)
    __target__PyErr_Print();
    STATS_AFTER(PyErr_Print)
    LOG_AFTER_VOID
}
STATS_CONTAINER(PyErr_PrintEx, PyErr_Print)
void (*__target__PyErr_PrintEx)(int) = NULL;
PyAPI_FUNC(void) PyErr_PrintEx(int a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyErr_PrintEx)
    __target__PyErr_PrintEx(a);
    STATS_AFTER(PyErr_PrintEx)
    LOG_AFTER_VOID
}
PyAPI_FUNC(PyObject*) PyErr_ProgramText(const char* a, int b) {
    unimplemented("PyErr_ProgramText"); exit(-1);
}
PyAPI_FUNC(PyObject*) PyErr_ProgramTextObject(PyObject* a, int b) {
    unimplemented("PyErr_ProgramTextObject"); exit(-1);
}
PyAPI_FUNC(void) PyErr_RangedSyntaxLocationObject(PyObject* a, int b, int c, int d, int e) {
    unimplemented("PyErr_RangedSyntaxLocationObject"); exit(-1);
}
STATS_CONTAINER(PyErr_Restore, PyErr_PrintEx)
void (*__target__PyErr_Restore)(PyObject*, PyObject*, PyObject*) = NULL;
PyAPI_FUNC(void) PyErr_Restore(PyObject* a, PyObject* b, PyObject* c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    STATS_BEFORE(PyErr_Restore)
    __target__PyErr_Restore(a, b, c);
    STATS_AFTER(PyErr_Restore)
    LOG_AFTER_VOID
}
STATS_CONTAINER(PyErr_SetExcInfo, PyErr_Restore)
void (*__target__PyErr_SetExcInfo)(PyObject*, PyObject*, PyObject*) = NULL;
PyAPI_FUNC(void) PyErr_SetExcInfo(PyObject* a, PyObject* b, PyObject* c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    STATS_BEFORE(PyErr_SetExcInfo)
    __target__PyErr_SetExcInfo(a, b, c);
    STATS_AFTER(PyErr_SetExcInfo)
    LOG_AFTER_VOID
}
STATS_CONTAINER(PyErr_SetFromErrno, PyErr_SetExcInfo)
PyObject* (*__target__PyErr_SetFromErrno)(PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyErr_SetFromErrno(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyErr_SetFromErrno)
    PyObject* result = (PyObject*) __target__PyErr_SetFromErrno(a);
    STATS_AFTER(PyErr_SetFromErrno)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyErr_SetFromErrnoWithFilename, PyErr_SetFromErrno)
PyObject* (*__target__PyErr_SetFromErrnoWithFilename)(PyObject*, const char*) = NULL;
PyAPI_FUNC(PyObject*) PyErr_SetFromErrnoWithFilename(PyObject* a, const char* b) {
    LOG("0x%lx '%s'(0x%lx)", (unsigned long) a, b?b:"<null>", (unsigned long) b);
    STATS_BEFORE(PyErr_SetFromErrnoWithFilename)
    PyObject* result = (PyObject*) __target__PyErr_SetFromErrnoWithFilename(a, b);
    STATS_AFTER(PyErr_SetFromErrnoWithFilename)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyErr_SetFromErrnoWithFilenameObject, PyErr_SetFromErrnoWithFilename)
PyObject* (*__target__PyErr_SetFromErrnoWithFilenameObject)(PyObject*, PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyErr_SetFromErrnoWithFilenameObject(PyObject* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(PyErr_SetFromErrnoWithFilenameObject)
    PyObject* result = (PyObject*) __target__PyErr_SetFromErrnoWithFilenameObject(a, b);
    STATS_AFTER(PyErr_SetFromErrnoWithFilenameObject)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyErr_SetFromErrnoWithFilenameObjects, PyErr_SetFromErrnoWithFilenameObject)
PyObject* (*__target__PyErr_SetFromErrnoWithFilenameObjects)(PyObject*, PyObject*, PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyErr_SetFromErrnoWithFilenameObjects(PyObject* a, PyObject* b, PyObject* c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    STATS_BEFORE(PyErr_SetFromErrnoWithFilenameObjects)
    PyObject* result = (PyObject*) __target__PyErr_SetFromErrnoWithFilenameObjects(a, b, c);
    STATS_AFTER(PyErr_SetFromErrnoWithFilenameObjects)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(PyObject*) PyErr_SetImportError(PyObject* a, PyObject* b, PyObject* c) {
    unimplemented("PyErr_SetImportError"); exit(-1);
}
PyAPI_FUNC(PyObject*) PyErr_SetImportErrorSubclass(PyObject* a, PyObject* b, PyObject* c, PyObject* d) {
    unimplemented("PyErr_SetImportErrorSubclass"); exit(-1);
}
PyAPI_FUNC(void) PyErr_SetInterrupt() {
    unimplemented("PyErr_SetInterrupt"); exit(-1);
}
PyAPI_FUNC(int) PyErr_SetInterruptEx(int a) {
    unimplemented("PyErr_SetInterruptEx"); exit(-1);
}
STATS_CONTAINER(PyErr_SetNone, PyErr_SetFromErrnoWithFilenameObjects)
void (*__target__PyErr_SetNone)(PyObject*) = NULL;
PyAPI_FUNC(void) PyErr_SetNone(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyErr_SetNone)
    __target__PyErr_SetNone(a);
    STATS_AFTER(PyErr_SetNone)
    LOG_AFTER_VOID
}
STATS_CONTAINER(PyErr_SetObject, PyErr_SetNone)
void (*__target__PyErr_SetObject)(PyObject*, PyObject*) = NULL;
PyAPI_FUNC(void) PyErr_SetObject(PyObject* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(PyErr_SetObject)
    __target__PyErr_SetObject(a, b);
    STATS_AFTER(PyErr_SetObject)
    LOG_AFTER_VOID
}
STATS_CONTAINER(PyErr_SetString, PyErr_SetObject)
void (*__target__PyErr_SetString)(PyObject*, const char*) = NULL;
PyAPI_FUNC(void) PyErr_SetString(PyObject* a, const char* b) {
    LOG("0x%lx '%s'(0x%lx)", (unsigned long) a, b?b:"<null>", (unsigned long) b);
    STATS_BEFORE(PyErr_SetString)
    __target__PyErr_SetString(a, b);
    STATS_AFTER(PyErr_SetString)
    LOG_AFTER_VOID
}
PyAPI_FUNC(void) PyErr_SyntaxLocation(const char* a, int b) {
    unimplemented("PyErr_SyntaxLocation"); exit(-1);
}
PyAPI_FUNC(void) PyErr_SyntaxLocationEx(const char* a, int b, int c) {
    unimplemented("PyErr_SyntaxLocationEx"); exit(-1);
}
PyAPI_FUNC(void) PyErr_SyntaxLocationObject(PyObject* a, int b, int c) {
    unimplemented("PyErr_SyntaxLocationObject"); exit(-1);
}
PyAPI_FUNC(int) PyErr_WarnExplicit(PyObject* a, const char* b, const char* c, int d, const char* e, PyObject* f) {
    unimplemented("PyErr_WarnExplicit"); exit(-1);
}
PyAPI_FUNC(int) PyErr_WarnExplicitFormat(PyObject* a, const char* b, int c, const char* d, PyObject* e, const char* f, ...) {
    unimplemented("PyErr_WarnExplicitFormat"); exit(-1);
}
PyAPI_FUNC(int) PyErr_WarnExplicitObject(PyObject* a, PyObject* b, PyObject* c, int d, PyObject* e, PyObject* f) {
    unimplemented("PyErr_WarnExplicitObject"); exit(-1);
}
STATS_CONTAINER(PyErr_WriteUnraisable, PyErr_SetString)
void (*__target__PyErr_WriteUnraisable)(PyObject*) = NULL;
PyAPI_FUNC(void) PyErr_WriteUnraisable(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyErr_WriteUnraisable)
    __target__PyErr_WriteUnraisable(a);
    STATS_AFTER(PyErr_WriteUnraisable)
    LOG_AFTER_VOID
}
PyAPI_FUNC(void) PyEval_AcquireLock() {
    unimplemented("PyEval_AcquireLock"); exit(-1);
}
PyAPI_FUNC(void) PyEval_AcquireThread(PyThreadState* a) {
    unimplemented("PyEval_AcquireThread"); exit(-1);
}
PyAPI_FUNC(PyObject*) PyEval_CallFunction(PyObject* a, const char* b, ...) {
    unimplemented("PyEval_CallFunction"); exit(-1);
}
PyAPI_FUNC(PyObject*) PyEval_CallMethod(PyObject* a, const char* b, const char* c, ...) {
    unimplemented("PyEval_CallMethod"); exit(-1);
}
STATS_CONTAINER(PyEval_CallObjectWithKeywords, PyErr_WriteUnraisable)
PyObject* (*__target__PyEval_CallObjectWithKeywords)(PyObject*, PyObject*, PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyEval_CallObjectWithKeywords(PyObject* a, PyObject* b, PyObject* c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    STATS_BEFORE(PyEval_CallObjectWithKeywords)
    PyObject* result = (PyObject*) __target__PyEval_CallObjectWithKeywords(a, b, c);
    STATS_AFTER(PyEval_CallObjectWithKeywords)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyEval_EvalCode, PyEval_CallObjectWithKeywords)
PyObject* (*__target__PyEval_EvalCode)(PyObject*, PyObject*, PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyEval_EvalCode(PyObject* a, PyObject* b, PyObject* c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    STATS_BEFORE(PyEval_EvalCode)
    PyObject* result = (PyObject*) __target__PyEval_EvalCode(a, b, c);
    STATS_AFTER(PyEval_EvalCode)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyEval_EvalCodeEx, PyEval_EvalCode)
PyObject* (*__target__PyEval_EvalCodeEx)(PyObject*, PyObject*, PyObject*, PyObject*const*, int, PyObject*const*, int, PyObject*const*, int, PyObject*, PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyEval_EvalCodeEx(PyObject* a, PyObject* b, PyObject* c, PyObject*const* d, int e, PyObject*const* f, int g, PyObject*const* h, int i, PyObject* j, PyObject* k) {
    LOG("0x%lx 0x%lx 0x%lx 0x%lx 0x%lx 0x%lx 0x%lx 0x%lx 0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c, (unsigned long) d, (unsigned long) e, (unsigned long) f, (unsigned long) g, (unsigned long) h, (unsigned long) i, (unsigned long) j, (unsigned long) k);
    STATS_BEFORE(PyEval_EvalCodeEx)
    PyObject* result = (PyObject*) __target__PyEval_EvalCodeEx(a, b, c, d, e, f, g, h, i, j, k);
    STATS_AFTER(PyEval_EvalCodeEx)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(PyObject*) PyEval_EvalFrame(PyFrameObject* a) {
    unimplemented("PyEval_EvalFrame"); exit(-1);
}
PyAPI_FUNC(PyObject*) PyEval_EvalFrameEx(PyFrameObject* a, int b) {
    unimplemented("PyEval_EvalFrameEx"); exit(-1);
}
STATS_CONTAINER(PyEval_GetBuiltins, PyEval_EvalCodeEx)
PyObject* (*__target__PyEval_GetBuiltins)() = NULL;
PyAPI_FUNC(PyObject*) PyEval_GetBuiltins() {
    LOGS("");
    STATS_BEFORE(PyEval_GetBuiltins)
    PyObject* result = (PyObject*) __target__PyEval_GetBuiltins();
    STATS_AFTER(PyEval_GetBuiltins)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(PyFrameObject*) PyEval_GetFrame() {
    unimplemented("PyEval_GetFrame"); exit(-1);
}
PyAPI_FUNC(const char*) PyEval_GetFuncDesc(PyObject* a) {
    unimplemented("PyEval_GetFuncDesc"); exit(-1);
}
PyAPI_FUNC(const char*) PyEval_GetFuncName(PyObject* a) {
    unimplemented("PyEval_GetFuncName"); exit(-1);
}
PyAPI_FUNC(PyObject*) PyEval_GetGlobals() {
    unimplemented("PyEval_GetGlobals"); exit(-1);
}
PyAPI_FUNC(PyObject*) PyEval_GetLocals() {
    unimplemented("PyEval_GetLocals"); exit(-1);
}
STATS_CONTAINER(PyEval_InitThreads, PyEval_GetBuiltins)
void (*__target__PyEval_InitThreads)() = NULL;
PyAPI_FUNC(void) PyEval_InitThreads() {
    LOGS("");
    STATS_BEFORE(PyEval_InitThreads)
    __target__PyEval_InitThreads();
    STATS_AFTER(PyEval_InitThreads)
    LOG_AFTER_VOID
}
STATS_CONTAINER(PyEval_MergeCompilerFlags, PyEval_InitThreads)
int (*__target__PyEval_MergeCompilerFlags)(PyCompilerFlags*) = NULL;
PyAPI_FUNC(int) PyEval_MergeCompilerFlags(PyCompilerFlags* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyEval_MergeCompilerFlags)
    int result = (int) __target__PyEval_MergeCompilerFlags(a);
    STATS_AFTER(PyEval_MergeCompilerFlags)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(void) PyEval_ReleaseLock() {
    unimplemented("PyEval_ReleaseLock"); exit(-1);
}
PyAPI_FUNC(void) PyEval_ReleaseThread(PyThreadState* a) {
    unimplemented("PyEval_ReleaseThread"); exit(-1);
}
STATS_CONTAINER(PyEval_RestoreThread, PyEval_MergeCompilerFlags)
void (*__target__PyEval_RestoreThread)(PyThreadState*) = NULL;
PyAPI_FUNC(void) PyEval_RestoreThread(PyThreadState* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyEval_RestoreThread)
    __target__PyEval_RestoreThread(a);
    STATS_AFTER(PyEval_RestoreThread)
    LOG_AFTER_VOID
}
STATS_CONTAINER(PyEval_SaveThread, PyEval_RestoreThread)
PyThreadState* (*__target__PyEval_SaveThread)() = NULL;
PyAPI_FUNC(PyThreadState*) PyEval_SaveThread() {
    LOGS("");
    STATS_BEFORE(PyEval_SaveThread)
    PyThreadState* result = (PyThreadState*) __target__PyEval_SaveThread();
    STATS_AFTER(PyEval_SaveThread)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(void) PyEval_SetProfile(Py_tracefunc a, PyObject* b) {
    unimplemented("PyEval_SetProfile"); exit(-1);
}
PyAPI_FUNC(void) PyEval_SetTrace(Py_tracefunc a, PyObject* b) {
    unimplemented("PyEval_SetTrace"); exit(-1);
}
STATS_CONTAINER(PyEval_ThreadsInitialized, PyEval_SaveThread)
int (*__target__PyEval_ThreadsInitialized)() = NULL;
PyAPI_FUNC(int) PyEval_ThreadsInitialized() {
    LOGS("");
    STATS_BEFORE(PyEval_ThreadsInitialized)
    int result = (int) __target__PyEval_ThreadsInitialized();
    STATS_AFTER(PyEval_ThreadsInitialized)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(const char*) PyExceptionClass_Name(PyObject* a) {
    unimplemented("PyExceptionClass_Name"); exit(-1);
}
PyAPI_FUNC(PyObject*) PyException_GetCause(PyObject* a) {
    unimplemented("PyException_GetCause"); exit(-1);
}
STATS_CONTAINER(PyException_GetContext, PyEval_ThreadsInitialized)
PyObject* (*__target__PyException_GetContext)(PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyException_GetContext(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyException_GetContext)
    PyObject* result = (PyObject*) __target__PyException_GetContext(a);
    STATS_AFTER(PyException_GetContext)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(PyObject*) PyException_GetTraceback(PyObject* a) {
    unimplemented("PyException_GetTraceback"); exit(-1);
}
STATS_CONTAINER(PyException_SetCause, PyException_GetContext)
void (*__target__PyException_SetCause)(PyObject*, PyObject*) = NULL;
PyAPI_FUNC(void) PyException_SetCause(PyObject* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(PyException_SetCause)
    __target__PyException_SetCause(a, b);
    STATS_AFTER(PyException_SetCause)
    LOG_AFTER_VOID
}
STATS_CONTAINER(PyException_SetContext, PyException_SetCause)
void (*__target__PyException_SetContext)(PyObject*, PyObject*) = NULL;
PyAPI_FUNC(void) PyException_SetContext(PyObject* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(PyException_SetContext)
    __target__PyException_SetContext(a, b);
    STATS_AFTER(PyException_SetContext)
    LOG_AFTER_VOID
}
STATS_CONTAINER(PyException_SetTraceback, PyException_SetContext)
int (*__target__PyException_SetTraceback)(PyObject*, PyObject*) = NULL;
PyAPI_FUNC(int) PyException_SetTraceback(PyObject* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(PyException_SetTraceback)
    int result = (int) __target__PyException_SetTraceback(a, b);
    STATS_AFTER(PyException_SetTraceback)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(PyObject*) PyFile_FromFd(int a, const char* b, const char* c, int d, const char* e, const char* f, const char* g, int h) {
    unimplemented("PyFile_FromFd"); exit(-1);
}
PyAPI_FUNC(PyObject*) PyFile_GetLine(PyObject* a, int b) {
    unimplemented("PyFile_GetLine"); exit(-1);
}
PyAPI_FUNC(PyObject*) PyFile_NewStdPrinter(int a) {
    unimplemented("PyFile_NewStdPrinter"); exit(-1);
}
PyAPI_FUNC(PyObject*) PyFile_OpenCode(const char* a) {
    unimplemented("PyFile_OpenCode"); exit(-1);
}
PyAPI_FUNC(PyObject*) PyFile_OpenCodeObject(PyObject* a) {
    unimplemented("PyFile_OpenCodeObject"); exit(-1);
}
PyAPI_FUNC(int) PyFile_SetOpenCodeHook(Py_OpenCodeHookFunction a, void* b) {
    unimplemented("PyFile_SetOpenCodeHook"); exit(-1);
}
STATS_CONTAINER(PyFile_WriteObject, PyException_SetTraceback)
int (*__target__PyFile_WriteObject)(PyObject*, PyObject*, int) = NULL;
PyAPI_FUNC(int) PyFile_WriteObject(PyObject* a, PyObject* b, int c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    STATS_BEFORE(PyFile_WriteObject)
    int result = (int) __target__PyFile_WriteObject(a, b, c);
    STATS_AFTER(PyFile_WriteObject)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyFile_WriteString, PyFile_WriteObject)
int (*__target__PyFile_WriteString)(const char*, PyObject*) = NULL;
PyAPI_FUNC(int) PyFile_WriteString(const char* a, PyObject* b) {
    LOG("'%s'(0x%lx) 0x%lx", a?a:"<null>", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(PyFile_WriteString)
    int result = (int) __target__PyFile_WriteString(a, b);
    STATS_AFTER(PyFile_WriteString)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyFloat_AsDouble, PyFile_WriteString)
double (*__target__PyFloat_AsDouble)(PyObject*) = NULL;
PyAPI_FUNC(double) PyFloat_AsDouble(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyFloat_AsDouble)
    double result = (double) __target__PyFloat_AsDouble(a);
    STATS_AFTER(PyFloat_AsDouble)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyFloat_FromDouble, PyFloat_AsDouble)
PyObject* (*__target__PyFloat_FromDouble)(double) = NULL;
PyAPI_FUNC(PyObject*) PyFloat_FromDouble(double a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyFloat_FromDouble)
    PyObject* result = (PyObject*) __target__PyFloat_FromDouble(a);
    STATS_AFTER(PyFloat_FromDouble)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(PyObject*) PyFloat_FromString(PyObject* a) {
    unimplemented("PyFloat_FromString"); exit(-1);
}
PyAPI_FUNC(PyObject*) PyFloat_GetInfo() {
    unimplemented("PyFloat_GetInfo"); exit(-1);
}
PyAPI_FUNC(double) PyFloat_GetMax() {
    unimplemented("PyFloat_GetMax"); exit(-1);
}
PyAPI_FUNC(double) PyFloat_GetMin() {
    unimplemented("PyFloat_GetMin"); exit(-1);
}
PyAPI_FUNC(PyTryBlock*) PyFrame_BlockPop(PyFrameObject* a) {
    unimplemented("PyFrame_BlockPop"); exit(-1);
}
PyAPI_FUNC(void) PyFrame_BlockSetup(PyFrameObject* a, int b, int c, int d) {
    unimplemented("PyFrame_BlockSetup"); exit(-1);
}
PyAPI_FUNC(void) PyFrame_FastToLocals(PyFrameObject* a) {
    unimplemented("PyFrame_FastToLocals"); exit(-1);
}
PyAPI_FUNC(int) PyFrame_FastToLocalsWithError(PyFrameObject* a) {
    unimplemented("PyFrame_FastToLocalsWithError"); exit(-1);
}
PyAPI_FUNC(PyFrameObject*) PyFrame_GetBack(PyFrameObject* a) {
    unimplemented("PyFrame_GetBack"); exit(-1);
}
PyAPI_FUNC(PyCodeObject*) PyFrame_GetCode(PyFrameObject* a) {
    unimplemented("PyFrame_GetCode"); exit(-1);
}
PyAPI_FUNC(int) PyFrame_GetLineNumber(PyFrameObject* a) {
    unimplemented("PyFrame_GetLineNumber"); exit(-1);
}
PyAPI_FUNC(void) PyFrame_LocalsToFast(PyFrameObject* a, int b) {
    unimplemented("PyFrame_LocalsToFast"); exit(-1);
}
STATS_CONTAINER(PyFrame_New, PyFloat_FromDouble)
PyFrameObject* (*__target__PyFrame_New)(PyThreadState*, PyCodeObject*, PyObject*, PyObject*) = NULL;
PyAPI_FUNC(PyFrameObject*) PyFrame_New(PyThreadState* a, PyCodeObject* b, PyObject* c, PyObject* d) {
    LOG("0x%lx 0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c, (unsigned long) d);
    STATS_BEFORE(PyFrame_New)
    PyFrameObject* result = (PyFrameObject*) __target__PyFrame_New(a, b, c, d);
    STATS_AFTER(PyFrame_New)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyFrozenSet_New, PyFrame_New)
PyObject* (*__target__PyFrozenSet_New)(PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyFrozenSet_New(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyFrozenSet_New)
    PyObject* result = (PyObject*) __target__PyFrozenSet_New(a);
    STATS_AFTER(PyFrozenSet_New)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(PyObject*) PyFunction_GetAnnotations(PyObject* a) {
    unimplemented("PyFunction_GetAnnotations"); exit(-1);
}
PyAPI_FUNC(PyObject*) PyFunction_GetClosure(PyObject* a) {
    unimplemented("PyFunction_GetClosure"); exit(-1);
}
PyAPI_FUNC(PyObject*) PyFunction_GetCode(PyObject* a) {
    unimplemented("PyFunction_GetCode"); exit(-1);
}
PyAPI_FUNC(PyObject*) PyFunction_GetDefaults(PyObject* a) {
    unimplemented("PyFunction_GetDefaults"); exit(-1);
}
PyAPI_FUNC(PyObject*) PyFunction_GetGlobals(PyObject* a) {
    unimplemented("PyFunction_GetGlobals"); exit(-1);
}
PyAPI_FUNC(PyObject*) PyFunction_GetKwDefaults(PyObject* a) {
    unimplemented("PyFunction_GetKwDefaults"); exit(-1);
}
PyAPI_FUNC(PyObject*) PyFunction_GetModule(PyObject* a) {
    unimplemented("PyFunction_GetModule"); exit(-1);
}
PyAPI_FUNC(PyObject*) PyFunction_New(PyObject* a, PyObject* b) {
    unimplemented("PyFunction_New"); exit(-1);
}
PyAPI_FUNC(PyObject*) PyFunction_NewWithQualName(PyObject* a, PyObject* b, PyObject* c) {
    unimplemented("PyFunction_NewWithQualName"); exit(-1);
}
PyAPI_FUNC(int) PyFunction_SetAnnotations(PyObject* a, PyObject* b) {
    unimplemented("PyFunction_SetAnnotations"); exit(-1);
}
PyAPI_FUNC(int) PyFunction_SetClosure(PyObject* a, PyObject* b) {
    unimplemented("PyFunction_SetClosure"); exit(-1);
}
PyAPI_FUNC(int) PyFunction_SetDefaults(PyObject* a, PyObject* b) {
    unimplemented("PyFunction_SetDefaults"); exit(-1);
}
PyAPI_FUNC(int) PyFunction_SetKwDefaults(PyObject* a, PyObject* b) {
    unimplemented("PyFunction_SetKwDefaults"); exit(-1);
}
PyAPI_FUNC(Py_ssize_t) PyGC_Collect() {
    unimplemented("PyGC_Collect"); exit(-1);
}
PyAPI_FUNC(int) PyGC_Disable() {
    unimplemented("PyGC_Disable"); exit(-1);
}
PyAPI_FUNC(int) PyGC_Enable() {
    unimplemented("PyGC_Enable"); exit(-1);
}
PyAPI_FUNC(int) PyGC_IsEnabled() {
    unimplemented("PyGC_IsEnabled"); exit(-1);
}
PyAPI_FUNC(int) PyGILState_Check() {
    unimplemented("PyGILState_Check"); exit(-1);
}
STATS_CONTAINER(PyGILState_Ensure, PyFrozenSet_New)
PyGILState_STATE (*__target__PyGILState_Ensure)() = NULL;
PyAPI_FUNC(PyGILState_STATE) PyGILState_Ensure() {
    LOGS("");
    STATS_BEFORE(PyGILState_Ensure)
    PyGILState_STATE result = (PyGILState_STATE) __target__PyGILState_Ensure();
    STATS_AFTER(PyGILState_Ensure)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyGILState_GetThisThreadState, PyGILState_Ensure)
PyThreadState* (*__target__PyGILState_GetThisThreadState)() = NULL;
PyAPI_FUNC(PyThreadState*) PyGILState_GetThisThreadState() {
    LOGS("");
    STATS_BEFORE(PyGILState_GetThisThreadState)
    PyThreadState* result = (PyThreadState*) __target__PyGILState_GetThisThreadState();
    STATS_AFTER(PyGILState_GetThisThreadState)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyGILState_Release, PyGILState_GetThisThreadState)
void (*__target__PyGILState_Release)(PyGILState_STATE) = NULL;
PyAPI_FUNC(void) PyGILState_Release(PyGILState_STATE a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyGILState_Release)
    __target__PyGILState_Release(a);
    STATS_AFTER(PyGILState_Release)
    LOG_AFTER_VOID
}
STATS_CONTAINER(PyGen_New, PyGILState_Release)
PyObject* (*__target__PyGen_New)(PyFrameObject*) = NULL;
PyAPI_FUNC(PyObject*) PyGen_New(PyFrameObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyGen_New)
    PyObject* result = (PyObject*) __target__PyGen_New(a);
    STATS_AFTER(PyGen_New)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyGen_NewWithQualName, PyGen_New)
PyObject* (*__target__PyGen_NewWithQualName)(PyFrameObject*, PyObject*, PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyGen_NewWithQualName(PyFrameObject* a, PyObject* b, PyObject* c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    STATS_BEFORE(PyGen_NewWithQualName)
    PyObject* result = (PyObject*) __target__PyGen_NewWithQualName(a, b, c);
    STATS_AFTER(PyGen_NewWithQualName)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyImport_AddModule, PyGen_NewWithQualName)
PyObject* (*__target__PyImport_AddModule)(const char*) = NULL;
PyAPI_FUNC(PyObject*) PyImport_AddModule(const char* a) {
    LOG("'%s'(0x%lx)", a?a:"<null>", (unsigned long) a);
    STATS_BEFORE(PyImport_AddModule)
    PyObject* result = (PyObject*) __target__PyImport_AddModule(a);
    STATS_AFTER(PyImport_AddModule)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyImport_AddModuleObject, PyImport_AddModule)
PyObject* (*__target__PyImport_AddModuleObject)(PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyImport_AddModuleObject(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyImport_AddModuleObject)
    PyObject* result = (PyObject*) __target__PyImport_AddModuleObject(a);
    STATS_AFTER(PyImport_AddModuleObject)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(int) PyImport_AppendInittab(const char* a, PyObject*(*b)(void)) {
    unimplemented("PyImport_AppendInittab"); exit(-1);
}
PyAPI_FUNC(PyObject*) PyImport_ExecCodeModule(const char* a, PyObject* b) {
    unimplemented("PyImport_ExecCodeModule"); exit(-1);
}
PyAPI_FUNC(PyObject*) PyImport_ExecCodeModuleEx(const char* a, PyObject* b, const char* c) {
    unimplemented("PyImport_ExecCodeModuleEx"); exit(-1);
}
PyAPI_FUNC(PyObject*) PyImport_ExecCodeModuleObject(PyObject* a, PyObject* b, PyObject* c, PyObject* d) {
    unimplemented("PyImport_ExecCodeModuleObject"); exit(-1);
}
PyAPI_FUNC(PyObject*) PyImport_ExecCodeModuleWithPathnames(const char* a, PyObject* b, const char* c, const char* d) {
    unimplemented("PyImport_ExecCodeModuleWithPathnames"); exit(-1);
}
PyAPI_FUNC(int) PyImport_ExtendInittab(struct _inittab* a) {
    unimplemented("PyImport_ExtendInittab"); exit(-1);
}
PyAPI_FUNC(PyObject*) PyImport_GetImporter(PyObject* a) {
    unimplemented("PyImport_GetImporter"); exit(-1);
}
PyAPI_FUNC(long) PyImport_GetMagicNumber() {
    unimplemented("PyImport_GetMagicNumber"); exit(-1);
}
PyAPI_FUNC(const char*) PyImport_GetMagicTag() {
    unimplemented("PyImport_GetMagicTag"); exit(-1);
}
PyAPI_FUNC(PyObject*) PyImport_GetModule(PyObject* a) {
    unimplemented("PyImport_GetModule"); exit(-1);
}
STATS_CONTAINER(PyImport_GetModuleDict, PyImport_AddModuleObject)
PyObject* (*__target__PyImport_GetModuleDict)() = NULL;
PyAPI_FUNC(PyObject*) PyImport_GetModuleDict() {
    LOGS("");
    STATS_BEFORE(PyImport_GetModuleDict)
    PyObject* result = (PyObject*) __target__PyImport_GetModuleDict();
    STATS_AFTER(PyImport_GetModuleDict)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyImport_Import, PyImport_GetModuleDict)
PyObject* (*__target__PyImport_Import)(PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyImport_Import(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyImport_Import)
    PyObject* result = (PyObject*) __target__PyImport_Import(a);
    STATS_AFTER(PyImport_Import)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(int) PyImport_ImportFrozenModule(const char* a) {
    unimplemented("PyImport_ImportFrozenModule"); exit(-1);
}
PyAPI_FUNC(int) PyImport_ImportFrozenModuleObject(PyObject* a) {
    unimplemented("PyImport_ImportFrozenModuleObject"); exit(-1);
}
STATS_CONTAINER(PyImport_ImportModule, PyImport_Import)
PyObject* (*__target__PyImport_ImportModule)(const char*) = NULL;
PyAPI_FUNC(PyObject*) PyImport_ImportModule(const char* a) {
    LOG("'%s'(0x%lx)", a?a:"<null>", (unsigned long) a);
    STATS_BEFORE(PyImport_ImportModule)
    PyObject* result = (PyObject*) __target__PyImport_ImportModule(a);
    STATS_AFTER(PyImport_ImportModule)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyImport_ImportModuleLevel, PyImport_ImportModule)
PyObject* (*__target__PyImport_ImportModuleLevel)(const char*, PyObject*, PyObject*, PyObject*, int) = NULL;
PyAPI_FUNC(PyObject*) PyImport_ImportModuleLevel(const char* a, PyObject* b, PyObject* c, PyObject* d, int e) {
    LOG("'%s'(0x%lx) 0x%lx 0x%lx 0x%lx 0x%lx", a?a:"<null>", (unsigned long) a, (unsigned long) b, (unsigned long) c, (unsigned long) d, (unsigned long) e);
    STATS_BEFORE(PyImport_ImportModuleLevel)
    PyObject* result = (PyObject*) __target__PyImport_ImportModuleLevel(a, b, c, d, e);
    STATS_AFTER(PyImport_ImportModuleLevel)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyImport_ImportModuleLevelObject, PyImport_ImportModuleLevel)
PyObject* (*__target__PyImport_ImportModuleLevelObject)(PyObject*, PyObject*, PyObject*, PyObject*, int) = NULL;
PyAPI_FUNC(PyObject*) PyImport_ImportModuleLevelObject(PyObject* a, PyObject* b, PyObject* c, PyObject* d, int e) {
    LOG("0x%lx 0x%lx 0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c, (unsigned long) d, (unsigned long) e);
    STATS_BEFORE(PyImport_ImportModuleLevelObject)
    PyObject* result = (PyObject*) __target__PyImport_ImportModuleLevelObject(a, b, c, d, e);
    STATS_AFTER(PyImport_ImportModuleLevelObject)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyImport_ImportModuleNoBlock, PyImport_ImportModuleLevelObject)
PyObject* (*__target__PyImport_ImportModuleNoBlock)(const char*) = NULL;
PyAPI_FUNC(PyObject*) PyImport_ImportModuleNoBlock(const char* a) {
    LOG("'%s'(0x%lx)", a?a:"<null>", (unsigned long) a);
    STATS_BEFORE(PyImport_ImportModuleNoBlock)
    PyObject* result = (PyObject*) __target__PyImport_ImportModuleNoBlock(a);
    STATS_AFTER(PyImport_ImportModuleNoBlock)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(PyObject*) PyImport_ReloadModule(PyObject* a) {
    unimplemented("PyImport_ReloadModule"); exit(-1);
}
STATS_CONTAINER(PyIndex_Check, PyImport_ImportModuleNoBlock)
int (*__target__PyIndex_Check)(PyObject*) = NULL;
PyAPI_FUNC(int) PyIndex_Check(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyIndex_Check)
    int result = (int) __target__PyIndex_Check(a);
    STATS_AFTER(PyIndex_Check)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(PyObject*) PyInit__imp() {
    unimplemented("PyInit__imp"); exit(-1);
}
STATS_CONTAINER(PyInstanceMethod_Function, PyIndex_Check)
PyObject* (*__target__PyInstanceMethod_Function)(PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyInstanceMethod_Function(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyInstanceMethod_Function)
    PyObject* result = (PyObject*) __target__PyInstanceMethod_Function(a);
    STATS_AFTER(PyInstanceMethod_Function)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyInstanceMethod_New, PyInstanceMethod_Function)
PyObject* (*__target__PyInstanceMethod_New)(PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyInstanceMethod_New(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyInstanceMethod_New)
    PyObject* result = (PyObject*) __target__PyInstanceMethod_New(a);
    STATS_AFTER(PyInstanceMethod_New)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(void) PyInterpreterState_Clear(PyInterpreterState* a) {
    unimplemented("PyInterpreterState_Clear"); exit(-1);
}
PyAPI_FUNC(void) PyInterpreterState_Delete(PyInterpreterState* a) {
    unimplemented("PyInterpreterState_Delete"); exit(-1);
}
PyAPI_FUNC(PyInterpreterState*) PyInterpreterState_Get() {
    unimplemented("PyInterpreterState_Get"); exit(-1);
}
PyAPI_FUNC(PyObject*) PyInterpreterState_GetDict(PyInterpreterState* a) {
    unimplemented("PyInterpreterState_GetDict"); exit(-1);
}
STATS_CONTAINER(PyInterpreterState_GetID, PyInstanceMethod_New)
int64_t (*__target__PyInterpreterState_GetID)(PyInterpreterState*) = NULL;
PyAPI_FUNC(int64_t) PyInterpreterState_GetID(PyInterpreterState* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyInterpreterState_GetID)
    int64_t result = (int64_t) __target__PyInterpreterState_GetID(a);
    STATS_AFTER(PyInterpreterState_GetID)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyInterpreterState_GetIDFromThreadState, PyInterpreterState_GetID)
int64_t (*__target__PyInterpreterState_GetIDFromThreadState)(PyThreadState*) = NULL;
PyAPI_FUNC(int64_t) PyInterpreterState_GetIDFromThreadState(PyThreadState* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyInterpreterState_GetIDFromThreadState)
    int64_t result = (int64_t) __target__PyInterpreterState_GetIDFromThreadState(a);
    STATS_AFTER(PyInterpreterState_GetIDFromThreadState)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(PyInterpreterState*) PyInterpreterState_Head() {
    unimplemented("PyInterpreterState_Head"); exit(-1);
}
STATS_CONTAINER(PyInterpreterState_Main, PyInterpreterState_GetIDFromThreadState)
PyInterpreterState* (*__target__PyInterpreterState_Main)() = NULL;
PyAPI_FUNC(PyInterpreterState*) PyInterpreterState_Main() {
    LOGS("");
    STATS_BEFORE(PyInterpreterState_Main)
    PyInterpreterState* result = (PyInterpreterState*) __target__PyInterpreterState_Main();
    STATS_AFTER(PyInterpreterState_Main)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(PyInterpreterState*) PyInterpreterState_New() {
    unimplemented("PyInterpreterState_New"); exit(-1);
}
PyAPI_FUNC(PyInterpreterState*) PyInterpreterState_Next(PyInterpreterState* a) {
    unimplemented("PyInterpreterState_Next"); exit(-1);
}
PyAPI_FUNC(PyThreadState*) PyInterpreterState_ThreadHead(PyInterpreterState* a) {
    unimplemented("PyInterpreterState_ThreadHead"); exit(-1);
}
STATS_CONTAINER(PyIter_Check, PyInterpreterState_Main)
int (*__target__PyIter_Check)(PyObject*) = NULL;
PyAPI_FUNC(int) PyIter_Check(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyIter_Check)
    int result = (int) __target__PyIter_Check(a);
    STATS_AFTER(PyIter_Check)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyIter_Next, PyIter_Check)
PyObject* (*__target__PyIter_Next)(PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyIter_Next(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyIter_Next)
    PyObject* result = (PyObject*) __target__PyIter_Next(a);
    STATS_AFTER(PyIter_Next)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(PySendResult) PyIter_Send(PyObject* a, PyObject* b, PyObject** c) {
    unimplemented("PyIter_Send"); exit(-1);
}
PyAPI_FUNC(void) PyLineTable_InitAddressRange(const char* a, Py_ssize_t b, int c, PyCodeAddressRange* d) {
    unimplemented("PyLineTable_InitAddressRange"); exit(-1);
}
PyAPI_FUNC(int) PyLineTable_NextAddressRange(PyCodeAddressRange* a) {
    unimplemented("PyLineTable_NextAddressRange"); exit(-1);
}
PyAPI_FUNC(int) PyLineTable_PreviousAddressRange(PyCodeAddressRange* a) {
    unimplemented("PyLineTable_PreviousAddressRange"); exit(-1);
}
STATS_CONTAINER(PyList_Append, PyIter_Next)
int (*__target__PyList_Append)(PyObject*, PyObject*) = NULL;
PyAPI_FUNC(int) PyList_Append(PyObject* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(PyList_Append)
    int result = (int) __target__PyList_Append(a, b);
    STATS_AFTER(PyList_Append)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyList_AsTuple, PyList_Append)
PyObject* (*__target__PyList_AsTuple)(PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyList_AsTuple(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyList_AsTuple)
    PyObject* result = (PyObject*) __target__PyList_AsTuple(a);
    STATS_AFTER(PyList_AsTuple)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyList_GetItem, PyList_AsTuple)
PyObject* (*__target__PyList_GetItem)(PyObject*, Py_ssize_t) = NULL;
PyAPI_FUNC(PyObject*) PyList_GetItem(PyObject* a, Py_ssize_t b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(PyList_GetItem)
    PyObject* result = (PyObject*) __target__PyList_GetItem(a, b);
    STATS_AFTER(PyList_GetItem)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyList_GetSlice, PyList_GetItem)
PyObject* (*__target__PyList_GetSlice)(PyObject*, Py_ssize_t, Py_ssize_t) = NULL;
PyAPI_FUNC(PyObject*) PyList_GetSlice(PyObject* a, Py_ssize_t b, Py_ssize_t c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    STATS_BEFORE(PyList_GetSlice)
    PyObject* result = (PyObject*) __target__PyList_GetSlice(a, b, c);
    STATS_AFTER(PyList_GetSlice)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyList_Insert, PyList_GetSlice)
int (*__target__PyList_Insert)(PyObject*, Py_ssize_t, PyObject*) = NULL;
PyAPI_FUNC(int) PyList_Insert(PyObject* a, Py_ssize_t b, PyObject* c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    STATS_BEFORE(PyList_Insert)
    int result = (int) __target__PyList_Insert(a, b, c);
    STATS_AFTER(PyList_Insert)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyList_New, PyList_Insert)
PyObject* (*__target__PyList_New)(Py_ssize_t) = NULL;
PyAPI_FUNC(PyObject*) PyList_New(Py_ssize_t a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyList_New)
    PyObject* result = (PyObject*) __target__PyList_New(a);
    STATS_AFTER(PyList_New)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyList_Reverse, PyList_New)
int (*__target__PyList_Reverse)(PyObject*) = NULL;
PyAPI_FUNC(int) PyList_Reverse(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyList_Reverse)
    int result = (int) __target__PyList_Reverse(a);
    STATS_AFTER(PyList_Reverse)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyList_SetItem, PyList_Reverse)
int (*__target__PyList_SetItem)(PyObject*, Py_ssize_t, PyObject*) = NULL;
PyAPI_FUNC(int) PyList_SetItem(PyObject* a, Py_ssize_t b, PyObject* c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    STATS_BEFORE(PyList_SetItem)
    int result = (int) __target__PyList_SetItem(a, b, c);
    STATS_AFTER(PyList_SetItem)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyList_SetSlice, PyList_SetItem)
int (*__target__PyList_SetSlice)(PyObject*, Py_ssize_t, Py_ssize_t, PyObject*) = NULL;
PyAPI_FUNC(int) PyList_SetSlice(PyObject* a, Py_ssize_t b, Py_ssize_t c, PyObject* d) {
    LOG("0x%lx 0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c, (unsigned long) d);
    STATS_BEFORE(PyList_SetSlice)
    int result = (int) __target__PyList_SetSlice(a, b, c, d);
    STATS_AFTER(PyList_SetSlice)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyList_Size, PyList_SetSlice)
Py_ssize_t (*__target__PyList_Size)(PyObject*) = NULL;
PyAPI_FUNC(Py_ssize_t) PyList_Size(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyList_Size)
    Py_ssize_t result = (Py_ssize_t) __target__PyList_Size(a);
    STATS_AFTER(PyList_Size)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyList_Sort, PyList_Size)
int (*__target__PyList_Sort)(PyObject*) = NULL;
PyAPI_FUNC(int) PyList_Sort(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyList_Sort)
    int result = (int) __target__PyList_Sort(a);
    STATS_AFTER(PyList_Sort)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyLong_AsDouble, PyList_Sort)
double (*__target__PyLong_AsDouble)(PyObject*) = NULL;
PyAPI_FUNC(double) PyLong_AsDouble(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyLong_AsDouble)
    double result = (double) __target__PyLong_AsDouble(a);
    STATS_AFTER(PyLong_AsDouble)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyLong_AsLong, PyLong_AsDouble)
long (*__target__PyLong_AsLong)(PyObject*) = NULL;
PyAPI_FUNC(long) PyLong_AsLong(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyLong_AsLong)
    long result = (long) __target__PyLong_AsLong(a);
    STATS_AFTER(PyLong_AsLong)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyLong_AsLongAndOverflow, PyLong_AsLong)
long (*__target__PyLong_AsLongAndOverflow)(PyObject*, int*) = NULL;
PyAPI_FUNC(long) PyLong_AsLongAndOverflow(PyObject* a, int* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(PyLong_AsLongAndOverflow)
    long result = (long) __target__PyLong_AsLongAndOverflow(a, b);
    STATS_AFTER(PyLong_AsLongAndOverflow)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyLong_AsLongLong, PyLong_AsLongAndOverflow)
long long (*__target__PyLong_AsLongLong)(PyObject*) = NULL;
PyAPI_FUNC(long long) PyLong_AsLongLong(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyLong_AsLongLong)
    long long result = (long long) __target__PyLong_AsLongLong(a);
    STATS_AFTER(PyLong_AsLongLong)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyLong_AsLongLongAndOverflow, PyLong_AsLongLong)
long long (*__target__PyLong_AsLongLongAndOverflow)(PyObject*, int*) = NULL;
PyAPI_FUNC(long long) PyLong_AsLongLongAndOverflow(PyObject* a, int* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(PyLong_AsLongLongAndOverflow)
    long long result = (long long) __target__PyLong_AsLongLongAndOverflow(a, b);
    STATS_AFTER(PyLong_AsLongLongAndOverflow)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyLong_AsSize_t, PyLong_AsLongLongAndOverflow)
size_t (*__target__PyLong_AsSize_t)(PyObject*) = NULL;
PyAPI_FUNC(size_t) PyLong_AsSize_t(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyLong_AsSize_t)
    size_t result = (size_t) __target__PyLong_AsSize_t(a);
    STATS_AFTER(PyLong_AsSize_t)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyLong_AsSsize_t, PyLong_AsSize_t)
Py_ssize_t (*__target__PyLong_AsSsize_t)(PyObject*) = NULL;
PyAPI_FUNC(Py_ssize_t) PyLong_AsSsize_t(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyLong_AsSsize_t)
    Py_ssize_t result = (Py_ssize_t) __target__PyLong_AsSsize_t(a);
    STATS_AFTER(PyLong_AsSsize_t)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyLong_AsUnsignedLong, PyLong_AsSsize_t)
unsigned long (*__target__PyLong_AsUnsignedLong)(PyObject*) = NULL;
PyAPI_FUNC(unsigned long) PyLong_AsUnsignedLong(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyLong_AsUnsignedLong)
    unsigned long result = (unsigned long) __target__PyLong_AsUnsignedLong(a);
    STATS_AFTER(PyLong_AsUnsignedLong)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyLong_AsUnsignedLongLong, PyLong_AsUnsignedLong)
unsigned long long (*__target__PyLong_AsUnsignedLongLong)(PyObject*) = NULL;
PyAPI_FUNC(unsigned long long) PyLong_AsUnsignedLongLong(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyLong_AsUnsignedLongLong)
    unsigned long long result = (unsigned long long) __target__PyLong_AsUnsignedLongLong(a);
    STATS_AFTER(PyLong_AsUnsignedLongLong)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyLong_AsUnsignedLongLongMask, PyLong_AsUnsignedLongLong)
unsigned long long (*__target__PyLong_AsUnsignedLongLongMask)(PyObject*) = NULL;
PyAPI_FUNC(unsigned long long) PyLong_AsUnsignedLongLongMask(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyLong_AsUnsignedLongLongMask)
    unsigned long long result = (unsigned long long) __target__PyLong_AsUnsignedLongLongMask(a);
    STATS_AFTER(PyLong_AsUnsignedLongLongMask)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyLong_AsUnsignedLongMask, PyLong_AsUnsignedLongLongMask)
unsigned long (*__target__PyLong_AsUnsignedLongMask)(PyObject*) = NULL;
PyAPI_FUNC(unsigned long) PyLong_AsUnsignedLongMask(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyLong_AsUnsignedLongMask)
    unsigned long result = (unsigned long) __target__PyLong_AsUnsignedLongMask(a);
    STATS_AFTER(PyLong_AsUnsignedLongMask)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyLong_AsVoidPtr, PyLong_AsUnsignedLongMask)
void* (*__target__PyLong_AsVoidPtr)(PyObject*) = NULL;
PyAPI_FUNC(void*) PyLong_AsVoidPtr(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyLong_AsVoidPtr)
    void* result = (void*) __target__PyLong_AsVoidPtr(a);
    STATS_AFTER(PyLong_AsVoidPtr)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyLong_FromDouble, PyLong_AsVoidPtr)
PyObject* (*__target__PyLong_FromDouble)(double) = NULL;
PyAPI_FUNC(PyObject*) PyLong_FromDouble(double a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyLong_FromDouble)
    PyObject* result = (PyObject*) __target__PyLong_FromDouble(a);
    STATS_AFTER(PyLong_FromDouble)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyLong_FromLong, PyLong_FromDouble)
PyObject* (*__target__PyLong_FromLong)(long) = NULL;
PyAPI_FUNC(PyObject*) PyLong_FromLong(long a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyLong_FromLong)
    PyObject* result = (PyObject*) __target__PyLong_FromLong(a);
    STATS_AFTER(PyLong_FromLong)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyLong_FromLongLong, PyLong_FromLong)
PyObject* (*__target__PyLong_FromLongLong)(long long) = NULL;
PyAPI_FUNC(PyObject*) PyLong_FromLongLong(long long a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyLong_FromLongLong)
    PyObject* result = (PyObject*) __target__PyLong_FromLongLong(a);
    STATS_AFTER(PyLong_FromLongLong)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyLong_FromSize_t, PyLong_FromLongLong)
PyObject* (*__target__PyLong_FromSize_t)(size_t) = NULL;
PyAPI_FUNC(PyObject*) PyLong_FromSize_t(size_t a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyLong_FromSize_t)
    PyObject* result = (PyObject*) __target__PyLong_FromSize_t(a);
    STATS_AFTER(PyLong_FromSize_t)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyLong_FromSsize_t, PyLong_FromSize_t)
PyObject* (*__target__PyLong_FromSsize_t)(Py_ssize_t) = NULL;
PyAPI_FUNC(PyObject*) PyLong_FromSsize_t(Py_ssize_t a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyLong_FromSsize_t)
    PyObject* result = (PyObject*) __target__PyLong_FromSsize_t(a);
    STATS_AFTER(PyLong_FromSsize_t)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyLong_FromString, PyLong_FromSsize_t)
PyObject* (*__target__PyLong_FromString)(const char*, char**, int) = NULL;
PyAPI_FUNC(PyObject*) PyLong_FromString(const char* a, char** b, int c) {
    LOG("'%s'(0x%lx) 0x%lx 0x%lx", a?a:"<null>", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    STATS_BEFORE(PyLong_FromString)
    PyObject* result = (PyObject*) __target__PyLong_FromString(a, b, c);
    STATS_AFTER(PyLong_FromString)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(PyObject*) PyLong_FromUnicodeObject(PyObject* a, int b) {
    unimplemented("PyLong_FromUnicodeObject"); exit(-1);
}
STATS_CONTAINER(PyLong_FromUnsignedLong, PyLong_FromString)
PyObject* (*__target__PyLong_FromUnsignedLong)(unsigned long) = NULL;
PyAPI_FUNC(PyObject*) PyLong_FromUnsignedLong(unsigned long a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyLong_FromUnsignedLong)
    PyObject* result = (PyObject*) __target__PyLong_FromUnsignedLong(a);
    STATS_AFTER(PyLong_FromUnsignedLong)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyLong_FromUnsignedLongLong, PyLong_FromUnsignedLong)
PyObject* (*__target__PyLong_FromUnsignedLongLong)(unsigned long long) = NULL;
PyAPI_FUNC(PyObject*) PyLong_FromUnsignedLongLong(unsigned long long a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyLong_FromUnsignedLongLong)
    PyObject* result = (PyObject*) __target__PyLong_FromUnsignedLongLong(a);
    STATS_AFTER(PyLong_FromUnsignedLongLong)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyLong_FromVoidPtr, PyLong_FromUnsignedLongLong)
PyObject* (*__target__PyLong_FromVoidPtr)(void*) = NULL;
PyAPI_FUNC(PyObject*) PyLong_FromVoidPtr(void* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyLong_FromVoidPtr)
    PyObject* result = (PyObject*) __target__PyLong_FromVoidPtr(a);
    STATS_AFTER(PyLong_FromVoidPtr)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(PyObject*) PyLong_GetInfo() {
    unimplemented("PyLong_GetInfo"); exit(-1);
}
STATS_CONTAINER(PyMapping_Check, PyLong_FromVoidPtr)
int (*__target__PyMapping_Check)(PyObject*) = NULL;
PyAPI_FUNC(int) PyMapping_Check(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyMapping_Check)
    int result = (int) __target__PyMapping_Check(a);
    STATS_AFTER(PyMapping_Check)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyMapping_GetItemString, PyMapping_Check)
PyObject* (*__target__PyMapping_GetItemString)(PyObject*, const char*) = NULL;
PyAPI_FUNC(PyObject*) PyMapping_GetItemString(PyObject* a, const char* b) {
    LOG("0x%lx '%s'(0x%lx)", (unsigned long) a, b?b:"<null>", (unsigned long) b);
    STATS_BEFORE(PyMapping_GetItemString)
    PyObject* result = (PyObject*) __target__PyMapping_GetItemString(a, b);
    STATS_AFTER(PyMapping_GetItemString)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(int) PyMapping_HasKey(PyObject* a, PyObject* b) {
    unimplemented("PyMapping_HasKey"); exit(-1);
}
PyAPI_FUNC(int) PyMapping_HasKeyString(PyObject* a, const char* b) {
    unimplemented("PyMapping_HasKeyString"); exit(-1);
}
STATS_CONTAINER(PyMapping_Items, PyMapping_GetItemString)
PyObject* (*__target__PyMapping_Items)(PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyMapping_Items(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyMapping_Items)
    PyObject* result = (PyObject*) __target__PyMapping_Items(a);
    STATS_AFTER(PyMapping_Items)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyMapping_Keys, PyMapping_Items)
PyObject* (*__target__PyMapping_Keys)(PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyMapping_Keys(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyMapping_Keys)
    PyObject* result = (PyObject*) __target__PyMapping_Keys(a);
    STATS_AFTER(PyMapping_Keys)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(Py_ssize_t) PyMapping_Length(PyObject* a) {
    unimplemented("PyMapping_Length"); exit(-1);
}
PyAPI_FUNC(int) PyMapping_SetItemString(PyObject* a, const char* b, PyObject* c) {
    unimplemented("PyMapping_SetItemString"); exit(-1);
}
STATS_CONTAINER(PyMapping_Size, PyMapping_Keys)
Py_ssize_t (*__target__PyMapping_Size)(PyObject*) = NULL;
PyAPI_FUNC(Py_ssize_t) PyMapping_Size(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyMapping_Size)
    Py_ssize_t result = (Py_ssize_t) __target__PyMapping_Size(a);
    STATS_AFTER(PyMapping_Size)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyMapping_Values, PyMapping_Size)
PyObject* (*__target__PyMapping_Values)(PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyMapping_Values(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyMapping_Values)
    PyObject* result = (PyObject*) __target__PyMapping_Values(a);
    STATS_AFTER(PyMapping_Values)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyMem_Calloc, PyMapping_Values)
void* (*__target__PyMem_Calloc)(size_t, size_t) = NULL;
PyAPI_FUNC(void*) PyMem_Calloc(size_t a, size_t b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(PyMem_Calloc)
    void* result = (void*) __target__PyMem_Calloc(a, b);
    STATS_AFTER(PyMem_Calloc)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyMem_Free, PyMem_Calloc)
void (*__target__PyMem_Free)(void*) = NULL;
PyAPI_FUNC(void) PyMem_Free(void* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyMem_Free)
    __target__PyMem_Free(a);
    STATS_AFTER(PyMem_Free)
    LOG_AFTER_VOID
}
PyAPI_FUNC(void) PyMem_GetAllocator(PyMemAllocatorDomain a, PyMemAllocatorEx* b) {
    unimplemented("PyMem_GetAllocator"); exit(-1);
}
STATS_CONTAINER(PyMem_Malloc, PyMem_Free)
void* (*__target__PyMem_Malloc)(size_t) = NULL;
PyAPI_FUNC(void*) PyMem_Malloc(size_t a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyMem_Malloc)
    void* result = (void*) __target__PyMem_Malloc(a);
    STATS_AFTER(PyMem_Malloc)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyMem_RawCalloc, PyMem_Malloc)
void* (*__target__PyMem_RawCalloc)(size_t, size_t) = NULL;
PyAPI_FUNC(void*) PyMem_RawCalloc(size_t a, size_t b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(PyMem_RawCalloc)
    void* result = (void*) __target__PyMem_RawCalloc(a, b);
    STATS_AFTER(PyMem_RawCalloc)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyMem_RawFree, PyMem_RawCalloc)
void (*__target__PyMem_RawFree)(void*) = NULL;
PyAPI_FUNC(void) PyMem_RawFree(void* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyMem_RawFree)
    __target__PyMem_RawFree(a);
    STATS_AFTER(PyMem_RawFree)
    LOG_AFTER_VOID
}
STATS_CONTAINER(PyMem_RawMalloc, PyMem_RawFree)
void* (*__target__PyMem_RawMalloc)(size_t) = NULL;
PyAPI_FUNC(void*) PyMem_RawMalloc(size_t a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyMem_RawMalloc)
    void* result = (void*) __target__PyMem_RawMalloc(a);
    STATS_AFTER(PyMem_RawMalloc)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyMem_RawRealloc, PyMem_RawMalloc)
void* (*__target__PyMem_RawRealloc)(void*, size_t) = NULL;
PyAPI_FUNC(void*) PyMem_RawRealloc(void* a, size_t b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(PyMem_RawRealloc)
    void* result = (void*) __target__PyMem_RawRealloc(a, b);
    STATS_AFTER(PyMem_RawRealloc)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyMem_Realloc, PyMem_RawRealloc)
void* (*__target__PyMem_Realloc)(void*, size_t) = NULL;
PyAPI_FUNC(void*) PyMem_Realloc(void* a, size_t b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(PyMem_Realloc)
    void* result = (void*) __target__PyMem_Realloc(a, b);
    STATS_AFTER(PyMem_Realloc)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(void) PyMem_SetAllocator(PyMemAllocatorDomain a, PyMemAllocatorEx* b) {
    unimplemented("PyMem_SetAllocator"); exit(-1);
}
PyAPI_FUNC(void) PyMem_SetupDebugHooks() {
    unimplemented("PyMem_SetupDebugHooks"); exit(-1);
}
PyAPI_FUNC(PyObject*) PyMember_GetOne(const char* a, struct PyMemberDef* b) {
    unimplemented("PyMember_GetOne"); exit(-1);
}
PyAPI_FUNC(int) PyMember_SetOne(char* a, struct PyMemberDef* b, PyObject* c) {
    unimplemented("PyMember_SetOne"); exit(-1);
}
STATS_CONTAINER(PyMemoryView_FromBuffer, PyMem_Realloc)
PyObject* (*__target__PyMemoryView_FromBuffer)(Py_buffer*) = NULL;
PyAPI_FUNC(PyObject*) PyMemoryView_FromBuffer(Py_buffer* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyMemoryView_FromBuffer)
    PyObject* result = (PyObject*) __target__PyMemoryView_FromBuffer(a);
    STATS_AFTER(PyMemoryView_FromBuffer)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyMemoryView_FromMemory, PyMemoryView_FromBuffer)
PyObject* (*__target__PyMemoryView_FromMemory)(char*, Py_ssize_t, int) = NULL;
PyAPI_FUNC(PyObject*) PyMemoryView_FromMemory(char* a, Py_ssize_t b, int c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    STATS_BEFORE(PyMemoryView_FromMemory)
    PyObject* result = (PyObject*) __target__PyMemoryView_FromMemory(a, b, c);
    STATS_AFTER(PyMemoryView_FromMemory)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyMemoryView_FromObject, PyMemoryView_FromMemory)
PyObject* (*__target__PyMemoryView_FromObject)(PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyMemoryView_FromObject(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyMemoryView_FromObject)
    PyObject* result = (PyObject*) __target__PyMemoryView_FromObject(a);
    STATS_AFTER(PyMemoryView_FromObject)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyMemoryView_GetContiguous, PyMemoryView_FromObject)
PyObject* (*__target__PyMemoryView_GetContiguous)(PyObject*, int, char) = NULL;
PyAPI_FUNC(PyObject*) PyMemoryView_GetContiguous(PyObject* a, int b, char c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    STATS_BEFORE(PyMemoryView_GetContiguous)
    PyObject* result = (PyObject*) __target__PyMemoryView_GetContiguous(a, b, c);
    STATS_AFTER(PyMemoryView_GetContiguous)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyMethodDescrObject_GetMethod, PyMemoryView_GetContiguous)
PyMethodDef* (*__target__PyMethodDescrObject_GetMethod)(PyObject*) = NULL;
PyAPI_FUNC(PyMethodDef*) PyMethodDescrObject_GetMethod(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyMethodDescrObject_GetMethod)
    PyMethodDef* result = (PyMethodDef*) __target__PyMethodDescrObject_GetMethod(a);
    STATS_AFTER(PyMethodDescrObject_GetMethod)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyMethod_Function, PyMethodDescrObject_GetMethod)
PyObject* (*__target__PyMethod_Function)(PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyMethod_Function(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyMethod_Function)
    PyObject* result = (PyObject*) __target__PyMethod_Function(a);
    STATS_AFTER(PyMethod_Function)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyMethod_New, PyMethod_Function)
PyObject* (*__target__PyMethod_New)(PyObject*, PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyMethod_New(PyObject* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(PyMethod_New)
    PyObject* result = (PyObject*) __target__PyMethod_New(a, b);
    STATS_AFTER(PyMethod_New)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyMethod_Self, PyMethod_New)
PyObject* (*__target__PyMethod_Self)(PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyMethod_Self(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyMethod_Self)
    PyObject* result = (PyObject*) __target__PyMethod_Self(a);
    STATS_AFTER(PyMethod_Self)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyModuleDef_Init, PyMethod_Self)
PyObject* (*__target__PyModuleDef_Init)(struct PyModuleDef*) = NULL;
PyAPI_FUNC(PyObject*) PyModuleDef_Init(struct PyModuleDef* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyModuleDef_Init)
    PyObject* result = (PyObject*) __target__PyModuleDef_Init(a);
    STATS_AFTER(PyModuleDef_Init)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyModule_AddFunctions, PyModuleDef_Init)
int (*__target__PyModule_AddFunctions)(PyObject*, PyMethodDef*) = NULL;
PyAPI_FUNC(int) PyModule_AddFunctions(PyObject* a, PyMethodDef* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(PyModule_AddFunctions)
    int result = (int) __target__PyModule_AddFunctions(a, b);
    STATS_AFTER(PyModule_AddFunctions)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyModule_AddIntConstant, PyModule_AddFunctions)
int (*__target__PyModule_AddIntConstant)(PyObject*, const char*, long) = NULL;
PyAPI_FUNC(int) PyModule_AddIntConstant(PyObject* a, const char* b, long c) {
    LOG("0x%lx '%s'(0x%lx) 0x%lx", (unsigned long) a, b?b:"<null>", (unsigned long) b, (unsigned long) c);
    STATS_BEFORE(PyModule_AddIntConstant)
    int result = (int) __target__PyModule_AddIntConstant(a, b, c);
    STATS_AFTER(PyModule_AddIntConstant)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyModule_AddObjectRef, PyModule_AddIntConstant)
int (*__target__PyModule_AddObjectRef)(PyObject*, const char*, PyObject*) = NULL;
PyAPI_FUNC(int) PyModule_AddObjectRef(PyObject* a, const char* b, PyObject* c) {
    LOG("0x%lx '%s'(0x%lx) 0x%lx", (unsigned long) a, b?b:"<null>", (unsigned long) b, (unsigned long) c);
    STATS_BEFORE(PyModule_AddObjectRef)
    int result = (int) __target__PyModule_AddObjectRef(a, b, c);
    STATS_AFTER(PyModule_AddObjectRef)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyModule_Create2, PyModule_AddObjectRef)
PyObject* (*__target__PyModule_Create2)(struct PyModuleDef*, int) = NULL;
PyAPI_FUNC(PyObject*) PyModule_Create2(struct PyModuleDef* a, int b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(PyModule_Create2)
    PyObject* result = (PyObject*) __target__PyModule_Create2(a, b);
    STATS_AFTER(PyModule_Create2)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(int) PyModule_ExecDef(PyObject* a, PyModuleDef* b) {
    unimplemented("PyModule_ExecDef"); exit(-1);
}
PyAPI_FUNC(PyObject*) PyModule_FromDefAndSpec2(PyModuleDef* a, PyObject* b, int c) {
    unimplemented("PyModule_FromDefAndSpec2"); exit(-1);
}
STATS_CONTAINER(PyModule_GetDef, PyModule_Create2)
PyModuleDef* (*__target__PyModule_GetDef)(PyObject*) = NULL;
PyAPI_FUNC(PyModuleDef*) PyModule_GetDef(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyModule_GetDef)
    PyModuleDef* result = (PyModuleDef*) __target__PyModule_GetDef(a);
    STATS_AFTER(PyModule_GetDef)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyModule_GetDict, PyModule_GetDef)
PyObject* (*__target__PyModule_GetDict)(PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyModule_GetDict(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyModule_GetDict)
    PyObject* result = (PyObject*) __target__PyModule_GetDict(a);
    STATS_AFTER(PyModule_GetDict)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(const char*) PyModule_GetFilename(PyObject* a) {
    unimplemented("PyModule_GetFilename"); exit(-1);
}
PyAPI_FUNC(PyObject*) PyModule_GetFilenameObject(PyObject* a) {
    unimplemented("PyModule_GetFilenameObject"); exit(-1);
}
STATS_CONTAINER(PyModule_GetName, PyModule_GetDict)
const char* (*__target__PyModule_GetName)(PyObject*) = NULL;
PyAPI_FUNC(const char*) PyModule_GetName(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyModule_GetName)
    const char* result = (const char*) __target__PyModule_GetName(a);
    STATS_AFTER(PyModule_GetName)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyModule_GetNameObject, PyModule_GetName)
PyObject* (*__target__PyModule_GetNameObject)(PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyModule_GetNameObject(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyModule_GetNameObject)
    PyObject* result = (PyObject*) __target__PyModule_GetNameObject(a);
    STATS_AFTER(PyModule_GetNameObject)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyModule_GetState, PyModule_GetNameObject)
void* (*__target__PyModule_GetState)(PyObject*) = NULL;
PyAPI_FUNC(void*) PyModule_GetState(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyModule_GetState)
    void* result = (void*) __target__PyModule_GetState(a);
    STATS_AFTER(PyModule_GetState)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyModule_New, PyModule_GetState)
PyObject* (*__target__PyModule_New)(const char*) = NULL;
PyAPI_FUNC(PyObject*) PyModule_New(const char* a) {
    LOG("'%s'(0x%lx)", a?a:"<null>", (unsigned long) a);
    STATS_BEFORE(PyModule_New)
    PyObject* result = (PyObject*) __target__PyModule_New(a);
    STATS_AFTER(PyModule_New)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyModule_NewObject, PyModule_New)
PyObject* (*__target__PyModule_NewObject)(PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyModule_NewObject(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyModule_NewObject)
    PyObject* result = (PyObject*) __target__PyModule_NewObject(a);
    STATS_AFTER(PyModule_NewObject)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyModule_SetDocString, PyModule_NewObject)
int (*__target__PyModule_SetDocString)(PyObject*, const char*) = NULL;
PyAPI_FUNC(int) PyModule_SetDocString(PyObject* a, const char* b) {
    LOG("0x%lx '%s'(0x%lx)", (unsigned long) a, b?b:"<null>", (unsigned long) b);
    STATS_BEFORE(PyModule_SetDocString)
    int result = (int) __target__PyModule_SetDocString(a, b);
    STATS_AFTER(PyModule_SetDocString)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyNumber_Absolute, PyModule_SetDocString)
PyObject* (*__target__PyNumber_Absolute)(PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyNumber_Absolute(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyNumber_Absolute)
    PyObject* result = (PyObject*) __target__PyNumber_Absolute(a);
    STATS_AFTER(PyNumber_Absolute)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyNumber_Add, PyNumber_Absolute)
PyObject* (*__target__PyNumber_Add)(PyObject*, PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyNumber_Add(PyObject* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(PyNumber_Add)
    PyObject* result = (PyObject*) __target__PyNumber_Add(a, b);
    STATS_AFTER(PyNumber_Add)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyNumber_And, PyNumber_Add)
PyObject* (*__target__PyNumber_And)(PyObject*, PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyNumber_And(PyObject* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(PyNumber_And)
    PyObject* result = (PyObject*) __target__PyNumber_And(a, b);
    STATS_AFTER(PyNumber_And)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyNumber_AsSsize_t, PyNumber_And)
Py_ssize_t (*__target__PyNumber_AsSsize_t)(PyObject*, PyObject*) = NULL;
PyAPI_FUNC(Py_ssize_t) PyNumber_AsSsize_t(PyObject* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(PyNumber_AsSsize_t)
    Py_ssize_t result = (Py_ssize_t) __target__PyNumber_AsSsize_t(a, b);
    STATS_AFTER(PyNumber_AsSsize_t)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyNumber_Check, PyNumber_AsSsize_t)
int (*__target__PyNumber_Check)(PyObject*) = NULL;
PyAPI_FUNC(int) PyNumber_Check(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyNumber_Check)
    int result = (int) __target__PyNumber_Check(a);
    STATS_AFTER(PyNumber_Check)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyNumber_Divmod, PyNumber_Check)
PyObject* (*__target__PyNumber_Divmod)(PyObject*, PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyNumber_Divmod(PyObject* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(PyNumber_Divmod)
    PyObject* result = (PyObject*) __target__PyNumber_Divmod(a, b);
    STATS_AFTER(PyNumber_Divmod)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyNumber_Float, PyNumber_Divmod)
PyObject* (*__target__PyNumber_Float)(PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyNumber_Float(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyNumber_Float)
    PyObject* result = (PyObject*) __target__PyNumber_Float(a);
    STATS_AFTER(PyNumber_Float)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyNumber_FloorDivide, PyNumber_Float)
PyObject* (*__target__PyNumber_FloorDivide)(PyObject*, PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyNumber_FloorDivide(PyObject* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(PyNumber_FloorDivide)
    PyObject* result = (PyObject*) __target__PyNumber_FloorDivide(a, b);
    STATS_AFTER(PyNumber_FloorDivide)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyNumber_InPlaceAdd, PyNumber_FloorDivide)
PyObject* (*__target__PyNumber_InPlaceAdd)(PyObject*, PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyNumber_InPlaceAdd(PyObject* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(PyNumber_InPlaceAdd)
    PyObject* result = (PyObject*) __target__PyNumber_InPlaceAdd(a, b);
    STATS_AFTER(PyNumber_InPlaceAdd)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyNumber_InPlaceAnd, PyNumber_InPlaceAdd)
PyObject* (*__target__PyNumber_InPlaceAnd)(PyObject*, PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyNumber_InPlaceAnd(PyObject* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(PyNumber_InPlaceAnd)
    PyObject* result = (PyObject*) __target__PyNumber_InPlaceAnd(a, b);
    STATS_AFTER(PyNumber_InPlaceAnd)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyNumber_InPlaceFloorDivide, PyNumber_InPlaceAnd)
PyObject* (*__target__PyNumber_InPlaceFloorDivide)(PyObject*, PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyNumber_InPlaceFloorDivide(PyObject* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(PyNumber_InPlaceFloorDivide)
    PyObject* result = (PyObject*) __target__PyNumber_InPlaceFloorDivide(a, b);
    STATS_AFTER(PyNumber_InPlaceFloorDivide)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyNumber_InPlaceLshift, PyNumber_InPlaceFloorDivide)
PyObject* (*__target__PyNumber_InPlaceLshift)(PyObject*, PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyNumber_InPlaceLshift(PyObject* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(PyNumber_InPlaceLshift)
    PyObject* result = (PyObject*) __target__PyNumber_InPlaceLshift(a, b);
    STATS_AFTER(PyNumber_InPlaceLshift)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyNumber_InPlaceMatrixMultiply, PyNumber_InPlaceLshift)
PyObject* (*__target__PyNumber_InPlaceMatrixMultiply)(PyObject*, PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyNumber_InPlaceMatrixMultiply(PyObject* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(PyNumber_InPlaceMatrixMultiply)
    PyObject* result = (PyObject*) __target__PyNumber_InPlaceMatrixMultiply(a, b);
    STATS_AFTER(PyNumber_InPlaceMatrixMultiply)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyNumber_InPlaceMultiply, PyNumber_InPlaceMatrixMultiply)
PyObject* (*__target__PyNumber_InPlaceMultiply)(PyObject*, PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyNumber_InPlaceMultiply(PyObject* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(PyNumber_InPlaceMultiply)
    PyObject* result = (PyObject*) __target__PyNumber_InPlaceMultiply(a, b);
    STATS_AFTER(PyNumber_InPlaceMultiply)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyNumber_InPlaceOr, PyNumber_InPlaceMultiply)
PyObject* (*__target__PyNumber_InPlaceOr)(PyObject*, PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyNumber_InPlaceOr(PyObject* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(PyNumber_InPlaceOr)
    PyObject* result = (PyObject*) __target__PyNumber_InPlaceOr(a, b);
    STATS_AFTER(PyNumber_InPlaceOr)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyNumber_InPlacePower, PyNumber_InPlaceOr)
PyObject* (*__target__PyNumber_InPlacePower)(PyObject*, PyObject*, PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyNumber_InPlacePower(PyObject* a, PyObject* b, PyObject* c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    STATS_BEFORE(PyNumber_InPlacePower)
    PyObject* result = (PyObject*) __target__PyNumber_InPlacePower(a, b, c);
    STATS_AFTER(PyNumber_InPlacePower)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyNumber_InPlaceRemainder, PyNumber_InPlacePower)
PyObject* (*__target__PyNumber_InPlaceRemainder)(PyObject*, PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyNumber_InPlaceRemainder(PyObject* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(PyNumber_InPlaceRemainder)
    PyObject* result = (PyObject*) __target__PyNumber_InPlaceRemainder(a, b);
    STATS_AFTER(PyNumber_InPlaceRemainder)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyNumber_InPlaceRshift, PyNumber_InPlaceRemainder)
PyObject* (*__target__PyNumber_InPlaceRshift)(PyObject*, PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyNumber_InPlaceRshift(PyObject* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(PyNumber_InPlaceRshift)
    PyObject* result = (PyObject*) __target__PyNumber_InPlaceRshift(a, b);
    STATS_AFTER(PyNumber_InPlaceRshift)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyNumber_InPlaceSubtract, PyNumber_InPlaceRshift)
PyObject* (*__target__PyNumber_InPlaceSubtract)(PyObject*, PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyNumber_InPlaceSubtract(PyObject* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(PyNumber_InPlaceSubtract)
    PyObject* result = (PyObject*) __target__PyNumber_InPlaceSubtract(a, b);
    STATS_AFTER(PyNumber_InPlaceSubtract)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyNumber_InPlaceTrueDivide, PyNumber_InPlaceSubtract)
PyObject* (*__target__PyNumber_InPlaceTrueDivide)(PyObject*, PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyNumber_InPlaceTrueDivide(PyObject* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(PyNumber_InPlaceTrueDivide)
    PyObject* result = (PyObject*) __target__PyNumber_InPlaceTrueDivide(a, b);
    STATS_AFTER(PyNumber_InPlaceTrueDivide)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyNumber_InPlaceXor, PyNumber_InPlaceTrueDivide)
PyObject* (*__target__PyNumber_InPlaceXor)(PyObject*, PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyNumber_InPlaceXor(PyObject* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(PyNumber_InPlaceXor)
    PyObject* result = (PyObject*) __target__PyNumber_InPlaceXor(a, b);
    STATS_AFTER(PyNumber_InPlaceXor)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyNumber_Index, PyNumber_InPlaceXor)
PyObject* (*__target__PyNumber_Index)(PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyNumber_Index(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyNumber_Index)
    PyObject* result = (PyObject*) __target__PyNumber_Index(a);
    STATS_AFTER(PyNumber_Index)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyNumber_Invert, PyNumber_Index)
PyObject* (*__target__PyNumber_Invert)(PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyNumber_Invert(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyNumber_Invert)
    PyObject* result = (PyObject*) __target__PyNumber_Invert(a);
    STATS_AFTER(PyNumber_Invert)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyNumber_Long, PyNumber_Invert)
PyObject* (*__target__PyNumber_Long)(PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyNumber_Long(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyNumber_Long)
    PyObject* result = (PyObject*) __target__PyNumber_Long(a);
    STATS_AFTER(PyNumber_Long)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyNumber_Lshift, PyNumber_Long)
PyObject* (*__target__PyNumber_Lshift)(PyObject*, PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyNumber_Lshift(PyObject* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(PyNumber_Lshift)
    PyObject* result = (PyObject*) __target__PyNumber_Lshift(a, b);
    STATS_AFTER(PyNumber_Lshift)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyNumber_MatrixMultiply, PyNumber_Lshift)
PyObject* (*__target__PyNumber_MatrixMultiply)(PyObject*, PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyNumber_MatrixMultiply(PyObject* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(PyNumber_MatrixMultiply)
    PyObject* result = (PyObject*) __target__PyNumber_MatrixMultiply(a, b);
    STATS_AFTER(PyNumber_MatrixMultiply)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyNumber_Multiply, PyNumber_MatrixMultiply)
PyObject* (*__target__PyNumber_Multiply)(PyObject*, PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyNumber_Multiply(PyObject* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(PyNumber_Multiply)
    PyObject* result = (PyObject*) __target__PyNumber_Multiply(a, b);
    STATS_AFTER(PyNumber_Multiply)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyNumber_Negative, PyNumber_Multiply)
PyObject* (*__target__PyNumber_Negative)(PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyNumber_Negative(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyNumber_Negative)
    PyObject* result = (PyObject*) __target__PyNumber_Negative(a);
    STATS_AFTER(PyNumber_Negative)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyNumber_Or, PyNumber_Negative)
PyObject* (*__target__PyNumber_Or)(PyObject*, PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyNumber_Or(PyObject* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(PyNumber_Or)
    PyObject* result = (PyObject*) __target__PyNumber_Or(a, b);
    STATS_AFTER(PyNumber_Or)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyNumber_Positive, PyNumber_Or)
PyObject* (*__target__PyNumber_Positive)(PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyNumber_Positive(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyNumber_Positive)
    PyObject* result = (PyObject*) __target__PyNumber_Positive(a);
    STATS_AFTER(PyNumber_Positive)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyNumber_Power, PyNumber_Positive)
PyObject* (*__target__PyNumber_Power)(PyObject*, PyObject*, PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyNumber_Power(PyObject* a, PyObject* b, PyObject* c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    STATS_BEFORE(PyNumber_Power)
    PyObject* result = (PyObject*) __target__PyNumber_Power(a, b, c);
    STATS_AFTER(PyNumber_Power)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyNumber_Remainder, PyNumber_Power)
PyObject* (*__target__PyNumber_Remainder)(PyObject*, PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyNumber_Remainder(PyObject* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(PyNumber_Remainder)
    PyObject* result = (PyObject*) __target__PyNumber_Remainder(a, b);
    STATS_AFTER(PyNumber_Remainder)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyNumber_Rshift, PyNumber_Remainder)
PyObject* (*__target__PyNumber_Rshift)(PyObject*, PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyNumber_Rshift(PyObject* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(PyNumber_Rshift)
    PyObject* result = (PyObject*) __target__PyNumber_Rshift(a, b);
    STATS_AFTER(PyNumber_Rshift)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyNumber_Subtract, PyNumber_Rshift)
PyObject* (*__target__PyNumber_Subtract)(PyObject*, PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyNumber_Subtract(PyObject* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(PyNumber_Subtract)
    PyObject* result = (PyObject*) __target__PyNumber_Subtract(a, b);
    STATS_AFTER(PyNumber_Subtract)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyNumber_ToBase, PyNumber_Subtract)
PyObject* (*__target__PyNumber_ToBase)(PyObject*, int) = NULL;
PyAPI_FUNC(PyObject*) PyNumber_ToBase(PyObject* a, int b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(PyNumber_ToBase)
    PyObject* result = (PyObject*) __target__PyNumber_ToBase(a, b);
    STATS_AFTER(PyNumber_ToBase)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyNumber_TrueDivide, PyNumber_ToBase)
PyObject* (*__target__PyNumber_TrueDivide)(PyObject*, PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyNumber_TrueDivide(PyObject* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(PyNumber_TrueDivide)
    PyObject* result = (PyObject*) __target__PyNumber_TrueDivide(a, b);
    STATS_AFTER(PyNumber_TrueDivide)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyNumber_Xor, PyNumber_TrueDivide)
PyObject* (*__target__PyNumber_Xor)(PyObject*, PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyNumber_Xor(PyObject* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(PyNumber_Xor)
    PyObject* result = (PyObject*) __target__PyNumber_Xor(a, b);
    STATS_AFTER(PyNumber_Xor)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(int) PyODict_DelItem(PyObject* a, PyObject* b) {
    unimplemented("PyODict_DelItem"); exit(-1);
}
PyAPI_FUNC(PyObject*) PyODict_New() {
    unimplemented("PyODict_New"); exit(-1);
}
PyAPI_FUNC(int) PyODict_SetItem(PyObject* a, PyObject* b, PyObject* c) {
    unimplemented("PyODict_SetItem"); exit(-1);
}
PyAPI_FUNC(void) PyOS_AfterFork() {
    unimplemented("PyOS_AfterFork"); exit(-1);
}
PyAPI_FUNC(void) PyOS_AfterFork_Child() {
    unimplemented("PyOS_AfterFork_Child"); exit(-1);
}
PyAPI_FUNC(void) PyOS_AfterFork_Parent() {
    unimplemented("PyOS_AfterFork_Parent"); exit(-1);
}
PyAPI_FUNC(void) PyOS_BeforeFork() {
    unimplemented("PyOS_BeforeFork"); exit(-1);
}
STATS_CONTAINER(PyOS_FSPath, PyNumber_Xor)
PyObject* (*__target__PyOS_FSPath)(PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyOS_FSPath(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyOS_FSPath)
    PyObject* result = (PyObject*) __target__PyOS_FSPath(a);
    STATS_AFTER(PyOS_FSPath)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyOS_InterruptOccurred, PyOS_FSPath)
int (*__target__PyOS_InterruptOccurred)() = NULL;
PyAPI_FUNC(int) PyOS_InterruptOccurred() {
    LOGS("");
    STATS_BEFORE(PyOS_InterruptOccurred)
    int result = (int) __target__PyOS_InterruptOccurred();
    STATS_AFTER(PyOS_InterruptOccurred)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(char*) PyOS_Readline(FILE* a, FILE* b, const char* c) {
    unimplemented("PyOS_Readline"); exit(-1);
}
STATS_CONTAINER(PyOS_double_to_string, PyOS_InterruptOccurred)
char* (*__target__PyOS_double_to_string)(double, char, int, int, int*) = NULL;
PyAPI_FUNC(char*) PyOS_double_to_string(double a, char b, int c, int d, int* e) {
    LOG("0x%lx 0x%lx 0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c, (unsigned long) d, (unsigned long) e);
    STATS_BEFORE(PyOS_double_to_string)
    char* result = (char*) __target__PyOS_double_to_string(a, b, c, d, e);
    STATS_AFTER(PyOS_double_to_string)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(PyOS_sighandler_t) PyOS_getsig(int a) {
    unimplemented("PyOS_getsig"); exit(-1);
}
PyAPI_FUNC(int) PyOS_mystricmp(const char* a, const char* b) {
    unimplemented("PyOS_mystricmp"); exit(-1);
}
PyAPI_FUNC(int) PyOS_mystrnicmp(const char* a, const char* b, Py_ssize_t c) {
    unimplemented("PyOS_mystrnicmp"); exit(-1);
}
STATS_CONTAINER(PyOS_setsig, PyOS_double_to_string)
PyOS_sighandler_t (*__target__PyOS_setsig)(int, PyOS_sighandler_t) = NULL;
PyAPI_FUNC(PyOS_sighandler_t) PyOS_setsig(int a, PyOS_sighandler_t b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(PyOS_setsig)
    PyOS_sighandler_t result = (PyOS_sighandler_t) __target__PyOS_setsig(a, b);
    STATS_AFTER(PyOS_setsig)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyOS_string_to_double, PyOS_setsig)
double (*__target__PyOS_string_to_double)(const char*, char**, PyObject*) = NULL;
PyAPI_FUNC(double) PyOS_string_to_double(const char* a, char** b, PyObject* c) {
    LOG("'%s'(0x%lx) 0x%lx 0x%lx", a?a:"<null>", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    STATS_BEFORE(PyOS_string_to_double)
    double result = (double) __target__PyOS_string_to_double(a, b, c);
    STATS_AFTER(PyOS_string_to_double)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyOS_strtol, PyOS_string_to_double)
long (*__target__PyOS_strtol)(const char*, char**, int) = NULL;
PyAPI_FUNC(long) PyOS_strtol(const char* a, char** b, int c) {
    LOG("'%s'(0x%lx) 0x%lx 0x%lx", a?a:"<null>", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    STATS_BEFORE(PyOS_strtol)
    long result = (long) __target__PyOS_strtol(a, b, c);
    STATS_AFTER(PyOS_strtol)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyOS_strtoul, PyOS_strtol)
unsigned long (*__target__PyOS_strtoul)(const char*, char**, int) = NULL;
PyAPI_FUNC(unsigned long) PyOS_strtoul(const char* a, char** b, int c) {
    LOG("'%s'(0x%lx) 0x%lx 0x%lx", a?a:"<null>", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    STATS_BEFORE(PyOS_strtoul)
    unsigned long result = (unsigned long) __target__PyOS_strtoul(a, b, c);
    STATS_AFTER(PyOS_strtoul)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyOS_vsnprintf, PyOS_strtoul)
int (*__target__PyOS_vsnprintf)(char*, size_t, const char*, va_list) = NULL;
PyAPI_FUNC(int) PyOS_vsnprintf(char* a, size_t b, const char* c, va_list d) {
    LOG("0x%lx 0x%lx '%s'(0x%lx) 0x%lx", (unsigned long) a, (unsigned long) b, c?c:"<null>", (unsigned long) c, (unsigned long) d);
    STATS_BEFORE(PyOS_vsnprintf)
    int result = (int) __target__PyOS_vsnprintf(a, b, c, d);
    STATS_AFTER(PyOS_vsnprintf)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyObject_ASCII, PyOS_vsnprintf)
PyObject* (*__target__PyObject_ASCII)(PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyObject_ASCII(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyObject_ASCII)
    PyObject* result = (PyObject*) __target__PyObject_ASCII(a);
    STATS_AFTER(PyObject_ASCII)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(int) PyObject_AsCharBuffer(PyObject* a, const char** b, Py_ssize_t* c) {
    unimplemented("PyObject_AsCharBuffer"); exit(-1);
}
STATS_CONTAINER(PyObject_AsFileDescriptor, PyObject_ASCII)
int (*__target__PyObject_AsFileDescriptor)(PyObject*) = NULL;
PyAPI_FUNC(int) PyObject_AsFileDescriptor(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyObject_AsFileDescriptor)
    int result = (int) __target__PyObject_AsFileDescriptor(a);
    STATS_AFTER(PyObject_AsFileDescriptor)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(int) PyObject_AsReadBuffer(PyObject* a, const void** b, Py_ssize_t* c) {
    unimplemented("PyObject_AsReadBuffer"); exit(-1);
}
PyAPI_FUNC(int) PyObject_AsWriteBuffer(PyObject* a, void** b, Py_ssize_t* c) {
    unimplemented("PyObject_AsWriteBuffer"); exit(-1);
}
STATS_CONTAINER(PyObject_Bytes, PyObject_AsFileDescriptor)
PyObject* (*__target__PyObject_Bytes)(PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyObject_Bytes(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyObject_Bytes)
    PyObject* result = (PyObject*) __target__PyObject_Bytes(a);
    STATS_AFTER(PyObject_Bytes)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyObject_Call, PyObject_Bytes)
PyObject* (*__target__PyObject_Call)(PyObject*, PyObject*, PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyObject_Call(PyObject* a, PyObject* b, PyObject* c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    STATS_BEFORE(PyObject_Call)
    PyObject* result = (PyObject*) __target__PyObject_Call(a, b, c);
    STATS_AFTER(PyObject_Call)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(void) PyObject_CallFinalizer(PyObject* a) {
    unimplemented("PyObject_CallFinalizer"); exit(-1);
}
PyAPI_FUNC(int) PyObject_CallFinalizerFromDealloc(PyObject* a) {
    unimplemented("PyObject_CallFinalizerFromDealloc"); exit(-1);
}
PyAPI_FUNC(PyObject*) PyObject_CallNoArgs(PyObject* a) {
    unimplemented("PyObject_CallNoArgs"); exit(-1);
}
STATS_CONTAINER(PyObject_CallObject, PyObject_Call)
PyObject* (*__target__PyObject_CallObject)(PyObject*, PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyObject_CallObject(PyObject* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(PyObject_CallObject)
    PyObject* result = (PyObject*) __target__PyObject_CallObject(a, b);
    STATS_AFTER(PyObject_CallObject)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(void*) PyObject_Calloc(size_t a, size_t b) {
    unimplemented("PyObject_Calloc"); exit(-1);
}
STATS_CONTAINER(PyObject_CheckBuffer, PyObject_CallObject)
int (*__target__PyObject_CheckBuffer)(PyObject*) = NULL;
PyAPI_FUNC(int) PyObject_CheckBuffer(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyObject_CheckBuffer)
    int result = (int) __target__PyObject_CheckBuffer(a);
    STATS_AFTER(PyObject_CheckBuffer)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(int) PyObject_CheckReadBuffer(PyObject* a) {
    unimplemented("PyObject_CheckReadBuffer"); exit(-1);
}
STATS_CONTAINER(PyObject_ClearWeakRefs, PyObject_CheckBuffer)
void (*__target__PyObject_ClearWeakRefs)(PyObject*) = NULL;
PyAPI_FUNC(void) PyObject_ClearWeakRefs(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyObject_ClearWeakRefs)
    __target__PyObject_ClearWeakRefs(a);
    STATS_AFTER(PyObject_ClearWeakRefs)
    LOG_AFTER_VOID
}
PyAPI_FUNC(int) PyObject_CopyData(PyObject* a, PyObject* b) {
    unimplemented("PyObject_CopyData"); exit(-1);
}
STATS_CONTAINER(PyObject_DelItem, PyObject_ClearWeakRefs)
int (*__target__PyObject_DelItem)(PyObject*, PyObject*) = NULL;
PyAPI_FUNC(int) PyObject_DelItem(PyObject* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(PyObject_DelItem)
    int result = (int) __target__PyObject_DelItem(a, b);
    STATS_AFTER(PyObject_DelItem)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(int) PyObject_DelItemString(PyObject* a, const char* b) {
    unimplemented("PyObject_DelItemString"); exit(-1);
}
STATS_CONTAINER(PyObject_Dir, PyObject_DelItem)
PyObject* (*__target__PyObject_Dir)(PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyObject_Dir(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyObject_Dir)
    PyObject* result = (PyObject*) __target__PyObject_Dir(a);
    STATS_AFTER(PyObject_Dir)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyObject_Format, PyObject_Dir)
PyObject* (*__target__PyObject_Format)(PyObject*, PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyObject_Format(PyObject* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(PyObject_Format)
    PyObject* result = (PyObject*) __target__PyObject_Format(a, b);
    STATS_AFTER(PyObject_Format)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyObject_Free, PyObject_Format)
void (*__target__PyObject_Free)(void*) = NULL;
PyAPI_FUNC(void) PyObject_Free(void* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyObject_Free)
    __target__PyObject_Free(a);
    STATS_AFTER(PyObject_Free)
    LOG_AFTER_VOID
}
STATS_CONTAINER(PyObject_GC_Del, PyObject_Free)
void (*__target__PyObject_GC_Del)(void*) = NULL;
PyAPI_FUNC(void) PyObject_GC_Del(void* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyObject_GC_Del)
    __target__PyObject_GC_Del(a);
    STATS_AFTER(PyObject_GC_Del)
    LOG_AFTER_VOID
}
PyAPI_FUNC(int) PyObject_GC_IsFinalized(PyObject* a) {
    unimplemented("PyObject_GC_IsFinalized"); exit(-1);
}
PyAPI_FUNC(int) PyObject_GC_IsTracked(PyObject* a) {
    unimplemented("PyObject_GC_IsTracked"); exit(-1);
}
STATS_CONTAINER(PyObject_GC_Track, PyObject_GC_Del)
void (*__target__PyObject_GC_Track)(void*) = NULL;
PyAPI_FUNC(void) PyObject_GC_Track(void* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyObject_GC_Track)
    __target__PyObject_GC_Track(a);
    STATS_AFTER(PyObject_GC_Track)
    LOG_AFTER_VOID
}
STATS_CONTAINER(PyObject_GC_UnTrack, PyObject_GC_Track)
void (*__target__PyObject_GC_UnTrack)(void*) = NULL;
PyAPI_FUNC(void) PyObject_GC_UnTrack(void* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyObject_GC_UnTrack)
    __target__PyObject_GC_UnTrack(a);
    STATS_AFTER(PyObject_GC_UnTrack)
    LOG_AFTER_VOID
}
PyAPI_FUNC(PyObject**) PyObject_GET_WEAKREFS_LISTPTR(PyObject* a) {
    unimplemented("PyObject_GET_WEAKREFS_LISTPTR"); exit(-1);
}
STATS_CONTAINER(PyObject_GenericGetAttr, PyObject_GC_UnTrack)
PyObject* (*__target__PyObject_GenericGetAttr)(PyObject*, PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyObject_GenericGetAttr(PyObject* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(PyObject_GenericGetAttr)
    PyObject* result = (PyObject*) __target__PyObject_GenericGetAttr(a, b);
    STATS_AFTER(PyObject_GenericGetAttr)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyObject_GenericSetAttr, PyObject_GenericGetAttr)
int (*__target__PyObject_GenericSetAttr)(PyObject*, PyObject*, PyObject*) = NULL;
PyAPI_FUNC(int) PyObject_GenericSetAttr(PyObject* a, PyObject* b, PyObject* c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    STATS_BEFORE(PyObject_GenericSetAttr)
    int result = (int) __target__PyObject_GenericSetAttr(a, b, c);
    STATS_AFTER(PyObject_GenericSetAttr)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyObject_GenericSetDict, PyObject_GenericSetAttr)
int (*__target__PyObject_GenericSetDict)(PyObject*, PyObject*, void*) = NULL;
PyAPI_FUNC(int) PyObject_GenericSetDict(PyObject* a, PyObject* b, void* c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    STATS_BEFORE(PyObject_GenericSetDict)
    int result = (int) __target__PyObject_GenericSetDict(a, b, c);
    STATS_AFTER(PyObject_GenericSetDict)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(PyObject*) PyObject_GetAIter(PyObject* a) {
    unimplemented("PyObject_GetAIter"); exit(-1);
}
PyAPI_FUNC(void) PyObject_GetArenaAllocator(PyObjectArenaAllocator* a) {
    unimplemented("PyObject_GetArenaAllocator"); exit(-1);
}
STATS_CONTAINER(PyObject_GetAttr, PyObject_GenericSetDict)
PyObject* (*__target__PyObject_GetAttr)(PyObject*, PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyObject_GetAttr(PyObject* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(PyObject_GetAttr)
    PyObject* result = (PyObject*) __target__PyObject_GetAttr(a, b);
    STATS_AFTER(PyObject_GetAttr)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyObject_GetAttrString, PyObject_GetAttr)
PyObject* (*__target__PyObject_GetAttrString)(PyObject*, const char*) = NULL;
PyAPI_FUNC(PyObject*) PyObject_GetAttrString(PyObject* a, const char* b) {
    LOG("0x%lx '%s'(0x%lx)", (unsigned long) a, b?b:"<null>", (unsigned long) b);
    STATS_BEFORE(PyObject_GetAttrString)
    PyObject* result = (PyObject*) __target__PyObject_GetAttrString(a, b);
    STATS_AFTER(PyObject_GetAttrString)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyObject_GetBuffer, PyObject_GetAttrString)
int (*__target__PyObject_GetBuffer)(PyObject*, Py_buffer*, int) = NULL;
PyAPI_FUNC(int) PyObject_GetBuffer(PyObject* a, Py_buffer* b, int c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    STATS_BEFORE(PyObject_GetBuffer)
    int result = (int) __target__PyObject_GetBuffer(a, b, c);
    STATS_AFTER(PyObject_GetBuffer)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyObject_GetDoc, PyObject_GetBuffer)
const char* (*__target__PyObject_GetDoc)(PyObject*) = NULL;
PyAPI_FUNC(const char*) PyObject_GetDoc(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyObject_GetDoc)
    const char* result = (const char*) __target__PyObject_GetDoc(a);
    STATS_AFTER(PyObject_GetDoc)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyObject_GetItem, PyObject_GetDoc)
PyObject* (*__target__PyObject_GetItem)(PyObject*, PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyObject_GetItem(PyObject* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(PyObject_GetItem)
    PyObject* result = (PyObject*) __target__PyObject_GetItem(a, b);
    STATS_AFTER(PyObject_GetItem)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyObject_GetIter, PyObject_GetItem)
PyObject* (*__target__PyObject_GetIter)(PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyObject_GetIter(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyObject_GetIter)
    PyObject* result = (PyObject*) __target__PyObject_GetIter(a);
    STATS_AFTER(PyObject_GetIter)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyObject_HasAttr, PyObject_GetIter)
int (*__target__PyObject_HasAttr)(PyObject*, PyObject*) = NULL;
PyAPI_FUNC(int) PyObject_HasAttr(PyObject* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(PyObject_HasAttr)
    int result = (int) __target__PyObject_HasAttr(a, b);
    STATS_AFTER(PyObject_HasAttr)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyObject_HasAttrString, PyObject_HasAttr)
int (*__target__PyObject_HasAttrString)(PyObject*, const char*) = NULL;
PyAPI_FUNC(int) PyObject_HasAttrString(PyObject* a, const char* b) {
    LOG("0x%lx '%s'(0x%lx)", (unsigned long) a, b?b:"<null>", (unsigned long) b);
    STATS_BEFORE(PyObject_HasAttrString)
    int result = (int) __target__PyObject_HasAttrString(a, b);
    STATS_AFTER(PyObject_HasAttrString)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyObject_Hash, PyObject_HasAttrString)
Py_hash_t (*__target__PyObject_Hash)(PyObject*) = NULL;
PyAPI_FUNC(Py_hash_t) PyObject_Hash(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyObject_Hash)
    Py_hash_t result = (Py_hash_t) __target__PyObject_Hash(a);
    STATS_AFTER(PyObject_Hash)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyObject_HashNotImplemented, PyObject_Hash)
Py_hash_t (*__target__PyObject_HashNotImplemented)(PyObject*) = NULL;
PyAPI_FUNC(Py_hash_t) PyObject_HashNotImplemented(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyObject_HashNotImplemented)
    Py_hash_t result = (Py_hash_t) __target__PyObject_HashNotImplemented(a);
    STATS_AFTER(PyObject_HashNotImplemented)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(int) PyObject_IS_GC(PyObject* a) {
    unimplemented("PyObject_IS_GC"); exit(-1);
}
STATS_CONTAINER(PyObject_Init, PyObject_HashNotImplemented)
PyObject* (*__target__PyObject_Init)(PyObject*, PyTypeObject*) = NULL;
PyAPI_FUNC(PyObject*) PyObject_Init(PyObject* a, PyTypeObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(PyObject_Init)
    PyObject* result = (PyObject*) __target__PyObject_Init(a, b);
    STATS_AFTER(PyObject_Init)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyObject_InitVar, PyObject_Init)
PyVarObject* (*__target__PyObject_InitVar)(PyVarObject*, PyTypeObject*, Py_ssize_t) = NULL;
PyAPI_FUNC(PyVarObject*) PyObject_InitVar(PyVarObject* a, PyTypeObject* b, Py_ssize_t c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    STATS_BEFORE(PyObject_InitVar)
    PyVarObject* result = (PyVarObject*) __target__PyObject_InitVar(a, b, c);
    STATS_AFTER(PyObject_InitVar)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyObject_IsInstance, PyObject_InitVar)
int (*__target__PyObject_IsInstance)(PyObject*, PyObject*) = NULL;
PyAPI_FUNC(int) PyObject_IsInstance(PyObject* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(PyObject_IsInstance)
    int result = (int) __target__PyObject_IsInstance(a, b);
    STATS_AFTER(PyObject_IsInstance)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyObject_IsSubclass, PyObject_IsInstance)
int (*__target__PyObject_IsSubclass)(PyObject*, PyObject*) = NULL;
PyAPI_FUNC(int) PyObject_IsSubclass(PyObject* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(PyObject_IsSubclass)
    int result = (int) __target__PyObject_IsSubclass(a, b);
    STATS_AFTER(PyObject_IsSubclass)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyObject_IsTrue, PyObject_IsSubclass)
int (*__target__PyObject_IsTrue)(PyObject*) = NULL;
PyAPI_FUNC(int) PyObject_IsTrue(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyObject_IsTrue)
    int result = (int) __target__PyObject_IsTrue(a);
    STATS_AFTER(PyObject_IsTrue)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(Py_ssize_t) PyObject_Length(PyObject* a) {
    unimplemented("PyObject_Length"); exit(-1);
}
STATS_CONTAINER(PyObject_LengthHint, PyObject_IsTrue)
Py_ssize_t (*__target__PyObject_LengthHint)(PyObject*, Py_ssize_t) = NULL;
PyAPI_FUNC(Py_ssize_t) PyObject_LengthHint(PyObject* a, Py_ssize_t b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(PyObject_LengthHint)
    Py_ssize_t result = (Py_ssize_t) __target__PyObject_LengthHint(a, b);
    STATS_AFTER(PyObject_LengthHint)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyObject_Malloc, PyObject_LengthHint)
void* (*__target__PyObject_Malloc)(size_t) = NULL;
PyAPI_FUNC(void*) PyObject_Malloc(size_t a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyObject_Malloc)
    void* result = (void*) __target__PyObject_Malloc(a);
    STATS_AFTER(PyObject_Malloc)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyObject_Not, PyObject_Malloc)
int (*__target__PyObject_Not)(PyObject*) = NULL;
PyAPI_FUNC(int) PyObject_Not(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyObject_Not)
    int result = (int) __target__PyObject_Not(a);
    STATS_AFTER(PyObject_Not)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyObject_Print, PyObject_Not)
int (*__target__PyObject_Print)(PyObject*, FILE*, int) = NULL;
PyAPI_FUNC(int) PyObject_Print(PyObject* a, FILE* b, int c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    STATS_BEFORE(PyObject_Print)
    int result = (int) __target__PyObject_Print(a, b, c);
    STATS_AFTER(PyObject_Print)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyObject_Realloc, PyObject_Print)
void* (*__target__PyObject_Realloc)(void*, size_t) = NULL;
PyAPI_FUNC(void*) PyObject_Realloc(void* a, size_t b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(PyObject_Realloc)
    void* result = (void*) __target__PyObject_Realloc(a, b);
    STATS_AFTER(PyObject_Realloc)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyObject_Repr, PyObject_Realloc)
PyObject* (*__target__PyObject_Repr)(PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyObject_Repr(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyObject_Repr)
    PyObject* result = (PyObject*) __target__PyObject_Repr(a);
    STATS_AFTER(PyObject_Repr)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyObject_RichCompare, PyObject_Repr)
PyObject* (*__target__PyObject_RichCompare)(PyObject*, PyObject*, int) = NULL;
PyAPI_FUNC(PyObject*) PyObject_RichCompare(PyObject* a, PyObject* b, int c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    STATS_BEFORE(PyObject_RichCompare)
    PyObject* result = (PyObject*) __target__PyObject_RichCompare(a, b, c);
    STATS_AFTER(PyObject_RichCompare)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyObject_RichCompareBool, PyObject_RichCompare)
int (*__target__PyObject_RichCompareBool)(PyObject*, PyObject*, int) = NULL;
PyAPI_FUNC(int) PyObject_RichCompareBool(PyObject* a, PyObject* b, int c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    STATS_BEFORE(PyObject_RichCompareBool)
    int result = (int) __target__PyObject_RichCompareBool(a, b, c);
    STATS_AFTER(PyObject_RichCompareBool)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyObject_SelfIter, PyObject_RichCompareBool)
PyObject* (*__target__PyObject_SelfIter)(PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyObject_SelfIter(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyObject_SelfIter)
    PyObject* result = (PyObject*) __target__PyObject_SelfIter(a);
    STATS_AFTER(PyObject_SelfIter)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(void) PyObject_SetArenaAllocator(PyObjectArenaAllocator* a) {
    unimplemented("PyObject_SetArenaAllocator"); exit(-1);
}
STATS_CONTAINER(PyObject_SetAttr, PyObject_SelfIter)
int (*__target__PyObject_SetAttr)(PyObject*, PyObject*, PyObject*) = NULL;
PyAPI_FUNC(int) PyObject_SetAttr(PyObject* a, PyObject* b, PyObject* c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    STATS_BEFORE(PyObject_SetAttr)
    int result = (int) __target__PyObject_SetAttr(a, b, c);
    STATS_AFTER(PyObject_SetAttr)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyObject_SetAttrString, PyObject_SetAttr)
int (*__target__PyObject_SetAttrString)(PyObject*, const char*, PyObject*) = NULL;
PyAPI_FUNC(int) PyObject_SetAttrString(PyObject* a, const char* b, PyObject* c) {
    LOG("0x%lx '%s'(0x%lx) 0x%lx", (unsigned long) a, b?b:"<null>", (unsigned long) b, (unsigned long) c);
    STATS_BEFORE(PyObject_SetAttrString)
    int result = (int) __target__PyObject_SetAttrString(a, b, c);
    STATS_AFTER(PyObject_SetAttrString)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyObject_SetDoc, PyObject_SetAttrString)
int (*__target__PyObject_SetDoc)(PyObject*, const char*) = NULL;
PyAPI_FUNC(int) PyObject_SetDoc(PyObject* a, const char* b) {
    LOG("0x%lx '%s'(0x%lx)", (unsigned long) a, b?b:"<null>", (unsigned long) b);
    STATS_BEFORE(PyObject_SetDoc)
    int result = (int) __target__PyObject_SetDoc(a, b);
    STATS_AFTER(PyObject_SetDoc)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyObject_SetItem, PyObject_SetDoc)
int (*__target__PyObject_SetItem)(PyObject*, PyObject*, PyObject*) = NULL;
PyAPI_FUNC(int) PyObject_SetItem(PyObject* a, PyObject* b, PyObject* c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    STATS_BEFORE(PyObject_SetItem)
    int result = (int) __target__PyObject_SetItem(a, b, c);
    STATS_AFTER(PyObject_SetItem)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyObject_Size, PyObject_SetItem)
Py_ssize_t (*__target__PyObject_Size)(PyObject*) = NULL;
PyAPI_FUNC(Py_ssize_t) PyObject_Size(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyObject_Size)
    Py_ssize_t result = (Py_ssize_t) __target__PyObject_Size(a);
    STATS_AFTER(PyObject_Size)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyObject_Str, PyObject_Size)
PyObject* (*__target__PyObject_Str)(PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyObject_Str(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyObject_Str)
    PyObject* result = (PyObject*) __target__PyObject_Str(a);
    STATS_AFTER(PyObject_Str)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyObject_Type, PyObject_Str)
PyObject* (*__target__PyObject_Type)(PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyObject_Type(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyObject_Type)
    PyObject* result = (PyObject*) __target__PyObject_Type(a);
    STATS_AFTER(PyObject_Type)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyObject_VectorcallDict, PyObject_Type)
PyObject* (*__target__PyObject_VectorcallDict)(PyObject*, PyObject*const*, size_t, PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyObject_VectorcallDict(PyObject* a, PyObject*const* b, size_t c, PyObject* d) {
    LOG("0x%lx 0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c, (unsigned long) d);
    STATS_BEFORE(PyObject_VectorcallDict)
    PyObject* result = (PyObject*) __target__PyObject_VectorcallDict(a, b, c, d);
    STATS_AFTER(PyObject_VectorcallDict)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(PyObject*) PyObject_VectorcallMethod(PyObject* a, PyObject*const* b, size_t c, PyObject* d) {
    unimplemented("PyObject_VectorcallMethod"); exit(-1);
}
PyAPI_FUNC(PyObject*) PyPickleBuffer_FromObject(PyObject* a) {
    unimplemented("PyPickleBuffer_FromObject"); exit(-1);
}
PyAPI_FUNC(const Py_buffer*) PyPickleBuffer_GetBuffer(PyObject* a) {
    unimplemented("PyPickleBuffer_GetBuffer"); exit(-1);
}
PyAPI_FUNC(int) PyPickleBuffer_Release(PyObject* a) {
    unimplemented("PyPickleBuffer_Release"); exit(-1);
}
PyAPI_FUNC(void) PyPreConfig_InitIsolatedConfig(PyPreConfig* a) {
    unimplemented("PyPreConfig_InitIsolatedConfig"); exit(-1);
}
PyAPI_FUNC(void) PyPreConfig_InitPythonConfig(PyPreConfig* a) {
    unimplemented("PyPreConfig_InitPythonConfig"); exit(-1);
}
PyAPI_FUNC(int) PyRun_AnyFile(FILE* a, const char* b) {
    unimplemented("PyRun_AnyFile"); exit(-1);
}
PyAPI_FUNC(int) PyRun_AnyFileEx(FILE* a, const char* b, int c) {
    unimplemented("PyRun_AnyFileEx"); exit(-1);
}
PyAPI_FUNC(int) PyRun_AnyFileExFlags(FILE* a, const char* b, int c, PyCompilerFlags* d) {
    unimplemented("PyRun_AnyFileExFlags"); exit(-1);
}
PyAPI_FUNC(int) PyRun_AnyFileFlags(FILE* a, const char* b, PyCompilerFlags* c) {
    unimplemented("PyRun_AnyFileFlags"); exit(-1);
}
PyAPI_FUNC(PyObject*) PyRun_File(FILE* a, const char* b, int c, PyObject* d, PyObject* e) {
    unimplemented("PyRun_File"); exit(-1);
}
PyAPI_FUNC(PyObject*) PyRun_FileEx(FILE* a, const char* b, int c, PyObject* d, PyObject* e, int f) {
    unimplemented("PyRun_FileEx"); exit(-1);
}
PyAPI_FUNC(PyObject*) PyRun_FileExFlags(FILE* a, const char* b, int c, PyObject* d, PyObject* e, int f, PyCompilerFlags* g) {
    unimplemented("PyRun_FileExFlags"); exit(-1);
}
PyAPI_FUNC(PyObject*) PyRun_FileFlags(FILE* a, const char* b, int c, PyObject* d, PyObject* e, PyCompilerFlags* f) {
    unimplemented("PyRun_FileFlags"); exit(-1);
}
PyAPI_FUNC(int) PyRun_InteractiveLoop(FILE* a, const char* b) {
    unimplemented("PyRun_InteractiveLoop"); exit(-1);
}
PyAPI_FUNC(int) PyRun_InteractiveLoopFlags(FILE* a, const char* b, PyCompilerFlags* c) {
    unimplemented("PyRun_InteractiveLoopFlags"); exit(-1);
}
PyAPI_FUNC(int) PyRun_InteractiveOne(FILE* a, const char* b) {
    unimplemented("PyRun_InteractiveOne"); exit(-1);
}
PyAPI_FUNC(int) PyRun_InteractiveOneFlags(FILE* a, const char* b, PyCompilerFlags* c) {
    unimplemented("PyRun_InteractiveOneFlags"); exit(-1);
}
PyAPI_FUNC(int) PyRun_InteractiveOneObject(FILE* a, PyObject* b, PyCompilerFlags* c) {
    unimplemented("PyRun_InteractiveOneObject"); exit(-1);
}
PyAPI_FUNC(int) PyRun_SimpleFile(FILE* a, const char* b) {
    unimplemented("PyRun_SimpleFile"); exit(-1);
}
PyAPI_FUNC(int) PyRun_SimpleFileEx(FILE* a, const char* b, int c) {
    unimplemented("PyRun_SimpleFileEx"); exit(-1);
}
PyAPI_FUNC(int) PyRun_SimpleFileExFlags(FILE* a, const char* b, int c, PyCompilerFlags* d) {
    unimplemented("PyRun_SimpleFileExFlags"); exit(-1);
}
PyAPI_FUNC(int) PyRun_SimpleString(const char* a) {
    unimplemented("PyRun_SimpleString"); exit(-1);
}
PyAPI_FUNC(int) PyRun_SimpleStringFlags(const char* a, PyCompilerFlags* b) {
    unimplemented("PyRun_SimpleStringFlags"); exit(-1);
}
PyAPI_FUNC(PyObject*) PyRun_String(const char* a, int b, PyObject* c, PyObject* d) {
    unimplemented("PyRun_String"); exit(-1);
}
STATS_CONTAINER(PyRun_StringFlags, PyObject_VectorcallDict)
PyObject* (*__target__PyRun_StringFlags)(const char*, int, PyObject*, PyObject*, PyCompilerFlags*) = NULL;
PyAPI_FUNC(PyObject*) PyRun_StringFlags(const char* a, int b, PyObject* c, PyObject* d, PyCompilerFlags* e) {
    LOG("'%s'(0x%lx) 0x%lx 0x%lx 0x%lx 0x%lx", a?a:"<null>", (unsigned long) a, (unsigned long) b, (unsigned long) c, (unsigned long) d, (unsigned long) e);
    STATS_BEFORE(PyRun_StringFlags)
    PyObject* result = (PyObject*) __target__PyRun_StringFlags(a, b, c, d, e);
    STATS_AFTER(PyRun_StringFlags)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PySeqIter_New, PyRun_StringFlags)
PyObject* (*__target__PySeqIter_New)(PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PySeqIter_New(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PySeqIter_New)
    PyObject* result = (PyObject*) __target__PySeqIter_New(a);
    STATS_AFTER(PySeqIter_New)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PySequence_Check, PySeqIter_New)
int (*__target__PySequence_Check)(PyObject*) = NULL;
PyAPI_FUNC(int) PySequence_Check(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PySequence_Check)
    int result = (int) __target__PySequence_Check(a);
    STATS_AFTER(PySequence_Check)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PySequence_Concat, PySequence_Check)
PyObject* (*__target__PySequence_Concat)(PyObject*, PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PySequence_Concat(PyObject* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(PySequence_Concat)
    PyObject* result = (PyObject*) __target__PySequence_Concat(a, b);
    STATS_AFTER(PySequence_Concat)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PySequence_Contains, PySequence_Concat)
int (*__target__PySequence_Contains)(PyObject*, PyObject*) = NULL;
PyAPI_FUNC(int) PySequence_Contains(PyObject* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(PySequence_Contains)
    int result = (int) __target__PySequence_Contains(a, b);
    STATS_AFTER(PySequence_Contains)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(Py_ssize_t) PySequence_Count(PyObject* a, PyObject* b) {
    unimplemented("PySequence_Count"); exit(-1);
}
STATS_CONTAINER(PySequence_DelItem, PySequence_Contains)
int (*__target__PySequence_DelItem)(PyObject*, Py_ssize_t) = NULL;
PyAPI_FUNC(int) PySequence_DelItem(PyObject* a, Py_ssize_t b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(PySequence_DelItem)
    int result = (int) __target__PySequence_DelItem(a, b);
    STATS_AFTER(PySequence_DelItem)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(int) PySequence_DelSlice(PyObject* a, Py_ssize_t b, Py_ssize_t c) {
    unimplemented("PySequence_DelSlice"); exit(-1);
}
STATS_CONTAINER(PySequence_Fast, PySequence_DelItem)
PyObject* (*__target__PySequence_Fast)(PyObject*, const char*) = NULL;
PyAPI_FUNC(PyObject*) PySequence_Fast(PyObject* a, const char* b) {
    LOG("0x%lx '%s'(0x%lx)", (unsigned long) a, b?b:"<null>", (unsigned long) b);
    STATS_BEFORE(PySequence_Fast)
    PyObject* result = (PyObject*) __target__PySequence_Fast(a, b);
    STATS_AFTER(PySequence_Fast)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PySequence_GetItem, PySequence_Fast)
PyObject* (*__target__PySequence_GetItem)(PyObject*, Py_ssize_t) = NULL;
PyAPI_FUNC(PyObject*) PySequence_GetItem(PyObject* a, Py_ssize_t b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(PySequence_GetItem)
    PyObject* result = (PyObject*) __target__PySequence_GetItem(a, b);
    STATS_AFTER(PySequence_GetItem)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PySequence_GetSlice, PySequence_GetItem)
PyObject* (*__target__PySequence_GetSlice)(PyObject*, Py_ssize_t, Py_ssize_t) = NULL;
PyAPI_FUNC(PyObject*) PySequence_GetSlice(PyObject* a, Py_ssize_t b, Py_ssize_t c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    STATS_BEFORE(PySequence_GetSlice)
    PyObject* result = (PyObject*) __target__PySequence_GetSlice(a, b, c);
    STATS_AFTER(PySequence_GetSlice)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(int) PySequence_In(PyObject* a, PyObject* b) {
    unimplemented("PySequence_In"); exit(-1);
}
STATS_CONTAINER(PySequence_InPlaceConcat, PySequence_GetSlice)
PyObject* (*__target__PySequence_InPlaceConcat)(PyObject*, PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PySequence_InPlaceConcat(PyObject* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(PySequence_InPlaceConcat)
    PyObject* result = (PyObject*) __target__PySequence_InPlaceConcat(a, b);
    STATS_AFTER(PySequence_InPlaceConcat)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PySequence_InPlaceRepeat, PySequence_InPlaceConcat)
PyObject* (*__target__PySequence_InPlaceRepeat)(PyObject*, Py_ssize_t) = NULL;
PyAPI_FUNC(PyObject*) PySequence_InPlaceRepeat(PyObject* a, Py_ssize_t b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(PySequence_InPlaceRepeat)
    PyObject* result = (PyObject*) __target__PySequence_InPlaceRepeat(a, b);
    STATS_AFTER(PySequence_InPlaceRepeat)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(Py_ssize_t) PySequence_Index(PyObject* a, PyObject* b) {
    unimplemented("PySequence_Index"); exit(-1);
}
STATS_CONTAINER(PySequence_Length, PySequence_InPlaceRepeat)
Py_ssize_t (*__target__PySequence_Length)(PyObject*) = NULL;
PyAPI_FUNC(Py_ssize_t) PySequence_Length(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PySequence_Length)
    Py_ssize_t result = (Py_ssize_t) __target__PySequence_Length(a);
    STATS_AFTER(PySequence_Length)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PySequence_List, PySequence_Length)
PyObject* (*__target__PySequence_List)(PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PySequence_List(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PySequence_List)
    PyObject* result = (PyObject*) __target__PySequence_List(a);
    STATS_AFTER(PySequence_List)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PySequence_Repeat, PySequence_List)
PyObject* (*__target__PySequence_Repeat)(PyObject*, Py_ssize_t) = NULL;
PyAPI_FUNC(PyObject*) PySequence_Repeat(PyObject* a, Py_ssize_t b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(PySequence_Repeat)
    PyObject* result = (PyObject*) __target__PySequence_Repeat(a, b);
    STATS_AFTER(PySequence_Repeat)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PySequence_SetItem, PySequence_Repeat)
int (*__target__PySequence_SetItem)(PyObject*, Py_ssize_t, PyObject*) = NULL;
PyAPI_FUNC(int) PySequence_SetItem(PyObject* a, Py_ssize_t b, PyObject* c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    STATS_BEFORE(PySequence_SetItem)
    int result = (int) __target__PySequence_SetItem(a, b, c);
    STATS_AFTER(PySequence_SetItem)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(int) PySequence_SetSlice(PyObject* a, Py_ssize_t b, Py_ssize_t c, PyObject* d) {
    unimplemented("PySequence_SetSlice"); exit(-1);
}
STATS_CONTAINER(PySequence_Size, PySequence_SetItem)
Py_ssize_t (*__target__PySequence_Size)(PyObject*) = NULL;
PyAPI_FUNC(Py_ssize_t) PySequence_Size(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PySequence_Size)
    Py_ssize_t result = (Py_ssize_t) __target__PySequence_Size(a);
    STATS_AFTER(PySequence_Size)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PySequence_Tuple, PySequence_Size)
PyObject* (*__target__PySequence_Tuple)(PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PySequence_Tuple(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PySequence_Tuple)
    PyObject* result = (PyObject*) __target__PySequence_Tuple(a);
    STATS_AFTER(PySequence_Tuple)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PySet_Add, PySequence_Tuple)
int (*__target__PySet_Add)(PyObject*, PyObject*) = NULL;
PyAPI_FUNC(int) PySet_Add(PyObject* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(PySet_Add)
    int result = (int) __target__PySet_Add(a, b);
    STATS_AFTER(PySet_Add)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PySet_Clear, PySet_Add)
int (*__target__PySet_Clear)(PyObject*) = NULL;
PyAPI_FUNC(int) PySet_Clear(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PySet_Clear)
    int result = (int) __target__PySet_Clear(a);
    STATS_AFTER(PySet_Clear)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PySet_Contains, PySet_Clear)
int (*__target__PySet_Contains)(PyObject*, PyObject*) = NULL;
PyAPI_FUNC(int) PySet_Contains(PyObject* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(PySet_Contains)
    int result = (int) __target__PySet_Contains(a, b);
    STATS_AFTER(PySet_Contains)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PySet_Discard, PySet_Contains)
int (*__target__PySet_Discard)(PyObject*, PyObject*) = NULL;
PyAPI_FUNC(int) PySet_Discard(PyObject* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(PySet_Discard)
    int result = (int) __target__PySet_Discard(a, b);
    STATS_AFTER(PySet_Discard)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PySet_New, PySet_Discard)
PyObject* (*__target__PySet_New)(PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PySet_New(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PySet_New)
    PyObject* result = (PyObject*) __target__PySet_New(a);
    STATS_AFTER(PySet_New)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PySet_Pop, PySet_New)
PyObject* (*__target__PySet_Pop)(PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PySet_Pop(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PySet_Pop)
    PyObject* result = (PyObject*) __target__PySet_Pop(a);
    STATS_AFTER(PySet_Pop)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PySet_Size, PySet_Pop)
Py_ssize_t (*__target__PySet_Size)(PyObject*) = NULL;
PyAPI_FUNC(Py_ssize_t) PySet_Size(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PySet_Size)
    Py_ssize_t result = (Py_ssize_t) __target__PySet_Size(a);
    STATS_AFTER(PySet_Size)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(int) PySignal_SetWakeupFd(int a) {
    unimplemented("PySignal_SetWakeupFd"); exit(-1);
}
STATS_CONTAINER(PySlice_AdjustIndices, PySet_Size)
Py_ssize_t (*__target__PySlice_AdjustIndices)(Py_ssize_t, Py_ssize_t*, Py_ssize_t*, Py_ssize_t) = NULL;
PyAPI_FUNC(Py_ssize_t) PySlice_AdjustIndices(Py_ssize_t a, Py_ssize_t* b, Py_ssize_t* c, Py_ssize_t d) {
    LOG("0x%lx 0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c, (unsigned long) d);
    STATS_BEFORE(PySlice_AdjustIndices)
    Py_ssize_t result = (Py_ssize_t) __target__PySlice_AdjustIndices(a, b, c, d);
    STATS_AFTER(PySlice_AdjustIndices)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(int) PySlice_GetIndices(PyObject* a, Py_ssize_t b, Py_ssize_t* c, Py_ssize_t* d, Py_ssize_t* e) {
    unimplemented("PySlice_GetIndices"); exit(-1);
}
PyAPI_FUNC(int) PySlice_GetIndicesEx(PyObject* a, Py_ssize_t b, Py_ssize_t* c, Py_ssize_t* d, Py_ssize_t* e, Py_ssize_t* f) {
    unimplemented("PySlice_GetIndicesEx"); exit(-1);
}
STATS_CONTAINER(PySlice_New, PySlice_AdjustIndices)
PyObject* (*__target__PySlice_New)(PyObject*, PyObject*, PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PySlice_New(PyObject* a, PyObject* b, PyObject* c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    STATS_BEFORE(PySlice_New)
    PyObject* result = (PyObject*) __target__PySlice_New(a, b, c);
    STATS_AFTER(PySlice_New)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PySlice_Start, PySlice_New)
PyObject* (*__target__PySlice_Start)(PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PySlice_Start(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PySlice_Start)
    PyObject* result = (PyObject*) __target__PySlice_Start(a);
    STATS_AFTER(PySlice_Start)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PySlice_Step, PySlice_Start)
PyObject* (*__target__PySlice_Step)(PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PySlice_Step(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PySlice_Step)
    PyObject* result = (PyObject*) __target__PySlice_Step(a);
    STATS_AFTER(PySlice_Step)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PySlice_Stop, PySlice_Step)
PyObject* (*__target__PySlice_Stop)(PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PySlice_Stop(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PySlice_Stop)
    PyObject* result = (PyObject*) __target__PySlice_Stop(a);
    STATS_AFTER(PySlice_Stop)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PySlice_Unpack, PySlice_Stop)
int (*__target__PySlice_Unpack)(PyObject*, Py_ssize_t*, Py_ssize_t*, Py_ssize_t*) = NULL;
PyAPI_FUNC(int) PySlice_Unpack(PyObject* a, Py_ssize_t* b, Py_ssize_t* c, Py_ssize_t* d) {
    LOG("0x%lx 0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c, (unsigned long) d);
    STATS_BEFORE(PySlice_Unpack)
    int result = (int) __target__PySlice_Unpack(a, b, c, d);
    STATS_AFTER(PySlice_Unpack)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyState_AddModule, PySlice_Unpack)
int (*__target__PyState_AddModule)(PyObject*, struct PyModuleDef*) = NULL;
PyAPI_FUNC(int) PyState_AddModule(PyObject* a, struct PyModuleDef* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(PyState_AddModule)
    int result = (int) __target__PyState_AddModule(a, b);
    STATS_AFTER(PyState_AddModule)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyState_FindModule, PyState_AddModule)
PyObject* (*__target__PyState_FindModule)(struct PyModuleDef*) = NULL;
PyAPI_FUNC(PyObject*) PyState_FindModule(struct PyModuleDef* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyState_FindModule)
    PyObject* result = (PyObject*) __target__PyState_FindModule(a);
    STATS_AFTER(PyState_FindModule)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyState_RemoveModule, PyState_FindModule)
int (*__target__PyState_RemoveModule)(struct PyModuleDef*) = NULL;
PyAPI_FUNC(int) PyState_RemoveModule(struct PyModuleDef* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyState_RemoveModule)
    int result = (int) __target__PyState_RemoveModule(a);
    STATS_AFTER(PyState_RemoveModule)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyStaticMethod_New, PyState_RemoveModule)
PyObject* (*__target__PyStaticMethod_New)(PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyStaticMethod_New(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyStaticMethod_New)
    PyObject* result = (PyObject*) __target__PyStaticMethod_New(a);
    STATS_AFTER(PyStaticMethod_New)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(PyStatus) PyStatus_Error(const char* a) {
    unimplemented("PyStatus_Error"); exit(-1);
}
PyAPI_FUNC(int) PyStatus_Exception(PyStatus a) {
    unimplemented("PyStatus_Exception"); exit(-1);
}
PyAPI_FUNC(PyStatus) PyStatus_Exit(int a) {
    unimplemented("PyStatus_Exit"); exit(-1);
}
PyAPI_FUNC(int) PyStatus_IsError(PyStatus a) {
    unimplemented("PyStatus_IsError"); exit(-1);
}
PyAPI_FUNC(int) PyStatus_IsExit(PyStatus a) {
    unimplemented("PyStatus_IsExit"); exit(-1);
}
PyAPI_FUNC(PyStatus) PyStatus_NoMemory() {
    unimplemented("PyStatus_NoMemory"); exit(-1);
}
PyAPI_FUNC(PyStatus) PyStatus_Ok() {
    unimplemented("PyStatus_Ok"); exit(-1);
}
STATS_CONTAINER(PyStructSequence_GetItem, PyStaticMethod_New)
PyObject* (*__target__PyStructSequence_GetItem)(PyObject*, Py_ssize_t) = NULL;
PyAPI_FUNC(PyObject*) PyStructSequence_GetItem(PyObject* a, Py_ssize_t b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(PyStructSequence_GetItem)
    PyObject* result = (PyObject*) __target__PyStructSequence_GetItem(a, b);
    STATS_AFTER(PyStructSequence_GetItem)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyStructSequence_InitType, PyStructSequence_GetItem)
void (*__target__PyStructSequence_InitType)(PyTypeObject*, PyStructSequence_Desc*) = NULL;
PyAPI_FUNC(void) PyStructSequence_InitType(PyTypeObject* a, PyStructSequence_Desc* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(PyStructSequence_InitType)
    __target__PyStructSequence_InitType(a, b);
    STATS_AFTER(PyStructSequence_InitType)
    LOG_AFTER_VOID
}
STATS_CONTAINER(PyStructSequence_InitType2, PyStructSequence_InitType)
int (*__target__PyStructSequence_InitType2)(PyTypeObject*, PyStructSequence_Desc*) = NULL;
PyAPI_FUNC(int) PyStructSequence_InitType2(PyTypeObject* a, PyStructSequence_Desc* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(PyStructSequence_InitType2)
    int result = (int) __target__PyStructSequence_InitType2(a, b);
    STATS_AFTER(PyStructSequence_InitType2)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyStructSequence_New, PyStructSequence_InitType2)
PyObject* (*__target__PyStructSequence_New)(PyTypeObject*) = NULL;
PyAPI_FUNC(PyObject*) PyStructSequence_New(PyTypeObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyStructSequence_New)
    PyObject* result = (PyObject*) __target__PyStructSequence_New(a);
    STATS_AFTER(PyStructSequence_New)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyStructSequence_NewType, PyStructSequence_New)
PyTypeObject* (*__target__PyStructSequence_NewType)(PyStructSequence_Desc*) = NULL;
PyAPI_FUNC(PyTypeObject*) PyStructSequence_NewType(PyStructSequence_Desc* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyStructSequence_NewType)
    PyTypeObject* result = (PyTypeObject*) __target__PyStructSequence_NewType(a);
    STATS_AFTER(PyStructSequence_NewType)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyStructSequence_SetItem, PyStructSequence_NewType)
void (*__target__PyStructSequence_SetItem)(PyObject*, Py_ssize_t, PyObject*) = NULL;
PyAPI_FUNC(void) PyStructSequence_SetItem(PyObject* a, Py_ssize_t b, PyObject* c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    STATS_BEFORE(PyStructSequence_SetItem)
    __target__PyStructSequence_SetItem(a, b, c);
    STATS_AFTER(PyStructSequence_SetItem)
    LOG_AFTER_VOID
}
PyAPI_FUNC(int) PySys_AddAuditHook(Py_AuditHookFunction a, void* b) {
    unimplemented("PySys_AddAuditHook"); exit(-1);
}
PyAPI_FUNC(void) PySys_AddWarnOption(const wchar_t* a) {
    unimplemented("PySys_AddWarnOption"); exit(-1);
}
PyAPI_FUNC(void) PySys_AddWarnOptionUnicode(PyObject* a) {
    unimplemented("PySys_AddWarnOptionUnicode"); exit(-1);
}
PyAPI_FUNC(void) PySys_AddXOption(const wchar_t* a) {
    unimplemented("PySys_AddXOption"); exit(-1);
}
PyAPI_FUNC(int) PySys_Audit(const char* a, const char* b, ...) {
    unimplemented("PySys_Audit"); exit(-1);
}
PyAPI_FUNC(void) PySys_FormatStderr(const char* a, ...) {
    unimplemented("PySys_FormatStderr"); exit(-1);
}
PyAPI_FUNC(void) PySys_FormatStdout(const char* a, ...) {
    unimplemented("PySys_FormatStdout"); exit(-1);
}
STATS_CONTAINER(PySys_GetObject, PyStructSequence_SetItem)
PyObject* (*__target__PySys_GetObject)(const char*) = NULL;
PyAPI_FUNC(PyObject*) PySys_GetObject(const char* a) {
    LOG("'%s'(0x%lx)", a?a:"<null>", (unsigned long) a);
    STATS_BEFORE(PySys_GetObject)
    PyObject* result = (PyObject*) __target__PySys_GetObject(a);
    STATS_AFTER(PySys_GetObject)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(PyObject*) PySys_GetXOptions() {
    unimplemented("PySys_GetXOptions"); exit(-1);
}
PyAPI_FUNC(int) PySys_HasWarnOptions() {
    unimplemented("PySys_HasWarnOptions"); exit(-1);
}
PyAPI_FUNC(void) PySys_ResetWarnOptions() {
    unimplemented("PySys_ResetWarnOptions"); exit(-1);
}
PyAPI_FUNC(void) PySys_SetArgv(int a, wchar_t** b) {
    unimplemented("PySys_SetArgv"); exit(-1);
}
PyAPI_FUNC(void) PySys_SetArgvEx(int a, wchar_t** b, int c) {
    unimplemented("PySys_SetArgvEx"); exit(-1);
}
PyAPI_FUNC(int) PySys_SetObject(const char* a, PyObject* b) {
    unimplemented("PySys_SetObject"); exit(-1);
}
PyAPI_FUNC(void) PySys_SetPath(const wchar_t* a) {
    unimplemented("PySys_SetPath"); exit(-1);
}
PyAPI_FUNC(void) PySys_WriteStderr(const char* a, ...) {
    unimplemented("PySys_WriteStderr"); exit(-1);
}
PyAPI_FUNC(void) PySys_WriteStdout(const char* a, ...) {
    unimplemented("PySys_WriteStdout"); exit(-1);
}
STATS_CONTAINER(PyThreadState_Clear, PySys_GetObject)
void (*__target__PyThreadState_Clear)(PyThreadState*) = NULL;
PyAPI_FUNC(void) PyThreadState_Clear(PyThreadState* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyThreadState_Clear)
    __target__PyThreadState_Clear(a);
    STATS_AFTER(PyThreadState_Clear)
    LOG_AFTER_VOID
}
PyAPI_FUNC(void) PyThreadState_Delete(PyThreadState* a) {
    unimplemented("PyThreadState_Delete"); exit(-1);
}
STATS_CONTAINER(PyThreadState_DeleteCurrent, PyThreadState_Clear)
void (*__target__PyThreadState_DeleteCurrent)() = NULL;
PyAPI_FUNC(void) PyThreadState_DeleteCurrent() {
    LOGS("");
    STATS_BEFORE(PyThreadState_DeleteCurrent)
    __target__PyThreadState_DeleteCurrent();
    STATS_AFTER(PyThreadState_DeleteCurrent)
    LOG_AFTER_VOID
}
STATS_CONTAINER(PyThreadState_Get, PyThreadState_DeleteCurrent)
PyThreadState* (*__target__PyThreadState_Get)() = NULL;
MUST_INLINE PyAPI_FUNC(PyThreadState*) PyThreadState_Get_Inlined() {
    LOGS("");
    STATS_BEFORE(PyThreadState_Get)
    PyThreadState* result = (PyThreadState*) __target__PyThreadState_Get();
    STATS_AFTER(PyThreadState_Get)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyThreadState_GetDict, PyThreadState_Get)
PyObject* (*__target__PyThreadState_GetDict)() = NULL;
PyAPI_FUNC(PyObject*) PyThreadState_GetDict() {
    LOGS("");
    STATS_BEFORE(PyThreadState_GetDict)
    PyObject* result = (PyObject*) __target__PyThreadState_GetDict();
    STATS_AFTER(PyThreadState_GetDict)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(PyFrameObject*) PyThreadState_GetFrame(PyThreadState* a) {
    unimplemented("PyThreadState_GetFrame"); exit(-1);
}
PyAPI_FUNC(uint64_t) PyThreadState_GetID(PyThreadState* a) {
    unimplemented("PyThreadState_GetID"); exit(-1);
}
PyAPI_FUNC(PyInterpreterState*) PyThreadState_GetInterpreter(PyThreadState* a) {
    unimplemented("PyThreadState_GetInterpreter"); exit(-1);
}
PyAPI_FUNC(PyThreadState*) PyThreadState_New(PyInterpreterState* a) {
    unimplemented("PyThreadState_New"); exit(-1);
}
PyAPI_FUNC(PyThreadState*) PyThreadState_Next(PyThreadState* a) {
    unimplemented("PyThreadState_Next"); exit(-1);
}
PyAPI_FUNC(int) PyThreadState_SetAsyncExc(unsigned long a, PyObject* b) {
    unimplemented("PyThreadState_SetAsyncExc"); exit(-1);
}
PyAPI_FUNC(PyThreadState*) PyThreadState_Swap(PyThreadState* a) {
    unimplemented("PyThreadState_Swap"); exit(-1);
}
PyAPI_FUNC(PyObject*) PyThread_GetInfo() {
    unimplemented("PyThread_GetInfo"); exit(-1);
}
PyAPI_FUNC(void) PyThread_ReInitTLS() {
    unimplemented("PyThread_ReInitTLS"); exit(-1);
}
STATS_CONTAINER(PyThread_acquire_lock, PyThreadState_GetDict)
int (*__target__PyThread_acquire_lock)(PyThread_type_lock, int) = NULL;
PyAPI_FUNC(int) PyThread_acquire_lock(PyThread_type_lock a, int b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(PyThread_acquire_lock)
    int result = (int) __target__PyThread_acquire_lock(a, b);
    STATS_AFTER(PyThread_acquire_lock)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(PyLockStatus) PyThread_acquire_lock_timed(PyThread_type_lock a, long long b, int c) {
    unimplemented("PyThread_acquire_lock_timed"); exit(-1);
}
STATS_CONTAINER(PyThread_allocate_lock, PyThread_acquire_lock)
PyThread_type_lock (*__target__PyThread_allocate_lock)() = NULL;
PyAPI_FUNC(PyThread_type_lock) PyThread_allocate_lock() {
    LOGS("");
    STATS_BEFORE(PyThread_allocate_lock)
    PyThread_type_lock result = (PyThread_type_lock) __target__PyThread_allocate_lock();
    STATS_AFTER(PyThread_allocate_lock)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(int) PyThread_create_key() {
    unimplemented("PyThread_create_key"); exit(-1);
}
PyAPI_FUNC(void) PyThread_delete_key(int a) {
    unimplemented("PyThread_delete_key"); exit(-1);
}
PyAPI_FUNC(void) PyThread_delete_key_value(int a) {
    unimplemented("PyThread_delete_key_value"); exit(-1);
}
PyAPI_FUNC(void) PyThread_exit_thread() {
    unimplemented("PyThread_exit_thread"); exit(-1);
}
STATS_CONTAINER(PyThread_free_lock, PyThread_allocate_lock)
void (*__target__PyThread_free_lock)(PyThread_type_lock) = NULL;
PyAPI_FUNC(void) PyThread_free_lock(PyThread_type_lock a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyThread_free_lock)
    __target__PyThread_free_lock(a);
    STATS_AFTER(PyThread_free_lock)
    LOG_AFTER_VOID
}
PyAPI_FUNC(void*) PyThread_get_key_value(int a) {
    unimplemented("PyThread_get_key_value"); exit(-1);
}
PyAPI_FUNC(size_t) PyThread_get_stacksize() {
    unimplemented("PyThread_get_stacksize"); exit(-1);
}
PyAPI_FUNC(unsigned long) PyThread_get_thread_ident() {
    unimplemented("PyThread_get_thread_ident"); exit(-1);
}
PyAPI_FUNC(unsigned long) PyThread_get_thread_native_id() {
    unimplemented("PyThread_get_thread_native_id"); exit(-1);
}
PyAPI_FUNC(void) PyThread_init_thread() {
    unimplemented("PyThread_init_thread"); exit(-1);
}
STATS_CONTAINER(PyThread_release_lock, PyThread_free_lock)
void (*__target__PyThread_release_lock)(PyThread_type_lock) = NULL;
PyAPI_FUNC(void) PyThread_release_lock(PyThread_type_lock a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyThread_release_lock)
    __target__PyThread_release_lock(a);
    STATS_AFTER(PyThread_release_lock)
    LOG_AFTER_VOID
}
PyAPI_FUNC(int) PyThread_set_key_value(int a, void* b) {
    unimplemented("PyThread_set_key_value"); exit(-1);
}
PyAPI_FUNC(int) PyThread_set_stacksize(size_t a) {
    unimplemented("PyThread_set_stacksize"); exit(-1);
}
PyAPI_FUNC(unsigned long) PyThread_start_new_thread(void (*a)(void*), void* b) {
    unimplemented("PyThread_start_new_thread"); exit(-1);
}
STATS_CONTAINER(PyThread_tss_alloc, PyThread_release_lock)
Py_tss_t* (*__target__PyThread_tss_alloc)() = NULL;
PyAPI_FUNC(Py_tss_t*) PyThread_tss_alloc() {
    LOGS("");
    STATS_BEFORE(PyThread_tss_alloc)
    Py_tss_t* result = (Py_tss_t*) __target__PyThread_tss_alloc();
    STATS_AFTER(PyThread_tss_alloc)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyThread_tss_create, PyThread_tss_alloc)
int (*__target__PyThread_tss_create)(Py_tss_t*) = NULL;
PyAPI_FUNC(int) PyThread_tss_create(Py_tss_t* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyThread_tss_create)
    int result = (int) __target__PyThread_tss_create(a);
    STATS_AFTER(PyThread_tss_create)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyThread_tss_delete, PyThread_tss_create)
void (*__target__PyThread_tss_delete)(Py_tss_t*) = NULL;
PyAPI_FUNC(void) PyThread_tss_delete(Py_tss_t* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyThread_tss_delete)
    __target__PyThread_tss_delete(a);
    STATS_AFTER(PyThread_tss_delete)
    LOG_AFTER_VOID
}
STATS_CONTAINER(PyThread_tss_free, PyThread_tss_delete)
void (*__target__PyThread_tss_free)(Py_tss_t*) = NULL;
PyAPI_FUNC(void) PyThread_tss_free(Py_tss_t* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyThread_tss_free)
    __target__PyThread_tss_free(a);
    STATS_AFTER(PyThread_tss_free)
    LOG_AFTER_VOID
}
STATS_CONTAINER(PyThread_tss_get, PyThread_tss_free)
void* (*__target__PyThread_tss_get)(Py_tss_t*) = NULL;
PyAPI_FUNC(void*) PyThread_tss_get(Py_tss_t* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyThread_tss_get)
    void* result = (void*) __target__PyThread_tss_get(a);
    STATS_AFTER(PyThread_tss_get)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyThread_tss_is_created, PyThread_tss_get)
int (*__target__PyThread_tss_is_created)(Py_tss_t*) = NULL;
PyAPI_FUNC(int) PyThread_tss_is_created(Py_tss_t* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyThread_tss_is_created)
    int result = (int) __target__PyThread_tss_is_created(a);
    STATS_AFTER(PyThread_tss_is_created)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyThread_tss_set, PyThread_tss_is_created)
int (*__target__PyThread_tss_set)(Py_tss_t*, void*) = NULL;
PyAPI_FUNC(int) PyThread_tss_set(Py_tss_t* a, void* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(PyThread_tss_set)
    int result = (int) __target__PyThread_tss_set(a, b);
    STATS_AFTER(PyThread_tss_set)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyTraceBack_Here, PyThread_tss_set)
int (*__target__PyTraceBack_Here)(PyFrameObject*) = NULL;
PyAPI_FUNC(int) PyTraceBack_Here(PyFrameObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyTraceBack_Here)
    int result = (int) __target__PyTraceBack_Here(a);
    STATS_AFTER(PyTraceBack_Here)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(int) PyTraceBack_Print(PyObject* a, PyObject* b) {
    unimplemented("PyTraceBack_Print"); exit(-1);
}
STATS_CONTAINER(PyTraceMalloc_Track, PyTraceBack_Here)
int (*__target__PyTraceMalloc_Track)(unsigned int, uintptr_t, size_t) = NULL;
PyAPI_FUNC(int) PyTraceMalloc_Track(unsigned int a, uintptr_t b, size_t c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    STATS_BEFORE(PyTraceMalloc_Track)
    int result = (int) __target__PyTraceMalloc_Track(a, b, c);
    STATS_AFTER(PyTraceMalloc_Track)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyTraceMalloc_Untrack, PyTraceMalloc_Track)
int (*__target__PyTraceMalloc_Untrack)(unsigned int, uintptr_t) = NULL;
PyAPI_FUNC(int) PyTraceMalloc_Untrack(unsigned int a, uintptr_t b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(PyTraceMalloc_Untrack)
    int result = (int) __target__PyTraceMalloc_Untrack(a, b);
    STATS_AFTER(PyTraceMalloc_Untrack)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(PyFrameObject*) PyTruffleFrame_New(PyThreadState* a, PyCodeObject* b, PyObject* c, PyObject* d) {
    unimplemented("PyTruffleFrame_New"); exit(-1);
}
STATS_CONTAINER(PyTruffle_Debug, PyTraceMalloc_Untrack)
int (*__target__PyTruffle_Debug)(void*) = NULL;
PyAPI_FUNC(int) PyTruffle_Debug(void* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyTruffle_Debug)
    int result = (int) __target__PyTruffle_Debug(a);
    STATS_AFTER(PyTruffle_Debug)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyTruffle_DebugTrace, PyTruffle_Debug)
void (*__target__PyTruffle_DebugTrace)() = NULL;
PyAPI_FUNC(void) PyTruffle_DebugTrace() {
    LOGS("");
    STATS_BEFORE(PyTruffle_DebugTrace)
    __target__PyTruffle_DebugTrace();
    STATS_AFTER(PyTruffle_DebugTrace)
    LOG_AFTER_VOID
}
PyAPI_FUNC(PyObject*) PyTruffle_SeqIter_New(PyObject* a) {
    unimplemented("PyTruffle_SeqIter_New"); exit(-1);
}
STATS_CONTAINER(PyTruffle_ToNative, PyTruffle_DebugTrace)
int (*__target__PyTruffle_ToNative)(void*) = NULL;
PyAPI_FUNC(int) PyTruffle_ToNative(void* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyTruffle_ToNative)
    int result = (int) __target__PyTruffle_ToNative(a);
    STATS_AFTER(PyTruffle_ToNative)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyTuple_GetItem, PyTruffle_ToNative)
PyObject* (*__target__PyTuple_GetItem)(PyObject*, Py_ssize_t) = NULL;
PyAPI_FUNC(PyObject*) PyTuple_GetItem(PyObject* a, Py_ssize_t b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(PyTuple_GetItem)
    PyObject* result = (PyObject*) __target__PyTuple_GetItem(a, b);
    STATS_AFTER(PyTuple_GetItem)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyTuple_GetSlice, PyTuple_GetItem)
PyObject* (*__target__PyTuple_GetSlice)(PyObject*, Py_ssize_t, Py_ssize_t) = NULL;
PyAPI_FUNC(PyObject*) PyTuple_GetSlice(PyObject* a, Py_ssize_t b, Py_ssize_t c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    STATS_BEFORE(PyTuple_GetSlice)
    PyObject* result = (PyObject*) __target__PyTuple_GetSlice(a, b, c);
    STATS_AFTER(PyTuple_GetSlice)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyTuple_New, PyTuple_GetSlice)
PyObject* (*__target__PyTuple_New)(Py_ssize_t) = NULL;
PyAPI_FUNC(PyObject*) PyTuple_New(Py_ssize_t a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyTuple_New)
    PyObject* result = (PyObject*) __target__PyTuple_New(a);
    STATS_AFTER(PyTuple_New)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyTuple_SetItem, PyTuple_New)
int (*__target__PyTuple_SetItem)(PyObject*, Py_ssize_t, PyObject*) = NULL;
PyAPI_FUNC(int) PyTuple_SetItem(PyObject* a, Py_ssize_t b, PyObject* c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    STATS_BEFORE(PyTuple_SetItem)
    int result = (int) __target__PyTuple_SetItem(a, b, c);
    STATS_AFTER(PyTuple_SetItem)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyTuple_Size, PyTuple_SetItem)
Py_ssize_t (*__target__PyTuple_Size)(PyObject*) = NULL;
PyAPI_FUNC(Py_ssize_t) PyTuple_Size(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyTuple_Size)
    Py_ssize_t result = (Py_ssize_t) __target__PyTuple_Size(a);
    STATS_AFTER(PyTuple_Size)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(unsigned int) PyType_ClearCache() {
    unimplemented("PyType_ClearCache"); exit(-1);
}
STATS_CONTAINER(PyType_FromModuleAndSpec, PyTuple_Size)
PyObject* (*__target__PyType_FromModuleAndSpec)(PyObject*, PyType_Spec*, PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyType_FromModuleAndSpec(PyObject* a, PyType_Spec* b, PyObject* c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    STATS_BEFORE(PyType_FromModuleAndSpec)
    PyObject* result = (PyObject*) __target__PyType_FromModuleAndSpec(a, b, c);
    STATS_AFTER(PyType_FromModuleAndSpec)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyType_FromSpec, PyType_FromModuleAndSpec)
PyObject* (*__target__PyType_FromSpec)(PyType_Spec*) = NULL;
PyAPI_FUNC(PyObject*) PyType_FromSpec(PyType_Spec* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyType_FromSpec)
    PyObject* result = (PyObject*) __target__PyType_FromSpec(a);
    STATS_AFTER(PyType_FromSpec)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyType_FromSpecWithBases, PyType_FromSpec)
PyObject* (*__target__PyType_FromSpecWithBases)(PyType_Spec*, PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyType_FromSpecWithBases(PyType_Spec* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(PyType_FromSpecWithBases)
    PyObject* result = (PyObject*) __target__PyType_FromSpecWithBases(a, b);
    STATS_AFTER(PyType_FromSpecWithBases)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(PyObject*) PyType_FromSpecWithBasesAndMeta(PyType_Spec* a, PyObject* b, PyTypeObject* c) {
    unimplemented("PyType_FromSpecWithBasesAndMeta"); exit(-1);
}
STATS_CONTAINER(PyType_GenericAlloc, PyType_FromSpecWithBases)
PyObject* (*__target__PyType_GenericAlloc)(PyTypeObject*, Py_ssize_t) = NULL;
PyAPI_FUNC(PyObject*) PyType_GenericAlloc(PyTypeObject* a, Py_ssize_t b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(PyType_GenericAlloc)
    PyObject* result = (PyObject*) __target__PyType_GenericAlloc(a, b);
    STATS_AFTER(PyType_GenericAlloc)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyType_GenericNew, PyType_GenericAlloc)
PyObject* (*__target__PyType_GenericNew)(PyTypeObject*, PyObject*, PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyType_GenericNew(PyTypeObject* a, PyObject* b, PyObject* c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    STATS_BEFORE(PyType_GenericNew)
    PyObject* result = (PyObject*) __target__PyType_GenericNew(a, b, c);
    STATS_AFTER(PyType_GenericNew)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyType_GetFlags, PyType_GenericNew)
unsigned long (*__target__PyType_GetFlags)(PyTypeObject*) = NULL;
PyAPI_FUNC(unsigned long) PyType_GetFlags(PyTypeObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyType_GetFlags)
    unsigned long result = (unsigned long) __target__PyType_GetFlags(a);
    STATS_AFTER(PyType_GetFlags)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyType_GetModule, PyType_GetFlags)
PyObject* (*__target__PyType_GetModule)(PyTypeObject*) = NULL;
PyAPI_FUNC(PyObject*) PyType_GetModule(PyTypeObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyType_GetModule)
    PyObject* result = (PyObject*) __target__PyType_GetModule(a);
    STATS_AFTER(PyType_GetModule)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyType_GetModuleState, PyType_GetModule)
void* (*__target__PyType_GetModuleState)(PyTypeObject*) = NULL;
PyAPI_FUNC(void*) PyType_GetModuleState(PyTypeObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyType_GetModuleState)
    void* result = (void*) __target__PyType_GetModuleState(a);
    STATS_AFTER(PyType_GetModuleState)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyType_GetSlot, PyType_GetModuleState)
void* (*__target__PyType_GetSlot)(PyTypeObject*, int) = NULL;
PyAPI_FUNC(void*) PyType_GetSlot(PyTypeObject* a, int b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(PyType_GetSlot)
    void* result = (void*) __target__PyType_GetSlot(a, b);
    STATS_AFTER(PyType_GetSlot)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyType_IsSubtype, PyType_GetSlot)
int (*__target__PyType_IsSubtype)(PyTypeObject*, PyTypeObject*) = NULL;
PyAPI_FUNC(int) PyType_IsSubtype(PyTypeObject* a, PyTypeObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(PyType_IsSubtype)
    int result = (int) __target__PyType_IsSubtype(a, b);
    STATS_AFTER(PyType_IsSubtype)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyType_Modified, PyType_IsSubtype)
void (*__target__PyType_Modified)(PyTypeObject*) = NULL;
PyAPI_FUNC(void) PyType_Modified(PyTypeObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyType_Modified)
    __target__PyType_Modified(a);
    STATS_AFTER(PyType_Modified)
    LOG_AFTER_VOID
}
STATS_CONTAINER(PyType_Ready, PyType_Modified)
int (*__target__PyType_Ready)(PyTypeObject*) = NULL;
PyAPI_FUNC(int) PyType_Ready(PyTypeObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyType_Ready)
    int result = (int) __target__PyType_Ready(a);
    STATS_AFTER(PyType_Ready)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(PyObject*) PyUnicodeDecodeError_Create(const char* a, const char* b, Py_ssize_t c, Py_ssize_t d, Py_ssize_t e, const char* f) {
    unimplemented("PyUnicodeDecodeError_Create"); exit(-1);
}
PyAPI_FUNC(PyObject*) PyUnicodeDecodeError_GetEncoding(PyObject* a) {
    unimplemented("PyUnicodeDecodeError_GetEncoding"); exit(-1);
}
PyAPI_FUNC(int) PyUnicodeDecodeError_GetEnd(PyObject* a, Py_ssize_t* b) {
    unimplemented("PyUnicodeDecodeError_GetEnd"); exit(-1);
}
PyAPI_FUNC(PyObject*) PyUnicodeDecodeError_GetObject(PyObject* a) {
    unimplemented("PyUnicodeDecodeError_GetObject"); exit(-1);
}
PyAPI_FUNC(PyObject*) PyUnicodeDecodeError_GetReason(PyObject* a) {
    unimplemented("PyUnicodeDecodeError_GetReason"); exit(-1);
}
PyAPI_FUNC(int) PyUnicodeDecodeError_GetStart(PyObject* a, Py_ssize_t* b) {
    unimplemented("PyUnicodeDecodeError_GetStart"); exit(-1);
}
PyAPI_FUNC(int) PyUnicodeDecodeError_SetEnd(PyObject* a, Py_ssize_t b) {
    unimplemented("PyUnicodeDecodeError_SetEnd"); exit(-1);
}
PyAPI_FUNC(int) PyUnicodeDecodeError_SetReason(PyObject* a, const char* b) {
    unimplemented("PyUnicodeDecodeError_SetReason"); exit(-1);
}
PyAPI_FUNC(int) PyUnicodeDecodeError_SetStart(PyObject* a, Py_ssize_t b) {
    unimplemented("PyUnicodeDecodeError_SetStart"); exit(-1);
}
PyAPI_FUNC(PyObject*) PyUnicodeEncodeError_Create(const char* a, const Py_UNICODE* b, Py_ssize_t c, Py_ssize_t d, Py_ssize_t e, const char* f) {
    unimplemented("PyUnicodeEncodeError_Create"); exit(-1);
}
PyAPI_FUNC(PyObject*) PyUnicodeEncodeError_GetEncoding(PyObject* a) {
    unimplemented("PyUnicodeEncodeError_GetEncoding"); exit(-1);
}
PyAPI_FUNC(int) PyUnicodeEncodeError_GetEnd(PyObject* a, Py_ssize_t* b) {
    unimplemented("PyUnicodeEncodeError_GetEnd"); exit(-1);
}
PyAPI_FUNC(PyObject*) PyUnicodeEncodeError_GetObject(PyObject* a) {
    unimplemented("PyUnicodeEncodeError_GetObject"); exit(-1);
}
PyAPI_FUNC(PyObject*) PyUnicodeEncodeError_GetReason(PyObject* a) {
    unimplemented("PyUnicodeEncodeError_GetReason"); exit(-1);
}
PyAPI_FUNC(int) PyUnicodeEncodeError_GetStart(PyObject* a, Py_ssize_t* b) {
    unimplemented("PyUnicodeEncodeError_GetStart"); exit(-1);
}
PyAPI_FUNC(int) PyUnicodeEncodeError_SetEnd(PyObject* a, Py_ssize_t b) {
    unimplemented("PyUnicodeEncodeError_SetEnd"); exit(-1);
}
PyAPI_FUNC(int) PyUnicodeEncodeError_SetReason(PyObject* a, const char* b) {
    unimplemented("PyUnicodeEncodeError_SetReason"); exit(-1);
}
PyAPI_FUNC(int) PyUnicodeEncodeError_SetStart(PyObject* a, Py_ssize_t b) {
    unimplemented("PyUnicodeEncodeError_SetStart"); exit(-1);
}
PyAPI_FUNC(PyObject*) PyUnicodeTranslateError_Create(const Py_UNICODE* a, Py_ssize_t b, Py_ssize_t c, Py_ssize_t d, const char* e) {
    unimplemented("PyUnicodeTranslateError_Create"); exit(-1);
}
PyAPI_FUNC(int) PyUnicodeTranslateError_GetEnd(PyObject* a, Py_ssize_t* b) {
    unimplemented("PyUnicodeTranslateError_GetEnd"); exit(-1);
}
PyAPI_FUNC(PyObject*) PyUnicodeTranslateError_GetObject(PyObject* a) {
    unimplemented("PyUnicodeTranslateError_GetObject"); exit(-1);
}
PyAPI_FUNC(PyObject*) PyUnicodeTranslateError_GetReason(PyObject* a) {
    unimplemented("PyUnicodeTranslateError_GetReason"); exit(-1);
}
PyAPI_FUNC(int) PyUnicodeTranslateError_GetStart(PyObject* a, Py_ssize_t* b) {
    unimplemented("PyUnicodeTranslateError_GetStart"); exit(-1);
}
PyAPI_FUNC(int) PyUnicodeTranslateError_SetEnd(PyObject* a, Py_ssize_t b) {
    unimplemented("PyUnicodeTranslateError_SetEnd"); exit(-1);
}
PyAPI_FUNC(int) PyUnicodeTranslateError_SetReason(PyObject* a, const char* b) {
    unimplemented("PyUnicodeTranslateError_SetReason"); exit(-1);
}
PyAPI_FUNC(int) PyUnicodeTranslateError_SetStart(PyObject* a, Py_ssize_t b) {
    unimplemented("PyUnicodeTranslateError_SetStart"); exit(-1);
}
STATS_CONTAINER(PyUnicode_Append, PyType_Ready)
void (*__target__PyUnicode_Append)(PyObject**, PyObject*) = NULL;
PyAPI_FUNC(void) PyUnicode_Append(PyObject** a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(PyUnicode_Append)
    __target__PyUnicode_Append(a, b);
    STATS_AFTER(PyUnicode_Append)
    LOG_AFTER_VOID
}
STATS_CONTAINER(PyUnicode_AppendAndDel, PyUnicode_Append)
void (*__target__PyUnicode_AppendAndDel)(PyObject**, PyObject*) = NULL;
PyAPI_FUNC(void) PyUnicode_AppendAndDel(PyObject** a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(PyUnicode_AppendAndDel)
    __target__PyUnicode_AppendAndDel(a, b);
    STATS_AFTER(PyUnicode_AppendAndDel)
    LOG_AFTER_VOID
}
STATS_CONTAINER(PyUnicode_AsASCIIString, PyUnicode_AppendAndDel)
PyObject* (*__target__PyUnicode_AsASCIIString)(PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyUnicode_AsASCIIString(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyUnicode_AsASCIIString)
    PyObject* result = (PyObject*) __target__PyUnicode_AsASCIIString(a);
    STATS_AFTER(PyUnicode_AsASCIIString)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(PyObject*) PyUnicode_AsCharmapString(PyObject* a, PyObject* b) {
    unimplemented("PyUnicode_AsCharmapString"); exit(-1);
}
PyAPI_FUNC(PyObject*) PyUnicode_AsDecodedObject(PyObject* a, const char* b, const char* c) {
    unimplemented("PyUnicode_AsDecodedObject"); exit(-1);
}
PyAPI_FUNC(PyObject*) PyUnicode_AsDecodedUnicode(PyObject* a, const char* b, const char* c) {
    unimplemented("PyUnicode_AsDecodedUnicode"); exit(-1);
}
PyAPI_FUNC(PyObject*) PyUnicode_AsEncodedObject(PyObject* a, const char* b, const char* c) {
    unimplemented("PyUnicode_AsEncodedObject"); exit(-1);
}
STATS_CONTAINER(PyUnicode_AsEncodedString, PyUnicode_AsASCIIString)
PyObject* (*__target__PyUnicode_AsEncodedString)(PyObject*, const char*, const char*) = NULL;
PyAPI_FUNC(PyObject*) PyUnicode_AsEncodedString(PyObject* a, const char* b, const char* c) {
    LOG("0x%lx '%s'(0x%lx) '%s'(0x%lx)", (unsigned long) a, b?b:"<null>", (unsigned long) b, c?c:"<null>", (unsigned long) c);
    STATS_BEFORE(PyUnicode_AsEncodedString)
    PyObject* result = (PyObject*) __target__PyUnicode_AsEncodedString(a, b, c);
    STATS_AFTER(PyUnicode_AsEncodedString)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(PyObject*) PyUnicode_AsEncodedUnicode(PyObject* a, const char* b, const char* c) {
    unimplemented("PyUnicode_AsEncodedUnicode"); exit(-1);
}
STATS_CONTAINER(PyUnicode_AsLatin1String, PyUnicode_AsEncodedString)
PyObject* (*__target__PyUnicode_AsLatin1String)(PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyUnicode_AsLatin1String(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyUnicode_AsLatin1String)
    PyObject* result = (PyObject*) __target__PyUnicode_AsLatin1String(a);
    STATS_AFTER(PyUnicode_AsLatin1String)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(PyObject*) PyUnicode_AsRawUnicodeEscapeString(PyObject* a) {
    unimplemented("PyUnicode_AsRawUnicodeEscapeString"); exit(-1);
}
STATS_CONTAINER(PyUnicode_AsUCS4, PyUnicode_AsLatin1String)
Py_UCS4* (*__target__PyUnicode_AsUCS4)(PyObject*, Py_UCS4*, Py_ssize_t, int) = NULL;
PyAPI_FUNC(Py_UCS4*) PyUnicode_AsUCS4(PyObject* a, Py_UCS4* b, Py_ssize_t c, int d) {
    LOG("0x%lx 0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c, (unsigned long) d);
    STATS_BEFORE(PyUnicode_AsUCS4)
    Py_UCS4* result = (Py_UCS4*) __target__PyUnicode_AsUCS4(a, b, c, d);
    STATS_AFTER(PyUnicode_AsUCS4)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyUnicode_AsUCS4Copy, PyUnicode_AsUCS4)
Py_UCS4* (*__target__PyUnicode_AsUCS4Copy)(PyObject*) = NULL;
PyAPI_FUNC(Py_UCS4*) PyUnicode_AsUCS4Copy(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyUnicode_AsUCS4Copy)
    Py_UCS4* result = (Py_UCS4*) __target__PyUnicode_AsUCS4Copy(a);
    STATS_AFTER(PyUnicode_AsUCS4Copy)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(PyObject*) PyUnicode_AsUTF16String(PyObject* a) {
    unimplemented("PyUnicode_AsUTF16String"); exit(-1);
}
PyAPI_FUNC(PyObject*) PyUnicode_AsUTF32String(PyObject* a) {
    unimplemented("PyUnicode_AsUTF32String"); exit(-1);
}
STATS_CONTAINER(PyUnicode_AsUTF8, PyUnicode_AsUCS4Copy)
const char* (*__target__PyUnicode_AsUTF8)(PyObject*) = NULL;
PyAPI_FUNC(const char*) PyUnicode_AsUTF8(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyUnicode_AsUTF8)
    const char* result = (const char*) __target__PyUnicode_AsUTF8(a);
    STATS_AFTER(PyUnicode_AsUTF8)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyUnicode_AsUTF8AndSize, PyUnicode_AsUTF8)
const char* (*__target__PyUnicode_AsUTF8AndSize)(PyObject*, Py_ssize_t*) = NULL;
PyAPI_FUNC(const char*) PyUnicode_AsUTF8AndSize(PyObject* a, Py_ssize_t* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(PyUnicode_AsUTF8AndSize)
    const char* result = (const char*) __target__PyUnicode_AsUTF8AndSize(a, b);
    STATS_AFTER(PyUnicode_AsUTF8AndSize)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyUnicode_AsUTF8String, PyUnicode_AsUTF8AndSize)
PyObject* (*__target__PyUnicode_AsUTF8String)(PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyUnicode_AsUTF8String(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyUnicode_AsUTF8String)
    PyObject* result = (PyObject*) __target__PyUnicode_AsUTF8String(a);
    STATS_AFTER(PyUnicode_AsUTF8String)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyUnicode_AsUnicode, PyUnicode_AsUTF8String)
Py_UNICODE* (*__target__PyUnicode_AsUnicode)(PyObject*) = NULL;
PyAPI_FUNC(Py_UNICODE*) PyUnicode_AsUnicode(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyUnicode_AsUnicode)
    Py_UNICODE* result = (Py_UNICODE*) __target__PyUnicode_AsUnicode(a);
    STATS_AFTER(PyUnicode_AsUnicode)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyUnicode_AsUnicodeAndSize, PyUnicode_AsUnicode)
Py_UNICODE* (*__target__PyUnicode_AsUnicodeAndSize)(PyObject*, Py_ssize_t*) = NULL;
PyAPI_FUNC(Py_UNICODE*) PyUnicode_AsUnicodeAndSize(PyObject* a, Py_ssize_t* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(PyUnicode_AsUnicodeAndSize)
    Py_UNICODE* result = (Py_UNICODE*) __target__PyUnicode_AsUnicodeAndSize(a, b);
    STATS_AFTER(PyUnicode_AsUnicodeAndSize)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyUnicode_AsUnicodeEscapeString, PyUnicode_AsUnicodeAndSize)
PyObject* (*__target__PyUnicode_AsUnicodeEscapeString)(PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyUnicode_AsUnicodeEscapeString(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyUnicode_AsUnicodeEscapeString)
    PyObject* result = (PyObject*) __target__PyUnicode_AsUnicodeEscapeString(a);
    STATS_AFTER(PyUnicode_AsUnicodeEscapeString)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyUnicode_AsWideChar, PyUnicode_AsUnicodeEscapeString)
Py_ssize_t (*__target__PyUnicode_AsWideChar)(PyObject*, wchar_t*, Py_ssize_t) = NULL;
PyAPI_FUNC(Py_ssize_t) PyUnicode_AsWideChar(PyObject* a, wchar_t* b, Py_ssize_t c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    STATS_BEFORE(PyUnicode_AsWideChar)
    Py_ssize_t result = (Py_ssize_t) __target__PyUnicode_AsWideChar(a, b, c);
    STATS_AFTER(PyUnicode_AsWideChar)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(wchar_t*) PyUnicode_AsWideCharString(PyObject* a, Py_ssize_t* b) {
    unimplemented("PyUnicode_AsWideCharString"); exit(-1);
}
PyAPI_FUNC(PyObject*) PyUnicode_BuildEncodingMap(PyObject* a) {
    unimplemented("PyUnicode_BuildEncodingMap"); exit(-1);
}
STATS_CONTAINER(PyUnicode_Compare, PyUnicode_AsWideChar)
int (*__target__PyUnicode_Compare)(PyObject*, PyObject*) = NULL;
PyAPI_FUNC(int) PyUnicode_Compare(PyObject* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(PyUnicode_Compare)
    int result = (int) __target__PyUnicode_Compare(a, b);
    STATS_AFTER(PyUnicode_Compare)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(int) PyUnicode_CompareWithASCIIString(PyObject* a, const char* b) {
    unimplemented("PyUnicode_CompareWithASCIIString"); exit(-1);
}
STATS_CONTAINER(PyUnicode_Concat, PyUnicode_Compare)
PyObject* (*__target__PyUnicode_Concat)(PyObject*, PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyUnicode_Concat(PyObject* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(PyUnicode_Concat)
    PyObject* result = (PyObject*) __target__PyUnicode_Concat(a, b);
    STATS_AFTER(PyUnicode_Concat)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyUnicode_Contains, PyUnicode_Concat)
int (*__target__PyUnicode_Contains)(PyObject*, PyObject*) = NULL;
PyAPI_FUNC(int) PyUnicode_Contains(PyObject* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(PyUnicode_Contains)
    int result = (int) __target__PyUnicode_Contains(a, b);
    STATS_AFTER(PyUnicode_Contains)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(Py_ssize_t) PyUnicode_CopyCharacters(PyObject* a, Py_ssize_t b, PyObject* c, Py_ssize_t d, Py_ssize_t e) {
    unimplemented("PyUnicode_CopyCharacters"); exit(-1);
}
PyAPI_FUNC(Py_ssize_t) PyUnicode_Count(PyObject* a, PyObject* b, Py_ssize_t c, Py_ssize_t d) {
    unimplemented("PyUnicode_Count"); exit(-1);
}
STATS_CONTAINER(PyUnicode_Decode, PyUnicode_Contains)
PyObject* (*__target__PyUnicode_Decode)(const char*, Py_ssize_t, const char*, const char*) = NULL;
PyAPI_FUNC(PyObject*) PyUnicode_Decode(const char* a, Py_ssize_t b, const char* c, const char* d) {
    LOG("'%s'(0x%lx) 0x%lx '%s'(0x%lx) '%s'(0x%lx)", a?a:"<null>", (unsigned long) a, (unsigned long) b, c?c:"<null>", (unsigned long) c, d?d:"<null>", (unsigned long) d);
    STATS_BEFORE(PyUnicode_Decode)
    PyObject* result = (PyObject*) __target__PyUnicode_Decode(a, b, c, d);
    STATS_AFTER(PyUnicode_Decode)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyUnicode_DecodeASCII, PyUnicode_Decode)
PyObject* (*__target__PyUnicode_DecodeASCII)(const char*, Py_ssize_t, const char*) = NULL;
PyAPI_FUNC(PyObject*) PyUnicode_DecodeASCII(const char* a, Py_ssize_t b, const char* c) {
    LOG("'%s'(0x%lx) 0x%lx '%s'(0x%lx)", a?a:"<null>", (unsigned long) a, (unsigned long) b, c?c:"<null>", (unsigned long) c);
    STATS_BEFORE(PyUnicode_DecodeASCII)
    PyObject* result = (PyObject*) __target__PyUnicode_DecodeASCII(a, b, c);
    STATS_AFTER(PyUnicode_DecodeASCII)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(PyObject*) PyUnicode_DecodeCharmap(const char* a, Py_ssize_t b, PyObject* c, const char* d) {
    unimplemented("PyUnicode_DecodeCharmap"); exit(-1);
}
STATS_CONTAINER(PyUnicode_DecodeFSDefault, PyUnicode_DecodeASCII)
PyObject* (*__target__PyUnicode_DecodeFSDefault)(const char*) = NULL;
PyAPI_FUNC(PyObject*) PyUnicode_DecodeFSDefault(const char* a) {
    LOG("'%s'(0x%lx)", a?a:"<null>", (unsigned long) a);
    STATS_BEFORE(PyUnicode_DecodeFSDefault)
    PyObject* result = (PyObject*) __target__PyUnicode_DecodeFSDefault(a);
    STATS_AFTER(PyUnicode_DecodeFSDefault)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyUnicode_DecodeFSDefaultAndSize, PyUnicode_DecodeFSDefault)
PyObject* (*__target__PyUnicode_DecodeFSDefaultAndSize)(const char*, Py_ssize_t) = NULL;
PyAPI_FUNC(PyObject*) PyUnicode_DecodeFSDefaultAndSize(const char* a, Py_ssize_t b) {
    LOG("'%s'(0x%lx) 0x%lx", a?a:"<null>", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(PyUnicode_DecodeFSDefaultAndSize)
    PyObject* result = (PyObject*) __target__PyUnicode_DecodeFSDefaultAndSize(a, b);
    STATS_AFTER(PyUnicode_DecodeFSDefaultAndSize)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyUnicode_DecodeLatin1, PyUnicode_DecodeFSDefaultAndSize)
PyObject* (*__target__PyUnicode_DecodeLatin1)(const char*, Py_ssize_t, const char*) = NULL;
PyAPI_FUNC(PyObject*) PyUnicode_DecodeLatin1(const char* a, Py_ssize_t b, const char* c) {
    LOG("'%s'(0x%lx) 0x%lx '%s'(0x%lx)", a?a:"<null>", (unsigned long) a, (unsigned long) b, c?c:"<null>", (unsigned long) c);
    STATS_BEFORE(PyUnicode_DecodeLatin1)
    PyObject* result = (PyObject*) __target__PyUnicode_DecodeLatin1(a, b, c);
    STATS_AFTER(PyUnicode_DecodeLatin1)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(PyObject*) PyUnicode_DecodeLocale(const char* a, const char* b) {
    unimplemented("PyUnicode_DecodeLocale"); exit(-1);
}
PyAPI_FUNC(PyObject*) PyUnicode_DecodeLocaleAndSize(const char* a, Py_ssize_t b, const char* c) {
    unimplemented("PyUnicode_DecodeLocaleAndSize"); exit(-1);
}
PyAPI_FUNC(PyObject*) PyUnicode_DecodeRawUnicodeEscape(const char* a, Py_ssize_t b, const char* c) {
    unimplemented("PyUnicode_DecodeRawUnicodeEscape"); exit(-1);
}
PyAPI_FUNC(PyObject*) PyUnicode_DecodeUTF16(const char* a, Py_ssize_t b, const char* c, int* d) {
    unimplemented("PyUnicode_DecodeUTF16"); exit(-1);
}
PyAPI_FUNC(PyObject*) PyUnicode_DecodeUTF16Stateful(const char* a, Py_ssize_t b, const char* c, int* d, Py_ssize_t* e) {
    unimplemented("PyUnicode_DecodeUTF16Stateful"); exit(-1);
}
STATS_CONTAINER(PyUnicode_DecodeUTF32, PyUnicode_DecodeLatin1)
PyObject* (*__target__PyUnicode_DecodeUTF32)(const char*, Py_ssize_t, const char*, int*) = NULL;
PyAPI_FUNC(PyObject*) PyUnicode_DecodeUTF32(const char* a, Py_ssize_t b, const char* c, int* d) {
    LOG("'%s'(0x%lx) 0x%lx '%s'(0x%lx) 0x%lx", a?a:"<null>", (unsigned long) a, (unsigned long) b, c?c:"<null>", (unsigned long) c, (unsigned long) d);
    STATS_BEFORE(PyUnicode_DecodeUTF32)
    PyObject* result = (PyObject*) __target__PyUnicode_DecodeUTF32(a, b, c, d);
    STATS_AFTER(PyUnicode_DecodeUTF32)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(PyObject*) PyUnicode_DecodeUTF32Stateful(const char* a, Py_ssize_t b, const char* c, int* d, Py_ssize_t* e) {
    unimplemented("PyUnicode_DecodeUTF32Stateful"); exit(-1);
}
PyAPI_FUNC(PyObject*) PyUnicode_DecodeUTF7(const char* a, Py_ssize_t b, const char* c) {
    unimplemented("PyUnicode_DecodeUTF7"); exit(-1);
}
PyAPI_FUNC(PyObject*) PyUnicode_DecodeUTF7Stateful(const char* a, Py_ssize_t b, const char* c, Py_ssize_t* d) {
    unimplemented("PyUnicode_DecodeUTF7Stateful"); exit(-1);
}
STATS_CONTAINER(PyUnicode_DecodeUTF8, PyUnicode_DecodeUTF32)
PyObject* (*__target__PyUnicode_DecodeUTF8)(const char*, Py_ssize_t, const char*) = NULL;
PyAPI_FUNC(PyObject*) PyUnicode_DecodeUTF8(const char* a, Py_ssize_t b, const char* c) {
    LOG("'%s'(0x%lx) 0x%lx '%s'(0x%lx)", a?a:"<null>", (unsigned long) a, (unsigned long) b, c?c:"<null>", (unsigned long) c);
    STATS_BEFORE(PyUnicode_DecodeUTF8)
    PyObject* result = (PyObject*) __target__PyUnicode_DecodeUTF8(a, b, c);
    STATS_AFTER(PyUnicode_DecodeUTF8)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyUnicode_DecodeUTF8Stateful, PyUnicode_DecodeUTF8)
PyObject* (*__target__PyUnicode_DecodeUTF8Stateful)(const char*, Py_ssize_t, const char*, Py_ssize_t*) = NULL;
PyAPI_FUNC(PyObject*) PyUnicode_DecodeUTF8Stateful(const char* a, Py_ssize_t b, const char* c, Py_ssize_t* d) {
    LOG("'%s'(0x%lx) 0x%lx '%s'(0x%lx) 0x%lx", a?a:"<null>", (unsigned long) a, (unsigned long) b, c?c:"<null>", (unsigned long) c, (unsigned long) d);
    STATS_BEFORE(PyUnicode_DecodeUTF8Stateful)
    PyObject* result = (PyObject*) __target__PyUnicode_DecodeUTF8Stateful(a, b, c, d);
    STATS_AFTER(PyUnicode_DecodeUTF8Stateful)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(PyObject*) PyUnicode_DecodeUnicodeEscape(const char* a, Py_ssize_t b, const char* c) {
    unimplemented("PyUnicode_DecodeUnicodeEscape"); exit(-1);
}
PyAPI_FUNC(PyObject*) PyUnicode_Encode(const Py_UNICODE* a, Py_ssize_t b, const char* c, const char* d) {
    unimplemented("PyUnicode_Encode"); exit(-1);
}
PyAPI_FUNC(PyObject*) PyUnicode_EncodeASCII(const Py_UNICODE* a, Py_ssize_t b, const char* c) {
    unimplemented("PyUnicode_EncodeASCII"); exit(-1);
}
PyAPI_FUNC(PyObject*) PyUnicode_EncodeCharmap(const Py_UNICODE* a, Py_ssize_t b, PyObject* c, const char* d) {
    unimplemented("PyUnicode_EncodeCharmap"); exit(-1);
}
PyAPI_FUNC(int) PyUnicode_EncodeDecimal(Py_UNICODE* a, Py_ssize_t b, char* c, const char* d) {
    unimplemented("PyUnicode_EncodeDecimal"); exit(-1);
}
STATS_CONTAINER(PyUnicode_EncodeFSDefault, PyUnicode_DecodeUTF8Stateful)
PyObject* (*__target__PyUnicode_EncodeFSDefault)(PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyUnicode_EncodeFSDefault(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyUnicode_EncodeFSDefault)
    PyObject* result = (PyObject*) __target__PyUnicode_EncodeFSDefault(a);
    STATS_AFTER(PyUnicode_EncodeFSDefault)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(PyObject*) PyUnicode_EncodeLatin1(const Py_UNICODE* a, Py_ssize_t b, const char* c) {
    unimplemented("PyUnicode_EncodeLatin1"); exit(-1);
}
PyAPI_FUNC(PyObject*) PyUnicode_EncodeLocale(PyObject* a, const char* b) {
    unimplemented("PyUnicode_EncodeLocale"); exit(-1);
}
PyAPI_FUNC(PyObject*) PyUnicode_EncodeRawUnicodeEscape(const Py_UNICODE* a, Py_ssize_t b) {
    unimplemented("PyUnicode_EncodeRawUnicodeEscape"); exit(-1);
}
PyAPI_FUNC(PyObject*) PyUnicode_EncodeUTF16(const Py_UNICODE* a, Py_ssize_t b, const char* c, int d) {
    unimplemented("PyUnicode_EncodeUTF16"); exit(-1);
}
PyAPI_FUNC(PyObject*) PyUnicode_EncodeUTF32(const Py_UNICODE* a, Py_ssize_t b, const char* c, int d) {
    unimplemented("PyUnicode_EncodeUTF32"); exit(-1);
}
PyAPI_FUNC(PyObject*) PyUnicode_EncodeUTF7(const Py_UNICODE* a, Py_ssize_t b, int c, int d, const char* e) {
    unimplemented("PyUnicode_EncodeUTF7"); exit(-1);
}
PyAPI_FUNC(PyObject*) PyUnicode_EncodeUTF8(const Py_UNICODE* a, Py_ssize_t b, const char* c) {
    unimplemented("PyUnicode_EncodeUTF8"); exit(-1);
}
PyAPI_FUNC(PyObject*) PyUnicode_EncodeUnicodeEscape(const Py_UNICODE* a, Py_ssize_t b) {
    unimplemented("PyUnicode_EncodeUnicodeEscape"); exit(-1);
}
PyAPI_FUNC(int) PyUnicode_FSConverter(PyObject* a, void* b) {
    unimplemented("PyUnicode_FSConverter"); exit(-1);
}
PyAPI_FUNC(int) PyUnicode_FSDecoder(PyObject* a, void* b) {
    unimplemented("PyUnicode_FSDecoder"); exit(-1);
}
PyAPI_FUNC(Py_ssize_t) PyUnicode_Fill(PyObject* a, Py_ssize_t b, Py_ssize_t c, Py_UCS4 d) {
    unimplemented("PyUnicode_Fill"); exit(-1);
}
PyAPI_FUNC(Py_ssize_t) PyUnicode_Find(PyObject* a, PyObject* b, Py_ssize_t c, Py_ssize_t d, int e) {
    unimplemented("PyUnicode_Find"); exit(-1);
}
STATS_CONTAINER(PyUnicode_FindChar, PyUnicode_EncodeFSDefault)
Py_ssize_t (*__target__PyUnicode_FindChar)(PyObject*, Py_UCS4, Py_ssize_t, Py_ssize_t, int) = NULL;
PyAPI_FUNC(Py_ssize_t) PyUnicode_FindChar(PyObject* a, Py_UCS4 b, Py_ssize_t c, Py_ssize_t d, int e) {
    LOG("0x%lx 0x%lx 0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c, (unsigned long) d, (unsigned long) e);
    STATS_BEFORE(PyUnicode_FindChar)
    Py_ssize_t result = (Py_ssize_t) __target__PyUnicode_FindChar(a, b, c, d, e);
    STATS_AFTER(PyUnicode_FindChar)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyUnicode_Format, PyUnicode_FindChar)
PyObject* (*__target__PyUnicode_Format)(PyObject*, PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyUnicode_Format(PyObject* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(PyUnicode_Format)
    PyObject* result = (PyObject*) __target__PyUnicode_Format(a, b);
    STATS_AFTER(PyUnicode_Format)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyUnicode_FromEncodedObject, PyUnicode_Format)
PyObject* (*__target__PyUnicode_FromEncodedObject)(PyObject*, const char*, const char*) = NULL;
PyAPI_FUNC(PyObject*) PyUnicode_FromEncodedObject(PyObject* a, const char* b, const char* c) {
    LOG("0x%lx '%s'(0x%lx) '%s'(0x%lx)", (unsigned long) a, b?b:"<null>", (unsigned long) b, c?c:"<null>", (unsigned long) c);
    STATS_BEFORE(PyUnicode_FromEncodedObject)
    PyObject* result = (PyObject*) __target__PyUnicode_FromEncodedObject(a, b, c);
    STATS_AFTER(PyUnicode_FromEncodedObject)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyUnicode_FromFormatV, PyUnicode_FromEncodedObject)
PyObject* (*__target__PyUnicode_FromFormatV)(const char*, va_list) = NULL;
PyAPI_FUNC(PyObject*) PyUnicode_FromFormatV(const char* a, va_list b) {
    LOG("'%s'(0x%lx) 0x%lx", a?a:"<null>", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(PyUnicode_FromFormatV)
    PyObject* result = (PyObject*) __target__PyUnicode_FromFormatV(a, b);
    STATS_AFTER(PyUnicode_FromFormatV)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyUnicode_FromKindAndData, PyUnicode_FromFormatV)
PyObject* (*__target__PyUnicode_FromKindAndData)(int, const void*, Py_ssize_t) = NULL;
PyAPI_FUNC(PyObject*) PyUnicode_FromKindAndData(int a, const void* b, Py_ssize_t c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    STATS_BEFORE(PyUnicode_FromKindAndData)
    PyObject* result = (PyObject*) __target__PyUnicode_FromKindAndData(a, b, c);
    STATS_AFTER(PyUnicode_FromKindAndData)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyUnicode_FromObject, PyUnicode_FromKindAndData)
PyObject* (*__target__PyUnicode_FromObject)(PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyUnicode_FromObject(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyUnicode_FromObject)
    PyObject* result = (PyObject*) __target__PyUnicode_FromObject(a);
    STATS_AFTER(PyUnicode_FromObject)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyUnicode_FromOrdinal, PyUnicode_FromObject)
PyObject* (*__target__PyUnicode_FromOrdinal)(int) = NULL;
PyAPI_FUNC(PyObject*) PyUnicode_FromOrdinal(int a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyUnicode_FromOrdinal)
    PyObject* result = (PyObject*) __target__PyUnicode_FromOrdinal(a);
    STATS_AFTER(PyUnicode_FromOrdinal)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyUnicode_FromString, PyUnicode_FromOrdinal)
PyObject* (*__target__PyUnicode_FromString)(const char*) = NULL;
PyAPI_FUNC(PyObject*) PyUnicode_FromString(const char* a) {
    LOG("'%s'(0x%lx)", a?a:"<null>", (unsigned long) a);
    STATS_BEFORE(PyUnicode_FromString)
    PyObject* result = (PyObject*) __target__PyUnicode_FromString(a);
    STATS_AFTER(PyUnicode_FromString)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyUnicode_FromStringAndSize, PyUnicode_FromString)
PyObject* (*__target__PyUnicode_FromStringAndSize)(const char*, Py_ssize_t) = NULL;
PyAPI_FUNC(PyObject*) PyUnicode_FromStringAndSize(const char* a, Py_ssize_t b) {
    LOG("'%s'(0x%lx) 0x%lx", a?a:"<null>", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(PyUnicode_FromStringAndSize)
    PyObject* result = (PyObject*) __target__PyUnicode_FromStringAndSize(a, b);
    STATS_AFTER(PyUnicode_FromStringAndSize)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyUnicode_FromUnicode, PyUnicode_FromStringAndSize)
PyObject* (*__target__PyUnicode_FromUnicode)(const Py_UNICODE*, Py_ssize_t) = NULL;
PyAPI_FUNC(PyObject*) PyUnicode_FromUnicode(const Py_UNICODE* a, Py_ssize_t b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(PyUnicode_FromUnicode)
    PyObject* result = (PyObject*) __target__PyUnicode_FromUnicode(a, b);
    STATS_AFTER(PyUnicode_FromUnicode)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyUnicode_FromWideChar, PyUnicode_FromUnicode)
PyObject* (*__target__PyUnicode_FromWideChar)(const wchar_t*, Py_ssize_t) = NULL;
PyAPI_FUNC(PyObject*) PyUnicode_FromWideChar(const wchar_t* a, Py_ssize_t b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(PyUnicode_FromWideChar)
    PyObject* result = (PyObject*) __target__PyUnicode_FromWideChar(a, b);
    STATS_AFTER(PyUnicode_FromWideChar)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(const char*) PyUnicode_GetDefaultEncoding() {
    unimplemented("PyUnicode_GetDefaultEncoding"); exit(-1);
}
STATS_CONTAINER(PyUnicode_GetLength, PyUnicode_FromWideChar)
Py_ssize_t (*__target__PyUnicode_GetLength)(PyObject*) = NULL;
PyAPI_FUNC(Py_ssize_t) PyUnicode_GetLength(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyUnicode_GetLength)
    Py_ssize_t result = (Py_ssize_t) __target__PyUnicode_GetLength(a);
    STATS_AFTER(PyUnicode_GetLength)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(Py_ssize_t) PyUnicode_GetSize(PyObject* a) {
    unimplemented("PyUnicode_GetSize"); exit(-1);
}
STATS_CONTAINER(PyUnicode_InternFromString, PyUnicode_GetLength)
PyObject* (*__target__PyUnicode_InternFromString)(const char*) = NULL;
PyAPI_FUNC(PyObject*) PyUnicode_InternFromString(const char* a) {
    LOG("'%s'(0x%lx)", a?a:"<null>", (unsigned long) a);
    STATS_BEFORE(PyUnicode_InternFromString)
    PyObject* result = (PyObject*) __target__PyUnicode_InternFromString(a);
    STATS_AFTER(PyUnicode_InternFromString)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(void) PyUnicode_InternImmortal(PyObject** a) {
    unimplemented("PyUnicode_InternImmortal"); exit(-1);
}
STATS_CONTAINER(PyUnicode_InternInPlace, PyUnicode_InternFromString)
void (*__target__PyUnicode_InternInPlace)(PyObject**) = NULL;
PyAPI_FUNC(void) PyUnicode_InternInPlace(PyObject** a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyUnicode_InternInPlace)
    __target__PyUnicode_InternInPlace(a);
    STATS_AFTER(PyUnicode_InternInPlace)
    LOG_AFTER_VOID
}
PyAPI_FUNC(int) PyUnicode_IsIdentifier(PyObject* a) {
    unimplemented("PyUnicode_IsIdentifier"); exit(-1);
}
STATS_CONTAINER(PyUnicode_Join, PyUnicode_InternInPlace)
PyObject* (*__target__PyUnicode_Join)(PyObject*, PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyUnicode_Join(PyObject* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(PyUnicode_Join)
    PyObject* result = (PyObject*) __target__PyUnicode_Join(a, b);
    STATS_AFTER(PyUnicode_Join)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyUnicode_New, PyUnicode_Join)
PyObject* (*__target__PyUnicode_New)(Py_ssize_t, Py_UCS4) = NULL;
PyAPI_FUNC(PyObject*) PyUnicode_New(Py_ssize_t a, Py_UCS4 b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(PyUnicode_New)
    PyObject* result = (PyObject*) __target__PyUnicode_New(a, b);
    STATS_AFTER(PyUnicode_New)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(PyObject*) PyUnicode_Partition(PyObject* a, PyObject* b) {
    unimplemented("PyUnicode_Partition"); exit(-1);
}
PyAPI_FUNC(PyObject*) PyUnicode_RPartition(PyObject* a, PyObject* b) {
    unimplemented("PyUnicode_RPartition"); exit(-1);
}
PyAPI_FUNC(PyObject*) PyUnicode_RSplit(PyObject* a, PyObject* b, Py_ssize_t c) {
    unimplemented("PyUnicode_RSplit"); exit(-1);
}
STATS_CONTAINER(PyUnicode_ReadChar, PyUnicode_New)
Py_UCS4 (*__target__PyUnicode_ReadChar)(PyObject*, Py_ssize_t) = NULL;
PyAPI_FUNC(Py_UCS4) PyUnicode_ReadChar(PyObject* a, Py_ssize_t b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(PyUnicode_ReadChar)
    Py_UCS4 result = (Py_UCS4) __target__PyUnicode_ReadChar(a, b);
    STATS_AFTER(PyUnicode_ReadChar)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyUnicode_Replace, PyUnicode_ReadChar)
PyObject* (*__target__PyUnicode_Replace)(PyObject*, PyObject*, PyObject*, Py_ssize_t) = NULL;
PyAPI_FUNC(PyObject*) PyUnicode_Replace(PyObject* a, PyObject* b, PyObject* c, Py_ssize_t d) {
    LOG("0x%lx 0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c, (unsigned long) d);
    STATS_BEFORE(PyUnicode_Replace)
    PyObject* result = (PyObject*) __target__PyUnicode_Replace(a, b, c, d);
    STATS_AFTER(PyUnicode_Replace)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(int) PyUnicode_Resize(PyObject** a, Py_ssize_t b) {
    unimplemented("PyUnicode_Resize"); exit(-1);
}
PyAPI_FUNC(PyObject*) PyUnicode_RichCompare(PyObject* a, PyObject* b, int c) {
    unimplemented("PyUnicode_RichCompare"); exit(-1);
}
STATS_CONTAINER(PyUnicode_Split, PyUnicode_Replace)
PyObject* (*__target__PyUnicode_Split)(PyObject*, PyObject*, Py_ssize_t) = NULL;
PyAPI_FUNC(PyObject*) PyUnicode_Split(PyObject* a, PyObject* b, Py_ssize_t c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    STATS_BEFORE(PyUnicode_Split)
    PyObject* result = (PyObject*) __target__PyUnicode_Split(a, b, c);
    STATS_AFTER(PyUnicode_Split)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(PyObject*) PyUnicode_Splitlines(PyObject* a, int b) {
    unimplemented("PyUnicode_Splitlines"); exit(-1);
}
STATS_CONTAINER(PyUnicode_Substring, PyUnicode_Split)
PyObject* (*__target__PyUnicode_Substring)(PyObject*, Py_ssize_t, Py_ssize_t) = NULL;
PyAPI_FUNC(PyObject*) PyUnicode_Substring(PyObject* a, Py_ssize_t b, Py_ssize_t c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    STATS_BEFORE(PyUnicode_Substring)
    PyObject* result = (PyObject*) __target__PyUnicode_Substring(a, b, c);
    STATS_AFTER(PyUnicode_Substring)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyUnicode_Tailmatch, PyUnicode_Substring)
Py_ssize_t (*__target__PyUnicode_Tailmatch)(PyObject*, PyObject*, Py_ssize_t, Py_ssize_t, int) = NULL;
PyAPI_FUNC(Py_ssize_t) PyUnicode_Tailmatch(PyObject* a, PyObject* b, Py_ssize_t c, Py_ssize_t d, int e) {
    LOG("0x%lx 0x%lx 0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c, (unsigned long) d, (unsigned long) e);
    STATS_BEFORE(PyUnicode_Tailmatch)
    Py_ssize_t result = (Py_ssize_t) __target__PyUnicode_Tailmatch(a, b, c, d, e);
    STATS_AFTER(PyUnicode_Tailmatch)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(PyObject*) PyUnicode_TransformDecimalToASCII(Py_UNICODE* a, Py_ssize_t b) {
    unimplemented("PyUnicode_TransformDecimalToASCII"); exit(-1);
}
PyAPI_FUNC(PyObject*) PyUnicode_Translate(PyObject* a, PyObject* b, const char* c) {
    unimplemented("PyUnicode_Translate"); exit(-1);
}
PyAPI_FUNC(PyObject*) PyUnicode_TranslateCharmap(const Py_UNICODE* a, Py_ssize_t b, PyObject* c, const char* d) {
    unimplemented("PyUnicode_TranslateCharmap"); exit(-1);
}
PyAPI_FUNC(int) PyUnicode_WriteChar(PyObject* a, Py_ssize_t b, Py_UCS4 c) {
    unimplemented("PyUnicode_WriteChar"); exit(-1);
}
STATS_CONTAINER(PyVectorcall_Call, PyUnicode_Tailmatch)
PyObject* (*__target__PyVectorcall_Call)(PyObject*, PyObject*, PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyVectorcall_Call(PyObject* a, PyObject* b, PyObject* c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    STATS_BEFORE(PyVectorcall_Call)
    PyObject* result = (PyObject*) __target__PyVectorcall_Call(a, b, c);
    STATS_AFTER(PyVectorcall_Call)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyWeakref_GetObject, PyVectorcall_Call)
PyObject* (*__target__PyWeakref_GetObject)(PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyWeakref_GetObject(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(PyWeakref_GetObject)
    PyObject* result = (PyObject*) __target__PyWeakref_GetObject(a);
    STATS_AFTER(PyWeakref_GetObject)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(PyObject*) PyWeakref_NewProxy(PyObject* a, PyObject* b) {
    unimplemented("PyWeakref_NewProxy"); exit(-1);
}
STATS_CONTAINER(PyWeakref_NewRef, PyWeakref_GetObject)
PyObject* (*__target__PyWeakref_NewRef)(PyObject*, PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyWeakref_NewRef(PyObject* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(PyWeakref_NewRef)
    PyObject* result = (PyObject*) __target__PyWeakref_NewRef(a, b);
    STATS_AFTER(PyWeakref_NewRef)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(PyStatus) PyWideStringList_Append(PyWideStringList* a, const wchar_t* b) {
    unimplemented("PyWideStringList_Append"); exit(-1);
}
PyAPI_FUNC(PyStatus) PyWideStringList_Insert(PyWideStringList* a, Py_ssize_t b, const wchar_t* c) {
    unimplemented("PyWideStringList_Insert"); exit(-1);
}
PyAPI_FUNC(PyObject*) PyWrapper_New(PyObject* a, PyObject* b) {
    unimplemented("PyWrapper_New"); exit(-1);
}
PyAPI_FUNC(int) Py_AddPendingCall(int (*a)(void*), void* b) {
    unimplemented("Py_AddPendingCall"); exit(-1);
}
STATS_CONTAINER(Py_AtExit, PyWeakref_NewRef)
int (*__target__Py_AtExit)(void (*)(void)) = NULL;
PyAPI_FUNC(int) Py_AtExit(void (*a)(void)) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(Py_AtExit)
    int result = (int) __target__Py_AtExit(a);
    STATS_AFTER(Py_AtExit)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(int) Py_BytesMain(int a, char** b) {
    unimplemented("Py_BytesMain"); exit(-1);
}
PyAPI_FUNC(PyObject*) Py_CompileString(const char* a, const char* b, int c) {
    unimplemented("Py_CompileString"); exit(-1);
}
PyAPI_FUNC(PyObject*) Py_CompileStringExFlags(const char* a, const char* b, int c, PyCompilerFlags* d, int e) {
    unimplemented("Py_CompileStringExFlags"); exit(-1);
}
PyAPI_FUNC(PyObject*) Py_CompileStringObject(const char* a, PyObject* b, int c, PyCompilerFlags* d, int e) {
    unimplemented("Py_CompileStringObject"); exit(-1);
}
STATS_CONTAINER(Py_DecRef, Py_AtExit)
void (*__target__Py_DecRef)(PyObject*) = NULL;
PyAPI_FUNC(void) Py_DecRef(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(Py_DecRef)
    __target__Py_DecRef(a);
    STATS_AFTER(Py_DecRef)
    LOG_AFTER_VOID
}
PyAPI_FUNC(wchar_t*) Py_DecodeLocale(const char* a, size_t* b) {
    unimplemented("Py_DecodeLocale"); exit(-1);
}
PyAPI_FUNC(char*) Py_EncodeLocale(const wchar_t* a, size_t* b) {
    unimplemented("Py_EncodeLocale"); exit(-1);
}
PyAPI_FUNC(void) Py_EndInterpreter(PyThreadState* a) {
    unimplemented("Py_EndInterpreter"); exit(-1);
}
STATS_CONTAINER(Py_EnterRecursiveCall, Py_DecRef)
int (*__target__Py_EnterRecursiveCall)(const char*) = NULL;
PyAPI_FUNC(int) Py_EnterRecursiveCall(const char* a) {
    LOG("'%s'(0x%lx)", a?a:"<null>", (unsigned long) a);
    STATS_BEFORE(Py_EnterRecursiveCall)
    int result = (int) __target__Py_EnterRecursiveCall(a);
    STATS_AFTER(Py_EnterRecursiveCall)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(void) Py_Exit(int a) {
    unimplemented("Py_Exit"); exit(-1);
}
PyAPI_FUNC(void) Py_ExitStatusException(PyStatus a) {
    unimplemented("Py_ExitStatusException"); exit(-1);
}
PyAPI_FUNC(void) Py_FatalError(const char* a) {
    unimplemented("Py_FatalError"); exit(-1);
}
PyAPI_FUNC(int) Py_FdIsInteractive(FILE* a, const char* b) {
    unimplemented("Py_FdIsInteractive"); exit(-1);
}
PyAPI_FUNC(void) Py_Finalize() {
    unimplemented("Py_Finalize"); exit(-1);
}
PyAPI_FUNC(int) Py_FinalizeEx() {
    unimplemented("Py_FinalizeEx"); exit(-1);
}
PyAPI_FUNC(int) Py_FrozenMain(int a, char** b) {
    unimplemented("Py_FrozenMain"); exit(-1);
}
STATS_CONTAINER(Py_GenericAlias, Py_EnterRecursiveCall)
PyObject* (*__target__Py_GenericAlias)(PyObject*, PyObject*) = NULL;
PyAPI_FUNC(PyObject*) Py_GenericAlias(PyObject* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(Py_GenericAlias)
    PyObject* result = (PyObject*) __target__Py_GenericAlias(a, b);
    STATS_AFTER(Py_GenericAlias)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(void) Py_GetArgcArgv(int* a, wchar_t*** b) {
    unimplemented("Py_GetArgcArgv"); exit(-1);
}
STATS_CONTAINER(Py_GetBuildInfo, Py_GenericAlias)
const char* (*__target__Py_GetBuildInfo)() = NULL;
PyAPI_FUNC(const char*) Py_GetBuildInfo() {
    LOGS("");
    STATS_BEFORE(Py_GetBuildInfo)
    const char* result = (const char*) __target__Py_GetBuildInfo();
    STATS_AFTER(Py_GetBuildInfo)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(Py_GetCompiler, Py_GetBuildInfo)
const char* (*__target__Py_GetCompiler)() = NULL;
PyAPI_FUNC(const char*) Py_GetCompiler() {
    LOGS("");
    STATS_BEFORE(Py_GetCompiler)
    const char* result = (const char*) __target__Py_GetCompiler();
    STATS_AFTER(Py_GetCompiler)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(const char*) Py_GetCopyright() {
    unimplemented("Py_GetCopyright"); exit(-1);
}
PyAPI_FUNC(wchar_t*) Py_GetExecPrefix() {
    unimplemented("Py_GetExecPrefix"); exit(-1);
}
PyAPI_FUNC(wchar_t*) Py_GetPath() {
    unimplemented("Py_GetPath"); exit(-1);
}
PyAPI_FUNC(const char*) Py_GetPlatform() {
    unimplemented("Py_GetPlatform"); exit(-1);
}
PyAPI_FUNC(wchar_t*) Py_GetPrefix() {
    unimplemented("Py_GetPrefix"); exit(-1);
}
PyAPI_FUNC(wchar_t*) Py_GetProgramFullPath() {
    unimplemented("Py_GetProgramFullPath"); exit(-1);
}
PyAPI_FUNC(wchar_t*) Py_GetProgramName() {
    unimplemented("Py_GetProgramName"); exit(-1);
}
PyAPI_FUNC(wchar_t*) Py_GetPythonHome() {
    unimplemented("Py_GetPythonHome"); exit(-1);
}
PyAPI_FUNC(int) Py_GetRecursionLimit() {
    unimplemented("Py_GetRecursionLimit"); exit(-1);
}
STATS_CONTAINER(Py_GetVersion, Py_GetCompiler)
const char* (*__target__Py_GetVersion)() = NULL;
PyAPI_FUNC(const char*) Py_GetVersion() {
    LOGS("");
    STATS_BEFORE(Py_GetVersion)
    const char* result = (const char*) __target__Py_GetVersion();
    STATS_AFTER(Py_GetVersion)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(Py_IncRef, Py_GetVersion)
void (*__target__Py_IncRef)(PyObject*) = NULL;
PyAPI_FUNC(void) Py_IncRef(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(Py_IncRef)
    __target__Py_IncRef(a);
    STATS_AFTER(Py_IncRef)
    LOG_AFTER_VOID
}
PyAPI_FUNC(void) Py_Initialize() {
    unimplemented("Py_Initialize"); exit(-1);
}
PyAPI_FUNC(void) Py_InitializeEx(int a) {
    unimplemented("Py_InitializeEx"); exit(-1);
}
PyAPI_FUNC(PyStatus) Py_InitializeFromConfig(const PyConfig* a) {
    unimplemented("Py_InitializeFromConfig"); exit(-1);
}
STATS_CONTAINER(Py_Is, Py_IncRef)
int (*__target__Py_Is)(PyObject*, PyObject*) = NULL;
PyAPI_FUNC(int) Py_Is(PyObject* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(Py_Is)
    int result = (int) __target__Py_Is(a, b);
    STATS_AFTER(Py_Is)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(Py_IsFalse, Py_Is)
int (*__target__Py_IsFalse)(PyObject*) = NULL;
PyAPI_FUNC(int) Py_IsFalse(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(Py_IsFalse)
    int result = (int) __target__Py_IsFalse(a);
    STATS_AFTER(Py_IsFalse)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(int) Py_IsInitialized() {
    unimplemented("Py_IsInitialized"); exit(-1);
}
STATS_CONTAINER(Py_IsNone, Py_IsFalse)
int (*__target__Py_IsNone)(PyObject*) = NULL;
PyAPI_FUNC(int) Py_IsNone(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(Py_IsNone)
    int result = (int) __target__Py_IsNone(a);
    STATS_AFTER(Py_IsNone)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(Py_IsTrue, Py_IsNone)
int (*__target__Py_IsTrue)(PyObject*) = NULL;
PyAPI_FUNC(int) Py_IsTrue(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(Py_IsTrue)
    int result = (int) __target__Py_IsTrue(a);
    STATS_AFTER(Py_IsTrue)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(Py_LeaveRecursiveCall, Py_IsTrue)
void (*__target__Py_LeaveRecursiveCall)() = NULL;
PyAPI_FUNC(void) Py_LeaveRecursiveCall() {
    LOGS("");
    STATS_BEFORE(Py_LeaveRecursiveCall)
    __target__Py_LeaveRecursiveCall();
    STATS_AFTER(Py_LeaveRecursiveCall)
    LOG_AFTER_VOID
}
PyAPI_FUNC(int) Py_Main(int a, wchar_t** b) {
    unimplemented("Py_Main"); exit(-1);
}
PyAPI_FUNC(int) Py_MakePendingCalls() {
    unimplemented("Py_MakePendingCalls"); exit(-1);
}
PyAPI_FUNC(PyThreadState*) Py_NewInterpreter() {
    unimplemented("Py_NewInterpreter"); exit(-1);
}
STATS_CONTAINER(Py_NewRef, Py_LeaveRecursiveCall)
PyObject* (*__target__Py_NewRef)(PyObject*) = NULL;
PyAPI_FUNC(PyObject*) Py_NewRef(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(Py_NewRef)
    PyObject* result = (PyObject*) __target__Py_NewRef(a);
    STATS_AFTER(Py_NewRef)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(PyStatus) Py_PreInitialize(const PyPreConfig* a) {
    unimplemented("Py_PreInitialize"); exit(-1);
}
PyAPI_FUNC(PyStatus) Py_PreInitializeFromArgs(const PyPreConfig* a, Py_ssize_t b, wchar_t** c) {
    unimplemented("Py_PreInitializeFromArgs"); exit(-1);
}
PyAPI_FUNC(PyStatus) Py_PreInitializeFromBytesArgs(const PyPreConfig* a, Py_ssize_t b, char** c) {
    unimplemented("Py_PreInitializeFromBytesArgs"); exit(-1);
}
PyAPI_FUNC(int) Py_ReprEnter(PyObject* a) {
    unimplemented("Py_ReprEnter"); exit(-1);
}
PyAPI_FUNC(void) Py_ReprLeave(PyObject* a) {
    unimplemented("Py_ReprLeave"); exit(-1);
}
PyAPI_FUNC(int) Py_RunMain() {
    unimplemented("Py_RunMain"); exit(-1);
}
PyAPI_FUNC(void) Py_SetPath(const wchar_t* a) {
    unimplemented("Py_SetPath"); exit(-1);
}
PyAPI_FUNC(void) Py_SetProgramName(const wchar_t* a) {
    unimplemented("Py_SetProgramName"); exit(-1);
}
PyAPI_FUNC(void) Py_SetPythonHome(const wchar_t* a) {
    unimplemented("Py_SetPythonHome"); exit(-1);
}
PyAPI_FUNC(void) Py_SetRecursionLimit(int a) {
    unimplemented("Py_SetRecursionLimit"); exit(-1);
}
PyAPI_FUNC(int) Py_SetStandardStreamEncoding(const char* a, const char* b) {
    unimplemented("Py_SetStandardStreamEncoding"); exit(-1);
}
PyAPI_FUNC(Py_UNICODE*) Py_UNICODE_strcat(Py_UNICODE* a, const Py_UNICODE* b) {
    unimplemented("Py_UNICODE_strcat"); exit(-1);
}
PyAPI_FUNC(Py_UNICODE*) Py_UNICODE_strchr(const Py_UNICODE* a, Py_UNICODE b) {
    unimplemented("Py_UNICODE_strchr"); exit(-1);
}
PyAPI_FUNC(int) Py_UNICODE_strcmp(const Py_UNICODE* a, const Py_UNICODE* b) {
    unimplemented("Py_UNICODE_strcmp"); exit(-1);
}
PyAPI_FUNC(Py_UNICODE*) Py_UNICODE_strcpy(Py_UNICODE* a, const Py_UNICODE* b) {
    unimplemented("Py_UNICODE_strcpy"); exit(-1);
}
PyAPI_FUNC(size_t) Py_UNICODE_strlen(const Py_UNICODE* a) {
    unimplemented("Py_UNICODE_strlen"); exit(-1);
}
PyAPI_FUNC(int) Py_UNICODE_strncmp(const Py_UNICODE* a, const Py_UNICODE* b, size_t c) {
    unimplemented("Py_UNICODE_strncmp"); exit(-1);
}
PyAPI_FUNC(Py_UNICODE*) Py_UNICODE_strncpy(Py_UNICODE* a, const Py_UNICODE* b, size_t c) {
    unimplemented("Py_UNICODE_strncpy"); exit(-1);
}
PyAPI_FUNC(Py_UNICODE*) Py_UNICODE_strrchr(const Py_UNICODE* a, Py_UNICODE b) {
    unimplemented("Py_UNICODE_strrchr"); exit(-1);
}
PyAPI_FUNC(char*) Py_UniversalNewlineFgets(char* a, int b, FILE* c, PyObject* d) {
    unimplemented("Py_UniversalNewlineFgets"); exit(-1);
}
STATS_CONTAINER(Py_XNewRef, Py_NewRef)
PyObject* (*__target__Py_XNewRef)(PyObject*) = NULL;
PyAPI_FUNC(PyObject*) Py_XNewRef(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(Py_XNewRef)
    PyObject* result = (PyObject*) __target__Py_XNewRef(a);
    STATS_AFTER(Py_XNewRef)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyASCIIObject_LENGTH, Py_XNewRef)
Py_ssize_t (*__target___PyASCIIObject_LENGTH)(PyASCIIObject*) = NULL;
PyAPI_FUNC(Py_ssize_t) _PyASCIIObject_LENGTH(PyASCIIObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(_PyASCIIObject_LENGTH)
    Py_ssize_t result = (Py_ssize_t) __target___PyASCIIObject_LENGTH(a);
    STATS_AFTER(_PyASCIIObject_LENGTH)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyASCIIObject_STATE_ASCII, _PyASCIIObject_LENGTH)
unsigned int (*__target___PyASCIIObject_STATE_ASCII)(PyASCIIObject*) = NULL;
PyAPI_FUNC(unsigned int) _PyASCIIObject_STATE_ASCII(PyASCIIObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(_PyASCIIObject_STATE_ASCII)
    unsigned int result = (unsigned int) __target___PyASCIIObject_STATE_ASCII(a);
    STATS_AFTER(_PyASCIIObject_STATE_ASCII)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyASCIIObject_STATE_COMPACT, _PyASCIIObject_STATE_ASCII)
unsigned int (*__target___PyASCIIObject_STATE_COMPACT)(PyASCIIObject*) = NULL;
PyAPI_FUNC(unsigned int) _PyASCIIObject_STATE_COMPACT(PyASCIIObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(_PyASCIIObject_STATE_COMPACT)
    unsigned int result = (unsigned int) __target___PyASCIIObject_STATE_COMPACT(a);
    STATS_AFTER(_PyASCIIObject_STATE_COMPACT)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyASCIIObject_STATE_KIND, _PyASCIIObject_STATE_COMPACT)
unsigned int (*__target___PyASCIIObject_STATE_KIND)(PyASCIIObject*) = NULL;
PyAPI_FUNC(unsigned int) _PyASCIIObject_STATE_KIND(PyASCIIObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(_PyASCIIObject_STATE_KIND)
    unsigned int result = (unsigned int) __target___PyASCIIObject_STATE_KIND(a);
    STATS_AFTER(_PyASCIIObject_STATE_KIND)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyASCIIObject_STATE_READY, _PyASCIIObject_STATE_KIND)
unsigned int (*__target___PyASCIIObject_STATE_READY)(PyASCIIObject*) = NULL;
PyAPI_FUNC(unsigned int) _PyASCIIObject_STATE_READY(PyASCIIObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(_PyASCIIObject_STATE_READY)
    unsigned int result = (unsigned int) __target___PyASCIIObject_STATE_READY(a);
    STATS_AFTER(_PyASCIIObject_STATE_READY)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyASCIIObject_WSTR, _PyASCIIObject_STATE_READY)
wchar_t* (*__target___PyASCIIObject_WSTR)(PyASCIIObject*) = NULL;
PyAPI_FUNC(wchar_t*) _PyASCIIObject_WSTR(PyASCIIObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(_PyASCIIObject_WSTR)
    wchar_t* result = (wchar_t*) __target___PyASCIIObject_WSTR(a);
    STATS_AFTER(_PyASCIIObject_WSTR)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(void) _PyArg_Fini() {
    unimplemented("_PyArg_Fini"); exit(-1);
}
PyAPI_FUNC(int) _PyArg_NoKwnames(const char* a, PyObject* b) {
    unimplemented("_PyArg_NoKwnames"); exit(-1);
}
PyAPI_FUNC(int) _PyArg_ParseStack(PyObject*const* a, Py_ssize_t b, const char* c, ...) {
    unimplemented("_PyArg_ParseStack"); exit(-1);
}
PyAPI_FUNC(int) _PyArg_ParseStackAndKeywords(PyObject*const* a, Py_ssize_t b, PyObject* c, struct _PyArg_Parser* d, ...) {
    unimplemented("_PyArg_ParseStackAndKeywords"); exit(-1);
}
PyAPI_FUNC(int) _PyArg_ParseStackAndKeywords_SizeT(PyObject*const* a, Py_ssize_t b, PyObject* c, struct _PyArg_Parser* d, ...) {
    unimplemented("_PyArg_ParseStackAndKeywords_SizeT"); exit(-1);
}
STATS_CONTAINER(_PyArg_VaParseTupleAndKeywordsFast, _PyASCIIObject_WSTR)
int (*__target___PyArg_VaParseTupleAndKeywordsFast)(PyObject*, PyObject*, struct _PyArg_Parser*, va_list) = NULL;
PyAPI_FUNC(int) _PyArg_VaParseTupleAndKeywordsFast(PyObject* a, PyObject* b, struct _PyArg_Parser* c, va_list d) {
    LOG("0x%lx 0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c, (unsigned long) d);
    STATS_BEFORE(_PyArg_VaParseTupleAndKeywordsFast)
    int result = (int) __target___PyArg_VaParseTupleAndKeywordsFast(a, b, c, d);
    STATS_AFTER(_PyArg_VaParseTupleAndKeywordsFast)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyArg_VaParseTupleAndKeywordsFast_SizeT, _PyArg_VaParseTupleAndKeywordsFast)
int (*__target___PyArg_VaParseTupleAndKeywordsFast_SizeT)(PyObject*, PyObject*, struct _PyArg_Parser*, va_list) = NULL;
PyAPI_FUNC(int) _PyArg_VaParseTupleAndKeywordsFast_SizeT(PyObject* a, PyObject* b, struct _PyArg_Parser* c, va_list d) {
    LOG("0x%lx 0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c, (unsigned long) d);
    STATS_BEFORE(_PyArg_VaParseTupleAndKeywordsFast_SizeT)
    int result = (int) __target___PyArg_VaParseTupleAndKeywordsFast_SizeT(a, b, c, d);
    STATS_AFTER(_PyArg_VaParseTupleAndKeywordsFast_SizeT)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyArg_VaParseTupleAndKeywords_SizeT, _PyArg_VaParseTupleAndKeywordsFast_SizeT)
int (*__target___PyArg_VaParseTupleAndKeywords_SizeT)(PyObject*, PyObject*, const char*, char**, va_list) = NULL;
PyAPI_FUNC(int) _PyArg_VaParseTupleAndKeywords_SizeT(PyObject* a, PyObject* b, const char* c, char** d, va_list e) {
    LOG("0x%lx 0x%lx '%s'(0x%lx) 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, c?c:"<null>", (unsigned long) c, (unsigned long) d, (unsigned long) e);
    STATS_BEFORE(_PyArg_VaParseTupleAndKeywords_SizeT)
    int result = (int) __target___PyArg_VaParseTupleAndKeywords_SizeT(a, b, c, d, e);
    STATS_AFTER(_PyArg_VaParseTupleAndKeywords_SizeT)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyArg_VaParse_SizeT, _PyArg_VaParseTupleAndKeywords_SizeT)
int (*__target___PyArg_VaParse_SizeT)(PyObject*, const char*, va_list) = NULL;
PyAPI_FUNC(int) _PyArg_VaParse_SizeT(PyObject* a, const char* b, va_list c) {
    LOG("0x%lx '%s'(0x%lx) 0x%lx", (unsigned long) a, b?b:"<null>", (unsigned long) b, (unsigned long) c);
    STATS_BEFORE(_PyArg_VaParse_SizeT)
    int result = (int) __target___PyArg_VaParse_SizeT(a, b, c);
    STATS_AFTER(_PyArg_VaParse_SizeT)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(PyObject*) _PyAsyncGenValueWrapperNew(PyObject* a) {
    unimplemented("_PyAsyncGenValueWrapperNew"); exit(-1);
}
STATS_CONTAINER(_PyByteArray_Start, _PyArg_VaParse_SizeT)
char* (*__target___PyByteArray_Start)(PyObject*) = NULL;
PyAPI_FUNC(char*) _PyByteArray_Start(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(_PyByteArray_Start)
    char* result = (char*) __target___PyByteArray_Start(a);
    STATS_AFTER(_PyByteArray_Start)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyBytesWriter_Alloc, _PyByteArray_Start)
void* (*__target___PyBytesWriter_Alloc)(_PyBytesWriter*, Py_ssize_t) = NULL;
PyAPI_FUNC(void*) _PyBytesWriter_Alloc(_PyBytesWriter* a, Py_ssize_t b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(_PyBytesWriter_Alloc)
    void* result = (void*) __target___PyBytesWriter_Alloc(a, b);
    STATS_AFTER(_PyBytesWriter_Alloc)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyBytesWriter_Dealloc, _PyBytesWriter_Alloc)
void (*__target___PyBytesWriter_Dealloc)(_PyBytesWriter*) = NULL;
PyAPI_FUNC(void) _PyBytesWriter_Dealloc(_PyBytesWriter* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(_PyBytesWriter_Dealloc)
    __target___PyBytesWriter_Dealloc(a);
    STATS_AFTER(_PyBytesWriter_Dealloc)
    LOG_AFTER_VOID
}
STATS_CONTAINER(_PyBytesWriter_Finish, _PyBytesWriter_Dealloc)
PyObject* (*__target___PyBytesWriter_Finish)(_PyBytesWriter*, void*) = NULL;
PyAPI_FUNC(PyObject*) _PyBytesWriter_Finish(_PyBytesWriter* a, void* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(_PyBytesWriter_Finish)
    PyObject* result = (PyObject*) __target___PyBytesWriter_Finish(a, b);
    STATS_AFTER(_PyBytesWriter_Finish)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyBytesWriter_Init, _PyBytesWriter_Finish)
void (*__target___PyBytesWriter_Init)(_PyBytesWriter*) = NULL;
PyAPI_FUNC(void) _PyBytesWriter_Init(_PyBytesWriter* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(_PyBytesWriter_Init)
    __target___PyBytesWriter_Init(a);
    STATS_AFTER(_PyBytesWriter_Init)
    LOG_AFTER_VOID
}
STATS_CONTAINER(_PyBytesWriter_Prepare, _PyBytesWriter_Init)
void* (*__target___PyBytesWriter_Prepare)(_PyBytesWriter*, void*, Py_ssize_t) = NULL;
PyAPI_FUNC(void*) _PyBytesWriter_Prepare(_PyBytesWriter* a, void* b, Py_ssize_t c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    STATS_BEFORE(_PyBytesWriter_Prepare)
    void* result = (void*) __target___PyBytesWriter_Prepare(a, b, c);
    STATS_AFTER(_PyBytesWriter_Prepare)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyBytesWriter_Resize, _PyBytesWriter_Prepare)
void* (*__target___PyBytesWriter_Resize)(_PyBytesWriter*, void*, Py_ssize_t) = NULL;
PyAPI_FUNC(void*) _PyBytesWriter_Resize(_PyBytesWriter* a, void* b, Py_ssize_t c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    STATS_BEFORE(_PyBytesWriter_Resize)
    void* result = (void*) __target___PyBytesWriter_Resize(a, b, c);
    STATS_AFTER(_PyBytesWriter_Resize)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyBytesWriter_WriteBytes, _PyBytesWriter_Resize)
void* (*__target___PyBytesWriter_WriteBytes)(_PyBytesWriter*, void*, const void*, Py_ssize_t) = NULL;
PyAPI_FUNC(void*) _PyBytesWriter_WriteBytes(_PyBytesWriter* a, void* b, const void* c, Py_ssize_t d) {
    LOG("0x%lx 0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c, (unsigned long) d);
    STATS_BEFORE(_PyBytesWriter_WriteBytes)
    void* result = (void*) __target___PyBytesWriter_WriteBytes(a, b, c, d);
    STATS_AFTER(_PyBytesWriter_WriteBytes)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(PyObject*) _PyBytes_DecodeEscape(const char* a, Py_ssize_t b, const char* c, const char** d) {
    unimplemented("_PyBytes_DecodeEscape"); exit(-1);
}
PyAPI_FUNC(PyObject*) _PyBytes_FormatEx(const char* a, Py_ssize_t b, PyObject* c, int d) {
    unimplemented("_PyBytes_FormatEx"); exit(-1);
}
PyAPI_FUNC(PyObject*) _PyBytes_FromHex(PyObject* a, int b) {
    unimplemented("_PyBytes_FromHex"); exit(-1);
}
STATS_CONTAINER(_PyBytes_Join, _PyBytesWriter_WriteBytes)
PyObject* (*__target___PyBytes_Join)(PyObject*, PyObject*) = NULL;
PyAPI_FUNC(PyObject*) _PyBytes_Join(PyObject* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(_PyBytes_Join)
    PyObject* result = (PyObject*) __target___PyBytes_Join(a, b);
    STATS_AFTER(_PyBytes_Join)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyBytes_Resize, _PyBytes_Join)
int (*__target___PyBytes_Resize)(PyObject**, Py_ssize_t) = NULL;
PyAPI_FUNC(int) _PyBytes_Resize(PyObject** a, Py_ssize_t b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(_PyBytes_Resize)
    int result = (int) __target___PyBytes_Resize(a, b);
    STATS_AFTER(_PyBytes_Resize)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(void) _PyCFunction_DebugMallocStats(FILE* a) {
    unimplemented("_PyCFunction_DebugMallocStats"); exit(-1);
}
PyAPI_FUNC(PyObject*) _PyCFunction_FastCallDict(PyObject* a, PyObject*const* b, Py_ssize_t c, PyObject* d) {
    unimplemented("_PyCFunction_FastCallDict"); exit(-1);
}
PyAPI_FUNC(int) _PyCode_CheckLineNumber(int a, PyCodeAddressRange* b) {
    unimplemented("_PyCode_CheckLineNumber"); exit(-1);
}
PyAPI_FUNC(PyObject*) _PyCode_ConstantKey(PyObject* a) {
    unimplemented("_PyCode_ConstantKey"); exit(-1);
}
PyAPI_FUNC(int) _PyCode_GetExtra(PyObject* a, Py_ssize_t b, void** c) {
    unimplemented("_PyCode_GetExtra"); exit(-1);
}
PyAPI_FUNC(int) _PyCode_InitAddressRange(PyCodeObject* a, PyCodeAddressRange* b) {
    unimplemented("_PyCode_InitAddressRange"); exit(-1);
}
PyAPI_FUNC(int) _PyCode_SetExtra(PyObject* a, Py_ssize_t b, void* c) {
    unimplemented("_PyCode_SetExtra"); exit(-1);
}
PyAPI_FUNC(PyObject*) _PyCodecInfo_GetIncrementalDecoder(PyObject* a, const char* b) {
    unimplemented("_PyCodecInfo_GetIncrementalDecoder"); exit(-1);
}
PyAPI_FUNC(PyObject*) _PyCodecInfo_GetIncrementalEncoder(PyObject* a, const char* b) {
    unimplemented("_PyCodecInfo_GetIncrementalEncoder"); exit(-1);
}
PyAPI_FUNC(PyObject*) _PyCodec_DecodeText(PyObject* a, const char* b, const char* c) {
    unimplemented("_PyCodec_DecodeText"); exit(-1);
}
PyAPI_FUNC(PyObject*) _PyCodec_EncodeText(PyObject* a, const char* b, const char* c) {
    unimplemented("_PyCodec_EncodeText"); exit(-1);
}
PyAPI_FUNC(int) _PyCodec_Forget(const char* a) {
    unimplemented("_PyCodec_Forget"); exit(-1);
}
PyAPI_FUNC(PyObject*) _PyCodec_Lookup(const char* a) {
    unimplemented("_PyCodec_Lookup"); exit(-1);
}
PyAPI_FUNC(PyObject*) _PyCodec_LookupTextEncoding(const char* a, const char* b) {
    unimplemented("_PyCodec_LookupTextEncoding"); exit(-1);
}
PyAPI_FUNC(int) _PyComplex_FormatAdvancedWriter(_PyUnicodeWriter* a, PyObject* b, PyObject* c, Py_ssize_t d, Py_ssize_t e) {
    unimplemented("_PyComplex_FormatAdvancedWriter"); exit(-1);
}
PyAPI_FUNC(PyObject*) _PyContext_NewHamtForTests() {
    unimplemented("_PyContext_NewHamtForTests"); exit(-1);
}
PyAPI_FUNC(PyObject*) _PyCoro_GetAwaitableIter(PyObject* a) {
    unimplemented("_PyCoro_GetAwaitableIter"); exit(-1);
}
PyAPI_FUNC(crossinterpdatafunc) _PyCrossInterpreterData_Lookup(PyObject* a) {
    unimplemented("_PyCrossInterpreterData_Lookup"); exit(-1);
}
PyAPI_FUNC(PyObject*) _PyCrossInterpreterData_NewObject(_PyCrossInterpreterData* a) {
    unimplemented("_PyCrossInterpreterData_NewObject"); exit(-1);
}
PyAPI_FUNC(int) _PyCrossInterpreterData_RegisterClass(PyTypeObject* a, crossinterpdatafunc b) {
    unimplemented("_PyCrossInterpreterData_RegisterClass"); exit(-1);
}
PyAPI_FUNC(void) _PyCrossInterpreterData_Release(_PyCrossInterpreterData* a) {
    unimplemented("_PyCrossInterpreterData_Release"); exit(-1);
}
PyAPI_FUNC(void) _PyDebugAllocatorStats(FILE* a, const char* b, int c, size_t d) {
    unimplemented("_PyDebugAllocatorStats"); exit(-1);
}
PyAPI_FUNC(PyObject*) _PyDictView_Intersect(PyObject* a, PyObject* b) {
    unimplemented("_PyDictView_Intersect"); exit(-1);
}
PyAPI_FUNC(PyObject*) _PyDictView_New(PyObject* a, PyTypeObject* b) {
    unimplemented("_PyDictView_New"); exit(-1);
}
PyAPI_FUNC(int) _PyDict_Contains_KnownHash(PyObject* a, PyObject* b, Py_hash_t c) {
    unimplemented("_PyDict_Contains_KnownHash"); exit(-1);
}
PyAPI_FUNC(void) _PyDict_DebugMallocStats(FILE* a) {
    unimplemented("_PyDict_DebugMallocStats"); exit(-1);
}
PyAPI_FUNC(int) _PyDict_DelItemId(PyObject* a, struct _Py_Identifier* b) {
    unimplemented("_PyDict_DelItemId"); exit(-1);
}
PyAPI_FUNC(int) _PyDict_DelItemIf(PyObject* a, PyObject* b, int (*c)(PyObject*value)) {
    unimplemented("_PyDict_DelItemIf"); exit(-1);
}
PyAPI_FUNC(int) _PyDict_DelItem_KnownHash(PyObject* a, PyObject* b, Py_hash_t c) {
    unimplemented("_PyDict_DelItem_KnownHash"); exit(-1);
}
PyAPI_FUNC(PyObject*) _PyDict_FromKeys(PyObject* a, PyObject* b, PyObject* c) {
    unimplemented("_PyDict_FromKeys"); exit(-1);
}
PyAPI_FUNC(Py_ssize_t) _PyDict_GetItemHint(PyDictObject* a, PyObject* b, Py_ssize_t c, PyObject** d) {
    unimplemented("_PyDict_GetItemHint"); exit(-1);
}
PyAPI_FUNC(int) _PyDict_HasOnlyStringKeys(PyObject* a) {
    unimplemented("_PyDict_HasOnlyStringKeys"); exit(-1);
}
PyAPI_FUNC(Py_ssize_t) _PyDict_KeysSize(PyDictKeysObject* a) {
    unimplemented("_PyDict_KeysSize"); exit(-1);
}
PyAPI_FUNC(PyObject*) _PyDict_LoadGlobal(PyDictObject* a, PyDictObject* b, PyObject* c) {
    unimplemented("_PyDict_LoadGlobal"); exit(-1);
}
PyAPI_FUNC(void) _PyDict_MaybeUntrack(PyObject* a) {
    unimplemented("_PyDict_MaybeUntrack"); exit(-1);
}
PyAPI_FUNC(int) _PyDict_MergeEx(PyObject* a, PyObject* b, int c) {
    unimplemented("_PyDict_MergeEx"); exit(-1);
}
PyAPI_FUNC(PyDictKeysObject*) _PyDict_NewKeysForClass() {
    unimplemented("_PyDict_NewKeysForClass"); exit(-1);
}
STATS_CONTAINER(_PyDict_Pop, _PyBytes_Resize)
PyObject* (*__target___PyDict_Pop)(PyObject*, PyObject*, PyObject*) = NULL;
PyAPI_FUNC(PyObject*) _PyDict_Pop(PyObject* a, PyObject* b, PyObject* c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    STATS_BEFORE(_PyDict_Pop)
    PyObject* result = (PyObject*) __target___PyDict_Pop(a, b, c);
    STATS_AFTER(_PyDict_Pop)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(PyObject*) _PyDict_Pop_KnownHash(PyObject* a, PyObject* b, Py_hash_t c, PyObject* d) {
    unimplemented("_PyDict_Pop_KnownHash"); exit(-1);
}
STATS_CONTAINER(_PyDict_SetItem_KnownHash, _PyDict_Pop)
int (*__target___PyDict_SetItem_KnownHash)(PyObject*, PyObject*, PyObject*, Py_hash_t) = NULL;
PyAPI_FUNC(int) _PyDict_SetItem_KnownHash(PyObject* a, PyObject* b, PyObject* c, Py_hash_t d) {
    LOG("0x%lx 0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c, (unsigned long) d);
    STATS_BEFORE(_PyDict_SetItem_KnownHash)
    int result = (int) __target___PyDict_SetItem_KnownHash(a, b, c, d);
    STATS_AFTER(_PyDict_SetItem_KnownHash)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(Py_ssize_t) _PyDict_SizeOf(PyDictObject* a) {
    unimplemented("_PyDict_SizeOf"); exit(-1);
}
STATS_CONTAINER(_PyErr_BadInternalCall, _PyDict_SetItem_KnownHash)
void (*__target___PyErr_BadInternalCall)(const char*, int) = NULL;
PyAPI_FUNC(void) _PyErr_BadInternalCall(const char* a, int b) {
    LOG("'%s'(0x%lx) 0x%lx", a?a:"<null>", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(_PyErr_BadInternalCall)
    __target___PyErr_BadInternalCall(a, b);
    STATS_AFTER(_PyErr_BadInternalCall)
    LOG_AFTER_VOID
}
PyAPI_FUNC(void) _PyErr_ChainExceptions(PyObject* a, PyObject* b, PyObject* c) {
    unimplemented("_PyErr_ChainExceptions"); exit(-1);
}
PyAPI_FUNC(int) _PyErr_CheckSignals() {
    unimplemented("_PyErr_CheckSignals"); exit(-1);
}
STATS_CONTAINER(_PyErr_CreateAndSetException, _PyErr_BadInternalCall)
void (*__target___PyErr_CreateAndSetException)(PyObject*, PyObject*) = NULL;
PyAPI_FUNC(void) _PyErr_CreateAndSetException(PyObject* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(_PyErr_CreateAndSetException)
    __target___PyErr_CreateAndSetException(a, b);
    STATS_AFTER(_PyErr_CreateAndSetException)
    LOG_AFTER_VOID
}
PyAPI_FUNC(PyObject*) _PyErr_FormatFromCause(PyObject* a, const char* b, ...) {
    unimplemented("_PyErr_FormatFromCause"); exit(-1);
}
PyAPI_FUNC(void) _PyErr_GetExcInfo(PyThreadState* a, PyObject** b, PyObject** c, PyObject** d) {
    unimplemented("_PyErr_GetExcInfo"); exit(-1);
}
PyAPI_FUNC(_PyErr_StackItem*) _PyErr_GetTopmostException(PyThreadState* a) {
    unimplemented("_PyErr_GetTopmostException"); exit(-1);
}
PyAPI_FUNC(PyObject*) _PyErr_ProgramDecodedTextObject(PyObject* a, int b, const char* c) {
    unimplemented("_PyErr_ProgramDecodedTextObject"); exit(-1);
}
PyAPI_FUNC(void) _PyErr_SetKeyError(PyObject* a) {
    unimplemented("_PyErr_SetKeyError"); exit(-1);
}
PyAPI_FUNC(PyObject*) _PyErr_TrySetFromCause(const char* a, ...) {
    unimplemented("_PyErr_TrySetFromCause"); exit(-1);
}
PyAPI_FUNC(void) _PyErr_WarnUnawaitedCoroutine(PyObject* a) {
    unimplemented("_PyErr_WarnUnawaitedCoroutine"); exit(-1);
}
STATS_CONTAINER(_PyErr_WriteUnraisableMsg, _PyErr_CreateAndSetException)
void (*__target___PyErr_WriteUnraisableMsg)(const char*, PyObject*) = NULL;
PyAPI_FUNC(void) _PyErr_WriteUnraisableMsg(const char* a, PyObject* b) {
    LOG("'%s'(0x%lx) 0x%lx", a?a:"<null>", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(_PyErr_WriteUnraisableMsg)
    __target___PyErr_WriteUnraisableMsg(a, b);
    STATS_AFTER(_PyErr_WriteUnraisableMsg)
    LOG_AFTER_VOID
}
PyAPI_FUNC(PyObject*) _PyEval_CallTracing(PyObject* a, PyObject* b) {
    unimplemented("_PyEval_CallTracing"); exit(-1);
}
PyAPI_FUNC(PyObject*) _PyEval_EvalFrameDefault(PyThreadState* a, PyFrameObject* b, int c) {
    unimplemented("_PyEval_EvalFrameDefault"); exit(-1);
}
PyAPI_FUNC(PyObject*) _PyEval_GetAsyncGenFinalizer() {
    unimplemented("_PyEval_GetAsyncGenFinalizer"); exit(-1);
}
PyAPI_FUNC(PyObject*) _PyEval_GetAsyncGenFirstiter() {
    unimplemented("_PyEval_GetAsyncGenFirstiter"); exit(-1);
}
PyAPI_FUNC(PyObject*) _PyEval_GetBuiltinId(_Py_Identifier* a) {
    unimplemented("_PyEval_GetBuiltinId"); exit(-1);
}
PyAPI_FUNC(int) _PyEval_GetCoroutineOriginTrackingDepth() {
    unimplemented("_PyEval_GetCoroutineOriginTrackingDepth"); exit(-1);
}
PyAPI_FUNC(unsigned long) _PyEval_GetSwitchInterval() {
    unimplemented("_PyEval_GetSwitchInterval"); exit(-1);
}
PyAPI_FUNC(Py_ssize_t) _PyEval_RequestCodeExtraIndex(freefunc a) {
    unimplemented("_PyEval_RequestCodeExtraIndex"); exit(-1);
}
PyAPI_FUNC(int) _PyEval_SetAsyncGenFinalizer(PyObject* a) {
    unimplemented("_PyEval_SetAsyncGenFinalizer"); exit(-1);
}
PyAPI_FUNC(int) _PyEval_SetAsyncGenFirstiter(PyObject* a) {
    unimplemented("_PyEval_SetAsyncGenFirstiter"); exit(-1);
}
PyAPI_FUNC(void) _PyEval_SetCoroutineOriginTrackingDepth(int a) {
    unimplemented("_PyEval_SetCoroutineOriginTrackingDepth"); exit(-1);
}
PyAPI_FUNC(int) _PyEval_SetProfile(PyThreadState* a, Py_tracefunc b, PyObject* c) {
    unimplemented("_PyEval_SetProfile"); exit(-1);
}
PyAPI_FUNC(void) _PyEval_SetSwitchInterval(unsigned long a) {
    unimplemented("_PyEval_SetSwitchInterval"); exit(-1);
}
PyAPI_FUNC(int) _PyEval_SetTrace(PyThreadState* a, Py_tracefunc b, PyObject* c) {
    unimplemented("_PyEval_SetTrace"); exit(-1);
}
STATS_CONTAINER(_PyEval_SliceIndex, _PyErr_WriteUnraisableMsg)
int (*__target___PyEval_SliceIndex)(PyObject*, Py_ssize_t*) = NULL;
PyAPI_FUNC(int) _PyEval_SliceIndex(PyObject* a, Py_ssize_t* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(_PyEval_SliceIndex)
    int result = (int) __target___PyEval_SliceIndex(a, b);
    STATS_AFTER(_PyEval_SliceIndex)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(int) _PyEval_SliceIndexNotNone(PyObject* a, Py_ssize_t* b) {
    unimplemented("_PyEval_SliceIndexNotNone"); exit(-1);
}
PyAPI_FUNC(void) _PyFloat_DebugMallocStats(FILE* a) {
    unimplemented("_PyFloat_DebugMallocStats"); exit(-1);
}
PyAPI_FUNC(int) _PyFloat_FormatAdvancedWriter(_PyUnicodeWriter* a, PyObject* b, PyObject* c, Py_ssize_t d, Py_ssize_t e) {
    unimplemented("_PyFloat_FormatAdvancedWriter"); exit(-1);
}
STATS_CONTAINER(_PyFloat_Pack2, _PyEval_SliceIndex)
int (*__target___PyFloat_Pack2)(double, unsigned char*, int) = NULL;
PyAPI_FUNC(int) _PyFloat_Pack2(double a, unsigned char* b, int c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    STATS_BEFORE(_PyFloat_Pack2)
    int result = (int) __target___PyFloat_Pack2(a, b, c);
    STATS_AFTER(_PyFloat_Pack2)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyFloat_Pack4, _PyFloat_Pack2)
int (*__target___PyFloat_Pack4)(double, unsigned char*, int) = NULL;
PyAPI_FUNC(int) _PyFloat_Pack4(double a, unsigned char* b, int c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    STATS_BEFORE(_PyFloat_Pack4)
    int result = (int) __target___PyFloat_Pack4(a, b, c);
    STATS_AFTER(_PyFloat_Pack4)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyFloat_Pack8, _PyFloat_Pack4)
int (*__target___PyFloat_Pack8)(double, unsigned char*, int) = NULL;
PyAPI_FUNC(int) _PyFloat_Pack8(double a, unsigned char* b, int c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    STATS_BEFORE(_PyFloat_Pack8)
    int result = (int) __target___PyFloat_Pack8(a, b, c);
    STATS_AFTER(_PyFloat_Pack8)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyFloat_Unpack2, _PyFloat_Pack8)
double (*__target___PyFloat_Unpack2)(const unsigned char*, int) = NULL;
PyAPI_FUNC(double) _PyFloat_Unpack2(const unsigned char* a, int b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(_PyFloat_Unpack2)
    double result = (double) __target___PyFloat_Unpack2(a, b);
    STATS_AFTER(_PyFloat_Unpack2)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyFloat_Unpack4, _PyFloat_Unpack2)
double (*__target___PyFloat_Unpack4)(const unsigned char*, int) = NULL;
PyAPI_FUNC(double) _PyFloat_Unpack4(const unsigned char* a, int b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(_PyFloat_Unpack4)
    double result = (double) __target___PyFloat_Unpack4(a, b);
    STATS_AFTER(_PyFloat_Unpack4)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyFloat_Unpack8, _PyFloat_Unpack4)
double (*__target___PyFloat_Unpack8)(const unsigned char*, int) = NULL;
PyAPI_FUNC(double) _PyFloat_Unpack8(const unsigned char* a, int b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(_PyFloat_Unpack8)
    double result = (double) __target___PyFloat_Unpack8(a, b);
    STATS_AFTER(_PyFloat_Unpack8)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(void) _PyFrame_DebugMallocStats(FILE* a) {
    unimplemented("_PyFrame_DebugMallocStats"); exit(-1);
}
PyAPI_FUNC(PyFrameObject*) _PyFrame_New_NoTrack(PyThreadState* a, PyFrameConstructor* b, PyObject* c) {
    unimplemented("_PyFrame_New_NoTrack"); exit(-1);
}
STATS_CONTAINER(_PyFrame_SetLineNumber, _PyFloat_Unpack8)
void (*__target___PyFrame_SetLineNumber)(PyFrameObject*, int) = NULL;
PyAPI_FUNC(void) _PyFrame_SetLineNumber(PyFrameObject* a, int b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(_PyFrame_SetLineNumber)
    __target___PyFrame_SetLineNumber(a, b);
    STATS_AFTER(_PyFrame_SetLineNumber)
    LOG_AFTER_VOID
}
PyAPI_FUNC(PyObject*) _PyFunction_FastCallDict(PyObject* a, PyObject*const* b, Py_ssize_t c, PyObject* d) {
    unimplemented("_PyFunction_FastCallDict"); exit(-1);
}
PyAPI_FUNC(PyObject*) _PyFunction_Vectorcall(PyObject* a, PyObject*const* b, size_t c, PyObject* d) {
    unimplemented("_PyFunction_Vectorcall"); exit(-1);
}
PyAPI_FUNC(PyInterpreterState*) _PyGILState_GetInterpreterStateUnsafe() {
    unimplemented("_PyGILState_GetInterpreterStateUnsafe"); exit(-1);
}
STATS_CONTAINER(_PyGen_FetchStopIterationValue, _PyFrame_SetLineNumber)
int (*__target___PyGen_FetchStopIterationValue)(PyObject**) = NULL;
PyAPI_FUNC(int) _PyGen_FetchStopIterationValue(PyObject** a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(_PyGen_FetchStopIterationValue)
    int result = (int) __target___PyGen_FetchStopIterationValue(a);
    STATS_AFTER(_PyGen_FetchStopIterationValue)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyGen_Finalize, _PyGen_FetchStopIterationValue)
void (*__target___PyGen_Finalize)(PyObject*) = NULL;
PyAPI_FUNC(void) _PyGen_Finalize(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(_PyGen_Finalize)
    __target___PyGen_Finalize(a);
    STATS_AFTER(_PyGen_Finalize)
    LOG_AFTER_VOID
}
STATS_CONTAINER(_PyGen_Send, _PyGen_Finalize)
PyObject* (*__target___PyGen_Send)(PyGenObject*, PyObject*) = NULL;
PyAPI_FUNC(PyObject*) _PyGen_Send(PyGenObject* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(_PyGen_Send)
    PyObject* result = (PyObject*) __target___PyGen_Send(a, b);
    STATS_AFTER(_PyGen_Send)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyGen_SetStopIterationValue, _PyGen_Send)
int (*__target___PyGen_SetStopIterationValue)(PyObject*) = NULL;
PyAPI_FUNC(int) _PyGen_SetStopIterationValue(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(_PyGen_SetStopIterationValue)
    int result = (int) __target___PyGen_SetStopIterationValue(a);
    STATS_AFTER(_PyGen_SetStopIterationValue)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyGen_yf, _PyGen_SetStopIterationValue)
PyObject* (*__target___PyGen_yf)(PyGenObject*) = NULL;
PyAPI_FUNC(PyObject*) _PyGen_yf(PyGenObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(_PyGen_yf)
    PyObject* result = (PyObject*) __target___PyGen_yf(a);
    STATS_AFTER(_PyGen_yf)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(void) _PyImport_AcquireLock() {
    unimplemented("_PyImport_AcquireLock"); exit(-1);
}
PyAPI_FUNC(PyObject*) _PyImport_FindBuiltin(const char* a, PyObject* b) {
    unimplemented("_PyImport_FindBuiltin"); exit(-1);
}
PyAPI_FUNC(PyObject*) _PyImport_FindExtensionObject(PyObject* a, PyObject* b) {
    unimplemented("_PyImport_FindExtensionObject"); exit(-1);
}
PyAPI_FUNC(PyObject*) _PyImport_FindExtensionObjectEx(PyObject* a, PyObject* b, PyObject* c) {
    unimplemented("_PyImport_FindExtensionObjectEx"); exit(-1);
}
PyAPI_FUNC(int) _PyImport_FixupBuiltin(PyObject* a, const char* b, PyObject* c) {
    unimplemented("_PyImport_FixupBuiltin"); exit(-1);
}
PyAPI_FUNC(int) _PyImport_FixupExtensionObject(PyObject* a, PyObject* b, PyObject* c, PyObject* d) {
    unimplemented("_PyImport_FixupExtensionObject"); exit(-1);
}
PyAPI_FUNC(PyObject*) _PyImport_GetModuleAttr(PyObject* a, PyObject* b) {
    unimplemented("_PyImport_GetModuleAttr"); exit(-1);
}
PyAPI_FUNC(PyObject*) _PyImport_GetModuleAttrString(const char* a, const char* b) {
    unimplemented("_PyImport_GetModuleAttrString"); exit(-1);
}
PyAPI_FUNC(PyObject*) _PyImport_GetModuleId(struct _Py_Identifier* a) {
    unimplemented("_PyImport_GetModuleId"); exit(-1);
}
PyAPI_FUNC(int) _PyImport_IsInitialized(PyInterpreterState* a) {
    unimplemented("_PyImport_IsInitialized"); exit(-1);
}
PyAPI_FUNC(void) _PyImport_ReInitLock() {
    unimplemented("_PyImport_ReInitLock"); exit(-1);
}
PyAPI_FUNC(int) _PyImport_ReleaseLock() {
    unimplemented("_PyImport_ReleaseLock"); exit(-1);
}
STATS_CONTAINER(_PyImport_SetModule, _PyGen_yf)
int (*__target___PyImport_SetModule)(PyObject*, PyObject*) = NULL;
PyAPI_FUNC(int) _PyImport_SetModule(PyObject* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(_PyImport_SetModule)
    int result = (int) __target___PyImport_SetModule(a, b);
    STATS_AFTER(_PyImport_SetModule)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(int) _PyImport_SetModuleString(const char* a, PyObject* b) {
    unimplemented("_PyImport_SetModuleString"); exit(-1);
}
PyAPI_FUNC(PyInterpreterState*) _PyInterpreterState_Get() {
    unimplemented("_PyInterpreterState_Get"); exit(-1);
}
PyAPI_FUNC(const PyConfig*) _PyInterpreterState_GetConfig(PyInterpreterState* a) {
    unimplemented("_PyInterpreterState_GetConfig"); exit(-1);
}
PyAPI_FUNC(int) _PyInterpreterState_GetConfigCopy(PyConfig* a) {
    unimplemented("_PyInterpreterState_GetConfigCopy"); exit(-1);
}
PyAPI_FUNC(_PyFrameEvalFunction) _PyInterpreterState_GetEvalFrameFunc(PyInterpreterState* a) {
    unimplemented("_PyInterpreterState_GetEvalFrameFunc"); exit(-1);
}
PyAPI_FUNC(PyObject*) _PyInterpreterState_GetMainModule(PyInterpreterState* a) {
    unimplemented("_PyInterpreterState_GetMainModule"); exit(-1);
}
PyAPI_FUNC(void) _PyInterpreterState_RequireIDRef(PyInterpreterState* a, int b) {
    unimplemented("_PyInterpreterState_RequireIDRef"); exit(-1);
}
PyAPI_FUNC(int) _PyInterpreterState_RequiresIDRef(PyInterpreterState* a) {
    unimplemented("_PyInterpreterState_RequiresIDRef"); exit(-1);
}
PyAPI_FUNC(int) _PyInterpreterState_SetConfig(const PyConfig* a) {
    unimplemented("_PyInterpreterState_SetConfig"); exit(-1);
}
PyAPI_FUNC(void) _PyInterpreterState_SetEvalFrameFunc(PyInterpreterState* a, _PyFrameEvalFunction b) {
    unimplemented("_PyInterpreterState_SetEvalFrameFunc"); exit(-1);
}
PyAPI_FUNC(void) _PyList_DebugMallocStats(FILE* a) {
    unimplemented("_PyList_DebugMallocStats"); exit(-1);
}
STATS_CONTAINER(_PyList_Extend, _PyImport_SetModule)
PyObject* (*__target___PyList_Extend)(PyListObject*, PyObject*) = NULL;
PyAPI_FUNC(PyObject*) _PyList_Extend(PyListObject* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(_PyList_Extend)
    PyObject* result = (PyObject*) __target___PyList_Extend(a, b);
    STATS_AFTER(_PyList_Extend)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyLong_AsByteArray, _PyList_Extend)
int (*__target___PyLong_AsByteArray)(PyLongObject*, unsigned char*, size_t, int, int) = NULL;
PyAPI_FUNC(int) _PyLong_AsByteArray(PyLongObject* a, unsigned char* b, size_t c, int d, int e) {
    LOG("0x%lx 0x%lx 0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c, (unsigned long) d, (unsigned long) e);
    STATS_BEFORE(_PyLong_AsByteArray)
    int result = (int) __target___PyLong_AsByteArray(a, b, c, d, e);
    STATS_AFTER(_PyLong_AsByteArray)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyLong_AsInt, _PyLong_AsByteArray)
int (*__target___PyLong_AsInt)(PyObject*) = NULL;
PyAPI_FUNC(int) _PyLong_AsInt(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(_PyLong_AsInt)
    int result = (int) __target___PyLong_AsInt(a);
    STATS_AFTER(_PyLong_AsInt)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(time_t) _PyLong_AsTime_t(PyObject* a) {
    unimplemented("_PyLong_AsTime_t"); exit(-1);
}
PyAPI_FUNC(PyObject*) _PyLong_Copy(PyLongObject* a) {
    unimplemented("_PyLong_Copy"); exit(-1);
}
PyAPI_FUNC(PyObject*) _PyLong_DivmodNear(PyObject* a, PyObject* b) {
    unimplemented("_PyLong_DivmodNear"); exit(-1);
}
PyAPI_FUNC(int) _PyLong_FileDescriptor_Converter(PyObject* a, void* b) {
    unimplemented("_PyLong_FileDescriptor_Converter"); exit(-1);
}
PyAPI_FUNC(PyObject*) _PyLong_Format(PyObject* a, int b) {
    unimplemented("_PyLong_Format"); exit(-1);
}
PyAPI_FUNC(int) _PyLong_FormatAdvancedWriter(_PyUnicodeWriter* a, PyObject* b, PyObject* c, Py_ssize_t d, Py_ssize_t e) {
    unimplemented("_PyLong_FormatAdvancedWriter"); exit(-1);
}
PyAPI_FUNC(char*) _PyLong_FormatBytesWriter(_PyBytesWriter* a, char* b, PyObject* c, int d, int e) {
    unimplemented("_PyLong_FormatBytesWriter"); exit(-1);
}
PyAPI_FUNC(int) _PyLong_FormatWriter(_PyUnicodeWriter* a, PyObject* b, int c, int d) {
    unimplemented("_PyLong_FormatWriter"); exit(-1);
}
PyAPI_FUNC(double) _PyLong_Frexp(PyLongObject* a, Py_ssize_t* b) {
    unimplemented("_PyLong_Frexp"); exit(-1);
}
PyAPI_FUNC(PyObject*) _PyLong_FromByteArray(const unsigned char* a, size_t b, int c, int d) {
    unimplemented("_PyLong_FromByteArray"); exit(-1);
}
PyAPI_FUNC(PyObject*) _PyLong_FromBytes(const char* a, Py_ssize_t b, int c) {
    unimplemented("_PyLong_FromBytes"); exit(-1);
}
STATS_CONTAINER(_PyLong_FromTime_t, _PyLong_AsInt)
PyObject* (*__target___PyLong_FromTime_t)(time_t) = NULL;
PyAPI_FUNC(PyObject*) _PyLong_FromTime_t(time_t a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(_PyLong_FromTime_t)
    PyObject* result = (PyObject*) __target___PyLong_FromTime_t(a);
    STATS_AFTER(_PyLong_FromTime_t)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(PyObject*) _PyLong_GCD(PyObject* a, PyObject* b) {
    unimplemented("_PyLong_GCD"); exit(-1);
}
PyAPI_FUNC(PyObject*) _PyLong_Lshift(PyObject* a, size_t b) {
    unimplemented("_PyLong_Lshift"); exit(-1);
}
PyAPI_FUNC(PyLongObject*) _PyLong_New(Py_ssize_t a) {
    unimplemented("_PyLong_New"); exit(-1);
}
PyAPI_FUNC(size_t) _PyLong_NumBits(PyObject* a) {
    unimplemented("_PyLong_NumBits"); exit(-1);
}
PyAPI_FUNC(PyObject*) _PyLong_Rshift(PyObject* a, size_t b) {
    unimplemented("_PyLong_Rshift"); exit(-1);
}
STATS_CONTAINER(_PyLong_Sign, _PyLong_FromTime_t)
int (*__target___PyLong_Sign)(PyObject*) = NULL;
PyAPI_FUNC(int) _PyLong_Sign(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(_PyLong_Sign)
    int result = (int) __target___PyLong_Sign(a);
    STATS_AFTER(_PyLong_Sign)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(int) _PyLong_Size_t_Converter(PyObject* a, void* b) {
    unimplemented("_PyLong_Size_t_Converter"); exit(-1);
}
PyAPI_FUNC(int) _PyLong_UnsignedInt_Converter(PyObject* a, void* b) {
    unimplemented("_PyLong_UnsignedInt_Converter"); exit(-1);
}
PyAPI_FUNC(int) _PyLong_UnsignedLongLong_Converter(PyObject* a, void* b) {
    unimplemented("_PyLong_UnsignedLongLong_Converter"); exit(-1);
}
PyAPI_FUNC(int) _PyLong_UnsignedLong_Converter(PyObject* a, void* b) {
    unimplemented("_PyLong_UnsignedLong_Converter"); exit(-1);
}
PyAPI_FUNC(int) _PyLong_UnsignedShort_Converter(PyObject* a, void* b) {
    unimplemented("_PyLong_UnsignedShort_Converter"); exit(-1);
}
PyAPI_FUNC(const char*) _PyMem_GetCurrentAllocatorName() {
    unimplemented("_PyMem_GetCurrentAllocatorName"); exit(-1);
}
PyAPI_FUNC(char*) _PyMem_RawStrdup(const char* a) {
    unimplemented("_PyMem_RawStrdup"); exit(-1);
}
PyAPI_FUNC(wchar_t*) _PyMem_RawWcsdup(const wchar_t* a) {
    unimplemented("_PyMem_RawWcsdup"); exit(-1);
}
PyAPI_FUNC(char*) _PyMem_Strdup(const char* a) {
    unimplemented("_PyMem_Strdup"); exit(-1);
}
STATS_CONTAINER(_PyMemoryView_GetBuffer, _PyLong_Sign)
Py_buffer* (*__target___PyMemoryView_GetBuffer)(PyObject*) = NULL;
PyAPI_FUNC(Py_buffer*) _PyMemoryView_GetBuffer(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(_PyMemoryView_GetBuffer)
    Py_buffer* result = (Py_buffer*) __target___PyMemoryView_GetBuffer(a);
    STATS_AFTER(_PyMemoryView_GetBuffer)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(PyObject*) _PyMethodDef_RawFastCallDict(PyMethodDef* a, PyObject* b, PyObject*const* c, Py_ssize_t d, PyObject* e) {
    unimplemented("_PyMethodDef_RawFastCallDict"); exit(-1);
}
PyAPI_FUNC(PyObject*) _PyMethodDef_RawFastCallKeywords(PyMethodDef* a, PyObject* b, PyObject*const* c, Py_ssize_t d, PyObject* e) {
    unimplemented("_PyMethodDef_RawFastCallKeywords"); exit(-1);
}
PyAPI_FUNC(void) _PyMethod_DebugMallocStats(FILE* a) {
    unimplemented("_PyMethod_DebugMallocStats"); exit(-1);
}
PyAPI_FUNC(int) _PyModuleSpec_IsInitializing(PyObject* a) {
    unimplemented("_PyModuleSpec_IsInitializing"); exit(-1);
}
PyAPI_FUNC(void) _PyModule_Clear(PyObject* a) {
    unimplemented("_PyModule_Clear"); exit(-1);
}
PyAPI_FUNC(void) _PyModule_ClearDict(PyObject* a) {
    unimplemented("_PyModule_ClearDict"); exit(-1);
}
STATS_CONTAINER(_PyModule_CreateInitialized, _PyMemoryView_GetBuffer)
PyObject* (*__target___PyModule_CreateInitialized)(struct PyModuleDef*, int) = NULL;
PyAPI_FUNC(PyObject*) _PyModule_CreateInitialized(struct PyModuleDef* a, int b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(_PyModule_CreateInitialized)
    PyObject* result = (PyObject*) __target___PyModule_CreateInitialized(a, b);
    STATS_AFTER(_PyModule_CreateInitialized)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyModule_GetDef, _PyModule_CreateInitialized)
PyModuleDef* (*__target___PyModule_GetDef)(PyObject*) = NULL;
PyAPI_FUNC(PyModuleDef*) _PyModule_GetDef(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(_PyModule_GetDef)
    PyModuleDef* result = (PyModuleDef*) __target___PyModule_GetDef(a);
    STATS_AFTER(_PyModule_GetDef)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyModule_GetDict, _PyModule_GetDef)
PyObject* (*__target___PyModule_GetDict)(PyObject*) = NULL;
PyAPI_FUNC(PyObject*) _PyModule_GetDict(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(_PyModule_GetDict)
    PyObject* result = (PyObject*) __target___PyModule_GetDict(a);
    STATS_AFTER(_PyModule_GetDict)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyModule_GetState, _PyModule_GetDict)
void* (*__target___PyModule_GetState)(PyObject*) = NULL;
PyAPI_FUNC(void*) _PyModule_GetState(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(_PyModule_GetState)
    void* result = (void*) __target___PyModule_GetState(a);
    STATS_AFTER(_PyModule_GetState)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyNamespace_New, _PyModule_GetState)
PyObject* (*__target___PyNamespace_New)(PyObject*) = NULL;
PyAPI_FUNC(PyObject*) _PyNamespace_New(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(_PyNamespace_New)
    PyObject* result = (PyObject*) __target___PyNamespace_New(a);
    STATS_AFTER(_PyNamespace_New)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyNumber_Index, _PyNamespace_New)
PyObject* (*__target___PyNumber_Index)(PyObject*) = NULL;
PyAPI_FUNC(PyObject*) _PyNumber_Index(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(_PyNumber_Index)
    PyObject* result = (PyObject*) __target___PyNumber_Index(a);
    STATS_AFTER(_PyNumber_Index)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(int) _PyOS_IsMainThread() {
    unimplemented("_PyOS_IsMainThread"); exit(-1);
}
PyAPI_FUNC(int) _PyOS_URandom(void* a, Py_ssize_t b) {
    unimplemented("_PyOS_URandom"); exit(-1);
}
PyAPI_FUNC(int) _PyOS_URandomNonblock(void* a, Py_ssize_t b) {
    unimplemented("_PyOS_URandomNonblock"); exit(-1);
}
PyAPI_FUNC(int) _PyObjectDict_SetItem(PyTypeObject* a, PyObject** b, PyObject* c, PyObject* d) {
    unimplemented("_PyObjectDict_SetItem"); exit(-1);
}
PyAPI_FUNC(void) _PyObject_AssertFailed(PyObject* a, const char* b, const char* c, const char* d, int e, const char* f) {
    unimplemented("_PyObject_AssertFailed"); exit(-1);
}
STATS_CONTAINER(_PyObject_Call1, _PyNumber_Index)
PyObject* (*__target___PyObject_Call1)(PyObject*, PyObject*, PyObject*, int) = NULL;
PyAPI_FUNC(PyObject*) _PyObject_Call1(PyObject* a, PyObject* b, PyObject* c, int d) {
    LOG("0x%lx 0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c, (unsigned long) d);
    STATS_BEFORE(_PyObject_Call1)
    PyObject* result = (PyObject*) __target___PyObject_Call1(a, b, c, d);
    STATS_AFTER(_PyObject_Call1)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyObject_CallMethod1, _PyObject_Call1)
PyObject* (*__target___PyObject_CallMethod1)(PyObject*, const char*, PyObject*, int) = NULL;
PyAPI_FUNC(PyObject*) _PyObject_CallMethod1(PyObject* a, const char* b, PyObject* c, int d) {
    LOG("0x%lx '%s'(0x%lx) 0x%lx 0x%lx", (unsigned long) a, b?b:"<null>", (unsigned long) b, (unsigned long) c, (unsigned long) d);
    STATS_BEFORE(_PyObject_CallMethod1)
    PyObject* result = (PyObject*) __target___PyObject_CallMethod1(a, b, c, d);
    STATS_AFTER(_PyObject_CallMethod1)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(PyObject*) _PyObject_CallMethodId(PyObject* a, _Py_Identifier* b, const char* c, ...) {
    unimplemented("_PyObject_CallMethodId"); exit(-1);
}
PyAPI_FUNC(PyObject*) _PyObject_CallMethodIdObjArgs(PyObject* a, struct _Py_Identifier* b, ...) {
    unimplemented("_PyObject_CallMethodIdObjArgs"); exit(-1);
}
PyAPI_FUNC(PyObject*) _PyObject_CallMethodId_SizeT(PyObject* a, _Py_Identifier* b, const char* c, ...) {
    unimplemented("_PyObject_CallMethodId_SizeT"); exit(-1);
}
PyAPI_FUNC(PyObject*) _PyObject_Call_Prepend(PyObject* a, PyObject* b, PyObject* c, PyObject* d) {
    unimplemented("_PyObject_Call_Prepend"); exit(-1);
}
PyAPI_FUNC(int) _PyObject_CheckConsistency(PyObject* a, int b) {
    unimplemented("_PyObject_CheckConsistency"); exit(-1);
}
PyAPI_FUNC(int) _PyObject_CheckCrossInterpreterData(PyObject* a) {
    unimplemented("_PyObject_CheckCrossInterpreterData"); exit(-1);
}
PyAPI_FUNC(int) _PyObject_DebugMallocStats(FILE* a) {
    unimplemented("_PyObject_DebugMallocStats"); exit(-1);
}
PyAPI_FUNC(void) _PyObject_DebugTypeStats(FILE* a) {
    unimplemented("_PyObject_DebugTypeStats"); exit(-1);
}
STATS_CONTAINER(_PyObject_Dump, _PyObject_CallMethod1)
void (*__target___PyObject_Dump)(PyObject*) = NULL;
PyAPI_FUNC(void) _PyObject_Dump(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(_PyObject_Dump)
    __target___PyObject_Dump(a);
    STATS_AFTER(_PyObject_Dump)
    LOG_AFTER_VOID
}
PyAPI_FUNC(PyObject*) _PyObject_FastCallDict(PyObject* a, PyObject*const* b, size_t c, PyObject* d) {
    unimplemented("_PyObject_FastCallDict"); exit(-1);
}
PyAPI_FUNC(PyObject*) _PyObject_FastCall_Prepend(PyObject* a, PyObject* b, PyObject*const* c, Py_ssize_t d) {
    unimplemented("_PyObject_FastCall_Prepend"); exit(-1);
}
PyAPI_FUNC(PyObject*) _PyObject_FunctionStr(PyObject* a) {
    unimplemented("_PyObject_FunctionStr"); exit(-1);
}
STATS_CONTAINER(_PyObject_GC_Calloc, _PyObject_Dump)
PyObject* (*__target___PyObject_GC_Calloc)(size_t) = NULL;
PyAPI_FUNC(PyObject*) _PyObject_GC_Calloc(size_t a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(_PyObject_GC_Calloc)
    PyObject* result = (PyObject*) __target___PyObject_GC_Calloc(a);
    STATS_AFTER(_PyObject_GC_Calloc)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyObject_GC_Malloc, _PyObject_GC_Calloc)
PyObject* (*__target___PyObject_GC_Malloc)(size_t) = NULL;
PyAPI_FUNC(PyObject*) _PyObject_GC_Malloc(size_t a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(_PyObject_GC_Malloc)
    PyObject* result = (PyObject*) __target___PyObject_GC_Malloc(a);
    STATS_AFTER(_PyObject_GC_Malloc)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyObject_GC_New, _PyObject_GC_Malloc)
PyObject* (*__target___PyObject_GC_New)(PyTypeObject*) = NULL;
PyAPI_FUNC(PyObject*) _PyObject_GC_New(PyTypeObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(_PyObject_GC_New)
    PyObject* result = (PyObject*) __target___PyObject_GC_New(a);
    STATS_AFTER(_PyObject_GC_New)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyObject_GC_NewVar, _PyObject_GC_New)
PyVarObject* (*__target___PyObject_GC_NewVar)(PyTypeObject*, Py_ssize_t) = NULL;
PyAPI_FUNC(PyVarObject*) _PyObject_GC_NewVar(PyTypeObject* a, Py_ssize_t b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(_PyObject_GC_NewVar)
    PyVarObject* result = (PyVarObject*) __target___PyObject_GC_NewVar(a, b);
    STATS_AFTER(_PyObject_GC_NewVar)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(PyVarObject*) _PyObject_GC_Resize(PyVarObject* a, Py_ssize_t b) {
    unimplemented("_PyObject_GC_Resize"); exit(-1);
}
PyAPI_FUNC(PyObject*) _PyObject_GenericGetAttrWithDict(PyObject* a, PyObject* b, PyObject* c, int d) {
    unimplemented("_PyObject_GenericGetAttrWithDict"); exit(-1);
}
PyAPI_FUNC(int) _PyObject_GenericSetAttrWithDict(PyObject* a, PyObject* b, PyObject* c, PyObject* d) {
    unimplemented("_PyObject_GenericSetAttrWithDict"); exit(-1);
}
STATS_CONTAINER(_PyObject_GetAttrId, _PyObject_GC_NewVar)
PyObject* (*__target___PyObject_GetAttrId)(PyObject*, struct _Py_Identifier*) = NULL;
PyAPI_FUNC(PyObject*) _PyObject_GetAttrId(PyObject* a, struct _Py_Identifier* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(_PyObject_GetAttrId)
    PyObject* result = (PyObject*) __target___PyObject_GetAttrId(a, b);
    STATS_AFTER(_PyObject_GetAttrId)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(int) _PyObject_GetCrossInterpreterData(PyObject* a, _PyCrossInterpreterData* b) {
    unimplemented("_PyObject_GetCrossInterpreterData"); exit(-1);
}
PyAPI_FUNC(int) _PyObject_GetMethod(PyObject* a, PyObject* b, PyObject** c) {
    unimplemented("_PyObject_GetMethod"); exit(-1);
}
PyAPI_FUNC(int) _PyObject_HasAttrId(PyObject* a, struct _Py_Identifier* b) {
    unimplemented("_PyObject_HasAttrId"); exit(-1);
}
PyAPI_FUNC(int) _PyObject_HasLen(PyObject* a) {
    unimplemented("_PyObject_HasLen"); exit(-1);
}
PyAPI_FUNC(int) _PyObject_IsAbstract(PyObject* a) {
    unimplemented("_PyObject_IsAbstract"); exit(-1);
}
PyAPI_FUNC(int) _PyObject_IsFreed(PyObject* a) {
    unimplemented("_PyObject_IsFreed"); exit(-1);
}
STATS_CONTAINER(_PyObject_LookupAttr, _PyObject_GetAttrId)
int (*__target___PyObject_LookupAttr)(PyObject*, PyObject*, PyObject**) = NULL;
PyAPI_FUNC(int) _PyObject_LookupAttr(PyObject* a, PyObject* b, PyObject** c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    STATS_BEFORE(_PyObject_LookupAttr)
    int result = (int) __target___PyObject_LookupAttr(a, b, c);
    STATS_AFTER(_PyObject_LookupAttr)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyObject_LookupAttrId, _PyObject_LookupAttr)
int (*__target___PyObject_LookupAttrId)(PyObject*, struct _Py_Identifier*, PyObject**) = NULL;
PyAPI_FUNC(int) _PyObject_LookupAttrId(PyObject* a, struct _Py_Identifier* b, PyObject** c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    STATS_BEFORE(_PyObject_LookupAttrId)
    int result = (int) __target___PyObject_LookupAttrId(a, b, c);
    STATS_AFTER(_PyObject_LookupAttrId)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(PyObject*) _PyObject_LookupSpecial(PyObject* a, _Py_Identifier* b) {
    unimplemented("_PyObject_LookupSpecial"); exit(-1);
}
STATS_CONTAINER(_PyObject_MakeTpCall, _PyObject_LookupAttrId)
PyObject* (*__target___PyObject_MakeTpCall)(PyThreadState*, PyObject*, PyObject*const*, Py_ssize_t, PyObject*) = NULL;
PyAPI_FUNC(PyObject*) _PyObject_MakeTpCall(PyThreadState* a, PyObject* b, PyObject*const* c, Py_ssize_t d, PyObject* e) {
    LOG("0x%lx 0x%lx 0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c, (unsigned long) d, (unsigned long) e);
    STATS_BEFORE(_PyObject_MakeTpCall)
    PyObject* result = (PyObject*) __target___PyObject_MakeTpCall(a, b, c, d, e);
    STATS_AFTER(_PyObject_MakeTpCall)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyObject_New, _PyObject_MakeTpCall)
PyObject* (*__target___PyObject_New)(PyTypeObject*) = NULL;
PyAPI_FUNC(PyObject*) _PyObject_New(PyTypeObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(_PyObject_New)
    PyObject* result = (PyObject*) __target___PyObject_New(a);
    STATS_AFTER(_PyObject_New)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyObject_NewVar, _PyObject_New)
PyVarObject* (*__target___PyObject_NewVar)(PyTypeObject*, Py_ssize_t) = NULL;
PyAPI_FUNC(PyVarObject*) _PyObject_NewVar(PyTypeObject* a, Py_ssize_t b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(_PyObject_NewVar)
    PyVarObject* result = (PyVarObject*) __target___PyObject_NewVar(a, b);
    STATS_AFTER(_PyObject_NewVar)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyObject_NextNotImplemented, _PyObject_NewVar)
PyObject* (*__target___PyObject_NextNotImplemented)(PyObject*) = NULL;
PyAPI_FUNC(PyObject*) _PyObject_NextNotImplemented(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(_PyObject_NextNotImplemented)
    PyObject* result = (PyObject*) __target___PyObject_NextNotImplemented(a);
    STATS_AFTER(_PyObject_NextNotImplemented)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(int) _PyObject_RealIsInstance(PyObject* a, PyObject* b) {
    unimplemented("_PyObject_RealIsInstance"); exit(-1);
}
PyAPI_FUNC(int) _PyObject_RealIsSubclass(PyObject* a, PyObject* b) {
    unimplemented("_PyObject_RealIsSubclass"); exit(-1);
}
STATS_CONTAINER(_PyObject_SetAttrId, _PyObject_NextNotImplemented)
int (*__target___PyObject_SetAttrId)(PyObject*, struct _Py_Identifier*, PyObject*) = NULL;
PyAPI_FUNC(int) _PyObject_SetAttrId(PyObject* a, struct _Py_Identifier* b, PyObject* c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    STATS_BEFORE(_PyObject_SetAttrId)
    int result = (int) __target___PyObject_SetAttrId(a, b, c);
    STATS_AFTER(_PyObject_SetAttrId)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(int) _PyRun_AnyFileObject(FILE* a, PyObject* b, int c, PyCompilerFlags* d) {
    unimplemented("_PyRun_AnyFileObject"); exit(-1);
}
PyAPI_FUNC(int) _PyRun_InteractiveLoopObject(FILE* a, PyObject* b, PyCompilerFlags* c) {
    unimplemented("_PyRun_InteractiveLoopObject"); exit(-1);
}
PyAPI_FUNC(int) _PyRun_SimpleFileObject(FILE* a, PyObject* b, int c, PyCompilerFlags* d) {
    unimplemented("_PyRun_SimpleFileObject"); exit(-1);
}
PyAPI_FUNC(char*const*) _PySequence_BytesToCharpArray(PyObject* a) {
    unimplemented("_PySequence_BytesToCharpArray"); exit(-1);
}
STATS_CONTAINER(_PySequence_Fast_ITEMS, _PyObject_SetAttrId)
PyObject** (*__target___PySequence_Fast_ITEMS)(PyObject*) = NULL;
PyAPI_FUNC(PyObject**) _PySequence_Fast_ITEMS(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(_PySequence_Fast_ITEMS)
    PyObject** result = (PyObject**) __target___PySequence_Fast_ITEMS(a);
    STATS_AFTER(_PySequence_Fast_ITEMS)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PySequence_ITEM, _PySequence_Fast_ITEMS)
PyObject* (*__target___PySequence_ITEM)(PyObject*, Py_ssize_t) = NULL;
PyAPI_FUNC(PyObject*) _PySequence_ITEM(PyObject* a, Py_ssize_t b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(_PySequence_ITEM)
    PyObject* result = (PyObject*) __target___PySequence_ITEM(a, b);
    STATS_AFTER(_PySequence_ITEM)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(Py_ssize_t) _PySequence_IterSearch(PyObject* a, PyObject* b, int c) {
    unimplemented("_PySequence_IterSearch"); exit(-1);
}
STATS_CONTAINER(_PySet_NextEntry, _PySequence_ITEM)
int (*__target___PySet_NextEntry)(PyObject*, Py_ssize_t*, PyObject**, Py_hash_t*) = NULL;
PyAPI_FUNC(int) _PySet_NextEntry(PyObject* a, Py_ssize_t* b, PyObject** c, Py_hash_t* d) {
    LOG("0x%lx 0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c, (unsigned long) d);
    STATS_BEFORE(_PySet_NextEntry)
    int result = (int) __target___PySet_NextEntry(a, b, c, d);
    STATS_AFTER(_PySet_NextEntry)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(int) _PySet_Update(PyObject* a, PyObject* b) {
    unimplemented("_PySet_Update"); exit(-1);
}
PyAPI_FUNC(void) _PySignal_AfterFork() {
    unimplemented("_PySignal_AfterFork"); exit(-1);
}
PyAPI_FUNC(PyObject*) _PySlice_FromIndices(Py_ssize_t a, Py_ssize_t b) {
    unimplemented("_PySlice_FromIndices"); exit(-1);
}
PyAPI_FUNC(int) _PySlice_GetLongIndices(PySliceObject* a, PyObject* b, PyObject** c, PyObject** d, PyObject** e) {
    unimplemented("_PySlice_GetLongIndices"); exit(-1);
}
PyAPI_FUNC(PyObject*) _PyStack_AsDict(PyObject*const* a, PyObject* b) {
    unimplemented("_PyStack_AsDict"); exit(-1);
}
STATS_CONTAINER(_PyStack_UnpackDict, _PySet_NextEntry)
int (*__target___PyStack_UnpackDict)(PyObject*const*, Py_ssize_t, PyObject*, PyObject*const**, PyObject**) = NULL;
PyAPI_FUNC(int) _PyStack_UnpackDict(PyObject*const* a, Py_ssize_t b, PyObject* c, PyObject*const** d, PyObject** e) {
    LOG("0x%lx 0x%lx 0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c, (unsigned long) d, (unsigned long) e);
    STATS_BEFORE(_PyStack_UnpackDict)
    int result = (int) __target___PyStack_UnpackDict(a, b, c, d, e);
    STATS_AFTER(_PyStack_UnpackDict)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(int) _PyState_AddModule(PyObject* a, struct PyModuleDef* b) {
    unimplemented("_PyState_AddModule"); exit(-1);
}
PyAPI_FUNC(PyObject*) _PySys_GetObjectId(_Py_Identifier* a) {
    unimplemented("_PySys_GetObjectId"); exit(-1);
}
PyAPI_FUNC(size_t) _PySys_GetSizeOf(PyObject* a) {
    unimplemented("_PySys_GetSizeOf"); exit(-1);
}
PyAPI_FUNC(int) _PySys_SetObjectId(_Py_Identifier* a, PyObject* b) {
    unimplemented("_PySys_SetObjectId"); exit(-1);
}
PyAPI_FUNC(PyObject*) _PyThreadState_GetDict(PyThreadState* a) {
    unimplemented("_PyThreadState_GetDict"); exit(-1);
}
PyAPI_FUNC(PyThreadState*) _PyThreadState_Prealloc(PyInterpreterState* a) {
    unimplemented("_PyThreadState_Prealloc"); exit(-1);
}
STATS_CONTAINER(_PyThreadState_UncheckedGet, _PyStack_UnpackDict)
PyThreadState* (*__target___PyThreadState_UncheckedGet)() = NULL;
PyAPI_FUNC(PyThreadState*) _PyThreadState_UncheckedGet() {
    LOGS("");
    STATS_BEFORE(_PyThreadState_UncheckedGet)
    PyThreadState* result = (PyThreadState*) __target___PyThreadState_UncheckedGet();
    STATS_AFTER(_PyThreadState_UncheckedGet)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(PyObject*) _PyThread_CurrentExceptions() {
    unimplemented("_PyThread_CurrentExceptions"); exit(-1);
}
PyAPI_FUNC(PyObject*) _PyThread_CurrentFrames() {
    unimplemented("_PyThread_CurrentFrames"); exit(-1);
}
PyAPI_FUNC(int) _PyThread_at_fork_reinit(PyThread_type_lock* a) {
    unimplemented("_PyThread_at_fork_reinit"); exit(-1);
}
PyAPI_FUNC(_PyTime_t) _PyTime_AsMicroseconds(_PyTime_t a, _PyTime_round_t b) {
    unimplemented("_PyTime_AsMicroseconds"); exit(-1);
}
PyAPI_FUNC(_PyTime_t) _PyTime_AsMilliseconds(_PyTime_t a, _PyTime_round_t b) {
    unimplemented("_PyTime_AsMilliseconds"); exit(-1);
}
PyAPI_FUNC(PyObject*) _PyTime_AsNanosecondsObject(_PyTime_t a) {
    unimplemented("_PyTime_AsNanosecondsObject"); exit(-1);
}
PyAPI_FUNC(double) _PyTime_AsSecondsDouble(_PyTime_t a) {
    unimplemented("_PyTime_AsSecondsDouble"); exit(-1);
}
PyAPI_FUNC(int) _PyTime_AsTimespec(_PyTime_t a, struct timespec* b) {
    unimplemented("_PyTime_AsTimespec"); exit(-1);
}
PyAPI_FUNC(int) _PyTime_AsTimeval(_PyTime_t a, struct timeval* b, _PyTime_round_t c) {
    unimplemented("_PyTime_AsTimeval"); exit(-1);
}
PyAPI_FUNC(int) _PyTime_AsTimevalTime_t(_PyTime_t a, time_t* b, int* c, _PyTime_round_t d) {
    unimplemented("_PyTime_AsTimevalTime_t"); exit(-1);
}
PyAPI_FUNC(int) _PyTime_AsTimeval_noraise(_PyTime_t a, struct timeval* b, _PyTime_round_t c) {
    unimplemented("_PyTime_AsTimeval_noraise"); exit(-1);
}
PyAPI_FUNC(int) _PyTime_FromMillisecondsObject(_PyTime_t* a, PyObject* b, _PyTime_round_t c) {
    unimplemented("_PyTime_FromMillisecondsObject"); exit(-1);
}
PyAPI_FUNC(_PyTime_t) _PyTime_FromNanoseconds(_PyTime_t a) {
    unimplemented("_PyTime_FromNanoseconds"); exit(-1);
}
PyAPI_FUNC(int) _PyTime_FromNanosecondsObject(_PyTime_t* a, PyObject* b) {
    unimplemented("_PyTime_FromNanosecondsObject"); exit(-1);
}
PyAPI_FUNC(_PyTime_t) _PyTime_FromSeconds(int a) {
    unimplemented("_PyTime_FromSeconds"); exit(-1);
}
PyAPI_FUNC(int) _PyTime_FromSecondsObject(_PyTime_t* a, PyObject* b, _PyTime_round_t c) {
    unimplemented("_PyTime_FromSecondsObject"); exit(-1);
}
PyAPI_FUNC(int) _PyTime_FromTimespec(_PyTime_t* a, struct timespec* b) {
    unimplemented("_PyTime_FromTimespec"); exit(-1);
}
PyAPI_FUNC(int) _PyTime_FromTimeval(_PyTime_t* a, struct timeval* b) {
    unimplemented("_PyTime_FromTimeval"); exit(-1);
}
PyAPI_FUNC(_PyTime_t) _PyTime_GetMonotonicClock() {
    unimplemented("_PyTime_GetMonotonicClock"); exit(-1);
}
PyAPI_FUNC(int) _PyTime_GetMonotonicClockWithInfo(_PyTime_t* a, _Py_clock_info_t* b) {
    unimplemented("_PyTime_GetMonotonicClockWithInfo"); exit(-1);
}
PyAPI_FUNC(_PyTime_t) _PyTime_GetPerfCounter() {
    unimplemented("_PyTime_GetPerfCounter"); exit(-1);
}
PyAPI_FUNC(int) _PyTime_GetPerfCounterWithInfo(_PyTime_t* a, _Py_clock_info_t* b) {
    unimplemented("_PyTime_GetPerfCounterWithInfo"); exit(-1);
}
PyAPI_FUNC(_PyTime_t) _PyTime_GetSystemClock() {
    unimplemented("_PyTime_GetSystemClock"); exit(-1);
}
PyAPI_FUNC(int) _PyTime_GetSystemClockWithInfo(_PyTime_t* a, _Py_clock_info_t* b) {
    unimplemented("_PyTime_GetSystemClockWithInfo"); exit(-1);
}
PyAPI_FUNC(_PyTime_t) _PyTime_MulDiv(_PyTime_t a, _PyTime_t b, _PyTime_t c) {
    unimplemented("_PyTime_MulDiv"); exit(-1);
}
PyAPI_FUNC(int) _PyTime_ObjectToTime_t(PyObject* a, time_t* b, _PyTime_round_t c) {
    unimplemented("_PyTime_ObjectToTime_t"); exit(-1);
}
PyAPI_FUNC(int) _PyTime_ObjectToTimespec(PyObject* a, time_t* b, long* c, _PyTime_round_t d) {
    unimplemented("_PyTime_ObjectToTimespec"); exit(-1);
}
PyAPI_FUNC(int) _PyTime_ObjectToTimeval(PyObject* a, time_t* b, long* c, _PyTime_round_t d) {
    unimplemented("_PyTime_ObjectToTimeval"); exit(-1);
}
PyAPI_FUNC(int) _PyTime_gmtime(time_t a, struct tm* b) {
    unimplemented("_PyTime_gmtime"); exit(-1);
}
PyAPI_FUNC(int) _PyTime_localtime(time_t a, struct tm* b) {
    unimplemented("_PyTime_localtime"); exit(-1);
}
PyAPI_FUNC(PyObject*) _PyTraceMalloc_GetTraceback(unsigned int a, uintptr_t b) {
    unimplemented("_PyTraceMalloc_GetTraceback"); exit(-1);
}
STATS_CONTAINER(_PyTraceMalloc_NewReference, _PyThreadState_UncheckedGet)
int (*__target___PyTraceMalloc_NewReference)(PyObject*) = NULL;
PyAPI_FUNC(int) _PyTraceMalloc_NewReference(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(_PyTraceMalloc_NewReference)
    int result = (int) __target___PyTraceMalloc_NewReference(a);
    STATS_AFTER(_PyTraceMalloc_NewReference)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyTraceback_Add, _PyTraceMalloc_NewReference)
void (*__target___PyTraceback_Add)(const char*, const char*, int) = NULL;
PyAPI_FUNC(void) _PyTraceback_Add(const char* a, const char* b, int c) {
    LOG("'%s'(0x%lx) '%s'(0x%lx) 0x%lx", a?a:"<null>", (unsigned long) a, b?b:"<null>", (unsigned long) b, (unsigned long) c);
    STATS_BEFORE(_PyTraceback_Add)
    __target___PyTraceback_Add(a, b, c);
    STATS_AFTER(_PyTraceback_Add)
    LOG_AFTER_VOID
}
PyAPI_FUNC(int) _PyTrash_begin(struct _ts* a, PyObject* b) {
    unimplemented("_PyTrash_begin"); exit(-1);
}
PyAPI_FUNC(int) _PyTrash_cond(PyObject* a, destructor b) {
    unimplemented("_PyTrash_cond"); exit(-1);
}
PyAPI_FUNC(void) _PyTrash_deposit_object(PyObject* a) {
    unimplemented("_PyTrash_deposit_object"); exit(-1);
}
PyAPI_FUNC(void) _PyTrash_destroy_chain() {
    unimplemented("_PyTrash_destroy_chain"); exit(-1);
}
PyAPI_FUNC(void) _PyTrash_end(struct _ts* a) {
    unimplemented("_PyTrash_end"); exit(-1);
}
PyAPI_FUNC(void) _PyTrash_thread_deposit_object(PyObject* a) {
    unimplemented("_PyTrash_thread_deposit_object"); exit(-1);
}
PyAPI_FUNC(void) _PyTrash_thread_destroy_chain() {
    unimplemented("_PyTrash_thread_destroy_chain"); exit(-1);
}
PyAPI_FUNC(void) _PyTuple_DebugMallocStats(FILE* a) {
    unimplemented("_PyTuple_DebugMallocStats"); exit(-1);
}
PyAPI_FUNC(void) _PyTuple_MaybeUntrack(PyObject* a) {
    unimplemented("_PyTuple_MaybeUntrack"); exit(-1);
}
PyAPI_FUNC(int) _PyTuple_Resize(PyObject** a, Py_ssize_t b) {
    unimplemented("_PyTuple_Resize"); exit(-1);
}
PyAPI_FUNC(PyTypeObject*) _PyType_CalculateMetaclass(PyTypeObject* a, PyObject* b) {
    unimplemented("_PyType_CalculateMetaclass"); exit(-1);
}
PyAPI_FUNC(PyObject*) _PyType_GetDocFromInternalDoc(const char* a, const char* b) {
    unimplemented("_PyType_GetDocFromInternalDoc"); exit(-1);
}
STATS_CONTAINER(_PyType_GetModuleByDef, _PyTraceback_Add)
PyObject* (*__target___PyType_GetModuleByDef)(PyTypeObject*, struct PyModuleDef*) = NULL;
PyAPI_FUNC(PyObject*) _PyType_GetModuleByDef(PyTypeObject* a, struct PyModuleDef* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(_PyType_GetModuleByDef)
    PyObject* result = (PyObject*) __target___PyType_GetModuleByDef(a, b);
    STATS_AFTER(_PyType_GetModuleByDef)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(PyObject*) _PyType_GetTextSignatureFromInternalDoc(const char* a, const char* b) {
    unimplemented("_PyType_GetTextSignatureFromInternalDoc"); exit(-1);
}
STATS_CONTAINER(_PyType_Lookup, _PyType_GetModuleByDef)
PyObject* (*__target___PyType_Lookup)(PyTypeObject*, PyObject*) = NULL;
PyAPI_FUNC(PyObject*) _PyType_Lookup(PyTypeObject* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(_PyType_Lookup)
    PyObject* result = (PyObject*) __target___PyType_Lookup(a, b);
    STATS_AFTER(_PyType_Lookup)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(PyObject*) _PyType_LookupId(PyTypeObject* a, _Py_Identifier* b) {
    unimplemented("_PyType_LookupId"); exit(-1);
}
STATS_CONTAINER(_PyType_Name, _PyType_Lookup)
const char* (*__target___PyType_Name)(PyTypeObject*) = NULL;
PyAPI_FUNC(const char*) _PyType_Name(PyTypeObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(_PyType_Name)
    const char* result = (const char*) __target___PyType_Name(a);
    STATS_AFTER(_PyType_Name)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyUnicodeObject_DATA, _PyType_Name)
void* (*__target___PyUnicodeObject_DATA)(PyUnicodeObject*) = NULL;
PyAPI_FUNC(void*) _PyUnicodeObject_DATA(PyUnicodeObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(_PyUnicodeObject_DATA)
    void* result = (void*) __target___PyUnicodeObject_DATA(a);
    STATS_AFTER(_PyUnicodeObject_DATA)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(PyObject*) _PyUnicodeTranslateError_Create(PyObject* a, Py_ssize_t b, Py_ssize_t c, const char* d) {
    unimplemented("_PyUnicodeTranslateError_Create"); exit(-1);
}
PyAPI_FUNC(void) _PyUnicodeWriter_Dealloc(_PyUnicodeWriter* a) {
    unimplemented("_PyUnicodeWriter_Dealloc"); exit(-1);
}
PyAPI_FUNC(PyObject*) _PyUnicodeWriter_Finish(_PyUnicodeWriter* a) {
    unimplemented("_PyUnicodeWriter_Finish"); exit(-1);
}
PyAPI_FUNC(void) _PyUnicodeWriter_Init(_PyUnicodeWriter* a) {
    unimplemented("_PyUnicodeWriter_Init"); exit(-1);
}
PyAPI_FUNC(int) _PyUnicodeWriter_PrepareInternal(_PyUnicodeWriter* a, Py_ssize_t b, Py_UCS4 c) {
    unimplemented("_PyUnicodeWriter_PrepareInternal"); exit(-1);
}
PyAPI_FUNC(int) _PyUnicodeWriter_PrepareKindInternal(_PyUnicodeWriter* a, enum PyUnicode_Kind b) {
    unimplemented("_PyUnicodeWriter_PrepareKindInternal"); exit(-1);
}
PyAPI_FUNC(int) _PyUnicodeWriter_WriteASCIIString(_PyUnicodeWriter* a, const char* b, Py_ssize_t c) {
    unimplemented("_PyUnicodeWriter_WriteASCIIString"); exit(-1);
}
PyAPI_FUNC(int) _PyUnicodeWriter_WriteChar(_PyUnicodeWriter* a, Py_UCS4 b) {
    unimplemented("_PyUnicodeWriter_WriteChar"); exit(-1);
}
PyAPI_FUNC(int) _PyUnicodeWriter_WriteLatin1String(_PyUnicodeWriter* a, const char* b, Py_ssize_t c) {
    unimplemented("_PyUnicodeWriter_WriteLatin1String"); exit(-1);
}
PyAPI_FUNC(int) _PyUnicodeWriter_WriteStr(_PyUnicodeWriter* a, PyObject* b) {
    unimplemented("_PyUnicodeWriter_WriteStr"); exit(-1);
}
PyAPI_FUNC(int) _PyUnicodeWriter_WriteSubstring(_PyUnicodeWriter* a, PyObject* b, Py_ssize_t c, Py_ssize_t d) {
    unimplemented("_PyUnicodeWriter_WriteSubstring"); exit(-1);
}
STATS_CONTAINER(_PyUnicode_AsASCIIString, _PyUnicodeObject_DATA)
PyObject* (*__target___PyUnicode_AsASCIIString)(PyObject*, const char*) = NULL;
PyAPI_FUNC(PyObject*) _PyUnicode_AsASCIIString(PyObject* a, const char* b) {
    LOG("0x%lx '%s'(0x%lx)", (unsigned long) a, b?b:"<null>", (unsigned long) b);
    STATS_BEFORE(_PyUnicode_AsASCIIString)
    PyObject* result = (PyObject*) __target___PyUnicode_AsASCIIString(a, b);
    STATS_AFTER(_PyUnicode_AsASCIIString)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(void*) _PyUnicode_AsKind(PyObject* a, unsigned int b) {
    unimplemented("_PyUnicode_AsKind"); exit(-1);
}
STATS_CONTAINER(_PyUnicode_AsLatin1String, _PyUnicode_AsASCIIString)
PyObject* (*__target___PyUnicode_AsLatin1String)(PyObject*, const char*) = NULL;
PyAPI_FUNC(PyObject*) _PyUnicode_AsLatin1String(PyObject* a, const char* b) {
    LOG("0x%lx '%s'(0x%lx)", (unsigned long) a, b?b:"<null>", (unsigned long) b);
    STATS_BEFORE(_PyUnicode_AsLatin1String)
    PyObject* result = (PyObject*) __target___PyUnicode_AsLatin1String(a, b);
    STATS_AFTER(_PyUnicode_AsLatin1String)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyUnicode_AsUTF8String, _PyUnicode_AsLatin1String)
PyObject* (*__target___PyUnicode_AsUTF8String)(PyObject*, const char*) = NULL;
PyAPI_FUNC(PyObject*) _PyUnicode_AsUTF8String(PyObject* a, const char* b) {
    LOG("0x%lx '%s'(0x%lx)", (unsigned long) a, b?b:"<null>", (unsigned long) b);
    STATS_BEFORE(_PyUnicode_AsUTF8String)
    PyObject* result = (PyObject*) __target___PyUnicode_AsUTF8String(a, b);
    STATS_AFTER(_PyUnicode_AsUTF8String)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(const Py_UNICODE*) _PyUnicode_AsUnicode(PyObject* a) {
    unimplemented("_PyUnicode_AsUnicode"); exit(-1);
}
PyAPI_FUNC(int) _PyUnicode_CheckConsistency(PyObject* a, int b) {
    unimplemented("_PyUnicode_CheckConsistency"); exit(-1);
}
PyAPI_FUNC(PyObject*) _PyUnicode_Copy(PyObject* a) {
    unimplemented("_PyUnicode_Copy"); exit(-1);
}
PyAPI_FUNC(PyObject*) _PyUnicode_DecodeRawUnicodeEscapeStateful(const char* a, Py_ssize_t b, const char* c, Py_ssize_t* d) {
    unimplemented("_PyUnicode_DecodeRawUnicodeEscapeStateful"); exit(-1);
}
PyAPI_FUNC(PyObject*) _PyUnicode_DecodeUnicodeEscapeInternal(const char* a, Py_ssize_t b, const char* c, Py_ssize_t* d, const char** e) {
    unimplemented("_PyUnicode_DecodeUnicodeEscapeInternal"); exit(-1);
}
PyAPI_FUNC(PyObject*) _PyUnicode_DecodeUnicodeEscapeStateful(const char* a, Py_ssize_t b, const char* c, Py_ssize_t* d) {
    unimplemented("_PyUnicode_DecodeUnicodeEscapeStateful"); exit(-1);
}
PyAPI_FUNC(int) _PyUnicode_EQ(PyObject* a, PyObject* b) {
    unimplemented("_PyUnicode_EQ"); exit(-1);
}
PyAPI_FUNC(PyObject*) _PyUnicode_EncodeCharmap(PyObject* a, PyObject* b, const char* c) {
    unimplemented("_PyUnicode_EncodeCharmap"); exit(-1);
}
PyAPI_FUNC(PyObject*) _PyUnicode_EncodeUTF16(PyObject* a, const char* b, int c) {
    unimplemented("_PyUnicode_EncodeUTF16"); exit(-1);
}
PyAPI_FUNC(PyObject*) _PyUnicode_EncodeUTF32(PyObject* a, const char* b, int c) {
    unimplemented("_PyUnicode_EncodeUTF32"); exit(-1);
}
PyAPI_FUNC(PyObject*) _PyUnicode_EncodeUTF7(PyObject* a, int b, int c, const char* d) {
    unimplemented("_PyUnicode_EncodeUTF7"); exit(-1);
}
STATS_CONTAINER(_PyUnicode_EqualToASCIIId, _PyUnicode_AsUTF8String)
int (*__target___PyUnicode_EqualToASCIIId)(PyObject*, _Py_Identifier*) = NULL;
PyAPI_FUNC(int) _PyUnicode_EqualToASCIIId(PyObject* a, _Py_Identifier* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(_PyUnicode_EqualToASCIIId)
    int result = (int) __target___PyUnicode_EqualToASCIIId(a, b);
    STATS_AFTER(_PyUnicode_EqualToASCIIId)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyUnicode_EqualToASCIIString, _PyUnicode_EqualToASCIIId)
int (*__target___PyUnicode_EqualToASCIIString)(PyObject*, const char*) = NULL;
PyAPI_FUNC(int) _PyUnicode_EqualToASCIIString(PyObject* a, const char* b) {
    LOG("0x%lx '%s'(0x%lx)", (unsigned long) a, b?b:"<null>", (unsigned long) b);
    STATS_BEFORE(_PyUnicode_EqualToASCIIString)
    int result = (int) __target___PyUnicode_EqualToASCIIString(a, b);
    STATS_AFTER(_PyUnicode_EqualToASCIIString)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(void) _PyUnicode_FastCopyCharacters(PyObject* a, Py_ssize_t b, PyObject* c, Py_ssize_t d, Py_ssize_t e) {
    unimplemented("_PyUnicode_FastCopyCharacters"); exit(-1);
}
PyAPI_FUNC(void) _PyUnicode_FastFill(PyObject* a, Py_ssize_t b, Py_ssize_t c, Py_UCS4 d) {
    unimplemented("_PyUnicode_FastFill"); exit(-1);
}
PyAPI_FUNC(Py_UCS4) _PyUnicode_FindMaxChar(PyObject* a, Py_ssize_t b, Py_ssize_t c) {
    unimplemented("_PyUnicode_FindMaxChar"); exit(-1);
}
PyAPI_FUNC(int) _PyUnicode_FormatAdvancedWriter(_PyUnicodeWriter* a, PyObject* b, PyObject* c, Py_ssize_t d, Py_ssize_t e) {
    unimplemented("_PyUnicode_FormatAdvancedWriter"); exit(-1);
}
PyAPI_FUNC(PyObject*) _PyUnicode_FormatLong(PyObject* a, int b, int c, int d) {
    unimplemented("_PyUnicode_FormatLong"); exit(-1);
}
PyAPI_FUNC(PyObject*) _PyUnicode_FromASCII(const char* a, Py_ssize_t b) {
    unimplemented("_PyUnicode_FromASCII"); exit(-1);
}
STATS_CONTAINER(_PyUnicode_FromId, _PyUnicode_EqualToASCIIString)
PyObject* (*__target___PyUnicode_FromId)(_Py_Identifier*) = NULL;
PyAPI_FUNC(PyObject*) _PyUnicode_FromId(_Py_Identifier* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(_PyUnicode_FromId)
    PyObject* result = (PyObject*) __target___PyUnicode_FromId(a);
    STATS_AFTER(_PyUnicode_FromId)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(Py_ssize_t) _PyUnicode_InsertThousandsGrouping(_PyUnicodeWriter* a, Py_ssize_t b, PyObject* c, Py_ssize_t d, Py_ssize_t e, Py_ssize_t f, const char* g, PyObject* h, Py_UCS4* i) {
    unimplemented("_PyUnicode_InsertThousandsGrouping"); exit(-1);
}
STATS_CONTAINER(_PyUnicode_IsAlpha, _PyUnicode_FromId)
int (*__target___PyUnicode_IsAlpha)(Py_UCS4) = NULL;
PyAPI_FUNC(int) _PyUnicode_IsAlpha(Py_UCS4 a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(_PyUnicode_IsAlpha)
    int result = (int) __target___PyUnicode_IsAlpha(a);
    STATS_AFTER(_PyUnicode_IsAlpha)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyUnicode_IsCaseIgnorable, _PyUnicode_IsAlpha)
int (*__target___PyUnicode_IsCaseIgnorable)(Py_UCS4) = NULL;
PyAPI_FUNC(int) _PyUnicode_IsCaseIgnorable(Py_UCS4 a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(_PyUnicode_IsCaseIgnorable)
    int result = (int) __target___PyUnicode_IsCaseIgnorable(a);
    STATS_AFTER(_PyUnicode_IsCaseIgnorable)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyUnicode_IsCased, _PyUnicode_IsCaseIgnorable)
int (*__target___PyUnicode_IsCased)(Py_UCS4) = NULL;
PyAPI_FUNC(int) _PyUnicode_IsCased(Py_UCS4 a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(_PyUnicode_IsCased)
    int result = (int) __target___PyUnicode_IsCased(a);
    STATS_AFTER(_PyUnicode_IsCased)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyUnicode_IsDecimalDigit, _PyUnicode_IsCased)
int (*__target___PyUnicode_IsDecimalDigit)(Py_UCS4) = NULL;
PyAPI_FUNC(int) _PyUnicode_IsDecimalDigit(Py_UCS4 a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(_PyUnicode_IsDecimalDigit)
    int result = (int) __target___PyUnicode_IsDecimalDigit(a);
    STATS_AFTER(_PyUnicode_IsDecimalDigit)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyUnicode_IsDigit, _PyUnicode_IsDecimalDigit)
int (*__target___PyUnicode_IsDigit)(Py_UCS4) = NULL;
PyAPI_FUNC(int) _PyUnicode_IsDigit(Py_UCS4 a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(_PyUnicode_IsDigit)
    int result = (int) __target___PyUnicode_IsDigit(a);
    STATS_AFTER(_PyUnicode_IsDigit)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyUnicode_IsLinebreak, _PyUnicode_IsDigit)
int (*__target___PyUnicode_IsLinebreak)(const Py_UCS4) = NULL;
PyAPI_FUNC(int) _PyUnicode_IsLinebreak(const Py_UCS4 a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(_PyUnicode_IsLinebreak)
    int result = (int) __target___PyUnicode_IsLinebreak(a);
    STATS_AFTER(_PyUnicode_IsLinebreak)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyUnicode_IsLowercase, _PyUnicode_IsLinebreak)
int (*__target___PyUnicode_IsLowercase)(Py_UCS4) = NULL;
PyAPI_FUNC(int) _PyUnicode_IsLowercase(Py_UCS4 a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(_PyUnicode_IsLowercase)
    int result = (int) __target___PyUnicode_IsLowercase(a);
    STATS_AFTER(_PyUnicode_IsLowercase)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyUnicode_IsNumeric, _PyUnicode_IsLowercase)
int (*__target___PyUnicode_IsNumeric)(Py_UCS4) = NULL;
PyAPI_FUNC(int) _PyUnicode_IsNumeric(Py_UCS4 a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(_PyUnicode_IsNumeric)
    int result = (int) __target___PyUnicode_IsNumeric(a);
    STATS_AFTER(_PyUnicode_IsNumeric)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyUnicode_IsPrintable, _PyUnicode_IsNumeric)
int (*__target___PyUnicode_IsPrintable)(Py_UCS4) = NULL;
PyAPI_FUNC(int) _PyUnicode_IsPrintable(Py_UCS4 a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(_PyUnicode_IsPrintable)
    int result = (int) __target___PyUnicode_IsPrintable(a);
    STATS_AFTER(_PyUnicode_IsPrintable)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyUnicode_IsTitlecase, _PyUnicode_IsPrintable)
int (*__target___PyUnicode_IsTitlecase)(Py_UCS4) = NULL;
PyAPI_FUNC(int) _PyUnicode_IsTitlecase(Py_UCS4 a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(_PyUnicode_IsTitlecase)
    int result = (int) __target___PyUnicode_IsTitlecase(a);
    STATS_AFTER(_PyUnicode_IsTitlecase)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyUnicode_IsUppercase, _PyUnicode_IsTitlecase)
int (*__target___PyUnicode_IsUppercase)(Py_UCS4) = NULL;
PyAPI_FUNC(int) _PyUnicode_IsUppercase(Py_UCS4 a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(_PyUnicode_IsUppercase)
    int result = (int) __target___PyUnicode_IsUppercase(a);
    STATS_AFTER(_PyUnicode_IsUppercase)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyUnicode_IsWhitespace, _PyUnicode_IsUppercase)
int (*__target___PyUnicode_IsWhitespace)(const Py_UCS4) = NULL;
PyAPI_FUNC(int) _PyUnicode_IsWhitespace(const Py_UCS4 a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(_PyUnicode_IsWhitespace)
    int result = (int) __target___PyUnicode_IsWhitespace(a);
    STATS_AFTER(_PyUnicode_IsWhitespace)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyUnicode_IsXidContinue, _PyUnicode_IsWhitespace)
int (*__target___PyUnicode_IsXidContinue)(Py_UCS4) = NULL;
PyAPI_FUNC(int) _PyUnicode_IsXidContinue(Py_UCS4 a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(_PyUnicode_IsXidContinue)
    int result = (int) __target___PyUnicode_IsXidContinue(a);
    STATS_AFTER(_PyUnicode_IsXidContinue)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyUnicode_IsXidStart, _PyUnicode_IsXidContinue)
int (*__target___PyUnicode_IsXidStart)(Py_UCS4) = NULL;
PyAPI_FUNC(int) _PyUnicode_IsXidStart(Py_UCS4 a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(_PyUnicode_IsXidStart)
    int result = (int) __target___PyUnicode_IsXidStart(a);
    STATS_AFTER(_PyUnicode_IsXidStart)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(PyObject*) _PyUnicode_JoinArray(PyObject* a, PyObject*const* b, Py_ssize_t c) {
    unimplemented("_PyUnicode_JoinArray"); exit(-1);
}
STATS_CONTAINER(_PyUnicode_Ready, _PyUnicode_IsXidStart)
int (*__target___PyUnicode_Ready)(PyObject*) = NULL;
PyAPI_FUNC(int) _PyUnicode_Ready(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(_PyUnicode_Ready)
    int result = (int) __target___PyUnicode_Ready(a);
    STATS_AFTER(_PyUnicode_Ready)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(Py_ssize_t) _PyUnicode_ScanIdentifier(PyObject* a) {
    unimplemented("_PyUnicode_ScanIdentifier"); exit(-1);
}
STATS_CONTAINER(_PyUnicode_ToDecimalDigit, _PyUnicode_Ready)
int (*__target___PyUnicode_ToDecimalDigit)(Py_UCS4) = NULL;
PyAPI_FUNC(int) _PyUnicode_ToDecimalDigit(Py_UCS4 a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(_PyUnicode_ToDecimalDigit)
    int result = (int) __target___PyUnicode_ToDecimalDigit(a);
    STATS_AFTER(_PyUnicode_ToDecimalDigit)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyUnicode_ToDigit, _PyUnicode_ToDecimalDigit)
int (*__target___PyUnicode_ToDigit)(Py_UCS4) = NULL;
PyAPI_FUNC(int) _PyUnicode_ToDigit(Py_UCS4 a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(_PyUnicode_ToDigit)
    int result = (int) __target___PyUnicode_ToDigit(a);
    STATS_AFTER(_PyUnicode_ToDigit)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyUnicode_ToFoldedFull, _PyUnicode_ToDigit)
int (*__target___PyUnicode_ToFoldedFull)(Py_UCS4, Py_UCS4*) = NULL;
PyAPI_FUNC(int) _PyUnicode_ToFoldedFull(Py_UCS4 a, Py_UCS4* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(_PyUnicode_ToFoldedFull)
    int result = (int) __target___PyUnicode_ToFoldedFull(a, b);
    STATS_AFTER(_PyUnicode_ToFoldedFull)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyUnicode_ToLowerFull, _PyUnicode_ToFoldedFull)
int (*__target___PyUnicode_ToLowerFull)(Py_UCS4, Py_UCS4*) = NULL;
PyAPI_FUNC(int) _PyUnicode_ToLowerFull(Py_UCS4 a, Py_UCS4* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(_PyUnicode_ToLowerFull)
    int result = (int) __target___PyUnicode_ToLowerFull(a, b);
    STATS_AFTER(_PyUnicode_ToLowerFull)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyUnicode_ToLowercase, _PyUnicode_ToLowerFull)
Py_UCS4 (*__target___PyUnicode_ToLowercase)(Py_UCS4) = NULL;
PyAPI_FUNC(Py_UCS4) _PyUnicode_ToLowercase(Py_UCS4 a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(_PyUnicode_ToLowercase)
    Py_UCS4 result = (Py_UCS4) __target___PyUnicode_ToLowercase(a);
    STATS_AFTER(_PyUnicode_ToLowercase)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyUnicode_ToNumeric, _PyUnicode_ToLowercase)
double (*__target___PyUnicode_ToNumeric)(Py_UCS4) = NULL;
PyAPI_FUNC(double) _PyUnicode_ToNumeric(Py_UCS4 a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(_PyUnicode_ToNumeric)
    double result = (double) __target___PyUnicode_ToNumeric(a);
    STATS_AFTER(_PyUnicode_ToNumeric)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyUnicode_ToTitleFull, _PyUnicode_ToNumeric)
int (*__target___PyUnicode_ToTitleFull)(Py_UCS4, Py_UCS4*) = NULL;
PyAPI_FUNC(int) _PyUnicode_ToTitleFull(Py_UCS4 a, Py_UCS4* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(_PyUnicode_ToTitleFull)
    int result = (int) __target___PyUnicode_ToTitleFull(a, b);
    STATS_AFTER(_PyUnicode_ToTitleFull)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyUnicode_ToTitlecase, _PyUnicode_ToTitleFull)
Py_UCS4 (*__target___PyUnicode_ToTitlecase)(Py_UCS4) = NULL;
PyAPI_FUNC(Py_UCS4) _PyUnicode_ToTitlecase(Py_UCS4 a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(_PyUnicode_ToTitlecase)
    Py_UCS4 result = (Py_UCS4) __target___PyUnicode_ToTitlecase(a);
    STATS_AFTER(_PyUnicode_ToTitlecase)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyUnicode_ToUpperFull, _PyUnicode_ToTitlecase)
int (*__target___PyUnicode_ToUpperFull)(Py_UCS4, Py_UCS4*) = NULL;
PyAPI_FUNC(int) _PyUnicode_ToUpperFull(Py_UCS4 a, Py_UCS4* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(_PyUnicode_ToUpperFull)
    int result = (int) __target___PyUnicode_ToUpperFull(a, b);
    STATS_AFTER(_PyUnicode_ToUpperFull)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyUnicode_ToUppercase, _PyUnicode_ToUpperFull)
Py_UCS4 (*__target___PyUnicode_ToUppercase)(Py_UCS4) = NULL;
PyAPI_FUNC(Py_UCS4) _PyUnicode_ToUppercase(Py_UCS4 a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(_PyUnicode_ToUppercase)
    Py_UCS4 result = (Py_UCS4) __target___PyUnicode_ToUppercase(a);
    STATS_AFTER(_PyUnicode_ToUppercase)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(PyObject*) _PyUnicode_TransformDecimalAndSpaceToASCII(PyObject* a) {
    unimplemented("_PyUnicode_TransformDecimalAndSpaceToASCII"); exit(-1);
}
PyAPI_FUNC(int) _PyUnicode_WideCharString_Converter(PyObject* a, void* b) {
    unimplemented("_PyUnicode_WideCharString_Converter"); exit(-1);
}
PyAPI_FUNC(int) _PyUnicode_WideCharString_Opt_Converter(PyObject* a, void* b) {
    unimplemented("_PyUnicode_WideCharString_Opt_Converter"); exit(-1);
}
PyAPI_FUNC(PyObject*) _PyUnicode_XStrip(PyObject* a, int b, PyObject* c) {
    unimplemented("_PyUnicode_XStrip"); exit(-1);
}
STATS_CONTAINER(_PyUnicode_get_wstr_length, _PyUnicode_ToUppercase)
Py_ssize_t (*__target___PyUnicode_get_wstr_length)(PyObject*) = NULL;
PyAPI_FUNC(Py_ssize_t) _PyUnicode_get_wstr_length(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(_PyUnicode_get_wstr_length)
    Py_ssize_t result = (Py_ssize_t) __target___PyUnicode_get_wstr_length(a);
    STATS_AFTER(_PyUnicode_get_wstr_length)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(PyObject*) _PyWarnings_Init() {
    unimplemented("_PyWarnings_Init"); exit(-1);
}
PyAPI_FUNC(void) _PyWeakref_ClearRef(PyWeakReference* a) {
    unimplemented("_PyWeakref_ClearRef"); exit(-1);
}
PyAPI_FUNC(Py_ssize_t) _PyWeakref_GetWeakrefCount(PyWeakReference* a) {
    unimplemented("_PyWeakref_GetWeakrefCount"); exit(-1);
}
PyAPI_FUNC(void) _Py_BreakPoint() {
    unimplemented("_Py_BreakPoint"); exit(-1);
}
PyAPI_FUNC(PyObject*) _Py_CheckFunctionResult(PyThreadState* a, PyObject* b, PyObject* c, const char* d) {
    unimplemented("_Py_CheckFunctionResult"); exit(-1);
}
PyAPI_FUNC(int) _Py_CheckRecursiveCall(const char* a) {
    unimplemented("_Py_CheckRecursiveCall"); exit(-1);
}
PyAPI_FUNC(int) _Py_CoerceLegacyLocale(int a) {
    unimplemented("_Py_CoerceLegacyLocale"); exit(-1);
}
STATS_CONTAINER(_Py_Dealloc, _PyUnicode_get_wstr_length)
void (*__target___Py_Dealloc)(PyObject*) = NULL;
PyAPI_FUNC(void) _Py_Dealloc(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(_Py_Dealloc)
    __target___Py_Dealloc(a);
    STATS_AFTER(_Py_Dealloc)
    LOG_AFTER_VOID
}
STATS_CONTAINER(_Py_DecRef, _Py_Dealloc)
void (*__target___Py_DecRef)(PyObject*) = NULL;
PyAPI_FUNC(void) _Py_DecRef(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(_Py_DecRef)
    __target___Py_DecRef(a);
    STATS_AFTER(_Py_DecRef)
    LOG_AFTER_VOID
}
PyAPI_FUNC(int) _Py_DecodeLocaleEx(const char* a, wchar_t** b, size_t* c, const char** d, int e, _Py_error_handler f) {
    unimplemented("_Py_DecodeLocaleEx"); exit(-1);
}
PyAPI_FUNC(int) _Py_DisplaySourceLine(PyObject* a, PyObject* b, int c, int d) {
    unimplemented("_Py_DisplaySourceLine"); exit(-1);
}
PyAPI_FUNC(int) _Py_EncodeLocaleEx(const wchar_t* a, char** b, size_t* c, const char** d, int e, _Py_error_handler f) {
    unimplemented("_Py_EncodeLocaleEx"); exit(-1);
}
PyAPI_FUNC(char*) _Py_EncodeLocaleRaw(const wchar_t* a, size_t* b) {
    unimplemented("_Py_EncodeLocaleRaw"); exit(-1);
}
PyAPI_FUNC(void) _Py_FatalErrorFormat(const char* a, const char* b, ...) {
    unimplemented("_Py_FatalErrorFormat"); exit(-1);
}
STATS_CONTAINER(_Py_FatalErrorFunc, _Py_DecRef)
void (*__target___Py_FatalErrorFunc)(const char*, const char*) = NULL;
PyAPI_FUNC(void) _Py_FatalErrorFunc(const char* a, const char* b) {
    LOG("'%s'(0x%lx) '%s'(0x%lx)", a?a:"<null>", (unsigned long) a, b?b:"<null>", (unsigned long) b);
    STATS_BEFORE(_Py_FatalErrorFunc)
    __target___Py_FatalErrorFunc(a, b);
    STATS_AFTER(_Py_FatalErrorFunc)
    LOG_AFTER_VOID
    abort();
}
PyAPI_FUNC(int) _Py_FdIsInteractive(FILE* a, PyObject* b) {
    unimplemented("_Py_FdIsInteractive"); exit(-1);
}
PyAPI_FUNC(void) _Py_FreeCharPArray(char*const a[]) {
    unimplemented("_Py_FreeCharPArray"); exit(-1);
}
PyAPI_FUNC(Py_ssize_t) _Py_GetAllocatedBlocks() {
    unimplemented("_Py_GetAllocatedBlocks"); exit(-1);
}
PyAPI_FUNC(const PyConfig*) _Py_GetConfig() {
    unimplemented("_Py_GetConfig"); exit(-1);
}
PyAPI_FUNC(_Py_error_handler) _Py_GetErrorHandler(const char* a) {
    unimplemented("_Py_GetErrorHandler"); exit(-1);
}
STATS_CONTAINER(_Py_HashBytes, _Py_FatalErrorFunc)
Py_hash_t (*__target___Py_HashBytes)(const void*, Py_ssize_t) = NULL;
PyAPI_FUNC(Py_hash_t) _Py_HashBytes(const void* a, Py_ssize_t b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(_Py_HashBytes)
    Py_hash_t result = (Py_hash_t) __target___Py_HashBytes(a, b);
    STATS_AFTER(_Py_HashBytes)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_Py_HashDouble, _Py_HashBytes)
Py_hash_t (*__target___Py_HashDouble)(PyObject*, double) = NULL;
PyAPI_FUNC(Py_hash_t) _Py_HashDouble(PyObject* a, double b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(_Py_HashDouble)
    Py_hash_t result = (Py_hash_t) __target___Py_HashDouble(a, b);
    STATS_AFTER(_Py_HashDouble)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_Py_HashPointer, _Py_HashDouble)
Py_hash_t (*__target___Py_HashPointer)(const void*) = NULL;
PyAPI_FUNC(Py_hash_t) _Py_HashPointer(const void* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(_Py_HashPointer)
    Py_hash_t result = (Py_hash_t) __target___Py_HashPointer(a);
    STATS_AFTER(_Py_HashPointer)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_Py_HashPointerRaw, _Py_HashPointer)
Py_hash_t (*__target___Py_HashPointerRaw)(const void*) = NULL;
PyAPI_FUNC(Py_hash_t) _Py_HashPointerRaw(const void* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(_Py_HashPointerRaw)
    Py_hash_t result = (Py_hash_t) __target___Py_HashPointerRaw(a);
    STATS_AFTER(_Py_HashPointerRaw)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_Py_IncRef, _Py_HashPointerRaw)
void (*__target___Py_IncRef)(PyObject*) = NULL;
PyAPI_FUNC(void) _Py_IncRef(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(_Py_IncRef)
    __target___Py_IncRef(a);
    STATS_AFTER(_Py_IncRef)
    LOG_AFTER_VOID
}
PyAPI_FUNC(PyStatus) _Py_InitializeMain() {
    unimplemented("_Py_InitializeMain"); exit(-1);
}
PyAPI_FUNC(int) _Py_IsCoreInitialized() {
    unimplemented("_Py_IsCoreInitialized"); exit(-1);
}
PyAPI_FUNC(int) _Py_IsFinalizing() {
    unimplemented("_Py_IsFinalizing"); exit(-1);
}
PyAPI_FUNC(int) _Py_LegacyLocaleDetected(int a) {
    unimplemented("_Py_LegacyLocaleDetected"); exit(-1);
}
PyAPI_FUNC(PyObject*) _Py_Mangle(PyObject* a, PyObject* b) {
    unimplemented("_Py_Mangle"); exit(-1);
}
PyAPI_FUNC(PyThreadState*) _Py_NewInterpreter(int a) {
    unimplemented("_Py_NewInterpreter"); exit(-1);
}
STATS_CONTAINER(_Py_NewReference, _Py_IncRef)
void (*__target___Py_NewReference)(PyObject*) = NULL;
PyAPI_FUNC(void) _Py_NewReference(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(_Py_NewReference)
    __target___Py_NewReference(a);
    STATS_AFTER(_Py_NewReference)
    LOG_AFTER_VOID
}
STATS_CONTAINER(_Py_REFCNT, _Py_NewReference)
Py_ssize_t (*__target___Py_REFCNT)(const PyObject*) = NULL;
PyAPI_FUNC(Py_ssize_t) _Py_REFCNT(const PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(_Py_REFCNT)
    Py_ssize_t result = (Py_ssize_t) __target___Py_REFCNT(a);
    STATS_AFTER(_Py_REFCNT)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(void) _Py_RestoreSignals() {
    unimplemented("_Py_RestoreSignals"); exit(-1);
}
STATS_CONTAINER(_Py_SET_REFCNT, _Py_REFCNT)
Py_ssize_t (*__target___Py_SET_REFCNT)(PyObject*, Py_ssize_t) = NULL;
PyAPI_FUNC(Py_ssize_t) _Py_SET_REFCNT(PyObject* a, Py_ssize_t b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(_Py_SET_REFCNT)
    Py_ssize_t result = (Py_ssize_t) __target___Py_SET_REFCNT(a, b);
    STATS_AFTER(_Py_SET_REFCNT)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_Py_SET_SIZE, _Py_SET_REFCNT)
void (*__target___Py_SET_SIZE)(PyVarObject*, Py_ssize_t) = NULL;
PyAPI_FUNC(void) _Py_SET_SIZE(PyVarObject* a, Py_ssize_t b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(_Py_SET_SIZE)
    __target___Py_SET_SIZE(a, b);
    STATS_AFTER(_Py_SET_SIZE)
    LOG_AFTER_VOID
}
STATS_CONTAINER(_Py_SET_TYPE, _Py_SET_SIZE)
void (*__target___Py_SET_TYPE)(PyObject*, PyTypeObject*) = NULL;
PyAPI_FUNC(void) _Py_SET_TYPE(PyObject* a, PyTypeObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(_Py_SET_TYPE)
    __target___Py_SET_TYPE(a, b);
    STATS_AFTER(_Py_SET_TYPE)
    LOG_AFTER_VOID
}
STATS_CONTAINER(_Py_SIZE, _Py_SET_TYPE)
Py_ssize_t (*__target___Py_SIZE)(const PyVarObject*) = NULL;
PyAPI_FUNC(Py_ssize_t) _Py_SIZE(const PyVarObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(_Py_SIZE)
    Py_ssize_t result = (Py_ssize_t) __target___Py_SIZE(a);
    STATS_AFTER(_Py_SIZE)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(char*) _Py_SetLocaleFromEnv(int a) {
    unimplemented("_Py_SetLocaleFromEnv"); exit(-1);
}
PyAPI_FUNC(void) _Py_SetProgramFullPath(const wchar_t* a) {
    unimplemented("_Py_SetProgramFullPath"); exit(-1);
}
PyAPI_FUNC(const char*) _Py_SourceAsString(PyObject* a, const char* b, const char* c, PyCompilerFlags* d, PyObject** e) {
    unimplemented("_Py_SourceAsString"); exit(-1);
}
STATS_CONTAINER(_Py_TYPE, _Py_SIZE)
PyTypeObject* (*__target___Py_TYPE)(const PyObject*) = NULL;
PyAPI_FUNC(PyTypeObject*) _Py_TYPE(const PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    STATS_BEFORE(_Py_TYPE)
    PyTypeObject* result = (PyTypeObject*) __target___Py_TYPE(a);
    STATS_AFTER(_Py_TYPE)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(int) _Py_abspath(const wchar_t* a, wchar_t** b) {
    unimplemented("_Py_abspath"); exit(-1);
}
PyAPI_FUNC(void) _Py_add_one_to_index_C(int a, Py_ssize_t* b, const Py_ssize_t* c) {
    unimplemented("_Py_add_one_to_index_C"); exit(-1);
}
PyAPI_FUNC(void) _Py_add_one_to_index_F(int a, Py_ssize_t* b, const Py_ssize_t* c) {
    unimplemented("_Py_add_one_to_index_F"); exit(-1);
}
PyAPI_FUNC(int) _Py_convert_optional_to_ssize_t(PyObject* a, void* b) {
    unimplemented("_Py_convert_optional_to_ssize_t"); exit(-1);
}
PyAPI_FUNC(PyObject*) _Py_device_encoding(int a) {
    unimplemented("_Py_device_encoding"); exit(-1);
}
PyAPI_FUNC(char*) _Py_dg_dtoa(double a, int b, int c, int* d, int* e, char** f) {
    unimplemented("_Py_dg_dtoa"); exit(-1);
}
PyAPI_FUNC(void) _Py_dg_freedtoa(char* a) {
    unimplemented("_Py_dg_freedtoa"); exit(-1);
}
PyAPI_FUNC(double) _Py_dg_infinity(int a) {
    unimplemented("_Py_dg_infinity"); exit(-1);
}
PyAPI_FUNC(double) _Py_dg_stdnan(int a) {
    unimplemented("_Py_dg_stdnan"); exit(-1);
}
PyAPI_FUNC(double) _Py_dg_strtod(const char* a, char** b) {
    unimplemented("_Py_dg_strtod"); exit(-1);
}
PyAPI_FUNC(int) _Py_dup(int a) {
    unimplemented("_Py_dup"); exit(-1);
}
PyAPI_FUNC(FILE*) _Py_fopen(const char* a, const char* b) {
    unimplemented("_Py_fopen"); exit(-1);
}
PyAPI_FUNC(FILE*) _Py_fopen_obj(PyObject* a, const char* b) {
    unimplemented("_Py_fopen_obj"); exit(-1);
}
PyAPI_FUNC(int) _Py_fstat(int a, struct stat* b) {
    unimplemented("_Py_fstat"); exit(-1);
}
PyAPI_FUNC(int) _Py_fstat_noraise(int a, struct stat* b) {
    unimplemented("_Py_fstat_noraise"); exit(-1);
}
PyAPI_FUNC(int) _Py_get_blocking(int a) {
    unimplemented("_Py_get_blocking"); exit(-1);
}
PyAPI_FUNC(int) _Py_get_inheritable(int a) {
    unimplemented("_Py_get_inheritable"); exit(-1);
}
STATS_CONTAINER(_Py_gitidentifier, _Py_TYPE)
const char* (*__target___Py_gitidentifier)() = NULL;
PyAPI_FUNC(const char*) _Py_gitidentifier() {
    LOGS("");
    STATS_BEFORE(_Py_gitidentifier)
    const char* result = (const char*) __target___Py_gitidentifier();
    STATS_AFTER(_Py_gitidentifier)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_Py_gitversion, _Py_gitidentifier)
const char* (*__target___Py_gitversion)() = NULL;
PyAPI_FUNC(const char*) _Py_gitversion() {
    LOGS("");
    STATS_BEFORE(_Py_gitversion)
    const char* result = (const char*) __target___Py_gitversion();
    STATS_AFTER(_Py_gitversion)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(int) _Py_isabs(const wchar_t* a) {
    unimplemented("_Py_isabs"); exit(-1);
}
PyAPI_FUNC(int) _Py_open(const char* a, int b) {
    unimplemented("_Py_open"); exit(-1);
}
PyAPI_FUNC(int) _Py_open_noraise(const char* a, int b) {
    unimplemented("_Py_open_noraise"); exit(-1);
}
STATS_CONTAINER(_Py_parse_inf_or_nan, _Py_gitversion)
double (*__target___Py_parse_inf_or_nan)(const char*, char**) = NULL;
PyAPI_FUNC(double) _Py_parse_inf_or_nan(const char* a, char** b) {
    LOG("'%s'(0x%lx) 0x%lx", a?a:"<null>", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(_Py_parse_inf_or_nan)
    double result = (double) __target___Py_parse_inf_or_nan(a, b);
    STATS_AFTER(_Py_parse_inf_or_nan)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(Py_ssize_t) _Py_read(int a, void* b, size_t c) {
    unimplemented("_Py_read"); exit(-1);
}
PyAPI_FUNC(int) _Py_set_blocking(int a, int b) {
    unimplemented("_Py_set_blocking"); exit(-1);
}
PyAPI_FUNC(int) _Py_set_inheritable(int a, int b, int* c) {
    unimplemented("_Py_set_inheritable"); exit(-1);
}
PyAPI_FUNC(int) _Py_set_inheritable_async_safe(int a, int b, int* c) {
    unimplemented("_Py_set_inheritable_async_safe"); exit(-1);
}
PyAPI_FUNC(int) _Py_stat(PyObject* a, struct stat* b) {
    unimplemented("_Py_stat"); exit(-1);
}
STATS_CONTAINER(_Py_strhex, _Py_parse_inf_or_nan)
PyObject* (*__target___Py_strhex)(const char*, const Py_ssize_t) = NULL;
PyAPI_FUNC(PyObject*) _Py_strhex(const char* a, const Py_ssize_t b) {
    LOG("'%s'(0x%lx) 0x%lx", a?a:"<null>", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(_Py_strhex)
    PyObject* result = (PyObject*) __target___Py_strhex(a, b);
    STATS_AFTER(_Py_strhex)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_Py_strhex_bytes, _Py_strhex)
PyObject* (*__target___Py_strhex_bytes)(const char*, const Py_ssize_t) = NULL;
PyAPI_FUNC(PyObject*) _Py_strhex_bytes(const char* a, const Py_ssize_t b) {
    LOG("'%s'(0x%lx) 0x%lx", a?a:"<null>", (unsigned long) a, (unsigned long) b);
    STATS_BEFORE(_Py_strhex_bytes)
    PyObject* result = (PyObject*) __target___Py_strhex_bytes(a, b);
    STATS_AFTER(_Py_strhex_bytes)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_Py_strhex_bytes_with_sep, _Py_strhex_bytes)
PyObject* (*__target___Py_strhex_bytes_with_sep)(const char*, const Py_ssize_t, const PyObject*, const int) = NULL;
PyAPI_FUNC(PyObject*) _Py_strhex_bytes_with_sep(const char* a, const Py_ssize_t b, const PyObject* c, const int d) {
    LOG("'%s'(0x%lx) 0x%lx 0x%lx 0x%lx", a?a:"<null>", (unsigned long) a, (unsigned long) b, (unsigned long) c, (unsigned long) d);
    STATS_BEFORE(_Py_strhex_bytes_with_sep)
    PyObject* result = (PyObject*) __target___Py_strhex_bytes_with_sep(a, b, c, d);
    STATS_AFTER(_Py_strhex_bytes_with_sep)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_Py_strhex_with_sep, _Py_strhex_bytes_with_sep)
PyObject* (*__target___Py_strhex_with_sep)(const char*, const Py_ssize_t, const PyObject*, const int) = NULL;
PyAPI_FUNC(PyObject*) _Py_strhex_with_sep(const char* a, const Py_ssize_t b, const PyObject* c, const int d) {
    LOG("'%s'(0x%lx) 0x%lx 0x%lx 0x%lx", a?a:"<null>", (unsigned long) a, (unsigned long) b, (unsigned long) c, (unsigned long) d);
    STATS_BEFORE(_Py_strhex_with_sep)
    PyObject* result = (PyObject*) __target___Py_strhex_with_sep(a, b, c, d);
    STATS_AFTER(_Py_strhex_with_sep)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_Py_string_to_number_with_underscores, _Py_strhex_with_sep)
PyObject* (*__target___Py_string_to_number_with_underscores)(const char*, Py_ssize_t, const char*, PyObject*, void*, PyObject*(*)(const char*, Py_ssize_t, void*)) = NULL;
PyAPI_FUNC(PyObject*) _Py_string_to_number_with_underscores(const char* a, Py_ssize_t b, const char* c, PyObject* d, void* e, PyObject*(*f)(const char*, Py_ssize_t, void*)) {
    LOG("'%s'(0x%lx) 0x%lx '%s'(0x%lx) 0x%lx 0x%lx 0x%lx", a?a:"<null>", (unsigned long) a, (unsigned long) b, c?c:"<null>", (unsigned long) c, (unsigned long) d, (unsigned long) e, (unsigned long) f);
    STATS_BEFORE(_Py_string_to_number_with_underscores)
    PyObject* result = (PyObject*) __target___Py_string_to_number_with_underscores(a, b, c, d, e, f);
    STATS_AFTER(_Py_string_to_number_with_underscores)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(FILE*) _Py_wfopen(const wchar_t* a, const wchar_t* b) {
    unimplemented("_Py_wfopen"); exit(-1);
}
PyAPI_FUNC(wchar_t*) _Py_wgetcwd(wchar_t* a, size_t b) {
    unimplemented("_Py_wgetcwd"); exit(-1);
}
PyAPI_FUNC(int) _Py_wreadlink(const wchar_t* a, wchar_t* b, size_t c) {
    unimplemented("_Py_wreadlink"); exit(-1);
}
PyAPI_FUNC(wchar_t*) _Py_wrealpath(const wchar_t* a, wchar_t* b, size_t c) {
    unimplemented("_Py_wrealpath"); exit(-1);
}
PyAPI_FUNC(Py_ssize_t) _Py_write(int a, const void* b, size_t c) {
    unimplemented("_Py_write"); exit(-1);
}
PyAPI_FUNC(Py_ssize_t) _Py_write_noraise(int a, const void* b, size_t c) {
    unimplemented("_Py_write_noraise"); exit(-1);
}
PyAPI_FUNC(int) PyArg_ParseTuple(PyObject* a, const char* b, ...) {
    va_list args;
    va_start(args, b);
    int result = (int) PyArg_VaParse(a, b, args);
    va_end(args);
    return result;
}
PyAPI_FUNC(int) PyArg_ParseTupleAndKeywords(PyObject* a, PyObject* b, const char* c, char** d, ...) {
    va_list args;
    va_start(args, d);
    int result = (int) PyArg_VaParseTupleAndKeywords(a, b, c, d, args);
    va_end(args);
    return result;
}
PyAPI_FUNC(PyObject*) PyBytes_FromFormat(const char* a, ...) {
    va_list args;
    va_start(args, a);
    PyObject* result = (PyObject*) PyBytes_FromFormatV(a, args);
    va_end(args);
    return result;
}
PyAPI_FUNC(PyObject*) PyErr_Format(PyObject* a, const char* b, ...) {
    va_list args;
    va_start(args, b);
    PyObject* result = (PyObject*) PyErr_FormatV(a, b, args);
    va_end(args);
    return result;
}
PyAPI_FUNC(int) PyOS_snprintf(char* a, size_t b, const char* c, ...) {
    va_list args;
    va_start(args, c);
    int result = (int) PyOS_vsnprintf(a, b, c, args);
    va_end(args);
    return result;
}
PyAPI_FUNC(PyObject*) PyUnicode_FromFormat(const char* a, ...) {
    va_list args;
    va_start(args, a);
    PyObject* result = (PyObject*) PyUnicode_FromFormatV(a, args);
    va_end(args);
    return result;
}
PyAPI_FUNC(int) _PyArg_ParseTupleAndKeywordsFast(PyObject* a, PyObject* b, struct _PyArg_Parser* c, ...) {
    va_list args;
    va_start(args, c);
    int result = (int) _PyArg_VaParseTupleAndKeywordsFast(a, b, c, args);
    va_end(args);
    return result;
}
PyAPI_FUNC(int) _PyArg_ParseTupleAndKeywordsFast_SizeT(PyObject* a, PyObject* b, struct _PyArg_Parser* c, ...) {
    va_list args;
    va_start(args, c);
    int result = (int) _PyArg_VaParseTupleAndKeywordsFast_SizeT(a, b, c, args);
    va_end(args);
    return result;
}
PyAPI_FUNC(int) _PyArg_ParseTupleAndKeywords_SizeT(PyObject* a, PyObject* b, const char* c, char** d, ...) {
    va_list args;
    va_start(args, d);
    int result = (int) PyArg_VaParseTupleAndKeywords(a, b, c, d, args);
    va_end(args);
    return result;
}
PyAPI_FUNC(int) _PyArg_ParseTuple_SizeT(PyObject* a, const char* b, ...) {
    va_list args;
    va_start(args, b);
    int result = (int) PyArg_VaParse(a, b, args);
    va_end(args);
    return result;
}
#ifdef STATS
CAPIStats* getStatsList() { return &__stats___Py_string_to_number_with_underscores; }
#endif
void initializeCAPIForwards(void* (*getAPI)(const char*)) {
    __target__PyArg_VaParse = getAPI("PyArg_VaParse");
    __target__PyArg_VaParseTupleAndKeywords = getAPI("PyArg_VaParseTupleAndKeywords");
    __target__PyBuffer_FillInfo = getAPI("PyBuffer_FillInfo");
    __target__PyBuffer_IsContiguous = getAPI("PyBuffer_IsContiguous");
    __target__PyBuffer_Release = getAPI("PyBuffer_Release");
    __target__PyByteArray_FromStringAndSize = getAPI("PyByteArray_FromStringAndSize");
    __target__PyByteArray_Resize = getAPI("PyByteArray_Resize");
    __target__PyBytes_AsString = getAPI("PyBytes_AsString");
    __target__PyBytes_AsStringAndSize = getAPI("PyBytes_AsStringAndSize");
    __target__PyBytes_Concat = getAPI("PyBytes_Concat");
    __target__PyBytes_ConcatAndDel = getAPI("PyBytes_ConcatAndDel");
    __target__PyBytes_FromFormatV = getAPI("PyBytes_FromFormatV");
    __target__PyBytes_FromObject = getAPI("PyBytes_FromObject");
    __target__PyBytes_FromString = getAPI("PyBytes_FromString");
    __target__PyBytes_FromStringAndSize = getAPI("PyBytes_FromStringAndSize");
    __target__PyBytes_Size = getAPI("PyBytes_Size");
    __target__PyCFunction_GetClass = getAPI("PyCFunction_GetClass");
    __target__PyCFunction_GetFlags = getAPI("PyCFunction_GetFlags");
    __target__PyCFunction_GetFunction = getAPI("PyCFunction_GetFunction");
    __target__PyCFunction_GetSelf = getAPI("PyCFunction_GetSelf");
    __target__PyCFunction_New = getAPI("PyCFunction_New");
    __target__PyCFunction_NewEx = getAPI("PyCFunction_NewEx");
    __target__PyCMethod_New = getAPI("PyCMethod_New");
    __target__PyCallIter_New = getAPI("PyCallIter_New");
    __target__PyCallable_Check = getAPI("PyCallable_Check");
    __target__PyCapsule_GetContext = getAPI("PyCapsule_GetContext");
    __target__PyCapsule_GetDestructor = getAPI("PyCapsule_GetDestructor");
    __target__PyCapsule_GetName = getAPI("PyCapsule_GetName");
    __target__PyCapsule_GetPointer = getAPI("PyCapsule_GetPointer");
    __target__PyCapsule_Import = getAPI("PyCapsule_Import");
    __target__PyCapsule_IsValid = getAPI("PyCapsule_IsValid");
    __target__PyCapsule_New = getAPI("PyCapsule_New");
    __target__PyCapsule_SetContext = getAPI("PyCapsule_SetContext");
    __target__PyCapsule_SetDestructor = getAPI("PyCapsule_SetDestructor");
    __target__PyCapsule_SetName = getAPI("PyCapsule_SetName");
    __target__PyCapsule_SetPointer = getAPI("PyCapsule_SetPointer");
    __target__PyClassMethod_New = getAPI("PyClassMethod_New");
    __target__PyCode_New = getAPI("PyCode_New");
    __target__PyCode_NewEmpty = getAPI("PyCode_NewEmpty");
    __target__PyCode_NewWithPosOnlyArgs = getAPI("PyCode_NewWithPosOnlyArgs");
    __target__PyComplex_FromDoubles = getAPI("PyComplex_FromDoubles");
    __target__PyComplex_ImagAsDouble = getAPI("PyComplex_ImagAsDouble");
    __target__PyComplex_RealAsDouble = getAPI("PyComplex_RealAsDouble");
    __target__PyContextVar_Get = getAPI("PyContextVar_Get");
    __target__PyContextVar_New = getAPI("PyContextVar_New");
    __target__PyContextVar_Set = getAPI("PyContextVar_Set");
    __target__PyDescrObject_GetName = getAPI("PyDescrObject_GetName");
    __target__PyDescrObject_GetType = getAPI("PyDescrObject_GetType");
    __target__PyDescr_NewClassMethod = getAPI("PyDescr_NewClassMethod");
    __target__PyDescr_NewGetSet = getAPI("PyDescr_NewGetSet");
    __target__PyDictProxy_New = getAPI("PyDictProxy_New");
    __target__PyDict_Clear = getAPI("PyDict_Clear");
    __target__PyDict_Contains = getAPI("PyDict_Contains");
    __target__PyDict_Copy = getAPI("PyDict_Copy");
    __target__PyDict_DelItem = getAPI("PyDict_DelItem");
    __target__PyDict_GetItem = getAPI("PyDict_GetItem");
    __target__PyDict_GetItemWithError = getAPI("PyDict_GetItemWithError");
    __target__PyDict_Keys = getAPI("PyDict_Keys");
    __target__PyDict_Merge = getAPI("PyDict_Merge");
    __target__PyDict_New = getAPI("PyDict_New");
    __target__PyDict_SetDefault = getAPI("PyDict_SetDefault");
    __target__PyDict_SetItem = getAPI("PyDict_SetItem");
    __target__PyDict_Size = getAPI("PyDict_Size");
    __target__PyDict_Update = getAPI("PyDict_Update");
    __target__PyDict_Values = getAPI("PyDict_Values");
    __target__PyErr_BadArgument = getAPI("PyErr_BadArgument");
    __target__PyErr_BadInternalCall = getAPI("PyErr_BadInternalCall");
    __target__PyErr_CheckSignals = getAPI("PyErr_CheckSignals");
    __target__PyErr_Clear = getAPI("PyErr_Clear");
    __target__PyErr_Display = getAPI("PyErr_Display");
    __target__PyErr_ExceptionMatches = getAPI("PyErr_ExceptionMatches");
    __target__PyErr_Fetch = getAPI("PyErr_Fetch");
    __target__PyErr_FormatV = getAPI("PyErr_FormatV");
    __target__PyErr_GetExcInfo = getAPI("PyErr_GetExcInfo");
    __target__PyErr_GivenExceptionMatches = getAPI("PyErr_GivenExceptionMatches");
    __target__PyErr_NewException = getAPI("PyErr_NewException");
    __target__PyErr_NewExceptionWithDoc = getAPI("PyErr_NewExceptionWithDoc");
    __target__PyErr_NoMemory = getAPI("PyErr_NoMemory");
    __target__PyErr_NormalizeException = getAPI("PyErr_NormalizeException");
    __target__PyErr_Occurred = getAPI("PyErr_Occurred");
    __target__PyErr_Print = getAPI("PyErr_Print");
    __target__PyErr_PrintEx = getAPI("PyErr_PrintEx");
    __target__PyErr_Restore = getAPI("PyErr_Restore");
    __target__PyErr_SetExcInfo = getAPI("PyErr_SetExcInfo");
    __target__PyErr_SetFromErrno = getAPI("PyErr_SetFromErrno");
    __target__PyErr_SetFromErrnoWithFilename = getAPI("PyErr_SetFromErrnoWithFilename");
    __target__PyErr_SetFromErrnoWithFilenameObject = getAPI("PyErr_SetFromErrnoWithFilenameObject");
    __target__PyErr_SetFromErrnoWithFilenameObjects = getAPI("PyErr_SetFromErrnoWithFilenameObjects");
    __target__PyErr_SetNone = getAPI("PyErr_SetNone");
    __target__PyErr_SetObject = getAPI("PyErr_SetObject");
    __target__PyErr_SetString = getAPI("PyErr_SetString");
    __target__PyErr_WriteUnraisable = getAPI("PyErr_WriteUnraisable");
    __target__PyEval_CallObjectWithKeywords = getAPI("PyEval_CallObjectWithKeywords");
    __target__PyEval_EvalCode = getAPI("PyEval_EvalCode");
    __target__PyEval_EvalCodeEx = getAPI("PyEval_EvalCodeEx");
    __target__PyEval_GetBuiltins = getAPI("PyEval_GetBuiltins");
    __target__PyEval_InitThreads = getAPI("PyEval_InitThreads");
    __target__PyEval_MergeCompilerFlags = getAPI("PyEval_MergeCompilerFlags");
    __target__PyEval_RestoreThread = getAPI("PyEval_RestoreThread");
    __target__PyEval_SaveThread = getAPI("PyEval_SaveThread");
    __target__PyEval_ThreadsInitialized = getAPI("PyEval_ThreadsInitialized");
    __target__PyException_GetContext = getAPI("PyException_GetContext");
    __target__PyException_SetCause = getAPI("PyException_SetCause");
    __target__PyException_SetContext = getAPI("PyException_SetContext");
    __target__PyException_SetTraceback = getAPI("PyException_SetTraceback");
    __target__PyFile_WriteObject = getAPI("PyFile_WriteObject");
    __target__PyFile_WriteString = getAPI("PyFile_WriteString");
    __target__PyFloat_AsDouble = getAPI("PyFloat_AsDouble");
    __target__PyFloat_FromDouble = getAPI("PyFloat_FromDouble");
    __target__PyFrame_New = getAPI("PyFrame_New");
    __target__PyFrozenSet_New = getAPI("PyFrozenSet_New");
    __target__PyGILState_Ensure = getAPI("PyGILState_Ensure");
    __target__PyGILState_GetThisThreadState = getAPI("PyGILState_GetThisThreadState");
    __target__PyGILState_Release = getAPI("PyGILState_Release");
    __target__PyGen_New = getAPI("PyGen_New");
    __target__PyGen_NewWithQualName = getAPI("PyGen_NewWithQualName");
    __target__PyImport_AddModule = getAPI("PyImport_AddModule");
    __target__PyImport_AddModuleObject = getAPI("PyImport_AddModuleObject");
    __target__PyImport_GetModuleDict = getAPI("PyImport_GetModuleDict");
    __target__PyImport_Import = getAPI("PyImport_Import");
    __target__PyImport_ImportModule = getAPI("PyImport_ImportModule");
    __target__PyImport_ImportModuleLevel = getAPI("PyImport_ImportModuleLevel");
    __target__PyImport_ImportModuleLevelObject = getAPI("PyImport_ImportModuleLevelObject");
    __target__PyImport_ImportModuleNoBlock = getAPI("PyImport_ImportModuleNoBlock");
    __target__PyIndex_Check = getAPI("PyIndex_Check");
    __target__PyInstanceMethod_Function = getAPI("PyInstanceMethod_Function");
    __target__PyInstanceMethod_New = getAPI("PyInstanceMethod_New");
    __target__PyInterpreterState_GetID = getAPI("PyInterpreterState_GetID");
    __target__PyInterpreterState_GetIDFromThreadState = getAPI("PyInterpreterState_GetIDFromThreadState");
    __target__PyInterpreterState_Main = getAPI("PyInterpreterState_Main");
    __target__PyIter_Check = getAPI("PyIter_Check");
    __target__PyIter_Next = getAPI("PyIter_Next");
    __target__PyList_Append = getAPI("PyList_Append");
    __target__PyList_AsTuple = getAPI("PyList_AsTuple");
    __target__PyList_GetItem = getAPI("PyList_GetItem");
    __target__PyList_GetSlice = getAPI("PyList_GetSlice");
    __target__PyList_Insert = getAPI("PyList_Insert");
    __target__PyList_New = getAPI("PyList_New");
    __target__PyList_Reverse = getAPI("PyList_Reverse");
    __target__PyList_SetItem = getAPI("PyList_SetItem");
    __target__PyList_SetSlice = getAPI("PyList_SetSlice");
    __target__PyList_Size = getAPI("PyList_Size");
    __target__PyList_Sort = getAPI("PyList_Sort");
    __target__PyLong_AsDouble = getAPI("PyLong_AsDouble");
    __target__PyLong_AsLong = getAPI("PyLong_AsLong");
    __target__PyLong_AsLongAndOverflow = getAPI("PyLong_AsLongAndOverflow");
    __target__PyLong_AsLongLong = getAPI("PyLong_AsLongLong");
    __target__PyLong_AsLongLongAndOverflow = getAPI("PyLong_AsLongLongAndOverflow");
    __target__PyLong_AsSize_t = getAPI("PyLong_AsSize_t");
    __target__PyLong_AsSsize_t = getAPI("PyLong_AsSsize_t");
    __target__PyLong_AsUnsignedLong = getAPI("PyLong_AsUnsignedLong");
    __target__PyLong_AsUnsignedLongLong = getAPI("PyLong_AsUnsignedLongLong");
    __target__PyLong_AsUnsignedLongLongMask = getAPI("PyLong_AsUnsignedLongLongMask");
    __target__PyLong_AsUnsignedLongMask = getAPI("PyLong_AsUnsignedLongMask");
    __target__PyLong_AsVoidPtr = getAPI("PyLong_AsVoidPtr");
    __target__PyLong_FromDouble = getAPI("PyLong_FromDouble");
    __target__PyLong_FromLong = getAPI("PyLong_FromLong");
    __target__PyLong_FromLongLong = getAPI("PyLong_FromLongLong");
    __target__PyLong_FromSize_t = getAPI("PyLong_FromSize_t");
    __target__PyLong_FromSsize_t = getAPI("PyLong_FromSsize_t");
    __target__PyLong_FromString = getAPI("PyLong_FromString");
    __target__PyLong_FromUnsignedLong = getAPI("PyLong_FromUnsignedLong");
    __target__PyLong_FromUnsignedLongLong = getAPI("PyLong_FromUnsignedLongLong");
    __target__PyLong_FromVoidPtr = getAPI("PyLong_FromVoidPtr");
    __target__PyMapping_Check = getAPI("PyMapping_Check");
    __target__PyMapping_GetItemString = getAPI("PyMapping_GetItemString");
    __target__PyMapping_Items = getAPI("PyMapping_Items");
    __target__PyMapping_Keys = getAPI("PyMapping_Keys");
    __target__PyMapping_Size = getAPI("PyMapping_Size");
    __target__PyMapping_Values = getAPI("PyMapping_Values");
    __target__PyMem_Calloc = getAPI("PyMem_Calloc");
    __target__PyMem_Free = getAPI("PyMem_Free");
    __target__PyMem_Malloc = getAPI("PyMem_Malloc");
    __target__PyMem_RawCalloc = getAPI("PyMem_RawCalloc");
    __target__PyMem_RawFree = getAPI("PyMem_RawFree");
    __target__PyMem_RawMalloc = getAPI("PyMem_RawMalloc");
    __target__PyMem_RawRealloc = getAPI("PyMem_RawRealloc");
    __target__PyMem_Realloc = getAPI("PyMem_Realloc");
    __target__PyMemoryView_FromBuffer = getAPI("PyMemoryView_FromBuffer");
    __target__PyMemoryView_FromMemory = getAPI("PyMemoryView_FromMemory");
    __target__PyMemoryView_FromObject = getAPI("PyMemoryView_FromObject");
    __target__PyMemoryView_GetContiguous = getAPI("PyMemoryView_GetContiguous");
    __target__PyMethodDescrObject_GetMethod = getAPI("PyMethodDescrObject_GetMethod");
    __target__PyMethod_Function = getAPI("PyMethod_Function");
    __target__PyMethod_New = getAPI("PyMethod_New");
    __target__PyMethod_Self = getAPI("PyMethod_Self");
    __target__PyModuleDef_Init = getAPI("PyModuleDef_Init");
    __target__PyModule_AddFunctions = getAPI("PyModule_AddFunctions");
    __target__PyModule_AddIntConstant = getAPI("PyModule_AddIntConstant");
    __target__PyModule_AddObjectRef = getAPI("PyModule_AddObjectRef");
    __target__PyModule_Create2 = getAPI("PyModule_Create2");
    __target__PyModule_GetDef = getAPI("PyModule_GetDef");
    __target__PyModule_GetDict = getAPI("PyModule_GetDict");
    __target__PyModule_GetName = getAPI("PyModule_GetName");
    __target__PyModule_GetNameObject = getAPI("PyModule_GetNameObject");
    __target__PyModule_GetState = getAPI("PyModule_GetState");
    __target__PyModule_New = getAPI("PyModule_New");
    __target__PyModule_NewObject = getAPI("PyModule_NewObject");
    __target__PyModule_SetDocString = getAPI("PyModule_SetDocString");
    __target__PyNumber_Absolute = getAPI("PyNumber_Absolute");
    __target__PyNumber_Add = getAPI("PyNumber_Add");
    __target__PyNumber_And = getAPI("PyNumber_And");
    __target__PyNumber_AsSsize_t = getAPI("PyNumber_AsSsize_t");
    __target__PyNumber_Check = getAPI("PyNumber_Check");
    __target__PyNumber_Divmod = getAPI("PyNumber_Divmod");
    __target__PyNumber_Float = getAPI("PyNumber_Float");
    __target__PyNumber_FloorDivide = getAPI("PyNumber_FloorDivide");
    __target__PyNumber_InPlaceAdd = getAPI("PyNumber_InPlaceAdd");
    __target__PyNumber_InPlaceAnd = getAPI("PyNumber_InPlaceAnd");
    __target__PyNumber_InPlaceFloorDivide = getAPI("PyNumber_InPlaceFloorDivide");
    __target__PyNumber_InPlaceLshift = getAPI("PyNumber_InPlaceLshift");
    __target__PyNumber_InPlaceMatrixMultiply = getAPI("PyNumber_InPlaceMatrixMultiply");
    __target__PyNumber_InPlaceMultiply = getAPI("PyNumber_InPlaceMultiply");
    __target__PyNumber_InPlaceOr = getAPI("PyNumber_InPlaceOr");
    __target__PyNumber_InPlacePower = getAPI("PyNumber_InPlacePower");
    __target__PyNumber_InPlaceRemainder = getAPI("PyNumber_InPlaceRemainder");
    __target__PyNumber_InPlaceRshift = getAPI("PyNumber_InPlaceRshift");
    __target__PyNumber_InPlaceSubtract = getAPI("PyNumber_InPlaceSubtract");
    __target__PyNumber_InPlaceTrueDivide = getAPI("PyNumber_InPlaceTrueDivide");
    __target__PyNumber_InPlaceXor = getAPI("PyNumber_InPlaceXor");
    __target__PyNumber_Index = getAPI("PyNumber_Index");
    __target__PyNumber_Invert = getAPI("PyNumber_Invert");
    __target__PyNumber_Long = getAPI("PyNumber_Long");
    __target__PyNumber_Lshift = getAPI("PyNumber_Lshift");
    __target__PyNumber_MatrixMultiply = getAPI("PyNumber_MatrixMultiply");
    __target__PyNumber_Multiply = getAPI("PyNumber_Multiply");
    __target__PyNumber_Negative = getAPI("PyNumber_Negative");
    __target__PyNumber_Or = getAPI("PyNumber_Or");
    __target__PyNumber_Positive = getAPI("PyNumber_Positive");
    __target__PyNumber_Power = getAPI("PyNumber_Power");
    __target__PyNumber_Remainder = getAPI("PyNumber_Remainder");
    __target__PyNumber_Rshift = getAPI("PyNumber_Rshift");
    __target__PyNumber_Subtract = getAPI("PyNumber_Subtract");
    __target__PyNumber_ToBase = getAPI("PyNumber_ToBase");
    __target__PyNumber_TrueDivide = getAPI("PyNumber_TrueDivide");
    __target__PyNumber_Xor = getAPI("PyNumber_Xor");
    __target__PyOS_FSPath = getAPI("PyOS_FSPath");
    __target__PyOS_InterruptOccurred = getAPI("PyOS_InterruptOccurred");
    __target__PyOS_double_to_string = getAPI("PyOS_double_to_string");
    __target__PyOS_setsig = getAPI("PyOS_setsig");
    __target__PyOS_string_to_double = getAPI("PyOS_string_to_double");
    __target__PyOS_strtol = getAPI("PyOS_strtol");
    __target__PyOS_strtoul = getAPI("PyOS_strtoul");
    __target__PyOS_vsnprintf = getAPI("PyOS_vsnprintf");
    __target__PyObject_ASCII = getAPI("PyObject_ASCII");
    __target__PyObject_AsFileDescriptor = getAPI("PyObject_AsFileDescriptor");
    __target__PyObject_Bytes = getAPI("PyObject_Bytes");
    __target__PyObject_Call = getAPI("PyObject_Call");
    __target__PyObject_CallObject = getAPI("PyObject_CallObject");
    __target__PyObject_CheckBuffer = getAPI("PyObject_CheckBuffer");
    __target__PyObject_ClearWeakRefs = getAPI("PyObject_ClearWeakRefs");
    __target__PyObject_DelItem = getAPI("PyObject_DelItem");
    __target__PyObject_Dir = getAPI("PyObject_Dir");
    __target__PyObject_Format = getAPI("PyObject_Format");
    __target__PyObject_Free = getAPI("PyObject_Free");
    __target__PyObject_GC_Del = getAPI("PyObject_GC_Del");
    __target__PyObject_GC_Track = getAPI("PyObject_GC_Track");
    __target__PyObject_GC_UnTrack = getAPI("PyObject_GC_UnTrack");
    __target__PyObject_GenericGetAttr = getAPI("PyObject_GenericGetAttr");
    __target__PyObject_GenericSetAttr = getAPI("PyObject_GenericSetAttr");
    __target__PyObject_GenericSetDict = getAPI("PyObject_GenericSetDict");
    __target__PyObject_GetAttr = getAPI("PyObject_GetAttr");
    __target__PyObject_GetAttrString = getAPI("PyObject_GetAttrString");
    __target__PyObject_GetBuffer = getAPI("PyObject_GetBuffer");
    __target__PyObject_GetDoc = getAPI("PyObject_GetDoc");
    __target__PyObject_GetItem = getAPI("PyObject_GetItem");
    __target__PyObject_GetIter = getAPI("PyObject_GetIter");
    __target__PyObject_HasAttr = getAPI("PyObject_HasAttr");
    __target__PyObject_HasAttrString = getAPI("PyObject_HasAttrString");
    __target__PyObject_Hash = getAPI("PyObject_Hash");
    __target__PyObject_HashNotImplemented = getAPI("PyObject_HashNotImplemented");
    __target__PyObject_Init = getAPI("PyObject_Init");
    __target__PyObject_InitVar = getAPI("PyObject_InitVar");
    __target__PyObject_IsInstance = getAPI("PyObject_IsInstance");
    __target__PyObject_IsSubclass = getAPI("PyObject_IsSubclass");
    __target__PyObject_IsTrue = getAPI("PyObject_IsTrue");
    __target__PyObject_LengthHint = getAPI("PyObject_LengthHint");
    __target__PyObject_Malloc = getAPI("PyObject_Malloc");
    __target__PyObject_Not = getAPI("PyObject_Not");
    __target__PyObject_Print = getAPI("PyObject_Print");
    __target__PyObject_Realloc = getAPI("PyObject_Realloc");
    __target__PyObject_Repr = getAPI("PyObject_Repr");
    __target__PyObject_RichCompare = getAPI("PyObject_RichCompare");
    __target__PyObject_RichCompareBool = getAPI("PyObject_RichCompareBool");
    __target__PyObject_SelfIter = getAPI("PyObject_SelfIter");
    __target__PyObject_SetAttr = getAPI("PyObject_SetAttr");
    __target__PyObject_SetAttrString = getAPI("PyObject_SetAttrString");
    __target__PyObject_SetDoc = getAPI("PyObject_SetDoc");
    __target__PyObject_SetItem = getAPI("PyObject_SetItem");
    __target__PyObject_Size = getAPI("PyObject_Size");
    __target__PyObject_Str = getAPI("PyObject_Str");
    __target__PyObject_Type = getAPI("PyObject_Type");
    __target__PyObject_VectorcallDict = getAPI("PyObject_VectorcallDict");
    __target__PyRun_StringFlags = getAPI("PyRun_StringFlags");
    __target__PySeqIter_New = getAPI("PySeqIter_New");
    __target__PySequence_Check = getAPI("PySequence_Check");
    __target__PySequence_Concat = getAPI("PySequence_Concat");
    __target__PySequence_Contains = getAPI("PySequence_Contains");
    __target__PySequence_DelItem = getAPI("PySequence_DelItem");
    __target__PySequence_Fast = getAPI("PySequence_Fast");
    __target__PySequence_GetItem = getAPI("PySequence_GetItem");
    __target__PySequence_GetSlice = getAPI("PySequence_GetSlice");
    __target__PySequence_InPlaceConcat = getAPI("PySequence_InPlaceConcat");
    __target__PySequence_InPlaceRepeat = getAPI("PySequence_InPlaceRepeat");
    __target__PySequence_Length = getAPI("PySequence_Length");
    __target__PySequence_List = getAPI("PySequence_List");
    __target__PySequence_Repeat = getAPI("PySequence_Repeat");
    __target__PySequence_SetItem = getAPI("PySequence_SetItem");
    __target__PySequence_Size = getAPI("PySequence_Size");
    __target__PySequence_Tuple = getAPI("PySequence_Tuple");
    __target__PySet_Add = getAPI("PySet_Add");
    __target__PySet_Clear = getAPI("PySet_Clear");
    __target__PySet_Contains = getAPI("PySet_Contains");
    __target__PySet_Discard = getAPI("PySet_Discard");
    __target__PySet_New = getAPI("PySet_New");
    __target__PySet_Pop = getAPI("PySet_Pop");
    __target__PySet_Size = getAPI("PySet_Size");
    __target__PySlice_AdjustIndices = getAPI("PySlice_AdjustIndices");
    __target__PySlice_New = getAPI("PySlice_New");
    __target__PySlice_Start = getAPI("PySlice_Start");
    __target__PySlice_Step = getAPI("PySlice_Step");
    __target__PySlice_Stop = getAPI("PySlice_Stop");
    __target__PySlice_Unpack = getAPI("PySlice_Unpack");
    __target__PyState_AddModule = getAPI("PyState_AddModule");
    __target__PyState_FindModule = getAPI("PyState_FindModule");
    __target__PyState_RemoveModule = getAPI("PyState_RemoveModule");
    __target__PyStaticMethod_New = getAPI("PyStaticMethod_New");
    __target__PyStructSequence_GetItem = getAPI("PyStructSequence_GetItem");
    __target__PyStructSequence_InitType = getAPI("PyStructSequence_InitType");
    __target__PyStructSequence_InitType2 = getAPI("PyStructSequence_InitType2");
    __target__PyStructSequence_New = getAPI("PyStructSequence_New");
    __target__PyStructSequence_NewType = getAPI("PyStructSequence_NewType");
    __target__PyStructSequence_SetItem = getAPI("PyStructSequence_SetItem");
    __target__PySys_GetObject = getAPI("PySys_GetObject");
    __target__PyThreadState_Clear = getAPI("PyThreadState_Clear");
    __target__PyThreadState_DeleteCurrent = getAPI("PyThreadState_DeleteCurrent");
    __target__PyThreadState_Get = getAPI("PyThreadState_Get");
    __target__PyThreadState_GetDict = getAPI("PyThreadState_GetDict");
    __target__PyThread_acquire_lock = getAPI("PyThread_acquire_lock");
    __target__PyThread_allocate_lock = getAPI("PyThread_allocate_lock");
    __target__PyThread_free_lock = getAPI("PyThread_free_lock");
    __target__PyThread_release_lock = getAPI("PyThread_release_lock");
    __target__PyThread_tss_alloc = getAPI("PyThread_tss_alloc");
    __target__PyThread_tss_create = getAPI("PyThread_tss_create");
    __target__PyThread_tss_delete = getAPI("PyThread_tss_delete");
    __target__PyThread_tss_free = getAPI("PyThread_tss_free");
    __target__PyThread_tss_get = getAPI("PyThread_tss_get");
    __target__PyThread_tss_is_created = getAPI("PyThread_tss_is_created");
    __target__PyThread_tss_set = getAPI("PyThread_tss_set");
    __target__PyTraceBack_Here = getAPI("PyTraceBack_Here");
    __target__PyTraceMalloc_Track = getAPI("PyTraceMalloc_Track");
    __target__PyTraceMalloc_Untrack = getAPI("PyTraceMalloc_Untrack");
    __target__PyTruffle_Debug = getAPI("PyTruffle_Debug");
    __target__PyTruffle_DebugTrace = getAPI("PyTruffle_DebugTrace");
    __target__PyTruffle_ToNative = getAPI("PyTruffle_ToNative");
    __target__PyTuple_GetItem = getAPI("PyTuple_GetItem");
    __target__PyTuple_GetSlice = getAPI("PyTuple_GetSlice");
    __target__PyTuple_New = getAPI("PyTuple_New");
    __target__PyTuple_SetItem = getAPI("PyTuple_SetItem");
    __target__PyTuple_Size = getAPI("PyTuple_Size");
    __target__PyType_FromModuleAndSpec = getAPI("PyType_FromModuleAndSpec");
    __target__PyType_FromSpec = getAPI("PyType_FromSpec");
    __target__PyType_FromSpecWithBases = getAPI("PyType_FromSpecWithBases");
    __target__PyType_GenericAlloc = getAPI("PyType_GenericAlloc");
    __target__PyType_GenericNew = getAPI("PyType_GenericNew");
    __target__PyType_GetFlags = getAPI("PyType_GetFlags");
    __target__PyType_GetModule = getAPI("PyType_GetModule");
    __target__PyType_GetModuleState = getAPI("PyType_GetModuleState");
    __target__PyType_GetSlot = getAPI("PyType_GetSlot");
    __target__PyType_IsSubtype = getAPI("PyType_IsSubtype");
    __target__PyType_Modified = getAPI("PyType_Modified");
    __target__PyType_Ready = getAPI("PyType_Ready");
    __target__PyUnicode_Append = getAPI("PyUnicode_Append");
    __target__PyUnicode_AppendAndDel = getAPI("PyUnicode_AppendAndDel");
    __target__PyUnicode_AsASCIIString = getAPI("PyUnicode_AsASCIIString");
    __target__PyUnicode_AsEncodedString = getAPI("PyUnicode_AsEncodedString");
    __target__PyUnicode_AsLatin1String = getAPI("PyUnicode_AsLatin1String");
    __target__PyUnicode_AsUCS4 = getAPI("PyUnicode_AsUCS4");
    __target__PyUnicode_AsUCS4Copy = getAPI("PyUnicode_AsUCS4Copy");
    __target__PyUnicode_AsUTF8 = getAPI("PyUnicode_AsUTF8");
    __target__PyUnicode_AsUTF8AndSize = getAPI("PyUnicode_AsUTF8AndSize");
    __target__PyUnicode_AsUTF8String = getAPI("PyUnicode_AsUTF8String");
    __target__PyUnicode_AsUnicode = getAPI("PyUnicode_AsUnicode");
    __target__PyUnicode_AsUnicodeAndSize = getAPI("PyUnicode_AsUnicodeAndSize");
    __target__PyUnicode_AsUnicodeEscapeString = getAPI("PyUnicode_AsUnicodeEscapeString");
    __target__PyUnicode_AsWideChar = getAPI("PyUnicode_AsWideChar");
    __target__PyUnicode_Compare = getAPI("PyUnicode_Compare");
    __target__PyUnicode_Concat = getAPI("PyUnicode_Concat");
    __target__PyUnicode_Contains = getAPI("PyUnicode_Contains");
    __target__PyUnicode_Decode = getAPI("PyUnicode_Decode");
    __target__PyUnicode_DecodeASCII = getAPI("PyUnicode_DecodeASCII");
    __target__PyUnicode_DecodeFSDefault = getAPI("PyUnicode_DecodeFSDefault");
    __target__PyUnicode_DecodeFSDefaultAndSize = getAPI("PyUnicode_DecodeFSDefaultAndSize");
    __target__PyUnicode_DecodeLatin1 = getAPI("PyUnicode_DecodeLatin1");
    __target__PyUnicode_DecodeUTF32 = getAPI("PyUnicode_DecodeUTF32");
    __target__PyUnicode_DecodeUTF8 = getAPI("PyUnicode_DecodeUTF8");
    __target__PyUnicode_DecodeUTF8Stateful = getAPI("PyUnicode_DecodeUTF8Stateful");
    __target__PyUnicode_EncodeFSDefault = getAPI("PyUnicode_EncodeFSDefault");
    __target__PyUnicode_FindChar = getAPI("PyUnicode_FindChar");
    __target__PyUnicode_Format = getAPI("PyUnicode_Format");
    __target__PyUnicode_FromEncodedObject = getAPI("PyUnicode_FromEncodedObject");
    __target__PyUnicode_FromFormatV = getAPI("PyUnicode_FromFormatV");
    __target__PyUnicode_FromKindAndData = getAPI("PyUnicode_FromKindAndData");
    __target__PyUnicode_FromObject = getAPI("PyUnicode_FromObject");
    __target__PyUnicode_FromOrdinal = getAPI("PyUnicode_FromOrdinal");
    __target__PyUnicode_FromString = getAPI("PyUnicode_FromString");
    __target__PyUnicode_FromStringAndSize = getAPI("PyUnicode_FromStringAndSize");
    __target__PyUnicode_FromUnicode = getAPI("PyUnicode_FromUnicode");
    __target__PyUnicode_FromWideChar = getAPI("PyUnicode_FromWideChar");
    __target__PyUnicode_GetLength = getAPI("PyUnicode_GetLength");
    __target__PyUnicode_InternFromString = getAPI("PyUnicode_InternFromString");
    __target__PyUnicode_InternInPlace = getAPI("PyUnicode_InternInPlace");
    __target__PyUnicode_Join = getAPI("PyUnicode_Join");
    __target__PyUnicode_New = getAPI("PyUnicode_New");
    __target__PyUnicode_ReadChar = getAPI("PyUnicode_ReadChar");
    __target__PyUnicode_Replace = getAPI("PyUnicode_Replace");
    __target__PyUnicode_Split = getAPI("PyUnicode_Split");
    __target__PyUnicode_Substring = getAPI("PyUnicode_Substring");
    __target__PyUnicode_Tailmatch = getAPI("PyUnicode_Tailmatch");
    __target__PyVectorcall_Call = getAPI("PyVectorcall_Call");
    __target__PyWeakref_GetObject = getAPI("PyWeakref_GetObject");
    __target__PyWeakref_NewRef = getAPI("PyWeakref_NewRef");
    __target__Py_AtExit = getAPI("Py_AtExit");
    __target__Py_DecRef = getAPI("Py_DecRef");
    __target__Py_EnterRecursiveCall = getAPI("Py_EnterRecursiveCall");
    __target__Py_GenericAlias = getAPI("Py_GenericAlias");
    __target__Py_GetBuildInfo = getAPI("Py_GetBuildInfo");
    __target__Py_GetCompiler = getAPI("Py_GetCompiler");
    __target__Py_GetVersion = getAPI("Py_GetVersion");
    __target__Py_IncRef = getAPI("Py_IncRef");
    __target__Py_Is = getAPI("Py_Is");
    __target__Py_IsFalse = getAPI("Py_IsFalse");
    __target__Py_IsNone = getAPI("Py_IsNone");
    __target__Py_IsTrue = getAPI("Py_IsTrue");
    __target__Py_LeaveRecursiveCall = getAPI("Py_LeaveRecursiveCall");
    __target__Py_NewRef = getAPI("Py_NewRef");
    __target__Py_XNewRef = getAPI("Py_XNewRef");
    __target___PyASCIIObject_LENGTH = getAPI("_PyASCIIObject_LENGTH");
    __target___PyASCIIObject_STATE_ASCII = getAPI("_PyASCIIObject_STATE_ASCII");
    __target___PyASCIIObject_STATE_COMPACT = getAPI("_PyASCIIObject_STATE_COMPACT");
    __target___PyASCIIObject_STATE_KIND = getAPI("_PyASCIIObject_STATE_KIND");
    __target___PyASCIIObject_STATE_READY = getAPI("_PyASCIIObject_STATE_READY");
    __target___PyASCIIObject_WSTR = getAPI("_PyASCIIObject_WSTR");
    __target___PyArg_VaParseTupleAndKeywordsFast = getAPI("_PyArg_VaParseTupleAndKeywordsFast");
    __target___PyArg_VaParseTupleAndKeywordsFast_SizeT = getAPI("_PyArg_VaParseTupleAndKeywordsFast_SizeT");
    __target___PyArg_VaParseTupleAndKeywords_SizeT = getAPI("_PyArg_VaParseTupleAndKeywords_SizeT");
    __target___PyArg_VaParse_SizeT = getAPI("_PyArg_VaParse_SizeT");
    __target___PyByteArray_Start = getAPI("_PyByteArray_Start");
    __target___PyBytesWriter_Alloc = getAPI("_PyBytesWriter_Alloc");
    __target___PyBytesWriter_Dealloc = getAPI("_PyBytesWriter_Dealloc");
    __target___PyBytesWriter_Finish = getAPI("_PyBytesWriter_Finish");
    __target___PyBytesWriter_Init = getAPI("_PyBytesWriter_Init");
    __target___PyBytesWriter_Prepare = getAPI("_PyBytesWriter_Prepare");
    __target___PyBytesWriter_Resize = getAPI("_PyBytesWriter_Resize");
    __target___PyBytesWriter_WriteBytes = getAPI("_PyBytesWriter_WriteBytes");
    __target___PyBytes_Join = getAPI("_PyBytes_Join");
    __target___PyBytes_Resize = getAPI("_PyBytes_Resize");
    __target___PyDict_Pop = getAPI("_PyDict_Pop");
    __target___PyDict_SetItem_KnownHash = getAPI("_PyDict_SetItem_KnownHash");
    __target___PyErr_BadInternalCall = getAPI("_PyErr_BadInternalCall");
    __target___PyErr_CreateAndSetException = getAPI("_PyErr_CreateAndSetException");
    __target___PyErr_WriteUnraisableMsg = getAPI("_PyErr_WriteUnraisableMsg");
    __target___PyEval_SliceIndex = getAPI("_PyEval_SliceIndex");
    __target___PyFloat_Pack2 = getAPI("_PyFloat_Pack2");
    __target___PyFloat_Pack4 = getAPI("_PyFloat_Pack4");
    __target___PyFloat_Pack8 = getAPI("_PyFloat_Pack8");
    __target___PyFloat_Unpack2 = getAPI("_PyFloat_Unpack2");
    __target___PyFloat_Unpack4 = getAPI("_PyFloat_Unpack4");
    __target___PyFloat_Unpack8 = getAPI("_PyFloat_Unpack8");
    __target___PyFrame_SetLineNumber = getAPI("_PyFrame_SetLineNumber");
    __target___PyGen_FetchStopIterationValue = getAPI("_PyGen_FetchStopIterationValue");
    __target___PyGen_Finalize = getAPI("_PyGen_Finalize");
    __target___PyGen_Send = getAPI("_PyGen_Send");
    __target___PyGen_SetStopIterationValue = getAPI("_PyGen_SetStopIterationValue");
    __target___PyGen_yf = getAPI("_PyGen_yf");
    __target___PyImport_SetModule = getAPI("_PyImport_SetModule");
    __target___PyList_Extend = getAPI("_PyList_Extend");
    __target___PyLong_AsByteArray = getAPI("_PyLong_AsByteArray");
    __target___PyLong_AsInt = getAPI("_PyLong_AsInt");
    __target___PyLong_FromTime_t = getAPI("_PyLong_FromTime_t");
    __target___PyLong_Sign = getAPI("_PyLong_Sign");
    __target___PyMemoryView_GetBuffer = getAPI("_PyMemoryView_GetBuffer");
    __target___PyModule_CreateInitialized = getAPI("_PyModule_CreateInitialized");
    __target___PyModule_GetDef = getAPI("_PyModule_GetDef");
    __target___PyModule_GetDict = getAPI("_PyModule_GetDict");
    __target___PyModule_GetState = getAPI("_PyModule_GetState");
    __target___PyNamespace_New = getAPI("_PyNamespace_New");
    __target___PyNumber_Index = getAPI("_PyNumber_Index");
    __target___PyObject_Call1 = getAPI("_PyObject_Call1");
    __target___PyObject_CallMethod1 = getAPI("_PyObject_CallMethod1");
    __target___PyObject_Dump = getAPI("_PyObject_Dump");
    __target___PyObject_GC_Calloc = getAPI("_PyObject_GC_Calloc");
    __target___PyObject_GC_Malloc = getAPI("_PyObject_GC_Malloc");
    __target___PyObject_GC_New = getAPI("_PyObject_GC_New");
    __target___PyObject_GC_NewVar = getAPI("_PyObject_GC_NewVar");
    __target___PyObject_GetAttrId = getAPI("_PyObject_GetAttrId");
    __target___PyObject_LookupAttr = getAPI("_PyObject_LookupAttr");
    __target___PyObject_LookupAttrId = getAPI("_PyObject_LookupAttrId");
    __target___PyObject_MakeTpCall = getAPI("_PyObject_MakeTpCall");
    __target___PyObject_New = getAPI("_PyObject_New");
    __target___PyObject_NewVar = getAPI("_PyObject_NewVar");
    __target___PyObject_NextNotImplemented = getAPI("_PyObject_NextNotImplemented");
    __target___PyObject_SetAttrId = getAPI("_PyObject_SetAttrId");
    __target___PySequence_Fast_ITEMS = getAPI("_PySequence_Fast_ITEMS");
    __target___PySequence_ITEM = getAPI("_PySequence_ITEM");
    __target___PySet_NextEntry = getAPI("_PySet_NextEntry");
    __target___PyStack_UnpackDict = getAPI("_PyStack_UnpackDict");
    __target___PyThreadState_UncheckedGet = getAPI("_PyThreadState_UncheckedGet");
    __target___PyTraceMalloc_NewReference = getAPI("_PyTraceMalloc_NewReference");
    __target___PyTraceback_Add = getAPI("_PyTraceback_Add");
    __target___PyType_GetModuleByDef = getAPI("_PyType_GetModuleByDef");
    __target___PyType_Lookup = getAPI("_PyType_Lookup");
    __target___PyType_Name = getAPI("_PyType_Name");
    __target___PyUnicodeObject_DATA = getAPI("_PyUnicodeObject_DATA");
    __target___PyUnicode_AsASCIIString = getAPI("_PyUnicode_AsASCIIString");
    __target___PyUnicode_AsLatin1String = getAPI("_PyUnicode_AsLatin1String");
    __target___PyUnicode_AsUTF8String = getAPI("_PyUnicode_AsUTF8String");
    __target___PyUnicode_EqualToASCIIId = getAPI("_PyUnicode_EqualToASCIIId");
    __target___PyUnicode_EqualToASCIIString = getAPI("_PyUnicode_EqualToASCIIString");
    __target___PyUnicode_FromId = getAPI("_PyUnicode_FromId");
    __target___PyUnicode_IsAlpha = getAPI("_PyUnicode_IsAlpha");
    __target___PyUnicode_IsCaseIgnorable = getAPI("_PyUnicode_IsCaseIgnorable");
    __target___PyUnicode_IsCased = getAPI("_PyUnicode_IsCased");
    __target___PyUnicode_IsDecimalDigit = getAPI("_PyUnicode_IsDecimalDigit");
    __target___PyUnicode_IsDigit = getAPI("_PyUnicode_IsDigit");
    __target___PyUnicode_IsLinebreak = getAPI("_PyUnicode_IsLinebreak");
    __target___PyUnicode_IsLowercase = getAPI("_PyUnicode_IsLowercase");
    __target___PyUnicode_IsNumeric = getAPI("_PyUnicode_IsNumeric");
    __target___PyUnicode_IsPrintable = getAPI("_PyUnicode_IsPrintable");
    __target___PyUnicode_IsTitlecase = getAPI("_PyUnicode_IsTitlecase");
    __target___PyUnicode_IsUppercase = getAPI("_PyUnicode_IsUppercase");
    __target___PyUnicode_IsWhitespace = getAPI("_PyUnicode_IsWhitespace");
    __target___PyUnicode_IsXidContinue = getAPI("_PyUnicode_IsXidContinue");
    __target___PyUnicode_IsXidStart = getAPI("_PyUnicode_IsXidStart");
    __target___PyUnicode_Ready = getAPI("_PyUnicode_Ready");
    __target___PyUnicode_ToDecimalDigit = getAPI("_PyUnicode_ToDecimalDigit");
    __target___PyUnicode_ToDigit = getAPI("_PyUnicode_ToDigit");
    __target___PyUnicode_ToFoldedFull = getAPI("_PyUnicode_ToFoldedFull");
    __target___PyUnicode_ToLowerFull = getAPI("_PyUnicode_ToLowerFull");
    __target___PyUnicode_ToLowercase = getAPI("_PyUnicode_ToLowercase");
    __target___PyUnicode_ToNumeric = getAPI("_PyUnicode_ToNumeric");
    __target___PyUnicode_ToTitleFull = getAPI("_PyUnicode_ToTitleFull");
    __target___PyUnicode_ToTitlecase = getAPI("_PyUnicode_ToTitlecase");
    __target___PyUnicode_ToUpperFull = getAPI("_PyUnicode_ToUpperFull");
    __target___PyUnicode_ToUppercase = getAPI("_PyUnicode_ToUppercase");
    __target___PyUnicode_get_wstr_length = getAPI("_PyUnicode_get_wstr_length");
    __target___Py_Dealloc = getAPI("_Py_Dealloc");
    __target___Py_DecRef = getAPI("_Py_DecRef");
    __target___Py_FatalErrorFunc = getAPI("_Py_FatalErrorFunc");
    __target___Py_HashBytes = getAPI("_Py_HashBytes");
    __target___Py_HashDouble = getAPI("_Py_HashDouble");
    __target___Py_HashPointer = getAPI("_Py_HashPointer");
    __target___Py_HashPointerRaw = getAPI("_Py_HashPointerRaw");
    __target___Py_IncRef = getAPI("_Py_IncRef");
    __target___Py_NewReference = getAPI("_Py_NewReference");
    __target___Py_REFCNT = getAPI("_Py_REFCNT");
    __target___Py_SET_REFCNT = getAPI("_Py_SET_REFCNT");
    __target___Py_SET_SIZE = getAPI("_Py_SET_SIZE");
    __target___Py_SET_TYPE = getAPI("_Py_SET_TYPE");
    __target___Py_SIZE = getAPI("_Py_SIZE");
    __target___Py_TYPE = getAPI("_Py_TYPE");
    __target___Py_gitidentifier = getAPI("_Py_gitidentifier");
    __target___Py_gitversion = getAPI("_Py_gitversion");
    __target___Py_parse_inf_or_nan = getAPI("_Py_parse_inf_or_nan");
    __target___Py_strhex = getAPI("_Py_strhex");
    __target___Py_strhex_bytes = getAPI("_Py_strhex_bytes");
    __target___Py_strhex_bytes_with_sep = getAPI("_Py_strhex_bytes_with_sep");
    __target___Py_strhex_with_sep = getAPI("_Py_strhex_with_sep");
    __target___Py_string_to_number_with_underscores = getAPI("_Py_string_to_number_with_underscores");
}
// {{end CAPI_BUILTINS}}
