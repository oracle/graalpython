# Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
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

import codecs
import re
import sys

from . import CPyExtType, CPyExtTestCase, CPyExtFunction, unhandled_error_compare, GRAALPYTHON, CPyExtFunctionOutVars

__dir__ = __file__.rpartition("/")[0]


def _reference_fromobject(args):
    if isinstance(args[0], str):
        return str(args[0])
    raise TypeError("Can't convert '%s' object to str implicitly" % type(args[0]).__name__)


def _reference_intern(args):
    return sys.intern(args[0])


def _reference_findchar(args):
    string = args[0]
    char = chr(args[1])
    start = args[2]
    end = args[3]
    direction = args[4]
    if sys.version_info.minor < 7 and end >= len(string):
        return -1
    if not isinstance(string, str):
        return -1
    if direction == 1:
        return string.find(char, start, end)
    elif direction == -1:
        return string.rfind(char, start, end)


def _reference_unicode_escape(args):
    import _codecs
    return _codecs.unicode_escape_encode(args[0])[0]


def _reference_fromformat(args):
    fmt = args[0]
    fmt_args = args[1:]
    # replace specifiers
    for s in ["%ld", "%li", "%lu", "%lld", "%lli", "%llu"]:
        fmt = fmt.replace(s, "%d")
    return fmt % fmt_args


def _reference_readchar(args):
    s = args[0]
    i = args[1]
    if i < 0:
        # just ensure that it is out of bounds
        i = len(s) + 1
    return ord(s[i])


def _reference_contains(args):
    if not isinstance(args[0], str) or not isinstance(args[1], str):
        raise TypeError
    return args[1] in args[0]


def _reference_compare(args):
    if not isinstance(args[0], str) or not isinstance(args[1], str):
        raise TypeError

    if args[0] == args[1]:
        return 0
    elif args[0] < args[1]:
        return -1
    else:
        return 1


def _reference_as_encoded_string(args):
    if not isinstance(args[0], str):
        raise TypeError

    s = args[0]
    encoding = args[1]
    errors = args[2]
    return s.encode(encoding, errors)


_codecs_module = None


def _reference_as_unicode_escape_string(args):
    if not isinstance(args[0], str):
        raise TypeError
    global _codecs_module
    if not _codecs_module:
        import _codecs as _codecs_module
    return _codecs_module.unicode_escape_encode(args[0])[0]


def _reference_tailmatch(args):
    if not isinstance(args[0], str) or not isinstance(args[1], str):
        raise TypeError

    s = args[0]
    substr = args[1]
    start = args[2]
    end = args[3]
    direction = args[4]
    if direction > 0:
        return 1 if s[start:end].endswith(substr) else 0
    return 1 if s[start:end].startswith(substr) else 0


def _reference_find(args):
    s, sub, start, end, direction = args
    if direction > 0:
        return s.find(sub, start, end)
    else:
        return s.rfind(sub, start, end)


def _decoder_for_utf16(byteorder):
    if byteorder == 0:
        return codecs.utf_16_decode
    elif byteorder < 0:
        return codecs.utf_16_le_decode
    else:
        return codecs.utf_16_be_decode


class CustomString(str):
    pass


class Dummy():
    pass


class Displayable:
    def __str__(self):
        return 'str: 文字列'

    def __repr__(self):
        return 'repr: 文字列'


class BrokenDisplayable:
    def __str__(self):
        raise NotImplementedError

    def __repr__(self):
        raise NotImplementedError


def compare_ptr_string(x, y):
    if isinstance(x, str) and isinstance(y, str):
        x = re.sub(r'0x[0-9a-fA-F]+', '0xPLACEHOLDER', x)
        y = re.sub(r'0x[0-9a-fA-F]+', '0xPLACEHOLDER', y)
    return unhandled_error_compare(x, y)


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

    test_PyUnicode_FromFormat0 = CPyExtFunction(
        lambda args: args[0].replace('%%', '%'),
        lambda: (
            ("hello, world!",),
            ("<%%>",),
            ("<%6>",),
        ),
        code="""PyObject* wrap_PyUnicode_FromFormat0(char* fmt) {
            return PyUnicode_FromFormat(fmt);
        }
        """,
        resultspec="O",
        argspec='s',
        arguments=["char* fmt"],
        callfunction="wrap_PyUnicode_FromFormat0",
        cmpfunc=unhandled_error_compare
    )

    test_PyUnicode_FromFormat_c = CPyExtFunction(
        _reference_fromformat,
        lambda: (
            ("char %c\n", ord('x')),
            ("char %c\n", ord('あ')),
        ),
        resultspec="O",
        argspec='si',
        arguments=["char* fmt", "int c"],
        callfunction="PyUnicode_FromFormat",
        cmpfunc=unhandled_error_compare
    )

    test_PyUnicode_FromFormat_U = CPyExtFunction(
        lambda args: f'obj({args[1]})',
        lambda: (
            ("obj(%U)", "str"),
        ),
        resultspec="O",
        argspec='sO',
        arguments=["char* fmt", "PyObject* obj"],
        callfunction="PyUnicode_FromFormat",
        cmpfunc=unhandled_error_compare
    )

    test_PyUnicode_FromFormat_V = CPyExtFunction(
        lambda args: f'obj({args[1] or args[2]})',
        lambda: (
            ("obj(%V)", "str", "fallback"),
            ("obj(%V)", None, "fallback"),
        ),
        code="""PyObject* wrap_PyUnicode_FromFormat_V(char* fmt, PyObject* obj, char* fallback) {
            if (obj == Py_None)
                obj = NULL;
            return PyUnicode_FromFormat(fmt, obj, fallback);
        }
        """,
        resultspec="O",
        argspec='sOs',
        arguments=["char* fmt", "PyObject* obj", "char* fallback"],
        callfunction="wrap_PyUnicode_FromFormat_V",
        cmpfunc=unhandled_error_compare
    )

    test_PyUnicode_FromFormat_S = CPyExtFunction(
        lambda args: f'obj({args[1]})',
        lambda: (
            ("obj(%S)", "str"),
            ("obj(%S)", Displayable()),
            ("obj(%S)", BrokenDisplayable()),
        ),
        resultspec="O",
        argspec='sO',
        arguments=["char* fmt", "PyObject* obj"],
        callfunction="PyUnicode_FromFormat",
        cmpfunc=unhandled_error_compare
    )

    test_PyUnicode_FromFormat_R = CPyExtFunction(
        lambda args: f'obj({args[1]!r})',
        lambda: (
            ("obj(%R)", "str"),
            ("obj(%R)", Displayable()),
            ("obj(%R)", BrokenDisplayable()),
        ),
        resultspec="O",
        argspec='sO',
        arguments=["char* fmt", "PyObject* obj"],
        callfunction="PyUnicode_FromFormat",
        cmpfunc=unhandled_error_compare
    )

    test_PyUnicode_FromFormat_A = CPyExtFunction(
        lambda args: f'obj({args[1]!a})',
        lambda: (
            ("obj(%A)", "str"),
            ("obj(%A)", Displayable()),
            ("obj(%A)", BrokenDisplayable()),
        ),
        resultspec="O",
        argspec='sO',
        arguments=["char* fmt", "PyObject* obj"],
        callfunction="PyUnicode_FromFormat",
        cmpfunc=unhandled_error_compare
    )

    test_PyUnicode_FromFormat_p = CPyExtFunction(
        lambda args: "ptr(0xdeadbeef)",
        lambda: (
            ("ptr(%p)", object()),
        ),
        resultspec="O",
        argspec='sO',
        arguments=["char* fmt", "PyObject* obj"],
        callfunction="PyUnicode_FromFormat",
        cmpfunc=compare_ptr_string
    )

    test_PyUnicode_FromFormat4 = CPyExtFunction(
        _reference_fromformat,
        lambda: (
            ("word0: %s; word1: %s; int: %d; long long: %lld", "hello", "world", 1234, 1234),
            ("word0: %s; word1: %s; int: %d; long long: %lld", "hello", "world", 1234, (1 << 44) + 123),
        ),
        code="typedef long long longlong_t;",
        resultspec="O",
        argspec='sssiL',
        arguments=["char* fmt", "char* arg0", "char* arg1", "int n", "longlong_t l"],
        callfunction="PyUnicode_FromFormat",
        cmpfunc=unhandled_error_compare
    )

    test_PyUnicode_FromUnicode = CPyExtFunction(
        lambda args: args[0],
        lambda: (
            ("hello",),
            ("hellö",),
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
            ("hello",),
            ("world",),
            ("this is a longer text also cöntaining weird Ümläuts",),
        ),
        resultspec="n",
        argspec='O',
        arguments=["PyObject* v"],
        cmpfunc=unhandled_error_compare
    )

    test_PyUnicode_Concat = CPyExtFunction(
        lambda args: args[0] + args[1],
        lambda: (
            ("hello", ", world"),
            ("", "world"),
            ("this is a longer text also cöntaining weird Ümläuts", ""),
        ),
        resultspec="O",
        argspec='OO',
        arguments=["PyObject* left", "PyObject* right"],
        cmpfunc=unhandled_error_compare
    )

    test_PyUnicode_FromEncodedObject = CPyExtFunction(
        lambda args: bytes(args[0]).decode(args[1], args[2]),
        lambda: (
            (b"hello", "ascii", "strict"),
            ("hellö".encode(), "ascii", "strict"),
            ("hellö".encode(), "ascii", "ignore"),
            ("hellö".encode(), "ascii", "replace"),
            ("hellö".encode(), "utf-8", "strict"),
            ("hellö".encode(), "utf-8", "blah"),
            (memoryview(b"hello"), "ascii", "strict"),
            (memoryview(b'hell\xc3\xb6'), "utf-8", "strict"),
            (memoryview(b'hell\xc3\xb6'), "ascii", "strict"),
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
            ("hello",),
            ("hellö",),
        ),
        resultspec="s",
        argspec='O',
        arguments=["PyObject* s"],
        cmpfunc=unhandled_error_compare
    )

    test_PyUnicode_AsUTF8String = CPyExtFunction(
        lambda args: args[0].encode("utf-8"),
        lambda: (
            ("hello",),
            ("hellö",),
        ),
        resultspec="O",
        argspec='O',
        arguments=["PyObject* s"],
        cmpfunc=unhandled_error_compare
    )

    test_PyUnicode_AsUTF8AndSize = CPyExtFunctionOutVars(
        lambda args: (s := args[0].encode("utf-8"), len(s)),
        lambda: (
            ("hello",),
            ("hellö",),
        ),
        resultspec="yn",
        resulttype='const char*',
        argspec='O',
        arguments=["PyObject* s"],
        resultvars=["Py_ssize_t size"],
        cmpfunc=unhandled_error_compare
    )

    test_PyUnicode_DecodeUTF32 = CPyExtFunction(
        lambda args: args[1],
        lambda: (
            # UTF32-LE codepoint for 'hello'
            (b"\x68\x00\x00\x00\x65\x00\x00\x00\x6c\x00\x00\x00\x6c\x00\x00\x00\x6f\x00\x00\x00", "hello"),
            # UTF-32 codepoint for 'hellö'
            (b"\x68\x00\x00\x00\x65\x00\x00\x00\x6c\x00\x00\x00\x6c\x00\x00\x00\xf6\x00\x00\x00", "hellö"),
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

    test_PyUnicode_DecodeUTF8 = CPyExtFunction(
        lambda args: args[0].decode('utf-8', errors=args[1].decode('ascii')),
        lambda: (
            ('skål'.encode('utf-8'), b'strict'),
            (b'\xc3', b'strict'),
            (b'\xa5l', b'strict'),
            (b'\xc3', b'replace'),
            (b'\xa5l', b'replace'),
        ),
        resultspec="O",
        argspec='y#y',
        arguments=["char* bytes", "Py_ssize_t size", "char* errors"],
        cmpfunc=unhandled_error_compare
    )

    test_PyUnicode_DecodeUTF8Stateful = CPyExtFunction(
        lambda args: codecs.utf_8_decode(args[0], args[1].decode('ascii'), False),
        lambda: (
            ('skål'.encode('utf-8'), b'strict'),
            (b'\xc3', b'strict'),
            (b'\xa5l', b'strict'),
            (b'\xc3', b'replace'),
            (b'\xa5l', b'replace'),
        ),
        code='''
            PyObject* wrap_PyUnicode_DecodeUTF8Stateful(const char* bytes, Py_ssize_t size, const char* errors) {
                Py_ssize_t consumed = 0;
                PyObject* res = PyUnicode_DecodeUTF8Stateful(bytes, size, errors, &consumed);
                if (!res)
                    return NULL;
                return Py_BuildValue("On", res, consumed);
            }
        ''',
        callfunction="wrap_PyUnicode_DecodeUTF8Stateful",
        resultspec="O",
        argspec='y#y',
        arguments=["char* bytes", "Py_ssize_t size", "char* errors"],
        cmpfunc=unhandled_error_compare
    )

    test_PyUnicode_DecodeUTF16 = CPyExtFunction(
        lambda args: _decoder_for_utf16(args[2])(args[0], args[1].decode('ascii'), True)[0],
        lambda: (
            ('skål'.encode('utf-16'), b'strict', 0),
            ('skål'.encode('utf-16-le'), b'strict', -1),
            ('skål'.encode('utf-16-be'), b'strict', 1),
            (b'a', b'strict', 0),
            (b'=\xd8', b'strict', 0),
            (b'\x02\xde', b'strict', 0),
            (b'\x02\xde', b'replace', 0),
        ),
        code='''
            PyObject* wrap_PyUnicode_DecodeUTF16(const char* bytes, Py_ssize_t size, const char* errors, int byteorder) {
                return PyUnicode_DecodeUTF16(bytes, size, errors, &byteorder);
            }
        ''',
        callfunction="wrap_PyUnicode_DecodeUTF16",
        resultspec="O",
        argspec='y#yi',
        arguments=["char* bytes", "Py_ssize_t size", "char* errors", "int byteorder"],
        cmpfunc=unhandled_error_compare
    )

    test_PyUnicode_DecodeUTF16Stateful = CPyExtFunction(
        lambda args: _decoder_for_utf16(args[2])(args[0], args[1].decode('ascii'), False),
        lambda: (
            ('skål'.encode('utf-16'), b'strict', 0),
            ('skål'.encode('utf-16-le'), b'strict', -1),
            ('skål'.encode('utf-16-be'), b'strict', 1),
            (b'a', b'strict', 0),
            (b'=\xd8', b'strict', 0),
            (b'\x02\xde', b'strict', 0),
            (b'\x02\xde', b'replace', 0),
        ),
        code='''
            PyObject* wrap_PyUnicode_DecodeUTF16Stateful(const char* bytes, Py_ssize_t size, const char* errors, int byteorder) {
                Py_ssize_t consumed = 0;
                PyObject* res = PyUnicode_DecodeUTF16Stateful(bytes, size, errors, &byteorder, &consumed);
                if (!res)
                    return NULL;
                return Py_BuildValue("On", res, consumed);
            }
        ''',
        callfunction="wrap_PyUnicode_DecodeUTF16Stateful",
        resultspec="O",
        argspec='y#yi',
        arguments=["char* bytes", "Py_ssize_t size", "char* errors", "int byteorder"],
        cmpfunc=unhandled_error_compare
    )

    test_PyUnicode_AsLatin1String = CPyExtFunction(
        lambda args: args[0].encode("iso-8859-1"),
        lambda: (
            ("hello",),
            ("hellö",),
        ),
        resultspec="O",
        argspec='O',
        arguments=["PyObject* s"],
        cmpfunc=unhandled_error_compare
    )

    test_PyUnicode_AsASCIIString = CPyExtFunction(
        lambda args: args[0].encode("ascii"),
        lambda: (
            ("hello",),
            ("hellö",),
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

    test_PyUnicode_Check = CPyExtFunction(
        lambda args: isinstance(args[0], str),
        lambda: (
            ("hello",),
            ("hellö",),
            (b"hello",),
            ("hellö",),
            (['a', 'b', 'c'],),
        ),
        resultspec="i",
        argspec='O',
        arguments=["PyObject* o"],
        cmpfunc=unhandled_error_compare
    )

    test_PyUnicode_GET_SIZE = CPyExtFunction(
        lambda args: len(args[0]),
        lambda: (
            ("hello",),
        ),
        resultspec="n",
        argspec='O',
        arguments=["PyObject* o"],
        cmpfunc=unhandled_error_compare
    )

    test_PyUnicode_AsUnicode = CPyExtFunction(
        lambda args: True,
        lambda: (
            ("hello", b'\x68\x00\x65\x00\x6c\x00\x6c\x00\x6f\x00',
             b"\x68\x00\x00\x00\x65\x00\x00\x00\x6c\x00\x00\x00\x6c\x00\x00\x00\x6f\x00\x00\x00"),
        ),
        code=""" PyObject* wrap_PyUnicode_AsUnicode(PyObject* unicodeObj, PyObject* expected_16, PyObject* expected_32) {
            Py_ssize_t n = Py_UNICODE_SIZE == 2 ? PyBytes_Size(expected_16) : PyBytes_Size(expected_32);
            char* actual_bytes = (char*) PyUnicode_AsUnicode(unicodeObj);
            char* expected_bytes = Py_UNICODE_SIZE == 2 ? PyBytes_AsString(expected_16) : PyBytes_AsString(expected_32);
            Py_ssize_t i;
            for (i=0; i < n; i++) {
                if (actual_bytes[i] != expected_bytes[i]) {
                    PyErr_Format(PyExc_ValueError, "invalid byte at %d: expected '%c', but was '%c'", i, expected_bytes[i], actual_bytes[i]);
                    return NULL;
                }
            }
            return Py_True;
        }
        """,
        resultspec="O",
        argspec='OOO',
        arguments=["PyObject* unicodeObj", "PyObject* expected_16", "PyObject* expected_32"],
        callfunction="wrap_PyUnicode_AsUnicode",
        cmpfunc=unhandled_error_compare
    )

    test_PyUnicode_AsUnicodeAndSize = CPyExtFunction(
        lambda args: True,
        lambda: (
            ("hello", b'\x68\x00\x65\x00\x6c\x00\x6c\x00\x6f\x00',
             b"\x68\x00\x00\x00\x65\x00\x00\x00\x6c\x00\x00\x00\x6c\x00\x00\x00\x6f\x00\x00\x00"),
        ),
        code=""" PyObject* wrap_PyUnicode_AsUnicodeAndSize(PyObject* unicodeObj, PyObject* expected_16, PyObject* expected_32) {
            Py_ssize_t n = Py_UNICODE_SIZE == 2 ? PyBytes_Size(expected_16) : PyBytes_Size(expected_32);
            Py_ssize_t expected_n = n / Py_UNICODE_SIZE;
            Py_ssize_t actual_n = 0;
            char* actual_bytes = (char*) PyUnicode_AsUnicodeAndSize(unicodeObj, &actual_n);
            char* expected_bytes = Py_UNICODE_SIZE == 2 ? PyBytes_AsString(expected_16) : PyBytes_AsString(expected_32);
            Py_ssize_t i;
            if (expected_n != actual_n) {
                PyErr_Format(PyExc_ValueError, "invalid size: expected '%ld', but was '%ld'", expected_n, actual_n);
                return NULL;
            }
            for (i=0; i < n; i++) {
                if (actual_bytes[i] != expected_bytes[i]) {
                    PyErr_Format(PyExc_ValueError, "invalid byte at %d: expected '%c', but was '%c'", i, expected_bytes[i], actual_bytes[i]);
                    return NULL;
                }
            }
            return Py_True;
        }
        """,
        resultspec="O",
        argspec='OOO',
        arguments=["PyObject* unicodeObj", "PyObject* expected_16", "PyObject* expected_32"],
        callfunction="wrap_PyUnicode_AsUnicodeAndSize",
        cmpfunc=unhandled_error_compare
    )

    test_PyUnicode_FindChar = CPyExtFunction(
        _reference_findchar,
        lambda: (
            ("hello", ord("l"), 0, 5, 1),
            ("hello", ord("l"), 0, 5, -1),
            ("hello", ord("x"), 0, 5, 1),
            ("hello", ord("l"), 0, 2, 1),
            ("hello", ord("l"), 3, 5, 1),
            ("hello", ord("l"), 4, 5, 1),
        ),
        code="""Py_ssize_t wrap_PyUnicode_FindChar(PyObject* str, Py_ssize_t ch, Py_ssize_t start, Py_ssize_t end, int direction) {
            return PyUnicode_FindChar(str, ch, start, end, direction);
        }
        """,
        resultspec="n",
        argspec='Onnni',
        arguments=["PyObject* str", "Py_ssize_t ch", "Py_ssize_t start", "Py_ssize_t end", "int direction"],
        callfunction="wrap_PyUnicode_FindChar",
        cmpfunc=unhandled_error_compare
    )

    test_PyUnicode_Substring = CPyExtFunction(
        lambda args: args[0][args[1]:args[2]],
        lambda: (
            ("hello", 0, 5),
            ("hello", 0, 0),
            ("hello", 0, 1),
            ("hello", 4, 5),
            ("hello", 1, 4),
        ),
        resultspec="O",
        argspec='Onn',
        arguments=["PyObject* str", "Py_ssize_t start", "Py_ssize_t end"],
        cmpfunc=unhandled_error_compare
    )

    test_PyUnicode_Join = CPyExtFunction(
        lambda args: args[0].join(args[1]),
        lambda: (
            (", ", [0, 1, 2, 3, 4, 5]),
            (", ", []),
            (", ", None),
            (", ", ("a", "b", "c")),
        ),
        resultspec="O",
        argspec='OO',
        arguments=["PyObject* str", "PyObject* seq"],
        cmpfunc=unhandled_error_compare
    )

    test_PyUnicode_Compare = CPyExtFunction(
        _reference_compare,
        lambda: (
            ("a", "a"),
            ("a", "b"),
            ("a", None),
            ("a", 1),
        ),
        resultspec="i",
        argspec='OO',
        arguments=["PyObject* left", "PyObject* right"],
        cmpfunc=unhandled_error_compare
    )

    test_PyUnicode_Tailmatch = CPyExtFunction(
        _reference_tailmatch,
        lambda: (
            ("abc", "a", 0, 1, 0),
            ("abc", "a", 0, 1, 1),
            ("abc", "a", 0, 0, 1),
            ("abc", "c", 0, 1, 0),
            ("abc", "c", 0, 1, 1),
            ("abc", None, 0, 1, 1),
            ("abc", 1, 1, 0, 1),
        ),
        resultspec="i",
        argspec='OOnni',
        arguments=["PyObject* left", "PyObject* right", "Py_ssize_t start", "Py_ssize_t end", "int direction"],
        cmpfunc=unhandled_error_compare
    )

    test_PyUnicode_FromOrdinal = CPyExtFunction(
        lambda args: chr(args[0]),
        lambda: (
            (97,),
            (65,),
            (332,),
        ),
        resultspec="O",
        argspec='i',
        arguments=["int ordinal"],
        cmpfunc=unhandled_error_compare
    )

    # NOTE: this test assumes that Python uses UTF-8 encoding for source files
    test_PyUnicode_FromKindAndData = CPyExtFunction(
        lambda args: args[3],
        lambda: (
            (4, bytearray([0xA2, 0x0E, 0x02, 0x00]), 1, "𠺢"),
            (4, bytearray([0xA2, 0x0E, 0x02, 0x00, 0x4C, 0x0F, 0x02, 0x00]), 2, "𠺢𠽌"),
            (2, bytearray([0x30, 0x20]), 1, "‰"),
            (2, bytearray([0x30, 0x20, 0x3C, 0x20]), 2, "‰‼"),
        ),
        code='''PyObject* wrap_PyUnicode_FromKindAndData(int kind, Py_buffer buffer, Py_ssize_t size, PyObject* dummy) {
            return PyUnicode_FromKindAndData(kind, (const char *)buffer.buf, size);
        }
        ''',
        resultspec="O",
        argspec='iy*nO',
        arguments=["int kind", "Py_buffer buffer", "Py_ssize_t size", "PyObject* dummy"],
        callfunction="wrap_PyUnicode_FromKindAndData",
        cmpfunc=unhandled_error_compare
    )

    test_PyUnicode_AsEncodedString = CPyExtFunction(
        _reference_as_encoded_string,
        lambda: (
            ("abcd", "ascii", "report"),
            ("abcd", "utf8", "report"),
            ("öüä", "ascii", "report"),
            ("öüä", "utf8", "report"),
            ("öüä", "ascii", "ignore"),
            ("öüä", "ascii", "replace"),
            (1, "ascii", "replace"),
        ),
        resultspec="O",
        argspec='Oss',
        arguments=["PyObject* str", "const char* encoding", "const char* errors"],
        cmpfunc=unhandled_error_compare
    )

    test_PyUnicode_AsUnicodeEscapeString = CPyExtFunction(
        _reference_as_unicode_escape_string,
        lambda: (
            ("abcd",),
            ("öüä",),
            (1,),
        ),
        resultspec="O",
        argspec='O',
        arguments=["PyObject* s"],
        cmpfunc=unhandled_error_compare
    )

    # NOTE: this test assumes that Python uses UTF-8 encoding for source files
    test_PyUnicode_FromWideChar = CPyExtFunction(
        lambda args: args[3],
        lambda: (
            (4, bytearray([0xA2, 0x0E, 0x02, 0x00]), 1, "𠺢"),
            (4, bytearray([0xA2, 0x0E, 0x02, 0x00, 0x4C, 0x0F, 0x02, 0x00]), 2, "𠺢𠽌"),
            (2, bytearray([0x30, 0x20]), 1, "‰"),
            (2, bytearray([0x30, 0x20, 0x3C, 0x20]), 2, "‰‼"),
        ),
        code='''PyObject* wrap_PyUnicode_FromWideChar(int expectedWcharSize, Py_buffer buffer, Py_ssize_t size, PyObject* dummy) {
            if (SIZEOF_WCHAR_T == expectedWcharSize) {
                return PyUnicode_FromWideChar((const wchar_t *) buffer.buf, size);
            }
            return dummy;
        }
        ''',
        resultspec="O",
        argspec='iy*nO',
        arguments=["int expectedWcharSize", "Py_buffer buffer", "Py_ssize_t size", "PyObject* dummy"],
        callfunction="wrap_PyUnicode_FromWideChar",
        cmpfunc=unhandled_error_compare
    )

    test_PyUnicode_New = CPyExtFunction(
        lambda args: args[3],
        lambda: (
            (134818, bytearray([0xA2, 0x0E, 0x02, 0x00]), 1, "𠺢"),
            (134988, bytearray([0xA2, 0x0E, 0x02, 0x00, 0x4C, 0x0F, 0x02, 0x00]), 2, "𠺢𠽌"),
            (8240, bytearray([0x30, 0x20]), 1, "‰"),
            (8252, bytearray([0x30, 0x20, 0x3C, 0x20]), 2, "‰‼"),
            (127, bytearray([0x61, 0x62, 0x63, 0x64]), 4, "abcd"),
            (127, bytearray([0x61, 0x62, 0x63, 0x64]), 2, "ab"),
        ),
        code='''PyObject* wrap_PyUnicode_New(Py_ssize_t maxchar, Py_buffer buffer, Py_ssize_t nchars, PyObject* dummy) {
            PyObject* obj = PyUnicode_New(nchars, (Py_UCS4) maxchar);
            void* data = PyUnicode_DATA(obj);
            size_t char_size;
            if (maxchar < 256) {
                char_size = 1;
            } else if (maxchar < 65536) {
                char_size = 2;
            } else {
                char_size = 4;
            }
            memcpy(data, buffer.buf, nchars * char_size);
            PyUnicode_READY(obj);
            return obj;
        }
        ''',
        resultspec="O",
        argspec='ny*nO',
        arguments=["Py_ssize_t maxchar", "Py_buffer buffer", "Py_ssize_t nchars", "PyObject* dummy"],
        callfunction="wrap_PyUnicode_New",
        cmpfunc=unhandled_error_compare
    )

    test_PyUnicode_ReadChar = CPyExtFunction(
        _reference_readchar,
        lambda: (
            ("hello", 0),
            ("hello", 4),
            ("hello", 100),
            ("hello", -1),
            ("höllö", 4),
        ),
        code='''PyObject* wrap_PyUnicode_ReadChar(PyObject* unicode, Py_ssize_t index) {
            Py_UCS4 res = PyUnicode_ReadChar(unicode, index);
            if (res == -1 && PyErr_Occurred()) {
               return NULL;
            }
            return PyLong_FromLong((long) res);
        }
        ''',
        resultspec="O",
        argspec='On',
        arguments=["PyObject* str", "Py_ssize_t index"],
        callfunction="wrap_PyUnicode_ReadChar",
        cmpfunc=unhandled_error_compare
    )

    test_PyUnicode_Contains = CPyExtFunction(
        _reference_contains,
        lambda: (
            ("aaa", "bbb"),
            ("aaa", "a"),
        ),
        resultspec="i",
        argspec='OO',
        arguments=["PyObject* haystack", "PyObject* needle"],
        cmpfunc=unhandled_error_compare
    )

    test_PyUnicode_Split = CPyExtFunction(
        lambda args: args[0].split(args[1], args[2]),
        lambda: (
            ("foo.bar.baz", ".", 0),
            ("foo.bar.baz", ".", 1),
            ("foo.bar.baz", 7, 0),
        ),
        resultspec="O",
        argspec='OOn',
        arguments=["PyObject* string", "PyObject* sep", "Py_ssize_t maxsplit"],
        cmpfunc=unhandled_error_compare
    )

    test_PyUnicode_Find = CPyExtFunction(
        _reference_find,
        lambda: (
            ("<a> <a> <a>", "<a>", 0, -1, 1),
            ("<a> <a> <a>", "<a>", 0, -1, -1),
            ("<a> <a> <a>", "<a>", 2, -1, 1),
            ("<a> <a> <a>", "<a>", 2, 5, 1),
            ("<a> <a> <a>", "<a>", 2, 10, 1),
            ("<a> <a> <a>", "<a>", 2, 10, -1),
            ("<a> <a> <a>", "<>", 0, -1, 1),
        ),
        code="""
        PyObject* wrap_PyUnicode_Find(PyObject* string, PyObject* sub, Py_ssize_t start, Py_ssize_t end, int direction) {
            Py_ssize_t result = PyUnicode_Find(string, sub, start, end, direction);
            if (result == -2)
                return NULL;
            return PyLong_FromLong(result);
        }
        """,
        callfunction='wrap_PyUnicode_Find',
        resultspec="O",
        argspec='OOnni',
        arguments=["PyObject* string", "PyObject* sub", "Py_ssize_t start", "Py_ssize_t end", "int direction"],
        cmpfunc=unhandled_error_compare
    )

    test_PyUnicode_Count = CPyExtFunction(
        lambda args: args[0].count(args[1], args[2], args[3]),
        lambda: (
            (". .. ....", ".", 0, -1),
            (". .. ....", ".", 3, 7),
            (". .. ....", ".", 3, 19),
            (". .. ....", "..", 0, -1),
            (". .. ....", "...", 0, 4),
        ),
        resultspec="n",
        argspec='OOnn',
        arguments=["PyObject* string", "PyObject* sub", "Py_ssize_t start", "Py_ssize_t end"],
        cmpfunc=unhandled_error_compare
    )

class TestUnicodeObject(object):
    def test_intern(self):
        TestIntern = CPyExtType(
            "TestIntern",
            '''
            static PyObject* set_intern_str(PyObject* self, PyObject* str) {
                PyUnicode_InternInPlace(&str);
                ((TestInternObject*)self)->str = str;
                return str;
            }

            static PyObject* check_is_same_str_ptr(PyObject* self, PyObject* str) {
                PyUnicode_InternInPlace(&str);
                if (str == ((TestInternObject*)self)->str) {
                    Py_RETURN_TRUE;
                } else {
                    Py_RETURN_FALSE;
                }
            }
            ''',
            cmembers="PyObject *str;",
            tp_methods='''
            {"set_intern_str", (PyCFunction)set_intern_str, METH_O, ""},
            {"check_is_same_str_ptr", (PyCFunction)check_is_same_str_ptr, METH_O, ""}
            ''',
        )
        tester = TestIntern()
        s = 'some text'
        assert tester.set_intern_str(s) == s
        assert tester.check_is_same_str_ptr(s)
