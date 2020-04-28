# Copyright (c) 2019, 2020, Oracle and/or its affiliates.
# Copyright (C) 1996-2017 Python Software Foundation
#
# Licensed under the PYTHON SOFTWARE FOUNDATION LICENSE VERSION 2

import unittest
import binascii
import array
import sys

class MyInt():
    def __init__(self, value):
        self.value = value

    def __int__(self):
        return self.value

class BinASCIITest(unittest.TestCase):

    type2test = bytes

    def test_b2a_base64_newline(self):
        b = self.type2test(b'hello')
        self.assertEqual(binascii.b2a_base64(b),
                         b'aGVsbG8=\n')
        if (sys.version_info.major >= 3 and sys.version_info.minor >= 6):
            self.assertEqual(binascii.b2a_base64(b, newline=True),
                         b'aGVsbG8=\n')
            self.assertEqual(binascii.b2a_base64(b, newline=False),
                         b'aGVsbG8=')

    def test_b2a_base64_int_newline(self):
        b = self.type2test(b'hello')
        if (sys.version_info.major >= 3 and sys.version_info.minor >= 6):
            self.assertEqual(binascii.b2a_base64(b, newline=125),
                         b'aGVsbG8=\n')
            self.assertEqual(binascii.b2a_base64(b, newline=-10),
                         b'aGVsbG8=\n')
            self.assertEqual(binascii.b2a_base64(b, newline=0),
                         b'aGVsbG8=')

    def test_b2a_base64_object_newline(self):
        b = self.type2test(b'hello')
        if (sys.version_info.major >= 3 and sys.version_info.minor >= 6):
            self.assertEqual(binascii.b2a_base64(b, newline=MyInt(125)),
                         b'aGVsbG8=\n')
            self.assertEqual(binascii.b2a_base64(b, newline=MyInt(-10)),
                         b'aGVsbG8=\n')
            self.assertEqual(binascii.b2a_base64(b, newline=MyInt(0)),
                         b'aGVsbG8=')

    def test_b2a_base64_wrong_newline(self):
        b = self.type2test(b'hello')
        if (sys.version_info.major >= 3 and sys.version_info.minor >= 6):
            self.assertRaises(TypeError, binascii.b2a_base64, b, newline='ahoj')

    def test_b2a_base64_return_type(self):
        b = self.type2test(b'hello')
        self.assertEqual(type(binascii.b2a_base64(b)), bytes)
        if (sys.version_info.major >= 3 and sys.version_info.minor >= 6):
            self.assertEqual(type(binascii.b2a_base64(b, newline=False)), bytes)
            
    def test_a2b_hex(self):
        b = self.type2test(b'68656c6c6f')
        self.assertEqual(binascii.unhexlify(b), b'hello')
        b = self.type2test(b'68656C6c6F')
        self.assertEqual(binascii.unhexlify(b), b'hello')

    def test_b2a_hex(self):
        b = self.type2test(b'helloo')
        self.assertEqual(binascii.hexlify(b), b'68656c6c6f6f')

class ArrayBinASCIITest(BinASCIITest):
    def type2test(self, s):
        return array.array('b', list(s))


class BytearrayBinASCIITest(BinASCIITest):
    type2test = bytearray


class MemoryviewBinASCIITest(BinASCIITest):
    type2test = memoryview

class IndependetTest(unittest.TestCase):

    def test_b2a_base64_wrong_first_arg(self):
        if (sys.version_info.major >= 3 and sys.version_info.minor >= 6):
            self.assertRaises(TypeError, binascii.b2a_base64, 'Ahoj', newline=True)
            self.assertRaises(TypeError, binascii.b2a_base64, 10, newline=True)
