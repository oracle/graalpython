# Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
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
import copy
import random
import sys
import unittest
from collections import deque

BIG = 100000


def fail():
    raise SyntaxError
    yield 1


class BadCmp:
    def __eq__(self, other):
        raise RuntimeError


class MutateCmp:
    def __init__(self, deque, result):
        self.deque = deque
        self.result = result

    def __eq__(self, other):
        self.deque.clear()
        return self.result


class TestBasic(unittest.TestCase):

    def test_basics(self):
        d = deque(range(-5125, -5000))
        d.__init__(range(200))
        for i in range(200, 400):
            d.append(i)
        for i in reversed(range(-200, 0)):
            d.appendleft(i)
        self.assertEqual(list(d), list(range(-200, 400)))
        self.assertEqual(len(d), 600)

        left = [d.popleft() for i in range(250)]
        self.assertEqual(left, list(range(-200, 50)))
        self.assertEqual(list(d), list(range(50, 400)))

        right = [d.pop() for i in range(250)]
        right.reverse()
        self.assertEqual(right, list(range(150, 400)))
        self.assertEqual(list(d), list(range(50, 150)))

    def test_maxlen(self):
        self.assertRaises(ValueError, deque, 'abc', -1)
        self.assertRaises(ValueError, deque, 'abc', -2)
        it = iter(range(10))
        d = deque(it, maxlen=3)
        self.assertEqual(list(it), [])
        self.assertEqual(repr(d), 'deque([7, 8, 9], maxlen=3)')
        self.assertEqual(list(d), [7, 8, 9])
        self.assertEqual(d, deque(range(10), 3))
        d.append(10)
        self.assertEqual(list(d), [8, 9, 10])
        d.appendleft(7)
        self.assertEqual(list(d), [7, 8, 9])
        d.extend([10, 11])
        self.assertEqual(list(d), [9, 10, 11])
        d.extendleft([8, 7])
        self.assertEqual(list(d), [7, 8, 9])
        d = deque(range(200), maxlen=10)
        d.append(d)
        d = deque(range(10), maxlen=None)
        self.assertEqual(repr(d), 'deque([0, 1, 2, 3, 4, 5, 6, 7, 8, 9])')

    def test_maxlen_zero(self):
        it = iter(range(100))
        deque(it, maxlen=0)
        self.assertEqual(list(it), [])

        it = iter(range(100))
        d = deque(maxlen=0)
        d.extend(it)
        self.assertEqual(list(it), [])

        it = iter(range(100))
        d = deque(maxlen=0)
        d.extendleft(it)
        self.assertEqual(list(it), [])

    def test_maxlen_attribute(self):
        self.assertEqual(deque().maxlen, None)
        self.assertEqual(deque('abc').maxlen, None)
        self.assertEqual(deque('abc', maxlen=4).maxlen, 4)
        self.assertEqual(deque('abc', maxlen=2).maxlen, 2)
        self.assertEqual(deque('abc', maxlen=0).maxlen, 0)
        with self.assertRaises(AttributeError):
            d = deque('abc')
            d.maxlen = 10

    def test_count(self):
        for s in ('', 'abracadabra', 'simsalabim'*500+'abc'):
            s = list(s)
            d = deque(s)
            for letter in 'abcdefghijklmnopqrstuvwxyz':
                self.assertEqual(s.count(letter), d.count(letter), (s, d, letter))
        self.assertRaises(TypeError, d.count)       # too few args
        self.assertRaises(TypeError, d.count, 1, 2) # too many args
        class BadCompare:
            def __eq__(self, other):
                raise ArithmeticError
        d = deque([1, 2, BadCompare(), 3])
        self.assertRaises(ArithmeticError, d.count, 2)
        d = deque([1, 2, 3])
        self.assertRaises(ArithmeticError, d.count, BadCompare())
        class MutatingCompare:
            def __eq__(self, other):
                self.d.pop()
                return True
        m = MutatingCompare()
        d = deque([1, 2, 3, m, 4, 5])
        m.d = d
        self.assertRaises(RuntimeError, d.count, 3)

        # test issue11004
        # block advance failed after rotation aligned elements on right side of block
        d = deque([None]*16)
        for i in range(len(d)):
            d.rotate(-1)
        d.rotate(1)
        self.assertEqual(d.count(1), 0)
        self.assertEqual(d.count(None), 16)

    def test_comparisons(self):
        d = deque('xabc'); d.popleft()
        for e in [d, deque('abc'), deque('ab'), deque(), list(d)]:
            self.assertEqual(d==e, type(d)==type(e) and list(d)==list(e))
            self.assertEqual(d!=e, not(type(d)==type(e) and list(d)==list(e)))

        args = map(deque, ('', 'a', 'b', 'ab', 'ba', 'abc', 'xba', 'xabc', 'cba'))
        for x in args:
            for y in args:
                self.assertEqual(x == y, list(x) == list(y), (x,y))
                self.assertEqual(x != y, list(x) != list(y), (x,y))
                self.assertEqual(x <  y, list(x) <  list(y), (x,y))
                self.assertEqual(x <= y, list(x) <= list(y), (x,y))
                self.assertEqual(x >  y, list(x) >  list(y), (x,y))
                self.assertEqual(x >= y, list(x) >= list(y), (x,y))

    def test_contains(self):
        n = 200

        d = deque(range(n))
        for i in range(n):
            self.assertTrue(i in d)
        self.assertTrue((n+1) not in d)

        # Test detection of mutation during iteration
        d = deque(range(n))
        d[n//2] = MutateCmp(d, False)
        with self.assertRaises(RuntimeError):
            n in d

        # Test detection of comparison exceptions
        d = deque(range(n))
        d[n//2] = BadCmp()
        with self.assertRaises(RuntimeError):
            n in d

    def test_extend(self):
        d = deque('a')
        self.assertRaises(TypeError, d.extend, 1)
        d.extend('bcd')
        self.assertEqual(list(d), list('abcd'))
        d.extend(d)
        self.assertEqual(list(d), list('abcdabcd'))

    @unittest.skipIf(sys.implementation.name == 'cpython' and sys.version_info[0:2] < (3, 5), "skipping for cPython versions < 3.5")
    def test_add(self):
        d = deque()
        e = deque('abc')
        f = deque('def')
        self.assertEqual(d + d, deque())
        self.assertEqual(e + f, deque('abcdef'))
        self.assertEqual(e + e, deque('abcabc'))
        self.assertEqual(e + d, deque('abc'))
        self.assertEqual(d + e, deque('abc'))

        g = deque('abcdef', maxlen=4)
        h = deque('gh')
        self.assertEqual(g + h, deque('efgh'))

        with self.assertRaises(TypeError):
            deque('abc') + 'def'

    def test_iadd(self):
        d = deque('a')
        d += 'bcd'
        self.assertEqual(list(d), list('abcd'))
        d += d
        self.assertEqual(list(d), list('abcdabcd'))

    def test_extendleft(self):
        d = deque('a')
        self.assertRaises(TypeError, d.extendleft, 1)
        d.extendleft('bcd')
        self.assertEqual(list(d), list(reversed('abcd')))
        d.extendleft(d)
        self.assertEqual(list(d), list('abcddcba'))
        d = deque()
        d.extendleft(range(1000))
        self.assertEqual(list(d), list(reversed(range(1000))))
        self.assertRaises(SyntaxError, d.extendleft, fail())

    def test_getitem(self):
        n = 200
        d = deque(range(n))
        l = list(range(n))
        for i in range(n):
            d.popleft()
            l.pop(0)
            if random.random() < 0.5:
                d.append(i)
                l.append(i)
            for j in range(1-len(l), len(l)):
                assert d[j] == l[j]

        d = deque('superman')
        self.assertEqual(d[0], 's')
        self.assertEqual(d[-1], 'n')
        d = deque()
        self.assertRaises(IndexError, d.__getitem__, 0)
        self.assertRaises(IndexError, d.__getitem__, -1)

    @unittest.skipIf(sys.implementation.name == 'cpython' and sys.version_info[0:2] < (3, 5), "skipping for cPython versions < 3.5")
    def test_index(self):
        for n in 1, 2, 30, 40, 200:

            d = deque(range(n))
            for i in range(n):
                self.assertEqual(d.index(i), i)

            with self.assertRaises(ValueError):
                d.index(n+1)

            # Test detection of mutation during iteration
            d = deque(range(n))
            d[n//2] = MutateCmp(d, False)
            with self.assertRaises(RuntimeError):
                d.index(n)

            # Test detection of comparison exceptions
            d = deque(range(n))
            d[n//2] = BadCmp()
            with self.assertRaises(RuntimeError):
                d.index(n)

        # Test start and stop arguments behavior matches list.index()
        elements = 'ABCDEFGHI'
        d = deque(elements * 2)
        s = list(elements * 2)
        for start in range(-5 - len(s)*2, 5 + len(s) * 2):
            for stop in range(-5 - len(s)*2, 5 + len(s) * 2):
                for element in elements + 'Z':
                    try:
                        target = s.index(element, start, stop)
                    except ValueError:
                        self.assertRaises(ValueError, lambda: d.index(element, start, stop))
                    else:
                        self.assertEqual(d.index(element, start, stop), target)

    @unittest.skipIf(sys.implementation.name == 'cpython' and sys.version_info[0:2] < (3, 5), "skipping for cPython versions < 3.5")
    def test_index_bug_24913(self):
        d = deque('A' * 3)
        with self.assertRaises(ValueError):
            i = d.index("Hello world", 0, 4)

    @unittest.skipIf(sys.implementation.name == 'cpython' and sys.version_info[0:2] < (3, 5), "skipping for cPython versions < 3.5")
    def test_imul(self):
        for n in (-10, -1, 0, 1, 2, 10, 1000):
            d = deque()
            d *= n
            self.assertEqual(d, deque())
            self.assertIsNone(d.maxlen)

        for n in (-10, -1, 0, 1, 2, 10, 1000):
            d = deque('a')
            d *= n
            self.assertEqual(d, deque('a' * n))
            self.assertIsNone(d.maxlen)

        for n in (-10, -1, 0, 1, 2, 10, 499, 500, 501, 1000):
            d = deque('a', 500)
            d *= n
            self.assertEqual(d, deque('a' * min(n, 500)))
            self.assertEqual(d.maxlen, 500)

        for n in (-10, -1, 0, 1, 2, 10, 1000):
            d = deque('abcdef')
            d *= n
            self.assertEqual(d, deque('abcdef' * n))
            self.assertIsNone(d.maxlen)

        for n in (-10, -1, 0, 1, 2, 10, 499, 500, 501, 1000):
            d = deque('abcdef', 500)
            d *= n
            self.assertEqual(d, deque(('abcdef' * n)[-500:]))
            self.assertEqual(d.maxlen, 500)

    @unittest.skipIf(sys.implementation.name == 'cpython' and sys.version_info[0:2] < (3, 5), "skipping for cPython versions < 3.5")
    def test_mul(self):
        d = deque('abc')
        self.assertEqual(d * -5, deque())
        self.assertEqual(d * 0, deque())
        self.assertEqual(d * 1, deque('abc'))
        self.assertEqual(d * 2, deque('abcabc'))
        self.assertEqual(d * 3, deque('abcabcabc'))

        self.assertEqual(deque() * 0, deque())
        self.assertEqual(deque() * 1, deque())
        self.assertEqual(deque() * 5, deque())

        self.assertEqual(-5 * d, deque())
        self.assertEqual(0 * d, deque())
        self.assertEqual(1 * d, deque('abc'))
        self.assertEqual(2 * d, deque('abcabc'))
        self.assertEqual(3 * d, deque('abcabcabc'))

        d = deque('abc', maxlen=5)
        self.assertEqual(d * -5, deque())
        self.assertEqual(d * 0, deque())
        self.assertEqual(d * 1, deque('abc'))
        self.assertEqual(d * 2, deque('bcabc'))
        self.assertEqual(d * 30, deque('bcabc'))

    def test_setitem(self):
        n = 200
        d = deque(range(n))
        for i in range(n):
            d[i] = 10 * i
        self.assertEqual(list(d), [10*i for i in range(n)])
        l = list(d)
        for i in range(1-n, 0, -1):
            d[i] = 7*i
            l[i] = 7*i
        self.assertEqual(list(d), l)

    def test_delitem(self):
        n = 500         # O(n**2) test, don't make this too big
        d = deque(range(n))
        self.assertRaises(IndexError, d.__delitem__, -n-1)
        self.assertRaises(IndexError, d.__delitem__, n)
        for i in range(n):
            self.assertEqual(len(d), n-i)
            j = random.randrange(-len(d), len(d))
            val = d[j]
            self.assertTrue(val in d)
            del d[j]
            self.assertTrue(val not in d)
        self.assertEqual(len(d), 0)

    def test_reverse(self):
        n = 500         # O(n**2) test, don't make this too big
        data = [random.random() for i in range(n)]
        for i in range(n):
            d = deque(data[:i])
            r = d.reverse()
            self.assertEqual(list(d), list(reversed(data[:i])))
            self.assertIs(r, None)
            d.reverse()
            self.assertEqual(list(d), data[:i])
        self.assertRaises(TypeError, d.reverse, 1)          # Arity is zero

    def test_rotate(self):
        s = tuple('abcde')
        n = len(s)

        d = deque(s)
        d.rotate(1)             # verify rot(1)
        self.assertEqual(''.join(d), 'eabcd')

        d = deque(s)
        d.rotate(-1)            # verify rot(-1)
        self.assertEqual(''.join(d), 'bcdea')
        d.rotate()              # check default to 1
        self.assertEqual(tuple(d), s)

        for i in range(n*3):
            d = deque(s)
            e = deque(d)
            d.rotate(i)         # check vs. rot(1) n times
            for j in range(i):
                e.rotate(1)
            self.assertEqual(tuple(d), tuple(e))
            d.rotate(-i)        # check that it works in reverse
            self.assertEqual(tuple(d), s)
            e.rotate(n-i)       # check that it wraps forward
            self.assertEqual(tuple(e), s)

        for i in range(n*3):
            d = deque(s)
            e = deque(d)
            d.rotate(-i)
            for j in range(i):
                e.rotate(-1)    # check vs. rot(-1) n times
            self.assertEqual(tuple(d), tuple(e))
            d.rotate(i)         # check that it works in reverse
            self.assertEqual(tuple(d), s)
            e.rotate(i-n)       # check that it wraps backaround
            self.assertEqual(tuple(e), s)

        d = deque(s)
        e = deque(s)
        e.rotate(BIG+17)        # verify on long series of rotates
        dr = d.rotate
        for i in range(BIG+17):
            dr()
        self.assertEqual(tuple(d), tuple(e))

        self.assertRaises(TypeError, d.rotate, 'x')   # Wrong arg type
        self.assertRaises(TypeError, d.rotate, 1, 10) # Too many args

        d = deque()
        d.rotate()              # rotate an empty deque
        self.assertEqual(d, deque())

    def test_len(self):
        d = deque('ab')
        self.assertEqual(len(d), 2)
        d.popleft()
        self.assertEqual(len(d), 1)
        d.pop()
        self.assertEqual(len(d), 0)
        self.assertRaises(IndexError, d.pop)
        self.assertEqual(len(d), 0)
        d.append('c')
        self.assertEqual(len(d), 1)
        d.appendleft('d')
        self.assertEqual(len(d), 2)
        d.clear()
        self.assertEqual(len(d), 0)

    def test_underflow(self):
        d = deque()
        self.assertRaises(IndexError, d.pop)
        self.assertRaises(IndexError, d.popleft)

    def test_clear(self):
        d = deque(range(100))
        self.assertEqual(len(d), 100)
        d.clear()
        self.assertEqual(len(d), 0)
        self.assertEqual(list(d), [])
        d.clear()               # clear an empty deque
        self.assertEqual(list(d), [])

    def test_remove(self):
        d = deque('abcdefghcij')
        d.remove('c')
        self.assertEqual(d, deque('abdefghcij'))
        d.remove('c')
        self.assertEqual(d, deque('abdefghij'))
        self.assertRaises(ValueError, d.remove, 'c')
        self.assertEqual(d, deque('abdefghij'))

        # Handle comparison errors
        d = deque(['a', 'b', BadCmp(), 'c'])
        e = deque(d)
        self.assertRaises(RuntimeError, d.remove, 'c')
        for x, y in zip(d, e):
            # verify that original order and values are retained.
            self.assertTrue(x is y)

        # Handle evil mutator
        for match in (True, False):
            d = deque(['ab'])
            d.extend([MutateCmp(d, match), 'c'])
            self.assertRaises(IndexError, d.remove, 'c')
            self.assertEqual(d, deque())

    def test_init(self):
        self.assertRaises(TypeError, deque, 'abc', 2, 3)
        self.assertRaises(TypeError, deque, 1)

    def test_hash(self):
        self.assertRaises(TypeError, hash, deque('abc'))

    def test_long_steadystate_queue_popleft(self):
        for size in (0, 1, 2, 100, 1000):
            d = deque(range(size))
            append, pop = d.append, d.popleft
            for i in range(size, BIG):
                append(i)
                x = pop()
                if x != i - size:
                    self.assertEqual(x, i-size)
            self.assertEqual(list(d), list(range(BIG-size, BIG)))

    def test_long_steadystate_queue_popright(self):
        for size in (0, 1, 2, 100, 1000):
            d = deque(reversed(range(size)))
            append, pop = d.appendleft, d.pop
            for i in range(size, BIG):
                append(i)
                x = pop()
                if x != i - size:
                    self.assertEqual(x, i-size)
            self.assertEqual(list(reversed(list(d))),
                             list(range(BIG-size, BIG)))

    def test_big_queue_popleft(self):
        pass
        d = deque()
        append, pop = d.append, d.popleft
        for i in range(BIG):
            append(i)
        for i in range(BIG):
            x = pop()
            if x != i:
                self.assertEqual(x, i)

    def test_big_queue_popright(self):
        d = deque()
        append, pop = d.appendleft, d.pop
        for i in range(BIG):
            append(i)
        for i in range(BIG):
            x = pop()
            if x != i:
                self.assertEqual(x, i)

    def test_big_stack_right(self):
        d = deque()
        append, pop = d.append, d.pop
        for i in range(BIG):
            append(i)
        for i in reversed(range(BIG)):
            x = pop()
            if x != i:
                self.assertEqual(x, i)
        self.assertEqual(len(d), 0)

    def test_big_stack_left(self):
        d = deque()
        append, pop = d.appendleft, d.popleft
        for i in range(BIG):
            append(i)
        for i in reversed(range(BIG)):
            x = pop()
            if x != i:
                self.assertEqual(x, i)
        self.assertEqual(len(d), 0)

    def test_roundtrip_iter_init(self):
        d = deque(range(200))
        e = deque(d)
        self.assertNotEqual(id(d), id(e))
        self.assertEqual(list(d), list(e))

    @unittest.skipIf(sys.implementation.name == 'cpython' and sys.version_info[0:2] < (3, 5), "skipping for cPython versions < 3.5")
    def test_copy(self):
        mut = [10]
        d = deque([mut])
        e = copy.copy(d)
        self.assertEqual(list(d), list(e))
        mut[0] = 11
        self.assertNotEqual(id(d), id(e))
        self.assertEqual(list(d), list(e))

        for i in range(5):
            for maxlen in range(-1, 6):
                s = [random.random() for j in range(i)]
                d = deque(s) if maxlen == -1 else deque(s, maxlen)
                e = d.copy()
                self.assertEqual(d, e)
                self.assertEqual(d.maxlen, e.maxlen)
                self.assertTrue(all(x is y for x, y in zip(d, e)))

    @unittest.skipIf(sys.implementation.name == 'cpython' and sys.version_info[0:2] < (3, 5), "skipping for cPython versions < 3.5")
    def test_copy_method(self):
        mut = [10]
        d = deque([mut])
        e = d.copy()
        self.assertEqual(list(d), list(e))
        mut[0] = 11
        self.assertNotEqual(id(d), id(e))
        self.assertEqual(list(d), list(e))

    def test_reversed(self):
        for s in ('abcd', range(2000)):
            self.assertEqual(list(reversed(deque(s))), list(reversed(s)))

    def test_reversed_new(self):
        klass = type(reversed(deque()))
        for s in ('abcd', range(2000)):
            self.assertEqual(list(klass(deque(s))), list(reversed(s)))
