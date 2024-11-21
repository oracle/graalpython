# Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
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


# IMPORTANT: DO NOT MOVE!
# This test checks that lineno works on frames,
# it MUST stay on this line!
def test_lineno():
    assert sys._getframe(0).f_lineno == 47


# IMPORTANT: DO NOT MOVE!
def test_nested_lineno():
    def test_nested():
        return sys._getframe(0)

    f = test_nested()
    assert f.f_lineno == 53

# IMPORTANT: DO NOT MOVE!
def test_nested_lineno_return_loc():
    def test_nested():
        f = sys._getframe(0)
        if True:
            return f
        return None

    f = test_nested()
    assert f.f_lineno == 63

# IMPORTANT: DO NOT MOVE!
def test_nested_lineno_implicit_return():
    f = None
    def test_nested():
        nonlocal f
        f = sys._getframe(0)
        dummy = 42

    test_nested()
    assert f.f_lineno == 75

# IMPORTANT: DO NOT MOVE!
def test_nested_lineno_finally():
    def test_nested():
        try:
            return sys._getframe(0)
        finally:
            dummy = 42

    f = test_nested()
    assert f.f_lineno == 86, f.f_lineno

# IMPORTANT: DO NOT MOVE!
def test_nested_lineno_multiline_return():
    def test_nested():
        f = sys._getframe(0)
        if f:
            return (
                f)
        return None

    f = test_nested()
    assert f.f_lineno == 96

def test_read_and_write_locals():
    a = 1
    b = ''
    ls = sys._getframe(0).f_locals
    assert ls['a'] == 1
    assert ls['b'] == ''
    ls['a'] = sys
    assert ls['a'] == sys


def test_backref():
    a = 'test_backref'

    def foo():
        a = 'foo'
        return sys._getframe(0).f_back

    assert foo().f_locals['a'] == 'test_backref'

    def get_frame():
        return sys._getframe(0)

    def get_frame_caller():
        return get_frame()

    def do_stackwalk(f):
        stack = []
        while f:
            stack.append(f)
            f = f.f_back
        return stack

    stack = do_stackwalk(get_frame_caller())
    actual_fnames = [n.f_code.co_name for n in stack]
    expected_prefix = ['get_frame', 'get_frame_caller', 'test_backref']
    assert len(stack) >= len(expected_prefix)
    assert expected_prefix == actual_fnames[:len(expected_prefix)]


def test_backref_recursive():
    def get_frame():
        return sys._getframe(0)

    def foo(i):
        if i == 1:
            f = get_frame()
            stack = []
            while f:
                stack.append(f)
                f = f.f_back
            return stack
        else:
            # This recursive call will cause
            return foo(i + 1)

    def bar():
        return foo(0)

    s = bar()
    print([n.f_code for n in s])


def test_code():
    code = sys._getframe().f_code
    assert code.co_filename == test_code.__code__.co_filename
    assert code.co_firstlineno == test_code.__code__.co_firstlineno
    assert code.co_name == test_code.__code__.co_name


def test_builtins():
    assert print == sys._getframe().f_builtins["print"]


def test_locals_sync():
    a = 1
    l = locals()
    assert l == {'a': 1}
    b = 2
    # Forces caller frame materialization, this used to erroneously cause the locals dict to update
    globals()
    assert l == {'a': 1}
    # Now this should really cause the locals dict to update
    locals()
    assert l == {'a': 1, 'b': 2, 'l': l}


def test_locals_cells():
    x = 1

    def foo():
        return x, locals()

    assert foo()[1]['x'] == 1

    cell = foo.__closure__[0]

    assert type(locals()['cell']).__name__ == 'cell'


def test_locals_freevar_in_class():
    x = 1

    class Foo:
        c = x
        assert 'c' in locals()
        assert 'x' not in locals()

# GR-22089
# def test_backref_from_traceback():
#     def bar():
#         raise RuntimeError
#
#     def foo():
#         bar()
#
#     try:
#         foo()
#     except Exception as e:
#         assert e.__traceback__.tb_frame.f_back.f_code == sys._getframe(0).f_back.f_code
#         assert e.__traceback__.tb_next.tb_next.tb_frame.f_back.f_code == foo.__code__
#         assert e.__traceback__.tb_next.tb_frame.f_back.f_code == test_backref_from_traceback.__code__
