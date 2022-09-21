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

def test_list_iter():
    class Foo(list):
        def __init__(self):
            super().__init__()
            super().append(int(1))
            super().append(int(2))

        def __iter__(self):
            return iter([10, 20])

        def get_store(self):
            iter(super())

    def dosum(obj):
        return sum(obj)

    obj = Foo()
    assert len(obj) == 2 and obj[0] == 1 and obj[1] == 2
    values = []
    for x in obj:
        values.append(x)
    assert values == [10, 20], "Values are not as expected; was {!s}".format(values)
    result0 = sum(obj)
    result1 = dosum(obj)
    assert sum([1,2,3]) == 6
    assert dosum([1,2,3]) == 6
    assert result0 == 30, "Unexpected result for sum; was {!s}".format(result)
    assert result1 == 30, "Unexpected result for sum; was {!s}".format(result)
    i = iter([1,2,1.2,2,3])
    next(i)
    assert [next(i),next(i),next(i)] == [2,1.2,2]
    i = iter([1,2,1.2,2,3])
    next(i)
    assert (next(i),next(i),next(i)) == (2,1.2,2)


def test_dict_iter():
    expected_keys = ["a", "b", "c"]
    table = {"a": 1, "b": 2, "c": 3}
    keys = []
    for key in table:
        keys.append(key)

    for key in expected_keys:
        assert key in keys, "key {!s} is not contained in {!s}".format(key, keys)


def test_sentinel_iter0():
    sentinel = "end"
    l = ["1", "2", "3", sentinel]

    class IterObj(object):
        i = 0

        def __init__(self, data):
            self.data = data

        def member_callable(self):
            res = self.data[self.i]
            self.i = self.i + 1
            return res

    expected = l[:-1]
    it = iter(IterObj(l).member_callable, sentinel)
    res = list(it)
    assert expected == res, "unexpected result, was {}".format(res)


def test_sentinel_iter1():
    sentinel = "end"
    l = ["1", "2", "3", sentinel]

    class IterObj(object):
        i = 0

        def __init__(self, data):
            self.data = data

        def __call__(self):
            res = self.data[self.i]
            self.i = self.i + 1
            return res

    expected = l[:len(l)-1]
    it = iter(IterObj(l), sentinel)
    res = list(it)
    assert expected == res, "unexpected result, was {}".format(res)

def test_sentinel_bogus_iterable():
    class BogusIterObj:        
        __call__ = 1

    it = iter(BogusIterObj(), 42)
    try:
        for i in it: 
            pass
    except TypeError: 
        pass
    else: 
        assert False
    
def test_array_from_iterable():
    class CustomIterator(object):
        elem = 10

        def __iter__(self):
            return self

        def __next__(self):
            if self.elem < 50:
                res = self.elem
                self.elem = self.elem + 10
                return res
            raise StopIteration()

    class CustomIterable(object):
        def __iter__(self):
            return CustomIterator()

    obj = CustomIterable()
    # TODO enable once array module is fixed
    #assert array.array("i", obj) == array.array("i", [10, 20, 30, 40])


def test_iter_without_iter():
    class MyIterable:
        def __getitem__(self, k):
            if k >= 10:
                raise IndexError
            return k

    result = []
    for k in MyIterable():
        result.append(k)
    assert result == list(range(10)), "result should be {}, was {}".format(result, list(range(10)))


def test_set_from_iterable():
    assert len({a for b in bool.mro() for a in b.__dict__}) > 0
