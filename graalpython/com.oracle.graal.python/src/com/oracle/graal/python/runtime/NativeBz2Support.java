/*
 * Copyright (c) 2020, 2026, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.annotations.NativeSimpleType.DOUBLE;
import static com.oracle.graal.python.annotations.NativeSimpleType.POINTER;
import static com.oracle.graal.python.annotations.NativeSimpleType.SINT32;
import static com.oracle.graal.python.annotations.NativeSimpleType.SINT64;
import static com.oracle.graal.python.annotations.NativeSimpleType.VOID;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.DowncallSignature;
import com.oracle.graal.python.runtime.nativeaccess.NativeLibrary;
import com.oracle.graal.python.runtime.nativeaccess.NativeMemory;
import com.oracle.truffle.api.ThreadLocalAction.Access;
import com.oracle.truffle.api.TruffleLogger;

public class NativeBz2Support extends NativeCompressionSupport {

    private static final TruffleLogger LOGGER = PythonLanguage.getLogger(NativeBz2Support.class);
    private static final String SUPPORTING_NATIVE_LIB_NAME = "bz2support";

    abstract static class Bz2NativeFunctions {
        @DowncallSignature(returnType = POINTER)
        abstract long bz_create_bzst_stream();

        @DowncallSignature(returnType = DOUBLE, argumentTypes = {POINTER})
        abstract double bz_get_timeElapsed(long bzst);

        @DowncallSignature(returnType = VOID, argumentTypes = {POINTER})
        abstract void bz_free_stream(long bzst);

        @DowncallSignature(returnType = VOID, argumentTypes = {POINTER})
        abstract void bz_gc_helper(long bzst);

        @DowncallSignature(returnType = SINT64, argumentTypes = {POINTER})
        abstract long bz_get_next_in_index(long bzst);

        @DowncallSignature(returnType = SINT64, argumentTypes = {POINTER})
        abstract long bz_get_bzs_avail_in_real(long bzst);

        @DowncallSignature(returnType = VOID, argumentTypes = {POINTER, SINT64})
        abstract void bz_set_bzs_avail_in_real(long bzst, long v);

        @DowncallSignature(returnType = SINT64, argumentTypes = {POINTER})
        abstract long bz_get_output_buffer_size(long bzst);

        @DowncallSignature(returnType = VOID, argumentTypes = {POINTER, POINTER})
        abstract void bz_get_output_buffer(long bzst, long dest);

        @DowncallSignature(returnType = SINT32, argumentTypes = {POINTER, SINT32})
        abstract int bz_compressor_init(long bzst, int compresslevel);

        @DowncallSignature(returnType = SINT32, argumentTypes = {POINTER, POINTER, SINT64, SINT32, SINT64})
        abstract int bz_compress(long bzst, long data, long len, int action, long bufsize);

        @DowncallSignature(returnType = SINT32, argumentTypes = {POINTER})
        abstract int bz_decompress_init(long bzst);

        @DowncallSignature(returnType = SINT32, argumentTypes = {POINTER, POINTER, SINT64, SINT64, SINT64, SINT64})
        abstract int bz_decompress(long bzst, long inputBuffer, long offset, long maxLength, long bufsize, long bzsAvailInReal);

        static NativeLibrary loadNativeLibrary(PythonContext context) {
            return NativeCompressionSupport.loadNativeLibrary(context, SUPPORTING_NATIVE_LIB_NAME);
        }
    }

    private final Bz2NativeFunctions nativeFunctions;

    private NativeBz2Support(PythonContext context) {
        super(context);
        this.nativeFunctions = isAvailable() ? new Bz2NativeFunctionsGen(context) : null;
    }

    public static NativeBz2Support createNative(PythonContext context, String noNativeAccessHelp) {
        return new NativeBz2Support(context);
    }

    static class PointerReleaseCallback implements AsyncHandler.AsyncAction {
        private final Pointer pointer;

        public PointerReleaseCallback(Pointer pointer) {
            this.pointer = pointer;
        }

        @Override
        public void execute(PythonContext context, Access access) {
            if (!pointer.markReleased()) {
                assert pointer.isReleased();
                return;
            }
            try {
                pointer.doRelease();
                LOGGER.finest("NativeBz2Support pointer has been freed");
            } catch (Exception e) {
                LOGGER.severe("Error while trying to free NativeBz2Support pointer: " + e.getMessage());
            }
        }
    }

    public static class Pointer extends AsyncHandler.SharedFinalizer.FinalizableReference {

        private final NativeBz2Support lib;
        private final long pointer;

        public Pointer(Object referent, long pointer, NativeBz2Support lib) {
            super(referent, lib.pythonContext.getSharedFinalizer());
            this.lib = lib;
            this.pointer = pointer;
        }

        public long getPointer() {
            return pointer;
        }

        protected void doRelease() {
            lib.gcReleaseHelper(pointer);
        }

        @Override
        public AsyncHandler.AsyncAction release() {
            if (!isReleased()) {
                return new PointerReleaseCallback(this);
            }
            return null;
        }
    }

    public Object getTimeElapsed(long zst) {
        return nativeFunctions.bz_get_timeElapsed(zst);
    }

    public Object gcReleaseHelper(long bzst) {
        nativeFunctions.bz_gc_helper(bzst);
        return null;
    }

    public long createStream() {
        return nativeFunctions.bz_create_bzst_stream();
    }

    public void deallocateStream(long bzst) {
        nativeFunctions.bz_free_stream(bzst);
    }

    public long getNextInIndex(long bzst) {
        return nativeFunctions.bz_get_next_in_index(bzst);
    }

    public long getBzsAvailInReal(long bzst) {
        return nativeFunctions.bz_get_bzs_avail_in_real(bzst);
    }

    public void setBzsAvailInReal(long bzst, long v) {
        nativeFunctions.bz_set_bzs_avail_in_real(bzst, v);
    }

    public long getOutputBufferSize(long bzst) {
        return nativeFunctions.bz_get_output_buffer_size(bzst);
    }

    public void getOutputBuffer(long bzst, byte[] dest) {
        if (dest.length == 0) {
            return;
        }
        long nativeDest = NativeMemory.mallocByteArray(dest.length);
        try {
            nativeFunctions.bz_get_output_buffer(bzst, nativeDest);
            NativeMemory.readByteArrayElements(nativeDest, 0, dest, 0, dest.length);
        } finally {
            NativeMemory.free(nativeDest);
        }
    }

    public int compressInit(long bzst, int compresslevel) {
        return nativeFunctions.bz_compressor_init(bzst, compresslevel);
    }

    public int compress(long bzst, byte[] data, long len, int action, long bufsize) {
        long nativeData = copyToNativeByteArray(data, (int) len);
        try {
            return nativeFunctions.bz_compress(bzst, nativeData, len, action, bufsize);
        } finally {
            if (nativeData != NativeMemory.NULLPTR) {
                NativeMemory.free(nativeData);
            }
        }
    }

    public int decompressInit(long bzst) {
        return nativeFunctions.bz_decompress_init(bzst);
    }

    public int decompress(long bzst, byte[] inputBuffer, long offset, long maxLength, long bufsize, long bzsAvailInReal) {
        long nativeInputBuffer = copyToNativeByteArray(inputBuffer);
        try {
            return nativeFunctions.bz_decompress(bzst, nativeInputBuffer, offset, maxLength, bufsize, bzsAvailInReal);
        } finally {
            if (nativeInputBuffer != NativeMemory.NULLPTR) {
                NativeMemory.free(nativeInputBuffer);
            }
        }
    }
}
