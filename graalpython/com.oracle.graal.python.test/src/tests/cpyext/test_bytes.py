# Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
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
import unittest

from . import CPyExtTestCase, CPyExtFunction, CPyExtFunctionOutVars, unhandled_error_compare, CPyExtType, \
    is_native_object

__dir__ = __file__.rpartition("/")[0]


def _reference_from_string_n(args):
    arg_str = args[0]
    arg_n = args[1]
    return bytes(arg_str[0:arg_n], "utf-8")

def _reference_from_object(args):
    obj = args[0]
    if type(obj) == bytes:
        return obj
    if isinstance(obj, (list, tuple, memoryview)) or (not isinstance(obj, str) and hasattr(obj, "__iter__")):
        return bytes(obj)
    raise TypeError("cannot convert '%s' object to bytes" % type(obj).__name__)

def _as_string(args):
    if not isinstance(args[0], bytes):
        return TypeError()
    return args[0].decode()

def _as_string_ba(args):
    return args[0].decode()

def _as_string_and_size(args):
    if not isinstance(args[0], bytes):
        return TypeError()
    arg_bytes = args[0]
    s = arg_bytes.decode("utf-8")
    return s, len(s)


def _reference_format(args):
    fmt = args[0]
    fmt_args = tuple(args[1:])
    return (fmt % fmt_args).encode()

class CIter:
    def __iter__(self):
        return iter([1, 2, 3])


BytesSubclass = CPyExtType(
    "BytesSubclass",
    '',
    struct_base='PyBytesObject bytes;',
    tp_itemsize='sizeof(char)',
    tp_base='&PyBytes_Type',
    tp_new='0',
    tp_alloc='0',
    tp_free='0',
)

ByteArraySubclass = CPyExtType(
    "ByteArraySubclass",
    '',
    struct_base='PyByteArrayObject bytes;',
    tp_itemsize='sizeof(char)',
    tp_base='&PyByteArray_Type',
    tp_new='0',
    tp_alloc='0',
    tp_free='0',
)


class TestPyBytes(CPyExtTestCase):

    # Below are the PyBytes_* identifiers that we know are used in numpy

    # PyBytes_FromStringAndSize
    test_PyBytes_FromStringAndSize = CPyExtFunction(
        _reference_from_string_n,
        lambda: (("hello world", 11), ("hello world", 5)),
        resultspec="O",
        argspec='sn',
        arguments=("char* str", "Py_ssize_t sz"),
    )

    # PyBytes_FromStringAndSize
    test_PyBytes_FromStringAndSizeNULL = CPyExtFunction(
        lambda args: len(b"\x00" * args[0]),
        lambda: ((128,),),
        code="""Py_ssize_t PyBytes_FromStringAndSizeNULL(Py_ssize_t n) {
            // we are return the length because the content is random (uninitialized)
            return PyBytes_Size(PyBytes_FromStringAndSize(NULL, n));
        }
        """,
        resultspec="n",
        argspec='n',
        arguments=["Py_ssize_t n"],
    )

    # PyBytes_FromString
    test_PyBytes_FromString = CPyExtFunction(
        lambda arg: bytes(arg[0], "utf-8"),
        lambda: (("hello world a second time",),),
        resultspec="O",
        argspec="s",
        arguments=["char* str"],
    )

    # PyBytes_AsString
    test_PyBytes_AsString = CPyExtFunction(
        _as_string,
        lambda: (
            (b"hello",),
            (b"world",),
            (BytesSubclass(b"hello"),),
            (list(),),
            ("hello",)
        ),
        resultspec="s",
        argspec="O",
        arguments=["PyObject* arg"],
        cmpfunc=unhandled_error_compare
    )

    # PyByteArray_AsString
    test_PyByteArray_AsString = CPyExtFunction(
        _as_string_ba,
        lambda: (
            (bytearray(b"hello"),),
            (bytearray(b"world"),),
            (ByteArraySubclass(b"hello"),),
        ),
        resultspec="s",
        argspec="O",
        arguments=["PyObject* arg"],
        cmpfunc=unhandled_error_compare
    )

    # PyBytes_AsStringAndSize
    test_PyBytes_AsStringAndSize = CPyExtFunction(
        _as_string_and_size,
        lambda: (
            (b"hello",),
            (b"world",),
            (BytesSubclass(b"hello"),),
            (list(),),
            ("hello",)
        ),
        code="""
        static PyObject* wrap_PyBytes_AsStringAndSize(PyObject* arg) {
            char* s;
            Py_ssize_t sz;
            if (PyBytes_AsStringAndSize(arg, &s, &sz) < 0)
                return NULL;
            return Py_BuildValue("sn", s, sz);
        }
        """,
        callfunction='wrap_PyBytes_AsStringAndSize',
        argspec="O",
        arguments=["PyObject* arg"],
        cmpfunc=unhandled_error_compare
    )

    test_native_storage = CPyExtFunction(
        lambda args: args[0].encode('utf-8')[-1],
        lambda: (("hello",), ("world",)),
        argspec="O",
        arguments=["PyObject* arg"],
        resultspec="i",
        # The code is creating the bytes objects in such a roundabout way in order to make sure the native storage will
        # get collected after the test
        code="""
        int wrap_test_native_storage(PyObject* str) {
            PyObject* bytes = PyUnicode_AsUTF8String(str);
            if (bytes == NULL)
                return -1;
            char* s;
            Py_ssize_t sz;
            if (PyBytes_AsStringAndSize(bytes, &s, &sz) < 0)
                return -1;
            int ret = s[sz - 1];
            Py_DECREF(bytes);
            return ret;
        }
        """,
        callfunction='wrap_test_native_storage',
    )

    # PyBytes_Size
    test_PyBytes_Size = CPyExtFunction(
        lambda b: len(b[0]),
        lambda: (
            (b"hello",),
            (b"hello world",),
            (b"",),
            (BytesSubclass(b"hello"),),
        ),
        resultspec="n",
        argspec="O",
        arguments=["PyObject* arg"],
    )

    # PyByteArray_Size
    test_PyByteArray_Size = CPyExtFunction(
        lambda b: len(b[0]),
        lambda: (
            (bytearray(b"hello"),),
            (bytearray(b"hello world"),),
            (bytearray(b""),),
            (ByteArraySubclass(b"hello"),),
        ),
        resultspec="n",
        argspec="O",
        arguments=["PyObject* arg"],
    )

    # PyBytes_GET_SIZE
    test_PyBytes_GET_SIZE = CPyExtFunction(
        lambda b: len(b[0]),
        lambda: (
            (b"hello",),
            (b"hello world",),
            (b"",),
            (BytesSubclass(b"hello"),),
        ),
        resultspec="n",
        argspec="O",
        arguments=["PyObject* arg"],
        cmpfunc=unhandled_error_compare
    )

    # PyBytes_FromFormat
    test_PyBytes_FromFormat = CPyExtFunction(
        _reference_format,
        lambda: (("hello %s %s, %d times", "beautiful", "world", 3),),
        resultspec="O",
        argspec="sssi",
        arguments=["char* fmt", "char* arg0", "char* arg1", "int arg2"],
    )
    
    # PyBytes_FromObject
    test_PyBytes_FromObject = CPyExtFunction(
        _reference_from_object,
        lambda: (("hello", ),
                 (bytes(1), ),
                 (1, ),
                 ([1, 2, 4], ),
                 ((1, 2, 3), ),
                 (memoryview(b'abc'), ),
                 (CIter(),)),
        resultspec="O",
        argspec="O",
        cmpfunc=unhandled_error_compare
    )

    # PyBytes_Concat
    test_PyBytes_Concat = CPyExtFunctionOutVars(
        lambda args: (0, args[0] + args[1]),        
        lambda: tuple([tuple(["hello".encode(), " world".encode()])]),
        code='''int wrap_PyBytes_Concat(PyObject** arg0, PyObject* arg1) {
            if(*arg0) {
                Py_INCREF(*arg0);
            }
            if(arg1) {
                Py_INCREF(arg1);
            }
            PyBytes_Concat(arg0, arg1);
            return 0;
        }''',
        resultspec="iO",
        argspec="OO",
        arguments=["PyObject* arg0", "PyObject* arg1"],
        resulttype="int",
        argumentnames="&arg0, arg1",
        resultvarnames="arg0",
        callfunction="wrap_PyBytes_Concat"
    )

    # PyBytes_ConcatAndDel
    test_PyBytes_ConcatAndDel = CPyExtFunctionOutVars(
        lambda args: (0, args[0] + args[1]),
        lambda: tuple([tuple(["hello".encode(), " world".encode()])]),
        code='''int wrap_PyBytes_ConcatAndDel(PyObject** arg0, PyObject* arg1) {
            if(*arg0) {
                Py_INCREF(*arg0);
            }
            if(arg1) {
                Py_INCREF(arg1);
            }
            PyBytes_ConcatAndDel(arg0, arg1);
            return 0;
        }''',
        resultspec="iO",
        argspec="OO",
        arguments=["PyObject* arg0", "PyObject* arg1"],
        resulttype="int",
        argumentnames="&arg0, arg1",
        resultvarnames="arg0",
        callfunction="wrap_PyBytes_ConcatAndDel"
    )

    test_PyBytes_Check = CPyExtFunction(
        lambda args: isinstance(args[0], bytes),
        lambda: (
            (b"hello",),
            ("hello",),
            ("hellö".encode(),),
            (BytesSubclass(b"hello"),),
        ),
        resultspec="i",
        argspec='O',
        arguments=["PyObject* o"],
        cmpfunc=unhandled_error_compare
    )

    test_PyBytes_CheckExact = CPyExtFunction(
        lambda args: type(args[0]) == bytes,
        lambda: (
            (b"hello",),
            (bytes(),),
            ("hello",),
            ("hellö".encode(),),
            (1,),
            (dict(),),
            (tuple(),),
            (BytesSubclass(b"hello"),),
        ),
        resultspec="i",
        argspec='O',
        arguments=["PyObject* o"],
        cmpfunc=unhandled_error_compare
    )

    # PyBytes_AS_STRING
    test_PyBytes_AS_STRING = CPyExtFunction(
        lambda b: b[0].decode("utf-8"),
        lambda: (
            (b"hello",),
            ("hellö".encode("utf-8"),),
            (b"hello world",),
            (b"",),
            (BytesSubclass(b"hello"),),
        ),
        resultspec="s",
        argspec="O",
        arguments=["PyObject* arg"],
        cmpfunc=unhandled_error_compare
    )

    test_PyBytes_Resize = CPyExtFunction(
        lambda args: args[0][:args[1]],
        lambda: (
            (b"hello_beatiful_world", 5),
        ),
        code="""
        PyObject* wrap_PyBytes_Resize(PyObject* bytesObj, Py_ssize_t new_size) {
            /* we need to create a fresh bytes object */
            PyObject* res = PyBytes_FromString(PyBytes_AsString(bytesObj));
            _PyBytes_Resize(&res, new_size);
            return res;
        }
        """,
        resultspec="O",
        argspec="On",
        arguments=["PyObject* bytesObj", "Py_ssize_t new_size"],
        callfunction="wrap_PyBytes_Resize",
        cmpfunc=unhandled_error_compare
    )

    test__PyBytes_Join = CPyExtFunction(
        lambda args: args[0].join(args[1]),
        lambda: ( 
            (b"hello", b"world"),
        ),        
        resultspec="O",
        argspec="OO",
        arguments=["PyObject* original", "PyObject* newPart"],
        cmpfunc=unhandled_error_compare
    )

    test_PyBytes_Resize_NativeStorage = CPyExtFunction(
        lambda args: args[1],
        lambda: (
            (b"hello_beatiful_world", b"hello_world"),
        ),
        code="""
        #include <stdio.h>
        
        /* Copies content from 'smaller' to 'larger_content' and returns a pointer to the last char. */
        static char* do_pointer_arithmetics(char* larger_content, PyObject* smaller) {
            char* smaller_content = PyBytes_AS_STRING(smaller);
            Py_ssize_t smaller_len = PyBytes_Size(smaller);
            
            // 'smaller_len + 1' also contains the null byte
            memcpy(larger_content, smaller_content, smaller_len + 1);
            return larger_content + smaller_len;
        }
        
        PyObject* resize_bytes(PyObject* larger, PyObject* smaller) {
            char* data;
            char* dummy;
            char* end_ptr;
            Py_ssize_t len;
            Py_ssize_t new_len;
            PyObject* larger_copy;
            
            Py_INCREF(larger);
            Py_INCREF(smaller);
            
            /* we need to create a fresh bytes object */
            larger_copy = PyBytes_FromString(PyBytes_AsString(larger));
            Py_DECREF(larger);
            
            len = PyBytes_Size(larger_copy) + 1;
            
            dummy = (char*) calloc(len, sizeof(char));
            data = PyBytes_AS_STRING(larger_copy);
            
            /* this will force the bytes object's content to native */
            snprintf(data, len, "%s", dummy);
            free(dummy);
            
            /* copy smaller data and return the pointer to the last char */
            end_ptr = do_pointer_arithmetics(data, smaller);
            Py_DECREF(smaller);
            
            /* compute new size */
            new_len = (Py_ssize_t) (end_ptr - PyBytes_AS_STRING(larger_copy));
            _PyBytes_Resize(&larger_copy, new_len);
            memcpy(PyBytes_AS_STRING(larger_copy), data, new_len * sizeof(char));
            return larger_copy;
        }
        """,
        resultspec="O",
        argspec="OO",
        arguments=["PyObject* larger", "PyObject* smaller"],
        callfunction="resize_bytes",
        cmpfunc=unhandled_error_compare
    )

    test_bytearray_buffer = CPyExtFunction(
        lambda args: args[1],
        lambda: (
            (bytearray(b"hello_world"), b"Hello_worlds"),
        ),
        code="""
        PyObject* test_buffer(PyObject* bytes, PyObject* expected) {
            Py_buffer buffer;
            PyObject* ret;
            if (PyObject_GetBuffer(bytes, &buffer, PyBUF_SIMPLE | PyBUF_WRITABLE) != 0)
                return NULL;
            *(char*)buffer.buf = 'H';
            Py_ssize_t len = PyObject_Size(bytes);
            if (len == -1)
                goto error_release;
            ret = PyObject_CallMethod(bytes, "insert", "ni", len, 'x');
            if (ret != NULL) {
                Py_DECREF(ret);
                PyErr_SetString(PyExc_AssertionError, "insert didn't raise BufferError");
                goto error_release;
            }
            if (!PyErr_ExceptionMatches(PyExc_BufferError))
                goto error_release;
            PyErr_Clear();
            PyBuffer_Release(&buffer);
            ret = PyObject_CallMethod(bytes, "insert", "ni", len, 's');
            if (ret == NULL)
                return NULL;
            Py_DECREF(ret);
            Py_INCREF(bytes);
            return bytes;
        error_release: 
            PyBuffer_Release(&buffer);
            return NULL;            
        }
        """,
        resultspec="O",
        argspec="OO",
        arguments=["PyObject* bytes", "PyObject* expected"],
        callfunction="test_buffer",
        cmpfunc=unhandled_error_compare
    )

    test_set_size = CPyExtFunction(
        lambda args: b'1234',
        lambda: ((),),
        code="""
        PyObject* set_size() {
            PyObject* output = PyBytes_FromStringAndSize(NULL, 50);
            if (output == NULL)
                return NULL;
            char* s = PyBytes_AsString(output);
            memcpy(s, "1234", 4);
            Py_SET_SIZE(output, 4);
            return output;
        }
        """,
        resultspec="O",
        argspec="",
        arguments=[],
        callfunction="set_size",
        cmpfunc=unhandled_error_compare
    )


class ObjectTests(unittest.TestCase):
    def test_create_from_buffer(self):
        TestType = CPyExtType(
            "TestBytesBuffer1",
            """
            int bufcount = 0;
            char buf[] = {98, 111, 111};
            int getbuffer(TestBytesBuffer1Object *self, Py_buffer *view, int flags) {
                bufcount++;
                return PyBuffer_FillInfo(view, (PyObject*)self, buf, sizeof(buf), 1, flags);
            }
            void releasebuffer(TestBytesBuffer1Object *self, Py_buffer *view) {
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
        self.assertEqual(b'boo', bytes(obj))
        self.assertEqual(b'boo', bytearray(obj))
        self.assertEqual(0, obj.get_bufcount())

    def test_create_from_buffer_not_buffer(self):
        # test that we fall through to iteration when the object doesn't report a buffer
        TestType = CPyExtType(
            "TestBytesIterable1",
            """
            PyObject* iter(PyObject* self) {
                PyErr_SetString(PyExc_ValueError, "Expected");
                return NULL;
            }
            """,
            tp_iter='&iter'
        )

        self.assertRaises(ValueError, bytes, TestType())
        self.assertRaises(ValueError, bytearray, TestType())

    def test_create_from_buffer_exception(self):
        TestType = CPyExtType(
            "TestBytesBuffer2",
            """
            int getbuffer(TestBytesBuffer2Object *self, Py_buffer *view, int flags) {
                PyErr_SetString(PyExc_ValueError, "I'm broken");
                return -1;
            }
            static PyBufferProcs as_buffer = {
                (getbufferproc)getbuffer,
                (releasebufferproc)NULL,
            };
            """,
            tp_as_buffer='&as_buffer',
        )
        self.assertRaises(ValueError, bytes, TestType())
        self.assertRaises(ValueError, bytearray, TestType())

    def test_tp_as_buffer_pickle(self):
        TestAsBufferPickle = CPyExtType(
            "TestAsBufferPickle",
            """
            static PyObject* get_bytes(PyObject* self, PyObject *obj) {
                Py_buffer view;
                if (PyObject_GetBuffer(obj, &view, PyBUF_FULL_RO) != 0)
                    return NULL;
                PyObject* bytes = PyBytes_FromStringAndSize(view.buf, view.len);
                PyBuffer_Release(&view);
                return bytes;
            }
            """,
            tp_methods='{"get_bytes", (PyCFunction)get_bytes, METH_O | METH_CLASS, ""}',
        )
        TestType = CPyExtType(
            "TestMemoryViewBufferPickle",
            """
            char buf[] = {1,2,3,4};
            int getbuffer(TestMemoryViewBufferPickleObject *self, Py_buffer *view, int flags) {
                return PyBuffer_FillInfo(view, (PyObject*)self, buf, 4, 1, flags);
            }
            void releasebuffer(TestMemoryViewBufferPickleObject *self, Py_buffer *view) {}
            
            static PyBufferProcs as_buffer = {
                (getbufferproc)getbuffer,
                (releasebufferproc)releasebuffer,
            };
            """,
            tp_as_buffer='&as_buffer',
        )
        obj = TestType()
        b = bytes([1,2,3,4]) # same as `buf[] = {1,2,3,4};`
        import pickle
        b2 = TestAsBufferPickle.get_bytes(pickle.PickleBuffer(memoryview(obj)))
        b1 = TestAsBufferPickle.get_bytes(pickle.PickleBuffer(bytearray([1,2,3,4])))
        assert b == b1
        assert b == b2


class TestNativeSubclass(unittest.TestCase):
    def _verify_common(self, b):
        assert b
        assert not BytesSubclass(b'')
        assert len(b) == 5
        assert b[1] == ord('e')
        assert b[1:] == b"ello"
        assert b == b"hello"
        assert b < b"xxxxx"
        assert b > b"aaaaa"
        assert ord('e') in b
        assert b + b" world" == b"hello world"
        assert b * 2 == b"hellohello"
        assert list(b) == [ord('h'), ord('e'), ord('l'), ord('l'), ord('o')]
        assert b.index(ord('l')) == 2
        assert b.count(ord('l')) == 2
        assert b.decode('ascii') == 'hello'
        assert BytesSubclass(b'hello ').strip() == b'hello'
        assert BytesSubclass(b',').join([b'a', BytesSubclass(b'b')]) == b'a,b'
        assert BytesSubclass(b'(%s)') % b'a' == b'(a)'
        assert b.startswith(b'h')
        assert b.endswith(b'o')
        assert b.hex() == b'hello'.hex()
        assert b.islower()
        assert b.replace(b'e', b'a') == b'hallo'
        assert b.upper() == b'HELLO'
        assert BytesSubclass(b'a,b').split(BytesSubclass(b',')) == [b'a', b'b']

    def _verify_bytes(self, b):
        self._verify_common(b)
        assert is_native_object(b)
        assert repr(b) == "b'hello'", repr(b)
        assert hash(b) == hash(b'hello')

    def _verify_bytearray(self, b):
        self._verify_common(b)
        # assert is_native_object(b) # TODO: BuiltinConstructors.ByteArrayNode doesnt allocate native objects
        self.assertRaises(TypeError, lambda: hash(b))

    def test_bytes_subclass(self):
        b = BytesSubclass(b"hello")
        assert type(b) == BytesSubclass
        self._verify_bytes(b)

    def test_managed_bytes_subclass(self):
        class ManagedSubclass(BytesSubclass):
            pass

        b = ManagedSubclass(b"hello")
        assert type(b) == ManagedSubclass
        self._verify_bytes(b)

    def test_bytearray_subclass(self):
        b = ByteArraySubclass(b"hello")
        assert type(b) == ByteArraySubclass
        # TODO: if TypeNode.GetNameNode is equivalent to _PyType_Name, it should transform tp_name "abc.xzy" to "xyz"
        # assert repr(b) == "ByteArraySubclass(b'hello')", repr(b)
        self._verify_bytearray(b)

    def test_managed_bytearray_subclass(self):
        class ArrManagedSubclass(ByteArraySubclass):
            pass

        b = ArrManagedSubclass(b"hello")
        assert type(b) == ArrManagedSubclass
        # TODO: if TypeNode.GetNameNode is equivalent to _PyType_Name, it should transform tp_name "abc.xzy" to "xyz"
        # assert repr(b) == "ArrManagedSubclass(b'hello')", repr(b)
        self._verify_bytearray(b)