# Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
import os.path
import struct

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

# TODO: GR-60735, we cannot support this without NFI struct by value support
def ignore_test_custom_libs():
    # 16B: returned in registers on System V AMD64 ABI
    class MySmallStruct1(ctypes.Structure):
        _fields_ = [("num", ctypes.c_int64), ("str", ctypes.c_char_p)]

    # 16B incl. 3B padding: not returned in registers on System V AMD64 ABI because of multiple fields
    class MySmallStruct2(ctypes.Structure):
        _fields_ = [("num", ctypes.c_int32), ("str", ctypes.c_char_p), ("end", ctypes.c_int8)]

    class MyLargeStruct(ctypes.Structure):
        _fields_ = [("str", ctypes.c_char_p),
                    ("num1", ctypes.c_int32),
                    ("num2", ctypes.c_int64),
                    ("num3", ctypes.c_double),
                    ("num4", ctypes.c_int16),
                    ("num5", ctypes.c_int8),
                    ("num6", ctypes.c_int32),
                    ("num7", ctypes.c_int32)]

    from distutils.ccompiler import new_compiler
    import tempfile

    cc = new_compiler()
    with tempfile.TemporaryDirectory() as tmp_dir:
        original_cwd = os.getcwd()
        try:
            os.chdir(tmp_dir)
            print(tmp_dir)
            with open('src.c', 'x') as f:
                f.write("""
                    #include <stdint.h>

                    typedef struct {
                        int32_t num;
                        const char *data;
                    } MySmallStruct1;

                    MySmallStruct1 get_small_struct1() {
                        MySmallStruct1 s = {42, "hello world"};
                        return s;
                    }

                    typedef struct {
                        int32_t num;
                        const char *data;
                        int8_t end;
                    } MySmallStruct2;

                    MySmallStruct2 get_small_struct2() {
                        MySmallStruct2 s = {42, "hello world", 42};
                        return s;
                    }

                    typedef struct {
                        const char *data;
                        int32_t num1;
                        int64_t num2;
                        double num3;
                        int16_t num4;
                        int8_t num5;
                        int32_t num6;
                        int32_t num7;
                    } MyLargeStruct;

                    MyLargeStruct get_large_struct() {
                        MyLargeStruct s = {"hello world", 42, 42, 42, 42, 42, 42, 42};
                        return s;
                    }
                    """)
            cc.compile(['src.c'])
            cc.link_shared_lib(['src.o'], 'myshlib')

            ctypes_lib = ctypes.CDLL(os.path.join(tmp_dir, 'libmyshlib.so'))

            ctypes_lib.get_small_struct1.argtypes = []
            ctypes_lib.get_small_struct1.restype = MySmallStruct1
            result = ctypes_lib.get_small_struct1()
            assert result.num == 42, result.num

            ctypes_lib.get_small_struct2.argtypes = []
            ctypes_lib.get_small_struct2.restype = MySmallStruct2
            result = ctypes_lib.get_small_struct2()
            assert result.num == 42, result.num
            assert result.end == 42, result.end

            ctypes_lib.get_large_struct.argtypes = []
            ctypes_lib.get_large_struct.restype = MyLargeStruct
            result = ctypes_lib.get_large_struct()
            assert result.num1 == 42
            assert result.num2 == 42
            assert result.num3 == 42
            assert result.num4 == 42
            assert result.num5 == 42
            assert result.num6 == 42
            assert result.num7 == 42
        finally:
            os.chdir(original_cwd)
