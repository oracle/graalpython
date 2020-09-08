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

class TestDict(HPyTest):

    def test_Check(self):
        mod = self.make_module("""
            HPyDef_METH(f, "f", f_impl, HPyFunc_O)
            static HPy f_impl(HPyContext ctx, HPy self, HPy arg)
            {
                if (HPyDict_Check(ctx, arg))
                    return HPy_Dup(ctx, ctx->h_True);
                return HPy_Dup(ctx, ctx->h_False);
            }
            @EXPORT(f)
            @INIT
        """)
        class MyDict(dict):
            pass

        assert mod.f({}) is True
        assert mod.f([]) is False
        assert mod.f(MyDict()) is True

    def test_New(self):
        mod = self.make_module("""
            HPyDef_METH(f, "f", f_impl, HPyFunc_NOARGS)
            static HPy f_impl(HPyContext ctx, HPy self)
            {
                return HPyDict_New(ctx);
            }
            @EXPORT(f)
            @INIT
        """)
        assert mod.f() == {}

    def test_SetItem(self):
        mod = self.make_module("""
            HPyDef_METH(f, "f", f_impl, HPyFunc_O)
            static HPy f_impl(HPyContext ctx, HPy self, HPy arg)
            {
                HPy dict = HPyDict_New(ctx);
                if (HPy_IsNull(dict))
                    return HPy_NULL;
                HPy val = HPyLong_FromLong(ctx, 1234);
                if (HPyDict_SetItem(ctx, dict, arg, val) == -1)
                    return HPy_NULL;
                return dict;
            }
            @EXPORT(f)
            @INIT
        """)
        assert mod.f('hello') == {'hello': 1234}

    def test_GetItem(self):
        mod = self.make_module("""
            HPyDef_METH(f, "f", f_impl, HPyFunc_O)
            static HPy f_impl(HPyContext ctx, HPy self, HPy arg)
            {
                HPy key = HPyUnicode_FromString(ctx, "hello");
                if (HPy_IsNull(key))
                    return HPy_NULL;
                HPy val = HPyDict_GetItem(ctx, arg, key);
                if (HPy_IsNull(val))
                    return HPy_Dup(ctx, ctx->h_None);
                return val;
            }
            @EXPORT(f)
            @INIT
        """)
        assert mod.f({'hello': 1}) == 1
        assert mod.f({}) is None
