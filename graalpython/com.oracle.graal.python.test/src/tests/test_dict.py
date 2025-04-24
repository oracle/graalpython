# Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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

import unittest, sys

def assert_raises(err, fn, *args, **kwargs):
    raised = False
    try:
        fn(*args, **kwargs)
    except err:
        raised = True
    assert raised


def test_equality():

    class EqualTo:
        def __init__(self, to):
            self.to = to

        def __eq__(self, other):
            return other == self.to

        def __hash__(self):
            return hash(self.to)

        def __repr__(self):
            return f"<equal to {self.to}>"

    dicts = [
        {'a': 'a'},
        {'a': 'b'},
        {'a': 'a', 'b': 'b'},
        {str(i): i for i in range(101)},
        {str(i): str(i) for i in range(101)},
        {1: 1},
        {1: 2},
        {1: 1, 2: 2},
    ]

    for d1 in dicts:
        for d2 in dicts:

            def check(a, b):
                if d1 is d2:
                    assert a == b, f"{a} should be equal to {b}"
                else:
                    assert a != b, f"{a} should not be equal to {b}"

            eq1 = {EqualTo(k): EqualTo(v) for k, v in d1.items()}
            eq2 = {EqualTo(k): EqualTo(v) for k, v in d2.items()}

            check(d1, d2)
            check(d1, eq2)
            check(eq1, d2)


def test_views():
    d = dict()
    d['a'] = 1
    d['b'] = 2
    d['c'] = 3

    assert len(d) == 3
    # assert d.keys() == {'a', 'b', 'c'}
    assert len(d.keys()) == 3, "keys view has invalid length"
    assert set(d.keys()) == {'a', 'b', 'c'}, "keys view invalid"
    assert len(d.values()) == 3, "values view has invalid length"
    assert set(d.values()) == {1, 2, 3}, "values view invalid"
    assert len(d.items()) == 3, "items view has invalid length"
    assert set(d.items()) == {('a', 1), ('b', 2), ('c', 3)}, "items view invalid"


def test_generator():
    lst = ['a', 'b', 'c']
    d = {k: 1 for k in lst}
    assert len(d) == 3
    # assert d.keys() == {'a', 'b', 'c'}
    assert set(d.values()) == {1, 1, 1}


def test_fromkeys():
    d = dict.fromkeys(['a', 'b', 'c'])
    assert len(d) == 3
    assert set(d.keys()) == {'a', 'b', 'c'}
    assert set(d.values()) == {None}

    d = dict.fromkeys(['a', 'b', 'c'], 1)
    assert len(d) == 3
    assert set(d.keys()) == {'a', 'b', 'c'}
    assert set(d.values()) == {1}

    d = dict.fromkeys(['a', 'b', 'c'], 1.0)
    assert len(d) == 3
    assert set(d.keys()) == {'a', 'b', 'c'}
    assert set(d.values()) == {1.0}

    d = dict.fromkeys(['a', 'b', 'c'], 'd')
    assert len(d) == 3
    assert set(d.keys()) == {'a', 'b', 'c'}
    assert set(d.values()) == {'d'}

    d = dict.fromkeys(['a', 'b', 'c'], None)
    assert len(d) == 3
    assert set(d.keys()) == {'a', 'b', 'c'}
    assert set(d.values()) == {None}

    o = object()
    d = dict.fromkeys(['a', 'b', 'c'], o)
    assert len(d) == 3
    assert set(d.keys()) == {'a', 'b', 'c'}
    assert set(d.values()) == {o}

    class preset(dict):
        def __init__(self):
            self['a'] = 1
    assert preset.fromkeys(['b']) == {'a':1, 'b':None}
    assert preset.fromkeys(['b'], 2) == {'a':1, 'b':2}

    class morethanoneinitargraiseserror(dict):
        def __init__(self, anotherArg):
            self.__init__()
    assert_raises(TypeError, morethanoneinitargraiseserror.fromkeys, [1])

    class nosetitem:
        pass

    class nosetitem2(dict):
        def __new__(cls):
            return nosetitem()
    assert_raises(TypeError, nosetitem2.fromkeys, [1])

    # Regression test for GitHub issue #232
    def foo(**kwargs):
        return dict.fromkeys(kwargs, 1)

    assert foo(a=5, b=6) == {'a': 1, 'b': 1}


def test_init():
    d = dict(a=1, b=2, c=3)
    assert len(d) == 3
    assert set(d.keys()) == {'a', 'b', 'c'}

    d = dict.fromkeys(['a', 'b', 'c'])
    assert len(d) == 3
    assert set(d.keys()) == {'a', 'b', 'c'}
    assert list(d.values()) == [None, None, None]

    d = dict.fromkeys(['a', 'b', 'c'], 1)
    assert len(d) == 3
    assert set(d.keys()) == {'a', 'b', 'c'}
    assert list(d.values()) == [1, 1, 1]

    assert_raises(TypeError, dict.fromkeys, 10)

    d = {'a':1, 'b':2}
    d.__init__()
    assert d == {'a':1, 'b':2}
    d.__init__({'c':3})
    assert d == {'a':1, 'b':2, 'c':3}
    d.__init__({'d':4})
    assert d == {'a':1, 'b':2, 'c':3, 'd':4}

def test_init1():
    try:
        dict([("a", 1), ("b", 2)], [("c", 3), ("d", 4)])
        assert False, "expected TypeError"
    except TypeError as e:
        import re
        assert re.match(r"dict expected at most 1 arguments?, got 2", str(e)), "invalid error message: %s" % e


def test_init2():
    try:
        dict([("a", 1), ("b", 2), ("c", 3), ("d", 4), 5])
        assert False, "expected TypeError"
    except TypeError as e:
        assert "cannot convert dictionary update sequence element #4 to a sequence" == str(
            e), "invalid error message: %s" % str(e)


def test_init3():
    try:
        dict([("a", 1), ("b", 2), ("c", 3), ("d", 4), [5]])
        assert False, "expected ValueError"
    except ValueError as e:
        assert "dictionary update sequence element #4 has length 1; 2 is required" == str(e), "invalid error message"

    try:
        dict("5")
        assert False, "expected ValueError"
    except ValueError as e:
        assert "dictionary update sequence element #0 has length 1; 2 is required" == str(e), "invalid error message"

    try:
        dict([("a", 1), ("b", 2), ("c", 3), ("d", 4), "5"])
        assert False, "expected ValueError"
    except ValueError as e:
        assert "dictionary update sequence element #4 has length 1; 2 is required" == str(e), "invalid error message"


def test_init4():
    pairs = []
    for i in range(0, 100000):
        pairs.append((str(i * 100), i * 100))
    d = dict(pairs)
    assert len(d) == 100000, "invalid length, expected 100.000 but was %d".format(len(d))


def test_init5():
    key_set = {'a', 'b', 'c', 'd'}

    class CustomMappingObject:
        def __init__(self, keys):
            self.__keys = keys

        def keys(self):
            return self.__keys

        def __getitem__(self, key):
            if key in self.__keys:
                return ord(key)
            raise KeyError(key)

        def __len__(self):
            return len(self.keys)

    d = dict(CustomMappingObject(key_set))
    assert len(d) == 4, "invalid length, expected 4 but was %d" % len(d)
    assert set(d.keys()) == key_set, "unexpected keys: %s" % str(d.keys())
    assert set(d.values()) == {97, 98, 99, 100}, "unexpected values: %s" % str(d.values())

def test_init6():
    try:
        dict(1)
        assert False, "expected TypeError"
    except TypeError as e:
        assert "'int' object is not iterable" == str(e), "invalid error message"

def test_init_kwargs():
    kwargs = {'ONE': 'one', 'TWO': 'two'}
    d = dict([(1, 11), (2, 22)], **kwargs)
    assert len(d) == 4, "invalid length, expected 4 but was %d" % len(d)
    assert set(d.keys()) == {1, 2, 'ONE', 'TWO'}, "unexpected keys: %s" % str(d.keys())
    assert set(d.values()) == {11, 22, 'one', 'two'}, "unexpected values: %s" % str(d.values())


def test_custom_key_object0():
    class CollisionKey:
        def __init__(self, val):
            self.val = val

        def __hash__(self):
            return 1234

        def __eq__(self, other):
            if type(other) == type(self):
                return self.val == other.val
            return False

    key0 = CollisionKey(0)
    key1 = CollisionKey(1)
    key1eq = CollisionKey(1)
    key2 = CollisionKey(2)
    d = {key0: "hello", key1: "world"}
    assert key0 in d, "key0 should be contained"
    assert key1 in d, "key1 should be contained"
    assert key1 is not key1eq, "key1 and key1eq are not the same object"
    assert key1eq in d, "key1eq should be contained"
    assert key2 not in d, "key2 should NOT be contained"


def test_custom_key_object1():
    class MyInt(int):
        def __hash__(self):
            return MyInt(int.__hash__(self))

    class LongInt(int):
        def __hash__(self):
            return 2 ** 32 + int.__hash__(self) - 2 ** 32

    d = {i: r for i, r in enumerate(range(100))}
    d[MyInt(10)] = "hello"
    d[LongInt(20)] = "world"
    assert MyInt(10) in d, "MyInt(10) should be contained"
    assert LongInt(20) in d, "LongInt(20) should be contained"
    assert d[MyInt(20)] == d[LongInt(20)], "MyInt(20) should be considered the same as LongInt(20)"
    assert d[LongInt(10)] == d[MyInt(10)], "MyInt(10) should be considered the same as LongInt(10)"


def test_mutable_key():
    def insert_unhashable(d, mutable_key):
        try:
            d[mutable_key] = "hello"
            assert False, "unhashable key must raise exception"
        except TypeError as e:
            assert "unhashable" in str(e), "invalid exception %s" % str(e)

    insert_unhashable(dict(), [1, 2, 3])
    insert_unhashable(dict(), {"a": 1, "b": 2})
    # this should work since tuples are imutable
    d = {}
    d[("a", "b")] = "hello"


def test_copy():
    d0 = dict(a=1, b=2)
    assert set(d0.keys()) == {'a', 'b'}

    d1 = d0.copy()
    assert set(d1.keys()) == {'a', 'b'}

    d1['c'] = 3
    assert set(d0.keys()) == {'a', 'b'}
    assert set(d1.keys()) == {'a', 'b', 'c'}


def test_keywords():
    def modifying(**kwargs):
        kwargs["a"] = 10
        kwargs["b"] = 20
        return kwargs

    def reading(**kwargs):
        assert kwargs["a"] == 1
        assert kwargs["b"] == 2
        res = modifying(**kwargs)
        assert kwargs["a"] == 1
        assert kwargs["b"] == 2
        return res

    res = reading(a=1, b=2)
    assert res["a"] == 10
    assert res["b"] == 20


def test_fixed_storage():
    class Foo:
        pass

    obj = Foo()
    d = obj.__dict__
    for i in range(200):
        attr_name = "method" + str(i)
        d[attr_name] = lambda: attr_name

    for i in range(200):
        attr_name = "method" + str(i)
        method_to_call = getattr(obj, attr_name)
        assert method_to_call() == attr_name


def test_get_default():
    d = {"a": 1}
    assert d.get("a") == 1
    assert d.get("a", 2) == 1
    assert d.get("b") is None
    assert d.get("b", 2) == 2


def test_in_dict_keys():
    d = {'a': 1, 'b': 2, 'c': 3}
    keys = d.keys()
    assert 'a' in keys


def test_create_seq_and_kw():
    d = dict({'a': 1, 'b': 2, 'c': 3}, d=4)
    for k in ['a', 'b', 'c', 'd']:
        assert k in d

    d = dict(dict(a=1, b=2, c=3), d=4)
    for k in ['a', 'b', 'c', 'd']:
        assert k in d


def test_dictview_set_operations_on_keys():
    k1 = {1: 1, 2: 2}.keys()
    k2 = {1: 1, 2: 2, 3: 3}.keys()
    k3 = {4: 4}.keys()

    assert k1 - k2 == set()
    assert k1 - k3 == {1, 2}
    assert k2 - k1 == {3}
    assert k3 - k1 == {4}
    assert k1 & k2 == {1, 2}
    assert k1 & k3 == set()
    assert k1 | k2 == {1, 2, 3}
    assert k1 ^ k2 == {3}
    assert k1 ^ k3 == {1, 2, 4}

def test_dictview_keys_operations():
    d1 = {'a': 1, 'b': 2}

    # &
    assert d1.keys() & 'b' == {'b'}
    assert d1.keys() & 'ab'  == {'a', 'b'}
    assert d1.keys() & ['a'] == {'a'}
    assert d1.keys() & ['a', 'b'] == {'a', 'b'}
    assert d1.keys() & [1, 2] == set()
    assert d1.keys() & ('a') == {'a'}
    assert d1.keys() & ('a', 'b') == {'a', 'b'}
    assert d1.keys() & {('a', 1)} == set()
    assert d1.keys() & d1 == {'a', 'b'}
    assert d1.keys() & d1.values() == set()
    assert d1.keys() & {1:'a', 2:'b'}.values() == {'a', 'b'}
    assert {1:1, 2:2}.keys() & range(1,3) == {1, 2}

    def chargen(c1, c2):
        for c in range(ord(c1), ord(c2)+1):
            yield chr(c)
    assert d1.keys() & chargen('a', 'b') == {'a', 'b'}

    assert_raises(TypeError, lambda: d1.keys() & 1)
    class TC:
        pass
    assert_raises(TypeError, lambda: d1.keys() & TC())

    # |
    assert d1.keys() | 'b' == {'a', 'b'}
    assert d1.keys() | 'bc' == {'a', 'c', 'b'}
    assert d1.keys() | ['a'] == {'a', 'b'}
    assert d1.keys() | ['a', 'b'] == {'a', 'b'}
    assert d1.keys() | ['c', 'b'] == {'a', 'c', 'b'}
    assert d1.keys() | [1, 2] == {'a', 1, 2, 'b'}
    assert d1.keys() | ('a') == {'a', 'b'}
    assert d1.keys() | ('a', 'b') == {'a', 'b'}
    assert d1.keys() | {('a', 1)} == {'a', ('a', 1), 'b'}
    assert d1.keys() | d1 == {'a', 'b'}
    assert d1.keys() | d1.values() == {'a', 1, 2, 'b'}
    assert d1.keys() | {1:'a', 2:'b'}.values() == {'a', 'b'}
    assert {1:1, 2:2}.keys() | range(1,3) == {1, 2}

    assert d1.keys() | chargen('a', 'c') == {'a', 'b', 'c'}

    assert_raises(TypeError, lambda: d1.keys() | 1)
    assert_raises(TypeError, lambda: d1.keys() | TC())

    # ^
    assert d1.keys() ^ 'a' == {'b'}
    assert d1.keys() ^ "ab" == set()
    assert d1.keys() ^ ['a'] == {'b'}
    assert d1.keys() ^ ['a', 'b'] == set()
    assert d1.keys() ^ [1, 2] == {'a', 1, 2, 'b'}
    assert d1.keys() ^ ('a') == {'b'}
    assert d1.keys() ^ ('a', 'b') == set()
    assert d1.keys() ^ {('a', 1)} == {'a', ('a', 1), 'b'}
    assert d1.keys() ^ d1 == set()
    assert d1.keys() ^ d1.values() == {'a', 1, 2, 'b'}
    assert d1.keys() ^ {1:'a', 2:'b'}.values() == set()
    assert {1:1, 2:2}.keys() ^ range(1,3) == set()

    assert d1.keys() ^ chargen('b', 'c') == {'a', 'c'}

    assert_raises(TypeError, lambda: d1.keys() ^ 1)
    assert_raises(TypeError, lambda: d1.keys() ^ TC())

    # -
    assert d1.keys() - 'a' == {'b'}
    assert d1.keys() - "ab" == set()
    assert d1.keys() - ['a'] == {'b'}
    assert d1.keys() - ['a', 'b'] == set()
    assert d1.keys() - [1, 2] == {'a', 'b'}
    assert d1.keys() - ('a') == {'b'}
    assert d1.keys() - ('a', 'b') == set()
    assert d1.keys() - {('a', 1)} == {'a', 'b'}
    assert d1.keys() - d1 == set()
    assert d1.keys() - d1.values() == {'a', 'b'}
    assert d1.keys() - {1:'a', 2:'b'}.values() == set()
    assert {1:1, 2:2}.keys() - range(1,3) == set()

    assert d1.keys() - chargen('b', 'c') == {'a'}

    assert_raises(TypeError, lambda: d1.keys() - 1)
    assert_raises(TypeError, lambda: d1.keys() - TC())

def test_dictview_items_operations():
    d1 = {'a': 1, 'b': 2}

    # &
    assert d1.items() & 'b' == set()
    assert d1.items() & "ab" == set()
    assert d1.items() & ['a'] == set()
    assert d1.items() & ['a', 'b'] == set()
    assert d1.items() & [1, 2] == set()
    assert d1.items() & ('a') == set()
    assert d1.items() & ('a', 'b') == set()
    assert d1.items() & {('a', 1)} == {('a', 1)}
    assert d1.items() & d1 == set()
    assert d1.items() & d1.values() == set()
    assert d1.items() & {1:'a', 2:'b'}.values() == set()
    assert d1.items() & tuple(d1.items()) == {('a', 1), ('b', 2)}
    assert d1.items() & tuple(('a', 1)) == set()
    assert {1:1, 2:2}.items() & range(1,3) == set()

    def tuplegen(i, c1, c2):
        for c in range(ord(c1), ord(c2)+1):
            i += 1
            yield (chr(c), i)

    assert d1.items() & tuplegen(0, 'a', 'b') == {('a', 1), ('b', 2)}

    assert_raises(TypeError, lambda: d1.items() & 1)
    class TC:
        pass
    assert_raises(TypeError, lambda: d1.items() & TC())

    # |
    assert d1.items() | 'b' == {('a', 1), ('b', 2), 'b'}
    assert d1.items() | "ab" == {('a', 1), ('b', 2), 'a', 'b'}
    assert d1.items() | ['a'] == {('a', 1), ('b', 2), 'a'}
    assert d1.items() | ['a', 'b'] == {('a', 1), ('b', 2), 'a', 'b'}
    assert d1.items() | [1, 2] == {('a', 1), ('b', 2), 1, 2}
    assert d1.items() | ('a') == {('a', 1), ('b', 2), 'a'}
    assert d1.items() | ('a', 'b') == {('a', 1), ('b', 2), 'a', 'b'}
    assert d1.items() | {('a', 1)} == {('a', 1), ('b', 2)}
    assert d1.items() | d1 == {('a', 1), ('b', 2), 'a', 'b'}
    assert d1.items() | d1.values() == {('a', 1), ('b', 2), 1, 2}
    assert d1.items() | {1:'a', 2:'b'}.values() == {('a', 1), ('b', 2), 'a', 'b'}
    assert d1.items() | tuple(d1.items()) == {('a', 1), ('b', 2)}
    assert d1.items() | tuple(('a', 1)) == {('a', 1), ('b', 2), 'a', 1}
    assert {1:1, 2:2}.items() | range(1,3) == {1, 2, (1, 1), (2, 2)}

    assert d1.items() | tuplegen(0, 'a', 'c') == {('a', 1), ('b', 2), ('c', 3)}

    assert_raises(TypeError, lambda: d1.items() | 1)
    assert_raises(TypeError, lambda: d1.items() | TC())

    # ^
    assert d1.items() ^ 'a' == {('a', 1), 'a', ('b', 2)}
    assert d1.items() ^ "ab" == {('a', 1), 'a', ('b', 2), 'b'}
    assert d1.items() ^ ['a'] == {('a', 1), 'a', ('b', 2)}
    assert d1.items() ^ ['a', 'b'] == {('a', 1), 'a', ('b', 2), 'b'}
    assert d1.items() ^ [1, 2] == {('a', 1), 1, 2, ('b', 2)}
    assert d1.items() ^ ('a') == {('a', 1), 'a', ('b', 2)}
    assert d1.items() ^ ('a', 'b') == {('a', 1), 'a', ('b', 2), 'b'}
    assert d1.items() ^ {('a', 1)} == {('b', 2)}
    assert d1.items() ^ d1 == {('a', 1), 'a', ('b', 2), 'b'}
    assert d1.items() ^ d1.values() == {('a', 1), 1, 2, ('b', 2)}
    assert d1.items() ^ {1:'a', 2:'b'}.values() == {('a', 1), 'a', ('b', 2), 'b'}
    assert {1:1, 2:2}.items() ^ range(1,3) == {1, 2, (1, 1), (2, 2)}

    assert d1.items() ^ tuplegen(1, 'b', 'c') == {('a', 1), ('c', 3)}

    assert_raises(TypeError, lambda: d1.items() ^ 1)
    assert_raises(TypeError, lambda: d1.items() ^ TC())

    # -
    assert d1.items() - 'a' == {('a', 1), ('b', 2)}
    assert d1.items() - "ab" == {('a', 1), ('b', 2)}
    assert d1.items() - ['a'] == {('a', 1), ('b', 2)}
    assert d1.items() - ['a', 'b'] == {('a', 1), ('b', 2)}
    assert d1.items() - [1, 2] == {('a', 1), ('b', 2)}
    assert d1.items() - ('a') == {('a', 1), ('b', 2)}
    assert d1.items() - ('a', 'b') == {('a', 1), ('b', 2)}
    assert d1.items() - {('a', 1)} == {('b', 2)}
    assert d1.items() - d1 == {('a', 1), ('b', 2)}
    assert d1.items() - d1.values() == {('a', 1), ('b', 2)}
    assert d1.items() - {1:'a', 2:'b'}.values() ==  {('a', 1), ('b', 2)}
    assert {1:1, 2:2}.items() - range(1,3) == {(1, 1), (2, 2)}

    assert d1.items() - tuplegen(1, 'b', 'c') == {('a', 1)}

    assert_raises(TypeError, lambda: d1.items() - 1)
    assert_raises(TypeError, lambda: d1.items() - TC())

def test_dictview_set_operations_on_items():
    k1 = {1: 1, 2: 2}.items()
    k2 = {1: 1, 2: 2, 3: 3}.items()
    k3 = {4: 4}.items()

    assert k1 - k2 == set()
    assert k1 - k3 == {(1, 1), (2, 2)}
    assert k2 - k1 == {(3, 3)}
    assert k3 - k1 == {(4, 4)}
    assert k1 & k2 == {(1, 1), (2, 2)}
    assert k1 & k3 == set()
    assert k1 | k2 == {(1, 1), (2, 2), (3, 3)}
    assert k1 ^ k2 == {(3, 3)}
    assert k1 ^ k3 == {(1, 1), (2, 2), (4, 4)}


def test_dictview_mixed_set_operations():
    # Just a few for .keys()
    assert {1: 1}.keys() == {1}
    assert {1} == {1: 1}.keys()
    assert {1: 1}.keys() | {2} == {1, 2}
    assert {2} | {1: 1}.keys() == {1, 2}
    # And a few for .items()
    assert {1: 1}.items() == {(1, 1)}
    assert {(1, 1)} == {1: 1}.items()
    assert {1: 1}.items() | {2} == {(1, 1), 2}
    assert {2} | {1: 1}.items() == {(1, 1), 2}


def test_setdefault():
    # dict.setdefault()
    d = {}
    assert d.setdefault('key0') is None
    d.setdefault('key0', [])
    assert d.setdefault('key0') is None
    d.setdefault('key', []).append(3)
    assert d['key'][0] == 3
    d.setdefault('key', []).append(4)
    assert len(d['key']) == 2
    assert_raises(TypeError, d.setdefault)

    class Exc(Exception):
        pass

    class BadHash(object):
        fail = False

        def __hash__(self):
            if self.fail:
                raise Exc()
            else:
                return 42

    x = BadHash()
    d[x] = 42
    x.fail = True
    assert_raises(Exc, d.setdefault, x, [])

    class SideEffectHash:
        def __init__(self):
            self.hash_called = 0
        def __hash__(self):
            self.hash_called += 1
            return 42

    d = {'hello': 'world'}
    key = SideEffectHash()
    assert d.setdefault(key, 'new') == 'new'
    assert key.hash_called == 1
    assert d.setdefault(key, 'another new') == 'new'
    assert key.hash_called == 2


def test_keys_contained():
    helper_keys_contained(lambda x: x.keys())
    helper_keys_contained(lambda x: x.items())


def helper_keys_contained(fn):
    # Test rich comparisons against dict key views, which should behave the
    # same as sets.
    empty = fn(dict())
    empty2 = fn(dict())
    smaller = fn({1: 1, 2: 2})
    larger = fn({1: 1, 2: 2, 3: 3})
    larger2 = fn({1: 1, 2: 2, 3: 3})
    larger3 = fn({4: 1, 2: 2, 3: 3})

    assert smaller < larger
    assert smaller <= larger
    assert larger > smaller
    assert larger >= smaller

    assert not smaller >= larger
    assert not smaller > larger
    assert not larger <= smaller
    assert not larger < smaller

    assert not smaller < larger3
    assert not smaller <= larger3
    assert not larger3 > smaller
    assert not larger3 >= smaller

    # Inequality strictness
    assert larger2 >= larger
    assert larger2 <= larger
    assert not larger2 > larger
    assert not larger2 < larger

    assert larger == larger2
    assert smaller != larger

    # There is an optimization on the zero-element case.
    assert empty == empty2
    assert not empty != empty2
    assert not empty == smaller
    assert empty != smaller

    # With the same size, an elementwise compare happens
    assert larger != larger3
    assert not larger == larger3


def test_object_set_item_single_instance_non_str_key():
    class Foo(object):
        pass

    f = Foo()
    f.__dict__[1] = 1
    f.a = 'a'
    assert f.__dict__ == {1: 1, 'a': 'a'}

    def bar():
        pass

    bar.__dict__[1] = 1
    bar.a = 'a'
    assert 1 in bar.__dict__
    assert 'a' in bar.__dict__


def test_unhashable_key():
    d = {}
    key_list = [10, 11]
    assert_raises(TypeError, lambda: d[key_list])
    key_tuple_list = (key_list, 2)
    assert_raises(TypeError, lambda: d[key_tuple_list])


class EncodedString(str):
    # unicode string subclass to keep track of the original encoding.
    # 'encoding' is None for unicode strings and the source encoding
    # otherwise
    encoding = None

    def __deepcopy__(self, memo):
        return self

    def byteencode(self):
        assert self.encoding is not None
        return self.encode(self.encoding)

    def utf8encode(self):
        assert self.encoding is None
        return self.encode("UTF-8")

    @property
    def is_unicode(self):
        return self.encoding is None

    def contains_surrogates(self):
        return string_contains_surrogates(self)

    def as_utf8_string(self):
        return bytes_literal(self.utf8encode(), 'utf8')


def test_wrapped_string_contains1():
    test_string = EncodedString('something')
    d = {'something': (1, 0), 'nothing': (2, 0)}
    reached = False
    if test_string in d:
        reached = True
    assert reached


def test_wrapped_string_contains2():
    test_string = EncodedString('something')
    dict = {'something', 'nothing'}
    reached = False
    if test_string in dict:
        reached = True
    assert reached


def test_wrapped_string_get():
    a = 'test'
    dict = locals()
    assert dict['a']

@unittest.skipIf(sys.implementation.name == 'cpython' and sys.version_info[0:2] < (3, 8), "skipping for cPython versions < 3.8")
def test_reverse_locals():
    a = 'atest'
    b = 'btest'
    r = list(reversed(locals()))
    assert r == ['b', 'a'], "expected ['b', 'a'] got " + str(r) + " instead "

def test_concat():
    r = {**{}}
    assert len(r) == 0

    r = {**{1: 2}}
    assert len(r) == 1
    assert set(r.keys()) == {1}
    assert set(r.values()) == {2}

    r = {**{}, 1: 2}
    assert len(r) == 1
    assert set(r.keys()) == {1}
    assert set(r.values()) == {2}

    r = {**{1: 2}, 3: 4, 6: 8}
    assert len(r) == 3
    assert set(r.keys()) == {1, 3, 6}
    assert set(r.values()) == {2, 4, 8}


def test_custom_ob_with_eq_and_hash():
    class MyClass(object):
        def __init__(self, x):
            self.x = x

        def __hash__(self):
            return id(self)

        def __eq__(self, other):
            return isinstance(other, MyClass) and self.x == other.x

    d = {}
    a = MyClass(10)
    d[a] = 20
    b = MyClass(10)

    assert a in d
    assert b not in d
    assert d.get(a, -1) == 20
    assert d.get(b, -1) == -1


def test_calling_hash_and_eq():
    count_hash = 0
    count_eq = 0

    class MyClass(object):
        def __init__(self, x):
            self.x = x

        def __hash__(self):
            nonlocal count_hash
            count_hash += 1
            return 12

        def __eq__(self, other):
            nonlocal count_eq
            count_eq += 1
            return isinstance(other, MyClass) and self.x == other.x

    d = {}
    a = MyClass(10)

    try:
        d[a] # we must not omit the call to __hash__, even when the dict is
             # empty
    except KeyError:
        assert count_hash == 1
    else:
        assert False

    d[a] = 20
    b = MyClass(10)

    assert a in d
    assert b in d
    assert count_hash == 4, count_hash
    assert count_eq == 1, count_eq


def test_hash_and_eq_for_dynamic_object_storage():
    class MyObject:
        def __init__(self, string):
            self.string = string

        def __eq__(self, other):
            return self.string == other

        def __hash__(self):
            return hash(self.string)

    d = {"1": 42}

    d2 = MyObject("1").__dict__
    d2["1"] = 42

    assert MyObject("1") in d
    assert d[MyObject("1")] == 42
    d[MyObject("1")] = 112
    assert d[MyObject("1")] == 112
    del d[MyObject("1")]
    assert "1" not in d

    assert MyObject("1") in d2
    assert d2[MyObject("1")] == 42
    d2[MyObject("1")] = 112
    assert d2[MyObject("1")] == 112
    del d2[MyObject("1")]
    assert "1" not in d2

def test_update_side_effect_on_other():
    class X:
        def __hash__(self):
            return 0
        def __eq__(self, o):
            other.clear()
            return False

    other = {'a':1, 'b': 2, X(): 3, 'c':4}
    d = {X(): 0, 1: 1}
    assert_raises(RuntimeError, d.update, other)
    assert 'c' not in d

    other = {'a':1, 'b': 2, X(): 3, 'c':4}
    d = {X(): 0, 1: 0}
    kw = {'kw': 1}

    raised = False
    try:
        d.update(other, **kw)
    except RuntimeError:
        raised = True
    assert raised

    assert 'kw' not in d


def test_update_side_effect_on_other_with_dom_storage():
    class MutatingKey:
        def __hash__(self):
            return hash('this_is_dom_storage')
        def __eq__(self, other):
            if hasattr(self, 'to_mutate'):
                self.to_mutate['eq'] = 'eq'
            return self is other

    class DomStorage:
        def __init__(self):
            self.this_is_dom_storage = 1

    key = MutatingKey()
    d = {key: 1, 'another': 2}
    d2 = DomStorage().__dict__
    key.to_mutate = d2
    assert_raises(RuntimeError, d.update, d2)

def test_iter_changed_size():
    def just_iterate(it):
        for i in it:
            pass

    def iterate_and_update(it):
        for i in it:
            d.update({3:3})

    # dict
    d = {1:1, 2:2}
    it = iter(d)
    del d[1]
    assert_raises(RuntimeError, just_iterate, it)

    d = {1:1, 2:2}
    assert_raises(RuntimeError, iterate_and_update, d)

    # keys
    d = {1:1, 2:2}
    it = iter(d.keys())
    del d[1]
    assert_raises(RuntimeError, just_iterate, it)

    d = {1:1}
    assert_raises(RuntimeError, iterate_and_update, d.keys())

    # values
    d = {1:1, 2:2}
    it = iter(d.values())
    del d[1]
    assert_raises(RuntimeError, just_iterate, it)

    d = {1:1}
    assert_raises(RuntimeError, iterate_and_update, d.values())

    # items
    d = {1:1, 2:2}
    it = iter(d.items())
    del d[1]
    assert_raises(RuntimeError, just_iterate, it)

    d = {1:1}
    assert_raises(RuntimeError, iterate_and_update, d.items())

def test_decorated_method_dict():
    def assert_bogus_dict_raises(dm):
        raised = False
        try:
            dm.__dict__ = 'a'
        except TypeError as e:
            raised = True
            assert "__dict__ must be set to a dictionary, not a 'str'" == str(e), "invalid error message"
        assert raised

    class A:
        def f():
            pass

    cm = classmethod(A.f)
    cm.x = 42
    assert cm.__dict__ == {
        '__module__': 'tests.test_dict',
        '__name__': 'f',
        '__qualname__': 'test_decorated_method_dict.<locals>.A.f',
        '__doc__': None,
        '__annotations__': {},
        'x': 42,
    }
    cm.__dict__ = {1:1}
    assert cm.__dict__ == {1:1}

    sm = staticmethod(A.f)
    sm.x = 42
    assert sm.__dict__ == {
        '__module__': 'tests.test_dict',
        '__name__': 'f',
        '__qualname__': 'test_decorated_method_dict.<locals>.A.f',
        '__doc__': None,
        '__annotations__': {},
        'x': 42,
    }
    sm.__dict__ = {1:1}
    assert sm.__dict__ == {1:1}

    assert_bogus_dict_raises(classmethod(A.f))
    assert_bogus_dict_raises(staticmethod(A.f))

    def f(): pass
    assert_bogus_dict_raises(classmethod(f))
    assert_bogus_dict_raises(staticmethod(f))

    class A:
        def f(self):
            def ff(): pass
            assert_bogus_dict_raises(classmethod(ff))
            assert_bogus_dict_raises(staticmethod(ff))
    A().f()

def test_update():
    x = {1: 0, 2: 1}
    y = {}
    y.update(x)
    assert y == x

def test_module_dict():
    import sys
    ModuleType = type(sys)

    foo = ModuleType.__new__(ModuleType)
    assert foo.__dict__ == {}

    foo = ModuleType.__new__(ModuleType)
    foo.f = 1
    assert foo.__dict__ == {"f" : 1}

    del foo.f
    assert foo.__dict__ == {}

    foo = ModuleType.__new__(ModuleType)
    foo.f = 1
    del foo.f
    assert foo.__dict__ == {}

def test_hashcode_str_subclass():
    class subclass(str):
        pass
    s = "\x96\0\x13\x1d\x18\x03"
    assert {42: 4, s: 1}[subclass(s)] == 1
    assert {42: 4, subclass(s): 1}[s] == 1

def test_append_in_eq_during_lookup():
    class Key:
        def __init__(self, d, hash):
            self.d = d
            self.done = False
            self.hash = hash
        def __hash__(self):
            return self.hash
        def __eq__(self, other):
            if not self.done:
                self.done = True
                d[self.hash] = 'expected value ' + str(self.hash)
            return other is self

    d = dict()
    for i in range(256):
        k = Key(d, i)
        d[k] = 'other value ' + str(i)
        # 1 should have the same hash as Key, the __eq__ should insert actual 1.
        # What may happen:
        # 1. the insertion does not cause rehashing, no side effect is detected and lookup
        # is not restarted, but it still finds the item, because now it is in a collision chain
        # 2. the insertion causes rehashing, the indices array is relocated, the side effect
        # is detected and we restart the lookup
        assert d[i] == 'expected value ' + str(i)

def test_delete_in_eq_during_lookup():
    class Key:
        def __init__(self, d):
            self.d = d
            self.done = False
        def __hash__(self):
            return 1
        def __eq__(self, other):
            if not self.done:
                self.done = True
                del d[self]
            return isinstance(other, Key)

    d = dict()
    # repeat few times to trigger re-hashing
    for i in range(256):
        d[Key(d)] = 'some value'
        # Here CPython detects the side effect and restarts the lookup
        assert d.get(Key(d), None) is None

def test_delete_in_eq_during_insert():
    class Key:
        def __init__(self, d):
            self.d = d
            self.done = False
        def __hash__(self):
            return 1
        def __eq__(self, other):
            if not self.done:
                self.done = True
                del d[self]
            return isinstance(other, Key)

    d = dict()
    # repeat few times to trigger compaction in delete
    for i in range(256):
        d[Key(d)] = 'some value'
        # Here CPython detects the side effects and restarts the insertion
        d[1] = 'other value'
        assert d == {1: 'other value'}
        del d[1]

def test_override_inserted_value_in_eq():
    class Key:
        def __init__(self, d):
            self.d = d
            self.done = False
        def __hash__(self):
            return 1
        def __eq__(self, other):
            if not self.done:
                self.done = True
                d[self] = 'override value'
            return isinstance(other, Key)

    d = dict()
    # repeat few times to trigger compaction and rehashing
    for i in range(256):
        d[Key(d)] = 'some value'
        # Here CPython detects the side effect and restarts the lookup
        val = d[Key(d)]
        assert val == 'override value', val
        del d[Key(d)]

def test_check_ref_identity_before_eq():
    eq_calls = 0
    class Key:
        def __hash__(self):
            return 1
        def __eq__(self, other):
            nonlocal eq_calls
            eq_calls += 1
            return self is other

    # check that our __eq__ works
    k = Key()
    assert k == k
    assert eq_calls == 1

    d = dict()
    d[k] = 'some value'
    assert d[k] == 'some value'
    assert eq_calls == 1


class TrackingKey:
    def __init__(self, id):
        self.clear_observations()
        self.id = id
    def __hash__(self):
        self.hash_calls += 1
        return hash(self.id)
    def __eq__(self, other):
        self.eq_calls += 1
        return self.id == getattr(other, 'id', other)
    def clear_observations(self):
        self.hash_calls = 0
        self.eq_calls = 0


def test_pop_side_effects():
    key = TrackingKey(1)
    d = {key: 42, 'other_key': 33}
    assert key.hash_calls == 1
    assert key.eq_calls == 0

    lookup_key = TrackingKey(1)
    assert d.pop(lookup_key) == 42
    assert lookup_key.hash_calls == 1
    assert lookup_key.eq_calls == 0


def test_eq_side_effects():
    key = TrackingKey('foo')
    d1 = {key: 42}
    key.clear_observations()
    d2 = {'foo': 42}  # This should use specialized storage strategy

    assert d1 == d2
    assert key.hash_calls == 0
    assert key.eq_calls == 1


# TODO: GR-40680
# def test_iteration_and_del():
#     def test_iter(get_iterable):
#         try:
#             d = {'a': 1, 'b': 2}
#             for k in get_iterable(d):
#                 d['b'] = 42
#         except RuntimeError as e:
#             return
#         assert False
#     test_iter(lambda d: d.keys())
#     test_iter(lambda d: d.values())
#     test_iter(lambda d: d.items())

def test_dict_values_eq():
    # Regression test: dict_values should not override __eq__
    d1 = {1: 1, 2: 2}
    d2 = {1: 1, 2: 2, 3: 3}
    assert d1.values() != d2.values()

    d1 = {1: 1, 2: 2, 4: 4}
    assert d1.values() != d1.values()

def test_removing_attr_from_economic_map():
    class Test:
        pass

    o = Test()
    o.foo = 1
    o.__dict__[42] = 10
    del o.foo

    assert "foo" not in o.__dict__
