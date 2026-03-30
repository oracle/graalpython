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

import static com.oracle.truffle.api.CompilerDirectives.shouldNotReachHere;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

public final class NfiContext {
    private static final boolean IS_WINDOWS = System.getProperty("os.name", "").startsWith("Windows");

    private final ConcurrentLinkedQueue<NfiLibrary> libraries = new ConcurrentLinkedQueue<>();
    final Arena arena;

    @TruffleBoundary
    NfiContext() {
        arena = Arena.ofShared();
    }

    public void close() {
        for (NfiLibrary library : libraries) {
            int result;
            try {
                result = IS_WINDOWS ? (int) FREE_LIBRARY.invokeExact(freeLibraryPtr, library.ptr) : (int) DLCLOSE.invokeExact(dlclosePtr, library.ptr);
            } catch (Throwable e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
            if (result != 0) {
                // TODO(NFI2) log error
            }
        }
        arena.close();
    }

    public NfiLibrary loadLibrary(String name, int flags) {
        long lib;
        long nativeName = IS_WINDOWS ? NativeMemory.javaStringToNativeUtf16(name) : NativeMemory.javaStringToNativeUtf8(name);
        try {
            ensureLoader();
            if (IS_WINDOWS) {
                lib = (long) LOAD_LIBRARY_EX.invokeExact(loadLibraryExPtr, nativeName, MemorySegment.NULL, 0);
            } else {
                int callFlags = flags;
                if ((callFlags & (RTLD_LAZY | RTLD_NOW)) == 0) {
                    callFlags |= RTLD_NOW;
                }
                lib = (long) DLOPEN.invokeExact(dlopenPtr, nativeName, callFlags);
            }
        } catch (Throwable e) {
            throw CompilerDirectives.shouldNotReachHere(e);
        } finally {
            NativeMemory.free(nativeName);
        }
        if (lib == 0) {
            throw CompilerDirectives.shouldNotReachHere("Failed to load library " + name);
        }
        NfiLibrary library = new NfiLibrary(this, lib);
        libraries.add(library);
        return library;
    }

    @SuppressWarnings("static-method")
    long lookupOptionalSymbol(long library, String name) {
        // TODO(NFI2) if logging enabled, keep track of ptr->name mappings
        long nativeName = NativeMemory.javaStringToNativeUtf8(name);
        try {
            return IS_WINDOWS ? (long) GET_PROC_ADDRESS.invokeExact(getProcAddressPtr, library, nativeName) : (long) DLSYM.invokeExact(dlsymPtr, library, nativeName);
        } catch (Throwable e) {
            throw CompilerDirectives.shouldNotReachHere(e);
        } finally {
            NativeMemory.free(nativeName);
        }
    }

    // TODO(NFI2) platform-specific values for RTLD_* constants
    private static final int RTLD_LAZY = 1;
    private static final int RTLD_NOW = 2;

    @SuppressWarnings("restricted") //
    private static final MethodHandle DLOPEN = Linker.nativeLinker().downcallHandle(FunctionDescriptor.of(JAVA_LONG, JAVA_LONG, JAVA_INT));
    @SuppressWarnings("restricted") //
    private static final MethodHandle DLCLOSE = Linker.nativeLinker().downcallHandle(FunctionDescriptor.of(JAVA_INT, JAVA_LONG));
    @SuppressWarnings("restricted") //
    private static final MethodHandle DLSYM = Linker.nativeLinker().downcallHandle(FunctionDescriptor.of(JAVA_LONG, JAVA_LONG, JAVA_LONG));
    @SuppressWarnings("restricted") //
    private static final MethodHandle LOAD_LIBRARY_EX = Linker.nativeLinker().downcallHandle(FunctionDescriptor.of(JAVA_LONG, JAVA_LONG, ValueLayout.ADDRESS, JAVA_INT));
    @SuppressWarnings("restricted") //
    private static final MethodHandle FREE_LIBRARY = Linker.nativeLinker().downcallHandle(FunctionDescriptor.of(JAVA_INT, JAVA_LONG));
    @SuppressWarnings("restricted") //
    private static final MethodHandle GET_PROC_ADDRESS = Linker.nativeLinker().downcallHandle(FunctionDescriptor.of(JAVA_LONG, JAVA_LONG, JAVA_LONG));

    private static MemorySegment dlopenPtr;
    private static MemorySegment dlclosePtr;
    private static MemorySegment dlsymPtr;
    private static MemorySegment loadLibraryExPtr;
    private static MemorySegment freeLibraryPtr;
    private static MemorySegment getProcAddressPtr;
    private static Arena windowsLookupArena;
    private static SymbolLookup windowsLookup;

    // TODO(NFI2) error handling
    @SuppressWarnings("restricted")
    private static void ensureLoader() {
        if (IS_WINDOWS) {
            if (loadLibraryExPtr != null) {
                assert freeLibraryPtr != null;
                assert getProcAddressPtr != null;
                return;
            }
            if (windowsLookup == null) {
                windowsLookupArena = Arena.ofShared();
                windowsLookup = SymbolLookup.libraryLookup("kernel32", windowsLookupArena);
            }
            loadLibraryExPtr = windowsLookup.find("LoadLibraryExW").orElseThrow();
            freeLibraryPtr = windowsLookup.find("FreeLibrary").orElseThrow();
            getProcAddressPtr = windowsLookup.find("GetProcAddress").orElseThrow();
            return;
        }
        dlopenPtr = Linker.nativeLinker().defaultLookup().find("dlopen").orElseThrow();
        dlclosePtr = Linker.nativeLinker().defaultLookup().find("dlclose").orElseThrow();
        dlsymPtr = Linker.nativeLinker().defaultLookup().find("dlsym").orElseThrow();
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
            case SINT32 -> JAVA_INT;
            case SINT64 -> JAVA_LONG;
            case FLOAT -> ValueLayout.JAVA_FLOAT;
            case DOUBLE -> ValueLayout.JAVA_DOUBLE;
            case RAW_POINTER -> JAVA_LONG;
        };
    }
}
