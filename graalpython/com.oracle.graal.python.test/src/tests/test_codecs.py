# Copyright (c) 2019, Oracle and/or its affiliates.
# Copyright (C) 1996-2017 Python Software Foundation
#
# Licensed under the PYTHON SOFTWARE FOUNDATION LICENSE VERSION 2


def coding_checker(self, coder):
    def check(input, expect):
        self.assertEqual(coder(input), (expect, len(input)))
    return check


def assert_raises(err, fn, *args, **kwargs):
    raised = False
    try:
        fn(*args, **kwargs)
    except err:
        raised = True
    assert raised


def test_import():
    imported = True
    try:
        import codecs
    except ImportError:
        imported = False
    assert imported


def test_decode():
    import codecs

    # TODO: this does not work yet due to the fact that we do not handle all strings literal types yet
    # assert codecs.decode(b'\xe4\xf6\xfc', 'latin-1') == '\xe4\xf6\xfc'
    # assert_raises(TypeError, codecs.decode)
    assert codecs.decode(b'abc') == 'abc'
    # assert_raises(UnicodeDecodeError, codecs.decode, b'\xff', 'ascii')

    # test keywords
    # assert codecs.decode(obj=b'\xe4\xf6\xfc', encoding='latin-1') == '\xe4\xf6\xfc'
    # assert codecs.decode(b'[\xff]', 'ascii', errors='ignore') == '[]'
    assert codecs.decode(b'[]', 'ascii') == '[]'

    data0 = b'\xc5'
    data1 = b'\x91'
    assert codecs.utf_8_decode(data0, "strict") == ('', 0)
    assert codecs.utf_8_decode(data0 + data1, "strict") == ('ő', 2)
    assert_raises(UnicodeDecodeError, codecs.utf_8_decode, data0, "strict", True)


def test_encode():
    import codecs

    # TODO: this does not work yet due to the fact that we do not handle all strings literal types yet
    # assert codecs.encode('\xe4\xf6\xfc', 'latin-1') == b'\xe4\xf6\xfc'
    # assert_raises(TypeError, codecs.encode)
    assert_raises(LookupError, codecs.encode, "foo", "__spam__")
    # assert codecs.encode('abc') == b'abc'
    # assert_raises(UnicodeEncodeError, codecs.encode, '\xffff', 'ascii')

    # test keywords
    # assert codecs.encode(obj='\xe4\xf6\xfc', encoding='latin-1') == b'\xe4\xf6\xfc'
    # assert codecs.encode('[\xff]', 'ascii', errors='ignore') == b'[]'
    assert codecs.encode('[]', 'ascii') == b'[]'


import codecs
import unittest

### Codec APIs

class Codec(codecs.Codec):

    def encode(self,input,errors='strict'):
        return list(input), len(input)

    def decode(self,input,errors='strict'):
        return str(list(input)), len(input)

class StreamWriter(Codec,codecs.StreamWriter):
    pass

class StreamReader(Codec,codecs.StreamReader):
    pass

### encodings module API

def getregentry():

    return (Codec().encode,Codec().decode,StreamReader,StreamWriter)

### Decoding Map

decoding_map = codecs.make_identity_dict(range(256))
decoding_map.update({
        0x78: "abc", # 1-n decoding mapping
        b"abc": 0x0078,# 1-n encoding mapping
        0x01: None,   # decoding mapping to <undefined>
        0x79: "",    # decoding mapping to <remove character>
})

### Encoding Map

encoding_map = {}
for k,v in decoding_map.items():
    encoding_map[v] = k


# Register a search function which knows about our codec
def codec_search_function(encoding):
    if encoding == 'testcodec':
        return tuple(getregentry())
    return None

codecs.register(codec_search_function)

# test codec's name (see test/testcodec.py)
codecname = 'testcodec'

class CharmapCodecTest(unittest.TestCase):
    def test_constructorx(self):
        self.assertEqual(codecs.decode(b'abc', codecname), str(list(b'abc')))
        self.assertEqual(b'abc'.decode(codecname), str(list(b'abc')))

    def test_encodex(self):
        self.assertEqual(codecs.encode('abc', codecname), list('abc'))


class UnicodeEscapeTest(unittest.TestCase):
    def test_empty(self):
        self.assertEqual(codecs.unicode_escape_encode(""), (b"", 0))
        self.assertEqual(codecs.unicode_escape_decode(b""), ("", 0))

    def test_raw_encode(self):
        encode = codecs.unicode_escape_encode
        for b in range(32, 127):
            if b != b'\\'[0]:
                self.assertEqual(encode(chr(b)), (bytes([b]), 1))

    def test_escape_encode(self):
        encode = codecs.unicode_escape_encode
        check = coding_checker(self, encode)
        check('\t', br'\t')
        check('\n', br'\n')
        check('\r', br'\r')
        check('\\', br'\\')
        for b in range(32):
            if chr(b) not in '\t\n\r':
                check(chr(b), ('\\x%02x' % b).encode())
        for b in range(127, 256):
            check(chr(b), ('\\x%02x' % b).encode())
        check('\u20ac', br'\u20ac')
        # TODO Truffle: not working yet
        # check('\U0001d120', br'\U0001d120')

    def test_escape_decode(self):
        decode = codecs.unicode_escape_decode
        check = coding_checker(self, decode)
        check(b"[\\\n]", "[]")
        check(br'[\"]', '["]')
        check(br"[\']", "[']")
        check(br"[\\]", r"[\]")
        check(br"[\a]", "[\x07]")
        check(br"[\b]", "[\x08]")
        check(br"[\t]", "[\x09]")
        check(br"[\n]", "[\x0a]")
        check(br"[\v]", "[\x0b]")
        check(br"[\f]", "[\x0c]")
        check(br"[\r]", "[\x0d]")
        check(br"[\7]", "[\x07]")
        check(br"[\78]", "[\x078]")
        check(br"[\41]", "[!]")
        check(br"[\418]", "[!8]")
        check(br"[\101]", "[A]")
        check(br"[\1010]", "[A0]")
        check(br"[\x41]", "[A]")
        check(br"[\x410]", "[A0]")
        check(br"\u20ac", "\u20ac")
        check(br"\U0001d120", "\U0001d120")


def test_charmap_build():
    import codecs
    assert codecs.charmap_build(u'123456') == {49: 0, 50: 1, 51: 2,
                                               52: 3, 53: 4, 54: 5}


def test_decode_report_consumed():
    data = bytes('memory of “unsigned bytes” of the given length.', 'utf-8')
    dec = data.decode('utf-8')
    assert dec == "memory of “unsigned bytes” of the given length."
    assert len(data) == 51 and len(dec) == 47
    dec, consumed = codecs.utf_8_decode(data)
    assert dec == "memory of “unsigned bytes” of the given length."
    assert consumed == len(data)
