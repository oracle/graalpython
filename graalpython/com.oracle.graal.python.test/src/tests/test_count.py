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

from itertools import count


def assert_raises(err, fn, *args, **kwargs):
    raised = False
    try:
        fn(*args, **kwargs)
    except err:
        raised = True
    assert raised


def test_count():
    assert_raises(TypeError, count, 2, 3, 4)
    assert_raises(TypeError, count, 'a')
    c = count(3)
    assert repr(c) == 'count(3)'
    next(c)
    assert repr(c) == 'count(4)'
    c = count(-9)
    assert repr(c) == 'count(-9)'
    next(c)
    assert next(c) == -8
    assert repr(count(10.25)) == 'count(10.25)'
    assert repr(count(10.0)) == 'count(10.0)'
    assert type(next(count(10.0))) == float


def test_count_with_stride():
    c = count(3, 5)
    assert repr(c) == 'count(3, 5)'
    next(c)
    assert repr(c) == 'count(8, 5)'
    c = count(-9, 0)
    assert repr(c) == 'count(-9, 0)'
    next(c)
    assert repr(c) == 'count(-9, 0)'
    c = count(-9, -3)
    assert repr(c) == 'count(-9, -3)'
    next(c)
    assert repr(c) == 'count(-12, -3)'
    assert repr(c) == 'count(-12, -3)'
    assert repr(count(10.5, 1.25)) == 'count(10.5, 1.25)'
    assert repr(count(10.5, 1)) == 'count(10.5)'           # suppress step=1 when it's an int
    assert repr(count(10.5, 1.00)) == 'count(10.5, 1.0)'   # do show float values lilke 1.0
    c = count(10, 1.0)
    assert type(next(c)) == int
    # this does not pass on python 3.5.2, but passes on later versions
    # TODO: disabled for now
    # assert type(next(c)) == float
