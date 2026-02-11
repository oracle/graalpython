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

PyObject* call_method_obj_args(PyObject *module, PyObject *const *args, Py_ssize_t nargs) {
    if (!_PyArg_CheckPositional("call_method_obj_args", nargs, 3, 3)) {
        return NULL;
    }
    long n = PyLong_AsLong(args[0]);
    PyObject *receiver = args[1];
    PyObject *arg = args[2];
    PyObject *result;
    for (long i = 0; i < n; i++) {
        result = PyObject_CallFunctionObjArgs(receiver, arg, NULL);
        if (result == NULL) {
            return NULL;
        }
        Py_DECREF(result);
    }
    Py_RETURN_NONE;
}

static PyMethodDef MyModuleMethods[] = {
    {"call_method_obj_args", _PyCFunction_CAST(call_method_obj_args), METH_FASTCALL, NULL},
    {NULL, NULL, 0, NULL}  // Sentinel
};

static PyModuleDef native_type_module = {
    PyModuleDef_HEAD_INIT,
    "native_type_module",
    NULL,
    -1,
    MyModuleMethods
};

typedef struct {
    PyObject_HEAD
    vectorcallfunc vectorcall;
} NativeObject;

static PyObject *
NativeType_vectorcall(PyObject *callable, PyObject *const *args,
                            size_t nargsf, PyObject *kwnames)
{
    Py_RETURN_NONE;
}

static PyObject *
NativeType_new(PyTypeObject* type, PyObject* args, PyObject *kw)
{
    NativeObject *op = (NativeObject *)type->tp_alloc(type, 0);
    op->vectorcall = NativeType_vectorcall;
    return (PyObject *)op;
}

static PyTypeObject NativeType = {
    PyVarObject_HEAD_INIT(NULL, 0)
    "NativeType",
    sizeof(NativeObject),
    .tp_new = NativeType_new,
    .tp_call = PyVectorcall_Call,
    .tp_vectorcall_offset = offsetof(NativeObject, vectorcall),
    .tp_flags = Py_TPFLAGS_DEFAULT | Py_TPFLAGS_BASETYPE | Py_TPFLAGS_HAVE_VECTORCALL,
};

PyMODINIT_FUNC
PyInit_native_type_module(void)
{
    PyType_Ready(&NativeType);
    PyObject* m = PyModule_Create(&native_type_module);
    if (!m) {
        return NULL;
    }
    PyModule_AddObject(m, "NativeType", (PyObject *)&NativeType);
    return m;
}
"""


ccompile("native_type_module", code)
from native_type_module import NativeType, call_method_obj_args

def count(num):
    callable = NativeType()
    if call_method_obj_args(num, callable, None) is not None:
        raise RuntimeError()
    return 0


def measure(num):
    result = count(num)
    return result


def __benchmark__(num=1000000):
    return measure(num)
