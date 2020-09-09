# Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

# This module is split out from "_codecs" to avoid circular dependency with "codecs"
import _codecs
import codecs


class TruffleCodec(codecs.Codec):
    def __init__(self, encoding):
        self.encoding = encoding

    def encode(self, input, errors='strict'):
        return _codecs.__truffle_encode(input, self.encoding, errors)

    def decode(self, input, errors='strict'):
        return _codecs.__truffle_decode(input, self.encoding, errors, True)


# TODO - the incremental codec and reader/writer won't work well with stateful encodings, like some of the CJK encodings
class TruffleIncrementalEncoder(codecs.IncrementalEncoder):
    def __init__(self, encoding, *args, **kwargs):
        super().__init__(*args, **kwargs)
        self.encoding = encoding

    def encode(self, input, final=False):
        return _codecs.__truffle_encode(input, self.encoding, self.errors)[0]


class TruffleIncrementalDecoder(codecs.BufferedIncrementalDecoder):
    def __init__(self, encoding, *args, **kwargs):
        super().__init__(*args, **kwargs)
        self.encoding = encoding

    def _buffer_decode(self, input, errors, final):
        return _codecs.__truffle_decode(input, self.encoding, errors, final)


class TruffleStreamWriter(codecs.StreamWriter):
    def __init__(self, encoding, *args, **kwargs):
        super().__init__(*args, **kwargs)
        self.encoding = encoding

    def encode(self, input, errors='strict'):
        return _codecs.__truffle_encode(input, self.encoding, errors)


class TruffleStreamReader(codecs.StreamReader):
    def __init__(self, encoding, *args, **kwargs):
        super().__init__(*args, **kwargs)
        self.encoding = encoding

    def decode(self, input, errors='strict'):
        return _codecs.__truffle_decode(input, self.encoding, errors)


class apply_encoding:
    def __init__(self, fn, encoding):
        self.fn = fn
        self.encoding = encoding

    def __call__(self, *args, **kwargs):
        return self.fn(self.encoding, *args, **kwargs)


def codec_info_for_truffle(encoding):
    return codecs.CodecInfo(
        name=encoding,
        encode=TruffleCodec(encoding).encode,
        decode=TruffleCodec(encoding).decode,
        incrementalencoder=apply_encoding(TruffleIncrementalEncoder, encoding),
        incrementaldecoder=apply_encoding(TruffleIncrementalDecoder, encoding),
        streamreader=apply_encoding(TruffleStreamReader, encoding),
        streamwriter=apply_encoding(TruffleStreamWriter, encoding),
    )
