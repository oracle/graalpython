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

import java.lang.ref.PhantomReference;
import java.lang.ref.ReferenceQueue;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.exception.OSErrorEnum;
import com.oracle.graal.python.runtime.NativeLibrary.InvokeNativeFunction;
import com.oracle.graal.python.runtime.NativeLibrary.NFIBackend;
import com.oracle.graal.python.runtime.NativeLibrary.NativeFunction;
import com.oracle.graal.python.runtime.NativeLibrary.TypedNativeLibrary;
import com.oracle.graal.python.runtime.PosixSupportLibrary.Buffer;
import com.oracle.graal.python.runtime.PosixSupportLibrary.PosixException;
import com.oracle.graal.python.runtime.PosixSupportLibrary.PosixFd;
import com.oracle.graal.python.runtime.PosixSupportLibrary.PosixPath;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.profiles.BranchProfile;

/**
 * Implementation that invokes the native POSIX functions directly using NFI. This requires either
 * that the native access is allowed or to configure managed LLVM backend for NFI.
 */
@ExportLibrary(PosixSupportLibrary.class)
public final class NFIPosixSupport extends PosixSupport {
    private static final String SUPPORTING_NATIVE_LIB_NAME = "libposix";

    private static final int UNAME_BUF_LENGTH = 256;
    private static final int DIRENT_NAME_BUF_LENGTH = 256;

    private static final TruffleLogger LOGGER = PythonLanguage.getLogger(NFIPosixSupport.class);

    private final ReferenceQueue<DirStream> dirStreamRefQueue = new ReferenceQueue<>();

    enum NativeFunctions implements NativeFunction {
        get_errno("():sint32"),
        set_errno("(sint32):void"),
        call_strerror("(sint32, [sint8], sint32):void"),
        call_getpid("():sint64"),
        call_umask("(sint32):sint32"),
        call_openat("(sint32, [sint8], sint32, sint32):sint32"),
        call_close("(sint32):sint32"),
        call_read("(sint32, [sint8], uint64):sint64"),
        call_write("(sint32, [sint8], uint64):sint64"),
        call_dup("(sint32):sint32"),
        call_dup2("(sint32, sint32, sint32):sint32"),
        call_pipe2("([sint32]):sint32"),
        call_lseek("(sint32, sint64, sint32):sint64"),
        call_ftruncate("(sint32, sint64):sint32"),
        call_fsync("(sint32):sint32"),
        call_fstatat("(sint32, [sint8], sint32, [sint64]):sint32"),
        call_fstat("(sint32, [sint64]):sint32"),
        call_uname("([sint8], [sint8], [sint8], [sint8], [sint8], sint32):sint32"),
        call_unlinkat("(sint32, [sint8], sint32):sint32"),
        call_symlinkat("([sint8], sint32, [sint8]):sint32"),
        call_mkdirat("(sint32, [sint8], sint32):sint32"),
        call_getcwd("([sint8], uint64):sint32"),
        call_chdir("([sint8]):sint32"),
        call_fchdir("(sint32):sint32"),
        call_isatty("(sint32):sint32"),
        call_opendir("([sint8]):sint64"),
        call_fdopendir("(sint32):sint64"),
        call_closedir("(sint64, sint32):sint32"),
        call_readdir("(sint64, [sint8], uint64, [sint64]):sint32"),
        call_utimensat("(sint32, [sint8], [sint64], sint32):sint32"),
        call_futimens("(sint32, [sint64]):sint32"),
        call_renameat("(sint32, [sint8], sint32, [sint8]):sint32"),
        call_faccessat("(sint32, [sint8], sint32, sint32, sint32):sint32"),
        get_inheritable("(sint32):sint32"),
        set_inheritable("(sint32, sint32):sint32"),
        get_blocking("(sint32):sint32"),
        set_blocking("(sint32, sint32):sint32"),
        get_terminal_size("(sint32, [sint32]):sint32");

        private final String signature;

        NativeFunctions(String signature) {
            this.signature = signature;
        }

        @Override
        public String signature() {
            return signature;
        }
    }

    private final PythonContext context;
    private final TypedNativeLibrary<NativeFunctions> lib;

    private NFIPosixSupport(PythonContext context, NFIBackend backend) {
        this.context = context;
        lib = NativeLibrary.create(SUPPORTING_NATIVE_LIB_NAME, NativeFunctions.values(), backend,
                        "You can switch to pure Java emulated POSIX support, " +
                                        "which does not require native access privilege, by passing option " +
                                        "'--python.PosixModuleBackend=java'.");
        // TODO registerAsyncAction - use SharedFinalizer from PR #1316
    }

    public static NFIPosixSupport createNative(PythonContext context) {
        return new NFIPosixSupport(context, NFIBackend.NATIVE);
    }

    public static NFIPosixSupport createLLVM(PythonContext context) {
        return new NFIPosixSupport(context, NFIBackend.LLVM);
    }

    @ExportMessage
    public String getBackend() {
        return lib.getNfiBackend() == NFIBackend.LLVM ? "llvm" : "native";
    }

    @ExportMessage
    public String strerror(int errorCode,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) {
        // From man pages: The GNU C Library uses a buffer of 1024 characters for strerror().
        // This buffer size therefore should be sufficient to avoid an ERANGE error when calling
        // strerror_r().
        byte[] buf = new byte[1024];
        invokeNode.call(lib, NativeFunctions.call_strerror, errorCode, wrap(buf), buf.length);
        // TODO PyUnicode_DecodeLocale
        return cStringToJavaString(buf);
    }

    @ExportMessage
    public long getpid(@Shared("invoke") @Cached InvokeNativeFunction invokeNode) {
        return invokeNode.callLong(lib, NativeFunctions.call_getpid);
    }

    @ExportMessage
    public int umask(int mask,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        int result = invokeNode.callInt(lib, NativeFunctions.call_umask, mask);
        if (result < 0) {
            throw getErrnoAndThrowPosixException(invokeNode);
        }
        return result;
    }

    @ExportMessage
    public int openAt(int dirFd, PosixPath pathname, int flags, int mode,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode,
                    @Shared("async") @Cached BranchProfile asyncProfile) throws PosixException {
        while (true) {
            int fd = invokeNode.callInt(lib, NativeFunctions.call_openat, dirFd, pathToCString(pathname), flags, mode);
            if (fd >= 0) {
                return fd;
            }
            int errno = getErrno(invokeNode);
            if (errno != OSErrorEnum.EINTR.getNumber()) {
                throw newPosixException(invokeNode, errno, pathname.originalObject);
            }
            context.triggerAsyncActions(null, asyncProfile);
        }
    }

    @ExportMessage
    public void close(int fd,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        if (invokeNode.callInt(lib, NativeFunctions.call_close, fd) < 0) {
            throw getErrnoAndThrowPosixException(invokeNode);
        }
    }

    @ExportMessage
    public Buffer read(int fd, long length,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode,
                    @Shared("async") @Cached BranchProfile asyncProfile) throws PosixException {
        Buffer buffer = Buffer.allocate(length);
        while (true) {
            setErrno(invokeNode, 0);
            long n = invokeNode.callLong(lib, NativeFunctions.call_read, fd, wrap(buffer), length);
            if (n >= 0) {
                return buffer.withLength(n);
            }
            int errno = getErrno(invokeNode);
            if (errno != OSErrorEnum.EINTR.getNumber()) {
                throw newPosixException(invokeNode, errno);
            }
            context.triggerAsyncActions(null, asyncProfile);
        }
    }

    @ExportMessage
    public long write(int fd, Buffer data,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode,
                    @Shared("async") @Cached BranchProfile asyncProfile) throws PosixException {
        while (true) {
            setErrno(invokeNode, 0);
            long n = invokeNode.callLong(lib, NativeFunctions.call_write, fd, wrap(data), data.length);
            if (n >= 0) {
                return n;
            }
            int errno = getErrno(invokeNode);
            if (errno != OSErrorEnum.EINTR.getNumber()) {
                throw newPosixException(invokeNode, errno);
            }
            context.triggerAsyncActions(null, asyncProfile);
        }
    }

    @ExportMessage
    public int dup(int fd,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        int newFd = invokeNode.callInt(lib, NativeFunctions.call_dup, fd);
        if (newFd < 0) {
            throw getErrnoAndThrowPosixException(invokeNode);
        }
        return newFd;
    }

    @ExportMessage
    public int dup2(int fd, int fd2, boolean inheritable,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        int newFd = invokeNode.callInt(lib, NativeFunctions.call_dup2, fd, fd2, inheritable ? 1 : 0);
        if (newFd < 0) {
            throw getErrnoAndThrowPosixException(invokeNode);
        }
        return newFd;
    }

    @ExportMessage
    public boolean getInheritable(int fd,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        int result = invokeNode.callInt(lib, NativeFunctions.get_inheritable, fd);
        if (result < 0) {
            throw getErrnoAndThrowPosixException(invokeNode);
        }
        return result != 0;
    }

    @ExportMessage
    public void setInheritable(int fd, boolean inheritable,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        if (invokeNode.callInt(lib, NativeFunctions.set_inheritable, fd, inheritable ? 1 : 0) < 0) {
            throw getErrnoAndThrowPosixException(invokeNode);
        }
    }

    @ExportMessage
    public int[] pipe(
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        int[] fds = new int[2];
        if (invokeNode.callInt(lib, NativeFunctions.call_pipe2, context.getEnv().asGuestValue(fds)) != 0) {
            throw getErrnoAndThrowPosixException(invokeNode);
        }
        return fds;
    }

    @ExportMessage
    public long lseek(int fd, long offset, int how,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        long res = invokeNode.callLong(lib, NativeFunctions.call_lseek, fd, offset, how);
        if (res < 0) {
            throw getErrnoAndThrowPosixException(invokeNode);
        }
        return res;
    }

    @ExportMessage
    public void ftruncate(int fd, long length,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode,
                    @Shared("async") @Cached BranchProfile asyncProfile) throws PosixException {
        while (true) {
            int res = invokeNode.callInt(lib, NativeFunctions.call_ftruncate, fd, length);
            if (res == 0) {
                return;
            }
            int errno = getErrno(invokeNode);
            if (errno != OSErrorEnum.EINTR.getNumber()) {
                throw newPosixException(invokeNode, errno);
            }
            context.triggerAsyncActions(null, asyncProfile);
        }
    }

    @ExportMessage
    public void fsync(int fd,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode,
                    @Shared("async") @Cached BranchProfile asyncProfile) throws PosixException {
        while (true) {
            int res = invokeNode.callInt(lib, NativeFunctions.call_fsync, fd);
            if (res == 0) {
                return;
            }
            int errno = getErrno(invokeNode);
            if (errno != OSErrorEnum.EINTR.getNumber()) {
                throw newPosixException(invokeNode, errno);
            }
            context.triggerAsyncActions(null, asyncProfile);
        }
    }

    @ExportMessage
    public boolean getBlocking(int fd,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        int result = invokeNode.callInt(lib, NativeFunctions.get_blocking, fd);
        if (result < 0) {
            throw getErrnoAndThrowPosixException(invokeNode);
        }
        return result != 0;
    }

    @ExportMessage
    public void setBlocking(int fd, boolean blocking,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        if (invokeNode.callInt(lib, NativeFunctions.set_blocking, fd, blocking ? 1 : 0) < 0) {
            throw getErrnoAndThrowPosixException(invokeNode);
        }
    }

    @ExportMessage
    public int[] getTerminalSize(int fd,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        int[] size = new int[2];
        if (invokeNode.callInt(lib, NativeFunctions.get_terminal_size, fd, context.getEnv().asGuestValue(size)) != 0) {
            throw getErrnoAndThrowPosixException(invokeNode);
        }
        return size;
    }

    @ExportMessage
    public long[] fstatAt(int dirFd, PosixPath pathname, boolean followSymlinks,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        long[] out = new long[13];
        int res = invokeNode.callInt(lib, NativeFunctions.call_fstatat, dirFd, pathToCString(pathname), followSymlinks ? 1 : 0, wrap(out));
        if (res != 0) {
            throw newPosixException(invokeNode, getErrno(invokeNode), pathname.originalObject);
        }
        return out;
    }

    @ExportMessage
    public long[] fstat(int fd, Object filename, boolean handleEintr,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode,
                    @Shared("async") @Cached BranchProfile asyncProfile) throws PosixException {
        long[] out = new long[13];
        while (true) {
            int res = invokeNode.callInt(lib, NativeFunctions.call_fstat, fd, wrap(out));
            if (res == 0) {
                return out;
            }
            int errno = getErrno(invokeNode);
            if (!handleEintr || errno != OSErrorEnum.EINTR.getNumber()) {
                throw newPosixException(invokeNode, errno, filename);
            }
            context.triggerAsyncActions(null, asyncProfile);
        }
    }

    @ExportMessage
    public Object[] uname(
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        byte[] sys = new byte[UNAME_BUF_LENGTH];
        byte[] node = new byte[UNAME_BUF_LENGTH];
        byte[] rel = new byte[UNAME_BUF_LENGTH];
        byte[] ver = new byte[UNAME_BUF_LENGTH];
        byte[] machine = new byte[UNAME_BUF_LENGTH];
        int res = invokeNode.callInt(lib, NativeFunctions.call_uname, wrap(sys), wrap(node), wrap(rel), wrap(ver), wrap(machine), UNAME_BUF_LENGTH);
        if (res != 0) {
            throw getErrnoAndThrowPosixException(invokeNode);
        }
        return new Object[]{
                        // TODO PyUnicode_DecodeFSDefault
                        cStringToJavaString(sys),
                        cStringToJavaString(node),
                        cStringToJavaString(rel),
                        cStringToJavaString(ver),
                        cStringToJavaString(machine)
        };
    }

    @ExportMessage
    public void unlinkAt(int dirFd, PosixPath pathname, boolean rmdir,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        int result = invokeNode.callInt(lib, NativeFunctions.call_unlinkat, dirFd, pathToCString(pathname), rmdir ? 1 : 0);
        if (result != 0) {
            throw newPosixException(invokeNode, getErrno(invokeNode), pathname.originalObject);
        }
    }

    @ExportMessage
    public void symlinkAt(PosixPath target, int linkpathDirFd, PosixPath linkpath,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        int result = invokeNode.callInt(lib, NativeFunctions.call_symlinkat, pathToCString(target), linkpathDirFd, pathToCString(linkpath));
        if (result != 0) {
            throw newPosixException(invokeNode, getErrno(invokeNode), target.originalObject, linkpath.originalObject);
        }
    }

    @ExportMessage
    public void mkdirAt(int dirFd, PosixPath pathname, int mode,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        int result = invokeNode.callInt(lib, NativeFunctions.call_mkdirat, dirFd, pathToCString(pathname), mode);
        if (result != 0) {
            throw newPosixException(invokeNode, getErrno(invokeNode), pathname.originalObject);
        }
    }

    @ExportMessage
    public Object getcwd(
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        for (int bufLen = 1024;; bufLen += 1024) {
            Buffer buffer = Buffer.allocate(bufLen);
            int n = invokeNode.callInt(lib, NativeFunctions.call_getcwd, wrap(buffer), bufLen);
            if (n == 0) {
                buffer = buffer.withLength(findZero(buffer.data));
                return buffer;
            }
            int errno = getErrno(invokeNode);
            if (errno != OSErrorEnum.ERANGE.getNumber()) {
                throw newPosixException(invokeNode, errno);
            }
        }
    }

    @ExportMessage
    public void chdir(PosixPath path,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        int result = invokeNode.callInt(lib, NativeFunctions.call_chdir, pathToCString(path));
        if (result != 0) {
            throw newPosixException(invokeNode, getErrno(invokeNode), path.originalObject);
        }
    }

    @ExportMessage
    public void fchdir(int fd, Object pathname, boolean handleEintr,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode,
                    @Shared("async") @Cached BranchProfile asyncProfile) throws PosixException {
        while (true) {
            if (invokeNode.callInt(lib, NativeFunctions.call_fchdir, fd) == 0) {
                return;
            }
            int errno = getErrno(invokeNode);
            if (!handleEintr || errno != OSErrorEnum.EINTR.getNumber()) {
                throw newPosixException(invokeNode, errno, pathname);
            }
            context.triggerAsyncActions(null, asyncProfile);
        }
    }

    @ExportMessage
    public boolean isatty(int fd,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) {
        return invokeNode.callInt(lib, NativeFunctions.call_isatty, fd) != 0;
    }

    @ExportMessage
    public Object opendir(PosixPath path,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        long ptr = invokeNode.callLong(lib, NativeFunctions.call_opendir, pathToCString(path));
        if (ptr == 0) {
            throw newPosixException(invokeNode, getErrno(invokeNode), path.originalObject);
        }
        return new DirStream(dirStreamRefQueue, ptr, false);
    }

    @ExportMessage
    public Object fdopendir(PosixFd fd,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        long ptr = invokeNode.callLong(lib, NativeFunctions.call_fdopendir, fd.fd);
        if (ptr == 0) {
            throw newPosixException(invokeNode, getErrno(invokeNode), fd.originalObject);
        }
        return new DirStream(dirStreamRefQueue, ptr, true);
    }

    @ExportMessage
    public void closedir(Object dirStreamObj,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) {
        DirStream dirStream = (DirStream) dirStreamObj;
        synchronized (dirStream.ref.lock) {
            if (!dirStream.ref.closed) {
                dirStream.ref.closed = true;
                int res = invokeNode.callInt(lib, NativeFunctions.call_closedir, dirStream.ref.nativePtr, dirStream.ref.needsRewind ? 1 : 0);
                if (res != 0 && LOGGER.isLoggable(Level.INFO)) {
                    log(Level.INFO, "Error occured during closedir, errno=%d", getErrno(invokeNode));
                }
            }
        }
        DirStreamRef.removeFromSet(dirStream.ref);
    }

    @ExportMessage
    public Object readdir(Object dirStreamObj,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        DirStream dirStream = (DirStream) dirStreamObj;
        Buffer name = Buffer.allocate(DIRENT_NAME_BUF_LENGTH);
        long[] out = new long[2];
        int result;
        synchronized (dirStream.ref.lock) {
            if (dirStream.ref.closed) {
                return null;
            }
            do {
                result = invokeNode.callInt(lib, NativeFunctions.call_readdir, dirStream.ref.nativePtr, wrap(name), DIRENT_NAME_BUF_LENGTH, wrap(out));
            } while (result != 0 && name.data[0] == '.' && (name.data[1] == 0 || (name.data[1] == '.' && name.data[2] == 0)));
        }
        if (result != 0) {
            return new DirEntry(name.withLength(findZero(name.data)), out[0], (int) out[1]);
        }
        int errno = getErrno(invokeNode);
        if (errno == 0) {
            return null;
        }
        throw newPosixException(invokeNode, errno);
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
        static Buffer withSlash(@SuppressWarnings("unused") NFIPosixSupport receiver, DirEntry dirEntry, PosixPath scandirPath) {
            Buffer scandirPathBuffer = (Buffer) scandirPath.value;
            int pathLen = scandirPathBuffer.data.length;
            int nameLen = (int) dirEntry.name.length;
            byte[] buf = new byte[pathLen + nameLen];
            PythonUtils.arraycopy(scandirPathBuffer.data, 0, buf, 0, pathLen);
            PythonUtils.arraycopy(dirEntry.name.data, 0, buf, pathLen, nameLen);
            return Buffer.wrap(buf);
        }

        @Specialization(guards = "!endsWithSlash(scandirPath)")
        static Buffer withoutSlash(@SuppressWarnings("unused") NFIPosixSupport receiver, DirEntry dirEntry, PosixPath scandirPath) {
            Buffer scandirPathBuffer = (Buffer) scandirPath.value;
            int pathLen = scandirPathBuffer.data.length;
            int nameLen = (int) dirEntry.name.length;
            byte[] buf = new byte[pathLen + 1 + nameLen];
            PythonUtils.arraycopy(scandirPathBuffer.data, 0, buf, 0, pathLen);
            buf[pathLen] = PosixSupportLibrary.POSIX_FILENAME_SEPARATOR;
            PythonUtils.arraycopy(dirEntry.name.data, 0, buf, pathLen + 1, nameLen);
            return Buffer.wrap(buf);
        }

        protected static boolean endsWithSlash(PosixPath path) {
            Buffer b = (Buffer) path.value;
            return b.data[b.data.length - 1] == PosixSupportLibrary.POSIX_FILENAME_SEPARATOR;
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
    public void utimeNsAt(int dirFd, PosixPath pathname, long[] timespec, boolean followSymlinks,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        assert timespec == null || timespec.length == 4;
        int ret = invokeNode.callInt(lib, NativeFunctions.call_utimensat, dirFd, pathToCString(pathname), wrap(timespec), followSymlinks ? 1 : 0);
        if (ret != 0) {
            // filename is intentionally not included, see CPython's os_utime_impl
            throw newPosixException(invokeNode, getErrno(invokeNode));
        }
    }

    @ExportMessage
    public void futimeNs(PosixFd fd, long[] timespec,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        assert timespec == null || timespec.length == 4;
        int ret = invokeNode.callInt(lib, NativeFunctions.call_futimens, fd.fd, wrap(timespec));
        if (ret != 0) {
            // filename is intentionally not included, see CPython's os_utime_impl
            throw newPosixException(invokeNode, getErrno(invokeNode));
        }
    }

    @ExportMessage
    public void renameAt(int oldDirFd, PosixPath oldPath, int newDirFd, PosixPath newPath,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        int ret = invokeNode.callInt(lib, NativeFunctions.call_renameat, oldDirFd, pathToCString(oldPath), newDirFd, pathToCString(newPath));
        if (ret != 0) {
            throw newPosixException(invokeNode, getErrno(invokeNode), oldPath.originalObject, newPath.originalObject);
        }
    }

    @ExportMessage
    public boolean faccessAt(int dirFd, PosixPath path, int mode, boolean effectiveIds, boolean followSymlinks,
                             @Shared("invoke") @Cached InvokeNativeFunction invokeNode) {
        int ret = invokeNode.callInt(lib, NativeFunctions.call_faccessat, dirFd, pathToCString(path), mode, effectiveIds ? 1 : 0, followSymlinks ? 1 : 0);
        return ret == 0;
    }

    // ------------------
    // Path conversions

    @ExportMessage
    @SuppressWarnings("static-method")
    public Object createPathFromString(String path) {
        return checkPath(getStringBytes(path));
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    public Object createPathFromBytes(byte[] path) {
        return checkPath(path);
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    public String getPathAsString(Object path) {
        Buffer result = (Buffer) path;
        if (result.length > Integer.MAX_VALUE) {
            // sanity check that it is safe to cast result.length to int, to be removed once
            // we support large arrays
            throw CompilerDirectives.shouldNotReachHere("Posix path cannot fit into a Java array");
        }
        // TODO PyUnicode_DecodeFSDefault
        return PythonUtils.newString(result.data, 0, (int) result.length);
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    public PBytes getPathAsBytes(Object path, PythonObjectFactory factory) {
        Buffer result = (Buffer) path;
        if (result.length > Integer.MAX_VALUE) {
            // sanity check that it is safe to cast result.length to int, to be removed once
            // we support large arrays
            throw CompilerDirectives.shouldNotReachHere("Posix path cannot fit into a Java array");
        }
        return factory.createBytes(result.data, 0, (int) result.length);
    }

    @TruffleBoundary
    private static byte[] getStringBytes(String str) {
        // TODO replace getBytes with PyUnicode_FSConverter equivalent
        return str.getBytes();
    }

    private static Buffer checkPath(byte[] path) {
        for (byte b : path) {
            if (b == 0) {
                return null;
            }
        }
        return Buffer.wrap(path);
    }

    // ------------------
    // Objects/handles/pointers

    private static class DirStreamRef extends PhantomReference<DirStream> {

        // all alive references are stored in this set in order to keep them reachable
        private static final Set<DirStreamRef> dirStreamRefs = Collections.synchronizedSet(new HashSet<>());

        final long nativePtr;
        final boolean needsRewind;
        final Object lock;
        boolean closed;

        DirStreamRef(DirStream referent, ReferenceQueue<DirStream> queue, long nativePtr, boolean needsRewind) {
            super(referent, queue);
            this.nativePtr = nativePtr;
            this.needsRewind = needsRewind;
            this.lock = new Object();
            addToSet(this);
        }

        @TruffleBoundary
        static void addToSet(DirStreamRef ref) {
            dirStreamRefs.add(ref);
        }

        @TruffleBoundary
        static void removeFromSet(DirStreamRef ref) {
            dirStreamRefs.remove(ref);
        }

        @Override
        public String toString() {
            return "DirStreamStateRef{" +
                            "nativePtr=" + nativePtr +
                            ", needsRewind=" + needsRewind +
                            ", closed=" + closed +
                            '}';
        }
    }

    private static class DirStream {
        final DirStreamRef ref;

        DirStream(ReferenceQueue<DirStream> queue, long nativePtr, boolean needsRewind) {
            ref = new DirStreamRef(this, queue, nativePtr, needsRewind);
        }

        @Override
        public String toString() {
            CompilerAsserts.neverPartOfCompilation();
            return "DirStreamState -> " + ref;
        }
    }

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

    private int getErrno(InvokeNativeFunction invokeNode) {
        return invokeNode.callInt(lib, NativeFunctions.get_errno);
    }

    private void setErrno(InvokeNativeFunction invokeNode, int errno) {
        invokeNode.call(lib, NativeFunctions.set_errno, errno);
    }

    private PosixException getErrnoAndThrowPosixException(InvokeNativeFunction invokeNode) throws PosixException {
        throw newPosixException(invokeNode, getErrno(invokeNode));
    }

    private PosixException newPosixException(InvokeNativeFunction invokeNode, int errno) throws PosixException {
        throw newPosixException(invokeNode, errno, null, null);
    }

    private PosixException newPosixException(InvokeNativeFunction invokeNode, int errno, Object filename) throws PosixException {
        throw newPosixException(invokeNode, errno, filename, null);
    }

    private PosixException newPosixException(InvokeNativeFunction invokeNode, int errno, Object filename1, Object filename2) throws PosixException {
        throw new PosixException(errno, strerror(errno, invokeNode), filename1, filename2);
    }

    private Object wrap(byte[] bytes) {
        return context.getEnv().asGuestValue(bytes);
    }

    private Object wrap(long[] longs) {
        return context.getEnv().asGuestValue(longs);
    }

    private Object wrap(Buffer buffer) {
        return context.getEnv().asGuestValue(buffer.data);
    }

    private static String cStringToJavaString(byte[] buf) {
        return PythonUtils.newString(buf, 0, findZero(buf));
    }

    private static int findZero(byte[] buf) {
        for (int i = 0; i < buf.length; ++i) {
            if (buf[i] == 0) {
                return i;
            }
        }
        return buf.length;
    }

    private Object pathToCString(PosixPath path) {
        return bufferToCString((Buffer) path.value);
    }

    private Object bufferToCString(Buffer path) {
        return wrap(nullTerminate(path.data, (int) path.length));
    }

    private static byte[] nullTerminate(byte[] str, int length) {
        byte[] terminated = new byte[length + 1];
        PythonUtils.arraycopy(str, 0, terminated, 0, length);
        return terminated;
    }

    @TruffleBoundary
    private static void log(Level level, String fmt, Object... args) {
        if (LOGGER.isLoggable(level)) {
            LOGGER.log(level, String.format(fmt, args));
        }
    }
}
