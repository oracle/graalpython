# Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
import sys

import array

BIG_NUMBER = 99999937497465632974931

class _NamedIntConstant(int):
    def __new__(cls, value, name):
        self = super(_NamedIntConstant, cls).__new__(cls, value)
        self.name = name
        return self

    def __str__(self):
        return self.name

    __repr__ = __str__


def test_int_subclassing():
    MAXREPEAT = _NamedIntConstant(1, 'MAXREPEAT')
    assert MAXREPEAT == 1
    assert str(MAXREPEAT) == "MAXREPEAT"


def test_bigint():
    i = int(BIG_NUMBER)
    assert isinstance(i, int)
    assert i == BIG_NUMBER
    # from string
    i = int(str(BIG_NUMBER))
    assert isinstance(i, int)
    assert i == BIG_NUMBER


def test_boolean2int():
    assert int(True) == 1
    assert int(False) == 0


def test_bigint_mul():
    assert 99999937497465632974931 * 1223432423545234234123123 == 122343165886896325043539375228725106116626429513
    assert 99999937497465632974931 * (2**100) == 126764980791447734004805377032945185921379990352429056


def test_int_from_custom():
    class CustomInt4():
        def __int__(self):
            return 1

    class CustomInt8():
        def __int__(self):
            return 0xCAFEBABECAFED00D

    class SubInt(int):
        def __int__(self):
            return 0xBADF00D

    class NoInt():
        pass

    assert int(CustomInt4()) == 1
    assert int(CustomInt8()) == 0xCAFEBABECAFED00D
    assert CustomInt8() != 0xCAFEBABECAFED00D
    assert int(SubInt()) == 0xBADF00D
    assert SubInt() == 0
    try:
        int(NoInt())
        assert False, "converting non-integer to integer must not be possible"
    except BaseException as e:
        assert type(e) == TypeError, "expected type error, was: %r" % type(e)

def test_int_bit_length():
    assert (int(0)).bit_length() == 0
    assert (int(1)).bit_length() == 1
    assert (int(-1)).bit_length() == 1
    assert (int(255)).bit_length() == 8
    assert (int(-255)).bit_length() == 8
    assert (int(6227020800)).bit_length() == 33
    assert (int(-6227020800)).bit_length() == 33
    assert (int(2432902008176640000)).bit_length() == 62
    assert (int(-2432902008176640000)).bit_length() == 62
    assert (int(9999992432902008176640000999999)).bit_length() == 103
    assert (int(-9999992432902008176640000999999)).bit_length() == 103

class MyInt(int):
    pass

def test_int():
    def builtinTest(number):
        a = int(number)
        b = a.__int__()
        assert a == b
        assert a is b
        assert type(a) == int
        assert type(b) == int

    builtinTest(-9)
    builtinTest(0)
    builtinTest(9)
    builtinTest(6227020800)
    builtinTest(9999992432902008176640000999999)

    assert True.__int__() == 1
    assert False.__int__() == 0


def test_int_subclass():
    def subclassTest(number):
        a = MyInt(number)
        b = a.__int__()
        assert a == b
        assert a is not b
        assert type(a) == MyInt
        assert type(b) == int

    subclassTest(-9)
    subclassTest(0)
    subclassTest(9)
    subclassTest(6227020800)
    subclassTest(9999992432902008176640000999999)

def test_real_imag():
    def builtinTest(number):
        a = int(number)
        b = a.real
        c = a.imag
        assert a == b
        assert a is b
        assert c == 0
        assert type(a) == int
        assert type(b) == int
        assert type(c) == int

    builtinTest(-9)
    builtinTest(0)
    builtinTest(9)
    builtinTest(6227020800)
    builtinTest(9999992432902008176640000999999)

    assert True.real == 1
    assert False.real == 0
    assert True.imag == 0
    assert False.imag == 0

def test_real_imag_subclass():
    def subclassTest(number):
        a = MyInt(number)
        b = a.real
        c = a.imag
        assert a == b
        assert a is not b
        assert c == 0
        assert type(a) == MyInt
        assert type(b) == int
        assert type(c) == int

    subclassTest(-9)
    subclassTest(0)
    subclassTest(9)
    subclassTest(6227020800)
    subclassTest(9999992432902008176640000999999)

def test_numerator_denominator():
    def builtinTest(number):
        a = int(number)
        b = a.numerator
        c = a.denominator
        assert a == b
        assert a is b
        assert c == 1
        assert type(a) == int
        assert type(b) == int
        assert type(c) == int

    builtinTest(-9)
    builtinTest(0)
    builtinTest(9)
    builtinTest(6227020800)
    builtinTest(9999992432902008176640000999999)

    assert True.numerator == 1
    assert False.numerator == 0
    assert True.denominator == 1
    assert False.denominator == 1

def test_mumerator_denominator_subclass():
    def subclassTest(number):
        a = MyInt(number)
        b = a.numerator
        c = a.denominator
        assert a == b
        assert a is not b
        assert c == 1
        assert type(a) == MyInt
        assert type(b) == int
        assert type(c) == int

    subclassTest(-9)
    subclassTest(0)
    subclassTest(9)
    subclassTest(6227020800)
    subclassTest(9999992432902008176640000999999)

def test_conjugate():
    def builtinTest(number):
        a = int(number)
        b = a.conjugate()
        assert a == b
        assert a is b
        assert type(a) == int
        assert type(b) == int

    builtinTest(-9)
    builtinTest(0)
    builtinTest(9)
    builtinTest(6227020800)
    builtinTest(9999992432902008176640000999999)

    assert True.conjugate() == 1
    assert False.conjugate() == 0

def test_conjugate_subclass():
    def subclassTest(number):
        a = MyInt(number)
        b = a.conjugate()
        assert a == b
        assert a is not b
        assert type(a) == MyInt
        assert type(b) == int

    subclassTest(-9)
    subclassTest(0)
    subclassTest(9)
    subclassTest(6227020800)
    subclassTest(9999992432902008176640000999999)

class MyTrunc:
    def __trunc__(self):
        return 1972
class MyIntTrunc:
    def __trunc__(self):
        return 1972
    def __int__(self):
        return 66

def test_trunc():
    def builtinTest(number):
        a = int(number)
        b = a.__trunc__()
        assert a == b
        assert a is b
        assert type(a) == int
        assert type(b) == int

    builtinTest(-9)
    builtinTest(0)
    builtinTest(9)
    builtinTest(6227020800)
    builtinTest(9999992432902008176640000999999)

    assert True.__trunc__() == 1
    assert False.__trunc__() == 0

    assert int(MyTrunc()) == 1972
    assert int(MyIntTrunc()) == 66

def test_trunc_subclass():
    def subclassTest(number):
        a = MyInt(number)
        b = a.__trunc__()
        assert a == b
        assert a is not b
        assert type(a) == MyInt
        assert type(b) == int

    subclassTest(-9)
    subclassTest(0)
    subclassTest(9)
    subclassTest(6227020800)
    subclassTest(9999992432902008176640000999999)

    assert MyInt(MyTrunc()) == 1972
    assert MyInt(MyIntTrunc()) == 66

def test_create_int_from_bool():

    class SpecInt1:
        def __int__(self):
            return True

    class SpecInt0:
        def __int__(self):
            return False

    assert int(SpecInt1()) == 1
    assert int(SpecInt0()) == 0

def test_create_int_from_string():
  assert int("5c7920a80f5261a2e5322163c79b71a25a41f414", 16) == 527928385865769069253929759180846776123316630548


class FromBytesTests(unittest.TestCase):

    def check(self, tests, byteorder, signed=False):
        for test, expected in tests.items():
            try:
                self.assertEqual( int.from_bytes(test, byteorder, signed=signed), expected)
            except Exception as err:
                raise AssertionError(
                    "failed to convert {0} with byteorder={1!r} and signed={2}"
                    .format(test, byteorder, signed)) from err

    def test_SignedBigEndian(self):
        # Convert signed big-endian byte arrays to integers.
        tests1 = {
            b'': 0,
            b'\x00': 0,
            b'\x00\x00': 0,
            b'\x01': 1,
            b'\x00\x01': 1,
            b'\xff': -1,
            b'\xff\xff': -1,
            b'\x81': -127,
            b'\x80': -128,
            b'\xff\x7f': -129,
            b'\x7f': 127,
            b'\x00\x81': 129,
            b'\xff\x01': -255,
            b'\xff\x00': -256,
            b'\x00\xff': 255,
            b'\x01\x00': 256,
            b'\x7f\xff': 32767,
            b'\x80\x00': -32768,
            b'\x00\xff\xff': 65535,
            b'\xff\x00\x00': -65536,
            b'\x80\x00\x00': -8388608
        }
        self.check(tests1, 'big', signed=True)

    def test_SignedLittleEndian(self):
        # Convert signed little-endian byte arrays to integers.
        tests2 = {
            b'': 0,
            b'\x00': 0,
            b'\x00\x00': 0,
            b'\x01': 1,
            b'\x00\x01': 256,
            b'\xff': -1,
            b'\xff\xff': -1,
            b'\x81': -127,
            b'\x80': -128,
            b'\x7f\xff': -129,
            b'\x7f': 127,
            b'\x81\x00': 129,
            b'\x01\xff': -255,
            b'\x00\xff': -256,
            b'\xff\x00': 255,
            b'\x00\x01': 256,
            b'\xff\x7f': 32767,
            b'\x00\x80': -32768,
            b'\xff\xff\x00': 65535,
            b'\x00\x00\xff': -65536,
            b'\x00\x00\x80': -8388608
        }
        self.check(tests2, 'little', signed=True)

    def test_UnsignedBigEndian(self):
        # Convert unsigned big-endian byte arrays to integers.
        tests3 = {
            b'': 0,
            b'\x00': 0,
            b'\x01': 1,
            b'\x7f': 127,
            b'\x80': 128,
            b'\xff': 255,
            b'\x01\x00': 256,
            b'\x7f\xff': 32767,
            b'\x80\x00': 32768,
            b'\xff\xff': 65535,
            b'\x01\x00\x00': 65536,
        }
        self.check(tests3, 'big', signed=False)

    def test_UnsignedLittleEndian(self):
        # Convert integers to unsigned little-endian byte arrays.
        tests4 = {
            b'': 0,
            b'\x00': 0,
            b'\x01': 1,
            b'\x7f': 127,
            b'\x80': 128,
            b'\xff': 255,
            b'\x00\x01': 256,
            b'\xff\x7f': 32767,
            b'\x00\x80': 32768,
            b'\xff\xff': 65535,
            b'\x00\x00\x01': 65536,
        }
        self.check(tests4, 'little', signed=False)

    def test_IntObject(self):
        myint = MyInt
        self.assertIs(type(myint.from_bytes(b'\x00', 'big')), MyInt)
        self.assertEqual(myint.from_bytes(b'\x01', 'big'), 1)
        self.assertIs(
            type(myint.from_bytes(b'\x00', 'big', signed=False)), myint)
        self.assertEqual(myint.from_bytes(b'\x01', 'big', signed=False), 1)
        self.assertIs(type(myint.from_bytes(b'\x00', 'little')), myint)
        self.assertEqual(myint.from_bytes(b'\x01', 'little'), 1)
        self.assertIs(type(myint.from_bytes(
            b'\x00', 'little', signed=False)), myint)
        self.assertEqual(myint.from_bytes(b'\x01', 'little', signed=False), 1)

    def test_from_list(self):
        self.assertEqual(
            int.from_bytes([255, 0, 0], 'big', signed=True), -65536)
        self.assertEqual(
            MyInt.from_bytes([255, 0, 0], 'big', signed=True), -65536)
        self.assertIs(type(MyInt.from_bytes(
            [255, 0, 0], 'little', signed=False)), MyInt)

        class LyingList(list):
            def __iter__(self):
                return iter([10, 20, 30, 40])

        self.assertEqual(
            int.from_bytes(LyingList([255, 1, 1]), 'big'), 169090600)

    def test_from_tuple(self):
        self.assertEqual(
            int.from_bytes((255, 0, 0), 'big', signed=True), -65536)
        self.assertEqual(
            MyInt.from_bytes((255, 0, 0), 'big', signed=True), -65536)
        self.assertIs(type(MyInt.from_bytes(
            (255, 0, 0), 'little', signed=False)), MyInt)

        class LyingTuple(tuple):
            def __iter__(self):
                return iter((15, 25, 35, 45))
        self.assertEqual(
            int.from_bytes(LyingTuple((255, 1, 1)), 'big'), 253305645)

    def test_from_bytearray(self):
        self.assertEqual(int.from_bytes(
            bytearray(b'\xff\x00\x00'), 'big', signed=True), -65536)
        self.assertEqual(int.from_bytes(
            bytearray(b'\xff\x00\x00'), 'big', signed=True), -65536)

    def test_from_array(self):
        self.assertEqual(int.from_bytes(
            array.array('b', b'\xff\x00\x00'), 'big', signed=True), -65536)

    def test_from_memoryview(self):
        self.assertEqual(int.from_bytes(
            memoryview(b'\xff\x00\x00'), 'big', signed=True), -65536)

    def test_wrong_input(self):
        self.assertRaises(ValueError, int.from_bytes, [256], 'big')
        self.assertRaises(ValueError, int.from_bytes, (256,), 'big')
        self.assertRaises(ValueError, int.from_bytes, [0], 'big\x00')
        self.assertRaises(ValueError, int.from_bytes, [0], 'little\x00')
        self.assertRaises(TypeError, int.from_bytes, 0, 'big')
        self.assertRaises(TypeError, int.from_bytes, 0, 'big', True)

        self.assertRaises(TypeError, int.from_bytes, "", 'big')
        self.assertRaises(TypeError, int.from_bytes, "\x00", 'big')
        self.assertRaises(TypeError, MyInt.from_bytes, "", 'big')
        self.assertRaises(TypeError, MyInt.from_bytes, "\x00", 'big')
        self.assertRaises(TypeError, MyInt.from_bytes, 0, 'big')
        self.assertRaises(TypeError, int.from_bytes, 0, 'big', True)

    def test_int_subclass(self):
        class myint2(int):
            def __new__(cls, value):
                return int.__new__(cls, value + 1)

        i = myint2.from_bytes(b'\x01', 'big')
        self.assertIs(type(i), myint2)
        if (sys.version_info.major >= 3 and sys.version_info.minor >= 6):
            # It doesn't pass on old CPython
            self.assertEqual(i, 2)

        class myint3(int):
            def __init__(self, value):
                self.foo = 'bar'

        i = myint3.from_bytes(b'\x01', 'big')
        self.assertIs(type(i), myint3)
        self.assertEqual(i, 1)
        if (sys.version_info.major >= 3 and sys.version_info.minor >= 6):
            # It doesn't pass on old CPython
            self.assertEqual(getattr(i, 'foo', 'none'), 'bar')

    def test_range(self):
        self.assertEqual(int.from_bytes(range(5), 'big'), 16909060)
        self.assertEqual(int.from_bytes(range(5), 'little'), 17230332160)
        self.assertEqual(int.from_bytes(range(200,225), 'big'), 1260368276743602661175172759269383066378083427695751132536800)
        r = range(10)
        self.assertEqual(int.from_bytes(r[:], 'big'), 18591708106338011145)
        self.assertEqual(int.from_bytes(r[1:3], 'big'), 258)
        self.assertEqual(int.from_bytes(r[3:1], 'big'), 0)
        self.assertEqual(int.from_bytes(r[3:-1], 'big'), 3315799033608)

    def test_map(self):
        def myconvert(text):
            return int(text)
        self.assertEqual(int.from_bytes(map(myconvert, ["100","10","1"]), 'big'), 6556161)

    def test_from_byteslike_object(self):
        class mybyteslike():
            def __bytes__(self):
                return bytes([10,20])

        self.assertEqual(int.from_bytes(mybyteslike(), 'big'), 2580)

    def test_from_wrong_byteslike_object(self):
        class mybyteslike1():
            def __bytes__(self):
                return range(3)

        self.assertRaises(TypeError, int.from_bytes, mybyteslike1(), 'big')

        class mybyteslike2():
            def __bytes__(self):
                return array.array('b', [2, 2, 3])

        self.assertRaises(TypeError, int.from_bytes, mybyteslike2(), 'big')

    def test_from_list_with_byteslike(self):
        class StrangeList(list):
            def __bytes__(self):
                return bytes([3])
            def __iter__(self):
                return iter([10])

        self.assertEqual(int.from_bytes(StrangeList([4,5]), 'big'), 3)

class ToBytesTests(unittest.TestCase):

    class MyInt(int):
        pass

    def check(self, tests, byteorder, signed=False):
        for test, expected in tests.items():
            try:
                self.assertEqual(
                    test.to_bytes(len(expected), byteorder, signed=signed), expected)
            except Exception as err:
                raise AssertionError(
                    "failed to convert {0} with byteorder={1} and signed={2}"
                    .format(test, byteorder, signed)) from err

    def checkPIntSpec(self, tests, byteorder, signed=False):
        for test, expected in tests.items():
            try:
                self.assertEqual(
                    MyInt(test).to_bytes(len(expected), byteorder, signed=signed), expected)
            except Exception as err:
                raise AssertionError(
                    "failed to convert {0} with byteorder={1} and signed={2}"
                    .format(test, byteorder, signed)) from err


    def test_SignedBitEndian(self):
        # Convert integers to signed big-endian byte arrays.
        tests1 = {
            0: b'\x00',
            1: b'\x01',
            -1: b'\xff',
            -127: b'\x81',
            -128: b'\x80',
            -129: b'\xff\x7f',
            127: b'\x7f',
            129: b'\x00\x81',
            -255: b'\xff\x01',
            -256: b'\xff\x00',
            255: b'\x00\xff',
            256: b'\x01\x00',
            32767: b'\x7f\xff',
            -32768: b'\xff\x80\x00',
            65535: b'\x00\xff\xff',
            -65536: b'\xff\x00\x00',
            -8388608: b'\x80\x00\x00'
        }
        self.check(tests1, 'big', signed=True)
        self.checkPIntSpec(tests1, 'big', signed=True)

    def test_SignedLittleEndian(self):
        # Convert integers to signed little-endian byte arrays.
        tests2 = {
            0: b'\x00',
            1: b'\x01',
            -1: b'\xff',
            -127: b'\x81',
            -128: b'\x80',
            -129: b'\x7f\xff',
            127: b'\x7f',
            129: b'\x81\x00',
            -255: b'\x01\xff',
            -256: b'\x00\xff',
            255: b'\xff\x00',
            256: b'\x00\x01',
            32767: b'\xff\x7f',
            -32768: b'\x00\x80',
            65535: b'\xff\xff\x00',
            -65536: b'\x00\x00\xff',
            -8388608: b'\x00\x00\x80'
        }
        self.check(tests2, 'little', signed=True)
        self.checkPIntSpec(tests2, 'little', signed=True)

    def test_UnsignedBigEndian(self):
        # Convert integers to unsigned big-endian byte arrays.
        tests3 = {
            0: b'\x00',
            1: b'\x01',
            127: b'\x7f',
            128: b'\x80',
            255: b'\xff',
            256: b'\x01\x00',
            32767: b'\x7f\xff',
            32768: b'\x80\x00',
            65535: b'\xff\xff',
            65536: b'\x01\x00\x00'
        }
        self.check(tests3, 'big', signed=False)
        self.checkPIntSpec(tests3, 'big', signed=False)

    def test_UnsignedLittleEndian(self):
        # Convert integers to unsigned little-endian byte arrays.
        tests4 = {
            0: b'\x00',
            1: b'\x01',
            127: b'\x7f',
            128: b'\x80',
            255: b'\xff',
            256: b'\x00\x01',
            32767: b'\xff\x7f',
            32768: b'\x00\x80',
            65535: b'\xff\xff',
            65536: b'\x00\x00\x01'
        }
        self.check(tests4, 'little', signed=False)
        self.checkPIntSpec(tests4, 'little', signed=False)

    def test_SpecialCases(self):
        self.assertEqual((0).to_bytes(0, 'big'), b'')
        self.assertEqual((1).to_bytes(5, 'big'), b'\x00\x00\x00\x00\x01')
        self.assertEqual((0).to_bytes(5, 'big'), b'\x00\x00\x00\x00\x00')
        self.assertEqual((-1).to_bytes(5, 'big', signed=True),
                         b'\xff\xff\xff\xff\xff')

    def test_WrongInput(self):
        self.assertRaises(OverflowError, (256).to_bytes, 1, 'big', signed=False)
        self.assertRaises(OverflowError, (256).to_bytes, 1, 'big', signed=True)
        self.assertRaises(OverflowError, (256).to_bytes, 1, 'little', signed=False)
        self.assertRaises(OverflowError, (256).to_bytes, 1, 'little', signed=True)
        self.assertRaises(OverflowError, (-1).to_bytes, 2, 'big', signed=False)
        self.assertRaises(OverflowError, (-1).to_bytes, 2, 'little', signed=False)
        self.assertRaises(OverflowError, (1).to_bytes, 0, 'big')

    def test_WrongTypes(self):
        class MyTest():
            def __int__(self):
                return 3

        self.assertRaises(TypeError, (1).to_bytes, MyTest(), 'big')

        class MyTest2():
            def __int__(self):
              return 3
            def __index__(self):
              return 4

        self.assertEqual((1).to_bytes(MyTest2(), 'big'), b'\x00\x00\x00\x01')

    def test_SubClass(self):
        class MyTest(int):
            def __int__(self):
                return 3
            def __index__(self):
                return 4

        self.assertEqual(MyTest(1).to_bytes(MyTest(10), 'big'), b'\x00\x00\x00\x00\x00\x00\x00\x00\x00\x01')
