# Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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


class A:
    __slots__ = ("hello", "world")
    
    def __init__(self):
        self.hello = "hello"


class TestSlots(unittest.TestCase):
    def test_uninitialized_slot(self):
        obj = A()
        self.assertEqual(obj.hello, "hello")
        with self.assertRaises(AttributeError):
            obj.world
        obj.world = "world"
        self.assertEqual(obj.world, "world")
        
    def test_dict_and_weakref_are_listed_in_slots(self):
        class D: __slots__ = ['__dict__']
        self.assertEqual(tuple(D.__slots__), ('__dict__',))
        self.assertEqual(tuple(D().__slots__), ('__dict__',))

        class WR: __slots__ = ['__weakref__']
        self.assertEqual(tuple(WR.__slots__), ('__weakref__',))
        self.assertEqual(tuple(WR().__slots__), ('__weakref__',))

        class DWR: __slots__ = ['__dict__', '__weakref__']
        self.assertEqual(tuple(DWR.__slots__), ('__dict__', '__weakref__',))
        self.assertEqual(tuple(DWR().__slots__), ('__dict__', '__weakref__',))

    def test_dict_if_slots(self):
        class C: __slots__ = ['a']
        self.assertEqual(tuple(C.__dict__['__slots__']), ('a',))
        
    def test_slots_are_not_sorted(self):
        class C: __slots__ = ['b', 'a']
        self.assertEqual(tuple(C.__slots__), ('b', 'a',))

    def test_forbidden_slot_names(self):
            raised = False
            try:
                class C: 
                    __slots__ = ['__slots__']
            except ValueError:
                raised = True
            assert raised
            
            raised = False
            try:
                class C: 
                    v = 1
                    __slots__ = ['v']
            except ValueError:
                raised = True
            assert raised

    def test_bases_have_class_layout_conflict(self):    
        class A: __slots__ = ["a"]
        class B: __slots__ = ["b"]
        with self.assertRaisesRegex(TypeError, 'multiple bases have instance lay-out conflict'):
            class C(A, B): pass
        with self.assertRaisesRegex(TypeError, 'multiple bases have instance lay-out conflict'):
            class C(A, B): __slots__ = ["a"]
        class B: __slots__ = ["a"]    
        with self.assertRaisesRegex(TypeError, 'multiple bases have instance lay-out conflict'):
            class C(A, B): pass
        with self.assertRaisesRegex(TypeError, 'multiple bases have instance lay-out conflict'):
            class C(A, B): __slots__ = ["a"]
        with self.assertRaisesRegex(TypeError, 'multiple bases have instance lay-out conflict'):
            class C(A, B): __slots__ = ["a", "a"]
            
    def test_no_bases_have_class_layout_conflict(self):
        class A: __slots__ = ["__dict__"]
        class B: __slots__ = ["__dict__"]        
        class C(A, B): pass
        class C(A, B): __slots__ = ["a"]
        class C(A, B): __slots__ = ["__weakref__"]
                
        class A: __slots__ = ["__weakref__"]
        class B: __slots__ = ["__weakref__"]       
        class C(A, B): pass
        class C(A, B): __slots__ = ["a"]
        class C(A, B): __slots__ = ["_dict_"]
        
    def test_slot_disallowed(self):    
        class A: __slots__ = ["__dict__"]
        class B: __slots__ = ["__dict__"]                
        with self.assertRaisesRegex(TypeError, '__dict__ slot disallowed: we already got one'):
            class C(A, B): __slots__ = ["__dict__"]
        
        class A: pass
        class B: __slots__ = ["__dict__"]        
        with self.assertRaisesRegex(TypeError, '__dict__ slot disallowed: we already got one'):
            class C(A, B): __slots__ = ["__dict__"]
        
        class A: __slots__ = ["__weakref__"]
        class B: __slots__ = ["__weakref__"]
        with self.assertRaisesRegex(TypeError, '__weakref__ slot disallowed: either we already got one, or __itemsize__ != 0'):
            class C(A, B): __slots__ = ["__weakref__"]

        class A: pass
        class B: __slots__ = ["__weakref__"]        
        with self.assertRaisesRegex(TypeError, '__weakref__ slot disallowed: either we already got one, or __itemsize__ != 0'):
            class C(A, B): __slots__ = ["__weakref__"]                
        
        class A: pass
        class B: pass
        with self.assertRaisesRegex(TypeError, '__dict__ slot disallowed: we already got one'):
            class C(A, B): __slots__ = ["__dict__", "__dict__"]
        with self.assertRaisesRegex(TypeError, '__weakref__ slot disallowed: either we already got one, or __itemsize__ != 0'):
            class C(A, B): __slots__ = ["__weakref__", "__weakref__"]
            
if __name__ == "__main__":
    unittest.main()
