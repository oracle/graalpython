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


def assert_raises(err, fn, *args, **kwargs):
    raised = False
    try:
        fn(*args, **kwargs)
    except err:
        raised = True
    assert raised


def test_assign_local_func_return():
    var = {}

    def varfunc():
        return var

    varfunc()['x'] = 10

    assert 'x' in var
    assert var['x'] == 10


global_var = {}


def _varfunc():
    return global_var


def test_assign_nonlocal_func_return():
    _varfunc()['x'] = 10

    assert 'x' in global_var
    assert global_var['x'] == 10


def test_destructuring():
    a, b = (1, 2)
    assert a == 1 and b == 2

    a, b, c = "\xe0\xdf\xe7"
    assert a == "à" and b == "ß" and c == "ç"

    a, b, c = "\u0430\u0431\u0432"
    assert a == 'а' and b == 'б' and c == 'в'
    # TODO not supported yet
#     a, b, c = "\U0001d49c\U0001d49e\U0001d4b5"
#     assert a == '𝒜' and b == '𝒞' and c == '𝒵

    # starred desctructuring assignment
    a, b, *s, c, d = tuple(range(4))
    assert a == 0 and b == 1 and c == 2 and d == 3

    a, b, *s, c, d = tuple(range(10))
    assert a == 0 and b == 1 and s == [2, 3, 4, 5, 6, 7] and c == 8 and d == 9

    c = -1
    d = -1
    a, b, *s = tuple(range(10))
    assert a == 0 and b == 1 and s == [2, 3, 4, 5, 6, 7, 8, 9] and c == -1 and d == -1

    a = -1
    b = -1
    *s, c, d = tuple(range(10))
    assert a == -1 and b == -1 and s == [0, 1, 2, 3, 4, 5, 6, 7] and c == 8 and d == 9


def test_assigning_hidden_keys():
    class A():
        def __init__(self):
            self.__dict__["xyz"] = 1

    ary = [A(), A(), A(), A(), A(), A()]
    for a in ary:
        a.foo = 12

    for a in ary:
        id(a) # id is stored in a HiddenKey

    return
