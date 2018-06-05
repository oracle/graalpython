# Copyright (c) 2018, Oracle and/or its affiliates.
# Copyright (C) 1996-2017 Python Software Foundation
#
# Licensed under the PYTHON SOFTWARE FOUNDATION LICENSE VERSION 2


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
    # assert codecs.decode(b'abc') == 'abc'
    # assert_raises(UnicodeDecodeError, codecs.decode, b'\xff', 'ascii')
    #
    # test keywords
    # assert codecs.decode(obj=b'\xe4\xf6\xfc', encoding='latin-1') == '\xe4\xf6\xfc'
    # assert codecs.decode(b'[\xff]', 'ascii', errors='ignore') == '[]'
    assert codecs.decode(b'[]', 'ascii') == '[]'


def test_encode():
    import codecs

    # TODO: this does not work yet due to the fact that we do not handle all strings literal types yet
    # assert codecs.encode('\xe4\xf6\xfc', 'latin-1') == b'\xe4\xf6\xfc'
    # assert_raises(TypeError, codecs.encode)
    # assert_raises(LookupError, codecs.encode, "foo", "__spam__")
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
