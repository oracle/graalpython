# Copyright (c) 2018, 2024, Oracle and/or its affiliates.
# Copyright (C) 1996-2017 Python Software Foundation
#
# Licensed under the PYTHON SOFTWARE FOUNDATION LICENSE VERSION 2

import sys

from tests.util import assert_raises


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


def test_slice():
    b = bytes(range(8))
    m = memoryview(b)
    for i in (-10, -5, -1, 0, 1, 5, 10):
        for j in (-10, -5, -1, 0, 1, 5, 10):
            for k in (-10, -2, -1, 0, 1, 2, 10):
                for i2 in (-10, -5, -1, 0, 1, 5, 10):
                    for j2 in (-10, -5, -1, 0, 1, 5, 10):
                        for k2 in (-10, -2, -1, 0, 1, 2, 10):
                            try:
                                s1 = b[i:j:k][i2:j2:k2]
                            except Exception as e1:
                                try:
                                    m[i:j:k][i2:j2:k2]
                                except Exception as e2:
                                    assert type(e1) == type(e2)
                                else:
                                    assert False
                            else:
                                s2 = m[i:j:k][i2:j2:k2]
                                assert len(s1) == len(s2)
                                for l in range(-len(s1) - 1, len(s1) + 2):
                                    try:
                                        e1 = s1[l]
                                    except Exception as e1:
                                        try:
                                            s2[l]
                                        except Exception as e2:
                                            assert type(e1) == type(e2)
                                        else:
                                            assert False
                                    else:
                                        e2 = s2[l]
                                        assert e1 == e2

def test_unpack():
    assert memoryview(b'\xaa')[0] == 170
    assert memoryview(b'\xaa').cast('B')[0] == 170
    assert memoryview(b'\xaa').cast('b')[0] == -86
    assert memoryview(b'\xaa\xaa').cast('H')[0] == 43690
    assert memoryview(b'\xaa\xaa').cast('h')[0] == -21846
    assert memoryview(b'\xaa\xaa\xaa\xaa').cast('I')[0] == 2863311530
    assert memoryview(b'\xaa\xaa\xaa\xaa').cast('i')[0] == -1431655766
    assert memoryview(b'\xaa\xaa\xaa\xaa\xaa\xaa\xaa\xaa').cast('L')[0] == 12297829382473034410
    assert memoryview(b'\xaa\xaa\xaa\xaa\xaa\xaa\xaa\xaa').cast('l')[0] == -6148914691236517206
    assert memoryview(b'\xaa\xaa\xaa\xaa\xaa\xaa\xaa\xaa').cast('Q')[0] == 12297829382473034410
    assert memoryview(b'\xaa\xaa\xaa\xaa\xaa\xaa\xaa\xaa').cast('q')[0] == -6148914691236517206
    assert memoryview(b'\xaa\xaa\xaa\xaa\xaa\xaa\xaa\xaa').cast('N')[0] == 12297829382473034410
    assert memoryview(b'\xaa\xaa\xaa\xaa\xaa\xaa\xaa\xaa').cast('n')[0] == -6148914691236517206
    assert memoryview(b'\xaa\xaa\xaa\xaa\xaa\xaa\xaa\xaa').cast('P')[0] == 12297829382473034410
    assert memoryview(b'\x00').cast('?')[0] is False
    assert memoryview(b'\xaa').cast('?')[0] is True
    assert memoryview(b'\xaa\xaa\xaa\xaa').cast('f')[0] == -3.0316488252093987e-13
    assert memoryview(b'\xaa\xaa\xaa\xaa\xaa\xaa\xaa\xaa').cast('d')[0] == -3.7206620809969885e-103
    assert memoryview(b'\xaa').cast('c')[0] == b'\xaa'

def test_pack():
    b = bytearray(1)
    memoryview(b).cast('B')[0] = 170
    assert b == b'\xaa'
    b = bytearray(1)
    memoryview(b).cast('b')[0] = -86
    assert b == b'\xaa'
    b = bytearray(2)
    memoryview(b).cast('H')[0] = 43690
    assert b == b'\xaa\xaa'
    b = bytearray(2)
    memoryview(b).cast('h')[0] = -21846
    assert b == b'\xaa\xaa'
    b = bytearray(4)
    memoryview(b).cast('I')[0] = 2863311530
    assert b == b'\xaa\xaa\xaa\xaa'
    b = bytearray(4)
    memoryview(b).cast('i')[0] = -1431655766
    assert b == b'\xaa\xaa\xaa\xaa'
    b = bytearray(8)
    memoryview(b).cast('L')[0] = 12297829382473034410
    assert b == b'\xaa\xaa\xaa\xaa\xaa\xaa\xaa\xaa'
    b = bytearray(8)
    memoryview(b).cast('l')[0] = -6148914691236517206
    assert b == b'\xaa\xaa\xaa\xaa\xaa\xaa\xaa\xaa'
    b = bytearray(8)
    memoryview(b).cast('Q')[0] = 12297829382473034410
    assert b == b'\xaa\xaa\xaa\xaa\xaa\xaa\xaa\xaa'
    b = bytearray(8)
    memoryview(b).cast('q')[0] = -6148914691236517206
    assert b == b'\xaa\xaa\xaa\xaa\xaa\xaa\xaa\xaa'
    b = bytearray(8)
    memoryview(b).cast('N')[0] = 12297829382473034410
    assert b == b'\xaa\xaa\xaa\xaa\xaa\xaa\xaa\xaa'
    b = bytearray(8)
    memoryview(b).cast('n')[0] = -6148914691236517206
    assert b == b'\xaa\xaa\xaa\xaa\xaa\xaa\xaa\xaa'
    b = bytearray(8)
    memoryview(b).cast('P')[0] = 12297829382473034410
    assert b == b'\xaa\xaa\xaa\xaa\xaa\xaa\xaa\xaa'
    b = bytearray(4)
    memoryview(b).cast('f')[0] = -3.0316488252093987e-13
    assert b == b'\xaa\xaa\xaa\xaa'
    b = bytearray(8)
    memoryview(b).cast('d')[0] = -3.7206620809969885e-103
    assert b == b'\xaa\xaa\xaa\xaa\xaa\xaa\xaa\xaa'
    b = bytearray(1)
    memoryview(b).cast('c')[0] = b'\xaa'
    assert b == b'\xaa'
    b = bytearray(1)
    memoryview(b).cast('?')[0] = True
    assert b == b'\x01'
    memoryview(b).cast('?')[0] = False
    assert b == b'\x00'

def test_read_after_resize():
    if sys.implementation.name != "graalpy":
        return
    # CPython prevents resizing of acquired buffers at all to avoid a segfault
    # We don't want to impose locking on managed objects because we cannot automatically
    # release the lock by reference counting. Check that we don't hard crash when
    # does an out-of-bound read on a resized buffer
    b = bytearray(b'12341251452134523463456435643')
    m = memoryview(b)
    assert m[1] == ord('2')
    b.clear()
    assert_raises(IndexError, lambda: m[1])
    assert_raises(IndexError, lambda: m.tobytes())
    def assign():
        m[1] = 3
    assert_raises(IndexError, assign)

def test_mmap():
    import mmap
    m = mmap.mmap(-1, 1)
    mv = memoryview(m)
    assert len(mv) == 1
    assert mv.__getitem__(0) == 0
    mv.__setitem__(0, 1)
    assert mv.__getitem__(0) == 1
