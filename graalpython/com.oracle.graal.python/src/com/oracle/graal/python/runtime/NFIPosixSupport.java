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

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PArguments.ThreadState;
import com.oracle.graal.python.nodes.PConstructAndRaiseNode;
import com.oracle.graal.python.runtime.NativeLibrary.InvokeNativeFunction;
import com.oracle.graal.python.runtime.NativeLibrary.NativeFunction;
import com.oracle.graal.python.runtime.NativeLibrary.TypedNativeLibrary;
import com.oracle.graal.python.runtime.PosixSupportLibrary.PosixException;
import com.oracle.graal.python.runtime.PosixSupportLibrary.PosixPath;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

/**
 * Implementation that invokes the native POSIX functions directly using NFI. This requires either
 * that the native access is allowed or to configure managed LLVM backend for NFI.
 */
@ExportLibrary(PosixSupportLibrary.class)
public final class NFIPosixSupport {
    private static final String SUPPORTING_NATIVE_LIB_NAME = "libposix";

    private static final int EINTR = 4;

    enum NativeFunctions implements NativeFunction {
        get_errno("():sint32"),
        call_strerror("(sint32, [sint8], sint32):sint32"),
        call_getpid("():sint64"),
        call_umask("(sint64):sint64"),
        call_open_at("(sint32, string, sint32, sint32):sint32"),
        call_close("(sint32):sint32"),
        call_read("(sint32, [sint8], uint64):sint64");

        private final String signature;

        NativeFunctions(String signature) {
            this.signature = signature;
        }

        @Override
        public String signature() {
            return signature;
        }
    }

    private final TypedNativeLibrary<NativeFunctions> lib;

    private NFIPosixSupport(String backend) {
        lib = NativeLibrary.create(SUPPORTING_NATIVE_LIB_NAME, NativeFunctions.values(), backend);
    }

    public static NFIPosixSupport createNative() {
        return new NFIPosixSupport(null);
    }

    public static NFIPosixSupport createLLVM() {
        return new NFIPosixSupport("llvm");
    }

    @ExportMessage
    public long getpid(@Shared("invoke") @Cached InvokeNativeFunction invokeNode) {
        return invokeNode.callLong(lib, NativeFunctions.call_getpid);
    }

    @ExportMessage
    public long umask(long mask,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) {
        long result = invokeNode.callLong(lib, NativeFunctions.call_umask, mask);
        if (result < 0) {
            // TODO call errno() and raise OS error
            // create helper method for this (like CPython's posix_error)
        }
        return result;
    }

    @ExportMessage
    public int openAt(int dirFd, PosixPath pathname, int flags, int mode,
                    @CachedContext(PythonLanguage.class) PythonContext ctx,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) throws PosixException {
        // TODO C-string proxy instead of newString()
        while (true) {
            int fd = invokeNode.callInt(lib, NativeFunctions.call_open_at, dirFd, PythonUtils.newString(pathname.path), flags, mode);
            if (fd >= 0) {
                // TODO set inheritable, O_CLOEXEC support etc.
                return fd;
            }
            int errno = errno(invokeNode);
            if (errno != EINTR) {
                throw new PosixException(errno, strerror(ctx, invokeNode, errno), pathname.originalObject);
            }
            // TODO check signals
        }
    }

    @ExportMessage
    public int close(int fd,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) {
        // TODO error handling
        return invokeNode.callInt(lib, NativeFunctions.call_close, fd);
    }

    @ExportMessage
    public long read(int fd, byte[] buf,
                    @CachedContext(PythonLanguage.class) PythonContext ctx,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) {
        // TODO error handling
        return invokeNode.callLong(lib, NativeFunctions.call_read, fd, ctx.getEnv().asGuestValue(buf), buf.length);
    }

    private int errno(InvokeNativeFunction invokeNode) {
        return invokeNode.callInt(lib, NativeFunctions.get_errno);
    }

    private String strerror(PythonContext ctx, InvokeNativeFunction invokeNode, int error) {
        // From man pages: The GNU C Library uses a buffer of 1024 characters for strerror().
        // This buffer size therefore should be sufficient to avoid an ERANGE error when calling strerror_r().
        byte[] buf = new byte[1024];
        int result = invokeNode.callInt(lib, NativeFunctions.call_strerror, error, ctx.getEnv().asGuestValue(buf), buf.length);
        if (result != 0) {
            return "Unknown error";
        }
        return cStringToJavaString(buf);
    }

    private static String cStringToJavaString(byte[] buf) {
        for (int i = 0; i < buf.length; ++i) {
            if (buf[i] == 0) {
                return PythonUtils.newString(buf, 0, i);
            }
        }
        return PythonUtils.newString(buf);
    }
}
