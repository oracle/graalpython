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
// skip GIL
package com.oracle.graal.python.runtime;

import static com.oracle.graal.python.annotations.NativeSimpleType.POINTER;
import static com.oracle.graal.python.annotations.NativeSimpleType.SINT32;
import static com.oracle.graal.python.annotations.NativeSimpleType.SINT64;
import static com.oracle.graal.python.annotations.NativeSimpleType.VOID;
import static com.oracle.graal.python.lib.PyUnicodeFSDecoderNode.SURROGATE_ESCAPE_TO_UTF8_TRANSCODING_ERROR_HANDLER;
import static com.oracle.graal.python.nodes.StringLiterals.T_NATIVE;
import static com.oracle.graal.python.runtime.NativePosixConstants.OFFSETOF_STRUCT_IN6_ADDR_S6_ADDR;
import static com.oracle.graal.python.runtime.NativePosixConstants.OFFSETOF_STRUCT_IN_ADDR_S_ADDR;
import static com.oracle.graal.python.runtime.NativePosixConstants.OFFSETOF_STRUCT_SOCKADDR_IN6_SIN6_ADDR;
import static com.oracle.graal.python.runtime.NativePosixConstants.OFFSETOF_STRUCT_SOCKADDR_IN6_SIN6_FLOWINFO;
import static com.oracle.graal.python.runtime.NativePosixConstants.OFFSETOF_STRUCT_SOCKADDR_IN6_SIN6_PORT;
import static com.oracle.graal.python.runtime.NativePosixConstants.OFFSETOF_STRUCT_SOCKADDR_IN6_SIN6_SCOPE_ID;
import static com.oracle.graal.python.runtime.NativePosixConstants.OFFSETOF_STRUCT_SOCKADDR_IN_SIN_ADDR;
import static com.oracle.graal.python.runtime.NativePosixConstants.OFFSETOF_STRUCT_SOCKADDR_IN_SIN_PORT;
import static com.oracle.graal.python.runtime.NativePosixConstants.OFFSETOF_STRUCT_SOCKADDR_SA_FAMILY;
import static com.oracle.graal.python.runtime.NativePosixConstants.OFFSETOF_STRUCT_SOCKADDR_UN_SUN_PATH;
import static com.oracle.graal.python.runtime.NativePosixConstants.SIZEOF_STRUCT_SOCKADDR_IN;
import static com.oracle.graal.python.runtime.NativePosixConstants.SIZEOF_STRUCT_SOCKADDR_IN6;
import static com.oracle.graal.python.runtime.NativePosixConstants.SIZEOF_STRUCT_SOCKADDR_SA_FAMILY;
import static com.oracle.graal.python.runtime.NativePosixConstants.SIZEOF_STRUCT_SOCKADDR_STORAGE;
import static com.oracle.graal.python.runtime.NativePosixConstants.SIZEOF_STRUCT_SOCKADDR_UN_SUN_PATH;
import static com.oracle.graal.python.runtime.PosixConstants.AF_INET;
import static com.oracle.graal.python.runtime.PosixConstants.AF_INET6;
import static com.oracle.graal.python.runtime.PosixConstants.AF_UNIX;
import static com.oracle.graal.python.runtime.PosixConstants.AF_UNSPEC;
import static com.oracle.graal.python.runtime.PosixConstants.HOST_NAME_MAX;
import static com.oracle.graal.python.runtime.PosixConstants.INET6_ADDRSTRLEN;
import static com.oracle.graal.python.runtime.PosixConstants.INET_ADDRSTRLEN;
import static com.oracle.graal.python.runtime.PosixConstants.L_ctermid;
import static com.oracle.graal.python.runtime.PosixConstants.NI_MAXHOST;
import static com.oracle.graal.python.runtime.PosixConstants.NI_MAXSERV;
import static com.oracle.graal.python.runtime.PosixConstants.PATH_MAX;
import static com.oracle.graal.python.runtime.PosixConstants.WNOHANG;
import static com.oracle.graal.python.runtime.PosixConstants._POSIX_HOST_NAME_MAX;
import static com.oracle.graal.python.runtime.PosixSupportLibrary.POSIX_FILENAME_SEPARATOR;
import static com.oracle.graal.python.runtime.PosixSupportLibrary.UnsupportedPosixFeatureException;
import static com.oracle.graal.python.runtime.nativeaccess.NativeMemory.NULLPTR;
import static com.oracle.graal.python.util.PythonUtils.ARRAY_ACCESSOR;
import static com.oracle.graal.python.util.PythonUtils.ARRAY_ACCESSOR_BE;
import static com.oracle.graal.python.util.PythonUtils.EMPTY_LONG_ARRAY;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.truffle.api.CompilerDirectives.shouldNotReachHere;
import static com.oracle.truffle.api.strings.TruffleString.Encoding.UTF_8;

import java.util.ArrayList;
import java.util.logging.Level;

import org.graalvm.nativeimage.ImageInfo;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.DowncallSignature;
import com.oracle.graal.python.annotations.PythonOS;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.exception.OSErrorEnum;
import com.oracle.graal.python.lib.PyUnicodeFSDecoderNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.runtime.PosixSupportLibrary.AcceptResult;
import com.oracle.graal.python.runtime.PosixSupportLibrary.AddrInfoCursor;
import com.oracle.graal.python.runtime.PosixSupportLibrary.AddrInfoCursorLibrary;
import com.oracle.graal.python.runtime.PosixSupportLibrary.Buffer;
import com.oracle.graal.python.runtime.PosixSupportLibrary.GetAddrInfoException;
import com.oracle.graal.python.runtime.PosixSupportLibrary.Inet4SockAddr;
import com.oracle.graal.python.runtime.PosixSupportLibrary.Inet6SockAddr;
import com.oracle.graal.python.runtime.PosixSupportLibrary.InvalidAddressException;
import com.oracle.graal.python.runtime.PosixSupportLibrary.InvalidUnixSocketPathException;
import com.oracle.graal.python.runtime.PosixSupportLibrary.OpenPtyResult;
import com.oracle.graal.python.runtime.PosixSupportLibrary.PosixErrnoException;
import com.oracle.graal.python.runtime.PosixSupportLibrary.PosixException;
import com.oracle.graal.python.runtime.PosixSupportLibrary.PwdResult;
import com.oracle.graal.python.runtime.PosixSupportLibrary.RecvfromResult;
import com.oracle.graal.python.runtime.PosixSupportLibrary.RusageResult;
import com.oracle.graal.python.runtime.PosixSupportLibrary.SelectResult;
import com.oracle.graal.python.runtime.PosixSupportLibrary.Timeval;
import com.oracle.graal.python.runtime.PosixSupportLibrary.UniversalSockAddr;
import com.oracle.graal.python.runtime.PosixSupportLibrary.UniversalSockAddrLibrary;
import com.oracle.graal.python.runtime.PosixSupportLibrary.UnixSockAddr;
import com.oracle.graal.python.runtime.nativeaccess.NativeLibrary;
import com.oracle.graal.python.runtime.nativeaccess.NativeLibraryLoadException;
import com.oracle.graal.python.runtime.nativeaccess.NativeMemory;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.ArrayUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.TruffleSafepoint;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

import sun.misc.Unsafe;

/**
 * Implementation that invokes the native POSIX support library through generated native-access
 * downcalls.
 */
@ExportLibrary(PosixSupportLibrary.class)
public final class NativePosixSupport extends PosixSupport {
    private static final String SUPPORTING_NATIVE_LIB_NAME = "posix";
    private static final int UNAME_BUF_LENGTH = 256;
    private static final int DIRENT_NAME_BUF_LENGTH = 256;
    private static final int PWD_OUTPUT_LEN = 5;
    private static final int PWD_BUFFER_MAX_SIZE = Integer.MAX_VALUE >> 2;
    private static final int STRERROR_BUF_LENGTH = 1024;

    private static final int MAX_READ = Integer.MAX_VALUE / 2;

    private static final TruffleLogger LOGGER = PythonLanguage.getLogger(NativePosixSupport.class);

    private static final Unsafe UNSAFE = PythonUtils.initUnsafe();

    private static final Object CRYPT_LOCK = new Object();

    abstract static class PosixNativeFunctionInvoker {
        @DowncallSignature(returnType = SINT32, argumentTypes = {POINTER, SINT32})
        abstract int init_constants(long out, int len);

        @DowncallSignature(returnType = SINT32)
        abstract int get_errno();

        @DowncallSignature(returnType = VOID, argumentTypes = {SINT32})
        abstract void set_errno(int errno);

        @DowncallSignature(returnType = SINT64, argumentTypes = {SINT64, SINT32, SINT32, SINT32, SINT64})
        abstract long call_mmap(long length, int prot, int flags, int fd, long offset);

        @DowncallSignature(returnType = SINT32, argumentTypes = {SINT64, SINT64})
        abstract int call_munmap(long address, long length);

        @DowncallSignature(returnType = VOID, argumentTypes = {SINT64, SINT64, SINT64})
        abstract void call_msync(long address, long offset, long length);

        @DowncallSignature(returnType = VOID, argumentTypes = {SINT32, POINTER, SINT32})
        abstract void call_strerror(int error, long buf, int buflen);

        @DowncallSignature(returnType = SINT64)
        abstract long call_getpid();

        @DowncallSignature(returnType = SINT32, argumentTypes = {SINT32})
        abstract int call_umask(int mask);

        @DowncallSignature(returnType = SINT32, argumentTypes = {SINT32, POINTER, SINT32, SINT32})
        abstract int call_openat(int dirFd, long pathname, int flags, int mode);

        @DowncallSignature(returnType = SINT32, argumentTypes = {SINT32})
        abstract int call_close(int fd);

        @DowncallSignature(returnType = SINT64, argumentTypes = {SINT32, POINTER, SINT64})
        abstract long call_read(int fd, long buf, long count);

        @DowncallSignature(returnType = SINT64, argumentTypes = {SINT32, POINTER, SINT64})
        abstract long call_write(int fd, long buf, long count);

        @DowncallSignature(returnType = SINT32, argumentTypes = {SINT32})
        abstract int call_dup(int fd);

        @DowncallSignature(returnType = SINT32, argumentTypes = {SINT32, SINT32, SINT32})
        abstract int call_dup2(int oldfd, int newfd, int inheritable);

        @DowncallSignature(returnType = SINT32, argumentTypes = {POINTER})
        abstract int call_pipe2(long pipefd);

        @DowncallSignature(returnType = SINT32, argumentTypes = {SINT32, POINTER, SINT32, POINTER, SINT32, POINTER, SINT32, SINT64, SINT64, POINTER})
        abstract int call_select(int nfds, long readfds, int readfdsLen, long writefds, int writefdsLen, long errfds, int errfdsLen, long timeoutSec, long timeoutUsec, long selected);

        @DowncallSignature(returnType = SINT32, argumentTypes = {SINT32, SINT32, SINT64, SINT64})
        abstract int call_poll(int fd, int writing, long timeoutSec, long timeoutUsec);

        @DowncallSignature(returnType = SINT64, argumentTypes = {SINT32, SINT64, SINT32})
        abstract long call_lseek(int fd, long offset, int whence);

        @DowncallSignature(returnType = SINT32, argumentTypes = {SINT32, SINT64})
        abstract int call_ftruncate(int fd, long length);

        @DowncallSignature(returnType = SINT32, argumentTypes = {POINTER, SINT64})
        abstract int call_truncate(long path, long length);

        @DowncallSignature(returnType = SINT32, argumentTypes = {SINT32})
        abstract int call_fsync(int fd);

        @DowncallSignature(returnType = SINT32, argumentTypes = {SINT32, SINT32})
        abstract int call_flock(int fd, int operation);

        @DowncallSignature(returnType = SINT32, argumentTypes = {SINT32, SINT32, SINT32, SINT32, SINT64, SINT64})
        abstract int call_fcntl_lock(int fd, int blocking, int lockType, int whence, long start, long length);

        @DowncallSignature(returnType = SINT32, argumentTypes = {SINT32, POINTER, SINT32, POINTER})
        abstract int call_fstatat(int dirFd, long path, int followSymlinks, long out);

        @DowncallSignature(returnType = SINT32, argumentTypes = {SINT32, POINTER})
        abstract int call_fstat(int fd, long out);

        @DowncallSignature(returnType = SINT32, argumentTypes = {POINTER, POINTER})
        abstract int call_statvfs(long path, long out);

        @DowncallSignature(returnType = SINT32, argumentTypes = {SINT32, POINTER})
        abstract int call_fstatvfs(int fd, long out);

        @DowncallSignature(returnType = SINT32, argumentTypes = {POINTER, POINTER, POINTER, POINTER, POINTER, SINT32})
        abstract int call_uname(long sysname, long nodename, long release, long version, long machine, int size);

        @DowncallSignature(returnType = SINT32, argumentTypes = {SINT32, POINTER, SINT32})
        abstract int call_unlinkat(int dirFd, long pathname, int rmdir);

        @DowncallSignature(returnType = SINT32, argumentTypes = {SINT32, POINTER, SINT32, POINTER, SINT32})
        abstract int call_linkat(int oldDirFd, long oldPath, int newDirFd, long newPath, int flags);

        @DowncallSignature(returnType = SINT32, argumentTypes = {POINTER, SINT32, POINTER})
        abstract int call_symlinkat(long target, int dirFd, long linkpath);

        @DowncallSignature(returnType = SINT32, argumentTypes = {SINT32, POINTER, SINT32})
        abstract int call_mkdirat(int dirFd, long pathname, int mode);

        @DowncallSignature(returnType = SINT32, argumentTypes = {POINTER, SINT64})
        abstract int call_getcwd(long buf, long size);

        @DowncallSignature(returnType = SINT32, argumentTypes = {POINTER})
        abstract int call_chdir(long path);

        @DowncallSignature(returnType = SINT32, argumentTypes = {SINT32})
        abstract int call_fchdir(int fd);

        @DowncallSignature(returnType = SINT32, argumentTypes = {SINT32})
        abstract int call_isatty(int fd);

        @DowncallSignature(returnType = SINT64, argumentTypes = {POINTER})
        abstract long call_opendir(long name);

        @DowncallSignature(returnType = SINT64, argumentTypes = {SINT32})
        abstract long call_fdopendir(int fd);

        @DowncallSignature(returnType = SINT32, argumentTypes = {SINT64})
        abstract int call_closedir(long dirp);

        @DowncallSignature(returnType = SINT32, argumentTypes = {SINT64, POINTER, SINT64, POINTER})
        abstract int call_readdir(long dirp, long nameBuf, long nameBufSize, long out);

        @DowncallSignature(returnType = VOID, argumentTypes = {SINT64})
        abstract void call_rewinddir(long dirp);

        @DowncallSignature(returnType = SINT32, argumentTypes = {SINT32, POINTER, POINTER, SINT32})
        abstract int call_utimensat(int dirFd, long path, long timespec, int followSymlinks);

        @DowncallSignature(returnType = SINT32, argumentTypes = {SINT32, POINTER})
        abstract int call_futimens(int fd, long timespec);

        @DowncallSignature(returnType = SINT32, argumentTypes = {SINT32, POINTER})
        abstract int call_futimes(int fd, long timeval);

        @DowncallSignature(returnType = SINT32, argumentTypes = {POINTER, POINTER})
        abstract int call_lutimes(long filename, long timeval);

        @DowncallSignature(returnType = SINT32, argumentTypes = {POINTER, POINTER})
        abstract int call_utimes(long filename, long timeval);

        @DowncallSignature(returnType = SINT32, argumentTypes = {SINT32, POINTER, SINT32, POINTER})
        abstract int call_renameat(int oldDirFd, long oldPath, int newDirFd, long newPath);

        @DowncallSignature(returnType = SINT32, argumentTypes = {SINT32, POINTER, SINT32, SINT32, SINT32})
        abstract int call_faccessat(int dirFd, long path, int mode, int effectiveIds, int followSymlinks);

        @DowncallSignature(returnType = SINT32, argumentTypes = {SINT32, POINTER, SINT32, SINT32})
        abstract int call_fchmodat(int dirFd, long path, int mode, int followSymlinks);

        @DowncallSignature(returnType = SINT32, argumentTypes = {SINT32, SINT32})
        abstract int call_fchmod(int fd, int mode);

        @DowncallSignature(returnType = SINT32, argumentTypes = {SINT32, POINTER, SINT64, SINT64, SINT32})
        abstract int call_fchownat(int dirfd, long pathname, long owner, long group, int followSymlinks);

        @DowncallSignature(returnType = SINT32, argumentTypes = {SINT32, SINT64, SINT64})
        abstract int call_fchown(int fd, long owner, long group);

        @DowncallSignature(returnType = SINT64, argumentTypes = {SINT32, POINTER, POINTER, SINT64})
        abstract long call_readlinkat(int dirFd, long path, long buf, long size);

        @DowncallSignature(returnType = SINT32, argumentTypes = {SINT32})
        abstract int get_inheritable(int fd);

        @DowncallSignature(returnType = SINT32, argumentTypes = {SINT32, SINT32})
        abstract int set_inheritable(int fd, int inheritable);

        @DowncallSignature(returnType = SINT32, argumentTypes = {SINT32})
        abstract int get_blocking(int fd);

        @DowncallSignature(returnType = SINT32, argumentTypes = {SINT32, SINT32})
        abstract int set_blocking(int fd, int blocking);

        @DowncallSignature(returnType = SINT32, argumentTypes = {SINT32, POINTER})
        abstract int get_terminal_size(int fd, long size);

        @DowncallSignature(returnType = SINT32, argumentTypes = {SINT32})
        abstract int call_raise(int signal);

        @DowncallSignature(returnType = SINT32, argumentTypes = {SINT32})
        abstract int call_alarm(int seconds);

        @DowncallSignature(returnType = SINT32, argumentTypes = {SINT32, POINTER})
        abstract int call_getitimer(int which, long currentValue);

        @DowncallSignature(returnType = SINT32, argumentTypes = {SINT32, POINTER, POINTER})
        abstract int call_setitimer(int which, long newValue, long oldValue);

        @DowncallSignature(returnType = SINT32, argumentTypes = {SINT32})
        abstract int signal_self(int signal);

        @DowncallSignature(returnType = SINT32, argumentTypes = {SINT64, SINT32})
        abstract int call_kill(long pid, int signal);

        @DowncallSignature(returnType = SINT32, argumentTypes = {SINT64, SINT32})
        abstract int call_killpg(long pgid, int signal);

        @DowncallSignature(returnType = SINT64, argumentTypes = {SINT64, POINTER, SINT32})
        abstract long call_waitpid(long pid, long status, int options);

        @DowncallSignature(returnType = SINT32, argumentTypes = {SINT32})
        abstract int call_wcoredump(int status);

        @DowncallSignature(returnType = SINT32, argumentTypes = {SINT32})
        abstract int call_wifcontinued(int status);

        @DowncallSignature(returnType = SINT32, argumentTypes = {SINT32})
        abstract int call_wifstopped(int status);

        @DowncallSignature(returnType = SINT32, argumentTypes = {SINT32})
        abstract int call_wifsignaled(int status);

        @DowncallSignature(returnType = SINT32, argumentTypes = {SINT32})
        abstract int call_wifexited(int status);

        @DowncallSignature(returnType = SINT32, argumentTypes = {SINT32})
        abstract int call_wexitstatus(int status);

        @DowncallSignature(returnType = SINT32, argumentTypes = {SINT32})
        abstract int call_wtermsig(int status);

        @DowncallSignature(returnType = SINT32, argumentTypes = {SINT32})
        abstract int call_wstopsig(int status);

        @DowncallSignature(returnType = SINT64)
        abstract long call_getuid();

        @DowncallSignature(returnType = SINT64)
        abstract long call_geteuid();

        @DowncallSignature(returnType = SINT64)
        abstract long call_getgid();

        @DowncallSignature(returnType = SINT64)
        abstract long call_getegid();

        @DowncallSignature(returnType = SINT64)
        abstract long call_getppid();

        @DowncallSignature(returnType = SINT64, argumentTypes = {SINT64})
        abstract long call_getpgid(long pid);

        @DowncallSignature(returnType = SINT32, argumentTypes = {SINT64, SINT64})
        abstract int call_setpgid(long pid, long pgid);

        @DowncallSignature(returnType = SINT64)
        abstract long call_getpgrp();

        @DowncallSignature(returnType = SINT64, argumentTypes = {SINT64})
        abstract long call_getsid(long pid);

        @DowncallSignature(returnType = SINT64)
        abstract long call_setsid();

        @DowncallSignature(returnType = SINT32, argumentTypes = {SINT64, POINTER})
        abstract int call_getgroups(long size, long out);

        @DowncallSignature(returnType = SINT32, argumentTypes = {SINT32, POINTER})
        abstract int call_getrusage(int who, long out);

        @DowncallSignature(returnType = SINT32, argumentTypes = {POINTER})
        abstract int call_openpty(long outvars);

        @DowncallSignature(returnType = SINT32, argumentTypes = {POINTER})
        abstract int call_ctermid(long buf);

        @DowncallSignature(returnType = SINT32, argumentTypes = {POINTER, POINTER, SINT32})
        abstract int call_setenv(long name, long value, int overwrite);

        @DowncallSignature(returnType = SINT32, argumentTypes = {POINTER})
        abstract int call_unsetenv(long name);

        @DowncallSignature(returnType = SINT32, argumentTypes = {
                        POINTER, POINTER, SINT32, SINT32, SINT32, SINT32, SINT32, SINT32, SINT32, SINT32, SINT32, SINT32,
                        SINT32, SINT32, SINT32, SINT32, SINT32, SINT32, SINT32, POINTER, SINT64
        })
        abstract int fork_exec(long data, long offsets, int offsetsLen, int argsPos, int envPos, int cwdPos, int stdinRdFd, int stdinWrFd, int stdoutRdFd, int stdoutWrFd, int stderrRdFd,
                        int stderrWrFd, int errPipeRdFd, int errPipeWrFd, int closeFds, int restoreSignals, int callSetsid, int pgidToSet, int allowVFork, long fdsToKeep, long fdsToKeepLen);

        @DowncallSignature(returnType = VOID, argumentTypes = {POINTER, POINTER, SINT32})
        abstract void call_execv(long data, long offsets, int offsetsLen);

        @DowncallSignature(returnType = SINT32, argumentTypes = {POINTER})
        abstract int call_system(long pathname);

        @DowncallSignature(returnType = SINT32, argumentTypes = {SINT64, POINTER, SINT32, POINTER})
        abstract int call_getpwuid_r(long uid, long buffer, int bufferSize, long output);

        @DowncallSignature(returnType = SINT32, argumentTypes = {POINTER, POINTER, SINT32, POINTER})
        abstract int call_getpwname_r(long name, long buffer, int bufferSize, long output);

        @DowncallSignature(returnType = VOID)
        abstract void call_setpwent();

        @DowncallSignature(returnType = VOID)
        abstract void call_endpwent();

        @DowncallSignature(returnType = POINTER, argumentTypes = {POINTER})
        abstract long call_getpwent(long bufferSize);

        @DowncallSignature(returnType = SINT32, argumentTypes = {POINTER, POINTER, SINT32, POINTER})
        abstract int get_getpwent_data(long p, long buffer, int bufferSize, long output);

        @DowncallSignature(returnType = SINT64)
        abstract long get_sysconf_getpw_r_size_max();

        @DowncallSignature(returnType = SINT32, argumentTypes = {SINT32, SINT32, SINT32})
        abstract int call_socket(int family, int type, int protocol);

        @DowncallSignature(returnType = SINT32, argumentTypes = {SINT32, POINTER, POINTER})
        abstract int call_accept(int sockfd, long addr, long addrLen);

        @DowncallSignature(returnType = SINT32, argumentTypes = {SINT32, POINTER, SINT32})
        abstract int call_bind(int sockfd, long addr, int addrLen);

        @DowncallSignature(returnType = SINT32, argumentTypes = {SINT32, POINTER, SINT32})
        abstract int call_connect(int sockfd, long addr, int addrLen);

        @DowncallSignature(returnType = SINT32, argumentTypes = {SINT32, SINT32})
        abstract int call_listen(int sockfd, int backlog);

        @DowncallSignature(returnType = SINT32, argumentTypes = {SINT32, POINTER, POINTER})
        abstract int call_getpeername(int sockfd, long addr, long addrLen);

        @DowncallSignature(returnType = SINT32, argumentTypes = {SINT32, POINTER, POINTER})
        abstract int call_getsockname(int sockfd, long addr, long addrLen);

        @DowncallSignature(returnType = SINT32, argumentTypes = {SINT32, POINTER, SINT32, SINT32})
        abstract int call_send(int sockfd, long buf, int len, int flags);

        @DowncallSignature(returnType = SINT32, argumentTypes = {SINT32, POINTER, SINT32, SINT32, SINT32, POINTER, SINT32})
        abstract int call_sendto(int sockfd, long buf, int offset, int len, int flags, long addr, int addrLen);

        @DowncallSignature(returnType = SINT32, argumentTypes = {SINT32, POINTER, SINT32, SINT32})
        abstract int call_recv(int sockfd, long buf, int len, int flags);

        @DowncallSignature(returnType = SINT32, argumentTypes = {SINT32, POINTER, SINT32, SINT32, SINT32, POINTER, POINTER})
        abstract int call_recvfrom(int sockfd, long buf, int offset, int len, int flags, long srcAddr, long addrLen);

        @DowncallSignature(returnType = SINT32, argumentTypes = {SINT32, SINT32})
        abstract int call_shutdown(int sockfd, int how);

        @DowncallSignature(returnType = SINT32, argumentTypes = {SINT32, SINT32, SINT32, POINTER, POINTER})
        abstract int call_getsockopt(int sockfd, int level, int optname, long buf, long bufLen);

        @DowncallSignature(returnType = SINT32, argumentTypes = {SINT32, SINT32, SINT32, POINTER, SINT32})
        abstract int call_setsockopt(int sockfd, int level, int optname, long buf, int bufLen);

        @DowncallSignature(returnType = SINT32, argumentTypes = {POINTER})
        abstract int call_inet_addr(long src);

        @DowncallSignature(returnType = SINT64, argumentTypes = {POINTER})
        abstract long call_inet_aton(long src);

        @DowncallSignature(returnType = SINT32, argumentTypes = {SINT32, POINTER})
        abstract int call_inet_ntoa(int src, long dst);

        @DowncallSignature(returnType = SINT32, argumentTypes = {SINT32, POINTER, POINTER})
        abstract int call_inet_pton(int family, long src, long dst);

        @DowncallSignature(returnType = SINT32, argumentTypes = {SINT32, POINTER, POINTER, SINT32})
        abstract int call_inet_ntop(int family, long src, long dst, int dstSize);

        @DowncallSignature(returnType = SINT32, argumentTypes = {POINTER, SINT64})
        abstract int call_gethostname(long buf, long bufLen);

        @DowncallSignature(returnType = SINT32, argumentTypes = {POINTER, SINT32, POINTER, SINT32, POINTER, SINT32, SINT32})
        abstract int call_getnameinfo(long addr, int addrLen, long hostBuf, int hostBufLen, long servBuf, int servBufLen, int flags);

        @DowncallSignature(returnType = SINT32, argumentTypes = {POINTER, POINTER, SINT32, SINT32, SINT32, SINT32, POINTER})
        abstract int call_getaddrinfo(long node, long service, int family, int sockType, int protocol, int flags, long ptr);

        @DowncallSignature(returnType = VOID, argumentTypes = {SINT64})
        abstract void call_freeaddrinfo(long ptr);

        @DowncallSignature(returnType = VOID, argumentTypes = {SINT32, POINTER, SINT32})
        abstract void call_gai_strerror(int error, long buf, int buflen);

        @DowncallSignature(returnType = SINT32, argumentTypes = {SINT64, POINTER, POINTER, POINTER})
        abstract int get_addrinfo_members(long ptr, long intData, long longData, long addr);

        @DowncallSignature(returnType = POINTER, argumentTypes = {POINTER, SINT32, SINT32, SINT32})
        abstract long call_sem_open(long name, int openFlags, int mode, int value);

        @DowncallSignature(returnType = SINT32, argumentTypes = {POINTER})
        abstract int call_sem_close(long handle);

        @DowncallSignature(returnType = SINT32, argumentTypes = {POINTER})
        abstract int call_sem_unlink(long name);

        @DowncallSignature(returnType = SINT32, argumentTypes = {POINTER, POINTER})
        abstract int call_sem_getvalue(long handle, long value);

        @DowncallSignature(returnType = SINT32, argumentTypes = {POINTER})
        abstract int call_sem_post(long handle);

        @DowncallSignature(returnType = SINT32, argumentTypes = {POINTER})
        abstract int call_sem_wait(long handle);

        @DowncallSignature(returnType = SINT32, argumentTypes = {POINTER})
        abstract int call_sem_trywait(long handle);

        @DowncallSignature(returnType = SINT32, argumentTypes = {POINTER, SINT64})
        abstract int call_sem_timedwait(long handle, long deadlineNs);

        @DowncallSignature(returnType = SINT32, argumentTypes = {SINT32, SINT64, POINTER})
        abstract int call_ioctl_bytes(int fd, long request, long buffer);

        @DowncallSignature(returnType = SINT32, argumentTypes = {SINT32, SINT64, SINT32})
        abstract int call_ioctl_int(int fd, long request, int arg);

        @DowncallSignature(returnType = SINT64, argumentTypes = {SINT32})
        abstract long call_sysconf(int name);

        @TruffleBoundary
        static String getLibPath(PythonContext context) {
            String libPythonName = PythonContext.getSupportLibName(SUPPORTING_NATIVE_LIB_NAME);
            TruffleFile homePath = context.getEnv().getInternalTruffleFile(context.getCAPIHome().toJavaStringUncached());
            TruffleFile file = homePath.resolve(libPythonName);
            return file.getPath();
        }

        @TruffleBoundary
        static NativeLibrary loadNativeLibrary(PythonContext context) {
            String path = getLibPath(context);
            try {
                return context.ensureNativeContext().loadLibrary(path, PosixConstants.RTLD_LOCAL.value);
            } catch (NativeLibraryLoadException e) {
                throw new UnsupportedOperationException(String.format("""
                                Could not load posix support library from path '%s'. Troubleshooting:\s
                                Check permissions of the file.""", path), e);
            }
        }
    }

    abstract static class CryptNativeFunctionInvoker {
        @DowncallSignature(returnType = POINTER, argumentTypes = {POINTER, POINTER})
        abstract long crypt(long word, long salt);

        @TruffleBoundary
        static NativeLibrary loadNativeLibrary(PythonContext context) {
            if (PythonLanguage.getPythonOS() == PythonOS.PLATFORM_DARWIN) {
                return context.ensureNativeContext().getDefaultLibrary();
            }
            /*
             * We don't want to link the posix support library against libcrypt, because it might
             * not be available on the target Linux system and would make the whole support library
             * fail to load. Load it dynamically on demand instead.
             */
            try {
                return context.ensureNativeContext().loadLibrary("libcrypt.so", PosixConstants.RTLD_LOCAL.value);
            } catch (NativeLibraryLoadException e) {
                throw new UnsupportedOperationException("Could not load crypt support library 'libcrypt.so'.", e);
            }
        }
    }

    private final PythonContext context;
    private final TruffleString nativeBackend;
    private final PosixNativeFunctionInvoker posixNativeFunctionInvoker;
    private final CryptNativeFunctionInvoker cryptNativeFunctionInvoker;
    @CompilationFinal(dimensions = 1) private long[] constantValues;

    public NativePosixSupport(PythonContext context, TruffleString nativeBackend) {
        assert nativeBackend.equalsUncached(T_NATIVE, TS_ENCODING);
        this.context = context;
        this.nativeBackend = nativeBackend;
        this.posixNativeFunctionInvoker = new PosixNativeFunctionInvokerGen(context);
        this.cryptNativeFunctionInvoker = new CryptNativeFunctionInvokerGen(context);
        setEnv(context.getEnv());
    }

    long getConstant(NativePosixConstants constant) {
        if (constantValues == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            long[] values = new long[NativePosixConstants.values().length];
            long nativeValues = NativeMemory.mallocLongArray(values.length);
            try {
                int result = posixNativeFunctionInvoker.init_constants(nativeValues, values.length);
                if (result != 0) {
                    throw CompilerDirectives.shouldNotReachHere("Mismatched build of posix native library");
                }
                NativeMemory.readLongArrayElements(nativeValues, 0, values, 0, values.length);
                constantValues = values;
            } finally {
                NativeMemory.free(nativeValues);
            }
        }
        return constantValues[constant.ordinal()];
    }

    @Override
    public void setEnv(Env env) {
        if (env.isPreInitialization()) {
            return;
        }
        // Java NIO (and TruffleFile) do not expect/support changing native working directory since
        // it is inherently thread-unsafe operation. It is not defined how NIO behaves when native
        // cwd changes, thus we need to prevent TruffleFile from resolving relative paths using
        // NIO by setting Truffle cwd to a know value. This cannot be done lazily in chdir() because
        // native cwd is global, but Truffle cwd is per context.
        // TruffleFile will be unaware of the real working directory and keep resolving against the
        // original working directory. This should not matter since we do not use TruffleFile for
        // ordinary I/O when using the native backend.
        try {
            TruffleFile truffleFile = context.getEnv().getInternalTruffleFile(".").getAbsoluteFile();
            context.getEnv().setCurrentWorkingDirectory(truffleFile);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Unable to change Truffle working directory", e);
        }
    }

    @ExportMessage
    public TruffleString getBackend() {
        return nativeBackend;
    }

    @ExportMessage
    public TruffleString strerror(int errorCode,
                    @Bind Node inliningTarget,
                    @Shared("cString") @Cached NativeMemory.ZeroTerminatedUtf8ToTruffleStringNode zeroTerminatedUtf8ToTruffleStringNode) {
        // From man pages: The GNU C Library uses a buffer of 1024 characters for strerror().
        // This buffer size therefore should be sufficient to avoid an ERANGE error when calling
        // strerror_r().
        long buf = NativeMemory.mallocByteArray(STRERROR_BUF_LENGTH);
        try {
            posixNativeFunctionInvoker.call_strerror(errorCode, buf, STRERROR_BUF_LENGTH);
            // TODO PyUnicode_DecodeLocale
            return zeroTerminatedUtf8ToTruffleStringNode.execute(inliningTarget, buf);
        } finally {
            NativeMemory.free(buf);
        }
    }

    @ExportMessage
    public long getpid() {
        return posixNativeFunctionInvoker.call_getpid();
    }

    @ExportMessage
    public int umask(int mask) throws PosixException {
        int result = posixNativeFunctionInvoker.call_umask(mask);
        if (result < 0) {
            throw getErrnoAndThrowPosixException();
        }
        return result;
    }

    @ExportMessage
    public int openat(int dirFd, Object pathname, int flags, int mode) throws PosixException {
        long pathnamePtr = pathToNativeCString(pathname);
        try {
            int fd = posixNativeFunctionInvoker.call_openat(dirFd, pathnamePtr, flags, mode);
            if (fd < 0) {
                throw getErrnoAndThrowPosixException();
            }
            return fd;
        } finally {
            NativeMemory.free(pathnamePtr);
        }
    }

    @ExportMessage
    public int close(int fd) throws PosixException {
        final int rv = posixNativeFunctionInvoker.call_close(fd);
        if (rv < 0) {
            throw getErrnoAndThrowPosixException();
        }
        return rv;
    }

    @ExportMessage
    public Buffer read(int fd, long length) throws PosixException {
        long count = Math.min(length, MAX_READ);
        Buffer buffer = Buffer.allocate(count);
        long nativeBuffer = NativeMemory.mallocByteArrayOrNull(count);
        try {
            posixNativeFunctionInvoker.set_errno(0);
            long n = posixNativeFunctionInvoker.call_read(fd, nativeBuffer, count);
            if (n < 0) {
                throw getErrnoAndThrowPosixException();
            }
            NativeMemory.readByteArrayElements(nativeBuffer, 0, buffer.data, 0, (int) n);
            return buffer.withLength(n);
        } finally {
            NativeMemory.free(nativeBuffer);
        }
    }

    @ExportMessage
    public long write(int fd, Buffer data) throws PosixException {
        long nativeBuffer = NativeMemory.mallocByteArrayOrNull(data.length);
        try {
            NativeMemory.writeByteArrayElements(nativeBuffer, 0, data.data, 0, (int) data.length);
            posixNativeFunctionInvoker.set_errno(0);
            long n = posixNativeFunctionInvoker.call_write(fd, nativeBuffer, data.length);
            if (n < 0) {
                throw getErrnoAndThrowPosixException();
            }
            return n;
        } finally {
            NativeMemory.free(nativeBuffer);
        }
    }

    @ExportMessage
    public int dup(int fd) throws PosixException {
        int newFd = posixNativeFunctionInvoker.call_dup(fd);
        if (newFd < 0) {
            throw getErrnoAndThrowPosixException();
        }
        return newFd;
    }

    @ExportMessage
    public int dup2(int fd, int fd2, boolean inheritable) throws PosixException {
        int newFd = posixNativeFunctionInvoker.call_dup2(fd, fd2, inheritable ? 1 : 0);
        if (newFd < 0) {
            throw getErrnoAndThrowPosixException();
        }
        return newFd;
    }

    @ExportMessage
    public boolean getInheritable(int fd) throws PosixException {
        int result = posixNativeFunctionInvoker.get_inheritable(fd);
        if (result < 0) {
            throw getErrnoAndThrowPosixException();
        }
        return result != 0;
    }

    @ExportMessage
    public void setInheritable(int fd, boolean inheritable) throws PosixException {
        if (posixNativeFunctionInvoker.set_inheritable(fd, inheritable ? 1 : 0) < 0) {
            throw getErrnoAndThrowPosixException();
        }
    }

    @ExportMessage
    public int[] pipe() throws PosixException {
        int[] fds = new int[2];
        long nativeFds = NativeMemory.mallocIntArray(fds.length);
        try {
            if (posixNativeFunctionInvoker.call_pipe2(nativeFds) != 0) {
                throw getErrnoAndThrowPosixException();
            }
            NativeMemory.readIntArrayElements(nativeFds, 0, fds, 0, fds.length);
            return fds;
        } finally {
            NativeMemory.free(nativeFds);
        }
    }

    @ExportMessage
    public SelectResult select(int[] readfds, int[] writefds, int[] errorfds, Timeval timeout) throws PosixException {
        int largestFD = findMax(readfds, -1);
        largestFD = findMax(writefds, largestFD);
        largestFD = findMax(errorfds, largestFD);
        // This will be treated as boolean array (output parameter), each item indicating if given
        // FD was selected or not
        byte[] selected = new byte[readfds.length + writefds.length + errorfds.length];
        int nfds = largestFD == -1 ? 0 : largestFD + 1;
        long secs = -1, usecs = -1;
        if (timeout != null) {
            secs = timeout.getSeconds();
            usecs = timeout.getMicroseconds();
        }
        long nativeReadFds = NULLPTR;
        long nativeWriteFds = NULLPTR;
        long nativeErrorFds = NULLPTR;
        long nativeSelected = NULLPTR;
        try {
            nativeReadFds = NativeMemory.copyToNativeIntArrayOrNull(readfds);
            nativeWriteFds = NativeMemory.copyToNativeIntArrayOrNull(writefds);
            nativeErrorFds = NativeMemory.copyToNativeIntArrayOrNull(errorfds);
            nativeSelected = NativeMemory.mallocByteArrayOrNull(selected.length);
            int result = posixNativeFunctionInvoker.call_select(nfds,
                            nativeReadFds, readfds.length,
                            nativeWriteFds, writefds.length,
                            nativeErrorFds, errorfds.length,
                            secs, usecs, nativeSelected);
            if (result < 0) {
                throw getErrnoAndThrowPosixException();
            }
            if (selected.length > 0) {
                NativeMemory.readByteArrayElements(nativeSelected, 0, selected, 0, selected.length);
            }
        } finally {
            NativeMemory.free(nativeSelected);
            NativeMemory.free(nativeErrorFds);
            NativeMemory.free(nativeWriteFds);
            NativeMemory.free(nativeReadFds);
        }
        return new SelectResult(
                        selectFillInResult(readfds, selected, 0),
                        selectFillInResult(writefds, selected, readfds.length),
                        selectFillInResult(errorfds, selected, readfds.length + writefds.length));

    }

    private static boolean[] selectFillInResult(int[] fds, byte[] selected, int selectedOffset) {
        boolean[] res = new boolean[fds.length];
        for (int i = 0; i < fds.length; i++) {
            res[i] = selected[selectedOffset + i] != 0;
        }
        return res;
    }

    private static int findMax(int[] items, int currentMax) {
        int max = currentMax;
        for (int item : items) {
            if (item > max) {
                max = item;
            }
        }
        return max;
    }

    @ExportMessage
    public boolean poll(int fd, boolean forWriting, Timeval timeout) throws PosixException {
        long secs = -1, usecs = -1;
        if (timeout != null) {
            secs = timeout.getSeconds();
            usecs = timeout.getMicroseconds();
        }
        int result = posixNativeFunctionInvoker.call_poll(fd, forWriting ? 1 : 0, secs, usecs);
        if (result < 0) {
            throw getErrnoAndThrowPosixException();
        }
        if (result == 0) {
            return false;
        } else {
            return true;
        }
    }

    @ExportMessage
    public long lseek(int fd, long offset, int how) throws PosixException {
        long res = posixNativeFunctionInvoker.call_lseek(fd, offset, how);
        if (res < 0) {
            throw getErrnoAndThrowPosixException();
        }
        return res;
    }

    @ExportMessage
    public void ftruncate(int fd, long length) throws PosixException {
        int res = posixNativeFunctionInvoker.call_ftruncate(fd, length);
        if (res != 0) {
            throw getErrnoAndThrowPosixException();
        }
    }

    @ExportMessage
    public void truncate(Object path, long length) throws PosixException {
        long pathPtr = pathToNativeCString(path);
        try {
            int res = posixNativeFunctionInvoker.call_truncate(pathPtr, length);
            if (res != 0) {
                throw getErrnoAndThrowPosixException();
            }
        } finally {
            NativeMemory.free(pathPtr);
        }
    }

    @ExportMessage
    public void fsync(int fd) throws PosixException {
        int res = posixNativeFunctionInvoker.call_fsync(fd);
        if (res != 0) {
            throw getErrnoAndThrowPosixException();
        }
    }

    @ExportMessage
    void flock(int fd, int operation) throws PosixException {
        int res = posixNativeFunctionInvoker.call_flock(fd, operation);
        if (res != 0) {
            throw getErrnoAndThrowPosixException();
        }
    }

    @ExportMessage
    void fcntlLock(int fd, boolean blocking, int lockType, int whence, long start, long length) throws PosixException {
        int res = posixNativeFunctionInvoker.call_fcntl_lock(fd, blocking ? 1 : 0, lockType, whence, start, length);
        if (res != 0) {
            throw getErrnoAndThrowPosixException();
        }
    }

    @ExportMessage
    public boolean getBlocking(int fd) throws PosixException {
        int result = posixNativeFunctionInvoker.get_blocking(fd);
        if (result < 0) {
            throw getErrnoAndThrowPosixException();
        }
        return result != 0;
    }

    @ExportMessage
    public void setBlocking(int fd, boolean blocking) throws PosixException {
        if (posixNativeFunctionInvoker.set_blocking(fd, blocking ? 1 : 0) < 0) {
            throw getErrnoAndThrowPosixException();
        }
    }

    @ExportMessage
    public int[] getTerminalSize(int fd) throws PosixException {
        int[] size = new int[2];
        long nativeSize = NativeMemory.mallocIntArray(size.length);
        try {
            if (posixNativeFunctionInvoker.get_terminal_size(fd, nativeSize) != 0) {
                throw getErrnoAndThrowPosixException();
            }
            NativeMemory.readIntArrayElements(nativeSize, 0, size, 0, size.length);
            return size;
        } finally {
            NativeMemory.free(nativeSize);
        }
    }

    @ExportMessage
    public long sysconf(int name) throws PosixException {
        long result = posixNativeFunctionInvoker.call_sysconf(name);
        if (result == -1) {
            int errno = posixNativeFunctionInvoker.get_errno();
            if (errno != 0) {
                throw newPosixException(errno);
            }
        }
        return result;
    }

    @ExportMessage
    public long[] fstatat(int dirFd, Object pathname, boolean followSymlinks) throws PosixException {
        long[] out = new long[13];
        long nativeOut = NULLPTR;
        long pathnamePtr = NULLPTR;
        try {
            nativeOut = NativeMemory.mallocLongArray(out.length);
            pathnamePtr = pathToNativeCString(pathname);
            int res = posixNativeFunctionInvoker.call_fstatat(dirFd, pathnamePtr, followSymlinks ? 1 : 0, nativeOut);
            if (res != 0) {
                throw getErrnoAndThrowPosixException();
            }
            NativeMemory.readLongArrayElements(nativeOut, 0, out, 0, out.length);
            return out;
        } finally {
            NativeMemory.free(pathnamePtr);
            NativeMemory.free(nativeOut);
        }
    }

    @ExportMessage
    public long[] fstat(int fd) throws PosixException {
        long[] out = new long[13];
        long nativeOut = NativeMemory.mallocLongArray(out.length);
        try {
            int res = posixNativeFunctionInvoker.call_fstat(fd, nativeOut);
            if (res != 0) {
                throw getErrnoAndThrowPosixException();
            }
            NativeMemory.readLongArrayElements(nativeOut, 0, out, 0, out.length);
            return out;
        } finally {
            NativeMemory.free(nativeOut);
        }
    }

    @ExportMessage
    public long[] statvfs(Object path) throws PosixException {
        long[] out = new long[11];
        long nativeOut = NULLPTR;
        long pathPtr = NULLPTR;
        try {
            nativeOut = NativeMemory.mallocLongArray(out.length);
            pathPtr = pathToNativeCString(path);
            int res = posixNativeFunctionInvoker.call_statvfs(pathPtr, nativeOut);
            if (res != 0) {
                throw getErrnoAndThrowPosixException();
            }
            NativeMemory.readLongArrayElements(nativeOut, 0, out, 0, out.length);
            return out;
        } finally {
            NativeMemory.free(pathPtr);
            NativeMemory.free(nativeOut);
        }
    }

    @ExportMessage
    public long[] fstatvfs(int fd) throws PosixException {
        long[] out = new long[11];
        long nativeOut = NativeMemory.mallocLongArray(out.length);
        try {
            int res = posixNativeFunctionInvoker.call_fstatvfs(fd, nativeOut);
            if (res != 0) {
                throw getErrnoAndThrowPosixException();
            }
            NativeMemory.readLongArrayElements(nativeOut, 0, out, 0, out.length);
            return out;
        } finally {
            NativeMemory.free(nativeOut);
        }
    }

    @ExportMessage
    public Object[] uname(
                    @Bind Node inliningTarget,
                    @Shared("cString") @Cached NativeMemory.ZeroTerminatedUtf8ToTruffleStringNode zeroTerminatedUtf8ToTruffleStringNode) throws PosixException {
        long sysPtr = NULLPTR;
        long nodePtr = NULLPTR;
        long relPtr = NULLPTR;
        long verPtr = NULLPTR;
        long machinePtr = NULLPTR;
        try {
            sysPtr = NativeMemory.mallocByteArray(UNAME_BUF_LENGTH);
            nodePtr = NativeMemory.mallocByteArray(UNAME_BUF_LENGTH);
            relPtr = NativeMemory.mallocByteArray(UNAME_BUF_LENGTH);
            verPtr = NativeMemory.mallocByteArray(UNAME_BUF_LENGTH);
            machinePtr = NativeMemory.mallocByteArray(UNAME_BUF_LENGTH);
            int res = posixNativeFunctionInvoker.call_uname(sysPtr, nodePtr, relPtr, verPtr, machinePtr, UNAME_BUF_LENGTH);
            if (res != 0) {
                throw getErrnoAndThrowPosixException();
            }
            return new Object[]{
                            // TODO PyUnicode_DecodeFSDefault
                            zeroTerminatedUtf8ToTruffleStringNode.execute(inliningTarget, sysPtr),
                            zeroTerminatedUtf8ToTruffleStringNode.execute(inliningTarget, nodePtr),
                            zeroTerminatedUtf8ToTruffleStringNode.execute(inliningTarget, relPtr),
                            zeroTerminatedUtf8ToTruffleStringNode.execute(inliningTarget, verPtr),
                            zeroTerminatedUtf8ToTruffleStringNode.execute(inliningTarget, machinePtr)
            };
        } finally {
            NativeMemory.free(machinePtr);
            NativeMemory.free(verPtr);
            NativeMemory.free(relPtr);
            NativeMemory.free(nodePtr);
            NativeMemory.free(sysPtr);
        }
    }

    @ExportMessage
    public void unlinkat(int dirFd, Object pathname, boolean rmdir) throws PosixException {
        long pathnamePtr = pathToNativeCString(pathname);
        try {
            int result = posixNativeFunctionInvoker.call_unlinkat(dirFd, pathnamePtr, rmdir ? 1 : 0);
            if (result != 0) {
                throw getErrnoAndThrowPosixException();
            }
        } finally {
            NativeMemory.free(pathnamePtr);
        }
    }

    @ExportMessage
    public void linkat(int oldFdDir, Object oldPath, int newFdDir, Object newPath, int flags) throws PosixException {
        long oldPathPtr = NULLPTR;
        long newPathPtr = NULLPTR;
        try {
            oldPathPtr = pathToNativeCString(oldPath);
            newPathPtr = pathToNativeCString(newPath);
            int result = posixNativeFunctionInvoker.call_linkat(oldFdDir, oldPathPtr, newFdDir, newPathPtr, flags);
            if (result != 0) {
                throw getErrnoAndThrowPosixException();
            }
        } finally {
            NativeMemory.free(newPathPtr);
            NativeMemory.free(oldPathPtr);
        }
    }

    @ExportMessage
    public void symlinkat(Object target, int linkpathDirFd, Object linkpath) throws PosixException {
        long targetPtr = NULLPTR;
        long linkpathPtr = NULLPTR;
        try {
            targetPtr = pathToNativeCString(target);
            linkpathPtr = pathToNativeCString(linkpath);
            int result = posixNativeFunctionInvoker.call_symlinkat(targetPtr, linkpathDirFd, linkpathPtr);
            if (result != 0) {
                throw getErrnoAndThrowPosixException();
            }
        } finally {
            NativeMemory.free(linkpathPtr);
            NativeMemory.free(targetPtr);
        }
    }

    @ExportMessage
    public void mkdirat(int dirFd, Object pathname, int mode) throws PosixException {
        long pathnamePtr = pathToNativeCString(pathname);
        try {
            int result = posixNativeFunctionInvoker.call_mkdirat(dirFd, pathnamePtr, mode);
            if (result != 0) {
                throw getErrnoAndThrowPosixException();
            }
        } finally {
            NativeMemory.free(pathnamePtr);
        }
    }

    @ExportMessage
    public Object getcwd() throws PosixException {
        for (int bufLen = 1024;; bufLen += 1024) {
            Buffer buffer = Buffer.allocate(bufLen);
            long nativeBuffer = NativeMemory.mallocByteArray(bufLen);
            try {
                int n = posixNativeFunctionInvoker.call_getcwd(nativeBuffer, bufLen);
                if (n == 0) {
                    NativeMemory.readByteArrayElements(nativeBuffer, 0, buffer.data, 0, bufLen);
                    buffer = buffer.withLength(findZero(buffer.data));
                    return buffer;
                }
                int errno = posixNativeFunctionInvoker.get_errno();
                if (errno != OSErrorEnum.ERANGE.getNumber()) {
                    throw newPosixException(errno);
                }
            } finally {
                NativeMemory.free(nativeBuffer);
            }
        }
    }

    @ExportMessage
    public void chdir(Object path) throws PosixException {
        long pathPtr = pathToNativeCString(path);
        try {
            int result = posixNativeFunctionInvoker.call_chdir(pathPtr);
            if (result != 0) {
                throw getErrnoAndThrowPosixException();
            }
        } finally {
            NativeMemory.free(pathPtr);
        }
    }

    @ExportMessage
    public void fchdir(int fd) throws PosixException {
        int result = posixNativeFunctionInvoker.call_fchdir(fd);
        if (result != 0) {
            throw getErrnoAndThrowPosixException();
        }
    }

    @ExportMessage
    public boolean isatty(int fd) {
        return posixNativeFunctionInvoker.call_isatty(fd) != 0;
    }

    @ExportMessage
    public Object opendir(Object path) throws PosixException {
        long pathPtr = pathToNativeCString(path);
        try {
            long ptr = posixNativeFunctionInvoker.call_opendir(pathPtr);
            if (ptr == 0) {
                throw getErrnoAndThrowPosixException();
            }
            return ptr;
        } finally {
            NativeMemory.free(pathPtr);
        }
    }

    @ExportMessage
    public Object fdopendir(int fd) throws PosixException {
        long ptr = posixNativeFunctionInvoker.call_fdopendir(fd);
        if (ptr == 0) {
            throw getErrnoAndThrowPosixException();
        }
        return ptr;
    }

    @ExportMessage
    public void closedir(Object dirStreamObj) throws PosixException {
        int res = posixNativeFunctionInvoker.call_closedir(((Long) dirStreamObj).longValue());
        if (res != 0) {
            throw getErrnoAndThrowPosixException();
        }
    }

    @ExportMessage
    public Object readdir(Object dirStreamObj) throws PosixException {
        Buffer name = Buffer.allocate(DIRENT_NAME_BUF_LENGTH);
        long[] out = new long[2];
        long dirStream = ((Long) dirStreamObj).longValue();
        long nativeName = NULLPTR;
        long nativeOut = NULLPTR;
        try {
            nativeName = NativeMemory.mallocByteArray(DIRENT_NAME_BUF_LENGTH);
            nativeOut = NativeMemory.mallocLongArray(out.length);
            int result;
            do {
                result = posixNativeFunctionInvoker.call_readdir(dirStream, nativeName, DIRENT_NAME_BUF_LENGTH, nativeOut);
                if (result != 0) {
                    NativeMemory.readByteArrayElements(nativeName, 0, name.data, 0, name.data.length);
                }
            } while (result != 0 && name.data[0] == '.' && (name.data[1] == 0 || (name.data[1] == '.' && name.data[2] == 0)));
            if (result != 0) {
                NativeMemory.readLongArrayElements(nativeOut, 0, out, 0, out.length);
                return new DirEntry(name.withLength(findZero(name.data)), out[0], (int) out[1]);
            }
        } finally {
            NativeMemory.free(nativeOut);
            NativeMemory.free(nativeName);
        }
        int errno = posixNativeFunctionInvoker.get_errno();
        if (errno == 0) {
            return null;
        }
        throw newPosixException(errno);
    }

    @ExportMessage
    public void rewinddir(Object dirStreamObj) {
        posixNativeFunctionInvoker.call_rewinddir(((Long) dirStreamObj).longValue());
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    public Object dirEntryGetName(Object dirEntryObj) {
        DirEntry dirEntry = (DirEntry) dirEntryObj;
        return dirEntry.name;
    }

    @ExportMessage
    public static class DirEntryGetPath {
        @Specialization(guards = "endsWithSlash(scandirPath)")
        static Buffer withSlash(@SuppressWarnings("unused") NativePosixSupport receiver, DirEntry dirEntry, Object scandirPath) {
            Buffer scandirPathBuffer = (Buffer) scandirPath;
            int pathLen = scandirPathBuffer.data.length;
            int nameLen = (int) dirEntry.name.length;
            byte[] buf = new byte[pathLen + nameLen];
            PythonUtils.arraycopy(scandirPathBuffer.data, 0, buf, 0, pathLen);
            PythonUtils.arraycopy(dirEntry.name.data, 0, buf, pathLen, nameLen);
            return Buffer.wrap(buf);
        }

        @Specialization(guards = "!endsWithSlash(scandirPath)")
        static Buffer withoutSlash(@SuppressWarnings("unused") NativePosixSupport receiver, DirEntry dirEntry, Object scandirPath) {
            Buffer scandirPathBuffer = (Buffer) scandirPath;
            int pathLen = scandirPathBuffer.data.length;
            int nameLen = (int) dirEntry.name.length;
            byte[] buf = new byte[pathLen + 1 + nameLen];
            PythonUtils.arraycopy(scandirPathBuffer.data, 0, buf, 0, pathLen);
            buf[pathLen] = POSIX_FILENAME_SEPARATOR;
            PythonUtils.arraycopy(dirEntry.name.data, 0, buf, pathLen + 1, nameLen);
            return Buffer.wrap(buf);
        }

        protected static boolean endsWithSlash(Object path) {
            Buffer b = (Buffer) path;
            return b.data[b.data.length - 1] == POSIX_FILENAME_SEPARATOR;
        }
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    public long dirEntryGetInode(Object dirEntry) {
        DirEntry entry = (DirEntry) dirEntry;
        return entry.ino;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    public int dirEntryGetType(Object dirEntryObj) {
        DirEntry dirEntry = (DirEntry) dirEntryObj;
        return dirEntry.type;
    }

    @ExportMessage
    public void utimensat(int dirFd, Object pathname, long[] timespec, boolean followSymlinks) throws PosixException {
        assert PosixConstants.HAVE_UTIMENSAT.value;
        assert timespec == null || timespec.length == 4;
        long pathnamePtr = NULLPTR;
        long timespecPtr = NULLPTR;
        try {
            pathnamePtr = pathToNativeCString(pathname);
            timespecPtr = timespec == null ? NULLPTR : NativeMemory.copyToNativeLongArray(timespec);
            int ret = posixNativeFunctionInvoker.call_utimensat(dirFd, pathnamePtr, timespecPtr, followSymlinks ? 1 : 0);
            if (ret != 0) {
                throw getErrnoAndThrowPosixException();
            }
        } finally {
            NativeMemory.free(timespecPtr);
            NativeMemory.free(pathnamePtr);
        }
    }

    @ExportMessage
    public void futimens(int fd, long[] timespec) throws PosixException {
        assert PosixConstants.HAVE_FUTIMENS.value;
        assert timespec == null || timespec.length == 4;
        long timespecPtr = timespec == null ? NULLPTR : NativeMemory.copyToNativeLongArray(timespec);
        try {
            int ret = posixNativeFunctionInvoker.call_futimens(fd, timespecPtr);
            if (ret != 0) {
                throw getErrnoAndThrowPosixException();
            }
        } finally {
            NativeMemory.free(timespecPtr);
        }
    }

    @ExportMessage
    public void futimes(int fd, Timeval[] timeval) throws PosixException {
        assert timeval == null || timeval.length == 2;
        long timevalPtr = copyTimevalArrayToNativeOrNull(timeval);
        try {
            int ret = posixNativeFunctionInvoker.call_futimes(fd, timevalPtr);
            if (ret != 0) {
                throw getErrnoAndThrowPosixException();
            }
        } finally {
            NativeMemory.free(timevalPtr);
        }
    }

    @ExportMessage
    public void lutimes(Object filename, Timeval[] timeval) throws PosixException {
        assert timeval == null || timeval.length == 2;
        long filenamePtr = NULLPTR;
        long timevalPtr = NULLPTR;
        try {
            filenamePtr = pathToNativeCString(filename);
            timevalPtr = copyTimevalArrayToNativeOrNull(timeval);
            int ret = posixNativeFunctionInvoker.call_lutimes(filenamePtr, timevalPtr);
            if (ret != 0) {
                throw getErrnoAndThrowPosixException();
            }
        } finally {
            NativeMemory.free(timevalPtr);
            NativeMemory.free(filenamePtr);
        }
    }

    @ExportMessage
    public void utimes(Object filename, Timeval[] timeval) throws PosixException {
        assert timeval == null || timeval.length == 2;
        long filenamePtr = NULLPTR;
        long timevalPtr = NULLPTR;
        try {
            filenamePtr = pathToNativeCString(filename);
            timevalPtr = copyTimevalArrayToNativeOrNull(timeval);
            int ret = posixNativeFunctionInvoker.call_utimes(filenamePtr, timevalPtr);
            if (ret != 0) {
                throw getErrnoAndThrowPosixException();
            }
        } finally {
            NativeMemory.free(timevalPtr);
            NativeMemory.free(filenamePtr);
        }
    }

    @ExportMessage
    public void renameat(int oldDirFd, Object oldPath, int newDirFd, Object newPath) throws PosixException {
        long oldPathPtr = NULLPTR;
        long newPathPtr = NULLPTR;
        try {
            oldPathPtr = pathToNativeCString(oldPath);
            newPathPtr = pathToNativeCString(newPath);
            int ret = posixNativeFunctionInvoker.call_renameat(oldDirFd, oldPathPtr, newDirFd, newPathPtr);
            if (ret != 0) {
                throw getErrnoAndThrowPosixException();
            }
        } finally {
            NativeMemory.free(newPathPtr);
            NativeMemory.free(oldPathPtr);
        }
    }

    @ExportMessage
    public boolean faccessat(int dirFd, Object path, int mode, boolean effectiveIds, boolean followSymlinks) {
        long pathPtr = pathToNativeCString(path);
        int ret;
        try {
            ret = posixNativeFunctionInvoker.call_faccessat(dirFd, pathPtr, mode, effectiveIds ? 1 : 0, followSymlinks ? 1 : 0);
        } finally {
            NativeMemory.free(pathPtr);
        }
        if (ret != 0 && LOGGER.isLoggable(Level.FINE)) {
            log(Level.FINE, "faccessat return value: %d, errno: %d", ret, posixNativeFunctionInvoker.get_errno());
        }
        return ret == 0;
    }

    @ExportMessage
    public void fchmodat(int dirFd, Object path, int mode, boolean followSymlinks) throws PosixException {
        long pathPtr = pathToNativeCString(path);
        try {
            int ret = posixNativeFunctionInvoker.call_fchmodat(dirFd, pathPtr, mode, followSymlinks ? 1 : 0);
            if (ret != 0) {
                throw getErrnoAndThrowPosixException();
            }
        } finally {
            NativeMemory.free(pathPtr);
        }
    }

    @ExportMessage
    public void fchmod(int fd, int mode) throws PosixException {
        int ret = posixNativeFunctionInvoker.call_fchmod(fd, mode);
        if (ret != 0) {
            throw getErrnoAndThrowPosixException();
        }
    }

    @ExportMessage
    public void fchownat(int dirFd, Object path, long owner, long group, boolean followSymlinks) throws PosixException {
        long pathPtr = pathToNativeCString(path);
        try {
            int ret = posixNativeFunctionInvoker.call_fchownat(dirFd, pathPtr, owner, group, followSymlinks ? 1 : 0);
            if (ret != 0) {
                throw getErrnoAndThrowPosixException();
            }
        } finally {
            NativeMemory.free(pathPtr);
        }
    }

    @ExportMessage
    public void fchown(int fd, long owner, long group) throws PosixException {
        int ret = posixNativeFunctionInvoker.call_fchown(fd, owner, group);
        if (ret != 0) {
            throw getErrnoAndThrowPosixException();
        }
    }

    @ExportMessage
    public Object readlinkat(int dirFd, Object path) throws PosixException {
        Buffer buffer = Buffer.allocate(PATH_MAX.value);
        long pathPtr = pathToNativeCString(path);
        try {
            long nativeBuffer = NativeMemory.mallocByteArrayOrNull(PATH_MAX.value);
            try {
                long n = posixNativeFunctionInvoker.call_readlinkat(dirFd, pathPtr, nativeBuffer, PATH_MAX.value);
                if (n < 0) {
                    throw getErrnoAndThrowPosixException();
                }
                NativeMemory.readByteArrayElements(nativeBuffer, 0, buffer.data, 0, (int) n);
                return buffer.withLength(n);
            } finally {
                NativeMemory.free(nativeBuffer);
            }
        } finally {
            NativeMemory.free(pathPtr);
        }
    }

    @ExportMessage
    public void kill(long pid, int signal) throws PosixException {
        int res = posixNativeFunctionInvoker.call_kill(pid, signal);
        if (res == -1) {
            throw getErrnoAndThrowPosixException();
        }
    }

    @ExportMessage
    public void raise(int signal) throws PosixException {
        int res = posixNativeFunctionInvoker.call_raise(signal);
        if (res == -1) {
            throw getErrnoAndThrowPosixException();
        }
    }

    @ExportMessage
    public int alarm(int seconds) {
        return posixNativeFunctionInvoker.call_alarm(seconds);
    }

    @ExportMessage
    public Timeval[] getitimer(int which) throws PosixException {
        long nativeCurrentValue = NativeMemory.mallocLongArray(4);
        try {
            int res = posixNativeFunctionInvoker.call_getitimer(which, nativeCurrentValue);
            if (res == -1) {
                throw getErrnoAndThrowPosixException();
            }
            return unwrapTimeval(nativeCurrentValue);
        } finally {
            NativeMemory.free(nativeCurrentValue);
        }
    }

    @ExportMessage
    public Timeval[] setitimer(int which, Timeval delay, Timeval interval) throws PosixException {
        long nativeNewValue = NULLPTR;
        long nativeOldValue = NULLPTR;
        try {
            nativeNewValue = wrapItimerval(delay, interval);
            nativeOldValue = NativeMemory.mallocLongArray(4);
            int res = posixNativeFunctionInvoker.call_setitimer(which, nativeNewValue, nativeOldValue);
            if (res == -1) {
                throw getErrnoAndThrowPosixException();
            }
            return unwrapTimeval(nativeOldValue);
        } finally {
            NativeMemory.free(nativeOldValue);
            NativeMemory.free(nativeNewValue);
        }
    }

    @ExportMessage
    public void signalSelf(int signal) throws PosixException {
        if (!ImageInfo.inImageRuntimeCode()) {
            throw new UnsupportedPosixFeatureException("self-signals are only supported in native standalone");
        }
        int res = posixNativeFunctionInvoker.signal_self(signal);
        if (res == -1) {
            throw getErrnoAndThrowPosixException();
        }
    }

    @ExportMessage
    public void killpg(long pgid, int signal) throws PosixException {
        int res = posixNativeFunctionInvoker.call_killpg(pgid, signal);
        if (res == -1) {
            throw getErrnoAndThrowPosixException();
        }
    }

    @ExportMessage
    public long[] waitpid(long pid, int options,
                    @Bind Node node) throws PosixException {
        boolean hasNohang = (options & WNOHANG.getValueIfDefined()) != 0;
        int subOptions = options | WNOHANG.getValueIfDefined();
        long nativeStatus = NativeMemory.callocIntArray(1);
        try {
            long res = posixNativeFunctionInvoker.call_waitpid(pid, nativeStatus, subOptions);
            while (res == 0 && !hasNohang) {
                TruffleSafepoint.setBlockedThreadInterruptible(node, (ignored) -> {
                    Thread.sleep(20);
                }, null);
                res = posixNativeFunctionInvoker.call_waitpid(pid, nativeStatus, subOptions);
            }
            if (res < 0) {
                throw getErrnoAndThrowPosixException();
            }
            return new long[]{res, NativeMemory.readInt(nativeStatus)};
        } finally {
            NativeMemory.free(nativeStatus);
        }
    }

    @ExportMessage
    public boolean wcoredump(int status) {
        return posixNativeFunctionInvoker.call_wcoredump(status) != 0;
    }

    @ExportMessage
    public boolean wifcontinued(int status) {
        return posixNativeFunctionInvoker.call_wifcontinued(status) != 0;
    }

    @ExportMessage
    public boolean wifstopped(int status) {
        return posixNativeFunctionInvoker.call_wifstopped(status) != 0;
    }

    @ExportMessage
    public boolean wifsignaled(int status) {
        return posixNativeFunctionInvoker.call_wifsignaled(status) != 0;
    }

    @ExportMessage
    public boolean wifexited(int status) {
        return posixNativeFunctionInvoker.call_wifexited(status) != 0;
    }

    @ExportMessage
    public int wexitstatus(int status) {
        return posixNativeFunctionInvoker.call_wexitstatus(status);
    }

    @ExportMessage
    public int wtermsig(int status) {
        return posixNativeFunctionInvoker.call_wtermsig(status);
    }

    @ExportMessage
    public int wstopsig(int status) {
        return posixNativeFunctionInvoker.call_wstopsig(status);
    }

    @ExportMessage
    public long getuid() {
        return posixNativeFunctionInvoker.call_getuid();
    }

    @ExportMessage
    public long geteuid() {
        return posixNativeFunctionInvoker.call_geteuid();
    }

    @ExportMessage
    public long getgid() {
        return posixNativeFunctionInvoker.call_getgid();
    }

    @ExportMessage
    public long getegid() {
        return posixNativeFunctionInvoker.call_getegid();
    }

    @ExportMessage
    public long getppid() {
        return posixNativeFunctionInvoker.call_getppid();
    }

    @ExportMessage
    public void setpgid(long pid, long pgid) throws PosixException {
        int res = posixNativeFunctionInvoker.call_setpgid(pid, pgid);
        if (res < 0) {
            throw getErrnoAndThrowPosixException();
        }
    }

    @ExportMessage
    public long getpgid(long pid) throws PosixException {
        long res = posixNativeFunctionInvoker.call_getpgid(pid);
        if (res < 0) {
            throw getErrnoAndThrowPosixException();
        }
        return res;
    }

    @ExportMessage
    public long getpgrp() {
        return posixNativeFunctionInvoker.call_getpgrp();
    }

    @ExportMessage
    public long getsid(long pid) throws PosixException {
        long res = posixNativeFunctionInvoker.call_getsid(pid);
        if (res < 0) {
            throw getErrnoAndThrowPosixException();
        }
        return res;
    }

    @ExportMessage
    public long setsid() throws PosixException {
        long res = posixNativeFunctionInvoker.call_setsid();
        if (res < 0) {
            throw getErrnoAndThrowPosixException();
        }
        return res;
    }

    @ExportMessage
    public long[] getgroups() throws PosixException {
        // The first call gets us the number of groups, so we can allocate the output array
        int res = posixNativeFunctionInvoker.call_getgroups(0, NULLPTR);
        if (res < 0) {
            throw getErrnoAndThrowPosixException();
        }
        if (res == 0) {
            return EMPTY_LONG_ARRAY;
        }
        long[] groups = new long[res];
        long nativeGroups = NativeMemory.mallocLongArray(groups.length);
        try {
            res = posixNativeFunctionInvoker.call_getgroups(groups.length, nativeGroups);
            if (res < 0) {
                throw getErrnoAndThrowPosixException();
            }
            NativeMemory.readLongArrayElements(nativeGroups, 0, groups, 0, groups.length);
            return groups;
        } finally {
            NativeMemory.free(nativeGroups);
        }
    }

    @ExportMessage
    public RusageResult getrusage(int who) throws PosixException {
        long nativeResult = NativeMemory.mallocLongArray(16);
        try {
            int res = posixNativeFunctionInvoker.call_getrusage(who, nativeResult);
            if (res < 0) {
                throw getErrnoAndThrowPosixException();
            }
            return new RusageResult(
                            Double.longBitsToDouble(NativeMemory.readLongArrayElement(nativeResult, 0)),
                            Double.longBitsToDouble(NativeMemory.readLongArrayElement(nativeResult, 1)),
                            NativeMemory.readLongArrayElement(nativeResult, 2),
                            NativeMemory.readLongArrayElement(nativeResult, 3),
                            NativeMemory.readLongArrayElement(nativeResult, 4),
                            NativeMemory.readLongArrayElement(nativeResult, 5),
                            NativeMemory.readLongArrayElement(nativeResult, 6),
                            NativeMemory.readLongArrayElement(nativeResult, 7),
                            NativeMemory.readLongArrayElement(nativeResult, 8),
                            NativeMemory.readLongArrayElement(nativeResult, 9),
                            NativeMemory.readLongArrayElement(nativeResult, 10),
                            NativeMemory.readLongArrayElement(nativeResult, 11),
                            NativeMemory.readLongArrayElement(nativeResult, 12),
                            NativeMemory.readLongArrayElement(nativeResult, 13),
                            NativeMemory.readLongArrayElement(nativeResult, 14),
                            NativeMemory.readLongArrayElement(nativeResult, 15));
        } finally {
            NativeMemory.free(nativeResult);
        }
    }

    @ExportMessage
    public OpenPtyResult openpty() throws PosixException {
        long nativeOutvars = NativeMemory.mallocIntArray(2);
        try {
            int res = posixNativeFunctionInvoker.call_openpty(nativeOutvars);
            if (res == -1) {
                throw getErrnoAndThrowPosixException();
            }
            return new OpenPtyResult(
                            NativeMemory.readIntArrayElement(nativeOutvars, 0),
                            NativeMemory.readIntArrayElement(nativeOutvars, 1));
        } finally {
            NativeMemory.free(nativeOutvars);
        }
    }

    @ExportMessage
    public TruffleString ctermid(
                    @Bind Node inliningTarget,
                    @Shared("cString") @Cached NativeMemory.ZeroTerminatedUtf8ToTruffleStringNode zeroTerminatedUtf8ToTruffleStringNode) throws PosixException {
        long nativeBuf = NativeMemory.mallocByteArray(L_ctermid.value);
        try {
            int res = posixNativeFunctionInvoker.call_ctermid(nativeBuf);
            if (res == -1) {
                throw getErrnoAndThrowPosixException();
            }
            // TODO PyUnicode_DecodeFSDefault
            return zeroTerminatedUtf8ToTruffleStringNode.execute(inliningTarget, nativeBuf);
        } finally {
            NativeMemory.free(nativeBuf);
        }
    }

    @ExportMessage
    public void setenv(Object name, Object value, boolean overwrite) throws PosixException {
        long namePtr = NULLPTR;
        long valuePtr = NULLPTR;
        try {
            namePtr = pathToNativeCString(name);
            valuePtr = pathToNativeCString(value);
            int res = posixNativeFunctionInvoker.call_setenv(namePtr, valuePtr, overwrite ? 1 : 0);
            if (res == -1) {
                throw getErrnoAndThrowPosixException();
            }
        } finally {
            NativeMemory.free(valuePtr);
            NativeMemory.free(namePtr);
        }
    }

    @ExportMessage
    public void unsetenv(Object name) throws PosixException {
        long namePtr = pathToNativeCString(name);
        try {
            int res = posixNativeFunctionInvoker.call_unsetenv(namePtr);
            if (res == -1) {
                throw getErrnoAndThrowPosixException();
            }
        } finally {
            NativeMemory.free(namePtr);
        }
    }

    @ExportMessage
    public int forkExec(Object[] executables, Object[] args, Object cwd, Object[] env, int stdinReadFd, int stdinWriteFd, int stdoutReadFd, int stdoutWriteFd, int stderrReadFd, int stderrWriteFd,
                    int errPipeReadFd, int errPipeWriteFd, boolean closeFds, boolean restoreSignals, boolean callSetsid, int pgidToSet, int[] fdsToKeep, boolean allowVFork) throws PosixException {

        // The following strings and string arrays need to be present in the native function:
        // - char** of executable names ('\0'-terminated strings with an extra NULL at the end)
        // - char** of arguments ('\0'-terminated strings with an extra NULL at the end)
        // - an optional char** of env variables ('\0'-terminated strings with an extra NULL at the
        // end), must distinguish between NULL (child inherits env) and an empty array (child gets
        // empty env)
        // - an optional char* cwd ('\0'-terminated string or NULL)
        // We do this by concatenating all strings (including their terminating '\0' characters)
        // into one large byte buffer (which becomes 'char *') and pass an additional array of
        // offsets to mark where the individual strings begin. To prevent memory allocation
        // in C (and related free()), we reuse this array of integer offsets as an array of
        // C-strings (char **). For this reason, the array of offsets is allocated as long[].
        // In the offsets array we mark the places where NULL should be with a special value -1.
        // All that is left is to let the native function know where in the offsets array the
        // individual string arrays begin:
        // - executable names are always at index 0
        // - argsPos is the index in the offsets array pointing to the first argument
        // - envPos is either -1 or an index in the offsets array pointing to the first env string
        // - cwdPos is either -1 or an index in the offsets array pointing to the cwd string

        // First we calculate the lengths of the offsets array and the string buffer (dataLen).
        int offsetsLen;
        int argsPos;
        int envPos;
        int cwdPos;
        long dataLen;

        try {
            offsetsLen = executables.length + 1;
            dataLen = addLengthsOfCStrings(0, executables);

            argsPos = offsetsLen;
            offsetsLen += args.length + 1;
            dataLen = addLengthsOfCStrings(dataLen, args);

            if (env != null) {
                envPos = offsetsLen;
                offsetsLen += env.length + 1;
                dataLen = addLengthsOfCStrings(dataLen, env);
            } else {
                envPos = -1;
            }

            if (cwd != null) {
                cwdPos = offsetsLen;
                offsetsLen += 1;
                // The +1 in the second argument can overflow only if the buffer contains 2^63-1
                // bytes, which is impossible since we are using Java arrays limited to 2^31-1.
                dataLen = PythonUtils.addExact(dataLen, ((Buffer) cwd).length + 1L);
            } else {
                cwdPos = -1;
            }
        } catch (OverflowException e) {
            throw newPosixException(OSErrorEnum.E2BIG.getNumber());
        }

        // This also guarantees that offsetsLen did not overflow: we add +1 to dataLen for each
        // '\0', i.e. dataLen >= "number of strings" and offsetsLen < "number of strings" + 3
        // (3 accounts for the NULL terminating the executables, args and env arrays).
        if (dataLen >= Integer.MAX_VALUE - 3) {
            throw newPosixException(OSErrorEnum.E2BIG.getNumber());
        }

        byte[] data = new byte[(int) dataLen];
        long[] offsets = new long[offsetsLen];
        long offset = 0;

        offset = encodeCStringArray(data, offset, offsets, 0, executables);
        offset = encodeCStringArray(data, offset, offsets, argsPos, args);
        if (env != null) {
            offset = encodeCStringArray(data, offset, offsets, envPos, env);
        }
        if (cwd != null) {
            Buffer buf = (Buffer) cwd;
            int strLen = (int) buf.length;
            PythonUtils.arraycopy(buf.data, 0, data, (int) offset, strLen);
            offsets[cwdPos] = offset;
            offset += strLen + 1L;
        }
        assert offset == dataLen;

        long nativeData = NULLPTR;
        long nativeOffsets = NULLPTR;
        long nativeFdsToKeep = NULLPTR;
        try {
            nativeData = NativeMemory.copyToNativeByteArray(data);
            nativeOffsets = NativeMemory.copyToNativeLongArray(offsets);
            nativeFdsToKeep = NativeMemory.copyToNativeIntArrayOrNull(fdsToKeep);
            int res = posixNativeFunctionInvoker.fork_exec(nativeData, nativeOffsets, offsets.length, argsPos, envPos, cwdPos,
                            stdinReadFd, stdinWriteFd,
                            stdoutReadFd, stdoutWriteFd,
                            stderrReadFd, stderrWriteFd,
                            errPipeReadFd, errPipeWriteFd,
                            closeFds ? 1 : 0,
                            restoreSignals ? 1 : 0,
                            callSetsid ? 1 : 0,
                            pgidToSet,
                            allowVFork ? 1 : 0,
                            nativeFdsToKeep, fdsToKeep.length);
            if (res == -1) {
                throw getErrnoAndThrowPosixException();
            }
            return res;
        } finally {
            NativeMemory.free(nativeFdsToKeep);
            NativeMemory.free(nativeOffsets);
            NativeMemory.free(nativeData);
        }
    }

    @ExportMessage
    public void execv(Object pathname, Object[] args) throws PosixException {

        // The following strings and string arrays need to be present in the native function:
        // - char* - the pathname ('\0'-terminated string)
        // - char** of arguments ('\0'-terminated strings with an extra NULL at the end)
        // We do this by concatenating all strings (including their terminating '\0' characters)
        // into one large byte buffer (which becomes 'char *') and pass an additional array of
        // offsets to mark where the individual strings begin. To prevent memory allocation
        // in C (and related free()), we reuse this array of integer offsets as an array of
        // C-strings (char **). For this reason, the array of offsets is allocated as long[].
        // In the offsets array we mark the places where NULL should be with a special value -1.
        // - the pathname is always at index 0
        // - the arguments start at index 1

        // First we calculate the lengths of the offsets array and the string buffer (dataLen).
        int offsetsLen = 1 + args.length + 1;
        long pathnameLen = ((Buffer) pathname).length;
        long dataLen;

        try {
            // The +1 can overflow only if the buffer contains 2^63-1 bytes, which is impossible
            // since we are using Java arrays limited to 2^31-1.
            dataLen = addLengthsOfCStrings(pathnameLen + 1L, args);
        } catch (OverflowException e) {
            throw newPosixException(OSErrorEnum.E2BIG.getNumber());
        }

        // This also guarantees that offsetsLen did not overflow: we add +1 to dataLen for each
        // '\0', i.e. dataLen >= "number of strings" and offsetsLen == "number of strings" + 1
        // (1 accounts for the NULL terminating the args array).
        // Also, dataLen > pathnameLen, so this check makes sure that the cast of pathnameLen to int
        // below is safe.
        if (dataLen >= Integer.MAX_VALUE - 1) {
            throw newPosixException(OSErrorEnum.E2BIG.getNumber());
        }

        byte[] data = new byte[(int) dataLen];
        long[] offsets = new long[offsetsLen];

        PythonUtils.arraycopy(((Buffer) pathname).data, 0, data, 0, (int) pathnameLen);
        long offset = encodeCStringArray(data, pathnameLen + 1L, offsets, 1, args);
        assert offset == dataLen;

        long nativeData = NULLPTR;
        long nativeOffsets = NULLPTR;
        try {
            nativeData = NativeMemory.copyToNativeByteArray(data);
            nativeOffsets = NativeMemory.copyToNativeLongArray(offsets);
            posixNativeFunctionInvoker.call_execv(nativeData, nativeOffsets, offsets.length);
            throw getErrnoAndThrowPosixException();
        } finally {
            NativeMemory.free(nativeOffsets);
            NativeMemory.free(nativeData);
        }
    }

    @ExportMessage
    public int system(Object command) {
        long commandPtr = pathToNativeCString(command);
        try {
            return posixNativeFunctionInvoker.call_system(commandPtr);
        } finally {
            NativeMemory.free(commandPtr);
        }
    }

    private static long addLengthsOfCStrings(long prevLen, Object[] src) throws OverflowException {
        long len = prevLen;
        for (Object o : src) {
            len = PythonUtils.addExact(len, ((Buffer) o).length);
        }
        return PythonUtils.addExact(len, src.length);   // add space for terminating '\0'
    }

    /**
     * Copies null-terminated strings to a buffer {@code data} starting at position {@code offset},
     * and stores the offset of each string to the {@code offsets} array starting at index
     * {@code startPos}.
     */
    private static long encodeCStringArray(byte[] data, long startOffset, long[] offsets, int startPos, Object[] src) {
        // The code that calculates dataLen already checked that there is no overflow and that all
        // offsets fit into an int.
        long offset = startOffset;
        for (int i = 0; i < src.length; ++i) {
            Buffer buf = (Buffer) src[i];
            int strLen = (int) buf.length;
            PythonUtils.arraycopy(buf.data, 0, data, (int) offset, strLen);
            offsets[startPos + i] = offset;
            offset += strLen + 1;       // +1 for the terminating \0 character
        }
        offsets[startPos + src.length] = -1;        // this will become NULL in C (the char* array
        // needs to be terminated by a NULL)
        return offset;
    }

    private static final class MMapHandle {
        private final long pointer;
        private final long length;

        public MMapHandle(long pointer, long length) {
            this.pointer = pointer;
            this.length = length;
        }
    }

    @ExportMessage
    public Object mmap(long length, int prot, int flags, int fd, long offset) throws PosixException {
        long address = posixNativeFunctionInvoker.call_mmap(length, prot, flags, fd, offset);
        if (address == 0) {
            throw getErrnoAndThrowPosixException();
        }
        return new MMapHandle(address, length);
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    public byte mmapReadByte(Object mmap, long index) {
        MMapHandle handle = (MMapHandle) mmap;
        if (index < 0 || index >= handle.length) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new IndexOutOfBoundsException();
        }
        return UNSAFE.getByte(handle.pointer + index);
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    public void mmapWriteByte(Object mmap, long index, byte value) {
        MMapHandle handle = (MMapHandle) mmap;
        checkIndexAndLen(handle, index, 1);
        UNSAFE.putByte(handle.pointer + index, value);
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    public int mmapReadBytes(Object mmap, long index, byte[] bytes, int length) {
        MMapHandle handle = (MMapHandle) mmap;
        checkIndexAndLen(handle, index, length);
        UNSAFE.copyMemory(null, handle.pointer + index, bytes, Unsafe.ARRAY_BYTE_BASE_OFFSET, length);
        return length;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    public void mmapWriteBytes(Object mmap, long index, byte[] bytes, int length) {
        MMapHandle handle = (MMapHandle) mmap;
        checkIndexAndLen(handle, index, length);
        UNSAFE.copyMemory(bytes, Unsafe.ARRAY_BYTE_BASE_OFFSET, null, handle.pointer + index, length);
    }

    @ExportMessage
    public void mmapFlush(Object mmap, long offset, long length) {
        MMapHandle handle = (MMapHandle) mmap;
        checkIndexAndLen(handle, offset, length);
        posixNativeFunctionInvoker.call_msync(handle.pointer, offset, length);
    }

    @ExportMessage
    public void mmapUnmap(Object mmap, long length) throws PosixException {
        MMapHandle handle = (MMapHandle) mmap;
        if (length != handle.length) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new IllegalArgumentException();
        }
        int result = posixNativeFunctionInvoker.call_munmap(handle.pointer, length);
        if (result != 0) {
            throw getErrnoAndThrowPosixException();
        }
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    public long mmapGetPointer(Object mmap) {
        MMapHandle handle = (MMapHandle) mmap;
        return handle.pointer;
    }

    private static void checkIndexAndLen(MMapHandle handle, long index, long length) {
        if (length < 0) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new IllegalArgumentException();
        }
        if (index < 0 || index + length > handle.length) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new IndexOutOfBoundsException();
        }
    }

    @ExportMessage
    public int socket(int domain, int type, int protocol) throws PosixException {
        int result = posixNativeFunctionInvoker.call_socket(domain, type, protocol);
        if (result == -1) {
            throw getErrnoAndThrowPosixException();
        }
        return result;
    }

    @ExportMessage
    public AcceptResult accept(int sockfd) throws PosixException {
        UniversalSockAddrImpl addr = new UniversalSockAddrImpl(this);
        long nativeAddr = NULLPTR;
        long nativeAddrLen = NULLPTR;
        try {
            nativeAddr = NativeMemory.mallocByteArray(addr.data.length);
            nativeAddrLen = NativeMemory.mallocIntArray(1);
            int result = posixNativeFunctionInvoker.call_accept(sockfd, nativeAddr, nativeAddrLen);
            if (result == -1) {
                throw getErrnoAndThrowPosixException();
            }
            readNativeSockAddr(nativeAddr, nativeAddrLen, addr);
            return new AcceptResult(result, addr);
        } finally {
            NativeMemory.free(nativeAddrLen);
            NativeMemory.free(nativeAddr);
        }
    }

    @ExportMessage
    public void bind(int sockfd, UniversalSockAddr usa) throws PosixException {
        UniversalSockAddrImpl addr = (UniversalSockAddrImpl) usa;
        int addrLen = addr.getLen();
        long nativeAddr = NULLPTR;
        try {
            nativeAddr = NativeMemory.copyToNativeByteArray(addr.data, 0, addrLen);
            int result = posixNativeFunctionInvoker.call_bind(sockfd, nativeAddr, addrLen);
            if (result == -1) {
                throw getErrnoAndThrowPosixException();
            }
        } finally {
            NativeMemory.free(nativeAddr);
        }
    }

    @ExportMessage
    public void connect(int sockfd, UniversalSockAddr usa) throws PosixException {
        UniversalSockAddrImpl addr = (UniversalSockAddrImpl) usa;
        int addrLen = addr.getLen();
        long nativeAddr = NULLPTR;
        try {
            nativeAddr = NativeMemory.copyToNativeByteArray(addr.data, 0, addrLen);
            int result = posixNativeFunctionInvoker.call_connect(sockfd, nativeAddr, addrLen);
            if (result == -1) {
                throw getErrnoAndThrowPosixException();
            }
        } finally {
            NativeMemory.free(nativeAddr);
        }
    }

    @ExportMessage
    public void listen(int sockfd, int backlog) throws PosixException {
        int result = posixNativeFunctionInvoker.call_listen(sockfd, backlog);
        if (result == -1) {
            throw getErrnoAndThrowPosixException();
        }
    }

    @ExportMessage
    public UniversalSockAddr getpeername(int sockfd) throws PosixException {
        UniversalSockAddrImpl addr = new UniversalSockAddrImpl(this);
        long nativeAddr = NULLPTR;
        long nativeAddrLen = NULLPTR;
        try {
            nativeAddr = NativeMemory.mallocByteArray(addr.data.length);
            nativeAddrLen = NativeMemory.mallocIntArray(1);
            int result = posixNativeFunctionInvoker.call_getpeername(sockfd, nativeAddr, nativeAddrLen);
            if (result == -1) {
                throw getErrnoAndThrowPosixException();
            }
            readNativeSockAddr(nativeAddr, nativeAddrLen, addr);
            return addr;
        } finally {
            NativeMemory.free(nativeAddrLen);
            NativeMemory.free(nativeAddr);
        }
    }

    @ExportMessage
    public UniversalSockAddr getsockname(int sockfd) throws PosixException {
        UniversalSockAddrImpl addr = new UniversalSockAddrImpl(this);
        long nativeAddr = NULLPTR;
        long nativeAddrLen = NULLPTR;
        try {
            nativeAddr = NativeMemory.mallocByteArray(addr.data.length);
            nativeAddrLen = NativeMemory.mallocIntArray(1);
            int result = posixNativeFunctionInvoker.call_getsockname(sockfd, nativeAddr, nativeAddrLen);
            if (result == -1) {
                throw getErrnoAndThrowPosixException();
            }
            readNativeSockAddr(nativeAddr, nativeAddrLen, addr);
            return addr;
        } finally {
            NativeMemory.free(nativeAddrLen);
            NativeMemory.free(nativeAddr);
        }
    }

    @ExportMessage
    public int send(int sockfd, byte[] buf, int offset, int len, int flags) throws PosixException {
        checkBounds(buf, offset, len);
        long nativeBuffer = NativeMemory.mallocByteArrayOrNull(len);
        try {
            NativeMemory.writeByteArrayElements(nativeBuffer, 0, buf, offset, len);
            posixNativeFunctionInvoker.set_errno(0);
            int result = posixNativeFunctionInvoker.call_send(sockfd, nativeBuffer, len, flags);
            if (result == -1) {
                throw getErrnoAndThrowPosixException();
            }
            return result;
        } finally {
            NativeMemory.free(nativeBuffer);
        }
    }

    @ExportMessage
    public int sendto(int sockfd, byte[] buf, int offset, int len, int flags, UniversalSockAddr usa) throws PosixException {
        checkBounds(buf, offset, len);
        UniversalSockAddrImpl destAddr = (UniversalSockAddrImpl) usa;
        int destAddrLen = destAddr.getLen();
        long nativeBuffer = NULLPTR;
        long nativeDestAddr = NULLPTR;
        try {
            nativeBuffer = NativeMemory.mallocByteArrayOrNull(len);
            NativeMemory.writeByteArrayElements(nativeBuffer, 0, buf, offset, len);
            nativeDestAddr = NativeMemory.mallocByteArrayOrNull(destAddrLen);
            NativeMemory.writeByteArrayElements(nativeDestAddr, 0, destAddr.data, 0, destAddrLen);
            posixNativeFunctionInvoker.set_errno(0);
            int result = posixNativeFunctionInvoker.call_sendto(sockfd, nativeBuffer, 0, len, flags, nativeDestAddr, destAddrLen);
            if (result == -1) {
                throw getErrnoAndThrowPosixException();
            }
            return result;
        } finally {
            NativeMemory.free(nativeDestAddr);
            NativeMemory.free(nativeBuffer);
        }
    }

    @ExportMessage
    public int recv(int sockfd, byte[] buf, int offset, int len, int flags) throws PosixException {
        checkBounds(buf, offset, len);
        long nativeBuffer = NativeMemory.mallocByteArrayOrNull(len);
        try {
            posixNativeFunctionInvoker.set_errno(0);
            int result = posixNativeFunctionInvoker.call_recv(sockfd, nativeBuffer, len, flags);
            if (result == -1) {
                throw getErrnoAndThrowPosixException();
            }
            NativeMemory.readByteArrayElements(nativeBuffer, 0, buf, offset, result);
            return result;
        } finally {
            NativeMemory.free(nativeBuffer);
        }
    }

    @ExportMessage
    public RecvfromResult recvfrom(int sockfd, byte[] buf, int offset, int len, int flags) throws PosixException {
        checkBounds(buf, offset, len);
        UniversalSockAddrImpl srcAddr = new UniversalSockAddrImpl(this);
        long nativeBuffer = NULLPTR;
        long nativeSrcAddr = NULLPTR;
        long nativeAddrLen = NULLPTR;
        try {
            nativeBuffer = NativeMemory.mallocByteArrayOrNull(len);
            nativeSrcAddr = NativeMemory.mallocByteArray(srcAddr.data.length);
            nativeAddrLen = NativeMemory.mallocIntArray(1);
            posixNativeFunctionInvoker.set_errno(0);
            int result = posixNativeFunctionInvoker.call_recvfrom(sockfd, nativeBuffer, 0, len, flags, nativeSrcAddr, nativeAddrLen);
            if (result == -1) {
                throw getErrnoAndThrowPosixException();
            }
            NativeMemory.readByteArrayElements(nativeBuffer, 0, buf, offset, result);
            readNativeSockAddr(nativeSrcAddr, nativeAddrLen, srcAddr);
            return new RecvfromResult(result, srcAddr);
        } finally {
            NativeMemory.free(nativeAddrLen);
            NativeMemory.free(nativeSrcAddr);
            NativeMemory.free(nativeBuffer);
        }
    }

    @ExportMessage
    public void shutdown(int sockfd, int how) throws PosixException {
        int res = posixNativeFunctionInvoker.call_shutdown(sockfd, how);
        if (res != 0) {
            throw getErrnoAndThrowPosixException();
        }
    }

    @ExportMessage
    public int getsockopt(int sockfd, int level, int optname, byte[] optval, int optlen) throws PosixException {
        assert optlen >= 0 && optval.length >= optlen;
        long nativeOptval = NULLPTR;
        long nativeBufLen = NULLPTR;
        try {
            nativeOptval = NativeMemory.mallocByteArray(Math.max(optlen, 1));
            nativeBufLen = NativeMemory.mallocIntArray(1);
            NativeMemory.writeInt(nativeBufLen, optlen);
            int res = posixNativeFunctionInvoker.call_getsockopt(sockfd, level, optname, nativeOptval, nativeBufLen);
            if (res != 0) {
                throw getErrnoAndThrowPosixException();
            }
            int actualLen = NativeMemory.readInt(nativeBufLen);
            if (actualLen < 0 || actualLen > optval.length) {
                throw CompilerDirectives.shouldNotReachHere("Unexpected socket option length in getsockopt");
            }
            if (actualLen > 0) {
                NativeMemory.readByteArrayElements(nativeOptval, 0, optval, 0, actualLen);
            }
            return actualLen;
        } finally {
            NativeMemory.free(nativeBufLen);
            NativeMemory.free(nativeOptval);
        }
    }

    @ExportMessage
    public void setsockopt(int sockfd, int level, int optname, byte[] optval, int optlen) throws PosixException {
        assert optlen >= 0 && optval.length >= optlen;
        long nativeOptval = NULLPTR;
        try {
            nativeOptval = NativeMemory.copyToNativeByteArrayOrNull(optval, 0, optlen);
            int res = posixNativeFunctionInvoker.call_setsockopt(sockfd, level, optname, nativeOptval, optlen);
            if (res != 0) {
                throw getErrnoAndThrowPosixException();
            }
        } finally {
            NativeMemory.free(nativeOptval);
        }
    }

    @ExportMessage
    public int inet_addr(Object src) {
        long srcPtr = pathToNativeCString(src);
        try {
            return posixNativeFunctionInvoker.call_inet_addr(srcPtr);
        } finally {
            NativeMemory.free(srcPtr);
        }
    }

    @ExportMessage
    public int inet_aton(Object src) throws InvalidAddressException {
        long srcPtr = pathToNativeCString(src);
        try {
            long r = posixNativeFunctionInvoker.call_inet_aton(srcPtr);
            if (r < 0) {
                throw new InvalidAddressException();
            }
            return (int) r;
        } finally {
            NativeMemory.free(srcPtr);
        }
    }

    @ExportMessage
    public Object inet_ntoa(int src) {
        Buffer buf = Buffer.allocate(INET_ADDRSTRLEN.value);
        long nativeBuf = NativeMemory.mallocByteArray(INET_ADDRSTRLEN.value);
        try {
            int len = posixNativeFunctionInvoker.call_inet_ntoa(src, nativeBuf);
            if (len > 0) {
                NativeMemory.readByteArrayElements(nativeBuf, 0, buf.data, 0, len);
            }
            return buf.withLength(len);
        } finally {
            NativeMemory.free(nativeBuf);
        }
    }

    @ExportMessage
    public byte[] inet_pton(int family, Object src) throws PosixException, InvalidAddressException {
        byte[] buf = new byte[family == AF_INET.value ? 4 : 16];
        long srcPtr = NULLPTR;
        long nativeBuf = NULLPTR;
        try {
            srcPtr = pathToNativeCString(src);
            nativeBuf = NativeMemory.mallocByteArray(buf.length);
            int res = posixNativeFunctionInvoker.call_inet_pton(family, srcPtr, nativeBuf);
            // Rather unusually, the return value of 0 does not indicate success but is used by
            // inet_pton to report invalid format of the address (without setting errno).
            // Success is reported by returning 1.
            if (res == 1) {
                NativeMemory.readByteArrayElements(nativeBuf, 0, buf, 0, buf.length);
                return buf;
            }
            if (res == 0) {
                throw new InvalidAddressException();
            }
            throw getErrnoAndThrowPosixException();
        } finally {
            NativeMemory.free(nativeBuf);
            NativeMemory.free(srcPtr);
        }
    }

    @ExportMessage
    public Object inet_ntop(int family, byte[] src) throws PosixException {
        if ((family == AF_INET.value && src.length < 4) || (family == AF_INET6.value && src.length < 16)) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new IllegalArgumentException("Invalid length of IPv4/6 address");
        }
        Buffer buf = Buffer.allocate(INET6_ADDRSTRLEN.value);
        long nativeSrc = NULLPTR;
        long nativeBuf = NULLPTR;
        try {
            nativeSrc = NativeMemory.copyToNativeByteArray(src);
            nativeBuf = NativeMemory.mallocByteArray(INET6_ADDRSTRLEN.value);
            int res = posixNativeFunctionInvoker.call_inet_ntop(family, nativeSrc, nativeBuf, INET6_ADDRSTRLEN.value);
            if (res < 0) {
                throw getErrnoAndThrowPosixException();
            }
            NativeMemory.readByteArrayElements(nativeBuf, 0, buf.data, 0, buf.data.length);
            return buf.withLength(findZero(buf.data));
        } finally {
            NativeMemory.free(nativeBuf);
            NativeMemory.free(nativeSrc);
        }
    }

    @ExportMessage
    public Object gethostname() throws PosixException {
        int maxLen = (HOST_NAME_MAX.defined ? HOST_NAME_MAX.getValueIfDefined() : _POSIX_HOST_NAME_MAX.value) + 1;
        Buffer buf = Buffer.allocate(maxLen);
        long nativeBuf = NativeMemory.mallocByteArray(maxLen);
        try {
            int res = posixNativeFunctionInvoker.call_gethostname(nativeBuf, maxLen);
            if (res != 0) {
                throw getErrnoAndThrowPosixException();
            }
            NativeMemory.readByteArrayElements(nativeBuf, 0, buf.data, 0, buf.data.length);
            return buf.withLength(findZero(buf.data));
        } finally {
            NativeMemory.free(nativeBuf);
        }
    }

    @ExportMessage
    public Object[] getnameinfo(UniversalSockAddr usa, int flags,
                    @Bind Node inliningTarget,
                    @Shared("cString") @Cached NativeMemory.ZeroTerminatedUtf8ToTruffleStringNode zeroTerminatedUtf8ToTruffleStringNode) throws GetAddrInfoException {
        Buffer host = Buffer.allocate(NI_MAXHOST.value);
        Buffer serv = Buffer.allocate(NI_MAXSERV.value);
        UniversalSockAddrImpl addr = (UniversalSockAddrImpl) usa;
        long nativeAddr = NULLPTR;
        long nativeHost = NULLPTR;
        long nativeServ = NULLPTR;
        try {
            nativeAddr = NativeMemory.copyToNativeByteArray(addr.data, 0, addr.getLen());
            nativeHost = NativeMemory.mallocByteArray(NI_MAXHOST.value);
            nativeServ = NativeMemory.mallocByteArray(NI_MAXSERV.value);
            int res = posixNativeFunctionInvoker.call_getnameinfo(nativeAddr, addr.getLen(), nativeHost, NI_MAXHOST.value, nativeServ, NI_MAXSERV.value, flags);
            if (res != 0) {
                throw new GetAddrInfoException(res, gai_strerror(inliningTarget, res, zeroTerminatedUtf8ToTruffleStringNode));
            }
            NativeMemory.readByteArrayElements(nativeHost, 0, host.data, 0, host.data.length);
            NativeMemory.readByteArrayElements(nativeServ, 0, serv.data, 0, serv.data.length);
            return new Object[]{
                            host.withLength(findZero(host.data)),
                            serv.withLength(findZero(serv.data)),
            };
        } finally {
            NativeMemory.free(nativeServ);
            NativeMemory.free(nativeHost);
            NativeMemory.free(nativeAddr);
        }
    }

    @ExportMessage
    public AddrInfoCursor getaddrinfo(Object node, Object service, int family, int sockType, int protocol, int flags,
                    @Bind Node inliningTarget,
                    @Shared("cString") @Cached NativeMemory.ZeroTerminatedUtf8ToTruffleStringNode zeroTerminatedUtf8ToTruffleStringNode) throws GetAddrInfoException {
        long nodePtr = NULLPTR;
        long servicePtr = NULLPTR;
        long nativePtr = NULLPTR;
        try {
            nodePtr = pathToNativeCStringOrNull(node);
            servicePtr = pathToNativeCStringOrNull(service);
            nativePtr = NativeMemory.mallocLongArray(1);
            int res = posixNativeFunctionInvoker.call_getaddrinfo(nodePtr, servicePtr, family, sockType, protocol, flags, nativePtr);
            if (res != 0) {
                throw new GetAddrInfoException(res, gai_strerror(inliningTarget, res, zeroTerminatedUtf8ToTruffleStringNode));
            }
            long head = NativeMemory.readLong(nativePtr);
            assert head != 0;     // getaddrinfo should return at least one result
            return new AddrInfoCursorImpl(this, head);
        } finally {
            NativeMemory.free(nativePtr);
            NativeMemory.free(servicePtr);
            NativeMemory.free(nodePtr);
        }
    }

    @ExportMessage
    public TruffleString crypt(TruffleString word, TruffleString salt,
                    @Bind Node raisingNode,
                    @Exclusive @Cached TruffleString.SwitchEncodingNode switchEncodingToUtf8Node,
                    @Exclusive @Cached TruffleString.CopyToByteArrayNode copyToByteArrayNode,
                    @Exclusive @Cached NativeMemory.ZeroTerminatedUtf8ToTruffleStringNode zeroTerminatedUtf8ToTruffleStringNode) throws PosixException {
        /*
         * From the manpage: Upon successful completion, crypt returns a pointer to a string which
         * encodes both the hashed passphrase, and the settings that were used to encode it. See
         * crypt(5) for more detail on the format of hashed passphrases. crypt places its result in
         * a static storage area, which will be overwritten by subsequent calls to crypt. It is not
         * safe to call crypt from multiple threads simultaneously. Upon error, it may return a NULL
         * pointer or a pointer to an invalid hash, depending on the implementation.
         */
        long wordPtr = NULLPTR;
        long saltPtr = NULLPTR;
        try {
            wordPtr = stringToNativeUTF8CString(word, switchEncodingToUtf8Node, copyToByteArrayNode);
            saltPtr = stringToNativeUTF8CString(salt, switchEncodingToUtf8Node, copyToByteArrayNode);
            // Note GIL is not enough as crypt is using global memory, we need a really global lock
            synchronized (CRYPT_LOCK) {
                long resultPtr;
                try {
                    resultPtr = cryptNativeFunctionInvoker.crypt(wordPtr, saltPtr);
                } catch (UnsupportedOperationException e) {
                    // Thrown by the generated invoker when CryptNativeFunction.loadNativeLibrary
                    // fails during its lazy library initialization path.
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw PRaiseNode.raiseStatic(raisingNode, PythonBuiltinClassType.SystemError, ErrorMessages.UNABLE_TO_LOAD_LIBCRYPT);
                }
                // CPython doesn't handle the case of "invalid hash" return specially and neither do
                // we
                if (resultPtr == 0) {
                    throw getErrnoAndThrowPosixException();
                }
                return zeroTerminatedUtf8ToTruffleStringNode.execute(raisingNode, resultPtr);
            }
        } finally {
            if (wordPtr != NULLPTR) {
                NativeMemory.free(wordPtr);
            }
            if (saltPtr != NULLPTR) {
                NativeMemory.free(saltPtr);
            }
        }
    }

    private TruffleString gai_strerror(Node inliningTarget, int errorCode, NativeMemory.ZeroTerminatedUtf8ToTruffleStringNode zeroTerminatedUtf8ToTruffleStringNode) {
        long nativeBuf = NativeMemory.mallocByteArray(1024);
        try {
            posixNativeFunctionInvoker.call_gai_strerror(errorCode, nativeBuf, 1024);
            // TODO PyUnicode_DecodeLocale
            return zeroTerminatedUtf8ToTruffleStringNode.execute(inliningTarget, nativeBuf);
        } finally {
            NativeMemory.free(nativeBuf);
        }
    }

    /**
     * Provides access to {@code struct addrinfo}.
     *
     * The layout of native {@code struct addrinfo} is as follows:
     *
     * <pre>
     * {@code
     *     struct addrinfo {
     *         int              ai_flags;           // intData[0]
     *         int              ai_family;          // intData[1]
     *         int              ai_socktype;        // intData[2]
     *         int              ai_protocol;        // intData[3]
     *         socklen_t        ai_addrlen;         // intData[4]
     *         struct sockaddr *ai_addr;            // data copied into socketAddress[]
     *         char            *ai_canonname;       // longData[0]
     *         struct addrinfo *ai_next;            // longData[1]
     *     };
     * }
     * </pre>
     *
     * To avoid multiple native calls, we transfer the data in batch using arrays of {@code int}s and
     * {@code long}s - int values are stored in {@code intData}, the {@code ai_canonname} and
     * {@code ai_next} pointers are stored in {@code longData} and the socket address pointed to by
     * {@code ai_addr} is copied into Java byte array {@code socketAddress}. We also cache two
     * additional integers:
     * <ul>
     * <li>{@code intData[5]} contains {@code ai_addr->sa_family},</li>
     * <li>{@code intData[6]} contains the length of {@code ai_canonname} if it is not {@code null}
     * </li>
     * </ul>
     *
     * It is not clear whether it is guaranteed that {@code ai_family} and
     * {@code ai_addr->sa_family} are always the same. We provide both and use the later when
     * decoding the socket address.
     */
    private static class AddrInfo {
        private final int[] intData = new int[7];
        private final long[] longData = new long[2];
        private byte[] socketAddress;

        private void update(long ptr, NativePosixSupport nativePosixSupport) {
            socketAddress = new byte[(int) nativePosixSupport.getConstant(SIZEOF_STRUCT_SOCKADDR_STORAGE)];
            long nativeIntData = NULLPTR;
            long nativeLongData = NULLPTR;
            long nativeSocketAddress = NULLPTR;
            try {
                nativeIntData = NativeMemory.mallocIntArray(intData.length);
                nativeLongData = NativeMemory.mallocLongArray(longData.length);
                nativeSocketAddress = NativeMemory.mallocByteArray(socketAddress.length);
                int res = nativePosixSupport.posixNativeFunctionInvoker.get_addrinfo_members(ptr, nativeIntData, nativeLongData, nativeSocketAddress);
                if (res != 0) {
                    throw shouldNotReachHere("the length of ai_canonname does not fit into an int");
                }
                NativeMemory.readIntArrayElements(nativeIntData, 0, intData, 0, intData.length);
                NativeMemory.readLongArrayElements(nativeLongData, 0, longData, 0, longData.length);
                NativeMemory.readByteArrayElements(nativeSocketAddress, 0, socketAddress, 0, socketAddress.length);
            } finally {
                NativeMemory.free(nativeSocketAddress);
                NativeMemory.free(nativeLongData);
                NativeMemory.free(nativeIntData);
            }
        }

        int getFlags() {
            return intData[0];
        }

        int getFamily() {
            return intData[1];
        }

        int getSockType() {
            return intData[2];
        }

        int getProtocol() {
            return intData[3];
        }

        int getAddrLen() {
            return intData[4];
        }

        int getAddrFamily() {
            return intData[5];
        }

        int getCanonNameLen() {
            assert getCanonNamePtr() != 0;
            return intData[6];
        }

        long getCanonNamePtr() {
            return longData[0];
        }

        long getNextPtr() {
            return longData[1];
        }
    }

    @ExportLibrary(AddrInfoCursorLibrary.class)
    protected static class AddrInfoCursorImpl implements AddrInfoCursor {

        private final NativePosixSupport nativePosixSupport;
        private long head;
        private AddrInfo info;

        AddrInfoCursorImpl(NativePosixSupport nativePosixSupport, long head) {
            this.nativePosixSupport = nativePosixSupport;
            this.head = head;
            info = new AddrInfo();
            info.update(head, nativePosixSupport);
        }

        @ExportMessage
        void release() {
            checkReleased();
            nativePosixSupport.posixNativeFunctionInvoker.call_freeaddrinfo(head);
            head = 0;
        }

        @ExportMessage
        boolean next() {
            checkReleased();
            long nextPtr = info.getNextPtr();
            if (nextPtr == 0) {
                return false;
            }
            info.update(nextPtr, nativePosixSupport);
            return true;
        }

        @ExportMessage
        int getFlags() {
            checkReleased();
            return info.getFlags();
        }

        @ExportMessage
        int getFamily() {
            checkReleased();
            return info.getFamily();
        }

        @ExportMessage
        int getSockType() {
            checkReleased();
            return info.getSockType();
        }

        @ExportMessage
        int getProtocol() {
            checkReleased();
            return info.getProtocol();
        }

        @ExportMessage
        Object getCanonName() {
            checkReleased();
            long namePtr = info.getCanonNamePtr();
            if (namePtr == 0) {
                return null;
            }
            int nameLen = info.getCanonNameLen();
            byte[] buf = new byte[nameLen];
            UNSAFE.copyMemory(null, namePtr, buf, Unsafe.ARRAY_BYTE_BASE_OFFSET, nameLen);
            return Buffer.wrap(buf);
        }

        @ExportMessage
        UniversalSockAddr getSockAddr() {
            UniversalSockAddrImpl addr = new UniversalSockAddrImpl(nativePosixSupport);
            PythonUtils.arraycopy(info.socketAddress, 0, addr.data, 0, info.getAddrLen());
            addr.setFamily(info.getAddrFamily());
            addr.setLen(info.getAddrLen());
            return addr;
        }

        private void checkReleased() {
            if (head == 0) {
                throw shouldNotReachHere("AddrInfoCursor has already been released");
            }
        }
    }

    @ExportMessage
    UniversalSockAddr createUniversalSockAddrInet4(Inet4SockAddr src) {
        UniversalSockAddrImpl addr = new UniversalSockAddrImpl(this);
        addr.setFamily(AF_INET.value);
        ARRAY_ACCESSOR_BE.putShort(addr.data, getConstant(OFFSETOF_STRUCT_SOCKADDR_IN_SIN_PORT), (short) src.getPort());
        ARRAY_ACCESSOR_BE.putInt(addr.data, getConstant(OFFSETOF_STRUCT_SOCKADDR_IN_SIN_ADDR) + getConstant(OFFSETOF_STRUCT_IN_ADDR_S_ADDR), src.getAddress());
        addr.setLen((int) getConstant(SIZEOF_STRUCT_SOCKADDR_IN));
        return addr;
    }

    @ExportMessage
    UniversalSockAddr createUniversalSockAddrInet6(Inet6SockAddr src) {
        UniversalSockAddrImpl addr = new UniversalSockAddrImpl(this);
        addr.setFamily(AF_INET6.value);
        ARRAY_ACCESSOR_BE.putShort(addr.data, getConstant(OFFSETOF_STRUCT_SOCKADDR_IN6_SIN6_PORT), (short) src.getPort());
        int addrOffset = (int) getConstant(OFFSETOF_STRUCT_SOCKADDR_IN6_SIN6_ADDR) + (int) getConstant(OFFSETOF_STRUCT_IN6_ADDR_S6_ADDR);
        PythonUtils.arraycopy(src.getAddress(), 0, addr.data, addrOffset, 16);
        ARRAY_ACCESSOR_BE.putInt(addr.data, getConstant(OFFSETOF_STRUCT_SOCKADDR_IN6_SIN6_FLOWINFO), src.getFlowInfo());
        ARRAY_ACCESSOR_BE.putInt(addr.data, getConstant(OFFSETOF_STRUCT_SOCKADDR_IN6_SIN6_SCOPE_ID), src.getScopeId());
        addr.setLen((int) getConstant(SIZEOF_STRUCT_SOCKADDR_IN6));
        return addr;
    }

    @ExportMessage
    UniversalSockAddr createUniversalSockAddrUnix(UnixSockAddr src) throws InvalidUnixSocketPathException {
        UniversalSockAddrImpl addr = new UniversalSockAddrImpl(this);
        addr.setFamily(AF_UNIX.value);
        byte[] path = src.getPath();
        if (path.length > getConstant(SIZEOF_STRUCT_SOCKADDR_UN_SUN_PATH)) {
            throw InvalidUnixSocketPathException.INSTANCE;
        }
        int len = path.length + (int) getConstant(OFFSETOF_STRUCT_SOCKADDR_UN_SUN_PATH);
        PythonUtils.arraycopy(path, 0, addr.data, (int) getConstant(OFFSETOF_STRUCT_SOCKADDR_UN_SUN_PATH), path.length);
        addr.setLen(len);
        return addr;
    }

    @ExportLibrary(UniversalSockAddrLibrary.class)
    protected static class UniversalSockAddrImpl implements UniversalSockAddr {

        private final NativePosixSupport nativePosixSupport;
        private final byte[] data;
        private int len = 0;

        UniversalSockAddrImpl(NativePosixSupport nativePosixSupport) {
            this.nativePosixSupport = nativePosixSupport;
            this.data = new byte[(int) getConstant(SIZEOF_STRUCT_SOCKADDR_STORAGE)];
        }

        @ExportMessage
        int getFamily() {
            int offset = (int) getConstant(OFFSETOF_STRUCT_SOCKADDR_SA_FAMILY);
            int size = (int) getConstant(SIZEOF_STRUCT_SOCKADDR_SA_FAMILY);
            if (getLen() >= offset + size) {
                if (size == 1) {
                    return Byte.toUnsignedInt(data[offset]);
                } else if (size == 2) {
                    return Short.toUnsignedInt(ARRAY_ACCESSOR.getShort(data, offset));
                } else if (size == 4) {
                    return ARRAY_ACCESSOR.getInt(data, offset);
                } else {
                    throw CompilerDirectives.shouldNotReachHere("Unexpected sizeof(sa_family_t)");
                }
            } else {
                return AF_UNSPEC.value;
            }
        }

        void setFamily(int family) {
            int offset = (int) getConstant(OFFSETOF_STRUCT_SOCKADDR_SA_FAMILY);
            int size = (int) getConstant(SIZEOF_STRUCT_SOCKADDR_SA_FAMILY);
            if (size == 1) {
                data[offset] = (byte) family;
            } else if (size == 2) {
                ARRAY_ACCESSOR.putShort(data, offset, (short) family);
            } else if (size == 4) {
                ARRAY_ACCESSOR.putInt(data, offset, family);
            } else {
                throw CompilerDirectives.shouldNotReachHere("Unexpected sizeof(sa_family_t)");
            }
        }

        @ExportMessage
        Inet4SockAddr asInet4SockAddr() {
            if (getFamily() != AF_INET.value) {
                throw CompilerDirectives.shouldNotReachHere("Only AF_INET socket address can be converted to Inet4SockAddr");
            }
            if (getLen() != getConstant(SIZEOF_STRUCT_SOCKADDR_IN)) {
                throw CompilerDirectives.shouldNotReachHere("Wrong size of socket addr struct in asInet4SockAddr");
            }
            int port = Short.toUnsignedInt(ARRAY_ACCESSOR_BE.getShort(data, getConstant(OFFSETOF_STRUCT_SOCKADDR_IN_SIN_PORT)));
            int address = ARRAY_ACCESSOR_BE.getInt(data, getConstant(OFFSETOF_STRUCT_SOCKADDR_IN_SIN_ADDR) + getConstant(OFFSETOF_STRUCT_IN_ADDR_S_ADDR));
            return new Inet4SockAddr(port, address);
        }

        @ExportMessage
        Inet6SockAddr asInet6SockAddr() {
            if (getFamily() != AF_INET6.value) {
                throw CompilerDirectives.shouldNotReachHere("Only AF_INET6 socket address can be converted to Inet6SockAddr");
            }
            if (getLen() != getConstant(SIZEOF_STRUCT_SOCKADDR_IN6)) {
                throw CompilerDirectives.shouldNotReachHere("Wrong size of socket addr struct in asInet6SockAddr");
            }
            int port = Short.toUnsignedInt(ARRAY_ACCESSOR_BE.getShort(data, getConstant(OFFSETOF_STRUCT_SOCKADDR_IN6_SIN6_PORT)));
            int addrOffset = (int) getConstant(OFFSETOF_STRUCT_SOCKADDR_IN6_SIN6_ADDR) + (int) getConstant(OFFSETOF_STRUCT_IN6_ADDR_S6_ADDR);
            byte[] address = PythonUtils.arrayCopyOfRange(data, addrOffset, addrOffset + 16);
            int flowInfo = ARRAY_ACCESSOR_BE.getInt(data, getConstant(OFFSETOF_STRUCT_SOCKADDR_IN6_SIN6_FLOWINFO));
            int scopeId = ARRAY_ACCESSOR_BE.getInt(data, getConstant(OFFSETOF_STRUCT_SOCKADDR_IN6_SIN6_SCOPE_ID));
            return new Inet6SockAddr(port, address, flowInfo, scopeId);
        }

        @ExportMessage
        UnixSockAddr asUnixSockAddr() {
            if (getFamily() != AF_UNIX.value) {
                throw CompilerDirectives.shouldNotReachHere("Only AF_UNIX socket address can be converted to UnixSockAddr");
            }
            int pathOffset = (int) getConstant(OFFSETOF_STRUCT_SOCKADDR_UN_SUN_PATH);
            byte[] pathBuf;
            int linuxAddrLen = getLen() - pathOffset;
            if (linuxAddrLen > 0 && data[pathOffset] == '\0') {
                // Abstract Linux address
                pathBuf = PythonUtils.arrayCopyOfRange(data, pathOffset, pathOffset + linuxAddrLen);
            } else {
                // Regular NULL-terminated string
                int pathLen = ArrayUtils.indexOf(data, pathOffset, data.length, (byte) 0) - pathOffset;
                assert pathLen >= 0;
                pathBuf = PythonUtils.arrayCopyOfRange(data, pathOffset, pathOffset + pathLen);
            }
            return new UnixSockAddr(pathBuf);
        }

        long getConstant(NativePosixConstants constant) {
            return nativePosixSupport.getConstant(constant);
        }

        int getLen() {
            return len;
        }

        void setLen(int len) {
            this.len = len;
        }

    }

    @ExportMessage
    long semOpen(Object name, int openFlags, int mode, int value) throws PosixException {
        long namePtr = pathToNativeCString(name);
        try {
            long ptr = posixNativeFunctionInvoker.call_sem_open(namePtr, openFlags, mode, value);
            if (ptr == NULLPTR) {
                throw getErrnoAndThrowPosixException();
            }
            return ptr;
        } finally {
            NativeMemory.free(namePtr);
        }
    }

    @ExportMessage
    void semClose(long handle) throws PosixException {
        int res = posixNativeFunctionInvoker.call_sem_close(handle);
        if (res < 0) {
            throw getErrnoAndThrowPosixException();
        }
    }

    @ExportMessage
    void semUnlink(Object name) throws PosixException {
        long namePtr = pathToNativeCString(name);
        try {
            int res = posixNativeFunctionInvoker.call_sem_unlink(namePtr);
            if (res < 0) {
                throw getErrnoAndThrowPosixException();
            }
        } finally {
            NativeMemory.free(namePtr);
        }
    }

    private static final UnsupportedPosixFeatureException NO_SEM_GETVALUE_EXCEPTION = new UnsupportedPosixFeatureException("sem_getvalue is not available on the current platform");

    @ExportMessage
    int semGetValue(long handle) throws PosixException {
        /*
         * msimacek: It works on Linux, and it doesn't work on Darwin. It might work on some other
         * Unix-likes, but it's hard to check, so let's assume it only works on Linux for now
         */
        if (PythonLanguage.getPythonOS() != PythonOS.PLATFORM_LINUX) {
            throw NO_SEM_GETVALUE_EXCEPTION;
        }
        long nativeValue = NativeMemory.mallocIntArray(1);
        try {
            int res = posixNativeFunctionInvoker.call_sem_getvalue(handle, nativeValue);
            if (res < 0) {
                throw getErrnoAndThrowPosixException();
            }
            return NativeMemory.readInt(nativeValue);
        } finally {
            NativeMemory.free(nativeValue);
        }
    }

    @ExportMessage
    void semPost(long handle) throws PosixException {
        int res = posixNativeFunctionInvoker.call_sem_post(handle);
        if (res < 0) {
            throw getErrnoAndThrowPosixException();
        }
    }

    @ExportMessage
    void semWait(long handle) throws PosixException {
        int res = posixNativeFunctionInvoker.call_sem_wait(handle);
        if (res < 0) {
            throw getErrnoAndThrowPosixException();
        }
    }

    @ExportMessage
    boolean semTryWait(long handle) throws PosixException {
        int res = posixNativeFunctionInvoker.call_sem_trywait(handle);
        if (res < 0) {
            int errno = posixNativeFunctionInvoker.get_errno();
            if (errno == OSErrorEnum.EAGAIN.getNumber()) {
                return false;
            }
            throw newPosixException(errno);
        }
        return true;
    }

    @ExportMessage
    boolean semTimedWait(long handle, long deadlineNs,
                    @Bind Node node,
                    @CachedLibrary("this") PosixSupportLibrary thisLib) throws PosixException {
        if (PythonLanguage.getPythonOS() == PythonOS.PLATFORM_LINUX) {
            int res = posixNativeFunctionInvoker.call_sem_timedwait(handle, deadlineNs);
            if (res < 0) {
                int errno = posixNativeFunctionInvoker.get_errno();
                if (errno == OSErrorEnum.ETIMEDOUT.getNumber()) {
                    return false;
                }
                throw newPosixException(errno);
            }
            return true;
        } else {
            long deadlineMs = deadlineNs / 1_000_000;
            while (true) {
                if (thisLib.semTryWait(this, handle)) {
                    return true;
                }
                long currentMs = System.currentTimeMillis();
                if (currentMs > deadlineMs) {
                    return false;
                }
                long delayMs = Math.min(deadlineMs - currentMs, 20);
                TruffleSafepoint.setBlockedThreadInterruptible(node, Thread::sleep, delayMs);
            }
        }
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    public PwdResult getpwuid(long uid,
                    @Shared("tsFromBytes") @Cached TruffleString.FromByteArrayNode fromByteArrayNode,
                    @Shared("fromUtf8") @Cached TruffleString.SwitchEncodingNode switchEncodingFromUtf8Node) throws PosixException {
        return getpw(uid, NULLPTR, fromByteArrayNode, switchEncodingFromUtf8Node);
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    public PwdResult getpwnam(Object name,
                    @Shared("tsFromBytes") @Cached TruffleString.FromByteArrayNode fromByteArrayNode,
                    @Shared("fromUtf8") @Cached TruffleString.SwitchEncodingNode switchEncodingFromUtf8Node) throws PosixException {
        long namePtr = pathToNativeCString(name);
        try {
            return getpw(-1, namePtr, fromByteArrayNode, switchEncodingFromUtf8Node);
        } finally {
            NativeMemory.free(namePtr);
        }
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    public boolean hasGetpwentries() {
        return true;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    public PwdResult[] getpwentries(
                    @Shared("tsFromBytes") @Cached TruffleString.FromByteArrayNode fromByteArrayNode,
                    @Shared("fromUtf8") @Cached TruffleString.SwitchEncodingNode switchEncodingFromUtf8Node) throws PosixException {
        // Note: this is not thread safe, so potentially problematic while running multiple contexts
        // within one VM
        int sysConfMax = getSysConfPwdSizeMax();
        int initialBufferSize = sysConfMax == -1 ? 1024 : sysConfMax;

        ArrayList<PwdResult> result = new ArrayList<>();
        posixNativeFunctionInvoker.call_setpwent();
        long nativeBufferSize = NULLPTR;
        long nativeOutput = NULLPTR;
        int currentBufferSize = initialBufferSize;
        long nativeBuffer = NULLPTR;
        try {
            nativeBufferSize = NativeMemory.mallocLongArray(1);
            nativeOutput = NativeMemory.mallocLongArray(PWD_OUTPUT_LEN);
            nativeBuffer = NativeMemory.mallocByteArray(currentBufferSize);
            while (true) {
                long pwPtr = posixNativeFunctionInvoker.call_getpwent(nativeBufferSize);
                if (pwPtr == NULLPTR) {
                    break;
                }
                long bufferSize = NativeMemory.readLong(nativeBufferSize);
                if (bufferSize < 0 || bufferSize > PWD_BUFFER_MAX_SIZE) {
                    throw outOfMemoryPosixError();
                }
                if (currentBufferSize < bufferSize) {
                    NativeMemory.free(nativeBuffer);
                    currentBufferSize = (int) bufferSize;
                    nativeBuffer = NativeMemory.mallocByteArray(currentBufferSize);
                }
                int code = posixNativeFunctionInvoker.get_getpwent_data(pwPtr, nativeBuffer, currentBufferSize, nativeOutput);
                if (code != 0) {
                    throw CompilerDirectives.shouldNotReachHere("get_getpwent_data failed");
                }
                byte[] buffer = new byte[currentBufferSize];
                NativeMemory.readByteArrayElements(nativeBuffer, 0, buffer, 0, buffer.length);
                long[] output = new long[PWD_OUTPUT_LEN];
                NativeMemory.readLongArrayElements(nativeOutput, 0, output, 0, output.length);
                result.add(createPwdResult(buffer, output, fromByteArrayNode, switchEncodingFromUtf8Node));
            }
        } finally {
            posixNativeFunctionInvoker.call_endpwent();
            NativeMemory.free(nativeBuffer);
            NativeMemory.free(nativeOutput);
            NativeMemory.free(nativeBufferSize);
        }
        return toPwdResultArray(result);
    }

    @TruffleBoundary
    private static PwdResult[] toPwdResultArray(ArrayList<PwdResult> result) {
        return result.toArray(new PwdResult[0]);
    }

    private PwdResult getpw(long uid, long namePtr, TruffleString.FromByteArrayNode fromByteArrayNode,
                    TruffleString.SwitchEncodingNode switchEncodingFromUtf8Node) throws PosixException {
        int sysConfMax = getSysConfPwdSizeMax();
        int bufferSize = sysConfMax == -1 ? 1024 : sysConfMax;
        while (bufferSize < PWD_BUFFER_MAX_SIZE) {
            long nativeData = NULLPTR;
            long nativeOutput = NULLPTR;
            try {
                nativeData = NativeMemory.mallocByteArray(bufferSize);
                nativeOutput = NativeMemory.mallocLongArray(PWD_OUTPUT_LEN);
                int result = namePtr == NULLPTR
                                ? posixNativeFunctionInvoker.call_getpwuid_r(uid, nativeData, bufferSize, nativeOutput)
                                : posixNativeFunctionInvoker.call_getpwname_r(namePtr, nativeData, bufferSize, nativeOutput);
                if (result == -1) {
                    return null;
                }
                if (result == 0) {
                    byte[] data = new byte[bufferSize];
                    NativeMemory.readByteArrayElements(nativeData, 0, data, 0, data.length);
                    long[] output = new long[PWD_OUTPUT_LEN];
                    NativeMemory.readLongArrayElements(nativeOutput, 0, output, 0, output.length);
                    return createPwdResult(data, output, fromByteArrayNode, switchEncodingFromUtf8Node);
                }
                if (result != OSErrorEnum.ERANGE.getNumber() || sysConfMax != -1) {
                    // no point in trying larger buffer if we got different error or the OS already
                    // told
                    // us that sysConfMax should be enough...
                    throw newPosixException(result);
                }
            } finally {
                NativeMemory.free(nativeOutput);
                NativeMemory.free(nativeData);
            }
            bufferSize <<= 1;
        }
        throw outOfMemoryPosixError();
    }

    private static PwdResult createPwdResult(byte[] data, long[] output, TruffleString.FromByteArrayNode fromByteArrayNode, TruffleString.SwitchEncodingNode switchEncodingFromUtf8Node)
                    throws PosixException {
        return new PwdResult(
                        extractZeroTerminatedString(data, output[0], fromByteArrayNode, switchEncodingFromUtf8Node),
                        output[1], output[2],
                        extractZeroTerminatedString(data, output[3], fromByteArrayNode, switchEncodingFromUtf8Node),
                        extractZeroTerminatedString(data, output[4], fromByteArrayNode, switchEncodingFromUtf8Node));
    }

    @ExportMessage
    public int ioctlBytes(int fd, long request, byte[] arg) throws PosixException {
        long nativeArg = NULLPTR;
        try {
            nativeArg = NativeMemory.mallocByteArray(Math.max(arg.length, 1));
            if (arg.length > 0) {
                NativeMemory.writeByteArrayElements(nativeArg, 0, arg, 0, arg.length);
            }
            int res = posixNativeFunctionInvoker.call_ioctl_bytes(fd, request, nativeArg);
            if (res < 0) {
                throw getErrnoAndThrowPosixException();
            }
            if (arg.length > 0) {
                NativeMemory.readByteArrayElements(nativeArg, 0, arg, 0, arg.length);
            }
            return res;
        } finally {
            NativeMemory.free(nativeArg);
        }
    }

    @ExportMessage
    public int ioctlInt(int fd, long request, int arg) throws PosixException {
        int res = posixNativeFunctionInvoker.call_ioctl_int(fd, request, arg);
        if (res < 0) {
            throw getErrnoAndThrowPosixException();
        }
        return res;
    }

    private static TruffleString extractZeroTerminatedString(byte[] buffer, long longOffset, TruffleString.FromByteArrayNode fromByteArrayNode,
                    TruffleString.SwitchEncodingNode switchEncodingFromUtf8Node) throws PosixException {
        if (longOffset < 0 || longOffset >= buffer.length) {
            throw outOfMemoryPosixError();
        }
        int offset = (int) longOffset;
        int end = ArrayUtils.indexOf(buffer, offset, buffer.length, (byte) 0);
        if (end < 0) {
            throw CompilerDirectives.shouldNotReachHere("Could not find the end of the string");
        }
        // TODO PyUnicode_DecodeFSDefault
        return createString(buffer, offset, end - offset, true, fromByteArrayNode, switchEncodingFromUtf8Node);
    }

    private static PosixException outOfMemoryPosixError() throws PosixException {
        throw new PosixErrnoException(OSErrorEnum.ENOMEM.getNumber(), OSErrorEnum.ENOMEM.getMessage());
    }

    private int sysConfPwdSizeMax = -1;

    private int getSysConfPwdSizeMax() throws PosixException {
        if (CompilerDirectives.injectBranchProbability(CompilerDirectives.SLOWPATH_PROBABILITY, sysConfPwdSizeMax == -1)) {
            long sysConfMaxLong = posixNativeFunctionInvoker.get_sysconf_getpw_r_size_max();
            if (sysConfMaxLong != -1 && (sysConfMaxLong < 0 || sysConfMaxLong > PWD_BUFFER_MAX_SIZE)) {
                throw outOfMemoryPosixError();
            }
            sysConfPwdSizeMax = (int) sysConfMaxLong;
        }
        return sysConfPwdSizeMax;
    }

    // ------------------
    // Path conversions

    @ExportMessage
    @SuppressWarnings("static-method")
    public Object createPathFromString(TruffleString path,
                    @Exclusive @Cached TruffleString.SwitchEncodingNode switchEncodingNode,
                    @Exclusive @Cached TruffleString.CopyToByteArrayNode copyToByteArrayNode) {
        TruffleString utf8 = switchEncodingNode.execute(path, UTF_8, SURROGATE_ESCAPE_TO_UTF8_TRANSCODING_ERROR_HANDLER);
        return checkPath(copyToByteArrayNode.execute(utf8, UTF_8));
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    public Object createPathFromBytes(byte[] path) {
        return checkPath(path);
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    public TruffleString getPathAsString(Object path,
                    @Exclusive @Cached TruffleString.FromByteArrayNode fromByteArrayNode,
                    @Exclusive @Cached TruffleString.IsValidNode isValidNode,
                    @Exclusive @Cached TruffleString.SwitchEncodingNode switchEncodingNode) {
        Buffer result = (Buffer) path;
        if (result.length > Integer.MAX_VALUE) {
            // sanity check that it is safe to cast result.length to int, to be removed once
            // we support large arrays
            throw CompilerDirectives.shouldNotReachHere("Posix path cannot fit into a Java array");
        }
        int length = (int) result.length;
        TruffleString utf8 = fromByteArrayNode.execute(result.data, 0, length, UTF_8, true);
        if (isValidNode.execute(utf8, UTF_8)) {
            return switchEncodingNode.execute(utf8, TS_ENCODING);
        }
        return switchEncodingNode.execute(utf8, TS_ENCODING, PyUnicodeFSDecoderNode.SURROGATE_ESCAPE_FROM_UTF8_TRANSCODING_ERROR_HANDLER);
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    public Buffer getPathAsBytes(Object path) {
        return (Buffer) path;
    }

    private static TruffleString createString(byte[] src, int offset, int length, boolean copy, TruffleString.FromByteArrayNode fromByteArrayNode,
                    TruffleString.SwitchEncodingNode switchEncodingNode) {
        TruffleString utf8 = fromByteArrayNode.execute(src, offset, length, UTF_8, copy);
        return switchEncodingNode.execute(utf8, TS_ENCODING);
    }

    private static byte[] getStringBytes(TruffleString str, TruffleString.SwitchEncodingNode switchEncodingNode, TruffleString.CopyToByteArrayNode copyToByteArrayNode) {
        TruffleString utf8 = switchEncodingNode.execute(str, UTF_8);
        byte[] bytes = new byte[utf8.byteLength(UTF_8)];
        copyToByteArrayNode.execute(utf8, 0, bytes, 0, bytes.length, UTF_8);
        return bytes;
    }

    private static Buffer checkPath(byte[] path) {
        for (byte b : path) {
            if (b == 0) {
                return null;
            }
        }
        // TODO we keep a byte[] provided by the caller, who can potentially change it, making our
        // check for embedded nulls pointless. Maybe we should copy it and while on it, might as
        // well add the terminating null character, avoiding the copy we do later in pathToCString.
        return Buffer.wrap(path);
    }

    // ------------------
    // Objects/handles/pointers

    protected static class DirEntry {
        final Buffer name;
        final long ino;
        final int type;

        DirEntry(Buffer name, long ino, int type) {
            this.name = name;
            this.ino = ino;
            this.type = type;
        }

        @Override
        public String toString() {
            return "DirEntry{" +
                            "name='" + new String(name.data, 0, (int) name.length) + "'" +
                            ", ino=" + ino +
                            ", type=" + type +
                            '}';
        }
    }

    // ------------------
    // Helpers

    private PosixException getErrnoAndThrowPosixException() throws PosixException {
        throw newPosixException(posixNativeFunctionInvoker.get_errno());
    }

    @TruffleBoundary
    private PosixException newPosixException(int errno) throws PosixException {
        throw new PosixErrnoException(errno, strerror(errno, null, NativeMemory.ZeroTerminatedUtf8ToTruffleStringNode.getUncached()));
    }

    private static long copyTimevalArrayToNativeOrNull(Timeval[] timeval) {
        return timeval == null ? NULLPTR : NativeMemory.copyToNativeLongArray(new long[]{timeval[0].getSeconds(), timeval[0].getMicroseconds(), timeval[1].getSeconds(), timeval[1].getMicroseconds()});
    }

    private static long wrapItimerval(Timeval delay, Timeval interval) {
        long ptr = NativeMemory.mallocLongArray(4);
        NativeMemory.writeLongArrayElement(ptr, 0, delay.getSeconds());
        NativeMemory.writeLongArrayElement(ptr, 1, delay.getMicroseconds());
        NativeMemory.writeLongArrayElement(ptr, 2, interval.getSeconds());
        NativeMemory.writeLongArrayElement(ptr, 3, interval.getMicroseconds());
        return ptr;
    }

    private static void readNativeSockAddr(long nativeAddr, long nativeAddrLen, UniversalSockAddrImpl addr) {
        int addrLen = NativeMemory.readInt(nativeAddrLen);
        if (addrLen < 0 || addrLen > addr.data.length) {
            throw CompilerDirectives.shouldNotReachHere("Unexpected socket address length");
        }
        addr.setLen(addrLen);
        if (addrLen > 0) {
            NativeMemory.readByteArrayElements(nativeAddr, 0, addr.data, 0, addrLen);
        }
    }

    private static Timeval[] unwrapTimeval(long nativeTimeval) {
        return new Timeval[]{
                        new Timeval(NativeMemory.readLongArrayElement(nativeTimeval, 0), NativeMemory.readLongArrayElement(nativeTimeval, 1)),
                        new Timeval(NativeMemory.readLongArrayElement(nativeTimeval, 2), NativeMemory.readLongArrayElement(nativeTimeval, 3))
        };
    }

    private static int findZero(byte[] buf) {
        for (int i = 0; i < buf.length; ++i) {
            if (buf[i] == 0) {
                return i;
            }
        }
        return buf.length;
    }

    private long pathToNativeCStringOrNull(Object path) {
        return path == null ? NULLPTR : bufferToNativeCString((Buffer) path);
    }

    private long pathToNativeCString(Object path) {
        return bufferToNativeCString((Buffer) path);
    }

    private static long bufferToNativeCString(Buffer path) {
        return NativeMemory.copyToNativeZeroTerminatedByteArray(path.data, 0, (int) path.length);
    }

    private long stringToNativeUTF8CString(TruffleString input,
                    @Cached TruffleString.SwitchEncodingNode switchEncodingToUtf8Node,
                    @Cached TruffleString.CopyToByteArrayNode copyToByteArrayNode) {
        byte[] utf8 = getStringBytes(input, switchEncodingToUtf8Node, copyToByteArrayNode);
        return NativeMemory.copyToNativeZeroTerminatedByteArray(utf8, 0, utf8.length);
    }

    private static void checkBounds(byte[] buf, int offset, int length) {
        if (length < 0) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new IllegalArgumentException();
        }
        if (offset < 0 || offset + length > buf.length) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new IndexOutOfBoundsException();
        }
    }

    @TruffleBoundary
    private static void log(Level level, String fmt, Object... args) {
        if (LOGGER.isLoggable(level)) {
            LOGGER.log(level, String.format(fmt, args));
        }
    }
}
