/*
 * Copyright (c) 2017, 2024, Oracle and/or its affiliates. All rights reserved.
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

char _PyByteArray_empty_string[] = "";

// taken from CPython 3.7.0 "Objects/bytearrayobject.c"
int bytearray_getbuffer(PyByteArrayObject *obj, Py_buffer *view, int flags) {
    void *ptr;
    if (view == NULL) {
        PyErr_SetString(PyExc_BufferError,
            "bytearray_getbuffer: view==NULL argument is obsolete");
        return -1;
    }
    ptr = (void *) PyByteArray_AS_STRING(obj);
    /* cannot fail if view != NULL and readonly == 0 */
    (void)PyBuffer_FillInfo(view, (PyObject*)obj, ptr, Py_SIZE(obj), 0, flags);
    set_PyByteArrayObject_ob_exports(obj, PyByteArrayObject_ob_exports(obj) + 1);
    return 0;
}

void bytearray_releasebuffer(PyByteArrayObject *obj, Py_buffer *view) {
    set_PyByteArrayObject_ob_exports(obj, PyByteArrayObject_ob_exports(obj) - 1);
}

Py_ssize_t PyByteArray_Size(PyObject *self) {
    return PyByteArray_GET_SIZE(self);
}

PyObject* PyByteArray_FromStringAndSize(const char* str, Py_ssize_t sz) {
    if (sz < 0) {
        PyErr_SetString(PyExc_SystemError, "Negative size passed to PyByteArray_FromStringAndSize");
        return NULL;
    }
    if (str != NULL) {
        return GraalPyTruffleByteArray_FromStringAndSize(str, sz);
    }
    return GraalPyTruffle_ByteArray_EmptyWithCapacity(sz);
}
