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

# The code below calls an API function in a loop to measure performance of C
# extensions calling into the Python C API. The _PyObject_IsFreed function is
# such an API function, and it does not do much, for 0 it's just a c-to-c call.
code = """
#include "Python.h"

PyObject* simple_to_c(PyObject* mod, PyObject* arg) {
    long upper = PyLong_AsLong(arg);
    for (long i = 0; i < upper; i++) {
        _PyObject_IsFreed((PyObject *)0L);
    }
    Py_RETURN_NONE;
}

static PyMethodDef MyModuleMethods[] = {
    {"simple_to_c", simple_to_c, METH_O, NULL},
    {NULL, NULL, 0, NULL}  // Sentinel
};

static PyModuleDef simple_to_c_module = {
    PyModuleDef_HEAD_INIT,
    "simple_to_c_module",
    NULL,
    -1,
    MyModuleMethods
};

PyMODINIT_FUNC
PyInit_simple_to_c_module(void)
{
    return PyModule_Create(&simple_to_c_module);
}
"""


ccompile("simple_to_c_module", code)
from simple_to_c_module import simple_to_c

def count(num):
    if simple_to_c(num) is not None:
        raise RuntimeError()
    return 0


def measure(num):
    result = count(num)
    return result


def __benchmark__(num=1000000):
    return measure(num)
