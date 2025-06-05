# Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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

global_log = []


def simple_decorator(func):
    def wrapper(*args, **kwargs):
        global_log.append([func.__name__, args, kwargs])
        return func(*args, **kwargs)
    return wrapper


def test_simple_decorator():
    del global_log[:]

    @simple_decorator
    def myfunc(a, b, **kwargs):
        return a, b, kwargs

    assert myfunc(10, 20) == (10, 20, {})
    assert global_log[-1] == ["myfunc", (10, 20), {}]
    assert myfunc(10, 20, x=1, y=2) == (10, 20, {'x': 1, 'y': 2})
    assert global_log[-1] == ["myfunc", (10, 20), {'x': 1, 'y': 2}]


def test_eval_order():
    MyBase = None

    def create_base(dummy_arg):
        nonlocal MyBase
        class MyBaseK:
            pass
        MyBase = MyBaseK
        return lambda a: a

    # this should just work: the decorator expression first
    # creates MyBase and only then is the rest evaluated
    @create_base('dummy')
    class MyClass(MyBase):
        pass

    # dummy helper for following tests
    def my_decorator(x):
        return x

    class assert_name_error:
        def __init__(self, name):
            self.name = name
        def __enter__(self):
            pass
        def __exit__(self, exc_type, exc_val, exc_tb):
            assert exc_type == NameError, f"did not raise NameError with name '{self.name}'"
            assert self.name in str(exc_val), f"not NameError for name '{self.name}'"
            return True

    with assert_name_error('my_decoratorr'):
        @my_decoratorr
        class ClassA(NonExistingBaseClass):
            pass

    with assert_name_error('NonExistingBase'):
        @my_decorator
        class ClassA(NonExistingBase):
            pass

    with assert_name_error('my_decoratorr'):
        @my_decoratorr
        def my_function(x=NonexistingName):
            pass

    with assert_name_error('NonexistingDefaultName'):
        @my_decorator
        def my_function(x=NonexistingDefaultName):
            pass
