/* Copyright (c) 2018, 2019, Oracle and/or its affiliates.
 * Copyright (C) 1996-2017 Python Software Foundation
 *
 * Licensed under the PYTHON SOFTWARE FOUNDATION LICENSE VERSION 2
 */

#include "capi.h"

PyTypeObject PyEllipsis_Type = PY_TRUFFLE_TYPE("ellipsis", &PyType_Type, Py_TPFLAGS_DEFAULT, 0);
PyTypeObject PySlice_Type = PY_TRUFFLE_TYPE("slice", &PyType_Type, Py_TPFLAGS_DEFAULT | Py_TPFLAGS_HAVE_GC, sizeof(PySliceObject));

PyObject _Py_EllipsisObject = {
    _PyObject_EXTRA_INIT
    1, &PyEllipsis_Type
};

// Taken from CPython ceval.c
/* Extract a slice index from a PyLong or an object with the
   nb_index slot defined, and store in *pi.
   Silently reduce values larger than PY_SSIZE_T_MAX to PY_SSIZE_T_MAX,
   and silently boost values less than PY_SSIZE_T_MIN to PY_SSIZE_T_MIN.
   Return 0 on error, 1 on success. */
int _PyEval_SliceIndex(PyObject *v, Py_ssize_t *pi) {
    if (v != Py_None) {
        Py_ssize_t x;
        if (PyIndex_Check(v)) {
            x = PyNumber_AsSsize_t(v, NULL);
            if (x == -1 && PyErr_Occurred())
                return 0;
        }
        else {
            PyErr_SetString(PyExc_TypeError,
                            "slice indices must be integers or "
                            "None or have an __index__ method");
            return 0;
        }
        *pi = x;
    }
    return 1;
}

// Taken from CPython
int PySlice_Unpack(PyObject *_r, Py_ssize_t *start, Py_ssize_t *stop, Py_ssize_t *step) {
    PySliceObject *r = (PySliceObject*)_r;
    /* this is harder to get right than you might think */

    if (r->step == Py_None) {
        *step = 1;
    }
    else {
        if (!_PyEval_SliceIndex(r->step, step)) return -1;
        if (*step == 0) {
            PyErr_SetString(PyExc_ValueError,
                            "slice step cannot be zero");
            return -1;
        }
        /* Here *step might be -PY_SSIZE_T_MAX-1; in this case we replace it
         * with -PY_SSIZE_T_MAX.  This doesn't affect the semantics, and it
         * guards against later undefined behaviour resulting from code that
         * does "step = -step" as part of a slice reversal.
         */
        if (*step < -PY_SSIZE_T_MAX)
            *step = -PY_SSIZE_T_MAX;
    }

    if (r->start == Py_None) {
        *start = *step < 0 ? PY_SSIZE_T_MAX : 0;
    }
    else {
        if (!_PyEval_SliceIndex(r->start, start)) return -1;
    }

    if (r->stop == Py_None) {
        *stop = *step < 0 ? PY_SSIZE_T_MIN : PY_SSIZE_T_MAX;
    }
    else {
        if (!_PyEval_SliceIndex(r->stop, stop)) return -1;
    }

    return 0;
}

// taken from CPython
Py_ssize_t PySlice_AdjustIndices(Py_ssize_t length, Py_ssize_t *start, Py_ssize_t *stop, Py_ssize_t step) {
    /* this is harder to get right than you might think */

    assert(step != 0);
    assert(step >= -PY_SSIZE_T_MAX);

    if (*start < 0) {
        *start += length;
        if (*start < 0) {
            *start = (step < 0) ? -1 : 0;
        }
    }
    else if (*start >= length) {
        *start = (step < 0) ? length - 1 : length;
    }

    if (*stop < 0) {
        *stop += length;
        if (*stop < 0) {
            *stop = (step < 0) ? -1 : 0;
        }
    }
    else if (*stop >= length) {
        *stop = (step < 0) ? length - 1 : length;
    }

    if (step < 0) {
        if (*stop < *start) {
            return (*start - *stop - 1) / (-step) + 1;
        }
    }
    else {
        if (*start < *stop) {
            return (*stop - *start - 1) / step + 1;
        }
    }
    return 0;
}

UPCALL_ID(PySlice_New);
PyObject* PySlice_New(PyObject* start, PyObject *stop, PyObject *step) {
    return UPCALL_CEXT_O(_jls_PySlice_New, native_to_java(start), native_to_java(stop), native_to_java(step));
}
