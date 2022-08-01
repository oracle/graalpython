# MIT License
# 
# Copyright (c) 2022, Oracle and/or its affiliates.
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

import pytest
from .support import HPyTest


class TestContextVar(HPyTest):
    @pytest.mark.skip(reason="GR-40178")
    def test_ContextVar_basics(self):
        mod = self.make_module("""
            HPyDef_METH(create_ctxvar, "create_ctxvar", create_ctxvar_impl, HPyFunc_O)
            static HPy create_ctxvar_impl(HPyContext *ctx, HPy self, HPy default_value)
            {
                return HPyContextVar_New(ctx, "testctxvar", default_value);
            }

            HPyDef_METH(create_ctxvar_null, "create_ctxvar_null", create_ctxvar_null_impl, HPyFunc_NOARGS)
            static HPy create_ctxvar_null_impl(HPyContext *ctx, HPy self)
            {
                return HPyContextVar_New(ctx, "testctxvar", HPy_NULL);
            }

            HPyDef_METH(ctxvar_get, "ctxvar_get", ctxvar_get_impl, HPyFunc_O)
            static HPy ctxvar_get_impl(HPyContext *ctx, HPy self, HPy ctxvar)
            {
                HPy result;
                if (HPyContextVar_Get(ctx, ctxvar, HPy_NULL, &result))
                    return HPy_NULL;
                if (HPy_IsNull(result))
                    return HPyUnicode_FromString(ctx, "NO VALUE");
                return result;
            }

            HPyDef_METH(ctxvar_set, "ctxvar_set", ctxvar_set_impl, HPyFunc_VARARGS)
            static HPy ctxvar_set_impl(HPyContext *ctx, HPy self,
                    HPy *args, HPy_ssize_t nargs)
            {
                return HPyContextVar_Set(ctx, args[0], args[1]);
            }

            @EXPORT(create_ctxvar)
            @EXPORT(ctxvar_set)
            @EXPORT(ctxvar_get)
            @EXPORT(create_ctxvar_null)
            @INIT
        """)
        v = mod.create_ctxvar(42)
        assert mod.ctxvar_get(v) == 42
        mod.ctxvar_set(v, 'hello')
        assert mod.ctxvar_get(v) == 'hello'

        v = mod.create_ctxvar_null()
        assert mod.ctxvar_get(v) == 'NO VALUE'
