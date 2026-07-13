/*
 * Copyright (c) 2020, 2026, Oracle and/or its affiliates. All rights reserved.
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
// These functions are called from NativePosixSupport Java class using native-access downcalls

#ifdef _WIN32

#define WIN32_LEAN_AND_MEAN
#include <winsock2.h>
#include <ws2tcpip.h>
#include <afunix.h>
#include <assert.h>
#include <windows.h>
#include <direct.h>
#include <errno.h>
#include <fcntl.h>
#include <io.h>
#include <process.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <sys/types.h>

#include "errno_capture.h"

#ifndef SO_UPDATE_ACCEPT_CONTEXT
#define SO_UPDATE_ACCEPT_CONTEXT 0x700B
#endif
#ifndef ENOTSUP
#define ENOTSUP ENOSYS
#endif
#ifndef EWOULDBLOCK
#define EWOULDBLOCK 11
#endif
#ifndef EOVERFLOW
#define EOVERFLOW 75
#endif
#ifndef EALREADY
#define EALREADY 114
#endif
#ifndef EINPROGRESS
#define EINPROGRESS 115
#endif
#ifndef ENOTSOCK
#define ENOTSOCK 88
#endif
#ifndef EDESTADDRREQ
#define EDESTADDRREQ 89
#endif
#ifndef EMSGSIZE
#define EMSGSIZE 90
#endif
#ifndef EPROTOTYPE
#define EPROTOTYPE 91
#endif
#ifndef ENOPROTOOPT
#define ENOPROTOOPT 92
#endif
#ifndef EPROTONOSUPPORT
#define EPROTONOSUPPORT 93
#endif
#ifndef EAFNOSUPPORT
#define EAFNOSUPPORT 97
#endif
#ifndef EADDRINUSE
#define EADDRINUSE 98
#endif
#ifndef EADDRNOTAVAIL
#define EADDRNOTAVAIL 99
#endif
#ifndef ENETDOWN
#define ENETDOWN 100
#endif
#ifndef ENETUNREACH
#define ENETUNREACH 101
#endif
#ifndef ENETRESET
#define ENETRESET 102
#endif
#ifndef ECONNABORTED
#define ECONNABORTED 103
#endif
#ifndef ECONNRESET
#define ECONNRESET 104
#endif
#ifndef ENOBUFS
#define ENOBUFS 105
#endif
#ifndef EISCONN
#define EISCONN 106
#endif
#ifndef ENOTCONN
#define ENOTCONN 107
#endif
#ifndef ETIMEDOUT
#define ETIMEDOUT 110
#endif
#ifndef ECONNREFUSED
#define ECONNREFUSED 111
#endif
#ifndef EHOSTUNREACH
#define EHOSTUNREACH 113
#endif

#define GP_EWOULDBLOCK 11
#define GP_EINPROGRESS 115
#define GP_EALREADY 114
#define GP_ENOTSOCK 88
#define GP_EDESTADDRREQ 89
#define GP_EMSGSIZE 90
#define GP_EPROTOTYPE 91
#define GP_ENOPROTOOPT 92
#define GP_EPROTONOSUPPORT 93
#define GP_EAFNOSUPPORT 97
#define GP_EADDRINUSE 98
#define GP_EADDRNOTAVAIL 99
#define GP_ENETDOWN 100
#define GP_ENETUNREACH 101
#define GP_ENETRESET 102
#define GP_ECONNABORTED 103
#define GP_ECONNRESET 104
#define GP_ENOBUFS 105
#define GP_EISCONN 106
#define GP_ENOTCONN 107
#define GP_ETIMEDOUT 110
#define GP_ECONNREFUSED 111
#define GP_EHOSTUNREACH 113

#define GP_EXPORT __declspec(dllexport)

THREAD_LOCAL int errno_capture = 0;
THREAD_LOCAL int winerror_capture = 0;
THREAD_LOCAL int wsaerror_capture = 0;
THREAD_LOCAL int error_source_capture = ERROR_CAPTURE_ERRNO;

#define WIN_AT_FDCWD (-100)
#define WIN_GRAALPY_DEFAULT_DIR_FD (-1)
#define WIN_DT_UNKNOWN 0

typedef struct {
    HANDLE handle;
    WIN32_FIND_DATAW data;
    int first;
    int exhausted;
} win_dir_t;

static void silent_invalid_parameter_handler(const wchar_t *expression, const wchar_t *function, const wchar_t *file, unsigned int line, uintptr_t reserved) {
    (void) expression;
    (void) function;
    (void) file;
    (void) line;
    (void) reserved;
}

#if defined(_MSC_VER) && _MSC_VER >= 1900
#define BEGIN_SUPPRESS_IPH \
    { _invalid_parameter_handler old_handler = _set_thread_local_invalid_parameter_handler(silent_invalid_parameter_handler);
#define END_SUPPRESS_IPH \
    _set_thread_local_invalid_parameter_handler(old_handler); }
#else
#define BEGIN_SUPPRESS_IPH
#define END_SUPPRESS_IPH
#endif

static int is_default_dir_fd(int32_t dirFd) {
    return dirFd == 0 || dirFd < 0 || dirFd == WIN_AT_FDCWD || dirFd == WIN_GRAALPY_DEFAULT_DIR_FD;
}

static int unsupported(void) {
    errno = ENOSYS;
    capture_errno();
    return -1;
}

static void set_posix_errno(int error) {
    errno = error;
    capture_errors();
}

static void set_win_errno(DWORD error) {
    capture_errors();
    winerror_capture = (int) error;
    error_source_capture = ERROR_CAPTURE_WINAPI;
}

static int ensure_winsock(void) {
    static int initialized = 0;
    if (!initialized) {
        WSADATA wsa_data;
        int err = WSAStartup(MAKEWORD(2, 2), &wsa_data);
        if (err != 0) {
            capture_errors();
            wsaerror_capture = err;
            error_source_capture = ERROR_CAPTURE_WINSOCK;
            return -1;
        }
        initialized = 1;
    }
    return 0;
}

static int set_wsa_errno_from_error(int error) {
    capture_errors();
    wsaerror_capture = error;
    error_source_capture = ERROR_CAPTURE_WINSOCK;
    return -1;
}

static int set_wsa_errno(void) {
    return set_wsa_errno_from_error(WSAGetLastError());
}

static int close_noraise(int32_t fd) {
    int result;
    BEGIN_SUPPRESS_IPH
    result = _close(fd);
    END_SUPPRESS_IPH
    if (result < 0) {
        capture_errno();
    }
    return result;
}

static int64_t read_noraise(int32_t fd, void *buf, unsigned int count) {
    int result;
    BEGIN_SUPPRESS_IPH
    result = _read(fd, buf, count);
    END_SUPPRESS_IPH
    if (result < 0) {
        capture_errno();
    }
    return result;
}

static int64_t write_noraise(int32_t fd, void *buf, unsigned int count) {
    int result;
    BEGIN_SUPPRESS_IPH
    result = _write(fd, buf, count);
    END_SUPPRESS_IPH
    if (result < 0) {
        capture_errno();
    }
    return result;
}

static int64_t lseek_noraise(int32_t fd, int64_t offset, int32_t whence) {
    int64_t result;
    BEGIN_SUPPRESS_IPH
    result = _lseeki64(fd, offset, whence);
    END_SUPPRESS_IPH
    if (result < 0) {
        capture_errno();
    }
    return result;
}

static int chsize_noraise(int32_t fd, int64_t length) {
    int result;
    BEGIN_SUPPRESS_IPH
    result = _chsize_s(fd, length);
    END_SUPPRESS_IPH
    if (result != 0) {
        errno = result;
        capture_errno();
    }
    return result;
}

static int commit_noraise(int32_t fd) {
    int result;
    BEGIN_SUPPRESS_IPH
    result = _commit(fd);
    END_SUPPRESS_IPH
    if (result < 0) {
        capture_errno();
    }
    return result;
}

static int dup_noraise(int32_t fd) {
    int result;
    BEGIN_SUPPRESS_IPH
    result = _dup(fd);
    END_SUPPRESS_IPH
    if (result < 0) {
        capture_errno();
    }
    return result;
}

static int dup2_noraise(int32_t oldfd, int32_t newfd) {
    int result;
    BEGIN_SUPPRESS_IPH
    result = _dup2(oldfd, newfd);
    END_SUPPRESS_IPH
    if (result < 0) {
        capture_errno();
    }
    return result;
}

typedef struct {
    int fd;
    SOCKET socket;
    int blocking;
} win_socket_entry_t;

typedef struct {
    void *view;
    HANDLE mapping;
} win_mmap_entry_t;

#define WIN_SOCKET_TABLE_SIZE 1024
#define WIN_MMAP_TABLE_SIZE 1024

static win_socket_entry_t win_socket_table[WIN_SOCKET_TABLE_SIZE];
static INIT_ONCE win_socket_table_init_once = INIT_ONCE_STATIC_INIT;
static CRITICAL_SECTION win_socket_table_lock;

static win_mmap_entry_t win_mmap_table[WIN_MMAP_TABLE_SIZE];
static INIT_ONCE win_mmap_table_init_once = INIT_ONCE_STATIC_INIT;
static CRITICAL_SECTION win_mmap_table_lock;

static BOOL CALLBACK win_socket_table_init(PINIT_ONCE init_once, PVOID parameter, PVOID *context) {
    (void) init_once;
    (void) parameter;
    (void) context;
    InitializeCriticalSection(&win_socket_table_lock);
    for (int i = 0; i < WIN_SOCKET_TABLE_SIZE; i++) {
        win_socket_table[i].fd = -1;
        win_socket_table[i].socket = INVALID_SOCKET;
        win_socket_table[i].blocking = 1;
    }
    return TRUE;
}

static void ensure_socket_table(void) {
    InitOnceExecuteOnce(&win_socket_table_init_once, win_socket_table_init, NULL, NULL);
}

static BOOL CALLBACK win_mmap_table_init(PINIT_ONCE init_once, PVOID parameter, PVOID *context) {
    (void) init_once;
    (void) parameter;
    (void) context;
    InitializeCriticalSection(&win_mmap_table_lock);
    for (int i = 0; i < WIN_MMAP_TABLE_SIZE; i++) {
        win_mmap_table[i].view = NULL;
        win_mmap_table[i].mapping = NULL;
    }
    return TRUE;
}

static void ensure_mmap_table(void) {
    InitOnceExecuteOnce(&win_mmap_table_init_once, win_mmap_table_init, NULL, NULL);
}

static int win_add_mmap(void *view, HANDLE mapping) {
    ensure_mmap_table();
    EnterCriticalSection(&win_mmap_table_lock);
    for (int i = 0; i < WIN_MMAP_TABLE_SIZE; i++) {
        if (win_mmap_table[i].view == NULL) {
            win_mmap_table[i].view = view;
            win_mmap_table[i].mapping = mapping;
            LeaveCriticalSection(&win_mmap_table_lock);
            return 0;
        }
    }
    LeaveCriticalSection(&win_mmap_table_lock);
    set_posix_errno(ENOMEM);
    return -1;
}

static HANDLE win_remove_mmap(void *view) {
    HANDLE mapping = NULL;
    ensure_mmap_table();
    EnterCriticalSection(&win_mmap_table_lock);
    for (int i = 0; i < WIN_MMAP_TABLE_SIZE; i++) {
        if (win_mmap_table[i].view == view) {
            mapping = win_mmap_table[i].mapping;
            win_mmap_table[i].view = NULL;
            win_mmap_table[i].mapping = NULL;
            break;
        }
    }
    LeaveCriticalSection(&win_mmap_table_lock);
    return mapping;
}

static SOCKET win_socket_from_fd(int32_t fd) {
    SOCKET result = INVALID_SOCKET;
    ensure_socket_table();
    EnterCriticalSection(&win_socket_table_lock);
    for (int i = 0; i < WIN_SOCKET_TABLE_SIZE; i++) {
        if (win_socket_table[i].fd == fd) {
            result = win_socket_table[i].socket;
            break;
        }
    }
    LeaveCriticalSection(&win_socket_table_lock);
    return result;
}

static int win_socket_blocking_from_fd(int32_t fd) {
    int result = 1;
    ensure_socket_table();
    EnterCriticalSection(&win_socket_table_lock);
    for (int i = 0; i < WIN_SOCKET_TABLE_SIZE; i++) {
        if (win_socket_table[i].fd == fd) {
            result = win_socket_table[i].blocking;
            break;
        }
    }
    LeaveCriticalSection(&win_socket_table_lock);
    return result;
}

static int win_socket_entry_index(int32_t fd) {
    int result = -1;
    ensure_socket_table();
    EnterCriticalSection(&win_socket_table_lock);
    for (int i = 0; i < WIN_SOCKET_TABLE_SIZE; i++) {
        if (win_socket_table[i].fd == fd) {
            result = i;
            break;
        }
    }
    LeaveCriticalSection(&win_socket_table_lock);
    return result;
}

static int win_alloc_socket_fd(SOCKET s, int blocking) {
    int fd = _open("NUL", _O_RDONLY | _O_BINARY | _O_NOINHERIT);
    if (fd < 0) {
        capture_errno();
        closesocket(s);
        return -1;
    }

    ensure_socket_table();
    EnterCriticalSection(&win_socket_table_lock);
    for (int i = 0; i < WIN_SOCKET_TABLE_SIZE; i++) {
        if (win_socket_table[i].fd < 0) {
            win_socket_table[i].fd = fd;
            win_socket_table[i].socket = s;
            win_socket_table[i].blocking = blocking;
            LeaveCriticalSection(&win_socket_table_lock);
            return fd;
        }
    }
    LeaveCriticalSection(&win_socket_table_lock);

    close_noraise(fd);
    closesocket(s);
    set_posix_errno(EMFILE);
    return -1;
}

static SOCKET win_remove_socket_fd(int32_t fd) {
    SOCKET result = INVALID_SOCKET;
    ensure_socket_table();
    EnterCriticalSection(&win_socket_table_lock);
    for (int i = 0; i < WIN_SOCKET_TABLE_SIZE; i++) {
        if (win_socket_table[i].fd == fd) {
            result = win_socket_table[i].socket;
            win_socket_table[i].fd = -1;
            win_socket_table[i].socket = INVALID_SOCKET;
            win_socket_table[i].blocking = 1;
            break;
        }
    }
    LeaveCriticalSection(&win_socket_table_lock);
    return result;
}

GP_EXPORT int32_t set_inheritable(int32_t fd, int32_t inheritable);

/* The caller must hold win_socket_table_lock. */
static int win_replace_socket_fd_locked(int32_t fd, SOCKET s, int blocking, SOCKET *replaced) {
    assert(win_socket_table_lock.OwningThread == (HANDLE) (uintptr_t) GetCurrentThreadId());
    int free_index = -1;
    for (int i = 0; i < WIN_SOCKET_TABLE_SIZE; i++) {
        if (win_socket_table[i].fd == fd) {
            *replaced = win_socket_table[i].socket;
            win_socket_table[i].socket = s;
            win_socket_table[i].blocking = blocking;
            return 0;
        }
        if (free_index < 0 && win_socket_table[i].fd < 0) {
            free_index = i;
        }
    }
    if (free_index < 0) {
        set_posix_errno(EMFILE);
        return -1;
    }
    win_socket_table[free_index].fd = fd;
    win_socket_table[free_index].socket = s;
    win_socket_table[free_index].blocking = blocking;
    *replaced = INVALID_SOCKET;
    return 0;
}

static SOCKET win_duplicate_socket(SOCKET s) {
    WSAPROTOCOL_INFOA protocol_info;
    if (WSADuplicateSocketA(s, GetCurrentProcessId(), &protocol_info) == SOCKET_ERROR) {
        set_wsa_errno();
        return INVALID_SOCKET;
    }
    SOCKET dup = WSASocketA(FROM_PROTOCOL_INFO, FROM_PROTOCOL_INFO, FROM_PROTOCOL_INFO, &protocol_info, 0, WSA_FLAG_NO_HANDLE_INHERIT);
    if (dup == INVALID_SOCKET) {
        set_wsa_errno();
        return INVALID_SOCKET;
    }
    return dup;
}

static int win_socket_dup(int32_t fd) {
    SOCKET s = win_socket_from_fd(fd);
    int blocking = win_socket_blocking_from_fd(fd);
    SOCKET dup = win_duplicate_socket(s);
    if (dup == INVALID_SOCKET) {
        return -1;
    }
    return win_alloc_socket_fd(dup, blocking);
}

static int win_socket_dup2(int32_t oldfd, int32_t newfd, int32_t inheritable) {
    SOCKET dup = win_duplicate_socket(win_socket_from_fd(oldfd));
    if (dup == INVALID_SOCKET) {
        return -1;
    }
    int blocking = win_socket_blocking_from_fd(oldfd);

    /*
     * Keep the table locked while replacing the dummy CRT fd. This ensures that
     * a full table is reported before _dup2 changes newfd and that the table
     * entry is updated atomically with respect to other table users.
     */
    ensure_socket_table();
    EnterCriticalSection(&win_socket_table_lock);
    int table_has_slot = 0;
    for (int i = 0; i < WIN_SOCKET_TABLE_SIZE; i++) {
        if (win_socket_table[i].fd == newfd || win_socket_table[i].fd < 0) {
            table_has_slot = 1;
            break;
        }
    }
    if (!table_has_slot) {
        LeaveCriticalSection(&win_socket_table_lock);
        closesocket(dup);
        set_posix_errno(EMFILE);
        return -1;
    }

    if (dup2_noraise(oldfd, newfd) != 0) {
        LeaveCriticalSection(&win_socket_table_lock);
        closesocket(dup);
        return -1;
    }

    SOCKET replaced = INVALID_SOCKET;
    int replaced_result = win_replace_socket_fd_locked(newfd, dup, blocking, &replaced);
    LeaveCriticalSection(&win_socket_table_lock);
    if (replaced_result < 0) {
        closesocket(dup);
        return -1;
    }
    if (replaced != INVALID_SOCKET) {
        closesocket(replaced);
    }
    if (!inheritable) {
        return set_inheritable(newfd, 0);
    }
    return 0;
}

static intptr_t get_osfhandle_noraise(int32_t fd) {
    intptr_t handle;
    BEGIN_SUPPRESS_IPH
    handle = _get_osfhandle(fd);
    END_SUPPRESS_IPH
    if (handle == -1) {
        capture_errno();
    }
    return handle;
}

static int open_osfhandle_noraise(int64_t handle, int32_t flags) {
    int fd;
    BEGIN_SUPPRESS_IPH
    fd = _open_osfhandle((intptr_t) handle, flags);
    END_SUPPRESS_IPH
    if (fd < 0) {
        capture_errno();
    }
    return fd;
}

static void unix_time_to_filetime(int64_t seconds, int64_t nanoseconds, FILETIME *out) {
    int64_t ticks = (seconds + 11644473600LL) * 10000000LL + nanoseconds / 100;
    out->dwLowDateTime = (DWORD) ticks;
    out->dwHighDateTime = (DWORD) (ticks >> 32);
}

static int64_t filetime_to_unix_time(FILETIME time) {
    ULARGE_INTEGER ticks;
    ticks.LowPart = time.dwLowDateTime;
    ticks.HighPart = time.dwHighDateTime;
    return (int64_t) (ticks.QuadPart / 10000000ULL) - 11644473600LL;
}

static int set_file_times(HANDLE handle, int64_t *times, int32_t nanosecond_resolution) {
    FILETIME atime;
    FILETIME mtime;
    if (times == NULL) {
        GetSystemTimeAsFileTime(&mtime);
        atime = mtime;
    } else {
        unix_time_to_filetime(times[0], nanosecond_resolution ? times[1] : times[1] * 1000, &atime);
        unix_time_to_filetime(times[2], nanosecond_resolution ? times[3] : times[3] * 1000, &mtime);
    }
    if (!SetFileTime(handle, NULL, &atime, &mtime)) {
        set_win_errno(GetLastError());
        return -1;
    }
    return 0;
}

static int set_path_times(const wchar_t *path, int64_t *times, int32_t nanosecond_resolution, int32_t followSymlinks) {
    DWORD flags = FILE_FLAG_BACKUP_SEMANTICS;
    if (!followSymlinks) {
        flags |= FILE_FLAG_OPEN_REPARSE_POINT;
    }
    HANDLE handle = CreateFileW(path, FILE_WRITE_ATTRIBUTES, FILE_SHARE_READ | FILE_SHARE_WRITE | FILE_SHARE_DELETE,
                    NULL, OPEN_EXISTING, flags, NULL);
    if (handle == INVALID_HANDLE_VALUE) {
        set_win_errno(GetLastError());
        return -1;
    }
    int result = set_file_times(handle, times, nanosecond_resolution);
    CloseHandle(handle);
    return result;
}

static int root_path_from_path(const wchar_t *path, wchar_t *root, DWORD root_size) {
    wchar_t full_path[32768];
    DWORD full_len = GetFullPathNameW(path, (DWORD) (sizeof(full_path) / sizeof(wchar_t)), full_path, NULL);
    if (full_len == 0 || full_len >= sizeof(full_path) / sizeof(wchar_t)) {
        set_win_errno(GetLastError());
        return -1;
    }
    if (!GetVolumePathNameW(full_path, root, root_size)) {
        set_win_errno(GetLastError());
        return -1;
    }
    return 0;
}

static int statvfs_from_root(const wchar_t *root, int64_t *out) {
    ULARGE_INTEGER free_bytes_available;
    ULARGE_INTEGER total_number_of_bytes;
    ULARGE_INTEGER total_number_of_free_bytes;
    DWORD sectors_per_cluster = 0;
    DWORD bytes_per_sector = 0;
    DWORD number_of_free_clusters = 0;
    DWORD total_number_of_clusters = 0;
    if (!GetDiskFreeSpaceExW(root, &free_bytes_available, &total_number_of_bytes, &total_number_of_free_bytes)) {
        set_win_errno(GetLastError());
        return -1;
    }
    if (!GetDiskFreeSpaceW(root, &sectors_per_cluster, &bytes_per_sector, &number_of_free_clusters, &total_number_of_clusters)) {
        sectors_per_cluster = 1;
        bytes_per_sector = 4096;
    }
    uint64_t block_size = (uint64_t) sectors_per_cluster * bytes_per_sector;
    if (block_size == 0) {
        block_size = 4096;
    }
    out[0] = (int64_t) block_size;
    out[1] = (int64_t) block_size;
    out[2] = (int64_t) (total_number_of_bytes.QuadPart / block_size);
    out[3] = (int64_t) (total_number_of_free_bytes.QuadPart / block_size);
    out[4] = (int64_t) (free_bytes_available.QuadPart / block_size);
    out[5] = 0;
    out[6] = 0;
    out[7] = 0;
    out[8] = 0;
    out[9] = 255;
    out[10] = 0;
    return 0;
}

static int statvfs_from_path(const wchar_t *path, int64_t *out) {
    DWORD attributes = GetFileAttributesW(path);
    if (attributes == INVALID_FILE_ATTRIBUTES) {
        set_win_errno(GetLastError());
        return -1;
    }

    wchar_t root[MAX_PATH];
    if (root_path_from_path(path, root, MAX_PATH) != 0) {
        return -1;
    }
    return statvfs_from_root(root, out);
}

static int copy_string_to_buffer(char *buffer, int32_t bufferSize, size_t *offset, const char *value, uint64_t *out, int32_t outIndex) {
    size_t len = strlen(value) + 1;
    if (*offset + len > (size_t) bufferSize) {
        return ERANGE;
    }
    out[outIndex] = *offset;
    memcpy(buffer + *offset, value, len);
    *offset += len;
    return 0;
}

static int get_current_username(char *buffer, DWORD size) {
    const char *env_name = getenv("USERNAME");
    if (env_name != NULL && strlen(env_name) < size) {
        strcpy(buffer, env_name);
        return 0;
    }
    if (size > 4) {
        strcpy(buffer, "user");
        return 0;
    }
    return ERANGE;
}

static void get_current_home(char *buffer, size_t size) {
    const char *profile = getenv("USERPROFILE");
    if (profile != NULL && strlen(profile) < size) {
        strcpy(buffer, profile);
        return;
    }
    const char *drive = getenv("HOMEDRIVE");
    const char *path = getenv("HOMEPATH");
    if (drive != NULL && path != NULL && strlen(drive) + strlen(path) < size) {
        strcpy(buffer, drive);
        strcat(buffer, path);
        return;
    }
    if (size > 0) {
        buffer[0] = '\0';
    }
}

static int fill_current_pwd(uint64_t uid, char *buffer, int32_t bufferSize, uint64_t *output) {
    char name[256];
    char dir[1024];
    const char *shell = getenv("COMSPEC");
    size_t offset = 0;
    int result = get_current_username(name, sizeof(name));
    if (result != 0) {
        return result;
    }
    get_current_home(dir, sizeof(dir));
    if (shell == NULL) {
        shell = "";
    }
    if ((result = copy_string_to_buffer(buffer, bufferSize, &offset, name, output, 0)) != 0) {
        return result;
    }
    output[1] = uid;
    output[2] = 0;
    if ((result = copy_string_to_buffer(buffer, bufferSize, &offset, dir, output, 3)) != 0) {
        return result;
    }
    return copy_string_to_buffer(buffer, bufferSize, &offset, shell, output, 4);
}

static void stat_struct_to_longs(struct __stat64 *st, int64_t *out) {
    out[0] = st->st_mode;
    out[1] = st->st_ino;
    out[2] = st->st_dev;
    out[3] = st->st_nlink;
    out[4] = st->st_uid;
    out[5] = st->st_gid;
    out[6] = st->st_size;
    out[7] = st->st_atime;
    out[8] = st->st_mtime;
    out[9] = st->st_ctime;
    out[10] = 0;
    out[11] = 0;
    out[12] = 0;
}

static void stat_handle_to_longs(HANDLE handle, int64_t *out) {
    BY_HANDLE_FILE_INFORMATION info;
    if (GetFileInformationByHandle(handle, &info)) {
        out[1] = ((int64_t) info.nFileIndexHigh << 32) | info.nFileIndexLow;
        out[2] = info.dwVolumeSerialNumber;
        out[3] = info.nNumberOfLinks;
        out[7] = filetime_to_unix_time(info.ftLastAccessTime);
        out[8] = filetime_to_unix_time(info.ftLastWriteTime);
        out[9] = filetime_to_unix_time(info.ftCreationTime);
    }
}

static void stat_path_handle_to_longs(const wchar_t *path, int32_t followSymlinks, int64_t *out) {
    DWORD flags = FILE_FLAG_BACKUP_SEMANTICS;
    if (!followSymlinks) {
        flags |= FILE_FLAG_OPEN_REPARSE_POINT;
    }
    HANDLE handle = CreateFileW(path, FILE_READ_ATTRIBUTES, FILE_SHARE_READ | FILE_SHARE_WRITE | FILE_SHARE_DELETE, NULL, OPEN_EXISTING, flags, NULL);
    if (handle != INVALID_HANDLE_VALUE) {
        stat_handle_to_longs(handle, out);
        CloseHandle(handle);
    }
}

static void stat_attributes_to_longs(DWORD attributes, DWORD sizeHigh, DWORD sizeLow, FILETIME accessTime, FILETIME writeTime, FILETIME creationTime, int64_t *out) {
    memset(out, 0, 13 * sizeof(*out));
    if (attributes & FILE_ATTRIBUTE_DIRECTORY) {
        out[0] = _S_IFDIR | 0111;
    } else {
        out[0] = _S_IFREG;
    }
    out[0] |= attributes & FILE_ATTRIBUTE_READONLY ? 0444 : 0666;
    out[3] = 1;
    out[6] = ((int64_t) sizeHigh << 32) | sizeLow;
    out[7] = filetime_to_unix_time(accessTime);
    out[8] = filetime_to_unix_time(writeTime);
    out[9] = filetime_to_unix_time(creationTime);
}

static int stat_path_attributes_to_longs(const wchar_t *path, int64_t *out) {
    WIN32_FILE_ATTRIBUTE_DATA info;
    if (GetFileAttributesExW(path, GetFileExInfoStandard, &info)) {
        stat_attributes_to_longs(info.dwFileAttributes, info.nFileSizeHigh, info.nFileSizeLow,
                        info.ftLastAccessTime, info.ftLastWriteTime, info.ftCreationTime, out);
        return 0;
    }
    DWORD error = GetLastError();
    if (error != ERROR_ACCESS_DENIED && error != ERROR_SHARING_VIOLATION) {
        set_win_errno(error);
        return -1;
    }

    WIN32_FIND_DATAW findInfo;
    HANDLE findHandle = FindFirstFileW(path, &findInfo);
    if (findHandle == INVALID_HANDLE_VALUE) {
        DWORD findError = GetLastError();
        switch (findError) {
            case ERROR_FILE_NOT_FOUND:
            case ERROR_PATH_NOT_FOUND:
            case ERROR_NOT_READY:
            case ERROR_BAD_NET_NAME:
                error = findError;
                break;
        }
        set_win_errno(error);
        return -1;
    }
    stat_attributes_to_longs(findInfo.dwFileAttributes, findInfo.nFileSizeHigh, findInfo.nFileSizeLow,
                    findInfo.ftLastAccessTime, findInfo.ftLastWriteTime, findInfo.ftCreationTime, out);
    FindClose(findHandle);
    return 0;
}

GP_EXPORT int64_t call_getpid(void) {
    return _getpid();
}

GP_EXPORT int32_t call_umask(int32_t mask) {
    return _umask(mask);
}

GP_EXPORT int32_t get_inheritable(int32_t fd) {
    int socket_index = win_socket_entry_index(fd);
    if (socket_index >= 0) {
        DWORD flags;
        SOCKET s = win_socket_from_fd(fd);
        if (!GetHandleInformation((HANDLE) s, &flags)) {
            set_win_errno(GetLastError());
            return -1;
        }
        return (flags & HANDLE_FLAG_INHERIT) != 0;
    }
    intptr_t os_handle = get_osfhandle_noraise(fd);
    if (os_handle == -1) {
        return -1;
    }
    DWORD flags;
    if (!GetHandleInformation((HANDLE) os_handle, &flags)) {
        set_win_errno(GetLastError());
        return -1;
    }
    return (flags & HANDLE_FLAG_INHERIT) != 0;
}

GP_EXPORT int32_t set_inheritable(int32_t fd, int32_t inheritable) {
    int socket_index = win_socket_entry_index(fd);
    if (socket_index >= 0) {
        SOCKET s = win_socket_from_fd(fd);
        if (!SetHandleInformation((HANDLE) s, HANDLE_FLAG_INHERIT, inheritable ? HANDLE_FLAG_INHERIT : 0)) {
            set_win_errno(GetLastError());
            return -1;
        }
        intptr_t reserve_handle = get_osfhandle_noraise(fd);
        if (reserve_handle != -1) {
            SetHandleInformation((HANDLE) reserve_handle, HANDLE_FLAG_INHERIT, inheritable ? HANDLE_FLAG_INHERIT : 0);
        }
        return 0;
    }
    intptr_t os_handle = get_osfhandle_noraise(fd);
    if (os_handle == -1) {
        return -1;
    }
    if (!SetHandleInformation((HANDLE) os_handle, HANDLE_FLAG_INHERIT, inheritable ? HANDLE_FLAG_INHERIT : 0)) {
        set_win_errno(GetLastError());
        return -1;
    }
    return 0;
}

GP_EXPORT int32_t call_openat(int32_t dirFd, const wchar_t *pathname, int32_t flags, int32_t mode) {
    if (!is_default_dir_fd(dirFd)) {
        return unsupported();
    }
    int open_flags = flags | _O_NOINHERIT;
    if ((open_flags & _O_TEXT) == 0) {
        open_flags |= _O_BINARY;
    }
    int result = _wopen(pathname, open_flags, mode);
    // The following _setmode is defensive enforcement; _wopen(..., _O_BINARY, ...) should already set this mode.
    if (result < 0) {
        capture_errno();
    } else if ((open_flags & _O_TEXT) == 0 && _setmode(result, _O_BINARY) < 0) {
        int error = errno;
        close_noraise(result);
        set_posix_errno(error);
        return -1;
    }
    return result;
}

GP_EXPORT int32_t call_close(int32_t fd) {
    SOCKET s = win_remove_socket_fd(fd);
    if (s != INVALID_SOCKET) {
        int socket_result = closesocket(s);
        int socket_error = socket_result == SOCKET_ERROR ? WSAGetLastError() : 0;
        int fd_result = close_noraise(fd);
        if (socket_result == SOCKET_ERROR) {
            return set_wsa_errno_from_error(socket_error);
        }
        return fd_result;
    }
    return close_noraise(fd);
}

GP_EXPORT int64_t call_read(int32_t fd, void *buf, uint64_t count) {
    if (win_socket_entry_index(fd) >= 0) {
        int result = recv(win_socket_from_fd(fd), buf, count > INT_MAX ? INT_MAX : (int) count, 0);
        return result == SOCKET_ERROR ? set_wsa_errno() : result;
    }
    return read_noraise(fd, buf, count > INT_MAX ? INT_MAX : (unsigned int) count);
}

GP_EXPORT int64_t call_write(int32_t fd, void *buf, uint64_t count) {
    if (win_socket_entry_index(fd) >= 0) {
        int result = send(win_socket_from_fd(fd), buf, count > INT_MAX ? INT_MAX : (int) count, 0);
        return result == SOCKET_ERROR ? set_wsa_errno() : result;
    }
    return write_noraise(fd, buf, count > INT_MAX ? INT_MAX : (unsigned int) count);
}

GP_EXPORT int32_t call_dup(int32_t fd) {
    if (win_socket_entry_index(fd) >= 0) {
        return win_socket_dup(fd);
    }
    int result = dup_noraise(fd);
    if (result >= 0) {
        set_inheritable(result, 0);
    }
    return result;
}

GP_EXPORT int32_t call_dup2(int32_t oldfd, int32_t newfd, int32_t inheritable) {
    if (oldfd == newfd) {
        int result = dup2_noraise(oldfd, newfd);
        if (result == 0 && !inheritable) {
            result = set_inheritable(newfd, 0);
        }
        return result == 0 ? newfd : -1;
    }
    if (win_socket_entry_index(oldfd) >= 0) {
        return win_socket_dup2(oldfd, newfd, inheritable) == 0 ? newfd : -1;
    }
    int result = dup2_noraise(oldfd, newfd);
    if (result != 0) {
        return -1;
    }
    SOCKET stale_socket = win_remove_socket_fd(newfd);
    if (stale_socket != INVALID_SOCKET) {
        closesocket(stale_socket);
    }
    if (!inheritable) {
        result = set_inheritable(newfd, 0);
    }
    return result == 0 ? newfd : -1;
}

GP_EXPORT int32_t call_get_osfhandle(int32_t fd, int64_t *out) {
    intptr_t handle = get_osfhandle_noraise(fd);
    if (handle == -1) {
        return -1;
    }
    out[0] = (int64_t) handle;
    return 0;
}

GP_EXPORT int32_t graalpy_get_socket_handle(int32_t fd, int64_t *out) {
    SOCKET s = win_socket_from_fd(fd);
    if (s != INVALID_SOCKET) {
        out[0] = (int64_t) s;
        return 0;
    }
    if (get_osfhandle_noraise(fd) == -1) {
        set_posix_errno(EBADF);
    } else {
        set_posix_errno(GP_ENOTSOCK);
    }
    return -1;
}

GP_EXPORT int32_t call_open_osfhandle(int64_t handle, int32_t flags) {
    if ((flags & _O_TEXT) == 0) {
        flags |= _O_BINARY;
    }
    return open_osfhandle_noraise(handle, flags);
}

GP_EXPORT int32_t call_pipe2(int32_t *pipefd) {
    int result = _pipe(pipefd, 8192, _O_BINARY | _O_NOINHERIT);
    if (result < 0) {
        capture_errno();
    }
    return result;
}

GP_EXPORT int32_t call_select(int32_t nfds, int32_t* readfds, int32_t readfdsLen,
    int32_t* writefds, int32_t writefdsLen, int32_t* errfds, int32_t errfdsLen,
    int64_t timeoutSec, int64_t timeoutUsec, int8_t* selected) {
    fd_set read_set, write_set, err_set;
    fd_set *read_ptr = readfdsLen ? &read_set : NULL;
    fd_set *write_ptr = writefdsLen ? &write_set : NULL;
    fd_set *err_ptr = errfdsLen ? &err_set : NULL;
    int32_t selectedLen = readfdsLen + writefdsLen + errfdsLen;
    if (ensure_winsock() < 0) {
        return -1;
    }
    FD_ZERO(&read_set);
    FD_ZERO(&write_set);
    FD_ZERO(&err_set);
    for (int32_t i = 0; i < readfdsLen; i++) {
        FD_SET(win_socket_from_fd(readfds[i]), &read_set);
    }
    for (int32_t i = 0; i < writefdsLen; i++) {
        FD_SET(win_socket_from_fd(writefds[i]), &write_set);
    }
    for (int32_t i = 0; i < errfdsLen; i++) {
        FD_SET(win_socket_from_fd(errfds[i]), &err_set);
    }
    if (selectedLen > 0) {
        memset(selected, 0, selectedLen * sizeof(*selected));
    }
    struct timeval timeout = {(long) timeoutSec, (long) timeoutUsec};
    int result = select(nfds, read_ptr, write_ptr, err_ptr, timeoutSec >= 0 ? &timeout : NULL);
    if (result == SOCKET_ERROR) {
        return set_wsa_errno();
    }
    for (int32_t i = 0; i < readfdsLen; i++) {
        selected[i] = FD_ISSET(win_socket_from_fd(readfds[i]), &read_set) != 0;
    }
    for (int32_t i = 0; i < writefdsLen; i++) {
        selected[readfdsLen + i] = FD_ISSET(win_socket_from_fd(writefds[i]), &write_set) != 0;
    }
    for (int32_t i = 0; i < errfdsLen; i++) {
        selected[readfdsLen + writefdsLen + i] = FD_ISSET(win_socket_from_fd(errfds[i]), &err_set) != 0;
    }
    return result;
}

GP_EXPORT int32_t call_poll(int32_t fd, int32_t writing, int64_t timeoutSec, int64_t timeoutUsec) {
    int8_t selected = 0;
    return call_select(1, writing ? NULL : &fd, writing ? 0 : 1, writing ? &fd : NULL, writing ? 1 : 0, NULL, 0, timeoutSec, timeoutUsec, &selected);
}

GP_EXPORT int64_t call_lseek(int32_t fd, int64_t offset, int32_t whence) {
    return lseek_noraise(fd, offset, whence);
}

GP_EXPORT int32_t call_ftruncate(int32_t fd, int64_t length) {
    return chsize_noraise(fd, length) == 0 ? 0 : -1;
}

GP_EXPORT int32_t call_truncate(const wchar_t *path, int64_t length) {
    int fd = _wopen(path, _O_RDWR | _O_BINARY | _O_NOINHERIT);
    if (fd < 0) {
        capture_errno();
        return -1;
    }
    int result = call_ftruncate(fd, length);
    int error = result != 0 ? errno_capture : 0;
    close_noraise(fd);
    if (result != 0) {
        set_posix_errno(error);
    }
    return result;
}

GP_EXPORT int32_t call_fsync(int32_t fd) {
    return commit_noraise(fd);
}

GP_EXPORT int32_t call_flock(int32_t fd, int32_t operation) { return unsupported(); }
GP_EXPORT int32_t call_fcntl_lock(int32_t fd, int32_t blocking, int32_t lockType, int32_t whence, int64_t start, int64_t length) { return unsupported(); }
GP_EXPORT int32_t get_blocking(int32_t fd) {
    return win_socket_blocking_from_fd(fd);
}

GP_EXPORT int32_t set_blocking(int32_t fd, int32_t blocking) {
    int socket_index = win_socket_entry_index(fd);
    if (socket_index >= 0) {
        u_long nonblocking = blocking ? 0 : 1;
        if (ioctlsocket(win_socket_from_fd(fd), FIONBIO, &nonblocking) == SOCKET_ERROR) {
            return set_wsa_errno();
        }
        ensure_socket_table();
        EnterCriticalSection(&win_socket_table_lock);
        for (int i = 0; i < WIN_SOCKET_TABLE_SIZE; i++) {
            if (win_socket_table[i].fd == fd) {
                win_socket_table[i].blocking = blocking != 0;
                break;
            }
        }
        LeaveCriticalSection(&win_socket_table_lock);
        return 0;
    }
    if (get_osfhandle_noraise(fd) == -1) {
        return -1;
    }
    return 0;
}

GP_EXPORT int32_t get_terminal_size(int32_t fd, int32_t *size) {
    CONSOLE_SCREEN_BUFFER_INFO csbi;
    intptr_t os_handle = get_osfhandle_noraise(fd);
    if (os_handle == -1 || !GetConsoleScreenBufferInfo((HANDLE) os_handle, &csbi)) {
        set_posix_errno(ENOTTY);
        return -1;
    }
    size[0] = csbi.srWindow.Right - csbi.srWindow.Left + 1;
    size[1] = csbi.srWindow.Bottom - csbi.srWindow.Top + 1;
    return 0;
}

GP_EXPORT int32_t call_fstatat(int32_t dirFd, const wchar_t *path, int32_t followSymlinks, int64_t *out) {
    if (!is_default_dir_fd(dirFd)) {
        return unsupported();
    }
    struct __stat64 st;
    int result = _wstat64(path, &st);
    if (result == 0) {
        stat_struct_to_longs(&st, out);
        stat_path_handle_to_longs(path, followSymlinks, out);
        return 0;
    }
    return stat_path_attributes_to_longs(path, out);
}

GP_EXPORT int32_t call_fstat(int32_t fd, int64_t *out) {
    if (get_osfhandle_noraise(fd) == -1) {
        return -1;
    }
    struct __stat64 st;
    int result;
    BEGIN_SUPPRESS_IPH
    result = _fstat64(fd, &st);
    END_SUPPRESS_IPH
    if (result == 0) {
        stat_struct_to_longs(&st, out);
        stat_handle_to_longs((HANDLE) get_osfhandle_noraise(fd), out);
    }
    if (result != 0) {
        capture_errno();
    }
    return result;
}

GP_EXPORT int32_t call_statvfs(const wchar_t *path, int64_t *out) {
    return statvfs_from_path(path, out);
}

GP_EXPORT int32_t call_fstatvfs(int32_t fd, int64_t *out) {
    intptr_t os_handle = get_osfhandle_noraise(fd);
    if (os_handle == -1) {
        return -1;
    }
    wchar_t path[32768];
    DWORD len = GetFinalPathNameByHandleW((HANDLE) os_handle, path, (DWORD) (sizeof(path) / sizeof(wchar_t)), FILE_NAME_NORMALIZED | VOLUME_NAME_DOS);
    if (len == 0 || len >= sizeof(path) / sizeof(wchar_t)) {
        set_win_errno(GetLastError());
        return -1;
    }
    const wchar_t *path_for_volume = path;
    if (wcsncmp(path, L"\\\\?\\", 4) == 0) {
        path_for_volume = path + 4;
    }
    return statvfs_from_path(path_for_volume, out);
}
GP_EXPORT int32_t call_uname(char *sysname, char *nodename, char *release, char *version, char *machine, int32_t size) { return unsupported(); }

GP_EXPORT int32_t call_unlinkat(int32_t dirFd, const wchar_t *pathname, int32_t rmdir) {
    if (!is_default_dir_fd(dirFd)) {
        return unsupported();
    }
    if (rmdir) {
        int result = _wrmdir(pathname);
        if (result != 0 && errno == EINVAL) {
            DWORD attributes = GetFileAttributesW(pathname);
            if (attributes != INVALID_FILE_ATTRIBUTES && !(attributes & FILE_ATTRIBUTE_DIRECTORY)) {
                errno = ENOTDIR;
            }
        }
        if (result != 0) {
            capture_errno();
        }
        return result;
    }
    int result = _wunlink(pathname);
    if (result != 0) {
        capture_errno();
    }
    return result;
}

GP_EXPORT int32_t call_linkat(int32_t oldDirFd, const wchar_t *oldPath, int32_t newDirFd, const wchar_t *newPath, int32_t flags) { return unsupported(); }
GP_EXPORT int32_t call_symlinkat(const wchar_t *target, int32_t dirFd, const wchar_t *linkpath) { return unsupported(); }

GP_EXPORT int32_t call_mkdirat(int32_t dirFd, const wchar_t *pathname, int32_t mode) {
    if (!is_default_dir_fd(dirFd)) {
        return unsupported();
    }
    int result = _wmkdir(pathname);
    if (result != 0) {
        capture_errno();
    }
    return result;
}

GP_EXPORT int32_t call_getcwd(wchar_t *buf, uint64_t size) {
    DWORD len = GetCurrentDirectoryW(size > UINT32_MAX ? UINT32_MAX : (DWORD) size, buf);
    if (len == 0) {
        set_win_errno(GetLastError());
        return -1;
    }
    if (len >= size) {
        set_posix_errno(ERANGE);
        return -1;
    }
    return 0;
}
GP_EXPORT int32_t call_chdir(const wchar_t *path) {
    int result = _wchdir(path);
    if (result != 0) {
        capture_errno();
    }
    return result;
}
GP_EXPORT int32_t call_fchdir(int32_t fd) {
    if (get_osfhandle_noraise(fd) == -1) {
        return -1;
    }
    return unsupported();
}
GP_EXPORT int32_t call_fchown(int32_t fd, int64_t owner, int64_t group) { return unsupported(); }
GP_EXPORT int32_t call_fchownat(int32_t dirfd, const wchar_t *pathname, int64_t owner, int64_t group, int32_t followSymlinks) { return unsupported(); }
GP_EXPORT int32_t call_isatty(int32_t fd) { return _isatty(fd); }

GP_EXPORT intptr_t call_opendir(const wchar_t *name) {
    if (name[0] == L'\0') {
        set_posix_errno(ENOENT);
        return 0;
    }
    size_t len = wcslen(name);
    int needs_separator = len > 0 && name[len - 1] != L'\\' && name[len - 1] != L'/' && name[len - 1] != L':';
    size_t pattern_len = len + (needs_separator ? 4 : 3) + 1;
    wchar_t *pattern = (wchar_t *) malloc(pattern_len * sizeof(wchar_t));
    if (!pattern) {
        set_posix_errno(ENOMEM);
        return 0;
    }
    wcscpy(pattern, name);
    if (needs_separator) {
        pattern[len++] = L'\\';
    }
    wcscpy(pattern + len, L"*.*");

    win_dir_t *dir = (win_dir_t *) calloc(1, sizeof(win_dir_t));
    if (!dir) {
        free(pattern);
        set_posix_errno(ENOMEM);
        return 0;
    }
    dir->handle = FindFirstFileW(pattern, &dir->data);
    dir->first = 1;
    free(pattern);
    if (dir->handle == INVALID_HANDLE_VALUE) {
        DWORD error = GetLastError();
        if (error == ERROR_FILE_NOT_FOUND) {
            DWORD attributes = GetFileAttributesW(name);
            if (attributes != INVALID_FILE_ATTRIBUTES && (attributes & FILE_ATTRIBUTE_DIRECTORY)) {
                dir->exhausted = 1;
                return (intptr_t) dir;
            }
            if (attributes == INVALID_FILE_ATTRIBUTES) {
                error = GetLastError();
            }
        }
        free(dir);
        set_win_errno(error);
        return 0;
    }
    return (intptr_t) dir;
}

GP_EXPORT intptr_t call_fdopendir(int32_t fd) {
    (void) fd;
    set_posix_errno(ENOSYS);
    return 0;
}

GP_EXPORT int32_t call_closedir(intptr_t dirp) {
    win_dir_t *dir = (win_dir_t *) dirp;
    if (!dir) {
        set_posix_errno(EBADF);
        return -1;
    }
    BOOL ok = dir->handle == INVALID_HANDLE_VALUE ? TRUE : FindClose(dir->handle);
    DWORD error = ok ? ERROR_SUCCESS : GetLastError();
    free(dir);
    if (!ok) {
        set_win_errno(error);
        return -1;
    }
    return 0;
}

GP_EXPORT int32_t call_readdir(intptr_t dirp, wchar_t *nameBuf, uint64_t nameBufSize, int64_t *out) {
    win_dir_t *dir = (win_dir_t *) dirp;
    if (!dir || nameBufSize == 0) {
        set_posix_errno(EBADF);
        return -1;
    }
    if (dir->exhausted) {
        return 0;
    }
    if (!dir->first) {
        if (!FindNextFileW(dir->handle, &dir->data)) {
            DWORD error = GetLastError();
            if (error == ERROR_NO_MORE_FILES) {
                return 0;
            } else {
                set_win_errno(error);
                return -1;
            }
        }
    }
    dir->first = 0;

    size_t name_len = wcslen(dir->data.cFileName) + 1;
    if (name_len > nameBufSize) {
        set_posix_errno(ENAMETOOLONG);
        return -1;
    }
    memcpy(nameBuf, dir->data.cFileName, name_len * sizeof(wchar_t));
    out[0] = 0;
    out[1] = WIN_DT_UNKNOWN;
    return 1;
}

GP_EXPORT void call_rewinddir(intptr_t dirp) {}
GP_EXPORT int32_t call_utimensat(int32_t dirFd, const wchar_t *path, int64_t *timespec, int32_t followSymlinks) {
    if (!is_default_dir_fd(dirFd)) {
        return unsupported();
    }
    return set_path_times(path, timespec, 1, followSymlinks);
}

GP_EXPORT int32_t call_futimens(int32_t fd, int64_t *timespec) {
    intptr_t os_handle = get_osfhandle_noraise(fd);
    if (os_handle == -1) {
        return -1;
    }
    return set_file_times((HANDLE) os_handle, timespec, 1);
}

GP_EXPORT int32_t call_futimes(int32_t fd, int64_t *timeval) {
    intptr_t os_handle = get_osfhandle_noraise(fd);
    if (os_handle == -1) {
        return -1;
    }
    return set_file_times((HANDLE) os_handle, timeval, 0);
}

GP_EXPORT int32_t call_lutimes(const wchar_t *filename, int64_t *timeval) {
    return set_path_times(filename, timeval, 0, 0);
}

GP_EXPORT int32_t call_utimes(const wchar_t *filename, int64_t *timeval) {
    return set_path_times(filename, timeval, 0, 1);
}

GP_EXPORT int32_t call_renameat(int32_t oldDirFd, const wchar_t *oldPath, int32_t newDirFd, const wchar_t *newPath) {
    if (!is_default_dir_fd(oldDirFd) || !is_default_dir_fd(newDirFd)) {
        return unsupported();
    }
    int result = _wrename(oldPath, newPath);
    if (result != 0) {
        capture_errno();
    }
    return result;
}

GP_EXPORT int32_t call_replaceat(int32_t oldDirFd, const wchar_t *oldPath, int32_t newDirFd, const wchar_t *newPath) {
    if (!is_default_dir_fd(oldDirFd) || !is_default_dir_fd(newDirFd)) {
        return unsupported();
    }
    BOOL result = MoveFileExW(oldPath, newPath, MOVEFILE_REPLACE_EXISTING);
    DWORD error = result ? ERROR_SUCCESS : GetLastError();
    if (!result) {
        set_win_errno(error);
        return -1;
    }
    return 0;
}

GP_EXPORT int32_t call_faccessat(int32_t dirFd, const wchar_t *path, int32_t mode, int32_t effectiveIds, int32_t followSymlinks) {
    if (!is_default_dir_fd(dirFd)) {
        return unsupported();
    }
    int result = _waccess(path, mode);
    if (result != 0) {
        capture_errno();
    }
    return result;
}

GP_EXPORT int32_t call_fchmodat(int32_t dirFd, const wchar_t *path, int32_t mode, int32_t followSymlinks) {
    if (!is_default_dir_fd(dirFd)) {
        return unsupported();
    }
    int result = _wchmod(path, mode);
    if (result != 0) {
        capture_errno();
    }
    return result;
}

GP_EXPORT int32_t call_fchmod(int32_t fd, int32_t mode) {
    (void) mode;
    if (get_osfhandle_noraise(fd) == -1) {
        return -1;
    }
    return unsupported();
}
GP_EXPORT int64_t call_readlinkat(int32_t dirFd, const wchar_t *path, wchar_t *buf, uint64_t size) {
    if (!is_default_dir_fd(dirFd)) {
        return unsupported();
    }
    DWORD attributes = GetFileAttributesW(path);
    DWORD error = attributes == INVALID_FILE_ATTRIBUTES ? GetLastError() : ERROR_SUCCESS;
    if (attributes == INVALID_FILE_ATTRIBUTES) {
        set_win_errno(error);
        return -1;
    }
    return unsupported();
}
GP_EXPORT int64_t call_waitpid(int64_t pid, int32_t *status, int32_t options) { return unsupported(); }
GP_EXPORT int32_t call_wcoredump(int32_t status) { return 0; }
GP_EXPORT int32_t call_wifcontinued(int32_t status) { return 0; }
GP_EXPORT int32_t call_wifstopped(int32_t status) { return 0; }
GP_EXPORT int32_t call_wifsignaled(int32_t status) { return 0; }
GP_EXPORT int32_t call_wifexited(int32_t status) { return 0; }
GP_EXPORT int32_t call_wexitstatus(int32_t status) { return status; }
GP_EXPORT int32_t call_wtermsig(int32_t status) { return 0; }
GP_EXPORT int32_t call_wstopsig(int32_t status) { return 0; }
GP_EXPORT int32_t call_kill(int64_t pid, int32_t signal) {
    if (signal == CTRL_C_EVENT || signal == CTRL_BREAK_EVENT) {
        if (!GenerateConsoleCtrlEvent((DWORD) signal, (DWORD) pid)) {
            set_win_errno(GetLastError());
            return -1;
        }
        return 0;
    }

    HANDLE process = OpenProcess(PROCESS_ALL_ACCESS, FALSE, (DWORD) pid);
    if (process == NULL) {
        set_win_errno(GetLastError());
        return -1;
    }
    BOOL result = TerminateProcess(process, (UINT) signal);
    DWORD error = GetLastError();
    CloseHandle(process);
    if (!result) {
        set_win_errno(error);
        return -1;
    }
    return 0;
}
GP_EXPORT int32_t call_raise(int32_t signal) { return raise(signal); }
GP_EXPORT int32_t call_alarm(int32_t seconds) { return 0; }
GP_EXPORT int32_t call_getitimer(int32_t which, int64_t *current_value) { return unsupported(); }
GP_EXPORT int32_t call_setitimer(int32_t which, int64_t *new_value, int64_t *old_value) { return unsupported(); }
GP_EXPORT int32_t signal_self(int32_t signal) {
    int result = raise(signal);
    if (result != 0) {
        capture_errno();
    }
    return result;
}
GP_EXPORT int32_t call_killpg(int64_t pgid, int32_t signal) { return unsupported(); }
GP_EXPORT int64_t call_getuid(void) { return 0; }
GP_EXPORT int64_t call_geteuid(void) { return 1; }
GP_EXPORT int64_t call_getgid(void) { return 0; }
GP_EXPORT int64_t call_getegid(void) { return 0; }
GP_EXPORT int64_t call_getppid(void) { return 0; }
GP_EXPORT int64_t call_getpgid(int64_t pid) { return unsupported(); }
GP_EXPORT int32_t call_setpgid(int64_t pid, int64_t pgid) { return unsupported(); }
GP_EXPORT int64_t call_getpgrp(void) { return 0; }
GP_EXPORT int64_t call_getsid(int64_t pid) { return unsupported(); }
GP_EXPORT int64_t call_setsid(void) { return unsupported(); }
GP_EXPORT int32_t call_getgroups(int64_t size, int64_t* out) { return 0; }
GP_EXPORT int32_t call_getrusage(int32_t who, uint64_t* out) { return unsupported(); }
GP_EXPORT int32_t call_openpty(int32_t *outvars) { return unsupported(); }
GP_EXPORT int32_t call_ctermid(char *buf) { return unsupported(); }
GP_EXPORT int32_t call_setenv(wchar_t *name, wchar_t *value, int overwrite) {
    (void) overwrite;
    size_t name_len = wcslen(name);
    size_t value_len = wcslen(value);
    wchar_t *env = malloc((name_len + value_len + 2) * sizeof(wchar_t));
    if (env == NULL) {
        set_posix_errno(ENOMEM);
        return -1;
    }
    memcpy(env, name, name_len * sizeof(wchar_t));
    env[name_len] = L'=';
    memcpy(env + name_len + 1, value, (value_len + 1) * sizeof(wchar_t));
    int result = _wputenv(env);
    free(env);
    if (result != 0) {
        capture_errno();
        return -1;
    }
    return 0;
}
GP_EXPORT int32_t call_unsetenv(wchar_t *name) {
    size_t name_len = wcslen(name);
    wchar_t *env = malloc((name_len + 2) * sizeof(wchar_t));
    if (env == NULL) {
        set_posix_errno(ENOMEM);
        return -1;
    }
    memcpy(env, name, name_len * sizeof(wchar_t));
    env[name_len] = L'=';
    env[name_len + 1] = L'\0';
    int result = _wputenv(env);
    free(env);
    if (result != 0) {
        capture_errno();
        return -1;
    }
    return 0;
}
GP_EXPORT void call_execv(char *data, int64_t *offsets, int32_t offsetsLen) { set_posix_errno(ENOSYS); }
GP_EXPORT int32_t call_system(const wchar_t *command) { return _wsystem(command); }

GP_EXPORT int64_t call_mmap(int64_t length, int32_t prot, int32_t flags, int32_t fd, int64_t offset, wchar_t *tagname) {
    const int32_t win_prot_read = 1;
    const int32_t win_prot_write = 2;
    const int32_t win_map_shared = 1;
    HANDLE file_handle = INVALID_HANDLE_VALUE;
    DWORD protect;
    DWORD desired_access;
    uint64_t max_size = (uint64_t) (offset + length);
    HANDLE mapping;
    DWORD mapping_error;
    void *view;

    if (length < 0 || offset < 0) {
        set_posix_errno(EINVAL);
        return 0;
    }

    if (fd != -1) {
        intptr_t os_handle = get_osfhandle_noraise(fd);
        if (os_handle == -1) {
            return 0;
        }
        file_handle = (HANDLE) os_handle;
    } else {
        max_size = (uint64_t) length;
    }

    if ((prot & win_prot_write) != 0) {
        if ((flags & win_map_shared) != 0) {
            protect = PAGE_READWRITE;
            desired_access = FILE_MAP_WRITE;
        } else {
            protect = PAGE_WRITECOPY;
            desired_access = FILE_MAP_COPY;
        }
    } else if ((prot & win_prot_read) != 0) {
        protect = PAGE_READONLY;
        desired_access = FILE_MAP_READ;
    } else {
        protect = PAGE_NOACCESS;
        desired_access = 0;
    }

    SetLastError(ERROR_SUCCESS);
    mapping = CreateFileMappingW(file_handle, NULL, protect, (DWORD) (max_size >> 32), (DWORD) max_size, tagname);
    mapping_error = GetLastError();
    if (mapping == NULL) {
        set_win_errno(mapping_error);
        return 0;
    }

    view = MapViewOfFile(mapping, desired_access, (DWORD) ((uint64_t) offset >> 32), (DWORD) offset, (SIZE_T) length);
    if (view == NULL) {
        set_win_errno(GetLastError());
        CloseHandle(mapping);
        return 0;
    }

    if (win_add_mmap(view, mapping) < 0) {
        UnmapViewOfFile(view);
        CloseHandle(mapping);
        return 0;
    }
    SetLastError(mapping_error);
    return (int64_t) view;
}

GP_EXPORT int32_t call_munmap(int64_t address, int64_t length) {
    (void) length;
    void *view = (void *) address;
    HANDLE mapping = win_remove_mmap(view);
    int result = 0;
    if (mapping == NULL) {
        set_posix_errno(EINVAL);
        return -1;
    }
    if (!UnmapViewOfFile(view)) {
        set_win_errno(GetLastError());
        result = -1;
    }
    if (!CloseHandle(mapping) && result == 0) {
        set_win_errno(GetLastError());
        result = -1;
    }
    return result;
}

GP_EXPORT void call_msync(int64_t address, int64_t offset, int64_t length) {
    FlushViewOfFile((void *) (address + offset), (SIZE_T) length);
}

GP_EXPORT int32_t call_socket(int32_t family, int32_t type, int32_t protocol) {
    if (ensure_winsock() < 0) {
        return -1;
    }
    SOCKET s = socket(family, type, protocol);
    if (s == INVALID_SOCKET) {
        return set_wsa_errno();
    }
    return win_alloc_socket_fd(s, 1);
}

GP_EXPORT int32_t call_accept(int32_t sockfd, int8_t *addr, int32_t *addr_len) {
    int len = sizeof(struct sockaddr_storage);
    SOCKET s = accept(win_socket_from_fd(sockfd), (struct sockaddr *) addr, &len);
    if (s == INVALID_SOCKET) {
        return set_wsa_errno();
    }
    *addr_len = len;
    return win_alloc_socket_fd(s, 1);
}

GP_EXPORT int32_t call_bind(int32_t sockfd, int8_t *addr, int32_t addr_len) { int r = bind(win_socket_from_fd(sockfd), (struct sockaddr *) addr, addr_len); return r == SOCKET_ERROR ? set_wsa_errno() : r; }
GP_EXPORT int32_t call_connect(int32_t sockfd, int8_t *addr, int32_t addr_len) { int r = connect(win_socket_from_fd(sockfd), (struct sockaddr *) addr, addr_len); return r == SOCKET_ERROR ? set_wsa_errno() : r; }
GP_EXPORT int32_t call_listen(int32_t sockfd, int32_t backlog) { int r = listen(win_socket_from_fd(sockfd), backlog); return r == SOCKET_ERROR ? set_wsa_errno() : r; }
GP_EXPORT int32_t call_getpeername(int32_t sockfd, int8_t *addr, int32_t *addr_len) { int len = sizeof(struct sockaddr_storage); int r = getpeername(win_socket_from_fd(sockfd), (struct sockaddr *) addr, &len); if (r == SOCKET_ERROR) return set_wsa_errno(); *addr_len = len; return r; }
GP_EXPORT int32_t call_getsockname(int32_t sockfd, int8_t *addr, int32_t *addr_len) {
    SOCKET s = win_socket_from_fd(sockfd);
    if (s == INVALID_SOCKET) {
        set_posix_errno(get_osfhandle_noraise(sockfd) == -1 ? EBADF : GP_ENOTSOCK);
        return -1;
    }
    int len = sizeof(struct sockaddr_storage);
    int r = getsockname(s, (struct sockaddr *) addr, &len);
    if (r == SOCKET_ERROR) {
        return set_wsa_errno();
    }
    *addr_len = len;
    return r;
}
GP_EXPORT int32_t call_send(int32_t sockfd, void *buf, int32_t len, int32_t flags) { int r = send(win_socket_from_fd(sockfd), buf, len, flags); return r == SOCKET_ERROR ? set_wsa_errno() : r; }
GP_EXPORT int32_t call_sendto(int32_t sockfd, void *buf, int32_t offset, int32_t len, int32_t flags, int8_t *addr, int32_t addr_len) { int r = sendto(win_socket_from_fd(sockfd), ((char *) buf) + offset, len, flags, (struct sockaddr *) addr, addr_len); return r == SOCKET_ERROR ? set_wsa_errno() : r; }
GP_EXPORT int32_t call_recv(int32_t sockfd, void *buf, int32_t len, int32_t flags) { int r = recv(win_socket_from_fd(sockfd), buf, len, flags); return r == SOCKET_ERROR ? set_wsa_errno() : r; }
GP_EXPORT int32_t call_recvfrom(int32_t sockfd, void *buf, int32_t offset, int32_t len, int32_t flags, int8_t *src_addr, int32_t *addr_len) { int l = sizeof(struct sockaddr_storage); int r = recvfrom(win_socket_from_fd(sockfd), ((char *) buf) + offset, len, flags, (struct sockaddr *) src_addr, &l); if (r == SOCKET_ERROR) return set_wsa_errno(); *addr_len = l; return r; }
GP_EXPORT int32_t call_shutdown(int32_t sockfd, int32_t how) { int r = shutdown(win_socket_from_fd(sockfd), how); return r == SOCKET_ERROR ? set_wsa_errno() : r; }
GP_EXPORT int32_t call_getsockopt(int32_t sockfd, int32_t level, int32_t optname, void *buf, int32_t *bufLen) { int len = *bufLen; int r = getsockopt(win_socket_from_fd(sockfd), level, optname, buf, &len); if (r == SOCKET_ERROR) return set_wsa_errno(); *bufLen = len; return r; }
GP_EXPORT int32_t call_setsockopt(int32_t sockfd, int32_t level, int32_t optname, void *buf, int32_t bufLen) {
    SOCKET s = win_socket_from_fd(sockfd);
    uintptr_t socket_handle;
    if (s == INVALID_SOCKET) {
        return set_wsa_errno();
    }
    if (level == SOL_SOCKET && optname == SO_UPDATE_ACCEPT_CONTEXT && buf != NULL && bufLen == sizeof(uintptr_t)) {
        int32_t listen_fd = (int32_t) (*(uintptr_t *) buf);
        SOCKET listen_socket = win_socket_from_fd(listen_fd);
        if (listen_socket == INVALID_SOCKET) {
            return set_wsa_errno();
        }
        socket_handle = (uintptr_t) listen_socket;
        buf = &socket_handle;
    }
    int r = setsockopt(s, level, optname, buf, bufLen);
    return r == SOCKET_ERROR ? set_wsa_errno() : r;
}
GP_EXPORT int32_t call_inet_addr(const char *src) { return ntohl(inet_addr(src)); }
GP_EXPORT int64_t call_inet_aton(const char *src) {
    unsigned long packed = inet_addr(src);
    if (packed == INADDR_NONE && strcmp(src, "255.255.255.255") != 0) {
        return -1;
    }
    return (int64_t) (uint32_t) ntohl(packed);
}
GP_EXPORT int32_t call_inet_ntoa(int32_t src, char *dst) { struct in_addr addr; addr.s_addr = htonl(src); const char *s = inet_ntop(AF_INET, &addr, dst, INET_ADDRSTRLEN); return s == NULL ? -1 : (int32_t) strlen(s); }
GP_EXPORT int32_t call_inet_pton(int32_t family, const char *src, void *dst) { int r = inet_pton(family, src, dst); return r == SOCKET_ERROR ? set_wsa_errno() : r; }
GP_EXPORT int32_t call_inet_ntop(int32_t family, void *src, char *dst, int32_t dstSize) { return inet_ntop(family, src, dst, dstSize) == NULL ? set_wsa_errno() : 0; }
GP_EXPORT int32_t call_gethostname(wchar_t *buf, int64_t bufLen) {
    wchar_t stack_buf[MAX_COMPUTERNAME_LENGTH + 1];
    DWORD size = sizeof(stack_buf) / sizeof(stack_buf[0]);
    wchar_t *name = stack_buf;
    int result = 0;

    if (!GetComputerNameExW(ComputerNamePhysicalDnsHostname, stack_buf, &size)) {
        if (GetLastError() != ERROR_MORE_DATA) {
            set_win_errno(GetLastError());
            return -1;
        }
        if (size == 0) {
            if (bufLen > 0) {
                buf[0] = L'\0';
                return 0;
            }
            set_posix_errno(EFAULT);
            return -1;
        }
        name = malloc(size * sizeof(wchar_t));
        if (name == NULL) {
            set_posix_errno(ENOMEM);
            return -1;
        }
        if (!GetComputerNameExW(ComputerNamePhysicalDnsHostname, name, &size)) {
            set_win_errno(GetLastError());
            free(name);
            return -1;
        }
    }

    if ((int64_t) size >= bufLen) {
        set_posix_errno(ENAMETOOLONG);
        result = -1;
    } else {
        memcpy(buf, name, size * sizeof(wchar_t));
        buf[size] = L'\0';
    }

    if (name != stack_buf) {
        free(name);
    }
    return result;
}
GP_EXPORT int32_t call_getnameinfo(int8_t *addr, int32_t addr_len, char *hostBuf, int32_t hostBufLen, char *servBuf, int32_t servBufLen, int32_t flags) { return getnameinfo((struct sockaddr *) addr, addr_len, hostBuf, hostBufLen, servBuf, servBufLen, flags); }
GP_EXPORT int32_t call_getaddrinfo(const char *node, const char *service, int32_t family, int32_t sockType, int32_t protocol, int32_t flags, int64_t *ptr) { struct addrinfo hints = {0}; struct addrinfo *res = NULL; hints.ai_family = family; hints.ai_socktype = sockType; hints.ai_protocol = protocol; hints.ai_flags = flags; int ret = getaddrinfo(node, service, &hints, &res); if (ret == 0) { *ptr = (int64_t) res; } return ret; }
GP_EXPORT void call_freeaddrinfo(int64_t ptr) { freeaddrinfo((struct addrinfo *) ptr); }
GP_EXPORT void call_gai_strerror(int32_t error, char *buf, int32_t buflen) { snprintf(buf, buflen, "%d", error); }
GP_EXPORT int32_t get_addrinfo_members(int64_t ptr, int32_t *intData, int64_t *longData, int8_t *addr) {
    struct addrinfo *ai = (struct addrinfo *) ptr;
    if (!ai) {
        return 0;
    }

    memcpy(addr, ai->ai_addr, ai->ai_addrlen);

    longData[0] = (int64_t) ai->ai_canonname;
    longData[1] = (int64_t) ai->ai_next;

    intData[0] = ai->ai_flags;
    intData[1] = ai->ai_family;
    intData[2] = ai->ai_socktype;
    intData[3] = ai->ai_protocol;
    intData[4] = (int32_t) ai->ai_addrlen;
    intData[5] = ai->ai_addr->sa_family;
    intData[6] = 0;
    if (ai->ai_canonname != NULL) {
        size_t len = strlen(ai->ai_canonname);
        if (len >= 0x7fffffff) {
            return -1;
        }
        intData[6] = (int32_t) len;
    }
    return 0;
}

GP_EXPORT void *call_sem_open(const char *name, int32_t openFlags, int32_t mode, int32_t value) {
    (void) mode;
    HANDLE handle;
    if ((openFlags & O_CREAT) != 0) {
        handle = CreateSemaphoreA(NULL, value, LONG_MAX, NULL);
    } else {
        handle = OpenSemaphoreA(SEMAPHORE_ALL_ACCESS, FALSE, name);
    }
    if (handle == NULL) {
        set_win_errno(GetLastError());
    }
    return handle;
}
GP_EXPORT int32_t call_sem_close(void* handle) {
    if (!CloseHandle((HANDLE) handle)) {
        set_win_errno(GetLastError());
        return -1;
    }
    return 0;
}
GP_EXPORT int32_t call_sem_unlink(const char *name) {
    (void) name;
    return 0;
}
GP_EXPORT int32_t call_sem_getvalue(void* handle, int32_t *value) {
    DWORD result = WaitForSingleObject((HANDLE) handle, 0);
    if (result == WAIT_TIMEOUT) {
        *value = 0;
        return 0;
    }
    if (result != WAIT_OBJECT_0) {
        set_win_errno(GetLastError());
        return -1;
    }
    LONG previousCount;
    if (!ReleaseSemaphore((HANDLE) handle, 1, &previousCount)) {
        set_win_errno(GetLastError());
        return -1;
    }
    *value = previousCount + 1;
    return 0;
}
GP_EXPORT int32_t call_sem_post(void* handle) {
    if (!ReleaseSemaphore((HANDLE) handle, 1, NULL)) {
        DWORD error = GetLastError();
        set_win_errno(error);
        return -1;
    }
    return 0;
}
GP_EXPORT int32_t call_sem_wait(void* handle) {
    DWORD result = WaitForSingleObject((HANDLE) handle, INFINITE);
    if (result == WAIT_OBJECT_0) {
        return 0;
    }
    set_win_errno(GetLastError());
    return -1;
}
GP_EXPORT int32_t call_sem_trywait(void* handle) {
    DWORD result = WaitForSingleObject((HANDLE) handle, 0);
    if (result == WAIT_OBJECT_0) {
        return 0;
    }
    if (result == WAIT_TIMEOUT) {
        set_posix_errno(EAGAIN);
    } else {
        set_win_errno(GetLastError());
    }
    return -1;
}
GP_EXPORT int32_t call_sem_timedwait(void* handle, int64_t deadlineNs) { return unsupported(); }
GP_EXPORT int64_t get_sysconf_getpw_r_size_max(void) { return -1; }
GP_EXPORT int32_t call_getpwuid_r(uint64_t uid, char *buffer, int32_t bufferSize, uint64_t *output) {
    if (uid != 0) {
        return -1;
    }
    return fill_current_pwd(uid, buffer, bufferSize, output);
}

GP_EXPORT int32_t call_getpwname_r(const char *name, char *buffer, int32_t bufferSize, uint64_t *output) {
    char current_name[256];
    int result = get_current_username(current_name, sizeof(current_name));
    if (result != 0) {
        return result;
    }
    if (_stricmp(name, current_name) != 0) {
        return -1;
    }
    return fill_current_pwd(0, buffer, bufferSize, output);
}
GP_EXPORT void call_setpwent(void) {}
GP_EXPORT void call_endpwent(void) {}
GP_EXPORT void *call_getpwent(int64_t *bufferSize) { return NULL; }
GP_EXPORT int32_t get_getpwent_data(void *p, char *buffer, int32_t bufferSize, uint64_t *output) { return ENOSYS; }
GP_EXPORT int32_t call_ioctl_bytes(int32_t fd, uint64_t request, char* buffer) { return unsupported(); }
GP_EXPORT int32_t call_ioctl_int(int32_t fd, uint64_t request, int32_t arg) { return unsupported(); }
GP_EXPORT int64_t call_sysconf(int32_t name) { set_posix_errno(EINVAL); return -1; }
GP_EXPORT int32_t get_errno(void) { return errno_capture; }
GP_EXPORT int32_t get_winerror(void) { return winerror_capture; }
GP_EXPORT int32_t get_wsaerror(void) { return wsaerror_capture; }
GP_EXPORT int32_t get_error_source(void) { return error_source_capture; }
GP_EXPORT void set_errno(int e) { errno = e; }

GP_EXPORT void call_initialize(void) {
    _setmode(0, _O_BINARY);
    _setmode(1, _O_BINARY);
    _setmode(2, _O_BINARY);
}

GP_EXPORT int32_t init_constants(int64_t* out, int32_t len) {
    if (len != 33)
        return -1;
    out[0] = sizeof(struct sockaddr);
    out[1] = sizeof(((struct sockaddr*)0)->sa_family);
    out[2] = offsetof(struct sockaddr, sa_family);
    out[3] = sizeof(struct sockaddr_storage);
    out[4] = sizeof(struct sockaddr_in);
    out[5] = sizeof(((struct sockaddr_in*)0)->sin_family);
    out[6] = offsetof(struct sockaddr_in, sin_family);
    out[7] = sizeof(((struct sockaddr_in*)0)->sin_port);
    out[8] = offsetof(struct sockaddr_in, sin_port);
    out[9] = sizeof(((struct sockaddr_in*)0)->sin_addr);
    out[10] = offsetof(struct sockaddr_in, sin_addr);
    out[11] = sizeof(struct sockaddr_in6);
    out[12] = sizeof(((struct sockaddr_in6*)0)->sin6_family);
    out[13] = offsetof(struct sockaddr_in6, sin6_family);
    out[14] = sizeof(((struct sockaddr_in6*)0)->sin6_port);
    out[15] = offsetof(struct sockaddr_in6, sin6_port);
    out[16] = sizeof(((struct sockaddr_in6*)0)->sin6_flowinfo);
    out[17] = offsetof(struct sockaddr_in6, sin6_flowinfo);
    out[18] = sizeof(((struct sockaddr_in6*)0)->sin6_addr);
    out[19] = offsetof(struct sockaddr_in6, sin6_addr);
    out[20] = sizeof(((struct sockaddr_in6*)0)->sin6_scope_id);
    out[21] = offsetof(struct sockaddr_in6, sin6_scope_id);
    out[22] = sizeof(struct in_addr);
    out[23] = sizeof(((struct in_addr*)0)->s_addr);
    out[24] = offsetof(struct in_addr, s_addr);
    out[25] = sizeof(struct in6_addr);
    out[26] = sizeof(((struct in6_addr*)0)->s6_addr);
    out[27] = offsetof(struct in6_addr, s6_addr);
    out[28] = sizeof(struct sockaddr_un);
    out[29] = sizeof(((struct sockaddr_un*)0)->sun_family);
    out[30] = offsetof(struct sockaddr_un, sun_family);
    out[31] = sizeof(((struct sockaddr_un*)0)->sun_path);
    out[32] = offsetof(struct sockaddr_un, sun_path);
    return 0;
}

#else

// This file uses GNU extensions. Functions that require non-GNU versions (e.g. strerror_r)
// need to go to posix_no_gnu.c
#if defined(__gnu_linux__) && !defined(_GNU_SOURCE)
#define _GNU_SOURCE 1
#endif

#include <arpa/inet.h>
#include <assert.h>
#include <dirent.h>
#include <errno.h>
#include <fcntl.h>
#include <netdb.h>
#include <netinet/in.h>
#include <semaphore.h>
#include <signal.h>
#include <stddef.h>
#include <stdio.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/stat.h>
#include <sys/statvfs.h>
#include <sys/select.h>
#include <sys/socket.h>
#include <sys/time.h>
#include <sys/types.h>
#include <sys/un.h>
#include <sys/utsname.h>
#include <sys/wait.h>
#include <sys/file.h>
#include <sys/mman.h>
#include <unistd.h>
#include <pwd.h>

#include "errno_capture.h"

THREAD_LOCAL int errno_capture = 0;

int32_t signal_self_segv(void);

#ifdef __APPLE__
#include <util.h>
#else
#include <pty.h>
#endif

#ifndef _WIN32
#include <time.h>
#include <poll.h>
#include <limits.h>
#include <sys/resource.h>
#endif

int64_t call_getpid(void) {
    return getpid();
}

int32_t call_umask(int32_t mask) {
    return umask(mask);
}

int32_t get_inheritable(int32_t fd) {
    int flags = fcntl(fd, F_GETFD, 0);
    if (flags < 0) {
        capture_errno();
        return flags;
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
    } else {
        capture_errno();
    }
    return res;
}

int32_t call_openat(int32_t dirFd, const char *pathname, int32_t flags, int32_t mode) {
    CAPTURE_ERRNO_AND_RETURN(-1, openat(dirFd, pathname, flags, mode));
}

int32_t call_close(int32_t fd) {
    CAPTURE_ERRNO_AND_RETURN(-1, close(fd));
}

int64_t call_read(int32_t fd, void *buf, uint64_t count) {
    CAPTURE_ERRNO_AND_RETURN(-1, read(fd, buf, count));
}

int64_t call_write(int32_t fd, void *buf, uint64_t count) {
    CAPTURE_ERRNO_AND_RETURN(-1, write(fd, buf, count));
}

int32_t call_dup(int32_t fd) {
    CAPTURE_ERRNO_AND_RETURN(-1, fcntl(fd, F_DUPFD_CLOEXEC, 0));
}

int32_t call_dup2(int32_t oldfd, int32_t newfd, int32_t inheritable) {
#ifdef __gnu_linux__
    if (!inheritable) {
        CAPTURE_ERRNO_AND_RETURN(-1, dup3(oldfd, newfd, O_CLOEXEC));
    }
#endif
    int res = dup2(oldfd, newfd);
    if (res < 0) {
        capture_errno();
        return res;
    }
    if (!inheritable && set_inheritable(res, 0) < 0) {
        close(res);
        return -1;
    }
    return res;
}

int32_t call_get_osfhandle(int32_t fd, int64_t *out) {
    (void) fd;
    (void) out;
    errno = ENOSYS;
    return -1;
}

int32_t call_open_osfhandle(int64_t handle, int32_t flags) {
    (void) handle;
    (void) flags;
    errno = ENOSYS;
    return -1;
}

int32_t call_pipe2(int32_t *pipefd) {
#ifdef __gnu_linux__
    CAPTURE_ERRNO_AND_RETURN(-1, pipe2(pipefd, O_CLOEXEC));
#else
    int res = pipe(pipefd);
    if (res != 0) {
        capture_errno();
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
    int32_t selectedLen = readfdsLen + writefdsLen + errfdsLen;
    fill_fd_set(&readfdsSet, readfds, readfdsLen);
    fill_fd_set(&writefdsSet, writefds, writefdsLen);
    fill_fd_set(&errfdsSet, errfds, errfdsLen);
    if (selectedLen > 0) {
        memset(selected, 0, selectedLen * sizeof(*selected));
    }

    struct timeval timeout = {timeoutSec, timeoutUsec};

    int result = select(nfds, &readfdsSet, &writefdsSet, &errfdsSet, timeoutSec >= 0 ? &timeout : NULL);

    // fill in the output parameter
    fill_select_result(readfds, readfdsLen, &readfdsSet, selected, 0);
    fill_select_result(writefds, writefdsLen, &writefdsSet, selected, readfdsLen);
    fill_select_result(errfds, errfdsLen, &errfdsSet, selected, readfdsLen + writefdsLen);
    CAPTURE_ERRNO_AND_RETURN(-1, (int32_t) result);
}

int32_t call_poll(int32_t fd, int32_t writing, int64_t timeoutSec, int64_t timeoutUsec) {
#ifdef _WIN32
    // for windows, use select() as a worse fallback
    int selected[2] = {0, 0};
    return call_select(1,
                       writing ? NULL : &fd, writing ? 0 : 1,
                       writing ? &fd : NULL, writing ? 1 : 0,
                       NULL, 0,
                       timeoutSec, timeoutUsec, &selected);
#else
    struct pollfd pollfd;
    pollfd.fd = fd;
    pollfd.events = writing ? POLLOUT : POLLIN;

    int timeout_ms;
    if (timeoutSec < 0) {
        timeout_ms = -1;
    } else if (timeoutSec > INT_MAX / 1000) {
        errno = EINVAL;
        capture_errno();
        return -1;
    } else {
        int64_t timeout_ms_64 = timeoutSec * 1000 + timeoutUsec / 1000;
        if (timeout_ms_64 > INT_MAX) {
            errno = EINVAL;
            capture_errno();
            return -1;
        }
        timeout_ms = (int)timeout_ms_64;
    }
    CAPTURE_ERRNO_AND_RETURN(-1, poll(&pollfd, 1, timeout_ms));
#endif
}

int64_t call_lseek(int32_t fd, int64_t offset, int32_t whence) {
    CAPTURE_ERRNO_AND_RETURN(-1, lseek(fd, offset, whence));
}

int32_t call_ftruncate(int32_t fd, int64_t length) {
    CAPTURE_ERRNO_AND_RETURN(-1, ftruncate(fd, length));
}

int32_t call_truncate(const char* path, int64_t length) {
    CAPTURE_ERRNO_AND_RETURN(-1, truncate(path, length));
}

int32_t call_fsync(int32_t fd) {
    CAPTURE_ERRNO_AND_RETURN(-1, fsync(fd));
}

int32_t call_flock(int32_t fd, int32_t operation) {
    CAPTURE_ERRNO_AND_RETURN(-1, flock(fd, operation));
}

int32_t call_fcntl_lock(int32_t fd, int32_t blocking, int32_t lockType, int32_t whence, int64_t start, int64_t length) {
    struct flock l;
    l.l_type = lockType;
    l.l_whence = whence;
    l.l_start = start;
    l.l_len = length;
    CAPTURE_ERRNO_AND_RETURN(-1, fcntl(fd, blocking ? F_SETLKW : F_SETLK, &l));
}

int32_t get_blocking(int32_t fd) {
    int flags = fcntl(fd, F_GETFL, 0);
    if (flags == -1) {
        capture_errno();
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
    CAPTURE_ERRNO_AND_RETURN(-1, res);
}

int32_t get_terminal_size(int32_t fd, int32_t *size) {
    struct winsize w;
    int res = ioctl(fd, TIOCGWINSZ, &w);
    if (res == 0) {
        size[0] = w.ws_col;
        size[1] = w.ws_row;
    } else {
        capture_errno();
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
    } else {
        capture_errno();
    }
    return result;
}

int32_t call_fstat(int32_t fd, int64_t *out) {
    struct stat st;
    int result = fstat(fd, &st);
    if (result == 0) {
        stat_struct_to_longs(&st, out);
    } else {
        capture_errno();
    }
    return result;
}

static void statvfs_struct_to_longs(struct statvfs *st, int64_t *out) {
    // TODO some of these use implementation-defined behaviour of unsigned -> signed conversion
    out[0] = st->f_bsize;
    out[1] = st->f_frsize;
    out[2] = st->f_blocks;
    out[3] = st->f_bfree;
    out[4] = st->f_bavail;
    out[5] = st->f_files;
    out[6] = st->f_ffree;
    out[7] = st->f_favail;
    out[8] = st->f_flag;
    out[9] = st->f_namemax;
    out[10] = st->f_fsid;
}

int32_t call_statvfs(const char *path, int64_t *out) {
    struct statvfs st;
    int result = statvfs(path, &st);
    if (result == 0) {
        statvfs_struct_to_longs(&st, out);
    } else {
        capture_errno();
    }
    return result;
}

int32_t call_fstatvfs(int32_t fd, int64_t *out) {
    struct statvfs st;
    int result = fstatvfs(fd, &st);
    if (result == 0) {
        statvfs_struct_to_longs(&st, out);
    } else {
        capture_errno();
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
    } else {
        capture_errno();
    }
    return result;
}

int32_t call_unlinkat(int32_t dirFd, const char *pathname, int32_t rmdir) {
    CAPTURE_ERRNO_AND_RETURN(-1, unlinkat(dirFd, pathname, rmdir ? AT_REMOVEDIR : 0));
}

int32_t call_linkat(int32_t oldDirFd, const char *oldPath, int32_t newDirFd, const char *newPath, int32_t flags) {
    CAPTURE_ERRNO_AND_RETURN(-1, linkat(oldDirFd, oldPath, newDirFd, newPath, flags));
}

int32_t call_symlinkat(const char *target, int32_t dirFd, const char *linkpath) {
    CAPTURE_ERRNO_AND_RETURN(-1, symlinkat(target, dirFd, linkpath));
}

int32_t call_mkdirat(int32_t dirFd, const char *pathname, int32_t mode) {
    CAPTURE_ERRNO_AND_RETURN(-1, mkdirat(dirFd, pathname, mode));
}

int32_t call_getcwd(char *buf, uint64_t size) {
    CAPTURE_ERRNO_AND_RETURN(-1, getcwd(buf, size) == NULL ? -1 : 0);
}

int32_t call_chdir(const char *path) {
    CAPTURE_ERRNO_AND_RETURN(-1, chdir(path));
}

int32_t call_fchdir(int32_t fd) {
    CAPTURE_ERRNO_AND_RETURN(-1, fchdir(fd));
}

int32_t call_fchown(int32_t fd, int64_t owner, int64_t group) {
    CAPTURE_ERRNO_AND_RETURN(-1, fchown(fd, owner, group));
}

int32_t call_fchownat(int32_t dirfd, const char *pathname, int64_t owner, int64_t group, int32_t followSymlinks) {
    CAPTURE_ERRNO_AND_RETURN(-1, fchownat(dirfd, pathname, owner, group, followSymlinks ? 0 : AT_SYMLINK_NOFOLLOW));
}

int32_t call_isatty(int32_t fd) {
    return isatty(fd);
}

intptr_t call_opendir(const char *name) {
    CAPTURE_ERRNO_AND_RETURN(0, (intptr_t) opendir(name));
}

intptr_t call_fdopendir(int32_t fd) {
    CAPTURE_ERRNO_AND_RETURN(0, (intptr_t) fdopendir(fd));
}

int32_t call_closedir(intptr_t dirp) {
    CAPTURE_ERRNO_AND_RETURN(-1, closedir((DIR *) dirp));
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
    if (errno != 0) {
        capture_errno();
        return -1;
    }
    return 0;
}

void call_rewinddir(intptr_t dirp) {
    rewinddir((DIR *) dirp);
}

#ifdef __gnu_linux__
int32_t call_utimensat(int32_t dirFd, const char *path, int64_t *timespec, int32_t followSymlinks) {
    if (!timespec) {
        CAPTURE_ERRNO_AND_RETURN(-1, utimensat(dirFd, path, NULL, followSymlinks ? 0 : AT_SYMLINK_NOFOLLOW));
    } else {
        struct timespec times[2];
        times[0].tv_sec = timespec[0];
        times[0].tv_nsec = timespec[1];
        times[1].tv_sec = timespec[2];
        times[1].tv_nsec = timespec[3];
        CAPTURE_ERRNO_AND_RETURN(-1, utimensat(dirFd, path, times, followSymlinks ? 0 : AT_SYMLINK_NOFOLLOW));
    }
}

int32_t call_futimens(int32_t fd, int64_t *timespec) {
    if (!timespec) {
        CAPTURE_ERRNO_AND_RETURN(-1, futimens(fd, NULL));
    } else {
        struct timespec times[2];
        times[0].tv_sec = timespec[0];
        times[0].tv_nsec = timespec[1];
        times[1].tv_sec = timespec[2];
        times[1].tv_nsec = timespec[3];
        CAPTURE_ERRNO_AND_RETURN(-1, futimens(fd, times));
    }
}
#endif

int32_t call_futimes(int32_t fd, int64_t *timeval) {
    if (!timeval) {
        CAPTURE_ERRNO_AND_RETURN(-1, futimes(fd, NULL));
    } else {
        struct timeval times[2];
        times[0].tv_sec = timeval[0];
        times[0].tv_usec = timeval[1];
        times[1].tv_sec = timeval[2];
        times[1].tv_usec = timeval[3];
        CAPTURE_ERRNO_AND_RETURN(-1, futimes(fd, times));
    }
}

int32_t call_lutimes(const char *filename, int64_t *timeval) {
    if (!timeval) {
        CAPTURE_ERRNO_AND_RETURN(-1, lutimes(filename, NULL));
    } else {
        struct timeval times[2];
        times[0].tv_sec = timeval[0];
        times[0].tv_usec = timeval[1];
        times[1].tv_sec = timeval[2];
        times[1].tv_usec = timeval[3];
        CAPTURE_ERRNO_AND_RETURN(-1, lutimes(filename, times));
    }
}

int32_t call_utimes(const char *filename, int64_t *timeval) {
    if (!timeval) {
        CAPTURE_ERRNO_AND_RETURN(-1, utimes(filename, NULL));
    } else {
        struct timeval times[2];
        times[0].tv_sec = timeval[0];
        times[0].tv_usec = timeval[1];
        times[1].tv_sec = timeval[2];
        times[1].tv_usec = timeval[3];
        CAPTURE_ERRNO_AND_RETURN(-1, utimes(filename, times));
    }
}

int32_t call_renameat(int32_t oldDirFd, const char *oldPath, int32_t newDirFd, const char *newPath) {
    CAPTURE_ERRNO_AND_RETURN(-1, renameat(oldDirFd, oldPath, newDirFd, newPath));
}

int32_t call_replaceat(int32_t oldDirFd, const char *oldPath, int32_t newDirFd, const char *newPath) {
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
    CAPTURE_ERRNO_AND_RETURN(-1, faccessat(dirFd, path, mode, flags));
}

int32_t call_fchmodat(int32_t dirFd, const char *path, int32_t mode, int32_t followSymlinks) {
    CAPTURE_ERRNO_AND_RETURN(-1, fchmodat(dirFd, path, mode, followSymlinks ? 0 : AT_SYMLINK_NOFOLLOW));
}

int32_t call_fchmod(int32_t fd, int32_t mode) {
    CAPTURE_ERRNO_AND_RETURN(-1, fchmod(fd, mode));
}

int64_t call_readlinkat(int32_t dirFd, const char *path, char *buf, uint64_t size) {
    CAPTURE_ERRNO_AND_RETURN(-1, readlinkat(dirFd, path, buf, size));
}

int64_t call_waitpid(int64_t pid, int32_t *status, int32_t options) {
    CAPTURE_ERRNO_AND_RETURN(-1, waitpid(pid, status, options));
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
    CAPTURE_ERRNO_AND_RETURN(-1, kill(pid, signal));
}

int32_t call_raise(int32_t signal) {
    return raise(signal);
}

int32_t call_alarm(int32_t seconds) {
    return alarm(seconds);
}

static void long_array_to_itimerval(int64_t *src, struct itimerval *dst) {
    dst->it_value.tv_sec = src[0];
    dst->it_value.tv_usec = src[1];
    dst->it_interval.tv_sec = src[2];
    dst->it_interval.tv_usec = src[3];
}

static void itimerval_to_long_array(struct itimerval *src, int64_t *dst) {
    dst[0] = src->it_value.tv_sec;
    dst[1] = src->it_value.tv_usec;
    dst[2] = src->it_interval.tv_sec;
    dst[3] = src->it_interval.tv_usec;
}

int32_t call_getitimer(int32_t which, int64_t *current_value) {
    struct itimerval current;
    int32_t result = getitimer(which, &current);
    if (result == 0) {
        itimerval_to_long_array(&current, current_value);
    } else {
        capture_errno();
    }
    return result;
}

int32_t call_setitimer(int32_t which, int64_t *new_value, int64_t *old_value) {
    struct itimerval new_timer;
    struct itimerval old_timer;
    long_array_to_itimerval(new_value, &new_timer);
    int32_t result = setitimer(which, &new_timer, &old_timer);
    if (result == 0) {
        itimerval_to_long_array(&old_timer, old_value);
    } else {
        capture_errno();
    }
    return result;
}

int32_t signal_self(int32_t signal) {
    switch (signal) {
        case SIGABRT:
            abort();
            break;
        case SIGSEGV:
            return signal_self_segv();
        default:
            errno = EINVAL;
            capture_errno();
            return -1;
    }
    _exit(128 + signal);
}

int32_t call_killpg(int64_t pgid, int32_t signal) {
    CAPTURE_ERRNO_AND_RETURN(-1, killpg(pgid, signal));
}

int64_t call_getuid(void) {
    return getuid();
}

int64_t call_geteuid(void) {
    return geteuid();
}

int64_t call_getgid(void) {
    return getgid();
}

int64_t call_getegid(void) {
    return getegid();
}

int64_t call_getppid(void) {
    return getppid();
}

int64_t call_getpgid(int64_t pid) {
    CAPTURE_ERRNO_AND_RETURN(-1, getpgid(pid));
}

int32_t call_setpgid(int64_t pid, int64_t pgid) {
	CAPTURE_ERRNO_AND_RETURN(-1, setpgid(pid, pgid));
}

int64_t call_getpgrp(void) {
    return getpgrp();
}

int64_t call_getsid(int64_t pid) {
    CAPTURE_ERRNO_AND_RETURN(-1, getsid(pid));
}

int64_t call_setsid() {
    CAPTURE_ERRNO_AND_RETURN(-1, setsid());
}

int32_t call_getgroups(int64_t size, int64_t* out) {
    if (size > 0) {
        // gid_t can be different types, we need to copy the results
        gid_t* tmp = calloc(size, sizeof(gid_t));
        if (!tmp) {
            return -1;
        }
        int32_t res = getgroups(size, tmp);
        for (int64_t i = 0; i < size; i++) {
            out[i] = tmp[i];
        }
        free(tmp);
        CAPTURE_ERRNO_AND_RETURN(-1, res);
    } else {
        CAPTURE_ERRNO_AND_RETURN(-1, getgroups(size, NULL));
    }
}

int32_t call_getrusage(int32_t who, uint64_t* out) {
#ifndef _WIN32
    struct rusage ru;
    int result = getrusage(who, &ru);
    if (result == -1) {
        CAPTURE_ERRNO_AND_RETURN(-1, result);
    }
    int offset = 0;
    // POSIX prescribes only ru_utime and ru_stime members, macOS and Linux
    // have (at least) all those below
# define COPYDOUBLE(sec, usec) do { \
        double value = (double)(sec) + ((double)(usec) / 1000000.0); \
        memcpy(&out[offset++], &value, sizeof(value)); \
    } while (0)
# define COPYLONG(v) out[offset++] = (uint64_t)(int64_t)(v)
    COPYDOUBLE(ru.ru_utime.tv_sec, ru.ru_utime.tv_usec);
    COPYDOUBLE(ru.ru_stime.tv_sec, ru.ru_stime.tv_usec);
    COPYLONG(ru.ru_maxrss);
    COPYLONG(ru.ru_ixrss);
    COPYLONG(ru.ru_idrss);
    COPYLONG(ru.ru_isrss);
    COPYLONG(ru.ru_minflt);
    COPYLONG(ru.ru_majflt);
    COPYLONG(ru.ru_nswap);
    COPYLONG(ru.ru_inblock);
    COPYLONG(ru.ru_oublock);
    COPYLONG(ru.ru_msgsnd);
    COPYLONG(ru.ru_msgrcv);
    COPYLONG(ru.ru_nsignals);
    COPYLONG(ru.ru_nvcsw);
    COPYLONG(ru.ru_nivcsw);
    CAPTURE_ERRNO_AND_RETURN(-1, 0);
# undef COPYLONG
# undef COPYDOUBLE
#else
    errno = ENOSYS;
    capture_errno();
#endif
}

int32_t call_openpty(int32_t *outvars) {
    CAPTURE_ERRNO_AND_RETURN(-1, openpty(outvars, outvars + 1, NULL, NULL, NULL));
}

int32_t call_ctermid(char *buf) {
    return ctermid(buf) == NULL ? -1 : 0;
}

int32_t call_setenv(char *name, char *value, int overwrite) {
    CAPTURE_ERRNO_AND_RETURN(-1, setenv(name, value, overwrite));
}

int32_t call_unsetenv(char *name) {
    CAPTURE_ERRNO_AND_RETURN(-1, unsetenv(name));
}

// See comment in NativePosixSupport.execv() for the description of arguments
void call_execv(char *data, int64_t *offsets, int32_t offsetsLen) {
    // We reuse the memory allocated for offsets to avoid the need to allocate and reliably free another array
    char **strings = (char **) offsets;
    for (int32_t i = 0; i < offsetsLen; ++i) {
        strings[i] = offsets[i] == -1 ? NULL : data + offsets[i];
    }

    char *pathname = strings[0];
    char **argv = strings + 1;
    execv(pathname, argv);
    capture_errno();
}

int32_t call_system(const char *pathname) {
    return system(pathname);
}

int64_t call_mmap(int64_t length, int32_t prot, int32_t flags, int32_t fd, int64_t offset, char *tagname) {
    (void) tagname;
    void *result = mmap(NULL, length, prot, flags, fd, offset);
    if (result == MAP_FAILED) {
        capture_errno();
        return 0;
    }
    return (int64_t) result;
}

int32_t call_munmap(int64_t address, int64_t length) {
    CAPTURE_ERRNO_AND_RETURN(-1, munmap((void *) address, length));
}

void call_msync(int64_t address, int64_t offset, int64_t length) {
    // TODO: can be generalized to also accept different flags,
    // but MS_SYNC and such seem to be defined to different values across systems
    msync(((int8_t *) address) + offset, length, MS_SYNC);
}

int32_t call_socket(int32_t family, int32_t type, int32_t protocol) {
    CAPTURE_ERRNO_AND_RETURN(-1, socket(family, type, protocol));
}

// On Java side, socket addresses are stored in a Java byte[] (here represented by a int8_t *).
// Since there are no guarantees about the alignment of this pointer, we cannot simply cast it
// to (struct sockaddr *), instead we do a copy. This shouldn't be a big deal since it is
// just 16/28 bytes (for AF_INET/AF_INET6 respectively).

int32_t call_accept(int32_t sockfd, int8_t *addr, int32_t *addr_len) {
    struct sockaddr_storage sa;
    socklen_t l = sizeof(sa);
    int res = accept(sockfd, (struct sockaddr *) &sa, &l);
    if (res >= 0) {
        assert(l <= sizeof(sockaddr_storage));      // l is small enough to be representable by int32_t...
        *addr_len = (int32_t)l;                     // ...so this unsigned->signed conversion is well defined
        memcpy(addr, &sa, l);
    }
    CAPTURE_ERRNO_AND_RETURN(-1, res);
}

int32_t call_bind(int32_t sockfd, int8_t *addr, int32_t addr_len) {
    struct sockaddr_storage sa;
    memcpy(&sa, addr, addr_len);
    CAPTURE_ERRNO_AND_RETURN(-1, bind(sockfd, (struct sockaddr *) &sa, addr_len));
}

int32_t call_connect(int32_t sockfd, int8_t *addr, int32_t addr_len) {
    struct sockaddr_storage sa;
    memcpy(&sa, addr, addr_len);
    CAPTURE_ERRNO_AND_RETURN(-1, connect(sockfd, (struct sockaddr *) &sa, addr_len));
}

int32_t call_listen(int32_t sockfd, int32_t backlog) {
    CAPTURE_ERRNO_AND_RETURN(-1, listen(sockfd, backlog));
}

int32_t call_getpeername(int32_t sockfd, int8_t *addr, int32_t *addr_len) {
    struct sockaddr_storage sa;
    socklen_t l = sizeof(sa);
    int res = getpeername(sockfd, (struct sockaddr *) &sa, &l);
    if (res != -1) {
        assert(l <= sizeof(sockaddr_storage));      // l is small enough to be representable by int32_t...
        *addr_len = (int32_t)l;                     // ...so this unsigned->signed conversion is well defined
        memcpy(addr, &sa, l);
    } else {
        capture_errno();
    }
    return res;
}

int32_t call_getsockname(int32_t sockfd, int8_t *addr, int32_t *addr_len) {
    struct sockaddr_storage sa;
    socklen_t l = sizeof(sa);
    int res = getsockname(sockfd, (struct sockaddr *) &sa, &l);
    if (res != -1) {
        assert(l <= sizeof(sockaddr_storage));      // l is small enough to be representable by int32_t...
        *addr_len = (int32_t)l;                     // ...so this unsigned->signed conversion is well defined
        memcpy(addr, &sa, l);
    } else {
        capture_errno();
    }
    return res;
}

//TODO len should be size_t, retval should be ssize_t
int32_t call_send(int32_t sockfd, void *buf, int32_t len, int32_t flags) {
    ssize_t res = send(sockfd, buf, len, flags);
    assert (res == (int32_t) res);
    CAPTURE_ERRNO_AND_RETURN(-1, res);
}

int32_t call_sendto(int32_t sockfd, void *buf, int32_t offset, int32_t len, int32_t flags, int8_t *addr, int32_t addr_len) {
    struct sockaddr_storage sa;
    memcpy(&sa, addr, addr_len);
    CAPTURE_ERRNO_AND_RETURN(-1, sendto(sockfd, buf + offset, len, flags, (struct sockaddr *) &sa, addr_len));
}

int32_t call_recv(int32_t sockfd, void *buf, int32_t len, int32_t flags) {
    CAPTURE_ERRNO_AND_RETURN(-1, recv(sockfd, buf, len, flags));
}

int32_t call_recvfrom(int32_t sockfd, void *buf, int32_t offset, int32_t len, int32_t flags, int8_t *src_addr, int32_t *addr_len) {
    struct sockaddr_storage sa;
    socklen_t l = sizeof(sa);
    ssize_t res = recvfrom(sockfd, buf + offset, len, flags, (struct sockaddr *) &sa, &l);
    if (res != -1) {
        assert(l <= sizeof(sockaddr_storage));      // l is small enough to be representable by int32_t...
        *addr_len = (int32_t)l;                     // ...so this unsigned->signed conversion is well defined
        memcpy(src_addr, &sa, l);
    } else {
        capture_errno();
    }
    assert (res == (int32_t) res);
    return (int32_t) res;
}

int32_t call_shutdown(int32_t sockfd, int32_t how) {
    CAPTURE_ERRNO_AND_RETURN(-1, shutdown(sockfd, how));
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
        capture_errno();
        return -1;
    }
    int res = getsockopt(sockfd, level, optname, alignedBuf, &len);
    if (res == 0) {
        *bufLen = len;
        memcpy(buf, alignedBuf, len);
    } else {
        capture_errno();
    }
    return res;
}

int32_t call_setsockopt(int32_t sockfd, int32_t level, int32_t optname, void *buf, int32_t bufLen) {
    // see comments in call_getsockopt
    char alignedBuf[MAX_SOCKOPT_LEN] __attribute__ ((aligned));
    if (bufLen > sizeof(alignedBuf)) {
        errno = ENOMEM;
        capture_errno();
        return -1;
    }
    memcpy(alignedBuf, buf, bufLen);
    CAPTURE_ERRNO_AND_RETURN(-1, setsockopt(sockfd, level, optname, alignedBuf, bufLen));
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
    CAPTURE_ERRNO_AND_RETURN(-1, inet_pton(family, src, dst));
}

int32_t call_inet_ntop(int32_t family, void *src, char *dst, int32_t dstSize) {
    const char *r = inet_ntop(family, src, dst, dstSize);
    CAPTURE_ERRNO_AND_RETURN(-1, r == NULL ? -1 : 0);
}

int32_t call_gethostname(char *buf, int64_t bufLen) {
    CAPTURE_ERRNO_AND_RETURN(-1, gethostname(buf, bufLen));
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
    // see NativePosixSupport.AddrInfo for description of the way data is transferred
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

sem_t* call_sem_open(const char *name, int32_t openFlags, int32_t mode, int32_t value) {
    sem_t* result = sem_open(name, openFlags, mode, value);
    if (result == SEM_FAILED) {
        capture_errno();
    }
    return result;
}

int32_t call_sem_close(sem_t* handle) {
    CAPTURE_ERRNO_AND_RETURN(-1, sem_close(handle));
}

int32_t call_shm_open(const char *name, int32_t openFlags, int32_t mode) {
    CAPTURE_ERRNO_AND_RETURN(-1, shm_open(name, openFlags, mode));
}

int32_t call_shm_unlink(const char *name) {
    CAPTURE_ERRNO_AND_RETURN(-1, shm_unlink(name));
}

int32_t call_sem_unlink(const char *name) {
    CAPTURE_ERRNO_AND_RETURN(-1, sem_unlink(name));
}

#ifdef __linux__
int32_t call_sem_getvalue(sem_t* handle, int32_t *value) {
    int valueInt;
    int res = sem_getvalue(handle, &valueInt);
    if (res == 0) {
        *value = valueInt;
    } else {
        capture_errno();
    }
    return res;
}
#endif

int32_t call_sem_post(sem_t* handle) {
    CAPTURE_ERRNO_AND_RETURN(-1, sem_post(handle));
}

int32_t call_sem_wait(sem_t* handle) {
    CAPTURE_ERRNO_AND_RETURN(-1, sem_wait(handle));
}

int32_t call_sem_trywait(sem_t* handle) {
    CAPTURE_ERRNO_AND_RETURN(-1, sem_trywait(handle));
}

#ifdef __linux__
int32_t call_sem_timedwait(sem_t* handle, int64_t deadlineNs) {
    const int64_t nsInSec = 1000 * 1000 * 1000;
    struct timespec deadline = {deadlineNs / nsInSec, deadlineNs % nsInSec};
    CAPTURE_ERRNO_AND_RETURN(-1, sem_timedwait(handle, &deadline));
}
#endif

int64_t get_sysconf_getpw_r_size_max(void) {
    return sysconf(_SC_GETPW_R_SIZE_MAX);
}

static void pwd_to_out(struct passwd* p, char* buffer, uint64_t *output) {
    output[0] = p->pw_name - buffer;
    output[1] = p->pw_uid;
    output[2] = p->pw_gid;
    output[3] = p->pw_dir - buffer;
    output[4] = p->pw_shell - buffer;
}

// The caller must provide a buffer that will be filled with '\0' terminated strings.
// The "output" array contains in this order:
//   0) offset of the start of 'name' within the provided buffer ('\0' terminated string)
//   1) uid
//   2) gid
//   3) offset of the start of 'dir' within the provided buffer
//   4) offset of the start of 'shell' within the provided buffer
// On top of error codes from the underlying POSIX call, this may also return -1 when the entry was not found.
int32_t call_getpwuid_r(uint64_t uid, char *buffer, int32_t bufferSize, uint64_t *output) {
    struct passwd pwd;
    struct passwd *p;
    int status = getpwuid_r(uid, &pwd, buffer, bufferSize, &p);
    if (status != 0) {
        return status;
    }
    if (p == NULL) {
        return -1;
    }
    pwd_to_out(p, buffer, output);
    return 0;
}

// See the docs of call_getpwuid_r above
int32_t call_getpwname_r(const char *name, char *buffer, int32_t bufferSize, uint64_t *output) {
    struct passwd pwd;
    struct passwd *p;
    int status = getpwnam_r(name, &pwd, buffer, bufferSize, &p);
    if (status != 0) {
        return status;
    }
    if (p == NULL) {
        return -1;
    }
    pwd_to_out(p, buffer, output);
    return 0;
}

// Following 3 functions are not thread safe:

void call_setpwent(void) {
    setpwent();
}

void call_endpwent(void) {
    endpwent();
}

struct passwd *call_getpwent(int64_t *bufferSize) {
    struct passwd *p = getpwent();
    if (p != NULL) {
        // the +3 is for terminating '\0'
        *bufferSize = strlen(p->pw_name) + strlen(p->pw_dir) + strlen(p->pw_shell) + 3;
    }
    // always capture errno because NULL result may also be a valid result
    capture_errno();
    return p;
}

int32_t get_getpwent_data(struct passwd *p, char *buffer, int32_t bufferSize, uint64_t *output) {
    size_t nameLen = strlen(p->pw_name);
    size_t dirLen = strlen(p->pw_dir);
    size_t dirOffset = nameLen + 1; // +1 for terminating '\0'
    size_t shellOffset = nameLen + dirLen + 2;

    if (shellOffset + strlen(p->pw_shell) + 1 >= bufferSize) {
        // should not happen if the caller correctly used the size given by call_getpwent
        return -1;
    }

    strncpy(buffer, p->pw_name, bufferSize);
    strncpy(buffer + dirOffset, p->pw_dir, bufferSize - dirOffset);
    strncpy(buffer + shellOffset, p->pw_shell, bufferSize - shellOffset);

    output[0] = 0; // name offset
    output[1] = p->pw_uid;
    output[2] = p->pw_gid;
    output[3] = dirOffset;
    output[4] = shellOffset;
    return 0;
}

int32_t call_ioctl_bytes(int32_t fd, uint64_t request, char* buffer) {
    CAPTURE_ERRNO_AND_RETURN(-1, ioctl(fd, request, buffer));
}

int32_t call_ioctl_int(int32_t fd, uint64_t request, int32_t arg) {
    CAPTURE_ERRNO_AND_RETURN(-1, ioctl(fd, request, (int)arg));
}

int64_t call_sysconf(int32_t name) {
    errno = 0;
    int64_t result = sysconf(name);
    if (result == -1 && errno != 0) {
        capture_errno();
        // Private sentinel: sysconf itself may return -1 without an error.
        return INT64_MIN;
    }
    return result;
}

int32_t get_errno() {
    return errno_capture;
}

void call_initialize(void) {
}

int32_t get_winerror() {
    return 0;
}

int32_t get_wsaerror() {
    return 0;
}

int32_t get_error_source() {
    return ERROR_CAPTURE_ERRNO;
}
#define unix_or_0(x) x

// start generated
int32_t init_constants(int64_t* out, int32_t len) {
    if (len != 33)
        return -1;
    out[0] = sizeof(struct sockaddr);
    out[1] = sizeof(((struct sockaddr*)0)->sa_family);
    out[2] = offsetof(struct sockaddr, sa_family);
    out[3] = sizeof(struct sockaddr_storage);
    out[4] = sizeof(struct sockaddr_in);
    out[5] = sizeof(((struct sockaddr_in*)0)->sin_family);
    out[6] = offsetof(struct sockaddr_in, sin_family);
    out[7] = sizeof(((struct sockaddr_in*)0)->sin_port);
    out[8] = offsetof(struct sockaddr_in, sin_port);
    out[9] = sizeof(((struct sockaddr_in*)0)->sin_addr);
    out[10] = offsetof(struct sockaddr_in, sin_addr);
    out[11] = sizeof(struct sockaddr_in6);
    out[12] = sizeof(((struct sockaddr_in6*)0)->sin6_family);
    out[13] = offsetof(struct sockaddr_in6, sin6_family);
    out[14] = sizeof(((struct sockaddr_in6*)0)->sin6_port);
    out[15] = offsetof(struct sockaddr_in6, sin6_port);
    out[16] = sizeof(((struct sockaddr_in6*)0)->sin6_flowinfo);
    out[17] = offsetof(struct sockaddr_in6, sin6_flowinfo);
    out[18] = sizeof(((struct sockaddr_in6*)0)->sin6_addr);
    out[19] = offsetof(struct sockaddr_in6, sin6_addr);
    out[20] = sizeof(((struct sockaddr_in6*)0)->sin6_scope_id);
    out[21] = offsetof(struct sockaddr_in6, sin6_scope_id);
    out[22] = sizeof(struct in_addr);
    out[23] = sizeof(((struct in_addr*)0)->s_addr);
    out[24] = offsetof(struct in_addr, s_addr);
    out[25] = sizeof(struct in6_addr);
    out[26] = sizeof(((struct in6_addr*)0)->s6_addr);
    out[27] = offsetof(struct in6_addr, s6_addr);
    out[28] = unix_or_0(sizeof(struct sockaddr_un));
    out[29] = unix_or_0(sizeof(((struct sockaddr_un*)0)->sun_family));
    out[30] = unix_or_0(offsetof(struct sockaddr_un, sun_family));
    out[31] = unix_or_0(sizeof(((struct sockaddr_un*)0)->sun_path));
    out[32] = unix_or_0(offsetof(struct sockaddr_un, sun_path));
    return 0;
}
// end generated

#endif
