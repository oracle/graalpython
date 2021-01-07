/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.api.CompilerDirectives.SLOWPATH_PROBABILITY;
import static com.oracle.truffle.api.CompilerDirectives.injectBranchProbability;

import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.logging.Level;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.modules.GraalPythonModuleBuiltins;
import com.oracle.graal.python.builtins.objects.exception.OSErrorEnum;
import com.oracle.graal.python.runtime.PosixSupportLibrary.Buffer;
import com.oracle.graal.python.runtime.PosixSupportLibrary.PosixException;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.llvm.api.Toolchain;

/**
 * Implementation that invokes the native POSIX functions directly using NFI. This requires either
 * that the native access is allowed or to configure managed LLVM backend for NFI.
 */
@ExportLibrary(PosixSupportLibrary.class)
public final class NFIPosixSupport extends PosixSupport {
    private static final String SUPPORTING_NATIVE_LIB_NAME = "libposix";

    private static final int UNAME_BUF_LENGTH = 256;
    private static final int DIRENT_NAME_BUF_LENGTH = 256;
    private static final int PATH_MAX = 4096;

    private static final int MAX_READ = Integer.MAX_VALUE / 2;

    private static final TruffleLogger LOGGER = PythonLanguage.getLogger(NFIPosixSupport.class);

    private enum PosixNativeFunction {
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
        call_fchmodat("(sint32, [sint8], sint32, sint32):sint32"),
        call_fchmod("(sint32, sint32):sint32"),
        call_readlinkat("(sint32, [sint8], [sint8], uint64):sint64"),
        get_inheritable("(sint32):sint32"),
        set_inheritable("(sint32, sint32):sint32"),
        get_blocking("(sint32):sint32"),
        set_blocking("(sint32, sint32):sint32"),
        get_terminal_size("(sint32, [sint32]):sint32");

        private final String signature;

        PosixNativeFunction(String signature) {
            this.signature = signature;
        }
    }

    protected static final class InvokeNativeFunction extends Node {
        private static final InvokeNativeFunction UNCACHED = new InvokeNativeFunction(InteropLibrary.getUncached(), InteropLibrary.getUncached());

        @Child private InteropLibrary functionInterop;
        @Child private InteropLibrary resultInterop;

        public InvokeNativeFunction(InteropLibrary functionInterop, InteropLibrary resultInterop) {
            this.functionInterop = functionInterop;
            this.resultInterop = resultInterop;
        }

        public static InvokeNativeFunction create() {
            return new InvokeNativeFunction(InteropLibrary.getFactory().createDispatched(2), null);
        }

        public static InvokeNativeFunction getUncached() {
            return UNCACHED;
        }

        public Object call(NFIPosixSupport posix, PosixNativeFunction function, Object... args) {
            if (injectBranchProbability(SLOWPATH_PROBABILITY, posix.nfiLibrary == null)) {
                loadLibrary(posix);
            }
            if (injectBranchProbability(SLOWPATH_PROBABILITY, posix.cachedFunctions.get(function.ordinal()) == null)) {
                loadFunction(posix, posix.nfiLibrary, function);
            }
            Object funObject = posix.cachedFunctions.get(function.ordinal());
            try {
                return functionInterop.execute(funObject, args);
            } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }

        public long callLong(NFIPosixSupport posix, PosixNativeFunction function, Object... args) {
            try {
                return ensureResultInterop().asLong(call(posix, function, args));
            } catch (UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }

        public int callInt(NFIPosixSupport posix, PosixNativeFunction function, Object... args) {
            try {
                return ensureResultInterop().asInt(call(posix, function, args));
            } catch (UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }

        // Temporary - will be replaced with something else when we move this to Truffle
        private static String getLibPath(PythonContext context) {
            CompilerAsserts.neverPartOfCompilation();

            String os = PythonUtils.getPythonOSName();
            String multiArch = PythonUtils.getPythonArch() + "-" + os;
            String cacheTag = "graalpython-38";
            Env env = context.getEnv();
            LanguageInfo llvmInfo = env.getInternalLanguages().get(GraalPythonModuleBuiltins.LLVM_LANGUAGE);
            Toolchain toolchain = env.lookup(llvmInfo, Toolchain.class);
            String toolchainId = toolchain.getIdentifier();

            // only use '.dylib' if we are on 'Darwin-native'
            String soExt;
            if ("darwin".equals(os) && "native".equals(toolchainId)) {
                soExt = ".dylib";
            } else {
                soExt = ".so";
            }

            String libPythonName = NFIPosixSupport.SUPPORTING_NATIVE_LIB_NAME + "." + cacheTag + "-" + toolchainId + "-" + multiArch + soExt;
            TruffleFile homePath = context.getEnv().getInternalTruffleFile(context.getCAPIHome());
            TruffleFile file = homePath.resolve(libPythonName);
            return file.getPath();
        }

        @TruffleBoundary
        private static void loadLibrary(NFIPosixSupport posix) {
            String path = getLibPath(posix.context);
            String withClause = posix.nfiBackend.equals("native") ? "" : "with " + posix.nfiBackend;
            String src = String.format("%sload (RTLD_LOCAL) \"%s\"", withClause, path);
            Source loadSrc = Source.newBuilder("nfi", src, "load:" + SUPPORTING_NATIVE_LIB_NAME).internal(true).build();

            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine(String.format("Loading native library: %s", src));
            }
            try {
                posix.nfiLibrary = posix.context.getEnv().parseInternal(loadSrc).call();
            } catch (Throwable e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }

        @TruffleBoundary
        private static void loadFunction(NFIPosixSupport posix, Object library, PosixNativeFunction function) {
            Object unbound;
            try {
                InteropLibrary interop = InteropLibrary.getUncached();
                unbound = interop.readMember(library, function.name());
                posix.cachedFunctions.set(function.ordinal(), interop.invokeMember(unbound, "bind", function.signature));
            } catch (UnsupportedMessageException | UnknownIdentifierException | ArityException | UnsupportedTypeException e) {
                throw CompilerDirectives.shouldNotReachHere(function.name(), e);
            }
        }

        private InteropLibrary ensureResultInterop() {
            if (resultInterop == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                resultInterop = insert(InteropLibrary.getFactory().createDispatched(2));
            }
            return resultInterop;
        }
    }

    private final PythonContext context;
    private final String nfiBackend;
    private volatile Object nfiLibrary;
    private final AtomicReferenceArray<Object> cachedFunctions;

    private NFIPosixSupport(PythonContext context, String nfiBackend) {
        this.context = context;
        this.nfiBackend = nfiBackend;
        this.cachedFunctions = new AtomicReferenceArray<>(PosixNativeFunction.values().length);
    }

    public static NFIPosixSupport createNative(PythonContext context) {
        return new NFIPosixSupport(context, "native");
    }

    public static NFIPosixSupport createLLVM(PythonContext context) {
        return new NFIPosixSupport(context, "llvm");
    }

    @ExportMessage
    public String getBackend() {
        return nfiBackend;
    }

    @ExportMessage
    public String strerror(int errorCode,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) {
        // From man pages: The GNU C Library uses a buffer of 1024 characters for strerror().
        // This buffer size therefore should be sufficient to avoid an ERANGE error when calling
        // strerror_r().
        byte[] buf = new byte[1024];
        invokeNode.call(this, PosixNativeFunction.call_strerror, errorCode, wrap(buf), buf.length);
        // TODO PyUnicode_DecodeLocale
        return cStringToJavaString(buf);
    }

    @ExportMessage
    public long getpid(@Shared("invoke") @Cached InvokeNativeFunction invokeNode) {
        return invokeNode.callLong(this, PosixNativeFunction.call_getpid);
    }

    @ExportMessage
    public int umask(int mask,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        int result = invokeNode.callInt(this, PosixNativeFunction.call_umask, mask);
        if (result < 0) {
            throw getErrnoAndThrowPosixException(invokeNode);
        }
        return result;
    }

    @ExportMessage
    public int openat(int dirFd, Object pathname, int flags, int mode,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        int fd = invokeNode.callInt(this, PosixNativeFunction.call_openat, dirFd, pathToCString(pathname), flags, mode);
        if (fd < 0) {
            throw getErrnoAndThrowPosixException(invokeNode);
        }
        return fd;
    }

    @ExportMessage
    public void close(int fd,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        if (invokeNode.callInt(this, PosixNativeFunction.call_close, fd) < 0) {
            throw getErrnoAndThrowPosixException(invokeNode);
        }
    }

    @ExportMessage
    public Buffer read(int fd, long length,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        long count = Math.min(length, MAX_READ);
        Buffer buffer = Buffer.allocate(count);
        setErrno(invokeNode, 0);        // TODO CPython does this, but do we need it?
        long n = invokeNode.callLong(this, PosixNativeFunction.call_read, fd, wrap(buffer), count);
        if (n < 0) {
            throw getErrnoAndThrowPosixException(invokeNode);
        }
        return buffer.withLength(n);
    }

    @ExportMessage
    public long write(int fd, Buffer data,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        setErrno(invokeNode, 0);        // TODO CPython does this, but do we need it?
        long n = invokeNode.callLong(this, PosixNativeFunction.call_write, fd, wrap(data), data.length);
        if (n < 0) {
            throw getErrnoAndThrowPosixException(invokeNode);
        }
        return n;
    }

    @ExportMessage
    public int dup(int fd,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        int newFd = invokeNode.callInt(this, PosixNativeFunction.call_dup, fd);
        if (newFd < 0) {
            throw getErrnoAndThrowPosixException(invokeNode);
        }
        return newFd;
    }

    @ExportMessage
    public int dup2(int fd, int fd2, boolean inheritable,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        int newFd = invokeNode.callInt(this, PosixNativeFunction.call_dup2, fd, fd2, inheritable ? 1 : 0);
        if (newFd < 0) {
            throw getErrnoAndThrowPosixException(invokeNode);
        }
        return newFd;
    }

    @ExportMessage
    public boolean getInheritable(int fd,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        int result = invokeNode.callInt(this, PosixNativeFunction.get_inheritable, fd);
        if (result < 0) {
            throw getErrnoAndThrowPosixException(invokeNode);
        }
        return result != 0;
    }

    @ExportMessage
    public void setInheritable(int fd, boolean inheritable,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        if (invokeNode.callInt(this, PosixNativeFunction.set_inheritable, fd, inheritable ? 1 : 0) < 0) {
            throw getErrnoAndThrowPosixException(invokeNode);
        }
    }

    @ExportMessage
    public int[] pipe(
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        int[] fds = new int[2];
        if (invokeNode.callInt(this, PosixNativeFunction.call_pipe2, context.getEnv().asGuestValue(fds)) != 0) {
            throw getErrnoAndThrowPosixException(invokeNode);
        }
        return fds;
    }

    @ExportMessage
    public long lseek(int fd, long offset, int how,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        long res = invokeNode.callLong(this, PosixNativeFunction.call_lseek, fd, offset, how);
        if (res < 0) {
            throw getErrnoAndThrowPosixException(invokeNode);
        }
        return res;
    }

    @ExportMessage
    public void ftruncate(int fd, long length,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        int res = invokeNode.callInt(this, PosixNativeFunction.call_ftruncate, fd, length);
        if (res != 0) {
            throw getErrnoAndThrowPosixException(invokeNode);
        }
    }

    @ExportMessage
    public void fsync(int fd,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        int res = invokeNode.callInt(this, PosixNativeFunction.call_fsync, fd);
        if (res != 0) {
            throw getErrnoAndThrowPosixException(invokeNode);
        }
    }

    @ExportMessage
    public boolean getBlocking(int fd,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        int result = invokeNode.callInt(this, PosixNativeFunction.get_blocking, fd);
        if (result < 0) {
            throw getErrnoAndThrowPosixException(invokeNode);
        }
        return result != 0;
    }

    @ExportMessage
    public void setBlocking(int fd, boolean blocking,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        if (invokeNode.callInt(this, PosixNativeFunction.set_blocking, fd, blocking ? 1 : 0) < 0) {
            throw getErrnoAndThrowPosixException(invokeNode);
        }
    }

    @ExportMessage
    public int[] getTerminalSize(int fd,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        int[] size = new int[2];
        if (invokeNode.callInt(this, PosixNativeFunction.get_terminal_size, fd, context.getEnv().asGuestValue(size)) != 0) {
            throw getErrnoAndThrowPosixException(invokeNode);
        }
        return size;
    }

    @ExportMessage
    public long[] fstatat(int dirFd, Object pathname, boolean followSymlinks,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        long[] out = new long[13];
        int res = invokeNode.callInt(this, PosixNativeFunction.call_fstatat, dirFd, pathToCString(pathname), followSymlinks ? 1 : 0, wrap(out));
        if (res != 0) {
            throw newPosixException(invokeNode, getErrno(invokeNode));
        }
        return out;
    }

    @ExportMessage
    public long[] fstat(int fd,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        long[] out = new long[13];
        int res = invokeNode.callInt(this, PosixNativeFunction.call_fstat, fd, wrap(out));
        if (res != 0) {
            throw newPosixException(invokeNode, getErrno(invokeNode));
        }
        return out;
    }

    @ExportMessage
    public Object[] uname(
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        byte[] sys = new byte[UNAME_BUF_LENGTH];
        byte[] node = new byte[UNAME_BUF_LENGTH];
        byte[] rel = new byte[UNAME_BUF_LENGTH];
        byte[] ver = new byte[UNAME_BUF_LENGTH];
        byte[] machine = new byte[UNAME_BUF_LENGTH];
        int res = invokeNode.callInt(this, PosixNativeFunction.call_uname, wrap(sys), wrap(node), wrap(rel), wrap(ver), wrap(machine), UNAME_BUF_LENGTH);
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
    public void unlinkat(int dirFd, Object pathname, boolean rmdir,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        int result = invokeNode.callInt(this, PosixNativeFunction.call_unlinkat, dirFd, pathToCString(pathname), rmdir ? 1 : 0);
        if (result != 0) {
            throw newPosixException(invokeNode, getErrno(invokeNode));
        }
    }

    @ExportMessage
    public void symlinkat(Object target, int linkpathDirFd, Object linkpath,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        int result = invokeNode.callInt(this, PosixNativeFunction.call_symlinkat, pathToCString(target), linkpathDirFd, pathToCString(linkpath));
        if (result != 0) {
            throw newPosixException(invokeNode, getErrno(invokeNode));
        }
    }

    @ExportMessage
    public void mkdirat(int dirFd, Object pathname, int mode,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        int result = invokeNode.callInt(this, PosixNativeFunction.call_mkdirat, dirFd, pathToCString(pathname), mode);
        if (result != 0) {
            throw newPosixException(invokeNode, getErrno(invokeNode));
        }
    }

    @ExportMessage
    public Object getcwd(
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        for (int bufLen = 1024;; bufLen += 1024) {
            Buffer buffer = Buffer.allocate(bufLen);
            int n = invokeNode.callInt(this, PosixNativeFunction.call_getcwd, wrap(buffer), bufLen);
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
    public void chdir(Object path,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        int result = invokeNode.callInt(this, PosixNativeFunction.call_chdir, pathToCString(path));
        if (result != 0) {
            throw newPosixException(invokeNode, getErrno(invokeNode));
        }
    }

    @ExportMessage
    public void fchdir(int fd,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        int result = invokeNode.callInt(this, PosixNativeFunction.call_fchdir, fd);
        if (result != 0) {
            throw newPosixException(invokeNode, getErrno(invokeNode));
        }
    }

    @ExportMessage
    public boolean isatty(int fd,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) {
        return invokeNode.callInt(this, PosixNativeFunction.call_isatty, fd) != 0;
    }

    @ExportMessage
    public Object opendir(Object path,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        long ptr = invokeNode.callLong(this, PosixNativeFunction.call_opendir, pathToCString(path));
        if (ptr == 0) {
            throw newPosixException(invokeNode, getErrno(invokeNode));
        }
        return new DirStream(ptr, false);
    }

    @ExportMessage
    public Object fdopendir(int fd,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        long ptr = invokeNode.callLong(this, PosixNativeFunction.call_fdopendir, fd);
        if (ptr == 0) {
            throw newPosixException(invokeNode, getErrno(invokeNode));
        }
        return new DirStream(ptr, true);
    }

    @ExportMessage
    public void closedir(Object dirStreamObj,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) {
        DirStream dirStream = (DirStream) dirStreamObj;
        synchronized (dirStream.lock) {
            if (!dirStream.closed) {
                dirStream.closed = true;
                int res = invokeNode.callInt(this, PosixNativeFunction.call_closedir, dirStream.nativePtr, dirStream.needsRewind ? 1 : 0);
                if (res != 0 && LOGGER.isLoggable(Level.INFO)) {
                    log(Level.INFO, "Error occured during closedir, errno=%d", getErrno(invokeNode));
                }
            }
        }
    }

    @ExportMessage
    public Object readdir(Object dirStreamObj,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        DirStream dirStream = (DirStream) dirStreamObj;
        Buffer name = Buffer.allocate(DIRENT_NAME_BUF_LENGTH);
        long[] out = new long[2];
        int result;
        synchronized (dirStream.lock) {
            if (dirStream.closed) {
                return null;
            }
            do {
                result = invokeNode.callInt(this, PosixNativeFunction.call_readdir, dirStream.nativePtr, wrap(name), DIRENT_NAME_BUF_LENGTH, wrap(out));
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
        static Buffer withSlash(@SuppressWarnings("unused") NFIPosixSupport receiver, DirEntry dirEntry, Object scandirPath) {
            Buffer scandirPathBuffer = (Buffer) scandirPath;
            int pathLen = scandirPathBuffer.data.length;
            int nameLen = (int) dirEntry.name.length;
            byte[] buf = new byte[pathLen + nameLen];
            PythonUtils.arraycopy(scandirPathBuffer.data, 0, buf, 0, pathLen);
            PythonUtils.arraycopy(dirEntry.name.data, 0, buf, pathLen, nameLen);
            return Buffer.wrap(buf);
        }

        @Specialization(guards = "!endsWithSlash(scandirPath)")
        static Buffer withoutSlash(@SuppressWarnings("unused") NFIPosixSupport receiver, DirEntry dirEntry, Object scandirPath) {
            Buffer scandirPathBuffer = (Buffer) scandirPath;
            int pathLen = scandirPathBuffer.data.length;
            int nameLen = (int) dirEntry.name.length;
            byte[] buf = new byte[pathLen + 1 + nameLen];
            PythonUtils.arraycopy(scandirPathBuffer.data, 0, buf, 0, pathLen);
            buf[pathLen] = PosixSupportLibrary.POSIX_FILENAME_SEPARATOR;
            PythonUtils.arraycopy(dirEntry.name.data, 0, buf, pathLen + 1, nameLen);
            return Buffer.wrap(buf);
        }

        protected static boolean endsWithSlash(Object path) {
            Buffer b = (Buffer) path;
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
    public void utimensat(int dirFd, Object pathname, long[] timespec, boolean followSymlinks,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        assert timespec == null || timespec.length == 4;
        int ret = invokeNode.callInt(this, PosixNativeFunction.call_utimensat, dirFd, pathToCString(pathname), wrap(timespec), followSymlinks ? 1 : 0);
        if (ret != 0) {
            throw newPosixException(invokeNode, getErrno(invokeNode));
        }
    }

    @ExportMessage
    public void futimens(int fd, long[] timespec,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        assert timespec == null || timespec.length == 4;
        int ret = invokeNode.callInt(this, PosixNativeFunction.call_futimens, fd, wrap(timespec));
        if (ret != 0) {
            throw newPosixException(invokeNode, getErrno(invokeNode));
        }
    }

    @ExportMessage
    public void renameat(int oldDirFd, Object oldPath, int newDirFd, Object newPath,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        int ret = invokeNode.callInt(this, PosixNativeFunction.call_renameat, oldDirFd, pathToCString(oldPath), newDirFd, pathToCString(newPath));
        if (ret != 0) {
            throw newPosixException(invokeNode, getErrno(invokeNode));
        }
    }

    @ExportMessage
    public boolean faccessat(int dirFd, Object path, int mode, boolean effectiveIds, boolean followSymlinks,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) {
        int ret = invokeNode.callInt(this, PosixNativeFunction.call_faccessat, dirFd, pathToCString(path), mode, effectiveIds ? 1 : 0, followSymlinks ? 1 : 0);
        if (ret != 0 && LOGGER.isLoggable(Level.FINE)) {
            log(Level.FINE, "faccessat return value: %d, errno: %d", ret, getErrno(invokeNode));
        }
        return ret == 0;
    }

    @ExportMessage
    public void fchmodat(int dirFd, Object path, int mode, boolean followSymlinks,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        int ret = invokeNode.callInt(this, PosixNativeFunction.call_fchmodat, dirFd, pathToCString(path), mode, followSymlinks ? 1 : 0);
        if (ret != 0) {
            throw newPosixException(invokeNode, getErrno(invokeNode));
        }
    }

    @ExportMessage
    public void fchmod(int fd, int mode,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        int ret = invokeNode.callInt(this, PosixNativeFunction.call_fchmod, fd, mode);
        if (ret != 0) {
            throw newPosixException(invokeNode, getErrno(invokeNode));
        }
    }

    @ExportMessage
    public Object readlinkat(int dirFd, Object path,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        Buffer buffer = Buffer.allocate(PATH_MAX);
        long n = invokeNode.callLong(this, PosixNativeFunction.call_readlinkat, dirFd, pathToCString(path), wrap(buffer), PATH_MAX);
        if (n < 0) {
            throw newPosixException(invokeNode, getErrno(invokeNode));
        }
        return buffer.withLength(n);
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
    public Buffer getPathAsBytes(Object path) {
        return (Buffer) path;
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

    private static class DirStream {
        final long nativePtr;
        final boolean needsRewind;
        final Object lock;
        boolean closed;

        DirStream(long nativePtr, boolean needsRewind) {
            this.nativePtr = nativePtr;
            this.needsRewind = needsRewind;
            this.lock = new Object();
        }

        @Override
        public String toString() {
            return "DirStream{" +
                            "nativePtr=" + nativePtr +
                            ", needsRewind=" + needsRewind +
                            ", closed=" + closed +
                            '}';
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
        return invokeNode.callInt(this, PosixNativeFunction.get_errno);
    }

    private void setErrno(InvokeNativeFunction invokeNode, int errno) {
        invokeNode.call(this, PosixNativeFunction.set_errno, errno);
    }

    private PosixException getErrnoAndThrowPosixException(InvokeNativeFunction invokeNode) throws PosixException {
        throw newPosixException(invokeNode, getErrno(invokeNode));
    }

    private PosixException newPosixException(InvokeNativeFunction invokeNode, int errno) throws PosixException {
        throw new PosixException(errno, strerror(errno, invokeNode));
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

    private Object pathToCString(Object path) {
        return bufferToCString((Buffer) path);
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
