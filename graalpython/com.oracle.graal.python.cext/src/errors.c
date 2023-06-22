/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
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

#undef PyErr_BadInternalCall
void PyErr_BadInternalCall(void) {
    assert(0 && "bad argument to internal function");
    PyErr_SetString(PyExc_SystemError, "bad argument to internal function");
}
#define PyErr_BadInternalCall() _PyErr_BadInternalCall(__FILE__, __LINE__)


void PyErr_SetString(PyObject *exception, const char *string) {
    PyObject *value = PyUnicode_FromString(string);
    PyErr_SetObject(exception, value);
}

void PyErr_SetObject(PyObject *exception, PyObject *value) {
	Graal_PyTruffleErr_CreateAndSetException(exception, value);
}

void PyErr_Clear(void) {
    PyErr_Restore(NULL, NULL, NULL);
}

void PyErr_SetNone(PyObject *exception) {
	Graal_PyTruffleErr_CreateAndSetException(exception, Py_None);
}

void PyErr_Fetch(PyObject **p_type, PyObject **p_value, PyObject **p_traceback) {
    PyObject* result = GraalPyTruffleErr_Fetch();
    if(result == NULL) {
    	*p_type = NULL;
    	*p_value = NULL;
    	*p_traceback = NULL;
    } else {
    	*p_type = PyTuple_GetItem(result, 0);
    	*p_value = PyTuple_GetItem(result, 1);
    	*p_traceback = PyTuple_GetItem(result, 2);
        Py_XINCREF(*p_type);
        Py_XINCREF(*p_value);
        Py_XINCREF(*p_traceback);
        Py_DecRef(result);
    }
}

void PyErr_GetExcInfo(PyObject **p_type, PyObject **p_value, PyObject **p_traceback) {
    PyObject* result = GraalPyTruffleErr_GetExcInfo();
    if(result == NULL) {
    	*p_type = NULL;
    	*p_value = NULL;
    	*p_traceback = NULL;
    } else {
    	*p_type = PyTuple_GetItem(result, 0);
    	*p_value = PyTuple_GetItem(result, 1);
    	*p_traceback = PyTuple_GetItem(result, 2);
        Py_XINCREF(*p_type);
        Py_XINCREF(*p_value);
        Py_XINCREF(*p_traceback);
        Py_DecRef(result);
    }
}

// taken from CPython "Python/errors.c"
void PyErr_Print(void) {
    PyErr_PrintEx(1);
}

// taken from CPython "Python/errors.c"
int PyErr_BadArgument(void) {
    PyErr_SetString(PyExc_TypeError, "bad argument type for built-in operation");
    return 0;
}

// partially taken from CPython "Python/errors.c"
PyObject * PyErr_NoMemory(void) {
    PyErr_SetNone(PyExc_MemoryError);
    return NULL;
}

// taken from CPython "Python/errors.c"
int PyErr_ExceptionMatches(PyObject *exc) {
    return PyErr_GivenExceptionMatches(PyErr_Occurred(), exc);
}

NO_INLINE
PyObject* PyErr_Format(PyObject* exception, const char* fmt, ...) {
    va_list args;
    va_start(args, fmt);
    PyObject* formatted_msg = PyUnicode_FromFormatV(fmt, args);
    va_end(args);
    Graal_PyTruffleErr_CreateAndSetException(exception, formatted_msg);
    return NULL;
}

NO_INLINE
PyObject* PyErr_FormatV(PyObject* exception, const char* fmt, va_list va) {
    va_list args;
    va_copy(args, va);
    PyObject* formatted_msg = PyUnicode_FromFormatV(fmt, args);
    va_end(args);
    Graal_PyTruffleErr_CreateAndSetException(exception, formatted_msg);
    return NULL;
}

void PyErr_WriteUnraisable(PyObject *obj) {
    _PyErr_WriteUnraisableMsg(NULL, obj);
}

void PyErr_NormalizeException(PyObject **exc, PyObject **val, PyObject **tb) {
    // (tfel): nothing to do here from our side, the exception is already
    // reified
    return;
}

// taken from CPython "Python/errors.c"
PyObject* PyErr_SetFromErrno(PyObject* exc) {
    return PyErr_SetFromErrnoWithFilenameObjects(exc, NULL, NULL);
}

// taken from CPython "Python/errors.c"
PyObject *
PyErr_SetFromErrnoWithFilename(PyObject *exc, const char *filename)
{
    PyObject *name = filename ? PyUnicode_DecodeFSDefault(filename) : NULL;
    PyObject *result = PyErr_SetFromErrnoWithFilenameObjects(exc, name, NULL);
    Py_XDECREF(name);
    return result;
}

// taken from CPython "Python/errors.c"
PyObject* PyErr_SetFromErrnoWithFilenameObject(PyObject* exc, PyObject* filenameObject) {
    return PyErr_SetFromErrnoWithFilenameObjects(exc, filenameObject, NULL);
}

// partially taken from CPython "Python/errors.c"
PyObject* PyErr_SetFromErrnoWithFilenameObjects(PyObject* exc, PyObject* filenameObject, PyObject* filenameObject2) {
    PyObject *message;
    PyObject *v, *args;
    int i = errno;

    if (i != 0) {
        char *s = strerror(i);
        // TODO(fa): use PyUnicode_DecodeLocale once available
        // message = PyUnicode_DecodeLocale(s, "surrogateescape");
        message = PyUnicode_FromString(s);
    }
    else {
        /* Sometimes errno didn't get set */
        message = PyUnicode_FromString("Error");
    }

    if (message == NULL)
    {
        return NULL;
    }

    if (filenameObject != NULL) {
        if (filenameObject2 != NULL)
            args = Py_BuildValue("(iOOiO)", i, message, filenameObject, 0, filenameObject2);
        else
            args = Py_BuildValue("(iOO)", i, message, filenameObject);
    } else {
        assert(filenameObject2 == NULL);
        args = Py_BuildValue("(iO)", i, message);
    }
    Py_DECREF(message);

    if (args != NULL) {
        v = PyObject_Call(exc, args, NULL);
        Py_DECREF(args);
        if (v != NULL) {
            PyErr_SetObject((PyObject *) Py_TYPE(v), v);
            Py_DECREF(v);
        }
    }
    return NULL;
}
