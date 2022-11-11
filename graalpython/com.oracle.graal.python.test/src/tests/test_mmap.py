# Copyright (c) 2019, 2022, Oracle and/or its affiliates.
# Copyright (C) 1996-2017 Python Software Foundation
#
# Licensed under the PYTHON SOFTWARE FOUNDATION LICENSE VERSION 2
import os
import re
import unittest
from test.support import run_unittest
from test.support.import_helper import import_module
from test.support.os_helper import TESTFN

# Skip test if we can't import mmap.
mmap = import_module('mmap')

PAGESIZE = mmap.PAGESIZE

class MmapTests(unittest.TestCase):

    def setUpClass(self):
        if os.path.exists(TESTFN):
            os.unlink(TESTFN)

    def tearDownClass(self):
        try:
            os.unlink(TESTFN)
        except OSError:
            pass

    def test_basic(self):
        # Test mmap module on Unix systems and Windows

        # Create a file to be mmap'ed.
        f = open(TESTFN, 'bw+')
        try:
            # Write 2 pages worth of data to the file
            f.write(b'\0'* PAGESIZE)
            f.write(b'foo')
            f.write(b'\0'* (PAGESIZE-3) )
            f.flush()
            m = mmap.mmap(f.fileno(), 2 * PAGESIZE)
        finally:
            f.close()

        # Simple sanity checks

        tp = str(type(m))  # SF bug 128713:  segfaulted on Linux
        self.assertEqual(m.find(b'foo'), PAGESIZE)

        self.assertEqual(len(m), 2*PAGESIZE)

        self.assertEqual(m[0], 0)
        self.assertEqual(m[0:3], b'\0\0\0')

        # Shouldn't crash on boundary (Issue #5292)
        self.assertRaises(IndexError, m.__getitem__, len(m))
        self.assertRaises(IndexError, m.__setitem__, len(m), b'\0')

        # Modify the file's content
        m[0] = b'3'[0]
        m[PAGESIZE +3: PAGESIZE +3+3] = b'bar'

        # Check that the modification worked
        self.assertEqual(m[0], b'3'[0])
        self.assertEqual(m[0:3], b'3\0\0')
        self.assertEqual(m[PAGESIZE-1 : PAGESIZE + 7], b'\0foobar\0')

        m.flush()

        # Test doing a regular expression match in an mmap'ed file
        match = re.search(b'[A-Za-z]+', m)
        if match is None:
            self.fail('regex match on mmap failed!')
        else:
            start, end = match.span(0)
            length = end - start

            self.assertEqual(start, PAGESIZE)
            self.assertEqual(end, PAGESIZE + 6)

        # test seeking around (try to overflow the seek implementation)
        m.seek(0,0)
        self.assertEqual(m.tell(), 0)
        m.seek(42,1)
        self.assertEqual(m.tell(), 42)
        m.seek(0,2)
        self.assertEqual(m.tell(), len(m))

        # Try to seek to negative position...
        self.assertRaises(ValueError, m.seek, -1)

        # Try to seek beyond end of mmap...
        self.assertRaises(ValueError, m.seek, 1, 2)

        # Try to seek to negative position...
        self.assertRaises(ValueError, m.seek, -len(m)-1, 2)

        # Try resizing map
#         try:
#             m.resize(512)
#         except SystemError:
#             # resize() not supported
#             # No messages are printed, since the output of this test suite
#             # would then be different across platforms.
#             pass
#         else:
#             # resize() is supported
#             self.assertEqual(len(m), 512)
#             # Check that we can no longer seek beyond the new size.
#             self.assertRaises(ValueError, m.seek, 513, 0)
# 
#             # Check that the underlying file is truncated too
#             # (bug #728515)
#             f = open(TESTFN, 'rb')
#             try:
#                 f.seek(0, 2)
#                 self.assertEqual(f.tell(), 512)
#             finally:
#                 f.close()
#             self.assertEqual(m.size(), 512)

        m.close()


    def test_bad_file_desc(self):
        # Try opening a bad file descriptor...
        self.assertRaises(OSError, mmap.mmap, -2, 4096)


    def test_anonymous(self):
        # anonymous mmap.mmap(-1, PAGE)
        m = mmap.mmap(-1, PAGESIZE)
        for x in range(PAGESIZE):
            self.assertEqual(m[x], 0,
                             "anonymously mmap'ed contents should be zero")

        for x in range(PAGESIZE):
            b = x & 0xff
            m[x] = b
            self.assertEqual(m[x], b)

    def test_read_all(self):
        m = mmap.mmap(-1, 16)

        # With no parameters, or None or a negative argument, reads all
        m.write(bytes(range(16)))
        m.seek(0)
        self.assertEqual(m.read(), bytes(range(16)))
        m.seek(8)
        self.assertEqual(m.read(), bytes(range(8, 16)))
        m.seek(16)
        self.assertEqual(m.read(), b'')
        m.seek(3)
        self.assertEqual(m.read(None), bytes(range(3, 16)))
        m.seek(4)
        self.assertEqual(m.read(-1), bytes(range(4, 16)))
        m.seek(5)
        self.assertEqual(m.read(-2), bytes(range(5, 16)))
        m.seek(9)
        self.assertEqual(m.read(-42), bytes(range(9, 16)))
        m.close()



    def test_context_manager(self):
        with mmap.mmap(-1, 10) as m:
            self.assertFalse(m.closed)
        self.assertTrue(m.closed)

    def test_context_manager_exception(self):
        # Test that the OSError gets passed through
        with self.assertRaises(Exception) as exc:
            with mmap.mmap(-1, 10) as m:
                raise OSError
        self.assertIsInstance(exc.exception, OSError,
                              "wrong exception raised in context manager")
        self.assertTrue(m.closed, "context manager failed")


FIND_BUFFER_SIZE = 1024 # keep in sync with FindNode#BUFFER_SIZE
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
        assert m.find(b'abc', 0, needle_pos+2) == -1
        assert m.find(b'abc', 0, needle_pos+3) == needle_pos
        assert m.find(b'abc', needle_pos) == needle_pos
        assert m.find(b'abc', needle_pos+1) == -1
        assert m.find(b'abc', needle_pos-1) == needle_pos
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


def test_main():
    #run_unittest(MmapTests, LargeMmapTests)
    run_unittest(MmapTests)

if __name__ == '__main__':
    test_main()
