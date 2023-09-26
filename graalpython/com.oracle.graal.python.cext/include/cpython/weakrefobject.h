/* Copyright (c) 2023, Oracle and/or its affiliates.
 * Copyright (C) 1996-2023 Python Software Foundation
 *
 * Licensed under the PYTHON SOFTWARE FOUNDATION LICENSE VERSION 2
 */
#ifndef Py_CPYTHON_WEAKREFOBJECT_H
#  error "this header file must not be included directly"
#endif

/* PyWeakReference is the base struct for the Python ReferenceType, ProxyType,
 * and CallableProxyType.
 */
struct _PyWeakReference {
    PyObject_HEAD

    /* The object to which this is a weak reference, or Py_None if none.
     * Note that this is a stealth reference:  wr_object's refcount is
     * not incremented to reflect this pointer.
     */
    PyObject *wr_object;

    /* A callable to invoke when wr_object dies, or NULL if none. */
    PyObject *wr_callback;

    /* A cache for wr_object's hash code.  As usual for hashes, this is -1
     * if the hash code isn't known yet.
     */
    Py_hash_t hash;

    /* If wr_object is weakly referenced, wr_object has a doubly-linked NULL-
     * terminated list of weak references to it.  These are the list pointers.
     * If wr_object goes away, wr_object is set to Py_None, and these pointers
     * have no meaning then.
     */
    PyWeakReference *wr_prev;
    PyWeakReference *wr_next;
    vectorcallfunc vectorcall;
};

PyAPI_FUNC(Py_ssize_t) _PyWeakref_GetWeakrefCount(PyWeakReference *head);

PyAPI_FUNC(void) _PyWeakref_ClearRef(PyWeakReference *self);

PyAPI_FUNC(PyObject*) PyWeakref_GET_OBJECT(PyObject *ref_obj);

#if !defined(Py_LIMITED_API) || Py_LIMITED_API+0 < 0x030b0000
#  define PyWeakref_GET_OBJECT(ref) PyWeakref_GET_OBJECT(_PyObject_CAST(ref))
#endif
