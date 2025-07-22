/* Copyright (c) 2024, 2025, Oracle and/or its affiliates.
 * Copyright (C) 1996-2023 Python Software Foundation
 *
 * Licensed under the PYTHON SOFTWARE FOUNDATION LICENSE VERSION 2
 */
#ifndef Py_INTERNAL_GLOBAL_OBJECTS_H
#define Py_INTERNAL_GLOBAL_OBJECTS_H
#ifdef __cplusplus
extern "C" {
#endif

#ifndef Py_BUILD_CORE
#  error "this header requires Py_BUILD_CORE define"
#endif

#if 0 // GraalPy change
#include "pycore_hashtable.h"       // _Py_hashtable_t
#endif // GraalPy change
#include "pycore_gc.h"              // PyGC_Head
#if 0 // GraalPy change
#include "pycore_global_strings.h"  // struct _Py_global_strings
#include "pycore_hamt.h"            // PyHamtNode_Bitmap
#include "pycore_context.h"         // _PyContextTokenMissing
#endif // GraalPy change
#include "pycore_typeobject.h"      // pytype_slotdef


// These would be in pycore_long.h if it weren't for an include cycle.
#define _PY_NSMALLPOSINTS           257
#define _PY_NSMALLNEGINTS           5


// Only immutable objects should be considered runtime-global.
// All others must be per-interpreter.

#define _Py_GLOBAL_OBJECT(NAME) \
    _PyRuntime.static_objects.NAME
#define _Py_SINGLETON(NAME) \
    _Py_GLOBAL_OBJECT(singletons.NAME)

#if 0 // GraalPy change
struct _Py_cached_objects {
    // XXX We could statically allocate the hashtable.
    _Py_hashtable_t *interned_strings;
};
#endif // GraalPy change

struct _Py_static_objects {
    struct {
        /* Small integers are preallocated in this array so that they
         * can be shared.
         * The integers that are preallocated are those in the range
         * -_PY_NSMALLNEGINTS (inclusive) to _PY_NSMALLPOSINTS (exclusive).
         */
#if 0 // GraalPy change
        PyLongObject small_ints[_PY_NSMALLNEGINTS + _PY_NSMALLPOSINTS];

        PyBytesObject bytes_empty;
        struct {
            PyBytesObject ob;
            char eos;
        } bytes_characters[256];

        struct _Py_global_strings strings;

        _PyGC_Head_UNUSED _tuple_empty_gc_not_used;
        PyTupleObject tuple_empty;

        _PyGC_Head_UNUSED _hamt_bitmap_node_empty_gc_not_used;
        PyHamtNode_Bitmap hamt_bitmap_node_empty;
        _PyContextTokenMissing context_token_missing;
#endif // GraalPy change
        char GraalDummyField;
    } singletons;
};

#define _Py_INTERP_CACHED_OBJECT(interp, NAME) \
    (interp)->cached_objects.NAME

struct _Py_interp_cached_objects {
    PyObject *interned_strings;

    /* AST */
    PyObject *str_replace_inf;

    /* object.__reduce__ */
    PyObject *objreduce;
    PyObject *type_slots_pname;
    pytype_slotdef *type_slots_ptrs[MAX_EQUIV];

    /* TypeVar and related types */
    PyTypeObject *generic_type;
    PyTypeObject *typevar_type;
    PyTypeObject *typevartuple_type;
    PyTypeObject *paramspec_type;
    PyTypeObject *paramspecargs_type;
    PyTypeObject *paramspeckwargs_type;
};

#define _Py_INTERP_STATIC_OBJECT(interp, NAME) \
    (interp)->static_objects.NAME
#define _Py_INTERP_SINGLETON(interp, NAME) \
    _Py_INTERP_STATIC_OBJECT(interp, singletons.NAME)

struct _Py_interp_static_objects {
    struct {
        int _not_used;
        // hamt_empty is here instead of global because of its weakreflist.
        _PyGC_Head_UNUSED _hamt_empty_gc_not_used;
#if 0 // GraalPy change
        PyHamtObject hamt_empty;
#endif // GraalPy change
        PyBaseExceptionObject last_resort_memory_error;
    } singletons;
};


#ifdef __cplusplus
}
#endif
#endif /* !Py_INTERNAL_GLOBAL_OBJECTS_H */
