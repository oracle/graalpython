/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

// Helper functions that mostly delegate to POSIX functions
// These functions are called from NFIPosixSupport Java class using NFI

// This file uses GNU extensions. Functions that require non-GNU versions (e.g. strerror_r)
// need to go to posix_no_gnu.c
#ifdef __gnu_linux__
#define _GNU_SOURCE
#endif

#include <errno.h>
#include <fcntl.h>
#include <sys/stat.h>
#include <sys/ioctl.h>
#include <stdint.h>
#include <string.h>
#include <unistd.h>


int64_t call_getpid() {
  return getpid();
}

int64_t call_umask(int64_t mask) {
  // TODO umask uses mode_t as argument/retval -> what Java type should we map it into? Using long for now.
  return umask(mask);
}

int32_t get_inheritable(int32_t fd) {
    int flags = fcntl(fd, F_GETFD, 0);
    if (flags < 0) {
        return -1;
    }
    return !(flags & FD_CLOEXEC);
}

int32_t set_inheritable(int32_t fd, int32_t inheritable) {
    int res = fcntl(fd, F_GETFD);
    if (res >= 0) {
        int new_flags;
        if (inheritable) {
            new_flags = res & ~FD_CLOEXEC;
        } else {
            new_flags = res | FD_CLOEXEC;
        }
        if (new_flags != res) {
            res = fcntl(fd, F_SETFD, new_flags);
        }
    }
    return res;
}

int32_t call_open_at(int32_t dirFd, const char *pathname, int32_t flags, int32_t mode) {
    return openat(dirFd, pathname, flags, mode);
}

int32_t call_close(int32_t fd) {
    return close(fd);
}

int64_t call_read(int32_t fd, void *buf, uint64_t count) {
    return read(fd, buf, count);
}

int64_t call_write(int32_t fd, void *buf, uint64_t count) {
    return write(fd, buf, count);
}

int32_t call_dup(int32_t fd) {
    return fcntl(fd, F_DUPFD_CLOEXEC, 0);
}

int32_t call_dup2(int32_t oldfd, int32_t newfd, int32_t inheritable) {
#ifdef __gnu_linux__
    return dup3(oldfd, newfd, inheritable ? 0 : O_CLOEXEC);
#else
    int res = dup2(oldfd, newfd);
    if (res < 0) {
        return res;
    }
    if (!inheritable) {
        if (set_inheritable(res, 0) < 0) {
            close(res);
            return -1;
        }
    }
    return res;
#endif
}

int32_t call_pipe2(int32_t *pipefd) {
#ifdef __gnu_linux__
    return pipe2(pipefd, O_CLOEXEC);
#else
    int res = pipe(pipefd);
    if (res != 0) {
        return res;
    }
    if (set_inheritable(pipefd[0], 0) < 0 || set_inheritable(pipefd[1], 0) < 0) {
        close(pipefd[0]);
        close(pipefd[1]);
        return -1;
    }
    return 0;
#endif
}

int64_t call_lseek(int32_t fd, int64_t offset, int32_t whence) {
    return lseek(fd, offset, whence);
}

int32_t call_ftruncate(int32_t fd, int64_t length) {
    return ftruncate(fd, length);
}

int32_t call_fsync(int32_t fd) {
    return fsync(fd);
}

int32_t get_blocking(int32_t fd) {
    int flags = fcntl(fd, F_GETFL, 0);
    if (flags < 0) {
        return -1;
    }
    return !(flags & O_NONBLOCK);
}

int32_t set_blocking(int32_t fd, int32_t blocking) {
    int res = fcntl(fd, F_GETFL);
    if (res >= 0) {
        int flags;
        if (blocking) {
            flags = res & ~O_NONBLOCK;
        } else {
            flags = res | O_NONBLOCK;
        }
        res = fcntl(fd, F_SETFL, flags);
    }
    return res;
}

int32_t get_terminal_size(int32_t fd, int32_t *size) {
    struct winsize w;
    int res = ioctl(fd, TIOCGWINSZ, &w);
    if (res == 0) {
        size[0] = w.ws_col;
        size[1] = w.ws_row;
    }
    return res;
}

int32_t get_errno() {
    return errno;
}

void set_errno(int e) {
    errno = e;
}
