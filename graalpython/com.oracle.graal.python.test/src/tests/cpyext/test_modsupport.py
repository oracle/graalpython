# Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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

from . import CPyExtTestCase, CPyExtFunction, unhandled_error_compare


ModuleType = type(sys)


class SubModuleType(ModuleType):
    pass


def _reference_format_specifier_w_star(args):
    bytes_like = args[0]
    bytes_like[0] = ord('a')
    return bytes_like


def _reference_typecheck(args, expected_type):
    if not isinstance(args[0][0], expected_type):
        raise TypeError
    return args[0][0]


def _reference_parse_O(args):
    if not isinstance(args[0], tuple) or not isinstance(args[1], dict):
        raise SystemError
    if len(args[0]) == 1:
        return args[0][0]
    elif "arg0" in args[1]:
        return args[1]["arg0"]
    raise TypeError


def _reference_parse_tuple(args):
    try:
        t = args[0][0]
        if len(t) != 2:
            raise TypeError
        return t[0], t[1]
    except Exception:
        raise TypeError


def _reference_validate_keywords(args):
    kwargs = args[0]
    if not isinstance(kwargs, dict):
        raise SystemError
    if not all(isinstance(k, str) for k in kwargs):
        raise TypeError
    return 1


class Indexable:
    def __int__(self):
        return 456

    def __index__(self):
        return 123


class MySeq:
    def __len__(self):
        return 2

    def __getitem__(self, item):
        if item == 0:
            return 'x'
        elif item == 1:
            return 'y'
        else:
            raise IndexError


class BadSeq:
    def __len__(self):
        return 2

    def __getitem__(self, item):
        raise IndexError

class TestModsupport(CPyExtTestCase):

    testmod = type(sys)("foo")

    test_PyModule_AddStringConstant = CPyExtFunction(
        lambda args: getattr(args[0], args[1]) == args[2],
        lambda: (
            (TestModsupport.testmod, "key", "value"),
        ),
        resultspec="i",
        argspec="Oss",
        arguments=["PyObject* m", "const char* name", "const char* value"],
        cmpfunc=lambda cr, pr: cr == 0 and pr is True
    )

    test_format_specifier_w_star = CPyExtFunction(
        _reference_format_specifier_w_star,
        lambda: (
            (bytearray(b'helloworld'),),
        ),
        code='''PyObject* wrap_PyArg_ParseTuple(PyObject* bytesLike) {
            Py_buffer buf;
            PyObject* args = PyTuple_New(1);
            char *ptr = NULL;
            Py_INCREF(bytesLike);
            PyTuple_SetItem(args, 0, bytesLike);
            Py_INCREF(args);
            if (PyArg_ParseTuple(args, "w*", &buf) == 0) {
                return NULL;
            }
            ptr = (char*) (buf.buf);
            ptr[0] = 'a';
            Py_DECREF(args);
            return bytesLike;
        }
        ''',
        resultspec="O",
        argspec="O",
        arguments=["PyObject* bytesLike"],
        callfunction="wrap_PyArg_ParseTuple",
        cmpfunc=unhandled_error_compare
    )

    test_parseargs_O = CPyExtFunction(
        _reference_parse_O,
        lambda: (
            (('helloworld', ), dict()),
            (tuple(), {"arg0": 'helloworld'}),
            (tuple(), dict()),
            (tuple(), {"arg1": 'helloworld'}),
            (1, dict()),
            (tuple(), 1),
            (("a", "excess"), dict()),
        ),
        code='''
        static PyObject* wrap_PyArg_ParseTupleAndKeywords(PyObject* argTuple, PyObject* kwargs) {
            PyObject* out = NULL;
            static char *kwdnames[] = {"arg0", NULL};
            if (PyArg_ParseTupleAndKeywords(argTuple, kwargs, "O", kwdnames, &out) == 0) {
                return NULL;
            }
            Py_XINCREF(out);
            return out;
        }
        ''',
        resultspec="O",
        argspec="OO",
        arguments=["PyObject* argTuple", "PyObject* kwargs"],
        callfunction="wrap_PyArg_ParseTupleAndKeywords",
        cmpfunc=unhandled_error_compare
    )

    test_parseargs_tuple = CPyExtFunction(
        _reference_parse_tuple,
        lambda: (
            ((("a", "b"),),),
            ((["a", "b"],),),
            ((MySeq(),),),
            ((["a"],),),
            ((["a", "b", "c"],),),
            ((1,),),
            ((BadSeq(),),),
        ),
        code='''
        static PyObject* wrap_PyArg_ParseTuple(PyObject* argTuple) {
            PyObject* a = NULL;
            PyObject* b = NULL;
            if (PyArg_ParseTuple(argTuple, "(OO)", &a, &b) == 0) {
                return NULL;
            }
            return Py_BuildValue("(OO)", a, b);
        }
        ''',
        resultspec="O",
        argspec="O",
        arguments=["PyObject* argTuple"],
        callfunction="wrap_PyArg_ParseTuple",
        cmpfunc=unhandled_error_compare
    )

    test_parseargs_O_conv = CPyExtFunction(
        lambda args: True if args[0][0] else False,
        lambda: (
            ((b'helloworld', ),),
            ((b'', ),),
            (([0, 1, 2], ),),
            (([], ),),
        ),
        code='''
        static int convert_seq(PyObject* obj, void* out) {
            PyObject **objOut = (PyObject **)out;
            *objOut = PySequence_Size(obj) == 0 ? Py_False : Py_True;
            return 1;
        }

        static PyObject* wrap_PyArg_ParseTuple(PyObject* argTuple) {
            PyObject* out = NULL;
            Py_INCREF(argTuple);
            if (PyArg_ParseTuple(argTuple, "O&", convert_seq, &out) == 0) {
                return NULL;
            }
            Py_XINCREF(out);
            return out;
        }
        ''',
        resultspec="O",
        argspec="O",
        arguments=["PyObject* argTuple"],
        callfunction="wrap_PyArg_ParseTuple",
        cmpfunc=unhandled_error_compare
    )

    test_parseargs_O_typecheck = CPyExtFunction(
        lambda args: _reference_typecheck(args, str),
        lambda: (
            (('helloworld', ),),
            (('', ),),
            (([0, 1, 2], ),),
            (([], ),),
        ),
        code='''
        static PyObject* wrap_PyArg_ParseTuple(PyObject* argTuple) {
            PyObject* out = NULL;
            Py_INCREF(argTuple);
            if (PyArg_ParseTuple(argTuple, "O!", &PyUnicode_Type, &out) == 0) {
                return NULL;
            }
            Py_XINCREF(out);
            return out;
        }
        ''',
        resultspec="O",
        argspec="O",
        arguments=["PyObject* argTuple"],
        callfunction="wrap_PyArg_ParseTuple",
        cmpfunc=unhandled_error_compare
    )

    test_parseargs_H = CPyExtFunction(
        lambda args: args[1],
        lambda: (
            ((0, ), 0),
            ((-1, ), 0xFFFF),
            ((-2, ), 0xFFFE),
            ((1, ), 1),
            ((2, ), 2),
            # fits in a a short
            ((0xFFFF, ), 0xFFFF),
            # fits in an int
            ((0xFFFFFFFF, ), 0xFFFF),
            ((0x1234CAFE, ), 0xCAFE),
            # still fits in a long
            ((0xFFFFFFFFFFFFFFFF, ), 0xFFFF),
            # will be a big one
            ((0xFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF, ), 0xFFFF),
        ),
        code='''
        static unsigned short wrap_PyArg_ParseTuple(PyObject* argTuple, PyObject* expected) {
            unsigned short out = 0xDEAD;
            Py_INCREF(argTuple);
            if (PyArg_ParseTuple(argTuple, "H", &out) == 0) {
                return -1;
            }
            return out;
        }
        ''',
        resultspec="H",
        argspec="OO",
        arguments=["PyObject* argTuple", "PyObject* expected"],
        callfunction="wrap_PyArg_ParseTuple",
        cmpfunc=unhandled_error_compare
    )

    test_parseargs_I = CPyExtFunction(
        lambda args: args[1],
        lambda: (
            ((0, ), 0),
            ((-1, ), 0xFFFFFFFF),
            ((-2, ), 0xFFFFFFFE),
            ((1, ), 1),
            ((2, ), 2),
            # fits in an int
            ((0xFFFFFFFF, ), 0xFFFFFFFF),
            # still fits in a long
            ((0xFFFFFFFFFFFFFFFF, ), 0xFFFFFFFF),
            # will be a big one
            ((0xFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF, ), 0xFFFFFFFF),
            ((Indexable(), ), 123 if sys.version_info > (3, 8, 0) else 456),
        ),
        code='''
        static unsigned int wrap_PyArg_ParseTuple(PyObject* argTuple, PyObject* expected) {
            unsigned int out = 0xCAFEBABE;
            Py_INCREF(argTuple);
            if (PyArg_ParseTuple(argTuple, "I", &out) == 0) {
                return -1;
            }
            return out;
        }
        ''',
        resultspec="I",
        argspec="OO",
        arguments=["PyObject* argTuple", "PyObject* expected"],
        callfunction="wrap_PyArg_ParseTuple",
        cmpfunc=unhandled_error_compare
    )

    test_parseargs_K = CPyExtFunction(
        lambda args: args[1],
        lambda: (
            ((0, ), 0),
            ((-1, ), 0xFFFFFFFFFFFFFFFF),
            ((-2, ), 0xFFFFFFFFFFFFFFFE),
            ((1, ), 1),
            ((2, ), 2),
            # fits in an int
            ((0xFFFFFFFF, ), 0xFFFFFFFF),
            ((0xFFFFCAFE, ), 0xFFFFCAFE),
            # still fits in a long
            ((0xFFFFFFFFFFFFFFFF, ), 0xFFFFFFFFFFFFFFFF),
            ((0xFFFFFFFFCAFEFFFF, ), 0xFFFFFFFFCAFEFFFF),
            # will be a big one
            ((0xFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF, ), 0xFFFFFFFFFFFFFFFF),
            ((0xFFFFFFFFFFFFFFFFFFFFFFFCAFEFFFF, ), 0xFFFFFFFFCAFEFFFF),
        ),
        code='''
        static unsigned long long wrap_PyArg_ParseTuple(PyObject* argTuple, PyObject* expected) {
            unsigned long long out = 0xDEADBEEFDEADBEEF;
            Py_INCREF(argTuple);
            if (PyArg_ParseTuple(argTuple, "K", &out) == 0) {
                return -1;
            }
            return out;
        }
        ''',
        resultspec="K",
        argspec="OO",
        arguments=["PyObject* argTuple", "PyObject* expected"],
        callfunction="wrap_PyArg_ParseTuple",
        cmpfunc=unhandled_error_compare
    )

    test_parseargs_S = CPyExtFunction(
        lambda args: _reference_typecheck(args, bytes),
        lambda: (
            ((b'', ), ),
            ((b'helloworld', ), ),
            ((bytearray(b''), ), ),
            ((bytearray(b'helloworld'), ), ),
            (('helloworld', ), ),
        ),
        code='''
        static PyObject * wrap_PyArg_ParseTuple(PyObject* argTuple) {
            PyObject *out = NULL;
            Py_INCREF(argTuple);
            if (PyArg_ParseTuple(argTuple, "S", &out) == 0) {
                return NULL;
            }
            Py_DECREF(argTuple);
            Py_XINCREF(out);
            return out;
        }
        ''',
        resultspec="O",
        argspec="O",
        arguments=["PyObject* argTuple"],
        callfunction="wrap_PyArg_ParseTuple",
        cmpfunc=unhandled_error_compare
    )

    test_parseargs_Y = CPyExtFunction(
        lambda args: _reference_typecheck(args, bytearray),
        lambda: (
            ((b'', ), ),
            ((b'helloworld', ), ),
            ((bytearray(b''), ), ),
            ((bytearray(b'helloworld'), ), ),
            (('helloworld', ), ),
        ),
        code='''
        static PyObject * wrap_PyArg_ParseTuple(PyObject* argTuple) {
            PyObject *out = NULL;
            Py_INCREF(argTuple);
            if (PyArg_ParseTuple(argTuple, "Y", &out) == 0) {
                return NULL;
            }
            Py_DECREF(argTuple);
            Py_XINCREF(out);
            return out;
        }
        ''',
        resultspec="O",
        argspec="O",
        arguments=["PyObject* argTuple"],
        callfunction="wrap_PyArg_ParseTuple",
        cmpfunc=unhandled_error_compare
    )

    test_parseargs_U = CPyExtFunction(
        lambda args: _reference_typecheck(args, str),
        lambda: (
            ((b'', ), ),
            ((b'helloworld', ), ),
            ((bytearray(b''), ), ),
            ((bytearray(b'helloworld'), ), ),
            (('helloworld', ), ),
        ),
        code='''
        static PyObject * wrap_PyArg_ParseTuple(PyObject* argTuple) {
            PyObject *out = NULL;
            Py_INCREF(argTuple);
            if (PyArg_ParseTuple(argTuple, "U", &out) == 0) {
                return NULL;
            }
            Py_DECREF(argTuple);
            Py_XINCREF(out);
            return out;
        }
        ''',
        resultspec="O",
        argspec="O",
        arguments=["PyObject* argTuple"],
        callfunction="wrap_PyArg_ParseTuple",
        cmpfunc=unhandled_error_compare
    )

    test_parseargs_y_lower = CPyExtFunction(
        lambda args: args[0][0].decode(),
        lambda: (
            ((b'', ), ),
            ((b'helloworld', ), ),
        ),
        code='''
        static const char * wrap_PyArg_ParseTuple(PyObject* argTuple) {
            const char *out = NULL;
            Py_INCREF(argTuple);
            if (PyArg_ParseTuple(argTuple, "y", &out) == 0) {
                return NULL;
            }
            Py_DECREF(argTuple);
            return out;
        }
        ''',
        resultspec="s",
        argspec="O",
        arguments=["PyObject* argTuple"],
        callfunction="wrap_PyArg_ParseTuple",
        cmpfunc=unhandled_error_compare
    )

    test_parseargs_y_hash = CPyExtFunction(
        lambda args: (args[0][0].decode(), len(args[0][0])),
        lambda: (
            ((b'', ), ),
            ((b'helloworld', ), ),
        ),
        code='''
        static PyObject * wrap_PyArg_ParseTuple(PyObject* argTuple) {
            const char *out = NULL;
            Py_ssize_t cnt = 0;
            Py_INCREF(argTuple);
            if (PyArg_ParseTuple(argTuple, "y#", &out, &cnt) == 0) {
                return NULL;
            }
            Py_DECREF(argTuple);
            return Py_BuildValue("si", out, cnt);
        }
        ''',
        resultspec="O",
        argspec="O",
        arguments=["PyObject* argTuple"],
        callfunction="wrap_PyArg_ParseTuple",
        cmpfunc=unhandled_error_compare
    )

    test_parseargs_y_star = CPyExtFunction(
        lambda args: (args[0][0].decode(), len(args[0][0])),
        lambda: (
            ((b'', ), ),
            ((b'helloworld', ), ),
        ),
        code='''
        static PyObject * wrap_PyArg_ParseTuple(PyObject* argTuple) {
            Py_buffer view;
            Py_INCREF(argTuple);
            if (PyArg_ParseTuple(argTuple, "y*", &view) == 0) {
                return NULL;
            }
            Py_DECREF(argTuple);
            PyObject *result = Py_BuildValue("si", (char*)view.buf, view.len);
            PyBuffer_Release(&view);
            return result;
        }
        ''',
        resultspec="O",
        argspec="O",
        arguments=["PyObject* argTuple"],
        callfunction="wrap_PyArg_ParseTuple",
        cmpfunc=unhandled_error_compare
    )

    test_parseargs_es = CPyExtFunction(
        lambda args: args[0][0].decode() if isinstance(args[0][0], bytes) else args[0][0],
        lambda: (
            (('helloworld', ), ),
        ),
        code='''
        static PyObject * wrap_PyArg_ParseTuple(PyObject* argTuple) {
            char * out;
            Py_INCREF(argTuple);
            if (PyArg_ParseTuple(argTuple, "es", "UTF-8", &out) == 0) {
                return NULL;
            }
            Py_DECREF(argTuple);
            PyObject *result = PyUnicode_FromString(out);
            PyMem_Free(out);
            return result;
        }
        ''',
        resultspec="O",
        argspec="O",
        arguments=["PyObject* argTuple"],
        callfunction="wrap_PyArg_ParseTuple",
        cmpfunc=unhandled_error_compare
    )

    test_parseargs_et = CPyExtFunction(
        lambda args: args[0][0].decode() if isinstance(args[0][0], bytes) else args[0][0],
        lambda: (
            (('helloworld', ), ),
        ),
        code='''
        static PyObject * wrap_PyArg_ParseTuple(PyObject* argTuple) {
            char * out;
            Py_INCREF(argTuple);
            if (PyArg_ParseTuple(argTuple, "es", "UTF-8", &out) == 0) {
                return NULL;
            }
            Py_DECREF(argTuple);
            PyObject *result = PyUnicode_FromString(out);
            PyMem_Free(out);
            return result;
        }
        ''',
        resultspec="O",
        argspec="O",
        arguments=["PyObject* argTuple"],
        callfunction="wrap_PyArg_ParseTuple",
        cmpfunc=unhandled_error_compare
    )

    test_parseargs_valist = CPyExtFunction(
        lambda args: args[0],
        lambda: (
            ((b'hello', 1, b'world'),),
        ),
        code='''
        static int do_call(PyObject* argTuple, char *format, ...) {
            int result = 0;
            va_list va;
            va_start(va, format);
            result = PyArg_VaParse(argTuple, "OiO", va);
            va_end(va);
            return result;
        }

        static PyObject* wrap_PyArg_VaParse(PyObject* argTuple) {
            PyObject* out0 = NULL;
            int out1 = 0;
            PyObject* out2 = NULL;
            PyObject* res = NULL;
            Py_INCREF(argTuple);
            if (do_call(argTuple, "OiO", &out0, &out1, &out2) == 0) {
                return NULL;
            }
            Py_DECREF(argTuple);
            Py_XINCREF(out0);
            Py_XINCREF(out2);
            res = PyTuple_Pack(3, out0, PyLong_FromLong(out1), out2);
            Py_XINCREF(res);
            return res;
        }
        ''',
        resultspec="O",
        argspec="O",
        arguments=["PyObject* argTuple"],
        callfunction="wrap_PyArg_VaParse",
        cmpfunc=unhandled_error_compare
    )

    test_parseargs_and_kwd_valist = CPyExtFunction(
        lambda args: args[0],
        lambda: (
            ((b'hello', 1, b'world'),),
        ),
        code='''
        static int do_call(PyObject* argTuple, char *format, ...) {
            int result = 0;
            char *kwnames[] = { "a", "b", "c", NULL };
            va_list va;
            va_start(va, format);
            result = PyArg_VaParseTupleAndKeywords(argTuple, PyDict_New(), "OiO", kwnames, va);
            va_end(va);
            return result;
        }

        static PyObject* wrap_PyArg_VaParseTupleAndKeywords_SizeT(PyObject* argTuple) {
            PyObject* out0 = NULL;
            int out1 = 0;
            PyObject* out2 = NULL;
            PyObject* res = NULL;
            Py_INCREF(argTuple);
            if (do_call(argTuple, "OiO", &out0, &out1, &out2) == 0) {
                return NULL;
            }
            Py_DECREF(argTuple);
            Py_XINCREF(out0);
            Py_XINCREF(out2);
            res = PyTuple_Pack(3, out0, PyLong_FromLong(out1), out2);
            Py_XINCREF(res);
            return res;
        }
        ''',
        resultspec="O",
        argspec="O",
        arguments=["PyObject* argTuple"],
        callfunction="wrap_PyArg_VaParseTupleAndKeywords_SizeT",
        cmpfunc=unhandled_error_compare
    )

    # ensure that allocations are aligned to 16 bytes
    test_aligned_malloc = CPyExtFunction(
        lambda args: 0,
        lambda: ((1,), (16,), (64,), (63,), (15,), (31,), (7,), (9,)),
        code='''
        static int wrap_PyObject_Malloc(int size) {
            return ((int) (long) PyObject_Malloc(size)) & 0xf;
        }
        ''',
        resultspec="i",
        argspec="i",
        arguments=["int size"],
        callfunction="wrap_PyObject_Malloc",
        cmpfunc=unhandled_error_compare
    )

    test_Py_BuildValue = CPyExtFunction(
        lambda args: {args[1]: args[2], args[3]: args[4]},
        lambda: (
            ('{O:O, O:O}', 'hello', 'world', 'foo', 'bar'),
        ),
        resultspec="O",
        argspec="sOOOO",
        arguments=["char* fmt", "PyObject* a", "PyObject* b", "PyObject* c", "PyObject* d"],
        cmpfunc=unhandled_error_compare
    )

    # For some reason, some 'PyModule_*' functions are in 'modsupport.h'
    test_PyModule_SetDocString = CPyExtFunction(
        lambda args: args[1],
        lambda: (
            (ModuleType("hello"), "hello doc"),
            (SubModuleType("subhello"), "subhello doc"),
        ),
        code='''
        static PyObject* wrap_PyModule_SetDocString(PyObject* object, char* doc) {
            PyModule_SetDocString(object, (const char *)doc);
            return PyObject_GetAttrString(object, "__doc__");
        }
        ''',
        resultspec="O",
        argspec='Os',
        callfunction="wrap_PyModule_SetDocString",
        arguments=["PyObject* object", "char* doc"],
    )

    def compare_new_object(x, y):
        return isinstance(x, ModuleType) and x.__name__ == y[0]

    test_PyModule_NewObject = CPyExtFunction(
        lambda args: args,
        lambda: (
            ('testmodule',),
        ),
        resultspec="O",
        argspec="O",
        arguments=["PyObject* a"],
        cmpfunc=compare_new_object
    )

    test_PyArg_ValidateKeywordArguments = CPyExtFunction(
        _reference_validate_keywords,
        lambda: (
            ({'a': 1, 'b': 2},),
            ({'a': 1, 1: 2},),
            ("not-dict",),
        ),
        resultspec='i',
        argspec='O',
        arguments=["PyObject* kwds"],
        cmpfunc=unhandled_error_compare,
    )
