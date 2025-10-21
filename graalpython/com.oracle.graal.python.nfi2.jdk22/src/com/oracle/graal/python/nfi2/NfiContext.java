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

import static com.oracle.truffle.api.CompilerDirectives.shouldNotReachHere;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

import org.graalvm.nativeimage.DowncallDescriptor;
import org.graalvm.nativeimage.ForeignFunctions;
import org.graalvm.nativeimage.ImageInfo;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

public final class NfiContext {

    final Arena arena;

    @TruffleBoundary
    NfiContext() {
        // TODO(NFI2) is shared Arena OK?
        arena = Arena.ofShared();
    }

    public void close() {
        // TODO(NFI2) dlclose all libraries
        arena.close();
    }

    public NfiLibrary loadLibrary(String name, int flags) {
        long lib;
        long nativeName = NativeMemory.javaStringToNativeUtf8(name);
        try {
            ensureDlopenDlsym();
            // TODO(NFI2) only add RTLD_LAZY flag if actually needed
            if (ImageInfo.inImageCode()) {
                lib = (long) ForeignFunctions.invoke(dlopenDescriptor, dlopenPtr.address(), nativeName, flags | RTLD_LAZY);
            } else {
                lib = (long) dlopen.invokeExact(nativeName, flags | RTLD_LAZY);
            }
        } catch (Throwable e) {
            throw CompilerDirectives.shouldNotReachHere(e);
        } finally {
            NativeMemory.free(nativeName);
        }
        if (lib == 0) {
            throw CompilerDirectives.shouldNotReachHere("Failed to load library " + name);
        }
        return new NfiLibrary(this, lib);
    }

    long lookupOptionalSymbol(long library, String name) {
        // TODO(NFI2) if logging enabled, keep track of ptr->name mappings
        long nativeName = NativeMemory.javaStringToNativeUtf8(name);
        try {
            if (ImageInfo.inImageCode()) {
                return (long) ForeignFunctions.invoke(dlsymDescriptor, dlsymPtr.address(), library, nativeName);
            } else {
                return (long) dlsym.invokeExact(library, nativeName);
            }
        } catch (Throwable e) {
            throw CompilerDirectives.shouldNotReachHere(e);
        } finally {
            NativeMemory.free(nativeName);
        }
    }

    // TODO(NFI2) platform-specific values for RTLD_* constants
    private static final int RTLD_LAZY = 1;

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
    // TODO(NFI2) Windows LoadLibrary/GetProcAddress
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

    static FunctionDescriptor createFunctionDescriptor(NfiType resType, NfiType[] argTypes) {
        MemoryLayout[] argLayouts = new MemoryLayout[argTypes.length];
        for (int i = 0; i < argTypes.length; i++) {
            argLayouts[i] = asLayout(argTypes[i]);
        }
        return resType == NfiType.VOID ? FunctionDescriptor.ofVoid(argLayouts) : FunctionDescriptor.of(asLayout(resType), argLayouts);
    }

    private static MemoryLayout asLayout(NfiType type) {
        return switch (type) {
            case VOID -> throw shouldNotReachHere("VOID has no layout");
            case SINT8 -> ValueLayout.JAVA_BYTE;
            case SINT16 -> ValueLayout.JAVA_SHORT;
            case SINT32 -> ValueLayout.JAVA_INT;
            case SINT64 -> ValueLayout.JAVA_LONG;
            case FLOAT -> ValueLayout.JAVA_FLOAT;
            case DOUBLE -> ValueLayout.JAVA_DOUBLE;
            case POINTER, RAW_POINTER -> ValueLayout.JAVA_LONG;
        };
    }
}
