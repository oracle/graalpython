# MIT License
# 
# Copyright (c) 2020, 2022, Oracle and/or its affiliates.
# Copyright (c) 2019 pyhandle
# 
# Permission is hereby granted, free of charge, to any person obtaining a copy
# of this software and associated documentation files (the "Software"), to deal
# in the Software without restriction, including without limitation the rights
# to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
# copies of the Software, and to permit persons to whom the Software is
# furnished to do so, subject to the following conditions:
# 
# The above copyright notice and this permission notice shall be included in all
# copies or substantial portions of the Software.
# 
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
# SOFTWARE.

"""
NOTE: this tests are also meant to be run as PyPy "applevel" tests.

This means that global imports will NOT be visible inside the test
functions. In particular, you have to "import pytest" inside the test in order
to be able to use e.g. pytest.raises (which on PyPy will be implemented by a
"fake pytest module")
"""
import math

from .support import HPyTest


class TestObject(HPyTest):
    def test_getattr(self):
        import pytest
        mod = self.make_module("""
            HPyDef_METH(f, "f", f_impl, HPyFunc_O)
            static HPy f_impl(HPyContext *ctx, HPy self, HPy arg)
            {
                HPy name, result;
                name = HPyUnicode_FromString(ctx, "foo");
                if (HPy_IsNull(name))
                    return HPy_NULL;
                result = HPy_GetAttr(ctx, arg, name);
                HPy_Close(ctx, name);
                if (HPy_IsNull(result))
                    return HPy_NULL;
                return result;
            }
            @EXPORT(f)
            @INIT
        """)

        class Attrs:
            def __init__(self, **kw):
                for k, v in kw.items():
                    setattr(self, k, v)

        class ClassAttr:
            foo = 10

        class PropAttr:
            @property
            def foo(self):
                return 11

        assert mod.f(Attrs(foo=5)) == 5
        with pytest.raises(AttributeError):
            mod.f(Attrs())
        with pytest.raises(AttributeError):
            mod.f(42)
        assert mod.f(ClassAttr) == 10
        assert mod.f(ClassAttr()) == 10
        assert mod.f(PropAttr()) == 11

    def test_getattr_s(self):
        import pytest
        mod = self.make_module("""
            HPyDef_METH(f, "f", f_impl, HPyFunc_O)
            static HPy f_impl(HPyContext *ctx, HPy self, HPy arg)
            {
                HPy result;
                result = HPy_GetAttr_s(ctx, arg, "foo");
                if (HPy_IsNull(result))
                    return HPy_NULL;
                return result;
            }

            HPyDef_METH(g, "g", g_impl, HPyFunc_O)
            static HPy g_impl(HPyContext *ctx, HPy self, HPy arg)
            {
                HPy result;
                result = HPy_MaybeGetAttr_s(ctx, arg, "foo");
                if (HPy_IsNull(result) && !HPyErr_Occurred(ctx))
                    return HPy_Dup(ctx, ctx->h_None);
                return result;
            }

            @EXPORT(f)
            @EXPORT(g)
            @INIT
        """)

        class Attrs:
            def __init__(self, **kw):
                for k, v in kw.items():
                    setattr(self, k, v)

        class ClassAttr:
            foo = 10

        class PropAttr:
            @property
            def foo(self):
                return 11

        assert mod.f(Attrs(foo=5)) == 5
        with pytest.raises(AttributeError):
            mod.f(Attrs())
        with pytest.raises(AttributeError):
            mod.f(42)
        assert mod.f(ClassAttr) == 10
        assert mod.f(ClassAttr()) == 10
        assert mod.f(PropAttr()) == 11

        assert mod.g(Attrs(foo=5)) == 5
        assert mod.g(Attrs()) is None
        assert mod.g(42) is None
        assert mod.g(ClassAttr) == 10
        assert mod.g(ClassAttr()) == 10
        assert mod.g(PropAttr()) == 11
        assert mod.g(type) is None

    def test_hasattr(self):
        mod = self.make_module("""
            HPyDef_METH(f, "f", f_impl, HPyFunc_O)
            static HPy f_impl(HPyContext *ctx, HPy self, HPy arg)
            {
                HPy name;
                int result;
                name = HPyUnicode_FromString(ctx, "foo");
                if (HPy_IsNull(name))
                    return HPy_NULL;
                result = HPy_HasAttr(ctx, arg, name);
                HPy_Close(ctx, name);
                if (result == -1)
                    return HPy_NULL;
                if (result)
                    return HPy_Dup(ctx, ctx->h_True);
                return HPy_Dup(ctx, ctx->h_False);
            }
            @EXPORT(f)
            @INIT
        """)

        class Attrs:
            def __init__(self, **kw):
                for k, v in kw.items():
                    setattr(self, k, v)

        class ClassAttr:
            foo = 10

        class PropAttr:
            @property
            def foo(self):
                return 11

        class PropAttrRaising:
            @property
            def foo(self):
                raise RuntimeError


        assert mod.f(Attrs(foo=5)) is True
        assert mod.f(Attrs()) is False
        assert mod.f(42) is False
        assert mod.f(ClassAttr) is True
        assert mod.f(ClassAttr()) is True
        assert mod.f(PropAttr()) is True
        assert mod.f(PropAttrRaising()) is False


    def test_hasattr_s(self):
        mod = self.make_module("""
            HPyDef_METH(f, "f", f_impl, HPyFunc_O)
            static HPy f_impl(HPyContext *ctx, HPy self, HPy arg)
            {
                int result;
                result = HPy_HasAttr_s(ctx, arg, "foo");
                if (result == -1)
                    return HPy_NULL;
                if (result)
                    return HPy_Dup(ctx, ctx->h_True);
                return HPy_Dup(ctx, ctx->h_False);
            }
            @EXPORT(f)
            @INIT
        """)

        class Attrs:
            def __init__(self, **kw):
                for k, v in kw.items():
                    setattr(self, k, v)

        class ClassAttr:
            foo = 10

        class PropAttr:
            @property
            def foo(self):
                return 11

        class PropAttrRaising:
            @property
            def foo(self):
                raise RuntimeError

        assert mod.f(Attrs(foo=5)) is True
        assert mod.f(Attrs()) is False
        assert mod.f(42) is False
        assert mod.f(ClassAttr) is True
        assert mod.f(ClassAttr()) is True
        assert mod.f(PropAttr()) is True
        assert mod.f(PropAttrRaising()) is False

    def test_setattr(self):
        import pytest
        mod = self.make_module("""
            HPyDef_METH(f, "f", f_impl, HPyFunc_O)
            static HPy f_impl(HPyContext *ctx, HPy self, HPy arg)
            {
                HPy name;
                int result;
                name = HPyUnicode_FromString(ctx, "foo");
                if (HPy_IsNull(name))
                    return HPy_NULL;
                result = HPy_SetAttr(ctx, arg, name, ctx->h_True);
                HPy_Close(ctx, name);
                if (result < 0)
                    return HPy_NULL;
                return HPy_Dup(ctx, ctx->h_None);
            }
            @EXPORT(f)
            @INIT
        """)

        class Attrs:
            pass

        class ClassAttr:
            pass

        class ReadOnlyPropAttr:
            @property
            def foo(self):
                return 11

        class WritablePropAttr:
            @property
            def foo(self):
                return self._foo

            @foo.setter
            def foo(self, value):
                self._foo = value

        a = Attrs()
        mod.f(a)
        assert a.foo is True

        mod.f(ClassAttr)
        assert ClassAttr.foo is True
        assert ClassAttr().foo is True

        with pytest.raises(AttributeError):
            mod.f(object())

        with pytest.raises(AttributeError):
            mod.f(ReadOnlyPropAttr())

        b = WritablePropAttr()
        mod.f(b)
        assert b.foo is True

    def test_setattr_s(self):
        import pytest
        mod = self.make_module("""
            HPyDef_METH(f, "f", f_impl, HPyFunc_O)
            static HPy f_impl(HPyContext *ctx, HPy self, HPy arg)
            {
                int result;
                result = HPy_SetAttr_s(ctx, arg, "foo", ctx->h_True);
                if (result < 0)
                    return HPy_NULL;
                return HPy_Dup(ctx, ctx->h_None);
            }
            @EXPORT(f)
            @INIT
        """)

        class Attrs:
            pass

        class ClassAttr:
            pass

        class ReadOnlyPropAttr:
            @property
            def foo(self):
                return 11

        class WritablePropAttr:
            @property
            def foo(self):
                return self._foo

            @foo.setter
            def foo(self, value):
                self._foo = value

        a = Attrs()
        mod.f(a)
        assert a.foo is True

        mod.f(ClassAttr)
        assert ClassAttr.foo is True
        assert ClassAttr().foo is True

        with pytest.raises(AttributeError):
            mod.f(object())

        with pytest.raises(AttributeError):
            mod.f(ReadOnlyPropAttr())

        b = WritablePropAttr()
        mod.f(b)
        assert b.foo is True

    def check_subscript_type_error(self, fun):
        import pytest
        for obj in [42, 3.14, None]:
            with pytest.raises(TypeError):
                fun(obj)

    def test_getitem(self):
        import pytest
        mod = self.make_module("""
            HPyDef_METH(f, "f", f_impl, HPyFunc_O)
            static HPy f_impl(HPyContext *ctx, HPy self, HPy arg)
            {
                HPy key, result;
                key = HPyLong_FromLong(ctx, 3);
                if (HPy_IsNull(key))
                    return HPy_NULL;
                result = HPy_GetItem(ctx, arg, key);
                HPy_Close(ctx, key);
                if (HPy_IsNull(result))
                    return HPy_NULL;
                return result;
            }
            @EXPORT(f)
            @INIT
        """)
        assert mod.f({3: "hello"}) == "hello"
        assert mod.f({3: 42}) == 42
        assert mod.f({3: 0.5}) == 0.5
        assert math.isnan(mod.f({3: math.nan}))
        with pytest.raises(KeyError) as exc:
            mod.f({1: "bad"})
        assert exc.value.args == (3,)

        assert mod.f([0, 1, 2, "hello"]) == "hello"
        with pytest.raises(IndexError):
            mod.f([])

        self.check_subscript_type_error(mod.f)

    def test_getitem_i(self):
        import pytest
        mod = self.make_module("""
            HPyDef_METH(f, "f", f_impl, HPyFunc_O)
            static HPy f_impl(HPyContext *ctx, HPy self, HPy arg)
            {
                HPy result;
                result = HPy_GetItem_i(ctx, arg, 3);
                if (HPy_IsNull(result))
                    return HPy_NULL;
                return result;
            }
            @EXPORT(f)
            @INIT
        """)
        assert mod.f({3: "hello"}) == "hello"
        assert mod.f({3: 42}) == 42
        assert mod.f({3: 0.5}) == 0.5
        assert math.isnan(mod.f({3: math.nan}))
        with pytest.raises(KeyError) as exc:
            mod.f({1: "bad"})
        assert exc.value.args == (3,)

        assert mod.f([0, 1, 2, "hello"]) == "hello"
        with pytest.raises(IndexError):
            mod.f([])

        self.check_subscript_type_error(mod.f)

    def test_getitem_s(self):
        import pytest
        mod = self.make_module("""
            HPyDef_METH(f, "f", f_impl, HPyFunc_O)
            static HPy f_impl(HPyContext *ctx, HPy self, HPy arg)
            {
                HPy result;
                result = HPy_GetItem_s(ctx, arg, "limes");
                if (HPy_IsNull(result))
                    return HPy_NULL;
                return result;
            }
            @EXPORT(f)
            @INIT
        """)
        assert mod.f({"limes": "hello"}) == "hello"
        assert mod.f({"limes": 42}) == 42
        assert mod.f({"limes": 0.5}) == 0.5
        assert math.isnan(mod.f({"limes": math.nan}))
        with pytest.raises(KeyError) as exc:
            mod.f({"oranges": "bad"})
        assert exc.value.args == ("limes",)

        with pytest.raises(TypeError):
            mod.f([])

        self.check_subscript_type_error(mod.f)

    def test_setitem(self):
        import pytest
        mod = self.make_module("""
            HPyDef_METH(f, "f", f_impl, HPyFunc_O)
            static HPy f_impl(HPyContext *ctx, HPy self, HPy arg)
            {
                HPy key;
                int result;
                key = HPyLong_FromLong(ctx, 3);
                if (HPy_IsNull(key))
                    return HPy_NULL;
                result = HPy_SetItem(ctx, arg, key, ctx->h_True);
                HPy_Close(ctx, key);
                if (result < 0)
                    return HPy_NULL;
                return HPy_Dup(ctx, arg);
            }
            @EXPORT(f)
            @INIT
        """)
        assert mod.f({}) == {3: True}
        assert mod.f({"a": 1}) == {"a": 1, 3: True}
        assert mod.f({3: False}) == {3: True}

        assert mod.f([0, 1, 2, False]) == [0, 1, 2, True]
        with pytest.raises(IndexError):
            mod.f([])

        self.check_subscript_type_error(mod.f)


    def test_setitem_i(self):
        import pytest
        mod = self.make_module("""
            HPyDef_METH(f, "f", f_impl, HPyFunc_O)
            static HPy f_impl(HPyContext *ctx, HPy self, HPy arg)
            {
                int result;
                result = HPy_SetItem_i(ctx, arg, 3, ctx->h_True);
                if (result < 0)
                    return HPy_NULL;
                return HPy_Dup(ctx, arg);
            }
            @EXPORT(f)
            @INIT
        """)
        assert mod.f({}) == {3: True}
        assert mod.f({"a": 1}) == {"a": 1, 3: True}
        assert mod.f({3: False}) == {3: True}

        assert mod.f([0, 1, 2, False]) == [0, 1, 2, True]
        with pytest.raises(IndexError):
            mod.f([])

        self.check_subscript_type_error(mod.f)

    def test_setitem_s(self):
        import pytest
        mod = self.make_module("""
            HPyDef_METH(f, "f", f_impl, HPyFunc_O)
            static HPy f_impl(HPyContext *ctx, HPy self, HPy arg)
            {
                int result;
                result = HPy_SetItem_s(ctx, arg, "limes", ctx->h_True);
                if (result < 0)
                    return HPy_NULL;
                return HPy_Dup(ctx, arg);
            }
            @EXPORT(f)
            @INIT
        """)
        assert mod.f({}) == {"limes": True}
        assert mod.f({"a": 1}) == {"a": 1, "limes": True}
        assert mod.f({"limes": False}) == {"limes": True}

        with pytest.raises(TypeError):
            mod.f([])

        self.check_subscript_type_error(mod.f)

    def test_length(self):
        mod = self.make_module("""
            HPyDef_METH(f, "f", f_impl, HPyFunc_O)
            static HPy f_impl(HPyContext *ctx, HPy self, HPy arg)
            {
                HPy_ssize_t result;
                result = HPy_Length(ctx, arg);
                if (result < 0)
                    return HPy_NULL;
                return HPyLong_FromSsize_t(ctx, result);
            }
            @EXPORT(f)
            @INIT
        """)
        assert mod.f([5,6,7,8]) == 4
        assert mod.f({"a": 1}) == 1

    def test_contains(self):
        mod = self.make_module("""
            HPyDef_METH(f, "f", f_impl, HPyFunc_VARARGS)
            static HPy f_impl(HPyContext *ctx, HPy self, HPy *args, HPy_ssize_t nargs)
            {
                int result = HPy_Contains(ctx, args[0], args[1]);
                if (result == -1) {
                    return HPy_NULL;
                }
                return HPyLong_FromLong(ctx, result);
            }
            @EXPORT(f)
            @INIT
        """)

        class WithContains:
            def __contains__(self, item):
                return item == 42

        class WithIter:
            def __iter__(self):
                return [1, 2, 3].__iter__()

        class WithGetitem:
            def __getitem__(self, item):
                if item > 3:
                    raise IndexError()
                else:
                    return item

        class Dummy:
            pass

        assert mod.f([5, 6, 42, 7, 8], 42)
        assert not mod.f([5, 6, 42, 7, 8], 4)

        assert mod.f(WithContains(), 42)
        assert not mod.f(WithContains(), 1)

        assert mod.f(WithIter(), 2)
        assert not mod.f(WithIter(), 33)

        assert mod.f(WithGetitem(), 2)
        assert not mod.f(WithGetitem(), 33)

        import pytest
        with pytest.raises(TypeError):
            mod.f(Dummy(), 42)

    def test_dump(self):
        # _HPy_Dump is supposed to be used e.g. inside a gdb session: it
        # prints various about the given handle to stdout, and it's
        # implementation-specific. As such, it's hard to write a meaningful
        # test: let's just call it an check it doesn't crash.
        mod = self.make_module("""
            HPyDef_METH(f, "f", f_impl, HPyFunc_O)
            static HPy f_impl(HPyContext *ctx, HPy self, HPy arg)
            {
                _HPy_Dump(ctx, arg);
                return HPy_Dup(ctx, ctx->h_None);
            }
            @EXPORT(f)
            @INIT
        """)
        mod.f('hello')

    def test_type(self):
        mod = self.make_module("""
            HPyDef_METH(f, "f", f_impl, HPyFunc_O)
            static HPy f_impl(HPyContext *ctx, HPy self, HPy arg)
            {
                return HPy_Type(ctx, arg);
            }
            @EXPORT(f)
            @INIT
        """)
        assert mod.f('hello') is str
        assert mod.f(42) is int

    def test_typecheck_and_subtype(self):
        mod = self.make_module("""
            HPyDef_METH(f, "f", f_impl, HPyFunc_VARARGS)
            static HPy f_impl(HPyContext *ctx, HPy self, HPy *args, HPy_ssize_t nargs)
            {
                HPy a, b;
                int c, res;
                if (!HPyArg_Parse(ctx, NULL, args, nargs, "OOi", &a, &b, &c))
                    return HPy_NULL;
                if (c) {
                    res = HPy_TypeCheck(ctx, a, b);
                } else {
                    HPy t = HPy_Type(ctx, a);
                    res = HPyType_IsSubtype(ctx, t, b);
                    HPy_Close(ctx, t);
                }
                return HPyBool_FromLong(ctx, res);
            }
            @EXPORT(f)
            @INIT
        """)
        class MyStr(str):
            pass
        for use_typecheck in [True, False]:
            assert mod.f('hello', str, use_typecheck)
            assert not mod.f('hello', int, use_typecheck)
            assert mod.f(MyStr('hello'), str, use_typecheck)

    def test_is(self):
        mod = self.make_module("""
            HPyDef_METH(f, "f", f_impl, HPyFunc_VARARGS)
            static HPy f_impl(HPyContext *ctx, HPy self, HPy *args, HPy_ssize_t nargs)
            {
                HPy obj, other;
                if (!HPyArg_Parse(ctx, NULL, args, nargs, "OO", &obj, &other))
                    return HPy_NULL;
                int res = HPy_Is(ctx, obj, other);
                return HPyBool_FromLong(ctx, res);
            }
            @EXPORT(f)
            @INIT
        """)
        assert mod.f(None, None)
        a = object()
        assert mod.f(a, a)
        assert not mod.f(a, None)

    def test_is_ctx_constant(self):
        mod = self.make_module("""
            HPyDef_METH(f, "f", f_impl, HPyFunc_VARARGS)
            static HPy f_impl(HPyContext *ctx, HPy self, HPy *args, HPy_ssize_t nargs)
            {
                HPy obj;
                int constant;
                if (!HPyArg_Parse(ctx, NULL, args, nargs, "iO", &constant, &obj))
                    return HPy_NULL;
                HPy h_constant;
                switch (constant) {
                case 0:
                    h_constant = ctx->h_None;
                    break;
                case 1:
                    h_constant = ctx->h_True;
                    break;
                case 2:
                    h_constant = ctx->h_False;
                    break;
                case 3:
                    h_constant = ctx->h_NotImplemented;
                    break;
                case 4:
                    h_constant = ctx->h_Ellipsis;
                    break;
                default:
                    HPyErr_SetString(ctx, ctx->h_ValueError, "invalid choice");
                    return HPy_NULL;
                }
                int res = HPy_Is(ctx, obj, h_constant);
                return HPyBool_FromLong(ctx, res);
            }
            @EXPORT(f)
            @INIT
        """)
        ctx_constants = [None, True, False, NotImplemented, Ellipsis]
        for idx, const in enumerate(ctx_constants):
            for other_idx in range(len(ctx_constants)):
                expected = (idx == other_idx)
                assert mod.f(other_idx, const) == expected, "{}, {}, {}".format(other_idx, const, expected)
