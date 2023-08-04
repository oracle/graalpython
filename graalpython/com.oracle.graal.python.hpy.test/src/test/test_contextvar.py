# MIT License
#
# Copyright (c) 2023, 2023, Oracle and/or its affiliates.
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


class TestHPyContextVar(HPyTest):

    def test_basics(self):
        mod = self.make_module("""
            HPyDef_METH(new_ctxv, "new_ctxv", HPyFunc_NOARGS)
            static HPy new_ctxv_impl(HPyContext *ctx, HPy self)
            {
                return HPyContextVar_New(ctx, "test_contextvar", HPy_NULL);
            }

            HPyDef_METH(set_ctxv, "set_ctxv", HPyFunc_VARARGS)
            static HPy set_ctxv_impl(HPyContext *ctx, HPy self, const HPy *args, size_t nargs)
            {
                HPy obj, val;
                if (!HPyArg_Parse(ctx, NULL, args, nargs, "OO", &obj, &val))
                    return HPy_NULL;
                return HPyContextVar_Set(ctx, obj, val);
            }
            HPyDef_METH(get_ctxv, "get_ctxv", HPyFunc_VARARGS)
            static HPy get_ctxv_impl(HPyContext *ctx, HPy self, const HPy *args, size_t nargs)
            {
                HPy obj, def=HPy_NULL, val;
                if (!HPyArg_Parse(ctx, NULL, args, nargs, "O|O", &obj, &def))
                    return HPy_NULL;
                if (HPyContextVar_Get(ctx, obj, def, &val) < 0) {
                    return HPy_NULL;
                }
                return val;
            }


            @EXPORT(new_ctxv)
            @EXPORT(get_ctxv)
            @EXPORT(set_ctxv)
            @INIT
        """)
        var = mod.new_ctxv()
        tok = mod.set_ctxv(var, 4)
        assert tok.var is var
        four = mod.get_ctxv(var)
        assert four == 4
