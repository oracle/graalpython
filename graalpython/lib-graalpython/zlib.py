# Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

class compressobj():
    """Return a compressor object.

    level
      The compression level (an integer in the range 0-9 or -1; default is
      currently equivalent to 6).  Higher compression levels are slower,
      but produce smaller results.
    method
      The compression algorithm.  If given, this must be DEFLATED.
    wbits
      +9 to +15: The base-two logarithm of the window size.  Include a zlib
          container.
      -9 to -15: Generate a raw stream.
      +25 to +31: Include a gzip container.
    memLevel
      Controls the amount of memory used for internal compression state.
      Valid values range from 1 to 9.  Higher values result in higher memory
      usage, faster compression, and smaller output.
    strategy
      Used to tune the compression algorithm.  Possible values are
      Z_DEFAULT_STRATEGY, Z_FILTERED, and Z_HUFFMAN_ONLY.
    zdict
      The predefined compression dictionary - a sequence of bytes
      containing subsequences that are likely to occur in the input data.
    """

    def __new__(cls, level=-1, method=DEFLATED, wbits=MAX_WBITS, memLevel=DEF_MEM_LEVEL, strategy=Z_DEFAULT_STRATEGY, zdict=None):
        instance = object.__new__(cls)
        instance.args = (level, method, wbits, memLevel, strategy, zdict)
        instance.stream = zlib_deflateInit(level, method, wbits, memLevel, strategy, zdict)
        return instance

    def compress(self, data):
        """Returns a bytes object containing compressed data.

        data
          Binary data to be compressed.

        After calling this function, some of the input data may still be stored
        in internal buffers for later processing.  Call the flush() method to
        clear these buffers.
        """
        if self.stream:
            return zlib_deflateCompress(self.stream, data, Z_NO_FLUSH)
        else:
            raise error("compressor object already flushed")

    def copy(self):
        "Return a copy of the compression object."
        return compressobj(*self.args)

    def flush(self, mode=Z_FINISH):
        """Return a bytes object containing any remaining compressed data.

        mode
          One of the constants Z_SYNC_FLUSH, Z_FULL_FLUSH, Z_FINISH.
          If mode == Z_FINISH, the compressor object can no longer be
          used after calling the flush() method.  Otherwise, more data
          can still be compressed.
        """
        if self.stream:
            result = zlib_deflateCompress(self.stream, b"", mode)
            if mode == Z_FINISH:
                self.stream = None
            return result
        else:
            raise error("compressor object already flushed")


class decompressobj():
    """decompressobj([wbits]) -- Return a decompressor object.

    Optional arg wbits is the window buffer size.
    """
    def __new__(cls, wbits=MAX_WBITS, zdict=b""):
        self = object.__new__(cls)
        self.unused_data = b""
        self.unconsumed_tail = b""
        self.eof = False
        self.stream = zlib_inflateInit(wbits, zdict)
        return self

    def decompress(self, data, max_length=0):
        """
        decompress(data[, max_length]) -- Return a string containing the
        decompressed version of the data.

        If the max_length parameter is specified then the return value will be
        no longer than max_length.  Unconsumed input data will be stored in the
        unconsumed_tail attribute.
        """
        if max_length < 0:
            raise ValueError("max_length must be greater than zero")
        result, self.eof, unused_len = zlib_inflateDecompress(self.stream, data, max_length)
        tail = data[len(data) - unused_len:]
        if self.eof:
            self.unconsumed_tail = b""
            self.unused_data += tail
        else:
            self.unconsumed_tail = tail

        return result

    def flush(self, length=None):
        try:
            return decompress(self.unconsumed_tail)
        except error:
            return b""
