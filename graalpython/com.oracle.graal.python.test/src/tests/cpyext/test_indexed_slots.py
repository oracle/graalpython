# Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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

from . import CPyExtTestCase, CPyExtHeapType
__dir__ = __file__.rpartition("/")[0]


class BaseWithSlots:
    __slots__ = ('a', 'b')

class AnotherBaseWithSlots:
    __slots__ = ('b',)

class EmptyBase:
    pass

# This is used to increase tp_basicsize in CPyExtHeapType which needs to be grater that the basic size of the
# base class. We do not use the C struct in any way - in fact in CPython, the `space` member declared here
# overlaps with __slots__ of the base class.
def cmembers(i):
    return f"long space[{i}];"

class TestIndexedSlots(CPyExtTestCase):
    def test_slots_in_base(self):
        N1 = CPyExtHeapType('Na1', bases=(BaseWithSlots,), cmembers=cmembers(4))
        N2 = CPyExtHeapType('Na2', bases=(N1,), cmembers=cmembers(8))

        class M1(EmptyBase, N2):
            pass

        class M2(N2, EmptyBase):
            pass

        for C in (N1, N2, M1, M2):
            x = C()
            x.a, x.b = 12, 13
            self.assertEqual((12, 13), (x.a, x.b))

    def test_slots_in_subclass(self):
        N1 = CPyExtHeapType('Nb1', bases=(EmptyBase,), cmembers=cmembers(4))

        class M1(N1):
            __slots__ = ('a', 'b')

        class M2(N1):
            __slots__ = ('a', 'b')

        class M3(M1):
            __slots__ = ('c',)

        for C in (M1, M2):
            x = C()
            x.a, x.b = 12, 13
            self.assertEqual((12, 13), (x.a, x.b))

        x = M3()
        x.a, x.b, x.c = 22, 23, 24
        self.assertEqual((22, 23, 24), (x.a, x.b, x.c))


    def test_slots_in_base_and_subclass(self):
        N1 = CPyExtHeapType('Nd1', bases=(BaseWithSlots,), cmembers=cmembers(4))

        class M1(N1):
            __slots__ = ('c', 'd')

        class M2(N1):
            __slots__ = ('c', 'd', 'a')

        class M3(M1):
            __slots__ = ('b', 'a')

        for C in (M1, M2, M3):
            x = C()
            x.a, x.b, x.c, x.d = 12, 13, 14, 15
            self.assertEqual((12, 13, 14, 15), (x.a, x.b, x.c, x.d))

    def test_multi_bases(self):
        N1 = CPyExtHeapType('Ne1', bases=(BaseWithSlots, EmptyBase), cmembers=cmembers(6))
        N2 = CPyExtHeapType('Ne2', bases=(EmptyBase, BaseWithSlots), cmembers=cmembers(6))

        class M1(N1):
            pass

        class M2(N1):
            __slots__ = ('b',)

        for C in (N1, N2, M1, M2):
            x = C()
            x.a, x.b = 12, 13
            self.assertEqual((12, 13), (x.a, x.b))

    def test_layout_conflict(self):
        N1 = CPyExtHeapType('Nf1', bases=(BaseWithSlots,), cmembers=cmembers(4))
        self.assertRaises(TypeError, CPyExtHeapType, 'Nf2', bases=(AnotherBaseWithSlots, BaseWithSlots))
        self.assertRaises(TypeError, CPyExtHeapType, 'Nf3', bases=(AnotherBaseWithSlots, N1))
