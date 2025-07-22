/* Copyright (c) 2018, 2024, Oracle and/or its affiliates.
 * Copyright (C) 1996-2020 Python Software Foundation
 *
 * Licensed under the PYTHON SOFTWARE FOUNDATION LICENSE VERSION 2
 */

#ifndef Py_COMPILE_H
#define Py_COMPILE_H
#ifdef __cplusplus
extern "C" {
#endif

/* These definitions must match corresponding definitions in graminit.h. */
#define Py_single_input 256
#define Py_file_input 257
#define Py_eval_input 258
#define Py_func_type_input 345

#ifndef Py_LIMITED_API
#  define Py_CPYTHON_COMPILE_H
#  include "cpython/compile.h"
#  undef Py_CPYTHON_COMPILE_H
#endif

#ifdef __cplusplus
}
#endif
#endif /* !Py_COMPILE_H */
