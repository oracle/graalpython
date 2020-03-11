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


def f3(a, b=f(1,2), c=10, *args, d="hello", e="world"):
    return a, b, c, args, d, e


class MyClass(object):
    def __init__(self, x = 10):
        pass


def test_defaults():
    assert f.__defaults__ == (10,)
    assert f2.__defaults__ == ((1, 2, 10, (), {}), 10)


def test_kwdefaults():
    assert f.__kwdefaults__ == None
    assert f2.__kwdefaults__ == None
    assert f3.__kwdefaults__ == { "d": "hello", "e": "world"}


def test_defaults_method():
    obj = MyClass()
    assert obj.__init__.__defaults__ == (10,)

    def assgn():
        obj.__init__.__defaults__ = (12,)
    assert_raises(AttributeError, assgn)


def test_constructor():
    import types
    func_copy = types.FunctionType(f.__code__, f.__globals__, f.__name__, f.__defaults__, f.__closure__)

    assert func_copy(1, 2) == (1, 2, 10, (), {}), func_copy(1, 2)
    assert func_copy(1, 2, 3) == (1, 2, 3, (), {}), func_copy(1, 2, 3)
    assert func_copy(1, 2, 3, 4, 5, x=2) == (1, 2, 3, (4, 5), {'x': 2}), func_copy(1, 2, 3, 4, 5, x=2)


def test_inner_function_with_defaults():
    def make_func(sep):
        def inner(sep=sep):
            return sep
        return inner

    inner_a = make_func(",")
    inner_b = make_func("\t")
    assert inner_b() == "\t"
    assert inner_a() == ","


def test_inner_function_with_closure():
    def make_func(sep):
        def inner():
            return sep
        return inner

    inner_a = make_func(",")
    inner_b = make_func("\t")
    assert inner_a() == ","
    assert inner_b() == "\t"


def test_inner_generator_with_defaults():
    def make_func(sep):
        def inner(sep=sep):
            yield sep
        return inner

    inner_a = make_func(",")()
    inner_b = make_func("\t")()
    assert next(inner_b) == "\t"
    assert next(inner_a) == ","


def test_inner_generator_with_closure():
    def make_func(sep):
        closure_value = sep
        def inner():
            yield closure_value
        return inner

    inner_a = make_func(",")()
    inner_b = make_func("\t")()
    assert next(inner_b) == "\t"
    assert next(inner_a) == ","


def test_function_changes_defaults():
    def foo(a):
        return a

    assert foo.__defaults__ is None
    assert foo.__kwdefaults__ is None
    assert_raises(TypeError, foo)

    foo.__defaults__ = (1,)
    assert foo() == 1

    foo.__kwdefaults__ = {"a": 12}
    assert foo() == 1

    foo.__defaults__ = None
    assert_raises(TypeError, foo)


def test_function_changes_kwdefaults():
    def foo(*args, x=1):
        return x

    assert foo.__defaults__ is None
    assert foo.__kwdefaults__ == {"x": 1}
    assert foo() == 1

    foo.__kwdefaults__ = {"x": 32}
    assert foo() == 32

    foo.__kwdefaults__ = None
    assert_raises(TypeError, foo)


def test_code_change():
    def foo():
        return "foo"

    def bar(a):
        return "bar" + str(a)

    assert foo() == "foo"
    foo.__code__ = bar.__code__
    assert foo(1) == "bar1"
    assert_raises(TypeError, foo)


def test_code_marshal_with_freevars():
    import marshal
    def foo():
        x,y = 1,2
        def bar():
            return x,y
        return bar

    def baz():
        x,y = 2,3
        def bar():
            return y,x
        return bar

    foobar_str = marshal.dumps(foo().__code__)
    foobar_code = marshal.loads(foobar_str)
    assert_raises(TypeError, exec, foobar_code)

    bazbar = baz()
    assert bazbar() == (3,2)

    def assign_code(x, y):
        if isinstance(y, type(assign_code)):
            x.__code__ = y.__code__
        else:
            x.__code__ = y

    assert_raises(ValueError, assign_code, foo, bazbar)
    assert_raises(ValueError, assign_code, foo, foobar_code)
    bazbar.__code__ = foobar_code
    assert bazbar() == (2,3)
    

def test_function_dict_writeable():
    def foo(): pass
    new_dict = { "customProp": "hello, world"}
    foo.__dict__ = new_dict
    assert foo.customProp == "hello, world"


def test_function_text_signature_writable():
    def foo(): pass
    foo.__text_signature__ = 'foo()'
    assert foo.__text_signature__ == 'foo()'
