/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
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

#include <polyglot.h>

#include "Python.h"
#include <truffle.h>

#define SRC_CS "utf-8"

/* Flags definitions representing global (debug) options. */
#define PY_TRUFFLE_TRACE_MEM 0x1


/* Private types are defined here because we need to declare the type cast. */
typedef struct {
    PyObject_HEAD
    PyObject *md_dict;
    struct PyModuleDef *md_def;
    void *md_state;
    PyObject *md_weaklist;
    PyObject *md_name;  /* for logging purposes after md_dict is cleared */
} PyModuleObject;

// taken from CPython "Objects/capsule.c"
typedef struct {
    PyObject_HEAD
    void *pointer;
    const char *name;
    void *context;
    PyCapsule_Destructor destructor;
} PyCapsule;

typedef struct {
    PyObject_VAR_HEAD
    int readonly;
    void *buf_delegate;
} PyBufferDecorator;

PyAPI_DATA(PyTypeObject) PyBuffer_Type;
PyAPI_DATA(PyTypeObject) _PyExc_BaseException;

typedef void (*init_upcall)();

extern void *PY_TRUFFLE_CEXT;
extern void *PY_BUILTIN;
extern void *Py_NoValue;
extern init_upcall upcalls[];
extern unsigned init_upcall_n;
extern uint32_t Py_Truffle_Options;

/* upcall helpers */
MUST_INLINE
PyObject* polyglot_ensure_ptr(void *obj) {
	return polyglot_fits_in_i64(obj) ? (PyObject*) polyglot_as_i64(obj) : (PyObject*) obj;
}

MUST_INLINE
int32_t polyglot_ensure_i32(void *obj) {
	return polyglot_fits_in_i32(obj) ? polyglot_as_i32(obj) : (int32_t) obj;
}

MUST_INLINE
int64_t polyglot_ensure_i64(void *obj) {
	return polyglot_fits_in_i64(obj) ? polyglot_as_i64(obj) : (int64_t) obj;
}

MUST_INLINE
double polyglot_ensure_double(void *obj) {
	return polyglot_fits_in_double(obj) ? polyglot_as_double(obj) : (double) ((int64_t)obj);
}

MUST_INLINE
int PyTruffle_Trace_Memory() {
	return Py_Truffle_Options & PY_TRUFFLE_TRACE_MEM;
}

/* upcall functions for calling into Python */
void*(*pytruffle_decorate_function)(void *fun0, void* fun1);
extern PyObject*(*PY_TRUFFLE_LANDING)(void *rcv, void* name, ...);
extern void*(*PY_TRUFFLE_LANDING_L)(void *rcv, void* name, ...);
extern void*(*PY_TRUFFLE_LANDING_D)(void *rcv, void* name, ...);
extern void*(*PY_TRUFFLE_LANDING_PTR)(void *rcv, void* name, ...);
extern PyObject*(*PY_TRUFFLE_CEXT_LANDING)(void* name, ...);
extern void* (*PY_TRUFFLE_CEXT_LANDING_L)(void* name, ...);
extern void* (*PY_TRUFFLE_CEXT_LANDING_D)(void* name, ...);
extern void* (*PY_TRUFFLE_CEXT_LANDING_PTR)(void* name, ...);

/* Call function with return type 'PyObject *'; does polyglot cast and error handling */
#define UPCALL_O(__recv__, __name__, ...) PY_TRUFFLE_LANDING((__recv__), __name__, ##__VA_ARGS__)

/* Call function with a primitive return; no polyglot cast but error handling */
#define UPCALL_P(__recv__, __name__, ...) (PY_TRUFFLE_LANDING_L((__recv__), __name__, ##__VA_ARGS__))

/* Call function with return type 'int'; no polyglot cast but error handling */
#define UPCALL_I(__recv__, __name__, ...) (polyglot_ensure_i32(UPCALL_P(__recv__, __name__, ##__VA_ARGS__)))

/* Call function with return type 'long'; no polyglot cast but error handling */
#define UPCALL_L(__recv__, __name__, ...) (polyglot_ensure_i64(UPCALL_P(__recv__, __name__, ##__VA_ARGS__)))

/* Call function with return type 'double'; no polyglot cast but error handling */
#define UPCALL_D(__recv__, __name__, ...) (polyglot_ensure_double(PY_TRUFFLE_LANDING_D((__recv__), __name__, ##__VA_ARGS__)))

/* Call function with return type 'void*'; no polyglot cast and no error handling */
#define UPCALL_PTR(__recv__, __name__, ...) (polyglot_ensure_ptr(PY_TRUFFLE_LANDING_PTR((__recv__), __name__, ##__VA_ARGS__)))

/* Call function of 'python_cext' module with return type 'PyObject *'; does polyglot cast and error handling */
#define UPCALL_CEXT_O(__name__, ...) PY_TRUFFLE_CEXT_LANDING(__name__, ##__VA_ARGS__)

/* Call void function of 'python_cext' module; no polyglot cast and no error handling */
#define UPCALL_CEXT_VOID(__name__, ...) ((void)PY_TRUFFLE_CEXT_LANDING(__name__, ##__VA_ARGS__))

/* Call function of 'python_cext' module with return type 'PyObject*'; no polyglot cast but error handling */
#define UPCALL_CEXT_NOCAST(__name__, ...) PY_TRUFFLE_CEXT_LANDING(__name__, ##__VA_ARGS__)

/* Call function of 'python_cext' module with return type 'void*'; no polyglot cast and no error handling */
#define UPCALL_CEXT_PTR(__name__, ...) (polyglot_ensure_ptr(PY_TRUFFLE_CEXT_LANDING_PTR(__name__, ##__VA_ARGS__)))

/* Call function of 'python_cext' module with a primitive return; no polyglot cast but error handling */
#define UPCALL_CEXT_P(__name__, ...) (PY_TRUFFLE_CEXT_LANDING_L(__name__, ##__VA_ARGS__))

/* Call function of 'python_cext' module with return type 'int'; no polyglot cast but error handling */
#define UPCALL_CEXT_I(__name__, ...) (polyglot_ensure_i32(UPCALL_CEXT_P(__name__, ##__VA_ARGS__)))

/* Call function of 'python_cext' module with return type 'long'; no polyglot cast but error handling */
#define UPCALL_CEXT_L(__name__, ...) (polyglot_ensure_i64(UPCALL_CEXT_P(__name__, ##__VA_ARGS__)))

/* Call function of 'python_cext' module with return type 'double'; no polyglot cast but error handling */
#define UPCALL_CEXT_D(__name__, ...) (polyglot_ensure_double(PY_TRUFFLE_CEXT_LANDING_D(__name__, ##__VA_ARGS__)))

#define UPCALL_ID(name)                                                 \
    static void* _jls_ ## name;                                         \
    __attribute__((constructor))                                        \
    static void init_upcall_ ## name(void) {                               \
       _jls_ ## name = polyglot_get_member(PY_TRUFFLE_CEXT, polyglot_from_string(#name, SRC_CS)); \
    }

#define UPCALL_TYPED_ID(name, fun_t)                                                                     \
    static fun_t _jls_ ## name;                                                                          \
    __attribute__((constructor))                                                                         \
    static void init_upcall_ ## name(void) {                                                             \
       _jls_ ## name = (fun_t)polyglot_get_member(PY_TRUFFLE_CEXT, polyglot_from_string(#name, SRC_CS)); \
    }

#define as_char_pointer(obj) ((const char*)UPCALL_CEXT_PTR(polyglot_from_string("to_char_pointer", "ascii"), native_to_java(obj)))
#define as_long(obj) ((long)polyglot_as_i64(polyglot_invoke(PY_TRUFFLE_CEXT, "to_long", to_java(obj))))
#define as_long_long(obj) ((long long)polyglot_as_i64(polyglot_invoke(PY_TRUFFLE_CEXT, "PyLong_AsPrimitive", to_java(obj), 1, sizeof(long long))))
#define as_unsigned_long_long(obj) ((unsigned long long)polyglot_as_i64(polyglot_invoke(PY_TRUFFLE_CEXT, "PyLong_AsPrimitive", to_java(obj), 0, sizeof(unsigned long long))))
#define as_int(obj) ((int)as_long(obj))
#define as_short(obj) ((short)as_long(obj))
#define as_uchar(obj) ((unsigned char)as_long(obj))
#define as_char(obj) ((char)as_long(obj))
#define as_double(obj) polyglot_as_double(polyglot_invoke(PY_TRUFFLE_CEXT, "to_double", to_java(obj)))
#define as_float(obj) ((float)as_double(obj))

typedef int (*alloc_reporter_fun_t)(int64_t, Py_ssize_t size);
extern alloc_reporter_fun_t PyObject_AllocationReporter;

typedef void* (*cache_t)(uint64_t);
extern cache_t cache;

// Heuristic to test if some value is a pointer object
// TODO we need a reliable solution for that
#define IS_POINTER(__val__) (polyglot_is_value(__val__) && !polyglot_fits_in_i64(__val__))

#define resolve_handle(__cache__, __addr__) (__cache__)(__addr__)

void initialize_type_structure(PyTypeObject* structure, PyTypeObject* ptype, polyglot_typeid tid);
Py_ssize_t PyTruffle_Type_AddSlots(PyTypeObject* cls, PyObject* slotsTuple);


MUST_INLINE
void* native_to_java(PyObject* obj) {
    if (obj == NULL) {
        return Py_NoValue;
    } else if (obj == Py_None) {
        return Py_None;
    } else if (polyglot_is_string(obj)) {
        return obj;
    } else if (!truffle_cannot_be_handle(obj)) {
        return resolve_handle(cache, (uint64_t)obj);
    }
    return obj;
}


extern void* native_to_java_exported(PyObject* obj);

MUST_INLINE
void* native_to_java_slim(PyObject* obj) {
    if (!truffle_cannot_be_handle(obj)) {
        return truffle_managed_from_handle(obj);
    }
    return obj;
}

MUST_INLINE
PyTypeObject* native_type_to_java(PyTypeObject* type) {
	if (!truffle_cannot_be_handle(type)) {
        return (PyTypeObject *)truffle_managed_from_handle(type);
    }
    return type;
}

/* This is our version of 'PyObject_Free' which is also able to free Sulong handles. */
MUST_INLINE
void PyTruffle_Object_Free(void* ptr) {
	if((!truffle_cannot_be_handle(ptr) && truffle_is_handle_to_managed(ptr)) || polyglot_is_value(ptr)) {
		(void) polyglot_invoke(PY_TRUFFLE_CEXT, "PyTruffle_Object_Free", native_to_java(ptr));
	} else {
	    if(PyTruffle_Trace_Memory()) {
	        (void) polyglot_invoke(PY_TRUFFLE_CEXT, "PyTruffle_Trace_Free", (int64_t)ptr);
	    }
		free(ptr);
	}
}

MUST_INLINE int PyTruffle_Report_Allocation(PyObject* obj, Py_ssize_t size) {
    return PyObject_AllocationReporter(obj, size);
}

extern void* to_java(PyObject* obj);
extern void* to_java_type(PyTypeObject* cls);
extern PyObject* to_sulong(void *o);

// defined in 'exceptions.c'
void initialize_exceptions();
// defined in 'pyhash.c'
void initialize_hashes();

#define JWRAPPER_DIRECT                      (polyglot_invoke(PY_TRUFFLE_CEXT, "METH_DIRECT"))
#define JWRAPPER_FASTCALL                    (polyglot_invoke(PY_TRUFFLE_CEXT, "METH_FASTCALL"))
#define JWRAPPER_FASTCALL_WITH_KEYWORDS      (polyglot_invoke(PY_TRUFFLE_CEXT, "METH_FASTCALL_WITH_KEYWORDS"))
#define JWRAPPER_KEYWORDS                    (polyglot_invoke(PY_TRUFFLE_CEXT, "METH_KEYWORDS"))
#define JWRAPPER_VARARGS                     (polyglot_invoke(PY_TRUFFLE_CEXT, "METH_VARARGS"))
#define JWRAPPER_NOARGS                      (polyglot_invoke(PY_TRUFFLE_CEXT, "METH_NOARGS"))
#define JWRAPPER_O                           (polyglot_invoke(PY_TRUFFLE_CEXT, "METH_O"))
#define JWRAPPER_UNSUPPORTED                 (polyglot_invoke(PY_TRUFFLE_CEXT, "METH_UNSUPPORTED"))
#define JWRAPPER_ALLOC                       (polyglot_invoke(PY_TRUFFLE_CEXT, "METH_ALLOC"))
#define JWRAPPER_SSIZE_ARG                   JWRAPPER_ALLOC
#define JWRAPPER_GETATTR                     (polyglot_invoke(PY_TRUFFLE_CEXT, "METH_GETATTR"))
#define JWRAPPER_SETATTR                     (polyglot_invoke(PY_TRUFFLE_CEXT, "METH_SETATTR"))
#define JWRAPPER_RICHCMP                     (polyglot_invoke(PY_TRUFFLE_CEXT, "METH_RICHCMP"))
#define JWRAPPER_SSIZE_OBJ_ARG               (polyglot_invoke(PY_TRUFFLE_CEXT, "METH_SSIZE_OBJ_ARG"))
#define JWRAPPER_REVERSE                     (polyglot_invoke(PY_TRUFFLE_CEXT, "METH_REVERSE"))
#define JWRAPPER_POW                         (polyglot_invoke(PY_TRUFFLE_CEXT, "METH_POW"))
#define JWRAPPER_REVERSE_POW                 (polyglot_invoke(PY_TRUFFLE_CEXT, "METH_REVERSE_POW"))
#define JWRAPPER_LT                          (polyglot_invoke(PY_TRUFFLE_CEXT, "METH_LT"))
#define JWRAPPER_LE                          (polyglot_invoke(PY_TRUFFLE_CEXT, "METH_LE"))
#define JWRAPPER_EQ                          (polyglot_invoke(PY_TRUFFLE_CEXT, "METH_EQ"))
#define JWRAPPER_NE                          (polyglot_invoke(PY_TRUFFLE_CEXT, "METH_NE"))
#define JWRAPPER_GT                          (polyglot_invoke(PY_TRUFFLE_CEXT, "METH_GT"))
#define JWRAPPER_GE                          (polyglot_invoke(PY_TRUFFLE_CEXT, "METH_GE"))

#define TDEBUG __builtin_debugtrap()
#define get_method_flags_wrapper(flags)                                                  \
    (((flags) < 0) ?                                                                     \
     JWRAPPER_DIRECT :                                                                   \
     ((((flags) & (METH_FASTCALL | METH_KEYWORDS)) == (METH_FASTCALL | METH_KEYWORDS)) ? \
      JWRAPPER_FASTCALL_WITH_KEYWORDS :                                                  \
     (((flags) & METH_FASTCALL) ?                                                        \
      JWRAPPER_FASTCALL :                                                                \
      (((flags) & METH_KEYWORDS) ?                                                       \
       JWRAPPER_KEYWORDS :                                                               \
       (((flags) & METH_VARARGS) ?                                                       \
        JWRAPPER_VARARGS :                                                               \
        (((flags) & METH_NOARGS) ?                                                       \
         JWRAPPER_NOARGS :                                                               \
         (((flags) & METH_O) ?                                                           \
          JWRAPPER_O :                                                                   \
          JWRAPPER_UNSUPPORTED)))))))

#define PY_TRUFFLE_TYPE_WITH_ALLOC(__TYPE_NAME__, __SUPER_TYPE__, __FLAGS__, __SIZE__, __ALLOC__, __DEALLOC__, __FREE__) {\
    PyVarObject_HEAD_INIT((__SUPER_TYPE__), 0)\
    __TYPE_NAME__,                              /* tp_name */\
    (__SIZE__),                                 /* tp_basicsize */\
    0,                                          /* tp_itemsize */\
    (__DEALLOC__),                              /* tp_dealloc */\
    0,                                          /* tp_vectorcall_offset */\
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

#define PY_TRUFFLE_TYPE(__TYPE_NAME__, __SUPER_TYPE__, __FLAGS__, __SIZE__) PY_TRUFFLE_TYPE_WITH_ALLOC(__TYPE_NAME__, __SUPER_TYPE__, __FLAGS__, __SIZE__, 0, 0, 0)

/** to be used from Java code only; returns a type's basic size */
#define BASICSIZE_GETTER(__typename__)extern Py_ssize_t get_ ## __typename__ ## _basicsize() { \
	return sizeof(__typename__); \
} \


int PyTruffle_Debug(void *arg);

extern PyObject marker_struct;
extern PyObject* wrapped_null;

/* An error marker object.
 * The object should not be converted to_java and is intended to be returned in the error case.
 * That's mainly useful for direct calls (without landing functions) of 'python_cext' functions. */
#define ERROR_MARKER wrapped_null

/* internal functions to avoid unnecessary managed <-> native conversions */

/* STR */
__attribute__((always_inline)) PyObject* PyTruffle_Unicode_FromFormat(const char *fmt, va_list va, void **args, int argc);
__attribute__((always_inline)) PyObject* PyTruffle_Tuple_Pack(int dummy, va_list va, void **args, int argc);

/* BYTES, BYTEARRAY */
int bytes_buffer_getbuffer(PyBytesObject *self, Py_buffer *view, int flags);
int bytearray_getbuffer(PyByteArrayObject *obj, Py_buffer *view, int flags);

/* Like 'memcpy' but can read/write from/to managed objects. */
int bytes_copy2mem(char* target, char* source, size_t nbytes);

/* MEMORYVIEW, BUFFERDECORATOR */
int bufferdecorator_getbuffer(PyBufferDecorator *self, Py_buffer *view, int flags);

#if 1
/*
 * (tfel): On native Sulong, using va_list will force all arguments to native
 * memory, which hinders escape analysis and PE in a big way. To avoid this,
 * when we have function called with var args (rather than already with a
 * va_list), we allocate a managed array of void*, fill it with the arguments,
 * and pass that one on. In the target functions, we use the macros below to
 * access the variable arguments part depending on whether it is a va_list or a
 * managed void* array. The assumption is that once everything is compiled
 * together, the managed array with arguments will be escape analyzed away.
 */
#define CallWithPolyglotArgs(result, last, off, function, ...)          \
    va_list __va_list;                                                  \
    int __poly_argc = polyglot_get_arg_count();                         \
    int __poly_args_s = sizeof(void*) * (__poly_argc - off);            \
    void **__poly_args = truffle_managed_malloc(__poly_args_s);         \
    for (int i = off; i < __poly_argc; i++) {                           \
        __poly_args[i - off] = polyglot_get_arg(i);                     \
    }                                                                   \
    result = function(__VA_ARGS__, __va_list, __poly_args, __poly_argc)
#else
/*
 * (tfel): Just skip the optimization with using a managed malloc and use
 * va_list always.
 */
#define CallWithPolyglotArgs(result, last, off, function, ...)          \
    va_list __poly_args;                                                \
    va_start(__poly_args, last);                                        \
    result = function(__VA_ARGS__, __poly_args, NULL, 0);               \
    va_end(__poly_args)
#endif

typedef PyObject* PyObjectPtr;
POLYGLOT_DECLARE_TYPE(PyObjectPtr);

typedef int (*setitem_fun_t)(PyObject*, Py_ssize_t, PyObject*);

#endif
