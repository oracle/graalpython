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


def test_positional_args():
    def foo(a, b, c):
        return a, b, c

    assert foo(1, 2, 3) == (1, 2, 3)
    assert foo(a=1, b=2, c=3) == (1, 2, 3)
    assert foo(c=3, a=1, b=2) == (1, 2, 3)
    assert foo(1, 2, c=3) == (1, 2, 3)
    assert foo(1, b=2, c=3) == (1, 2, 3)
    assert_raises(TypeError, foo, (1,), c=3)
    assert_raises(SyntaxError, exec, 'foo(a=1, 2, 3)')
    assert_raises(TypeError, foo, (1, 2), b=2)


bar = 10


def f0():
    pass


def f1(*args):
    pass


def f2(*args, **kw):
    pass


def f3(**kw):
    pass


def f4(foo=bar):
    pass


def f5(foo=bar, *args):
    pass


def f6(foo=bar, *args, **kw):
    pass


def f7(foo=bar, **kw):
    pass


def f8(a, b):
    pass


def f9(a, b, *args):
    pass


def f10(a, b, *args, **kw):
    pass


def f11(a, b, **kw):
    pass


def f12(a, b, foo=bar):
    pass


def f13(a, b, foo=bar, *args):
    pass


def f14(a, b, foo=bar, *args, **kw):
    pass


def f15(a, b, foo=bar, **kw):
    pass


def f16(*, a):
    pass


def f17(*, a=5):
    pass


def f18(*, a=5, b):
    pass


def f19(*, a, b=5):
    pass


def f20(*, a, b=5, **kwds):
    pass


def f21(*args, a):
    pass


def f22(*args, a=5):
    pass


def f23(*args, a=5, b):
    pass


def f24(*args, a, b=5):
    pass


def f25(*args, a, b=5, **kwds):
    pass

def f26(**kw):
    return kw

def assert_parses(call_expr):
    raised = False
    try:
        eval(call_expr)
    except:
        raised = True
    assert not raised


def assert_call_raises(exception, call_expr):
    assert_raises(exception, eval, call_expr)


def test_parse_args():
    assert_parses("f0()")
    assert_call_raises(TypeError, "f0(1, 2, 3)")
    assert_call_raises(TypeError, "f0(a=1, b=1)")
    assert_call_raises(TypeError, "f0(1,2,3,4, a=1, b=1)")

    assert_parses("f1()")
    assert_parses("f1(1, 2, 3)")
    assert_call_raises(TypeError, "f1(a=1)")

    assert_parses("f2()")
    assert_parses("f2(1, 2, 3)")
    assert_parses("f2(1, 2, 3, a=1, b=2)")
    assert_parses("f2(a=1, b=2)")

    assert_parses("f3()")
    assert_call_raises(TypeError, "f3(1, 2, 3)")  # TypeError: f3() takes 0 positional arguments but 3 were given
    assert_call_raises(TypeError, "f3(1, 2, 3, a=1, b=2)")  # TypeError: f3() takes 0 positional arguments but 3 were given
    assert_parses("f3(a=1, b=2)")

    assert_parses("f4()")
    assert_parses("f4(10)")
    assert_parses("f4(foo=10)")
    assert_call_raises(TypeError, "f4(a=10)")  # TypeError: f4() got an unexpected keyword argument 'a'

    assert_call_raises(SyntaxError, "f5(foo=1, 2, 3)")  # SyntaxError: positional argument follows keyword argument
    assert_parses("f5(foo=1)")
    assert_call_raises(TypeError, "f5(a=1)")  # TypeError: f5() got an unexpected keyword argument 'a'
    assert_parses("f5(1, 2, 3)")

    assert_parses("f6(foo=1)")
    assert_parses("f6(foo=1, a=2)")
    assert_call_raises(SyntaxError, "f6(foo=1, 3, 4, a=2)")  # SyntaxError: positional argument follows keyword argument

    assert_call_raises(TypeError, "f7(1, 2)")  # TypeError: f7() takes from 0 to 1 positional arguments but 2 were given
    assert_parses("f7(1)")
    assert_parses("f7(1, a=2)")
    assert_parses("f7(foo=1, a=2)")
    assert_parses("f7()")

    assert_call_raises(TypeError, "f8()")  # TypeError: f8() missing 2 required positional arguments: 'a' and 'b'
    assert_call_raises(TypeError, "f8(1)")  # TypeError: f8() missing 1 required positional argument: 'b'
    assert_parses("f8(1,2)")
    assert_call_raises(TypeError, "f8(1,2,3)")  # TypeError: f8() takes 2 positional arguments but 3 were given

    assert_call_raises(TypeError, "f9()")  # TypeError: f8() missing 2 required positional arguments: 'a' and 'b'
    assert_parses("f9(1,2,3)")

    assert_parses("f10(1,2,3,4,c=1)")
    assert_call_raises(TypeError, "f10(1,2,3,4,a=1)")  # TypeError: f10() got multiple values for argument 'a'

    assert_parses("f11(a=1, b=2)")
    assert_parses("f11(a=1, b=2, c=3)")
    assert_call_raises(SyntaxError, "f11(a=1, b=2, a=3)")  # SyntaxError: keyword argument repeated
    assert_call_raises(TypeError, "f11(1, b=2, a=3)")  # TypeError: f11() got multiple values for argument 'a'

    assert_parses("f12(1,2)")
    assert_call_raises(TypeError, "f12(a=1)")  # TypeError: f12() missing 1 required positional argument: 'b'
    assert_parses("f12(1,2,3)")
    assert_parses("f12(1,2,foo=3)")
    assert_parses("f12(a=1,b=2,foo=3)")
    assert_call_raises(SyntaxError, "f12(a=1,2,foo=3)")  # SyntaxError: positional argument follows keyword argument

    assert_parses("f13(1,2,3,4)")
    assert_call_raises(TypeError, "f13(1, 2, foo=3, c=4)")  # TypeError: f13() got an unexpected keyword argument 'c'

    assert_parses("f14(1, 2, foo=3, c=4)")
    assert_parses("f14(a=1, b=2, foo=3, c=4)")
    assert_parses("f14(a=1, b=2, foo=3)")
    assert_parses("f14(1, 2, 3, c=4)")
    assert_parses("f14(1, 2, 3, 4, 5, 6, 7, d=1)")
    assert_call_raises(TypeError, "f14(1, 2, 3, a=4)")  # TypeError: f14() got multiple values for argument 'a'

    assert_parses("f15(1, 2, foo=3, c=4)")
    assert_parses("f15(a=1, b=2, foo=3, c=4)")
    assert_parses("f15(a=1, b=2, foo=3)")
    assert_parses("f15(1, 2, 3, c=4)")
    assert_call_raises(TypeError, "f15(1, 2, 3, 4, 5, 6, 7, d=1)")  # TypeError: f15() takes from 2 to 3 positional arguments but 7 were given

    # keyword only args
    assert_call_raises(TypeError, "f16()")  # TypeError: f16() missing 1 required keyword-only argument: 'a'
    assert_call_raises(TypeError, "f16(1)")  # TypeError: f16() takes 0 positional arguments but 1 was given
    assert_parses("f16(a=1)")
    assert_call_raises(TypeError, "f16(a=1, b=1)")  # TypeError: f16() got an unexpected keyword argument 'b'

    assert_parses("f17()")
    assert_parses("f17(a=1)")
    assert_call_raises(TypeError, "f17(b=1)")  # TypeError: f17() got an unexpected keyword argument 'b'
    assert_call_raises(TypeError, "f17(1)")  # TypeError: f17() takes 0 positional arguments but 1 was given

    assert_call_raises(TypeError, "f18(1,2)")  # TypeError: f18() takes 0 positional arguments but 2 were given
    assert_call_raises(SyntaxError, "f18(a=1,2)")  # SyntaxError: positional argument follows keyword argument
    assert_parses("f18(a=1,b=2)")
    assert_call_raises(TypeError, "f18(a=1,c=2)")  # TypeError: f18() got an unexpected keyword argument 'c'
    assert_call_raises(TypeError, "f18(1,b=2)")  # TypeError: f18() takes 0 positional arguments but 1 positional argument (and 1 keyword-only argument) were given

    assert_parses("f19(a=1)")
    assert_parses("f19(a=1, b=2)")
    assert_call_raises(TypeError, "f19(1, b=2)")  # TypeError: f19() takes 0 positional arguments but 1 positional argument (and 1 keyword-only argument) were given
    assert_call_raises(TypeError, "f19(1)")  # TypeError: f19() takes 0 positional arguments but 1 was given

    assert_parses("f20(a=1)")
    assert_parses("f20(a=1, b=2)")
    assert_parses("f20(a=1, b=2, c=3)")
    assert_call_raises(SyntaxError, "f20(a=1, b=2, a=3)")  # SyntaxError: keyword argument repeated
    assert_call_raises(TypeError, "f20(1, b=2)")  # TypeError: f20() takes 0 positional arguments but 1 positional argument (and 1 keyword-only argument) were given
    assert_call_raises(TypeError, "f20(1)")  # TypeError: f20() takes 0 positional arguments but 1 was given

    assert_call_raises(TypeError, "f21(1,2,3)")  # TypeError: f21() missing 1 required keyword-only argument: 'a'
    assert_parses("f21(1,2,a=3)")
    assert_parses("f21(a=3)")

    assert_parses("f22()")
    assert_parses("f22(a=3)")
    assert_parses("f22(1,2,a=3)")
    assert_call_raises(TypeError, "f22(a=2, b=3)")  # TypeError: f22() got an unexpected keyword argument 'b'

    assert_call_raises(TypeError, "f23(1,2,3)")  # TypeError: f23() missing 1 required keyword-only argument: 'b'
    assert_parses("f23(1,2,3,b=4)")
    assert_parses("f23(1,2,a=3,b=4)")
    assert_parses("f23(1,2,b=4,a=3)")

    assert_call_raises(TypeError, "f24(1,2,3)")  # TypeError: f24() missing 1 required keyword-only argument: 'a'
    assert_parses("f24(1,2,a=3)")
    assert_parses("f24(1,2,a=3,b=4)")
    assert_call_raises(TypeError, "f24(1,2, a=3, b=4, c=5)")  # TypeError: f24() got an unexpected keyword argument 'c'
    assert_parses("f24(a=1)")
    assert_call_raises(TypeError, "f24(1)")  # TypeError: f24() missing 1 required keyword-only argument: 'a'
    assert_parses("f24(a=1, b=2)")
    assert_call_raises(SyntaxError, "f24(a=1, 2)")  # SyntaxError: positional argument follows keyword argument

    assert_call_raises(TypeError, "f25()")  # TypeError: f25() missing 1 required keyword-only argument: 'a'
    assert_call_raises(TypeError, "f25(1,2,3)")  # TypeError: f25() missing 1 required keyword-only argument: 'a'
    assert_parses("f25(1,2,3,a=4)")
    assert_parses("f25(1,2,3,a=4,b=5)")
    assert_call_raises(SyntaxError, "f25(1,2,3,a=4,5)")  # SyntaxError: positional argument follows keyword argument
    assert_parses("f25(1,2,3,a=4,b=5,c=6)")
    assert_parses("f25(1,2,3,a=4,c=6)")
    assert_call_raises(TypeError, "f25(1,2,3,c=6)")  # TypeError: f25() missing 1 required keyword-only argument: 'a'
    assert_parses("f25(a=4,c=6)")
    assert_parses("f25(a=4)")

def test_multiple_values_for_keyword_argument():
    assert_call_raises(TypeError, "f26(a=1, **{'a' : 2})") # TypeError: f26() got multiple values for keyword argument 'a'
    assert_call_raises(TypeError, "f26(**{'a' : 4}, **{'a': 3})")  # TypeError: f26() got multiple values for keyword argument 'a'
    
b = 1
def test_argument_must_be_mapping():    
    assert_call_raises(TypeError, "f26(a=1, **b)") # TypeError: f26() argument after ** must be a mapping, not int
    assert_call_raises(TypeError, "f26(**b)") # TypeError: f26() argument after ** must be a mapping, not int
    class MyDict(dict):
        pass
    d = MyDict()
    assert f26(**d) == {}

fooo = f26
def test_multiple_values_with_callable_name():
    def assert_get_message(err, fn, *args, **kwargs):
        raised = False
        try:
            fn(*args, **kwargs)
        except err as ex:
            return str(ex)
        assert raised
        
    def assert_call_raises_get_message(exception, call_expr):
        return assert_get_message(exception, eval, call_expr)

    msg = assert_call_raises_get_message(TypeError, "fooo(a=1, **b)") # TypeError: f26() argument after ** must be a mapping, not int
    assert msg == "f26() argument after ** must be a mapping, not int"
    
    msg = assert_call_raises_get_message(TypeError, "fooo(**{'a' : 4}, **{'a': 3})")  # TypeError: f26() got multiple values for keyword argument 'a'
    assert msg == "f26() got multiple values for keyword argument 'a'"
    