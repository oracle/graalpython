/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nfi2;

import java.lang.invoke.MethodHandle;
import java.lang.ref.Reference;

import org.graalvm.nativeimage.ForeignFunctions;
import org.graalvm.nativeimage.ImageInfo;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

public final class NfiBoundFunction {
    private final long ptr;
    private final MethodHandle boundHandle;
    private final NfiDowncallSignature signature;

    NfiBoundFunction(long ptr, MethodHandle boundHandle, NfiDowncallSignature signature) {
        this.ptr = ptr;
        this.boundHandle = boundHandle;
        this.signature = signature;
    }

    public long getAddress() {
        return ptr;
    }

    @TruffleBoundary
    public Object invoke(Object... args) {
        Object[] convertedArgs = signature.convertArgs(args);
        Object result;
        try {
            if (ImageInfo.inImageCode()) {
                result = ForeignFunctions.invoke(signature.downcallDescriptor, ptr, convertedArgs);
            } else {
                result = boundHandle.invokeExact(convertedArgs);
            }
        } catch (Throwable e) {
            throw CompilerDirectives.shouldNotReachHere(e);
        } finally {
            Reference.reachabilityFence(convertedArgs);
            Reference.reachabilityFence(args);
        }
        return signature.convertResult(result);
    }

    @Override
    @TruffleBoundary
    public String toString() {
        if (ImageInfo.inImageCode()) {
            return "NfiBoundFunction[" +
                            "ptr=" + ptr + ", " +
                            "signature=" + signature + ']';
        } else {
            return "NfiBoundFunction[" +
                            "ptr=" + ptr + ", " +
                            "boundHandle=" + boundHandle + ", " +
                            "signature=" + signature + ']';
        }
    }
}
