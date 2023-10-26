# Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
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
import signal
import subprocess
import unittest

import sys


class SignalTests(unittest.TestCase):
    def test_args_validation(self):
        try:
            import _signal
        except ImportError:
            import signal as _signal
        self.assertRaises(OverflowError, lambda : _signal.signal(0x8000000000000000, 0))
        self.assertRaises(OverflowError, lambda : _signal.signal(0x800000000000000, 0))
        self.assertRaises(TypeError, lambda : _signal.signal(_signal.SIGALRM, 0x100))
        self.assertRaises(TypeError, lambda : _signal.signal(_signal.SIGALRM, 'string'))


def test_alarm2():
    import time
    import signal

    class CustomError(RuntimeError):
        pass

    def handler(signum, frame):
        raise CustomError(signum, frame)

    oldhandler = signal.signal(signal.SIGALRM, handler)
    assert oldhandler == signal.SIG_DFL, "oldhandler != SIG_DFL"
    assert signal.getsignal(signal.SIGALRM) is handler, "getsignal handler != handler"

    signal.alarm(1)

    try:
        tries = 20
        while tries:
            time.sleep(0.5)
            tries -= 1
    except CustomError as e:
        assert e.args[0] == signal.SIGALRM
        assert e.args[1].f_code.co_name  # just check that we have access to the frame
    else:
        assert False, "Signal handler didn't trigger or propagate exception"


def test_interrupt():
    proc = subprocess.Popen(
        [sys.executable, '-c', 'import time; print("s", flush=True); time.sleep(5)'],
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )
    assert proc.stdout.read(2) == b's\n'
    proc.send_signal(signal.SIGINT)
    _, stderr = proc.communicate()
    assert b'KeyboardInterrupt' in stderr
    assert b'Traceback' in stderr
    # TODO we should properly make the process exit indicate the signal, but it might not be feasible under JVM/SVM
    # See CPython's main.c:exit_sigint for how they do it
    assert proc.wait() != 0
