# Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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

# ankitv 10/10/13
# Iterating by Sequence Index


def assert_raises(err, fn, *args, **kwargs):
    raised = False
    try:
        fn(*args, **kwargs)
    except err:
        raised = True
    assert raised


class PassThru(Exception):
    pass


def check_pass_thru():
    raise PassThru
    yield 1


def test_set_or_union():
    s1 = {1, 2, 3}
    s2 = {4, 5, 6}
    s3 = {1, 2, 4}
    s4 = {1, 2, 3}

    or_result = s1 | s2
    union_result = s1.union(s2)
    assert or_result == {1, 2, 3, 4, 5, 6}
    assert union_result == {1, 2, 3, 4, 5, 6}

    or_result = s1 | s3
    union_result = s1.union(s3)
    assert or_result == {1, 2, 3, 4}
    assert union_result == {1, 2, 3, 4}

    or_result = s1 | s4
    union_result = s1.union(s4)
    assert or_result == {1, 2, 3}
    assert union_result == {1, 2, 3}

    assert frozenset((1,2)) | {1:2}.items() == {1, 2, (1, 2)}
    assert frozenset((1,2)) | {1:2}.keys() == {1, 2}

def test_set_and():
    assert frozenset((1,2)) & {1:2}.items() == set()
    assert frozenset((1,2)) & {1:2}.keys() == {1}

def test_set_union():
    assert {1, 2, 3}.union({1: 'a', 2: 'b', 4: 'd'}) == {1, 2, 3, 4}
    assert {1, 2, 3}.union([2, 3, 4, 5]) == {1, 2, 3, 4, 5}
    assert {1, 2, 3}.union((3, 4, 5, 6)) == {1, 2, 3, 4, 5, 6}


def test_set_remove():
    s = {1, 2, 3}
    assert s == {1, 2, 3}
    s.remove(3)
    assert s == {1, 2}


def test_set_le():
    assert set("a") <= set("abc")


def test_difference():
    word = 'simsalabim'
    otherword = 'madagascar'
    letters = 'abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ'
    s = set(word)
    d = dict.fromkeys(word)

    i = s.difference(otherword)
    for c in letters:
        assert (c in i) == (c in d and c not in otherword)

    assert s == set(word)
    assert type(i) == set
    assert_raises(PassThru, s.difference, check_pass_thru())
    assert_raises(TypeError, s.difference, [[]])

    for C in set, frozenset, dict.fromkeys, str, list, tuple:
        assert set('abcba').difference(C('cdc')) == set('ab')
        assert set('abcba').difference(C('efgfe')) == set('abc')
        assert set('abcba').difference(C('ccb')) == set('a')
        assert set('abcba').difference(C('ef')) == set('abc')
        assert set('abcba').difference() == set('abc')
        assert set('abcba').difference(C('a'), C('b')) == set('c')


def test_difference_update():
    word = 'simsalabim'
    otherword = 'madagascar'
    letters = 'abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ'
    s = set(word)
    d = dict.fromkeys(word)


    retval = s.difference_update(otherword)
    assert retval == None

    for c in (word + otherword):
        if c in word and c not in otherword:
            assert c in s
        else:
            assert c not in s

    assert_raises(PassThru, s.difference_update, check_pass_thru())
    assert_raises(TypeError, s.difference_update, [[]])
    # assert_raises(TypeError, s.symmetric_difference_update, [[]])

    for p, q in (('cdc', 'ab'), ('efgfe', 'abc'), ('ccb', 'a'), ('ef', 'abc')):
        for C in set, frozenset, dict.fromkeys, str, list, tuple:
            s = set('abcba')
            assert s.difference_update(C(p)) == None
            assert s == set(q)

            s = set('abcdefghih')
            s.difference_update()
            assert s == set('abcdefghih')

            s = set('abcdefghih')
            s.difference_update(C('aba'))
            assert s == set('cdefghih')

            s = set('abcdefghih')
            s.difference_update(C('cdc'), C('aba'))
            assert s == set('efghih')


def test_sub_and_super():
    for thetype in [set, frozenset]:
        p, q, r = map(thetype, ['ab', 'abcde', 'def'])
        assert p < q
        assert p <= q
        assert q <= q
        assert q > p
        assert q >= p
        assert not q < r
        assert not q <= r
        assert not q > r
        assert not q >= r
        assert set('a').issubset('abc')
        assert set('abc').issuperset('a')
        assert not set('a').issubset('cbs')
        assert not set('cbs').issuperset('a')


def test_superset_list():
    set = {1, 2, 3, 4}
    list = [1, 2, 3, 4]
    visited= False
    if set.issuperset(list):
        visited = True
    assert visited


def test_intersection():
    word = 'simsalabim'
    otherword = 'madagascar'
    letters = 'abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ'
    s = set(word)
    d = dict.fromkeys(word)

    i = s.intersection(otherword)
    for c in letters:
        assert (c in i) == (c in d and c in otherword)

    assert s == set(word)
    # assert type(i) == set
    assert_raises(PassThru, s.intersection, check_pass_thru())

    for C in set, frozenset, dict.fromkeys, str, list, tuple:
        assert set('abcba').intersection(C('cdc')) == set('cc')
        assert set('abcba').intersection(C('efgfe')) == set('')
        assert set('abcba').intersection(C('ccb')) == set('bc')
        assert set('abcba').intersection(C('ef')) == set('')
        assert set('abcba').intersection(C('cbcf'), C('bag')) == set('b')

    # TODO: currently the id function behaves a bit differently than the one in cPython
    # s = set('abcba')
    # z = s.intersection()
    # if set == frozenset():
    #     assert id(s) == id(z)
    # else:
    #     assert id(s) != id(z)


def test_same_id():
    empty_ids = set([id(frozenset()) for i in range(100)])
    assert len(empty_ids) == 1


def test_rich_compare():
    class TestRichSetCompare:
        def __gt__(self, some_set):
            self.gt_called = True
            return False
        def __lt__(self, some_set):
            self.lt_called = True
            return False
        def __ge__(self, some_set):
            self.ge_called = True
            return False
        def __le__(self, some_set):
            self.le_called = True
            return False

    # This first tries the builtin rich set comparison, which doesn't know
    # how to handle the custom object. Upon returning NotImplemented, the
    # corresponding comparison on the right object is invoked.
    myset = {1, 2, 3}

    myobj = TestRichSetCompare()
    myset < myobj
    assert myobj.gt_called

    myobj = TestRichSetCompare()
    myset > myobj
    assert myobj.lt_called

    myobj = TestRichSetCompare()
    myset <= myobj
    assert myobj.ge_called

    myobj = TestRichSetCompare()
    myset >= myobj
    assert myobj.le_called


def test_pop():
    s = {1, 2, 3}
    l = []
    l.append(s.pop())
    l.append(s.pop())
    l.append(s.pop())
    assert set(l) == {1, 2, 3}
    try:
        s.pop()
    except BaseException as e:
        assert type(e) == KeyError, "expected KeyError, got %s" % type(e)


def test_set_delete():
    s = {1, 2, 3}
    assert s == {1, 2, 3}
    s.discard(3)
    assert s == {1, 2}

    # string keys
    s = {'a', 'b', 'c'}
    assert s == {'a', 'b', 'c'}
    s.discard('c')
    assert s == {'a', 'b'}


def test_literal():
    d = {"a": 1, "b": 2, "c": 3}
    e = {"uff": "foo"}
    assert {*d, *e} == {"a", "b", "c", "uff"}

    d = {}
    assert {*d} == set()

