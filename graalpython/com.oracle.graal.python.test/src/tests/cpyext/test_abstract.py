# Copyright (c) 2018, Oracle and/or its affiliates.
#
# The Universal Permissive License (UPL), Version 1.0
#
# Subject to the condition set forth below, permission is hereby granted to any
# person obtaining a copy of this software, associated documentation and/or data
# (collectively the "Software"), free of charge and under any and all copyright
# rights in the Software, and any and all patent rights owned or freely
# licensable by each licensor hereunder covering either (i) the unmodified
# Software as contributed to or provided by such licensor, or (ii) the Larger
# Works (as defined below), to deal in both
#
# (a) the Software, and
# (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
#     one is included with the Software (each a "Larger Work" to which the
#     Software is contributed by such licensors),
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
from . import CPyExtTestCase, CPyExtFunction, CPyExtFunctionOutVars, unhandled_error_compare, GRAALPYTHON
__dir__ = __file__.rpartition("/")[0]


def _safe_check(v, type_check):
    try:
        return type_check(v)
    except:
        return False


def _reference_checknumber(args):
    v = args[0]
    return _safe_check(v, lambda x: isinstance(int(x), int)) or _safe_check(v, lambda x: isinstance(float(x), float))


def _reference_index(args):
    v = args[0]
    if not hasattr(v, "__index__"):
        raise TypeError
    result = v.__index__()
    if not isinstance(result, int):
        raise TypeError
    return result


def _reference_asssize_t(args):
    v = args[0]
    err = args[1]
    result = -1
    try:
        return _reference_index((v,))
    except BaseException:
        if sys.version_info.minor >= 6:
            raise SystemError
        else:
            return -1


class NoNumber():
    pass


class DummyIntable():
    def __int__(self):
        return 0xCAFE


class DummyIntSubclass(int):
    def __int__(self):
        return 0xBABE


class DummyFloatable():
    def __float__(self):
        return 3.14159


class DummyFloatSubclass(float):
    def __float__(self):
        return 2.71828


def _default_bin_arith_args():
    return (
        (0,0),
        (0,-1),
        (3,2),
        (10,5),
        (29.3,4.7),
        (0.3,-1.5),
        (False,1),
        (False,1.3),
        (True,1),
        (True,1.3),
        ("hello, ", "world"),
        ("hello, ", 3),
        ((1,2,3), (4,5,6)),
        ((1,2,3), 2),
        (0x7fffffff,0x7fffffff),
        (0xffffffffffffffffffffffffffffffff,-1),
        (DummyIntable(),0xBABE),
        (0xBABE, DummyIntable()),
        (DummyIntSubclass(),0xCAFE),
        (0xCAFE, DummyIntSubclass()),
        (NoNumber(),1),
        (4,NoNumber()),
    )


class TestPyNumber(CPyExtTestCase):
    def compile_module(self, name):
        type(self).mro()[1].__dict__["test_%s" % name].create_module(name)
        super(TestPyNumber, self).compile_module(name)


    test_PyNumber_Check = CPyExtFunction(
        _reference_checknumber,
        lambda: (
            (0,),
            (-1,),
            (0.1,),
            (-1.3,),
            (False,),
            (True,),
            ("hello",),
            ((1,2,3),),
            (0x7fffffff,),
            (0xffffffffffffffffffffffffffffffff,),
            (DummyIntable(),),
            (DummyIntSubclass(),),
            (NoNumber(),),
            (DummyFloatable(),),
            (DummyFloatSubclass(),),
        ),
        resultspec="i",
        argspec='O',
        arguments=["PyObject* v"],
        cmpfunc=unhandled_error_compare
    )

    test_PyNumber_Add = CPyExtFunction(
        lambda args: args[0] + args[1],
        lambda: (
            (0,0),
            (0,-1),
            (0.1,0.0),
            (0.3,-1.5),
            (False,1),
            (False,1.3),
            (True,1),
            (True,1.3),
            ("hello, ", "world"),
            ((1,2,3), (4,5,6)),
            (0x7fffffff,0x7fffffff),
            (0xffffffffffffffffffffffffffffffff,-1),
            (DummyIntable(),0xCAFE),
            (DummyIntSubclass(),0xBABE),
            (NoNumber(),1),
        ),
        resultspec="O",
        argspec='OO',
        arguments=["PyObject* v", "PyObject* w"],
        cmpfunc=unhandled_error_compare
    )

    test_PyNumber_Subtract = CPyExtFunction(
        lambda args: args[0] - args[1],
        lambda: (
            (0,0),
            (0,-1),
            (0.1,0.0),
            (0.3,-1.5),
            (False,1),
            (False,1.3),
            (True,1),
            (True,1.3),
            ("hello, ", "world"),
            ((1,2,3), (4,5,6)),
            (0x7fffffff,0x7fffffff),
            (0xffffffffffffffffffffffffffffffff,-1),
            (DummyIntable(),0xCAFE),
            (DummyIntSubclass(),0xBABE),
            (NoNumber(),1),
        ),
        resultspec="O",
        argspec='OO',
        arguments=["PyObject* v", "PyObject* w"],
        cmpfunc=unhandled_error_compare
    )

    test_PyNumber_Multiply = CPyExtFunction(
        lambda args: args[0] * args[1],
        lambda: (
            (0,0),
            (0,-1),
            (0.1,0.0),
            (0.3,-1.5),
            (False,1),
            (False,1.3),
            (True,1),
            (True,1.3),
            ("hello, ", "world"),
            ("hello, ", 3),
            ("hello, ", 0),
            ((1,2,3), (4,5,6)),
            ((1,2,3), 2),
            ((1,2,3), 0),
            (0x7fffffff,0x7fffffff),
            (0xffffffffffffffffffffffffffffffff,-1),
            (DummyIntable(),2.3),
            (DummyIntSubclass(),2.3),
            (NoNumber(),2),
        ),
        resultspec="O",
        argspec='OO',
        arguments=["PyObject* v", "PyObject* w"],
        cmpfunc=unhandled_error_compare
    )

    test_PyNumber_TrueDivide = CPyExtFunction(
        lambda args: args[0] / args[1],
        _default_bin_arith_args,
        resultspec="O",
        argspec='OO',
        arguments=["PyObject* v", "PyObject* w"],
        cmpfunc=unhandled_error_compare
    )

    test_PyNumber_FloorDivide = CPyExtFunction(
        lambda args: args[0] // args[1],
        _default_bin_arith_args,
        resultspec="O",
        argspec='OO',
        arguments=["PyObject* v", "PyObject* w"],
        cmpfunc=unhandled_error_compare
    )

    test_PyNumber_Remainder = CPyExtFunction(
        lambda args: args[0] % args[1],
        _default_bin_arith_args,
        resultspec="O",
        argspec='OO',
        arguments=["PyObject* v", "PyObject* w"],
        cmpfunc=unhandled_error_compare
    )

    test_PyNumber_Lshift = CPyExtFunction(
        lambda args: args[0] << args[1],
        _default_bin_arith_args,
        resultspec="O",
        argspec='OO',
        arguments=["PyObject* v", "PyObject* w"],
        cmpfunc=unhandled_error_compare
    )

    test_PyNumber_Rshift = CPyExtFunction(
        lambda args: args[0] >> args[1],
        _default_bin_arith_args,
        resultspec="O",
        argspec='OO',
        arguments=["PyObject* v", "PyObject* w"],
        cmpfunc=unhandled_error_compare
    )

    test_PyNumber_Or = CPyExtFunction(
        lambda args: args[0] | args[1],
        _default_bin_arith_args,
        resultspec="O",
        argspec='OO',
        arguments=["PyObject* v", "PyObject* w"],
        cmpfunc=unhandled_error_compare
    )

    test_PyNumber_And = CPyExtFunction(
        lambda args: args[0] & args[1],
        _default_bin_arith_args,
        resultspec="O",
        argspec='OO',
        arguments=["PyObject* v", "PyObject* w"],
        cmpfunc=unhandled_error_compare
    )

    test_PyNumber_Xor = CPyExtFunction(
        lambda args: args[0] ^ args[1],
        _default_bin_arith_args,
        resultspec="O",
        argspec='OO',
        arguments=["PyObject* v", "PyObject* w"],
        cmpfunc=unhandled_error_compare
    )

    test_PyNumber_Positive = CPyExtFunction(
        lambda args: +args[0],
        lambda: (
            (0,),
            (1,),
            (-1,),
            (0x7FFFFFFFFFFFFFF,),
            (DummyIntable(),),
            (DummyIntSubclass(),),
            (NoNumber(),),
        ),
        resultspec="O",
        argspec='O',
        arguments=["PyObject* v"],
        cmpfunc=unhandled_error_compare
    )

    test_PyNumber_Negative = CPyExtFunction(
        lambda args: -args[0],
        lambda: (
            (0,),
            (1,),
            (-1,),
            (0x7FFFFFFFFFFFFFF,),
            (DummyIntable(),),
            (DummyIntSubclass(),),
            (NoNumber(),),
        ),
        resultspec="O",
        argspec='O',
        arguments=["PyObject* v"],
        cmpfunc=unhandled_error_compare
    )

    test_PyNumber_Index = CPyExtFunction(
        _reference_index,
        lambda: (
            (0,),
            (1,),
            (-1,),
            (1.0,),
            (0x7FFFFFFF,),
            (0x7FFFFFFFFFFFFFFF,),
            (DummyIntable(),),
            (DummyIntSubclass(),),
            (NoNumber(),),
        ),
        resultspec="O",
        argspec='O',
        arguments=["PyObject* v"],
        cmpfunc=unhandled_error_compare
    )

    test_PyNumber_AsSsize_t = CPyExtFunction(
        _reference_asssize_t,
        lambda: (
            (0,OverflowError),
            (1,ValueError),
            (1,OverflowError),
            (-1,OverflowError),
            (1.0,OverflowError),
            (0x7FFFFFFF,OverflowError),
            (0x7FFFFFFFFFFFFFFF,OverflowError),
            (DummyIntable(),OverflowError),
            (DummyIntSubclass(),OverflowError),
            (NoNumber(),OverflowError),
            (NoNumber(),ValueError),
        ),
        resultspec="n",
        argspec='OO',
        arguments=["PyObject* v", "PyObject* err"],
        cmpfunc=unhandled_error_compare
    )

    test_PyNumber_Long = CPyExtFunction(
        lambda args: int(args[0]),
        lambda: (
            (0,),
            (1,),
            (-1,),
            (1.0,),
            (0x7FFFFFFF,),
            (0x7FFFFFFFFFFFFFFF,),
            (DummyIntable(),),
            (DummyIntSubclass(),),
            (NoNumber(),),
        ),
        resultspec="O",
        argspec='O',
        arguments=["PyObject* v"],
        cmpfunc=unhandled_error_compare
    )

    test_PyNumber_Float = CPyExtFunction(
        lambda args: float(args[0]),
        lambda: (
            (0,),
            (1,),
            (-1,),
            (1.0,),
            (0x7FFFFFFF,),
            (0x7FFFFFFFFFFFFFFF,),
            (DummyIntable(),),
            (DummyIntSubclass(),),
            (NoNumber(),),
        ),
        resultspec="O",
        argspec='O',
        arguments=["PyObject* v"],
        cmpfunc=unhandled_error_compare
    )

class TestPySequence(CPyExtTestCase):
    def compile_module(self, name):
        type(self).mro()[1].__dict__["test_%s" % name].create_module(name)
        super(TestPySequence, self).compile_module(name)

    test_PySequence_Fast_GET_SIZE = CPyExtFunction(
        lambda args: len(args[0]),
        lambda: (
            (tuple(),),
            (list(),),
            ((1,2,3),),
            (("a", "b"),),
        ),
        resultspec="n",
        argspec='O',
        arguments=["PyObject* tuple"],
    )

    test_PySequence_Fast_GET_ITEM = CPyExtFunction(
        lambda args: args[0][args[1]],
        lambda: (
            ((1,2,3),0),
            ((1,2,3),1),
            ((1,2,3),2),
            (['a','b','c'],0),
            (['a','b','c'],1),
            (['a','b','c'],2),
        ),
        resultspec="O",
        argspec='On',
        arguments=["PyObject* sequence", "Py_ssize_t idx"],
    )

    test_PySequence_Fast_GET_SIZE = CPyExtFunction(
        lambda args: len(args[0]),
        lambda: (
            (tuple(),),
            ((1,2,3),),
            ((None,),),
            ([],),
            (['a','b','c'],),
            ([None],),
        ),
        resultspec="n",
        argspec='O',
        arguments=["PyObject* sequence"],
    )

    test_PySequence_Fast_ITEMS = CPyExtFunction(
        lambda args: list(args[0]),
        lambda: (
            (tuple(),),
            ((1,2,3),),
            ((None,),),
            ([],),
            (['a','b','c'],),
            ([None],),
        ),
        code='''PyObject* wrap_PySequence_Fast_ITEMS(PyObject* sequence) {
            Py_ssize_t i;
            Py_ssize_t n = PySequence_Fast_GET_SIZE(sequence);
            PyObject **items = PySequence_Fast_ITEMS(sequence);
            PyObject* result = PyList_New(n);
            for (i = 0; i < n; i++) {
                PyList_SetItem(result, i, items[i]);
            } 
            return result;
        }
        ''',
        resultspec="O",
        argspec='O',
        callfunction="wrap_PySequence_Fast_ITEMS",
        arguments=["PyObject* sequence"],
    )

