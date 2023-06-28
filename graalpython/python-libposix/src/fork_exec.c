/* Copyright (c) 2021, 2023, Oracle and/or its affiliates.
 * Copyright (C) 1996-2020 Python Software Foundation
 *
 * Licensed under the PYTHON SOFTWARE FOUNDATION LICENSE VERSION 2
 */
#if defined(__gnu_linux__) && !defined(_GNU_SOURCE)
#define _GNU_SOURCE 1
#endif

#include <unistd.h>
#include <fcntl.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <dirent.h>
#include <errno.h>
#include <assert.h>

// These definitions emulate CPython's equivalents so that the copy&pasted code below works without too many changes
#define HAVE_DIRFD 1
#define HAVE_SETSID 1

int32_t set_inheritable(int32_t fd, int32_t inheritable);

// TODO JDK also handles EINTR for dup2 calls
// TODO neither CPython nor JDK handle EINTR returned from close(), see NOTES on man page for close(2)

static const char *Py_hexdigits = "0123456789abcdef";

static int
_Py_set_inheritable_async_safe(int fd, int inheritable, int *atomic_flag_works)
{
    assert(atomic_flag_works == NULL);
    return set_inheritable(fd, inheritable);
}

static void
_Py_write_noraise(int fd, const void *buf, size_t count)
{
    while (write(fd, buf, count) < 0 && errno == EINTR)
        ;
}

static void
_Py_RestoreSignals()
{
}


// The following code is taken directly from CPython's _posixsubprocess.c with some minor changes:
//  - py_fds_to_keep is not a PyObject, but an int array
//  - there's no preexec_fn

#if defined(__FreeBSD__) || (defined(__APPLE__) && defined(__MACH__))
# define FD_DIR "/dev/fd"
#else
# define FD_DIR "/proc/self/fd"
#endif

#define POSIX_CALL(call)   do { if ((call) == -1) goto error; } while (0)

/* Convert ASCII to a positive int, no libc call. no overflow. -1 on error. */
static int
_pos_int_from_ascii(const char *name)
{
    int num = 0;
    while (*name >= '0' && *name <= '9') {
        num = num * 10 + (*name - '0');
        ++name;
    }
    if (*name)
        return -1;  /* Non digit found, not a number. */
    return num;
}

/* Is fd found in the sorted Python Sequence? */
static int
_is_fd_in_sorted_fd_sequence(int fd, int *fd_sequence, ssize_t fd_sequence_len)
{
    /* Binary search. */
    ssize_t search_min = 0;
    ssize_t search_max = fd_sequence_len - 1;
    if (search_max < 0)
        return 0;
    do {
        long middle = (search_min + search_max) / 2;
        int middle_fd = fd_sequence[middle];
        if (fd == middle_fd)
            return 1;
        if (fd > middle_fd)
            search_min = middle + 1;
        else
            search_max = middle - 1;
    } while (search_min <= search_max);
    return 0;
}

static int
make_inheritable(int *fds_to_keep, ssize_t len, int errpipe_write)
{
    ssize_t i;

    for (i = 0; i < len; ++i) {
        int fd = fds_to_keep[i];
        assert(0 <= fd && fd <= INT_MAX);
        if (fd == errpipe_write) {
            /* errpipe_write is part of py_fds_to_keep. It must be closed at
               exec(), but kept open in the child process until exec() is
               called. */
            continue;
        }
        if (_Py_set_inheritable_async_safe(fd, 1, NULL) < 0)
            return -1;
    }
    return 0;
}


/* Get the maximum file descriptor that could be opened by this process.
 * This function is async signal safe for use between fork() and exec().
 */
static long
safe_get_max_fd(void)
{
    long local_max_fd;
#if defined(__NetBSD__)
    local_max_fd = fcntl(0, F_MAXFD);
    if (local_max_fd >= 0)
        return local_max_fd;
#endif
#if defined(HAVE_SYS_RESOURCE_H) && defined(__OpenBSD__)
    struct rlimit rl;
    /* Not on the POSIX async signal safe functions list but likely
     * safe.  TODO - Someone should audit OpenBSD to make sure. */
    if (getrlimit(RLIMIT_NOFILE, &rl) >= 0)
        return (long) rl.rlim_max;
#endif
#ifdef _SC_OPEN_MAX
    local_max_fd = sysconf(_SC_OPEN_MAX);
    if (local_max_fd == -1)
#endif
        local_max_fd = 256;  /* Matches legacy Lib/subprocess.py behavior. */
    return local_max_fd;
}


/* Close all file descriptors in the range from start_fd and higher
 * except for those in py_fds_to_keep.  If the range defined by
 * [start_fd, safe_get_max_fd()) is large this will take a long
 * time as it calls close() on EVERY possible fd.
 *
 * It isn't possible to know for sure what the max fd to go up to
 * is for processes with the capability of raising their maximum.
 */
static void
_close_fds_by_brute_force(long start_fd, int *fds_to_keep, ssize_t num_fds_to_keep)
{
    long end_fd = safe_get_max_fd();
    ssize_t keep_seq_idx;
    int fd_num;
    /* As py_fds_to_keep is sorted we can loop through the list closing
     * fds in between any in the keep list falling within our range. */
    for (keep_seq_idx = 0; keep_seq_idx < num_fds_to_keep; ++keep_seq_idx) {
        int keep_fd = fds_to_keep[keep_seq_idx];
        if (keep_fd < start_fd)
            continue;
        for (fd_num = start_fd; fd_num < keep_fd; ++fd_num) {
            close(fd_num);
        }
        start_fd = keep_fd + 1;
    }
    if (start_fd <= end_fd) {
        for (fd_num = start_fd; fd_num < end_fd; ++fd_num) {
            close(fd_num);
        }
    }
}

/* Close all open file descriptors from start_fd and higher.
 * Do not close any in the sorted py_fds_to_keep tuple.
 *
 * This function violates the strict use of async signal safe functions. :(
 * It calls opendir(), readdir() and closedir().  Of these, the one most
 * likely to ever cause a problem is opendir() as it performs an internal
 * malloc().  Practically this should not be a problem.  The Java VM makes the
 * same calls between fork and exec in its own UNIXProcess_md.c implementation.
 *
 * readdir_r() is not used because it provides no benefit.  It is typically
 * implemented as readdir() followed by memcpy().  See also:
 *   http://womble.decadent.org.uk/readdir_r-advisory.html
 */
static void
_close_open_fds_maybe_unsafe(long start_fd, int* fds_to_keep, ssize_t fds_to_keep_len)
{
    DIR *proc_fd_dir;
#ifndef HAVE_DIRFD
    while (_is_fd_in_sorted_fd_sequence(start_fd, fds_to_keep, fds_to_keep_len)) {
        ++start_fd;
    }
    /* Close our lowest fd before we call opendir so that it is likely to
     * reuse that fd otherwise we might close opendir's file descriptor in
     * our loop.  This trick assumes that fd's are allocated on a lowest
     * available basis. */
    close(start_fd);
    ++start_fd;
#endif

#if defined(__FreeBSD__)
    if (!_is_fdescfs_mounted_on_dev_fd())
        proc_fd_dir = NULL;
    else
#endif
        proc_fd_dir = opendir(FD_DIR);
    if (!proc_fd_dir) {
        /* No way to get a list of open fds. */
        _close_fds_by_brute_force(start_fd, fds_to_keep, fds_to_keep_len);
    } else {
        struct dirent *dir_entry;
#ifdef HAVE_DIRFD
        int fd_used_by_opendir = dirfd(proc_fd_dir);
#else
        int fd_used_by_opendir = start_fd - 1;
#endif
        errno = 0;
        while ((dir_entry = readdir(proc_fd_dir))) {
            int fd;
            if ((fd = _pos_int_from_ascii(dir_entry->d_name)) < 0)
                continue;  /* Not a number. */
            if (fd != fd_used_by_opendir && fd >= start_fd &&
                !_is_fd_in_sorted_fd_sequence(fd, fds_to_keep, fds_to_keep_len)) {
                close(fd);
            }
            errno = 0;
        }
        if (errno) {
            /* readdir error, revert behavior. Highly Unlikely. */
            _close_fds_by_brute_force(start_fd, fds_to_keep, fds_to_keep_len);
        }
        closedir(proc_fd_dir);
    }
}

#define _close_open_fds _close_open_fds_maybe_unsafe

/*
 * This function is code executed in the child process immediately after fork
 * to set things up and call exec().
 *
 * All of the code in this function must only use async-signal-safe functions,
 * listed at `man 7 signal` or
 * http://www.opengroup.org/onlinepubs/009695399/functions/xsh_chap02_04.html.
 *
 * This restriction is documented at
 * http://www.opengroup.org/onlinepubs/009695399/functions/fork.html.
 */
static void
child_exec(char *const exec_array[],
           char *const argv[],
           char *const envp[],
           const char *cwd,
           int p2cread, int p2cwrite,
           int c2pread, int c2pwrite,
           int errread, int errwrite,
           int errpipe_read, int errpipe_write,
           int close_fds, int restore_signals,
           int call_setsid,
           int *fds_to_keep,
           ssize_t fds_to_keep_len)
{
    int i, saved_errno, reached_preexec = 0;
    /* Buffer large enough to hold a hex integer.  We can't malloc. */
    char hex_errno[sizeof(saved_errno)*2+1];

    if (make_inheritable(fds_to_keep, fds_to_keep_len, errpipe_write) < 0)
        goto error;

    /* Close parent's pipe ends. */
    if (p2cwrite != -1)
        POSIX_CALL(close(p2cwrite));
    if (c2pread != -1)
        POSIX_CALL(close(c2pread));
    if (errread != -1)
        POSIX_CALL(close(errread));
    POSIX_CALL(close(errpipe_read));

    /* When duping fds, if there arises a situation where one of the fds is
       either 0, 1 or 2, it is possible that it is overwritten (#12607). */
    if (c2pwrite == 0) {
        POSIX_CALL(c2pwrite = dup(c2pwrite));
        /* issue32270 */
        if (_Py_set_inheritable_async_safe(c2pwrite, 0, NULL) < 0) {
            goto error;
        }
    }
    while (errwrite == 0 || errwrite == 1) {
        POSIX_CALL(errwrite = dup(errwrite));
        /* issue32270 */
        if (_Py_set_inheritable_async_safe(errwrite, 0, NULL) < 0) {
            goto error;
        }
    }

    /* Dup fds for child.
       dup2() removes the CLOEXEC flag but we must do it ourselves if dup2()
       would be a no-op (issue #10806). */
    if (p2cread == 0) {
        if (_Py_set_inheritable_async_safe(p2cread, 1, NULL) < 0)
            goto error;
    }
    else if (p2cread != -1)
        POSIX_CALL(dup2(p2cread, 0));  /* stdin */

    if (c2pwrite == 1) {
        if (_Py_set_inheritable_async_safe(c2pwrite, 1, NULL) < 0)
            goto error;
    }
    else if (c2pwrite != -1)
        POSIX_CALL(dup2(c2pwrite, 1));  /* stdout */

    if (errwrite == 2) {
        if (_Py_set_inheritable_async_safe(errwrite, 1, NULL) < 0)
            goto error;
    }
    else if (errwrite != -1)
        POSIX_CALL(dup2(errwrite, 2));  /* stderr */

    /* We no longer manually close p2cread, c2pwrite, and errwrite here as
     * _close_open_fds takes care when it is not already non-inheritable. */

    if (cwd)
        POSIX_CALL(chdir(cwd));

    if (restore_signals)
        _Py_RestoreSignals();

#ifdef HAVE_SETSID
    if (call_setsid)
        POSIX_CALL(setsid());
#endif

    reached_preexec = 1;

    /* close FDs after executing preexec_fn, which might open FDs */
    if (close_fds) {
        /* TODO HP-UX could use pstat_getproc() if anyone cares about it. */
        _close_open_fds(3, fds_to_keep, fds_to_keep_len);
    }

    /* This loop matches the Lib/os.py _execvpe()'s PATH search when */
    /* given the executable_list generated by Lib/subprocess.py.     */
    saved_errno = 0;
    for (i = 0; exec_array[i] != NULL; ++i) {
        const char *executable = exec_array[i];
        if (envp) {
            execve(executable, argv, envp);
        } else {
            execv(executable, argv);
        }
        if (errno != ENOENT && errno != ENOTDIR && saved_errno == 0) {
            saved_errno = errno;
        }
    }
    /* Report the first exec error, not the last. */
    if (saved_errno)
        errno = saved_errno;

error:
    saved_errno = errno;
    /* Report the posix error to our parent process. */
    /* We ignore all write() return values as the total size of our writes is
       less than PIPEBUF and we cannot do anything about an error anyways.
       Use _Py_write_noraise() to retry write() if it is interrupted by a
       signal (fails with EINTR). */
    if (saved_errno) {
        char *cur;
        _Py_write_noraise(errpipe_write, "OSError:", 8);
        cur = hex_errno + sizeof(hex_errno);
        while (saved_errno != 0 && cur != hex_errno) {
            *--cur = Py_hexdigits[saved_errno % 16];
            saved_errno /= 16;
        }
        _Py_write_noraise(errpipe_write, cur, hex_errno + sizeof(hex_errno) - cur);
        _Py_write_noraise(errpipe_write, ":", 1);
        if (!reached_preexec) {
            /* Indicate to the parent that the error happened before exec(). */
            _Py_write_noraise(errpipe_write, "noexec", 6);
        }
        /* We can't call strerror(saved_errno).  It is not async signal safe.
         * The parent process will look the error message up. */
    } else {
        _Py_write_noraise(errpipe_write, "SubprocessError:0:", 18);
    }
}


/*
 * data, offsets, offsetsLen, argsPos, envPos, cwdPos - see comment in NFiPosixSupport.forkExec()
 * stdinRdFd - read end of the pipe for the child's stdin - closed by parent, dupped to fd 0 by child
 * stdinWrFd - write end of the pipe for the child's stdin - closed by child, written to by parent
 * stdoutRdFd - read end of the pipe for the child's stdout - closed by child, read from by parent
 * stdoutWrFd - write end of the pipe for the child's stdout - closed by parent, dupped to fd 1 by child
 * stderrRdFd - read end of the pipe for the child's stderr - closed by child, read from by parent
 * stderrWrFd - write end of the pipe for the child's stderr - closed by parent, dupped to fd 2 by child
 * errPipeRdFd - read end of the pipe for reporting exec errors to parent - closed by child, read from by parent
 * errPipeWrFd - write end of the pipe for reporting exec errors to parent - closed by parent, written to by child if an error occurs
                    - the child process closes this fd in any case:
                        - on success, no data is written and the fd is closed by exec() because its O_CLOEXEC is set
                        - on error, the error code is written and the fd is closed because the child process exits
 * closeFds - nonzero if all fds except 0, 1, 2 and those in fdsToKeep should be explicitly closed by the child
 *            (if nonzero, then errPipeWrFd must be in fdsToKeep)
 * restoreSignals - currently not used
 * callSetsid - if nonzero, the child calls setsid before exec()
 * fdsToKeep, fdsToKeepLen - a sorted list of fds to keep open (the child clears their O_CLOEXEC)
 */
int32_t fork_exec(
            char *data, int64_t *offsets, int32_t offsetsLen, int32_t argsPos, int32_t envPos, int32_t cwdPos,
            int32_t stdinRdFd, int32_t stdinWrFd,
            int32_t stdoutRdFd, int32_t stdoutWrFd,
            int32_t stderrRdFd, int32_t stderrWrFd,
            int32_t errPipeRdFd, int32_t errPipeWrFd,
            int32_t closeFds,
            int32_t restoreSignals,
            int32_t callSetsid,
            int32_t *fdsToKeep, int64_t fdsToKeepLen
            ) {

    // We reuse the memory allocated for offsets to avoid the need to allocate and reliably free another array
    char **strings = (char **) offsets;
    for (int32_t i = 0; i < offsetsLen; ++i) {
        strings[i] = offsets[i] == -1 ? NULL : data + offsets[i];
    }

    char **exec_list = strings;
    char **argv = strings + argsPos;
    char **envp = envPos == -1 ? NULL : strings + envPos;
    char *cwd = cwdPos == -1 ? NULL : strings[cwdPos];

    pid_t pid = fork();
    if (pid == 0) {
        child_exec(
            exec_list, argv, envp, cwd,
            stdinRdFd, stdinWrFd,
            stdoutRdFd, stdoutWrFd,
            stderrRdFd, stderrWrFd,
            errPipeRdFd, errPipeWrFd,
            closeFds,
            restoreSignals,
            callSetsid,
            fdsToKeep, fdsToKeepLen
        );
        _exit(255);
    }
    return pid;
}
