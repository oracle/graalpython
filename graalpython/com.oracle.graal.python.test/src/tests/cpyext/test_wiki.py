# Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
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

from . import CPyExtTestCase, compile_module_from_file


class TestWiki(CPyExtTestCase):

    def test_noddy(self):
        noddy = compile_module_from_file('noddy')
        assert type(noddy.Noddy) is type
        assert type(noddy.Noddy()) is noddy.Noddy, str(type(noddy.Noddy()))
        assert str(noddy.Noddy()).startswith("<noddy.Noddy object at"), str(noddy.Noddy())

    def test_noddy2(self):
        noddy2 = compile_module_from_file('noddy2')
        nd = noddy2.Noddy2("First", "Last", 42)
        assert nd.name() == "'First' 'Last' '42'"
        assert nd.first == "First", ("%s != First" % nd.first)
        assert nd.last == "Last"
        assert nd.number == 42

        nd.first = "Hello"
        nd.last = "World"
        nd.number = 1234
        nd.n_short = 0x7fff
        nd.n_long = 0x7fffffffffffffff
        nd.n_float = 0.1234
        nd.n_double = 0.123456789432634
        nd.c = 'z'
        nd.n_byte = 0x7f
        nd.n_ubyte = 0xff
        nd.n_ushort = 0xffff
        nd.n_unumber = 0xffffffff
        nd.n_ulong = 0xffffffffffffffff

        assert nd.first == "Hello", ("%s != Hello" % nd.first)
        assert nd.last == "World", ("%s != World" % nd.last)
        assert nd.number == 1234, ("%s != 1234" % nd.number)
        assert nd.n_short == 0x7fff, ("%s != 32767" % nd.n_short)
        assert nd.n_long == 0x7fffffffffffffff, ("%s != 0x7fffffffffffffff" % nd.n_long)
        assert 0.12341 - nd.n_float < 0.00001 , ("%s != 0.1234" % nd.n_float)
        assert 0.123456789432634 - nd.n_double < 0.0000000000000001, ("%s != 0.123456789432634" % nd.n_double)
        # assert nd.c == 'z', ("%s != 'z'" % nd.c)
        assert nd.n_byte == 0x7f, ("%s != 127" % nd.n_byte)
        assert nd.n_ubyte == 0xff, ("%s != 255" % nd.n_ubyte)
        assert nd.n_ushort == 0xffff, ("%s != 65535" % nd.n_ushort)
        assert nd.n_unumber == 0xffffffff, ("%s != 0xffffffff" % nd.n_unumber)
        assert nd.n_ulong == 0xffffffffffffffff, ("%s != 0xffffffffffffffff" % nd.n_ulong)

    def test_noddy3(self):
        noddy3 = compile_module_from_file('noddy3')
        nd = noddy3.Noddy(b"First", b"Last", 42)
        assert nd.name() == "b'First' b'Last'", nd.name()
        assert nd.first == b"First", ("%s != First" % nd.first)
        assert nd.last == b"Last"
        assert nd.number == 42
