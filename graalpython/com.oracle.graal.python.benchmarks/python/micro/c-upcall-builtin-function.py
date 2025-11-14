# Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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

PyObject* simple_upcall(PyObject* mod, PyObject* args) {
    PyObject *func = PyTuple_GetItem(args, 0);
    PyObject *self = PyTuple_GetItem(args, 1);
    long upper = PyLong_AsLong(PyTuple_GetItem(args, 2));
    if (!PyCFunction_Check(func)) {
        PyErr_SetString(PyExc_TypeError, "expected a builtin method");
        return NULL;
    }
#ifdef GRAALVM_PYTHON
    PyMethodDef *ml = GraalPyCFunction_GetMethodDef(func);
#else
    PyCFunctionObject *cfunc = (PyCFunctionObject*)func;
    PyMethodDef *ml = cfunc->m_ml;
#endif
    PyCFunction ml_meth = ml->ml_meth;

    for (long i = 0; i < upper; i++) {
        PyObject *res = ml_meth(self, NULL);
        Py_XDECREF(res);
        if (!res) {
            return NULL;
        }
    }
    Py_RETURN_NONE;
}

static PyMethodDef MyModuleMethods[] = {
    {"simple_upcall", simple_upcall, METH_O, NULL},
    {NULL, NULL, 0, NULL}  // Sentinel
};

static PyModuleDef simple_upcall_module = {
    PyModuleDef_HEAD_INIT,
    "simple_upcall_module",
    NULL,
    -1,
    MyModuleMethods
};

PyMODINIT_FUNC
PyInit_simple_upcall_module(void)
{
    return PyModule_Create(&simple_upcall_module);
}
"""


ccompile("simple_upcall_module", code)
from simple_upcall_module import simple_upcall

def count(num):
    lst = []
    my_tuple = (lst.clear, lst, num)
    if simple_upcall(my_tuple) is not None:
        raise RuntimeError()
    return 0


def measure(num):
    result = count(num)
    return result


def __benchmark__(num=1000000):
    return measure(num)
