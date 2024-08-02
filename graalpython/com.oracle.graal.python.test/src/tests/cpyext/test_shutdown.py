# Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import os
import signal
import subprocess
from pathlib import Path

import sys

DIR = Path(__file__).parent
MODULE_PATH = DIR / 'module_with_native_destructor.py'
ENV = dict(os.environ)
ENV['PYTHONPATH'] = str(DIR.parent.parent)
ARGS = []
if sys.implementation.name == 'graalpy':
    ARGS = ['--python.EnableDebuggingBuiltins']
    if not __graalpython__.is_native and __graalpython__.is_bytecode_dsl_interpreter:
        ARGS += ['--vm.Dpython.EnableBytecodeDSLInterpreter=true']
COMMAND = [sys.executable, *ARGS, str(MODULE_PATH)]


# Test that running Py_DECREF in native global destructor doesn't crash
def test_normal_exit():
    subprocess.run(COMMAND, check=True, env=ENV)


def test_sigterm():
    proc = subprocess.Popen([*COMMAND, "sleep"], env=ENV, stdout=subprocess.PIPE)
    expected = b'sleeping\n'
    assert proc.stdout.read(len(expected)) == expected
    proc.terminate()
    assert proc.wait() in [-signal.SIGTERM, 128 + signal.SIGTERM]
