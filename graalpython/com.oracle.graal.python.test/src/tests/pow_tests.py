# Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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


def test_pow():
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
