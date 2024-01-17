import mmap

PAGESIZE = mmap.PAGESIZE
FIND_BUFFER_SIZE = 1024  # keep in sync with FindNode#BUFFER_SIZE


def test_find():
    cases = [
        # (size, needle_pos)
        (FIND_BUFFER_SIZE * 3 + 1, FIND_BUFFER_SIZE * 3 - 2),
        (FIND_BUFFER_SIZE * 3 + 3, FIND_BUFFER_SIZE * 3),
        (FIND_BUFFER_SIZE * 2, FIND_BUFFER_SIZE),
        (FIND_BUFFER_SIZE * 2, FIND_BUFFER_SIZE - 1),
        (11, 1),
    ]
    for (size, needle_pos) in cases:
        m = mmap.mmap(-1, size)
        m[needle_pos] = b'a'[0]
        m[needle_pos + 1] = b'b'[0]
        m[needle_pos + 2] = b'c'[0]
        assert m.find(b'abc') == needle_pos
        assert m.find(b'abc', 0, needle_pos) == -1
        assert m.find(b'abc', 0, needle_pos + 2) == -1
        assert m.find(b'abc', 0, needle_pos + 3) == needle_pos
        assert m.find(b'abc', needle_pos) == needle_pos
        assert m.find(b'abc', needle_pos + 1) == -1
        assert m.find(b'abc', needle_pos - 1) == needle_pos
        m.close()


def test_getitem():
    m = mmap.mmap(-1, 12)
    for i in range(0, 12):
        m[i] = i
    assert m[slice(-10, 100)] == b'\x02\x03\x04\x05\x06\x07\x08\t\n\x0b'


def test_readline():
    m = mmap.mmap(-1, 9)
    for i in range(0, 9):
        m[i] = i
    m[4] = b'\n'[0]
    assert m.readline() == b'\x00\x01\x02\x03\n'
    assert m.readline() == b'\x05\x06\x07\x08'

    m = mmap.mmap(-1, 1024 + 3)
    m[1024] = b'\n'[0]
    m[1025] = b'a'[0]
    m[1026] = b'b'[0]
    assert m.readline() == (b'\x00' * 1024) + b'\n'
    assert m.readline() == b'ab'
