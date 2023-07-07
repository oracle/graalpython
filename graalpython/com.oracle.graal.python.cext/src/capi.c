/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "capi.h"
#include <stdio.h>
#include <time.h>
#ifdef MS_WINDOWS
#include "libloaderapi.h"
#include "pathcch.h"
#pragma comment(lib, "Pathcch.lib")
#endif

#define ASSERTIONS

typedef struct arrayobject {
    PyObject_VAR_HEAD
    char *ob_item;
    Py_ssize_t allocated;
    const struct arraydescr *ob_descr;
    PyObject *weakreflist; /* List of weak references */
    int ob_exports;  /* Number of exported buffers */
} arrayobject;

typedef struct {
    PyObject_HEAD
    Py_ssize_t index;
    arrayobject *ao;
    PyObject* (*getitem)(struct arrayobject *, Py_ssize_t);
} arrayiterobject;

// taken from CPython "Objects/typeobject.c"
typedef struct {
    PyObject_HEAD
    PyTypeObject *type;
    PyObject *obj;
    PyTypeObject *obj_type;
} superobject;

// add structure hint for declaring PyCapsule type
/* Internal structure of PyCapsule */
typedef struct {
    PyObject_HEAD
    void *pointer;
    const char *name;
    void *context;
    PyCapsule_Destructor destructor;
} PyCapsule;

typedef struct {
    PyObject_HEAD
    PyObject *ns_dict;
} _PyNamespaceObject;

// taken from CPython "Objects/descrobject.c"
typedef struct {
    PyObject_HEAD
    PyObject *mapping;
} mappingproxyobject;

static void object_dealloc(PyObject *self) {
    Py_TYPE(self)->tp_free(self);
}

// taken from CPython "Objects/bytesobject.c"
#define PyBytesObject_SIZE (offsetof(PyBytesObject, ob_sval) + 1)

/* prototype */
PyObject* PyTruffle_Tuple_Alloc(PyTypeObject* cls, Py_ssize_t nitems);
void PyTruffle_Tuple_Dealloc(PyTupleObject* tuple);

PyAPI_DATA(PyTypeObject) _PyExc_BaseException;
PyAPI_DATA(PyTypeObject) _PyExc_StopIteration;

PyTypeObject PyType_Type = 					PY_TRUFFLE_TYPE_WITH_ITEMSIZE("type", &PyType_Type, Py_TPFLAGS_DEFAULT | Py_TPFLAGS_HAVE_GC | Py_TPFLAGS_BASETYPE | Py_TPFLAGS_TYPE_SUBCLASS, sizeof(PyHeapTypeObject), sizeof(PyMemberDef));
PyTypeObject _PyExc_BaseException = 		PY_TRUFFLE_TYPE("BaseException", &PyType_Type, Py_TPFLAGS_DEFAULT | Py_TPFLAGS_HAVE_GC | Py_TPFLAGS_BASETYPE | Py_TPFLAGS_BASE_EXC_SUBCLASS, sizeof(PyBaseExceptionObject));
PyTypeObject _PyExc_StopIteration = 		PY_TRUFFLE_TYPE("StopIteration", &PyType_Type, Py_TPFLAGS_DEFAULT | Py_TPFLAGS_HAVE_GC | Py_TPFLAGS_BASETYPE | Py_TPFLAGS_BASE_EXC_SUBCLASS, sizeof(PyStopIterationObject));
PyTypeObject _PyNamespace_Type = 			PY_TRUFFLE_TYPE("SimpleNamespace", &PyType_Type, Py_TPFLAGS_DEFAULT | Py_TPFLAGS_HAVE_GC | Py_TPFLAGS_BASETYPE, sizeof(_PyNamespaceObject));
PyTypeObject _PyNone_Type = 				PY_TRUFFLE_TYPE("NoneType", &PyType_Type, Py_TPFLAGS_DEFAULT, 0);
PyTypeObject _PyNotImplemented_Type = 		PY_TRUFFLE_TYPE("NotImplementedType", &PyType_Type, Py_TPFLAGS_DEFAULT, 0);
PyTypeObject _PyWeakref_CallableProxyType = PY_TRUFFLE_TYPE("weakcallableproxy", &PyType_Type, Py_TPFLAGS_DEFAULT | Py_TPFLAGS_HAVE_GC, sizeof(PyWeakReference));
PyTypeObject _PyWeakref_ProxyType = 		PY_TRUFFLE_TYPE("weakproxy", &PyType_Type, Py_TPFLAGS_DEFAULT | Py_TPFLAGS_HAVE_GC, sizeof(PyWeakReference));
PyTypeObject _PyWeakref_RefType = 			PY_TRUFFLE_TYPE("weakref", &PyType_Type, Py_TPFLAGS_DEFAULT | Py_TPFLAGS_HAVE_GC | Py_TPFLAGS_BASETYPE, sizeof(PyWeakReference));
PyTypeObject Arraytype = 					PY_TRUFFLE_TYPE("array", &PyType_Type, Py_TPFLAGS_DEFAULT | Py_TPFLAGS_BASETYPE | Py_TPFLAGS_SEQUENCE, sizeof(arrayobject));
PyTypeObject PyArrayIter_Type = 			PY_TRUFFLE_TYPE("arrayiterator", &PyType_Type, Py_TPFLAGS_DEFAULT | Py_TPFLAGS_HAVE_GC, sizeof(arrayiterobject));
PyTypeObject PyBaseObject_Type = 			PY_TRUFFLE_TYPE_WITH_ALLOC("object", &PyType_Type, Py_TPFLAGS_DEFAULT | Py_TPFLAGS_BASETYPE, sizeof(PyObject), PyType_GenericAlloc, object_dealloc, PyObject_Del);
PyTypeObject PyBool_Type = 					PY_TRUFFLE_TYPE("bool", &PyType_Type, Py_TPFLAGS_DEFAULT | Py_TPFLAGS_LONG_SUBCLASS | _Py_TPFLAGS_MATCH_SELF, sizeof(struct _longobject));
PyTypeObject PyByteArray_Type = 			PY_TRUFFLE_TYPE("bytearray", &PyType_Type, Py_TPFLAGS_DEFAULT | Py_TPFLAGS_BASETYPE | _Py_TPFLAGS_MATCH_SELF, sizeof(PyByteArrayObject));
PyTypeObject PyBytes_Type = 				PY_TRUFFLE_TYPE_WITH_ITEMSIZE("bytes", &PyType_Type, Py_TPFLAGS_DEFAULT | Py_TPFLAGS_BASETYPE | Py_TPFLAGS_BYTES_SUBCLASS | _Py_TPFLAGS_MATCH_SELF, PyBytesObject_SIZE, sizeof(char));
PyTypeObject PyCapsule_Type = 				PY_TRUFFLE_TYPE("PyCapsule", &PyType_Type, 0, sizeof(PyCapsule));
PyTypeObject PyCFunction_Type = 			PY_TRUFFLE_TYPE("builtin_function_or_method", &PyType_Type, Py_TPFLAGS_DEFAULT | Py_TPFLAGS_HAVE_GC, sizeof(PyCFunctionObject));
PyTypeObject PyCMethod_Type = 				PY_TRUFFLE_TYPE("builtin_method", &PyCFunction_Type, Py_TPFLAGS_DEFAULT | Py_TPFLAGS_HAVE_GC, sizeof(PyCFunctionObject));
PyTypeObject PyCode_Type = 					PY_TRUFFLE_TYPE("code", &PyType_Type, Py_TPFLAGS_DEFAULT, sizeof(PyTypeObject));
PyTypeObject PyComplex_Type = 				PY_TRUFFLE_TYPE("complex", &PyType_Type, Py_TPFLAGS_DEFAULT | Py_TPFLAGS_BASETYPE, sizeof(PyComplexObject));
PyTypeObject PyDict_Type = 					PY_TRUFFLE_TYPE("dict", &PyType_Type, Py_TPFLAGS_DEFAULT | Py_TPFLAGS_HAVE_GC | Py_TPFLAGS_BASETYPE | Py_TPFLAGS_DICT_SUBCLASS | _Py_TPFLAGS_MATCH_SELF | Py_TPFLAGS_MAPPING, sizeof(PyDictObject));
PyTypeObject PyDictProxy_Type = 			PY_TRUFFLE_TYPE("mappingproxy", &PyType_Type, Py_TPFLAGS_DEFAULT | Py_TPFLAGS_HAVE_GC | Py_TPFLAGS_MAPPING, sizeof(mappingproxyobject));
PyTypeObject PyEllipsis_Type = 				PY_TRUFFLE_TYPE("ellipsis", &PyType_Type, Py_TPFLAGS_DEFAULT, 0);
PyTypeObject PyFloat_Type = 				PY_TRUFFLE_TYPE("float", &PyType_Type, Py_TPFLAGS_DEFAULT | Py_TPFLAGS_BASETYPE | _Py_TPFLAGS_MATCH_SELF, sizeof(PyFloatObject));
PyTypeObject PyFrame_Type = 				PY_TRUFFLE_TYPE_WITH_ITEMSIZE("frame", &PyType_Type, Py_TPFLAGS_DEFAULT | Py_TPFLAGS_HAVE_GC, sizeof(PyTypeObject), sizeof(PyObject *));
PyTypeObject PyFrozenSet_Type = 			PY_TRUFFLE_TYPE("frozenset", &PyType_Type, Py_TPFLAGS_DEFAULT | Py_TPFLAGS_BASETYPE | Py_TPFLAGS_HAVE_GC | _Py_TPFLAGS_MATCH_SELF, sizeof(PySetObject));
PyTypeObject PyFunction_Type = 				PY_TRUFFLE_TYPE("function", &PyType_Type, Py_TPFLAGS_DEFAULT | Py_TPFLAGS_HAVE_GC | Py_TPFLAGS_METHOD_DESCRIPTOR, sizeof(PyFunctionObject));
PyTypeObject PyGen_Type = 					PY_TRUFFLE_TYPE("generator", &PyType_Type, Py_TPFLAGS_DEFAULT | Py_TPFLAGS_HAVE_GC, sizeof(PyGenObject));
PyTypeObject PyGetSetDescr_Type = 			PY_TRUFFLE_TYPE("getset_descriptor", &PyType_Type, Py_TPFLAGS_DEFAULT | Py_TPFLAGS_HAVE_GC, sizeof(PyGetSetDescrObject));
PyTypeObject PyInstanceMethod_Type = 		PY_TRUFFLE_TYPE("instancemethod", &PyType_Type, Py_TPFLAGS_DEFAULT | Py_TPFLAGS_HAVE_GC, sizeof(PyInstanceMethodObject));
PyTypeObject PyList_Type = 					PY_TRUFFLE_TYPE("list", &PyType_Type, Py_TPFLAGS_DEFAULT | Py_TPFLAGS_HAVE_GC | Py_TPFLAGS_BASETYPE | Py_TPFLAGS_LIST_SUBCLASS | _Py_TPFLAGS_MATCH_SELF | Py_TPFLAGS_SEQUENCE, sizeof(PyListObject));
PyTypeObject PyLong_Type = 					PY_TRUFFLE_TYPE_WITH_ITEMSIZE("int", &PyType_Type, Py_TPFLAGS_DEFAULT | Py_TPFLAGS_BASETYPE | Py_TPFLAGS_LONG_SUBCLASS | _Py_TPFLAGS_MATCH_SELF, offsetof(PyLongObject, ob_digit), sizeof(PyObject *));
PyTypeObject PyMemberDescr_Type = 			PY_TRUFFLE_TYPE("member_descriptor", &PyType_Type, Py_TPFLAGS_DEFAULT | Py_TPFLAGS_HAVE_GC, sizeof(PyMemberDescrObject));
PyTypeObject PyMemoryView_Type = 			PY_TRUFFLE_TYPE_WITH_ITEMSIZE("memoryview", &PyType_Type, Py_TPFLAGS_DEFAULT | Py_TPFLAGS_HAVE_GC | Py_TPFLAGS_SEQUENCE, offsetof(PyMemoryViewObject, ob_array), sizeof(Py_ssize_t));
PyTypeObject PyMethod_Type = 				PY_TRUFFLE_TYPE("method", &PyType_Type, Py_TPFLAGS_DEFAULT | Py_TPFLAGS_HAVE_GC, sizeof(PyMethodObject));
PyTypeObject PyMethodDescr_Type = 			PY_TRUFFLE_TYPE("method_descriptor", &PyType_Type, Py_TPFLAGS_DEFAULT | Py_TPFLAGS_HAVE_GC | Py_TPFLAGS_METHOD_DESCRIPTOR, sizeof(PyMethodDescrObject));
PyTypeObject PyModule_Type = 				PY_TRUFFLE_TYPE("module", &PyType_Type, Py_TPFLAGS_DEFAULT | Py_TPFLAGS_HAVE_GC | Py_TPFLAGS_BASETYPE, sizeof(PyModuleObject));
PyTypeObject PyModuleDef_Type = 			PY_TRUFFLE_TYPE("moduledef", &PyType_Type, 0, sizeof(struct PyModuleDef));
PyTypeObject PyProperty_Type = 				PY_TRUFFLE_TYPE("property", &PyType_Type, Py_TPFLAGS_DEFAULT | Py_TPFLAGS_HAVE_GC | Py_TPFLAGS_BASETYPE, sizeof(propertyobject));
PyTypeObject PySet_Type = 					PY_TRUFFLE_TYPE("set", &PyType_Type, Py_TPFLAGS_DEFAULT | Py_TPFLAGS_BASETYPE | Py_TPFLAGS_HAVE_GC | _Py_TPFLAGS_MATCH_SELF, sizeof(PySetObject));
PyTypeObject PySlice_Type = 				PY_TRUFFLE_TYPE("slice", &PyType_Type, Py_TPFLAGS_DEFAULT | Py_TPFLAGS_HAVE_GC, sizeof(PySliceObject));
PyTypeObject PyStaticMethod_Type = 			PY_TRUFFLE_TYPE("staticmethod", &PyType_Type, Py_TPFLAGS_DEFAULT | Py_TPFLAGS_BASETYPE | Py_TPFLAGS_HAVE_GC, sizeof(PyType_Type));
PyTypeObject PySuper_Type = 				PY_TRUFFLE_TYPE("super", &PyType_Type, Py_TPFLAGS_DEFAULT | Py_TPFLAGS_HAVE_GC | Py_TPFLAGS_BASETYPE, sizeof(superobject));
PyTypeObject PyTraceBack_Type = 			PY_TRUFFLE_TYPE("traceback", &PyType_Type, Py_TPFLAGS_DEFAULT | Py_TPFLAGS_HAVE_GC, sizeof(PyTypeObject));
PyTypeObject PyTuple_Type = 				PY_TRUFFLE_TYPE_GENERIC("tuple", &PyType_Type, Py_TPFLAGS_DEFAULT | Py_TPFLAGS_HAVE_GC | Py_TPFLAGS_BASETYPE | Py_TPFLAGS_TUPLE_SUBCLASS | _Py_TPFLAGS_MATCH_SELF | Py_TPFLAGS_SEQUENCE, sizeof(PyTupleObject) - sizeof(PyObject *), sizeof(PyObject *), PyTruffle_Tuple_Alloc, (destructor)PyTruffle_Tuple_Dealloc, 0, 0);
PyTypeObject PyUnicode_Type = 				PY_TRUFFLE_TYPE("str", &PyType_Type, Py_TPFLAGS_DEFAULT | Py_TPFLAGS_BASETYPE | Py_TPFLAGS_UNICODE_SUBCLASS | _Py_TPFLAGS_MATCH_SELF, sizeof(PyUnicodeObject));
/* NOTE: we use the same Python type (namely 'PBuiltinFunction') for 'wrapper_descriptor' as for 'method_descriptor'; so the flags must be the same! */
PyTypeObject PyWrapperDescr_Type = 			PY_TRUFFLE_TYPE("wrapper_descriptor", &PyType_Type, Py_TPFLAGS_DEFAULT | Py_TPFLAGS_HAVE_GC | Py_TPFLAGS_METHOD_DESCRIPTOR, sizeof(PyWrapperDescrObject));
// dummy definitions:
PyTypeObject _PyBytesIOBuffer_Type =		PY_TRUFFLE_TYPE("_io._BytesIOBuffer", &PyType_Type, Py_TPFLAGS_DEFAULT | Py_TPFLAGS_HAVE_GC, 0);

/*
 * This header includes definitions for constant arrays like:
 * _Py_ascii_whitespace, _Py_ctype_table, _Py_ctype_tolower, _Py_ctype_toupper.
 */
#include "const_arrays.h"

#define BUILTIN(NAME, RET, ...) RET (*Graal##NAME)(__VA_ARGS__);
CAPI_BUILTINS
#undef BUILTIN

PyAPI_FUNC(void) initialize_builtins(void* (*getBuiltin)(int id)) {
	int id = 0;
//#define BUILTIN(NAME, RET, ...) printf("initializing " #NAME "\n"); Graal##NAME = (RET(*)(__VA_ARGS__)) getBuiltin(id++);
#define BUILTIN(NAME, RET, ...) Graal##NAME = (RET(*)(__VA_ARGS__)) getBuiltin(id++);
CAPI_BUILTINS
#undef BUILTIN
}

uint32_t Py_Truffle_Options;
cache_query_t points_to_py_handle_space;
ptr_cache_t pythonToNative;
void_ptr_cache_t javaStringToTruffleString;


void initialize_type_structure(PyTypeObject* structure, PyTypeObject* ptype, polyglot_typeid tid) {
    // Store the Sulong struct type id to be used for instances of this class
    GraalPyTruffle_Set_SulongType(ptype, tid);

    unsigned long original_flags = structure->tp_flags;
    Py_ssize_t basicsize = structure->tp_basicsize;
    Py_ssize_t itemsize = structure->tp_itemsize;
    allocfunc alloc_fun = structure->tp_alloc;
    destructor dealloc_fun = structure->tp_dealloc;
    freefunc free_fun = structure->tp_free;
    Py_ssize_t vectorcall_offset = structure->tp_vectorcall_offset;
    PyBufferProcs* as_buffer = structure->tp_as_buffer;
    PyTypeObject* type_handle = truffle_assign_managed(structure, ptype);
    // write flags as specified in the dummy to the PythonClass object
    set_PyTypeObject_tp_flags(type_handle, original_flags | Py_TPFLAGS_READY | Py_TPFLAGS_IMMUTABLETYPE);
    set_PyTypeObject_tp_basicsize(type_handle, basicsize);
    set_PyTypeObject_tp_itemsize(type_handle, itemsize);
    if (alloc_fun) {
    	set_PyTypeObject_tp_alloc(type_handle, alloc_fun);
    }
    if (dealloc_fun) {
    	set_PyTypeObject_tp_dealloc(type_handle, dealloc_fun);
    }
    if (free_fun) {
    	set_PyTypeObject_tp_free(type_handle, free_fun);
    }
    if (free_fun) {
    	set_PyTypeObject_tp_free(type_handle, free_fun);
    }
    if (vectorcall_offset) {
    	set_PyTypeObject_tp_vectorcall_offset (type_handle, vectorcall_offset);
    }
    if (as_buffer) {
    	set_PyTypeObject_tp_as_buffer(type_handle, as_buffer);
    }
}

static void initialize_builtin_type(PyTypeObject* structure, const char* typname, polyglot_typeid tid) {
    PyTypeObject* ptype = GraalPyTruffle_Type(truffleString(typname));
    initialize_type_structure(structure, ptype, tid);
}

#define TYPES_AND_STRUCTS \
declare_struct(PyType_Type, type, _typeobject) \
declare_struct(PyBaseObject_Type, object, _object) \
declare_type(PyUnicode_Type, str, PyUnicodeObject) \
declare_struct(PyLong_Type, int, _longobject) \
declare_type(PyBytes_Type, bytes, PyBytesObject) \
declare_type(PyDict_Type, dict, PyDictObject) \
declare_type(PyTuple_Type, tuple, PyTupleObject) \
declare_type(PyList_Type, list, PyListObject) \
declare_type(Arraytype, array, arrayobject) \
declare_type(PyArrayIter_Type, arrayiterator, arrayiterobject) \
declare_type(PyComplex_Type, complex, PyComplexObject) \
declare_type(PyModule_Type, module, PyModuleObject) \
declare_type(PyModuleDef_Type, moduledef, PyModuleDef) \
declare_type(PyMemoryView_Type, memoryview, PyMemoryViewObject) \
declare_type(PySet_Type, set, PySetObject) \
declare_type(PyFloat_Type, float, PyFloatObject) \
declare_type(PySlice_Type, slice, PySliceObject) \
declare_type(PyByteArray_Type, bytearray, PyByteArrayObject) \
declare_type(PyCFunction_Type, builtin_function_or_method, PyCFunctionObject) \
declare_type(PyCMethod_Type, builtin_method, PyCMethodObject) \
declare_type(PyWrapperDescr_Type, wrapper_descriptor, PyWrapperDescrObject) \
declare_type(PyCapsule_Type, capsule, PyCapsule) \
declare_type(PyMethodDescr_Type, method_descriptor, PyMethodDescrObject) \
declare_type(PyGetSetDescr_Type, getset_descriptor, PyGetSetDescrObject) \
declare_type(PyMemberDescr_Type, member_descriptor, PyMemberDescrObject) \
declare_type(_PyExc_BaseException, BaseException, PyBaseExceptionObject) \
declare_type(_PyExc_StopIteration, StopIteration, PyStopIterationObject) \
declare_type(PyFunction_Type, function, PyFunctionObject) \
declare_type(PyMethod_Type, method, PyMethodObject) \
declare_type(PyInstanceMethod_Type, instancemethod, PyInstanceMethodObject) \
declare_type(PyCode_Type, code, PyCodeObject) \
declare_type(PyFrame_Type, frame, PyFrameObject) \
declare_type(PyTraceBack_Type, traceback, PyTracebackObject) \
declare_type(_PyWeakref_RefType, ReferenceType, PyWeakReference) \
declare_type(PyGen_Type, generator, PyGenObject) \
declare_type(PyProperty_Type, property, propertyobject) \
initialize_type(PySuper_Type, super, _object) \
initialize_type(_PyNone_Type, NoneType, _object) \
initialize_type(PyFrozenSet_Type, frozenset, PySetObject) \
initialize_type(PyBool_Type, bool, _longobject) \
initialize_type(_PyNotImplemented_Type, NotImplementedType, _object) \
initialize_type(PyDictProxy_Type, mappingproxy, _object) \
initialize_type(PyEllipsis_Type, ellipsis, _object) \
initialize_type(_PyWeakref_ProxyType, ProxyType, PyWeakReference) \
initialize_type(_PyWeakref_CallableProxyType, CallableProxyType, PyWeakReference)
/* The last few types use the same object structure as others, and thus
 POLYGLOT_DECLARE_TYPE should not be called again */

#define initialize_type(typeobject, typename, structtype) // empty
#define declare_struct(typeobject, typename, structtype) POLYGLOT_DECLARE_STRUCT(structtype);
#define declare_type(typeobject, typename, objecttype) POLYGLOT_DECLARE_TYPE(objecttype);
TYPES_AND_STRUCTS
#undef initialize_type
#undef declare_struct
#undef declare_type

static void initialize_builtin_types_and_structs() {
#define initialize_type(typeobject, typename, structtype)          \
	initialize_builtin_type(&typeobject, #typename, polyglot_ ## structtype ## _typeid());
#define declare_struct(typeobject, typename, structtype) initialize_type(typeobject, typename, structtype)
#define declare_type(typeobject, typename, objecttype) initialize_type(typeobject, typename, objecttype)
	 TYPES_AND_STRUCTS
#undef initialize_type
#undef declare_struct
#undef declare_type
 }


POLYGLOT_DECLARE_TYPE(newfunc);
POLYGLOT_DECLARE_TYPE(Py_buffer);

/* primitive and pointer type declarations */

#define REGISTER_BASIC_TYPE(typename)                                     \
    POLYGLOT_DECLARE_TYPE(typename);                                      \
    NO_INLINE PyAPI_FUNC(polyglot_typeid) get_ ## typename ## _typeid(void)  { \
        return polyglot_ ## typename ## _typeid();                        \
    }

/* just a renaming to avoid name clash with Java types */
typedef void*              void_ptr_t;
typedef char               char_t;
typedef float              float_t;
typedef double             double_t;
typedef int                int_t;
typedef unsigned int       uint_t;
typedef long               long_t;
typedef unsigned long      ulong_t;
typedef long long          longlong_t;
typedef unsigned long long ulonglong_t;

REGISTER_BASIC_TYPE(void_ptr_t);
REGISTER_BASIC_TYPE(int_t);
REGISTER_BASIC_TYPE(uint_t);
REGISTER_BASIC_TYPE(long_t);
REGISTER_BASIC_TYPE(ulong_t);
REGISTER_BASIC_TYPE(longlong_t);
REGISTER_BASIC_TYPE(ulonglong_t);
REGISTER_BASIC_TYPE(int64_t);
REGISTER_BASIC_TYPE(int32_t);
REGISTER_BASIC_TYPE(int16_t);
REGISTER_BASIC_TYPE(int8_t);
REGISTER_BASIC_TYPE(uint64_t);
REGISTER_BASIC_TYPE(uint32_t);
REGISTER_BASIC_TYPE(uint16_t);
REGISTER_BASIC_TYPE(uint8_t);
REGISTER_BASIC_TYPE(Py_complex);
REGISTER_BASIC_TYPE(char_t);
REGISTER_BASIC_TYPE(PyObject);
REGISTER_BASIC_TYPE(PyMethodDef);
REGISTER_BASIC_TYPE(PyTypeObject);
REGISTER_BASIC_TYPE(float_t);
REGISTER_BASIC_TYPE(double_t);
REGISTER_BASIC_TYPE(Py_ssize_t);
REGISTER_BASIC_TYPE(size_t);
REGISTER_BASIC_TYPE(PyThreadState);

/* For pointers, make them look like an array of size 1 such that it is
   possible to dereference the pointer by accessing element 0. */
#define REGISTER_POINTER_TYPE(basetype, ptrtype)                                  \
    typedef basetype* ptrtype;                                                    \
    POLYGLOT_DECLARE_TYPE(ptrtype);                                               \
    NO_INLINE PyAPI_FUNC(polyglot_typeid) get_ ## ptrtype ## _typeid(void)  { \
        return polyglot_array_typeid(polyglot_ ## basetype ## _typeid(), 1);      \
    }

REGISTER_POINTER_TYPE(int64_t, int64_ptr_t);
REGISTER_POINTER_TYPE(int32_t, int32_ptr_t);
REGISTER_POINTER_TYPE(int16_t, int16_ptr_t);
REGISTER_POINTER_TYPE(int8_t, int8_ptr_t);
REGISTER_POINTER_TYPE(char_t, char_ptr_t);
REGISTER_POINTER_TYPE(uint64_t, uint64_ptr_t);
REGISTER_POINTER_TYPE(uint32_t, uint32_ptr_t);
REGISTER_POINTER_TYPE(uint16_t, uint16_ptr_t);
REGISTER_POINTER_TYPE(uint8_t, uint8_ptr_t);
REGISTER_POINTER_TYPE(Py_complex, Py_complex_ptr_t);
REGISTER_POINTER_TYPE(PyObject, PyObject_ptr_t);
REGISTER_POINTER_TYPE(PyObject_ptr_t, PyObject_ptr_ptr_t);
REGISTER_POINTER_TYPE(float_t, float_ptr_t);
REGISTER_POINTER_TYPE(double_t, double_ptr_t);
REGISTER_POINTER_TYPE(Py_ssize_t, Py_ssize_ptr_t);

struct _longobject* _Py_FalseStructReference;
struct _longobject* _Py_TrueStructReference;
PyObject* _Py_EllipsisObjectReference;
PyObject* _Py_NoneStructReference;
PyObject* _Py_NotImplementedStructReference;

PyObject* _PyTruffle_Zero;
PyObject* _PyTruffle_One;

static void initialize_globals() {
    GraalPyTruffle_Register_NULL(NULL);

    _Py_NoneStructReference = GraalPyTruffle_None();
    _Py_NotImplementedStructReference = GraalPyTruffle_NotImplemented();
    _Py_EllipsisObjectReference = GraalPyTruffle_Ellipsis();
    _Py_TrueStructReference = (struct _longobject*)GraalPyTruffle_True();
    _Py_FalseStructReference = (struct _longobject*)GraalPyTruffle_False();
    _PyTruffle_Zero = GraalPyTruffleLong_Zero();
    _PyTruffle_One = GraalPyTruffleLong_One();
}

/* internal functions to avoid unnecessary managed <-> native conversions */

/* BYTES, BYTEARRAY */
int bytes_buffer_getbuffer(PyBytesObject *self, Py_buffer *view, int flags);
int bytearray_getbuffer(PyByteArrayObject *obj, Py_buffer *view, int flags);
void bytearray_releasebuffer(PyByteArrayObject *obj, Py_buffer *view);

/* MEMORYVIEW */
int memoryview_getbuffer(PyMemoryViewObject *self, Py_buffer *view, int flags);
void memoryview_releasebuffer(PyMemoryViewObject *self, Py_buffer *view);

static void initialize_bufferprocs() {
    static PyBufferProcs bytes_as_buffer = {
        (getbufferproc)bytes_buffer_getbuffer,       /* bf_getbuffer */
        (releasebufferproc)NULL,                     /* bf_releasebuffer */
    };
    set_PyTypeObject_tp_as_buffer(&PyBytes_Type, &bytes_as_buffer);

    static PyBufferProcs bytearray_as_buffer = {
        (getbufferproc)bytearray_getbuffer,          /* bf_getbuffer */
        (releasebufferproc)bytearray_releasebuffer,  /* bf_releasebuffer */
    };
    set_PyTypeObject_tp_as_buffer(&PyByteArray_Type, &bytearray_as_buffer);

    static PyBufferProcs memory_as_buffer = {
        (getbufferproc)memoryview_getbuffer,         /* bf_getbuffer */
        (releasebufferproc)memoryview_releasebuffer, /* bf_releasebuffer */
    };
    set_PyTypeObject_tp_as_buffer(&PyMemoryView_Type, &memory_as_buffer);
}

/** to be used from Java code only; reads native fields */
#define TYPE_FIELD_GETTER(RECEIVER, NAME) \
PyAPI_FUNC(PyTypeObject*) get_##NAME(RECEIVER obj) {  \
    return obj->NAME;                     \
}
#define OBJECT_FIELD_GETTER(RECEIVER, NAME) \
PyAPI_FUNC(PyObject*) get_##NAME(RECEIVER obj) {        \
    return (PyObject*) obj->NAME;           \
}
#define OBJECT_FIELD_SETTER(RECEIVER, NAME) \
PyAPI_FUNC(void) set_##NAME(RECEIVER obj, PyObject* value) {        \
    Py_XINCREF(value);                      \
    Py_XSETREF(obj->NAME, value);           \
}
#define PRIMITIVE_FIELD_GETTER(RECEIVER, RESULT, NAME) \
PyAPI_FUNC(RESULT) get_##NAME(RECEIVER obj) {                      \
    return obj->NAME;                                  \
}
#define PRIMITIVE_FIELD_SETTER(RECEIVER, TYPE, NAME) \
PyAPI_FUNC(void) set_##NAME(RECEIVER obj, TYPE value) {                      \
    obj->NAME = value;                                  \
}
#define PRIMITIVE_SUBFIELD_GETTER(RECEIVER, FIELD, RESULT, NAME) \
PyAPI_FUNC(RESULT) get_##NAME(RECEIVER obj) {                    \
    return obj->FIELD? obj->FIELD->NAME : NULL;                   \
}

#define PRIMITIVE_EMBEDDED_FIELD_GETTER(RECEIVER, FIELD, RESULT, NAME) \
PyAPI_FUNC(RESULT) get_##FIELD##_##NAME(RECEIVER obj) { \
    return obj->FIELD.NAME; \
}

TYPE_FIELD_GETTER(PyObject*, ob_type)
PRIMITIVE_FIELD_GETTER(PyObject*, Py_ssize_t, ob_refcnt)
PRIMITIVE_FIELD_GETTER(PyVarObject*, Py_ssize_t, ob_size)
OBJECT_FIELD_GETTER(PyTypeObject*, tp_dict)
OBJECT_FIELD_GETTER(PyTypeObject*, tp_base)
OBJECT_FIELD_GETTER(PyTypeObject*, tp_bases)

PRIMITIVE_FIELD_GETTER(PyTypeObject*, reprfunc, tp_repr)
PRIMITIVE_FIELD_GETTER(PyTypeObject*, reprfunc, tp_str)
PRIMITIVE_FIELD_GETTER(PyTypeObject*, getattrofunc, tp_getattro)
PRIMITIVE_FIELD_GETTER(PyTypeObject*, setattrofunc, tp_setattro)
PRIMITIVE_FIELD_GETTER(PyTypeObject*, hashfunc, tp_hash)
PRIMITIVE_FIELD_GETTER(PyTypeObject*, ternaryfunc, tp_call)
PRIMITIVE_FIELD_GETTER(PyTypeObject*, getiterfunc, tp_iter)
PRIMITIVE_FIELD_GETTER(PyTypeObject*, iternextfunc, tp_iternext)
PRIMITIVE_FIELD_GETTER(PyTypeObject*, descrgetfunc, tp_descr_get)
PRIMITIVE_FIELD_GETTER(PyTypeObject*, descrsetfunc, tp_descr_set)
PRIMITIVE_FIELD_GETTER(PyTypeObject*, initproc, tp_init)
PRIMITIVE_FIELD_GETTER(PyTypeObject*, richcmpfunc, tp_richcompare)
PRIMITIVE_SUBFIELD_GETTER(PyTypeObject*, tp_as_number, binaryfunc, nb_add)
PRIMITIVE_SUBFIELD_GETTER(PyTypeObject*, tp_as_number, binaryfunc, nb_subtract)
PRIMITIVE_SUBFIELD_GETTER(PyTypeObject*, tp_as_number, binaryfunc, nb_multiply)
PRIMITIVE_SUBFIELD_GETTER(PyTypeObject*, tp_as_number, binaryfunc, nb_remainder)
PRIMITIVE_SUBFIELD_GETTER(PyTypeObject*, tp_as_number, binaryfunc, nb_divmod)
PRIMITIVE_SUBFIELD_GETTER(PyTypeObject*, tp_as_number, ternaryfunc, nb_power)
PRIMITIVE_SUBFIELD_GETTER(PyTypeObject*, tp_as_number, unaryfunc, nb_negative)
PRIMITIVE_SUBFIELD_GETTER(PyTypeObject*, tp_as_number, unaryfunc, nb_positive)
PRIMITIVE_SUBFIELD_GETTER(PyTypeObject*, tp_as_number, unaryfunc, nb_absolute)
PRIMITIVE_SUBFIELD_GETTER(PyTypeObject*, tp_as_number, inquiry, nb_bool)
PRIMITIVE_SUBFIELD_GETTER(PyTypeObject*, tp_as_number, unaryfunc, nb_invert)
PRIMITIVE_SUBFIELD_GETTER(PyTypeObject*, tp_as_number, binaryfunc, nb_lshift)
PRIMITIVE_SUBFIELD_GETTER(PyTypeObject*, tp_as_number, binaryfunc, nb_rshift)
PRIMITIVE_SUBFIELD_GETTER(PyTypeObject*, tp_as_number, binaryfunc, nb_and)
PRIMITIVE_SUBFIELD_GETTER(PyTypeObject*, tp_as_number, binaryfunc, nb_xor)
PRIMITIVE_SUBFIELD_GETTER(PyTypeObject*, tp_as_number, binaryfunc, nb_or)
PRIMITIVE_SUBFIELD_GETTER(PyTypeObject*, tp_as_number, unaryfunc, nb_int)
PRIMITIVE_SUBFIELD_GETTER(PyTypeObject*, tp_as_number, unaryfunc, nb_float)
PRIMITIVE_SUBFIELD_GETTER(PyTypeObject*, tp_as_number, binaryfunc, nb_inplace_add)
PRIMITIVE_SUBFIELD_GETTER(PyTypeObject*, tp_as_number, binaryfunc, nb_inplace_subtract)
PRIMITIVE_SUBFIELD_GETTER(PyTypeObject*, tp_as_number, binaryfunc, nb_inplace_multiply)
PRIMITIVE_SUBFIELD_GETTER(PyTypeObject*, tp_as_number, binaryfunc, nb_inplace_remainder)
PRIMITIVE_SUBFIELD_GETTER(PyTypeObject*, tp_as_number, ternaryfunc, nb_inplace_power)
PRIMITIVE_SUBFIELD_GETTER(PyTypeObject*, tp_as_number, binaryfunc, nb_inplace_lshift)
PRIMITIVE_SUBFIELD_GETTER(PyTypeObject*, tp_as_number, binaryfunc, nb_inplace_rshift)
PRIMITIVE_SUBFIELD_GETTER(PyTypeObject*, tp_as_number, binaryfunc, nb_inplace_and)
PRIMITIVE_SUBFIELD_GETTER(PyTypeObject*, tp_as_number, binaryfunc, nb_inplace_xor)
PRIMITIVE_SUBFIELD_GETTER(PyTypeObject*, tp_as_number, binaryfunc, nb_inplace_or)
PRIMITIVE_SUBFIELD_GETTER(PyTypeObject*, tp_as_number, binaryfunc, nb_floor_divide)
PRIMITIVE_SUBFIELD_GETTER(PyTypeObject*, tp_as_number, binaryfunc, nb_true_divide)
PRIMITIVE_SUBFIELD_GETTER(PyTypeObject*, tp_as_number, binaryfunc, nb_inplace_floor_divide)
PRIMITIVE_SUBFIELD_GETTER(PyTypeObject*, tp_as_number, binaryfunc, nb_inplace_true_divide)
PRIMITIVE_SUBFIELD_GETTER(PyTypeObject*, tp_as_number, unaryfunc, nb_index)
PRIMITIVE_SUBFIELD_GETTER(PyTypeObject*, tp_as_number, binaryfunc, nb_matrix_multiply)
PRIMITIVE_SUBFIELD_GETTER(PyTypeObject*, tp_as_number, binaryfunc, nb_inplace_matrix_multiply)
PRIMITIVE_SUBFIELD_GETTER(PyTypeObject*, tp_as_sequence, lenfunc, sq_length)
PRIMITIVE_SUBFIELD_GETTER(PyTypeObject*, tp_as_sequence, binaryfunc, sq_concat)
PRIMITIVE_SUBFIELD_GETTER(PyTypeObject*, tp_as_sequence, ssizeargfunc, sq_repeat)
PRIMITIVE_SUBFIELD_GETTER(PyTypeObject*, tp_as_sequence, ssizeargfunc, sq_item)
PRIMITIVE_SUBFIELD_GETTER(PyTypeObject*, tp_as_sequence, ssizeobjargproc, sq_ass_item)
PRIMITIVE_SUBFIELD_GETTER(PyTypeObject*, tp_as_sequence, objobjproc, sq_contains)
PRIMITIVE_SUBFIELD_GETTER(PyTypeObject*, tp_as_sequence, binaryfunc, sq_inplace_concat)
PRIMITIVE_SUBFIELD_GETTER(PyTypeObject*, tp_as_sequence, ssizeargfunc, sq_inplace_repeat)
PRIMITIVE_SUBFIELD_GETTER(PyTypeObject*, tp_as_mapping, lenfunc, mp_length)
PRIMITIVE_SUBFIELD_GETTER(PyTypeObject*, tp_as_mapping, binaryfunc, mp_subscript)
PRIMITIVE_SUBFIELD_GETTER(PyTypeObject*, tp_as_mapping, objobjargproc, mp_ass_subscript)

PRIMITIVE_FIELD_GETTER(PyTypeObject*, const char*, tp_name)
OBJECT_FIELD_GETTER(PyTypeObject*, tp_mro)
OBJECT_FIELD_GETTER(PyTypeObject*, tp_subclasses)
PRIMITIVE_FIELD_GETTER(PyTypeObject*, Py_ssize_t, tp_vectorcall_offset)
PRIMITIVE_FIELD_GETTER(PyTypeObject*, Py_ssize_t, tp_dictoffset)
PRIMITIVE_FIELD_GETTER(PyTypeObject*, Py_ssize_t, tp_weaklistoffset)
PRIMITIVE_FIELD_GETTER(PyTypeObject*, Py_ssize_t, tp_itemsize)
PRIMITIVE_FIELD_GETTER(PyTypeObject*, Py_ssize_t, tp_basicsize)
PRIMITIVE_FIELD_GETTER(PyTypeObject*, allocfunc, tp_alloc)
PRIMITIVE_FIELD_GETTER(PyTypeObject*, destructor, tp_dealloc)
PRIMITIVE_FIELD_GETTER(PyTypeObject*, freefunc, tp_free)
PRIMITIVE_FIELD_GETTER(PyTypeObject*, PyBufferProcs*, tp_as_buffer)
PRIMITIVE_FIELD_GETTER(PyTypeObject*, unsigned long, tp_flags)
PRIMITIVE_FIELD_GETTER(PyModuleDef_Base*, Py_ssize_t, m_index)
PRIMITIVE_FIELD_GETTER(PyModuleDef*, Py_ssize_t, m_size)
PRIMITIVE_FIELD_GETTER(PyModuleDef*, const char*, m_doc)
PRIMITIVE_EMBEDDED_FIELD_GETTER(PyComplexObject*, cval, double, real)
PRIMITIVE_EMBEDDED_FIELD_GETTER(PyComplexObject*, cval, double, imag)
OBJECT_FIELD_GETTER(PyBaseExceptionObject*, args);
OBJECT_FIELD_GETTER(PyBaseExceptionObject*, context);
OBJECT_FIELD_GETTER(PyBaseExceptionObject*, cause);
OBJECT_FIELD_GETTER(PyBaseExceptionObject*, traceback);
PRIMITIVE_FIELD_GETTER(PyBaseExceptionObject*, char, suppress_context);
OBJECT_FIELD_SETTER(PyBaseExceptionObject*, args);
OBJECT_FIELD_SETTER(PyBaseExceptionObject*, context);
PyAPI_FUNC(void) set_cause(PyBaseExceptionObject* obj, PyObject* value) {
    Py_XSETREF(obj->cause, value);
    obj->suppress_context = 1;
}
OBJECT_FIELD_SETTER(PyBaseExceptionObject*, traceback);
PRIMITIVE_FIELD_SETTER(PyBaseExceptionObject*, char, suppress_context);


char* get_ob_sval(PyObject* op) {
	return ((PyBytesObject *)(op))->ob_sval;
}

int64_t get_methods_flags(PyTypeObject *cls) {
    if (cls == NULL) {
        return 0;
    }

    int64_t flags = 0;
	PyNumberMethods* number = cls->tp_as_number;
    if (number != NULL) {
#define COMPUTE_FLAGS(NAME, BIT_IDX) flags |= ((number->NAME != NULL) * BIT_IDX);
		COMPUTE_FLAGS(nb_add, NB_ADD)
		COMPUTE_FLAGS(nb_subtract, NB_SUBTRACT)
		COMPUTE_FLAGS(nb_multiply, NB_MULTIPLY)
		COMPUTE_FLAGS(nb_remainder, NB_REMAINDER)
		COMPUTE_FLAGS(nb_divmod, NB_DIVMOD)
		COMPUTE_FLAGS(nb_power, NB_POWER)
		COMPUTE_FLAGS(nb_negative, NB_NEGATIVE)
		COMPUTE_FLAGS(nb_positive, NB_POSITIVE)
		COMPUTE_FLAGS(nb_absolute, NB_ABSOLUTE)
		COMPUTE_FLAGS(nb_bool, NB_BOOL)
		COMPUTE_FLAGS(nb_invert, NB_INVERT)
		COMPUTE_FLAGS(nb_lshift, NB_LSHIFT)
		COMPUTE_FLAGS(nb_rshift, NB_RSHIFT)
		COMPUTE_FLAGS(nb_and, NB_AND)
		COMPUTE_FLAGS(nb_xor, NB_XOR)
		COMPUTE_FLAGS(nb_or, NB_OR)
		COMPUTE_FLAGS(nb_int, NB_INT)
		COMPUTE_FLAGS(nb_float, NB_FLOAT)
		COMPUTE_FLAGS(nb_inplace_add, NB_INPLACE_ADD)
		COMPUTE_FLAGS(nb_inplace_subtract, NB_INPLACE_SUBTRACT)
		COMPUTE_FLAGS(nb_inplace_multiply, NB_INPLACE_MULTIPLY)
		COMPUTE_FLAGS(nb_inplace_remainder, NB_INPLACE_REMAINDER)
		COMPUTE_FLAGS(nb_inplace_power, NB_INPLACE_POWER)
		COMPUTE_FLAGS(nb_inplace_lshift, NB_INPLACE_LSHIFT)
		COMPUTE_FLAGS(nb_inplace_rshift, NB_INPLACE_RSHIFT)
		COMPUTE_FLAGS(nb_inplace_and, NB_INPLACE_AND)
		COMPUTE_FLAGS(nb_inplace_xor, NB_INPLACE_XOR)
		COMPUTE_FLAGS(nb_inplace_or, NB_INPLACE_OR)
		COMPUTE_FLAGS(nb_floor_divide, NB_FLOOR_DIVIDE)
		COMPUTE_FLAGS(nb_true_divide, NB_TRUE_DIVIDE)
		COMPUTE_FLAGS(nb_inplace_floor_divide, NB_INPLACE_FLOOR_DIVIDE)
		COMPUTE_FLAGS(nb_inplace_true_divide, NB_INPLACE_TRUE_DIVIDE)
		COMPUTE_FLAGS(nb_index, NB_INDEX)
		COMPUTE_FLAGS(nb_matrix_multiply, NB_MATRIX_MULTIPLY)
		COMPUTE_FLAGS(nb_inplace_matrix_multiply, NB_INPLACE_MATRIX_MULTIPLY)
#undef COMPUTE_FLAGS
    }

    PySequenceMethods *sequence = cls->tp_as_sequence;
    if (sequence != NULL) {
#define COMPUTE_FLAGS(NAME, BIT_IDX) flags |= ((sequence->NAME != NULL) * BIT_IDX);
		COMPUTE_FLAGS(sq_length, SQ_LENGTH)
		COMPUTE_FLAGS(sq_concat, SQ_CONCAT)
		COMPUTE_FLAGS(sq_repeat, SQ_REPEAT)
		COMPUTE_FLAGS(sq_item, SQ_ITEM)
		COMPUTE_FLAGS(sq_ass_item, SQ_ASS_ITEM)
		COMPUTE_FLAGS(sq_contains, SQ_CONTAINS)
		COMPUTE_FLAGS(sq_inplace_concat, SQ_INPLACE_CONCAT)
		COMPUTE_FLAGS(sq_inplace_repeat, SQ_INPLACE_REPEAT)
#undef COMPUTE_FLAGS
    }

    PyMappingMethods *mapping = cls->tp_as_mapping;
    if (mapping != NULL) {
#define COMPUTE_FLAGS(NAME, BIT_IDX) flags |= ((mapping->NAME != NULL) * BIT_IDX);
		COMPUTE_FLAGS(mp_length, MP_LENGTH)
		COMPUTE_FLAGS(mp_subscript, MP_SUBSCRIPT)
		COMPUTE_FLAGS(mp_ass_subscript, MP_ASS_SUBSCRIPT)
#undef COMPUTE_FLAGS
    }
    return flags;
}

// not quite as in CPython, this assumes that x is already a double. The rest of
// the implementation is in the Float constructor in Java
PyObject* float_subtype_new(PyTypeObject *type, double x) {
    PyObject* newobj = type->tp_alloc(type, 0);
    if (newobj == NULL) {
        Py_DECREF(newobj);
        return NULL;
    }
    ((PyFloatObject *)newobj)->ob_fval = x;
    return newobj;
}

/**
 * To be used from Java code only. Reads native 'PyModuleDef.m_methods' field and
 * returns a typed pointer that can be used as interop array.
 */
PyAPI_FUNC(PyMethodDef*) get_PyModuleDef_m_methods(PyModuleDef* moduleDef) {
    PyMethodDef* members = moduleDef->m_methods;
    if (members) {
        uint64_t i = 0;
        while (members[i].ml_name != NULL) {
        	i++;
        }
        return polyglot_from_PyMethodDef_array(members, i);
    }
	return NULL;
}

POLYGLOT_DECLARE_TYPE(PyModuleDef_Slot);
/**
 * To be used from Java code only. Reads native 'PyModuleDef.m_slots' field and
 * returns a typed pointer that can be used as interop array.
 */
PyAPI_FUNC(PyModuleDef_Slot*) get_PyModuleDef_m_slots(PyModuleDef* moduleDef) {
    PyModuleDef_Slot* slots = moduleDef->m_slots;
    if (slots) {
        uint64_t i = 0;
        while (slots[i].slot != 0) {
        	i++;
        }
        return polyglot_from_PyModuleDef_Slot_array(slots, i);
    }
	return NULL;
}

/** to be used from Java code only; returns the type ID for a byte array */
PyAPI_FUNC(polyglot_typeid) get_byte_array_typeid(uint64_t len) {
    return polyglot_array_typeid(polyglot_i8_typeid(), len);
}

/** to be used from Java code only; returns the type ID for a uint32_t array */
PyAPI_FUNC(polyglot_typeid) get_uint32_t_array_typeid(uint64_t len) {
    return polyglot_array_typeid(polyglot_uint32_t_typeid(), len);
}

/** to be used from Java code only; returns the type ID for a 'PyObject*' array */
PyAPI_FUNC(polyglot_typeid) get_ptr_array_typeid(uint64_t len) {
    return polyglot_array_typeid(polyglot_PyObjectPtr_typeid(), len);
}

/** to be used from Java code only; returns the type ID PyThreadState */
PyAPI_FUNC(polyglot_typeid) get_thread_state_typeid() {
    return polyglot_PyThreadState_typeid();
}

/** to be used from Java code only; returns the type ID newfunc */
PyAPI_FUNC(polyglot_typeid) get_newfunc_typeid() {
    return polyglot_newfunc_typeid();
}

/** to be used from Java code only; calls INCREF */
PyAPI_FUNC(void) PyTruffle_INCREF(PyObject* obj) {
    Py_INCREF(obj);
}

/** to be used from Java code only; calls DECREF */
PyAPI_FUNC(void) PyTruffle_DECREF(PyObject* obj) {
    Py_DECREF(obj);
}

/** to be used from Java code only; calls ADDREF */
PyAPI_FUNC(Py_ssize_t) PyTruffle_ADDREF(intptr_t ptr, Py_ssize_t value) {
	PyObject* obj = (PyObject*) ptr; // avoid type attachment at the interop boundary
#ifdef ASSERTIONS
	if (obj->ob_refcnt & 0xFFFFFFFF00000000L) {
		char buf[1024];
		sprintf(buf, "suspicious refcnt value during managed adjustment for %p (%lli %p + %lli)\n", obj, obj->ob_refcnt, (void*) obj->ob_refcnt, value);
		Py_FatalError(buf);
	}
	if ((obj->ob_refcnt + value) <= 0) {
		char buf[1024];
		sprintf(buf, "refcnt reached zero during managed adjustment for %p (%lli %p + %lli)\n", obj, obj->ob_refcnt, (void*) obj->ob_refcnt, value);
		Py_FatalError(buf);
	}
//	printf("refcnt value during managed adjustment for %p (%lli %p + %lli)\n", obj, obj->ob_refcnt, (void*) obj->ob_refcnt, value);
#endif // ASSERTIONS

	return (obj->ob_refcnt += value);
}

/** to be used from Java code only; calls DECREF */
PyAPI_FUNC(Py_ssize_t) PyTruffle_SUBREF(intptr_t ptr, Py_ssize_t value) {
	PyObject* obj = (PyObject*) ptr; // avoid type attachment at the interop boundary
#ifdef ASSERTIONS
	if (obj->ob_refcnt & 0xFFFFFFFF00000000L) {
		char buf[1024];
		sprintf(buf, "suspicious refcnt value during managed adjustment for %p (%lli %p - %lli)\n", obj, obj->ob_refcnt, (void*) obj->ob_refcnt, value);
		Py_FatalError(buf);
	}
	if ((obj->ob_refcnt - value) < 0) {
		char buf[1024];
		sprintf(buf, "refcnt below zero during managed adjustment for %p (%lli %p - %lli)\n", obj, obj->ob_refcnt, (void*) obj->ob_refcnt, value);
		Py_FatalError(buf);
	}
//	printf("refcnt value during managed adjustment for %p (%lli %p - %lli)\n", obj, obj->ob_refcnt, (void*) obj->ob_refcnt, value);
#endif // ASSERTIONS

    Py_ssize_t new_value = ((obj->ob_refcnt) -= value);
    if (new_value == 0) {
        _Py_Dealloc(obj);
    }
#ifdef Py_REF_DEBUG
    else if (new_value < 0) {
        _Py_NegativeRefcount(filename, lineno, op);
    }
#endif
    return new_value;
}

/** to be used from Java code only; calls DECREF */
PyAPI_FUNC(Py_ssize_t) PyTruffle_bulk_DEALLOC(intptr_t ptrArray[], int64_t len) {

	for (int i = 0; i < len; i++) {
    	PyObject *obj = (PyObject*) ptrArray[i];
        _Py_Dealloc(obj);
    }
    return 0;
}

PyAPI_FUNC(uint64_t) PyTruffle_Wchar_Size() {
    return SIZEOF_WCHAR_T;
}

/** free's a native pointer or releases a Sulong handle; DO NOT CALL WITH MANAGED POINTERS ! */
PyAPI_FUNC(void) PyTruffle_Free(intptr_t val) {
    PyMem_RawFree((void*) val);
}

PyAPI_FUNC(const char*) PyTruffle_StringToCstr(void* o, int32_t strLen) {
    const char *buffer;
    const char *str;
    uint64_t bufsize = 4 * (strLen + 1) * sizeof(char);
    uint64_t written;

    // we allocate 4 bytes for a char; this will in all cases be enough
    buffer = (const char*) malloc(bufsize);

    written = polyglot_as_string(o, buffer, bufsize, SRC_CS) + 1;

    str = (const char*) PyMem_RawMalloc(written * sizeof(char));
    memcpy(str, buffer, written * sizeof(char));
    free(buffer);

    return str;
}

/* Use this function to decode a C char array to a Java string using the source file encoding. */
PyAPI_FUNC(void*) PyTruffle_CstrToString(void* o) {
    return truffleString(o);
}

/* Use this function to decode a C ASCII string to a Java string. */
PyAPI_FUNC(void*) PyTruffle_AsciiToString(void* ptr) {
    return polyglot_from_string(ptr, "ascii");
}

/* To be used from Java code only.
 * This function is used if a native class inherits from a managed class but uses the 'object.__new__'.
 * This function roughly corresponds to CPython's 'object_new'. */
PyAPI_FUNC(PyObject*) PyTruffle_Object_New(PyTypeObject* cls, PyTypeObject* dominatingNativeClass, PyObject* args, PyObject* kwds) {
    return dominatingNativeClass->tp_alloc(cls, 0);
}

PyAPI_FUNC(void) PyTruffle_PatchType(PyTypeObject* result, PyTypeObject* base) {
	result->tp_base = base;
}

PyAPI_FUNC(void*) PyTruffle_ConvertToPointer(uint64_t value) {
	return (void*) value;
}

PyAPI_FUNC(void) PyTruffle_PopulateType(PyTypeObject* result, PyTypeObject* type, int isType) {
    result->ob_base.ob_base.ob_refcnt = GraalPy_get_PyObject_ob_refcnt(_PyObject_CAST(type));
    if (isType) {
    	// self-reference
    	result->ob_base.ob_base.ob_type = result;
    } else {
    	result->ob_base.ob_base.ob_type = GraalPy_get_PyObject_ob_type(_PyObject_CAST(type));
    }
	result->ob_base.ob_size = GraalPy_get_PyVarObject_ob_size(_PyVarObject_CAST(type));

#define COPY_SLOT(NAME) result->NAME = GraalPy_get_PyTypeObject_##NAME(type);
	COPY_SLOT(tp_name)
	COPY_SLOT(tp_basicsize)
	COPY_SLOT(tp_itemsize)
	COPY_SLOT(tp_dealloc)
	COPY_SLOT(tp_vectorcall_offset)
	COPY_SLOT(tp_getattr)
	COPY_SLOT(tp_setattr)
	COPY_SLOT(tp_repr)
	COPY_SLOT(tp_hash)
	COPY_SLOT(tp_call)
	COPY_SLOT(tp_str)
	COPY_SLOT(tp_getattro)
	COPY_SLOT(tp_setattro)
	COPY_SLOT(tp_flags)
	COPY_SLOT(tp_doc)
	COPY_SLOT(tp_traverse)
	COPY_SLOT(tp_clear)
	COPY_SLOT(tp_richcompare)
	COPY_SLOT(tp_weaklistoffset)
	COPY_SLOT(tp_iter)
	COPY_SLOT(tp_iternext)
	COPY_SLOT(tp_methods)
	COPY_SLOT(tp_members)
	COPY_SLOT(tp_getset)
	if (!isType) {
		COPY_SLOT(tp_base)
	}
	COPY_SLOT(tp_dict)
	COPY_SLOT(tp_descr_get)
	COPY_SLOT(tp_descr_set)
	COPY_SLOT(tp_dictoffset)
	COPY_SLOT(tp_init)
	COPY_SLOT(tp_alloc)
	COPY_SLOT(tp_new)
	COPY_SLOT(tp_free)
	COPY_SLOT(tp_is_gc)
	COPY_SLOT(tp_bases)
	COPY_SLOT(tp_mro)
	COPY_SLOT(tp_cache)
	COPY_SLOT(tp_subclasses)
	COPY_SLOT(tp_weaklist)
	COPY_SLOT(tp_del)
	COPY_SLOT(tp_version_tag)
	COPY_SLOT(tp_finalize)
	COPY_SLOT(tp_vectorcall)
//	COPY_SLOT(tp_print)
#undef COPY_SLOT
	PyAsyncMethods* async = GraalPy_get_PyTypeObject_tp_as_async(type);
	if (async == NULL) {
		result->tp_as_async = NULL;
	} else {
		result->tp_as_async = (PyAsyncMethods*) malloc(sizeof(PyAsyncMethods));
#define COPY_SLOT(NAME) result->tp_as_async->NAME = GraalPy_get_PyAsyncMethods_##NAME(async);
		COPY_SLOT(am_await)
		COPY_SLOT(am_aiter)
		COPY_SLOT(am_anext)
#undef COPY_SLOT
	}
	PyNumberMethods* number = GraalPy_get_PyTypeObject_tp_as_number(type);
	if (number == NULL) {
		result->tp_as_number = NULL;
	} else {
		result->tp_as_number = (PyNumberMethods*) malloc(sizeof(PyNumberMethods));
#define COPY_SLOT(NAME) result->tp_as_number->NAME = GraalPy_get_PyNumberMethods_##NAME(number);
		COPY_SLOT(nb_add)
		COPY_SLOT(nb_subtract)
		COPY_SLOT(nb_multiply)
		COPY_SLOT(nb_remainder)
		COPY_SLOT(nb_divmod)
		COPY_SLOT(nb_power)
		COPY_SLOT(nb_negative)
		COPY_SLOT(nb_positive)
		COPY_SLOT(nb_absolute)
		COPY_SLOT(nb_bool)
		COPY_SLOT(nb_invert)
		COPY_SLOT(nb_lshift)
		COPY_SLOT(nb_rshift)
		COPY_SLOT(nb_and)
		COPY_SLOT(nb_xor)
		COPY_SLOT(nb_or)
		COPY_SLOT(nb_int)
		COPY_SLOT(nb_float)
		COPY_SLOT(nb_inplace_add)
		COPY_SLOT(nb_inplace_subtract)
		COPY_SLOT(nb_inplace_multiply)
		COPY_SLOT(nb_inplace_remainder)
		COPY_SLOT(nb_inplace_power)
		COPY_SLOT(nb_inplace_lshift)
		COPY_SLOT(nb_inplace_rshift)
		COPY_SLOT(nb_inplace_and)
		COPY_SLOT(nb_inplace_xor)
		COPY_SLOT(nb_inplace_or)
		COPY_SLOT(nb_floor_divide)
		COPY_SLOT(nb_true_divide)
		COPY_SLOT(nb_inplace_floor_divide)
		COPY_SLOT(nb_inplace_true_divide)
		COPY_SLOT(nb_index)
		COPY_SLOT(nb_matrix_multiply)
		COPY_SLOT(nb_inplace_matrix_multiply)
#undef COPY_SLOT
	}
	PySequenceMethods* sequence = GraalPy_get_PyTypeObject_tp_as_sequence(type);
	if (sequence == NULL) {
		result->tp_as_sequence = NULL;
	} else {
		result->tp_as_sequence = (PySequenceMethods*) malloc(sizeof(PySequenceMethods));
#define COPY_SLOT(NAME) result->tp_as_sequence->NAME = GraalPy_get_PySequenceMethods_##NAME(sequence);
		COPY_SLOT(sq_length)
		COPY_SLOT(sq_concat)
		COPY_SLOT(sq_repeat)
		COPY_SLOT(sq_item)
		COPY_SLOT(sq_ass_item)
		COPY_SLOT(sq_contains)
		COPY_SLOT(sq_inplace_concat)
		COPY_SLOT(sq_inplace_repeat)
#undef COPY_SLOT
	}
	PyMappingMethods* mapping = GraalPy_get_PyTypeObject_tp_as_mapping(type);
	if (mapping == NULL) {
		result->tp_as_mapping = NULL;
	} else {
		result->tp_as_mapping = (PyMappingMethods*) malloc(sizeof(PyMappingMethods));
#define COPY_SLOT(NAME) result->tp_as_mapping->NAME = GraalPy_get_PyMappingMethods_##NAME(mapping);
		COPY_SLOT(mp_length)
		COPY_SLOT(mp_subscript)
		COPY_SLOT(mp_ass_subscript)
#undef COPY_SLOT
	}
	PyBufferProcs* buffer = GraalPy_get_PyTypeObject_tp_as_buffer(type);
	if (buffer == NULL) {
		result->tp_as_buffer = NULL;
	} else {
		result->tp_as_buffer = buffer; // is always a native pointer

//		result->tp_as_buffer = (PyBufferProcs*) malloc(sizeof(PyBufferProcs));
//#define COPY_SLOT(NAME) result->tp_as_buffer->NAME = GraalPy_get_PyBufferProcs_##NAME(buffer);
//		COPY_SLOT(bf_getbuffer)
//		COPY_SLOT(bf_releasebuffer)
//#undef COPY_SLOT
	}
}

PyAPI_FUNC(PyTypeObject*) PyTruffle_AllocateType(PyTypeObject* type) {
	return (PyTypeObject*) PyMem_RawMalloc(sizeof(PyTypeObject));
}

PyAPI_FUNC(PyMethodDef*) PyTruffle_AllocateMethodDef(PyMethodDef* type) {
	PyMethodDef* result = (PyMethodDef*) PyMem_RawMalloc(sizeof(PyMethodDef));

#define COPY_SLOT(NAME) result->NAME = type->NAME;
	COPY_SLOT(ml_name)
	COPY_SLOT(ml_meth)
	COPY_SLOT(ml_flags)
	COPY_SLOT(ml_doc)
#undef COPY_SLOT
	return result;
}

PyAPI_FUNC(PyDateTime_CAPI*) PyTruffle_AllocateDateTimeAPI(PyDateTime_CAPI* api) {
	PyDateTime_CAPI* result = (PyDateTime_CAPI*) PyMem_RawMalloc(sizeof(PyDateTime_CAPI));

#define COPY_SLOT(NAME) result->NAME = api->NAME;
	COPY_SLOT(DateType)
	COPY_SLOT(DateTimeType)
	COPY_SLOT(TimeType)
	COPY_SLOT(DeltaType)
	COPY_SLOT(TZInfoType)
	COPY_SLOT(TimeZone_UTC)
	COPY_SLOT(Date_FromDate)
	COPY_SLOT(DateTime_FromDateAndTime)
	COPY_SLOT(Time_FromTime)
	COPY_SLOT(Delta_FromDelta)
	COPY_SLOT(TimeZone_FromTimeZone)
	COPY_SLOT(DateTime_FromTimestamp)
	COPY_SLOT(Date_FromTimestamp)
	COPY_SLOT(DateTime_FromDateAndTimeAndFold)
	COPY_SLOT(Time_FromTimeAndFold)
#undef COPY_SLOT
	return result;
}

PyAPI_FUNC(PyMemoryViewObject*) PyTruffle_AllocateMemoryView(PyMemoryViewObject* view) {
	PyMemoryViewObject* result = (PyMemoryViewObject*) PyMem_RawMalloc(sizeof(PyMemoryViewObject));

	result->ob_base.ob_base.ob_refcnt = 0x1000; // TODO: immortal for now
	result->ob_base.ob_base.ob_type = Py_TYPE(view);
#define COPY_SLOT(NAME) result->NAME = view->NAME;
//	COPY_SLOT(mbuf)
//	COPY_SLOT(hash)
	COPY_SLOT(flags)
	COPY_SLOT(exports)
//	COPY_SLOT(weakreflist)
	COPY_SLOT(view.buf)
	COPY_SLOT(view.obj)
	COPY_SLOT(view.len)
	COPY_SLOT(view.itemsize)
	COPY_SLOT(view.readonly)
	COPY_SLOT(view.ndim)
	COPY_SLOT(view.format)
	COPY_SLOT(view.shape)
	COPY_SLOT(view.strides)
	COPY_SLOT(view.suboffsets)
	COPY_SLOT(view.internal)
#undef COPY_SLOT
	return result;
}

#define PRIMITIVE_ARRAY_TO_NATIVE(__jtype__, __ctype__, __polyglot_type__, __element_cast__) \
    void* PyTruffle_##__jtype__##ArrayToNative(const void* jarray, int64_t len) { \
        int64_t i; \
        int64_t size = len + 1; \
        __ctype__* carr = (__ctype__*) malloc(size * sizeof(__ctype__)); \
        carr[len] = (__ctype__)0; \
        for (i=0; i < len; i++) { \
            carr[i] = __element_cast__(polyglot_get_array_element(jarray, i)); \
        } \
        return polyglot_from_##__polyglot_type__##_array(carr, len); \
    } \
    void* PyTruffle_##__jtype__##ArrayRealloc(const void* array, int64_t len) { \
        int64_t size = len + 1; \
        __ctype__* carr = (__ctype__*) realloc(array, size * sizeof(__ctype__)); \
        carr[len] = (__ctype__)0; \
        return polyglot_from_##__polyglot_type__##_array(carr, len); \
    }

PRIMITIVE_ARRAY_TO_NATIVE(Byte, int8_t, i8, polyglot_as_i8);
PRIMITIVE_ARRAY_TO_NATIVE(Int, int32_t, i32, polyglot_as_i32);
PRIMITIVE_ARRAY_TO_NATIVE(Long, int64_t, i64, polyglot_as_i64);
PRIMITIVE_ARRAY_TO_NATIVE(Double, double, double, polyglot_as_double);
PRIMITIVE_ARRAY_TO_NATIVE(Object, PyObjectPtr, PyObjectPtr, (PyObjectPtr));

void PyTruffle_PrimitiveArrayFree(void* array) {
    free(array);
}

void PyTruffle_ObjectArrayFree(PyObject** array, int32_t size) {
    for (int i = 0; i < size; i++) {
        Py_DECREF(array[i]);
    }
    free(array);
}

void PyTruffle_SetStorageItem(PyObject** ptr, int32_t index, PyObject* newitem) {
    Py_XSETREF(ptr[index], newitem);
}

void PyTruffle_InitializeStorageItem(PyObject** ptr, int32_t index, PyObject* newitem) {
    ptr[index] = newitem;
}

PyAPI_FUNC(Py_ssize_t) PyTruffle_Object_Size(PyObject *op) {
    return ((PyVarObject*)op)->ob_size;
}

#define ReadMember(object, offset, T) ((T*)(((char*)object) + offset))[0]

PyAPI_FUNC(int) ReadShortMember(PyObject* object, Py_ssize_t offset) {
    return ReadMember(object, offset, short);
}

PyAPI_FUNC(int) ReadIntMember(PyObject* object, Py_ssize_t offset) {
    return ReadMember(object, offset, int);
}

PyAPI_FUNC(long) ReadLongMember(PyObject* object, Py_ssize_t offset) {
    return ReadMember(object, offset, long);
}

PyAPI_FUNC(double) ReadFloatMember(PyObject* object, Py_ssize_t offset) {
    return ReadMember(object, offset, float);
}

PyAPI_FUNC(double) ReadDoubleMember(PyObject* object, Py_ssize_t offset) {
    return ReadMember(object, offset, double);
}

PyAPI_FUNC(void*) ReadStringMember(PyObject* object, Py_ssize_t offset) {
    char *ptr = ReadMember(object, offset, char*);
    if (ptr != NULL) {
    	return polyglot_from_string(ReadMember(object, offset, char*), "utf-8");
    }
    return NULL;
}

PyAPI_FUNC(void*) ReadStringInPlaceMember(PyObject* object, Py_ssize_t offset) {
    char *addr = (char*) (((char*)object) + offset);
    return polyglot_from_string(addr, "utf-8");
}


PyAPI_FUNC(PyObject*) ReadObjectMember(PyObject* object, Py_ssize_t offset) {
    PyObject* member = ReadMember(object, offset, PyObject*);
    if (member == NULL) {
        member = Py_None;
    }
    Py_INCREF(member);
    return member;
}

PyAPI_FUNC(int) ReadCharMember(PyObject* object, Py_ssize_t offset) {
    return ReadMember(object, offset, char);
}

PyAPI_FUNC(int) ReadUByteMember(PyObject* object, Py_ssize_t offset) {
    return ReadMember(object, offset, unsigned char);
}

PyAPI_FUNC(int) ReadUShortMember(PyObject* object, Py_ssize_t offset) {
    return ReadMember(object, offset, unsigned short);
}

PyAPI_FUNC(long) ReadUIntMember(PyObject* object, Py_ssize_t offset) {
    return ReadMember(object, offset, unsigned int);
}

PyAPI_FUNC(unsigned long) ReadULongMember(PyObject* object, Py_ssize_t offset) {
    return ReadMember(object, offset, unsigned long);
}

PyAPI_FUNC(PyObject*) ReadObjectExMember(PyObject* object, Py_ssize_t offset) {
    PyObject* member = ReadMember(object, offset, PyObject*);
    if (member == NULL) {
        return NULL;
    } else {
        Py_INCREF(member);
        return member;
    }
}

PyAPI_FUNC(long long) ReadLongLongMember(PyObject* object, Py_ssize_t offset) {
    return ReadMember(object, offset, long long);
}

PyAPI_FUNC(unsigned long long) ReadULongLongMember(PyObject* object, Py_ssize_t offset) {
    return ReadMember(object, offset, unsigned long long);
}

PyAPI_FUNC(Py_ssize_t) ReadPySSizeT(PyObject* object, Py_ssize_t offset) {
    return ReadMember(object, offset, Py_ssize_t);
}


#define WriteMember(object, offset, value, T) *(T*)(((char*)object) + offset) = (T)(value)

PyAPI_FUNC(int) WriteShortMember(PyObject* object, Py_ssize_t offset, short value) {
    WriteMember(object, offset, value, short);
    return 0;
}

PyAPI_FUNC(int) WriteIntMember(PyObject* object, Py_ssize_t offset, int value) {
    WriteMember(object, offset, value, int);
    return 0;
}

PyAPI_FUNC(int) WriteLongMember(PyObject* object, Py_ssize_t offset, long value) {
    WriteMember(object, offset, value, long);
    return 0;
}

PyAPI_FUNC(int) WriteFloatMember(PyObject* object, Py_ssize_t offset, float value) {
    WriteMember(object, offset, value, float);
    return 0;
}

PyAPI_FUNC(int) WriteDoubleMember(PyObject* object, Py_ssize_t offset, double value) {
    WriteMember(object, offset, value, double);
    return 0;
}

PyAPI_FUNC(int) WriteStringMember(PyObject* object, Py_ssize_t offset, char* value) {
    WriteMember(object, offset, value, char*);
    return 0;
}

PyAPI_FUNC(int) WriteStringInPlaceMember(PyObject* object, Py_ssize_t offset, char* value) {
    char *addr = (char*) (((char*) object) + offset);
    size_t n;
    if (polyglot_has_array_elements(value)) {
        n = polyglot_get_array_size(value);
    } else {
        n = strlen(value);
    }
    memcpy(addr, value, n);
    return 0;
}

PyAPI_FUNC(int) WriteObjectMember(PyObject* object, Py_ssize_t offset, PyObject* value) {
    /* We first need to decref the old value. */
    PyObject *oldv = ReadMember(object, offset, PyObject*);
    Py_XINCREF(value);
    WriteMember(object, offset, value, PyObject*);
    Py_XDECREF(oldv);
    return 0;
}

PyAPI_FUNC(int) WriteCharMember(PyObject* object, Py_ssize_t offset, char value) {
    WriteMember(object, offset, value, char);
    return 0;
}

PyAPI_FUNC(int) WriteByteMember(PyObject* object, Py_ssize_t offset, char value) {
    WriteMember(object, offset, value, char);
    return 0;
}

PyAPI_FUNC(int) WriteUByteMember(PyObject* object, Py_ssize_t offset, unsigned char value) {
    WriteMember(object, offset, value, uint8_t);
    return 0;
}

PyAPI_FUNC(int) WriteUShortMember(PyObject* object, Py_ssize_t offset, unsigned short value) {
    WriteMember(object, offset, value, unsigned short);
    return 0;
}

PyAPI_FUNC(int) WriteUIntMember(PyObject* object, Py_ssize_t offset, unsigned int value) {
    WriteMember(object, offset, value, unsigned int);
    return 0;
}

PyAPI_FUNC(int) WriteULongMember(PyObject* object, Py_ssize_t offset, unsigned long value) {
    WriteMember(object, offset, value, unsigned long);
    return 0;
}

PyAPI_FUNC(int) WriteObjectExMember(PyObject* object, Py_ssize_t offset, PyObject* value) {
    PyObject *oldv = ReadMember(object, offset, PyObject*);
    if (value == NULL && oldv == NULL) {
        return 1;
    }
    Py_XINCREF(value);
    WriteMember(object, offset, value, PyObject*);
    Py_XDECREF(oldv);
    return 0;
}

PyAPI_FUNC(int) WriteLongLongMember(PyObject* object, Py_ssize_t offset, long long value) {
    WriteMember(object, offset, value, long long);
    return 0;
}

PyAPI_FUNC(int) WriteULongLongMember(PyObject* object, Py_ssize_t offset, unsigned long long value) {
    WriteMember(object, offset, value, unsigned long long);
    return 0;
}

PyAPI_FUNC(int) WritePySSizeT(PyObject* object, Py_ssize_t offset, Py_ssize_t value) {
    WriteMember(object, offset, value, Py_ssize_t);
    return 0;
}

PyObject* wrapped_null;

#undef ReadMember
#undef WriteMember

PyAPI_FUNC(int) truffle_ptr_compare(void* x, void* y, int op) {
    switch (op) {
    case Py_LT:
        return x < y;
    case Py_LE:
        return x <= y;
    case Py_EQ:
        return x == y;
    case Py_NE:
        return x != y;
    case Py_GT:
        return x > y;
    case Py_GE:
        return x >= y;
    default:
        return -1;
    }
}

PyAPI_FUNC(void*) truffle_ptr_convert(size_t value) {
	return (void*) value;
}

PyAPI_FUNC(void*) truffle_ptr_add(void* x, Py_ssize_t y) {
    return x + y;
}

PyAPI_FUNC(double) truffle_read_ob_fval(PyFloatObject* fobj) {
    return fobj->ob_fval;
}

PyAPI_FUNC(void) truffle_memcpy_bytes(void *dest, size_t dest_offset, void *src, size_t src_offset, size_t len) {
    memcpy(dest + dest_offset, src + src_offset, len);
}

/* called from Java to get number of bits per long digit */
PyAPI_FUNC(int32_t) get_long_bits_in_digit() {
    return PYLONG_BITS_IN_DIGIT;
}

PyAPI_FUNC(void) register_native_slots(PyTypeObject* managed_class, PyGetSetDef* getsets, PyMemberDef* members) {
    if (getsets || members) {
        GraalPyTruffle_Set_Native_Slots(managed_class, getsets, members);
    }
}

PyAPI_FUNC(PyObject*) truffle_create_datetime_capsule(void *object) {
    return PyCapsule_New(object, "datetime.datetime_CAPI", NULL);
}

PyAPI_FUNC(int) truffle_subclass_check(PyObject* type) {
    return PyType_FastSubclass(Py_TYPE(type), Py_TPFLAGS_TYPE_SUBCLASS);
}

// Implements the basesisze check in typeobject.c:_PyObject_GetState
PyAPI_FUNC(int) tuffle_check_basesize_for_getstate(PyTypeObject* type, int slot_num) {
    Py_ssize_t basicsize = PyBaseObject_Type.tp_basicsize;
    if (type->tp_dictoffset)
        basicsize += sizeof(PyObject *);
    if (type->tp_weaklistoffset)
        basicsize += sizeof(PyObject *);
    if (slot_num)
        basicsize += sizeof(PyObject *) * PyList_GET_SIZE(slot_num);
    return type->tp_basicsize > basicsize;
}

PyAPI_FUNC(void) truffle_set_tp_flags(PyTypeObject* type, unsigned long flags) {
    type->tp_flags = flags;
}

PyAPI_FUNC(int) truffle_BASETYPE_check(PyObject* type) {
    return PyType_HasFeature(Py_TYPE(type), Py_TPFLAGS_BASETYPE);
}

PyAPI_FUNC(void*) truffle_get_constant(int entry) {
	// this needs to correspond to CApiContext.resolveConstant
	switch(entry) {
	case 0:
		return _Py_ascii_whitespace;
	case 1:
		return _Py_ctype_table;
	case 2:
		return _Py_ctype_tolower;
	case 3:
		return _Py_ctype_toupper;
	case 4:
		return &_Py_tracemalloc_config;
	case 5:
		return &_Py_HashSecret;
	case 6:
		return &Py_DebugFlag;
	case 7:
		return &Py_VerboseFlag;
	case 8:
		return &Py_QuietFlag;
	case 9:
		return &Py_InteractiveFlag;
	case 10:
		return &Py_InspectFlag;
	case 11:
		return &Py_OptimizeFlag;
	case 12:
		return &Py_NoSiteFlag;
	case 13:
		return &Py_BytesWarningFlag;
	case 14:
		return &Py_FrozenFlag;
	case 15:
		return &Py_IgnoreEnvironmentFlag;
	case 16:
		return &Py_DontWriteBytecodeFlag;
	case 17:
		return &Py_NoUserSiteDirectory;
	case 18:
		return &Py_UnbufferedStdioFlag;
	case 19:
		return &Py_HashRandomizationFlag;
	case 20:
		return &Py_IsolatedFlag;
	}
	return NULL;
}

/*
 * These locations need to be shared between native and Sulong - if the native parts are initialized,
 * we assign_managed them to share them.
 */
PyAPI_FUNC(void) initialize_native_locations(void* allocateMemory, void* maxNativeMemory, void* nativeMemoryGCBarrier) {
	truffle_assign_managed(&PyTruffle_AllocatedMemory, allocateMemory);
	truffle_assign_managed(&PyTruffle_MaxNativeMemory, maxNativeMemory);
	truffle_assign_managed(&PyTruffle_NativeMemoryGCBarrier, nativeMemoryGCBarrier);
}

// defined in 'exceptions.c'
void initialize_exceptions();
// defined in 'pyhash.c'
void initialize_hashes();

void* _graalvm_llvm_va_arg(void* valist,void* type);

// forcing the _graalvm_llvm_va_arg function to be available
void forceVA(int a, ...) {
	va_list va;
	va_start(va, a);
	_graalvm_llvm_va_arg(&va, polyglot_i32_typeid());
	va_end(va);
}

PyAPI_FUNC(void) initialize_graal_capi(ptr_cache_t _pythonToNative, void_ptr_cache_t _javaStringToTruffleString, cache_query_t _points_to_py_handle_space, void* (*getBuiltin)(int id)) {
	clock_t t;
	t = clock();
    forceVA(1, 2, 3);

	pythonToNative = _pythonToNative;
	javaStringToTruffleString = _javaStringToTruffleString;
	points_to_py_handle_space = _points_to_py_handle_space;

	initialize_builtins(getBuiltin);

    Py_Truffle_Options = GraalPyTruffle_Native_Options();

    // initialize global variables like '_Py_NoneStruct', etc.
	initialize_builtin_types_and_structs();
    initialize_globals();
    initialize_exceptions();
    initialize_hashes();
    initialize_bufferprocs();

#ifdef MS_WINDOWS
    // when initializing the C API, the appropriate libraries (like
    // python-native.dll or graalvm-llvm.dll) are loaded with their full paths.
    // However, they are not automatically on the search path when any
    // extension modules are loaded later, and Windows wants to resolve them
    // again. So we get their runtime paths here and add those to the dll
    // search path.
    LPSTR lpMsgBuf;
    wchar_t path[MAX_PATH];
    char pathA[MAX_PATH];
    HMODULE hm = NULL;
    if (GetModuleHandleExW(GET_MODULE_HANDLE_EX_FLAG_FROM_ADDRESS |
                           GET_MODULE_HANDLE_EX_FLAG_UNCHANGED_REFCOUNT,
                           (LPCWSTR) &initialize_graal_capi, &hm) == 0) {
        int ret = GetLastError();
        FormatMessageA(FORMAT_MESSAGE_ALLOCATE_BUFFER | FORMAT_MESSAGE_FROM_SYSTEM | FORMAT_MESSAGE_IGNORE_INSERTS,
                       NULL, ret, MAKELANGID(LANG_NEUTRAL, SUBLANG_DEFAULT), &lpMsgBuf, 0, NULL);
        PyTruffle_Log(PY_TRUFFLE_LOG_FINE, "finding python-native.dll handle failed, error = %s\n", lpMsgBuf);
        LocalFree(lpMsgBuf);
    } else {
        if (GetModuleFileNameW(hm, (LPWSTR)path, sizeof(path)) == 0) {
            int ret = GetLastError();
            FormatMessageA(FORMAT_MESSAGE_ALLOCATE_BUFFER | FORMAT_MESSAGE_FROM_SYSTEM | FORMAT_MESSAGE_IGNORE_INSERTS,
                           NULL, ret, MAKELANGID(LANG_NEUTRAL, SUBLANG_DEFAULT), &lpMsgBuf, 0, NULL);
            PyTruffle_Log(PY_TRUFFLE_LOG_FINE, "finding python-native.dll path failed, error = %s\n", lpMsgBuf);
            LocalFree(lpMsgBuf);
        } else {
            wcstombs(pathA, path, sizeof(pathA));
            PyTruffle_Log(PY_TRUFFLE_LOG_FINE, "Adding python-native.dll path '%s' to search path.\n", pathA);
            PathCchRemoveFileSpec((PWSTR)path, sizeof(path));
            AddDllDirectory((LPWSTR)path);
        }
    }
    if (GetModuleHandleExW(GET_MODULE_HANDLE_EX_FLAG_FROM_ADDRESS |
                           GET_MODULE_HANDLE_EX_FLAG_UNCHANGED_REFCOUNT,
                           (LPCWSTR) &polyglot_from_string, &hm) == 0) {
        int ret = GetLastError();
        FormatMessageA(FORMAT_MESSAGE_ALLOCATE_BUFFER | FORMAT_MESSAGE_FROM_SYSTEM | FORMAT_MESSAGE_IGNORE_INSERTS,
                       NULL, ret, MAKELANGID(LANG_NEUTRAL, SUBLANG_DEFAULT), &lpMsgBuf, 0, NULL);
        PyTruffle_Log(PY_TRUFFLE_LOG_FINE, "finding graalvm-llvm.dll handle failed, error = %s\n", lpMsgBuf);
        LocalFree(lpMsgBuf);
    } else {
        if (GetModuleFileNameW(hm, (LPWSTR)path, sizeof(path)) == 0) {
            int ret = GetLastError();
            FormatMessageA(FORMAT_MESSAGE_ALLOCATE_BUFFER | FORMAT_MESSAGE_FROM_SYSTEM | FORMAT_MESSAGE_IGNORE_INSERTS,
                           NULL, ret, MAKELANGID(LANG_NEUTRAL, SUBLANG_DEFAULT), &lpMsgBuf, 0, NULL);
            PyTruffle_Log(PY_TRUFFLE_LOG_FINE, "finding graalvm-llvm.dll path failed, error = %s\n", lpMsgBuf);
            LocalFree(lpMsgBuf);
        } else {
            wcstombs(pathA, path, sizeof(pathA));
            PyTruffle_Log(PY_TRUFFLE_LOG_FINE, "Adding graalvm-llvm.dll path '%s' to search path.\n", pathA);
            PathCchRemoveFileSpec((LPWSTR)path, sizeof(path));
            AddDllDirectory((LPWSTR)path);
        }
    }
#endif

    // TODO: initialize during cext initialization doesn't work at the moment
    // This is hardcoded the same way in capi_native.c
    Py_FileSystemDefaultEncoding = "utf-8"; // strdup(PyUnicode_AsUTF8(GraalPyTruffle_FileSystemDefaultEncoding()));

    PyTruffle_Log(PY_TRUFFLE_LOG_FINE, "initialize_graal_capi: %fs", ((double) (clock() - t)) / CLOCKS_PER_SEC);
}

// {{start CAPI_BUILTINS}}
// GENERATED CODE - see CApiCodeGen
// This can be re-generated using the 'mx python-capi-forwards' command or
// by executing the main class CApiCodeGen

#undef PyByteArray_Resize
PyAPI_FUNC(int) PyByteArray_Resize(PyObject* a, Py_ssize_t b) {
    return GraalPyByteArray_Resize(a, b);
}
#undef PyBytes_FromObject
PyAPI_FUNC(PyObject*) PyBytes_FromObject(PyObject* a) {
    return GraalPyBytes_FromObject(a);
}
#undef PyBytes_Size
PyAPI_FUNC(Py_ssize_t) PyBytes_Size(PyObject* a) {
    return GraalPyBytes_Size(a);
}
#undef PyCallIter_New
PyAPI_FUNC(PyObject*) PyCallIter_New(PyObject* a, PyObject* b) {
    return GraalPyCallIter_New(a, b);
}
#undef PyCallable_Check
PyAPI_FUNC(int) PyCallable_Check(PyObject* a) {
    return GraalPyCallable_Check(a);
}
#undef PyCapsule_GetContext
PyAPI_FUNC(void*) PyCapsule_GetContext(PyObject* a) {
    return GraalPyCapsule_GetContext(a);
}
#undef PyCapsule_GetDestructor
PyAPI_FUNC(PyCapsule_Destructor) PyCapsule_GetDestructor(PyObject* a) {
    return GraalPyCapsule_GetDestructor(a);
}
#undef PyCapsule_GetName
PyAPI_FUNC(const char*) PyCapsule_GetName(PyObject* a) {
    return GraalPyCapsule_GetName(a);
}
#undef PyCapsule_GetPointer
PyAPI_FUNC(void*) PyCapsule_GetPointer(PyObject* a, const char* b) {
    return GraalPyCapsule_GetPointer(a, truffleString(b));
}
#undef PyCapsule_Import
PyAPI_FUNC(void*) PyCapsule_Import(const char* a, int b) {
    return GraalPyCapsule_Import(truffleString(a), b);
}
#undef PyCapsule_IsValid
PyAPI_FUNC(int) PyCapsule_IsValid(PyObject* a, const char* b) {
    return GraalPyCapsule_IsValid(a, truffleString(b));
}
#undef PyCapsule_New
PyAPI_FUNC(PyObject*) PyCapsule_New(void* a, const char* b, PyCapsule_Destructor c) {
    return GraalPyCapsule_New(a, truffleString(b), c);
}
#undef PyCapsule_SetContext
PyAPI_FUNC(int) PyCapsule_SetContext(PyObject* a, void* b) {
    return GraalPyCapsule_SetContext(a, b);
}
#undef PyCapsule_SetDestructor
PyAPI_FUNC(int) PyCapsule_SetDestructor(PyObject* a, PyCapsule_Destructor b) {
    return GraalPyCapsule_SetDestructor(a, b);
}
#undef PyCapsule_SetName
PyAPI_FUNC(int) PyCapsule_SetName(PyObject* a, const char* b) {
    return GraalPyCapsule_SetName(a, truffleString(b));
}
#undef PyCapsule_SetPointer
PyAPI_FUNC(int) PyCapsule_SetPointer(PyObject* a, void* b) {
    return GraalPyCapsule_SetPointer(a, b);
}
#undef PyClassMethod_New
PyAPI_FUNC(PyObject*) PyClassMethod_New(PyObject* a) {
    return GraalPyClassMethod_New(a);
}
#undef PyCode_Addr2Line
PyAPI_FUNC(int) PyCode_Addr2Line(PyCodeObject* a, int b) {
    return GraalPyCode_Addr2Line(a, b);
}
#undef PyCode_GetFileName
PyAPI_FUNC(PyObject*) PyCode_GetFileName(PyCodeObject* a) {
    return GraalPyCode_GetFileName(a);
}
#undef PyCode_GetName
PyAPI_FUNC(PyObject*) PyCode_GetName(PyCodeObject* a) {
    return GraalPyCode_GetName(a);
}
#undef PyCode_New
PyAPI_FUNC(PyCodeObject*) PyCode_New(int a, int b, int c, int d, int e, PyObject* f, PyObject* g, PyObject* h, PyObject* i, PyObject* j, PyObject* k, PyObject* l, PyObject* m, int n, PyObject* o) {
    return GraalPyCode_New(a, b, c, d, e, f, g, h, i, j, k, l, m, n, o);
}
#undef PyCode_NewEmpty
PyAPI_FUNC(PyCodeObject*) PyCode_NewEmpty(const char* a, const char* b, int c) {
    return GraalPyCode_NewEmpty(truffleString(a), truffleString(b), c);
}
#undef PyCode_NewWithPosOnlyArgs
PyAPI_FUNC(PyCodeObject*) PyCode_NewWithPosOnlyArgs(int a, int b, int c, int d, int e, int f, PyObject* g, PyObject* h, PyObject* i, PyObject* j, PyObject* k, PyObject* l, PyObject* m, PyObject* n, int o, PyObject* p) {
    return GraalPyCode_NewWithPosOnlyArgs(a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p);
}
#undef PyCodec_Decoder
PyAPI_FUNC(PyObject*) PyCodec_Decoder(const char* a) {
    return GraalPyCodec_Decoder(truffleString(a));
}
#undef PyCodec_Encoder
PyAPI_FUNC(PyObject*) PyCodec_Encoder(const char* a) {
    return GraalPyCodec_Encoder(truffleString(a));
}
#undef PyComplex_FromDoubles
PyAPI_FUNC(PyObject*) PyComplex_FromDoubles(double a, double b) {
    return GraalPyComplex_FromDoubles(a, b);
}
#undef PyComplex_ImagAsDouble
PyAPI_FUNC(double) PyComplex_ImagAsDouble(PyObject* a) {
    return GraalPyComplex_ImagAsDouble(a);
}
#undef PyComplex_RealAsDouble
PyAPI_FUNC(double) PyComplex_RealAsDouble(PyObject* a) {
    return GraalPyComplex_RealAsDouble(a);
}
#undef PyContextVar_New
PyAPI_FUNC(PyObject*) PyContextVar_New(const char* a, PyObject* b) {
    return GraalPyContextVar_New(truffleString(a), b);
}
#undef PyContextVar_Set
PyAPI_FUNC(PyObject*) PyContextVar_Set(PyObject* a, PyObject* b) {
    return GraalPyContextVar_Set(a, b);
}
#undef PyDictProxy_New
PyAPI_FUNC(PyObject*) PyDictProxy_New(PyObject* a) {
    return GraalPyDictProxy_New(a);
}
#undef PyDict_Clear
PyAPI_FUNC(void) PyDict_Clear(PyObject* a) {
    GraalPyDict_Clear(a);
}
#undef PyDict_Contains
PyAPI_FUNC(int) PyDict_Contains(PyObject* a, PyObject* b) {
    return GraalPyDict_Contains(a, b);
}
#undef PyDict_Copy
PyAPI_FUNC(PyObject*) PyDict_Copy(PyObject* a) {
    return GraalPyDict_Copy(a);
}
#undef PyDict_DelItem
PyAPI_FUNC(int) PyDict_DelItem(PyObject* a, PyObject* b) {
    return GraalPyDict_DelItem(a, b);
}
#undef PyDict_GetItem
PyAPI_FUNC(PyObject*) PyDict_GetItem(PyObject* a, PyObject* b) {
    return GraalPyDict_GetItem(a, b);
}
#undef PyDict_GetItemWithError
PyAPI_FUNC(PyObject*) PyDict_GetItemWithError(PyObject* a, PyObject* b) {
    return GraalPyDict_GetItemWithError(a, b);
}
#undef PyDict_Items
PyAPI_FUNC(PyObject*) PyDict_Items(PyObject* a) {
    return GraalPyDict_Items(a);
}
#undef PyDict_Keys
PyAPI_FUNC(PyObject*) PyDict_Keys(PyObject* a) {
    return GraalPyDict_Keys(a);
}
#undef PyDict_Merge
PyAPI_FUNC(int) PyDict_Merge(PyObject* a, PyObject* b, int c) {
    return GraalPyDict_Merge(a, b, c);
}
#undef PyDict_New
PyAPI_FUNC(PyObject*) PyDict_New() {
    return GraalPyDict_New();
}
#undef PyDict_SetDefault
PyAPI_FUNC(PyObject*) PyDict_SetDefault(PyObject* a, PyObject* b, PyObject* c) {
    return GraalPyDict_SetDefault(a, b, c);
}
#undef PyDict_SetItem
PyAPI_FUNC(int) PyDict_SetItem(PyObject* a, PyObject* b, PyObject* c) {
    return GraalPyDict_SetItem(a, b, c);
}
#undef PyDict_Size
PyAPI_FUNC(Py_ssize_t) PyDict_Size(PyObject* a) {
    return GraalPyDict_Size(a);
}
#undef PyDict_Update
PyAPI_FUNC(int) PyDict_Update(PyObject* a, PyObject* b) {
    return GraalPyDict_Update(a, b);
}
#undef PyDict_Values
PyAPI_FUNC(PyObject*) PyDict_Values(PyObject* a) {
    return GraalPyDict_Values(a);
}
#undef PyErr_Display
PyAPI_FUNC(void) PyErr_Display(PyObject* a, PyObject* b, PyObject* c) {
    GraalPyErr_Display(a, b, c);
}
#undef PyErr_GivenExceptionMatches
PyAPI_FUNC(int) PyErr_GivenExceptionMatches(PyObject* a, PyObject* b) {
    return GraalPyErr_GivenExceptionMatches(a, b);
}
#undef PyErr_NewException
PyAPI_FUNC(PyObject*) PyErr_NewException(const char* a, PyObject* b, PyObject* c) {
    return GraalPyErr_NewException(truffleString(a), b, c);
}
#undef PyErr_NewExceptionWithDoc
PyAPI_FUNC(PyObject*) PyErr_NewExceptionWithDoc(const char* a, const char* b, PyObject* c, PyObject* d) {
    return GraalPyErr_NewExceptionWithDoc(truffleString(a), truffleString(b), c, d);
}
#undef PyErr_Occurred
PyAPI_FUNC(PyObject*) PyErr_Occurred() {
    return GraalPyErr_Occurred();
}
#undef PyErr_PrintEx
PyAPI_FUNC(void) PyErr_PrintEx(int a) {
    GraalPyErr_PrintEx(a);
}
#undef PyErr_Restore
PyAPI_FUNC(void) PyErr_Restore(PyObject* a, PyObject* b, PyObject* c) {
    GraalPyErr_Restore(a, b, c);
}
#undef PyErr_SetExcInfo
PyAPI_FUNC(void) PyErr_SetExcInfo(PyObject* a, PyObject* b, PyObject* c) {
    GraalPyErr_SetExcInfo(a, b, c);
}
#undef PyEval_GetBuiltins
PyAPI_FUNC(PyObject*) PyEval_GetBuiltins() {
    return GraalPyEval_GetBuiltins();
}
#undef PyEval_GetFrame
PyAPI_FUNC(PyFrameObject*) PyEval_GetFrame() {
    return GraalPyEval_GetFrame();
}
#undef PyEval_RestoreThread
PyAPI_FUNC(void) PyEval_RestoreThread(PyThreadState* a) {
    GraalPyEval_RestoreThread(a);
}
#undef PyEval_SaveThread
PyAPI_FUNC(PyThreadState*) PyEval_SaveThread() {
    return GraalPyEval_SaveThread();
}
#undef PyException_GetCause
PyAPI_FUNC(PyObject*) PyException_GetCause(PyObject* a) {
    return GraalPyException_GetCause(a);
}
#undef PyException_GetContext
PyAPI_FUNC(PyObject*) PyException_GetContext(PyObject* a) {
    return GraalPyException_GetContext(a);
}
#undef PyException_SetCause
PyAPI_FUNC(void) PyException_SetCause(PyObject* a, PyObject* b) {
    GraalPyException_SetCause(a, b);
}
#undef PyException_SetContext
PyAPI_FUNC(void) PyException_SetContext(PyObject* a, PyObject* b) {
    GraalPyException_SetContext(a, b);
}
#undef PyException_SetTraceback
PyAPI_FUNC(int) PyException_SetTraceback(PyObject* a, PyObject* b) {
    return GraalPyException_SetTraceback(a, b);
}
#undef PyFile_WriteObject
PyAPI_FUNC(int) PyFile_WriteObject(PyObject* a, PyObject* b, int c) {
    return GraalPyFile_WriteObject(a, b, c);
}
#undef PyFloat_FromDouble
PyAPI_FUNC(PyObject*) PyFloat_FromDouble(double a) {
    return GraalPyFloat_FromDouble(a);
}
#undef PyFloat_FromString
PyAPI_FUNC(PyObject*) PyFloat_FromString(PyObject* a) {
    return GraalPyFloat_FromString(a);
}
#undef PyFrame_GetBack
PyAPI_FUNC(PyFrameObject*) PyFrame_GetBack(PyFrameObject* a) {
    return GraalPyFrame_GetBack(a);
}
#undef PyFrame_GetBuiltins
PyAPI_FUNC(PyObject*) PyFrame_GetBuiltins(PyFrameObject* a) {
    return GraalPyFrame_GetBuiltins(a);
}
#undef PyFrame_GetCode
PyAPI_FUNC(PyCodeObject*) PyFrame_GetCode(PyFrameObject* a) {
    return GraalPyFrame_GetCode(a);
}
#undef PyFrame_GetGlobals
PyAPI_FUNC(PyObject*) PyFrame_GetGlobals(PyFrameObject* a) {
    return GraalPyFrame_GetGlobals(a);
}
#undef PyFrame_GetLasti
PyAPI_FUNC(int) PyFrame_GetLasti(PyFrameObject* a) {
    return GraalPyFrame_GetLasti(a);
}
#undef PyFrame_GetLineNumber
PyAPI_FUNC(int) PyFrame_GetLineNumber(PyFrameObject* a) {
    return GraalPyFrame_GetLineNumber(a);
}
#undef PyFrame_GetLocals
PyAPI_FUNC(PyObject*) PyFrame_GetLocals(PyFrameObject* a) {
    return GraalPyFrame_GetLocals(a);
}
#undef PyFrame_New
PyAPI_FUNC(PyFrameObject*) PyFrame_New(PyThreadState* a, PyCodeObject* b, PyObject* c, PyObject* d) {
    return GraalPyFrame_New(a, b, c, d);
}
#undef PyFrozenSet_New
PyAPI_FUNC(PyObject*) PyFrozenSet_New(PyObject* a) {
    return GraalPyFrozenSet_New(a);
}
#undef PyGILState_Check
PyAPI_FUNC(int) PyGILState_Check() {
    return GraalPyGILState_Check();
}
#undef PyImport_GetModuleDict
PyAPI_FUNC(PyObject*) PyImport_GetModuleDict() {
    return GraalPyImport_GetModuleDict();
}
#undef PyImport_Import
PyAPI_FUNC(PyObject*) PyImport_Import(PyObject* a) {
    return GraalPyImport_Import(a);
}
#undef PyImport_ImportModule
PyAPI_FUNC(PyObject*) PyImport_ImportModule(const char* a) {
    return GraalPyImport_ImportModule(truffleString(a));
}
#undef PyImport_ImportModuleLevelObject
PyAPI_FUNC(PyObject*) PyImport_ImportModuleLevelObject(PyObject* a, PyObject* b, PyObject* c, PyObject* d, int e) {
    return GraalPyImport_ImportModuleLevelObject(a, b, c, d, e);
}
#undef PyImport_ImportModuleNoBlock
PyAPI_FUNC(PyObject*) PyImport_ImportModuleNoBlock(const char* a) {
    return GraalPyImport_ImportModuleNoBlock(truffleString(a));
}
#undef PyIndex_Check
PyAPI_FUNC(int) PyIndex_Check(PyObject* a) {
    return GraalPyIndex_Check(a);
}
#undef PyInstanceMethod_New
PyAPI_FUNC(PyObject*) PyInstanceMethod_New(PyObject* a) {
    return GraalPyInstanceMethod_New(a);
}
#undef PyIter_Check
PyAPI_FUNC(int) PyIter_Check(PyObject* a) {
    return GraalPyIter_Check(a);
}
#undef PyIter_Next
PyAPI_FUNC(PyObject*) PyIter_Next(PyObject* a) {
    return GraalPyIter_Next(a);
}
#undef PyList_Append
PyAPI_FUNC(int) PyList_Append(PyObject* a, PyObject* b) {
    return GraalPyList_Append(a, b);
}
#undef PyList_AsTuple
PyAPI_FUNC(PyObject*) PyList_AsTuple(PyObject* a) {
    return GraalPyList_AsTuple(a);
}
#undef PyList_GetItem
PyAPI_FUNC(PyObject*) PyList_GetItem(PyObject* a, Py_ssize_t b) {
    return GraalPyList_GetItem(a, b);
}
#undef PyList_GetSlice
PyAPI_FUNC(PyObject*) PyList_GetSlice(PyObject* a, Py_ssize_t b, Py_ssize_t c) {
    return GraalPyList_GetSlice(a, b, c);
}
#undef PyList_Insert
PyAPI_FUNC(int) PyList_Insert(PyObject* a, Py_ssize_t b, PyObject* c) {
    return GraalPyList_Insert(a, b, c);
}
#undef PyList_New
PyAPI_FUNC(PyObject*) PyList_New(Py_ssize_t a) {
    return GraalPyList_New(a);
}
#undef PyList_Reverse
PyAPI_FUNC(int) PyList_Reverse(PyObject* a) {
    return GraalPyList_Reverse(a);
}
#undef PyList_SetItem
PyAPI_FUNC(int) PyList_SetItem(PyObject* a, Py_ssize_t b, PyObject* c) {
    return GraalPyList_SetItem(a, b, c);
}
#undef PyList_SetSlice
PyAPI_FUNC(int) PyList_SetSlice(PyObject* a, Py_ssize_t b, Py_ssize_t c, PyObject* d) {
    return GraalPyList_SetSlice(a, b, c, d);
}
#undef PyList_Size
PyAPI_FUNC(Py_ssize_t) PyList_Size(PyObject* a) {
    return GraalPyList_Size(a);
}
#undef PyList_Sort
PyAPI_FUNC(int) PyList_Sort(PyObject* a) {
    return GraalPyList_Sort(a);
}
#undef PyLong_AsVoidPtr
PyAPI_FUNC(void*) PyLong_AsVoidPtr(PyObject* a) {
    return GraalPyLong_AsVoidPtr(a);
}
#undef PyLong_FromDouble
PyAPI_FUNC(PyObject*) PyLong_FromDouble(double a) {
    return GraalPyLong_FromDouble(a);
}
#undef PyLong_FromLong
PyAPI_FUNC(PyObject*) PyLong_FromLong(long a) {
    return GraalPyLong_FromLong(a);
}
#undef PyLong_FromLongLong
PyAPI_FUNC(PyObject*) PyLong_FromLongLong(long long a) {
    return GraalPyLong_FromLongLong(a);
}
#undef PyLong_FromSize_t
PyAPI_FUNC(PyObject*) PyLong_FromSize_t(size_t a) {
    return GraalPyLong_FromSize_t(a);
}
#undef PyLong_FromSsize_t
PyAPI_FUNC(PyObject*) PyLong_FromSsize_t(Py_ssize_t a) {
    return GraalPyLong_FromSsize_t(a);
}
#undef PyLong_FromUnsignedLong
PyAPI_FUNC(PyObject*) PyLong_FromUnsignedLong(unsigned long a) {
    return GraalPyLong_FromUnsignedLong(a);
}
#undef PyLong_FromUnsignedLongLong
PyAPI_FUNC(PyObject*) PyLong_FromUnsignedLongLong(unsigned long long a) {
    return GraalPyLong_FromUnsignedLongLong(a);
}
#undef PyMapping_Check
PyAPI_FUNC(int) PyMapping_Check(PyObject* a) {
    return GraalPyMapping_Check(a);
}
#undef PyMapping_Items
PyAPI_FUNC(PyObject*) PyMapping_Items(PyObject* a) {
    return GraalPyMapping_Items(a);
}
#undef PyMapping_Keys
PyAPI_FUNC(PyObject*) PyMapping_Keys(PyObject* a) {
    return GraalPyMapping_Keys(a);
}
#undef PyMapping_Size
PyAPI_FUNC(Py_ssize_t) PyMapping_Size(PyObject* a) {
    return GraalPyMapping_Size(a);
}
#undef PyMapping_Values
PyAPI_FUNC(PyObject*) PyMapping_Values(PyObject* a) {
    return GraalPyMapping_Values(a);
}
#undef PyMemoryView_FromObject
PyAPI_FUNC(PyObject*) PyMemoryView_FromObject(PyObject* a) {
    return GraalPyMemoryView_FromObject(a);
}
#undef PyMemoryView_GetContiguous
PyAPI_FUNC(PyObject*) PyMemoryView_GetContiguous(PyObject* a, int b, char c) {
    return GraalPyMemoryView_GetContiguous(a, b, c);
}
#undef PyMethod_New
PyAPI_FUNC(PyObject*) PyMethod_New(PyObject* a, PyObject* b) {
    return GraalPyMethod_New(a, b);
}
#undef PyModule_AddIntConstant
PyAPI_FUNC(int) PyModule_AddIntConstant(PyObject* a, const char* b, long c) {
    return GraalPyModule_AddIntConstant(a, truffleString(b), c);
}
#undef PyModule_AddObjectRef
PyAPI_FUNC(int) PyModule_AddObjectRef(PyObject* a, const char* b, PyObject* c) {
    return GraalPyModule_AddObjectRef(a, truffleString(b), c);
}
#undef PyModule_GetNameObject
PyAPI_FUNC(PyObject*) PyModule_GetNameObject(PyObject* a) {
    return GraalPyModule_GetNameObject(a);
}
#undef PyModule_New
PyAPI_FUNC(PyObject*) PyModule_New(const char* a) {
    return GraalPyModule_New(truffleString(a));
}
#undef PyModule_NewObject
PyAPI_FUNC(PyObject*) PyModule_NewObject(PyObject* a) {
    return GraalPyModule_NewObject(a);
}
#undef PyModule_SetDocString
PyAPI_FUNC(int) PyModule_SetDocString(PyObject* a, const char* b) {
    return GraalPyModule_SetDocString(a, truffleString(b));
}
#undef PyNumber_Absolute
PyAPI_FUNC(PyObject*) PyNumber_Absolute(PyObject* a) {
    return GraalPyNumber_Absolute(a);
}
#undef PyNumber_Check
PyAPI_FUNC(int) PyNumber_Check(PyObject* a) {
    return GraalPyNumber_Check(a);
}
#undef PyNumber_Divmod
PyAPI_FUNC(PyObject*) PyNumber_Divmod(PyObject* a, PyObject* b) {
    return GraalPyNumber_Divmod(a, b);
}
#undef PyNumber_Float
PyAPI_FUNC(PyObject*) PyNumber_Float(PyObject* a) {
    return GraalPyNumber_Float(a);
}
#undef PyNumber_InPlacePower
PyAPI_FUNC(PyObject*) PyNumber_InPlacePower(PyObject* a, PyObject* b, PyObject* c) {
    return GraalPyNumber_InPlacePower(a, b, c);
}
#undef PyNumber_Index
PyAPI_FUNC(PyObject*) PyNumber_Index(PyObject* a) {
    return GraalPyNumber_Index(a);
}
#undef PyNumber_Long
PyAPI_FUNC(PyObject*) PyNumber_Long(PyObject* a) {
    return GraalPyNumber_Long(a);
}
#undef PyNumber_Power
PyAPI_FUNC(PyObject*) PyNumber_Power(PyObject* a, PyObject* b, PyObject* c) {
    return GraalPyNumber_Power(a, b, c);
}
#undef PyNumber_ToBase
PyAPI_FUNC(PyObject*) PyNumber_ToBase(PyObject* a, int b) {
    return GraalPyNumber_ToBase(a, b);
}
#undef PyOS_FSPath
PyAPI_FUNC(PyObject*) PyOS_FSPath(PyObject* a) {
    return GraalPyOS_FSPath(a);
}
#undef PyObject_ASCII
PyAPI_FUNC(PyObject*) PyObject_ASCII(PyObject* a) {
    return GraalPyObject_ASCII(a);
}
#undef PyObject_AsFileDescriptor
PyAPI_FUNC(int) PyObject_AsFileDescriptor(PyObject* a) {
    return GraalPyObject_AsFileDescriptor(a);
}
#undef PyObject_Bytes
PyAPI_FUNC(PyObject*) PyObject_Bytes(PyObject* a) {
    return GraalPyObject_Bytes(a);
}
#undef PyObject_ClearWeakRefs
PyAPI_FUNC(void) PyObject_ClearWeakRefs(PyObject* a) {
    GraalPyObject_ClearWeakRefs(a);
}
#undef PyObject_DelItem
PyAPI_FUNC(int) PyObject_DelItem(PyObject* a, PyObject* b) {
    return GraalPyObject_DelItem(a, b);
}
#undef PyObject_Dir
PyAPI_FUNC(PyObject*) PyObject_Dir(PyObject* a) {
    return GraalPyObject_Dir(a);
}
#undef PyObject_Format
PyAPI_FUNC(PyObject*) PyObject_Format(PyObject* a, PyObject* b) {
    return GraalPyObject_Format(a, b);
}
#undef PyObject_GC_Track
PyAPI_FUNC(void) PyObject_GC_Track(void* a) {
    GraalPyObject_GC_Track(a);
}
#undef PyObject_GC_UnTrack
PyAPI_FUNC(void) PyObject_GC_UnTrack(void* a) {
    GraalPyObject_GC_UnTrack(a);
}
#undef PyObject_GetDoc
PyAPI_FUNC(const char*) PyObject_GetDoc(PyObject* a) {
    return GraalPyObject_GetDoc(a);
}
#undef PyObject_GetItem
PyAPI_FUNC(PyObject*) PyObject_GetItem(PyObject* a, PyObject* b) {
    return GraalPyObject_GetItem(a, b);
}
#undef PyObject_GetIter
PyAPI_FUNC(PyObject*) PyObject_GetIter(PyObject* a) {
    return GraalPyObject_GetIter(a);
}
#undef PyObject_HasAttr
PyAPI_FUNC(int) PyObject_HasAttr(PyObject* a, PyObject* b) {
    return GraalPyObject_HasAttr(a, b);
}
#undef PyObject_HasAttrString
PyAPI_FUNC(int) PyObject_HasAttrString(PyObject* a, const char* b) {
    return GraalPyObject_HasAttrString(a, truffleString(b));
}
#undef PyObject_Hash
PyAPI_FUNC(Py_hash_t) PyObject_Hash(PyObject* a) {
    return GraalPyObject_Hash(a);
}
#undef PyObject_HashNotImplemented
PyAPI_FUNC(Py_hash_t) PyObject_HashNotImplemented(PyObject* a) {
    return GraalPyObject_HashNotImplemented(a);
}
#undef PyObject_IsInstance
PyAPI_FUNC(int) PyObject_IsInstance(PyObject* a, PyObject* b) {
    return GraalPyObject_IsInstance(a, b);
}
#undef PyObject_IsSubclass
PyAPI_FUNC(int) PyObject_IsSubclass(PyObject* a, PyObject* b) {
    return GraalPyObject_IsSubclass(a, b);
}
#undef PyObject_IsTrue
PyAPI_FUNC(int) PyObject_IsTrue(PyObject* a) {
    return GraalPyObject_IsTrue(a);
}
#undef PyObject_LengthHint
PyAPI_FUNC(Py_ssize_t) PyObject_LengthHint(PyObject* a, Py_ssize_t b) {
    return GraalPyObject_LengthHint(a, b);
}
#undef PyObject_Repr
PyAPI_FUNC(PyObject*) PyObject_Repr(PyObject* a) {
    return GraalPyObject_Repr(a);
}
#undef PyObject_RichCompare
PyAPI_FUNC(PyObject*) PyObject_RichCompare(PyObject* a, PyObject* b, int c) {
    return GraalPyObject_RichCompare(a, b, c);
}
#undef PyObject_SetDoc
PyAPI_FUNC(int) PyObject_SetDoc(PyObject* a, const char* b) {
    return GraalPyObject_SetDoc(a, truffleString(b));
}
#undef PyObject_SetItem
PyAPI_FUNC(int) PyObject_SetItem(PyObject* a, PyObject* b, PyObject* c) {
    return GraalPyObject_SetItem(a, b, c);
}
#undef PyObject_Size
PyAPI_FUNC(Py_ssize_t) PyObject_Size(PyObject* a) {
    return GraalPyObject_Size(a);
}
#undef PyObject_Str
PyAPI_FUNC(PyObject*) PyObject_Str(PyObject* a) {
    return GraalPyObject_Str(a);
}
#undef PyObject_Type
PyAPI_FUNC(PyObject*) PyObject_Type(PyObject* a) {
    return GraalPyObject_Type(a);
}
#undef PyRun_StringFlags
PyAPI_FUNC(PyObject*) PyRun_StringFlags(const char* a, int b, PyObject* c, PyObject* d, PyCompilerFlags* e) {
    return GraalPyRun_StringFlags(truffleString(a), b, c, d, e);
}
#undef PySeqIter_New
PyAPI_FUNC(PyObject*) PySeqIter_New(PyObject* a) {
    return GraalPySeqIter_New(a);
}
#undef PySequence_Check
PyAPI_FUNC(int) PySequence_Check(PyObject* a) {
    return GraalPySequence_Check(a);
}
#undef PySequence_Concat
PyAPI_FUNC(PyObject*) PySequence_Concat(PyObject* a, PyObject* b) {
    return GraalPySequence_Concat(a, b);
}
#undef PySequence_Contains
PyAPI_FUNC(int) PySequence_Contains(PyObject* a, PyObject* b) {
    return GraalPySequence_Contains(a, b);
}
#undef PySequence_Count
PyAPI_FUNC(Py_ssize_t) PySequence_Count(PyObject* a, PyObject* b) {
    return GraalPySequence_Count(a, b);
}
#undef PySequence_DelItem
PyAPI_FUNC(int) PySequence_DelItem(PyObject* a, Py_ssize_t b) {
    return GraalPySequence_DelItem(a, b);
}
#undef PySequence_DelSlice
PyAPI_FUNC(int) PySequence_DelSlice(PyObject* a, Py_ssize_t b, Py_ssize_t c) {
    return GraalPySequence_DelSlice(a, b, c);
}
#undef PySequence_GetItem
PyAPI_FUNC(PyObject*) PySequence_GetItem(PyObject* a, Py_ssize_t b) {
    return GraalPySequence_GetItem(a, b);
}
#undef PySequence_GetSlice
PyAPI_FUNC(PyObject*) PySequence_GetSlice(PyObject* a, Py_ssize_t b, Py_ssize_t c) {
    return GraalPySequence_GetSlice(a, b, c);
}
#undef PySequence_InPlaceConcat
PyAPI_FUNC(PyObject*) PySequence_InPlaceConcat(PyObject* a, PyObject* b) {
    return GraalPySequence_InPlaceConcat(a, b);
}
#undef PySequence_InPlaceRepeat
PyAPI_FUNC(PyObject*) PySequence_InPlaceRepeat(PyObject* a, Py_ssize_t b) {
    return GraalPySequence_InPlaceRepeat(a, b);
}
#undef PySequence_Index
PyAPI_FUNC(Py_ssize_t) PySequence_Index(PyObject* a, PyObject* b) {
    return GraalPySequence_Index(a, b);
}
#undef PySequence_Length
PyAPI_FUNC(Py_ssize_t) PySequence_Length(PyObject* a) {
    return GraalPySequence_Length(a);
}
#undef PySequence_List
PyAPI_FUNC(PyObject*) PySequence_List(PyObject* a) {
    return GraalPySequence_List(a);
}
#undef PySequence_Repeat
PyAPI_FUNC(PyObject*) PySequence_Repeat(PyObject* a, Py_ssize_t b) {
    return GraalPySequence_Repeat(a, b);
}
#undef PySequence_SetItem
PyAPI_FUNC(int) PySequence_SetItem(PyObject* a, Py_ssize_t b, PyObject* c) {
    return GraalPySequence_SetItem(a, b, c);
}
#undef PySequence_SetSlice
PyAPI_FUNC(int) PySequence_SetSlice(PyObject* a, Py_ssize_t b, Py_ssize_t c, PyObject* d) {
    return GraalPySequence_SetSlice(a, b, c, d);
}
#undef PySequence_Size
PyAPI_FUNC(Py_ssize_t) PySequence_Size(PyObject* a) {
    return GraalPySequence_Size(a);
}
#undef PySequence_Tuple
PyAPI_FUNC(PyObject*) PySequence_Tuple(PyObject* a) {
    return GraalPySequence_Tuple(a);
}
#undef PySet_Add
PyAPI_FUNC(int) PySet_Add(PyObject* a, PyObject* b) {
    return GraalPySet_Add(a, b);
}
#undef PySet_Clear
PyAPI_FUNC(int) PySet_Clear(PyObject* a) {
    return GraalPySet_Clear(a);
}
#undef PySet_Contains
PyAPI_FUNC(int) PySet_Contains(PyObject* a, PyObject* b) {
    return GraalPySet_Contains(a, b);
}
#undef PySet_Discard
PyAPI_FUNC(int) PySet_Discard(PyObject* a, PyObject* b) {
    return GraalPySet_Discard(a, b);
}
#undef PySet_New
PyAPI_FUNC(PyObject*) PySet_New(PyObject* a) {
    return GraalPySet_New(a);
}
#undef PySet_Pop
PyAPI_FUNC(PyObject*) PySet_Pop(PyObject* a) {
    return GraalPySet_Pop(a);
}
#undef PySet_Size
PyAPI_FUNC(Py_ssize_t) PySet_Size(PyObject* a) {
    return GraalPySet_Size(a);
}
#undef PySlice_New
PyAPI_FUNC(PyObject*) PySlice_New(PyObject* a, PyObject* b, PyObject* c) {
    return GraalPySlice_New(a, b, c);
}
#undef PyStaticMethod_New
PyAPI_FUNC(PyObject*) PyStaticMethod_New(PyObject* a) {
    return GraalPyStaticMethod_New(a);
}
#undef PyStructSequence_New
PyAPI_FUNC(PyObject*) PyStructSequence_New(PyTypeObject* a) {
    return GraalPyStructSequence_New(a);
}
#undef PySys_GetObject
PyAPI_FUNC(PyObject*) PySys_GetObject(const char* a) {
    return GraalPySys_GetObject(truffleString(a));
}
#undef PyThreadState_Get
PyAPI_FUNC(PyThreadState*) PyThreadState_Get() {
    return GraalPyThreadState_Get();
}
#undef PyThreadState_GetDict
PyAPI_FUNC(PyObject*) PyThreadState_GetDict() {
    return GraalPyThreadState_GetDict();
}
#undef PyThread_acquire_lock
PyAPI_FUNC(int) PyThread_acquire_lock(PyThread_type_lock a, int b) {
    return GraalPyThread_acquire_lock(a, b);
}
#undef PyThread_allocate_lock
PyAPI_FUNC(PyThread_type_lock) PyThread_allocate_lock() {
    return GraalPyThread_allocate_lock();
}
#undef PyThread_get_thread_ident
PyAPI_FUNC(unsigned long) PyThread_get_thread_ident() {
    return GraalPyThread_get_thread_ident();
}
#undef PyThread_release_lock
PyAPI_FUNC(void) PyThread_release_lock(PyThread_type_lock a) {
    GraalPyThread_release_lock(a);
}
#undef PyTraceBack_Here
PyAPI_FUNC(int) PyTraceBack_Here(PyFrameObject* a) {
    return GraalPyTraceBack_Here(a);
}
#undef PyTraceMalloc_Track
PyAPI_FUNC(int) PyTraceMalloc_Track(unsigned int a, uintptr_t b, size_t c) {
    return GraalPyTraceMalloc_Track(a, b, c);
}
#undef PyTraceMalloc_Untrack
PyAPI_FUNC(int) PyTraceMalloc_Untrack(unsigned int a, uintptr_t b) {
    return GraalPyTraceMalloc_Untrack(a, b);
}
#undef PyTruffleGILState_Ensure
PyAPI_FUNC(int) PyTruffleGILState_Ensure() {
    return GraalPyTruffleGILState_Ensure();
}
#undef PyTruffleGILState_Release
PyAPI_FUNC(void) PyTruffleGILState_Release() {
    GraalPyTruffleGILState_Release();
}
#undef PyTruffle_Debug
PyAPI_FUNC(int) PyTruffle_Debug(void* a) {
    return GraalPyTruffle_Debug(a);
}
#undef PyTruffle_DebugTrace
PyAPI_FUNC(void) PyTruffle_DebugTrace() {
    GraalPyTruffle_DebugTrace();
}
#undef PyTruffle_PyDateTime_GET_TZINFO
PyAPI_FUNC(PyObject*) PyTruffle_PyDateTime_GET_TZINFO(PyObject* a) {
    return GraalPyTruffle_PyDateTime_GET_TZINFO(a);
}
#undef PyTruffle_ToNative
PyAPI_FUNC(int) PyTruffle_ToNative(void* a) {
    return GraalPyTruffle_ToNative(a);
}
#undef PyTruffle_Unicode_AsUTF8AndSize_CharPtr
PyAPI_FUNC(const char*) PyTruffle_Unicode_AsUTF8AndSize_CharPtr(PyObject* a) {
    return GraalPyTruffle_Unicode_AsUTF8AndSize_CharPtr(a);
}
#undef PyTruffle_Unicode_AsUTF8AndSize_Size
PyAPI_FUNC(Py_ssize_t) PyTruffle_Unicode_AsUTF8AndSize_Size(PyObject* a) {
    return GraalPyTruffle_Unicode_AsUTF8AndSize_Size(a);
}
#undef PyTruffle_Unicode_AsUnicodeAndSize_CharPtr
PyAPI_FUNC(Py_UNICODE*) PyTruffle_Unicode_AsUnicodeAndSize_CharPtr(PyObject* a) {
    return GraalPyTruffle_Unicode_AsUnicodeAndSize_CharPtr(a);
}
#undef PyTruffle_Unicode_AsUnicodeAndSize_Size
PyAPI_FUNC(Py_ssize_t) PyTruffle_Unicode_AsUnicodeAndSize_Size(PyObject* a) {
    return GraalPyTruffle_Unicode_AsUnicodeAndSize_Size(a);
}
#undef PyTuple_GetItem
PyAPI_FUNC(PyObject*) PyTuple_GetItem(PyObject* a, Py_ssize_t b) {
    return GraalPyTuple_GetItem(a, b);
}
#undef PyTuple_GetSlice
PyAPI_FUNC(PyObject*) PyTuple_GetSlice(PyObject* a, Py_ssize_t b, Py_ssize_t c) {
    return GraalPyTuple_GetSlice(a, b, c);
}
#undef PyTuple_New
PyAPI_FUNC(PyObject*) PyTuple_New(Py_ssize_t a) {
    return GraalPyTuple_New(a);
}
#undef PyTuple_SetItem
PyAPI_FUNC(int) PyTuple_SetItem(PyObject* a, Py_ssize_t b, PyObject* c) {
    return GraalPyTuple_SetItem(a, b, c);
}
#undef PyTuple_Size
PyAPI_FUNC(Py_ssize_t) PyTuple_Size(PyObject* a) {
    return GraalPyTuple_Size(a);
}
#undef PyType_IsSubtype
PyAPI_FUNC(int) PyType_IsSubtype(PyTypeObject* a, PyTypeObject* b) {
    return GraalPyType_IsSubtype(a, b);
}
#undef PyUnicodeDecodeError_Create
PyAPI_FUNC(PyObject*) PyUnicodeDecodeError_Create(const char* a, const char* b, Py_ssize_t c, Py_ssize_t d, Py_ssize_t e, const char* f) {
    return GraalPyUnicodeDecodeError_Create(truffleString(a), b, c, d, e, truffleString(f));
}
#undef PyUnicode_AsEncodedString
PyAPI_FUNC(PyObject*) PyUnicode_AsEncodedString(PyObject* a, const char* b, const char* c) {
    return GraalPyUnicode_AsEncodedString(a, truffleString(b), truffleString(c));
}
#undef PyUnicode_AsUnicodeEscapeString
PyAPI_FUNC(PyObject*) PyUnicode_AsUnicodeEscapeString(PyObject* a) {
    return GraalPyUnicode_AsUnicodeEscapeString(a);
}
#undef PyUnicode_Compare
PyAPI_FUNC(int) PyUnicode_Compare(PyObject* a, PyObject* b) {
    return GraalPyUnicode_Compare(a, b);
}
#undef PyUnicode_Concat
PyAPI_FUNC(PyObject*) PyUnicode_Concat(PyObject* a, PyObject* b) {
    return GraalPyUnicode_Concat(a, b);
}
#undef PyUnicode_Contains
PyAPI_FUNC(int) PyUnicode_Contains(PyObject* a, PyObject* b) {
    return GraalPyUnicode_Contains(a, b);
}
#undef PyUnicode_DecodeFSDefault
PyAPI_FUNC(PyObject*) PyUnicode_DecodeFSDefault(const char* a) {
    return GraalPyUnicode_DecodeFSDefault(truffleString(a));
}
#undef PyUnicode_EncodeFSDefault
PyAPI_FUNC(PyObject*) PyUnicode_EncodeFSDefault(PyObject* a) {
    return GraalPyUnicode_EncodeFSDefault(a);
}
#undef PyUnicode_FindChar
PyAPI_FUNC(Py_ssize_t) PyUnicode_FindChar(PyObject* a, Py_UCS4 b, Py_ssize_t c, Py_ssize_t d, int e) {
    return GraalPyUnicode_FindChar(a, b, c, d, e);
}
#undef PyUnicode_Format
PyAPI_FUNC(PyObject*) PyUnicode_Format(PyObject* a, PyObject* b) {
    return GraalPyUnicode_Format(a, b);
}
#undef PyUnicode_FromEncodedObject
PyAPI_FUNC(PyObject*) PyUnicode_FromEncodedObject(PyObject* a, const char* b, const char* c) {
    return GraalPyUnicode_FromEncodedObject(a, truffleString(b), truffleString(c));
}
#undef PyUnicode_FromObject
PyAPI_FUNC(PyObject*) PyUnicode_FromObject(PyObject* a) {
    return GraalPyUnicode_FromObject(a);
}
#undef PyUnicode_FromOrdinal
PyAPI_FUNC(PyObject*) PyUnicode_FromOrdinal(int a) {
    return GraalPyUnicode_FromOrdinal(a);
}
#undef PyUnicode_FromString
PyAPI_FUNC(PyObject*) PyUnicode_FromString(const char* a) {
    return GraalPyUnicode_FromString(truffleString(a));
}
#undef PyUnicode_Join
PyAPI_FUNC(PyObject*) PyUnicode_Join(PyObject* a, PyObject* b) {
    return GraalPyUnicode_Join(a, b);
}
#undef PyUnicode_ReadChar
PyAPI_FUNC(Py_UCS4) PyUnicode_ReadChar(PyObject* a, Py_ssize_t b) {
    return GraalPyUnicode_ReadChar(a, b);
}
#undef PyUnicode_Replace
PyAPI_FUNC(PyObject*) PyUnicode_Replace(PyObject* a, PyObject* b, PyObject* c, Py_ssize_t d) {
    return GraalPyUnicode_Replace(a, b, c, d);
}
#undef PyUnicode_Split
PyAPI_FUNC(PyObject*) PyUnicode_Split(PyObject* a, PyObject* b, Py_ssize_t c) {
    return GraalPyUnicode_Split(a, b, c);
}
#undef PyUnicode_Substring
PyAPI_FUNC(PyObject*) PyUnicode_Substring(PyObject* a, Py_ssize_t b, Py_ssize_t c) {
    return GraalPyUnicode_Substring(a, b, c);
}
#undef PyUnicode_Tailmatch
PyAPI_FUNC(Py_ssize_t) PyUnicode_Tailmatch(PyObject* a, PyObject* b, Py_ssize_t c, Py_ssize_t d, int e) {
    return GraalPyUnicode_Tailmatch(a, b, c, d, e);
}
#undef PyWeakref_GetObject
PyAPI_FUNC(PyObject*) PyWeakref_GetObject(PyObject* a) {
    return GraalPyWeakref_GetObject(a);
}
#undef PyWeakref_NewRef
PyAPI_FUNC(PyObject*) PyWeakref_NewRef(PyObject* a, PyObject* b) {
    return GraalPyWeakref_NewRef(a, b);
}
#undef Py_AtExit
PyAPI_FUNC(int) Py_AtExit(void (*a)(void)) {
    return GraalPy_AtExit(a);
}
#undef Py_CompileString
PyAPI_FUNC(PyObject*) Py_CompileString(const char* a, const char* b, int c) {
    return GraalPy_CompileString(truffleString(a), truffleString(b), c);
}
#undef Py_CompileStringExFlags
PyAPI_FUNC(PyObject*) Py_CompileStringExFlags(const char* a, const char* b, int c, PyCompilerFlags* d, int e) {
    return GraalPy_CompileStringExFlags(truffleString(a), truffleString(b), c, d, e);
}
#undef Py_CompileStringObject
PyAPI_FUNC(PyObject*) Py_CompileStringObject(const char* a, PyObject* b, int c, PyCompilerFlags* d, int e) {
    return GraalPy_CompileStringObject(truffleString(a), b, c, d, e);
}
#undef Py_EnterRecursiveCall
PyAPI_FUNC(int) Py_EnterRecursiveCall(const char* a) {
    return GraalPy_EnterRecursiveCall(a);
}
#undef Py_GenericAlias
PyAPI_FUNC(PyObject*) Py_GenericAlias(PyObject* a, PyObject* b) {
    return GraalPy_GenericAlias(a, b);
}
#undef Py_LeaveRecursiveCall
PyAPI_FUNC(void) Py_LeaveRecursiveCall() {
    GraalPy_LeaveRecursiveCall();
}
#undef _PyBytes_Join
PyAPI_FUNC(PyObject*) _PyBytes_Join(PyObject* a, PyObject* b) {
    return Graal_PyBytes_Join(a, b);
}
#undef _PyDict_Pop
PyAPI_FUNC(PyObject*) _PyDict_Pop(PyObject* a, PyObject* b, PyObject* c) {
    return Graal_PyDict_Pop(a, b, c);
}
#undef _PyDict_SetItem_KnownHash
PyAPI_FUNC(int) _PyDict_SetItem_KnownHash(PyObject* a, PyObject* b, PyObject* c, Py_hash_t d) {
    return Graal_PyDict_SetItem_KnownHash(a, b, c, d);
}
#undef _PyErr_BadInternalCall
PyAPI_FUNC(void) _PyErr_BadInternalCall(const char* a, int b) {
    Graal_PyErr_BadInternalCall(truffleString(a), b);
}
#undef _PyErr_WriteUnraisableMsg
PyAPI_FUNC(void) _PyErr_WriteUnraisableMsg(const char* a, PyObject* b) {
    Graal_PyErr_WriteUnraisableMsg(truffleString(a), b);
}
#undef _PyList_Extend
PyAPI_FUNC(PyObject*) _PyList_Extend(PyListObject* a, PyObject* b) {
    return Graal_PyList_Extend(a, b);
}
#undef _PyList_SET_ITEM
PyAPI_FUNC(void) _PyList_SET_ITEM(PyObject* a, Py_ssize_t b, PyObject* c) {
    Graal_PyList_SET_ITEM(a, b, c);
}
#undef _PyLong_Sign
PyAPI_FUNC(int) _PyLong_Sign(PyObject* a) {
    return Graal_PyLong_Sign(a);
}
#undef _PyNamespace_New
PyAPI_FUNC(PyObject*) _PyNamespace_New(PyObject* a) {
    return Graal_PyNamespace_New(a);
}
#undef _PyNumber_Index
PyAPI_FUNC(PyObject*) _PyNumber_Index(PyObject* a) {
    return Graal_PyNumber_Index(a);
}
#undef _PyObject_Dump
PyAPI_FUNC(void) _PyObject_Dump(PyObject* a) {
    Graal_PyObject_Dump(a);
}
#undef _PyTraceMalloc_NewReference
PyAPI_FUNC(int) _PyTraceMalloc_NewReference(PyObject* a) {
    return Graal_PyTraceMalloc_NewReference(a);
}
#undef _PyTraceback_Add
PyAPI_FUNC(void) _PyTraceback_Add(const char* a, const char* b, int c) {
    Graal_PyTraceback_Add(truffleString(a), truffleString(b), c);
}
#undef _PyTruffleErr_CreateAndSetException
PyAPI_FUNC(void) _PyTruffleErr_CreateAndSetException(PyObject* a, PyObject* b) {
    Graal_PyTruffleErr_CreateAndSetException(a, b);
}
#undef _PyTruffleObject_Call1
PyAPI_FUNC(PyObject*) _PyTruffleObject_Call1(PyObject* a, PyObject* b, PyObject* c, int d) {
    return Graal_PyTruffleObject_Call1(a, b, c, d);
}
#undef _PyTruffleObject_CallMethod1
PyAPI_FUNC(PyObject*) _PyTruffleObject_CallMethod1(PyObject* a, const char* b, PyObject* c, int d) {
    return Graal_PyTruffleObject_CallMethod1(a, truffleString(b), c, d);
}
#undef _PyTuple_SET_ITEM
PyAPI_FUNC(int) _PyTuple_SET_ITEM(PyObject* a, Py_ssize_t b, PyObject* c) {
    return Graal_PyTuple_SET_ITEM(a, b, c);
}
#undef _PyType_Lookup
PyAPI_FUNC(PyObject*) _PyType_Lookup(PyTypeObject* a, PyObject* b) {
    return Graal_PyType_Lookup(a, b);
}
#undef _PyUnicode_AsASCIIString
PyAPI_FUNC(PyObject*) _PyUnicode_AsASCIIString(PyObject* a, const char* b) {
    return Graal_PyUnicode_AsASCIIString(a, truffleString(b));
}
#undef _PyUnicode_AsLatin1String
PyAPI_FUNC(PyObject*) _PyUnicode_AsLatin1String(PyObject* a, const char* b) {
    return Graal_PyUnicode_AsLatin1String(a, truffleString(b));
}
#undef _PyUnicode_AsUTF8String
PyAPI_FUNC(PyObject*) _PyUnicode_AsUTF8String(PyObject* a, const char* b) {
    return Graal_PyUnicode_AsUTF8String(a, truffleString(b));
}
#undef _PyUnicode_EqualToASCIIString
PyAPI_FUNC(int) _PyUnicode_EqualToASCIIString(PyObject* a, const char* b) {
    return Graal_PyUnicode_EqualToASCIIString(a, truffleString(b));
}
#undef _Py_GetErrorHandler
PyAPI_FUNC(_Py_error_handler) _Py_GetErrorHandler(const char* a) {
    return Graal_Py_GetErrorHandler(truffleString(a));
}
#undef _Py_HashDouble
PyAPI_FUNC(Py_hash_t) _Py_HashDouble(PyObject* a, double b) {
    return Graal_Py_HashDouble(a, b);
}
// {{end CAPI_BUILTINS}}
