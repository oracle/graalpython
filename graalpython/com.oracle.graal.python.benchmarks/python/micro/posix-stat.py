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

"""Look up metadata for existing paths, as import and pathlib code commonly do."""

import os
import tempfile

_DIRECTORY = None
_PATHS = None


def __setup__(num=40000):
    global _DIRECTORY, _PATHS
    _DIRECTORY = tempfile.TemporaryDirectory(prefix="graalpy-posix-stat-")
    paths = []
    for i in range(16):
        path = os.path.join(_DIRECTORY.name, "file-%02d" % i)
        with open(path, "wb") as stream:
            stream.write(b"x")
        paths.append(path)
    _PATHS = tuple(paths)


def __benchmark__(num=40000):
    total = 0
    for i in range(num):
        total += os.stat(_PATHS[i & 15]).st_size
    return total


def __teardown__():
    global _DIRECTORY, _PATHS
    _PATHS = None
    if _DIRECTORY is not None:
        _DIRECTORY.cleanup()
        _DIRECTORY = None
