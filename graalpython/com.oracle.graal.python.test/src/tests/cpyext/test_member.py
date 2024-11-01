# Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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

from . import CPyExtType, assert_raises


def _reference_classmethod(args):
    if isinstance(args[0], type(list.append)):
        return classmethod(args[0])()
    raise TypeError

TYPES = {
    "SHORT",
    "INT",
    "LONG",
    "FLOAT",
    "DOUBLE",
    "STRING",
    "OBJECT",
    "CHAR",
    "BYTE",
    "UBYTE",
    "USHORT",
    "UINT",
    "ULONG",
    "STRING_INPLACE",
    "BOOL",
    "OBJECT_EX",
    "LONGLONG",
    "ULONGLONG",
    "PYSSIZET",
    # "NONE",
}

def _get_c_type(ptype):
    if ptype == "STRING_INPLACE" or ptype == "STRING":
        return "char *"
    elif ptype == "OBJECT" or ptype == "OBJECT_EX":
        return "PyObject *"
    elif ptype == "LONGLONG":
        return "long long"
    elif ptype == "ULONGLONG":
        return "unsigned long long"
    elif ptype[0] == "U":
        return "unsigned " + ptype[1:].lower()
    return ptype.lower()

class TestMethod(unittest.TestCase):

    def test_member(self):
        TestMember = CPyExtType(
            "TestMember",
            '''
            #include <string.h>
            #include <limits.h>

            PyObject* set_string(PyObject *self, PyObject *arg) {
                TestMemberObject *tmo = (TestMemberObject *)self;
                const char *utf8 = PyUnicode_AsUTF8(arg);
                if (utf8 == NULL)
                    return NULL;
                tmo->member_string = strdup(utf8);
                Py_INCREF(Py_None);
                return Py_None;
            }

            PyObject* get_min_values(PyObject *self) {
                PyObject *result = PyTuple_New(9);
                PyTuple_SetItem(result, 0, PyLong_FromSsize_t(CHAR_MIN));
                PyTuple_SetItem(result, 1, PyLong_FromLong(0));
                PyTuple_SetItem(result, 2, PyLong_FromSsize_t(SHRT_MIN));
                PyTuple_SetItem(result, 3, PyLong_FromLong(0));
                PyTuple_SetItem(result, 4, PyLong_FromSsize_t(INT_MIN));
                PyTuple_SetItem(result, 5, PyLong_FromLong(0));
                PyTuple_SetItem(result, 6, PyLong_FromSsize_t(LONG_MIN));
                PyTuple_SetItem(result, 7, PyLong_FromLong(0));
                PyTuple_SetItem(result, 8, PyLong_FromSsize_t(PY_SSIZE_T_MIN));
                return result;
            }

            PyObject* get_max_values(PyObject *self) {
                PyObject *result = PyTuple_New(9);
                PyTuple_SetItem(result, 0, PyLong_FromSize_t(CHAR_MAX));
                PyTuple_SetItem(result, 1, PyLong_FromSize_t(UCHAR_MAX));
                PyTuple_SetItem(result, 2, PyLong_FromSize_t(SHRT_MAX));
                PyTuple_SetItem(result, 3, PyLong_FromSize_t(USHRT_MAX));
                PyTuple_SetItem(result, 4, PyLong_FromSize_t(INT_MAX));
                PyTuple_SetItem(result, 5, PyLong_FromSize_t(UINT_MAX));
                PyTuple_SetItem(result, 6, PyLong_FromSize_t(LONG_MAX));
                PyTuple_SetItem(result, 7, PyLong_FromSize_t(ULONG_MAX));
                PyTuple_SetItem(result, 8, PyLong_FromSize_t(PY_SSIZE_T_MAX));
                return result;
            }
            ''',
            cmembers="""
            PyObject *member_o;
            PyObject *member_o_ex;
            short member_short;
            int member_int;
            long member_long;
            float member_float;
            double member_double;
            char *member_string;
            char member_char;
            char member_byte;
            unsigned char member_ubyte;
            unsigned short member_ushort;
            unsigned int member_uint;
            unsigned long member_ulong;
            char member_bool;
            long long member_longlong;
            unsigned long long member_ulonglong;
            Py_ssize_t member_pyssizet;
            """,
            tp_members="""
            // instance methods
            {"member_o", T_OBJECT, offsetof(TestMemberObject, member_o), 0, "object member"},
            {"member_o_ex", T_OBJECT_EX, offsetof(TestMemberObject, member_o_ex), 0, "object ex member"},
            {"member_short", T_SHORT, offsetof(TestMemberObject, member_short), 0, "object ex member"},
            {"member_int", T_INT, offsetof(TestMemberObject, member_int), 0, "int member"},
            {"member_long", T_LONG, offsetof(TestMemberObject, member_long), 0, "long member"},
            {"member_float", T_FLOAT, offsetof(TestMemberObject, member_float), 0, "float member"},
            {"member_double", T_DOUBLE, offsetof(TestMemberObject, member_double), 0, "double member"},
            {"member_string", T_STRING, offsetof(TestMemberObject, member_string), 0, "string member"},
            {"member_char", T_CHAR, offsetof(TestMemberObject, member_char), 0, "char member"},
            {"member_byte", T_BYTE, offsetof(TestMemberObject, member_byte), 0, "byte member"},
            {"member_ubyte", T_UBYTE, offsetof(TestMemberObject, member_ubyte), 0, "ubyte member"},
            {"member_ushort", T_USHORT, offsetof(TestMemberObject, member_ushort), 0, "ushort member"},
            {"member_uint", T_UINT, offsetof(TestMemberObject, member_uint), 0, "uint member"},
            {"member_ulong", T_ULONG, offsetof(TestMemberObject, member_ulong), 0, "ulong member"},
            {"member_bool", T_BOOL, offsetof(TestMemberObject, member_bool), 0, "bool member"},
            {"member_longlong", T_LONGLONG, offsetof(TestMemberObject, member_longlong), 0, "longlong member"},
            {"member_ulonglong", T_ULONGLONG, offsetof(TestMemberObject, member_ulonglong), 0, "ulonglong member"},
            {"member_pyssizet", T_PYSSIZET, offsetof(TestMemberObject, member_pyssizet), 0, "pyssizet member"}
            """,
            tp_methods='''
            {"set_string", (PyCFunction)set_string, METH_O, ""},
            {"get_min_values", (PyCFunction)get_min_values, METH_NOARGS, ""},
            {"get_max_values", (PyCFunction)get_max_values, METH_NOARGS, ""}
            ''',
        )

        obj = TestMember()

        dummy = object()

        # T_OBJECT
        assert obj.member_o is None, f"member is {obj.member_o}"
        del obj.member_o
        assert obj.member_o is None, f"member is {obj.member_o}"
        obj.member_o = dummy
        assert obj.member_o is dummy, f"member is {obj.member_o}"
        del obj.member_o
        assert obj.member_o is None, f"member is {obj.member_o}"

        # T_OBJECT_EX
        assert_raises(AttributeError, lambda x: x.member_o_ex, obj)
        assert_raises(AttributeError, delattr, obj, "member_o_ex")
        obj.member_o_ex = dummy
        assert obj.member_o_ex is dummy
        del obj.member_o_ex
        assert_raises(AttributeError, lambda x: x.member_o_ex, obj)
        assert_raises(AttributeError, delattr, obj, "member_o_ex")

        # T_BOOL
        assert obj.member_bool is False
        assert_raises(TypeError, setattr, obj, "member_bool", "hello")
        obj.member_bool = True
        assert obj.member_bool is True
        obj.member_bool = False
        assert obj.member_bool is False
        assert_raises(TypeError, delattr, obj, "member_bool")

        # TODO(fa): We ignore warnings for now since GraalPy doesn't issue them.
        import warnings
        warnings.simplefilter("ignore")

        # char, uchar, short, ushort, int, uint, long, ulong, Py_ssize_t
        max_values = obj.get_max_values()
        min_values = obj.get_min_values()

        # all int-like members smaller than C long
        for i, m in enumerate(("member_byte", "member_ubyte", "member_short", "member_ushort", "member_int",
                               "member_uint")):
            assert type(getattr(obj, m)) is int
            assert getattr(obj, m) == 0
            assert_raises(TypeError, delattr, obj, m)
            setattr(obj, m, max_values[i])
            assert getattr(obj, m) == max_values[i], "member %s; was: %r" % (m, getattr(obj, m))
            # max_value + 1 will be truncated but must not throw an error
            setattr(obj, m, max_values[i] + 1)
            val = getattr(obj, m)
            assert val != max_values[i], "was: %r" % getattr(obj, m)
            setattr(obj, m, min_values[i])
            assert getattr(obj, m) == min_values[i], "member %s; was: %r" % (m, getattr(obj, m))
            # min_value - 1 will be truncated but must not throw an error
            setattr(obj, m, min_values[i] - 1)
            val = getattr(obj, m)
            assert val != min_values[i], "was: %r" % getattr(obj, m)
            assert_raises(TypeError, setattr, obj, m, "hello")
            assert_raises(OverflowError, setattr, obj, m, int(-1e40))
            assert_raises(OverflowError, setattr, obj, m, int(1e40))

        # T_LONG, T_ULONG, T_PYSSIZET
        max_values = (0x7FFFFFFFFFFFFFFF, 0xFFFFFFFFFFFFFFFF, 0x7FFFFFFFFFFFFFFF, 0x7FFFFFFFFFFFFFFF, 0xFFFFFFFFFFFFFFFF)
        err_values = (-1, 0xFFFFFFFFFFFFFFFF, -1, -1, 0xFFFFFFFFFFFFFFFF)
        for i, m in enumerate(("member_long", "member_ulong", "member_pyssizet", "member_longlong", "member_ulonglong")):
            assert type(getattr(obj, m)) is int
            assert getattr(obj, m) == 0
            assert_raises(TypeError, delattr, obj, m)
            setattr(obj, m, max_values[i])
            assert getattr(obj, m) == max_values[i]
            assert_raises(OverflowError, setattr, obj, m, max_values[i] + 1)
            val = getattr(obj, m)
            assert val == err_values[i], "member: %s ;; was: %r" % (m, val)

        # T_FLOAT, T_DOUBLE
        for i, m in enumerate(("member_float", "member_double")):
            assert type(getattr(obj, m)) is float
            assert getattr(obj, m) == 0.0
            assert_raises(TypeError, delattr, obj, m)
            setattr(obj, m, 1.1)
            assert 1.0 <= getattr(obj, m) <= 1.2

        # T_FLOAT and T_DOUBLE behave a bit different when writing invalid values
        obj.member_float = 1.0
        obj.member_double = 1.0
        assert_raises(TypeError, setattr, obj, "member_float", "hello")
        assert_raises(TypeError, setattr, obj, "member_double", "hello")
        # T_FLOAT will just keep its previous value
        assert obj.member_float >= 1.0, "was: %r" % obj.member_float
        # T_DOUBLE will be set to '(double) -1'
        assert obj.member_double == -1.0, "was: %r" % obj.member_double

        # T_STRING
        assert obj.member_string is None
        assert_raises(TypeError, delattr, obj, "member_string")
        assert_raises(TypeError, setattr, obj, "member_string", "hello")
        obj.set_string("hello")
        assert type(obj.member_string) is str
        assert obj.member_string == "hello"

        # T_CHAR
        assert type(obj.member_char) is str
        assert obj.member_char == "\x00", "was: %r" % obj.member_char
        obj.member_char = "x"
        assert obj.member_char == "x"
        assert_raises(TypeError, delattr, obj, "member_char")
        assert_raises(TypeError, setattr, obj, "member_char", 1)
        assert_raises(TypeError, setattr, obj, "member_char", "xyz")
        assert obj.member_char == "x"

        warnings.resetwarnings()
