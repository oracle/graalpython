/*
 * Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
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

#include <stdio.h>


// export the SizeT arg parse functions, because we use them in contrast to cpython on windows for core modules that we link dynamically
PyAPI_FUNC(int) _PyArg_Parse_SizeT(PyObject *, const char *, ...);
PyAPI_FUNC(int) _PyArg_ParseTuple_SizeT(PyObject *, const char *, ...);
PyAPI_FUNC(int) _PyArg_ParseTupleAndKeywords_SizeT(PyObject *, PyObject *,
                                                  const char *, char **, ...);
PyAPI_FUNC(int) _PyArg_VaParse_SizeT(PyObject *, const char *, va_list);
PyAPI_FUNC(int) _PyArg_VaParseTupleAndKeywords_SizeT(PyObject *, PyObject *,
                                                  const char *, char **, va_list);
PyAPI_FUNC(int) _PyArg_VaParseTupleAndKeywordsFast_SizeT(PyObject *, PyObject *, struct _PyArg_Parser *, va_list);
PyAPI_FUNC(int) _PyArg_ParseTupleAndKeywordsFast_SizeT(PyObject *, PyObject *, struct _PyArg_Parser *, ...);
PyAPI_FUNC(int) _PyArg_ParseStack_SizeT(PyObject **, Py_ssize_t, const char*, ...);

static int getbuffer(PyObject *arg, Py_buffer *view, const char **errmsg) {
    if (PyObject_GetBuffer(arg, view, PyBUF_SIMPLE) != 0) {
        *errmsg = "bytes-like object";
        return -1;
    }
    if (!PyBuffer_IsContiguous(view, 'C')) {
        PyBuffer_Release(view);
        *errmsg = "contiguous buffer";
        return -1;
    }
    return 0;
}

PyAPI_FUNC(int) get_buffer_r(PyObject *arg, Py_buffer *view) {
    if (PyObject_GetBuffer(arg, view, PyBUF_SIMPLE) != 0) {
        return -1;
    }
    if (!PyBuffer_IsContiguous(view, 'C')) {
        PyBuffer_Release(view);
        return -2;
    }
    return 0;
}

PyAPI_FUNC(int) get_buffer_rw(PyObject *arg, Py_buffer *view) {
    if (PyObject_GetBuffer(arg, view, PyBUF_WRITABLE) != 0) {
        PyErr_Clear();
        return -1;
    }
    if (!PyBuffer_IsContiguous(view, 'C')) {
        PyBuffer_Release(view);
        return -2;
    }
    return 0;
}

PyAPI_FUNC(Py_ssize_t) convertbuffer(PyObject *arg, const void **p) {
    PyBufferProcs *pb = Py_TYPE(arg)->tp_as_buffer;
    Py_ssize_t count;
    Py_buffer view;

    *p = NULL;
    if (pb != NULL && pb->bf_releasebuffer != NULL) {
        // *errmsg = "read-only bytes-like object";
        return -3;
    }

    int get_buffer_result = get_buffer_r(arg, &view);
    if (get_buffer_result < 0) {
        return get_buffer_result;
    }
    count = view.len;
    *p = view.buf;
    PyBuffer_Release(&view);
    return count;
}

/* argparse */

int PyArg_VaParseTupleAndKeywords(PyObject *argv, PyObject *kwds, const char *format, char **kwdnames, va_list va) {
	va_list lva;
	va_copy(lva, va);
    int result = GraalPyTruffle_Arg_ParseTupleAndKeywords(argv, kwds, format, kwdnames, &lva);
    va_end(lva);
    return result;
}

int _PyArg_VaParseTupleAndKeywords_SizeT(PyObject *argv, PyObject *kwds, const char *format, char **kwdnames, va_list va) {
	va_list lva;
	va_copy(lva, va);
    int result = GraalPyTruffle_Arg_ParseTupleAndKeywords(argv, kwds, format, kwdnames, &lva);
    va_end(lva);
    return result;
}

int PyArg_ParseTupleAndKeywords(PyObject *argv, PyObject *kwds, const char *format, char** kwdnames, ...) {
    va_list __vl;
    va_start(__vl, kwdnames);
    int result = GraalPyTruffle_Arg_ParseTupleAndKeywords(argv, kwds, format, kwdnames, &__vl);
    va_end(__vl);
    return result;
}


int _PyArg_ParseTupleAndKeywords_SizeT(PyObject *argv, PyObject *kwds, const char *format, char** kwdnames, ...) {
    va_list __vl;
    va_start(__vl, kwdnames);
    int result = GraalPyTruffle_Arg_ParseTupleAndKeywords(argv, kwds, format, kwdnames, &__vl);
    va_end(__vl);
    return result;
}

NO_INLINE
int PyArg_ParseStack(PyObject **args, Py_ssize_t nargs, const char* format, ...) {
    va_list __vl;
    va_start(__vl, format);
    int result = GraalPyTruffle_Arg_ParseArrayAndKeywords(args, nargs, NULL, format, NULL, &__vl);
    va_end(__vl);
    return result;
}

NO_INLINE
int _PyArg_ParseStack_SizeT(PyObject **args, Py_ssize_t nargs, const char* format, ...) {
    va_list __vl;
    va_start(__vl, format);
    int result = GraalPyTruffle_Arg_ParseArrayAndKeywords(args, nargs, NULL, format, NULL, &__vl);
    va_end(__vl);
    return result;
}

int _PyArg_VaParseTupleAndKeywordsFast(PyObject *args, PyObject *kwargs, struct _PyArg_Parser *parser, va_list va) {
    return _PyArg_VaParseTupleAndKeywords_SizeT(args, kwargs, parser->format, parser->keywords, va);
}

int _PyArg_VaParseTupleAndKeywordsFast_SizeT(PyObject *args, PyObject *kwargs, struct _PyArg_Parser *parser, va_list va) {
    return _PyArg_VaParseTupleAndKeywords_SizeT(args, kwargs, parser->format, parser->keywords, va);
}

int _PyArg_ParseTupleAndKeywordsFast(PyObject *args, PyObject *kwargs, struct _PyArg_Parser *parser, ...) {
    va_list __vl;
    va_start(__vl, parser);
    int result = GraalPyTruffle_Arg_ParseTupleAndKeywords(args, kwargs, parser->format, parser->keywords, &__vl);
    va_end(__vl);
    return result;
}

int _PyArg_ParseTupleAndKeywordsFast_SizeT(PyObject *args, PyObject *kwargs, struct _PyArg_Parser *parser, ...) {
    va_list __vl;
    va_start(__vl, parser);
    int result = GraalPyTruffle_Arg_ParseTupleAndKeywords(args, kwargs, parser->format, parser->keywords, &__vl);
    va_end(__vl);
    return result;
}


NO_INLINE
int PyArg_ParseTuple(PyObject *args, const char *format, ...) {
    va_list __vl;
    va_start(__vl, format);
    int result = GraalPyTruffle_Arg_ParseTupleAndKeywords(args, NULL, format, NULL, &__vl);
    va_end(__vl);
	return result;
}

NO_INLINE
int _PyArg_ParseTuple_SizeT(PyObject *args, const char *format, ...) {
    va_list __vl;
    va_start(__vl, format);
    int result = GraalPyTruffle_Arg_ParseTupleAndKeywords(args, NULL, format, NULL, &__vl);
    va_end(__vl);
	return result;
}

int PyArg_VaParse(PyObject *args, const char *format, va_list va) {
    return _PyArg_VaParseTupleAndKeywords_SizeT(args, NULL, format, NULL, va);
}

int _PyArg_VaParse_SizeT(PyObject *args, const char *format, va_list va) {
    return _PyArg_VaParseTupleAndKeywords_SizeT(args, NULL, format, NULL, va);
}

NO_INLINE
int PyArg_Parse(PyObject *args, const char *format, ...) {
    va_list __vl;
    va_start(__vl, format);
    int result = GraalPyTruffle_Arg_ParseArrayAndKeywords(&args, 1, NULL, format, NULL, &__vl);
    va_end(__vl);
	return result;
}

NO_INLINE
int _PyArg_Parse_SizeT(PyObject *args, const char *format, ...) {
    va_list __vl;
    va_start(__vl, format);
    int result = GraalPyTruffle_Arg_ParseArrayAndKeywords(&args, 1, NULL, format, NULL, &__vl);
    va_end(__vl);
    return result;
}
