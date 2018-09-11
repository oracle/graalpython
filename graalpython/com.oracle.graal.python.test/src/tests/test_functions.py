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


def assert_raises(err, fn, *args, **kwargs):
    raised = False
    try:
        fn(*args, **kwargs)
    except err:
        raised = True
    assert raised


def test_name():
    def foo():
        pass
    assert "foo" in str(foo)
    assert foo.__name__ == "foo"
    foo.__name__ = "bar"
    assert foo.__name__ == "bar"
    assert "bar" not in str(foo)
    assert "foo" in str(foo)
    try:
        foo.__name__ = 42
    except TypeError as e:
        assert "__name__ must be set to a string object" in str(e)
    else:
        assert False


def f(a, b, c=10, *args, **kwargs):
    return a, b, c, args, kwargs


def f2(a=f(1, 2), b=10):
    return a, b


class MyClass(object):
    def __init__(self, x = 10):
        pass


def test_defaults():
    assert f.__defaults__ == (10,)
    assert f2.__defaults__ == ((1, 2, 10, (), {}), 10)


def test_defaults_method():
    obj = MyClass()
    assert obj.__init__.__defaults__ == (10,)

    def assgn():
        obj.__init__.__defaults__ = (12,)
    assert_raises(AttributeError, assgn)


def test_constructor():
    import types
    func_copy = types.FunctionType(f.__code__, f.__globals__, f.__name__, f.__defaults__, f.__closure__)

    assert func_copy(1, 2) == (1, 2, 10, (), {})
    assert func_copy(1, 2, 3) == (1, 2, 3, (), {})
    assert func_copy(1, 2, 3, 4, 5, x=2) == (1, 2, 3, (4, 5), {'x': 2})
