# -*- encoding: utf-8 -*-
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

import pytest
from .support import HPyTest

class TestUnicode(HPyTest):

    def test_Check(self):
        mod = self.make_module("""
            HPyDef_METH(f, "f", f_impl, HPyFunc_O)
            static HPy f_impl(HPyContext *ctx, HPy self, HPy arg)
            {
                if (HPyUnicode_Check(ctx, arg))
                    return HPy_Dup(ctx, ctx->h_True);
                return HPy_Dup(ctx, ctx->h_False);
            }
            @EXPORT(f)
            @INIT
        """)
        class MyUnicode(str):
            pass

        assert mod.f('hello') is True
        assert mod.f(b'hello') is False
        assert mod.f(MyUnicode('hello')) is True

    def test_FromString(self):
        mod = self.make_module("""
            HPyDef_METH(f, "f", f_impl, HPyFunc_NOARGS)
            static HPy f_impl(HPyContext *ctx, HPy self)
            {
                return HPyUnicode_FromString(ctx, "foobar");
            }
            @EXPORT(f)
            @INIT
        """)
        assert mod.f() == "foobar"

    def test_FromWideChar(self):
        mod = self.make_module("""
            HPyDef_METH(f, "f", f_impl, HPyFunc_O)
            static HPy f_impl(HPyContext *ctx, HPy self, HPy arg)
            {
                const wchar_t buf[] = { 'h', 'e', 'l', 'l', 0xf2, ' ',
                                        'w', 'o', 'r', 'l', 'd', 0 };
                long n = HPyLong_AsLong(ctx, arg);
                return HPyUnicode_FromWideChar(ctx, buf, n);
            }
            @EXPORT(f)
            @INIT
        """)
        assert mod.f(-1) == "hellò world"
        assert mod.f(11) == "hellò world"
        assert mod.f(5) == "hellò"


    def test_AsUTF8String(self):
        mod = self.make_module("""
            HPyDef_METH(f, "f", f_impl, HPyFunc_O)
            static HPy f_impl(HPyContext *ctx, HPy self, HPy arg)
            {
                return HPyUnicode_AsUTF8String(ctx, arg);
            }
            @EXPORT(f)
            @INIT
        """)
        s = 'hellò'
        b = mod.f(s)
        assert type(b) is bytes
        assert b == s.encode('utf-8')

    def test_AsASCIIString(self):
        mod = self.make_module("""
            HPyDef_METH(f, "f", f_impl, HPyFunc_O)
            static HPy f_impl(HPyContext *ctx, HPy self, HPy arg)
            {
                return HPyUnicode_AsASCIIString(ctx, arg);
            }
            @EXPORT(f)
            @INIT
        """)
        s = 'world'
        b = mod.f(s)
        assert type(b) is bytes
        assert b == s.encode('ascii')

    def test_AsLatin1String(self):
        mod = self.make_module("""
            HPyDef_METH(f, "f", f_impl, HPyFunc_O)
            static HPy f_impl(HPyContext *ctx, HPy self, HPy arg)
            {
                return HPyUnicode_AsLatin1String(ctx, arg);
            }
            @EXPORT(f)
            @INIT
        """)
        s = "Müller"
        b = mod.f(s)
        assert type(b) is bytes
        assert b == s.encode('latin1')

    def test_AsUTF8AndSize(self):
        mod = self.make_module("""
            #include <string.h>

            static HPy as_utf8_and_size(HPyContext *ctx, HPy arg, HPy_ssize_t *size) 
            {
                HPy_ssize_t n;
                const char* buf = HPyUnicode_AsUTF8AndSize(ctx, arg, size);
                long res = 0;

                if (size)
                    n = *size;
                else
                    n = strlen(buf);

                for(int i=0; i<n; i++)
                    res = (res * 10) + buf[i];
                return HPyLong_FromLong(ctx, res);
            }

            HPyDef_METH(f, "f", f_impl, HPyFunc_O)
            static HPy f_impl(HPyContext *ctx, HPy self, HPy arg)
            {
                HPy_ssize_t n;
                return as_utf8_and_size(ctx, arg, &n);
            }

            HPyDef_METH(g, "g", g_impl, HPyFunc_O)
            static HPy g_impl(HPyContext *ctx, HPy self, HPy arg)
            {
                return as_utf8_and_size(ctx, arg, NULL);
            }
            @EXPORT(f)
            @EXPORT(g)
            @INIT
        """)
        assert mod.f('ABC') == 100*ord('A') + 10*ord('B') + ord('C')
        assert mod.f(b'A\0C'.decode('utf-8')) == 100*ord('A') + ord('C')
        assert mod.g('ABC') == 100*ord('A') + 10*ord('B') + ord('C')
        assert mod.g(b'A'.decode('utf-8')) == ord('A')
        assert mod.g(b'A\0'.decode('utf-8')) == ord('A')

    def test_DecodeLatin1(self):
        mod = self.make_module("""
            HPyDef_METH(f, "f", f_impl, HPyFunc_O)
            static HPy f_impl(HPyContext *ctx, HPy self, HPy arg)
            {
                const char* buf = HPyBytes_AS_STRING(ctx, arg);
                HPy_ssize_t n = HPyBytes_Size(ctx, arg);
                return HPyUnicode_DecodeLatin1(ctx, buf, n, "");
            }
            @EXPORT(f)
            @INIT
        """)
        res = mod.f(b'M\xfcller')
        assert res == "Müller"

    def test_DecodeASCII(self):
        mod = self.make_module("""
            HPyDef_METH(f, "f", f_impl, HPyFunc_O)
            static HPy f_impl(HPyContext *ctx, HPy self, HPy arg)
            {
                const char* buf = HPyBytes_AS_STRING(ctx, arg);
                HPy_ssize_t n = HPyBytes_Size(ctx, arg);
                return HPyUnicode_DecodeASCII(ctx, buf, n, "");
            }
            @EXPORT(f)
            @INIT
        """)
        assert mod.f(b'hello') == "hello"

    def test_ReadChar(self):
        mod = self.make_module("""
            HPyDef_METH(f, "f", f_impl, HPyFunc_O)
            static HPy f_impl(HPyContext *ctx, HPy self, HPy arg)
            {
                long c = HPyUnicode_ReadChar(ctx, arg, 1);
                return HPyLong_FromLong(ctx, c);
            }
            @EXPORT(f)
            @INIT
        """)
        assert mod.f('ABC') == 66

    def test_EncodeFSDefault(self):
        mod = self.make_module("""
            HPyDef_METH(f, "f", f_impl, HPyFunc_O)
            static HPy f_impl(HPyContext *ctx, HPy self, HPy arg)
            {
                return HPyUnicode_EncodeFSDefault(ctx, arg);
            }
            @EXPORT(f)
            @INIT
        """)
        assert mod.f('ABC') == b'ABC'

    def test_DecodeFSDefault(self):
        mod = self.make_module("""
            HPyDef_METH(f, "f", f_impl, HPyFunc_O)
            static HPy f_impl(HPyContext *ctx, HPy self, HPy arg)
            {
                HPy_ssize_t n;
                const char* buf = HPyUnicode_AsUTF8AndSize(ctx, arg, &n);
                return HPyUnicode_DecodeFSDefault(ctx, buf);
            }

            HPyDef_METH(g, "g", g_impl, HPyFunc_NOARGS)
            static HPy g_impl(HPyContext *ctx, HPy self)
            {
                const char buf[5] = { 'a', 'b', '\\0', 'c' };
                return HPyUnicode_DecodeFSDefaultAndSize(ctx, buf, 4);
            }

            @EXPORT(f)
            @EXPORT(g)
            @INIT
        """)
        assert mod.f('ABC') == "ABC"
        assert mod.g().encode('ascii') == b'ab\0c'
