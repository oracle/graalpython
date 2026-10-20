/*
 * Copyright (c) 2022, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.cext.capi.transitions;

import com.oracle.graal.python.annotations.NativeSimpleType;

/** C signature and native type metadata, independent of Java/native conversion nodes. */
public enum ArgDescriptor {
    Void(NativeSimpleType.VOID, "void"),
    VoidNoReturn(NativeSimpleType.VOID, "void"),
    PyObject(NativeSimpleType.POINTER, "PyObject*", true),
    PyObjectBorrowed(NativeSimpleType.POINTER, "PyObject*", true),
    PyObjectAsTruffleString(NativeSimpleType.POINTER, "PyObject*"),
    PyTypeObject(NativeSimpleType.POINTER, "PyTypeObject*", true),
    PyTypeObjectBorrowed(NativeSimpleType.POINTER, "PyTypeObject*", true),
    PyTypeObjectTransfer(NativeSimpleType.POINTER, "PyTypeObject*", true),
    PyTypeObjectRawPointer(NativeSimpleType.POINTER, "PyTypeObject*"),
    PyListObject(NativeSimpleType.POINTER, "PyListObject*", true),
    PyTupleObject(NativeSimpleType.POINTER, "PyTupleObject*", true),
    PyMethodObject(NativeSimpleType.POINTER, "PyMethodObject*", true),
    PyInstanceMethodObject(NativeSimpleType.POINTER, "PyInstanceMethodObject*", true),
    PyObjectTransfer(NativeSimpleType.POINTER, "PyObject*", true),
    PyObjectReturn(NativeSimpleType.POINTER, "PyObject*", true),
    PyObjectRawPointer(NativeSimpleType.POINTER, "PyObject*"),
    Pointer(NativeSimpleType.POINTER, "void*"),
    Py_ssize_t(NativeSimpleType.SINT64, "Py_ssize_t"),
    Py_hash_t(NativeSimpleType.SINT64, "Py_hash_t"),
    Int(NativeSimpleType.SINT32, "int"),
    ConstInt(NativeSimpleType.SINT32, "const int"),
    Double(NativeSimpleType.DOUBLE, "double"),
    Float(NativeSimpleType.FLOAT, "float"),
    Long(NativeSimpleType.SINT64, "long"),
    PyObjectConstArray(NativeSimpleType.POINTER, "PyObject *const *"),

    _FRAME(NativeSimpleType.POINTER, "struct _frame*", true),
    _MOD_PTR("struct _mod*"),
    _NODE_PTR("struct _node*"),
    _PY_CLOCK_INFO_T_PTR("_Py_clock_info_t*"),
    _PY_ERROR_HANDLER(NativeSimpleType.SINT32, "_Py_error_handler"),
    _PY_IDENTIFIER_PTR("struct _Py_Identifier*"),
    _PYARG_PARSER_PTR("struct _PyArg_Parser*"),
    _PYBYTESWRITER_PTR("_PyBytesWriter*"),
    _PYCROSSINTERPRETERDATA_PTR("_PyCrossInterpreterData*"),
    _PYERR_STACKITEM_PTR("_PyErr_StackItem*"),
    _PYTIME_ROUND_T("_PyTime_round_t"),
    _PYTIME_T("PyTime_t"),
    _PYTIME_T_PTR("PyTime_t*"),
    _PYUNICODEWRITER_PTR("_PyUnicodeWriter*"),
    CHAR(NativeSimpleType.SINT8, "char"),
    UCHAR(NativeSimpleType.SINT8, "unsigned char"),
    CHAR_CONST("char*const"),
    CHAR_CONST_PTR("char*const*"),
    CHAR_CONST_ARRAY("char*const []"),
    CHAR_PTR(NativeSimpleType.POINTER, "char*"),
    CHAR_PTR_LIST(NativeSimpleType.POINTER, "char**"),
    ConstCharPtrAsTruffleString(NativeSimpleType.POINTER, "const char*"),
    ConstCharPtrAsTruffleStringStrict(NativeSimpleType.POINTER, "const char*"),
    ConstCharPtr(NativeSimpleType.POINTER, "const char*"),
    CharPtrAsTruffleString(NativeSimpleType.POINTER, "char*"),
    CONST_CHAR_PTR_LIST("const char**"),
    CONST_PY_BUFFER("const Py_buffer*"),
    CONST_PY_SSIZE_T("const Py_ssize_t"),
    CONST_SIZE_T("const size_t"),
    CONST_PY_SSIZE_T_PTR("const Py_ssize_t*"),
    CONST_PY_UCS4("const Py_UCS4"),
    CONST_PY_UNICODE("const Py_UNICODE*"),
    CONST_PYCONFIG_PTR("const PyConfig*"),
    CONST_PYPRECONFIG_PTR("const PyPreConfig*"),
    CONST_UINT8_T_PTR(NativeSimpleType.POINTER, "const uint8_t*"),
    CONST_UNSIGNED_CHAR_PTR(NativeSimpleType.POINTER, "const unsigned char*"),
    CONST_VOID_PTR(NativeSimpleType.POINTER, "const void*"),
    CONST_VOID_PTR_LIST("const void**"),
    CONST_WCHAR_PTR(NativeSimpleType.POINTER, "const wchar_t*"),
    CROSSINTERPDATAFUNC("crossinterpdatafunc"),
    FILE_PTR("FILE*"),
    FREEFUNC("freefunc"),
    INITTAB("struct _inittab*"),
    UINT16_T(NativeSimpleType.SINT16, "uint16_t"),
    INT32_T(NativeSimpleType.SINT32, "int32_t"),
    INT32_T_PTR(NativeSimpleType.POINTER, "int32_t*"),
    INT_LIST("int*"),
    INT8_T_PTR(NativeSimpleType.POINTER, "int8_t*"),
    INT64_T(NativeSimpleType.SINT64, "int64_t"),
    INTPTR_T_PTR(NativeSimpleType.POINTER, "intptr_t*"),
    LONG_LONG(NativeSimpleType.SINT64, "long long"),
    LONG_PTR("long*"),
    DIGIT_PTR(NativeSimpleType.POINTER, "digit*"),
    INT64_T_PTR(NativeSimpleType.POINTER, "int64_t*"),
    PyASCIIObject(NativeSimpleType.POINTER, "PyASCIIObject*", true),
    PY_AUDITHOOKFUNCTION("Py_AuditHookFunction"),
    Py_buffer("Py_buffer"),
    PY_BUFFER_PTR(NativeSimpleType.POINTER, "Py_buffer*"),
    CONST_PY_BUFFER_PTR(NativeSimpleType.POINTER, "const Py_buffer*"),
    CONST_PY_SLOT_PTR(NativeSimpleType.POINTER, "const PySlot*"),
    PY_C_FUNCTION(NativeSimpleType.POINTER, "PyCFunction"),
    PyByteArrayObject(NativeSimpleType.POINTER, "PyByteArrayObject*", true),
    PyCFunctionObject(NativeSimpleType.POINTER, "PyCFunctionObject*", true),
    PyCMethodObject(NativeSimpleType.POINTER, "PyCMethodObject*", true),
    PY_CAPSULE_DESTRUCTOR(NativeSimpleType.POINTER, "PyCapsule_Destructor"),
    PyCodeObject(NativeSimpleType.POINTER, "PyCodeObject*", true),
    PyCodeObjectTransfer(NativeSimpleType.POINTER, "PyCodeObject*", true),
    PyCodeObjectRawPointer(NativeSimpleType.POINTER, "PyCodeObject*"),
    PyCode_WatchCallback(NativeSimpleType.POINTER, "PyCode_WatchCallback"),
    PY_COMPILER_FLAGS(NativeSimpleType.POINTER, "PyCompilerFlags*"),
    PY_COMPLEX("Py_complex"),
    PY_CRITICAL_SECTION_PTR(NativeSimpleType.POINTER, "PyCriticalSection*"),
    PY_CRITICAL_SECTION2_PTR(NativeSimpleType.POINTER, "PyCriticalSection2*"),
    PyCodeAddressRange("PyCodeAddressRange*"),
    PyCompactUnicodeObject(NativeSimpleType.POINTER, "PyCompactUnicodeObject*", true),
    PyFunctionObject(NativeSimpleType.POINTER, "PyFunctionObject*", true),
    PyFrameConstructor("PyFrameConstructor*"),
    PyFrameObject(NativeSimpleType.POINTER, "PyFrameObject*", true),
    PyFrameObjectBorrowed(NativeSimpleType.POINTER, "PyFrameObject*", true),
    PyFrameObjectTransfer(NativeSimpleType.POINTER, "PyFrameObject*", true),
    PyFrameObjectRawPointer(NativeSimpleType.POINTER, "PyFrameObject*"),
    _PyFrameEvalFunction("_PyFrameEvalFunction"),
    _PyInterpreterFrame("struct _PyInterpreterFrame*"),
    PY_GEN_OBJECT(NativeSimpleType.POINTER, "PyGenObject*", true),
    PyGetSetDef(NativeSimpleType.POINTER, "PyGetSetDef*"),
    PY_GIL_STATE_STATE(NativeSimpleType.SINT32, "PyGILState_STATE"),
    PY_ABI_INFO_PTR(NativeSimpleType.POINTER, "PyABIInfo*"),
    PY_INTERPRETER_GUARD_PTR(NativeSimpleType.POINTER, "PyInterpreterGuard*"),
    PY_INTERPRETER_VIEW_PTR(NativeSimpleType.POINTER, "PyInterpreterView*"),
    PY_HASH_T_PTR(NativeSimpleType.POINTER, "Py_hash_t*"),
    PY_IDENTIFIER("_Py_Identifier*"),
    PyInterpreterState(NativeSimpleType.POINTER, "PyInterpreterState*"),
    ConstPyInterpreterConfig(NativeSimpleType.POINTER, "const PyInterpreterConfig*"),
    PY_LOCK_STATUS("PyLockStatus"),
    PyLongObject(NativeSimpleType.POINTER, "PyLongObject*", true),
    ConstPyLongObject(NativeSimpleType.POINTER, "const PyLongObject*", true),
    PyLongObjectTransfer(NativeSimpleType.POINTER, "PyLongObject*", true),
    PY_LONG_EXPORT_PTR(NativeSimpleType.POINTER, "PyLongExport*"),
    PY_LONG_WRITER_PTR(NativeSimpleType.POINTER, "PyLongWriter*"),
    CONST_PY_LONG_LAYOUT_PTR(NativeSimpleType.POINTER, "const PyLongLayout*"),
    PyMemberDef(NativeSimpleType.POINTER, "PyMemberDef*"),
    PyModuleObject(NativeSimpleType.POINTER, "PyModuleObject*", true),
    PyModuleObjectTransfer(NativeSimpleType.POINTER, "PyModuleObject*", true),
    PyMethodDef(NativeSimpleType.POINTER, "PyMethodDef*"),
    PY_MONITORING_STATE_PTR(NativeSimpleType.POINTER, "PyMonitoringState*"),
    PyModuleDef(NativeSimpleType.POINTER, "PyModuleDef*"), // it's unclear if this should be
    // PyObject
    PyModuleDefSlot(NativeSimpleType.POINTER, "PyModuleDef_Slot*"),
    PyNumberMethods(NativeSimpleType.POINTER, "PyNumberMethods*"),
    PySequenceMethods(NativeSimpleType.POINTER, "PySequenceMethods*"),
    PyMappingMethods(NativeSimpleType.POINTER, "PyMappingMethods*"),
    PyAsyncMethods(NativeSimpleType.POINTER, "PyAsyncMethods*"),
    PyBufferProcs(NativeSimpleType.POINTER, "PyBufferProcs*"),
    PyMethodDescrObject(NativeSimpleType.POINTER, "PyMethodDescrObject*", true),
    PySendResult(NativeSimpleType.SINT32, "PySendResult"),
    PySetObject(NativeSimpleType.POINTER, "PySetObject*", true),
    PyDescrObject(NativeSimpleType.POINTER, "PyDescrObject*", true),
    PY_MUTEX_PTR(NativeSimpleType.POINTER, "PyMutex*"),
    PY_OPENCODEHOOKFUNCTION("Py_OpenCodeHookFunction"),
    PY_OS_SIGHANDLER("PyOS_sighandler_t"),
    PySliceObject(NativeSimpleType.POINTER, "PySliceObject*", true),
    PY_SSIZE_T_PTR(NativeSimpleType.POINTER, "Py_ssize_t*"),
    PY_STRUCT_SEQUENCE_DESC("PyStructSequence_Desc*"),
    PyThreadState(NativeSimpleType.POINTER, "PyThreadState*"),
    PyThreadStatePtr(NativeSimpleType.POINTER, "PyThreadState**"),
    PY_THREAD_STATE_TOKEN_PTR(NativeSimpleType.POINTER, "PyThreadStateToken*"),
    PY_REF_TRACER(NativeSimpleType.POINTER, "PyRefTracer"),
    PY_THREAD_TYPE_LOCK(NativeSimpleType.SINT64, "PyThread_type_lock"),
    PY_THREAD_TYPE_LOCK_PTR(NativeSimpleType.POINTER, "PyThread_type_lock*"),
    PyTryBlock("PyTryBlock*"),
    PY_TRACEFUNC("Py_tracefunc"),
    PY_TSS_T_PTR("Py_tss_t*"),
    PY_TYPE_SPEC("PyType_Spec*"),
    PY_SLOT_PTR(NativeSimpleType.POINTER, "struct PySlot*"),
    PY_TYPE_OBJECT_PTR_LIST(NativeSimpleType.POINTER, "PyTypeObject**"),
    PY_UCS4(NativeSimpleType.SINT32, "Py_UCS4"),
    PY_UCS4_PTR("Py_UCS4*"),
    PY_UNICODE("Py_UNICODE"),
    PyUnicodeObject(NativeSimpleType.POINTER, "PyUnicodeObject*", true),
    PY_UNICODE_PTR(NativeSimpleType.POINTER, "Py_UNICODE*"),
    PyVarObject(NativeSimpleType.POINTER, "PyVarObject*", true),
    ConstPyVarObject(NativeSimpleType.POINTER, "const PyVarObject*", true),
    PYADDRPAIR_PTR("PyAddrPair*"),
    PYCONFIG_PTR("PyConfig*"),
    PYDICTKEYSOBJECT_PTR("PyDictKeysObject*"),
    PYDICTOBJECT_PTR("PyDictObject*"),
    PYHASH_FUNCDEF_PTR("PyHash_FuncDef*"),
    PYMEMALLOCATORDOMAIN("PyMemAllocatorDomain"),
    PYMEMALLOCATOREX_PTR("PyMemAllocatorEx*"),
    PYMODULEDEF_PTR("struct PyModuleDef*"),
    PyObjectConst("PyObject*const"),
    PyObjectConstPtr(NativeSimpleType.POINTER, "PyObject*const*"),
    PYOBJECT_CONST_PTR_LIST("PyObject*const**"),
    PyObjectPtr(NativeSimpleType.POINTER, "PyObject**"),
    PYOBJECTARENAALLOCATOR_PTR("PyObjectArenaAllocator*"),
    PYPRECONFIG_PTR("PyPreConfig*"),
    PYSTATUS("PyStatus"),
    PYUNICODE_KIND("enum PyUnicode_Kind"),
    PYWEAKREFERENCE_PTR(NativeSimpleType.POINTER, "PyWeakReference*"),
    PYWIDESTRINGLIST_PTR("PyWideStringList*"),
    PyDict_WatchCallback(NativeSimpleType.POINTER, "PyDict_WatchCallback"),
    PyFunction_WatchCallback(NativeSimpleType.POINTER, "PyFunction_WatchCallback"),
    PyType_WatchCallback(NativeSimpleType.POINTER, "PyType_WatchCallback"),
    gcvisitobjects_t(NativeSimpleType.POINTER, "gcvisitobjects_t"),
    SIZE_T(NativeSimpleType.SINT64, "size_t"),
    SIZE_T_PTR("size_t*"),
    STAT_PTR("struct stat*"),
    PY_STAT_PTR("struct _Py_stat_struct*"),
    TIME_T("time_t"),
    TIME_T_PTR("time_t*"),
    TIMESPEC_PTR("struct timespec*"),
    TIMEVAL_PTR("struct timeval*"),
    TM_PTR("struct tm*"),
    UINTPTR_T(NativeSimpleType.SINT64, "uintptr_t"),
    UINT32_T(NativeSimpleType.SINT32, "uint32_t"),
    UINT32_T_PTR(NativeSimpleType.POINTER, "uint32_t*"),
    UINT64_T(NativeSimpleType.SINT64, "uint64_t"),
    UINT64_T_PTR(NativeSimpleType.POINTER, "uint64_t*"),
    UNSIGNED_CHAR_PTR(NativeSimpleType.POINTER, "unsigned char*"),
    UNSIGNED_INT(NativeSimpleType.SINT32, "unsigned int"),
    UNSIGNED_LONG(NativeSimpleType.SINT64, "unsigned long"),
    UNSIGNED_LONG_LONG(NativeSimpleType.SINT64, "unsigned long long"),
    VA_LIST(NativeSimpleType.POINTER, "va_list"),
    VA_LIST_PTR(NativeSimpleType.POINTER, "va_list*"),
    VARARGS("..."),
    VOID_PTR_LIST(NativeSimpleType.POINTER, "void**"),
    WCHAR_T_PTR(NativeSimpleType.POINTER, "wchar_t*"),
    WCHAR_T_CONST_PTR(NativeSimpleType.POINTER, "wchar_t*const*"),
    WCHAR_T_PTR_LIST(NativeSimpleType.POINTER, "wchar_t**"),
    WCHAR_T_PTR_PTR_LIST(NativeSimpleType.POINTER, "wchar_t***"),
    WRAPPERBASE(NativeSimpleType.POINTER, "struct wrapperbase*"),
    destructor(NativeSimpleType.POINTER, "destructor"),
    getattrfunc(NativeSimpleType.POINTER, "getattrfunc"),
    setattrfunc(NativeSimpleType.POINTER, "setattrfunc"),
    reprfunc(NativeSimpleType.POINTER, "reprfunc"),
    hashfunc(NativeSimpleType.POINTER, "hashfunc"),
    ternaryfunc(NativeSimpleType.POINTER, "ternaryfunc"),
    getattrofunc(NativeSimpleType.POINTER, "getattrofunc"),
    setattrofunc(NativeSimpleType.POINTER, "setattrofunc"),
    traverseproc(NativeSimpleType.POINTER, "traverseproc"),
    visitproc(NativeSimpleType.POINTER, "visitproc"),
    inquiry(NativeSimpleType.POINTER, "inquiry"),
    richcmpfunc(NativeSimpleType.POINTER, "richcmpfunc"),
    getiterfunc(NativeSimpleType.POINTER, "getiterfunc"),
    iternextfunc(NativeSimpleType.POINTER, "iternextfunc"),
    descrgetfunc(NativeSimpleType.POINTER, "descrgetfunc"),
    descrsetfunc(NativeSimpleType.POINTER, "descrsetfunc"),
    initproc(NativeSimpleType.POINTER, "initproc"),
    allocfunc(NativeSimpleType.POINTER, "allocfunc"),
    newfunc(NativeSimpleType.POINTER, "newfunc"),
    freefunc(NativeSimpleType.POINTER, "freefunc"),
    vectorcallfunc(NativeSimpleType.POINTER, "vectorcallfunc"),
    binaryfunc(NativeSimpleType.POINTER, "binaryfunc"),
    unaryfunc(NativeSimpleType.POINTER, "unaryfunc"),
    lenfunc(NativeSimpleType.POINTER, "lenfunc"),
    ssizeargfunc(NativeSimpleType.POINTER, "ssizeargfunc"),
    ssizeobjargproc(NativeSimpleType.POINTER, "ssizeobjargproc"),
    objobjproc(NativeSimpleType.POINTER, "objobjproc"),
    objobjargproc(NativeSimpleType.POINTER, "objobjargproc"),
    getbufferproc(NativeSimpleType.POINTER, "getbufferproc"),
    releasebufferproc(NativeSimpleType.POINTER, "releasebufferproc"),
    getter(NativeSimpleType.POINTER, "getter"),
    setter(NativeSimpleType.POINTER, "setter"),
    sendfunc(NativeSimpleType.POINTER, "sendfunc"),

    func_objint("int (*)(PyObject*value)"),
    func_voidvoidptr("void (*)(void*)"),
    func_voidvoid(NativeSimpleType.POINTER, "void (*)(void)"),
    func_intvoidptr("int (*)(void*)"),
    func_objvoid("PyObject*(*)(void)"),
    func_objcharsizevoidptr("PyObject*(*)(const char*, Py_ssize_t, void*)"),
    xid_newobjectfunc(NativeSimpleType.POINTER, "xid_newobjectfunc"),
    atexit_datacallbackfunc(NativeSimpleType.POINTER, "atexit_datacallbackfunc"),

    PrimitiveResult32(NativeSimpleType.SINT32, "int"),
    PrimitiveResult64(NativeSimpleType.SINT64, "long");

    private final String cSignature;
    /** Null for descriptors whose native type has not been specified. */
    private final NativeSimpleType nativeSimpleType;
    /** Whether struct accesses treat this type as a Python object reference rather than raw data. */
    private final boolean pyObject;

    ArgDescriptor(String cSignature) {
        this(null, cSignature);
    }

    ArgDescriptor(NativeSimpleType nativeSimpleType, String cSignature) {
        this(nativeSimpleType, cSignature, false);
    }

    ArgDescriptor(NativeSimpleType nativeSimpleType, String cSignature, boolean pyObject) {
        this.nativeSimpleType = nativeSimpleType;
        this.cSignature = cSignature;
        this.pyObject = pyObject;
    }

    public String getCSignature() {
        return cSignature;
    }

    public NativeSimpleType getNativeSimpleType() {
        // Preserve the legacy carrier type for signature-only descriptors with no native type.
        return nativeSimpleType == null ? NativeSimpleType.SINT64 : nativeSimpleType;
    }

    public boolean isPyObjectOrPointer() {
        return isPyObject() || isPointer();
    }

    public boolean isPointer() {
        return nativeSimpleType == NativeSimpleType.POINTER && !pyObject;
    }

    public boolean isPyObject() {
        return pyObject;
    }

    public boolean isCharPtr() {
        return this == CharPtrAsTruffleString || this == CHAR_PTR || this == ConstCharPtr || this == ConstCharPtrAsTruffleString || this == ConstCharPtrAsTruffleStringStrict;
    }

    public boolean isI64() {
        return nativeSimpleType == NativeSimpleType.SINT64;
    }

    public boolean isI8() {
        return nativeSimpleType == NativeSimpleType.SINT8;
    }

    public boolean isI32() {
        return nativeSimpleType == NativeSimpleType.SINT32;
    }

    public boolean isI16() {
        return nativeSimpleType == NativeSimpleType.SINT16;
    }

    public boolean isFloat() {
        return nativeSimpleType == NativeSimpleType.FLOAT;
    }

    public boolean isDouble() {
        return nativeSimpleType == NativeSimpleType.DOUBLE;
    }
}
