# Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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

import platform
import sys
import unittest

# The platform.system() == 'Java' is to make it possible to run with Jython
if (platform.system() == 'Java' or sys.implementation.name == "graalpy") and \
        not __graalpython__.is_native:
    from java.util import Arrays
    from java.lang import StringBuilder
    import jarray


    class AbstractJArrayTest(unittest.TestCase):
        type = None
        default_value = None
        instance = None
        bad_instance = object()
        assertValueEqual = unittest.TestCase.assertEqual

        def test_zeros(self):
            if self.type is None:
                return
            array = jarray.zeros(2, self.type)
            self.assertValueEqual(len(array), 2)
            self.assertValueEqual(array[0], self.default_value)
            self.assertValueEqual(array[1], self.default_value)
            array[0] = self.instance
            self.assertValueEqual(array[0], self.instance)
            with self.assertRaises(TypeError):
                array[0] = self.bad_instance
            Arrays.fill(array, self.instance)
            self.assertValueEqual(array[0], self.instance)
            self.assertValueEqual(array[1], self.instance)
            self.assertRaises(TypeError, jarray.zeros, None)

        def test_array(self):
            if self.type is None:
                return
            array = jarray.array([self.instance, self.default_value], self.type)
            self.assertValueEqual(array[0], self.instance)
            self.assertValueEqual(array[1], self.default_value)
            with self.assertRaises(TypeError):
                jarray.array([self.instance, self.bad_instance], self.type)

            def gen():
                yield self.instance
                yield self.default_value

            array = jarray.array(gen(), self.type)
            self.assertValueEqual(array[0], self.instance)
            self.assertValueEqual(array[1], self.default_value)
            with self.assertRaises(TypeError):
                jarray.array(5, self.type)


    class BooleanJArrayTest(AbstractJArrayTest, unittest.TestCase):
        type = 'z'
        default_value = False
        instance = True


    class ByteJArrayTest(AbstractJArrayTest, unittest.TestCase):
        type = 'b'
        default_value = 0
        instance = -1


    class CharJArrayTest(AbstractJArrayTest, unittest.TestCase):
        type = 'c'
        default_value = '\0'
        instance = 'a'


    class ShortJArrayTest(AbstractJArrayTest, unittest.TestCase):
        type = 'h'
        default_value = 0
        instance = 266


    class IntJArrayTest(AbstractJArrayTest, unittest.TestCase):
        type = 'i'
        default_value = 0
        instance = 123456


    class LongJArrayTest(AbstractJArrayTest, unittest.TestCase):
        type = 'l'
        default_value = 0
        instance = 1099511627776

    # TODO interop doesn't support assigning doubles to a float array: GR-27806
    # class FloatJArrayTest(AbstractJArrayTest, unittest.TestCase):
    #     type = 'f'
    #     default_value = 0.0
    #     instance = 0.14
    #     assertValueEqual = unittest.TestCase.assertAlmostEqual


    class DoubleJArrayTest(AbstractJArrayTest, unittest.TestCase):
        type = 'd'
        default_value = 0.0
        instance = 0.14
        assertValueEqual = unittest.TestCase.assertAlmostEqual


    class ObjectJArrayTest(AbstractJArrayTest, unittest.TestCase):
        type = StringBuilder
        default_value = None
        instance = StringBuilder("aaa")


    class ErrorTest(unittest.TestCase):
        def test_wrong_type(self):
            with self.assertRaises(ValueError):
                jarray.array([1, 2], 'x')
            with self.assertRaises(TypeError):
                jarray.array([1, 2])
            with self.assertRaises(TypeError):
                jarray.array([1, 2], 6)
            with self.assertRaises(TypeError):
                jarray.array([1, 2], StringBuilder())

if __name__ == '__main__':
    unittest.main()
