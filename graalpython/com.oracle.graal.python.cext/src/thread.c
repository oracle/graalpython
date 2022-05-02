/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
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
