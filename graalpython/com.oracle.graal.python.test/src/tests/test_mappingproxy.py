# Copyright (c) 2018, 2026, Oracle and/or its affiliates. All rights reserved.
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

# the dict representation of a type is a mappingproxy
import array, collections

_mappingproxy = type(type.__dict__)


def assert_raises(err, fn, *args, **kwargs):
    raised = False
    try:
        fn(*args, **kwargs)
    except err:
        raised = True
    assert raised


def test_type():
    assert "__name__" in type.__dict__
    assert _mappingproxy.__name__ == "mappingproxy"


def test_immutable():
    def fn_set(mp):
        mp["a"] = "hello"
    assert_raises(TypeError, fn_set, _mappingproxy({"b": 2}))

    def fn_del(mp):
        del mp["a"]
    assert_raises(TypeError, fn_del, _mappingproxy({"a": 1}))


def test_views():
    d = {"a": 1, "b": 2, "c": 3}
    mp = _mappingproxy(d)

    assert len(mp) == 3
    assert d.keys() == {'a', 'b', 'c'}
    assert mp.keys() == d.keys()
    assert len(mp.keys()) == 3, "keys view has invalid length"
    assert set(mp.keys()) == {'a', 'b', 'c'}, "keys view invalid"
    assert len(mp.values()) == 3, "values view has invalid length"
    assert set(mp.values()) == {1, 2, 3}, "values view invalid"
    assert len(mp.items()) == 3, "items view has invalid length"
    assert set(mp.items()) == {('a', 1), ('b', 2), ('c', 3)}, "items view invalid"


def test_values_descriptor_rejects_non_mappingproxy():
    class NotADict:
        values = type(object.__dict__).values

    assert_raises(TypeError, type(object.__dict__).values.__get__, NotADict(), NotADict)
    assert_raises(TypeError, lambda: NotADict().values)


def test_init():
    class CustomMappingObject:
        def __init__(self, keys, values):
            self._keys = keys
            self._values = values

        def __getitem__(self, k):
            for i in range(len(self._keys)):
                if k == self._keys[i]:
                    return self._values[i]
            raise KeyError

        def __setitem__(self, k, v):
            for i in range(len(self._keys)):
                if k == self._keys[i]:
                    self._values[i] = v
                    return v
            raise KeyError

        def keys(self):
            return set(self._keys)

        def values(self):
            return self._values

        def items(self):
            return {(self._keys[i], self._values[i]) for i in range(len(self._keys))}

        def __len__(self):
            return len(self._keys)

    mp_list = _mappingproxy(CustomMappingObject(["a", "b", "c"], [1, 2, 3]))
    assert mp_list.keys() == {"a", "b", "c"}


def test_init_invalid():
    def mp_init(*args):
        return _mappingproxy(*args)
    assert_raises(TypeError, mp_init)
    assert_raises(TypeError, mp_init, None)


def test_iter():
    d = {"a": 1, "b": 2, "c": 3}
    mp = _mappingproxy(d)

    mp_keys = set([k for k in mp])
    assert d.keys() == mp_keys


def test_set_update_mappingproxy_storage_and_live_changes():
    class Owner:
        pass

    obj = Owner()
    for i in range(40):
        setattr(obj, f'key{i}', i)
    for d in ({}, {'a': 1, 2: 3, (4, 5): 6}, obj.__dict__):
        proxy = _mappingproxy(d)
        result = {'existing'}
        result.update(proxy)
        assert result == {'existing'} | set(d)
        d['added'] = 42
        result = set()
        result.update(proxy)
        assert result == set(d)
        del d['added']
        result = set()
        result.update(proxy)
        assert result == set(d)


def test_set_update_mappingproxy_custom_iteration():
    calls = []

    class CustomDict(dict):
        def __iter__(self):
            calls.append('dict iter')
            return iter(('projected',))

    class CustomMapping:
        def __getitem__(self, key):
            raise AssertionError('values must not be fetched')

        def __iter__(self):
            calls.append('mapping iter')
            return iter(('custom',))

    for mapping, expected, call in (({'plain': 1}, {'plain'}, None),
                                    (CustomDict(hidden=1), {'projected'}, 'dict iter'),
                                    (CustomMapping(), {'custom'}, 'mapping iter'),
                                    ({'plain_again': 1}, {'plain_again'}, None)):
        for proxy in (_mappingproxy(mapping), _mappingproxy(_mappingproxy(mapping))):
            calls.clear()
            result = set()
            result.update(proxy)
            assert result == expected
            assert calls == ([] if call is None else [call])


def test_set_update_mappingproxy_hash_side_effects():
    calls = []
    fail = False

    class Key:
        def __hash__(self):
            calls.append('hash')
            if fail:
                raise ValueError('hash failed')
            return 42

    key = Key()
    proxy = _mappingproxy({key: 'value'})
    calls.clear()
    result = set()
    result.update(proxy)
    assert calls == ['hash']
    assert next(iter(result)) is key
    fail = True
    assert_raises(ValueError, set().update, proxy)


def test_set_update_mappingproxy_detects_mutation():
    for mutation in ('add', 'delete', 'value'):
        active = False

        class Key:
            def __hash__(self):
                if active:
                    if mutation == 'add':
                        d['extra'] = 1
                    elif mutation == 'delete':
                        del d['last']
                    else:
                        d['last'] = 'changed'
                return 42

        key = Key()
        d = {key: 1, 'middle': 2, 'last': 3}
        active = True
        if mutation == 'value':
            result = set()
            result.update(_mappingproxy(d))
            assert len(result) == 3
            assert d['last'] == 'changed'
        else:
            assert_raises(RuntimeError, set().update, _mappingproxy(d))

    d = {'trigger': 1, 'last': 2}

    class CollidingKey:
        def __hash__(self):
            return hash('trigger')

        def __eq__(self, other):
            d['extra'] = 3
            return False

    result = {CollidingKey()}
    assert_raises(RuntimeError, result.update, _mappingproxy(d))


def test_dir_mappingproxy_inheritance_and_tombstones():
    class Base:
        base = 1

    class Left(Base):
        left = 2

    class Right(Base):
        right = 3

    class Derived(Left, Right):
        own = 4

    obj = Derived()
    obj.instance = 5
    for i in range(10):
        Derived.temporary = i
        del Derived.temporary
        if i % 2:
            Derived.temporary = i
        expected = set(obj.__dict__)
        for cls in Derived.__mro__:
            expected.update(cls.__dict__)
        assert dir(obj) == sorted(expected)


def test_dir_mappingproxy_custom_metaclass_and_dir():
    calls = []

    class Meta(type):
        def __getattribute__(cls, name):
            if name == '__dict__':
                calls.append('dict')
                return _mappingproxy({'projected': 1})
            return super().__getattribute__(name)

    class Owner(metaclass=Meta):
        hidden = 1

    names = dir(Owner)
    assert 'projected' in names
    assert 'hidden' not in names
    assert calls == ['dict']

    class CustomDir:
        def __dir__(self):
            return ['z', 'a']

    assert dir(CustomDir()) == ['a', 'z']


def test_create():
    _mappingproxy(dict())
    mp = _mappingproxy({'a': 1})
    _mappingproxy(mp)
    _mappingproxy('abc')
    _mappingproxy(b'abc')
    _mappingproxy(bytearray(b'abc'))
    assert_raises(TypeError, _mappingproxy, ())
    assert_raises(TypeError,_mappingproxy, (1,2,3))
    assert_raises(TypeError,_mappingproxy, [])
    assert_raises(TypeError,_mappingproxy, [1,2,3])
    _mappingproxy(memoryview(b'abc'))
    _mappingproxy(array.array("I", [1,2,3]))
    assert_raises(TypeError, _mappingproxy, collections.deque([1,2,3]))
    assert_raises(TypeError, _mappingproxy, set())
    assert_raises(TypeError, _mappingproxy, {1,2,3})
    assert_raises(TypeError, _mappingproxy, None)
    assert_raises(TypeError, _mappingproxy, 123)

def test_iter_changed_size():
    class A:
        pass

    def foo():
        pass

    try:
        for i in A.__dict__:
            setattr(A, 'foo', foo)
    except RuntimeError:
        raised = True
    assert raised
