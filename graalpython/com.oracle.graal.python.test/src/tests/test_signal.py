# Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

def skip_test_alarm():
    # (tfel): this test is very brittle, because it is supposed to work with our
    # first, very primitive implementation of signal handlers, which does not
    # allow Python code to run in the handler. So we rely on a side-effect on an
    # open file descriptor instead.
    try:
        import _signal
    except ImportError:
        import signal as _signal
    import posix
    import time
    import sys

    # first, we start opening files until the fd is the same as SIGALRM
    fds = []
    dupd_fd = None
    fd = None

    try:
        fd = posix.open(__file__, posix.O_RDONLY)
        while fd < _signal.SIGALRM:
            fds.append(fd)
            fd = posix.open(__file__, posix.O_RDONLY)

        if fd > _signal.SIGALRM:
            dupd_fd = posix.dup(_signal.SIGALRM)
            posix.close(_signal.SIGALRM)
            fd = posix.open(__file__, posix.O_RDONLY)

        # close the unneeded fds
        for oldfd in fds:
            posix.close(oldfd)

        assert fd == _signal.SIGALRM, "fd not equal to SIGALRM"

        # temporary: graalpython doesn't check the argcount for the handler atm
        if sys.implementation.name == "graalpython":
            handler = posix.close
        else:
            handler = lambda s,f: posix.close(s)

        oldhandler = _signal.signal(_signal.SIGALRM, handler)
        assert oldhandler == _signal.SIG_DFL, "oldhandler != SIG_DFL"
        assert _signal.getsignal(_signal.SIGALRM) is handler, "getsignal handler != handler"

        # schedule the alarm signal, that will trigger the handler, which
        # will in turn close our file
        _signal.alarm(1)

        # wait for the signal to come in and be handled
        time.sleep(1.5)

        # check for the side-effect
        try:
            posix.read(fd, 1)
        except OSError:
            assert True
        else:
            assert False, "file is still open"
    finally:
        if dupd_fd is not None:
            try:
                posix.close(fd)
            except OSError:
                pass
            posix.dup(dupd_fd) # duplicates back into just free'd fd
