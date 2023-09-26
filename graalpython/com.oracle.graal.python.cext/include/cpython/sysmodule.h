/* Copyright (c) 2020, 2023, Oracle and/or its affiliates.
 * Copyright (C) 1996-2020 Python Software Foundation
 *
 * Licensed under the PYTHON SOFTWARE FOUNDATION LICENSE VERSION 2
 */
#ifndef Py_CPYTHON_SYSMODULE_H
#  error "this header file must not be included directly"
#endif

PyAPI_FUNC(PyObject *) _PySys_GetAttr(PyThreadState *tstate,
                                      PyObject *name);

PyAPI_FUNC(size_t) _PySys_GetSizeOf(PyObject *);

typedef int(*Py_AuditHookFunction)(const char *, PyObject *, void *);

PyAPI_FUNC(int) PySys_Audit(
    const char *event,
    const char *argFormat,
    ...);
PyAPI_FUNC(int) PySys_AddAuditHook(Py_AuditHookFunction, void*);
