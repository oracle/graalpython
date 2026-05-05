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

# The code below calls an API function in a loop to measure performance of C
# extensions calling into the Python C API. The _PyObject_IsFreed function is
# such an API function, and it does not do much, for 0 it's just a c-to-c call.
code = """
#include "Python.h"
#include <stddef.h>

static PyObject* iterate_dict(PyObject *module, PyObject *const *args, Py_ssize_t nargs) {
    if (!_PyArg_CheckPositional("iterate_dict", nargs, 2, 2)) {
        return NULL;
    }
    long n = PyLong_AsLong(args[0]);
    PyObject *arg = args[1];
    
    /* variables for dict iteration */
    PyObject *key;
    PyObject *value;
    Py_ssize_t pos;
    Py_hash_t hash;
    
    if (!PyDict_Check(arg)) {
        PyErr_SetString(PyExc_TypeError, "arg must be a dict");
        return NULL;
    }
    
    long long cnt = 0;
    for (long i = 0; i < n; i++) {
        cnt = 0;
        pos = 0;
        while (_PyDict_Next(arg, &pos, &key, &value, &hash)) {
            if (key == NULL) {
                PyErr_SetString(PyExc_ValueError, "key must not be NULL");
                return NULL;
            }
            if (value == NULL) {
                PyErr_SetString(PyExc_ValueError, "value must not be NULL");
                return NULL;
            }
            cnt++;
        }
    }
    return PyLong_FromLongLong(cnt);
}

static PyMethodDef MyModuleMethods[] = {
    {"iterate_dict", _PyCFunction_CAST(iterate_dict), METH_FASTCALL, NULL},
    {NULL, NULL, 0, NULL}  // Sentinel
};

static PyModuleDef iterate_dict_module = {
    PyModuleDef_HEAD_INIT,
    "iterate_dict_module",
    NULL,
    -1,
    MyModuleMethods
};

PyMODINIT_FUNC
PyInit_iterate_dict_module(void)
{
    return PyModule_Create(&iterate_dict_module);
}
"""

ccompile("iterate_dict_module", code)
from iterate_dict_module import iterate_dict


def count(num):
    d = {"a": 1, "b": 2, "c": 3}
    result = iterate_dict(num, d)
    if result != len(d):
        raise RuntimeError("was: " + str(result))
    return 0


def measure(num):
    result = count(num)
    return result


def __benchmark__(num=1000000):
    return measure(num)
