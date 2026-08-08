# Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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


def assert_raises(err, fn, *args, **kwargs):
    raised = False
    try:
        fn(*args, **kwargs)
    except err:
        raised = True
    assert raised


def test_explicit_none_start():
    # GH-1074: start defaults to 0 only when the argument is omitted; an
    # explicitly passed None goes through PyNumber_Index and raises
    assert_raises(TypeError, enumerate, 'abc', None)
    assert_raises(TypeError, enumerate, 'abc', start=None)


def test_omitted_start_after_rejected_start():
    # GH-1074: the specializations are shared, so rejecting a bad start must
    # not break a later call that omits it
    assert_raises(TypeError, enumerate, 'abc', 'x')
    assert list(enumerate('abc')) == [(0, 'a'), (1, 'b'), (2, 'c')]


def test_bool_start():
    # GH-1074: bool is an int subclass, so True is a start of 1
    assert list(enumerate('abc', True)) == [(1, 'a'), (2, 'b'), (3, 'c')]
    assert list(enumerate('abc', False)) == [(0, 'a'), (1, 'b'), (2, 'c')]


def test_index_start():
    # GH-1074: any object implementing __index__ is accepted as start
    class Idx:
        def __index__(self):
            return 3

    assert list(enumerate([9, 8, 7], Idx())) == [(3, 9), (4, 8), (5, 7)]


def test_huge_index_start():
    # GH-1074: an __index__ result too large for a Java long still works
    class BigIdx:
        def __index__(self):
            return 2 ** 70

    assert list(enumerate('a', BigIdx())) == [(1180591620717411303424, 'a')]


def test_float_start_is_rejected():
    assert_raises(TypeError, enumerate, 'abc', 1.0)


def test_omitted_start_after_coerced_start():
    # GH-1074: coercing a start must not break a later call that omits it
    assert list(enumerate('abc', True)) == [(1, 'a'), (2, 'b'), (3, 'c')]
    assert list(enumerate('abc')) == [(0, 'a'), (1, 'b'), (2, 'c')]
