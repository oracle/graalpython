# Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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


class MyCustomString(str):
    def __hash__(self):
        return super(MyCustomString, self).__hash__() | 2
    def __eq__(self, other):
        return super(MyCustomString, self).__eq__(other)


try:
    set_strategy = __graalpython__.set_storage_strategy
    FACTORIES = [
        lambda: set_strategy(dict(), 'empty'),
        lambda: set_strategy(dict(), 'dynamicobject'),
        lambda: set_strategy(dict(), 'economicmap'),
    ]
except NameError:
    # For CPython, just to verify the test results
    FACTORIES = [lambda: dict()]


ALL_KEYS = [1, 1.5, 'foo', MyCustomString()]
ALL_VALUES = list(range(len(ALL_KEYS)))


# Several interesting combinations of keys to trigger the transitions at different points
KEYS = [
    ALL_KEYS,
    ['foo', MyCustomString(), 1],
    [MyCustomString(), 'foo'],
    ['foo1', 'foo2'],
]
VALUES = [list(range(len(k))) for k in KEYS]
KEYS_VALUES = [list(zip(k, v)) for (k, v) in zip(KEYS, VALUES)]


def assert_raises_keyerror(d, key):
    raised = False
    try:
        d[key]
    except KeyError:
        raised = True
    assert raised


def test_add_contains_one_key():
    keys_values = list(zip(ALL_KEYS, ALL_VALUES))
    combinations = [(f, kv) for f in FACTORIES for kv in keys_values]
    for (f, (k, v)) in combinations:
        d = f()
        d[k] = v
        assert d[k] == v
        for (k2, v2) in keys_values + [(k, v)]:
            # the key we just inserted is set and all other keys are not set
            assert (k2 == k) == (k2 in d)
        assert d[k] == v


def test_add_contains_copy():
    for i in range(len(KEYS)):
        keys = KEYS[i]
        keys_values = KEYS_VALUES[i]
        for f in FACTORIES:
            d = f()

            # check that it's really empty
            assert len(d) == 0
            for k in keys:
                assert k not in d
                assert_raises_keyerror(d, k)

            # inset the items one by one, check that
            expected_len = 0
            for (k, v) in keys_values:
                d[k] = v
                expected_len += 1
                assert k in d
                assert d[k] == v
                assert 2 not in d
                assert_raises_keyerror(d, 2)
                assert_raises_keyerror(d, 'bar')
                assert len(d) == expected_len

            # insertion order is preserved
            assert keys == [k for k in d]
            assert keys == list(d.keys())
            assert keys_values == list(d.items())

            cpy = d.copy()
            cpy[42] = 'answer'
            assert_raises_keyerror(d, 42)
            assert cpy[42] == 'answer'

            d.clear()
            assert len(d) == 0
            assert len(cpy) > 0
            for (k, v) in keys_values:
                assert cpy[k] == v
                assert_raises_keyerror(d, k)


def test_pop():
    for keys_values in KEYS_VALUES:
        for f in FACTORIES:
            d = f()
            for (k, v) in keys_values:
                d[k] = v
            assert d.pop('bar', 'default') == 'default'
            expected_len = len(d)
            for (k, v) in keys_values:
                assert d.pop(k) == v
                expected_len -= 1
                assert expected_len == len(d)


def test_popitem():
    for keys_values in KEYS_VALUES:
        for f in FACTORIES:
            d = f()
            for (k, v) in keys_values:
                d[k] = v

            expected_len = len(d)
            reversed_key_values = keys_values
            reversed_key_values.reverse()
            for (k, v) in reversed_key_values:
                (actual_k, actual_v) = d.popitem()
                assert actual_k == k
                assert actual_v == v
                expected_len -= 1
                assert expected_len == len(d)


def test_delete():
    for keys_values in KEYS_VALUES:
        for f in FACTORIES:
            for (k, v) in keys_values:
                d = f()
                for (k2, v2) in keys_values:
                    d[k2] = v2
                del d[k]
                assert_raises_keyerror(d, k)
                for (k2, v2) in keys_values:
                    if k2 != k:
                        assert d[k2] == v2


def test_update():
    # take all possible combinations of two dict storages, setting one key and value to each,
    # then updating one with the other and then checking the result
    factories2 = [(f1, f2) for f1 in FACTORIES for f2 in FACTORIES]
    all_keys_vals = list(zip(ALL_KEYS, ALL_VALUES))
    all_keys_values2 = [(a, b) for a in all_keys_vals for b in all_keys_vals]
    combinations = [(f, k) for f in factories2 for k in all_keys_values2]
    for ((f1, f2), ((k1, v1), (k2, v2))) in combinations:
        d1 = f1()
        d2 = f2()
        d1[k1] = v1
        d2[k2] = v2
        d1.update(d2)
        assert k1 in d1
        assert k2 in d2
        if k1 == k2:
            assert len(d1) == 1
            assert d1[k1] == v2
        else:
            assert len(d1) == 2
            assert d1[k1] == v1
            assert d1[k2] == v2


log = []
class LoggingStr(str):
    def __hash__(self):
        log.append('Hash on %r' % self)
        return 1
    def __eq__(self, other):
        log.append('Eq on %r and %r' % (self, other))
        return super(LoggingStr, self).__eq__(other)

def test_side_effects():
    for (f, key) in zip(FACTORIES, ['a', 'b', 'c', 'd', 'e', 'f', 'g']):
        log.clear()
        d = f()
        d[LoggingStr(key)] = 42
        assert log == ["Hash on '%s'" % key]
        log.clear()
        assert d[LoggingStr(key)] == 42
        assert log == [
            "Hash on '%s'" % key,
            "Eq on '%s' and '%s'" % (key, key)]
        log.clear()
        assert_raises_keyerror(d, LoggingStr('foo'))
        assert log == [
            "Hash on 'foo'",
            "Eq on '%s' and 'foo'" % key]
