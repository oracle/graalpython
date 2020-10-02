# Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
import itertools
from . import CPyExtTestCase, CPyExtFunction, unhandled_error_compare_with_message, unhandled_error_compare
__dir__ = __file__.rpartition("/")[0]

indices = (-2, 0, 1, 2, 10)
slices = list(itertools.starmap(slice, itertools.product(indices, indices, indices)))


class TestPyMemoryView(CPyExtTestCase):
    def compile_module(self, name):
        type(self).mro()[1].__dict__["test_%s" % name].create_module(name)
        super(TestPyMemoryView, self).compile_module(name)

    test_memoryview_read = CPyExtFunction(
        lambda args: args[1],
        lambda: (
            ((0, 0, 0, 0), 258),
            ((0, 1, 1, 0), 191),
            ((1, 0, 0, 1), 344),
            ((1, 1, 1, 1), 926),
            ((1, 1, 3, 1), IndexError("index out of bounds on dimension 3")),
            ((1, 1, 1, "a"), TypeError("memoryview: invalid slice key")),
            ((1, 1, 1, sys.maxsize + 1), IndexError("cannot fit 'int' into an index-sized integer")),
            (5, NotImplementedError("multi-dimensional sub-views are not implemented")),
            ((1, 2), NotImplementedError("sub-views are not implemented")),
            ("a", TypeError("memoryview: invalid slice key")),
        ),
        code='''
            const Py_ssize_t ndim = 4;
            Py_ssize_t shape[] = {2, 2, 2, 2};
            Py_ssize_t strides[] = {sizeof(void*), sizeof(void*) * 2, sizeof(short) * 2, sizeof(short)};
            Py_ssize_t suboffsets[] = {-1, 0, -1, -1};
            short s1[2][2] = {{258, 345}, {190, 924}};
            short s2[2][2] = {{257, 344}, {189, 923}};
            short s3[2][2] = {{259, 346}, {191, 925}};
            short s4[2][2] = {{260, 347}, {192, 926}};
            void* ptr_buf[2][2] = {{s1, s2}, {s3, s4}};
            Py_buffer buffer = {
                    ptr_buf,
                    NULL,
                    2 * 2 * 2 * 2 * sizeof(short),
                    sizeof(short),
                    1,
                    ndim,
                    "h",
                    shape,
                    strides,
                    suboffsets,
            };
    
            static PyObject* test_read(PyObject *key, PyObject* expected) {
                PyObject *mv = PyMemoryView_FromBuffer(&buffer);
                if (!mv)
                    return NULL;
                PyObject *item = PyObject_GetItem(mv, key);
                Py_DECREF(mv);
                return item;
            }
        ''',
        resultspec='O',
        argspec='OO',
        arguments=["PyObject* key", "PyObject* expected"],
        callfunction="test_read",
        cmpfunc=unhandled_error_compare_with_message,
    )

    test_memoryview_slice = CPyExtFunction(
        lambda args: bytes((5, 6, 255, 128, 99))[args[0]][args[1]][args[2]],
        lambda: list(itertools.product(slices, slices, range(-2, 5))),
        code='''
            uint8_t bytes[] = {5, 6, 255, 128, 99};
            Py_buffer buffer = {
                    bytes,
                    NULL,
                    5 * sizeof(uint8_t),
                    sizeof(uint8_t),
                    1,
                    1,
                    "B",
            };
            static PyObject* test_slice(PyObject *slice1, PyObject* slice2, PyObject* index) {
                PyObject *mv = PyMemoryView_FromBuffer(&buffer);
                if (!mv)
                    return NULL;
                PyObject *sub1 = PyObject_GetItem(mv, slice1);
                Py_DECREF(mv);
                if (!sub1) {
                    return NULL;
                }
                PyObject *sub2 = PyObject_GetItem(sub1, slice2);
                Py_DECREF(sub1);
                if (!sub2) {
                    return NULL;
                }
                PyObject *item = PyObject_GetItem(sub2, index);
                Py_DECREF(sub2);
                return item;
            }
        ''',
        resultspec='O',
        argspec='OOO',
        arguments=['PyObject* slice1', 'PyObject* slice2', 'PyObject* index'],
        callfunction="test_slice",
        cmpfunc=unhandled_error_compare,
    )

    test_memoryview_read_0dim = CPyExtFunction(
        lambda args: args[1],
        lambda: (
            ((), 123456),
            (1, TypeError("invalid indexing of 0-dim memory")),
            ((1,), TypeError("invalid indexing of 0-dim memory")),
        ),
        code='''
            long number = 123456;
            Py_buffer buffer = {
                    &number,
                    NULL,
                    sizeof(long),
                    sizeof(long),
                    1,
                    0,
                    "l",
            };
    
            static PyObject* test_read(PyObject *key, PyObject* expected) {
                PyObject *mv = PyMemoryView_FromBuffer(&buffer);
                if (!mv)
                    return NULL;
                PyObject *item = PyObject_GetItem(mv, key);
                Py_DECREF(mv);
                return item;
            }
        ''',
        resultspec='O',
        argspec='OO',
        arguments=["PyObject* key", "PyObject* expected"],
        callfunction="test_read",
        cmpfunc=unhandled_error_compare_with_message,
    )
