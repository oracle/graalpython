# Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
} NativeCustomObject;

PyObject* nc_classmethod(PyObject* class, PyObject* args) {
    void** somememory = (void**)calloc(sizeof(PyObject*), 2);
    somememory[0] = class;
    somememory[1] = PyTuple_GET_ITEM(args, 0);
    int res = PyType_IsSubtype((PyTypeObject*)somememory[0], (PyTypeObject*)somememory[1]);
    res += PyType_IsSubtype((PyTypeObject*)somememory[1], (PyTypeObject*)somememory[0]);
    return PyLong_FromLong(res);
}

static struct PyMethodDef nc_methods[] = {
    {"customClassMethod", nc_classmethod, METH_VARARGS | METH_CLASS, ""},
    {NULL, NULL, 0, NULL}
};

static PyTypeObject NativeCustomType = {
    PyVarObject_HEAD_INIT(NULL, 0)
        "NativeCustomType.NativeCustomType",
    sizeof(NativeCustomObject),       /* tp_basicsize */
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
    "",
    0,                          /* tp_traverse */
    0,                          /* tp_clear */
    0,                          /* tp_richcompare */
    0,                          /* tp_weaklistoffset */
    0,                          /* tp_iter */
    0,                          /* tp_iternext */
    nc_methods,                 /* tp_methods */
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

static PyModuleDef c_classmethod_module = {
    PyModuleDef_HEAD_INIT,
    "c_classmethod_module",
    "",
    -1,
    NULL, NULL, NULL, NULL, NULL
};

PyMODINIT_FUNC
PyInit_c_classmethod(void)
{
    PyObject* m;

    if (PyType_Ready(&NativeCustomType) < 0)
        return NULL;

    m = PyModule_Create(&c_classmethod_module);
    if (m == NULL)
        return NULL;

    Py_INCREF(&NativeCustomType);
    PyModule_AddObject(m, "NativeCustomType", (PyObject *)&NativeCustomType);
    return m;
}

"""


ccompile("c_classmethod", code)
from c_classmethod import NativeCustomType

def count(num):
    total = 0
    classes = [str, object, type]
    for i in range(num):
        total += NativeCustomType.customClassMethod(classes[i % len(classes)])
    return total


def measure(num):
    result = count(num)
    print("result = " + str(result))


def __benchmark__(num=1000000):
    measure(num)
