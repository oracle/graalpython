# Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

import mmap

PAGESIZE = mmap.PAGESIZE
FIND_BUFFER_SIZE = 1024  # keep in sync with FindNode#BUFFER_SIZE


def test_find():
    cases = [
        # (size, needle_pos)
        (FIND_BUFFER_SIZE * 3 + 1, FIND_BUFFER_SIZE * 3 - 2),
        (FIND_BUFFER_SIZE * 3 + 3, FIND_BUFFER_SIZE * 3),
        (FIND_BUFFER_SIZE * 2, FIND_BUFFER_SIZE),
        (FIND_BUFFER_SIZE * 2, FIND_BUFFER_SIZE - 1),
        (11, 1),
    ]
    for (size, needle_pos) in cases:
        m = mmap.mmap(-1, size)
        m[needle_pos] = b'a'[0]
        m[needle_pos + 1] = b'b'[0]
        m[needle_pos + 2] = b'c'[0]
        assert m.find(b'abc') == needle_pos
        assert m.find(b'abc', 0, needle_pos) == -1
        assert m.find(b'abc', 0, needle_pos + 2) == -1
        assert m.find(b'abc', 0, needle_pos + 3) == needle_pos
        assert m.find(b'abc', needle_pos) == needle_pos
        assert m.find(b'abc', needle_pos + 1) == -1
        assert m.find(b'abc', needle_pos - 1) == needle_pos
        m.close()


def test_getitem():
    m = mmap.mmap(-1, 12)
    for i in range(0, 12):
        m[i] = i
    assert m[slice(-10, 100)] == b'\x02\x03\x04\x05\x06\x07\x08\t\n\x0b'


def test_readline():
    m = mmap.mmap(-1, 9)
    for i in range(0, 9):
        m[i] = i
    m[4] = b'\n'[0]
    assert m.readline() == b'\x00\x01\x02\x03\n'
    assert m.readline() == b'\x05\x06\x07\x08'

    m = mmap.mmap(-1, 1024 + 3)
    m[1024] = b'\n'[0]
    m[1025] = b'a'[0]
    m[1026] = b'b'[0]
    assert m.readline() == (b'\x00' * 1024) + b'\n'
    assert m.readline() == b'ab'

def test_iter():
    m = mmap.mmap(-1, 3)
    for i in range(0, 3):
        m[i] = i + 2

    it = iter(m)
    assert next(it) == b'\x02'
    assert next(it) == b'\x03'
    assert next(it) == b'\x04'

    l = list()
    for i in m:
        l.append(i)

    assert l == [b'\x02', b'\x03', b'\x04']