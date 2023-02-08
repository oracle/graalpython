# Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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

import hashlib
import hmac
import unittest


class HashlibTest(unittest.TestCase):

    def test_messagedigest_update_after_digest(self):
        sha1 = hashlib.sha1()
        sha1.update(b'a')
        self.assertEqual('86f7e437faa5a7fce15d1ddcb9eaeaea377667b8', sha1.hexdigest())
        sha1.update(b'b')
        self.assertEqual('da23614e02469a0d7c7bd1bdab5c9c474b1904dc', sha1.hexdigest())

    def test_mac_update_after_digest(self):
        hm = hmac.new(b'key', digestmod=hashlib.sha256)
        hm.update(b'a')
        self.assertEqual('780c3db4ce3de5b9e55816fba98f590631d96c075271b26976238d5f4444219b', hm.hexdigest())
        hm.update(b'b')
        self.assertEqual('5c1c0c948cbef5ad7d191e46e5901fa0395f85f694fa4633f665a1a637017b65', hm.hexdigest())

    def test_messagedigest_buffer_length_in_constructor(self):
        sha1 = hashlib.sha1(self._get_buffer())
        self.assertEqual('86f7e437faa5a7fce15d1ddcb9eaeaea377667b8', sha1.hexdigest())

    def test_messagedigest_buffer_length_in_update(self):
        sha1 = hashlib.sha1()
        sha1.update(self._get_buffer())
        self.assertEqual('86f7e437faa5a7fce15d1ddcb9eaeaea377667b8', sha1.hexdigest())

    def test_mac_buffer_length_in_constructor(self):
        hm = hmac.new(b'key', self._get_buffer(), digestmod=hashlib.sha256)
        self.assertEqual('780c3db4ce3de5b9e55816fba98f590631d96c075271b26976238d5f4444219b', hm.hexdigest())

    def test_mac_buffer_length_in_update(self):
        hm = hmac.new(b'key', digestmod=hashlib.sha256)
        hm.update(self._get_buffer())
        self.assertEqual('780c3db4ce3de5b9e55816fba98f590631d96c075271b26976238d5f4444219b', hm.hexdigest())

    def test_mac_key_length(self):
        hm = hmac.new(self._get_buffer(), b'data', digestmod=hashlib.sha256)
        self.assertEqual('c449f6626bf7f997cda786d07895f086c2fa18eab25b1c08c4de66a5d46a2a08', hm.hexdigest())

    @staticmethod
    def _get_buffer():
        ba = bytearray(b'ab')
        ba.remove(ord('b'))
        # The capacity of ba should now be different from its length.
        # Also, the rest of the buffer is not filled with binary zeroes - this is important for
        # test_mac_key_length because according to RFC 2104, the key is padded with zeroes anyway.
        # So providing a buffer with capacity > length, but with zero padding as the key argument to HMAC
        # would not trigger the bug.
        return ba


if __name__ == '__main__':
    unittest.main()
