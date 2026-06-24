/* Copyright (c) 2026, Oracle and/or its affiliates.
 * Copyright (C) 1996-2026 Python Software Foundation
 *
 * Licensed under the PYTHON SOFTWARE FOUNDATION LICENSE VERSION 2
 */
#ifndef Py_CPYTHON_HASH_H
#  error "this header file must not be included directly"
#endif

/* Prime multiplier used in string and various other hashes. */
#define PyHASH_MULTIPLIER 1000003UL  /* 0xf4243 */

/* Parameters used for the numeric hash implementation.  See notes for
   _Py_HashDouble in Python/pyhash.c.  Numeric hashes are based on
   reduction modulo the prime 2**_PyHASH_BITS - 1. */

#if SIZEOF_VOID_P >= 8
#  define PyHASH_BITS 61
#else
#  define PyHASH_BITS 31
#endif

#define PyHASH_MODULUS (((size_t)1 << PyHASH_BITS) - 1)
#define PyHASH_INF 314159
#define PyHASH_IMAG PyHASH_MULTIPLIER

/* Aliases kept for backward compatibility with Python 3.12 */
#define _PyHASH_MULTIPLIER PyHASH_MULTIPLIER
#define _PyHASH_BITS PyHASH_BITS
// GraalPy change
extern long _PyHASH_MODULUS;
extern long _PyHASH_INF;
extern long _PyHASH_IMAG;

/* Helpers for hash functions */
PyAPI_FUNC(Py_hash_t) _Py_HashDouble(PyObject *, double);

// Kept for backward compatibility
#define _Py_HashPointer Py_HashPointer


/* hash function definition */
typedef struct {
    Py_hash_t (*const hash)(const void *, Py_ssize_t);
    const char *name;
    const int hash_bits;
    const int seed_bits;
} PyHash_FuncDef;

PyAPI_FUNC(PyHash_FuncDef*) PyHash_GetFuncDef(void);

PyAPI_FUNC(Py_hash_t) Py_HashPointer(const void *ptr);
PyAPI_FUNC(Py_hash_t) PyObject_GenericHash(PyObject *);
