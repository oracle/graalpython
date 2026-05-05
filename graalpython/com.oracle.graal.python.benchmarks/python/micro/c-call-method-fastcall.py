# Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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

static PyObject* NativeCustomType_method_fastcall(PyObject *module, PyObject *const *args, Py_ssize_t nargs) {
    Py_ssize_t i;
    for (i=0; i < nargs; i++) {
        if (!args[i]) {
            PyErr_SetString(PyExc_ValueError, "arg must not be NULL");
            return NULL;
        }
    }
    Py_RETURN_NONE;
}

static struct PyMethodDef NativeCustomType_methods[] = {
    {"method_fastcall", (PyCFunction)NativeCustomType_method_fastcall, METH_FASTCALL, NULL},
    {NULL, NULL, 0, NULL}
};

static PyTypeObject NativeCustomType = {
    PyVarObject_HEAD_INIT(NULL, 0)
    .tp_name = "NativeCustomType",
    .tp_basicsize = sizeof(PyObject),
    .tp_new = PyType_GenericNew,
    .tp_flags = Py_TPFLAGS_DEFAULT,
    .tp_methods = NativeCustomType_methods
};

static PyModuleDef c_method_module = {
    PyModuleDef_HEAD_INIT,
    "c_method_module",
    "",
    -1,
    NULL, NULL, NULL, NULL, NULL
};

PyMODINIT_FUNC
PyInit_c_method_module(void)
{
    PyObject* m;

    if (PyType_Ready(&NativeCustomType) < 0)
        return NULL;

    m = PyModule_Create(&c_method_module);
    if (m == NULL)
        return NULL;

    PyModule_AddObject(m, "NativeCustomType", (PyObject *)&NativeCustomType);
    return m;
}

"""

import sys
import os

# Add benchmark directory to path to allow import of harness.py
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from harness import ccompile

ccompile("c_method_module", code)
from c_method_module import NativeCustomType


def count(num):
    obj = NativeCustomType()
    for i in range(num):
        res = obj.method_fastcall(i, i+1, i+2)
        assert res is None
    return 0


def measure(num):
    return count(num)


def __benchmark__(num=1000000):
    return measure(num)


def run():
    __benchmark__(num=3000)


def warmupIterations():
    return 0


def iterations():
    return 10


def summary():
    return {
        "name": "OutlierRemovalAverageSummary",
        "lower-threshold": 0.0,
        "upper-threshold": 0.7,
    }


def dependencies():
    # Required to run `ccompile`
    return ["harness.py", "tests/__init__.py"]
