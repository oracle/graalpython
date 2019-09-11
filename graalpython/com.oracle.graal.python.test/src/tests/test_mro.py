# Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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


def test_class_attr_change():
    class A(object):
        counter = 0

    for i in range(10):
        A.counter += 1

    assert A.counter == 10


def test_class_attr_deleted():
    class A(object):
        counter = 0

    class B(A):
        counter = 1

    for i in range(10):
        B.counter += 1

    assert B.counter == 11
    assert A.counter == 0
    del B.counter
    assert B.counter == 0

    for i in range(10):
        A.counter += 1
    assert A.counter == 10


def test_class_attr_added():
    class A(object):
        counter = 0

    class B(A):
        pass

    for i in range(10):
        B.counter += 1

    assert B.counter == 10
    assert A.counter == 0
    B.counter = 1
    assert B.counter == 1

    for i in range(10):
        A.counter += 1
    assert A.counter == 10


def test_class_attr_add_del():
    class A:
        foo = 1

    class B(A):
        foo = 2

    class C(B):
        foo = 3

    C.foo += 1
    C.foo += 1
    C.foo += 1
    C.foo += 1
    C.foo += 1
    C.foo += 1
    C.foo += 1

    assert C.foo == 10
    del C.foo
    assert C.foo == 2
    del B.foo
    assert C.foo == 1
    B.foo = 5
    assert C.foo == 5
    C.foo = 10
    assert C.foo == 10


def test_class_assignment():
    class A:
        foo = 1

    class B(A):
        foo = 2

    a = A()
    assert a.foo == 1
    a.__class__ = B
    assert a.foo == 2
    b = B()
    assert b.foo == 2
    b.__class__ = A
    assert b.foo == 1
    assert type(a) == B
    assert type(b) == A

    try:
        a.__class__ = 1
    except TypeError:
        assert True
    else:
        assert False

    try:
        a.__class__ = object
    except TypeError:
        assert True
    else:
        assert False

    try:
        object().__class__ = object
    except TypeError:
        assert True
    else:
        assert False


def test_class_slots():
    class X():
        __slots__ = "_local__impl", "__dict__"

        def __init__(self):
            self._local__impl = 1
            self.foo = 12
            self.__dict__ = {"bar": 42}


    assert X().bar == 42
    assert X()._local__impl == 1
    try:
        X().foo
    except AttributeError:
        assert True
    else:
        assert False

    x = X()
    x.foo = 1
    assert x.foo == 1
    assert x.__dict__["foo"] == 1
    x.__dict__["_local__impl"] = 22
    assert x._local__impl == 1

    assert X.__dict__["_local__impl"].__get__(x, type(x)) == 1


def test_class_with_slots_assignment():
    class X():
        __slots__ = "a", "b"

    class Y():
        __slots__ = "a", "b"

    class Z():
        __slots__ = "b", "c"


    x = X()
    x.__class__ = Y
    assert type(x) == Y
    try:
        x.__class__ = Z
    except TypeError as e:
        assert True
    else:
        assert False
