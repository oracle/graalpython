# Copyright (c) 2018, 2021, Oracle and/or its affiliates.
# Copyright (C) 1996-2017 Python Software Foundation
#
# Licensed under the PYTHON SOFTWARE FOUNDATION LICENSE VERSION 2

import unittest

import sys

class Empty:
    def __repr__(self):
        return '<Empty>'

class Cmp:
    def __init__(self,arg):
        self.arg = arg

    def __repr__(self):
        return '<Cmp %s>' % self.arg

    def __eq__(self, other):
        return self.arg == other

class First():

    def __init__(self, value):
        self.value = value

    def __eq__(self, other):
        return self.value == other.value

class BasicComparisonTest(unittest.TestCase):

    set1 = [2, 2.0, 2, 2+0j, Cmp(2.0)]
    set2 = [[1], (3,), None, Empty()]
    candidates = set1 + set2

    def test_comparisons(self):
        for a in self.candidates:
            for b in self.candidates:
                if ((a in self.set1) and (b in self.set1)) or a is b:
                    self.assertEqual(a, b)
                else:
                    self.assertNotEqual(a, b)

    def test_id_comparisons(self):
        # Ensure default comparison compares id() of args
        L = []
        for i in range(10):
            L.insert(len(L)//2, Empty())
        for a in L:
            for b in L:
                self.assertEqual(a == b, id(a) == id(b),
                                 'a=%r, b=%r' % (a, b))

    def test_ne(self):
        x = First(1)
        y = First(1)
        z = First(2)

        self.assertIs(x == y, True)
        self.assertIs(x != y, False)
        self.assertIs(x != z, True)

    if (sys.version_info.major >= 3 and sys.version_info.minor >= 5):
        def test_ne_high_priority(self):
            """object.__ne__() should allow reflected __ne__() to be tried"""
            calls = []

            class Left:
                # Inherits object.__ne__()
                def __eq__(*args):
                    calls.append('Left.__eq__')
                    return NotImplemented

            class Right:

                def __eq__(*args):
                    calls.append('Right.__eq__')
                    return NotImplemented

                def __ne__(*args):
                    calls.append('Right.__ne__')
                    return NotImplemented

            Left() != Right()
            self.assertSequenceEqual(calls, ['Left.__eq__', 'Right.__ne__'])

        def test_ne_low_priority(self):
            """object.__ne__() should not invoke reflected __eq__()"""
            calls = []

            class Base:

                # Inherits object.__ne__()
                def __eq__(*args):
                    calls.append('Base.__eq__')
                    return NotImplemented

            class Derived(Base):  # Subclassing forces higher priority

                def __eq__(*args):
                    calls.append('Derived.__eq__')
                    return NotImplemented
                def __ne__(*args):
                    calls.append('Derived.__ne__')
                    return NotImplemented

            Base() != Derived()
            self.assertSequenceEqual(calls, ['Derived.__ne__', 'Base.__eq__'])
