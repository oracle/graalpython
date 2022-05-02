/* Copyright (c) 2022, Oracle and/or its affiliates.
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

char* _PyByteArray_Start(PyObject*);

/* Macros, trading safety for speed */
#define PyByteArray_AS_STRING(self) \
    (assert(PyByteArray_Check(self)), \
     Py_SIZE(self) ? _PyByteArray_Start((PyObject *)self) : _PyByteArray_empty_string)
#define PyByteArray_GET_SIZE(self) (assert(PyByteArray_Check(self)), Py_SIZE(self))

PyAPI_DATA(char) _PyByteArray_empty_string[];
