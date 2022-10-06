# Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
from . import CPyExtType

__dir__ = __file__.rpartition("/")[0]


def assert_raises(err, fn, *args, **kwargs):
    raised = False
    try:
        fn(*args, **kwargs)
    except err:
        raised = True
    assert raised


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

class TestMethod(object):

    def test_member(self):
        TestMember = CPyExtType(
            "TestMember",
            '''
            #include <string.h>

            PyObject* setString(PyObject *self, PyObject *arg) {
                TestMemberObject *tmo = (TestMemberObject *)self;
                const char *utf8 = PyUnicode_AsUTF8(arg);
                if (utf8 == NULL)
                    return NULL;
                tmo->member_string = strdup(utf8);
                Py_INCREF(Py_None);
                return Py_None;
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
            tp_methods='{"setString", (PyCFunction)setString, METH_O, ""}',
        )

        obj = TestMember()

        dummy = object()

        # T_OBJECT
        assert obj.member_o is None
        del obj.member_o
        assert obj.member_o is None
        obj.member_o = dummy
        assert obj.member_o is dummy
        del obj.member_o
        assert obj.member_o is None

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

        # all int-like members smaller than C long
        max_values = (0x7F, 0xFF, 0x7FFF, 0xFFFF, 0x7FFFFFFF, 0xFFFFFFFF)
        overflow_val = (-(0x7F+1), 0, -(0x7FFF+1), 0, -(0x7FFFFFFF+1), 0)
        for i, m in enumerate(("member_byte", "member_ubyte", "member_short", "member_ushort", "member_int",
                               "member_uint")):
            assert type(getattr(obj, m)) is int
            assert getattr(obj, m) == 0
            assert_raises(TypeError, delattr, obj, m)
            setattr(obj, m, max_values[i])
            assert getattr(obj, m) == max_values[i], "member %s; was: %r" % (m, getattr(obj, m))
            # max_value + 1 will be truncated to -1
            setattr(obj, m, max_values[i] + 1)
            assert getattr(obj, m) == overflow_val[i], "was: %r" % getattr(obj, m)
            assert_raises(TypeError, setattr, obj, m, "hello")
            assert getattr(obj, m) == overflow_val[i], "was: %r" % getattr(obj, m)

        # T_LONG, T_ULONG, T_PYSSIZET
        max_values = (0x7FFFFFFFFFFFFFFF, 0xFFFFFFFFFFFFFFFF, 0x7FFFFFFFFFFFFFFF)
        err_values = (-1, -1 & 0xFFFFFFFFFFFFFFFF, -1)
        for i, m in enumerate(("member_long", "member_ulong", "member_pyssizet")):
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

        assert obj.member_string is None
        assert_raises(TypeError, delattr, obj, "member_string")
        assert_raises(TypeError, setattr, obj, "member_string", "hello")
        obj.setString("hello")
        assert type(obj.member_string) is str
        assert obj.member_string == "hello"

        warnings.resetwarnings()

# class TestPyMethod(CPyExtTestCase):
#     def compile_module(self, name):
#         type(self).mro()[1].__dict__["test_%s" % name].create_module(name)
#         super().compile_module(name)
#
#     test_PyMethod_New = CPyExtFunction(
#         lambda args: types.MethodType(*args),
#         lambda: (
#             (list.append, 6),
#         ),
#         resultspec="O",
#         argspec='OO',
#         arguments=["PyObject* func", "PyObject* self"],
#         cmpfunc=unhandled_error_compare
#     )
