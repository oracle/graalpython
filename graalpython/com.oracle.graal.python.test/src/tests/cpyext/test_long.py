# Copyright (c) 2018, Oracle and/or its affiliates.
#
# All rights reserved.
import sys
from . import CPyExtTestCase, CPyExtFunction, CPyExtFunctionOutVars, unhandled_error_compare, GRAALPYTHON
__dir__ = __file__.rpartition("/")[0]


def _reference_aslong(args):
    # We cannot be sure if we are on 32-bit or 64-bit architecture. So, assume the smaller one.
    n = int(args[0])
    if n > 0x7fffffff:
        if sys.version_info.minor >= 6:
            raise SystemError
        else:
            return -1
    return n


def _reference_as_unsigned_long(args):
    # We cannot be sure if we are on 32-bit or 64-bit architecture. So, assume the smaller one.
    n = args[0]
    if n > 0x7fffffff or n < 0:
        if sys.version_info.minor >= 6:
            raise SystemError
        else:
            return -1
    return int(n)


def _reference_aslong_overflow(args):
    # We cannot be sure if we are on 32-bit or 64-bit architecture. So, assume the smaller one.
    n = args[0]
    if n > 0x7fffffff:
        if sys.version_info.minor >= 6:
            raise SystemError
        else:
            return (-1, 1)
    return (int(n), 0)


def _reference_fromvoidptr(args):
    n = args[0]
    if n < 0:
        return ((~abs(n))& 0xffffffffffffffff) + 1
    return n


def _reference_fromlong(args):
    n = args[0]
    return n 


class DummyNonInt():
    pass


class DummyIntable():
    def __int__(self):
        return 0xCAFE


class DummyIntSubclass(float):
    def __int__(self):
        return 0xBABE


class TestPyLong(CPyExtTestCase):
    def compile_module(self, name):
        type(self).mro()[1].__dict__["test_%s" % name].create_module(name)
        super(TestPyLong, self).compile_module(name)


    test_PyLong_AsLong = CPyExtFunction(
        lambda args: True,
        lambda: (
            (0,0), 
            (-1,-1),
            (0x7fffffff,0x7fffffff),
            (0xffffffffffffffffffffffffffffffff,-1),
            (DummyIntable(),0xCAFE),
            (DummyIntSubclass(),0xBABE),
            (DummyNonInt(),-1),
        ),
        code='''int wrap_PyLong_AsLong(PyObject* obj, long expected) {
            long res = PyLong_AsLong(obj);
            PyErr_Clear();
            if (res == expected) {
                return 1;
            } else {
                if (expected != -1 && PyErr_Occurred()) {
                    PyErr_Print();
                } else {
                    fprintf(stderr, "expected: %ld\\nactual: %ld\\n", expected, res);
                    fflush(stderr);
                }
                return 0;
            }
        }''',
        resultspec="l",
        argspec='Ol',
        arguments=["PyObject* obj", "long expected"],
        callfunction="wrap_PyLong_AsLong",
        cmpfunc=unhandled_error_compare
    )

    test_PyLong_AsLongAndOverflow = CPyExtFunctionOutVars(
        _reference_aslong_overflow,
        lambda: (
            (0,), 
            (-1,),
            (0x7fffffff,),
        ),
        resultspec="li",
        argspec='O',
        arguments=["PyObject* obj"],
        resultvars=["int overflow"],
        cmpfunc=unhandled_error_compare
    )

    test_PyLong_AsUnsignedLong = CPyExtFunction(
        _reference_as_unsigned_long,
        lambda: (
            (0,), 
            # TODO disable because CPython 3.4.1 does not correctly catch exceptions from native
            #(-1,),
            (0x7fffffff,),
        ),
        resultspec="l",
        argspec='O',
        arguments=["PyObject* obj"],
        cmpfunc=unhandled_error_compare
    )

    test_PyLong_AsSsize_t = CPyExtFunction(
        lambda args: int(args[0]),
        lambda: (
            (0,), 
            (-1,),
            (0x7fffffff,),
        ),
        resultspec="n",
        argspec='O',
        arguments=["PyObject* obj"],
        cmpfunc=unhandled_error_compare
    )

    test_PyLong_FromSsize_t = CPyExtFunction(
        lambda args: int(args[0]),
        lambda: (
            (0,), 
            (-1,), 
            (1,),
            (0xffffffff,),
        ),
        resultspec="O",
        argspec='n',
        arguments=["Py_ssize_t n"],
        cmpfunc=unhandled_error_compare
    )

    test_PyLong_FromDouble = CPyExtFunction(
        lambda args: int(args[0]),
        lambda: (
            (0.0,), 
            (-1.0,),
            (-11.123456789123456789,),
        ),
        resultspec="O",
        argspec='d',
        arguments=["double d"],
        cmpfunc=unhandled_error_compare
    )

    test_PyLong_FromVoidPtr = CPyExtFunction(
        _reference_fromvoidptr,
        lambda: (
            (0,), 
            (-1,), 
            (-2,), 
            (1,),
            (0xffffffff,),
        ),
        resultspec="O",
        argspec='n',
        arguments=["Py_ssize_t n"],
        cmpfunc=unhandled_error_compare
    )
