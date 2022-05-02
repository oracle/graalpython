// generated from class com.oracle.graal.python.builtins.objects.cext.capi.CApiFunction

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
FIRST_STATS_CONTAINER(PyAIter_Check)
int (*__target__PyAIter_Check)(PyObject*) = NULL;
PyAPI_FUNC(int) PyAIter_Check(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyAIter_Check == NULL) {
        __target__PyAIter_Check = resolveAPI("PyAIter_Check");
    }
    STATS_BEFORE(PyAIter_Check)
    int result = (int) __target__PyAIter_Check(a);
    STATS_AFTER(PyAIter_Check)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyArg_VaParse, PyAIter_Check)
int (*__target__PyArg_VaParse)(PyObject*, const char*, va_list) = NULL;
PyAPI_FUNC(int) PyArg_VaParse(PyObject* a, const char* b, va_list c) {
    LOG("0x%lx '%s'(0x%lx) 0x%lx", (unsigned long) a, b?b:"<null>", (unsigned long) b, (unsigned long) c);
    if (__target__PyArg_VaParse == NULL) {
        __target__PyArg_VaParse = resolveAPI("PyArg_VaParse");
    }
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
    if (__target__PyArg_VaParseTupleAndKeywords == NULL) {
        __target__PyArg_VaParseTupleAndKeywords = resolveAPI("PyArg_VaParseTupleAndKeywords");
    }
    STATS_BEFORE(PyArg_VaParseTupleAndKeywords)
    int result = (int) __target__PyArg_VaParseTupleAndKeywords(a, b, c, d, e);
    STATS_AFTER(PyArg_VaParseTupleAndKeywords)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyArg_ValidateKeywordArguments, PyArg_VaParseTupleAndKeywords)
int (*__target__PyArg_ValidateKeywordArguments)(PyObject*) = NULL;
PyAPI_FUNC(int) PyArg_ValidateKeywordArguments(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyArg_ValidateKeywordArguments == NULL) {
        __target__PyArg_ValidateKeywordArguments = resolveAPI("PyArg_ValidateKeywordArguments");
    }
    STATS_BEFORE(PyArg_ValidateKeywordArguments)
    int result = (int) __target__PyArg_ValidateKeywordArguments(a);
    STATS_AFTER(PyArg_ValidateKeywordArguments)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyAsyncGen_New, PyArg_ValidateKeywordArguments)
PyObject* (*__target__PyAsyncGen_New)(PyFrameObject*, PyObject*, PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyAsyncGen_New(PyFrameObject* a, PyObject* b, PyObject* c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    if (__target__PyAsyncGen_New == NULL) {
        __target__PyAsyncGen_New = resolveAPI("PyAsyncGen_New");
    }
    STATS_BEFORE(PyAsyncGen_New)
    PyObject* result = (PyObject*) __target__PyAsyncGen_New(a, b, c);
    STATS_AFTER(PyAsyncGen_New)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyBuffer_FillContiguousStrides, PyAsyncGen_New)
void (*__target__PyBuffer_FillContiguousStrides)(int, Py_ssize_t*, Py_ssize_t*, int, char) = NULL;
PyAPI_FUNC(void) PyBuffer_FillContiguousStrides(int a, Py_ssize_t* b, Py_ssize_t* c, int d, char e) {
    LOG("0x%lx 0x%lx 0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c, (unsigned long) d, (unsigned long) e);
    if (__target__PyBuffer_FillContiguousStrides == NULL) {
        __target__PyBuffer_FillContiguousStrides = resolveAPI("PyBuffer_FillContiguousStrides");
    }
    STATS_BEFORE(PyBuffer_FillContiguousStrides)
    __target__PyBuffer_FillContiguousStrides(a, b, c, d, e);
    STATS_AFTER(PyBuffer_FillContiguousStrides)
    LOG_AFTER_VOID
}
STATS_CONTAINER(PyBuffer_FillInfo, PyBuffer_FillContiguousStrides)
int (*__target__PyBuffer_FillInfo)(Py_buffer*, PyObject*, void*, Py_ssize_t, int, int) = NULL;
PyAPI_FUNC(int) PyBuffer_FillInfo(Py_buffer* a, PyObject* b, void* c, Py_ssize_t d, int e, int f) {
    LOG("0x%lx 0x%lx 0x%lx 0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c, (unsigned long) d, (unsigned long) e, (unsigned long) f);
    if (__target__PyBuffer_FillInfo == NULL) {
        __target__PyBuffer_FillInfo = resolveAPI("PyBuffer_FillInfo");
    }
    STATS_BEFORE(PyBuffer_FillInfo)
    int result = (int) __target__PyBuffer_FillInfo(a, b, c, d, e, f);
    STATS_AFTER(PyBuffer_FillInfo)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyBuffer_FromContiguous, PyBuffer_FillInfo)
int (*__target__PyBuffer_FromContiguous)(Py_buffer*, void*, Py_ssize_t, char) = NULL;
PyAPI_FUNC(int) PyBuffer_FromContiguous(Py_buffer* a, void* b, Py_ssize_t c, char d) {
    LOG("0x%lx 0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c, (unsigned long) d);
    if (__target__PyBuffer_FromContiguous == NULL) {
        __target__PyBuffer_FromContiguous = resolveAPI("PyBuffer_FromContiguous");
    }
    STATS_BEFORE(PyBuffer_FromContiguous)
    int result = (int) __target__PyBuffer_FromContiguous(a, b, c, d);
    STATS_AFTER(PyBuffer_FromContiguous)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyBuffer_GetPointer, PyBuffer_FromContiguous)
void* (*__target__PyBuffer_GetPointer)(Py_buffer*, Py_ssize_t*) = NULL;
PyAPI_FUNC(void*) PyBuffer_GetPointer(Py_buffer* a, Py_ssize_t* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target__PyBuffer_GetPointer == NULL) {
        __target__PyBuffer_GetPointer = resolveAPI("PyBuffer_GetPointer");
    }
    STATS_BEFORE(PyBuffer_GetPointer)
    void* result = (void*) __target__PyBuffer_GetPointer(a, b);
    STATS_AFTER(PyBuffer_GetPointer)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyBuffer_IsContiguous, PyBuffer_GetPointer)
int (*__target__PyBuffer_IsContiguous)(const Py_buffer*, char) = NULL;
PyAPI_FUNC(int) PyBuffer_IsContiguous(const Py_buffer* a, char b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target__PyBuffer_IsContiguous == NULL) {
        __target__PyBuffer_IsContiguous = resolveAPI("PyBuffer_IsContiguous");
    }
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
    if (__target__PyBuffer_Release == NULL) {
        __target__PyBuffer_Release = resolveAPI("PyBuffer_Release");
    }
    STATS_BEFORE(PyBuffer_Release)
    __target__PyBuffer_Release(a);
    STATS_AFTER(PyBuffer_Release)
    LOG_AFTER_VOID
}
STATS_CONTAINER(PyBuffer_SizeFromFormat, PyBuffer_Release)
Py_ssize_t (*__target__PyBuffer_SizeFromFormat)(const char*) = NULL;
PyAPI_FUNC(Py_ssize_t) PyBuffer_SizeFromFormat(const char* a) {
    LOG("'%s'(0x%lx)", a?a:"<null>", (unsigned long) a);
    if (__target__PyBuffer_SizeFromFormat == NULL) {
        __target__PyBuffer_SizeFromFormat = resolveAPI("PyBuffer_SizeFromFormat");
    }
    STATS_BEFORE(PyBuffer_SizeFromFormat)
    Py_ssize_t result = (Py_ssize_t) __target__PyBuffer_SizeFromFormat(a);
    STATS_AFTER(PyBuffer_SizeFromFormat)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyBuffer_ToContiguous, PyBuffer_SizeFromFormat)
int (*__target__PyBuffer_ToContiguous)(void*, Py_buffer*, Py_ssize_t, char) = NULL;
PyAPI_FUNC(int) PyBuffer_ToContiguous(void* a, Py_buffer* b, Py_ssize_t c, char d) {
    LOG("0x%lx 0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c, (unsigned long) d);
    if (__target__PyBuffer_ToContiguous == NULL) {
        __target__PyBuffer_ToContiguous = resolveAPI("PyBuffer_ToContiguous");
    }
    STATS_BEFORE(PyBuffer_ToContiguous)
    int result = (int) __target__PyBuffer_ToContiguous(a, b, c, d);
    STATS_AFTER(PyBuffer_ToContiguous)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyByteArray_AsString, PyBuffer_ToContiguous)
char* (*__target__PyByteArray_AsString)(PyObject*) = NULL;
PyAPI_FUNC(char*) PyByteArray_AsString(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyByteArray_AsString == NULL) {
        __target__PyByteArray_AsString = resolveAPI("PyByteArray_AsString");
    }
    STATS_BEFORE(PyByteArray_AsString)
    char* result = (char*) __target__PyByteArray_AsString(a);
    STATS_AFTER(PyByteArray_AsString)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyByteArray_Concat, PyByteArray_AsString)
PyObject* (*__target__PyByteArray_Concat)(PyObject*, PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyByteArray_Concat(PyObject* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target__PyByteArray_Concat == NULL) {
        __target__PyByteArray_Concat = resolveAPI("PyByteArray_Concat");
    }
    STATS_BEFORE(PyByteArray_Concat)
    PyObject* result = (PyObject*) __target__PyByteArray_Concat(a, b);
    STATS_AFTER(PyByteArray_Concat)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyByteArray_FromObject, PyByteArray_Concat)
PyObject* (*__target__PyByteArray_FromObject)(PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyByteArray_FromObject(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyByteArray_FromObject == NULL) {
        __target__PyByteArray_FromObject = resolveAPI("PyByteArray_FromObject");
    }
    STATS_BEFORE(PyByteArray_FromObject)
    PyObject* result = (PyObject*) __target__PyByteArray_FromObject(a);
    STATS_AFTER(PyByteArray_FromObject)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyByteArray_FromStringAndSize, PyByteArray_FromObject)
PyObject* (*__target__PyByteArray_FromStringAndSize)(const char*, Py_ssize_t) = NULL;
PyAPI_FUNC(PyObject*) PyByteArray_FromStringAndSize(const char* a, Py_ssize_t b) {
    LOG("'%s'(0x%lx) 0x%lx", a?a:"<null>", (unsigned long) a, (unsigned long) b);
    if (__target__PyByteArray_FromStringAndSize == NULL) {
        __target__PyByteArray_FromStringAndSize = resolveAPI("PyByteArray_FromStringAndSize");
    }
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
    if (__target__PyByteArray_Resize == NULL) {
        __target__PyByteArray_Resize = resolveAPI("PyByteArray_Resize");
    }
    STATS_BEFORE(PyByteArray_Resize)
    int result = (int) __target__PyByteArray_Resize(a, b);
    STATS_AFTER(PyByteArray_Resize)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyByteArray_Size, PyByteArray_Resize)
Py_ssize_t (*__target__PyByteArray_Size)(PyObject*) = NULL;
PyAPI_FUNC(Py_ssize_t) PyByteArray_Size(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyByteArray_Size == NULL) {
        __target__PyByteArray_Size = resolveAPI("PyByteArray_Size");
    }
    STATS_BEFORE(PyByteArray_Size)
    Py_ssize_t result = (Py_ssize_t) __target__PyByteArray_Size(a);
    STATS_AFTER(PyByteArray_Size)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyBytes_AsString, PyByteArray_Size)
char* (*__target__PyBytes_AsString)(PyObject*) = NULL;
PyAPI_FUNC(char*) PyBytes_AsString(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyBytes_AsString == NULL) {
        __target__PyBytes_AsString = resolveAPI("PyBytes_AsString");
    }
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
    if (__target__PyBytes_AsStringAndSize == NULL) {
        __target__PyBytes_AsStringAndSize = resolveAPI("PyBytes_AsStringAndSize");
    }
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
    if (__target__PyBytes_Concat == NULL) {
        __target__PyBytes_Concat = resolveAPI("PyBytes_Concat");
    }
    STATS_BEFORE(PyBytes_Concat)
    __target__PyBytes_Concat(a, b);
    STATS_AFTER(PyBytes_Concat)
    LOG_AFTER_VOID
}
STATS_CONTAINER(PyBytes_ConcatAndDel, PyBytes_Concat)
void (*__target__PyBytes_ConcatAndDel)(PyObject**, PyObject*) = NULL;
PyAPI_FUNC(void) PyBytes_ConcatAndDel(PyObject** a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target__PyBytes_ConcatAndDel == NULL) {
        __target__PyBytes_ConcatAndDel = resolveAPI("PyBytes_ConcatAndDel");
    }
    STATS_BEFORE(PyBytes_ConcatAndDel)
    __target__PyBytes_ConcatAndDel(a, b);
    STATS_AFTER(PyBytes_ConcatAndDel)
    LOG_AFTER_VOID
}
STATS_CONTAINER(PyBytes_DecodeEscape, PyBytes_ConcatAndDel)
PyObject* (*__target__PyBytes_DecodeEscape)(const char*, Py_ssize_t, const char*, Py_ssize_t, const char*) = NULL;
PyAPI_FUNC(PyObject*) PyBytes_DecodeEscape(const char* a, Py_ssize_t b, const char* c, Py_ssize_t d, const char* e) {
    LOG("'%s'(0x%lx) 0x%lx '%s'(0x%lx) 0x%lx '%s'(0x%lx)", a?a:"<null>", (unsigned long) a, (unsigned long) b, c?c:"<null>", (unsigned long) c, (unsigned long) d, e?e:"<null>", (unsigned long) e);
    if (__target__PyBytes_DecodeEscape == NULL) {
        __target__PyBytes_DecodeEscape = resolveAPI("PyBytes_DecodeEscape");
    }
    STATS_BEFORE(PyBytes_DecodeEscape)
    PyObject* result = (PyObject*) __target__PyBytes_DecodeEscape(a, b, c, d, e);
    STATS_AFTER(PyBytes_DecodeEscape)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyBytes_FromFormatV, PyBytes_DecodeEscape)
PyObject* (*__target__PyBytes_FromFormatV)(const char*, va_list) = NULL;
PyAPI_FUNC(PyObject*) PyBytes_FromFormatV(const char* a, va_list b) {
    LOG("'%s'(0x%lx) 0x%lx", a?a:"<null>", (unsigned long) a, (unsigned long) b);
    if (__target__PyBytes_FromFormatV == NULL) {
        __target__PyBytes_FromFormatV = resolveAPI("PyBytes_FromFormatV");
    }
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
    if (__target__PyBytes_FromObject == NULL) {
        __target__PyBytes_FromObject = resolveAPI("PyBytes_FromObject");
    }
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
    if (__target__PyBytes_FromString == NULL) {
        __target__PyBytes_FromString = resolveAPI("PyBytes_FromString");
    }
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
    if (__target__PyBytes_FromStringAndSize == NULL) {
        __target__PyBytes_FromStringAndSize = resolveAPI("PyBytes_FromStringAndSize");
    }
    STATS_BEFORE(PyBytes_FromStringAndSize)
    PyObject* result = (PyObject*) __target__PyBytes_FromStringAndSize(a, b);
    STATS_AFTER(PyBytes_FromStringAndSize)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyBytes_Repr, PyBytes_FromStringAndSize)
PyObject* (*__target__PyBytes_Repr)(PyObject*, int) = NULL;
PyAPI_FUNC(PyObject*) PyBytes_Repr(PyObject* a, int b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target__PyBytes_Repr == NULL) {
        __target__PyBytes_Repr = resolveAPI("PyBytes_Repr");
    }
    STATS_BEFORE(PyBytes_Repr)
    PyObject* result = (PyObject*) __target__PyBytes_Repr(a, b);
    STATS_AFTER(PyBytes_Repr)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyBytes_Size, PyBytes_Repr)
Py_ssize_t (*__target__PyBytes_Size)(PyObject*) = NULL;
PyAPI_FUNC(Py_ssize_t) PyBytes_Size(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyBytes_Size == NULL) {
        __target__PyBytes_Size = resolveAPI("PyBytes_Size");
    }
    STATS_BEFORE(PyBytes_Size)
    Py_ssize_t result = (Py_ssize_t) __target__PyBytes_Size(a);
    STATS_AFTER(PyBytes_Size)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyCFunction_Call, PyBytes_Size)
PyObject* (*__target__PyCFunction_Call)(PyObject*, PyObject*, PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyCFunction_Call(PyObject* a, PyObject* b, PyObject* c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    if (__target__PyCFunction_Call == NULL) {
        __target__PyCFunction_Call = resolveAPI("PyCFunction_Call");
    }
    STATS_BEFORE(PyCFunction_Call)
    PyObject* result = (PyObject*) __target__PyCFunction_Call(a, b, c);
    STATS_AFTER(PyCFunction_Call)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyCFunction_GetClass, PyCFunction_Call)
PyTypeObject* (*__target__PyCFunction_GetClass)(PyObject*) = NULL;
PyAPI_FUNC(PyTypeObject*) PyCFunction_GetClass(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyCFunction_GetClass == NULL) {
        __target__PyCFunction_GetClass = resolveAPI("PyCFunction_GetClass");
    }
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
    if (__target__PyCFunction_GetFlags == NULL) {
        __target__PyCFunction_GetFlags = resolveAPI("PyCFunction_GetFlags");
    }
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
    if (__target__PyCFunction_GetFunction == NULL) {
        __target__PyCFunction_GetFunction = resolveAPI("PyCFunction_GetFunction");
    }
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
    if (__target__PyCFunction_GetSelf == NULL) {
        __target__PyCFunction_GetSelf = resolveAPI("PyCFunction_GetSelf");
    }
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
    if (__target__PyCFunction_New == NULL) {
        __target__PyCFunction_New = resolveAPI("PyCFunction_New");
    }
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
    if (__target__PyCFunction_NewEx == NULL) {
        __target__PyCFunction_NewEx = resolveAPI("PyCFunction_NewEx");
    }
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
    if (__target__PyCMethod_New == NULL) {
        __target__PyCMethod_New = resolveAPI("PyCMethod_New");
    }
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
    if (__target__PyCallIter_New == NULL) {
        __target__PyCallIter_New = resolveAPI("PyCallIter_New");
    }
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
    if (__target__PyCallable_Check == NULL) {
        __target__PyCallable_Check = resolveAPI("PyCallable_Check");
    }
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
    if (__target__PyCapsule_GetContext == NULL) {
        __target__PyCapsule_GetContext = resolveAPI("PyCapsule_GetContext");
    }
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
    if (__target__PyCapsule_GetDestructor == NULL) {
        __target__PyCapsule_GetDestructor = resolveAPI("PyCapsule_GetDestructor");
    }
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
    if (__target__PyCapsule_GetName == NULL) {
        __target__PyCapsule_GetName = resolveAPI("PyCapsule_GetName");
    }
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
    if (__target__PyCapsule_GetPointer == NULL) {
        __target__PyCapsule_GetPointer = resolveAPI("PyCapsule_GetPointer");
    }
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
    if (__target__PyCapsule_Import == NULL) {
        __target__PyCapsule_Import = resolveAPI("PyCapsule_Import");
    }
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
    if (__target__PyCapsule_IsValid == NULL) {
        __target__PyCapsule_IsValid = resolveAPI("PyCapsule_IsValid");
    }
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
    if (__target__PyCapsule_New == NULL) {
        __target__PyCapsule_New = resolveAPI("PyCapsule_New");
    }
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
    if (__target__PyCapsule_SetContext == NULL) {
        __target__PyCapsule_SetContext = resolveAPI("PyCapsule_SetContext");
    }
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
    if (__target__PyCapsule_SetDestructor == NULL) {
        __target__PyCapsule_SetDestructor = resolveAPI("PyCapsule_SetDestructor");
    }
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
    if (__target__PyCapsule_SetName == NULL) {
        __target__PyCapsule_SetName = resolveAPI("PyCapsule_SetName");
    }
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
    if (__target__PyCapsule_SetPointer == NULL) {
        __target__PyCapsule_SetPointer = resolveAPI("PyCapsule_SetPointer");
    }
    STATS_BEFORE(PyCapsule_SetPointer)
    int result = (int) __target__PyCapsule_SetPointer(a, b);
    STATS_AFTER(PyCapsule_SetPointer)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyCell_Get, PyCapsule_SetPointer)
PyObject* (*__target__PyCell_Get)(PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyCell_Get(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyCell_Get == NULL) {
        __target__PyCell_Get = resolveAPI("PyCell_Get");
    }
    STATS_BEFORE(PyCell_Get)
    PyObject* result = (PyObject*) __target__PyCell_Get(a);
    STATS_AFTER(PyCell_Get)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyCell_New, PyCell_Get)
PyObject* (*__target__PyCell_New)(PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyCell_New(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyCell_New == NULL) {
        __target__PyCell_New = resolveAPI("PyCell_New");
    }
    STATS_BEFORE(PyCell_New)
    PyObject* result = (PyObject*) __target__PyCell_New(a);
    STATS_AFTER(PyCell_New)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyCell_Set, PyCell_New)
int (*__target__PyCell_Set)(PyObject*, PyObject*) = NULL;
PyAPI_FUNC(int) PyCell_Set(PyObject* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target__PyCell_Set == NULL) {
        __target__PyCell_Set = resolveAPI("PyCell_Set");
    }
    STATS_BEFORE(PyCell_Set)
    int result = (int) __target__PyCell_Set(a, b);
    STATS_AFTER(PyCell_Set)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyClassMethod_New, PyCell_Set)
PyObject* (*__target__PyClassMethod_New)(PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyClassMethod_New(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyClassMethod_New == NULL) {
        __target__PyClassMethod_New = resolveAPI("PyClassMethod_New");
    }
    STATS_BEFORE(PyClassMethod_New)
    PyObject* result = (PyObject*) __target__PyClassMethod_New(a);
    STATS_AFTER(PyClassMethod_New)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyCode_Addr2Line, PyClassMethod_New)
int (*__target__PyCode_Addr2Line)(PyCodeObject*, int) = NULL;
PyAPI_FUNC(int) PyCode_Addr2Line(PyCodeObject* a, int b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target__PyCode_Addr2Line == NULL) {
        __target__PyCode_Addr2Line = resolveAPI("PyCode_Addr2Line");
    }
    STATS_BEFORE(PyCode_Addr2Line)
    int result = (int) __target__PyCode_Addr2Line(a, b);
    STATS_AFTER(PyCode_Addr2Line)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyCode_New, PyCode_Addr2Line)
PyCodeObject* (*__target__PyCode_New)(int, int, int, int, int, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, int, PyObject*) = NULL;
PyAPI_FUNC(PyCodeObject*) PyCode_New(int a, int b, int c, int d, int e, PyObject* f, PyObject* g, PyObject* h, PyObject* i, PyObject* j, PyObject* k, PyObject* l, PyObject* m, int n, PyObject* o) {
    LOG("0x%lx 0x%lx 0x%lx 0x%lx 0x%lx 0x%lx 0x%lx 0x%lx 0x%lx 0x%lx 0x%lx 0x%lx 0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c, (unsigned long) d, (unsigned long) e, (unsigned long) f, (unsigned long) g, (unsigned long) h, (unsigned long) i, (unsigned long) j, (unsigned long) k, (unsigned long) l, (unsigned long) m, (unsigned long) n, (unsigned long) o);
    if (__target__PyCode_New == NULL) {
        __target__PyCode_New = resolveAPI("PyCode_New");
    }
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
    if (__target__PyCode_NewEmpty == NULL) {
        __target__PyCode_NewEmpty = resolveAPI("PyCode_NewEmpty");
    }
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
    if (__target__PyCode_NewWithPosOnlyArgs == NULL) {
        __target__PyCode_NewWithPosOnlyArgs = resolveAPI("PyCode_NewWithPosOnlyArgs");
    }
    STATS_BEFORE(PyCode_NewWithPosOnlyArgs)
    PyCodeObject* result = (PyCodeObject*) __target__PyCode_NewWithPosOnlyArgs(a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p);
    STATS_AFTER(PyCode_NewWithPosOnlyArgs)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyCode_Optimize, PyCode_NewWithPosOnlyArgs)
PyObject* (*__target__PyCode_Optimize)(PyObject*, PyObject*, PyObject*, PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyCode_Optimize(PyObject* a, PyObject* b, PyObject* c, PyObject* d) {
    LOG("0x%lx 0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c, (unsigned long) d);
    if (__target__PyCode_Optimize == NULL) {
        __target__PyCode_Optimize = resolveAPI("PyCode_Optimize");
    }
    STATS_BEFORE(PyCode_Optimize)
    PyObject* result = (PyObject*) __target__PyCode_Optimize(a, b, c, d);
    STATS_AFTER(PyCode_Optimize)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyCodec_BackslashReplaceErrors, PyCode_Optimize)
PyObject* (*__target__PyCodec_BackslashReplaceErrors)(PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyCodec_BackslashReplaceErrors(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyCodec_BackslashReplaceErrors == NULL) {
        __target__PyCodec_BackslashReplaceErrors = resolveAPI("PyCodec_BackslashReplaceErrors");
    }
    STATS_BEFORE(PyCodec_BackslashReplaceErrors)
    PyObject* result = (PyObject*) __target__PyCodec_BackslashReplaceErrors(a);
    STATS_AFTER(PyCodec_BackslashReplaceErrors)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyCodec_Decode, PyCodec_BackslashReplaceErrors)
PyObject* (*__target__PyCodec_Decode)(PyObject*, const char*, const char*) = NULL;
PyAPI_FUNC(PyObject*) PyCodec_Decode(PyObject* a, const char* b, const char* c) {
    LOG("0x%lx '%s'(0x%lx) '%s'(0x%lx)", (unsigned long) a, b?b:"<null>", (unsigned long) b, c?c:"<null>", (unsigned long) c);
    if (__target__PyCodec_Decode == NULL) {
        __target__PyCodec_Decode = resolveAPI("PyCodec_Decode");
    }
    STATS_BEFORE(PyCodec_Decode)
    PyObject* result = (PyObject*) __target__PyCodec_Decode(a, b, c);
    STATS_AFTER(PyCodec_Decode)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyCodec_Decoder, PyCodec_Decode)
PyObject* (*__target__PyCodec_Decoder)(const char*) = NULL;
PyAPI_FUNC(PyObject*) PyCodec_Decoder(const char* a) {
    LOG("'%s'(0x%lx)", a?a:"<null>", (unsigned long) a);
    if (__target__PyCodec_Decoder == NULL) {
        __target__PyCodec_Decoder = resolveAPI("PyCodec_Decoder");
    }
    STATS_BEFORE(PyCodec_Decoder)
    PyObject* result = (PyObject*) __target__PyCodec_Decoder(a);
    STATS_AFTER(PyCodec_Decoder)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyCodec_Encode, PyCodec_Decoder)
PyObject* (*__target__PyCodec_Encode)(PyObject*, const char*, const char*) = NULL;
PyAPI_FUNC(PyObject*) PyCodec_Encode(PyObject* a, const char* b, const char* c) {
    LOG("0x%lx '%s'(0x%lx) '%s'(0x%lx)", (unsigned long) a, b?b:"<null>", (unsigned long) b, c?c:"<null>", (unsigned long) c);
    if (__target__PyCodec_Encode == NULL) {
        __target__PyCodec_Encode = resolveAPI("PyCodec_Encode");
    }
    STATS_BEFORE(PyCodec_Encode)
    PyObject* result = (PyObject*) __target__PyCodec_Encode(a, b, c);
    STATS_AFTER(PyCodec_Encode)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyCodec_Encoder, PyCodec_Encode)
PyObject* (*__target__PyCodec_Encoder)(const char*) = NULL;
PyAPI_FUNC(PyObject*) PyCodec_Encoder(const char* a) {
    LOG("'%s'(0x%lx)", a?a:"<null>", (unsigned long) a);
    if (__target__PyCodec_Encoder == NULL) {
        __target__PyCodec_Encoder = resolveAPI("PyCodec_Encoder");
    }
    STATS_BEFORE(PyCodec_Encoder)
    PyObject* result = (PyObject*) __target__PyCodec_Encoder(a);
    STATS_AFTER(PyCodec_Encoder)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyCodec_IgnoreErrors, PyCodec_Encoder)
PyObject* (*__target__PyCodec_IgnoreErrors)(PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyCodec_IgnoreErrors(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyCodec_IgnoreErrors == NULL) {
        __target__PyCodec_IgnoreErrors = resolveAPI("PyCodec_IgnoreErrors");
    }
    STATS_BEFORE(PyCodec_IgnoreErrors)
    PyObject* result = (PyObject*) __target__PyCodec_IgnoreErrors(a);
    STATS_AFTER(PyCodec_IgnoreErrors)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyCodec_IncrementalDecoder, PyCodec_IgnoreErrors)
PyObject* (*__target__PyCodec_IncrementalDecoder)(const char*, const char*) = NULL;
PyAPI_FUNC(PyObject*) PyCodec_IncrementalDecoder(const char* a, const char* b) {
    LOG("'%s'(0x%lx) '%s'(0x%lx)", a?a:"<null>", (unsigned long) a, b?b:"<null>", (unsigned long) b);
    if (__target__PyCodec_IncrementalDecoder == NULL) {
        __target__PyCodec_IncrementalDecoder = resolveAPI("PyCodec_IncrementalDecoder");
    }
    STATS_BEFORE(PyCodec_IncrementalDecoder)
    PyObject* result = (PyObject*) __target__PyCodec_IncrementalDecoder(a, b);
    STATS_AFTER(PyCodec_IncrementalDecoder)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyCodec_IncrementalEncoder, PyCodec_IncrementalDecoder)
PyObject* (*__target__PyCodec_IncrementalEncoder)(const char*, const char*) = NULL;
PyAPI_FUNC(PyObject*) PyCodec_IncrementalEncoder(const char* a, const char* b) {
    LOG("'%s'(0x%lx) '%s'(0x%lx)", a?a:"<null>", (unsigned long) a, b?b:"<null>", (unsigned long) b);
    if (__target__PyCodec_IncrementalEncoder == NULL) {
        __target__PyCodec_IncrementalEncoder = resolveAPI("PyCodec_IncrementalEncoder");
    }
    STATS_BEFORE(PyCodec_IncrementalEncoder)
    PyObject* result = (PyObject*) __target__PyCodec_IncrementalEncoder(a, b);
    STATS_AFTER(PyCodec_IncrementalEncoder)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyCodec_KnownEncoding, PyCodec_IncrementalEncoder)
int (*__target__PyCodec_KnownEncoding)(const char*) = NULL;
PyAPI_FUNC(int) PyCodec_KnownEncoding(const char* a) {
    LOG("'%s'(0x%lx)", a?a:"<null>", (unsigned long) a);
    if (__target__PyCodec_KnownEncoding == NULL) {
        __target__PyCodec_KnownEncoding = resolveAPI("PyCodec_KnownEncoding");
    }
    STATS_BEFORE(PyCodec_KnownEncoding)
    int result = (int) __target__PyCodec_KnownEncoding(a);
    STATS_AFTER(PyCodec_KnownEncoding)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyCodec_LookupError, PyCodec_KnownEncoding)
PyObject* (*__target__PyCodec_LookupError)(const char*) = NULL;
PyAPI_FUNC(PyObject*) PyCodec_LookupError(const char* a) {
    LOG("'%s'(0x%lx)", a?a:"<null>", (unsigned long) a);
    if (__target__PyCodec_LookupError == NULL) {
        __target__PyCodec_LookupError = resolveAPI("PyCodec_LookupError");
    }
    STATS_BEFORE(PyCodec_LookupError)
    PyObject* result = (PyObject*) __target__PyCodec_LookupError(a);
    STATS_AFTER(PyCodec_LookupError)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyCodec_NameReplaceErrors, PyCodec_LookupError)
PyObject* (*__target__PyCodec_NameReplaceErrors)(PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyCodec_NameReplaceErrors(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyCodec_NameReplaceErrors == NULL) {
        __target__PyCodec_NameReplaceErrors = resolveAPI("PyCodec_NameReplaceErrors");
    }
    STATS_BEFORE(PyCodec_NameReplaceErrors)
    PyObject* result = (PyObject*) __target__PyCodec_NameReplaceErrors(a);
    STATS_AFTER(PyCodec_NameReplaceErrors)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyCodec_Register, PyCodec_NameReplaceErrors)
int (*__target__PyCodec_Register)(PyObject*) = NULL;
PyAPI_FUNC(int) PyCodec_Register(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyCodec_Register == NULL) {
        __target__PyCodec_Register = resolveAPI("PyCodec_Register");
    }
    STATS_BEFORE(PyCodec_Register)
    int result = (int) __target__PyCodec_Register(a);
    STATS_AFTER(PyCodec_Register)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyCodec_RegisterError, PyCodec_Register)
int (*__target__PyCodec_RegisterError)(const char*, PyObject*) = NULL;
PyAPI_FUNC(int) PyCodec_RegisterError(const char* a, PyObject* b) {
    LOG("'%s'(0x%lx) 0x%lx", a?a:"<null>", (unsigned long) a, (unsigned long) b);
    if (__target__PyCodec_RegisterError == NULL) {
        __target__PyCodec_RegisterError = resolveAPI("PyCodec_RegisterError");
    }
    STATS_BEFORE(PyCodec_RegisterError)
    int result = (int) __target__PyCodec_RegisterError(a, b);
    STATS_AFTER(PyCodec_RegisterError)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyCodec_ReplaceErrors, PyCodec_RegisterError)
PyObject* (*__target__PyCodec_ReplaceErrors)(PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyCodec_ReplaceErrors(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyCodec_ReplaceErrors == NULL) {
        __target__PyCodec_ReplaceErrors = resolveAPI("PyCodec_ReplaceErrors");
    }
    STATS_BEFORE(PyCodec_ReplaceErrors)
    PyObject* result = (PyObject*) __target__PyCodec_ReplaceErrors(a);
    STATS_AFTER(PyCodec_ReplaceErrors)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyCodec_StreamReader, PyCodec_ReplaceErrors)
PyObject* (*__target__PyCodec_StreamReader)(const char*, PyObject*, const char*) = NULL;
PyAPI_FUNC(PyObject*) PyCodec_StreamReader(const char* a, PyObject* b, const char* c) {
    LOG("'%s'(0x%lx) 0x%lx '%s'(0x%lx)", a?a:"<null>", (unsigned long) a, (unsigned long) b, c?c:"<null>", (unsigned long) c);
    if (__target__PyCodec_StreamReader == NULL) {
        __target__PyCodec_StreamReader = resolveAPI("PyCodec_StreamReader");
    }
    STATS_BEFORE(PyCodec_StreamReader)
    PyObject* result = (PyObject*) __target__PyCodec_StreamReader(a, b, c);
    STATS_AFTER(PyCodec_StreamReader)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyCodec_StreamWriter, PyCodec_StreamReader)
PyObject* (*__target__PyCodec_StreamWriter)(const char*, PyObject*, const char*) = NULL;
PyAPI_FUNC(PyObject*) PyCodec_StreamWriter(const char* a, PyObject* b, const char* c) {
    LOG("'%s'(0x%lx) 0x%lx '%s'(0x%lx)", a?a:"<null>", (unsigned long) a, (unsigned long) b, c?c:"<null>", (unsigned long) c);
    if (__target__PyCodec_StreamWriter == NULL) {
        __target__PyCodec_StreamWriter = resolveAPI("PyCodec_StreamWriter");
    }
    STATS_BEFORE(PyCodec_StreamWriter)
    PyObject* result = (PyObject*) __target__PyCodec_StreamWriter(a, b, c);
    STATS_AFTER(PyCodec_StreamWriter)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyCodec_StrictErrors, PyCodec_StreamWriter)
PyObject* (*__target__PyCodec_StrictErrors)(PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyCodec_StrictErrors(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyCodec_StrictErrors == NULL) {
        __target__PyCodec_StrictErrors = resolveAPI("PyCodec_StrictErrors");
    }
    STATS_BEFORE(PyCodec_StrictErrors)
    PyObject* result = (PyObject*) __target__PyCodec_StrictErrors(a);
    STATS_AFTER(PyCodec_StrictErrors)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyCodec_Unregister, PyCodec_StrictErrors)
int (*__target__PyCodec_Unregister)(PyObject*) = NULL;
PyAPI_FUNC(int) PyCodec_Unregister(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyCodec_Unregister == NULL) {
        __target__PyCodec_Unregister = resolveAPI("PyCodec_Unregister");
    }
    STATS_BEFORE(PyCodec_Unregister)
    int result = (int) __target__PyCodec_Unregister(a);
    STATS_AFTER(PyCodec_Unregister)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyCodec_XMLCharRefReplaceErrors, PyCodec_Unregister)
PyObject* (*__target__PyCodec_XMLCharRefReplaceErrors)(PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyCodec_XMLCharRefReplaceErrors(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyCodec_XMLCharRefReplaceErrors == NULL) {
        __target__PyCodec_XMLCharRefReplaceErrors = resolveAPI("PyCodec_XMLCharRefReplaceErrors");
    }
    STATS_BEFORE(PyCodec_XMLCharRefReplaceErrors)
    PyObject* result = (PyObject*) __target__PyCodec_XMLCharRefReplaceErrors(a);
    STATS_AFTER(PyCodec_XMLCharRefReplaceErrors)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyCompile_OpcodeStackEffect, PyCodec_XMLCharRefReplaceErrors)
int (*__target__PyCompile_OpcodeStackEffect)(int, int) = NULL;
PyAPI_FUNC(int) PyCompile_OpcodeStackEffect(int a, int b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target__PyCompile_OpcodeStackEffect == NULL) {
        __target__PyCompile_OpcodeStackEffect = resolveAPI("PyCompile_OpcodeStackEffect");
    }
    STATS_BEFORE(PyCompile_OpcodeStackEffect)
    int result = (int) __target__PyCompile_OpcodeStackEffect(a, b);
    STATS_AFTER(PyCompile_OpcodeStackEffect)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyCompile_OpcodeStackEffectWithJump, PyCompile_OpcodeStackEffect)
int (*__target__PyCompile_OpcodeStackEffectWithJump)(int, int, int) = NULL;
PyAPI_FUNC(int) PyCompile_OpcodeStackEffectWithJump(int a, int b, int c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    if (__target__PyCompile_OpcodeStackEffectWithJump == NULL) {
        __target__PyCompile_OpcodeStackEffectWithJump = resolveAPI("PyCompile_OpcodeStackEffectWithJump");
    }
    STATS_BEFORE(PyCompile_OpcodeStackEffectWithJump)
    int result = (int) __target__PyCompile_OpcodeStackEffectWithJump(a, b, c);
    STATS_AFTER(PyCompile_OpcodeStackEffectWithJump)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyComplex_FromDoubles, PyCompile_OpcodeStackEffectWithJump)
PyObject* (*__target__PyComplex_FromDoubles)(double, double) = NULL;
PyAPI_FUNC(PyObject*) PyComplex_FromDoubles(double a, double b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target__PyComplex_FromDoubles == NULL) {
        __target__PyComplex_FromDoubles = resolveAPI("PyComplex_FromDoubles");
    }
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
    if (__target__PyComplex_ImagAsDouble == NULL) {
        __target__PyComplex_ImagAsDouble = resolveAPI("PyComplex_ImagAsDouble");
    }
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
    if (__target__PyComplex_RealAsDouble == NULL) {
        __target__PyComplex_RealAsDouble = resolveAPI("PyComplex_RealAsDouble");
    }
    STATS_BEFORE(PyComplex_RealAsDouble)
    double result = (double) __target__PyComplex_RealAsDouble(a);
    STATS_AFTER(PyComplex_RealAsDouble)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyConfig_Clear, PyComplex_RealAsDouble)
void (*__target__PyConfig_Clear)(PyConfig*) = NULL;
PyAPI_FUNC(void) PyConfig_Clear(PyConfig* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyConfig_Clear == NULL) {
        __target__PyConfig_Clear = resolveAPI("PyConfig_Clear");
    }
    STATS_BEFORE(PyConfig_Clear)
    __target__PyConfig_Clear(a);
    STATS_AFTER(PyConfig_Clear)
    LOG_AFTER_VOID
}
STATS_CONTAINER(PyConfig_InitIsolatedConfig, PyConfig_Clear)
void (*__target__PyConfig_InitIsolatedConfig)(PyConfig*) = NULL;
PyAPI_FUNC(void) PyConfig_InitIsolatedConfig(PyConfig* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyConfig_InitIsolatedConfig == NULL) {
        __target__PyConfig_InitIsolatedConfig = resolveAPI("PyConfig_InitIsolatedConfig");
    }
    STATS_BEFORE(PyConfig_InitIsolatedConfig)
    __target__PyConfig_InitIsolatedConfig(a);
    STATS_AFTER(PyConfig_InitIsolatedConfig)
    LOG_AFTER_VOID
}
STATS_CONTAINER(PyConfig_InitPythonConfig, PyConfig_InitIsolatedConfig)
void (*__target__PyConfig_InitPythonConfig)(PyConfig*) = NULL;
PyAPI_FUNC(void) PyConfig_InitPythonConfig(PyConfig* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyConfig_InitPythonConfig == NULL) {
        __target__PyConfig_InitPythonConfig = resolveAPI("PyConfig_InitPythonConfig");
    }
    STATS_BEFORE(PyConfig_InitPythonConfig)
    __target__PyConfig_InitPythonConfig(a);
    STATS_AFTER(PyConfig_InitPythonConfig)
    LOG_AFTER_VOID
}
STATS_CONTAINER(PyConfig_Read, PyConfig_InitPythonConfig)
PyStatus (*__target__PyConfig_Read)(PyConfig*) = NULL;
PyAPI_FUNC(PyStatus) PyConfig_Read(PyConfig* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyConfig_Read == NULL) {
        __target__PyConfig_Read = resolveAPI("PyConfig_Read");
    }
    STATS_BEFORE(PyConfig_Read)
    PyStatus result = (PyStatus) __target__PyConfig_Read(a);
    STATS_AFTER(PyConfig_Read)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyConfig_SetArgv, PyConfig_Read)
PyStatus (*__target__PyConfig_SetArgv)(PyConfig*, Py_ssize_t, wchar_t*const*) = NULL;
PyAPI_FUNC(PyStatus) PyConfig_SetArgv(PyConfig* a, Py_ssize_t b, wchar_t*const* c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    if (__target__PyConfig_SetArgv == NULL) {
        __target__PyConfig_SetArgv = resolveAPI("PyConfig_SetArgv");
    }
    STATS_BEFORE(PyConfig_SetArgv)
    PyStatus result = (PyStatus) __target__PyConfig_SetArgv(a, b, c);
    STATS_AFTER(PyConfig_SetArgv)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyConfig_SetBytesArgv, PyConfig_SetArgv)
PyStatus (*__target__PyConfig_SetBytesArgv)(PyConfig*, Py_ssize_t, char*const*) = NULL;
PyAPI_FUNC(PyStatus) PyConfig_SetBytesArgv(PyConfig* a, Py_ssize_t b, char*const* c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    if (__target__PyConfig_SetBytesArgv == NULL) {
        __target__PyConfig_SetBytesArgv = resolveAPI("PyConfig_SetBytesArgv");
    }
    STATS_BEFORE(PyConfig_SetBytesArgv)
    PyStatus result = (PyStatus) __target__PyConfig_SetBytesArgv(a, b, c);
    STATS_AFTER(PyConfig_SetBytesArgv)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyConfig_SetBytesString, PyConfig_SetBytesArgv)
PyStatus (*__target__PyConfig_SetBytesString)(PyConfig*, wchar_t**, const char*) = NULL;
PyAPI_FUNC(PyStatus) PyConfig_SetBytesString(PyConfig* a, wchar_t** b, const char* c) {
    LOG("0x%lx 0x%lx '%s'(0x%lx)", (unsigned long) a, (unsigned long) b, c?c:"<null>", (unsigned long) c);
    if (__target__PyConfig_SetBytesString == NULL) {
        __target__PyConfig_SetBytesString = resolveAPI("PyConfig_SetBytesString");
    }
    STATS_BEFORE(PyConfig_SetBytesString)
    PyStatus result = (PyStatus) __target__PyConfig_SetBytesString(a, b, c);
    STATS_AFTER(PyConfig_SetBytesString)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyConfig_SetString, PyConfig_SetBytesString)
PyStatus (*__target__PyConfig_SetString)(PyConfig*, wchar_t**, const wchar_t*) = NULL;
PyAPI_FUNC(PyStatus) PyConfig_SetString(PyConfig* a, wchar_t** b, const wchar_t* c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    if (__target__PyConfig_SetString == NULL) {
        __target__PyConfig_SetString = resolveAPI("PyConfig_SetString");
    }
    STATS_BEFORE(PyConfig_SetString)
    PyStatus result = (PyStatus) __target__PyConfig_SetString(a, b, c);
    STATS_AFTER(PyConfig_SetString)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyConfig_SetWideStringList, PyConfig_SetString)
PyStatus (*__target__PyConfig_SetWideStringList)(PyConfig*, PyWideStringList*, Py_ssize_t, wchar_t**) = NULL;
PyAPI_FUNC(PyStatus) PyConfig_SetWideStringList(PyConfig* a, PyWideStringList* b, Py_ssize_t c, wchar_t** d) {
    LOG("0x%lx 0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c, (unsigned long) d);
    if (__target__PyConfig_SetWideStringList == NULL) {
        __target__PyConfig_SetWideStringList = resolveAPI("PyConfig_SetWideStringList");
    }
    STATS_BEFORE(PyConfig_SetWideStringList)
    PyStatus result = (PyStatus) __target__PyConfig_SetWideStringList(a, b, c, d);
    STATS_AFTER(PyConfig_SetWideStringList)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyContextVar_Get, PyConfig_SetWideStringList)
int (*__target__PyContextVar_Get)(PyObject*, PyObject*, PyObject**) = NULL;
PyAPI_FUNC(int) PyContextVar_Get(PyObject* a, PyObject* b, PyObject** c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    if (__target__PyContextVar_Get == NULL) {
        __target__PyContextVar_Get = resolveAPI("PyContextVar_Get");
    }
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
    if (__target__PyContextVar_New == NULL) {
        __target__PyContextVar_New = resolveAPI("PyContextVar_New");
    }
    STATS_BEFORE(PyContextVar_New)
    PyObject* result = (PyObject*) __target__PyContextVar_New(a, b);
    STATS_AFTER(PyContextVar_New)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyContextVar_Reset, PyContextVar_New)
int (*__target__PyContextVar_Reset)(PyObject*, PyObject*) = NULL;
PyAPI_FUNC(int) PyContextVar_Reset(PyObject* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target__PyContextVar_Reset == NULL) {
        __target__PyContextVar_Reset = resolveAPI("PyContextVar_Reset");
    }
    STATS_BEFORE(PyContextVar_Reset)
    int result = (int) __target__PyContextVar_Reset(a, b);
    STATS_AFTER(PyContextVar_Reset)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyContextVar_Set, PyContextVar_Reset)
PyObject* (*__target__PyContextVar_Set)(PyObject*, PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyContextVar_Set(PyObject* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target__PyContextVar_Set == NULL) {
        __target__PyContextVar_Set = resolveAPI("PyContextVar_Set");
    }
    STATS_BEFORE(PyContextVar_Set)
    PyObject* result = (PyObject*) __target__PyContextVar_Set(a, b);
    STATS_AFTER(PyContextVar_Set)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyContext_Copy, PyContextVar_Set)
PyObject* (*__target__PyContext_Copy)(PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyContext_Copy(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyContext_Copy == NULL) {
        __target__PyContext_Copy = resolveAPI("PyContext_Copy");
    }
    STATS_BEFORE(PyContext_Copy)
    PyObject* result = (PyObject*) __target__PyContext_Copy(a);
    STATS_AFTER(PyContext_Copy)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyContext_CopyCurrent, PyContext_Copy)
PyObject* (*__target__PyContext_CopyCurrent)() = NULL;
PyAPI_FUNC(PyObject*) PyContext_CopyCurrent() {
    LOGS("");
    if (__target__PyContext_CopyCurrent == NULL) {
        __target__PyContext_CopyCurrent = resolveAPI("PyContext_CopyCurrent");
    }
    STATS_BEFORE(PyContext_CopyCurrent)
    PyObject* result = (PyObject*) __target__PyContext_CopyCurrent();
    STATS_AFTER(PyContext_CopyCurrent)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyContext_Enter, PyContext_CopyCurrent)
int (*__target__PyContext_Enter)(PyObject*) = NULL;
PyAPI_FUNC(int) PyContext_Enter(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyContext_Enter == NULL) {
        __target__PyContext_Enter = resolveAPI("PyContext_Enter");
    }
    STATS_BEFORE(PyContext_Enter)
    int result = (int) __target__PyContext_Enter(a);
    STATS_AFTER(PyContext_Enter)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyContext_Exit, PyContext_Enter)
int (*__target__PyContext_Exit)(PyObject*) = NULL;
PyAPI_FUNC(int) PyContext_Exit(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyContext_Exit == NULL) {
        __target__PyContext_Exit = resolveAPI("PyContext_Exit");
    }
    STATS_BEFORE(PyContext_Exit)
    int result = (int) __target__PyContext_Exit(a);
    STATS_AFTER(PyContext_Exit)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyContext_New, PyContext_Exit)
PyObject* (*__target__PyContext_New)() = NULL;
PyAPI_FUNC(PyObject*) PyContext_New() {
    LOGS("");
    if (__target__PyContext_New == NULL) {
        __target__PyContext_New = resolveAPI("PyContext_New");
    }
    STATS_BEFORE(PyContext_New)
    PyObject* result = (PyObject*) __target__PyContext_New();
    STATS_AFTER(PyContext_New)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyCoro_New, PyContext_New)
PyObject* (*__target__PyCoro_New)(PyFrameObject*, PyObject*, PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyCoro_New(PyFrameObject* a, PyObject* b, PyObject* c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    if (__target__PyCoro_New == NULL) {
        __target__PyCoro_New = resolveAPI("PyCoro_New");
    }
    STATS_BEFORE(PyCoro_New)
    PyObject* result = (PyObject*) __target__PyCoro_New(a, b, c);
    STATS_AFTER(PyCoro_New)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyDescrObject_GetName, PyCoro_New)
PyObject* (*__target__PyDescrObject_GetName)(PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyDescrObject_GetName(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyDescrObject_GetName == NULL) {
        __target__PyDescrObject_GetName = resolveAPI("PyDescrObject_GetName");
    }
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
    if (__target__PyDescrObject_GetType == NULL) {
        __target__PyDescrObject_GetType = resolveAPI("PyDescrObject_GetType");
    }
    STATS_BEFORE(PyDescrObject_GetType)
    PyTypeObject* result = (PyTypeObject*) __target__PyDescrObject_GetType(a);
    STATS_AFTER(PyDescrObject_GetType)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyDescr_IsData, PyDescrObject_GetType)
int (*__target__PyDescr_IsData)(PyObject*) = NULL;
PyAPI_FUNC(int) PyDescr_IsData(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyDescr_IsData == NULL) {
        __target__PyDescr_IsData = resolveAPI("PyDescr_IsData");
    }
    STATS_BEFORE(PyDescr_IsData)
    int result = (int) __target__PyDescr_IsData(a);
    STATS_AFTER(PyDescr_IsData)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyDescr_NewClassMethod, PyDescr_IsData)
PyObject* (*__target__PyDescr_NewClassMethod)(PyTypeObject*, PyMethodDef*) = NULL;
PyAPI_FUNC(PyObject*) PyDescr_NewClassMethod(PyTypeObject* a, PyMethodDef* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target__PyDescr_NewClassMethod == NULL) {
        __target__PyDescr_NewClassMethod = resolveAPI("PyDescr_NewClassMethod");
    }
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
    if (__target__PyDescr_NewGetSet == NULL) {
        __target__PyDescr_NewGetSet = resolveAPI("PyDescr_NewGetSet");
    }
    STATS_BEFORE(PyDescr_NewGetSet)
    PyObject* result = (PyObject*) __target__PyDescr_NewGetSet(a, b);
    STATS_AFTER(PyDescr_NewGetSet)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyDescr_NewMember, PyDescr_NewGetSet)
PyObject* (*__target__PyDescr_NewMember)(PyTypeObject*, struct PyMemberDef*) = NULL;
PyAPI_FUNC(PyObject*) PyDescr_NewMember(PyTypeObject* a, struct PyMemberDef* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target__PyDescr_NewMember == NULL) {
        __target__PyDescr_NewMember = resolveAPI("PyDescr_NewMember");
    }
    STATS_BEFORE(PyDescr_NewMember)
    PyObject* result = (PyObject*) __target__PyDescr_NewMember(a, b);
    STATS_AFTER(PyDescr_NewMember)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyDescr_NewMethod, PyDescr_NewMember)
PyObject* (*__target__PyDescr_NewMethod)(PyTypeObject*, PyMethodDef*) = NULL;
PyAPI_FUNC(PyObject*) PyDescr_NewMethod(PyTypeObject* a, PyMethodDef* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target__PyDescr_NewMethod == NULL) {
        __target__PyDescr_NewMethod = resolveAPI("PyDescr_NewMethod");
    }
    STATS_BEFORE(PyDescr_NewMethod)
    PyObject* result = (PyObject*) __target__PyDescr_NewMethod(a, b);
    STATS_AFTER(PyDescr_NewMethod)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyDescr_NewWrapper, PyDescr_NewMethod)
PyObject* (*__target__PyDescr_NewWrapper)(PyTypeObject*, struct wrapperbase*, void*) = NULL;
PyAPI_FUNC(PyObject*) PyDescr_NewWrapper(PyTypeObject* a, struct wrapperbase* b, void* c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    if (__target__PyDescr_NewWrapper == NULL) {
        __target__PyDescr_NewWrapper = resolveAPI("PyDescr_NewWrapper");
    }
    STATS_BEFORE(PyDescr_NewWrapper)
    PyObject* result = (PyObject*) __target__PyDescr_NewWrapper(a, b, c);
    STATS_AFTER(PyDescr_NewWrapper)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyDictProxy_New, PyDescr_NewWrapper)
PyObject* (*__target__PyDictProxy_New)(PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyDictProxy_New(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyDictProxy_New == NULL) {
        __target__PyDictProxy_New = resolveAPI("PyDictProxy_New");
    }
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
    if (__target__PyDict_Clear == NULL) {
        __target__PyDict_Clear = resolveAPI("PyDict_Clear");
    }
    STATS_BEFORE(PyDict_Clear)
    __target__PyDict_Clear(a);
    STATS_AFTER(PyDict_Clear)
    LOG_AFTER_VOID
}
STATS_CONTAINER(PyDict_Contains, PyDict_Clear)
int (*__target__PyDict_Contains)(PyObject*, PyObject*) = NULL;
PyAPI_FUNC(int) PyDict_Contains(PyObject* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target__PyDict_Contains == NULL) {
        __target__PyDict_Contains = resolveAPI("PyDict_Contains");
    }
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
    if (__target__PyDict_Copy == NULL) {
        __target__PyDict_Copy = resolveAPI("PyDict_Copy");
    }
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
    if (__target__PyDict_DelItem == NULL) {
        __target__PyDict_DelItem = resolveAPI("PyDict_DelItem");
    }
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
    if (__target__PyDict_GetItem == NULL) {
        __target__PyDict_GetItem = resolveAPI("PyDict_GetItem");
    }
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
    if (__target__PyDict_GetItemWithError == NULL) {
        __target__PyDict_GetItemWithError = resolveAPI("PyDict_GetItemWithError");
    }
    STATS_BEFORE(PyDict_GetItemWithError)
    PyObject* result = (PyObject*) __target__PyDict_GetItemWithError(a, b);
    STATS_AFTER(PyDict_GetItemWithError)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyDict_Items, PyDict_GetItemWithError)
PyObject* (*__target__PyDict_Items)(PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyDict_Items(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyDict_Items == NULL) {
        __target__PyDict_Items = resolveAPI("PyDict_Items");
    }
    STATS_BEFORE(PyDict_Items)
    PyObject* result = (PyObject*) __target__PyDict_Items(a);
    STATS_AFTER(PyDict_Items)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyDict_Keys, PyDict_Items)
PyObject* (*__target__PyDict_Keys)(PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyDict_Keys(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyDict_Keys == NULL) {
        __target__PyDict_Keys = resolveAPI("PyDict_Keys");
    }
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
    if (__target__PyDict_Merge == NULL) {
        __target__PyDict_Merge = resolveAPI("PyDict_Merge");
    }
    STATS_BEFORE(PyDict_Merge)
    int result = (int) __target__PyDict_Merge(a, b, c);
    STATS_AFTER(PyDict_Merge)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyDict_MergeFromSeq2, PyDict_Merge)
int (*__target__PyDict_MergeFromSeq2)(PyObject*, PyObject*, int) = NULL;
PyAPI_FUNC(int) PyDict_MergeFromSeq2(PyObject* a, PyObject* b, int c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    if (__target__PyDict_MergeFromSeq2 == NULL) {
        __target__PyDict_MergeFromSeq2 = resolveAPI("PyDict_MergeFromSeq2");
    }
    STATS_BEFORE(PyDict_MergeFromSeq2)
    int result = (int) __target__PyDict_MergeFromSeq2(a, b, c);
    STATS_AFTER(PyDict_MergeFromSeq2)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyDict_New, PyDict_MergeFromSeq2)
PyObject* (*__target__PyDict_New)() = NULL;
PyAPI_FUNC(PyObject*) PyDict_New() {
    LOGS("");
    if (__target__PyDict_New == NULL) {
        __target__PyDict_New = resolveAPI("PyDict_New");
    }
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
    if (__target__PyDict_SetDefault == NULL) {
        __target__PyDict_SetDefault = resolveAPI("PyDict_SetDefault");
    }
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
    if (__target__PyDict_SetItem == NULL) {
        __target__PyDict_SetItem = resolveAPI("PyDict_SetItem");
    }
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
    if (__target__PyDict_Size == NULL) {
        __target__PyDict_Size = resolveAPI("PyDict_Size");
    }
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
    if (__target__PyDict_Update == NULL) {
        __target__PyDict_Update = resolveAPI("PyDict_Update");
    }
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
    if (__target__PyDict_Values == NULL) {
        __target__PyDict_Values = resolveAPI("PyDict_Values");
    }
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
    if (__target__PyErr_BadArgument == NULL) {
        __target__PyErr_BadArgument = resolveAPI("PyErr_BadArgument");
    }
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
    if (__target__PyErr_BadInternalCall == NULL) {
        __target__PyErr_BadInternalCall = resolveAPI("PyErr_BadInternalCall");
    }
    STATS_BEFORE(PyErr_BadInternalCall)
    __target__PyErr_BadInternalCall();
    STATS_AFTER(PyErr_BadInternalCall)
    LOG_AFTER_VOID
}
STATS_CONTAINER(PyErr_CheckSignals, PyErr_BadInternalCall)
int (*__target__PyErr_CheckSignals)() = NULL;
PyAPI_FUNC(int) PyErr_CheckSignals() {
    LOGS("");
    if (__target__PyErr_CheckSignals == NULL) {
        __target__PyErr_CheckSignals = resolveAPI("PyErr_CheckSignals");
    }
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
    if (__target__PyErr_Clear == NULL) {
        __target__PyErr_Clear = resolveAPI("PyErr_Clear");
    }
    STATS_BEFORE(PyErr_Clear)
    __target__PyErr_Clear();
    STATS_AFTER(PyErr_Clear)
    LOG_AFTER_VOID
}
STATS_CONTAINER(PyErr_Display, PyErr_Clear)
void (*__target__PyErr_Display)(PyObject*, PyObject*, PyObject*) = NULL;
PyAPI_FUNC(void) PyErr_Display(PyObject* a, PyObject* b, PyObject* c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    if (__target__PyErr_Display == NULL) {
        __target__PyErr_Display = resolveAPI("PyErr_Display");
    }
    STATS_BEFORE(PyErr_Display)
    __target__PyErr_Display(a, b, c);
    STATS_AFTER(PyErr_Display)
    LOG_AFTER_VOID
}
STATS_CONTAINER(PyErr_ExceptionMatches, PyErr_Display)
int (*__target__PyErr_ExceptionMatches)(PyObject*) = NULL;
PyAPI_FUNC(int) PyErr_ExceptionMatches(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyErr_ExceptionMatches == NULL) {
        __target__PyErr_ExceptionMatches = resolveAPI("PyErr_ExceptionMatches");
    }
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
    if (__target__PyErr_Fetch == NULL) {
        __target__PyErr_Fetch = resolveAPI("PyErr_Fetch");
    }
    STATS_BEFORE(PyErr_Fetch)
    __target__PyErr_Fetch(a, b, c);
    STATS_AFTER(PyErr_Fetch)
    LOG_AFTER_VOID
}
STATS_CONTAINER(PyErr_FormatV, PyErr_Fetch)
PyObject* (*__target__PyErr_FormatV)(PyObject*, const char*, va_list) = NULL;
PyAPI_FUNC(PyObject*) PyErr_FormatV(PyObject* a, const char* b, va_list c) {
    LOG("0x%lx '%s'(0x%lx) 0x%lx", (unsigned long) a, b?b:"<null>", (unsigned long) b, (unsigned long) c);
    if (__target__PyErr_FormatV == NULL) {
        __target__PyErr_FormatV = resolveAPI("PyErr_FormatV");
    }
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
    if (__target__PyErr_GetExcInfo == NULL) {
        __target__PyErr_GetExcInfo = resolveAPI("PyErr_GetExcInfo");
    }
    STATS_BEFORE(PyErr_GetExcInfo)
    __target__PyErr_GetExcInfo(a, b, c);
    STATS_AFTER(PyErr_GetExcInfo)
    LOG_AFTER_VOID
}
STATS_CONTAINER(PyErr_GivenExceptionMatches, PyErr_GetExcInfo)
int (*__target__PyErr_GivenExceptionMatches)(PyObject*, PyObject*) = NULL;
PyAPI_FUNC(int) PyErr_GivenExceptionMatches(PyObject* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target__PyErr_GivenExceptionMatches == NULL) {
        __target__PyErr_GivenExceptionMatches = resolveAPI("PyErr_GivenExceptionMatches");
    }
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
    if (__target__PyErr_NewException == NULL) {
        __target__PyErr_NewException = resolveAPI("PyErr_NewException");
    }
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
    if (__target__PyErr_NewExceptionWithDoc == NULL) {
        __target__PyErr_NewExceptionWithDoc = resolveAPI("PyErr_NewExceptionWithDoc");
    }
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
    if (__target__PyErr_NoMemory == NULL) {
        __target__PyErr_NoMemory = resolveAPI("PyErr_NoMemory");
    }
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
    if (__target__PyErr_NormalizeException == NULL) {
        __target__PyErr_NormalizeException = resolveAPI("PyErr_NormalizeException");
    }
    STATS_BEFORE(PyErr_NormalizeException)
    __target__PyErr_NormalizeException(a, b, c);
    STATS_AFTER(PyErr_NormalizeException)
    LOG_AFTER_VOID
}
STATS_CONTAINER(PyErr_Occurred, PyErr_NormalizeException)
PyObject* (*__target__PyErr_Occurred)() = NULL;
PyAPI_FUNC(PyObject*) PyErr_Occurred() {
    LOGS("");
    if (__target__PyErr_Occurred == NULL) {
        __target__PyErr_Occurred = resolveAPI("PyErr_Occurred");
    }
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
    if (__target__PyErr_Print == NULL) {
        __target__PyErr_Print = resolveAPI("PyErr_Print");
    }
    STATS_BEFORE(PyErr_Print)
    __target__PyErr_Print();
    STATS_AFTER(PyErr_Print)
    LOG_AFTER_VOID
}
STATS_CONTAINER(PyErr_PrintEx, PyErr_Print)
void (*__target__PyErr_PrintEx)(int) = NULL;
PyAPI_FUNC(void) PyErr_PrintEx(int a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyErr_PrintEx == NULL) {
        __target__PyErr_PrintEx = resolveAPI("PyErr_PrintEx");
    }
    STATS_BEFORE(PyErr_PrintEx)
    __target__PyErr_PrintEx(a);
    STATS_AFTER(PyErr_PrintEx)
    LOG_AFTER_VOID
}
STATS_CONTAINER(PyErr_ProgramText, PyErr_PrintEx)
PyObject* (*__target__PyErr_ProgramText)(const char*, int) = NULL;
PyAPI_FUNC(PyObject*) PyErr_ProgramText(const char* a, int b) {
    LOG("'%s'(0x%lx) 0x%lx", a?a:"<null>", (unsigned long) a, (unsigned long) b);
    if (__target__PyErr_ProgramText == NULL) {
        __target__PyErr_ProgramText = resolveAPI("PyErr_ProgramText");
    }
    STATS_BEFORE(PyErr_ProgramText)
    PyObject* result = (PyObject*) __target__PyErr_ProgramText(a, b);
    STATS_AFTER(PyErr_ProgramText)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyErr_ProgramTextObject, PyErr_ProgramText)
PyObject* (*__target__PyErr_ProgramTextObject)(PyObject*, int) = NULL;
PyAPI_FUNC(PyObject*) PyErr_ProgramTextObject(PyObject* a, int b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target__PyErr_ProgramTextObject == NULL) {
        __target__PyErr_ProgramTextObject = resolveAPI("PyErr_ProgramTextObject");
    }
    STATS_BEFORE(PyErr_ProgramTextObject)
    PyObject* result = (PyObject*) __target__PyErr_ProgramTextObject(a, b);
    STATS_AFTER(PyErr_ProgramTextObject)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyErr_RangedSyntaxLocationObject, PyErr_ProgramTextObject)
void (*__target__PyErr_RangedSyntaxLocationObject)(PyObject*, int, int, int, int) = NULL;
PyAPI_FUNC(void) PyErr_RangedSyntaxLocationObject(PyObject* a, int b, int c, int d, int e) {
    LOG("0x%lx 0x%lx 0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c, (unsigned long) d, (unsigned long) e);
    if (__target__PyErr_RangedSyntaxLocationObject == NULL) {
        __target__PyErr_RangedSyntaxLocationObject = resolveAPI("PyErr_RangedSyntaxLocationObject");
    }
    STATS_BEFORE(PyErr_RangedSyntaxLocationObject)
    __target__PyErr_RangedSyntaxLocationObject(a, b, c, d, e);
    STATS_AFTER(PyErr_RangedSyntaxLocationObject)
    LOG_AFTER_VOID
}
STATS_CONTAINER(PyErr_Restore, PyErr_RangedSyntaxLocationObject)
void (*__target__PyErr_Restore)(PyObject*, PyObject*, PyObject*) = NULL;
PyAPI_FUNC(void) PyErr_Restore(PyObject* a, PyObject* b, PyObject* c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    if (__target__PyErr_Restore == NULL) {
        __target__PyErr_Restore = resolveAPI("PyErr_Restore");
    }
    STATS_BEFORE(PyErr_Restore)
    __target__PyErr_Restore(a, b, c);
    STATS_AFTER(PyErr_Restore)
    LOG_AFTER_VOID
}
STATS_CONTAINER(PyErr_SetExcInfo, PyErr_Restore)
void (*__target__PyErr_SetExcInfo)(PyObject*, PyObject*, PyObject*) = NULL;
PyAPI_FUNC(void) PyErr_SetExcInfo(PyObject* a, PyObject* b, PyObject* c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    if (__target__PyErr_SetExcInfo == NULL) {
        __target__PyErr_SetExcInfo = resolveAPI("PyErr_SetExcInfo");
    }
    STATS_BEFORE(PyErr_SetExcInfo)
    __target__PyErr_SetExcInfo(a, b, c);
    STATS_AFTER(PyErr_SetExcInfo)
    LOG_AFTER_VOID
}
STATS_CONTAINER(PyErr_SetFromErrno, PyErr_SetExcInfo)
PyObject* (*__target__PyErr_SetFromErrno)(PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyErr_SetFromErrno(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyErr_SetFromErrno == NULL) {
        __target__PyErr_SetFromErrno = resolveAPI("PyErr_SetFromErrno");
    }
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
    if (__target__PyErr_SetFromErrnoWithFilename == NULL) {
        __target__PyErr_SetFromErrnoWithFilename = resolveAPI("PyErr_SetFromErrnoWithFilename");
    }
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
    if (__target__PyErr_SetFromErrnoWithFilenameObject == NULL) {
        __target__PyErr_SetFromErrnoWithFilenameObject = resolveAPI("PyErr_SetFromErrnoWithFilenameObject");
    }
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
    if (__target__PyErr_SetFromErrnoWithFilenameObjects == NULL) {
        __target__PyErr_SetFromErrnoWithFilenameObjects = resolveAPI("PyErr_SetFromErrnoWithFilenameObjects");
    }
    STATS_BEFORE(PyErr_SetFromErrnoWithFilenameObjects)
    PyObject* result = (PyObject*) __target__PyErr_SetFromErrnoWithFilenameObjects(a, b, c);
    STATS_AFTER(PyErr_SetFromErrnoWithFilenameObjects)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyErr_SetImportError, PyErr_SetFromErrnoWithFilenameObjects)
PyObject* (*__target__PyErr_SetImportError)(PyObject*, PyObject*, PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyErr_SetImportError(PyObject* a, PyObject* b, PyObject* c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    if (__target__PyErr_SetImportError == NULL) {
        __target__PyErr_SetImportError = resolveAPI("PyErr_SetImportError");
    }
    STATS_BEFORE(PyErr_SetImportError)
    PyObject* result = (PyObject*) __target__PyErr_SetImportError(a, b, c);
    STATS_AFTER(PyErr_SetImportError)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyErr_SetImportErrorSubclass, PyErr_SetImportError)
PyObject* (*__target__PyErr_SetImportErrorSubclass)(PyObject*, PyObject*, PyObject*, PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyErr_SetImportErrorSubclass(PyObject* a, PyObject* b, PyObject* c, PyObject* d) {
    LOG("0x%lx 0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c, (unsigned long) d);
    if (__target__PyErr_SetImportErrorSubclass == NULL) {
        __target__PyErr_SetImportErrorSubclass = resolveAPI("PyErr_SetImportErrorSubclass");
    }
    STATS_BEFORE(PyErr_SetImportErrorSubclass)
    PyObject* result = (PyObject*) __target__PyErr_SetImportErrorSubclass(a, b, c, d);
    STATS_AFTER(PyErr_SetImportErrorSubclass)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyErr_SetInterrupt, PyErr_SetImportErrorSubclass)
void (*__target__PyErr_SetInterrupt)() = NULL;
PyAPI_FUNC(void) PyErr_SetInterrupt() {
    LOGS("");
    if (__target__PyErr_SetInterrupt == NULL) {
        __target__PyErr_SetInterrupt = resolveAPI("PyErr_SetInterrupt");
    }
    STATS_BEFORE(PyErr_SetInterrupt)
    __target__PyErr_SetInterrupt();
    STATS_AFTER(PyErr_SetInterrupt)
    LOG_AFTER_VOID
}
STATS_CONTAINER(PyErr_SetInterruptEx, PyErr_SetInterrupt)
int (*__target__PyErr_SetInterruptEx)(int) = NULL;
PyAPI_FUNC(int) PyErr_SetInterruptEx(int a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyErr_SetInterruptEx == NULL) {
        __target__PyErr_SetInterruptEx = resolveAPI("PyErr_SetInterruptEx");
    }
    STATS_BEFORE(PyErr_SetInterruptEx)
    int result = (int) __target__PyErr_SetInterruptEx(a);
    STATS_AFTER(PyErr_SetInterruptEx)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyErr_SetNone, PyErr_SetInterruptEx)
void (*__target__PyErr_SetNone)(PyObject*) = NULL;
PyAPI_FUNC(void) PyErr_SetNone(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyErr_SetNone == NULL) {
        __target__PyErr_SetNone = resolveAPI("PyErr_SetNone");
    }
    STATS_BEFORE(PyErr_SetNone)
    __target__PyErr_SetNone(a);
    STATS_AFTER(PyErr_SetNone)
    LOG_AFTER_VOID
}
STATS_CONTAINER(PyErr_SetObject, PyErr_SetNone)
void (*__target__PyErr_SetObject)(PyObject*, PyObject*) = NULL;
PyAPI_FUNC(void) PyErr_SetObject(PyObject* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target__PyErr_SetObject == NULL) {
        __target__PyErr_SetObject = resolveAPI("PyErr_SetObject");
    }
    STATS_BEFORE(PyErr_SetObject)
    __target__PyErr_SetObject(a, b);
    STATS_AFTER(PyErr_SetObject)
    LOG_AFTER_VOID
}
STATS_CONTAINER(PyErr_SetString, PyErr_SetObject)
void (*__target__PyErr_SetString)(PyObject*, const char*) = NULL;
PyAPI_FUNC(void) PyErr_SetString(PyObject* a, const char* b) {
    LOG("0x%lx '%s'(0x%lx)", (unsigned long) a, b?b:"<null>", (unsigned long) b);
    if (__target__PyErr_SetString == NULL) {
        __target__PyErr_SetString = resolveAPI("PyErr_SetString");
    }
    STATS_BEFORE(PyErr_SetString)
    __target__PyErr_SetString(a, b);
    STATS_AFTER(PyErr_SetString)
    LOG_AFTER_VOID
}
STATS_CONTAINER(PyErr_SyntaxLocation, PyErr_SetString)
void (*__target__PyErr_SyntaxLocation)(const char*, int) = NULL;
PyAPI_FUNC(void) PyErr_SyntaxLocation(const char* a, int b) {
    LOG("'%s'(0x%lx) 0x%lx", a?a:"<null>", (unsigned long) a, (unsigned long) b);
    if (__target__PyErr_SyntaxLocation == NULL) {
        __target__PyErr_SyntaxLocation = resolveAPI("PyErr_SyntaxLocation");
    }
    STATS_BEFORE(PyErr_SyntaxLocation)
    __target__PyErr_SyntaxLocation(a, b);
    STATS_AFTER(PyErr_SyntaxLocation)
    LOG_AFTER_VOID
}
STATS_CONTAINER(PyErr_SyntaxLocationEx, PyErr_SyntaxLocation)
void (*__target__PyErr_SyntaxLocationEx)(const char*, int, int) = NULL;
PyAPI_FUNC(void) PyErr_SyntaxLocationEx(const char* a, int b, int c) {
    LOG("'%s'(0x%lx) 0x%lx 0x%lx", a?a:"<null>", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    if (__target__PyErr_SyntaxLocationEx == NULL) {
        __target__PyErr_SyntaxLocationEx = resolveAPI("PyErr_SyntaxLocationEx");
    }
    STATS_BEFORE(PyErr_SyntaxLocationEx)
    __target__PyErr_SyntaxLocationEx(a, b, c);
    STATS_AFTER(PyErr_SyntaxLocationEx)
    LOG_AFTER_VOID
}
STATS_CONTAINER(PyErr_SyntaxLocationObject, PyErr_SyntaxLocationEx)
void (*__target__PyErr_SyntaxLocationObject)(PyObject*, int, int) = NULL;
PyAPI_FUNC(void) PyErr_SyntaxLocationObject(PyObject* a, int b, int c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    if (__target__PyErr_SyntaxLocationObject == NULL) {
        __target__PyErr_SyntaxLocationObject = resolveAPI("PyErr_SyntaxLocationObject");
    }
    STATS_BEFORE(PyErr_SyntaxLocationObject)
    __target__PyErr_SyntaxLocationObject(a, b, c);
    STATS_AFTER(PyErr_SyntaxLocationObject)
    LOG_AFTER_VOID
}
STATS_CONTAINER(PyErr_WarnExplicit, PyErr_SyntaxLocationObject)
int (*__target__PyErr_WarnExplicit)(PyObject*, const char*, const char*, int, const char*, PyObject*) = NULL;
PyAPI_FUNC(int) PyErr_WarnExplicit(PyObject* a, const char* b, const char* c, int d, const char* e, PyObject* f) {
    LOG("0x%lx '%s'(0x%lx) '%s'(0x%lx) 0x%lx '%s'(0x%lx) 0x%lx", (unsigned long) a, b?b:"<null>", (unsigned long) b, c?c:"<null>", (unsigned long) c, (unsigned long) d, e?e:"<null>", (unsigned long) e, (unsigned long) f);
    if (__target__PyErr_WarnExplicit == NULL) {
        __target__PyErr_WarnExplicit = resolveAPI("PyErr_WarnExplicit");
    }
    STATS_BEFORE(PyErr_WarnExplicit)
    int result = (int) __target__PyErr_WarnExplicit(a, b, c, d, e, f);
    STATS_AFTER(PyErr_WarnExplicit)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyErr_WarnExplicitObject, PyErr_WarnExplicit)
int (*__target__PyErr_WarnExplicitObject)(PyObject*, PyObject*, PyObject*, int, PyObject*, PyObject*) = NULL;
PyAPI_FUNC(int) PyErr_WarnExplicitObject(PyObject* a, PyObject* b, PyObject* c, int d, PyObject* e, PyObject* f) {
    LOG("0x%lx 0x%lx 0x%lx 0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c, (unsigned long) d, (unsigned long) e, (unsigned long) f);
    if (__target__PyErr_WarnExplicitObject == NULL) {
        __target__PyErr_WarnExplicitObject = resolveAPI("PyErr_WarnExplicitObject");
    }
    STATS_BEFORE(PyErr_WarnExplicitObject)
    int result = (int) __target__PyErr_WarnExplicitObject(a, b, c, d, e, f);
    STATS_AFTER(PyErr_WarnExplicitObject)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyErr_WriteUnraisable, PyErr_WarnExplicitObject)
void (*__target__PyErr_WriteUnraisable)(PyObject*) = NULL;
PyAPI_FUNC(void) PyErr_WriteUnraisable(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyErr_WriteUnraisable == NULL) {
        __target__PyErr_WriteUnraisable = resolveAPI("PyErr_WriteUnraisable");
    }
    STATS_BEFORE(PyErr_WriteUnraisable)
    __target__PyErr_WriteUnraisable(a);
    STATS_AFTER(PyErr_WriteUnraisable)
    LOG_AFTER_VOID
}
STATS_CONTAINER(PyEval_AcquireLock, PyErr_WriteUnraisable)
void (*__target__PyEval_AcquireLock)() = NULL;
PyAPI_FUNC(void) PyEval_AcquireLock() {
    LOGS("");
    if (__target__PyEval_AcquireLock == NULL) {
        __target__PyEval_AcquireLock = resolveAPI("PyEval_AcquireLock");
    }
    STATS_BEFORE(PyEval_AcquireLock)
    __target__PyEval_AcquireLock();
    STATS_AFTER(PyEval_AcquireLock)
    LOG_AFTER_VOID
}
STATS_CONTAINER(PyEval_AcquireThread, PyEval_AcquireLock)
void (*__target__PyEval_AcquireThread)(PyThreadState*) = NULL;
PyAPI_FUNC(void) PyEval_AcquireThread(PyThreadState* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyEval_AcquireThread == NULL) {
        __target__PyEval_AcquireThread = resolveAPI("PyEval_AcquireThread");
    }
    STATS_BEFORE(PyEval_AcquireThread)
    __target__PyEval_AcquireThread(a);
    STATS_AFTER(PyEval_AcquireThread)
    LOG_AFTER_VOID
}
STATS_CONTAINER(PyEval_CallObjectWithKeywords, PyEval_AcquireThread)
PyObject* (*__target__PyEval_CallObjectWithKeywords)(PyObject*, PyObject*, PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyEval_CallObjectWithKeywords(PyObject* a, PyObject* b, PyObject* c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    if (__target__PyEval_CallObjectWithKeywords == NULL) {
        __target__PyEval_CallObjectWithKeywords = resolveAPI("PyEval_CallObjectWithKeywords");
    }
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
    if (__target__PyEval_EvalCode == NULL) {
        __target__PyEval_EvalCode = resolveAPI("PyEval_EvalCode");
    }
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
    if (__target__PyEval_EvalCodeEx == NULL) {
        __target__PyEval_EvalCodeEx = resolveAPI("PyEval_EvalCodeEx");
    }
    STATS_BEFORE(PyEval_EvalCodeEx)
    PyObject* result = (PyObject*) __target__PyEval_EvalCodeEx(a, b, c, d, e, f, g, h, i, j, k);
    STATS_AFTER(PyEval_EvalCodeEx)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyEval_EvalFrame, PyEval_EvalCodeEx)
PyObject* (*__target__PyEval_EvalFrame)(PyFrameObject*) = NULL;
PyAPI_FUNC(PyObject*) PyEval_EvalFrame(PyFrameObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyEval_EvalFrame == NULL) {
        __target__PyEval_EvalFrame = resolveAPI("PyEval_EvalFrame");
    }
    STATS_BEFORE(PyEval_EvalFrame)
    PyObject* result = (PyObject*) __target__PyEval_EvalFrame(a);
    STATS_AFTER(PyEval_EvalFrame)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyEval_EvalFrameEx, PyEval_EvalFrame)
PyObject* (*__target__PyEval_EvalFrameEx)(PyFrameObject*, int) = NULL;
PyAPI_FUNC(PyObject*) PyEval_EvalFrameEx(PyFrameObject* a, int b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target__PyEval_EvalFrameEx == NULL) {
        __target__PyEval_EvalFrameEx = resolveAPI("PyEval_EvalFrameEx");
    }
    STATS_BEFORE(PyEval_EvalFrameEx)
    PyObject* result = (PyObject*) __target__PyEval_EvalFrameEx(a, b);
    STATS_AFTER(PyEval_EvalFrameEx)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyEval_GetBuiltins, PyEval_EvalFrameEx)
PyObject* (*__target__PyEval_GetBuiltins)() = NULL;
PyAPI_FUNC(PyObject*) PyEval_GetBuiltins() {
    LOGS("");
    if (__target__PyEval_GetBuiltins == NULL) {
        __target__PyEval_GetBuiltins = resolveAPI("PyEval_GetBuiltins");
    }
    STATS_BEFORE(PyEval_GetBuiltins)
    PyObject* result = (PyObject*) __target__PyEval_GetBuiltins();
    STATS_AFTER(PyEval_GetBuiltins)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyEval_GetFrame, PyEval_GetBuiltins)
PyFrameObject* (*__target__PyEval_GetFrame)() = NULL;
PyAPI_FUNC(PyFrameObject*) PyEval_GetFrame() {
    LOGS("");
    if (__target__PyEval_GetFrame == NULL) {
        __target__PyEval_GetFrame = resolveAPI("PyEval_GetFrame");
    }
    STATS_BEFORE(PyEval_GetFrame)
    PyFrameObject* result = (PyFrameObject*) __target__PyEval_GetFrame();
    STATS_AFTER(PyEval_GetFrame)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyEval_GetFuncDesc, PyEval_GetFrame)
const char* (*__target__PyEval_GetFuncDesc)(PyObject*) = NULL;
PyAPI_FUNC(const char*) PyEval_GetFuncDesc(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyEval_GetFuncDesc == NULL) {
        __target__PyEval_GetFuncDesc = resolveAPI("PyEval_GetFuncDesc");
    }
    STATS_BEFORE(PyEval_GetFuncDesc)
    const char* result = (const char*) __target__PyEval_GetFuncDesc(a);
    STATS_AFTER(PyEval_GetFuncDesc)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyEval_GetFuncName, PyEval_GetFuncDesc)
const char* (*__target__PyEval_GetFuncName)(PyObject*) = NULL;
PyAPI_FUNC(const char*) PyEval_GetFuncName(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyEval_GetFuncName == NULL) {
        __target__PyEval_GetFuncName = resolveAPI("PyEval_GetFuncName");
    }
    STATS_BEFORE(PyEval_GetFuncName)
    const char* result = (const char*) __target__PyEval_GetFuncName(a);
    STATS_AFTER(PyEval_GetFuncName)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyEval_GetGlobals, PyEval_GetFuncName)
PyObject* (*__target__PyEval_GetGlobals)() = NULL;
PyAPI_FUNC(PyObject*) PyEval_GetGlobals() {
    LOGS("");
    if (__target__PyEval_GetGlobals == NULL) {
        __target__PyEval_GetGlobals = resolveAPI("PyEval_GetGlobals");
    }
    STATS_BEFORE(PyEval_GetGlobals)
    PyObject* result = (PyObject*) __target__PyEval_GetGlobals();
    STATS_AFTER(PyEval_GetGlobals)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyEval_GetLocals, PyEval_GetGlobals)
PyObject* (*__target__PyEval_GetLocals)() = NULL;
PyAPI_FUNC(PyObject*) PyEval_GetLocals() {
    LOGS("");
    if (__target__PyEval_GetLocals == NULL) {
        __target__PyEval_GetLocals = resolveAPI("PyEval_GetLocals");
    }
    STATS_BEFORE(PyEval_GetLocals)
    PyObject* result = (PyObject*) __target__PyEval_GetLocals();
    STATS_AFTER(PyEval_GetLocals)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyEval_InitThreads, PyEval_GetLocals)
void (*__target__PyEval_InitThreads)() = NULL;
PyAPI_FUNC(void) PyEval_InitThreads() {
    LOGS("");
    if (__target__PyEval_InitThreads == NULL) {
        __target__PyEval_InitThreads = resolveAPI("PyEval_InitThreads");
    }
    STATS_BEFORE(PyEval_InitThreads)
    __target__PyEval_InitThreads();
    STATS_AFTER(PyEval_InitThreads)
    LOG_AFTER_VOID
}
STATS_CONTAINER(PyEval_MergeCompilerFlags, PyEval_InitThreads)
int (*__target__PyEval_MergeCompilerFlags)(PyCompilerFlags*) = NULL;
PyAPI_FUNC(int) PyEval_MergeCompilerFlags(PyCompilerFlags* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyEval_MergeCompilerFlags == NULL) {
        __target__PyEval_MergeCompilerFlags = resolveAPI("PyEval_MergeCompilerFlags");
    }
    STATS_BEFORE(PyEval_MergeCompilerFlags)
    int result = (int) __target__PyEval_MergeCompilerFlags(a);
    STATS_AFTER(PyEval_MergeCompilerFlags)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyEval_ReleaseLock, PyEval_MergeCompilerFlags)
void (*__target__PyEval_ReleaseLock)() = NULL;
PyAPI_FUNC(void) PyEval_ReleaseLock() {
    LOGS("");
    if (__target__PyEval_ReleaseLock == NULL) {
        __target__PyEval_ReleaseLock = resolveAPI("PyEval_ReleaseLock");
    }
    STATS_BEFORE(PyEval_ReleaseLock)
    __target__PyEval_ReleaseLock();
    STATS_AFTER(PyEval_ReleaseLock)
    LOG_AFTER_VOID
}
STATS_CONTAINER(PyEval_ReleaseThread, PyEval_ReleaseLock)
void (*__target__PyEval_ReleaseThread)(PyThreadState*) = NULL;
PyAPI_FUNC(void) PyEval_ReleaseThread(PyThreadState* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyEval_ReleaseThread == NULL) {
        __target__PyEval_ReleaseThread = resolveAPI("PyEval_ReleaseThread");
    }
    STATS_BEFORE(PyEval_ReleaseThread)
    __target__PyEval_ReleaseThread(a);
    STATS_AFTER(PyEval_ReleaseThread)
    LOG_AFTER_VOID
}
STATS_CONTAINER(PyEval_RestoreThread, PyEval_ReleaseThread)
void (*__target__PyEval_RestoreThread)(PyThreadState*) = NULL;
PyAPI_FUNC(void) PyEval_RestoreThread(PyThreadState* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyEval_RestoreThread == NULL) {
        __target__PyEval_RestoreThread = resolveAPI("PyEval_RestoreThread");
    }
    STATS_BEFORE(PyEval_RestoreThread)
    __target__PyEval_RestoreThread(a);
    STATS_AFTER(PyEval_RestoreThread)
    LOG_AFTER_VOID
}
STATS_CONTAINER(PyEval_SaveThread, PyEval_RestoreThread)
PyThreadState* (*__target__PyEval_SaveThread)() = NULL;
PyAPI_FUNC(PyThreadState*) PyEval_SaveThread() {
    LOGS("");
    if (__target__PyEval_SaveThread == NULL) {
        __target__PyEval_SaveThread = resolveAPI("PyEval_SaveThread");
    }
    STATS_BEFORE(PyEval_SaveThread)
    PyThreadState* result = (PyThreadState*) __target__PyEval_SaveThread();
    STATS_AFTER(PyEval_SaveThread)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyEval_SetProfile, PyEval_SaveThread)
void (*__target__PyEval_SetProfile)(Py_tracefunc, PyObject*) = NULL;
PyAPI_FUNC(void) PyEval_SetProfile(Py_tracefunc a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target__PyEval_SetProfile == NULL) {
        __target__PyEval_SetProfile = resolveAPI("PyEval_SetProfile");
    }
    STATS_BEFORE(PyEval_SetProfile)
    __target__PyEval_SetProfile(a, b);
    STATS_AFTER(PyEval_SetProfile)
    LOG_AFTER_VOID
}
STATS_CONTAINER(PyEval_SetTrace, PyEval_SetProfile)
void (*__target__PyEval_SetTrace)(Py_tracefunc, PyObject*) = NULL;
PyAPI_FUNC(void) PyEval_SetTrace(Py_tracefunc a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target__PyEval_SetTrace == NULL) {
        __target__PyEval_SetTrace = resolveAPI("PyEval_SetTrace");
    }
    STATS_BEFORE(PyEval_SetTrace)
    __target__PyEval_SetTrace(a, b);
    STATS_AFTER(PyEval_SetTrace)
    LOG_AFTER_VOID
}
STATS_CONTAINER(PyEval_ThreadsInitialized, PyEval_SetTrace)
int (*__target__PyEval_ThreadsInitialized)() = NULL;
PyAPI_FUNC(int) PyEval_ThreadsInitialized() {
    LOGS("");
    if (__target__PyEval_ThreadsInitialized == NULL) {
        __target__PyEval_ThreadsInitialized = resolveAPI("PyEval_ThreadsInitialized");
    }
    STATS_BEFORE(PyEval_ThreadsInitialized)
    int result = (int) __target__PyEval_ThreadsInitialized();
    STATS_AFTER(PyEval_ThreadsInitialized)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyExceptionClass_Name, PyEval_ThreadsInitialized)
const char* (*__target__PyExceptionClass_Name)(PyObject*) = NULL;
PyAPI_FUNC(const char*) PyExceptionClass_Name(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyExceptionClass_Name == NULL) {
        __target__PyExceptionClass_Name = resolveAPI("PyExceptionClass_Name");
    }
    STATS_BEFORE(PyExceptionClass_Name)
    const char* result = (const char*) __target__PyExceptionClass_Name(a);
    STATS_AFTER(PyExceptionClass_Name)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyException_GetCause, PyExceptionClass_Name)
PyObject* (*__target__PyException_GetCause)(PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyException_GetCause(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyException_GetCause == NULL) {
        __target__PyException_GetCause = resolveAPI("PyException_GetCause");
    }
    STATS_BEFORE(PyException_GetCause)
    PyObject* result = (PyObject*) __target__PyException_GetCause(a);
    STATS_AFTER(PyException_GetCause)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyException_GetContext, PyException_GetCause)
PyObject* (*__target__PyException_GetContext)(PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyException_GetContext(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyException_GetContext == NULL) {
        __target__PyException_GetContext = resolveAPI("PyException_GetContext");
    }
    STATS_BEFORE(PyException_GetContext)
    PyObject* result = (PyObject*) __target__PyException_GetContext(a);
    STATS_AFTER(PyException_GetContext)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyException_GetTraceback, PyException_GetContext)
PyObject* (*__target__PyException_GetTraceback)(PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyException_GetTraceback(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyException_GetTraceback == NULL) {
        __target__PyException_GetTraceback = resolveAPI("PyException_GetTraceback");
    }
    STATS_BEFORE(PyException_GetTraceback)
    PyObject* result = (PyObject*) __target__PyException_GetTraceback(a);
    STATS_AFTER(PyException_GetTraceback)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyException_SetCause, PyException_GetTraceback)
void (*__target__PyException_SetCause)(PyObject*, PyObject*) = NULL;
PyAPI_FUNC(void) PyException_SetCause(PyObject* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target__PyException_SetCause == NULL) {
        __target__PyException_SetCause = resolveAPI("PyException_SetCause");
    }
    STATS_BEFORE(PyException_SetCause)
    __target__PyException_SetCause(a, b);
    STATS_AFTER(PyException_SetCause)
    LOG_AFTER_VOID
}
STATS_CONTAINER(PyException_SetContext, PyException_SetCause)
void (*__target__PyException_SetContext)(PyObject*, PyObject*) = NULL;
PyAPI_FUNC(void) PyException_SetContext(PyObject* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target__PyException_SetContext == NULL) {
        __target__PyException_SetContext = resolveAPI("PyException_SetContext");
    }
    STATS_BEFORE(PyException_SetContext)
    __target__PyException_SetContext(a, b);
    STATS_AFTER(PyException_SetContext)
    LOG_AFTER_VOID
}
STATS_CONTAINER(PyException_SetTraceback, PyException_SetContext)
int (*__target__PyException_SetTraceback)(PyObject*, PyObject*) = NULL;
PyAPI_FUNC(int) PyException_SetTraceback(PyObject* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target__PyException_SetTraceback == NULL) {
        __target__PyException_SetTraceback = resolveAPI("PyException_SetTraceback");
    }
    STATS_BEFORE(PyException_SetTraceback)
    int result = (int) __target__PyException_SetTraceback(a, b);
    STATS_AFTER(PyException_SetTraceback)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyFile_FromFd, PyException_SetTraceback)
PyObject* (*__target__PyFile_FromFd)(int, const char*, const char*, int, const char*, const char*, const char*, int) = NULL;
PyAPI_FUNC(PyObject*) PyFile_FromFd(int a, const char* b, const char* c, int d, const char* e, const char* f, const char* g, int h) {
    LOG("0x%lx '%s'(0x%lx) '%s'(0x%lx) 0x%lx '%s'(0x%lx) '%s'(0x%lx) '%s'(0x%lx) 0x%lx", (unsigned long) a, b?b:"<null>", (unsigned long) b, c?c:"<null>", (unsigned long) c, (unsigned long) d, e?e:"<null>", (unsigned long) e, f?f:"<null>", (unsigned long) f, g?g:"<null>", (unsigned long) g, (unsigned long) h);
    if (__target__PyFile_FromFd == NULL) {
        __target__PyFile_FromFd = resolveAPI("PyFile_FromFd");
    }
    STATS_BEFORE(PyFile_FromFd)
    PyObject* result = (PyObject*) __target__PyFile_FromFd(a, b, c, d, e, f, g, h);
    STATS_AFTER(PyFile_FromFd)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyFile_GetLine, PyFile_FromFd)
PyObject* (*__target__PyFile_GetLine)(PyObject*, int) = NULL;
PyAPI_FUNC(PyObject*) PyFile_GetLine(PyObject* a, int b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target__PyFile_GetLine == NULL) {
        __target__PyFile_GetLine = resolveAPI("PyFile_GetLine");
    }
    STATS_BEFORE(PyFile_GetLine)
    PyObject* result = (PyObject*) __target__PyFile_GetLine(a, b);
    STATS_AFTER(PyFile_GetLine)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyFile_NewStdPrinter, PyFile_GetLine)
PyObject* (*__target__PyFile_NewStdPrinter)(int) = NULL;
PyAPI_FUNC(PyObject*) PyFile_NewStdPrinter(int a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyFile_NewStdPrinter == NULL) {
        __target__PyFile_NewStdPrinter = resolveAPI("PyFile_NewStdPrinter");
    }
    STATS_BEFORE(PyFile_NewStdPrinter)
    PyObject* result = (PyObject*) __target__PyFile_NewStdPrinter(a);
    STATS_AFTER(PyFile_NewStdPrinter)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyFile_OpenCode, PyFile_NewStdPrinter)
PyObject* (*__target__PyFile_OpenCode)(const char*) = NULL;
PyAPI_FUNC(PyObject*) PyFile_OpenCode(const char* a) {
    LOG("'%s'(0x%lx)", a?a:"<null>", (unsigned long) a);
    if (__target__PyFile_OpenCode == NULL) {
        __target__PyFile_OpenCode = resolveAPI("PyFile_OpenCode");
    }
    STATS_BEFORE(PyFile_OpenCode)
    PyObject* result = (PyObject*) __target__PyFile_OpenCode(a);
    STATS_AFTER(PyFile_OpenCode)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyFile_OpenCodeObject, PyFile_OpenCode)
PyObject* (*__target__PyFile_OpenCodeObject)(PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyFile_OpenCodeObject(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyFile_OpenCodeObject == NULL) {
        __target__PyFile_OpenCodeObject = resolveAPI("PyFile_OpenCodeObject");
    }
    STATS_BEFORE(PyFile_OpenCodeObject)
    PyObject* result = (PyObject*) __target__PyFile_OpenCodeObject(a);
    STATS_AFTER(PyFile_OpenCodeObject)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyFile_SetOpenCodeHook, PyFile_OpenCodeObject)
int (*__target__PyFile_SetOpenCodeHook)(Py_OpenCodeHookFunction, void*) = NULL;
PyAPI_FUNC(int) PyFile_SetOpenCodeHook(Py_OpenCodeHookFunction a, void* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target__PyFile_SetOpenCodeHook == NULL) {
        __target__PyFile_SetOpenCodeHook = resolveAPI("PyFile_SetOpenCodeHook");
    }
    STATS_BEFORE(PyFile_SetOpenCodeHook)
    int result = (int) __target__PyFile_SetOpenCodeHook(a, b);
    STATS_AFTER(PyFile_SetOpenCodeHook)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyFile_WriteObject, PyFile_SetOpenCodeHook)
int (*__target__PyFile_WriteObject)(PyObject*, PyObject*, int) = NULL;
PyAPI_FUNC(int) PyFile_WriteObject(PyObject* a, PyObject* b, int c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    if (__target__PyFile_WriteObject == NULL) {
        __target__PyFile_WriteObject = resolveAPI("PyFile_WriteObject");
    }
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
    if (__target__PyFile_WriteString == NULL) {
        __target__PyFile_WriteString = resolveAPI("PyFile_WriteString");
    }
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
    if (__target__PyFloat_AsDouble == NULL) {
        __target__PyFloat_AsDouble = resolveAPI("PyFloat_AsDouble");
    }
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
    if (__target__PyFloat_FromDouble == NULL) {
        __target__PyFloat_FromDouble = resolveAPI("PyFloat_FromDouble");
    }
    STATS_BEFORE(PyFloat_FromDouble)
    PyObject* result = (PyObject*) __target__PyFloat_FromDouble(a);
    STATS_AFTER(PyFloat_FromDouble)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyFloat_FromString, PyFloat_FromDouble)
PyObject* (*__target__PyFloat_FromString)(PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyFloat_FromString(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyFloat_FromString == NULL) {
        __target__PyFloat_FromString = resolveAPI("PyFloat_FromString");
    }
    STATS_BEFORE(PyFloat_FromString)
    PyObject* result = (PyObject*) __target__PyFloat_FromString(a);
    STATS_AFTER(PyFloat_FromString)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyFloat_GetInfo, PyFloat_FromString)
PyObject* (*__target__PyFloat_GetInfo)() = NULL;
PyAPI_FUNC(PyObject*) PyFloat_GetInfo() {
    LOGS("");
    if (__target__PyFloat_GetInfo == NULL) {
        __target__PyFloat_GetInfo = resolveAPI("PyFloat_GetInfo");
    }
    STATS_BEFORE(PyFloat_GetInfo)
    PyObject* result = (PyObject*) __target__PyFloat_GetInfo();
    STATS_AFTER(PyFloat_GetInfo)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyFloat_GetMax, PyFloat_GetInfo)
double (*__target__PyFloat_GetMax)() = NULL;
PyAPI_FUNC(double) PyFloat_GetMax() {
    LOGS("");
    if (__target__PyFloat_GetMax == NULL) {
        __target__PyFloat_GetMax = resolveAPI("PyFloat_GetMax");
    }
    STATS_BEFORE(PyFloat_GetMax)
    double result = (double) __target__PyFloat_GetMax();
    STATS_AFTER(PyFloat_GetMax)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyFloat_GetMin, PyFloat_GetMax)
double (*__target__PyFloat_GetMin)() = NULL;
PyAPI_FUNC(double) PyFloat_GetMin() {
    LOGS("");
    if (__target__PyFloat_GetMin == NULL) {
        __target__PyFloat_GetMin = resolveAPI("PyFloat_GetMin");
    }
    STATS_BEFORE(PyFloat_GetMin)
    double result = (double) __target__PyFloat_GetMin();
    STATS_AFTER(PyFloat_GetMin)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyFrame_BlockPop, PyFloat_GetMin)
PyTryBlock* (*__target__PyFrame_BlockPop)(PyFrameObject*) = NULL;
PyAPI_FUNC(PyTryBlock*) PyFrame_BlockPop(PyFrameObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyFrame_BlockPop == NULL) {
        __target__PyFrame_BlockPop = resolveAPI("PyFrame_BlockPop");
    }
    STATS_BEFORE(PyFrame_BlockPop)
    PyTryBlock* result = (PyTryBlock*) __target__PyFrame_BlockPop(a);
    STATS_AFTER(PyFrame_BlockPop)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyFrame_BlockSetup, PyFrame_BlockPop)
void (*__target__PyFrame_BlockSetup)(PyFrameObject*, int, int, int) = NULL;
PyAPI_FUNC(void) PyFrame_BlockSetup(PyFrameObject* a, int b, int c, int d) {
    LOG("0x%lx 0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c, (unsigned long) d);
    if (__target__PyFrame_BlockSetup == NULL) {
        __target__PyFrame_BlockSetup = resolveAPI("PyFrame_BlockSetup");
    }
    STATS_BEFORE(PyFrame_BlockSetup)
    __target__PyFrame_BlockSetup(a, b, c, d);
    STATS_AFTER(PyFrame_BlockSetup)
    LOG_AFTER_VOID
}
STATS_CONTAINER(PyFrame_FastToLocals, PyFrame_BlockSetup)
void (*__target__PyFrame_FastToLocals)(PyFrameObject*) = NULL;
PyAPI_FUNC(void) PyFrame_FastToLocals(PyFrameObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyFrame_FastToLocals == NULL) {
        __target__PyFrame_FastToLocals = resolveAPI("PyFrame_FastToLocals");
    }
    STATS_BEFORE(PyFrame_FastToLocals)
    __target__PyFrame_FastToLocals(a);
    STATS_AFTER(PyFrame_FastToLocals)
    LOG_AFTER_VOID
}
STATS_CONTAINER(PyFrame_FastToLocalsWithError, PyFrame_FastToLocals)
int (*__target__PyFrame_FastToLocalsWithError)(PyFrameObject*) = NULL;
PyAPI_FUNC(int) PyFrame_FastToLocalsWithError(PyFrameObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyFrame_FastToLocalsWithError == NULL) {
        __target__PyFrame_FastToLocalsWithError = resolveAPI("PyFrame_FastToLocalsWithError");
    }
    STATS_BEFORE(PyFrame_FastToLocalsWithError)
    int result = (int) __target__PyFrame_FastToLocalsWithError(a);
    STATS_AFTER(PyFrame_FastToLocalsWithError)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyFrame_GetBack, PyFrame_FastToLocalsWithError)
PyFrameObject* (*__target__PyFrame_GetBack)(PyFrameObject*) = NULL;
PyAPI_FUNC(PyFrameObject*) PyFrame_GetBack(PyFrameObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyFrame_GetBack == NULL) {
        __target__PyFrame_GetBack = resolveAPI("PyFrame_GetBack");
    }
    STATS_BEFORE(PyFrame_GetBack)
    PyFrameObject* result = (PyFrameObject*) __target__PyFrame_GetBack(a);
    STATS_AFTER(PyFrame_GetBack)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyFrame_GetCode, PyFrame_GetBack)
PyCodeObject* (*__target__PyFrame_GetCode)(PyFrameObject*) = NULL;
PyAPI_FUNC(PyCodeObject*) PyFrame_GetCode(PyFrameObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyFrame_GetCode == NULL) {
        __target__PyFrame_GetCode = resolveAPI("PyFrame_GetCode");
    }
    STATS_BEFORE(PyFrame_GetCode)
    PyCodeObject* result = (PyCodeObject*) __target__PyFrame_GetCode(a);
    STATS_AFTER(PyFrame_GetCode)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyFrame_GetLineNumber, PyFrame_GetCode)
int (*__target__PyFrame_GetLineNumber)(PyFrameObject*) = NULL;
PyAPI_FUNC(int) PyFrame_GetLineNumber(PyFrameObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyFrame_GetLineNumber == NULL) {
        __target__PyFrame_GetLineNumber = resolveAPI("PyFrame_GetLineNumber");
    }
    STATS_BEFORE(PyFrame_GetLineNumber)
    int result = (int) __target__PyFrame_GetLineNumber(a);
    STATS_AFTER(PyFrame_GetLineNumber)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyFrame_LocalsToFast, PyFrame_GetLineNumber)
void (*__target__PyFrame_LocalsToFast)(PyFrameObject*, int) = NULL;
PyAPI_FUNC(void) PyFrame_LocalsToFast(PyFrameObject* a, int b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target__PyFrame_LocalsToFast == NULL) {
        __target__PyFrame_LocalsToFast = resolveAPI("PyFrame_LocalsToFast");
    }
    STATS_BEFORE(PyFrame_LocalsToFast)
    __target__PyFrame_LocalsToFast(a, b);
    STATS_AFTER(PyFrame_LocalsToFast)
    LOG_AFTER_VOID
}
STATS_CONTAINER(PyFrame_New, PyFrame_LocalsToFast)
PyFrameObject* (*__target__PyFrame_New)(PyThreadState*, PyCodeObject*, PyObject*, PyObject*) = NULL;
PyAPI_FUNC(PyFrameObject*) PyFrame_New(PyThreadState* a, PyCodeObject* b, PyObject* c, PyObject* d) {
    LOG("0x%lx 0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c, (unsigned long) d);
    if (__target__PyFrame_New == NULL) {
        __target__PyFrame_New = resolveAPI("PyFrame_New");
    }
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
    if (__target__PyFrozenSet_New == NULL) {
        __target__PyFrozenSet_New = resolveAPI("PyFrozenSet_New");
    }
    STATS_BEFORE(PyFrozenSet_New)
    PyObject* result = (PyObject*) __target__PyFrozenSet_New(a);
    STATS_AFTER(PyFrozenSet_New)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyFunction_GetAnnotations, PyFrozenSet_New)
PyObject* (*__target__PyFunction_GetAnnotations)(PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyFunction_GetAnnotations(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyFunction_GetAnnotations == NULL) {
        __target__PyFunction_GetAnnotations = resolveAPI("PyFunction_GetAnnotations");
    }
    STATS_BEFORE(PyFunction_GetAnnotations)
    PyObject* result = (PyObject*) __target__PyFunction_GetAnnotations(a);
    STATS_AFTER(PyFunction_GetAnnotations)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyFunction_GetClosure, PyFunction_GetAnnotations)
PyObject* (*__target__PyFunction_GetClosure)(PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyFunction_GetClosure(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyFunction_GetClosure == NULL) {
        __target__PyFunction_GetClosure = resolveAPI("PyFunction_GetClosure");
    }
    STATS_BEFORE(PyFunction_GetClosure)
    PyObject* result = (PyObject*) __target__PyFunction_GetClosure(a);
    STATS_AFTER(PyFunction_GetClosure)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyFunction_GetCode, PyFunction_GetClosure)
PyObject* (*__target__PyFunction_GetCode)(PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyFunction_GetCode(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyFunction_GetCode == NULL) {
        __target__PyFunction_GetCode = resolveAPI("PyFunction_GetCode");
    }
    STATS_BEFORE(PyFunction_GetCode)
    PyObject* result = (PyObject*) __target__PyFunction_GetCode(a);
    STATS_AFTER(PyFunction_GetCode)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyFunction_GetDefaults, PyFunction_GetCode)
PyObject* (*__target__PyFunction_GetDefaults)(PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyFunction_GetDefaults(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyFunction_GetDefaults == NULL) {
        __target__PyFunction_GetDefaults = resolveAPI("PyFunction_GetDefaults");
    }
    STATS_BEFORE(PyFunction_GetDefaults)
    PyObject* result = (PyObject*) __target__PyFunction_GetDefaults(a);
    STATS_AFTER(PyFunction_GetDefaults)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyFunction_GetGlobals, PyFunction_GetDefaults)
PyObject* (*__target__PyFunction_GetGlobals)(PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyFunction_GetGlobals(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyFunction_GetGlobals == NULL) {
        __target__PyFunction_GetGlobals = resolveAPI("PyFunction_GetGlobals");
    }
    STATS_BEFORE(PyFunction_GetGlobals)
    PyObject* result = (PyObject*) __target__PyFunction_GetGlobals(a);
    STATS_AFTER(PyFunction_GetGlobals)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyFunction_GetKwDefaults, PyFunction_GetGlobals)
PyObject* (*__target__PyFunction_GetKwDefaults)(PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyFunction_GetKwDefaults(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyFunction_GetKwDefaults == NULL) {
        __target__PyFunction_GetKwDefaults = resolveAPI("PyFunction_GetKwDefaults");
    }
    STATS_BEFORE(PyFunction_GetKwDefaults)
    PyObject* result = (PyObject*) __target__PyFunction_GetKwDefaults(a);
    STATS_AFTER(PyFunction_GetKwDefaults)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyFunction_GetModule, PyFunction_GetKwDefaults)
PyObject* (*__target__PyFunction_GetModule)(PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyFunction_GetModule(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyFunction_GetModule == NULL) {
        __target__PyFunction_GetModule = resolveAPI("PyFunction_GetModule");
    }
    STATS_BEFORE(PyFunction_GetModule)
    PyObject* result = (PyObject*) __target__PyFunction_GetModule(a);
    STATS_AFTER(PyFunction_GetModule)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyFunction_New, PyFunction_GetModule)
PyObject* (*__target__PyFunction_New)(PyObject*, PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyFunction_New(PyObject* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target__PyFunction_New == NULL) {
        __target__PyFunction_New = resolveAPI("PyFunction_New");
    }
    STATS_BEFORE(PyFunction_New)
    PyObject* result = (PyObject*) __target__PyFunction_New(a, b);
    STATS_AFTER(PyFunction_New)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyFunction_NewWithQualName, PyFunction_New)
PyObject* (*__target__PyFunction_NewWithQualName)(PyObject*, PyObject*, PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyFunction_NewWithQualName(PyObject* a, PyObject* b, PyObject* c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    if (__target__PyFunction_NewWithQualName == NULL) {
        __target__PyFunction_NewWithQualName = resolveAPI("PyFunction_NewWithQualName");
    }
    STATS_BEFORE(PyFunction_NewWithQualName)
    PyObject* result = (PyObject*) __target__PyFunction_NewWithQualName(a, b, c);
    STATS_AFTER(PyFunction_NewWithQualName)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyFunction_SetAnnotations, PyFunction_NewWithQualName)
int (*__target__PyFunction_SetAnnotations)(PyObject*, PyObject*) = NULL;
PyAPI_FUNC(int) PyFunction_SetAnnotations(PyObject* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target__PyFunction_SetAnnotations == NULL) {
        __target__PyFunction_SetAnnotations = resolveAPI("PyFunction_SetAnnotations");
    }
    STATS_BEFORE(PyFunction_SetAnnotations)
    int result = (int) __target__PyFunction_SetAnnotations(a, b);
    STATS_AFTER(PyFunction_SetAnnotations)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyFunction_SetClosure, PyFunction_SetAnnotations)
int (*__target__PyFunction_SetClosure)(PyObject*, PyObject*) = NULL;
PyAPI_FUNC(int) PyFunction_SetClosure(PyObject* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target__PyFunction_SetClosure == NULL) {
        __target__PyFunction_SetClosure = resolveAPI("PyFunction_SetClosure");
    }
    STATS_BEFORE(PyFunction_SetClosure)
    int result = (int) __target__PyFunction_SetClosure(a, b);
    STATS_AFTER(PyFunction_SetClosure)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyFunction_SetDefaults, PyFunction_SetClosure)
int (*__target__PyFunction_SetDefaults)(PyObject*, PyObject*) = NULL;
PyAPI_FUNC(int) PyFunction_SetDefaults(PyObject* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target__PyFunction_SetDefaults == NULL) {
        __target__PyFunction_SetDefaults = resolveAPI("PyFunction_SetDefaults");
    }
    STATS_BEFORE(PyFunction_SetDefaults)
    int result = (int) __target__PyFunction_SetDefaults(a, b);
    STATS_AFTER(PyFunction_SetDefaults)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyFunction_SetKwDefaults, PyFunction_SetDefaults)
int (*__target__PyFunction_SetKwDefaults)(PyObject*, PyObject*) = NULL;
PyAPI_FUNC(int) PyFunction_SetKwDefaults(PyObject* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target__PyFunction_SetKwDefaults == NULL) {
        __target__PyFunction_SetKwDefaults = resolveAPI("PyFunction_SetKwDefaults");
    }
    STATS_BEFORE(PyFunction_SetKwDefaults)
    int result = (int) __target__PyFunction_SetKwDefaults(a, b);
    STATS_AFTER(PyFunction_SetKwDefaults)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyGC_Collect, PyFunction_SetKwDefaults)
Py_ssize_t (*__target__PyGC_Collect)() = NULL;
PyAPI_FUNC(Py_ssize_t) PyGC_Collect() {
    LOGS("");
    if (__target__PyGC_Collect == NULL) {
        __target__PyGC_Collect = resolveAPI("PyGC_Collect");
    }
    STATS_BEFORE(PyGC_Collect)
    Py_ssize_t result = (Py_ssize_t) __target__PyGC_Collect();
    STATS_AFTER(PyGC_Collect)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyGC_Disable, PyGC_Collect)
int (*__target__PyGC_Disable)() = NULL;
PyAPI_FUNC(int) PyGC_Disable() {
    LOGS("");
    if (__target__PyGC_Disable == NULL) {
        __target__PyGC_Disable = resolveAPI("PyGC_Disable");
    }
    STATS_BEFORE(PyGC_Disable)
    int result = (int) __target__PyGC_Disable();
    STATS_AFTER(PyGC_Disable)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyGC_Enable, PyGC_Disable)
int (*__target__PyGC_Enable)() = NULL;
PyAPI_FUNC(int) PyGC_Enable() {
    LOGS("");
    if (__target__PyGC_Enable == NULL) {
        __target__PyGC_Enable = resolveAPI("PyGC_Enable");
    }
    STATS_BEFORE(PyGC_Enable)
    int result = (int) __target__PyGC_Enable();
    STATS_AFTER(PyGC_Enable)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyGC_IsEnabled, PyGC_Enable)
int (*__target__PyGC_IsEnabled)() = NULL;
PyAPI_FUNC(int) PyGC_IsEnabled() {
    LOGS("");
    if (__target__PyGC_IsEnabled == NULL) {
        __target__PyGC_IsEnabled = resolveAPI("PyGC_IsEnabled");
    }
    STATS_BEFORE(PyGC_IsEnabled)
    int result = (int) __target__PyGC_IsEnabled();
    STATS_AFTER(PyGC_IsEnabled)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyGILState_Check, PyGC_IsEnabled)
int (*__target__PyGILState_Check)() = NULL;
PyAPI_FUNC(int) PyGILState_Check() {
    LOGS("");
    if (__target__PyGILState_Check == NULL) {
        __target__PyGILState_Check = resolveAPI("PyGILState_Check");
    }
    STATS_BEFORE(PyGILState_Check)
    int result = (int) __target__PyGILState_Check();
    STATS_AFTER(PyGILState_Check)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyGILState_Ensure, PyGILState_Check)
PyGILState_STATE (*__target__PyGILState_Ensure)() = NULL;
PyAPI_FUNC(PyGILState_STATE) PyGILState_Ensure() {
    LOGS("");
    if (__target__PyGILState_Ensure == NULL) {
        __target__PyGILState_Ensure = resolveAPI("PyGILState_Ensure");
    }
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
    if (__target__PyGILState_GetThisThreadState == NULL) {
        __target__PyGILState_GetThisThreadState = resolveAPI("PyGILState_GetThisThreadState");
    }
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
    if (__target__PyGILState_Release == NULL) {
        __target__PyGILState_Release = resolveAPI("PyGILState_Release");
    }
    STATS_BEFORE(PyGILState_Release)
    __target__PyGILState_Release(a);
    STATS_AFTER(PyGILState_Release)
    LOG_AFTER_VOID
}
STATS_CONTAINER(PyGen_New, PyGILState_Release)
PyObject* (*__target__PyGen_New)(PyFrameObject*) = NULL;
PyAPI_FUNC(PyObject*) PyGen_New(PyFrameObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyGen_New == NULL) {
        __target__PyGen_New = resolveAPI("PyGen_New");
    }
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
    if (__target__PyGen_NewWithQualName == NULL) {
        __target__PyGen_NewWithQualName = resolveAPI("PyGen_NewWithQualName");
    }
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
    if (__target__PyImport_AddModule == NULL) {
        __target__PyImport_AddModule = resolveAPI("PyImport_AddModule");
    }
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
    if (__target__PyImport_AddModuleObject == NULL) {
        __target__PyImport_AddModuleObject = resolveAPI("PyImport_AddModuleObject");
    }
    STATS_BEFORE(PyImport_AddModuleObject)
    PyObject* result = (PyObject*) __target__PyImport_AddModuleObject(a);
    STATS_AFTER(PyImport_AddModuleObject)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyImport_AppendInittab, PyImport_AddModuleObject)
int (*__target__PyImport_AppendInittab)(const char*, PyObject*(*)(void)) = NULL;
PyAPI_FUNC(int) PyImport_AppendInittab(const char* a, PyObject*(*b)(void)) {
    LOG("'%s'(0x%lx) 0x%lx", a?a:"<null>", (unsigned long) a, (unsigned long) b);
    if (__target__PyImport_AppendInittab == NULL) {
        __target__PyImport_AppendInittab = resolveAPI("PyImport_AppendInittab");
    }
    STATS_BEFORE(PyImport_AppendInittab)
    int result = (int) __target__PyImport_AppendInittab(a, b);
    STATS_AFTER(PyImport_AppendInittab)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyImport_ExecCodeModule, PyImport_AppendInittab)
PyObject* (*__target__PyImport_ExecCodeModule)(const char*, PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyImport_ExecCodeModule(const char* a, PyObject* b) {
    LOG("'%s'(0x%lx) 0x%lx", a?a:"<null>", (unsigned long) a, (unsigned long) b);
    if (__target__PyImport_ExecCodeModule == NULL) {
        __target__PyImport_ExecCodeModule = resolveAPI("PyImport_ExecCodeModule");
    }
    STATS_BEFORE(PyImport_ExecCodeModule)
    PyObject* result = (PyObject*) __target__PyImport_ExecCodeModule(a, b);
    STATS_AFTER(PyImport_ExecCodeModule)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyImport_ExecCodeModuleEx, PyImport_ExecCodeModule)
PyObject* (*__target__PyImport_ExecCodeModuleEx)(const char*, PyObject*, const char*) = NULL;
PyAPI_FUNC(PyObject*) PyImport_ExecCodeModuleEx(const char* a, PyObject* b, const char* c) {
    LOG("'%s'(0x%lx) 0x%lx '%s'(0x%lx)", a?a:"<null>", (unsigned long) a, (unsigned long) b, c?c:"<null>", (unsigned long) c);
    if (__target__PyImport_ExecCodeModuleEx == NULL) {
        __target__PyImport_ExecCodeModuleEx = resolveAPI("PyImport_ExecCodeModuleEx");
    }
    STATS_BEFORE(PyImport_ExecCodeModuleEx)
    PyObject* result = (PyObject*) __target__PyImport_ExecCodeModuleEx(a, b, c);
    STATS_AFTER(PyImport_ExecCodeModuleEx)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyImport_ExecCodeModuleObject, PyImport_ExecCodeModuleEx)
PyObject* (*__target__PyImport_ExecCodeModuleObject)(PyObject*, PyObject*, PyObject*, PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyImport_ExecCodeModuleObject(PyObject* a, PyObject* b, PyObject* c, PyObject* d) {
    LOG("0x%lx 0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c, (unsigned long) d);
    if (__target__PyImport_ExecCodeModuleObject == NULL) {
        __target__PyImport_ExecCodeModuleObject = resolveAPI("PyImport_ExecCodeModuleObject");
    }
    STATS_BEFORE(PyImport_ExecCodeModuleObject)
    PyObject* result = (PyObject*) __target__PyImport_ExecCodeModuleObject(a, b, c, d);
    STATS_AFTER(PyImport_ExecCodeModuleObject)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyImport_ExecCodeModuleWithPathnames, PyImport_ExecCodeModuleObject)
PyObject* (*__target__PyImport_ExecCodeModuleWithPathnames)(const char*, PyObject*, const char*, const char*) = NULL;
PyAPI_FUNC(PyObject*) PyImport_ExecCodeModuleWithPathnames(const char* a, PyObject* b, const char* c, const char* d) {
    LOG("'%s'(0x%lx) 0x%lx '%s'(0x%lx) '%s'(0x%lx)", a?a:"<null>", (unsigned long) a, (unsigned long) b, c?c:"<null>", (unsigned long) c, d?d:"<null>", (unsigned long) d);
    if (__target__PyImport_ExecCodeModuleWithPathnames == NULL) {
        __target__PyImport_ExecCodeModuleWithPathnames = resolveAPI("PyImport_ExecCodeModuleWithPathnames");
    }
    STATS_BEFORE(PyImport_ExecCodeModuleWithPathnames)
    PyObject* result = (PyObject*) __target__PyImport_ExecCodeModuleWithPathnames(a, b, c, d);
    STATS_AFTER(PyImport_ExecCodeModuleWithPathnames)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyImport_ExtendInittab, PyImport_ExecCodeModuleWithPathnames)
int (*__target__PyImport_ExtendInittab)(struct _inittab*) = NULL;
PyAPI_FUNC(int) PyImport_ExtendInittab(struct _inittab* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyImport_ExtendInittab == NULL) {
        __target__PyImport_ExtendInittab = resolveAPI("PyImport_ExtendInittab");
    }
    STATS_BEFORE(PyImport_ExtendInittab)
    int result = (int) __target__PyImport_ExtendInittab(a);
    STATS_AFTER(PyImport_ExtendInittab)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyImport_GetImporter, PyImport_ExtendInittab)
PyObject* (*__target__PyImport_GetImporter)(PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyImport_GetImporter(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyImport_GetImporter == NULL) {
        __target__PyImport_GetImporter = resolveAPI("PyImport_GetImporter");
    }
    STATS_BEFORE(PyImport_GetImporter)
    PyObject* result = (PyObject*) __target__PyImport_GetImporter(a);
    STATS_AFTER(PyImport_GetImporter)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyImport_GetMagicNumber, PyImport_GetImporter)
long (*__target__PyImport_GetMagicNumber)() = NULL;
PyAPI_FUNC(long) PyImport_GetMagicNumber() {
    LOGS("");
    if (__target__PyImport_GetMagicNumber == NULL) {
        __target__PyImport_GetMagicNumber = resolveAPI("PyImport_GetMagicNumber");
    }
    STATS_BEFORE(PyImport_GetMagicNumber)
    long result = (long) __target__PyImport_GetMagicNumber();
    STATS_AFTER(PyImport_GetMagicNumber)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyImport_GetMagicTag, PyImport_GetMagicNumber)
const char* (*__target__PyImport_GetMagicTag)() = NULL;
PyAPI_FUNC(const char*) PyImport_GetMagicTag() {
    LOGS("");
    if (__target__PyImport_GetMagicTag == NULL) {
        __target__PyImport_GetMagicTag = resolveAPI("PyImport_GetMagicTag");
    }
    STATS_BEFORE(PyImport_GetMagicTag)
    const char* result = (const char*) __target__PyImport_GetMagicTag();
    STATS_AFTER(PyImport_GetMagicTag)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyImport_GetModule, PyImport_GetMagicTag)
PyObject* (*__target__PyImport_GetModule)(PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyImport_GetModule(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyImport_GetModule == NULL) {
        __target__PyImport_GetModule = resolveAPI("PyImport_GetModule");
    }
    STATS_BEFORE(PyImport_GetModule)
    PyObject* result = (PyObject*) __target__PyImport_GetModule(a);
    STATS_AFTER(PyImport_GetModule)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyImport_GetModuleDict, PyImport_GetModule)
PyObject* (*__target__PyImport_GetModuleDict)() = NULL;
PyAPI_FUNC(PyObject*) PyImport_GetModuleDict() {
    LOGS("");
    if (__target__PyImport_GetModuleDict == NULL) {
        __target__PyImport_GetModuleDict = resolveAPI("PyImport_GetModuleDict");
    }
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
    if (__target__PyImport_Import == NULL) {
        __target__PyImport_Import = resolveAPI("PyImport_Import");
    }
    STATS_BEFORE(PyImport_Import)
    PyObject* result = (PyObject*) __target__PyImport_Import(a);
    STATS_AFTER(PyImport_Import)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyImport_ImportFrozenModule, PyImport_Import)
int (*__target__PyImport_ImportFrozenModule)(const char*) = NULL;
PyAPI_FUNC(int) PyImport_ImportFrozenModule(const char* a) {
    LOG("'%s'(0x%lx)", a?a:"<null>", (unsigned long) a);
    if (__target__PyImport_ImportFrozenModule == NULL) {
        __target__PyImport_ImportFrozenModule = resolveAPI("PyImport_ImportFrozenModule");
    }
    STATS_BEFORE(PyImport_ImportFrozenModule)
    int result = (int) __target__PyImport_ImportFrozenModule(a);
    STATS_AFTER(PyImport_ImportFrozenModule)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyImport_ImportFrozenModuleObject, PyImport_ImportFrozenModule)
int (*__target__PyImport_ImportFrozenModuleObject)(PyObject*) = NULL;
PyAPI_FUNC(int) PyImport_ImportFrozenModuleObject(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyImport_ImportFrozenModuleObject == NULL) {
        __target__PyImport_ImportFrozenModuleObject = resolveAPI("PyImport_ImportFrozenModuleObject");
    }
    STATS_BEFORE(PyImport_ImportFrozenModuleObject)
    int result = (int) __target__PyImport_ImportFrozenModuleObject(a);
    STATS_AFTER(PyImport_ImportFrozenModuleObject)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyImport_ImportModule, PyImport_ImportFrozenModuleObject)
PyObject* (*__target__PyImport_ImportModule)(const char*) = NULL;
PyAPI_FUNC(PyObject*) PyImport_ImportModule(const char* a) {
    LOG("'%s'(0x%lx)", a?a:"<null>", (unsigned long) a);
    if (__target__PyImport_ImportModule == NULL) {
        __target__PyImport_ImportModule = resolveAPI("PyImport_ImportModule");
    }
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
    if (__target__PyImport_ImportModuleLevel == NULL) {
        __target__PyImport_ImportModuleLevel = resolveAPI("PyImport_ImportModuleLevel");
    }
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
    if (__target__PyImport_ImportModuleLevelObject == NULL) {
        __target__PyImport_ImportModuleLevelObject = resolveAPI("PyImport_ImportModuleLevelObject");
    }
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
    if (__target__PyImport_ImportModuleNoBlock == NULL) {
        __target__PyImport_ImportModuleNoBlock = resolveAPI("PyImport_ImportModuleNoBlock");
    }
    STATS_BEFORE(PyImport_ImportModuleNoBlock)
    PyObject* result = (PyObject*) __target__PyImport_ImportModuleNoBlock(a);
    STATS_AFTER(PyImport_ImportModuleNoBlock)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyImport_ReloadModule, PyImport_ImportModuleNoBlock)
PyObject* (*__target__PyImport_ReloadModule)(PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyImport_ReloadModule(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyImport_ReloadModule == NULL) {
        __target__PyImport_ReloadModule = resolveAPI("PyImport_ReloadModule");
    }
    STATS_BEFORE(PyImport_ReloadModule)
    PyObject* result = (PyObject*) __target__PyImport_ReloadModule(a);
    STATS_AFTER(PyImport_ReloadModule)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyIndex_Check, PyImport_ReloadModule)
int (*__target__PyIndex_Check)(PyObject*) = NULL;
PyAPI_FUNC(int) PyIndex_Check(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyIndex_Check == NULL) {
        __target__PyIndex_Check = resolveAPI("PyIndex_Check");
    }
    STATS_BEFORE(PyIndex_Check)
    int result = (int) __target__PyIndex_Check(a);
    STATS_AFTER(PyIndex_Check)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyInit__imp, PyIndex_Check)
PyObject* (*__target__PyInit__imp)() = NULL;
PyAPI_FUNC(PyObject*) PyInit__imp() {
    LOGS("");
    if (__target__PyInit__imp == NULL) {
        __target__PyInit__imp = resolveAPI("PyInit__imp");
    }
    STATS_BEFORE(PyInit__imp)
    PyObject* result = (PyObject*) __target__PyInit__imp();
    STATS_AFTER(PyInit__imp)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyInstanceMethod_Function, PyInit__imp)
PyObject* (*__target__PyInstanceMethod_Function)(PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyInstanceMethod_Function(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyInstanceMethod_Function == NULL) {
        __target__PyInstanceMethod_Function = resolveAPI("PyInstanceMethod_Function");
    }
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
    if (__target__PyInstanceMethod_New == NULL) {
        __target__PyInstanceMethod_New = resolveAPI("PyInstanceMethod_New");
    }
    STATS_BEFORE(PyInstanceMethod_New)
    PyObject* result = (PyObject*) __target__PyInstanceMethod_New(a);
    STATS_AFTER(PyInstanceMethod_New)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyInterpreterState_Clear, PyInstanceMethod_New)
void (*__target__PyInterpreterState_Clear)(PyInterpreterState*) = NULL;
PyAPI_FUNC(void) PyInterpreterState_Clear(PyInterpreterState* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyInterpreterState_Clear == NULL) {
        __target__PyInterpreterState_Clear = resolveAPI("PyInterpreterState_Clear");
    }
    STATS_BEFORE(PyInterpreterState_Clear)
    __target__PyInterpreterState_Clear(a);
    STATS_AFTER(PyInterpreterState_Clear)
    LOG_AFTER_VOID
}
STATS_CONTAINER(PyInterpreterState_Delete, PyInterpreterState_Clear)
void (*__target__PyInterpreterState_Delete)(PyInterpreterState*) = NULL;
PyAPI_FUNC(void) PyInterpreterState_Delete(PyInterpreterState* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyInterpreterState_Delete == NULL) {
        __target__PyInterpreterState_Delete = resolveAPI("PyInterpreterState_Delete");
    }
    STATS_BEFORE(PyInterpreterState_Delete)
    __target__PyInterpreterState_Delete(a);
    STATS_AFTER(PyInterpreterState_Delete)
    LOG_AFTER_VOID
}
STATS_CONTAINER(PyInterpreterState_Get, PyInterpreterState_Delete)
PyInterpreterState* (*__target__PyInterpreterState_Get)() = NULL;
PyAPI_FUNC(PyInterpreterState*) PyInterpreterState_Get() {
    LOGS("");
    if (__target__PyInterpreterState_Get == NULL) {
        __target__PyInterpreterState_Get = resolveAPI("PyInterpreterState_Get");
    }
    STATS_BEFORE(PyInterpreterState_Get)
    PyInterpreterState* result = (PyInterpreterState*) __target__PyInterpreterState_Get();
    STATS_AFTER(PyInterpreterState_Get)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyInterpreterState_GetDict, PyInterpreterState_Get)
PyObject* (*__target__PyInterpreterState_GetDict)(PyInterpreterState*) = NULL;
PyAPI_FUNC(PyObject*) PyInterpreterState_GetDict(PyInterpreterState* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyInterpreterState_GetDict == NULL) {
        __target__PyInterpreterState_GetDict = resolveAPI("PyInterpreterState_GetDict");
    }
    STATS_BEFORE(PyInterpreterState_GetDict)
    PyObject* result = (PyObject*) __target__PyInterpreterState_GetDict(a);
    STATS_AFTER(PyInterpreterState_GetDict)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyInterpreterState_GetID, PyInterpreterState_GetDict)
int64_t (*__target__PyInterpreterState_GetID)(PyInterpreterState*) = NULL;
PyAPI_FUNC(int64_t) PyInterpreterState_GetID(PyInterpreterState* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyInterpreterState_GetID == NULL) {
        __target__PyInterpreterState_GetID = resolveAPI("PyInterpreterState_GetID");
    }
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
    if (__target__PyInterpreterState_GetIDFromThreadState == NULL) {
        __target__PyInterpreterState_GetIDFromThreadState = resolveAPI("PyInterpreterState_GetIDFromThreadState");
    }
    STATS_BEFORE(PyInterpreterState_GetIDFromThreadState)
    int64_t result = (int64_t) __target__PyInterpreterState_GetIDFromThreadState(a);
    STATS_AFTER(PyInterpreterState_GetIDFromThreadState)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyInterpreterState_Head, PyInterpreterState_GetIDFromThreadState)
PyInterpreterState* (*__target__PyInterpreterState_Head)() = NULL;
PyAPI_FUNC(PyInterpreterState*) PyInterpreterState_Head() {
    LOGS("");
    if (__target__PyInterpreterState_Head == NULL) {
        __target__PyInterpreterState_Head = resolveAPI("PyInterpreterState_Head");
    }
    STATS_BEFORE(PyInterpreterState_Head)
    PyInterpreterState* result = (PyInterpreterState*) __target__PyInterpreterState_Head();
    STATS_AFTER(PyInterpreterState_Head)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyInterpreterState_Main, PyInterpreterState_Head)
PyInterpreterState* (*__target__PyInterpreterState_Main)() = NULL;
PyAPI_FUNC(PyInterpreterState*) PyInterpreterState_Main() {
    LOGS("");
    if (__target__PyInterpreterState_Main == NULL) {
        __target__PyInterpreterState_Main = resolveAPI("PyInterpreterState_Main");
    }
    STATS_BEFORE(PyInterpreterState_Main)
    PyInterpreterState* result = (PyInterpreterState*) __target__PyInterpreterState_Main();
    STATS_AFTER(PyInterpreterState_Main)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyInterpreterState_New, PyInterpreterState_Main)
PyInterpreterState* (*__target__PyInterpreterState_New)() = NULL;
PyAPI_FUNC(PyInterpreterState*) PyInterpreterState_New() {
    LOGS("");
    if (__target__PyInterpreterState_New == NULL) {
        __target__PyInterpreterState_New = resolveAPI("PyInterpreterState_New");
    }
    STATS_BEFORE(PyInterpreterState_New)
    PyInterpreterState* result = (PyInterpreterState*) __target__PyInterpreterState_New();
    STATS_AFTER(PyInterpreterState_New)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyInterpreterState_Next, PyInterpreterState_New)
PyInterpreterState* (*__target__PyInterpreterState_Next)(PyInterpreterState*) = NULL;
PyAPI_FUNC(PyInterpreterState*) PyInterpreterState_Next(PyInterpreterState* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyInterpreterState_Next == NULL) {
        __target__PyInterpreterState_Next = resolveAPI("PyInterpreterState_Next");
    }
    STATS_BEFORE(PyInterpreterState_Next)
    PyInterpreterState* result = (PyInterpreterState*) __target__PyInterpreterState_Next(a);
    STATS_AFTER(PyInterpreterState_Next)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyInterpreterState_ThreadHead, PyInterpreterState_Next)
PyThreadState* (*__target__PyInterpreterState_ThreadHead)(PyInterpreterState*) = NULL;
PyAPI_FUNC(PyThreadState*) PyInterpreterState_ThreadHead(PyInterpreterState* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyInterpreterState_ThreadHead == NULL) {
        __target__PyInterpreterState_ThreadHead = resolveAPI("PyInterpreterState_ThreadHead");
    }
    STATS_BEFORE(PyInterpreterState_ThreadHead)
    PyThreadState* result = (PyThreadState*) __target__PyInterpreterState_ThreadHead(a);
    STATS_AFTER(PyInterpreterState_ThreadHead)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyIter_Check, PyInterpreterState_ThreadHead)
int (*__target__PyIter_Check)(PyObject*) = NULL;
PyAPI_FUNC(int) PyIter_Check(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyIter_Check == NULL) {
        __target__PyIter_Check = resolveAPI("PyIter_Check");
    }
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
    if (__target__PyIter_Next == NULL) {
        __target__PyIter_Next = resolveAPI("PyIter_Next");
    }
    STATS_BEFORE(PyIter_Next)
    PyObject* result = (PyObject*) __target__PyIter_Next(a);
    STATS_AFTER(PyIter_Next)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyIter_Send, PyIter_Next)
PySendResult (*__target__PyIter_Send)(PyObject*, PyObject*, PyObject**) = NULL;
PyAPI_FUNC(PySendResult) PyIter_Send(PyObject* a, PyObject* b, PyObject** c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    if (__target__PyIter_Send == NULL) {
        __target__PyIter_Send = resolveAPI("PyIter_Send");
    }
    STATS_BEFORE(PyIter_Send)
    PySendResult result = (PySendResult) __target__PyIter_Send(a, b, c);
    STATS_AFTER(PyIter_Send)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyLineTable_InitAddressRange, PyIter_Send)
void (*__target__PyLineTable_InitAddressRange)(const char*, Py_ssize_t, int, PyCodeAddressRange*) = NULL;
PyAPI_FUNC(void) PyLineTable_InitAddressRange(const char* a, Py_ssize_t b, int c, PyCodeAddressRange* d) {
    LOG("'%s'(0x%lx) 0x%lx 0x%lx 0x%lx", a?a:"<null>", (unsigned long) a, (unsigned long) b, (unsigned long) c, (unsigned long) d);
    if (__target__PyLineTable_InitAddressRange == NULL) {
        __target__PyLineTable_InitAddressRange = resolveAPI("PyLineTable_InitAddressRange");
    }
    STATS_BEFORE(PyLineTable_InitAddressRange)
    __target__PyLineTable_InitAddressRange(a, b, c, d);
    STATS_AFTER(PyLineTable_InitAddressRange)
    LOG_AFTER_VOID
}
STATS_CONTAINER(PyLineTable_NextAddressRange, PyLineTable_InitAddressRange)
int (*__target__PyLineTable_NextAddressRange)(PyCodeAddressRange*) = NULL;
PyAPI_FUNC(int) PyLineTable_NextAddressRange(PyCodeAddressRange* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyLineTable_NextAddressRange == NULL) {
        __target__PyLineTable_NextAddressRange = resolveAPI("PyLineTable_NextAddressRange");
    }
    STATS_BEFORE(PyLineTable_NextAddressRange)
    int result = (int) __target__PyLineTable_NextAddressRange(a);
    STATS_AFTER(PyLineTable_NextAddressRange)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyLineTable_PreviousAddressRange, PyLineTable_NextAddressRange)
int (*__target__PyLineTable_PreviousAddressRange)(PyCodeAddressRange*) = NULL;
PyAPI_FUNC(int) PyLineTable_PreviousAddressRange(PyCodeAddressRange* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyLineTable_PreviousAddressRange == NULL) {
        __target__PyLineTable_PreviousAddressRange = resolveAPI("PyLineTable_PreviousAddressRange");
    }
    STATS_BEFORE(PyLineTable_PreviousAddressRange)
    int result = (int) __target__PyLineTable_PreviousAddressRange(a);
    STATS_AFTER(PyLineTable_PreviousAddressRange)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyList_Append, PyLineTable_PreviousAddressRange)
int (*__target__PyList_Append)(PyObject*, PyObject*) = NULL;
PyAPI_FUNC(int) PyList_Append(PyObject* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target__PyList_Append == NULL) {
        __target__PyList_Append = resolveAPI("PyList_Append");
    }
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
    if (__target__PyList_AsTuple == NULL) {
        __target__PyList_AsTuple = resolveAPI("PyList_AsTuple");
    }
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
    if (__target__PyList_GetItem == NULL) {
        __target__PyList_GetItem = resolveAPI("PyList_GetItem");
    }
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
    if (__target__PyList_GetSlice == NULL) {
        __target__PyList_GetSlice = resolveAPI("PyList_GetSlice");
    }
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
    if (__target__PyList_Insert == NULL) {
        __target__PyList_Insert = resolveAPI("PyList_Insert");
    }
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
    if (__target__PyList_New == NULL) {
        __target__PyList_New = resolveAPI("PyList_New");
    }
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
    if (__target__PyList_Reverse == NULL) {
        __target__PyList_Reverse = resolveAPI("PyList_Reverse");
    }
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
    if (__target__PyList_SetItem == NULL) {
        __target__PyList_SetItem = resolveAPI("PyList_SetItem");
    }
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
    if (__target__PyList_SetSlice == NULL) {
        __target__PyList_SetSlice = resolveAPI("PyList_SetSlice");
    }
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
    if (__target__PyList_Size == NULL) {
        __target__PyList_Size = resolveAPI("PyList_Size");
    }
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
    if (__target__PyList_Sort == NULL) {
        __target__PyList_Sort = resolveAPI("PyList_Sort");
    }
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
    if (__target__PyLong_AsDouble == NULL) {
        __target__PyLong_AsDouble = resolveAPI("PyLong_AsDouble");
    }
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
    if (__target__PyLong_AsLong == NULL) {
        __target__PyLong_AsLong = resolveAPI("PyLong_AsLong");
    }
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
    if (__target__PyLong_AsLongAndOverflow == NULL) {
        __target__PyLong_AsLongAndOverflow = resolveAPI("PyLong_AsLongAndOverflow");
    }
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
    if (__target__PyLong_AsLongLong == NULL) {
        __target__PyLong_AsLongLong = resolveAPI("PyLong_AsLongLong");
    }
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
    if (__target__PyLong_AsLongLongAndOverflow == NULL) {
        __target__PyLong_AsLongLongAndOverflow = resolveAPI("PyLong_AsLongLongAndOverflow");
    }
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
    if (__target__PyLong_AsSize_t == NULL) {
        __target__PyLong_AsSize_t = resolveAPI("PyLong_AsSize_t");
    }
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
    if (__target__PyLong_AsSsize_t == NULL) {
        __target__PyLong_AsSsize_t = resolveAPI("PyLong_AsSsize_t");
    }
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
    if (__target__PyLong_AsUnsignedLong == NULL) {
        __target__PyLong_AsUnsignedLong = resolveAPI("PyLong_AsUnsignedLong");
    }
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
    if (__target__PyLong_AsUnsignedLongLong == NULL) {
        __target__PyLong_AsUnsignedLongLong = resolveAPI("PyLong_AsUnsignedLongLong");
    }
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
    if (__target__PyLong_AsUnsignedLongLongMask == NULL) {
        __target__PyLong_AsUnsignedLongLongMask = resolveAPI("PyLong_AsUnsignedLongLongMask");
    }
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
    if (__target__PyLong_AsUnsignedLongMask == NULL) {
        __target__PyLong_AsUnsignedLongMask = resolveAPI("PyLong_AsUnsignedLongMask");
    }
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
    if (__target__PyLong_AsVoidPtr == NULL) {
        __target__PyLong_AsVoidPtr = resolveAPI("PyLong_AsVoidPtr");
    }
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
    if (__target__PyLong_FromDouble == NULL) {
        __target__PyLong_FromDouble = resolveAPI("PyLong_FromDouble");
    }
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
    if (__target__PyLong_FromLong == NULL) {
        __target__PyLong_FromLong = resolveAPI("PyLong_FromLong");
    }
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
    if (__target__PyLong_FromLongLong == NULL) {
        __target__PyLong_FromLongLong = resolveAPI("PyLong_FromLongLong");
    }
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
    if (__target__PyLong_FromSize_t == NULL) {
        __target__PyLong_FromSize_t = resolveAPI("PyLong_FromSize_t");
    }
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
    if (__target__PyLong_FromSsize_t == NULL) {
        __target__PyLong_FromSsize_t = resolveAPI("PyLong_FromSsize_t");
    }
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
    if (__target__PyLong_FromString == NULL) {
        __target__PyLong_FromString = resolveAPI("PyLong_FromString");
    }
    STATS_BEFORE(PyLong_FromString)
    PyObject* result = (PyObject*) __target__PyLong_FromString(a, b, c);
    STATS_AFTER(PyLong_FromString)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyLong_FromUnicodeObject, PyLong_FromString)
PyObject* (*__target__PyLong_FromUnicodeObject)(PyObject*, int) = NULL;
PyAPI_FUNC(PyObject*) PyLong_FromUnicodeObject(PyObject* a, int b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target__PyLong_FromUnicodeObject == NULL) {
        __target__PyLong_FromUnicodeObject = resolveAPI("PyLong_FromUnicodeObject");
    }
    STATS_BEFORE(PyLong_FromUnicodeObject)
    PyObject* result = (PyObject*) __target__PyLong_FromUnicodeObject(a, b);
    STATS_AFTER(PyLong_FromUnicodeObject)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyLong_FromUnsignedLong, PyLong_FromUnicodeObject)
PyObject* (*__target__PyLong_FromUnsignedLong)(unsigned long) = NULL;
PyAPI_FUNC(PyObject*) PyLong_FromUnsignedLong(unsigned long a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyLong_FromUnsignedLong == NULL) {
        __target__PyLong_FromUnsignedLong = resolveAPI("PyLong_FromUnsignedLong");
    }
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
    if (__target__PyLong_FromUnsignedLongLong == NULL) {
        __target__PyLong_FromUnsignedLongLong = resolveAPI("PyLong_FromUnsignedLongLong");
    }
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
    if (__target__PyLong_FromVoidPtr == NULL) {
        __target__PyLong_FromVoidPtr = resolveAPI("PyLong_FromVoidPtr");
    }
    STATS_BEFORE(PyLong_FromVoidPtr)
    PyObject* result = (PyObject*) __target__PyLong_FromVoidPtr(a);
    STATS_AFTER(PyLong_FromVoidPtr)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyLong_GetInfo, PyLong_FromVoidPtr)
PyObject* (*__target__PyLong_GetInfo)() = NULL;
PyAPI_FUNC(PyObject*) PyLong_GetInfo() {
    LOGS("");
    if (__target__PyLong_GetInfo == NULL) {
        __target__PyLong_GetInfo = resolveAPI("PyLong_GetInfo");
    }
    STATS_BEFORE(PyLong_GetInfo)
    PyObject* result = (PyObject*) __target__PyLong_GetInfo();
    STATS_AFTER(PyLong_GetInfo)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyMapping_Check, PyLong_GetInfo)
int (*__target__PyMapping_Check)(PyObject*) = NULL;
PyAPI_FUNC(int) PyMapping_Check(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyMapping_Check == NULL) {
        __target__PyMapping_Check = resolveAPI("PyMapping_Check");
    }
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
    if (__target__PyMapping_GetItemString == NULL) {
        __target__PyMapping_GetItemString = resolveAPI("PyMapping_GetItemString");
    }
    STATS_BEFORE(PyMapping_GetItemString)
    PyObject* result = (PyObject*) __target__PyMapping_GetItemString(a, b);
    STATS_AFTER(PyMapping_GetItemString)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyMapping_HasKey, PyMapping_GetItemString)
int (*__target__PyMapping_HasKey)(PyObject*, PyObject*) = NULL;
PyAPI_FUNC(int) PyMapping_HasKey(PyObject* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target__PyMapping_HasKey == NULL) {
        __target__PyMapping_HasKey = resolveAPI("PyMapping_HasKey");
    }
    STATS_BEFORE(PyMapping_HasKey)
    int result = (int) __target__PyMapping_HasKey(a, b);
    STATS_AFTER(PyMapping_HasKey)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyMapping_HasKeyString, PyMapping_HasKey)
int (*__target__PyMapping_HasKeyString)(PyObject*, const char*) = NULL;
PyAPI_FUNC(int) PyMapping_HasKeyString(PyObject* a, const char* b) {
    LOG("0x%lx '%s'(0x%lx)", (unsigned long) a, b?b:"<null>", (unsigned long) b);
    if (__target__PyMapping_HasKeyString == NULL) {
        __target__PyMapping_HasKeyString = resolveAPI("PyMapping_HasKeyString");
    }
    STATS_BEFORE(PyMapping_HasKeyString)
    int result = (int) __target__PyMapping_HasKeyString(a, b);
    STATS_AFTER(PyMapping_HasKeyString)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyMapping_Items, PyMapping_HasKeyString)
PyObject* (*__target__PyMapping_Items)(PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyMapping_Items(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyMapping_Items == NULL) {
        __target__PyMapping_Items = resolveAPI("PyMapping_Items");
    }
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
    if (__target__PyMapping_Keys == NULL) {
        __target__PyMapping_Keys = resolveAPI("PyMapping_Keys");
    }
    STATS_BEFORE(PyMapping_Keys)
    PyObject* result = (PyObject*) __target__PyMapping_Keys(a);
    STATS_AFTER(PyMapping_Keys)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyMapping_Length, PyMapping_Keys)
Py_ssize_t (*__target__PyMapping_Length)(PyObject*) = NULL;
PyAPI_FUNC(Py_ssize_t) PyMapping_Length(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyMapping_Length == NULL) {
        __target__PyMapping_Length = resolveAPI("PyMapping_Length");
    }
    STATS_BEFORE(PyMapping_Length)
    Py_ssize_t result = (Py_ssize_t) __target__PyMapping_Length(a);
    STATS_AFTER(PyMapping_Length)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyMapping_SetItemString, PyMapping_Length)
int (*__target__PyMapping_SetItemString)(PyObject*, const char*, PyObject*) = NULL;
PyAPI_FUNC(int) PyMapping_SetItemString(PyObject* a, const char* b, PyObject* c) {
    LOG("0x%lx '%s'(0x%lx) 0x%lx", (unsigned long) a, b?b:"<null>", (unsigned long) b, (unsigned long) c);
    if (__target__PyMapping_SetItemString == NULL) {
        __target__PyMapping_SetItemString = resolveAPI("PyMapping_SetItemString");
    }
    STATS_BEFORE(PyMapping_SetItemString)
    int result = (int) __target__PyMapping_SetItemString(a, b, c);
    STATS_AFTER(PyMapping_SetItemString)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyMapping_Size, PyMapping_SetItemString)
Py_ssize_t (*__target__PyMapping_Size)(PyObject*) = NULL;
PyAPI_FUNC(Py_ssize_t) PyMapping_Size(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyMapping_Size == NULL) {
        __target__PyMapping_Size = resolveAPI("PyMapping_Size");
    }
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
    if (__target__PyMapping_Values == NULL) {
        __target__PyMapping_Values = resolveAPI("PyMapping_Values");
    }
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
    if (__target__PyMem_Calloc == NULL) {
        __target__PyMem_Calloc = resolveAPI("PyMem_Calloc");
    }
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
    if (__target__PyMem_Free == NULL) {
        __target__PyMem_Free = resolveAPI("PyMem_Free");
    }
    STATS_BEFORE(PyMem_Free)
    __target__PyMem_Free(a);
    STATS_AFTER(PyMem_Free)
    LOG_AFTER_VOID
}
STATS_CONTAINER(PyMem_GetAllocator, PyMem_Free)
void (*__target__PyMem_GetAllocator)(PyMemAllocatorDomain, PyMemAllocatorEx*) = NULL;
PyAPI_FUNC(void) PyMem_GetAllocator(PyMemAllocatorDomain a, PyMemAllocatorEx* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target__PyMem_GetAllocator == NULL) {
        __target__PyMem_GetAllocator = resolveAPI("PyMem_GetAllocator");
    }
    STATS_BEFORE(PyMem_GetAllocator)
    __target__PyMem_GetAllocator(a, b);
    STATS_AFTER(PyMem_GetAllocator)
    LOG_AFTER_VOID
}
STATS_CONTAINER(PyMem_Malloc, PyMem_GetAllocator)
void* (*__target__PyMem_Malloc)(size_t) = NULL;
PyAPI_FUNC(void*) PyMem_Malloc(size_t a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyMem_Malloc == NULL) {
        __target__PyMem_Malloc = resolveAPI("PyMem_Malloc");
    }
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
    if (__target__PyMem_RawCalloc == NULL) {
        __target__PyMem_RawCalloc = resolveAPI("PyMem_RawCalloc");
    }
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
    if (__target__PyMem_RawFree == NULL) {
        __target__PyMem_RawFree = resolveAPI("PyMem_RawFree");
    }
    STATS_BEFORE(PyMem_RawFree)
    __target__PyMem_RawFree(a);
    STATS_AFTER(PyMem_RawFree)
    LOG_AFTER_VOID
}
STATS_CONTAINER(PyMem_RawMalloc, PyMem_RawFree)
void* (*__target__PyMem_RawMalloc)(size_t) = NULL;
PyAPI_FUNC(void*) PyMem_RawMalloc(size_t a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyMem_RawMalloc == NULL) {
        __target__PyMem_RawMalloc = resolveAPI("PyMem_RawMalloc");
    }
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
    if (__target__PyMem_RawRealloc == NULL) {
        __target__PyMem_RawRealloc = resolveAPI("PyMem_RawRealloc");
    }
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
    if (__target__PyMem_Realloc == NULL) {
        __target__PyMem_Realloc = resolveAPI("PyMem_Realloc");
    }
    STATS_BEFORE(PyMem_Realloc)
    void* result = (void*) __target__PyMem_Realloc(a, b);
    STATS_AFTER(PyMem_Realloc)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyMem_SetAllocator, PyMem_Realloc)
void (*__target__PyMem_SetAllocator)(PyMemAllocatorDomain, PyMemAllocatorEx*) = NULL;
PyAPI_FUNC(void) PyMem_SetAllocator(PyMemAllocatorDomain a, PyMemAllocatorEx* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target__PyMem_SetAllocator == NULL) {
        __target__PyMem_SetAllocator = resolveAPI("PyMem_SetAllocator");
    }
    STATS_BEFORE(PyMem_SetAllocator)
    __target__PyMem_SetAllocator(a, b);
    STATS_AFTER(PyMem_SetAllocator)
    LOG_AFTER_VOID
}
STATS_CONTAINER(PyMem_SetupDebugHooks, PyMem_SetAllocator)
void (*__target__PyMem_SetupDebugHooks)() = NULL;
PyAPI_FUNC(void) PyMem_SetupDebugHooks() {
    LOGS("");
    if (__target__PyMem_SetupDebugHooks == NULL) {
        __target__PyMem_SetupDebugHooks = resolveAPI("PyMem_SetupDebugHooks");
    }
    STATS_BEFORE(PyMem_SetupDebugHooks)
    __target__PyMem_SetupDebugHooks();
    STATS_AFTER(PyMem_SetupDebugHooks)
    LOG_AFTER_VOID
}
STATS_CONTAINER(PyMember_GetOne, PyMem_SetupDebugHooks)
PyObject* (*__target__PyMember_GetOne)(const char*, struct PyMemberDef*) = NULL;
PyAPI_FUNC(PyObject*) PyMember_GetOne(const char* a, struct PyMemberDef* b) {
    LOG("'%s'(0x%lx) 0x%lx", a?a:"<null>", (unsigned long) a, (unsigned long) b);
    if (__target__PyMember_GetOne == NULL) {
        __target__PyMember_GetOne = resolveAPI("PyMember_GetOne");
    }
    STATS_BEFORE(PyMember_GetOne)
    PyObject* result = (PyObject*) __target__PyMember_GetOne(a, b);
    STATS_AFTER(PyMember_GetOne)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyMember_SetOne, PyMember_GetOne)
int (*__target__PyMember_SetOne)(char*, struct PyMemberDef*, PyObject*) = NULL;
PyAPI_FUNC(int) PyMember_SetOne(char* a, struct PyMemberDef* b, PyObject* c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    if (__target__PyMember_SetOne == NULL) {
        __target__PyMember_SetOne = resolveAPI("PyMember_SetOne");
    }
    STATS_BEFORE(PyMember_SetOne)
    int result = (int) __target__PyMember_SetOne(a, b, c);
    STATS_AFTER(PyMember_SetOne)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyMemoryView_FromBuffer, PyMember_SetOne)
PyObject* (*__target__PyMemoryView_FromBuffer)(Py_buffer*) = NULL;
PyAPI_FUNC(PyObject*) PyMemoryView_FromBuffer(Py_buffer* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyMemoryView_FromBuffer == NULL) {
        __target__PyMemoryView_FromBuffer = resolveAPI("PyMemoryView_FromBuffer");
    }
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
    if (__target__PyMemoryView_FromMemory == NULL) {
        __target__PyMemoryView_FromMemory = resolveAPI("PyMemoryView_FromMemory");
    }
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
    if (__target__PyMemoryView_FromObject == NULL) {
        __target__PyMemoryView_FromObject = resolveAPI("PyMemoryView_FromObject");
    }
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
    if (__target__PyMemoryView_GetContiguous == NULL) {
        __target__PyMemoryView_GetContiguous = resolveAPI("PyMemoryView_GetContiguous");
    }
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
    if (__target__PyMethodDescrObject_GetMethod == NULL) {
        __target__PyMethodDescrObject_GetMethod = resolveAPI("PyMethodDescrObject_GetMethod");
    }
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
    if (__target__PyMethod_Function == NULL) {
        __target__PyMethod_Function = resolveAPI("PyMethod_Function");
    }
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
    if (__target__PyMethod_New == NULL) {
        __target__PyMethod_New = resolveAPI("PyMethod_New");
    }
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
    if (__target__PyMethod_Self == NULL) {
        __target__PyMethod_Self = resolveAPI("PyMethod_Self");
    }
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
    if (__target__PyModuleDef_Init == NULL) {
        __target__PyModuleDef_Init = resolveAPI("PyModuleDef_Init");
    }
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
    if (__target__PyModule_AddFunctions == NULL) {
        __target__PyModule_AddFunctions = resolveAPI("PyModule_AddFunctions");
    }
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
    if (__target__PyModule_AddIntConstant == NULL) {
        __target__PyModule_AddIntConstant = resolveAPI("PyModule_AddIntConstant");
    }
    STATS_BEFORE(PyModule_AddIntConstant)
    int result = (int) __target__PyModule_AddIntConstant(a, b, c);
    STATS_AFTER(PyModule_AddIntConstant)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyModule_AddObject, PyModule_AddIntConstant)
int (*__target__PyModule_AddObject)(PyObject*, const char*, PyObject*) = NULL;
PyAPI_FUNC(int) PyModule_AddObject(PyObject* a, const char* b, PyObject* c) {
    LOG("0x%lx '%s'(0x%lx) 0x%lx", (unsigned long) a, b?b:"<null>", (unsigned long) b, (unsigned long) c);
    if (__target__PyModule_AddObject == NULL) {
        __target__PyModule_AddObject = resolveAPI("PyModule_AddObject");
    }
    STATS_BEFORE(PyModule_AddObject)
    int result = (int) __target__PyModule_AddObject(a, b, c);
    STATS_AFTER(PyModule_AddObject)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyModule_AddObjectRef, PyModule_AddObject)
int (*__target__PyModule_AddObjectRef)(PyObject*, const char*, PyObject*) = NULL;
PyAPI_FUNC(int) PyModule_AddObjectRef(PyObject* a, const char* b, PyObject* c) {
    LOG("0x%lx '%s'(0x%lx) 0x%lx", (unsigned long) a, b?b:"<null>", (unsigned long) b, (unsigned long) c);
    if (__target__PyModule_AddObjectRef == NULL) {
        __target__PyModule_AddObjectRef = resolveAPI("PyModule_AddObjectRef");
    }
    STATS_BEFORE(PyModule_AddObjectRef)
    int result = (int) __target__PyModule_AddObjectRef(a, b, c);
    STATS_AFTER(PyModule_AddObjectRef)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyModule_AddStringConstant, PyModule_AddObjectRef)
int (*__target__PyModule_AddStringConstant)(PyObject*, const char*, const char*) = NULL;
PyAPI_FUNC(int) PyModule_AddStringConstant(PyObject* a, const char* b, const char* c) {
    LOG("0x%lx '%s'(0x%lx) '%s'(0x%lx)", (unsigned long) a, b?b:"<null>", (unsigned long) b, c?c:"<null>", (unsigned long) c);
    if (__target__PyModule_AddStringConstant == NULL) {
        __target__PyModule_AddStringConstant = resolveAPI("PyModule_AddStringConstant");
    }
    STATS_BEFORE(PyModule_AddStringConstant)
    int result = (int) __target__PyModule_AddStringConstant(a, b, c);
    STATS_AFTER(PyModule_AddStringConstant)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyModule_AddType, PyModule_AddStringConstant)
int (*__target__PyModule_AddType)(PyObject*, PyTypeObject*) = NULL;
PyAPI_FUNC(int) PyModule_AddType(PyObject* a, PyTypeObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target__PyModule_AddType == NULL) {
        __target__PyModule_AddType = resolveAPI("PyModule_AddType");
    }
    STATS_BEFORE(PyModule_AddType)
    int result = (int) __target__PyModule_AddType(a, b);
    STATS_AFTER(PyModule_AddType)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyModule_Create2, PyModule_AddType)
PyObject* (*__target__PyModule_Create2)(struct PyModuleDef*, int) = NULL;
PyAPI_FUNC(PyObject*) PyModule_Create2(struct PyModuleDef* a, int b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target__PyModule_Create2 == NULL) {
        __target__PyModule_Create2 = resolveAPI("PyModule_Create2");
    }
    STATS_BEFORE(PyModule_Create2)
    PyObject* result = (PyObject*) __target__PyModule_Create2(a, b);
    STATS_AFTER(PyModule_Create2)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyModule_ExecDef, PyModule_Create2)
int (*__target__PyModule_ExecDef)(PyObject*, PyModuleDef*) = NULL;
PyAPI_FUNC(int) PyModule_ExecDef(PyObject* a, PyModuleDef* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target__PyModule_ExecDef == NULL) {
        __target__PyModule_ExecDef = resolveAPI("PyModule_ExecDef");
    }
    STATS_BEFORE(PyModule_ExecDef)
    int result = (int) __target__PyModule_ExecDef(a, b);
    STATS_AFTER(PyModule_ExecDef)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyModule_FromDefAndSpec2, PyModule_ExecDef)
PyObject* (*__target__PyModule_FromDefAndSpec2)(PyModuleDef*, PyObject*, int) = NULL;
PyAPI_FUNC(PyObject*) PyModule_FromDefAndSpec2(PyModuleDef* a, PyObject* b, int c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    if (__target__PyModule_FromDefAndSpec2 == NULL) {
        __target__PyModule_FromDefAndSpec2 = resolveAPI("PyModule_FromDefAndSpec2");
    }
    STATS_BEFORE(PyModule_FromDefAndSpec2)
    PyObject* result = (PyObject*) __target__PyModule_FromDefAndSpec2(a, b, c);
    STATS_AFTER(PyModule_FromDefAndSpec2)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyModule_GetDef, PyModule_FromDefAndSpec2)
PyModuleDef* (*__target__PyModule_GetDef)(PyObject*) = NULL;
PyAPI_FUNC(PyModuleDef*) PyModule_GetDef(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyModule_GetDef == NULL) {
        __target__PyModule_GetDef = resolveAPI("PyModule_GetDef");
    }
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
    if (__target__PyModule_GetDict == NULL) {
        __target__PyModule_GetDict = resolveAPI("PyModule_GetDict");
    }
    STATS_BEFORE(PyModule_GetDict)
    PyObject* result = (PyObject*) __target__PyModule_GetDict(a);
    STATS_AFTER(PyModule_GetDict)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyModule_GetFilename, PyModule_GetDict)
const char* (*__target__PyModule_GetFilename)(PyObject*) = NULL;
PyAPI_FUNC(const char*) PyModule_GetFilename(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyModule_GetFilename == NULL) {
        __target__PyModule_GetFilename = resolveAPI("PyModule_GetFilename");
    }
    STATS_BEFORE(PyModule_GetFilename)
    const char* result = (const char*) __target__PyModule_GetFilename(a);
    STATS_AFTER(PyModule_GetFilename)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyModule_GetFilenameObject, PyModule_GetFilename)
PyObject* (*__target__PyModule_GetFilenameObject)(PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyModule_GetFilenameObject(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyModule_GetFilenameObject == NULL) {
        __target__PyModule_GetFilenameObject = resolveAPI("PyModule_GetFilenameObject");
    }
    STATS_BEFORE(PyModule_GetFilenameObject)
    PyObject* result = (PyObject*) __target__PyModule_GetFilenameObject(a);
    STATS_AFTER(PyModule_GetFilenameObject)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyModule_GetName, PyModule_GetFilenameObject)
const char* (*__target__PyModule_GetName)(PyObject*) = NULL;
PyAPI_FUNC(const char*) PyModule_GetName(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyModule_GetName == NULL) {
        __target__PyModule_GetName = resolveAPI("PyModule_GetName");
    }
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
    if (__target__PyModule_GetNameObject == NULL) {
        __target__PyModule_GetNameObject = resolveAPI("PyModule_GetNameObject");
    }
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
    if (__target__PyModule_GetState == NULL) {
        __target__PyModule_GetState = resolveAPI("PyModule_GetState");
    }
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
    if (__target__PyModule_New == NULL) {
        __target__PyModule_New = resolveAPI("PyModule_New");
    }
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
    if (__target__PyModule_NewObject == NULL) {
        __target__PyModule_NewObject = resolveAPI("PyModule_NewObject");
    }
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
    if (__target__PyModule_SetDocString == NULL) {
        __target__PyModule_SetDocString = resolveAPI("PyModule_SetDocString");
    }
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
    if (__target__PyNumber_Absolute == NULL) {
        __target__PyNumber_Absolute = resolveAPI("PyNumber_Absolute");
    }
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
    if (__target__PyNumber_Add == NULL) {
        __target__PyNumber_Add = resolveAPI("PyNumber_Add");
    }
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
    if (__target__PyNumber_And == NULL) {
        __target__PyNumber_And = resolveAPI("PyNumber_And");
    }
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
    if (__target__PyNumber_AsSsize_t == NULL) {
        __target__PyNumber_AsSsize_t = resolveAPI("PyNumber_AsSsize_t");
    }
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
    if (__target__PyNumber_Check == NULL) {
        __target__PyNumber_Check = resolveAPI("PyNumber_Check");
    }
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
    if (__target__PyNumber_Divmod == NULL) {
        __target__PyNumber_Divmod = resolveAPI("PyNumber_Divmod");
    }
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
    if (__target__PyNumber_Float == NULL) {
        __target__PyNumber_Float = resolveAPI("PyNumber_Float");
    }
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
    if (__target__PyNumber_FloorDivide == NULL) {
        __target__PyNumber_FloorDivide = resolveAPI("PyNumber_FloorDivide");
    }
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
    if (__target__PyNumber_InPlaceAdd == NULL) {
        __target__PyNumber_InPlaceAdd = resolveAPI("PyNumber_InPlaceAdd");
    }
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
    if (__target__PyNumber_InPlaceAnd == NULL) {
        __target__PyNumber_InPlaceAnd = resolveAPI("PyNumber_InPlaceAnd");
    }
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
    if (__target__PyNumber_InPlaceFloorDivide == NULL) {
        __target__PyNumber_InPlaceFloorDivide = resolveAPI("PyNumber_InPlaceFloorDivide");
    }
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
    if (__target__PyNumber_InPlaceLshift == NULL) {
        __target__PyNumber_InPlaceLshift = resolveAPI("PyNumber_InPlaceLshift");
    }
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
    if (__target__PyNumber_InPlaceMatrixMultiply == NULL) {
        __target__PyNumber_InPlaceMatrixMultiply = resolveAPI("PyNumber_InPlaceMatrixMultiply");
    }
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
    if (__target__PyNumber_InPlaceMultiply == NULL) {
        __target__PyNumber_InPlaceMultiply = resolveAPI("PyNumber_InPlaceMultiply");
    }
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
    if (__target__PyNumber_InPlaceOr == NULL) {
        __target__PyNumber_InPlaceOr = resolveAPI("PyNumber_InPlaceOr");
    }
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
    if (__target__PyNumber_InPlacePower == NULL) {
        __target__PyNumber_InPlacePower = resolveAPI("PyNumber_InPlacePower");
    }
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
    if (__target__PyNumber_InPlaceRemainder == NULL) {
        __target__PyNumber_InPlaceRemainder = resolveAPI("PyNumber_InPlaceRemainder");
    }
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
    if (__target__PyNumber_InPlaceRshift == NULL) {
        __target__PyNumber_InPlaceRshift = resolveAPI("PyNumber_InPlaceRshift");
    }
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
    if (__target__PyNumber_InPlaceSubtract == NULL) {
        __target__PyNumber_InPlaceSubtract = resolveAPI("PyNumber_InPlaceSubtract");
    }
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
    if (__target__PyNumber_InPlaceTrueDivide == NULL) {
        __target__PyNumber_InPlaceTrueDivide = resolveAPI("PyNumber_InPlaceTrueDivide");
    }
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
    if (__target__PyNumber_InPlaceXor == NULL) {
        __target__PyNumber_InPlaceXor = resolveAPI("PyNumber_InPlaceXor");
    }
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
    if (__target__PyNumber_Index == NULL) {
        __target__PyNumber_Index = resolveAPI("PyNumber_Index");
    }
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
    if (__target__PyNumber_Invert == NULL) {
        __target__PyNumber_Invert = resolveAPI("PyNumber_Invert");
    }
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
    if (__target__PyNumber_Long == NULL) {
        __target__PyNumber_Long = resolveAPI("PyNumber_Long");
    }
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
    if (__target__PyNumber_Lshift == NULL) {
        __target__PyNumber_Lshift = resolveAPI("PyNumber_Lshift");
    }
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
    if (__target__PyNumber_MatrixMultiply == NULL) {
        __target__PyNumber_MatrixMultiply = resolveAPI("PyNumber_MatrixMultiply");
    }
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
    if (__target__PyNumber_Multiply == NULL) {
        __target__PyNumber_Multiply = resolveAPI("PyNumber_Multiply");
    }
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
    if (__target__PyNumber_Negative == NULL) {
        __target__PyNumber_Negative = resolveAPI("PyNumber_Negative");
    }
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
    if (__target__PyNumber_Or == NULL) {
        __target__PyNumber_Or = resolveAPI("PyNumber_Or");
    }
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
    if (__target__PyNumber_Positive == NULL) {
        __target__PyNumber_Positive = resolveAPI("PyNumber_Positive");
    }
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
    if (__target__PyNumber_Power == NULL) {
        __target__PyNumber_Power = resolveAPI("PyNumber_Power");
    }
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
    if (__target__PyNumber_Remainder == NULL) {
        __target__PyNumber_Remainder = resolveAPI("PyNumber_Remainder");
    }
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
    if (__target__PyNumber_Rshift == NULL) {
        __target__PyNumber_Rshift = resolveAPI("PyNumber_Rshift");
    }
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
    if (__target__PyNumber_Subtract == NULL) {
        __target__PyNumber_Subtract = resolveAPI("PyNumber_Subtract");
    }
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
    if (__target__PyNumber_ToBase == NULL) {
        __target__PyNumber_ToBase = resolveAPI("PyNumber_ToBase");
    }
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
    if (__target__PyNumber_TrueDivide == NULL) {
        __target__PyNumber_TrueDivide = resolveAPI("PyNumber_TrueDivide");
    }
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
    if (__target__PyNumber_Xor == NULL) {
        __target__PyNumber_Xor = resolveAPI("PyNumber_Xor");
    }
    STATS_BEFORE(PyNumber_Xor)
    PyObject* result = (PyObject*) __target__PyNumber_Xor(a, b);
    STATS_AFTER(PyNumber_Xor)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyODict_DelItem, PyNumber_Xor)
int (*__target__PyODict_DelItem)(PyObject*, PyObject*) = NULL;
PyAPI_FUNC(int) PyODict_DelItem(PyObject* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target__PyODict_DelItem == NULL) {
        __target__PyODict_DelItem = resolveAPI("PyODict_DelItem");
    }
    STATS_BEFORE(PyODict_DelItem)
    int result = (int) __target__PyODict_DelItem(a, b);
    STATS_AFTER(PyODict_DelItem)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyODict_New, PyODict_DelItem)
PyObject* (*__target__PyODict_New)() = NULL;
PyAPI_FUNC(PyObject*) PyODict_New() {
    LOGS("");
    if (__target__PyODict_New == NULL) {
        __target__PyODict_New = resolveAPI("PyODict_New");
    }
    STATS_BEFORE(PyODict_New)
    PyObject* result = (PyObject*) __target__PyODict_New();
    STATS_AFTER(PyODict_New)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyODict_SetItem, PyODict_New)
int (*__target__PyODict_SetItem)(PyObject*, PyObject*, PyObject*) = NULL;
PyAPI_FUNC(int) PyODict_SetItem(PyObject* a, PyObject* b, PyObject* c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    if (__target__PyODict_SetItem == NULL) {
        __target__PyODict_SetItem = resolveAPI("PyODict_SetItem");
    }
    STATS_BEFORE(PyODict_SetItem)
    int result = (int) __target__PyODict_SetItem(a, b, c);
    STATS_AFTER(PyODict_SetItem)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyOS_AfterFork, PyODict_SetItem)
void (*__target__PyOS_AfterFork)() = NULL;
PyAPI_FUNC(void) PyOS_AfterFork() {
    LOGS("");
    if (__target__PyOS_AfterFork == NULL) {
        __target__PyOS_AfterFork = resolveAPI("PyOS_AfterFork");
    }
    STATS_BEFORE(PyOS_AfterFork)
    __target__PyOS_AfterFork();
    STATS_AFTER(PyOS_AfterFork)
    LOG_AFTER_VOID
}
STATS_CONTAINER(PyOS_AfterFork_Child, PyOS_AfterFork)
void (*__target__PyOS_AfterFork_Child)() = NULL;
PyAPI_FUNC(void) PyOS_AfterFork_Child() {
    LOGS("");
    if (__target__PyOS_AfterFork_Child == NULL) {
        __target__PyOS_AfterFork_Child = resolveAPI("PyOS_AfterFork_Child");
    }
    STATS_BEFORE(PyOS_AfterFork_Child)
    __target__PyOS_AfterFork_Child();
    STATS_AFTER(PyOS_AfterFork_Child)
    LOG_AFTER_VOID
}
STATS_CONTAINER(PyOS_AfterFork_Parent, PyOS_AfterFork_Child)
void (*__target__PyOS_AfterFork_Parent)() = NULL;
PyAPI_FUNC(void) PyOS_AfterFork_Parent() {
    LOGS("");
    if (__target__PyOS_AfterFork_Parent == NULL) {
        __target__PyOS_AfterFork_Parent = resolveAPI("PyOS_AfterFork_Parent");
    }
    STATS_BEFORE(PyOS_AfterFork_Parent)
    __target__PyOS_AfterFork_Parent();
    STATS_AFTER(PyOS_AfterFork_Parent)
    LOG_AFTER_VOID
}
STATS_CONTAINER(PyOS_BeforeFork, PyOS_AfterFork_Parent)
void (*__target__PyOS_BeforeFork)() = NULL;
PyAPI_FUNC(void) PyOS_BeforeFork() {
    LOGS("");
    if (__target__PyOS_BeforeFork == NULL) {
        __target__PyOS_BeforeFork = resolveAPI("PyOS_BeforeFork");
    }
    STATS_BEFORE(PyOS_BeforeFork)
    __target__PyOS_BeforeFork();
    STATS_AFTER(PyOS_BeforeFork)
    LOG_AFTER_VOID
}
STATS_CONTAINER(PyOS_FSPath, PyOS_BeforeFork)
PyObject* (*__target__PyOS_FSPath)(PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyOS_FSPath(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyOS_FSPath == NULL) {
        __target__PyOS_FSPath = resolveAPI("PyOS_FSPath");
    }
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
    if (__target__PyOS_InterruptOccurred == NULL) {
        __target__PyOS_InterruptOccurred = resolveAPI("PyOS_InterruptOccurred");
    }
    STATS_BEFORE(PyOS_InterruptOccurred)
    int result = (int) __target__PyOS_InterruptOccurred();
    STATS_AFTER(PyOS_InterruptOccurred)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyOS_Readline, PyOS_InterruptOccurred)
char* (*__target__PyOS_Readline)(FILE*, FILE*, const char*) = NULL;
PyAPI_FUNC(char*) PyOS_Readline(FILE* a, FILE* b, const char* c) {
    LOG("0x%lx 0x%lx '%s'(0x%lx)", (unsigned long) a, (unsigned long) b, c?c:"<null>", (unsigned long) c);
    if (__target__PyOS_Readline == NULL) {
        __target__PyOS_Readline = resolveAPI("PyOS_Readline");
    }
    STATS_BEFORE(PyOS_Readline)
    char* result = (char*) __target__PyOS_Readline(a, b, c);
    STATS_AFTER(PyOS_Readline)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyOS_double_to_string, PyOS_Readline)
char* (*__target__PyOS_double_to_string)(double, char, int, int, int*) = NULL;
PyAPI_FUNC(char*) PyOS_double_to_string(double a, char b, int c, int d, int* e) {
    LOG("0x%lx 0x%lx 0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c, (unsigned long) d, (unsigned long) e);
    if (__target__PyOS_double_to_string == NULL) {
        __target__PyOS_double_to_string = resolveAPI("PyOS_double_to_string");
    }
    STATS_BEFORE(PyOS_double_to_string)
    char* result = (char*) __target__PyOS_double_to_string(a, b, c, d, e);
    STATS_AFTER(PyOS_double_to_string)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyOS_getsig, PyOS_double_to_string)
PyOS_sighandler_t (*__target__PyOS_getsig)(int) = NULL;
PyAPI_FUNC(PyOS_sighandler_t) PyOS_getsig(int a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyOS_getsig == NULL) {
        __target__PyOS_getsig = resolveAPI("PyOS_getsig");
    }
    STATS_BEFORE(PyOS_getsig)
    PyOS_sighandler_t result = (PyOS_sighandler_t) __target__PyOS_getsig(a);
    STATS_AFTER(PyOS_getsig)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyOS_mystricmp, PyOS_getsig)
int (*__target__PyOS_mystricmp)(const char*, const char*) = NULL;
PyAPI_FUNC(int) PyOS_mystricmp(const char* a, const char* b) {
    LOG("'%s'(0x%lx) '%s'(0x%lx)", a?a:"<null>", (unsigned long) a, b?b:"<null>", (unsigned long) b);
    if (__target__PyOS_mystricmp == NULL) {
        __target__PyOS_mystricmp = resolveAPI("PyOS_mystricmp");
    }
    STATS_BEFORE(PyOS_mystricmp)
    int result = (int) __target__PyOS_mystricmp(a, b);
    STATS_AFTER(PyOS_mystricmp)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyOS_mystrnicmp, PyOS_mystricmp)
int (*__target__PyOS_mystrnicmp)(const char*, const char*, Py_ssize_t) = NULL;
PyAPI_FUNC(int) PyOS_mystrnicmp(const char* a, const char* b, Py_ssize_t c) {
    LOG("'%s'(0x%lx) '%s'(0x%lx) 0x%lx", a?a:"<null>", (unsigned long) a, b?b:"<null>", (unsigned long) b, (unsigned long) c);
    if (__target__PyOS_mystrnicmp == NULL) {
        __target__PyOS_mystrnicmp = resolveAPI("PyOS_mystrnicmp");
    }
    STATS_BEFORE(PyOS_mystrnicmp)
    int result = (int) __target__PyOS_mystrnicmp(a, b, c);
    STATS_AFTER(PyOS_mystrnicmp)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyOS_setsig, PyOS_mystrnicmp)
PyOS_sighandler_t (*__target__PyOS_setsig)(int, PyOS_sighandler_t) = NULL;
PyAPI_FUNC(PyOS_sighandler_t) PyOS_setsig(int a, PyOS_sighandler_t b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target__PyOS_setsig == NULL) {
        __target__PyOS_setsig = resolveAPI("PyOS_setsig");
    }
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
    if (__target__PyOS_string_to_double == NULL) {
        __target__PyOS_string_to_double = resolveAPI("PyOS_string_to_double");
    }
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
    if (__target__PyOS_strtol == NULL) {
        __target__PyOS_strtol = resolveAPI("PyOS_strtol");
    }
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
    if (__target__PyOS_strtoul == NULL) {
        __target__PyOS_strtoul = resolveAPI("PyOS_strtoul");
    }
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
    if (__target__PyOS_vsnprintf == NULL) {
        __target__PyOS_vsnprintf = resolveAPI("PyOS_vsnprintf");
    }
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
    if (__target__PyObject_ASCII == NULL) {
        __target__PyObject_ASCII = resolveAPI("PyObject_ASCII");
    }
    STATS_BEFORE(PyObject_ASCII)
    PyObject* result = (PyObject*) __target__PyObject_ASCII(a);
    STATS_AFTER(PyObject_ASCII)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyObject_AsCharBuffer, PyObject_ASCII)
int (*__target__PyObject_AsCharBuffer)(PyObject*, const char**, Py_ssize_t*) = NULL;
PyAPI_FUNC(int) PyObject_AsCharBuffer(PyObject* a, const char** b, Py_ssize_t* c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    if (__target__PyObject_AsCharBuffer == NULL) {
        __target__PyObject_AsCharBuffer = resolveAPI("PyObject_AsCharBuffer");
    }
    STATS_BEFORE(PyObject_AsCharBuffer)
    int result = (int) __target__PyObject_AsCharBuffer(a, b, c);
    STATS_AFTER(PyObject_AsCharBuffer)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyObject_AsFileDescriptor, PyObject_AsCharBuffer)
int (*__target__PyObject_AsFileDescriptor)(PyObject*) = NULL;
PyAPI_FUNC(int) PyObject_AsFileDescriptor(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyObject_AsFileDescriptor == NULL) {
        __target__PyObject_AsFileDescriptor = resolveAPI("PyObject_AsFileDescriptor");
    }
    STATS_BEFORE(PyObject_AsFileDescriptor)
    int result = (int) __target__PyObject_AsFileDescriptor(a);
    STATS_AFTER(PyObject_AsFileDescriptor)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyObject_AsReadBuffer, PyObject_AsFileDescriptor)
int (*__target__PyObject_AsReadBuffer)(PyObject*, const void**, Py_ssize_t*) = NULL;
PyAPI_FUNC(int) PyObject_AsReadBuffer(PyObject* a, const void** b, Py_ssize_t* c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    if (__target__PyObject_AsReadBuffer == NULL) {
        __target__PyObject_AsReadBuffer = resolveAPI("PyObject_AsReadBuffer");
    }
    STATS_BEFORE(PyObject_AsReadBuffer)
    int result = (int) __target__PyObject_AsReadBuffer(a, b, c);
    STATS_AFTER(PyObject_AsReadBuffer)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyObject_AsWriteBuffer, PyObject_AsReadBuffer)
int (*__target__PyObject_AsWriteBuffer)(PyObject*, void**, Py_ssize_t*) = NULL;
PyAPI_FUNC(int) PyObject_AsWriteBuffer(PyObject* a, void** b, Py_ssize_t* c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    if (__target__PyObject_AsWriteBuffer == NULL) {
        __target__PyObject_AsWriteBuffer = resolveAPI("PyObject_AsWriteBuffer");
    }
    STATS_BEFORE(PyObject_AsWriteBuffer)
    int result = (int) __target__PyObject_AsWriteBuffer(a, b, c);
    STATS_AFTER(PyObject_AsWriteBuffer)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyObject_Bytes, PyObject_AsWriteBuffer)
PyObject* (*__target__PyObject_Bytes)(PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyObject_Bytes(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyObject_Bytes == NULL) {
        __target__PyObject_Bytes = resolveAPI("PyObject_Bytes");
    }
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
    if (__target__PyObject_Call == NULL) {
        __target__PyObject_Call = resolveAPI("PyObject_Call");
    }
    STATS_BEFORE(PyObject_Call)
    PyObject* result = (PyObject*) __target__PyObject_Call(a, b, c);
    STATS_AFTER(PyObject_Call)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyObject_CallFinalizer, PyObject_Call)
void (*__target__PyObject_CallFinalizer)(PyObject*) = NULL;
PyAPI_FUNC(void) PyObject_CallFinalizer(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyObject_CallFinalizer == NULL) {
        __target__PyObject_CallFinalizer = resolveAPI("PyObject_CallFinalizer");
    }
    STATS_BEFORE(PyObject_CallFinalizer)
    __target__PyObject_CallFinalizer(a);
    STATS_AFTER(PyObject_CallFinalizer)
    LOG_AFTER_VOID
}
STATS_CONTAINER(PyObject_CallFinalizerFromDealloc, PyObject_CallFinalizer)
int (*__target__PyObject_CallFinalizerFromDealloc)(PyObject*) = NULL;
PyAPI_FUNC(int) PyObject_CallFinalizerFromDealloc(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyObject_CallFinalizerFromDealloc == NULL) {
        __target__PyObject_CallFinalizerFromDealloc = resolveAPI("PyObject_CallFinalizerFromDealloc");
    }
    STATS_BEFORE(PyObject_CallFinalizerFromDealloc)
    int result = (int) __target__PyObject_CallFinalizerFromDealloc(a);
    STATS_AFTER(PyObject_CallFinalizerFromDealloc)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyObject_CallNoArgs, PyObject_CallFinalizerFromDealloc)
PyObject* (*__target__PyObject_CallNoArgs)(PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyObject_CallNoArgs(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyObject_CallNoArgs == NULL) {
        __target__PyObject_CallNoArgs = resolveAPI("PyObject_CallNoArgs");
    }
    STATS_BEFORE(PyObject_CallNoArgs)
    PyObject* result = (PyObject*) __target__PyObject_CallNoArgs(a);
    STATS_AFTER(PyObject_CallNoArgs)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyObject_CallObject, PyObject_CallNoArgs)
PyObject* (*__target__PyObject_CallObject)(PyObject*, PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyObject_CallObject(PyObject* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target__PyObject_CallObject == NULL) {
        __target__PyObject_CallObject = resolveAPI("PyObject_CallObject");
    }
    STATS_BEFORE(PyObject_CallObject)
    PyObject* result = (PyObject*) __target__PyObject_CallObject(a, b);
    STATS_AFTER(PyObject_CallObject)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyObject_Calloc, PyObject_CallObject)
void* (*__target__PyObject_Calloc)(size_t, size_t) = NULL;
PyAPI_FUNC(void*) PyObject_Calloc(size_t a, size_t b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target__PyObject_Calloc == NULL) {
        __target__PyObject_Calloc = resolveAPI("PyObject_Calloc");
    }
    STATS_BEFORE(PyObject_Calloc)
    void* result = (void*) __target__PyObject_Calloc(a, b);
    STATS_AFTER(PyObject_Calloc)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyObject_CheckBuffer, PyObject_Calloc)
int (*__target__PyObject_CheckBuffer)(PyObject*) = NULL;
PyAPI_FUNC(int) PyObject_CheckBuffer(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyObject_CheckBuffer == NULL) {
        __target__PyObject_CheckBuffer = resolveAPI("PyObject_CheckBuffer");
    }
    STATS_BEFORE(PyObject_CheckBuffer)
    int result = (int) __target__PyObject_CheckBuffer(a);
    STATS_AFTER(PyObject_CheckBuffer)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyObject_CheckReadBuffer, PyObject_CheckBuffer)
int (*__target__PyObject_CheckReadBuffer)(PyObject*) = NULL;
PyAPI_FUNC(int) PyObject_CheckReadBuffer(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyObject_CheckReadBuffer == NULL) {
        __target__PyObject_CheckReadBuffer = resolveAPI("PyObject_CheckReadBuffer");
    }
    STATS_BEFORE(PyObject_CheckReadBuffer)
    int result = (int) __target__PyObject_CheckReadBuffer(a);
    STATS_AFTER(PyObject_CheckReadBuffer)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyObject_ClearWeakRefs, PyObject_CheckReadBuffer)
void (*__target__PyObject_ClearWeakRefs)(PyObject*) = NULL;
PyAPI_FUNC(void) PyObject_ClearWeakRefs(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyObject_ClearWeakRefs == NULL) {
        __target__PyObject_ClearWeakRefs = resolveAPI("PyObject_ClearWeakRefs");
    }
    STATS_BEFORE(PyObject_ClearWeakRefs)
    __target__PyObject_ClearWeakRefs(a);
    STATS_AFTER(PyObject_ClearWeakRefs)
    LOG_AFTER_VOID
}
STATS_CONTAINER(PyObject_CopyData, PyObject_ClearWeakRefs)
int (*__target__PyObject_CopyData)(PyObject*, PyObject*) = NULL;
PyAPI_FUNC(int) PyObject_CopyData(PyObject* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target__PyObject_CopyData == NULL) {
        __target__PyObject_CopyData = resolveAPI("PyObject_CopyData");
    }
    STATS_BEFORE(PyObject_CopyData)
    int result = (int) __target__PyObject_CopyData(a, b);
    STATS_AFTER(PyObject_CopyData)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyObject_DelItem, PyObject_CopyData)
int (*__target__PyObject_DelItem)(PyObject*, PyObject*) = NULL;
PyAPI_FUNC(int) PyObject_DelItem(PyObject* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target__PyObject_DelItem == NULL) {
        __target__PyObject_DelItem = resolveAPI("PyObject_DelItem");
    }
    STATS_BEFORE(PyObject_DelItem)
    int result = (int) __target__PyObject_DelItem(a, b);
    STATS_AFTER(PyObject_DelItem)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyObject_DelItemString, PyObject_DelItem)
int (*__target__PyObject_DelItemString)(PyObject*, const char*) = NULL;
PyAPI_FUNC(int) PyObject_DelItemString(PyObject* a, const char* b) {
    LOG("0x%lx '%s'(0x%lx)", (unsigned long) a, b?b:"<null>", (unsigned long) b);
    if (__target__PyObject_DelItemString == NULL) {
        __target__PyObject_DelItemString = resolveAPI("PyObject_DelItemString");
    }
    STATS_BEFORE(PyObject_DelItemString)
    int result = (int) __target__PyObject_DelItemString(a, b);
    STATS_AFTER(PyObject_DelItemString)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyObject_Dir, PyObject_DelItemString)
PyObject* (*__target__PyObject_Dir)(PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyObject_Dir(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyObject_Dir == NULL) {
        __target__PyObject_Dir = resolveAPI("PyObject_Dir");
    }
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
    if (__target__PyObject_Format == NULL) {
        __target__PyObject_Format = resolveAPI("PyObject_Format");
    }
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
    if (__target__PyObject_Free == NULL) {
        __target__PyObject_Free = resolveAPI("PyObject_Free");
    }
    STATS_BEFORE(PyObject_Free)
    __target__PyObject_Free(a);
    STATS_AFTER(PyObject_Free)
    LOG_AFTER_VOID
}
STATS_CONTAINER(PyObject_GC_Del, PyObject_Free)
void (*__target__PyObject_GC_Del)(void*) = NULL;
PyAPI_FUNC(void) PyObject_GC_Del(void* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyObject_GC_Del == NULL) {
        __target__PyObject_GC_Del = resolveAPI("PyObject_GC_Del");
    }
    STATS_BEFORE(PyObject_GC_Del)
    __target__PyObject_GC_Del(a);
    STATS_AFTER(PyObject_GC_Del)
    LOG_AFTER_VOID
}
STATS_CONTAINER(PyObject_GC_IsFinalized, PyObject_GC_Del)
int (*__target__PyObject_GC_IsFinalized)(PyObject*) = NULL;
PyAPI_FUNC(int) PyObject_GC_IsFinalized(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyObject_GC_IsFinalized == NULL) {
        __target__PyObject_GC_IsFinalized = resolveAPI("PyObject_GC_IsFinalized");
    }
    STATS_BEFORE(PyObject_GC_IsFinalized)
    int result = (int) __target__PyObject_GC_IsFinalized(a);
    STATS_AFTER(PyObject_GC_IsFinalized)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyObject_GC_IsTracked, PyObject_GC_IsFinalized)
int (*__target__PyObject_GC_IsTracked)(PyObject*) = NULL;
PyAPI_FUNC(int) PyObject_GC_IsTracked(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyObject_GC_IsTracked == NULL) {
        __target__PyObject_GC_IsTracked = resolveAPI("PyObject_GC_IsTracked");
    }
    STATS_BEFORE(PyObject_GC_IsTracked)
    int result = (int) __target__PyObject_GC_IsTracked(a);
    STATS_AFTER(PyObject_GC_IsTracked)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyObject_GC_Track, PyObject_GC_IsTracked)
void (*__target__PyObject_GC_Track)(void*) = NULL;
PyAPI_FUNC(void) PyObject_GC_Track(void* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyObject_GC_Track == NULL) {
        __target__PyObject_GC_Track = resolveAPI("PyObject_GC_Track");
    }
    STATS_BEFORE(PyObject_GC_Track)
    __target__PyObject_GC_Track(a);
    STATS_AFTER(PyObject_GC_Track)
    LOG_AFTER_VOID
}
STATS_CONTAINER(PyObject_GC_UnTrack, PyObject_GC_Track)
void (*__target__PyObject_GC_UnTrack)(void*) = NULL;
PyAPI_FUNC(void) PyObject_GC_UnTrack(void* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyObject_GC_UnTrack == NULL) {
        __target__PyObject_GC_UnTrack = resolveAPI("PyObject_GC_UnTrack");
    }
    STATS_BEFORE(PyObject_GC_UnTrack)
    __target__PyObject_GC_UnTrack(a);
    STATS_AFTER(PyObject_GC_UnTrack)
    LOG_AFTER_VOID
}
STATS_CONTAINER(PyObject_GET_WEAKREFS_LISTPTR, PyObject_GC_UnTrack)
PyObject** (*__target__PyObject_GET_WEAKREFS_LISTPTR)(PyObject*) = NULL;
PyAPI_FUNC(PyObject**) PyObject_GET_WEAKREFS_LISTPTR(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyObject_GET_WEAKREFS_LISTPTR == NULL) {
        __target__PyObject_GET_WEAKREFS_LISTPTR = resolveAPI("PyObject_GET_WEAKREFS_LISTPTR");
    }
    STATS_BEFORE(PyObject_GET_WEAKREFS_LISTPTR)
    PyObject** result = (PyObject**) __target__PyObject_GET_WEAKREFS_LISTPTR(a);
    STATS_AFTER(PyObject_GET_WEAKREFS_LISTPTR)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyObject_GenericGetAttr, PyObject_GET_WEAKREFS_LISTPTR)
PyObject* (*__target__PyObject_GenericGetAttr)(PyObject*, PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyObject_GenericGetAttr(PyObject* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target__PyObject_GenericGetAttr == NULL) {
        __target__PyObject_GenericGetAttr = resolveAPI("PyObject_GenericGetAttr");
    }
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
    if (__target__PyObject_GenericSetAttr == NULL) {
        __target__PyObject_GenericSetAttr = resolveAPI("PyObject_GenericSetAttr");
    }
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
    if (__target__PyObject_GenericSetDict == NULL) {
        __target__PyObject_GenericSetDict = resolveAPI("PyObject_GenericSetDict");
    }
    STATS_BEFORE(PyObject_GenericSetDict)
    int result = (int) __target__PyObject_GenericSetDict(a, b, c);
    STATS_AFTER(PyObject_GenericSetDict)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyObject_GetAIter, PyObject_GenericSetDict)
PyObject* (*__target__PyObject_GetAIter)(PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyObject_GetAIter(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyObject_GetAIter == NULL) {
        __target__PyObject_GetAIter = resolveAPI("PyObject_GetAIter");
    }
    STATS_BEFORE(PyObject_GetAIter)
    PyObject* result = (PyObject*) __target__PyObject_GetAIter(a);
    STATS_AFTER(PyObject_GetAIter)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyObject_GetArenaAllocator, PyObject_GetAIter)
void (*__target__PyObject_GetArenaAllocator)(PyObjectArenaAllocator*) = NULL;
PyAPI_FUNC(void) PyObject_GetArenaAllocator(PyObjectArenaAllocator* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyObject_GetArenaAllocator == NULL) {
        __target__PyObject_GetArenaAllocator = resolveAPI("PyObject_GetArenaAllocator");
    }
    STATS_BEFORE(PyObject_GetArenaAllocator)
    __target__PyObject_GetArenaAllocator(a);
    STATS_AFTER(PyObject_GetArenaAllocator)
    LOG_AFTER_VOID
}
STATS_CONTAINER(PyObject_GetAttr, PyObject_GetArenaAllocator)
PyObject* (*__target__PyObject_GetAttr)(PyObject*, PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyObject_GetAttr(PyObject* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target__PyObject_GetAttr == NULL) {
        __target__PyObject_GetAttr = resolveAPI("PyObject_GetAttr");
    }
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
    if (__target__PyObject_GetAttrString == NULL) {
        __target__PyObject_GetAttrString = resolveAPI("PyObject_GetAttrString");
    }
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
    if (__target__PyObject_GetBuffer == NULL) {
        __target__PyObject_GetBuffer = resolveAPI("PyObject_GetBuffer");
    }
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
    if (__target__PyObject_GetDoc == NULL) {
        __target__PyObject_GetDoc = resolveAPI("PyObject_GetDoc");
    }
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
    if (__target__PyObject_GetItem == NULL) {
        __target__PyObject_GetItem = resolveAPI("PyObject_GetItem");
    }
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
    if (__target__PyObject_GetIter == NULL) {
        __target__PyObject_GetIter = resolveAPI("PyObject_GetIter");
    }
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
    if (__target__PyObject_HasAttr == NULL) {
        __target__PyObject_HasAttr = resolveAPI("PyObject_HasAttr");
    }
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
    if (__target__PyObject_HasAttrString == NULL) {
        __target__PyObject_HasAttrString = resolveAPI("PyObject_HasAttrString");
    }
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
    if (__target__PyObject_Hash == NULL) {
        __target__PyObject_Hash = resolveAPI("PyObject_Hash");
    }
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
    if (__target__PyObject_HashNotImplemented == NULL) {
        __target__PyObject_HashNotImplemented = resolveAPI("PyObject_HashNotImplemented");
    }
    STATS_BEFORE(PyObject_HashNotImplemented)
    Py_hash_t result = (Py_hash_t) __target__PyObject_HashNotImplemented(a);
    STATS_AFTER(PyObject_HashNotImplemented)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyObject_IS_GC, PyObject_HashNotImplemented)
int (*__target__PyObject_IS_GC)(PyObject*) = NULL;
PyAPI_FUNC(int) PyObject_IS_GC(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyObject_IS_GC == NULL) {
        __target__PyObject_IS_GC = resolveAPI("PyObject_IS_GC");
    }
    STATS_BEFORE(PyObject_IS_GC)
    int result = (int) __target__PyObject_IS_GC(a);
    STATS_AFTER(PyObject_IS_GC)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyObject_Init, PyObject_IS_GC)
PyObject* (*__target__PyObject_Init)(PyObject*, PyTypeObject*) = NULL;
PyAPI_FUNC(PyObject*) PyObject_Init(PyObject* a, PyTypeObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target__PyObject_Init == NULL) {
        __target__PyObject_Init = resolveAPI("PyObject_Init");
    }
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
    if (__target__PyObject_InitVar == NULL) {
        __target__PyObject_InitVar = resolveAPI("PyObject_InitVar");
    }
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
    if (__target__PyObject_IsInstance == NULL) {
        __target__PyObject_IsInstance = resolveAPI("PyObject_IsInstance");
    }
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
    if (__target__PyObject_IsSubclass == NULL) {
        __target__PyObject_IsSubclass = resolveAPI("PyObject_IsSubclass");
    }
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
    if (__target__PyObject_IsTrue == NULL) {
        __target__PyObject_IsTrue = resolveAPI("PyObject_IsTrue");
    }
    STATS_BEFORE(PyObject_IsTrue)
    int result = (int) __target__PyObject_IsTrue(a);
    STATS_AFTER(PyObject_IsTrue)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyObject_Length, PyObject_IsTrue)
Py_ssize_t (*__target__PyObject_Length)(PyObject*) = NULL;
PyAPI_FUNC(Py_ssize_t) PyObject_Length(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyObject_Length == NULL) {
        __target__PyObject_Length = resolveAPI("PyObject_Length");
    }
    STATS_BEFORE(PyObject_Length)
    Py_ssize_t result = (Py_ssize_t) __target__PyObject_Length(a);
    STATS_AFTER(PyObject_Length)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyObject_LengthHint, PyObject_Length)
Py_ssize_t (*__target__PyObject_LengthHint)(PyObject*, Py_ssize_t) = NULL;
PyAPI_FUNC(Py_ssize_t) PyObject_LengthHint(PyObject* a, Py_ssize_t b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target__PyObject_LengthHint == NULL) {
        __target__PyObject_LengthHint = resolveAPI("PyObject_LengthHint");
    }
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
    if (__target__PyObject_Malloc == NULL) {
        __target__PyObject_Malloc = resolveAPI("PyObject_Malloc");
    }
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
    if (__target__PyObject_Not == NULL) {
        __target__PyObject_Not = resolveAPI("PyObject_Not");
    }
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
    if (__target__PyObject_Print == NULL) {
        __target__PyObject_Print = resolveAPI("PyObject_Print");
    }
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
    if (__target__PyObject_Realloc == NULL) {
        __target__PyObject_Realloc = resolveAPI("PyObject_Realloc");
    }
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
    if (__target__PyObject_Repr == NULL) {
        __target__PyObject_Repr = resolveAPI("PyObject_Repr");
    }
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
    if (__target__PyObject_RichCompare == NULL) {
        __target__PyObject_RichCompare = resolveAPI("PyObject_RichCompare");
    }
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
    if (__target__PyObject_RichCompareBool == NULL) {
        __target__PyObject_RichCompareBool = resolveAPI("PyObject_RichCompareBool");
    }
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
    if (__target__PyObject_SelfIter == NULL) {
        __target__PyObject_SelfIter = resolveAPI("PyObject_SelfIter");
    }
    STATS_BEFORE(PyObject_SelfIter)
    PyObject* result = (PyObject*) __target__PyObject_SelfIter(a);
    STATS_AFTER(PyObject_SelfIter)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyObject_SetArenaAllocator, PyObject_SelfIter)
void (*__target__PyObject_SetArenaAllocator)(PyObjectArenaAllocator*) = NULL;
PyAPI_FUNC(void) PyObject_SetArenaAllocator(PyObjectArenaAllocator* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyObject_SetArenaAllocator == NULL) {
        __target__PyObject_SetArenaAllocator = resolveAPI("PyObject_SetArenaAllocator");
    }
    STATS_BEFORE(PyObject_SetArenaAllocator)
    __target__PyObject_SetArenaAllocator(a);
    STATS_AFTER(PyObject_SetArenaAllocator)
    LOG_AFTER_VOID
}
STATS_CONTAINER(PyObject_SetAttr, PyObject_SetArenaAllocator)
int (*__target__PyObject_SetAttr)(PyObject*, PyObject*, PyObject*) = NULL;
PyAPI_FUNC(int) PyObject_SetAttr(PyObject* a, PyObject* b, PyObject* c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    if (__target__PyObject_SetAttr == NULL) {
        __target__PyObject_SetAttr = resolveAPI("PyObject_SetAttr");
    }
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
    if (__target__PyObject_SetAttrString == NULL) {
        __target__PyObject_SetAttrString = resolveAPI("PyObject_SetAttrString");
    }
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
    if (__target__PyObject_SetDoc == NULL) {
        __target__PyObject_SetDoc = resolveAPI("PyObject_SetDoc");
    }
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
    if (__target__PyObject_SetItem == NULL) {
        __target__PyObject_SetItem = resolveAPI("PyObject_SetItem");
    }
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
    if (__target__PyObject_Size == NULL) {
        __target__PyObject_Size = resolveAPI("PyObject_Size");
    }
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
    if (__target__PyObject_Str == NULL) {
        __target__PyObject_Str = resolveAPI("PyObject_Str");
    }
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
    if (__target__PyObject_Type == NULL) {
        __target__PyObject_Type = resolveAPI("PyObject_Type");
    }
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
    if (__target__PyObject_VectorcallDict == NULL) {
        __target__PyObject_VectorcallDict = resolveAPI("PyObject_VectorcallDict");
    }
    STATS_BEFORE(PyObject_VectorcallDict)
    PyObject* result = (PyObject*) __target__PyObject_VectorcallDict(a, b, c, d);
    STATS_AFTER(PyObject_VectorcallDict)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyObject_VectorcallMethod, PyObject_VectorcallDict)
PyObject* (*__target__PyObject_VectorcallMethod)(PyObject*, PyObject*const*, size_t, PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyObject_VectorcallMethod(PyObject* a, PyObject*const* b, size_t c, PyObject* d) {
    LOG("0x%lx 0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c, (unsigned long) d);
    if (__target__PyObject_VectorcallMethod == NULL) {
        __target__PyObject_VectorcallMethod = resolveAPI("PyObject_VectorcallMethod");
    }
    STATS_BEFORE(PyObject_VectorcallMethod)
    PyObject* result = (PyObject*) __target__PyObject_VectorcallMethod(a, b, c, d);
    STATS_AFTER(PyObject_VectorcallMethod)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyPickleBuffer_FromObject, PyObject_VectorcallMethod)
PyObject* (*__target__PyPickleBuffer_FromObject)(PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyPickleBuffer_FromObject(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyPickleBuffer_FromObject == NULL) {
        __target__PyPickleBuffer_FromObject = resolveAPI("PyPickleBuffer_FromObject");
    }
    STATS_BEFORE(PyPickleBuffer_FromObject)
    PyObject* result = (PyObject*) __target__PyPickleBuffer_FromObject(a);
    STATS_AFTER(PyPickleBuffer_FromObject)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyPickleBuffer_GetBuffer, PyPickleBuffer_FromObject)
const Py_buffer* (*__target__PyPickleBuffer_GetBuffer)(PyObject*) = NULL;
PyAPI_FUNC(const Py_buffer*) PyPickleBuffer_GetBuffer(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyPickleBuffer_GetBuffer == NULL) {
        __target__PyPickleBuffer_GetBuffer = resolveAPI("PyPickleBuffer_GetBuffer");
    }
    STATS_BEFORE(PyPickleBuffer_GetBuffer)
    const Py_buffer* result = (const Py_buffer*) __target__PyPickleBuffer_GetBuffer(a);
    STATS_AFTER(PyPickleBuffer_GetBuffer)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyPickleBuffer_Release, PyPickleBuffer_GetBuffer)
int (*__target__PyPickleBuffer_Release)(PyObject*) = NULL;
PyAPI_FUNC(int) PyPickleBuffer_Release(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyPickleBuffer_Release == NULL) {
        __target__PyPickleBuffer_Release = resolveAPI("PyPickleBuffer_Release");
    }
    STATS_BEFORE(PyPickleBuffer_Release)
    int result = (int) __target__PyPickleBuffer_Release(a);
    STATS_AFTER(PyPickleBuffer_Release)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyPreConfig_InitIsolatedConfig, PyPickleBuffer_Release)
void (*__target__PyPreConfig_InitIsolatedConfig)(PyPreConfig*) = NULL;
PyAPI_FUNC(void) PyPreConfig_InitIsolatedConfig(PyPreConfig* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyPreConfig_InitIsolatedConfig == NULL) {
        __target__PyPreConfig_InitIsolatedConfig = resolveAPI("PyPreConfig_InitIsolatedConfig");
    }
    STATS_BEFORE(PyPreConfig_InitIsolatedConfig)
    __target__PyPreConfig_InitIsolatedConfig(a);
    STATS_AFTER(PyPreConfig_InitIsolatedConfig)
    LOG_AFTER_VOID
}
STATS_CONTAINER(PyPreConfig_InitPythonConfig, PyPreConfig_InitIsolatedConfig)
void (*__target__PyPreConfig_InitPythonConfig)(PyPreConfig*) = NULL;
PyAPI_FUNC(void) PyPreConfig_InitPythonConfig(PyPreConfig* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyPreConfig_InitPythonConfig == NULL) {
        __target__PyPreConfig_InitPythonConfig = resolveAPI("PyPreConfig_InitPythonConfig");
    }
    STATS_BEFORE(PyPreConfig_InitPythonConfig)
    __target__PyPreConfig_InitPythonConfig(a);
    STATS_AFTER(PyPreConfig_InitPythonConfig)
    LOG_AFTER_VOID
}
STATS_CONTAINER(PyRun_AnyFile, PyPreConfig_InitPythonConfig)
int (*__target__PyRun_AnyFile)(FILE*, const char*) = NULL;
PyAPI_FUNC(int) PyRun_AnyFile(FILE* a, const char* b) {
    LOG("0x%lx '%s'(0x%lx)", (unsigned long) a, b?b:"<null>", (unsigned long) b);
    if (__target__PyRun_AnyFile == NULL) {
        __target__PyRun_AnyFile = resolveAPI("PyRun_AnyFile");
    }
    STATS_BEFORE(PyRun_AnyFile)
    int result = (int) __target__PyRun_AnyFile(a, b);
    STATS_AFTER(PyRun_AnyFile)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyRun_AnyFileEx, PyRun_AnyFile)
int (*__target__PyRun_AnyFileEx)(FILE*, const char*, int) = NULL;
PyAPI_FUNC(int) PyRun_AnyFileEx(FILE* a, const char* b, int c) {
    LOG("0x%lx '%s'(0x%lx) 0x%lx", (unsigned long) a, b?b:"<null>", (unsigned long) b, (unsigned long) c);
    if (__target__PyRun_AnyFileEx == NULL) {
        __target__PyRun_AnyFileEx = resolveAPI("PyRun_AnyFileEx");
    }
    STATS_BEFORE(PyRun_AnyFileEx)
    int result = (int) __target__PyRun_AnyFileEx(a, b, c);
    STATS_AFTER(PyRun_AnyFileEx)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyRun_AnyFileExFlags, PyRun_AnyFileEx)
int (*__target__PyRun_AnyFileExFlags)(FILE*, const char*, int, PyCompilerFlags*) = NULL;
PyAPI_FUNC(int) PyRun_AnyFileExFlags(FILE* a, const char* b, int c, PyCompilerFlags* d) {
    LOG("0x%lx '%s'(0x%lx) 0x%lx 0x%lx", (unsigned long) a, b?b:"<null>", (unsigned long) b, (unsigned long) c, (unsigned long) d);
    if (__target__PyRun_AnyFileExFlags == NULL) {
        __target__PyRun_AnyFileExFlags = resolveAPI("PyRun_AnyFileExFlags");
    }
    STATS_BEFORE(PyRun_AnyFileExFlags)
    int result = (int) __target__PyRun_AnyFileExFlags(a, b, c, d);
    STATS_AFTER(PyRun_AnyFileExFlags)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyRun_AnyFileFlags, PyRun_AnyFileExFlags)
int (*__target__PyRun_AnyFileFlags)(FILE*, const char*, PyCompilerFlags*) = NULL;
PyAPI_FUNC(int) PyRun_AnyFileFlags(FILE* a, const char* b, PyCompilerFlags* c) {
    LOG("0x%lx '%s'(0x%lx) 0x%lx", (unsigned long) a, b?b:"<null>", (unsigned long) b, (unsigned long) c);
    if (__target__PyRun_AnyFileFlags == NULL) {
        __target__PyRun_AnyFileFlags = resolveAPI("PyRun_AnyFileFlags");
    }
    STATS_BEFORE(PyRun_AnyFileFlags)
    int result = (int) __target__PyRun_AnyFileFlags(a, b, c);
    STATS_AFTER(PyRun_AnyFileFlags)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyRun_File, PyRun_AnyFileFlags)
PyObject* (*__target__PyRun_File)(FILE*, const char*, int, PyObject*, PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyRun_File(FILE* a, const char* b, int c, PyObject* d, PyObject* e) {
    LOG("0x%lx '%s'(0x%lx) 0x%lx 0x%lx 0x%lx", (unsigned long) a, b?b:"<null>", (unsigned long) b, (unsigned long) c, (unsigned long) d, (unsigned long) e);
    if (__target__PyRun_File == NULL) {
        __target__PyRun_File = resolveAPI("PyRun_File");
    }
    STATS_BEFORE(PyRun_File)
    PyObject* result = (PyObject*) __target__PyRun_File(a, b, c, d, e);
    STATS_AFTER(PyRun_File)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyRun_FileEx, PyRun_File)
PyObject* (*__target__PyRun_FileEx)(FILE*, const char*, int, PyObject*, PyObject*, int) = NULL;
PyAPI_FUNC(PyObject*) PyRun_FileEx(FILE* a, const char* b, int c, PyObject* d, PyObject* e, int f) {
    LOG("0x%lx '%s'(0x%lx) 0x%lx 0x%lx 0x%lx 0x%lx", (unsigned long) a, b?b:"<null>", (unsigned long) b, (unsigned long) c, (unsigned long) d, (unsigned long) e, (unsigned long) f);
    if (__target__PyRun_FileEx == NULL) {
        __target__PyRun_FileEx = resolveAPI("PyRun_FileEx");
    }
    STATS_BEFORE(PyRun_FileEx)
    PyObject* result = (PyObject*) __target__PyRun_FileEx(a, b, c, d, e, f);
    STATS_AFTER(PyRun_FileEx)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyRun_FileExFlags, PyRun_FileEx)
PyObject* (*__target__PyRun_FileExFlags)(FILE*, const char*, int, PyObject*, PyObject*, int, PyCompilerFlags*) = NULL;
PyAPI_FUNC(PyObject*) PyRun_FileExFlags(FILE* a, const char* b, int c, PyObject* d, PyObject* e, int f, PyCompilerFlags* g) {
    LOG("0x%lx '%s'(0x%lx) 0x%lx 0x%lx 0x%lx 0x%lx 0x%lx", (unsigned long) a, b?b:"<null>", (unsigned long) b, (unsigned long) c, (unsigned long) d, (unsigned long) e, (unsigned long) f, (unsigned long) g);
    if (__target__PyRun_FileExFlags == NULL) {
        __target__PyRun_FileExFlags = resolveAPI("PyRun_FileExFlags");
    }
    STATS_BEFORE(PyRun_FileExFlags)
    PyObject* result = (PyObject*) __target__PyRun_FileExFlags(a, b, c, d, e, f, g);
    STATS_AFTER(PyRun_FileExFlags)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyRun_FileFlags, PyRun_FileExFlags)
PyObject* (*__target__PyRun_FileFlags)(FILE*, const char*, int, PyObject*, PyObject*, PyCompilerFlags*) = NULL;
PyAPI_FUNC(PyObject*) PyRun_FileFlags(FILE* a, const char* b, int c, PyObject* d, PyObject* e, PyCompilerFlags* f) {
    LOG("0x%lx '%s'(0x%lx) 0x%lx 0x%lx 0x%lx 0x%lx", (unsigned long) a, b?b:"<null>", (unsigned long) b, (unsigned long) c, (unsigned long) d, (unsigned long) e, (unsigned long) f);
    if (__target__PyRun_FileFlags == NULL) {
        __target__PyRun_FileFlags = resolveAPI("PyRun_FileFlags");
    }
    STATS_BEFORE(PyRun_FileFlags)
    PyObject* result = (PyObject*) __target__PyRun_FileFlags(a, b, c, d, e, f);
    STATS_AFTER(PyRun_FileFlags)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyRun_InteractiveLoop, PyRun_FileFlags)
int (*__target__PyRun_InteractiveLoop)(FILE*, const char*) = NULL;
PyAPI_FUNC(int) PyRun_InteractiveLoop(FILE* a, const char* b) {
    LOG("0x%lx '%s'(0x%lx)", (unsigned long) a, b?b:"<null>", (unsigned long) b);
    if (__target__PyRun_InteractiveLoop == NULL) {
        __target__PyRun_InteractiveLoop = resolveAPI("PyRun_InteractiveLoop");
    }
    STATS_BEFORE(PyRun_InteractiveLoop)
    int result = (int) __target__PyRun_InteractiveLoop(a, b);
    STATS_AFTER(PyRun_InteractiveLoop)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyRun_InteractiveLoopFlags, PyRun_InteractiveLoop)
int (*__target__PyRun_InteractiveLoopFlags)(FILE*, const char*, PyCompilerFlags*) = NULL;
PyAPI_FUNC(int) PyRun_InteractiveLoopFlags(FILE* a, const char* b, PyCompilerFlags* c) {
    LOG("0x%lx '%s'(0x%lx) 0x%lx", (unsigned long) a, b?b:"<null>", (unsigned long) b, (unsigned long) c);
    if (__target__PyRun_InteractiveLoopFlags == NULL) {
        __target__PyRun_InteractiveLoopFlags = resolveAPI("PyRun_InteractiveLoopFlags");
    }
    STATS_BEFORE(PyRun_InteractiveLoopFlags)
    int result = (int) __target__PyRun_InteractiveLoopFlags(a, b, c);
    STATS_AFTER(PyRun_InteractiveLoopFlags)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyRun_InteractiveOne, PyRun_InteractiveLoopFlags)
int (*__target__PyRun_InteractiveOne)(FILE*, const char*) = NULL;
PyAPI_FUNC(int) PyRun_InteractiveOne(FILE* a, const char* b) {
    LOG("0x%lx '%s'(0x%lx)", (unsigned long) a, b?b:"<null>", (unsigned long) b);
    if (__target__PyRun_InteractiveOne == NULL) {
        __target__PyRun_InteractiveOne = resolveAPI("PyRun_InteractiveOne");
    }
    STATS_BEFORE(PyRun_InteractiveOne)
    int result = (int) __target__PyRun_InteractiveOne(a, b);
    STATS_AFTER(PyRun_InteractiveOne)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyRun_InteractiveOneFlags, PyRun_InteractiveOne)
int (*__target__PyRun_InteractiveOneFlags)(FILE*, const char*, PyCompilerFlags*) = NULL;
PyAPI_FUNC(int) PyRun_InteractiveOneFlags(FILE* a, const char* b, PyCompilerFlags* c) {
    LOG("0x%lx '%s'(0x%lx) 0x%lx", (unsigned long) a, b?b:"<null>", (unsigned long) b, (unsigned long) c);
    if (__target__PyRun_InteractiveOneFlags == NULL) {
        __target__PyRun_InteractiveOneFlags = resolveAPI("PyRun_InteractiveOneFlags");
    }
    STATS_BEFORE(PyRun_InteractiveOneFlags)
    int result = (int) __target__PyRun_InteractiveOneFlags(a, b, c);
    STATS_AFTER(PyRun_InteractiveOneFlags)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyRun_InteractiveOneObject, PyRun_InteractiveOneFlags)
int (*__target__PyRun_InteractiveOneObject)(FILE*, PyObject*, PyCompilerFlags*) = NULL;
PyAPI_FUNC(int) PyRun_InteractiveOneObject(FILE* a, PyObject* b, PyCompilerFlags* c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    if (__target__PyRun_InteractiveOneObject == NULL) {
        __target__PyRun_InteractiveOneObject = resolveAPI("PyRun_InteractiveOneObject");
    }
    STATS_BEFORE(PyRun_InteractiveOneObject)
    int result = (int) __target__PyRun_InteractiveOneObject(a, b, c);
    STATS_AFTER(PyRun_InteractiveOneObject)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyRun_SimpleFile, PyRun_InteractiveOneObject)
int (*__target__PyRun_SimpleFile)(FILE*, const char*) = NULL;
PyAPI_FUNC(int) PyRun_SimpleFile(FILE* a, const char* b) {
    LOG("0x%lx '%s'(0x%lx)", (unsigned long) a, b?b:"<null>", (unsigned long) b);
    if (__target__PyRun_SimpleFile == NULL) {
        __target__PyRun_SimpleFile = resolveAPI("PyRun_SimpleFile");
    }
    STATS_BEFORE(PyRun_SimpleFile)
    int result = (int) __target__PyRun_SimpleFile(a, b);
    STATS_AFTER(PyRun_SimpleFile)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyRun_SimpleFileEx, PyRun_SimpleFile)
int (*__target__PyRun_SimpleFileEx)(FILE*, const char*, int) = NULL;
PyAPI_FUNC(int) PyRun_SimpleFileEx(FILE* a, const char* b, int c) {
    LOG("0x%lx '%s'(0x%lx) 0x%lx", (unsigned long) a, b?b:"<null>", (unsigned long) b, (unsigned long) c);
    if (__target__PyRun_SimpleFileEx == NULL) {
        __target__PyRun_SimpleFileEx = resolveAPI("PyRun_SimpleFileEx");
    }
    STATS_BEFORE(PyRun_SimpleFileEx)
    int result = (int) __target__PyRun_SimpleFileEx(a, b, c);
    STATS_AFTER(PyRun_SimpleFileEx)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyRun_SimpleFileExFlags, PyRun_SimpleFileEx)
int (*__target__PyRun_SimpleFileExFlags)(FILE*, const char*, int, PyCompilerFlags*) = NULL;
PyAPI_FUNC(int) PyRun_SimpleFileExFlags(FILE* a, const char* b, int c, PyCompilerFlags* d) {
    LOG("0x%lx '%s'(0x%lx) 0x%lx 0x%lx", (unsigned long) a, b?b:"<null>", (unsigned long) b, (unsigned long) c, (unsigned long) d);
    if (__target__PyRun_SimpleFileExFlags == NULL) {
        __target__PyRun_SimpleFileExFlags = resolveAPI("PyRun_SimpleFileExFlags");
    }
    STATS_BEFORE(PyRun_SimpleFileExFlags)
    int result = (int) __target__PyRun_SimpleFileExFlags(a, b, c, d);
    STATS_AFTER(PyRun_SimpleFileExFlags)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyRun_SimpleString, PyRun_SimpleFileExFlags)
int (*__target__PyRun_SimpleString)(const char*) = NULL;
PyAPI_FUNC(int) PyRun_SimpleString(const char* a) {
    LOG("'%s'(0x%lx)", a?a:"<null>", (unsigned long) a);
    if (__target__PyRun_SimpleString == NULL) {
        __target__PyRun_SimpleString = resolveAPI("PyRun_SimpleString");
    }
    STATS_BEFORE(PyRun_SimpleString)
    int result = (int) __target__PyRun_SimpleString(a);
    STATS_AFTER(PyRun_SimpleString)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyRun_SimpleStringFlags, PyRun_SimpleString)
int (*__target__PyRun_SimpleStringFlags)(const char*, PyCompilerFlags*) = NULL;
PyAPI_FUNC(int) PyRun_SimpleStringFlags(const char* a, PyCompilerFlags* b) {
    LOG("'%s'(0x%lx) 0x%lx", a?a:"<null>", (unsigned long) a, (unsigned long) b);
    if (__target__PyRun_SimpleStringFlags == NULL) {
        __target__PyRun_SimpleStringFlags = resolveAPI("PyRun_SimpleStringFlags");
    }
    STATS_BEFORE(PyRun_SimpleStringFlags)
    int result = (int) __target__PyRun_SimpleStringFlags(a, b);
    STATS_AFTER(PyRun_SimpleStringFlags)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyRun_String, PyRun_SimpleStringFlags)
PyObject* (*__target__PyRun_String)(const char*, int, PyObject*, PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyRun_String(const char* a, int b, PyObject* c, PyObject* d) {
    LOG("'%s'(0x%lx) 0x%lx 0x%lx 0x%lx", a?a:"<null>", (unsigned long) a, (unsigned long) b, (unsigned long) c, (unsigned long) d);
    if (__target__PyRun_String == NULL) {
        __target__PyRun_String = resolveAPI("PyRun_String");
    }
    STATS_BEFORE(PyRun_String)
    PyObject* result = (PyObject*) __target__PyRun_String(a, b, c, d);
    STATS_AFTER(PyRun_String)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyRun_StringFlags, PyRun_String)
PyObject* (*__target__PyRun_StringFlags)(const char*, int, PyObject*, PyObject*, PyCompilerFlags*) = NULL;
PyAPI_FUNC(PyObject*) PyRun_StringFlags(const char* a, int b, PyObject* c, PyObject* d, PyCompilerFlags* e) {
    LOG("'%s'(0x%lx) 0x%lx 0x%lx 0x%lx 0x%lx", a?a:"<null>", (unsigned long) a, (unsigned long) b, (unsigned long) c, (unsigned long) d, (unsigned long) e);
    if (__target__PyRun_StringFlags == NULL) {
        __target__PyRun_StringFlags = resolveAPI("PyRun_StringFlags");
    }
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
    if (__target__PySeqIter_New == NULL) {
        __target__PySeqIter_New = resolveAPI("PySeqIter_New");
    }
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
    if (__target__PySequence_Check == NULL) {
        __target__PySequence_Check = resolveAPI("PySequence_Check");
    }
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
    if (__target__PySequence_Concat == NULL) {
        __target__PySequence_Concat = resolveAPI("PySequence_Concat");
    }
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
    if (__target__PySequence_Contains == NULL) {
        __target__PySequence_Contains = resolveAPI("PySequence_Contains");
    }
    STATS_BEFORE(PySequence_Contains)
    int result = (int) __target__PySequence_Contains(a, b);
    STATS_AFTER(PySequence_Contains)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PySequence_Count, PySequence_Contains)
Py_ssize_t (*__target__PySequence_Count)(PyObject*, PyObject*) = NULL;
PyAPI_FUNC(Py_ssize_t) PySequence_Count(PyObject* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target__PySequence_Count == NULL) {
        __target__PySequence_Count = resolveAPI("PySequence_Count");
    }
    STATS_BEFORE(PySequence_Count)
    Py_ssize_t result = (Py_ssize_t) __target__PySequence_Count(a, b);
    STATS_AFTER(PySequence_Count)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PySequence_DelItem, PySequence_Count)
int (*__target__PySequence_DelItem)(PyObject*, Py_ssize_t) = NULL;
PyAPI_FUNC(int) PySequence_DelItem(PyObject* a, Py_ssize_t b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target__PySequence_DelItem == NULL) {
        __target__PySequence_DelItem = resolveAPI("PySequence_DelItem");
    }
    STATS_BEFORE(PySequence_DelItem)
    int result = (int) __target__PySequence_DelItem(a, b);
    STATS_AFTER(PySequence_DelItem)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PySequence_DelSlice, PySequence_DelItem)
int (*__target__PySequence_DelSlice)(PyObject*, Py_ssize_t, Py_ssize_t) = NULL;
PyAPI_FUNC(int) PySequence_DelSlice(PyObject* a, Py_ssize_t b, Py_ssize_t c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    if (__target__PySequence_DelSlice == NULL) {
        __target__PySequence_DelSlice = resolveAPI("PySequence_DelSlice");
    }
    STATS_BEFORE(PySequence_DelSlice)
    int result = (int) __target__PySequence_DelSlice(a, b, c);
    STATS_AFTER(PySequence_DelSlice)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PySequence_Fast, PySequence_DelSlice)
PyObject* (*__target__PySequence_Fast)(PyObject*, const char*) = NULL;
PyAPI_FUNC(PyObject*) PySequence_Fast(PyObject* a, const char* b) {
    LOG("0x%lx '%s'(0x%lx)", (unsigned long) a, b?b:"<null>", (unsigned long) b);
    if (__target__PySequence_Fast == NULL) {
        __target__PySequence_Fast = resolveAPI("PySequence_Fast");
    }
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
    if (__target__PySequence_GetItem == NULL) {
        __target__PySequence_GetItem = resolveAPI("PySequence_GetItem");
    }
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
    if (__target__PySequence_GetSlice == NULL) {
        __target__PySequence_GetSlice = resolveAPI("PySequence_GetSlice");
    }
    STATS_BEFORE(PySequence_GetSlice)
    PyObject* result = (PyObject*) __target__PySequence_GetSlice(a, b, c);
    STATS_AFTER(PySequence_GetSlice)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PySequence_In, PySequence_GetSlice)
int (*__target__PySequence_In)(PyObject*, PyObject*) = NULL;
PyAPI_FUNC(int) PySequence_In(PyObject* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target__PySequence_In == NULL) {
        __target__PySequence_In = resolveAPI("PySequence_In");
    }
    STATS_BEFORE(PySequence_In)
    int result = (int) __target__PySequence_In(a, b);
    STATS_AFTER(PySequence_In)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PySequence_InPlaceConcat, PySequence_In)
PyObject* (*__target__PySequence_InPlaceConcat)(PyObject*, PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PySequence_InPlaceConcat(PyObject* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target__PySequence_InPlaceConcat == NULL) {
        __target__PySequence_InPlaceConcat = resolveAPI("PySequence_InPlaceConcat");
    }
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
    if (__target__PySequence_InPlaceRepeat == NULL) {
        __target__PySequence_InPlaceRepeat = resolveAPI("PySequence_InPlaceRepeat");
    }
    STATS_BEFORE(PySequence_InPlaceRepeat)
    PyObject* result = (PyObject*) __target__PySequence_InPlaceRepeat(a, b);
    STATS_AFTER(PySequence_InPlaceRepeat)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PySequence_Index, PySequence_InPlaceRepeat)
Py_ssize_t (*__target__PySequence_Index)(PyObject*, PyObject*) = NULL;
PyAPI_FUNC(Py_ssize_t) PySequence_Index(PyObject* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target__PySequence_Index == NULL) {
        __target__PySequence_Index = resolveAPI("PySequence_Index");
    }
    STATS_BEFORE(PySequence_Index)
    Py_ssize_t result = (Py_ssize_t) __target__PySequence_Index(a, b);
    STATS_AFTER(PySequence_Index)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PySequence_Length, PySequence_Index)
Py_ssize_t (*__target__PySequence_Length)(PyObject*) = NULL;
PyAPI_FUNC(Py_ssize_t) PySequence_Length(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PySequence_Length == NULL) {
        __target__PySequence_Length = resolveAPI("PySequence_Length");
    }
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
    if (__target__PySequence_List == NULL) {
        __target__PySequence_List = resolveAPI("PySequence_List");
    }
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
    if (__target__PySequence_Repeat == NULL) {
        __target__PySequence_Repeat = resolveAPI("PySequence_Repeat");
    }
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
    if (__target__PySequence_SetItem == NULL) {
        __target__PySequence_SetItem = resolveAPI("PySequence_SetItem");
    }
    STATS_BEFORE(PySequence_SetItem)
    int result = (int) __target__PySequence_SetItem(a, b, c);
    STATS_AFTER(PySequence_SetItem)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PySequence_SetSlice, PySequence_SetItem)
int (*__target__PySequence_SetSlice)(PyObject*, Py_ssize_t, Py_ssize_t, PyObject*) = NULL;
PyAPI_FUNC(int) PySequence_SetSlice(PyObject* a, Py_ssize_t b, Py_ssize_t c, PyObject* d) {
    LOG("0x%lx 0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c, (unsigned long) d);
    if (__target__PySequence_SetSlice == NULL) {
        __target__PySequence_SetSlice = resolveAPI("PySequence_SetSlice");
    }
    STATS_BEFORE(PySequence_SetSlice)
    int result = (int) __target__PySequence_SetSlice(a, b, c, d);
    STATS_AFTER(PySequence_SetSlice)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PySequence_Size, PySequence_SetSlice)
Py_ssize_t (*__target__PySequence_Size)(PyObject*) = NULL;
PyAPI_FUNC(Py_ssize_t) PySequence_Size(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PySequence_Size == NULL) {
        __target__PySequence_Size = resolveAPI("PySequence_Size");
    }
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
    if (__target__PySequence_Tuple == NULL) {
        __target__PySequence_Tuple = resolveAPI("PySequence_Tuple");
    }
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
    if (__target__PySet_Add == NULL) {
        __target__PySet_Add = resolveAPI("PySet_Add");
    }
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
    if (__target__PySet_Clear == NULL) {
        __target__PySet_Clear = resolveAPI("PySet_Clear");
    }
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
    if (__target__PySet_Contains == NULL) {
        __target__PySet_Contains = resolveAPI("PySet_Contains");
    }
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
    if (__target__PySet_Discard == NULL) {
        __target__PySet_Discard = resolveAPI("PySet_Discard");
    }
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
    if (__target__PySet_New == NULL) {
        __target__PySet_New = resolveAPI("PySet_New");
    }
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
    if (__target__PySet_Pop == NULL) {
        __target__PySet_Pop = resolveAPI("PySet_Pop");
    }
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
    if (__target__PySet_Size == NULL) {
        __target__PySet_Size = resolveAPI("PySet_Size");
    }
    STATS_BEFORE(PySet_Size)
    Py_ssize_t result = (Py_ssize_t) __target__PySet_Size(a);
    STATS_AFTER(PySet_Size)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PySignal_SetWakeupFd, PySet_Size)
int (*__target__PySignal_SetWakeupFd)(int) = NULL;
PyAPI_FUNC(int) PySignal_SetWakeupFd(int a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PySignal_SetWakeupFd == NULL) {
        __target__PySignal_SetWakeupFd = resolveAPI("PySignal_SetWakeupFd");
    }
    STATS_BEFORE(PySignal_SetWakeupFd)
    int result = (int) __target__PySignal_SetWakeupFd(a);
    STATS_AFTER(PySignal_SetWakeupFd)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PySlice_AdjustIndices, PySignal_SetWakeupFd)
Py_ssize_t (*__target__PySlice_AdjustIndices)(Py_ssize_t, Py_ssize_t*, Py_ssize_t*, Py_ssize_t) = NULL;
PyAPI_FUNC(Py_ssize_t) PySlice_AdjustIndices(Py_ssize_t a, Py_ssize_t* b, Py_ssize_t* c, Py_ssize_t d) {
    LOG("0x%lx 0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c, (unsigned long) d);
    if (__target__PySlice_AdjustIndices == NULL) {
        __target__PySlice_AdjustIndices = resolveAPI("PySlice_AdjustIndices");
    }
    STATS_BEFORE(PySlice_AdjustIndices)
    Py_ssize_t result = (Py_ssize_t) __target__PySlice_AdjustIndices(a, b, c, d);
    STATS_AFTER(PySlice_AdjustIndices)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PySlice_GetIndices, PySlice_AdjustIndices)
int (*__target__PySlice_GetIndices)(PyObject*, Py_ssize_t, Py_ssize_t*, Py_ssize_t*, Py_ssize_t*) = NULL;
PyAPI_FUNC(int) PySlice_GetIndices(PyObject* a, Py_ssize_t b, Py_ssize_t* c, Py_ssize_t* d, Py_ssize_t* e) {
    LOG("0x%lx 0x%lx 0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c, (unsigned long) d, (unsigned long) e);
    if (__target__PySlice_GetIndices == NULL) {
        __target__PySlice_GetIndices = resolveAPI("PySlice_GetIndices");
    }
    STATS_BEFORE(PySlice_GetIndices)
    int result = (int) __target__PySlice_GetIndices(a, b, c, d, e);
    STATS_AFTER(PySlice_GetIndices)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PySlice_GetIndicesEx, PySlice_GetIndices)
int (*__target__PySlice_GetIndicesEx)(PyObject*, Py_ssize_t, Py_ssize_t*, Py_ssize_t*, Py_ssize_t*, Py_ssize_t*) = NULL;
PyAPI_FUNC(int) PySlice_GetIndicesEx(PyObject* a, Py_ssize_t b, Py_ssize_t* c, Py_ssize_t* d, Py_ssize_t* e, Py_ssize_t* f) {
    LOG("0x%lx 0x%lx 0x%lx 0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c, (unsigned long) d, (unsigned long) e, (unsigned long) f);
    if (__target__PySlice_GetIndicesEx == NULL) {
        __target__PySlice_GetIndicesEx = resolveAPI("PySlice_GetIndicesEx");
    }
    STATS_BEFORE(PySlice_GetIndicesEx)
    int result = (int) __target__PySlice_GetIndicesEx(a, b, c, d, e, f);
    STATS_AFTER(PySlice_GetIndicesEx)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PySlice_New, PySlice_GetIndicesEx)
PyObject* (*__target__PySlice_New)(PyObject*, PyObject*, PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PySlice_New(PyObject* a, PyObject* b, PyObject* c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    if (__target__PySlice_New == NULL) {
        __target__PySlice_New = resolveAPI("PySlice_New");
    }
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
    if (__target__PySlice_Start == NULL) {
        __target__PySlice_Start = resolveAPI("PySlice_Start");
    }
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
    if (__target__PySlice_Step == NULL) {
        __target__PySlice_Step = resolveAPI("PySlice_Step");
    }
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
    if (__target__PySlice_Stop == NULL) {
        __target__PySlice_Stop = resolveAPI("PySlice_Stop");
    }
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
    if (__target__PySlice_Unpack == NULL) {
        __target__PySlice_Unpack = resolveAPI("PySlice_Unpack");
    }
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
    if (__target__PyState_AddModule == NULL) {
        __target__PyState_AddModule = resolveAPI("PyState_AddModule");
    }
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
    if (__target__PyState_FindModule == NULL) {
        __target__PyState_FindModule = resolveAPI("PyState_FindModule");
    }
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
    if (__target__PyState_RemoveModule == NULL) {
        __target__PyState_RemoveModule = resolveAPI("PyState_RemoveModule");
    }
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
    if (__target__PyStaticMethod_New == NULL) {
        __target__PyStaticMethod_New = resolveAPI("PyStaticMethod_New");
    }
    STATS_BEFORE(PyStaticMethod_New)
    PyObject* result = (PyObject*) __target__PyStaticMethod_New(a);
    STATS_AFTER(PyStaticMethod_New)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyStatus_Error, PyStaticMethod_New)
PyStatus (*__target__PyStatus_Error)(const char*) = NULL;
PyAPI_FUNC(PyStatus) PyStatus_Error(const char* a) {
    LOG("'%s'(0x%lx)", a?a:"<null>", (unsigned long) a);
    if (__target__PyStatus_Error == NULL) {
        __target__PyStatus_Error = resolveAPI("PyStatus_Error");
    }
    STATS_BEFORE(PyStatus_Error)
    PyStatus result = (PyStatus) __target__PyStatus_Error(a);
    STATS_AFTER(PyStatus_Error)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyStatus_Exception, PyStatus_Error)
int (*__target__PyStatus_Exception)(PyStatus) = NULL;
PyAPI_FUNC(int) PyStatus_Exception(PyStatus a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyStatus_Exception == NULL) {
        __target__PyStatus_Exception = resolveAPI("PyStatus_Exception");
    }
    STATS_BEFORE(PyStatus_Exception)
    int result = (int) __target__PyStatus_Exception(a);
    STATS_AFTER(PyStatus_Exception)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyStatus_Exit, PyStatus_Exception)
PyStatus (*__target__PyStatus_Exit)(int) = NULL;
PyAPI_FUNC(PyStatus) PyStatus_Exit(int a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyStatus_Exit == NULL) {
        __target__PyStatus_Exit = resolveAPI("PyStatus_Exit");
    }
    STATS_BEFORE(PyStatus_Exit)
    PyStatus result = (PyStatus) __target__PyStatus_Exit(a);
    STATS_AFTER(PyStatus_Exit)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyStatus_IsError, PyStatus_Exit)
int (*__target__PyStatus_IsError)(PyStatus) = NULL;
PyAPI_FUNC(int) PyStatus_IsError(PyStatus a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyStatus_IsError == NULL) {
        __target__PyStatus_IsError = resolveAPI("PyStatus_IsError");
    }
    STATS_BEFORE(PyStatus_IsError)
    int result = (int) __target__PyStatus_IsError(a);
    STATS_AFTER(PyStatus_IsError)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyStatus_IsExit, PyStatus_IsError)
int (*__target__PyStatus_IsExit)(PyStatus) = NULL;
PyAPI_FUNC(int) PyStatus_IsExit(PyStatus a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyStatus_IsExit == NULL) {
        __target__PyStatus_IsExit = resolveAPI("PyStatus_IsExit");
    }
    STATS_BEFORE(PyStatus_IsExit)
    int result = (int) __target__PyStatus_IsExit(a);
    STATS_AFTER(PyStatus_IsExit)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyStatus_NoMemory, PyStatus_IsExit)
PyStatus (*__target__PyStatus_NoMemory)() = NULL;
PyAPI_FUNC(PyStatus) PyStatus_NoMemory() {
    LOGS("");
    if (__target__PyStatus_NoMemory == NULL) {
        __target__PyStatus_NoMemory = resolveAPI("PyStatus_NoMemory");
    }
    STATS_BEFORE(PyStatus_NoMemory)
    PyStatus result = (PyStatus) __target__PyStatus_NoMemory();
    STATS_AFTER(PyStatus_NoMemory)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyStatus_Ok, PyStatus_NoMemory)
PyStatus (*__target__PyStatus_Ok)() = NULL;
PyAPI_FUNC(PyStatus) PyStatus_Ok() {
    LOGS("");
    if (__target__PyStatus_Ok == NULL) {
        __target__PyStatus_Ok = resolveAPI("PyStatus_Ok");
    }
    STATS_BEFORE(PyStatus_Ok)
    PyStatus result = (PyStatus) __target__PyStatus_Ok();
    STATS_AFTER(PyStatus_Ok)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyStructSequence_GetItem, PyStatus_Ok)
PyObject* (*__target__PyStructSequence_GetItem)(PyObject*, Py_ssize_t) = NULL;
PyAPI_FUNC(PyObject*) PyStructSequence_GetItem(PyObject* a, Py_ssize_t b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target__PyStructSequence_GetItem == NULL) {
        __target__PyStructSequence_GetItem = resolveAPI("PyStructSequence_GetItem");
    }
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
    if (__target__PyStructSequence_InitType == NULL) {
        __target__PyStructSequence_InitType = resolveAPI("PyStructSequence_InitType");
    }
    STATS_BEFORE(PyStructSequence_InitType)
    __target__PyStructSequence_InitType(a, b);
    STATS_AFTER(PyStructSequence_InitType)
    LOG_AFTER_VOID
}
STATS_CONTAINER(PyStructSequence_InitType2, PyStructSequence_InitType)
int (*__target__PyStructSequence_InitType2)(PyTypeObject*, PyStructSequence_Desc*) = NULL;
PyAPI_FUNC(int) PyStructSequence_InitType2(PyTypeObject* a, PyStructSequence_Desc* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target__PyStructSequence_InitType2 == NULL) {
        __target__PyStructSequence_InitType2 = resolveAPI("PyStructSequence_InitType2");
    }
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
    if (__target__PyStructSequence_New == NULL) {
        __target__PyStructSequence_New = resolveAPI("PyStructSequence_New");
    }
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
    if (__target__PyStructSequence_NewType == NULL) {
        __target__PyStructSequence_NewType = resolveAPI("PyStructSequence_NewType");
    }
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
    if (__target__PyStructSequence_SetItem == NULL) {
        __target__PyStructSequence_SetItem = resolveAPI("PyStructSequence_SetItem");
    }
    STATS_BEFORE(PyStructSequence_SetItem)
    __target__PyStructSequence_SetItem(a, b, c);
    STATS_AFTER(PyStructSequence_SetItem)
    LOG_AFTER_VOID
}
STATS_CONTAINER(PySys_AddAuditHook, PyStructSequence_SetItem)
int (*__target__PySys_AddAuditHook)(Py_AuditHookFunction, void*) = NULL;
PyAPI_FUNC(int) PySys_AddAuditHook(Py_AuditHookFunction a, void* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target__PySys_AddAuditHook == NULL) {
        __target__PySys_AddAuditHook = resolveAPI("PySys_AddAuditHook");
    }
    STATS_BEFORE(PySys_AddAuditHook)
    int result = (int) __target__PySys_AddAuditHook(a, b);
    STATS_AFTER(PySys_AddAuditHook)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PySys_AddWarnOption, PySys_AddAuditHook)
void (*__target__PySys_AddWarnOption)(const wchar_t*) = NULL;
PyAPI_FUNC(void) PySys_AddWarnOption(const wchar_t* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PySys_AddWarnOption == NULL) {
        __target__PySys_AddWarnOption = resolveAPI("PySys_AddWarnOption");
    }
    STATS_BEFORE(PySys_AddWarnOption)
    __target__PySys_AddWarnOption(a);
    STATS_AFTER(PySys_AddWarnOption)
    LOG_AFTER_VOID
}
STATS_CONTAINER(PySys_AddWarnOptionUnicode, PySys_AddWarnOption)
void (*__target__PySys_AddWarnOptionUnicode)(PyObject*) = NULL;
PyAPI_FUNC(void) PySys_AddWarnOptionUnicode(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PySys_AddWarnOptionUnicode == NULL) {
        __target__PySys_AddWarnOptionUnicode = resolveAPI("PySys_AddWarnOptionUnicode");
    }
    STATS_BEFORE(PySys_AddWarnOptionUnicode)
    __target__PySys_AddWarnOptionUnicode(a);
    STATS_AFTER(PySys_AddWarnOptionUnicode)
    LOG_AFTER_VOID
}
STATS_CONTAINER(PySys_AddXOption, PySys_AddWarnOptionUnicode)
void (*__target__PySys_AddXOption)(const wchar_t*) = NULL;
PyAPI_FUNC(void) PySys_AddXOption(const wchar_t* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PySys_AddXOption == NULL) {
        __target__PySys_AddXOption = resolveAPI("PySys_AddXOption");
    }
    STATS_BEFORE(PySys_AddXOption)
    __target__PySys_AddXOption(a);
    STATS_AFTER(PySys_AddXOption)
    LOG_AFTER_VOID
}
STATS_CONTAINER(PySys_GetObject, PySys_AddXOption)
PyObject* (*__target__PySys_GetObject)(const char*) = NULL;
PyAPI_FUNC(PyObject*) PySys_GetObject(const char* a) {
    LOG("'%s'(0x%lx)", a?a:"<null>", (unsigned long) a);
    if (__target__PySys_GetObject == NULL) {
        __target__PySys_GetObject = resolveAPI("PySys_GetObject");
    }
    STATS_BEFORE(PySys_GetObject)
    PyObject* result = (PyObject*) __target__PySys_GetObject(a);
    STATS_AFTER(PySys_GetObject)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PySys_GetXOptions, PySys_GetObject)
PyObject* (*__target__PySys_GetXOptions)() = NULL;
PyAPI_FUNC(PyObject*) PySys_GetXOptions() {
    LOGS("");
    if (__target__PySys_GetXOptions == NULL) {
        __target__PySys_GetXOptions = resolveAPI("PySys_GetXOptions");
    }
    STATS_BEFORE(PySys_GetXOptions)
    PyObject* result = (PyObject*) __target__PySys_GetXOptions();
    STATS_AFTER(PySys_GetXOptions)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PySys_HasWarnOptions, PySys_GetXOptions)
int (*__target__PySys_HasWarnOptions)() = NULL;
PyAPI_FUNC(int) PySys_HasWarnOptions() {
    LOGS("");
    if (__target__PySys_HasWarnOptions == NULL) {
        __target__PySys_HasWarnOptions = resolveAPI("PySys_HasWarnOptions");
    }
    STATS_BEFORE(PySys_HasWarnOptions)
    int result = (int) __target__PySys_HasWarnOptions();
    STATS_AFTER(PySys_HasWarnOptions)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PySys_ResetWarnOptions, PySys_HasWarnOptions)
void (*__target__PySys_ResetWarnOptions)() = NULL;
PyAPI_FUNC(void) PySys_ResetWarnOptions() {
    LOGS("");
    if (__target__PySys_ResetWarnOptions == NULL) {
        __target__PySys_ResetWarnOptions = resolveAPI("PySys_ResetWarnOptions");
    }
    STATS_BEFORE(PySys_ResetWarnOptions)
    __target__PySys_ResetWarnOptions();
    STATS_AFTER(PySys_ResetWarnOptions)
    LOG_AFTER_VOID
}
STATS_CONTAINER(PySys_SetArgv, PySys_ResetWarnOptions)
void (*__target__PySys_SetArgv)(int, wchar_t**) = NULL;
PyAPI_FUNC(void) PySys_SetArgv(int a, wchar_t** b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target__PySys_SetArgv == NULL) {
        __target__PySys_SetArgv = resolveAPI("PySys_SetArgv");
    }
    STATS_BEFORE(PySys_SetArgv)
    __target__PySys_SetArgv(a, b);
    STATS_AFTER(PySys_SetArgv)
    LOG_AFTER_VOID
}
STATS_CONTAINER(PySys_SetArgvEx, PySys_SetArgv)
void (*__target__PySys_SetArgvEx)(int, wchar_t**, int) = NULL;
PyAPI_FUNC(void) PySys_SetArgvEx(int a, wchar_t** b, int c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    if (__target__PySys_SetArgvEx == NULL) {
        __target__PySys_SetArgvEx = resolveAPI("PySys_SetArgvEx");
    }
    STATS_BEFORE(PySys_SetArgvEx)
    __target__PySys_SetArgvEx(a, b, c);
    STATS_AFTER(PySys_SetArgvEx)
    LOG_AFTER_VOID
}
STATS_CONTAINER(PySys_SetObject, PySys_SetArgvEx)
int (*__target__PySys_SetObject)(const char*, PyObject*) = NULL;
PyAPI_FUNC(int) PySys_SetObject(const char* a, PyObject* b) {
    LOG("'%s'(0x%lx) 0x%lx", a?a:"<null>", (unsigned long) a, (unsigned long) b);
    if (__target__PySys_SetObject == NULL) {
        __target__PySys_SetObject = resolveAPI("PySys_SetObject");
    }
    STATS_BEFORE(PySys_SetObject)
    int result = (int) __target__PySys_SetObject(a, b);
    STATS_AFTER(PySys_SetObject)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PySys_SetPath, PySys_SetObject)
void (*__target__PySys_SetPath)(const wchar_t*) = NULL;
PyAPI_FUNC(void) PySys_SetPath(const wchar_t* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PySys_SetPath == NULL) {
        __target__PySys_SetPath = resolveAPI("PySys_SetPath");
    }
    STATS_BEFORE(PySys_SetPath)
    __target__PySys_SetPath(a);
    STATS_AFTER(PySys_SetPath)
    LOG_AFTER_VOID
}
STATS_CONTAINER(PyThreadState_Clear, PySys_SetPath)
void (*__target__PyThreadState_Clear)(PyThreadState*) = NULL;
PyAPI_FUNC(void) PyThreadState_Clear(PyThreadState* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyThreadState_Clear == NULL) {
        __target__PyThreadState_Clear = resolveAPI("PyThreadState_Clear");
    }
    STATS_BEFORE(PyThreadState_Clear)
    __target__PyThreadState_Clear(a);
    STATS_AFTER(PyThreadState_Clear)
    LOG_AFTER_VOID
}
STATS_CONTAINER(PyThreadState_Delete, PyThreadState_Clear)
void (*__target__PyThreadState_Delete)(PyThreadState*) = NULL;
PyAPI_FUNC(void) PyThreadState_Delete(PyThreadState* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyThreadState_Delete == NULL) {
        __target__PyThreadState_Delete = resolveAPI("PyThreadState_Delete");
    }
    STATS_BEFORE(PyThreadState_Delete)
    __target__PyThreadState_Delete(a);
    STATS_AFTER(PyThreadState_Delete)
    LOG_AFTER_VOID
}
STATS_CONTAINER(PyThreadState_DeleteCurrent, PyThreadState_Delete)
void (*__target__PyThreadState_DeleteCurrent)() = NULL;
PyAPI_FUNC(void) PyThreadState_DeleteCurrent() {
    LOGS("");
    if (__target__PyThreadState_DeleteCurrent == NULL) {
        __target__PyThreadState_DeleteCurrent = resolveAPI("PyThreadState_DeleteCurrent");
    }
    STATS_BEFORE(PyThreadState_DeleteCurrent)
    __target__PyThreadState_DeleteCurrent();
    STATS_AFTER(PyThreadState_DeleteCurrent)
    LOG_AFTER_VOID
}
STATS_CONTAINER(PyThreadState_Get, PyThreadState_DeleteCurrent)
PyThreadState* (*__target__PyThreadState_Get)() = NULL;
MUST_INLINE PyAPI_FUNC(PyThreadState*) PyThreadState_Get_Inlined() {
    LOGS("");
    if (__target__PyThreadState_Get == NULL) {
        __target__PyThreadState_Get = resolveAPI("PyThreadState_Get");
    }
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
    if (__target__PyThreadState_GetDict == NULL) {
        __target__PyThreadState_GetDict = resolveAPI("PyThreadState_GetDict");
    }
    STATS_BEFORE(PyThreadState_GetDict)
    PyObject* result = (PyObject*) __target__PyThreadState_GetDict();
    STATS_AFTER(PyThreadState_GetDict)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyThreadState_GetFrame, PyThreadState_GetDict)
PyFrameObject* (*__target__PyThreadState_GetFrame)(PyThreadState*) = NULL;
PyAPI_FUNC(PyFrameObject*) PyThreadState_GetFrame(PyThreadState* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyThreadState_GetFrame == NULL) {
        __target__PyThreadState_GetFrame = resolveAPI("PyThreadState_GetFrame");
    }
    STATS_BEFORE(PyThreadState_GetFrame)
    PyFrameObject* result = (PyFrameObject*) __target__PyThreadState_GetFrame(a);
    STATS_AFTER(PyThreadState_GetFrame)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyThreadState_GetID, PyThreadState_GetFrame)
uint64_t (*__target__PyThreadState_GetID)(PyThreadState*) = NULL;
PyAPI_FUNC(uint64_t) PyThreadState_GetID(PyThreadState* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyThreadState_GetID == NULL) {
        __target__PyThreadState_GetID = resolveAPI("PyThreadState_GetID");
    }
    STATS_BEFORE(PyThreadState_GetID)
    uint64_t result = (uint64_t) __target__PyThreadState_GetID(a);
    STATS_AFTER(PyThreadState_GetID)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyThreadState_GetInterpreter, PyThreadState_GetID)
PyInterpreterState* (*__target__PyThreadState_GetInterpreter)(PyThreadState*) = NULL;
PyAPI_FUNC(PyInterpreterState*) PyThreadState_GetInterpreter(PyThreadState* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyThreadState_GetInterpreter == NULL) {
        __target__PyThreadState_GetInterpreter = resolveAPI("PyThreadState_GetInterpreter");
    }
    STATS_BEFORE(PyThreadState_GetInterpreter)
    PyInterpreterState* result = (PyInterpreterState*) __target__PyThreadState_GetInterpreter(a);
    STATS_AFTER(PyThreadState_GetInterpreter)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyThreadState_New, PyThreadState_GetInterpreter)
PyThreadState* (*__target__PyThreadState_New)(PyInterpreterState*) = NULL;
PyAPI_FUNC(PyThreadState*) PyThreadState_New(PyInterpreterState* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyThreadState_New == NULL) {
        __target__PyThreadState_New = resolveAPI("PyThreadState_New");
    }
    STATS_BEFORE(PyThreadState_New)
    PyThreadState* result = (PyThreadState*) __target__PyThreadState_New(a);
    STATS_AFTER(PyThreadState_New)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyThreadState_Next, PyThreadState_New)
PyThreadState* (*__target__PyThreadState_Next)(PyThreadState*) = NULL;
PyAPI_FUNC(PyThreadState*) PyThreadState_Next(PyThreadState* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyThreadState_Next == NULL) {
        __target__PyThreadState_Next = resolveAPI("PyThreadState_Next");
    }
    STATS_BEFORE(PyThreadState_Next)
    PyThreadState* result = (PyThreadState*) __target__PyThreadState_Next(a);
    STATS_AFTER(PyThreadState_Next)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyThreadState_SetAsyncExc, PyThreadState_Next)
int (*__target__PyThreadState_SetAsyncExc)(unsigned long, PyObject*) = NULL;
PyAPI_FUNC(int) PyThreadState_SetAsyncExc(unsigned long a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target__PyThreadState_SetAsyncExc == NULL) {
        __target__PyThreadState_SetAsyncExc = resolveAPI("PyThreadState_SetAsyncExc");
    }
    STATS_BEFORE(PyThreadState_SetAsyncExc)
    int result = (int) __target__PyThreadState_SetAsyncExc(a, b);
    STATS_AFTER(PyThreadState_SetAsyncExc)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyThreadState_Swap, PyThreadState_SetAsyncExc)
PyThreadState* (*__target__PyThreadState_Swap)(PyThreadState*) = NULL;
PyAPI_FUNC(PyThreadState*) PyThreadState_Swap(PyThreadState* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyThreadState_Swap == NULL) {
        __target__PyThreadState_Swap = resolveAPI("PyThreadState_Swap");
    }
    STATS_BEFORE(PyThreadState_Swap)
    PyThreadState* result = (PyThreadState*) __target__PyThreadState_Swap(a);
    STATS_AFTER(PyThreadState_Swap)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyThread_GetInfo, PyThreadState_Swap)
PyObject* (*__target__PyThread_GetInfo)() = NULL;
PyAPI_FUNC(PyObject*) PyThread_GetInfo() {
    LOGS("");
    if (__target__PyThread_GetInfo == NULL) {
        __target__PyThread_GetInfo = resolveAPI("PyThread_GetInfo");
    }
    STATS_BEFORE(PyThread_GetInfo)
    PyObject* result = (PyObject*) __target__PyThread_GetInfo();
    STATS_AFTER(PyThread_GetInfo)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyThread_ReInitTLS, PyThread_GetInfo)
void (*__target__PyThread_ReInitTLS)() = NULL;
PyAPI_FUNC(void) PyThread_ReInitTLS() {
    LOGS("");
    if (__target__PyThread_ReInitTLS == NULL) {
        __target__PyThread_ReInitTLS = resolveAPI("PyThread_ReInitTLS");
    }
    STATS_BEFORE(PyThread_ReInitTLS)
    __target__PyThread_ReInitTLS();
    STATS_AFTER(PyThread_ReInitTLS)
    LOG_AFTER_VOID
}
STATS_CONTAINER(PyThread_acquire_lock, PyThread_ReInitTLS)
int (*__target__PyThread_acquire_lock)(PyThread_type_lock, int) = NULL;
PyAPI_FUNC(int) PyThread_acquire_lock(PyThread_type_lock a, int b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target__PyThread_acquire_lock == NULL) {
        __target__PyThread_acquire_lock = resolveAPI("PyThread_acquire_lock");
    }
    STATS_BEFORE(PyThread_acquire_lock)
    int result = (int) __target__PyThread_acquire_lock(a, b);
    STATS_AFTER(PyThread_acquire_lock)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyThread_acquire_lock_timed, PyThread_acquire_lock)
PyLockStatus (*__target__PyThread_acquire_lock_timed)(PyThread_type_lock, long long, int) = NULL;
PyAPI_FUNC(PyLockStatus) PyThread_acquire_lock_timed(PyThread_type_lock a, long long b, int c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    if (__target__PyThread_acquire_lock_timed == NULL) {
        __target__PyThread_acquire_lock_timed = resolveAPI("PyThread_acquire_lock_timed");
    }
    STATS_BEFORE(PyThread_acquire_lock_timed)
    PyLockStatus result = (PyLockStatus) __target__PyThread_acquire_lock_timed(a, b, c);
    STATS_AFTER(PyThread_acquire_lock_timed)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyThread_allocate_lock, PyThread_acquire_lock_timed)
PyThread_type_lock (*__target__PyThread_allocate_lock)() = NULL;
PyAPI_FUNC(PyThread_type_lock) PyThread_allocate_lock() {
    LOGS("");
    if (__target__PyThread_allocate_lock == NULL) {
        __target__PyThread_allocate_lock = resolveAPI("PyThread_allocate_lock");
    }
    STATS_BEFORE(PyThread_allocate_lock)
    PyThread_type_lock result = (PyThread_type_lock) __target__PyThread_allocate_lock();
    STATS_AFTER(PyThread_allocate_lock)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyThread_create_key, PyThread_allocate_lock)
int (*__target__PyThread_create_key)() = NULL;
PyAPI_FUNC(int) PyThread_create_key() {
    LOGS("");
    if (__target__PyThread_create_key == NULL) {
        __target__PyThread_create_key = resolveAPI("PyThread_create_key");
    }
    STATS_BEFORE(PyThread_create_key)
    int result = (int) __target__PyThread_create_key();
    STATS_AFTER(PyThread_create_key)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyThread_delete_key, PyThread_create_key)
void (*__target__PyThread_delete_key)(int) = NULL;
PyAPI_FUNC(void) PyThread_delete_key(int a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyThread_delete_key == NULL) {
        __target__PyThread_delete_key = resolveAPI("PyThread_delete_key");
    }
    STATS_BEFORE(PyThread_delete_key)
    __target__PyThread_delete_key(a);
    STATS_AFTER(PyThread_delete_key)
    LOG_AFTER_VOID
}
STATS_CONTAINER(PyThread_delete_key_value, PyThread_delete_key)
void (*__target__PyThread_delete_key_value)(int) = NULL;
PyAPI_FUNC(void) PyThread_delete_key_value(int a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyThread_delete_key_value == NULL) {
        __target__PyThread_delete_key_value = resolveAPI("PyThread_delete_key_value");
    }
    STATS_BEFORE(PyThread_delete_key_value)
    __target__PyThread_delete_key_value(a);
    STATS_AFTER(PyThread_delete_key_value)
    LOG_AFTER_VOID
}
STATS_CONTAINER(PyThread_exit_thread, PyThread_delete_key_value)
void (*__target__PyThread_exit_thread)() = NULL;
PyAPI_FUNC(void) PyThread_exit_thread() {
    LOGS("");
    if (__target__PyThread_exit_thread == NULL) {
        __target__PyThread_exit_thread = resolveAPI("PyThread_exit_thread");
    }
    STATS_BEFORE(PyThread_exit_thread)
    __target__PyThread_exit_thread();
    STATS_AFTER(PyThread_exit_thread)
    LOG_AFTER_VOID
    abort();
}
STATS_CONTAINER(PyThread_free_lock, PyThread_exit_thread)
void (*__target__PyThread_free_lock)(PyThread_type_lock) = NULL;
PyAPI_FUNC(void) PyThread_free_lock(PyThread_type_lock a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyThread_free_lock == NULL) {
        __target__PyThread_free_lock = resolveAPI("PyThread_free_lock");
    }
    STATS_BEFORE(PyThread_free_lock)
    __target__PyThread_free_lock(a);
    STATS_AFTER(PyThread_free_lock)
    LOG_AFTER_VOID
}
STATS_CONTAINER(PyThread_get_key_value, PyThread_free_lock)
void* (*__target__PyThread_get_key_value)(int) = NULL;
PyAPI_FUNC(void*) PyThread_get_key_value(int a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyThread_get_key_value == NULL) {
        __target__PyThread_get_key_value = resolveAPI("PyThread_get_key_value");
    }
    STATS_BEFORE(PyThread_get_key_value)
    void* result = (void*) __target__PyThread_get_key_value(a);
    STATS_AFTER(PyThread_get_key_value)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyThread_get_stacksize, PyThread_get_key_value)
size_t (*__target__PyThread_get_stacksize)() = NULL;
PyAPI_FUNC(size_t) PyThread_get_stacksize() {
    LOGS("");
    if (__target__PyThread_get_stacksize == NULL) {
        __target__PyThread_get_stacksize = resolveAPI("PyThread_get_stacksize");
    }
    STATS_BEFORE(PyThread_get_stacksize)
    size_t result = (size_t) __target__PyThread_get_stacksize();
    STATS_AFTER(PyThread_get_stacksize)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyThread_get_thread_ident, PyThread_get_stacksize)
unsigned long (*__target__PyThread_get_thread_ident)() = NULL;
PyAPI_FUNC(unsigned long) PyThread_get_thread_ident() {
    LOGS("");
    if (__target__PyThread_get_thread_ident == NULL) {
        __target__PyThread_get_thread_ident = resolveAPI("PyThread_get_thread_ident");
    }
    STATS_BEFORE(PyThread_get_thread_ident)
    unsigned long result = (unsigned long) __target__PyThread_get_thread_ident();
    STATS_AFTER(PyThread_get_thread_ident)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyThread_get_thread_native_id, PyThread_get_thread_ident)
unsigned long (*__target__PyThread_get_thread_native_id)() = NULL;
PyAPI_FUNC(unsigned long) PyThread_get_thread_native_id() {
    LOGS("");
    if (__target__PyThread_get_thread_native_id == NULL) {
        __target__PyThread_get_thread_native_id = resolveAPI("PyThread_get_thread_native_id");
    }
    STATS_BEFORE(PyThread_get_thread_native_id)
    unsigned long result = (unsigned long) __target__PyThread_get_thread_native_id();
    STATS_AFTER(PyThread_get_thread_native_id)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyThread_init_thread, PyThread_get_thread_native_id)
void (*__target__PyThread_init_thread)() = NULL;
PyAPI_FUNC(void) PyThread_init_thread() {
    LOGS("");
    if (__target__PyThread_init_thread == NULL) {
        __target__PyThread_init_thread = resolveAPI("PyThread_init_thread");
    }
    STATS_BEFORE(PyThread_init_thread)
    __target__PyThread_init_thread();
    STATS_AFTER(PyThread_init_thread)
    LOG_AFTER_VOID
}
STATS_CONTAINER(PyThread_release_lock, PyThread_init_thread)
void (*__target__PyThread_release_lock)(PyThread_type_lock) = NULL;
PyAPI_FUNC(void) PyThread_release_lock(PyThread_type_lock a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyThread_release_lock == NULL) {
        __target__PyThread_release_lock = resolveAPI("PyThread_release_lock");
    }
    STATS_BEFORE(PyThread_release_lock)
    __target__PyThread_release_lock(a);
    STATS_AFTER(PyThread_release_lock)
    LOG_AFTER_VOID
}
STATS_CONTAINER(PyThread_set_key_value, PyThread_release_lock)
int (*__target__PyThread_set_key_value)(int, void*) = NULL;
PyAPI_FUNC(int) PyThread_set_key_value(int a, void* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target__PyThread_set_key_value == NULL) {
        __target__PyThread_set_key_value = resolveAPI("PyThread_set_key_value");
    }
    STATS_BEFORE(PyThread_set_key_value)
    int result = (int) __target__PyThread_set_key_value(a, b);
    STATS_AFTER(PyThread_set_key_value)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyThread_set_stacksize, PyThread_set_key_value)
int (*__target__PyThread_set_stacksize)(size_t) = NULL;
PyAPI_FUNC(int) PyThread_set_stacksize(size_t a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyThread_set_stacksize == NULL) {
        __target__PyThread_set_stacksize = resolveAPI("PyThread_set_stacksize");
    }
    STATS_BEFORE(PyThread_set_stacksize)
    int result = (int) __target__PyThread_set_stacksize(a);
    STATS_AFTER(PyThread_set_stacksize)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyThread_start_new_thread, PyThread_set_stacksize)
unsigned long (*__target__PyThread_start_new_thread)(void (*)(void*), void*) = NULL;
PyAPI_FUNC(unsigned long) PyThread_start_new_thread(void (*a)(void*), void* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target__PyThread_start_new_thread == NULL) {
        __target__PyThread_start_new_thread = resolveAPI("PyThread_start_new_thread");
    }
    STATS_BEFORE(PyThread_start_new_thread)
    unsigned long result = (unsigned long) __target__PyThread_start_new_thread(a, b);
    STATS_AFTER(PyThread_start_new_thread)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyThread_tss_alloc, PyThread_start_new_thread)
Py_tss_t* (*__target__PyThread_tss_alloc)() = NULL;
PyAPI_FUNC(Py_tss_t*) PyThread_tss_alloc() {
    LOGS("");
    if (__target__PyThread_tss_alloc == NULL) {
        __target__PyThread_tss_alloc = resolveAPI("PyThread_tss_alloc");
    }
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
    if (__target__PyThread_tss_create == NULL) {
        __target__PyThread_tss_create = resolveAPI("PyThread_tss_create");
    }
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
    if (__target__PyThread_tss_delete == NULL) {
        __target__PyThread_tss_delete = resolveAPI("PyThread_tss_delete");
    }
    STATS_BEFORE(PyThread_tss_delete)
    __target__PyThread_tss_delete(a);
    STATS_AFTER(PyThread_tss_delete)
    LOG_AFTER_VOID
}
STATS_CONTAINER(PyThread_tss_free, PyThread_tss_delete)
void (*__target__PyThread_tss_free)(Py_tss_t*) = NULL;
PyAPI_FUNC(void) PyThread_tss_free(Py_tss_t* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyThread_tss_free == NULL) {
        __target__PyThread_tss_free = resolveAPI("PyThread_tss_free");
    }
    STATS_BEFORE(PyThread_tss_free)
    __target__PyThread_tss_free(a);
    STATS_AFTER(PyThread_tss_free)
    LOG_AFTER_VOID
}
STATS_CONTAINER(PyThread_tss_get, PyThread_tss_free)
void* (*__target__PyThread_tss_get)(Py_tss_t*) = NULL;
PyAPI_FUNC(void*) PyThread_tss_get(Py_tss_t* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyThread_tss_get == NULL) {
        __target__PyThread_tss_get = resolveAPI("PyThread_tss_get");
    }
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
    if (__target__PyThread_tss_is_created == NULL) {
        __target__PyThread_tss_is_created = resolveAPI("PyThread_tss_is_created");
    }
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
    if (__target__PyThread_tss_set == NULL) {
        __target__PyThread_tss_set = resolveAPI("PyThread_tss_set");
    }
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
    if (__target__PyTraceBack_Here == NULL) {
        __target__PyTraceBack_Here = resolveAPI("PyTraceBack_Here");
    }
    STATS_BEFORE(PyTraceBack_Here)
    int result = (int) __target__PyTraceBack_Here(a);
    STATS_AFTER(PyTraceBack_Here)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyTraceBack_Print, PyTraceBack_Here)
int (*__target__PyTraceBack_Print)(PyObject*, PyObject*) = NULL;
PyAPI_FUNC(int) PyTraceBack_Print(PyObject* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target__PyTraceBack_Print == NULL) {
        __target__PyTraceBack_Print = resolveAPI("PyTraceBack_Print");
    }
    STATS_BEFORE(PyTraceBack_Print)
    int result = (int) __target__PyTraceBack_Print(a, b);
    STATS_AFTER(PyTraceBack_Print)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyTraceMalloc_Track, PyTraceBack_Print)
int (*__target__PyTraceMalloc_Track)(unsigned int, uintptr_t, size_t) = NULL;
PyAPI_FUNC(int) PyTraceMalloc_Track(unsigned int a, uintptr_t b, size_t c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    if (__target__PyTraceMalloc_Track == NULL) {
        __target__PyTraceMalloc_Track = resolveAPI("PyTraceMalloc_Track");
    }
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
    if (__target__PyTraceMalloc_Untrack == NULL) {
        __target__PyTraceMalloc_Untrack = resolveAPI("PyTraceMalloc_Untrack");
    }
    STATS_BEFORE(PyTraceMalloc_Untrack)
    int result = (int) __target__PyTraceMalloc_Untrack(a, b);
    STATS_AFTER(PyTraceMalloc_Untrack)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyTruffleFrame_New, PyTraceMalloc_Untrack)
PyFrameObject* (*__target__PyTruffleFrame_New)(PyThreadState*, PyCodeObject*, PyObject*, PyObject*) = NULL;
PyAPI_FUNC(PyFrameObject*) PyTruffleFrame_New(PyThreadState* a, PyCodeObject* b, PyObject* c, PyObject* d) {
    LOG("0x%lx 0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c, (unsigned long) d);
    if (__target__PyTruffleFrame_New == NULL) {
        __target__PyTruffleFrame_New = resolveAPI("PyTruffleFrame_New");
    }
    STATS_BEFORE(PyTruffleFrame_New)
    PyFrameObject* result = (PyFrameObject*) __target__PyTruffleFrame_New(a, b, c, d);
    STATS_AFTER(PyTruffleFrame_New)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyTruffle_Debug, PyTruffleFrame_New)
int (*__target__PyTruffle_Debug)(void*) = NULL;
PyAPI_FUNC(int) PyTruffle_Debug(void* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyTruffle_Debug == NULL) {
        __target__PyTruffle_Debug = resolveAPI("PyTruffle_Debug");
    }
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
    if (__target__PyTruffle_DebugTrace == NULL) {
        __target__PyTruffle_DebugTrace = resolveAPI("PyTruffle_DebugTrace");
    }
    STATS_BEFORE(PyTruffle_DebugTrace)
    __target__PyTruffle_DebugTrace();
    STATS_AFTER(PyTruffle_DebugTrace)
    LOG_AFTER_VOID
}
STATS_CONTAINER(PyTruffle_SeqIter_New, PyTruffle_DebugTrace)
PyObject* (*__target__PyTruffle_SeqIter_New)(PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyTruffle_SeqIter_New(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyTruffle_SeqIter_New == NULL) {
        __target__PyTruffle_SeqIter_New = resolveAPI("PyTruffle_SeqIter_New");
    }
    STATS_BEFORE(PyTruffle_SeqIter_New)
    PyObject* result = (PyObject*) __target__PyTruffle_SeqIter_New(a);
    STATS_AFTER(PyTruffle_SeqIter_New)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyTruffle_ToNative, PyTruffle_SeqIter_New)
int (*__target__PyTruffle_ToNative)(void*) = NULL;
PyAPI_FUNC(int) PyTruffle_ToNative(void* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyTruffle_ToNative == NULL) {
        __target__PyTruffle_ToNative = resolveAPI("PyTruffle_ToNative");
    }
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
    if (__target__PyTuple_GetItem == NULL) {
        __target__PyTuple_GetItem = resolveAPI("PyTuple_GetItem");
    }
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
    if (__target__PyTuple_GetSlice == NULL) {
        __target__PyTuple_GetSlice = resolveAPI("PyTuple_GetSlice");
    }
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
    if (__target__PyTuple_New == NULL) {
        __target__PyTuple_New = resolveAPI("PyTuple_New");
    }
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
    if (__target__PyTuple_SetItem == NULL) {
        __target__PyTuple_SetItem = resolveAPI("PyTuple_SetItem");
    }
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
    if (__target__PyTuple_Size == NULL) {
        __target__PyTuple_Size = resolveAPI("PyTuple_Size");
    }
    STATS_BEFORE(PyTuple_Size)
    Py_ssize_t result = (Py_ssize_t) __target__PyTuple_Size(a);
    STATS_AFTER(PyTuple_Size)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyType_ClearCache, PyTuple_Size)
unsigned int (*__target__PyType_ClearCache)() = NULL;
PyAPI_FUNC(unsigned int) PyType_ClearCache() {
    LOGS("");
    if (__target__PyType_ClearCache == NULL) {
        __target__PyType_ClearCache = resolveAPI("PyType_ClearCache");
    }
    STATS_BEFORE(PyType_ClearCache)
    unsigned int result = (unsigned int) __target__PyType_ClearCache();
    STATS_AFTER(PyType_ClearCache)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyType_FromModuleAndSpec, PyType_ClearCache)
PyObject* (*__target__PyType_FromModuleAndSpec)(PyObject*, PyType_Spec*, PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyType_FromModuleAndSpec(PyObject* a, PyType_Spec* b, PyObject* c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    if (__target__PyType_FromModuleAndSpec == NULL) {
        __target__PyType_FromModuleAndSpec = resolveAPI("PyType_FromModuleAndSpec");
    }
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
    if (__target__PyType_FromSpec == NULL) {
        __target__PyType_FromSpec = resolveAPI("PyType_FromSpec");
    }
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
    if (__target__PyType_FromSpecWithBases == NULL) {
        __target__PyType_FromSpecWithBases = resolveAPI("PyType_FromSpecWithBases");
    }
    STATS_BEFORE(PyType_FromSpecWithBases)
    PyObject* result = (PyObject*) __target__PyType_FromSpecWithBases(a, b);
    STATS_AFTER(PyType_FromSpecWithBases)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyType_FromSpecWithBasesAndMeta, PyType_FromSpecWithBases)
PyObject* (*__target__PyType_FromSpecWithBasesAndMeta)(PyType_Spec*, PyObject*, PyTypeObject*) = NULL;
PyAPI_FUNC(PyObject*) PyType_FromSpecWithBasesAndMeta(PyType_Spec* a, PyObject* b, PyTypeObject* c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    if (__target__PyType_FromSpecWithBasesAndMeta == NULL) {
        __target__PyType_FromSpecWithBasesAndMeta = resolveAPI("PyType_FromSpecWithBasesAndMeta");
    }
    STATS_BEFORE(PyType_FromSpecWithBasesAndMeta)
    PyObject* result = (PyObject*) __target__PyType_FromSpecWithBasesAndMeta(a, b, c);
    STATS_AFTER(PyType_FromSpecWithBasesAndMeta)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyType_GenericAlloc, PyType_FromSpecWithBasesAndMeta)
PyObject* (*__target__PyType_GenericAlloc)(PyTypeObject*, Py_ssize_t) = NULL;
PyAPI_FUNC(PyObject*) PyType_GenericAlloc(PyTypeObject* a, Py_ssize_t b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target__PyType_GenericAlloc == NULL) {
        __target__PyType_GenericAlloc = resolveAPI("PyType_GenericAlloc");
    }
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
    if (__target__PyType_GenericNew == NULL) {
        __target__PyType_GenericNew = resolveAPI("PyType_GenericNew");
    }
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
    if (__target__PyType_GetFlags == NULL) {
        __target__PyType_GetFlags = resolveAPI("PyType_GetFlags");
    }
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
    if (__target__PyType_GetModule == NULL) {
        __target__PyType_GetModule = resolveAPI("PyType_GetModule");
    }
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
    if (__target__PyType_GetModuleState == NULL) {
        __target__PyType_GetModuleState = resolveAPI("PyType_GetModuleState");
    }
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
    if (__target__PyType_GetSlot == NULL) {
        __target__PyType_GetSlot = resolveAPI("PyType_GetSlot");
    }
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
    if (__target__PyType_IsSubtype == NULL) {
        __target__PyType_IsSubtype = resolveAPI("PyType_IsSubtype");
    }
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
    if (__target__PyType_Modified == NULL) {
        __target__PyType_Modified = resolveAPI("PyType_Modified");
    }
    STATS_BEFORE(PyType_Modified)
    __target__PyType_Modified(a);
    STATS_AFTER(PyType_Modified)
    LOG_AFTER_VOID
}
STATS_CONTAINER(PyType_Ready, PyType_Modified)
int (*__target__PyType_Ready)(PyTypeObject*) = NULL;
PyAPI_FUNC(int) PyType_Ready(PyTypeObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyType_Ready == NULL) {
        __target__PyType_Ready = resolveAPI("PyType_Ready");
    }
    STATS_BEFORE(PyType_Ready)
    int result = (int) __target__PyType_Ready(a);
    STATS_AFTER(PyType_Ready)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyUnicodeDecodeError_Create, PyType_Ready)
PyObject* (*__target__PyUnicodeDecodeError_Create)(const char*, const char*, Py_ssize_t, Py_ssize_t, Py_ssize_t, const char*) = NULL;
PyAPI_FUNC(PyObject*) PyUnicodeDecodeError_Create(const char* a, const char* b, Py_ssize_t c, Py_ssize_t d, Py_ssize_t e, const char* f) {
    LOG("'%s'(0x%lx) '%s'(0x%lx) 0x%lx 0x%lx 0x%lx '%s'(0x%lx)", a?a:"<null>", (unsigned long) a, b?b:"<null>", (unsigned long) b, (unsigned long) c, (unsigned long) d, (unsigned long) e, f?f:"<null>", (unsigned long) f);
    if (__target__PyUnicodeDecodeError_Create == NULL) {
        __target__PyUnicodeDecodeError_Create = resolveAPI("PyUnicodeDecodeError_Create");
    }
    STATS_BEFORE(PyUnicodeDecodeError_Create)
    PyObject* result = (PyObject*) __target__PyUnicodeDecodeError_Create(a, b, c, d, e, f);
    STATS_AFTER(PyUnicodeDecodeError_Create)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyUnicodeDecodeError_GetEncoding, PyUnicodeDecodeError_Create)
PyObject* (*__target__PyUnicodeDecodeError_GetEncoding)(PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyUnicodeDecodeError_GetEncoding(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyUnicodeDecodeError_GetEncoding == NULL) {
        __target__PyUnicodeDecodeError_GetEncoding = resolveAPI("PyUnicodeDecodeError_GetEncoding");
    }
    STATS_BEFORE(PyUnicodeDecodeError_GetEncoding)
    PyObject* result = (PyObject*) __target__PyUnicodeDecodeError_GetEncoding(a);
    STATS_AFTER(PyUnicodeDecodeError_GetEncoding)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyUnicodeDecodeError_GetEnd, PyUnicodeDecodeError_GetEncoding)
int (*__target__PyUnicodeDecodeError_GetEnd)(PyObject*, Py_ssize_t*) = NULL;
PyAPI_FUNC(int) PyUnicodeDecodeError_GetEnd(PyObject* a, Py_ssize_t* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target__PyUnicodeDecodeError_GetEnd == NULL) {
        __target__PyUnicodeDecodeError_GetEnd = resolveAPI("PyUnicodeDecodeError_GetEnd");
    }
    STATS_BEFORE(PyUnicodeDecodeError_GetEnd)
    int result = (int) __target__PyUnicodeDecodeError_GetEnd(a, b);
    STATS_AFTER(PyUnicodeDecodeError_GetEnd)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyUnicodeDecodeError_GetObject, PyUnicodeDecodeError_GetEnd)
PyObject* (*__target__PyUnicodeDecodeError_GetObject)(PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyUnicodeDecodeError_GetObject(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyUnicodeDecodeError_GetObject == NULL) {
        __target__PyUnicodeDecodeError_GetObject = resolveAPI("PyUnicodeDecodeError_GetObject");
    }
    STATS_BEFORE(PyUnicodeDecodeError_GetObject)
    PyObject* result = (PyObject*) __target__PyUnicodeDecodeError_GetObject(a);
    STATS_AFTER(PyUnicodeDecodeError_GetObject)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyUnicodeDecodeError_GetReason, PyUnicodeDecodeError_GetObject)
PyObject* (*__target__PyUnicodeDecodeError_GetReason)(PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyUnicodeDecodeError_GetReason(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyUnicodeDecodeError_GetReason == NULL) {
        __target__PyUnicodeDecodeError_GetReason = resolveAPI("PyUnicodeDecodeError_GetReason");
    }
    STATS_BEFORE(PyUnicodeDecodeError_GetReason)
    PyObject* result = (PyObject*) __target__PyUnicodeDecodeError_GetReason(a);
    STATS_AFTER(PyUnicodeDecodeError_GetReason)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyUnicodeDecodeError_GetStart, PyUnicodeDecodeError_GetReason)
int (*__target__PyUnicodeDecodeError_GetStart)(PyObject*, Py_ssize_t*) = NULL;
PyAPI_FUNC(int) PyUnicodeDecodeError_GetStart(PyObject* a, Py_ssize_t* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target__PyUnicodeDecodeError_GetStart == NULL) {
        __target__PyUnicodeDecodeError_GetStart = resolveAPI("PyUnicodeDecodeError_GetStart");
    }
    STATS_BEFORE(PyUnicodeDecodeError_GetStart)
    int result = (int) __target__PyUnicodeDecodeError_GetStart(a, b);
    STATS_AFTER(PyUnicodeDecodeError_GetStart)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyUnicodeDecodeError_SetEnd, PyUnicodeDecodeError_GetStart)
int (*__target__PyUnicodeDecodeError_SetEnd)(PyObject*, Py_ssize_t) = NULL;
PyAPI_FUNC(int) PyUnicodeDecodeError_SetEnd(PyObject* a, Py_ssize_t b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target__PyUnicodeDecodeError_SetEnd == NULL) {
        __target__PyUnicodeDecodeError_SetEnd = resolveAPI("PyUnicodeDecodeError_SetEnd");
    }
    STATS_BEFORE(PyUnicodeDecodeError_SetEnd)
    int result = (int) __target__PyUnicodeDecodeError_SetEnd(a, b);
    STATS_AFTER(PyUnicodeDecodeError_SetEnd)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyUnicodeDecodeError_SetReason, PyUnicodeDecodeError_SetEnd)
int (*__target__PyUnicodeDecodeError_SetReason)(PyObject*, const char*) = NULL;
PyAPI_FUNC(int) PyUnicodeDecodeError_SetReason(PyObject* a, const char* b) {
    LOG("0x%lx '%s'(0x%lx)", (unsigned long) a, b?b:"<null>", (unsigned long) b);
    if (__target__PyUnicodeDecodeError_SetReason == NULL) {
        __target__PyUnicodeDecodeError_SetReason = resolveAPI("PyUnicodeDecodeError_SetReason");
    }
    STATS_BEFORE(PyUnicodeDecodeError_SetReason)
    int result = (int) __target__PyUnicodeDecodeError_SetReason(a, b);
    STATS_AFTER(PyUnicodeDecodeError_SetReason)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyUnicodeDecodeError_SetStart, PyUnicodeDecodeError_SetReason)
int (*__target__PyUnicodeDecodeError_SetStart)(PyObject*, Py_ssize_t) = NULL;
PyAPI_FUNC(int) PyUnicodeDecodeError_SetStart(PyObject* a, Py_ssize_t b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target__PyUnicodeDecodeError_SetStart == NULL) {
        __target__PyUnicodeDecodeError_SetStart = resolveAPI("PyUnicodeDecodeError_SetStart");
    }
    STATS_BEFORE(PyUnicodeDecodeError_SetStart)
    int result = (int) __target__PyUnicodeDecodeError_SetStart(a, b);
    STATS_AFTER(PyUnicodeDecodeError_SetStart)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyUnicodeEncodeError_Create, PyUnicodeDecodeError_SetStart)
PyObject* (*__target__PyUnicodeEncodeError_Create)(const char*, const Py_UNICODE*, Py_ssize_t, Py_ssize_t, Py_ssize_t, const char*) = NULL;
PyAPI_FUNC(PyObject*) PyUnicodeEncodeError_Create(const char* a, const Py_UNICODE* b, Py_ssize_t c, Py_ssize_t d, Py_ssize_t e, const char* f) {
    LOG("'%s'(0x%lx) 0x%lx 0x%lx 0x%lx 0x%lx '%s'(0x%lx)", a?a:"<null>", (unsigned long) a, (unsigned long) b, (unsigned long) c, (unsigned long) d, (unsigned long) e, f?f:"<null>", (unsigned long) f);
    if (__target__PyUnicodeEncodeError_Create == NULL) {
        __target__PyUnicodeEncodeError_Create = resolveAPI("PyUnicodeEncodeError_Create");
    }
    STATS_BEFORE(PyUnicodeEncodeError_Create)
    PyObject* result = (PyObject*) __target__PyUnicodeEncodeError_Create(a, b, c, d, e, f);
    STATS_AFTER(PyUnicodeEncodeError_Create)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyUnicodeEncodeError_GetEncoding, PyUnicodeEncodeError_Create)
PyObject* (*__target__PyUnicodeEncodeError_GetEncoding)(PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyUnicodeEncodeError_GetEncoding(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyUnicodeEncodeError_GetEncoding == NULL) {
        __target__PyUnicodeEncodeError_GetEncoding = resolveAPI("PyUnicodeEncodeError_GetEncoding");
    }
    STATS_BEFORE(PyUnicodeEncodeError_GetEncoding)
    PyObject* result = (PyObject*) __target__PyUnicodeEncodeError_GetEncoding(a);
    STATS_AFTER(PyUnicodeEncodeError_GetEncoding)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyUnicodeEncodeError_GetEnd, PyUnicodeEncodeError_GetEncoding)
int (*__target__PyUnicodeEncodeError_GetEnd)(PyObject*, Py_ssize_t*) = NULL;
PyAPI_FUNC(int) PyUnicodeEncodeError_GetEnd(PyObject* a, Py_ssize_t* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target__PyUnicodeEncodeError_GetEnd == NULL) {
        __target__PyUnicodeEncodeError_GetEnd = resolveAPI("PyUnicodeEncodeError_GetEnd");
    }
    STATS_BEFORE(PyUnicodeEncodeError_GetEnd)
    int result = (int) __target__PyUnicodeEncodeError_GetEnd(a, b);
    STATS_AFTER(PyUnicodeEncodeError_GetEnd)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyUnicodeEncodeError_GetObject, PyUnicodeEncodeError_GetEnd)
PyObject* (*__target__PyUnicodeEncodeError_GetObject)(PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyUnicodeEncodeError_GetObject(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyUnicodeEncodeError_GetObject == NULL) {
        __target__PyUnicodeEncodeError_GetObject = resolveAPI("PyUnicodeEncodeError_GetObject");
    }
    STATS_BEFORE(PyUnicodeEncodeError_GetObject)
    PyObject* result = (PyObject*) __target__PyUnicodeEncodeError_GetObject(a);
    STATS_AFTER(PyUnicodeEncodeError_GetObject)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyUnicodeEncodeError_GetReason, PyUnicodeEncodeError_GetObject)
PyObject* (*__target__PyUnicodeEncodeError_GetReason)(PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyUnicodeEncodeError_GetReason(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyUnicodeEncodeError_GetReason == NULL) {
        __target__PyUnicodeEncodeError_GetReason = resolveAPI("PyUnicodeEncodeError_GetReason");
    }
    STATS_BEFORE(PyUnicodeEncodeError_GetReason)
    PyObject* result = (PyObject*) __target__PyUnicodeEncodeError_GetReason(a);
    STATS_AFTER(PyUnicodeEncodeError_GetReason)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyUnicodeEncodeError_GetStart, PyUnicodeEncodeError_GetReason)
int (*__target__PyUnicodeEncodeError_GetStart)(PyObject*, Py_ssize_t*) = NULL;
PyAPI_FUNC(int) PyUnicodeEncodeError_GetStart(PyObject* a, Py_ssize_t* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target__PyUnicodeEncodeError_GetStart == NULL) {
        __target__PyUnicodeEncodeError_GetStart = resolveAPI("PyUnicodeEncodeError_GetStart");
    }
    STATS_BEFORE(PyUnicodeEncodeError_GetStart)
    int result = (int) __target__PyUnicodeEncodeError_GetStart(a, b);
    STATS_AFTER(PyUnicodeEncodeError_GetStart)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyUnicodeEncodeError_SetEnd, PyUnicodeEncodeError_GetStart)
int (*__target__PyUnicodeEncodeError_SetEnd)(PyObject*, Py_ssize_t) = NULL;
PyAPI_FUNC(int) PyUnicodeEncodeError_SetEnd(PyObject* a, Py_ssize_t b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target__PyUnicodeEncodeError_SetEnd == NULL) {
        __target__PyUnicodeEncodeError_SetEnd = resolveAPI("PyUnicodeEncodeError_SetEnd");
    }
    STATS_BEFORE(PyUnicodeEncodeError_SetEnd)
    int result = (int) __target__PyUnicodeEncodeError_SetEnd(a, b);
    STATS_AFTER(PyUnicodeEncodeError_SetEnd)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyUnicodeEncodeError_SetReason, PyUnicodeEncodeError_SetEnd)
int (*__target__PyUnicodeEncodeError_SetReason)(PyObject*, const char*) = NULL;
PyAPI_FUNC(int) PyUnicodeEncodeError_SetReason(PyObject* a, const char* b) {
    LOG("0x%lx '%s'(0x%lx)", (unsigned long) a, b?b:"<null>", (unsigned long) b);
    if (__target__PyUnicodeEncodeError_SetReason == NULL) {
        __target__PyUnicodeEncodeError_SetReason = resolveAPI("PyUnicodeEncodeError_SetReason");
    }
    STATS_BEFORE(PyUnicodeEncodeError_SetReason)
    int result = (int) __target__PyUnicodeEncodeError_SetReason(a, b);
    STATS_AFTER(PyUnicodeEncodeError_SetReason)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyUnicodeEncodeError_SetStart, PyUnicodeEncodeError_SetReason)
int (*__target__PyUnicodeEncodeError_SetStart)(PyObject*, Py_ssize_t) = NULL;
PyAPI_FUNC(int) PyUnicodeEncodeError_SetStart(PyObject* a, Py_ssize_t b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target__PyUnicodeEncodeError_SetStart == NULL) {
        __target__PyUnicodeEncodeError_SetStart = resolveAPI("PyUnicodeEncodeError_SetStart");
    }
    STATS_BEFORE(PyUnicodeEncodeError_SetStart)
    int result = (int) __target__PyUnicodeEncodeError_SetStart(a, b);
    STATS_AFTER(PyUnicodeEncodeError_SetStart)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyUnicodeTranslateError_Create, PyUnicodeEncodeError_SetStart)
PyObject* (*__target__PyUnicodeTranslateError_Create)(const Py_UNICODE*, Py_ssize_t, Py_ssize_t, Py_ssize_t, const char*) = NULL;
PyAPI_FUNC(PyObject*) PyUnicodeTranslateError_Create(const Py_UNICODE* a, Py_ssize_t b, Py_ssize_t c, Py_ssize_t d, const char* e) {
    LOG("0x%lx 0x%lx 0x%lx 0x%lx '%s'(0x%lx)", (unsigned long) a, (unsigned long) b, (unsigned long) c, (unsigned long) d, e?e:"<null>", (unsigned long) e);
    if (__target__PyUnicodeTranslateError_Create == NULL) {
        __target__PyUnicodeTranslateError_Create = resolveAPI("PyUnicodeTranslateError_Create");
    }
    STATS_BEFORE(PyUnicodeTranslateError_Create)
    PyObject* result = (PyObject*) __target__PyUnicodeTranslateError_Create(a, b, c, d, e);
    STATS_AFTER(PyUnicodeTranslateError_Create)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyUnicodeTranslateError_GetEnd, PyUnicodeTranslateError_Create)
int (*__target__PyUnicodeTranslateError_GetEnd)(PyObject*, Py_ssize_t*) = NULL;
PyAPI_FUNC(int) PyUnicodeTranslateError_GetEnd(PyObject* a, Py_ssize_t* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target__PyUnicodeTranslateError_GetEnd == NULL) {
        __target__PyUnicodeTranslateError_GetEnd = resolveAPI("PyUnicodeTranslateError_GetEnd");
    }
    STATS_BEFORE(PyUnicodeTranslateError_GetEnd)
    int result = (int) __target__PyUnicodeTranslateError_GetEnd(a, b);
    STATS_AFTER(PyUnicodeTranslateError_GetEnd)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyUnicodeTranslateError_GetObject, PyUnicodeTranslateError_GetEnd)
PyObject* (*__target__PyUnicodeTranslateError_GetObject)(PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyUnicodeTranslateError_GetObject(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyUnicodeTranslateError_GetObject == NULL) {
        __target__PyUnicodeTranslateError_GetObject = resolveAPI("PyUnicodeTranslateError_GetObject");
    }
    STATS_BEFORE(PyUnicodeTranslateError_GetObject)
    PyObject* result = (PyObject*) __target__PyUnicodeTranslateError_GetObject(a);
    STATS_AFTER(PyUnicodeTranslateError_GetObject)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyUnicodeTranslateError_GetReason, PyUnicodeTranslateError_GetObject)
PyObject* (*__target__PyUnicodeTranslateError_GetReason)(PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyUnicodeTranslateError_GetReason(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyUnicodeTranslateError_GetReason == NULL) {
        __target__PyUnicodeTranslateError_GetReason = resolveAPI("PyUnicodeTranslateError_GetReason");
    }
    STATS_BEFORE(PyUnicodeTranslateError_GetReason)
    PyObject* result = (PyObject*) __target__PyUnicodeTranslateError_GetReason(a);
    STATS_AFTER(PyUnicodeTranslateError_GetReason)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyUnicodeTranslateError_GetStart, PyUnicodeTranslateError_GetReason)
int (*__target__PyUnicodeTranslateError_GetStart)(PyObject*, Py_ssize_t*) = NULL;
PyAPI_FUNC(int) PyUnicodeTranslateError_GetStart(PyObject* a, Py_ssize_t* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target__PyUnicodeTranslateError_GetStart == NULL) {
        __target__PyUnicodeTranslateError_GetStart = resolveAPI("PyUnicodeTranslateError_GetStart");
    }
    STATS_BEFORE(PyUnicodeTranslateError_GetStart)
    int result = (int) __target__PyUnicodeTranslateError_GetStart(a, b);
    STATS_AFTER(PyUnicodeTranslateError_GetStart)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyUnicodeTranslateError_SetEnd, PyUnicodeTranslateError_GetStart)
int (*__target__PyUnicodeTranslateError_SetEnd)(PyObject*, Py_ssize_t) = NULL;
PyAPI_FUNC(int) PyUnicodeTranslateError_SetEnd(PyObject* a, Py_ssize_t b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target__PyUnicodeTranslateError_SetEnd == NULL) {
        __target__PyUnicodeTranslateError_SetEnd = resolveAPI("PyUnicodeTranslateError_SetEnd");
    }
    STATS_BEFORE(PyUnicodeTranslateError_SetEnd)
    int result = (int) __target__PyUnicodeTranslateError_SetEnd(a, b);
    STATS_AFTER(PyUnicodeTranslateError_SetEnd)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyUnicodeTranslateError_SetReason, PyUnicodeTranslateError_SetEnd)
int (*__target__PyUnicodeTranslateError_SetReason)(PyObject*, const char*) = NULL;
PyAPI_FUNC(int) PyUnicodeTranslateError_SetReason(PyObject* a, const char* b) {
    LOG("0x%lx '%s'(0x%lx)", (unsigned long) a, b?b:"<null>", (unsigned long) b);
    if (__target__PyUnicodeTranslateError_SetReason == NULL) {
        __target__PyUnicodeTranslateError_SetReason = resolveAPI("PyUnicodeTranslateError_SetReason");
    }
    STATS_BEFORE(PyUnicodeTranslateError_SetReason)
    int result = (int) __target__PyUnicodeTranslateError_SetReason(a, b);
    STATS_AFTER(PyUnicodeTranslateError_SetReason)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyUnicodeTranslateError_SetStart, PyUnicodeTranslateError_SetReason)
int (*__target__PyUnicodeTranslateError_SetStart)(PyObject*, Py_ssize_t) = NULL;
PyAPI_FUNC(int) PyUnicodeTranslateError_SetStart(PyObject* a, Py_ssize_t b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target__PyUnicodeTranslateError_SetStart == NULL) {
        __target__PyUnicodeTranslateError_SetStart = resolveAPI("PyUnicodeTranslateError_SetStart");
    }
    STATS_BEFORE(PyUnicodeTranslateError_SetStart)
    int result = (int) __target__PyUnicodeTranslateError_SetStart(a, b);
    STATS_AFTER(PyUnicodeTranslateError_SetStart)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyUnicode_Append, PyUnicodeTranslateError_SetStart)
void (*__target__PyUnicode_Append)(PyObject**, PyObject*) = NULL;
PyAPI_FUNC(void) PyUnicode_Append(PyObject** a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target__PyUnicode_Append == NULL) {
        __target__PyUnicode_Append = resolveAPI("PyUnicode_Append");
    }
    STATS_BEFORE(PyUnicode_Append)
    __target__PyUnicode_Append(a, b);
    STATS_AFTER(PyUnicode_Append)
    LOG_AFTER_VOID
}
STATS_CONTAINER(PyUnicode_AppendAndDel, PyUnicode_Append)
void (*__target__PyUnicode_AppendAndDel)(PyObject**, PyObject*) = NULL;
PyAPI_FUNC(void) PyUnicode_AppendAndDel(PyObject** a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target__PyUnicode_AppendAndDel == NULL) {
        __target__PyUnicode_AppendAndDel = resolveAPI("PyUnicode_AppendAndDel");
    }
    STATS_BEFORE(PyUnicode_AppendAndDel)
    __target__PyUnicode_AppendAndDel(a, b);
    STATS_AFTER(PyUnicode_AppendAndDel)
    LOG_AFTER_VOID
}
STATS_CONTAINER(PyUnicode_AsASCIIString, PyUnicode_AppendAndDel)
PyObject* (*__target__PyUnicode_AsASCIIString)(PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyUnicode_AsASCIIString(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyUnicode_AsASCIIString == NULL) {
        __target__PyUnicode_AsASCIIString = resolveAPI("PyUnicode_AsASCIIString");
    }
    STATS_BEFORE(PyUnicode_AsASCIIString)
    PyObject* result = (PyObject*) __target__PyUnicode_AsASCIIString(a);
    STATS_AFTER(PyUnicode_AsASCIIString)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyUnicode_AsCharmapString, PyUnicode_AsASCIIString)
PyObject* (*__target__PyUnicode_AsCharmapString)(PyObject*, PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyUnicode_AsCharmapString(PyObject* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target__PyUnicode_AsCharmapString == NULL) {
        __target__PyUnicode_AsCharmapString = resolveAPI("PyUnicode_AsCharmapString");
    }
    STATS_BEFORE(PyUnicode_AsCharmapString)
    PyObject* result = (PyObject*) __target__PyUnicode_AsCharmapString(a, b);
    STATS_AFTER(PyUnicode_AsCharmapString)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyUnicode_AsDecodedObject, PyUnicode_AsCharmapString)
PyObject* (*__target__PyUnicode_AsDecodedObject)(PyObject*, const char*, const char*) = NULL;
PyAPI_FUNC(PyObject*) PyUnicode_AsDecodedObject(PyObject* a, const char* b, const char* c) {
    LOG("0x%lx '%s'(0x%lx) '%s'(0x%lx)", (unsigned long) a, b?b:"<null>", (unsigned long) b, c?c:"<null>", (unsigned long) c);
    if (__target__PyUnicode_AsDecodedObject == NULL) {
        __target__PyUnicode_AsDecodedObject = resolveAPI("PyUnicode_AsDecodedObject");
    }
    STATS_BEFORE(PyUnicode_AsDecodedObject)
    PyObject* result = (PyObject*) __target__PyUnicode_AsDecodedObject(a, b, c);
    STATS_AFTER(PyUnicode_AsDecodedObject)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyUnicode_AsDecodedUnicode, PyUnicode_AsDecodedObject)
PyObject* (*__target__PyUnicode_AsDecodedUnicode)(PyObject*, const char*, const char*) = NULL;
PyAPI_FUNC(PyObject*) PyUnicode_AsDecodedUnicode(PyObject* a, const char* b, const char* c) {
    LOG("0x%lx '%s'(0x%lx) '%s'(0x%lx)", (unsigned long) a, b?b:"<null>", (unsigned long) b, c?c:"<null>", (unsigned long) c);
    if (__target__PyUnicode_AsDecodedUnicode == NULL) {
        __target__PyUnicode_AsDecodedUnicode = resolveAPI("PyUnicode_AsDecodedUnicode");
    }
    STATS_BEFORE(PyUnicode_AsDecodedUnicode)
    PyObject* result = (PyObject*) __target__PyUnicode_AsDecodedUnicode(a, b, c);
    STATS_AFTER(PyUnicode_AsDecodedUnicode)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyUnicode_AsEncodedObject, PyUnicode_AsDecodedUnicode)
PyObject* (*__target__PyUnicode_AsEncodedObject)(PyObject*, const char*, const char*) = NULL;
PyAPI_FUNC(PyObject*) PyUnicode_AsEncodedObject(PyObject* a, const char* b, const char* c) {
    LOG("0x%lx '%s'(0x%lx) '%s'(0x%lx)", (unsigned long) a, b?b:"<null>", (unsigned long) b, c?c:"<null>", (unsigned long) c);
    if (__target__PyUnicode_AsEncodedObject == NULL) {
        __target__PyUnicode_AsEncodedObject = resolveAPI("PyUnicode_AsEncodedObject");
    }
    STATS_BEFORE(PyUnicode_AsEncodedObject)
    PyObject* result = (PyObject*) __target__PyUnicode_AsEncodedObject(a, b, c);
    STATS_AFTER(PyUnicode_AsEncodedObject)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyUnicode_AsEncodedString, PyUnicode_AsEncodedObject)
PyObject* (*__target__PyUnicode_AsEncodedString)(PyObject*, const char*, const char*) = NULL;
PyAPI_FUNC(PyObject*) PyUnicode_AsEncodedString(PyObject* a, const char* b, const char* c) {
    LOG("0x%lx '%s'(0x%lx) '%s'(0x%lx)", (unsigned long) a, b?b:"<null>", (unsigned long) b, c?c:"<null>", (unsigned long) c);
    if (__target__PyUnicode_AsEncodedString == NULL) {
        __target__PyUnicode_AsEncodedString = resolveAPI("PyUnicode_AsEncodedString");
    }
    STATS_BEFORE(PyUnicode_AsEncodedString)
    PyObject* result = (PyObject*) __target__PyUnicode_AsEncodedString(a, b, c);
    STATS_AFTER(PyUnicode_AsEncodedString)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyUnicode_AsEncodedUnicode, PyUnicode_AsEncodedString)
PyObject* (*__target__PyUnicode_AsEncodedUnicode)(PyObject*, const char*, const char*) = NULL;
PyAPI_FUNC(PyObject*) PyUnicode_AsEncodedUnicode(PyObject* a, const char* b, const char* c) {
    LOG("0x%lx '%s'(0x%lx) '%s'(0x%lx)", (unsigned long) a, b?b:"<null>", (unsigned long) b, c?c:"<null>", (unsigned long) c);
    if (__target__PyUnicode_AsEncodedUnicode == NULL) {
        __target__PyUnicode_AsEncodedUnicode = resolveAPI("PyUnicode_AsEncodedUnicode");
    }
    STATS_BEFORE(PyUnicode_AsEncodedUnicode)
    PyObject* result = (PyObject*) __target__PyUnicode_AsEncodedUnicode(a, b, c);
    STATS_AFTER(PyUnicode_AsEncodedUnicode)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyUnicode_AsLatin1String, PyUnicode_AsEncodedUnicode)
PyObject* (*__target__PyUnicode_AsLatin1String)(PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyUnicode_AsLatin1String(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyUnicode_AsLatin1String == NULL) {
        __target__PyUnicode_AsLatin1String = resolveAPI("PyUnicode_AsLatin1String");
    }
    STATS_BEFORE(PyUnicode_AsLatin1String)
    PyObject* result = (PyObject*) __target__PyUnicode_AsLatin1String(a);
    STATS_AFTER(PyUnicode_AsLatin1String)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyUnicode_AsRawUnicodeEscapeString, PyUnicode_AsLatin1String)
PyObject* (*__target__PyUnicode_AsRawUnicodeEscapeString)(PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyUnicode_AsRawUnicodeEscapeString(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyUnicode_AsRawUnicodeEscapeString == NULL) {
        __target__PyUnicode_AsRawUnicodeEscapeString = resolveAPI("PyUnicode_AsRawUnicodeEscapeString");
    }
    STATS_BEFORE(PyUnicode_AsRawUnicodeEscapeString)
    PyObject* result = (PyObject*) __target__PyUnicode_AsRawUnicodeEscapeString(a);
    STATS_AFTER(PyUnicode_AsRawUnicodeEscapeString)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyUnicode_AsUCS4, PyUnicode_AsRawUnicodeEscapeString)
Py_UCS4* (*__target__PyUnicode_AsUCS4)(PyObject*, Py_UCS4*, Py_ssize_t, int) = NULL;
PyAPI_FUNC(Py_UCS4*) PyUnicode_AsUCS4(PyObject* a, Py_UCS4* b, Py_ssize_t c, int d) {
    LOG("0x%lx 0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c, (unsigned long) d);
    if (__target__PyUnicode_AsUCS4 == NULL) {
        __target__PyUnicode_AsUCS4 = resolveAPI("PyUnicode_AsUCS4");
    }
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
    if (__target__PyUnicode_AsUCS4Copy == NULL) {
        __target__PyUnicode_AsUCS4Copy = resolveAPI("PyUnicode_AsUCS4Copy");
    }
    STATS_BEFORE(PyUnicode_AsUCS4Copy)
    Py_UCS4* result = (Py_UCS4*) __target__PyUnicode_AsUCS4Copy(a);
    STATS_AFTER(PyUnicode_AsUCS4Copy)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyUnicode_AsUTF16String, PyUnicode_AsUCS4Copy)
PyObject* (*__target__PyUnicode_AsUTF16String)(PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyUnicode_AsUTF16String(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyUnicode_AsUTF16String == NULL) {
        __target__PyUnicode_AsUTF16String = resolveAPI("PyUnicode_AsUTF16String");
    }
    STATS_BEFORE(PyUnicode_AsUTF16String)
    PyObject* result = (PyObject*) __target__PyUnicode_AsUTF16String(a);
    STATS_AFTER(PyUnicode_AsUTF16String)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyUnicode_AsUTF32String, PyUnicode_AsUTF16String)
PyObject* (*__target__PyUnicode_AsUTF32String)(PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyUnicode_AsUTF32String(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyUnicode_AsUTF32String == NULL) {
        __target__PyUnicode_AsUTF32String = resolveAPI("PyUnicode_AsUTF32String");
    }
    STATS_BEFORE(PyUnicode_AsUTF32String)
    PyObject* result = (PyObject*) __target__PyUnicode_AsUTF32String(a);
    STATS_AFTER(PyUnicode_AsUTF32String)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyUnicode_AsUTF8, PyUnicode_AsUTF32String)
const char* (*__target__PyUnicode_AsUTF8)(PyObject*) = NULL;
PyAPI_FUNC(const char*) PyUnicode_AsUTF8(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyUnicode_AsUTF8 == NULL) {
        __target__PyUnicode_AsUTF8 = resolveAPI("PyUnicode_AsUTF8");
    }
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
    if (__target__PyUnicode_AsUTF8AndSize == NULL) {
        __target__PyUnicode_AsUTF8AndSize = resolveAPI("PyUnicode_AsUTF8AndSize");
    }
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
    if (__target__PyUnicode_AsUTF8String == NULL) {
        __target__PyUnicode_AsUTF8String = resolveAPI("PyUnicode_AsUTF8String");
    }
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
    if (__target__PyUnicode_AsUnicode == NULL) {
        __target__PyUnicode_AsUnicode = resolveAPI("PyUnicode_AsUnicode");
    }
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
    if (__target__PyUnicode_AsUnicodeAndSize == NULL) {
        __target__PyUnicode_AsUnicodeAndSize = resolveAPI("PyUnicode_AsUnicodeAndSize");
    }
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
    if (__target__PyUnicode_AsUnicodeEscapeString == NULL) {
        __target__PyUnicode_AsUnicodeEscapeString = resolveAPI("PyUnicode_AsUnicodeEscapeString");
    }
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
    if (__target__PyUnicode_AsWideChar == NULL) {
        __target__PyUnicode_AsWideChar = resolveAPI("PyUnicode_AsWideChar");
    }
    STATS_BEFORE(PyUnicode_AsWideChar)
    Py_ssize_t result = (Py_ssize_t) __target__PyUnicode_AsWideChar(a, b, c);
    STATS_AFTER(PyUnicode_AsWideChar)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyUnicode_AsWideCharString, PyUnicode_AsWideChar)
wchar_t* (*__target__PyUnicode_AsWideCharString)(PyObject*, Py_ssize_t*) = NULL;
PyAPI_FUNC(wchar_t*) PyUnicode_AsWideCharString(PyObject* a, Py_ssize_t* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target__PyUnicode_AsWideCharString == NULL) {
        __target__PyUnicode_AsWideCharString = resolveAPI("PyUnicode_AsWideCharString");
    }
    STATS_BEFORE(PyUnicode_AsWideCharString)
    wchar_t* result = (wchar_t*) __target__PyUnicode_AsWideCharString(a, b);
    STATS_AFTER(PyUnicode_AsWideCharString)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyUnicode_BuildEncodingMap, PyUnicode_AsWideCharString)
PyObject* (*__target__PyUnicode_BuildEncodingMap)(PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyUnicode_BuildEncodingMap(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyUnicode_BuildEncodingMap == NULL) {
        __target__PyUnicode_BuildEncodingMap = resolveAPI("PyUnicode_BuildEncodingMap");
    }
    STATS_BEFORE(PyUnicode_BuildEncodingMap)
    PyObject* result = (PyObject*) __target__PyUnicode_BuildEncodingMap(a);
    STATS_AFTER(PyUnicode_BuildEncodingMap)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyUnicode_Compare, PyUnicode_BuildEncodingMap)
int (*__target__PyUnicode_Compare)(PyObject*, PyObject*) = NULL;
PyAPI_FUNC(int) PyUnicode_Compare(PyObject* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target__PyUnicode_Compare == NULL) {
        __target__PyUnicode_Compare = resolveAPI("PyUnicode_Compare");
    }
    STATS_BEFORE(PyUnicode_Compare)
    int result = (int) __target__PyUnicode_Compare(a, b);
    STATS_AFTER(PyUnicode_Compare)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyUnicode_CompareWithASCIIString, PyUnicode_Compare)
int (*__target__PyUnicode_CompareWithASCIIString)(PyObject*, const char*) = NULL;
PyAPI_FUNC(int) PyUnicode_CompareWithASCIIString(PyObject* a, const char* b) {
    LOG("0x%lx '%s'(0x%lx)", (unsigned long) a, b?b:"<null>", (unsigned long) b);
    if (__target__PyUnicode_CompareWithASCIIString == NULL) {
        __target__PyUnicode_CompareWithASCIIString = resolveAPI("PyUnicode_CompareWithASCIIString");
    }
    STATS_BEFORE(PyUnicode_CompareWithASCIIString)
    int result = (int) __target__PyUnicode_CompareWithASCIIString(a, b);
    STATS_AFTER(PyUnicode_CompareWithASCIIString)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyUnicode_Concat, PyUnicode_CompareWithASCIIString)
PyObject* (*__target__PyUnicode_Concat)(PyObject*, PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyUnicode_Concat(PyObject* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target__PyUnicode_Concat == NULL) {
        __target__PyUnicode_Concat = resolveAPI("PyUnicode_Concat");
    }
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
    if (__target__PyUnicode_Contains == NULL) {
        __target__PyUnicode_Contains = resolveAPI("PyUnicode_Contains");
    }
    STATS_BEFORE(PyUnicode_Contains)
    int result = (int) __target__PyUnicode_Contains(a, b);
    STATS_AFTER(PyUnicode_Contains)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyUnicode_CopyCharacters, PyUnicode_Contains)
Py_ssize_t (*__target__PyUnicode_CopyCharacters)(PyObject*, Py_ssize_t, PyObject*, Py_ssize_t, Py_ssize_t) = NULL;
PyAPI_FUNC(Py_ssize_t) PyUnicode_CopyCharacters(PyObject* a, Py_ssize_t b, PyObject* c, Py_ssize_t d, Py_ssize_t e) {
    LOG("0x%lx 0x%lx 0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c, (unsigned long) d, (unsigned long) e);
    if (__target__PyUnicode_CopyCharacters == NULL) {
        __target__PyUnicode_CopyCharacters = resolveAPI("PyUnicode_CopyCharacters");
    }
    STATS_BEFORE(PyUnicode_CopyCharacters)
    Py_ssize_t result = (Py_ssize_t) __target__PyUnicode_CopyCharacters(a, b, c, d, e);
    STATS_AFTER(PyUnicode_CopyCharacters)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyUnicode_Count, PyUnicode_CopyCharacters)
Py_ssize_t (*__target__PyUnicode_Count)(PyObject*, PyObject*, Py_ssize_t, Py_ssize_t) = NULL;
PyAPI_FUNC(Py_ssize_t) PyUnicode_Count(PyObject* a, PyObject* b, Py_ssize_t c, Py_ssize_t d) {
    LOG("0x%lx 0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c, (unsigned long) d);
    if (__target__PyUnicode_Count == NULL) {
        __target__PyUnicode_Count = resolveAPI("PyUnicode_Count");
    }
    STATS_BEFORE(PyUnicode_Count)
    Py_ssize_t result = (Py_ssize_t) __target__PyUnicode_Count(a, b, c, d);
    STATS_AFTER(PyUnicode_Count)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyUnicode_Decode, PyUnicode_Count)
PyObject* (*__target__PyUnicode_Decode)(const char*, Py_ssize_t, const char*, const char*) = NULL;
PyAPI_FUNC(PyObject*) PyUnicode_Decode(const char* a, Py_ssize_t b, const char* c, const char* d) {
    LOG("'%s'(0x%lx) 0x%lx '%s'(0x%lx) '%s'(0x%lx)", a?a:"<null>", (unsigned long) a, (unsigned long) b, c?c:"<null>", (unsigned long) c, d?d:"<null>", (unsigned long) d);
    if (__target__PyUnicode_Decode == NULL) {
        __target__PyUnicode_Decode = resolveAPI("PyUnicode_Decode");
    }
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
    if (__target__PyUnicode_DecodeASCII == NULL) {
        __target__PyUnicode_DecodeASCII = resolveAPI("PyUnicode_DecodeASCII");
    }
    STATS_BEFORE(PyUnicode_DecodeASCII)
    PyObject* result = (PyObject*) __target__PyUnicode_DecodeASCII(a, b, c);
    STATS_AFTER(PyUnicode_DecodeASCII)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyUnicode_DecodeCharmap, PyUnicode_DecodeASCII)
PyObject* (*__target__PyUnicode_DecodeCharmap)(const char*, Py_ssize_t, PyObject*, const char*) = NULL;
PyAPI_FUNC(PyObject*) PyUnicode_DecodeCharmap(const char* a, Py_ssize_t b, PyObject* c, const char* d) {
    LOG("'%s'(0x%lx) 0x%lx 0x%lx '%s'(0x%lx)", a?a:"<null>", (unsigned long) a, (unsigned long) b, (unsigned long) c, d?d:"<null>", (unsigned long) d);
    if (__target__PyUnicode_DecodeCharmap == NULL) {
        __target__PyUnicode_DecodeCharmap = resolveAPI("PyUnicode_DecodeCharmap");
    }
    STATS_BEFORE(PyUnicode_DecodeCharmap)
    PyObject* result = (PyObject*) __target__PyUnicode_DecodeCharmap(a, b, c, d);
    STATS_AFTER(PyUnicode_DecodeCharmap)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyUnicode_DecodeFSDefault, PyUnicode_DecodeCharmap)
PyObject* (*__target__PyUnicode_DecodeFSDefault)(const char*) = NULL;
PyAPI_FUNC(PyObject*) PyUnicode_DecodeFSDefault(const char* a) {
    LOG("'%s'(0x%lx)", a?a:"<null>", (unsigned long) a);
    if (__target__PyUnicode_DecodeFSDefault == NULL) {
        __target__PyUnicode_DecodeFSDefault = resolveAPI("PyUnicode_DecodeFSDefault");
    }
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
    if (__target__PyUnicode_DecodeFSDefaultAndSize == NULL) {
        __target__PyUnicode_DecodeFSDefaultAndSize = resolveAPI("PyUnicode_DecodeFSDefaultAndSize");
    }
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
    if (__target__PyUnicode_DecodeLatin1 == NULL) {
        __target__PyUnicode_DecodeLatin1 = resolveAPI("PyUnicode_DecodeLatin1");
    }
    STATS_BEFORE(PyUnicode_DecodeLatin1)
    PyObject* result = (PyObject*) __target__PyUnicode_DecodeLatin1(a, b, c);
    STATS_AFTER(PyUnicode_DecodeLatin1)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyUnicode_DecodeLocale, PyUnicode_DecodeLatin1)
PyObject* (*__target__PyUnicode_DecodeLocale)(const char*, const char*) = NULL;
PyAPI_FUNC(PyObject*) PyUnicode_DecodeLocale(const char* a, const char* b) {
    LOG("'%s'(0x%lx) '%s'(0x%lx)", a?a:"<null>", (unsigned long) a, b?b:"<null>", (unsigned long) b);
    if (__target__PyUnicode_DecodeLocale == NULL) {
        __target__PyUnicode_DecodeLocale = resolveAPI("PyUnicode_DecodeLocale");
    }
    STATS_BEFORE(PyUnicode_DecodeLocale)
    PyObject* result = (PyObject*) __target__PyUnicode_DecodeLocale(a, b);
    STATS_AFTER(PyUnicode_DecodeLocale)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyUnicode_DecodeLocaleAndSize, PyUnicode_DecodeLocale)
PyObject* (*__target__PyUnicode_DecodeLocaleAndSize)(const char*, Py_ssize_t, const char*) = NULL;
PyAPI_FUNC(PyObject*) PyUnicode_DecodeLocaleAndSize(const char* a, Py_ssize_t b, const char* c) {
    LOG("'%s'(0x%lx) 0x%lx '%s'(0x%lx)", a?a:"<null>", (unsigned long) a, (unsigned long) b, c?c:"<null>", (unsigned long) c);
    if (__target__PyUnicode_DecodeLocaleAndSize == NULL) {
        __target__PyUnicode_DecodeLocaleAndSize = resolveAPI("PyUnicode_DecodeLocaleAndSize");
    }
    STATS_BEFORE(PyUnicode_DecodeLocaleAndSize)
    PyObject* result = (PyObject*) __target__PyUnicode_DecodeLocaleAndSize(a, b, c);
    STATS_AFTER(PyUnicode_DecodeLocaleAndSize)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyUnicode_DecodeRawUnicodeEscape, PyUnicode_DecodeLocaleAndSize)
PyObject* (*__target__PyUnicode_DecodeRawUnicodeEscape)(const char*, Py_ssize_t, const char*) = NULL;
PyAPI_FUNC(PyObject*) PyUnicode_DecodeRawUnicodeEscape(const char* a, Py_ssize_t b, const char* c) {
    LOG("'%s'(0x%lx) 0x%lx '%s'(0x%lx)", a?a:"<null>", (unsigned long) a, (unsigned long) b, c?c:"<null>", (unsigned long) c);
    if (__target__PyUnicode_DecodeRawUnicodeEscape == NULL) {
        __target__PyUnicode_DecodeRawUnicodeEscape = resolveAPI("PyUnicode_DecodeRawUnicodeEscape");
    }
    STATS_BEFORE(PyUnicode_DecodeRawUnicodeEscape)
    PyObject* result = (PyObject*) __target__PyUnicode_DecodeRawUnicodeEscape(a, b, c);
    STATS_AFTER(PyUnicode_DecodeRawUnicodeEscape)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyUnicode_DecodeUTF16, PyUnicode_DecodeRawUnicodeEscape)
PyObject* (*__target__PyUnicode_DecodeUTF16)(const char*, Py_ssize_t, const char*, int*) = NULL;
PyAPI_FUNC(PyObject*) PyUnicode_DecodeUTF16(const char* a, Py_ssize_t b, const char* c, int* d) {
    LOG("'%s'(0x%lx) 0x%lx '%s'(0x%lx) 0x%lx", a?a:"<null>", (unsigned long) a, (unsigned long) b, c?c:"<null>", (unsigned long) c, (unsigned long) d);
    if (__target__PyUnicode_DecodeUTF16 == NULL) {
        __target__PyUnicode_DecodeUTF16 = resolveAPI("PyUnicode_DecodeUTF16");
    }
    STATS_BEFORE(PyUnicode_DecodeUTF16)
    PyObject* result = (PyObject*) __target__PyUnicode_DecodeUTF16(a, b, c, d);
    STATS_AFTER(PyUnicode_DecodeUTF16)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyUnicode_DecodeUTF16Stateful, PyUnicode_DecodeUTF16)
PyObject* (*__target__PyUnicode_DecodeUTF16Stateful)(const char*, Py_ssize_t, const char*, int*, Py_ssize_t*) = NULL;
PyAPI_FUNC(PyObject*) PyUnicode_DecodeUTF16Stateful(const char* a, Py_ssize_t b, const char* c, int* d, Py_ssize_t* e) {
    LOG("'%s'(0x%lx) 0x%lx '%s'(0x%lx) 0x%lx 0x%lx", a?a:"<null>", (unsigned long) a, (unsigned long) b, c?c:"<null>", (unsigned long) c, (unsigned long) d, (unsigned long) e);
    if (__target__PyUnicode_DecodeUTF16Stateful == NULL) {
        __target__PyUnicode_DecodeUTF16Stateful = resolveAPI("PyUnicode_DecodeUTF16Stateful");
    }
    STATS_BEFORE(PyUnicode_DecodeUTF16Stateful)
    PyObject* result = (PyObject*) __target__PyUnicode_DecodeUTF16Stateful(a, b, c, d, e);
    STATS_AFTER(PyUnicode_DecodeUTF16Stateful)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyUnicode_DecodeUTF32, PyUnicode_DecodeUTF16Stateful)
PyObject* (*__target__PyUnicode_DecodeUTF32)(const char*, Py_ssize_t, const char*, int*) = NULL;
PyAPI_FUNC(PyObject*) PyUnicode_DecodeUTF32(const char* a, Py_ssize_t b, const char* c, int* d) {
    LOG("'%s'(0x%lx) 0x%lx '%s'(0x%lx) 0x%lx", a?a:"<null>", (unsigned long) a, (unsigned long) b, c?c:"<null>", (unsigned long) c, (unsigned long) d);
    if (__target__PyUnicode_DecodeUTF32 == NULL) {
        __target__PyUnicode_DecodeUTF32 = resolveAPI("PyUnicode_DecodeUTF32");
    }
    STATS_BEFORE(PyUnicode_DecodeUTF32)
    PyObject* result = (PyObject*) __target__PyUnicode_DecodeUTF32(a, b, c, d);
    STATS_AFTER(PyUnicode_DecodeUTF32)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyUnicode_DecodeUTF32Stateful, PyUnicode_DecodeUTF32)
PyObject* (*__target__PyUnicode_DecodeUTF32Stateful)(const char*, Py_ssize_t, const char*, int*, Py_ssize_t*) = NULL;
PyAPI_FUNC(PyObject*) PyUnicode_DecodeUTF32Stateful(const char* a, Py_ssize_t b, const char* c, int* d, Py_ssize_t* e) {
    LOG("'%s'(0x%lx) 0x%lx '%s'(0x%lx) 0x%lx 0x%lx", a?a:"<null>", (unsigned long) a, (unsigned long) b, c?c:"<null>", (unsigned long) c, (unsigned long) d, (unsigned long) e);
    if (__target__PyUnicode_DecodeUTF32Stateful == NULL) {
        __target__PyUnicode_DecodeUTF32Stateful = resolveAPI("PyUnicode_DecodeUTF32Stateful");
    }
    STATS_BEFORE(PyUnicode_DecodeUTF32Stateful)
    PyObject* result = (PyObject*) __target__PyUnicode_DecodeUTF32Stateful(a, b, c, d, e);
    STATS_AFTER(PyUnicode_DecodeUTF32Stateful)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyUnicode_DecodeUTF7, PyUnicode_DecodeUTF32Stateful)
PyObject* (*__target__PyUnicode_DecodeUTF7)(const char*, Py_ssize_t, const char*) = NULL;
PyAPI_FUNC(PyObject*) PyUnicode_DecodeUTF7(const char* a, Py_ssize_t b, const char* c) {
    LOG("'%s'(0x%lx) 0x%lx '%s'(0x%lx)", a?a:"<null>", (unsigned long) a, (unsigned long) b, c?c:"<null>", (unsigned long) c);
    if (__target__PyUnicode_DecodeUTF7 == NULL) {
        __target__PyUnicode_DecodeUTF7 = resolveAPI("PyUnicode_DecodeUTF7");
    }
    STATS_BEFORE(PyUnicode_DecodeUTF7)
    PyObject* result = (PyObject*) __target__PyUnicode_DecodeUTF7(a, b, c);
    STATS_AFTER(PyUnicode_DecodeUTF7)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyUnicode_DecodeUTF7Stateful, PyUnicode_DecodeUTF7)
PyObject* (*__target__PyUnicode_DecodeUTF7Stateful)(const char*, Py_ssize_t, const char*, Py_ssize_t*) = NULL;
PyAPI_FUNC(PyObject*) PyUnicode_DecodeUTF7Stateful(const char* a, Py_ssize_t b, const char* c, Py_ssize_t* d) {
    LOG("'%s'(0x%lx) 0x%lx '%s'(0x%lx) 0x%lx", a?a:"<null>", (unsigned long) a, (unsigned long) b, c?c:"<null>", (unsigned long) c, (unsigned long) d);
    if (__target__PyUnicode_DecodeUTF7Stateful == NULL) {
        __target__PyUnicode_DecodeUTF7Stateful = resolveAPI("PyUnicode_DecodeUTF7Stateful");
    }
    STATS_BEFORE(PyUnicode_DecodeUTF7Stateful)
    PyObject* result = (PyObject*) __target__PyUnicode_DecodeUTF7Stateful(a, b, c, d);
    STATS_AFTER(PyUnicode_DecodeUTF7Stateful)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyUnicode_DecodeUTF8, PyUnicode_DecodeUTF7Stateful)
PyObject* (*__target__PyUnicode_DecodeUTF8)(const char*, Py_ssize_t, const char*) = NULL;
PyAPI_FUNC(PyObject*) PyUnicode_DecodeUTF8(const char* a, Py_ssize_t b, const char* c) {
    LOG("'%s'(0x%lx) 0x%lx '%s'(0x%lx)", a?a:"<null>", (unsigned long) a, (unsigned long) b, c?c:"<null>", (unsigned long) c);
    if (__target__PyUnicode_DecodeUTF8 == NULL) {
        __target__PyUnicode_DecodeUTF8 = resolveAPI("PyUnicode_DecodeUTF8");
    }
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
    if (__target__PyUnicode_DecodeUTF8Stateful == NULL) {
        __target__PyUnicode_DecodeUTF8Stateful = resolveAPI("PyUnicode_DecodeUTF8Stateful");
    }
    STATS_BEFORE(PyUnicode_DecodeUTF8Stateful)
    PyObject* result = (PyObject*) __target__PyUnicode_DecodeUTF8Stateful(a, b, c, d);
    STATS_AFTER(PyUnicode_DecodeUTF8Stateful)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyUnicode_DecodeUnicodeEscape, PyUnicode_DecodeUTF8Stateful)
PyObject* (*__target__PyUnicode_DecodeUnicodeEscape)(const char*, Py_ssize_t, const char*) = NULL;
PyAPI_FUNC(PyObject*) PyUnicode_DecodeUnicodeEscape(const char* a, Py_ssize_t b, const char* c) {
    LOG("'%s'(0x%lx) 0x%lx '%s'(0x%lx)", a?a:"<null>", (unsigned long) a, (unsigned long) b, c?c:"<null>", (unsigned long) c);
    if (__target__PyUnicode_DecodeUnicodeEscape == NULL) {
        __target__PyUnicode_DecodeUnicodeEscape = resolveAPI("PyUnicode_DecodeUnicodeEscape");
    }
    STATS_BEFORE(PyUnicode_DecodeUnicodeEscape)
    PyObject* result = (PyObject*) __target__PyUnicode_DecodeUnicodeEscape(a, b, c);
    STATS_AFTER(PyUnicode_DecodeUnicodeEscape)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyUnicode_Encode, PyUnicode_DecodeUnicodeEscape)
PyObject* (*__target__PyUnicode_Encode)(const Py_UNICODE*, Py_ssize_t, const char*, const char*) = NULL;
PyAPI_FUNC(PyObject*) PyUnicode_Encode(const Py_UNICODE* a, Py_ssize_t b, const char* c, const char* d) {
    LOG("0x%lx 0x%lx '%s'(0x%lx) '%s'(0x%lx)", (unsigned long) a, (unsigned long) b, c?c:"<null>", (unsigned long) c, d?d:"<null>", (unsigned long) d);
    if (__target__PyUnicode_Encode == NULL) {
        __target__PyUnicode_Encode = resolveAPI("PyUnicode_Encode");
    }
    STATS_BEFORE(PyUnicode_Encode)
    PyObject* result = (PyObject*) __target__PyUnicode_Encode(a, b, c, d);
    STATS_AFTER(PyUnicode_Encode)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyUnicode_EncodeASCII, PyUnicode_Encode)
PyObject* (*__target__PyUnicode_EncodeASCII)(const Py_UNICODE*, Py_ssize_t, const char*) = NULL;
PyAPI_FUNC(PyObject*) PyUnicode_EncodeASCII(const Py_UNICODE* a, Py_ssize_t b, const char* c) {
    LOG("0x%lx 0x%lx '%s'(0x%lx)", (unsigned long) a, (unsigned long) b, c?c:"<null>", (unsigned long) c);
    if (__target__PyUnicode_EncodeASCII == NULL) {
        __target__PyUnicode_EncodeASCII = resolveAPI("PyUnicode_EncodeASCII");
    }
    STATS_BEFORE(PyUnicode_EncodeASCII)
    PyObject* result = (PyObject*) __target__PyUnicode_EncodeASCII(a, b, c);
    STATS_AFTER(PyUnicode_EncodeASCII)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyUnicode_EncodeCharmap, PyUnicode_EncodeASCII)
PyObject* (*__target__PyUnicode_EncodeCharmap)(const Py_UNICODE*, Py_ssize_t, PyObject*, const char*) = NULL;
PyAPI_FUNC(PyObject*) PyUnicode_EncodeCharmap(const Py_UNICODE* a, Py_ssize_t b, PyObject* c, const char* d) {
    LOG("0x%lx 0x%lx 0x%lx '%s'(0x%lx)", (unsigned long) a, (unsigned long) b, (unsigned long) c, d?d:"<null>", (unsigned long) d);
    if (__target__PyUnicode_EncodeCharmap == NULL) {
        __target__PyUnicode_EncodeCharmap = resolveAPI("PyUnicode_EncodeCharmap");
    }
    STATS_BEFORE(PyUnicode_EncodeCharmap)
    PyObject* result = (PyObject*) __target__PyUnicode_EncodeCharmap(a, b, c, d);
    STATS_AFTER(PyUnicode_EncodeCharmap)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyUnicode_EncodeDecimal, PyUnicode_EncodeCharmap)
int (*__target__PyUnicode_EncodeDecimal)(Py_UNICODE*, Py_ssize_t, char*, const char*) = NULL;
PyAPI_FUNC(int) PyUnicode_EncodeDecimal(Py_UNICODE* a, Py_ssize_t b, char* c, const char* d) {
    LOG("0x%lx 0x%lx 0x%lx '%s'(0x%lx)", (unsigned long) a, (unsigned long) b, (unsigned long) c, d?d:"<null>", (unsigned long) d);
    if (__target__PyUnicode_EncodeDecimal == NULL) {
        __target__PyUnicode_EncodeDecimal = resolveAPI("PyUnicode_EncodeDecimal");
    }
    STATS_BEFORE(PyUnicode_EncodeDecimal)
    int result = (int) __target__PyUnicode_EncodeDecimal(a, b, c, d);
    STATS_AFTER(PyUnicode_EncodeDecimal)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyUnicode_EncodeFSDefault, PyUnicode_EncodeDecimal)
PyObject* (*__target__PyUnicode_EncodeFSDefault)(PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyUnicode_EncodeFSDefault(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyUnicode_EncodeFSDefault == NULL) {
        __target__PyUnicode_EncodeFSDefault = resolveAPI("PyUnicode_EncodeFSDefault");
    }
    STATS_BEFORE(PyUnicode_EncodeFSDefault)
    PyObject* result = (PyObject*) __target__PyUnicode_EncodeFSDefault(a);
    STATS_AFTER(PyUnicode_EncodeFSDefault)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyUnicode_EncodeLatin1, PyUnicode_EncodeFSDefault)
PyObject* (*__target__PyUnicode_EncodeLatin1)(const Py_UNICODE*, Py_ssize_t, const char*) = NULL;
PyAPI_FUNC(PyObject*) PyUnicode_EncodeLatin1(const Py_UNICODE* a, Py_ssize_t b, const char* c) {
    LOG("0x%lx 0x%lx '%s'(0x%lx)", (unsigned long) a, (unsigned long) b, c?c:"<null>", (unsigned long) c);
    if (__target__PyUnicode_EncodeLatin1 == NULL) {
        __target__PyUnicode_EncodeLatin1 = resolveAPI("PyUnicode_EncodeLatin1");
    }
    STATS_BEFORE(PyUnicode_EncodeLatin1)
    PyObject* result = (PyObject*) __target__PyUnicode_EncodeLatin1(a, b, c);
    STATS_AFTER(PyUnicode_EncodeLatin1)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyUnicode_EncodeLocale, PyUnicode_EncodeLatin1)
PyObject* (*__target__PyUnicode_EncodeLocale)(PyObject*, const char*) = NULL;
PyAPI_FUNC(PyObject*) PyUnicode_EncodeLocale(PyObject* a, const char* b) {
    LOG("0x%lx '%s'(0x%lx)", (unsigned long) a, b?b:"<null>", (unsigned long) b);
    if (__target__PyUnicode_EncodeLocale == NULL) {
        __target__PyUnicode_EncodeLocale = resolveAPI("PyUnicode_EncodeLocale");
    }
    STATS_BEFORE(PyUnicode_EncodeLocale)
    PyObject* result = (PyObject*) __target__PyUnicode_EncodeLocale(a, b);
    STATS_AFTER(PyUnicode_EncodeLocale)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyUnicode_EncodeRawUnicodeEscape, PyUnicode_EncodeLocale)
PyObject* (*__target__PyUnicode_EncodeRawUnicodeEscape)(const Py_UNICODE*, Py_ssize_t) = NULL;
PyAPI_FUNC(PyObject*) PyUnicode_EncodeRawUnicodeEscape(const Py_UNICODE* a, Py_ssize_t b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target__PyUnicode_EncodeRawUnicodeEscape == NULL) {
        __target__PyUnicode_EncodeRawUnicodeEscape = resolveAPI("PyUnicode_EncodeRawUnicodeEscape");
    }
    STATS_BEFORE(PyUnicode_EncodeRawUnicodeEscape)
    PyObject* result = (PyObject*) __target__PyUnicode_EncodeRawUnicodeEscape(a, b);
    STATS_AFTER(PyUnicode_EncodeRawUnicodeEscape)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyUnicode_EncodeUTF16, PyUnicode_EncodeRawUnicodeEscape)
PyObject* (*__target__PyUnicode_EncodeUTF16)(const Py_UNICODE*, Py_ssize_t, const char*, int) = NULL;
PyAPI_FUNC(PyObject*) PyUnicode_EncodeUTF16(const Py_UNICODE* a, Py_ssize_t b, const char* c, int d) {
    LOG("0x%lx 0x%lx '%s'(0x%lx) 0x%lx", (unsigned long) a, (unsigned long) b, c?c:"<null>", (unsigned long) c, (unsigned long) d);
    if (__target__PyUnicode_EncodeUTF16 == NULL) {
        __target__PyUnicode_EncodeUTF16 = resolveAPI("PyUnicode_EncodeUTF16");
    }
    STATS_BEFORE(PyUnicode_EncodeUTF16)
    PyObject* result = (PyObject*) __target__PyUnicode_EncodeUTF16(a, b, c, d);
    STATS_AFTER(PyUnicode_EncodeUTF16)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyUnicode_EncodeUTF32, PyUnicode_EncodeUTF16)
PyObject* (*__target__PyUnicode_EncodeUTF32)(const Py_UNICODE*, Py_ssize_t, const char*, int) = NULL;
PyAPI_FUNC(PyObject*) PyUnicode_EncodeUTF32(const Py_UNICODE* a, Py_ssize_t b, const char* c, int d) {
    LOG("0x%lx 0x%lx '%s'(0x%lx) 0x%lx", (unsigned long) a, (unsigned long) b, c?c:"<null>", (unsigned long) c, (unsigned long) d);
    if (__target__PyUnicode_EncodeUTF32 == NULL) {
        __target__PyUnicode_EncodeUTF32 = resolveAPI("PyUnicode_EncodeUTF32");
    }
    STATS_BEFORE(PyUnicode_EncodeUTF32)
    PyObject* result = (PyObject*) __target__PyUnicode_EncodeUTF32(a, b, c, d);
    STATS_AFTER(PyUnicode_EncodeUTF32)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyUnicode_EncodeUTF7, PyUnicode_EncodeUTF32)
PyObject* (*__target__PyUnicode_EncodeUTF7)(const Py_UNICODE*, Py_ssize_t, int, int, const char*) = NULL;
PyAPI_FUNC(PyObject*) PyUnicode_EncodeUTF7(const Py_UNICODE* a, Py_ssize_t b, int c, int d, const char* e) {
    LOG("0x%lx 0x%lx 0x%lx 0x%lx '%s'(0x%lx)", (unsigned long) a, (unsigned long) b, (unsigned long) c, (unsigned long) d, e?e:"<null>", (unsigned long) e);
    if (__target__PyUnicode_EncodeUTF7 == NULL) {
        __target__PyUnicode_EncodeUTF7 = resolveAPI("PyUnicode_EncodeUTF7");
    }
    STATS_BEFORE(PyUnicode_EncodeUTF7)
    PyObject* result = (PyObject*) __target__PyUnicode_EncodeUTF7(a, b, c, d, e);
    STATS_AFTER(PyUnicode_EncodeUTF7)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyUnicode_EncodeUTF8, PyUnicode_EncodeUTF7)
PyObject* (*__target__PyUnicode_EncodeUTF8)(const Py_UNICODE*, Py_ssize_t, const char*) = NULL;
PyAPI_FUNC(PyObject*) PyUnicode_EncodeUTF8(const Py_UNICODE* a, Py_ssize_t b, const char* c) {
    LOG("0x%lx 0x%lx '%s'(0x%lx)", (unsigned long) a, (unsigned long) b, c?c:"<null>", (unsigned long) c);
    if (__target__PyUnicode_EncodeUTF8 == NULL) {
        __target__PyUnicode_EncodeUTF8 = resolveAPI("PyUnicode_EncodeUTF8");
    }
    STATS_BEFORE(PyUnicode_EncodeUTF8)
    PyObject* result = (PyObject*) __target__PyUnicode_EncodeUTF8(a, b, c);
    STATS_AFTER(PyUnicode_EncodeUTF8)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyUnicode_EncodeUnicodeEscape, PyUnicode_EncodeUTF8)
PyObject* (*__target__PyUnicode_EncodeUnicodeEscape)(const Py_UNICODE*, Py_ssize_t) = NULL;
PyAPI_FUNC(PyObject*) PyUnicode_EncodeUnicodeEscape(const Py_UNICODE* a, Py_ssize_t b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target__PyUnicode_EncodeUnicodeEscape == NULL) {
        __target__PyUnicode_EncodeUnicodeEscape = resolveAPI("PyUnicode_EncodeUnicodeEscape");
    }
    STATS_BEFORE(PyUnicode_EncodeUnicodeEscape)
    PyObject* result = (PyObject*) __target__PyUnicode_EncodeUnicodeEscape(a, b);
    STATS_AFTER(PyUnicode_EncodeUnicodeEscape)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyUnicode_FSConverter, PyUnicode_EncodeUnicodeEscape)
int (*__target__PyUnicode_FSConverter)(PyObject*, void*) = NULL;
PyAPI_FUNC(int) PyUnicode_FSConverter(PyObject* a, void* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target__PyUnicode_FSConverter == NULL) {
        __target__PyUnicode_FSConverter = resolveAPI("PyUnicode_FSConverter");
    }
    STATS_BEFORE(PyUnicode_FSConverter)
    int result = (int) __target__PyUnicode_FSConverter(a, b);
    STATS_AFTER(PyUnicode_FSConverter)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyUnicode_FSDecoder, PyUnicode_FSConverter)
int (*__target__PyUnicode_FSDecoder)(PyObject*, void*) = NULL;
PyAPI_FUNC(int) PyUnicode_FSDecoder(PyObject* a, void* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target__PyUnicode_FSDecoder == NULL) {
        __target__PyUnicode_FSDecoder = resolveAPI("PyUnicode_FSDecoder");
    }
    STATS_BEFORE(PyUnicode_FSDecoder)
    int result = (int) __target__PyUnicode_FSDecoder(a, b);
    STATS_AFTER(PyUnicode_FSDecoder)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyUnicode_Fill, PyUnicode_FSDecoder)
Py_ssize_t (*__target__PyUnicode_Fill)(PyObject*, Py_ssize_t, Py_ssize_t, Py_UCS4) = NULL;
PyAPI_FUNC(Py_ssize_t) PyUnicode_Fill(PyObject* a, Py_ssize_t b, Py_ssize_t c, Py_UCS4 d) {
    LOG("0x%lx 0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c, (unsigned long) d);
    if (__target__PyUnicode_Fill == NULL) {
        __target__PyUnicode_Fill = resolveAPI("PyUnicode_Fill");
    }
    STATS_BEFORE(PyUnicode_Fill)
    Py_ssize_t result = (Py_ssize_t) __target__PyUnicode_Fill(a, b, c, d);
    STATS_AFTER(PyUnicode_Fill)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyUnicode_Find, PyUnicode_Fill)
Py_ssize_t (*__target__PyUnicode_Find)(PyObject*, PyObject*, Py_ssize_t, Py_ssize_t, int) = NULL;
PyAPI_FUNC(Py_ssize_t) PyUnicode_Find(PyObject* a, PyObject* b, Py_ssize_t c, Py_ssize_t d, int e) {
    LOG("0x%lx 0x%lx 0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c, (unsigned long) d, (unsigned long) e);
    if (__target__PyUnicode_Find == NULL) {
        __target__PyUnicode_Find = resolveAPI("PyUnicode_Find");
    }
    STATS_BEFORE(PyUnicode_Find)
    Py_ssize_t result = (Py_ssize_t) __target__PyUnicode_Find(a, b, c, d, e);
    STATS_AFTER(PyUnicode_Find)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyUnicode_FindChar, PyUnicode_Find)
Py_ssize_t (*__target__PyUnicode_FindChar)(PyObject*, Py_UCS4, Py_ssize_t, Py_ssize_t, int) = NULL;
PyAPI_FUNC(Py_ssize_t) PyUnicode_FindChar(PyObject* a, Py_UCS4 b, Py_ssize_t c, Py_ssize_t d, int e) {
    LOG("0x%lx 0x%lx 0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c, (unsigned long) d, (unsigned long) e);
    if (__target__PyUnicode_FindChar == NULL) {
        __target__PyUnicode_FindChar = resolveAPI("PyUnicode_FindChar");
    }
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
    if (__target__PyUnicode_Format == NULL) {
        __target__PyUnicode_Format = resolveAPI("PyUnicode_Format");
    }
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
    if (__target__PyUnicode_FromEncodedObject == NULL) {
        __target__PyUnicode_FromEncodedObject = resolveAPI("PyUnicode_FromEncodedObject");
    }
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
    if (__target__PyUnicode_FromFormatV == NULL) {
        __target__PyUnicode_FromFormatV = resolveAPI("PyUnicode_FromFormatV");
    }
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
    if (__target__PyUnicode_FromKindAndData == NULL) {
        __target__PyUnicode_FromKindAndData = resolveAPI("PyUnicode_FromKindAndData");
    }
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
    if (__target__PyUnicode_FromObject == NULL) {
        __target__PyUnicode_FromObject = resolveAPI("PyUnicode_FromObject");
    }
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
    if (__target__PyUnicode_FromOrdinal == NULL) {
        __target__PyUnicode_FromOrdinal = resolveAPI("PyUnicode_FromOrdinal");
    }
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
    if (__target__PyUnicode_FromString == NULL) {
        __target__PyUnicode_FromString = resolveAPI("PyUnicode_FromString");
    }
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
    if (__target__PyUnicode_FromStringAndSize == NULL) {
        __target__PyUnicode_FromStringAndSize = resolveAPI("PyUnicode_FromStringAndSize");
    }
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
    if (__target__PyUnicode_FromUnicode == NULL) {
        __target__PyUnicode_FromUnicode = resolveAPI("PyUnicode_FromUnicode");
    }
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
    if (__target__PyUnicode_FromWideChar == NULL) {
        __target__PyUnicode_FromWideChar = resolveAPI("PyUnicode_FromWideChar");
    }
    STATS_BEFORE(PyUnicode_FromWideChar)
    PyObject* result = (PyObject*) __target__PyUnicode_FromWideChar(a, b);
    STATS_AFTER(PyUnicode_FromWideChar)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyUnicode_GetDefaultEncoding, PyUnicode_FromWideChar)
const char* (*__target__PyUnicode_GetDefaultEncoding)() = NULL;
PyAPI_FUNC(const char*) PyUnicode_GetDefaultEncoding() {
    LOGS("");
    if (__target__PyUnicode_GetDefaultEncoding == NULL) {
        __target__PyUnicode_GetDefaultEncoding = resolveAPI("PyUnicode_GetDefaultEncoding");
    }
    STATS_BEFORE(PyUnicode_GetDefaultEncoding)
    const char* result = (const char*) __target__PyUnicode_GetDefaultEncoding();
    STATS_AFTER(PyUnicode_GetDefaultEncoding)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyUnicode_GetLength, PyUnicode_GetDefaultEncoding)
Py_ssize_t (*__target__PyUnicode_GetLength)(PyObject*) = NULL;
PyAPI_FUNC(Py_ssize_t) PyUnicode_GetLength(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyUnicode_GetLength == NULL) {
        __target__PyUnicode_GetLength = resolveAPI("PyUnicode_GetLength");
    }
    STATS_BEFORE(PyUnicode_GetLength)
    Py_ssize_t result = (Py_ssize_t) __target__PyUnicode_GetLength(a);
    STATS_AFTER(PyUnicode_GetLength)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyUnicode_GetSize, PyUnicode_GetLength)
Py_ssize_t (*__target__PyUnicode_GetSize)(PyObject*) = NULL;
PyAPI_FUNC(Py_ssize_t) PyUnicode_GetSize(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyUnicode_GetSize == NULL) {
        __target__PyUnicode_GetSize = resolveAPI("PyUnicode_GetSize");
    }
    STATS_BEFORE(PyUnicode_GetSize)
    Py_ssize_t result = (Py_ssize_t) __target__PyUnicode_GetSize(a);
    STATS_AFTER(PyUnicode_GetSize)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyUnicode_InternFromString, PyUnicode_GetSize)
PyObject* (*__target__PyUnicode_InternFromString)(const char*) = NULL;
PyAPI_FUNC(PyObject*) PyUnicode_InternFromString(const char* a) {
    LOG("'%s'(0x%lx)", a?a:"<null>", (unsigned long) a);
    if (__target__PyUnicode_InternFromString == NULL) {
        __target__PyUnicode_InternFromString = resolveAPI("PyUnicode_InternFromString");
    }
    STATS_BEFORE(PyUnicode_InternFromString)
    PyObject* result = (PyObject*) __target__PyUnicode_InternFromString(a);
    STATS_AFTER(PyUnicode_InternFromString)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyUnicode_InternImmortal, PyUnicode_InternFromString)
void (*__target__PyUnicode_InternImmortal)(PyObject**) = NULL;
PyAPI_FUNC(void) PyUnicode_InternImmortal(PyObject** a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyUnicode_InternImmortal == NULL) {
        __target__PyUnicode_InternImmortal = resolveAPI("PyUnicode_InternImmortal");
    }
    STATS_BEFORE(PyUnicode_InternImmortal)
    __target__PyUnicode_InternImmortal(a);
    STATS_AFTER(PyUnicode_InternImmortal)
    LOG_AFTER_VOID
}
STATS_CONTAINER(PyUnicode_InternInPlace, PyUnicode_InternImmortal)
void (*__target__PyUnicode_InternInPlace)(PyObject**) = NULL;
PyAPI_FUNC(void) PyUnicode_InternInPlace(PyObject** a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyUnicode_InternInPlace == NULL) {
        __target__PyUnicode_InternInPlace = resolveAPI("PyUnicode_InternInPlace");
    }
    STATS_BEFORE(PyUnicode_InternInPlace)
    __target__PyUnicode_InternInPlace(a);
    STATS_AFTER(PyUnicode_InternInPlace)
    LOG_AFTER_VOID
}
STATS_CONTAINER(PyUnicode_IsIdentifier, PyUnicode_InternInPlace)
int (*__target__PyUnicode_IsIdentifier)(PyObject*) = NULL;
PyAPI_FUNC(int) PyUnicode_IsIdentifier(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__PyUnicode_IsIdentifier == NULL) {
        __target__PyUnicode_IsIdentifier = resolveAPI("PyUnicode_IsIdentifier");
    }
    STATS_BEFORE(PyUnicode_IsIdentifier)
    int result = (int) __target__PyUnicode_IsIdentifier(a);
    STATS_AFTER(PyUnicode_IsIdentifier)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyUnicode_Join, PyUnicode_IsIdentifier)
PyObject* (*__target__PyUnicode_Join)(PyObject*, PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyUnicode_Join(PyObject* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target__PyUnicode_Join == NULL) {
        __target__PyUnicode_Join = resolveAPI("PyUnicode_Join");
    }
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
    if (__target__PyUnicode_New == NULL) {
        __target__PyUnicode_New = resolveAPI("PyUnicode_New");
    }
    STATS_BEFORE(PyUnicode_New)
    PyObject* result = (PyObject*) __target__PyUnicode_New(a, b);
    STATS_AFTER(PyUnicode_New)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyUnicode_Partition, PyUnicode_New)
PyObject* (*__target__PyUnicode_Partition)(PyObject*, PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyUnicode_Partition(PyObject* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target__PyUnicode_Partition == NULL) {
        __target__PyUnicode_Partition = resolveAPI("PyUnicode_Partition");
    }
    STATS_BEFORE(PyUnicode_Partition)
    PyObject* result = (PyObject*) __target__PyUnicode_Partition(a, b);
    STATS_AFTER(PyUnicode_Partition)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyUnicode_RPartition, PyUnicode_Partition)
PyObject* (*__target__PyUnicode_RPartition)(PyObject*, PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyUnicode_RPartition(PyObject* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target__PyUnicode_RPartition == NULL) {
        __target__PyUnicode_RPartition = resolveAPI("PyUnicode_RPartition");
    }
    STATS_BEFORE(PyUnicode_RPartition)
    PyObject* result = (PyObject*) __target__PyUnicode_RPartition(a, b);
    STATS_AFTER(PyUnicode_RPartition)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyUnicode_RSplit, PyUnicode_RPartition)
PyObject* (*__target__PyUnicode_RSplit)(PyObject*, PyObject*, Py_ssize_t) = NULL;
PyAPI_FUNC(PyObject*) PyUnicode_RSplit(PyObject* a, PyObject* b, Py_ssize_t c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    if (__target__PyUnicode_RSplit == NULL) {
        __target__PyUnicode_RSplit = resolveAPI("PyUnicode_RSplit");
    }
    STATS_BEFORE(PyUnicode_RSplit)
    PyObject* result = (PyObject*) __target__PyUnicode_RSplit(a, b, c);
    STATS_AFTER(PyUnicode_RSplit)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyUnicode_ReadChar, PyUnicode_RSplit)
Py_UCS4 (*__target__PyUnicode_ReadChar)(PyObject*, Py_ssize_t) = NULL;
PyAPI_FUNC(Py_UCS4) PyUnicode_ReadChar(PyObject* a, Py_ssize_t b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target__PyUnicode_ReadChar == NULL) {
        __target__PyUnicode_ReadChar = resolveAPI("PyUnicode_ReadChar");
    }
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
    if (__target__PyUnicode_Replace == NULL) {
        __target__PyUnicode_Replace = resolveAPI("PyUnicode_Replace");
    }
    STATS_BEFORE(PyUnicode_Replace)
    PyObject* result = (PyObject*) __target__PyUnicode_Replace(a, b, c, d);
    STATS_AFTER(PyUnicode_Replace)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyUnicode_Resize, PyUnicode_Replace)
int (*__target__PyUnicode_Resize)(PyObject**, Py_ssize_t) = NULL;
PyAPI_FUNC(int) PyUnicode_Resize(PyObject** a, Py_ssize_t b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target__PyUnicode_Resize == NULL) {
        __target__PyUnicode_Resize = resolveAPI("PyUnicode_Resize");
    }
    STATS_BEFORE(PyUnicode_Resize)
    int result = (int) __target__PyUnicode_Resize(a, b);
    STATS_AFTER(PyUnicode_Resize)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyUnicode_RichCompare, PyUnicode_Resize)
PyObject* (*__target__PyUnicode_RichCompare)(PyObject*, PyObject*, int) = NULL;
PyAPI_FUNC(PyObject*) PyUnicode_RichCompare(PyObject* a, PyObject* b, int c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    if (__target__PyUnicode_RichCompare == NULL) {
        __target__PyUnicode_RichCompare = resolveAPI("PyUnicode_RichCompare");
    }
    STATS_BEFORE(PyUnicode_RichCompare)
    PyObject* result = (PyObject*) __target__PyUnicode_RichCompare(a, b, c);
    STATS_AFTER(PyUnicode_RichCompare)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyUnicode_Split, PyUnicode_RichCompare)
PyObject* (*__target__PyUnicode_Split)(PyObject*, PyObject*, Py_ssize_t) = NULL;
PyAPI_FUNC(PyObject*) PyUnicode_Split(PyObject* a, PyObject* b, Py_ssize_t c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    if (__target__PyUnicode_Split == NULL) {
        __target__PyUnicode_Split = resolveAPI("PyUnicode_Split");
    }
    STATS_BEFORE(PyUnicode_Split)
    PyObject* result = (PyObject*) __target__PyUnicode_Split(a, b, c);
    STATS_AFTER(PyUnicode_Split)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyUnicode_Splitlines, PyUnicode_Split)
PyObject* (*__target__PyUnicode_Splitlines)(PyObject*, int) = NULL;
PyAPI_FUNC(PyObject*) PyUnicode_Splitlines(PyObject* a, int b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target__PyUnicode_Splitlines == NULL) {
        __target__PyUnicode_Splitlines = resolveAPI("PyUnicode_Splitlines");
    }
    STATS_BEFORE(PyUnicode_Splitlines)
    PyObject* result = (PyObject*) __target__PyUnicode_Splitlines(a, b);
    STATS_AFTER(PyUnicode_Splitlines)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyUnicode_Substring, PyUnicode_Splitlines)
PyObject* (*__target__PyUnicode_Substring)(PyObject*, Py_ssize_t, Py_ssize_t) = NULL;
PyAPI_FUNC(PyObject*) PyUnicode_Substring(PyObject* a, Py_ssize_t b, Py_ssize_t c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    if (__target__PyUnicode_Substring == NULL) {
        __target__PyUnicode_Substring = resolveAPI("PyUnicode_Substring");
    }
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
    if (__target__PyUnicode_Tailmatch == NULL) {
        __target__PyUnicode_Tailmatch = resolveAPI("PyUnicode_Tailmatch");
    }
    STATS_BEFORE(PyUnicode_Tailmatch)
    Py_ssize_t result = (Py_ssize_t) __target__PyUnicode_Tailmatch(a, b, c, d, e);
    STATS_AFTER(PyUnicode_Tailmatch)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyUnicode_TransformDecimalToASCII, PyUnicode_Tailmatch)
PyObject* (*__target__PyUnicode_TransformDecimalToASCII)(Py_UNICODE*, Py_ssize_t) = NULL;
PyAPI_FUNC(PyObject*) PyUnicode_TransformDecimalToASCII(Py_UNICODE* a, Py_ssize_t b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target__PyUnicode_TransformDecimalToASCII == NULL) {
        __target__PyUnicode_TransformDecimalToASCII = resolveAPI("PyUnicode_TransformDecimalToASCII");
    }
    STATS_BEFORE(PyUnicode_TransformDecimalToASCII)
    PyObject* result = (PyObject*) __target__PyUnicode_TransformDecimalToASCII(a, b);
    STATS_AFTER(PyUnicode_TransformDecimalToASCII)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyUnicode_Translate, PyUnicode_TransformDecimalToASCII)
PyObject* (*__target__PyUnicode_Translate)(PyObject*, PyObject*, const char*) = NULL;
PyAPI_FUNC(PyObject*) PyUnicode_Translate(PyObject* a, PyObject* b, const char* c) {
    LOG("0x%lx 0x%lx '%s'(0x%lx)", (unsigned long) a, (unsigned long) b, c?c:"<null>", (unsigned long) c);
    if (__target__PyUnicode_Translate == NULL) {
        __target__PyUnicode_Translate = resolveAPI("PyUnicode_Translate");
    }
    STATS_BEFORE(PyUnicode_Translate)
    PyObject* result = (PyObject*) __target__PyUnicode_Translate(a, b, c);
    STATS_AFTER(PyUnicode_Translate)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyUnicode_TranslateCharmap, PyUnicode_Translate)
PyObject* (*__target__PyUnicode_TranslateCharmap)(const Py_UNICODE*, Py_ssize_t, PyObject*, const char*) = NULL;
PyAPI_FUNC(PyObject*) PyUnicode_TranslateCharmap(const Py_UNICODE* a, Py_ssize_t b, PyObject* c, const char* d) {
    LOG("0x%lx 0x%lx 0x%lx '%s'(0x%lx)", (unsigned long) a, (unsigned long) b, (unsigned long) c, d?d:"<null>", (unsigned long) d);
    if (__target__PyUnicode_TranslateCharmap == NULL) {
        __target__PyUnicode_TranslateCharmap = resolveAPI("PyUnicode_TranslateCharmap");
    }
    STATS_BEFORE(PyUnicode_TranslateCharmap)
    PyObject* result = (PyObject*) __target__PyUnicode_TranslateCharmap(a, b, c, d);
    STATS_AFTER(PyUnicode_TranslateCharmap)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyUnicode_WriteChar, PyUnicode_TranslateCharmap)
int (*__target__PyUnicode_WriteChar)(PyObject*, Py_ssize_t, Py_UCS4) = NULL;
PyAPI_FUNC(int) PyUnicode_WriteChar(PyObject* a, Py_ssize_t b, Py_UCS4 c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    if (__target__PyUnicode_WriteChar == NULL) {
        __target__PyUnicode_WriteChar = resolveAPI("PyUnicode_WriteChar");
    }
    STATS_BEFORE(PyUnicode_WriteChar)
    int result = (int) __target__PyUnicode_WriteChar(a, b, c);
    STATS_AFTER(PyUnicode_WriteChar)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyVectorcall_Call, PyUnicode_WriteChar)
PyObject* (*__target__PyVectorcall_Call)(PyObject*, PyObject*, PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyVectorcall_Call(PyObject* a, PyObject* b, PyObject* c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    if (__target__PyVectorcall_Call == NULL) {
        __target__PyVectorcall_Call = resolveAPI("PyVectorcall_Call");
    }
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
    if (__target__PyWeakref_GetObject == NULL) {
        __target__PyWeakref_GetObject = resolveAPI("PyWeakref_GetObject");
    }
    STATS_BEFORE(PyWeakref_GetObject)
    PyObject* result = (PyObject*) __target__PyWeakref_GetObject(a);
    STATS_AFTER(PyWeakref_GetObject)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyWeakref_NewProxy, PyWeakref_GetObject)
PyObject* (*__target__PyWeakref_NewProxy)(PyObject*, PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyWeakref_NewProxy(PyObject* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target__PyWeakref_NewProxy == NULL) {
        __target__PyWeakref_NewProxy = resolveAPI("PyWeakref_NewProxy");
    }
    STATS_BEFORE(PyWeakref_NewProxy)
    PyObject* result = (PyObject*) __target__PyWeakref_NewProxy(a, b);
    STATS_AFTER(PyWeakref_NewProxy)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyWeakref_NewRef, PyWeakref_NewProxy)
PyObject* (*__target__PyWeakref_NewRef)(PyObject*, PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyWeakref_NewRef(PyObject* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target__PyWeakref_NewRef == NULL) {
        __target__PyWeakref_NewRef = resolveAPI("PyWeakref_NewRef");
    }
    STATS_BEFORE(PyWeakref_NewRef)
    PyObject* result = (PyObject*) __target__PyWeakref_NewRef(a, b);
    STATS_AFTER(PyWeakref_NewRef)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyWideStringList_Append, PyWeakref_NewRef)
PyStatus (*__target__PyWideStringList_Append)(PyWideStringList*, const wchar_t*) = NULL;
PyAPI_FUNC(PyStatus) PyWideStringList_Append(PyWideStringList* a, const wchar_t* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target__PyWideStringList_Append == NULL) {
        __target__PyWideStringList_Append = resolveAPI("PyWideStringList_Append");
    }
    STATS_BEFORE(PyWideStringList_Append)
    PyStatus result = (PyStatus) __target__PyWideStringList_Append(a, b);
    STATS_AFTER(PyWideStringList_Append)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyWideStringList_Insert, PyWideStringList_Append)
PyStatus (*__target__PyWideStringList_Insert)(PyWideStringList*, Py_ssize_t, const wchar_t*) = NULL;
PyAPI_FUNC(PyStatus) PyWideStringList_Insert(PyWideStringList* a, Py_ssize_t b, const wchar_t* c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    if (__target__PyWideStringList_Insert == NULL) {
        __target__PyWideStringList_Insert = resolveAPI("PyWideStringList_Insert");
    }
    STATS_BEFORE(PyWideStringList_Insert)
    PyStatus result = (PyStatus) __target__PyWideStringList_Insert(a, b, c);
    STATS_AFTER(PyWideStringList_Insert)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(PyWrapper_New, PyWideStringList_Insert)
PyObject* (*__target__PyWrapper_New)(PyObject*, PyObject*) = NULL;
PyAPI_FUNC(PyObject*) PyWrapper_New(PyObject* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target__PyWrapper_New == NULL) {
        __target__PyWrapper_New = resolveAPI("PyWrapper_New");
    }
    STATS_BEFORE(PyWrapper_New)
    PyObject* result = (PyObject*) __target__PyWrapper_New(a, b);
    STATS_AFTER(PyWrapper_New)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(Py_AddPendingCall, PyWrapper_New)
int (*__target__Py_AddPendingCall)(int (*)(void*), void*) = NULL;
PyAPI_FUNC(int) Py_AddPendingCall(int (*a)(void*), void* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target__Py_AddPendingCall == NULL) {
        __target__Py_AddPendingCall = resolveAPI("Py_AddPendingCall");
    }
    STATS_BEFORE(Py_AddPendingCall)
    int result = (int) __target__Py_AddPendingCall(a, b);
    STATS_AFTER(Py_AddPendingCall)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(Py_AtExit, Py_AddPendingCall)
int (*__target__Py_AtExit)(void (*)(void)) = NULL;
PyAPI_FUNC(int) Py_AtExit(void (*a)(void)) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__Py_AtExit == NULL) {
        __target__Py_AtExit = resolveAPI("Py_AtExit");
    }
    STATS_BEFORE(Py_AtExit)
    int result = (int) __target__Py_AtExit(a);
    STATS_AFTER(Py_AtExit)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(Py_BytesMain, Py_AtExit)
int (*__target__Py_BytesMain)(int, char**) = NULL;
PyAPI_FUNC(int) Py_BytesMain(int a, char** b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target__Py_BytesMain == NULL) {
        __target__Py_BytesMain = resolveAPI("Py_BytesMain");
    }
    STATS_BEFORE(Py_BytesMain)
    int result = (int) __target__Py_BytesMain(a, b);
    STATS_AFTER(Py_BytesMain)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(Py_CompileString, Py_BytesMain)
PyObject* (*__target__Py_CompileString)(const char*, const char*, int) = NULL;
PyAPI_FUNC(PyObject*) Py_CompileString(const char* a, const char* b, int c) {
    LOG("'%s'(0x%lx) '%s'(0x%lx) 0x%lx", a?a:"<null>", (unsigned long) a, b?b:"<null>", (unsigned long) b, (unsigned long) c);
    if (__target__Py_CompileString == NULL) {
        __target__Py_CompileString = resolveAPI("Py_CompileString");
    }
    STATS_BEFORE(Py_CompileString)
    PyObject* result = (PyObject*) __target__Py_CompileString(a, b, c);
    STATS_AFTER(Py_CompileString)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(Py_CompileStringExFlags, Py_CompileString)
PyObject* (*__target__Py_CompileStringExFlags)(const char*, const char*, int, PyCompilerFlags*, int) = NULL;
PyAPI_FUNC(PyObject*) Py_CompileStringExFlags(const char* a, const char* b, int c, PyCompilerFlags* d, int e) {
    LOG("'%s'(0x%lx) '%s'(0x%lx) 0x%lx 0x%lx 0x%lx", a?a:"<null>", (unsigned long) a, b?b:"<null>", (unsigned long) b, (unsigned long) c, (unsigned long) d, (unsigned long) e);
    if (__target__Py_CompileStringExFlags == NULL) {
        __target__Py_CompileStringExFlags = resolveAPI("Py_CompileStringExFlags");
    }
    STATS_BEFORE(Py_CompileStringExFlags)
    PyObject* result = (PyObject*) __target__Py_CompileStringExFlags(a, b, c, d, e);
    STATS_AFTER(Py_CompileStringExFlags)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(Py_CompileStringObject, Py_CompileStringExFlags)
PyObject* (*__target__Py_CompileStringObject)(const char*, PyObject*, int, PyCompilerFlags*, int) = NULL;
PyAPI_FUNC(PyObject*) Py_CompileStringObject(const char* a, PyObject* b, int c, PyCompilerFlags* d, int e) {
    LOG("'%s'(0x%lx) 0x%lx 0x%lx 0x%lx 0x%lx", a?a:"<null>", (unsigned long) a, (unsigned long) b, (unsigned long) c, (unsigned long) d, (unsigned long) e);
    if (__target__Py_CompileStringObject == NULL) {
        __target__Py_CompileStringObject = resolveAPI("Py_CompileStringObject");
    }
    STATS_BEFORE(Py_CompileStringObject)
    PyObject* result = (PyObject*) __target__Py_CompileStringObject(a, b, c, d, e);
    STATS_AFTER(Py_CompileStringObject)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(Py_DecRef, Py_CompileStringObject)
void (*__target__Py_DecRef)(PyObject*) = NULL;
PyAPI_FUNC(void) Py_DecRef(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__Py_DecRef == NULL) {
        __target__Py_DecRef = resolveAPI("Py_DecRef");
    }
    STATS_BEFORE(Py_DecRef)
    __target__Py_DecRef(a);
    STATS_AFTER(Py_DecRef)
    LOG_AFTER_VOID
}
STATS_CONTAINER(Py_DecodeLocale, Py_DecRef)
wchar_t* (*__target__Py_DecodeLocale)(const char*, size_t*) = NULL;
PyAPI_FUNC(wchar_t*) Py_DecodeLocale(const char* a, size_t* b) {
    LOG("'%s'(0x%lx) 0x%lx", a?a:"<null>", (unsigned long) a, (unsigned long) b);
    if (__target__Py_DecodeLocale == NULL) {
        __target__Py_DecodeLocale = resolveAPI("Py_DecodeLocale");
    }
    STATS_BEFORE(Py_DecodeLocale)
    wchar_t* result = (wchar_t*) __target__Py_DecodeLocale(a, b);
    STATS_AFTER(Py_DecodeLocale)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(Py_EncodeLocale, Py_DecodeLocale)
char* (*__target__Py_EncodeLocale)(const wchar_t*, size_t*) = NULL;
PyAPI_FUNC(char*) Py_EncodeLocale(const wchar_t* a, size_t* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target__Py_EncodeLocale == NULL) {
        __target__Py_EncodeLocale = resolveAPI("Py_EncodeLocale");
    }
    STATS_BEFORE(Py_EncodeLocale)
    char* result = (char*) __target__Py_EncodeLocale(a, b);
    STATS_AFTER(Py_EncodeLocale)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(Py_EndInterpreter, Py_EncodeLocale)
void (*__target__Py_EndInterpreter)(PyThreadState*) = NULL;
PyAPI_FUNC(void) Py_EndInterpreter(PyThreadState* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__Py_EndInterpreter == NULL) {
        __target__Py_EndInterpreter = resolveAPI("Py_EndInterpreter");
    }
    STATS_BEFORE(Py_EndInterpreter)
    __target__Py_EndInterpreter(a);
    STATS_AFTER(Py_EndInterpreter)
    LOG_AFTER_VOID
}
STATS_CONTAINER(Py_EnterRecursiveCall, Py_EndInterpreter)
int (*__target__Py_EnterRecursiveCall)(const char*) = NULL;
PyAPI_FUNC(int) Py_EnterRecursiveCall(const char* a) {
    LOG("'%s'(0x%lx)", a?a:"<null>", (unsigned long) a);
    if (__target__Py_EnterRecursiveCall == NULL) {
        __target__Py_EnterRecursiveCall = resolveAPI("Py_EnterRecursiveCall");
    }
    STATS_BEFORE(Py_EnterRecursiveCall)
    int result = (int) __target__Py_EnterRecursiveCall(a);
    STATS_AFTER(Py_EnterRecursiveCall)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(Py_Exit, Py_EnterRecursiveCall)
void (*__target__Py_Exit)(int) = NULL;
PyAPI_FUNC(void) Py_Exit(int a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__Py_Exit == NULL) {
        __target__Py_Exit = resolveAPI("Py_Exit");
    }
    STATS_BEFORE(Py_Exit)
    __target__Py_Exit(a);
    STATS_AFTER(Py_Exit)
    LOG_AFTER_VOID
    abort();
}
STATS_CONTAINER(Py_ExitStatusException, Py_Exit)
void (*__target__Py_ExitStatusException)(PyStatus) = NULL;
PyAPI_FUNC(void) Py_ExitStatusException(PyStatus a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__Py_ExitStatusException == NULL) {
        __target__Py_ExitStatusException = resolveAPI("Py_ExitStatusException");
    }
    STATS_BEFORE(Py_ExitStatusException)
    __target__Py_ExitStatusException(a);
    STATS_AFTER(Py_ExitStatusException)
    LOG_AFTER_VOID
    abort();
}
STATS_CONTAINER(Py_FatalError, Py_ExitStatusException)
void (*__target__Py_FatalError)(const char*) = NULL;
PyAPI_FUNC(void) Py_FatalError(const char* a) {
    LOG("'%s'(0x%lx)", a?a:"<null>", (unsigned long) a);
    if (__target__Py_FatalError == NULL) {
        __target__Py_FatalError = resolveAPI("Py_FatalError");
    }
    STATS_BEFORE(Py_FatalError)
    __target__Py_FatalError(a);
    STATS_AFTER(Py_FatalError)
    LOG_AFTER_VOID
    abort();
}
STATS_CONTAINER(Py_FdIsInteractive, Py_FatalError)
int (*__target__Py_FdIsInteractive)(FILE*, const char*) = NULL;
PyAPI_FUNC(int) Py_FdIsInteractive(FILE* a, const char* b) {
    LOG("0x%lx '%s'(0x%lx)", (unsigned long) a, b?b:"<null>", (unsigned long) b);
    if (__target__Py_FdIsInteractive == NULL) {
        __target__Py_FdIsInteractive = resolveAPI("Py_FdIsInteractive");
    }
    STATS_BEFORE(Py_FdIsInteractive)
    int result = (int) __target__Py_FdIsInteractive(a, b);
    STATS_AFTER(Py_FdIsInteractive)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(Py_Finalize, Py_FdIsInteractive)
void (*__target__Py_Finalize)() = NULL;
PyAPI_FUNC(void) Py_Finalize() {
    LOGS("");
    if (__target__Py_Finalize == NULL) {
        __target__Py_Finalize = resolveAPI("Py_Finalize");
    }
    STATS_BEFORE(Py_Finalize)
    __target__Py_Finalize();
    STATS_AFTER(Py_Finalize)
    LOG_AFTER_VOID
}
STATS_CONTAINER(Py_FinalizeEx, Py_Finalize)
int (*__target__Py_FinalizeEx)() = NULL;
PyAPI_FUNC(int) Py_FinalizeEx() {
    LOGS("");
    if (__target__Py_FinalizeEx == NULL) {
        __target__Py_FinalizeEx = resolveAPI("Py_FinalizeEx");
    }
    STATS_BEFORE(Py_FinalizeEx)
    int result = (int) __target__Py_FinalizeEx();
    STATS_AFTER(Py_FinalizeEx)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(Py_FrozenMain, Py_FinalizeEx)
int (*__target__Py_FrozenMain)(int, char**) = NULL;
PyAPI_FUNC(int) Py_FrozenMain(int a, char** b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target__Py_FrozenMain == NULL) {
        __target__Py_FrozenMain = resolveAPI("Py_FrozenMain");
    }
    STATS_BEFORE(Py_FrozenMain)
    int result = (int) __target__Py_FrozenMain(a, b);
    STATS_AFTER(Py_FrozenMain)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(Py_GenericAlias, Py_FrozenMain)
PyObject* (*__target__Py_GenericAlias)(PyObject*, PyObject*) = NULL;
PyAPI_FUNC(PyObject*) Py_GenericAlias(PyObject* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target__Py_GenericAlias == NULL) {
        __target__Py_GenericAlias = resolveAPI("Py_GenericAlias");
    }
    STATS_BEFORE(Py_GenericAlias)
    PyObject* result = (PyObject*) __target__Py_GenericAlias(a, b);
    STATS_AFTER(Py_GenericAlias)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(Py_GetArgcArgv, Py_GenericAlias)
void (*__target__Py_GetArgcArgv)(int*, wchar_t***) = NULL;
PyAPI_FUNC(void) Py_GetArgcArgv(int* a, wchar_t*** b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target__Py_GetArgcArgv == NULL) {
        __target__Py_GetArgcArgv = resolveAPI("Py_GetArgcArgv");
    }
    STATS_BEFORE(Py_GetArgcArgv)
    __target__Py_GetArgcArgv(a, b);
    STATS_AFTER(Py_GetArgcArgv)
    LOG_AFTER_VOID
}
STATS_CONTAINER(Py_GetBuildInfo, Py_GetArgcArgv)
const char* (*__target__Py_GetBuildInfo)() = NULL;
PyAPI_FUNC(const char*) Py_GetBuildInfo() {
    LOGS("");
    if (__target__Py_GetBuildInfo == NULL) {
        __target__Py_GetBuildInfo = resolveAPI("Py_GetBuildInfo");
    }
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
    if (__target__Py_GetCompiler == NULL) {
        __target__Py_GetCompiler = resolveAPI("Py_GetCompiler");
    }
    STATS_BEFORE(Py_GetCompiler)
    const char* result = (const char*) __target__Py_GetCompiler();
    STATS_AFTER(Py_GetCompiler)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(Py_GetCopyright, Py_GetCompiler)
const char* (*__target__Py_GetCopyright)() = NULL;
PyAPI_FUNC(const char*) Py_GetCopyright() {
    LOGS("");
    if (__target__Py_GetCopyright == NULL) {
        __target__Py_GetCopyright = resolveAPI("Py_GetCopyright");
    }
    STATS_BEFORE(Py_GetCopyright)
    const char* result = (const char*) __target__Py_GetCopyright();
    STATS_AFTER(Py_GetCopyright)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(Py_GetExecPrefix, Py_GetCopyright)
wchar_t* (*__target__Py_GetExecPrefix)() = NULL;
PyAPI_FUNC(wchar_t*) Py_GetExecPrefix() {
    LOGS("");
    if (__target__Py_GetExecPrefix == NULL) {
        __target__Py_GetExecPrefix = resolveAPI("Py_GetExecPrefix");
    }
    STATS_BEFORE(Py_GetExecPrefix)
    wchar_t* result = (wchar_t*) __target__Py_GetExecPrefix();
    STATS_AFTER(Py_GetExecPrefix)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(Py_GetPath, Py_GetExecPrefix)
wchar_t* (*__target__Py_GetPath)() = NULL;
PyAPI_FUNC(wchar_t*) Py_GetPath() {
    LOGS("");
    if (__target__Py_GetPath == NULL) {
        __target__Py_GetPath = resolveAPI("Py_GetPath");
    }
    STATS_BEFORE(Py_GetPath)
    wchar_t* result = (wchar_t*) __target__Py_GetPath();
    STATS_AFTER(Py_GetPath)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(Py_GetPlatform, Py_GetPath)
const char* (*__target__Py_GetPlatform)() = NULL;
PyAPI_FUNC(const char*) Py_GetPlatform() {
    LOGS("");
    if (__target__Py_GetPlatform == NULL) {
        __target__Py_GetPlatform = resolveAPI("Py_GetPlatform");
    }
    STATS_BEFORE(Py_GetPlatform)
    const char* result = (const char*) __target__Py_GetPlatform();
    STATS_AFTER(Py_GetPlatform)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(Py_GetPrefix, Py_GetPlatform)
wchar_t* (*__target__Py_GetPrefix)() = NULL;
PyAPI_FUNC(wchar_t*) Py_GetPrefix() {
    LOGS("");
    if (__target__Py_GetPrefix == NULL) {
        __target__Py_GetPrefix = resolveAPI("Py_GetPrefix");
    }
    STATS_BEFORE(Py_GetPrefix)
    wchar_t* result = (wchar_t*) __target__Py_GetPrefix();
    STATS_AFTER(Py_GetPrefix)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(Py_GetProgramFullPath, Py_GetPrefix)
wchar_t* (*__target__Py_GetProgramFullPath)() = NULL;
PyAPI_FUNC(wchar_t*) Py_GetProgramFullPath() {
    LOGS("");
    if (__target__Py_GetProgramFullPath == NULL) {
        __target__Py_GetProgramFullPath = resolveAPI("Py_GetProgramFullPath");
    }
    STATS_BEFORE(Py_GetProgramFullPath)
    wchar_t* result = (wchar_t*) __target__Py_GetProgramFullPath();
    STATS_AFTER(Py_GetProgramFullPath)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(Py_GetProgramName, Py_GetProgramFullPath)
wchar_t* (*__target__Py_GetProgramName)() = NULL;
PyAPI_FUNC(wchar_t*) Py_GetProgramName() {
    LOGS("");
    if (__target__Py_GetProgramName == NULL) {
        __target__Py_GetProgramName = resolveAPI("Py_GetProgramName");
    }
    STATS_BEFORE(Py_GetProgramName)
    wchar_t* result = (wchar_t*) __target__Py_GetProgramName();
    STATS_AFTER(Py_GetProgramName)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(Py_GetPythonHome, Py_GetProgramName)
wchar_t* (*__target__Py_GetPythonHome)() = NULL;
PyAPI_FUNC(wchar_t*) Py_GetPythonHome() {
    LOGS("");
    if (__target__Py_GetPythonHome == NULL) {
        __target__Py_GetPythonHome = resolveAPI("Py_GetPythonHome");
    }
    STATS_BEFORE(Py_GetPythonHome)
    wchar_t* result = (wchar_t*) __target__Py_GetPythonHome();
    STATS_AFTER(Py_GetPythonHome)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(Py_GetRecursionLimit, Py_GetPythonHome)
int (*__target__Py_GetRecursionLimit)() = NULL;
PyAPI_FUNC(int) Py_GetRecursionLimit() {
    LOGS("");
    if (__target__Py_GetRecursionLimit == NULL) {
        __target__Py_GetRecursionLimit = resolveAPI("Py_GetRecursionLimit");
    }
    STATS_BEFORE(Py_GetRecursionLimit)
    int result = (int) __target__Py_GetRecursionLimit();
    STATS_AFTER(Py_GetRecursionLimit)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(Py_GetVersion, Py_GetRecursionLimit)
const char* (*__target__Py_GetVersion)() = NULL;
PyAPI_FUNC(const char*) Py_GetVersion() {
    LOGS("");
    if (__target__Py_GetVersion == NULL) {
        __target__Py_GetVersion = resolveAPI("Py_GetVersion");
    }
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
    if (__target__Py_IncRef == NULL) {
        __target__Py_IncRef = resolveAPI("Py_IncRef");
    }
    STATS_BEFORE(Py_IncRef)
    __target__Py_IncRef(a);
    STATS_AFTER(Py_IncRef)
    LOG_AFTER_VOID
}
STATS_CONTAINER(Py_Initialize, Py_IncRef)
void (*__target__Py_Initialize)() = NULL;
PyAPI_FUNC(void) Py_Initialize() {
    LOGS("");
    if (__target__Py_Initialize == NULL) {
        __target__Py_Initialize = resolveAPI("Py_Initialize");
    }
    STATS_BEFORE(Py_Initialize)
    __target__Py_Initialize();
    STATS_AFTER(Py_Initialize)
    LOG_AFTER_VOID
}
STATS_CONTAINER(Py_InitializeEx, Py_Initialize)
void (*__target__Py_InitializeEx)(int) = NULL;
PyAPI_FUNC(void) Py_InitializeEx(int a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__Py_InitializeEx == NULL) {
        __target__Py_InitializeEx = resolveAPI("Py_InitializeEx");
    }
    STATS_BEFORE(Py_InitializeEx)
    __target__Py_InitializeEx(a);
    STATS_AFTER(Py_InitializeEx)
    LOG_AFTER_VOID
}
STATS_CONTAINER(Py_InitializeFromConfig, Py_InitializeEx)
PyStatus (*__target__Py_InitializeFromConfig)(const PyConfig*) = NULL;
PyAPI_FUNC(PyStatus) Py_InitializeFromConfig(const PyConfig* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__Py_InitializeFromConfig == NULL) {
        __target__Py_InitializeFromConfig = resolveAPI("Py_InitializeFromConfig");
    }
    STATS_BEFORE(Py_InitializeFromConfig)
    PyStatus result = (PyStatus) __target__Py_InitializeFromConfig(a);
    STATS_AFTER(Py_InitializeFromConfig)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(Py_Is, Py_InitializeFromConfig)
int (*__target__Py_Is)(PyObject*, PyObject*) = NULL;
PyAPI_FUNC(int) Py_Is(PyObject* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target__Py_Is == NULL) {
        __target__Py_Is = resolveAPI("Py_Is");
    }
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
    if (__target__Py_IsFalse == NULL) {
        __target__Py_IsFalse = resolveAPI("Py_IsFalse");
    }
    STATS_BEFORE(Py_IsFalse)
    int result = (int) __target__Py_IsFalse(a);
    STATS_AFTER(Py_IsFalse)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(Py_IsInitialized, Py_IsFalse)
int (*__target__Py_IsInitialized)() = NULL;
PyAPI_FUNC(int) Py_IsInitialized() {
    LOGS("");
    if (__target__Py_IsInitialized == NULL) {
        __target__Py_IsInitialized = resolveAPI("Py_IsInitialized");
    }
    STATS_BEFORE(Py_IsInitialized)
    int result = (int) __target__Py_IsInitialized();
    STATS_AFTER(Py_IsInitialized)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(Py_IsNone, Py_IsInitialized)
int (*__target__Py_IsNone)(PyObject*) = NULL;
PyAPI_FUNC(int) Py_IsNone(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__Py_IsNone == NULL) {
        __target__Py_IsNone = resolveAPI("Py_IsNone");
    }
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
    if (__target__Py_IsTrue == NULL) {
        __target__Py_IsTrue = resolveAPI("Py_IsTrue");
    }
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
    if (__target__Py_LeaveRecursiveCall == NULL) {
        __target__Py_LeaveRecursiveCall = resolveAPI("Py_LeaveRecursiveCall");
    }
    STATS_BEFORE(Py_LeaveRecursiveCall)
    __target__Py_LeaveRecursiveCall();
    STATS_AFTER(Py_LeaveRecursiveCall)
    LOG_AFTER_VOID
}
STATS_CONTAINER(Py_Main, Py_LeaveRecursiveCall)
int (*__target__Py_Main)(int, wchar_t**) = NULL;
PyAPI_FUNC(int) Py_Main(int a, wchar_t** b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target__Py_Main == NULL) {
        __target__Py_Main = resolveAPI("Py_Main");
    }
    STATS_BEFORE(Py_Main)
    int result = (int) __target__Py_Main(a, b);
    STATS_AFTER(Py_Main)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(Py_MakePendingCalls, Py_Main)
int (*__target__Py_MakePendingCalls)() = NULL;
PyAPI_FUNC(int) Py_MakePendingCalls() {
    LOGS("");
    if (__target__Py_MakePendingCalls == NULL) {
        __target__Py_MakePendingCalls = resolveAPI("Py_MakePendingCalls");
    }
    STATS_BEFORE(Py_MakePendingCalls)
    int result = (int) __target__Py_MakePendingCalls();
    STATS_AFTER(Py_MakePendingCalls)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(Py_NewInterpreter, Py_MakePendingCalls)
PyThreadState* (*__target__Py_NewInterpreter)() = NULL;
PyAPI_FUNC(PyThreadState*) Py_NewInterpreter() {
    LOGS("");
    if (__target__Py_NewInterpreter == NULL) {
        __target__Py_NewInterpreter = resolveAPI("Py_NewInterpreter");
    }
    STATS_BEFORE(Py_NewInterpreter)
    PyThreadState* result = (PyThreadState*) __target__Py_NewInterpreter();
    STATS_AFTER(Py_NewInterpreter)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(Py_NewRef, Py_NewInterpreter)
PyObject* (*__target__Py_NewRef)(PyObject*) = NULL;
PyAPI_FUNC(PyObject*) Py_NewRef(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__Py_NewRef == NULL) {
        __target__Py_NewRef = resolveAPI("Py_NewRef");
    }
    STATS_BEFORE(Py_NewRef)
    PyObject* result = (PyObject*) __target__Py_NewRef(a);
    STATS_AFTER(Py_NewRef)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(Py_PreInitialize, Py_NewRef)
PyStatus (*__target__Py_PreInitialize)(const PyPreConfig*) = NULL;
PyAPI_FUNC(PyStatus) Py_PreInitialize(const PyPreConfig* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__Py_PreInitialize == NULL) {
        __target__Py_PreInitialize = resolveAPI("Py_PreInitialize");
    }
    STATS_BEFORE(Py_PreInitialize)
    PyStatus result = (PyStatus) __target__Py_PreInitialize(a);
    STATS_AFTER(Py_PreInitialize)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(Py_PreInitializeFromArgs, Py_PreInitialize)
PyStatus (*__target__Py_PreInitializeFromArgs)(const PyPreConfig*, Py_ssize_t, wchar_t**) = NULL;
PyAPI_FUNC(PyStatus) Py_PreInitializeFromArgs(const PyPreConfig* a, Py_ssize_t b, wchar_t** c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    if (__target__Py_PreInitializeFromArgs == NULL) {
        __target__Py_PreInitializeFromArgs = resolveAPI("Py_PreInitializeFromArgs");
    }
    STATS_BEFORE(Py_PreInitializeFromArgs)
    PyStatus result = (PyStatus) __target__Py_PreInitializeFromArgs(a, b, c);
    STATS_AFTER(Py_PreInitializeFromArgs)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(Py_PreInitializeFromBytesArgs, Py_PreInitializeFromArgs)
PyStatus (*__target__Py_PreInitializeFromBytesArgs)(const PyPreConfig*, Py_ssize_t, char**) = NULL;
PyAPI_FUNC(PyStatus) Py_PreInitializeFromBytesArgs(const PyPreConfig* a, Py_ssize_t b, char** c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    if (__target__Py_PreInitializeFromBytesArgs == NULL) {
        __target__Py_PreInitializeFromBytesArgs = resolveAPI("Py_PreInitializeFromBytesArgs");
    }
    STATS_BEFORE(Py_PreInitializeFromBytesArgs)
    PyStatus result = (PyStatus) __target__Py_PreInitializeFromBytesArgs(a, b, c);
    STATS_AFTER(Py_PreInitializeFromBytesArgs)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(Py_ReprEnter, Py_PreInitializeFromBytesArgs)
int (*__target__Py_ReprEnter)(PyObject*) = NULL;
PyAPI_FUNC(int) Py_ReprEnter(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__Py_ReprEnter == NULL) {
        __target__Py_ReprEnter = resolveAPI("Py_ReprEnter");
    }
    STATS_BEFORE(Py_ReprEnter)
    int result = (int) __target__Py_ReprEnter(a);
    STATS_AFTER(Py_ReprEnter)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(Py_ReprLeave, Py_ReprEnter)
void (*__target__Py_ReprLeave)(PyObject*) = NULL;
PyAPI_FUNC(void) Py_ReprLeave(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__Py_ReprLeave == NULL) {
        __target__Py_ReprLeave = resolveAPI("Py_ReprLeave");
    }
    STATS_BEFORE(Py_ReprLeave)
    __target__Py_ReprLeave(a);
    STATS_AFTER(Py_ReprLeave)
    LOG_AFTER_VOID
}
STATS_CONTAINER(Py_RunMain, Py_ReprLeave)
int (*__target__Py_RunMain)() = NULL;
PyAPI_FUNC(int) Py_RunMain() {
    LOGS("");
    if (__target__Py_RunMain == NULL) {
        __target__Py_RunMain = resolveAPI("Py_RunMain");
    }
    STATS_BEFORE(Py_RunMain)
    int result = (int) __target__Py_RunMain();
    STATS_AFTER(Py_RunMain)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(Py_SetPath, Py_RunMain)
void (*__target__Py_SetPath)(const wchar_t*) = NULL;
PyAPI_FUNC(void) Py_SetPath(const wchar_t* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__Py_SetPath == NULL) {
        __target__Py_SetPath = resolveAPI("Py_SetPath");
    }
    STATS_BEFORE(Py_SetPath)
    __target__Py_SetPath(a);
    STATS_AFTER(Py_SetPath)
    LOG_AFTER_VOID
}
STATS_CONTAINER(Py_SetProgramName, Py_SetPath)
void (*__target__Py_SetProgramName)(const wchar_t*) = NULL;
PyAPI_FUNC(void) Py_SetProgramName(const wchar_t* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__Py_SetProgramName == NULL) {
        __target__Py_SetProgramName = resolveAPI("Py_SetProgramName");
    }
    STATS_BEFORE(Py_SetProgramName)
    __target__Py_SetProgramName(a);
    STATS_AFTER(Py_SetProgramName)
    LOG_AFTER_VOID
}
STATS_CONTAINER(Py_SetPythonHome, Py_SetProgramName)
void (*__target__Py_SetPythonHome)(const wchar_t*) = NULL;
PyAPI_FUNC(void) Py_SetPythonHome(const wchar_t* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__Py_SetPythonHome == NULL) {
        __target__Py_SetPythonHome = resolveAPI("Py_SetPythonHome");
    }
    STATS_BEFORE(Py_SetPythonHome)
    __target__Py_SetPythonHome(a);
    STATS_AFTER(Py_SetPythonHome)
    LOG_AFTER_VOID
}
STATS_CONTAINER(Py_SetRecursionLimit, Py_SetPythonHome)
void (*__target__Py_SetRecursionLimit)(int) = NULL;
PyAPI_FUNC(void) Py_SetRecursionLimit(int a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__Py_SetRecursionLimit == NULL) {
        __target__Py_SetRecursionLimit = resolveAPI("Py_SetRecursionLimit");
    }
    STATS_BEFORE(Py_SetRecursionLimit)
    __target__Py_SetRecursionLimit(a);
    STATS_AFTER(Py_SetRecursionLimit)
    LOG_AFTER_VOID
}
STATS_CONTAINER(Py_SetStandardStreamEncoding, Py_SetRecursionLimit)
int (*__target__Py_SetStandardStreamEncoding)(const char*, const char*) = NULL;
PyAPI_FUNC(int) Py_SetStandardStreamEncoding(const char* a, const char* b) {
    LOG("'%s'(0x%lx) '%s'(0x%lx)", a?a:"<null>", (unsigned long) a, b?b:"<null>", (unsigned long) b);
    if (__target__Py_SetStandardStreamEncoding == NULL) {
        __target__Py_SetStandardStreamEncoding = resolveAPI("Py_SetStandardStreamEncoding");
    }
    STATS_BEFORE(Py_SetStandardStreamEncoding)
    int result = (int) __target__Py_SetStandardStreamEncoding(a, b);
    STATS_AFTER(Py_SetStandardStreamEncoding)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(Py_UNICODE_strcat, Py_SetStandardStreamEncoding)
Py_UNICODE* (*__target__Py_UNICODE_strcat)(Py_UNICODE*, const Py_UNICODE*) = NULL;
PyAPI_FUNC(Py_UNICODE*) Py_UNICODE_strcat(Py_UNICODE* a, const Py_UNICODE* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target__Py_UNICODE_strcat == NULL) {
        __target__Py_UNICODE_strcat = resolveAPI("Py_UNICODE_strcat");
    }
    STATS_BEFORE(Py_UNICODE_strcat)
    Py_UNICODE* result = (Py_UNICODE*) __target__Py_UNICODE_strcat(a, b);
    STATS_AFTER(Py_UNICODE_strcat)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(Py_UNICODE_strchr, Py_UNICODE_strcat)
Py_UNICODE* (*__target__Py_UNICODE_strchr)(const Py_UNICODE*, Py_UNICODE) = NULL;
PyAPI_FUNC(Py_UNICODE*) Py_UNICODE_strchr(const Py_UNICODE* a, Py_UNICODE b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target__Py_UNICODE_strchr == NULL) {
        __target__Py_UNICODE_strchr = resolveAPI("Py_UNICODE_strchr");
    }
    STATS_BEFORE(Py_UNICODE_strchr)
    Py_UNICODE* result = (Py_UNICODE*) __target__Py_UNICODE_strchr(a, b);
    STATS_AFTER(Py_UNICODE_strchr)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(Py_UNICODE_strcmp, Py_UNICODE_strchr)
int (*__target__Py_UNICODE_strcmp)(const Py_UNICODE*, const Py_UNICODE*) = NULL;
PyAPI_FUNC(int) Py_UNICODE_strcmp(const Py_UNICODE* a, const Py_UNICODE* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target__Py_UNICODE_strcmp == NULL) {
        __target__Py_UNICODE_strcmp = resolveAPI("Py_UNICODE_strcmp");
    }
    STATS_BEFORE(Py_UNICODE_strcmp)
    int result = (int) __target__Py_UNICODE_strcmp(a, b);
    STATS_AFTER(Py_UNICODE_strcmp)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(Py_UNICODE_strcpy, Py_UNICODE_strcmp)
Py_UNICODE* (*__target__Py_UNICODE_strcpy)(Py_UNICODE*, const Py_UNICODE*) = NULL;
PyAPI_FUNC(Py_UNICODE*) Py_UNICODE_strcpy(Py_UNICODE* a, const Py_UNICODE* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target__Py_UNICODE_strcpy == NULL) {
        __target__Py_UNICODE_strcpy = resolveAPI("Py_UNICODE_strcpy");
    }
    STATS_BEFORE(Py_UNICODE_strcpy)
    Py_UNICODE* result = (Py_UNICODE*) __target__Py_UNICODE_strcpy(a, b);
    STATS_AFTER(Py_UNICODE_strcpy)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(Py_UNICODE_strlen, Py_UNICODE_strcpy)
size_t (*__target__Py_UNICODE_strlen)(const Py_UNICODE*) = NULL;
PyAPI_FUNC(size_t) Py_UNICODE_strlen(const Py_UNICODE* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__Py_UNICODE_strlen == NULL) {
        __target__Py_UNICODE_strlen = resolveAPI("Py_UNICODE_strlen");
    }
    STATS_BEFORE(Py_UNICODE_strlen)
    size_t result = (size_t) __target__Py_UNICODE_strlen(a);
    STATS_AFTER(Py_UNICODE_strlen)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(Py_UNICODE_strncmp, Py_UNICODE_strlen)
int (*__target__Py_UNICODE_strncmp)(const Py_UNICODE*, const Py_UNICODE*, size_t) = NULL;
PyAPI_FUNC(int) Py_UNICODE_strncmp(const Py_UNICODE* a, const Py_UNICODE* b, size_t c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    if (__target__Py_UNICODE_strncmp == NULL) {
        __target__Py_UNICODE_strncmp = resolveAPI("Py_UNICODE_strncmp");
    }
    STATS_BEFORE(Py_UNICODE_strncmp)
    int result = (int) __target__Py_UNICODE_strncmp(a, b, c);
    STATS_AFTER(Py_UNICODE_strncmp)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(Py_UNICODE_strncpy, Py_UNICODE_strncmp)
Py_UNICODE* (*__target__Py_UNICODE_strncpy)(Py_UNICODE*, const Py_UNICODE*, size_t) = NULL;
PyAPI_FUNC(Py_UNICODE*) Py_UNICODE_strncpy(Py_UNICODE* a, const Py_UNICODE* b, size_t c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    if (__target__Py_UNICODE_strncpy == NULL) {
        __target__Py_UNICODE_strncpy = resolveAPI("Py_UNICODE_strncpy");
    }
    STATS_BEFORE(Py_UNICODE_strncpy)
    Py_UNICODE* result = (Py_UNICODE*) __target__Py_UNICODE_strncpy(a, b, c);
    STATS_AFTER(Py_UNICODE_strncpy)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(Py_UNICODE_strrchr, Py_UNICODE_strncpy)
Py_UNICODE* (*__target__Py_UNICODE_strrchr)(const Py_UNICODE*, Py_UNICODE) = NULL;
PyAPI_FUNC(Py_UNICODE*) Py_UNICODE_strrchr(const Py_UNICODE* a, Py_UNICODE b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target__Py_UNICODE_strrchr == NULL) {
        __target__Py_UNICODE_strrchr = resolveAPI("Py_UNICODE_strrchr");
    }
    STATS_BEFORE(Py_UNICODE_strrchr)
    Py_UNICODE* result = (Py_UNICODE*) __target__Py_UNICODE_strrchr(a, b);
    STATS_AFTER(Py_UNICODE_strrchr)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(Py_UniversalNewlineFgets, Py_UNICODE_strrchr)
char* (*__target__Py_UniversalNewlineFgets)(char*, int, FILE*, PyObject*) = NULL;
PyAPI_FUNC(char*) Py_UniversalNewlineFgets(char* a, int b, FILE* c, PyObject* d) {
    LOG("0x%lx 0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c, (unsigned long) d);
    if (__target__Py_UniversalNewlineFgets == NULL) {
        __target__Py_UniversalNewlineFgets = resolveAPI("Py_UniversalNewlineFgets");
    }
    STATS_BEFORE(Py_UniversalNewlineFgets)
    char* result = (char*) __target__Py_UniversalNewlineFgets(a, b, c, d);
    STATS_AFTER(Py_UniversalNewlineFgets)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(Py_VaBuildValue, Py_UniversalNewlineFgets)
PyObject* (*__target__Py_VaBuildValue)(const char*, va_list) = NULL;
PyAPI_FUNC(PyObject*) Py_VaBuildValue(const char* a, va_list b) {
    LOG("'%s'(0x%lx) 0x%lx", a?a:"<null>", (unsigned long) a, (unsigned long) b);
    if (__target__Py_VaBuildValue == NULL) {
        __target__Py_VaBuildValue = resolveAPI("Py_VaBuildValue");
    }
    STATS_BEFORE(Py_VaBuildValue)
    PyObject* result = (PyObject*) __target__Py_VaBuildValue(a, b);
    STATS_AFTER(Py_VaBuildValue)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(Py_XNewRef, Py_VaBuildValue)
PyObject* (*__target__Py_XNewRef)(PyObject*) = NULL;
PyAPI_FUNC(PyObject*) Py_XNewRef(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target__Py_XNewRef == NULL) {
        __target__Py_XNewRef = resolveAPI("Py_XNewRef");
    }
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
    if (__target___PyASCIIObject_LENGTH == NULL) {
        __target___PyASCIIObject_LENGTH = resolveAPI("_PyASCIIObject_LENGTH");
    }
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
    if (__target___PyASCIIObject_STATE_ASCII == NULL) {
        __target___PyASCIIObject_STATE_ASCII = resolveAPI("_PyASCIIObject_STATE_ASCII");
    }
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
    if (__target___PyASCIIObject_STATE_COMPACT == NULL) {
        __target___PyASCIIObject_STATE_COMPACT = resolveAPI("_PyASCIIObject_STATE_COMPACT");
    }
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
    if (__target___PyASCIIObject_STATE_KIND == NULL) {
        __target___PyASCIIObject_STATE_KIND = resolveAPI("_PyASCIIObject_STATE_KIND");
    }
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
    if (__target___PyASCIIObject_STATE_READY == NULL) {
        __target___PyASCIIObject_STATE_READY = resolveAPI("_PyASCIIObject_STATE_READY");
    }
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
    if (__target___PyASCIIObject_WSTR == NULL) {
        __target___PyASCIIObject_WSTR = resolveAPI("_PyASCIIObject_WSTR");
    }
    STATS_BEFORE(_PyASCIIObject_WSTR)
    wchar_t* result = (wchar_t*) __target___PyASCIIObject_WSTR(a);
    STATS_AFTER(_PyASCIIObject_WSTR)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyArg_BadArgument, _PyASCIIObject_WSTR)
void (*__target___PyArg_BadArgument)(const char*, const char*, const char*, PyObject*) = NULL;
PyAPI_FUNC(void) _PyArg_BadArgument(const char* a, const char* b, const char* c, PyObject* d) {
    LOG("'%s'(0x%lx) '%s'(0x%lx) '%s'(0x%lx) 0x%lx", a?a:"<null>", (unsigned long) a, b?b:"<null>", (unsigned long) b, c?c:"<null>", (unsigned long) c, (unsigned long) d);
    if (__target___PyArg_BadArgument == NULL) {
        __target___PyArg_BadArgument = resolveAPI("_PyArg_BadArgument");
    }
    STATS_BEFORE(_PyArg_BadArgument)
    __target___PyArg_BadArgument(a, b, c, d);
    STATS_AFTER(_PyArg_BadArgument)
    LOG_AFTER_VOID
}
STATS_CONTAINER(_PyArg_CheckPositional, _PyArg_BadArgument)
int (*__target___PyArg_CheckPositional)(const char*, Py_ssize_t, Py_ssize_t, Py_ssize_t) = NULL;
PyAPI_FUNC(int) _PyArg_CheckPositional(const char* a, Py_ssize_t b, Py_ssize_t c, Py_ssize_t d) {
    LOG("'%s'(0x%lx) 0x%lx 0x%lx 0x%lx", a?a:"<null>", (unsigned long) a, (unsigned long) b, (unsigned long) c, (unsigned long) d);
    if (__target___PyArg_CheckPositional == NULL) {
        __target___PyArg_CheckPositional = resolveAPI("_PyArg_CheckPositional");
    }
    STATS_BEFORE(_PyArg_CheckPositional)
    int result = (int) __target___PyArg_CheckPositional(a, b, c, d);
    STATS_AFTER(_PyArg_CheckPositional)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyArg_Fini, _PyArg_CheckPositional)
void (*__target___PyArg_Fini)() = NULL;
PyAPI_FUNC(void) _PyArg_Fini() {
    LOGS("");
    if (__target___PyArg_Fini == NULL) {
        __target___PyArg_Fini = resolveAPI("_PyArg_Fini");
    }
    STATS_BEFORE(_PyArg_Fini)
    __target___PyArg_Fini();
    STATS_AFTER(_PyArg_Fini)
    LOG_AFTER_VOID
}
STATS_CONTAINER(_PyArg_NoKeywords, _PyArg_Fini)
int (*__target___PyArg_NoKeywords)(const char*, PyObject*) = NULL;
PyAPI_FUNC(int) _PyArg_NoKeywords(const char* a, PyObject* b) {
    LOG("'%s'(0x%lx) 0x%lx", a?a:"<null>", (unsigned long) a, (unsigned long) b);
    if (__target___PyArg_NoKeywords == NULL) {
        __target___PyArg_NoKeywords = resolveAPI("_PyArg_NoKeywords");
    }
    STATS_BEFORE(_PyArg_NoKeywords)
    int result = (int) __target___PyArg_NoKeywords(a, b);
    STATS_AFTER(_PyArg_NoKeywords)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyArg_NoKwnames, _PyArg_NoKeywords)
int (*__target___PyArg_NoKwnames)(const char*, PyObject*) = NULL;
PyAPI_FUNC(int) _PyArg_NoKwnames(const char* a, PyObject* b) {
    LOG("'%s'(0x%lx) 0x%lx", a?a:"<null>", (unsigned long) a, (unsigned long) b);
    if (__target___PyArg_NoKwnames == NULL) {
        __target___PyArg_NoKwnames = resolveAPI("_PyArg_NoKwnames");
    }
    STATS_BEFORE(_PyArg_NoKwnames)
    int result = (int) __target___PyArg_NoKwnames(a, b);
    STATS_AFTER(_PyArg_NoKwnames)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyArg_NoPositional, _PyArg_NoKwnames)
int (*__target___PyArg_NoPositional)(const char*, PyObject*) = NULL;
PyAPI_FUNC(int) _PyArg_NoPositional(const char* a, PyObject* b) {
    LOG("'%s'(0x%lx) 0x%lx", a?a:"<null>", (unsigned long) a, (unsigned long) b);
    if (__target___PyArg_NoPositional == NULL) {
        __target___PyArg_NoPositional = resolveAPI("_PyArg_NoPositional");
    }
    STATS_BEFORE(_PyArg_NoPositional)
    int result = (int) __target___PyArg_NoPositional(a, b);
    STATS_AFTER(_PyArg_NoPositional)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyArg_UnpackKeywords, _PyArg_NoPositional)
PyObject*const* (*__target___PyArg_UnpackKeywords)(PyObject*const*, Py_ssize_t, PyObject*, PyObject*, struct _PyArg_Parser*, int, int, int, PyObject**) = NULL;
PyAPI_FUNC(PyObject*const*) _PyArg_UnpackKeywords(PyObject*const* a, Py_ssize_t b, PyObject* c, PyObject* d, struct _PyArg_Parser* e, int f, int g, int h, PyObject** i) {
    LOG("0x%lx 0x%lx 0x%lx 0x%lx 0x%lx 0x%lx 0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c, (unsigned long) d, (unsigned long) e, (unsigned long) f, (unsigned long) g, (unsigned long) h, (unsigned long) i);
    if (__target___PyArg_UnpackKeywords == NULL) {
        __target___PyArg_UnpackKeywords = resolveAPI("_PyArg_UnpackKeywords");
    }
    STATS_BEFORE(_PyArg_UnpackKeywords)
    PyObject*const* result = (PyObject*const*) __target___PyArg_UnpackKeywords(a, b, c, d, e, f, g, h, i);
    STATS_AFTER(_PyArg_UnpackKeywords)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyArg_VaParseTupleAndKeywordsFast, _PyArg_UnpackKeywords)
int (*__target___PyArg_VaParseTupleAndKeywordsFast)(PyObject*, PyObject*, struct _PyArg_Parser*, va_list) = NULL;
PyAPI_FUNC(int) _PyArg_VaParseTupleAndKeywordsFast(PyObject* a, PyObject* b, struct _PyArg_Parser* c, va_list d) {
    LOG("0x%lx 0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c, (unsigned long) d);
    if (__target___PyArg_VaParseTupleAndKeywordsFast == NULL) {
        __target___PyArg_VaParseTupleAndKeywordsFast = resolveAPI("_PyArg_VaParseTupleAndKeywordsFast");
    }
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
    if (__target___PyArg_VaParseTupleAndKeywordsFast_SizeT == NULL) {
        __target___PyArg_VaParseTupleAndKeywordsFast_SizeT = resolveAPI("_PyArg_VaParseTupleAndKeywordsFast_SizeT");
    }
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
    if (__target___PyArg_VaParseTupleAndKeywords_SizeT == NULL) {
        __target___PyArg_VaParseTupleAndKeywords_SizeT = resolveAPI("_PyArg_VaParseTupleAndKeywords_SizeT");
    }
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
    if (__target___PyArg_VaParse_SizeT == NULL) {
        __target___PyArg_VaParse_SizeT = resolveAPI("_PyArg_VaParse_SizeT");
    }
    STATS_BEFORE(_PyArg_VaParse_SizeT)
    int result = (int) __target___PyArg_VaParse_SizeT(a, b, c);
    STATS_AFTER(_PyArg_VaParse_SizeT)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyAsyncGenValueWrapperNew, _PyArg_VaParse_SizeT)
PyObject* (*__target___PyAsyncGenValueWrapperNew)(PyObject*) = NULL;
PyAPI_FUNC(PyObject*) _PyAsyncGenValueWrapperNew(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target___PyAsyncGenValueWrapperNew == NULL) {
        __target___PyAsyncGenValueWrapperNew = resolveAPI("_PyAsyncGenValueWrapperNew");
    }
    STATS_BEFORE(_PyAsyncGenValueWrapperNew)
    PyObject* result = (PyObject*) __target___PyAsyncGenValueWrapperNew(a);
    STATS_AFTER(_PyAsyncGenValueWrapperNew)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyByteArray_Start, _PyAsyncGenValueWrapperNew)
char* (*__target___PyByteArray_Start)(PyObject*) = NULL;
PyAPI_FUNC(char*) _PyByteArray_Start(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target___PyByteArray_Start == NULL) {
        __target___PyByteArray_Start = resolveAPI("_PyByteArray_Start");
    }
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
    if (__target___PyBytesWriter_Alloc == NULL) {
        __target___PyBytesWriter_Alloc = resolveAPI("_PyBytesWriter_Alloc");
    }
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
    if (__target___PyBytesWriter_Dealloc == NULL) {
        __target___PyBytesWriter_Dealloc = resolveAPI("_PyBytesWriter_Dealloc");
    }
    STATS_BEFORE(_PyBytesWriter_Dealloc)
    __target___PyBytesWriter_Dealloc(a);
    STATS_AFTER(_PyBytesWriter_Dealloc)
    LOG_AFTER_VOID
}
STATS_CONTAINER(_PyBytesWriter_Finish, _PyBytesWriter_Dealloc)
PyObject* (*__target___PyBytesWriter_Finish)(_PyBytesWriter*, void*) = NULL;
PyAPI_FUNC(PyObject*) _PyBytesWriter_Finish(_PyBytesWriter* a, void* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target___PyBytesWriter_Finish == NULL) {
        __target___PyBytesWriter_Finish = resolveAPI("_PyBytesWriter_Finish");
    }
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
    if (__target___PyBytesWriter_Init == NULL) {
        __target___PyBytesWriter_Init = resolveAPI("_PyBytesWriter_Init");
    }
    STATS_BEFORE(_PyBytesWriter_Init)
    __target___PyBytesWriter_Init(a);
    STATS_AFTER(_PyBytesWriter_Init)
    LOG_AFTER_VOID
}
STATS_CONTAINER(_PyBytesWriter_Prepare, _PyBytesWriter_Init)
void* (*__target___PyBytesWriter_Prepare)(_PyBytesWriter*, void*, Py_ssize_t) = NULL;
PyAPI_FUNC(void*) _PyBytesWriter_Prepare(_PyBytesWriter* a, void* b, Py_ssize_t c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    if (__target___PyBytesWriter_Prepare == NULL) {
        __target___PyBytesWriter_Prepare = resolveAPI("_PyBytesWriter_Prepare");
    }
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
    if (__target___PyBytesWriter_Resize == NULL) {
        __target___PyBytesWriter_Resize = resolveAPI("_PyBytesWriter_Resize");
    }
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
    if (__target___PyBytesWriter_WriteBytes == NULL) {
        __target___PyBytesWriter_WriteBytes = resolveAPI("_PyBytesWriter_WriteBytes");
    }
    STATS_BEFORE(_PyBytesWriter_WriteBytes)
    void* result = (void*) __target___PyBytesWriter_WriteBytes(a, b, c, d);
    STATS_AFTER(_PyBytesWriter_WriteBytes)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyBytes_DecodeEscape, _PyBytesWriter_WriteBytes)
PyObject* (*__target___PyBytes_DecodeEscape)(const char*, Py_ssize_t, const char*, const char**) = NULL;
PyAPI_FUNC(PyObject*) _PyBytes_DecodeEscape(const char* a, Py_ssize_t b, const char* c, const char** d) {
    LOG("'%s'(0x%lx) 0x%lx '%s'(0x%lx) 0x%lx", a?a:"<null>", (unsigned long) a, (unsigned long) b, c?c:"<null>", (unsigned long) c, (unsigned long) d);
    if (__target___PyBytes_DecodeEscape == NULL) {
        __target___PyBytes_DecodeEscape = resolveAPI("_PyBytes_DecodeEscape");
    }
    STATS_BEFORE(_PyBytes_DecodeEscape)
    PyObject* result = (PyObject*) __target___PyBytes_DecodeEscape(a, b, c, d);
    STATS_AFTER(_PyBytes_DecodeEscape)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyBytes_FormatEx, _PyBytes_DecodeEscape)
PyObject* (*__target___PyBytes_FormatEx)(const char*, Py_ssize_t, PyObject*, int) = NULL;
PyAPI_FUNC(PyObject*) _PyBytes_FormatEx(const char* a, Py_ssize_t b, PyObject* c, int d) {
    LOG("'%s'(0x%lx) 0x%lx 0x%lx 0x%lx", a?a:"<null>", (unsigned long) a, (unsigned long) b, (unsigned long) c, (unsigned long) d);
    if (__target___PyBytes_FormatEx == NULL) {
        __target___PyBytes_FormatEx = resolveAPI("_PyBytes_FormatEx");
    }
    STATS_BEFORE(_PyBytes_FormatEx)
    PyObject* result = (PyObject*) __target___PyBytes_FormatEx(a, b, c, d);
    STATS_AFTER(_PyBytes_FormatEx)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyBytes_FromHex, _PyBytes_FormatEx)
PyObject* (*__target___PyBytes_FromHex)(PyObject*, int) = NULL;
PyAPI_FUNC(PyObject*) _PyBytes_FromHex(PyObject* a, int b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target___PyBytes_FromHex == NULL) {
        __target___PyBytes_FromHex = resolveAPI("_PyBytes_FromHex");
    }
    STATS_BEFORE(_PyBytes_FromHex)
    PyObject* result = (PyObject*) __target___PyBytes_FromHex(a, b);
    STATS_AFTER(_PyBytes_FromHex)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyBytes_Join, _PyBytes_FromHex)
PyObject* (*__target___PyBytes_Join)(PyObject*, PyObject*) = NULL;
PyAPI_FUNC(PyObject*) _PyBytes_Join(PyObject* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target___PyBytes_Join == NULL) {
        __target___PyBytes_Join = resolveAPI("_PyBytes_Join");
    }
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
    if (__target___PyBytes_Resize == NULL) {
        __target___PyBytes_Resize = resolveAPI("_PyBytes_Resize");
    }
    STATS_BEFORE(_PyBytes_Resize)
    int result = (int) __target___PyBytes_Resize(a, b);
    STATS_AFTER(_PyBytes_Resize)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyCFunction_DebugMallocStats, _PyBytes_Resize)
void (*__target___PyCFunction_DebugMallocStats)(FILE*) = NULL;
PyAPI_FUNC(void) _PyCFunction_DebugMallocStats(FILE* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target___PyCFunction_DebugMallocStats == NULL) {
        __target___PyCFunction_DebugMallocStats = resolveAPI("_PyCFunction_DebugMallocStats");
    }
    STATS_BEFORE(_PyCFunction_DebugMallocStats)
    __target___PyCFunction_DebugMallocStats(a);
    STATS_AFTER(_PyCFunction_DebugMallocStats)
    LOG_AFTER_VOID
}
STATS_CONTAINER(_PyCFunction_FastCallDict, _PyCFunction_DebugMallocStats)
PyObject* (*__target___PyCFunction_FastCallDict)(PyObject*, PyObject*const*, Py_ssize_t, PyObject*) = NULL;
PyAPI_FUNC(PyObject*) _PyCFunction_FastCallDict(PyObject* a, PyObject*const* b, Py_ssize_t c, PyObject* d) {
    LOG("0x%lx 0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c, (unsigned long) d);
    if (__target___PyCFunction_FastCallDict == NULL) {
        __target___PyCFunction_FastCallDict = resolveAPI("_PyCFunction_FastCallDict");
    }
    STATS_BEFORE(_PyCFunction_FastCallDict)
    PyObject* result = (PyObject*) __target___PyCFunction_FastCallDict(a, b, c, d);
    STATS_AFTER(_PyCFunction_FastCallDict)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyCode_CheckLineNumber, _PyCFunction_FastCallDict)
int (*__target___PyCode_CheckLineNumber)(int, PyCodeAddressRange*) = NULL;
PyAPI_FUNC(int) _PyCode_CheckLineNumber(int a, PyCodeAddressRange* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target___PyCode_CheckLineNumber == NULL) {
        __target___PyCode_CheckLineNumber = resolveAPI("_PyCode_CheckLineNumber");
    }
    STATS_BEFORE(_PyCode_CheckLineNumber)
    int result = (int) __target___PyCode_CheckLineNumber(a, b);
    STATS_AFTER(_PyCode_CheckLineNumber)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyCode_ConstantKey, _PyCode_CheckLineNumber)
PyObject* (*__target___PyCode_ConstantKey)(PyObject*) = NULL;
PyAPI_FUNC(PyObject*) _PyCode_ConstantKey(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target___PyCode_ConstantKey == NULL) {
        __target___PyCode_ConstantKey = resolveAPI("_PyCode_ConstantKey");
    }
    STATS_BEFORE(_PyCode_ConstantKey)
    PyObject* result = (PyObject*) __target___PyCode_ConstantKey(a);
    STATS_AFTER(_PyCode_ConstantKey)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyCode_GetExtra, _PyCode_ConstantKey)
int (*__target___PyCode_GetExtra)(PyObject*, Py_ssize_t, void**) = NULL;
PyAPI_FUNC(int) _PyCode_GetExtra(PyObject* a, Py_ssize_t b, void** c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    if (__target___PyCode_GetExtra == NULL) {
        __target___PyCode_GetExtra = resolveAPI("_PyCode_GetExtra");
    }
    STATS_BEFORE(_PyCode_GetExtra)
    int result = (int) __target___PyCode_GetExtra(a, b, c);
    STATS_AFTER(_PyCode_GetExtra)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyCode_InitAddressRange, _PyCode_GetExtra)
int (*__target___PyCode_InitAddressRange)(PyCodeObject*, PyCodeAddressRange*) = NULL;
PyAPI_FUNC(int) _PyCode_InitAddressRange(PyCodeObject* a, PyCodeAddressRange* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target___PyCode_InitAddressRange == NULL) {
        __target___PyCode_InitAddressRange = resolveAPI("_PyCode_InitAddressRange");
    }
    STATS_BEFORE(_PyCode_InitAddressRange)
    int result = (int) __target___PyCode_InitAddressRange(a, b);
    STATS_AFTER(_PyCode_InitAddressRange)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyCode_SetExtra, _PyCode_InitAddressRange)
int (*__target___PyCode_SetExtra)(PyObject*, Py_ssize_t, void*) = NULL;
PyAPI_FUNC(int) _PyCode_SetExtra(PyObject* a, Py_ssize_t b, void* c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    if (__target___PyCode_SetExtra == NULL) {
        __target___PyCode_SetExtra = resolveAPI("_PyCode_SetExtra");
    }
    STATS_BEFORE(_PyCode_SetExtra)
    int result = (int) __target___PyCode_SetExtra(a, b, c);
    STATS_AFTER(_PyCode_SetExtra)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyCodecInfo_GetIncrementalDecoder, _PyCode_SetExtra)
PyObject* (*__target___PyCodecInfo_GetIncrementalDecoder)(PyObject*, const char*) = NULL;
PyAPI_FUNC(PyObject*) _PyCodecInfo_GetIncrementalDecoder(PyObject* a, const char* b) {
    LOG("0x%lx '%s'(0x%lx)", (unsigned long) a, b?b:"<null>", (unsigned long) b);
    if (__target___PyCodecInfo_GetIncrementalDecoder == NULL) {
        __target___PyCodecInfo_GetIncrementalDecoder = resolveAPI("_PyCodecInfo_GetIncrementalDecoder");
    }
    STATS_BEFORE(_PyCodecInfo_GetIncrementalDecoder)
    PyObject* result = (PyObject*) __target___PyCodecInfo_GetIncrementalDecoder(a, b);
    STATS_AFTER(_PyCodecInfo_GetIncrementalDecoder)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyCodecInfo_GetIncrementalEncoder, _PyCodecInfo_GetIncrementalDecoder)
PyObject* (*__target___PyCodecInfo_GetIncrementalEncoder)(PyObject*, const char*) = NULL;
PyAPI_FUNC(PyObject*) _PyCodecInfo_GetIncrementalEncoder(PyObject* a, const char* b) {
    LOG("0x%lx '%s'(0x%lx)", (unsigned long) a, b?b:"<null>", (unsigned long) b);
    if (__target___PyCodecInfo_GetIncrementalEncoder == NULL) {
        __target___PyCodecInfo_GetIncrementalEncoder = resolveAPI("_PyCodecInfo_GetIncrementalEncoder");
    }
    STATS_BEFORE(_PyCodecInfo_GetIncrementalEncoder)
    PyObject* result = (PyObject*) __target___PyCodecInfo_GetIncrementalEncoder(a, b);
    STATS_AFTER(_PyCodecInfo_GetIncrementalEncoder)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyCodec_DecodeText, _PyCodecInfo_GetIncrementalEncoder)
PyObject* (*__target___PyCodec_DecodeText)(PyObject*, const char*, const char*) = NULL;
PyAPI_FUNC(PyObject*) _PyCodec_DecodeText(PyObject* a, const char* b, const char* c) {
    LOG("0x%lx '%s'(0x%lx) '%s'(0x%lx)", (unsigned long) a, b?b:"<null>", (unsigned long) b, c?c:"<null>", (unsigned long) c);
    if (__target___PyCodec_DecodeText == NULL) {
        __target___PyCodec_DecodeText = resolveAPI("_PyCodec_DecodeText");
    }
    STATS_BEFORE(_PyCodec_DecodeText)
    PyObject* result = (PyObject*) __target___PyCodec_DecodeText(a, b, c);
    STATS_AFTER(_PyCodec_DecodeText)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyCodec_EncodeText, _PyCodec_DecodeText)
PyObject* (*__target___PyCodec_EncodeText)(PyObject*, const char*, const char*) = NULL;
PyAPI_FUNC(PyObject*) _PyCodec_EncodeText(PyObject* a, const char* b, const char* c) {
    LOG("0x%lx '%s'(0x%lx) '%s'(0x%lx)", (unsigned long) a, b?b:"<null>", (unsigned long) b, c?c:"<null>", (unsigned long) c);
    if (__target___PyCodec_EncodeText == NULL) {
        __target___PyCodec_EncodeText = resolveAPI("_PyCodec_EncodeText");
    }
    STATS_BEFORE(_PyCodec_EncodeText)
    PyObject* result = (PyObject*) __target___PyCodec_EncodeText(a, b, c);
    STATS_AFTER(_PyCodec_EncodeText)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyCodec_Forget, _PyCodec_EncodeText)
int (*__target___PyCodec_Forget)(const char*) = NULL;
PyAPI_FUNC(int) _PyCodec_Forget(const char* a) {
    LOG("'%s'(0x%lx)", a?a:"<null>", (unsigned long) a);
    if (__target___PyCodec_Forget == NULL) {
        __target___PyCodec_Forget = resolveAPI("_PyCodec_Forget");
    }
    STATS_BEFORE(_PyCodec_Forget)
    int result = (int) __target___PyCodec_Forget(a);
    STATS_AFTER(_PyCodec_Forget)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyCodec_Lookup, _PyCodec_Forget)
PyObject* (*__target___PyCodec_Lookup)(const char*) = NULL;
PyAPI_FUNC(PyObject*) _PyCodec_Lookup(const char* a) {
    LOG("'%s'(0x%lx)", a?a:"<null>", (unsigned long) a);
    if (__target___PyCodec_Lookup == NULL) {
        __target___PyCodec_Lookup = resolveAPI("_PyCodec_Lookup");
    }
    STATS_BEFORE(_PyCodec_Lookup)
    PyObject* result = (PyObject*) __target___PyCodec_Lookup(a);
    STATS_AFTER(_PyCodec_Lookup)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyCodec_LookupTextEncoding, _PyCodec_Lookup)
PyObject* (*__target___PyCodec_LookupTextEncoding)(const char*, const char*) = NULL;
PyAPI_FUNC(PyObject*) _PyCodec_LookupTextEncoding(const char* a, const char* b) {
    LOG("'%s'(0x%lx) '%s'(0x%lx)", a?a:"<null>", (unsigned long) a, b?b:"<null>", (unsigned long) b);
    if (__target___PyCodec_LookupTextEncoding == NULL) {
        __target___PyCodec_LookupTextEncoding = resolveAPI("_PyCodec_LookupTextEncoding");
    }
    STATS_BEFORE(_PyCodec_LookupTextEncoding)
    PyObject* result = (PyObject*) __target___PyCodec_LookupTextEncoding(a, b);
    STATS_AFTER(_PyCodec_LookupTextEncoding)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyComplex_FormatAdvancedWriter, _PyCodec_LookupTextEncoding)
int (*__target___PyComplex_FormatAdvancedWriter)(_PyUnicodeWriter*, PyObject*, PyObject*, Py_ssize_t, Py_ssize_t) = NULL;
PyAPI_FUNC(int) _PyComplex_FormatAdvancedWriter(_PyUnicodeWriter* a, PyObject* b, PyObject* c, Py_ssize_t d, Py_ssize_t e) {
    LOG("0x%lx 0x%lx 0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c, (unsigned long) d, (unsigned long) e);
    if (__target___PyComplex_FormatAdvancedWriter == NULL) {
        __target___PyComplex_FormatAdvancedWriter = resolveAPI("_PyComplex_FormatAdvancedWriter");
    }
    STATS_BEFORE(_PyComplex_FormatAdvancedWriter)
    int result = (int) __target___PyComplex_FormatAdvancedWriter(a, b, c, d, e);
    STATS_AFTER(_PyComplex_FormatAdvancedWriter)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyContext_NewHamtForTests, _PyComplex_FormatAdvancedWriter)
PyObject* (*__target___PyContext_NewHamtForTests)() = NULL;
PyAPI_FUNC(PyObject*) _PyContext_NewHamtForTests() {
    LOGS("");
    if (__target___PyContext_NewHamtForTests == NULL) {
        __target___PyContext_NewHamtForTests = resolveAPI("_PyContext_NewHamtForTests");
    }
    STATS_BEFORE(_PyContext_NewHamtForTests)
    PyObject* result = (PyObject*) __target___PyContext_NewHamtForTests();
    STATS_AFTER(_PyContext_NewHamtForTests)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyCoro_GetAwaitableIter, _PyContext_NewHamtForTests)
PyObject* (*__target___PyCoro_GetAwaitableIter)(PyObject*) = NULL;
PyAPI_FUNC(PyObject*) _PyCoro_GetAwaitableIter(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target___PyCoro_GetAwaitableIter == NULL) {
        __target___PyCoro_GetAwaitableIter = resolveAPI("_PyCoro_GetAwaitableIter");
    }
    STATS_BEFORE(_PyCoro_GetAwaitableIter)
    PyObject* result = (PyObject*) __target___PyCoro_GetAwaitableIter(a);
    STATS_AFTER(_PyCoro_GetAwaitableIter)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyCrossInterpreterData_Lookup, _PyCoro_GetAwaitableIter)
crossinterpdatafunc (*__target___PyCrossInterpreterData_Lookup)(PyObject*) = NULL;
PyAPI_FUNC(crossinterpdatafunc) _PyCrossInterpreterData_Lookup(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target___PyCrossInterpreterData_Lookup == NULL) {
        __target___PyCrossInterpreterData_Lookup = resolveAPI("_PyCrossInterpreterData_Lookup");
    }
    STATS_BEFORE(_PyCrossInterpreterData_Lookup)
    crossinterpdatafunc result = (crossinterpdatafunc) __target___PyCrossInterpreterData_Lookup(a);
    STATS_AFTER(_PyCrossInterpreterData_Lookup)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyCrossInterpreterData_NewObject, _PyCrossInterpreterData_Lookup)
PyObject* (*__target___PyCrossInterpreterData_NewObject)(_PyCrossInterpreterData*) = NULL;
PyAPI_FUNC(PyObject*) _PyCrossInterpreterData_NewObject(_PyCrossInterpreterData* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target___PyCrossInterpreterData_NewObject == NULL) {
        __target___PyCrossInterpreterData_NewObject = resolveAPI("_PyCrossInterpreterData_NewObject");
    }
    STATS_BEFORE(_PyCrossInterpreterData_NewObject)
    PyObject* result = (PyObject*) __target___PyCrossInterpreterData_NewObject(a);
    STATS_AFTER(_PyCrossInterpreterData_NewObject)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyCrossInterpreterData_RegisterClass, _PyCrossInterpreterData_NewObject)
int (*__target___PyCrossInterpreterData_RegisterClass)(PyTypeObject*, crossinterpdatafunc) = NULL;
PyAPI_FUNC(int) _PyCrossInterpreterData_RegisterClass(PyTypeObject* a, crossinterpdatafunc b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target___PyCrossInterpreterData_RegisterClass == NULL) {
        __target___PyCrossInterpreterData_RegisterClass = resolveAPI("_PyCrossInterpreterData_RegisterClass");
    }
    STATS_BEFORE(_PyCrossInterpreterData_RegisterClass)
    int result = (int) __target___PyCrossInterpreterData_RegisterClass(a, b);
    STATS_AFTER(_PyCrossInterpreterData_RegisterClass)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyCrossInterpreterData_Release, _PyCrossInterpreterData_RegisterClass)
void (*__target___PyCrossInterpreterData_Release)(_PyCrossInterpreterData*) = NULL;
PyAPI_FUNC(void) _PyCrossInterpreterData_Release(_PyCrossInterpreterData* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target___PyCrossInterpreterData_Release == NULL) {
        __target___PyCrossInterpreterData_Release = resolveAPI("_PyCrossInterpreterData_Release");
    }
    STATS_BEFORE(_PyCrossInterpreterData_Release)
    __target___PyCrossInterpreterData_Release(a);
    STATS_AFTER(_PyCrossInterpreterData_Release)
    LOG_AFTER_VOID
}
STATS_CONTAINER(_PyDebugAllocatorStats, _PyCrossInterpreterData_Release)
void (*__target___PyDebugAllocatorStats)(FILE*, const char*, int, size_t) = NULL;
PyAPI_FUNC(void) _PyDebugAllocatorStats(FILE* a, const char* b, int c, size_t d) {
    LOG("0x%lx '%s'(0x%lx) 0x%lx 0x%lx", (unsigned long) a, b?b:"<null>", (unsigned long) b, (unsigned long) c, (unsigned long) d);
    if (__target___PyDebugAllocatorStats == NULL) {
        __target___PyDebugAllocatorStats = resolveAPI("_PyDebugAllocatorStats");
    }
    STATS_BEFORE(_PyDebugAllocatorStats)
    __target___PyDebugAllocatorStats(a, b, c, d);
    STATS_AFTER(_PyDebugAllocatorStats)
    LOG_AFTER_VOID
}
STATS_CONTAINER(_PyDictView_Intersect, _PyDebugAllocatorStats)
PyObject* (*__target___PyDictView_Intersect)(PyObject*, PyObject*) = NULL;
PyAPI_FUNC(PyObject*) _PyDictView_Intersect(PyObject* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target___PyDictView_Intersect == NULL) {
        __target___PyDictView_Intersect = resolveAPI("_PyDictView_Intersect");
    }
    STATS_BEFORE(_PyDictView_Intersect)
    PyObject* result = (PyObject*) __target___PyDictView_Intersect(a, b);
    STATS_AFTER(_PyDictView_Intersect)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyDictView_New, _PyDictView_Intersect)
PyObject* (*__target___PyDictView_New)(PyObject*, PyTypeObject*) = NULL;
PyAPI_FUNC(PyObject*) _PyDictView_New(PyObject* a, PyTypeObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target___PyDictView_New == NULL) {
        __target___PyDictView_New = resolveAPI("_PyDictView_New");
    }
    STATS_BEFORE(_PyDictView_New)
    PyObject* result = (PyObject*) __target___PyDictView_New(a, b);
    STATS_AFTER(_PyDictView_New)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyDict_Contains_KnownHash, _PyDictView_New)
int (*__target___PyDict_Contains_KnownHash)(PyObject*, PyObject*, Py_hash_t) = NULL;
PyAPI_FUNC(int) _PyDict_Contains_KnownHash(PyObject* a, PyObject* b, Py_hash_t c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    if (__target___PyDict_Contains_KnownHash == NULL) {
        __target___PyDict_Contains_KnownHash = resolveAPI("_PyDict_Contains_KnownHash");
    }
    STATS_BEFORE(_PyDict_Contains_KnownHash)
    int result = (int) __target___PyDict_Contains_KnownHash(a, b, c);
    STATS_AFTER(_PyDict_Contains_KnownHash)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyDict_DebugMallocStats, _PyDict_Contains_KnownHash)
void (*__target___PyDict_DebugMallocStats)(FILE*) = NULL;
PyAPI_FUNC(void) _PyDict_DebugMallocStats(FILE* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target___PyDict_DebugMallocStats == NULL) {
        __target___PyDict_DebugMallocStats = resolveAPI("_PyDict_DebugMallocStats");
    }
    STATS_BEFORE(_PyDict_DebugMallocStats)
    __target___PyDict_DebugMallocStats(a);
    STATS_AFTER(_PyDict_DebugMallocStats)
    LOG_AFTER_VOID
}
STATS_CONTAINER(_PyDict_DelItemId, _PyDict_DebugMallocStats)
int (*__target___PyDict_DelItemId)(PyObject*, struct _Py_Identifier*) = NULL;
PyAPI_FUNC(int) _PyDict_DelItemId(PyObject* a, struct _Py_Identifier* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target___PyDict_DelItemId == NULL) {
        __target___PyDict_DelItemId = resolveAPI("_PyDict_DelItemId");
    }
    STATS_BEFORE(_PyDict_DelItemId)
    int result = (int) __target___PyDict_DelItemId(a, b);
    STATS_AFTER(_PyDict_DelItemId)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyDict_DelItemIf, _PyDict_DelItemId)
int (*__target___PyDict_DelItemIf)(PyObject*, PyObject*, int (*)(PyObject*value)) = NULL;
PyAPI_FUNC(int) _PyDict_DelItemIf(PyObject* a, PyObject* b, int (*c)(PyObject*value)) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    if (__target___PyDict_DelItemIf == NULL) {
        __target___PyDict_DelItemIf = resolveAPI("_PyDict_DelItemIf");
    }
    STATS_BEFORE(_PyDict_DelItemIf)
    int result = (int) __target___PyDict_DelItemIf(a, b, c);
    STATS_AFTER(_PyDict_DelItemIf)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyDict_DelItem_KnownHash, _PyDict_DelItemIf)
int (*__target___PyDict_DelItem_KnownHash)(PyObject*, PyObject*, Py_hash_t) = NULL;
PyAPI_FUNC(int) _PyDict_DelItem_KnownHash(PyObject* a, PyObject* b, Py_hash_t c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    if (__target___PyDict_DelItem_KnownHash == NULL) {
        __target___PyDict_DelItem_KnownHash = resolveAPI("_PyDict_DelItem_KnownHash");
    }
    STATS_BEFORE(_PyDict_DelItem_KnownHash)
    int result = (int) __target___PyDict_DelItem_KnownHash(a, b, c);
    STATS_AFTER(_PyDict_DelItem_KnownHash)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyDict_FromKeys, _PyDict_DelItem_KnownHash)
PyObject* (*__target___PyDict_FromKeys)(PyObject*, PyObject*, PyObject*) = NULL;
PyAPI_FUNC(PyObject*) _PyDict_FromKeys(PyObject* a, PyObject* b, PyObject* c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    if (__target___PyDict_FromKeys == NULL) {
        __target___PyDict_FromKeys = resolveAPI("_PyDict_FromKeys");
    }
    STATS_BEFORE(_PyDict_FromKeys)
    PyObject* result = (PyObject*) __target___PyDict_FromKeys(a, b, c);
    STATS_AFTER(_PyDict_FromKeys)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyDict_GetItemHint, _PyDict_FromKeys)
Py_ssize_t (*__target___PyDict_GetItemHint)(PyDictObject*, PyObject*, Py_ssize_t, PyObject**) = NULL;
PyAPI_FUNC(Py_ssize_t) _PyDict_GetItemHint(PyDictObject* a, PyObject* b, Py_ssize_t c, PyObject** d) {
    LOG("0x%lx 0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c, (unsigned long) d);
    if (__target___PyDict_GetItemHint == NULL) {
        __target___PyDict_GetItemHint = resolveAPI("_PyDict_GetItemHint");
    }
    STATS_BEFORE(_PyDict_GetItemHint)
    Py_ssize_t result = (Py_ssize_t) __target___PyDict_GetItemHint(a, b, c, d);
    STATS_AFTER(_PyDict_GetItemHint)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyDict_HasOnlyStringKeys, _PyDict_GetItemHint)
int (*__target___PyDict_HasOnlyStringKeys)(PyObject*) = NULL;
PyAPI_FUNC(int) _PyDict_HasOnlyStringKeys(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target___PyDict_HasOnlyStringKeys == NULL) {
        __target___PyDict_HasOnlyStringKeys = resolveAPI("_PyDict_HasOnlyStringKeys");
    }
    STATS_BEFORE(_PyDict_HasOnlyStringKeys)
    int result = (int) __target___PyDict_HasOnlyStringKeys(a);
    STATS_AFTER(_PyDict_HasOnlyStringKeys)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyDict_KeysSize, _PyDict_HasOnlyStringKeys)
Py_ssize_t (*__target___PyDict_KeysSize)(PyDictKeysObject*) = NULL;
PyAPI_FUNC(Py_ssize_t) _PyDict_KeysSize(PyDictKeysObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target___PyDict_KeysSize == NULL) {
        __target___PyDict_KeysSize = resolveAPI("_PyDict_KeysSize");
    }
    STATS_BEFORE(_PyDict_KeysSize)
    Py_ssize_t result = (Py_ssize_t) __target___PyDict_KeysSize(a);
    STATS_AFTER(_PyDict_KeysSize)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyDict_LoadGlobal, _PyDict_KeysSize)
PyObject* (*__target___PyDict_LoadGlobal)(PyDictObject*, PyDictObject*, PyObject*) = NULL;
PyAPI_FUNC(PyObject*) _PyDict_LoadGlobal(PyDictObject* a, PyDictObject* b, PyObject* c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    if (__target___PyDict_LoadGlobal == NULL) {
        __target___PyDict_LoadGlobal = resolveAPI("_PyDict_LoadGlobal");
    }
    STATS_BEFORE(_PyDict_LoadGlobal)
    PyObject* result = (PyObject*) __target___PyDict_LoadGlobal(a, b, c);
    STATS_AFTER(_PyDict_LoadGlobal)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyDict_MaybeUntrack, _PyDict_LoadGlobal)
void (*__target___PyDict_MaybeUntrack)(PyObject*) = NULL;
PyAPI_FUNC(void) _PyDict_MaybeUntrack(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target___PyDict_MaybeUntrack == NULL) {
        __target___PyDict_MaybeUntrack = resolveAPI("_PyDict_MaybeUntrack");
    }
    STATS_BEFORE(_PyDict_MaybeUntrack)
    __target___PyDict_MaybeUntrack(a);
    STATS_AFTER(_PyDict_MaybeUntrack)
    LOG_AFTER_VOID
}
STATS_CONTAINER(_PyDict_MergeEx, _PyDict_MaybeUntrack)
int (*__target___PyDict_MergeEx)(PyObject*, PyObject*, int) = NULL;
PyAPI_FUNC(int) _PyDict_MergeEx(PyObject* a, PyObject* b, int c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    if (__target___PyDict_MergeEx == NULL) {
        __target___PyDict_MergeEx = resolveAPI("_PyDict_MergeEx");
    }
    STATS_BEFORE(_PyDict_MergeEx)
    int result = (int) __target___PyDict_MergeEx(a, b, c);
    STATS_AFTER(_PyDict_MergeEx)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyDict_NewKeysForClass, _PyDict_MergeEx)
PyDictKeysObject* (*__target___PyDict_NewKeysForClass)() = NULL;
PyAPI_FUNC(PyDictKeysObject*) _PyDict_NewKeysForClass() {
    LOGS("");
    if (__target___PyDict_NewKeysForClass == NULL) {
        __target___PyDict_NewKeysForClass = resolveAPI("_PyDict_NewKeysForClass");
    }
    STATS_BEFORE(_PyDict_NewKeysForClass)
    PyDictKeysObject* result = (PyDictKeysObject*) __target___PyDict_NewKeysForClass();
    STATS_AFTER(_PyDict_NewKeysForClass)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyDict_Pop, _PyDict_NewKeysForClass)
PyObject* (*__target___PyDict_Pop)(PyObject*, PyObject*, PyObject*) = NULL;
PyAPI_FUNC(PyObject*) _PyDict_Pop(PyObject* a, PyObject* b, PyObject* c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    if (__target___PyDict_Pop == NULL) {
        __target___PyDict_Pop = resolveAPI("_PyDict_Pop");
    }
    STATS_BEFORE(_PyDict_Pop)
    PyObject* result = (PyObject*) __target___PyDict_Pop(a, b, c);
    STATS_AFTER(_PyDict_Pop)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyDict_Pop_KnownHash, _PyDict_Pop)
PyObject* (*__target___PyDict_Pop_KnownHash)(PyObject*, PyObject*, Py_hash_t, PyObject*) = NULL;
PyAPI_FUNC(PyObject*) _PyDict_Pop_KnownHash(PyObject* a, PyObject* b, Py_hash_t c, PyObject* d) {
    LOG("0x%lx 0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c, (unsigned long) d);
    if (__target___PyDict_Pop_KnownHash == NULL) {
        __target___PyDict_Pop_KnownHash = resolveAPI("_PyDict_Pop_KnownHash");
    }
    STATS_BEFORE(_PyDict_Pop_KnownHash)
    PyObject* result = (PyObject*) __target___PyDict_Pop_KnownHash(a, b, c, d);
    STATS_AFTER(_PyDict_Pop_KnownHash)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyDict_SetItem_KnownHash, _PyDict_Pop_KnownHash)
int (*__target___PyDict_SetItem_KnownHash)(PyObject*, PyObject*, PyObject*, Py_hash_t) = NULL;
PyAPI_FUNC(int) _PyDict_SetItem_KnownHash(PyObject* a, PyObject* b, PyObject* c, Py_hash_t d) {
    LOG("0x%lx 0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c, (unsigned long) d);
    if (__target___PyDict_SetItem_KnownHash == NULL) {
        __target___PyDict_SetItem_KnownHash = resolveAPI("_PyDict_SetItem_KnownHash");
    }
    STATS_BEFORE(_PyDict_SetItem_KnownHash)
    int result = (int) __target___PyDict_SetItem_KnownHash(a, b, c, d);
    STATS_AFTER(_PyDict_SetItem_KnownHash)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyDict_SizeOf, _PyDict_SetItem_KnownHash)
Py_ssize_t (*__target___PyDict_SizeOf)(PyDictObject*) = NULL;
PyAPI_FUNC(Py_ssize_t) _PyDict_SizeOf(PyDictObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target___PyDict_SizeOf == NULL) {
        __target___PyDict_SizeOf = resolveAPI("_PyDict_SizeOf");
    }
    STATS_BEFORE(_PyDict_SizeOf)
    Py_ssize_t result = (Py_ssize_t) __target___PyDict_SizeOf(a);
    STATS_AFTER(_PyDict_SizeOf)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyErr_BadInternalCall, _PyDict_SizeOf)
void (*__target___PyErr_BadInternalCall)(const char*, int) = NULL;
PyAPI_FUNC(void) _PyErr_BadInternalCall(const char* a, int b) {
    LOG("'%s'(0x%lx) 0x%lx", a?a:"<null>", (unsigned long) a, (unsigned long) b);
    if (__target___PyErr_BadInternalCall == NULL) {
        __target___PyErr_BadInternalCall = resolveAPI("_PyErr_BadInternalCall");
    }
    STATS_BEFORE(_PyErr_BadInternalCall)
    __target___PyErr_BadInternalCall(a, b);
    STATS_AFTER(_PyErr_BadInternalCall)
    LOG_AFTER_VOID
}
STATS_CONTAINER(_PyErr_ChainExceptions, _PyErr_BadInternalCall)
void (*__target___PyErr_ChainExceptions)(PyObject*, PyObject*, PyObject*) = NULL;
PyAPI_FUNC(void) _PyErr_ChainExceptions(PyObject* a, PyObject* b, PyObject* c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    if (__target___PyErr_ChainExceptions == NULL) {
        __target___PyErr_ChainExceptions = resolveAPI("_PyErr_ChainExceptions");
    }
    STATS_BEFORE(_PyErr_ChainExceptions)
    __target___PyErr_ChainExceptions(a, b, c);
    STATS_AFTER(_PyErr_ChainExceptions)
    LOG_AFTER_VOID
}
STATS_CONTAINER(_PyErr_CheckSignals, _PyErr_ChainExceptions)
int (*__target___PyErr_CheckSignals)() = NULL;
PyAPI_FUNC(int) _PyErr_CheckSignals() {
    LOGS("");
    if (__target___PyErr_CheckSignals == NULL) {
        __target___PyErr_CheckSignals = resolveAPI("_PyErr_CheckSignals");
    }
    STATS_BEFORE(_PyErr_CheckSignals)
    int result = (int) __target___PyErr_CheckSignals();
    STATS_AFTER(_PyErr_CheckSignals)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyErr_CreateAndSetException, _PyErr_CheckSignals)
void (*__target___PyErr_CreateAndSetException)(PyObject*, PyObject*) = NULL;
PyAPI_FUNC(void) _PyErr_CreateAndSetException(PyObject* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target___PyErr_CreateAndSetException == NULL) {
        __target___PyErr_CreateAndSetException = resolveAPI("_PyErr_CreateAndSetException");
    }
    STATS_BEFORE(_PyErr_CreateAndSetException)
    __target___PyErr_CreateAndSetException(a, b);
    STATS_AFTER(_PyErr_CreateAndSetException)
    LOG_AFTER_VOID
}
STATS_CONTAINER(_PyErr_GetExcInfo, _PyErr_CreateAndSetException)
void (*__target___PyErr_GetExcInfo)(PyThreadState*, PyObject**, PyObject**, PyObject**) = NULL;
PyAPI_FUNC(void) _PyErr_GetExcInfo(PyThreadState* a, PyObject** b, PyObject** c, PyObject** d) {
    LOG("0x%lx 0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c, (unsigned long) d);
    if (__target___PyErr_GetExcInfo == NULL) {
        __target___PyErr_GetExcInfo = resolveAPI("_PyErr_GetExcInfo");
    }
    STATS_BEFORE(_PyErr_GetExcInfo)
    __target___PyErr_GetExcInfo(a, b, c, d);
    STATS_AFTER(_PyErr_GetExcInfo)
    LOG_AFTER_VOID
}
STATS_CONTAINER(_PyErr_GetTopmostException, _PyErr_GetExcInfo)
_PyErr_StackItem* (*__target___PyErr_GetTopmostException)(PyThreadState*) = NULL;
PyAPI_FUNC(_PyErr_StackItem*) _PyErr_GetTopmostException(PyThreadState* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target___PyErr_GetTopmostException == NULL) {
        __target___PyErr_GetTopmostException = resolveAPI("_PyErr_GetTopmostException");
    }
    STATS_BEFORE(_PyErr_GetTopmostException)
    _PyErr_StackItem* result = (_PyErr_StackItem*) __target___PyErr_GetTopmostException(a);
    STATS_AFTER(_PyErr_GetTopmostException)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyErr_ProgramDecodedTextObject, _PyErr_GetTopmostException)
PyObject* (*__target___PyErr_ProgramDecodedTextObject)(PyObject*, int, const char*) = NULL;
PyAPI_FUNC(PyObject*) _PyErr_ProgramDecodedTextObject(PyObject* a, int b, const char* c) {
    LOG("0x%lx 0x%lx '%s'(0x%lx)", (unsigned long) a, (unsigned long) b, c?c:"<null>", (unsigned long) c);
    if (__target___PyErr_ProgramDecodedTextObject == NULL) {
        __target___PyErr_ProgramDecodedTextObject = resolveAPI("_PyErr_ProgramDecodedTextObject");
    }
    STATS_BEFORE(_PyErr_ProgramDecodedTextObject)
    PyObject* result = (PyObject*) __target___PyErr_ProgramDecodedTextObject(a, b, c);
    STATS_AFTER(_PyErr_ProgramDecodedTextObject)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyErr_SetKeyError, _PyErr_ProgramDecodedTextObject)
void (*__target___PyErr_SetKeyError)(PyObject*) = NULL;
PyAPI_FUNC(void) _PyErr_SetKeyError(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target___PyErr_SetKeyError == NULL) {
        __target___PyErr_SetKeyError = resolveAPI("_PyErr_SetKeyError");
    }
    STATS_BEFORE(_PyErr_SetKeyError)
    __target___PyErr_SetKeyError(a);
    STATS_AFTER(_PyErr_SetKeyError)
    LOG_AFTER_VOID
}
STATS_CONTAINER(_PyErr_WarnUnawaitedCoroutine, _PyErr_SetKeyError)
void (*__target___PyErr_WarnUnawaitedCoroutine)(PyObject*) = NULL;
PyAPI_FUNC(void) _PyErr_WarnUnawaitedCoroutine(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target___PyErr_WarnUnawaitedCoroutine == NULL) {
        __target___PyErr_WarnUnawaitedCoroutine = resolveAPI("_PyErr_WarnUnawaitedCoroutine");
    }
    STATS_BEFORE(_PyErr_WarnUnawaitedCoroutine)
    __target___PyErr_WarnUnawaitedCoroutine(a);
    STATS_AFTER(_PyErr_WarnUnawaitedCoroutine)
    LOG_AFTER_VOID
}
STATS_CONTAINER(_PyErr_WriteUnraisableMsg, _PyErr_WarnUnawaitedCoroutine)
void (*__target___PyErr_WriteUnraisableMsg)(const char*, PyObject*) = NULL;
PyAPI_FUNC(void) _PyErr_WriteUnraisableMsg(const char* a, PyObject* b) {
    LOG("'%s'(0x%lx) 0x%lx", a?a:"<null>", (unsigned long) a, (unsigned long) b);
    if (__target___PyErr_WriteUnraisableMsg == NULL) {
        __target___PyErr_WriteUnraisableMsg = resolveAPI("_PyErr_WriteUnraisableMsg");
    }
    STATS_BEFORE(_PyErr_WriteUnraisableMsg)
    __target___PyErr_WriteUnraisableMsg(a, b);
    STATS_AFTER(_PyErr_WriteUnraisableMsg)
    LOG_AFTER_VOID
}
STATS_CONTAINER(_PyEval_CallTracing, _PyErr_WriteUnraisableMsg)
PyObject* (*__target___PyEval_CallTracing)(PyObject*, PyObject*) = NULL;
PyAPI_FUNC(PyObject*) _PyEval_CallTracing(PyObject* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target___PyEval_CallTracing == NULL) {
        __target___PyEval_CallTracing = resolveAPI("_PyEval_CallTracing");
    }
    STATS_BEFORE(_PyEval_CallTracing)
    PyObject* result = (PyObject*) __target___PyEval_CallTracing(a, b);
    STATS_AFTER(_PyEval_CallTracing)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyEval_EvalFrameDefault, _PyEval_CallTracing)
PyObject* (*__target___PyEval_EvalFrameDefault)(PyThreadState*, PyFrameObject*, int) = NULL;
PyAPI_FUNC(PyObject*) _PyEval_EvalFrameDefault(PyThreadState* a, PyFrameObject* b, int c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    if (__target___PyEval_EvalFrameDefault == NULL) {
        __target___PyEval_EvalFrameDefault = resolveAPI("_PyEval_EvalFrameDefault");
    }
    STATS_BEFORE(_PyEval_EvalFrameDefault)
    PyObject* result = (PyObject*) __target___PyEval_EvalFrameDefault(a, b, c);
    STATS_AFTER(_PyEval_EvalFrameDefault)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyEval_GetAsyncGenFinalizer, _PyEval_EvalFrameDefault)
PyObject* (*__target___PyEval_GetAsyncGenFinalizer)() = NULL;
PyAPI_FUNC(PyObject*) _PyEval_GetAsyncGenFinalizer() {
    LOGS("");
    if (__target___PyEval_GetAsyncGenFinalizer == NULL) {
        __target___PyEval_GetAsyncGenFinalizer = resolveAPI("_PyEval_GetAsyncGenFinalizer");
    }
    STATS_BEFORE(_PyEval_GetAsyncGenFinalizer)
    PyObject* result = (PyObject*) __target___PyEval_GetAsyncGenFinalizer();
    STATS_AFTER(_PyEval_GetAsyncGenFinalizer)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyEval_GetAsyncGenFirstiter, _PyEval_GetAsyncGenFinalizer)
PyObject* (*__target___PyEval_GetAsyncGenFirstiter)() = NULL;
PyAPI_FUNC(PyObject*) _PyEval_GetAsyncGenFirstiter() {
    LOGS("");
    if (__target___PyEval_GetAsyncGenFirstiter == NULL) {
        __target___PyEval_GetAsyncGenFirstiter = resolveAPI("_PyEval_GetAsyncGenFirstiter");
    }
    STATS_BEFORE(_PyEval_GetAsyncGenFirstiter)
    PyObject* result = (PyObject*) __target___PyEval_GetAsyncGenFirstiter();
    STATS_AFTER(_PyEval_GetAsyncGenFirstiter)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyEval_GetBuiltinId, _PyEval_GetAsyncGenFirstiter)
PyObject* (*__target___PyEval_GetBuiltinId)(_Py_Identifier*) = NULL;
PyAPI_FUNC(PyObject*) _PyEval_GetBuiltinId(_Py_Identifier* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target___PyEval_GetBuiltinId == NULL) {
        __target___PyEval_GetBuiltinId = resolveAPI("_PyEval_GetBuiltinId");
    }
    STATS_BEFORE(_PyEval_GetBuiltinId)
    PyObject* result = (PyObject*) __target___PyEval_GetBuiltinId(a);
    STATS_AFTER(_PyEval_GetBuiltinId)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyEval_GetCoroutineOriginTrackingDepth, _PyEval_GetBuiltinId)
int (*__target___PyEval_GetCoroutineOriginTrackingDepth)() = NULL;
PyAPI_FUNC(int) _PyEval_GetCoroutineOriginTrackingDepth() {
    LOGS("");
    if (__target___PyEval_GetCoroutineOriginTrackingDepth == NULL) {
        __target___PyEval_GetCoroutineOriginTrackingDepth = resolveAPI("_PyEval_GetCoroutineOriginTrackingDepth");
    }
    STATS_BEFORE(_PyEval_GetCoroutineOriginTrackingDepth)
    int result = (int) __target___PyEval_GetCoroutineOriginTrackingDepth();
    STATS_AFTER(_PyEval_GetCoroutineOriginTrackingDepth)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyEval_GetSwitchInterval, _PyEval_GetCoroutineOriginTrackingDepth)
unsigned long (*__target___PyEval_GetSwitchInterval)() = NULL;
PyAPI_FUNC(unsigned long) _PyEval_GetSwitchInterval() {
    LOGS("");
    if (__target___PyEval_GetSwitchInterval == NULL) {
        __target___PyEval_GetSwitchInterval = resolveAPI("_PyEval_GetSwitchInterval");
    }
    STATS_BEFORE(_PyEval_GetSwitchInterval)
    unsigned long result = (unsigned long) __target___PyEval_GetSwitchInterval();
    STATS_AFTER(_PyEval_GetSwitchInterval)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyEval_RequestCodeExtraIndex, _PyEval_GetSwitchInterval)
Py_ssize_t (*__target___PyEval_RequestCodeExtraIndex)(freefunc) = NULL;
PyAPI_FUNC(Py_ssize_t) _PyEval_RequestCodeExtraIndex(freefunc a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target___PyEval_RequestCodeExtraIndex == NULL) {
        __target___PyEval_RequestCodeExtraIndex = resolveAPI("_PyEval_RequestCodeExtraIndex");
    }
    STATS_BEFORE(_PyEval_RequestCodeExtraIndex)
    Py_ssize_t result = (Py_ssize_t) __target___PyEval_RequestCodeExtraIndex(a);
    STATS_AFTER(_PyEval_RequestCodeExtraIndex)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyEval_SetAsyncGenFinalizer, _PyEval_RequestCodeExtraIndex)
int (*__target___PyEval_SetAsyncGenFinalizer)(PyObject*) = NULL;
PyAPI_FUNC(int) _PyEval_SetAsyncGenFinalizer(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target___PyEval_SetAsyncGenFinalizer == NULL) {
        __target___PyEval_SetAsyncGenFinalizer = resolveAPI("_PyEval_SetAsyncGenFinalizer");
    }
    STATS_BEFORE(_PyEval_SetAsyncGenFinalizer)
    int result = (int) __target___PyEval_SetAsyncGenFinalizer(a);
    STATS_AFTER(_PyEval_SetAsyncGenFinalizer)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyEval_SetAsyncGenFirstiter, _PyEval_SetAsyncGenFinalizer)
int (*__target___PyEval_SetAsyncGenFirstiter)(PyObject*) = NULL;
PyAPI_FUNC(int) _PyEval_SetAsyncGenFirstiter(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target___PyEval_SetAsyncGenFirstiter == NULL) {
        __target___PyEval_SetAsyncGenFirstiter = resolveAPI("_PyEval_SetAsyncGenFirstiter");
    }
    STATS_BEFORE(_PyEval_SetAsyncGenFirstiter)
    int result = (int) __target___PyEval_SetAsyncGenFirstiter(a);
    STATS_AFTER(_PyEval_SetAsyncGenFirstiter)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyEval_SetCoroutineOriginTrackingDepth, _PyEval_SetAsyncGenFirstiter)
void (*__target___PyEval_SetCoroutineOriginTrackingDepth)(int) = NULL;
PyAPI_FUNC(void) _PyEval_SetCoroutineOriginTrackingDepth(int a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target___PyEval_SetCoroutineOriginTrackingDepth == NULL) {
        __target___PyEval_SetCoroutineOriginTrackingDepth = resolveAPI("_PyEval_SetCoroutineOriginTrackingDepth");
    }
    STATS_BEFORE(_PyEval_SetCoroutineOriginTrackingDepth)
    __target___PyEval_SetCoroutineOriginTrackingDepth(a);
    STATS_AFTER(_PyEval_SetCoroutineOriginTrackingDepth)
    LOG_AFTER_VOID
}
STATS_CONTAINER(_PyEval_SetProfile, _PyEval_SetCoroutineOriginTrackingDepth)
int (*__target___PyEval_SetProfile)(PyThreadState*, Py_tracefunc, PyObject*) = NULL;
PyAPI_FUNC(int) _PyEval_SetProfile(PyThreadState* a, Py_tracefunc b, PyObject* c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    if (__target___PyEval_SetProfile == NULL) {
        __target___PyEval_SetProfile = resolveAPI("_PyEval_SetProfile");
    }
    STATS_BEFORE(_PyEval_SetProfile)
    int result = (int) __target___PyEval_SetProfile(a, b, c);
    STATS_AFTER(_PyEval_SetProfile)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyEval_SetSwitchInterval, _PyEval_SetProfile)
void (*__target___PyEval_SetSwitchInterval)(unsigned long) = NULL;
PyAPI_FUNC(void) _PyEval_SetSwitchInterval(unsigned long a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target___PyEval_SetSwitchInterval == NULL) {
        __target___PyEval_SetSwitchInterval = resolveAPI("_PyEval_SetSwitchInterval");
    }
    STATS_BEFORE(_PyEval_SetSwitchInterval)
    __target___PyEval_SetSwitchInterval(a);
    STATS_AFTER(_PyEval_SetSwitchInterval)
    LOG_AFTER_VOID
}
STATS_CONTAINER(_PyEval_SetTrace, _PyEval_SetSwitchInterval)
int (*__target___PyEval_SetTrace)(PyThreadState*, Py_tracefunc, PyObject*) = NULL;
PyAPI_FUNC(int) _PyEval_SetTrace(PyThreadState* a, Py_tracefunc b, PyObject* c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    if (__target___PyEval_SetTrace == NULL) {
        __target___PyEval_SetTrace = resolveAPI("_PyEval_SetTrace");
    }
    STATS_BEFORE(_PyEval_SetTrace)
    int result = (int) __target___PyEval_SetTrace(a, b, c);
    STATS_AFTER(_PyEval_SetTrace)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyEval_SliceIndex, _PyEval_SetTrace)
int (*__target___PyEval_SliceIndex)(PyObject*, Py_ssize_t*) = NULL;
PyAPI_FUNC(int) _PyEval_SliceIndex(PyObject* a, Py_ssize_t* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target___PyEval_SliceIndex == NULL) {
        __target___PyEval_SliceIndex = resolveAPI("_PyEval_SliceIndex");
    }
    STATS_BEFORE(_PyEval_SliceIndex)
    int result = (int) __target___PyEval_SliceIndex(a, b);
    STATS_AFTER(_PyEval_SliceIndex)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyEval_SliceIndexNotNone, _PyEval_SliceIndex)
int (*__target___PyEval_SliceIndexNotNone)(PyObject*, Py_ssize_t*) = NULL;
PyAPI_FUNC(int) _PyEval_SliceIndexNotNone(PyObject* a, Py_ssize_t* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target___PyEval_SliceIndexNotNone == NULL) {
        __target___PyEval_SliceIndexNotNone = resolveAPI("_PyEval_SliceIndexNotNone");
    }
    STATS_BEFORE(_PyEval_SliceIndexNotNone)
    int result = (int) __target___PyEval_SliceIndexNotNone(a, b);
    STATS_AFTER(_PyEval_SliceIndexNotNone)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyFloat_DebugMallocStats, _PyEval_SliceIndexNotNone)
void (*__target___PyFloat_DebugMallocStats)(FILE*) = NULL;
PyAPI_FUNC(void) _PyFloat_DebugMallocStats(FILE* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target___PyFloat_DebugMallocStats == NULL) {
        __target___PyFloat_DebugMallocStats = resolveAPI("_PyFloat_DebugMallocStats");
    }
    STATS_BEFORE(_PyFloat_DebugMallocStats)
    __target___PyFloat_DebugMallocStats(a);
    STATS_AFTER(_PyFloat_DebugMallocStats)
    LOG_AFTER_VOID
}
STATS_CONTAINER(_PyFloat_FormatAdvancedWriter, _PyFloat_DebugMallocStats)
int (*__target___PyFloat_FormatAdvancedWriter)(_PyUnicodeWriter*, PyObject*, PyObject*, Py_ssize_t, Py_ssize_t) = NULL;
PyAPI_FUNC(int) _PyFloat_FormatAdvancedWriter(_PyUnicodeWriter* a, PyObject* b, PyObject* c, Py_ssize_t d, Py_ssize_t e) {
    LOG("0x%lx 0x%lx 0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c, (unsigned long) d, (unsigned long) e);
    if (__target___PyFloat_FormatAdvancedWriter == NULL) {
        __target___PyFloat_FormatAdvancedWriter = resolveAPI("_PyFloat_FormatAdvancedWriter");
    }
    STATS_BEFORE(_PyFloat_FormatAdvancedWriter)
    int result = (int) __target___PyFloat_FormatAdvancedWriter(a, b, c, d, e);
    STATS_AFTER(_PyFloat_FormatAdvancedWriter)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyFloat_Pack2, _PyFloat_FormatAdvancedWriter)
int (*__target___PyFloat_Pack2)(double, unsigned char*, int) = NULL;
PyAPI_FUNC(int) _PyFloat_Pack2(double a, unsigned char* b, int c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    if (__target___PyFloat_Pack2 == NULL) {
        __target___PyFloat_Pack2 = resolveAPI("_PyFloat_Pack2");
    }
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
    if (__target___PyFloat_Pack4 == NULL) {
        __target___PyFloat_Pack4 = resolveAPI("_PyFloat_Pack4");
    }
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
    if (__target___PyFloat_Pack8 == NULL) {
        __target___PyFloat_Pack8 = resolveAPI("_PyFloat_Pack8");
    }
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
    if (__target___PyFloat_Unpack2 == NULL) {
        __target___PyFloat_Unpack2 = resolveAPI("_PyFloat_Unpack2");
    }
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
    if (__target___PyFloat_Unpack4 == NULL) {
        __target___PyFloat_Unpack4 = resolveAPI("_PyFloat_Unpack4");
    }
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
    if (__target___PyFloat_Unpack8 == NULL) {
        __target___PyFloat_Unpack8 = resolveAPI("_PyFloat_Unpack8");
    }
    STATS_BEFORE(_PyFloat_Unpack8)
    double result = (double) __target___PyFloat_Unpack8(a, b);
    STATS_AFTER(_PyFloat_Unpack8)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyFrame_DebugMallocStats, _PyFloat_Unpack8)
void (*__target___PyFrame_DebugMallocStats)(FILE*) = NULL;
PyAPI_FUNC(void) _PyFrame_DebugMallocStats(FILE* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target___PyFrame_DebugMallocStats == NULL) {
        __target___PyFrame_DebugMallocStats = resolveAPI("_PyFrame_DebugMallocStats");
    }
    STATS_BEFORE(_PyFrame_DebugMallocStats)
    __target___PyFrame_DebugMallocStats(a);
    STATS_AFTER(_PyFrame_DebugMallocStats)
    LOG_AFTER_VOID
}
STATS_CONTAINER(_PyFrame_New_NoTrack, _PyFrame_DebugMallocStats)
PyFrameObject* (*__target___PyFrame_New_NoTrack)(PyThreadState*, PyFrameConstructor*, PyObject*) = NULL;
PyAPI_FUNC(PyFrameObject*) _PyFrame_New_NoTrack(PyThreadState* a, PyFrameConstructor* b, PyObject* c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    if (__target___PyFrame_New_NoTrack == NULL) {
        __target___PyFrame_New_NoTrack = resolveAPI("_PyFrame_New_NoTrack");
    }
    STATS_BEFORE(_PyFrame_New_NoTrack)
    PyFrameObject* result = (PyFrameObject*) __target___PyFrame_New_NoTrack(a, b, c);
    STATS_AFTER(_PyFrame_New_NoTrack)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyFrame_SetLineNumber, _PyFrame_New_NoTrack)
void (*__target___PyFrame_SetLineNumber)(PyFrameObject*, int) = NULL;
PyAPI_FUNC(void) _PyFrame_SetLineNumber(PyFrameObject* a, int b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target___PyFrame_SetLineNumber == NULL) {
        __target___PyFrame_SetLineNumber = resolveAPI("_PyFrame_SetLineNumber");
    }
    STATS_BEFORE(_PyFrame_SetLineNumber)
    __target___PyFrame_SetLineNumber(a, b);
    STATS_AFTER(_PyFrame_SetLineNumber)
    LOG_AFTER_VOID
}
STATS_CONTAINER(_PyFunction_FastCallDict, _PyFrame_SetLineNumber)
PyObject* (*__target___PyFunction_FastCallDict)(PyObject*, PyObject*const*, Py_ssize_t, PyObject*) = NULL;
PyAPI_FUNC(PyObject*) _PyFunction_FastCallDict(PyObject* a, PyObject*const* b, Py_ssize_t c, PyObject* d) {
    LOG("0x%lx 0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c, (unsigned long) d);
    if (__target___PyFunction_FastCallDict == NULL) {
        __target___PyFunction_FastCallDict = resolveAPI("_PyFunction_FastCallDict");
    }
    STATS_BEFORE(_PyFunction_FastCallDict)
    PyObject* result = (PyObject*) __target___PyFunction_FastCallDict(a, b, c, d);
    STATS_AFTER(_PyFunction_FastCallDict)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyFunction_Vectorcall, _PyFunction_FastCallDict)
PyObject* (*__target___PyFunction_Vectorcall)(PyObject*, PyObject*const*, size_t, PyObject*) = NULL;
PyAPI_FUNC(PyObject*) _PyFunction_Vectorcall(PyObject* a, PyObject*const* b, size_t c, PyObject* d) {
    LOG("0x%lx 0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c, (unsigned long) d);
    if (__target___PyFunction_Vectorcall == NULL) {
        __target___PyFunction_Vectorcall = resolveAPI("_PyFunction_Vectorcall");
    }
    STATS_BEFORE(_PyFunction_Vectorcall)
    PyObject* result = (PyObject*) __target___PyFunction_Vectorcall(a, b, c, d);
    STATS_AFTER(_PyFunction_Vectorcall)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyGILState_GetInterpreterStateUnsafe, _PyFunction_Vectorcall)
PyInterpreterState* (*__target___PyGILState_GetInterpreterStateUnsafe)() = NULL;
PyAPI_FUNC(PyInterpreterState*) _PyGILState_GetInterpreterStateUnsafe() {
    LOGS("");
    if (__target___PyGILState_GetInterpreterStateUnsafe == NULL) {
        __target___PyGILState_GetInterpreterStateUnsafe = resolveAPI("_PyGILState_GetInterpreterStateUnsafe");
    }
    STATS_BEFORE(_PyGILState_GetInterpreterStateUnsafe)
    PyInterpreterState* result = (PyInterpreterState*) __target___PyGILState_GetInterpreterStateUnsafe();
    STATS_AFTER(_PyGILState_GetInterpreterStateUnsafe)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyGen_FetchStopIterationValue, _PyGILState_GetInterpreterStateUnsafe)
int (*__target___PyGen_FetchStopIterationValue)(PyObject**) = NULL;
PyAPI_FUNC(int) _PyGen_FetchStopIterationValue(PyObject** a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target___PyGen_FetchStopIterationValue == NULL) {
        __target___PyGen_FetchStopIterationValue = resolveAPI("_PyGen_FetchStopIterationValue");
    }
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
    if (__target___PyGen_Finalize == NULL) {
        __target___PyGen_Finalize = resolveAPI("_PyGen_Finalize");
    }
    STATS_BEFORE(_PyGen_Finalize)
    __target___PyGen_Finalize(a);
    STATS_AFTER(_PyGen_Finalize)
    LOG_AFTER_VOID
}
STATS_CONTAINER(_PyGen_Send, _PyGen_Finalize)
PyObject* (*__target___PyGen_Send)(PyGenObject*, PyObject*) = NULL;
PyAPI_FUNC(PyObject*) _PyGen_Send(PyGenObject* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target___PyGen_Send == NULL) {
        __target___PyGen_Send = resolveAPI("_PyGen_Send");
    }
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
    if (__target___PyGen_SetStopIterationValue == NULL) {
        __target___PyGen_SetStopIterationValue = resolveAPI("_PyGen_SetStopIterationValue");
    }
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
    if (__target___PyGen_yf == NULL) {
        __target___PyGen_yf = resolveAPI("_PyGen_yf");
    }
    STATS_BEFORE(_PyGen_yf)
    PyObject* result = (PyObject*) __target___PyGen_yf(a);
    STATS_AFTER(_PyGen_yf)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyImport_AcquireLock, _PyGen_yf)
void (*__target___PyImport_AcquireLock)() = NULL;
PyAPI_FUNC(void) _PyImport_AcquireLock() {
    LOGS("");
    if (__target___PyImport_AcquireLock == NULL) {
        __target___PyImport_AcquireLock = resolveAPI("_PyImport_AcquireLock");
    }
    STATS_BEFORE(_PyImport_AcquireLock)
    __target___PyImport_AcquireLock();
    STATS_AFTER(_PyImport_AcquireLock)
    LOG_AFTER_VOID
}
STATS_CONTAINER(_PyImport_FindBuiltin, _PyImport_AcquireLock)
PyObject* (*__target___PyImport_FindBuiltin)(const char*, PyObject*) = NULL;
PyAPI_FUNC(PyObject*) _PyImport_FindBuiltin(const char* a, PyObject* b) {
    LOG("'%s'(0x%lx) 0x%lx", a?a:"<null>", (unsigned long) a, (unsigned long) b);
    if (__target___PyImport_FindBuiltin == NULL) {
        __target___PyImport_FindBuiltin = resolveAPI("_PyImport_FindBuiltin");
    }
    STATS_BEFORE(_PyImport_FindBuiltin)
    PyObject* result = (PyObject*) __target___PyImport_FindBuiltin(a, b);
    STATS_AFTER(_PyImport_FindBuiltin)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyImport_FindExtensionObject, _PyImport_FindBuiltin)
PyObject* (*__target___PyImport_FindExtensionObject)(PyObject*, PyObject*) = NULL;
PyAPI_FUNC(PyObject*) _PyImport_FindExtensionObject(PyObject* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target___PyImport_FindExtensionObject == NULL) {
        __target___PyImport_FindExtensionObject = resolveAPI("_PyImport_FindExtensionObject");
    }
    STATS_BEFORE(_PyImport_FindExtensionObject)
    PyObject* result = (PyObject*) __target___PyImport_FindExtensionObject(a, b);
    STATS_AFTER(_PyImport_FindExtensionObject)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyImport_FindExtensionObjectEx, _PyImport_FindExtensionObject)
PyObject* (*__target___PyImport_FindExtensionObjectEx)(PyObject*, PyObject*, PyObject*) = NULL;
PyAPI_FUNC(PyObject*) _PyImport_FindExtensionObjectEx(PyObject* a, PyObject* b, PyObject* c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    if (__target___PyImport_FindExtensionObjectEx == NULL) {
        __target___PyImport_FindExtensionObjectEx = resolveAPI("_PyImport_FindExtensionObjectEx");
    }
    STATS_BEFORE(_PyImport_FindExtensionObjectEx)
    PyObject* result = (PyObject*) __target___PyImport_FindExtensionObjectEx(a, b, c);
    STATS_AFTER(_PyImport_FindExtensionObjectEx)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyImport_FixupBuiltin, _PyImport_FindExtensionObjectEx)
int (*__target___PyImport_FixupBuiltin)(PyObject*, const char*, PyObject*) = NULL;
PyAPI_FUNC(int) _PyImport_FixupBuiltin(PyObject* a, const char* b, PyObject* c) {
    LOG("0x%lx '%s'(0x%lx) 0x%lx", (unsigned long) a, b?b:"<null>", (unsigned long) b, (unsigned long) c);
    if (__target___PyImport_FixupBuiltin == NULL) {
        __target___PyImport_FixupBuiltin = resolveAPI("_PyImport_FixupBuiltin");
    }
    STATS_BEFORE(_PyImport_FixupBuiltin)
    int result = (int) __target___PyImport_FixupBuiltin(a, b, c);
    STATS_AFTER(_PyImport_FixupBuiltin)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyImport_FixupExtensionObject, _PyImport_FixupBuiltin)
int (*__target___PyImport_FixupExtensionObject)(PyObject*, PyObject*, PyObject*, PyObject*) = NULL;
PyAPI_FUNC(int) _PyImport_FixupExtensionObject(PyObject* a, PyObject* b, PyObject* c, PyObject* d) {
    LOG("0x%lx 0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c, (unsigned long) d);
    if (__target___PyImport_FixupExtensionObject == NULL) {
        __target___PyImport_FixupExtensionObject = resolveAPI("_PyImport_FixupExtensionObject");
    }
    STATS_BEFORE(_PyImport_FixupExtensionObject)
    int result = (int) __target___PyImport_FixupExtensionObject(a, b, c, d);
    STATS_AFTER(_PyImport_FixupExtensionObject)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyImport_GetModuleAttr, _PyImport_FixupExtensionObject)
PyObject* (*__target___PyImport_GetModuleAttr)(PyObject*, PyObject*) = NULL;
PyAPI_FUNC(PyObject*) _PyImport_GetModuleAttr(PyObject* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target___PyImport_GetModuleAttr == NULL) {
        __target___PyImport_GetModuleAttr = resolveAPI("_PyImport_GetModuleAttr");
    }
    STATS_BEFORE(_PyImport_GetModuleAttr)
    PyObject* result = (PyObject*) __target___PyImport_GetModuleAttr(a, b);
    STATS_AFTER(_PyImport_GetModuleAttr)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyImport_GetModuleAttrString, _PyImport_GetModuleAttr)
PyObject* (*__target___PyImport_GetModuleAttrString)(const char*, const char*) = NULL;
PyAPI_FUNC(PyObject*) _PyImport_GetModuleAttrString(const char* a, const char* b) {
    LOG("'%s'(0x%lx) '%s'(0x%lx)", a?a:"<null>", (unsigned long) a, b?b:"<null>", (unsigned long) b);
    if (__target___PyImport_GetModuleAttrString == NULL) {
        __target___PyImport_GetModuleAttrString = resolveAPI("_PyImport_GetModuleAttrString");
    }
    STATS_BEFORE(_PyImport_GetModuleAttrString)
    PyObject* result = (PyObject*) __target___PyImport_GetModuleAttrString(a, b);
    STATS_AFTER(_PyImport_GetModuleAttrString)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyImport_GetModuleId, _PyImport_GetModuleAttrString)
PyObject* (*__target___PyImport_GetModuleId)(struct _Py_Identifier*) = NULL;
PyAPI_FUNC(PyObject*) _PyImport_GetModuleId(struct _Py_Identifier* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target___PyImport_GetModuleId == NULL) {
        __target___PyImport_GetModuleId = resolveAPI("_PyImport_GetModuleId");
    }
    STATS_BEFORE(_PyImport_GetModuleId)
    PyObject* result = (PyObject*) __target___PyImport_GetModuleId(a);
    STATS_AFTER(_PyImport_GetModuleId)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyImport_IsInitialized, _PyImport_GetModuleId)
int (*__target___PyImport_IsInitialized)(PyInterpreterState*) = NULL;
PyAPI_FUNC(int) _PyImport_IsInitialized(PyInterpreterState* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target___PyImport_IsInitialized == NULL) {
        __target___PyImport_IsInitialized = resolveAPI("_PyImport_IsInitialized");
    }
    STATS_BEFORE(_PyImport_IsInitialized)
    int result = (int) __target___PyImport_IsInitialized(a);
    STATS_AFTER(_PyImport_IsInitialized)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyImport_ReInitLock, _PyImport_IsInitialized)
void (*__target___PyImport_ReInitLock)() = NULL;
PyAPI_FUNC(void) _PyImport_ReInitLock() {
    LOGS("");
    if (__target___PyImport_ReInitLock == NULL) {
        __target___PyImport_ReInitLock = resolveAPI("_PyImport_ReInitLock");
    }
    STATS_BEFORE(_PyImport_ReInitLock)
    __target___PyImport_ReInitLock();
    STATS_AFTER(_PyImport_ReInitLock)
    LOG_AFTER_VOID
}
STATS_CONTAINER(_PyImport_ReleaseLock, _PyImport_ReInitLock)
int (*__target___PyImport_ReleaseLock)() = NULL;
PyAPI_FUNC(int) _PyImport_ReleaseLock() {
    LOGS("");
    if (__target___PyImport_ReleaseLock == NULL) {
        __target___PyImport_ReleaseLock = resolveAPI("_PyImport_ReleaseLock");
    }
    STATS_BEFORE(_PyImport_ReleaseLock)
    int result = (int) __target___PyImport_ReleaseLock();
    STATS_AFTER(_PyImport_ReleaseLock)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyImport_SetModule, _PyImport_ReleaseLock)
int (*__target___PyImport_SetModule)(PyObject*, PyObject*) = NULL;
PyAPI_FUNC(int) _PyImport_SetModule(PyObject* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target___PyImport_SetModule == NULL) {
        __target___PyImport_SetModule = resolveAPI("_PyImport_SetModule");
    }
    STATS_BEFORE(_PyImport_SetModule)
    int result = (int) __target___PyImport_SetModule(a, b);
    STATS_AFTER(_PyImport_SetModule)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyImport_SetModuleString, _PyImport_SetModule)
int (*__target___PyImport_SetModuleString)(const char*, PyObject*) = NULL;
PyAPI_FUNC(int) _PyImport_SetModuleString(const char* a, PyObject* b) {
    LOG("'%s'(0x%lx) 0x%lx", a?a:"<null>", (unsigned long) a, (unsigned long) b);
    if (__target___PyImport_SetModuleString == NULL) {
        __target___PyImport_SetModuleString = resolveAPI("_PyImport_SetModuleString");
    }
    STATS_BEFORE(_PyImport_SetModuleString)
    int result = (int) __target___PyImport_SetModuleString(a, b);
    STATS_AFTER(_PyImport_SetModuleString)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyInterpreterState_Get, _PyImport_SetModuleString)
PyInterpreterState* (*__target___PyInterpreterState_Get)() = NULL;
PyAPI_FUNC(PyInterpreterState*) _PyInterpreterState_Get() {
    LOGS("");
    if (__target___PyInterpreterState_Get == NULL) {
        __target___PyInterpreterState_Get = resolveAPI("_PyInterpreterState_Get");
    }
    STATS_BEFORE(_PyInterpreterState_Get)
    PyInterpreterState* result = (PyInterpreterState*) __target___PyInterpreterState_Get();
    STATS_AFTER(_PyInterpreterState_Get)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyInterpreterState_GetConfig, _PyInterpreterState_Get)
const PyConfig* (*__target___PyInterpreterState_GetConfig)(PyInterpreterState*) = NULL;
PyAPI_FUNC(const PyConfig*) _PyInterpreterState_GetConfig(PyInterpreterState* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target___PyInterpreterState_GetConfig == NULL) {
        __target___PyInterpreterState_GetConfig = resolveAPI("_PyInterpreterState_GetConfig");
    }
    STATS_BEFORE(_PyInterpreterState_GetConfig)
    const PyConfig* result = (const PyConfig*) __target___PyInterpreterState_GetConfig(a);
    STATS_AFTER(_PyInterpreterState_GetConfig)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyInterpreterState_GetConfigCopy, _PyInterpreterState_GetConfig)
int (*__target___PyInterpreterState_GetConfigCopy)(PyConfig*) = NULL;
PyAPI_FUNC(int) _PyInterpreterState_GetConfigCopy(PyConfig* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target___PyInterpreterState_GetConfigCopy == NULL) {
        __target___PyInterpreterState_GetConfigCopy = resolveAPI("_PyInterpreterState_GetConfigCopy");
    }
    STATS_BEFORE(_PyInterpreterState_GetConfigCopy)
    int result = (int) __target___PyInterpreterState_GetConfigCopy(a);
    STATS_AFTER(_PyInterpreterState_GetConfigCopy)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyInterpreterState_GetEvalFrameFunc, _PyInterpreterState_GetConfigCopy)
_PyFrameEvalFunction (*__target___PyInterpreterState_GetEvalFrameFunc)(PyInterpreterState*) = NULL;
PyAPI_FUNC(_PyFrameEvalFunction) _PyInterpreterState_GetEvalFrameFunc(PyInterpreterState* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target___PyInterpreterState_GetEvalFrameFunc == NULL) {
        __target___PyInterpreterState_GetEvalFrameFunc = resolveAPI("_PyInterpreterState_GetEvalFrameFunc");
    }
    STATS_BEFORE(_PyInterpreterState_GetEvalFrameFunc)
    _PyFrameEvalFunction result = (_PyFrameEvalFunction) __target___PyInterpreterState_GetEvalFrameFunc(a);
    STATS_AFTER(_PyInterpreterState_GetEvalFrameFunc)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyInterpreterState_GetMainModule, _PyInterpreterState_GetEvalFrameFunc)
PyObject* (*__target___PyInterpreterState_GetMainModule)(PyInterpreterState*) = NULL;
PyAPI_FUNC(PyObject*) _PyInterpreterState_GetMainModule(PyInterpreterState* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target___PyInterpreterState_GetMainModule == NULL) {
        __target___PyInterpreterState_GetMainModule = resolveAPI("_PyInterpreterState_GetMainModule");
    }
    STATS_BEFORE(_PyInterpreterState_GetMainModule)
    PyObject* result = (PyObject*) __target___PyInterpreterState_GetMainModule(a);
    STATS_AFTER(_PyInterpreterState_GetMainModule)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyInterpreterState_RequireIDRef, _PyInterpreterState_GetMainModule)
void (*__target___PyInterpreterState_RequireIDRef)(PyInterpreterState*, int) = NULL;
PyAPI_FUNC(void) _PyInterpreterState_RequireIDRef(PyInterpreterState* a, int b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target___PyInterpreterState_RequireIDRef == NULL) {
        __target___PyInterpreterState_RequireIDRef = resolveAPI("_PyInterpreterState_RequireIDRef");
    }
    STATS_BEFORE(_PyInterpreterState_RequireIDRef)
    __target___PyInterpreterState_RequireIDRef(a, b);
    STATS_AFTER(_PyInterpreterState_RequireIDRef)
    LOG_AFTER_VOID
}
STATS_CONTAINER(_PyInterpreterState_RequiresIDRef, _PyInterpreterState_RequireIDRef)
int (*__target___PyInterpreterState_RequiresIDRef)(PyInterpreterState*) = NULL;
PyAPI_FUNC(int) _PyInterpreterState_RequiresIDRef(PyInterpreterState* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target___PyInterpreterState_RequiresIDRef == NULL) {
        __target___PyInterpreterState_RequiresIDRef = resolveAPI("_PyInterpreterState_RequiresIDRef");
    }
    STATS_BEFORE(_PyInterpreterState_RequiresIDRef)
    int result = (int) __target___PyInterpreterState_RequiresIDRef(a);
    STATS_AFTER(_PyInterpreterState_RequiresIDRef)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyInterpreterState_SetConfig, _PyInterpreterState_RequiresIDRef)
int (*__target___PyInterpreterState_SetConfig)(const PyConfig*) = NULL;
PyAPI_FUNC(int) _PyInterpreterState_SetConfig(const PyConfig* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target___PyInterpreterState_SetConfig == NULL) {
        __target___PyInterpreterState_SetConfig = resolveAPI("_PyInterpreterState_SetConfig");
    }
    STATS_BEFORE(_PyInterpreterState_SetConfig)
    int result = (int) __target___PyInterpreterState_SetConfig(a);
    STATS_AFTER(_PyInterpreterState_SetConfig)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyInterpreterState_SetEvalFrameFunc, _PyInterpreterState_SetConfig)
void (*__target___PyInterpreterState_SetEvalFrameFunc)(PyInterpreterState*, _PyFrameEvalFunction) = NULL;
PyAPI_FUNC(void) _PyInterpreterState_SetEvalFrameFunc(PyInterpreterState* a, _PyFrameEvalFunction b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target___PyInterpreterState_SetEvalFrameFunc == NULL) {
        __target___PyInterpreterState_SetEvalFrameFunc = resolveAPI("_PyInterpreterState_SetEvalFrameFunc");
    }
    STATS_BEFORE(_PyInterpreterState_SetEvalFrameFunc)
    __target___PyInterpreterState_SetEvalFrameFunc(a, b);
    STATS_AFTER(_PyInterpreterState_SetEvalFrameFunc)
    LOG_AFTER_VOID
}
STATS_CONTAINER(_PyList_DebugMallocStats, _PyInterpreterState_SetEvalFrameFunc)
void (*__target___PyList_DebugMallocStats)(FILE*) = NULL;
PyAPI_FUNC(void) _PyList_DebugMallocStats(FILE* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target___PyList_DebugMallocStats == NULL) {
        __target___PyList_DebugMallocStats = resolveAPI("_PyList_DebugMallocStats");
    }
    STATS_BEFORE(_PyList_DebugMallocStats)
    __target___PyList_DebugMallocStats(a);
    STATS_AFTER(_PyList_DebugMallocStats)
    LOG_AFTER_VOID
}
STATS_CONTAINER(_PyList_Extend, _PyList_DebugMallocStats)
PyObject* (*__target___PyList_Extend)(PyListObject*, PyObject*) = NULL;
PyAPI_FUNC(PyObject*) _PyList_Extend(PyListObject* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target___PyList_Extend == NULL) {
        __target___PyList_Extend = resolveAPI("_PyList_Extend");
    }
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
    if (__target___PyLong_AsByteArray == NULL) {
        __target___PyLong_AsByteArray = resolveAPI("_PyLong_AsByteArray");
    }
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
    if (__target___PyLong_AsInt == NULL) {
        __target___PyLong_AsInt = resolveAPI("_PyLong_AsInt");
    }
    STATS_BEFORE(_PyLong_AsInt)
    int result = (int) __target___PyLong_AsInt(a);
    STATS_AFTER(_PyLong_AsInt)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyLong_AsTime_t, _PyLong_AsInt)
time_t (*__target___PyLong_AsTime_t)(PyObject*) = NULL;
PyAPI_FUNC(time_t) _PyLong_AsTime_t(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target___PyLong_AsTime_t == NULL) {
        __target___PyLong_AsTime_t = resolveAPI("_PyLong_AsTime_t");
    }
    STATS_BEFORE(_PyLong_AsTime_t)
    time_t result = (time_t) __target___PyLong_AsTime_t(a);
    STATS_AFTER(_PyLong_AsTime_t)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyLong_Copy, _PyLong_AsTime_t)
PyObject* (*__target___PyLong_Copy)(PyLongObject*) = NULL;
PyAPI_FUNC(PyObject*) _PyLong_Copy(PyLongObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target___PyLong_Copy == NULL) {
        __target___PyLong_Copy = resolveAPI("_PyLong_Copy");
    }
    STATS_BEFORE(_PyLong_Copy)
    PyObject* result = (PyObject*) __target___PyLong_Copy(a);
    STATS_AFTER(_PyLong_Copy)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyLong_DivmodNear, _PyLong_Copy)
PyObject* (*__target___PyLong_DivmodNear)(PyObject*, PyObject*) = NULL;
PyAPI_FUNC(PyObject*) _PyLong_DivmodNear(PyObject* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target___PyLong_DivmodNear == NULL) {
        __target___PyLong_DivmodNear = resolveAPI("_PyLong_DivmodNear");
    }
    STATS_BEFORE(_PyLong_DivmodNear)
    PyObject* result = (PyObject*) __target___PyLong_DivmodNear(a, b);
    STATS_AFTER(_PyLong_DivmodNear)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyLong_FileDescriptor_Converter, _PyLong_DivmodNear)
int (*__target___PyLong_FileDescriptor_Converter)(PyObject*, void*) = NULL;
PyAPI_FUNC(int) _PyLong_FileDescriptor_Converter(PyObject* a, void* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target___PyLong_FileDescriptor_Converter == NULL) {
        __target___PyLong_FileDescriptor_Converter = resolveAPI("_PyLong_FileDescriptor_Converter");
    }
    STATS_BEFORE(_PyLong_FileDescriptor_Converter)
    int result = (int) __target___PyLong_FileDescriptor_Converter(a, b);
    STATS_AFTER(_PyLong_FileDescriptor_Converter)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyLong_Format, _PyLong_FileDescriptor_Converter)
PyObject* (*__target___PyLong_Format)(PyObject*, int) = NULL;
PyAPI_FUNC(PyObject*) _PyLong_Format(PyObject* a, int b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target___PyLong_Format == NULL) {
        __target___PyLong_Format = resolveAPI("_PyLong_Format");
    }
    STATS_BEFORE(_PyLong_Format)
    PyObject* result = (PyObject*) __target___PyLong_Format(a, b);
    STATS_AFTER(_PyLong_Format)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyLong_FormatAdvancedWriter, _PyLong_Format)
int (*__target___PyLong_FormatAdvancedWriter)(_PyUnicodeWriter*, PyObject*, PyObject*, Py_ssize_t, Py_ssize_t) = NULL;
PyAPI_FUNC(int) _PyLong_FormatAdvancedWriter(_PyUnicodeWriter* a, PyObject* b, PyObject* c, Py_ssize_t d, Py_ssize_t e) {
    LOG("0x%lx 0x%lx 0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c, (unsigned long) d, (unsigned long) e);
    if (__target___PyLong_FormatAdvancedWriter == NULL) {
        __target___PyLong_FormatAdvancedWriter = resolveAPI("_PyLong_FormatAdvancedWriter");
    }
    STATS_BEFORE(_PyLong_FormatAdvancedWriter)
    int result = (int) __target___PyLong_FormatAdvancedWriter(a, b, c, d, e);
    STATS_AFTER(_PyLong_FormatAdvancedWriter)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyLong_FormatBytesWriter, _PyLong_FormatAdvancedWriter)
char* (*__target___PyLong_FormatBytesWriter)(_PyBytesWriter*, char*, PyObject*, int, int) = NULL;
PyAPI_FUNC(char*) _PyLong_FormatBytesWriter(_PyBytesWriter* a, char* b, PyObject* c, int d, int e) {
    LOG("0x%lx 0x%lx 0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c, (unsigned long) d, (unsigned long) e);
    if (__target___PyLong_FormatBytesWriter == NULL) {
        __target___PyLong_FormatBytesWriter = resolveAPI("_PyLong_FormatBytesWriter");
    }
    STATS_BEFORE(_PyLong_FormatBytesWriter)
    char* result = (char*) __target___PyLong_FormatBytesWriter(a, b, c, d, e);
    STATS_AFTER(_PyLong_FormatBytesWriter)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyLong_FormatWriter, _PyLong_FormatBytesWriter)
int (*__target___PyLong_FormatWriter)(_PyUnicodeWriter*, PyObject*, int, int) = NULL;
PyAPI_FUNC(int) _PyLong_FormatWriter(_PyUnicodeWriter* a, PyObject* b, int c, int d) {
    LOG("0x%lx 0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c, (unsigned long) d);
    if (__target___PyLong_FormatWriter == NULL) {
        __target___PyLong_FormatWriter = resolveAPI("_PyLong_FormatWriter");
    }
    STATS_BEFORE(_PyLong_FormatWriter)
    int result = (int) __target___PyLong_FormatWriter(a, b, c, d);
    STATS_AFTER(_PyLong_FormatWriter)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyLong_Frexp, _PyLong_FormatWriter)
double (*__target___PyLong_Frexp)(PyLongObject*, Py_ssize_t*) = NULL;
PyAPI_FUNC(double) _PyLong_Frexp(PyLongObject* a, Py_ssize_t* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target___PyLong_Frexp == NULL) {
        __target___PyLong_Frexp = resolveAPI("_PyLong_Frexp");
    }
    STATS_BEFORE(_PyLong_Frexp)
    double result = (double) __target___PyLong_Frexp(a, b);
    STATS_AFTER(_PyLong_Frexp)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyLong_FromByteArray, _PyLong_Frexp)
PyObject* (*__target___PyLong_FromByteArray)(const unsigned char*, size_t, int, int) = NULL;
PyAPI_FUNC(PyObject*) _PyLong_FromByteArray(const unsigned char* a, size_t b, int c, int d) {
    LOG("0x%lx 0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c, (unsigned long) d);
    if (__target___PyLong_FromByteArray == NULL) {
        __target___PyLong_FromByteArray = resolveAPI("_PyLong_FromByteArray");
    }
    STATS_BEFORE(_PyLong_FromByteArray)
    PyObject* result = (PyObject*) __target___PyLong_FromByteArray(a, b, c, d);
    STATS_AFTER(_PyLong_FromByteArray)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyLong_FromBytes, _PyLong_FromByteArray)
PyObject* (*__target___PyLong_FromBytes)(const char*, Py_ssize_t, int) = NULL;
PyAPI_FUNC(PyObject*) _PyLong_FromBytes(const char* a, Py_ssize_t b, int c) {
    LOG("'%s'(0x%lx) 0x%lx 0x%lx", a?a:"<null>", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    if (__target___PyLong_FromBytes == NULL) {
        __target___PyLong_FromBytes = resolveAPI("_PyLong_FromBytes");
    }
    STATS_BEFORE(_PyLong_FromBytes)
    PyObject* result = (PyObject*) __target___PyLong_FromBytes(a, b, c);
    STATS_AFTER(_PyLong_FromBytes)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyLong_FromTime_t, _PyLong_FromBytes)
PyObject* (*__target___PyLong_FromTime_t)(time_t) = NULL;
PyAPI_FUNC(PyObject*) _PyLong_FromTime_t(time_t a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target___PyLong_FromTime_t == NULL) {
        __target___PyLong_FromTime_t = resolveAPI("_PyLong_FromTime_t");
    }
    STATS_BEFORE(_PyLong_FromTime_t)
    PyObject* result = (PyObject*) __target___PyLong_FromTime_t(a);
    STATS_AFTER(_PyLong_FromTime_t)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyLong_GCD, _PyLong_FromTime_t)
PyObject* (*__target___PyLong_GCD)(PyObject*, PyObject*) = NULL;
PyAPI_FUNC(PyObject*) _PyLong_GCD(PyObject* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target___PyLong_GCD == NULL) {
        __target___PyLong_GCD = resolveAPI("_PyLong_GCD");
    }
    STATS_BEFORE(_PyLong_GCD)
    PyObject* result = (PyObject*) __target___PyLong_GCD(a, b);
    STATS_AFTER(_PyLong_GCD)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyLong_Lshift, _PyLong_GCD)
PyObject* (*__target___PyLong_Lshift)(PyObject*, size_t) = NULL;
PyAPI_FUNC(PyObject*) _PyLong_Lshift(PyObject* a, size_t b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target___PyLong_Lshift == NULL) {
        __target___PyLong_Lshift = resolveAPI("_PyLong_Lshift");
    }
    STATS_BEFORE(_PyLong_Lshift)
    PyObject* result = (PyObject*) __target___PyLong_Lshift(a, b);
    STATS_AFTER(_PyLong_Lshift)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyLong_New, _PyLong_Lshift)
PyLongObject* (*__target___PyLong_New)(Py_ssize_t) = NULL;
PyAPI_FUNC(PyLongObject*) _PyLong_New(Py_ssize_t a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target___PyLong_New == NULL) {
        __target___PyLong_New = resolveAPI("_PyLong_New");
    }
    STATS_BEFORE(_PyLong_New)
    PyLongObject* result = (PyLongObject*) __target___PyLong_New(a);
    STATS_AFTER(_PyLong_New)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyLong_NumBits, _PyLong_New)
size_t (*__target___PyLong_NumBits)(PyObject*) = NULL;
PyAPI_FUNC(size_t) _PyLong_NumBits(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target___PyLong_NumBits == NULL) {
        __target___PyLong_NumBits = resolveAPI("_PyLong_NumBits");
    }
    STATS_BEFORE(_PyLong_NumBits)
    size_t result = (size_t) __target___PyLong_NumBits(a);
    STATS_AFTER(_PyLong_NumBits)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyLong_Rshift, _PyLong_NumBits)
PyObject* (*__target___PyLong_Rshift)(PyObject*, size_t) = NULL;
PyAPI_FUNC(PyObject*) _PyLong_Rshift(PyObject* a, size_t b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target___PyLong_Rshift == NULL) {
        __target___PyLong_Rshift = resolveAPI("_PyLong_Rshift");
    }
    STATS_BEFORE(_PyLong_Rshift)
    PyObject* result = (PyObject*) __target___PyLong_Rshift(a, b);
    STATS_AFTER(_PyLong_Rshift)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyLong_Sign, _PyLong_Rshift)
int (*__target___PyLong_Sign)(PyObject*) = NULL;
PyAPI_FUNC(int) _PyLong_Sign(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target___PyLong_Sign == NULL) {
        __target___PyLong_Sign = resolveAPI("_PyLong_Sign");
    }
    STATS_BEFORE(_PyLong_Sign)
    int result = (int) __target___PyLong_Sign(a);
    STATS_AFTER(_PyLong_Sign)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyLong_Size_t_Converter, _PyLong_Sign)
int (*__target___PyLong_Size_t_Converter)(PyObject*, void*) = NULL;
PyAPI_FUNC(int) _PyLong_Size_t_Converter(PyObject* a, void* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target___PyLong_Size_t_Converter == NULL) {
        __target___PyLong_Size_t_Converter = resolveAPI("_PyLong_Size_t_Converter");
    }
    STATS_BEFORE(_PyLong_Size_t_Converter)
    int result = (int) __target___PyLong_Size_t_Converter(a, b);
    STATS_AFTER(_PyLong_Size_t_Converter)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyLong_UnsignedInt_Converter, _PyLong_Size_t_Converter)
int (*__target___PyLong_UnsignedInt_Converter)(PyObject*, void*) = NULL;
PyAPI_FUNC(int) _PyLong_UnsignedInt_Converter(PyObject* a, void* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target___PyLong_UnsignedInt_Converter == NULL) {
        __target___PyLong_UnsignedInt_Converter = resolveAPI("_PyLong_UnsignedInt_Converter");
    }
    STATS_BEFORE(_PyLong_UnsignedInt_Converter)
    int result = (int) __target___PyLong_UnsignedInt_Converter(a, b);
    STATS_AFTER(_PyLong_UnsignedInt_Converter)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyLong_UnsignedLongLong_Converter, _PyLong_UnsignedInt_Converter)
int (*__target___PyLong_UnsignedLongLong_Converter)(PyObject*, void*) = NULL;
PyAPI_FUNC(int) _PyLong_UnsignedLongLong_Converter(PyObject* a, void* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target___PyLong_UnsignedLongLong_Converter == NULL) {
        __target___PyLong_UnsignedLongLong_Converter = resolveAPI("_PyLong_UnsignedLongLong_Converter");
    }
    STATS_BEFORE(_PyLong_UnsignedLongLong_Converter)
    int result = (int) __target___PyLong_UnsignedLongLong_Converter(a, b);
    STATS_AFTER(_PyLong_UnsignedLongLong_Converter)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyLong_UnsignedLong_Converter, _PyLong_UnsignedLongLong_Converter)
int (*__target___PyLong_UnsignedLong_Converter)(PyObject*, void*) = NULL;
PyAPI_FUNC(int) _PyLong_UnsignedLong_Converter(PyObject* a, void* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target___PyLong_UnsignedLong_Converter == NULL) {
        __target___PyLong_UnsignedLong_Converter = resolveAPI("_PyLong_UnsignedLong_Converter");
    }
    STATS_BEFORE(_PyLong_UnsignedLong_Converter)
    int result = (int) __target___PyLong_UnsignedLong_Converter(a, b);
    STATS_AFTER(_PyLong_UnsignedLong_Converter)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyLong_UnsignedShort_Converter, _PyLong_UnsignedLong_Converter)
int (*__target___PyLong_UnsignedShort_Converter)(PyObject*, void*) = NULL;
PyAPI_FUNC(int) _PyLong_UnsignedShort_Converter(PyObject* a, void* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target___PyLong_UnsignedShort_Converter == NULL) {
        __target___PyLong_UnsignedShort_Converter = resolveAPI("_PyLong_UnsignedShort_Converter");
    }
    STATS_BEFORE(_PyLong_UnsignedShort_Converter)
    int result = (int) __target___PyLong_UnsignedShort_Converter(a, b);
    STATS_AFTER(_PyLong_UnsignedShort_Converter)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyMem_GetCurrentAllocatorName, _PyLong_UnsignedShort_Converter)
const char* (*__target___PyMem_GetCurrentAllocatorName)() = NULL;
PyAPI_FUNC(const char*) _PyMem_GetCurrentAllocatorName() {
    LOGS("");
    if (__target___PyMem_GetCurrentAllocatorName == NULL) {
        __target___PyMem_GetCurrentAllocatorName = resolveAPI("_PyMem_GetCurrentAllocatorName");
    }
    STATS_BEFORE(_PyMem_GetCurrentAllocatorName)
    const char* result = (const char*) __target___PyMem_GetCurrentAllocatorName();
    STATS_AFTER(_PyMem_GetCurrentAllocatorName)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyMem_RawStrdup, _PyMem_GetCurrentAllocatorName)
char* (*__target___PyMem_RawStrdup)(const char*) = NULL;
PyAPI_FUNC(char*) _PyMem_RawStrdup(const char* a) {
    LOG("'%s'(0x%lx)", a?a:"<null>", (unsigned long) a);
    if (__target___PyMem_RawStrdup == NULL) {
        __target___PyMem_RawStrdup = resolveAPI("_PyMem_RawStrdup");
    }
    STATS_BEFORE(_PyMem_RawStrdup)
    char* result = (char*) __target___PyMem_RawStrdup(a);
    STATS_AFTER(_PyMem_RawStrdup)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyMem_RawWcsdup, _PyMem_RawStrdup)
wchar_t* (*__target___PyMem_RawWcsdup)(const wchar_t*) = NULL;
PyAPI_FUNC(wchar_t*) _PyMem_RawWcsdup(const wchar_t* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target___PyMem_RawWcsdup == NULL) {
        __target___PyMem_RawWcsdup = resolveAPI("_PyMem_RawWcsdup");
    }
    STATS_BEFORE(_PyMem_RawWcsdup)
    wchar_t* result = (wchar_t*) __target___PyMem_RawWcsdup(a);
    STATS_AFTER(_PyMem_RawWcsdup)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyMem_Strdup, _PyMem_RawWcsdup)
char* (*__target___PyMem_Strdup)(const char*) = NULL;
PyAPI_FUNC(char*) _PyMem_Strdup(const char* a) {
    LOG("'%s'(0x%lx)", a?a:"<null>", (unsigned long) a);
    if (__target___PyMem_Strdup == NULL) {
        __target___PyMem_Strdup = resolveAPI("_PyMem_Strdup");
    }
    STATS_BEFORE(_PyMem_Strdup)
    char* result = (char*) __target___PyMem_Strdup(a);
    STATS_AFTER(_PyMem_Strdup)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyMemoryView_GetBuffer, _PyMem_Strdup)
Py_buffer* (*__target___PyMemoryView_GetBuffer)(PyObject*) = NULL;
PyAPI_FUNC(Py_buffer*) _PyMemoryView_GetBuffer(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target___PyMemoryView_GetBuffer == NULL) {
        __target___PyMemoryView_GetBuffer = resolveAPI("_PyMemoryView_GetBuffer");
    }
    STATS_BEFORE(_PyMemoryView_GetBuffer)
    Py_buffer* result = (Py_buffer*) __target___PyMemoryView_GetBuffer(a);
    STATS_AFTER(_PyMemoryView_GetBuffer)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyMethodDef_RawFastCallDict, _PyMemoryView_GetBuffer)
PyObject* (*__target___PyMethodDef_RawFastCallDict)(PyMethodDef*, PyObject*, PyObject*const*, Py_ssize_t, PyObject*) = NULL;
PyAPI_FUNC(PyObject*) _PyMethodDef_RawFastCallDict(PyMethodDef* a, PyObject* b, PyObject*const* c, Py_ssize_t d, PyObject* e) {
    LOG("0x%lx 0x%lx 0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c, (unsigned long) d, (unsigned long) e);
    if (__target___PyMethodDef_RawFastCallDict == NULL) {
        __target___PyMethodDef_RawFastCallDict = resolveAPI("_PyMethodDef_RawFastCallDict");
    }
    STATS_BEFORE(_PyMethodDef_RawFastCallDict)
    PyObject* result = (PyObject*) __target___PyMethodDef_RawFastCallDict(a, b, c, d, e);
    STATS_AFTER(_PyMethodDef_RawFastCallDict)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyMethodDef_RawFastCallKeywords, _PyMethodDef_RawFastCallDict)
PyObject* (*__target___PyMethodDef_RawFastCallKeywords)(PyMethodDef*, PyObject*, PyObject*const*, Py_ssize_t, PyObject*) = NULL;
PyAPI_FUNC(PyObject*) _PyMethodDef_RawFastCallKeywords(PyMethodDef* a, PyObject* b, PyObject*const* c, Py_ssize_t d, PyObject* e) {
    LOG("0x%lx 0x%lx 0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c, (unsigned long) d, (unsigned long) e);
    if (__target___PyMethodDef_RawFastCallKeywords == NULL) {
        __target___PyMethodDef_RawFastCallKeywords = resolveAPI("_PyMethodDef_RawFastCallKeywords");
    }
    STATS_BEFORE(_PyMethodDef_RawFastCallKeywords)
    PyObject* result = (PyObject*) __target___PyMethodDef_RawFastCallKeywords(a, b, c, d, e);
    STATS_AFTER(_PyMethodDef_RawFastCallKeywords)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyMethod_DebugMallocStats, _PyMethodDef_RawFastCallKeywords)
void (*__target___PyMethod_DebugMallocStats)(FILE*) = NULL;
PyAPI_FUNC(void) _PyMethod_DebugMallocStats(FILE* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target___PyMethod_DebugMallocStats == NULL) {
        __target___PyMethod_DebugMallocStats = resolveAPI("_PyMethod_DebugMallocStats");
    }
    STATS_BEFORE(_PyMethod_DebugMallocStats)
    __target___PyMethod_DebugMallocStats(a);
    STATS_AFTER(_PyMethod_DebugMallocStats)
    LOG_AFTER_VOID
}
STATS_CONTAINER(_PyModuleSpec_IsInitializing, _PyMethod_DebugMallocStats)
int (*__target___PyModuleSpec_IsInitializing)(PyObject*) = NULL;
PyAPI_FUNC(int) _PyModuleSpec_IsInitializing(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target___PyModuleSpec_IsInitializing == NULL) {
        __target___PyModuleSpec_IsInitializing = resolveAPI("_PyModuleSpec_IsInitializing");
    }
    STATS_BEFORE(_PyModuleSpec_IsInitializing)
    int result = (int) __target___PyModuleSpec_IsInitializing(a);
    STATS_AFTER(_PyModuleSpec_IsInitializing)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyModule_Clear, _PyModuleSpec_IsInitializing)
void (*__target___PyModule_Clear)(PyObject*) = NULL;
PyAPI_FUNC(void) _PyModule_Clear(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target___PyModule_Clear == NULL) {
        __target___PyModule_Clear = resolveAPI("_PyModule_Clear");
    }
    STATS_BEFORE(_PyModule_Clear)
    __target___PyModule_Clear(a);
    STATS_AFTER(_PyModule_Clear)
    LOG_AFTER_VOID
}
STATS_CONTAINER(_PyModule_ClearDict, _PyModule_Clear)
void (*__target___PyModule_ClearDict)(PyObject*) = NULL;
PyAPI_FUNC(void) _PyModule_ClearDict(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target___PyModule_ClearDict == NULL) {
        __target___PyModule_ClearDict = resolveAPI("_PyModule_ClearDict");
    }
    STATS_BEFORE(_PyModule_ClearDict)
    __target___PyModule_ClearDict(a);
    STATS_AFTER(_PyModule_ClearDict)
    LOG_AFTER_VOID
}
STATS_CONTAINER(_PyModule_CreateInitialized, _PyModule_ClearDict)
PyObject* (*__target___PyModule_CreateInitialized)(struct PyModuleDef*, int) = NULL;
PyAPI_FUNC(PyObject*) _PyModule_CreateInitialized(struct PyModuleDef* a, int b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target___PyModule_CreateInitialized == NULL) {
        __target___PyModule_CreateInitialized = resolveAPI("_PyModule_CreateInitialized");
    }
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
    if (__target___PyModule_GetDef == NULL) {
        __target___PyModule_GetDef = resolveAPI("_PyModule_GetDef");
    }
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
    if (__target___PyModule_GetDict == NULL) {
        __target___PyModule_GetDict = resolveAPI("_PyModule_GetDict");
    }
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
    if (__target___PyModule_GetState == NULL) {
        __target___PyModule_GetState = resolveAPI("_PyModule_GetState");
    }
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
    if (__target___PyNamespace_New == NULL) {
        __target___PyNamespace_New = resolveAPI("_PyNamespace_New");
    }
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
    if (__target___PyNumber_Index == NULL) {
        __target___PyNumber_Index = resolveAPI("_PyNumber_Index");
    }
    STATS_BEFORE(_PyNumber_Index)
    PyObject* result = (PyObject*) __target___PyNumber_Index(a);
    STATS_AFTER(_PyNumber_Index)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyOS_IsMainThread, _PyNumber_Index)
int (*__target___PyOS_IsMainThread)() = NULL;
PyAPI_FUNC(int) _PyOS_IsMainThread() {
    LOGS("");
    if (__target___PyOS_IsMainThread == NULL) {
        __target___PyOS_IsMainThread = resolveAPI("_PyOS_IsMainThread");
    }
    STATS_BEFORE(_PyOS_IsMainThread)
    int result = (int) __target___PyOS_IsMainThread();
    STATS_AFTER(_PyOS_IsMainThread)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyOS_URandom, _PyOS_IsMainThread)
int (*__target___PyOS_URandom)(void*, Py_ssize_t) = NULL;
PyAPI_FUNC(int) _PyOS_URandom(void* a, Py_ssize_t b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target___PyOS_URandom == NULL) {
        __target___PyOS_URandom = resolveAPI("_PyOS_URandom");
    }
    STATS_BEFORE(_PyOS_URandom)
    int result = (int) __target___PyOS_URandom(a, b);
    STATS_AFTER(_PyOS_URandom)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyOS_URandomNonblock, _PyOS_URandom)
int (*__target___PyOS_URandomNonblock)(void*, Py_ssize_t) = NULL;
PyAPI_FUNC(int) _PyOS_URandomNonblock(void* a, Py_ssize_t b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target___PyOS_URandomNonblock == NULL) {
        __target___PyOS_URandomNonblock = resolveAPI("_PyOS_URandomNonblock");
    }
    STATS_BEFORE(_PyOS_URandomNonblock)
    int result = (int) __target___PyOS_URandomNonblock(a, b);
    STATS_AFTER(_PyOS_URandomNonblock)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyObjectDict_SetItem, _PyOS_URandomNonblock)
int (*__target___PyObjectDict_SetItem)(PyTypeObject*, PyObject**, PyObject*, PyObject*) = NULL;
PyAPI_FUNC(int) _PyObjectDict_SetItem(PyTypeObject* a, PyObject** b, PyObject* c, PyObject* d) {
    LOG("0x%lx 0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c, (unsigned long) d);
    if (__target___PyObjectDict_SetItem == NULL) {
        __target___PyObjectDict_SetItem = resolveAPI("_PyObjectDict_SetItem");
    }
    STATS_BEFORE(_PyObjectDict_SetItem)
    int result = (int) __target___PyObjectDict_SetItem(a, b, c, d);
    STATS_AFTER(_PyObjectDict_SetItem)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyObject_AssertFailed, _PyObjectDict_SetItem)
void (*__target___PyObject_AssertFailed)(PyObject*, const char*, const char*, const char*, int, const char*) = NULL;
PyAPI_FUNC(void) _PyObject_AssertFailed(PyObject* a, const char* b, const char* c, const char* d, int e, const char* f) {
    LOG("0x%lx '%s'(0x%lx) '%s'(0x%lx) '%s'(0x%lx) 0x%lx '%s'(0x%lx)", (unsigned long) a, b?b:"<null>", (unsigned long) b, c?c:"<null>", (unsigned long) c, d?d:"<null>", (unsigned long) d, (unsigned long) e, f?f:"<null>", (unsigned long) f);
    if (__target___PyObject_AssertFailed == NULL) {
        __target___PyObject_AssertFailed = resolveAPI("_PyObject_AssertFailed");
    }
    STATS_BEFORE(_PyObject_AssertFailed)
    __target___PyObject_AssertFailed(a, b, c, d, e, f);
    STATS_AFTER(_PyObject_AssertFailed)
    LOG_AFTER_VOID
    abort();
}
STATS_CONTAINER(_PyObject_Call1, _PyObject_AssertFailed)
PyObject* (*__target___PyObject_Call1)(PyObject*, PyObject*, PyObject*, int) = NULL;
PyAPI_FUNC(PyObject*) _PyObject_Call1(PyObject* a, PyObject* b, PyObject* c, int d) {
    LOG("0x%lx 0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c, (unsigned long) d);
    if (__target___PyObject_Call1 == NULL) {
        __target___PyObject_Call1 = resolveAPI("_PyObject_Call1");
    }
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
    if (__target___PyObject_CallMethod1 == NULL) {
        __target___PyObject_CallMethod1 = resolveAPI("_PyObject_CallMethod1");
    }
    STATS_BEFORE(_PyObject_CallMethod1)
    PyObject* result = (PyObject*) __target___PyObject_CallMethod1(a, b, c, d);
    STATS_AFTER(_PyObject_CallMethod1)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyObject_Call_Prepend, _PyObject_CallMethod1)
PyObject* (*__target___PyObject_Call_Prepend)(PyObject*, PyObject*, PyObject*, PyObject*) = NULL;
PyAPI_FUNC(PyObject*) _PyObject_Call_Prepend(PyObject* a, PyObject* b, PyObject* c, PyObject* d) {
    LOG("0x%lx 0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c, (unsigned long) d);
    if (__target___PyObject_Call_Prepend == NULL) {
        __target___PyObject_Call_Prepend = resolveAPI("_PyObject_Call_Prepend");
    }
    STATS_BEFORE(_PyObject_Call_Prepend)
    PyObject* result = (PyObject*) __target___PyObject_Call_Prepend(a, b, c, d);
    STATS_AFTER(_PyObject_Call_Prepend)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyObject_CheckConsistency, _PyObject_Call_Prepend)
int (*__target___PyObject_CheckConsistency)(PyObject*, int) = NULL;
PyAPI_FUNC(int) _PyObject_CheckConsistency(PyObject* a, int b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target___PyObject_CheckConsistency == NULL) {
        __target___PyObject_CheckConsistency = resolveAPI("_PyObject_CheckConsistency");
    }
    STATS_BEFORE(_PyObject_CheckConsistency)
    int result = (int) __target___PyObject_CheckConsistency(a, b);
    STATS_AFTER(_PyObject_CheckConsistency)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyObject_CheckCrossInterpreterData, _PyObject_CheckConsistency)
int (*__target___PyObject_CheckCrossInterpreterData)(PyObject*) = NULL;
PyAPI_FUNC(int) _PyObject_CheckCrossInterpreterData(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target___PyObject_CheckCrossInterpreterData == NULL) {
        __target___PyObject_CheckCrossInterpreterData = resolveAPI("_PyObject_CheckCrossInterpreterData");
    }
    STATS_BEFORE(_PyObject_CheckCrossInterpreterData)
    int result = (int) __target___PyObject_CheckCrossInterpreterData(a);
    STATS_AFTER(_PyObject_CheckCrossInterpreterData)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyObject_DebugMallocStats, _PyObject_CheckCrossInterpreterData)
int (*__target___PyObject_DebugMallocStats)(FILE*) = NULL;
PyAPI_FUNC(int) _PyObject_DebugMallocStats(FILE* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target___PyObject_DebugMallocStats == NULL) {
        __target___PyObject_DebugMallocStats = resolveAPI("_PyObject_DebugMallocStats");
    }
    STATS_BEFORE(_PyObject_DebugMallocStats)
    int result = (int) __target___PyObject_DebugMallocStats(a);
    STATS_AFTER(_PyObject_DebugMallocStats)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyObject_DebugTypeStats, _PyObject_DebugMallocStats)
void (*__target___PyObject_DebugTypeStats)(FILE*) = NULL;
PyAPI_FUNC(void) _PyObject_DebugTypeStats(FILE* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target___PyObject_DebugTypeStats == NULL) {
        __target___PyObject_DebugTypeStats = resolveAPI("_PyObject_DebugTypeStats");
    }
    STATS_BEFORE(_PyObject_DebugTypeStats)
    __target___PyObject_DebugTypeStats(a);
    STATS_AFTER(_PyObject_DebugTypeStats)
    LOG_AFTER_VOID
}
STATS_CONTAINER(_PyObject_Dump, _PyObject_DebugTypeStats)
void (*__target___PyObject_Dump)(PyObject*) = NULL;
PyAPI_FUNC(void) _PyObject_Dump(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target___PyObject_Dump == NULL) {
        __target___PyObject_Dump = resolveAPI("_PyObject_Dump");
    }
    STATS_BEFORE(_PyObject_Dump)
    __target___PyObject_Dump(a);
    STATS_AFTER(_PyObject_Dump)
    LOG_AFTER_VOID
}
STATS_CONTAINER(_PyObject_FastCallDict, _PyObject_Dump)
PyObject* (*__target___PyObject_FastCallDict)(PyObject*, PyObject*const*, size_t, PyObject*) = NULL;
PyAPI_FUNC(PyObject*) _PyObject_FastCallDict(PyObject* a, PyObject*const* b, size_t c, PyObject* d) {
    LOG("0x%lx 0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c, (unsigned long) d);
    if (__target___PyObject_FastCallDict == NULL) {
        __target___PyObject_FastCallDict = resolveAPI("_PyObject_FastCallDict");
    }
    STATS_BEFORE(_PyObject_FastCallDict)
    PyObject* result = (PyObject*) __target___PyObject_FastCallDict(a, b, c, d);
    STATS_AFTER(_PyObject_FastCallDict)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyObject_FastCall_Prepend, _PyObject_FastCallDict)
PyObject* (*__target___PyObject_FastCall_Prepend)(PyObject*, PyObject*, PyObject*const*, Py_ssize_t) = NULL;
PyAPI_FUNC(PyObject*) _PyObject_FastCall_Prepend(PyObject* a, PyObject* b, PyObject*const* c, Py_ssize_t d) {
    LOG("0x%lx 0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c, (unsigned long) d);
    if (__target___PyObject_FastCall_Prepend == NULL) {
        __target___PyObject_FastCall_Prepend = resolveAPI("_PyObject_FastCall_Prepend");
    }
    STATS_BEFORE(_PyObject_FastCall_Prepend)
    PyObject* result = (PyObject*) __target___PyObject_FastCall_Prepend(a, b, c, d);
    STATS_AFTER(_PyObject_FastCall_Prepend)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyObject_FunctionStr, _PyObject_FastCall_Prepend)
PyObject* (*__target___PyObject_FunctionStr)(PyObject*) = NULL;
PyAPI_FUNC(PyObject*) _PyObject_FunctionStr(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target___PyObject_FunctionStr == NULL) {
        __target___PyObject_FunctionStr = resolveAPI("_PyObject_FunctionStr");
    }
    STATS_BEFORE(_PyObject_FunctionStr)
    PyObject* result = (PyObject*) __target___PyObject_FunctionStr(a);
    STATS_AFTER(_PyObject_FunctionStr)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyObject_GC_Calloc, _PyObject_FunctionStr)
PyObject* (*__target___PyObject_GC_Calloc)(size_t) = NULL;
PyAPI_FUNC(PyObject*) _PyObject_GC_Calloc(size_t a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target___PyObject_GC_Calloc == NULL) {
        __target___PyObject_GC_Calloc = resolveAPI("_PyObject_GC_Calloc");
    }
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
    if (__target___PyObject_GC_Malloc == NULL) {
        __target___PyObject_GC_Malloc = resolveAPI("_PyObject_GC_Malloc");
    }
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
    if (__target___PyObject_GC_New == NULL) {
        __target___PyObject_GC_New = resolveAPI("_PyObject_GC_New");
    }
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
    if (__target___PyObject_GC_NewVar == NULL) {
        __target___PyObject_GC_NewVar = resolveAPI("_PyObject_GC_NewVar");
    }
    STATS_BEFORE(_PyObject_GC_NewVar)
    PyVarObject* result = (PyVarObject*) __target___PyObject_GC_NewVar(a, b);
    STATS_AFTER(_PyObject_GC_NewVar)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyObject_GC_Resize, _PyObject_GC_NewVar)
PyVarObject* (*__target___PyObject_GC_Resize)(PyVarObject*, Py_ssize_t) = NULL;
PyAPI_FUNC(PyVarObject*) _PyObject_GC_Resize(PyVarObject* a, Py_ssize_t b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target___PyObject_GC_Resize == NULL) {
        __target___PyObject_GC_Resize = resolveAPI("_PyObject_GC_Resize");
    }
    STATS_BEFORE(_PyObject_GC_Resize)
    PyVarObject* result = (PyVarObject*) __target___PyObject_GC_Resize(a, b);
    STATS_AFTER(_PyObject_GC_Resize)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyObject_GenericGetAttrWithDict, _PyObject_GC_Resize)
PyObject* (*__target___PyObject_GenericGetAttrWithDict)(PyObject*, PyObject*, PyObject*, int) = NULL;
PyAPI_FUNC(PyObject*) _PyObject_GenericGetAttrWithDict(PyObject* a, PyObject* b, PyObject* c, int d) {
    LOG("0x%lx 0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c, (unsigned long) d);
    if (__target___PyObject_GenericGetAttrWithDict == NULL) {
        __target___PyObject_GenericGetAttrWithDict = resolveAPI("_PyObject_GenericGetAttrWithDict");
    }
    STATS_BEFORE(_PyObject_GenericGetAttrWithDict)
    PyObject* result = (PyObject*) __target___PyObject_GenericGetAttrWithDict(a, b, c, d);
    STATS_AFTER(_PyObject_GenericGetAttrWithDict)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyObject_GenericSetAttrWithDict, _PyObject_GenericGetAttrWithDict)
int (*__target___PyObject_GenericSetAttrWithDict)(PyObject*, PyObject*, PyObject*, PyObject*) = NULL;
PyAPI_FUNC(int) _PyObject_GenericSetAttrWithDict(PyObject* a, PyObject* b, PyObject* c, PyObject* d) {
    LOG("0x%lx 0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c, (unsigned long) d);
    if (__target___PyObject_GenericSetAttrWithDict == NULL) {
        __target___PyObject_GenericSetAttrWithDict = resolveAPI("_PyObject_GenericSetAttrWithDict");
    }
    STATS_BEFORE(_PyObject_GenericSetAttrWithDict)
    int result = (int) __target___PyObject_GenericSetAttrWithDict(a, b, c, d);
    STATS_AFTER(_PyObject_GenericSetAttrWithDict)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyObject_GetAttrId, _PyObject_GenericSetAttrWithDict)
PyObject* (*__target___PyObject_GetAttrId)(PyObject*, struct _Py_Identifier*) = NULL;
PyAPI_FUNC(PyObject*) _PyObject_GetAttrId(PyObject* a, struct _Py_Identifier* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target___PyObject_GetAttrId == NULL) {
        __target___PyObject_GetAttrId = resolveAPI("_PyObject_GetAttrId");
    }
    STATS_BEFORE(_PyObject_GetAttrId)
    PyObject* result = (PyObject*) __target___PyObject_GetAttrId(a, b);
    STATS_AFTER(_PyObject_GetAttrId)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyObject_GetCrossInterpreterData, _PyObject_GetAttrId)
int (*__target___PyObject_GetCrossInterpreterData)(PyObject*, _PyCrossInterpreterData*) = NULL;
PyAPI_FUNC(int) _PyObject_GetCrossInterpreterData(PyObject* a, _PyCrossInterpreterData* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target___PyObject_GetCrossInterpreterData == NULL) {
        __target___PyObject_GetCrossInterpreterData = resolveAPI("_PyObject_GetCrossInterpreterData");
    }
    STATS_BEFORE(_PyObject_GetCrossInterpreterData)
    int result = (int) __target___PyObject_GetCrossInterpreterData(a, b);
    STATS_AFTER(_PyObject_GetCrossInterpreterData)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyObject_GetMethod, _PyObject_GetCrossInterpreterData)
int (*__target___PyObject_GetMethod)(PyObject*, PyObject*, PyObject**) = NULL;
PyAPI_FUNC(int) _PyObject_GetMethod(PyObject* a, PyObject* b, PyObject** c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    if (__target___PyObject_GetMethod == NULL) {
        __target___PyObject_GetMethod = resolveAPI("_PyObject_GetMethod");
    }
    STATS_BEFORE(_PyObject_GetMethod)
    int result = (int) __target___PyObject_GetMethod(a, b, c);
    STATS_AFTER(_PyObject_GetMethod)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyObject_HasAttrId, _PyObject_GetMethod)
int (*__target___PyObject_HasAttrId)(PyObject*, struct _Py_Identifier*) = NULL;
PyAPI_FUNC(int) _PyObject_HasAttrId(PyObject* a, struct _Py_Identifier* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target___PyObject_HasAttrId == NULL) {
        __target___PyObject_HasAttrId = resolveAPI("_PyObject_HasAttrId");
    }
    STATS_BEFORE(_PyObject_HasAttrId)
    int result = (int) __target___PyObject_HasAttrId(a, b);
    STATS_AFTER(_PyObject_HasAttrId)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyObject_HasLen, _PyObject_HasAttrId)
int (*__target___PyObject_HasLen)(PyObject*) = NULL;
PyAPI_FUNC(int) _PyObject_HasLen(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target___PyObject_HasLen == NULL) {
        __target___PyObject_HasLen = resolveAPI("_PyObject_HasLen");
    }
    STATS_BEFORE(_PyObject_HasLen)
    int result = (int) __target___PyObject_HasLen(a);
    STATS_AFTER(_PyObject_HasLen)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyObject_IsAbstract, _PyObject_HasLen)
int (*__target___PyObject_IsAbstract)(PyObject*) = NULL;
PyAPI_FUNC(int) _PyObject_IsAbstract(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target___PyObject_IsAbstract == NULL) {
        __target___PyObject_IsAbstract = resolveAPI("_PyObject_IsAbstract");
    }
    STATS_BEFORE(_PyObject_IsAbstract)
    int result = (int) __target___PyObject_IsAbstract(a);
    STATS_AFTER(_PyObject_IsAbstract)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyObject_IsFreed, _PyObject_IsAbstract)
int (*__target___PyObject_IsFreed)(PyObject*) = NULL;
PyAPI_FUNC(int) _PyObject_IsFreed(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target___PyObject_IsFreed == NULL) {
        __target___PyObject_IsFreed = resolveAPI("_PyObject_IsFreed");
    }
    STATS_BEFORE(_PyObject_IsFreed)
    int result = (int) __target___PyObject_IsFreed(a);
    STATS_AFTER(_PyObject_IsFreed)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyObject_LookupAttr, _PyObject_IsFreed)
int (*__target___PyObject_LookupAttr)(PyObject*, PyObject*, PyObject**) = NULL;
PyAPI_FUNC(int) _PyObject_LookupAttr(PyObject* a, PyObject* b, PyObject** c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    if (__target___PyObject_LookupAttr == NULL) {
        __target___PyObject_LookupAttr = resolveAPI("_PyObject_LookupAttr");
    }
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
    if (__target___PyObject_LookupAttrId == NULL) {
        __target___PyObject_LookupAttrId = resolveAPI("_PyObject_LookupAttrId");
    }
    STATS_BEFORE(_PyObject_LookupAttrId)
    int result = (int) __target___PyObject_LookupAttrId(a, b, c);
    STATS_AFTER(_PyObject_LookupAttrId)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyObject_LookupSpecial, _PyObject_LookupAttrId)
PyObject* (*__target___PyObject_LookupSpecial)(PyObject*, _Py_Identifier*) = NULL;
PyAPI_FUNC(PyObject*) _PyObject_LookupSpecial(PyObject* a, _Py_Identifier* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target___PyObject_LookupSpecial == NULL) {
        __target___PyObject_LookupSpecial = resolveAPI("_PyObject_LookupSpecial");
    }
    STATS_BEFORE(_PyObject_LookupSpecial)
    PyObject* result = (PyObject*) __target___PyObject_LookupSpecial(a, b);
    STATS_AFTER(_PyObject_LookupSpecial)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyObject_MakeTpCall, _PyObject_LookupSpecial)
PyObject* (*__target___PyObject_MakeTpCall)(PyThreadState*, PyObject*, PyObject*const*, Py_ssize_t, PyObject*) = NULL;
PyAPI_FUNC(PyObject*) _PyObject_MakeTpCall(PyThreadState* a, PyObject* b, PyObject*const* c, Py_ssize_t d, PyObject* e) {
    LOG("0x%lx 0x%lx 0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c, (unsigned long) d, (unsigned long) e);
    if (__target___PyObject_MakeTpCall == NULL) {
        __target___PyObject_MakeTpCall = resolveAPI("_PyObject_MakeTpCall");
    }
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
    if (__target___PyObject_New == NULL) {
        __target___PyObject_New = resolveAPI("_PyObject_New");
    }
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
    if (__target___PyObject_NewVar == NULL) {
        __target___PyObject_NewVar = resolveAPI("_PyObject_NewVar");
    }
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
    if (__target___PyObject_NextNotImplemented == NULL) {
        __target___PyObject_NextNotImplemented = resolveAPI("_PyObject_NextNotImplemented");
    }
    STATS_BEFORE(_PyObject_NextNotImplemented)
    PyObject* result = (PyObject*) __target___PyObject_NextNotImplemented(a);
    STATS_AFTER(_PyObject_NextNotImplemented)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyObject_RealIsInstance, _PyObject_NextNotImplemented)
int (*__target___PyObject_RealIsInstance)(PyObject*, PyObject*) = NULL;
PyAPI_FUNC(int) _PyObject_RealIsInstance(PyObject* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target___PyObject_RealIsInstance == NULL) {
        __target___PyObject_RealIsInstance = resolveAPI("_PyObject_RealIsInstance");
    }
    STATS_BEFORE(_PyObject_RealIsInstance)
    int result = (int) __target___PyObject_RealIsInstance(a, b);
    STATS_AFTER(_PyObject_RealIsInstance)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyObject_RealIsSubclass, _PyObject_RealIsInstance)
int (*__target___PyObject_RealIsSubclass)(PyObject*, PyObject*) = NULL;
PyAPI_FUNC(int) _PyObject_RealIsSubclass(PyObject* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target___PyObject_RealIsSubclass == NULL) {
        __target___PyObject_RealIsSubclass = resolveAPI("_PyObject_RealIsSubclass");
    }
    STATS_BEFORE(_PyObject_RealIsSubclass)
    int result = (int) __target___PyObject_RealIsSubclass(a, b);
    STATS_AFTER(_PyObject_RealIsSubclass)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyObject_SetAttrId, _PyObject_RealIsSubclass)
int (*__target___PyObject_SetAttrId)(PyObject*, struct _Py_Identifier*, PyObject*) = NULL;
PyAPI_FUNC(int) _PyObject_SetAttrId(PyObject* a, struct _Py_Identifier* b, PyObject* c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    if (__target___PyObject_SetAttrId == NULL) {
        __target___PyObject_SetAttrId = resolveAPI("_PyObject_SetAttrId");
    }
    STATS_BEFORE(_PyObject_SetAttrId)
    int result = (int) __target___PyObject_SetAttrId(a, b, c);
    STATS_AFTER(_PyObject_SetAttrId)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyRun_AnyFileObject, _PyObject_SetAttrId)
int (*__target___PyRun_AnyFileObject)(FILE*, PyObject*, int, PyCompilerFlags*) = NULL;
PyAPI_FUNC(int) _PyRun_AnyFileObject(FILE* a, PyObject* b, int c, PyCompilerFlags* d) {
    LOG("0x%lx 0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c, (unsigned long) d);
    if (__target___PyRun_AnyFileObject == NULL) {
        __target___PyRun_AnyFileObject = resolveAPI("_PyRun_AnyFileObject");
    }
    STATS_BEFORE(_PyRun_AnyFileObject)
    int result = (int) __target___PyRun_AnyFileObject(a, b, c, d);
    STATS_AFTER(_PyRun_AnyFileObject)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyRun_InteractiveLoopObject, _PyRun_AnyFileObject)
int (*__target___PyRun_InteractiveLoopObject)(FILE*, PyObject*, PyCompilerFlags*) = NULL;
PyAPI_FUNC(int) _PyRun_InteractiveLoopObject(FILE* a, PyObject* b, PyCompilerFlags* c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    if (__target___PyRun_InteractiveLoopObject == NULL) {
        __target___PyRun_InteractiveLoopObject = resolveAPI("_PyRun_InteractiveLoopObject");
    }
    STATS_BEFORE(_PyRun_InteractiveLoopObject)
    int result = (int) __target___PyRun_InteractiveLoopObject(a, b, c);
    STATS_AFTER(_PyRun_InteractiveLoopObject)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyRun_SimpleFileObject, _PyRun_InteractiveLoopObject)
int (*__target___PyRun_SimpleFileObject)(FILE*, PyObject*, int, PyCompilerFlags*) = NULL;
PyAPI_FUNC(int) _PyRun_SimpleFileObject(FILE* a, PyObject* b, int c, PyCompilerFlags* d) {
    LOG("0x%lx 0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c, (unsigned long) d);
    if (__target___PyRun_SimpleFileObject == NULL) {
        __target___PyRun_SimpleFileObject = resolveAPI("_PyRun_SimpleFileObject");
    }
    STATS_BEFORE(_PyRun_SimpleFileObject)
    int result = (int) __target___PyRun_SimpleFileObject(a, b, c, d);
    STATS_AFTER(_PyRun_SimpleFileObject)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PySequence_BytesToCharpArray, _PyRun_SimpleFileObject)
char*const* (*__target___PySequence_BytesToCharpArray)(PyObject*) = NULL;
PyAPI_FUNC(char*const*) _PySequence_BytesToCharpArray(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target___PySequence_BytesToCharpArray == NULL) {
        __target___PySequence_BytesToCharpArray = resolveAPI("_PySequence_BytesToCharpArray");
    }
    STATS_BEFORE(_PySequence_BytesToCharpArray)
    char*const* result = (char*const*) __target___PySequence_BytesToCharpArray(a);
    STATS_AFTER(_PySequence_BytesToCharpArray)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PySequence_Fast_ITEMS, _PySequence_BytesToCharpArray)
PyObject** (*__target___PySequence_Fast_ITEMS)(PyObject*) = NULL;
PyAPI_FUNC(PyObject**) _PySequence_Fast_ITEMS(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target___PySequence_Fast_ITEMS == NULL) {
        __target___PySequence_Fast_ITEMS = resolveAPI("_PySequence_Fast_ITEMS");
    }
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
    if (__target___PySequence_ITEM == NULL) {
        __target___PySequence_ITEM = resolveAPI("_PySequence_ITEM");
    }
    STATS_BEFORE(_PySequence_ITEM)
    PyObject* result = (PyObject*) __target___PySequence_ITEM(a, b);
    STATS_AFTER(_PySequence_ITEM)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PySequence_IterSearch, _PySequence_ITEM)
Py_ssize_t (*__target___PySequence_IterSearch)(PyObject*, PyObject*, int) = NULL;
PyAPI_FUNC(Py_ssize_t) _PySequence_IterSearch(PyObject* a, PyObject* b, int c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    if (__target___PySequence_IterSearch == NULL) {
        __target___PySequence_IterSearch = resolveAPI("_PySequence_IterSearch");
    }
    STATS_BEFORE(_PySequence_IterSearch)
    Py_ssize_t result = (Py_ssize_t) __target___PySequence_IterSearch(a, b, c);
    STATS_AFTER(_PySequence_IterSearch)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PySet_NextEntry, _PySequence_IterSearch)
int (*__target___PySet_NextEntry)(PyObject*, Py_ssize_t*, PyObject**, Py_hash_t*) = NULL;
PyAPI_FUNC(int) _PySet_NextEntry(PyObject* a, Py_ssize_t* b, PyObject** c, Py_hash_t* d) {
    LOG("0x%lx 0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c, (unsigned long) d);
    if (__target___PySet_NextEntry == NULL) {
        __target___PySet_NextEntry = resolveAPI("_PySet_NextEntry");
    }
    STATS_BEFORE(_PySet_NextEntry)
    int result = (int) __target___PySet_NextEntry(a, b, c, d);
    STATS_AFTER(_PySet_NextEntry)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PySet_Update, _PySet_NextEntry)
int (*__target___PySet_Update)(PyObject*, PyObject*) = NULL;
PyAPI_FUNC(int) _PySet_Update(PyObject* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target___PySet_Update == NULL) {
        __target___PySet_Update = resolveAPI("_PySet_Update");
    }
    STATS_BEFORE(_PySet_Update)
    int result = (int) __target___PySet_Update(a, b);
    STATS_AFTER(_PySet_Update)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PySignal_AfterFork, _PySet_Update)
void (*__target___PySignal_AfterFork)() = NULL;
PyAPI_FUNC(void) _PySignal_AfterFork() {
    LOGS("");
    if (__target___PySignal_AfterFork == NULL) {
        __target___PySignal_AfterFork = resolveAPI("_PySignal_AfterFork");
    }
    STATS_BEFORE(_PySignal_AfterFork)
    __target___PySignal_AfterFork();
    STATS_AFTER(_PySignal_AfterFork)
    LOG_AFTER_VOID
}
STATS_CONTAINER(_PySlice_FromIndices, _PySignal_AfterFork)
PyObject* (*__target___PySlice_FromIndices)(Py_ssize_t, Py_ssize_t) = NULL;
PyAPI_FUNC(PyObject*) _PySlice_FromIndices(Py_ssize_t a, Py_ssize_t b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target___PySlice_FromIndices == NULL) {
        __target___PySlice_FromIndices = resolveAPI("_PySlice_FromIndices");
    }
    STATS_BEFORE(_PySlice_FromIndices)
    PyObject* result = (PyObject*) __target___PySlice_FromIndices(a, b);
    STATS_AFTER(_PySlice_FromIndices)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PySlice_GetLongIndices, _PySlice_FromIndices)
int (*__target___PySlice_GetLongIndices)(PySliceObject*, PyObject*, PyObject**, PyObject**, PyObject**) = NULL;
PyAPI_FUNC(int) _PySlice_GetLongIndices(PySliceObject* a, PyObject* b, PyObject** c, PyObject** d, PyObject** e) {
    LOG("0x%lx 0x%lx 0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c, (unsigned long) d, (unsigned long) e);
    if (__target___PySlice_GetLongIndices == NULL) {
        __target___PySlice_GetLongIndices = resolveAPI("_PySlice_GetLongIndices");
    }
    STATS_BEFORE(_PySlice_GetLongIndices)
    int result = (int) __target___PySlice_GetLongIndices(a, b, c, d, e);
    STATS_AFTER(_PySlice_GetLongIndices)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyStack_AsDict, _PySlice_GetLongIndices)
PyObject* (*__target___PyStack_AsDict)(PyObject*const*, PyObject*) = NULL;
PyAPI_FUNC(PyObject*) _PyStack_AsDict(PyObject*const* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target___PyStack_AsDict == NULL) {
        __target___PyStack_AsDict = resolveAPI("_PyStack_AsDict");
    }
    STATS_BEFORE(_PyStack_AsDict)
    PyObject* result = (PyObject*) __target___PyStack_AsDict(a, b);
    STATS_AFTER(_PyStack_AsDict)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyStack_UnpackDict, _PyStack_AsDict)
int (*__target___PyStack_UnpackDict)(PyObject*const*, Py_ssize_t, PyObject*, PyObject*const**, PyObject**) = NULL;
PyAPI_FUNC(int) _PyStack_UnpackDict(PyObject*const* a, Py_ssize_t b, PyObject* c, PyObject*const** d, PyObject** e) {
    LOG("0x%lx 0x%lx 0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c, (unsigned long) d, (unsigned long) e);
    if (__target___PyStack_UnpackDict == NULL) {
        __target___PyStack_UnpackDict = resolveAPI("_PyStack_UnpackDict");
    }
    STATS_BEFORE(_PyStack_UnpackDict)
    int result = (int) __target___PyStack_UnpackDict(a, b, c, d, e);
    STATS_AFTER(_PyStack_UnpackDict)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyState_AddModule, _PyStack_UnpackDict)
int (*__target___PyState_AddModule)(PyObject*, struct PyModuleDef*) = NULL;
PyAPI_FUNC(int) _PyState_AddModule(PyObject* a, struct PyModuleDef* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target___PyState_AddModule == NULL) {
        __target___PyState_AddModule = resolveAPI("_PyState_AddModule");
    }
    STATS_BEFORE(_PyState_AddModule)
    int result = (int) __target___PyState_AddModule(a, b);
    STATS_AFTER(_PyState_AddModule)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PySys_GetObjectId, _PyState_AddModule)
PyObject* (*__target___PySys_GetObjectId)(_Py_Identifier*) = NULL;
PyAPI_FUNC(PyObject*) _PySys_GetObjectId(_Py_Identifier* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target___PySys_GetObjectId == NULL) {
        __target___PySys_GetObjectId = resolveAPI("_PySys_GetObjectId");
    }
    STATS_BEFORE(_PySys_GetObjectId)
    PyObject* result = (PyObject*) __target___PySys_GetObjectId(a);
    STATS_AFTER(_PySys_GetObjectId)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PySys_GetSizeOf, _PySys_GetObjectId)
size_t (*__target___PySys_GetSizeOf)(PyObject*) = NULL;
PyAPI_FUNC(size_t) _PySys_GetSizeOf(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target___PySys_GetSizeOf == NULL) {
        __target___PySys_GetSizeOf = resolveAPI("_PySys_GetSizeOf");
    }
    STATS_BEFORE(_PySys_GetSizeOf)
    size_t result = (size_t) __target___PySys_GetSizeOf(a);
    STATS_AFTER(_PySys_GetSizeOf)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PySys_SetObjectId, _PySys_GetSizeOf)
int (*__target___PySys_SetObjectId)(_Py_Identifier*, PyObject*) = NULL;
PyAPI_FUNC(int) _PySys_SetObjectId(_Py_Identifier* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target___PySys_SetObjectId == NULL) {
        __target___PySys_SetObjectId = resolveAPI("_PySys_SetObjectId");
    }
    STATS_BEFORE(_PySys_SetObjectId)
    int result = (int) __target___PySys_SetObjectId(a, b);
    STATS_AFTER(_PySys_SetObjectId)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyThreadState_GetDict, _PySys_SetObjectId)
PyObject* (*__target___PyThreadState_GetDict)(PyThreadState*) = NULL;
PyAPI_FUNC(PyObject*) _PyThreadState_GetDict(PyThreadState* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target___PyThreadState_GetDict == NULL) {
        __target___PyThreadState_GetDict = resolveAPI("_PyThreadState_GetDict");
    }
    STATS_BEFORE(_PyThreadState_GetDict)
    PyObject* result = (PyObject*) __target___PyThreadState_GetDict(a);
    STATS_AFTER(_PyThreadState_GetDict)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyThreadState_Prealloc, _PyThreadState_GetDict)
PyThreadState* (*__target___PyThreadState_Prealloc)(PyInterpreterState*) = NULL;
PyAPI_FUNC(PyThreadState*) _PyThreadState_Prealloc(PyInterpreterState* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target___PyThreadState_Prealloc == NULL) {
        __target___PyThreadState_Prealloc = resolveAPI("_PyThreadState_Prealloc");
    }
    STATS_BEFORE(_PyThreadState_Prealloc)
    PyThreadState* result = (PyThreadState*) __target___PyThreadState_Prealloc(a);
    STATS_AFTER(_PyThreadState_Prealloc)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyThreadState_UncheckedGet, _PyThreadState_Prealloc)
PyThreadState* (*__target___PyThreadState_UncheckedGet)() = NULL;
PyAPI_FUNC(PyThreadState*) _PyThreadState_UncheckedGet() {
    LOGS("");
    if (__target___PyThreadState_UncheckedGet == NULL) {
        __target___PyThreadState_UncheckedGet = resolveAPI("_PyThreadState_UncheckedGet");
    }
    STATS_BEFORE(_PyThreadState_UncheckedGet)
    PyThreadState* result = (PyThreadState*) __target___PyThreadState_UncheckedGet();
    STATS_AFTER(_PyThreadState_UncheckedGet)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyThread_CurrentExceptions, _PyThreadState_UncheckedGet)
PyObject* (*__target___PyThread_CurrentExceptions)() = NULL;
PyAPI_FUNC(PyObject*) _PyThread_CurrentExceptions() {
    LOGS("");
    if (__target___PyThread_CurrentExceptions == NULL) {
        __target___PyThread_CurrentExceptions = resolveAPI("_PyThread_CurrentExceptions");
    }
    STATS_BEFORE(_PyThread_CurrentExceptions)
    PyObject* result = (PyObject*) __target___PyThread_CurrentExceptions();
    STATS_AFTER(_PyThread_CurrentExceptions)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyThread_CurrentFrames, _PyThread_CurrentExceptions)
PyObject* (*__target___PyThread_CurrentFrames)() = NULL;
PyAPI_FUNC(PyObject*) _PyThread_CurrentFrames() {
    LOGS("");
    if (__target___PyThread_CurrentFrames == NULL) {
        __target___PyThread_CurrentFrames = resolveAPI("_PyThread_CurrentFrames");
    }
    STATS_BEFORE(_PyThread_CurrentFrames)
    PyObject* result = (PyObject*) __target___PyThread_CurrentFrames();
    STATS_AFTER(_PyThread_CurrentFrames)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyThread_at_fork_reinit, _PyThread_CurrentFrames)
int (*__target___PyThread_at_fork_reinit)(PyThread_type_lock*) = NULL;
PyAPI_FUNC(int) _PyThread_at_fork_reinit(PyThread_type_lock* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target___PyThread_at_fork_reinit == NULL) {
        __target___PyThread_at_fork_reinit = resolveAPI("_PyThread_at_fork_reinit");
    }
    STATS_BEFORE(_PyThread_at_fork_reinit)
    int result = (int) __target___PyThread_at_fork_reinit(a);
    STATS_AFTER(_PyThread_at_fork_reinit)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyTime_AsMicroseconds, _PyThread_at_fork_reinit)
_PyTime_t (*__target___PyTime_AsMicroseconds)(_PyTime_t, _PyTime_round_t) = NULL;
PyAPI_FUNC(_PyTime_t) _PyTime_AsMicroseconds(_PyTime_t a, _PyTime_round_t b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target___PyTime_AsMicroseconds == NULL) {
        __target___PyTime_AsMicroseconds = resolveAPI("_PyTime_AsMicroseconds");
    }
    STATS_BEFORE(_PyTime_AsMicroseconds)
    _PyTime_t result = (_PyTime_t) __target___PyTime_AsMicroseconds(a, b);
    STATS_AFTER(_PyTime_AsMicroseconds)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyTime_AsMilliseconds, _PyTime_AsMicroseconds)
_PyTime_t (*__target___PyTime_AsMilliseconds)(_PyTime_t, _PyTime_round_t) = NULL;
PyAPI_FUNC(_PyTime_t) _PyTime_AsMilliseconds(_PyTime_t a, _PyTime_round_t b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target___PyTime_AsMilliseconds == NULL) {
        __target___PyTime_AsMilliseconds = resolveAPI("_PyTime_AsMilliseconds");
    }
    STATS_BEFORE(_PyTime_AsMilliseconds)
    _PyTime_t result = (_PyTime_t) __target___PyTime_AsMilliseconds(a, b);
    STATS_AFTER(_PyTime_AsMilliseconds)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyTime_AsNanosecondsObject, _PyTime_AsMilliseconds)
PyObject* (*__target___PyTime_AsNanosecondsObject)(_PyTime_t) = NULL;
PyAPI_FUNC(PyObject*) _PyTime_AsNanosecondsObject(_PyTime_t a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target___PyTime_AsNanosecondsObject == NULL) {
        __target___PyTime_AsNanosecondsObject = resolveAPI("_PyTime_AsNanosecondsObject");
    }
    STATS_BEFORE(_PyTime_AsNanosecondsObject)
    PyObject* result = (PyObject*) __target___PyTime_AsNanosecondsObject(a);
    STATS_AFTER(_PyTime_AsNanosecondsObject)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyTime_AsSecondsDouble, _PyTime_AsNanosecondsObject)
double (*__target___PyTime_AsSecondsDouble)(_PyTime_t) = NULL;
PyAPI_FUNC(double) _PyTime_AsSecondsDouble(_PyTime_t a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target___PyTime_AsSecondsDouble == NULL) {
        __target___PyTime_AsSecondsDouble = resolveAPI("_PyTime_AsSecondsDouble");
    }
    STATS_BEFORE(_PyTime_AsSecondsDouble)
    double result = (double) __target___PyTime_AsSecondsDouble(a);
    STATS_AFTER(_PyTime_AsSecondsDouble)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyTime_AsTimespec, _PyTime_AsSecondsDouble)
int (*__target___PyTime_AsTimespec)(_PyTime_t, struct timespec*) = NULL;
PyAPI_FUNC(int) _PyTime_AsTimespec(_PyTime_t a, struct timespec* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target___PyTime_AsTimespec == NULL) {
        __target___PyTime_AsTimespec = resolveAPI("_PyTime_AsTimespec");
    }
    STATS_BEFORE(_PyTime_AsTimespec)
    int result = (int) __target___PyTime_AsTimespec(a, b);
    STATS_AFTER(_PyTime_AsTimespec)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyTime_AsTimeval, _PyTime_AsTimespec)
int (*__target___PyTime_AsTimeval)(_PyTime_t, struct timeval*, _PyTime_round_t) = NULL;
PyAPI_FUNC(int) _PyTime_AsTimeval(_PyTime_t a, struct timeval* b, _PyTime_round_t c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    if (__target___PyTime_AsTimeval == NULL) {
        __target___PyTime_AsTimeval = resolveAPI("_PyTime_AsTimeval");
    }
    STATS_BEFORE(_PyTime_AsTimeval)
    int result = (int) __target___PyTime_AsTimeval(a, b, c);
    STATS_AFTER(_PyTime_AsTimeval)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyTime_AsTimevalTime_t, _PyTime_AsTimeval)
int (*__target___PyTime_AsTimevalTime_t)(_PyTime_t, time_t*, int*, _PyTime_round_t) = NULL;
PyAPI_FUNC(int) _PyTime_AsTimevalTime_t(_PyTime_t a, time_t* b, int* c, _PyTime_round_t d) {
    LOG("0x%lx 0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c, (unsigned long) d);
    if (__target___PyTime_AsTimevalTime_t == NULL) {
        __target___PyTime_AsTimevalTime_t = resolveAPI("_PyTime_AsTimevalTime_t");
    }
    STATS_BEFORE(_PyTime_AsTimevalTime_t)
    int result = (int) __target___PyTime_AsTimevalTime_t(a, b, c, d);
    STATS_AFTER(_PyTime_AsTimevalTime_t)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyTime_AsTimeval_noraise, _PyTime_AsTimevalTime_t)
int (*__target___PyTime_AsTimeval_noraise)(_PyTime_t, struct timeval*, _PyTime_round_t) = NULL;
PyAPI_FUNC(int) _PyTime_AsTimeval_noraise(_PyTime_t a, struct timeval* b, _PyTime_round_t c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    if (__target___PyTime_AsTimeval_noraise == NULL) {
        __target___PyTime_AsTimeval_noraise = resolveAPI("_PyTime_AsTimeval_noraise");
    }
    STATS_BEFORE(_PyTime_AsTimeval_noraise)
    int result = (int) __target___PyTime_AsTimeval_noraise(a, b, c);
    STATS_AFTER(_PyTime_AsTimeval_noraise)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyTime_FromMillisecondsObject, _PyTime_AsTimeval_noraise)
int (*__target___PyTime_FromMillisecondsObject)(_PyTime_t*, PyObject*, _PyTime_round_t) = NULL;
PyAPI_FUNC(int) _PyTime_FromMillisecondsObject(_PyTime_t* a, PyObject* b, _PyTime_round_t c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    if (__target___PyTime_FromMillisecondsObject == NULL) {
        __target___PyTime_FromMillisecondsObject = resolveAPI("_PyTime_FromMillisecondsObject");
    }
    STATS_BEFORE(_PyTime_FromMillisecondsObject)
    int result = (int) __target___PyTime_FromMillisecondsObject(a, b, c);
    STATS_AFTER(_PyTime_FromMillisecondsObject)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyTime_FromNanoseconds, _PyTime_FromMillisecondsObject)
_PyTime_t (*__target___PyTime_FromNanoseconds)(_PyTime_t) = NULL;
PyAPI_FUNC(_PyTime_t) _PyTime_FromNanoseconds(_PyTime_t a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target___PyTime_FromNanoseconds == NULL) {
        __target___PyTime_FromNanoseconds = resolveAPI("_PyTime_FromNanoseconds");
    }
    STATS_BEFORE(_PyTime_FromNanoseconds)
    _PyTime_t result = (_PyTime_t) __target___PyTime_FromNanoseconds(a);
    STATS_AFTER(_PyTime_FromNanoseconds)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyTime_FromNanosecondsObject, _PyTime_FromNanoseconds)
int (*__target___PyTime_FromNanosecondsObject)(_PyTime_t*, PyObject*) = NULL;
PyAPI_FUNC(int) _PyTime_FromNanosecondsObject(_PyTime_t* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target___PyTime_FromNanosecondsObject == NULL) {
        __target___PyTime_FromNanosecondsObject = resolveAPI("_PyTime_FromNanosecondsObject");
    }
    STATS_BEFORE(_PyTime_FromNanosecondsObject)
    int result = (int) __target___PyTime_FromNanosecondsObject(a, b);
    STATS_AFTER(_PyTime_FromNanosecondsObject)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyTime_FromSeconds, _PyTime_FromNanosecondsObject)
_PyTime_t (*__target___PyTime_FromSeconds)(int) = NULL;
PyAPI_FUNC(_PyTime_t) _PyTime_FromSeconds(int a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target___PyTime_FromSeconds == NULL) {
        __target___PyTime_FromSeconds = resolveAPI("_PyTime_FromSeconds");
    }
    STATS_BEFORE(_PyTime_FromSeconds)
    _PyTime_t result = (_PyTime_t) __target___PyTime_FromSeconds(a);
    STATS_AFTER(_PyTime_FromSeconds)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyTime_FromSecondsObject, _PyTime_FromSeconds)
int (*__target___PyTime_FromSecondsObject)(_PyTime_t*, PyObject*, _PyTime_round_t) = NULL;
PyAPI_FUNC(int) _PyTime_FromSecondsObject(_PyTime_t* a, PyObject* b, _PyTime_round_t c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    if (__target___PyTime_FromSecondsObject == NULL) {
        __target___PyTime_FromSecondsObject = resolveAPI("_PyTime_FromSecondsObject");
    }
    STATS_BEFORE(_PyTime_FromSecondsObject)
    int result = (int) __target___PyTime_FromSecondsObject(a, b, c);
    STATS_AFTER(_PyTime_FromSecondsObject)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyTime_FromTimespec, _PyTime_FromSecondsObject)
int (*__target___PyTime_FromTimespec)(_PyTime_t*, struct timespec*) = NULL;
PyAPI_FUNC(int) _PyTime_FromTimespec(_PyTime_t* a, struct timespec* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target___PyTime_FromTimespec == NULL) {
        __target___PyTime_FromTimespec = resolveAPI("_PyTime_FromTimespec");
    }
    STATS_BEFORE(_PyTime_FromTimespec)
    int result = (int) __target___PyTime_FromTimespec(a, b);
    STATS_AFTER(_PyTime_FromTimespec)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyTime_FromTimeval, _PyTime_FromTimespec)
int (*__target___PyTime_FromTimeval)(_PyTime_t*, struct timeval*) = NULL;
PyAPI_FUNC(int) _PyTime_FromTimeval(_PyTime_t* a, struct timeval* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target___PyTime_FromTimeval == NULL) {
        __target___PyTime_FromTimeval = resolveAPI("_PyTime_FromTimeval");
    }
    STATS_BEFORE(_PyTime_FromTimeval)
    int result = (int) __target___PyTime_FromTimeval(a, b);
    STATS_AFTER(_PyTime_FromTimeval)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyTime_GetMonotonicClock, _PyTime_FromTimeval)
_PyTime_t (*__target___PyTime_GetMonotonicClock)() = NULL;
PyAPI_FUNC(_PyTime_t) _PyTime_GetMonotonicClock() {
    LOGS("");
    if (__target___PyTime_GetMonotonicClock == NULL) {
        __target___PyTime_GetMonotonicClock = resolveAPI("_PyTime_GetMonotonicClock");
    }
    STATS_BEFORE(_PyTime_GetMonotonicClock)
    _PyTime_t result = (_PyTime_t) __target___PyTime_GetMonotonicClock();
    STATS_AFTER(_PyTime_GetMonotonicClock)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyTime_GetMonotonicClockWithInfo, _PyTime_GetMonotonicClock)
int (*__target___PyTime_GetMonotonicClockWithInfo)(_PyTime_t*, _Py_clock_info_t*) = NULL;
PyAPI_FUNC(int) _PyTime_GetMonotonicClockWithInfo(_PyTime_t* a, _Py_clock_info_t* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target___PyTime_GetMonotonicClockWithInfo == NULL) {
        __target___PyTime_GetMonotonicClockWithInfo = resolveAPI("_PyTime_GetMonotonicClockWithInfo");
    }
    STATS_BEFORE(_PyTime_GetMonotonicClockWithInfo)
    int result = (int) __target___PyTime_GetMonotonicClockWithInfo(a, b);
    STATS_AFTER(_PyTime_GetMonotonicClockWithInfo)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyTime_GetPerfCounter, _PyTime_GetMonotonicClockWithInfo)
_PyTime_t (*__target___PyTime_GetPerfCounter)() = NULL;
PyAPI_FUNC(_PyTime_t) _PyTime_GetPerfCounter() {
    LOGS("");
    if (__target___PyTime_GetPerfCounter == NULL) {
        __target___PyTime_GetPerfCounter = resolveAPI("_PyTime_GetPerfCounter");
    }
    STATS_BEFORE(_PyTime_GetPerfCounter)
    _PyTime_t result = (_PyTime_t) __target___PyTime_GetPerfCounter();
    STATS_AFTER(_PyTime_GetPerfCounter)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyTime_GetPerfCounterWithInfo, _PyTime_GetPerfCounter)
int (*__target___PyTime_GetPerfCounterWithInfo)(_PyTime_t*, _Py_clock_info_t*) = NULL;
PyAPI_FUNC(int) _PyTime_GetPerfCounterWithInfo(_PyTime_t* a, _Py_clock_info_t* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target___PyTime_GetPerfCounterWithInfo == NULL) {
        __target___PyTime_GetPerfCounterWithInfo = resolveAPI("_PyTime_GetPerfCounterWithInfo");
    }
    STATS_BEFORE(_PyTime_GetPerfCounterWithInfo)
    int result = (int) __target___PyTime_GetPerfCounterWithInfo(a, b);
    STATS_AFTER(_PyTime_GetPerfCounterWithInfo)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyTime_GetSystemClock, _PyTime_GetPerfCounterWithInfo)
_PyTime_t (*__target___PyTime_GetSystemClock)() = NULL;
PyAPI_FUNC(_PyTime_t) _PyTime_GetSystemClock() {
    LOGS("");
    if (__target___PyTime_GetSystemClock == NULL) {
        __target___PyTime_GetSystemClock = resolveAPI("_PyTime_GetSystemClock");
    }
    STATS_BEFORE(_PyTime_GetSystemClock)
    _PyTime_t result = (_PyTime_t) __target___PyTime_GetSystemClock();
    STATS_AFTER(_PyTime_GetSystemClock)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyTime_GetSystemClockWithInfo, _PyTime_GetSystemClock)
int (*__target___PyTime_GetSystemClockWithInfo)(_PyTime_t*, _Py_clock_info_t*) = NULL;
PyAPI_FUNC(int) _PyTime_GetSystemClockWithInfo(_PyTime_t* a, _Py_clock_info_t* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target___PyTime_GetSystemClockWithInfo == NULL) {
        __target___PyTime_GetSystemClockWithInfo = resolveAPI("_PyTime_GetSystemClockWithInfo");
    }
    STATS_BEFORE(_PyTime_GetSystemClockWithInfo)
    int result = (int) __target___PyTime_GetSystemClockWithInfo(a, b);
    STATS_AFTER(_PyTime_GetSystemClockWithInfo)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyTime_MulDiv, _PyTime_GetSystemClockWithInfo)
_PyTime_t (*__target___PyTime_MulDiv)(_PyTime_t, _PyTime_t, _PyTime_t) = NULL;
PyAPI_FUNC(_PyTime_t) _PyTime_MulDiv(_PyTime_t a, _PyTime_t b, _PyTime_t c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    if (__target___PyTime_MulDiv == NULL) {
        __target___PyTime_MulDiv = resolveAPI("_PyTime_MulDiv");
    }
    STATS_BEFORE(_PyTime_MulDiv)
    _PyTime_t result = (_PyTime_t) __target___PyTime_MulDiv(a, b, c);
    STATS_AFTER(_PyTime_MulDiv)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyTime_ObjectToTime_t, _PyTime_MulDiv)
int (*__target___PyTime_ObjectToTime_t)(PyObject*, time_t*, _PyTime_round_t) = NULL;
PyAPI_FUNC(int) _PyTime_ObjectToTime_t(PyObject* a, time_t* b, _PyTime_round_t c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    if (__target___PyTime_ObjectToTime_t == NULL) {
        __target___PyTime_ObjectToTime_t = resolveAPI("_PyTime_ObjectToTime_t");
    }
    STATS_BEFORE(_PyTime_ObjectToTime_t)
    int result = (int) __target___PyTime_ObjectToTime_t(a, b, c);
    STATS_AFTER(_PyTime_ObjectToTime_t)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyTime_ObjectToTimespec, _PyTime_ObjectToTime_t)
int (*__target___PyTime_ObjectToTimespec)(PyObject*, time_t*, long*, _PyTime_round_t) = NULL;
PyAPI_FUNC(int) _PyTime_ObjectToTimespec(PyObject* a, time_t* b, long* c, _PyTime_round_t d) {
    LOG("0x%lx 0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c, (unsigned long) d);
    if (__target___PyTime_ObjectToTimespec == NULL) {
        __target___PyTime_ObjectToTimespec = resolveAPI("_PyTime_ObjectToTimespec");
    }
    STATS_BEFORE(_PyTime_ObjectToTimespec)
    int result = (int) __target___PyTime_ObjectToTimespec(a, b, c, d);
    STATS_AFTER(_PyTime_ObjectToTimespec)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyTime_ObjectToTimeval, _PyTime_ObjectToTimespec)
int (*__target___PyTime_ObjectToTimeval)(PyObject*, time_t*, long*, _PyTime_round_t) = NULL;
PyAPI_FUNC(int) _PyTime_ObjectToTimeval(PyObject* a, time_t* b, long* c, _PyTime_round_t d) {
    LOG("0x%lx 0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c, (unsigned long) d);
    if (__target___PyTime_ObjectToTimeval == NULL) {
        __target___PyTime_ObjectToTimeval = resolveAPI("_PyTime_ObjectToTimeval");
    }
    STATS_BEFORE(_PyTime_ObjectToTimeval)
    int result = (int) __target___PyTime_ObjectToTimeval(a, b, c, d);
    STATS_AFTER(_PyTime_ObjectToTimeval)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyTime_gmtime, _PyTime_ObjectToTimeval)
int (*__target___PyTime_gmtime)(time_t, struct tm*) = NULL;
PyAPI_FUNC(int) _PyTime_gmtime(time_t a, struct tm* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target___PyTime_gmtime == NULL) {
        __target___PyTime_gmtime = resolveAPI("_PyTime_gmtime");
    }
    STATS_BEFORE(_PyTime_gmtime)
    int result = (int) __target___PyTime_gmtime(a, b);
    STATS_AFTER(_PyTime_gmtime)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyTime_localtime, _PyTime_gmtime)
int (*__target___PyTime_localtime)(time_t, struct tm*) = NULL;
PyAPI_FUNC(int) _PyTime_localtime(time_t a, struct tm* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target___PyTime_localtime == NULL) {
        __target___PyTime_localtime = resolveAPI("_PyTime_localtime");
    }
    STATS_BEFORE(_PyTime_localtime)
    int result = (int) __target___PyTime_localtime(a, b);
    STATS_AFTER(_PyTime_localtime)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyTraceMalloc_GetTraceback, _PyTime_localtime)
PyObject* (*__target___PyTraceMalloc_GetTraceback)(unsigned int, uintptr_t) = NULL;
PyAPI_FUNC(PyObject*) _PyTraceMalloc_GetTraceback(unsigned int a, uintptr_t b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target___PyTraceMalloc_GetTraceback == NULL) {
        __target___PyTraceMalloc_GetTraceback = resolveAPI("_PyTraceMalloc_GetTraceback");
    }
    STATS_BEFORE(_PyTraceMalloc_GetTraceback)
    PyObject* result = (PyObject*) __target___PyTraceMalloc_GetTraceback(a, b);
    STATS_AFTER(_PyTraceMalloc_GetTraceback)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyTraceMalloc_NewReference, _PyTraceMalloc_GetTraceback)
int (*__target___PyTraceMalloc_NewReference)(PyObject*) = NULL;
PyAPI_FUNC(int) _PyTraceMalloc_NewReference(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target___PyTraceMalloc_NewReference == NULL) {
        __target___PyTraceMalloc_NewReference = resolveAPI("_PyTraceMalloc_NewReference");
    }
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
    if (__target___PyTraceback_Add == NULL) {
        __target___PyTraceback_Add = resolveAPI("_PyTraceback_Add");
    }
    STATS_BEFORE(_PyTraceback_Add)
    __target___PyTraceback_Add(a, b, c);
    STATS_AFTER(_PyTraceback_Add)
    LOG_AFTER_VOID
}
STATS_CONTAINER(_PyTrash_begin, _PyTraceback_Add)
int (*__target___PyTrash_begin)(struct _ts*, PyObject*) = NULL;
PyAPI_FUNC(int) _PyTrash_begin(struct _ts* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target___PyTrash_begin == NULL) {
        __target___PyTrash_begin = resolveAPI("_PyTrash_begin");
    }
    STATS_BEFORE(_PyTrash_begin)
    int result = (int) __target___PyTrash_begin(a, b);
    STATS_AFTER(_PyTrash_begin)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyTrash_cond, _PyTrash_begin)
int (*__target___PyTrash_cond)(PyObject*, destructor) = NULL;
PyAPI_FUNC(int) _PyTrash_cond(PyObject* a, destructor b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target___PyTrash_cond == NULL) {
        __target___PyTrash_cond = resolveAPI("_PyTrash_cond");
    }
    STATS_BEFORE(_PyTrash_cond)
    int result = (int) __target___PyTrash_cond(a, b);
    STATS_AFTER(_PyTrash_cond)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyTrash_deposit_object, _PyTrash_cond)
void (*__target___PyTrash_deposit_object)(PyObject*) = NULL;
PyAPI_FUNC(void) _PyTrash_deposit_object(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target___PyTrash_deposit_object == NULL) {
        __target___PyTrash_deposit_object = resolveAPI("_PyTrash_deposit_object");
    }
    STATS_BEFORE(_PyTrash_deposit_object)
    __target___PyTrash_deposit_object(a);
    STATS_AFTER(_PyTrash_deposit_object)
    LOG_AFTER_VOID
}
STATS_CONTAINER(_PyTrash_destroy_chain, _PyTrash_deposit_object)
void (*__target___PyTrash_destroy_chain)() = NULL;
PyAPI_FUNC(void) _PyTrash_destroy_chain() {
    LOGS("");
    if (__target___PyTrash_destroy_chain == NULL) {
        __target___PyTrash_destroy_chain = resolveAPI("_PyTrash_destroy_chain");
    }
    STATS_BEFORE(_PyTrash_destroy_chain)
    __target___PyTrash_destroy_chain();
    STATS_AFTER(_PyTrash_destroy_chain)
    LOG_AFTER_VOID
}
STATS_CONTAINER(_PyTrash_end, _PyTrash_destroy_chain)
void (*__target___PyTrash_end)(struct _ts*) = NULL;
PyAPI_FUNC(void) _PyTrash_end(struct _ts* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target___PyTrash_end == NULL) {
        __target___PyTrash_end = resolveAPI("_PyTrash_end");
    }
    STATS_BEFORE(_PyTrash_end)
    __target___PyTrash_end(a);
    STATS_AFTER(_PyTrash_end)
    LOG_AFTER_VOID
}
STATS_CONTAINER(_PyTrash_thread_deposit_object, _PyTrash_end)
void (*__target___PyTrash_thread_deposit_object)(PyObject*) = NULL;
PyAPI_FUNC(void) _PyTrash_thread_deposit_object(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target___PyTrash_thread_deposit_object == NULL) {
        __target___PyTrash_thread_deposit_object = resolveAPI("_PyTrash_thread_deposit_object");
    }
    STATS_BEFORE(_PyTrash_thread_deposit_object)
    __target___PyTrash_thread_deposit_object(a);
    STATS_AFTER(_PyTrash_thread_deposit_object)
    LOG_AFTER_VOID
}
STATS_CONTAINER(_PyTrash_thread_destroy_chain, _PyTrash_thread_deposit_object)
void (*__target___PyTrash_thread_destroy_chain)() = NULL;
PyAPI_FUNC(void) _PyTrash_thread_destroy_chain() {
    LOGS("");
    if (__target___PyTrash_thread_destroy_chain == NULL) {
        __target___PyTrash_thread_destroy_chain = resolveAPI("_PyTrash_thread_destroy_chain");
    }
    STATS_BEFORE(_PyTrash_thread_destroy_chain)
    __target___PyTrash_thread_destroy_chain();
    STATS_AFTER(_PyTrash_thread_destroy_chain)
    LOG_AFTER_VOID
}
STATS_CONTAINER(_PyTuple_DebugMallocStats, _PyTrash_thread_destroy_chain)
void (*__target___PyTuple_DebugMallocStats)(FILE*) = NULL;
PyAPI_FUNC(void) _PyTuple_DebugMallocStats(FILE* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target___PyTuple_DebugMallocStats == NULL) {
        __target___PyTuple_DebugMallocStats = resolveAPI("_PyTuple_DebugMallocStats");
    }
    STATS_BEFORE(_PyTuple_DebugMallocStats)
    __target___PyTuple_DebugMallocStats(a);
    STATS_AFTER(_PyTuple_DebugMallocStats)
    LOG_AFTER_VOID
}
STATS_CONTAINER(_PyTuple_MaybeUntrack, _PyTuple_DebugMallocStats)
void (*__target___PyTuple_MaybeUntrack)(PyObject*) = NULL;
PyAPI_FUNC(void) _PyTuple_MaybeUntrack(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target___PyTuple_MaybeUntrack == NULL) {
        __target___PyTuple_MaybeUntrack = resolveAPI("_PyTuple_MaybeUntrack");
    }
    STATS_BEFORE(_PyTuple_MaybeUntrack)
    __target___PyTuple_MaybeUntrack(a);
    STATS_AFTER(_PyTuple_MaybeUntrack)
    LOG_AFTER_VOID
}
STATS_CONTAINER(_PyTuple_Resize, _PyTuple_MaybeUntrack)
int (*__target___PyTuple_Resize)(PyObject**, Py_ssize_t) = NULL;
PyAPI_FUNC(int) _PyTuple_Resize(PyObject** a, Py_ssize_t b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target___PyTuple_Resize == NULL) {
        __target___PyTuple_Resize = resolveAPI("_PyTuple_Resize");
    }
    STATS_BEFORE(_PyTuple_Resize)
    int result = (int) __target___PyTuple_Resize(a, b);
    STATS_AFTER(_PyTuple_Resize)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyType_CalculateMetaclass, _PyTuple_Resize)
PyTypeObject* (*__target___PyType_CalculateMetaclass)(PyTypeObject*, PyObject*) = NULL;
PyAPI_FUNC(PyTypeObject*) _PyType_CalculateMetaclass(PyTypeObject* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target___PyType_CalculateMetaclass == NULL) {
        __target___PyType_CalculateMetaclass = resolveAPI("_PyType_CalculateMetaclass");
    }
    STATS_BEFORE(_PyType_CalculateMetaclass)
    PyTypeObject* result = (PyTypeObject*) __target___PyType_CalculateMetaclass(a, b);
    STATS_AFTER(_PyType_CalculateMetaclass)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyType_GetDocFromInternalDoc, _PyType_CalculateMetaclass)
PyObject* (*__target___PyType_GetDocFromInternalDoc)(const char*, const char*) = NULL;
PyAPI_FUNC(PyObject*) _PyType_GetDocFromInternalDoc(const char* a, const char* b) {
    LOG("'%s'(0x%lx) '%s'(0x%lx)", a?a:"<null>", (unsigned long) a, b?b:"<null>", (unsigned long) b);
    if (__target___PyType_GetDocFromInternalDoc == NULL) {
        __target___PyType_GetDocFromInternalDoc = resolveAPI("_PyType_GetDocFromInternalDoc");
    }
    STATS_BEFORE(_PyType_GetDocFromInternalDoc)
    PyObject* result = (PyObject*) __target___PyType_GetDocFromInternalDoc(a, b);
    STATS_AFTER(_PyType_GetDocFromInternalDoc)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyType_GetModuleByDef, _PyType_GetDocFromInternalDoc)
PyObject* (*__target___PyType_GetModuleByDef)(PyTypeObject*, struct PyModuleDef*) = NULL;
PyAPI_FUNC(PyObject*) _PyType_GetModuleByDef(PyTypeObject* a, struct PyModuleDef* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target___PyType_GetModuleByDef == NULL) {
        __target___PyType_GetModuleByDef = resolveAPI("_PyType_GetModuleByDef");
    }
    STATS_BEFORE(_PyType_GetModuleByDef)
    PyObject* result = (PyObject*) __target___PyType_GetModuleByDef(a, b);
    STATS_AFTER(_PyType_GetModuleByDef)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyType_GetTextSignatureFromInternalDoc, _PyType_GetModuleByDef)
PyObject* (*__target___PyType_GetTextSignatureFromInternalDoc)(const char*, const char*) = NULL;
PyAPI_FUNC(PyObject*) _PyType_GetTextSignatureFromInternalDoc(const char* a, const char* b) {
    LOG("'%s'(0x%lx) '%s'(0x%lx)", a?a:"<null>", (unsigned long) a, b?b:"<null>", (unsigned long) b);
    if (__target___PyType_GetTextSignatureFromInternalDoc == NULL) {
        __target___PyType_GetTextSignatureFromInternalDoc = resolveAPI("_PyType_GetTextSignatureFromInternalDoc");
    }
    STATS_BEFORE(_PyType_GetTextSignatureFromInternalDoc)
    PyObject* result = (PyObject*) __target___PyType_GetTextSignatureFromInternalDoc(a, b);
    STATS_AFTER(_PyType_GetTextSignatureFromInternalDoc)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyType_Lookup, _PyType_GetTextSignatureFromInternalDoc)
PyObject* (*__target___PyType_Lookup)(PyTypeObject*, PyObject*) = NULL;
PyAPI_FUNC(PyObject*) _PyType_Lookup(PyTypeObject* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target___PyType_Lookup == NULL) {
        __target___PyType_Lookup = resolveAPI("_PyType_Lookup");
    }
    STATS_BEFORE(_PyType_Lookup)
    PyObject* result = (PyObject*) __target___PyType_Lookup(a, b);
    STATS_AFTER(_PyType_Lookup)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyType_LookupId, _PyType_Lookup)
PyObject* (*__target___PyType_LookupId)(PyTypeObject*, _Py_Identifier*) = NULL;
PyAPI_FUNC(PyObject*) _PyType_LookupId(PyTypeObject* a, _Py_Identifier* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target___PyType_LookupId == NULL) {
        __target___PyType_LookupId = resolveAPI("_PyType_LookupId");
    }
    STATS_BEFORE(_PyType_LookupId)
    PyObject* result = (PyObject*) __target___PyType_LookupId(a, b);
    STATS_AFTER(_PyType_LookupId)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyType_Name, _PyType_LookupId)
const char* (*__target___PyType_Name)(PyTypeObject*) = NULL;
PyAPI_FUNC(const char*) _PyType_Name(PyTypeObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target___PyType_Name == NULL) {
        __target___PyType_Name = resolveAPI("_PyType_Name");
    }
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
    if (__target___PyUnicodeObject_DATA == NULL) {
        __target___PyUnicodeObject_DATA = resolveAPI("_PyUnicodeObject_DATA");
    }
    STATS_BEFORE(_PyUnicodeObject_DATA)
    void* result = (void*) __target___PyUnicodeObject_DATA(a);
    STATS_AFTER(_PyUnicodeObject_DATA)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyUnicodeTranslateError_Create, _PyUnicodeObject_DATA)
PyObject* (*__target___PyUnicodeTranslateError_Create)(PyObject*, Py_ssize_t, Py_ssize_t, const char*) = NULL;
PyAPI_FUNC(PyObject*) _PyUnicodeTranslateError_Create(PyObject* a, Py_ssize_t b, Py_ssize_t c, const char* d) {
    LOG("0x%lx 0x%lx 0x%lx '%s'(0x%lx)", (unsigned long) a, (unsigned long) b, (unsigned long) c, d?d:"<null>", (unsigned long) d);
    if (__target___PyUnicodeTranslateError_Create == NULL) {
        __target___PyUnicodeTranslateError_Create = resolveAPI("_PyUnicodeTranslateError_Create");
    }
    STATS_BEFORE(_PyUnicodeTranslateError_Create)
    PyObject* result = (PyObject*) __target___PyUnicodeTranslateError_Create(a, b, c, d);
    STATS_AFTER(_PyUnicodeTranslateError_Create)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyUnicodeWriter_Dealloc, _PyUnicodeTranslateError_Create)
void (*__target___PyUnicodeWriter_Dealloc)(_PyUnicodeWriter*) = NULL;
PyAPI_FUNC(void) _PyUnicodeWriter_Dealloc(_PyUnicodeWriter* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target___PyUnicodeWriter_Dealloc == NULL) {
        __target___PyUnicodeWriter_Dealloc = resolveAPI("_PyUnicodeWriter_Dealloc");
    }
    STATS_BEFORE(_PyUnicodeWriter_Dealloc)
    __target___PyUnicodeWriter_Dealloc(a);
    STATS_AFTER(_PyUnicodeWriter_Dealloc)
    LOG_AFTER_VOID
}
STATS_CONTAINER(_PyUnicodeWriter_Finish, _PyUnicodeWriter_Dealloc)
PyObject* (*__target___PyUnicodeWriter_Finish)(_PyUnicodeWriter*) = NULL;
PyAPI_FUNC(PyObject*) _PyUnicodeWriter_Finish(_PyUnicodeWriter* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target___PyUnicodeWriter_Finish == NULL) {
        __target___PyUnicodeWriter_Finish = resolveAPI("_PyUnicodeWriter_Finish");
    }
    STATS_BEFORE(_PyUnicodeWriter_Finish)
    PyObject* result = (PyObject*) __target___PyUnicodeWriter_Finish(a);
    STATS_AFTER(_PyUnicodeWriter_Finish)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyUnicodeWriter_Init, _PyUnicodeWriter_Finish)
void (*__target___PyUnicodeWriter_Init)(_PyUnicodeWriter*) = NULL;
PyAPI_FUNC(void) _PyUnicodeWriter_Init(_PyUnicodeWriter* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target___PyUnicodeWriter_Init == NULL) {
        __target___PyUnicodeWriter_Init = resolveAPI("_PyUnicodeWriter_Init");
    }
    STATS_BEFORE(_PyUnicodeWriter_Init)
    __target___PyUnicodeWriter_Init(a);
    STATS_AFTER(_PyUnicodeWriter_Init)
    LOG_AFTER_VOID
}
STATS_CONTAINER(_PyUnicodeWriter_PrepareInternal, _PyUnicodeWriter_Init)
int (*__target___PyUnicodeWriter_PrepareInternal)(_PyUnicodeWriter*, Py_ssize_t, Py_UCS4) = NULL;
PyAPI_FUNC(int) _PyUnicodeWriter_PrepareInternal(_PyUnicodeWriter* a, Py_ssize_t b, Py_UCS4 c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    if (__target___PyUnicodeWriter_PrepareInternal == NULL) {
        __target___PyUnicodeWriter_PrepareInternal = resolveAPI("_PyUnicodeWriter_PrepareInternal");
    }
    STATS_BEFORE(_PyUnicodeWriter_PrepareInternal)
    int result = (int) __target___PyUnicodeWriter_PrepareInternal(a, b, c);
    STATS_AFTER(_PyUnicodeWriter_PrepareInternal)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyUnicodeWriter_PrepareKindInternal, _PyUnicodeWriter_PrepareInternal)
int (*__target___PyUnicodeWriter_PrepareKindInternal)(_PyUnicodeWriter*, enum PyUnicode_Kind) = NULL;
PyAPI_FUNC(int) _PyUnicodeWriter_PrepareKindInternal(_PyUnicodeWriter* a, enum PyUnicode_Kind b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target___PyUnicodeWriter_PrepareKindInternal == NULL) {
        __target___PyUnicodeWriter_PrepareKindInternal = resolveAPI("_PyUnicodeWriter_PrepareKindInternal");
    }
    STATS_BEFORE(_PyUnicodeWriter_PrepareKindInternal)
    int result = (int) __target___PyUnicodeWriter_PrepareKindInternal(a, b);
    STATS_AFTER(_PyUnicodeWriter_PrepareKindInternal)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyUnicodeWriter_WriteASCIIString, _PyUnicodeWriter_PrepareKindInternal)
int (*__target___PyUnicodeWriter_WriteASCIIString)(_PyUnicodeWriter*, const char*, Py_ssize_t) = NULL;
PyAPI_FUNC(int) _PyUnicodeWriter_WriteASCIIString(_PyUnicodeWriter* a, const char* b, Py_ssize_t c) {
    LOG("0x%lx '%s'(0x%lx) 0x%lx", (unsigned long) a, b?b:"<null>", (unsigned long) b, (unsigned long) c);
    if (__target___PyUnicodeWriter_WriteASCIIString == NULL) {
        __target___PyUnicodeWriter_WriteASCIIString = resolveAPI("_PyUnicodeWriter_WriteASCIIString");
    }
    STATS_BEFORE(_PyUnicodeWriter_WriteASCIIString)
    int result = (int) __target___PyUnicodeWriter_WriteASCIIString(a, b, c);
    STATS_AFTER(_PyUnicodeWriter_WriteASCIIString)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyUnicodeWriter_WriteChar, _PyUnicodeWriter_WriteASCIIString)
int (*__target___PyUnicodeWriter_WriteChar)(_PyUnicodeWriter*, Py_UCS4) = NULL;
PyAPI_FUNC(int) _PyUnicodeWriter_WriteChar(_PyUnicodeWriter* a, Py_UCS4 b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target___PyUnicodeWriter_WriteChar == NULL) {
        __target___PyUnicodeWriter_WriteChar = resolveAPI("_PyUnicodeWriter_WriteChar");
    }
    STATS_BEFORE(_PyUnicodeWriter_WriteChar)
    int result = (int) __target___PyUnicodeWriter_WriteChar(a, b);
    STATS_AFTER(_PyUnicodeWriter_WriteChar)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyUnicodeWriter_WriteLatin1String, _PyUnicodeWriter_WriteChar)
int (*__target___PyUnicodeWriter_WriteLatin1String)(_PyUnicodeWriter*, const char*, Py_ssize_t) = NULL;
PyAPI_FUNC(int) _PyUnicodeWriter_WriteLatin1String(_PyUnicodeWriter* a, const char* b, Py_ssize_t c) {
    LOG("0x%lx '%s'(0x%lx) 0x%lx", (unsigned long) a, b?b:"<null>", (unsigned long) b, (unsigned long) c);
    if (__target___PyUnicodeWriter_WriteLatin1String == NULL) {
        __target___PyUnicodeWriter_WriteLatin1String = resolveAPI("_PyUnicodeWriter_WriteLatin1String");
    }
    STATS_BEFORE(_PyUnicodeWriter_WriteLatin1String)
    int result = (int) __target___PyUnicodeWriter_WriteLatin1String(a, b, c);
    STATS_AFTER(_PyUnicodeWriter_WriteLatin1String)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyUnicodeWriter_WriteStr, _PyUnicodeWriter_WriteLatin1String)
int (*__target___PyUnicodeWriter_WriteStr)(_PyUnicodeWriter*, PyObject*) = NULL;
PyAPI_FUNC(int) _PyUnicodeWriter_WriteStr(_PyUnicodeWriter* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target___PyUnicodeWriter_WriteStr == NULL) {
        __target___PyUnicodeWriter_WriteStr = resolveAPI("_PyUnicodeWriter_WriteStr");
    }
    STATS_BEFORE(_PyUnicodeWriter_WriteStr)
    int result = (int) __target___PyUnicodeWriter_WriteStr(a, b);
    STATS_AFTER(_PyUnicodeWriter_WriteStr)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyUnicodeWriter_WriteSubstring, _PyUnicodeWriter_WriteStr)
int (*__target___PyUnicodeWriter_WriteSubstring)(_PyUnicodeWriter*, PyObject*, Py_ssize_t, Py_ssize_t) = NULL;
PyAPI_FUNC(int) _PyUnicodeWriter_WriteSubstring(_PyUnicodeWriter* a, PyObject* b, Py_ssize_t c, Py_ssize_t d) {
    LOG("0x%lx 0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c, (unsigned long) d);
    if (__target___PyUnicodeWriter_WriteSubstring == NULL) {
        __target___PyUnicodeWriter_WriteSubstring = resolveAPI("_PyUnicodeWriter_WriteSubstring");
    }
    STATS_BEFORE(_PyUnicodeWriter_WriteSubstring)
    int result = (int) __target___PyUnicodeWriter_WriteSubstring(a, b, c, d);
    STATS_AFTER(_PyUnicodeWriter_WriteSubstring)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyUnicode_AsASCIIString, _PyUnicodeWriter_WriteSubstring)
PyObject* (*__target___PyUnicode_AsASCIIString)(PyObject*, const char*) = NULL;
PyAPI_FUNC(PyObject*) _PyUnicode_AsASCIIString(PyObject* a, const char* b) {
    LOG("0x%lx '%s'(0x%lx)", (unsigned long) a, b?b:"<null>", (unsigned long) b);
    if (__target___PyUnicode_AsASCIIString == NULL) {
        __target___PyUnicode_AsASCIIString = resolveAPI("_PyUnicode_AsASCIIString");
    }
    STATS_BEFORE(_PyUnicode_AsASCIIString)
    PyObject* result = (PyObject*) __target___PyUnicode_AsASCIIString(a, b);
    STATS_AFTER(_PyUnicode_AsASCIIString)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyUnicode_AsKind, _PyUnicode_AsASCIIString)
void* (*__target___PyUnicode_AsKind)(PyObject*, unsigned int) = NULL;
PyAPI_FUNC(void*) _PyUnicode_AsKind(PyObject* a, unsigned int b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target___PyUnicode_AsKind == NULL) {
        __target___PyUnicode_AsKind = resolveAPI("_PyUnicode_AsKind");
    }
    STATS_BEFORE(_PyUnicode_AsKind)
    void* result = (void*) __target___PyUnicode_AsKind(a, b);
    STATS_AFTER(_PyUnicode_AsKind)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyUnicode_AsLatin1String, _PyUnicode_AsKind)
PyObject* (*__target___PyUnicode_AsLatin1String)(PyObject*, const char*) = NULL;
PyAPI_FUNC(PyObject*) _PyUnicode_AsLatin1String(PyObject* a, const char* b) {
    LOG("0x%lx '%s'(0x%lx)", (unsigned long) a, b?b:"<null>", (unsigned long) b);
    if (__target___PyUnicode_AsLatin1String == NULL) {
        __target___PyUnicode_AsLatin1String = resolveAPI("_PyUnicode_AsLatin1String");
    }
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
    if (__target___PyUnicode_AsUTF8String == NULL) {
        __target___PyUnicode_AsUTF8String = resolveAPI("_PyUnicode_AsUTF8String");
    }
    STATS_BEFORE(_PyUnicode_AsUTF8String)
    PyObject* result = (PyObject*) __target___PyUnicode_AsUTF8String(a, b);
    STATS_AFTER(_PyUnicode_AsUTF8String)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyUnicode_AsUnicode, _PyUnicode_AsUTF8String)
const Py_UNICODE* (*__target___PyUnicode_AsUnicode)(PyObject*) = NULL;
PyAPI_FUNC(const Py_UNICODE*) _PyUnicode_AsUnicode(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target___PyUnicode_AsUnicode == NULL) {
        __target___PyUnicode_AsUnicode = resolveAPI("_PyUnicode_AsUnicode");
    }
    STATS_BEFORE(_PyUnicode_AsUnicode)
    const Py_UNICODE* result = (const Py_UNICODE*) __target___PyUnicode_AsUnicode(a);
    STATS_AFTER(_PyUnicode_AsUnicode)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyUnicode_CheckConsistency, _PyUnicode_AsUnicode)
int (*__target___PyUnicode_CheckConsistency)(PyObject*, int) = NULL;
PyAPI_FUNC(int) _PyUnicode_CheckConsistency(PyObject* a, int b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target___PyUnicode_CheckConsistency == NULL) {
        __target___PyUnicode_CheckConsistency = resolveAPI("_PyUnicode_CheckConsistency");
    }
    STATS_BEFORE(_PyUnicode_CheckConsistency)
    int result = (int) __target___PyUnicode_CheckConsistency(a, b);
    STATS_AFTER(_PyUnicode_CheckConsistency)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyUnicode_Copy, _PyUnicode_CheckConsistency)
PyObject* (*__target___PyUnicode_Copy)(PyObject*) = NULL;
PyAPI_FUNC(PyObject*) _PyUnicode_Copy(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target___PyUnicode_Copy == NULL) {
        __target___PyUnicode_Copy = resolveAPI("_PyUnicode_Copy");
    }
    STATS_BEFORE(_PyUnicode_Copy)
    PyObject* result = (PyObject*) __target___PyUnicode_Copy(a);
    STATS_AFTER(_PyUnicode_Copy)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyUnicode_DecodeRawUnicodeEscapeStateful, _PyUnicode_Copy)
PyObject* (*__target___PyUnicode_DecodeRawUnicodeEscapeStateful)(const char*, Py_ssize_t, const char*, Py_ssize_t*) = NULL;
PyAPI_FUNC(PyObject*) _PyUnicode_DecodeRawUnicodeEscapeStateful(const char* a, Py_ssize_t b, const char* c, Py_ssize_t* d) {
    LOG("'%s'(0x%lx) 0x%lx '%s'(0x%lx) 0x%lx", a?a:"<null>", (unsigned long) a, (unsigned long) b, c?c:"<null>", (unsigned long) c, (unsigned long) d);
    if (__target___PyUnicode_DecodeRawUnicodeEscapeStateful == NULL) {
        __target___PyUnicode_DecodeRawUnicodeEscapeStateful = resolveAPI("_PyUnicode_DecodeRawUnicodeEscapeStateful");
    }
    STATS_BEFORE(_PyUnicode_DecodeRawUnicodeEscapeStateful)
    PyObject* result = (PyObject*) __target___PyUnicode_DecodeRawUnicodeEscapeStateful(a, b, c, d);
    STATS_AFTER(_PyUnicode_DecodeRawUnicodeEscapeStateful)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyUnicode_DecodeUnicodeEscapeInternal, _PyUnicode_DecodeRawUnicodeEscapeStateful)
PyObject* (*__target___PyUnicode_DecodeUnicodeEscapeInternal)(const char*, Py_ssize_t, const char*, Py_ssize_t*, const char**) = NULL;
PyAPI_FUNC(PyObject*) _PyUnicode_DecodeUnicodeEscapeInternal(const char* a, Py_ssize_t b, const char* c, Py_ssize_t* d, const char** e) {
    LOG("'%s'(0x%lx) 0x%lx '%s'(0x%lx) 0x%lx 0x%lx", a?a:"<null>", (unsigned long) a, (unsigned long) b, c?c:"<null>", (unsigned long) c, (unsigned long) d, (unsigned long) e);
    if (__target___PyUnicode_DecodeUnicodeEscapeInternal == NULL) {
        __target___PyUnicode_DecodeUnicodeEscapeInternal = resolveAPI("_PyUnicode_DecodeUnicodeEscapeInternal");
    }
    STATS_BEFORE(_PyUnicode_DecodeUnicodeEscapeInternal)
    PyObject* result = (PyObject*) __target___PyUnicode_DecodeUnicodeEscapeInternal(a, b, c, d, e);
    STATS_AFTER(_PyUnicode_DecodeUnicodeEscapeInternal)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyUnicode_DecodeUnicodeEscapeStateful, _PyUnicode_DecodeUnicodeEscapeInternal)
PyObject* (*__target___PyUnicode_DecodeUnicodeEscapeStateful)(const char*, Py_ssize_t, const char*, Py_ssize_t*) = NULL;
PyAPI_FUNC(PyObject*) _PyUnicode_DecodeUnicodeEscapeStateful(const char* a, Py_ssize_t b, const char* c, Py_ssize_t* d) {
    LOG("'%s'(0x%lx) 0x%lx '%s'(0x%lx) 0x%lx", a?a:"<null>", (unsigned long) a, (unsigned long) b, c?c:"<null>", (unsigned long) c, (unsigned long) d);
    if (__target___PyUnicode_DecodeUnicodeEscapeStateful == NULL) {
        __target___PyUnicode_DecodeUnicodeEscapeStateful = resolveAPI("_PyUnicode_DecodeUnicodeEscapeStateful");
    }
    STATS_BEFORE(_PyUnicode_DecodeUnicodeEscapeStateful)
    PyObject* result = (PyObject*) __target___PyUnicode_DecodeUnicodeEscapeStateful(a, b, c, d);
    STATS_AFTER(_PyUnicode_DecodeUnicodeEscapeStateful)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyUnicode_EQ, _PyUnicode_DecodeUnicodeEscapeStateful)
int (*__target___PyUnicode_EQ)(PyObject*, PyObject*) = NULL;
PyAPI_FUNC(int) _PyUnicode_EQ(PyObject* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target___PyUnicode_EQ == NULL) {
        __target___PyUnicode_EQ = resolveAPI("_PyUnicode_EQ");
    }
    STATS_BEFORE(_PyUnicode_EQ)
    int result = (int) __target___PyUnicode_EQ(a, b);
    STATS_AFTER(_PyUnicode_EQ)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyUnicode_EncodeCharmap, _PyUnicode_EQ)
PyObject* (*__target___PyUnicode_EncodeCharmap)(PyObject*, PyObject*, const char*) = NULL;
PyAPI_FUNC(PyObject*) _PyUnicode_EncodeCharmap(PyObject* a, PyObject* b, const char* c) {
    LOG("0x%lx 0x%lx '%s'(0x%lx)", (unsigned long) a, (unsigned long) b, c?c:"<null>", (unsigned long) c);
    if (__target___PyUnicode_EncodeCharmap == NULL) {
        __target___PyUnicode_EncodeCharmap = resolveAPI("_PyUnicode_EncodeCharmap");
    }
    STATS_BEFORE(_PyUnicode_EncodeCharmap)
    PyObject* result = (PyObject*) __target___PyUnicode_EncodeCharmap(a, b, c);
    STATS_AFTER(_PyUnicode_EncodeCharmap)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyUnicode_EncodeUTF16, _PyUnicode_EncodeCharmap)
PyObject* (*__target___PyUnicode_EncodeUTF16)(PyObject*, const char*, int) = NULL;
PyAPI_FUNC(PyObject*) _PyUnicode_EncodeUTF16(PyObject* a, const char* b, int c) {
    LOG("0x%lx '%s'(0x%lx) 0x%lx", (unsigned long) a, b?b:"<null>", (unsigned long) b, (unsigned long) c);
    if (__target___PyUnicode_EncodeUTF16 == NULL) {
        __target___PyUnicode_EncodeUTF16 = resolveAPI("_PyUnicode_EncodeUTF16");
    }
    STATS_BEFORE(_PyUnicode_EncodeUTF16)
    PyObject* result = (PyObject*) __target___PyUnicode_EncodeUTF16(a, b, c);
    STATS_AFTER(_PyUnicode_EncodeUTF16)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyUnicode_EncodeUTF32, _PyUnicode_EncodeUTF16)
PyObject* (*__target___PyUnicode_EncodeUTF32)(PyObject*, const char*, int) = NULL;
PyAPI_FUNC(PyObject*) _PyUnicode_EncodeUTF32(PyObject* a, const char* b, int c) {
    LOG("0x%lx '%s'(0x%lx) 0x%lx", (unsigned long) a, b?b:"<null>", (unsigned long) b, (unsigned long) c);
    if (__target___PyUnicode_EncodeUTF32 == NULL) {
        __target___PyUnicode_EncodeUTF32 = resolveAPI("_PyUnicode_EncodeUTF32");
    }
    STATS_BEFORE(_PyUnicode_EncodeUTF32)
    PyObject* result = (PyObject*) __target___PyUnicode_EncodeUTF32(a, b, c);
    STATS_AFTER(_PyUnicode_EncodeUTF32)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyUnicode_EncodeUTF7, _PyUnicode_EncodeUTF32)
PyObject* (*__target___PyUnicode_EncodeUTF7)(PyObject*, int, int, const char*) = NULL;
PyAPI_FUNC(PyObject*) _PyUnicode_EncodeUTF7(PyObject* a, int b, int c, const char* d) {
    LOG("0x%lx 0x%lx 0x%lx '%s'(0x%lx)", (unsigned long) a, (unsigned long) b, (unsigned long) c, d?d:"<null>", (unsigned long) d);
    if (__target___PyUnicode_EncodeUTF7 == NULL) {
        __target___PyUnicode_EncodeUTF7 = resolveAPI("_PyUnicode_EncodeUTF7");
    }
    STATS_BEFORE(_PyUnicode_EncodeUTF7)
    PyObject* result = (PyObject*) __target___PyUnicode_EncodeUTF7(a, b, c, d);
    STATS_AFTER(_PyUnicode_EncodeUTF7)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyUnicode_EqualToASCIIId, _PyUnicode_EncodeUTF7)
int (*__target___PyUnicode_EqualToASCIIId)(PyObject*, _Py_Identifier*) = NULL;
PyAPI_FUNC(int) _PyUnicode_EqualToASCIIId(PyObject* a, _Py_Identifier* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target___PyUnicode_EqualToASCIIId == NULL) {
        __target___PyUnicode_EqualToASCIIId = resolveAPI("_PyUnicode_EqualToASCIIId");
    }
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
    if (__target___PyUnicode_EqualToASCIIString == NULL) {
        __target___PyUnicode_EqualToASCIIString = resolveAPI("_PyUnicode_EqualToASCIIString");
    }
    STATS_BEFORE(_PyUnicode_EqualToASCIIString)
    int result = (int) __target___PyUnicode_EqualToASCIIString(a, b);
    STATS_AFTER(_PyUnicode_EqualToASCIIString)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyUnicode_FastCopyCharacters, _PyUnicode_EqualToASCIIString)
void (*__target___PyUnicode_FastCopyCharacters)(PyObject*, Py_ssize_t, PyObject*, Py_ssize_t, Py_ssize_t) = NULL;
PyAPI_FUNC(void) _PyUnicode_FastCopyCharacters(PyObject* a, Py_ssize_t b, PyObject* c, Py_ssize_t d, Py_ssize_t e) {
    LOG("0x%lx 0x%lx 0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c, (unsigned long) d, (unsigned long) e);
    if (__target___PyUnicode_FastCopyCharacters == NULL) {
        __target___PyUnicode_FastCopyCharacters = resolveAPI("_PyUnicode_FastCopyCharacters");
    }
    STATS_BEFORE(_PyUnicode_FastCopyCharacters)
    __target___PyUnicode_FastCopyCharacters(a, b, c, d, e);
    STATS_AFTER(_PyUnicode_FastCopyCharacters)
    LOG_AFTER_VOID
}
STATS_CONTAINER(_PyUnicode_FastFill, _PyUnicode_FastCopyCharacters)
void (*__target___PyUnicode_FastFill)(PyObject*, Py_ssize_t, Py_ssize_t, Py_UCS4) = NULL;
PyAPI_FUNC(void) _PyUnicode_FastFill(PyObject* a, Py_ssize_t b, Py_ssize_t c, Py_UCS4 d) {
    LOG("0x%lx 0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c, (unsigned long) d);
    if (__target___PyUnicode_FastFill == NULL) {
        __target___PyUnicode_FastFill = resolveAPI("_PyUnicode_FastFill");
    }
    STATS_BEFORE(_PyUnicode_FastFill)
    __target___PyUnicode_FastFill(a, b, c, d);
    STATS_AFTER(_PyUnicode_FastFill)
    LOG_AFTER_VOID
}
STATS_CONTAINER(_PyUnicode_FindMaxChar, _PyUnicode_FastFill)
Py_UCS4 (*__target___PyUnicode_FindMaxChar)(PyObject*, Py_ssize_t, Py_ssize_t) = NULL;
PyAPI_FUNC(Py_UCS4) _PyUnicode_FindMaxChar(PyObject* a, Py_ssize_t b, Py_ssize_t c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    if (__target___PyUnicode_FindMaxChar == NULL) {
        __target___PyUnicode_FindMaxChar = resolveAPI("_PyUnicode_FindMaxChar");
    }
    STATS_BEFORE(_PyUnicode_FindMaxChar)
    Py_UCS4 result = (Py_UCS4) __target___PyUnicode_FindMaxChar(a, b, c);
    STATS_AFTER(_PyUnicode_FindMaxChar)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyUnicode_FormatAdvancedWriter, _PyUnicode_FindMaxChar)
int (*__target___PyUnicode_FormatAdvancedWriter)(_PyUnicodeWriter*, PyObject*, PyObject*, Py_ssize_t, Py_ssize_t) = NULL;
PyAPI_FUNC(int) _PyUnicode_FormatAdvancedWriter(_PyUnicodeWriter* a, PyObject* b, PyObject* c, Py_ssize_t d, Py_ssize_t e) {
    LOG("0x%lx 0x%lx 0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c, (unsigned long) d, (unsigned long) e);
    if (__target___PyUnicode_FormatAdvancedWriter == NULL) {
        __target___PyUnicode_FormatAdvancedWriter = resolveAPI("_PyUnicode_FormatAdvancedWriter");
    }
    STATS_BEFORE(_PyUnicode_FormatAdvancedWriter)
    int result = (int) __target___PyUnicode_FormatAdvancedWriter(a, b, c, d, e);
    STATS_AFTER(_PyUnicode_FormatAdvancedWriter)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyUnicode_FormatLong, _PyUnicode_FormatAdvancedWriter)
PyObject* (*__target___PyUnicode_FormatLong)(PyObject*, int, int, int) = NULL;
PyAPI_FUNC(PyObject*) _PyUnicode_FormatLong(PyObject* a, int b, int c, int d) {
    LOG("0x%lx 0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c, (unsigned long) d);
    if (__target___PyUnicode_FormatLong == NULL) {
        __target___PyUnicode_FormatLong = resolveAPI("_PyUnicode_FormatLong");
    }
    STATS_BEFORE(_PyUnicode_FormatLong)
    PyObject* result = (PyObject*) __target___PyUnicode_FormatLong(a, b, c, d);
    STATS_AFTER(_PyUnicode_FormatLong)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyUnicode_FromASCII, _PyUnicode_FormatLong)
PyObject* (*__target___PyUnicode_FromASCII)(const char*, Py_ssize_t) = NULL;
PyAPI_FUNC(PyObject*) _PyUnicode_FromASCII(const char* a, Py_ssize_t b) {
    LOG("'%s'(0x%lx) 0x%lx", a?a:"<null>", (unsigned long) a, (unsigned long) b);
    if (__target___PyUnicode_FromASCII == NULL) {
        __target___PyUnicode_FromASCII = resolveAPI("_PyUnicode_FromASCII");
    }
    STATS_BEFORE(_PyUnicode_FromASCII)
    PyObject* result = (PyObject*) __target___PyUnicode_FromASCII(a, b);
    STATS_AFTER(_PyUnicode_FromASCII)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyUnicode_FromId, _PyUnicode_FromASCII)
PyObject* (*__target___PyUnicode_FromId)(_Py_Identifier*) = NULL;
PyAPI_FUNC(PyObject*) _PyUnicode_FromId(_Py_Identifier* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target___PyUnicode_FromId == NULL) {
        __target___PyUnicode_FromId = resolveAPI("_PyUnicode_FromId");
    }
    STATS_BEFORE(_PyUnicode_FromId)
    PyObject* result = (PyObject*) __target___PyUnicode_FromId(a);
    STATS_AFTER(_PyUnicode_FromId)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyUnicode_InsertThousandsGrouping, _PyUnicode_FromId)
Py_ssize_t (*__target___PyUnicode_InsertThousandsGrouping)(_PyUnicodeWriter*, Py_ssize_t, PyObject*, Py_ssize_t, Py_ssize_t, Py_ssize_t, const char*, PyObject*, Py_UCS4*) = NULL;
PyAPI_FUNC(Py_ssize_t) _PyUnicode_InsertThousandsGrouping(_PyUnicodeWriter* a, Py_ssize_t b, PyObject* c, Py_ssize_t d, Py_ssize_t e, Py_ssize_t f, const char* g, PyObject* h, Py_UCS4* i) {
    LOG("0x%lx 0x%lx 0x%lx 0x%lx 0x%lx 0x%lx '%s'(0x%lx) 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c, (unsigned long) d, (unsigned long) e, (unsigned long) f, g?g:"<null>", (unsigned long) g, (unsigned long) h, (unsigned long) i);
    if (__target___PyUnicode_InsertThousandsGrouping == NULL) {
        __target___PyUnicode_InsertThousandsGrouping = resolveAPI("_PyUnicode_InsertThousandsGrouping");
    }
    STATS_BEFORE(_PyUnicode_InsertThousandsGrouping)
    Py_ssize_t result = (Py_ssize_t) __target___PyUnicode_InsertThousandsGrouping(a, b, c, d, e, f, g, h, i);
    STATS_AFTER(_PyUnicode_InsertThousandsGrouping)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyUnicode_IsAlpha, _PyUnicode_InsertThousandsGrouping)
int (*__target___PyUnicode_IsAlpha)(Py_UCS4) = NULL;
PyAPI_FUNC(int) _PyUnicode_IsAlpha(Py_UCS4 a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target___PyUnicode_IsAlpha == NULL) {
        __target___PyUnicode_IsAlpha = resolveAPI("_PyUnicode_IsAlpha");
    }
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
    if (__target___PyUnicode_IsCaseIgnorable == NULL) {
        __target___PyUnicode_IsCaseIgnorable = resolveAPI("_PyUnicode_IsCaseIgnorable");
    }
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
    if (__target___PyUnicode_IsCased == NULL) {
        __target___PyUnicode_IsCased = resolveAPI("_PyUnicode_IsCased");
    }
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
    if (__target___PyUnicode_IsDecimalDigit == NULL) {
        __target___PyUnicode_IsDecimalDigit = resolveAPI("_PyUnicode_IsDecimalDigit");
    }
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
    if (__target___PyUnicode_IsDigit == NULL) {
        __target___PyUnicode_IsDigit = resolveAPI("_PyUnicode_IsDigit");
    }
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
    if (__target___PyUnicode_IsLinebreak == NULL) {
        __target___PyUnicode_IsLinebreak = resolveAPI("_PyUnicode_IsLinebreak");
    }
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
    if (__target___PyUnicode_IsLowercase == NULL) {
        __target___PyUnicode_IsLowercase = resolveAPI("_PyUnicode_IsLowercase");
    }
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
    if (__target___PyUnicode_IsNumeric == NULL) {
        __target___PyUnicode_IsNumeric = resolveAPI("_PyUnicode_IsNumeric");
    }
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
    if (__target___PyUnicode_IsPrintable == NULL) {
        __target___PyUnicode_IsPrintable = resolveAPI("_PyUnicode_IsPrintable");
    }
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
    if (__target___PyUnicode_IsTitlecase == NULL) {
        __target___PyUnicode_IsTitlecase = resolveAPI("_PyUnicode_IsTitlecase");
    }
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
    if (__target___PyUnicode_IsUppercase == NULL) {
        __target___PyUnicode_IsUppercase = resolveAPI("_PyUnicode_IsUppercase");
    }
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
    if (__target___PyUnicode_IsWhitespace == NULL) {
        __target___PyUnicode_IsWhitespace = resolveAPI("_PyUnicode_IsWhitespace");
    }
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
    if (__target___PyUnicode_IsXidContinue == NULL) {
        __target___PyUnicode_IsXidContinue = resolveAPI("_PyUnicode_IsXidContinue");
    }
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
    if (__target___PyUnicode_IsXidStart == NULL) {
        __target___PyUnicode_IsXidStart = resolveAPI("_PyUnicode_IsXidStart");
    }
    STATS_BEFORE(_PyUnicode_IsXidStart)
    int result = (int) __target___PyUnicode_IsXidStart(a);
    STATS_AFTER(_PyUnicode_IsXidStart)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyUnicode_JoinArray, _PyUnicode_IsXidStart)
PyObject* (*__target___PyUnicode_JoinArray)(PyObject*, PyObject*const*, Py_ssize_t) = NULL;
PyAPI_FUNC(PyObject*) _PyUnicode_JoinArray(PyObject* a, PyObject*const* b, Py_ssize_t c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    if (__target___PyUnicode_JoinArray == NULL) {
        __target___PyUnicode_JoinArray = resolveAPI("_PyUnicode_JoinArray");
    }
    STATS_BEFORE(_PyUnicode_JoinArray)
    PyObject* result = (PyObject*) __target___PyUnicode_JoinArray(a, b, c);
    STATS_AFTER(_PyUnicode_JoinArray)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyUnicode_Ready, _PyUnicode_JoinArray)
int (*__target___PyUnicode_Ready)(PyObject*) = NULL;
PyAPI_FUNC(int) _PyUnicode_Ready(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target___PyUnicode_Ready == NULL) {
        __target___PyUnicode_Ready = resolveAPI("_PyUnicode_Ready");
    }
    STATS_BEFORE(_PyUnicode_Ready)
    int result = (int) __target___PyUnicode_Ready(a);
    STATS_AFTER(_PyUnicode_Ready)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyUnicode_ScanIdentifier, _PyUnicode_Ready)
Py_ssize_t (*__target___PyUnicode_ScanIdentifier)(PyObject*) = NULL;
PyAPI_FUNC(Py_ssize_t) _PyUnicode_ScanIdentifier(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target___PyUnicode_ScanIdentifier == NULL) {
        __target___PyUnicode_ScanIdentifier = resolveAPI("_PyUnicode_ScanIdentifier");
    }
    STATS_BEFORE(_PyUnicode_ScanIdentifier)
    Py_ssize_t result = (Py_ssize_t) __target___PyUnicode_ScanIdentifier(a);
    STATS_AFTER(_PyUnicode_ScanIdentifier)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyUnicode_ToDecimalDigit, _PyUnicode_ScanIdentifier)
int (*__target___PyUnicode_ToDecimalDigit)(Py_UCS4) = NULL;
PyAPI_FUNC(int) _PyUnicode_ToDecimalDigit(Py_UCS4 a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target___PyUnicode_ToDecimalDigit == NULL) {
        __target___PyUnicode_ToDecimalDigit = resolveAPI("_PyUnicode_ToDecimalDigit");
    }
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
    if (__target___PyUnicode_ToDigit == NULL) {
        __target___PyUnicode_ToDigit = resolveAPI("_PyUnicode_ToDigit");
    }
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
    if (__target___PyUnicode_ToFoldedFull == NULL) {
        __target___PyUnicode_ToFoldedFull = resolveAPI("_PyUnicode_ToFoldedFull");
    }
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
    if (__target___PyUnicode_ToLowerFull == NULL) {
        __target___PyUnicode_ToLowerFull = resolveAPI("_PyUnicode_ToLowerFull");
    }
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
    if (__target___PyUnicode_ToLowercase == NULL) {
        __target___PyUnicode_ToLowercase = resolveAPI("_PyUnicode_ToLowercase");
    }
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
    if (__target___PyUnicode_ToNumeric == NULL) {
        __target___PyUnicode_ToNumeric = resolveAPI("_PyUnicode_ToNumeric");
    }
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
    if (__target___PyUnicode_ToTitleFull == NULL) {
        __target___PyUnicode_ToTitleFull = resolveAPI("_PyUnicode_ToTitleFull");
    }
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
    if (__target___PyUnicode_ToTitlecase == NULL) {
        __target___PyUnicode_ToTitlecase = resolveAPI("_PyUnicode_ToTitlecase");
    }
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
    if (__target___PyUnicode_ToUpperFull == NULL) {
        __target___PyUnicode_ToUpperFull = resolveAPI("_PyUnicode_ToUpperFull");
    }
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
    if (__target___PyUnicode_ToUppercase == NULL) {
        __target___PyUnicode_ToUppercase = resolveAPI("_PyUnicode_ToUppercase");
    }
    STATS_BEFORE(_PyUnicode_ToUppercase)
    Py_UCS4 result = (Py_UCS4) __target___PyUnicode_ToUppercase(a);
    STATS_AFTER(_PyUnicode_ToUppercase)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyUnicode_TransformDecimalAndSpaceToASCII, _PyUnicode_ToUppercase)
PyObject* (*__target___PyUnicode_TransformDecimalAndSpaceToASCII)(PyObject*) = NULL;
PyAPI_FUNC(PyObject*) _PyUnicode_TransformDecimalAndSpaceToASCII(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target___PyUnicode_TransformDecimalAndSpaceToASCII == NULL) {
        __target___PyUnicode_TransformDecimalAndSpaceToASCII = resolveAPI("_PyUnicode_TransformDecimalAndSpaceToASCII");
    }
    STATS_BEFORE(_PyUnicode_TransformDecimalAndSpaceToASCII)
    PyObject* result = (PyObject*) __target___PyUnicode_TransformDecimalAndSpaceToASCII(a);
    STATS_AFTER(_PyUnicode_TransformDecimalAndSpaceToASCII)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyUnicode_WideCharString_Converter, _PyUnicode_TransformDecimalAndSpaceToASCII)
int (*__target___PyUnicode_WideCharString_Converter)(PyObject*, void*) = NULL;
PyAPI_FUNC(int) _PyUnicode_WideCharString_Converter(PyObject* a, void* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target___PyUnicode_WideCharString_Converter == NULL) {
        __target___PyUnicode_WideCharString_Converter = resolveAPI("_PyUnicode_WideCharString_Converter");
    }
    STATS_BEFORE(_PyUnicode_WideCharString_Converter)
    int result = (int) __target___PyUnicode_WideCharString_Converter(a, b);
    STATS_AFTER(_PyUnicode_WideCharString_Converter)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyUnicode_WideCharString_Opt_Converter, _PyUnicode_WideCharString_Converter)
int (*__target___PyUnicode_WideCharString_Opt_Converter)(PyObject*, void*) = NULL;
PyAPI_FUNC(int) _PyUnicode_WideCharString_Opt_Converter(PyObject* a, void* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target___PyUnicode_WideCharString_Opt_Converter == NULL) {
        __target___PyUnicode_WideCharString_Opt_Converter = resolveAPI("_PyUnicode_WideCharString_Opt_Converter");
    }
    STATS_BEFORE(_PyUnicode_WideCharString_Opt_Converter)
    int result = (int) __target___PyUnicode_WideCharString_Opt_Converter(a, b);
    STATS_AFTER(_PyUnicode_WideCharString_Opt_Converter)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyUnicode_XStrip, _PyUnicode_WideCharString_Opt_Converter)
PyObject* (*__target___PyUnicode_XStrip)(PyObject*, int, PyObject*) = NULL;
PyAPI_FUNC(PyObject*) _PyUnicode_XStrip(PyObject* a, int b, PyObject* c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    if (__target___PyUnicode_XStrip == NULL) {
        __target___PyUnicode_XStrip = resolveAPI("_PyUnicode_XStrip");
    }
    STATS_BEFORE(_PyUnicode_XStrip)
    PyObject* result = (PyObject*) __target___PyUnicode_XStrip(a, b, c);
    STATS_AFTER(_PyUnicode_XStrip)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyUnicode_get_wstr_length, _PyUnicode_XStrip)
Py_ssize_t (*__target___PyUnicode_get_wstr_length)(PyObject*) = NULL;
PyAPI_FUNC(Py_ssize_t) _PyUnicode_get_wstr_length(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target___PyUnicode_get_wstr_length == NULL) {
        __target___PyUnicode_get_wstr_length = resolveAPI("_PyUnicode_get_wstr_length");
    }
    STATS_BEFORE(_PyUnicode_get_wstr_length)
    Py_ssize_t result = (Py_ssize_t) __target___PyUnicode_get_wstr_length(a);
    STATS_AFTER(_PyUnicode_get_wstr_length)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyWarnings_Init, _PyUnicode_get_wstr_length)
PyObject* (*__target___PyWarnings_Init)() = NULL;
PyAPI_FUNC(PyObject*) _PyWarnings_Init() {
    LOGS("");
    if (__target___PyWarnings_Init == NULL) {
        __target___PyWarnings_Init = resolveAPI("_PyWarnings_Init");
    }
    STATS_BEFORE(_PyWarnings_Init)
    PyObject* result = (PyObject*) __target___PyWarnings_Init();
    STATS_AFTER(_PyWarnings_Init)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_PyWeakref_ClearRef, _PyWarnings_Init)
void (*__target___PyWeakref_ClearRef)(PyWeakReference*) = NULL;
PyAPI_FUNC(void) _PyWeakref_ClearRef(PyWeakReference* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target___PyWeakref_ClearRef == NULL) {
        __target___PyWeakref_ClearRef = resolveAPI("_PyWeakref_ClearRef");
    }
    STATS_BEFORE(_PyWeakref_ClearRef)
    __target___PyWeakref_ClearRef(a);
    STATS_AFTER(_PyWeakref_ClearRef)
    LOG_AFTER_VOID
}
STATS_CONTAINER(_PyWeakref_GetWeakrefCount, _PyWeakref_ClearRef)
Py_ssize_t (*__target___PyWeakref_GetWeakrefCount)(PyWeakReference*) = NULL;
PyAPI_FUNC(Py_ssize_t) _PyWeakref_GetWeakrefCount(PyWeakReference* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target___PyWeakref_GetWeakrefCount == NULL) {
        __target___PyWeakref_GetWeakrefCount = resolveAPI("_PyWeakref_GetWeakrefCount");
    }
    STATS_BEFORE(_PyWeakref_GetWeakrefCount)
    Py_ssize_t result = (Py_ssize_t) __target___PyWeakref_GetWeakrefCount(a);
    STATS_AFTER(_PyWeakref_GetWeakrefCount)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_Py_BreakPoint, _PyWeakref_GetWeakrefCount)
void (*__target___Py_BreakPoint)() = NULL;
PyAPI_FUNC(void) _Py_BreakPoint() {
    LOGS("");
    if (__target___Py_BreakPoint == NULL) {
        __target___Py_BreakPoint = resolveAPI("_Py_BreakPoint");
    }
    STATS_BEFORE(_Py_BreakPoint)
    __target___Py_BreakPoint();
    STATS_AFTER(_Py_BreakPoint)
    LOG_AFTER_VOID
}
STATS_CONTAINER(_Py_CheckFunctionResult, _Py_BreakPoint)
PyObject* (*__target___Py_CheckFunctionResult)(PyThreadState*, PyObject*, PyObject*, const char*) = NULL;
PyAPI_FUNC(PyObject*) _Py_CheckFunctionResult(PyThreadState* a, PyObject* b, PyObject* c, const char* d) {
    LOG("0x%lx 0x%lx 0x%lx '%s'(0x%lx)", (unsigned long) a, (unsigned long) b, (unsigned long) c, d?d:"<null>", (unsigned long) d);
    if (__target___Py_CheckFunctionResult == NULL) {
        __target___Py_CheckFunctionResult = resolveAPI("_Py_CheckFunctionResult");
    }
    STATS_BEFORE(_Py_CheckFunctionResult)
    PyObject* result = (PyObject*) __target___Py_CheckFunctionResult(a, b, c, d);
    STATS_AFTER(_Py_CheckFunctionResult)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_Py_CheckRecursiveCall, _Py_CheckFunctionResult)
int (*__target___Py_CheckRecursiveCall)(const char*) = NULL;
PyAPI_FUNC(int) _Py_CheckRecursiveCall(const char* a) {
    LOG("'%s'(0x%lx)", a?a:"<null>", (unsigned long) a);
    if (__target___Py_CheckRecursiveCall == NULL) {
        __target___Py_CheckRecursiveCall = resolveAPI("_Py_CheckRecursiveCall");
    }
    STATS_BEFORE(_Py_CheckRecursiveCall)
    int result = (int) __target___Py_CheckRecursiveCall(a);
    STATS_AFTER(_Py_CheckRecursiveCall)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_Py_CoerceLegacyLocale, _Py_CheckRecursiveCall)
int (*__target___Py_CoerceLegacyLocale)(int) = NULL;
PyAPI_FUNC(int) _Py_CoerceLegacyLocale(int a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target___Py_CoerceLegacyLocale == NULL) {
        __target___Py_CoerceLegacyLocale = resolveAPI("_Py_CoerceLegacyLocale");
    }
    STATS_BEFORE(_Py_CoerceLegacyLocale)
    int result = (int) __target___Py_CoerceLegacyLocale(a);
    STATS_AFTER(_Py_CoerceLegacyLocale)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_Py_Dealloc, _Py_CoerceLegacyLocale)
void (*__target___Py_Dealloc)(PyObject*) = NULL;
PyAPI_FUNC(void) _Py_Dealloc(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target___Py_Dealloc == NULL) {
        __target___Py_Dealloc = resolveAPI("_Py_Dealloc");
    }
    STATS_BEFORE(_Py_Dealloc)
    __target___Py_Dealloc(a);
    STATS_AFTER(_Py_Dealloc)
    LOG_AFTER_VOID
}
STATS_CONTAINER(_Py_DecRef, _Py_Dealloc)
void (*__target___Py_DecRef)(PyObject*) = NULL;
PyAPI_FUNC(void) _Py_DecRef(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target___Py_DecRef == NULL) {
        __target___Py_DecRef = resolveAPI("_Py_DecRef");
    }
    STATS_BEFORE(_Py_DecRef)
    __target___Py_DecRef(a);
    STATS_AFTER(_Py_DecRef)
    LOG_AFTER_VOID
}
STATS_CONTAINER(_Py_DecodeLocaleEx, _Py_DecRef)
int (*__target___Py_DecodeLocaleEx)(const char*, wchar_t**, size_t*, const char**, int, _Py_error_handler) = NULL;
PyAPI_FUNC(int) _Py_DecodeLocaleEx(const char* a, wchar_t** b, size_t* c, const char** d, int e, _Py_error_handler f) {
    LOG("'%s'(0x%lx) 0x%lx 0x%lx 0x%lx 0x%lx 0x%lx", a?a:"<null>", (unsigned long) a, (unsigned long) b, (unsigned long) c, (unsigned long) d, (unsigned long) e, (unsigned long) f);
    if (__target___Py_DecodeLocaleEx == NULL) {
        __target___Py_DecodeLocaleEx = resolveAPI("_Py_DecodeLocaleEx");
    }
    STATS_BEFORE(_Py_DecodeLocaleEx)
    int result = (int) __target___Py_DecodeLocaleEx(a, b, c, d, e, f);
    STATS_AFTER(_Py_DecodeLocaleEx)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_Py_DisplaySourceLine, _Py_DecodeLocaleEx)
int (*__target___Py_DisplaySourceLine)(PyObject*, PyObject*, int, int) = NULL;
PyAPI_FUNC(int) _Py_DisplaySourceLine(PyObject* a, PyObject* b, int c, int d) {
    LOG("0x%lx 0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c, (unsigned long) d);
    if (__target___Py_DisplaySourceLine == NULL) {
        __target___Py_DisplaySourceLine = resolveAPI("_Py_DisplaySourceLine");
    }
    STATS_BEFORE(_Py_DisplaySourceLine)
    int result = (int) __target___Py_DisplaySourceLine(a, b, c, d);
    STATS_AFTER(_Py_DisplaySourceLine)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_Py_EncodeLocaleEx, _Py_DisplaySourceLine)
int (*__target___Py_EncodeLocaleEx)(const wchar_t*, char**, size_t*, const char**, int, _Py_error_handler) = NULL;
PyAPI_FUNC(int) _Py_EncodeLocaleEx(const wchar_t* a, char** b, size_t* c, const char** d, int e, _Py_error_handler f) {
    LOG("0x%lx 0x%lx 0x%lx 0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c, (unsigned long) d, (unsigned long) e, (unsigned long) f);
    if (__target___Py_EncodeLocaleEx == NULL) {
        __target___Py_EncodeLocaleEx = resolveAPI("_Py_EncodeLocaleEx");
    }
    STATS_BEFORE(_Py_EncodeLocaleEx)
    int result = (int) __target___Py_EncodeLocaleEx(a, b, c, d, e, f);
    STATS_AFTER(_Py_EncodeLocaleEx)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_Py_EncodeLocaleRaw, _Py_EncodeLocaleEx)
char* (*__target___Py_EncodeLocaleRaw)(const wchar_t*, size_t*) = NULL;
PyAPI_FUNC(char*) _Py_EncodeLocaleRaw(const wchar_t* a, size_t* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target___Py_EncodeLocaleRaw == NULL) {
        __target___Py_EncodeLocaleRaw = resolveAPI("_Py_EncodeLocaleRaw");
    }
    STATS_BEFORE(_Py_EncodeLocaleRaw)
    char* result = (char*) __target___Py_EncodeLocaleRaw(a, b);
    STATS_AFTER(_Py_EncodeLocaleRaw)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_Py_FatalErrorFunc, _Py_EncodeLocaleRaw)
void (*__target___Py_FatalErrorFunc)(const char*, const char*) = NULL;
PyAPI_FUNC(void) _Py_FatalErrorFunc(const char* a, const char* b) {
    LOG("'%s'(0x%lx) '%s'(0x%lx)", a?a:"<null>", (unsigned long) a, b?b:"<null>", (unsigned long) b);
    if (__target___Py_FatalErrorFunc == NULL) {
        __target___Py_FatalErrorFunc = resolveAPI("_Py_FatalErrorFunc");
    }
    STATS_BEFORE(_Py_FatalErrorFunc)
    __target___Py_FatalErrorFunc(a, b);
    STATS_AFTER(_Py_FatalErrorFunc)
    LOG_AFTER_VOID
    abort();
}
STATS_CONTAINER(_Py_FdIsInteractive, _Py_FatalErrorFunc)
int (*__target___Py_FdIsInteractive)(FILE*, PyObject*) = NULL;
PyAPI_FUNC(int) _Py_FdIsInteractive(FILE* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target___Py_FdIsInteractive == NULL) {
        __target___Py_FdIsInteractive = resolveAPI("_Py_FdIsInteractive");
    }
    STATS_BEFORE(_Py_FdIsInteractive)
    int result = (int) __target___Py_FdIsInteractive(a, b);
    STATS_AFTER(_Py_FdIsInteractive)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_Py_FreeCharPArray, _Py_FdIsInteractive)
void (*__target___Py_FreeCharPArray)(char*const []) = NULL;
PyAPI_FUNC(void) _Py_FreeCharPArray(char*const a[]) {
    LOG("0x%lx", (unsigned long) a);
    if (__target___Py_FreeCharPArray == NULL) {
        __target___Py_FreeCharPArray = resolveAPI("_Py_FreeCharPArray");
    }
    STATS_BEFORE(_Py_FreeCharPArray)
    __target___Py_FreeCharPArray(a);
    STATS_AFTER(_Py_FreeCharPArray)
    LOG_AFTER_VOID
}
STATS_CONTAINER(_Py_GetAllocatedBlocks, _Py_FreeCharPArray)
Py_ssize_t (*__target___Py_GetAllocatedBlocks)() = NULL;
PyAPI_FUNC(Py_ssize_t) _Py_GetAllocatedBlocks() {
    LOGS("");
    if (__target___Py_GetAllocatedBlocks == NULL) {
        __target___Py_GetAllocatedBlocks = resolveAPI("_Py_GetAllocatedBlocks");
    }
    STATS_BEFORE(_Py_GetAllocatedBlocks)
    Py_ssize_t result = (Py_ssize_t) __target___Py_GetAllocatedBlocks();
    STATS_AFTER(_Py_GetAllocatedBlocks)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_Py_GetConfig, _Py_GetAllocatedBlocks)
const PyConfig* (*__target___Py_GetConfig)() = NULL;
PyAPI_FUNC(const PyConfig*) _Py_GetConfig() {
    LOGS("");
    if (__target___Py_GetConfig == NULL) {
        __target___Py_GetConfig = resolveAPI("_Py_GetConfig");
    }
    STATS_BEFORE(_Py_GetConfig)
    const PyConfig* result = (const PyConfig*) __target___Py_GetConfig();
    STATS_AFTER(_Py_GetConfig)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_Py_GetErrorHandler, _Py_GetConfig)
_Py_error_handler (*__target___Py_GetErrorHandler)(const char*) = NULL;
PyAPI_FUNC(_Py_error_handler) _Py_GetErrorHandler(const char* a) {
    LOG("'%s'(0x%lx)", a?a:"<null>", (unsigned long) a);
    if (__target___Py_GetErrorHandler == NULL) {
        __target___Py_GetErrorHandler = resolveAPI("_Py_GetErrorHandler");
    }
    STATS_BEFORE(_Py_GetErrorHandler)
    _Py_error_handler result = (_Py_error_handler) __target___Py_GetErrorHandler(a);
    STATS_AFTER(_Py_GetErrorHandler)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_Py_HashBytes, _Py_GetErrorHandler)
Py_hash_t (*__target___Py_HashBytes)(const void*, Py_ssize_t) = NULL;
PyAPI_FUNC(Py_hash_t) _Py_HashBytes(const void* a, Py_ssize_t b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target___Py_HashBytes == NULL) {
        __target___Py_HashBytes = resolveAPI("_Py_HashBytes");
    }
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
    if (__target___Py_HashDouble == NULL) {
        __target___Py_HashDouble = resolveAPI("_Py_HashDouble");
    }
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
    if (__target___Py_HashPointer == NULL) {
        __target___Py_HashPointer = resolveAPI("_Py_HashPointer");
    }
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
    if (__target___Py_HashPointerRaw == NULL) {
        __target___Py_HashPointerRaw = resolveAPI("_Py_HashPointerRaw");
    }
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
    if (__target___Py_IncRef == NULL) {
        __target___Py_IncRef = resolveAPI("_Py_IncRef");
    }
    STATS_BEFORE(_Py_IncRef)
    __target___Py_IncRef(a);
    STATS_AFTER(_Py_IncRef)
    LOG_AFTER_VOID
}
STATS_CONTAINER(_Py_InitializeMain, _Py_IncRef)
PyStatus (*__target___Py_InitializeMain)() = NULL;
PyAPI_FUNC(PyStatus) _Py_InitializeMain() {
    LOGS("");
    if (__target___Py_InitializeMain == NULL) {
        __target___Py_InitializeMain = resolveAPI("_Py_InitializeMain");
    }
    STATS_BEFORE(_Py_InitializeMain)
    PyStatus result = (PyStatus) __target___Py_InitializeMain();
    STATS_AFTER(_Py_InitializeMain)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_Py_IsCoreInitialized, _Py_InitializeMain)
int (*__target___Py_IsCoreInitialized)() = NULL;
PyAPI_FUNC(int) _Py_IsCoreInitialized() {
    LOGS("");
    if (__target___Py_IsCoreInitialized == NULL) {
        __target___Py_IsCoreInitialized = resolveAPI("_Py_IsCoreInitialized");
    }
    STATS_BEFORE(_Py_IsCoreInitialized)
    int result = (int) __target___Py_IsCoreInitialized();
    STATS_AFTER(_Py_IsCoreInitialized)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_Py_IsFinalizing, _Py_IsCoreInitialized)
int (*__target___Py_IsFinalizing)() = NULL;
PyAPI_FUNC(int) _Py_IsFinalizing() {
    LOGS("");
    if (__target___Py_IsFinalizing == NULL) {
        __target___Py_IsFinalizing = resolveAPI("_Py_IsFinalizing");
    }
    STATS_BEFORE(_Py_IsFinalizing)
    int result = (int) __target___Py_IsFinalizing();
    STATS_AFTER(_Py_IsFinalizing)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_Py_LegacyLocaleDetected, _Py_IsFinalizing)
int (*__target___Py_LegacyLocaleDetected)(int) = NULL;
PyAPI_FUNC(int) _Py_LegacyLocaleDetected(int a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target___Py_LegacyLocaleDetected == NULL) {
        __target___Py_LegacyLocaleDetected = resolveAPI("_Py_LegacyLocaleDetected");
    }
    STATS_BEFORE(_Py_LegacyLocaleDetected)
    int result = (int) __target___Py_LegacyLocaleDetected(a);
    STATS_AFTER(_Py_LegacyLocaleDetected)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_Py_Mangle, _Py_LegacyLocaleDetected)
PyObject* (*__target___Py_Mangle)(PyObject*, PyObject*) = NULL;
PyAPI_FUNC(PyObject*) _Py_Mangle(PyObject* a, PyObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target___Py_Mangle == NULL) {
        __target___Py_Mangle = resolveAPI("_Py_Mangle");
    }
    STATS_BEFORE(_Py_Mangle)
    PyObject* result = (PyObject*) __target___Py_Mangle(a, b);
    STATS_AFTER(_Py_Mangle)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_Py_NewInterpreter, _Py_Mangle)
PyThreadState* (*__target___Py_NewInterpreter)(int) = NULL;
PyAPI_FUNC(PyThreadState*) _Py_NewInterpreter(int a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target___Py_NewInterpreter == NULL) {
        __target___Py_NewInterpreter = resolveAPI("_Py_NewInterpreter");
    }
    STATS_BEFORE(_Py_NewInterpreter)
    PyThreadState* result = (PyThreadState*) __target___Py_NewInterpreter(a);
    STATS_AFTER(_Py_NewInterpreter)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_Py_NewReference, _Py_NewInterpreter)
void (*__target___Py_NewReference)(PyObject*) = NULL;
PyAPI_FUNC(void) _Py_NewReference(PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target___Py_NewReference == NULL) {
        __target___Py_NewReference = resolveAPI("_Py_NewReference");
    }
    STATS_BEFORE(_Py_NewReference)
    __target___Py_NewReference(a);
    STATS_AFTER(_Py_NewReference)
    LOG_AFTER_VOID
}
STATS_CONTAINER(_Py_REFCNT, _Py_NewReference)
Py_ssize_t (*__target___Py_REFCNT)(const PyObject*) = NULL;
PyAPI_FUNC(Py_ssize_t) _Py_REFCNT(const PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target___Py_REFCNT == NULL) {
        __target___Py_REFCNT = resolveAPI("_Py_REFCNT");
    }
    STATS_BEFORE(_Py_REFCNT)
    Py_ssize_t result = (Py_ssize_t) __target___Py_REFCNT(a);
    STATS_AFTER(_Py_REFCNT)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_Py_RestoreSignals, _Py_REFCNT)
void (*__target___Py_RestoreSignals)() = NULL;
PyAPI_FUNC(void) _Py_RestoreSignals() {
    LOGS("");
    if (__target___Py_RestoreSignals == NULL) {
        __target___Py_RestoreSignals = resolveAPI("_Py_RestoreSignals");
    }
    STATS_BEFORE(_Py_RestoreSignals)
    __target___Py_RestoreSignals();
    STATS_AFTER(_Py_RestoreSignals)
    LOG_AFTER_VOID
}
STATS_CONTAINER(_Py_SET_REFCNT, _Py_RestoreSignals)
Py_ssize_t (*__target___Py_SET_REFCNT)(PyObject*, Py_ssize_t) = NULL;
PyAPI_FUNC(Py_ssize_t) _Py_SET_REFCNT(PyObject* a, Py_ssize_t b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target___Py_SET_REFCNT == NULL) {
        __target___Py_SET_REFCNT = resolveAPI("_Py_SET_REFCNT");
    }
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
    if (__target___Py_SET_SIZE == NULL) {
        __target___Py_SET_SIZE = resolveAPI("_Py_SET_SIZE");
    }
    STATS_BEFORE(_Py_SET_SIZE)
    __target___Py_SET_SIZE(a, b);
    STATS_AFTER(_Py_SET_SIZE)
    LOG_AFTER_VOID
}
STATS_CONTAINER(_Py_SET_TYPE, _Py_SET_SIZE)
void (*__target___Py_SET_TYPE)(PyObject*, PyTypeObject*) = NULL;
PyAPI_FUNC(void) _Py_SET_TYPE(PyObject* a, PyTypeObject* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target___Py_SET_TYPE == NULL) {
        __target___Py_SET_TYPE = resolveAPI("_Py_SET_TYPE");
    }
    STATS_BEFORE(_Py_SET_TYPE)
    __target___Py_SET_TYPE(a, b);
    STATS_AFTER(_Py_SET_TYPE)
    LOG_AFTER_VOID
}
STATS_CONTAINER(_Py_SIZE, _Py_SET_TYPE)
Py_ssize_t (*__target___Py_SIZE)(const PyVarObject*) = NULL;
PyAPI_FUNC(Py_ssize_t) _Py_SIZE(const PyVarObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target___Py_SIZE == NULL) {
        __target___Py_SIZE = resolveAPI("_Py_SIZE");
    }
    STATS_BEFORE(_Py_SIZE)
    Py_ssize_t result = (Py_ssize_t) __target___Py_SIZE(a);
    STATS_AFTER(_Py_SIZE)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_Py_SetLocaleFromEnv, _Py_SIZE)
char* (*__target___Py_SetLocaleFromEnv)(int) = NULL;
PyAPI_FUNC(char*) _Py_SetLocaleFromEnv(int a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target___Py_SetLocaleFromEnv == NULL) {
        __target___Py_SetLocaleFromEnv = resolveAPI("_Py_SetLocaleFromEnv");
    }
    STATS_BEFORE(_Py_SetLocaleFromEnv)
    char* result = (char*) __target___Py_SetLocaleFromEnv(a);
    STATS_AFTER(_Py_SetLocaleFromEnv)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_Py_SetProgramFullPath, _Py_SetLocaleFromEnv)
void (*__target___Py_SetProgramFullPath)(const wchar_t*) = NULL;
PyAPI_FUNC(void) _Py_SetProgramFullPath(const wchar_t* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target___Py_SetProgramFullPath == NULL) {
        __target___Py_SetProgramFullPath = resolveAPI("_Py_SetProgramFullPath");
    }
    STATS_BEFORE(_Py_SetProgramFullPath)
    __target___Py_SetProgramFullPath(a);
    STATS_AFTER(_Py_SetProgramFullPath)
    LOG_AFTER_VOID
}
STATS_CONTAINER(_Py_SourceAsString, _Py_SetProgramFullPath)
const char* (*__target___Py_SourceAsString)(PyObject*, const char*, const char*, PyCompilerFlags*, PyObject**) = NULL;
PyAPI_FUNC(const char*) _Py_SourceAsString(PyObject* a, const char* b, const char* c, PyCompilerFlags* d, PyObject** e) {
    LOG("0x%lx '%s'(0x%lx) '%s'(0x%lx) 0x%lx 0x%lx", (unsigned long) a, b?b:"<null>", (unsigned long) b, c?c:"<null>", (unsigned long) c, (unsigned long) d, (unsigned long) e);
    if (__target___Py_SourceAsString == NULL) {
        __target___Py_SourceAsString = resolveAPI("_Py_SourceAsString");
    }
    STATS_BEFORE(_Py_SourceAsString)
    const char* result = (const char*) __target___Py_SourceAsString(a, b, c, d, e);
    STATS_AFTER(_Py_SourceAsString)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_Py_TYPE, _Py_SourceAsString)
PyTypeObject* (*__target___Py_TYPE)(const PyObject*) = NULL;
PyAPI_FUNC(PyTypeObject*) _Py_TYPE(const PyObject* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target___Py_TYPE == NULL) {
        __target___Py_TYPE = resolveAPI("_Py_TYPE");
    }
    STATS_BEFORE(_Py_TYPE)
    PyTypeObject* result = (PyTypeObject*) __target___Py_TYPE(a);
    STATS_AFTER(_Py_TYPE)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_Py_VaBuildStack, _Py_TYPE)
PyObject** (*__target___Py_VaBuildStack)(PyObject**, Py_ssize_t, const char*, va_list, Py_ssize_t*) = NULL;
PyAPI_FUNC(PyObject**) _Py_VaBuildStack(PyObject** a, Py_ssize_t b, const char* c, va_list d, Py_ssize_t* e) {
    LOG("0x%lx 0x%lx '%s'(0x%lx) 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, c?c:"<null>", (unsigned long) c, (unsigned long) d, (unsigned long) e);
    if (__target___Py_VaBuildStack == NULL) {
        __target___Py_VaBuildStack = resolveAPI("_Py_VaBuildStack");
    }
    STATS_BEFORE(_Py_VaBuildStack)
    PyObject** result = (PyObject**) __target___Py_VaBuildStack(a, b, c, d, e);
    STATS_AFTER(_Py_VaBuildStack)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_Py_VaBuildStack_SizeT, _Py_VaBuildStack)
PyObject** (*__target___Py_VaBuildStack_SizeT)(PyObject**, Py_ssize_t, const char*, va_list, Py_ssize_t*) = NULL;
PyAPI_FUNC(PyObject**) _Py_VaBuildStack_SizeT(PyObject** a, Py_ssize_t b, const char* c, va_list d, Py_ssize_t* e) {
    LOG("0x%lx 0x%lx '%s'(0x%lx) 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, c?c:"<null>", (unsigned long) c, (unsigned long) d, (unsigned long) e);
    if (__target___Py_VaBuildStack_SizeT == NULL) {
        __target___Py_VaBuildStack_SizeT = resolveAPI("_Py_VaBuildStack_SizeT");
    }
    STATS_BEFORE(_Py_VaBuildStack_SizeT)
    PyObject** result = (PyObject**) __target___Py_VaBuildStack_SizeT(a, b, c, d, e);
    STATS_AFTER(_Py_VaBuildStack_SizeT)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_Py_VaBuildValue_SizeT, _Py_VaBuildStack_SizeT)
PyObject* (*__target___Py_VaBuildValue_SizeT)(const char*, va_list) = NULL;
PyAPI_FUNC(PyObject*) _Py_VaBuildValue_SizeT(const char* a, va_list b) {
    LOG("'%s'(0x%lx) 0x%lx", a?a:"<null>", (unsigned long) a, (unsigned long) b);
    if (__target___Py_VaBuildValue_SizeT == NULL) {
        __target___Py_VaBuildValue_SizeT = resolveAPI("_Py_VaBuildValue_SizeT");
    }
    STATS_BEFORE(_Py_VaBuildValue_SizeT)
    PyObject* result = (PyObject*) __target___Py_VaBuildValue_SizeT(a, b);
    STATS_AFTER(_Py_VaBuildValue_SizeT)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_Py_abspath, _Py_VaBuildValue_SizeT)
int (*__target___Py_abspath)(const wchar_t*, wchar_t**) = NULL;
PyAPI_FUNC(int) _Py_abspath(const wchar_t* a, wchar_t** b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target___Py_abspath == NULL) {
        __target___Py_abspath = resolveAPI("_Py_abspath");
    }
    STATS_BEFORE(_Py_abspath)
    int result = (int) __target___Py_abspath(a, b);
    STATS_AFTER(_Py_abspath)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_Py_add_one_to_index_C, _Py_abspath)
void (*__target___Py_add_one_to_index_C)(int, Py_ssize_t*, const Py_ssize_t*) = NULL;
PyAPI_FUNC(void) _Py_add_one_to_index_C(int a, Py_ssize_t* b, const Py_ssize_t* c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    if (__target___Py_add_one_to_index_C == NULL) {
        __target___Py_add_one_to_index_C = resolveAPI("_Py_add_one_to_index_C");
    }
    STATS_BEFORE(_Py_add_one_to_index_C)
    __target___Py_add_one_to_index_C(a, b, c);
    STATS_AFTER(_Py_add_one_to_index_C)
    LOG_AFTER_VOID
}
STATS_CONTAINER(_Py_add_one_to_index_F, _Py_add_one_to_index_C)
void (*__target___Py_add_one_to_index_F)(int, Py_ssize_t*, const Py_ssize_t*) = NULL;
PyAPI_FUNC(void) _Py_add_one_to_index_F(int a, Py_ssize_t* b, const Py_ssize_t* c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    if (__target___Py_add_one_to_index_F == NULL) {
        __target___Py_add_one_to_index_F = resolveAPI("_Py_add_one_to_index_F");
    }
    STATS_BEFORE(_Py_add_one_to_index_F)
    __target___Py_add_one_to_index_F(a, b, c);
    STATS_AFTER(_Py_add_one_to_index_F)
    LOG_AFTER_VOID
}
STATS_CONTAINER(_Py_convert_optional_to_ssize_t, _Py_add_one_to_index_F)
int (*__target___Py_convert_optional_to_ssize_t)(PyObject*, void*) = NULL;
PyAPI_FUNC(int) _Py_convert_optional_to_ssize_t(PyObject* a, void* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target___Py_convert_optional_to_ssize_t == NULL) {
        __target___Py_convert_optional_to_ssize_t = resolveAPI("_Py_convert_optional_to_ssize_t");
    }
    STATS_BEFORE(_Py_convert_optional_to_ssize_t)
    int result = (int) __target___Py_convert_optional_to_ssize_t(a, b);
    STATS_AFTER(_Py_convert_optional_to_ssize_t)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_Py_device_encoding, _Py_convert_optional_to_ssize_t)
PyObject* (*__target___Py_device_encoding)(int) = NULL;
PyAPI_FUNC(PyObject*) _Py_device_encoding(int a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target___Py_device_encoding == NULL) {
        __target___Py_device_encoding = resolveAPI("_Py_device_encoding");
    }
    STATS_BEFORE(_Py_device_encoding)
    PyObject* result = (PyObject*) __target___Py_device_encoding(a);
    STATS_AFTER(_Py_device_encoding)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_Py_dg_dtoa, _Py_device_encoding)
char* (*__target___Py_dg_dtoa)(double, int, int, int*, int*, char**) = NULL;
PyAPI_FUNC(char*) _Py_dg_dtoa(double a, int b, int c, int* d, int* e, char** f) {
    LOG("0x%lx 0x%lx 0x%lx 0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c, (unsigned long) d, (unsigned long) e, (unsigned long) f);
    if (__target___Py_dg_dtoa == NULL) {
        __target___Py_dg_dtoa = resolveAPI("_Py_dg_dtoa");
    }
    STATS_BEFORE(_Py_dg_dtoa)
    char* result = (char*) __target___Py_dg_dtoa(a, b, c, d, e, f);
    STATS_AFTER(_Py_dg_dtoa)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_Py_dg_freedtoa, _Py_dg_dtoa)
void (*__target___Py_dg_freedtoa)(char*) = NULL;
PyAPI_FUNC(void) _Py_dg_freedtoa(char* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target___Py_dg_freedtoa == NULL) {
        __target___Py_dg_freedtoa = resolveAPI("_Py_dg_freedtoa");
    }
    STATS_BEFORE(_Py_dg_freedtoa)
    __target___Py_dg_freedtoa(a);
    STATS_AFTER(_Py_dg_freedtoa)
    LOG_AFTER_VOID
}
STATS_CONTAINER(_Py_dg_infinity, _Py_dg_freedtoa)
double (*__target___Py_dg_infinity)(int) = NULL;
PyAPI_FUNC(double) _Py_dg_infinity(int a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target___Py_dg_infinity == NULL) {
        __target___Py_dg_infinity = resolveAPI("_Py_dg_infinity");
    }
    STATS_BEFORE(_Py_dg_infinity)
    double result = (double) __target___Py_dg_infinity(a);
    STATS_AFTER(_Py_dg_infinity)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_Py_dg_stdnan, _Py_dg_infinity)
double (*__target___Py_dg_stdnan)(int) = NULL;
PyAPI_FUNC(double) _Py_dg_stdnan(int a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target___Py_dg_stdnan == NULL) {
        __target___Py_dg_stdnan = resolveAPI("_Py_dg_stdnan");
    }
    STATS_BEFORE(_Py_dg_stdnan)
    double result = (double) __target___Py_dg_stdnan(a);
    STATS_AFTER(_Py_dg_stdnan)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_Py_dg_strtod, _Py_dg_stdnan)
double (*__target___Py_dg_strtod)(const char*, char**) = NULL;
PyAPI_FUNC(double) _Py_dg_strtod(const char* a, char** b) {
    LOG("'%s'(0x%lx) 0x%lx", a?a:"<null>", (unsigned long) a, (unsigned long) b);
    if (__target___Py_dg_strtod == NULL) {
        __target___Py_dg_strtod = resolveAPI("_Py_dg_strtod");
    }
    STATS_BEFORE(_Py_dg_strtod)
    double result = (double) __target___Py_dg_strtod(a, b);
    STATS_AFTER(_Py_dg_strtod)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_Py_dup, _Py_dg_strtod)
int (*__target___Py_dup)(int) = NULL;
PyAPI_FUNC(int) _Py_dup(int a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target___Py_dup == NULL) {
        __target___Py_dup = resolveAPI("_Py_dup");
    }
    STATS_BEFORE(_Py_dup)
    int result = (int) __target___Py_dup(a);
    STATS_AFTER(_Py_dup)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_Py_fopen, _Py_dup)
FILE* (*__target___Py_fopen)(const char*, const char*) = NULL;
PyAPI_FUNC(FILE*) _Py_fopen(const char* a, const char* b) {
    LOG("'%s'(0x%lx) '%s'(0x%lx)", a?a:"<null>", (unsigned long) a, b?b:"<null>", (unsigned long) b);
    if (__target___Py_fopen == NULL) {
        __target___Py_fopen = resolveAPI("_Py_fopen");
    }
    STATS_BEFORE(_Py_fopen)
    FILE* result = (FILE*) __target___Py_fopen(a, b);
    STATS_AFTER(_Py_fopen)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_Py_fopen_obj, _Py_fopen)
FILE* (*__target___Py_fopen_obj)(PyObject*, const char*) = NULL;
PyAPI_FUNC(FILE*) _Py_fopen_obj(PyObject* a, const char* b) {
    LOG("0x%lx '%s'(0x%lx)", (unsigned long) a, b?b:"<null>", (unsigned long) b);
    if (__target___Py_fopen_obj == NULL) {
        __target___Py_fopen_obj = resolveAPI("_Py_fopen_obj");
    }
    STATS_BEFORE(_Py_fopen_obj)
    FILE* result = (FILE*) __target___Py_fopen_obj(a, b);
    STATS_AFTER(_Py_fopen_obj)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_Py_fstat, _Py_fopen_obj)
int (*__target___Py_fstat)(int, struct stat*) = NULL;
PyAPI_FUNC(int) _Py_fstat(int a, struct stat* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target___Py_fstat == NULL) {
        __target___Py_fstat = resolveAPI("_Py_fstat");
    }
    STATS_BEFORE(_Py_fstat)
    int result = (int) __target___Py_fstat(a, b);
    STATS_AFTER(_Py_fstat)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_Py_fstat_noraise, _Py_fstat)
int (*__target___Py_fstat_noraise)(int, struct stat*) = NULL;
PyAPI_FUNC(int) _Py_fstat_noraise(int a, struct stat* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target___Py_fstat_noraise == NULL) {
        __target___Py_fstat_noraise = resolveAPI("_Py_fstat_noraise");
    }
    STATS_BEFORE(_Py_fstat_noraise)
    int result = (int) __target___Py_fstat_noraise(a, b);
    STATS_AFTER(_Py_fstat_noraise)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_Py_get_blocking, _Py_fstat_noraise)
int (*__target___Py_get_blocking)(int) = NULL;
PyAPI_FUNC(int) _Py_get_blocking(int a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target___Py_get_blocking == NULL) {
        __target___Py_get_blocking = resolveAPI("_Py_get_blocking");
    }
    STATS_BEFORE(_Py_get_blocking)
    int result = (int) __target___Py_get_blocking(a);
    STATS_AFTER(_Py_get_blocking)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_Py_get_inheritable, _Py_get_blocking)
int (*__target___Py_get_inheritable)(int) = NULL;
PyAPI_FUNC(int) _Py_get_inheritable(int a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target___Py_get_inheritable == NULL) {
        __target___Py_get_inheritable = resolveAPI("_Py_get_inheritable");
    }
    STATS_BEFORE(_Py_get_inheritable)
    int result = (int) __target___Py_get_inheritable(a);
    STATS_AFTER(_Py_get_inheritable)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_Py_gitidentifier, _Py_get_inheritable)
const char* (*__target___Py_gitidentifier)() = NULL;
PyAPI_FUNC(const char*) _Py_gitidentifier() {
    LOGS("");
    if (__target___Py_gitidentifier == NULL) {
        __target___Py_gitidentifier = resolveAPI("_Py_gitidentifier");
    }
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
    if (__target___Py_gitversion == NULL) {
        __target___Py_gitversion = resolveAPI("_Py_gitversion");
    }
    STATS_BEFORE(_Py_gitversion)
    const char* result = (const char*) __target___Py_gitversion();
    STATS_AFTER(_Py_gitversion)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_Py_isabs, _Py_gitversion)
int (*__target___Py_isabs)(const wchar_t*) = NULL;
PyAPI_FUNC(int) _Py_isabs(const wchar_t* a) {
    LOG("0x%lx", (unsigned long) a);
    if (__target___Py_isabs == NULL) {
        __target___Py_isabs = resolveAPI("_Py_isabs");
    }
    STATS_BEFORE(_Py_isabs)
    int result = (int) __target___Py_isabs(a);
    STATS_AFTER(_Py_isabs)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_Py_open, _Py_isabs)
int (*__target___Py_open)(const char*, int) = NULL;
PyAPI_FUNC(int) _Py_open(const char* a, int b) {
    LOG("'%s'(0x%lx) 0x%lx", a?a:"<null>", (unsigned long) a, (unsigned long) b);
    if (__target___Py_open == NULL) {
        __target___Py_open = resolveAPI("_Py_open");
    }
    STATS_BEFORE(_Py_open)
    int result = (int) __target___Py_open(a, b);
    STATS_AFTER(_Py_open)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_Py_open_noraise, _Py_open)
int (*__target___Py_open_noraise)(const char*, int) = NULL;
PyAPI_FUNC(int) _Py_open_noraise(const char* a, int b) {
    LOG("'%s'(0x%lx) 0x%lx", a?a:"<null>", (unsigned long) a, (unsigned long) b);
    if (__target___Py_open_noraise == NULL) {
        __target___Py_open_noraise = resolveAPI("_Py_open_noraise");
    }
    STATS_BEFORE(_Py_open_noraise)
    int result = (int) __target___Py_open_noraise(a, b);
    STATS_AFTER(_Py_open_noraise)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_Py_parse_inf_or_nan, _Py_open_noraise)
double (*__target___Py_parse_inf_or_nan)(const char*, char**) = NULL;
PyAPI_FUNC(double) _Py_parse_inf_or_nan(const char* a, char** b) {
    LOG("'%s'(0x%lx) 0x%lx", a?a:"<null>", (unsigned long) a, (unsigned long) b);
    if (__target___Py_parse_inf_or_nan == NULL) {
        __target___Py_parse_inf_or_nan = resolveAPI("_Py_parse_inf_or_nan");
    }
    STATS_BEFORE(_Py_parse_inf_or_nan)
    double result = (double) __target___Py_parse_inf_or_nan(a, b);
    STATS_AFTER(_Py_parse_inf_or_nan)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_Py_read, _Py_parse_inf_or_nan)
Py_ssize_t (*__target___Py_read)(int, void*, size_t) = NULL;
PyAPI_FUNC(Py_ssize_t) _Py_read(int a, void* b, size_t c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    if (__target___Py_read == NULL) {
        __target___Py_read = resolveAPI("_Py_read");
    }
    STATS_BEFORE(_Py_read)
    Py_ssize_t result = (Py_ssize_t) __target___Py_read(a, b, c);
    STATS_AFTER(_Py_read)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_Py_set_blocking, _Py_read)
int (*__target___Py_set_blocking)(int, int) = NULL;
PyAPI_FUNC(int) _Py_set_blocking(int a, int b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target___Py_set_blocking == NULL) {
        __target___Py_set_blocking = resolveAPI("_Py_set_blocking");
    }
    STATS_BEFORE(_Py_set_blocking)
    int result = (int) __target___Py_set_blocking(a, b);
    STATS_AFTER(_Py_set_blocking)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_Py_set_inheritable, _Py_set_blocking)
int (*__target___Py_set_inheritable)(int, int, int*) = NULL;
PyAPI_FUNC(int) _Py_set_inheritable(int a, int b, int* c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    if (__target___Py_set_inheritable == NULL) {
        __target___Py_set_inheritable = resolveAPI("_Py_set_inheritable");
    }
    STATS_BEFORE(_Py_set_inheritable)
    int result = (int) __target___Py_set_inheritable(a, b, c);
    STATS_AFTER(_Py_set_inheritable)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_Py_set_inheritable_async_safe, _Py_set_inheritable)
int (*__target___Py_set_inheritable_async_safe)(int, int, int*) = NULL;
PyAPI_FUNC(int) _Py_set_inheritable_async_safe(int a, int b, int* c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    if (__target___Py_set_inheritable_async_safe == NULL) {
        __target___Py_set_inheritable_async_safe = resolveAPI("_Py_set_inheritable_async_safe");
    }
    STATS_BEFORE(_Py_set_inheritable_async_safe)
    int result = (int) __target___Py_set_inheritable_async_safe(a, b, c);
    STATS_AFTER(_Py_set_inheritable_async_safe)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_Py_stat, _Py_set_inheritable_async_safe)
int (*__target___Py_stat)(PyObject*, struct stat*) = NULL;
PyAPI_FUNC(int) _Py_stat(PyObject* a, struct stat* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target___Py_stat == NULL) {
        __target___Py_stat = resolveAPI("_Py_stat");
    }
    STATS_BEFORE(_Py_stat)
    int result = (int) __target___Py_stat(a, b);
    STATS_AFTER(_Py_stat)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_Py_strhex, _Py_stat)
PyObject* (*__target___Py_strhex)(const char*, const Py_ssize_t) = NULL;
PyAPI_FUNC(PyObject*) _Py_strhex(const char* a, const Py_ssize_t b) {
    LOG("'%s'(0x%lx) 0x%lx", a?a:"<null>", (unsigned long) a, (unsigned long) b);
    if (__target___Py_strhex == NULL) {
        __target___Py_strhex = resolveAPI("_Py_strhex");
    }
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
    if (__target___Py_strhex_bytes == NULL) {
        __target___Py_strhex_bytes = resolveAPI("_Py_strhex_bytes");
    }
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
    if (__target___Py_strhex_bytes_with_sep == NULL) {
        __target___Py_strhex_bytes_with_sep = resolveAPI("_Py_strhex_bytes_with_sep");
    }
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
    if (__target___Py_strhex_with_sep == NULL) {
        __target___Py_strhex_with_sep = resolveAPI("_Py_strhex_with_sep");
    }
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
    if (__target___Py_string_to_number_with_underscores == NULL) {
        __target___Py_string_to_number_with_underscores = resolveAPI("_Py_string_to_number_with_underscores");
    }
    STATS_BEFORE(_Py_string_to_number_with_underscores)
    PyObject* result = (PyObject*) __target___Py_string_to_number_with_underscores(a, b, c, d, e, f);
    STATS_AFTER(_Py_string_to_number_with_underscores)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_Py_wfopen, _Py_string_to_number_with_underscores)
FILE* (*__target___Py_wfopen)(const wchar_t*, const wchar_t*) = NULL;
PyAPI_FUNC(FILE*) _Py_wfopen(const wchar_t* a, const wchar_t* b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target___Py_wfopen == NULL) {
        __target___Py_wfopen = resolveAPI("_Py_wfopen");
    }
    STATS_BEFORE(_Py_wfopen)
    FILE* result = (FILE*) __target___Py_wfopen(a, b);
    STATS_AFTER(_Py_wfopen)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_Py_wgetcwd, _Py_wfopen)
wchar_t* (*__target___Py_wgetcwd)(wchar_t*, size_t) = NULL;
PyAPI_FUNC(wchar_t*) _Py_wgetcwd(wchar_t* a, size_t b) {
    LOG("0x%lx 0x%lx", (unsigned long) a, (unsigned long) b);
    if (__target___Py_wgetcwd == NULL) {
        __target___Py_wgetcwd = resolveAPI("_Py_wgetcwd");
    }
    STATS_BEFORE(_Py_wgetcwd)
    wchar_t* result = (wchar_t*) __target___Py_wgetcwd(a, b);
    STATS_AFTER(_Py_wgetcwd)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_Py_wreadlink, _Py_wgetcwd)
int (*__target___Py_wreadlink)(const wchar_t*, wchar_t*, size_t) = NULL;
PyAPI_FUNC(int) _Py_wreadlink(const wchar_t* a, wchar_t* b, size_t c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    if (__target___Py_wreadlink == NULL) {
        __target___Py_wreadlink = resolveAPI("_Py_wreadlink");
    }
    STATS_BEFORE(_Py_wreadlink)
    int result = (int) __target___Py_wreadlink(a, b, c);
    STATS_AFTER(_Py_wreadlink)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_Py_wrealpath, _Py_wreadlink)
wchar_t* (*__target___Py_wrealpath)(const wchar_t*, wchar_t*, size_t) = NULL;
PyAPI_FUNC(wchar_t*) _Py_wrealpath(const wchar_t* a, wchar_t* b, size_t c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    if (__target___Py_wrealpath == NULL) {
        __target___Py_wrealpath = resolveAPI("_Py_wrealpath");
    }
    STATS_BEFORE(_Py_wrealpath)
    wchar_t* result = (wchar_t*) __target___Py_wrealpath(a, b, c);
    STATS_AFTER(_Py_wrealpath)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_Py_write, _Py_wrealpath)
Py_ssize_t (*__target___Py_write)(int, const void*, size_t) = NULL;
PyAPI_FUNC(Py_ssize_t) _Py_write(int a, const void* b, size_t c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    if (__target___Py_write == NULL) {
        __target___Py_write = resolveAPI("_Py_write");
    }
    STATS_BEFORE(_Py_write)
    Py_ssize_t result = (Py_ssize_t) __target___Py_write(a, b, c);
    STATS_AFTER(_Py_write)
    LOG_AFTER
    return result;
}
STATS_CONTAINER(_Py_write_noraise, _Py_write)
Py_ssize_t (*__target___Py_write_noraise)(int, const void*, size_t) = NULL;
PyAPI_FUNC(Py_ssize_t) _Py_write_noraise(int a, const void* b, size_t c) {
    LOG("0x%lx 0x%lx 0x%lx", (unsigned long) a, (unsigned long) b, (unsigned long) c);
    if (__target___Py_write_noraise == NULL) {
        __target___Py_write_noraise = resolveAPI("_Py_write_noraise");
    }
    STATS_BEFORE(_Py_write_noraise)
    Py_ssize_t result = (Py_ssize_t) __target___Py_write_noraise(a, b, c);
    STATS_AFTER(_Py_write_noraise)
    LOG_AFTER
    return result;
}
PyAPI_FUNC(int) PyArg_Parse(PyObject* a, const char* b, ...) {
    va_list args;
    va_start(args, b);
    int result = (int) PyArg_VaParse(a, b, args);
    va_end(args);
    return result;
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
PyAPI_FUNC(PyObject*) Py_BuildValue(const char* a, ...) {
    va_list args;
    va_start(args, a);
    PyObject* result = (PyObject*) Py_VaBuildValue(a, args);
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
PyAPI_FUNC(int) _PyArg_Parse_SizeT(PyObject* a, const char* b, ...) {
    va_list args;
    va_start(args, b);
    int result = (int) PyArg_VaParse(a, b, args);
    va_end(args);
    return result;
}
PyAPI_FUNC(PyObject*) _Py_BuildValue_SizeT(const char* a, ...) {
    va_list args;
    va_start(args, a);
    PyObject* result = (PyObject*) _Py_VaBuildValue_SizeT(a, args);
    va_end(args);
    return result;
}
#ifdef STATS
CAPIStats* getStatsList() { return &__stats___Py_write_noraise; }
#endif
