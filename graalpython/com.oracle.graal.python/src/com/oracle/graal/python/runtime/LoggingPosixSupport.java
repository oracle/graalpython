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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.logging.Level;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.runtime.PosixSupportLibrary.AcceptResult;
import com.oracle.graal.python.runtime.PosixSupportLibrary.AddrInfoCursor;
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
import com.oracle.graal.python.runtime.PosixSupportLibrary.UnixSockAddr;
import com.oracle.graal.python.runtime.PosixSupportLibrary.UnsupportedPosixFeatureException;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.strings.TruffleString;

/**
 * Implementation of POSIX support that delegates to another instance and logs all the messages
 * together with their arguments, return values and exceptions. Note that:
 * <ul>
 * <li>data read from/written to files are not logged</li>
 * <li>all filenames including full paths are logged</li>
 * <li>only {@link PosixException} are logged</li>
 * <li>this class assumes default string/bytes encoding to keep it simple</li>
 * <li>logging must be enabled using the
 * {@code --log.python.com.oracle.graal.python.runtime.LoggingPosixSupport.level=FINER} option</li>
 * </ul>
 *
 * Logging levels:
 * <ul>
 * <li>FINER - all important messages</li>
 * <li>FINEST - supporting messages (e.g. path conversions) + top 5 frames of the call stack</li>
 * </ul>
 */
@ExportLibrary(PosixSupportLibrary.class)
public class LoggingPosixSupport extends PosixSupport {

    private static final TruffleLogger LOGGER = PythonLanguage.getLogger(LoggingPosixSupport.class);
    private static final Level DEFAULT_LEVEL = Level.FINER;

    protected final PosixSupport delegate;

    public LoggingPosixSupport(PosixSupport delegate) {
        this.delegate = delegate;
        LOGGER.log(Level.INFO, "Using " + delegate.getClass());
    }

    public static boolean isEnabled() {
        return LoggingPosixSupport.LOGGER.isLoggable(DEFAULT_LEVEL);
    }

    @Override
    public void setEnv(Env env) {
        delegate.setEnv(env);
    }

    @Override
    public final TruffleString getBackend() {
        logEnter(Level.FINEST, "getBackend", "");
        return logExit(Level.FINEST, "getBackend", "%s", delegate.getBackend());
    }

    @Override
    public final TruffleString strerror(int errorCode) {
        logEnter(Level.FINEST, "strerror", "%d", errorCode);
        return logExit(Level.FINEST, "strerror", "%s", delegate.strerror(errorCode));
    }

    @Override
    public final long getpid() {
        logEnter("getpid", "");
        return logExit("getpid", "%d", delegate.getpid());
    }

    @Override
    public final int umask(int mask) throws PosixException {
        logEnter("umask", "0%o", mask);
        try {
            return logExit("umask", "0%o", delegate.umask(mask));
        } catch (PosixException e) {
            throw logException("umask", e);
        }
    }

    @Override
    public final int openat(int dirFd, Object pathname, int flags, int mode) throws PosixException {
        logEnter("openAt", "%d, %s, 0x%x, 0%o", dirFd, pathname, flags, mode);
        try {
            return logExit("openAt", "%d", delegate.openat(dirFd, pathname, flags, mode));
        } catch (PosixException e) {
            throw logException("openAt", e);
        }
    }

    @Override
    public final int close(int fd) throws PosixException {
        logEnter("close", "%d", fd);
        try {
            return delegate.close(fd);
        } catch (PosixException e) {
            throw logException("close", e);
        }
    }

    @Override
    public final Buffer read(int fd, long length) throws PosixException {
        logEnter("read", "%d, %d", fd, length);
        try {
            Buffer retVal = delegate.read(fd, length);
            logExit("read", "%d", retVal.length);
            return retVal;
        } catch (PosixException e) {
            throw logException("read", e);
        }
    }

    @Override
    public final long write(int fd, Buffer data) throws PosixException {
        logEnter("write", "%d, %d", fd, data.length);
        try {
            return logExit("write", "%d", delegate.write(fd, data));
        } catch (PosixException e) {
            throw logException("write", e);
        }
    }

    @Override
    @TruffleBoundary
    public final int getWindowsConsoleType(int fd) {
        logEnter("getWindowsConsoleType", "%d", fd);
        return logExit("getWindowsConsoleType", "%d", delegate.getWindowsConsoleType(fd));
    }

    @Override
    @TruffleBoundary
    public final long writeWindowsConsole(int fd, Buffer data) throws PosixException {
        logEnter("writeWindowsConsole", "%d, %d", fd, data.length);
        try {
            return logExit("writeWindowsConsole", "%d", delegate.writeWindowsConsole(fd, data));
        } catch (PosixException e) {
            throw logException("writeWindowsConsole", e);
        }
    }

    @Override
    public final int dup(int fd) throws PosixException {
        logEnter("dup", "%d", fd);
        try {
            return logExit("dup", "%d", delegate.dup(fd));
        } catch (PosixException e) {
            throw logException("dup", e);
        }
    }

    @Override
    public final int dup2(int fd, int fd2, boolean inheritable) throws PosixException {
        logEnter("dup2", "%d, %d, %b", fd, fd2, inheritable);
        try {
            return logExit("dup2", "%d", delegate.dup2(fd, fd2, inheritable));
        } catch (PosixException e) {
            throw logException("dup2", e);
        }
    }

    @Override
    public final boolean getInheritable(int fd) throws PosixException {
        logEnter("getInheritable", "%d", fd);
        try {
            return logExit("getInheritable", "%b", delegate.getInheritable(fd));
        } catch (PosixException e) {
            throw logException("getInheritable", e);
        }
    }

    @Override
    public final void setInheritable(int fd, boolean inheritable) throws PosixException {
        logEnter("setInheritable", "%d, %b", fd, inheritable);
        try {
            delegate.setInheritable(fd, inheritable);
        } catch (PosixException e) {
            throw logException("setInheritable", e);
        }
    }

    @Override
    public final long getOsfHandle(int fd) throws PosixException {
        logEnter("getOsfHandle", "%d", fd);
        try {
            return logExit("getOsfHandle", "%d", delegate.getOsfHandle(fd));
        } catch (PosixException e) {
            throw logException("getOsfHandle", e);
        }
    }

    @Override
    public final int openOsfHandle(long handle, int flags) throws PosixException {
        logEnter("openOsfHandle", "%d, %d", handle, flags);
        try {
            return logExit("openOsfHandle", "%d", delegate.openOsfHandle(handle, flags));
        } catch (PosixException e) {
            throw logException("openOsfHandle", e);
        }
    }

    @Override
    public final int setMode(int fd, int mode) throws PosixException {
        logEnter("setMode", "%d, %d", fd, mode);
        try {
            return logExit("setMode", "%d", delegate.setMode(fd, mode));
        } catch (PosixException e) {
            throw logException("setMode", e);
        }
    }

    @Override
    public final void msvcrtLocking(int fd, int mode, long nbytes) throws PosixException {
        logEnter("msvcrtLocking", "%d, %d, %d", fd, mode, nbytes);
        try {
            delegate.msvcrtLocking(fd, mode, nbytes);
        } catch (PosixException e) {
            throw logException("msvcrtLocking", e);
        }
    }

    @Override
    public final int[] pipe() throws PosixException {
        logEnter("pipe", "");
        try {
            return logExit("pipe", "%s", delegate.pipe());
        } catch (PosixException e) {
            throw logException("pipe", e);
        }
    }

    @Override
    public final SelectResult select(int[] readfds, int[] writefds, int[] errorfds, Timeval timeout) throws PosixException {
        logEnter("select", "%s %s %s %s", readfds, writefds, errorfds, timeout);
        try {
            return logExit("select", "%s", delegate.select(readfds, writefds, errorfds, timeout));
        } catch (PosixException e) {
            throw logException("select", e);
        }
    }

    @Override
    public final void poll(int[] fds, int[] events, int[] revents, int timeout) throws PosixException {
        logEnter("poll", "%s %s %s", fds, events, timeout);
        try {
            delegate.poll(fds, events, revents, timeout);
            logExit("poll", "%s", revents);
        } catch (PosixException e) {
            throw logException("poll", e);
        }
    }

    @Override
    public final long lseek(int fd, long offset, int how) throws PosixException {
        logEnter("lseek", "%d, %d, %d", fd, offset, how);
        try {
            return logExit("lseek", "%d", delegate.lseek(fd, offset, how));
        } catch (PosixException e) {
            throw logException("lseek", e);
        }
    }

    @Override
    public final void ftruncate(int fd, long length) throws PosixException {
        logEnter("ftruncate", "%d, %d", fd, length);
        try {
            delegate.ftruncate(fd, length);
        } catch (PosixException e) {
            throw logException("ftruncate", e);
        }
    }

    @Override
    public final void truncate(Object path, long length) throws PosixException {
        logEnter("truncate", "%s, %d", path, length);
        try {
            delegate.truncate(path, length);
        } catch (PosixException e) {
            throw logException("truncate", e);
        }
    }

    @Override
    public final void fsync(int fd) throws PosixException {
        logEnter("fsync", "%d", fd);
        try {
            delegate.fsync(fd);
        } catch (PosixException e) {
            throw logException("fsync", e);
        }
    }

    @Override
    public final void flock(int fd, int operation) throws PosixException {
        logEnter("flock", "%d %d", fd, operation);
        try {
            delegate.flock(fd, operation);
        } catch (PosixException e) {
            throw logException("flock", e);
        }
    }

    @Override
    public final void fcntlLock(int fd, boolean blocking, int lockType, int whence, long start, long length) throws PosixException {
        logEnter("fcntlLock", "%d %s %d %d %d %d", fd, blocking, lockType, whence, start, length);
        try {
            delegate.fcntlLock(fd, blocking, lockType, whence, start, length);
        } catch (PosixException e) {
            throw logException("fcntlLock", e);
        }
    }

    @Override
    public final boolean getBlocking(int fd) throws PosixException {
        logEnter("getBlocking", "%d", fd);
        try {
            return logExit("getBlocking", "%b", delegate.getBlocking(fd));
        } catch (PosixException e) {
            throw logException("getBlocking", e);
        }
    }

    @Override
    public final void setBlocking(int fd, boolean blocking) throws PosixException {
        logEnter("setBlocking", "%d, %b", fd, blocking);
        try {
            delegate.setBlocking(fd, blocking);
        } catch (PosixException e) {
            throw logException("setBlocking", e);
        }
    }

    @Override
    public final int[] getTerminalSize(int fd) throws PosixException {
        logEnter("getTerminalSize", "%d", fd);
        try {
            return logExit("getTerminalSize", "%s", delegate.getTerminalSize(fd));
        } catch (PosixException e) {
            throw logException("getTerminalSize", e);
        }
    }

    @Override
    public final long sysconf(int name) throws PosixException {
        logEnter("sysconf", "%d", name);
        try {
            return logExit("sysconf", "%s", delegate.sysconf(name));
        } catch (PosixException e) {
            throw logException("sysconf", e);
        }
    }

    @Override
    @TruffleBoundary
    public final long[] fstatat(int dirFd, Object pathname, boolean followSymlinks) throws PosixException {
        logEnter("fstatAt", "%d, %s, %b", dirFd, pathname, followSymlinks);
        try {
            return logExit("fstatAt", "%s", delegate.fstatat(dirFd, pathname, followSymlinks));
        } catch (PosixException e) {
            throw logException("fstatAt", e);
        }
    }

    @Override
    @TruffleBoundary
    public final long[] fstat(int fd) throws PosixException {
        logEnter("fstat", "%d", fd);
        try {
            return logExit("fstat", "%s", delegate.fstat(fd));
        } catch (PosixException e) {
            throw logException("fstat", e);
        }
    }

    @Override
    @TruffleBoundary
    public final long[] statvfs(Object path) throws PosixException {
        logEnter("statvfs", "%s", path);
        try {
            return logExit("statvfs", "%s", delegate.statvfs(path));
        } catch (PosixException e) {
            throw logException("statvfs", e);
        }
    }

    @Override
    @TruffleBoundary
    public final long[] fstatvfs(int fd) throws PosixException {
        logEnter("fstatvfs", "%d", fd);
        try {
            return logExit("fstatvfs", "%s", delegate.fstatvfs(fd));
        } catch (PosixException e) {
            throw logException("fstatvfs", e);
        }
    }

    @Override
    public final Object[] uname() throws PosixException {
        logEnter("uname", "");
        try {
            return logExit("uname", "%s", delegate.uname());
        } catch (PosixException e) {
            throw logException("uname", e);
        }
    }

    @Override
    @TruffleBoundary
    public final WindowsVersion getWindowsVersion() throws PosixException {
        logEnter("getWindowsVersion", "");
        try {
            return logExit("getWindowsVersion", "%s", delegate.getWindowsVersion());
        } catch (PosixException e) {
            throw logException("getWindowsVersion", e);
        }
    }

    @Override
    @TruffleBoundary
    public final void unlinkat(int dirFd, Object pathname, boolean rmdir) throws PosixException {
        logEnter("unlinkAt", "%d, %s, %b", dirFd, pathname, rmdir);
        try {
            delegate.unlinkat(dirFd, pathname, rmdir);
        } catch (PosixException e) {
            throw logException("unlinkAt", e);
        }
    }

    @Override
    @TruffleBoundary
    public final void linkat(int oldFdDir, Object oldPath, int newFdDir, Object newPath, int flags) throws PosixException {
        logEnter("linkAt", "%d, %s, %d, %s, %d", oldFdDir, oldPath, newFdDir, newPath, flags);
        try {
            delegate.linkat(oldFdDir, oldPath, newFdDir, newPath, flags);
        } catch (PosixException e) {
            throw logException("symlinkAt", e);
        }
    }

    @Override
    @TruffleBoundary
    public final void symlinkat(Object target, int linkpathDirFd, Object linkpath) throws PosixException {
        logEnter("symlinkAt", "%s, %d, %s", target, linkpathDirFd, linkpath);
        try {
            delegate.symlinkat(target, linkpathDirFd, linkpath);
        } catch (PosixException e) {
            throw logException("symlinkAt", e);
        }
    }

    @Override
    @TruffleBoundary
    public final void mkdirat(int dirFd, Object pathname, int mode) throws PosixException {
        logEnter("mkdirAt", "%d, %s, 0%o", dirFd, pathname, mode);
        try {
            delegate.mkdirat(dirFd, pathname, mode);
        } catch (PosixException e) {
            throw logException("mkdirAt", e);
        }
    }

    @Override
    @TruffleBoundary
    public final Object getcwd() throws PosixException {
        logEnter("getcwd", "");
        try {
            return logExit("getcwd", "%s", delegate.getcwd());
        } catch (PosixException e) {
            throw logException("getcwd", e);
        }
    }

    @Override
    @TruffleBoundary
    public final void chdir(Object path) throws PosixException {
        logEnter("chdir", "%s", path);
        try {
            delegate.chdir(path);
        } catch (PosixException e) {
            throw logException("chdir", e);
        }
    }

    @Override
    @TruffleBoundary
    public final void fchdir(int fd) throws PosixException {
        logEnter("fchdir", "%d", fd);
        try {
            delegate.fchdir(fd);
        } catch (PosixException e) {
            throw logException("fchdir", e);
        }
    }

    @Override
    @TruffleBoundary
    public final boolean isatty(int fd) {
        logEnter("isatty", "%d", fd);
        return logExit("isatty", "%b", delegate.isatty(fd));
    }

    @Override
    @TruffleBoundary
    public final Object opendir(Object path) throws PosixException {
        logEnter("opendir", "%s", path);
        try {
            return logExit("opendir", "%s", delegate.opendir(path));
        } catch (PosixException e) {
            throw logException("opendir", e);
        }
    }

    @Override
    @TruffleBoundary
    public final Object fdopendir(int fd) throws PosixException {
        logEnter("fdopendir", "%d", fd);
        try {
            return logExit("fdopendir", "%s", delegate.fdopendir(fd));
        } catch (PosixException e) {
            throw logException("fdopendir", e);
        }
    }

    @Override
    @TruffleBoundary
    public final void closedir(Object dirStream) throws PosixException {
        logEnter("closedir", "%s", dirStream);
        try {
            delegate.closedir(dirStream);
        } catch (PosixException e) {
            throw logException("closedir", e);
        }
    }

    @Override
    @TruffleBoundary
    public final Object readdir(Object dirStream) throws PosixException {
        logEnter("readdir", "%s", dirStream);
        try {
            return logExit("readdir", "%s", delegate.readdir(dirStream));
        } catch (PosixException e) {
            throw logException("readdir", e);
        }
    }

    @Override
    @TruffleBoundary
    public final void rewinddir(Object dirStream) {
        logEnter("rewinddir", "%s", dirStream);
        delegate.rewinddir(dirStream);
    }

    @Override
    @TruffleBoundary
    public final Object dirEntryGetName(Object dirEntry) throws PosixException {
        logEnter("dirEntryGetName", "%s", dirEntry);
        try {
            return logExit("dirEntryGetName", "%s", delegate.dirEntryGetName(dirEntry));
        } catch (PosixException e) {
            throw logException("dirEntryGetName", e);
        }
    }

    @Override
    @TruffleBoundary
    public final Object dirEntryGetPath(Object dirEntry, Object scandirPath) throws PosixException {
        logEnter("dirEntryGetPath", "%s, %s", dirEntry, scandirPath);
        try {
            return logExit("dirEntryGetPath", "%s", delegate.dirEntryGetPath(dirEntry, scandirPath));
        } catch (PosixException e) {
            throw logException("dirEntryGetPath", e);
        }
    }

    @Override
    @TruffleBoundary
    public final long dirEntryGetInode(Object dirEntry) throws PosixException {
        logEnter("dirEntryGetInode", "%s", dirEntry);
        try {
            return logExit("dirEntryGetInode", "%d", delegate.dirEntryGetInode(dirEntry));
        } catch (PosixException e) {
            throw logException("dirEntryGetInode", e);
        }
    }

    @Override
    @TruffleBoundary
    public final int dirEntryGetType(Object dirEntry) {
        logEnter("dirEntryGetType", "%s", dirEntry);
        return logExit("dirEntryGetType", "%d", delegate.dirEntryGetType(dirEntry));
    }

    @Override
    @TruffleBoundary
    public final void utimensat(int dirFd, Object pathname, long[] timespec, boolean followSymlinks) throws PosixException {
        logEnter("utimeNsAt", "%d, %s, %s, %b", dirFd, pathname, timespec, followSymlinks);
        try {
            delegate.utimensat(dirFd, pathname, timespec, followSymlinks);
        } catch (PosixException e) {
            throw logException("utimeNsAt", e);
        }
    }

    @Override
    @TruffleBoundary
    public final void futimens(int fd, long[] timespec) throws PosixException {
        logEnter("futimeNs", "%d, %s", fd, timespec);
        try {
            delegate.futimens(fd, timespec);
        } catch (PosixException e) {
            throw logException("futimeNs", e);
        }
    }

    @Override
    @TruffleBoundary
    public final void futimes(int fd, Timeval[] timeval) throws PosixException {
        logEnter("futimes", "%d, %s", fd, timeval);
        try {
            delegate.futimes(fd, timeval);
        } catch (PosixException e) {
            throw logException("futimes", e);
        }
    }

    @Override
    @TruffleBoundary
    public final void lutimes(Object filename, Timeval[] timeval) throws PosixException {
        logEnter("lutimes", "%s, %s", filename, timeval);
        try {
            delegate.lutimes(filename, timeval);
        } catch (PosixException e) {
            throw logException("lutimes", e);
        }
    }

    @Override
    @TruffleBoundary
    public final void utimes(Object filename, Timeval[] timeval) throws PosixException {
        logEnter("utimes", "%s, %s", filename, timeval);
        try {
            delegate.utimes(filename, timeval);
        } catch (PosixException e) {
            throw logException("utimes", e);
        }
    }

    @Override
    @TruffleBoundary
    public final void renameat(int oldDirFd, Object oldPath, int newDirFd, Object newPath) throws PosixException {
        logEnter("renameAt", "%d, %s, %d, %s", oldDirFd, oldPath, newDirFd, newPath);
        try {
            delegate.renameat(oldDirFd, oldPath, newDirFd, newPath);
        } catch (PosixException e) {
            throw logException("renameAt", e);
        }
    }

    @Override
    @TruffleBoundary
    public final void replaceat(int oldDirFd, Object oldPath, int newDirFd, Object newPath) throws PosixException {
        logEnter("replaceAt", "%d, %s, %d, %s", oldDirFd, oldPath, newDirFd, newPath);
        try {
            delegate.replaceat(oldDirFd, oldPath, newDirFd, newPath);
        } catch (PosixException e) {
            throw logException("replaceAt", e);
        }
    }

    @Override
    @TruffleBoundary
    public final boolean faccessat(int dirFd, Object path, int mode, boolean effectiveIds, boolean followSymlinks) throws UnsupportedPosixFeatureException {
        logEnter("faccessAt", "%d, %s, 0%o, %b, %b", dirFd, path, mode, effectiveIds, followSymlinks);
        try {
            return logExit("faccessAt", "%b", delegate.faccessat(dirFd, path, mode, effectiveIds, followSymlinks));
        } catch (UnsupportedPosixFeatureException e) {
            throw logException("faccessAt", e);
        }
    }

    @Override
    @TruffleBoundary
    public final void fchmodat(int dirFd, Object path, int mode, boolean followSymlinks) throws PosixException {
        logEnter("fchmodat", "%d, %s, 0%o, %b", dirFd, path, mode, followSymlinks);
        try {
            delegate.fchmodat(dirFd, path, mode, followSymlinks);
        } catch (PosixException e) {
            throw logException("fchmodat", e);
        }
    }

    @Override
    @TruffleBoundary
    public final void fchmod(int fd, int mode) throws PosixException {
        logEnter("fchmod", "%d, 0%o", fd, mode);
        try {
            delegate.fchmod(fd, mode);
        } catch (PosixException e) {
            throw logException("fchmod", e);
        }
    }

    @Override
    @TruffleBoundary
    public final void fchownat(int dirFd, Object path, long owner, long group, boolean followSymlinks) throws PosixException {
        logEnter("fchownat", "%d, %s, %d, %d, %b", dirFd, path, owner, group, followSymlinks);
        try {
            delegate.fchownat(dirFd, path, owner, group, followSymlinks);
        } catch (PosixException e) {
            throw logException("fchownat", e);
        }
    }

    @Override
    @TruffleBoundary
    public final void fchown(int fd, long owner, long group) throws PosixException {
        logEnter("fchown", "%d, %d, %d", fd, owner, group);
        try {
            delegate.fchown(fd, owner, group);
        } catch (PosixException e) {
            throw logException("fchown", e);
        }
    }

    @Override
    public final Object readlinkat(int dirFd, Object path) throws PosixException {
        logEnter("readlinkat", "%d, %s", dirFd, path);
        try {
            return logExit("readlinkat", "%s", delegate.readlinkat(dirFd, path));
        } catch (PosixException e) {
            throw logException("readlinkat", e);
        }
    }

    @Override
    @TruffleBoundary
    public final void kill(long pid, int signal) throws PosixException {
        logEnter("kill", "%d, %d", pid, signal);
        try {
            delegate.kill(pid, signal);
        } catch (PosixException e) {
            throw logException("kill", e);
        }
    }

    @Override
    @TruffleBoundary
    public final void raise(int signal) throws PosixException {
        logEnter("raise", "%d", signal);
        try {
            delegate.raise(signal);
        } catch (PosixException e) {
            throw logException("raise", e);
        }
    }

    @Override
    @TruffleBoundary
    public final int alarm(int seconds) throws PosixException {
        logEnter("alarm", "%d", seconds);
        try {
            return logExit("alarm", "%d", delegate.alarm(seconds));
        } catch (PosixException e) {
            throw logException("alarm", e);
        }
    }

    @Override
    @TruffleBoundary
    public final Timeval[] getitimer(int which) throws PosixException {
        logEnter("getitimer", "%d", which);
        try {
            return logExit("getitimer", "%s", delegate.getitimer(which));
        } catch (PosixException e) {
            throw logException("getitimer", e);
        }
    }

    @Override
    @TruffleBoundary
    public final Timeval[] setitimer(int which, Timeval delay, Timeval interval) throws PosixException {
        logEnter("setitimer", "%d, %s, %s", which, delay, interval);
        try {
            return logExit("setitimer", "%s", delegate.setitimer(which, delay, interval));
        } catch (PosixException e) {
            throw logException("setitimer", e);
        }
    }

    @Override
    @TruffleBoundary
    public final void signalSelf(int signal) throws PosixException {
        logEnter("signalSelf", "%d", signal);
        try {
            delegate.signalSelf(signal);
        } catch (PosixException e) {
            throw logException("signalSelf", e);
        }
    }

    @Override
    @TruffleBoundary
    public final void killpg(long pgid, int signal) throws PosixException {
        logEnter("killpg", "%d, %d", pgid, signal);
        try {
            delegate.killpg(pgid, signal);
        } catch (PosixException e) {
            throw logException("killpg", e);
        }
    }

    @Override
    @TruffleBoundary
    public Object mmap(Node location, long length, int prot, int flags, int fd, long offset, Object tagname) throws PosixException {
        logEnter("mmap", "%d, %d, %d, %d, %d, %s", length, prot, flags, fd, offset, tagname);
        try {
            return logExit("mmap", "%s", delegate.mmap(location, length, prot, flags, fd, offset, tagname));
        } catch (PosixException e) {
            throw logException("mmap", e);
        }
    }

    @Override
    @TruffleBoundary
    public final long[] waitpid(Node location, long pid, int options) throws PosixException {
        logEnter("waitpid", "%d, %d", pid, options);
        try {
            return logExit("waitpid", "%s", delegate.waitpid(location, pid, options));
        } catch (PosixException e) {
            throw logException("waitpid", e);
        }
    }

    @Override
    @TruffleBoundary
    public byte mmapReadByte(Object mmap, long index) throws PosixException {
        logEnter("mmapReadByte", "%s, %d", mmap, index);
        try {
            return logExit("mmapReadByte", "%s", delegate.mmapReadByte(mmap, index));
        } catch (PosixException e) {
            throw logException("mmapReadByte", e);
        }
    }

    @Override
    @TruffleBoundary
    public void mmapWriteByte(Object mmap, long index, byte value) throws PosixException {
        logEnter("mmapWriteByte", "%s, %d, %d", mmap, index, value);
        try {
            delegate.mmapWriteByte(mmap, index, value);
        } catch (PosixException e) {
            throw logException("mmapWriteByte", e);
        }
    }

    @Override
    @TruffleBoundary
    public final boolean wcoredump(int status) {
        logEnter("wcoredump", "%d", status);
        return logExit("wcoredump", "%b", delegate.wcoredump(status));
    }

    @Override
    @TruffleBoundary
    public final boolean wifcontinued(int status) {
        logEnter("wifcontinued", "%d", status);
        return logExit("wifcontinued", "%b", delegate.wifcontinued(status));
    }

    @Override
    @TruffleBoundary
    public final boolean wifstopped(int status) {
        logEnter("wifstopped", "%d", status);
        return logExit("wifstopped", "%b", delegate.wifstopped(status));
    }

    @Override
    @TruffleBoundary
    public final boolean wifsignaled(int status) {
        logEnter("wifsignaled", "%d", status);
        return logExit("wifsignaled", "%b", delegate.wifsignaled(status));
    }

    @Override
    @TruffleBoundary
    public final boolean wifexited(int status) {
        logEnter("wifexited", "%d", status);
        return logExit("wifexited", "%b", delegate.wifexited(status));
    }

    @Override
    @TruffleBoundary
    public final int wexitstatus(int status) {
        logEnter("wexitstatus", "%d", status);
        return logExit("wexitstatus", "%d", delegate.wexitstatus(status));
    }

    @Override
    @TruffleBoundary
    public final int wtermsig(int status) {
        logEnter("wtermsig", "%d", status);
        return logExit("wtermsig", "%d", delegate.wtermsig(status));
    }

    @Override
    @TruffleBoundary
    public final int wstopsig(int status) {
        logEnter("wstopsig", "%d", status);
        return logExit("wstopsig", "%d", delegate.wstopsig(status));
    }

    @Override
    @TruffleBoundary
    public final long getuid() {
        logEnter("getuid", "");
        return logExit("getuid", "%d", delegate.getuid());
    }

    @Override
    @TruffleBoundary
    public final long geteuid() throws UnsupportedPosixFeatureException {
        logEnter("geteuid", "");
        try {
        return logExit("geteuid", "%d", delegate.geteuid());
        } catch (UnsupportedPosixFeatureException e) {
            throw logException("geteuid", e);
        }
    }

    @Override
    @TruffleBoundary
    public final long getgid() {
        logEnter("getgid", "");
        return logExit("getgid", "%d", delegate.getgid());
    }

    @Override
    @TruffleBoundary
    public final long getegid() throws UnsupportedPosixFeatureException {
        logEnter("getegid", "");
        try {
        return logExit("getegid", "%d", delegate.getegid());
        } catch (UnsupportedPosixFeatureException e) {
            throw logException("getegid", e);
        }
    }

    @Override
    @TruffleBoundary
    public final long getppid() throws UnsupportedPosixFeatureException {
        logEnter("getppid", "");
        try {
            return logExit("getppid", "%d", delegate.getppid());
        } catch (UnsupportedPosixFeatureException e) {
            throw logException("getppid", e);
        }
    }

    @Override
    @TruffleBoundary
    public final long getpgid(long pid) throws PosixException {
        logEnter("getpgid", "%d", pid);
        try {
            return logExit("getpgid", "%d", delegate.getpgid(pid));
        } catch (PosixException e) {
            throw logException("getpgid", e);
        }
    }

    @Override
    @TruffleBoundary
    public final void setpgid(long pid, long pgid) throws PosixException {
        logEnter("setpgid", "%d, %d", pid, pgid);
        try {
            delegate.setpgid(pid, pgid);
        } catch (PosixException e) {
            throw logException("setpgid", e);
        }
    }

    @Override
    @TruffleBoundary
    public final long getpgrp() throws UnsupportedPosixFeatureException {
        logEnter("getpgrp", "");
        try {
            return logExit("getpgrp", "%d", delegate.getpgrp());
        } catch (UnsupportedPosixFeatureException e) {
            throw logException("getpgrp", e);
        }
    }

    @Override
    @TruffleBoundary
    public final long getsid(long pid) throws PosixException {
        logEnter("getsid", "%d", pid);
        try {
            return logExit("getsid", "%d", delegate.getsid(pid));
        } catch (PosixException e) {
            throw logException("getsid", e);
        }
    }

    @Override
    @TruffleBoundary
    public final long setsid() throws PosixException {
        logEnter("setsid", "");
        try {
            return logExit("getsid", "%d", delegate.setsid());
        } catch (PosixException e) {
            throw logException("setsid", e);
        }
    }

    @Override
    @TruffleBoundary
    public final long[] getgroups() throws PosixException {
        logEnter("getgroups", "");
        try {
            return logExit("getgroups", "%s", delegate.getgroups());
        } catch (PosixException e) {
            throw logException("getgroups", e);
        }
    }

    @Override
    @TruffleBoundary
    public final RusageResult getrusage(int who) throws PosixException {
        logEnter("getrusage", "%d", who);
        try {
            return logExit("getrusage", "%s", delegate.getrusage(who));
        } catch (PosixException e) {
            throw logException("getrusage", e);
        }
    }

    @Override
    @TruffleBoundary
    public int mmapReadBytes(Object mmap, long index, byte[] bytes, int length) throws PosixException {
        logEnter("mmapReadBytes", "%s, %d, %d", mmap, index, length);
        try {
            return logExit("mmapReadBytes", "%s", delegate.mmapReadBytes(mmap, index, bytes, length));
        } catch (PosixException e) {
            throw logException("mmapReadBytes", e);
        }
    }

    @Override
    @TruffleBoundary
    public final OpenPtyResult openpty() throws PosixException {
        logEnter("openpty", "");
        try {
            return logExit("openpty", "%s", delegate.openpty());
        } catch (PosixException e) {
            throw logException("openpty", e);
        }
    }

    @Override
    @TruffleBoundary
    public final TruffleString ctermid() throws PosixException {
        logEnter("ctermid", "");
        try {
            return logExit("ctermid", "%s", delegate.ctermid());
        } catch (PosixException e) {
            throw logException("ctermid", e);
        }
    }

    @Override
    @TruffleBoundary
    public final void setenv(Object name, Object value, boolean overwrite) throws PosixException {
        logEnter("setenv", "%s, %s, %b", name, value, overwrite);
        try {
            delegate.setenv(name, value, overwrite);
        } catch (PosixException e) {
            throw logException("setenv", e);
        }
    }

    @Override
    @TruffleBoundary
    public final void unsetenv(Object name) throws PosixException {
        logEnter("unsetenv", "%s", name);
        try {
            delegate.unsetenv(name);
        } catch (PosixException e) {
            throw logException("unsetenv", e);
        }
    }

    @Override
    @TruffleBoundary
    public void mmapWriteBytes(Object mmap, long index, byte[] bytes, int length) throws PosixException {
        logEnter("mmapWriteBytes", "%s, %d, %d", mmap, index, length);
        try {
            delegate.mmapWriteBytes(mmap, index, bytes, length);
        } catch (PosixException e) {
            throw logException("mmapWriteBytes", e);
        }
    }

    @Override
    @TruffleBoundary
    public final int forkExec(Object[] executables, Object[] args, Object cwd, Object[] env, int stdinReadFd, int stdinWriteFd, int stdoutReadFd, int stdoutWriteFd, int stderrReadFd, int stderrWriteFd,
                    int errPipeReadFd, int errPipeWriteFd, boolean closeFds, boolean restoreSignals, boolean callSetsid, int pgidToSet, int[] fdsToKeep, boolean allowVFork) throws PosixException {
        logEnter("forkExec", "%s, %s, %s, %s, %d, %d, %d, %d, %d, %d, %d, %d, %b, %b, %b, %d, %s, %b", executables, args, cwd, env, stdinReadFd, stdinWriteFd, stdoutReadFd, stdoutWriteFd,
                        stderrReadFd,
                        stderrWriteFd, errPipeReadFd, errPipeWriteFd, closeFds, restoreSignals, callSetsid, pgidToSet, fdsToKeep, allowVFork);
        try {
            return logExit("forkExec", "%d", delegate.forkExec(executables, args, cwd, env, stdinReadFd, stdinWriteFd, stdoutReadFd, stdoutWriteFd, stderrReadFd, stderrWriteFd, errPipeReadFd,
                            errPipeWriteFd, closeFds, restoreSignals, callSetsid, pgidToSet, fdsToKeep, allowVFork));
        } catch (PosixException e) {
            throw logException("forkExec", e);
        }
    }

    @Override
    @TruffleBoundary
    public void mmapFlush(Object mmap, long offset, long length) throws PosixException {
        logEnter("mmapFlush", "%s, %d, %d", mmap, offset, length);
        try {
            delegate.mmapFlush(mmap, offset, length);
        } catch (PosixException e) {
            throw logException("mmapFlush", e);
        }
    }

    @Override
    @TruffleBoundary
    public long mmapGetPointer(Object mmap) throws UnsupportedPosixFeatureException {
        logEnter("mmapGetPointer", "%s", mmap);
        try {
            return delegate.mmapGetPointer(mmap);
        } catch (UnsupportedPosixFeatureException e) {
            throw logException("mmapGetPointer", e);
        }
    }

    @Override
    @TruffleBoundary
    public final void execv(Object pathname, Object[] args) throws PosixException {
        logEnter("execv", "%s, %s", pathname, args);
        try {
            delegate.execv(pathname, args);
        } catch (PosixException e) {
            throw logException("execv", e);
        }
    }

    @Override
    @TruffleBoundary
    public void mmapUnmap(Object mmap, long length) throws PosixException {
        logEnter("mmapUnmap", "%s %d", mmap, length);
        try {
            delegate.mmapUnmap(mmap, length);
        } catch (PosixException e) {
            throw logException("mmapUnmap", e);
        }
    }

    @Override
    @TruffleBoundary
    public PwdResult getpwuid(long uid) throws PosixException {
        logEnter("getpwuid", "%d", uid);
        try {
            return logExit("getpwuid", "%s", delegate.getpwuid(uid));
        } catch (PosixException e) {
            throw logException("getpwuid", e);
        }
    }

    @Override
    @TruffleBoundary
    public PwdResult getpwnam(Object name) throws PosixException {
        logEnter("getpwnam", "%s", name);
        try {
            return logExit("getpwnam", "%s", delegate.getpwnam(name));
        } catch (PosixException e) {
            throw logException("getpwnam", e);
        }
    }

    @Override
    @TruffleBoundary
    public boolean hasGetpwentries() {
        return logExit("hasGetpwentries", "%b", delegate.hasGetpwentries());
    }

    @Override
    @TruffleBoundary
    public PwdResult[] getpwentries() throws PosixException {
        logEnter("getpwentries", "");
        try {
            return logExit("getpwentries", "%s", delegate.getpwentries());
        } catch (PosixException e) {
            throw logException("getpwentries", e);
        }
    }

    @ExportMessage
    final int ioctlBytes(int fd, long request, byte[] arg,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) throws PosixException {
        logEnter("ioctl", "%d %d %s", fd, request, arg);
        try {
            return logExit("ioctl", "%d", lib.ioctlBytes(delegate, fd, request, arg));
        } catch (PosixException e) {
            throw logException("ioctl", e);
        }
    }

    @ExportMessage
    final int ioctlInt(int fd, long request, int arg,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) throws PosixException {
        logEnter("ioctl", "%d %d %d", fd, request, arg);
        try {
            return logExit("ioctl", "%d", lib.ioctlInt(delegate, fd, request, arg));
        } catch (PosixException e) {
            throw logException("ioctl", e);
        }
    }

    @Override
    @TruffleBoundary
    public final int system(Object command) {
        logEnter("system", "%s", command);
        return logExit("system", "%d", delegate.system(command));
    }

    @Override
    @TruffleBoundary
    public final int socket(int domain, int type, int protocol) throws PosixException {
        logEnter("socket", "%d, %d, %d", domain, type, protocol);
        try {
            return logExit("socket", "%d", delegate.socket(domain, type, protocol));
        } catch (PosixException e) {
            throw logException("socket", e);
        }
    }

    @Override
    @TruffleBoundary
    public final AcceptResult accept(int sockfd) throws PosixException {
        logEnter("accept", "%d", sockfd);
        try {
            return logExit("accept", "%s", delegate.accept(sockfd));
        } catch (PosixException e) {
            throw logException("accept", e);
        }
    }

    @Override
    @TruffleBoundary
    public final void bind(int sockfd, UniversalSockAddr addr) throws PosixException {
        logEnter("bind", "%d, %s", sockfd, addr);
        try {
            delegate.bind(sockfd, addr);
        } catch (PosixException e) {
            throw logException("bind", e);
        }
    }

    @Override
    @TruffleBoundary
    public final void connect(int sockfd, UniversalSockAddr addr) throws PosixException {
        logEnter("connect", "%d, %s", sockfd, addr);
        try {
            delegate.connect(sockfd, addr);
        } catch (PosixException e) {
            throw logException("connect", e);
        }
    }

    @Override
    @TruffleBoundary
    public final void listen(int sockfd, int backlog) throws PosixException {
        logEnter("listen", "%d, %d", sockfd, backlog);
        try {
            delegate.listen(sockfd, backlog);
        } catch (PosixException e) {
            throw logException("listen", e);
        }
    }

    @Override
    @TruffleBoundary
    public final UniversalSockAddr getpeername(int sockfd) throws PosixException {
        logEnter("getpeername", "%d", sockfd);
        try {
            return logExit("getpeername", "%s", delegate.getpeername(sockfd));
        } catch (PosixException e) {
            throw logException("getpeername", e);
        }
    }

    @Override
    @TruffleBoundary
    public final UniversalSockAddr getsockname(int sockfd) throws PosixException {
        logEnter("getsockname", "%d", sockfd);
        try {
            return logExit("getsockname", "%s", delegate.getsockname(sockfd));
        } catch (PosixException e) {
            throw logException("getsockname", e);
        }
    }

    @Override
    @TruffleBoundary
    public final int send(int sockfd, byte[] buf, int offset, int len, int flags) throws PosixException {
        logEnter("send", "%d, %d, %d, %d", sockfd, offset, len, flags);
        try {
            return logExit("send", "%d", delegate.send(sockfd, buf, offset, len, flags));
        } catch (PosixException e) {
            throw logException("send", e);
        }
    }

    @Override
    @TruffleBoundary
    public final int sendto(int sockfd, byte[] buf, int offset, int len, int flags, UniversalSockAddr destAddr) throws PosixException {
        logEnter("sendto", "%d, %d, %d, %d, %s", sockfd, offset, len, flags, destAddr);
        try {
            return logExit("sendto", "%d", delegate.sendto(sockfd, buf, offset, len, flags, destAddr));
        } catch (PosixException e) {
            throw logException("sendto", e);
        }
    }

    @Override
    @TruffleBoundary
    public final int recv(int sockfd, byte[] buf, int offset, int len, int flags) throws PosixException {
        logEnter("recv", "%d, %d, %d, %d", sockfd, offset, len, flags);
        try {
            return logExit("recv", "%d", delegate.recv(sockfd, buf, offset, len, flags));
        } catch (PosixException e) {
            throw logException("recv", e);
        }
    }

    @Override
    @TruffleBoundary
    public final RecvfromResult recvfrom(int sockfd, byte[] buf, int offset, int len, int flags) throws PosixException {
        logEnter("recvfrom", "%d, %d, %d, %d", sockfd, offset, len, flags);
        try {
            return logExit("recvfrom", "%s", delegate.recvfrom(sockfd, buf, offset, len, flags));
        } catch (PosixException e) {
            throw logException("recvfrom", e);
        }
    }

    @Override
    @TruffleBoundary
    public final void shutdown(int sockfd, int how) throws PosixException {
        logEnter("shutdown", "%d, %d", sockfd, how);
        try {
            delegate.shutdown(sockfd, how);
        } catch (PosixException e) {
            throw logException("shutdown", e);
        }
    }

    @Override
    @TruffleBoundary
    public final int getsockopt(int sockfd, int level, int optname, byte[] optval, int optlen) throws PosixException {
        logEnter("getsockopt", "%d, %d, %d, %s, %d", sockfd, level, optname, optval, optlen);
        try {
            return logExit("getsockopt", "%d", delegate.getsockopt(sockfd, level, optname, optval, optlen));
        } catch (PosixException e) {
            throw logException("getsockopt", e);
        }
    }

    @Override
    @TruffleBoundary
    public final void setsockopt(int sockfd, int level, int optname, byte[] optval, int optlen) throws PosixException {
        logEnter("setsockopt", "%d, %d, %d, %s, %d", sockfd, level, optname, optval, optlen);
        try {
            delegate.setsockopt(sockfd, level, optname, optval, optlen);
        } catch (PosixException e) {
            throw logException("setsockopt", e);
        }
    }

    @Override
    @TruffleBoundary
    public final int inet_addr(Object src) {
        logEnter("inet_addr", "%s", src);
        return logExit("inet_addr", "%d", delegate.inet_addr(src));
    }

    @Override
    @TruffleBoundary
    public final int inet_aton(Object src) throws InvalidAddressException {
        logEnter("inet_aton", "%s", src);
        try {
            return logExit("inet_aton", "%d", delegate.inet_aton(src));
        } catch (InvalidAddressException e) {
            throw logException("inet_aton", e);
        }
    }

    @Override
    @TruffleBoundary
    public final Object inet_ntoa(int address) {
        logEnter("inet_ntoa", "%d", address);
        return logExit("inet_ntoa", "%s", delegate.inet_ntoa(address));
    }

    @Override
    @TruffleBoundary
    public final byte[] inet_pton(int family, Object src) throws PosixException, InvalidAddressException {
        logEnter("inet_pton", "%d, %s", family, src);
        try {
            return logExit("inet_pton", "%s", delegate.inet_pton(family, src));
        } catch (PosixException e) {
            throw logException("inet_pton", e);
        } catch (InvalidAddressException e) {
            throw logException("inet_pton", e);
        }
    }

    @Override
    @TruffleBoundary
    public final Object inet_ntop(int family, byte[] src) throws PosixException {
        logEnter("inet_ntop", "%d, %s", family, src);
        try {
            return logExit("inet_ntop", "%s", delegate.inet_ntop(family, src));
        } catch (PosixException e) {
            throw logException("inet_ntop", e);
        }
    }

    @Override
    @TruffleBoundary
    public final Object gethostname() throws PosixException {
        logEnter("gethostname", "");
        try {
            return logExit("gethostname", "%s", delegate.gethostname());
        } catch (PosixException e) {
            throw logException("gethostname", e);
        }
    }

    @Override
    @TruffleBoundary
    public final Object[] getnameinfo(UniversalSockAddr addr, int flags) throws UnsupportedPosixFeatureException, GetAddrInfoException {
        logEnter("getnameinfo", "%s, %d", addr, flags);
        try {
            return logExit("getnameinfo", "%s", delegate.getnameinfo(addr, flags));
        } catch (UnsupportedPosixFeatureException e) {
            throw logException("getnameinfo", e);
        } catch (GetAddrInfoException e) {
            throw logException("getnameinfo", e);
        }
    }

    @Override
    @TruffleBoundary
    public final AddrInfoCursor getaddrinfo(Object node, Object service, int family, int sockType, int protocol, int flags) throws UnsupportedPosixFeatureException, GetAddrInfoException {
        logEnter("getaddrinfo", "%s, %s, %d, %d, %d, %d", node, service, family, sockType, protocol, flags);
        try {
            return logExit("getaddrinfo", "%s", delegate.getaddrinfo(node, service, family, sockType, protocol, flags));
        } catch (UnsupportedPosixFeatureException e) {
            throw logException("getaddrinfo", e);
        } catch (GetAddrInfoException e) {
            throw logException("getaddrinfo", e);
        }
    }

    @Override
    @TruffleBoundary
    public final long semOpen(Object name, int openFlags, int mode, int value) throws PosixException {
        logEnter("semOpen", "%s %d %d %d", name, openFlags, mode, value);
        try {
            return logExit("semOpen", "0x%x", delegate.semOpen(name, openFlags, mode, value));
        } catch (PosixException e) {
            throw logException("semOpen", e);
        }
    }

    @Override
    @TruffleBoundary
    public final void semClose(long handle) throws PosixException {
        logEnter("semClose", "0x%x", handle);
        try {
            delegate.semClose(handle);
        } catch (PosixException e) {
            throw logException("semClose", e);
        }
    }

    @Override
    @TruffleBoundary
    public final void semUnlink(Object name) throws PosixException {
        logEnter("semUnlink", "%s", name);
        try {
            delegate.semUnlink(name);
        } catch (PosixException e) {
            throw logException("semUnlink", e);
        }
    }

    @Override
    @TruffleBoundary
    public final int shmOpen(Object name, int openFlags, int mode) throws PosixException {
        logEnter("shmOpen", "%s %d %d", name, openFlags, mode);
        try {
            return logExit("shmOpen", "%d", delegate.shmOpen(name, openFlags, mode));
        } catch (PosixException e) {
            throw logException("shmOpen", e);
        }
    }

    @Override
    @TruffleBoundary
    public final void shmUnlink(Object name) throws PosixException {
        logEnter("shmUnlink", "%s", name);
        try {
            delegate.shmUnlink(name);
        } catch (PosixException e) {
            throw logException("shmUnlink", e);
        }
    }

    @Override
    @TruffleBoundary
    public final int semGetValue(long handle) throws PosixException {
        logEnter("semGetValue", "0x%x", handle);
        try {
            return logExit("semGetValue", "%d", delegate.semGetValue(handle));
        } catch (PosixException e) {
            throw logException("semGetValue", e);
        }
    }

    @Override
    @TruffleBoundary
    public final void semPost(long handle) throws PosixException {
        logEnter("semPost", "0x%x", handle);
        try {
            delegate.semPost(handle);
        } catch (PosixException e) {
            throw logException("semPost", e);
        }
    }

    @Override
    @TruffleBoundary
    public final void semWait(long handle) throws PosixException {
        logEnter("semWait", "0x%x", handle);
        try {
            delegate.semWait(handle);
        } catch (PosixException e) {
            throw logException("semWait", e);
        }
    }

    @Override
    @TruffleBoundary
    public final boolean semTryWait(long handle) throws PosixException {
        logEnter("semTryWait", "0x%x", handle);
        try {
            return logExit("semTryWait", "%b", delegate.semTryWait(handle));
        } catch (PosixException e) {
            throw logException("semTryWait", e);
        }
    }

    @Override
    @TruffleBoundary
    public final boolean semTimedWait(Node location, long handle, long deadlineNs) throws PosixException {
        logEnter("semTimedWait", "0x%x %d", handle, deadlineNs);
        try {
            return logExit("semTimedWait", "%b", delegate.semTimedWait(location, handle, deadlineNs));
        } catch (PosixException e) {
            throw logException("semTimedWait", e);
        }
    }

    @ExportMessage
    final UniversalSockAddr createUniversalSockAddrInet4(Inet4SockAddr src,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) {
        logEnter("createUniversalSockAddrInet4", "%s", src);
        return logExit("createUniversalSockAddrInet4", "%s", lib.createUniversalSockAddrInet4(delegate, src));
    }

    @ExportMessage
    final UniversalSockAddr createUniversalSockAddrInet6(Inet6SockAddr src,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) {
        logEnter("createUniversalSockAddrInet6", "%s", src);
        return logExit("createUniversalSockAddrInet6", "%s", lib.createUniversalSockAddrInet6(delegate, src));
    }

    @ExportMessage
    final UniversalSockAddr createUniversalSockAddrUnix(UnixSockAddr src,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) throws UnsupportedPosixFeatureException, InvalidUnixSocketPathException {
        logEnter("createUniversalSockAddrUnix", "%s", src);
        try {
            return logExit("createUniversalSockAddrUnix", "%s", lib.createUniversalSockAddrUnix(delegate, src));
        } catch (UnsupportedPosixFeatureException e) {
            throw logException("createUniversalSockAddrUnix", e);
        } catch (InvalidUnixSocketPathException e) {
            throw logException("createUniversalSockAddrUnix", e);
        }
    }

    @Override
    @TruffleBoundary
    public final Object createPathFromString(TruffleString path) {
        logEnter(Level.FINEST, "createPathFromString", "%s", path);
        return logExit(Level.FINEST, "createPathFromString", "%s", delegate.createPathFromString(path));
    }

    @Override
    @TruffleBoundary
    public final Object createPathFromBytes(byte[] path) {
        logEnter(Level.FINEST, "createPathFromBytes", "%s", path);
        return logExit(Level.FINEST, "createPathFromBytes", "%s", delegate.createPathFromBytes(path));
    }

    @Override
    @TruffleBoundary
    public final TruffleString getPathAsString(Object path) {
        logEnter(Level.FINEST, "getPathAsString", "%s", path);
        return logExit(Level.FINEST, "getPathAsString", "%s", delegate.getPathAsString(path));
    }

    @Override
    @TruffleBoundary
    public final Buffer getPathAsBytes(Object path) {
        logEnter(Level.FINEST, "getPathAsBytes", "%s", path);
        return logExit(Level.FINEST, "getPathAsBytes", "%s", delegate.getPathAsBytes(path));
    }

    @Override
    @TruffleBoundary
    public final Object createCStringFromString(TruffleString string) {
        return delegate.createCStringFromString(string);
    }

    @Override
    @TruffleBoundary
    public final Object createCStringFromBytes(byte[] bytes) {
        return delegate.createCStringFromBytes(bytes);
    }

    @Override
    @TruffleBoundary
    public final Object createWideStringFromString(TruffleString string) {
        return delegate.createWideStringFromString(string);
    }

    @Override
    @TruffleBoundary
    public final TruffleString getCStringAsString(Object string) {
        return delegate.getCStringAsString(string);
    }

    @Override
    @TruffleBoundary
    public final Buffer getCStringAsBytes(Object string) {
        return delegate.getCStringAsBytes(string);
    }

    @TruffleBoundary
    private static void logEnter(Level level, String msg, String argFmt, Object... args) {
        if (LOGGER.isLoggable(level)) {
            LOGGER.log(level, msg + '(' + String.format(argFmt, fixLogArgs(args)) + ')');
            if (LOGGER.isLoggable(Level.FINEST)) {
                logStackTrace(Level.FINEST, 0, 5);
            }
        }
    }

    private static void logEnter(String msg, String argFmt, Object... args) {
        logEnter(DEFAULT_LEVEL, msg, argFmt, args);
    }

    @TruffleBoundary
    private static <T> T logExit(Level level, String msg, String argFmt, T retVal) {
        if (LOGGER.isLoggable(level)) {
            LOGGER.log(level, msg + " -> return " + String.format(argFmt, fixLogArg(retVal)));
        }
        return retVal;
    }

    private static <T> T logExit(String msg, String argFmt, T retVal) {
        return logExit(DEFAULT_LEVEL, msg, argFmt, retVal);
    }

    @TruffleBoundary
    private static UnsupportedPosixFeatureException logException(Level level, String msg, UnsupportedPosixFeatureException e) throws UnsupportedPosixFeatureException {
        if (LOGGER.isLoggable(level)) {
            LOGGER.log(level, msg + String.format(" -> throw unsupported, msg=%s", fixLogArg(e.getMessage())));
        }
        throw e;
    }

    @TruffleBoundary
    private static PosixException logException(Level level, String msg, PosixException e) throws PosixException {
        if (LOGGER.isLoggable(level)) {
            if (e instanceof PosixErrnoException errnoException) {
                LOGGER.log(level, msg + String.format(" -> throw errno=%d, msg=%s", fixLogArgs(errnoException.getErrorCode(), errnoException.getMessage())));
            } else {
                LOGGER.log(level, msg + String.format(" -> throw unsupported, msg=%s", fixLogArg(e.getMessage())));
            }
        }
        throw e;
    }

    @TruffleBoundary
    private static GetAddrInfoException logException(Level level, String msg, GetAddrInfoException e) throws GetAddrInfoException {
        if (LOGGER.isLoggable(level)) {
            LOGGER.log(level, msg + String.format(" -> throw error code=%d, msg=%s", fixLogArgs(e.getErrorCode(), e.getMessage())));
        }
        throw e;
    }

    @TruffleBoundary
    private static InvalidAddressException logException(Level level, String msg, InvalidAddressException e) throws InvalidAddressException {
        if (LOGGER.isLoggable(level)) {
            LOGGER.log(level, msg + " -> throw InvalidAddressException");
        }
        throw e;
    }

    @TruffleBoundary
    private static InvalidUnixSocketPathException logException(Level level, String msg, InvalidUnixSocketPathException e) throws InvalidUnixSocketPathException {
        if (LOGGER.isLoggable(level)) {
            LOGGER.log(level, msg + " -> throw InvalidUnixSocketPathException");
        }
        throw e;
    }

    private static PosixException logException(String msg, PosixException e) throws PosixException {
        throw logException(DEFAULT_LEVEL, msg, e);
    }

    private static UnsupportedPosixFeatureException logException(String msg, UnsupportedPosixFeatureException e) throws UnsupportedPosixFeatureException {
        throw logException(DEFAULT_LEVEL, msg, e);
    }

    private static GetAddrInfoException logException(String msg, GetAddrInfoException e) throws GetAddrInfoException {
        throw logException(DEFAULT_LEVEL, msg, e);
    }

    private static InvalidAddressException logException(String msg, InvalidAddressException e) throws InvalidAddressException {
        throw logException(DEFAULT_LEVEL, msg, e);
    }

    private static InvalidUnixSocketPathException logException(String msg, InvalidUnixSocketPathException e) throws InvalidUnixSocketPathException {
        throw logException(DEFAULT_LEVEL, msg, e);
    }

    private static Object fixLogArg(Object arg) {
        if (arg instanceof String || arg instanceof TruffleString) {
            return "'" + arg + "'";
        }
        if (arg instanceof Buffer) {
            Buffer b = (Buffer) arg;
            return "Buffer{" + asString(b.data, 0, (int) b.length) + "}";
        }
        if (arg instanceof Timeval) {
            Timeval t = (Timeval) arg;
            return "Timeval{" + t.getSeconds() + ", " + t.getMicroseconds() + "}";
        }
        if (arg instanceof byte[]) {
            byte[] bytes = (byte[]) arg;
            return asString(bytes, 0, bytes.length);
        }
        if (arg instanceof int[]) {
            return Arrays.toString((int[]) arg);
        }
        if (arg instanceof long[]) {
            return Arrays.toString((long[]) arg);
        }
        if (arg instanceof Object[]) {
            Object[] src = (Object[]) arg;
            Object[] res = new Object[src.length];
            for (int i = 0; i < src.length; ++i) {
                res[i] = fixLogArg(src[i]);
            }
            return Arrays.toString(res);
        }
        return arg;
    }

    private static Object[] fixLogArgs(Object... args) {
        Object[] fixed = new Object[args.length];
        for (int i = 0; i < args.length; ++i) {
            fixed[i] = fixLogArg(args[i]);
        }
        return fixed;
    }

    @TruffleBoundary
    private static String asString(byte[] bytes, int offset, int length) {
        return "b'" + new String(bytes, offset, length) + "'";
    }

    @TruffleBoundary
    private static void logStackTrace(Level level, int first, int depth) {
        ArrayList<String> stack = new ArrayList<>();
        Truffle.getRuntime().iterateFrames(frameInstance -> {
            String str = formatFrame(frameInstance);
            if (str != null) {
                stack.add(str);
            }
            return null;
        });
        int cnt = Math.min(stack.size(), depth);
        for (int i = first; i < cnt; ++i) {
            LOGGER.log(level, stack.get(i));
        }
    }

    private static String formatFrame(FrameInstance frameInstance) {
        RootNode rootNode = ((RootCallTarget) frameInstance.getCallTarget()).getRootNode();
        String rootName = rootNode.getQualifiedName();
        Node location = frameInstance.getCallNode();
        if (location == null) {
            location = rootNode;
        }
        SourceSection sourceSection = null;
        while (location != null && sourceSection == null) {
            sourceSection = location.getSourceSection();
            location = location.getParent();
        }
        if (rootName == null && sourceSection == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder("    .");    // the dot tricks IDEA into hyperlinking
        // the file & line
        sb.append(rootName == null ? "???" : rootName);
        if (sourceSection != null) {
            sb.append(" (");
            sb.append(sourceSection.getSource().getName());
            sb.append(':');
            sb.append(sourceSection.getStartLine());
            sb.append(')');
        }
        return sb.toString();
    }
}
