# Copyright (c) 2018, Oracle and/or its affiliates.
# Copyright (C) 1996-2017 Python Software Foundation
#
# Licensed under the PYTHON SOFTWARE FOUNDATION LICENSE VERSION 2

import unittest
import sys

class ExceptionTests(unittest.TestCase):

    def testInvalidTraceback(self):
        try:
            Exception().__traceback__ = 5
        except TypeError as e:
            self.assertIn("__traceback__ must be a traceback", str(e))
        else:
            self.fail("No exception raised")

    def testNoneClearsTracebackAttr(self):
        try:
            raise IndexError(4)
        except:
            tb = sys.exc_info()[2]

        e = Exception()
        e.__traceback__ = tb
        e.__traceback__ = None
        self.assertEqual(e.__traceback__, None)

    def testWithTraceback(self):
        try:
            raise IndexError(4)
        except:
            tb = sys.exc_info()[2]

        e = BaseException().with_traceback(tb)
        self.assertIsInstance(e, BaseException)
        # TODO this dosn't work yet
        #self.assertEqual(e.__traceback__, tb)

        e = IndexError(5).with_traceback(tb)
        self.assertIsInstance(e, IndexError)
        # TODO this dosn't work yet
        #self.assertEqual(e.__traceback__, tb)

        class MyException(Exception):
            pass

        e = MyException().with_traceback(tb)
        self.assertIsInstance(e, MyException)
        # TODO this dosn't work yet
        #self.assertEqual(e.__traceback__, tb)
