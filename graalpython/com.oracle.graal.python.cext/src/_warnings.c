/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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

UPCALL_ID(_PyErr_Warn);

// partially taken from CPython "Python/_warnings.c"
MUST_INLINE static int warn_unicode(PyObject *category, PyObject *message, Py_ssize_t stack_level, PyObject *source) {
    PyObject *res;

    if (category == NULL) {
        category = PyExc_RuntimeWarning;
    }

    PyObject* result = UPCALL_CEXT_O(_jls__PyErr_Warn, native_to_java(message), native_to_java(category), stack_level, native_to_java(source));
    if(result == NULL) {
    	return -1;
    }
    Py_DECREF(result);
    return 0;
}

// taken from CPython "Python/_warnings.c"
int PyErr_WarnEx(PyObject *category, const char *text, Py_ssize_t stack_level) {
    int ret;
    PyObject *message = PyUnicode_FromString(text);
    if (message == NULL)
        return -1;
    ret = warn_unicode(category, message, stack_level, NULL);
    Py_DECREF(message);
    return ret;
}

NO_INLINE int PyErr_WarnFormat(PyObject *category, Py_ssize_t stack_level, const char *format, ...) {
    va_list args;
    va_start(args, format);
    PyObject* result = PyTruffle_Unicode_FromFormat(format, args);
    va_end(args);
    return warn_unicode(category, result, stack_level, Py_None);
}

int PyErr_ResourceWarning(PyObject *source, Py_ssize_t stack_level, const char *format, ...) {
    va_list args;
    va_start(args, format);
    PyObject* result = PyTruffle_Unicode_FromFormat(format, args);
    va_end(args);
    return warn_unicode(PyExc_ResourceWarning, result, stack_level, source);
}

