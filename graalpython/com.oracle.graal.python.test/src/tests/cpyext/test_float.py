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
import math

from . import CPyExtType, CPyExtTestCase, CPyExtFunction, CPyExtFunctionOutVars, unhandled_error_compare, \
    is_native_object

__dir__ = __file__.rpartition("/")[0]


def _float_compare(x, y):
    def isNan(x):
        return isinstance(x, float) and x != x

    if (isinstance(x, BaseException) and isinstance(y, BaseException)):
        return type(x) == type(y)
    else:
        # either equal or both are NaN
        return x == y or isNan(x) and isNan(y)


def _reference_asdouble(args):
    n = args[0]
    if isinstance(n, float):
        return n
    return float(n)


class DummyNonFloat():
    pass


class DummyFloatable():

    def __float__(self):
        return 3.14159


class DummyFloatSubclass(float):

    def __float__(self):
        return 2.71828


NativeFloatSubclass = CPyExtType(
    'NativeFloatSubclass',
    '',
    struct_base='PyFloatObject base',
    tp_base="&PyFloat_Type",
    tp_new='0',
    tp_alloc='0',
    tp_free='0',
)


class TestPyFloat(CPyExtTestCase):

    def compile_module(self, name):
        type(self).mro()[1].__dict__["test_%s" % name].create_module(name)
        super(TestPyFloat, self).compile_module(name)

    test_PyFloat_AsDouble = CPyExtFunctionOutVars(
        lambda args: True,
        lambda: (
            (float(0.0), 0.0),
            (float(-1.0), -1.0),
            (float(0xffffffffffffffffffffffff), 0xffffffffffffffffffffffff),
            (float('nan'), float('nan')),
            (DummyFloatable(), 3.14159),
            (DummyFloatSubclass(), 0.0),
            (DummyNonFloat(), -1.0),
        ),
        code='''int wrap_PyFloat_AsDouble(PyObject* obj, double expected) {
            double res = PyFloat_AsDouble(obj);
            PyErr_Clear();
            if (res == expected || (res != res && expected != expected)) {
                return 1;
            } else {
                if (expected != -1.0 && PyErr_Occurred()) {
                    PyErr_Print();
                } else {
                    fprintf(stderr, "expected: %lf\\nactual: %lf\\n", expected, res);
                    fflush(stderr);
                }
                return 0;
            }
        }''',
        resultspec="i",
        argspec='Od',
        arguments=["PyObject* obj", "double expected"],
        resulttype="int",
        callfunction="wrap_PyFloat_AsDouble",
    )

    test_PyFloat_FromDouble = CPyExtFunction(
        lambda args: float(args[0]),
        lambda: (
            (0.0,),
            (-1.0,),
            (-11.123456789123456789,),
            (float('nan'),)
        ),
        resultspec="O",
        argspec='d',
        arguments=["double d"],
        cmpfunc=_float_compare
    )

    test_PyFloat_FromString = CPyExtFunction(
        lambda args: float(args[0]),
        lambda: (
            ("1",),
            ("0.0",),
            ("-1.0",),
            ("-11.123456789123456789",),
            ("nan",),
            ("-inf",),
            ("not-a-float",),
        ),
        resultspec="O",
        argspec='O',
        arguments=["PyObject* string"],
        cmpfunc=_float_compare
    )

    test_PyFloat_Check = CPyExtFunction(
        lambda args: isinstance(args[0], float),
        lambda: (
            (0.0,),
            (1.0,),
            (float('nan'),),
            (float(),),
            (DummyNonFloat(),),
            (DummyFloatable(),),
            (DummyFloatSubclass(),),
            (1,),
            (True,),
        ),
        resultspec="i",
        argspec='O',
        arguments=["PyObject* o"],
        cmpfunc=unhandled_error_compare
    )

    test_PyFloat_CheckExact = CPyExtFunction(
        lambda args: type(args[0]) is float,
        lambda: (
            (0.0,),
            (1.0,),
            (float('nan'),),
            (float(),),
            (DummyNonFloat(),),
            (DummyFloatable(),),
            (DummyFloatSubclass(),),
            (1,),
            (True,),
        ),
        resultspec="i",
        argspec='O',
        arguments=["PyObject* o"],
        cmpfunc=unhandled_error_compare
    )


class TestPyOSDouble:
    def test_PyOS_double_to_string(self):
        TestPyOS_Double_To_String = CPyExtType(
            "TestPyOS_Double_To_String",
            '''
            static PyObject* testPyOS_D_to_Str(PyObject* self, PyObject *pyval) {
                double val = PyFloat_AsDouble(pyval);
                char *str = PyOS_double_to_string(val, 'f', 6, 0x2, NULL);
                return PyUnicode_FromString(str);
            }
            ''',
            tp_methods='{"PyOS_double_to_string_test", (PyCFunction)testPyOS_D_to_Str, METH_O, ""}',
        )
        tester = TestPyOS_Double_To_String()
        assert tester.PyOS_double_to_string_test(150.604459) == '150.604459'
        assert tester.PyOS_double_to_string_test(174.426353) == '174.426353'
        assert tester.PyOS_double_to_string_test(151.074362) == '151.074362'
        assert tester.PyOS_double_to_string_test(190.08) == '190.080000'

    def test_PyOS_string_to_double(self):
        TestPyOS_String_To_Double = CPyExtType(
            "TestPyOS_String_To_Double",
            '''
            static PyObject* testPyOS_Str_to_D(PyObject* self, PyObject *str) {
                char *endptr;
                const char *s = (char *) PyUnicode_AsUTF8(str);
                double ret = PyOS_string_to_double(s, &endptr, NULL);
                if (PyErr_Occurred()) {
                    return NULL;
                }
                return PyFloat_FromDouble(ret);
            }
            ''',
            tp_methods='{"PyOS_string_to_double_test", (PyCFunction)testPyOS_Str_to_D, METH_O, ""}',
        )
        tester = TestPyOS_String_To_Double()
        assert tester.PyOS_string_to_double_test('5') == float(5)
        assert tester.PyOS_string_to_double_test('150.604459') == float(150.604459)


class TestNativeFloatSubclass:
    def test_create(self):
        f = NativeFloatSubclass(1.0)
        assert is_native_object(f)

        class ManagedSubclass(NativeFloatSubclass):
            pass

        f = ManagedSubclass(1.0)
        assert is_native_object(f)

    def test_methods(self):
        f = NativeFloatSubclass(1.1)
        zero = NativeFloatSubclass(0.0)
        nan = NativeFloatSubclass('nan')

        assert bool(f)
        assert not bool(zero)

        assert f == 1.1
        assert nan != nan
        assert f != zero
        assert +f == 1.1
        assert -f == -1.1
        assert f > 0
        assert f < 2
        assert f >= 1.1
        assert f <= 1.1
        assert str(f) == '1.1'
        assert repr(f) == '1.1'
        assert int(f) == 1
        assert format(f, 'f') == '1.100000'
        assert abs(NativeFloatSubclass(-2.1)) == 2.1
        assert round(f) == 1.0
        assert f + 1.0 == 2.1
        assert 1.0 + f == 2.1
        assert f * 2 == 2.2
        assert f - 1.6 == -0.5
        assert f // 2 == 0.0
        assert f % 2 == 1.1
        assert f / 2.0 == 0.55
        assert f ** 0 == 1.0
        assert divmod(f, 2) == (0.0, 1.1)
        assert hash(f) == hash(1.1)
        assert f.hex() == (1.1).hex()
        assert f.real == 1.1
        assert f.imag == 0.0
        assert f.conjugate() == 1.1
        assert not f.is_integer()
        assert f.as_integer_ratio() == (1.1).as_integer_ratio()
        assert f.__getnewargs__() == (1.1,)

    def test_math(self):
        assert math.isnan(NativeFloatSubclass('nan'))
        assert math.isinf(NativeFloatSubclass('inf'))
        assert not math.isfinite(NativeFloatSubclass('inf'))
        f = NativeFloatSubclass(0.9)
        for fn in [math.ceil, math.floor, math.sqrt, math.exp, math.expm1, math.frexp, math.modf, math.sin, math.asin,
                   math.cos, math.acos, math.tan, math.atan, math.log2, math.log10, math.log1p, math.fabs, math.trunc,
                   math.degrees, math.radians, math.gamma, math.lgamma, math.sqrt]:
            assert fn(f) == fn(0.9)

        assert math.log(f, 10) == math.log(0.9, 10)
