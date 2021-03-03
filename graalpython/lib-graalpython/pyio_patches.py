# Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
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
sys.path.append(__graalpython__.stdlib_home)
try:
    import _pyio
    import io
    import os
finally:
    assert len(sys.path) == 1
    sys.path.pop()


import _io
import builtins


# ----------------------------------------------------------------------------------------------------------------------
#
# patch _io
#
# ----------------------------------------------------------------------------------------------------------------------

for module in [_io, io]:
    setattr(module, 'TextIOWrapper', _pyio.TextIOWrapper)
    setattr(module, 'IncrementalNewlineDecoder', _pyio.IncrementalNewlineDecoder)
    setattr(module, 'BufferedRWPair', _pyio.BufferedRWPair)
    setattr(module, 'StringIO', _pyio.StringIO)
    setattr(module, 'BytesIO', _pyio.BytesIO)


# XXX: temporary until all necessary patches are removed.
@__graalpython__.builtin
def open(file, mode="r", buffering=-1, encoding=None, errors=None,
         newline=None, closefd=True, opener=None):
    if not isinstance(file, int):
        file = os.fspath(file)
    if not isinstance(file, (str, bytes, int)):
        raise TypeError("invalid file: %r" % file)
    if not isinstance(mode, str):
        raise TypeError("invalid mode: %r" % mode)
    if not isinstance(buffering, int):
        raise TypeError("invalid buffering: %r" % buffering)
    if encoding is not None and not isinstance(encoding, str):
        raise TypeError("invalid encoding: %r" % encoding)
    if errors is not None and not isinstance(errors, str):
        raise TypeError("invalid errors: %r" % errors)
    modes = set(mode)
    if modes - set("axrwb+tU") or len(mode) > len(modes):
        raise ValueError("invalid mode: %r" % mode)
    creating = "x" in modes
    reading = "r" in modes
    writing = "w" in modes
    appending = "a" in modes
    updating = "+" in modes
    text = "t" in modes
    binary = "b" in modes
    if "U" in modes:
        if creating or writing or appending or updating:
            raise ValueError("mode U cannot be combined with 'x', 'w', 'a', or '+'")
        import warnings
        warnings.warn("'U' mode is deprecated",
                      DeprecationWarning, 2)
        reading = True
    if text and binary:
        raise ValueError("can't have text and binary mode at once")
    if creating + reading + writing + appending > 1:
        raise ValueError("can't have read/write/append mode at once")
    if not (creating or reading or writing or appending):
        raise ValueError("must have exactly one of read/write/append mode")
    if binary and encoding is not None:
        raise ValueError("binary mode doesn't take an encoding argument")
    if binary and errors is not None:
        raise ValueError("binary mode doesn't take an errors argument")
    if binary and newline is not None:
        raise ValueError("binary mode doesn't take a newline argument")
    if binary and buffering == 1:
        import warnings
        warnings.warn("line buffering (buffering=1) isn't supported in binary "
                      "mode, the default buffer size will be used",
                      RuntimeWarning, 2)
    raw = io.FileIO(file,
                 (creating and "x" or "") +
                 (reading and "r" or "") +
                 (writing and "w" or "") +
                 (appending and "a" or "") +
                 (updating and "+" or ""),
                 closefd, opener=opener)
    result = raw
    try:
        line_buffering = False
        if buffering == 1 or buffering < 0 and raw.isatty():
            buffering = -1
            line_buffering = True
        if buffering < 0:
            buffering = io.DEFAULT_BUFFER_SIZE
            try:
                bs = os.fstat(raw.fileno()).st_blksize
            except (OSError, AttributeError):
                pass
            else:
                if bs > 1:
                    buffering = bs
        if buffering < 0:
            raise ValueError("invalid buffering size")
        if buffering == 0:
            if binary:
                return result
            raise ValueError("can't have unbuffered text I/O")
        if updating:
            buffer = io.BufferedRandom(raw, buffering)
        elif creating or writing or appending:
            buffer = io.BufferedWriter(raw, buffering)
        elif reading:
            buffer = io.BufferedReader(raw, buffering)
        else:
            raise ValueError("unknown mode: %r" % mode)
        result = buffer
        if binary:
            return result
        text = io.TextIOWrapper(buffer, encoding, errors, newline, line_buffering)
        result = text
        text.mode = mode
        return result
    except:
        result.close()
        raise

for module in [_io, io]:
    setattr(module, 'open', open)

setattr(builtins, 'open', open)

sys.stdin = _pyio.TextIOWrapper(_io.BufferedReader(sys.stdin), encoding=__graalpython__.stdio_encoding, errors=__graalpython__.stdio_error, line_buffering=True)
sys.stdin.mode = "r"
sys.__stdin__ = sys.stdin
sys.stdout = _pyio.TextIOWrapper(_pyio.BufferedWriter(sys.stdout), encoding=__graalpython__.stdio_encoding, errors=__graalpython__.stdio_error, line_buffering=True)
sys.stdout.mode = "w"
sys.__stdout__ = sys.stdout
sys.stderr = _pyio.TextIOWrapper(_pyio.BufferedWriter(sys.stderr.file_io), encoding=__graalpython__.stdio_encoding, errors="backslashreplace", line_buffering=True)
sys.stderr.mode = "w"
sys.__stderr__ = sys.stderr


# Try to close the std streams when we exit.
# To make this work reliably, we probably have to implement the _io module in Java
import atexit
def close_stdouts(so=sys.stdout, se=sys.stderr):
    try:
        so.close()
    except:
        pass
    try:
        se.close()
    except:
        pass
atexit.register(close_stdouts)


# See comment in _pyio.py. This method isn't strictly necessary and is provided
# on CPython for performance. Because it goes through memoryview, it is slower
# for us due to the overhead of memoryview being in C and the warmup cost
# associated with that. We remove it and rely on the (for us faster) base
# implementation.
del _pyio.BufferedReader._readinto
