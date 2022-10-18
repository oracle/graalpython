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


typedef struct arrayobject {
    PyObject_VAR_HEAD
    char *ob_item;
    Py_ssize_t allocated;
    const struct arraydescr *ob_descr;
    PyObject *weakreflist; /* List of weak references */
    int ob_exports;  /* Number of exported buffers */
} arrayobject;

PyTypeObject Arraytype = PY_TRUFFLE_TYPE("array", &PyType_Type, Py_TPFLAGS_DEFAULT | Py_TPFLAGS_BASETYPE | Py_TPFLAGS_SEQUENCE, sizeof(arrayobject));

typedef struct {
    PyObject_HEAD
    Py_ssize_t index;
    arrayobject *ao;
    PyObject* (*getitem)(struct arrayobject *, Py_ssize_t);
} arrayiterobject;

// add structure hint for declaring PyCapsule type
/* Internal structure of PyCapsule */
typedef struct {
    PyObject_HEAD
    void *pointer;
    const char *name;
    void *context;
    PyCapsule_Destructor destructor;
} PyCapsule;


PyTypeObject PyArrayIter_Type = PY_TRUFFLE_TYPE("arrayiterator", &PyType_Type, Py_TPFLAGS_DEFAULT | Py_TPFLAGS_HAVE_GC, sizeof(arrayiterobject));

void *PY_TRUFFLE_CEXT;
void *PY_BUILTIN;
void *PY_SYS;
void *Py_NoValue;

void*(*pytruffle_decorate_function)(void *fun0, void* fun1);

PyObject*(*PY_TRUFFLE_LANDING_BORROWED)(void *rcv, void* name, ...);
PyObject*(*PY_TRUFFLE_LANDING_NEWREF)(void *rcv, void* name, ...);
void*(*PY_TRUFFLE_LANDING_L)(void *rcv, void* name, ...);
void*(*PY_TRUFFLE_LANDING_D)(void *rcv, void* name, ...);
void*(*PY_TRUFFLE_LANDING_PTR)(void *rcv, void* name, ...);
PyObject*(*PY_TRUFFLE_CEXT_LANDING_BORROWED)(void* name, ...);
PyObject*(*PY_TRUFFLE_CEXT_LANDING_NEWREF)(void* name, ...);
void* (*PY_TRUFFLE_CEXT_LANDING_L)(void* name, ...);
void* (*PY_TRUFFLE_CEXT_LANDING_D)(void* name, ...);
void* (*PY_TRUFFLE_CEXT_LANDING_PTR)(void* name, ...);

uint32_t Py_Truffle_Options;


ptr_cache_t ptr_cache;
ptr_cache_t ptr_cache_stealing;
type_ptr_cache_t type_ptr_cache;

alloc_upcall_fun_t alloc_upcall;
free_upcall_fun_t free_upcall;

__attribute__((constructor (__COUNTER__)))
static void initialize_upcall_functions() {
    PY_TRUFFLE_CEXT = (void*)polyglot_eval("python", "import python_cext\npython_cext");
    PY_BUILTIN = (void*)polyglot_eval("python", "import builtins\nbuiltins");
    PY_SYS = (void*)polyglot_eval("python", "import sys\nsys");

    pytruffle_decorate_function = ((void*(*)(void *fun0, void* fun1))polyglot_get_member(PY_TRUFFLE_CEXT, polyglot_from_string("PyTruffle_Decorate_Function", SRC_CS)));

    PY_TRUFFLE_LANDING_BORROWED = ((PyObject*(*)(void *rcv, void* name, ...))polyglot_get_member(PY_TRUFFLE_CEXT, polyglot_from_string("PyTruffle_Upcall_Borrowed", SRC_CS)));
    PY_TRUFFLE_LANDING_NEWREF = ((PyObject*(*)(void *rcv, void* name, ...))polyglot_get_member(PY_TRUFFLE_CEXT, polyglot_from_string("PyTruffle_Upcall_NewRef", SRC_CS)));
    PY_TRUFFLE_LANDING_L = ((void*(*)(void *rcv, void* name, ...))polyglot_get_member(PY_TRUFFLE_CEXT, polyglot_from_string("PyTruffle_Upcall_l", SRC_CS)));
    PY_TRUFFLE_LANDING_D = ((void*(*)(void *rcv, void* name, ...))polyglot_get_member(PY_TRUFFLE_CEXT, polyglot_from_string("PyTruffle_Upcall_d", SRC_CS)));
    PY_TRUFFLE_LANDING_PTR = ((void*(*)(void *rcv, void* name, ...))polyglot_get_member(PY_TRUFFLE_CEXT, polyglot_from_string("PyTruffle_Upcall_ptr", SRC_CS)));
    PY_TRUFFLE_CEXT_LANDING_BORROWED = ((PyObject*(*)(void* name, ...))polyglot_get_member(PY_TRUFFLE_CEXT, polyglot_from_string("PyTruffle_Cext_Upcall_Borrowed", SRC_CS)));
    PY_TRUFFLE_CEXT_LANDING_NEWREF = ((PyObject*(*)(void* name, ...))polyglot_get_member(PY_TRUFFLE_CEXT, polyglot_from_string("PyTruffle_Cext_Upcall_NewRef", SRC_CS)));
    PY_TRUFFLE_CEXT_LANDING_L = ((void*(*)(void* name, ...))polyglot_get_member(PY_TRUFFLE_CEXT, polyglot_from_string("PyTruffle_Cext_Upcall_l", SRC_CS)));
    PY_TRUFFLE_CEXT_LANDING_D = ((void*(*)(void* name, ...))polyglot_get_member(PY_TRUFFLE_CEXT, polyglot_from_string("PyTruffle_Cext_Upcall_d", SRC_CS)));
    PY_TRUFFLE_CEXT_LANDING_PTR = ((void*(*)(void* name, ...))polyglot_get_member(PY_TRUFFLE_CEXT, polyglot_from_string("PyTruffle_Cext_Upcall_ptr", SRC_CS)));

    Py_Truffle_Options = (uint32_t) polyglot_as_i32(polyglot_invoke(PY_TRUFFLE_CEXT, "PyTruffle_Native_Options"));

    Py_NoValue = UPCALL_CEXT_O(polyglot_from_string("Py_NoValue", SRC_CS));

    alloc_upcall = (alloc_upcall_fun_t) polyglot_invoke(PY_TRUFFLE_CEXT, "PyTruffle_Create_Lightweight_Upcall", polyglot_from_string("PyTruffle_Object_Alloc", SRC_CS));
    free_upcall = (free_upcall_fun_t) polyglot_invoke(PY_TRUFFLE_CEXT, "PyTruffle_Create_Lightweight_Upcall", polyglot_from_string("PyTruffle_Object_Free", SRC_CS));
}

__attribute__((constructor (__COUNTER__)))
static void initialize_handle_cache() {
    ptr_cache = (ptr_cache_t) polyglot_invoke(PY_TRUFFLE_CEXT, "PyTruffle_PtrCache_Create", 0);
    ptr_cache_stealing = (ptr_cache_t) polyglot_invoke(PY_TRUFFLE_CEXT, "PyTruffle_PtrCache_Create", 1);
    type_ptr_cache = (type_ptr_cache_t) polyglot_invoke(PY_TRUFFLE_CEXT, "PyTruffle_PtrCache_Create", 0);
}

void initialize_type_structure(PyTypeObject* structure, PyTypeObject* ptype, polyglot_typeid tid) {
    // Store the Sulong struct type id to be used for instances of this class
    polyglot_invoke(PY_TRUFFLE_CEXT, "PyTruffle_Set_SulongType", ptype, tid);

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
    type_handle->tp_flags = original_flags | Py_TPFLAGS_READY | Py_TPFLAGS_IMMUTABLETYPE;
    type_handle->tp_basicsize = basicsize;
    type_handle->tp_itemsize = itemsize;
    if (alloc_fun) {
        type_handle->tp_alloc = alloc_fun;
    }
    if (dealloc_fun) {
        type_handle->tp_dealloc = dealloc_fun;
    }
    if (free_fun) {
        type_handle->tp_free = free_fun;
    }
    if (free_fun) {
        type_handle->tp_free = free_fun;
    }
    if (vectorcall_offset) {
        type_handle->tp_vectorcall_offset = vectorcall_offset;
    }
    if (as_buffer) {
        type_handle->tp_as_buffer = as_buffer;
    }
}

static void initialize_builtin_type(PyTypeObject* structure, const char* typname, polyglot_typeid tid) {
    PyTypeObject* ptype = (PyTypeObject*)UPCALL_CEXT_O(polyglot_from_string("PyTruffle_Type", SRC_CS), polyglot_from_string(typname, SRC_CS));
    initialize_type_structure(structure, ptype, tid);
}

#define ctor_hidden(a) __attribute__((constructor (10 ## a
#define ctor(a) ctor_hidden(a))))
#define init_hidden(a, b) initialize ## a ## _ ## b ## _gen
#define init(a, b) init_hidden(a, b)

#define initialize_type(typeobject, typename, struct)              \
    ctor(__COUNTER__)                                              \
    static void init(__COUNTER__, typeobject)(void) {              \
        initialize_builtin_type(&typeobject,                       \
                                #typename,                         \
                                polyglot_ ## struct ## _typeid()); \
    }

#define declare_struct(typeobject, typename, struct)    \
    POLYGLOT_DECLARE_STRUCT(struct);                    \
    initialize_type(typeobject, typename, struct)

#define declare_type(typeobject, typename, objecttype)  \
    POLYGLOT_DECLARE_TYPE(objecttype);                  \
    initialize_type(typeobject, typename, objecttype)

declare_struct(PyType_Type, type, _typeobject);
declare_struct(PyBaseObject_Type, object, _object);
declare_type(PyUnicode_Type, str, PyUnicodeObject);
declare_struct(PyLong_Type, int, _longobject);
declare_type(PyBytes_Type, bytes, PyBytesObject);
declare_type(PyDict_Type, dict, PyDictObject);
declare_type(PyTuple_Type, tuple, PyTupleObject);
declare_type(PyList_Type, list, PyListObject);
declare_type(Arraytype, array, arrayobject);
declare_type(PyArrayIter_Type, arrayiterator, arrayiterobject);
declare_type(PyComplex_Type, complex, PyComplexObject);
declare_type(PyModule_Type, module, PyModuleObject);
declare_type(PyModuleDef_Type, moduledef, PyModuleDef);
declare_type(PyMemoryView_Type, memoryview, PyMemoryViewObject);
declare_type(PySet_Type, set, PySetObject);
declare_type(PyFloat_Type, float, PyFloatObject);
declare_type(PySlice_Type, slice, PySliceObject);
declare_type(PyByteArray_Type, bytearray, PyByteArrayObject);
declare_type(PyCFunction_Type, builtin_function_or_method, PyCFunctionObject);
declare_type(PyCMethod_Type, builtin_method, PyCMethodObject);
declare_type(PyWrapperDescr_Type, wrapper_descriptor, PyWrapperDescrObject);
declare_type(PyCapsule_Type, PyCapsule, PyCapsule);
// tfel: Both method_descriptor maps to both PyWrapperDescr_Type and
// PyMethodDescr_Type. This reflects our interpreter, but we need to make sure
// that the dynamic type for method_descriptor is always going to be
// PyMethodDescr_Type, so these two declarations cannot be in the wrong order
declare_type(PyMethodDescr_Type, method_descriptor, PyMethodDescrObject);
declare_type(PyGetSetDescr_Type, getset_descriptor, PyGetSetDescrObject);
declare_type(PyMemberDescr_Type, member_descriptor, PyMemberDescrObject);
declare_type(_PyExc_BaseException, BaseException, PyBaseExceptionObject);
declare_type(_PyExc_StopIteration, StopIteration, PyStopIterationObject);
declare_type(PyBuffer_Type, buffer, PyBufferDecorator);
declare_type(PyFunction_Type, function, PyFunctionObject);
declare_type(PyMethod_Type, method, PyMethodObject);
declare_type(PyInstanceMethod_Type, instancemethod, PyInstanceMethodObject);
declare_type(PyCode_Type, code, PyCodeObject);
declare_type(PyFrame_Type, frame, PyFrameObject);
declare_type(PyTraceBack_Type, traceback, PyTracebackObject);
declare_type(_PyWeakref_RefType, ReferenceType, PyWeakReference);
declare_type(PyGen_Type, generator, PyGenObject);
declare_type(PyProperty_Type, property, propertyobject);
// Below types use the same object structure as others, and thus
// POLYGLOT_DECLARE_TYPE should not be called again
initialize_type(PySuper_Type, super, _object);
initialize_type(_PyNone_Type, NoneType, _object);
initialize_type(PyFrozenSet_Type, frozenset, PySetObject);
initialize_type(PyBool_Type, bool, _longobject);
initialize_type(_PyNotImplemented_Type, NotImplementedType, _object);
initialize_type(PyDictProxy_Type, mappingproxy, _object);
initialize_type(PyEllipsis_Type, ellipsis, _object);
initialize_type(_PyWeakref_ProxyType, ProxyType, PyWeakReference);
initialize_type(_PyWeakref_CallableProxyType, CallableProxyType, PyWeakReference);

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
    // register native NULL
    wrapped_null = polyglot_invoke(PY_TRUFFLE_CEXT, polyglot_from_string("PyTruffle_Register_NULL", SRC_CS), NULL);

    // None
    _Py_NoneStructReference = (void*) UPCALL_CEXT_O(polyglot_from_string("Py_None", SRC_CS));

    // NotImplemented
    _Py_NotImplementedStructReference = (void*) UPCALL_CEXT_O(polyglot_from_string("Py_NotImplemented", SRC_CS));

    // Ellipsis
    _Py_EllipsisObjectReference = (void*) UPCALL_CEXT_O(polyglot_from_string("Py_Ellipsis", SRC_CS));

    // True, False
    _Py_TrueStructReference = (void*) UPCALL_CEXT_O(polyglot_from_string("Py_True", SRC_CS));
    _Py_FalseStructReference = (void*) UPCALL_CEXT_O(polyglot_from_string("Py_False", SRC_CS));

    _PyTruffle_Zero = (void*) UPCALL_CEXT_O(polyglot_from_string("PyLong_FromLongLong", SRC_CS), 0, 1);
    _PyTruffle_One = (void*) UPCALL_CEXT_O(polyglot_from_string("PyLong_FromLongLong", SRC_CS), 1, 1);
}

static void initialize_bufferprocs() {
    static PyBufferProcs bytes_as_buffer = {
        (getbufferproc)bytes_buffer_getbuffer,       /* bf_getbuffer */
        (releasebufferproc)NULL,                     /* bf_releasebuffer */
    };
    PyBytes_Type.tp_as_buffer = &bytes_as_buffer;

    static PyBufferProcs bytearray_as_buffer = {
        (getbufferproc)bytearray_getbuffer,          /* bf_getbuffer */
        (releasebufferproc)bytearray_releasebuffer,  /* bf_releasebuffer */
    };
    PyByteArray_Type.tp_as_buffer = &bytearray_as_buffer;

    static PyBufferProcs buffer_as_buffer = {
        (getbufferproc)bufferdecorator_getbuffer,    /* bf_getbuffer */
        (releasebufferproc)NULL,                     /* bf_releasebuffer */
    };
    PyBuffer_Type.tp_as_buffer = &buffer_as_buffer;

    static PyBufferProcs memory_as_buffer = {
        (getbufferproc)memoryview_getbuffer,         /* bf_getbuffer */
        (releasebufferproc)memoryview_releasebuffer, /* bf_releasebuffer */
    };
    PyMemoryView_Type.tp_as_buffer = &memory_as_buffer;
}

static void initialize_filesystemencoding() {
    Py_FileSystemDefaultEncoding = (const char *)to_sulong(polyglot_invoke(PY_SYS, "getfilesystemencoding"));
}

__attribute__((constructor (20000)))
static void initialize_capi() {
    // initialize global variables like '_Py_NoneStruct', etc.
    initialize_globals();
    initialize_exceptions();
    initialize_hashes();
    initialize_bufferprocs();
    initialize_filesystemencoding();
}

// Workaround: use 'uint64' to avoid conversion to an LLVM boxed primitive such
// that it is guaranteed to return a pointer object.
void* native_long_to_java(uint64_t val) {
	PyObject* obj = (PyObject*) val;
    if (obj == NULL) {
        return Py_NoValue;
    } else if (obj == Py_None) {
        return Py_None;
    } else if (points_to_handle_space(obj)) {
        return resolve_handle((void*) obj);
    }
    return obj;
}

MUST_INLINE
void* to_java(PyObject* obj) {
    return polyglot_invoke(PY_TRUFFLE_CEXT, "to_java", native_to_java(obj));
}

void* to_java_type(PyTypeObject* cls) {
    return to_java((PyObject*)cls);
}

PyObject* to_sulong(void *o) {
    return polyglot_invoke(PY_TRUFFLE_CEXT, "to_sulong", o);
}

/** to be used from Java code only; reads native fields */
#define TYPE_FIELD_GETTER(RECEIVER, NAME) \
PyAPI_FUNC(PyTypeObject*) get_##NAME(RECEIVER obj) {                   \
    return native_type_to_java(obj->NAME);          \
}
#define OBJECT_FIELD_GETTER(RECEIVER, NAME) \
PyAPI_FUNC(PyObject*) get_##NAME(RECEIVER obj) {                   \
    return native_to_java((PyObject*) obj->NAME);          \
}
#define PRIMITIVE_FIELD_GETTER(RECEIVER, RESULT, NAME) \
PyAPI_FUNC(RESULT) get_##NAME(RECEIVER obj) {                      \
    return obj->NAME;                                  \
}

TYPE_FIELD_GETTER(PyObject*, ob_type)
PRIMITIVE_FIELD_GETTER(PyObject*, Py_ssize_t, ob_refcnt)
OBJECT_FIELD_GETTER(PyTypeObject*, tp_dict)
OBJECT_FIELD_GETTER(PyTypeObject*, tp_base)
OBJECT_FIELD_GETTER(PyTypeObject*, tp_bases)
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

/** to be used from Java code only; calls DECREF */
PyAPI_FUNC(Py_ssize_t) PyTruffle_ADDREF(PyObject* obj, Py_ssize_t value) {
    return (obj->ob_refcnt += value);
}

/** to be used from Java code only; calls DECREF */
PyAPI_FUNC(Py_ssize_t) PyTruffle_SUBREF(PyObject* obj, Py_ssize_t value) {
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
PyAPI_FUNC(Py_ssize_t) PyTruffle_bulk_SUBREF(PyObject *ptrArray[], Py_ssize_t values[], int64_t len) {
    int64_t i;
    PyObject *obj;
    Py_ssize_t value;

    for (i = 0; i < len; i++) {
        obj = ptrArray[i];
        value = values[i];
        /* IMPORTANT: 'value == 0' indicates we should not process the reference at all */
        if (value > 0) {
            Py_ssize_t new_value = ((obj->ob_refcnt) -= value);
            if (new_value == 0) {
                _Py_Dealloc(obj);
            }
#ifdef Py_REF_DEBUG
            else if (new_value < 0) {
                _Py_NegativeRefcount(filename, lineno, op);
            }
#endif
        }
    }
    return 0;
}

typedef struct PyObjectHandle {
    PyObject_HEAD
} PyObjectHandle;

PyAPI_FUNC(uint64_t) PyTruffle_Wchar_Size() {
    return SIZEOF_WCHAR_T;
}

/** free's a native pointer or releases a Sulong handle; DO NOT CALL WITH MANAGED POINTERS ! */
PyAPI_FUNC(void) PyTruffle_Free(unsigned long val) {
	void *obj = (void*) val;
    if (points_to_handle_space(obj) && is_handle(obj)) {
        release_handle(obj);
    } else {
        PyMem_RawFree(obj);
    }
}

/** to be used from Java code only; creates the deref handle for a sequence wrapper */
PyAPI_FUNC(void*) NativeHandle_ForArray(void* jobj, Py_ssize_t element_size) {
    return create_deref_handle(jobj);
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
    return polyglot_from_string(o, SRC_CS);
}

/* Use this function to decode a C ASCII string to a Java string. */
PyAPI_FUNC(void*) PyTruffle_AsciiToString(void* ptr) {
    return polyglot_from_string(ptr, "ascii");
}

/* To be used from Java code only.
 * This function is used if a native class inherits from a managed class but uses the 'object.__new__'.
 * This function roughly corresponds to CPython's 'object_new'. */
PyAPI_FUNC(PyObject*) PyTruffle_Object_New(PyTypeObject* cls, PyTypeObject* dominatingNativeClass, PyObject* args, PyObject* kwds) {
    return native_to_java_stealing(dominatingNativeClass->tp_alloc(cls, 0));
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

void* ReadStringMember(PyObject* object, Py_ssize_t offset) {
    char *ptr = ReadMember(object, offset, char*);
    if (ptr != NULL) {
    	return polyglot_from_string(ReadMember(object, offset, char*), "utf-8");
    }
    return NULL;
}

void* ReadStringInPlaceMember(PyObject* object, Py_ssize_t offset) {
    char *addr = (char*) (((char*)object) + offset);
    return polyglot_from_string(addr, "utf-8");
}


PyObject* ReadObjectMember(PyObject* object, Py_ssize_t offset) {
    PyObject* member = ReadMember(object, offset, PyObject*);
    if (member == NULL) {
        member = Py_None;
    }
    Py_INCREF(member);
    return native_to_java(member);
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
        return native_to_java(member);
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

PyObject marker_struct = {
    _PyObject_EXTRA_INIT
    1, &PyBaseObject_Type
};

#undef ReadMember
#undef WriteMember

PyAPI_FUNC(int) PyTruffle_Debug(void *arg) {
    polyglot_invoke(PY_TRUFFLE_CEXT, "PyTruffle_Debug", arg);
    return 0;
}

PyAPI_FUNC(int) PyTruffle_ToNative(void *arg) {
	if (polyglot_has_member(PY_TRUFFLE_CEXT, "PyTruffle_ToNative")) {
		polyglot_invoke(PY_TRUFFLE_CEXT, "PyTruffle_ToNative", arg);
		return 0;
	} else {
		printf("PyTruffle_ToNative is not enabled - enable with --python.EnableDebuggingBuiltins\n");
		return 1;
	}
}

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
        polyglot_invoke(PY_TRUFFLE_CEXT, "PyTruffle_Set_Native_Slots", native_type_to_java(managed_class), native_pointer_to_java(getsets), native_pointer_to_java(members));
    }
}

PyAPI_FUNC(PyObject*) truffle_create_datetime_capsule(void *object) {
    if (PyType_Ready(&PyCapsule_Type) < 0) {
        return NULL;
    }
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
