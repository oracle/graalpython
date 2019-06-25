# Copyright (c) 2018, 2019, Oracle and/or its affiliates.
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

    def test_GR11897(self):
        globs = {}
        code = 'a' + ' = ' + '8.01234567890123'
        exec(code, globs)
        self.assertEqual(globs['a'], 8.01234567890123)

    def test_divmod_complex(self):
        c1, c2 = complex(3, 2), complex(4,1)
        self.assertRaises(TypeError, divmod, c1, c2)
        self.assertRaises(TypeError, divmod, 10, c2)
        self.assertRaises(TypeError, divmod, c1, 10)

    def test_getitem_typeerror(self):
        a = object()
        try:
            a[1]
        except TypeError :
            pass
        else:
            self.assertTrue(False)
