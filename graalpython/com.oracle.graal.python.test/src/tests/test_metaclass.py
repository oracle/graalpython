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

import sys


global_log = []


def clear_global_log(func):
    def wrapper(*args, **kwargs):
        del global_log[:]
        return func(*args, **kwargs)
    return wrapper


class Meta(type):
    def __prepare__(*args, **kwargs):
        global_log.append(["__prepare__", args, kwargs])
        return type.__prepare__(*args, **kwargs)

    def __new__(*args, **kwargs):
        global_log.append(["__new__", args, kwargs])
        return type.__new__(*args, **kwargs)

    def __init__(*args, **kwargs):
        global_log.append(["__init__", args, kwargs])
        return type.__init__(*args, **kwargs)

    def __call__(*args, **kwargs):
        global_log.append(["__call__", args, kwargs])
        return type.__call__(*args, **kwargs)


@clear_global_log
def test_class_construction():
    class Foo(metaclass=Meta):
        pass
    Foo()
    assert global_log[0] == ["__prepare__", ("Foo", tuple()), {}]

    initial_dict = {'__qualname__': 'test_class_construction.<locals>.Foo', '__module__': 'tests.test_metaclass'}
    # if sys.implementation.name == "graalpython":
    #     initial_dict = {
    #         '__module__': 'test_metaclass'
    #     }
    assert global_log[1] == ["__new__", (Meta, "Foo", tuple(), initial_dict), {}]
    assert global_log[2] == ["__init__", (Foo, "Foo", tuple(), initial_dict), {}]
    assert global_log[3] == ["__call__", (Foo,), {}]


@clear_global_log
def test_metaclass_methods():
    class MyMeta(type):
        def __new__(meta, name, bases, dct):
            global_log.append(["__new__", meta, name, bases, dct])
            return super(MyMeta, meta).__new__(meta, name, bases, dct)

        def __init__(cls, name, bases, dct):
            global_log.append(["__init__", cls, name, bases, dct])
            super(MyMeta, cls).__init__(name, bases, dct)

        def __call__(cls, *args, **kwds):
            global_log.append(["__call__", cls, args, kwds])
            return type.__call__(cls, *args, **kwds)

        def a_method(cls, arg):
            return cls, arg

    class MyClass(metaclass=MyMeta):
        def __init__(self, a, b):
            global_log.append(["MyKlass object", a, b])

    assert isinstance(MyClass, MyMeta)
    ns_dict = {
        '__qualname__': 'test_metaclass_methods.<locals>.MyClass',
        '__init__': MyClass.__init__,
        '__module__': 'tests.test_metaclass'
    }
    # if sys.implementation.name == "graalpython":
    #     ns_dict = {
    #         '__init__': MyClass.__init__,
    #         '__module__': 'test_metaclass',
    #     }
    assert len(global_log) == 2
    assert global_log[0] == ["__new__", MyMeta, "MyClass", (), ns_dict]
    assert global_log[1] == ["__init__", MyClass, "MyClass", (), ns_dict]

    assert MyClass.a_method(10) == (MyClass, 10)
    m = MyClass(1, 2)
    assert isinstance(m, MyClass)
    assert len(global_log) == 4
    assert global_log[2] == ['__call__', MyClass, (1, 2), {}]
    assert global_log[3] == ['MyKlass object', 1, 2]
    

class A:
    class B:
        pass


def test_nested_class():
    assert A.__name__ == "A"
    assert A.__qualname__ == "A"
    assert A.__module__ == __name__, "should be '%s' but was '%s'" % (__name__, A.__module__)
    assert A.B.__name__ == "B"
    assert A.B.__qualname__ == "A.B"
    assert A.B.__module__ == __name__
