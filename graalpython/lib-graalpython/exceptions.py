# Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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

# ----------------------------------------------------------------------------------------------------------------------
#
# the exceptions / errors
#
# ----------------------------------------------------------------------------------------------------------------------

def SystemExit__init__(self, *args):
    if len(args) > 1:
        self.code = args
    elif len(args) == 1:
        self.code = args[0]
    else:
        self.code = 0
    BaseException.__init__(self, *args)

SystemExit.__init__ = SystemExit__init__
del SystemExit__init__

def ImportError__init__(self, *args, name=None, path=None, **kwargs):
    if kwargs:
        kwarg = next(iter(kwargs))
        raise TypeError(f"'{kwarg}' is an invalid keyword argument for ImportError")
    BaseException.__init__(self, *args)
    self.msg = args[0] if args else None
    self.name = name
    self.path = path

ImportError.__init__ = ImportError__init__
del ImportError__init__

# EnvironmentError is just an alias of OSError (i.e. 'EnvironmentError is OSError == True')
EnvironmentError = OSError


def StopIteration__value__get(self):
    if not hasattr(self, "__value__"):
        if self.args:
            self.__value__ = self.args[0]
        else:
            self.__value__ = None
    return self.__value__


def StopIteration__value__set(self, arg):
    self.__value__ = arg


StopIteration.value = property(fget=StopIteration__value__get, fset=StopIteration__value__set)


def SyntaxError__init__(self, *args, **kwargs):
    BaseException.__init__(self, *args, **kwargs)
    self.msg = None
    self.filename = None
    self.lineno = None
    self.offset = None
    self.text = None
    self.print_file_and_line = None
    if len(args) > 0:
        self.msg = args[0]
    if len(args) == 2:
        info = tuple(args[1])
        if len(info) != 4:
            raise IndexError("tuple index out of range")
        self.filename = info[0]
        self.lineno = info[1]
        self.offset = info[2]
        self.text = info[3]


SyntaxError.__init__ = SyntaxError__init__
del SyntaxError__init__


def UnicodeEncodeError__init__(self, encoding, object, start, end, reason):
    BaseException.__init__(self, encoding, object, start, end, reason)
    self.encoding = encoding
    self.object = object
    self.start = start
    self.end = end
    self.reason = reason


UnicodeEncodeError.__init__ = UnicodeEncodeError__init__
del UnicodeEncodeError__init__


def UnicodeDecodeError__init__(self, encoding, object, start, end, reason):
    BaseException.__init__(self, encoding, object, start, end, reason)
    self.encoding = encoding
    if isinstance(object, bytes):
        self.object = object
    else:
        self.object = bytes(object)
    self.start = start
    self.end = end
    self.reason = reason


UnicodeDecodeError.__init__ = UnicodeDecodeError__init__
del UnicodeDecodeError__init__


def UnicodeTranslateError__init__(self, object, start, end, reason):
    self.object = object
    self.start = start
    self.end = end
    self.reason = reason


UnicodeTranslateError.__init__ = UnicodeTranslateError__init__
del UnicodeTranslateError__init__


# These errors are just an alias of OSError (i.e. 'EnvironmentError is OSError == True')
EnvironmentError = OSError
IOError = OSError

import errno

_errnomap = {
    errno.EISDIR: IsADirectoryError,
    errno.EAGAIN: BlockingIOError,
    errno.EALREADY: BlockingIOError,
    errno.EINPROGRESS: BlockingIOError,
    errno.EWOULDBLOCK: BlockingIOError,
    errno.EPIPE: BrokenPipeError,
    errno.ESHUTDOWN: BrokenPipeError,
    errno.ECHILD: ChildProcessError,
    errno.ECONNABORTED: ConnectionAbortedError,
    errno.ECONNREFUSED: ConnectionRefusedError,
    errno.ECONNRESET: ConnectionResetError,
    errno.EEXIST: FileExistsError,
    errno.ENOENT: FileNotFoundError,
    errno.ENOTDIR: NotADirectoryError,
    errno.EINTR: InterruptedError,
    errno.EACCES: PermissionError,
    errno.EPERM: PermissionError,
    errno.ESRCH: ProcessLookupError,
    errno.ETIMEDOUT: TimeoutError
}

def _oserror_use_init(subtype):
    return subtype.__init__ is not OSError.__init__ and subtype.__new__ is OSError.__new__

def _oserror_init(self, *arg):
    narg = len(arg)
    self.args = arg
    self.errno = None
    self.strerror = None
    self.filename = None
    self.filename2 = None
    if (2 <= narg and narg <= 5):
        self.args = arg[0:2]
        self.errno = arg[0]
        self.strerror = arg[1]
        if(narg >= 5):
            self.filename2 = arg[4]
        if(narg >= 3):
            if type(self) == BlockingIOError:
                try:
                    self.characters_written = arg[2].__index__()
                except Exception:
                    self.filename = arg[2]
            else:
                self.filename = arg[2]

def OSError__new__(subtype, *args, **kwds):
    newtype = subtype
    if (not _oserror_use_init(newtype) and len(args) > 1):
        myerrno = args[0]
        if (type(myerrno) is int and subtype is OSError and myerrno in _errnomap):
            newtype = _errnomap[myerrno]

    self = BaseException.__new__(newtype)
    self.errno = self.strerror = self.filename = self.filename2 = None
    if (not _oserror_use_init(newtype)):
        _oserror_init(self, *args)
    return self

def OSError__init__(self, *args, **kwds):
    if (not _oserror_use_init(type(self))):
        return None
    _oserror_init(self, *args)

def OSError__str__(self):
    if (self.filename):
        if(self.filename2):
            return "[Errno %s] %s: %s -> %s" % (self.errno, self.strerror, self.filename, self.filename2)
        else:
            return "[Errno %s] %s: %s" % (self.errno, self.strerror, self.filename)
    if(self.errno and self.strerror):
        return "[Errno %s] %s" % (self.errno, self.strerror)
    return BaseException.__str__(self)

OSError.__new__ = OSError__new__
OSError.__init__ = OSError__init__
OSError.__str__ = OSError__str__
OSError.errno = -1
OSError.strerror = None
OSError.filename = None
OSError.filename2 = None
del OSError__init__
del OSError__new__
del OSError__str__

