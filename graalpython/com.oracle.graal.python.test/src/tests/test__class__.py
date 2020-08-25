# Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
from test.support import run_unittest
import types

def find_code_object(t):
    for ob in t:
      if type(ob) is types.CodeType:
        return ob

class BasicTests(unittest.TestCase):

    def tearDown(self):
        nonlocal __class__
        __class__ = BasicTests

    def test_locals01(self):

        class X:
            self.assertFalse ('__class__' in locals(), "__class__ can not be available in class function")
            def methodX(this):
                self.assertFalse ('__class__' in locals(), "__class__ should not be in locals() in a method, where is not used")
        X().methodX()


    def test_locals02(self):

        class X:
            self.assertFalse ('__class__' in locals(), "__class__ can not be available in class function")
            def methodX(this):
                z = __class__
                self.assertTrue ('__class__' in locals(), "__class__ has to be in locals() in a method, where is used")
                self.assertEqual(z, X, "__class__ has to contain the class object X")
        X().methodX()


    def test_inClassFunction(self):

        class X:
            def createY(this):
                class Y:
                    z = __class__
                    self.assertFalse ('__class__' in locals(), "__class__ can not be available in class function")
                    self.assertEqual(z, X, "__class__ has to contain the enclosing class object in a class function")
                    def methodY(this):
                        y = __class__
                        self.assertTrue ('__class__' in locals(), "__class__ has to be in locals() in a method, where is used")
                        self.assertEqual(y, Y, "__class__ has to contain the class object Y")
                return Y

            def methodX(this):
                yy = __class__
                self.assertTrue ('__class__' in locals(), "__class__ has to be in locals() in a method, where is used")
                self.assertEqual(yy, X, "__class__ has to contain the class object X")
        X().methodX()
        X().createY()().methodY()

    def test_dir_in_method_without_usage(self):
         
        class X:
            def method(this):
                self.assertFalse('__class__' in dir(), "__class__ can not be listed (dir()) in method without usage")
        X().method()

    def test_dir_in_method_with_usage(self):
         
        class X66:
            def method(this):
                y = __class__
                self.assertTrue('__class__' in dir(), "__class__ has to be listed (dir()) in method with usage")
                self.assertEqual(y.__name__, "X66")
        X66().method()

    def test_dir_in_method_overwrite_class(self):
         
        class X66:
            def method(this):
                __class__ = 10
                self.assertTrue('__class__' in dir(), "__class__ has to be listed (dir()) in method with usage")
                self.assertEqual(__class__, 10)
        X66().method()

    def test_cells_empty(self):
        
        class X:
            pass
        
        co = find_code_object(self.test_cells_empty.__code__.co_consts)
        self.assertEqual('X', co.co_name, "Expected code object with name 'X'")
        self.assertFalse('__class__' in co.co_cellvars, "__class__ should not be in cellvars of X") 
        self.assertFalse('__class__' in co.co_freevars, "__class__ should not be in freevars of X")

    def test_only_freevars(self):
       
        class X22:
            def m(self):
               z = __class__
	
        co = find_code_object(self.test_only_freevars.__code__.co_consts)
        self.assertEqual('X22', co.co_name, "Expected code object with name 'X'")
        self.assertTrue('__class__' in co.co_cellvars, "__class__ should not be in cellvars of X") 
        self.assertFalse('__class__' in co.co_freevars, "__class__ should not be in freevars of X")
        co = X22.m.__code__
        self.assertFalse('__class__' in co.co_cellvars, "__class__ should not be in cellvars of X.m") 
        self.assertTrue('__class__' in co.co_freevars, "__class__ has to be in freevars of X.m")

    def test_nonlocal__class__usage(self):
        class YN:
            pass
        class XN:
            def fn1(self):
                nonlocal __class__
                __class__ = YN
            def fn2(self):
                return __class__
            def fn3(slef):
                nonlocal __class__
                return __class__

        x = XN()
        self.assertIs(XN, x.fn2())
        self.assertIs(XN, x.fn3())
        x.fn1()
        self.assertIs(YN, x.fn2())
        self.assertIs(YN, x.fn3())

if __name__ == '__main__':
    unittest.main()