/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.api.CompilerDirectives.shouldNotReachHere;

import java.util.HashSet;
import java.util.IdentityHashMap;

import com.oracle.graal.python.runtime.PosixSupportLibrary.AcceptResult;
import com.oracle.graal.python.runtime.PosixSupportLibrary.AddrInfoCursor;
import com.oracle.graal.python.runtime.PosixSupportLibrary.Buffer;
import com.oracle.graal.python.runtime.PosixSupportLibrary.GetAddrInfoException;
import com.oracle.graal.python.runtime.PosixSupportLibrary.Inet4SockAddr;
import com.oracle.graal.python.runtime.PosixSupportLibrary.Inet6SockAddr;
import com.oracle.graal.python.runtime.PosixSupportLibrary.InvalidAddressException;
import com.oracle.graal.python.runtime.PosixSupportLibrary.InvalidUnixSocketPathException;
import com.oracle.graal.python.runtime.PosixSupportLibrary.OpenPtyResult;
import com.oracle.graal.python.runtime.PosixSupportLibrary.PosixException;
import com.oracle.graal.python.runtime.PosixSupportLibrary.PwdResult;
import com.oracle.graal.python.runtime.PosixSupportLibrary.RecvfromResult;
import com.oracle.graal.python.runtime.PosixSupportLibrary.RusageResult;
import com.oracle.graal.python.runtime.PosixSupportLibrary.SelectResult;
import com.oracle.graal.python.runtime.PosixSupportLibrary.Timeval;
import com.oracle.graal.python.runtime.PosixSupportLibrary.UniversalSockAddr;
import com.oracle.graal.python.runtime.PosixSupportLibrary.UnixSockAddr;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.strings.TruffleString;

@ExportLibrary(PosixSupportLibrary.class)
public class PreInitPosixSupport extends PosixSupport {

    protected final PosixSupport nativePosixSupport;
    private PosixSupport emulatedPosixSupport;
    private HashSet<Integer> emulatedFds;
    private IdentityHashMap<Object, Object> emulatedDirStreams;
    private boolean inPreInitialization;

    public PreInitPosixSupport(Env env, PosixSupport nativePosixSupport, PosixSupport emulatedPosixSupport) {
        this.inPreInitialization = env.isPreInitialization();
        this.nativePosixSupport = nativePosixSupport;
        this.emulatedPosixSupport = emulatedPosixSupport;
        if (emulatedPosixSupport != null) {
            emulatedFds = new HashSet<>();
            emulatedDirStreams = new IdentityHashMap<>();
        }
    }

    @Override
    public void setEnv(Env env) {
        assert !env.isPreInitialization();
        this.inPreInitialization = env.isPreInitialization();
        nativePosixSupport.setEnv(env);
    }

    public void checkLeakingResources() {
        assert inPreInitialization;
        if (!emulatedFds.isEmpty()) {
            throw shouldNotReachHere("Emulated fds leaked into the image");
        }
        if (!emulatedDirStreams.isEmpty()) {
            throw shouldNotReachHere("Emulated dirStreams leaked into the image");
        }
        emulatedPosixSupport = null;
        emulatedFds = null;
        emulatedDirStreams = null;
    }

    private void checkNotInPreInitialization() {
        if (inPreInitialization) {
            throw shouldNotReachHere("Posix call not expected during pre-initialization");
        }
    }

    @TruffleBoundary
    private int addFd(int fd) {
        if (emulatedFds.contains(fd)) {
            throw shouldNotReachHere("duplicate fd");
        }
        emulatedFds.add(fd);
        return fd;
    }

    @TruffleBoundary
    private int removeFd(int fd) {
        if (!emulatedFds.contains(fd)) {
            throw shouldNotReachHere("Closing fd that has not been open");
        }
        emulatedFds.remove(fd);
        return fd;
    }

    @TruffleBoundary
    private Object addDirStream(Object dirStream) {
        if (emulatedDirStreams.containsKey(dirStream)) {
            throw shouldNotReachHere("Duplicate dirStream");
        }
        emulatedDirStreams.put(dirStream, dirStream);
        return dirStream;
    }

    @TruffleBoundary
    private Object removeDirStream(Object dirStream) {
        if (!emulatedDirStreams.containsKey(dirStream)) {
            throw shouldNotReachHere("Closing dirStream that has not been open");
        }
        emulatedDirStreams.remove(dirStream);
        return dirStream;
    }

    @ExportMessage
    final TruffleString getBackend(@CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) {
        checkNotInPreInitialization();
        return nativeLib.getBackend(nativePosixSupport);
    }

    @ExportMessage
    final TruffleString strerror(int errorCode,
                    @CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) {
        checkNotInPreInitialization();
        return nativeLib.strerror(nativePosixSupport, errorCode);
    }

    @ExportMessage
    final long getpid(@CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) {
        checkNotInPreInitialization();
        return nativeLib.getpid(nativePosixSupport);
    }

    @ExportMessage
    final int umask(int mask,
                    @CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) throws PosixException {
        checkNotInPreInitialization();
        return nativeLib.umask(nativePosixSupport, mask);
    }

    @ExportMessage
    final int openat(int dirFd, Object pathname, int flags, int mode,
                    @CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) throws PosixException {
        if (inPreInitialization) {
            return addFd(PosixSupportLibrary.getUncached().openat(emulatedPosixSupport, dirFd, pathname, flags, mode));
        }
        return nativeLib.openat(nativePosixSupport, dirFd, pathname, flags, mode);
    }

    @ExportMessage
    final int close(int fd,
                    @CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) throws PosixException {
        if (inPreInitialization) {
            return PosixSupportLibrary.getUncached().close(emulatedPosixSupport, removeFd(fd));
        }
        return nativeLib.close(nativePosixSupport, fd);
    }

    @ExportMessage
    final Buffer read(int fd, long length,
                    @CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) throws PosixException {
        if (inPreInitialization) {
            return PosixSupportLibrary.getUncached().read(emulatedPosixSupport, fd, length);
        }
        return nativeLib.read(nativePosixSupport, fd, length);
    }

    @ExportMessage
    final long write(int fd, Buffer data,
                    @CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) throws PosixException {
        checkNotInPreInitialization();
        return nativeLib.write(nativePosixSupport, fd, data);
    }

    @ExportMessage
    final int dup(int fd,
                    @CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) throws PosixException {
        checkNotInPreInitialization();
        return nativeLib.dup(nativePosixSupport, fd);
    }

    @ExportMessage
    final int dup2(int fd, int fd2, boolean inheritable,
                    @CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) throws PosixException {
        checkNotInPreInitialization();
        return nativeLib.dup2(nativePosixSupport, fd, fd2, inheritable);
    }

    @ExportMessage
    final boolean getInheritable(int fd,
                    @CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) throws PosixException {
        checkNotInPreInitialization();
        return nativeLib.getInheritable(nativePosixSupport, fd);
    }

    @ExportMessage
    final void setInheritable(int fd, boolean inheritable,
                    @CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) throws PosixException {
        if (inPreInitialization) {
            PosixSupportLibrary.getUncached().setInheritable(emulatedPosixSupport, fd, inheritable);
            return;
        }
        nativeLib.setInheritable(nativePosixSupport, fd, inheritable);
    }

    @ExportMessage
    final int[] pipe(@CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) throws PosixException {
        checkNotInPreInitialization();
        return nativeLib.pipe(nativePosixSupport);
    }

    @ExportMessage
    final SelectResult select(int[] readfds, int[] writefds, int[] errorfds, Timeval timeout,
                    @CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) throws PosixException {
        checkNotInPreInitialization();
        return nativeLib.select(nativePosixSupport, readfds, writefds, errorfds, timeout);
    }

    @ExportMessage
    final boolean poll(int fd, boolean forWriting, Timeval timeout,
                    @CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) throws PosixException {
        checkNotInPreInitialization();
        return nativeLib.poll(nativePosixSupport, fd, forWriting, timeout);
    }

    @ExportMessage
    final long lseek(int fd, long offset, int how,
                    @CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) throws PosixException {
        if (inPreInitialization) {
            return PosixSupportLibrary.getUncached().lseek(emulatedPosixSupport, fd, offset, how);
        }
        return nativeLib.lseek(nativePosixSupport, fd, offset, how);
    }

    @ExportMessage
    final void ftruncate(int fd, long length,
                    @CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) throws PosixException {
        checkNotInPreInitialization();
        nativeLib.ftruncate(nativePosixSupport, fd, length);
    }

    @ExportMessage
    final void truncate(Object path, long length,
                    @CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) throws PosixException {
        checkNotInPreInitialization();
        nativeLib.truncate(nativePosixSupport, path, length);
    }

    @ExportMessage
    final void fsync(int fd,
                    @CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) throws PosixException {
        checkNotInPreInitialization();
        nativeLib.fsync(nativePosixSupport, fd);
    }

    @ExportMessage
    final void flock(int fd, int operation,
                    @CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) throws PosixException {
        checkNotInPreInitialization();
        nativeLib.flock(nativePosixSupport, fd, operation);
    }

    @ExportMessage
    final void fcntlLock(int fd, boolean blocking, int lockType, int whence, long start, long length,
                    @CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) throws PosixException {
        checkNotInPreInitialization();
        nativeLib.fcntlLock(nativePosixSupport, fd, blocking, lockType, whence, start, length);
    }

    @ExportMessage
    final boolean getBlocking(int fd,
                    @CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) throws PosixException {
        checkNotInPreInitialization();
        return nativeLib.getBlocking(nativePosixSupport, fd);
    }

    @ExportMessage
    final void setBlocking(int fd, boolean blocking,
                    @CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) throws PosixException {
        checkNotInPreInitialization();
        nativeLib.setBlocking(nativePosixSupport, fd, blocking);
    }

    @ExportMessage
    final int[] getTerminalSize(int fd,
                    @CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) throws PosixException {
        checkNotInPreInitialization();
        return nativeLib.getTerminalSize(nativePosixSupport, fd);
    }

    @ExportMessage
    final long sysconf(int name,
                    @CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) throws PosixException {
        checkNotInPreInitialization();
        return nativeLib.sysconf(nativePosixSupport, name);
    }

    @ExportMessage
    final long[] fstatat(int dirFd, Object pathname, boolean followSymlinks,
                    @CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) throws PosixException {
        if (inPreInitialization) {
            return PosixSupportLibrary.getUncached().fstatat(emulatedPosixSupport, dirFd, pathname, followSymlinks);
        }
        return nativeLib.fstatat(nativePosixSupport, dirFd, pathname, followSymlinks);
    }

    @ExportMessage
    final long[] fstat(int fd,
                    @CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) throws PosixException {
        if (inPreInitialization) {
            return PosixSupportLibrary.getUncached().fstat(emulatedPosixSupport, fd);
        }
        return nativeLib.fstat(nativePosixSupport, fd);
    }

    @ExportMessage
    final long[] statvfs(Object path,
                    @CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) throws PosixException {
        if (inPreInitialization) {
            return PosixSupportLibrary.getUncached().statvfs(emulatedPosixSupport, path);
        }
        return nativeLib.statvfs(nativePosixSupport, path);
    }

    @ExportMessage
    final long[] fstatvfs(int fd,
                    @CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) throws PosixException {
        if (inPreInitialization) {
            return PosixSupportLibrary.getUncached().fstatvfs(emulatedPosixSupport, fd);
        }
        return nativeLib.fstatvfs(nativePosixSupport, fd);
    }

    @ExportMessage
    final Object[] uname(@CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) throws PosixException {
        checkNotInPreInitialization();
        return nativeLib.uname(nativePosixSupport);
    }

    @ExportMessage
    final void unlinkat(int dirFd, Object pathname, boolean rmdir,
                    @CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) throws PosixException {
        checkNotInPreInitialization();
        nativeLib.unlinkat(nativePosixSupport, dirFd, pathname, rmdir);
    }

    @ExportMessage
    final void linkat(int oldFdDir, Object oldPath, int newFdDir, Object newPath, int flags,
                    @CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) throws PosixException {
        checkNotInPreInitialization();
        nativeLib.linkat(nativePosixSupport, oldFdDir, oldPath, newFdDir, newPath, flags);
    }

    @ExportMessage
    final void symlinkat(Object target, int linkpathDirFd, Object linkpath,
                    @CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) throws PosixException {
        checkNotInPreInitialization();
        nativeLib.symlinkat(nativePosixSupport, target, linkpathDirFd, linkpath);
    }

    @ExportMessage
    final void mkdirat(int dirFd, Object pathname, int mode,
                    @CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) throws PosixException {
        checkNotInPreInitialization();
        nativeLib.mkdirat(nativePosixSupport, dirFd, pathname, mode);
    }

    @ExportMessage
    final Object getcwd(@CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) throws PosixException {
        checkNotInPreInitialization();
        return nativeLib.getcwd(nativePosixSupport);
    }

    @ExportMessage
    final void chdir(Object path,
                    @CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) throws PosixException {
        checkNotInPreInitialization();
        nativeLib.chdir(nativePosixSupport, path);
    }

    @ExportMessage
    final void fchdir(int fd,
                    @CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) throws PosixException {
        checkNotInPreInitialization();
        nativeLib.fchdir(nativePosixSupport, fd);
    }

    @ExportMessage
    final boolean isatty(int fd,
                    @CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) {
        checkNotInPreInitialization();
        return nativeLib.isatty(nativePosixSupport, fd);
    }

    @ExportMessage
    final Object opendir(Object path,
                    @CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) throws PosixException {
        if (inPreInitialization) {
            return addDirStream(PosixSupportLibrary.getUncached().opendir(emulatedPosixSupport, path));
        }
        return nativeLib.opendir(nativePosixSupport, path);
    }

    @ExportMessage
    final Object fdopendir(int fd,
                    @CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) throws PosixException {
        checkNotInPreInitialization();
        return nativeLib.fdopendir(nativePosixSupport, fd);
    }

    @ExportMessage
    final void closedir(Object dirStream,
                    @CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) throws PosixException {
        if (inPreInitialization) {
            PosixSupportLibrary.getUncached().closedir(emulatedPosixSupport, removeDirStream(dirStream));
            return;
        }
        nativeLib.closedir(nativePosixSupport, dirStream);
    }

    @ExportMessage
    final Object readdir(Object dirStream,
                    @CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) throws PosixException {
        if (inPreInitialization) {
            return PosixSupportLibrary.getUncached().readdir(emulatedPosixSupport, dirStream);
        }
        return nativeLib.readdir(nativePosixSupport, dirStream);
    }

    @ExportMessage
    final void rewinddir(Object dirStream,
                    @CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) {
        if (inPreInitialization) {
            PosixSupportLibrary.getUncached().rewinddir(emulatedPosixSupport, dirStream);
        }
        nativeLib.rewinddir(nativePosixSupport, dirStream);
    }

    @ExportMessage
    final Object dirEntryGetName(Object dirEntry,
                    @CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) throws PosixException {
        if (inPreInitialization) {
            return PosixSupportLibrary.getUncached().dirEntryGetName(emulatedPosixSupport, dirEntry);
        }
        return nativeLib.dirEntryGetName(nativePosixSupport, dirEntry);
    }

    @ExportMessage
    final Object dirEntryGetPath(Object dirEntry, Object scandirPath,
                    @CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) throws PosixException {
        checkNotInPreInitialization();
        return nativeLib.dirEntryGetPath(nativePosixSupport, dirEntry, scandirPath);
    }

    @ExportMessage
    final long dirEntryGetInode(Object dirEntry,
                    @CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) throws PosixException {
        checkNotInPreInitialization();
        return nativeLib.dirEntryGetInode(nativePosixSupport, dirEntry);
    }

    @ExportMessage
    final int dirEntryGetType(Object dirEntry,
                    @CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) {
        checkNotInPreInitialization();
        return nativeLib.dirEntryGetType(nativePosixSupport, dirEntry);
    }

    @ExportMessage
    final void utimensat(int dirFd, Object pathname, long[] timespec, boolean followSymlinks,
                    @CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) throws PosixException {
        checkNotInPreInitialization();
        nativeLib.utimensat(nativePosixSupport, dirFd, pathname, timespec, followSymlinks);
    }

    @ExportMessage
    final void futimens(int fd, long[] timespec,
                    @CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) throws PosixException {
        checkNotInPreInitialization();
        nativeLib.futimens(nativePosixSupport, fd, timespec);
    }

    @ExportMessage
    final void futimes(int fd, Timeval[] timeval,
                    @CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) throws PosixException {
        checkNotInPreInitialization();
        nativeLib.futimes(nativePosixSupport, fd, timeval);
    }

    @ExportMessage
    final void lutimes(Object filename, Timeval[] timeval,
                    @CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) throws PosixException {
        checkNotInPreInitialization();
        nativeLib.lutimes(nativePosixSupport, filename, timeval);
    }

    @ExportMessage
    final void utimes(Object filename, Timeval[] timeval,
                    @CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) throws PosixException {
        checkNotInPreInitialization();
        nativeLib.utimes(nativePosixSupport, filename, timeval);
    }

    @ExportMessage
    final void renameat(int oldDirFd, Object oldPath, int newDirFd, Object newPath,
                    @CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) throws PosixException {
        checkNotInPreInitialization();
        nativeLib.renameat(nativePosixSupport, oldDirFd, oldPath, newDirFd, newPath);
    }

    @ExportMessage
    final boolean faccessat(int dirFd, Object path, int mode, boolean effectiveIds, boolean followSymlinks,
                    @CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) {
        checkNotInPreInitialization();
        return nativeLib.faccessat(nativePosixSupport, dirFd, path, mode, effectiveIds, followSymlinks);
    }

    @ExportMessage
    final void fchmodat(int dirFd, Object path, int mode, boolean followSymlinks,
                    @CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) throws PosixException {
        checkNotInPreInitialization();
        nativeLib.fchmodat(nativePosixSupport, dirFd, path, mode, followSymlinks);
    }

    @ExportMessage
    final void fchmod(int fd, int mode,
                    @CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) throws PosixException {
        checkNotInPreInitialization();
        nativeLib.fchmod(nativePosixSupport, fd, mode);
    }

    @ExportMessage
    final void fchownat(int dirFd, Object path, long owner, long group, boolean followSymlinks,
                    @CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) throws PosixException {
        checkNotInPreInitialization();
        nativeLib.fchownat(nativePosixSupport, dirFd, path, owner, group, followSymlinks);
    }

    @ExportMessage
    final void fchown(int fd, long owner, long group,
                    @CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) throws PosixException {
        checkNotInPreInitialization();
        nativeLib.fchown(nativePosixSupport, fd, owner, group);
    }

    @ExportMessage
    final Object readlinkat(int dirFd, Object path,
                    @CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) throws PosixException {
        checkNotInPreInitialization();
        return nativeLib.readlinkat(nativePosixSupport, dirFd, path);
    }

    @ExportMessage
    final void kill(long pid, int signal,
                    @CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) throws PosixException {
        checkNotInPreInitialization();
        nativeLib.kill(nativePosixSupport, pid, signal);
    }

    @ExportMessage
    final void killpg(long pgid, int signal,
                    @CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) throws PosixException {
        checkNotInPreInitialization();
        nativeLib.killpg(nativePosixSupport, pgid, signal);
    }

    @ExportMessage
    final long[] waitpid(long pid, int options,
                    @CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) throws PosixException {
        checkNotInPreInitialization();
        return nativeLib.waitpid(nativePosixSupport, pid, options);
    }

    @ExportMessage
    final void abort(@CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) {
        checkNotInPreInitialization();
        nativeLib.abort(nativePosixSupport);
    }

    @ExportMessage
    final boolean wcoredump(int status,
                    @CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) {
        checkNotInPreInitialization();
        return nativeLib.wcoredump(nativePosixSupport, status);
    }

    @ExportMessage
    final boolean wifcontinued(int status,
                    @CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) {
        checkNotInPreInitialization();
        return nativeLib.wifcontinued(nativePosixSupport, status);
    }

    @ExportMessage
    final boolean wifstopped(int status,
                    @CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) {
        checkNotInPreInitialization();
        return nativeLib.wifstopped(nativePosixSupport, status);
    }

    @ExportMessage
    final boolean wifsignaled(int status,
                    @CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) {
        checkNotInPreInitialization();
        return nativeLib.wifsignaled(nativePosixSupport, status);
    }

    @ExportMessage
    final boolean wifexited(int status,
                    @CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) {
        checkNotInPreInitialization();
        return nativeLib.wifexited(nativePosixSupport, status);
    }

    @ExportMessage
    final int wexitstatus(int status,
                    @CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) {
        checkNotInPreInitialization();
        return nativeLib.wexitstatus(nativePosixSupport, status);
    }

    @ExportMessage
    final int wtermsig(int status,
                    @CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) {
        checkNotInPreInitialization();
        return nativeLib.wtermsig(nativePosixSupport, status);
    }

    @ExportMessage
    final int wstopsig(int status,
                    @CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) {
        checkNotInPreInitialization();
        return nativeLib.wstopsig(nativePosixSupport, status);
    }

    @ExportMessage
    final long getuid(@CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) {
        checkNotInPreInitialization();
        return nativeLib.getuid(nativePosixSupport);
    }

    @ExportMessage
    final long geteuid(@CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) {
        checkNotInPreInitialization();
        return nativeLib.geteuid(nativePosixSupport);
    }

    @ExportMessage
    final long getgid(@CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) {
        checkNotInPreInitialization();
        return nativeLib.getgid(nativePosixSupport);
    }

    @ExportMessage
    final long getegid(@CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) {
        checkNotInPreInitialization();
        return nativeLib.getegid(nativePosixSupport);
    }

    @ExportMessage
    final long getppid(@CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) {
        checkNotInPreInitialization();
        return nativeLib.getppid(nativePosixSupport);
    }

    @ExportMessage
    final long getpgid(long pid,
                    @CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) throws PosixException {
        checkNotInPreInitialization();
        return nativeLib.getpgid(nativePosixSupport, pid);
    }

    @ExportMessage
    final void setpgid(long pid, long pgid,
                    @CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) throws PosixException {
        checkNotInPreInitialization();
        nativeLib.setpgid(nativePosixSupport, pid, pgid);
    }

    @ExportMessage
    final long getpgrp(@CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) {
        checkNotInPreInitialization();
        return nativeLib.getpgrp(nativePosixSupport);
    }

    @ExportMessage
    final long getsid(long pid,
                    @CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) throws PosixException {
        checkNotInPreInitialization();
        return nativeLib.getsid(nativePosixSupport, pid);
    }

    @ExportMessage
    final long setsid(
                    @CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) throws PosixException {
        checkNotInPreInitialization();
        return nativeLib.setsid(nativePosixSupport);
    }

    @ExportMessage
    final long[] getgroups(
                    @CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) throws PosixException {
        checkNotInPreInitialization();
        return nativeLib.getgroups(nativePosixSupport);
    }

    @ExportMessage
    final RusageResult getrusage(int who,
                    @CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) throws PosixException {
        checkNotInPreInitialization();
        return nativeLib.getrusage(nativePosixSupport, who);
    }

    @ExportMessage
    final OpenPtyResult openpty(@CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) throws PosixException {
        checkNotInPreInitialization();
        return nativeLib.openpty(nativePosixSupport);
    }

    @ExportMessage
    final TruffleString ctermid(@CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) throws PosixException {
        checkNotInPreInitialization();
        return nativeLib.ctermid(nativePosixSupport);
    }

    @ExportMessage
    final void setenv(Object name, Object value, boolean overwrite,
                    @CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) throws PosixException {
        checkNotInPreInitialization();
        nativeLib.setenv(nativePosixSupport, name, value, overwrite);
    }

    @ExportMessage
    final void unsetenv(Object name,
                    @CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) throws PosixException {
        checkNotInPreInitialization();
        nativeLib.unsetenv(nativePosixSupport, name);
    }

    @ExportMessage
    final int forkExec(Object[] executables, Object[] args, Object cwd, Object[] env, int stdinReadFd, int stdinWriteFd, int stdoutReadFd, int stdoutWriteFd, int stderrReadFd, int stderrWriteFd,
                    int errPipeReadFd, int errPipeWriteFd, boolean closeFds, boolean restoreSignals, boolean callSetsid, int[] fdsToKeep,
                    @CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) throws PosixException {
        checkNotInPreInitialization();
        return nativeLib.forkExec(nativePosixSupport, executables, args, cwd, env, stdinReadFd, stdinWriteFd, stdoutReadFd, stdoutWriteFd, stderrReadFd, stderrWriteFd, errPipeReadFd, errPipeWriteFd,
                        closeFds, restoreSignals, callSetsid, fdsToKeep);
    }

    @ExportMessage
    final void execv(Object pathname, Object[] args,
                    @CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) throws PosixException {
        checkNotInPreInitialization();
        nativeLib.execv(nativePosixSupport, pathname, args);
    }

    @ExportMessage
    final int system(Object command,
                    @CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) {
        checkNotInPreInitialization();
        return nativeLib.system(nativePosixSupport, command);
    }

    @ExportMessage
    final Object mmap(long length, int prot, int flags, int fd, long offset,
                    @CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) throws PosixException {
        checkNotInPreInitialization();
        return nativeLib.mmap(nativePosixSupport, length, prot, flags, fd, offset);
    }

    @ExportMessage
    final byte mmapReadByte(Object mmap, long index,
                    @CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) throws PosixException {
        checkNotInPreInitialization();
        return nativeLib.mmapReadByte(nativePosixSupport, mmap, index);
    }

    @ExportMessage
    final void mmapWriteByte(Object mmap, long index, byte value,
                    @CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) throws PosixException {
        checkNotInPreInitialization();
        nativeLib.mmapWriteByte(nativePosixSupport, mmap, index, value);
    }

    @ExportMessage
    final int mmapReadBytes(Object mmap, long index, byte[] bytes, int length,
                    @CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) throws PosixException {
        checkNotInPreInitialization();
        return nativeLib.mmapReadBytes(nativePosixSupport, mmap, index, bytes, length);
    }

    @ExportMessage
    final void mmapWriteBytes(Object mmap, long index, byte[] bytes, int length,
                    @CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) throws PosixException {
        checkNotInPreInitialization();
        nativeLib.mmapWriteBytes(nativePosixSupport, mmap, index, bytes, length);
    }

    @ExportMessage
    final void mmapFlush(Object mmap, long offset, long length,
                    @CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) throws PosixException {
        checkNotInPreInitialization();
        nativeLib.mmapFlush(nativePosixSupport, mmap, offset, length);
    }

    @ExportMessage
    final void mmapUnmap(Object mmap, long length,
                    @CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) throws PosixException {
        checkNotInPreInitialization();
        nativeLib.mmapUnmap(nativePosixSupport, mmap, length);
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    final long mmapGetPointer(Object mmap,
                    @CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) {
        checkNotInPreInitialization();
        return nativeLib.mmapGetPointer(nativePosixSupport, mmap);
    }

    @ExportMessage
    public PwdResult getpwuid(long uid,
                    @CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) throws PosixException {
        checkNotInPreInitialization();
        return nativeLib.getpwuid(nativePosixSupport, uid);
    }

    @ExportMessage
    public PwdResult getpwnam(Object name,
                    @CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) throws PosixException {
        checkNotInPreInitialization();
        return nativeLib.getpwnam(nativePosixSupport, name);
    }

    @ExportMessage
    public boolean hasGetpwentries(@CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) {
        return nativeLib.hasGetpwentries(nativePosixSupport);
    }

    @ExportMessage
    public PwdResult[] getpwentries(@CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) throws PosixException {
        checkNotInPreInitialization();
        return nativeLib.getpwentries(nativePosixSupport);
    }

    @ExportMessage
    final int ioctlBytes(int fd, long request, byte[] arg,
                    @CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) throws PosixException {
        checkNotInPreInitialization();
        return nativeLib.ioctlBytes(nativePosixSupport, fd, request, arg);
    }

    @ExportMessage
    final int ioctlInt(int fd, long request, int arg,
                    @CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) throws PosixException {
        checkNotInPreInitialization();
        return nativeLib.ioctlInt(nativePosixSupport, fd, request, arg);
    }

    @ExportMessage
    final int socket(int domain, int type, int protocol,
                    @CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) throws PosixException {
        checkNotInPreInitialization();
        return nativeLib.socket(nativePosixSupport, domain, type, protocol);
    }

    @ExportMessage
    final AcceptResult accept(int sockfd,
                    @CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) throws PosixException {
        checkNotInPreInitialization();
        return nativeLib.accept(nativePosixSupport, sockfd);
    }

    @ExportMessage
    final void bind(int sockfd, UniversalSockAddr addr,
                    @CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) throws PosixException {
        checkNotInPreInitialization();
        nativeLib.bind(nativePosixSupport, sockfd, addr);
    }

    @ExportMessage
    final void connect(int sockfd, UniversalSockAddr addr,
                    @CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) throws PosixException {
        checkNotInPreInitialization();
        nativeLib.connect(nativePosixSupport, sockfd, addr);
    }

    @ExportMessage
    final void listen(int sockfd, int backlog,
                    @CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) throws PosixException {
        checkNotInPreInitialization();
        nativeLib.listen(nativePosixSupport, sockfd, backlog);
    }

    @ExportMessage
    final UniversalSockAddr getpeername(int sockfd,
                    @CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) throws PosixException {
        checkNotInPreInitialization();
        return nativeLib.getpeername(nativePosixSupport, sockfd);
    }

    @ExportMessage
    final UniversalSockAddr getsockname(int sockfd,
                    @CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) throws PosixException {
        checkNotInPreInitialization();
        return nativeLib.getsockname(nativePosixSupport, sockfd);
    }

    @ExportMessage
    final int send(int sockfd, byte[] buf, int offset, int len, int flags,
                    @CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) throws PosixException {
        checkNotInPreInitialization();
        return nativeLib.send(nativePosixSupport, sockfd, buf, offset, len, flags);
    }

    @ExportMessage
    final int sendto(int sockfd, byte[] buf, int offset, int len, int flags, UniversalSockAddr destAddr,
                    @CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) throws PosixException {
        checkNotInPreInitialization();
        return nativeLib.sendto(nativePosixSupport, sockfd, buf, offset, len, flags, destAddr);
    }

    @ExportMessage
    final int recv(int sockfd, byte[] buf, int offset, int len, int flags,
                    @CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) throws PosixException {
        checkNotInPreInitialization();
        return nativeLib.recv(nativePosixSupport, sockfd, buf, offset, len, flags);
    }

    @ExportMessage
    final RecvfromResult recvfrom(int sockfd, byte[] buf, int offset, int len, int flags,
                    @CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) throws PosixException {
        checkNotInPreInitialization();
        return nativeLib.recvfrom(nativePosixSupport, sockfd, buf, offset, len, flags);
    }

    @ExportMessage
    final void shutdown(int sockfd, int how,
                    @CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) throws PosixException {
        checkNotInPreInitialization();
        nativeLib.shutdown(nativePosixSupport, sockfd, how);
    }

    @ExportMessage
    final int getsockopt(int sockfd, int level, int optname, byte[] optval, int optlen,
                    @CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) throws PosixException {
        checkNotInPreInitialization();
        return nativeLib.getsockopt(nativePosixSupport, sockfd, level, optname, optval, optlen);
    }

    @ExportMessage
    final void setsockopt(int sockfd, int level, int optname, byte[] optval, int optlen,
                    @CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) throws PosixException {
        checkNotInPreInitialization();
        nativeLib.setsockopt(nativePosixSupport, sockfd, level, optname, optval, optlen);
    }

    @ExportMessage
    final int inet_addr(Object src,
                    @CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) {
        checkNotInPreInitialization();
        return nativeLib.inet_addr(nativePosixSupport, src);
    }

    @ExportMessage
    final int inet_aton(Object src,
                    @CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) throws InvalidAddressException {
        checkNotInPreInitialization();
        return nativeLib.inet_aton(nativePosixSupport, src);
    }

    @ExportMessage
    final Object inet_ntoa(int address,
                    @CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) {
        checkNotInPreInitialization();
        return nativeLib.inet_ntoa(nativePosixSupport, address);
    }

    @ExportMessage
    final byte[] inet_pton(int family, Object src,
                    @CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) throws PosixException, InvalidAddressException {
        checkNotInPreInitialization();
        return nativeLib.inet_pton(nativePosixSupport, family, src);
    }

    @ExportMessage
    final Object inet_ntop(int family, byte[] src,
                    @CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) throws PosixException {
        checkNotInPreInitialization();
        return nativeLib.inet_ntop(nativePosixSupport, family, src);
    }

    @ExportMessage
    final Object gethostname(@CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) throws PosixException {
        checkNotInPreInitialization();
        return nativeLib.gethostname(nativePosixSupport);
    }

    @ExportMessage
    final Object[] getnameinfo(UniversalSockAddr addr, int flags,
                    @CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) throws GetAddrInfoException {
        checkNotInPreInitialization();
        return nativeLib.getnameinfo(nativePosixSupport, addr, flags);
    }

    @ExportMessage
    final AddrInfoCursor getaddrinfo(Object node, Object service, int family, int sockType, int protocol, int flags,
                    @CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) throws GetAddrInfoException {
        checkNotInPreInitialization();
        return nativeLib.getaddrinfo(nativePosixSupport, node, service, family, sockType, protocol, flags);
    }

    @ExportMessage
    final TruffleString crypt(TruffleString word, TruffleString salt,
                    @CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) throws PosixException {
        checkNotInPreInitialization();
        return nativeLib.crypt(nativePosixSupport, word, salt);
    }

    @ExportMessage
    final long semOpen(Object name, int openFlags, int mode, int value,
                    @CachedLibrary("this.nativePosixSupport") PosixSupportLibrary lib) throws PosixException {
        checkNotInPreInitialization();
        return lib.semOpen(nativePosixSupport, name, openFlags, mode, value);
    }

    @ExportMessage
    final void semClose(long handle,
                    @CachedLibrary("this.nativePosixSupport") PosixSupportLibrary lib) throws PosixException {
        checkNotInPreInitialization();
        lib.semClose(nativePosixSupport, handle);
    }

    @ExportMessage
    final void semUnlink(Object name,
                    @CachedLibrary("this.nativePosixSupport") PosixSupportLibrary lib) throws PosixException {
        checkNotInPreInitialization();
        lib.semUnlink(nativePosixSupport, name);
    }

    @ExportMessage
    final int semGetValue(long handle,
                    @CachedLibrary("this.nativePosixSupport") PosixSupportLibrary lib) throws PosixException {
        checkNotInPreInitialization();
        return lib.semGetValue(nativePosixSupport, handle);
    }

    @ExportMessage
    final void semPost(long handle,
                    @CachedLibrary("this.nativePosixSupport") PosixSupportLibrary lib) throws PosixException {
        checkNotInPreInitialization();
        lib.semPost(nativePosixSupport, handle);
    }

    @ExportMessage
    final void semWait(long handle,
                    @CachedLibrary("this.nativePosixSupport") PosixSupportLibrary lib) throws PosixException {
        checkNotInPreInitialization();
        lib.semWait(nativePosixSupport, handle);
    }

    @ExportMessage
    final boolean semTryWait(long handle,
                    @CachedLibrary("this.nativePosixSupport") PosixSupportLibrary lib) throws PosixException {
        checkNotInPreInitialization();
        return lib.semTryWait(nativePosixSupport, handle);
    }

    @ExportMessage
    final boolean semTimedWait(long handle, long deadlineNs,
                    @CachedLibrary("this.nativePosixSupport") PosixSupportLibrary lib) throws PosixException {
        checkNotInPreInitialization();
        return lib.semTimedWait(nativePosixSupport, handle, deadlineNs);
    }

    @ExportMessage
    final UniversalSockAddr createUniversalSockAddrInet4(Inet4SockAddr src,
                    @CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) {
        checkNotInPreInitialization();
        return nativeLib.createUniversalSockAddrInet4(nativePosixSupport, src);
    }

    @ExportMessage
    final UniversalSockAddr createUniversalSockAddrInet6(Inet6SockAddr src,
                    @CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) {
        checkNotInPreInitialization();
        return nativeLib.createUniversalSockAddrInet6(nativePosixSupport, src);
    }

    @ExportMessage
    final UniversalSockAddr createUniversalSockAddrUnix(UnixSockAddr src,
                    @CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) throws InvalidUnixSocketPathException {
        checkNotInPreInitialization();
        return nativeLib.createUniversalSockAddrUnix(nativePosixSupport, src);
    }

    @ExportMessage
    final Object createPathFromString(TruffleString path,
                    @CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) {
        if (inPreInitialization) {
            return PosixSupportLibrary.getUncached().createPathFromString(emulatedPosixSupport, path);
        }
        return nativeLib.createPathFromString(nativePosixSupport, path);
    }

    @ExportMessage
    final Object createPathFromBytes(byte[] path,
                    @CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) {
        if (inPreInitialization) {
            return PosixSupportLibrary.getUncached().createPathFromBytes(emulatedPosixSupport, path);
        }
        return nativeLib.createPathFromBytes(nativePosixSupport, path);
    }

    @ExportMessage
    final TruffleString getPathAsString(Object path,
                    @CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) {
        if (inPreInitialization) {
            return PosixSupportLibrary.getUncached().getPathAsString(emulatedPosixSupport, path);
        }
        return nativeLib.getPathAsString(nativePosixSupport, path);
    }

    @ExportMessage
    final Buffer getPathAsBytes(Object path,
                    @CachedLibrary("this.nativePosixSupport") PosixSupportLibrary nativeLib) {
        if (inPreInitialization) {
            return PosixSupportLibrary.getUncached().getPathAsBytes(emulatedPosixSupport, path);
        }
        return nativeLib.getPathAsBytes(nativePosixSupport, path);
    }
}
