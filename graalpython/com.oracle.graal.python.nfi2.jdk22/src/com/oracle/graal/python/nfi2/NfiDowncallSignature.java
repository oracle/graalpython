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
package com.oracle.graal.python.nfi2;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.ref.Reference;
import java.util.Arrays;
import java.util.Objects;

import org.graalvm.nativeimage.ImageInfo;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

public final class NfiDowncallSignature {

    static final MethodType DOWNCALL_METHOD_TYPE = MethodType.methodType(Object.class, new Class<?>[]{long.class, Object[].class});

    private static final MethodHandle OF_ADDRESS;

    static {
        try {
            OF_ADDRESS = MethodHandles.lookup().findStatic(
                    MemorySegment.class,
                    "ofAddress",
                    MethodType.methodType(MemorySegment.class, long.class)
            );
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }


    private final NfiType resType;
    private final NfiType[] argTypes;
    private final MethodHandle downcallMethodHandle;

    @SuppressWarnings("restricted")
    NfiDowncallSignature(NfiType resType, NfiType[] argTypes) {
        this.resType = resType;
        this.argTypes = argTypes;
        FunctionDescriptor functionDescriptor = NfiContext.createFunctionDescriptor(resType, argTypes);
        MethodHandle methodHandle = Linker.nativeLinker().downcallHandle(functionDescriptor);
        methodHandle = MethodHandles.filterArguments(methodHandle, 0, OF_ADDRESS);
        methodHandle = methodHandle.asSpreader(1, Object[].class, argTypes.length);
        methodHandle = methodHandle.asType(DOWNCALL_METHOD_TYPE);
        downcallMethodHandle = methodHandle;
    }

    public static boolean isAvailable() {
        return true;
    }

    public MethodHandle getMethodHandle() {
        return downcallMethodHandle;
    }

    public NfiBoundFunction bind(@SuppressWarnings("unused") NfiContext context, long pointer) {
        // TODO(NFI2) if logging enabled, use context to lookup name
        assert !ImageInfo.inImageBuildtimeCode() : "binding native address ad image build time";
        MethodHandle boundMH = MethodHandles.insertArguments(downcallMethodHandle, 0, pointer);
        return new NfiBoundFunction(pointer, boundMH, this);
    }

    @TruffleBoundary(allowInlining = true)
    public Object invoke(@SuppressWarnings("unused") NfiContext context, long function, Object... args) {
        assert checkArgTypes(args);
        try {
            return downcallMethodHandle.invokeExact(function, args);
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

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        NfiDowncallSignature that = (NfiDowncallSignature) o;
        return resType == that.resType && Objects.deepEquals(argTypes, that.argTypes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(resType, Arrays.hashCode(argTypes));
    }
}
