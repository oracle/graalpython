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
import operator
import unittest

from . import CPyExtTestCase, CPyExtFunction, unhandled_error_compare, CPyExtType, is_native_object


def _float_compare(x, y):

    def isNan(x):
        return isinstance(x, float) and x != x

    if (isinstance(x, BaseException) and isinstance(y, BaseException)):
        return type(x) == type(y)
    else:
        # either equal or both are NaN
        return x == y or isNan(x) and isNan(y)


def _reference_realasdouble(args):
    n = args[0]
    if isinstance(n, complex):
        return n.real
    try:
        return n.__float__()
    except:
        raise TypeError
        
def _reference_fromdoubles(args):
    if isinstance(args[0], float) and isinstance(args[1], float):
        return complex(args[0], args[1])
    raise SystemError

class DummyNonComplex():
    pass


class DummyComplexable():
    def __init__(self, r, i):
        self.r = r
        self.i = i
    def __complex__(self):
        return complex(self.r, self.i)


class DummyComplexSubclass(complex):
    pass


NativeComplexSubclass = CPyExtType(
    "NativeComplexSubclass",
    '',
    struct_base='PyComplexObject base;',
    tp_base='&PyComplex_Type',
    tp_new='0',
    tp_alloc='0',
    tp_free='0',
)


class ManagedNativeComplexSubclass(NativeComplexSubclass):
    pass


class TestPyComplex(CPyExtTestCase):

    test_PyComplex_AsCComplex = CPyExtFunction(
        lambda args: True,
        lambda: (
            (complex(1.0, 2.0), 1.0, 2.0),
            (DummyComplexSubclass(2.0, 3.0), 2.0, 3.0),
            (NativeComplexSubclass(1.0, 2.0), 1.0, 2.0),
            (ManagedNativeComplexSubclass(1.0, 2.0), 1.0, 2.0),
        ),
        code='''int isNaN(double d) {
            return d != d;
        }

        int wrap_PyComplex_AsCComplex(PyObject* obj, double expectedReal, double expectedImag) {
            Py_complex res = PyComplex_AsCComplex(obj);
            if ((res.real == expectedReal && res.imag == expectedImag) || (isNaN(res.real) && isNaN(expectedReal))) {
                return 1;
            } else {
                if (expectedReal != -1.0 && PyErr_Occurred()) {
                    PyErr_Print();
                } else {
                    fprintf(stderr, "expected: (%lf + %lf j)\\nactual: (%lf + %lf j)\\n", expectedReal, expectedImag, res.real, res.imag);
                    fflush(stderr);
                }
                return 0;
            }
        }''',
        resultspec="i",
        argspec='Odd',
        arguments=["PyObject* obj", "double expectedReal", "double expectedImag"],
        resulttype="int",
        callfunction="wrap_PyComplex_AsCComplex",
    )

    test_PyComplex_cval = CPyExtFunction(
        lambda args: (args[0].real, args[0].imag) if type(args[0]) is complex else None,
        lambda: (
            (complex(1.0, 2.0), ),
            (DummyComplexSubclass(2.0, 3.0), ),
            (NativeComplexSubclass(1.0, 2.0),),
            (ManagedNativeComplexSubclass(1.0, 2.0),),
        ),
        code='''
        PyObject* wrap_PyComplex_cval(PyObject* obj) {
            if (PyComplex_CheckExact(obj)) {
                Py_complex res = PyComplex_AsCComplex(obj);
                return PyTuple_Pack(2, PyFloat_FromDouble(res.real), PyFloat_FromDouble(res.imag));
            }
            return Py_None;
        }''',
        resultspec="O",
        argspec='O',
        arguments=["PyObject* obj"],
        callfunction="wrap_PyComplex_cval",
    )

    test_PyComplex_RealAsDouble = CPyExtFunction(
        _reference_realasdouble,
        lambda: (
            (complex(0.0, 2.0), ),
            (complex(1.0, 2.0), ),
            (DummyComplexSubclass(2.0, 3.0), ),
            (NativeComplexSubclass(1.0, 2.0),),
            (ManagedNativeComplexSubclass(1.0, 2.0),),
            ("10.0", ),
        ),
        resultspec="f",
        argspec='O',
        arguments=["PyObject* obj"],
        cmpfunc=unhandled_error_compare
    )

    test_PyComplex_ImagAsDouble = CPyExtFunction(
        lambda args: args[0].imag if isinstance(args[0], complex) else 0.0,
        lambda: (
            (complex(0.0, 2.0), ),
            (complex(1.0, 2.0), ),
            (DummyComplexSubclass(2.0, 3.0), ),
            (NativeComplexSubclass(1.0, 2.0),),
            (ManagedNativeComplexSubclass(1.0, 2.0),),
            ("10.0", ),
        ),
        resultspec="f",
        argspec='O',
        arguments=["PyObject* obj"],
        cmpfunc=unhandled_error_compare
    )
    
    test_PyComplex_FromDoubles = CPyExtFunction(
        _reference_fromdoubles,
        lambda: (
            (float(0.0), float(2.0), ),
            (1.0, 2.0, ),
        ),
        resultspec="O",
        argspec='dd',
        arguments=["double r", "double i"],
        cmpfunc=unhandled_error_compare
    )


class TestNativeComplex(unittest.TestCase):
    def test_builtins_on_subclass(self):
        for t in [NativeComplexSubclass, ManagedNativeComplexSubclass]:
            c = t(2, 3)
            assert is_native_object(c)
            assert type(c) is t
            assert c.real == 2
            assert c.imag == 3
            assert type(complex(c)) is complex
            assert complex(c) == 2 + 3j
            assert c == 2 + 3j
            assert t(2) == 2
            assert t(2.4) == 2.4

            def assert_op_same(f):
                assert f(t(2, 3)) == f(2 + 3j)

            assert_op_same(abs)
            assert_op_same(repr)
            assert_op_same(format)
            assert_op_same(bool)
            assert_op_same(hash)
            assert_op_same(operator.pos)
            assert_op_same(operator.neg)
            assert_op_same(lambda x: x + (1 + 2j))
            assert_op_same(lambda x: x - (1 + 2j))
            assert_op_same(lambda x: x * (1 + 2j))
            assert_op_same(lambda x: x / (1 + 2j))
            assert_op_same(lambda x: x ** (1 + 2j))
            assert_op_same(lambda x: x.conjugate())
