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
package com.oracle.graal.python.runtime;

import com.oracle.graal.python.runtime.PosixSupportLibrary.Buffer;
import com.oracle.graal.python.runtime.PosixSupportLibrary.PosixException;
import com.oracle.graal.python.runtime.PosixSupportLibrary.SelectResult;
import com.oracle.graal.python.runtime.PosixSupportLibrary.Timeval;
import com.oracle.graal.python.runtime.PosixSupportLibrary.UnsupportedPosixFeatureException;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

public abstract class PosixSupport {

    public static final int ST_MODE = 0;

    public static abstract class Path {
    }

    public void setEnv(@SuppressWarnings("unused") Env env) {
        // nop
    }

    public static PosixSupport get(Node node) {
        return PythonContext.get(node).getPosixSupport();
    }

    public abstract void raise(int signal) throws PosixException;

    public abstract int alarm(int seconds) throws PosixException;

    public abstract Timeval[] getitimer(int which) throws PosixException;

    public abstract Timeval[] setitimer(int which, Timeval delay, Timeval interval) throws PosixException;

    public abstract void signalSelf(int signal) throws PosixException;

    public abstract void kill(long pid, int signal) throws PosixException;

    public abstract void killpg(long pid, int signal) throws PosixException;

    public abstract long[] waitpid(Node location, long pid, int options) throws PosixException;

    public abstract boolean wcoredump(int status);

    public abstract boolean wifcontinued(int status);

    public abstract boolean wifstopped(int status);

    public abstract boolean wifsignaled(int status);

    public abstract boolean wifexited(int status);

    public abstract int wexitstatus(int status);

    public abstract int wtermsig(int status);

    public abstract int wstopsig(int status);

    public abstract TruffleString getBackend();

    public abstract TruffleString strerror(int errorCode);

    public abstract long getpid();

    public abstract int umask(int mask) throws PosixException;

    public abstract int openat(int dirFd, Object pathname, int flags, int mode) throws PosixException;

    public abstract int close(int fd) throws PosixException;

    public abstract long getOsfHandle(int fd) throws PosixException;

    public abstract int openOsfHandle(long handle, int flags) throws PosixException;

    public abstract int setMode(int fd, int mode) throws PosixException;

    public abstract void msvcrtLocking(int fd, int mode, long nbytes) throws PosixException;

    public abstract int[] pipe() throws PosixException;

    public abstract SelectResult select(int[] readfds, int[] writefds, int[] errorfds, Timeval timeout) throws PosixException;

    public abstract void poll(int[] fds, int[] events, int[] revents, int timeout) throws PosixException;

    public abstract long lseek(int fd, long offset, int how) throws PosixException;

    public abstract void ftruncate(int fd, long length) throws PosixException;

    public abstract void truncate(Object path, long length) throws PosixException;

    public abstract void fsync(int fd) throws PosixException;

    public abstract void flock(int fd, int operation) throws PosixException;

    public abstract void fcntlLock(int fd, boolean blocking, int lockType, int whence, long start, long length) throws PosixException;

    public abstract boolean getBlocking(int fd) throws PosixException;

    public abstract void setBlocking(int fd, boolean blocking) throws PosixException;

    public abstract int[] getTerminalSize(int fd) throws PosixException;

    public abstract long sysconf(int name) throws PosixException;

    public abstract Buffer read(int fd, long length) throws PosixException;

    public abstract long write(int fd, Buffer data) throws PosixException;

    /** Returns {@code 'r'}, {@code 'w'}, or zero when {@code fd} is not a Windows console. */
    public abstract int getWindowsConsoleType(int fd);

    public abstract long writeWindowsConsole(int fd, Buffer data) throws PosixException;

    public abstract int dup(int fd) throws PosixException;

    public abstract int dup2(int fd, int fd2, boolean inheritable) throws PosixException;

    public abstract boolean getInheritable(int fd) throws PosixException;

    public abstract void setInheritable(int fd, boolean inheritable) throws PosixException;

    // see stat_struct_to_longs in posix.c for the layout of the array
    public abstract long[] fstatat(int dirFd, Object pathname, boolean followSymlinks) throws PosixException;

    /**
     * Performs operation of fstat(fd).
     *
     * @param fd the file descriptor
     * @return see {@code stat_struct_to_longs} in posix.c for the layout of the array. There are
     *         constants for some of the indices, e.g., {@link #ST_MODE}.
     * @throws PosixException if an error occurs
     */
    public abstract long[] fstat(int fd) throws PosixException;

    public abstract long[] statvfs(Object path) throws PosixException;

    public abstract long[] fstatvfs(int fd) throws PosixException;

    public abstract Object[] uname() throws PosixException;

    public record WindowsVersion(int major, int minor, int build, int platform, TruffleString servicePack,
                    int servicePackMajor, int servicePackMinor, int suiteMask, int productType,
                    int platformMajor, int platformMinor, int platformBuild) {
    }

    /** Returns the Windows version and product information. */
    public abstract WindowsVersion getWindowsVersion() throws PosixException;

    public abstract void unlinkat(int dirFd, Object pathname, boolean rmdir) throws PosixException;

    public abstract void linkat(int oldFdDir, Object oldPath, int newFdDir, Object newPath, int flags) throws PosixException;

    public abstract void symlinkat(Object target, int linkpathDirFd, Object linkpath) throws PosixException;

    public abstract void mkdirat(int dirFd, Object pathname, int mode) throws PosixException;

    public abstract Object getcwd() throws PosixException;

    public abstract void chdir(Object path) throws PosixException;

    public abstract void fchdir(int fd) throws PosixException;

    public abstract boolean isatty(int fd);

    /** Caller is responsible for closing the returned directory stream with {@link #closedir(Object)}. */
    public abstract Object opendir(Object path) throws PosixException;

    public abstract Object fdopendir(int fd) throws PosixException;

    /** Implementations must deal with this being called more than once. */
    public abstract void closedir(Object dirStream) throws PosixException;

    /** Returns null when there are no more entries or the stream has been closed. */
    public abstract Object readdir(Object dirStream) throws PosixException;

    public abstract void rewinddir(Object dirStream);

    /** Returns an opaque directory-entry name suitable for path conversion. */
    public abstract Object dirEntryGetName(Object dirEntry) throws PosixException;

    /** Returns the entry name joined to the path originally passed to {@link #opendir(Object)}. */
    public abstract Object dirEntryGetPath(Object dirEntry, Object scandirPath) throws PosixException;

    public abstract long dirEntryGetInode(Object dirEntry) throws PosixException;

    public abstract int dirEntryGetType(Object dirEntry);

    /** The timespec contains access seconds/nanoseconds, then modification seconds/nanoseconds, or is null for now. */
    public abstract void utimensat(int dirFd, Object pathname, long[] timespec, boolean followSymlinks) throws PosixException;

    public abstract void futimens(int fd, long[] timespec) throws PosixException;

    /** The timeval is null or contains access and modification times. */
    public abstract void futimes(int fd, Timeval[] timeval) throws PosixException;

    public abstract void lutimes(Object filename, Timeval[] timeval) throws PosixException;

    public abstract void utimes(Object filename, Timeval[] timeval) throws PosixException;

    public abstract void renameat(int oldDirFd, Object oldPath, int newDirFd, Object newPath) throws PosixException;

    public abstract void replaceat(int oldDirFd, Object oldPath, int newDirFd, Object newPath) throws PosixException;

    public abstract boolean faccessat(int dirFd, Object path, int mode, boolean effectiveIds, boolean followSymlinks) throws UnsupportedPosixFeatureException;

    public abstract void fchmodat(int dirFd, Object path, int mode, boolean followSymlinks) throws PosixException;

    public abstract void fchmod(int fd, int mode) throws PosixException;

    public abstract void fchownat(int dirFd, Object pathname, long owner, long group, boolean followSymlinks) throws PosixException;

    public abstract void fchown(int fd, long owner, long group) throws PosixException;

    public abstract Object readlinkat(int dirFd, Object path) throws PosixException;
}
