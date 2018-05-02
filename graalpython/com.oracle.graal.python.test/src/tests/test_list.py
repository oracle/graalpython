# Copyright (c) 2018, Oracle and/or its affiliates.
#
# The Universal Permissive License (UPL), Version 1.0
#
# Subject to the condition set forth below, permission is hereby granted to any
# person obtaining a copy of this software, associated documentation and/or data
# (collectively the "Software"), free of charge and under any and all copyright
# rights in the Software, and any and all patent rights owned or freely
# licensable by each licensor hereunder covering either (i) the unmodified
# Software as contributed to or provided by such licensor, or (ii) the Larger
# Works (as defined below), to deal in both
#
# (a) the Software, and
# (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
#     one is included with the Software (each a "Larger Work" to which the
#     Software is contributed by such licensors),
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

import seq_tests

LONG_NUMBER = 6227020800;

import list_tests


class ListTest(list_tests.CommonTest):
    type2test = list
    
    # ======== Specific test for Graal Python ======

    def test_getitem(self):
        l = [1, 2, 3]
        self.assertEqual(1, l[False])

    def pop_all_list(self, list):
        size = len(list)
        self.assertRaises(IndexError, list.pop, size)
        self.assertRaises(IndexError, list.pop, 0 - size -1)
        for i in range (size - 1, -1, -1):
            self.assertEqual(list[i], list.pop())
        self.assertRaises(IndexError, list.pop)

    def test_pop_int(self):
        l = [1, 2, 3, 4]
        self.pop_all_list(l)

        l = list([1, 2, 3, 4])
        self.pop_all_list(l)

    def test_pop_long(self):
        l = [LONG_NUMBER + 1, LONG_NUMBER +2, LONG_NUMBER +3, LONG_NUMBER +4]
        self.pop_all_list(l)

        l = list([LONG_NUMBER +1, LONG_NUMBER +2, LONG_NUMBER +3, LONG_NUMBER +4])
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
        self.slice_test(list(range(0,20)), slice(1,5), [1, 2, 3, 4])
        self.slice_test(list(range(0,20)), slice(0,5), [0,1, 2, 3, 4])
        self.slice_test(list(range(0,20)), slice(-1,5), [])
        self.slice_test(list(range(0,20)), slice(-15,5), [])
        self.slice_test(list(range(0,20)), slice(-16,5), [4])
        self.slice_test(list(range(0,20)), slice(-22,5), [0,1, 2, 3, 4])
        #self.slice_test(list(range(0,20)), slice(-LONG_NUMBER,5), [0,1, 2, 3, 4])

        self.slice_test(list(range(0,20)), slice(15,20), [15, 16, 17, 18, 19])
        self.slice_test(list(range(0,20)), slice(15,20), [15, 16, 17, 18, 19])
        self.slice_test(list(range(0,20)), slice(-15,7), [5, 6])
        self.slice_test(list(range(0,20)), slice(18,70), [18, 19])

        self.slice_test(list(range(0,20)), slice(2,70,5), [2, 7, 12, 17])
        self.slice_test(list(range(0,20)), slice(2,70,-5), [])
        self.slice_test(list(range(0,20)), slice(15,6,-5), [15, 10])
        self.slice_test(list(range(0,20)), slice(15,6,5), [])
        self.slice_test(list(range(0,20)), slice(-15,6,5), [5])
        self.slice_test(list(range(0,20)), slice(-15,6,-5), [])
        self.slice_test(list(range(0,20)), slice(-2, -21, -4), [18, 14, 10, 6, 2])

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
        l = [1,2,3,4]
        r[:] = l
        self.assertEqual(l, r)
    
    def test_set_slice(self):
        a = [1,2]
        a[1:2] = [7,6,5,4]
        self.assertEqual([1, 7, 6, 5, 4], a)
        a = [1, 2, 3, 4]
        a[1:8] = [33]
        self.assertEqual([1, 33], a)
        a = [1,2,3,4]
        a[1:8] = [33,34,35,36,37,38]
        self.assertEqual([1, 33,34,35,36,37,38], a)
        a = list(range(20))
        a[1:19] = [55, 55]
        self.assertEqual([0,55,55,19],a)
        a = [1,2,3,4]
        a[1:3] =[11] 
        self.assertEqual([1, 11, 4], a)
        a = [1,2,3,4]
        a[1:3] =[11,12,13,14,15,16] 
        self.assertEqual([1, 11,12,13,14,15,16, 4], a)
        a = [1,2]
        a[:] = (1, 2, 4, 5)
        self.assertEqual([1,2,4,5], a)

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

        a = [1,2,3,4]
        a[2:] = MyIter2([33,44,55,66])
        self.assertEqual([1,2,33,44,55,66], a)

    def test_set_strange_slice(self):
        a = list(range(20))
        a[18:2] = [4,3,5]
        self.assertEqual([0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 4, 3, 5, 18, 19], a)
        a = list(range(20))
        a[18:0:-2] = [11,22,33,44,55,66,77,88,99]
        self.assertEqual([0, 1, 99, 3, 88, 5, 77, 7, 66, 9, 55, 11, 44, 13, 33, 15, 22, 17, 11, 19], a)
        a = list(range(20))
        a[18:-5] = [11,22,33,44,55,66,77,88,99]
        self.assertEqual([0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 11, 22, 33, 44, 55, 66, 77, 88, 99, 18, 19], a)
        a = list(range(20))
        a[-2:-20:-2] = [11,22,33,44,55,66,77,88,99]
        self.assertEqual([0, 1, 99, 3, 88, 5, 77, 7, 66, 9, 55, 11, 44, 13, 33, 15, 22, 17, 11, 19], a)
        a = list(range(20))
        a[20:-20] = [11,22,33,44,55,66,77,88,99]
        self.assertEqual([0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 11, 22, 33, 44, 55, 66, 77, 88, 99], a)

    def test_set_slice_generalize_storage(self):
        a = [1,2]
        a[:] = 'ahoj'
        self.assertEqual(['a', 'h', 'o', 'j'], a)
        a = [1,2]
        a[1:5] = [1.1, 2.2, 3.3]
        self.assertEqual([1,1.1, 2.2, 3.3], a)
