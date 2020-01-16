# Copyright (c) 2018, 2020, Oracle and/or its affiliates.
# Copyright (C) 1996-2017 Python Software Foundation
#
# Licensed under the PYTHON SOFTWARE FOUNDATION LICENSE VERSION 2
import seq_tests
#import pickle
from compare import CompareTest

class TupleTest(seq_tests.CommonTest):

    type2test = tuple

    def test_constructors(self):
        super().test_constructors()
        # calling built-in types without argument must return empty
        self.assertEqual(tuple(), ())
        t0_3 = (0, 1, 2, 3)
        t0_3_bis = tuple(t0_3)
        self.assertTrue(t0_3 is t0_3_bis)
        self.assertEqual(tuple([]), ())
        self.assertEqual(tuple([0, 1, 2, 3]), (0, 1, 2, 3))
        self.assertEqual(tuple(''), ())
        self.assertEqual(tuple('spam'), ('s', 'p', 'a', 'm'))

    def test_literal(self):
        self.assertEqual((1,2,3), (*[1,2,3],))

    def test_truth(self):
        super().test_truth()
        self.assertTrue(not ())
        self.assertTrue((42, ))

    def test_len(self):
        super().test_len()
        self.assertEqual(len(()), 0)
        self.assertEqual(len((0,)), 1)
        self.assertEqual(len((0, 1, 2)), 3)

    def test_iadd(self):
        super().test_iadd()
        u = (0, 1)
        u2 = u
        u += (2, 3)
        self.assertTrue(u is not u2)

    def test_imul(self):
        super().test_imul()
        u = (0, 1)
        u2 = u
        u *= 3
        self.assertTrue(u is not u2)

    def test_tupleresizebug(self):
        # Check that a specific bug in _PyTuple_Resize() is squashed.
        def f():
            for i in range(1000):
                yield i
        self.assertEqual(list(tuple(f())), list(range(1000)))

# TODO This test is currently faing on the gate. If you run the test separately
# it's ok.
#    def test_hash(self):
        # See SF bug 942952:  Weakness in tuple hash
        # The hash should:
        #      be non-commutative
        #      should spread-out closely spaced values
        #      should not exhibit cancellation in tuples like (x,(x,y))
        #      should be distinct from element hashes:  hash(x)!=hash((x,))
        # This test exercises those cases.
        # For a pure random hash and N=50, the expected number of occupied
        #      buckets when tossing 252,600 balls into 2**32 buckets
        #      is 252,592.6, or about 7.4 expected collisions.  The
        #      standard deviation is 2.73.  On a box with 64-bit hash
        #      codes, no collisions are expected.  Here we accept no
        #      more than 15 collisions.  Any worse and the hash function
        #      is sorely suspect.

#        N=50
#        base = list(range(N))
#        xp = [(i, j) for i in base for j in base]
#        inps = base + [(i, j) for i in base for j in xp] + \
#                     [(i, j) for i in xp for j in base] + xp + list(zip(base))
#        collisions = len(inps) - len(set(map(hash, inps)))
#        self.assertTrue(collisions <= 15)

    def test_repr(self):
        l0 = tuple()
        l2 = (0, 1, 2)
        a0 = self.type2test(l0)
        a2 = self.type2test(l2)

        self.assertEqual(str(a0), repr(l0))
        self.assertEqual(str(a2), repr(l2))
        self.assertEqual(repr(a0), "()")
        self.assertEqual(repr(a2), "(0, 1, 2)")

    def test_repr_large(self):
        # Check the repr of large list objects
        def check(n):
            l = (0,) * n
            s = repr(l)
            self.assertEqual(s,
                '(' + ', '.join(['0'] * n) + ')')
        check(10)       # check our checking code
        check(1000000)

#    def test_iterator_pickle(self):
#        # Userlist iterators don't support pickling yet since
#        # they are based on generators.
#        data = self.type2test([4, 5, 6, 7])
#        for proto in range(pickle.HIGHEST_PROTOCOL + 1):
#            itorg = iter(data)
#            d = pickle.dumps(itorg, proto)
#            it = pickle.loads(d)
#            self.assertEqual(type(itorg), type(it))
#            self.assertEqual(self.type2test(it), self.type2test(data))
#
#            it = pickle.loads(d)
#            next(it)
#            d = pickle.dumps(it, proto)
#            self.assertEqual(self.type2test(it), self.type2test(data)[1:])

#    def test_reversed_pickle(self):
#        data = self.type2test([4, 5, 6, 7])
#        for proto in range(pickle.HIGHEST_PROTOCOL + 1):
#            itorg = reversed(data)
#            d = pickle.dumps(itorg, proto)
#            it = pickle.loads(d)
#            self.assertEqual(type(itorg), type(it))
#            self.assertEqual(self.type2test(it), self.type2test(reversed(data)))
#
#            it = pickle.loads(d)
#            next(it)
#            d = pickle.dumps(it, proto)
#            self.assertEqual(self.type2test(it), self.type2test(reversed(data))[1:])

    def test_no_comdat_folding(self):
        # Issue 8847: In the PGO build, the MSVC linker's COMDAT folding
        # optimization causes failures in code that relies on distinct
        # function addresses.
        class T(tuple): pass
        with self.assertRaises(TypeError):
            [3,] + T((1,2))

    def assertLess(self, left, right):
        self.assertTrue(left < right)
        self.assertTrue(left <= right)
        self.assertFalse(left>right)
        self.assertFalse(left>=right)
        self.assertFalse(left==right)
        self.assertTrue(left!=right)

    def test_lexicographic_ordering(self):
        # Issue 21100
        a = self.type2test([1, 2])
        b = self.type2test([1, 2, 0])
        c = self.type2test([1, 3])
        self.assertLess(a, b)
        self.assertLess(b, c)

    def test_index(self):
        super().test_index()
        t = (0, 1, 2, 3, 4, 5)
        self.assertEqual(t.index(0, False, True), 0)
        self.assertRaises(TypeError, t.index, 1, 1.0)
        self.assertRaises(TypeError, t.index, 1, 1.0, 2.0)
        self.assertRaises(TypeError, t.index, 1, "a", 2.0)

    def test_index_class(self):
        t = (0, 1, 2, 3, 4, 5)
        class IndexI():
            def __index__(self):
                return 1;

        class IndexII():
            def __index__(self):
                return 29;

        self.assertEqual(t.index(3, IndexI()), 3)
        self.assertEqual(t.index(3, 1, IndexII()), 3)
        self.assertEqual(t.index(3, IndexI(), IndexII()), 3)

        class IndexF():
            def __index__(self):
                return 1.0;

        self.assertRaises(TypeError, t.index, 3, IndexF())

        class IndexO():
            def __index__(self):
                return 'a';

        self.assertRaises(TypeError, t.index, 3, IndexO())

        class IndexI2():
            def __index__(self):
                return IndexI();

        self.assertRaises(TypeError, t.index, 3, IndexI2())
        self.assertEqual(t.index(0, False, True), 0)

    def test_getItem_class(self):

        def raiseTypeError(tuple, index):
            try:
                tuple[index]
                self.assertTrue(False, "Operation {} [{}] should raise TypeError".format(tuple, index))
            except TypeError:
                pass

        t = (0, 1, 2, 3, 4, 5)

        self.assertEqual(t[True], 1)

        class IndexI():
            def __index__(self):
                return 1;
        self.assertEqual(t[IndexI()], 1)

        class IndexF():
            def __index__(self):
                return 1.0;

        raiseTypeError(t, IndexF())

        t = (NotImplemented,)
        self.assertEqual(t[0], NotImplemented)


# Tests for Truffle specializations
    def test_lying_tuple(self):
        class MyTuple(tuple):
            def __iter__(self):
                yield 1

        t = (2,)
        tt = tuple((2,))
        self.assertEqual(t,tt)

        ttt = tuple(t)
        self.assertEqual(t,ttt)
        self.assertEqual(tt,ttt)
        self.assertTrue(ttt is t)

        m = MyTuple((2,))
        mt = MyTuple(t)
        mm = MyTuple(m)
        tm = tuple(m)

        self.assertEqual(m,t)
        self.assertEqual(m,mt)
        self.assertNotEqual(m,mm)
        self.assertNotEqual(tm, m)
        self.assertNotEqual(tm, mt)
        self.assertEqual(tm, mm)
        self.assertFalse(m is t)
        self.assertFalse(m is mt)
        self.assertFalse(m is tm)
        self.assertFalse(m is mm)

    def test_creating_tuple(self):
        class MyTuple(tuple):
            pass

        def maketuple(t):
            return tuple(t)

        a = MyTuple((1,2))
        b = tuple(a)
        self.assertFalse(a is b)

        b = MyTuple(a)
        self.assertFalse(a is b)

        b = tuple((1,2))
        self.assertFalse(maketuple(a) is maketuple(b))
        self.assertTrue(maketuple(b) is maketuple(b))
        self.assertTrue(tuple(b) is b)
        self.assertFalse(tuple(a) is a)


class TupleCompareTest(CompareTest):

    def test_compare(self):
        t1 = (1, 2, 3)
        t2 = (1,2,3,0)
        t3 = (1,2,3,4)

        self.comp_eq(t1, t1)

        self.comp_ne(t1, t2)
        self.comp_ne(t2, t3)

        self.comp_ge(t1, t1, True)
        self.comp_ge(t2, t1, False)
        self.comp_ge(t3, t2, False)
        self.comp_ge(t3, t1, False)

        self.comp_le(t1, t1, True)
        self.comp_le(t1, t2, False)
        self.comp_le(t2, t3, False)
        self.comp_le(t1, t3, False)

        self.comp_lt(t1, t2)
        self.comp_lt(t2, t3)
        self.comp_lt(t1, t3)

        self.comp_gt(t2, t1)
        self.comp_gt(t3, t2)
        self.comp_gt(t3, t1)

    def test_equal_other(self):
        def tryWithOtherType(left, right):
            self.assertFalse(left == right, "Operation {} == {} should be False".format(left, right))
            self.assertTrue(left != right, "Operation {} != {} should be True".format(left, right))

        t1 = (1, 2, 3)
        tryWithOtherType(t1, 1)
        tryWithOtherType(t1, 'hello')
        tryWithOtherType(t1, False)
        tryWithOtherType(t1, [1, 2, 3])
        tryWithOtherType(t1, {1, 2, 3})
        tryWithOtherType(t1, {'one':1, 'two':2, 'three':3})

    def test_raiseTypeError(self):
        def tryWithOtherType(left, right):
            def raiseTypeError(left, op, right):
                try:
                    if op == "<":
                        left < right
                    elif op == ">":
                        left > right
                    elif op == "<=":
                        left <= right
                    elif op == ">=":
                        left >= right
                    self.assertTrue(False, "Operation {} {} {} should raise TypeError".format(left, op, right))
                except TypeError:
                    pass

            raiseTypeError(left, "<", right)
            raiseTypeError(left, ">", right)
            raiseTypeError(left, "<=", right)
            raiseTypeError(left, ">=", right)

        t1 = (1, 2, 3)
        tryWithOtherType(t1, 1)
        tryWithOtherType(t1, True)
        tryWithOtherType(t1, 'hello')

    def test_extendingClass(self):
        class MyTuple(tuple):
            def __eq__(self, value):
                return 'eq'
            def __ne__(self, value):
                return value;
            def __gt__(self, value):
                return 10
            def __lt__(self, value):
                return 11.11
            def __ge__(self, value):
                return value + 5
            def __le__(self, value):
                r = super().__le__(value)
                return "OK:" + str(r)

        t1 = MyTuple((1, 10))
        self.assertEqual(t1 == 1, 'eq')
        self.assertEqual(t1 != 'ne', 'ne')
        self.assertEqual(t1 > 'ne', 10)
        self.assertEqual(t1 < 1, 11.11)
        self.assertEqual(t1 >= 6, 11)
        self.assertEqual(t1 <= (1, 1), 'OK:False')
        self.assertEqual(t1 <= (1, 10), 'OK:True')
        self.assertEqual(t1 <= (1, 10, 0), 'OK:True')

    def test_slice(self):
        t1 = tuple(range (1, 22, 2))
        s = slice(2, 6)
        self.assertEqual(t1[s], (5, 7, 9, 11))


def test_same_id():
    empty_ids = set([id(tuple()) for i in range(100)])
    assert len(empty_ids) == 1


def test_hashing():
    assert isinstance(hash((1,2)), int)
    try:
        hash(([],))
    except TypeError as e:
        assert "unhashable type: 'list'" in str(e)
    else:
        assert False
