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

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.invoke.MethodHandle;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

public final class NfiUpcallSignature {
    private final NfiType resType;
    private final NfiType[] argTypes;

    @SuppressWarnings("restricted")
    NfiUpcallSignature(NfiType resType, NfiType[] argTypes) {
        this.resType = resType;
        this.argTypes = argTypes;
    }

    public NfiType[] getArgTypes() {
        return argTypes;
    }

    public NfiType getReturnType() {
        return resType;
    }

    @SuppressWarnings({"unused", "restricted"})
    public long createClosure(NfiContext context, String name, MethodHandle staticMethodHandle) {
        Class<?>[] javaArgTypes = new Class<?>[argTypes.length];
        for (int i = 0; i < argTypes.length; i++) {
            javaArgTypes[i] = argTypes[i].asJavaType();
        }
        FunctionDescriptor functionDescriptor = NfiContext.createFunctionDescriptor(resType, argTypes);
        return Linker.nativeLinker().upcallStub(staticMethodHandle, functionDescriptor, context.arena).address();
    }

    @SuppressWarnings("unused")
    private static Object closureLoggingWrapper(String name, NfiUpcallSignature signature, MethodHandle inner, Object[] args) {
        // TODO(NFI2) implement logging
        try {
            return inner.invoke(args);
        } catch (Throwable e) {
            throw CompilerDirectives.shouldNotReachHere(e);
        }
    }

    @Override
    @TruffleBoundary
    public String toString() {
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
