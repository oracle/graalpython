# Copyright (c) 2019, 2024, Oracle and/or its affiliates.
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
        check('\U0001d120A', br'\U0001d120A')
        check('\ud800', b'\\ud800')
        check('\ud800A', b'\\ud800A')

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


class DefaultErrorHandlerTest(unittest.TestCase):
    def test_default_registration(self):
        import _codecs
        for name in ('strict_errors', 'ignore_errors', 'replace_errors', 'xmlcharrefreplace_errors', 'backslashreplace_errors', 'namereplace_errors', 'surrogatepass', 'surrogateescape'):
            self.assertRaises(AttributeError, getattr, _codecs, name)

    def test_strict(self):
        handler = codecs.lookup_error('strict')
        self.assertRaisesRegex(TypeError, 'codec must pass exception instance', handler, 'not_an_exception')
        self.assertRaisesRegex(KeyError, 'error message', handler, KeyError('error message'))

    def test_ignore_encode(self):
        handler = codecs.lookup_error('ignore')
        self.assertEqual(('', 2), handler(UnicodeEncodeError('a', 'abc', 1, 2, 'c')))
        self.assertEqual(('', 3), handler(UnicodeEncodeError('a', 'abc', 1, 4, 'c')))
        self.assertEqual(('', 1), handler(UnicodeEncodeError('a', 'abc', 1, 0, 'c')))

    def test_ignore_decode(self):
        handler = codecs.lookup_error('ignore')
        self.assertEqual(('', 2), handler(UnicodeDecodeError('a', b'abc', 1, 2, 'c')))
        self.assertEqual(('', 3), handler(UnicodeDecodeError('a', b'abc', 1, 4, 'c')))
        self.assertEqual(('', 1), handler(UnicodeDecodeError('a', b'abc', 1, 0, 'c')))

    def test_ignore_translate(self):
        handler = codecs.lookup_error('ignore')
        self.assertEqual(('', 2), handler(UnicodeTranslateError('abc', 1, 2, 'c')))
        self.assertEqual(('', 3), handler(UnicodeTranslateError('abc', 1, 4, 'c')))
        self.assertEqual(('', 1), handler(UnicodeTranslateError('abc', 1, 0, 'c')))

    def test_ignore_others(self):
        handler = codecs.lookup_error('ignore')
        self.assertRaisesRegex(TypeError, "don't know how to handle str in error callback", handler, 'not_an_exception')
        self.assertRaisesRegex(TypeError, "don't know how to handle KeyError in error callback", handler, KeyError('error message'))

    def test_replace_encode(self):
        handler = codecs.lookup_error('replace')
        self.assertEqual(('?', 2), handler(UnicodeEncodeError('a', 'abc', 1, 2, 'c')))
        self.assertEqual(('???', 3), handler(UnicodeEncodeError('a', 'abc', -1, 4, 'c')))
        self.assertEqual(('', 1), handler(UnicodeEncodeError('a', 'abc', 1, 1, 'c')))
        if sys.implementation.name == "graalpy":
            # CPython raises SystemError in this case
            self.assertEqual(('', 1), handler(UnicodeEncodeError('a', 'abc', 2, 1, 'c')))

    def test_replace_decode(self):
        handler = codecs.lookup_error('replace')
        self.assertEqual(('\uFFFD', 2), handler(UnicodeDecodeError('a', b'abc', 1, 2, 'c')))
        self.assertEqual(('\uFFFD', 3), handler(UnicodeDecodeError('a', b'abc', -1, 4, 'c')))
        self.assertEqual(('\uFFFD', 1), handler(UnicodeDecodeError('a', b'abc', 1, 1, 'c')))
        self.assertEqual(('\uFFFD', 1), handler(UnicodeDecodeError('a', b'abc', 2, 1, 'c')))

    def test_replace_translate(self):
        handler = codecs.lookup_error('replace')
        self.assertEqual(('\uFFFD', 2), handler(UnicodeTranslateError('abc', 1, 2, 'c')))
        self.assertEqual(('\uFFFD\uFFFD\uFFFD', 3), handler(UnicodeTranslateError('abc', -1, 4, 'c')))
        if sys.implementation.name == "graalpy":
            # CPython fails an assertion in this case "Python/codecs.c:739: PyCodec_ReplaceErrors: Assertion `PyUnicode_KIND(res) == PyUnicode_2BYTE_KIND' failed."
            self.assertEqual(('', 1), handler(UnicodeTranslateError('abc', 1, 1, 'c')))
            # CPython raises SystemError in this case
            self.assertEqual(('', 1), handler(UnicodeTranslateError('abc', 2, 1, 'c')))

    def test_replace_others(self):
        handler = codecs.lookup_error('replace')
        self.assertRaisesRegex(TypeError, "don't know how to handle str in error callback", handler, 'not_an_exception')
        self.assertRaisesRegex(TypeError, "don't know how to handle KeyError in error callback", handler, KeyError('error message'))

    def test_xmlcharrefreplace_encode(self):
        handler = codecs.lookup_error('xmlcharrefreplace')
        self.assertEqual(('&#98;', 2), handler(UnicodeEncodeError('a', 'abc', 1, 2, 'c')))
        self.assertEqual(('&#97;&#98;&#99;', 3), handler(UnicodeEncodeError('a', 'abc', -1, 4, 'c')))
        self.assertEqual(('', 1), handler(UnicodeEncodeError('a', 'abc', 1, 1, 'c')))
        self.assertEqual(('', 1), handler(UnicodeEncodeError('a', 'abc', 2, 1, 'c')))
        self.assertEqual(('&#0;&#9;&#99;&#999;&#9999;&#99999;&#999999;&#1114111;', 8), handler(UnicodeEncodeError('a', '\u0000\u0009\u0063\u03E7\u270F\U0001869F\U000F423F\U0010FFFF', 0, 8, 'c')))

    def test_xmlcharrefreplace_others(self):
        handler = codecs.lookup_error('xmlcharrefreplace')
        self.assertRaisesRegex(TypeError, "don't know how to handle str in error callback", handler, 'not_an_exception')
        self.assertRaisesRegex(TypeError, "don't know how to handle KeyError in error callback", handler, KeyError('error message'))
        self.assertRaisesRegex(TypeError, "don't know how to handle UnicodeDecodeError in error callback", handler, UnicodeDecodeError('', b'', 1, 1, ''))
        self.assertRaisesRegex(TypeError, "don't know how to handle UnicodeTranslateError in error callback", handler, UnicodeTranslateError('', 1, 1, ''))

    def test_backslashreplace_encode(self):
        handler = codecs.lookup_error('backslashreplace')
        self.assertEqual(('\\x62', 2), handler(UnicodeEncodeError('a', 'abc', 1, 2, 'c')))
        self.assertEqual(('\\x62\\x63', 3), handler(UnicodeEncodeError('a', 'abc', 1, 4, 'c')))
        self.assertEqual(('', 1), handler(UnicodeEncodeError('a', 'abc', 1, 0, 'c')))
        self.assertEqual(('', 1), handler(UnicodeEncodeError('a', 'abc', 2, 1, 'c')))
        self.assertEqual(('\\x00\\xff\\u0100\\uffff\\U00010000\\U0010ffff', 6), handler(UnicodeEncodeError('a', '\u0000\u00ff\u0100\uffff\U00010000\U0010ffff', 0, 6, 'c')))

    def test_backslashreplace_decode(self):
        handler = codecs.lookup_error('backslashreplace')
        self.assertEqual(('\\x62', 2), handler(UnicodeDecodeError('a', b'abc', 1, 2, 'c')))
        self.assertEqual(('\\x62\\x63', 3), handler(UnicodeDecodeError('a', b'abc', 1, 4, 'c')))
        self.assertEqual(('', 1), handler(UnicodeDecodeError('a', b'abc', 1, 0, 'c')))
        if sys.implementation.name == "graalpy":
            # CPython raises SystemError in this case
            self.assertEqual(('', 1), handler(UnicodeDecodeError('a', b'abc', 2, 1, 'c')))

    def test_backslashreplace_translate(self):
        handler = codecs.lookup_error('backslashreplace')
        self.assertEqual(('\\x62', 2), handler(UnicodeTranslateError('abc', 1, 2, 'c')))
        self.assertEqual(('\\x62\\x63', 3), handler(UnicodeTranslateError('abc', 1, 4, 'c')))
        self.assertEqual(('', 1), handler(UnicodeTranslateError('abc', 1, 0, 'c')))
        self.assertEqual(('', 1), handler(UnicodeTranslateError('abc', 2, 1, 'c')))
        self.assertEqual(('\\x00\\xff\\u0100\\uffff\\U00010000\\U0010ffff', 6), handler(UnicodeTranslateError('\u0000\u00ff\u0100\uffff\U00010000\U0010ffff', 0, 6, 'c')))

    def test_backslashreplace_others(self):
        handler = codecs.lookup_error('backslashreplace')
        self.assertRaisesRegex(TypeError, "don't know how to handle str in error callback", handler, 'not_an_exception')
        self.assertRaisesRegex(TypeError, "don't know how to handle KeyError in error callback", handler, KeyError('error message'))

    def test_namereplace_encode(self):
        handler = codecs.lookup_error('namereplace')
        self.assertEqual(('\\N{LATIN SMALL LETTER B}', 2), handler(UnicodeEncodeError('a', 'abc', 1, 2, 'c')))
        self.assertEqual(('\\N{LATIN SMALL LETTER A}\\N{LATIN SMALL LETTER B}\\N{LATIN SMALL LETTER C}', 3), handler(UnicodeEncodeError('a', 'abc', -1, 4, 'c')))
        self.assertEqual(('', 1), handler(UnicodeEncodeError('a', 'abc', 1, 1, 'c')))
        self.assertEqual(('', 2), handler(UnicodeEncodeError('a', 'abc', 2, 1, 'c')))
        self.assertEqual(('\\x00\\x09\\N{LATIN SMALL LETTER C}\\u0378\\N{COPTIC SMALL LETTER KHEI}\\N{PENCIL}\\U000f423f\\U0010ffff', 8), handler(UnicodeEncodeError('a', '\u0000\u0009\u0063\u0378\u03E7\u270F\U000F423F\U0010FFFF', 0, 89, 'c')))

    def test_namereplace_others(self):
        handler = codecs.lookup_error('namereplace')
        self.assertRaisesRegex(TypeError, "don't know how to handle str in error callback", handler, 'not_an_exception')
        self.assertRaisesRegex(TypeError, "don't know how to handle KeyError in error callback", handler, KeyError('error message'))
        self.assertRaisesRegex(TypeError, "don't know how to handle UnicodeDecodeError in error callback", handler, UnicodeDecodeError('', b'', 1, 1, ''))
        self.assertRaisesRegex(TypeError, "don't know how to handle UnicodeTranslateError in error callback", handler, UnicodeTranslateError('', 1, 1, ''))

    def test_surrogatepass_encode_utf8(self):
        handler = codecs.lookup_error('surrogatepass')
        self.assertEqual((b'\xed\xa0\x80\xed\xbf\xbf', 3), handler(UnicodeEncodeError('utf-8', 'a\uD800\uDFFFd', 1, 3, 'c')))
        self.assertEqual((b'\xed\xa0\x80\xed\xbf\xbf', 3), handler(UnicodeEncodeError('uTf_8', 'a\uD800\uDFFFd', 1, 3, 'c')))
        self.assertEqual((b'\xed\xa0\x80\xed\xbf\xbf', 3), handler(UnicodeEncodeError('UtF8', 'a\uD800\uDFFFd', 1, 3, 'c')))
        self.assertEqual((b'\xed\xa0\x80\xed\xbf\xbf', 3), handler(UnicodeEncodeError('CP_UTF8', 'a\uD800\uDFFFd', 1, 3, 'c')))
        # non-surrogates are still errors
        self.assertRaises(UnicodeEncodeError, handler, UnicodeEncodeError('utf-8', 'abc', 1, 2, 'c'))
        # start/end clamping
        self.assertEqual((b'\xed\xa0\x80\xed\xbf\xbf', 2), handler(UnicodeEncodeError('utf-8', '\uD800\uDFFF', -1, 14, 'c')))
        # start > end
        if sys.implementation.name == "graalpy":
            # CPython raises SystemError in this case
            self.assertEqual((b'', 1), handler(UnicodeEncodeError('utf-8', '\uD800\uDDDD\uDFFF', 2, 1, 'c')))

    def test_surrogatepass_encode_utf16(self):
        handler = codecs.lookup_error('surrogatepass')
        self.assertEqual((b'\x00\xd8\xff\xdf', 3), handler(UnicodeEncodeError('utf-16-le', 'a\uD800\uDFFFd', 1, 3, 'c')))
        self.assertEqual((b'\x00\xd8\xff\xdf', 3), handler(UnicodeEncodeError('UTF_16_LE', 'a\uD800\uDFFFd', 1, 3, 'c')))
        self.assertEqual((b'\xd8\x00\xdf\xff', 3), handler(UnicodeEncodeError('UtF16-bE', 'a\uD800\uDFFFd', 1, 3, 'c')))
        self.assertEqual((b'\xd8\x00\xdf\xff' if sys.byteorder == 'big' else b'\x00\xd8\xff\xdf', 3), handler(UnicodeEncodeError('utf-16', 'a\uD800\uDFFFd', 1, 3, 'c')))
        # non-surrogates are still errors
        self.assertRaises(UnicodeEncodeError, handler, UnicodeEncodeError('utf-16', 'abc', 1, 2, 'c'))
        # start/end clamping
        self.assertEqual((b'\xd8\x00\xdf\xff', 2), handler(UnicodeEncodeError('utf-16-be', '\uD800\uDFFF', -1, 14, 'c')))
        # start > end
        if sys.implementation.name == "graalpy":
            # CPython raises SystemError in this case
            self.assertEqual((b'', 1), handler(UnicodeEncodeError('utf-16', '\uD800\uDDDD\uDFFF', 2, 1, 'c')))

    def test_surrogatepass_encode_utf32(self):
        handler = codecs.lookup_error('surrogatepass')
        self.assertEqual((b'\x00\xd8\x00\x00\xff\xdf\x00\x00', 3), handler(UnicodeEncodeError('utf-32-le', 'a\uD800\uDFFFd', 1, 3, 'c')))
        self.assertEqual((b'\x00\xd8\x00\x00\xff\xdf\x00\x00', 3), handler(UnicodeEncodeError('UTF_32_LE', 'a\uD800\uDFFFd', 1, 3, 'c')))
        self.assertEqual((b'\x00\x00\xd8\x00\x00\x00\xdf\xff', 3), handler(UnicodeEncodeError('UtF32-bE', 'a\uD800\uDFFFd', 1, 3, 'c')))
        self.assertEqual((b'\x00\x00\xd8\x00\x00\x00\xdf\xff' if sys.byteorder == 'big' else b'\x00\xd8\x00\x00\xff\xdf\x00\x00', 3), handler(UnicodeEncodeError('utf-32', 'a\uD800\uDFFFd', 1, 3, 'c')))
        # non-surrogates are still errors
        self.assertRaises(UnicodeEncodeError, handler, UnicodeEncodeError('utf-32', 'abc', 1, 2, 'c'))
        # start/end clamping
        self.assertEqual((b'\x00\x00\xd8\x00\x00\x00\xdf\xff', 2), handler(UnicodeEncodeError('utf-32-be', '\uD800\uDFFF', -1, 14, 'c')))
        # start > end
        if sys.implementation.name == "graalpy":
            # CPython raises SystemError in this case
            self.assertEqual((b'', 1), handler(UnicodeEncodeError('utf-32', '\uD800\uDDDD\uDFFF', 2, 1, 'c')))

    def test_surrogatepass_encode_unknown(self):
        handler = codecs.lookup_error('surrogatepass')
        self.assertRaises(UnicodeEncodeError, handler, UnicodeEncodeError('latin1', 'abc', 1, 2, 'c'))
        self.assertRaises(UnicodeEncodeError, handler, UnicodeEncodeError('utf-7', 'abc', 1, 2, 'c'))
        self.assertRaises(UnicodeEncodeError, handler, UnicodeEncodeError('utf8', 'abc', 1, 2, 'c'))
        self.assertRaises(UnicodeEncodeError, handler, UnicodeEncodeError('cp_utf8', 'abc', 1, 2, 'c'))
        self.assertRaises(UnicodeEncodeError, handler, UnicodeEncodeError('utf-8-le', 'abc', 1, 2, 'c'))
        self.assertRaises(UnicodeEncodeError, handler, UnicodeEncodeError('utf-16-xe', 'abc', 1, 2, 'c'))
        self.assertRaises(UnicodeEncodeError, handler, UnicodeEncodeError('utf-16-l', 'abc', 1, 2, 'c'))
        self.assertRaises(UnicodeEncodeError, handler, UnicodeEncodeError('utf-16-', 'abc', 1, 2, 'c'))
        self.assertRaises(UnicodeEncodeError, handler, UnicodeEncodeError('CP_UTF16', 'abc', 1, 2, 'c'))
        self.assertRaises(UnicodeEncodeError, handler, UnicodeEncodeError('utf-32-xe', 'abc', 1, 2, 'c'))
        self.assertRaises(UnicodeEncodeError, handler, UnicodeEncodeError('utf-32-l', 'abc', 1, 2, 'c'))
        self.assertRaises(UnicodeEncodeError, handler, UnicodeEncodeError('utf-32-', 'abc', 1, 2, 'c'))
        self.assertRaises(UnicodeEncodeError, handler, UnicodeEncodeError('CP_UTF32', 'abc', 1, 2, 'c'))

    def test_surrogatepass_decode_utf8(self):
        handler = codecs.lookup_error('surrogatepass')
        self.assertEqual(('\ud800', 4), handler(UnicodeDecodeError('utf-8', b'a\xed\xa0\x80\xed\xbf\xbfd', 1, 7, 'c')))
        self.assertEqual(('\ud800', 4), handler(UnicodeDecodeError('UTF_8', b'a\xed\xa0\x80\xed\xbf\xbfd', 1, 7, 'c')))
        self.assertEqual(('\ud800', 4), handler(UnicodeDecodeError('CP_UTF8', b'a\xed\xa0\x80\xed\xbf\xbfd', 1, 7, 'c')))
        # non-surrogates are still errors
        self.assertRaises(UnicodeDecodeError, handler, UnicodeDecodeError('utf-8', b'abc', 1, 2, 'c'))
        # start/end clamping
        self.assertEqual(('\ud800', 3), handler(UnicodeDecodeError('utf-8', b'\xed\xa0\x80\xed\xbf\xbf', -1, 14, 'c')))
        # end is ignored
        for end in (-1, 0, 3, 4, 5, 6, 100):
            self.assertEqual(('\udfff', 6), handler(UnicodeDecodeError('utf-8', b'\xed\xa0\x80\xed\xbf\xbf', 3, end, 'c')))

    def test_surrogatepass_decode_utf16(self):
        handler = codecs.lookup_error('surrogatepass')
        self.assertEqual(('\ud800', 3), handler(UnicodeDecodeError('utf-16-le', b'a\x00\xd8\xff\xdfd', 1, 7, 'c')))
        self.assertEqual(('\ud800', 3), handler(UnicodeDecodeError('UtF_16-Be', b'a\xd8\x00\xdf\xffd', 1, 7, 'c')))
        self.assertEqual(('\ud800', 3), handler(UnicodeDecodeError('UTF_16', b'a\xd8\x00\xdf\xffd' if sys.byteorder == 'big' else b'a\x00\xd8\xff\xdfd', 1, 7, 'c')))
        # non-surrogates are still errors
        self.assertRaises(UnicodeDecodeError, handler, UnicodeDecodeError('utf-16', b'abc', 1, 2, 'c'))
        # start/end clamping
        self.assertEqual(('\ud800', 2), handler(UnicodeDecodeError('utf-16-le', b'\x00\xd8\xff\xdf', -1, 14, 'c')))
        # end is ignored
        for end in (-1, 0, 3, 4, 5, 6, 100):
            self.assertEqual(('\udfff', 4), handler(UnicodeDecodeError('utf-16-le', b'\x00\xd8\xff\xdf', 2, end, 'c')))

    def test_surrogatepass_decode_utf32(self):
        handler = codecs.lookup_error('surrogatepass')
        self.assertEqual(('\ud800', 5), handler(UnicodeDecodeError('utf-32-le', b'a\x00\xd8\x00\x00\xff\xdf\x00\x00d', 1, 11, 'c')))
        self.assertEqual(('\ud800', 5), handler(UnicodeDecodeError('UtF_32-Be', b'a\x00\x00\xd8\x00\x00\x00\xdf\xffd', 1, 11, 'c')))
        self.assertEqual(('\ud800', 5), handler(UnicodeDecodeError('UTF_32', b'a\x00\x00\xd8\x00\x00\x00\xdf\xffd' if sys.byteorder == 'big' else b'a\x00\xd8\x00\x00\xff\xdf\x00\x00d', 1, 11, 'c')))
        # non-surrogates are still errors
        self.assertRaises(UnicodeDecodeError, handler, UnicodeDecodeError('utf-32', b'abcde', 1, 4, 'c'))
        # start/end clamping
        self.assertEqual(('\ud800', 4), handler(UnicodeDecodeError('utf-32-le', b'\x00\xd8\x00\x00\xff\xdf\x00\x00', -1, 14, 'c')))
        # end is ignored
        for end in (-1, 0, 3, 4, 5, 8, 100):
            self.assertEqual(('\udfff', 8), handler(UnicodeDecodeError('utf-32-le', b'\x00\xd8\x00\x00\xff\xdf\x00\x00', 4, end, 'c')))

    def test_surrogatepass_decode_unknown(self):
        handler = codecs.lookup_error('surrogatepass')
        self.assertRaises(UnicodeDecodeError, handler, UnicodeDecodeError('latin1', b'abc', 1, 2, 'c'))
        self.assertRaises(UnicodeDecodeError, handler, UnicodeDecodeError('utf-7', b'abc', 1, 2, 'c'))
        self.assertRaises(UnicodeDecodeError, handler, UnicodeDecodeError('utf8', b'abc', 1, 2, 'c'))
        self.assertRaises(UnicodeDecodeError, handler, UnicodeDecodeError('cp_utf8', b'abc', 1, 2, 'c'))
        self.assertRaises(UnicodeDecodeError, handler, UnicodeDecodeError('utf-8-le', b'abc', 1, 2, 'c'))
        self.assertRaises(UnicodeDecodeError, handler, UnicodeDecodeError('utf-16-xe', b'abc', 1, 2, 'c'))
        self.assertRaises(UnicodeDecodeError, handler, UnicodeDecodeError('utf-16-l', b'abc', 1, 2, 'c'))
        self.assertRaises(UnicodeDecodeError, handler, UnicodeDecodeError('utf-16-', b'abc', 1, 2, 'c'))
        self.assertRaises(UnicodeDecodeError, handler, UnicodeDecodeError('CP_UTF16', b'abc', 1, 2, 'c'))
        self.assertRaises(UnicodeDecodeError, handler, UnicodeDecodeError('utf-32-xe', b'abc', 1, 2, 'c'))
        self.assertRaises(UnicodeDecodeError, handler, UnicodeDecodeError('utf-32-l', b'abc', 1, 2, 'c'))
        self.assertRaises(UnicodeDecodeError, handler, UnicodeDecodeError('utf-32-', b'abc', 1, 2, 'c'))
        self.assertRaises(UnicodeDecodeError, handler, UnicodeDecodeError('CP_UTF32', b'abc', 1, 2, 'c'))

    def test_surrogatepass_others(self):
        handler = codecs.lookup_error('surrogatepass')
        self.assertRaisesRegex(TypeError, "don't know how to handle str in error callback", handler, 'not_an_exception')
        self.assertRaisesRegex(TypeError, "don't know how to handle KeyError in error callback", handler, KeyError('error message'))
        self.assertRaisesRegex(TypeError, "don't know how to handle UnicodeTranslateError in error callback", handler, UnicodeTranslateError('', 1, 1, ''))

    def test_surrogateescape_encode(self):
        handler = codecs.lookup_error('surrogateescape')
        self.assertEqual((b'\x80\xff', 3), handler(UnicodeEncodeError('utf-8', 'a\udc80\udcffd', 1, 3, 'c')))
        # values outside the range 0xdc80-0xdcff are still errors
        self.assertRaises(UnicodeEncodeError, handler, UnicodeEncodeError('utf-16', '\udc7f', 0, 1, 'c'))
        self.assertRaises(UnicodeEncodeError, handler, UnicodeEncodeError('utf-16', '\udd00', 0, 1, 'c'))
        # start/end clamping
        self.assertEqual((b'\x80\xff', 2), handler(UnicodeEncodeError('utf-16-le', '\udc80\udcff', -1, 14, 'c')))
        # start > end
        if sys.implementation.name == "graalpy":
            # CPython raises SystemError in this case
            self.assertEqual((b'', 1), handler(UnicodeEncodeError('abc', '\udc80\udc81\udcfe\udcff', 3, 1, 'c')))

    def test_surrogateescape_decode(self):
        handler = codecs.lookup_error('surrogateescape')
        self.assertEqual(('\udc80\udcff', 3), handler(UnicodeDecodeError('utf-8', b'a\x80\xffd', 1, 3, 'c')))
        # values outside the range 0x80-0xff are still errors if they are at the start
        self.assertRaises(UnicodeDecodeError, handler, UnicodeDecodeError('utf-16', b'abc', 1, 2, 'c'))
        # values outside the range 0x80-0xff stop the handler
        self.assertEqual(('\udc80\udcff', 2), handler(UnicodeDecodeError('utf-16', b'\x80\xffabc', 0, 5, 'c')))
        # at most 4 characters are processed
        self.assertEqual(('\udc80\udc81\udc82\udc83', 4), handler(UnicodeDecodeError('utf-16', b'\x80\x81\x82\x83\x84\x85', 0, 6, 'c')))
        # start/end clamping
        self.assertEqual(('\udc80\udcff', 2), handler(UnicodeDecodeError('utf-16-le', b'\x80\xff', -1, 14, 'c')))
        # start > end
        self.assertRaises(UnicodeDecodeError, handler, UnicodeDecodeError('abc', b'\x80\x81\x82\x83\x84\x85', 3, 1, 'c'))

    def test_surrogateescape_others(self):
        handler = codecs.lookup_error('surrogateescape')
        self.assertRaisesRegex(TypeError, "don't know how to handle str in error callback", handler, 'not_an_exception')
        self.assertRaisesRegex(TypeError, "don't know how to handle KeyError in error callback", handler, KeyError('error message'))
        self.assertRaisesRegex(TypeError, "don't know how to handle UnicodeTranslateError in error callback", handler, UnicodeTranslateError('', 1, 1, ''))


class CharmapDecodeLatin1Test(unittest.TestCase):
    def test_wrong_type(self):
        self.assertRaises(TypeError, codecs.charmap_decode, '')

    def test_empty_string(self):
        self.assertEqual(('', 0), codecs.charmap_decode(b''))

    def test_none_error(self):
        self.assertEqual(('abc', 3), codecs.charmap_decode(b'abc', None))

    def test_ignored_error(self):
        self.assertEqual(('abc', 3), codecs.charmap_decode(b'abc', 'this is ignored'))

    def test_explicit_none_mapping(self):
        self.assertEqual(('abc', 3), codecs.charmap_decode(b'abc', 'strict', None))

    def test_corner_cases(self):
        self.assertEqual(('\x7f\x80\x9f\0\xff', 5), codecs.charmap_decode(b'\x7f\x80\x9f\0\xff', 'strict', None))


class CharmapDecodeStringMappingTest(unittest.TestCase):
    def test_wrong_type(self):
        self.assertRaises(TypeError, codecs.charmap_decode, '', 'strict', 'abc')

    def test_empty_string(self):
        self.assertEqual(('', 0), codecs.charmap_decode(b'', 'strict', 'abc'))

    def test_none_error(self):
        self.assertEqual(('cba', 3), codecs.charmap_decode(b'\x02\x01\x00', None, 'abc'))

    def test_smp_codepoint(self):
        self.assertEqual(('c\U00010400a', 3), codecs.charmap_decode(b'\x02\x01\x00', 'strict', 'a\U00010400c'))

    def test_broken_surrogates(self):
        self.assertEqual(('\udc00a\ud801', 3), codecs.charmap_decode(b'\x02\x01\x00', 'strict', '\ud801a\udc00'))

    def test_surrogates_dont_join(self):
        self.assertEqual(('\ud801\udc00', 2), codecs.charmap_decode(b'\x00\x02', 'strict', '\ud801a\udc00'))

    def test_explicit_unmapped_strict(self):
        with self.assertRaisesRegex(UnicodeDecodeError, "'charmap' codec can't decode byte 0x01 in position 1: character maps to <undefined>"):
            codecs.charmap_decode(b'\x02\x01\x00', 'strict', 'a\ufffec')

    def test_custom_handler(self):
        def err_handler(exc):
            self.assertEqual('charmap', exc.encoding)
            self.assertEqual(b'\x02\x01\x00', exc.object)
            self.assertEqual(1, exc.start)
            self.assertEqual(2, exc.end)
            return 'XYZ', 3
        codecs.register_error('test.err_handler', err_handler)
        self.assertEqual(('cXYZ', 3), codecs.charmap_decode(b'\x02\x01\x00', 'test.err_handler', 'a\ufffec'))

    def test_custom_handler_negative(self):
        def err_handler(exc):
            return 'XYZ', -1
        codecs.register_error('test.err_handler', err_handler)
        self.assertEqual(('cXYZa', 3), codecs.charmap_decode(b'\x02\x01\x00', 'test.err_handler', 'a\ufffec'))

    def test_change_src_in_handler(self):
        def err_handler(exc):
            exc.object = b'\x03\x02'
            return 'A', 1
        codecs.register_error('test.err_handler', err_handler)
        self.assertEqual(('cAc', 6), codecs.charmap_decode(b'\x02\x01\x00zzz', 'test.err_handler', 'a\ufffec'))


class CharmapDecodeDictMappingTest(unittest.TestCase):
    def test_wrong_type(self):
        self.assertRaises(TypeError, codecs.charmap_decode, '', 'strict', {0: 'a', 1: 'b', 2: 'c'})

    def test_empty_string(self):
        self.assertEqual(('', 0), codecs.charmap_decode(b'', 'strict', {0: 'a', 1: 'b', 2: 'c'}))

    def test_none_error(self):
        self.assertEqual(('cba', 3), codecs.charmap_decode(b'\x02\x01\x00', None, {0: 'a', 1: 'b', 2: 'c'}))

    def test_smp_codepoint(self):
        self.assertEqual(('c\U00010400a', 3), codecs.charmap_decode(b'\x02\x01\x00', 'strict', {0: 'a', 1: '\U00010400', 2: 'c'}))

    def test_broken_surrogates(self):
        self.assertEqual(('\udc00a\ud801', 3), codecs.charmap_decode(b'\x02\x01\x00', 'strict', {0: '\ud801', 1: 'a', 2: '\udc00'}))

    def test_surrogates_dont_join(self):
        self.assertEqual(('\ud801\udc00', 2), codecs.charmap_decode(b'\x00\x02', 'strict', {0: '\ud801', 1: 'a', 2: '\udc00'}))

    def test_x(self):
        self.assertEqual(('A\ufffeCXYZ$', 3), codecs.charmap_decode(b'\x02\x01\x00', 'strict', {0: ord('$'), 1: 'XYZ', 2: 'A\ufffeC'}))

    def test_explicit_unmapped_strict(self):
        with self.assertRaisesRegex(UnicodeDecodeError, "'charmap' codec can't decode byte 0x01 in position 1: character maps to <undefined>"):
            codecs.charmap_decode(b'\x02\x01\x00', 'strict', {0: 'a', 1: '\ufffe', 2: 'c'})

    def test_explicit_unmapped_none_strict(self):
        with self.assertRaisesRegex(UnicodeDecodeError, "'charmap' codec can't decode byte 0x01 in position 1: character maps to <undefined>"):
            codecs.charmap_decode(b'\x02\x01\x00', 'strict', {0: 'a', 1: None, 2: 'c'})

    def test_explicit_unmapped_int_strict(self):
        with self.assertRaisesRegex(UnicodeDecodeError, "'charmap' codec can't decode byte 0x01 in position 1: character maps to <undefined>"):
            codecs.charmap_decode(b'\x02\x01\x00', 'strict', {0: 'a', 1: 0xfffe, 2: 'c'})

    def test_missing_unmapping_strict(self):
        with self.assertRaisesRegex(UnicodeDecodeError, "'charmap' codec can't decode byte 0x03 in position 1: character maps to <undefined>"):
            codecs.charmap_decode(b'\x02\x03\x00', 'strict', {0: 'a', 1: 'b', 2: 'c'})

    def test_invalid_type(self):
        with self.assertRaisesRegex(TypeError, "character mapping must return integer, None or str"):
            codecs.charmap_decode(b'\x02\x01\x00', 'strict', {0: 'a', 1: (), 2: 'c'})

    def test_invalid_range1(self):
        with self.assertRaisesRegex(TypeError, "character mapping must be in range\\(0x110000\\)"):
            codecs.charmap_decode(b'\x02\x01\x00', 'strict', {0: 'a', 1: -1, 2: 'c'})

    def test_invalid_range2(self):
        with self.assertRaisesRegex(TypeError, "character mapping must be in range\\(0x110000\\)"):
            codecs.charmap_decode(b'\x02\x01\x00', 'strict', {0: 'a', 1: 0x110000, 2: 'c'})

    def test_max_unicode(self):
        self.assertEqual(('c\U0010FFFFa', 3), codecs.charmap_decode(b'\x02\x01\x00', 'strict', {0: 'a', 1: 0x10FFFF, 2: 'c'}))

    def test_custom_handler(self):
        def err_handler(exc):
            self.assertEqual('charmap', exc.encoding)
            self.assertEqual(b'\x02\x01\x00', exc.object)
            self.assertEqual(1, exc.start)
            self.assertEqual(2, exc.end)
            return 'XYZ', 3
        codecs.register_error('test.err_handler', err_handler)
        self.assertEqual(('cXYZ', 3), codecs.charmap_decode(b'\x02\x01\x00', 'test.err_handler', {0: 'a', 1: 0xfffe, 2: 'c'}))

    def test_custom_handler_negative(self):
        def err_handler(exc):
            return 'XYZ', -1
        codecs.register_error('test.err_handler', err_handler)
        self.assertEqual(('cXYZa', 3), codecs.charmap_decode(b'\x02\x01\x00', 'test.err_handler', {0: 'a', 1: 0xfffe, 2: 'c'}))

    def test_change_src_in_handler(self):
        def err_handler(exc):
            exc.object = b'\x03\x02'
            return 'A', 1
        codecs.register_error('test.err_handler', err_handler)
        self.assertEqual(('cAc', 6), codecs.charmap_decode(b'\x02\x01\x00zzz', 'test.err_handler', {0: 'a', 1: 0xfffe, 2: 'c'}))


class CharmapEncodeTest(unittest.TestCase):
    def test_build_dict(self):
        self.assertEqual({97: 0}, codecs.charmap_build('a'))
        self.assertEqual({97: 0, 98: 1, 99: 2}, codecs.charmap_build('abc'))
        self.assertEqual({97: 0, 0xfffe: 1, 99: 2}, codecs.charmap_build('a\uFFFEc'))
        self.assertEqual({97: 2}, codecs.charmap_build('aaa'))

    def test_encode_dict(self):
        self.assertEqual((b'\x01xyz\x01', 3), codecs.charmap_encode('aba', 'strict', {ord('a'): 1, ord('b'): b'xyz'}))

    def test_encode_dict_range(self):
        with self.assertRaisesRegex(TypeError, "character mapping must be in range\\(256\\)"):
            codecs.charmap_encode('a', 'strict', {ord('a'): -1})

        with self.assertRaisesRegex(TypeError, "character mapping must be in range\\(256\\)"):
            codecs.charmap_encode('a', 'strict', {ord('a'): 256})

    def test_encode_dict_type(self):
        with self.assertRaisesRegex(TypeError, "character mapping must return integer, bytes or None, not str"):
            codecs.charmap_encode('a', 'strict', {ord('a'): ''})

    def test_encode_neither_dict_nor_encodingmap(self):
        self.assertEqual((b'zyx', 3), codecs.charmap_encode('\x02\x01\x00', 'strict', b'xyz'))

    def test_encode_encodingmap(self):
        m = codecs.charmap_build('\x00a\u1234c\uffff')
        self.assertEqual((b'\x03\x02\x01\x04', 4), codecs.charmap_encode('c\u1234a\uffff', 'strict', m))

    def test_encode_dict_combines_errors(self):
        def err_handler(exc):
            self.assertEqual('charmap', exc.encoding)
            self.assertEqual('abcd', exc.object)
            self.assertEqual(1, exc.start)
            self.assertEqual(3, exc.end)
            return b'XYZ', 4
        codecs.register_error('test.err_handler', err_handler)
        self.assertEqual((b'\x01XYZ', 4), codecs.charmap_encode('abcd', 'test.err_handler', {ord('a'): 1, ord('b'): None, ord('d'): 3}))

    def test_encode_dict_combines_errors_edge(self):
        def err_handler(exc):
            self.assertEqual('charmap', exc.encoding)
            self.assertEqual('abc', exc.object)
            self.assertEqual(1, exc.start)
            self.assertEqual(3, exc.end)
            return b'XYZ', 3
        codecs.register_error('test.err_handler', err_handler)
        self.assertEqual((b'\x01XYZ', 3), codecs.charmap_encode('abc', 'test.err_handler', {ord('a'): 1, ord('b'): None, ord('d'): 3}))

    def test_encode_dict_error_handler_str_result(self):
        def err_handler(exc):
            return 'X', 3       # str result goes through the mapping again
        codecs.register_error('test.err_handler', err_handler)
        self.assertEqual((b'\x01\x04\x03', 4), codecs.charmap_encode('abcd', 'test.err_handler', {ord('a'): 1, ord('b'): None, ord('d'): 3, ord('X'): 4}))

    def test_encode_dict_error_handler_str_result_err(self):
        def err_handler(exc):
            return 'X', 3       # str result goes through the mapping again, but handler is not called again
        codecs.register_error('test.err_handler', err_handler)
        with self.assertRaisesRegex(UnicodeEncodeError, "'charmap' codec can't encode characters in position 1-2: character maps to <undefined>"):
            codecs.charmap_encode('abcd', 'test.err_handler', {ord('a'): 1, ord('b'): None, ord('d'): 3, ord('X'): None})

    def test_encode_dict_err_strict(self):
        with self.assertRaisesRegex(UnicodeEncodeError, "'charmap' codec can't encode characters in position 1-2: character maps to <undefined>"):
            codecs.charmap_encode('abcd', 'strict', {ord('a'): 1, ord('b'): None, ord('d'): 3})

    def test_encode_dict_err_replace(self):
        self.assertEqual((b'\x01\x04\x04\x03', 4), codecs.charmap_encode('abcd', 'replace', {ord('a'): 1, ord('b'): None, ord('d'): 3, ord('?'): 4}))

    def test_encode_dict_err_ignore(self):
        self.assertEqual((b'\x01\x03', 4), codecs.charmap_encode('abcd', 'ignore', {ord('a'): 1, ord('b'): None, ord('d'): 3}))

    def test_encode_dict_err_xmlcharrefreplace(self):
        self.assertEqual((b'\x00\r\x0c\x0b\n\x0e\r\x0c\x0b\x0b\x0e\x01', 4), codecs.charmap_encode('abcd', 'xmlcharrefreplace', codecs.charmap_build('ad0123456789#&;')))


class MultibyteCodecTest(unittest.TestCase):

    # just a smoke test
    def test_encode(self):
        import _codecs_tw
        codec = _codecs_tw.getcodec('big5')

        self.assertEqual((b'', 0), codec.encode(""))
        self.assertRaises(TypeError, codec.encode)
        self.assertRaises(UnicodeEncodeError, codec.encode, '\xffff')


class UTF32Test(unittest.TestCase):
    def test_utf32_surrogate_error(self):
        with self.assertRaisesRegex(UnicodeDecodeError, "codec can't decode bytes in position 4-7"):
            b'a\x00\x00\x00\x00\xd8\x00\x00z\x00\x00\x00'.decode('utf-32')


if __name__ == '__main__':
    unittest.main()
