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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import org.graalvm.nativeimage.ImageInfo;

public abstract class NativeAccessSupport {
    private static final NativeAccessSupport INSTANCE = createImpl();

    protected NativeAccessSupport() {
    }

    private static NativeAccessSupport createImpl() {
        if (!ImageInfo.inImageCode() && Runtime.version().feature() < 22) {
            return new NativeAccessSupportJdk21();
        }
        return new NativeAccessSupportJdk22Gen();
    }

    protected static UnsupportedOperationException unsupported() {
        return new UnsupportedOperationException(NativeContext.UNAVAILABLE);
    }

    protected static MethodHandle unsupportedDowncallHandle(MethodType methodType) {
        MethodHandle methodHandle = MethodHandles.throwException(methodType.returnType(), UnsupportedOperationException.class);
        methodHandle = MethodHandles.insertArguments(methodHandle, 0, unsupported());
        return MethodHandles.dropArguments(methodHandle, 0, methodType.parameterList());
    }

    protected static Class<?> asJavaType(NativeSimpleType type) {
        return switch (type) {
            case VOID -> void.class;
            case SINT8 -> byte.class;
            case SINT16 -> short.class;
            case SINT32 -> int.class;
            case SINT64 -> long.class;
            case FLOAT -> float.class;
            case DOUBLE -> double.class;
            case RAW_POINTER -> long.class;
        };
    }

    static Object createArena() {
        return INSTANCE.createArenaImpl();
    }

    static void closeArena(Object arena) {
        INSTANCE.closeArenaImpl(arena);
    }

    static NativeLibraryLookup libraryLookup(String name, Object arena) {
        return INSTANCE.libraryLookupImpl(name, arena);
    }

    static long lookupSymbol(NativeLibraryLookup lookup, String name) {
        return lookup.find(name).orElseThrow();
    }

    static long lookupDefault(String name) {
        return INSTANCE.lookupDefaultImpl(name);
    }

    static MethodHandle createDowncallHandle(NativeSimpleType resType, NativeSimpleType... argTypes) {
        return INSTANCE.createTypedDowncallHandle(resType, argTypes);
    }

    public static MethodHandle createDowncallHandle(MethodType methodType, boolean critical) {
        return INSTANCE.createDowncallHandleImpl(methodType, critical);
    }

    public static boolean isAvailable() {
        return INSTANCE.isAvailableImpl();
    }

    static long createClosure(MethodHandle staticMethodHandle, NativeSimpleType resType, NativeSimpleType[] argTypes, Object arena) {
        return INSTANCE.createClosureImpl(staticMethodHandle, resType, argTypes, arena);
    }

    public static boolean isCurrentThreadVirtual() {
        return INSTANCE.isCurrentThreadVirtualImpl();
    }

    private MethodHandle createTypedDowncallHandle(NativeSimpleType resType, NativeSimpleType... argTypes) {
        Class<?>[] parameterTypes = new Class<?>[argTypes.length + 1];
        parameterTypes[0] = long.class;
        for (int i = 0; i < argTypes.length; i++) {
            parameterTypes[i + 1] = asJavaType(argTypes[i]);
        }
        return createDowncallHandleImpl(MethodType.methodType(asJavaType(resType), parameterTypes), false);
    }

    protected abstract Object createArenaImpl();

    protected abstract void closeArenaImpl(Object arena);

    protected abstract NativeLibraryLookup libraryLookupImpl(String name, Object arena);

    protected abstract long lookupDefaultImpl(String name);

    protected abstract MethodHandle createDowncallHandleImpl(MethodType methodType, boolean critical);

    protected abstract long createClosureImpl(MethodHandle staticMethodHandle, NativeSimpleType resType, NativeSimpleType[] argTypes, Object arena);

    protected boolean isAvailableImpl() {
        return true;
    }

    protected abstract boolean isCurrentThreadVirtualImpl();
}
