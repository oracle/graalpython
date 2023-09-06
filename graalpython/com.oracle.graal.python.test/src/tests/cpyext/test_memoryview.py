# Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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

import itertools
import sys
import struct

from . import CPyExtTestCase, CPyExtFunction, CPyExtType, unhandled_error_compare_with_message, unhandled_error_compare

__dir__ = __file__.rpartition("/")[0]

indices = (-2, 2, 10)
# indices = (-2, 0, 1, 2, 10)
slices = list(itertools.starmap(slice, itertools.product(indices, indices, indices)))


suboffsets_buf = '''\
    const Py_ssize_t ndim = 4;
    Py_ssize_t shape[] = {2, 2, 2, 2};
    Py_ssize_t strides[] = {sizeof(void*), sizeof(void*) * 2, sizeof(short) * 2, sizeof(short)};
    Py_ssize_t suboffsets[] = {-1, 0, -1, -1};
    short s1[2][2] = {{258, 345}, {190, 924}};
    short s2[2][2] = {{257, 344}, {189, 923}};
    short s3[2][2] = {{259, 346}, {191, 925}};
    short s4[2][2] = {{260, 347}, {192, 926}};
    void* ptr_buf[2][2] = {{s1, s2}, {s3, s4}};
    void fill_buffer(Py_buffer* buffer) {
        buffer->buf = ptr_buf;
        buffer->len = 2 * 2 * 2 * 2 * sizeof(short);
        buffer->itemsize = sizeof(short);
        buffer->readonly = 1;
        buffer->ndim = ndim;
        buffer->format = "h";
        buffer->shape = shape;
        buffer->strides = strides;
        buffer->suboffsets =suboffsets;
    }
    '''


def decode_ucs(mv, kind):
    b = bytes(mv)
    if kind == 1:
        return b.decode('latin-1')
    elif kind == 2:
        return b.decode('utf-16')
    else:
        assert kind == 4
        return b.decode('utf-32')


def compare_unicode_bytes_with_kind(x, y):
    if isinstance(x, BaseException) and isinstance(y, BaseException):
        return type(x) == type(y)
    elif isinstance(x, BaseException) or isinstance(y, BaseException):
        return False
    else:
        return decode_ucs(*x) == decode_ucs(*y)


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
        code=suboffsets_buf + '''
            static PyObject* test_read(PyObject *key, PyObject* expected) {
                Py_buffer buffer;
                fill_buffer(&buffer);
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

    test_memoryview_fromobject = CPyExtFunction(
        lambda args: args[2],
        lambda: (
            (b'123', 2, ord('3')),
            (bytearray(b'123'), 2, ord('3')),
            (memoryview(bytearray(b'123')), 2, ord('3')),
            ([1, 2, 3], 0, TypeError("memoryview: a bytes-like object is required, not 'list'")),
            # This tests that exceptions are properly converted on C boundary
            (None, 0, None),
        ),
        code='''
            static PyObject* test_fromobject(PyObject *object, PyObject *key, PyObject* expected) {
                PyObject *mv = PyMemoryView_FromObject(object);
                if (!mv) {
                    if (object == Py_None) {
                        PyErr_Clear();
                        Py_RETURN_NONE;
                    }
                    return NULL;
                }
                PyObject *item = PyObject_GetItem(mv, key);
                Py_DECREF(mv);
                return item;
            }
        ''',
        resultspec='O',
        argspec='OOO',
        arguments=["PyObject* object", "PyObject* key", "PyObject* expected"],
        callfunction="test_fromobject",
        cmpfunc=unhandled_error_compare_with_message,
    )

    test_memoryview_frommemory = CPyExtFunction(
        lambda args: args[1],
        lambda: (
            (1, 50),
            (2, 60),
            (3, IndexError("index out of bounds on dimension 1"))
        ),
        code='''
            char mem[] = {40, 50, 60};
            static PyObject* test_frommemory(PyObject *key, PyObject* expected) {
                PyObject *mv = PyMemoryView_FromMemory(mem, 3, PyBUF_READ);
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
        callfunction="test_frommemory",
        cmpfunc=unhandled_error_compare_with_message,
    )

    # Test creating memoryview on top of a pointer that is in fact a managed bytes object wrapper
    test_memoryview_frommemory_bytes_wrapper = CPyExtFunction(
        lambda args: memoryview(args[0])[args[1]],
        lambda: (
            (b"a", 0),
            (b"abcdefgh", 5),
            (b"abcdefgh", 8),
        ),
        code='''
            static PyObject* test_frommemory_bytes(PyObject* input, PyObject *key) {
                PyObject *mv = PyMemoryView_FromMemory(PyBytes_AS_STRING(input), PyBytes_GET_SIZE(input), PyBUF_READ);
                if (!mv)
                    return NULL;
                PyObject *item = PyObject_GetItem(mv, key);
                Py_DECREF(mv);
                return item;
            }
        ''',
        resultspec='O',
        argspec='OO',
        arguments=["PyObject* input", "PyObject* key"],
        callfunction="test_frommemory_bytes",
        cmpfunc=unhandled_error_compare,
    )

    # Test creating memoryview on top of a pointer that is in fact a managed unicode object wrapper
    ignore_test_memoryview_frommemory_unicode_wrapper = CPyExtFunction(
        lambda args: (memoryview(args[0].encode('utf-32')), 4),
        lambda: (
            ("asdf",),
            ("ニャー",),
            # ("😂",),  # TODO doesn't work because PyUnicode_GET_LENGTH returns 2
        ),
        code='''
            static PyObject* test_frommemory_unicode(PyObject* input) {
                PyObject *mv = PyMemoryView_FromMemory(PyUnicode_DATA(input), PyUnicode_GET_LENGTH(input) * PyUnicode_KIND(input), PyBUF_READ);
                if (!mv)
                    return NULL;
                return Py_BuildValue("Oi", mv, PyUnicode_KIND(input));
            }
        ''',
        resultspec='O',
        argspec='O',
        arguments=["PyObject* input"],
        callfunction="test_frommemory_unicode",
        cmpfunc=compare_unicode_bytes_with_kind,
    )

    test_memoryview_tolist = CPyExtFunction(
        lambda args: args[0],
        lambda: [
            ([
                [
                    [[258, 345], [190, 924]],
                    [[259, 346], [191, 925]],
                ],
                [
                    [[257, 344], [189, 923]],
                    [[260, 347], [192, 926]],
                ],
            ],),
        ],
        code=suboffsets_buf + '''
            static PyObject* test_read(PyObject* expected) {
                Py_buffer buffer;
                fill_buffer(&buffer);
                PyObject *mv = PyMemoryView_FromBuffer(&buffer);
                if (!mv)
                    return NULL;
                PyObject *list = PyObject_CallMethod(mv, "tolist", NULL);
                Py_DECREF(mv);
                return list;
            }
        ''',
        resultspec='O',
        argspec='O',
        arguments=["PyObject* expected"],
        callfunction="test_read",
        cmpfunc=unhandled_error_compare_with_message,
    )

    test_memoryview_tobytes = CPyExtFunction(
        lambda args: args[1],
        lambda: [
            (None, b'\x02\x01Y\x01\xbe\x00\x9c\x03\x03\x01Z\x01\xbf\x00\x9d\x03\x01\x01X\x01\xbd\x00\x9b\x03\x04\x01[\x01\xc0\x00\x9e\x03'),
            ('C', b'\x02\x01Y\x01\xbe\x00\x9c\x03\x03\x01Z\x01\xbf\x00\x9d\x03\x01\x01X\x01\xbd\x00\x9b\x03\x04\x01[\x01\xc0\x00\x9e\x03'),
            ('A', b'\x02\x01Y\x01\xbe\x00\x9c\x03\x03\x01Z\x01\xbf\x00\x9d\x03\x01\x01X\x01\xbd\x00\x9b\x03\x04\x01[\x01\xc0\x00\x9e\x03'),
            ('F', b'\x02\x01\x01\x01\x03\x01\x04\x01\xbe\x00\xbd\x00\xbf\x00\xc0\x00Y\x01X\x01Z\x01[\x01\x9c\x03\x9b\x03\x9d\x03\x9e\x03'),
            ('X', ValueError("order must be 'C', 'F' or 'A'")),
        ],
        code=suboffsets_buf + '''
            static PyObject* test_read(PyObject* order, PyObject* expected) {
                Py_buffer buffer;
                fill_buffer(&buffer);
                PyObject *mv = PyMemoryView_FromBuffer(&buffer);
                if (!mv)
                    return NULL;
                PyObject *bytes = PyObject_CallMethod(mv, "tobytes", "O", order);
                Py_DECREF(mv);
                return bytes;
            }
        ''',
        resultspec='O',
        argspec='OO',
        arguments=["PyObject* order", "PyObject* expected"],
        callfunction="test_read",
        cmpfunc=unhandled_error_compare_with_message,
    )

    test_memoryview_getcontiguous = CPyExtFunction(
        lambda args: args[1],
        lambda: [
            (bytearray(b'12345678'), b'12345678'),
            (memoryview(b'12345678'), b'12345678'),
            (memoryview(b'12345678')[::2], b'1357'),
            (memoryview(b'12345678abcd').cast('i')[::2], b'1234abcd'),
        ],
        code='''
            static PyObject* test_getcontiguous(PyObject* obj, PyObject* expected) {
                PyObject *mv = PyMemoryView_GetContiguous(obj, PyBUF_READ, 'C');
                if (!mv)
                    return NULL;
                Py_buffer *view = PyMemoryView_GET_BUFFER(mv);
                PyObject *bytes = PyBytes_FromStringAndSize(view->buf, view->len);
                Py_DECREF(mv);
                return bytes;
            }
        ''',
        resultspec='O',
        argspec='OO',
        arguments=["PyObject* order", "PyObject* expected"],
        callfunction="test_getcontiguous",
        cmpfunc=unhandled_error_compare_with_message,
    )

    ignored_test_memoryview_slice = CPyExtFunction(
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

    test_memoryview_write = CPyExtFunction(
        lambda args: args[3],
        lambda: [l + (v,) for l in [
            (bytearray(b'12345678'), 5, ord('0'), b'12345078'),
            (bytearray(b'12345678'), 10, ord('0'), IndexError("index out of bounds on dimension 1")),
            (bytearray(b'12345678'), "a", ord('0'), TypeError("memoryview: invalid slice key")),
            (bytearray(b'12345678'), slice(2, 5), b'abc', b'12abc678'),
            (bytearray(b'12345678'), slice(2, 5), memoryview(b'abcdef')[::2], b'12ace678'),
            (bytearray(b'12345678'), slice(None, None, 2), b'abcd', b'a2b4c6d8'),
            (bytearray(b'12345678'), slice(2, 5), b'1', ValueError("memoryview assignment: lvalue and rvalue have different structures")),
            (bytearray(b'12345678'), slice(2, 5), b'1111', ValueError("memoryview assignment: lvalue and rvalue have different structures")),
        ] for v in [True, False]],
        # TODO test slice copy from native source
        code='''
            static PyObject* test_write(PyObject *dest, PyObject *key, PyObject *value, PyObject *expected, int destNative) {
                PyObject *ret = NULL;
                PyObject *mv;
                Py_buffer buffer;
                if (destNative) {
                    if (PyObject_GetBuffer(dest, &buffer, PyBUF_FULL_RO) != 0)
                        return NULL;
                    mv = PyMemoryView_FromBuffer(&buffer);
                } else {
                    mv = PyMemoryView_FromObject(dest);
                }
                if (!mv)
                    goto error_buf;
                if (PyObject_SetItem(mv, key, value) != 0)
                    goto error_mv;
                ret = dest;
            error_mv:
                Py_DECREF(mv);
            error_buf:
                if (destNative)
                    PyBuffer_Release(&buffer);
                return ret;
            }
        ''',
        resultspec='O',
        argspec='OOOOp',
        arguments=["PyObject* dest", "PyObject* key", "PyObject* value", "PyObject* expected", "int destNative"],
        callfunction="test_write",
        cmpfunc=unhandled_error_compare_with_message,
    )


class TestObject(object):
    def test_memoryview_fromobject_multidim(self):
        TestType = CPyExtType(
            "TestMemoryViewMultidim",
            """
            PyObject* get_converted_view(PyObject* self, PyObject* obj) {
                PyObject *mv = PyMemoryView_FromObject(obj);
                if (!mv) {
                    return NULL;
                }

                Py_buffer *view = PyMemoryView_GET_BUFFER(mv);
                // int i = (int)PyNumber_AsSsize_t(idx, NULL);
                return PyMemoryView_FromBuffer(view);
            }
            """,
            tp_methods='{"get_converted_view", get_converted_view, METH_O, ""}',
        )

        obj = TestType()
        t1_buf = struct.pack("d"*12, *[1.5*x for x in range(12)])
        t1_mv = memoryview(t1_buf).cast('d', shape=[3,4])
        assert t1_mv == obj.get_converted_view(t1_mv)

    def test_memoryview_acquire_release(self):
        TestType = CPyExtType(
            "TestMemoryViewBuffer1",
            """
            int bufcount = 0;
            char buf[] = {123, 124, 125, 126};
            int getbuffer(TestMemoryViewBuffer1Object *self, Py_buffer *view, int flags) {
                bufcount++;
                return PyBuffer_FillInfo(view, (PyObject*)self, buf, 4, 1, flags);
            }
            void releasebuffer(TestMemoryViewBuffer1Object *self, Py_buffer *view) {
                bufcount--;
            }
            static PyBufferProcs as_buffer = {
                (getbufferproc)getbuffer,
                (releasebufferproc)releasebuffer,
            };
            PyObject* get_bufcount(PyObject* self, PyObject* args) {
                return PyLong_FromLong(bufcount);
            }
            """,
            tp_as_buffer='&as_buffer',
            tp_methods='{"get_bufcount", get_bufcount, METH_NOARGS, ""}',
        )
        obj = TestType()
        assert obj.get_bufcount() == 0
        mv1 = memoryview(obj)
        assert mv1[3] == 126
        assert obj.get_bufcount() == 1
        mv2 = memoryview(obj)
        mv3 = mv2[1:]
        assert obj.get_bufcount() == 2
        assert mv2[3] == 126
        mv2.release()
        assert mv3[2] == 126
        mv1.release()
        mv3.release()
        assert obj.get_bufcount() == 0
