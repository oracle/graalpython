# Copyright (c) 2019, 2026, Oracle and/or its affiliates. All rights reserved.
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
import operator
import unittest

class OperatorTest(unittest.TestCase):

    def test_eq(self):
        class C(object):
            def __eq__(self, other):
                raise SyntaxError
        self.assertRaises(TypeError, operator.eq)
        self.assertRaises(SyntaxError, operator.eq, C(), C())
        self.assertFalse(operator.eq(1, 0))
        self.assertFalse(operator.eq(1, 0.0))
        self.assertTrue(operator.eq(1, 1))
        self.assertTrue(operator.eq(1, 1.0))
        self.assertFalse(operator.eq(1, 2))
        self.assertFalse(operator.eq(1, 2.0))

    def test_operator_function_stored_as_class_attribute(self):
        class C:
            add = operator.add
            eq = operator.eq

        self.assertEqual(C().add(1, 2), 3)
        self.assertIs(C().eq(1, 1), True)

    def test_getitem(self):
        a = range(10)
        self.assertRaises(TypeError, operator.getitem)
        self.assertRaises(TypeError, operator.getitem, a, None)
        self.assertEqual(operator.getitem(a, 2), 2)

    def test_setitem_delitem_convert_index_before_type_error(self):
        class RaisingIndex:
            def __index__(self):
                raise ZeroDivisionError

        class Plain:
            pass

        self.assertRaises(ZeroDivisionError, operator.setitem, (), RaisingIndex(), 0)
        self.assertRaises(ZeroDivisionError, operator.setitem, frozenset(), RaisingIndex(), 0)
        self.assertRaises(ZeroDivisionError, operator.setitem, Plain(), RaisingIndex(), 0)
        self.assertRaises(IndexError, operator.setitem, '', 2**100, 0)
        self.assertRaises(ZeroDivisionError, operator.delitem, '', RaisingIndex())
        self.assertRaises(ZeroDivisionError, operator.delitem, Plain(), RaisingIndex())
        self.assertRaises(IndexError, operator.delitem, (), 2**100)
        self.assertRaises(TypeError, operator.setitem, (), 2**40, 0)
        self.assertRaises(TypeError, operator.delitem, '', 2**40)
        self.assertRaises(TypeError, operator.setitem, (), 0, 0)
        self.assertRaises(TypeError, operator.delitem, (), 'a')
        self.assertRaises(TypeError, operator.setitem, 1, RaisingIndex(), 0)
        self.assertRaises(TypeError, operator.delitem, list[int], RaisingIndex())
