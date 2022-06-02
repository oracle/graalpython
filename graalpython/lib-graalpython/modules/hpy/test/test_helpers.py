# MIT License
# 
# Copyright (c) 2021, Oracle and/or its affiliates.
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
from .support import HPyTest


class TestHPyModuleAddType(HPyTest):
    def test_with_spec_only(self):
        mod = self.make_module("""
            static HPyType_Spec dummy_spec = {
                .name = "mytest.Dummy",
            };

            HPyDef_METH(f, "f", f_impl, HPyFunc_NOARGS)
            static HPy f_impl(HPyContext *ctx, HPy self)
            {
                if (!HPyHelpers_AddType(ctx, self, "Dummy", &dummy_spec, NULL))
                {
                    return HPy_NULL;
                }
                return HPy_Dup(ctx, ctx->h_None);
            }

            @EXPORT(f)
            @INIT
        """)
        assert not hasattr(mod, "Dummy")
        mod.f()
        assert isinstance(mod.Dummy, type)
        assert mod.Dummy.__name__ == "Dummy"
        assert isinstance(mod.Dummy(), mod.Dummy)

    def test_with_spec_and_params(self):
        mod = self.make_module("""
            static HPyType_Spec dummy_spec = {
                .name = "mytest.Dummy",
            };

            HPyDef_METH(f, "f", f_impl, HPyFunc_NOARGS)
            static HPy f_impl(HPyContext *ctx, HPy self)
            {
                HPyType_SpecParam param[] = {
                    { HPyType_SpecParam_Base, ctx->h_LongType },
                    { (HPyType_SpecParam_Kind)0 }
                };
                if (!HPyHelpers_AddType(ctx, self, "Dummy", &dummy_spec, param))
                {
                    return HPy_NULL;
                }
                return HPy_Dup(ctx, ctx->h_None);
            }

            @EXPORT(f)
            @INIT
        """)
        assert not hasattr(mod, "Dummy")
        mod.f()
        assert isinstance(mod.Dummy, type)
        assert mod.Dummy.__name__ == "Dummy"
        assert isinstance(mod.Dummy(), mod.Dummy)
        assert isinstance(mod.Dummy(), int)
        assert mod.Dummy() == 0
        assert mod.Dummy(3) == 3
