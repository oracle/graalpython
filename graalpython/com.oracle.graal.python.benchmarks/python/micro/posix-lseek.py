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

"""Reposition an open descriptor within a cached file."""

import os
import tempfile

_DIRECTORY = None
_FD = None


def __setup__(num=40000):
    global _DIRECTORY, _FD
    _DIRECTORY = tempfile.TemporaryDirectory(prefix="graalpy-posix-lseek-")
    path = os.path.join(_DIRECTORY.name, "data")
    _FD = os.open(path, os.O_CREAT | os.O_RDWR, 0o600)
    os.ftruncate(_FD, 4 * 1024 * 1024)


def __benchmark__(num=40000):
    total = 0
    for i in range(num):
        total += os.lseek(_FD, (i & 1023) * 4096, os.SEEK_SET)
    return total


def __teardown__():
    global _DIRECTORY, _FD
    if _FD is not None:
        os.close(_FD)
        _FD = None
    if _DIRECTORY is not None:
        _DIRECTORY.cleanup()
        _DIRECTORY = None
