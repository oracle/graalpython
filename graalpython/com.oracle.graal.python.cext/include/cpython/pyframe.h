/* Copyright (c) 2023, Oracle and/or its affiliates.
 * Copyright (C) 1996-2023 Python Software Foundation
 *
 * Licensed under the PYTHON SOFTWARE FOUNDATION LICENSE VERSION 2
 */
#ifndef Py_CPYTHON_PYFRAME_H
#  error "this header file must not be included directly"
#endif

PyAPI_DATA(PyTypeObject) PyFrame_Type;

#define PyFrame_Check(op) Py_IS_TYPE((op), &PyFrame_Type)

PyAPI_FUNC(PyFrameObject *) PyFrame_GetBack(PyFrameObject *frame);
PyAPI_FUNC(PyObject *) PyFrame_GetLocals(PyFrameObject *frame);

PyAPI_FUNC(PyObject *) PyFrame_GetGlobals(PyFrameObject *frame);
PyAPI_FUNC(PyObject *) PyFrame_GetBuiltins(PyFrameObject *frame);

PyAPI_FUNC(PyObject *) PyFrame_GetGenerator(PyFrameObject *frame);
PyAPI_FUNC(int) PyFrame_GetLasti(PyFrameObject *frame);

