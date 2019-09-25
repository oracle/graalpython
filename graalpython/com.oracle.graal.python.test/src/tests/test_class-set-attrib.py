# Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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

counter = 0


class Foo:
    def __setattr__(self, key, value):
        global counter
        counter = counter + 1
        object.__setattr__(self, key, value)

    def __delattr__(self, key):
        global counter
        counter = counter + 10
        object.__delattr__(self, key)


def test_call():
    global counter
    counter = 0
    f = Foo()
    f.a = 1
    Foo.b = 123
    assert counter == 1, "setting attrib on class should not call its own __setattr__"
    del f.a
    del Foo.b
    assert counter == 11, "deleting attrib on class should not call its own __delattr__"


class AClass:
    pass


class BClass(AClass):
    pass


class CClass(BClass):
    pass


class DClass(BClass):
    pass


def custom_set(self, key, value):
    object.__setattr__(self, key, value + 10 if isinstance(value, int) else value)


def custom_get(self, key):
    value = object.__getattribute__(self, key)
    return value + 100 if isinstance(value, int) else value


def test_assignments():
    object = CClass()
    # writing to BClass changes the result, writing to DClass doesn't
    targets = (AClass, BClass, DClass, CClass, object)
    results = (0, 1, 1, 3, 4)
    for i in range(0, len(targets)):
        targets[i].foo = i
        assert object.foo == results[i], "normal %d" % i
    # make sure that a custom __getattribute__ is used
    BClass.__getattribute__ = custom_get
    for i in range(0, len(targets)):
        targets[i].bar = i
        assert object.bar == results[i] + 100, "custom get %d" % i
    # check correct lookups when deleting attributes
    for i in reversed(range(0, len(targets))):
        assert object.bar == results[i] + 100, "delete %d" % i
        del targets[i].bar
    # make sure a custom __setattr__ is used
    BClass.__setattr__ = custom_set
    object.baz = 9
    assert object.baz == 119, "custom set"


def test_setattr_via_decorator():
    def setdec(func):
        setattr(func, 'SPECIAL_ATTR', {'a': 1, 'b': 2})
        return func

    @setdec
    def f():
        return 1

    assert hasattr(f, 'SPECIAL_ATTR')
    assert getattr(f, 'SPECIAL_ATTR') == {'a': 1, 'b': 2}

    class MyClass(object):
        @setdec
        def f(self):
            return 1

    m = MyClass()
    assert hasattr(m.f, 'SPECIAL_ATTR')
    assert getattr(m.f, 'SPECIAL_ATTR') == {'a': 1, 'b': 2}


def test_deepcopy_attribute_removal():
    from copy import deepcopy

    class A:
        def __init__(self):
            self.a = "a"

        def add_rem_attr(self):
            self.b = "b"
            del self.b

    i1 = A()
    assert i1.__dict__ == deepcopy(i1).__dict__

    i1.add_rem_attr()
    assert i1.__dict__ == deepcopy(i1).__dict__
