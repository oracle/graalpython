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

#if defined(__GNUC__) && (__GNUC__ > 2) && defined(__OPTIMIZE__)
#  define UNLIKELY(value) __builtin_expect((value), 0)
#  define LIKELY(value) __builtin_expect((value), 1)
#else
#  define UNLIKELY(value) (value)
#  define LIKELY(value) (value)
#endif

#if defined(__GNUC__)
#define THREAD_LOCAL __thread
#elif defined(_MSC_VER)
#define THREAD_LOCAL __declspec(thread)
#else
#error "don't know how to declare thread local variable"
#endif

#ifdef MS_WINDOWS
// define the below, otherwise windows' sdk defines complex to _complex and breaks us
#define _COMPLEX_DEFINED
#endif

#define NEEDS_PY_IDENTIFIER

#include "Python.h"
#include "datetime.h"
#include "structmember.h"
#include "frameobject.h"
#include "pycore_moduleobject.h"
#include "pycore_pymem.h"
#include "pycore_fileutils.h"
#include "bytesobject.h"
#include "pycore_global_objects.h" // _PY_NSMALLPOSINTS

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
typedef struct _gc_runtime_state GCState; // originally in 'gcmodule.c'

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

typedef struct {
    PyObject_HEAD
    int32_t handle_table_index;
} GraalPyObject;

typedef struct {
    GraalPyObject ob_base;
    Py_ssize_t ob_size;
    PyObject **ob_item;
} GraalPyVarObject;

typedef struct {
    GraalPyObject ob_base;
    double ob_fval;
} GraalPyFloatObject;

// {{start CAPI_BUILTINS}}
#include "capi.gen.h"


#define BUILTIN(NAME, RET, ...) extern PyAPI_FUNC(RET) (*Graal##NAME)(__VA_ARGS__);
CAPI_BUILTINS
#undef BUILTIN

#define GET_SLOT_SPECIAL(OBJ, RECEIVER, NAME, SPECIAL) ( points_to_py_handle_space(OBJ) ? GraalPy_get_##RECEIVER##_##NAME((RECEIVER*) (OBJ)) : ((RECEIVER*) (OBJ))->SPECIAL )

PyAPI_DATA(uint32_t) Py_Truffle_Options;

#ifndef GRAALVM_PYTHON_LLVM_MANAGED
extern THREAD_LOCAL Py_LOCAL_SYMBOL PyThreadState *tstate_current;
#endif /* GRAALVM_PYTHON_LLVM_MANAGED */

extern Py_LOCAL_SYMBOL int graalpy_finalizing;

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
#define JWRAPPER_DESCR_DELETE                46
#define JWRAPPER_DELATTRO                    47
#define JWRAPPER_SSIZE_ARG                   48


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

// looked up by NFI, so exported
PyAPI_FUNC(void) register_native_slots(PyTypeObject* managed_class, PyGetSetDef* getsets, PyMemberDef* members);

// export the SizeT arg parse functions, because we use them in contrast to cpython on windows for core modules that we link dynamically
PyAPI_FUNC(int) _PyArg_Parse_SizeT(PyObject *, const char *, ...);
PyAPI_FUNC(int) _PyArg_ParseTuple_SizeT(PyObject *, const char *, ...);
PyAPI_FUNC(int) _PyArg_ParseTupleAndKeywords_SizeT(PyObject *, PyObject *,
                                                  const char *, char **, ...);
PyAPI_FUNC(int) _PyArg_VaParse_SizeT(PyObject *, const char *, va_list);
PyAPI_FUNC(int) _PyArg_VaParseTupleAndKeywords_SizeT(PyObject *, PyObject *,
                                                  const char *, char **, va_list);


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
PY_TRUFFLE_TYPE_WITH_ALLOC(PyCapsule_Type, 		"capsule", 			    	&PyType_Type, sizeof(PyCapsule), PyType_GenericAlloc, capsule_dealloc, PyObject_Del) \
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
PY_TRUFFLE_TYPE_GENERIC(PyUnicode_Type,	"str", 					    &PyType_Type, sizeof(PyUnicodeObject), 0, NULL, unicode_dealloc, PyObject_Del, 0) \
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
PY_TRUFFLE_TYPE(Py_GenericAliasType,    "types.GenericAlias", 		&PyType_Type, sizeof(PyObject)) \
/* PyPickleBufferObject (PyObject_HEAD + Py_buffer + PyObject*) is defined within Objects/picklebufobject.c, so its not exposed. */ \
PY_TRUFFLE_TYPE(PyPickleBuffer_Type, 	"_pickle.PickleBuffer",     &PyType_Type, sizeof(PyPickleBufferObject)) \
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
EXCEPTION(BaseExceptionGroup) \
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
