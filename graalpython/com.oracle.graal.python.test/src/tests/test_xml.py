# Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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

import unittest
from xml.parsers import expat


class PyExpatXmlTest(unittest.TestCase):

    def test_xml_decl_handler_not_called_without_xml_declaration(self):
        parser = expat.ParserCreate()
        xml_decl_calls = []

        parser.XmlDeclHandler = lambda *args: xml_decl_calls.append(args)
        parser.Parse(b"<doc/>", True)

        self.assertEqual([], xml_decl_calls)

    def test_parser_create_encoding_is_used_for_byte_input(self):
        parser = expat.ParserCreate(encoding="iso-8859-1")
        character_data = []

        parser.CharacterDataHandler = character_data.append
        parser.Parse(b"<doc>\xe9</doc>", True)

        self.assertEqual(["\xe9"], character_data)

    def test_external_doctype_reports_no_internal_subset(self):
        parser = expat.ParserCreate()
        doctype_calls = []

        parser.StartDoctypeDeclHandler = lambda *args: doctype_calls.append(args)
        parser.ExternalEntityRefHandler = lambda *args: 1
        parser.Parse(b'<!DOCTYPE doc SYSTEM "x.dtd"><doc/>', True)

        self.assertEqual([("doc", "x.dtd", None, 0)], doctype_calls)


if __name__ == '__main__':
    unittest.main()
