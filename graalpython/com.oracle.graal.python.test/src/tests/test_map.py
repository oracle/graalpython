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

# map


def test_map0():
    items = [1, 2, 3, 4, 5]

    def sqr(x):
        return x ** 2

    assert list(map(sqr, items)) == [1, 4, 9, 16, 25]


def test_map1():
    items = [1, 2, 3, 4, 5]
    assert list(map(lambda x: x ** 2, items)) == [1, 4, 9, 16, 25]


def test_map2():
    items0 = [0, 1, 2, 3, 4]
    items1 = [5, 6, 7, 8, 9]
    assert list(map(lambda x, y: x * y, items0, items1)) == [0, 6, 14, 24, 36]


def test_map_contains():
    assert 1 in map(lambda s: s, [1])
    assert 2 not in map(lambda s: s, [1])

    class X():
        def __iter__(self):
            return self

        def __next__(self):
            i = getattr(self, "i", 0)
            if i == 1:
                raise StopIteration
            else:
                self.i = i + 1
                return i

    # the below checks that __contains__ consumes the iterator
    m = map(lambda s: s, X())
    assert 0 in m
    assert 0 not in m
