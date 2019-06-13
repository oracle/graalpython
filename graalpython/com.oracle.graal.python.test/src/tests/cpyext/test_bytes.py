# Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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

from . import CPyExtTestCase, CPyExtFunction, CPyExtFunctionOutVars, unhandled_error_compare, GRAALPYTHON
__dir__ = __file__.rpartition("/")[0]


def _reference_from_string_n(args):
    arg_str = args[0]
    arg_n = args[1]
    return bytes(arg_str[0:arg_n], "utf-8")


def _as_string_and_size(args):
    arg_bytes = args[0]
    s = arg_bytes.decode("utf-8")
    return (0, s, len(s))


def _reference_format(args):
    fmt = args[0]
    fmt_args = tuple(args[1:])
    return (fmt % fmt_args).encode()


class TestPyBytes(CPyExtTestCase):

    def compile_module(self, name):
        type(self).mro()[1].__dict__["test_%s" % name].create_module(name)
        super(TestPyBytes, self).compile_module(name)

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
        lambda args: len(b"\x00"*args[0]),
        lambda: ( (128, ), ),
        code = """Py_ssize_t PyBytes_FromStringAndSizeNULL(Py_ssize_t n) {
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
        lambda b: b[0].decode(),
        lambda: ((b"hello",), (b"world",)),
        resultspec="s",
        argspec="O",
        arguments=["PyObject* arg"],
    )

    # PyBytes_AsStringAndSize
    test_PyBytes_AsStringAndSize = CPyExtFunctionOutVars(
        _as_string_and_size,
        lambda: ((b"hello",), (b"world",)),
        resultspec="isn",
        argspec="O",
        arguments=["PyObject* arg"],
        resultvars=("char* s", "Py_ssize_t sz"),
        resulttype="int"
    )

    # PyBytes_Size
    test_PyBytes_Size = CPyExtFunction(
        lambda b: len(b[0]),
        lambda: ((b"hello",), (b"hello world",), (b"",)),
        resultspec="n",
        argspec="O",
        arguments=["PyObject* arg"],
    )

    # PyBytes_GET_SIZE
    test_PyBytes_GET_SIZE = CPyExtFunction(
        lambda b: len(b[0]),
        lambda: ((b"hello",), (b"hello world",), (b"",)),
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
        ),
        resultspec="i",
        argspec='O',
        arguments=["PyObject* o"],
        cmpfunc=unhandled_error_compare
    )

    test_PyBytes_CheckExact = CPyExtFunction(
        lambda args: isinstance(args[0], bytes),
        lambda: (
            (b"hello",),
            (bytes(),),
            ("hello",),
            ("hellö".encode(),),
            (1,),
            (dict(),),
            (tuple(),),
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
            (b"",)
        ),
        resultspec="s",
        argspec="O",
        arguments=["PyObject* arg"],
        cmpfunc=unhandled_error_compare
    )

    test_PyBytes_Mutation = CPyExtFunction(
        lambda args: args[1],
        lambda: (
            (b"hello", b"hallo"),
        ),
        code="""PyObject* mutate_bytes(PyObject* bytesObj, PyObject* expected) {
            char* content = PyBytes_AS_STRING(bytesObj);
            char* copy = (char*) malloc(PyBytes_Size(bytesObj)+1);
            content[1] = 'a';
            memcpy(copy, content, PyBytes_Size(bytesObj)+1);
            return PyBytes_FromString(copy);
        }
        """,
        resultspec="O",
        argspec="OO",
        arguments=["PyObject* bytesObj", "PyObject* expected"],
        callfunction="mutate_bytes",
        cmpfunc=unhandled_error_compare
    )
