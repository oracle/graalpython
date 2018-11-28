# Copyright (c) 2018, Oracle and/or its affiliates.
# Copyright (C) 1996-2017 Python Software Foundation
#
# Licensed under the PYTHON SOFTWARE FOUNDATION LICENSE VERSION 2


def test_subscript():
    v = memoryview(b'abcefg')
    assert v[1] == 98, "1: was: %s" % v[1]
    assert v[-1] == 103, "2: was: %s" % v[-1]
    assert isinstance(v[1:4], memoryview), "3: was: %s" % type(v[1:4])
    res = v[1:4]
    assert str(bytes(res)) == str(b"bce")
    assert bytes(res) == b"bce"


def test_readonly():
    v = memoryview(bytearray(b"abcdefg"))
    assert not v.readonly
    try:
        v.readonly = False
    except AttributeError:
        assert True
    else:
        assert False


def _test_assignment():
    data = bytearray(b'abcefg')
    v = memoryview(data)
    v[0] = ord(b'z')
    assert data == bytearray(b'zbcefg')
    v[1:4] = b'123'
    assert data == bytearray(b'z123fg')
    try:
            v[2:3] = b'spam'
    except ValueError:
            assert True
    else:
        assert False
    v[2:6] = b'spam'
    assert data == bytearray(b'z1spam')


def test_tobytes():
    b = b"hello"
    v = memoryview(b)
    assert v.tobytes() == b

    b = b"\xff\x00\x00"
    v = memoryview(b)
    assert v.tobytes() == b
