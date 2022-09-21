/* Copyright (c) 2018, 2021, Oracle and/or its affiliates.
 * Copyright (C) 1996-2020 Python Software Foundation
 *
 * Licensed under the PYTHON SOFTWARE FOUNDATION LICENSE VERSION 2
 */

/* Definitions for bytecode */

#ifndef Py_CODE_H
#define Py_CODE_H
#ifdef __cplusplus
extern "C" {
#endif

typedef struct PyCodeObject PyCodeObject;

#ifndef Py_LIMITED_API
#  define Py_CPYTHON_CODE_H
#  include  "cpython/code.h"
#  undef Py_CPYTHON_CODE_H
#endif

#ifdef __cplusplus
}
#endif
#endif /* !Py_CODE_H */
