/* Copyright (c) 2022, 2024, Oracle and/or its affiliates.
 * Copyright (C) 1996-2022 Python Software Foundation
 *
 * Licensed under the PYTHON SOFTWARE FOUNDATION LICENSE VERSION 2
 */
#ifndef Py_CPYTHON_BYTEARRAYOBJECT_H
#  error "this header file must not be included directly"
#endif

/* Object layout */
typedef struct {
    PyObject_VAR_HEAD
    Py_ssize_t ob_alloc;   /* How many bytes allocated in ob_bytes */
    char *ob_bytes;        /* Physical backing buffer */
    char *ob_start;        /* Logical start inside ob_bytes */
    Py_ssize_t ob_exports; /* How many buffer exports */
} PyByteArrayObject;

PyAPI_DATA(char) _PyByteArray_empty_string[];

/* Macros and static inline functions, trading safety for speed */
#define _PyByteArray_CAST(op) \
    (assert(PyByteArray_Check(op)), _Py_CAST(PyByteArrayObject*, op))

static inline char* PyByteArray_AS_STRING(PyObject *op)
{
    return PyByteArray_AsString(op);
}
#define PyByteArray_AS_STRING(self) PyByteArray_AS_STRING(_PyObject_CAST(self))

static inline Py_ssize_t PyByteArray_GET_SIZE(PyObject *op) {
    PyByteArrayObject *self = _PyByteArray_CAST(op);
    return Py_SIZE(self);
}
#define PyByteArray_GET_SIZE(self) PyByteArray_GET_SIZE(_PyObject_CAST(self))
