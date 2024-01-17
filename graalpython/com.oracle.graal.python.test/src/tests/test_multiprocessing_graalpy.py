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
import multiprocessing
from multiprocessing.connection import wait
from functools import wraps
from dataclasses import dataclass

import os
import sys
import time


if sys.implementation.name == 'graalpy':
    def graalpy_multiprocessing(test):
        @wraps(test)
        def set_graalpy():
            original_ctx = multiprocessing.get_context().get_start_method()
            try:
                multiprocessing.set_start_method('graalpy', force=True)
                test()
            finally:
                multiprocessing.set_start_method(original_ctx, force=True)
        return set_graalpy


    @graalpy_multiprocessing
    def test_SemLock_raises_on_non_string_name():
        from _multiprocessing_graalpy import SemLock
        try:
            SemLock(kind=1, value=1, name={1: 2}, maxvalue=1, unlink=1)
        except TypeError:
            pass
        else:
            assert False


    @graalpy_multiprocessing
    def test_wait_timeout():
        timeout = 3
        a, b = multiprocessing.Pipe()
        x, y = multiprocessing.connection.Pipe(False)  # Truffle multiprocessing pipe
        for fds in [[a, b], [x, y], [a, b, x, y]]:
            start = time.monotonic()
            res = wait(fds, timeout)
            delta = time.monotonic() - start
            assert not res
            assert delta < timeout * 2
            assert delta > timeout / 2


    @graalpy_multiprocessing
    def test_wait():
        a, b = multiprocessing.Pipe()
        x, y = multiprocessing.connection.Pipe(False)  # Truffle multiprocessing pipe
        a.send(42)
        res = wait([b, y], 3)
        assert res == [b], "res1"
        assert b.recv() == 42, "res2"
        y.send(33)
        res = wait([b, x], 3)
        assert res == [x], "res3"
        assert x.recv() == 33, "res4"
