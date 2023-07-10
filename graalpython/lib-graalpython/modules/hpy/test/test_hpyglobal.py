# Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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

"""
NOTE: this tests are also meant to be run as PyPy "applevel" tests.

This means that global imports will NOT be visible inside the test
functions. In particular, you have to "import pytest" inside the test in order
to be able to use e.g. pytest.raises (which on PyPy will be implemented by a
"fake pytest module")
"""
from .support import HPyTest


class TestHPyGlobal(HPyTest):

    def test_basics(self):
        mod = self.make_module("""
            HPyGlobal myglobal;

            HPyDef_METH(setg, "setg", HPyFunc_O)
            static HPy setg_impl(HPyContext *ctx, HPy self, HPy arg)
            {
                HPyGlobal_Store(ctx, &myglobal, arg);
                return HPy_Dup(ctx, ctx->h_None);
            }

            HPyDef_METH(getg, "getg", HPyFunc_NOARGS)
            static HPy getg_impl(HPyContext *ctx, HPy self)
            {
                return HPyGlobal_Load(ctx, myglobal);
            }

            @EXPORT(setg)
            @EXPORT(getg)
            @EXPORT_GLOBAL(myglobal)
            @INIT
        """)
        obj = {'hello': 'world'}
        assert mod.setg(obj) is None
        assert mod.getg() is obj

    def test_twoglobals(self):
        mod = self.make_module("""
            HPyGlobal myglobal1;
            HPyGlobal myglobal2;

            HPyDef_METH(setg1, "setg1", HPyFunc_O)
            static HPy setg1_impl(HPyContext *ctx, HPy self, HPy arg)
            {
                HPyGlobal_Store(ctx, &myglobal1, arg);
                return HPy_Dup(ctx, ctx->h_None);
            }

            HPyDef_METH(setg2, "setg2", HPyFunc_O)
            static HPy setg2_impl(HPyContext *ctx, HPy self, HPy arg)
            {
                HPyGlobal_Store(ctx, &myglobal2, arg);
                return HPy_Dup(ctx, ctx->h_None);
            }

            HPyDef_METH(getg1, "getg1", HPyFunc_NOARGS)
            static HPy getg1_impl(HPyContext *ctx, HPy self)
            {
                return HPyGlobal_Load(ctx, myglobal1);
            }

            HPyDef_METH(getg2, "getg2", HPyFunc_NOARGS)
            static HPy getg2_impl(HPyContext *ctx, HPy self)
            {
                return HPyGlobal_Load(ctx, myglobal2);
            }

            @EXPORT(setg1)
            @EXPORT(setg2)
            @EXPORT(getg1)
            @EXPORT(getg2)
            @EXPORT_GLOBAL(myglobal1)
            @EXPORT_GLOBAL(myglobal2)
            @INIT
        """)
        obj1 = {'hello': 'world'}
        obj2 = {'foo': 'bar'}
        assert mod.setg1(obj1) is None
        assert mod.setg2(obj2) is None
        assert mod.getg1() is obj1
        assert mod.getg2() is obj2
