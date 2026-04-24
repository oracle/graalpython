/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.runtime.nativeaccess;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import org.graalvm.nativeimage.ImageInfo;

import java.lang.invoke.MethodHandle;
import java.lang.ref.Reference;

public final class NfiBoundFunction {
    private final long ptr;
    private final MethodHandle boundHandle;
    private final NfiType resType;
    private final NfiType[] argTypes;

    private NfiBoundFunction(long ptr, MethodHandle boundHandle, NfiType resType, NfiType[] argTypes) {
        this.ptr = ptr;
        this.boundHandle = boundHandle;
        this.resType = resType;
        this.argTypes = argTypes;
    }

    public static NfiBoundFunction create(@SuppressWarnings("unused") NfiContext context, long pointer, NfiType resType, NfiType... argTypes) {
        // TODO(NFI2) if logging enabled, use context to lookup name
        return new NfiBoundFunction(pointer, NfiSupport.createBoundHandle(pointer, resType, argTypes), resType, argTypes.clone());
    }

    public long getAddress() {
        return ptr;
    }

    @TruffleBoundary(allowInlining = true)
    public Object invoke(Object... args) {
        assert checkArgTypes(args);
        try {
            return boundHandle.invokeExact(args);
        } catch (Throwable e) {
            throw CompilerDirectives.shouldNotReachHere(e);
        } finally {
            Reference.reachabilityFence(args);
        }
    }

    boolean checkArgTypes(Object[] args) {
        if (args.length != argTypes.length) {
            return false;
        }
        for (int i = 0; i < args.length; i++) {
            if (!argTypes[i].checkType(args[i])) {
                return false;
            }
        }
        return true;
    }

    @Override
    @TruffleBoundary
    public String toString() {
        String signature = toSignatureString();
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

    @TruffleBoundary
    private String toSignatureString() {
        StringBuilder sb = new StringBuilder("(");
        for (int i = 0; i < argTypes.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(argTypes[i]);
        }
        sb.append("): ");
        sb.append(resType);
        return sb.toString();
    }
}
