# MIT License
#
# Copyright (c) 2020, Oracle and/or its affiliates.
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

class TestTuple(HPyTest):

    def test_FromArray(self):
        mod = self.make_module("""
            HPyDef_METH(f, "f", f_impl, HPyFunc_O)
            static HPy f_impl(HPyContext ctx, HPy self, HPy arg)
            {
                HPy x = HPyLong_FromLong(ctx, 42);
                if (HPy_IsNull(x))
                     return HPy_NULL;
                HPy items[] = {self, arg, x};
                HPy res = HPyTuple_FromArray(ctx, items, 3);
                HPy_Close(ctx, x);
                return res;
            }
            @EXPORT(f)
            @INIT
        """)
        assert mod.f('hello') == (mod, 'hello', 42)

    def test_Pack(self):
        mod = self.make_module("""
            HPyDef_METH(f, "f", f_impl, HPyFunc_O)
            static HPy f_impl(HPyContext ctx, HPy self, HPy arg)
            {
                HPy x = HPyLong_FromLong(ctx, 42);
                if (HPy_IsNull(x))
                     return HPy_NULL;
                return HPyTuple_Pack(ctx, 3, self, arg, x);
            }
            @EXPORT(f)
            @INIT
        """)
        assert mod.f('hello') == (mod, 'hello', 42)

    def test_TupleBuilder(self):
        mod = self.make_module("""
            HPyDef_METH(f, "f", f_impl, HPyFunc_O)
            static HPy f_impl(HPyContext ctx, HPy h_self, HPy h_arg)
            {
                HPyTupleBuilder builder = HPyTupleBuilder_New(ctx, 3);
                HPyTupleBuilder_Set(ctx, builder, 0, h_arg);
                HPyTupleBuilder_Set(ctx, builder, 1, ctx->h_True);
                HPy h_num = HPyLong_FromLong(ctx, -42);
                if (HPy_IsNull(h_num))
                {
                    HPyTupleBuilder_Cancel(ctx, builder);
                    return HPy_NULL;
                }
                HPyTupleBuilder_Set(ctx, builder, 2, h_num);
                HPy_Close(ctx, h_num);
                HPy h_tuple = HPyTupleBuilder_Build(ctx, builder);
                return h_tuple;
            }
            @EXPORT(f)
            @INIT
        """)
        assert mod.f("xy") == ("xy", True, -42)
