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

import _codecs


def decode(self, encoding="utf-8", errors="strict"):
    """Decode the bytes using the codec registered for encoding.

    encoding
      The encoding with which to decode the bytes.
    errors
      The error handling scheme to use for the handling of decoding errors.
      The default is 'strict' meaning that decoding errors raise a
      UnicodeDecodeError. Other possible values are 'ignore' and 'replace'
      as well as any other name registered with codecs.register_error that
      can handle UnicodeDecodeErrors.
    """
    result = _codecs.decode(self, encoding=encoding, errors=errors)
    if not isinstance(result, str):
        raise TypeError("'%s' encoder returned '%s' instead of 'str'; use codecs.decode() to decode to arbitrary types"
                        % (encoding, type(result).__name__))
    return result


bytes.decode = __graalpython__.builtin_method(decode)


def strip(self, what=None):
    return self.lstrip(what).rstrip(what)


bytes.strip = strip
