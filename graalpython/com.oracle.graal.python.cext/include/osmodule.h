/* Copyright (c) 2022, Oracle and/or its affiliates.
 * Copyright (C) 1996-2017 Python Software Foundation
 *
 * Licensed under the PYTHON SOFTWARE FOUNDATION LICENSE VERSION 2
 */
/* os module interface */

#ifndef Py_OSMODULE_H
#define Py_OSMODULE_H
#ifdef __cplusplus
extern "C" {
#endif

#if !defined(Py_LIMITED_API) || Py_LIMITED_API+0 >= 0x03060000
PyAPI_FUNC(PyObject *) PyOS_FSPath(PyObject *path);
#endif

#ifdef __cplusplus
}
#endif
#endif /* !Py_OSMODULE_H */
