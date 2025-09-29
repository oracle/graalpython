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
package com.oracle.graal.python.nfi;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;

import org.graalvm.nativeimage.DowncallDescriptor;
import org.graalvm.nativeimage.ForeignFunctions;
import org.graalvm.nativeimage.ImageInfo;

import com.oracle.graal.python.runtime.PosixConstants;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.interop.UnknownIdentifierException;

import sun.misc.Unsafe;

public final class Nfi2 {

    private static final FunctionDescriptor DLOPEN_FUNCTION_DESCRIPTOR = FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT);
    private static final FunctionDescriptor DLSYM_FUNCTION_DESCRIPTOR = FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG);
    private static final DowncallDescriptor dlopenDescriptor;
    private static final DowncallDescriptor dlsymDescriptor;

    private static MethodHandle dlopen;
    private static MethodHandle dlsym;

    private static MemorySegment dlopenPtr;
    private static MemorySegment dlsymPtr;

    static {
        if (ImageInfo.inImageCode()) {
            dlopenDescriptor = ForeignFunctions.getDowncallDescriptor(DLOPEN_FUNCTION_DESCRIPTOR);
            dlsymDescriptor = ForeignFunctions.getDowncallDescriptor(DLSYM_FUNCTION_DESCRIPTOR);
        } else {
            dlopenDescriptor = null;
            dlsymDescriptor = null;
        }
    }

    // TODO(NFI2) error handling
    @SuppressWarnings("restricted")
    private static void ensureDlopenDlsym() {
        if (dlopenPtr != null) {
            return;
        }
        dlopenPtr = Linker.nativeLinker().defaultLookup().find("dlopen").get();
        dlsymPtr = Linker.nativeLinker().defaultLookup().find("dlsym").get();
        if (!ImageInfo.inImageCode()) {
            dlopen = Linker.nativeLinker().downcallHandle(dlopenPtr, DLOPEN_FUNCTION_DESCRIPTOR);
            dlsym = Linker.nativeLinker().downcallHandle(dlsymPtr, DLSYM_FUNCTION_DESCRIPTOR);
        }
    }

    public static long loadLibraryUncached(String name, int flags) {
        long lib;
        long nativeName = javaStringToNativeUtf8(name);
        try {
            ensureDlopenDlsym();
            // TODO(NFI2) only add RTLD_LAZY flag if actually needed
            if (ImageInfo.inImageCode()) {
                lib = (long) ForeignFunctions.invoke(dlopenDescriptor, dlopenPtr.address(), nativeName, flags | PosixConstants.RTLD_LAZY.value);
            } else {
                lib = (long) dlopen.invokeExact(nativeName, flags | PosixConstants.RTLD_LAZY.value);
            }
        } catch (Throwable e) {
            // TODO(NFI2) proper exception handling
            throw CompilerDirectives.shouldNotReachHere(e);
        } finally {
            free(nativeName);
        }
        if (lib == 0) {
            throw CompilerDirectives.shouldNotReachHere("Failed to load library " + name);
        }
        return lib;
    }

    public static long lookupSymbolUncached(long library, String name) throws UnknownIdentifierException {
        long symbol;
        long nativeName = javaStringToNativeUtf8(name);
        try {
            ensureDlopenDlsym();
            if (ImageInfo.inImageCode()) {
                symbol = (long) ForeignFunctions.invoke(dlsymDescriptor, dlsymPtr.address(), library, nativeName);
            } else {
                symbol = (long) dlsym.invokeExact(library, nativeName);
            }
        } catch (Throwable e) {
            throw CompilerDirectives.shouldNotReachHere(e);
        } finally {
            free(nativeName);
        }
        if (symbol == 0) {
            throw UnknownIdentifierException.create("symbol " + name + " not found");
        }
        return symbol;
    }

    public static NfiSignature createSignatureUncached(NfiType resType, NfiType... argTypes) {
        // TODO(NFI2) should we cache signatures?
        return new NfiSignature(resType, argTypes);
    }

    static final Unsafe UNSAFE = initUnsafe();

    private static Unsafe initUnsafe() {
        try {
            // Fast path when we are trusted.
            return Unsafe.getUnsafe();
        } catch (SecurityException se) {
            // Slow path when we are not trusted.
            try {
                Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
                theUnsafe.setAccessible(true);
                return (Unsafe) theUnsafe.get(Unsafe.class);
            } catch (Exception e) {
                throw CompilerDirectives.shouldNotReachHere("exception while trying to get Unsafe", e);
            }
        }
    }

    static long javaStringToNativeUtf8(String s) {
        byte[] utf8 = s.getBytes(StandardCharsets.UTF_8);
        long ptr = malloc(utf8.length + 1);
        UNSAFE.copyMemory(utf8, UNSAFE.arrayBaseOffset(byte[].class), null, ptr, utf8.length);
        UNSAFE.putByte(ptr + utf8.length, (byte) 0);
        return ptr;
    }

    public static long malloc(long size) {
        assert size > 0;
        return UNSAFE.allocateMemory(size);
    }

    public static void free(long ptr) {
        UNSAFE.freeMemory(ptr);
    }
}
