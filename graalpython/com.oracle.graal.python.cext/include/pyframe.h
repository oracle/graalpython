/* Copyright (c) 2022, 2023, Oracle and/or its affiliates.
 * Copyright (C) 1996-2022 Python Software Foundation
 *
 * Licensed under the PYTHON SOFTWARE FOUNDATION LICENSE VERSION 2
 */
/* Limited C API of PyFrame API
 *
 * Include "frameobject.h" to get the PyFrameObject structure.
 */

#ifndef Py_PYFRAME_H
#define Py_PYFRAME_H
#ifdef __cplusplus
extern "C" {
#endif

/* Return the line of code the frame is currently executing. */
PyAPI_FUNC(int) PyFrame_GetLineNumber(PyFrameObject *);

PyAPI_FUNC(PyCodeObject *) PyFrame_GetCode(PyFrameObject *frame);

#ifndef Py_LIMITED_API
#  define Py_CPYTHON_PYFRAME_H
#  include "cpython/pyframe.h"
#  undef Py_CPYTHON_PYFRAME_H
#endif

#ifdef __cplusplus
}
#endif
#endif /* !Py_PYFRAME_H */
