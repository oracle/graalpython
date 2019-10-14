# Copyright (c) 2018, 2019, Oracle and/or its affiliates.
# Copyright (C) 1996-2017 Python Software Foundation
#
# Licensed under the PYTHON SOFTWARE FOUNDATION LICENSE VERSION 2

import seq_tests
import sys
# import pickle

LONG_NUMBER = 6227020800;

import list_tests
from compare import CompareTest


class ListTest(list_tests.CommonTest):
    type2test = list

    def test_basic(self):
        self.assertEqual(list([]), [])
        l0_3 = [0, 1, 2, 3]
        l0_3_bis = list(l0_3)
        self.assertEqual(l0_3, l0_3_bis)
        self.assertTrue(l0_3 is not l0_3_bis)
        self.assertEqual(list(()), [])
        self.assertEqual(list((0, 1, 2, 3)), [0, 1, 2, 3])
        self.assertEqual(list(''), [])
        self.assertEqual(list('spam'), ['s', 'p', 'a', 'm'])

#        if sys.maxsize == 0x7fffffff:
            # This test can currently only work on 32-bit machines.
            # XXX If/when PySequence_Length() returns a ssize_t, it should be
            # XXX re-enabled.
            # Verify clearing of bug #556025.
            # This assumes that the max data size (sys.maxint) == max
            # address size this also assumes that the address size is at
            # least 4 bytes with 8 byte addresses, the bug is not well
            # tested
            #
            # Note: This test is expected to SEGV under Cygwin 1.3.12 or
            # earlier due to a newlib bug.  See the following mailing list
            # thread for the details:

            #     http://sources.redhat.com/ml/newlib/2002/msg00369.html
# TODO currently we get OutOfMemmory Exception
#            self.assertRaises(MemoryError, list, range(sys.maxsize // 2))

        # This code used to segfault in Py2.4a3
        x = []
        x.extend(-y for y in x)
        self.assertEqual(x, [])

        l = [0x1FFFFFFFF, 1, 2, 3, 4]
        l[0] = "hello"
        self.assertEqual(l, ["hello", 1, 2, 3, 4])

    def test_literal(self):
        self.assertEqual([1,2,3], [*[1,2,3]])
        self.assertEqual([1,2,3], [*(1,2,3)])
        self.assertEqual([1,2,3,4,5,6], [*(1,2,3), *(4,5,6)])

        # this will certainly create a list where the capacity of the storage is not exhausted, i.e., 'length < cap',
        # and so the storage contains null elements
        l = []
        for c in "abcdefghijk":
            l.append(c)
        self.assertEqual(['a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k'], [*l])

    def test_truth(self):
        super().test_truth()
        self.assertTrue(not [])
        self.assertTrue([42])

    def test_identity(self):
        self.assertTrue([] is not [])

    def test_len(self):
        super().test_len()
        self.assertEqual(len([]), 0)
        self.assertEqual(len([0]), 1)
        self.assertEqual(len([0, 1, 2]), 3)

    def test_overflow(self):
        lst = [4, 5, 6, 7]
        n = int((sys.maxsize * 2 + 2) // len(lst))

        def mul(a, b): return a * b

        def imul(a, b): a *= b

        self.assertRaises((MemoryError, OverflowError), mul, lst, n)
        self.assertRaises((MemoryError, OverflowError), imul, lst, n)

    def test_repr_large(self):

        # Check the repr of large list objects
        def check(n):
            l = [0] * n
            s = repr(l)
            self.assertEqual(s,
                '[' + ', '.join(['0'] * n) + ']')

        check(10)  # check our checking code
        check(1000000)

    # TODO currently sulong crashes when pickle.dumps is used.

    '''
    def test_iterator_pickle(self):
        # Userlist iterators don't support pickling yet since
        # they are based on generators.
        data = self.type2test([4, 5, 6, 7])
        it = itorg = iter(data)
        d = pickle.dumps(it)
        it = pickle.loads(d)
        self.assertEqual(type(itorg), type(it))
        self.assertEqual(self.type2test(it), self.type2test(data))

        it = pickle.loads(d)
        next(it)
        d = pickle.dumps(it)
        self.assertEqual(self.type2test(it), self.type2test(data)[1:])

    def test_reversed_pickle(self):
        data = self.type2test([4, 5, 6, 7])
        it = itorg = reversed(data)
        d = pickle.dumps(it)
        it = pickle.loads(d)
        self.assertEqual(type(itorg), type(it))
        self.assertEqual(self.type2test(it), self.type2test(reversed(data)))

        it = pickle.loads(d)
        next(it)
        d = pickle.dumps(it)
        self.assertEqual(self.type2test(it), self.type2test(reversed(data))[1:])
    '''

    def test_no_comdat_folding(self):

        # Issue 8847: In the PGO build, the MSVC linker's COMDAT folding
        # optimization causes failures in code that relies on distinct
        # function addresses.
        class L(list): pass

        with self.assertRaises(TypeError):
            (3,) + L([1, 2])

    # ======== Specific test for Graal Python ======

    def test_getitem(self):
        l = [1, 2, 3]
        self.assertEqual(1, l[False])
        
        class IdxObj:
            __cnt = 0
            def __index__(self):
                cur = self.__cnt
                self.__cnt += 1
                return cur
        idxObj = IdxObj()
        cpy = [l[idxObj], l[idxObj], l[idxObj]]
        self.assertEqual(cpy, l)

    def pop_all_list(self, list):
        size = len(list)
        self.assertRaises(IndexError, list.pop, size)
        self.assertRaises(IndexError, list.pop, 0 - size - 1)
        for i in range (size - 1, -1, -1):
            self.assertEqual(list[i], list.pop())
        self.assertRaises(IndexError, list.pop)

    def test_pop_int(self):
        l = [1, 2, 3, 4]
        self.pop_all_list(l)

        l = list([1, 2, 3, 4])
        self.pop_all_list(l)

    def test_pop_long(self):
        l = [LONG_NUMBER + 1, LONG_NUMBER + 2, LONG_NUMBER + 3, LONG_NUMBER + 4]
        self.pop_all_list(l)

        l = list([LONG_NUMBER + 1, LONG_NUMBER + 2, LONG_NUMBER + 3, LONG_NUMBER + 4])
        self.pop_all_list(l)

    def test_pop_double(self):
        l = [1.1, 2.1, 3.1, 4.1]
        self.pop_all_list(l)

        l = list([1.1, 2.1, 3.1, 4.1])
        self.pop_all_list(l)

    def test_pop_string(self):
        l = ['a', 'h', 'o', 'j']
        self.pop_all_list(l)

        l = list('ahoj')
        self.pop_all_list(l)

    def test_pop_mix(self):
        l = [1, 1.1, 'h', 'hello', [1, 2, 3]]
        self.pop_all_list(l)

        l = list([1, 1.1, 'h', 'hello', [1, 2, 3]])
        self.pop_all_list(l)

    def test_pop_boolean(self):
        l = [True, False, True, False]
        self.assertEqual(True, l.pop(False))
        self.assertEqual(True, l.pop(True))
        self.pop_all_list(l)

    def test_pop_border(self):
        l = [1, 2, 5];
        self.assertRaises(IndexError, l.pop, LONG_NUMBER)

    def test_del_item(self):
        l = [1, 2, 3]
        l.__delitem__(0)
        self.assertEqual([2, 3], l)
        l.__delitem__(-1)
        self.assertEqual([2], l)

        l = [1, 2, 3]
        del(l[1])
        self.assertEqual([1, 3], l)
        del(l[False])
        self.assertEqual([3], l)

        l = [1.1, 2.1, 3.1]
        l.__delitem__(0)
        self.assertEqual([2.1, 3.1], l)
        l.__delitem__(-1)
        self.assertEqual([2.1], l)

        l = [1.1, 2.1, 3.1]
        del(l[1])
        self.assertEqual([1.1, 3.1], l)

        l = ["1", "2", "3", "4", "5", "6"]
        del l[4]
        self.assertEqual(["1", "2", "3", "4", "6"], l)

    def test_del_border(self):
        l = [1, 2, 3]
        self.assertRaises(IndexError, l.__delitem__, 3)
        self.assertRaises(IndexError, l.__delitem__, -4)
        self.assertRaises(IndexError, l.__delitem__, LONG_NUMBER)
        self.assertRaises(TypeError, l.__delitem__, 'a')

    def slice_test(self, l, s, expected):
        result = l[s]
        if (s.step == None):
            result2 = l[s.start:s.stop]
        else:
            result2 = l[s.start:s.stop:s.step]
        self.assertEqual(result, result2, "list[s] and list[s.start:s.stop:s.step] has to be same. Fails with [{}:{}:{}]".format(s.start, s.stop, s.step))
        self.assertEqual(result, expected, "list[{}:{}:{}] should be {}, but is {}".format(s.start, s.stop, s.step, expected, result))

    def test_slice(self):
        self.slice_test(list(range(0, 20)), slice(1, 5), [1, 2, 3, 4])
        self.slice_test(list(range(0, 20)), slice(0, 5), [0, 1, 2, 3, 4])
        self.slice_test(list(range(0, 20)), slice(-1, 5), [])
        self.slice_test(list(range(0, 20)), slice(-15, 5), [])
        self.slice_test(list(range(0, 20)), slice(-16, 5), [4])
        self.slice_test(list(range(0, 20)), slice(-22, 5), [0, 1, 2, 3, 4])
        # self.slice_test(list(range(0,20)), slice(-LONG_NUMBER,5), [0,1, 2, 3, 4])

        self.slice_test(list(range(0, 20)), slice(15, 20), [15, 16, 17, 18, 19])
        self.slice_test(list(range(0, 20)), slice(15, 20), [15, 16, 17, 18, 19])
        self.slice_test(list(range(0, 20)), slice(-15, 7), [5, 6])
        self.slice_test(list(range(0, 20)), slice(18, 70), [18, 19])

        self.slice_test(list(range(0, 20)), slice(2, 70, 5), [2, 7, 12, 17])
        self.slice_test(list(range(0, 20)), slice(2, 70, -5), [])
        self.slice_test(list(range(0, 20)), slice(15, 6, -5), [15, 10])
        self.slice_test(list(range(0, 20)), slice(15, 6, 5), [])
        self.slice_test(list(range(0, 20)), slice(-15, 6, 5), [5])
        self.slice_test(list(range(0, 20)), slice(-15, 6, -5), [])
        self.slice_test(list(range(0, 20)), slice(-2, -21, -4), [18, 14, 10, 6, 2])

    def del_slice(self, l, s, expected):
        tmplist = list(l)
        del(tmplist[s])
        self.assertEqual(tmplist, expected, "del(list([{}:{}:{}])) expected: {}, get: {}".format(s.start, s.stop, s.step, expected, tmplist))
        tmplist = list(l)
        if (s.step == None):
            del(tmplist[s.start:s.stop])
        else:
            del(tmplist[s.start:s.stop:s.step])
        self.assertEqual(tmplist, expected, "del(list( slice({}, {}, {}))) expected: {}, get: {}".format(s.start, s.stop, s.step, expected, tmplist))

    def test_del_slice(self):
        self.del_slice(list(range(0, 20)), slice(1, 19), [0, 19])
        self.del_slice(list(range(0, 20)), slice(0, 19), [19])
        self.del_slice(list(range(0, 20)), slice(1, 20), [0])
        self.del_slice(list(range(0, 20)), slice(10, 100), [0, 1, 2, 3, 4, 5, 6, 7, 8, 9])
        self.del_slice(list(range(0, 20)), slice(-10, 100), [0, 1, 2, 3, 4, 5, 6, 7, 8, 9])
        self.del_slice(list(range(0, 20)), slice(-10, 5), [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19])
        self.del_slice(list(range(0, 20)), slice(-5, -1), [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 19])
        self.del_slice(list(range(0, 20)), slice(-30, -1), [19])

    def test_del_slice_step(self):
        self.del_slice(list(range(0, 20)), slice(0, 20, 2), [1, 3, 5, 7, 9, 11, 13, 15, 17, 19])
        self.del_slice(list(range(0, 20)), slice(0, 20, 5), [1, 2, 3, 4, 6, 7, 8, 9, 11, 12, 13, 14, 16, 17, 18, 19])
        self.del_slice(list(range(0, 20)), slice(-1, -55, 2), [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19])
        self.del_slice(list(range(0, 20)), slice(-1, -55, -2), [0, 2, 4, 6, 8, 10, 12, 14, 16, 18])
        self.del_slice(list(range(0, 20)), slice(-1, -55, -3), [0, 2, 3, 5, 6, 8, 9, 11, 12, 14, 15, 17, 18])
        self.del_slice(list(range(0, 20)), slice(-3, -55, -1), [18, 19])
        self.del_slice(list(range(0, 20)), slice(20, 2, -2), [0, 1, 2, 4, 6, 8, 10, 12, 14, 16, 18])
        self.del_slice(list(range(0, 20)), slice(20, 2, -3), [0, 1, 2, 3, 5, 6, 8, 9, 11, 12, 14, 15, 17, 18])
        self.del_slice(list(range(0, 20)), slice(20, 1, -3), [0, 1, 2, 3, 5, 6, 8, 9, 11, 12, 14, 15, 17, 18])
        self.del_slice(list(range(0, 20)), slice(20, 0, -3), [0, 2, 3, 5, 6, 8, 9, 11, 12, 14, 15, 17, 18])
        self.del_slice(list(range(0, 20)), slice(-3, 55, -1), [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19])
        self.del_slice(list(range(0, 20)), slice(-100, -5, 2), [1, 3, 5, 7, 9, 11, 13, 15, 16, 17, 18, 19])
        self.del_slice(list(range(0, 20)), slice(-15, -55, -1), [6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19])
        self.del_slice(list(range(0, 20)), slice(-4, -18, -3), [0, 1, 2, 3, 5, 6, 8, 9, 11, 12, 14, 15, 17, 18, 19])
        self.del_slice(list(range(0, 20)), slice(-3, -55, -4), [0, 2, 3, 4, 6, 7, 8, 10, 11, 12, 14, 15, 16, 18, 19])
        self.del_slice(list(range(0, 20)), slice(-1, -55, -4), [0, 1, 2, 4, 5, 6, 8, 9, 10, 12, 13, 14, 16, 17, 18])

    def test_ininicialization_with_slice(self):
        r = []
        l = [1, 2, 3, 4]
        r[:] = l
        self.assertEqual(l, r)

    def test_set_slice(self):
        a = [1, 2]
        a[1:2] = [7, 6, 5, 4]
        self.assertEqual([1, 7, 6, 5, 4], a)

        a = [1, 2, 3, 4]
        a[1:8] = [33]
        self.assertEqual([1, 33], a)

        a = [1, 2, 3, 4]
        a[1:8] = [33, 34, 35, 36, 37, 38]
        self.assertEqual([1, 33, 34, 35, 36, 37, 38], a)

        a = list(range(20))
        a[1:19] = [55, 55]
        self.assertEqual([0, 55, 55, 19], a)

        a = [1, 2, 3, 4]
        a[1:3] = [11]
        self.assertEqual([1, 11, 4], a)

        a = [1, 2, 3, 4]
        a[1:3] = [11, 12, 13, 14, 15, 16]
        self.assertEqual([1, 11, 12, 13, 14, 15, 16, 4], a)

        a = [1, 2]
        a[:] = (1, 2, 4, 5)
        self.assertEqual([1, 2, 4, 5], a)

        a = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10]
        a[0:10:2] = [10, 30, 50, 70, 90]
        self.assertEqual([10, 2, 30, 4, 50, 6, 70, 8, 90, 10], a)

        a = [1, 2, 3, 4, 5, 6]
        a[1:4:1] = [42, 42, 42]
        self.assertEqual([1, 42, 42, 42, 5, 6], a)

        a = [1, 2, 3, 4, 5, 6]
        a[5:8:1] = [42, 42, 42]
        self.assertEqual([1, 2, 3, 4, 5, 42, 42, 42], a)

        a = ["1", "2", "3", "4", "5", "6"]
        a[1:4:1] = ["42", "42", "42"]
        self.assertEqual(['1', '42', '42', '42', '5', '6'], a)

        a = ["1", "2", "3", "4", "5", "6"]
        a[5:8:1] = ["42", "42", "42"]
        self.assertEqual(["1", "2", "3", "4", "5", '42', '42', '42'], a)
        
        a = [1, 2, 3, 4]
        a[-9223372036854775809:9223372036854775808] = [9, 10, 11, 12]
        self.assertEqual([9, 10, 11, 12], a)
        
        try:
            a = [1, 2, 3, 4]
            a[-1000:1000:999999999999999999999999999999999999999999999999999999999999999999999999999] = [5,6,7,8]
        except ValueError: 
            self.assertEqual([1, 2, 3, 4], a)
        else:
            assert False, "expected ValueError"
            
        a = [1, 2, 3, 4]
        a[:] = map(next, [iter([None,]), iter([None,])])
        self.assertEqual([None, None], a)


    def test_set_slice_class_iter(self):

        class MyIter():

            def __init__(self, base):
                self.itera = iter(base)

            def __next__(self):
                return next(self.itera)

            def __iter__(self):
                return self

        a = list(range(10))
        a[::2] = MyIter(tuple(range(5)))
        self.assertEqual([0, 1, 1, 3, 2, 5, 3, 7, 4, 9], a)

    def test_set_slice_class_getitem(self):

        class MyIter2():

            def __init__(self, base):
                self.base = base

            def __getitem__(self, key):
                return self.base[key]

        a = [1, 2, 3, 4]
        a[2:] = MyIter2([33, 44, 55, 66])
        self.assertEqual([1, 2, 33, 44, 55, 66], a)

    def test_set_strange_slice(self):
        a = list(range(20))
        a[18:2] = [4, 3, 5]
        self.assertEqual([0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 4, 3, 5, 18, 19], a)
        a = list(range(20))
        a[18:0:-2] = [11, 22, 33, 44, 55, 66, 77, 88, 99]
        self.assertEqual([0, 1, 99, 3, 88, 5, 77, 7, 66, 9, 55, 11, 44, 13, 33, 15, 22, 17, 11, 19], a)
        a = list(range(20))
        a[18:-5] = [11, 22, 33, 44, 55, 66, 77, 88, 99]
        self.assertEqual([0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 11, 22, 33, 44, 55, 66, 77, 88, 99, 18, 19], a)
        a = list(range(20))
        a[-2:-20:-2] = [11, 22, 33, 44, 55, 66, 77, 88, 99]
        self.assertEqual([0, 1, 99, 3, 88, 5, 77, 7, 66, 9, 55, 11, 44, 13, 33, 15, 22, 17, 11, 19], a)
        a = list(range(20))
        a[20:-20] = [11, 22, 33, 44, 55, 66, 77, 88, 99]
        self.assertEqual([0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 11, 22, 33, 44, 55, 66, 77, 88, 99], a)

    def test_set_slice_generalize_storage(self):
        a = [1, 2]
        a[:] = 'ahoj'
        self.assertEqual(['a', 'h', 'o', 'j'], a)
        a = [1, 2]
        a[1:5] = [1.1, 2.2, 3.3]
        self.assertEqual([1, 1.1, 2.2, 3.3], a)

    def test_extend_spec(self):
        a = [1, 2]
        a.extend(a)
        self.assertEqual([1, 2, 1, 2], a)
        a = [923123123123]
        a.extend(a)
        self.assertEqual([923123123123, 923123123123], a)
        a = [1.1, 2.1]
        a.extend(a)
        self.assertEqual([1.1, 2.1, 1.1, 2.1], a)

        a = []
        a.extend(range(1, 4))
        self.assertEqual([1, 2, 3], a)

        a = []
        a.extend('ahoj')
        self.assertEqual(['a', 'h', 'o', 'j'], a)

    def test_extend(self):
        a = [1, 2, 3, 4, 5, 6]
        b = [1, 2, 3, 4, 5, 6]
        a.extend(b)
        self.assertEqual([1, 2, 3, 4, 5, 6, 1, 2, 3, 4, 5, 6], a)

        a = ["a", "b", "c"]
        b = ["a", "b", "c"]
        a.extend(b)
        self.assertEqual(["a", "b", "c", "a", "b", "c"], a)

        a = []
        a.extend([])
        self.assertEqual([], a)

    def test_extend_bytes(self):
        l = []
        l.extend(b"asdf")
        self.assertEqual([97, 115, 100, 102], l)

    def test_remove_spec(self):
        a = [1, 2]
        a.remove(2);
        self.assertEqual([1], a)
        a.remove(1)
        self.assertEqual([], a)

        a = [0, 1, 0, 1, 2]
        a.remove(True)
        self.assertEqual([0, 0, 1, 2], a)
        a.remove(False)
        self.assertEqual([0, 1, 2], a)

        a = list([LONG_NUMBER, LONG_NUMBER + 1])
        a.remove(LONG_NUMBER + 1)
        self.assertEqual([LONG_NUMBER], a)

        class MyInt(int):
            pass

        a = [1, 2, 3]
        a.remove(MyInt(2))
        self.assertEqual([1, 3], a)

        a = ["1", "2"]
        a.remove("2");
        self.assertEqual(["1"], a)

        a = [1.1, 2.2, 3.3]
        a.remove(2.2);
        self.assertEqual([1.1, 3.3], a)

    def test_insert_spec(self):
        a = [1, 2]
        self.assertRaises(TypeError, a.insert, [1, 2, 3], 1)

        class MyInt(int):
            pass

        a = [2, 4]
        a.insert(MyInt(1), 3)
        self.assertEqual([2, 3, 4], a)

        class MyIndex():

            def __index__(self):
                return 2

        a.insert(MyIndex(), 7)
        self.assertEqual([2, 3, 7, 4], a)

        class SecondIndex(int):

            def __index__(self):
                return self + 3;

        a = [0, 0, 0, 0, 0]
        a.insert(SecondIndex(1), 1)
        self.assertEqual([0, 1, 0, 0, 0, 0], a)

        a = [0]
        a.insert(LONG_NUMBER, 1)
        self.assertEqual([0, 1], a)

        a.insert(False, -1)
        self.assertEqual([-1, 0, 1], a)

    def test_StopIteration(self):
        l = [1.0]
        i = l.__iter__()
        i.__next__()
        self.assertRaises(StopIteration, i.__next__)
        l.append(2.0)
        self.assertRaises(StopIteration, i.__next__)
        l.append('a')
        self.assertRaises(StopIteration, i.__next__)

        l = []
        i = l.__iter__()
        self.assertRaises(StopIteration, i.__next__)
        l.append(2.0)
        self.assertRaises(StopIteration, i.__next__)

        l = ['a']
        i = l.__iter__()
        i.__next__()
        self.assertRaises(StopIteration, i.__next__)
        l.append('b')
        self.assertRaises(StopIteration, i.__next__)
        l.append(3)
        self.assertRaises(StopIteration, i.__next__)

    def test_add(self):
        l1 = [1, 2, 3]
        l2 = ["a", "b", "c"]
        self.assertEqual(l1 + l2, [1, 2, 3, "a", "b", "c"])

    def test_iadd_special(self):
        a = [1]
        a += (2, 3)
        self.assertEqual([1, 2, 3], a)

        a += {'a' : 1, 'b' : 2}
        # we need to compare sets since order is not guaranteed
        self.assertEqual({1, 2, 3, 'a', 'b'}, set(a))

        a = [1]
        a += range(2, 5)
        self.assertEqual([1, 2, 3, 4], a)
        self.assertRaises(TypeError, a.__iadd__, 1)

        class MyList(list):

            def __iadd__(self, value):
                return super().__iadd__([100])

        mya = MyList([1, 2])
        mya += [3]
        self.assertEqual([1, 2, 100], mya)

        a = [1, 2]
        a += a
        self.assertEqual([1, 2, 1, 2], a)

    def test_imul_len(self):
        a = [1]
        a *= 0
        self.assertEqual(0, len(a))

        a = [1]
        a *= 1
        self.assertEqual(1, len(a))

        a = [1]
        a *= -11
        self.assertEqual(0, len(a))

        a = [1]
        a *= 10
        self.assertEqual(10, len(a))

        a = [1, 2]
        a *= 4
        self.assertEqual(8, len(a))

    def test_imul_01(self):

        class My():

            def __init__(self, value):
                self.value = value

            def __index__(self):
                return self.value + 1;

        l = [1]
        ob = My(10)
        l *= ob
        self.assertEqual(11, len(l))

    def test_imul_02(self):

        class My():

            def __init__(self, value):
                self.value = value

            def __index__(self):
                return LONG_NUMBER * LONG_NUMBER

        l = [1]
        ob = My(10)
        self.assertRaises(OverflowError, l.__imul__, ob)

    def test_imul_03(self):

        class My():

            def __init__(self, value):
                self.value = value

            def __index__(self):
                return 'Ahoj'

        l = [1]
        ob = My(10)
        self.assertRaises(TypeError, l.__imul__, ob)

    def test_append(self):
        l = []
        l.append(1)
        l.append(0x1FF)
        l.append(0x1FFFFFFFF)
        l.append("hello")
        self.assertEqual(l, [1, 0x1FF, 0x1FFFFFFFF, "hello"])

        l = ["a", "b", "c"]
        l.append("d")
        self.assertEqual(l, ["a", "b", "c", "d"])

    def test_extend_bytes_2(self):
        b = bytes([3,4,255])
        l = [1,2]
        l.extend(b)
        self.assertEqual(l, [1,2,3,4,255])

    def test_extend_bytearray(self):
        b = bytearray([3,4,255])
        l = [1,2]
        l.extend(b)
        self.assertEqual(l, [1,2,3,4,255])

    def test_init_extend_with_lying_list(self):
        class LyingList(list):
            def __iter__(self):
                return iter([10, 20, 30, 40])

        l = LyingList([1,2,3,4])
        self.assertEqual([1,2,3,4], l)

        ll = list(l)
        self.assertEqual([10,20,30,40], ll)

        ll = list(LyingList([1.0, 5.6, 7.7]))
        self.assertEqual([10,20,30,40], ll)

        a = [1,0]
        a.extend(l)
        self.assertEqual([1,0,10,20,30,40], a)

        a = [1,0]
        a.extend(ll)
        self.assertEqual([1,0,10,20,30,40], a)

        ll.extend(ll)
        self.assertEqual([10,20,30,40,10,20,30,40], ll)

        l.extend(l)
        self.assertEqual([1,2,3,4,10,20,30,40], l)

class ListCompareTest(CompareTest):

    def test_compare(self):
        l1 = [1, 2, 3]
        l2 = [1, 2, 3, 0]
        l3 = [1, 2, 3, 4]

        self.comp_eq(l1, l1)

        self.comp_ne(l1, l2)
        self.comp_ne(l2, l3)

        self.comp_ge(l1, l1, True)
        self.comp_ge(l2, l1, False)
        self.comp_ge(l3, l2, False)
        self.comp_ge(l3, l1, False)

        self.comp_le(l1, l1, True)
        self.comp_le(l1, l2, False)
        self.comp_le(l2, l3, False)
        self.comp_le(l1, l3, False)

        self.comp_lt(l1, l2)
        self.comp_lt(l2, l3)
        self.comp_lt(l1, l3)

        self.comp_gt(l2, l1)
        self.comp_gt(l3, l2)
        self.comp_gt(l3, l1)

    def test_equal_other(self):

        def tryWithOtherType(left, right):
            self.assertFalse(left == right, "Operation {} == {} should be False".format(left, right))
            self.assertTrue(left != right, "Operation {} != {} should be True".format(left, right))

        l1 = [1, 2, 3]
        tryWithOtherType(l1, 1)
        tryWithOtherType(l1, 'hello')
        tryWithOtherType(l1, False)
        tryWithOtherType(l1, (1, 2, 3))
        tryWithOtherType(l1, {1, 2, 3})
        tryWithOtherType(l1, {'one':1, 'two':2, 'three':3})

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

        l1 = [1, 2, 3]
        tryWithOtherType(l1, 1)
        tryWithOtherType(l1, True)
        tryWithOtherType(l1, 'hello')

    def test_extendingClass(self):

        class MyList(list):

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

        l1 = MyList([1, 10])
        self.assertEqual(l1 == 1, 'eq')
        self.assertEqual(l1 != 'ne', 'ne')
        self.assertEqual(l1 > 'ne', 10)
        self.assertEqual(l1 < 1, 11.11)
        self.assertEqual(l1 >= 6, 11)
        self.assertEqual(l1 <= [1, 1], 'OK:False')
        self.assertEqual(l1 <= [1, 10], 'OK:True')
        self.assertEqual(l1 <= [1, 10, 0], 'OK:True')
