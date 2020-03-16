# Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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


__codec_search_path__ = []
__codec_search_cache__ = {}
__codec_error_registry__ = {}


@__builtin__
def register(search_function):
    if not hasattr(search_function, "__call__"):
        raise TypeError("argument must be callable")
    __codec_search_path__.append(search_function)


def __normalizestring(string):
    return string.replace(' ', '-').lower()


@__builtin__
def lookup(encoding):
    normalized_encoding = __normalizestring(encoding)
    # First, try to lookup the name in the registry dictionary
    result = __codec_search_cache__.get(normalized_encoding)
    if result:
        return result

    # Next, scan the search functions in order of registration
    for func in __codec_search_path__:
        result = func(normalized_encoding)
        if result:
            if not (isinstance(result, tuple) and len(result) == 4):
                raise TypeError("codec search functions must return 4-tuples %r")
            break

    if result:
        # Cache and return the result
        __codec_search_cache__[normalized_encoding] = result
        return result

    raise LookupError("unknown encoding: %s" % encoding)


def _forget_codec(encoding):
    normalized_encoding = __normalizestring(encoding)
    return __codec_search_cache__.pop(normalized_encoding)


def __codec_getitem(encoding, index):
    return lookup(encoding)[index]


def __encoder(encoding):
    return __codec_getitem(encoding, 0)


def __decoder(encoding):
    return __codec_getitem(encoding, 1)


@__builtin__
def encode(obj, encoding='utf-8', errors='strict'):
    encoder = __encoder(encoding)
    if encoder:
        result = encoder(obj, errors)
        if not (isinstance(result, tuple) and len(result) == 2):
            raise TypeError('encoder must return a tuple (object, integer)')
        return result[0]


@__builtin__
def decode(obj, encoding='utf-8', errors='strict'):
    decoder = __decoder(encoding)
    if decoder:
        result = decoder(obj, errors)
        if not (isinstance(result, tuple) and len(result) == 2):
            raise TypeError('decoder must return a tuple (object, integer)')
        return result[0]


@__builtin__
def register_error(errors, handler):
    if not hasattr(handler, '__call__'):
        raise TypeError('handler must be callable')
    __codec_error_registry__[errors] = handler


@__builtin__
def lookup_error(errors='strict'):
    handler = __codec_error_registry__.get(errors)
    if handler is None:
        raise LookupError('unknown error handler name %s'.format(errors))
    return handler


@__builtin__
def escape_encode(data, errors=None):
    return unicode_escape_encode(data, errors)


@__builtin__
def escape_decode(data, errors=None):
    return unicode_escape_decode(data, errors)


@__builtin__
def utf_8_encode(string, errors=None):
    return __truffle_encode(string, "utf-8", errors)


@__builtin__
def utf_8_decode(string, errors=None, final=False):
    return __truffle_decode(string, "utf-8", errors, final)


@__builtin__
def utf_7_encode(string, errors=None):
    return __truffle_encode(string, "utf-7", errors)


@__builtin__
def utf_7_decode(string, errors=None, final=False):
    return __truffle_decode(string, "utf-7", errors, final)


@__builtin__
def utf_16_encode(string, errors=None, byteorder=0):
    return __truffle_encode(string, "utf-16", errors)


@__builtin__
def utf_16_decode(string, errors=None, final=False):
    return __truffle_decode(string, "utf-16", errors, final)


@__builtin__
def utf_16_le_encode(string, errors=None):
    return __truffle_encode(string, "utf-16-le", errors)


@__builtin__
def utf_16_le_decode(string, errors=None, final=False):
    return __truffle_decode(string, "utf-16-le", errors, final)


@__builtin__
def utf_16_be_encode(string, errors=None):
    return __truffle_encode(string, "utf-16-be", errors)


@__builtin__
def utf_16_be_decode(string, errors=None, final=False):
    return __truffle_decode(string, "utf-16-be", errors, final)


@__builtin__
def utf_16_ex_decode(data, errors=None, byteorder=0, final=False):
    raise NotImplementedError("utf_16_ex_decode")


@__builtin__
def utf_32_encode(string, errors=None, byteorder=0):
    return __truffle_encode(string, "utf-32", errors)


@__builtin__
def utf_32_decode(string, errors=None, final=False):
    return __truffle_decode(string, "utf-32", errors, final)


@__builtin__
def utf_32_le_encode(string, errors=None):
    return __truffle_encode(string, "utf-32-le", errors)


@__builtin__
def utf_32_le_decode(string, errors=None, final=False):
    return __truffle_decode(string, "utf-32-le", errors, final)


@__builtin__
def utf_32_be_encode(string, errors=None):
    return __truffle_encode(string, "utf-32-be", errors)


@__builtin__
def utf_32_be_decode(string, errors=None, final=False):
    return __truffle_decode(string, "utf-32-be", errors, final)


@__builtin__
def utf_32_ex_decode(data, errors=None, byteorder=0, final=False):
    raise NotImplementedError("utf_32_ex_decode")


@__builtin__
def unicode_internal_encode(obj, errors=None):
    raise NotImplementedError("unicode_internal_encode")


@__builtin__
def unicode_internal_decode(obj, errors=None):
    raise NotImplementedError("unicode_internal_decode")


@__builtin__
def raw_unicode_escape_encode(string, errors=None):
    return __truffle_raw_encode(string, errors)


@__builtin__
def raw_unicode_escape_decode(string, errors=None):
    return __truffle_raw_decode(string, errors)


@__builtin__
def latin_1_encode(string, errors=None):
    return __truffle_encode(string, "latin-1", errors)


@__builtin__
def latin_1_decode(string, errors=None):
    return __truffle_decode(string, "latin-1", errors)


@__builtin__
def ascii_encode(string, errors=None):
    return __truffle_encode(string, "ascii", errors)


@__builtin__
def ascii_decode(string, errors=None):
    return __truffle_decode(string, "ascii", errors)


@__builtin__
def charmap_encode(string, errors=None, mapping=None):
    return __truffle_encode(string, "cp437", errors)


@__builtin__
def charmap_decode(string, errors=None, mapping=None):
    return __truffle_decode(string, "cp437", errors)


@__builtin__
def readbuffer_encode(data, errors=None):
    raise NotImplementedError("readbuffer_encode")


@__builtin__
def mbcs_encode(string, errors=None):
    raise NotImplementedError("mbcs_encode")


@__builtin__
def mbcs_decode(string, errors=None, final=False):
    raise NotImplementedError("mbcs_decode")


@__builtin__
def oem_encode(string, errors):
    raise NotImplementedError("oem_encode")


@__builtin__
def oem_decode(string, errors=None, final=False):
    raise NotImplementedError("oem_decode")


@__builtin__
def code_page_encode(code_page, string, errors=None):
    raise NotImplementedError("code_page_encode")


@__builtin__
def code_page_decode(code_page, string, errors=None, final=False):
    raise NotImplementedError("code_page_decode")


# at this point during context startup, sys.path isn't initialized, so we need
# to set it up
import sys
sys.path.append(sys.graal_python_stdlib_home)
try:
    import encodings
    # we import the below two encodings, because they are often used so it's
    # useful to have them available preloaded
    import encodings.ascii
    import encodings.utf_8
finally:
    assert len(sys.path) == 1
    sys.path.pop()
