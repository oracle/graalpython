# Copyright (c) 2019, Oracle and/or its affiliates.
# Copyright (C) 1996-2017 Python Software Foundation
#
# Licensed under the PYTHON SOFTWARE FOUNDATION LICENSE VERSION 2

import unittest
import marshal
import array
import types
import sys

class BaseMarshalUnmarshal:
    def helper(self, sample, *extra):
        new = marshal.loads(marshal.dumps(sample, *extra))
        self.assertEqual(sample, new)     

class IntTest (unittest.TestCase, BaseMarshalUnmarshal):

    def test_ints(self):
        # Test a range of Python ints larger than the machine word size.
        n = sys.maxsize ** 20
        while n:
            for expected in (-n, n):
                self.helper(expected)
            n = n >> 1

    def test_int64(self):
        # Simulate int marshaling with TYPE_INT64.
        maxint64 = (1 << 63) - 1
        minint64 = -maxint64-1
        for base in maxint64, minint64, -maxint64, -(minint64 >> 1):
            while base:
                self.helper(base)
                if base == -1:  # a fixed-point for shifting right 1
                    base = 0
                else:
                    base >>= 1

        self.helper(0x1032547698badcfe)
        self.helper(-0x1032547698badcff)
        self.helper(0x7f6e5d4c3b2a1908)
        self.helper(-0x7f6e5d4c3b2a1909)

    def test_bool(self):
        for b in (True, False):
            self.helper(b)

class FloatTest(unittest.TestCase, BaseMarshalUnmarshal):
    def test_floats(self):
        # Test a few floats
        small = 1e-25
        n = sys.maxsize * 3.7e250
        while n > small:
            for expected in (-n, n):
                self.helper(float(expected))
            n /= 123.4567

        f = 0.0
        s = marshal.dumps(f, 2)
        got = marshal.loads(s)
        self.assertEqual(f, got)
        # and with version <= 1 (floats marshalled differently then)
        s = marshal.dumps(f, 1)
        got = marshal.loads(s)
        self.assertEqual(f, got)

        n = sys.maxsize * 3.7e-250
        while n < small:
            for expected in (-n, n):
                f = float(expected)
                self.helper(f)
                self.helper(f, 1)
            n *= 123.4567

class StringTest(unittest.TestCase, BaseMarshalUnmarshal):
    def test_unicode(self):
        for s in ["", "Andr\xe8 Previn", "abc", " "*10000]:
            self.helper(marshal.loads(marshal.dumps(s)))

    def test_string(self):
        for s in ["", "Andr\xe8 Previn", "abc", " "*10000]:
            self.helper(s)

    def test_bytes(self):
        for s in [b"", b"Andr\xe8 Previn", b"abc", b" "*10000]:
            self.helper(s)

class ExceptionTest(unittest.TestCase):

    def test_exceptions(self):
        new = marshal.loads(marshal.dumps(StopIteration))
        self.assertEqual(StopIteration, new)

class CodeTest(unittest.TestCase):

    # TODO, currently the object created from serialized data is not equal.
    #def test_code(self):
    #    co = ExceptionTest.test_exceptions.__code__
    #    new = marshal.loads(marshal.dumps(co))
    #    self.assertEqual(co, new)

    def test_many_codeobjects(self):
        # Issue2957: bad recursion count on code objects
        count = 5000    # more than MAX_MARSHAL_STACK_DEPTH
        codes = (ExceptionTest.test_exceptions.__code__,) * count
        marshal.loads(marshal.dumps(codes))

    def test_different_filenames(self):
        co1 = compile("x", "f1", "exec")
        co2 = compile("y", "f2", "exec")
        co1, co2 = marshal.loads(marshal.dumps((co1, co2)))
        self.assertEqual(co1.co_filename, "f1")
        self.assertEqual(co2.co_filename, "f2")

    def test_same_filename_used(self):
        s = """def f(): pass\ndef g(): pass"""
        co = compile(s, "myfile", "exec")
        co = marshal.loads(marshal.dumps(co))
        for obj in co.co_consts:
            if isinstance(obj, types.CodeType):
                self.assertIs(co.co_filename, obj.co_filename)

class ContainerTest(unittest.TestCase, BaseMarshalUnmarshal):
    d = {'astring': 'foo@bar.baz.spam',
         'afloat': 7283.43,
         'anint': 2**20,
         'ashortlong': 2,
         'alist': ['.zyx.41'],
         'atuple': ('.zyx.41',)*10,
         'aboolean': False,
         'aunicode': "Andr\xe8 Previn"
         }

    def test_dict(self):
        self.helper(self.d)

    def test_list(self):
        self.helper(list(self.d.items()))

    def test_tuple(self):
        self.helper(tuple(self.d.keys()))

    def test_sets(self):
        for constructor in (set, frozenset):
            self.helper(constructor(self.d.keys()))

    # TODO enable this test, when GR-13961 and GR-13962 will be fixed
    #def test_empty_frozenset_singleton(self):
    #    # marshal.loads() must reuse the empty frozenset singleton
    #    obj = frozenset()
    #    obj2 = marshal.loads(marshal.dumps(obj))
    #    self.assertIs(obj2, obj)

class BufferTest(unittest.TestCase, BaseMarshalUnmarshal):

    def test_bytearray(self):
        b = bytearray(b"abc")
        self.helper(b)
        new = marshal.loads(marshal.dumps(b))
        self.assertEqual(type(new), bytes)

    def test_memoryview(self):
        b = memoryview(b"abc")
        #self.helper(b)
        new = marshal.loads(marshal.dumps(b))
        self.assertEqual(type(new), bytes)

    ## TODO currently we don't support all the variation of arrays. 
    #def test_array(self):
    #    a = array.array('b', b"abc")
    #    new = marshal.loads(marshal.dumps(a))
    #    self.assertEqual(new, b"abc")
