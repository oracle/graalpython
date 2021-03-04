/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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

#include <dirent.h>
#include <errno.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/stat.h>
#include <sys/select.h>
#include <sys/types.h>
#include <sys/utsname.h>
#include <sys/wait.h>
#include <sys/file.h>
#include <sys/mman.h>
#include <unistd.h>


// TODO remove this once we properly synchronize constants between Java and C
static int fixDirFd(int dirFd) {
    return dirFd == -100 ? AT_FDCWD : dirFd;
}

int64_t call_getpid() {
    return getpid();
}

int32_t call_umask(int32_t mask) {
    return umask(mask);
}

int32_t get_inheritable(int32_t fd) {
    int flags = fcntl(fd, F_GETFD, 0);
    if (flags < 0) {
        return -1;
    }
    return !(flags & FD_CLOEXEC);
}

// note: this is also called between fork() and exec()
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

int32_t call_openat(int32_t dirFd, const char *pathname, int32_t flags, int32_t mode) {
    int fixedFlags = flags;
    // TODO remove this once we properly synchronize constants between Java and C
    if (flags & 64) {
        fixedFlags &= ~64;
        fixedFlags |= O_CREAT;
    }
    if (flags & 524288) {
        fixedFlags &= ~524288;
        fixedFlags |= O_CLOEXEC;
    }
    return openat(fixDirFd(dirFd), pathname, fixedFlags, mode);
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

static void fill_select_result(int32_t* fds, int32_t fdsLen, fd_set* set, int8_t* result, int32_t resultOffset) {
    for (int32_t i = 0; i < fdsLen; ++i) {
        if (FD_ISSET(fds[i], set)) {
            result[resultOffset + i] = 1;
        }
    }
}

static void fill_fd_set(fd_set *set, int32_t* fds, int32_t len) {
    FD_ZERO(set);
    for (int32_t i = 0; i < len; ++i) {
        FD_SET(fds[i], set);
    }
}

// selected is output parameter, a non-zero value will be written to
// indices of file descriptors that were selected. The array indices
// should be interpreted as if read/write/err file descriptors were
// concatendated into one array
int32_t call_select(int32_t nfds, int32_t* readfds, int32_t readfdsLen,
    int32_t* writefds, int32_t writefdsLen, int32_t* errfds, int32_t errfdsLen,
    int64_t timeoutSec, int64_t timeoutUsec, int8_t* selected) {

    fd_set readfdsSet, writefdsSet, errfdsSet;
    fill_fd_set(&readfdsSet, readfds, readfdsLen);
    fill_fd_set(&writefdsSet, writefds, writefdsLen);
    fill_fd_set(&errfdsSet, errfds, errfdsLen);

    struct timeval timeout = {timeoutSec, timeoutUsec};

    int result = select(nfds, &readfdsSet, &writefdsSet, &errfdsSet, timeoutSec >= 0 ? &timeout : NULL);

    // fill in the output parameter
    fill_select_result(readfds, readfdsLen, &readfdsSet, selected, 0);
    fill_select_result(writefds, writefdsLen, &writefdsSet, selected, readfdsLen);
    fill_select_result(errfds, errfdsLen, &errfdsSet, selected, readfdsLen + writefdsLen);
    return (int32_t) result;
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

int32_t call_flock(int32_t fd, int32_t operation) {
    return flock(fd, operation);
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

static void stat_struct_to_longs(struct stat *st, int64_t *out) {
    // TODO some of these use implementation-defined behaviour of unsigned -> signed conversion
    out[0] = st->st_mode;
    out[1] = st->st_ino;
    out[2] = st->st_dev;
    out[3] = st->st_nlink;
    out[4] = st->st_uid;
    out[5] = st->st_gid;
    out[6] = st->st_size;
#ifdef __APPLE__
    out[7] = st->st_atimespec.tv_sec;
    out[8] = st->st_mtimespec.tv_sec;
    out[9] = st->st_ctimespec.tv_sec;
    out[10] = st->st_atimespec.tv_nsec;
    out[11] = st->st_mtimespec.tv_nsec;
    out[12] = st->st_ctimespec.tv_nsec;
#else
    out[7] = st->st_atim.tv_sec;
    out[8] = st->st_mtim.tv_sec;
    out[9] = st->st_ctim.tv_sec;
    out[10] = st->st_atim.tv_nsec;
    out[11] = st->st_mtim.tv_nsec;
    out[12] = st->st_ctim.tv_nsec;
#endif
}

int32_t call_fstatat(int32_t dirFd, const char *path, int32_t followSymlinks, int64_t *out) {
    struct stat st;
    int result = fstatat(fixDirFd(dirFd), path, &st, followSymlinks ? 0 : AT_SYMLINK_NOFOLLOW);
    if (result == 0) {
        stat_struct_to_longs(&st, out);
    }
    return result;
}

int32_t call_fstat(int32_t fd, int64_t *out) {
    struct stat st;
    int result = fstat(fd, &st);
    if (result == 0) {
        stat_struct_to_longs(&st, out);
    }
    return result;
}

int32_t call_uname(char *sysname, char *nodename, char *release, char *version, char *machine, int32_t size) {
    struct utsname buf;
    int result = uname(&buf);
    if (result == 0) {
        snprintf(sysname, size, "%s", buf.sysname);
        snprintf(nodename, size, "%s", buf.nodename);
        snprintf(release, size, "%s", buf.release);
        snprintf(version, size, "%s", buf.version);
        snprintf(machine, size, "%s", buf.machine);
    }
    return result;
}

int32_t call_unlinkat(int32_t dirFd, const char *pathname, int32_t rmdir) {
    return unlinkat(fixDirFd(dirFd), pathname, rmdir ? AT_REMOVEDIR : 0);
}

int32_t call_symlinkat(const char *target, int32_t dirFd, const char *linkpath) {
    return symlinkat(target, fixDirFd(dirFd), linkpath);
}

int32_t call_mkdirat(int32_t dirFd, const char *pathname, int32_t mode) {
    return mkdirat(fixDirFd(dirFd), pathname, mode);
}

int32_t call_getcwd(char *buf, uint64_t size) {
    return getcwd(buf, size) == NULL ? -1 : 0;
}

int32_t call_chdir(const char *path) {
    return chdir(path);
}

int32_t call_fchdir(int32_t fd) {
    return fchdir(fd);
}

int32_t call_isatty(int32_t fd) {
    return isatty(fd);
}

intptr_t call_opendir(const char *name) {
    return (intptr_t) opendir(name);
}

intptr_t call_fdopendir(int32_t fd) {
    int fd2 = dup(fd);
    if (fd2 == -1) {
        return 0;
    }
    DIR *dirp = fdopendir(fd2);
    if (dirp == NULL) {
        close(fd2);
    }
    return (intptr_t) dirp;
}

int32_t call_closedir(intptr_t dirp, int32_t rewind) {
    if (rewind) {
        rewinddir((DIR *) dirp);
    }
    return closedir((DIR *) dirp);
}

int32_t call_readdir(intptr_t dirp, char *nameBuf, uint64_t nameBufSize, int64_t *out) {
    errno = 0;
    struct dirent *dirEntry = readdir((DIR *) dirp);
    if (dirEntry != NULL) {
        snprintf(nameBuf, nameBufSize, "%s", dirEntry->d_name);
        out[0] = dirEntry->d_ino;
        out[1] = dirEntry->d_type;
        return 1;
    }
    return 0;
}

int32_t call_utimensat(int32_t dirFd, const char *path, int64_t *timespec, int32_t followSymlinks) {
    if (!timespec) {
        return utimensat(fixDirFd(dirFd), path, NULL, followSymlinks ? 0 : AT_SYMLINK_NOFOLLOW);
    } else {
        struct timespec times[2];
        times[0].tv_sec = timespec[0];
        times[0].tv_nsec = timespec[1];
        times[1].tv_sec = timespec[2];
        times[1].tv_nsec = timespec[3];
        return utimensat(fixDirFd(dirFd), path, times, followSymlinks ? 0 : AT_SYMLINK_NOFOLLOW);
    }
}

int32_t call_futimens(int32_t fd, int64_t *timespec) {
    if (!timespec) {
        return futimens(fd, NULL);
    } else {
        struct timespec times[2];
        times[0].tv_sec = timespec[0];
        times[0].tv_nsec = timespec[1];
        times[1].tv_sec = timespec[2];
        times[1].tv_nsec = timespec[3];
        return futimens(fd, times);
    }
}

int32_t call_renameat(int32_t oldDirFd, const char *oldPath, int32_t newDirFd, const char *newPath) {
    return renameat(fixDirFd(oldDirFd), oldPath, fixDirFd(newDirFd), newPath);
}

int32_t call_faccessat(int32_t dirFd, const char *path, int32_t mode, int32_t effectiveIds, int32_t followSymlinks) {
    int flags = 0;
    if (!followSymlinks) {
        flags |= AT_SYMLINK_NOFOLLOW;
    }
    if (effectiveIds) {
        flags |= AT_EACCESS;
    }
    return faccessat(fixDirFd(dirFd), path, mode, flags);
}

int32_t call_fchmodat(int32_t dirFd, const char *path, int32_t mode, int32_t followSymlinks) {
    return fchmodat(fixDirFd(dirFd), path, mode, followSymlinks ? 0 : AT_SYMLINK_NOFOLLOW);
}

int32_t call_fchmod(int32_t fd, int32_t mode) {
    return fchmod(fd, mode);
}

int64_t call_readlinkat(int32_t dirFd, const char *path, char *buf, uint64_t size) {
    return readlinkat(fixDirFd(dirFd), path, buf, size);
}

int64_t call_waitpid(int64_t pid, int32_t *status, int32_t options) {
    return waitpid(pid, status, options);
}

int32_t call_wcoredump(int32_t status) {
    return WCOREDUMP(status) ? 1 : 0;
}

int32_t call_wifcontinued(int32_t status) {
    return WIFCONTINUED(status) ? 1 : 0;
}

int32_t call_wifstopped(int32_t status) {
    return WIFSTOPPED(status) ? 1 : 0;
}

int32_t call_wifsignaled(int32_t status) {
    return WIFSIGNALED(status) ? 1 : 0;
}

int32_t call_wifexited(int32_t status) {
    return WIFEXITED(status) ? 1 : 0;
}

int32_t call_wexitstatus(int32_t status) {
    return WEXITSTATUS(status);
}

int32_t call_wtermsig(int32_t status) {
    return WTERMSIG(status);
}

int32_t call_wstopsig(int32_t status) {
    return WSTOPSIG(status);
}

int32_t call_kill(int64_t pid, int32_t signal) {
    return kill(pid, signal);
}

int64_t call_getuid() {
    return getuid();
}

int64_t call_getppid() {
    return getppid();
}

int64_t call_getsid(int64_t pid) {
    return getsid(pid);
}

int32_t call_ctermid(char *buf) {
    return ctermid(buf) == NULL ? -1 : 0;
}

int32_t call_setenv(char *name, char *value, int overwrite) {
    return setenv(name, value, overwrite);
}

// See comment in NFiPosixSupport.execv() for the description of arguments
void call_execv(char *data, int64_t *offsets, int32_t offsetsLen) {
    // We reuse the memory allocated for offsets to avoid the need to allocate and reliably free another array
    char **strings = (char **) offsets;
    for (int32_t i = 0; i < offsetsLen; ++i) {
        strings[i] = offsets[i] == -1 ? NULL : data + offsets[i];
    }

    char *pathname = strings[0];
    char **argv = strings + 1;
    execv(pathname, argv);
}

int32_t call_system(const char *pathname) {
    return system(pathname);
}

void *call_mmap(int64_t length, int32_t prot, int32_t flags, int32_t fd, int64_t offset) {
    void *result = mmap(NULL, length, prot, flags, fd, offset);
    return result == MAP_FAILED ? NULL : result;
}

int32_t call_munmap(void* address, int64_t length) {
    return munmap(address, length);
}

void call_msync(void* address, int64_t offset, int64_t length) {
    // TODO: can be generalized to also accept different flags,
    // but MS_SYNC and such seem to be defined to different values across systems
    msync(address + offset, length, MS_SYNC);
}

int8_t read_byte(int8_t *address, int64_t index) {
    return address[index];
}

void write_bytes(int8_t *address, int8_t* buffer, int64_t index, int32_t length) {
    memcpy(address + index, buffer, length);
}

void read_bytes(int8_t *address, int8_t* buffer, int64_t index, int32_t length) {
    memcpy(buffer, address + index, length);
}

int32_t get_errno() {
    return errno;
}

void set_errno(int e) {
    errno = e;
}
