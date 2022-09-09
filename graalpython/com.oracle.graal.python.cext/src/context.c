/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

UPCALL_ID(PyContextVar_New);
PyObject *
PyContextVar_New(const char *name, PyObject *def)
{
    if (name == NULL) {
        return NULL;
    }
    return UPCALL_CEXT_O(_jls_PyContextVar_New, polyglot_from_string(name, SRC_CS), native_to_java(def));
}

typedef PyObject *(*contextvar_get_fun_t)(PyObject *, PyObject *, void *);
UPCALL_TYPED_ID(PyContextVar_Get, contextvar_get_fun_t);
int
PyContextVar_Get(PyObject *var, PyObject *default_value, PyObject **value)
{
    static void *error_marker = &marker_struct;
    PyObject *res = _jls_PyContextVar_Get(native_to_java(var), native_to_java(default_value), error_marker);
    if ((void *)res == error_marker) {
        *value = NULL;
        return -1;
    }
    *value = res;
    return 0;
}

UPCALL_ID(PyContextVar_Set);
PyObject *
PyContextVar_Set(PyObject *var, PyObject *value)
{
    return UPCALL_CEXT_O(_jls_PyContextVar_Set, native_to_java(var), native_to_java(value));
}

