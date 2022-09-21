# Copyright (c) 2018, 2021, Oracle and/or its affiliates.
# Copyright (C) 1996-2017 Python Software Foundation
#
# Licensed under the PYTHON SOFTWARE FOUNDATION LICENSE VERSION 2
# Test of a sorted() written in Python
import unittest

def sorted(iterable):
    result = list(iterable)

    for passnum in range(len(result)-1,0,-1):
        for i in range(passnum):
            if result[i] > result[i+1]:
                temp = result[i]
                result[i] = result[i+1]
                result[i+1] = temp

    return result

def test_sorted():
    lst = [2,1,4,3]
    assert sorted(lst) == [1,2,3,4]


class TestSorted(unittest.TestCase):
    def test_inputtypes(self):
        s = 'abracadabra'
        types = [list, tuple, str]
        for T in types:
            self.assertEqual(sorted(s), sorted(T(s)))

        s = ''.join(set(s))  # unique letters only
        types = [str, set, frozenset, list, tuple, dict.fromkeys]
        for T in types:
            self.assertEqual(sorted(s), sorted(T(s)))

    def test_baddecorator(self):
        data = 'The quick Brown fox Jumped over The lazy Dog'.split()
        self.assertRaises(TypeError, sorted, data, None, lambda x,y: 0)

    def test_list_subclass(self):
        class MyList(list):
            def __iter__(self):
                return iter([4, 2, 5])

        # Use eval to get the fast path specialization
        self.assertEqual(eval("sorted(MyList())", {"MyList": MyList}), [2, 4, 5])
