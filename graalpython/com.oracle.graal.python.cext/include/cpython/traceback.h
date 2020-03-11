/* Copyright (c) 2020, Oracle and/or its affiliates.
 * Copyright (C) 1996-2020 Python Software Foundation
 *
 * Licensed under the PYTHON SOFTWARE FOUNDATION LICENSE VERSION 2
 */
#ifndef Py_CPYTHON_TRACEBACK_H
#  error "this header file must not be included directly"
#endif

#ifdef __cplusplus
extern "C" {
#endif

typedef struct _traceback {
    PyObject_HEAD
    struct _traceback *tb_next;
    struct _frame *tb_frame;
    int tb_lasti;
    int tb_lineno;
} PyTracebackObject;

PyAPI_FUNC(int) _Py_DisplaySourceLine(PyObject *, PyObject *, int, int);
PyAPI_FUNC(void) _PyTraceback_Add(const char *, const char *, int);

#ifdef __cplusplus
}
#endif
