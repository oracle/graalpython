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

#include <errno.h>
#include <fcntl.h>
#include <sys/stat.h>
#include <stdint.h>
#include <string.h>
#include <unistd.h>

/*
  There are two versions of strerror_r and we need the POSIX one. The following lines double-check
  that we got it. First, we check that _GNU_SOURCE has not been defined by any of the included headers.
  Then we explicitly declare the function with POSIX signature which should force the compiler to
  report an error in case we got the GNU version somehow.
*/
#ifdef _GNU_SOURCE
#error "Someone defined _GNU_SOURCE"
#endif
int strerror_r(int errnum, char *buf, size_t buflen);

int64_t call_getpid() {
  return getpid();
}

int64_t call_umask(int64_t mask) {
  // TODO umask uses mode_t as argument/retval -> what Java type should we map it into? Using long for now.
  return umask(mask);
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

int32_t call_fcntl_int(int32_t fd, int32_t cmd, int32_t arg) {
    return fcntl(fd, cmd, arg);
}

int32_t call_dup2(int32_t oldfd, int32_t newfd) {
    return dup2(oldfd, newfd);
}

int32_t call_dup3(int32_t oldfd, int32_t newfd, int32_t flags) {
    // TODO dup3() is not POSIX, but requires _GNU_SOURCE, which we do not want because of strerror_r
    return dup3(oldfd, newfd, flags);
}

int32_t call_pipe2(int32_t *pipefd, int32_t flags) {
    // TODO pipe2() is not POSIX, but requires _GNU_SOURCE, which we do not want because of strerror_r
    return pipe2(pipefd, flags);
}

int64_t call_lseek(int32_t fd, int64_t offset, int32_t whence) {
    return lseek(fd, offset, whence);
}

int32_t call_ftruncate(int32_t fd, int64_t length) {
    return ftruncate(fd, length);
}

int32_t get_errno() {
    return errno;
}

void set_errno(int e) {
    errno = e;
}

int32_t call_strerror(int32_t error, char *buf, int32_t buflen) {
    return strerror_r(error, buf, buflen);
}
