/*
 * Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
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
#include <trufflenfi.h>

#define ASSERTIONS

#ifdef GRAALVM_PYTHON_LLVM_MANAGED
int points_to_py_handle_space(void* ptr) {
   return polyglot_is_value(ptr);
}
#endif

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

static void capsule_dealloc(PyObject *o) {
    PyCapsule *capsule = (PyCapsule *)o;
    if (capsule->destructor) {
        capsule->destructor(o);
    }
    PyObject_Free(o);
}

// taken from CPython "Objects/bytesobject.c"
#define PyBytesObject_SIZE (offsetof(PyBytesObject, ob_sval) + 1)

/* prototype */
PyObject* PyTruffle_Tuple_Alloc(PyTypeObject* cls, Py_ssize_t nitems);
void PyTruffle_Tuple_Dealloc(PyTupleObject* tuple);

PyAPI_DATA(PyTypeObject) _PyExc_BaseException;
PyAPI_DATA(PyTypeObject) _PyExc_StopIteration;
PyAPI_DATA(PyTypeObject) mmap_object_type;

// used for sizeof(...)
typedef struct {
    PyObject_HEAD
    PyObject *iters;
    PyObject *func;
} mapobject;

typedef struct {
    PyObject_HEAD
    PyObject *start;
    PyObject *stop;
    PyObject *step;
    PyObject *length;
} rangeobject;

typedef struct {
    PyObject_HEAD
    Py_ssize_t tuplesize;
    PyObject *ittuple;     /* tuple of iterators */
    PyObject *result;
    int strict;
} zipobject;

#define PY_TRUFFLE_TYPE_GENERIC(GLOBAL_NAME, __TYPE_NAME__, __SUPER_TYPE__, __SIZE__, __ITEMSIZE__, __ALLOC__, __DEALLOC__, __FREE__, __VCALL_OFFSET__) \
PyTypeObject GLOBAL_NAME = {\
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
    0,                                          /* tp_flags */\
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
};

#define PY_TRUFFLE_TYPE_UNIMPLEMENTED(GLOBAL_NAME) PyTypeObject GLOBAL_NAME;

PY_TYPE_OBJECTS

#undef PY_TRUFFLE_TYPE_GENERIC
#undef PY_TRUFFLE_TYPE_UNIMPLEMENTED

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

#undef bool
static void initialize_builtin_types_and_structs() {
	clock_t t = clock();
    PyTruffle_Log(PY_TRUFFLE_LOG_FINE, "initialize_builtin_types_and_structs...");
	static int64_t builtin_types[] = {
#define PY_TRUFFLE_TYPE_GENERIC(GLOBAL_NAME, __TYPE_NAME__, a, b, c, d, e, f, g) &GLOBAL_NAME, __TYPE_NAME__,
#define PY_TRUFFLE_TYPE_UNIMPLEMENTED(GLOBAL_NAME) // empty
    PY_TYPE_OBJECTS
#undef PY_TRUFFLE_TYPE_GENERIC
#undef PY_TRUFFLE_TYPE_UNIMPLEMENTED
        NULL, NULL
	};

	GraalPyTruffle_InitBuiltinTypesAndStructs(builtin_types);

	// fix up for circular dependency:
	PyType_Type.tp_base = &PyBaseObject_Type;

	PyTruffle_Log(PY_TRUFFLE_LOG_FINE, "initialize_builtin_types_and_structs: %fs", ((double) (clock() - t)) / CLOCKS_PER_SEC);
 }

int mmap_getbuffer(PyObject *self, Py_buffer *view, int flags) {
	// TODO(fa) readonly flag
    return PyBuffer_FillInfo(view, (PyObject*)self, GraalPyTruffle_GetMMapData(self), PyObject_Size((PyObject *)self), 0, flags);
}

PyAPI_FUNC(void) mmap_init_bufferprotocol(PyObject* mmap_type) {
	PyTruffle_Log(PY_TRUFFLE_LOG_FINE, "mmap_init_bufferprotocol");
	assert(PyType_Check(mmap_type));

	static PyBufferProcs mmap_as_buffer = {
	    (getbufferproc)mmap_getbuffer,
	    (releasebufferproc)NULL,
	};
	set_PyTypeObject_tp_as_buffer(mmap_type, &mmap_as_buffer);
	((PyTypeObject*) mmap_type)->tp_as_buffer = &mmap_as_buffer;
}

struct _longobject* _Py_FalseStructReference;
struct _longobject* _Py_TrueStructReference;
PyObject* _Py_EllipsisObjectReference;
PyObject* _Py_NoneStructReference;
PyObject* _Py_NotImplementedStructReference;

PyObject* _PyTruffle_Zero;
PyObject* _PyTruffle_One;

#ifndef GRAALVM_PYTHON_LLVM_MANAGED
THREAD_LOCAL PyThreadState *tstate_current = NULL;
#endif /* GRAALVM_PYTHON_LLVM_MANAGED */

static void initialize_globals() {
#ifndef GRAALVM_PYTHON_LLVM_MANAGED
    // store the thread state into a thread local variable
    tstate_current = GraalPyThreadState_Get();
#endif /* GRAALVM_PYTHON_LLVM_MANAGED */
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
int memory_getbuf(PyMemoryViewObject *self, Py_buffer *view, int flags);
void memory_releasebuf(PyMemoryViewObject *self, Py_buffer *view);

static void initialize_bufferprocs() {
    static PyBufferProcs bytes_as_buffer = {
        (getbufferproc)bytes_buffer_getbuffer,       /* bf_getbuffer */
        (releasebufferproc)NULL,                     /* bf_releasebuffer */
    };
    PyBytes_Type.tp_as_buffer = &bytes_as_buffer;
    GraalPy_set_PyTypeObject_tp_as_buffer(&PyBytes_Type, &bytes_as_buffer);

    static PyBufferProcs bytearray_as_buffer = {
        (getbufferproc)bytearray_getbuffer,          /* bf_getbuffer */
        (releasebufferproc)bytearray_releasebuffer,  /* bf_releasebuffer */
    };
    PyByteArray_Type.tp_as_buffer = &bytearray_as_buffer;
    GraalPy_set_PyTypeObject_tp_as_buffer(&PyByteArray_Type, &bytearray_as_buffer);

    static PyBufferProcs memory_as_buffer = {
        (getbufferproc)memory_getbuf,         /* bf_getbuffer */
        (releasebufferproc)memory_releasebuf, /* bf_releasebuffer */
    };
    PyMemoryView_Type.tp_as_buffer = &memory_as_buffer;
    GraalPy_set_PyTypeObject_tp_as_buffer(&PyMemoryView_Type, &memory_as_buffer);

    static PyBufferProcs array_as_buffer;
    array_as_buffer.bf_getbuffer = GraalPyTruffle_Array_getbuffer,
    array_as_buffer.bf_releasebuffer = GraalPyTruffle_Array_releasebuffer,
    Arraytype.tp_as_buffer = &array_as_buffer;
    GraalPy_set_PyTypeObject_tp_as_buffer(&Arraytype, &array_as_buffer);
}

int is_builtin_type(PyTypeObject *tp) {
#define PY_TRUFFLE_TYPE_GENERIC(GLOBAL_NAME, __TYPE_NAME__, a, b, c, d, e, f, g) (tp == &GLOBAL_NAME) ||
#define PY_TRUFFLE_TYPE_UNIMPLEMENTED(GLOBAL_NAME) // empty
    return PY_TYPE_OBJECTS 0;
#undef PY_TRUFFLE_TYPE_GENERIC
#undef PY_TRUFFLE_TYPE_UNIMPLEMENTED
}

PyAPI_FUNC(int64_t) get_methods_flags(PyTypeObject *cls) {
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

    PyAsyncMethods *async = cls->tp_as_async;
    if (async != NULL) {
#define COMPUTE_FLAGS(NAME, BIT_IDX) flags |= ((async->NAME != NULL) * BIT_IDX);
		COMPUTE_FLAGS(am_await, AM_AWAIT)
		COMPUTE_FLAGS(am_aiter, AM_AITER)
		COMPUTE_FLAGS(am_anext, AM_ANEXT)
		COMPUTE_FLAGS(am_send, AM_SEND)
#undef COMPUTE_FLAGS
    }
    return flags;
}

// not quite as in CPython, this assumes that x is already a double. The rest of
// the implementation is in the Float constructor in Java
PyAPI_FUNC(PyObject*) float_subtype_new(PyTypeObject *type, double x) {
    PyObject* newobj = type->tp_alloc(type, 0);
    if (newobj == NULL) {
        Py_DECREF(newobj);
        return NULL;
    }
    ((PyFloatObject *)newobj)->ob_fval = x;
    return newobj;
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
		sprintf(buf, "suspicious refcnt value during managed adjustment for %p (%zd 0x%zx + %zd)\n", obj, obj->ob_refcnt, obj->ob_refcnt, value);
		Py_FatalError(buf);
	}
	if ((obj->ob_refcnt + value) <= 0) {
		char buf[1024];
		sprintf(buf, "refcnt reached zero during managed adjustment for %p (%zd 0x%zx + %zd)\n", obj, obj->ob_refcnt, obj->ob_refcnt, value);
		Py_FatalError(buf);
	}
//	printf("refcnt value during managed adjustment for %p (%zd 0x%zx + %zd)\n", obj, obj->ob_refcnt, obj->ob_refcnt, value);
#endif // ASSERTIONS

	return (obj->ob_refcnt += value);
}

/** to be used from Java code only; calls DECREF */
PyAPI_FUNC(Py_ssize_t) PyTruffle_SUBREF(intptr_t ptr, Py_ssize_t value) {
	PyObject* obj = (PyObject*) ptr; // avoid type attachment at the interop boundary
#ifdef ASSERTIONS
	if (obj->ob_refcnt & 0xFFFFFFFF00000000L) {
		char buf[1024];
		sprintf(buf, "suspicious refcnt value during managed adjustment for %p (%zd 0x%zx - %zd)\n", obj, obj->ob_refcnt, obj->ob_refcnt, value);
		Py_FatalError(buf);
	}
	if ((obj->ob_refcnt - value) < 0) {
		char buf[1024];
		sprintf(buf, "refcnt below zero during managed adjustment for %p (%zd 0x%zx - %zd)\n", obj, obj->ob_refcnt, obj->ob_refcnt, value);
		Py_FatalError(buf);
	}
//	printf("refcnt value during managed adjustment for %p (%zd 0x%zx - %zd)\n", obj, obj->ob_refcnt, obj->ob_refcnt, value);
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

/** to be used from Java code only and only at exit; calls _Py_Dealloc */
PyAPI_FUNC(Py_ssize_t) PyTruffle_shutdown_bulk_DEALLOC(intptr_t ptrArray[], int64_t len) {
    /* some objects depends on others which might get deallocated in the process
        of an earlier deallocation of the other object. To avoid double deallocations,
        we, temporarly, make all objects immortal artificially */
	for (int i = 0; i < len; i++) {
    	PyObject *obj = (PyObject*) ptrArray[i];
        obj->ob_refcnt = 999999999; // object.h:_Py_IMMORTAL_REFCNT
    }
	for (int i = 0; i < len; i++) {
    	PyObject *obj = (PyObject*) ptrArray[i];
        if (Py_TYPE(obj)->tp_dealloc != object_dealloc) {
            /* we don't need to care about objects with default deallocation process */
            obj->ob_refcnt = 0;
            _Py_Dealloc(obj);
        }
    }
    return 0;
}

/** free's a native pointer or releases a Sulong handle; DO NOT CALL WITH MANAGED POINTERS ! */
PyAPI_FUNC(void) PyTruffle_Free(intptr_t val) {
    PyMem_RawFree((void*) val);
}

/* To be used from Java code only.
 * This function is used if a native class inherits from a managed class but uses the 'object.__new__'.
 * This function roughly corresponds to CPython's 'object_new'. */
PyAPI_FUNC(PyObject*) PyTruffle_Object_New(PyTypeObject* cls) {
    return cls->tp_alloc(cls, 0);
}

PyAPI_FUNC(void*) PyTruffle_Add_Offset(void* value, long offset) {
	return ((char*) value) + offset;
}

PyAPI_FUNC(void) PyTruffle_ObjectArrayRelease(PyObject** array, int32_t size) {
    for (int i = 0; i < size; i++) {
        /* This needs to use 'Py_XDECREF' because we use this function to
           deallocate storages of tuples, lists, ..., where this is done in the
           appropriate 'tp_traverse' function which uses 'Py_VISIT' and this
           allows an element to be 'NULL'. Elements may, in particular, be
           'NULL' if a tuple dies before all elements are initialized. */
        Py_XDECREF(array[i]);
    }
}

PyAPI_FUNC(void) PyTruffle_SetStorageItem(PyObject** ptr, int32_t index, PyObject* newitem) {
    Py_XSETREF(ptr[index], newitem);
}

PyAPI_FUNC(void) PyTruffle_InitializeStorageItem(PyObject** ptr, int32_t index, PyObject* newitem) {
    ptr[index] = newitem;
}

#define ReadMember(object, offset, T) ((T*)(((char*)object) + offset))[0]

PyAPI_FUNC(int) ReadShortMember(void* object, Py_ssize_t offset) {
    return ReadMember(object, offset, short);
}

PyAPI_FUNC(int) ReadIntMember(void* object, Py_ssize_t offset) {
    return ReadMember(object, offset, int);
}

PyAPI_FUNC(long) ReadLongMember(void* object, Py_ssize_t offset) {
    return ReadMember(object, offset, long);
}

PyAPI_FUNC(double) ReadFloatMember(void* object, Py_ssize_t offset) {
    return ReadMember(object, offset, float);
}

PyAPI_FUNC(double) ReadDoubleMember(void* object, Py_ssize_t offset) {
    return ReadMember(object, offset, double);
}

PyAPI_FUNC(void*) ReadStringMember(void* object, Py_ssize_t offset) {
    char *ptr = ReadMember(object, offset, char*);
    if (ptr != NULL) {
    	return ReadMember(object, offset, char*);
    }
    return NULL;
}

PyAPI_FUNC(void*) ReadStringInPlaceMember(void* object, Py_ssize_t offset) {
    char *addr = (char*) (((char*)object) + offset);
    return addr;
}

PyAPI_FUNC(void*) ReadPointerMember(void* object, Py_ssize_t offset) {
    return ReadMember(object, offset, void*);
}

PyAPI_FUNC(PyObject*) ReadObjectMember(void* object, Py_ssize_t offset) {
    PyObject* member = ReadMember(object, offset, PyObject*);
    if (member == NULL) {
        member = Py_None;
    }
    Py_INCREF(member);
    return member;
}

PyAPI_FUNC(int) ReadCharMember(void* object, Py_ssize_t offset) {
    return ReadMember(object, offset, char);
}

PyAPI_FUNC(int) ReadUByteMember(void* object, Py_ssize_t offset) {
    return ReadMember(object, offset, unsigned char);
}

PyAPI_FUNC(int) ReadUShortMember(void* object, Py_ssize_t offset) {
    return ReadMember(object, offset, unsigned short);
}

PyAPI_FUNC(long) ReadUIntMember(void* object, Py_ssize_t offset) {
    return ReadMember(object, offset, unsigned int);
}

PyAPI_FUNC(unsigned long) ReadULongMember(void* object, Py_ssize_t offset) {
    return ReadMember(object, offset, unsigned long);
}

PyAPI_FUNC(PyObject*) ReadObjectExMember(void* object, Py_ssize_t offset) {
    PyObject* member = ReadMember(object, offset, PyObject*);
    if (member == NULL) {
        return NULL;
    } else {
        Py_INCREF(member);
        return member;
    }
}

PyAPI_FUNC(long long) ReadLongLongMember(void* object, Py_ssize_t offset) {
    return ReadMember(object, offset, long long);
}

PyAPI_FUNC(unsigned long long) ReadULongLongMember(void* object, Py_ssize_t offset) {
    return ReadMember(object, offset, unsigned long long);
}

PyAPI_FUNC(Py_ssize_t) ReadPySSizeT(void* object, Py_ssize_t offset) {
    return ReadMember(object, offset, Py_ssize_t);
}


#define WriteMember(object, offset, value, T) *(T*)(((char*)object) + offset) = (T)(value)

PyAPI_FUNC(int) WriteShortMember(void* object, Py_ssize_t offset, short value) {
    WriteMember(object, offset, value, short);
    return 0;
}

PyAPI_FUNC(int) WriteIntMember(void* object, Py_ssize_t offset, int value) {
    WriteMember(object, offset, value, int);
    return 0;
}

PyAPI_FUNC(int) WriteLongMember(void* object, Py_ssize_t offset, long value) {
    WriteMember(object, offset, value, long);
    return 0;
}

PyAPI_FUNC(int) WriteFloatMember(void* object, Py_ssize_t offset, double value) {
    WriteMember(object, offset, value, float);
    return 0;
}

PyAPI_FUNC(int) WriteDoubleMember(void* object, Py_ssize_t offset, double value) {
    WriteMember(object, offset, value, double);
    return 0;
}

PyAPI_FUNC(int) WriteStringMember(void* object, Py_ssize_t offset, char* value) {
    WriteMember(object, offset, value, char*);
    return 0;
}

PyAPI_FUNC(int) WriteStringInPlaceMember(void* object, Py_ssize_t offset, char* value) {
    char *addr = (char*) (((char*) object) + offset);
    size_t n;
//    if (polyglot_has_array_elements(value)) {
//        n = polyglot_get_array_size(value);
//    } else {
        n = strlen(value);
//    }
    memcpy(addr, value, n);
    return 0;
}

PyAPI_FUNC(int) WriteObjectMember(void* object, Py_ssize_t offset, PyObject* value) {
    /* We first need to decref the old value. */
    PyObject *oldv = ReadMember(object, offset, PyObject*);
    Py_XINCREF(value);
    WriteMember(object, offset, value, PyObject*);
    Py_XDECREF(oldv);
    return 0;
}

PyAPI_FUNC(int) WriteCharMember(void* object, Py_ssize_t offset, char value) {
    WriteMember(object, offset, value, char);
    return 0;
}

PyAPI_FUNC(int) WriteByteMember(void* object, Py_ssize_t offset, char value) {
    WriteMember(object, offset, value, char);
    return 0;
}

PyAPI_FUNC(int) WriteUByteMember(void* object, Py_ssize_t offset, unsigned char value) {
    WriteMember(object, offset, value, uint8_t);
    return 0;
}

PyAPI_FUNC(int) WriteUShortMember(void* object, Py_ssize_t offset, unsigned short value) {
    WriteMember(object, offset, value, unsigned short);
    return 0;
}

PyAPI_FUNC(int) WriteUIntMember(void* object, Py_ssize_t offset, unsigned int value) {
    WriteMember(object, offset, value, unsigned int);
    return 0;
}

PyAPI_FUNC(int) WriteULongMember(void* object, Py_ssize_t offset, unsigned long value) {
    WriteMember(object, offset, value, unsigned long);
    return 0;
}

PyAPI_FUNC(int) WriteObjectExMember(void* object, Py_ssize_t offset, PyObject* value) {
    PyObject *oldv = ReadMember(object, offset, PyObject*);
    if (value == NULL && oldv == NULL) {
        return 1;
    }
    Py_XINCREF(value);
    WriteMember(object, offset, value, PyObject*);
    Py_XDECREF(oldv);
    return 0;
}

PyAPI_FUNC(int) WriteLongLongMember(void* object, Py_ssize_t offset, long long value) {
    WriteMember(object, offset, value, long long);
    return 0;
}

PyAPI_FUNC(int) WriteULongLongMember(void* object, Py_ssize_t offset, unsigned long long value) {
    WriteMember(object, offset, value, unsigned long long);
    return 0;
}

PyAPI_FUNC(int) WritePySSizeT(void* object, Py_ssize_t offset, Py_ssize_t value) {
    WriteMember(object, offset, value, Py_ssize_t);
    return 0;
}

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

PyAPI_FUNC(void) truffle_memcpy_bytes(void *dest, size_t dest_offset, void *src, size_t src_offset, size_t len) {
    memcpy(dest + dest_offset, src + src_offset, len);
}

PyAPI_FUNC(void*) truffle_calloc(size_t count, size_t elsize) {
	return calloc(count, elsize);
}

// avoid problems with calling "void" intrinsics via interop
PyAPI_FUNC(int) truffle_free(void* ptr) {
	free(ptr);
	return 1;
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

PyAPI_FUNC(void) truffle_check_type_ready(PyTypeObject* type) {
    if (!(type->tp_flags & Py_TPFLAGS_READY)) {
        PyType_Ready(type);
    }
}

PyAPI_FUNC(int) truffle_BASETYPE_check(PyObject* type) {
    return PyType_HasFeature(Py_TYPE(type), Py_TPFLAGS_BASETYPE);
}

PyAPI_FUNC(void*) truffle_va_arg_pointer(va_list* va) {
	return va_arg(*va, void*);
}

PyAPI_FUNC(void*) truffle_convert_pointer(Py_ssize_t value) {
	return (void*) value;
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

// defined in 'exceptions.c'
void initialize_exceptions();
// defined in 'pyhash.c'
void initialize_hashes();
// defined in 'floatobject.c'
void _PyFloat_InitState(PyInterpreterState* state);

TruffleContext* TRUFFLE_CONTEXT;

PyAPI_FUNC(void) initialize_graal_capi(TruffleEnv* env, void* (*getBuiltin)(int id)) {
	clock_t t = clock();

	if (env) {
		TRUFFLE_CONTEXT = (*env)->getTruffleContext(env);
	}

	initialize_builtins(getBuiltin);
	PyTruffle_Log(PY_TRUFFLE_LOG_FINE, "initialize_builtins: %fs", ((double) (clock() - t)) / CLOCKS_PER_SEC);
    Py_Truffle_Options = GraalPyTruffle_Native_Options();

    // this will set PythonContext.nativeNull and is required to be first
    GraalPyTruffle_Register_NULL(NULL);

    initialize_builtin_types_and_structs();
    // initialize global variables like '_Py_NoneStruct', etc.
    initialize_globals();
    initialize_exceptions();
    initialize_hashes();
    initialize_bufferprocs();
    _PyFloat_InitState(NULL);

    // TODO: initialize during cext initialization doesn't work at the moment
    Py_FileSystemDefaultEncoding = "utf-8"; // strdup(PyUnicode_AsUTF8(GraalPyTruffle_FileSystemDefaultEncoding()));

    PyTruffle_Log(PY_TRUFFLE_LOG_FINE, "initialize_graal_capi: %fs", ((double) (clock() - t)) / CLOCKS_PER_SEC);
}

/*
This is a workaround for C++ modules, namely PyTorch, that declare global/static variables with destructors that call
_Py_DECREF. The destructors get called by libc during exit during which we cannot make upcalls as that would segfault.
So we rebind them to no-ops when exiting.
*/
Py_ssize_t nop_GraalPy_get_PyObject_ob_refcnt(PyObject* obj) {
    return IMMORTAL_REFCNT; // large dummy refcount
}

void nop_GraalPy_set_PyObject_ob_refcnt(PyObject* obj, Py_ssize_t refcnt) {
    // do nothing
}

void nop_GraalPyTruffle_NotifyRefCount(PyObject* obj, Py_ssize_t refcnt) {
    // do nothing
}

void nop_GraalPyTruffle_BulkNotifyRefCount(void* ptrs, int count) {
    // do nothing
}

/*
 * This array contains pairs of variable address and "reset value".
 * The variable location is usually the address of a function pointer variable
 * and the reset value is a new value to set at VM shutdown.
 * For further explanation why this is required, see Java method
 * 'CApiContext.ensureCapiWasLoaded'.
 *
 * Array format: [ var_addr, reset_val, var_addr1, reset_val1, ..., NULL ]
 *
 * ATTENTION: If the structure of the array's content is changed, method
 *            'CApiContext.addNativeFinalizer' *MUST BE* adopted.
 *
 * ATTENTION: the array is expected to be NULL-terminated !
 *
 */
static int64_t reset_func_ptrs[] = {
        &GraalPy_get_PyObject_ob_refcnt,
        nop_GraalPy_get_PyObject_ob_refcnt,
        &GraalPy_set_PyObject_ob_refcnt,
        nop_GraalPy_set_PyObject_ob_refcnt,
        &GraalPyTruffle_NotifyRefCount,
        nop_GraalPyTruffle_NotifyRefCount,
        &GraalPyTruffle_BulkNotifyRefCount,
        nop_GraalPyTruffle_NotifyRefCount,
        /* sentinel (required) */
        NULL
};

/*
 * This function is called from Java during C API initialization to get the
 * pointer to array 'reset_func_pts'.
 */
PyAPI_FUNC(int64_t *) GraalPy_get_finalize_capi_pointer_array() {
    return reset_func_ptrs;
}

#if ((__linux__ && __GNU_LIBRARY__) || __APPLE__)

#include <stdlib.h>
#include <string.h>
#include <execinfo.h>

static void print_c_stacktrace() {
    size_t max_stack_size = 16;
    void* stack = calloc(sizeof(void*), max_stack_size);
    if (stack == NULL) {
        return;
    }

    size_t stack_size = backtrace(stack, max_stack_size);
    char** symbols = backtrace_symbols(stack, stack_size);
    if (symbols == NULL) {
        free(stack);
        return;
    }

	for (size_t i = 0; i < stack_size; i++) {
		printf ("%s\n", symbols[i]);
    }
}

#else

static void print_c_stacktrace() {
    // other platforms are not supported
}

#endif

static void unimplemented(const char* name) {
    printf("Native stacktrace:\n");
    print_c_stacktrace();
}

#define FUNC_NOT_IMPLEMENTED unimplemented(__func__); GraalPyTrufflePrintStacktrace(__func__, "Function not implemented in GraalPy: "); exit(-1);

// {{start CAPI_BUILTINS}}
#include "capi.gen.c.h"
