/* Copyright (c) 2018, Oracle and/or its affiliates.
 * Copyright (C) 1996-2017 Python Software Foundation
 *
 * Licensed under the PYTHON SOFTWARE FOUNDATION LICENSE VERSION 2
 */

/* Return the compiler identification, if possible. */

#include "Python.h"

#ifndef COMPILER

#ifdef __GNUC__
#define COMPILER "\n[GCC " __VERSION__ "]"
#endif

#endif /* !COMPILER */

#ifndef COMPILER

#ifdef __cplusplus
#define COMPILER "[C++]"
#else
#define COMPILER "[C]"
#endif

#endif /* !COMPILER */

const char *
Py_GetCompiler(void)
{
    return COMPILER;
}
