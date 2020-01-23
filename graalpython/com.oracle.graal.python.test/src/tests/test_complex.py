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

import unittest
from math import atan2

def test_create():
    c = 4 + 4j
    assert c == complex(4, 4)
    c = 1.2 + 4j
    assert c == complex(1.2, 4)
    c = 1.2 + 4.1j
    assert c == complex(1.2, 4.1)
    c = 1 + 4.1j
    assert c == complex(1, 4.1)
    c = -4 + 4j
    assert c == complex(-4, 4)
    c = -1.2 + 4j
    assert c == complex(-1.2, 4)
    c = -1.2 + 4.1j
    assert c == complex(-1.2, 4.1)
    c = -1 + 4.1j
    assert c == complex(-1, 4.1)
    c = 4 - 4j
    assert c == complex(4, -4)
    c = 1.2 - 4j
    assert c == complex(1.2, -4)
    c = 1.2 - 4.1j
    assert c == complex(1.2, -4.1)
    c = 1 - 4.1j
    assert c == complex(1, -4.1)
    c = -4 - 4j
    assert c == complex(-4, -4)
    c = -1.2 - 4j
    assert c == complex(-1.2, -4)
    c = -1.2 - 4.1j
    assert c == complex(-1.2, -4.1)
    c = -1 - 4.1j
    assert c == complex(-1, -4.1)
    c = 1 - 0j
    assert c == complex(1, 0)

def test_create_from_complex():
    assert complex(2+2j, 1+1J) == complex(1, 3)

def test_sub():
    c1 = 1+1j
    assert (c1 - c1) == complex(0, 0)
    c2 = 2+2j
    assert (c2 - c1) == complex(1, 1)
    assert (c1 - c2) == complex(-1, -1)
    assert (c2 - 2) == complex(0, 2)
    assert (2 - c2) == complex(0, -2)
    assert (c2 - 1.5) == complex(0.5, 2)
    assert (1.5 - c2) == complex(-0.5, -2)


def test_div():
    c2 = 2+2j
    assert (c2 / 2) == complex(1, 1)
    assert (2 / c2) == complex(0.5, -0.5)
    assert (c2 / 0.5) == complex(4, 4)
    assert (0.5 / c2) == complex(0.125, -0.125)
    c1 = 8 + 6j
    assert (c1 / c2) == complex(3.5, -0.5)
    assert (c2 / c1) == complex(0.28, 0.04)

def assert_raises(err, fn, *args, **kwargs):
    raised = False
    try:
        fn(*args, **kwargs)
    except err:
        raised = True
    assert raised

def test_fromString():
    assert complex("1+2j") == complex(1, 2)
    assert complex("1-2j") == complex(1, -2)
    assert complex("-1-2j") == complex(-1, -2)
    assert complex("-1.1-2.2j") == complex(-1.1, -2.2)

    assert complex("  1+2j  ") == complex(1, 2)
    assert complex("  1-2j  ") == complex(1, -2)
    assert complex("  -1-2j  ") == complex(-1, -2)
    assert complex("  -1.1-2.2j  ") == complex(-1.1, -2.2)

    assert complex("   (1+2j)") == complex(1, 2)
    assert complex("   (   1-2j)") == complex(1, -2)
    assert complex("  (  -1-2j  )  ") == complex(-1, -2)
    assert complex("(   -1.1-2.2j  )") == complex(-1.1, -2.2)

def test_fromString_wrong():
    assert_raises (ValueError, complex, "1 +2j")
    assert_raises (ValueError, complex, "1+2 j")
    assert_raises (ValueError, complex, "(1+2j")
    assert_raises (ValueError, complex, "1+2j  )")
    assert_raises (ValueError, complex, "2j+1")
    assert_raises (TypeError, complex, None)
    assert_raises (ValueError, complex, "1e1ej")
    assert_raises (ValueError, complex, "1e++1ej")

def test_createFromObjects():
    class CP1(complex):
        pass

    class CP2(complex):
        def __complex__(self):
            return 42j

    assert CP1(1+4j) == complex(1, 4)

    c = CP2(1+4j)
    assert c == complex(1, 4)
    assert type(c) == CP2

    c = CP2(CP1(1+4j))
    assert c == complex(1, 4)
    assert type(c) == CP2

    c = complex(CP2(1+4j))
    assert c == complex(42j)
    assert type(c) == complex

    assert complex(2.5, complex(2+2j)) == complex(0.5, 2)
    assert complex(2.5, CP2(2+2j)) == complex(0.5, 2)

    assert complex(complex(2+2j), 2.5) == complex(2, 4.5)
    assert complex(CP1(2+2j), 2.5) == complex(2, 4.5)
    assert complex(CP2(2+2j), 2.5) == complex(0, 44.5)

    c = CP1(CP2(2+2j), 2.5)
    assert c == complex(0, 44.5)
    assert type(c) == CP1

    c = complex(5+5j, 7+7j)
    assert c == complex(-2, 12)
    assert type(c) == complex

    c = complex(5+5j, CP1(7+7j))
    assert c == complex(-2, 12)
    assert type(c) == complex

    c = complex(CP1(5+5j), 7+7j)
    assert c == complex(-2, 12)
    assert type(c) == complex
    
    c = complex(CP1(5+5j), CP1(7+7j))
    assert c == complex(-2, 12)
    assert type(c) == complex

    c = CP1(5+5j, 7+7j)
    assert c == complex(-2, 12)
    assert type(c) == CP1

    c = CP1(5+5j, CP1(7+7j))
    assert c == complex(-2, 12)
    assert type(c) == CP1

    c = CP1(CP1(5+5j), 7+7j)
    assert c == complex(-2, 12)
    assert type(c) == CP1
    
    c = CP1(CP1(5+5j), CP1(7+7j))
    assert c == complex(-2, 12)
    assert type(c) == CP1

    c = CP1(5+5j, CP2(7+7j))
    assert c == complex(-2, 12)
    assert type(c) == CP1

    c = CP1(CP2(5+5j), 7+7j)
    assert c == complex(-7, 49)
    assert type(c) == CP1
    
    c = CP1(CP2(5+5j), CP2(7+7j))
    assert c == complex(-7, 49)
    assert type(c) == CP1

    c = complex(5+5j, CP2(7+7j))
    assert c == complex(-2, 12)
    assert type(c) == complex

    c = complex(CP2(5+5j), 7+7j)
    assert c == complex(-7, 49)
    assert type(c) == complex
    
    c = complex(CP2(5+5j), CP2(7+7j))
    assert c == complex(-7, 49)
    assert type(c) == complex

    c = CP2(5+5j, 7+7j)
    assert c == complex(-2, 12)
    assert type(c) == CP2

    c = CP2(5+5j, CP2(7+7j))
    assert c == complex(-2, 12)
    assert type(c) == CP2

    c = CP2(CP2(5+5j), 7+7j)
    assert c == complex(-7, 49)
    assert type(c) == CP2
    
    c = CP2(CP2(5+5j), CP2(7+7j))
    assert c == complex(-7, 49)
    assert type(c) == CP2


class ComplexTest(unittest.TestCase):

    def assertAlmostEqual(self, a, b):
        if isinstance(a, complex):
            if isinstance(b, complex):
                unittest.TestCase.assertAlmostEqual(self, a.real, b.real)
                unittest.TestCase.assertAlmostEqual(self, a.imag, b.imag)
            else:
                unittest.TestCase.assertAlmostEqual(self, a.real, b)
                unittest.TestCase.assertAlmostEqual(self, a.imag, 0.)
        else:
            if isinstance(b, complex):
                unittest.TestCase.assertAlmostEqual(self, a, b.real)
                unittest.TestCase.assertAlmostEqual(self, 0., b.imag)
            else:
                unittest.TestCase.assertAlmostEqual(self, a, b)

    def test_constructor(self):
        class OS:
            def __init__(self, value): self.value = value
            def __complex__(self): return self.value
        class NS(object):
            def __init__(self, value): self.value = value
            def __complex__(self): return self.value
        self.assertEqual(complex(OS(1+10j)), 1+10j)
        self.assertEqual(complex(NS(1+10j)), 1+10j)
        self.assertRaises(TypeError, complex, OS(None))
        self.assertRaises(TypeError, complex, NS(None))
        self.assertRaises(TypeError, complex, {})
        self.assertRaises(TypeError, complex, NS(1.5))
        self.assertRaises(TypeError, complex, NS(1))

        self.assertAlmostEqual(complex("1+10j"), 1+10j)
        self.assertAlmostEqual(complex(10), 10+0j)
        self.assertAlmostEqual(complex(10.0), 10+0j)
        self.assertAlmostEqual(complex(10), 10+0j)
        self.assertAlmostEqual(complex(10+0j), 10+0j)
        self.assertAlmostEqual(complex(1,10), 1+10j)
        self.assertAlmostEqual(complex(1,10), 1+10j)
        self.assertAlmostEqual(complex(1,10.0), 1+10j)
        self.assertAlmostEqual(complex(1,10), 1+10j)
        self.assertAlmostEqual(complex(1,10), 1+10j)
        self.assertAlmostEqual(complex(1,10.0), 1+10j)
        self.assertAlmostEqual(complex(1.0,10), 1+10j)
        self.assertAlmostEqual(complex(1.0,10), 1+10j)
        self.assertAlmostEqual(complex(1.0,10.0), 1+10j)
        self.assertAlmostEqual(complex(3.14+0j), 3.14+0j)
        self.assertAlmostEqual(complex(3.14), 3.14+0j)
        self.assertAlmostEqual(complex(314), 314.0+0j)
        self.assertAlmostEqual(complex(314), 314.0+0j)
        self.assertAlmostEqual(complex(3.14+0j, 0j), 3.14+0j)
        self.assertAlmostEqual(complex(3.14, 0.0), 3.14+0j)
        self.assertAlmostEqual(complex(314, 0), 314.0+0j)
        self.assertAlmostEqual(complex(314, 0), 314.0+0j)
        self.assertAlmostEqual(complex(0j, 3.14j), -3.14+0j)
        self.assertAlmostEqual(complex(0.0, 3.14j), -3.14+0j)
        self.assertAlmostEqual(complex(0j, 3.14), 3.14j)
        self.assertAlmostEqual(complex(0.0, 3.14), 3.14j)
        self.assertAlmostEqual(complex("1"), 1+0j)
        self.assertAlmostEqual(complex("1j"), 1j)
        self.assertAlmostEqual(complex(),  0)
        self.assertAlmostEqual(complex("-1"), -1)
        self.assertAlmostEqual(complex("+1"), +1)
        self.assertAlmostEqual(complex("(1+2j)"), 1+2j)
        self.assertAlmostEqual(complex("(1.3+2.2j)"), 1.3+2.2j)
        self.assertAlmostEqual(complex("3.14+1J"), 3.14+1j)
        self.assertAlmostEqual(complex(" ( +3.14-6J )"), 3.14-6j)
        self.assertAlmostEqual(complex(" ( +3.14-J )"), 3.14-1j)
        self.assertAlmostEqual(complex(" ( +3.14+j )"), 3.14+1j)
        self.assertAlmostEqual(complex("J"), 1j)
        self.assertAlmostEqual(complex("( j )"), 1j)
        self.assertAlmostEqual(complex("+J"), 1j)
        self.assertAlmostEqual(complex("( -j)"), -1j)
        self.assertAlmostEqual(complex('1e-500'), 0.0 + 0.0j)
        self.assertAlmostEqual(complex('-1e-500j'), 0.0 - 0.0j)
        self.assertAlmostEqual(complex('-1e-500+1e-500j'), -0.0 + 0.0j)

        class complex2(complex): pass
        self.assertAlmostEqual(complex(complex2(1+1j)), 1+1j)
        self.assertAlmostEqual(complex(real=17, imag=23), 17+23j)
        self.assertAlmostEqual(complex(real=17+23j), 17+23j)
        self.assertAlmostEqual(complex(real=17+23j, imag=23), 17+46j)
        self.assertAlmostEqual(complex(real=1+2j, imag=3+4j), -3+5j)


        # check that the sign of a zero in the real or imaginary part
        # is preserved when constructing from two floats.  (These checks
        # are harmless on systems without support for signed zeros.)
        def split_zeros(x):
            """Function that produces different results for 0. and -0."""
            return atan2(x, -1.)

        self.assertEqual(split_zeros(complex(1., 0.).imag), split_zeros(0.))
        self.assertEqual(split_zeros(complex(1., -0.).imag), split_zeros(-0.))
        self.assertEqual(split_zeros(complex(0., 1.).real), split_zeros(0.))
        self.assertEqual(split_zeros(complex(-0., 1.).real), split_zeros(-0.))

        c = 3.14 + 1j
        self.assertTrue(complex(c) is c)
        del c

        self.assertRaises(TypeError, complex, "1", "1")
        self.assertRaises(TypeError, complex, 1, "1")

        # SF bug 543840:  complex(string) accepts strings with \0
        # Fixed in 2.3.
        self.assertRaises(ValueError, complex, '1+1j\0j')

        self.assertRaises(TypeError, int, 5+3j)
        self.assertRaises(TypeError, int, 5+3j)
        self.assertRaises(TypeError, float, 5+3j)
        self.assertRaises(ValueError, complex, "")
        self.assertRaises(TypeError, complex, None)
        self.assertRaisesRegex(TypeError, "not 'NoneType'", complex, None)
        self.assertRaises(ValueError, complex, "\0")
        self.assertRaises(ValueError, complex, "3\09")
        self.assertRaises(TypeError, complex, "1", "2")
        self.assertRaises(TypeError, complex, "1", 42)
        self.assertRaises(TypeError, complex, 1, "2")
        self.assertRaises(ValueError, complex, "1+")
        self.assertRaises(ValueError, complex, "1+1j+1j")
        self.assertRaises(ValueError, complex, "--")
        self.assertRaises(ValueError, complex, "(1+2j")
        self.assertRaises(ValueError, complex, "1+2j)")
        self.assertRaises(ValueError, complex, "1+(2j)")
        self.assertRaises(ValueError, complex, "(1+2j)123")
        self.assertRaises(ValueError, complex, "x")
        self.assertRaises(ValueError, complex, "1j+2")
        self.assertRaises(ValueError, complex, "1e1ej")
        self.assertRaises(ValueError, complex, "1e++1ej")
        self.assertRaises(ValueError, complex, ")1+2j(")
        self.assertRaisesRegex(
            TypeError,
            "first argument must be a string or a number, not 'dict'",
            complex, {1:2}, 1)
        self.assertRaisesRegex(
            TypeError,
            "second argument must be a number, not 'dict'",
            complex, 1, {1:2})
        # the following three are accepted by Python 2.6
        self.assertRaises(ValueError, complex, "1..1j")
        self.assertRaises(ValueError, complex, "1.11.1j")
        self.assertRaises(ValueError, complex, "1e1.1j")

        # check that complex accepts long unicode strings
        self.assertEqual(type(complex("1"*500)), complex)
        # check whitespace processing
        self.assertEqual(complex('\N{EM SPACE}(\N{EN SPACE}1+1j ) '), 1+1j)
        # Invalid unicode string
        # See bpo-34087
        self.assertRaises(ValueError, complex, '\u3053\u3093\u306b\u3061\u306f')

        class EvilExc(Exception):
            pass

        class evilcomplex:
            def __complex__(self):
                raise EvilExc

        self.assertRaises(EvilExc, complex, evilcomplex())

        class float2:
            def __init__(self, value):
                self.value = value
            def __float__(self):
                return self.value

        self.assertAlmostEqual(complex(float2(42.)), 42)
        self.assertAlmostEqual(complex(real=float2(17.), imag=float2(23.)), 17+23j)
        self.assertRaises(TypeError, complex, float2(None))

        class complex0(complex):
            """Test usage of __complex__() when inheriting from 'complex'"""
            def __complex__(self):
                return 42j

        class complex1(complex):
            """Test usage of __complex__() with a __new__() method"""
            def __new__(self, value=0j):
                return complex.__new__(self, 2*value)
            def __complex__(self):
                return self

        class complex2(complex):
            """Make sure that __complex__() calls fail if anything other than a
            complex is returned"""
            def __complex__(self):
                return None

        self.assertEqual(complex(complex0(1j)), 42j)
        # TODO we are not able to throw warning now. 
#        with self.assertWarns(DeprecationWarning):
        self.assertEqual(complex(complex1(1j)), 2j)
        self.assertRaises(TypeError, complex, complex2(1j))
