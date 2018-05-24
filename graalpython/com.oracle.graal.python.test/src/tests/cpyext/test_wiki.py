# Copyright (c) 2018, Oracle and/or its affiliates.
#
# The Universal Permissive License (UPL), Version 1.0
#
# Subject to the condition set forth below, permission is hereby granted to any
# person obtaining a copy of this software, associated documentation and/or data
# (collectively the "Software"), free of charge and under any and all copyright
# rights in the Software, and any and all patent rights owned or freely
# licensable by each licensor hereunder covering either (i) the unmodified
# Software as contributed to or provided by such licensor, or (ii) the Larger
# Works (as defined below), to deal in both
#
# (a) the Software, and
# (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
#     one is included with the Software (each a "Larger Work" to which the
#     Software is contributed by such licensors),
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

from . import CPyExtTestCase, GRAALPYTHON


class TestWiki(CPyExtTestCase):
    def test_noddy(self):
        from . import noddy
        assert type(noddy.Noddy) is type
        assert type(noddy.Noddy()) is noddy.Noddy, str(type(noddy.Noddy()))
        assert str(noddy.Noddy()).startswith("<noddy.Noddy object at"), str(noddy.Noddy())

    def test_noddy2(self):
        from . import noddy2
        nd = noddy2.Noddy2("First", "Last", 42)
        assert nd.name() == "'First' 'Last' '42'"
        assert nd.first == "First", ("%s != First" % nd.first)
        assert nd.last == "Last"
        assert nd.number == 42

    def test_noddy3(self):
        from . import noddy3
        nd = noddy3.Noddy(b"First", b"Last", 42)
        assert nd.name() == "b'First' b'Last'", nd.name()
        assert nd.first == b"First", ("%s != First" % nd.first)
        assert nd.last == b"Last"
        assert nd.number == 42
