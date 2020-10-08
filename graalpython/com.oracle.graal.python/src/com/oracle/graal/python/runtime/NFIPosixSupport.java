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
import com.oracle.graal.python.runtime.NativeLibrary.InvokeNativeFunction;
import com.oracle.graal.python.runtime.NativeLibrary.NativeFunction;
import com.oracle.graal.python.runtime.NativeLibrary.TypedNativeLibrary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

/**
 * Implementation that invokes the native POSIX functions directly using NFI. This requires either
 * that the native access is allowed or to configure managed LLVM backend for NFI.
 */
@ExportLibrary(PosixSupportLibrary.class)
public final class NFIPosixSupport {
    private static final String SUPPORTING_NATIVE_LIB_NAME = "libposix";

    enum NativeFunctions implements NativeFunction {
        call_getpid("():sint64"),
        call_umask("(sint64):sint64"),
        call_open("(string, sint32):sint32"),
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
    public int open(String pathname, int flags,
                    @Shared("invoke") @Cached InvokeNativeFunction invokeNode) {
        // TODO error handling
        return invokeNode.callInt(lib, NativeFunctions.call_open, pathname, flags);
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
}
