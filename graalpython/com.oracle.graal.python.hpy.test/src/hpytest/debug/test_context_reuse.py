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

import pytest
from hpytest.support import HPyTest

pytestmark = pytest.mark.skipif(not HPyTest.supports_debug_mode(), reason="debug mode not supported")

@pytest.fixture
def hpy_abi():
    return "debug"


def test_reuse_context_from_global_variable(compiler, python_subprocess):
    mod = compiler.compile_module("""
        #include <stdio.h>

        HPyContext *keep;

        HPyDef_METH(f, "f", HPyFunc_NOARGS)
        static HPy f_impl(HPyContext *ctx, HPy self)
        {
            // We wrongly save the context to a global variable
            keep = ctx;
            return HPy_Dup(ctx, ctx->h_None);
        }

        HPyDef_METH(g, "g", HPyFunc_NOARGS)
        static HPy g_impl(HPyContext *ctx, HPy self)
        {
            HPy t = HPy_Dup(ctx, ctx->h_True);
            // just checking if the correct context works
            if (!HPy_TypeCheck(ctx, t, ctx->h_BoolType)) { 
                // if the correct context gives us bogus result,
                // this will make the test fail 
                HPy_Close(ctx, t);
                return HPy_Dup(ctx, ctx->h_None);
            }
            HPy_Close(ctx, t);
            fprintf(stdout, "Heavy Marmelade\\n");
            fflush(stdout);
            // Here we wrongly use "keep" instead of "ctx"
            return HPy_Dup(keep, ctx->h_None);
        }
        
        HPyDef_METH(bounce, "bounce", HPyFunc_O)
        static HPy bounce_impl(HPyContext *ctx, HPy self, HPy trampoline)
        {
            fprintf(stdout, "Bouncing...\\n");
            fflush(stdout);
            return HPy_CallTupleDict(ctx, trampoline, HPy_NULL, HPy_NULL);
        }
        
        HPyDef_METH(keep_and_bounce, "keep_and_bounce", HPyFunc_O)
        static HPy keep_and_bounce_impl(HPyContext *ctx, HPy self, HPy trampoline)
        {
            fprintf(stdout, "Bouncing differently...\\n");
            fflush(stdout);
            keep = ctx;
            return HPy_CallTupleDict(ctx, trampoline, HPy_NULL, HPy_NULL);
        }

        @EXPORT(f)
        @EXPORT(g)
        @EXPORT(bounce)
        @EXPORT(keep_and_bounce)
        @INIT
    """)

    code = "mod.f(); mod.g()"
    result = python_subprocess.run(mod, code)
    assert result.returncode != 0
    assert b"Error: Wrong HPy Context!" in result.stderr
    assert result.stdout == b"Heavy Marmelade\n"

    code = "mod.f(); mod.bounce(lambda: mod.g())"
    result = python_subprocess.run(mod, code)
    assert result.returncode != 0
    assert b"Error: Wrong HPy Context!" in result.stderr
    assert result.stdout == b"Bouncing...\nHeavy Marmelade\n"

    # checks the situation when the context cache runs out,
    # and we start reusing cached contexts
    code = "mod.f(); bounce_cnt = {};\n" \
           "def trampoline():\n" \
           "    global bounce_cnt\n" \
           "    bounce_cnt -= 1\n" \
           "    return mod.bounce(trampoline) if bounce_cnt > 0 else mod.g()\n" \
           "mod.bounce(trampoline)"

    # With the reference HPy debug context implementation if we happen to run
    # the usage of 'keep' on the same recycled context as when we saved 'keep',
    # then ctx == keep, and it will not fail.
    # To keep the test implementation agnostic, we just stress test it with
    # different numbers and check that it either crashes with the right error
    # or it does not crash and gives the correct result.
    HPY_DEBUG_CTX_CACHE_SIZE = 16
    for size in range(HPY_DEBUG_CTX_CACHE_SIZE-1, HPY_DEBUG_CTX_CACHE_SIZE+2):
        result = python_subprocess.run(mod, code.format(size))
        assert result.stdout == (b"Bouncing...\n" * size) + b"Heavy Marmelade\n"
        if result.returncode != 0:
            assert b"Error: Wrong HPy Context!" in result.stderr

    code = 'mod.keep_and_bounce(lambda: mod.g())'
    result = python_subprocess.run(mod, code)
    assert result.returncode != 0
    assert b"Error: Wrong HPy Context!" in result.stderr
    assert result.stdout == b"Bouncing differently...\n" + b"Heavy Marmelade\n"
