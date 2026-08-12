# Copyright (c) 2023, 2026, Oracle and/or its affiliates. All rights reserved.
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


def reference_array_new(args):
    array_type, size, template = args
    result = array_type(template.typecode, [0] * size)
    return type(result) is array_type, result


def reference_array_descriptor(args):
    array_obj, other = args
    return array_obj.typecode, True, array_obj.typecode == other.typecode


TEST_ARRAY = array('i', [1, 2, 3])


class ArraySubclass(array):
    pass


class TestPyArray(CPyExtTestCase):

    if sys.implementation.name == 'graalpy':
        test_GraalPyArray_Resize = CPyExtFunction(
            reference_array_resize,
            lambda: (
                (array('i', [1, 2, 3]), 1),
                (array('i'), 3),
            ),
            code="""
                PyObject* wrap_GraalPyArray_Resize(PyObject* array, Py_ssize_t new_size) {
                    if (GraalPyArray_Resize(array, new_size) < 0)
                        return NULL;
                    Py_INCREF(array);
                    return array;
                }
            """,
            callfunction="wrap_GraalPyArray_Resize",
            resultspec="O",
            argspec='On',
            arguments=["PyObject* array", "Py_ssize_t new_size"],
            cmpfunc=unhandled_error_compare,
        )

        test_GraalPyArray_Data = CPyExtFunction(
            lambda args: bytes(args[0]),
            lambda: (
                (TEST_ARRAY, len(TEST_ARRAY) * TEST_ARRAY.itemsize),
            ),
            code="""
            PyObject* wrap_GraalPyArray_Data(PyObject* array, Py_ssize_t size) {
                char* data = GraalPyArray_Data(array);
                if (data == NULL)
                    return NULL;
                return PyBytes_FromStringAndSize(data, size);
            }
            """,
            callfunction="wrap_GraalPyArray_Data",
            resultspec="O",
            argspec='On',
            arguments=["PyObject* array", "Py_ssize_t size"],
            cmpfunc=unhandled_error_compare,
        )

        test_GraalPyArray_New = CPyExtFunction(
            reference_array_new,
            lambda: (
                (array, 0, array('b')),
                (array, 3, array('i')),
                (ArraySubclass, 2, array('d')),
            ),
            code="""
            PyObject* wrap_GraalPyArray_New(PyObject* type, Py_ssize_t size, PyObject* template) {
                GraalPyArray_Descriptor* descriptor = GraalPyArray_GetDescriptor(template);
                if (descriptor == NULL)
                    return NULL;
                PyObject* result = GraalPyArray_New(type, size, descriptor);
                if (result == NULL)
                    return NULL;
                PyObject* is_requested_type = PyBool_FromLong(Py_TYPE(result) == (PyTypeObject*)type);
                if (is_requested_type == NULL) {
                    Py_DECREF(result);
                    return NULL;
                }
                return Py_BuildValue("NN", is_requested_type, result);
            }
            """,
            callfunction="wrap_GraalPyArray_New",
            resultspec="O",
            argspec="OnO",
            arguments=["PyObject* type", "Py_ssize_t size", "PyObject* template"],
            cmpfunc=unhandled_error_compare,
        )

        test_GraalPyArray_Descriptor = CPyExtFunction(
            reference_array_descriptor,
            lambda: (
                (array('b'), array('b')),
                (array('B'), array('B')),
                (array('u'), array('u')),
                (array('w'), array('w')),
                (array('h'), array('h')),
                (array('H'), array('H')),
                (array('i'), array('I')),
                (array('I'), array('I')),
                (array('l'), array('l')),
                (array('L'), array('L')),
                (array('q'), array('q')),
                (array('Q'), array('Q')),
                (array('f'), array('f')),
                (array('d'), array('d')),
            ),
            code="""
            PyObject* wrap_GraalPyArray_Descriptor(PyObject* array, PyObject* other) {
                GraalPyArray_Descriptor* descriptor = GraalPyArray_GetDescriptor(array);
                GraalPyArray_Descriptor* other_descriptor = GraalPyArray_GetDescriptor(other);
                PyObject* typecode;
                PyObject* itemsize;
                long itemsize_value;
                if (descriptor == NULL || other_descriptor == NULL)
                    return NULL;
                typecode = PyUnicode_FromOrdinal((unsigned char)descriptor->typecode);
                if (typecode == NULL)
                    return NULL;
                itemsize = PyObject_GetAttrString(array, "itemsize");
                if (itemsize == NULL) {
                    Py_DECREF(typecode);
                    return NULL;
                }
                itemsize_value = PyLong_AsLong(itemsize);
                Py_DECREF(itemsize);
                if (itemsize_value == -1 && PyErr_Occurred()) {
                    Py_DECREF(typecode);
                    return NULL;
                }
                return Py_BuildValue("Nii", typecode, descriptor->itemsize == itemsize_value,
                                     descriptor == other_descriptor);
            }
            """,
            callfunction="wrap_GraalPyArray_Descriptor",
            resultspec="O",
            argspec="OO",
            arguments=["PyObject* array", "PyObject* other"],
            cmpfunc=unhandled_error_compare,
        )

    test_array_Py_SIZE = CPyExtFunction(
        lambda args: len(args[0]),
        lambda: (
            (array('b'),),
            (array('i', [1, 2, 3]),),
            (ArraySubclass('d', [1, 2]),),
        ),
        code="""
        Py_ssize_t wrap_array_Py_SIZE(PyObject* array) {
            return Py_SIZE(array);
        }
        """,
        callfunction="wrap_array_Py_SIZE",
        resultspec="n",
        argspec="O",
        arguments=["PyObject* array"],
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
