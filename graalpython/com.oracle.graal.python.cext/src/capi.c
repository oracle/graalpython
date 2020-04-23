/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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

void *PY_TRUFFLE_CEXT;
void *PY_BUILTIN;
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


cache_t cache;
ptr_cache_t ptr_cache;
ptr_cache_t ptr_cache_stealing;

alloc_upcall_fun_t alloc_upcall;
free_upcall_fun_t free_upcall;

__attribute__((constructor (__COUNTER__)))
static void initialize_upcall_functions() {
    PY_TRUFFLE_CEXT = (void*)polyglot_eval("python", "import python_cext\npython_cext");
    PY_BUILTIN = (void*)polyglot_eval("python", "import builtins\nbuiltins");

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
    cache = (cache_t)polyglot_invoke(PY_TRUFFLE_CEXT, "PyTruffle_HandleCache_Create", truffle_managed_from_handle);
    ptr_cache = (ptr_cache_t)polyglot_invoke(PY_TRUFFLE_CEXT, "PyTruffle_PtrCache_Create", 0);
    ptr_cache_stealing = (ptr_cache_t)polyglot_invoke(PY_TRUFFLE_CEXT, "PyTruffle_PtrCache_Create", 1);
}

void initialize_type_structure(PyTypeObject* structure, PyTypeObject* ptype, polyglot_typeid tid) {
    // Store the Sulong struct type id to be used for instances of this class
    polyglot_invoke(PY_TRUFFLE_CEXT, "PyTruffle_Set_SulongType", ptype, tid);

    unsigned long original_flags = structure->tp_flags;
    Py_ssize_t basicsize = structure->tp_basicsize;
    allocfunc alloc_fun = structure->tp_alloc;
    destructor dealloc_fun = structure->tp_dealloc;
    freefunc free_fun = structure->tp_free;
    PyTypeObject* type_handle = truffle_assign_managed(structure, ptype);
    // write flags as specified in the dummy to the PythonClass object
    type_handle->tp_flags = original_flags | Py_TPFLAGS_READY;
    type_handle->tp_basicsize = basicsize;
    if (alloc_fun) {
    	type_handle->tp_alloc = alloc_fun;
    }
    if (dealloc_fun) {
    	type_handle->tp_dealloc = dealloc_fun;
    }
    if (free_fun) {
    	type_handle->tp_free = free_fun;
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

#define initialize_type(typeobject, typename, struct)                   \
    ctor(__COUNTER__)                                                   \
    static void init(__COUNTER__, typeobject)(void) {                   \
		initialize_builtin_type(&typeobject,                          \
                                #typename,                            \
                                polyglot_ ## struct ## _typeid());    \
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
declare_type(PyComplex_Type, complex, PyComplexObject);
declare_type(PyModule_Type, module, PyModuleObject);
declare_type(PyCapsule_Type, PyCapsule, PyCapsule);
declare_type(PyMemoryView_Type, memoryview, PyMemoryViewObject);
declare_type(PySet_Type, set, PySetObject);
declare_type(PyFloat_Type, float, PyFloatObject);
declare_type(PySlice_Type, slice, PySliceObject);
declare_type(PyByteArray_Type, bytearray, PyByteArrayObject);
declare_type(PyCFunction_Type, builtin_function_or_method, PyCFunctionObject);
declare_type(PyWrapperDescr_Type, method_descriptor, PyWrapperDescrObject); // LS: previously wrapper_descriptor
// tfel: Both method_descriptor maps to both PyWrapperDescr_Type and
// PyMethodDescr_Type. This reflects our interpreter, but we need to make sure
// that the dynamic type for method_descriptor is always going to be
// PyMethodDescr_Type, so these two declarations cannot be in the wrong order
declare_type(PyMethodDescr_Type, method_descriptor, PyMethodDescrObject);
declare_type(PyGetSetDescr_Type, getset_descriptor, PyGetSetDescrObject);
declare_type(PyMemberDescr_Type, property, PyMemberDescrObject); // LS: previously member_descriptor
declare_type(_PyExc_BaseException, BaseException, PyBaseExceptionObject);
declare_type(PyBuffer_Type, buffer, PyBufferDecorator);
declare_type(PyFunction_Type, function, PyFunctionObject);
declare_type(PyMethod_Type, method, PyMethodObject);
declare_type(PyCode_Type, code, PyCodeObject);
declare_type(PyFrame_Type, frame, PyFrameObject);
declare_type(PyTraceBack_Type, traceback, PyTracebackObject);
declare_type(_PyWeakref_RefType, ReferenceType, PyWeakReference);
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

POLYGLOT_DECLARE_TYPE(PyThreadState);
POLYGLOT_DECLARE_TYPE(newfunc);
POLYGLOT_DECLARE_TYPE(uint32_t);

static void initialize_globals() {
    // register native NULL
    wrapped_null = polyglot_invoke(PY_TRUFFLE_CEXT, polyglot_from_string("PyTruffle_Register_NULL", SRC_CS), NULL);

    // None
    PyObject* jnone = UPCALL_CEXT_O(polyglot_from_string("Py_None", SRC_CS));
    truffle_assign_managed(&_Py_NoneStruct, jnone);

    // NotImplemented
    void *jnotimpl = UPCALL_CEXT_O(polyglot_from_string("Py_NotImplemented", SRC_CS));
    truffle_assign_managed(&_Py_NotImplementedStruct, jnotimpl);

    // Ellipsis
    void *jellipsis = UPCALL_CEXT_O(polyglot_from_string("Py_Ellipsis", SRC_CS));
    truffle_assign_managed(&_Py_EllipsisObject, jellipsis);

    // True, False
    void *jtrue = UPCALL_CEXT_O(polyglot_from_string("Py_True", SRC_CS));
    truffle_assign_managed(&_Py_TrueStruct, jtrue);
    void *jfalse = UPCALL_CEXT_O(polyglot_from_string("Py_False", SRC_CS));
    truffle_assign_managed(&_Py_FalseStruct, jfalse);

    // error marker
    void *jerrormarker = UPCALL_CEXT_PTR(polyglot_from_string("Py_ErrorHandler", SRC_CS));
    truffle_assign_managed(&marker_struct, jerrormarker);

    // long zero, long one
    _PyLong_Zero = (PyObject *)&_Py_FalseStruct;
    _PyLong_One = (PyObject *)&_Py_TrueStruct;
}

static void initialize_bufferprocs() {
    polyglot_invoke(PY_TRUFFLE_CEXT, "PyTruffle_SetBufferProcs", native_to_java((PyObject*)&PyBytes_Type), (getbufferproc)bytes_buffer_getbuffer, (releasebufferproc)NULL);
    polyglot_invoke(PY_TRUFFLE_CEXT, "PyTruffle_SetBufferProcs", native_to_java((PyObject*)&PyByteArray_Type), (getbufferproc)bytearray_getbuffer, (releasebufferproc)NULL);
    polyglot_invoke(PY_TRUFFLE_CEXT, "PyTruffle_SetBufferProcs", native_to_java((PyObject*)&PyBuffer_Type), (getbufferproc)bufferdecorator_getbuffer, (releasebufferproc)NULL);
}

__attribute__((constructor (20000)))
static void initialize_capi() {
    // initialize global variables like '_Py_NoneStruct', etc.
    initialize_globals();
    initialize_exceptions();
    initialize_hashes();
    initialize_bufferprocs();
}

void* native_to_java_exported(PyObject* obj) {
    return native_to_java(obj);
}

void* native_to_java_stealing_exported(PyObject* obj) {
    return native_to_java_stealing(obj);
}

// This function does not guarantee that a pointer object is returned.
void* native_pointer_to_java_exported(void* val) {
	return native_pointer_to_java(val);
}

// Workaround: use 'uint64' to avoid conversion to an LLVM boxed primitive such
// that it is guaranteed to return a pointer object.
void* native_long_to_java(uint64_t val) {
	PyObject* obj = (PyObject*) val;
    if (obj == NULL) {
        return Py_NoValue;
    } else if (obj == Py_None) {
        return Py_None;
    } else if (!truffle_cannot_be_handle(obj)) {
        return resolve_handle(cache, (uint64_t)obj);
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

/** to be used from Java code only; reads native 'ob_type' field */
PyTypeObject* get_ob_type(PyObject* obj) {
    return native_type_to_java(obj->ob_type);
}

/** to be used from Java code only; reads native 'ob_refcnt' field */
Py_ssize_t get_ob_refcnt(PyObject* obj) {
    return obj->ob_refcnt;
}

/** to be used from Java code only; reads native 'tp_dict' field */
PyObject* get_tp_dict(PyTypeObject* obj) {
	return native_to_java(obj->tp_dict);
}

/** to be used from Java code only; reads native 'tp_bases' field */
PyObject* get_tp_bases(PyTypeObject* obj) {
	return native_to_java(obj->tp_bases);
}

/** to be used from Java code only; reads native 'tp_name' field */
PyObject* get_tp_name(PyTypeObject* obj) {
	return polyglot_from_string(obj->tp_name, SRC_CS);
}

/** to be used from Java code only; reads native 'tp_mro' field */
PyObject* get_tp_mro(PyTypeObject* obj) {
	return native_to_java_slim(obj->tp_mro);
}

/** to be used from Java code only; reads native 'tp_subclasses' field */
PyObject* get_tp_subclasses(PyTypeObject* obj) {
	return native_to_java(obj->tp_subclasses);
}

/** to be used from Java code only; reads native 'tp_dictoffset' field */
Py_ssize_t get_tp_dictoffset(PyTypeObject* obj) {
	return obj->tp_dictoffset;
}

/** to be used from Java code only; reads native 'tp_itemsize' field */
Py_ssize_t get_tp_itemsize(PyTypeObject* obj) {
	return obj->tp_itemsize;
}

/** to be used from Java code only; reads native 'tp_basicsize' field */
Py_ssize_t get_tp_basicsize(PyTypeObject* obj) {
	return obj->tp_basicsize;
}

/** to be used from Java code only; reads native 'tp_alloc' field */
allocfunc get_tp_alloc(PyTypeObject* obj) {
	return obj->tp_alloc;
}

/** to be used from Java code only; reads native 'tp_dealloc' field */
destructor get_tp_dealloc(PyTypeObject* obj) {
	return obj->tp_dealloc;
}

/** to be used from Java code only; reads native 'tp_free' field */
freefunc get_tp_free(PyTypeObject* obj) {
	return obj->tp_free;
}

/** to be used from Java code only; reads native 'tp_flags' field */
unsigned long get_tp_flags(PyTypeObject* obj) {
	return obj->tp_flags;
}

/** to be used from Java code only; returns the type ID for a byte array */
polyglot_typeid get_byte_array_typeid(uint64_t len) {
    return polyglot_array_typeid(polyglot_i8_typeid(), len);
}

/** to be used from Java code only; returns the type ID for a uint32_t array */
polyglot_typeid get_uint32_t_array_typeid(uint64_t len) {
    return polyglot_array_typeid(polyglot_uint32_t_typeid(), len);
}

/** to be used from Java code only; returns the type ID for a 'PyObject*' array */
polyglot_typeid get_ptr_array_typeid(uint64_t len) {
    return polyglot_array_typeid(polyglot_PyObjectPtr_typeid(), len);
}

/** to be used from Java code only; returns the type ID PyThreadState */
polyglot_typeid get_thread_state_typeid() {
    return polyglot_PyThreadState_typeid();
}

/** to be used from Java code only; returns the type ID newfunc */
polyglot_typeid get_newfunc_typeid() {
    return polyglot_newfunc_typeid();
}

/** to be used from Java code only; calls INCREF */
void PyTruffle_INCREF(PyObject* obj) {
	Py_INCREF(obj);
}

/** to be used from Java code only; calls DECREF */
void PyTruffle_DECREF(PyObject* obj) {
	Py_DECREF(obj);
}

/** to be used from Java code only; calls DECREF */
Py_ssize_t PyTruffle_ADDREF(PyObject* obj, Py_ssize_t value) {
	return (obj->ob_refcnt += value);
}

/** to be used from Java code only; calls DECREF */
Py_ssize_t PyTruffle_SUBREF(PyObject* obj, Py_ssize_t value) {
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

typedef struct PyObjectHandle {
    PyObject_HEAD
} PyObjectHandle;

uint64_t PyTruffle_Wchar_Size() {
    return SIZEOF_WCHAR_T;
}

/** free's a native pointer or releases a Sulong handle; DO NOT CALL WITH MANAGED POINTERS ! */
void PyTruffle_Free(void* obj) {
	if(!truffle_cannot_be_handle(obj) && truffle_is_handle_to_managed(obj)) {
		truffle_release_handle(obj);
	} else {
		free(obj);
	}
}

/** to be used from Java code only; creates the deref handle for a sequence wrapper */
void* NativeHandle_ForArray(void* jobj, ssize_t element_size) {
    return truffle_deref_handle_for_managed(jobj);
}

const char* PyTruffle_StringToCstr(void* o, int32_t strLen) {
    const char *buffer;
    const char *str;
    uint64_t bufsize = 4 * (strLen + 1) * sizeof(char);
    uint64_t written;

    // we allocate 4 bytes for a char; this will in all cases be enough
    buffer = (const char*) malloc(bufsize);

    written = polyglot_as_string(o, buffer, bufsize, SRC_CS) + 1;

    str = (const char*) malloc(written * sizeof(char));
    memcpy(str, buffer, written * sizeof(char));
    free(buffer);

    return str;
}

void* PyTruffle_CstrToString(void* o) {
    if (polyglot_fits_in_i64(o)) {
        return polyglot_from_string((const char*)polyglot_as_i64(o), SRC_CS);
    }
    return polyglot_from_string(o, SRC_CS);
}

PyObject* PyTruffle_Type_GenericNew(PyTypeObject* cls, PyTypeObject* dominatingNativeClass, PyObject* args, PyObject* kwds) {
    PyObject* newInstance;
    newInstance = dominatingNativeClass->tp_alloc(cls, 0);
    newInstance->ob_refcnt = 0;
    Py_TYPE(newInstance) = cls;
    return newInstance;
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

PRIMITIVE_ARRAY_TO_NATIVE(Byte, int8_t, i8, polyglot_as_i8);
PRIMITIVE_ARRAY_TO_NATIVE(Int, int32_t, i32, polyglot_as_i32);
PRIMITIVE_ARRAY_TO_NATIVE(Long, int64_t, i64, polyglot_as_i64);
PRIMITIVE_ARRAY_TO_NATIVE(Double, double, double, polyglot_as_double);
PRIMITIVE_ARRAY_TO_NATIVE(Object, PyObjectPtr, PyObjectPtr, (PyObjectPtr));

Py_ssize_t PyTruffle_Object_Size(PyObject *op) {
    return ((PyVarObject*)op)->ob_size;
}

#define ReadMember(object, offset, T) ((T*)(((char*)object) + offset))[0]

int ReadShortMember(PyObject* object, Py_ssize_t offset) {
    return ReadMember(object, offset, short);
}

int ReadIntMember(PyObject* object, Py_ssize_t offset) {
    return ReadMember(object, offset, int);
}

long ReadLongMember(PyObject* object, Py_ssize_t offset) {
    return ReadMember(object, offset, long);
}

double ReadFloatMember(PyObject* object, Py_ssize_t offset) {
    return ReadMember(object, offset, float);
}

double ReadDoubleMember(PyObject* object, Py_ssize_t offset) {
    return ReadMember(object, offset, double);
}

PyObject* ReadStringMember(PyObject* object, Py_ssize_t offset) {
    return (PyObject*)polyglot_from_string(ReadMember(object, offset, char*), "utf-8");
}

PyObject* ReadObjectMember(PyObject* object, Py_ssize_t offset) {
    PyObject* member = ReadMember(object, offset, PyObject*);
    if (member == NULL) {
        return Py_None;
    } else {
        return to_java(member);
    }
}

PyObject* ReadCharMember(PyObject* object, Py_ssize_t offset) {
    return polyglot_from_string_n(&ReadMember(object, offset, char), 1, "utf-8");
}

int ReadByteMember(PyObject* object, Py_ssize_t offset) {
    return ReadMember(object, offset, char);
}

int ReadUByteMember(PyObject* object, Py_ssize_t offset) {
    return ReadMember(object, offset, unsigned char);
}

int ReadUShortMember(PyObject* object, Py_ssize_t offset) {
    return ReadMember(object, offset, unsigned short);
}

long ReadUIntMember(PyObject* object, Py_ssize_t offset) {
    return ReadMember(object, offset, unsigned int);
}

PyObject* ReadULongMember(PyObject* object, Py_ssize_t offset) {
    return PyLong_FromUnsignedLong(ReadMember(object, offset, unsigned long));
}

PyObject* ReadBoolMember(PyObject* object, Py_ssize_t offset) {
    char flag = ReadMember(object, offset, char);
    return flag ? Py_True : Py_False;
}

PyObject* ReadObjectExMember(PyObject* object, Py_ssize_t offset) {
    PyObject* member = ReadMember(object, offset, PyObject*);
    if (member == NULL) {
        PyErr_SetString(PyExc_ValueError, "member must not be NULL");
        return NULL;
    } else {
        return to_java(member);
    }
}

PyObject* ReadLongLongMember(PyObject* object, Py_ssize_t offset) {
    return PyLong_FromLongLong(ReadMember(object, offset, long long));
}

PyObject* ReadULongLongMember(PyObject* object, Py_ssize_t offset) {
    return PyLong_FromUnsignedLongLong(ReadMember(object, offset, unsigned long long));
}

PyObject* ReadPySSizeT(PyObject* object, Py_ssize_t offset) {
    return PyLong_FromSsize_t(ReadMember(object, offset, Py_ssize_t));
}

#undef ReadMember

#define WriteMember(object, offset, value, T) *(T*)(((char*)object) + offset) = (T)(value)

PyObject* WriteShortMember(PyObject* object, Py_ssize_t offset, PyObject* value) {
    WriteMember(object, offset, PyLong_AsLong(value), short);
    return value;
}

PyObject* WriteIntMember(PyObject* object, Py_ssize_t offset, PyObject* value) {
    WriteMember(object, offset, PyLong_AsLong(value), int);
    return value;
}

PyObject* WriteLongMember(PyObject* object, Py_ssize_t offset, PyObject* value) {
    WriteMember(object, offset, PyLong_AsLong(value), long);
    return value;
}

PyObject* WriteFloatMember(PyObject* object, Py_ssize_t offset, PyObject* value) {
    WriteMember(object, offset, PyFloat_AsDouble(value), float);
    return value;
}

PyObject* WriteDoubleMember(PyObject* object, Py_ssize_t offset, PyObject* value) {
    WriteMember(object, offset, PyFloat_AsDouble(value), double);
    return value;
}

PyObject* WriteStringMember(PyObject* object, Py_ssize_t offset, PyObject* value) {
    WriteMember(object, offset, as_char_pointer(value), char*);
    return value;
}

PyObject* WriteObjectMember(PyObject* object, Py_ssize_t offset, PyObject* value) {
    WriteMember(object, offset, value, PyObject*);
    return value;
}

PyObject* WriteCharMember(PyObject* object, Py_ssize_t offset, PyObject* value) {
    const char* ptr = as_char_pointer(value);
    const char c = ptr[0];
    free(ptr);
    WriteMember(object, offset, c, char);
    return value;
}

PyObject* WriteByteMember(PyObject* object, Py_ssize_t offset, PyObject* value) {
    WriteMember(object, offset, PyLong_AsLong(value), char);
    return value;
}

PyObject* WriteUByteMember(PyObject* object, Py_ssize_t offset, PyObject* value) {
    WriteMember(object, offset, PyLong_AsLong(value), uint8_t);
    return value;
}

PyObject* WriteUShortMember(PyObject* object, Py_ssize_t offset, PyObject* value) {
    WriteMember(object, offset, PyLong_AsUnsignedLong(value), unsigned short);
    return value;
}

PyObject* WriteUIntMember(PyObject* object, Py_ssize_t offset, PyObject* value) {
    WriteMember(object, offset, PyLong_AsUnsignedLong(value), unsigned int);
    return value;
}

PyObject* WriteULongMember(PyObject* object, Py_ssize_t offset, PyObject* value) {
    WriteMember(object, offset, PyLong_AsUnsignedLong(value), unsigned long);
    return value;
}

PyObject* WriteBoolMember(PyObject* object, Py_ssize_t offset, PyObject* value) {
    WriteMember(object, offset, UPCALL_O(native_to_java(value), polyglot_from_string("__bool__", SRC_CS)) == Py_True ? (char)1 : (char)0, char);
    return value;
}

PyObject* WriteObjectExMember(PyObject* object, Py_ssize_t offset, PyObject* value) {
    if (value == NULL) {
        PyErr_SetString(PyExc_ValueError, "member must not be NULL");
        return NULL;
    } else {
        WriteMember(object, offset, value, PyObject*);
        return value;
    }
}

PyObject* WriteLongLongMember(PyObject* object, Py_ssize_t offset, PyObject* value) {
    WriteMember(object, offset, value, long long);
    return value;
}

PyObject* WriteULongLongMember(PyObject* object, Py_ssize_t offset, PyObject* value) {
    WriteMember(object, offset, value, unsigned long long);
    return value;
}

PyObject* WritePySSizeT(PyObject* object, Py_ssize_t offset, PyObject* value) {
    WriteMember(object, offset, value, Py_ssize_t);
    return value;
}

PyObject* wrapped_null;

PyObject marker_struct = {
    _PyObject_EXTRA_INIT
    1, &PyBaseObject_Type
};

#undef WriteMember

int PyTruffle_Debug(void *arg) {
    polyglot_invoke(PY_TRUFFLE_CEXT, "PyTruffle_Debug", arg);
    return 0;
}

#define ARG(__n) ((PyObject*)polyglot_get_arg((__n)))

int truffle_ptr_compare(void* x, void* y, int op) {
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

double truffle_read_ob_fval(PyFloatObject* fobj) {
	return fobj->ob_fval;
}

/* called from Java to get number of bits per long digit */
int32_t get_long_bits_in_digit() {
	return PYLONG_BITS_IN_DIGIT;
}
