/* Copyright (c) 2021, 2023, Oracle and/or its affiliates.
 * Copyright (C) 1996-2022 Python Software Foundation
 *
 * Licensed under the PYTHON SOFTWARE FOUNDATION LICENSE VERSION 2
 */
#include "capi.h"

Py_tss_t* PyThread_tss_alloc(void) {
    Py_tss_t *new_key = (Py_tss_t *)PyMem_RawMalloc(sizeof(Py_tss_t));
    if (new_key == NULL) {
        return NULL;
    }
    new_key->_is_initialized = 0;
    return new_key;
}

void PyThread_tss_free(Py_tss_t *key) {
    if (key != NULL) {
        PyThread_tss_delete(key);
        PyMem_RawFree((void *)key);
    }
}

int PyThread_tss_is_created(Py_tss_t *key) {
    return key->_is_initialized;
}

int PyThread_tss_create(Py_tss_t *key)
{
    if (key->_is_initialized) {
        return 0;
    }
    key->_key = GraalPyTruffle_tss_create();
    key->_is_initialized = 1;
    return 0;
}

void* PyThread_tss_get(Py_tss_t *key) {
    return GraalPyTruffle_tss_get(key->_key);
}

int PyThread_tss_set(Py_tss_t *key, void *value) {
    return GraalPyTruffle_tss_set(key->_key, value);
}

void PyThread_tss_delete(Py_tss_t *key){
    if (!key->_is_initialized) {
        return;
    }
    GraalPyTruffle_tss_delete(key->_key);
    key->_is_initialized = 0;
}
