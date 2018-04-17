# Copyright (c) 2018, Oracle and/or its affiliates.
#
# All rights reserved.
from . import CPyExtTestCase, CPyExtFunction, CPyExtFunctionOutVars, GRAALPYTHON
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
        lambda: ((b"hello", ), (b"world",)),
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
    )

    # PyBytes_Size
    test_PyBytes_Size = CPyExtFunction(
        lambda b: len(b[0]),
        lambda: ((b"hello", ), (b"hello world",), (b"",)),
        resultspec="n",
        argspec="O",
        arguments=["PyObject* arg"],
    )

    # PyBytes_FromFormat
    test_PyBytes_FromFormat = CPyExtFunction(
        _reference_format,
        lambda: (("hello %s %s, %d times", "beautiful", "world", 3), ),
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
        argumentnames="&arg0, arg1",
        resultvarnames="arg0",
        callfunction="wrap_PyBytes_ConcatAndDel"
    )


