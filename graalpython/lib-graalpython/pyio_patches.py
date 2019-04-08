# Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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

# at this point during context startup, sys.path isn't initialized, so we need
# to set it up
import sys
sys.path.append(sys.graal_python_stdlib_home)
try:
    import _pyio
    import io
finally:
    assert len(sys.path) == 1
    sys.path.pop()


import _io
import _sysconfig
import builtins


# ----------------------------------------------------------------------------------------------------------------------
#
# patch _io
#
# ----------------------------------------------------------------------------------------------------------------------

@__builtin__
def open(*args, **kwargs):
    return _pyio.open(*args, **kwargs)


for module in [_io, io]:
    setattr(module, 'open', open)
    setattr(module, 'TextIOWrapper', _pyio.TextIOWrapper)
    setattr(module, 'IncrementalNewlineDecoder', _pyio.IncrementalNewlineDecoder)
    setattr(module, 'BufferedRandom', _pyio.BufferedRandom)
    setattr(module, 'BufferedRWPair', _pyio.BufferedRWPair)
    setattr(module, 'BufferedWriter', _pyio.BufferedWriter)
    setattr(module, 'BufferedReader', _pyio.BufferedReader)
    setattr(module, 'StringIO', _pyio.StringIO)
    setattr(module, '_IOBase', _pyio.IOBase)
    setattr(module, 'BufferedIOBase', _pyio.BufferedIOBase)
    setattr(module, 'RawIOBase', _pyio.RawIOBase)
    setattr(module, 'FileIO', _pyio.FileIO)
    setattr(module, 'BytesIO', _pyio.BytesIO)
    setattr(module, '_TextIOBase', _pyio.TextIOBase)


setattr(builtins, 'open', open)


sys.stdin = _pyio.TextIOWrapper(_pyio.BufferedReader(sys.stdin), encoding="utf-8", line_buffering=True)
sys.stdin.mode = "r"
sys.__stdin__ = sys.stdin
sys.stdout = _pyio.TextIOWrapper(_pyio.BufferedWriter(sys.stdout), encoding="utf-8", line_buffering=True)
sys.stdout.mode = "w"
sys.__stdout__ = sys.stdout
sys.stderr = _pyio.TextIOWrapper(_pyio.BufferedWriter(sys.stderr), encoding="utf-8", line_buffering=True)
sys.stderr.mode = "w"
sys.__stderr__ = sys.stderr
