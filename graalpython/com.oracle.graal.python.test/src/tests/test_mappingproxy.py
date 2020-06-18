# Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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