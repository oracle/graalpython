# MIT License
#
# Copyright (c) 2021, 2024, Oracle and/or its affiliates.
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

class TestImport(HPyTest):

    def test_ImportModule(self):
        import pytest
        import sys
        mod = self.make_module("""
            HPyDef_METH(f, "f", HPyFunc_O)
            static HPy f_impl(HPyContext *ctx, HPy self, HPy h_name)
            {
                // we use bytes because ATM we don't have HPyUnicode_AsUTF8 or similar
                const char *name = HPyBytes_AsString(ctx, h_name);
                if (name == NULL)
                    return HPy_NULL;
                return HPyImport_ImportModule(ctx, name);
            }
            @EXPORT(f)
            @INIT
        """)
        sys2 = mod.f(b'sys')
        assert sys is sys2
        with pytest.raises(ImportError):
            mod.f(b'This is the name of a module which does not exist, hopefully')
