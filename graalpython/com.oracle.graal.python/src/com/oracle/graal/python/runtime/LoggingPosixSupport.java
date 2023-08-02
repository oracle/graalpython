/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.graal.python.runtime.PosixSupportLibrary.FamilySpecificSockAddr;
import com.oracle.graal.python.runtime.PosixSupportLibrary.GetAddrInfoException;
import com.oracle.graal.python.runtime.PosixSupportLibrary.InvalidAddressException;
import com.oracle.graal.python.runtime.PosixSupportLibrary.OpenPtyResult;
import com.oracle.graal.python.runtime.PosixSupportLibrary.PosixException;
import com.oracle.graal.python.runtime.PosixSupportLibrary.PwdResult;
import com.oracle.graal.python.runtime.PosixSupportLibrary.RecvfromResult;
import com.oracle.graal.python.runtime.PosixSupportLibrary.SelectResult;
import com.oracle.graal.python.runtime.PosixSupportLibrary.Timeval;
import com.oracle.graal.python.runtime.PosixSupportLibrary.UniversalSockAddr;
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

    @ExportMessage
    final TruffleString getBackend(
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) {
        logEnter(Level.FINEST, "getBackend", "");
        return logExit(Level.FINEST, "getBackend", "%s", lib.getBackend(delegate));
    }

    @ExportMessage
    final TruffleString strerror(int errorCode,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) {
        logEnter(Level.FINEST, "strerror", "%d", errorCode);
        return logExit(Level.FINEST, "strerror", "%s", lib.strerror(delegate, errorCode));
    }

    @ExportMessage
    final long getpid(
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) {
        logEnter("getpid", "");
        return logExit("getpid", "%d", lib.getpid(delegate));
    }

    @ExportMessage
    final int umask(int mask,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) throws PosixException {
        logEnter("umask", "0%o", mask);
        try {
            return logExit("umask", "0%o", lib.umask(delegate, mask));
        } catch (PosixException e) {
            throw logException("umask", e);
        }
    }

    @ExportMessage
    final int openat(int dirFd, Object pathname, int flags, int mode,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) throws PosixException {
        logEnter("openAt", "%d, %s, 0x%x, 0%o", dirFd, pathname, flags, mode);
        try {
            return logExit("openAt", "%d", lib.openat(delegate, dirFd, pathname, flags, mode));
        } catch (PosixException e) {
            throw logException("openAt", e);
        }
    }

    @ExportMessage
    final int close(int fd,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) throws PosixException {
        logEnter("close", "%d", fd);
        try {
            return lib.close(delegate, fd);
        } catch (PosixException e) {
            throw logException("close", e);
        }
    }

    @ExportMessage
    final Buffer read(int fd, long length,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) throws PosixException {
        logEnter("read", "%d, %d", fd, length);
        try {
            Buffer retVal = lib.read(delegate, fd, length);
            logExit("read", "%d", retVal.length);
            return retVal;
        } catch (PosixException e) {
            throw logException("read", e);
        }
    }

    @ExportMessage
    final long write(int fd, Buffer data,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) throws PosixException {
        logEnter("write", "%d, %d", fd, data.length);
        try {
            return logExit("write", "%d", lib.write(delegate, fd, data));
        } catch (PosixException e) {
            throw logException("write", e);
        }
    }

    @ExportMessage
    final int dup(int fd,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) throws PosixException {
        logEnter("dup", "%d", fd);
        try {
            return logExit("dup", "%d", lib.dup(delegate, fd));
        } catch (PosixException e) {
            throw logException("dup", e);
        }
    }

    @ExportMessage
    final int dup2(int fd, int fd2, boolean inheritable,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) throws PosixException {
        logEnter("dup2", "%d, %d, %b", fd, fd2, inheritable);
        try {
            return logExit("dup2", "%d", lib.dup2(delegate, fd, fd2, inheritable));
        } catch (PosixException e) {
            throw logException("dup2", e);
        }
    }

    @ExportMessage
    final boolean getInheritable(int fd,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) throws PosixException {
        logEnter("getInheritable", "%d", fd);
        try {
            return logExit("getInheritable", "%b", lib.getInheritable(delegate, fd));
        } catch (PosixException e) {
            throw logException("getInheritable", e);
        }
    }

    @ExportMessage
    final void setInheritable(int fd, boolean inheritable,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) throws PosixException {
        logEnter("setInheritable", "%d, %b", fd, inheritable);
        try {
            lib.setInheritable(delegate, fd, inheritable);
        } catch (PosixException e) {
            throw logException("setInheritable", e);
        }
    }

    @ExportMessage
    final int[] pipe(
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) throws PosixException {
        logEnter("pipe", "");
        try {
            return logExit("pipe", "%s", lib.pipe(delegate));
        } catch (PosixException e) {
            throw logException("pipe", e);
        }
    }

    @SuppressWarnings("all")
    @ExportMessage
    public SelectResult select(int[] readfds, int[] writefds, int[] errorfds, Timeval timeout,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) throws PosixException {
        logEnter("select", "%s %s %s %s", readfds, writefds, errorfds, timeout);
        try {
            return logExit("select", "%s", lib.select(delegate, readfds, writefds, errorfds, timeout));
        } catch (PosixException e) {
            throw logException("select", e);
        }
    }

    @ExportMessage
    final long lseek(int fd, long offset, int how,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) throws PosixException {
        logEnter("lseek", "%d, %d, %d", fd, offset, how);
        try {
            return logExit("lseek", "%d", lib.lseek(delegate, fd, offset, how));
        } catch (PosixException e) {
            throw logException("lseek", e);
        }
    }

    @ExportMessage
    final void ftruncate(int fd, long length,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) throws PosixException {
        logEnter("ftruncate", "%d, %d", fd, length);
        try {
            lib.ftruncate(delegate, fd, length);
        } catch (PosixException e) {
            throw logException("ftruncate", e);
        }
    }

    @ExportMessage
    final void fsync(int fd,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) throws PosixException {
        logEnter("fsync", "%d", fd);
        try {
            lib.fsync(delegate, fd);
        } catch (PosixException e) {
            throw logException("fsync", e);
        }
    }

    @ExportMessage
    final void flock(int fd, int operation,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) throws PosixException {
        logEnter("flock", "%d %d", fd, operation);
        try {
            lib.flock(delegate, fd, operation);
        } catch (PosixException e) {
            throw logException("flock", e);
        }
    }

    @ExportMessage
    final boolean getBlocking(int fd,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) throws PosixException {
        logEnter("getBlocking", "%d", fd);
        try {
            return logExit("getBlocking", "%b", lib.getBlocking(delegate, fd));
        } catch (PosixException e) {
            throw logException("getBlocking", e);
        }
    }

    @ExportMessage
    final void setBlocking(int fd, boolean blocking,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) throws PosixException {
        logEnter("setBlocking", "%d, %b", fd, blocking);
        try {
            lib.setBlocking(delegate, fd, blocking);
        } catch (PosixException e) {
            throw logException("setBlocking", e);
        }
    }

    @ExportMessage
    final int[] getTerminalSize(int fd,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) throws PosixException {
        logEnter("getTerminalSize", "%d", fd);
        try {
            return logExit("getTerminalSize", "%s", lib.getTerminalSize(delegate, fd));
        } catch (PosixException e) {
            throw logException("getTerminalSize", e);
        }
    }

    @ExportMessage
    final long[] fstatat(int dirFd, Object pathname, boolean followSymlinks,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) throws PosixException {
        logEnter("fstatAt", "%d, %s, %b", dirFd, pathname, followSymlinks);
        try {
            return logExit("fstatAt", "%s", lib.fstatat(delegate, dirFd, pathname, followSymlinks));
        } catch (PosixException e) {
            throw logException("fstatAt", e);
        }
    }

    @ExportMessage
    final long[] fstat(int fd,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) throws PosixException {
        logEnter("fstat", "%d", fd);
        try {
            return logExit("fstat", "%s", lib.fstat(delegate, fd));
        } catch (PosixException e) {
            throw logException("fstat", e);
        }
    }

    @ExportMessage
    final long[] statvfs(Object path,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) throws PosixException {
        logEnter("statvfs", "%s", path);
        try {
            return logExit("statvfs", "%s", lib.statvfs(delegate, path));
        } catch (PosixException e) {
            throw logException("statvfs", e);
        }
    }

    @ExportMessage
    final long[] fstatvfs(int fd,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) throws PosixException {
        logEnter("fstatvfs", "%d", fd);
        try {
            return logExit("fstatvfs", "%s", lib.fstatvfs(delegate, fd));
        } catch (PosixException e) {
            throw logException("fstatvfs", e);
        }
    }

    @ExportMessage
    final Object[] uname(
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) throws PosixException {
        logEnter("uname", "");
        try {
            return logExit("uname", "%s", lib.uname(delegate));
        } catch (PosixException e) {
            throw logException("uname", e);
        }
    }

    @ExportMessage
    final void unlinkat(int dirFd, Object pathname, boolean rmdir,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) throws PosixException {
        logEnter("unlinkAt", "%d, %s, %b", dirFd, pathname, rmdir);
        try {
            lib.unlinkat(delegate, dirFd, pathname, rmdir);
        } catch (PosixException e) {
            throw logException("unlinkAt", e);
        }
    }

    @ExportMessage
    final void linkat(int oldFdDir, Object oldPath, int newFdDir, Object newPath, int flags,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) throws PosixException {
        logEnter("linkAt", "%d, %s, %d, %s, %d", oldFdDir, oldPath, newFdDir, newPath, flags);
        try {
            lib.linkat(delegate, oldFdDir, oldPath, newFdDir, newPath, flags);
        } catch (PosixException e) {
            throw logException("symlinkAt", e);
        }
    }

    @ExportMessage
    final void symlinkat(Object target, int linkpathDirFd, Object linkpath,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) throws PosixException {
        logEnter("symlinkAt", "%s, %d, %s", target, linkpathDirFd, linkpath);
        try {
            lib.symlinkat(delegate, target, linkpathDirFd, linkpath);
        } catch (PosixException e) {
            throw logException("symlinkAt", e);
        }
    }

    @ExportMessage
    final void mkdirat(int dirFd, Object pathname, int mode,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) throws PosixException {
        logEnter("mkdirAt", "%d, %s, 0%o", dirFd, pathname, mode);
        try {
            lib.mkdirat(delegate, dirFd, pathname, mode);
        } catch (PosixException e) {
            throw logException("mkdirAt", e);
        }
    }

    @ExportMessage
    final Object getcwd(
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) throws PosixException {
        logEnter("getcwd", "");
        try {
            return logExit("getcwd", "%s", lib.getcwd(delegate));
        } catch (PosixException e) {
            throw logException("getcwd", e);
        }
    }

    @ExportMessage
    final void chdir(Object path,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) throws PosixException {
        logEnter("chdir", "%s", path);
        try {
            lib.chdir(delegate, path);
        } catch (PosixException e) {
            throw logException("chdir", e);
        }
    }

    @ExportMessage
    final void fchdir(int fd,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) throws PosixException {
        logEnter("fchdir", "%d", fd);
        try {
            lib.fchdir(delegate, fd);
        } catch (PosixException e) {
            throw logException("fchdir", e);
        }
    }

    @ExportMessage
    final boolean isatty(int fd,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) {
        logEnter("isatty", "%d", fd);
        return logExit("isatty", "%b", lib.isatty(delegate, fd));
    }

    @ExportMessage
    final Object opendir(Object path,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) throws PosixException {
        logEnter("opendir", "%s", path);
        try {
            return logExit("opendir", "%s", lib.opendir(delegate, path));
        } catch (PosixException e) {
            throw logException("opendir", e);
        }
    }

    @ExportMessage
    final Object fdopendir(int fd,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) throws PosixException {
        logEnter("fdopendir", "%d", fd);
        try {
            return logExit("fdopendir", "%s", lib.fdopendir(delegate, fd));
        } catch (PosixException e) {
            throw logException("fdopendir", e);
        }
    }

    @ExportMessage
    final void closedir(Object dirStream,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) throws PosixException {
        logEnter("closedir", "%s", dirStream);
        try {
            lib.closedir(delegate, dirStream);
        } catch (PosixException e) {
            throw logException("closedir", e);
        }
    }

    @ExportMessage
    final Object readdir(Object dirStream,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) throws PosixException {
        logEnter("readdir", "%s", dirStream);
        try {
            return logExit("readdir", "%s", lib.readdir(delegate, dirStream));
        } catch (PosixException e) {
            throw logException("readdir", e);
        }
    }

    @ExportMessage
    final void rewinddir(Object dirStream,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) {
        logEnter("rewinddir", "%s", dirStream);
        lib.rewinddir(delegate, dirStream);
    }

    @ExportMessage
    final Object dirEntryGetName(Object dirEntry,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) throws PosixException {
        logEnter("dirEntryGetName", "%s", dirEntry);
        try {
            return logExit("dirEntryGetName", "%s", lib.dirEntryGetName(delegate, dirEntry));
        } catch (PosixException e) {
            throw logException("dirEntryGetName", e);
        }
    }

    @ExportMessage
    final Object dirEntryGetPath(Object dirEntry, Object scandirPath,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) throws PosixException {
        logEnter("dirEntryGetPath", "%s, %s", dirEntry, scandirPath);
        try {
            return logExit("dirEntryGetPath", "%s", lib.dirEntryGetPath(delegate, dirEntry, scandirPath));
        } catch (PosixException e) {
            throw logException("dirEntryGetPath", e);
        }
    }

    @ExportMessage
    final long dirEntryGetInode(Object dirEntry,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) throws PosixException {
        logEnter("dirEntryGetInode", "%s", dirEntry);
        try {
            return logExit("dirEntryGetInode", "%d", lib.dirEntryGetInode(delegate, dirEntry));
        } catch (PosixException e) {
            throw logException("dirEntryGetInode", e);
        }
    }

    @ExportMessage
    final int dirEntryGetType(Object dirEntry,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) {
        logEnter("dirEntryGetType", "%s", dirEntry);
        return logExit("dirEntryGetType", "%d", lib.dirEntryGetType(delegate, dirEntry));
    }

    @ExportMessage
    final void utimensat(int dirFd, Object pathname, long[] timespec, boolean followSymlinks,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) throws PosixException {
        logEnter("utimeNsAt", "%d, %s, %s, %b", dirFd, pathname, timespec, followSymlinks);
        try {
            lib.utimensat(delegate, dirFd, pathname, timespec, followSymlinks);
        } catch (PosixException e) {
            throw logException("utimeNsAt", e);
        }
    }

    @ExportMessage
    final void futimens(int fd, long[] timespec,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) throws PosixException {
        logEnter("futimeNs", "%d, %s", fd, timespec);
        try {
            lib.futimens(delegate, fd, timespec);
        } catch (PosixException e) {
            throw logException("futimeNs", e);
        }
    }

    @ExportMessage
    final void futimes(int fd, Timeval[] timeval,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) throws PosixException {
        logEnter("futimes", "%d, %s", fd, timeval);
        try {
            lib.futimes(delegate, fd, timeval);
        } catch (PosixException e) {
            throw logException("futimes", e);
        }
    }

    @ExportMessage
    final void lutimes(Object filename, Timeval[] timeval,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) throws PosixException {
        logEnter("lutimes", "%s, %s", filename, timeval);
        try {
            lib.lutimes(delegate, filename, timeval);
        } catch (PosixException e) {
            throw logException("lutimes", e);
        }
    }

    @ExportMessage
    final void utimes(Object filename, Timeval[] timeval,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) throws PosixException {
        logEnter("utimes", "%s, %s", filename, timeval);
        try {
            lib.utimes(delegate, filename, timeval);
        } catch (PosixException e) {
            throw logException("utimes", e);
        }
    }

    @ExportMessage
    final void renameat(int oldDirFd, Object oldPath, int newDirFd, Object newPath,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) throws PosixException {
        logEnter("renameAt", "%d, %s, %d, %s", oldDirFd, oldPath, newDirFd, newPath);
        try {
            lib.renameat(delegate, oldDirFd, oldPath, newDirFd, newPath);
        } catch (PosixException e) {
            throw logException("renameAt", e);
        }
    }

    @ExportMessage
    final boolean faccessat(int dirFd, Object path, int mode, boolean effectiveIds, boolean followSymlinks,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) {
        logEnter("faccessAt", "%d, %s, 0%o, %b, %b", dirFd, path, mode, effectiveIds, followSymlinks);
        return logExit("faccessAt", "%b", lib.faccessat(delegate, dirFd, path, mode, effectiveIds, followSymlinks));
    }

    @ExportMessage
    final void fchmodat(int dirFd, Object path, int mode, boolean followSymlinks,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) throws PosixException {
        logEnter("fchmodat", "%d, %s, 0%o, %b", dirFd, path, mode, followSymlinks);
        try {
            lib.fchmodat(delegate, dirFd, path, mode, followSymlinks);
        } catch (PosixException e) {
            throw logException("fchmodat", e);
        }
    }

    @ExportMessage
    final void fchmod(int fd, int mode,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) throws PosixException {
        logEnter("fchmod", "%d, 0%o", fd, mode);
        try {
            lib.fchmod(delegate, fd, mode);
        } catch (PosixException e) {
            throw logException("fchmod", e);
        }
    }

    @ExportMessage
    final Object readlinkat(int dirFd, Object path,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) throws PosixException {
        logEnter("readlinkat", "%d, %s", dirFd, path);
        try {
            return logExit("readlinkat", "%s", lib.readlinkat(delegate, dirFd, path));
        } catch (PosixException e) {
            throw logException("readlinkat", e);
        }
    }

    @ExportMessage
    final void kill(long pid, int signal,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) throws PosixException {
        logEnter("kill", "%d, %d", pid, signal);
        try {
            lib.kill(delegate, pid, signal);
        } catch (PosixException e) {
            throw logException("kill", e);
        }
    }

    @ExportMessage
    final void killpg(long pgid, int signal,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) throws PosixException {
        logEnter("killpg", "%d, %d", pgid, signal);
        try {
            lib.killpg(delegate, pgid, signal);
        } catch (PosixException e) {
            throw logException("killpg", e);
        }
    }

    @ExportMessage
    public Object mmap(long length, int prot, int flags, int fd, long offset,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) throws PosixException {
        logEnter("mmap", "%d, %d, %d, %d, %d", length, prot, flags, fd, offset);
        try {
            return logExit("mmap", "%s", lib.mmap(delegate, length, prot, flags, fd, offset));
        } catch (PosixException e) {
            throw logException("mmap", e);
        }
    }

    @ExportMessage
    final long[] waitpid(long pid, int options,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) throws PosixException {
        logEnter("waitpid", "%d, %d", pid, options);
        try {
            return logExit("waitpid", "%s", lib.waitpid(delegate, pid, options));
        } catch (PosixException e) {
            throw logException("waitpid", e);
        }
    }

    @ExportMessage
    public byte mmapReadByte(Object mmap, long index,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) throws PosixException {
        logEnter("mmapReadByte", "%s, %d", mmap, index);
        try {
            return logExit("mmapReadByte", "%s", lib.mmapReadByte(delegate, mmap, index));
        } catch (PosixException e) {
            throw logException("mmapReadByte", e);
        }
    }

    @ExportMessage
    public void mmapWriteByte(Object mmap, long index, byte value,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) throws PosixException {
        logEnter("mmapWriteByte", "%s, %d, %d", mmap, index, value);
        try {
            lib.mmapWriteByte(delegate, mmap, index, value);
        } catch (PosixException e) {
            throw logException("mmapWriteByte", e);
        }
    }

    @ExportMessage
    final void abort(@CachedLibrary("this.delegate") PosixSupportLibrary lib) {
        logEnter("abort", "");
        lib.abort(this.delegate);
    }

    @ExportMessage
    final boolean wcoredump(int status,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) {
        logEnter("wcoredump", "%d", status);
        return logExit("wcoredump", "%b", lib.wcoredump(delegate, status));
    }

    @ExportMessage
    final boolean wifcontinued(int status,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) {
        logEnter("wifcontinued", "%d", status);
        return logExit("wifcontinued", "%b", lib.wifcontinued(delegate, status));
    }

    @ExportMessage
    final boolean wifstopped(int status,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) {
        logEnter("wifstopped", "%d", status);
        return logExit("wifstopped", "%b", lib.wifstopped(delegate, status));
    }

    @ExportMessage
    final boolean wifsignaled(int status,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) {
        logEnter("wifsignaled", "%d", status);
        return logExit("wifsignaled", "%b", lib.wifsignaled(delegate, status));
    }

    @ExportMessage
    final boolean wifexited(int status,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) {
        logEnter("wifexited", "%d", status);
        return logExit("wifexited", "%b", lib.wifexited(delegate, status));
    }

    @ExportMessage
    final int wexitstatus(int status,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) {
        logEnter("wexitstatus", "%d", status);
        return logExit("wexitstatus", "%d", lib.wexitstatus(delegate, status));
    }

    @ExportMessage
    final int wtermsig(int status,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) {
        logEnter("wtermsig", "%d", status);
        return logExit("wtermsig", "%d", lib.wtermsig(delegate, status));
    }

    @ExportMessage
    final int wstopsig(int status,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) {
        logEnter("wstopsig", "%d", status);
        return logExit("wstopsig", "%d", lib.wstopsig(delegate, status));
    }

    @ExportMessage
    final long getuid(
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) {
        logEnter("getuid", "");
        return logExit("getuid", "%d", lib.getuid(delegate));
    }

    @ExportMessage
    final long geteuid(
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) {
        logEnter("geteuid", "");
        return logExit("geteuid", "%d", lib.geteuid(delegate));
    }

    @ExportMessage
    final long getgid(
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) {
        logEnter("getgid", "");
        return logExit("getgid", "%d", lib.getgid(delegate));
    }

    @ExportMessage
    final long getppid(
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) {
        logEnter("getppid", "");
        return logExit("getppid", "%d", lib.getppid(delegate));
    }

    @ExportMessage
    final long getpgid(long pid,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) throws PosixException {
        logEnter("getpgid", "%d", pid);
        try {
            return logExit("getpgid", "%d", lib.getpgid(delegate, pid));
        } catch (PosixException e) {
            throw logException("getpgid", e);
        }
    }

    @ExportMessage
    final void setpgid(long pid, long pgid,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) throws PosixException {
        logEnter("setpgid", "%d, %d", pid, pgid);
        try {
            lib.setpgid(delegate, pid, pgid);
        } catch (PosixException e) {
            throw logException("setpgid", e);
        }
    }

    @ExportMessage
    final long getpgrp(
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) {
        logEnter("getpgrp", "");
        return logExit("getpgrp", "%d", lib.getpgrp(delegate));
    }

    @ExportMessage
    final long getsid(long pid,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) throws PosixException {
        logEnter("getsid", "%d", pid);
        try {
            return logExit("getsid", "%d", lib.getsid(delegate, pid));
        } catch (PosixException e) {
            throw logException("getsid", e);
        }
    }

    @ExportMessage
    final long setsid(
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) throws PosixException {
        logEnter("setsid", "");
        try {
            return logExit("getsid", "%d", lib.setsid(delegate));
        } catch (PosixException e) {
            throw logException("setsid", e);
        }
    }

    @ExportMessage
    public int mmapReadBytes(Object mmap, long index, byte[] bytes, int length,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) throws PosixException {
        logEnter("mmapReadBytes", "%s, %d, %d", mmap, index, length);
        try {
            return logExit("mmapReadBytes", "%s", lib.mmapReadBytes(delegate, mmap, index, bytes, length));
        } catch (PosixException e) {
            throw logException("mmapReadBytes", e);
        }
    }

    @ExportMessage
    final OpenPtyResult openpty(@CachedLibrary("this.delegate") PosixSupportLibrary lib) throws PosixException {
        logEnter("openpty", "");
        try {
            return logExit("openpty", "%s", lib.openpty(delegate));
        } catch (PosixException e) {
            throw logException("openpty", e);
        }
    }

    @ExportMessage
    final TruffleString ctermid(@CachedLibrary("this.delegate") PosixSupportLibrary lib) throws PosixException {
        logEnter("ctermid", "");
        try {
            return logExit("ctermid", "%s", lib.ctermid(delegate));
        } catch (PosixException e) {
            throw logException("ctermid", e);
        }
    }

    @ExportMessage
    final void setenv(Object name, Object value, boolean overwrite,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) throws PosixException {
        logEnter("setenv", "%s, %s, %b", name, value, overwrite);
        try {
            lib.setenv(delegate, name, value, overwrite);
        } catch (PosixException e) {
            throw logException("setenv", e);
        }
    }

    @ExportMessage
    final void unsetenv(Object name,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) throws PosixException {
        logEnter("unsetenv", "%s", name);
        try {
            lib.unsetenv(delegate, name);
        } catch (PosixException e) {
            throw logException("unsetenv", e);
        }
    }

    @ExportMessage
    public void mmapWriteBytes(Object mmap, long index, byte[] bytes, int length,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) throws PosixException {
        logEnter("mmapWriteBytes", "%s, %d, %d", mmap, index, length);
        try {
            lib.mmapWriteBytes(delegate, mmap, index, bytes, length);
        } catch (PosixException e) {
            throw logException("mmapWriteBytes", e);
        }
    }

    @ExportMessage
    final int forkExec(Object[] executables, Object[] args, Object cwd, Object[] env, int stdinReadFd, int stdinWriteFd, int stdoutReadFd, int stdoutWriteFd, int stderrReadFd, int stderrWriteFd,
                    int errPipeReadFd, int errPipeWriteFd, boolean closeFds, boolean restoreSignals, boolean callSetsid, int[] fdsToKeep,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) throws PosixException {
        logEnter("forkExec", "%s, %s, %s, %s, %d, %d, %d, %d, %d, %d, %d, %d, %b, %b, %b, %s", executables, args, cwd, env, stdinReadFd, stdinWriteFd, stdoutReadFd, stdoutWriteFd, stderrReadFd,
                        stderrWriteFd, errPipeReadFd, errPipeWriteFd, closeFds, restoreSignals, callSetsid, fdsToKeep);
        try {
            return logExit("forkExec", "%d", lib.forkExec(delegate, executables, args, cwd, env, stdinReadFd, stdinWriteFd, stdoutReadFd, stdoutWriteFd, stderrReadFd, stderrWriteFd, errPipeReadFd,
                            errPipeWriteFd, closeFds, restoreSignals, callSetsid, fdsToKeep));
        } catch (PosixException e) {
            throw logException("forkExec", e);
        }
    }

    @ExportMessage
    public void mmapFlush(Object mmap, long offset, long length,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) throws PosixException {
        logEnter("mmapFlush", "%s, %d, %d", mmap, offset, length);
        try {
            lib.mmapFlush(delegate, mmap, offset, length);
        } catch (PosixException e) {
            throw logException("mmapFlush", e);
        }
    }

    @ExportMessage
    public long mmapGetPointer(Object mmap,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) {
        logEnter("mmapGetPointer", "%s", mmap);
        return lib.mmapGetPointer(delegate, mmap);
    }

    @ExportMessage
    final void execv(Object pathname, Object[] args,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) throws PosixException {
        logEnter("execv", "%s, %s", pathname, args);
        try {
            lib.execv(delegate, pathname, args);
        } catch (PosixException e) {
            throw logException("execv", e);
        }
    }

    @ExportMessage
    public void mmapUnmap(Object mmap, long length,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) throws PosixException {
        logEnter("mmapUnmap", "%s %d", mmap, length);
        try {
            lib.mmapUnmap(delegate, mmap, length);
        } catch (PosixException e) {
            throw logException("mmapUnmap", e);
        }
    }

    @ExportMessage
    public PwdResult getpwuid(long uid,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) throws PosixException {
        logEnter("getpwuid", "%d", uid);
        try {
            return logExit("getpwuid", "%s", lib.getpwuid(delegate, uid));
        } catch (PosixException e) {
            throw logException("getpwuid", e);
        }
    }

    @ExportMessage
    public PwdResult getpwnam(Object name,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) throws PosixException {
        logEnter("getpwnam", "%s", name);
        try {
            return logExit("getpwnam", "%s", lib.getpwnam(delegate, name));
        } catch (PosixException e) {
            throw logException("getpwnam", e);
        }
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    public boolean hasGetpwentries(@CachedLibrary("this.delegate") PosixSupportLibrary lib) {
        return logExit("hasGetpwentries", "%b", lib.hasGetpwentries(delegate));
    }

    @ExportMessage
    public PwdResult[] getpwentries(
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) throws PosixException {
        logEnter("getpwentries", "");
        try {
            return logExit("getpwentries", "%s", lib.getpwentries(delegate));
        } catch (PosixException e) {
            throw logException("getpwentries", e);
        }
    }

    @ExportMessage
    final int system(Object command,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) {
        logEnter("system", "%s", command);
        return logExit("system", "%d", lib.system(delegate, command));
    }

    @ExportMessage
    final int socket(int domain, int type, int protocol,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) throws PosixException {
        logEnter("socket", "%d, %d, %d", domain, type, protocol);
        try {
            return logExit("socket", "%d", lib.socket(delegate, domain, type, protocol));
        } catch (PosixException e) {
            throw logException("socket", e);
        }
    }

    @ExportMessage
    final AcceptResult accept(int sockfd,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) throws PosixException {
        logEnter("accept", "%d", sockfd);
        try {
            return logExit("accept", "%s", lib.accept(delegate, sockfd));
        } catch (PosixException e) {
            throw logException("accept", e);
        }
    }

    @ExportMessage
    final void bind(int sockfd, UniversalSockAddr addr,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) throws PosixException {
        logEnter("bind", "%d, %s", sockfd, addr);
        try {
            lib.bind(delegate, sockfd, addr);
        } catch (PosixException e) {
            throw logException("bind", e);
        }
    }

    @ExportMessage
    final void connect(int sockfd, UniversalSockAddr addr,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) throws PosixException {
        logEnter("connect", "%d, %s", sockfd, addr);
        try {
            lib.connect(delegate, sockfd, addr);
        } catch (PosixException e) {
            throw logException("connect", e);
        }
    }

    @ExportMessage
    final void listen(int sockfd, int backlog,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) throws PosixException {
        logEnter("listen", "%d, %d", sockfd, backlog);
        try {
            lib.listen(delegate, sockfd, backlog);
        } catch (PosixException e) {
            throw logException("listen", e);
        }
    }

    @ExportMessage
    final UniversalSockAddr getpeername(int sockfd,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) throws PosixException {
        logEnter("getpeername", "%d", sockfd);
        try {
            return logExit("getpeername", "%s", lib.getpeername(delegate, sockfd));
        } catch (PosixException e) {
            throw logException("getpeername", e);
        }
    }

    @ExportMessage
    final UniversalSockAddr getsockname(int sockfd,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) throws PosixException {
        logEnter("getsockname", "%d", sockfd);
        try {
            return logExit("getsockname", "%s", lib.getsockname(delegate, sockfd));
        } catch (PosixException e) {
            throw logException("getsockname", e);
        }
    }

    @ExportMessage
    final int send(int sockfd, byte[] buf, int offset, int len, int flags,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) throws PosixException {
        logEnter("send", "%d, %d, %d, %d", sockfd, offset, len, flags);
        try {
            return logExit("send", "%d", lib.send(delegate, sockfd, buf, offset, len, flags));
        } catch (PosixException e) {
            throw logException("send", e);
        }
    }

    @ExportMessage
    final int sendto(int sockfd, byte[] buf, int offset, int len, int flags, UniversalSockAddr destAddr,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) throws PosixException {
        logEnter("sendto", "%d, %d, %d, %d, %s", sockfd, offset, len, flags, destAddr);
        try {
            return logExit("sendto", "%d", lib.sendto(delegate, sockfd, buf, offset, len, flags, destAddr));
        } catch (PosixException e) {
            throw logException("sendto", e);
        }
    }

    @ExportMessage
    final int recv(int sockfd, byte[] buf, int offset, int len, int flags,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) throws PosixException {
        logEnter("recv", "%d, %d, %d, %d", sockfd, offset, len, flags);
        try {
            return logExit("recv", "%d", lib.recv(delegate, sockfd, buf, offset, len, flags));
        } catch (PosixException e) {
            throw logException("recv", e);
        }
    }

    @ExportMessage
    final RecvfromResult recvfrom(int sockfd, byte[] buf, int offset, int len, int flags,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) throws PosixException {
        logEnter("recvfrom", "%d, %d, %d, %d", sockfd, offset, len, flags);
        try {
            return logExit("recvfrom", "%s", lib.recvfrom(delegate, sockfd, buf, offset, len, flags));
        } catch (PosixException e) {
            throw logException("recvfrom", e);
        }
    }

    @ExportMessage
    final void shutdown(int sockfd, int how,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) throws PosixException {
        logEnter("shutdown", "%d, %d", sockfd, how);
        try {
            lib.shutdown(delegate, sockfd, how);
        } catch (PosixException e) {
            throw logException("shutdown", e);
        }
    }

    @ExportMessage
    final int getsockopt(int sockfd, int level, int optname, byte[] optval, int optlen,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) throws PosixException {
        logEnter("getsockopt", "%d, %d, %d, %s, %d", sockfd, level, optname, optval, optlen);
        try {
            return logExit("getsockopt", "%d", lib.getsockopt(delegate, sockfd, level, optname, optval, optlen));
        } catch (PosixException e) {
            throw logException("getsockopt", e);
        }
    }

    @ExportMessage
    final void setsockopt(int sockfd, int level, int optname, byte[] optval, int optlen,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) throws PosixException {
        logEnter("setsockopt", "%d, %d, %d, %s, %d", sockfd, level, optname, optval, optlen);
        try {
            lib.setsockopt(delegate, sockfd, level, optname, optval, optlen);
        } catch (PosixException e) {
            throw logException("setsockopt", e);
        }
    }

    @ExportMessage
    final int inet_addr(Object src,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) {
        logEnter("inet_addr", "%s", src);
        return logExit("inet_addr", "%d", lib.inet_addr(delegate, src));
    }

    @ExportMessage
    final int inet_aton(Object src,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) throws InvalidAddressException {
        logEnter("inet_aton", "%s", src);
        try {
            return logExit("inet_aton", "%d", lib.inet_aton(delegate, src));
        } catch (InvalidAddressException e) {
            throw logException("inet_aton", e);
        }
    }

    @ExportMessage
    final Object inet_ntoa(int address,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) {
        logEnter("inet_ntoa", "%d", address);
        return logExit("inet_ntoa", "%s", lib.inet_ntoa(delegate, address));
    }

    @ExportMessage
    final byte[] inet_pton(int family, Object src,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) throws PosixException, InvalidAddressException {
        logEnter("inet_pton", "%d, %s", family, src);
        try {
            return logExit("inet_pton", "%s", lib.inet_pton(delegate, family, src));
        } catch (PosixException e) {
            throw logException("inet_pton", e);
        } catch (InvalidAddressException e) {
            throw logException("inet_pton", e);
        }
    }

    @ExportMessage
    final Object inet_ntop(int family, byte[] src,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) throws PosixException {
        logEnter("inet_ntop", "%d, %s", family, src);
        try {
            return logExit("inet_ntop", "%s", lib.inet_ntop(delegate, family, src));
        } catch (PosixException e) {
            throw logException("inet_ntop", e);
        }
    }

    @ExportMessage
    final Object gethostname(@CachedLibrary("this.delegate") PosixSupportLibrary lib) throws PosixException {
        logEnter("gethostname", "");
        try {
            return logExit("gethostname", "%s", lib.gethostname(delegate));
        } catch (PosixException e) {
            throw logException("gethostname", e);
        }
    }

    @ExportMessage
    final Object[] getnameinfo(UniversalSockAddr addr, int flags,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) throws GetAddrInfoException {
        logEnter("getnameinfo", "%s, %d", addr, flags);
        try {
            return logExit("getnameinfo", "%s", lib.getnameinfo(delegate, addr, flags));
        } catch (GetAddrInfoException e) {
            throw logException("getnameinfo", e);
        }
    }

    @ExportMessage
    final AddrInfoCursor getaddrinfo(Object node, Object service, int family, int sockType, int protocol, int flags,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) throws GetAddrInfoException {
        logEnter("getaddrinfo", "%s, %s, %d, %d, %d, %d", node, service, family, sockType, protocol, flags);
        try {
            return logExit("getaddrinfo", "%s", lib.getaddrinfo(delegate, node, service, family, sockType, protocol, flags));
        } catch (GetAddrInfoException e) {
            throw logException("getaddrinfo", e);
        }
    }

    @ExportMessage
    final TruffleString crypt(TruffleString word, TruffleString salt,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) throws PosixException {
        logEnter("crypt", "%s, %s", word, salt);
        try {
            return logExit("crypt", "%s", lib.crypt(delegate, word, salt));
        } catch (PosixException e) {
            throw logException("crypt", e);
        }
    }

    @ExportMessage
    final UniversalSockAddr createUniversalSockAddr(FamilySpecificSockAddr src,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) {
        logEnter("createUniversalSockAddr", "%s", src);
        return logExit("createUniversalSockAddr", "%s", lib.createUniversalSockAddr(delegate, src));
    }

    @ExportMessage
    final Object createPathFromString(TruffleString path,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) {
        logEnter(Level.FINEST, "createPathFromString", "%s", path);
        return logExit(Level.FINEST, "createPathFromString", "%s", lib.createPathFromString(delegate, path));
    }

    @ExportMessage
    final Object createPathFromBytes(byte[] path,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) {
        logEnter(Level.FINEST, "createPathFromBytes", "%s", path);
        return logExit(Level.FINEST, "createPathFromBytes", "%s", lib.createPathFromBytes(delegate, path));
    }

    @ExportMessage
    final TruffleString getPathAsString(Object path,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) {
        logEnter(Level.FINEST, "getPathAsString", "%s", path);
        return logExit(Level.FINEST, "getPathAsString", "%s", lib.getPathAsString(delegate, path));
    }

    @ExportMessage
    final Buffer getPathAsBytes(Object path,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) {
        logEnter(Level.FINEST, "getPathAsBytes", "%s", path);
        return logExit(Level.FINEST, "getPathAsBytes", "%s", lib.getPathAsBytes(delegate, path));
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
    private static PosixException logException(Level level, String msg, PosixException e) throws PosixException {
        if (LOGGER.isLoggable(level)) {
            LOGGER.log(level, msg + String.format(" -> throw errno=%d, msg=%s", fixLogArgs(e.getErrorCode(), e.getMessage())));
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

    private static PosixException logException(String msg, PosixException e) throws PosixException {
        throw logException(DEFAULT_LEVEL, msg, e);
    }

    private static GetAddrInfoException logException(String msg, GetAddrInfoException e) throws GetAddrInfoException {
        throw logException(DEFAULT_LEVEL, msg, e);
    }

    private static InvalidAddressException logException(String msg, InvalidAddressException e) throws InvalidAddressException {
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
