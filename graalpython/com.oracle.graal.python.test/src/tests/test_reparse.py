# Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

import compileall
import contextlib
import os
import re
import socket
import subprocess
import sys
import tempfile
import time
import unittest
from pathlib import Path

SYNC_PREAMBLE = '''
import sys
import socket

with socket.create_connection(('localhost', int(sys.argv[1]))) as sock:
    sock.recv(1)
'''


@contextlib.contextmanager
def pyc_reparse(test_content, expect_success=True, python_options=()):
    if sys.implementation.name != "graalpy" or not __graalpython__.is_bytecode_dsl_interpreter:
        raise unittest.SkipTest("Reparsing tests are only meaningful on bytecode DSL interpreter")
    with tempfile.TemporaryDirectory() as tempdir:
        tempdir_path = Path(tempdir)
        example_module_path = tempdir_path / "example.py"
        with open(example_module_path, "w") as f:
            f.write(SYNC_PREAMBLE)
            f.write(test_content)
        # Change mtime of the example module source to the past a bit to avoid mtime resolution issues
        os.utime(example_module_path, (time.time() - 1000, time.time() - 1000))
        compileall.compile_file(example_module_path, force=True, quiet=True)
        pyc_files = list((tempdir_path / '__pycache__').glob('*.pyc'))
        assert len(pyc_files) == 1, "Didn't find a .pyc file"
        with socket.create_server(('0.0.0.0', 0)) as server:
            port = server.getsockname()[1]
            env = os.environ.copy()
            env['PYTHONPATH'] = str(tempdir_path)
            proc = subprocess.Popen(
                [sys.executable, *python_options, "-m", "example", str(port)],
                env=env,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                text=True,
            )
            server.settimeout(3.0)
            retries = 20
            while retries:
                try:
                    with server.accept()[0] as sock:
                        yield example_module_path, pyc_files[0]
                        sock.sendall(b"x")
                    break
                except socket.timeout:
                    assert proc.poll() is None, proc.communicate()[0]
                    retries -= 1
            else:
                assert False, "Timed out wating for connection"
            out = proc.communicate()[0]
            if expect_success:
                assert proc.wait() == 0, out
            else:
                assert proc.wait() == 1 and re.search(r"SystemError:.*--python\.KeepBytecodeInMemory", out), out


TRACING_TEST = '''
import sys

def foo():
    a = 42
    return a

lines = []

def tracefunc(frame, event, arg):
    if event == "line" and frame.f_code is foo.__code__:
        lines.append(frame.f_lineno)
    return tracefunc

sys.settrace(tracefunc)
assert foo() == 42
firstlineno = foo.__code__.co_firstlineno
assert lines == [firstlineno + 1, firstlineno + 2], "Code didn't trace when expected"
'''


def test_reparse():
    with pyc_reparse(TRACING_TEST):
        pass


def test_reparse_deleted():
    with pyc_reparse(TRACING_TEST, expect_success=False) as (example_file, pyc_file):
        pyc_file.unlink()


def test_reparse_truncated():
    with pyc_reparse(TRACING_TEST, expect_success=False) as (example_file, pyc_file):
        with open(pyc_file, 'r+') as f:
            f.truncate()


def test_reparse_truncated_part():
    with pyc_reparse(TRACING_TEST, expect_success=False) as (example_file, pyc_file):
        with open(pyc_file, 'r+') as f:
            f.truncate(30)


def test_reparse_modified():
    with pyc_reparse(TRACING_TEST, expect_success=False) as (example_file, pyc_file):
        pyc_file.unlink()
        with open(example_file, 'w') as f:
            f.write(SYNC_PREAMBLE)
            f.write(TRACING_TEST.replace('a = 42', 'a = 32'))
        compileall.compile_file(example_file, force=True, quiet=True)
        assert pyc_file.exists()


def test_reparse_disabled():
    with pyc_reparse(TRACING_TEST, python_options=["--python.KeepBytecodeInMemory"], expect_success=True) \
            as (example_file, pyc_file):
        pyc_file.unlink()


CO_CODE_TEST = '''
def foo():
    a = 42
    return a

assert foo() == 42
foo.__code__ = foo.__code__.replace(co_code=foo.__code__.co_code)
assert foo() == 42
'''


def test_reparse_co_code():
    with pyc_reparse(CO_CODE_TEST):
        pass


def test_reparse_co_code_deleted():
    with pyc_reparse(CO_CODE_TEST, expect_success=False) as (example_file, pyc_file):
        pyc_file.unlink()
