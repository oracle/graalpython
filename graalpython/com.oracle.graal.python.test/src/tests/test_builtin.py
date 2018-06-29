# Copyright (c) 2018, Oracle and/or its affiliates.
# Copyright (C) 1996-2017 Python Software Foundation
#
# Licensed under the PYTHON SOFTWARE FOUNDATION LICENSE VERSION 2
    
import unittest

class MyIndexable(object):
    def __init__(self, value):
        self.value = value
    def __index__(self):
        return self.value

class BuiltinTest(unittest.TestCase):
    def test_bin(self):
        self.assertEqual(bin(0), '0b0')
        self.assertEqual(bin(1), '0b1')
        self.assertEqual(bin(-1), '-0b1')
        self.assertEqual(bin(2**65), '0b1' + '0' * 65)
        self.assertEqual(bin(2**65-1), '0b' + '1' * 65)
        self.assertEqual(bin(-(2**65)), '-0b1' + '0' * 65)
        self.assertEqual(bin(-(2**65-1)), '-0b' + '1' * 65)

        # test of specializations
        self.assertEqual(bin(MyIndexable(False)), '0b0')
        self.assertEqual(bin(MyIndexable(True)), '0b1')
        self.assertEqual(bin(MyIndexable(-(2**65))), '-0b1' + '0' * 65)
