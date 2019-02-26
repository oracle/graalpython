# Copyright (c) 2019, Oracle and/or its affiliates.
# Copyright (C) 1996-2017 Python Software Foundation
#
# Licensed under the PYTHON SOFTWARE FOUNDATION LICENSE VERSION 2

import unittest
from itertools import *

class CombinationsTests(unittest.TestCase):

    def test_combinations_with_replacement(self):
        cwr = combinations_with_replacement
        self.assertRaises(TypeError, cwr, 'abc')       # missing r argument
        self.assertRaises(TypeError, cwr, 'abc', 2, 1) # too many arguments
        self.assertRaises(TypeError, cwr, None)        # pool is not iterable
        self.assertRaises(ValueError, cwr, 'abc', -2)  # r is negative

        result = list()
        for a in cwr('ABC', 2):
            result += a
        correct = [('A','A'), ('A','B'), ('A','C'), ('B','B'), ('B','C'), ('C','C')]
        compare = list();
        for a in correct:
            compare += a;
        self.assertEqual(result,compare)

