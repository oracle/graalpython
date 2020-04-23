# Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# The Universal Permissive License (UPL), Version 1.0
#
# Subject to the condition set forth below, permission is hereby granted to any
# person obtaining a copy of this software, associated documentation and/or
# data (collectively the "Software"), free of charge and under any and all
# copyright rights in the Software, and any and all patent rights owned or
# freely licensable by each licensor hereunder covering either (i) the
# unmodified Software as contributed to or provided by such licensor, or (ii)
# the Larger Works (as defined below), to deal in both
#
# (a) the Software, and
#
# (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
# one is included with the Software each a "Larger Work" to which the Software
# is contributed by such licensors),
#
# without restriction, including without limitation the rights to copy, create
# derivative works of, display, perform, and distribute the Software and make,
# use, sell, offer for sale, import, export, have made, and have sold the
# Software and the Larger Work(s), and to sublicense the foregoing rights on
# either these or other terms.
#
# This license is subject to the following condition:
#
# The above copyright notice and either this complete permission notice or at a
# minimum a reference to the UPL must be included in all copies or substantial
# portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
# SOFTWARE.

code = """
#include "Python.h"

typedef struct {
    PyObject_HEAD;
    PyObject* it;
    uint64_t scale;
} NativeCustomIterableObject;


typedef struct {
    PyObject_HEAD;
    NativeCustomIterableObject* obj;
    uint64_t pos;
} NativeCustomIteratorObject;

PyObject* ci_item(PyObject* self, Py_ssize_t i);
int ci_init(PyObject* self, PyObject* args, PyObject* kwds);

PyObject* ci_iter(PyObject* self) {
    PyObject* result = ((NativeCustomIterableObject*)self)->it;
    Py_INCREF(result);
    return result;
}

PyObject* cit_iter(PyObject* self) {
    Py_INCREF(self);
    return self;
}

PyObject* cit_next(PyObject* self) {
    NativeCustomIteratorObject* s = (NativeCustomIteratorObject*)self;
    return ci_item((PyObject*)(s->obj), (s->pos)++);
}

PyObject* ci_item(PyObject* self, Py_ssize_t i) {
    return PyLong_FromSsize_t(((NativeCustomIterableObject*)self)->scale * i);
}


static PySequenceMethods ci_sequence_methods = {
    0,
    0,
    0,
    ci_item,
    0,
    0,
    0,
    0,
    0,
    0
};


static struct PyMethodDef ci_methods[] = {
    {NULL, NULL, 0, NULL}
};

static PyTypeObject CustomIteratorType = {
    PyVarObject_HEAD_INIT(NULL, 0)
        "NativeCustomIterable.NativeCustomIterator",
    sizeof(NativeCustomIteratorObject),       /* tp_basicsize */
    0,                          /* tp_itemsize */
    0,                          /* tp_dealloc */
    0,
    0,
    0,
    0,                          /* tp_reserved */
    0,
    0,
    0,
    0,
    0,
    0,
    0,
    0,
    0,
    0,
    Py_TPFLAGS_DEFAULT,
    0,
    0,                          /* tp_traverse */
    0,                          /* tp_clear */
    0,                          /* tp_richcompare */
    0,                          /* tp_weaklistoffset */
    cit_iter,                   /* tp_iter */
    cit_next,                   /* tp_iternext */
    ci_methods,                 /* tp_methods */
    NULL,                       /* tp_members */
    0,                          /* tp_getset */
    0,                          /* tp_base */
    0,                          /* tp_dict */
    0,                          /* tp_descr_get */
    0,                          /* tp_descr_set */
    0,                          /* tp_dictoffset */
    0,                          /* tp_init */
    PyType_GenericAlloc,        /* tp_alloc */
    PyType_GenericNew,          /* tp_new */
    PyObject_Del,               /* tp_free */
};

static PyTypeObject CustomIterableType = {
    PyVarObject_HEAD_INIT(NULL, 0)
        "NativeCustomIterable.NativeCustomIterable",
    sizeof(NativeCustomIterableObject),       /* tp_basicsize */
    0,                          /* tp_itemsize */
    0,                          /* tp_dealloc */
    0,
    0,
    0,
    0,                          /* tp_reserved */
    0,
    0,
    &ci_sequence_methods,
    0,
    0,
    0,
    0,
    0,
    0,
    0,
    Py_TPFLAGS_DEFAULT,
    0,
    0,                          /* tp_traverse */
    0,                          /* tp_clear */
    0,                          /* tp_richcompare */
    0,                          /* tp_weaklistoffset */
    ci_iter,                    /* tp_iter */
    0,                          /* tp_iternext */
    ci_methods,                 /* tp_methods */
    NULL,                       /* tp_members */
    0,                          /* tp_getset */
    0,                          /* tp_base */
    0,                          /* tp_dict */
    0,                          /* tp_descr_get */
    0,                          /* tp_descr_set */
    0,                          /* tp_dictoffset */
    ci_init,                    /* tp_init */
    PyType_GenericAlloc,        /* tp_alloc */
    PyType_GenericNew,          /* tp_new */
    PyObject_Del,               /* tp_free */
};

int ci_init(PyObject* self, PyObject* args, PyObject* kwds) {
    Py_XINCREF(args);
    Py_XINCREF(kwds);
    static char *kwlist[] = {"scale", NULL};
    Py_ssize_t n = 0;

    if (!PyArg_ParseTupleAndKeywords(args, kwds, "n", kwlist, &n)) {
        return NULL;
    }
    NativeCustomIterableObject* tself = (NativeCustomIterableObject*)self;
    tself->scale = n + 1;

    PyObject *argList = PyTuple_New(0);
    Py_INCREF(argList);
    PyObject *obj = PyObject_CallObject((PyObject *) &CustomIteratorType, argList);
    Py_DECREF(argList);
    Py_INCREF(obj);
    Py_INCREF(tself);
    ((NativeCustomIteratorObject*)obj)->obj = tself;
    tself->it = obj;

    return 0;
}

static PyModuleDef custom_iterable_module = {
    PyModuleDef_HEAD_INIT,
    "c_custom_iterable_module",
    "",
    -1,
    NULL, NULL, NULL, NULL, NULL
};

PyMODINIT_FUNC
PyInit_c_custom_iterable_module(void)
{
    PyObject* m;

    if (PyType_Ready(&CustomIterableType) < 0)
        return NULL;

    if (PyType_Ready(&CustomIteratorType) < 0)
        return NULL;

    m = PyModule_Create(&custom_iterable_module);
    if (m == NULL)
        return NULL;

    Py_INCREF(&CustomIterableType);
    Py_INCREF(&CustomIteratorType);
    PyModule_AddObject(m, "NativeCustomIterable", (PyObject *)&CustomIterableType);
    PyModule_AddObject(m, "NativeCustomIterator", (PyObject *)&CustomIteratorType);
    Py_INCREF(m);
    return m;
}

"""


ccompile("c_custom_iterable_module", code)
import c_custom_iterable_module

def count(num):
    idxObj = c_custom_iterable_module.NativeCustomIterable(num % 11)
    for t in range(num):
        it = iter(idxObj)
        val0 = next(it)
        val1 = next(it)
        val2 = next(it)

    return (val0, val1, val2)


def measure(num):
    result = count(num)
    print("result = " + str(result))


def __benchmark__(num=1000000):
    measure(num)
