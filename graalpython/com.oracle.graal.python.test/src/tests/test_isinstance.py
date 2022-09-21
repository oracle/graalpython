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


def assert_raises(err, fn, *args, **kwargs):
    raised = False
    try:
        fn(*args, **kwargs)
    except err:
        raised = True
    assert raised


def test_instancecheck():
    class M(type):
        def __instancecheck__(cls, instance):
            return isinstance(instance, C)

    class A(metaclass=M):
        pass

    class B(object):
        pass

    class C(object):
        pass

    a = A()
    b = B()
    c = C()

    assert isinstance(a, A)
    assert not isinstance(a, B)
    assert not isinstance(a, C)

    assert not isinstance(b, A)
    assert isinstance(b, B)
    assert not isinstance(b, C)

    assert isinstance(c, A)
    assert not isinstance(c, B)
    assert isinstance(c, C)


# Test to make sure that an AttributeError when accessing the instance's
# class's bases is masked.  This was actually a bug in Python 2.2 and
# 2.2.1 where the exception wasn't caught but it also wasn't being cleared
# (leading to an "undetected error" in the debug build).  Set up is,
# isinstance(inst, cls) where:
#
# - cls isn't a type, or a tuple
# - cls has a __bases__ attribute
# - inst has a __class__ attribute
# - inst.__class__ as no __bases__ attribute
#
# Sounds complicated, I know, but this mimics a situation where an
# extension type raises an AttributeError when its __bases__ attribute is
# gotten.  In that case, isinstance() should return False.
def test_class_has_no_bases():
    class I(object):
        def getclass(self):
            # This must return an object that has no __bases__ attribute
            return None
        __class__ = property(getclass)

    class C(object):
        def getbases(self):
            return ()
        __bases__ = property(getbases)

    assert not isinstance(I(), C())


# Like above except that inst.__class__.__bases__ raises an exception
# other than AttributeError
def test_bases_raises_other_than_attribute_error():
    class E(object):
        def getbases(self):
            raise RuntimeError
        __bases__ = property(getbases)

    class I(object):
        def getclass(self):
            return E()
        __class__ = property(getclass)

    class C(object):
        def getbases(self):
            return ()
        __bases__ = property(getbases)

    assert_raises(RuntimeError, isinstance, I(), C())


# Here's a situation where getattr(cls, '__bases__') raises an exception.
# If that exception is not AttributeError, it should not get masked
def test_dont_mask_non_attribute_error():
    class I: pass

    class C(object):
        def getbases(self):
            raise RuntimeError
        __bases__ = property(getbases)

    assert_raises(RuntimeError, isinstance, I(), C())


# Like above, except that getattr(cls, '__bases__') raises an
# AttributeError, which /should/ get masked as a TypeError
def test_mask_attribute_error():
    class I: pass

    class C(object):
        def getbases(self):
            raise AttributeError
        __bases__ = property(getbases)

    assert_raises(TypeError, isinstance, I(), C())


# check that we don't mask non AttributeErrors
# see: http://bugs.python.org/issue1574217
def test_isinstance_dont_mask_non_attribute_error():
    class C(object):
        def getclass(self):
            raise RuntimeError
        __class__ = property(getclass)

    c = C()
    assert_raises(RuntimeError, isinstance, c, bool)

    # test another code path
    class D: pass
    assert_raises(RuntimeError, isinstance, c, D)


# meta classes for creating abstract classes and instances
class AbstractClass(object):
    def __init__(self, bases):
        self.bases = bases

    def getbases(self):
        return self.bases
    __bases__ = property(getbases)

    def __call__(self):
        return AbstractInstance(self)


class AbstractInstance(object):
    def __init__(self, klass):
        self.klass = klass

    def getclass(self):
        return self.klass
    __class__ = property(getclass)


# abstract classes
AbstractSuper = AbstractClass(bases=())

AbstractChild = AbstractClass(bases=(AbstractSuper,))


# normal classes
class Super:
    pass


class Child(Super):
    pass


def test_isinstance_normal():
    # normal instances
    assert isinstance(Super(), Super)
    assert not isinstance(Super(), Child)
    assert not isinstance(Super(), AbstractSuper)
    assert not isinstance(Super(), AbstractChild)

    assert isinstance(Child(), Super)
    assert not isinstance(Child(), AbstractSuper)


def test_isinstance_abstract():
    # abstract instances
    assert isinstance(AbstractSuper(), AbstractSuper)
    assert not isinstance(AbstractSuper(), AbstractChild)
    assert not isinstance(AbstractSuper(), Super)
    assert not isinstance(AbstractSuper(), Child)

    assert isinstance(AbstractChild(), AbstractChild)
    assert isinstance(AbstractChild(), AbstractSuper)
    assert not isinstance(AbstractChild(), Super)
    assert not isinstance(AbstractChild(), Child)


def test_isinstance_recursive():
    called_instancecheck = 0
    expected_other = Child()

    class UnrelatedMeta(type):
        def __instancecheck__(self, other):
            nonlocal called_instancecheck
            called_instancecheck += 1
            assert other == expected_other
            # Force it do read caller frame for some more fun
            # Note that we should be now in indirect call
            globals()
            return super(UnrelatedMeta, self).__instancecheck__(other)

    class Unrelated(metaclass=UnrelatedMeta):
        pass

    tpl = (Unrelated,)
    for i in range(1, 20):
        tpl = (tpl, tuple([Unrelated] * i))

    def call_isinstance(expected, tpl):
        nonlocal called_instancecheck
        called_instancecheck = 0
        assert isinstance(expected, tpl) is False
        assert called_instancecheck == 191

    # Call the test few times to also force the compilation with some luck...
    for i in range(1, 1000):
        call_isinstance(expected_other, tpl)

    tpl = (Unrelated,)
    for i in range(1, 19):
        tpl = (tpl, tuple([Unrelated] * i))

    # the very last item that isinstance should inspect should be Super
    tpl = (tpl, tuple([Unrelated] * 18 + [Super]))

    called_instancecheck = 0
    assert isinstance(expected_other, tpl) is True
    assert called_instancecheck == 190