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
#include "pycore_pyerrors.h"

void
_PyErr_Restore(PyThreadState *tstate, PyObject *type, PyObject *value,
               PyObject *traceback)
{
    // GraalPy change: different implementation
    PyErr_Restore(type, value, traceback);
}

#undef PyErr_BadInternalCall
void PyErr_BadInternalCall(void) {
    assert(0 && "bad argument to internal function");
    PyErr_SetString(PyExc_SystemError, "bad argument to internal function");
}
#define PyErr_BadInternalCall() _PyErr_BadInternalCall(__FILE__, __LINE__)


void
_PyErr_SetString(PyThreadState *tstate, PyObject *exception,
                 const char *string)
{
    PyObject *value = PyUnicode_FromString(string);
    if (value != NULL) {
        _PyErr_SetObject(tstate, exception, value);
        Py_DECREF(value);
    }
}

void
PyErr_SetString(PyObject *exception, const char *string)
{
    // GraalPy change: don't get thread state
    _PyErr_SetString(NULL, exception, string);
}

void
_PyErr_SetObject(PyThreadState *tstate, PyObject *exception, PyObject *value)
{
	Graal_PyTruffleErr_CreateAndSetException(exception, value);
}

void
PyErr_SetObject(PyObject *exception, PyObject *value)
{
    // GraalPy change: don't get thread state
    _PyErr_SetObject(NULL, exception, value);
}

void
_PyErr_Clear(PyThreadState *tstate)
{
    PyErr_Restore(NULL, NULL, NULL);
}

void PyErr_Clear(void) {
    PyErr_Restore(NULL, NULL, NULL);
}

void
_PyErr_SetNone(PyThreadState *tstate, PyObject *exception)
{
    _PyErr_SetObject(tstate, exception, (PyObject *)NULL);
}


void
PyErr_SetNone(PyObject *exception)
{
    // GraalPy change: don't get thread state
    _PyErr_SetNone(NULL, exception);
}

void
_PyErr_Fetch(PyThreadState *tstate, PyObject **p_type, PyObject **p_value,
             PyObject **p_traceback)
{
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

void
PyErr_Fetch(PyObject **p_type, PyObject **p_value, PyObject **p_traceback)
{
    PyThreadState *tstate = NULL; // GraalPy change: don't get thread state
    _PyErr_Fetch(tstate, p_type, p_value, p_traceback);
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
PyObject *
_PyErr_NoMemory(PyThreadState *tstate)
{
    PyErr_SetNone(PyExc_MemoryError);
    return NULL;
}

PyObject *
PyErr_NoMemory(void)
{
    // GraalPy change: we don't use thread state
    return _PyErr_NoMemory(NULL);
}

// taken from CPython "Python/errors.c"
int PyErr_ExceptionMatches(PyObject *exc) {
    return PyErr_GivenExceptionMatches(PyErr_Occurred(), exc);
}

static PyObject *
_PyErr_FormatV(PyThreadState *tstate, PyObject *exception,
               const char *format, va_list vargs)
{
    PyObject* string;

    /* Issue #23571: PyUnicode_FromFormatV() must not be called with an
       exception set, it calls arbitrary Python code like PyObject_Repr() */
    _PyErr_Clear(tstate);

    string = PyUnicode_FromFormatV(format, vargs);
    if (string != NULL) {
        _PyErr_SetObject(tstate, exception, string);
        Py_DECREF(string);
    }
    return NULL;
}

PyObject *
PyErr_FormatV(PyObject *exception, const char *format, va_list vargs)
{
    PyThreadState *tstate = NULL; // GraalPy change: don't get thread state
    return _PyErr_FormatV(tstate, exception, format, vargs);
}


NO_INLINE // GraalPy change: disallow bitcode inlining
PyObject *
_PyErr_Format(PyThreadState *tstate, PyObject *exception,
              const char *format, ...)
{
    va_list vargs;
#ifdef HAVE_STDARG_PROTOTYPES
    va_start(vargs, format);
#else
    va_start(vargs);
#endif
    _PyErr_FormatV(tstate, exception, format, vargs);
    va_end(vargs);
    return NULL;
}


NO_INLINE // GraalPy change: disallow bitcode inlining
PyObject *
PyErr_Format(PyObject *exception, const char *format, ...)
{
    PyThreadState *tstate = NULL; // GrallPy change: don't get thread state
    va_list vargs;
#ifdef HAVE_STDARG_PROTOTYPES
    va_start(vargs, format);
#else
    va_start(vargs);
#endif
    _PyErr_FormatV(tstate, exception, format, vargs);
    va_end(vargs);
    return NULL;
}


static PyObject *
_PyErr_FormatVFromCause(PyThreadState *tstate, PyObject *exception,
                        const char *format, va_list vargs)
{
    PyObject *exc, *val, *val2, *tb;

    assert(_PyErr_Occurred(tstate));
    _PyErr_Fetch(tstate, &exc, &val, &tb);
    _PyErr_NormalizeException(tstate, &exc, &val, &tb);
    if (tb != NULL) {
        PyException_SetTraceback(val, tb);
        Py_DECREF(tb);
    }
    Py_DECREF(exc);
    assert(!_PyErr_Occurred(tstate));

    _PyErr_FormatV(tstate, exception, format, vargs);

    _PyErr_Fetch(tstate, &exc, &val2, &tb);
    _PyErr_NormalizeException(tstate, &exc, &val2, &tb);
    Py_INCREF(val);
    PyException_SetCause(val2, val);
    PyException_SetContext(val2, val);
    _PyErr_Restore(tstate, exc, val2, tb);

    return NULL;
}

NO_INLINE // GraalPy change: disallow bitcode inlining
PyObject *
_PyErr_FormatFromCauseTstate(PyThreadState *tstate, PyObject *exception,
                             const char *format, ...)
{
    va_list vargs;
#ifdef HAVE_STDARG_PROTOTYPES
    va_start(vargs, format);
#else
    va_start(vargs);
#endif
    _PyErr_FormatVFromCause(tstate, exception, format, vargs);
    va_end(vargs);
    return NULL;
}

NO_INLINE // GraalPy change: disallow bitcode inlining
PyObject *
_PyErr_FormatFromCause(PyObject *exception, const char *format, ...)
{
    PyThreadState *tstate = NULL; // GraalPy change: don't get thread state
    va_list vargs;
#ifdef HAVE_STDARG_PROTOTYPES
    va_start(vargs, format);
#else
    va_start(vargs);
#endif
    _PyErr_FormatVFromCause(tstate, exception, format, vargs);
    va_end(vargs);
    return NULL;
}

void PyErr_WriteUnraisable(PyObject *obj) {
    _PyErr_WriteUnraisableMsg(NULL, obj);
}

void
_PyErr_NormalizeException(PyThreadState *tstate, PyObject **exc,
                          PyObject **val, PyObject **tb)
{
    // (tfel): nothing to do here from our side, the exception is already
    // reified
    return;
}

void
PyErr_NormalizeException(PyObject **exc, PyObject **val, PyObject **tb)
{
    PyThreadState *tstate = NULL; // GraalPy change: don't get thread state
    _PyErr_NormalizeException(tstate, exc, val, tb);
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
