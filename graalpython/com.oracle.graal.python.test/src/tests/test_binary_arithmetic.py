# Copyright (c) 2018, 2020, Oracle and/or its affiliates.
# Copyright (c) 2013, Regents of the University of California
#
# All rights reserved.
#
# Redistribution and use in source and binary forms, with or without modification, are
# permitted provided that the following conditions are met:
#
# 1. Redistributions of source code must retain the above copyright notice, this list of
# conditions and the following disclaimer.
# 2. Redistributions in binary form must reproduce the above copyright notice, this list of
# conditions and the following disclaimer in the documentation and/or other materials provided
# with the distribution.
#
# THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
# OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
# MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
# COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
# EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
# GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
# AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
# NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
# OF THE POSSIBILITY OF SUCH DAMAGE.

import sys

def test_add_long_overflow():
    # max long value is written as long primitive
    val = 0x7fffffffffffffff
    assert val + 128 == 0x800000000000007f


def test_sub_long_overflow():
    # max long value is written as long primitive
    val = -0x8000000000000000
    assert val - 15 == -0x800000000000000f


def test_add_bignum():
    # 0x8000000000000000 is just one more than long max value
    left = 0x8000000000000000 + 1
    assert left == 0x8000000000000001
    right = 1 + 0x8000000000000000
    assert left == right
    both = left + right
    assert both == 0x10000000000000002


def test_add_bignum_narrowing():
    # -9223372036854775809 is just one less than long min value
    left = -0x8000000000000001 + 2
    assert left == -0x7fffffffffffffff
    right = 2 + -0x8000000000000001
    assert left == right
    both = left + right
    assert both == -0xfffffffffffffffe


def test_mul_long_overflow():
    # max long value is written as long primitive
    val = 0x7fffffffffffffff
    assert val * 2 == 0xfffffffffffffffe
    assert 0x8000000000000000 * 1 == 0x8000000000000000


def test_mod():
    assert 5 % 2 == 1
    assert 0x8000000000000000 % 2 == 0
    assert 2 % 0x8000000000000000 == 2
    assert 2 % -0x8000000000000000 == -0x7ffffffffffffffe
    assert 0x8000000000000000f % -0x8000000000000000 == -0x7ffffffffffffff1
    assert -2 % 0x8000000000000000 == 0x7ffffffffffffffe
    assert 0x8000000000000000 % 0x8000000000000000 == 0
    assert 0x8000000000000000 % 0x8000000000000000 == 0
    assert 0x8000000000000000 % 0x8000000000000001 == 0x8000000000000000
    assert 0x8000000000000001 % 0x8000000000000000 == 1

    class MyInt(int):
        pass
    assert 7 % MyInt(-2) == -1


def test_add_bool():
    assert True.__add__(True) == 2, "True.__add__(True)"
    assert True.__add__(False) == 1, "True.__add__(False)"
    assert False.__add__(True) == 1, "False.__add__(True)"
    assert False.__add__(False) == 0, "False.__add__(False)"
    assert False.__add__(0) == 0, "False.__add__(0)"
    assert True.__add__(0) == 1, "True.__add__(0)"
    assert False.__add__(1) == 1, "False.__add__(1)"
    assert True.__add__(1) == 2, "True.__add__(1)"
    assert False.__add__(0x7fffffff) == 0x7fffffff, "False.__add__(0x7fffffff)"
    assert True.__add__(0x7fffffff) == 0x80000000, "True.__add__(0x7fffffff)"
    assert False.__add__(0x7fffffffffffffff) == 0x7fffffffffffffff, "False.__add__(0x7fffffffffffffff)"
    assert True.__add__(0x7fffffffffffffff) == 0x8000000000000000, "True.__add__(0x7fffffffffffffff)"
    assert True.__add__(0.0) == NotImplemented, "True.__add__(0.0)"


def test_mul_bool():
    assert False.__mul__(False) == 0, "False.__mul__(False)"
    assert False.__mul__(True) == 0, "False.__mul__(True)"
    assert False.__mul__(1) == 0, "False.__mul__(1)"
    assert False.__mul__(0) == 0, "False.__mul__(0)"
    assert True.__mul__(0) == 0, "True.__mul__(0)"
    assert True.__mul__(1) == 1, "True.__mul__(1)"
    assert True.__mul__(0x7fffffff) == 0x7fffffff, "True.__mul__(0x7fffffff)"
    assert True.__mul__(0x7fffffffffffffff) == 0x7fffffffffffffff, "True.__mul__(0x7fffffffffffffff)"
    assert True.__mul__(0.0) == NotImplemented, "True.__mul__(0.0)"
    assert True * 1.0 == 1.0, "True * 1.0"
    assert False * 1.0 == 0.0, "False * 0.0"


def expectError(callable):
    try:
        callable()
        return False
    except TypeError as e:
        return True


def test_bin_comparison():
    class A:
        pass

    class B:
        def __gt__(self, other):
            return True

        def __ge__(self, other):
            return True

        def __lt__(self, other):
            return True

        def __le__(self, other):
            return True

    a = A()
    b = B()
    assert a > b, "Comparison 'a > b' failed"
    assert a >= b, "Comparison 'a >= b' failed"
    assert a < b, "Comparison 'a < b' failed"
    assert a <= b, "Comparison 'a <= b' failed"
    assert b > a, "Comparison 'b > a' failed"
    assert b >= a, "Comparison 'b >= a' failed"
    assert b < a, "Comparison 'b < a' failed"
    assert b <= a, "Comparison 'b <= a' failed"


def test_bin_comparison_wo_eq_ne():
    class A():
        def __eq__(self, o):
            return NotImplemented

    assert A() != A()
    a = A()
    assert a == a
    try:
        assert a <= a
    except TypeError:
        pass
    else:
        assert False


def test_floor_div():
    assert True // True == True
    assert True // 2 == 0
    assert True // 2.0 == 0.0
    assert 0 // True == 0
    assert 3 // 2 == 1
    assert 15 // 5 == 3
    assert 0 // 1 == 0
    assert 15.5 // True == 15.0
    assert 15.5 // 5 == 3.0
    assert 16.5 // 5.5 == 3.0
    assert 16.5 // 3.2 == 5.0
    assert -0x8000000000000001 // 2 == -0x4000000000000001
    # 0x8000000000000000 is one above long max
    assert 3 // 0x8000000000000000 == 0
    narrowed = 0x8000000000000000 // 0x10
    widenum = 0x80000000000000000 // 0x10
    assert narrowed == 0x800000000000000
    assert widenum == 0x8000000000000000
    longval = 0x80000000
    assert 0x8000000000000000 // longval == 0x100000000
    assert_exception(lambda: True // False, ZeroDivisionError)
    assert_exception(lambda: True // 0, ZeroDivisionError)
    assert_exception(lambda: True // 0.0, ZeroDivisionError)
    assert_exception(lambda: 3 // False, ZeroDivisionError)
    assert_exception(lambda: 3 // 0, ZeroDivisionError)
    assert_exception(lambda: 3 // 0.0, ZeroDivisionError)
    assert_exception(lambda: 3.0 // False, ZeroDivisionError)
    assert_exception(lambda: 5.4 // 0, ZeroDivisionError)
    assert_exception(lambda: 5.4 // 0.0, ZeroDivisionError)


def test_int_rfloor_div():
    assert int.__rfloordiv__(2, 5) == 2
    assert int.__rfloordiv__(2, 0x8000000000000001) == 0x4000000000000000
    assert int.__rfloordiv__(2, -0x8000000000000001) == -0x4000000000000001


def test_divmod():
    class Floatable:
        def __init__(self, val):
            self.val = val

        def __float__(self):
            return self.val

    def doDivmod(a, b):
        return divmod(a, b)

    argList = [(Floatable(3), Floatable(4)), (complex(1, 2), complex(3, 4))]
    for args in argList:
        assert_exception(lambda: doDivmod(*args), TypeError)


def test_subclass_ordered_binop():
    class A(int):
        def __add__(self, other):
            return 0xa

    class B(A):
        def __add__(self, other):
            return 0xb

    class C(B):
        __radd__ = B.__add__

    assert A(1) + A(1) == 0xa
    assert 1 + A(10) == 11
    assert A(10) + 1 == 0xa

    # TODO: we're doing this wrong right now
    # assert A(1) + B(1) == 0xa
    assert B(1) + A(2) == 0xb

    assert A(1) + C(3) == 0xb
    assert C(3) + A(1) == 0xb


def assert_exception(op, ex_type):
    try:
        op()
        assert False, "expected exception %s" % ex_type
    except BaseException as e:
        if type(e) == AssertionError:
            raise e
        else:
            assert type(e) == ex_type, "expected exception %s but got %s" % (ex_type, type(e))


def test_comparison_numeric_types():
    large_int = 2 ** 65
    numbers = [-large_int, -float(large_int), -1.4, -1, -1.0, -0.0, 0, 0.0, 1, 1.0, 1.4, large_int, float(large_int)]
    indices = [1, 1, 2, 3, 3, 4, 4, 4, 5, 5, 6, 7, 7]
    for i, a in zip(indices, numbers):
        for j, b in zip(indices, numbers):
            assert (a < b) == (i < j)
            assert (a <= b) == (i <= j)
            assert (a == b) == (i == j)
            assert (a != b) == (i != j)
            assert (a > b) == (i > j)
            assert (a >= b) == (i >= j)


def test_sub():
    x = 1 << 66
    y = 1
    assert x - y == 73786976294838206463
    assert y - x == -73786976294838206463

    assert int.__sub__(x, y) == 73786976294838206463
    assert int.__rsub__(y, x) == 73786976294838206463


def test_rshift():
    assert 1 >> 64 == 0
    assert 0xffffffffffff >> 128 == 0
    assert 0x7fffffec >> 24 == 0x7f
    assert 0x7fffffec >> 32 == 0
    assert 0x7fffffffffffffec >> 64 == 0
    bigint = 0xffffffffffffffffffffffffffffffff
    assert bigint >> 120 == 0xff
    assert bigint >> 128 == 0
    trimmed = bigint & 0x7fffffffffffffff
    assert trimmed >> 64 == 0


def test_lshift():
    assert 1 << 32 == 0x100000000
    assert 1 << 64 == 0x10000000000000000
    assert 1 << 128 == 0x100000000000000000000000000000000


def test_pow():
    # (0xffffffffffffffff >> 63) is used to produce a non-narrowed int
    assert 2**(0xffffffffffffffff >> 63) == 2

    if sys.implementation.name == "graalpython":
        try:
            2 ** (2**128)
        except ArithmeticError:
            assert True
        else:
            assert False

    assert 2 ** -(2**128) == 0.0

    class X(float):
        def __rpow__(self, other):
            return 42

    assert 2 ** X() == 42

    try:
        2.0 .__pow__(2, 2)
    except TypeError as e:
        assert True
    else:
        assert False

    try:
        2.0 .__pow__(2.0, 2.0)
    except TypeError as e:
        assert True
    else:
        assert False

    assert 2 ** 2.0 == 4.0

    assert 2 .__pow__("a") == NotImplemented
    assert 2 .__pow__("a", 2) == NotImplemented
    assert 2 .__pow__(2**30, 2**128) == 0

    # crafted to try specializations
    def mypow(a, b, c):
        return a.__pow__(b, c)

    values = [
        [1, 2, None, 1], # long
        [1, 128, None, 1], # BigInteger
        [1, -2, None, 1.0], # double result
        [1, 0xffffffffffffffffffffffffffffffff & 0x80, None, 1], # narrow to long
        [2, 0xffffffffffffffffffffffffffffffff & 0x80, None, 340282366920938463463374607431768211456], # cannot narrow
        [2, -(0xffffffffffffffffffffffffffffffff & 0x80), None, 2.938735877055719e-39], # double result
        [2**128, 0, None, 1], # narrow to long
        [2**128, 1, None, 340282366920938463463374607431768211456], # cannot narrow
        [2**128, -2, None, 8.636168555094445e-78], # double
        [2**128, 0xffffffffffffffffffffffffffffffff & 0x2, None, 115792089237316195423570985008687907853269984665640564039457584007913129639936], # large
        [2**128, -(0xffffffffffffffffffffffffffffffff & 0x8), None, 5.562684646268003e-309], # double result
        [1, 2, 3, 1], # fast path
        [2, 2**30, 2**128, 0], # generic
        ] + []

    if sys.version_info.minor >= 8:
        values += [
            [1, -2, 3, 1], # fast path double
            [1, 2, -3, -2], # negative mod
            [1, -2, -3, -2], # negative mod and negative right
            [1, -2**128, 3, 1], # mod and large negative right
            [1, -2**128, -3, -2], # negative mod and large negative right
            [1, -2**128, -2**64, -18446744073709551615], # large negative mod and large negative right
        ]

    for args in values:
        assert mypow(*args[:-1]) == args[-1], "%r -> %r == %r" % (args, mypow(*args[:-1]), args[-1])

    def mypow_rev(a, b, c):
        return a.__pow__(b, c)

    for args in reversed(values):
        assert mypow_rev(*args[:-1]) == args[-1], "%r -> %r == %r" % (args, mypow(*args[:-1]), args[-1])

    assert 2**1.0 == 2.0
