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
