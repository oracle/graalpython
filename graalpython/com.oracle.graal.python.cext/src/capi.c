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

    static PyBufferProcs array_as_buffer;
    array_as_buffer.bf_getbuffer = GraalPyTruffle_Array_getbuffer,
    array_as_buffer.bf_releasebuffer = GraalPyTruffle_Array_releasebuffer,
    Arraytype.tp_as_buffer = &array_as_buffer;
    GraalPy_set_PyTypeObject_tp_as_buffer(&Arraytype, &array_as_buffer);
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
	for (int i = 0; i < len; i++) {
    	PyObject *obj = (PyObject*) ptrArray[i];
        if (Py_TYPE(obj)->tp_dealloc != object_dealloc) {
            /* we don't need to care about objects with default deallocation process */
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

PyAPI_FUNC(void*) truffle_calloc(size_t size) {
	return calloc(1, size);
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

/*
This is a workaround for C++ modules, namely PyTorch, that declare global/static variables with destructors that call
_Py_DECREF. The destructors get called by libc during exit during which we cannot make upcalls as that would segfault.
So we rebind them to no-ops when exiting.
*/
Py_ssize_t nop_GraalPy_get_PyObject_ob_refcnt(PyObject* obj) {
 return 100; // large dummy refcount
}
void nop_GraalPy_set_PyObject_ob_refcnt(PyObject* obj, Py_ssize_t refcnt) {
 // do nothing
}
PyAPI_FUNC(void) finalizeCAPI() {
 GraalPy_get_PyObject_ob_refcnt = nop_GraalPy_get_PyObject_ob_refcnt;
 GraalPy_set_PyObject_ob_refcnt = nop_GraalPy_set_PyObject_ob_refcnt;
}

static void unimplemented(const char* name) {
	printf("Function not implemented in GraalPy: %s\n", name);
}

#define FUNC_NOT_IMPLEMENTED unimplemented(__func__); exit(-1);

// {{start CAPI_BUILTINS}}
// GENERATED CODE - see CApiCodeGen
// This can be re-generated using the 'mx python-capi-forwards' command or
// by executing the main class CApiCodeGen

#undef PyAIter_Check
PyAPI_FUNC(int) PyAIter_Check(PyObject* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyArg_ValidateKeywordArguments
PyAPI_FUNC(int) PyArg_ValidateKeywordArguments(PyObject* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyAsyncGen_New
PyAPI_FUNC(PyObject*) PyAsyncGen_New(PyFrameObject* a, PyObject* b, PyObject* c) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyBuffer_FillContiguousStrides
PyAPI_FUNC(void) PyBuffer_FillContiguousStrides(int a, Py_ssize_t* b, Py_ssize_t* c, int d, char e) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyBuffer_FromContiguous
PyAPI_FUNC(int) PyBuffer_FromContiguous(Py_buffer* a, void* b, Py_ssize_t c, char d) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyBuffer_GetPointer
PyAPI_FUNC(void*) PyBuffer_GetPointer(Py_buffer* a, Py_ssize_t* b) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyBuffer_SizeFromFormat
PyAPI_FUNC(Py_ssize_t) PyBuffer_SizeFromFormat(const char* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyBuffer_ToContiguous
PyAPI_FUNC(int) PyBuffer_ToContiguous(void* a, Py_buffer* b, Py_ssize_t c, char d) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyByteArray_Concat
PyAPI_FUNC(PyObject*) PyByteArray_Concat(PyObject* a, PyObject* b) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyByteArray_FromObject
PyAPI_FUNC(PyObject*) PyByteArray_FromObject(PyObject* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyByteArray_Resize
PyAPI_FUNC(int) PyByteArray_Resize(PyObject* a, Py_ssize_t b) {
    return GraalPyByteArray_Resize(a, b);
}
#undef PyBytes_AsString
PyAPI_FUNC(char*) PyBytes_AsString(PyObject* a) {
    return GraalPyBytes_AsString(a);
}
#undef PyBytes_DecodeEscape
PyAPI_FUNC(PyObject*) PyBytes_DecodeEscape(const char* a, Py_ssize_t b, const char* c, Py_ssize_t d, const char* e) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyBytes_FromObject
PyAPI_FUNC(PyObject*) PyBytes_FromObject(PyObject* a) {
    return GraalPyBytes_FromObject(a);
}
#undef PyBytes_Repr
PyAPI_FUNC(PyObject*) PyBytes_Repr(PyObject* a, int b) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyBytes_Size
PyAPI_FUNC(Py_ssize_t) PyBytes_Size(PyObject* a) {
    return GraalPyBytes_Size(a);
}
#undef PyCFunction_Call
PyAPI_FUNC(PyObject*) PyCFunction_Call(PyObject* a, PyObject* b, PyObject* c) {
    FUNC_NOT_IMPLEMENTED
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
#undef PyCell_Get
PyAPI_FUNC(PyObject*) PyCell_Get(PyObject* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyCell_New
PyAPI_FUNC(PyObject*) PyCell_New(PyObject* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyCell_Set
PyAPI_FUNC(int) PyCell_Set(PyObject* a, PyObject* b) {
    FUNC_NOT_IMPLEMENTED
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
#undef PyCode_Optimize
PyAPI_FUNC(PyObject*) PyCode_Optimize(PyObject* a, PyObject* b, PyObject* c, PyObject* d) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyCodec_BackslashReplaceErrors
PyAPI_FUNC(PyObject*) PyCodec_BackslashReplaceErrors(PyObject* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyCodec_Decode
PyAPI_FUNC(PyObject*) PyCodec_Decode(PyObject* a, const char* b, const char* c) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyCodec_Decoder
PyAPI_FUNC(PyObject*) PyCodec_Decoder(const char* a) {
    return GraalPyCodec_Decoder(a);
}
#undef PyCodec_Encode
PyAPI_FUNC(PyObject*) PyCodec_Encode(PyObject* a, const char* b, const char* c) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyCodec_Encoder
PyAPI_FUNC(PyObject*) PyCodec_Encoder(const char* a) {
    return GraalPyCodec_Encoder(a);
}
#undef PyCodec_IgnoreErrors
PyAPI_FUNC(PyObject*) PyCodec_IgnoreErrors(PyObject* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyCodec_IncrementalDecoder
PyAPI_FUNC(PyObject*) PyCodec_IncrementalDecoder(const char* a, const char* b) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyCodec_IncrementalEncoder
PyAPI_FUNC(PyObject*) PyCodec_IncrementalEncoder(const char* a, const char* b) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyCodec_KnownEncoding
PyAPI_FUNC(int) PyCodec_KnownEncoding(const char* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyCodec_LookupError
PyAPI_FUNC(PyObject*) PyCodec_LookupError(const char* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyCodec_NameReplaceErrors
PyAPI_FUNC(PyObject*) PyCodec_NameReplaceErrors(PyObject* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyCodec_Register
PyAPI_FUNC(int) PyCodec_Register(PyObject* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyCodec_RegisterError
PyAPI_FUNC(int) PyCodec_RegisterError(const char* a, PyObject* b) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyCodec_ReplaceErrors
PyAPI_FUNC(PyObject*) PyCodec_ReplaceErrors(PyObject* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyCodec_StreamReader
PyAPI_FUNC(PyObject*) PyCodec_StreamReader(const char* a, PyObject* b, const char* c) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyCodec_StreamWriter
PyAPI_FUNC(PyObject*) PyCodec_StreamWriter(const char* a, PyObject* b, const char* c) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyCodec_StrictErrors
PyAPI_FUNC(PyObject*) PyCodec_StrictErrors(PyObject* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyCodec_Unregister
PyAPI_FUNC(int) PyCodec_Unregister(PyObject* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyCodec_XMLCharRefReplaceErrors
PyAPI_FUNC(PyObject*) PyCodec_XMLCharRefReplaceErrors(PyObject* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyCompile_OpcodeStackEffect
PyAPI_FUNC(int) PyCompile_OpcodeStackEffect(int a, int b) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyCompile_OpcodeStackEffectWithJump
PyAPI_FUNC(int) PyCompile_OpcodeStackEffectWithJump(int a, int b, int c) {
    FUNC_NOT_IMPLEMENTED
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
#undef PyConfig_Clear
PyAPI_FUNC(void) PyConfig_Clear(PyConfig* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyConfig_InitIsolatedConfig
PyAPI_FUNC(void) PyConfig_InitIsolatedConfig(PyConfig* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyConfig_InitPythonConfig
PyAPI_FUNC(void) PyConfig_InitPythonConfig(PyConfig* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyConfig_Read
PyAPI_FUNC(PyStatus) PyConfig_Read(PyConfig* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyConfig_SetArgv
PyAPI_FUNC(PyStatus) PyConfig_SetArgv(PyConfig* a, Py_ssize_t b, wchar_t*const* c) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyConfig_SetBytesArgv
PyAPI_FUNC(PyStatus) PyConfig_SetBytesArgv(PyConfig* a, Py_ssize_t b, char*const* c) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyConfig_SetBytesString
PyAPI_FUNC(PyStatus) PyConfig_SetBytesString(PyConfig* a, wchar_t** b, const char* c) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyConfig_SetString
PyAPI_FUNC(PyStatus) PyConfig_SetString(PyConfig* a, wchar_t** b, const wchar_t* c) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyConfig_SetWideStringList
PyAPI_FUNC(PyStatus) PyConfig_SetWideStringList(PyConfig* a, PyWideStringList* b, Py_ssize_t c, wchar_t** d) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyContextVar_New
PyAPI_FUNC(PyObject*) PyContextVar_New(const char* a, PyObject* b) {
    return GraalPyContextVar_New(a, b);
}
#undef PyContextVar_Reset
PyAPI_FUNC(int) PyContextVar_Reset(PyObject* a, PyObject* b) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyContextVar_Set
PyAPI_FUNC(PyObject*) PyContextVar_Set(PyObject* a, PyObject* b) {
    return GraalPyContextVar_Set(a, b);
}
#undef PyContext_Copy
PyAPI_FUNC(PyObject*) PyContext_Copy(PyObject* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyContext_CopyCurrent
PyAPI_FUNC(PyObject*) PyContext_CopyCurrent() {
    FUNC_NOT_IMPLEMENTED
}
#undef PyContext_Enter
PyAPI_FUNC(int) PyContext_Enter(PyObject* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyContext_Exit
PyAPI_FUNC(int) PyContext_Exit(PyObject* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyContext_New
PyAPI_FUNC(PyObject*) PyContext_New() {
    FUNC_NOT_IMPLEMENTED
}
#undef PyCoro_New
PyAPI_FUNC(PyObject*) PyCoro_New(PyFrameObject* a, PyObject* b, PyObject* c) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyDescr_NewMember
PyAPI_FUNC(PyObject*) PyDescr_NewMember(PyTypeObject* a, struct PyMemberDef* b) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyDescr_NewMethod
PyAPI_FUNC(PyObject*) PyDescr_NewMethod(PyTypeObject* a, PyMethodDef* b) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyDescr_NewWrapper
PyAPI_FUNC(PyObject*) PyDescr_NewWrapper(PyTypeObject* a, struct wrapperbase* b, void* c) {
    FUNC_NOT_IMPLEMENTED
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
#undef PyDict_MergeFromSeq2
PyAPI_FUNC(int) PyDict_MergeFromSeq2(PyObject* a, PyObject* b, int c) {
    FUNC_NOT_IMPLEMENTED
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
#undef PyErr_ProgramText
PyAPI_FUNC(PyObject*) PyErr_ProgramText(const char* a, int b) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyErr_ProgramTextObject
PyAPI_FUNC(PyObject*) PyErr_ProgramTextObject(PyObject* a, int b) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyErr_RangedSyntaxLocationObject
PyAPI_FUNC(void) PyErr_RangedSyntaxLocationObject(PyObject* a, int b, int c, int d, int e) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyErr_Restore
PyAPI_FUNC(void) PyErr_Restore(PyObject* a, PyObject* b, PyObject* c) {
    GraalPyErr_Restore(a, b, c);
}
#undef PyErr_SetExcInfo
PyAPI_FUNC(void) PyErr_SetExcInfo(PyObject* a, PyObject* b, PyObject* c) {
    GraalPyErr_SetExcInfo(a, b, c);
}
#undef PyErr_SetImportError
PyAPI_FUNC(PyObject*) PyErr_SetImportError(PyObject* a, PyObject* b, PyObject* c) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyErr_SetImportErrorSubclass
PyAPI_FUNC(PyObject*) PyErr_SetImportErrorSubclass(PyObject* a, PyObject* b, PyObject* c, PyObject* d) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyErr_SetInterrupt
PyAPI_FUNC(void) PyErr_SetInterrupt() {
    FUNC_NOT_IMPLEMENTED
}
#undef PyErr_SetInterruptEx
PyAPI_FUNC(int) PyErr_SetInterruptEx(int a) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyErr_SyntaxLocation
PyAPI_FUNC(void) PyErr_SyntaxLocation(const char* a, int b) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyErr_SyntaxLocationEx
PyAPI_FUNC(void) PyErr_SyntaxLocationEx(const char* a, int b, int c) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyErr_SyntaxLocationObject
PyAPI_FUNC(void) PyErr_SyntaxLocationObject(PyObject* a, int b, int c) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyEval_AcquireLock
PyAPI_FUNC(void) PyEval_AcquireLock() {
    FUNC_NOT_IMPLEMENTED
}
#undef PyEval_AcquireThread
PyAPI_FUNC(void) PyEval_AcquireThread(PyThreadState* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyEval_CallFunction
PyAPI_FUNC(PyObject*) PyEval_CallFunction(PyObject* a, const char* b, ...) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyEval_CallMethod
PyAPI_FUNC(PyObject*) PyEval_CallMethod(PyObject* a, const char* b, const char* c, ...) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyEval_EvalFrame
PyAPI_FUNC(PyObject*) PyEval_EvalFrame(PyFrameObject* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyEval_EvalFrameEx
PyAPI_FUNC(PyObject*) PyEval_EvalFrameEx(PyFrameObject* a, int b) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyEval_GetBuiltins
PyAPI_FUNC(PyObject*) PyEval_GetBuiltins() {
    return GraalPyEval_GetBuiltins();
}
#undef PyEval_GetFrame
PyAPI_FUNC(PyFrameObject*) PyEval_GetFrame() {
    return GraalPyEval_GetFrame();
}
#undef PyEval_GetFuncDesc
PyAPI_FUNC(const char*) PyEval_GetFuncDesc(PyObject* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyEval_GetFuncName
PyAPI_FUNC(const char*) PyEval_GetFuncName(PyObject* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyEval_GetGlobals
PyAPI_FUNC(PyObject*) PyEval_GetGlobals() {
    FUNC_NOT_IMPLEMENTED
}
#undef PyEval_GetLocals
PyAPI_FUNC(PyObject*) PyEval_GetLocals() {
    FUNC_NOT_IMPLEMENTED
}
#undef PyEval_ReleaseLock
PyAPI_FUNC(void) PyEval_ReleaseLock() {
    FUNC_NOT_IMPLEMENTED
}
#undef PyEval_ReleaseThread
PyAPI_FUNC(void) PyEval_ReleaseThread(PyThreadState* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyEval_RestoreThread
PyAPI_FUNC(void) PyEval_RestoreThread(PyThreadState* a) {
    GraalPyEval_RestoreThread(a);
}
#undef PyEval_SaveThread
PyAPI_FUNC(PyThreadState*) PyEval_SaveThread() {
    return GraalPyEval_SaveThread();
}
#undef PyEval_SetProfile
PyAPI_FUNC(void) PyEval_SetProfile(Py_tracefunc a, PyObject* b) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyEval_SetTrace
PyAPI_FUNC(void) PyEval_SetTrace(Py_tracefunc a, PyObject* b) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyExceptionClass_Name
PyAPI_FUNC(const char*) PyExceptionClass_Name(PyObject* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyException_GetCause
PyAPI_FUNC(PyObject*) PyException_GetCause(PyObject* a) {
    return GraalPyException_GetCause(a);
}
#undef PyException_GetContext
PyAPI_FUNC(PyObject*) PyException_GetContext(PyObject* a) {
    return GraalPyException_GetContext(a);
}
#undef PyException_GetTraceback
PyAPI_FUNC(PyObject*) PyException_GetTraceback(PyObject* a) {
    FUNC_NOT_IMPLEMENTED
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
#undef PyFile_FromFd
PyAPI_FUNC(PyObject*) PyFile_FromFd(int a, const char* b, const char* c, int d, const char* e, const char* f, const char* g, int h) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyFile_GetLine
PyAPI_FUNC(PyObject*) PyFile_GetLine(PyObject* a, int b) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyFile_NewStdPrinter
PyAPI_FUNC(PyObject*) PyFile_NewStdPrinter(int a) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyFile_OpenCode
PyAPI_FUNC(PyObject*) PyFile_OpenCode(const char* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyFile_OpenCodeObject
PyAPI_FUNC(PyObject*) PyFile_OpenCodeObject(PyObject* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyFile_SetOpenCodeHook
PyAPI_FUNC(int) PyFile_SetOpenCodeHook(Py_OpenCodeHookFunction a, void* b) {
    FUNC_NOT_IMPLEMENTED
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
#undef PyFloat_GetInfo
PyAPI_FUNC(PyObject*) PyFloat_GetInfo() {
    FUNC_NOT_IMPLEMENTED
}
#undef PyFloat_GetMax
PyAPI_FUNC(double) PyFloat_GetMax() {
    FUNC_NOT_IMPLEMENTED
}
#undef PyFloat_GetMin
PyAPI_FUNC(double) PyFloat_GetMin() {
    FUNC_NOT_IMPLEMENTED
}
#undef PyFrame_BlockPop
PyAPI_FUNC(PyTryBlock*) PyFrame_BlockPop(PyFrameObject* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyFrame_BlockSetup
PyAPI_FUNC(void) PyFrame_BlockSetup(PyFrameObject* a, int b, int c, int d) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyFrame_FastToLocals
PyAPI_FUNC(void) PyFrame_FastToLocals(PyFrameObject* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyFrame_FastToLocalsWithError
PyAPI_FUNC(int) PyFrame_FastToLocalsWithError(PyFrameObject* a) {
    FUNC_NOT_IMPLEMENTED
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
#undef PyFrame_LocalsToFast
PyAPI_FUNC(void) PyFrame_LocalsToFast(PyFrameObject* a, int b) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyFrame_New
PyAPI_FUNC(PyFrameObject*) PyFrame_New(PyThreadState* a, PyCodeObject* b, PyObject* c, PyObject* d) {
    return GraalPyFrame_New(a, b, c, d);
}
#undef PyFrozenSet_New
PyAPI_FUNC(PyObject*) PyFrozenSet_New(PyObject* a) {
    return GraalPyFrozenSet_New(a);
}
#undef PyFunction_GetAnnotations
PyAPI_FUNC(PyObject*) PyFunction_GetAnnotations(PyObject* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyFunction_GetClosure
PyAPI_FUNC(PyObject*) PyFunction_GetClosure(PyObject* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyFunction_GetCode
PyAPI_FUNC(PyObject*) PyFunction_GetCode(PyObject* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyFunction_GetDefaults
PyAPI_FUNC(PyObject*) PyFunction_GetDefaults(PyObject* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyFunction_GetGlobals
PyAPI_FUNC(PyObject*) PyFunction_GetGlobals(PyObject* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyFunction_GetKwDefaults
PyAPI_FUNC(PyObject*) PyFunction_GetKwDefaults(PyObject* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyFunction_GetModule
PyAPI_FUNC(PyObject*) PyFunction_GetModule(PyObject* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyFunction_New
PyAPI_FUNC(PyObject*) PyFunction_New(PyObject* a, PyObject* b) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyFunction_NewWithQualName
PyAPI_FUNC(PyObject*) PyFunction_NewWithQualName(PyObject* a, PyObject* b, PyObject* c) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyFunction_SetAnnotations
PyAPI_FUNC(int) PyFunction_SetAnnotations(PyObject* a, PyObject* b) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyFunction_SetClosure
PyAPI_FUNC(int) PyFunction_SetClosure(PyObject* a, PyObject* b) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyFunction_SetDefaults
PyAPI_FUNC(int) PyFunction_SetDefaults(PyObject* a, PyObject* b) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyFunction_SetKwDefaults
PyAPI_FUNC(int) PyFunction_SetKwDefaults(PyObject* a, PyObject* b) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyGC_Collect
PyAPI_FUNC(Py_ssize_t) PyGC_Collect() {
    FUNC_NOT_IMPLEMENTED
}
#undef PyGC_Disable
PyAPI_FUNC(int) PyGC_Disable() {
    FUNC_NOT_IMPLEMENTED
}
#undef PyGC_Enable
PyAPI_FUNC(int) PyGC_Enable() {
    FUNC_NOT_IMPLEMENTED
}
#undef PyGC_IsEnabled
PyAPI_FUNC(int) PyGC_IsEnabled() {
    FUNC_NOT_IMPLEMENTED
}
#undef PyGILState_Check
PyAPI_FUNC(int) PyGILState_Check() {
    return GraalPyGILState_Check();
}
#undef PyImport_AppendInittab
PyAPI_FUNC(int) PyImport_AppendInittab(const char* a, PyObject*(*b)(void)) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyImport_ExecCodeModule
PyAPI_FUNC(PyObject*) PyImport_ExecCodeModule(const char* a, PyObject* b) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyImport_ExecCodeModuleEx
PyAPI_FUNC(PyObject*) PyImport_ExecCodeModuleEx(const char* a, PyObject* b, const char* c) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyImport_ExecCodeModuleObject
PyAPI_FUNC(PyObject*) PyImport_ExecCodeModuleObject(PyObject* a, PyObject* b, PyObject* c, PyObject* d) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyImport_ExecCodeModuleWithPathnames
PyAPI_FUNC(PyObject*) PyImport_ExecCodeModuleWithPathnames(const char* a, PyObject* b, const char* c, const char* d) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyImport_ExtendInittab
PyAPI_FUNC(int) PyImport_ExtendInittab(struct _inittab* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyImport_GetImporter
PyAPI_FUNC(PyObject*) PyImport_GetImporter(PyObject* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyImport_GetMagicNumber
PyAPI_FUNC(long) PyImport_GetMagicNumber() {
    FUNC_NOT_IMPLEMENTED
}
#undef PyImport_GetMagicTag
PyAPI_FUNC(const char*) PyImport_GetMagicTag() {
    FUNC_NOT_IMPLEMENTED
}
#undef PyImport_GetModule
PyAPI_FUNC(PyObject*) PyImport_GetModule(PyObject* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyImport_GetModuleDict
PyAPI_FUNC(PyObject*) PyImport_GetModuleDict() {
    return GraalPyImport_GetModuleDict();
}
#undef PyImport_Import
PyAPI_FUNC(PyObject*) PyImport_Import(PyObject* a) {
    return GraalPyImport_Import(a);
}
#undef PyImport_ImportFrozenModule
PyAPI_FUNC(int) PyImport_ImportFrozenModule(const char* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyImport_ImportFrozenModuleObject
PyAPI_FUNC(int) PyImport_ImportFrozenModuleObject(PyObject* a) {
    FUNC_NOT_IMPLEMENTED
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
#undef PyImport_ReloadModule
PyAPI_FUNC(PyObject*) PyImport_ReloadModule(PyObject* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyIndex_Check
PyAPI_FUNC(int) PyIndex_Check(PyObject* a) {
    return GraalPyIndex_Check(a);
}
#undef PyInit__imp
PyAPI_FUNC(PyObject*) PyInit__imp() {
    FUNC_NOT_IMPLEMENTED
}
#undef PyInstanceMethod_New
PyAPI_FUNC(PyObject*) PyInstanceMethod_New(PyObject* a) {
    return GraalPyInstanceMethod_New(a);
}
#undef PyInterpreterState_Clear
PyAPI_FUNC(void) PyInterpreterState_Clear(PyInterpreterState* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyInterpreterState_Delete
PyAPI_FUNC(void) PyInterpreterState_Delete(PyInterpreterState* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyInterpreterState_Get
PyAPI_FUNC(PyInterpreterState*) PyInterpreterState_Get() {
    FUNC_NOT_IMPLEMENTED
}
#undef PyInterpreterState_GetDict
PyAPI_FUNC(PyObject*) PyInterpreterState_GetDict(PyInterpreterState* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyInterpreterState_Head
PyAPI_FUNC(PyInterpreterState*) PyInterpreterState_Head() {
    FUNC_NOT_IMPLEMENTED
}
#undef PyInterpreterState_New
PyAPI_FUNC(PyInterpreterState*) PyInterpreterState_New() {
    FUNC_NOT_IMPLEMENTED
}
#undef PyInterpreterState_Next
PyAPI_FUNC(PyInterpreterState*) PyInterpreterState_Next(PyInterpreterState* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyInterpreterState_ThreadHead
PyAPI_FUNC(PyThreadState*) PyInterpreterState_ThreadHead(PyInterpreterState* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyIter_Check
PyAPI_FUNC(int) PyIter_Check(PyObject* a) {
    return GraalPyIter_Check(a);
}
#undef PyIter_Next
PyAPI_FUNC(PyObject*) PyIter_Next(PyObject* a) {
    return GraalPyIter_Next(a);
}
#undef PyIter_Send
PyAPI_FUNC(PySendResult) PyIter_Send(PyObject* a, PyObject* b, PyObject** c) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyLineTable_InitAddressRange
PyAPI_FUNC(void) PyLineTable_InitAddressRange(const char* a, Py_ssize_t b, int c, PyCodeAddressRange* d) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyLineTable_NextAddressRange
PyAPI_FUNC(int) PyLineTable_NextAddressRange(PyCodeAddressRange* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyLineTable_PreviousAddressRange
PyAPI_FUNC(int) PyLineTable_PreviousAddressRange(PyCodeAddressRange* a) {
    FUNC_NOT_IMPLEMENTED
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
#undef PyLong_FromUnicodeObject
PyAPI_FUNC(PyObject*) PyLong_FromUnicodeObject(PyObject* a, int b) {
    return GraalPyLong_FromUnicodeObject(a, b);
}
#undef PyLong_FromUnsignedLong
PyAPI_FUNC(PyObject*) PyLong_FromUnsignedLong(unsigned long a) {
    return GraalPyLong_FromUnsignedLong(a);
}
#undef PyLong_FromUnsignedLongLong
PyAPI_FUNC(PyObject*) PyLong_FromUnsignedLongLong(unsigned long long a) {
    return GraalPyLong_FromUnsignedLongLong(a);
}
#undef PyLong_GetInfo
PyAPI_FUNC(PyObject*) PyLong_GetInfo() {
    FUNC_NOT_IMPLEMENTED
}
#undef PyMapping_Check
PyAPI_FUNC(int) PyMapping_Check(PyObject* a) {
    return GraalPyMapping_Check(a);
}
#undef PyMapping_HasKey
PyAPI_FUNC(int) PyMapping_HasKey(PyObject* a, PyObject* b) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyMapping_HasKeyString
PyAPI_FUNC(int) PyMapping_HasKeyString(PyObject* a, const char* b) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyMapping_Items
PyAPI_FUNC(PyObject*) PyMapping_Items(PyObject* a) {
    return GraalPyMapping_Items(a);
}
#undef PyMapping_Keys
PyAPI_FUNC(PyObject*) PyMapping_Keys(PyObject* a) {
    return GraalPyMapping_Keys(a);
}
#undef PyMapping_Length
PyAPI_FUNC(Py_ssize_t) PyMapping_Length(PyObject* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyMapping_SetItemString
PyAPI_FUNC(int) PyMapping_SetItemString(PyObject* a, const char* b, PyObject* c) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyMapping_Size
PyAPI_FUNC(Py_ssize_t) PyMapping_Size(PyObject* a) {
    return GraalPyMapping_Size(a);
}
#undef PyMapping_Values
PyAPI_FUNC(PyObject*) PyMapping_Values(PyObject* a) {
    return GraalPyMapping_Values(a);
}
#undef PyMem_GetAllocator
PyAPI_FUNC(void) PyMem_GetAllocator(PyMemAllocatorDomain a, PyMemAllocatorEx* b) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyMem_SetAllocator
PyAPI_FUNC(void) PyMem_SetAllocator(PyMemAllocatorDomain a, PyMemAllocatorEx* b) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyMem_SetupDebugHooks
PyAPI_FUNC(void) PyMem_SetupDebugHooks() {
    FUNC_NOT_IMPLEMENTED
}
#undef PyMember_GetOne
PyAPI_FUNC(PyObject*) PyMember_GetOne(const char* a, struct PyMemberDef* b) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyMember_SetOne
PyAPI_FUNC(int) PyMember_SetOne(char* a, struct PyMemberDef* b, PyObject* c) {
    FUNC_NOT_IMPLEMENTED
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
#undef PyModule_ExecDef
PyAPI_FUNC(int) PyModule_ExecDef(PyObject* a, PyModuleDef* b) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyModule_FromDefAndSpec2
PyAPI_FUNC(PyObject*) PyModule_FromDefAndSpec2(PyModuleDef* a, PyObject* b, int c) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyModule_GetFilename
PyAPI_FUNC(const char*) PyModule_GetFilename(PyObject* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyModule_GetFilenameObject
PyAPI_FUNC(PyObject*) PyModule_GetFilenameObject(PyObject* a) {
    FUNC_NOT_IMPLEMENTED
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
#undef PyODict_DelItem
PyAPI_FUNC(int) PyODict_DelItem(PyObject* a, PyObject* b) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyODict_New
PyAPI_FUNC(PyObject*) PyODict_New() {
    FUNC_NOT_IMPLEMENTED
}
#undef PyODict_SetItem
PyAPI_FUNC(int) PyODict_SetItem(PyObject* a, PyObject* b, PyObject* c) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyOS_AfterFork
PyAPI_FUNC(void) PyOS_AfterFork() {
    FUNC_NOT_IMPLEMENTED
}
#undef PyOS_AfterFork_Child
PyAPI_FUNC(void) PyOS_AfterFork_Child() {
    FUNC_NOT_IMPLEMENTED
}
#undef PyOS_AfterFork_Parent
PyAPI_FUNC(void) PyOS_AfterFork_Parent() {
    FUNC_NOT_IMPLEMENTED
}
#undef PyOS_BeforeFork
PyAPI_FUNC(void) PyOS_BeforeFork() {
    FUNC_NOT_IMPLEMENTED
}
#undef PyOS_FSPath
PyAPI_FUNC(PyObject*) PyOS_FSPath(PyObject* a) {
    return GraalPyOS_FSPath(a);
}
#undef PyOS_InterruptOccurred
PyAPI_FUNC(int) PyOS_InterruptOccurred() {
    FUNC_NOT_IMPLEMENTED
}
#undef PyOS_Readline
PyAPI_FUNC(char*) PyOS_Readline(FILE* a, FILE* b, const char* c) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyOS_getsig
PyAPI_FUNC(PyOS_sighandler_t) PyOS_getsig(int a) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyOS_setsig
PyAPI_FUNC(PyOS_sighandler_t) PyOS_setsig(int a, PyOS_sighandler_t b) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyObject_ASCII
PyAPI_FUNC(PyObject*) PyObject_ASCII(PyObject* a) {
    return GraalPyObject_ASCII(a);
}
#undef PyObject_AsCharBuffer
PyAPI_FUNC(int) PyObject_AsCharBuffer(PyObject* a, const char** b, Py_ssize_t* c) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyObject_AsFileDescriptor
PyAPI_FUNC(int) PyObject_AsFileDescriptor(PyObject* a) {
    return GraalPyObject_AsFileDescriptor(a);
}
#undef PyObject_AsReadBuffer
PyAPI_FUNC(int) PyObject_AsReadBuffer(PyObject* a, const void** b, Py_ssize_t* c) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyObject_AsWriteBuffer
PyAPI_FUNC(int) PyObject_AsWriteBuffer(PyObject* a, void** b, Py_ssize_t* c) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyObject_Bytes
PyAPI_FUNC(PyObject*) PyObject_Bytes(PyObject* a) {
    return GraalPyObject_Bytes(a);
}
#undef PyObject_CallFinalizer
PyAPI_FUNC(void) PyObject_CallFinalizer(PyObject* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyObject_CallFinalizerFromDealloc
PyAPI_FUNC(int) PyObject_CallFinalizerFromDealloc(PyObject* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyObject_Calloc
PyAPI_FUNC(void*) PyObject_Calloc(size_t a, size_t b) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyObject_CheckReadBuffer
PyAPI_FUNC(int) PyObject_CheckReadBuffer(PyObject* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyObject_ClearWeakRefs
PyAPI_FUNC(void) PyObject_ClearWeakRefs(PyObject* a) {
    GraalPyObject_ClearWeakRefs(a);
}
#undef PyObject_CopyData
PyAPI_FUNC(int) PyObject_CopyData(PyObject* a, PyObject* b) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyObject_DelItem
PyAPI_FUNC(int) PyObject_DelItem(PyObject* a, PyObject* b) {
    return GraalPyObject_DelItem(a, b);
}
#undef PyObject_DelItemString
PyAPI_FUNC(int) PyObject_DelItemString(PyObject* a, const char* b) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyObject_Dir
PyAPI_FUNC(PyObject*) PyObject_Dir(PyObject* a) {
    return GraalPyObject_Dir(a);
}
#undef PyObject_Format
PyAPI_FUNC(PyObject*) PyObject_Format(PyObject* a, PyObject* b) {
    return GraalPyObject_Format(a, b);
}
#undef PyObject_GC_IsFinalized
PyAPI_FUNC(int) PyObject_GC_IsFinalized(PyObject* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyObject_GC_IsTracked
PyAPI_FUNC(int) PyObject_GC_IsTracked(PyObject* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyObject_GC_Track
PyAPI_FUNC(void) PyObject_GC_Track(void* a) {
    GraalPyObject_GC_Track(a);
}
#undef PyObject_GC_UnTrack
PyAPI_FUNC(void) PyObject_GC_UnTrack(void* a) {
    GraalPyObject_GC_UnTrack(a);
}
#undef PyObject_GET_WEAKREFS_LISTPTR
PyAPI_FUNC(PyObject**) PyObject_GET_WEAKREFS_LISTPTR(PyObject* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyObject_GetAIter
PyAPI_FUNC(PyObject*) PyObject_GetAIter(PyObject* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyObject_GetArenaAllocator
PyAPI_FUNC(void) PyObject_GetArenaAllocator(PyObjectArenaAllocator* a) {
    FUNC_NOT_IMPLEMENTED
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
#undef PyObject_IS_GC
PyAPI_FUNC(int) PyObject_IS_GC(PyObject* a) {
    FUNC_NOT_IMPLEMENTED
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
#undef PyObject_Length
PyAPI_FUNC(Py_ssize_t) PyObject_Length(PyObject* a) {
    FUNC_NOT_IMPLEMENTED
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
#undef PyObject_SetArenaAllocator
PyAPI_FUNC(void) PyObject_SetArenaAllocator(PyObjectArenaAllocator* a) {
    FUNC_NOT_IMPLEMENTED
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
#undef PyPickleBuffer_FromObject
PyAPI_FUNC(PyObject*) PyPickleBuffer_FromObject(PyObject* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyPickleBuffer_GetBuffer
PyAPI_FUNC(const Py_buffer*) PyPickleBuffer_GetBuffer(PyObject* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyPickleBuffer_Release
PyAPI_FUNC(int) PyPickleBuffer_Release(PyObject* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyPreConfig_InitIsolatedConfig
PyAPI_FUNC(void) PyPreConfig_InitIsolatedConfig(PyPreConfig* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyPreConfig_InitPythonConfig
PyAPI_FUNC(void) PyPreConfig_InitPythonConfig(PyPreConfig* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyRun_AnyFile
PyAPI_FUNC(int) PyRun_AnyFile(FILE* a, const char* b) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyRun_AnyFileEx
PyAPI_FUNC(int) PyRun_AnyFileEx(FILE* a, const char* b, int c) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyRun_AnyFileExFlags
PyAPI_FUNC(int) PyRun_AnyFileExFlags(FILE* a, const char* b, int c, PyCompilerFlags* d) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyRun_AnyFileFlags
PyAPI_FUNC(int) PyRun_AnyFileFlags(FILE* a, const char* b, PyCompilerFlags* c) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyRun_File
PyAPI_FUNC(PyObject*) PyRun_File(FILE* a, const char* b, int c, PyObject* d, PyObject* e) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyRun_FileEx
PyAPI_FUNC(PyObject*) PyRun_FileEx(FILE* a, const char* b, int c, PyObject* d, PyObject* e, int f) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyRun_FileExFlags
PyAPI_FUNC(PyObject*) PyRun_FileExFlags(FILE* a, const char* b, int c, PyObject* d, PyObject* e, int f, PyCompilerFlags* g) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyRun_FileFlags
PyAPI_FUNC(PyObject*) PyRun_FileFlags(FILE* a, const char* b, int c, PyObject* d, PyObject* e, PyCompilerFlags* f) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyRun_InteractiveLoop
PyAPI_FUNC(int) PyRun_InteractiveLoop(FILE* a, const char* b) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyRun_InteractiveLoopFlags
PyAPI_FUNC(int) PyRun_InteractiveLoopFlags(FILE* a, const char* b, PyCompilerFlags* c) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyRun_InteractiveOne
PyAPI_FUNC(int) PyRun_InteractiveOne(FILE* a, const char* b) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyRun_InteractiveOneFlags
PyAPI_FUNC(int) PyRun_InteractiveOneFlags(FILE* a, const char* b, PyCompilerFlags* c) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyRun_InteractiveOneObject
PyAPI_FUNC(int) PyRun_InteractiveOneObject(FILE* a, PyObject* b, PyCompilerFlags* c) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyRun_SimpleFile
PyAPI_FUNC(int) PyRun_SimpleFile(FILE* a, const char* b) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyRun_SimpleFileEx
PyAPI_FUNC(int) PyRun_SimpleFileEx(FILE* a, const char* b, int c) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyRun_SimpleFileExFlags
PyAPI_FUNC(int) PyRun_SimpleFileExFlags(FILE* a, const char* b, int c, PyCompilerFlags* d) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyRun_SimpleString
PyAPI_FUNC(int) PyRun_SimpleString(const char* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyRun_SimpleStringFlags
PyAPI_FUNC(int) PyRun_SimpleStringFlags(const char* a, PyCompilerFlags* b) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyRun_String
PyAPI_FUNC(PyObject*) PyRun_String(const char* a, int b, PyObject* c, PyObject* d) {
    FUNC_NOT_IMPLEMENTED
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
#undef PySequence_In
PyAPI_FUNC(int) PySequence_In(PyObject* a, PyObject* b) {
    FUNC_NOT_IMPLEMENTED
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
#undef PySignal_SetWakeupFd
PyAPI_FUNC(int) PySignal_SetWakeupFd(int a) {
    FUNC_NOT_IMPLEMENTED
}
#undef PySlice_GetIndices
PyAPI_FUNC(int) PySlice_GetIndices(PyObject* a, Py_ssize_t b, Py_ssize_t* c, Py_ssize_t* d, Py_ssize_t* e) {
    FUNC_NOT_IMPLEMENTED
}
#undef PySlice_GetIndicesEx
PyAPI_FUNC(int) PySlice_GetIndicesEx(PyObject* a, Py_ssize_t b, Py_ssize_t* c, Py_ssize_t* d, Py_ssize_t* e, Py_ssize_t* f) {
    FUNC_NOT_IMPLEMENTED
}
#undef PySlice_New
PyAPI_FUNC(PyObject*) PySlice_New(PyObject* a, PyObject* b, PyObject* c) {
    return GraalPySlice_New(a, b, c);
}
#undef PyStaticMethod_New
PyAPI_FUNC(PyObject*) PyStaticMethod_New(PyObject* a) {
    return GraalPyStaticMethod_New(a);
}
#undef PyStatus_Error
PyAPI_FUNC(PyStatus) PyStatus_Error(const char* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyStatus_Exception
PyAPI_FUNC(int) PyStatus_Exception(PyStatus a) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyStatus_Exit
PyAPI_FUNC(PyStatus) PyStatus_Exit(int a) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyStatus_IsError
PyAPI_FUNC(int) PyStatus_IsError(PyStatus a) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyStatus_IsExit
PyAPI_FUNC(int) PyStatus_IsExit(PyStatus a) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyStatus_NoMemory
PyAPI_FUNC(PyStatus) PyStatus_NoMemory() {
    FUNC_NOT_IMPLEMENTED
}
#undef PyStatus_Ok
PyAPI_FUNC(PyStatus) PyStatus_Ok() {
    FUNC_NOT_IMPLEMENTED
}
#undef PyStructSequence_New
PyAPI_FUNC(PyObject*) PyStructSequence_New(PyTypeObject* a) {
    return GraalPyStructSequence_New(a);
}
#undef PySys_AddAuditHook
PyAPI_FUNC(int) PySys_AddAuditHook(Py_AuditHookFunction a, void* b) {
    FUNC_NOT_IMPLEMENTED
}
#undef PySys_AddWarnOption
PyAPI_FUNC(void) PySys_AddWarnOption(const wchar_t* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef PySys_AddWarnOptionUnicode
PyAPI_FUNC(void) PySys_AddWarnOptionUnicode(PyObject* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef PySys_AddXOption
PyAPI_FUNC(void) PySys_AddXOption(const wchar_t* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef PySys_FormatStderr
PyAPI_FUNC(void) PySys_FormatStderr(const char* a, ...) {
    FUNC_NOT_IMPLEMENTED
}
#undef PySys_FormatStdout
PyAPI_FUNC(void) PySys_FormatStdout(const char* a, ...) {
    FUNC_NOT_IMPLEMENTED
}
#undef PySys_GetObject
PyAPI_FUNC(PyObject*) PySys_GetObject(const char* a) {
    return GraalPySys_GetObject(a);
}
#undef PySys_GetXOptions
PyAPI_FUNC(PyObject*) PySys_GetXOptions() {
    FUNC_NOT_IMPLEMENTED
}
#undef PySys_HasWarnOptions
PyAPI_FUNC(int) PySys_HasWarnOptions() {
    FUNC_NOT_IMPLEMENTED
}
#undef PySys_ResetWarnOptions
PyAPI_FUNC(void) PySys_ResetWarnOptions() {
    FUNC_NOT_IMPLEMENTED
}
#undef PySys_SetArgv
PyAPI_FUNC(void) PySys_SetArgv(int a, wchar_t** b) {
    FUNC_NOT_IMPLEMENTED
}
#undef PySys_SetArgvEx
PyAPI_FUNC(void) PySys_SetArgvEx(int a, wchar_t** b, int c) {
    FUNC_NOT_IMPLEMENTED
}
#undef PySys_SetObject
PyAPI_FUNC(int) PySys_SetObject(const char* a, PyObject* b) {
    FUNC_NOT_IMPLEMENTED
}
#undef PySys_SetPath
PyAPI_FUNC(void) PySys_SetPath(const wchar_t* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef PySys_WriteStderr
PyAPI_FUNC(void) PySys_WriteStderr(const char* a, ...) {
    FUNC_NOT_IMPLEMENTED
}
#undef PySys_WriteStdout
PyAPI_FUNC(void) PySys_WriteStdout(const char* a, ...) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyThreadState_Delete
PyAPI_FUNC(void) PyThreadState_Delete(PyThreadState* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyThreadState_Get
PyAPI_FUNC(PyThreadState*) PyThreadState_Get() {
    return GraalPyThreadState_Get();
}
#undef PyThreadState_GetDict
PyAPI_FUNC(PyObject*) PyThreadState_GetDict() {
    return GraalPyThreadState_GetDict();
}
#undef PyThreadState_GetFrame
PyAPI_FUNC(PyFrameObject*) PyThreadState_GetFrame(PyThreadState* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyThreadState_GetID
PyAPI_FUNC(uint64_t) PyThreadState_GetID(PyThreadState* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyThreadState_GetInterpreter
PyAPI_FUNC(PyInterpreterState*) PyThreadState_GetInterpreter(PyThreadState* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyThreadState_New
PyAPI_FUNC(PyThreadState*) PyThreadState_New(PyInterpreterState* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyThreadState_Next
PyAPI_FUNC(PyThreadState*) PyThreadState_Next(PyThreadState* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyThreadState_SetAsyncExc
PyAPI_FUNC(int) PyThreadState_SetAsyncExc(unsigned long a, PyObject* b) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyThreadState_Swap
PyAPI_FUNC(PyThreadState*) PyThreadState_Swap(PyThreadState* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyThread_GetInfo
PyAPI_FUNC(PyObject*) PyThread_GetInfo() {
    FUNC_NOT_IMPLEMENTED
}
#undef PyThread_ReInitTLS
PyAPI_FUNC(void) PyThread_ReInitTLS() {
    FUNC_NOT_IMPLEMENTED
}
#undef PyThread_acquire_lock
PyAPI_FUNC(int) PyThread_acquire_lock(PyThread_type_lock a, int b) {
    return GraalPyThread_acquire_lock(a, b);
}
#undef PyThread_acquire_lock_timed
PyAPI_FUNC(PyLockStatus) PyThread_acquire_lock_timed(PyThread_type_lock a, long long b, int c) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyThread_allocate_lock
PyAPI_FUNC(PyThread_type_lock) PyThread_allocate_lock() {
    return GraalPyThread_allocate_lock();
}
#undef PyThread_create_key
PyAPI_FUNC(int) PyThread_create_key() {
    FUNC_NOT_IMPLEMENTED
}
#undef PyThread_delete_key
PyAPI_FUNC(void) PyThread_delete_key(int a) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyThread_delete_key_value
PyAPI_FUNC(void) PyThread_delete_key_value(int a) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyThread_exit_thread
PyAPI_FUNC(void) PyThread_exit_thread() {
    FUNC_NOT_IMPLEMENTED
}
#undef PyThread_get_key_value
PyAPI_FUNC(void*) PyThread_get_key_value(int a) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyThread_get_stacksize
PyAPI_FUNC(size_t) PyThread_get_stacksize() {
    FUNC_NOT_IMPLEMENTED
}
#undef PyThread_get_thread_ident
PyAPI_FUNC(unsigned long) PyThread_get_thread_ident() {
    return GraalPyThread_get_thread_ident();
}
#undef PyThread_get_thread_native_id
PyAPI_FUNC(unsigned long) PyThread_get_thread_native_id() {
    FUNC_NOT_IMPLEMENTED
}
#undef PyThread_init_thread
PyAPI_FUNC(void) PyThread_init_thread() {
    FUNC_NOT_IMPLEMENTED
}
#undef PyThread_release_lock
PyAPI_FUNC(void) PyThread_release_lock(PyThread_type_lock a) {
    GraalPyThread_release_lock(a);
}
#undef PyThread_set_key_value
PyAPI_FUNC(int) PyThread_set_key_value(int a, void* b) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyThread_set_stacksize
PyAPI_FUNC(int) PyThread_set_stacksize(size_t a) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyThread_start_new_thread
PyAPI_FUNC(unsigned long) PyThread_start_new_thread(void (*a)(void*), void* b) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyTraceBack_Here
PyAPI_FUNC(int) PyTraceBack_Here(PyFrameObject* a) {
    return GraalPyTraceBack_Here(a);
}
#undef PyTraceBack_Print
PyAPI_FUNC(int) PyTraceBack_Print(PyObject* a, PyObject* b) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyTraceMalloc_Track
PyAPI_FUNC(int) PyTraceMalloc_Track(unsigned int a, uintptr_t b, size_t c) {
    return GraalPyTraceMalloc_Track(a, b, c);
}
#undef PyTraceMalloc_Untrack
PyAPI_FUNC(int) PyTraceMalloc_Untrack(unsigned int a, uintptr_t b) {
    return GraalPyTraceMalloc_Untrack(a, b);
}
#undef PyTruffleFrame_New
PyAPI_FUNC(PyFrameObject*) PyTruffleFrame_New(PyThreadState* a, PyCodeObject* b, PyObject* c, PyObject* d) {
    FUNC_NOT_IMPLEMENTED
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
#undef PyTruffle_SeqIter_New
PyAPI_FUNC(PyObject*) PyTruffle_SeqIter_New(PyObject* a) {
    FUNC_NOT_IMPLEMENTED
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
#undef PyType_ClearCache
PyAPI_FUNC(unsigned int) PyType_ClearCache() {
    FUNC_NOT_IMPLEMENTED
}
#undef PyType_IsSubtype
PyAPI_FUNC(int) PyType_IsSubtype(PyTypeObject* a, PyTypeObject* b) {
    return GraalPyType_IsSubtype(a, b);
}
#undef PyUnicodeDecodeError_Create
PyAPI_FUNC(PyObject*) PyUnicodeDecodeError_Create(const char* a, const char* b, Py_ssize_t c, Py_ssize_t d, Py_ssize_t e, const char* f) {
    return GraalPyUnicodeDecodeError_Create(a, b, c, d, e, f);
}
#undef PyUnicodeDecodeError_GetEncoding
PyAPI_FUNC(PyObject*) PyUnicodeDecodeError_GetEncoding(PyObject* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyUnicodeDecodeError_GetEnd
PyAPI_FUNC(int) PyUnicodeDecodeError_GetEnd(PyObject* a, Py_ssize_t* b) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyUnicodeDecodeError_GetObject
PyAPI_FUNC(PyObject*) PyUnicodeDecodeError_GetObject(PyObject* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyUnicodeDecodeError_GetReason
PyAPI_FUNC(PyObject*) PyUnicodeDecodeError_GetReason(PyObject* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyUnicodeDecodeError_GetStart
PyAPI_FUNC(int) PyUnicodeDecodeError_GetStart(PyObject* a, Py_ssize_t* b) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyUnicodeDecodeError_SetEnd
PyAPI_FUNC(int) PyUnicodeDecodeError_SetEnd(PyObject* a, Py_ssize_t b) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyUnicodeDecodeError_SetReason
PyAPI_FUNC(int) PyUnicodeDecodeError_SetReason(PyObject* a, const char* b) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyUnicodeDecodeError_SetStart
PyAPI_FUNC(int) PyUnicodeDecodeError_SetStart(PyObject* a, Py_ssize_t b) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyUnicodeEncodeError_Create
PyAPI_FUNC(PyObject*) PyUnicodeEncodeError_Create(const char* a, const Py_UNICODE* b, Py_ssize_t c, Py_ssize_t d, Py_ssize_t e, const char* f) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyUnicodeEncodeError_GetEncoding
PyAPI_FUNC(PyObject*) PyUnicodeEncodeError_GetEncoding(PyObject* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyUnicodeEncodeError_GetEnd
PyAPI_FUNC(int) PyUnicodeEncodeError_GetEnd(PyObject* a, Py_ssize_t* b) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyUnicodeEncodeError_GetObject
PyAPI_FUNC(PyObject*) PyUnicodeEncodeError_GetObject(PyObject* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyUnicodeEncodeError_GetReason
PyAPI_FUNC(PyObject*) PyUnicodeEncodeError_GetReason(PyObject* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyUnicodeEncodeError_GetStart
PyAPI_FUNC(int) PyUnicodeEncodeError_GetStart(PyObject* a, Py_ssize_t* b) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyUnicodeEncodeError_SetEnd
PyAPI_FUNC(int) PyUnicodeEncodeError_SetEnd(PyObject* a, Py_ssize_t b) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyUnicodeEncodeError_SetReason
PyAPI_FUNC(int) PyUnicodeEncodeError_SetReason(PyObject* a, const char* b) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyUnicodeEncodeError_SetStart
PyAPI_FUNC(int) PyUnicodeEncodeError_SetStart(PyObject* a, Py_ssize_t b) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyUnicodeTranslateError_Create
PyAPI_FUNC(PyObject*) PyUnicodeTranslateError_Create(const Py_UNICODE* a, Py_ssize_t b, Py_ssize_t c, Py_ssize_t d, const char* e) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyUnicodeTranslateError_GetEnd
PyAPI_FUNC(int) PyUnicodeTranslateError_GetEnd(PyObject* a, Py_ssize_t* b) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyUnicodeTranslateError_GetObject
PyAPI_FUNC(PyObject*) PyUnicodeTranslateError_GetObject(PyObject* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyUnicodeTranslateError_GetReason
PyAPI_FUNC(PyObject*) PyUnicodeTranslateError_GetReason(PyObject* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyUnicodeTranslateError_GetStart
PyAPI_FUNC(int) PyUnicodeTranslateError_GetStart(PyObject* a, Py_ssize_t* b) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyUnicodeTranslateError_SetEnd
PyAPI_FUNC(int) PyUnicodeTranslateError_SetEnd(PyObject* a, Py_ssize_t b) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyUnicodeTranslateError_SetReason
PyAPI_FUNC(int) PyUnicodeTranslateError_SetReason(PyObject* a, const char* b) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyUnicodeTranslateError_SetStart
PyAPI_FUNC(int) PyUnicodeTranslateError_SetStart(PyObject* a, Py_ssize_t b) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyUnicode_AsCharmapString
PyAPI_FUNC(PyObject*) PyUnicode_AsCharmapString(PyObject* a, PyObject* b) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyUnicode_AsDecodedObject
PyAPI_FUNC(PyObject*) PyUnicode_AsDecodedObject(PyObject* a, const char* b, const char* c) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyUnicode_AsDecodedUnicode
PyAPI_FUNC(PyObject*) PyUnicode_AsDecodedUnicode(PyObject* a, const char* b, const char* c) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyUnicode_AsEncodedObject
PyAPI_FUNC(PyObject*) PyUnicode_AsEncodedObject(PyObject* a, const char* b, const char* c) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyUnicode_AsEncodedString
PyAPI_FUNC(PyObject*) PyUnicode_AsEncodedString(PyObject* a, const char* b, const char* c) {
    return GraalPyUnicode_AsEncodedString(a, b, c);
}
#undef PyUnicode_AsEncodedUnicode
PyAPI_FUNC(PyObject*) PyUnicode_AsEncodedUnicode(PyObject* a, const char* b, const char* c) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyUnicode_AsRawUnicodeEscapeString
PyAPI_FUNC(PyObject*) PyUnicode_AsRawUnicodeEscapeString(PyObject* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyUnicode_AsUTF16String
PyAPI_FUNC(PyObject*) PyUnicode_AsUTF16String(PyObject* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyUnicode_AsUTF32String
PyAPI_FUNC(PyObject*) PyUnicode_AsUTF32String(PyObject* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyUnicode_AsUnicodeEscapeString
PyAPI_FUNC(PyObject*) PyUnicode_AsUnicodeEscapeString(PyObject* a) {
    return GraalPyUnicode_AsUnicodeEscapeString(a);
}
#undef PyUnicode_AsWideCharString
PyAPI_FUNC(wchar_t*) PyUnicode_AsWideCharString(PyObject* a, Py_ssize_t* b) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyUnicode_BuildEncodingMap
PyAPI_FUNC(PyObject*) PyUnicode_BuildEncodingMap(PyObject* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyUnicode_Compare
PyAPI_FUNC(int) PyUnicode_Compare(PyObject* a, PyObject* b) {
    return GraalPyUnicode_Compare(a, b);
}
#undef PyUnicode_CompareWithASCIIString
PyAPI_FUNC(int) PyUnicode_CompareWithASCIIString(PyObject* a, const char* b) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyUnicode_Concat
PyAPI_FUNC(PyObject*) PyUnicode_Concat(PyObject* a, PyObject* b) {
    return GraalPyUnicode_Concat(a, b);
}
#undef PyUnicode_Contains
PyAPI_FUNC(int) PyUnicode_Contains(PyObject* a, PyObject* b) {
    return GraalPyUnicode_Contains(a, b);
}
#undef PyUnicode_CopyCharacters
PyAPI_FUNC(Py_ssize_t) PyUnicode_CopyCharacters(PyObject* a, Py_ssize_t b, PyObject* c, Py_ssize_t d, Py_ssize_t e) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyUnicode_Count
PyAPI_FUNC(Py_ssize_t) PyUnicode_Count(PyObject* a, PyObject* b, Py_ssize_t c, Py_ssize_t d) {
    return GraalPyUnicode_Count(a, b, c, d);
}
#undef PyUnicode_DecodeCharmap
PyAPI_FUNC(PyObject*) PyUnicode_DecodeCharmap(const char* a, Py_ssize_t b, PyObject* c, const char* d) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyUnicode_DecodeFSDefault
PyAPI_FUNC(PyObject*) PyUnicode_DecodeFSDefault(const char* a) {
    return GraalPyUnicode_DecodeFSDefault(a);
}
#undef PyUnicode_DecodeLocale
PyAPI_FUNC(PyObject*) PyUnicode_DecodeLocale(const char* a, const char* b) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyUnicode_DecodeLocaleAndSize
PyAPI_FUNC(PyObject*) PyUnicode_DecodeLocaleAndSize(const char* a, Py_ssize_t b, const char* c) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyUnicode_DecodeRawUnicodeEscape
PyAPI_FUNC(PyObject*) PyUnicode_DecodeRawUnicodeEscape(const char* a, Py_ssize_t b, const char* c) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyUnicode_DecodeUTF7
PyAPI_FUNC(PyObject*) PyUnicode_DecodeUTF7(const char* a, Py_ssize_t b, const char* c) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyUnicode_DecodeUTF7Stateful
PyAPI_FUNC(PyObject*) PyUnicode_DecodeUTF7Stateful(const char* a, Py_ssize_t b, const char* c, Py_ssize_t* d) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyUnicode_DecodeUnicodeEscape
PyAPI_FUNC(PyObject*) PyUnicode_DecodeUnicodeEscape(const char* a, Py_ssize_t b, const char* c) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyUnicode_Encode
PyAPI_FUNC(PyObject*) PyUnicode_Encode(const Py_UNICODE* a, Py_ssize_t b, const char* c, const char* d) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyUnicode_EncodeASCII
PyAPI_FUNC(PyObject*) PyUnicode_EncodeASCII(const Py_UNICODE* a, Py_ssize_t b, const char* c) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyUnicode_EncodeCharmap
PyAPI_FUNC(PyObject*) PyUnicode_EncodeCharmap(const Py_UNICODE* a, Py_ssize_t b, PyObject* c, const char* d) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyUnicode_EncodeDecimal
PyAPI_FUNC(int) PyUnicode_EncodeDecimal(Py_UNICODE* a, Py_ssize_t b, char* c, const char* d) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyUnicode_EncodeFSDefault
PyAPI_FUNC(PyObject*) PyUnicode_EncodeFSDefault(PyObject* a) {
    return GraalPyUnicode_EncodeFSDefault(a);
}
#undef PyUnicode_EncodeLatin1
PyAPI_FUNC(PyObject*) PyUnicode_EncodeLatin1(const Py_UNICODE* a, Py_ssize_t b, const char* c) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyUnicode_EncodeLocale
PyAPI_FUNC(PyObject*) PyUnicode_EncodeLocale(PyObject* a, const char* b) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyUnicode_EncodeRawUnicodeEscape
PyAPI_FUNC(PyObject*) PyUnicode_EncodeRawUnicodeEscape(const Py_UNICODE* a, Py_ssize_t b) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyUnicode_EncodeUTF16
PyAPI_FUNC(PyObject*) PyUnicode_EncodeUTF16(const Py_UNICODE* a, Py_ssize_t b, const char* c, int d) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyUnicode_EncodeUTF32
PyAPI_FUNC(PyObject*) PyUnicode_EncodeUTF32(const Py_UNICODE* a, Py_ssize_t b, const char* c, int d) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyUnicode_EncodeUTF7
PyAPI_FUNC(PyObject*) PyUnicode_EncodeUTF7(const Py_UNICODE* a, Py_ssize_t b, int c, int d, const char* e) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyUnicode_EncodeUTF8
PyAPI_FUNC(PyObject*) PyUnicode_EncodeUTF8(const Py_UNICODE* a, Py_ssize_t b, const char* c) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyUnicode_EncodeUnicodeEscape
PyAPI_FUNC(PyObject*) PyUnicode_EncodeUnicodeEscape(const Py_UNICODE* a, Py_ssize_t b) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyUnicode_FSDecoder
PyAPI_FUNC(int) PyUnicode_FSDecoder(PyObject* a, void* b) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyUnicode_Fill
PyAPI_FUNC(Py_ssize_t) PyUnicode_Fill(PyObject* a, Py_ssize_t b, Py_ssize_t c, Py_UCS4 d) {
    FUNC_NOT_IMPLEMENTED
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
#undef PyUnicode_FromWideChar
PyAPI_FUNC(PyObject*) PyUnicode_FromWideChar(const wchar_t* a, Py_ssize_t b) {
    return GraalPyUnicode_FromWideChar(a, b);
}
#undef PyUnicode_GetDefaultEncoding
PyAPI_FUNC(const char*) PyUnicode_GetDefaultEncoding() {
    FUNC_NOT_IMPLEMENTED
}
#undef PyUnicode_GetSize
PyAPI_FUNC(Py_ssize_t) PyUnicode_GetSize(PyObject* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyUnicode_InternImmortal
PyAPI_FUNC(void) PyUnicode_InternImmortal(PyObject** a) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyUnicode_IsIdentifier
PyAPI_FUNC(int) PyUnicode_IsIdentifier(PyObject* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyUnicode_Join
PyAPI_FUNC(PyObject*) PyUnicode_Join(PyObject* a, PyObject* b) {
    return GraalPyUnicode_Join(a, b);
}
#undef PyUnicode_Partition
PyAPI_FUNC(PyObject*) PyUnicode_Partition(PyObject* a, PyObject* b) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyUnicode_RPartition
PyAPI_FUNC(PyObject*) PyUnicode_RPartition(PyObject* a, PyObject* b) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyUnicode_RSplit
PyAPI_FUNC(PyObject*) PyUnicode_RSplit(PyObject* a, PyObject* b, Py_ssize_t c) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyUnicode_ReadChar
PyAPI_FUNC(Py_UCS4) PyUnicode_ReadChar(PyObject* a, Py_ssize_t b) {
    return GraalPyUnicode_ReadChar(a, b);
}
#undef PyUnicode_Replace
PyAPI_FUNC(PyObject*) PyUnicode_Replace(PyObject* a, PyObject* b, PyObject* c, Py_ssize_t d) {
    return GraalPyUnicode_Replace(a, b, c, d);
}
#undef PyUnicode_Resize
PyAPI_FUNC(int) PyUnicode_Resize(PyObject** a, Py_ssize_t b) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyUnicode_RichCompare
PyAPI_FUNC(PyObject*) PyUnicode_RichCompare(PyObject* a, PyObject* b, int c) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyUnicode_Split
PyAPI_FUNC(PyObject*) PyUnicode_Split(PyObject* a, PyObject* b, Py_ssize_t c) {
    return GraalPyUnicode_Split(a, b, c);
}
#undef PyUnicode_Splitlines
PyAPI_FUNC(PyObject*) PyUnicode_Splitlines(PyObject* a, int b) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyUnicode_Substring
PyAPI_FUNC(PyObject*) PyUnicode_Substring(PyObject* a, Py_ssize_t b, Py_ssize_t c) {
    return GraalPyUnicode_Substring(a, b, c);
}
#undef PyUnicode_Tailmatch
PyAPI_FUNC(Py_ssize_t) PyUnicode_Tailmatch(PyObject* a, PyObject* b, Py_ssize_t c, Py_ssize_t d, int e) {
    return GraalPyUnicode_Tailmatch(a, b, c, d, e);
}
#undef PyUnicode_TransformDecimalToASCII
PyAPI_FUNC(PyObject*) PyUnicode_TransformDecimalToASCII(Py_UNICODE* a, Py_ssize_t b) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyUnicode_Translate
PyAPI_FUNC(PyObject*) PyUnicode_Translate(PyObject* a, PyObject* b, const char* c) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyUnicode_TranslateCharmap
PyAPI_FUNC(PyObject*) PyUnicode_TranslateCharmap(const Py_UNICODE* a, Py_ssize_t b, PyObject* c, const char* d) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyUnicode_WriteChar
PyAPI_FUNC(int) PyUnicode_WriteChar(PyObject* a, Py_ssize_t b, Py_UCS4 c) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyWeakref_GetObject
PyAPI_FUNC(PyObject*) PyWeakref_GetObject(PyObject* a) {
    return GraalPyWeakref_GetObject(a);
}
#undef PyWeakref_NewProxy
PyAPI_FUNC(PyObject*) PyWeakref_NewProxy(PyObject* a, PyObject* b) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyWeakref_NewRef
PyAPI_FUNC(PyObject*) PyWeakref_NewRef(PyObject* a, PyObject* b) {
    return GraalPyWeakref_NewRef(a, b);
}
#undef PyWideStringList_Append
PyAPI_FUNC(PyStatus) PyWideStringList_Append(PyWideStringList* a, const wchar_t* b) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyWideStringList_Insert
PyAPI_FUNC(PyStatus) PyWideStringList_Insert(PyWideStringList* a, Py_ssize_t b, const wchar_t* c) {
    FUNC_NOT_IMPLEMENTED
}
#undef PyWrapper_New
PyAPI_FUNC(PyObject*) PyWrapper_New(PyObject* a, PyObject* b) {
    FUNC_NOT_IMPLEMENTED
}
#undef Py_AddPendingCall
PyAPI_FUNC(int) Py_AddPendingCall(int (*a)(void*), void* b) {
    FUNC_NOT_IMPLEMENTED
}
#undef Py_AtExit
PyAPI_FUNC(int) Py_AtExit(void (*a)(void)) {
    return GraalPy_AtExit(a);
}
#undef Py_BytesMain
PyAPI_FUNC(int) Py_BytesMain(int a, char** b) {
    FUNC_NOT_IMPLEMENTED
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
#undef Py_DecodeLocale
PyAPI_FUNC(wchar_t*) Py_DecodeLocale(const char* a, size_t* b) {
    FUNC_NOT_IMPLEMENTED
}
#undef Py_EncodeLocale
PyAPI_FUNC(char*) Py_EncodeLocale(const wchar_t* a, size_t* b) {
    FUNC_NOT_IMPLEMENTED
}
#undef Py_EndInterpreter
PyAPI_FUNC(void) Py_EndInterpreter(PyThreadState* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef Py_EnterRecursiveCall
PyAPI_FUNC(int) Py_EnterRecursiveCall(const char* a) {
    return GraalPy_EnterRecursiveCall(a);
}
#undef Py_Exit
PyAPI_FUNC(void) Py_Exit(int a) {
    FUNC_NOT_IMPLEMENTED
}
#undef Py_ExitStatusException
PyAPI_FUNC(void) Py_ExitStatusException(PyStatus a) {
    FUNC_NOT_IMPLEMENTED
}
#undef Py_FatalError
PyAPI_FUNC(void) Py_FatalError(const char* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef Py_FdIsInteractive
PyAPI_FUNC(int) Py_FdIsInteractive(FILE* a, const char* b) {
    FUNC_NOT_IMPLEMENTED
}
#undef Py_Finalize
PyAPI_FUNC(void) Py_Finalize() {
    FUNC_NOT_IMPLEMENTED
}
#undef Py_FinalizeEx
PyAPI_FUNC(int) Py_FinalizeEx() {
    FUNC_NOT_IMPLEMENTED
}
#undef Py_FrozenMain
PyAPI_FUNC(int) Py_FrozenMain(int a, char** b) {
    FUNC_NOT_IMPLEMENTED
}
#undef Py_GenericAlias
PyAPI_FUNC(PyObject*) Py_GenericAlias(PyObject* a, PyObject* b) {
    return GraalPy_GenericAlias(a, b);
}
#undef Py_GetArgcArgv
PyAPI_FUNC(void) Py_GetArgcArgv(int* a, wchar_t*** b) {
    FUNC_NOT_IMPLEMENTED
}
#undef Py_GetCopyright
PyAPI_FUNC(const char*) Py_GetCopyright() {
    FUNC_NOT_IMPLEMENTED
}
#undef Py_GetExecPrefix
PyAPI_FUNC(wchar_t*) Py_GetExecPrefix() {
    FUNC_NOT_IMPLEMENTED
}
#undef Py_GetPath
PyAPI_FUNC(wchar_t*) Py_GetPath() {
    FUNC_NOT_IMPLEMENTED
}
#undef Py_GetPlatform
PyAPI_FUNC(const char*) Py_GetPlatform() {
    FUNC_NOT_IMPLEMENTED
}
#undef Py_GetPrefix
PyAPI_FUNC(wchar_t*) Py_GetPrefix() {
    FUNC_NOT_IMPLEMENTED
}
#undef Py_GetProgramFullPath
PyAPI_FUNC(wchar_t*) Py_GetProgramFullPath() {
    FUNC_NOT_IMPLEMENTED
}
#undef Py_GetProgramName
PyAPI_FUNC(wchar_t*) Py_GetProgramName() {
    FUNC_NOT_IMPLEMENTED
}
#undef Py_GetPythonHome
PyAPI_FUNC(wchar_t*) Py_GetPythonHome() {
    FUNC_NOT_IMPLEMENTED
}
#undef Py_GetRecursionLimit
PyAPI_FUNC(int) Py_GetRecursionLimit() {
    FUNC_NOT_IMPLEMENTED
}
#undef Py_Initialize
PyAPI_FUNC(void) Py_Initialize() {
    FUNC_NOT_IMPLEMENTED
}
#undef Py_InitializeEx
PyAPI_FUNC(void) Py_InitializeEx(int a) {
    FUNC_NOT_IMPLEMENTED
}
#undef Py_InitializeFromConfig
PyAPI_FUNC(PyStatus) Py_InitializeFromConfig(const PyConfig* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef Py_LeaveRecursiveCall
PyAPI_FUNC(void) Py_LeaveRecursiveCall() {
    GraalPy_LeaveRecursiveCall();
}
#undef Py_Main
PyAPI_FUNC(int) Py_Main(int a, wchar_t** b) {
    FUNC_NOT_IMPLEMENTED
}
#undef Py_MakePendingCalls
PyAPI_FUNC(int) Py_MakePendingCalls() {
    FUNC_NOT_IMPLEMENTED
}
#undef Py_NewInterpreter
PyAPI_FUNC(PyThreadState*) Py_NewInterpreter() {
    FUNC_NOT_IMPLEMENTED
}
#undef Py_PreInitialize
PyAPI_FUNC(PyStatus) Py_PreInitialize(const PyPreConfig* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef Py_PreInitializeFromArgs
PyAPI_FUNC(PyStatus) Py_PreInitializeFromArgs(const PyPreConfig* a, Py_ssize_t b, wchar_t** c) {
    FUNC_NOT_IMPLEMENTED
}
#undef Py_PreInitializeFromBytesArgs
PyAPI_FUNC(PyStatus) Py_PreInitializeFromBytesArgs(const PyPreConfig* a, Py_ssize_t b, char** c) {
    FUNC_NOT_IMPLEMENTED
}
#undef Py_ReprEnter
PyAPI_FUNC(int) Py_ReprEnter(PyObject* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef Py_ReprLeave
PyAPI_FUNC(void) Py_ReprLeave(PyObject* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef Py_RunMain
PyAPI_FUNC(int) Py_RunMain() {
    FUNC_NOT_IMPLEMENTED
}
#undef Py_SetPath
PyAPI_FUNC(void) Py_SetPath(const wchar_t* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef Py_SetProgramName
PyAPI_FUNC(void) Py_SetProgramName(const wchar_t* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef Py_SetPythonHome
PyAPI_FUNC(void) Py_SetPythonHome(const wchar_t* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef Py_SetRecursionLimit
PyAPI_FUNC(void) Py_SetRecursionLimit(int a) {
    FUNC_NOT_IMPLEMENTED
}
#undef Py_SetStandardStreamEncoding
PyAPI_FUNC(int) Py_SetStandardStreamEncoding(const char* a, const char* b) {
    FUNC_NOT_IMPLEMENTED
}
#undef Py_UniversalNewlineFgets
PyAPI_FUNC(char*) Py_UniversalNewlineFgets(char* a, int b, FILE* c, PyObject* d) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyArg_Fini
PyAPI_FUNC(void) _PyArg_Fini() {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyArg_NoKwnames
PyAPI_FUNC(int) _PyArg_NoKwnames(const char* a, PyObject* b) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyArg_ParseStack
PyAPI_FUNC(int) _PyArg_ParseStack(PyObject*const* a, Py_ssize_t b, const char* c, ...) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyArg_ParseStackAndKeywords
PyAPI_FUNC(int) _PyArg_ParseStackAndKeywords(PyObject*const* a, Py_ssize_t b, PyObject* c, struct _PyArg_Parser* d, ...) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyArg_ParseStackAndKeywords_SizeT
PyAPI_FUNC(int) _PyArg_ParseStackAndKeywords_SizeT(PyObject*const* a, Py_ssize_t b, PyObject* c, struct _PyArg_Parser* d, ...) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyArray_Data
PyAPI_FUNC(char*) _PyArray_Data(PyObject* a) {
    return Graal_PyArray_Data(a);
}
#undef _PyArray_Resize
PyAPI_FUNC(int) _PyArray_Resize(PyObject* a, Py_ssize_t b) {
    return Graal_PyArray_Resize(a, b);
}
#undef _PyAsyncGenValueWrapperNew
PyAPI_FUNC(PyObject*) _PyAsyncGenValueWrapperNew(PyObject* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyBytes_DecodeEscape
PyAPI_FUNC(PyObject*) _PyBytes_DecodeEscape(const char* a, Py_ssize_t b, const char* c, const char** d) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyBytes_FormatEx
PyAPI_FUNC(PyObject*) _PyBytes_FormatEx(const char* a, Py_ssize_t b, PyObject* c, int d) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyBytes_FromHex
PyAPI_FUNC(PyObject*) _PyBytes_FromHex(PyObject* a, int b) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyBytes_Join
PyAPI_FUNC(PyObject*) _PyBytes_Join(PyObject* a, PyObject* b) {
    return Graal_PyBytes_Join(a, b);
}
#undef _PyCode_CheckLineNumber
PyAPI_FUNC(int) _PyCode_CheckLineNumber(int a, PyCodeAddressRange* b) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyCode_ConstantKey
PyAPI_FUNC(PyObject*) _PyCode_ConstantKey(PyObject* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyCode_GetExtra
PyAPI_FUNC(int) _PyCode_GetExtra(PyObject* a, Py_ssize_t b, void** c) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyCode_InitAddressRange
PyAPI_FUNC(int) _PyCode_InitAddressRange(PyCodeObject* a, PyCodeAddressRange* b) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyCode_SetExtra
PyAPI_FUNC(int) _PyCode_SetExtra(PyObject* a, Py_ssize_t b, void* c) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyCodecInfo_GetIncrementalDecoder
PyAPI_FUNC(PyObject*) _PyCodecInfo_GetIncrementalDecoder(PyObject* a, const char* b) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyCodecInfo_GetIncrementalEncoder
PyAPI_FUNC(PyObject*) _PyCodecInfo_GetIncrementalEncoder(PyObject* a, const char* b) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyCodec_DecodeText
PyAPI_FUNC(PyObject*) _PyCodec_DecodeText(PyObject* a, const char* b, const char* c) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyCodec_EncodeText
PyAPI_FUNC(PyObject*) _PyCodec_EncodeText(PyObject* a, const char* b, const char* c) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyCodec_Forget
PyAPI_FUNC(int) _PyCodec_Forget(const char* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyCodec_Lookup
PyAPI_FUNC(PyObject*) _PyCodec_Lookup(const char* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyCodec_LookupTextEncoding
PyAPI_FUNC(PyObject*) _PyCodec_LookupTextEncoding(const char* a, const char* b) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyComplex_FormatAdvancedWriter
PyAPI_FUNC(int) _PyComplex_FormatAdvancedWriter(_PyUnicodeWriter* a, PyObject* b, PyObject* c, Py_ssize_t d, Py_ssize_t e) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyContext_NewHamtForTests
PyAPI_FUNC(PyObject*) _PyContext_NewHamtForTests() {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyCoro_GetAwaitableIter
PyAPI_FUNC(PyObject*) _PyCoro_GetAwaitableIter(PyObject* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyCrossInterpreterData_Lookup
PyAPI_FUNC(crossinterpdatafunc) _PyCrossInterpreterData_Lookup(PyObject* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyCrossInterpreterData_NewObject
PyAPI_FUNC(PyObject*) _PyCrossInterpreterData_NewObject(_PyCrossInterpreterData* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyCrossInterpreterData_RegisterClass
PyAPI_FUNC(int) _PyCrossInterpreterData_RegisterClass(PyTypeObject* a, crossinterpdatafunc b) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyCrossInterpreterData_Release
PyAPI_FUNC(void) _PyCrossInterpreterData_Release(_PyCrossInterpreterData* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyDebugAllocatorStats
PyAPI_FUNC(void) _PyDebugAllocatorStats(FILE* a, const char* b, int c, size_t d) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyDictView_Intersect
PyAPI_FUNC(PyObject*) _PyDictView_Intersect(PyObject* a, PyObject* b) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyDictView_New
PyAPI_FUNC(PyObject*) _PyDictView_New(PyObject* a, PyTypeObject* b) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyDict_Contains_KnownHash
PyAPI_FUNC(int) _PyDict_Contains_KnownHash(PyObject* a, PyObject* b, Py_hash_t c) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyDict_DebugMallocStats
PyAPI_FUNC(void) _PyDict_DebugMallocStats(FILE* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyDict_DelItemId
PyAPI_FUNC(int) _PyDict_DelItemId(PyObject* a, struct _Py_Identifier* b) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyDict_DelItemIf
PyAPI_FUNC(int) _PyDict_DelItemIf(PyObject* a, PyObject* b, int (*c)(PyObject*value)) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyDict_DelItem_KnownHash
PyAPI_FUNC(int) _PyDict_DelItem_KnownHash(PyObject* a, PyObject* b, Py_hash_t c) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyDict_FromKeys
PyAPI_FUNC(PyObject*) _PyDict_FromKeys(PyObject* a, PyObject* b, PyObject* c) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyDict_GetItemHint
PyAPI_FUNC(Py_ssize_t) _PyDict_GetItemHint(PyDictObject* a, PyObject* b, Py_ssize_t c, PyObject** d) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyDict_HasOnlyStringKeys
PyAPI_FUNC(int) _PyDict_HasOnlyStringKeys(PyObject* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyDict_KeysSize
PyAPI_FUNC(Py_ssize_t) _PyDict_KeysSize(PyDictKeysObject* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyDict_LoadGlobal
PyAPI_FUNC(PyObject*) _PyDict_LoadGlobal(PyDictObject* a, PyDictObject* b, PyObject* c) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyDict_MaybeUntrack
PyAPI_FUNC(void) _PyDict_MaybeUntrack(PyObject* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyDict_MergeEx
PyAPI_FUNC(int) _PyDict_MergeEx(PyObject* a, PyObject* b, int c) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyDict_NewKeysForClass
PyAPI_FUNC(PyDictKeysObject*) _PyDict_NewKeysForClass() {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyDict_Pop
PyAPI_FUNC(PyObject*) _PyDict_Pop(PyObject* a, PyObject* b, PyObject* c) {
    return Graal_PyDict_Pop(a, b, c);
}
#undef _PyDict_Pop_KnownHash
PyAPI_FUNC(PyObject*) _PyDict_Pop_KnownHash(PyObject* a, PyObject* b, Py_hash_t c, PyObject* d) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyDict_SetItem_KnownHash
PyAPI_FUNC(int) _PyDict_SetItem_KnownHash(PyObject* a, PyObject* b, PyObject* c, Py_hash_t d) {
    return Graal_PyDict_SetItem_KnownHash(a, b, c, d);
}
#undef _PyDict_SizeOf
PyAPI_FUNC(Py_ssize_t) _PyDict_SizeOf(PyDictObject* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyErr_BadInternalCall
PyAPI_FUNC(void) _PyErr_BadInternalCall(const char* a, int b) {
    Graal_PyErr_BadInternalCall(a, b);
}
#undef _PyErr_ChainExceptions
PyAPI_FUNC(void) _PyErr_ChainExceptions(PyObject* a, PyObject* b, PyObject* c) {
    Graal_PyErr_ChainExceptions(a, b, c);
}
#undef _PyErr_CheckSignals
PyAPI_FUNC(int) _PyErr_CheckSignals() {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyErr_GetExcInfo
PyAPI_FUNC(void) _PyErr_GetExcInfo(PyThreadState* a, PyObject** b, PyObject** c, PyObject** d) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyErr_GetTopmostException
PyAPI_FUNC(_PyErr_StackItem*) _PyErr_GetTopmostException(PyThreadState* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyErr_Occurred
PyAPI_FUNC(PyObject*) _PyErr_Occurred(PyThreadState* a) {
    return Graal_PyErr_Occurred(a);
}
#undef _PyErr_ProgramDecodedTextObject
PyAPI_FUNC(PyObject*) _PyErr_ProgramDecodedTextObject(PyObject* a, int b, const char* c) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyErr_SetKeyError
PyAPI_FUNC(void) _PyErr_SetKeyError(PyObject* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyErr_TrySetFromCause
PyAPI_FUNC(PyObject*) _PyErr_TrySetFromCause(const char* a, ...) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyErr_WarnUnawaitedCoroutine
PyAPI_FUNC(void) _PyErr_WarnUnawaitedCoroutine(PyObject* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyErr_WriteUnraisableMsg
PyAPI_FUNC(void) _PyErr_WriteUnraisableMsg(const char* a, PyObject* b) {
    Graal_PyErr_WriteUnraisableMsg(a, b);
}
#undef _PyEval_CallTracing
PyAPI_FUNC(PyObject*) _PyEval_CallTracing(PyObject* a, PyObject* b) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyEval_EvalFrameDefault
PyAPI_FUNC(PyObject*) _PyEval_EvalFrameDefault(PyThreadState* a, PyFrameObject* b, int c) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyEval_GetAsyncGenFinalizer
PyAPI_FUNC(PyObject*) _PyEval_GetAsyncGenFinalizer() {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyEval_GetAsyncGenFirstiter
PyAPI_FUNC(PyObject*) _PyEval_GetAsyncGenFirstiter() {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyEval_GetBuiltinId
PyAPI_FUNC(PyObject*) _PyEval_GetBuiltinId(_Py_Identifier* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyEval_GetCoroutineOriginTrackingDepth
PyAPI_FUNC(int) _PyEval_GetCoroutineOriginTrackingDepth() {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyEval_GetSwitchInterval
PyAPI_FUNC(unsigned long) _PyEval_GetSwitchInterval() {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyEval_RequestCodeExtraIndex
PyAPI_FUNC(Py_ssize_t) _PyEval_RequestCodeExtraIndex(freefunc a) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyEval_SetAsyncGenFinalizer
PyAPI_FUNC(int) _PyEval_SetAsyncGenFinalizer(PyObject* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyEval_SetAsyncGenFirstiter
PyAPI_FUNC(int) _PyEval_SetAsyncGenFirstiter(PyObject* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyEval_SetCoroutineOriginTrackingDepth
PyAPI_FUNC(void) _PyEval_SetCoroutineOriginTrackingDepth(int a) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyEval_SetProfile
PyAPI_FUNC(int) _PyEval_SetProfile(PyThreadState* a, Py_tracefunc b, PyObject* c) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyEval_SetSwitchInterval
PyAPI_FUNC(void) _PyEval_SetSwitchInterval(unsigned long a) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyEval_SetTrace
PyAPI_FUNC(int) _PyEval_SetTrace(PyThreadState* a, Py_tracefunc b, PyObject* c) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyEval_SliceIndexNotNone
PyAPI_FUNC(int) _PyEval_SliceIndexNotNone(PyObject* a, Py_ssize_t* b) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyFloat_DebugMallocStats
PyAPI_FUNC(void) _PyFloat_DebugMallocStats(FILE* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyFloat_FormatAdvancedWriter
PyAPI_FUNC(int) _PyFloat_FormatAdvancedWriter(_PyUnicodeWriter* a, PyObject* b, PyObject* c, Py_ssize_t d, Py_ssize_t e) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyFrame_DebugMallocStats
PyAPI_FUNC(void) _PyFrame_DebugMallocStats(FILE* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyFrame_New_NoTrack
PyAPI_FUNC(PyFrameObject*) _PyFrame_New_NoTrack(PyThreadState* a, PyFrameConstructor* b, PyObject* c) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyFunction_Vectorcall
PyAPI_FUNC(PyObject*) _PyFunction_Vectorcall(PyObject* a, PyObject*const* b, size_t c, PyObject* d) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyGILState_GetInterpreterStateUnsafe
PyAPI_FUNC(PyInterpreterState*) _PyGILState_GetInterpreterStateUnsafe() {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyImport_AcquireLock
PyAPI_FUNC(void) _PyImport_AcquireLock() {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyImport_FindExtensionObject
PyAPI_FUNC(PyObject*) _PyImport_FindExtensionObject(PyObject* a, PyObject* b) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyImport_FixupBuiltin
PyAPI_FUNC(int) _PyImport_FixupBuiltin(PyObject* a, const char* b, PyObject* c) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyImport_FixupExtensionObject
PyAPI_FUNC(int) _PyImport_FixupExtensionObject(PyObject* a, PyObject* b, PyObject* c, PyObject* d) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyImport_GetModuleAttr
PyAPI_FUNC(PyObject*) _PyImport_GetModuleAttr(PyObject* a, PyObject* b) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyImport_GetModuleAttrString
PyAPI_FUNC(PyObject*) _PyImport_GetModuleAttrString(const char* a, const char* b) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyImport_GetModuleId
PyAPI_FUNC(PyObject*) _PyImport_GetModuleId(struct _Py_Identifier* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyImport_IsInitialized
PyAPI_FUNC(int) _PyImport_IsInitialized(PyInterpreterState* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyImport_ReInitLock
PyAPI_FUNC(PyStatus) _PyImport_ReInitLock() {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyImport_ReleaseLock
PyAPI_FUNC(int) _PyImport_ReleaseLock() {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyImport_SetModuleString
PyAPI_FUNC(int) _PyImport_SetModuleString(const char* a, PyObject* b) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyInterpreterState_GetConfig
PyAPI_FUNC(const PyConfig*) _PyInterpreterState_GetConfig(PyInterpreterState* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyInterpreterState_GetConfigCopy
PyAPI_FUNC(int) _PyInterpreterState_GetConfigCopy(PyConfig* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyInterpreterState_GetEvalFrameFunc
PyAPI_FUNC(_PyFrameEvalFunction) _PyInterpreterState_GetEvalFrameFunc(PyInterpreterState* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyInterpreterState_GetMainModule
PyAPI_FUNC(PyObject*) _PyInterpreterState_GetMainModule(PyInterpreterState* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyInterpreterState_RequireIDRef
PyAPI_FUNC(void) _PyInterpreterState_RequireIDRef(PyInterpreterState* a, int b) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyInterpreterState_RequiresIDRef
PyAPI_FUNC(int) _PyInterpreterState_RequiresIDRef(PyInterpreterState* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyInterpreterState_SetConfig
PyAPI_FUNC(int) _PyInterpreterState_SetConfig(const PyConfig* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyInterpreterState_SetEvalFrameFunc
PyAPI_FUNC(void) _PyInterpreterState_SetEvalFrameFunc(PyInterpreterState* a, _PyFrameEvalFunction b) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyList_DebugMallocStats
PyAPI_FUNC(void) _PyList_DebugMallocStats(FILE* a) {
    FUNC_NOT_IMPLEMENTED
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
#undef _PyLong_AsTime_t
PyAPI_FUNC(time_t) _PyLong_AsTime_t(PyObject* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyLong_Copy
PyAPI_FUNC(PyObject*) _PyLong_Copy(PyLongObject* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyLong_DivmodNear
PyAPI_FUNC(PyObject*) _PyLong_DivmodNear(PyObject* a, PyObject* b) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyLong_FileDescriptor_Converter
PyAPI_FUNC(int) _PyLong_FileDescriptor_Converter(PyObject* a, void* b) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyLong_Format
PyAPI_FUNC(PyObject*) _PyLong_Format(PyObject* a, int b) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyLong_FormatAdvancedWriter
PyAPI_FUNC(int) _PyLong_FormatAdvancedWriter(_PyUnicodeWriter* a, PyObject* b, PyObject* c, Py_ssize_t d, Py_ssize_t e) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyLong_FormatBytesWriter
PyAPI_FUNC(char*) _PyLong_FormatBytesWriter(_PyBytesWriter* a, char* b, PyObject* c, int d, int e) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyLong_FormatWriter
PyAPI_FUNC(int) _PyLong_FormatWriter(_PyUnicodeWriter* a, PyObject* b, int c, int d) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyLong_Frexp
PyAPI_FUNC(double) _PyLong_Frexp(PyLongObject* a, Py_ssize_t* b) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyLong_FromByteArray
PyAPI_FUNC(PyObject*) _PyLong_FromByteArray(const unsigned char* a, size_t b, int c, int d) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyLong_FromBytes
PyAPI_FUNC(PyObject*) _PyLong_FromBytes(const char* a, Py_ssize_t b, int c) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyLong_GCD
PyAPI_FUNC(PyObject*) _PyLong_GCD(PyObject* a, PyObject* b) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyLong_Lshift
PyAPI_FUNC(PyObject*) _PyLong_Lshift(PyObject* a, size_t b) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyLong_New
PyAPI_FUNC(PyLongObject*) _PyLong_New(Py_ssize_t a) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyLong_NumBits
PyAPI_FUNC(size_t) _PyLong_NumBits(PyObject* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyLong_Rshift
PyAPI_FUNC(PyObject*) _PyLong_Rshift(PyObject* a, size_t b) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyLong_Sign
PyAPI_FUNC(int) _PyLong_Sign(PyObject* a) {
    return Graal_PyLong_Sign(a);
}
#undef _PyLong_Size_t_Converter
PyAPI_FUNC(int) _PyLong_Size_t_Converter(PyObject* a, void* b) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyLong_UnsignedInt_Converter
PyAPI_FUNC(int) _PyLong_UnsignedInt_Converter(PyObject* a, void* b) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyLong_UnsignedLongLong_Converter
PyAPI_FUNC(int) _PyLong_UnsignedLongLong_Converter(PyObject* a, void* b) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyLong_UnsignedLong_Converter
PyAPI_FUNC(int) _PyLong_UnsignedLong_Converter(PyObject* a, void* b) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyLong_UnsignedShort_Converter
PyAPI_FUNC(int) _PyLong_UnsignedShort_Converter(PyObject* a, void* b) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyMem_GetCurrentAllocatorName
PyAPI_FUNC(const char*) _PyMem_GetCurrentAllocatorName() {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyMem_RawStrdup
PyAPI_FUNC(char*) _PyMem_RawStrdup(const char* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyMem_RawWcsdup
PyAPI_FUNC(wchar_t*) _PyMem_RawWcsdup(const wchar_t* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyMem_Strdup
PyAPI_FUNC(char*) _PyMem_Strdup(const char* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyModuleSpec_IsInitializing
PyAPI_FUNC(int) _PyModuleSpec_IsInitializing(PyObject* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyModule_Clear
PyAPI_FUNC(void) _PyModule_Clear(PyObject* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyModule_ClearDict
PyAPI_FUNC(void) _PyModule_ClearDict(PyObject* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyNamespace_New
PyAPI_FUNC(PyObject*) _PyNamespace_New(PyObject* a) {
    return Graal_PyNamespace_New(a);
}
#undef _PyNumber_Index
PyAPI_FUNC(PyObject*) _PyNumber_Index(PyObject* a) {
    return Graal_PyNumber_Index(a);
}
#undef _PyOS_IsMainThread
PyAPI_FUNC(int) _PyOS_IsMainThread() {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyOS_URandom
PyAPI_FUNC(int) _PyOS_URandom(void* a, Py_ssize_t b) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyOS_URandomNonblock
PyAPI_FUNC(int) _PyOS_URandomNonblock(void* a, Py_ssize_t b) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyObjectDict_SetItem
PyAPI_FUNC(int) _PyObjectDict_SetItem(PyTypeObject* a, PyObject** b, PyObject* c, PyObject* d) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyObject_AssertFailed
PyAPI_FUNC(void) _PyObject_AssertFailed(PyObject* a, const char* b, const char* c, const char* d, int e, const char* f) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyObject_CallMethodId
PyAPI_FUNC(PyObject*) _PyObject_CallMethodId(PyObject* a, _Py_Identifier* b, const char* c, ...) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyObject_CallMethodId_SizeT
PyAPI_FUNC(PyObject*) _PyObject_CallMethodId_SizeT(PyObject* a, _Py_Identifier* b, const char* c, ...) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyObject_Call_Prepend
PyAPI_FUNC(PyObject*) _PyObject_Call_Prepend(PyThreadState* a, PyObject* b, PyObject* c, PyObject* d, PyObject* e) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyObject_CheckConsistency
PyAPI_FUNC(int) _PyObject_CheckConsistency(PyObject* a, int b) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyObject_CheckCrossInterpreterData
PyAPI_FUNC(int) _PyObject_CheckCrossInterpreterData(PyObject* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyObject_DebugMallocStats
PyAPI_FUNC(int) _PyObject_DebugMallocStats(FILE* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyObject_DebugTypeStats
PyAPI_FUNC(void) _PyObject_DebugTypeStats(FILE* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyObject_Dump
PyAPI_FUNC(void) _PyObject_Dump(PyObject* a) {
    Graal_PyObject_Dump(a);
}
#undef _PyObject_FunctionStr
PyAPI_FUNC(PyObject*) _PyObject_FunctionStr(PyObject* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyObject_GC_Resize
PyAPI_FUNC(PyVarObject*) _PyObject_GC_Resize(PyVarObject* a, Py_ssize_t b) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyObject_GenericGetAttrWithDict
PyAPI_FUNC(PyObject*) _PyObject_GenericGetAttrWithDict(PyObject* a, PyObject* b, PyObject* c, int d) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyObject_GenericSetAttrWithDict
PyAPI_FUNC(int) _PyObject_GenericSetAttrWithDict(PyObject* a, PyObject* b, PyObject* c, PyObject* d) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyObject_GetCrossInterpreterData
PyAPI_FUNC(int) _PyObject_GetCrossInterpreterData(PyObject* a, _PyCrossInterpreterData* b) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyObject_HasLen
PyAPI_FUNC(int) _PyObject_HasLen(PyObject* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyObject_IsAbstract
PyAPI_FUNC(int) _PyObject_IsAbstract(PyObject* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyObject_IsFreed
PyAPI_FUNC(int) _PyObject_IsFreed(PyObject* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyObject_LookupSpecial
PyAPI_FUNC(PyObject*) _PyObject_LookupSpecial(PyObject* a, _Py_Identifier* b) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyObject_MakeTpCall
PyAPI_FUNC(PyObject*) _PyObject_MakeTpCall(PyThreadState* a, PyObject* b, PyObject*const* c, Py_ssize_t d, PyObject* e) {
    return Graal_PyObject_MakeTpCall(a, b, c, d, e);
}
#undef _PyObject_RealIsInstance
PyAPI_FUNC(int) _PyObject_RealIsInstance(PyObject* a, PyObject* b) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyObject_RealIsSubclass
PyAPI_FUNC(int) _PyObject_RealIsSubclass(PyObject* a, PyObject* b) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyRun_AnyFileObject
PyAPI_FUNC(int) _PyRun_AnyFileObject(FILE* a, PyObject* b, int c, PyCompilerFlags* d) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyRun_InteractiveLoopObject
PyAPI_FUNC(int) _PyRun_InteractiveLoopObject(FILE* a, PyObject* b, PyCompilerFlags* c) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyRun_SimpleFileObject
PyAPI_FUNC(int) _PyRun_SimpleFileObject(FILE* a, PyObject* b, int c, PyCompilerFlags* d) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PySequence_BytesToCharpArray
PyAPI_FUNC(char*const*) _PySequence_BytesToCharpArray(PyObject* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PySequence_IterSearch
PyAPI_FUNC(Py_ssize_t) _PySequence_IterSearch(PyObject* a, PyObject* b, int c) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PySet_Update
PyAPI_FUNC(int) _PySet_Update(PyObject* a, PyObject* b) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PySignal_AfterFork
PyAPI_FUNC(void) _PySignal_AfterFork() {
    FUNC_NOT_IMPLEMENTED
}
#undef _PySlice_FromIndices
PyAPI_FUNC(PyObject*) _PySlice_FromIndices(Py_ssize_t a, Py_ssize_t b) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PySlice_GetLongIndices
PyAPI_FUNC(int) _PySlice_GetLongIndices(PySliceObject* a, PyObject* b, PyObject** c, PyObject** d, PyObject** e) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyStack_AsDict
PyAPI_FUNC(PyObject*) _PyStack_AsDict(PyObject*const* a, PyObject* b) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyState_AddModule
PyAPI_FUNC(int) _PyState_AddModule(PyThreadState* a, PyObject* b, struct PyModuleDef* c) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PySys_GetObjectId
PyAPI_FUNC(PyObject*) _PySys_GetObjectId(_Py_Identifier* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PySys_GetSizeOf
PyAPI_FUNC(size_t) _PySys_GetSizeOf(PyObject* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PySys_SetObjectId
PyAPI_FUNC(int) _PySys_SetObjectId(_Py_Identifier* a, PyObject* b) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyThreadState_GetDict
PyAPI_FUNC(PyObject*) _PyThreadState_GetDict(PyThreadState* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyThreadState_Prealloc
PyAPI_FUNC(PyThreadState*) _PyThreadState_Prealloc(PyInterpreterState* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyThread_CurrentExceptions
PyAPI_FUNC(PyObject*) _PyThread_CurrentExceptions() {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyThread_CurrentFrames
PyAPI_FUNC(PyObject*) _PyThread_CurrentFrames() {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyThread_at_fork_reinit
PyAPI_FUNC(int) _PyThread_at_fork_reinit(PyThread_type_lock* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyTime_AsMicroseconds
PyAPI_FUNC(_PyTime_t) _PyTime_AsMicroseconds(_PyTime_t a, _PyTime_round_t b) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyTime_AsMilliseconds
PyAPI_FUNC(_PyTime_t) _PyTime_AsMilliseconds(_PyTime_t a, _PyTime_round_t b) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyTime_AsNanosecondsObject
PyAPI_FUNC(PyObject*) _PyTime_AsNanosecondsObject(_PyTime_t a) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyTime_AsSecondsDouble
PyAPI_FUNC(double) _PyTime_AsSecondsDouble(_PyTime_t a) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyTime_AsTimespec
PyAPI_FUNC(int) _PyTime_AsTimespec(_PyTime_t a, struct timespec* b) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyTime_AsTimeval
PyAPI_FUNC(int) _PyTime_AsTimeval(_PyTime_t a, struct timeval* b, _PyTime_round_t c) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyTime_AsTimevalTime_t
PyAPI_FUNC(int) _PyTime_AsTimevalTime_t(_PyTime_t a, time_t* b, int* c, _PyTime_round_t d) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyTime_AsTimeval_noraise
PyAPI_FUNC(int) _PyTime_AsTimeval_noraise(_PyTime_t a, struct timeval* b, _PyTime_round_t c) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyTime_FromMillisecondsObject
PyAPI_FUNC(int) _PyTime_FromMillisecondsObject(_PyTime_t* a, PyObject* b, _PyTime_round_t c) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyTime_FromNanoseconds
PyAPI_FUNC(_PyTime_t) _PyTime_FromNanoseconds(_PyTime_t a) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyTime_FromNanosecondsObject
PyAPI_FUNC(int) _PyTime_FromNanosecondsObject(_PyTime_t* a, PyObject* b) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyTime_FromSeconds
PyAPI_FUNC(_PyTime_t) _PyTime_FromSeconds(int a) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyTime_FromSecondsObject
PyAPI_FUNC(int) _PyTime_FromSecondsObject(_PyTime_t* a, PyObject* b, _PyTime_round_t c) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyTime_FromTimespec
PyAPI_FUNC(int) _PyTime_FromTimespec(_PyTime_t* a, struct timespec* b) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyTime_FromTimeval
PyAPI_FUNC(int) _PyTime_FromTimeval(_PyTime_t* a, struct timeval* b) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyTime_GetMonotonicClock
PyAPI_FUNC(_PyTime_t) _PyTime_GetMonotonicClock() {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyTime_GetMonotonicClockWithInfo
PyAPI_FUNC(int) _PyTime_GetMonotonicClockWithInfo(_PyTime_t* a, _Py_clock_info_t* b) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyTime_GetPerfCounter
PyAPI_FUNC(_PyTime_t) _PyTime_GetPerfCounter() {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyTime_GetPerfCounterWithInfo
PyAPI_FUNC(int) _PyTime_GetPerfCounterWithInfo(_PyTime_t* a, _Py_clock_info_t* b) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyTime_GetSystemClock
PyAPI_FUNC(_PyTime_t) _PyTime_GetSystemClock() {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyTime_GetSystemClockWithInfo
PyAPI_FUNC(int) _PyTime_GetSystemClockWithInfo(_PyTime_t* a, _Py_clock_info_t* b) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyTime_MulDiv
PyAPI_FUNC(_PyTime_t) _PyTime_MulDiv(_PyTime_t a, _PyTime_t b, _PyTime_t c) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyTime_ObjectToTime_t
PyAPI_FUNC(int) _PyTime_ObjectToTime_t(PyObject* a, time_t* b, _PyTime_round_t c) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyTime_ObjectToTimespec
PyAPI_FUNC(int) _PyTime_ObjectToTimespec(PyObject* a, time_t* b, long* c, _PyTime_round_t d) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyTime_ObjectToTimeval
PyAPI_FUNC(int) _PyTime_ObjectToTimeval(PyObject* a, time_t* b, long* c, _PyTime_round_t d) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyTime_gmtime
PyAPI_FUNC(int) _PyTime_gmtime(time_t a, struct tm* b) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyTime_localtime
PyAPI_FUNC(int) _PyTime_localtime(time_t a, struct tm* b) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyTraceMalloc_GetTraceback
PyAPI_FUNC(PyObject*) _PyTraceMalloc_GetTraceback(unsigned int a, uintptr_t b) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyTraceMalloc_NewReference
PyAPI_FUNC(int) _PyTraceMalloc_NewReference(PyObject* a) {
    return Graal_PyTraceMalloc_NewReference(a);
}
#undef _PyTraceback_Add
PyAPI_FUNC(void) _PyTraceback_Add(const char* a, const char* b, int c) {
    Graal_PyTraceback_Add(a, b, c);
}
#undef _PyTrash_begin
PyAPI_FUNC(int) _PyTrash_begin(struct _ts* a, PyObject* b) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyTrash_cond
PyAPI_FUNC(int) _PyTrash_cond(PyObject* a, destructor b) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyTrash_deposit_object
PyAPI_FUNC(void) _PyTrash_deposit_object(PyObject* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyTrash_destroy_chain
PyAPI_FUNC(void) _PyTrash_destroy_chain() {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyTrash_end
PyAPI_FUNC(void) _PyTrash_end(struct _ts* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyTrash_thread_deposit_object
PyAPI_FUNC(void) _PyTrash_thread_deposit_object(PyObject* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyTrash_thread_destroy_chain
PyAPI_FUNC(void) _PyTrash_thread_destroy_chain() {
    FUNC_NOT_IMPLEMENTED
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
#undef _PyTuple_DebugMallocStats
PyAPI_FUNC(void) _PyTuple_DebugMallocStats(FILE* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyTuple_MaybeUntrack
PyAPI_FUNC(void) _PyTuple_MaybeUntrack(PyObject* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyTuple_Resize
PyAPI_FUNC(int) _PyTuple_Resize(PyObject** a, Py_ssize_t b) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyTuple_SET_ITEM
PyAPI_FUNC(int) _PyTuple_SET_ITEM(PyObject* a, Py_ssize_t b, PyObject* c) {
    return Graal_PyTuple_SET_ITEM(a, b, c);
}
#undef _PyType_CalculateMetaclass
PyAPI_FUNC(PyTypeObject*) _PyType_CalculateMetaclass(PyTypeObject* a, PyObject* b) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyType_GetDocFromInternalDoc
PyAPI_FUNC(PyObject*) _PyType_GetDocFromInternalDoc(const char* a, const char* b) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyType_GetTextSignatureFromInternalDoc
PyAPI_FUNC(PyObject*) _PyType_GetTextSignatureFromInternalDoc(const char* a, const char* b) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyType_Lookup
PyAPI_FUNC(PyObject*) _PyType_Lookup(PyTypeObject* a, PyObject* b) {
    return Graal_PyType_Lookup(a, b);
}
#undef _PyType_LookupId
PyAPI_FUNC(PyObject*) _PyType_LookupId(PyTypeObject* a, _Py_Identifier* b) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyUnicodeTranslateError_Create
PyAPI_FUNC(PyObject*) _PyUnicodeTranslateError_Create(PyObject* a, Py_ssize_t b, Py_ssize_t c, const char* d) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyUnicodeWriter_Dealloc
PyAPI_FUNC(void) _PyUnicodeWriter_Dealloc(_PyUnicodeWriter* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyUnicodeWriter_Finish
PyAPI_FUNC(PyObject*) _PyUnicodeWriter_Finish(_PyUnicodeWriter* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyUnicodeWriter_Init
PyAPI_FUNC(void) _PyUnicodeWriter_Init(_PyUnicodeWriter* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyUnicodeWriter_PrepareInternal
PyAPI_FUNC(int) _PyUnicodeWriter_PrepareInternal(_PyUnicodeWriter* a, Py_ssize_t b, Py_UCS4 c) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyUnicodeWriter_PrepareKindInternal
PyAPI_FUNC(int) _PyUnicodeWriter_PrepareKindInternal(_PyUnicodeWriter* a, enum PyUnicode_Kind b) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyUnicodeWriter_WriteASCIIString
PyAPI_FUNC(int) _PyUnicodeWriter_WriteASCIIString(_PyUnicodeWriter* a, const char* b, Py_ssize_t c) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyUnicodeWriter_WriteChar
PyAPI_FUNC(int) _PyUnicodeWriter_WriteChar(_PyUnicodeWriter* a, Py_UCS4 b) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyUnicodeWriter_WriteLatin1String
PyAPI_FUNC(int) _PyUnicodeWriter_WriteLatin1String(_PyUnicodeWriter* a, const char* b, Py_ssize_t c) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyUnicodeWriter_WriteStr
PyAPI_FUNC(int) _PyUnicodeWriter_WriteStr(_PyUnicodeWriter* a, PyObject* b) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyUnicodeWriter_WriteSubstring
PyAPI_FUNC(int) _PyUnicodeWriter_WriteSubstring(_PyUnicodeWriter* a, PyObject* b, Py_ssize_t c, Py_ssize_t d) {
    FUNC_NOT_IMPLEMENTED
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
#undef _PyUnicode_AsUnicode
PyAPI_FUNC(const Py_UNICODE*) _PyUnicode_AsUnicode(PyObject* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyUnicode_CheckConsistency
PyAPI_FUNC(int) _PyUnicode_CheckConsistency(PyObject* a, int b) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyUnicode_Copy
PyAPI_FUNC(PyObject*) _PyUnicode_Copy(PyObject* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyUnicode_DecodeRawUnicodeEscapeStateful
PyAPI_FUNC(PyObject*) _PyUnicode_DecodeRawUnicodeEscapeStateful(const char* a, Py_ssize_t b, const char* c, Py_ssize_t* d) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyUnicode_DecodeUnicodeEscapeInternal
PyAPI_FUNC(PyObject*) _PyUnicode_DecodeUnicodeEscapeInternal(const char* a, Py_ssize_t b, const char* c, Py_ssize_t* d, const char** e) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyUnicode_DecodeUnicodeEscapeStateful
PyAPI_FUNC(PyObject*) _PyUnicode_DecodeUnicodeEscapeStateful(const char* a, Py_ssize_t b, const char* c, Py_ssize_t* d) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyUnicode_EQ
PyAPI_FUNC(int) _PyUnicode_EQ(PyObject* a, PyObject* b) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyUnicode_EncodeCharmap
PyAPI_FUNC(PyObject*) _PyUnicode_EncodeCharmap(PyObject* a, PyObject* b, const char* c) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyUnicode_EncodeUTF16
PyAPI_FUNC(PyObject*) _PyUnicode_EncodeUTF16(PyObject* a, const char* b, int c) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyUnicode_EncodeUTF32
PyAPI_FUNC(PyObject*) _PyUnicode_EncodeUTF32(PyObject* a, const char* b, int c) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyUnicode_EncodeUTF7
PyAPI_FUNC(PyObject*) _PyUnicode_EncodeUTF7(PyObject* a, int b, int c, const char* d) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyUnicode_EqualToASCIIString
PyAPI_FUNC(int) _PyUnicode_EqualToASCIIString(PyObject* a, const char* b) {
    return Graal_PyUnicode_EqualToASCIIString(a, b);
}
#undef _PyUnicode_FastCopyCharacters
PyAPI_FUNC(void) _PyUnicode_FastCopyCharacters(PyObject* a, Py_ssize_t b, PyObject* c, Py_ssize_t d, Py_ssize_t e) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyUnicode_FastFill
PyAPI_FUNC(void) _PyUnicode_FastFill(PyObject* a, Py_ssize_t b, Py_ssize_t c, Py_UCS4 d) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyUnicode_FindMaxChar
PyAPI_FUNC(Py_UCS4) _PyUnicode_FindMaxChar(PyObject* a, Py_ssize_t b, Py_ssize_t c) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyUnicode_FormatAdvancedWriter
PyAPI_FUNC(int) _PyUnicode_FormatAdvancedWriter(_PyUnicodeWriter* a, PyObject* b, PyObject* c, Py_ssize_t d, Py_ssize_t e) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyUnicode_FormatLong
PyAPI_FUNC(PyObject*) _PyUnicode_FormatLong(PyObject* a, int b, int c, int d) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyUnicode_FromASCII
PyAPI_FUNC(PyObject*) _PyUnicode_FromASCII(const char* a, Py_ssize_t b) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyUnicode_InsertThousandsGrouping
PyAPI_FUNC(Py_ssize_t) _PyUnicode_InsertThousandsGrouping(_PyUnicodeWriter* a, Py_ssize_t b, PyObject* c, Py_ssize_t d, Py_ssize_t e, Py_ssize_t f, const char* g, PyObject* h, Py_UCS4* i) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyUnicode_JoinArray
PyAPI_FUNC(PyObject*) _PyUnicode_JoinArray(PyObject* a, PyObject*const* b, Py_ssize_t c) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyUnicode_ScanIdentifier
PyAPI_FUNC(Py_ssize_t) _PyUnicode_ScanIdentifier(PyObject* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyUnicode_TransformDecimalAndSpaceToASCII
PyAPI_FUNC(PyObject*) _PyUnicode_TransformDecimalAndSpaceToASCII(PyObject* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyUnicode_WideCharString_Converter
PyAPI_FUNC(int) _PyUnicode_WideCharString_Converter(PyObject* a, void* b) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyUnicode_WideCharString_Opt_Converter
PyAPI_FUNC(int) _PyUnicode_WideCharString_Opt_Converter(PyObject* a, void* b) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyUnicode_XStrip
PyAPI_FUNC(PyObject*) _PyUnicode_XStrip(PyObject* a, int b, PyObject* c) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyWarnings_Init
PyAPI_FUNC(PyObject*) _PyWarnings_Init() {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyWeakref_ClearRef
PyAPI_FUNC(void) _PyWeakref_ClearRef(PyWeakReference* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef _PyWeakref_GetWeakrefCount
PyAPI_FUNC(Py_ssize_t) _PyWeakref_GetWeakrefCount(PyWeakReference* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef _Py_BreakPoint
PyAPI_FUNC(void) _Py_BreakPoint() {
    FUNC_NOT_IMPLEMENTED
}
#undef _Py_CheckFunctionResult
PyAPI_FUNC(PyObject*) _Py_CheckFunctionResult(PyThreadState* a, PyObject* b, PyObject* c, const char* d) {
    FUNC_NOT_IMPLEMENTED
}
#undef _Py_CheckRecursiveCall
PyAPI_FUNC(int) _Py_CheckRecursiveCall(PyThreadState* a, const char* b) {
    FUNC_NOT_IMPLEMENTED
}
#undef _Py_CoerceLegacyLocale
PyAPI_FUNC(int) _Py_CoerceLegacyLocale(int a) {
    FUNC_NOT_IMPLEMENTED
}
#undef _Py_DecodeLocaleEx
PyAPI_FUNC(int) _Py_DecodeLocaleEx(const char* a, wchar_t** b, size_t* c, const char** d, int e, _Py_error_handler f) {
    FUNC_NOT_IMPLEMENTED
}
#undef _Py_DisplaySourceLine
PyAPI_FUNC(int) _Py_DisplaySourceLine(PyObject* a, PyObject* b, int c, int d) {
    FUNC_NOT_IMPLEMENTED
}
#undef _Py_EncodeLocaleEx
PyAPI_FUNC(int) _Py_EncodeLocaleEx(const wchar_t* a, char** b, size_t* c, const char** d, int e, _Py_error_handler f) {
    FUNC_NOT_IMPLEMENTED
}
#undef _Py_EncodeLocaleRaw
PyAPI_FUNC(char*) _Py_EncodeLocaleRaw(const wchar_t* a, size_t* b) {
    FUNC_NOT_IMPLEMENTED
}
#undef _Py_FatalErrorFormat
PyAPI_FUNC(void) _Py_FatalErrorFormat(const char* a, const char* b, ...) {
    FUNC_NOT_IMPLEMENTED
}
#undef _Py_FdIsInteractive
PyAPI_FUNC(int) _Py_FdIsInteractive(FILE* a, PyObject* b) {
    FUNC_NOT_IMPLEMENTED
}
#undef _Py_FreeCharPArray
PyAPI_FUNC(void) _Py_FreeCharPArray(char*const a[]) {
    FUNC_NOT_IMPLEMENTED
}
#undef _Py_GetAllocatedBlocks
PyAPI_FUNC(Py_ssize_t) _Py_GetAllocatedBlocks() {
    FUNC_NOT_IMPLEMENTED
}
#undef _Py_GetConfig
PyAPI_FUNC(const PyConfig*) _Py_GetConfig() {
    FUNC_NOT_IMPLEMENTED
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
#undef _Py_InitializeMain
PyAPI_FUNC(PyStatus) _Py_InitializeMain() {
    FUNC_NOT_IMPLEMENTED
}
#undef _Py_IsCoreInitialized
PyAPI_FUNC(int) _Py_IsCoreInitialized() {
    FUNC_NOT_IMPLEMENTED
}
#undef _Py_IsFinalizing
PyAPI_FUNC(int) _Py_IsFinalizing() {
    FUNC_NOT_IMPLEMENTED
}
#undef _Py_LegacyLocaleDetected
PyAPI_FUNC(int) _Py_LegacyLocaleDetected(int a) {
    FUNC_NOT_IMPLEMENTED
}
#undef _Py_Mangle
PyAPI_FUNC(PyObject*) _Py_Mangle(PyObject* a, PyObject* b) {
    FUNC_NOT_IMPLEMENTED
}
#undef _Py_NewInterpreter
PyAPI_FUNC(PyThreadState*) _Py_NewInterpreter(int a) {
    FUNC_NOT_IMPLEMENTED
}
#undef _Py_RestoreSignals
PyAPI_FUNC(void) _Py_RestoreSignals() {
    FUNC_NOT_IMPLEMENTED
}
#undef _Py_SetLocaleFromEnv
PyAPI_FUNC(char*) _Py_SetLocaleFromEnv(int a) {
    FUNC_NOT_IMPLEMENTED
}
#undef _Py_SetProgramFullPath
PyAPI_FUNC(void) _Py_SetProgramFullPath(const wchar_t* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef _Py_SourceAsString
PyAPI_FUNC(const char*) _Py_SourceAsString(PyObject* a, const char* b, const char* c, PyCompilerFlags* d, PyObject** e) {
    FUNC_NOT_IMPLEMENTED
}
#undef _Py_abspath
PyAPI_FUNC(int) _Py_abspath(const wchar_t* a, wchar_t** b) {
    FUNC_NOT_IMPLEMENTED
}
#undef _Py_add_one_to_index_C
PyAPI_FUNC(void) _Py_add_one_to_index_C(int a, Py_ssize_t* b, const Py_ssize_t* c) {
    FUNC_NOT_IMPLEMENTED
}
#undef _Py_add_one_to_index_F
PyAPI_FUNC(void) _Py_add_one_to_index_F(int a, Py_ssize_t* b, const Py_ssize_t* c) {
    FUNC_NOT_IMPLEMENTED
}
#undef _Py_convert_optional_to_ssize_t
PyAPI_FUNC(int) _Py_convert_optional_to_ssize_t(PyObject* a, void* b) {
    FUNC_NOT_IMPLEMENTED
}
#undef _Py_device_encoding
PyAPI_FUNC(PyObject*) _Py_device_encoding(int a) {
    FUNC_NOT_IMPLEMENTED
}
#undef _Py_dup
PyAPI_FUNC(int) _Py_dup(int a) {
    FUNC_NOT_IMPLEMENTED
}
#undef _Py_fopen_obj
PyAPI_FUNC(FILE*) _Py_fopen_obj(PyObject* a, const char* b) {
    FUNC_NOT_IMPLEMENTED
}
#undef _Py_fstat
PyAPI_FUNC(int) _Py_fstat(int a, struct _Py_stat_struct* b) {
    FUNC_NOT_IMPLEMENTED
}
#undef _Py_fstat_noraise
PyAPI_FUNC(int) _Py_fstat_noraise(int a, struct _Py_stat_struct* b) {
    FUNC_NOT_IMPLEMENTED
}
#undef _Py_get_blocking
PyAPI_FUNC(int) _Py_get_blocking(int a) {
    FUNC_NOT_IMPLEMENTED
}
#undef _Py_get_inheritable
PyAPI_FUNC(int) _Py_get_inheritable(int a) {
    FUNC_NOT_IMPLEMENTED
}
#undef _Py_isabs
PyAPI_FUNC(int) _Py_isabs(const wchar_t* a) {
    FUNC_NOT_IMPLEMENTED
}
#undef _Py_open
PyAPI_FUNC(int) _Py_open(const char* a, int b) {
    FUNC_NOT_IMPLEMENTED
}
#undef _Py_open_noraise
PyAPI_FUNC(int) _Py_open_noraise(const char* a, int b) {
    FUNC_NOT_IMPLEMENTED
}
#undef _Py_read
PyAPI_FUNC(Py_ssize_t) _Py_read(int a, void* b, size_t c) {
    FUNC_NOT_IMPLEMENTED
}
#undef _Py_set_blocking
PyAPI_FUNC(int) _Py_set_blocking(int a, int b) {
    FUNC_NOT_IMPLEMENTED
}
#undef _Py_set_inheritable
PyAPI_FUNC(int) _Py_set_inheritable(int a, int b, int* c) {
    FUNC_NOT_IMPLEMENTED
}
#undef _Py_set_inheritable_async_safe
PyAPI_FUNC(int) _Py_set_inheritable_async_safe(int a, int b, int* c) {
    FUNC_NOT_IMPLEMENTED
}
#undef _Py_stat
PyAPI_FUNC(int) _Py_stat(PyObject* a, struct stat* b) {
    FUNC_NOT_IMPLEMENTED
}
#undef _Py_wfopen
PyAPI_FUNC(FILE*) _Py_wfopen(const wchar_t* a, const wchar_t* b) {
    FUNC_NOT_IMPLEMENTED
}
#undef _Py_wgetcwd
PyAPI_FUNC(wchar_t*) _Py_wgetcwd(wchar_t* a, size_t b) {
    FUNC_NOT_IMPLEMENTED
}
#undef _Py_wreadlink
PyAPI_FUNC(int) _Py_wreadlink(const wchar_t* a, wchar_t* b, size_t c) {
    FUNC_NOT_IMPLEMENTED
}
#undef _Py_wrealpath
PyAPI_FUNC(wchar_t*) _Py_wrealpath(const wchar_t* a, wchar_t* b, size_t c) {
    FUNC_NOT_IMPLEMENTED
}
#undef _Py_write
PyAPI_FUNC(Py_ssize_t) _Py_write(int a, const void* b, size_t c) {
    FUNC_NOT_IMPLEMENTED
}
#undef _Py_write_noraise
PyAPI_FUNC(Py_ssize_t) _Py_write_noraise(int a, const void* b, size_t c) {
    FUNC_NOT_IMPLEMENTED
}
PyAPI_FUNC(int64_t*) PyTruffle_constants() {
    static int64_t constants[] = {
        (int64_t) PYLONG_BITS_IN_DIGIT,
        (int64_t) READONLY,
        (int64_t) CHAR_MIN,
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
        sizeof(long long),
        sizeof(Py_ssize_t),
        0xdead3333 // marker value
    };
    return sizes;
}
// {{end CAPI_BUILTINS}}
