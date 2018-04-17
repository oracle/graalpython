# Copyright (c) 2018, Oracle and/or its affiliates.
#
# All rights reserved.
import sys
from . import CPyExtTestCase, CPyExtFunction, CPyExtFunctionOutVars, unhandled_error_compare, GRAALPYTHON
__dir__ = __file__.rpartition("/")[0]


def _float_compare(x, y):
    def isNan(x):
        return isinstance(x, float) and x != x

    if (isinstance(x,BaseException) and isinstance(y,BaseException)):
        return type(x) == type(y)
    else:
        # either equal or both are NaN
        return x == y or isNan(x) and isNan(y)


def _reference_asdouble(args):
    n = args[0]
    if isinstance(n, float):
        return n
    try:
        return float(n)
    except:
        if sys.version_info.minor >= 6:
            raise SystemError
        else:
            return -1.0


class DummyNonFloat():
    pass


class DummyFloatable():
    def __float__(self):
        return 3.14159


class DummyFloatSubclass(float):
    def __float__(self):
        return 2.71828


class TestPyFloat(CPyExtTestCase):
    def compile_module(self, name):
        type(self).mro()[1].__dict__["test_%s" % name].create_module(name)
        super(TestPyFloat, self).compile_module(name)


    test_PyFloat_AsDouble = CPyExtFunctionOutVars(
        lambda args: True,
        lambda: (
            (float(0.0), 0.0), 
            (float(-1.0),-1.0),
            (float(0xffffffffffffffffffffffff),0xffffffffffffffffffffffff),
            (float('nan'),float('nan')),
            (DummyFloatable(),3.14159),
            (DummyFloatSubclass(),0.0),
            (DummyNonFloat(),-1.0),
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

