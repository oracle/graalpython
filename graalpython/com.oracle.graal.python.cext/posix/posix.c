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

#include <arpa/inet.h>
#include <assert.h>
#include <dirent.h>
#include <errno.h>
#include <fcntl.h>
#include <netdb.h>
#include <netinet/in.h>
#include <signal.h>
#include <stdio.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/stat.h>
#include <sys/select.h>
#include <sys/socket.h>
#include <sys/time.h>
#include <sys/types.h>
#include <sys/utsname.h>
#include <sys/wait.h>
#include <sys/file.h>
#include <sys/mman.h>
#include <unistd.h>


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
    int result = fstatat(dirFd, path, &st, followSymlinks ? 0 : AT_SYMLINK_NOFOLLOW);
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
    return unlinkat(dirFd, pathname, rmdir ? AT_REMOVEDIR : 0);
}

int32_t call_symlinkat(const char *target, int32_t dirFd, const char *linkpath) {
    return symlinkat(target, dirFd, linkpath);
}

int32_t call_mkdirat(int32_t dirFd, const char *pathname, int32_t mode) {
    return mkdirat(dirFd, pathname, mode);
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

#ifdef __gnu_linux__
int32_t call_utimensat(int32_t dirFd, const char *path, int64_t *timespec, int32_t followSymlinks) {
    if (!timespec) {
        return utimensat(dirFd, path, NULL, followSymlinks ? 0 : AT_SYMLINK_NOFOLLOW);
    } else {
        struct timespec times[2];
        times[0].tv_sec = timespec[0];
        times[0].tv_nsec = timespec[1];
        times[1].tv_sec = timespec[2];
        times[1].tv_nsec = timespec[3];
        return utimensat(dirFd, path, times, followSymlinks ? 0 : AT_SYMLINK_NOFOLLOW);
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
#endif

int32_t call_futimes(int32_t fd, int64_t *timeval) {
    if (!timeval) {
        return futimes(fd, NULL);
    } else {
        struct timeval times[2];
        times[0].tv_sec = timeval[0];
        times[0].tv_usec = timeval[1];
        times[1].tv_sec = timeval[2];
        times[1].tv_usec = timeval[3];
        return futimes(fd, times);
    }
}

int32_t call_lutimes(const char *filename, int64_t *timeval) {
    if (!timeval) {
        return lutimes(filename, NULL);
    } else {
        struct timeval times[2];
        times[0].tv_sec = timeval[0];
        times[0].tv_usec = timeval[1];
        times[1].tv_sec = timeval[2];
        times[1].tv_usec = timeval[3];
        return lutimes(filename, times);
    }
}

int32_t call_utimes(const char *filename, int64_t *timeval) {
    if (!timeval) {
        return utimes(filename, NULL);
    } else {
        struct timeval times[2];
        times[0].tv_sec = timeval[0];
        times[0].tv_usec = timeval[1];
        times[1].tv_sec = timeval[2];
        times[1].tv_usec = timeval[3];
        return utimes(filename, times);
    }
}

int32_t call_renameat(int32_t oldDirFd, const char *oldPath, int32_t newDirFd, const char *newPath) {
    return renameat(oldDirFd, oldPath, newDirFd, newPath);
}

int32_t call_faccessat(int32_t dirFd, const char *path, int32_t mode, int32_t effectiveIds, int32_t followSymlinks) {
    int flags = 0;
    if (!followSymlinks) {
        flags |= AT_SYMLINK_NOFOLLOW;
    }
    if (effectiveIds) {
        flags |= AT_EACCESS;
    }
    return faccessat(dirFd, path, mode, flags);
}

int32_t call_fchmodat(int32_t dirFd, const char *path, int32_t mode, int32_t followSymlinks) {
    return fchmodat(dirFd, path, mode, followSymlinks ? 0 : AT_SYMLINK_NOFOLLOW);
}

int32_t call_fchmod(int32_t fd, int32_t mode) {
    return fchmod(fd, mode);
}

int64_t call_readlinkat(int32_t dirFd, const char *path, char *buf, uint64_t size) {
    return readlinkat(dirFd, path, buf, size);
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

int64_t call_mmap(int64_t length, int32_t prot, int32_t flags, int32_t fd, int64_t offset) {
    void *result = mmap(NULL, length, prot, flags, fd, offset);
    return result == MAP_FAILED ? 0 : (int64_t) result;
}

int32_t call_munmap(int64_t address, int64_t length) {
    return munmap((void *) address, length);
}

void call_msync(int64_t address, int64_t offset, int64_t length) {
    // TODO: can be generalized to also accept different flags,
    // but MS_SYNC and such seem to be defined to different values across systems
    msync(((int8_t *) address) + offset, length, MS_SYNC);
}

int32_t call_socket(int32_t family, int32_t type, int32_t protocol) {
    return socket(family, type, protocol);
}

// On Java side, socket addresses are stored in a Java byte[] (here represented by a int8_t *).
// Since there are no guarantees about the alignment of this pointer, we cannot simply cast it
// to (struct sockaddr *), instead we do a copy. This shouldn't be a big deal since it is
// just 16/28 bytes (for AF_INET/AF_INET6 respectively).

int32_t call_accept(int32_t sockfd, int8_t *addr, int32_t *len_and_family) {
    struct sockaddr_storage sa;
    socklen_t l = sizeof(sa);
    int res = accept(sockfd, (struct sockaddr *) &sa, &l);
    if (res >= 0) {
        assert(l <= sizeof(sockaddr_storage));      // l is small enough to be representable by int32_t...
        len_and_family[0] = l;                      // ...so this unsigned->signed conversion is well defined
        len_and_family[1] = sa.ss_family;
        memcpy(addr, &sa, l);
    }
    return res;
}

int32_t call_bind(int32_t sockfd, int8_t *addr, int32_t addr_len) {
    struct sockaddr_storage sa;
    memcpy(&sa, addr, addr_len);
    return bind(sockfd, (struct sockaddr *) &sa, addr_len);
}

int32_t call_connect(int32_t sockfd, int8_t *addr, int32_t addr_len) {
    struct sockaddr_storage sa;
    memcpy(&sa, addr, addr_len);
    return connect(sockfd, (struct sockaddr *) &sa, addr_len);
}

int32_t call_listen(int32_t sockfd, int32_t backlog) {
    return listen(sockfd, backlog);
}

int32_t call_getpeername(int32_t sockfd, int8_t *addr, int32_t *len_and_family) {
    struct sockaddr_storage sa;
    socklen_t l = sizeof(sa);
    int res = getpeername(sockfd, (struct sockaddr *) &sa, &l);
    if (res != -1) {
        assert(l <= sizeof(sockaddr_storage));      // l is small enough to be representable by int32_t...
        len_and_family[0] = l;                      // ...so this unsigned->signed conversion is well defined
        len_and_family[1] = sa.ss_family;
        memcpy(addr, &sa, l);
    }
    return res;
}

int32_t call_getsockname(int32_t sockfd, int8_t *addr, int32_t *len_and_family) {
    struct sockaddr_storage sa;
    socklen_t l = sizeof(sa);
    int res = getsockname(sockfd, (struct sockaddr *) &sa, &l);
    if (res != -1) {
        assert(l <= sizeof(sockaddr_storage));      // l is small enough to be representable by int32_t...
        len_and_family[0] = l;                      // ...so this unsigned->signed conversion is well defined
        len_and_family[1] = sa.ss_family;
        memcpy(addr, &sa, l);
    }
    return res;
}

//TODO len should be size_t, retval should be ssize_t
int32_t call_send(int32_t sockfd, void *buf, int32_t len, int32_t flags) {
    return send(sockfd, buf, len, flags);
}

int32_t call_sendto(int32_t sockfd, void *buf, int32_t len, int32_t flags, int8_t *addr, int32_t addr_len) {
    struct sockaddr_storage sa;
    memcpy(&sa, addr, addr_len);
    return sendto(sockfd, buf, len, flags, (struct sockaddr *) &sa, addr_len);
}

int32_t call_recv(int32_t sockfd, void *buf, int32_t len, int32_t flags) {
    return recv(sockfd, buf, len, flags);
}

int32_t call_recvfrom(int32_t sockfd, void *buf, int32_t len, int32_t flags, int8_t *src_addr, int32_t *len_and_family) {
    struct sockaddr_storage sa;
    socklen_t l = sizeof(sa);
    int res = recvfrom(sockfd, buf, len, flags, (struct sockaddr *) &sa, &l);
    if (res != -1) {
        assert(l <= sizeof(sockaddr_storage));      // l is small enough to be representable by int32_t...
        len_and_family[0] = l;                      // ...so this unsigned->signed conversion is well defined
        len_and_family[1] = sa.ss_family;
        memcpy(src_addr, &sa, l);
    }
    return res;
}

int32_t call_shutdown(int32_t sockfd, int32_t how) {
    return shutdown(sockfd, how);
}

#define MAX_SOCKOPT_LEN 1024

int32_t call_getsockopt(int32_t sockfd, int32_t level, int32_t optname, void *buf, int32_t *bufLen) {
    // We don't know anything about the alignment of the buf pointer, neither we know what alignment
    // is expected by getsockopt, since that depends on the actual value of level/optname. Thus we
    // need to make a copy to a buffer that is aligned for any data type. We could use malloc for
    // this, but most options are just 4 bytes. Or we could use alloca, but I can't find any
    // documentation of alignment guarantees, its use is discouraged and there is no way of detecting
    // stack overflow, so we'd have to put some arbitrary limit to bufLen anyway - in which case
    // a properly aligned, stack-allocated buffer of a fixed size should work fine.
    // The limit of 1024 is inspired by the implementation of CPython's sock_getsockopt.
    char alignedBuf[MAX_SOCKOPT_LEN] __attribute__ ((aligned));
    socklen_t len = *bufLen;
    if (len > sizeof(alignedBuf)) {
        // If this ever happens, we can increase MAX_SOCKOPT_LEN or use malloc.
        errno = ENOMEM;
        return -1;
    }
    int res = getsockopt(sockfd, level, optname, alignedBuf, &len);
    if (res == 0) {
        *bufLen = len;
        memcpy(buf, alignedBuf, len);
    }
    return res;
}

int32_t call_setsockopt(int32_t sockfd, int32_t level, int32_t optname, void *buf, int32_t bufLen) {
    // see comments in call_getsockopt
    char alignedBuf[MAX_SOCKOPT_LEN] __attribute__ ((aligned));
    if (bufLen > sizeof(alignedBuf)) {
        errno = ENOMEM;
        return -1;
    }
    memcpy(alignedBuf, buf, bufLen);
    return setsockopt(sockfd, level, optname, alignedBuf, bufLen);
}

int32_t call_inet_addr(const char *src) {
    return ntohl(inet_addr(src));
}

int64_t call_inet_aton(const char *src) {
    struct in_addr addr;
    int r = inet_aton(src, &addr);
    if (r != 1) {
        return -1;
    }
    return ntohl(addr.s_addr) & 0xFFFFFFFF;
}

int32_t call_inet_ntoa(int32_t src, char *dst) {
    struct in_addr addr;
    addr.s_addr = htonl(src);
    const char *s = inet_ntoa(addr);
    size_t len = strlen(s);
    assert(len <= INET_ADDRSTRLEN - 1);
    memcpy(dst, s, len);
    return len;
}

int32_t call_inet_pton(int32_t family, const char *src, void *dst) {
    return inet_pton(family, src, dst);
}

int32_t call_inet_ntop(int32_t family, void *src, char *dst, int32_t dstSize) {
    const char *r = inet_ntop(family, src, dst, dstSize);
    return r == NULL ? -1 : 0;
}

int32_t call_gethostname(char *buf, int64_t bufLen) {
    return gethostname(buf, bufLen);
}

int32_t call_getnameinfo(int8_t *addr, int32_t addr_len, char *hostBuf, int32_t hostBufLen, char *servBuf, int32_t servBufLen, int32_t flags) {
    struct sockaddr_storage sa;
    memcpy(&sa, addr, addr_len);
    return getnameinfo((struct sockaddr *) &sa, addr_len, hostBuf, hostBufLen, servBuf, servBufLen, flags);
}

int32_t call_getaddrinfo(const char *node, const char *service, int32_t family, int32_t sockType, int32_t protocol, int32_t flags, int64_t *ptr) {
    struct addrinfo hints;
    struct addrinfo *res;
    memset(&hints, 0, sizeof(hints));
    hints.ai_flags = flags;
    hints.ai_family = family;
    hints.ai_socktype = sockType;
    hints.ai_protocol = protocol;
    int ret = getaddrinfo(node, service, &hints, &res);
    if (ret == 0) {
        *ptr = (int64_t) res;
    }
    return ret;
}

void call_freeaddrinfo(int64_t ptr) {
    freeaddrinfo((struct addrinfo *) ptr);
}

void call_gai_strerror(int32_t error, char *buf, int32_t buflen) {
    snprintf(buf, buflen, "%s", gai_strerror(error));
}

int32_t get_addrinfo_members(int64_t ptr, int32_t *intData, int64_t *longData, int8_t *addr) {
    // see NFIPosixSupport.AddrInfo for description of the way data is transferred
    struct addrinfo *ai = (struct addrinfo *) ptr;

    memcpy(addr, ai->ai_addr, ai->ai_addrlen);

    longData[0] = (int64_t) ai->ai_canonname;
    longData[1] = (int64_t) ai->ai_next;

    intData[0] = ai->ai_flags;
    intData[1] = ai->ai_family;
    intData[2] = ai->ai_socktype;
    intData[3] = ai->ai_protocol;
    assert(ai->ai_addr_len <= sizeof(sockaddr_storage));
    intData[4] = ai->ai_addrlen;
    intData[5] = ai->ai_addr->sa_family;
    if (ai->ai_canonname != NULL) {
        size_t len = strlen(ai->ai_canonname);
        if (len >= 0x7fffffff) {
            return -1;
        }
        intData[6] = len;
    }
    return 0;
}

void get_sockaddr_in_members(int8_t *addr, int32_t *members) {
    struct sockaddr_in sa;
    memcpy(&sa, addr, sizeof(sa));
    assert(sa.sin_family == AF_INET);
    members[0] = ntohs(sa.sin_port);
    members[1] = ntohl(sa.sin_addr.s_addr);
}

void get_sockaddr_in6_members(int8_t *addr, int32_t *members, int8_t *address) {
    struct sockaddr_in6 sa;
    memcpy(&sa, addr, sizeof(sa));
    assert(sa.sin_family == AF_INET6);
    members[0] = ntohs(sa.sin6_port);
    members[1] = ntohl(sa.sin6_flowinfo);
    members[2] = sa.sin6_scope_id;
    memcpy(address, &sa.sin6_addr, 16);
}

int32_t set_sockaddr_in_members(int8_t *addr, int32_t port, int32_t address) {
    struct sockaddr_in sa;
    memset(&sa, 0, sizeof(sa));
    sa.sin_family = AF_INET;
    sa.sin_port = htons(port);
    sa.sin_addr.s_addr = htonl(address);
    memcpy(addr, &sa, sizeof(sa));
    return sizeof(sa);
}

int32_t set_sockaddr_in6_members(int8_t *addr, int32_t port, int8_t *address, int32_t flowInfo, int32_t scopeId) {
    struct sockaddr_in6 sa;
    memset(&sa, 0, sizeof(sa));
    sa.sin6_family = AF_INET6;
    sa.sin6_port = htons(port);
    sa.sin6_flowinfo = htonl(flowInfo);
    sa.sin6_scope_id = scopeId;
    memcpy(&sa.sin6_addr, address, 16);
    memcpy(addr, &sa, sizeof(sa));
    return sizeof(sa);
}

int32_t get_errno() {
    return errno;
}

void set_errno(int e) {
    errno = e;
}
