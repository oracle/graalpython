/*
 * Copyright (c) 2018, Oracle and/or its affiliates.
 *
 * All rights reserved.
 */

// This header is not taken from CPython, because we want to use and hand out
// our own hashes. Right now it only exposes what we need.
#ifndef Py_HASH_H

#define Py_HASH_H
#ifdef __cplusplus
extern "C" {
#endif

PyAPI_FUNC(Py_hash_t) _Py_HashDouble(double);

extern long _PyHASH_INF;
extern long _PyHASH_NAN;
extern long _PyHASH_IMAG;
#define _PyHASH_MULTIPLIER _PyHASH_IMAG;

#ifdef __cplusplus
}
#endif

#endif /* !Py_HASH_H */
