# coding=utf-8
# Copyright (c) 2017, 2021, Oracle and/or its affiliates.
# Copyright (c) 2017, The PyPy Project
#
#     The MIT License
# Permission is hereby granted, free of charge, to any person
# obtaining a copy of this software and associated documentation
# files (the "Software"), to deal in the Software without
# restriction, including without limitation the rights to use,
# copy, modify, merge, publish, distribute, sublicense, and/or
# sell copies of the Software, and to permit persons to whom the
# Software is furnished to do so, subject to the following conditions:
#
# The above copyright notice and this permission notice shall be included
# in all copies or substantial portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS
# OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
# THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
# FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
# DEALINGS IN THE SOFTWARE.
import sys
_warn = sys.modules["_warnings"].warn
_os = sys.modules.get("posix", sys.modules.get("nt"))

SEEK_SET = 0
SEEK_CUR = 1
SEEK_END = 2

class BlockingIOError(OSError):
    pass


class UnsupportedOperation(OSError, ValueError):
    pass


class StdPrinter:
    def __init__(self, file_io):
        self.file_io = file_io

    def write(self, data):
        return self.file_io.write(bytes(data, "utf-8"))

    def __getattr__(self, attr):
        return self.file_io.__getattribute__(attr)


sys.stdin = FileIO(0, mode='r', closefd=False)
sys.stdin.name = "<stdin>"
sys.__stdin__ = sys.stdin
sys.stdout = FileIO(1, mode='w', closefd=False)
sys.stdout.name = "<stdout>"
sys.__stdout__ = sys.stdout
sys.stderr = StdPrinter(FileIO(2, mode='w', closefd=False))
sys.stderr.name = "<stderr>"
sys.__stderr__ = sys.stderr


# PEP 578 stub
def open_code(path):
    return FileIO(path, 'rb')


# ----------------------------------------------------------------------------------------------------------------------
#
# following definitions: patched in the pyio_patches module
#
# ----------------------------------------------------------------------------------------------------------------------


class BytesIO(_BufferedIOBase):
    pass



class StringIO(_TextIOBase):
    pass



class BufferedRWPair(_BufferedIOBase):
    pass


class IncrementalNewlineDecoder(object):
    pass


class TextIOWrapper(_TextIOBase):
    pass


def open(*args, **kwargs):
    raise NotImplementedError


# ----------------------------------------------------------------------------------------------------------------------
#
# needed for imports will be patched in the pyio_patches module
#
# ----------------------------------------------------------------------------------------------------------------------
import builtins
setattr(builtins, 'open', open)
globals()['open'] = open
