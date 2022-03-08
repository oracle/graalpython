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
        lambda: set_strategy(set(), 'empty'),
        lambda: set_strategy(set(), 'hashmap'),
        lambda: set_strategy(set(), 'dynamicobject'),
        lambda: set_strategy(set(), 'economicmap'),
    ]
except NameError:
    # For CPython, just to verify the test results
    FACTORIES = [lambda: set()]


KEYS = [1, 1.5, 'foo', MyCustomString()]
COMBINATIONS = [(f, k) for f in FACTORIES for k in KEYS]


def test_add_contains_one_key():
    for (f, key1) in COMBINATIONS:
        s = f()
        s.add(key1)
        for key2 in KEYS + [key1]:
            assert (key2 == key1) == (key2 in s)


def test_add_contains_iterate_all_keys():
    for f in FACTORIES:
        s = f()
        assert len(s) == 0
        for k in KEYS:
            assert k not in s
        expected_len = 0
        for k in KEYS:
            s.add(k)
            expected_len += 1
            assert k in s
            assert 2 not in s
            assert 'bar' not in s
            assert len(s) == expected_len
        assert set(KEYS) == s


FACTORIES2 = [(f1, f2) for f1 in FACTORIES for f2 in FACTORIES]
KEYS2 = [(k1, k2) for k1 in KEYS for k2 in KEYS]
COMBINATIONS2 = [(f, k) for f in FACTORIES2 for k in KEYS2]


def test_or():
    for ((f1, f2), (k1, k2)) in COMBINATIONS2:
        s1 = f1()
        s2 = f2()
        s1.add(k1)
        s2.add(k2)
        res = s1 | s2
        assert k1 in res
        assert k2 in res
        assert 2 not in res
        assert 'bar' not in res
        assert k1 == 1 or k2 == 1 or 1 not in res


def test_xor():
    for ((f1, f2), (k1, k2)) in COMBINATIONS2:
        s1 = f1()
        s2 = f2()
        s1.add(k1)
        s2.add(k2)
        res = s1 ^ s2
        if k1 == k2:
            assert len(res) == 0
        else:
            assert k1 in res
            assert k2 in res
        assert 2 not in res
        assert 'bar' not in res
        assert k1 == 1 or k2 == 1 or 1 not in res


def test_and():
    for ((f1, f2), (k1, k2)) in COMBINATIONS2:
        s1 = f1()
        s2 = f2()
        s1.add(k1)
        s2.add(k2)
        res = s1 & s2
        assert 2 not in res
        assert 'bar' not in res
        if k1 == k2:
            assert k1 in res
            assert len(res) == 1
        else:
            assert k1 not in res
            assert k2 not in res
            assert len(res) == 0


def test_find_custom_key():
    class MyWeirdKey(str):
        def __init__(self):
            self.log = []
        def __eq__(self, other):
            self.log.append('called __eq__ with %r' % other)
            return True
        def __hash__(self):
            return 'a'.__hash__()
    for f in FACTORIES:
        # Set with any value that has the same hash contains the weird key
        s = f()
        s.add('b')
        s.add('a')
        key = MyWeirdKey()
        assert key in s
        assert key.log == ["called __eq__ with 'a'"]
        # But empty set does not contain the weird key
        s = f()
        key = MyWeirdKey()
        assert key not in s
        assert key.log == []
