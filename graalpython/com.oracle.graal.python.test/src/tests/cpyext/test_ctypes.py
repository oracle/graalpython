# Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
import ctypes
import struct
import sys

from tests.cpyext import CPyExtTestCase, CPyExtType

BufferTester = CPyExtType(
    'BufferTester',
    cmembers='Py_buffer buffer;',
    code='''
    static PyObject* buffer_tester_new(PyTypeObject* type, PyObject* args, PyObject* kwargs) {
        PyObject* obj;
        if (PyArg_ParseTuple(args, "O", &obj) < 0)
            return NULL;
        PyObject* self = type->tp_alloc(type, 0);
        if (!self)
            return NULL;
        if (PyObject_GetBuffer(obj, &((BufferTesterObject*)self)->buffer, PyBUF_CONTIG_RO) < 0) {
            Py_DECREF(self);
            return NULL;
        }
        return self;
    }
    
    static PyObject* buffer_tester_enter(PyObject* self) {
        return Py_NewRef(self);
    }
    
    static PyObject* buffer_tester_exit(BufferTesterObject* self, PyObject* args) {
        PyBuffer_Release(&self->buffer);
        Py_RETURN_NONE;
    }
    
    static PyObject* buffer_tester_obj(BufferTesterObject* self) {
        return Py_NewRef(self->buffer.obj);
    }
    
    static PyObject* buffer_tester_bytes(BufferTesterObject* self) {
        return PyBytes_FromStringAndSize(self->buffer.buf, self->buffer.len);
    }
    
    static PyObject* buffer_tester_itemsize(BufferTesterObject* self) {
        return PyLong_FromSsize_t(self->buffer.itemsize);
    }
    
    static PyObject* buffer_tester_format(BufferTesterObject* self) {
        return PyBytes_FromString(self->buffer.format);
    }
    
    static PyObject* buffer_tester_shape(BufferTesterObject* self) {
        PyObject* tuple = PyTuple_New(self->buffer.ndim);
        if (!tuple)
            return NULL;
        for (int i = 0; i < self->buffer.ndim; i++) {
            PyObject* value = PyLong_FromSsize_t(self->buffer.shape[i]);
            if (!value)
                return NULL;
            PyTuple_SET_ITEM(tuple, i, value);
        }
        return tuple;
    }
    ''',
    tp_new='buffer_tester_new',
    tp_methods='''
    {"__enter__", (PyCFunction)buffer_tester_enter, METH_NOARGS, ""},
    {"__exit__", (PyCFunction)buffer_tester_exit, METH_VARARGS, ""}
    ''',
    tp_getset='''
    {"obj", (getter)buffer_tester_obj, NULL, NULL, NULL},
    {"bytes", (getter)buffer_tester_bytes, NULL, NULL, NULL},
    {"itemsize", (getter)buffer_tester_itemsize, NULL, NULL, NULL},
    {"format", (getter)buffer_tester_format, NULL, NULL, NULL},
    {"shape", (getter)buffer_tester_shape, NULL, NULL, NULL}
    ''',
)


class TestCDataBuffer(CPyExtTestCase):
    def test_buffer(self):
        int_format = struct.Struct(">i")
        inner_type = ctypes.c_int.__ctype_be__ * 2
        outer_type = inner_type * 2
        array = outer_type(inner_type(1, 2), inner_type(3, 4))
        with BufferTester(array) as buffer:
            assert buffer.obj is array
            assert buffer.bytes == b''.join(int_format.pack(n) for n in [1, 2, 3, 4])
            assert buffer.itemsize == int_format.size
            assert buffer.format.startswith(b'>')
            assert struct.Struct(buffer.format).size == int_format.size
            assert buffer.shape == (2, 2)
