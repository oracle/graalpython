# Copyright (c) 2018, 2026, Oracle and/or its affiliates. All rights reserved.
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
import sys
import unittest


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


def test_itimer():
    assert signal.getitimer(signal.ITIMER_REAL) == (0.0, 0.0)
    try:
        old = signal.setitimer(signal.ITIMER_REAL, 1.0, 0.25)
        assert old == (0.0, 0.0), old
        current = signal.getitimer(signal.ITIMER_REAL)
        assert 0.0 <= current[0] <= 1.0, current
        assert current[1] == 0.25, current
        old = signal.setitimer(signal.ITIMER_REAL, 0)
        assert 0.0 <= old[0] <= 1.0, old
        assert old[1] == 0.25, old
    finally:
        signal.setitimer(signal.ITIMER_REAL, 0)

    for func, args in (
        (signal.getitimer, (-1,)),
        (signal.setitimer, (-1, 0)),
        (signal.setitimer, (signal.ITIMER_REAL, -1)),
    ):
        try:
            func(*args)
        except signal.ItimerError as e:
            assert e.errno == 22, e
        else:
            raise AssertionError(f"{func.__name__}{args} did not raise ItimerError")


def test_emulated_timers_use_current_handler():
    if sys.implementation.name != 'graalpy' or __graalpython__.posix_module_backend() != 'java':
        return

    import time

    calls = []

    def first(signum, frame):
        calls.append(("first", signum, frame))

    def second(signum, frame):
        calls.append(("second", signum, frame))

    def wait_for_call(timeout=2.5):
        deadline = time.time() + timeout
        while time.time() < deadline:
            if calls:
                return True
            time.sleep(0.01)
        return False

    old_handler = signal.signal(signal.SIGALRM, first)
    try:
        signal.alarm(1)
        signal.signal(signal.SIGALRM, second)
        assert wait_for_call(), "alarm did not trigger handler"
        assert calls[0][0] == "second", calls
        assert calls[0][1] == signal.SIGALRM, calls
        assert calls[0][2].f_code.co_name

        calls.clear()
        signal.signal(signal.SIGALRM, first)
        signal.setitimer(signal.ITIMER_REAL, 0.05)
        signal.signal(signal.SIGALRM, second)
        assert wait_for_call(), "setitimer did not trigger handler"
        assert calls[0][0] == "second", calls
        assert calls[0][1] == signal.SIGALRM, calls
        assert calls[0][2].f_code.co_name

        calls.clear()
        signal.signal(signal.SIGALRM, signal.SIG_IGN)
        signal.setitimer(signal.ITIMER_REAL, 0.05)
        time.sleep(0.2)
        assert calls == []
    finally:
        signal.alarm(0)
        signal.setitimer(signal.ITIMER_REAL, 0)
        signal.signal(signal.SIGALRM, old_handler)


def test_interrupt():
    if sys.implementation.name == 'graalpy' and __graalpython__.posix_module_backend() == 'java':
        # Sending SIGINT does not work when using the Java backend for posix
        return
    proc = subprocess.Popen(
        [sys.executable, '-c', 'import time; print("s", flush=True); time.sleep(60)'],
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )
    actual_out = proc.stdout.read(2)
    assert actual_out == b's\n', f"{actual_out=}, {proc.stderr.read()=}"

    proc.send_signal(signal.SIGINT)
    _, stderr = proc.communicate()
    # TODO we should properly make the process exit indicate the signal, but it might not be feasible under JVM/SVM
    # See CPython's main.c:exit_sigint for how they do it
    assert proc.wait() != 0
    assert b'KeyboardInterrupt' in stderr, f"Unexpected stderr: {stderr}"
    assert b'Traceback' in stderr, f"Unexpected stderr: {stderr}"
