# Copyright (c) 2018, 2019, Oracle and/or its affiliates.
# Copyright (C) 1996-2017 Python Software Foundation
#
# Licensed under the PYTHON SOFTWARE FOUNDATION LICENSE VERSION 2
import unittest


def test_os_pipe():
    import os
    r,w = os.pipe()
    written = os.write(w, b"hello")
    assert os.read(r, written) == b"hello"


class TestSubprocess(unittest.TestCase):
    def setUp(self):
        global subprocess, io, sys
        import subprocess, io, sys

    def test_io_buffered_by_default(self):
        p = subprocess.Popen([sys.executable, "-c", "import sys; sys.exit(0)"],
                             stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                             stderr=subprocess.PIPE)
        try:
            self.assertIsInstance(p.stdin, io.BufferedIOBase)
            self.assertIsInstance(p.stdout, io.BufferedIOBase)
            self.assertIsInstance(p.stderr, io.BufferedIOBase)
        finally:
            p.stdin.close()
            p.stdout.close()
            p.stderr.close()
            p.wait()

    def test_io_unbuffered_works(self):
        p = subprocess.Popen([sys.executable, "-c", "import sys; sys.exit(0)"],
                             stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                             stderr=subprocess.PIPE, bufsize=0)
        try:
            self.assertIsInstance(p.stdin, io.RawIOBase)
            self.assertIsInstance(p.stdout, io.RawIOBase)
            self.assertIsInstance(p.stderr, io.RawIOBase)
        finally:
            p.stdin.close()
            p.stdout.close()
            p.stderr.close()
            p.wait()

    def test_check_call_zero(self):
        # check_call() function with zero return code
        rc = subprocess.check_call([sys.executable, "-c",
                                    "import sys; sys.exit(0)"])
        self.assertEqual(rc, 0)

    def test_check_call_nonzero(self):
        # check_call() function with non-zero return code
        with self.assertRaises(subprocess.CalledProcessError) as c:
            subprocess.check_call([sys.executable, "-c",
                                   "import sys; sys.exit(47)"])
        self.assertEqual(c.exception.returncode, 47)

    def test_check_output(self):
        # check_output() function with zero return code
        output = subprocess.check_output(
                [sys.executable, "-c", "print('BDFL')"])
        self.assertIn(b'BDFL', output)

    def test_check_output_nonzero(self):
        # check_call() function with non-zero return code
        with self.assertRaises(subprocess.CalledProcessError) as c:
            subprocess.check_output(
                    [sys.executable, "-c", "import sys; sys.exit(5)"])
        self.assertEqual(c.exception.returncode, 5)

    def test_check_output_stdout_arg(self):
        # check_output() refuses to accept 'stdout' argument
        with self.assertRaises(ValueError) as c:
            output = subprocess.check_output(
                    [sys.executable, "-c", "print('will not be run')"],
                    stdout=sys.stdout)
            self.fail("Expected ValueError when stdout arg supplied.")
        self.assertIn('stdout', c.exception.args[0])

    def test_kill(self):
        p = subprocess.Popen([sys.executable, "-c", "print('oh no')"])
        p.kill()
        assert True

    def test_waitpid(self):
        import os
        p = subprocess.Popen([sys.executable, "-c", "import time; time.sleep(2); 42"])
        try:
            assert os.waitpid(p.pid, os.WNOHANG) == (0, 0)
        finally:
            p.kill()
