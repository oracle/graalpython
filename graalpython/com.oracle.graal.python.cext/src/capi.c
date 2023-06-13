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
#include <dlfcn.h>
#include <truffle.h>
#include <trufflenfi.h>

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
    #__TYPE_NAME__,                              /* tp_name */\
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

void initialize_type_structure(PyTypeObject* structure, const char* name) {
    // Store the Sulong struct type id to be used for instances of this class

	PyTruffle_Log(PY_TRUFFLE_LOG_FINEST, "initialize_type_structure: %s", structure->tp_name);
	GraalPyTruffle_SetTypeStore(name, structure);
}

#undef bool
static void initialize_builtin_types_and_structs() {
	clock_t t = clock();
    PyTruffle_Log(PY_TRUFFLE_LOG_FINE, "initialize_builtin_types_and_structs...");
#define PY_TRUFFLE_TYPE_GENERIC(GLOBAL_NAME, __TYPE_NAME__, a, b, c, d, e, f, g) initialize_type_structure(&GLOBAL_NAME, #__TYPE_NAME__);
#define PY_TRUFFLE_TYPE_UNIMPLEMENTED(GLOBAL_NAME) // empty
    PY_TYPE_OBJECTS
#undef PY_TRUFFLE_TYPE_GENERIC
#undef PY_TRUFFLE_TYPE_UNIMPLEMENTED

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
    PyBytes_Type.tp_as_buffer = &bytes_as_buffer;
    GraalPy_set_PyTypeObject_tp_as_buffer(&PyBytes_Type, &bytes_as_buffer);

    static PyBufferProcs bytearray_as_buffer = {
        (getbufferproc)bytearray_getbuffer,          /* bf_getbuffer */
        (releasebufferproc)bytearray_releasebuffer,  /* bf_releasebuffer */
    };
    PyByteArray_Type.tp_as_buffer = &bytearray_as_buffer;
    GraalPy_set_PyTypeObject_tp_as_buffer(&PyByteArray_Type, &bytearray_as_buffer);

    static PyBufferProcs memory_as_buffer = {
        (getbufferproc)memoryview_getbuffer,         /* bf_getbuffer */
        (releasebufferproc)memoryview_releasebuffer, /* bf_releasebuffer */
    };
    PyMemoryView_Type.tp_as_buffer = &memory_as_buffer;
    GraalPy_set_PyTypeObject_tp_as_buffer(&PyMemoryView_Type, &memory_as_buffer);
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
        Py_DECREF(array[i]);
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

PyAPI_FUNC(void) truffle_memcpy_bytes(void *dest, size_t dest_offset, void *src, size_t src_offset, size_t len) {
    memcpy(dest + dest_offset, src + src_offset, len);
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

TruffleContext* TRUFFLE_CONTEXT;

PyAPI_FUNC(void) initialize_graal_capi(TruffleEnv* env, void* (*getBuiltin)(int id)) {
	clock_t t = clock();

	if (env) {
		TRUFFLE_CONTEXT = (*env)->getTruffleContext(env);
	}

	initialize_builtins(getBuiltin);
	PyTruffle_Log(PY_TRUFFLE_LOG_FINE, "initialize_builtins: %fs", ((double) (clock() - t)) / CLOCKS_PER_SEC);
    Py_Truffle_Options = GraalPyTruffle_Native_Options();


    // initialize global variables like '_Py_NoneStruct', etc.
    initialize_globals();
	initialize_builtin_types_and_structs();
    initialize_exceptions();
    initialize_hashes();
    initialize_bufferprocs();

    // TODO: initialize during cext initialization doesn't work at the moment
    Py_FileSystemDefaultEncoding = "utf-8"; // strdup(PyUnicode_AsUTF8(GraalPyTruffle_FileSystemDefaultEncoding()));

    PyTruffle_Log(PY_TRUFFLE_LOG_FINE, "initialize_graal_capi: %fs", ((double) (clock() - t)) / CLOCKS_PER_SEC);
}

static void unimplemented(const char* name) {
	printf("Function not implemented in GraalPy: %s\n", name);
}

#define FUNC_NOT_IMPLEMENTED unimplemented(__func__); exit(-1);

// {{start CAPI_BUILTINS}}
// GENERATED CODE - see CApiCodeGen
// This can be re-generated using the 'mx python-capi-forwards' command or
// by executing the main class CApiCodeGen

#undef PyByteArray_Resize
PyAPI_FUNC(int) PyByteArray_Resize(PyObject* a, Py_ssize_t b) {
    return GraalPyByteArray_Resize(a, b);
}
#undef PyBytes_AsString
PyAPI_FUNC(char*) PyBytes_AsString(PyObject* a) {
    return GraalPyBytes_AsString(a);
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
    return GraalPyCapsule_GetPointer(a, b);
}
#undef PyCapsule_Import
PyAPI_FUNC(void*) PyCapsule_Import(const char* a, int b) {
    return GraalPyCapsule_Import(a, b);
}
#undef PyCapsule_IsValid
PyAPI_FUNC(int) PyCapsule_IsValid(PyObject* a, const char* b) {
    return GraalPyCapsule_IsValid(a, b);
}
#undef PyCapsule_New
PyAPI_FUNC(PyObject*) PyCapsule_New(void* a, const char* b, PyCapsule_Destructor c) {
    return GraalPyCapsule_New(a, b, c);
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
    return GraalPyCapsule_SetName(a, b);
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
    return GraalPyCode_NewEmpty(a, b, c);
}
#undef PyCode_NewWithPosOnlyArgs
PyAPI_FUNC(PyCodeObject*) PyCode_NewWithPosOnlyArgs(int a, int b, int c, int d, int e, int f, PyObject* g, PyObject* h, PyObject* i, PyObject* j, PyObject* k, PyObject* l, PyObject* m, PyObject* n, int o, PyObject* p) {
    return GraalPyCode_NewWithPosOnlyArgs(a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p);
}
#undef PyCodec_Decoder
PyAPI_FUNC(PyObject*) PyCodec_Decoder(const char* a) {
    return GraalPyCodec_Decoder(a);
}
#undef PyCodec_Encoder
PyAPI_FUNC(PyObject*) PyCodec_Encoder(const char* a) {
    return GraalPyCodec_Encoder(a);
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
    return GraalPyContextVar_New(a, b);
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
    return GraalPyErr_NewException(a, b, c);
}
#undef PyErr_NewExceptionWithDoc
PyAPI_FUNC(PyObject*) PyErr_NewExceptionWithDoc(const char* a, const char* b, PyObject* c, PyObject* d) {
    return GraalPyErr_NewExceptionWithDoc(a, b, c, d);
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
    return GraalPyImport_ImportModule(a);
}
#undef PyImport_ImportModuleLevelObject
PyAPI_FUNC(PyObject*) PyImport_ImportModuleLevelObject(PyObject* a, PyObject* b, PyObject* c, PyObject* d, int e) {
    return GraalPyImport_ImportModuleLevelObject(a, b, c, d, e);
}
#undef PyImport_ImportModuleNoBlock
PyAPI_FUNC(PyObject*) PyImport_ImportModuleNoBlock(const char* a) {
    return GraalPyImport_ImportModuleNoBlock(a);
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
    return GraalPyModule_AddIntConstant(a, b, c);
}
#undef PyModule_AddObjectRef
PyAPI_FUNC(int) PyModule_AddObjectRef(PyObject* a, const char* b, PyObject* c) {
    return GraalPyModule_AddObjectRef(a, b, c);
}
#undef PyModule_GetNameObject
PyAPI_FUNC(PyObject*) PyModule_GetNameObject(PyObject* a) {
    return GraalPyModule_GetNameObject(a);
}
#undef PyModule_New
PyAPI_FUNC(PyObject*) PyModule_New(const char* a) {
    return GraalPyModule_New(a);
}
#undef PyModule_NewObject
PyAPI_FUNC(PyObject*) PyModule_NewObject(PyObject* a) {
    return GraalPyModule_NewObject(a);
}
#undef PyModule_SetDocString
PyAPI_FUNC(int) PyModule_SetDocString(PyObject* a, const char* b) {
    return GraalPyModule_SetDocString(a, b);
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
    return GraalPyObject_HasAttrString(a, b);
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
    return GraalPyObject_SetDoc(a, b);
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
    return GraalPyRun_StringFlags(a, b, c, d, e);
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
    return GraalPySys_GetObject(a);
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
    return GraalPyUnicodeDecodeError_Create(a, b, c, d, e, f);
}
#undef PyUnicode_AsEncodedString
PyAPI_FUNC(PyObject*) PyUnicode_AsEncodedString(PyObject* a, const char* b, const char* c) {
    return GraalPyUnicode_AsEncodedString(a, b, c);
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
    return GraalPyUnicode_DecodeFSDefault(a);
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
    return GraalPyUnicode_FromEncodedObject(a, b, c);
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
    return GraalPyUnicode_FromString(a);
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
    return GraalPy_CompileString(a, b, c);
}
#undef Py_CompileStringExFlags
PyAPI_FUNC(PyObject*) Py_CompileStringExFlags(const char* a, const char* b, int c, PyCompilerFlags* d, int e) {
    return GraalPy_CompileStringExFlags(a, b, c, d, e);
}
#undef Py_CompileStringObject
PyAPI_FUNC(PyObject*) Py_CompileStringObject(const char* a, PyObject* b, int c, PyCompilerFlags* d, int e) {
    return GraalPy_CompileStringObject(a, b, c, d, e);
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
#undef _PyArray_Resize
PyAPI_FUNC(int) _PyArray_Resize(PyObject* a, Py_ssize_t b) {
    return Graal_PyArray_Resize(a, b);
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
    Graal_PyErr_BadInternalCall(a, b);
}
#undef _PyErr_ChainExceptions
PyAPI_FUNC(void) _PyErr_ChainExceptions(PyObject* a, PyObject* b, PyObject* c) {
    Graal_PyErr_ChainExceptions(a, b, c);
}
#undef _PyErr_Occurred
PyAPI_FUNC(PyObject*) _PyErr_Occurred(PyThreadState* a) {
    return Graal_PyErr_Occurred(a);
}
#undef _PyErr_WriteUnraisableMsg
PyAPI_FUNC(void) _PyErr_WriteUnraisableMsg(const char* a, PyObject* b) {
    Graal_PyErr_WriteUnraisableMsg(a, b);
}
#undef _PyList_Extend
PyAPI_FUNC(PyObject*) _PyList_Extend(PyListObject* a, PyObject* b) {
    return Graal_PyList_Extend(a, b);
}
#undef _PyList_SET_ITEM
PyAPI_FUNC(void) _PyList_SET_ITEM(PyObject* a, Py_ssize_t b, PyObject* c) {
    Graal_PyList_SET_ITEM(a, b, c);
}
#undef _PyLong_AsByteArray
PyAPI_FUNC(int) _PyLong_AsByteArray(PyLongObject* a, unsigned char* b, size_t c, int d, int e) {
    return Graal_PyLong_AsByteArray(a, b, c, d, e);
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
#undef _PyObject_MakeTpCall
PyAPI_FUNC(PyObject*) _PyObject_MakeTpCall(PyThreadState* a, PyObject* b, PyObject*const* c, Py_ssize_t d, PyObject* e) {
    return Graal_PyObject_MakeTpCall(a, b, c, d, e);
}
#undef _PyTraceMalloc_NewReference
PyAPI_FUNC(int) _PyTraceMalloc_NewReference(PyObject* a) {
    return Graal_PyTraceMalloc_NewReference(a);
}
#undef _PyTraceback_Add
PyAPI_FUNC(void) _PyTraceback_Add(const char* a, const char* b, int c) {
    Graal_PyTraceback_Add(a, b, c);
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
    return Graal_PyTruffleObject_CallMethod1(a, b, c, d);
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
    return Graal_PyUnicode_AsASCIIString(a, b);
}
#undef _PyUnicode_AsLatin1String
PyAPI_FUNC(PyObject*) _PyUnicode_AsLatin1String(PyObject* a, const char* b) {
    return Graal_PyUnicode_AsLatin1String(a, b);
}
#undef _PyUnicode_AsUTF8String
PyAPI_FUNC(PyObject*) _PyUnicode_AsUTF8String(PyObject* a, const char* b) {
    return Graal_PyUnicode_AsUTF8String(a, b);
}
#undef _PyUnicode_EqualToASCIIString
PyAPI_FUNC(int) _PyUnicode_EqualToASCIIString(PyObject* a, const char* b) {
    return Graal_PyUnicode_EqualToASCIIString(a, b);
}
#undef _Py_GetErrorHandler
PyAPI_FUNC(_Py_error_handler) _Py_GetErrorHandler(const char* a) {
    return Graal_Py_GetErrorHandler(a);
}
#undef _Py_HashBytes
PyAPI_FUNC(Py_hash_t) _Py_HashBytes(const void* a, Py_ssize_t b) {
    return Graal_Py_HashBytes(a, b);
}
#undef _Py_HashDouble
PyAPI_FUNC(Py_hash_t) _Py_HashDouble(PyObject* a, double b) {
    return Graal_Py_HashDouble(a, b);
}
PyAPI_FUNC(int64_t*) PyTruffle_constants() {
    static int64_t constants[] = {
        (int64_t) SIZEOF_WCHAR_T,
        (int64_t) PYLONG_BITS_IN_DIGIT,
        (int64_t) READONLY,
        0xdead1111 // marker value
    };
    return constants;
}
PyAPI_FUNC(Py_ssize_t*) PyTruffle_struct_offsets() {
    static Py_ssize_t offsets[] = {
        offsetof(PyObject, ob_refcnt),
        offsetof(PyObject, ob_type),
        offsetof(PyVarObject, ob_size),
        offsetof(PyModuleDef, m_name),
        offsetof(PyModuleDef, m_doc),
        offsetof(PyModuleDef, m_size),
        offsetof(PyModuleDef, m_methods),
        offsetof(PyModuleDef, m_slots),
        offsetof(PyModuleDef, m_traverse),
        offsetof(PyModuleDef, m_clear),
        offsetof(PyModuleDef, m_free),
        offsetof(PyModuleDef_Slot, slot),
        offsetof(PyModuleDef_Slot, value),
        offsetof(PyMethodDef, ml_name),
        offsetof(PyMethodDef, ml_meth),
        offsetof(PyMethodDef, ml_flags),
        offsetof(PyMethodDef, ml_doc),
        offsetof(PyMemoryViewObject, hash),
        offsetof(PyMemoryViewObject, flags),
        offsetof(PyMemoryViewObject, exports),
        offsetof(PyMemoryViewObject, view),
        offsetof(PyMemoryViewObject, ob_array),
        offsetof(Py_buffer, buf),
        offsetof(Py_buffer, obj),
        offsetof(Py_buffer, len),
        offsetof(Py_buffer, itemsize),
        offsetof(Py_buffer, readonly),
        offsetof(Py_buffer, ndim),
        offsetof(Py_buffer, format),
        offsetof(Py_buffer, shape),
        offsetof(Py_buffer, strides),
        offsetof(Py_buffer, suboffsets),
        offsetof(Py_buffer, internal),
        offsetof(PyDateTime_CAPI, DateType),
        offsetof(PyDateTime_CAPI, DateTimeType),
        offsetof(PyDateTime_CAPI, TimeType),
        offsetof(PyDateTime_CAPI, DeltaType),
        offsetof(PyDateTime_CAPI, TZInfoType),
        offsetof(PyDateTime_CAPI, TimeZone_UTC),
        offsetof(PyDateTime_CAPI, Date_FromDate),
        offsetof(PyDateTime_CAPI, DateTime_FromDateAndTime),
        offsetof(PyDateTime_CAPI, Time_FromTime),
        offsetof(PyDateTime_CAPI, Delta_FromDelta),
        offsetof(PyDateTime_CAPI, TimeZone_FromTimeZone),
        offsetof(PyDateTime_CAPI, DateTime_FromTimestamp),
        offsetof(PyDateTime_CAPI, Date_FromTimestamp),
        offsetof(PyDateTime_CAPI, DateTime_FromDateAndTimeAndFold),
        offsetof(PyDateTime_CAPI, Time_FromTimeAndFold),
        offsetof(PyNumberMethods, nb_add),
        offsetof(PyNumberMethods, nb_subtract),
        offsetof(PyNumberMethods, nb_multiply),
        offsetof(PyNumberMethods, nb_remainder),
        offsetof(PyNumberMethods, nb_divmod),
        offsetof(PyNumberMethods, nb_power),
        offsetof(PyNumberMethods, nb_negative),
        offsetof(PyNumberMethods, nb_positive),
        offsetof(PyNumberMethods, nb_absolute),
        offsetof(PyNumberMethods, nb_bool),
        offsetof(PyNumberMethods, nb_invert),
        offsetof(PyNumberMethods, nb_lshift),
        offsetof(PyNumberMethods, nb_rshift),
        offsetof(PyNumberMethods, nb_and),
        offsetof(PyNumberMethods, nb_xor),
        offsetof(PyNumberMethods, nb_or),
        offsetof(PyNumberMethods, nb_int),
        offsetof(PyNumberMethods, nb_reserved),
        offsetof(PyNumberMethods, nb_float),
        offsetof(PyNumberMethods, nb_inplace_add),
        offsetof(PyNumberMethods, nb_inplace_subtract),
        offsetof(PyNumberMethods, nb_inplace_multiply),
        offsetof(PyNumberMethods, nb_inplace_remainder),
        offsetof(PyNumberMethods, nb_inplace_power),
        offsetof(PyNumberMethods, nb_inplace_lshift),
        offsetof(PyNumberMethods, nb_inplace_rshift),
        offsetof(PyNumberMethods, nb_inplace_and),
        offsetof(PyNumberMethods, nb_inplace_xor),
        offsetof(PyNumberMethods, nb_inplace_or),
        offsetof(PyNumberMethods, nb_floor_divide),
        offsetof(PyNumberMethods, nb_true_divide),
        offsetof(PyNumberMethods, nb_inplace_floor_divide),
        offsetof(PyNumberMethods, nb_inplace_true_divide),
        offsetof(PyNumberMethods, nb_index),
        offsetof(PyNumberMethods, nb_matrix_multiply),
        offsetof(PyNumberMethods, nb_inplace_matrix_multiply),
        offsetof(PySequenceMethods, sq_length),
        offsetof(PySequenceMethods, sq_concat),
        offsetof(PySequenceMethods, sq_repeat),
        offsetof(PySequenceMethods, sq_item),
        offsetof(PySequenceMethods, was_sq_slice),
        offsetof(PySequenceMethods, sq_ass_item),
        offsetof(PySequenceMethods, was_sq_ass_slice),
        offsetof(PySequenceMethods, sq_contains),
        offsetof(PySequenceMethods, sq_inplace_concat),
        offsetof(PySequenceMethods, sq_inplace_repeat),
        offsetof(PyMappingMethods, mp_length),
        offsetof(PyMappingMethods, mp_subscript),
        offsetof(PyMappingMethods, mp_ass_subscript),
        offsetof(PyAsyncMethods, am_await),
        offsetof(PyAsyncMethods, am_aiter),
        offsetof(PyAsyncMethods, am_anext),
        offsetof(PyAsyncMethods, am_send),
        offsetof(PyBufferProcs, bf_getbuffer),
        offsetof(PyBufferProcs, bf_releasebuffer),
        offsetof(PyTypeObject, tp_name),
        offsetof(PyTypeObject, tp_basicsize),
        offsetof(PyTypeObject, tp_itemsize),
        offsetof(PyTypeObject, tp_dealloc),
        offsetof(PyTypeObject, tp_vectorcall_offset),
        offsetof(PyTypeObject, tp_getattr),
        offsetof(PyTypeObject, tp_setattr),
        offsetof(PyTypeObject, tp_as_async),
        offsetof(PyTypeObject, tp_repr),
        offsetof(PyTypeObject, tp_as_number),
        offsetof(PyTypeObject, tp_as_sequence),
        offsetof(PyTypeObject, tp_as_mapping),
        offsetof(PyTypeObject, tp_hash),
        offsetof(PyTypeObject, tp_call),
        offsetof(PyTypeObject, tp_str),
        offsetof(PyTypeObject, tp_getattro),
        offsetof(PyTypeObject, tp_setattro),
        offsetof(PyTypeObject, tp_as_buffer),
        offsetof(PyTypeObject, tp_flags),
        offsetof(PyTypeObject, tp_doc),
        offsetof(PyTypeObject, tp_traverse),
        offsetof(PyTypeObject, tp_clear),
        offsetof(PyTypeObject, tp_richcompare),
        offsetof(PyTypeObject, tp_weaklistoffset),
        offsetof(PyTypeObject, tp_iter),
        offsetof(PyTypeObject, tp_iternext),
        offsetof(PyTypeObject, tp_methods),
        offsetof(PyTypeObject, tp_members),
        offsetof(PyTypeObject, tp_getset),
        offsetof(PyTypeObject, tp_base),
        offsetof(PyTypeObject, tp_dict),
        offsetof(PyTypeObject, tp_descr_get),
        offsetof(PyTypeObject, tp_descr_set),
        offsetof(PyTypeObject, tp_dictoffset),
        offsetof(PyTypeObject, tp_init),
        offsetof(PyTypeObject, tp_alloc),
        offsetof(PyTypeObject, tp_new),
        offsetof(PyTypeObject, tp_free),
        offsetof(PyTypeObject, tp_is_gc),
        offsetof(PyTypeObject, tp_bases),
        offsetof(PyTypeObject, tp_mro),
        offsetof(PyTypeObject, tp_cache),
        offsetof(PyTypeObject, tp_subclasses),
        offsetof(PyTypeObject, tp_weaklist),
        offsetof(PyTypeObject, tp_del),
        offsetof(PyTypeObject, tp_version_tag),
        offsetof(PyTypeObject, tp_finalize),
        offsetof(PyTypeObject, tp_vectorcall),
        offsetof(PyBytesObject, ob_shash),
        offsetof(PyBytesObject, ob_sval),
        offsetof(PyListObject, ob_item),
        offsetof(PyListObject, allocated),
        offsetof(PyTupleObject, ob_item),
        offsetof(PyFloatObject, ob_fval),
        offsetof(PyModuleDef_Base, m_index),
        offsetof(PyComplexObject, cval.real),
        offsetof(PyComplexObject, cval.imag),
        offsetof(PyASCIIObject, length),
        offsetof(PyASCIIObject, hash),
        offsetof(PyASCIIObject, state),
        offsetof(PyASCIIObject, wstr),
        offsetof(PyCompactUnicodeObject, utf8_length),
        offsetof(PyCompactUnicodeObject, utf8),
        offsetof(PyCompactUnicodeObject, wstr_length),
        offsetof(PyUnicodeObject, data),
        offsetof(PyGetSetDef, name),
        offsetof(PyGetSetDef, get),
        offsetof(PyGetSetDef, set),
        offsetof(PyGetSetDef, doc),
        offsetof(PyGetSetDef, closure),
        offsetof(PyMemberDef, name),
        offsetof(PyMemberDef, type),
        offsetof(PyMemberDef, offset),
        offsetof(PyMemberDef, flags),
        offsetof(PyMemberDef, doc),
        offsetof(PyThreadState, interp),
        offsetof(PyThreadState, dict),
        offsetof(PyBaseExceptionObject, dict),
        offsetof(PyBaseExceptionObject, args),
        offsetof(PyBaseExceptionObject, traceback),
        offsetof(PyBaseExceptionObject, context),
        offsetof(PyBaseExceptionObject, cause),
        offsetof(PyBaseExceptionObject, suppress_context),
        0xdead2222 // marker value
    };
    return offsets;
}
PyAPI_FUNC(Py_ssize_t*) PyTruffle_struct_sizes() {
    static Py_ssize_t sizes[] = {
        sizeof(PyModuleDef),
        sizeof(PyModuleDef_Slot),
        sizeof(PyMethodDef),
        sizeof(PyObject),
        sizeof(PyBytesObject),
        sizeof(PyListObject),
        sizeof(PyVarObject),
        sizeof(PyMemoryViewObject),
        sizeof(Py_buffer),
        sizeof(PyDateTime_CAPI),
        sizeof(PyNumberMethods),
        sizeof(PySequenceMethods),
        sizeof(PyMappingMethods),
        sizeof(PyAsyncMethods),
        sizeof(PyBufferProcs),
        sizeof(PyTypeObject),
        sizeof(PyTupleObject),
        sizeof(PyFloatObject),
        sizeof(PyLongObject),
        sizeof(PyModuleDef_Base),
        sizeof(PyComplexObject),
        sizeof(PyDateTime_Date),
        sizeof(PyDateTime_Time),
        sizeof(PyDateTime_DateTime),
        sizeof(PyDateTime_Delta),
        sizeof(PyASCIIObject),
        sizeof(PyCompactUnicodeObject),
        sizeof(PyBaseExceptionObject),
        sizeof(PyUnicodeObject),
        sizeof(Py_UNICODE),
        sizeof(PyGetSetDef),
        sizeof(PyMemberDef),
        sizeof(PyThreadState),
        sizeof(wchar_t),
        0xdead3333 // marker value
    };
    return sizes;
}
// {{end CAPI_BUILTINS}}
