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

// partially taken from CPython "Python/_warnings.c"
MUST_INLINE static int warn_unicode(PyObject *category, PyObject *message, Py_ssize_t stack_level, PyObject *source) {
    if (category == NULL) {
        category = PyExc_RuntimeWarning;
    }

    PyObject* result = Graal_PyTruffleErr_Warn(message, category, stack_level, source);
    if(result == NULL) {
    	return -1;
    }
    Py_DECREF(result);
    return 0;
}

MUST_INLINE static PyObject* warn_explicit(PyObject *category, PyObject *message,
              PyObject *filename, int lineno,
              PyObject *module, PyObject *registry) {
    return GraalPyTruffleErr_WarnExplicit(category, message, filename, lineno, module, registry);
}

// taken from CPython "Python/_warnings.c"
int PyErr_WarnEx(PyObject *category, const char *text, Py_ssize_t stack_level) {
    PyObject *message = PyUnicode_FromString(text);
    if (message == NULL)
        return -1;
    int ret = warn_unicode(category, message, stack_level, NULL);
    Py_DECREF(message);
    return ret;
}

NO_INLINE int PyErr_WarnFormat(PyObject *category, Py_ssize_t stack_level, const char *format, ...) {
    va_list args;
    va_start(args, format);
    PyObject* message = PyUnicode_FromFormatV(format, args);
    va_end(args);
    int ret = warn_unicode(category, message, stack_level, Py_None);
    Py_DECREF(message);
    return ret;
}

int PyErr_ResourceWarning(PyObject *source, Py_ssize_t stack_level, const char *format, ...) {
    va_list args;
    va_start(args, format);
    PyObject* message = PyUnicode_FromFormatV(format, args);
    va_end(args);
    int ret = warn_unicode(PyExc_ResourceWarning, message, stack_level, source);
    Py_DECREF(message);
    return ret;
}

// Adapted from CPython
int PyErr_WarnExplicitObject(PyObject *category, PyObject *message,
                         PyObject *filename, int lineno,
                         PyObject *module, PyObject *registry)
{
    PyObject *res;
    if (category == NULL)
        category = PyExc_RuntimeWarning;
    res = warn_explicit(category, message, filename, lineno,
                        module, registry);
    if (res == NULL)
        return -1;
    Py_DECREF(res);
    return 0;
}

// Adapted from CPython
int PyErr_WarnExplicit(PyObject *category, const char *text,
                   const char *filename_str, int lineno,
                   const char *module_str, PyObject *registry)
{
    PyObject *message = PyUnicode_FromString(text);
    PyObject *filename = PyUnicode_DecodeFSDefault(filename_str);
    PyObject *module = NULL;
    int ret = -1;

    if (message == NULL || filename == NULL)
        goto exit;
    if (module_str != NULL) {
        module = PyUnicode_FromString(module_str);
        if (module == NULL)
            goto exit;
    }

    ret = PyErr_WarnExplicitObject(category, message, filename, lineno,
                                   module, registry);

 exit:
    Py_XDECREF(message);
    Py_XDECREF(module);
    Py_XDECREF(filename);
    return ret;
}

// Adapted from CPython
int PyErr_WarnExplicitFormat(PyObject *category,
                         const char *filename_str, int lineno,
                         const char *module_str, PyObject *registry,
                         const char *format, ...)
{
    PyObject *message;
    PyObject *module = NULL;
    PyObject *filename = PyUnicode_DecodeFSDefault(filename_str);
    int ret = -1;
    va_list vargs;

    if (filename == NULL)
        goto exit;
    if (module_str != NULL) {
        module = PyUnicode_FromString(module_str);
        if (module == NULL)
            goto exit;
    }

#ifdef HAVE_STDARG_PROTOTYPES
    va_start(vargs, format);
#else
    va_start(vargs);
#endif
    message = PyUnicode_FromFormatV(format, vargs);
    if (message != NULL) {
        PyObject *res = warn_explicit(category, message, filename, lineno,
                            module, registry);
        Py_DECREF(message);
        if (res != NULL) {
            Py_DECREF(res);
            ret = 0;
        }
    }
    va_end(vargs);
exit:
    Py_XDECREF(module);
    Py_XDECREF(filename);
    return ret;
}

