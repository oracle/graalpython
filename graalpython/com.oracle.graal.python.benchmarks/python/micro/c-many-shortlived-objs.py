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
#ifdef GRAALVM_PYTHON
#include <graalpy/handles.h>
#endif

static PyObject* NativeType_consume(PyObject* self, PyObject* arg) {
#ifdef GRAALVM_PYTHON
    // Tagged integers would bypass the native companion allocation we measure.
    if (points_to_py_int_handle(arg)) {
        PyErr_SetString(PyExc_AssertionError, "expected an integer requiring native companion allocation");
        return NULL;
    }
#endif
    if (!PyLong_Check(arg)) {
        PyErr_SetString(PyExc_TypeError, "expected long");
        return NULL;
    }
    Py_RETURN_NONE;
}

static struct PyMethodDef NativeType_methods[] = {
    {"consume", (PyCFunction)NativeType_consume, METH_O, NULL},
    {NULL, NULL, 0, NULL}
};

static PyTypeObject NativeTypeType = {
    PyVarObject_HEAD_INIT(NULL, 0)
    .tp_name = "NativeType.NativeType",
    .tp_flags = Py_TPFLAGS_DEFAULT,
    .tp_new = PyType_GenericNew,
    .tp_methods = NativeType_methods
};

static PyModuleDef ManyShortlivedObjsModule = {
    PyModuleDef_HEAD_INIT,
    "c_many_shortlived_objs_module",
    "",
    -1,
    NULL, NULL, NULL, NULL, NULL
};

PyMODINIT_FUNC
PyInit_c_many_shortlived_objs_module(void)
{
    PyObject* m;
    if (PyType_Ready(&NativeTypeType) < 0) {
        return NULL;
    }
    m = PyModule_Create(&ManyShortlivedObjsModule);
    if (m == NULL) {
        return NULL;
    }
    Py_INCREF(&NativeTypeType);
    PyModule_AddObject(m, "NativeType", (PyObject *)&NativeTypeType);
    return m;
}

"""


import sys
import os
# Add benchmark directory to path to allow import of harness.py
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from harness import ccompile

ccompile("c_many_shortlived_objs_module", code)
import c_many_shortlived_objs_module


def measure(num):
    idxObj = c_many_shortlived_objs_module.NativeType()
    # Integers keep initialization overhead low, have a cheap native type-flags
    # check, and require only one managed PInt object without additional storage.
    # Containers need separate storage or risk reusing cached empty instances;
    # strings require an additional TruffleString object.
    # Deliberately exceed the signed 32-bit range to avoid tagged native pointers
    # and cached integers, forcing a fresh object and native companion allocation
    # for each integer passed to consume().
    base = 1<<32
    for i in range(base, base + num):
        result = idxObj.consume(i)
        assert result is None


def __benchmark__(num=1000000):
    measure(num)


def run():
    __benchmark__(num=40000)


def warmupIterations():
    return 0


def iterations():
    return 50


def summary():
    return {
        "name": "OutlierRemovalAverageSummary",
        "lower-threshold": 0.0,
        "upper-threshold": 0.1,
    }


def dependencies():
    # Required to run `ccompile`
    return ["harness.py", "tests/__init__.py"]
