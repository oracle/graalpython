# Copyright (c) 2019, 2022, Oracle and/or its affiliates. All rights reserved.
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
import csv
from tempfile import TemporaryFile

class TestUnicodeCharacters(unittest.TestCase):
    def test_read_utf_32_delimiter(self):
        test_data = ['a\U0001F642b']
        reader = csv.reader(test_data, delimiter="\U0001F642")
        self.assertEqual(list(reader), [['a', 'b']])

    def test_read_utf_32_field(self):
        test_data = ['a,\U0001F642,b']
        reader = csv.reader(test_data)
        self.assertEqual(list(reader), [['a', '\U0001F642', 'b']])

    def test_write_utf32_field(self):
        with TemporaryFile("w+", newline='') as fileobj:
            writer = csv.writer(fileobj)
            writer.writerow(['a', '\U0001F642','b'])

            expected = 'a,\U0001F642,b'
            fileobj.seek(0)

            self.assertEqual(fileobj.read(),
                             expected + writer.dialect.lineterminator)

    def test_write_utf32_field_with_utf32_delimiter(self):
        with TemporaryFile("w+", newline='') as fileobj:
            writer = csv.writer(fileobj, delimiter = '\U0001F642')
            writer.writerow(['a', '\U0001F642','b'])

            expected = 'a\U0001F642"\U0001F642"\U0001F642b'
            fileobj.seek(0)

            self.assertEqual(fileobj.read(),
                             expected + writer.dialect.lineterminator)





