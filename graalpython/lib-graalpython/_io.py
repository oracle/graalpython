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

class FileIO(_RawIOBase):
    @staticmethod
    def __isdir__(mode):
        # We cannot import the stat module here
        return (mode & 0o170000) == 0o040000

    @staticmethod
    def __decode_mode__(mode):
        O_BINARY = getattr(_os, "O_BINARY", 0)
        O_APPEND = getattr(_os, "O_APPEND", 0)

        _bad_mode = ValueError("Must have exactly one of read/write/create/append mode")

        flags = 0
        rwa = False
        readable = False
        writable = False
        created = False
        append = False
        plus = False

        for s in mode:
            if s == 'r':
                if rwa:
                    raise _bad_mode
                rwa = True
                readable = True
            elif s == 'w':
                if rwa:
                    raise _bad_mode
                rwa = True
                writable = True
                flags |= _os.O_CREAT | _os.O_TRUNC
            elif s == 'x':
                if rwa:
                    raise _bad_mode
                rwa = True
                created = True
                writable = True
                flags |= _os.O_EXCL | _os.O_CREAT
            elif s == 'a':
                if rwa:
                    raise _bad_mode
                rwa = True
                writable = True
                append = True
                flags |= O_APPEND | _os.O_CREAT
            elif s == 'b':
                pass
            elif s == '+':
                if plus:
                    raise _bad_mode
                readable = writable = True
                plus = True
            else:
                raise ValueError("invalid mode: %s" % mode)

        if not rwa:
            raise _bad_mode

        if readable and writable:
            flags |= _os.O_RDWR
        elif readable:
            flags |= _os.O_RDONLY
        else:
            flags |= _os.O_WRONLY

        flags |= O_BINARY

        return readable, writable, created, append, flags

    def __init__(self, name, mode='r', closefd=True, opener=None):
        _RawIOBase.__init__(self)
        self.__fd__ = -1
        self.__readable__ = False
        self.__writable__ = False
        self.__created__ = False
        self.__appending__ = False
        self.__seekable__ = -1
        self.__closefd__ = True
        self.name = None

        if self.__fd__ >= 0:
            if self.__closefd__:
                self.close()
            else:
                self.__fd__ = -1

        if isinstance(name, float):
            raise TypeError("integer argument expected, got float")

        fd = -1
        try:
            fd = int(name)
        except:
            pass
        else:
            if fd < 0:
                raise ValueError("negative file descriptor")

        self.__readable__, self.__writable__, self.__created__, self.__appending__, flags = FileIO.__decode_mode__(mode)

        fd_is_own = False
        try:
            if fd >= 0:
                self.__fd__ = fd
                self.__closefd__ = bool(closefd)
            else:
                self.__closefd__ = True
                if not closefd:
                    raise ValueError("Cannot use closefd=False with file name")

                if opener is None:
                    self.__fd__ = _os.open(name, flags, 0o666)
                    fd_is_own = True
                else:
                    fd = getattr(opener, name)(flags)
                    try:
                        self.__fd__ = int(fd)
                        if self.__fd__ < 0:
                            # The opener returned a negative result instead
                            # of raising an exception
                            raise ValueError("opener returned %d" % self.__fd__)
                        fd_is_own = True
                    except TypeError:
                        raise TypeError("expected integer from opener")
            # We do not check this for stdin/out/err, because it is not necessary and more importantly
            # it runs during context initialization and requires IO access so otherwise we would not be
            # able to start GraalPython without IO allowed
            if self.__fd__ not in [0, 1, 2]:
                st = _os.fstat(self.__fd__)
                # On Unix, fopen will succeed for directories.
                # In Python, there should be no file objects referring to
                # directories, so we need a check.
                if FileIO.__isdir__(st.st_mode):
                    raise OSError(21) # EISDIR
            self.__blksize__ = DEFAULT_BUFFER_SIZE
            self.name = name
            if self.__appending__:
                # For consistent behaviour, we explicitly seek to the end of file
                # (otherwise, it might be done only on the first write()).
                _os.lseek(self.__fd__, 0, SEEK_END)
        except:
            if not fd_is_own:
                self.__fd__ = -1
            raise

    @property
    def closefd(self):
        return self.__closefd__

    @property
    def mode(self):
        if self.__created__:
            if self.__readable__:
                return 'xb+'
            else:
                return 'xb'
        if self.__appending__:
            if self.__readable__:
                return 'ab+'
            else:
                return 'ab'
        elif self.__readable__:
            if self.__writable__:
                return 'rb+'
            else:
                return 'rb'
        else:
            return 'wb'

    @property
    def blksize(self):
        return self.__blksize__

    def _checkClosed(self, message=None):
        if message is None:
            message = "I/O operation on closed file"
        if self.__fd__ < 0:
            raise ValueError(message)

    def _checkReadable(self):
        if not self.readable():
            raise UnsupportedOperation("File not open for reading")

    def _checkWritable(self):
        if not self.writable():
            raise UnsupportedOperation("File not open for writing")

    def close(self):
        got_e = None
        try:
            _RawIOBase.close(self)
        except Exception as e:
            got_e = e
        if not self.__closefd__:
            self.__fd__ = -1
        else:
            if self.__fd__ >= 0:
                fd = self.__fd__
                self.__fd__ = -1
                _os.close(fd)
            if got_e:
                raise got_e

    def _dealloc_warn(self, source):
        if self.__fd__ >= 0 and self.closefd:
            _warn("unclosed file %s" % repr(source), ResourceWarning)

    def seek(self, pos, whence=0):
        self._checkClosed()
        return _os.lseek(self.__fd__, pos, whence)

    def tell(self):
        self._checkClosed()
        return _os.lseek(self.__fd__, 0, 1)

    def readable(self):
        self._checkClosed()
        return self.__readable__

    def writable(self):
        self._checkClosed()
        return self.__writable__

    def seekable(self):
        self._checkClosed()
        if self.__seekable__ < 0:
            try:
                _os.lseek(self.__fd__, 0, SEEK_CUR)
            except OSError:
                self.__seekable__ = 0
            else:
                self.__seekable__ = 1
        return self.__seekable__ == 1

    # ______________________________________________

    def fileno(self):
        self._checkClosed()
        return self.__fd__

    def isatty(self):
        self._checkClosed()
        return _os.isatty(self.__fd__)

    def __repr__(self):
        if self.__fd__ < 0:
            return "<_io.FileIO [closed]>"
        closefd = "True" if self.__closefd__ else "False"
        if self.name is None:
            return "<_io.FileIO fd=%d mode='%s' closefd=%s>" % (
                self.__fd__, self.mode, closefd
            )
        else:
            return "<_io.FileIO name=%s mode='%s' closefd=%s>" % (
                    repr(self.name), self.mode, closefd
            )

    # ______________________________________________

    def write(self, data):
        self._checkClosed()
        self._checkWritable()
        return _os.write(self.__fd__, data)

    def read(self, size=-1):
        self._checkClosed()
        self._checkReadable()
        if size < 0:
            return self.readall()
        return _os.read(self.__fd__, size)

    def readinto(self, rwbuffer):
        self._checkClosed()
        self._checkReadable()
        length = len(rwbuffer)
        buf = _os.read(self.__fd__, length)
        rwbuffer[:] = buf
        return len(buf)

    def readall(self):
        self._checkClosed()
        self._checkReadable()
        total = 0
        builder = []
        while True:
            newsize = 512 * 1024
            chunk = _os.read(self.__fd__, newsize)
            if len(chunk) == 0:
                break
            builder.append(chunk)
            total += len(chunk)
        return b"".join(builder)

    if sys.platform == "win32":
        def truncate(self, size):
            self._checkClosed()
            self._checkWritable()
            if size < 0:
                size = self.tell()
            raise NotImplementedError("truncate on win32")
    else:
        def truncate(self, size=-1):
            self._checkClosed()
            self._checkWritable()
            if size < 0:
                size = self.tell()
            _os.ftruncate(self.__fd__, size)
            return size


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


class BufferedWriter(_BufferedIOBase):
    pass


class BufferedRWPair(_BufferedIOBase):
    pass


class BufferedRandom(_BufferedIOBase):
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
