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


def assert_raises(err, fn, *args, **kwargs):
    raised = False
    try:
        fn(*args, **kwargs)
    except err:
        raised = True
    assert raised


def test_set_dict_attr_builtin_extension():
    class MyList(list):
        pass

    lst = MyList()
    assert lst.__dict__ == {}
    lst.__dict__ = {'a': 9}
    assert lst.a == 9
    assert lst.__dict__ == {'a': 9}


def test_get_dict_attr():
    o = object()

    def get_dict_attr():
        return o.__dict__

    def set_dict_attr():
        o.__dict__ = {'a': 10}

    assert_raises(AttributeError, get_dict_attr)
    assert_raises(AttributeError, set_dict_attr)


def test_set_dict_attr():
    class MyClass(object):
        def __init__(self):
            self.a = 9

    m = MyClass()
    assert m.a == 9
    assert m.__dict__ == {'a': 9}
    assert m.a == 9
    m.__dict__ = {'a': 10}
    assert m.__dict__ == {'a': 10}
    assert m.a == 10
    m.d = 20
    assert m.d == 20
    assert "d" in m.__dict__
    assert m.__dict__ == {'a': 10, 'd': 20}


def test_set_attr_builtins():
    lst = list()

    def set_attr():
        lst.a = 10

    assert_raises(AttributeError, set_attr)

    class MyList(list):
        pass

    mlst = MyList()
    mlst.a = 10
    assert mlst.a == 10


def test_set_dict_attr_with_getattr_defined():
    class MyOtherClass(object):
        def __getattribute__(self, item):
            return object.__getattribute__(self, item)

        def __getattr__(self, item):
            if item == "my_attr":
                return 10
            raise AttributeError

    m1 = MyOtherClass()

    def get_non_existing_attr():
        return m1.my_attr_2

    assert_raises(AttributeError, get_non_existing_attr)
    assert m1.my_attr == 10
    assert "my_attr" not in m1.__dict__

    m1.__dict__ = {'d': 10}
    assert m1.my_attr == 10
    assert "my_attr" not in m1.__dict__
    assert m1.d == 10


def test_class_attr():
    class AAA:
        def foo(self):
            assert __class__ == AAA
            assert self.__class__ == AAA

    class BBB:
        pass

    class CCC(AAA):
        def getclass(self):
            return BBB

        __class__ = property(getclass)

        def bar(self):
            assert __class__ == CCC
            assert self.__class__ == BBB

    AAA().foo()
    CCC().bar()
