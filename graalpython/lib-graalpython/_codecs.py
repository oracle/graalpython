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


__codecs_registry__ = []


def register(search_function):
    if not hasattr(search_function, "__call__"):
        raise TypeError("argument must be callable")
    __codecs_registry__.append(search_function)


def make_base_search_function():
    base_lookup = lookup
    base_encode = encode
    base_decode = decode
    def search_function(encoding):
        if base_lookup(encoding):
            from codecs import CodecInfo
            return CodecInfo(
                lambda data, errors=None: base_encode(data, encoding, errors),
                lambda data, errors=None: base_decode(data, encoding, errors),
                None, # TODO: stream_reader
                None, # TODO: stream_write
            )
    return search_function


register(make_base_search_function())
del make_base_search_function


def lookup(encoding):
    normalized_encoding = encoding.replace(" ", "-").lower()
    for r in __codecs_registry__:
        result = r(normalized_encoding)
        if result:
            if not (isinstance(result, tuple) and len(result) == 4):
                raise TypeError("codec search functions must return 4-tuples %r")
            else:
                return result
    raise LookupError("unknown encoding: %s" % encoding)


def encode(obj, encoding='utf-8', errors='strict'):
    return lookup(encoding)[0](obj, errors)[0]


def decode(obj, encoding='utf-8', errors='strict'):
    return lookup(encoding)[1](obj, errors)[0]
