/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "pycore_gc.h" // _PyGC_InitState

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

typedef struct {
    PyObject_HEAD
    /* The view exported by the original object */
    Py_buffer view;
    PyObject *weakreflist;
} PyPickleBufferObject;

// defined in 'unicodeobject.c'
void unicode_dealloc(PyObject *unicode);

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

#define PY_TRUFFLE_TYPE_EXTERN(GLOBAL_NAME, NAME)
#define PY_TRUFFLE_TYPE_UNIMPLEMENTED(GLOBAL_NAME) PyTypeObject GLOBAL_NAME;

PY_TYPE_OBJECTS

#undef PY_TRUFFLE_TYPE_GENERIC
#undef PY_TRUFFLE_TYPE_EXTERN
#undef PY_TRUFFLE_TYPE_UNIMPLEMENTED


#define PUBLIC_BUILTIN(NAME, RET, ...) RET (*GraalPyPrivate_Upcall_##NAME)(__VA_ARGS__);
#define PRIVATE_BUILTIN(NAME, RET, ...) RET (*NAME)(__VA_ARGS__);
CAPI_BUILTINS
#undef PUBLIC_BUILTIN
#undef PRIVATE_BUILTIN

static inline void initialize_builtins(void *builtin_closures[]) {
    int id = 0;
#define PUBLIC_BUILTIN(NAME, RET, ...) GraalPyPrivate_Upcall_##NAME = (RET(*)(__VA_ARGS__)) builtin_closures[id++];
#define PRIVATE_BUILTIN(NAME, RET, ...) NAME = (RET(*)(__VA_ARGS__)) builtin_closures[id++];
CAPI_BUILTINS
#undef PUBLIC_BUILTIN
#undef PRIVATE_BUILTIN
}

uint32_t Py_Truffle_Options;

#undef bool
static void initialize_builtin_types_and_structs() {
	clock_t t = clock();
    GraalPyPrivate_Log(PY_TRUFFLE_LOG_FINE, "initialize_builtin_types_and_structs...");
	static int64_t builtin_types[] = {
#define PY_TRUFFLE_TYPE_GENERIC(GLOBAL_NAME, __TYPE_NAME__, a, b, c, d, e, f, g) &GLOBAL_NAME, __TYPE_NAME__,
#define PY_TRUFFLE_TYPE_EXTERN(GLOBAL_NAME, __TYPE_NAME__) &GLOBAL_NAME, __TYPE_NAME__,
#define PY_TRUFFLE_TYPE_UNIMPLEMENTED(GLOBAL_NAME) // empty
    PY_TYPE_OBJECTS
#undef PY_TRUFFLE_TYPE_GENERIC
#undef PY_TRUFFLE_TYPE_EXTERN
#undef PY_TRUFFLE_TYPE_UNIMPLEMENTED
        NULL, NULL
	};

	GraalPyPrivate_InitBuiltinTypesAndStructs(builtin_types);

	// fix up for circular dependency:
	PyType_Type.tp_base = &PyBaseObject_Type;

	GraalPyPrivate_Log(PY_TRUFFLE_LOG_FINE, "initialize_builtin_types_and_structs: %fs", ((double) (clock() - t)) / CLOCKS_PER_SEC);
 }

int mmap_getbuffer(PyObject *self, Py_buffer *view, int flags) {
	// TODO(fa) readonly flag
    char* data = GraalPyPrivate_GetMMapData(self);
    if (!data) {
        return -1;
    }
    return PyBuffer_FillInfo(view, (PyObject*)self, data, PyObject_Size((PyObject *)self), 0, flags);
}

PyAPI_FUNC(void) GraalPyPrivate_MMap_InitBufferProtocol(PyObject* mmap_type) {
	GraalPyPrivate_Log(PY_TRUFFLE_LOG_FINE, "GraalPyPrivate_MMap_InitBufferProtocol");
	assert(PyType_Check(mmap_type));

	static PyBufferProcs mmap_as_buffer = {
	    (getbufferproc)mmap_getbuffer,
	    (releasebufferproc)NULL,
	};
	GraalPyPrivate_Type_SetBufferProcs((PyTypeObject *) mmap_type, &mmap_as_buffer);
	((PyTypeObject*) mmap_type)->tp_as_buffer = &mmap_as_buffer;
}

static int cdata_getbuffer(PyObject* type, Py_buffer* view, int flags) {
    return GraalPyPrivate_CData_NewGetBuffer(type, view, flags);
}

static void cdata_releasebuffer(PyObject* obj, Py_buffer* view) {
    GraalPyPrivate_CData_ReleaseBuffer(obj, view);
}

PyAPI_FUNC(void) GraalPyPrivate_CData_InitBufferProtocol(PyObject* type) {
    static PyBufferProcs cdata_as_buffer = {
        cdata_getbuffer,
        cdata_releasebuffer,
    };
    GraalPyPrivate_Type_SetBufferProcs(((PyTypeObject*) type), &cdata_as_buffer);
    ((PyTypeObject*) type)->tp_as_buffer = &cdata_as_buffer;
}

struct _longobject* _Py_FalseStructReference;
struct _longobject* _Py_TrueStructReference;
PyObject* _Py_EllipsisObjectReference;
PyObject* _Py_NoneStructReference;
PyObject* _Py_NotImplementedStructReference;

/*
 * This holds the thread-local reference to the PythonThreadState on the
 * managed side. If additional contexts used the C API with the same Python
 * library, we have to either update when switching contexts, or before each
 * downcall, or just leave this NULL at all times and incur an upcall in the
 * getter.
 */
THREAD_LOCAL PyThreadState *tstate_current = NULL;

static void initialize_globals() {
    // store the thread state into a thread local variable
    tstate_current = GraalPyPrivate_ThreadState_Get(&tstate_current);
    _Py_NoneStructReference = GraalPyPrivate_None();
    _Py_NotImplementedStructReference = GraalPyPrivate_NotImplemented();
    _Py_EllipsisObjectReference = GraalPyPrivate_Ellipsis();
    _Py_TrueStructReference = (struct _longobject*)GraalPyPrivate_True();
    _Py_FalseStructReference = (struct _longobject*)GraalPyPrivate_False();
}

/* internal functions to avoid unnecessary managed <-> native conversions */

/* BYTES, BYTEARRAY */
int bytes_buffer_getbuffer(PyBytesObject *self, Py_buffer *view, int flags);
int bytearray_getbuffer(PyByteArrayObject *obj, Py_buffer *view, int flags);
void bytearray_releasebuffer(PyByteArrayObject *obj, Py_buffer *view);

/* MEMORYVIEW */
int memory_getbuf(PyMemoryViewObject *self, Py_buffer *view, int flags);
void memory_releasebuf(PyMemoryViewObject *self, Py_buffer *view);

/* PICKLEBUFFER */
static int
picklebuf_getbuf(PyPickleBufferObject *self, Py_buffer *view, int flags)
{
    PyObject *self_view_obj = GraalPyPrivate_PickleBuffer_viewobj((PyObject*) self);
    return PyObject_GetBuffer(self_view_obj, view, flags);
}

static void empty_releasebuf(PyObject *self, Py_buffer *view) {}

static void initialize_bufferprocs() {
    static PyBufferProcs bytes_as_buffer = {
        (getbufferproc)bytes_buffer_getbuffer,       /* bf_getbuffer */
        (releasebufferproc)NULL,                     /* bf_releasebuffer */
    };
    PyBytes_Type.tp_as_buffer = &bytes_as_buffer;
    GraalPyPrivate_Type_SetBufferProcs(&PyBytes_Type, &bytes_as_buffer);

    static PyBufferProcs bytearray_as_buffer = {
        (getbufferproc)bytearray_getbuffer,          /* bf_getbuffer */
        (releasebufferproc)bytearray_releasebuffer,  /* bf_releasebuffer */
    };
    PyByteArray_Type.tp_as_buffer = &bytearray_as_buffer;
    GraalPyPrivate_Type_SetBufferProcs(&PyByteArray_Type, &bytearray_as_buffer);

    static PyBufferProcs memory_as_buffer = {
        (getbufferproc)memory_getbuf,         /* bf_getbuffer */
        (releasebufferproc)memory_releasebuf, /* bf_releasebuffer */
    };
    PyMemoryView_Type.tp_as_buffer = &memory_as_buffer;
    GraalPyPrivate_Type_SetBufferProcs(&PyMemoryView_Type, &memory_as_buffer);

    static PyBufferProcs array_as_buffer;
    array_as_buffer.bf_getbuffer = GraalPyPrivate_Array_getbuffer,
    array_as_buffer.bf_releasebuffer = GraalPyPrivate_Array_releasebuffer,
    Arraytype.tp_as_buffer = &array_as_buffer;
    GraalPyPrivate_Type_SetBufferProcs(&Arraytype, &array_as_buffer);

    static PyBufferProcs picklebuf_as_buffer;
    picklebuf_as_buffer.bf_getbuffer = (getbufferproc)picklebuf_getbuf,
    picklebuf_as_buffer.bf_releasebuffer = empty_releasebuf,
    PyPickleBuffer_Type.tp_as_buffer = &picklebuf_as_buffer;
    GraalPyPrivate_Type_SetBufferProcs(&PyPickleBuffer_Type, &picklebuf_as_buffer);

}


static int dummy_traverse(PyObject *self, visitproc f, void *i) {return 0;}

static void initialize_gc_types_related_slots() {
    _PyExc_Exception.tp_traverse = &dummy_traverse;
    _PyWeakref_RefType.tp_free = &GraalPyPrivate_Object_GC_Del;
}

int is_builtin_type(PyTypeObject *tp) {
#define PY_TRUFFLE_TYPE_GENERIC(GLOBAL_NAME, __TYPE_NAME__, a, b, c, d, e, f, g) (tp == &GLOBAL_NAME) ||
#define PY_TRUFFLE_TYPE_EXTERN(GLOBAL_NAME, __TYPE_NAME__) PY_TRUFFLE_TYPE_GENERIC(GLOBAL_NAME, __TYPE_NAME__, 0, 0, 0, 0, 0, 0, 0)
#define PY_TRUFFLE_TYPE_UNIMPLEMENTED(GLOBAL_NAME) // empty
    return PY_TYPE_OBJECTS 0;
#undef PY_TRUFFLE_TYPE_GENERIC
#undef PY_TRUFFLE_TYPE_EXTERN
#undef PY_TRUFFLE_TYPE_UNIMPLEMENTED
}

/** to be used from Java code only; calls INCREF */
PyAPI_FUNC(void) GraalPyPrivate_INCREF(PyObject* obj) {
    Py_INCREF(obj);
}

/** to be used from Java code only; calls DECREF */
PyAPI_FUNC(void) GraalPyPrivate_DECREF(PyObject* obj) {
    Py_DECREF(obj);
}

/** to be used from Java code only; calls '_Py_Dealloc' */
PyAPI_FUNC(Py_ssize_t)
GraalPyPrivate_SUBREF(intptr_t ptr, Py_ssize_t value)
{
    PyObject *obj = (PyObject*)ptr; // avoid type attachment at the interop boundary
#ifndef NDEBUG
    if (obj->ob_refcnt & 0xFFFFFFFF00000000L) {
        char buf[1024];
        sprintf(buf,
                "suspicious refcnt value during managed adjustment for %p (%zd 0x%zx - %zd)\n",
                obj, obj->ob_refcnt, obj->ob_refcnt, value);
        Py_FatalError(buf);
    }
    if ((obj->ob_refcnt - value) < 0) {
        char buf[1024];
        sprintf(buf,
                "refcnt below zero during managed adjustment for %p (%zd 0x%zx - %zd)\n",
                obj, obj->ob_refcnt, obj->ob_refcnt, value);
        Py_FatalError(buf);
    }
#endif // NDEBUG

    Py_ssize_t new_value = ((obj->ob_refcnt) -= value);
    if (new_value == 0) {
        GraalPyPrivate_Log(PY_TRUFFLE_LOG_FINER, "%s: _Py_Dealloc(0x%zx)",
                __func__, obj);
        _Py_Dealloc(obj);
    }
#ifdef Py_REF_DEBUG
    else if (new_value < 0) {
        _Py_NegativeRefcount(filename, lineno, op);
    }
#endif
    return new_value;
}

/** to be used from Java code only; calls '_Py_Dealloc' */
PyAPI_FUNC(Py_ssize_t)
GraalPyPrivate_BulkDealloc(intptr_t ptrArray[], int64_t len)
{
    for (int i = 0; i < len; i++) {
        PyObject *obj = (PyObject*)ptrArray[i];
        GraalPyPrivate_Log(PY_TRUFFLE_LOG_FINER, "%s: _Py_Dealloc(0x%zx)",
                __func__, obj);
        _Py_Dealloc(obj);
    }
    return 0;
}

/** to be used from Java code only and only at exit; calls _Py_Dealloc */
PyAPI_FUNC(Py_ssize_t)
GraalPyPrivate_BulkDeallocOnShutdown(intptr_t ptrArray[], int64_t len)
{
    /* some objects depends on others which might get deallocated in the
       process of an earlier deallocation of the other object. To avoid double
       deallocations, we, temporarily, make all objects immortal artificially */
    for (int i = 0; i < len; i++) {
        PyObject *obj = (PyObject*)ptrArray[i];
        obj->ob_refcnt = 999999999; // object.h:_Py_IMMORTAL_REFCNT
    }
    for (int i = 0; i < len; i++) {
        PyObject *obj = (PyObject*)ptrArray[i];
        if (Py_TYPE(obj)->tp_dealloc != object_dealloc) {
            /* we don't need to care about objects with default deallocation
               process */
            obj->ob_refcnt = 0;
            GraalPyPrivate_Log(PY_TRUFFLE_LOG_FINER, "%s: _Py_Dealloc(0x%zx)",
                    __func__, obj);
            _Py_Dealloc(obj);
        }
    }
    return 0;
}

/* To be used from Java code only.
 * This function is used if a native class inherits from a managed class but uses the 'object.__new__'.
 * This function roughly corresponds to CPython's 'object_new'. */
PyAPI_FUNC(PyObject*) GraalPyPrivate_ObjectNew(PyTypeObject* cls) {
    return cls->tp_alloc(cls, 0);
}

PyAPI_FUNC(void) GraalPyPrivate_ObjectArrayRelease(PyObject** array, int32_t size) {
    for (int i = 0; i < size; i++) {
        /* This needs to use 'Py_XDECREF' because we use this function to
           deallocate storages of tuples, lists, ..., where this is done in the
           appropriate 'tp_traverse' function which uses 'Py_VISIT' and this
           allows an element to be 'NULL'. Elements may, in particular, be
           'NULL' if a tuple dies before all elements are initialized. */
        Py_XDECREF(array[i]);
    }
}

#if defined(__APPLE__) && defined(__MACH__)
#include <mach/mach.h>
#elif defined(_WIN32)
#include <windows.h>
#include "psapi.h"
#endif

PyAPI_FUNC(size_t) GraalPyPrivate_GetCurrentRSS() {
    size_t rss = 0;
#if defined(__APPLE__) && defined(__MACH__)
    // MacOS
    struct mach_task_basic_info info;
    mach_msg_type_number_t infoCount = MACH_TASK_BASIC_INFO_COUNT;
    if (task_info(mach_task_self(), MACH_TASK_BASIC_INFO, (task_info_t) &info, &infoCount) == KERN_SUCCESS) {
        rss = (size_t)info.resident_size;
    }

#elif defined(__linux__) || defined(__gnu_linux__)
    // Linux
    FILE* fp = NULL;
    if ((fp = fopen( "/proc/self/statm", "r" )) != NULL) {
        if (fscanf(fp, "%*s%ld", (long *) &rss)) {
            rss *= (uint64_t) sysconf( _SC_PAGESIZE);
        }
        fclose(fp);
    }

#elif defined(_WIN32)
    // Windows
    PROCESS_MEMORY_COUNTERS pmc;
    if (GetProcessMemoryInfo(GetCurrentProcess(), &pmc, sizeof(pmc))) {
        rss = pmc.WorkingSetSize;
    }
#endif
    return rss / (1024 * 1024 /* bytes -> megabytes*/);
}


#define ReadMember(object, offset, T) ((T*)(((char*)object) + offset))[0]

PyAPI_FUNC(int) GraalPyPrivate_ReadShortMember(void* object, Py_ssize_t offset) {
    return ReadMember(object, offset, short);
}

PyAPI_FUNC(int) GraalPyPrivate_ReadIntMember(void* object, Py_ssize_t offset) {
    return ReadMember(object, offset, int);
}

PyAPI_FUNC(long) GraalPyPrivate_ReadLongMember(void* object, Py_ssize_t offset) {
    return ReadMember(object, offset, long);
}

PyAPI_FUNC(double) GraalPyPrivate_ReadFloatMember(void* object, Py_ssize_t offset) {
    return ReadMember(object, offset, float);
}

PyAPI_FUNC(double) GraalPyPrivate_ReadDoubleMember(void* object, Py_ssize_t offset) {
    return ReadMember(object, offset, double);
}

PyAPI_FUNC(void*) GraalPyPrivate_ReadPointerMember(void* object, Py_ssize_t offset) {
    return ReadMember(object, offset, void*);
}

PyAPI_FUNC(int) GraalPyPrivate_ReadCharMember(void* object, Py_ssize_t offset) {
    return ReadMember(object, offset, char);
}

#define WriteMember(object, offset, value, T) *(T*)(((char*)object) + offset) = (T)(value)

PyAPI_FUNC(int) GraalPyPrivate_WriteShortMember(void* object, Py_ssize_t offset, short value) {
    WriteMember(object, offset, value, short);
    return 0;
}

PyAPI_FUNC(int) GraalPyPrivate_WriteIntMember(void* object, Py_ssize_t offset, int value) {
    WriteMember(object, offset, value, int);
    return 0;
}

PyAPI_FUNC(int) GraalPyPrivate_WriteLongMember(void* object, Py_ssize_t offset, long value) {
    WriteMember(object, offset, value, long);
    return 0;
}

PyAPI_FUNC(int) GraalPyPrivate_WriteFloatMember(void* object, Py_ssize_t offset, double value) {
    WriteMember(object, offset, value, float);
    return 0;
}

PyAPI_FUNC(int) GraalPyPrivate_WriteDoubleMember(void* object, Py_ssize_t offset, double value) {
    WriteMember(object, offset, value, double);
    return 0;
}

PyAPI_FUNC(int) GraalPyPrivate_WriteObjectMember(void* object, Py_ssize_t offset, PyObject* value) {
    /* We first need to decref the old value. */
    PyObject *oldv = ReadMember(object, offset, PyObject*);
    Py_XINCREF(value);
    WriteMember(object, offset, value, PyObject*);
    Py_XDECREF(oldv);
    return 0;
}

PyAPI_FUNC(int) GraalPyPrivate_WritePointerMember(void* object, Py_ssize_t offset, void* value) {
    WriteMember(object, offset, value, void*);
    return 0;
}

PyAPI_FUNC(int) GraalPyPrivate_WriteCharMember(void* object, Py_ssize_t offset, char value) {
    WriteMember(object, offset, value, char);
    return 0;
}

#undef ReadMember
#undef WriteMember

PyAPI_FUNC(int) GraalPyPrivate_PointerCompare(void* x, void* y, int op) {
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

PyAPI_FUNC(void*) GraalPyPrivate_PointerAddOffset(void* x, Py_ssize_t y) {
    return (char *)x + y;
}

PyAPI_FUNC(int) GraalPyPrivate_SubclassCheck(PyObject* type) {
    return PyType_FastSubclass(Py_TYPE(type), Py_TPFLAGS_TYPE_SUBCLASS);
}

// Implements the basesisze check in typeobject.c:_PyObject_GetState
PyAPI_FUNC(int) GraalPyPrivate_CheckBasicsizeForGetstate(PyTypeObject* type, int slot_num) {
    Py_ssize_t basicsize = PyBaseObject_Type.tp_basicsize;
    if (type->tp_dictoffset)
        basicsize += sizeof(PyObject *);
    if (type->tp_weaklistoffset)
        basicsize += sizeof(PyObject *);
    if (slot_num)
        basicsize += sizeof(PyObject *) * slot_num;
    return type->tp_basicsize > basicsize;
}

PyAPI_FUNC(void) GraalPyPrivate_CheckTypeReady(PyTypeObject* type) {
    if (!(type->tp_flags & Py_TPFLAGS_READY)) {
        PyType_Ready(type);
    }
}

PyAPI_FUNC(void*) GraalPyPrivate_VaArgPointer(va_list* va) {
	return va_arg(*va, void*);
}

PyAPI_FUNC(void*) GraalPyPrivate_ConvertPointer(Py_ssize_t value) {
	return (void*) value;
}

PyAPI_FUNC(int) GraalPyPrivate_NoOpClear(PyObject* o) {
    return 0;
}

PyAPI_FUNC(int) GraalPyPrivate_NoOpTraverse(PyObject *self, visitproc visit, void *arg) {
    return 0;
}

// defined in 'exceptions.c'
void initialize_exceptions();
// defined in 'pyhash.c'
void initialize_hashes();
// defined in 'floatobject.c'
void _PyFloat_InitState(PyInterpreterState* state);

/*
 * This is used to allow Truffle to enter/leave the context on native threads
 * that were not created by NFI/Truffle/Java and thus not previously attached
 * to the context. See e.g. PyGILState_Ensure. This is used by some C
 * extensions to allow calling Python APIs from natively created threads. This
 * poses a problem if multiple contexts use the same library, since we cannot
 * know which context should be entered. CPython has the same problem (see
 * https://docs.python.org/3/c-api/init.html#bugs-and-caveats), in particular
 * the following quote:
 *
 *   Furthermore, extensions (such as ctypes) using these APIs to allow calling
 *   of Python code from non-Python created threads will probably be broken
 *   when using sub-interpreters.
 *
 * If we try to use the same libpython for multiple contexts, we can only
 * behave in a similar (likely broken) way as CPython: natively created threads
 * that use the PyGIL_* APIs to allow calling into Python will attach to the
 * first interpreter that initialized the C API (and thus set the
 * TRUFFLE_CONTEXT pointer) only.
 */
Py_LOCAL_SYMBOL TruffleContext* TRUFFLE_CONTEXT;

/*
 * This is only set during VM shutdown, so on the native side can only be used
 * to guard things that do not work during VM shutdown, not to guard things
 * that do not work during context shutdown! (This means that it would be safe
 * to share this global across multiple contexts.)
 */
Py_LOCAL_SYMBOL int8_t *_graalpy_finalizing = NULL;

PyAPI_FUNC(void) initialize_graal_capi(TruffleEnv* env, void **builtin_closures, GCState *gc) {
    clock_t t = clock();

    if (env) {
        TRUFFLE_CONTEXT = (*env)->getTruffleContext(env);
    }

    _PyGC_InitState(gc);

    /*
     * Initializing all these global fields with pointers to different contexts
     * could be ok even if all contexts share this library and its globals.
     * Each native stub is allocated using the AllocateNode and filled by each
     * context. The long value of the stub pointer is then given an index via
     * nativeStubLookupReserve, and the index is stored both in the stub for
     * fast access from native as well as in the PythonObjectReference which
     * wraps the stub on the managed side. The table is per context. Given that
     * C API initialisation is deterministic and we initialise only with
     * quasi-immortal objects, those indices are never repurposed for other
     * objects. So, while later contexts override the pointers for those global
     * objects with pointers to their own stubs, the index stored in those
     * stubs are the same across all contexts, and thus mapping to
     * context-specific objects works as intended. It'd a bit dodgy that the
     * ob_refcnt fields of those objects would show whacky behaviour. Maybe we
     * could just assert that everything stored during initialisation of the
     * GraalPy C API has IMMORTAL_REFCNT and that all stub indices actually
     * match. The only real problem would be if the last context exists and
     * actually frees the stub memory. We would have to VM-globally delay
     * freeing the "latest" stubs, but that's no big deal, we would simply keep
     * a reference to the "latest" handle stub table globally. When it changes
     * and the associated context already exited, we can free it now, when
     * context exits and its table is the "latest", we delay freeing it.
     */
    initialize_builtins(builtin_closures);
    GraalPyPrivate_Log(PY_TRUFFLE_LOG_FINE, "initialize_builtins: %fs", ((double) (clock() - t)) / CLOCKS_PER_SEC);
    Py_Truffle_Options = GraalPyPrivate_Native_Options();

    initialize_builtin_types_and_structs();
    // initialize global variables like '_Py_NoneStruct', etc.
    initialize_globals();
    initialize_exceptions();
    initialize_hashes();
    initialize_bufferprocs();
    initialize_gc_types_related_slots();
    _PyFloat_InitState(NULL);

    // TODO: initialize during cext initialization doesn't work at the moment
    Py_FileSystemDefaultEncoding = "utf-8"; // strdup(PyUnicode_AsUTF8(GraalPyPrivate_FileSystemDefaultEncoding()));

    GraalPyPrivate_Log(PY_TRUFFLE_LOG_FINE, "initialize_graal_capi: %fs", ((double) (clock() - t)) / CLOCKS_PER_SEC);
}

/*
 * This function is called from Java during C API initialization to get the
 * pointer `_graalpy_finalizing`.
 */
PyAPI_FUNC(int8_t *) GraalPyPrivate_GetFinalizeCApiPointer() {
    assert(!_graalpy_finalizing);
    // We actually leak this memory on purpose. On the Java side, this is
    // written to in a VM shutdown hook. Once such a hook is registered it
    // sticks around, so we're leaking those hooks anyway. 1 byte more to leak
    // does not strike me (timfel) as significant.
    _graalpy_finalizing = (int8_t *)calloc(1, sizeof(int8_t));
    return _graalpy_finalizing;
}

static void unimplemented(const char* name) {
    printf("Function not implemented in GraalPy: %s\n", name);
    print_c_stacktrace();
}

#define FUNC_NOT_IMPLEMENTED unimplemented(__func__); GraalPyPrivate_PrintStacktrace(); exit(-1);

// {{start CAPI_BUILTINS}}
#include "capi.gen.c.h"
