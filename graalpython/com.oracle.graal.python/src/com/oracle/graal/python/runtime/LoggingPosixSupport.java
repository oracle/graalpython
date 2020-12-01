/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.logging.Level;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.runtime.PosixSupportLibrary.Buffer;
import com.oracle.graal.python.runtime.PosixSupportLibrary.PosixException;
import com.oracle.graal.python.runtime.PosixSupportLibrary.PosixFd;
import com.oracle.graal.python.runtime.PosixSupportLibrary.PosixPath;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.util.PythonUtils;
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

/**
 * Implementation of POSIX support that delegates to another instance and logs all the messages
 * together with their arguments, return values and exceptions. Note that:
 * <ul>
 * <li>data read from/written to files are not logged</li>
 * <li>all filenames including full paths are logged</li>
 * <li>only {@link PosixException} are logged</li>
 * <li>this class assumes default string/bytes encoding to keep it simple</li>
 * <li>logging must be enabled using the
 * {@code --log.python.com.oracle.graal.python.runtime.LoggingPosixSupport.level=FINE} option</li>
 * </ul>
 *
 * Logging levels:
 * <ul>
 * <li>FINE - all important messages</li>
 * <li>FINER - supporting messages (e.g. path conversions) + top 3 frames of the call stack</li>
 * <li>FINEST - whole call stack</li>
 * </ul>
 */
@ExportLibrary(PosixSupportLibrary.class)
public class LoggingPosixSupport extends PosixSupport {

    private static final TruffleLogger LOGGER = PythonLanguage.getLogger(LoggingPosixSupport.class);
    private static final Level DEFAULT_LEVEL = Level.FINE;

    protected final PosixSupport delegate;

    public LoggingPosixSupport(PosixSupport delegate) {
        this.delegate = delegate;
    }

    public static boolean isEnabled() {
        return LoggingPosixSupport.LOGGER.isLoggable(DEFAULT_LEVEL);
    }

    @Override
    public void setEnv(Env env) {
        delegate.setEnv(env);
    }

    @ExportMessage
    final String getBackend(
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) {
        logEnter(Level.FINER, "getBackend", "");
        return logExit(Level.FINER, "getBackend", "%s", lib.getBackend(delegate));
    }

    @ExportMessage
    final String strerror(int errorCode,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) {
        logEnter(Level.FINER, "strerror", "%d", errorCode);
        return logExit(Level.FINER, "strerror", "%s", lib.strerror(delegate, errorCode));
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
    final int openAt(int dirFd, PosixPath pathname, int flags, int mode,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) throws PosixException {
        logEnter("openAt", "%d, %s, 0x%x, 0%o", dirFd, pathname, flags, mode);
        try {
            return logExit("openAt", "%d", lib.openAt(delegate, dirFd, pathname, flags, mode));
        } catch (PosixException e) {
            throw logException("openAt", e);
        }
    }

    @ExportMessage
    final void close(int fd,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) throws PosixException {
        logEnter("close", "%d", fd);
        try {
            lib.close(delegate, fd);
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
    final long[] fstatAt(int dirFd, PosixPath pathname, boolean followSymlinks,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) throws PosixException {
        logEnter("fstatAt", "%d, %s, %b", dirFd, pathname, followSymlinks);
        try {
            return logExit("fstatAt", "%s", lib.fstatAt(delegate, dirFd, pathname, followSymlinks));
        } catch (PosixException e) {
            throw logException("fstatAt", e);
        }
    }

    @ExportMessage
    final long[] fstat(int fd, Object filename, boolean handleEintr,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) throws PosixException {
        logEnter("fstat", "%d, %s, %b", fd, filename, handleEintr);
        try {
            return logExit("fstat", "%s", lib.fstat(delegate, fd, filename, handleEintr));
        } catch (PosixException e) {
            throw logException("fstat", e);
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
    final void unlinkAt(int dirFd, PosixPath pathname, boolean rmdir,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) throws PosixException {
        logEnter("unlinkAt", "%d, %s, %b", dirFd, pathname, rmdir);
        try {
            lib.unlinkAt(delegate, dirFd, pathname, rmdir);
        } catch (PosixException e) {
            throw logException("unlinkAt", e);
        }
    }

    @ExportMessage
    final void symlinkAt(PosixPath target, int linkpathDirFd, PosixPath linkpath,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) throws PosixException {
        logEnter("symlinkAt", "%s, %d, %s", target, linkpathDirFd, linkpath);
        try {
            lib.symlinkAt(delegate, target, linkpathDirFd, linkpath);
        } catch (PosixException e) {
            throw logException("symlinkAt", e);
        }
    }

    @ExportMessage
    final void mkdirAt(int dirFd, PosixPath pathname, int mode,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) throws PosixException {
        logEnter("mkdirAt", "%d, %s, 0%o", dirFd, pathname, mode);
        try {
            lib.mkdirAt(delegate, dirFd, pathname, mode);
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
    final void chdir(PosixPath path,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) throws PosixException {
        logEnter("chdir", "%s", path);
        try {
            lib.chdir(delegate, path);
        } catch (PosixException e) {
            throw logException("chdir", e);
        }
    }

    @ExportMessage
    final void fchdir(int fd, Object pathname, boolean handleEintr,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) throws PosixException {
        logEnter("fchdir", "%d, %s, %b", fd, pathname, handleEintr);
        try {
            lib.fchdir(delegate, fd, pathname, handleEintr);
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
    final Object opendir(PosixPath path,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) throws PosixException {
        logEnter("opendir", "%s", path);
        try {
            return logExit("opendir", "%s", lib.opendir(delegate, path));
        } catch (PosixException e) {
            throw logException("opendir", e);
        }
    }

    @ExportMessage
    final Object fdopendir(PosixFd fd,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) throws PosixException {
        logEnter("fdopendir", "%s", fd);
        try {
            return logExit("fdopendir", "%s", lib.fdopendir(delegate, fd));
        } catch (PosixException e) {
            throw logException("fdopendir", e);
        }
    }

    @ExportMessage
    final void closedir(Object dirStream,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) {
        logEnter("closedir", "%s", dirStream);
        lib.closedir(delegate, dirStream);
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
    final Object dirEntryGetPath(Object dirEntry, PosixPath scandirPath,
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
    final void utimeNsAt(int dirFd, PosixPath pathname, long[] timespec, boolean followSymlinks,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) throws PosixException {
        logEnter("utimeNsAt", "%d, %s, %s, %b", dirFd, pathname, timespec, followSymlinks);
        try {
            lib.utimeNsAt(delegate, dirFd, pathname, timespec, followSymlinks);
        } catch (PosixException e) {
            throw logException("utimeNsAt", e);
        }
    }

    @ExportMessage
    final void futimeNs(PosixFd fd, long[] timespec,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) throws PosixException {
        logEnter("futimeNs", "%s, %s", fd, timespec);
        try {
            lib.futimeNs(delegate, fd, timespec);
        } catch (PosixException e) {
            throw logException("futimeNs", e);
        }
    }

    @ExportMessage
    final void renameAt(int oldDirFd, PosixPath oldPath, int newDirFd, PosixPath newPath,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) throws PosixException {
        logEnter("renameAt", "%d, %s, %d, %s", oldDirFd, oldPath, newDirFd, newPath);
        try {
            lib.renameAt(delegate, oldDirFd, oldPath, newDirFd, newPath);
        } catch (PosixException e) {
            throw logException("renameAt", e);
        }
    }

    @ExportMessage
    final boolean faccessAt(int dirFd, PosixPath path, int mode, boolean effectiveIds, boolean followSymlinks,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) {
        logEnter("faccessAt", "%d, %s, 0%o, %b, %b", dirFd, path, mode, effectiveIds, followSymlinks);
        return logExit("faccessAt", "%b", lib.faccessAt(delegate, dirFd, path, mode, effectiveIds, followSymlinks));
    }

    @ExportMessage
    final void fchmodat(int dirFd, PosixPath path, int mode, boolean followSymlinks,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) throws PosixException {
        logEnter("fchmodat", "%d, %s, 0%o, %b", dirFd, path, mode, followSymlinks);
        try {
            lib.fchmodat(delegate, dirFd, path, mode, followSymlinks);
        } catch (PosixException e) {
            throw logException("fchmodat", e);
        }
    }

    @ExportMessage
    final void fchmod(PosixFd fd, int mode,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) throws PosixException {
        logEnter("fchmod", "%s, 0%o", fd, mode);
        try {
            lib.fchmod(delegate, fd, mode);
        } catch (PosixException e) {
            throw logException("fchmod", e);
        }
    }

    @ExportMessage
    final Object readlinkat(int dirFd, PosixPath path,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) throws PosixException {
        logEnter("readlinkat", "%d, %s", dirFd, path);
        try {
            return logExit("readlinkat", "%s", lib.readlinkat(delegate, dirFd, path));
        } catch (PosixException e) {
            throw logException("readlinkat", e);
        }
    }

    @ExportMessage
    final Object createPathFromString(String path,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) {
        logEnter(Level.FINER, "createPathFromString", "%s", path);
        return logExit(Level.FINER, "createPathFromString", "%s", lib.createPathFromString(delegate, path));
    }

    @ExportMessage
    final Object createPathFromBytes(byte[] path,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) {
        logEnter(Level.FINER, "createPathFromBytes", "%s", path);
        return logExit(Level.FINER, "createPathFromBytes", "%s", lib.createPathFromBytes(delegate, path));
    }

    @ExportMessage
    final String getPathAsString(Object path,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) {
        logEnter(Level.FINER, "getPathAsString", "%s", path);
        return logExit(Level.FINER, "getPathAsString", "%s", lib.getPathAsString(delegate, path));
    }

    @ExportMessage
    final PBytes getPathAsBytes(Object path, PythonObjectFactory factory,
                    @CachedLibrary("this.delegate") PosixSupportLibrary lib) {
        logEnter(Level.FINER, "getPathAsBytes", "%s", path);
        return logExit(Level.FINER, "getPathAsBytes", "%s", lib.getPathAsBytes(delegate, path, factory));
    }

    @TruffleBoundary
    private static void logEnter(Level level, String msg, String argFmt, Object... args) {
        if (LOGGER.isLoggable(level)) {
            LOGGER.log(level, msg + '(' + String.format(argFmt, fixLogArgs(args)) + ')');
            if (LOGGER.isLoggable(Level.FINEST)) {
                logStackTrace(Level.FINEST, 0, Integer.MAX_VALUE);
            } else if (LOGGER.isLoggable(Level.FINER)) {
                logStackTrace(Level.FINER, 0, 3);
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

    private static <T> T logExit(String msg, String argFtm, T retVal) {
        return logExit(DEFAULT_LEVEL, msg, argFtm, retVal);
    }

    @TruffleBoundary
    private static PosixException logException(Level level, String msg, PosixException e) throws PosixException {
        if (LOGGER.isLoggable(level)) {
            LOGGER.log(level, msg + String.format(" -> throw errno=%d, msg=%s, filename1=%s, filename2=%s", fixLogArgs(e.getErrorCode(), e.getMessage(), e.getFilename1(), e.getFilename2())));
        }
        throw e;
    }

    private static PosixException logException(String msg, PosixException e) throws PosixException {
        throw logException(DEFAULT_LEVEL, msg, e);
    }

    private static Object fixLogArg(Object arg) {
        if (arg instanceof PosixPath) {
            PosixPath path = (PosixPath) arg;
            return "PosixPath{" + fixLogArg(path.value) + ", " + fixLogArg(path.originalObject) + ", " + path.wasBufferLike + "}";
        }
        if (arg instanceof PosixFd) {
            PosixFd fd = (PosixFd) arg;
            return "PosixFd{" + fd.fd + ", " + fixLogArg(fd.originalObject) + "}";
        }
        if (arg instanceof String) {
            return "'" + arg + "'";
        }
        if (arg instanceof Buffer) {
            Buffer b = (Buffer) arg;
            return "Buffer{" + asString(b.data, 0, (int) b.length) + "}";
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
            return Arrays.toString((Object[]) arg);
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

    private static String asString(byte[] bytes, int offset, int length) {
        return "b'" + PythonUtils.newString(bytes, offset, length) + "'";
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
