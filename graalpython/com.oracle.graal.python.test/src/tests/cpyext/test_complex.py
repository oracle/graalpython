# Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
from . import CPyExtTestCase, CPyExtFunction, CPyExtFunctionOutVars, unhandled_error_compare, GRAALPYTHON
__dir__ = __file__.rpartition("/")[0]


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
        if sys.version_info.minor >= 6:
            raise SystemError
        else:
            return -1.0


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


class TestPyComplex(CPyExtTestCase):

    def compile_module(self, name):
        type(self).mro()[1].__dict__["test_%s" % name].create_module(name)
        super(TestPyComplex, self).compile_module(name)

    test_PyComplex_AsCComplex = CPyExtFunction(
        lambda args: True,
        lambda: (
            (complex(1.0, 2.0), 1.0, 2.0),
            (DummyComplexSubclass(2.0, 3.0), 2.0, 3.0),
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

    test_PyComplex_RealAsDouble = CPyExtFunction(
        _reference_realasdouble,
        lambda: (
            (complex(0.0, 2.0), ),
            (complex(1.0, 2.0), ),
            (DummyComplexSubclass(2.0, 3.0), ),
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
            ("10.0", ),
        ),
        resultspec="f",
        argspec='O',
        arguments=["PyObject* obj"],
        cmpfunc=unhandled_error_compare
    )

