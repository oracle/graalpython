# Copyright (c) 2018, Oracle and/or its affiliates.
# Copyright (C) 1996-2017 Python Software Foundation
#
# Licensed under the PYTHON SOFTWARE FOUNDATION LICENSE VERSION 2

import unittest

import sys

class First():

    def __init__(self, value):
        self.value = value

    def __eq__(self, other):
        return self.value == other.value

class BasicComparisonTest(unittest.TestCase):

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
