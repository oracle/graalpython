# Copyright (c) 2019, 2021, Oracle and/or its affiliates.
# Copyright (C) 1996-2017 Python Software Foundation
#
# Licensed under the PYTHON SOFTWARE FOUNDATION LICENSE VERSION 2
import sys


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

    assert codecs.decode(b'\xe4\xf6\xfc', 'latin-1') == '\xe4\xf6\xfc'
    assert_raises(TypeError, codecs.decode)
    assert codecs.decode(b'abc') == 'abc'
    assert_raises(UnicodeDecodeError, codecs.decode, b'\xff', 'ascii')

    # test keywords
    assert codecs.decode(obj=b'\xe4\xf6\xfc', encoding='latin-1') == '\xe4\xf6\xfc'
    assert codecs.decode(b'[\xff]', 'ascii', errors='ignore') == '[]'
    assert codecs.decode(b'[]', 'ascii') == '[]'
    assert codecs.decode(memoryview(b'[]'), 'ascii') == '[]'
    assert_raises(TypeError, codecs.decode, 'asdf', 'ascii')

    data0 = b'\xc5'
    data1 = b'\x91'
    assert codecs.utf_8_decode(data0, "strict") == ('', 0)
    assert codecs.utf_8_decode(data0 + data1, "strict") == ('ő', 2)
    assert_raises(UnicodeDecodeError, codecs.utf_8_decode, data0, "strict", True)


def test_encode():
    import codecs

    assert codecs.encode('\xe4\xf6\xfc', 'latin-1') == b'\xe4\xf6\xfc'
    assert_raises(TypeError, codecs.encode)
    assert_raises(LookupError, codecs.encode, "foo", "__spam__")
    assert codecs.encode('abc') == b'abc'
    assert_raises(UnicodeEncodeError, codecs.encode, '\xffff', 'ascii')

    # test keywords
    assert codecs.encode(obj='\xe4\xf6\xfc', encoding='latin-1') == b'\xe4\xf6\xfc'
    assert codecs.encode('[\xff]', 'ascii', errors='ignore') == b'[]'
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
        check('\U0001d120', br'\U0001d120')

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


class EscapeEncodeTest(unittest.TestCase):

    def test_escape_encode(self):
        tests = [
            (b'', (b'', 0)),
            (b'foobar', (b'foobar', 6)),
            (b'spam\0eggs', (b'spam\\x00eggs', 9)),
            (b'a\'b', (b"a\\'b", 3)),
            (b'b\\c', (b'b\\\\c', 3)),
            (b'c\nd', (b'c\\nd', 3)),
            (b'd\re', (b'd\\re', 3)),
            (b'f\x7fg', (b'f\\x7fg', 3)),
        ]
        for data, output in tests:
            self.assertEqual(codecs.escape_encode(data), output)
        self.assertRaises(TypeError, codecs.escape_encode, 'spam')
        self.assertRaises(TypeError, codecs.escape_encode, bytearray(b'spam'))


class EscapeDecodeTest(unittest.TestCase):
    def test_empty(self):
        self.assertEqual(codecs.escape_decode(b""), (b"", 0))
        self.assertEqual(codecs.escape_decode(bytearray()), (b"", 0))

    def test_raw(self):
        decode = codecs.escape_decode
        for b in range(256):
            b = bytes([b])
            if b != b'\\':
                self.assertEqual(decode(b + b'0'), (b + b'0', 2))

    def test_escape(self):
        decode = codecs.escape_decode
        check = coding_checker(self, decode)
        check(b"[\\\n]", b"[]")
        check(br'[\"]', b'["]')
        check(br"[\']", b"[']")
        check(br"[\\]", b"[\\]")
        check(br"[\a]", b"[\x07]")
        check(br"[\b]", b"[\x08]")
        check(br"[\t]", b"[\x09]")
        check(br"[\n]", b"[\x0a]")
        check(br"[\v]", b"[\x0b]")
        check(br"[\f]", b"[\x0c]")
        check(br"[\r]", b"[\x0d]")
        check(br"[\7]", b"[\x07]")
        check(br"[\78]", b"[\x078]")
        check(br"[\41]", b"[!]")
        check(br"[\418]", b"[!8]")
        check(br"[\101]", b"[A]")
        check(br"[\1010]", b"[A0]")
        check(br"[\501]", b"[A]")
        check(br"[\x41]", b"[A]")
        check(br"[\x410]", b"[A0]")
        for i in range(97, 123):
            b = bytes([i])
            if b not in b'abfnrtvx':
                check(b"\\" + b, b"\\" + b)
            check(b"\\" + b.upper(), b"\\" + b.upper())
        check(br"\8", b"\\8")
        check(br"\9", b"\\9")
        check(b"\\\xfa", b"\\\xfa")

    def test_errors(self):
        decode = codecs.escape_decode
        self.assertRaises(ValueError, decode, br"\x")
        self.assertRaises(ValueError, decode, br"[\x]")
        self.assertEqual(decode(br"[\x]\x", "ignore"), (b"[]", 6))
        self.assertEqual(decode(br"[\x]\x", "replace"), (b"[?]?", 6))
        self.assertRaises(ValueError, decode, br"\x0")
        self.assertRaises(ValueError, decode, br"[\x0]")
        self.assertEqual(decode(br"[\x0]\x0", "ignore"), (b"[]", 8))
        self.assertEqual(decode(br"[\x0]\x0", "replace"), (b"[?]?", 8))

class LookupTest(unittest.TestCase):
    def test_lookup(self):
        self.assertEqual(codecs.lookup('UTF-8').name, "utf-8")

    def test_lookup_error(self):
        def errhandler():
            pass
        self.assertRaises(TypeError, codecs.register_error, 1)
        self.assertRaises(TypeError, codecs.register_error, 'a', 1)
        self.assertRaises(LookupError, codecs.lookup_error, 'a')
        codecs.register_error('testhandler', errhandler)
        self.assertEqual(codecs.lookup_error('testhandler'), errhandler)

    def test_codecs_builtins(self):
        s = "abc"

        encoded = codecs.utf_8_encode(s)
        self.assertEqual(s, codecs.utf_8_decode(encoded[0])[0])

        encoded = codecs.utf_7_encode(s)
        self.assertEqual(s, codecs.utf_7_decode(encoded[0])[0])

        encoded = codecs.utf_16_encode(s)
        self.assertEqual(s, codecs.utf_16_decode(encoded[0])[0])

        encoded = codecs.utf_16_le_encode(s)
        self.assertEqual(s, codecs.utf_16_le_decode(encoded[0])[0])

        encoded = codecs.utf_16_be_encode(s)
        self.assertEqual(s, codecs.utf_16_be_decode(encoded[0])[0])

        encoded = codecs.utf_32_encode(s)
        self.assertEqual(s, codecs.utf_32_decode(encoded[0])[0])

        encoded = codecs.utf_32_le_encode(s)
        self.assertEqual(s, codecs.utf_32_le_decode(encoded[0])[0])

        encoded = codecs.utf_32_be_encode(s)
        self.assertEqual(s, codecs.utf_32_be_decode(encoded[0])[0])

        encoded = codecs.utf_32_be_encode(s)
        self.assertEqual(s, codecs.utf_32_be_decode(encoded[0])[0])

        encoded = codecs.raw_unicode_escape_encode(s)
        self.assertEqual(s, codecs.raw_unicode_escape_decode(encoded[0])[0])

        encoded = codecs.unicode_escape_encode(s)
        self.assertEqual(s, codecs.unicode_escape_decode(encoded[0])[0])

        encoded = codecs.latin_1_encode(s)
        self.assertEqual(s, codecs.latin_1_decode(encoded[0])[0])

        encoded = codecs.ascii_encode(s)
        self.assertEqual(s, codecs.ascii_decode(encoded[0])[0])


class UTFByteOrderTest(unittest.TestCase):
    def test_utf16_byteorder(self):
        self.assertEqual("😂".encode("utf-16-le"), b'=\xd8\x02\xde')
        self.assertEqual("😂".encode("utf-16-be"), b'\xd8=\xde\x02')
        if sys.byteorder == 'little':
            self.assertEqual("😂".encode("utf-16"), b'\xff\xfe=\xd8\x02\xde')
        else:
            self.assertEqual("😂".encode("utf-16"), b'\xfe\xff\xd8=\xde\x02')
        self.assertEqual(b'=\xd8\x02\xde'.decode('utf-16-le'), "😂")
        self.assertEqual(b'\xd8=\xde\x02'.decode('utf-16-be'), "😂")
        self.assertEqual(b'\xff\xfe=\xd8\x02\xde'.decode('utf-16'), "😂")
        self.assertEqual(b'\xfe\xff\xd8=\xde\x02'.decode('utf-16'), "😂")
        if sys.byteorder == 'little':
            self.assertEqual(b'=\xd8\x02\xde'.decode('utf-16'), "😂")
        else:
            self.assertEqual(b'\xd8=\xde\x02'.decode('utf-16'), "😂")

    def test_utf32_byteorder(self):
        self.assertEqual("😂".encode("utf-32-le"), b'\x02\xf6\x01\x00')
        self.assertEqual("😂".encode("utf-32-be"), b'\x00\x01\xf6\x02')
        if sys.byteorder == 'little':
            self.assertEqual("😂".encode("utf-32"), b'\xff\xfe\x00\x00\x02\xf6\x01\x00')
        else:
            self.assertEqual("😂".encode("utf-32"), b'\x00\x00\xfe\xff\xd8=\xde\x02')
        self.assertEqual(b'\x02\xf6\x01\x00'.decode('utf-32-le'), "😂")
        self.assertEqual(b'\x00\x01\xf6\x02'.decode('utf-32-be'), "😂")
        self.assertEqual(b'\xff\xfe\x00\x00\x02\xf6\x01\x00'.decode('utf-32'), "😂")
        self.assertEqual(b'\x00\x00\xfe\xff\x00\x01\xf6\x02'.decode('utf-32'), "😂")
        if sys.byteorder == 'little':
            self.assertEqual(b'\x02\xf6\x01\x00'.decode('utf-32'), "😂")
        else:
            self.assertEqual(b'\x00\x01\xf6\x02'.decode('utf-32'), "😂")
