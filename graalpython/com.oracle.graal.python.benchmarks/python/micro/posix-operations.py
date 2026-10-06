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

"""Benchmark POSIX file and directory operations; also used for native-runtime PGO training."""

import os
import tempfile


def __benchmark__(num=2000):
    # Repeated runtime calls make the native successors of PreInitPosixSupport's phase checks hot.
    # Both path types also exercise the backend selection used by string conversion nodes.
    data = b"posix-pgo"
    with tempfile.TemporaryDirectory(prefix="graalpy-pgo-posix-") as directory:
        filename = os.path.join(directory, "data")
        with open(filename, "wb") as stream:
            stream.write(data)
        os.mkdir(os.path.join(directory, "subdir"))
        paths = (filename, os.fsencode(filename))
        directories = (directory, os.fsencode(directory))
        statvfs = getattr(os, "statvfs", None)
        fstatvfs = getattr(os, "fstatvfs", None)
        for iteration in range(num):
            path = paths[iteration % 2]
            directory_path = directories[iteration % 2]
            fd = os.open(path, os.O_RDWR)
            try:
                os.set_inheritable(fd, False)
                os.lseek(fd, 0, os.SEEK_SET)
                read_data = os.read(fd, len(data))
                assert read_data == data
                os.lseek(fd, 0, os.SEEK_SET)
                written = os.write(fd, data)
                fd_stat = os.fstat(fd)
                path_stat = os.stat(path)
                link_stat = os.lstat(path)
                assert written == fd_stat.st_size == path_stat.st_size == link_stat.st_size == len(data)
                if statvfs is not None:
                    statvfs(path)
                if fstatvfs is not None:
                    fstatvfs(fd)
            finally:
                os.close(fd)

            with os.scandir(directory_path) as entries:
                for entry in entries:
                    assert entry.name
                    entry.stat()
                    entry.is_file()
                    entry.is_dir()
            names = os.listdir(directory_path)
            assert len(names) == 2
            if os.listdir in os.supports_fd:
                directory_fd = os.open(directory_path, os.O_RDONLY)
                try:
                    # listdir(fd) rewinds the directory stream before closing it.
                    names = os.listdir(directory_fd)
                    assert len(names) == 2
                finally:
                    os.close(directory_fd)
    return num
