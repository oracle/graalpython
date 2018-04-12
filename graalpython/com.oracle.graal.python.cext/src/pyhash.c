/*
 * Copyright (c) 2018, Oracle and/or its affiliates.
 *
 * All rights reserved.
 */
#include "capi.h"

Py_hash_t _Py_HashDouble(double value) {
    return truffle_invoke_l(PY_BUILTIN, "hash", value);
}

long _PyHASH_INF;
long _PyHASH_NAN;
long _PyHASH_IMAG;

void initialize_hashes() {
    _PyHASH_INF = truffle_invoke_l(PY_BUILTIN, "hash", INFINITY);
    _PyHASH_NAN = truffle_invoke_l(PY_BUILTIN, "hash", NAN);
    _PyHASH_IMAG = truffle_invoke_l(PY_TRUFFLE_CEXT, "PyHash_Imag");
}
