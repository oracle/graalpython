# Copyright (c) 2018, Oracle and/or its affiliates.
# Copyright (C) 1996-2017 Python Software Foundation
#
# Licensed under the PYTHON SOFTWARE FOUNDATION LICENSE VERSION 2
import math
import unittest
import sys
import struct

eps = 1E-05
INF = float('inf')
NINF = float('-inf')
NAN = float('nan')
LONG_INT = 6227020800
BIG_INT = 9999992432902008176640000999999
FLOAT_MAX = sys.float_info.max

class MyIndexable(object):
    def __init__(self, value):
        self.value = value
    def __index__(self):
        return self.value

""" The next three methods are needed for testing factorials
"""
def count_set_bits(n):
    """Number of '1' bits in binary expansion of a nonnnegative integer."""
    return 1 + count_set_bits(n & n - 1) if n else 0

def partial_product(start, stop):
    """Product of integers in range(start, stop, 2), computed recursively.
    start and stop should both be odd, with start <= stop.

    """
    numfactors = (stop - start) >> 1
    if not numfactors:
        return 1
    elif numfactors == 1:
        return start
    else:
        mid = (start + numfactors) | 1
        return partial_product(start, mid) * partial_product(mid, stop)

def py_factorial(n):
    """Factorial of nonnegative integer n, via "Binary Split Factorial Formula"
    described at http://www.luschny.de/math/factorial/binarysplitfact.html

    """
    inner = outer = 1
    for i in reversed(range(n.bit_length())):
        inner *= partial_product((n >> i + 1) + 1 | 1, (n >> i) + 1 | 1)
        outer *= inner
    return outer << (n - count_set_bits(n))

def ulp(x):
    """Return the value of the least significant bit of a
    float x, such that the first float bigger than x is x+ulp(x).
    Then, given an expected result x and a tolerance of n ulps,
    the result y should be such that abs(y-x) <= n * ulp(x).
    The results from this function will only make sense on platforms
    where native doubles are represented in IEEE 754 binary64 format.
    """
    x = abs(float(x))
    if math.isnan(x) or math.isinf(x):
        return x

    # Find next float up from x.
    n = struct.unpack('<q', struct.pack('<d', x))[0]
    x_next = struct.unpack('<d', struct.pack('<q', n + 1))[0]
    if math.isinf(x_next):
        # Corner case: x was the largest finite float. Then it's
        # not an exact power of two, so we can take the difference
        # between x and the previous float.
        x_prev = struct.unpack('<d', struct.pack('<q', n - 1))[0]
        return x - x_prev
    else:
        return x_next - x

def to_ulps(x):
    """Convert a non-NaN float x to an integer, in such a way that
    adjacent floats are converted to adjacent integers.  Then
    abs(ulps(x) - ulps(y)) gives the difference in ulps between two
    floats.

    The results from this function will only make sense on platforms
    where native doubles are represented in IEEE 754 binary64 format.

    Note: 0.0 and -0.0 are converted to 0 and -1, respectively.
    """
    n = struct.unpack('<q', struct.pack('<d', x))[0]
    if n < 0:
        n = ~(n+2**63)
    return n

def ulp_abs_check(expected, got, ulp_tol, abs_tol):
    """Given finite floats `expected` and `got`, check that they're
    approximately equal to within the given number of ulps or the
    given absolute tolerance, whichever is bigger.

    Returns None on success and an error message on failure.
    """
    ulp_error = abs(to_ulps(expected) - to_ulps(got))
    abs_error = abs(expected - got)

    # Succeed if either abs_error <= abs_tol or ulp_error <= ulp_tol.
    if abs_error <= abs_tol or ulp_error <= ulp_tol:
        return None
    else:
        fmt = ("error = {:.3g} ({:d} ulps); "
               "permitted error = {:.3g} or {:d} ulps")
        return fmt.format(abs_error, ulp_error, abs_tol, ulp_tol)

def result_check(expected, got, ulp_tol=5, abs_tol=0.0):
    # Common logic of MathTests.(ftest, test_testcases, test_mtestcases)
    """Compare arguments expected and got, as floats, if either
    is a float, using a tolerance expressed in multiples of
    ulp(expected) or absolutely (if given and greater).

    As a convenience, when neither argument is a float, and for
    non-finite floats, exact equality is demanded. Also, nan==nan
    as far as this function is concerned.

    Returns None on success and an error message on failure.
    """

    # Check exactly equal (applies also to strings representing exceptions)
    if got == expected:
        return None

    failure = "not equal"

    # Turn mixed float and int comparison (e.g. floor()) to all-float
    if isinstance(expected, float) and isinstance(got, int):
        got = float(got)
    elif isinstance(got, float) and isinstance(expected, int):
        expected = float(expected)

    if isinstance(expected, float) and isinstance(got, float):
        if math.isnan(expected) and math.isnan(got):
            # Pass, since both nan
            failure = None
        elif math.isinf(expected) or math.isinf(got):
            # We already know they're not equal, drop through to failure
            pass
        else:
            # Both are finite floats (now). Are they close enough?
            failure = ulp_abs_check(expected, got, ulp_tol, abs_tol)

    # arguments are not equal, and if numeric, are too far apart
    if failure is not None:
        fail_fmt = "expected {!r}, got {!r}"
        fail_msg = fail_fmt.format(expected, got)
        fail_msg += ' ({})'.format(failure)
        return fail_msg
    else:
        return None

class MyFloat:
    def __float__(self):
        return 0.6

class MyInt(object):
    def __init__(self, value):
        self.value = value
    def __int__(self):
        return self.value

class MathTests(unittest.TestCase):

    def ftest(self, name, got, expected, ulp_tol=5, abs_tol=0.0):
        """Compare arguments expected and got, as floats, if either
        is a float, using a tolerance expressed in multiples of
        ulp(expected) or absolutely, whichever is greater.

        As a convenience, when neither argument is a float, and for
        non-finite floats, exact equality is demanded. Also, nan==nan
        in this function.
        """
        failure = result_check(expected, got, ulp_tol, abs_tol)
        if failure is not None:
            raise RuntimeError("{}: {}".format(name, failure))
            #self.fail("{}: {}".format(name, failure))

    def testConstants(self):
        # Ref: Abramowitz & Stegun (Dover, 1965)
        self.ftest('pi', math.pi, 3.141592653589793238462643)
        self.ftest('e', math.e, 2.718281828459045235360287)
        if (sys.version_info.major >= 3 and sys.version_info.minor >= 6):
            # math.tau since 3.6
            self.assertEqual(math.tau, 2*math.pi)

    def testAcos(self):
        self.assertRaises(TypeError, math.acos)
        self.ftest('acos(-1)', math.acos(-1), math.pi)
        self.ftest('acos(0)', math.acos(0), math.pi/2)
        self.ftest('acos(1)', math.acos(1), 0)
        self.assertRaises(ValueError, math.acos, INF)
        self.assertRaises(ValueError, math.acos, NINF)
        self.assertRaises(ValueError, math.acos, 1 + eps)
        self.assertRaises(ValueError, math.acos, -1 - eps)
        self.assertTrue(math.isnan(math.acos(NAN)))

        self.assertEqual(math.acos(True), 0.0)
        self.assertRaises(ValueError, math.acos, 10)
        self.assertRaises(ValueError, math.acos, -10)
        self.assertRaises(ValueError, math.acos, LONG_INT)
        self.assertRaises(ValueError, math.acos, BIG_INT)
        self.assertRaises(TypeError, math.acos, 'ahoj')

        self.assertRaises(ValueError, math.acos, 9999992432902008176640000999999)

        self.ftest('acos(MyFloat())', math.acos(MyFloat()), 0.9272952180016123)

        class MyFloat2:
            def __float__(self):
                return 1.6
        self.assertRaises(ValueError, math.acos, MyFloat2())        

        class MyFloat3:
            def __float__(self):
                return 'ahoj'
        self.assertRaises(TypeError, math.acos, MyFloat3())

    def testAcosh(self):
        self.assertRaises(TypeError, math.acosh)
        self.ftest('acosh(1)', math.acosh(1), 0)
        self.ftest('acosh(2)', math.acosh(2), 1.3169578969248168)
        self.assertRaises(ValueError, math.acosh, 0)
        self.assertRaises(ValueError, math.acosh, -1)
        self.assertEqual(math.acosh(INF), INF)
        self.assertRaises(ValueError, math.acosh, NINF)
        self.assertTrue(math.isnan(math.acosh(NAN)))

        class MyFF:
            def __float__(self):
                return 1.4616427410996713
        self.ftest('acos(MyFloat())', math.acosh(MyFF()), 0.9272952180016123)
        self.assertRaises(ValueError, math.acosh, MyFloat())
        math.acosh(BIG_INT)
        self.assertRaises(TypeError, math.acosh, 'ahoj')

    def testAsin(self):
        self.assertRaises(TypeError, math.asin)
        self.ftest('asin(-1)', math.asin(-1), -math.pi/2)
        self.ftest('asin(0)', math.asin(0), 0)
        self.ftest('asin(1)', math.asin(1), math.pi/2)
        self.assertRaises(ValueError, math.asin, INF)
        self.assertRaises(ValueError, math.asin, NINF)
        self.assertRaises(ValueError, math.asin, 1 + eps)
        self.assertRaises(ValueError, math.asin, -1 - eps)
        self.assertTrue(math.isnan(math.asin(NAN)))

        self.assertRaises(ValueError, math.asin, 10)
        self.assertRaises(ValueError, math.asin, -10)
        self.assertRaises(ValueError, math.asin, LONG_INT)
        self.assertRaises(ValueError, math.asin, BIG_INT)
        self.assertRaises(TypeError, math.asin, 'ahoj')

    def testSqrt(self):
        self.assertRaises(TypeError, math.sqrt)
        self.ftest('sqrt(0)', math.sqrt(0), 0)
        self.ftest('sqrt(1)', math.sqrt(1), 1)
        self.ftest('sqrt(4)', math.sqrt(4), 2)
        self.assertEqual(math.sqrt(INF), INF)
        self.assertRaises(ValueError, math.sqrt, -1)
        self.assertRaises(ValueError, math.sqrt, NINF)
        self.assertTrue(math.isnan(math.sqrt(NAN)))
        
        math.sqrt(MyFloat())
        math.sqrt(BIG_INT)
        self.assertRaises(TypeError, math.asin, 'ahoj')

    def testLog(self):
        self.assertRaises(TypeError, math.log)
        self.ftest('log(1/e)', math.log(1/math.e), -1)
        self.ftest('log(1)', math.log(1), 0)
        self.ftest('log(e)', math.log(math.e), 1)
        self.ftest('log(32,2)', math.log(32,2), 5)
        self.ftest('log(10**40, 10)', math.log(10**40, 10), 40)
        self.ftest('log(10**40, 10**20)', math.log(10**40, 10**20), 2)
        self.ftest('log(10**1000)', math.log(10**1000), 2302.5850929940457)
        self.assertRaises(ValueError, math.log, -1.5)
        self.assertRaises(ValueError, math.log, -10**1000)
        self.assertRaises(ValueError, math.log, NINF)
        self.assertEqual(math.log(INF), INF)
        self.assertTrue(math.isnan(math.log(NAN)))

        math.log(MyFloat())
        self.assertRaises(ZeroDivisionError, math.log, MyFloat(), True)
        self.ftest('log(True, 1.1)', math.log(True, 1.1), 0)
        math.log(BIG_INT)
        math.log(BIG_INT, 4.6)
        self.ftest('log(BIG_INT, BIG_INT)', math.log(BIG_INT, BIG_INT), 1)
        self.assertRaises(ZeroDivisionError, math.log, BIG_INT, True)
        self.assertRaises(TypeError, math.asin, 'ahoj')

        math.log(MyFloat(), 10)
        math.log(MyFloat(), BIG_INT)
        math.log(MyFloat(), 7.4)
        self.ftest('log(MyFloat(), MyFloat())', math.log(MyFloat(), MyFloat()), 1)
        math.log(10, MyFloat())
        self.assertRaises(ValueError, math.log, 0)

    def testLog1p(self):
        self.assertRaises(TypeError, math.log1p)
        for n in [2, 2**90, 2**300]:
            self.assertAlmostEqual(math.log1p(n), math.log1p(float(n)))
        self.assertRaises(ValueError, math.log1p, -1)
        self.assertEqual(math.log1p(INF), INF)
        
        # test of specializations
        self.ftest('log1p(MyFloat())', math.log1p(MyFloat()), 0.4700036292457356)
        self.assertRaises(TypeError, math.log1p, 'ahoj')
        self.ftest('log1p(BIG_INT)', math.log1p(BIG_INT), 71.38013712610532)

    #@requires_IEEE_754
    def testLog2(self):
        self.assertRaises(TypeError, math.log2)

        # Check some integer values
        self.assertEqual(math.log2(1), 0.0)
        self.assertEqual(math.log2(2), 1.0)
        self.assertEqual(math.log2(4), 2.0)

        # Large integer values
        self.assertEqual(math.log2(2**1023), 1023.0)
        self.assertEqual(math.log2(2**1024), 1024.0)
        self.assertEqual(math.log2(2**2000), 2000.0)

        self.assertRaises(ValueError, math.log2, -1.5)
        self.assertRaises(ValueError, math.log2, NINF)
        self.assertTrue(math.isnan(math.log2(NAN)))

        # test of specializations
        self.ftest('log2(MyFloat())', math.log2(MyFloat()), -0.7369655941662062)
        self.assertRaises(TypeError, math.log2, 'ahoj')
        self.ftest('log2(BIG_INT)', math.log2(BIG_INT), 102.97976984980635)

    def testLog2Exact(self):
        # Check that we get exact equality for log2 of powers of 2.
        actual = [math.log2(math.ldexp(1.0, n)) for n in range(-1074, 1024)]
        expected = [float(n) for n in range(-1074, 1024)]
        self.assertEqual(actual, expected)

    def testLog10(self):
        self.assertRaises(TypeError, math.log10)
        self.ftest('log10(0.1)', math.log10(0.1), -1)
        self.ftest('log10(1)', math.log10(1), 0)
        self.ftest('log10(10)', math.log10(10), 1)
        self.ftest('log10(10**1000)', math.log10(10**1000), 1000.0)
        self.assertRaises(ValueError, math.log10, -1.5)
        self.assertRaises(ValueError, math.log10, -10**1000)
        self.assertRaises(ValueError, math.log10, NINF)
        self.assertEqual(math.log(INF), INF)
        self.assertTrue(math.isnan(math.log10(NAN)))

        # test of specializations
        self.ftest('log10(MyFloat())', math.log10(MyFloat()), -0.22184874961635637)
        self.assertRaises(TypeError, math.log10, 'ahoj')
        self.ftest('log10(BIG_INT)', math.log10(BIG_INT), 30.999999671364986)

    def testIsfinite(self):
        self.assertTrue(math.isfinite(0.0))
        self.assertTrue(math.isfinite(-0.0))
        self.assertTrue(math.isfinite(1.0))
        self.assertTrue(math.isfinite(-1.0))
        self.assertFalse(math.isfinite(float("nan")))
        self.assertFalse(math.isfinite(float("inf")))
        self.assertFalse(math.isfinite(float("-inf")))

        self.assertTrue(math.isfinite(True))
        self.assertTrue(math.isfinite(LONG_INT))
        self.assertTrue(math.isfinite(BIG_INT))
        self.assertRaises(TypeError, math.isfinite, 'ahoj')
        self.assertTrue(math.isfinite(MyFloat()))

    def testIsinf(self):
        self.assertTrue(math.isinf(float("inf")))
        self.assertTrue(math.isinf(float("-inf")))
        self.assertTrue(math.isinf(1E400))
        self.assertTrue(math.isinf(-1E400))
        self.assertFalse(math.isinf(float("nan")))
        self.assertFalse(math.isinf(0.))
        self.assertFalse(math.isinf(1.))
        
        self.assertFalse(math.isinf(True))
        self.assertFalse(math.isinf(LONG_INT))
        self.assertFalse(math.isinf(BIG_INT))
        self.assertRaises(TypeError, math.isinf, 'ahoj')
        self.assertFalse(math.isinf(MyFloat()))

    def test_ceil_basic(self):
        self.assertEqual(math.ceil(10), 10)
        self.assertEqual(math.ceil(-10), -10)
        self.assertEqual(math.ceil(10.1), 11)
        self.assertEqual(math.ceil(-10.1), -10)
        self.assertEqual(math.ceil(True), 1)
        self.assertEqual(math.ceil(False), 0)
        self.assertEqual(math.ceil(999999999999), 999999999999)
        self.assertEqual(math.ceil(999999999999999999999999), 999999999999999999999999)

    def test_ceil_float(self):
        self.assertEqual(math.ceil(999.1), 1000)
        self.assertEqual(math.ceil(999.0), 999)
        self.assertEqual(math.ceil(99999999999999.9), 100000000000000)
        self.assertEqual(math.ceil(9999999999999999999999.99999999999999989), 10000000000000000000000)

    def test_ceil_classes_int(self):
        class I(int):
            def m1():
                return 'Just a fake method';

        class I2(int):
            def __ceil__(self):
                return 11

        class I3(int):
            def __ceil__(self):
                return 'hello'

        self.assertEqual(math.ceil(I(22)), 22)
        self.assertEqual(math.ceil(I2(256)), 11)
        self.assertEqual(math.ceil(I(156)), 156)
        self.assertEqual(math.ceil(I2(777)), 11)
        self.assertEqual(math.ceil(I3(88)), 'hello')
        self.assertEqual(math.ceil(999.1), 1000)

    def test_ceil_classes_float(self):

        class F(float):
            def m1():
                return 'Just a fake method';

        class F2(float):
            def __ceil__(self):
                return 22.3

        self.assertEqual(math.ceil(F(4.5)), 5)
        self.assertEqual(math.ceil(F2(11.8)), 22.3)
        self.assertEqual(math.ceil(F(4.1)), 5)
        self.assertEqual(math.ceil(F2(11)), 22.3)
        self.assertEqual(math.ceil(999.1), 1000)

    def test_ceil_classes_general(self):

        class O:
            def __ceil__(self):
                return 'cau'

        self.assertRaises(TypeError, math.ceil, 'Word')
        self.assertEqual(math.ceil(O()), 'cau')
        self.assertRaises(TypeError, math.ceil, '1.2')
        self.assertEqual(math.ceil(O()), 'cau')
        self.assertEqual(math.ceil(999.1), 1000)

        self.assertEqual(math.ceil(MyFloat()),1)

        class F1():
            def __float__(self):
                return 1.1
            def __ceil__(self):
                return 44
        self.assertEqual(math.ceil(F1()), 44)

        class F2():
            def __float__(self):
                return 1.1
        self.assertEqual(math.ceil(F2()), 2)

    def test_basic_copysign(self):
        self.assertEqual(math.copysign(3, -0), 3.0)
        self.assertEqual(math.copysign(1, 42), 1.0)
        self.assertEqual(math.copysign(0., 42),  0.0)
        self.assertEqual(math.copysign(1., -42), -1.0)
        self.assertEqual(math.copysign(3, 0.), 3.0)
        self.assertEqual(math.copysign(4., -0.), -4.0)
        self.assertEqual(math.copysign(999999999, 1), 999999999)
        self.assertEqual(math.copysign(999999999999, 1), 999999999999)
        self.assertEqual(math.copysign(999999999999999, 1), 999999999999999)
        self.assertEqual(math.copysign(999999999999999999, 1), 1e+18)
        self.assertEqual(math.copysign(999999999999999999999, 1), 1e+21)
        self.assertEqual(math.copysign(9999999999999999999999999999999, 1), 1e+31)
        self.assertEqual(math.copysign(9999999999999999999999999999999, 1.0), 1e+31)
        self.assertEqual(math.copysign(999999999999999999999.1, 1), 999999999999999999999.1)
        self.assertRaises(TypeError, math.copysign, 'hello', 1)
        self.assertRaises(TypeError, math.copysign, 1, 'hello')
        
        self.assertEqual(math.copysign(MyFloat(), 1), 0.6)
        self.assertEqual(math.copysign(MyFloat(), -1), -0.6)
        self.assertEqual(math.copysign(1.2, MyFloat()), 1.2)
        self.assertEqual(math.copysign(MyFloat(), MyFloat()), 0.6)

    def test_inf_copysign(self):
        self.assertEqual(math.copysign(1.0, float('inf')), 1.0)
        self.assertEqual(math.copysign(1.0, float('-inf')), -1.0)

        self.assertEqual(math.copysign(1., 0.), 1.)
        self.assertEqual(math.copysign(1., -0.), -1.)
        self.assertEqual(math.copysign(INF, 0.), INF)
        self.assertEqual(math.copysign(INF, -0.), NINF)
        self.assertEqual(math.copysign(NINF, 0.), INF)
        self.assertEqual(math.copysign(NINF, -0.), NINF)

        self.assertEqual(math.copysign(1., INF), 1.)
        self.assertEqual(math.copysign(1., NINF), -1.)
        self.assertEqual(math.copysign(INF, INF), INF)
        self.assertEqual(math.copysign(INF, NINF), NINF)
        self.assertEqual(math.copysign(NINF, INF), INF)
        self.assertEqual(math.copysign(NINF, NINF), NINF)

    def test_nan_copysign(self):
        self.assertEqual(math.copysign(1.0, float('nan')), 1.0)
        # TODO This test fails due GR-8436
        #self.assertEqual(math.copysign(1.0, float('-nan')), -1.0)
        # TODO isnan is not implemented yet, uncoment when GR-8440
        self.assertTrue(math.isnan(math.copysign(NAN, 1.)))
        self.assertTrue(math.isnan(math.copysign(NAN, INF)))
        self.assertTrue(math.isnan(math.copysign(NAN, NINF)))
        self.assertTrue(math.isnan(math.copysign(NAN, NAN)))

    def test_isnan(self):
        self.assertTrue(math.isnan(float("nan")))
        # TODO This test fails due GR-8436
        #self.assertTrue(math.isnan(float("-nan")))
        self.assertTrue(math.isnan(float("inf") * 0.))
        self.assertFalse(math.isnan(float("inf")))
        self.assertFalse(math.isnan(0.))
        self.assertFalse(math.isnan(1.))
        self.assertFalse(math.isnan(99999999999999999999999999999999999))
        self.assertFalse(math.isnan(9999999999999999999.9999999999))
        self.assertFalse(math.isnan(True))

        self.assertRaises(TypeError, math.isnan, 'hello')

        self.assertFalse(math.isnan(False))
        self.assertFalse(math.isnan(MyFloat()))

    def testPow(self):
        self.assertRaises(TypeError, math.pow)
        self.ftest('pow(0,1)', math.pow(0,1), 0)
        self.ftest('pow(1,0)', math.pow(1,0), 1)
        self.ftest('pow(2,1)', math.pow(2,1), 2)
        self.ftest('pow(2,-1)', math.pow(2,-1), 0.5)
        self.assertEqual(math.pow(INF, 1), INF)
        self.assertEqual(math.pow(NINF, 1), NINF)
        self.assertEqual((math.pow(1, INF)), 1.)
        self.assertEqual((math.pow(1, NINF)), 1.)
        self.assertTrue(math.isnan(math.pow(NAN, 1)))
        self.assertTrue(math.isnan(math.pow(2, NAN)))
        self.assertTrue(math.isnan(math.pow(0, NAN)))
        self.assertEqual(math.pow(1, NAN), 1)

        # pow(0., x)
        self.assertEqual(math.pow(0., INF), 0.)
        self.assertEqual(math.pow(0., 3.), 0.)
        self.assertEqual(math.pow(0., 2.3), 0.)
        self.assertEqual(math.pow(0., 2.), 0.)
        self.assertEqual(math.pow(0., 0.), 1.)
        self.assertEqual(math.pow(0., -0.), 1.)
        self.assertRaises(ValueError, math.pow, 0., -2.)
        self.assertRaises(ValueError, math.pow, 0., -2.3)
        self.assertRaises(ValueError, math.pow, 0., -3.)
        self.assertRaises(ValueError, math.pow, 0., NINF)
        self.assertTrue(math.isnan(math.pow(0., NAN)))

        # pow(INF, x)
        self.assertEqual(math.pow(INF, INF), INF)
        self.assertEqual(math.pow(INF, 3.), INF)
        self.assertEqual(math.pow(INF, 2.3), INF)
        self.assertEqual(math.pow(INF, 2.), INF)
        self.assertEqual(math.pow(INF, 0.), 1.)
        self.assertEqual(math.pow(INF, -0.), 1.)
        self.assertEqual(math.pow(INF, -2.), 0.)
        self.assertEqual(math.pow(INF, -2.3), 0.)
        self.assertEqual(math.pow(INF, -3.), 0.)
        self.assertEqual(math.pow(INF, NINF), 0.)
        self.assertTrue(math.isnan(math.pow(INF, NAN)))

        # pow(-0., x)
        self.assertEqual(math.pow(-0., INF), 0.)
        self.assertEqual(math.pow(-0., 3.), -0.)
        self.assertEqual(math.pow(-0., 2.3), 0.)
        self.assertEqual(math.pow(-0., 2.), 0.)
        self.assertEqual(math.pow(-0., 0.), 1.)
        self.assertEqual(math.pow(-0., -0.), 1.)
        self.assertRaises(ValueError, math.pow, -0., -2.)
        self.assertRaises(ValueError, math.pow, -0., -2.3)
        self.assertRaises(ValueError, math.pow, -0., -3.)
        self.assertRaises(ValueError, math.pow, -0., NINF)
        self.assertTrue(math.isnan(math.pow(-0., NAN)))

        # pow(NINF, x)
        self.assertEqual(math.pow(NINF, INF), INF)
        self.assertEqual(math.pow(NINF, 3.), NINF)
        self.assertEqual(math.pow(NINF, 2.3), INF)
        self.assertEqual(math.pow(NINF, 2.), INF)
        self.assertEqual(math.pow(NINF, 0.), 1.)
        self.assertEqual(math.pow(NINF, -0.), 1.)
        self.assertEqual(math.pow(NINF, -2.), 0.)
        self.assertEqual(math.pow(NINF, -2.3), 0.)
        self.assertEqual(math.pow(NINF, -3.), -0.)
        self.assertEqual(math.pow(NINF, NINF), 0.)
        self.assertTrue(math.isnan(math.pow(NINF, NAN)))

        # pow(-1, x)
        self.assertEqual(math.pow(-1., INF), 1.)
        self.assertEqual(math.pow(-1., 3.), -1.)
        self.assertRaises(ValueError, math.pow, -1., 2.3)
        self.assertEqual(math.pow(-1., 2.), 1.)
        self.assertEqual(math.pow(-1., 0.), 1.)
        self.assertEqual(math.pow(-1., -0.), 1.)
        self.assertEqual(math.pow(-1., -2.), 1.)
        self.assertRaises(ValueError, math.pow, -1., -2.3)
        self.assertEqual(math.pow(-1., -3.), -1.)
        self.assertEqual(math.pow(-1., NINF), 1.)
        self.assertTrue(math.isnan(math.pow(-1., NAN)))

        # pow(1, x)
        self.assertEqual(math.pow(1., INF), 1.)
        self.assertEqual(math.pow(1., 3.), 1.)
        self.assertEqual(math.pow(1., 2.3), 1.)
        self.assertEqual(math.pow(1., 2.), 1.)
        self.assertEqual(math.pow(1., 0.), 1.)
        self.assertEqual(math.pow(1., -0.), 1.)
        self.assertEqual(math.pow(1., -2.), 1.)
        self.assertEqual(math.pow(1., -2.3), 1.)
        self.assertEqual(math.pow(1., -3.), 1.)
        self.assertEqual(math.pow(1., NINF), 1.)
        self.assertEqual(math.pow(1., NAN), 1.)

        # pow(x, 0) should be 1 for any x
        self.assertEqual(math.pow(2.3, 0.), 1.)
        self.assertEqual(math.pow(-2.3, 0.), 1.)
        self.assertEqual(math.pow(NAN, 0.), 1.)
        self.assertEqual(math.pow(2.3, -0.), 1.)
        self.assertEqual(math.pow(-2.3, -0.), 1.)
        self.assertEqual(math.pow(NAN, -0.), 1.)

        # pow(x, y) is invalid if x is negative and y is not integral
        self.assertRaises(ValueError, math.pow, -1., 2.3)
        self.assertRaises(ValueError, math.pow, -15., -3.1)

        # pow(x, NINF)
        self.assertEqual(math.pow(1.9, NINF), 0.)
        self.assertEqual(math.pow(1.1, NINF), 0.)
        self.assertEqual(math.pow(0.9, NINF), INF)
        self.assertEqual(math.pow(0.1, NINF), INF)
        self.assertEqual(math.pow(-0.1, NINF), INF)
        self.assertEqual(math.pow(-0.9, NINF), INF)
        self.assertEqual(math.pow(-1.1, NINF), 0.)
        self.assertEqual(math.pow(-1.9, NINF), 0.)

        # pow(x, INF)
        self.assertEqual(math.pow(1.9, INF), INF)
        self.assertEqual(math.pow(1.1, INF), INF)
        self.assertEqual(math.pow(0.9, INF), 0.)
        self.assertEqual(math.pow(0.1, INF), 0.)
        self.assertEqual(math.pow(-0.1, INF), 0.)
        self.assertEqual(math.pow(-0.9, INF), 0.)
        self.assertEqual(math.pow(-1.1, INF), INF)
        self.assertEqual(math.pow(-1.9, INF), INF)

        # pow(x, y) should work for x negative, y an integer
        self.ftest('(-2.)**3.', math.pow(-2.0, 3.0), -8.0)
        self.ftest('(-2.)**2.', math.pow(-2.0, 2.0), 4.0)
        self.ftest('(-2.)**1.', math.pow(-2.0, 1.0), -2.0)
        self.ftest('(-2.)**0.', math.pow(-2.0, 0.0), 1.0)
        self.ftest('(-2.)**-0.', math.pow(-2.0, -0.0), 1.0)
        self.ftest('(-2.)**-1.', math.pow(-2.0, -1.0), -0.5)
        self.ftest('(-2.)**-2.', math.pow(-2.0, -2.0), 0.25)
        self.ftest('(-2.)**-3.', math.pow(-2.0, -3.0), -0.125)
        self.assertRaises(ValueError, math.pow, -2.0, -0.5)
        self.assertRaises(ValueError, math.pow, -2.0, 0.5)

        self.assertRaises(OverflowError, math.pow, 999999999999999999999999999, 999999999999999999999999999)

        # testing specializations
        self.assertEqual(math.pow(0, 999999999999999999999999999), 0)
        self.assertEqual(math.pow(999999999999999999999999999, 0), 1)
        self.assertEqual(math.pow(0.0, 999999999999999999999999999), 0)
        self.assertEqual(math.pow(999999999999999999999999999, 0.0), 1)
        
        class MyNumber():
            def __float__(self):
                return -2.;
        self.ftest('MyFloat()**-3.', math.pow(MyNumber(), -3.0), -0.125)
    
    def testAtan2(self):
        self.assertRaises(TypeError, math.atan2)
        self.ftest('atan2(-1, 0)', math.atan2(-1, 0), -math.pi/2)
        self.ftest('atan2(-1, 1)', math.atan2(-1, 1), -math.pi/4)
        self.ftest('atan2(0, 1)', math.atan2(0, 1), 0)
        self.ftest('atan2(1, 1)', math.atan2(1, 1), math.pi/4)
        self.ftest('atan2(1, 0)', math.atan2(1, 0), math.pi/2)

        # math.atan2(0, x)
        self.ftest('atan2(0., -inf)', math.atan2(0., NINF), math.pi)
        self.ftest('atan2(0., -2.3)', math.atan2(0., -2.3), math.pi)
        self.ftest('atan2(0., -0.)', math.atan2(0., -0.), math.pi)
        self.assertEqual(math.atan2(0., 0.), 0.)
        self.assertEqual(math.atan2(0., 2.3), 0.)
        self.assertEqual(math.atan2(0., INF), 0.)
        self.assertTrue(math.isnan(math.atan2(0., NAN)))
        # math.atan2(-0, x)
        self.ftest('atan2(-0., -inf)', math.atan2(-0., NINF), -math.pi)
        self.ftest('atan2(-0., -2.3)', math.atan2(-0., -2.3), -math.pi)
        self.ftest('atan2(-0., -0.)', math.atan2(-0., -0.), -math.pi)
        self.assertEqual(math.atan2(-0., 0.), -0.)
        self.assertEqual(math.atan2(-0., 2.3), -0.)
        self.assertEqual(math.atan2(-0., INF), -0.)
        self.assertTrue(math.isnan(math.atan2(-0., NAN)))
        # math.atan2(INF, x)
        self.ftest('atan2(inf, -inf)', math.atan2(INF, NINF), math.pi*3/4)
        self.ftest('atan2(inf, -2.3)', math.atan2(INF, -2.3), math.pi/2)
        self.ftest('atan2(inf, -0.)', math.atan2(INF, -0.0), math.pi/2)
        self.ftest('atan2(inf, 0.)', math.atan2(INF, 0.0), math.pi/2)
        self.ftest('atan2(inf, 2.3)', math.atan2(INF, 2.3), math.pi/2)
        self.ftest('atan2(inf, inf)', math.atan2(INF, INF), math.pi/4)
        self.assertTrue(math.isnan(math.atan2(INF, NAN)))
        # math.atan2(NINF, x)
        self.ftest('atan2(-inf, -inf)', math.atan2(NINF, NINF), -math.pi*3/4)
        self.ftest('atan2(-inf, -2.3)', math.atan2(NINF, -2.3), -math.pi/2)
        self.ftest('atan2(-inf, -0.)', math.atan2(NINF, -0.0), -math.pi/2)
        self.ftest('atan2(-inf, 0.)', math.atan2(NINF, 0.0), -math.pi/2)
        self.ftest('atan2(-inf, 2.3)', math.atan2(NINF, 2.3), -math.pi/2)
        self.ftest('atan2(-inf, inf)', math.atan2(NINF, INF), -math.pi/4)
        self.assertTrue(math.isnan(math.atan2(NINF, NAN)))
        # math.atan2(+finite, x)
        self.ftest('atan2(2.3, -inf)', math.atan2(2.3, NINF), math.pi)
        self.ftest('atan2(2.3, -0.)', math.atan2(2.3, -0.), math.pi/2)
        self.ftest('atan2(2.3, 0.)', math.atan2(2.3, 0.), math.pi/2)
        self.assertEqual(math.atan2(2.3, INF), 0.)
        self.assertTrue(math.isnan(math.atan2(2.3, NAN)))
        # math.atan2(-finite, x)
        self.ftest('atan2(-2.3, -inf)', math.atan2(-2.3, NINF), -math.pi)
        self.ftest('atan2(-2.3, -0.)', math.atan2(-2.3, -0.), -math.pi/2)
        self.ftest('atan2(-2.3, 0.)', math.atan2(-2.3, 0.), -math.pi/2)
        self.assertEqual(math.atan2(-2.3, INF), -0.)
        self.assertTrue(math.isnan(math.atan2(-2.3, NAN)))
        # math.atan2(NAN, x)
        self.assertTrue(math.isnan(math.atan2(NAN, NINF)))
        self.assertTrue(math.isnan(math.atan2(NAN, -2.3)))
        self.assertTrue(math.isnan(math.atan2(NAN, -0.)))
        self.assertTrue(math.isnan(math.atan2(NAN, 0.)))
        self.assertTrue(math.isnan(math.atan2(NAN, 2.3)))
        self.assertTrue(math.isnan(math.atan2(NAN, INF)))
        self.assertTrue(math.isnan(math.atan2(NAN, NAN)))

        # Testing specializations
        self.ftest('atan2(0.5,1)', math.atan2(0.5,1), 0.4636476090008061)
        self.ftest('atan2(1,0.5)', math.atan2(1,0.5), 1.1071487177940904)
        self.ftest('atan2(BIG_INT,BIG_INT)', math.atan2(BIG_INT,BIG_INT), 0.7853981633974483)
        self.ftest('atan2(BIG_INT,1)', math.atan2(BIG_INT,1), 1.5707963267948966)
        self.ftest('atan2(BIG_INT,0.1)', math.atan2(BIG_INT,0.1), 1.5707963267948966)
        self.ftest('atan2(MyFloat(),MyFloat())', math.atan2(MyFloat(),MyFloat()), 0.7853981633974483)
        self.ftest('atan2(BIG_INT,MyFloat())', math.atan2(BIG_INT,MyFloat()), 1.5707963267948966)

    def testCos(self):
        self.assertRaises(TypeError, math.cos)
        self.ftest('cos(-pi/2)', math.cos(-math.pi/2), 0, abs_tol=ulp(1))
        self.ftest('cos(0)', math.cos(0), 1)
        self.ftest('cos(pi/2)', math.cos(math.pi/2), 0, abs_tol=ulp(1))
        self.ftest('cos(pi)', math.cos(math.pi), -1)
        try:
            self.assertTrue(math.isnan(math.cos(INF)))
            self.assertTrue(math.isnan(math.cos(NINF)))
        except ValueError:
            self.assertRaises(ValueError, math.cos, INF)
            self.assertRaises(ValueError, math.cos, NINF)
        self.assertTrue(math.isnan(math.cos(NAN)))
 
        #test of specializations
        self.ftest('cos(BIG_INT)', math.cos(BIG_INT), 0.4145587418469303)
        self.ftest('cos(MyFloat())', math.cos(MyFloat()), 0.8253356149096783)
        self.assertRaises(TypeError, math.cos, 'ahoj')

    def testCosh(self):
        self.assertRaises(TypeError, math.cosh)
        self.ftest('cosh(0)', math.cosh(0), 1)
        self.ftest('cosh(2)-2*cosh(1)**2', math.cosh(2)-2*math.cosh(1)**2, -1) # Thanks to Lambert
        self.assertEqual(math.cosh(INF), INF)
        self.assertEqual(math.cosh(NINF), INF)
        self.assertTrue(math.isnan(math.cosh(NAN)))

        # test of specializations
        self.ftest('cosh(MyFloat())', math.cosh(MyFloat()), 1.1854652182422676)
        self.assertRaises(TypeError, math.cosh, 'ahoj')
        self.assertRaises(OverflowError, math.cosh, BIG_INT)
        
    def testSin(self):
        self.assertRaises(TypeError, math.sin)
        self.ftest('sin(0)', math.sin(0), 0)
        self.ftest('sin(pi/2)', math.sin(math.pi/2), 1)
        self.ftest('sin(-pi/2)', math.sin(-math.pi/2), -1)
        try:
            self.assertTrue(math.isnan(math.sin(INF)))
            self.assertTrue(math.isnan(math.sin(NINF)))
        except ValueError:
            self.assertRaises(ValueError, math.sin, INF)
            self.assertRaises(ValueError, math.sin, NINF)
        self.assertTrue(math.isnan(math.sin(NAN)))

        # test of specializations
        self.ftest('sin(MyFloat())', math.sin(MyFloat()), 0.5646424733950354)
        self.assertRaises(TypeError, math.sin, 'ahoj')
        self.ftest('sin(MyFloat())', math.sin(BIG_INT), -0.9100225544228506)

    def testSinh(self):
        self.assertRaises(TypeError, math.sinh)
        self.ftest('sinh(0)', math.sinh(0), 0)
        self.ftest('sinh(1)**2-cosh(1)**2', math.sinh(1)**2-math.cosh(1)**2, -1)
        self.ftest('sinh(1)+sinh(-1)', math.sinh(1)+math.sinh(-1), 0)
        self.assertEqual(math.sinh(INF), INF)
        self.assertEqual(math.sinh(NINF), NINF)
        self.assertTrue(math.isnan(math.sinh(NAN)))
        
        # test of specializations
        self.ftest('sinh(MyFloat())', math.sinh(MyFloat()), 0.6366535821482412)
        self.assertRaises(TypeError, math.sinh, 'ahoj')
        self.assertRaises(OverflowError, math.sinh, BIG_INT)

    def testTan(self):
        self.assertRaises(TypeError, math.tan)
        self.ftest('tan(0)', math.tan(0), 0)
        self.ftest('tan(pi/4)', math.tan(math.pi/4), 1)
        self.ftest('tan(-pi/4)', math.tan(-math.pi/4), -1)
        try:
            self.assertTrue(math.isnan(math.tan(INF)))
            self.assertTrue(math.isnan(math.tan(NINF)))
        except:
            self.assertRaises(ValueError, math.tan, INF)
            self.assertRaises(ValueError, math.tan, NINF)
        self.assertTrue(math.isnan(math.tan(NAN)))

        # test of specializations
        self.ftest('tan(MyFloat())', math.tan(MyFloat()), 0.6841368083416923)
        self.assertRaises(TypeError, math.tan, 'ahoj')
        self.ftest('tan(BIG_INT)', math.tan(BIG_INT), -2.1951594854049974)

    def testTanh(self):
        self.assertRaises(TypeError, math.tanh)
        self.ftest('tanh(0)', math.tanh(0), 0)
        self.ftest('tanh(1)+tanh(-1)', math.tanh(1)+math.tanh(-1), 0, abs_tol=ulp(1))
        self.ftest('tanh(inf)', math.tanh(INF), 1)
        self.ftest('tanh(-inf)', math.tanh(NINF), -1)
        self.assertTrue(math.isnan(math.tanh(NAN)))

        # test of specializations
        self.ftest('tanh(MyFloat())', math.tanh(MyFloat()), 0.5370495669980353)
        self.assertRaises(TypeError, math.tanh, 'ahoj')
        self.ftest('tanh(BIG_INT)', math.tanh(BIG_INT), 1.0)

    def testAsinh(self):
        self.assertRaises(TypeError, math.asinh)
        self.ftest('asinh(0)', math.asinh(0), 0)
        self.ftest('asinh(1)', math.asinh(1), 0.88137358701954305)
        self.ftest('asinh(-1)', math.asinh(-1), -0.88137358701954305)
        self.assertEqual(math.asinh(INF), INF)
        self.assertEqual(math.asinh(NINF), NINF)
        self.assertTrue(math.isnan(math.asinh(NAN)))

        # test of specializations
        self.ftest('asinh(MyFloat())', math.asinh(MyFloat()), 0.5688248987322475)
        self.assertRaises(TypeError, math.asinh, 'ahoj')
        self.ftest('asinh(BIG_INT)', math.asinh(BIG_INT), 72.07328430666527)

    def testAtan(self):
        self.assertRaises(TypeError, math.atan)
        self.ftest('atan(-1)', math.atan(-1), -math.pi/4)
        self.ftest('atan(0)', math.atan(0), 0)
        self.ftest('atan(1)', math.atan(1), math.pi/4)
        self.ftest('atan(inf)', math.atan(INF), math.pi/2)
        self.ftest('atan(-inf)', math.atan(NINF), -math.pi/2)
        self.assertTrue(math.isnan(math.atan(NAN)))

        # test of specializations
        self.ftest('atan(MyFloat())', math.atan(MyFloat()), 0.5404195002705842)
        self.assertRaises(TypeError, math.atan, 'ahoj')
        self.ftest('atan(BIG_INT)', math.atan(BIG_INT), 1.5707963267948966)

    def testAtanh(self):
        self.assertRaises(TypeError, math.atan)
        self.ftest('atanh(0)', math.atanh(0), 0)
        self.ftest('atanh(0.5)', math.atanh(0.5), 0.54930614433405489)
        #self.ftest('atanh(-0.5)', math.atanh(-0.5), -0.54930614433405489)
        self.assertRaises(ValueError, math.atanh, 1)
        self.assertRaises(ValueError, math.atanh, -1)
        self.assertRaises(ValueError, math.atanh, INF)
        self.assertRaises(ValueError, math.atanh, NINF)
        self.assertTrue(math.isnan(math.atanh(NAN)))

        # test of specializations
        self.ftest('atanh(MyFloat())', math.atanh(MyFloat()), 0.6931471805599453)
        self.assertRaises(TypeError, math.atanh, 'ahoj')
        self.assertRaises(ValueError, math.atanh, BIG_INT)

    def testHypot(self):
        self.assertRaises(TypeError, math.hypot)
        self.ftest('hypot(0,0)', math.hypot(0,0), 0)
        self.ftest('hypot(3,4)', math.hypot(3,4), 5)
        self.assertEqual(math.hypot(NAN, INF), INF)
        self.assertEqual(math.hypot(INF, NAN), INF)
        self.assertEqual(math.hypot(NAN, NINF), INF)
        self.assertEqual(math.hypot(NINF, NAN), INF)
        self.assertRaises(OverflowError, math.hypot, FLOAT_MAX, FLOAT_MAX)
        self.assertTrue(math.isnan(math.hypot(1.0, NAN)))
        self.assertTrue(math.isnan(math.hypot(NAN, -2.0)))

        self.assertEqual(math.hypot(NINF, 1), INF)
        self.assertEqual(math.hypot(INF, 1), INF)
        self.assertEqual(math.hypot(1, INF), INF)
        self.assertEqual(math.hypot(1, NINF), INF)

        self.ftest('math.hypot(MyFloat(), MyFloat())', math.hypot(MyFloat(), MyFloat()), 0.848528137423857)
        self.ftest('math.hypot(BIG_INT, BIG_INT)', math.hypot(BIG_INT, BIG_INT), 1.4142124922238343e+31)
        self.assertRaises(TypeError, math.hypot, 'ahoj', 1)
        self.assertRaises(TypeError, math.hypot, 1, 'cau')

    def test_fabs(self):
        self.assertEqual(math.fabs(-1), 1)
        self.assertEqual(math.fabs(0), 0)
        self.assertEqual(math.fabs(1), 1)
        self.assertRaises(TypeError, math.fabs, 'string')
        self.assertEqual(math.fabs(99999999999999999), 1e+17)
        self.assertEqual(math.fabs(999999999999999999999999999), 1e+27)
        self.assertEqual(math.fabs(999999999999999999999999999.123456123456), 1e+27)
        self.assertEqual(math.fabs(True), 1.0)
        self.assertEqual(math.fabs(False), 0.0)

        self.assertEqual(math.fabs(MyFloat()), 0.6)

    def test_factorial(self):
        self.assertRaises(ValueError, math.factorial, float('nan'))
        self.assertRaises(ValueError, math.factorial, float('inf'))
        self.assertRaises(ValueError, math.factorial, float('-inf'))
        self.assertEqual(math.factorial(0), 1)
        self.assertEqual(math.factorial(0.0), 1)
        self.assertEqual(math.factorial(True), 1)
        self.assertEqual(math.factorial(False), 1)
        total = 1
        for i in range(1, 1000):
            total *= i
            self.assertEqual(math.factorial(i), total)
            self.assertEqual(math.factorial(float(i)), total)
            self.assertEqual(math.factorial(i), py_factorial(i))
        self.assertRaises(ValueError, math.factorial, -1)
        self.assertRaises(ValueError, math.factorial, -1.0)
        if (sys.version_info.major >= 3 and sys.version_info.minor >= 6):
            # these tests are failing in python 3.4.1
            self.assertRaises(ValueError, math.factorial, -10**100)
            self.assertRaises(ValueError, math.factorial, -1e100)
        self.assertRaises(ValueError, math.factorial, math.pi)
        self.assertRaises(TypeError, math.factorial, 'hello')
        self.assertEqual(math.factorial(13), 6227020800)
        self.assertEqual(math.factorial(30), 265252859812191058636308480000000)
        self.assertRaises(ValueError, math.factorial, -11.1)
        
        self.assertEqual(math.factorial(MyInt(4)), 24)
        self.assertEqual(math.factorial(MyInt(True)), 1)
        self.assertRaises(TypeError, math.factorial, MyIndexable(4))
        self.assertRaises(TypeError, math.factorial, MyFloat())
        self.assertRaises(TypeError, math.factorial, MyInt(0.6))

    def testGcd(self):
        if (sys.version_info.major >= 3 and sys.version_info.minor >= 5):
            gcd = math.gcd
            self.assertEqual(gcd(0, 0), 0)
            self.assertEqual(gcd(1, 0), 1)
            self.assertEqual(gcd(-1, 0), 1)
            self.assertEqual(gcd(0, 1), 1)
            self.assertEqual(gcd(0, -1), 1)
            self.assertEqual(gcd(7, 1), 1)
            self.assertEqual(gcd(7, -1), 1)
            self.assertEqual(gcd(-23, 15), 1)
            self.assertEqual(gcd(120, 84), 12)
            self.assertEqual(gcd(84, -120), 12)
            self.assertEqual(gcd(1216342683557601535506311712,
                                 436522681849110124616458784), 32)
            c = 652560
            x = 434610456570399902378880679233098819019853229470286994367836600566
            y = 1064502245825115327754847244914921553977
            a = x * c
            b = y * c
            self.assertEqual(gcd(a, b), c)
            self.assertEqual(gcd(b, a), c)
            self.assertEqual(gcd(-a, b), c)
            self.assertEqual(gcd(b, -a), c)
            self.assertEqual(gcd(a, -b), c)
            self.assertEqual(gcd(-b, a), c)
            self.assertEqual(gcd(-a, -b), c)
            self.assertEqual(gcd(-b, -a), c)
            c = 576559230871654959816130551884856912003141446781646602790216406874
            a = x * c
            b = y * c
            self.assertEqual(gcd(a, b), c)
            self.assertEqual(gcd(b, a), c)
            self.assertEqual(gcd(-a, b), c)
            self.assertEqual(gcd(b, -a), c)
            self.assertEqual(gcd(a, -b), c)
            self.assertEqual(gcd(-b, a), c)
            self.assertEqual(gcd(-a, -b), c)
            self.assertEqual(gcd(-b, -a), c)

            self.assertRaises(TypeError, gcd, 120.0, 84)
            self.assertRaises(TypeError, gcd, 120, 84.0)
            self.assertEqual(gcd(MyIndexable(120), MyIndexable(84)), 12)

            # test of specializations
            self.assertRaises(TypeError, gcd, 120, MyIndexable(6.0))
            self.assertRaises(TypeError, gcd, 'ahoj', 1)
            self.assertEqual(gcd(MyIndexable(True), MyIndexable(84)), 1)

    def test_floor(self):
        class TestFloor:
            def __floor__(self):
                return 42
        class TestNoFloor:
            pass
        self.ftest('floor(TestFloor())', math.floor(TestFloor()), 42)
        self.assertRaises(TypeError, math.floor, TestNoFloor())
        self.assertRaises(TypeError, math.floor)
        self.assertEqual(int, type(math.floor(0.5)))
        self.ftest('floor(0.5)', math.floor(0.5), 0)
        self.ftest('floor(1.0)', math.floor(1.0), 1)
        self.ftest('floor(1.5)', math.floor(1.5), 1)
        self.ftest('floor(-0.5)', math.floor(-0.5), -1)
        self.ftest('floor(-1.0)', math.floor(-1.0), -1)
        self.ftest('floor(-1.5)', math.floor(-1.5), -2)
        # pow() relies on floor() to check for integers
        # This fails on some platforms - so check it here
        self.ftest('floor(1.23e167)', math.floor(1.23e167), 1.23e167)
        self.ftest('floor(-1.23e167)', math.floor(-1.23e167), -1.23e167)
        #self.assertEqual(math.ceil(INF), INF)
        #self.assertEqual(math.ceil(NINF), NINF)
        #self.assertTrue(math.isnan(math.floor(NAN)))

        t = TestNoFloor()
        t.__floor__ = lambda *args: args
        self.assertRaises(TypeError, math.floor, t)
        self.assertRaises(TypeError, math.floor, t, 0)
        self.assertEqual(math.floor(True), 1)
        self.assertEqual(math.floor(False), 0)
        self.assertRaises(TypeError, math.floor, 'hello')
        self.assertEqual(math.floor(2432902008176640000), 2432902008176640000)
        self.assertEqual(math.floor(2432902008176640000999), 2432902008176640000999)
        self.assertEqual(math.floor(2432902008176640000999.99), 2432902008176640000999.99)

        self.assertEqual(math.floor(MyFloat()), 0)

        class MyFloorFloat():
            def __floor__(self):
                return 12
            def __float(self):
                return 112
        self.assertEqual(math.floor(MyFloorFloat()), 12)

    def test_fmod(self):
        self.assertRaises(TypeError, math.fmod)
        self.ftest('fmod(10, 1)', math.fmod(10, 1), 0.0)
        self.ftest('fmod(10, 0.5)', math.fmod(10, 0.5), 0.0)
        self.ftest('fmod(10, 1.5)', math.fmod(10, 1.5), 1.0)
        self.ftest('fmod(-10, 1)', math.fmod(-10, 1), -0.0)
        self.ftest('fmod(-10, 0.5)', math.fmod(-10, 0.5), -0.0)
        self.ftest('fmod(-10, 1.5)', math.fmod(-10, 1.5), -1.0)
        self.assertTrue(math.isnan(math.fmod(NAN, 1.)))
        self.assertTrue(math.isnan(math.fmod(1., NAN)))
        self.assertTrue(math.isnan(math.fmod(NAN, NAN)))
        self.assertRaises(ValueError, math.fmod, 1.0, 0.)
        self.assertRaises(ValueError, math.fmod, 1.0, 0)
        self.assertRaises(ValueError, math.fmod, 1.0, False)
        self.assertRaises(ValueError, math.fmod, 1, 0)
        self.assertRaises(ValueError, math.fmod, 1, 0.0)
        self.assertRaises(ValueError, math.fmod, 1, False)
        self.assertRaises(ValueError, math.fmod, 6227020800, 0)
        self.assertRaises(ValueError, math.fmod, 6227020800, 0.0)
        self.assertRaises(ValueError, math.fmod, 6227020800, False)
        self.assertRaises(ValueError, math.fmod, False, False)
        self.assertRaises(ValueError, math.fmod, False, 0.0)
        self.assertRaises(ValueError, math.fmod, False, 0)
        self.assertRaises(ValueError, math.fmod, INF, 1.)
        self.assertRaises(ValueError, math.fmod, NINF, 1.)
        self.assertRaises(ValueError, math.fmod, INF, 0.)
        self.assertRaises(ValueError, math.fmod, INF, 1)
        self.assertRaises(ValueError, math.fmod, INF, True)
        self.assertRaises(ValueError, math.fmod, INF, 2432902008176640000999)
        self.assertRaises(TypeError, math.fmod, False, 'hello')
        self.assertRaises(TypeError, math.fmod, 'hello', 1.0)
        self.assertRaises(TypeError, math.fmod, 6227020800, 'hello')
        self.assertRaises(TypeError, math.fmod, 'hello', 2432902008176640000999)
        self.assertEqual(math.fmod(3.0, INF), 3.0)
        self.assertEqual(math.fmod(-3.0, INF), -3.0)
        self.assertEqual(math.fmod(3.0, NINF), 3.0)
        self.assertEqual(math.fmod(-3.0, NINF), -3.0)
        self.assertEqual(math.fmod(0.0, 3.0), 0.0)
        self.assertEqual(math.fmod(0.0, NINF), 0.0)
        self.assertEqual(math.fmod(10.1, 1.0), 0.09999999999999964)
        self.assertEqual(math.fmod(10.1, 1), 0.09999999999999964)
        self.assertEqual(math.fmod(10.1, 6227020800), 10.1)
        self.assertEqual(math.fmod(10.1, True), 0.09999999999999964)
        self.assertEqual(math.fmod(10, 1.1), 0.0999999999999992)
        self.assertEqual(math.fmod(10, 3), 1.0)
        self.assertEqual(math.fmod(10, 6227020800), 10.0)
        self.assertEqual(math.fmod(10, True), 0.0)
        self.assertEqual(math.fmod(6227020800, 1.1), 1.0999994972085916)
        self.assertEqual(math.fmod(6227020800, 3), 0.0)
        self.assertEqual(math.fmod(6227020820, 6227020800), 20.0)
        self.assertEqual(math.fmod(6227020800, True), 0.0)
        self.assertEqual(math.fmod(6227020800, 2432902008176640000999), 6227020800.0)
        self.assertEqual(math.fmod(True, 0.1), 0.09999999999999995)
        self.assertEqual(math.fmod(True, 3), 1.0)
        self.assertEqual(math.fmod(True, 6227020800), 1.0)
        self.assertEqual(math.fmod(True, True), 0.0)
        self.assertEqual(math.fmod(10.6, 2432902008176640000999), 10.6)
        self.assertEqual(math.fmod(10.6, float(1.1)), 0.6999999999999988)
        self.assertEqual(math.fmod(24329020081766400009999, 2432902008176640000999), 0.0)
        self.assertEqual(math.fmod(2432902008176640000999, 1), 0.0)
        self.assertEqual(math.fmod(2432902008176640000999, 6227020800), 0.0)
        self.assertEqual(math.fmod(2432902008176640000999, True), 0.0)
        self.assertEqual(math.fmod(2432902008176640000999, 12.12), 10.396369527944033)
        self.assertEqual(math.fmod(-1e-100, 1e100), -1e-100)

        self.assertEqual(math.fmod(MyFloat(), 1), 0.6)
        self.assertEqual(math.fmod(MyFloat(), MyFloat()), 0.)

    def testExp(self):
        self.assertRaises(TypeError, math.exp)
        self.ftest('exp(-1)', math.exp(-1), 1/math.e)
        self.ftest('exp(0)', math.exp(0), 1)
        self.ftest('exp(1)', math.exp(1), math.e)
        self.assertEqual(math.exp(INF), INF)
        self.assertEqual(math.exp(NINF), 0.)
        self.assertTrue(math.isnan(math.exp(NAN)))
        self.assertRaises(OverflowError, math.exp, 1000000)
        
        # test of specializations
        self.ftest('exp(MyFloat())', math.exp(MyFloat()), 1.8221188003905089)
        self.assertRaises(TypeError, math.exp, 'ahoj')
        self.assertRaises(OverflowError, math.exp, BIG_INT)

    def testExpm1(self):
        self.assertRaises(TypeError, math.exp)
        self.ftest('expm1(-1)', math.expm1(-1), 1/math.e-1)
        self.ftest('expm1(0)', math.expm1(0), 0)
        self.ftest('expm1(1)', math.expm1(1), math.e-1)
        self.assertEqual(math.expm1(INF), INF)
        self.assertEqual(math.expm1(NINF), -1.)
        self.assertTrue(math.isnan(math.expm1(NAN)))
        self.assertRaises(OverflowError, math.expm1, 1000000)

        # test of specializations
        self.ftest('expm1(MyFloat())', math.expm1(MyFloat()), 0.8221188003905089)
        self.assertRaises(TypeError, math.expm1, 'ahoj')
        self.assertRaises(OverflowError, math.expm1, BIG_INT)

    def test_frexp(self):
        self.assertRaises(TypeError, math.frexp)

        def testfrexp(name, result, expected):
            (mant, exp), (emant, eexp) = result, expected
            if abs(mant-emant) > eps or exp != eexp:
                self.fail('%s returned %r, expected %r'%\
                          (name, result, expected))

        testfrexp('frexp(-1)', math.frexp(-1), (-0.5, 1))
        testfrexp('frexp(0)', math.frexp(0), (0, 0))
        testfrexp('frexp(1)', math.frexp(1), (0.5, 1))
        testfrexp('frexp(2)', math.frexp(2), (0.5, 2))

        self.assertEqual(math.frexp(INF)[0], INF)
        self.assertEqual(math.frexp(NINF)[0], NINF)
        self.assertTrue(math.isnan(math.frexp(NAN)[0]))

        # test of specializations
        testfrexp('frexp(MyFloat())', math.frexp(MyFloat()), (0.6, 0))
        self.assertRaises(TypeError, math.log1p, 'ahoj')
        testfrexp('log1p(BIG_INT)', math.frexp(BIG_INT), (0.9860753853527933, 103))

        testfrexp('frexp(True)', math.frexp(True), (0.5, 1))
        testfrexp('frexp(False)', math.frexp(False), (0.0, 0))
        testfrexp('frexp(6227020800)', math.frexp(6227020800), (0.7249206304550171, 33))
        testfrexp('frexp(2432902008176640000999)', math.frexp(2432902008176640000999), (0.5151870395916913, 72))
        self.assertRaises(TypeError, math.frexp, 'hello')

        class X(int):
            def getX():
                return 'Ahoj'

        class Y(float):
            def getY():
                return 'Ahoj'

        testfrexp('frexp(X(10))', math.frexp(X(10)), (0.625, 4))
        testfrexp('frexp(Y(11.11))', math.frexp(Y(11.11)), (0.694375, 4))
        testfrexp('frexp(2**1023)', math.frexp(2**1023), (0.5, 1024))
        self.assertRaises(OverflowError, math.frexp, 2**1024)
        testfrexp('frexp(MyFloat())', math.frexp(MyFloat()), (0.6, 0))

    def test_ldexp(self):
        self.assertRaises(TypeError, math.ldexp)
        self.ftest('ldexp(0,1)', math.ldexp(0,1), 0)
        self.ftest('ldexp(1,1)', math.ldexp(1,1), 2)
        self.ftest('ldexp(1,-1)', math.ldexp(1,-1), 0.5)
        self.ftest('ldexp(-1,1)', math.ldexp(-1,1), -2)
        self.assertRaises(OverflowError, math.ldexp, 1., 1000000)
        self.assertRaises(OverflowError, math.ldexp, -1., 1000000)
        self.assertEqual(math.ldexp(1., -1000000), 0.)
        self.assertEqual(math.ldexp(-1., -1000000), -0.)
        self.assertEqual(math.ldexp(INF, 30), INF)
        self.assertEqual(math.ldexp(NINF, -213), NINF)
        self.assertTrue(math.isnan(math.ldexp(NAN, 0)))

        # large second argument
        for n in [10**5, 10**10, 10**20, 10**40]:
            self.assertEqual(math.ldexp(INF, -n), INF)
            self.assertEqual(math.ldexp(NINF, -n), NINF)
            self.assertEqual(math.ldexp(1., -n), 0.)
            self.assertEqual(math.ldexp(-1., -n), -0.)
            self.assertEqual(math.ldexp(0., -n), 0.)
            self.assertEqual(math.ldexp(-0., -n), -0.)
            self.assertTrue(math.isnan(math.ldexp(NAN, -n)))

            self.assertRaises(OverflowError, math.ldexp, 1., n)
            self.assertRaises(OverflowError, math.ldexp, -1., n)
            self.assertEqual(math.ldexp(0., n), 0.)
            self.assertEqual(math.ldexp(-0., n), -0.)
            self.assertEqual(math.ldexp(INF, n), INF)
            self.assertEqual(math.ldexp(NINF, n), NINF)
            self.assertTrue(math.isnan(math.ldexp(NAN, n)))

        self.assertEqual(math.ldexp(24329020081766400009999, 60), 2.8049450438280313e+40)
        self.assertEqual(math.ldexp(-24329020081766400009999, 60), -2.8049450438280313e+40)
        self.assertEqual(math.ldexp(-24329020081766400009999, -60), -21102.061141675676)
        self.assertEqual(math.ldexp(24329020081766400009999, -60), 21102.061141675676)
        self.assertEqual(math.ldexp(True, True), 2)

        class FF(float):
            pass

        class II(int):
            pass
        self.assertEqual(math.ldexp(FF(10), II(12)), 40960.0)
        self.assertRaises(TypeError, math.ldexp, 'Hello', 1000000)
        self.assertRaises(TypeError, math.ldexp, 1, 'Hello')
        self.assertEqual(math.ldexp(7589167167882033, -48), 26.962138008038156)
    
        self.assertRaises(TypeError, math.ldexp, 1, MyIndexable(2))
        self.assertRaises(TypeError, math.ldexp, 1, MyInt(2))
        self.assertRaises(TypeError, math.ldexp, 1, MyFloat())
        self.assertEqual(math.ldexp(0.1, True), 0.2)
        self.assertEqual(math.ldexp(MyFloat(),True), 1.2)
        self.assertRaises(TypeError, math.ldexp, MyInt(2), MyFloat())

    def test_trunc(self):
        self.assertEqual(math.trunc(1), 1)
        self.assertEqual(math.trunc(-1), -1)
        self.assertEqual(type(math.trunc(1)), int)
        self.assertEqual(type(math.trunc(1.5)), int)
        self.assertEqual(math.trunc(1.5), 1)
        self.assertEqual(math.trunc(-1.5), -1)
        self.assertEqual(math.trunc(1.999999), 1)
        self.assertEqual(math.trunc(-1.999999), -1)
        self.assertEqual(math.trunc(-0.999999), -0)
        self.assertEqual(math.trunc(-100.999), -100)

        class TestTrunc(object):
            def __trunc__(self):
                return 23

        class TestNoTrunc(object):
            pass

        self.assertEqual(math.trunc(TestTrunc()), 23)

        self.assertRaises(TypeError, math.trunc)
        self.assertRaises(TypeError, math.trunc, 1, 2)
        self.assertRaises(TypeError, math.trunc, TestNoTrunc())

    def testDegrees(self):
        self.assertRaises(TypeError, math.degrees)
        self.ftest('degrees(pi)', math.degrees(math.pi), 180.0)
        self.ftest('degrees(pi/2)', math.degrees(math.pi/2), 90.0)
        self.ftest('degrees(-pi/4)', math.degrees(-math.pi/4), -45.0)
        self.ftest('degrees(0)', math.degrees(0), 0)

        # test of specializations
        self.ftest('degrees(MyFloat())', math.degrees(MyFloat()), 34.37746770784939)
        self.assertRaises(TypeError, math.degrees, 'ahoj')
        self.ftest('degrees(BIG_INT)', math.degrees(BIG_INT), 5.729573615680451e+32)

    def testRadians(self):
        self.assertRaises(TypeError, math.radians)
        self.ftest('radians(180)', math.radians(180), math.pi)
        self.ftest('radians(90)', math.radians(90), math.pi/2)
        self.ftest('radians(-45)', math.radians(-45), -math.pi/4)
        self.ftest('radians(0)', math.radians(0), 0)

        # test of specializations
        self.ftest('radians(MyFloat())', math.radians(MyFloat()), 0.010471975511965976)
        self.assertRaises(TypeError, math.radians, 'ahoj')
        self.ftest('radians(BIG_INT)', math.radians(BIG_INT), 1.7453279312865818e+29)

    def testModf(self):
        self.assertRaises(TypeError, math.modf)

        def testmodf(name, result, expected):
            (v1, v2), (e1, e2) = result, expected
            if abs(v1-e1) > eps or abs(v2-e2):
                self.fail('%s returned %r, expected %r'%\
                          (name, result, expected))

        testmodf('modf(1.5)', math.modf(1.5), (0.5, 1.0))
        testmodf('modf(-1.5)', math.modf(-1.5), (-0.5, -1.0))

        self.assertEqual(math.modf(INF), (0.0, INF))
        self.assertEqual(math.modf(NINF), (-0.0, NINF))

        modf_nan = math.modf(NAN)
        self.assertTrue(math.isnan(modf_nan[0]))
        self.assertTrue(math.isnan(modf_nan[1]))

        # test of specializations
        testmodf('modf(MyFloat())', math.modf(MyFloat()), (0.6, 0.0))
        self.assertRaises(TypeError, math.modf, 'ahoj')
        testmodf('modf(BIG_INT)', math.modf(BIG_INT), (0.0, 9.999992432902008e+30))

    def executeFnTest(self, values, fn, fnName):
        for value in values:
            result = fn(value[0])
            expected = value[1]
            if math.isnan(expected):
                self.assertTrue(math.isnan(result), "Test2 fail: {}({}) = {}, but was {}".format(fnName, value[0], expected, result))            
            else :
                if result != expected:
                    if (sys.version_info.major >= 3 and sys.version_info.minor >= 5):
                        self.assertTrue(math.isclose(result, expected, rel_tol=1e-13), "Test3 fail: {}({}) = {}, but was {}".format(fnName, value[0], expected, result))

    def test_erf(self):
        erfValues = [(0.0,  0.0), (-0.0, -0.0), (INF,  1.0), (NINF,  -1.0), (NAN, NAN),
            # tiny values
            (1e-308, 1.1283791670955125e-308), (5e-324, 4.9406564584124654e-324), 
            (1e-10, 1.1283791670955126e-10),
            # small integers
            (1, 0.842700792949715), (2, 0.99532226501895271), (3, 0.99997790950300136),
            (4, 0.99999998458274209), (5, 0.99999999999846256), (6, 1.0),
            (-1, -0.842700792949715), (-2, -0.99532226501895271), (-3, -0.99997790950300136),
            (-4, -0.99999998458274209), (-5, -0.99999999999846256), (-6, -1.0),
            # huge values should all go to +/-1, depending on sign
            (-40, -1.0), (1e16, 1.0), (-1e150, -1.0), (1.7e308, 1.0),
            #incorrectly signalled overflow on some platforms.
            (26.2, 1.0), (26.4, 1.0), (26.6, 1.0), (26.8, 1.0), (27.0, 1.0), (27.2, 1.0),
            (27.4, 1.0), (27.6, 1.0), (-26.2, -1.0), (-26.4, -1.0), (-26.6, -1.0),
            (-26.8, -1.0), (-27.0, -1.0), (-27.2, -1.0), (-27.4, -1.0), (-27.6, -1.0)
        ]
        self.executeFnTest(erfValues, math.erf, 'math.erf')

    def test_erfc(self):
        values = [(0.0, 1.0), (-0.0, 1.0), (INF, 0.0), (NINF, 2.0), (NAN, NAN),
            # tiny values
            (1e-308, 1.0), (5e-324, 1.0), (1e-10, 0.99999999988716204),
            # small integers
            (1, 0.157299207050285), (2, 0.004677734981047268), (3, 2.2090496998585482e-05),
            (4, 1.541725790028002e-08), (5, 1.5374597944280341e-12), 
            # this number needs to be rounded
            (6, 2.1519736712498925e-17),
            (-1, 1.842700792949715), (-2, 1.9953222650189528), (-3, 1.9999779095030015),
            (-4, 1.9999999845827421), (-5, 1.9999999999984626), (-6, 2.0),
            # as x -> infinity, erfc(x) behaves like exp(-x*x)/x/sqrt(pi)
            (20, 5.395865611607906e-176), (25, 8.300172571196514e-274), (27, 5.2370464393526292e-319), (28, 0.0),
            # huge values
            (-40, 2.0), (1e16, 0.0), (-1e150, 2.0), (1.7e308, 0.0),
            # incorrectly signalled overflow on some platforms.
            (26.2, 1.6432507924389793e-300), (26.4, 4.4017768588035507e-305), (26.6, 1.08851258854424e-309),
            (26.8, 2.4849621571966629e-314), (27.0, 5.2370464393526292e-319), (27.2, 9.8813129168249309e-324),
            (27.4, 0.0), (27.6, 0.0), (-26.2, 2.0), (-26.4, 2.0), (-26.6, 2.0),
            (-26.8, 2.0), (-27.0, 2.0), (-27.2, 2.0), (-27.4, 2.0), (-27.6, 2.0)
        ]
        self.executeFnTest(values, math.erfc, 'math.erfc')
        
    def test_gamma(self):
        self.assertRaises(ValueError, math.gamma, 0.)
        self.assertRaises(ValueError, math.gamma, -0.0)
        self.assertRaises(ValueError, math.gamma, NINF)
        self.assertRaises(ValueError, math.gamma, -1)
        self.assertRaises(ValueError, math.gamma, -2)
        self.assertRaises(ValueError, math.gamma, -1e16)
        self.assertRaises(ValueError, math.gamma, -1e300)
        self.assertRaises(OverflowError, math.gamma, 5.5e-309)
        self.assertRaises(OverflowError, math.gamma, 1e-309)
        self.assertRaises(OverflowError, math.gamma, 1e-323)
        self.assertRaises(OverflowError, math.gamma, 5e-324)
        self.assertRaises(OverflowError, math.gamma, 171.625)
        self.assertRaises(OverflowError, math.gamma, 172)
        self.assertRaises(OverflowError, math.gamma, 2000)
        self.assertRaises(OverflowError, math.gamma, 1.7e308)

        values = [
            # special values
            (INF, INF), (NAN, NAN),
            # small positive integers give factorials
            (1, 1), (2, 1), (3, 2), (4, 6), (5, 24), (6, 120),
            # half integers
            (0.5, 1.7724538509055159), (1.5, 0.88622692545275805), (2.5, 1.3293403881791372),
            (3.5, 3.323350970447842), (-0.5, -3.5449077018110322), (-1.5, 2.3632718012073544),
            (-2.5, -0.94530872048294170), (-3.5, 0.27008820585226917),
            # values near 0
            (0.1, 9.5135076986687306), 
            (0.01, 99.432585119150602), 
            (1e-8, 99999999.422784343),
            #(1e-16, 10000000000000000), 
            (1e-30, 9.9999999999999988e+29), (1e-160, 1.0000000000000000e+160),
            (1e-308, 1.0000000000000000e+308), 
            (5.6e-309, 1.7857142857142848e+308),
            (-0.1, -10.686287021193193), 
            (-0.01, -100.58719796441078), 
            (-1e-8, -100000000.57721567),
            (-1e-16, -10000000000000000), 
            (-1e-30, -9.9999999999999988e+29), (-1e-160, -1.0000000000000000e+160),
            (-1e-308, -1.0000000000000000e+308), 
            (-5.6e-309, -1.7857142857142848e+308),
            # values near negative integers
            (-0.99999999999999989, -9007199254740992.0), 
            (-1.0000000000000002, 4503599627370495.5),
            (-1.9999999999999998, 2251799813685248.5), 
            (-2.0000000000000004, -1125899906842623.5),
            (-100.00000000000001, -7.5400833348831090e-145), 
            (-99.999999999999986, 7.5400833348840962e-145),
            # large inputs
            (170, 4.2690680090047051e+304), 
            (171, 7.2574156153079990e+306), 
            (171.624, 1.7942117599248104e+308),
            # inputs for which gamma(x) is tiny
            (-100.5, -3.3536908198076787e-159), 
            (-160.5, -5.2555464470078293e-286), 
            (-170.5, -3.3127395215386074e-308),
            (-171.5, 1.9316265431711902e-310), (-176.5, -1.1956388629358166e-321), (-177.5, 4.9406564584124654e-324),
            (-178.5, -0.0), (-179.5, 0.0), (-201.0001, 0.0), (-202.9999, -0.0), (-1000.5, -0.0),
            (-1000000000.3, -0.0), (-4503599627370495.5, 0.0),
            # inputs that cause problems for the standard reflection formula,
            # thanks to loss of accuracy in 1-x
            (-63.349078729022985, 4.1777971677761880e-88),
            (-127.45117632943295, 1.1831110896236810e-214)
        ]
        self.executeFnTest(values, math.gamma, 'math.gamma')

    def test_lgamma(self):
        self.assertRaises(ValueError, math.lgamma, 0.)
        self.assertRaises(ValueError, math.lgamma, -0.0)
        self.assertRaises(ValueError, math.lgamma, -1)
        self.assertRaises(ValueError, math.lgamma, -2)
        self.assertRaises(ValueError, math.lgamma, -1)
        self.assertRaises(ValueError, math.lgamma, -1e300)
        self.assertRaises(ValueError, math.lgamma, -1.79e308)
        self.assertRaises(OverflowError, math.lgamma, 2.55998332785164e305)
        self.assertRaises(OverflowError, math.lgamma, 1.7e308)

        values = [(INF, INF), (-INF, INF), (NAN, NAN),
            # small positive integers give factorials
            (1, 0.0), (2, 0.0), 
            (3, 0.69314718055994529), 
            (4, 1.791759469228055), 
            (5, 3.1780538303479458), 
            (6, 4.7874917427820458), 
            # half integers
            (0.5, 0.57236494292470008),
            (1.5, -0.12078223763524522),
            (2.5, 0.28468287047291918),
            (3.5, 1.2009736023470743),
            (-0.5, 1.2655121234846454),
            (-1.5, 0.86004701537648098),
            (-2.5, -0.056243716497674054),
            (-3.5, -1.309006684993042),
            # values near 0
            (0.1, 2.252712651734206),
            (0.01, 4.5994798780420219),
            (1e-8, 18.420680738180209),
            (1e-16, 36.841361487904734),
            (1e-30, 69.077552789821368),
            (1e-160, 368.41361487904732),
            (1e-308, 709.19620864216608),
            (5.6e-309, 709.77602713741896),
            (5.5e-309, 709.79404564292167),
            (1e-309, 711.49879373516012),
            (1e-323, 743.74692474082133),
            (5e-324, 744.44007192138122),
            (-0.1, 2.3689613327287886),
            (-0.01, 4.6110249927528013),
            (-1e-8, 18.420680749724522),
            (-1e-16, 36.841361487904734),
            (-1e-30, 69.077552789821368),
            (-1e-160, 368.41361487904732),
            (-1e-308, 709.19620864216608),
            (-5.6e-309, 709.77602713741896),
            (-5.5e-309, 709.79404564292167),
            (-1e-309, 711.49879373516012),
            (-1e-323, 743.74692474082133),
            (-5e-324, 744.44007192138122),
            # values near negative integers
            (-0.99999999999999989, 36.736800569677101),
            (-1.0000000000000002, 36.043653389117154),
            (-1.9999999999999998, 35.350506208557213),
            (-2.0000000000000004, 34.657359027997266),
            (-100.00000000000001, -331.85460524980607),
            (-99.999999999999986, -331.85460524980596),
            # large inputs
            (170, 701.43726380873704),
            (171, 706.57306224578736),
            (171.624, 709.78077443669895),
            (171.625, 709.78591682948365),
            (172, 711.71472580228999),
            (2000, 13198.923448054265),
            (2.55998332785163e305, 1.7976931348623099e+308),
            # inputs for which gamma(x) is tiny
            (-100.5, -364.90096830942736),
            (-160.5, -656.88005261126432),
            (-170.5, -707.99843314507882),
            (-171.5, -713.14301641168481),
            (-176.5, -738.95247590846486),
            (-177.5, -744.13144651738037),
            (-178.5, -749.3160351186001),
            (-1000.5, -5914.4377011168517),
            (-30000.5, -279278.6629959144),
            (-4503599627370495.5, -1.5782258434492883e+17),
            # results close to 0:  positive argument ...
            (0.99999999999999989, 2.220446049250313e-16),
            (1.0000000000000002, -3.3306690738754696e-16),
            (1.9999999999999998, 0.0),
            (2.0000000000000004, 6.661338147750939e-16),
            # ... and negative argument
            (-2.7476826467, -5.24771337495622e-11),
            (-2.457024738, 3.346471988407984e-10)
        ]
        self.executeFnTest(values, math.lgamma, 'math.lgamma')

    def testFsum(self):
        # math.fsum relies on exact rounding for correct operation.
        # There's a known problem with IA32 floating-point that causes
        # inexact rounding in some situations, and will cause the
        # math.fsum tests below to fail; see issue #2937.  On non IEEE
        # 754 platforms, and on IEEE 754 platforms that exhibit the
        # problem described in issue #2937, we simply skip the whole
        # test.

        # Python version of math.fsum, for comparison.  Uses a
        # different algorithm based on frexp, ldexp and integer
        # arithmetic.

        from sys import float_info
        mant_dig = float_info.mant_dig
        etiny = float_info.min_exp - mant_dig

        def msum(iterable):
            """Full precision summation.  Compute sum(iterable) without any
            intermediate accumulation of error.  Based on the 'lsum' function
            at http://code.activestate.com/recipes/393090/

            """
            tmant, texp = 0, 0
            for x in iterable:
                mant, exp = math.frexp(x)
                mant, exp = int(math.ldexp(mant, mant_dig)), exp - mant_dig
                if texp > exp:
                    tmant <<= texp-exp
                    texp = exp
                else:
                    mant <<= exp-texp
                tmant += mant
            # Round tmant * 2**texp to a float.  The original recipe
            # used float(str(tmant)) * 2.0**texp for this, but that's
            # a little unsafe because str -> float conversion can't be
            # relied upon to do correct rounding on all platforms.
            tail = max(len(bin(abs(tmant)))-2 - mant_dig, etiny - texp)
            if tail > 0:
                h = 1 << (tail-1)
                tmant = tmant // (2*h) + bool(tmant & h and tmant & 3*h-1)
                texp += tail
            return math.ldexp(tmant, texp)

        test_values = [
            ([], 0.0),
            ([0.0], 0.0),
            ([1e100, 1.0, -1e100, 1e-100, 1e50, -1.0, -1e50], 1e-100),
            ([2.0**53, -0.5, -2.0**-54], 2.0**53-1.0),
            ([2.0**53, 1.0, 2.0**-100], 2.0**53+2.0),
            ([2.0**53+10.0, 1.0, 2.0**-100], 2.0**53+12.0),
            ([2.0**53-4.0, 0.5, 2.0**-54], 2.0**53-3.0),
            ([1./n for n in range(1, 1001)],
             float.fromhex('0x1.df11f45f4e61ap+2')),
            ([(-1.)**n/n for n in range(1, 1001)],
             float.fromhex('-0x1.62a2af1bd3624p-1')),
            ([1.7**(i+1)-1.7**i for i in range(1000)] + [-1.7**1000], -1.0),
            ([1e16, 1., 1e-16], 10000000000000002.0),
            ([1e16-2., 1.-2.**-53, -(1e16-2.), -(1.-2.**-53)], 0.0),
            # exercise code for resizing partials array
            ([2.**n - 2.**(n+50) + 2.**(n+52) for n in range(-1074, 972, 2)] +
             [-2.**1022],
             float.fromhex('0x1.5555555555555p+970')),
            ]

        for i, (vals, expected) in enumerate(test_values):
            try:
                actual = math.fsum(vals)
            except OverflowError:
                self.fail("test %d failed: got OverflowError, expected %r "
                          "for math.fsum(%.100r)" % (i, expected, vals))
            except ValueError:
                self.fail("test %d failed: got ValueError, expected %r "
                          "for math.fsum(%.100r)" % (i, expected, vals))
            self.assertEqual(actual, expected)

        from random import random, gauss, shuffle
        for j in range(1000):
            vals = [7, 1e100, -7, -1e100, -9e-20, 8e-20] * 10
            s = 0
            for i in range(200):
                v = gauss(0, random()) ** 7 - s
                s += v
                vals.append(v)
            shuffle(vals)

            s = msum(vals)
            self.assertEqual(msum(vals), math.fsum(vals))

        self.assertRaises(ValueError, math.fsum, [1., 2, INF, NINF])
        self.assertEqual(math.fsum([1., 2, INF, INF]), INF)

if (sys.version_info.major >= 3 and sys.version_info.minor >= 5):
    # math.isclose since 3.5
    class IsCloseTests(unittest.TestCase):
        isclose = staticmethod(math.isclose) # sublcasses should override this

        def assertIsClose(self, a, b, *args, **kwargs):
            self.assertTrue(self.isclose(a, b, *args, **kwargs),
                            msg="%s and %s should be close!" % (a, b))

        def assertIsNotClose(self, a, b, *args, **kwargs):
            self.assertFalse(self.isclose(a, b, *args, **kwargs),
                             msg="%s and %s should not be close!" % (a, b))

        def assertAllClose(self, examples, *args, **kwargs):
            for a, b in examples:
                self.assertIsClose(a, b, *args, **kwargs)

        def assertAllNotClose(self, examples, *args, **kwargs):
            for a, b in examples:
                self.assertIsNotClose(a, b, *args, **kwargs)

        def test_negative_tolerances(self):
            # ValueError should be raised if either tolerance is less than zero
            with self.assertRaises(ValueError):
                self.assertIsClose(1, 1, rel_tol=-1e-100)
            with self.assertRaises(ValueError):
                self.assertIsClose(1, 1, rel_tol=1e-100, abs_tol=-1e10)

        def test_identical(self):
            # identical values must test as close
            identical_examples = [(2.0, 2.0),
                                  (0.1e200, 0.1e200),
                                  (1.123e-300, 1.123e-300),
                                  (12345, 12345.0),
                                  (0.0, -0.0),
                                  (345678, 345678)]
            self.assertAllClose(identical_examples, rel_tol=0.0, abs_tol=0.0)

        def test_eight_decimal_places(self):
            # examples that are close to 1e-8, but not 1e-9
            eight_decimal_places_examples = [(1e8, 1e8 + 1),
                                             (-1e-8, -1.000000009e-8),
                                             (1.12345678, 1.12345679)]
            self.assertAllClose(eight_decimal_places_examples, rel_tol=1e-8)
            self.assertAllNotClose(eight_decimal_places_examples, rel_tol=1e-9)

        def test_near_zero(self):
            # values close to zero
            near_zero_examples = [(1e-9, 0.0),
                                  (-1e-9, 0.0),
                                  (-1e-150, 0.0)]
            # these should not be close to any rel_tol
            self.assertAllNotClose(near_zero_examples, rel_tol=0.9)
            # these should be close to abs_tol=1e-8
            self.assertAllClose(near_zero_examples, abs_tol=1e-8)

        def test_identical_infinite(self):
            # these are close regardless of tolerance -- i.e. they are equal
            self.assertIsClose(INF, INF)
            self.assertIsClose(INF, INF, abs_tol=0.0)
            self.assertIsClose(NINF, NINF)
            self.assertIsClose(NINF, NINF, abs_tol=0.0)

        def test_inf_ninf_nan(self):
            # these should never be close (following IEEE 754 rules for equality)
            not_close_examples = [(NAN, NAN),
                                  (NAN, 1e-100),
                                  (1e-100, NAN),
                                  (INF, NAN),
                                  (NAN, INF),
                                  (INF, NINF),
                                  (INF, 1.0),
                                  (1.0, INF),
                                  (INF, 1e308),
                                  (1e308, INF)]
            # use largest reasonable tolerance
            self.assertAllNotClose(not_close_examples, abs_tol=0.999999999999999)

        def test_zero_tolerance(self):
            # test with zero tolerance
            zero_tolerance_close_examples = [(1.0, 1.0),
                                             (-3.4, -3.4),
                                             (-1e-300, -1e-300)]
            self.assertAllClose(zero_tolerance_close_examples, rel_tol=0.0)

            zero_tolerance_not_close_examples = [(1.0, 1.000000000000001),
                                                 (0.99999999999999, 1.0),
                                                 (1.0e200, .999999999999999e200)]
            self.assertAllNotClose(zero_tolerance_not_close_examples, rel_tol=0.0)

        def test_asymmetry(self):
            # test the asymmetry example from PEP 485
            self.assertAllClose([(9, 10), (10, 9)], rel_tol=0.1)

        def test_integers(self):
            # test with integer values
            integer_examples = [(100000001, 100000000),
                                (123456789, 123456788)]

            self.assertAllClose(integer_examples, rel_tol=1e-8)
            self.assertAllNotClose(integer_examples, rel_tol=1e-9)

        # TODO the test is commented out due to GR-10712
        '''
        def test_decimals(self):
            # test with Decimal values
            from decimal import Decimal#

            decimal_examples = [(Decimal('1.00000001'), Decimal('1.0')),
                                (Decimal('1.00000001e-20'), Decimal('1.0e-20')),
                                (Decimal('1.00000001e-100'), Decimal('1.0e-100')),
                                (Decimal('1.00000001e20'), Decimal('1.0e20'))]
            self.assertAllClose(decimal_examples, rel_tol=1e-8)
            self.assertAllNotClose(decimal_examples, rel_tol=1e-9)
        '''

        # TODO the test is commented out due to GR-10711
        '''
        def test_fractions(self):
            # test with Fraction values
            from fractions import Fraction

            fraction_examples = [
                (Fraction(1, 100000000) + 1, Fraction(1)),
                (Fraction(100000001), Fraction(100000000)),
                (Fraction(10**8 + 1, 10**28), Fraction(1, 10**20))]
            self.assertAllClose(fraction_examples, rel_tol=1e-8)
            self.assertAllNotClose(fraction_examples, rel_tol=1e-9)
        '''
        def test_objects(self):
            # these are close regardless of tolerance -- i.e. they are equal
            self.assertIsClose(MyFloat(), MyFloat())
            self.assertIsClose(MyFloat(), MyFloat(), abs_tol=0.0)
            self.assertIsClose(MyFloat(), MyFloat(), abs_tol=MyFloat())
            self.assertIsClose(MyFloat(), MyFloat(), rel_tol=0.0)
            self.assertIsClose(MyFloat(), MyFloat(), rel_tol=MyFloat())

            self.assertIsNotClose(MyFloat(), 10)
