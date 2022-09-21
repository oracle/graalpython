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


def test_dont_mask_non_attribute_error():
    class C(object):
        def getbases(self):
            raise RuntimeError
        __bases__ = property(getbases)

    class S(C): pass

    assert_raises(RuntimeError, issubclass, C(), S())


def test_mask_attribute_error():
    class C(object):
        def getbases(self):
            raise AttributeError
        __bases__ = property(getbases)

    class S(C): pass

    assert_raises(TypeError, issubclass, C(), S())


# Like above, but test the second branch, where the __bases__ of the
# second arg (the cls arg) is tested.  This means the first arg must
# return a valid __bases__, and it's okay for it to be a normal --
# unrelated by inheritance -- class.
def test_dont_mask_non_attribute_error_in_cls_arg():
    class B: pass

    class C(object):
        def getbases(self):
            raise RuntimeError
        __bases__ = property(getbases)

    assert_raises(RuntimeError, issubclass, B, C())


def test_mask_attribute_error_in_cls_arg():
    class B: pass

    class C(object):
        def getbases(self):
            raise AttributeError
        __bases__ = property(getbases)

    assert_raises(TypeError, issubclass, B, C())


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


def test_subclass_normal():
    # normal classes
    assert issubclass(Super, Super)
    assert not issubclass(Super, AbstractSuper)
    assert not issubclass(Super, Child)

    assert issubclass(Child, Child)
    assert issubclass(Child, Super)
    assert not issubclass(Child, AbstractSuper)


def test_subclass_abstract():
    # abstract classes
    assert issubclass(AbstractSuper, AbstractSuper)
    assert not issubclass(AbstractSuper, AbstractChild)
    assert not issubclass(AbstractSuper, Child)

    assert issubclass(AbstractChild, AbstractChild)
    assert issubclass(AbstractChild, AbstractSuper)
    assert not issubclass(AbstractChild, Super)
    assert not issubclass(AbstractChild, Child)


def test_subclass_tuple():
    # test with a tuple as the second argument classes
    assert issubclass(Child, (Child,))
    assert issubclass(Child, (Super,))
    assert not issubclass(Super, (Child,))
    assert issubclass(Super, (Child, Super))
    assert not issubclass(Child, ())
    assert issubclass(Super, (Child, (Super,)))

    assert issubclass(int, (int, (float, int)))
    assert issubclass(str, (str, (Child, str)))


def test_abstract_numbers_issubclass():
    from numbers import Number, Integral, Complex, Real
    assert issubclass(int, Number)
    assert issubclass(int, Integral)
    assert issubclass(int, Complex)

    assert not issubclass(complex, Real)
    assert issubclass(complex, Complex)
