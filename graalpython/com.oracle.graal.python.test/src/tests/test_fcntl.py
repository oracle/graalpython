# Copyright (c) 2020, 2024, Oracle and/or its affiliates. All rights reserved.
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

try:
    __graalpython__.posix_module_backend()
except:
    class GP:
        def posix_module_backend(self):
            return 'cpython'
    __graalpython__ = GP()

import fcntl
import os
import subprocess
import tempfile
import time
import unittest
import sys

PREFIX = 'select_graalpython_test'
TEMP_DIR = tempfile.gettempdir()
TEST_FILENAME = f'{PREFIX}_{os.getpid()}_tmp_fcntl'
TEST_FILENAME_FULL_PATH = os.path.join(TEMP_DIR, TEST_FILENAME)

def log(msg):
    # print(msg)
    pass

def python_flock_blocks_sh_flock(python_flock_type, sh_flock_type):
    os.close(os.open(TEST_FILENAME_FULL_PATH, os.O_WRONLY | os.O_CREAT))
    file = os.open(TEST_FILENAME_FULL_PATH, os.O_WRONLY)
    try:
        fcntl.flock(file, python_flock_type)
        p = subprocess.Popen("flock -%s %s -c 'exit 42'" % (sh_flock_type, TEST_FILENAME_FULL_PATH), shell=True)
        log("sleeping...")
        time.sleep(0.25)
        assert p.poll() is None # the process should be still waiting for the lock
        log("unlocking the file...")
        fcntl.flock(file, fcntl.LOCK_UN) # release the lock
        log("checking the retcode...")
        time.sleep(0.25)
        assert p.poll() == 42
        log(f"{p.returncode=}")
    finally:
        fcntl.flock(file, fcntl.LOCK_UN)
        os.close(file)

class FcntlTests(unittest.TestCase):
    @unittest.skipUnless(__graalpython__.posix_module_backend() != 'java', 'No support in Truffle API (GR-28740)')
    @unittest.skipUnless(sys.platform != 'darwin', 'MacOSX does not have flock utility')
    def test_flock_x_and_x(self):
        python_flock_blocks_sh_flock(fcntl.LOCK_EX, 'x')

    @unittest.skipUnless(__graalpython__.posix_module_backend() != 'java', 'No support in Truffle API (GR-28740)')
    @unittest.skipUnless(sys.platform != 'darwin', 'MacOSX does not have flock utility')
    def test_flock_x_and_s(self):
        python_flock_blocks_sh_flock(fcntl.LOCK_EX, 's')

    @unittest.skipUnless(__graalpython__.posix_module_backend() != 'java', 'No support in Truffle API (GR-28740)')
    @unittest.skipUnless(sys.platform != 'darwin', 'MacOSX does not have flock utility')
    def test_flock_s_and_x(self):
        python_flock_blocks_sh_flock(fcntl.LOCK_SH, 'x')

    @unittest.skipUnless(__graalpython__.posix_module_backend() != 'java', 'No support in Truffle API (GR-28740)')
    @unittest.skipUnless(sys.platform != 'darwin', 'MacOSX does not have flock utility')
    @unittest.skipUnless("graalpython" in os.environ.get("BITBUCKET_REPO_URL", "graalpython"), "Do not run this in auxillary CI jobs, it can be flaky")
    def test_flock_s_and_s(self):
        os.close(os.open(TEST_FILENAME_FULL_PATH, os.O_WRONLY | os.O_CREAT))
        file = os.open(TEST_FILENAME_FULL_PATH, os.O_WRONLY)
        try:
            fcntl.flock(file, fcntl.LOCK_SH)
            p = subprocess.Popen("flock -s %s -c 'exit 42'" % TEST_FILENAME_FULL_PATH, shell=True)
            time.sleep(0.25)
            assert p.poll() == 42
        finally:
            fcntl.flock(file, fcntl.LOCK_UN)
            os.close(file)


class IoctlTests(unittest.TestCase):
    # Taken from CPython test_ioctl.py which unfortunately skips the whole file when not in a terminal
    def test_ioctl_signed_unsigned_code_param(self):
        import pty, termios, struct
        mfd, sfd = pty.openpty()
        try:
            if termios.TIOCSWINSZ < 0:
                set_winsz_opcode_maybe_neg = termios.TIOCSWINSZ
                set_winsz_opcode_pos = termios.TIOCSWINSZ & 0xffffffff
            else:
                set_winsz_opcode_pos = termios.TIOCSWINSZ
                set_winsz_opcode_maybe_neg, = struct.unpack("i",
                                                            struct.pack("I", termios.TIOCSWINSZ))

            our_winsz = struct.pack("HHHH",80,25,0,0)
            # test both with a positive and potentially negative ioctl code
            new_winsz = fcntl.ioctl(mfd, set_winsz_opcode_pos, our_winsz)
            new_winsz = fcntl.ioctl(mfd, set_winsz_opcode_maybe_neg, our_winsz)
        finally:
            os.close(mfd)
            os.close(sfd)
