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
import os


def a_function():
    pass


def wrapper():
    values = []
    global a_global

    a_global = set([11, 12])

    def my_func(arg_l, kwarg_case="empty set", kwarg_other=19):
        loc_1 = set(values)
        loc_2 = set(values)
        loc_3 = "set()"

        def inner_func():
            return kwarg_other + loc_2

        try:
            loc_1 &= kwarg_other
            yield loc_1
        except TypeError:
            pass
        else:
            print("expected TypeError")

    return my_func


a_global = 10


def test_name():
    assert a_function.__code__.co_name == "a_function"


def test_filename():
    assert a_function.__code__.co_filename.rpartition(os.sep)[2] == "test_code.py"


def test_firstlineno():
    assert a_function.__code__.co_firstlineno == 42


def test_code_attributes():
    code = wrapper().__code__
    assert code.co_argcount == 3
    assert code.co_kwonlyargcount == 0
    assert code.co_nlocals == 6
    assert code.co_flags & (1 << 5)
    assert not code.co_flags & (1 << 2)
    assert not code.co_flags & (1 << 3)
    assert {'set()', 'expected TypeError'}.issubset(code.co_consts)
    assert set(code.co_varnames) == {'arg_l', 'kwarg_case', 'kwarg_other', 'loc_1', 'loc_3', 'inner_func'}
    assert code.co_filename.endswith("test_code.py")
    assert code.co_name == "my_func"
    assert code.co_firstlineno == 52
    assert set(code.co_freevars) == {'values'}
    assert set(code.co_cellvars) == {'kwarg_other', 'loc_2'}


def test_code_copy():
    import types

    code = wrapper().__code__
    code2 = types.CodeType(
        code.co_argcount,
        code.co_posonlyargcount,
        code.co_kwonlyargcount,
        code.co_nlocals,
        code.co_stacksize,
        code.co_flags,
        code.co_code,
        code.co_consts,
        code.co_names,
        code.co_varnames,
        code.co_filename,
        code.co_name,
        code.co_qualname,
        code.co_firstlineno,
        code.co_linetable,
        code.co_exceptiontable,
        code.co_freevars,
        code.co_cellvars)


    assert code.co_argcount == code2.co_argcount
    assert code.co_kwonlyargcount == code2.co_kwonlyargcount
    assert code.co_nlocals == code2.co_nlocals
    assert code.co_stacksize == code2.co_stacksize
    assert code.co_flags == code2.co_flags
    assert code.co_code == code2.co_code
    assert code.co_consts == code2.co_consts
    assert set(code.co_names) == set(code2.co_names)
    assert set(code.co_varnames) == set(code2.co_varnames)
    assert code.co_filename == code2.co_filename
    assert code.co_name == code2.co_name
    assert code.co_qualname == code2.co_qualname
    assert code.co_firstlineno == code2.co_firstlineno
    assert code.co_linetable == code2.co_linetable
    assert code.co_exceptiontable == code2.co_exceptiontable
    assert set(code.co_freevars) == set(code2.co_freevars)
    assert set(code.co_cellvars) == set(code2.co_cellvars)


def test_module_code():
    import sys, os
    sys.path.insert(0, os.path.dirname(__file__))
    try:
        m = __import__('package.moduleA')
        with open(m.__file__, 'r') as MODULE:
            source = MODULE.read()
            code = compile(source, m.__file__, 'exec')
            assert code.co_argcount == 0
            assert code.co_kwonlyargcount == 0
            assert code.co_nlocals == 0
            assert {'PACKAGE DOC', 'after importing moduleY'}.issubset(set(code.co_consts))
            assert set(code.co_varnames) == set()
            assert code.co_filename.endswith("__init__.py")
            assert code.co_name.startswith("<module")
            assert code.co_firstlineno == 1
            assert code.co_freevars == tuple()
            assert code.co_cellvars == tuple()
    finally:
        del sys.path[0]


def test_function_code_consts():
    codestr = """
"module doc"
a = 1
def fn():
    "fn doc"
    def inner():
        return "this is fun"
    return inner()
"""
    import types

    code = compile(codestr, "<test>", "exec")
    assert "module doc" in code.co_consts
    assert 1 in code.co_consts
    assert "fn doc" not in code.co_consts
    for const in code.co_consts:
        if type(const) == types.CodeType:
            code = const
    assert "fn doc" in code.co_consts
    assert "this is fun" not in code.co_consts
    for const in code.co_consts:
        if type(const) == types.CodeType:
            code = const
    assert "this is fun" in code.co_consts


def test_generator_code_consts():
    codestr = """
"module doc"
def gen():
    "gen doc"
    def inner():
        return "this is fun"
    yield inner()
"""
    import types

    code = compile(codestr, "<test>", "exec")
    assert "module doc" in code.co_consts
    assert "gen doc" not in code.co_consts
    for const in code.co_consts:
        if type(const) == types.CodeType:
            code = const
    assert "gen doc" in code.co_consts
    assert "this is fun" not in code.co_consts
    for const in code.co_consts:
        if type(const) == types.CodeType:
            code = const
    assert "this is fun" in code.co_consts


def test_consts_do_not_leak_java_types():
    codestr = "['root']"
    code = compile(codestr, '<test>', 'exec')
    for const in code.co_consts:
        assert isinstance(const, (str, tuple)) or const is None


def test_generator_and_gen_body_code_are_equal():
    def g():
        yield 42

    x = g()
    assert g.__code__ is x.gi_code
