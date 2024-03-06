# Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
import sys
from array import array

from tests.cpyext import CPyExtTestCase, CPyExtFunction, unhandled_error_compare


def reference_array_resize(args):
    a, n = args
    if n < len(a):
        del a[n:]
    else:
        a.extend([0 for i in range(n - len(a))])
    return a


def reference_getbuffer(args):
    [a] = args
    return a, bytes(a), len(a) * a.itemsize, a.itemsize, 0, 1, a.typecode, len(a), a.itemsize


TEST_ARRAY = array('i', [1, 2, 3])


class TestPyArray(CPyExtTestCase):

    if sys.implementation.name == 'graalpy':
        test__PyArray_Resize = CPyExtFunction(
            reference_array_resize,
            lambda: (
                (array('i', [1, 2, 3]), 1),
                (array('i'), 3),
            ),
            code="""
                PyObject* wrap__PyArray_Resize(PyObject* array, Py_ssize_t new_size) {
                    if (_PyArray_Resize(array, new_size) < 0)
                        return NULL;
                    Py_INCREF(array);
                    return array;
                }
            """,
            callfunction="wrap__PyArray_Resize",
            resultspec="O",
            argspec='On',
            arguments=["PyObject* array", "Py_ssize_t new_size"],
            cmpfunc=unhandled_error_compare,
        )

        test__PyArray_Data = CPyExtFunction(
            lambda args: bytes(args[0]),
            lambda: (
                (TEST_ARRAY, len(TEST_ARRAY) * TEST_ARRAY.itemsize),
            ),
            code="""
            PyObject* wrap__PyArray_Data(PyObject* array, Py_ssize_t size) {
                char* data = _PyArray_Data(array);
                if (data == NULL)
                    return NULL;
                return PyBytes_FromStringAndSize(data, size);
            }
            """,
            callfunction="wrap__PyArray_Data",
            resultspec="O",
            argspec='On',
            arguments=["PyObject* array", "Py_ssize_t size"],
            cmpfunc=unhandled_error_compare,
        )

    test_array_getbuffer = CPyExtFunction(
        reference_getbuffer,
        lambda: (
            (array('h', [1, 2, 3]),),
        ),
        code="""
        PyObject* wrap_array_getbuffer(PyObject* array) {
            Py_buffer buf;
            if (PyObject_GetBuffer(array, &buf, PyBUF_FULL) != 0)
                return NULL;
            PyObject* bytes = PyBytes_FromStringAndSize(buf.buf, buf.len);
            if (!bytes)
                return NULL;
            PyObject* result = Py_BuildValue("OOnniisnn", buf.obj, bytes, buf.len, buf.itemsize, buf.readonly, buf.ndim,
                                             buf.format, buf.shape[0], buf.strides[0]);
            PyBuffer_Release(&buf);
            return result;
        }
        """,
        callfunction="wrap_array_getbuffer",
        resultspec="O",
        argspec="O",
        arguments=["PyObject* array"],
        cmpfunc=unhandled_error_compare,
    )
