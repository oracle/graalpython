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


def test_lambdas_as_function_default_argument_values():
    globs = {}
    exec("""
def x(aaaa, bbbb=None, cccc={}, dddd=lambda name: '*'+name):
    return aaaa, bbbb, cccc, dddd(aaaa)

retval = x('hello')
    """, globs)
    assert globs["retval"] == ('hello', None, {}, '*hello')


def test_required_kw_arg():
    globs = {}
    exec("""
def x(*, a=None, b):
    return a, b

try:
    x()
except TypeError:
    assert True
else:
    assert False

retval = x(b=42)
    """, globs)
    assert globs["retval"] == (None, 42)


def test_syntax_error_simple():
    globs = {}
    was_exception = False
    try:
        exec("""c = a += 3""", globs)
    except SyntaxError:
        was_exception = True
    assert was_exception


def test_lambda_no_args_with_nested_lambdas():
    no_err = True
    try:
        eval("lambda: ((lambda args: args[0], ), (lambda args: args[1], ), )")
    except Exception as e:
        no_err = False
    assert no_err


def test_byte_numeric_escapes():
    assert eval('b"PK\\005\\006\\00\\11\\22\\08"') == b'PK\x05\x06\x00\t\x12\x008'


def test_decorator_cell():
    foo = lambda x: "just a string, not %s" % x.__name__
    def run_me():
        @foo
        def func():
            pass
        return func
    assert run_me() == "just a string, not func", run_me()


def test_single_input_non_interactive():
    import sys
    oldhook = sys.displayhook
    got_value = None
    def newhook(value):
        nonlocal got_value
        got_value = value
    sys.displayhook = newhook
    try:
        code = compile('sum([1, 2, 3])', '', 'single')
        assert exec(code) == None
        assert got_value == 6
    finally:
        sys.displayhook = oldhook


def test_underscore_in_numbers():
    assert eval('1_0') == 10
    assert eval('0b1_1') == 0b11
    assert eval('0o1_7') == 0o17
    assert eval('0x1_f') == 0x1f


def test_annotation_scope():
    def foo(object: object):
        pass
    assert foo.__annotations__['object'] == object


import sys

def assert_raise_syntax_error(source, msg):
    try:
        compile(source, "", "single")
    except SyntaxError as e:
        assert msg in str(e), "\nCode:\n----\n%s\n----\n  Expected message: %s\n  Actual message: %s" % (source, msg, str(e))
    else:
        assert False , "Syntax Error was not raised.\nCode:\n----\n%s\n----\nhas to raise Syntax Error: %s" % (source,msg) 

def test_cannot_assign():
    if sys.implementation.version.minor >= 8:
        def check(s, msg):
            # test simple assignment
            assert_raise_syntax_error("%s = 1" % s, msg)
            # testing aug assignment
            assert_raise_syntax_error("%s += 1" % s, msg)
            # test with statement
            assert_raise_syntax_error("with foo as %s:\n pass" % s, msg)
            # test for statement
            assert_raise_syntax_error("for %s in range(1,10):\n pass" % s, msg)
            # test for comprehension statement
            assert_raise_syntax_error("[1 for %s in range(1,10)]" % s, msg)
        check("1", "cannot assign to literal")
        check("1.1", "cannot assign to literal")
        check("{1}", "cannot assign to set display")
        check("{}", "cannot assign to dict display")
        check("{1: 2}", "cannot assign to dict display")
        check("[1,2, 3]", "cannot assign to literal")
        check("(1,2, 3)", "cannot assign to literal")
        check("1.2j", "cannot assign to literal")
        check("None", "cannot assign to None")
        check("...", "cannot assign to Ellipsis")
        check("True", "cannot assign to True")
        check("False", "cannot assign to False")
        check("b''", "cannot assign to literal")
        check("''", "cannot assign to literal")
        check("f''", "cannot assign to f-string expression")
        check("(a,None, b)", "cannot assign to None")
        check("(a,True, b)", "cannot assign to True")
        check("(a,False, b)", "cannot assign to False")
        check("a+b", "cannot assign to operator")
        check("fn()", "cannot assign to function call")
        check("{letter for letter in 'ahoj'}", "cannot assign to set comprehension")
        check("[letter for letter in 'ahoj']", "cannot assign to list comprehension")
        check("(letter for letter in 'ahoj')", "cannot assign to generator expression")
        check("obj.True", "invalid syntax")
        check("(a, *True, b)", "cannot assign to True")
        check("(a, *False, b)", "cannot assign to False")
        check("(a, *None, b)", "cannot assign to None")
        check("(a, *..., b)", "cannot assign to Ellipsis")
        check("__debug__", "cannot assign to __debug__")
        check("a.__debug__", "cannot assign to __debug__")
        check("a.b.__debug__", "cannot assign to __debug__")

def test_cannot_assign_without_with():
    if sys.implementation.version.minor >= 8:
        def check(s, msg):
            # test simple assignment
            assert_raise_syntax_error("%s = 1" % s, msg)
            # testing aug assignment
            assert_raise_syntax_error("%s += 1" % s, msg)
            # test for statement
            assert_raise_syntax_error("for %s in range(1,10):\n pass" % s, msg)
            # test for comprehension statement
            assert_raise_syntax_error("[1 for %s in range(1,10)]" % s, msg)
        check("*True", "cannot assign to True")
        check("*False", "cannot assign to False")
        check("*None", "cannot assign to None")
        check("*...", "cannot assign to Ellipsis")
        check("[a, b, c + 1],", "cannot assign to operator")

def test_cannot_assign_other():
    if sys.implementation.version.minor >= 8:
        assert_raise_syntax_error("a if 1 else b = 1", "cannot assign to conditional expression")
        assert_raise_syntax_error("a if 1 else b -= 1", "cannot assign to conditional expression")
        assert_raise_syntax_error("f(True=2)", "cannot assign to True")
        assert_raise_syntax_error("f(__debug__=2)", "cannot assign to __debug__")
        assert_raise_syntax_error("def f(__debug__): pass\n", "cannot assign to __debug__")
        assert_raise_syntax_error("def f(*, x=lambda __debug__:0): pass\n", "cannot assign to __debug__")
        assert_raise_syntax_error("def f(*args:(lambda __debug__:0)): pass\n", "cannot assign to __debug__")
        assert_raise_syntax_error("def f(**kwargs:(lambda __debug__:0)): pass\n", "cannot assign to __debug__")
        assert_raise_syntax_error("def f(**__debug__): pass\n", "cannot assign to __debug__")
        assert_raise_syntax_error("def f(*xx, __debug__): pass\n", "cannot assign to __debug__")

def test_invalid_assignmetn_to_yield_expression():
    if sys.implementation.version.minor >= 8:
        assert_raise_syntax_error("def fn():\n (yield 10) = 1", "cannot assign to yield expression")
        assert_raise_syntax_error("def fn():\n (yield 10) += 1", "cannot assign to yield expression")
        assert_raise_syntax_error("def fn():\n with foo as (yield 10) : pass", "cannot assign to yield expression")
        assert_raise_syntax_error("def fn():\n for (yield 10) in range(1,10): pass", "cannot assign to yield expression") 

def test_invalid_ann_assignment():
    if sys.implementation.version.minor >= 8:
        assert_raise_syntax_error("lambda: x:x", 'illegal target for annotation')
        assert_raise_syntax_error("list(): int", "illegal target for annotation")
        assert_raise_syntax_error("{}: int", "illegal target for annotation")
        assert_raise_syntax_error("{1,2}: int", "illegal target for annotation")
        assert_raise_syntax_error("{'1':'2'}: int", "illegal target for annotation")
        assert_raise_syntax_error("[1,2]: int", "only single target (not list) can be annotated")
        assert_raise_syntax_error("(1,2): int", "only single target (not tuple) can be annotated")

def test_invalid_aug_assignment():
    if sys.implementation.version.minor >= 8:
        assert_raise_syntax_error("[] += 1", 'illegal expression for augmented assignment')
        assert_raise_syntax_error("() += 1", 'illegal expression for augmented assignment')
        assert_raise_syntax_error("x, y += (1, 2)", 'illegal expression for augmented assignment')
        assert_raise_syntax_error("x, y += 1", 'illegal expression for augmented assignment')

def test_invalid_star_import():
    assert_raise_syntax_error("def f(): from bla import *\n", 'import * only allowed at module level')

def test_invalid_return_statement():
    assert_raise_syntax_error("return 10", "'return' outside function")
    assert_raise_syntax_error("class A: return 10\n", "'return' outside function")


