# Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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


import types

def test_lambdas_as_function_default_argument_values():
    globs = {}
    exec("""
def x(aaaa, bbbb=None, cccc={}, dddd=lambda name: '*'+name):
    return aaaa, bbbb, cccc, dddd(aaaa)

retval = x('hello')
    """, globs)
    assert globs["retval"] == ('hello', None, {}, '*hello')


def test_codepoints_in_comment():
    # these comments are part of the test, also don't delete new line between the comments
    # the test do not fail, but the file can not be parse without proper fix.
    # assert a == '𝒜' and b == '𝒞' and c == '𝒵

    # assert a == '𝒜' and b == '𝒞' and c == '𝒵

    # another comment
    pass

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
        def check(s, label1, label2=None):
            if label1:
                msg1 = f'cannot assign to {label1}'
                msg2 = f"'{label2 or label1}' is an illegal expression for augmented assignment"
            else:
                msg1 = msg2 = "invalid syntax"
            # test simple assignment
            assert_raise_syntax_error("%s = 1" % s, msg1)
            # testing aug assignment
            if label1 != '__debug__':
                assert_raise_syntax_error("%s += 1" % s, msg2)
            # test with statement
            assert_raise_syntax_error("with foo as %s:\n pass" % s, msg1)
            # test for statement
            assert_raise_syntax_error("for %s in range(1,10):\n pass" % s, msg1)
            # test for comprehension statement
            assert_raise_syntax_error("[1 for %s in range(1,10)]" % s, msg1)
        check("1", "literal")
        check("1.1", "literal")
        check("{1}", "set display")
        check("{}", "dict literal")
        check("{1: 2}", "dict literal")
        check("[1,2, 3]", "literal", "list")
        check("(1,2, 3)", "literal", "tuple")
        check("1.2j", "literal")
        check("None", "None")
        check("...", "ellipsis")
        check("True", "True")
        check("False", "False")
        check("b''", "literal")
        check("''", "literal")
        check("f''", "f-string expression")
        check("(a,None, b)", "None", "tuple")
        check("(a,True, b)", "True", "tuple")
        check("(a,False, b)", "False", "tuple")
        check("a+b", "expression")
        check("fn()", "function call")
        check("{letter for letter in 'ahoj'}", "set comprehension")
        check("[letter for letter in 'ahoj']", "list comprehension")
        check("(letter for letter in 'ahoj')", "generator expression")
        check("obj.True", None)
        check("(a, *True, b)", "True", "tuple")
        check("(a, *False, b)", "False", "tuple")
        check("(a, *None, b)", "None", "tuple")
        check("(a, *..., b)", "ellipsis", "tuple")
        check("__debug__", "__debug__")
        check("a.__debug__", "__debug__")
        check("a.b.__debug__", "__debug__")

def test_cannot_assign_without_with():
    if sys.implementation.version.minor >= 8:
        def check(s, label1, label2=None):
            if label1:
                msg1 = f'cannot assign to {label1}'
                msg2 = f"'{label2 or label1}' is an illegal expression for augmented assignment"
            else:
                msg1 = msg2 = "invalid syntax"
            # test simple assignment
            assert_raise_syntax_error("%s = 1" % s, msg1)
            # testing aug assignment
            assert_raise_syntax_error("%s += 1" % s, msg2)
            # test for statement
            assert_raise_syntax_error("for %s in range(1,10):\n pass" % s, msg1)
            # test for comprehension statement
            assert_raise_syntax_error("[1 for %s in range(1,10)]" % s, msg1)
        check("*True", "True", "starred")
        check("*False", "False", "starred")
        check("*None", "None", "starred")
        check("*...", "ellipsis", "starred")
        check("[a, b, c + 1],", "expression", "tuple")

def test_cannot_assign_other():
    if sys.implementation.version.minor >= 8:
        assert_raise_syntax_error("a if 1 else b = 1", "cannot assign to conditional expression")
        assert_raise_syntax_error("a if 1 else b -= 1", "'conditional expression' is an illegal expression for augmented assignment")
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
        assert_raise_syntax_error("def fn():\n (yield 10) += 1", "'yield expression' is an illegal expression for augmented assignment")
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


def test_mangled_class_property():
    class P:
        __property = 1
        def get_property(self):
            return self.__property
        def get_mangled_property(self):
            return self._P__property

    p = P()
    assert P._P__property == 1
    assert "_P__property" in dir(P)
    assert "__property" not in dir(P)
    assert p._P__property == 1
    assert "_P__property" in dir(p)
    assert "__property" not in dir(p)
    assert p.get_property() == 1
    assert p.get_mangled_property() == 1

    try:
        print(P.__property)
    except AttributeError as ae:
        pass
    else:
        assert False, "AttributeError was not raised"

    try:
        print(p.__property)
    except AttributeError as ae:
        pass
    else:
        assert False, "AttributeError was not raised"

    class B:
        __ = 2

    b = B()
    assert B.__ == 2
    assert "__" in dir(B)
    assert b.__ == 2
    assert "__" in dir(b)

    class C:
        ___ = 3

    c = C()
    assert C.___ == 3
    assert "___" in dir(C)
    assert c.___ == 3
    assert "___" in dir(c)

    class D:
        _____ = 4

    d = D()
    assert D._____ == 4
    assert "_____" in dir(D)
    assert d._____ == 4
    assert "_____" in dir(d)

    class E:
        def __init__(self, value):
            self.__value = value
        def getValue(self):
            return self.__value

    e = E(5)
    assert e.getValue() == 5
    assert e._E__value == 5
    assert "_E__value" in dir(e)
    assert "__value" not in dir(e)

    class F:
        def __init__(self, value):
            self.__a = value
        def get(self):
            return self.__a

    f = F(5)
    assert "_F__a" in dir(f)
    assert "__a" not in dir(f)

def test_underscore_class_name():
    class _:
        __a = 1

    assert _.__a == 1
    assert "__a" in dir(_)


def test_mangled_class_method():
    class A:
        ___sound = "hello"
        def __hello(self):
            return self.___sound
        def hello(self):
            return self.__hello()

    a = A()
    assert "_A__hello" in dir(A)
    assert "__hello" not in dir(A)
    assert a.hello() == 'hello'

    try:
        print(A.__hello)
    except AttributeError as ae:
        pass
    else:
        assert False, "AttributeError was not raised"

    try:
        print(a.__hello)
    except AttributeError as ae:
        pass
    else:
        assert False, "AttributeError was not raised"

def test_mangled_private_class():
    class __P:
        __property = 1

    p = __P()
    assert __P._P__property == 1
    assert "_P__property" in dir(__P)
    assert "__property" not in dir(__P)
    assert p._P__property == 1
    assert "_P__property" in dir(p)
    assert "__property" not in dir(p)

    class __:
        __property = 2

    p = __()
    assert __.__property == 2
    assert "__property" in dir(__)
    assert p.__property == 2
    assert "__property" in dir(p)


    class _____Testik__:
        __property = 3

    p = _____Testik__()
    assert _____Testik__._Testik____property == 3
    assert "_Testik____property" in dir(_____Testik__)
    assert "__property" not in dir(_____Testik__)
    assert p._Testik____property == 3
    assert "_Testik____property" in dir(p)
    assert "__property" not in dir(p)

def test_mangled_import():
    class X:
        def fn(self):
            import __mangled_module

    assert '_X__mangled_module' in X.fn.__code__.co_varnames
    assert '__mangled_module' not in X.fn.__code__.co_varnames


def test_mangled_params():
    def xf(__param):
        return __param
    assert '__param' in xf.__code__.co_varnames
    assert xf(10) == 10

    class X:
        def m1(self, __param):
            return __param
        def m2(self, *__arg):
            return __arg
        def m3(self, **__kw):
            return __kw


    assert '_X__param' in X.m1.__code__.co_varnames
    assert '__param' not in X.m1.__code__.co_varnames
    assert X().m1(11) == 11

    assert '_X__arg' in X.m2.__code__.co_varnames
    assert '__arg' not in X.m2.__code__.co_varnames
    assert X().m2(1, 2, 3) == (1,2,3)

    assert '_X__kw' in X.m3.__code__.co_varnames
    assert '__kw' not in X.m3.__code__.co_varnames
    assert X().m3(a = 1, b = 2) == {'a':1, 'b':2}

def test_mangled_local_vars():
    class L:
        def fn(self, i):
            __result = 0
            for __index in range (1, i + 1):
                __result += __index
            return __result

    assert '_L__result' in L.fn.__code__.co_varnames
    assert '__result' not in L.fn.__code__.co_varnames
    assert '_L__index' in L.fn.__code__.co_varnames
    assert '__index' not in L.fn.__code__.co_varnames
    assert L().fn(5) == 15


def test_mangled_inner_function():
    def find_code_object(code_object, name):
        import types
        for object in code_object.co_consts:
            if type(object) == types.CodeType and object.co_name == name:
                return object

    class L:
        def fn(self, i):
            def __help(__i):
                __result = 0
                for __index in range (1, __i + 1):
                    __result += __index
                return __result
            return __help(i)

    assert '_L__help' in L.fn.__code__.co_varnames
    assert '__help' not in L.fn.__code__.co_varnames

    # CPython has stored as name of the code object the non mangle name. The question is, if this is right.
    co = find_code_object(L.fn.__code__, '__help')
    if co is None:
        co = find_code_object(L.fn.__code__, '_L__help')
    assert co is not None
    assert '_L__result' in co.co_varnames
    assert '__result' not in co.co_varnames
    assert '_L__index' in co.co_varnames
    assert '__index' not in co.co_varnames
    assert L().fn(5) == 15


def test_mangled_default_value_param():
    class D:
        def fn(self, __default = 5):
            return __default

    assert D().fn(_D__default = 11) == 11

    try:
        D().fn(__default = 11)
    except TypeError as ae:
        pass
    else:
        assert False, "TypeError was not raised"


def test_mangled_slots():
    class SlotClass:
        __slots__ = ("__mangle_me", "do_not_mangle_me")
        def __init__(self):
            self.__mangle_me = 123
            self.do_not_mangle_me = 456

    b = SlotClass()
    assert b._SlotClass__mangle_me == 123
    assert b.do_not_mangle_me == 456


def test_method_decorator():
    class S:
        def __init__(self, value):
            self.value = value
        @property
        def __ser(self):
            return self.value
        @__ser.setter
        def __ser(self, value):
            self.value = value

    s = S(10)
    assert s.value == 10
    assert s._S__ser == 10
    assert '_S__ser' in dir(S)
    assert '_S__ser' in dir(s)
    assert '__ser' not in dir(S)
    assert '__ser' not in dir(s)

    s.value = 11
    assert s._S__ser == 11

    s._S__ser = 12
    assert s.value == 12

    s.__ser = 13
    assert s.value == 12
    assert s.__ser == 13
    assert s._S__ser == 12

def test_fstring_function_overwrite():

    format = "hello from other side"
    assert f'text: {format}' == 'text: hello from other side'

    ascii = "hello from ascii"
    assert f'text: {ascii!a}' == "text: 'hello from ascii'"

    repr = "hello from repr"
    assert f'text: {repr!r}' == "text: 'hello from repr'"

    str = "hello from str"
    assert f'text: {str!s}' == "text: hello from str"

def test_fstring_class_overwrite():

    class A():
        def __format__(self, spec):
             return "__format__ from A " + spec
        def __str__(self):
             return "__str__ from A"
        def __repr__(self):
             return "__repr__ from A"
        def ascii(self):
             return "ascii from A"

    a = A()
    assert f'{a}' == '__format__ from A '
    assert f'{a:123}' == '__format__ from A 123'
    assert f'{a!s}' == '__str__ from A'
    assert f'{a!r}' == '__repr__ from A'
    assert f'{a!a}' == '__repr__ from A'
    assert f'{a.ascii()!a}' == "'ascii from A'"
    assert f'{a.ascii()!s}' == "ascii from A"
    assert f'{a.ascii()!r}' == "'ascii from A'"
    assert f'{a.ascii()}' == "ascii from A"

def test_fstring_basic():

    assert f'' == ''
    assert f'{{}}' == '{}'
    assert f'{{name}}' == '{name}'
    assert f'Hi {{name}}' == "Hi {name}"
    assert f'{{name}} first' == "{name} first"
    assert f'Hi {{name}} first' == "Hi {name} first"
    assert f'{{' == "{"
    assert f'a{{' == "a{"
    assert f'{{b' == "{b"
    assert f'a{{b' == "a{b"
    assert f'}}' == "}"
    assert f'a}}' == "a}"
    assert f'}}b' == "}b"
    assert f'a}}b' == "a}b"
    assert f'a{{}}' == "a{}"

def test_optimize():
    codestr = '''
"""There is module doc"""

assert "assert in module"

class MyClass():
  """There is class doc"""
  assert "assert in class"
  def method1(self):
    """There is method doc"""
    assert "assert in method"

def fn():
  """There is function doc"""
  assert "assert in function"

def gen():
  """There is generator doc"""
  assert "assert in generator"""
  yield 10
  yield 20
'''

    def check(code, optimize):
        def check_assert(code, value):
            if optimize < 1:
                assert value in code.co_consts, "'{}' is not in constants: {} (optimize={})".format(value, code.co_consts, optimize)
            else:
                assert value not in code.co_consts, "'{}' is in constants: {} (optimize={})".format(value, code.co_consts, optimize)

        def check_doc(code, doc):
            if optimize < 2:
                assert doc in code.co_consts, "'{}' is not in constants: {} (optimize={})".format(doc, code.co_consts, optimize)
            else:
                assert doc not in code.co_consts, "'{}' is in constants: {} (optimize={})".format(doc, code.co_consts, optimize)

        check_doc(code, 'There is module doc')
        check_assert(code, 'assert in module')
        for code2 in code.co_consts:
            if type(code2) == types.CodeType:
                if code2.co_name == 'MyClass':
                    check_doc(code2, 'There is class doc')
                    check_assert(code2, 'assert in class')
                    for code3 in code2.co_consts:
                        if type(code3) == types.CodeType:
                            check_doc(code3, 'There is method doc')
                            check_assert(code3, 'assert in method')
                if code2.co_name == 'fn':
                    check_doc(code2, 'There is function doc')
                    check_assert(code2, 'assert in function')
                if code2.co_name == 'gen':
                    check_doc(code2, 'There is generator doc')
                    check_assert(code2, 'assert in generator')

    # no optimization, default level
    code = compile(codestr, "<test>", "exec")
    check(code, 0)
    # no optimization, level -1
    code = compile(codestr, "<test-1>", "exec", optimize=-1)
    check(code, -1)
    # no optimization, level 0
    code = compile(codestr, "<test0>", "exec", optimize=0)
    check(code, 0)
    # optimization, level 1 -> skip asserts
    code = compile(codestr, "<test1>", "exec", optimize=1)
    check(code, 1)
    # optimization, level 2 -> skip asserts and documentations
    codestr2 = codestr + ' ';
    code2 = compile(codestr2, "<test2>", "exec", optimize=2)
    check(code2, 2)

def test_optimize_doc():

    codestr = '''
def fn():
  "Function Documentation"
'''
    code = compile(codestr, "<test>", "exec")
    for const in code.co_consts:
        if type(const) == types.CodeType:
            code1 = const
    code = compile(codestr, "<testOptimize>", "exec", optimize=2)
    for const in code.co_consts:
        if type(const) == types.CodeType:
            code2 = const
    assert "Function Documentation" in code1.co_consts
    assert "Function Documentation" not in code2.co_consts
    assert exec(code1) == exec(code2)

def test_annotations_in_global():
    test_globals = {'__annotations__': {}}
    code = compile ("a:int", "<test>", "exec")
    exec(code,test_globals)
    assert test_globals['__annotations__']['a'] == int

    test_globals = {'__annotations__': {}}
    code = compile ("a:22", "<test>", "exec")
    exec(code,test_globals)
    assert test_globals['__annotations__']['a'] == 22

    test_globals = {'__annotations__': {}}
    code = compile ("a:'hello'", "<test>", "exec")
    exec(code,test_globals)
    assert test_globals['__annotations__']['a'] == 'hello'

    test_globals = {'__annotations__': {}}
    code = compile ("a:'hello'; a+1", "<test>", "exec")
    try:
        exec(code,test_globals)
    except NameError:
        pass
    else:
        assert False, 'NameError was not raised'
    assert len(test_globals['__annotations__']) == 1
    assert test_globals['__annotations__']['a'] == 'hello'

    test_globals = {'__annotations__': {}}
    code = compile ("a: int = 1; a+1", "<test>", "exec")
    exec(code,test_globals)
    assert len(test_globals['__annotations__']) == 1
    assert test_globals['__annotations__']['a'] == int
    assert test_globals['a'] == 1

def test_annotations_in_function_declaration():
    def fn1(a:int, b: 5+6): pass
    assert len(fn1.__annotations__) == 2
    assert fn1.__annotations__['a'] == int
    assert fn1.__annotations__['b'] == 11

    def fn2(a: list, b: "Hello") -> int: pass
    assert len(fn2.__annotations__) == 3
    assert fn2.__annotations__['a'] == list
    assert fn2.__annotations__['b'] == "Hello"
    assert fn2.__annotations__['return'] == int

    def fn3() -> sum((1,2,3,4)): pass
    assert len(fn3.__annotations__) == 1
    assert fn3.__annotations__['return'] == 10

    def fn4() -> f'hello {1+4}': pass
    assert len(fn4.__annotations__) == 1
    assert fn4.__annotations__['return'] == 'hello 5'

    x = "Superman"
    def fn5() -> f'hello {x}': pass
    assert len(fn5.__annotations__) == 1
    assert fn5.__annotations__['return'] == 'hello Superman'


def test_annotations_in_function():
    test_globals = {'__annotations__': {}}
    source = '''def fn():
        a:1
        '''
    code = compile (source, "<test>", "exec")
    exec(code,test_globals)
    assert len(test_globals['__annotations__']) == 0
    assert len(test_globals['fn'].__annotations__) == 0
    assert 1 not in test_globals['fn'].__code__.co_consts   # the annotation is ignored in function

    source = '''def fn():
        a:int =1
        '''
    code = compile (source, "<test>", "exec")
    exec(code,test_globals)
    assert len(test_globals['__annotations__']) == 0
    assert hasattr(test_globals['fn'], '__annotations__')
    assert len(test_globals['fn'].__annotations__) == 0
    assert 1 in test_globals['fn'].__code__.co_consts

def test_annotations_in_class():

    test_globals = {'__annotations__': {}}
    source = '''class Bif:
        pass
        '''
    code = compile (source, "<test>", "exec")
    exec(code, test_globals)
    assert hasattr(test_globals['Bif'], '__annotations__')
    assert len(test_globals['Bif'].__annotations__) == 0

    test_globals = {'__annotations__': {}}
    source = '''class Baf:
        a:int
        '''
    code = compile (source, "<test>", "exec")
    exec(code,test_globals)
    assert len(test_globals['__annotations__']) == 0
    assert hasattr(test_globals['Baf'], '__annotations__')
    assert len(test_globals['Baf'].__annotations__) == 1
    assert test_globals['Baf'].__annotations__['a'] == int
    assert 'a' not in dir(test_globals['Baf'])

    test_globals = {'__annotations__': {}}
    source = '''class Buf:
        aa:int = 1
        '''
    code = compile (source, "<test>", "exec")
    exec(code,test_globals)
    assert len(test_globals['__annotations__']) == 0
    assert len(test_globals['Buf'].__annotations__) == 1
    assert test_globals['Buf'].__annotations__['aa'] == int
    assert 'aa' in dir(test_globals['Buf'])

    test_globals = {'__annotations__': {}}
    source = '''class Buf:
        aa:int = 1
        '''
    code = compile (source, "<test>", "exec")
    exec(code,test_globals)
    assert len(test_globals['__annotations__']) == 0
    assert len(test_globals['Buf'].__annotations__) == 1
    assert test_globals['Buf'].__annotations__['aa'] == int
    assert 'aa' in dir(test_globals['Buf'])

    # git issue #188
    test_globals = {'__annotations__': {}}
    source = '''class Style:
        _path: str
        __slots__ = ["_path"]
        '''
    code = compile (source, "<test>", "exec")
    exec(code,test_globals)
    assert len(test_globals['__annotations__']) == 0
    assert len(test_globals['Style'].__annotations__) == 1
    assert test_globals['Style'].__annotations__['_path'] == str
    assert '_path' in dir(test_globals['Style'])

def test_negative_float():

    def check_const(fn, expected):
        for const in fn.__code__.co_consts:
            if repr(const) == repr(expected):
                return True
        else:
            return False

    def fn1():
        return -0.0

    assert check_const(fn1, -0.0)


def find_count_in(collection, what):
    count = 0;
    for item in collection:
        if item == what:
            count +=1
    return count

def test_same_consts():
    def fn1(): a = 1; b = 1; return a + b
    assert find_count_in(fn1.__code__.co_consts, 1) == 1

    def fn2(): a = 'a'; b = 'a'; return a + b
    assert find_count_in(fn2.__code__.co_consts, 'a') == 1

def test_tuple_in_const():
    def fn1() : return (0,)
    assert (0,) in fn1.__code__.co_consts
    assert 0 not in fn1.__code__.co_consts

    def fn2() : return (1, 2, 3, 1, 2, 3)
    assert (1, 2, 3, 1, 2, 3) in fn2.__code__.co_consts
    assert 1 not in fn2.__code__.co_consts
    assert 2 not in fn2.__code__.co_consts
    assert 3 not in fn2.__code__.co_consts
    assert find_count_in(fn2.__code__.co_consts, (1, 2, 3, 1, 2, 3)) == 1

    def fn3() : a = 1; return (1, 2, 1)
    assert (1, 2, 1) in fn3.__code__.co_consts
    assert find_count_in(fn3.__code__.co_consts, 1) == 1
    assert 2 not in fn3.__code__.co_consts

    def fn4() : a = 1; b = (1,2,3); c = 4; return (1, 2, 3, 1, 2, 3)
    assert (1, 2, 3) in fn4.__code__.co_consts
    assert (1, 2, 3, 1, 2, 3) in fn4.__code__.co_consts
    assert 2 not in fn4.__code__.co_consts
    assert find_count_in(fn4.__code__.co_consts, 1) == 1
    assert find_count_in(fn4.__code__.co_consts, 4) == 1

def test_ComprehensionGeneratorExpr():
    def create_list(gen):
        result = []
        for i in gen:
            result.append(i)
        return result

    gen = (i for i in range(3))
    assert [0,1,2] == create_list(gen)
    gen = (e+1 for e in (i*2 for i in (1,2,3)))
    assert [3,5,7] == create_list(gen)
    gen = ((c,s) for c in ('a','b') for s in (1,2))
    assert [('a', 1), ('a', 2), ('b', 1), ('b', 2)] == create_list(gen)
    gen = ((s,c) for c in ('a','b') for s in (1,2))
    assert [(1, 'a'), (2, 'a'), (1, 'b'), (2, 'b')] ==  create_list(gen)

def test_ComprehensionListExpr():
    assert [3,5,7] ==  [e+1 for e in (i*2 for i in (1,2,3))]
    assert [3,5,7] ==  [e+1 for e in [i*2 for i in (1,2,3)]]
    assert [('a', 1), ('a', 2), ('b', 1), ('b', 2)] == [ (c,s) for c in ('a','b') for s in (1,2)]
    assert [(1, 'a'), (2, 'a'), (1, 'b'), (2, 'b')] == [ (s,c) for c in ('a','b') for s in (1,2)]

def test_BYTE_ORDER_MARK():
    assert eval('u"\N{BYTE ORDER MARK}"') == '\ufeff'

