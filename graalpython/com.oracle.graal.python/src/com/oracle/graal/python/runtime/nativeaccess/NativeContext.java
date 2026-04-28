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
import java.util.concurrent.ConcurrentLinkedQueue;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.PythonOS;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

public final class NativeContext {
    public static final String UNAVAILABLE = "JEP 454 is not included on this JDK, this prevents loading native extensions modules.";

    private static final int LOAD_LIBRARY_SEARCH_DLL_LOAD_DIR = 0x00000100;
    private static final int LOAD_LIBRARY_SEARCH_APPLICATION_DIR = 0x00000200;
    private static final int LOAD_LIBRARY_SEARCH_USER_DIRS = 0x00000400;
    private static final int LOAD_LIBRARY_SEARCH_SYSTEM32 = 0x00000800;
    private static final int LOAD_LIBRARY_SEARCH_DEFAULT_DIRS = 0x00001000;
    private static final int WINDOWS_LOAD_LIBRARY_SEARCH_MASK = LOAD_LIBRARY_SEARCH_DLL_LOAD_DIR | LOAD_LIBRARY_SEARCH_APPLICATION_DIR | LOAD_LIBRARY_SEARCH_USER_DIRS |
                    LOAD_LIBRARY_SEARCH_SYSTEM32 | LOAD_LIBRARY_SEARCH_DEFAULT_DIRS;
    private static final int WINDOWS_DEFAULT_LOAD_LIBRARY_FLAGS = LOAD_LIBRARY_SEARCH_DEFAULT_DIRS | LOAD_LIBRARY_SEARCH_DLL_LOAD_DIR;
    private static final int FORMAT_MESSAGE_IGNORE_INSERTS = 0x00000200;
    private static final int FORMAT_MESSAGE_FROM_SYSTEM = 0x00001000;
    private static final int FORMAT_MESSAGE_BUFFER_CHARS = 2048;

    private final ConcurrentLinkedQueue<NfiLibrary> libraries = new ConcurrentLinkedQueue<>();
    final Object arena;

    @TruffleBoundary
    NativeContext() {
        arena = NfiSupport.createArena();
    }

    public void close() {
        CompilerAsserts.neverPartOfCompilation();
        for (NfiLibrary library : libraries) {
            int result;
            try {
                result = isWindows() ? (int) FREE_LIBRARY.invokeExact(freeLibraryPtr, library.ptr) : (int) DLCLOSE.invokeExact(dlclosePtr, library.ptr);
            } catch (Throwable e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
            if (result != 0) {
                // TODO(NFI2) log error
            }
        }
        NfiSupport.closeArena(arena);
    }

    public NfiLibrary loadLibrary(String name, int flags) throws NfiLoadException {
        CompilerAsserts.neverPartOfCompilation();

        // This needs to be done first and may fail if the executing JDK does not support FFM API.
        ensureLoader();

        long lib;
        long nativeName = isWindows() ? NativeMemory.javaStringToNativeUtf16(name) : NativeMemory.javaStringToNativeUtf8(name);
        try {
            if (isWindows()) {
                int callFlags = sanitizeWindowsLoadLibraryFlags(flags) | WINDOWS_DEFAULT_LOAD_LIBRARY_FLAGS;
                lib = (long) LOAD_LIBRARY_EX.invokeExact(loadLibraryExPtr, nativeName, 0L, callFlags);
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
            throw createLoadLibraryException();
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
            return isWindows() ? (long) GET_PROC_ADDRESS.invokeExact(getProcAddressPtr, library, nativeName) : (long) DLSYM.invokeExact(dlsymPtr, library, nativeName);
        } catch (Throwable e) {
            throw CompilerDirectives.shouldNotReachHere(e);
        } finally {
            NativeMemory.free(nativeName);
        }
    }

    private static boolean isWindows() {
        return PythonLanguage.getPythonOS() == PythonOS.PLATFORM_WIN32;
    }

    // TODO(NFI2) platform-specific values for RTLD_* constants
    private static final int RTLD_LAZY = 1;
    private static final int RTLD_NOW = 2;

    private static final MethodHandle DLOPEN = NfiSupport.createDowncallHandle(NfiType.SINT64, NfiType.RAW_POINTER, NfiType.SINT32);
    private static final MethodHandle DLCLOSE = NfiSupport.createDowncallHandle(NfiType.SINT32, NfiType.SINT64);
    private static final MethodHandle DLSYM = NfiSupport.createDowncallHandle(NfiType.SINT64, NfiType.SINT64, NfiType.RAW_POINTER);
    private static final MethodHandle LOAD_LIBRARY_EX = NfiSupport.createDowncallHandle(NfiType.SINT64, NfiType.RAW_POINTER, NfiType.RAW_POINTER, NfiType.SINT32);
    private static final MethodHandle FREE_LIBRARY = NfiSupport.createDowncallHandle(NfiType.SINT32, NfiType.SINT64);
    private static final MethodHandle GET_PROC_ADDRESS = NfiSupport.createDowncallHandle(NfiType.SINT64, NfiType.SINT64, NfiType.RAW_POINTER);
    private static final MethodHandle GET_LAST_ERROR = NfiSupport.createDowncallHandle(NfiType.SINT32);
    private static final MethodHandle FORMAT_MESSAGE = NfiSupport.createDowncallHandle(NfiType.SINT32, NfiType.SINT32, NfiType.RAW_POINTER, NfiType.SINT32, NfiType.SINT32,
                    NfiType.RAW_POINTER, NfiType.SINT32, NfiType.RAW_POINTER);
    private static final MethodHandle DLERROR = NfiSupport.createDowncallHandle(NfiType.SINT64);

    private static long dlopenPtr;
    private static long dlclosePtr;
    private static long dlsymPtr;
    private static long dlerrorPtr;
    private static long loadLibraryExPtr;
    private static long freeLibraryPtr;
    private static long getProcAddressPtr;
    private static long getLastErrorPtr;
    private static long formatMessagePtr;
    private static Object windowsLookupArena;
    private static NativeLibraryLookup windowsLookup;

    private static void ensureLoader() throws UnsupportedOperationException {
        if (isWindows()) {
            if (loadLibraryExPtr != 0) {
                assert freeLibraryPtr != 0;
                assert getProcAddressPtr != 0;
                assert getLastErrorPtr != 0;
                assert formatMessagePtr != 0;
                return;
            }
            if (windowsLookup == null) {
                windowsLookupArena = NfiSupport.createArena();
                windowsLookup = NfiSupport.libraryLookup("kernel32", windowsLookupArena);
            }
            loadLibraryExPtr = NfiSupport.lookupSymbol(windowsLookup, "LoadLibraryExW");
            freeLibraryPtr = NfiSupport.lookupSymbol(windowsLookup, "FreeLibrary");
            getProcAddressPtr = NfiSupport.lookupSymbol(windowsLookup, "GetProcAddress");
            getLastErrorPtr = NfiSupport.lookupSymbol(windowsLookup, "GetLastError");
            formatMessagePtr = NfiSupport.lookupSymbol(windowsLookup, "FormatMessageW");
            return;
        }
        dlopenPtr = NfiSupport.lookupDefault("dlopen");
        dlclosePtr = NfiSupport.lookupDefault("dlclose");
        dlsymPtr = NfiSupport.lookupDefault("dlsym");
        dlerrorPtr = NfiSupport.lookupDefault("dlerror");
    }

    private static int sanitizeWindowsLoadLibraryFlags(int flags) {
        return flags & WINDOWS_LOAD_LIBRARY_SEARCH_MASK;
    }

    @TruffleBoundary
    private static NfiLoadException createLoadLibraryException() {
        if (isWindows()) {
            int errorCode = getLastError();
            String detail = formatWindowsError(errorCode);
            if (detail == null || detail.isBlank()) {
                return new NfiLoadException("Windows error " + errorCode);
            }
            return new NfiLoadException("Windows error " + errorCode + ": " + detail);
        }
        String detail = getDlError();
        if (detail == null || detail.isBlank()) {
            return new NfiLoadException("dlopen failed");
        }
        return new NfiLoadException(detail);
    }

    private static int getLastError() {
        try {
            return (int) GET_LAST_ERROR.invokeExact(getLastErrorPtr);
        } catch (Throwable e) {
            throw CompilerDirectives.shouldNotReachHere(e);
        }
    }

    private static String getDlError() {
        try {
            long ptr = (long) DLERROR.invokeExact(dlerrorPtr);
            if (ptr == 0) {
                return null;
            }
            return NativeMemory.zeroTerminatedUtf8ToJavaString(ptr);
        } catch (Throwable e) {
            throw CompilerDirectives.shouldNotReachHere(e);
        }
    }

    private static String formatWindowsError(int errorCode) {
        long buffer = NativeMemory.callocShortArray(FORMAT_MESSAGE_BUFFER_CHARS);
        try {
            int result;
            try {
                result = (int) FORMAT_MESSAGE.invokeExact(formatMessagePtr, FORMAT_MESSAGE_FROM_SYSTEM | FORMAT_MESSAGE_IGNORE_INSERTS, 0L, errorCode, 0, buffer,
                                FORMAT_MESSAGE_BUFFER_CHARS, 0L);
            } catch (Throwable e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
            if (result == 0) {
                return null;
            }
            return NativeMemory.zeroTerminatedUtf16ToJavaString(buffer).stripTrailing();
        } finally {
            NativeMemory.free(buffer);
        }
    }
}
