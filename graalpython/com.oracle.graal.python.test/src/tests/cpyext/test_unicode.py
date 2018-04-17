# Copyright (c) 2018, Oracle and/or its affiliates.
#
# All rights reserved.
import sys
import warnings
from . import CPyExtTestCase, CPyExtFunction, CPyExtFunctionOutVars, CPyExtFunctionVoid, unhandled_error_compare, GRAALPYTHON
__dir__ = __file__.rpartition("/")[0]


def _reference_fromobject(args):
    if isinstance(args[0], str):
        return str(args[0])
    raise TypeError("Can't convert '%s' object to str implicitly" % type(args[0]).__name__)


def _reference_intern(args):
    return sys.intern(args[0])
    

class CustomString(str):
    pass


class Dummy():
    pass


def gen_intern_args():
    args = (
        ("some text",),
    )
    if GRAALPYTHON:
        import copy
        return copy.copy(args)
    else:
        return args


class TestPyUnicode(CPyExtTestCase):
    def compile_module(self, name):
        type(self).mro()[1].__dict__["test_%s" % name].create_module(name)
        super(TestPyUnicode, self).compile_module(name)


    test_PyUnicode_FromObject = CPyExtFunction(
        _reference_fromobject,
        lambda: (
            ("hello",),
            (CustomString(),),
            (b"hello",),
            (Dummy(),),
            (str,),
        ),
        resultspec="O",
        argspec='O',
        arguments=["PyObject* v"],
        cmpfunc=unhandled_error_compare
    )

    test_PyUnicode_FromStringAndSize = CPyExtFunction(
        lambda args: args[0][:args[1]],
        lambda: (
            ("hello", 3),
            ("hello", 5),
        ),
        resultspec="O",
        argspec='sn',
        arguments=["char* v", "Py_ssize_t n"],
        cmpfunc=unhandled_error_compare
    )

    test_PyUnicode_FromFormat = CPyExtFunction(
        lambda args: args[0] % tuple(args[1:]),
        lambda: (
            ("word0: %s; word1: %s; int: %d", "hello", "world", 1234),
        ),
        resultspec="O",
        argspec='sssi',
        arguments=["char* fmt", "char* arg0", "char* arg1", "int n"],
        cmpfunc=unhandled_error_compare
    )

    test_PyUnicode_FromUnicode = CPyExtFunction(
        lambda args: args[0],
        lambda: (
            ("hello", ),
            ("hellö", ),
        ),
        code="""#include <unicodeobject.h>
        
        PyObject* wrap_PyUnicode_FromUnicode(PyObject* strObj) {
            Py_UNICODE* wchars;
            Py_ssize_t n;
            n = PyUnicode_GetLength(strObj);
            wchars = malloc(n*sizeof(Py_UNICODE));
            PyUnicode_AsWideChar(strObj, wchars, n);
            return PyUnicode_FromUnicode(wchars, n);
        }
        """,
        resultspec="O",
        argspec='O',
        arguments=["PyObject* strObj"],
        callfunction="wrap_PyUnicode_FromUnicode",
        cmpfunc=unhandled_error_compare
    )

    test_PyUnicode_GetLength = CPyExtFunction(
        lambda args: len(args[0]),
        lambda: (
            ("hello", ),
            ("world", ),
            ("this is a longer text also cöntaining weird Ümläuts", ),
        ),
        resultspec="n",
        argspec='O',
        arguments=["PyObject* v"],
        cmpfunc=unhandled_error_compare
    )

    test_PyUnicode_Concat = CPyExtFunction(
        lambda args: args[0] + args[1],
        lambda: (
            ("hello", ", world" ),
            ("", "world" ),
            ("this is a longer text also cöntaining weird Ümläuts", "" ),
        ),
        resultspec="O",
        argspec='OO',
        arguments=["PyObject* left", "PyObject* right"],
        cmpfunc=unhandled_error_compare
    )

    test_PyUnicode_FromEncodedObject = CPyExtFunction(
        lambda args: args[0].decode(args[1], args[2]),
        lambda: (
            (b"hello", "ascii", "strict" ),
            ("hellö".encode(), "ascii", "strict" ),
            ("hellö".encode(), "ascii", "ignore" ),
            ("hellö".encode(), "ascii", "replace" ),
            ("hellö".encode(), "utf-8", "strict" ),
            ("hellö".encode(), "utf-8", "blah" ),
        ),
        resultspec="O",
        argspec='Oss',
        arguments=["PyObject* o", "char* encoding", "char* errors"],
        cmpfunc=unhandled_error_compare
    )

    test_PyUnicode_InternInPlace = CPyExtFunction(
        _reference_intern,
        gen_intern_args,
        code="""PyObject* wrap_PyUnicode_InternInPlace(PyObject* s) {
            PyObject *result = s;
            PyUnicode_InternInPlace(&result);
            return result;
        }
        """,
        resultspec="O",
        argspec='O',
        arguments=["PyObject* s"],
        callfunction="wrap_PyUnicode_InternInPlace",
        cmpfunc=unhandled_error_compare
    )

    test_PyUnicode_InternFromString = CPyExtFunction(
        _reference_intern,
        gen_intern_args,
        resultspec="O",
        argspec='s',
        arguments=["char* s"],
        cmpfunc=unhandled_error_compare
    )

    test_PyUnicode_AsUTF8 = CPyExtFunction(
        lambda args: args[0],
        lambda: (
            ("hello", ),
            ("hellö", ),
        ),
        resultspec="s",
        argspec='O',
        arguments=["PyObject* s"],
        cmpfunc=unhandled_error_compare
    )

    test_PyUnicode_AsUTF8String = CPyExtFunction(
        lambda args: args[0].encode("utf-8"),
        lambda: (
            ("hello", ),
            ("hellö", ),
        ),
        resultspec="O",
        argspec='O',
        arguments=["PyObject* s"],
        cmpfunc=unhandled_error_compare
    )

    test_PyUnicode_DecodeUTF32 = CPyExtFunction(
        lambda args: args[1],
        lambda: (
            # UTF32-LE codepoint for 'hello'
            (b"\x68\x00\x00\x00\x65\x00\x00\x00\x6c\x00\x00\x00\x6c\x00\x00\x00\x6f\x00\x00\x00", "hello" ),
            # UTF-32 codepoint for 'hellö'
            (b"\x68\x00\x00\x00\x65\x00\x00\x00\x6c\x00\x00\x00\x6c\x00\x00\x00\xf6\x00\x00\x00", "hellö" ),
        ),
        code="""PyObject* wrap_PyUnicode_DecodeUTF32(PyObject* bytesObj, PyObject* expected) {
            int byteorder = -1;
            Py_ssize_t n = PyObject_Length(bytesObj);
            char* bytes = PyBytes_AsString(bytesObj);
            PyObject* result = PyUnicode_DecodeUTF32(bytes, n, NULL, &byteorder);
            return result;
        }
        """,
        resultspec="O",
        argspec='OO',
        arguments=["PyObject* strObj", "PyObject* expected"],
        callfunction="wrap_PyUnicode_DecodeUTF32",
        cmpfunc=unhandled_error_compare
    )

    test_PyUnicode_AsLatin1String = CPyExtFunction(
        lambda args: args[0].encode("iso-8859-1"),
        lambda: (
            ("hello", ),
            ("hellö", ),
        ),
        resultspec="O",
        argspec='O',
        arguments=["PyObject* s"],
        cmpfunc=unhandled_error_compare
    )

    test_PyUnicode_AsASCIIString = CPyExtFunction(
        lambda args: args[0].encode("ascii"),
        lambda: (
            ("hello", ),
            ("hellö", ),
        ),
        resultspec="O",
        argspec='O',
        arguments=["PyObject* s"],
        cmpfunc=unhandled_error_compare
    )

    test_PyUnicode_Format = CPyExtFunction(
        lambda args: args[0] % args[1],
        lambda: (
            ("%s, %s", ("hello", "world")),
            ("hellö, %s", ("wörld",)),
            ("%s, %r", ("hello", "world")),
            ("nothing else", tuple()),
        ),
        resultspec="O",
        argspec='OO',
        arguments=["PyObject* format", "PyObject* fmt_args"],
        cmpfunc=unhandled_error_compare
    )
