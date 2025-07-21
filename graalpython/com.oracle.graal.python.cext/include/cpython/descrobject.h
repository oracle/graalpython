/* Copyright (c) 2023, 2025, Oracle and/or its affiliates.
 * Copyright (C) 1996-2023 Python Software Foundation
 *
 * Licensed under the PYTHON SOFTWARE FOUNDATION LICENSE VERSION 2
 */
#ifndef Py_CPYTHON_DESCROBJECT_H
#  error "this header file must not be included directly"
#endif

typedef PyObject *(*wrapperfunc)(PyObject *self, PyObject *args,
                                 void *wrapped);

typedef PyObject *(*wrapperfunc_kwds)(PyObject *self, PyObject *args,
                                      void *wrapped, PyObject *kwds);

struct wrapperbase {
    const char *name;
    int offset;
    void *function;
    wrapperfunc wrapper;
    const char *doc;
    int flags;
    PyObject *name_strobj;
};

/* Flags for above struct */
#define PyWrapperFlag_KEYWORDS 1 /* wrapper function takes keyword args */

/* Various kinds of descriptor objects */

typedef struct {
    PyObject_HEAD
    PyTypeObject *d_type;
    PyObject *d_name;
    PyObject *d_qualname;
} PyDescrObject;

#define PyDescr_COMMON PyDescrObject d_common

#define PyDescr_TYPE(x) GraalPyDescrObject_GetType((PyObject*)(x))
#define PyDescr_NAME(x) GraalPyDescrObject_GetName((PyObject*)(x))

typedef struct {
    PyDescr_COMMON;
    PyMethodDef *d_method;
    vectorcallfunc vectorcall;
} PyMethodDescrObject;

typedef struct {
    PyDescr_COMMON;
    PyMemberDef *d_member;
} PyMemberDescrObject;

typedef struct {
    PyDescr_COMMON;
    PyGetSetDef *d_getset;
} PyGetSetDescrObject;

typedef struct {
    PyDescr_COMMON;
    struct wrapperbase *d_base;
    void *d_wrapped; /* This can be any function pointer */
} PyWrapperDescrObject;

PyAPI_DATA(PyTypeObject) _PyMethodWrapper_Type;

PyAPI_FUNC(PyObject *) PyDescr_NewWrapper(PyTypeObject *,
                                                struct wrapperbase *, void *);
PyAPI_FUNC(int) PyDescr_IsData(PyObject *);

// GraalPy public API to replace struct accessors
PyAPI_FUNC(PyMethodDef*) GraalPyMethodDescrObject_GetMethod(PyObject*);
PyAPI_FUNC(PyTypeObject*) GraalPyDescrObject_GetType(PyObject*);
PyAPI_FUNC(PyObject*) GraalPyDescrObject_GetName(PyObject*);
// Deprecated aliases kept for current versions of Cython
// Remove in 27.0
#define PyDescrObject_GetType GraalPyDescrObject_GetType
#define PyMethodDescrObject_GetMethod GraalPyMethodDescrObject_GetMethod
