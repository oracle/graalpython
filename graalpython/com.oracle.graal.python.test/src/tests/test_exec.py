# Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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

# The basis for this file before inclusion and extension here is
# Copyright (c) 2017, The PyPy Project
#
#     The MIT License
# Permission is hereby granted, free of charge, to any person
# obtaining a copy of this software and associated documentation
# files (the "Software"), to deal in the Software without
# restriction, including without limitation the rights to use,
# copy, modify, merge, publish, distribute, sublicense, and/or
# sell copies of the Software, and to permit persons to whom the
# Software is furnished to do so, subject to the following conditions:
#
# The above copyright notice and this permission notice shall be included
# in all copies or substantial portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS
# OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
# THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
# FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
# DEALINGS IN THE SOFTWARE.


def raises(exc, func, *args):
    try:
        func(*args)
    except exc:
        pass
    else:
        assert False


class ExecTests:
    def test_string(self):
        g = {}
        l = {}
        exec("a = 3", g, l)
        assert l['a'] == 3

    def test_localfill(self):
        g = {}
        exec("a = 3", g)
        assert g['a'] == 3

    def test_builtinsupply(self):
        g = {}
        exec("pass", g)
        assert '__builtins__' in g

    def test_invalidglobal(self):
        def f():
            exec('pass', 1)
        raises(TypeError, f)

    def test_invalidlocal(self):
        def f():
            exec('pass', {}, 2)
        raises(TypeError, f)

    def test_codeobject(self):
        co = compile("a = 3", '<string>', 'exec')
        g = {}
        l = {}
        exec(co, g, l)
        assert l['a'] == 3

    def test_implicit(self):
        a = 4
        exec("a = 3")
        assert a == 4

    def test_tuplelocals(self):
        g = {}
        l = {}
        exec("a = 3", g, l)
        assert l['a'] == 3

    def test_tupleglobals(self):
        g = {}
        exec("a = 3", g)
        assert g['a'] == 3

    def test_exceptionfallthrough(self):
        def f():
            exec('raise TypeError', {})
        raises(TypeError, f)

    def test_global_stmt(self):
        g = {}
        l = {}
        co = compile("global a; a=5", '', 'exec')
        #import dis
        #dis.dis(co)
        exec(co, g, l)
        assert l == {}
        assert g['a'] == 5

    def test_specialcase_free_load(self):
        exec("""if 1:
            def f():
                exec('a=3')
                return a
            raises(NameError, f)\n""")

    def test_specialcase_free_load2(self):
        exec("""if 1:
            def f(a):
                exec('a=3')
                return a
            x = f(4)\n""")
        assert eval("x") == 4

    def test_nested_names_are_not_confused(self):
        def get_nested_class():
            method_and_var = "var"
            class Test(object):
                def method_and_var(self):
                    return "method"
                def test(self):
                    return method_and_var
                def actual_global(self):
                    return str("global")
                def str(self):
                    return str(self)
            return Test()
        t = get_nested_class()
        assert t.actual_global() == "global"
        assert t.test() == 'var'
        assert t.method_and_var() == 'method'

    def test_exec_load_name(self):
        d = {'x': 2}
        exec("""if 1:
            def f():
                save = x
                exec("x=3")
                return x,save
        \n""", d)
        res = d['f']()
        assert res == (2, 2)

    def test_space_bug(self):
        d = {}
        exec("x=5 ", d)
        assert d['x'] == 5

    def test_synerr(self):
        def x():
            exec("1 2")
        raises(SyntaxError, x)

    def test_mapping_as_locals(self):
        class M(object):
            def __getitem__(self, key):
                return key
            def __setitem__(self, key, value):
                self.result[key] = value
            def setdefault(self, key, value):
                assert key == '__builtins__'
        m = M()
        m.result = {}
        exec("x=m", {}, m)
        assert m.result == {'x': 'm'}
        try:
            exec("y=n", m)
        except TypeError:
            pass
        else:
            assert False, 'Expected TypeError'
        raises(TypeError, eval, "m", m)

    def test_filename(self):
        try:
            exec("'unmatched_quote")
        except SyntaxError as msg:
            assert msg.filename == '<string>', msg.filename
        try:
            eval("'unmatched_quote")
        except SyntaxError as msg:
            assert msg.filename == '<string>', msg.filename

    def test_exec_and_name_lookups(self):
        ns = {}
        exec("""def f():
            exec('x=1', globals())
            return x\n""", ns)

        f = ns['f']

        try:
            res = f()
        except NameError as e: # keep py.test from exploding confused
            raise e

        assert res == 1

    def test_exec_unicode(self):
        # 's' is a bytes string
        s = b"x = '\xd0\xb9\xd1\x86\xd1\x83\xd0\xba\xd0\xb5\xd0\xbd'"
        # 'u' is a unicode
        u = s.decode('utf-8')
        ns = {}
        exec(u, ns)
        x = ns['x']
        assert len(x) == 6
        assert ord(x[0]) == 0x0439
        assert ord(x[1]) == 0x0446
        assert ord(x[2]) == 0x0443
        assert ord(x[3]) == 0x043a
        assert ord(x[4]) == 0x0435
        assert ord(x[5]) == 0x043d

    def test_compile_bytes(self):
        s = b"x = '\xd0\xb9\xd1\x86\xd1\x83\xd0\xba\xd0\xb5\xd0\xbd'"
        c = compile(s, '<input>', 'exec')
        ns = {}
        exec(c, ns)
        x = ns['x']
        assert len(x) == 6
        assert ord(x[0]) == 0x0439

    def test_issue3297(self):
        c = compile("a, b = '\U0001010F', '\\U0001010F'", "dummy", "exec")
        d = {}
        exec(c, d)
        assert d['a'] == d['b']
        assert len(d['a']) == len(d['b'])
        assert d['a'] == d['b']

    def test_locals_call(self):
        l = locals()
        exec("""if 1:
            assert locals() is l
            def f(a):
                exec('a=3')
                return a
            x = f(4)\n""")
        assert eval("locals() is l")
        assert l["x"] == 4

    def test_custom_locals(self):
        class M(object):
            def __getitem__(self, key):
                return self.result[key]
            def __setitem__(self, key, value):
                self.result[key] = value
        m = M()
        m.result = {"m": m, "M": M}
        exec("""if 1:
            assert locals() is m
            def f(a):
                exec('a=3')
                return a
            x = f(4)
            assert locals()["x"] == 4
            x = 12
            assert isinstance(locals(), M)
            assert locals()["x"] == 12\n""", None, m)
        assert eval("locals() is m", None, m)
        assert m["x"] == 12

    def test_locals_is_globals(self):
        exec("assert locals() is globals()", globals())

    def test_custom_locals2(self):
        class M(object):
            def __getitem__(self, key):
                return key
        m = M()
        ns = {}
        exec("global x; x = y", ns, m)
        assert ns["x"] == "y";
        assert eval("x", None, m) == "x"

    def test_exec_encoding(self):
        x = {}
        exec(b'#!/usr/bin/python\n# vim:fileencoding=cp1250\nx = "\x9elu\x9dou\xe8k\xfd k\xf9\xf2"', x)
        assert x['x'] == 'žluťoučký kůň'

    def test_exec_invalid_encoding(self):
        def fn():
            exec(b'# encoding: cp12413254\nx=1')
        raises(SyntaxError, fn)

    def test_exec_ignore_decoded(self):
        x = {}
        exec('# encoding: cp12413254\nx=1', x)
        assert x['x'] == 1

    def test_exec_bom(self):
        x = {}
        exec(b'\xef\xbb\xbfx = "\xe6\xa5\xbd\xe3\x81\x97\xe3\x81\x84"', x)
        assert x['x'] == '楽しい'

    def test_exec_bom_invalid(self):
        def fn():
            exec(b'\xef\xbb\xbf#encoding:latin-1\nx = "\xe9\xa7\x84\xe7\x9b\xae"')
        raises(SyntaxError, fn)
