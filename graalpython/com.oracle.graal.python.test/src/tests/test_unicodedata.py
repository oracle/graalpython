# Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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

import unicodedata
import unittest

class TestUnicodedata(unittest.TestCase):

    def test_args_validation(self):
        self.assertRaises(TypeError, unicodedata.category, None)
        self.assertRaises(TypeError, unicodedata.bidirectional, None)
        self.assertRaises(TypeError, unicodedata.name, None)


    def test_normalize(self):
        self.assertRaises(TypeError, unicodedata.normalize)
        self.assertRaises(ValueError, unicodedata.normalize, 'unknown', 'xx')
        assert unicodedata.normalize('NFKC', '') == ''


    def test_category(self):
        assert unicodedata.category('\uFFFE') == 'Cn'
        assert unicodedata.category('a') == 'Ll'
        assert unicodedata.category('A') == 'Lu'
        self.assertRaises(TypeError, unicodedata.category)
        self.assertRaises(TypeError, unicodedata.category, 'xx')


    def test_lookup(self):
        unicode_name = "ARABIC SMALL HIGH LIGATURE ALEF WITH LAM WITH YEH"
        self.assertEqual(unicodedata.lookup(unicode_name), "\u0616")

        unicode_name_alias = "ARABIC SMALL HIGH LIGATURE ALEF WITH YEH BARREE"
        self.assertEqual(unicodedata.lookup(unicode_name_alias), "\u0616")

        with self.assertRaisesRegex(KeyError, "undefined character name 'wrong-name'"):
            unicodedata.lookup("wrong-name")

        with self.assertRaisesRegex(KeyError, "name too long"):
            unicodedata.lookup("a" * 257)


    def test_east_asian_width(self):
        list = [1, 2, 3]
        with self.assertRaisesRegex(TypeError, r"east_asian_width\(\) argument must be a unicode character, not list"):
            unicodedata.east_asian_width(list)

        multi_character_string = "abc"
        with self.assertRaisesRegex(TypeError, r"east_asian_width\(\) argument must be a unicode character, not str"):
            unicodedata.east_asian_width(multi_character_string)

        empty_string = ""
        with self.assertRaisesRegex(TypeError, r"east_asian_width\(\) argument must be a unicode character, not str"):
            unicodedata.east_asian_width(empty_string)


    def test_combining(self):
        list = [1, 2, 3]
        with self.assertRaisesRegex(TypeError, r"combining\(\) argument must be a unicode character, not list"):
            unicodedata.combining(list)

        multi_character_string = "abc"
        with self.assertRaisesRegex(TypeError, r"combining\(\) argument must be a unicode character, not str"):
            unicodedata.combining(multi_character_string)

        empty_string = ""
        with self.assertRaisesRegex(TypeError, r"combining\(\) argument must be a unicode character, not str"):
            unicodedata.combining(empty_string)