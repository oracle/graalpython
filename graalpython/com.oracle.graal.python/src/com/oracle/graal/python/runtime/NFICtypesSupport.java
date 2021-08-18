/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleLogger;

/*-
 * Generated using:
 * scripts/nfi_gen.py -name Ctypes -cpath graalpython/com.oracle.graal.python.cext/ctypes/ctypes_helper.c -lib libctypes
 */
public class NFICtypesSupport {

    private static final TruffleLogger LOGGER = PythonLanguage.getLogger(NFICtypesSupport.class);

    enum CtypesNativeFunctions implements NativeLibrary.NativeFunction {

        /*-
          nfi_function: name('malloc')
          void* ctypesMalloc(size_t size)
        */
        ctypesMalloc("(UINT64): POINTER"),

        /*-
          nfi_function: name('free')
          void ctypesFree(void *ptr)
        */
        ctypesFree("(POINTER): VOID"),

        /*-
          nfi_function: name('gcReleaseHelper') release(true)
          void CtypesGCHelper(void *ptr)
        */
        CtypesGCHelper("(POINTER): VOID"),

        /*-
          nfi_function: name('memcpy')
          void ctypesMemcpy(void *src, Byte *dest, size_t size, int free_src)
        */
        ctypesMemcpy("(POINTER, [UINT8], UINT64, SINT32): VOID"),

        /*-
          nfi_function: name('toNative')
          void* ctypesToNative(Byte *src, size_t size)
        */
        ctypesToNative("([UINT8], UINT64): POINTER");

        private final String signature;

        CtypesNativeFunctions(String signature) {
            this.signature = signature;
        }

        @Override
        public String signature() {
            return signature;
        }
    }

    private static final String SUPPORTING_NATIVE_LIB_NAME = "libctypes";

    private final PythonContext pythonContext;
    private final NativeLibrary.TypedNativeLibrary<CtypesNativeFunctions> typedNativeLib;

    @CompilerDirectives.CompilationFinal private boolean available;

    private NFICtypesSupport(PythonContext context, NativeLibrary.NFIBackend backend, String noNativeAccessHelp) {
        if (context.isNativeAccessAllowed()) {
            this.pythonContext = context;
            this.typedNativeLib = NativeLibrary.create(SUPPORTING_NATIVE_LIB_NAME, CtypesNativeFunctions.values(),
                            backend, noNativeAccessHelp, true);
            this.available = true;
        } else {
            this.pythonContext = null;
            this.typedNativeLib = null;
            this.available = false;
        }
    }

    public static NFICtypesSupport createNative(PythonContext context, String noNativeAccessHelp) {
        return new NFICtypesSupport(context, NativeLibrary.NFIBackend.NATIVE, noNativeAccessHelp);
    }

    public static NFICtypesSupport createLLVM(PythonContext context, String noNativeAccessHelp) {
        return new NFICtypesSupport(context, NativeLibrary.NFIBackend.LLVM, noNativeAccessHelp);
    }

    static class PointerReleaseCallback implements AsyncHandler.AsyncAction {
        private final Pointer pointer;

        public PointerReleaseCallback(Pointer pointer) {
            this.pointer = pointer;
        }

        @Override
        public void execute(PythonContext context) {
            synchronized (pointer) {
                if (pointer.isReleased()) {
                    return;
                }
                try {
                    pointer.doRelease();
                    pointer.markReleased();
                    LOGGER.finest("NFICtypesSupport pointer has been freed");
                } catch (Exception e) {
                    LOGGER.severe("Error while trying to free NFICtypesSupport pointer: " + e.getMessage());
                }
            }
        }
    }

    public static class Pointer extends AsyncHandler.SharedFinalizer.FinalizableReference {

        private final NFICtypesSupport lib;

        public Pointer(Object referent, Object ptr, NFICtypesSupport lib) {
            super(referent, ptr, lib.pythonContext.getSharedFinalizer());
            this.lib = lib;
        }

        protected void doRelease() {
            lib.gcReleaseHelper(getReference());
        }

        @Override
        public AsyncHandler.AsyncAction release() {
            if (!isReleased()) {
                return new PointerReleaseCallback(this);
            }
            return null;
        }
    }

    public void notAvailable() {
        if (available) {
            CompilerAsserts.neverPartOfCompilation("Checking NFICtypesSupport availability should only be done during initialization.");
            available = false;
        }
    }

    public boolean isAvailable() {
        return available;
    }

    public PythonContext getContext() {
        return pythonContext;
    }

    /**
     *
     * @param ptr void *ptr
     *
     */
    public Object gcReleaseHelper(Object ptr) {
        return typedNativeLib.callUncached(pythonContext, CtypesNativeFunctions.CtypesGCHelper, ptr);
    }

    /**
     * 
     * @param size size_t size
     * @return void*
     */
    public Object malloc(long size,
                    NativeLibrary.InvokeNativeFunction invokeNode) {
        return invokeNode.call(typedNativeLib, CtypesNativeFunctions.ctypesMalloc, size);
    }

    /**
     * 
     * @param ptr void *ptr
     *
     */
    public void free(Object ptr,
                    NativeLibrary.InvokeNativeFunction invokeNode) {
        invokeNode.call(typedNativeLib, CtypesNativeFunctions.ctypesFree, ptr);
    }

    /**
     * 
     * @param src void *src
     * @param dest Byte *dest
     * @param size size_t size
     * @param free_src int free_src
     *
     */
    public void memcpy(Object src, Object dest, long size, int free_src,
                    NativeLibrary.InvokeNativeFunction invokeNode) {
        invokeNode.call(typedNativeLib, CtypesNativeFunctions.ctypesMemcpy, src, dest, size, free_src);
    }

    /**
     * 
     * @param src Byte *src
     * @param size size_t size
     * @return void*
     */
    public Object toNative(Object src, long size,
                    NativeLibrary.InvokeNativeFunction invokeNode) {
        return invokeNode.call(typedNativeLib, CtypesNativeFunctions.ctypesToNative, src, size);
    }

}
