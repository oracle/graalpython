/* Copyright (c) 2023, 2024, Oracle and/or its affiliates.
 * Copyright (C) 1996-2023 Python Software Foundation
 *
 * Licensed under the PYTHON SOFTWARE FOUNDATION LICENSE VERSION 2
 */
#ifndef Py_CPYTHON_FLOATOBJECT_H
#  error "this header file must not be included directly"
#endif

typedef struct {
    PyObject_HEAD
    double ob_fval;
} PyFloatObject;

// Macro version of PyFloat_AsDouble() trading safety for speed.
// It doesn't check if op is a double object.
#define PyFloat_AS_DOUBLE(op) PyFloat_AsDouble((PyObject*)(op))


PyAPI_FUNC(int) PyFloat_Pack2(double x, char *p, int le);
PyAPI_FUNC(int) PyFloat_Pack4(double x, char *p, int le);
PyAPI_FUNC(int) PyFloat_Pack8(double x, char *p, int le);

PyAPI_FUNC(double) PyFloat_Unpack2(const char *p, int le);
PyAPI_FUNC(double) PyFloat_Unpack4(const char *p, int le);
PyAPI_FUNC(double) PyFloat_Unpack8(const char *p, int le);
