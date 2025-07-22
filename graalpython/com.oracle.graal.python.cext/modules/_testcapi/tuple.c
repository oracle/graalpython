/* Copyright (c) 2024, Oracle and/or its affiliates.
 * Copyright (C) 1996-2024 Python Software Foundation
 *
 * Licensed under the PYTHON SOFTWARE FOUNDATION LICENSE VERSION 2
 */
#include "parts.h"
#include "util.h"


static PyObject *
tuple_get_size(PyObject *Py_UNUSED(module), PyObject *obj)
{
    NULLABLE(obj);
    RETURN_SIZE(PyTuple_GET_SIZE(obj));
}

static PyObject *
tuple_get_item(PyObject *Py_UNUSED(module), PyObject *args)
{
    PyObject *obj;
    Py_ssize_t i;
    if (!PyArg_ParseTuple(args, "On", &obj, &i)) {
        return NULL;
    }
    NULLABLE(obj);
    return Py_XNewRef(PyTuple_GET_ITEM(obj, i));
}

static PyObject *
tuple_copy(PyObject *tuple)
{
    Py_ssize_t size = PyTuple_GET_SIZE(tuple);
    PyObject *newtuple = PyTuple_New(size);
    if (!newtuple) {
        return NULL;
    }
    for (Py_ssize_t n = 0; n < size; n++) {
        PyTuple_SET_ITEM(newtuple, n, Py_XNewRef(PyTuple_GET_ITEM(tuple, n)));
    }
    return newtuple;
}

static PyObject *
tuple_set_item(PyObject *Py_UNUSED(module), PyObject *args)
{
    PyObject *obj, *value, *newtuple;
    Py_ssize_t i;
    if (!PyArg_ParseTuple(args, "OnO", &obj, &i, &value)) {
        return NULL;
    }
    NULLABLE(value);
    if (PyTuple_CheckExact(obj)) {
        newtuple = tuple_copy(obj);
        if (!newtuple) {
            return NULL;
        }

        PyObject *val = PyTuple_GET_ITEM(newtuple, i);
        PyTuple_SET_ITEM(newtuple, i, Py_XNewRef(value));
        Py_DECREF(val);
        return newtuple;
    }
    else {
        NULLABLE(obj);

        PyObject *val = PyTuple_GET_ITEM(obj, i);
        PyTuple_SET_ITEM(obj, i, Py_XNewRef(value));
        Py_DECREF(val);
        return Py_XNewRef(obj);
    }
}

static PyObject *
_tuple_resize(PyObject *Py_UNUSED(module), PyObject *args)
{
    PyObject *tup;
    Py_ssize_t newsize;
    int new = 1;
    if (!PyArg_ParseTuple(args, "On|p", &tup, &newsize, &new)) {
        return NULL;
    }
    if (new) {
        tup = tuple_copy(tup);
        if (!tup) {
            return NULL;
        }
    }
    else {
        NULLABLE(tup);
        Py_XINCREF(tup);
    }
    int r = _PyTuple_Resize(&tup, newsize);
    if (r == -1) {
        assert(tup == NULL);
        return NULL;
    }
    return tup;
}

static PyObject *
_check_tuple_item_is_NULL(PyObject *Py_UNUSED(module), PyObject *args)
{
    PyObject *obj;
    Py_ssize_t i;
    if (!PyArg_ParseTuple(args, "On", &obj, &i)) {
        return NULL;
    }
    return PyLong_FromLong(PyTuple_GET_ITEM(obj, i) == NULL);
}

static PyObject *
tuple_check(PyObject* Py_UNUSED(module), PyObject *obj)
{
    NULLABLE(obj);
    return PyLong_FromLong(PyTuple_Check(obj));
}

static PyObject *
tuple_checkexact(PyObject* Py_UNUSED(module), PyObject *obj)
{
    NULLABLE(obj);
    return PyLong_FromLong(PyTuple_CheckExact(obj));
}

static PyObject *
tuple_new(PyObject* Py_UNUSED(module), PyObject *len)
{
    return PyTuple_New(PyLong_AsSsize_t(len));
}

static PyObject *
tuple_pack(PyObject *Py_UNUSED(module), PyObject *args)
{
    PyObject *arg1 = NULL, *arg2 = NULL;
    Py_ssize_t size;

    if (!PyArg_ParseTuple(args, "n|OO", &size, &arg1, &arg2)) {
        return NULL;
    }
    if (arg1) {
        NULLABLE(arg1);
        if (arg2) {
            NULLABLE(arg2);
            return PyTuple_Pack(size, arg1, arg2);
        }
        return PyTuple_Pack(size, arg1);
    }
    return PyTuple_Pack(size);
}

static PyObject *
tuple_size(PyObject *Py_UNUSED(module), PyObject *obj)
{
    NULLABLE(obj);
    RETURN_SIZE(PyTuple_Size(obj));
}

static PyObject *
tuple_getitem(PyObject *Py_UNUSED(module), PyObject *args)
{
    PyObject *obj;
    Py_ssize_t i;
    if (!PyArg_ParseTuple(args, "On", &obj, &i)) {
        return NULL;
    }
    NULLABLE(obj);
    return Py_XNewRef(PyTuple_GetItem(obj, i));
}

static PyObject *
tuple_getslice(PyObject *Py_UNUSED(module), PyObject *args)
{
    PyObject *obj;
    Py_ssize_t ilow, ihigh;
    if (!PyArg_ParseTuple(args, "Onn", &obj, &ilow, &ihigh)) {
        return NULL;
    }
    NULLABLE(obj);
    return PyTuple_GetSlice(obj, ilow, ihigh);
}

static PyObject *
tuple_setitem(PyObject *Py_UNUSED(module), PyObject *args)
{
    PyObject *obj, *value, *newtuple = NULL;
    Py_ssize_t i;
    if (!PyArg_ParseTuple(args, "OnO", &obj, &i, &value)) {
        return NULL;
    }
    NULLABLE(value);
    if (PyTuple_CheckExact(obj)) {
        Py_ssize_t size = PyTuple_Size(obj);
        newtuple = PyTuple_New(size);
        if (!newtuple) {
            return NULL;
        }
        for (Py_ssize_t n = 0; n < size; n++) {
            if (PyTuple_SetItem(newtuple, n,
                                Py_XNewRef(PyTuple_GetItem(obj, n))) == -1) {
                Py_DECREF(newtuple);
                return NULL;
            }
        }

        if (PyTuple_SetItem(newtuple, i, Py_XNewRef(value)) == -1) {
            Py_DECREF(newtuple);
            return NULL;
        }
        return newtuple;
    }
    else {
        NULLABLE(obj);

        if (PyTuple_SetItem(obj, i, Py_XNewRef(value)) == -1) {
            return NULL;
        }
        return Py_XNewRef(obj);
    }
}


static PyMethodDef test_methods[] = {
    {"tuple_get_size", tuple_get_size, METH_O},
    {"tuple_get_item", tuple_get_item, METH_VARARGS},
    {"tuple_set_item", tuple_set_item, METH_VARARGS},
    {"_tuple_resize", _tuple_resize, METH_VARARGS},
    {"_check_tuple_item_is_NULL", _check_tuple_item_is_NULL, METH_VARARGS},
    /* Limited C API */
    {"tuple_check", tuple_check, METH_O},
    {"tuple_checkexact", tuple_checkexact, METH_O},
    {"tuple_new", tuple_new, METH_O},
    {"tuple_pack", tuple_pack, METH_VARARGS},
    {"tuple_size", tuple_size, METH_O},
    {"tuple_getitem", tuple_getitem, METH_VARARGS},
    {"tuple_getslice", tuple_getslice, METH_VARARGS},
    {"tuple_setitem", tuple_setitem, METH_VARARGS},
    {NULL},
};

int
_PyTestCapi_Init_Tuple(PyObject *m)
{
    if (PyModule_AddFunctions(m, test_methods) < 0) {
        return -1;
    }

    return 0;
}
