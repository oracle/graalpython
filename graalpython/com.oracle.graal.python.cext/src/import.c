/*
 * Copyright (c) 2018, Oracle and/or its affiliates.
 *
 * All rights reserved.
 */
#include "capi.h"

PyObject* PyImport_ImportModule(const char *name) {
    return to_sulong(truffle_invoke(PY_BUILTIN, "__import__", truffle_read_string(name)));
}
