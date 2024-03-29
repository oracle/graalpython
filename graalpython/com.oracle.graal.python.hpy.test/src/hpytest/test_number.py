# MIT License
# 
# Copyright (c) 2020, 2023, Oracle and/or its affiliates.
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

from .support import HPyTest


class TestNumber(HPyTest):

    def test_bool_from_bool_and_long(self):
        import pytest
        mod = self.make_module("""
            HPyDef_METH(from_bool, "from_bool", HPyFunc_O)
            static HPy from_bool_impl(HPyContext *ctx, HPy self, HPy arg)
            {
                int32_t x = HPyLong_AsInt32_t(ctx, arg);
                if (x == -1 && HPyErr_Occurred(ctx))
                    return HPy_NULL;
                if (x != 0 && x != 1) {
                    HPyErr_SetString(ctx, ctx->h_ValueError,
                                         "value must be 0 or 1");
                    return HPy_NULL;
                }
                return HPyBool_FromBool(ctx, (x ? true : false));
            }

            HPyDef_METH(from_long, "from_long", HPyFunc_O)
            static HPy from_long_impl(HPyContext *ctx, HPy self, HPy arg)
            {
                long x = HPyLong_AsLong(ctx, arg);
                if (x == -1 && HPyErr_Occurred(ctx))
                    return HPy_NULL;
                return HPyBool_FromLong(ctx, x);
            }
            @EXPORT(from_bool)
            @EXPORT(from_long)
            @INIT
        """)
        assert mod.from_bool(0) is False
        assert mod.from_bool(1) is True
        with pytest.raises(ValueError):
            mod.from_bool(2)
        assert mod.from_long(0) is False
        assert mod.from_long(42) is True

    def test_unary(self):
        import pytest
        import operator
        ops = (
            ('Negative', operator.neg),
            ('Positive', operator.pos),
            ('Absolute', abs),
            ('Invert', operator.invert),
            ('Index', operator.index),
            ('Long', int),
            ('Float', float),
        )
        template = """
            HPyDef_METH(f_{c_name}, "f_{c_name}", HPyFunc_O)
            static HPy f_{c_name}_impl(HPyContext *ctx, HPy self, HPy arg)
            {{
                return HPy_{c_name}(ctx, arg);
            }}
            @EXPORT(f_{c_name})
        """
        code = []
        for c_name, _ in ops:
            code.append(template.format(c_name=c_name))
        code.append('@INIT')
        mod = self.make_module('\n'.join(code), name='unary_ops')
        for c_name, op in ops:
            c_op = getattr(mod, 'f_%s' % c_name)
            assert c_op(-5) == op(-5)
            assert c_op(6) == op(6)
            try:
                res = op(4.75)
            except Exception as e:
                with pytest.raises(e.__class__):
                    c_op(4.75)
            else:
                assert c_op(4.75) == res

    def test_binary(self):
        import operator
        ops = (
            ('Add', operator.add),
            ('Subtract', operator.sub),
            ('Multiply', operator.mul),
            ('FloorDivide', operator.floordiv),
            ('TrueDivide', operator.truediv),
            ('Remainder', operator.mod),
            ('Divmod', divmod),
            ('Lshift', operator.lshift),
            ('Rshift', operator.rshift),
            ('And', operator.and_),
            ('Xor', operator.xor),
            ('Or', operator.or_),
        )
        template = """
            HPyDef_METH(f_{c_name}, "f_{c_name}", HPyFunc_VARARGS)
            static HPy f_{c_name}_impl(HPyContext *ctx, HPy self,
                              const HPy *args, size_t nargs)
            {{
                HPy a, b;
                if (!HPyArg_Parse(ctx, NULL, args, nargs, "OO", &a, &b))
                    return HPy_NULL;
                return HPy_{c_name}(ctx, a, b);
            }}
            @EXPORT(f_{c_name})
            """
        code = []
        for c_name, _ in ops:
            code.append(template.format(c_name=c_name))
        code.append('@INIT')
        mod = self.make_module('\n'.join(code), name='binary_ops')
        for c_name, op in ops:
            c_op = getattr(mod, 'f_%s' % c_name)
            assert c_op(5, 4) == op(5, 4)
            assert c_op(6, 3) == op(6, 3)

    def test_power(self):
        mod = self.make_module("""
            HPyDef_METH(f, "f", HPyFunc_VARARGS)
            static HPy f_impl(HPyContext *ctx, HPy self,
                              const HPy *args, size_t nargs)
            {
                HPy a, b, c;
                if (!HPyArg_Parse(ctx, NULL, args, nargs, "OOO", &a, &b, &c))
                    return HPy_NULL;
                return HPy_Power(ctx, a, b, c);
            }
            @EXPORT(f)
            @INIT
        """)
        assert mod.f(4, 5, None) == 4 ** 5
        assert mod.f(4, 5, 7) == pow(4, 5, 7)

    def test_matmul(self):
        class Mat:
            def __matmul__(self, other):
                return ('matmul', self, other)
        m1 = Mat()
        m2 = Mat()
        mod = self.make_module("""
            HPyDef_METH(f, "f", HPyFunc_VARARGS)
            static HPy f_impl(HPyContext *ctx, HPy self,
                              const HPy *args, size_t nargs)
            {
                HPy a, b;
                if (!HPyArg_Parse(ctx, NULL, args, nargs, "OO", &a, &b))
                    return HPy_NULL;
                return HPy_MatrixMultiply(ctx, a, b);
            }
            @EXPORT(f)
            @INIT
        """)
        assert mod.f(m1, m2) == m1.__matmul__(m2)

    def test_inplace_binary(self):
        import operator
        for c_name, py_name in [
                ('Add', '__iadd__'),
                ('Subtract', '__isub__'),
                ('Multiply', '__imul__'),
                ('FloorDivide', '__ifloordiv__'),
                ('TrueDivide', '__itruediv__'),
                ('Remainder', '__imod__'),
                ('Lshift', '__ilshift__'),
                ('Rshift', '__irshift__'),
                ('And', '__iand__'),
                ('Xor', '__ixor__'),
                ('Or', '__ior__'),
                ]:
            mod = self.make_module("""
                HPyDef_METH(f, "f", HPyFunc_VARARGS)
                static HPy f_impl(HPyContext *ctx, HPy self,
                                  const HPy *args, size_t nargs)
                {
                    HPy a, b;
                    if (!HPyArg_Parse(ctx, NULL, args, nargs, "OO", &a, &b))
                        return HPy_NULL;
                    return HPy_InPlace%s(ctx, a, b);
                }
                @EXPORT(f)
                @INIT
            """ % (c_name,), name='number_'+c_name)
            class A:
                def mymethod(self, b):
                    return (py_name, b)
            setattr(A, py_name, A.mymethod)
            assert mod.f(A(), 12.34) == A().mymethod(12.34)

    def test_inplace_power(self):
        mod = self.make_module("""
            HPyDef_METH(f, "f", HPyFunc_VARARGS)
            static HPy f_impl(HPyContext *ctx, HPy self,
                              const HPy *args, size_t nargs)
            {
                HPy a, b, c;
                if (!HPyArg_Parse(ctx, NULL, args, nargs, "OOO", &a, &b, &c))
                    return HPy_NULL;
                return HPy_InPlacePower(ctx, a, b, c);
            }
            @EXPORT(f)
            @INIT
        """)
        class A:
            def __ipow__(self, b):
                return ('ipow', b)
        # the behavior of PyNumber_InPlacePower is weird: if __ipow__ is
        # defined, the 3rd arg is always ignored, even if the doc say the
        # opposite
        assert mod.f(A(), 5, None) == A().__ipow__(5)
        assert mod.f(A(), 7, 'hello') == A().__ipow__(7)
        assert mod.f(4, 5, 7) == pow(4, 5, 7)

    def test_inplace_matmul(self):
        class Mat:
            def __imatmul__(self, other):
                return ('imatmul', self, other)
        m1 = Mat()
        m2 = Mat()
        mod = self.make_module("""
            HPyDef_METH(f, "f", HPyFunc_VARARGS)
            static HPy f_impl(HPyContext *ctx, HPy self,
                              const HPy *args, size_t nargs)
            {
                HPy a, b;
                if (!HPyArg_Parse(ctx, NULL, args, nargs, "OO", &a, &b))
                    return HPy_NULL;
                return HPy_InPlaceMatrixMultiply(ctx, a, b);
            }
            @EXPORT(f)
            @INIT
        """)
        assert mod.f(m1, m2) == m1.__imatmul__(m2)

    def test_number_check(self):
        mod = self.make_module("""
            HPyDef_METH(f, "f", HPyFunc_O)
            static HPy f_impl(HPyContext *ctx, HPy self, HPy arg)
            {
                int cond = HPyNumber_Check(ctx, arg);
                return HPyLong_FromLong(ctx, cond);
            }
            @EXPORT(f)
            @INIT
        """)
        assert mod.f("foo") == 0
        assert mod.f(42) == 1
        assert mod.f(42.1) == 1
