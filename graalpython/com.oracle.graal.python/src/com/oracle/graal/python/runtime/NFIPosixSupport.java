/*
 * Copyright (c) 2020, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.nodes.StringLiterals.J_DEFAULT;
import static com.oracle.graal.python.nodes.StringLiterals.J_NATIVE;
import static com.oracle.graal.python.nodes.StringLiterals.J_NFI_LANGUAGE;
import static com.oracle.graal.python.nodes.StringLiterals.T_NATIVE;
import static com.oracle.graal.python.runtime.NFIPosixConstants.OFFSETOF_STRUCT_IN6_ADDR_S6_ADDR;
import static com.oracle.graal.python.runtime.NFIPosixConstants.OFFSETOF_STRUCT_IN_ADDR_S_ADDR;
import static com.oracle.graal.python.runtime.NFIPosixConstants.OFFSETOF_STRUCT_SOCKADDR_IN6_SIN6_ADDR;
import static com.oracle.graal.python.runtime.NFIPosixConstants.OFFSETOF_STRUCT_SOCKADDR_IN6_SIN6_FLOWINFO;
import static com.oracle.graal.python.runtime.NFIPosixConstants.OFFSETOF_STRUCT_SOCKADDR_IN6_SIN6_PORT;
import static com.oracle.graal.python.runtime.NFIPosixConstants.OFFSETOF_STRUCT_SOCKADDR_IN6_SIN6_SCOPE_ID;
import static com.oracle.graal.python.runtime.NFIPosixConstants.OFFSETOF_STRUCT_SOCKADDR_IN_SIN_ADDR;
import static com.oracle.graal.python.runtime.NFIPosixConstants.OFFSETOF_STRUCT_SOCKADDR_IN_SIN_PORT;
import static com.oracle.graal.python.runtime.NFIPosixConstants.OFFSETOF_STRUCT_SOCKADDR_SA_FAMILY;
import static com.oracle.graal.python.runtime.NFIPosixConstants.OFFSETOF_STRUCT_SOCKADDR_UN_SUN_PATH;
import static com.oracle.graal.python.runtime.NFIPosixConstants.SIZEOF_STRUCT_SOCKADDR_IN;
import static com.oracle.graal.python.runtime.NFIPosixConstants.SIZEOF_STRUCT_SOCKADDR_IN6;
import static com.oracle.graal.python.runtime.NFIPosixConstants.SIZEOF_STRUCT_SOCKADDR_SA_FAMILY;
import static com.oracle.graal.python.runtime.NFIPosixConstants.SIZEOF_STRUCT_SOCKADDR_STORAGE;
import static com.oracle.graal.python.runtime.NFIPosixConstants.SIZEOF_STRUCT_SOCKADDR_UN_SUN_PATH;
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
import static com.oracle.graal.python.util.PythonUtils.ARRAY_ACCESSOR;
import static com.oracle.graal.python.util.PythonUtils.ARRAY_ACCESSOR_BE;
import static com.oracle.graal.python.util.PythonUtils.EMPTY_LONG_ARRAY;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.truffle.api.CompilerDirectives.SLOWPATH_PROBABILITY;
import static com.oracle.truffle.api.CompilerDirectives.injectBranchProbability;
import static com.oracle.truffle.api.CompilerDirectives.shouldNotReachHere;
import static com.oracle.truffle.api.strings.TruffleString.Encoding.UTF_8;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.logging.Level;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonOS;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.exception.OSErrorEnum;
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
import com.oracle.graal.python.runtime.PosixSupportLibrary.PosixException;
import com.oracle.graal.python.runtime.PosixSupportLibrary.PwdResult;
import com.oracle.graal.python.runtime.PosixSupportLibrary.RecvfromResult;
import com.oracle.graal.python.runtime.PosixSupportLibrary.RusageResult;
import com.oracle.graal.python.runtime.PosixSupportLibrary.SelectResult;
import com.oracle.graal.python.runtime.PosixSupportLibrary.Timeval;
import com.oracle.graal.python.runtime.PosixSupportLibrary.UniversalSockAddr;
import com.oracle.graal.python.runtime.PosixSupportLibrary.UniversalSockAddrLibrary;
import com.oracle.graal.python.runtime.PosixSupportLibrary.UnixSockAddr;
import com.oracle.graal.python.util.FunctionWithSignature;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.TruffleSafepoint;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.nfi.api.SignatureLibrary;

import sun.misc.Unsafe;

/**
 * Implementation that invokes the native POSIX functions directly using NFI. This requires either
 * that the native access is allowed or to configure managed LLVM backend for NFI.
 */
@ExportLibrary(PosixSupportLibrary.class)
public final class NFIPosixSupport extends PosixSupport {
    private static final String SUPPORTING_NATIVE_LIB_NAME = "posix";

    private static final int UNAME_BUF_LENGTH = 256;
    private static final int DIRENT_NAME_BUF_LENGTH = 256;
    private static final int PWD_OUTPUT_LEN = 5;
    private static final int PWD_BUFFER_MAX_SIZE = Integer.MAX_VALUE >> 2;

    private static final int MAX_READ = Integer.MAX_VALUE / 2;

    private static final TruffleLogger LOGGER = PythonLanguage.getLogger(NFIPosixSupport.class);

    private static final Unsafe UNSAFE = PythonUtils.initUnsafe();

    private static final Object CRYPT_LOCK = new Object();

    private enum PosixNativeFunction {
        init_constants("([sint64], sint32):sint32"),

        get_errno("():sint32"),
        set_errno("(sint32):void"),
        call_mmap("(sint64, sint32, sint32, sint32, sint64):sint64"),
        call_munmap("(sint64, sint64):sint32"),
        call_msync("(sint64, sint64, sint64):void"),
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
        call_select("(sint32, [sint32], sint32, [sint32], sint32, [sint32], sint32, sint64, sint64, [sint8]):sint32"),
        call_poll("(sint32, sint32, sint64, sint64):sint32"),
        call_lseek("(sint32, sint64, sint32):sint64"),
        call_ftruncate("(sint32, sint64):sint32"),
        call_truncate("([sint8], sint64):sint32"),
        call_fsync("(sint32):sint32"),
        call_flock("(sint32, sint32):sint32"),
        call_fcntl_lock("(sint32, sint32, sint32, sint32, sint64, sint64):sint32"),
        call_fstatat("(sint32, [sint8], sint32, [sint64]):sint32"),
        call_fstat("(sint32, [sint64]):sint32"),
        call_statvfs("([sint8], [sint64]):sint32"),
        call_fstatvfs("(sint32, [sint64]):sint32"),
        call_uname("([sint8], [sint8], [sint8], [sint8], [sint8], sint32):sint32"),
        call_unlinkat("(sint32, [sint8], sint32):sint32"),
        call_linkat("(sint32, [sint8], sint32, [sint8], sint32):sint32"),
        call_symlinkat("([sint8], sint32, [sint8]):sint32"),
        call_mkdirat("(sint32, [sint8], sint32):sint32"),
        call_getcwd("([sint8], uint64):sint32"),
        call_chdir("([sint8]):sint32"),
        call_fchdir("(sint32):sint32"),
        call_isatty("(sint32):sint32"),
        call_opendir("([sint8]):sint64"),
        call_fdopendir("(sint32):sint64"),
        call_closedir("(sint64):sint32"),
        call_readdir("(sint64, [sint8], uint64, [sint64]):sint32"),
        call_rewinddir("(sint64):void"),
        call_utimensat("(sint32, [sint8], [sint64], sint32):sint32"),
        call_futimens("(sint32, [sint64]):sint32"),
        call_futimes("(sint32, [sint64]):sint32"),
        call_lutimes("([sint8], [sint64]):sint32"),
        call_utimes("([sint8], [sint64]):sint32"),
        call_renameat("(sint32, [sint8], sint32, [sint8]):sint32"),
        call_faccessat("(sint32, [sint8], sint32, sint32, sint32):sint32"),
        call_fchmodat("(sint32, [sint8], sint32, sint32):sint32"),
        call_fchmod("(sint32, sint32):sint32"),
        call_fchownat("(sint32, [sint8], sint64, sint64, sint32):sint32"),
        call_fchown("(sint32, sint64, sint64):sint32"),
        call_readlinkat("(sint32, [sint8], [sint8], uint64):sint64"),
        get_inheritable("(sint32):sint32"),
        set_inheritable("(sint32, sint32):sint32"),
        get_blocking("(sint32):sint32"),
        set_blocking("(sint32, sint32):sint32"),
        get_terminal_size("(sint32, [sint32]):sint32"),
        call_kill("(sint64, sint32):sint32"),
        call_killpg("(sint64, sint32):sint32"),
        call_abort("():void"),
        call_waitpid("(sint64, [sint32], sint32):sint64"),
        call_wcoredump("(sint32):sint32"),
        call_wifcontinued("(sint32):sint32"),
        call_wifstopped("(sint32):sint32"),
        call_wifsignaled("(sint32):sint32"),
        call_wifexited("(sint32):sint32"),
        call_wexitstatus("(sint32):sint32"),
        call_wtermsig("(sint32):sint32"),
        call_wstopsig("(sint32):sint32"),
        call_getuid("():sint64"),
        call_geteuid("():sint64"),
        call_getgid("():sint64"),
        call_getegid("():sint64"),
        call_getppid("():sint64"),
        call_getpgid("(sint64):sint64"),
        call_setpgid("(sint64,sint64):sint32"),
        call_getpgrp("():sint64"),
        call_getsid("(sint64):sint64"),
        call_setsid("():sint64"),
        call_getgroups("(sint64, [sint64]):sint32"),
        call_getrusage("(sint32, [sint64]):sint32"),
        call_openpty("([sint32]):sint32"),
        call_ctermid("([sint8]):sint32"),
        call_setenv("([sint8], [sint8], sint32):sint32"),
        call_unsetenv("([sint8]):sint32"),
        fork_exec("([sint8], [sint64], sint32, sint32, sint32, sint32, sint32, sint32, sint32, sint32, sint32, sint32, sint32, sint32, sint32, sint32, sint32, [sint32], sint64):sint32"),
        call_execv("([sint8], [sint64], sint32):void"),
        call_system("([sint8]):sint32"),

        call_getpwuid_r("(uint64,[sint8],sint32,[uint64]):sint32"),
        call_getpwname_r("([sint8],[sint8],sint32,[uint64]):sint32"),
        call_setpwent("():void"),
        call_endpwent("():void"),
        call_getpwent("([sint64]):pointer"),
        get_getpwent_data("(pointer,[sint8],sint32,[uint64]):sint32"),
        get_sysconf_getpw_r_size_max("():sint64"),

        call_socket("(sint32, sint32, sint32):sint32"),
        call_accept("(sint32, [sint8], [sint32]):sint32"),
        call_bind("(sint32, [sint8], sint32):sint32"),
        call_connect("(sint32, [sint8], sint32):sint32"),
        call_listen("(sint32, sint32):sint32"),
        call_getpeername("(sint32, [sint8], [sint32]):sint32"),
        call_getsockname("(sint32, [sint8], [sint32]):sint32"),
        call_send("(sint32, [sint8], sint32, sint32, sint32):sint32"),
        call_sendto("(sint32, [sint8], sint32, sint32, sint32, [sint8], sint32):sint32"),
        call_recv("(sint32, [sint8], sint32, sint32, sint32):sint32"),
        call_recvfrom("(sint32, [sint8], sint32, sint32, sint32, [sint8], [sint32]):sint32"),
        call_shutdown("(sint32, sint32): sint32"),
        call_getsockopt("(sint32, sint32, sint32, [sint8], [sint32]):sint32"),
        call_setsockopt("(sint32, sint32, sint32, [sint8], sint32):sint32"),

        call_inet_addr("([sint8]):sint32"),
        call_inet_aton("([sint8]):sint64"),
        call_inet_ntoa("(sint32, [sint8]):sint32"),
        call_inet_pton("(sint32, [sint8], [sint8]):sint32"),
        call_inet_ntop("(sint32, [sint8], [sint8], sint32):sint32"),
        call_gethostname("([sint8], sint64):sint32"),

        call_getnameinfo("([sint8], sint32, [sint8], sint32, [sint8], sint32, sint32):sint32"),
        call_getaddrinfo("([sint8], [sint8], sint32, sint32, sint32, sint32, [sint64]):sint32"),
        call_freeaddrinfo("(sint64):void"),
        call_gai_strerror("(sint32, [sint8], sint32):void"),
        get_addrinfo_members("(sint64, [sint32], [sint64], [sint8]):sint32"),

        call_sem_open("([sint8], sint32, sint32, sint32):pointer"),
        call_sem_close("(pointer):sint32"),
        call_sem_unlink("([sint8]):sint32"),
        call_sem_getvalue("(pointer, [sint32]):sint32"),
        call_sem_post("(pointer):sint32"),
        call_sem_wait("(pointer):sint32"),
        call_sem_trywait("(pointer):sint32"),
        call_sem_timedwait("(pointer, sint64):sint32"),

        call_ioctl_bytes("(sint32, uint64, [sint8]):sint32"),
        call_ioctl_int("(sint32, uint64, sint32):sint32"),

        call_sysconf("(sint32):sint64"),

        crypt("([sint8], [sint8]):sint64");

        private final String signature;

        PosixNativeFunction(String signature) {
            this.signature = signature;
        }
    }

    protected static final class InvokeNativeFunction extends Node {
        private static final InvokeNativeFunction UNCACHED = new InvokeNativeFunction(SignatureLibrary.getUncached(), InteropLibrary.getUncached());

        @Child private SignatureLibrary functionInterop;
        @Child private InteropLibrary resultInterop;

        public InvokeNativeFunction(SignatureLibrary functionInterop, InteropLibrary resultInterop) {
            this.functionInterop = functionInterop;
            this.resultInterop = resultInterop;
        }

        @NeverDefault
        public static InvokeNativeFunction create() {
            return new InvokeNativeFunction(SignatureLibrary.getFactory().createDispatched(2), null);
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
            FunctionWithSignature funObject = posix.cachedFunctions.get(function.ordinal());
            try {
                return functionInterop.call(funObject.signature(), funObject.function(), args);
            } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }

        public long callLong(NFIPosixSupport posix, PosixNativeFunction function, Object... args) {
            try {
                return getResultInterop().asLong(call(posix, function, args));
            } catch (UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }

        public int callInt(NFIPosixSupport posix, PosixNativeFunction function, Object... args) {
            try {
                return getResultInterop().asInt(call(posix, function, args));
            } catch (UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }

        public byte callByte(NFIPosixSupport posix, PosixNativeFunction function, Object... args) {
            try {
                return getResultInterop().asByte(call(posix, function, args));
            } catch (UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }

        // Temporary - will be replaced with something else when we move this to Truffle
        private static String getLibPath(PythonContext context) {
            CompilerAsserts.neverPartOfCompilation();
            String libPythonName = PythonContext.getSupportLibName(NFIPosixSupport.SUPPORTING_NATIVE_LIB_NAME);
            TruffleFile homePath = context.getEnv().getInternalTruffleFile(context.getCAPIHome().toJavaStringUncached());
            TruffleFile file = homePath.resolve(libPythonName);
            return file.getPath();
        }

        @TruffleBoundary
        private static void loadLibrary(NFIPosixSupport posix) {
            String path = getLibPath(posix.context);
            try {
                posix.nfiLibrary = loadLibrary(posix, path);
            } catch (Throwable e) {
                throw new UnsupportedOperationException(String.format("""
                                Could not load posix support library from path '%s'. Troubleshooting:\s
                                Check permissions of the file.
                                Missing runtime Maven dependency 'org.graalvm.truffle:truffle-nfi-libffi' (should be a dependency of `org.graalvm.polyglot:python{-community}`)?""",
                                path));
            }
        }

        @TruffleBoundary
        private static Object loadLibrary(NFIPosixSupport posix, String path) {
            String backend = posix.nfiBackend.toJavaStringUncached();
            Env env = posix.context.getEnv();

            posix.context.ensureNFILanguage(null, "PosixModuleBackend", "native");

            Source loadSrc;
            if (path != null) {
                String withClause = backend.equals(J_NATIVE) ? "" : "with " + backend;
                String src = String.format("%sload (RTLD_LOCAL) \"%s\"", withClause, path);
                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.fine(String.format("Loading native library: %s", src));
                }
                loadSrc = Source.newBuilder(J_NFI_LANGUAGE, src, "load:" + SUPPORTING_NATIVE_LIB_NAME).internal(true).build();
            } else {
                loadSrc = Source.newBuilder(J_NFI_LANGUAGE, J_DEFAULT, J_DEFAULT).internal(true).build();
            }

            return env.parseInternal(loadSrc).call();
        }

        @TruffleBoundary
        private static void loadFunction(NFIPosixSupport posix, Object library, PosixNativeFunction function) {
            Object unbound;
            try {
                InteropLibrary interop = InteropLibrary.getUncached();

                String sig = String.format("with %s %s", posix.nfiBackend, function.signature);
                Source sigSrc = Source.newBuilder(J_NFI_LANGUAGE, sig, "posix-nfi-signature").internal(true).build();
                Object signature = posix.context.getEnv().parseInternal(sigSrc).call();

                unbound = interop.readMember(library, function.name());
                posix.cachedFunctions.set(function.ordinal(), new FunctionWithSignature(signature, unbound));
            } catch (UnsupportedMessageException | UnknownIdentifierException e) {
                throw CompilerDirectives.shouldNotReachHere(function.name(), e);
            }
        }

        public InteropLibrary getResultInterop() {
            if (resultInterop == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                resultInterop = insert(InteropLibrary.getFactory().createDispatched(2));
            }
            return resultInterop;
        }
    }

    private final PythonContext context;
    private final TruffleString nfiBackend;
    private volatile Object nfiLibrary;
    private volatile Object cryptLibrary;
    private final AtomicReferenceArray<FunctionWithSignature> cachedFunctions;
    @CompilationFinal(dimensions = 1) private long[] constantValues;

    public NFIPosixSupport(PythonContext context, TruffleString nfiBackend) {
        assert nfiBackend.equalsUncached(T_NATIVE, TS_ENCODING);
        this.context = context;
        this.nfiBackend = nfiBackend;
        this.cachedFunctions = new AtomicReferenceArray<>(PosixNativeFunction.values().length);
        setEnv(context.getEnv());
    }

    long getConstant(NFIPosixConstants constant) {
        if (constantValues == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            constantValues = new long[NFIPosixConstants.values().length];
            int result = InvokeNativeFunction.getUncached().callInt(this, PosixNativeFunction.init_constants, constantValues, constantValues.length);
            if (result != 0) {
                throw CompilerDirectives.shouldNotReachHere("Mismatched build of posix native library");
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
        // ordinary I/O when using NFI backend.
        try {
            TruffleFile truffleFile = context.getEnv().getInternalTruffleFile(".").getAbsoluteFile();
            context.getEnv().setCurrentWorkingDirectory(truffleFile);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Unable to change Truffle working directory", e);
        }
    }

    @ExportMessage
    public TruffleString getBackend() {
        return nfiBackend;
    }

    @ExportMessage
    public TruffleString strerror(int errorCode,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode,
                    @Shared("tsFromBytes") @Cached TruffleString.FromByteArrayNode fromByteArrayNode,
                    @Shared("fromUtf8") @Cached TruffleString.SwitchEncodingNode switchEncodingFromUtf8Node) {
        // From man pages: The GNU C Library uses a buffer of 1024 characters for strerror().
        // This buffer size therefore should be sufficient to avoid an ERANGE error when calling
        // strerror_r().
        byte[] buf = new byte[1024];
        invokeNode.call(this, PosixNativeFunction.call_strerror, errorCode, buf, buf.length);
        // TODO PyUnicode_DecodeLocale
        return cStringToTruffleString(buf, fromByteArrayNode, switchEncodingFromUtf8Node);
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
    public int close(int fd,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        final int rv = invokeNode.callInt(this, PosixNativeFunction.call_close, fd);
        if (rv < 0) {
            throw getErrnoAndThrowPosixException(invokeNode);
        }
        return rv;
    }

    @ExportMessage
    public Buffer read(int fd, long length,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        long count = Math.min(length, MAX_READ);
        Buffer buffer = Buffer.allocate(count);
        setErrno(invokeNode, 0);        // TODO CPython does this, but do we need it?
        long n = invokeNode.callLong(this, PosixNativeFunction.call_read, fd, buffer.data, count);
        if (n < 0) {
            throw getErrnoAndThrowPosixException(invokeNode);
        }
        return buffer.withLength(n);
    }

    @ExportMessage
    public long write(int fd, Buffer data,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        setErrno(invokeNode, 0);        // TODO CPython does this, but do we need it?
        long n = invokeNode.callLong(this, PosixNativeFunction.call_write, fd, data.data, data.length);
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
        if (invokeNode.callInt(this, PosixNativeFunction.call_pipe2, fds) != 0) {
            throw getErrnoAndThrowPosixException(invokeNode);
        }
        return fds;
    }

    @ExportMessage
    public SelectResult select(int[] readfds, int[] writefds, int[] errorfds, Timeval timeout,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
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
        int result = invokeNode.callInt(this, PosixNativeFunction.call_select, nfds,
                        readfds, readfds.length,
                        writefds, writefds.length,
                        errorfds, errorfds.length,
                        secs, usecs, selected);
        if (result < 0) {
            throw getErrnoAndThrowPosixException(invokeNode);
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
    public boolean poll(int fd, boolean forWriting, Timeval timeout,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        long secs = -1, usecs = -1;
        if (timeout != null) {
            secs = timeout.getSeconds();
            usecs = timeout.getMicroseconds();
        }
        int result = invokeNode.callInt(this, PosixNativeFunction.call_poll, fd,
                        forWriting ? 1 : 0, secs, usecs);
        if (result < 0) {
            throw getErrnoAndThrowPosixException(invokeNode);
        }
        if (result == 0) {
            return false;
        } else {
            return true;
        }
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
    public void truncate(Object path, long length,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        int res = invokeNode.callInt(this, PosixNativeFunction.call_truncate, pathToCString(path), length);
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
    void flock(int fd, int operation,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        int res = invokeNode.callInt(this, PosixNativeFunction.call_flock, fd, operation);
        if (res != 0) {
            throw getErrnoAndThrowPosixException(invokeNode);
        }
    }

    @ExportMessage
    void fcntlLock(int fd, boolean blocking, int lockType, int whence, long start, long length,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        int res = invokeNode.callInt(this, PosixNativeFunction.call_fcntl_lock, fd, blocking, lockType, whence, start, length);
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
        if (invokeNode.callInt(this, PosixNativeFunction.get_terminal_size, fd, size) != 0) {
            throw getErrnoAndThrowPosixException(invokeNode);
        }
        return size;
    }

    @ExportMessage
    public long sysconf(int name,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        long result = invokeNode.callLong(this, PosixNativeFunction.call_sysconf, name);
        if (result == -1) {
            int errno = getErrno(invokeNode);
            if (errno != 0) {
                throw newPosixException(invokeNode, errno);
            }
        }
        return result;
    }

    @ExportMessage
    public long[] fstatat(int dirFd, Object pathname, boolean followSymlinks,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        long[] out = new long[13];
        int res = invokeNode.callInt(this, PosixNativeFunction.call_fstatat, dirFd, pathToCString(pathname), followSymlinks ? 1 : 0, out);
        if (res != 0) {
            throw newPosixException(invokeNode, getErrno(invokeNode));
        }
        return out;
    }

    @ExportMessage
    public long[] fstat(int fd,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        long[] out = new long[13];
        int res = invokeNode.callInt(this, PosixNativeFunction.call_fstat, fd, out);
        if (res != 0) {
            throw newPosixException(invokeNode, getErrno(invokeNode));
        }
        return out;
    }

    @ExportMessage
    public long[] statvfs(Object path,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        long[] out = new long[11];
        int res = invokeNode.callInt(this, PosixNativeFunction.call_statvfs, pathToCString(path), out);
        if (res != 0) {
            throw newPosixException(invokeNode, getErrno(invokeNode));
        }
        return out;
    }

    @ExportMessage
    public long[] fstatvfs(int fd,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        long[] out = new long[11];
        int res = invokeNode.callInt(this, PosixNativeFunction.call_fstatvfs, fd, out);
        if (res != 0) {
            throw newPosixException(invokeNode, getErrno(invokeNode));
        }
        return out;
    }

    @ExportMessage
    public Object[] uname(
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode,
                    @Shared("tsFromBytes") @Cached TruffleString.FromByteArrayNode fromByteArrayNode,
                    @Shared("fromUtf8") @Cached TruffleString.SwitchEncodingNode switchEncodingFromUtf8Node) throws PosixException {
        byte[] sys = new byte[UNAME_BUF_LENGTH];
        byte[] node = new byte[UNAME_BUF_LENGTH];
        byte[] rel = new byte[UNAME_BUF_LENGTH];
        byte[] ver = new byte[UNAME_BUF_LENGTH];
        byte[] machine = new byte[UNAME_BUF_LENGTH];
        int res = invokeNode.callInt(this, PosixNativeFunction.call_uname, sys, node, rel, ver, machine, UNAME_BUF_LENGTH);
        if (res != 0) {
            throw getErrnoAndThrowPosixException(invokeNode);
        }
        return new Object[]{
                        // TODO PyUnicode_DecodeFSDefault
                        cStringToTruffleString(sys, fromByteArrayNode, switchEncodingFromUtf8Node),
                        cStringToTruffleString(node, fromByteArrayNode, switchEncodingFromUtf8Node),
                        cStringToTruffleString(rel, fromByteArrayNode, switchEncodingFromUtf8Node),
                        cStringToTruffleString(ver, fromByteArrayNode, switchEncodingFromUtf8Node),
                        cStringToTruffleString(machine, fromByteArrayNode, switchEncodingFromUtf8Node)
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
    public void linkat(int oldFdDir, Object oldPath, int newFdDir, Object newPath, int flags,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        int result = invokeNode.callInt(this, PosixNativeFunction.call_linkat, oldFdDir, pathToCString(oldPath), newFdDir, pathToCString(newPath), flags);
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
            int n = invokeNode.callInt(this, PosixNativeFunction.call_getcwd, buffer.data, bufLen);
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
        return ptr;
    }

    @ExportMessage
    public Object fdopendir(int fd,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        long ptr = invokeNode.callLong(this, PosixNativeFunction.call_fdopendir, fd);
        if (ptr == 0) {
            throw newPosixException(invokeNode, getErrno(invokeNode));
        }
        return ptr;
    }

    @ExportMessage
    public void closedir(Object dirStreamObj,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        int res = invokeNode.callInt(this, PosixNativeFunction.call_closedir, dirStreamObj);
        if (res != 0) {
            throw getErrnoAndThrowPosixException(invokeNode);
        }
    }

    @ExportMessage
    public Object readdir(Object dirStreamObj,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        Buffer name = Buffer.allocate(DIRENT_NAME_BUF_LENGTH);
        long[] out = new long[2];
        int result;
        do {
            result = invokeNode.callInt(this, PosixNativeFunction.call_readdir, dirStreamObj, name.data, DIRENT_NAME_BUF_LENGTH, out);
        } while (result != 0 && name.data[0] == '.' && (name.data[1] == 0 || (name.data[1] == '.' && name.data[2] == 0)));
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
    public void rewinddir(Object dirStreamObj,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) {
        invokeNode.call(this, PosixNativeFunction.call_rewinddir, dirStreamObj);
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
    public void utimensat(int dirFd, Object pathname, long[] timespec, boolean followSymlinks,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        assert PosixConstants.HAVE_UTIMENSAT.value;
        assert timespec == null || timespec.length == 4;
        int ret = invokeNode.callInt(this, PosixNativeFunction.call_utimensat, dirFd, pathToCString(pathname), wrap(timespec), followSymlinks ? 1 : 0);
        if (ret != 0) {
            throw newPosixException(invokeNode, getErrno(invokeNode));
        }
    }

    @ExportMessage
    public void futimens(int fd, long[] timespec,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        assert PosixConstants.HAVE_FUTIMENS.value;
        assert timespec == null || timespec.length == 4;
        int ret = invokeNode.callInt(this, PosixNativeFunction.call_futimens, fd, wrap(timespec));
        if (ret != 0) {
            throw newPosixException(invokeNode, getErrno(invokeNode));
        }
    }

    @ExportMessage
    public void futimes(int fd, Timeval[] timeval,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        assert timeval == null || timeval.length == 2;
        int ret = invokeNode.callInt(this, PosixNativeFunction.call_futimes, fd, wrap(timeval));
        if (ret != 0) {
            throw newPosixException(invokeNode, getErrno(invokeNode));
        }
    }

    @ExportMessage
    public void lutimes(Object filename, Timeval[] timeval,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        assert timeval == null || timeval.length == 2;
        int ret = invokeNode.callInt(this, PosixNativeFunction.call_lutimes, pathToCString(filename), wrap(timeval));
        if (ret != 0) {
            throw newPosixException(invokeNode, getErrno(invokeNode));
        }
    }

    @ExportMessage
    public void utimes(Object filename, Timeval[] timeval,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        assert timeval == null || timeval.length == 2;
        int ret = invokeNode.callInt(this, PosixNativeFunction.call_utimes, pathToCString(filename), wrap(timeval));
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
    public void fchownat(int dirFd, Object path, long owner, long group, boolean followSymlinks,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        int ret = invokeNode.callInt(this, PosixNativeFunction.call_fchownat, dirFd, pathToCString(path), owner, group, followSymlinks ? 1 : 0);
        if (ret != 0) {
            throw newPosixException(invokeNode, getErrno(invokeNode));
        }
    }

    @ExportMessage
    public void fchown(int fd, long owner, long group,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        int ret = invokeNode.callInt(this, PosixNativeFunction.call_fchown, fd, owner, group);
        if (ret != 0) {
            throw newPosixException(invokeNode, getErrno(invokeNode));
        }
    }

    @ExportMessage
    public Object readlinkat(int dirFd, Object path,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        Buffer buffer = Buffer.allocate(PATH_MAX.value);
        long n = invokeNode.callLong(this, PosixNativeFunction.call_readlinkat, dirFd, pathToCString(path), buffer.data, PATH_MAX.value);
        if (n < 0) {
            throw newPosixException(invokeNode, getErrno(invokeNode));
        }
        return buffer.withLength(n);
    }

    @ExportMessage
    public void kill(long pid, int signal,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        int res = invokeNode.callInt(this, PosixNativeFunction.call_kill, pid, signal);
        if (res == -1) {
            throw getErrnoAndThrowPosixException(invokeNode);
        }
    }

    @ExportMessage
    public void killpg(long pgid, int signal,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        int res = invokeNode.callInt(this, PosixNativeFunction.call_killpg, pgid, signal);
        if (res == -1) {
            throw getErrnoAndThrowPosixException(invokeNode);
        }
    }

    @ExportMessage
    public long[] waitpid(long pid, int options,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        int[] status = new int[1];
        boolean hasNohang = (options & WNOHANG.getValueIfDefined()) != 0;
        int subOptions = options | WNOHANG.getValueIfDefined();
        Object wrappedStatus = status;
        long res = invokeNode.callLong(this, PosixNativeFunction.call_waitpid, pid, wrappedStatus, subOptions);
        while (res == 0 && !hasNohang) {
            TruffleSafepoint.setBlockedThreadInterruptible(invokeNode, (ignored) -> {
                Thread.sleep(20);
            }, null);
            res = invokeNode.callLong(this, PosixNativeFunction.call_waitpid, pid, wrappedStatus, subOptions);
        }
        if (res < 0) {
            throw getErrnoAndThrowPosixException(invokeNode);
        }
        return new long[]{res, status[0]};
    }

    @ExportMessage
    public boolean wcoredump(int status,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) {
        return invokeNode.callInt(this, PosixNativeFunction.call_wcoredump, status) != 0;
    }

    @ExportMessage
    public void abort(@Shared("invoke") @Cached InvokeNativeFunction invokeNode) {
        invokeNode.call(this, PosixNativeFunction.call_abort);
    }

    @ExportMessage
    public boolean wifcontinued(int status,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) {
        return invokeNode.callInt(this, PosixNativeFunction.call_wifcontinued, status) != 0;
    }

    @ExportMessage
    public boolean wifstopped(int status,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) {
        return invokeNode.callInt(this, PosixNativeFunction.call_wifstopped, status) != 0;
    }

    @ExportMessage
    public boolean wifsignaled(int status,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) {
        return invokeNode.callInt(this, PosixNativeFunction.call_wifsignaled, status) != 0;
    }

    @ExportMessage
    public boolean wifexited(int status,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) {
        return invokeNode.callInt(this, PosixNativeFunction.call_wifexited, status) != 0;
    }

    @ExportMessage
    public int wexitstatus(int status,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) {
        return invokeNode.callInt(this, PosixNativeFunction.call_wexitstatus, status);
    }

    @ExportMessage
    public int wtermsig(int status,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) {
        return invokeNode.callInt(this, PosixNativeFunction.call_wtermsig, status);
    }

    @ExportMessage
    public int wstopsig(int status,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) {
        return invokeNode.callInt(this, PosixNativeFunction.call_wstopsig, status);
    }

    @ExportMessage
    public long getuid(@Shared("invoke") @Cached InvokeNativeFunction invokeNode) {
        return invokeNode.callLong(this, PosixNativeFunction.call_getuid);
    }

    @ExportMessage
    public long geteuid(@Shared("invoke") @Cached InvokeNativeFunction invokeNode) {
        return invokeNode.callLong(this, PosixNativeFunction.call_geteuid);
    }

    @ExportMessage
    public long getgid(@Shared("invoke") @Cached InvokeNativeFunction invokeNode) {
        return invokeNode.callLong(this, PosixNativeFunction.call_getgid);
    }

    @ExportMessage
    public long getegid(@Shared("invoke") @Cached InvokeNativeFunction invokeNode) {
        return invokeNode.callLong(this, PosixNativeFunction.call_getegid);
    }

    @ExportMessage
    public long getppid(@Shared("invoke") @Cached InvokeNativeFunction invokeNode) {
        return invokeNode.callLong(this, PosixNativeFunction.call_getppid);
    }

    @ExportMessage
    public void setpgid(long pid, long pgid,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        int res = invokeNode.callInt(this, PosixNativeFunction.call_setpgid, pid, pgid);
        if (res < 0) {
            throw getErrnoAndThrowPosixException(invokeNode);
        }
    }

    @ExportMessage
    public long getpgid(long pid,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        long res = invokeNode.callLong(this, PosixNativeFunction.call_getpgid, pid);
        if (res < 0) {
            throw getErrnoAndThrowPosixException(invokeNode);
        }
        return res;
    }

    @ExportMessage
    public long getpgrp(@Shared("invoke") @Cached InvokeNativeFunction invokeNode) {
        return invokeNode.callLong(this, PosixNativeFunction.call_getpgrp);
    }

    @ExportMessage
    public long getsid(long pid,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        long res = invokeNode.callLong(this, PosixNativeFunction.call_getsid, pid);
        if (res < 0) {
            throw getErrnoAndThrowPosixException(invokeNode);
        }
        return res;
    }

    @ExportMessage
    public long setsid(
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        long res = invokeNode.callLong(this, PosixNativeFunction.call_setsid);
        if (res < 0) {
            throw getErrnoAndThrowPosixException(invokeNode);
        }
        return res;
    }

    @ExportMessage
    public long[] getgroups(
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        // The first call gets us the number of groups, so we can allocate the output array
        int res = invokeNode.callInt(this, PosixNativeFunction.call_getgroups, 0, 0);
        if (res < 0) {
            throw getErrnoAndThrowPosixException(invokeNode);
        }
        if (res == 0) {
            return EMPTY_LONG_ARRAY;
        }
        long[] groups = new long[res];
        res = invokeNode.callInt(this, PosixNativeFunction.call_getgroups, groups.length, groups);
        if (res < 0) {
            throw getErrnoAndThrowPosixException(invokeNode);
        }
        return groups;
    }

    @ExportMessage
    public RusageResult getrusage(int who,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        long[] result = new long[16];
        int res = invokeNode.callInt(this, PosixNativeFunction.call_getrusage, who, result);
        if (res < 0) {
            throw getErrnoAndThrowPosixException(invokeNode);
        }
        return new RusageResult(Double.longBitsToDouble(result[0]), Double.longBitsToDouble(result[1]),
                        result[2], result[3], result[4], result[5],
                        result[6], result[7], result[8], result[9], result[10],
                        result[11], result[12], result[13], result[14], result[15]);
    }

    @ExportMessage
    public OpenPtyResult openpty(@Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        int[] outvars = new int[2];
        int res = invokeNode.callInt(this, PosixNativeFunction.call_openpty, outvars);
        if (res == -1) {
            throw getErrnoAndThrowPosixException(invokeNode);
        }
        return new OpenPtyResult(outvars[0], outvars[1]);
    }

    @ExportMessage
    public TruffleString ctermid(@Shared("invoke") @Cached InvokeNativeFunction invokeNode,
                    @Shared("tsFromBytes") @Cached TruffleString.FromByteArrayNode fromByteArrayNode,
                    @Shared("fromUtf8") @Cached TruffleString.SwitchEncodingNode switchEncodingFromUtf8Node) throws PosixException {
        byte[] buf = new byte[L_ctermid.value];
        int res = invokeNode.callInt(this, PosixNativeFunction.call_ctermid, buf);
        if (res == -1) {
            throw getErrnoAndThrowPosixException(invokeNode);
        }
        // TODO PyUnicode_DecodeFSDefault
        return cStringToTruffleString(buf, fromByteArrayNode, switchEncodingFromUtf8Node);
    }

    @ExportMessage
    public void setenv(Object name, Object value, boolean overwrite,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        int res = invokeNode.callInt(this, PosixNativeFunction.call_setenv, pathToCString(name), pathToCString(value), overwrite ? 1 : 0);
        if (res == -1) {
            throw getErrnoAndThrowPosixException(invokeNode);
        }
    }

    @ExportMessage
    public void unsetenv(Object name,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        int res = invokeNode.callInt(this, PosixNativeFunction.call_unsetenv, pathToCString(name));
        if (res == -1) {
            throw getErrnoAndThrowPosixException(invokeNode);
        }
    }

    @ExportMessage
    public int forkExec(Object[] executables, Object[] args, Object cwd, Object[] env, int stdinReadFd, int stdinWriteFd, int stdoutReadFd, int stdoutWriteFd, int stderrReadFd, int stderrWriteFd,
                    int errPipeReadFd, int errPipeWriteFd, boolean closeFds, boolean restoreSignals, boolean callSetsid, int[] fdsToKeep,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {

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
            throw newPosixException(invokeNode, OSErrorEnum.E2BIG.getNumber());
        }

        // This also guarantees that offsetsLen did not overflow: we add +1 to dataLen for each
        // '\0', i.e. dataLen >= "number of strings" and offsetsLen < "number of strings" + 3
        // (3 accounts for the NULL terminating the executables, args and env arrays).
        if (dataLen >= Integer.MAX_VALUE - 3) {
            throw newPosixException(invokeNode, OSErrorEnum.E2BIG.getNumber());
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

        int res = invokeNode.callInt(this, PosixNativeFunction.fork_exec,
                        data, offsets, offsets.length, argsPos, envPos, cwdPos,
                        stdinReadFd, stdinWriteFd,
                        stdoutReadFd, stdoutWriteFd,
                        stderrReadFd, stderrWriteFd,
                        errPipeReadFd, errPipeWriteFd,
                        closeFds ? 1 : 0,
                        restoreSignals ? 1 : 0,
                        callSetsid ? 1 : 0,
                        fdsToKeep, fdsToKeep.length);
        if (res == -1) {
            throw getErrnoAndThrowPosixException(invokeNode);
        }
        return res;
    }

    @ExportMessage
    public void execv(Object pathname, Object[] args,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {

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
            throw newPosixException(invokeNode, OSErrorEnum.E2BIG.getNumber());
        }

        // This also guarantees that offsetsLen did not overflow: we add +1 to dataLen for each
        // '\0', i.e. dataLen >= "number of strings" and offsetsLen == "number of strings" + 1
        // (1 accounts for the NULL terminating the args array).
        // Also, dataLen > pathnameLen, so this check makes sure that the cast of pathnameLen to int
        // below is safe.
        if (dataLen >= Integer.MAX_VALUE - 1) {
            throw newPosixException(invokeNode, OSErrorEnum.E2BIG.getNumber());
        }

        byte[] data = new byte[(int) dataLen];
        long[] offsets = new long[offsetsLen];

        PythonUtils.arraycopy(((Buffer) pathname).data, 0, data, 0, (int) pathnameLen);
        long offset = encodeCStringArray(data, pathnameLen + 1L, offsets, 1, args);
        assert offset == dataLen;

        invokeNode.call(this, PosixNativeFunction.call_execv, data, offsets, offsets.length);
        throw getErrnoAndThrowPosixException(invokeNode);
    }

    @ExportMessage
    public int system(Object command,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) {
        return invokeNode.callInt(this, PosixNativeFunction.call_system, pathToCString(command));
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
    public Object mmap(long length, int prot, int flags, int fd, long offset,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        long address = invokeNode.callLong(this, PosixNativeFunction.call_mmap, length, prot, flags, fd, offset);
        if (address == 0) {
            throw newPosixException(invokeNode, getErrno(invokeNode));
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
    public void mmapFlush(Object mmap, long offset, long length,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) {
        MMapHandle handle = (MMapHandle) mmap;
        checkIndexAndLen(handle, offset, length);
        invokeNode.call(this, PosixNativeFunction.call_msync, handle.pointer, offset, length);
    }

    @ExportMessage
    public void mmapUnmap(Object mmap, long length,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        MMapHandle handle = (MMapHandle) mmap;
        if (length != handle.length) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new IllegalArgumentException();
        }
        int result = invokeNode.callInt(this, PosixNativeFunction.call_munmap, handle.pointer, length);
        if (result != 0) {
            throw newPosixException(invokeNode, getErrno(invokeNode));
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
    public int socket(int domain, int type, int protocol,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        int result = invokeNode.callInt(this, PosixNativeFunction.call_socket, domain, type, protocol);
        if (result == -1) {
            throw getErrnoAndThrowPosixException(invokeNode);
        }
        return result;
    }

    @ExportMessage
    public AcceptResult accept(int sockfd,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        UniversalSockAddrImpl addr = new UniversalSockAddrImpl(this);
        int result = invokeNode.callInt(this, PosixNativeFunction.call_accept, sockfd, addr.data, addr.len);
        if (result == -1) {
            throw getErrnoAndThrowPosixException(invokeNode);
        }
        return new AcceptResult(result, addr);
    }

    @ExportMessage
    public void bind(int sockfd, UniversalSockAddr usa,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        UniversalSockAddrImpl addr = (UniversalSockAddrImpl) usa;
        int result = invokeNode.callInt(this, PosixNativeFunction.call_bind, sockfd, addr.data, addr.getLen());
        if (result == -1) {
            throw getErrnoAndThrowPosixException(invokeNode);
        }
    }

    @ExportMessage
    public void connect(int sockfd, UniversalSockAddr usa,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        UniversalSockAddrImpl addr = (UniversalSockAddrImpl) usa;
        int result = invokeNode.callInt(this, PosixNativeFunction.call_connect, sockfd, addr.data, addr.getLen());
        if (result == -1) {
            throw getErrnoAndThrowPosixException(invokeNode);
        }
    }

    @ExportMessage
    public void listen(int sockfd, int backlog,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        int result = invokeNode.callInt(this, PosixNativeFunction.call_listen, sockfd, backlog);
        if (result == -1) {
            throw getErrnoAndThrowPosixException(invokeNode);
        }
    }

    @ExportMessage
    public UniversalSockAddr getpeername(int sockfd,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        UniversalSockAddrImpl addr = new UniversalSockAddrImpl(this);
        int result = invokeNode.callInt(this, PosixNativeFunction.call_getpeername, sockfd, addr.data, addr.len);
        if (result == -1) {
            throw getErrnoAndThrowPosixException(invokeNode);
        }
        return addr;
    }

    @ExportMessage
    public UniversalSockAddr getsockname(int sockfd,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        UniversalSockAddrImpl addr = new UniversalSockAddrImpl(this);
        int result = invokeNode.callInt(this, PosixNativeFunction.call_getsockname, sockfd, addr.data, addr.len);
        if (result == -1) {
            throw getErrnoAndThrowPosixException(invokeNode);
        }
        return addr;
    }

    @ExportMessage
    public int send(int sockfd, byte[] buf, int offset, int len, int flags,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        checkBounds(buf, offset, len);
        int result = invokeNode.callInt(this, PosixNativeFunction.call_send, sockfd, buf, offset, len, flags);
        if (result == -1) {
            throw getErrnoAndThrowPosixException(invokeNode);
        }
        return result;
    }

    @ExportMessage
    public int sendto(int sockfd, byte[] buf, int offset, int len, int flags, UniversalSockAddr usa,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        checkBounds(buf, offset, len);
        UniversalSockAddrImpl destAddr = (UniversalSockAddrImpl) usa;
        int result = invokeNode.callInt(this, PosixNativeFunction.call_sendto, sockfd, buf, offset, len, flags, destAddr.data, destAddr.getLen());
        if (result == -1) {
            throw getErrnoAndThrowPosixException(invokeNode);
        }
        return result;
    }

    @ExportMessage
    public int recv(int sockfd, byte[] buf, int offset, int len, int flags,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        checkBounds(buf, offset, len);
        int result = invokeNode.callInt(this, PosixNativeFunction.call_recv, sockfd, buf, offset, len, flags);
        if (result == -1) {
            throw getErrnoAndThrowPosixException(invokeNode);
        }
        return result;
    }

    @ExportMessage
    public RecvfromResult recvfrom(int sockfd, byte[] buf, int offset, int len, int flags,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        checkBounds(buf, offset, len);
        UniversalSockAddrImpl srcAddr = new UniversalSockAddrImpl(this);
        int result = invokeNode.callInt(this, PosixNativeFunction.call_recvfrom, sockfd, buf, offset, len, flags, srcAddr.data, srcAddr.len);
        if (result == -1) {
            throw getErrnoAndThrowPosixException(invokeNode);
        }
        return new RecvfromResult(result, srcAddr);
    }

    @ExportMessage
    public void shutdown(int sockfd, int how,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        int res = invokeNode.callInt(this, PosixNativeFunction.call_shutdown, sockfd, how);
        if (res != 0) {
            throw getErrnoAndThrowPosixException(invokeNode);
        }
    }

    @ExportMessage
    public int getsockopt(int sockfd, int level, int optname, byte[] optval, int optlen,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        assert optlen >= 0 && optval.length >= optlen;
        int[] bufLen = new int[]{optlen};
        int res = invokeNode.callInt(this, PosixNativeFunction.call_getsockopt, sockfd, level, optname, optval, bufLen);
        if (res != 0) {
            throw getErrnoAndThrowPosixException(invokeNode);
        }
        return bufLen[0];
    }

    @ExportMessage
    public void setsockopt(int sockfd, int level, int optname, byte[] optval, int optlen,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        assert optlen >= 0 && optval.length >= optlen;
        int res = invokeNode.callInt(this, PosixNativeFunction.call_setsockopt, sockfd, level, optname, optval, optlen);
        if (res != 0) {
            throw getErrnoAndThrowPosixException(invokeNode);
        }
    }

    @ExportMessage
    public int inet_addr(Object src,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) {
        return invokeNode.callInt(this, PosixNativeFunction.call_inet_addr, pathToCString(src));
    }

    @ExportMessage
    public int inet_aton(Object src,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws InvalidAddressException {
        long r = invokeNode.callLong(this, PosixNativeFunction.call_inet_aton, pathToCString(src));
        if (r < 0) {
            throw new InvalidAddressException();
        }
        return (int) r;
    }

    @ExportMessage
    public Object inet_ntoa(int src,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) {
        Buffer buf = Buffer.allocate(INET_ADDRSTRLEN.value);
        int len = invokeNode.callInt(this, PosixNativeFunction.call_inet_ntoa, src, buf.data);
        return buf.withLength(len);
    }

    @ExportMessage
    public byte[] inet_pton(int family, Object src,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException, InvalidAddressException {
        byte[] buf = new byte[family == AF_INET.value ? 4 : 16];
        int res = invokeNode.callInt(this, PosixNativeFunction.call_inet_pton, family, pathToCString(src), buf);
        // Rather unusually, the return value of 0 does not indicate success but is used by
        // inet_pton to report invalid format of the address (without setting errno).
        // Success is reported by returning 1.
        if (res == 1) {
            return buf;
        }
        if (res == 0) {
            throw new InvalidAddressException();
        }
        throw getErrnoAndThrowPosixException(invokeNode);
    }

    @ExportMessage
    public Object inet_ntop(int family, byte[] src,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        if ((family == AF_INET.value && src.length < 4) || (family == AF_INET6.value && src.length < 16)) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new IllegalArgumentException("Invalid length of IPv4/6 address");
        }
        Buffer buf = Buffer.allocate(INET6_ADDRSTRLEN.value);
        int res = invokeNode.callInt(this, PosixNativeFunction.call_inet_ntop, family, src, buf.data, INET6_ADDRSTRLEN.value);
        if (res < 0) {
            throw getErrnoAndThrowPosixException(invokeNode);
        }
        return buf.withLength(findZero(buf.data));
    }

    @ExportMessage
    public Object gethostname(@Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        int maxLen = (HOST_NAME_MAX.defined ? HOST_NAME_MAX.getValueIfDefined() : _POSIX_HOST_NAME_MAX.value) + 1;
        Buffer buf = Buffer.allocate(maxLen);
        int res = invokeNode.callInt(this, PosixNativeFunction.call_gethostname, buf.data, maxLen);
        if (res != 0) {
            throw getErrnoAndThrowPosixException(invokeNode);
        }
        return buf.withLength(findZero(buf.data));
    }

    @ExportMessage
    public Object[] getnameinfo(UniversalSockAddr usa, int flags,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode,
                    @Shared("tsFromBytes") @Cached TruffleString.FromByteArrayNode fromByteArrayNode,
                    @Shared("fromUtf8") @Cached TruffleString.SwitchEncodingNode switchEncodingFromUtf8Node) throws GetAddrInfoException {
        Buffer host = Buffer.allocate(NI_MAXHOST.value);
        Buffer serv = Buffer.allocate(NI_MAXSERV.value);
        UniversalSockAddrImpl addr = (UniversalSockAddrImpl) usa;
        int res = invokeNode.callInt(this, PosixNativeFunction.call_getnameinfo, addr.data, addr.getLen(), host.data, NI_MAXHOST.value, serv.data, NI_MAXSERV.value, flags);
        if (res != 0) {
            throw new GetAddrInfoException(res, gai_strerror(res, invokeNode, fromByteArrayNode, switchEncodingFromUtf8Node));
        }
        return new Object[]{
                        host.withLength(findZero(host.data)),
                        serv.withLength(findZero(serv.data)),
        };
    }

    @ExportMessage
    public AddrInfoCursor getaddrinfo(Object node, Object service, int family, int sockType, int protocol, int flags,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode,
                    @Shared("tsFromBytes") @Cached TruffleString.FromByteArrayNode fromByteArrayNode,
                    @Shared("fromUtf8") @Cached TruffleString.SwitchEncodingNode switchEncodingFromUtf8Node) throws GetAddrInfoException {
        long[] ptr = new long[1];
        int res = invokeNode.callInt(this, PosixNativeFunction.call_getaddrinfo, pathToCStringOrNull(node), pathToCStringOrNull(service), family, sockType, protocol, flags, ptr);
        if (res != 0) {
            throw new GetAddrInfoException(res, gai_strerror(res, invokeNode, fromByteArrayNode, switchEncodingFromUtf8Node));
        }
        assert ptr[0] != 0;     // getaddrinfo should return at least one result
        return new AddrInfoCursorImpl(this, ptr[0], invokeNode);
    }

    @ExportMessage
    public TruffleString crypt(TruffleString word, TruffleString salt,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode,
                    @Shared("toUtf8") @Cached TruffleString.SwitchEncodingNode switchEncodingToUtf8Node,
                    @Shared("tsCopyBytes") @Cached TruffleString.CopyToByteArrayNode copyToByteArrayNode,
                    @Shared("tsFromBytes") @Cached TruffleString.FromByteArrayNode fromByteArrayNode,
                    @Shared("fromUtf8") @Cached TruffleString.SwitchEncodingNode switchEncodingFromUtf8Node) throws PosixException {
        /*
         * We don't want to link the posix library with libcrypt, because it might not be available
         * on the target system and would make the whole posix library fail to load. So we load it
         * dynamically on demand.
         */
        if (injectBranchProbability(SLOWPATH_PROBABILITY, cryptLibrary == null)) {
            try {
                cryptLibrary = InvokeNativeFunction.loadLibrary(this, PythonOS.getPythonOS() != PythonOS.PLATFORM_DARWIN ? "libcrypt.so" : null);
            } catch (Throwable e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw PRaiseNode.raiseStatic(invokeNode, PythonBuiltinClassType.SystemError, ErrorMessages.UNABLE_TO_LOAD_LIBCRYPT);
            }
        }
        PosixNativeFunction function = PosixNativeFunction.crypt;
        if (injectBranchProbability(SLOWPATH_PROBABILITY, cachedFunctions.get(function.ordinal()) == null)) {
            InvokeNativeFunction.loadFunction(this, cryptLibrary, function);
        }
        FunctionWithSignature funObject = cachedFunctions.get(function.ordinal());
        /*
         * From the manpage: Upon successful completion, crypt returns a pointer to a string which
         * encodes both the hashed passphrase, and the settings that were used to encode it. See
         * crypt(5) for more detail on the format of hashed passphrases. crypt places its result in
         * a static storage area, which will be overwritten by subsequent calls to crypt. It is not
         * safe to call crypt from multiple threads simultaneously. Upon error, it may return a NULL
         * pointer or a pointer to an invalid hash, depending on the implementation.
         */
        // Note GIL is not enough as crypt is using global memory, so we need a really global lock
        synchronized (CRYPT_LOCK) {
            long resultPtr;
            Object[] args = new Object[]{
                            stringToUTF8CString(word, switchEncodingToUtf8Node, copyToByteArrayNode),
                            stringToUTF8CString(salt, switchEncodingToUtf8Node, copyToByteArrayNode)};
            try {
                Object interopResult = invokeNode.functionInterop.call(funObject.signature(), funObject.function(), args);
                resultPtr = invokeNode.getResultInterop().asLong(interopResult);
            } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
            // CPython doesn't handle the case of "invalid hash" return specially and neither do we
            if (resultPtr == 0) {
                throw getErrnoAndThrowPosixException(invokeNode);
            }
            int len = 0;
            while (UNSAFE.getByte(resultPtr + len) != 0) {
                len++;
            }
            byte[] resultBytes = new byte[len];
            UNSAFE.copyMemory(null, resultPtr, resultBytes, Unsafe.ARRAY_BYTE_BASE_OFFSET, len);
            return createString(resultBytes, 0, resultBytes.length, false, fromByteArrayNode, switchEncodingFromUtf8Node);
        }
    }

    private TruffleString gai_strerror(int errorCode, InvokeNativeFunction invokeNode, TruffleString.FromByteArrayNode fromByteArrayNode, TruffleString.SwitchEncodingNode switchEncodingFromUtf8Node) {
        byte[] buf = new byte[1024];
        invokeNode.call(this, PosixNativeFunction.call_gai_strerror, errorCode, buf, buf.length);
        // TODO PyUnicode_DecodeLocale
        return cStringToTruffleString(buf, fromByteArrayNode, switchEncodingFromUtf8Node);
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
     * To avoid multiple NFI calls, we transfer the data in batch using arrays of {@code int}s and
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

        private void update(long ptr, NFIPosixSupport nfiPosixSupport, InvokeNativeFunction invokeNode) {
            socketAddress = new byte[(int) nfiPosixSupport.getConstant(SIZEOF_STRUCT_SOCKADDR_STORAGE)];
            int res = invokeNode.callInt(nfiPosixSupport, PosixNativeFunction.get_addrinfo_members, ptr, intData, longData,
                            socketAddress);
            if (res != 0) {
                throw shouldNotReachHere("the length of ai_canonname does not fit into an int");
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

        private final NFIPosixSupport nfiPosixSupport;
        private long head;
        private AddrInfo info;

        AddrInfoCursorImpl(NFIPosixSupport nfiPosixSupport, long head, InvokeNativeFunction invokeNode) {
            this.nfiPosixSupport = nfiPosixSupport;
            this.head = head;
            info = new AddrInfo();
            info.update(head, nfiPosixSupport, invokeNode);
        }

        @ExportMessage
        void release(@Shared("invoke") @Cached InvokeNativeFunction invokeNode) {
            checkReleased();
            invokeNode.call(nfiPosixSupport, PosixNativeFunction.call_freeaddrinfo, head);
            head = 0;
        }

        @ExportMessage
        boolean next(@Shared("invoke") @Cached InvokeNativeFunction invokeNode) {
            checkReleased();
            long nextPtr = info.getNextPtr();
            if (nextPtr == 0) {
                return false;
            }
            info.update(nextPtr, nfiPosixSupport, invokeNode);
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
            UniversalSockAddrImpl addr = new UniversalSockAddrImpl(nfiPosixSupport);
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

        private final NFIPosixSupport nfiPosixSupport;
        private final byte[] data;
        private final int[] len = new int[]{0};

        UniversalSockAddrImpl(NFIPosixSupport nfiPosixSupport) {
            this.nfiPosixSupport = nfiPosixSupport;
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
                int pathLen = -1;
                for (int i = pathOffset; i < data.length; i++) {
                    if (data[i] == '\0') {
                        pathLen = i - pathOffset;
                        break;
                    }
                }
                assert pathLen >= 0;
                pathBuf = PythonUtils.arrayCopyOfRange(data, pathOffset, pathOffset + pathLen);
            }
            return new UnixSockAddr(pathBuf);
        }

        long getConstant(NFIPosixConstants constant) {
            return nfiPosixSupport.getConstant(constant);
        }

        int getLen() {
            return len[0];
        }

        void setLen(int len) {
            this.len[0] = len;
        }

    }

    @ExportMessage
    long semOpen(Object name, int openFlags, int mode, int value,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        Object ptr = invokeNode.call(this, PosixNativeFunction.call_sem_open, pathToCString(name), openFlags, mode, value);
        if (invokeNode.getResultInterop().isNull(ptr)) {
            throw getErrnoAndThrowPosixException(invokeNode);
        }
        try {
            return invokeNode.getResultInterop().asPointer(ptr);
        } catch (UnsupportedMessageException e) {
            throw CompilerDirectives.shouldNotReachHere(e);
        }
    }

    @ExportMessage
    void semClose(long handle,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        int res = invokeNode.callInt(this, PosixNativeFunction.call_sem_close, handle);
        if (res < 0) {
            throw getErrnoAndThrowPosixException(invokeNode);
        }
    }

    @ExportMessage
    void semUnlink(Object name,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        int res = invokeNode.callInt(this, PosixNativeFunction.call_sem_unlink, pathToCString(name));
        if (res < 0) {
            throw getErrnoAndThrowPosixException(invokeNode);
        }
    }

    private static final UnsupportedPosixFeatureException NO_SEM_GETVALUE_EXCEPTION = new UnsupportedPosixFeatureException("sem_getvalue is not available on the current platform");

    @ExportMessage
    int semGetValue(long handle,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        /*
         * msimacek: It works on Linux, and it doesn't work on Darwin. It might work on some other
         * Unix-likes, but it's hard to check, so let's assume it only works on Linux for now
         */
        if (PythonOS.getPythonOS() != PythonOS.PLATFORM_LINUX) {
            throw NO_SEM_GETVALUE_EXCEPTION;
        }
        int[] value = new int[1];
        int res = invokeNode.callInt(this, PosixNativeFunction.call_sem_getvalue, handle, value);
        if (res < 0) {
            throw getErrnoAndThrowPosixException(invokeNode);
        }
        return value[0];
    }

    @ExportMessage
    void semPost(long handle,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        int res = invokeNode.callInt(this, PosixNativeFunction.call_sem_post, handle);
        if (res < 0) {
            throw getErrnoAndThrowPosixException(invokeNode);
        }
    }

    @ExportMessage
    void semWait(long handle,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        int res = invokeNode.callInt(this, PosixNativeFunction.call_sem_wait, handle);
        if (res < 0) {
            throw getErrnoAndThrowPosixException(invokeNode);
        }
    }

    @ExportMessage
    boolean semTryWait(long handle,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        int res = invokeNode.callInt(this, PosixNativeFunction.call_sem_trywait, handle);
        if (res < 0) {
            int errno = getErrno(invokeNode);
            if (errno == OSErrorEnum.EAGAIN.getNumber()) {
                return false;
            }
            throw newPosixException(invokeNode, errno);
        }
        return true;
    }

    @ExportMessage
    boolean semTimedWait(long handle, long deadlineNs,
                    @Bind Node node,
                    @CachedLibrary("this") PosixSupportLibrary thisLib,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        if (PythonOS.getPythonOS() == PythonOS.PLATFORM_LINUX) {
            int res = invokeNode.callInt(this, PosixNativeFunction.call_sem_timedwait, handle, deadlineNs);
            if (res < 0) {
                int errno = getErrno(invokeNode);
                if (errno == OSErrorEnum.ETIMEDOUT.getNumber()) {
                    return false;
                }
                throw newPosixException(invokeNode, errno);
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
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode,
                    @Shared("tsFromBytes") @Cached TruffleString.FromByteArrayNode fromByteArrayNode,
                    @Shared("fromUtf8") @Cached TruffleString.SwitchEncodingNode switchEncodingFromUtf8Node) throws PosixException {
        return getpw(PosixNativeFunction.call_getpwuid_r, uid, invokeNode, fromByteArrayNode, switchEncodingFromUtf8Node);
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    public PwdResult getpwnam(Object name,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode,
                    @Shared("tsFromBytes") @Cached TruffleString.FromByteArrayNode fromByteArrayNode,
                    @Shared("fromUtf8") @Cached TruffleString.SwitchEncodingNode switchEncodingFromUtf8Node) throws PosixException {
        return getpw(PosixNativeFunction.call_getpwname_r, pathToCString(name), invokeNode, fromByteArrayNode, switchEncodingFromUtf8Node);
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    public boolean hasGetpwentries() {
        return true;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    public PwdResult[] getpwentries(
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode,
                    @Shared("tsFromBytes") @Cached TruffleString.FromByteArrayNode fromByteArrayNode,
                    @Shared("fromUtf8") @Cached TruffleString.SwitchEncodingNode switchEncodingFromUtf8Node) throws PosixException {
        // Note: this is not thread safe, so potentially problematic while running multiple contexts
        // within one VM
        int sysConfMax = getSysConfPwdSizeMax(invokeNode);
        int initialBufferSize = sysConfMax == -1 ? 1024 : sysConfMax;

        ArrayList<PwdResult> result = new ArrayList<>();
        invokeNode.call(this, PosixNativeFunction.call_setpwent);
        long[] bufferSize = new long[1];
        long[] output = new long[PWD_OUTPUT_LEN];
        byte[] buffer = new byte[initialBufferSize];
        try {
            while (true) {
                Object pwPtr = invokeNode.call(this, PosixNativeFunction.call_getpwent, bufferSize);
                if (invokeNode.getResultInterop().isNull(pwPtr)) {
                    break;
                }
                if (bufferSize[0] < 0 || bufferSize[0] > PWD_BUFFER_MAX_SIZE) {
                    throw outOfMemoryPosixError();
                }
                if (buffer.length < bufferSize[0]) {
                    buffer = new byte[(int) bufferSize[0]];
                }
                int code = invokeNode.callInt(this, PosixNativeFunction.get_getpwent_data, pwPtr, buffer, buffer.length, output);
                if (code != 0) {
                    throw CompilerDirectives.shouldNotReachHere("get_getpwent_data failed");
                }
                result.add(createPwdResult(buffer, output, fromByteArrayNode, switchEncodingFromUtf8Node));
            }
        } finally {
            invokeNode.call(this, PosixNativeFunction.call_endpwent);
        }
        return toPwdResultArray(result);
    }

    @TruffleBoundary
    private static PwdResult[] toPwdResultArray(ArrayList<PwdResult> result) {
        return result.toArray(new PwdResult[0]);
    }

    private PwdResult getpw(PosixNativeFunction pwfun, Object pwfunArg, InvokeNativeFunction invokeNode, TruffleString.FromByteArrayNode fromByteArrayNode,
                    TruffleString.SwitchEncodingNode switchEncodingFromUtf8Node) throws PosixException {
        int sysConfMax = getSysConfPwdSizeMax(invokeNode);
        int bufferSize = sysConfMax == -1 ? 1024 : sysConfMax;
        while (bufferSize < PWD_BUFFER_MAX_SIZE) {
            byte[] data = new byte[bufferSize];
            long[] output = new long[PWD_OUTPUT_LEN];
            int result = invokeNode.callInt(this, pwfun, pwfunArg, data, data.length, output);
            if (result == -1) {
                return null;
            }
            if (result == 0) {
                return createPwdResult(data, output, fromByteArrayNode, switchEncodingFromUtf8Node);
            }
            if (result != OSErrorEnum.ERANGE.getNumber() || sysConfMax != -1) {
                // no point in trying larger buffer if we got different error or the OS already told
                // us that sysConfMax should be enough...
                throw newPosixException(invokeNode, result);
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
    public int ioctlBytes(int fd, long request, byte[] arg,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        int res = invokeNode.callInt(this, PosixNativeFunction.call_ioctl_bytes, fd, request, arg);
        if (res < 0) {
            throw newPosixException(invokeNode, getErrno(invokeNode));
        }
        return res;
    }

    @ExportMessage
    public int ioctlInt(int fd, long request, int arg,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        int res = invokeNode.callInt(this, PosixNativeFunction.call_ioctl_int, fd, request, arg);
        if (res < 0) {
            throw newPosixException(invokeNode, getErrno(invokeNode));
        }
        return res;
    }

    private static TruffleString extractZeroTerminatedString(byte[] buffer, long longOffset, TruffleString.FromByteArrayNode fromByteArrayNode,
                    TruffleString.SwitchEncodingNode switchEncodingFromUtf8Node) throws PosixException {
        if (longOffset < 0 || longOffset >= buffer.length) {
            throw outOfMemoryPosixError();
        }
        int offset = (int) longOffset;
        int end = offset;
        while (end < buffer.length && buffer[end] != '\0') {
            end++;
        }
        if (end == buffer.length) {
            throw CompilerDirectives.shouldNotReachHere("Could not find the end of the string");
        }
        // TODO PyUnicode_DecodeFSDefault
        return createString(buffer, offset, end - offset, true, fromByteArrayNode, switchEncodingFromUtf8Node);
    }

    private static PosixException outOfMemoryPosixError() throws PosixException {
        throw new PosixException(OSErrorEnum.ENOMEM.getNumber(), OSErrorEnum.ENOMEM.getMessage());
    }

    private int sysConfPwdSizeMax = -1;

    private int getSysConfPwdSizeMax(InvokeNativeFunction invokeNode) throws PosixException {
        if (CompilerDirectives.injectBranchProbability(SLOWPATH_PROBABILITY, sysConfPwdSizeMax == -1)) {
            long sysConfMaxLong = invokeNode.callLong(this, PosixNativeFunction.get_sysconf_getpw_r_size_max);
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
                    @Shared("toUtf8") @Cached TruffleString.SwitchEncodingNode switchEncodingNode,
                    @Shared("tsCopyBytes") @Cached TruffleString.CopyToByteArrayNode copyToByteArrayNode) {
        return checkPath(getStringBytes(path, switchEncodingNode, copyToByteArrayNode));
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    public Object createPathFromBytes(byte[] path) {
        return checkPath(path);
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    public TruffleString getPathAsString(Object path,
                    @Shared("tsFromBytes") @Cached TruffleString.FromByteArrayNode fromByteArrayNode,
                    @Shared("fromUtf8") @Cached TruffleString.SwitchEncodingNode switchEncodingNode) {
        Buffer result = (Buffer) path;
        if (result.length > Integer.MAX_VALUE) {
            // sanity check that it is safe to cast result.length to int, to be removed once
            // we support large arrays
            throw CompilerDirectives.shouldNotReachHere("Posix path cannot fit into a Java array");
        }
        return createString(result.data, 0, (int) result.length, true, fromByteArrayNode, switchEncodingNode);
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    public Buffer getPathAsBytes(Object path) {
        return (Buffer) path;
    }

    private static TruffleString createString(byte[] src, int offset, int length, boolean copy, TruffleString.FromByteArrayNode fromByteArrayNode,
                    TruffleString.SwitchEncodingNode switchEncodingNode) {
        // TODO PyUnicode_DecodeFSDefault
        TruffleString utf8 = fromByteArrayNode.execute(src, offset, length, UTF_8, copy);
        return switchEncodingNode.execute(utf8, TS_ENCODING);
    }

    private static byte[] getStringBytes(TruffleString str, TruffleString.SwitchEncodingNode switchEncodingNode, TruffleString.CopyToByteArrayNode copyToByteArrayNode) {
        // TODO replace getBytes with PyUnicode_FSConverter equivalent
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

    private int getErrno(InvokeNativeFunction invokeNode) {
        return invokeNode.callInt(this, PosixNativeFunction.get_errno);
    }

    private void setErrno(InvokeNativeFunction invokeNode, int errno) {
        invokeNode.call(this, PosixNativeFunction.set_errno, errno);
    }

    private PosixException getErrnoAndThrowPosixException(InvokeNativeFunction invokeNode) throws PosixException {
        throw newPosixException(invokeNode, getErrno(invokeNode));
    }

    @TruffleBoundary
    private PosixException newPosixException(InvokeNativeFunction invokeNode, int errno) throws PosixException {
        throw new PosixException(errno, strerror(errno, invokeNode, TruffleString.FromByteArrayNode.getUncached(), TruffleString.SwitchEncodingNode.getUncached()));
    }

    private Object wrap(long[] value) {
        if (value == null) {
            return PNone.NO_VALUE;
        } else {
            return value;
        }
    }

    private Object wrap(Timeval[] timeval) {
        if (timeval == null) {
            return PNone.NO_VALUE;
        } else {
            return new long[]{timeval[0].getSeconds(), timeval[0].getMicroseconds(), timeval[1].getSeconds(), timeval[1].getMicroseconds()};
        }
    }

    private static TruffleString cStringToTruffleString(byte[] buf, TruffleString.FromByteArrayNode fromByteArrayNode, TruffleString.SwitchEncodingNode switchEncodingNode) {
        return createString(buf, 0, findZero(buf), true, fromByteArrayNode, switchEncodingNode);
    }

    private static int findZero(byte[] buf) {
        for (int i = 0; i < buf.length; ++i) {
            if (buf[i] == 0) {
                return i;
            }
        }
        return buf.length;
    }

    private Object pathToCStringOrNull(Object path) {
        return path == null ? PNone.NO_VALUE : bufferToCString((Buffer) path);
    }

    private Object pathToCString(Object path) {
        return bufferToCString((Buffer) path);
    }

    private Object bufferToCString(Buffer path) {
        return nullTerminate(path.data, (int) path.length);
    }

    private Object stringToUTF8CString(TruffleString input,
                    @Cached TruffleString.SwitchEncodingNode switchEncodingToUtf8Node,
                    @Cached TruffleString.CopyToByteArrayNode copyToByteArrayNode) {
        byte[] utf8 = getStringBytes(input, switchEncodingToUtf8Node, copyToByteArrayNode);
        return nullTerminate(utf8, utf8.length);
    }

    private static byte[] nullTerminate(byte[] str, int length) {
        byte[] terminated = new byte[length + 1];
        PythonUtils.arraycopy(str, 0, terminated, 0, length);
        return terminated;
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
