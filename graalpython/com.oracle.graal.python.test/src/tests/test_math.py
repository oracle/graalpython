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
            self.fail("{}: {}".format(name, failure))

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
        # TODO uncomment when GR-10346 will be fixed
        #self.ftest('acosh(2)', math.acosh(2), 1.3169578969248168)
        self.assertRaises(ValueError, math.acosh, 0)
        self.assertRaises(ValueError, math.acosh, -1)
        self.assertEqual(math.acosh(INF), INF)
        self.assertRaises(ValueError, math.acosh, NINF)
        self.assertTrue(math.isnan(math.acosh(NAN)))

        class MyFF:
            def __float__(self):
                return 6
        # TODO uncomment when GR-10346 will be fixed
        #self.ftest('acos(MyFloat())', math.acosh(MyFF()), 0.9272952180016123)
        self.assertRaises(ValueError, math.acosh, MyFloat())

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
