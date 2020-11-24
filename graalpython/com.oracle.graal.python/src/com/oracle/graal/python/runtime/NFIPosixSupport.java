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
import java.util.ArrayList;
import java.util.Arrays;
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
import com.oracle.graal.python.runtime.PosixSupportLibrary.PosixExceptionBase;
import com.oracle.graal.python.runtime.PosixSupportLibrary.PosixExceptionWithOpaquePath;
import com.oracle.graal.python.runtime.PosixSupportLibrary.PosixFd;
import com.oracle.graal.python.runtime.PosixSupportLibrary.PosixPath;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.source.SourceSection;

/**
 * Implementation that invokes the native POSIX functions directly using NFI. This requires either
 * that the native access is allowed or to configure managed LLVM backend for NFI.
 *
 * Logging levels:
 * <ul>
 * <li>FINE - messages, their arguments, return values and thrown exceptions</li>
 * <li>FINER - top 3 frames of the call stack</li>
 * <li>FINEST - whole call stack</li>
 * </ul>
 */
@ExportLibrary(PosixSupportLibrary.class)
public final class NFIPosixSupport extends PosixSupport {
    private static final String SUPPORTING_NATIVE_LIB_NAME = "libposix";

    private static final int UNAME_BUF_LENGTH = 256;
    private static final int DIRENT_NAME_BUF_LENGTH = 256;

    private static final int DT_UNKNOWN = 0;
    private static final int DT_DIR = 4;
    private static final int DT_REG = 8;
    private static final int DT_LNK = 10;

    private static final int S_IFMT = 0170000;
    private static final int S_IFDIR = 0040000;
    private static final int S_IFREG = 0100000;
    private static final int S_IFLNK = 0120000;

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
        logEnter("getpid", "");
        return invokeNode.callLong(lib, NativeFunctions.call_getpid);
    }

    @ExportMessage
    public int umask(int mask,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        logEnter("umask", "0%o", mask);
        int result = invokeNode.callInt(lib, NativeFunctions.call_umask, mask);
        if (result < 0) {
            throw getErrnoAndThrowPosixException(invokeNode);
        }
        logExit("umask", "0%o", result);
        return result;
    }

    @ExportMessage
    public int openAt(int dirFd, PosixPath pathname, int flags, int mode,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode,
                    @Shared("async") @Cached BranchProfile asyncProfile) throws PosixException {
        logEnter("openAt", "'%s', 0x%x, 0%o", pathname, flags, mode);
        while (true) {
            int fd = invokeNode.callInt(lib, NativeFunctions.call_openat, dirFd, pathToCString(pathname), flags, mode);
            if (fd >= 0) {
                logExit("openAt", "%d", fd);
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
        logEnter("close", "%d", fd);
        if (invokeNode.callInt(lib, NativeFunctions.call_close, fd) < 0) {
            throw getErrnoAndThrowPosixException(invokeNode);
        }
    }

    @ExportMessage
    public Buffer read(int fd, long length,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode,
                    @Shared("async") @Cached BranchProfile asyncProfile) throws PosixException {
        logEnter("read", "%d, %d", fd, length);
        Buffer buffer = Buffer.allocate(length);
        while (true) {
            setErrno(invokeNode, 0);
            long n = invokeNode.callLong(lib, NativeFunctions.call_read, fd, wrap(buffer), length);
            if (n >= 0) {
                logExit("write", "%d", n);
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
        logEnter("write", "%d, %d", fd, data.length);
        while (true) {
            setErrno(invokeNode, 0);
            long n = invokeNode.callLong(lib, NativeFunctions.call_write, fd, wrap(data), data.length);
            if (n >= 0) {
                logExit("write", "%d", n);
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
        logEnter("dup", "%d", fd);
        int newFd = invokeNode.callInt(lib, NativeFunctions.call_dup, fd);
        if (newFd < 0) {
            throw getErrnoAndThrowPosixException(invokeNode);
        }
        logExit("dup", "%d", newFd);
        return newFd;
    }

    @ExportMessage
    public int dup2(int fd, int fd2, boolean inheritable,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        logEnter("dup2", "%d, %d", fd, fd2);
        int newFd = invokeNode.callInt(lib, NativeFunctions.call_dup2, fd, fd2, inheritable ? 1 : 0);
        if (newFd < 0) {
            throw getErrnoAndThrowPosixException(invokeNode);
        }
        logExit("dup2", "%d", newFd);
        return newFd;
    }

    @ExportMessage
    public boolean getInheritable(int fd,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        logEnter("getInheritable", "%d", fd);
        int result = invokeNode.callInt(lib, NativeFunctions.get_inheritable, fd);
        if (result < 0) {
            throw getErrnoAndThrowPosixException(invokeNode);
        }
        logExit("getInheritable", "%b", result != 0);
        return result != 0;
    }

    @ExportMessage
    public void setInheritable(int fd, boolean inheritable,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        logEnter("setInheritable", "%d, %b", fd, inheritable);
        if (invokeNode.callInt(lib, NativeFunctions.set_inheritable, fd, inheritable ? 1 : 0) < 0) {
            throw getErrnoAndThrowPosixException(invokeNode);
        }
    }

    @ExportMessage
    public int[] pipe(
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        logEnter("pipe", "");
        int[] fds = new int[2];
        if (invokeNode.callInt(lib, NativeFunctions.call_pipe2, context.getEnv().asGuestValue(fds)) != 0) {
            throw getErrnoAndThrowPosixException(invokeNode);
        }
        logExit("pipe", "%d, %d", fds[0], fds[1]);
        return fds;
    }

    @ExportMessage
    public long lseek(int fd, long offset, int how,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        logEnter("lseek", "%d, %d, %d", fd, offset, how);
        long res = invokeNode.callLong(lib, NativeFunctions.call_lseek, fd, offset, how);
        if (res < 0) {
            throw getErrnoAndThrowPosixException(invokeNode);
        }
        logExit("lseek", "%d", res);
        return res;
    }

    @ExportMessage
    public void ftruncate(int fd, long length,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode,
                    @Shared("async") @Cached BranchProfile asyncProfile) throws PosixException {
        logEnter("ftruncate", "%d, %d", fd, length);
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
        logEnter("fsync", "%d", fd);
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
        logEnter("getBlocking", "%d", fd);
        int result = invokeNode.callInt(lib, NativeFunctions.get_blocking, fd);
        if (result < 0) {
            throw getErrnoAndThrowPosixException(invokeNode);
        }
        logExit("getBlocking", "%b", result != 0);
        return result != 0;
    }

    @ExportMessage
    public void setBlocking(int fd, boolean blocking,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        logEnter("setBlocking", "%d, %b", fd, blocking);
        if (invokeNode.callInt(lib, NativeFunctions.set_blocking, fd, blocking ? 1 : 0) < 0) {
            throw getErrnoAndThrowPosixException(invokeNode);
        }
    }

    @ExportMessage
    public int[] getTerminalSize(int fd,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        logEnter("getTerminalSize", "%d", fd);
        int[] size = new int[2];
        if (invokeNode.callInt(lib, NativeFunctions.get_terminal_size, fd, context.getEnv().asGuestValue(size)) != 0) {
            throw getErrnoAndThrowPosixException(invokeNode);
        }
        logExit("getTerminalSize", "%d, %d", size[0], size[1]);
        return size;
    }

    @ExportMessage
    public long[] fstatAt(int dirFd, PosixPath pathname, boolean followSymlinks,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        logEnter("fstatAt", "%d, '%s', %b", dirFd, pathname, followSymlinks);
        long[] out = new long[13];
        int res = invokeNode.callInt(lib, NativeFunctions.call_fstatat, dirFd, pathToCString(pathname), followSymlinks ? 1 : 0, context.getEnv().asGuestValue(out));
        if (res != 0) {
            throw newPosixException(invokeNode, getErrno(invokeNode), pathname.originalObject);
        }
        logExit("fstatAt", "%s", out);
        return out;
    }

    @ExportMessage
    public long[] fstat(int fd, Object filename, boolean handleEintr,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode,
                    @Shared("async") @Cached BranchProfile asyncProfile) throws PosixException {
        logEnter("fstat", "%d, %s, %b", fd, filename, handleEintr);
        long[] out = new long[13];
        while (true) {
            int res = invokeNode.callInt(lib, NativeFunctions.call_fstat, fd, context.getEnv().asGuestValue(out));
            if (res == 0) {
                logExit("fstat", "%s", out);
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
        logEnter("uname", "");
        byte[] sys = new byte[UNAME_BUF_LENGTH];
        byte[] node = new byte[UNAME_BUF_LENGTH];
        byte[] rel = new byte[UNAME_BUF_LENGTH];
        byte[] ver = new byte[UNAME_BUF_LENGTH];
        byte[] machine = new byte[UNAME_BUF_LENGTH];
        int res = invokeNode.callInt(lib, NativeFunctions.call_uname, wrap(sys), wrap(node), wrap(rel), wrap(ver), wrap(machine), UNAME_BUF_LENGTH);
        if (res != 0) {
            throw getErrnoAndThrowPosixException(invokeNode);
        }
        Object[] result = new Object[]{
                        // TODO PyUnicode_DecodeFSDefault
                        cStringToJavaString(sys),
                        cStringToJavaString(node),
                        cStringToJavaString(rel),
                        cStringToJavaString(ver),
                        cStringToJavaString(machine)
        };
        logEnter("uname", "'%s', '%s', '%s', '%s', '%s'", result);
        return result;
    }

    @ExportMessage
    public void unlinkAt(int dirFd, PosixPath pathname, boolean rmdir,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        logEnter("unlinkAt", "%d, '%s', %b", dirFd, pathname, rmdir);
        int result = invokeNode.callInt(lib, NativeFunctions.call_unlinkat, dirFd, pathToCString(pathname), rmdir ? 1 : 0);
        if (result != 0) {
            throw newPosixException(invokeNode, getErrno(invokeNode), pathname.originalObject);
        }
    }

    @ExportMessage
    public void symlinkAt(PosixPath target, int linkpathDirFd, PosixPath linkpath,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        logEnter("symlinkAt", "'%s', %d, '%s'", target, linkpathDirFd, linkpath);
        int result = invokeNode.callInt(lib, NativeFunctions.call_symlinkat, pathToCString(target), linkpathDirFd, pathToCString(linkpath));
        if (result != 0) {
            throw newPosixException(invokeNode, getErrno(invokeNode), target.originalObject, linkpath.originalObject);
        }
    }

    @ExportMessage
    public void mkdirAt(int dirFd, PosixPath pathname, int mode,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        logEnter("mkdirAt", "%d, '%s', 0%o", dirFd, pathname, mode);
        int result = invokeNode.callInt(lib, NativeFunctions.call_mkdirat, dirFd, pathToCString(pathname), mode);
        if (result != 0) {
            throw newPosixException(invokeNode, getErrno(invokeNode), pathname.originalObject);
        }
    }

    @ExportMessage
    public Object getcwd(
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        logEnter("getcwd", "");
        for (int bufLen = 1024;; bufLen += 1024) {
            Buffer buffer = Buffer.allocate(bufLen);
            int n = invokeNode.callInt(lib, NativeFunctions.call_getcwd, wrap(buffer), bufLen);
            if (n == 0) {
                buffer = buffer.withLength(findZero(buffer.data));
                logExit("getcwd", "'%s'", buffer);
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
        logEnter("chdir", "'%s'", path);
        int result = invokeNode.callInt(lib, NativeFunctions.call_chdir, pathToCString(path));
        if (result != 0) {
            throw newPosixException(invokeNode, getErrno(invokeNode), path.originalObject);
        }
    }

    @ExportMessage
    public void fchdir(int fd, Object pathname, boolean handleEintr,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode,
                    @Shared("async") @Cached BranchProfile asyncProfile) throws PosixException {
        logEnter("fchdir", "%d, %s, %b", fd, pathname, handleEintr);
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
        logEnter("isatty", "%d", fd);
        boolean res = invokeNode.callInt(lib, NativeFunctions.call_isatty, fd) != 0;
        logExit("isatty", "%b", res);
        return res;
    }

    @ExportMessage
    public Object opendir(PosixPath path,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        logEnter("opendir", "'%s'", path);
        long ptr = invokeNode.callLong(lib, NativeFunctions.call_opendir, pathToCString(path));
        if (ptr == 0) {
            throw newPosixException(invokeNode, getErrno(invokeNode), path.originalObject);
        }
        Object dirStream = new DirStream(dirStreamRefQueue, ptr, (byte[]) path.value, PosixSupportLibrary.DEFAULT_DIR_FD);
        logExit("opendir", "%s", dirStream);
        return dirStream;
    }

    @ExportMessage
    public Object fdopendir(PosixFd fd,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        logEnter("fdopendir", "%d", fd.fd);
        long ptr = invokeNode.callLong(lib, NativeFunctions.call_fdopendir, fd.fd);
        if (ptr == 0) {
            throw newPosixException(invokeNode, getErrno(invokeNode), fd.originalObject);
        }
        Object dirStream = new DirStream(dirStreamRefQueue, ptr, null, fd.fd);
        logExit("fdopendir", "%s", dirStream);
        return dirStream;
    }

    @ExportMessage
    public void closedir(Object dirStreamObj,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) {
        logEnter("closedir", "%s", dirStreamObj);
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
        logEnter("readdir", "%s", dirStreamObj);
        DirStream dirStream = (DirStream) dirStreamObj;
        Buffer name = Buffer.allocate(DIRENT_NAME_BUF_LENGTH);
        long[] out = new long[2];
        int result;
        synchronized (dirStream.ref.lock) {
            if (dirStream.ref.closed) {
                logExit("readdir", "(closed)");
                return null;
            }
            do {
                result = invokeNode.callInt(lib, NativeFunctions.call_readdir, dirStream.ref.nativePtr, wrap(name), DIRENT_NAME_BUF_LENGTH, context.getEnv().asGuestValue(out));
            } while (result != 0 && name.data[0] == '.' && (name.data[1] == 0 || (name.data[1] == '.' && name.data[2] == 0)));
        }
        if (result != 0) {
            DirEntry dirEntry = new DirEntry(dirStream.path, dirStream.dirFd, name.withLength(findZero(name.data)), out[0], out[1]);
            logExit("readdir", "%s", dirEntry);
            return dirEntry;
        }
        int errno = getErrno(invokeNode);
        if (errno == 0) {
            logExit("readdir", "(no more entries)");
            return null;
        }
        throw newPosixException(invokeNode, errno);
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    public Object dirEntryGetName(Object dirEntryObj) {
        logEnter("dirEntryGetName", "%s", dirEntryObj);
        DirEntry dirEntry = (DirEntry) dirEntryObj;
        logExit("dirEntryGetName", "'%s'", dirEntry.name);
        return dirEntry.name;
    }

    // TODO should we cache the result?
    @ExportMessage
    public static class DirEntryGetPath {
        @Specialization(guards = "dirEntry.dirPath == null")
        static Buffer noPath(@SuppressWarnings("unused") NFIPosixSupport receiver, DirEntry dirEntry) {
            logEnter("dirEntryGetPath", "%s", dirEntry);
            logExit("dirEntryGetPath", "'%s'", dirEntry.name);
            return dirEntry.name;
        }

        @Specialization(guards = {"dirEntry.dirPath != null", "endsWithSlash(dirEntry)"})
        static Buffer withSlash(@SuppressWarnings("unused") NFIPosixSupport receiver, DirEntry dirEntry) {
            logEnter("dirEntryGetPath", "%s", dirEntry);
            int pathLen = dirEntry.dirPath.length;
            int nameLen = (int) dirEntry.name.length;
            byte[] buf = new byte[pathLen + nameLen];
            PythonUtils.arraycopy(dirEntry.dirPath, 0, buf, 0, pathLen);
            PythonUtils.arraycopy(dirEntry.name.data, 0, buf, pathLen, nameLen);
            Buffer path = Buffer.wrap(buf);
            logExit("dirEntryGetPath", "'%s'", path);
            return path;
        }

        @Specialization(guards = {"dirEntry.dirPath != null", "!endsWithSlash(dirEntry)"})
        static Buffer withoutSlash(@SuppressWarnings("unused") NFIPosixSupport receiver, DirEntry dirEntry) {
            logEnter("dirEntryGetPath", "%s", dirEntry);
            int pathLen = dirEntry.dirPath.length;
            int nameLen = (int) dirEntry.name.length;
            byte[] buf = new byte[pathLen + 1 + nameLen];
            PythonUtils.arraycopy(dirEntry.dirPath, 0, buf, 0, pathLen);
            buf[pathLen] = '/';
            PythonUtils.arraycopy(dirEntry.name.data, 0, buf, pathLen + 1, nameLen);
            Buffer path = Buffer.wrap(buf);
            logExit("dirEntryGetPath", "'%s'", path);
            return path;
        }

        protected static boolean endsWithSlash(DirEntry dirEntry) {
            return dirEntry.dirPath[dirEntry.dirPath.length - 1] == '/';
        }
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    public long dirEntryGetInode(Object dirEntry) {
        logEnter("dirEntryGetInode", "%s", dirEntry);
        DirEntry entry = (DirEntry) dirEntry;
        logExit("dirEntryGetInode", "%d", entry.ino);
        return entry.ino;
    }

    @ExportMessage
    public long[] dirEntryStat(Object dirEntryObj, boolean followSymlinks,
                    @CachedLibrary("this") PosixSupportLibrary posixLib,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException, PosixExceptionWithOpaquePath {
        logEnter("dirEntryStat", "%s, %b", dirEntryObj, followSymlinks);
        DirEntry dirEntry = (DirEntry) dirEntryObj;
        // There are two caches - one for `follow_symlinks=True` and the other for
        // 'follow_symlinks=False`.
        // The are different only when the dir entry is a symlink. If it is not, they need to be the
        // same,
        // so we must make sure that fstatat() gets called only once.
        long[] res = dirEntry.getStatCache(followSymlinks);
        if (res == null) {
            if (followSymlinks && !posixLib.dirEntryIsSymlink(this, dirEntry)) {
                // The entry is not a symlink, so both stat caches need to have the
                // same value. Also, the `follow_symlinks=False` cache might already be filled in.
                // (In fact, the call to dirEntryIsSymlink in the condition may fill it.)
                // So we call ourselves recursively to either use or fill that cache first, and the
                // `follow_symlinks=True` cache will be filled below.
                res = dirEntryStat(dirEntry, false, posixLib, invokeNode);
            } else {
                Buffer path = (Buffer) posixLib.dirEntryGetPath(this, dirEntry);
                res = new long[13];
                if (invokeNode.callInt(lib, NativeFunctions.call_fstatat, dirEntry.dirFd, bufferToCString(path), followSymlinks ? 1 : 0, context.getEnv().asGuestValue(res)) != 0) {
                    throw newPosixExceptionWithOpaquePath(invokeNode, getErrno(invokeNode), path);
                }
            }
            dirEntry.setStatCache(followSymlinks, res);
        }
        logExit("dirEntryStat", "%s", res);
        return res;
    }

    @ExportMessage
    abstract static class DirEntryIsSymlink {
        @Specialization(guards = "isTypeKnown(dirEntry)")
        static boolean known(@SuppressWarnings("unused") NFIPosixSupport receiver, DirEntry dirEntry) {
            logEnter("dirEntryIsSymlink", "%s", dirEntry);
            boolean result = dirEntry.type == DT_LNK;
            logExit("dirEntryIsSymlink", "%b", result);
            return result;
        }

        @Specialization(guards = "!isTypeKnown(dirEntry)")
        static boolean unknown(NFIPosixSupport receiver, DirEntry dirEntry,
                        @CachedLibrary("receiver") PosixSupportLibrary posixLib) throws PosixException, PosixExceptionWithOpaquePath {
            logEnter("dirEntryIsSymlink", "%s", dirEntry);
            boolean result = receiver.dirEntryTestMode(dirEntry, false, S_IFLNK, posixLib);
            logExit("dirEntryIsSymlink", "%b", result);
            return result;
        }

        static boolean isTypeKnown(DirEntry dirEntry) {
            return dirEntry.type != DT_UNKNOWN;
        }
    }

    @ExportMessage
    abstract static class DirEntryIsFile {
        @Specialization(guards = "isTypeKnown(dirEntry, followSymlinks)")
        @SuppressWarnings("unused")
        static boolean known(NFIPosixSupport receiver, DirEntry dirEntry, boolean followSymlinks) {
            logEnter("dirEntryIsFile", "%s", dirEntry);
            boolean result = dirEntry.type == DT_REG;
            logExit("dirEntryIsFile", "%b", result);
            return result;
        }

        @Specialization(guards = "!isTypeKnown(dirEntry, followSymlinks)")
        static boolean unknown(NFIPosixSupport receiver, DirEntry dirEntry, boolean followSymlinks,
                        @CachedLibrary("receiver") PosixSupportLibrary posixLib) throws PosixException, PosixExceptionWithOpaquePath {
            logEnter("dirEntryIsFile", "%s", dirEntry);
            boolean result = receiver.dirEntryTestMode(dirEntry, followSymlinks, S_IFREG, posixLib);
            logExit("dirEntryIsFile", "%b", result);
            return result;
        }

        static boolean isTypeKnown(DirEntry dirEntry, boolean followSymlinks) {
            return dirEntry.type != DT_UNKNOWN && (dirEntry.type != DT_LNK || !followSymlinks);
        }
    }

    @ExportMessage
    abstract static class DirEntryIsDir {
        @Specialization(guards = "isTypeKnown(dirEntry, followSymlinks)")
        @SuppressWarnings("unused")
        static boolean known(NFIPosixSupport receiver, DirEntry dirEntry, boolean followSymlinks) {
            logEnter("dirEntryIsDir", "%s", dirEntry);
            boolean result = dirEntry.type == DT_DIR;
            logExit("dirEntryIsDir", "%b", result);
            return result;
        }

        @Specialization(guards = "!isTypeKnown(dirEntry, followSymlinks)")
        static boolean unknown(NFIPosixSupport receiver, DirEntry dirEntry, boolean followSymlinks,
                        @CachedLibrary("receiver") PosixSupportLibrary posixLib) throws PosixException, PosixExceptionWithOpaquePath {
            logEnter("dirEntryIsDir", "%s", dirEntry);
            boolean result = receiver.dirEntryTestMode(dirEntry, followSymlinks, S_IFDIR, posixLib);
            logExit("dirEntryIsDir", "%b", result);
            return result;
        }

        static boolean isTypeKnown(DirEntry dirEntry, boolean followSymlinks) {
            return dirEntry.type != DT_UNKNOWN && (dirEntry.type != DT_LNK || !followSymlinks);
        }
    }

    private boolean dirEntryTestMode(DirEntry dirEntry, boolean followSymlinks, int modeBits, PosixSupportLibrary posixLib) throws PosixException, PosixExceptionWithOpaquePath {
        assert dirEntry.type == DT_UNKNOWN || (dirEntry.type == DT_LNK && followSymlinks);
        long[] stat;
        try {
            stat = posixLib.dirEntryStat(this, dirEntry, followSymlinks);
        } catch (PosixExceptionBase e) {
            if (e.getErrorCode() == OSErrorEnum.ENOENT.getNumber()) {
                return false;
            }
            throw e;
        }
        long mode = stat[0];        // TODO constant sync with native code
        return (mode & S_IFMT) == modeBits;
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

    private static byte[] checkPath(byte[] path) {
        for (byte b : path) {
            if (b == 0) {
                return null;
            }
        }
        return path;
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
        final byte[] path;
        final int dirFd;

        DirStream(ReferenceQueue<DirStream> queue, long nativePtr, byte[] path, int dirFd) {
            ref = new DirStreamRef(this, queue, nativePtr, path == null);
            this.path = path;
            this.dirFd = dirFd;
        }

        @Override
        public String toString() {
            CompilerAsserts.neverPartOfCompilation();
            return "DirStreamState -> " + ref + ", path=" + (path == null ? "null" : new String(path)) + ", dirFd=" + dirFd;
        }
    }

    protected static class DirEntry {
        final byte[] dirPath;
        final int dirFd;
        final Buffer name;
        final long ino;
        final long type;
        long[] lstatCache;
        long[] statCache;

        DirEntry(byte[] dirPath, int dirFd, Buffer name, long ino, long type) {
            this.dirPath = dirPath;
            this.dirFd = dirFd;
            this.name = name;
            this.ino = ino;
            this.type = type;
        }

        long[] getStatCache(boolean followSymlinks) {
            return followSymlinks ? statCache : lstatCache;
        }

        long[] setStatCache(boolean followSymlinks, long[] value) {
            if (followSymlinks) {
                statCache = value;
            } else {
                lstatCache = value;
            }
            return value;
        }

        @Override
        public String toString() {
            return "DirEntry{" +
                            "name='" + new String(name.data, 0, (int) name.length) + "'" +
                            ", ino=" + ino +
                            ", type=" + type +
                            ", dirPath=" + (dirPath == null ? "null" : new String(dirPath)) +
                            ", dirFd=" + dirFd +
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

    private PosixException newPosixExceptionWithOpaquePath(InvokeNativeFunction invokeNode, int errno, Buffer filename) throws PosixExceptionWithOpaquePath {
        String msg = strerror(errno, invokeNode);
        log(Level.FINE, "  -> throw errno=%d, msg=%s, filename1=%s, filename2=%s", errno, msg, filename, null);
        throw new PosixExceptionWithOpaquePath(errno, msg, filename, null);
    }

    private PosixException newPosixException(InvokeNativeFunction invokeNode, int errno, Object filename1, Object filename2) throws PosixException {
        String msg = strerror(errno, invokeNode);
        log(Level.FINE, "  -> throw errno=%d, msg=%s, filename1=%s, filename2=%s", errno, msg, filename1, filename2);
        throw new PosixException(errno, msg, filename1, filename2);
    }

    private Object wrap(byte[] bytes) {
        return context.getEnv().asGuestValue(bytes);
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
        return wrap(nullTerminate((byte[]) path.value));
    }

    private Object bufferToCString(Buffer path) {
        return wrap(nullTerminate(path.data, (int) path.length));
    }

    private static byte[] nullTerminate(byte[] str) {
        return nullTerminate(str, str.length);
    }

    private static byte[] nullTerminate(byte[] str, int length) {
        byte[] terminated = new byte[length + 1];
        PythonUtils.arraycopy(str, 0, terminated, 0, length);
        return terminated;
    }

    private static final TruffleLogger LOGGER = PythonLanguage.getLogger(NFIPosixSupport.class);

    @TruffleBoundary
    private static void log(Level level, String fmt, Object... args) {
        if (LOGGER.isLoggable(level)) {
            fixLogArgs(args);
            LOGGER.log(level, String.format(fmt, args));
        }
    }

    @TruffleBoundary
    private static void logEnter(String msg, String argFmt, Object... args) {
        if (LOGGER.isLoggable(Level.FINE)) {
            fixLogArgs(args);
            LOGGER.log(Level.FINE, msg + '(' + String.format(argFmt, args) + ')');
            if (LOGGER.isLoggable(Level.FINEST)) {
                logStackTrace(Level.FINEST, 0, Integer.MAX_VALUE);
            } else if (LOGGER.isLoggable(Level.FINER)) {
                logStackTrace(Level.FINER, 0, 3);
            }
        }
    }

    @TruffleBoundary
    private static void logExit(@SuppressWarnings("unused") String msg, String argFmt, Object... args) {
        if (LOGGER.isLoggable(Level.FINE)) {
            fixLogArgs(args);
            LOGGER.log(Level.FINE, "  -> return " + String.format(argFmt, args));
        }
    }

    private static void fixLogArgs(Object[] args) {
        for (int i = 0; i < args.length; ++i) {
            if (args[i] instanceof PosixPath) {
                args[i] = new String((byte[]) ((PosixPath) args[i]).value);
            }
            if (args[i] instanceof long[]) {
                args[i] = Arrays.toString((long[]) args[i]);
            }
            if (args[i] instanceof Buffer) {
                Buffer b = (Buffer) args[i];
                args[i] = PythonUtils.newString(b.data, 0, (int) b.length);
            }
        }
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
