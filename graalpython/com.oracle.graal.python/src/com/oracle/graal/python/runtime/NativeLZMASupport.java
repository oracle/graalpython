/*
 * Copyright (c) 2021, 2026, Oracle and/or its affiliates. All rights reserved.
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

public final class NativeLZMASupport extends NativeCompressionSupport {

    private static final TruffleLogger LOGGER = PythonLanguage.getLogger(NativeLZMASupport.class);
    private static final String SUPPORTING_NATIVE_LIB_NAME = "lzmasupport";

    public static final int FORMAT_AUTO_INDEX = 0;
    public static final int FORMAT_XZ_INDEX = 1;
    public static final int FORMAT_ALONE_INDEX = 2;
    public static final int FORMAT_RAW_INDEX = 3;
    public static final int CHECK_NONE_INDEX = 0;
    public static final int CHECK_CRC32_INDEX = 1;
    public static final int CHECK_CRC64_INDEX = 2;
    public static final int CHECK_SHA256_INDEX = 3;
    public static final int CHECK_ID_MAX_INDEX = 4;
    public static final int CHECK_UNKNOWN_INDEX = 5;
    public static final int FILTER_LZMA1_INDEX = 0;
    public static final int FILTER_LZMA2_INDEX = 1;
    public static final int FILTER_DELTA_INDEX = 2;
    public static final int FILTER_X86_INDEX = 3;
    public static final int FILTER_POWERPC_INDEX = 4;
    public static final int FILTER_IA64_INDEX = 5;
    public static final int FILTER_ARM_INDEX = 6;
    public static final int FILTER_ARMTHUMB_INDEX = 7;
    public static final int FILTER_SPARC_INDEX = 8;
    public static final int MF_HC3_INDEX = 0;
    public static final int MF_HC4_INDEX = 1;
    public static final int MF_BT2_INDEX = 2;
    public static final int MF_BT3_INDEX = 3;
    public static final int MF_BT4_INDEX = 4;
    public static final int MODE_FAST_INDEX = 0;
    public static final int MODE_NORMAL_INDEX = 1;
    public static final int PRESET_DEFAULT_INDEX = 0;
    public static final int PRESET_EXTREME_INDEX = 1;
    public static final int ID_INDEX = 0;
    public static final int PRESET_INDEX = 1;
    public static final int DICT_SIZE_INDEX = 2;
    public static final int LC_INDEX = 3;
    public static final int LP_INDEX = 4;
    public static final int PB_INDEX = 5;
    public static final int MODE_INDEX = 6;
    public static final int NICE_LEN_INDEX = 7;
    public static final int MF_INDEX = 8;
    public static final int DEPTH_INDEX = 9;
    public static final int DIST_INDEX = 1;
    public static final int START_OFFSET_INDEX = 1;
    public static final int MAX_OPTS_INDEX = 10;
    public static final int LZMA_ID_ERROR = 98;
    public static final int LZMA_PRESET_ERROR = 99;

    abstract static class LZMANativeFunctions {
        @DowncallSignature(returnType = VOID, argumentTypes = {POINTER, POINTER, POINTER, POINTER, POINTER, POINTER})
        abstract void get_macros(long formats, long checks, long filters, long mfs, long modes, long preset);

        @DowncallSignature(returnType = POINTER)
        abstract long lzma_create_lzmast_stream();

        @DowncallSignature(returnType = DOUBLE, argumentTypes = {POINTER})
        abstract double lzma_get_timeElapsed(long lzmast);

        @DowncallSignature(returnType = VOID, argumentTypes = {POINTER})
        abstract void lzma_free_stream(long lzmast);

        @DowncallSignature(returnType = VOID, argumentTypes = {POINTER})
        abstract void lzma_gc_helper(long lzmast);

        @DowncallSignature(returnType = SINT64, argumentTypes = {POINTER})
        abstract long lzma_get_next_in_index(long lzmast);

        @DowncallSignature(returnType = SINT64, argumentTypes = {POINTER})
        abstract long lzma_get_lzs_avail_in(long lzmast);

        @DowncallSignature(returnType = SINT64, argumentTypes = {POINTER})
        abstract long lzma_get_lzs_avail_out(long lzmast);

        @DowncallSignature(returnType = SINT32, argumentTypes = {POINTER})
        abstract int lzma_lzma_get_check(long lzmast);

        @DowncallSignature(returnType = VOID, argumentTypes = {POINTER, SINT64})
        abstract void lzma_set_lzs_avail_in(long lzmast, long v);

        @DowncallSignature(returnType = SINT64, argumentTypes = {POINTER})
        abstract long lzma_get_output_buffer_size(long lzmast);

        @DowncallSignature(returnType = VOID, argumentTypes = {POINTER, POINTER})
        abstract void lzma_get_output_buffer(long lzmast, long dest);

        @DowncallSignature(returnType = SINT32, argumentTypes = {SINT32})
        abstract int lzma_lzma_check_is_supported(int checkId);

        @DowncallSignature(returnType = SINT32, argumentTypes = {POINTER, SINT32, POINTER})
        abstract int lzma_set_filter_spec_lzma(long lzmast, int fidx, long opts);

        @DowncallSignature(returnType = SINT32, argumentTypes = {POINTER, SINT32, POINTER})
        abstract int lzma_set_filter_spec_delta(long lzmast, int fidx, long opts);

        @DowncallSignature(returnType = SINT32, argumentTypes = {POINTER, SINT32, POINTER})
        abstract int lzma_set_filter_spec_bcj(long lzmast, int fidx, long opts);

        @DowncallSignature(returnType = SINT32, argumentTypes = {POINTER, POINTER})
        abstract int lzma_encode_filter_spec(long lzmast, long opts);

        @DowncallSignature(returnType = SINT32, argumentTypes = {SINT64, POINTER, SINT32, POINTER})
        abstract int lzma_decode_filter_spec(long filterId, long encodedProps, int len, long opts);

        @DowncallSignature(returnType = SINT32, argumentTypes = {POINTER, SINT32, SINT32})
        abstract int lzma_lzma_easy_encoder(long lzmast, int preset, int check);

        @DowncallSignature(returnType = SINT32, argumentTypes = {POINTER, SINT32})
        abstract int lzma_lzma_stream_encoder(long lzmast, int check);

        @DowncallSignature(returnType = SINT32, argumentTypes = {POINTER, SINT32})
        abstract int lzma_lzma_alone_encoder_preset(long lzmast, int preset);

        @DowncallSignature(returnType = SINT32, argumentTypes = {POINTER})
        abstract int lzma_lzma_alone_encoder(long lzmast);

        @DowncallSignature(returnType = SINT32, argumentTypes = {POINTER})
        abstract int lzma_lzma_raw_encoder(long lzmast);

        @DowncallSignature(returnType = SINT32, argumentTypes = {POINTER, POINTER, SINT64, SINT32, SINT64})
        abstract int lzma_compress(long lzmast, long data, long len, int iaction, long bufsize);

        @DowncallSignature(returnType = SINT32, argumentTypes = {POINTER})
        abstract int lzma_lzma_raw_decoder(long lzmast);

        @DowncallSignature(returnType = SINT32, argumentTypes = {POINTER, SINT64, SINT32})
        abstract int lzma_lzma_auto_decoder(long lzmast, long memlimit, int decoderFlags);

        @DowncallSignature(returnType = SINT32, argumentTypes = {POINTER, SINT64, SINT32})
        abstract int lzma_lzma_stream_decoder(long lzmast, long memlimit, int decoderFlags);

        @DowncallSignature(returnType = SINT32, argumentTypes = {POINTER, SINT64})
        abstract int lzma_lzma_alone_decoder(long lzmast, long memlimit);

        @DowncallSignature(returnType = SINT32, argumentTypes = {POINTER, POINTER, SINT64, SINT64, SINT64, SINT64})
        abstract int lzma_decompress(long lzmast, long inputBuffer, long offset, long maxLength, long bufsize, long lzsAvailIn);

        static NativeLibrary loadNativeLibrary(PythonContext context) {
            return NativeCompressionSupport.loadNativeLibrary(context, SUPPORTING_NATIVE_LIB_NAME);
        }
    }

    private final LZMANativeFunctions nativeFunctions;

    private NativeLZMASupport(PythonContext context) {
        super(context);
        this.nativeFunctions = isAvailable() ? new LZMANativeFunctionsGen(context) : null;
    }

    public static NativeLZMASupport createNative(PythonContext context, String noNativeAccessHelp) {
        return new NativeLZMASupport(context);
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
                LOGGER.finest("NativeLZMASupport pointer has been freed");
            } catch (Exception e) {
                LOGGER.severe("Error while trying to free NativeLZMASupport pointer: " + e.getMessage());
            }
        }
    }

    public static class Pointer extends AsyncHandler.SharedFinalizer.FinalizableReference {

        private final NativeLZMASupport lib;
        private final long pointer;

        public Pointer(Object referent, long pointer, NativeLZMASupport lib) {
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

    public void getMacros(int[] formats, int[] checks, long[] filters, int[] mfs, int[] modes, long[] preset) {
        long nativeFormats = copyToNativeIntArray(formats);
        long nativeChecks = copyToNativeIntArray(checks);
        long nativeFilters = copyToNativeLongArray(filters);
        long nativeMfs = copyToNativeIntArray(mfs);
        long nativeModes = copyToNativeIntArray(modes);
        long nativePreset = copyToNativeLongArray(preset);
        try {
            nativeFunctions.get_macros(nativeFormats, nativeChecks, nativeFilters, nativeMfs, nativeModes, nativePreset);
            NativeMemory.readIntArrayElements(nativeFormats, 0, formats, 0, formats.length);
            NativeMemory.readIntArrayElements(nativeChecks, 0, checks, 0, checks.length);
            NativeMemory.readLongArrayElements(nativeFilters, 0, filters, 0, filters.length);
            NativeMemory.readIntArrayElements(nativeMfs, 0, mfs, 0, mfs.length);
            NativeMemory.readIntArrayElements(nativeModes, 0, modes, 0, modes.length);
            NativeMemory.readLongArrayElements(nativePreset, 0, preset, 0, preset.length);
        } finally {
            NativeMemory.free(nativeFormats);
            NativeMemory.free(nativeChecks);
            NativeMemory.free(nativeFilters);
            NativeMemory.free(nativeMfs);
            NativeMemory.free(nativeModes);
            NativeMemory.free(nativePreset);
        }
    }

    public Object getTimeElapsed(long lzmast) {
        return nativeFunctions.lzma_get_timeElapsed(lzmast);
    }

    public Object gcReleaseHelper(long lzmast) {
        nativeFunctions.lzma_gc_helper(lzmast);
        return null;
    }

    public long createStream() {
        return nativeFunctions.lzma_create_lzmast_stream();
    }

    public void deallocateStream(long lzmast) {
        nativeFunctions.lzma_free_stream(lzmast);
    }

    public long getNextInIndex(long lzmast) {
        return nativeFunctions.lzma_get_next_in_index(lzmast);
    }

    public long getLzsAvailIn(long lzmast) {
        return nativeFunctions.lzma_get_lzs_avail_in(lzmast);
    }

    public long getLzsAvailOut(long lzmast) {
        return nativeFunctions.lzma_get_lzs_avail_out(lzmast);
    }

    public int getLzsCheck(long lzmast) {
        return nativeFunctions.lzma_lzma_get_check(lzmast);
    }

    public void setLzsAvailIn(long lzmast, long v) {
        nativeFunctions.lzma_set_lzs_avail_in(lzmast, v);
    }

    public long getOutputBufferSize(long lzmast) {
        return nativeFunctions.lzma_get_output_buffer_size(lzmast);
    }

    public void getOutputBuffer(long lzmast, byte[] dest) {
        if (dest.length == 0) {
            return;
        }
        long nativeDest = NativeMemory.mallocByteArray(dest.length);
        try {
            nativeFunctions.lzma_get_output_buffer(lzmast, nativeDest);
            NativeMemory.readByteArrayElements(nativeDest, 0, dest, 0, dest.length);
        } finally {
            NativeMemory.free(nativeDest);
        }
    }

    public int checkIsSupported(int checkId) {
        return nativeFunctions.lzma_lzma_check_is_supported(checkId);
    }

    public int setFilterSpecLZMA(long lzmast, int fidx, long[] opts) {
        long nativeOpts = copyToNativeLongArray(opts);
        try {
            return nativeFunctions.lzma_set_filter_spec_lzma(lzmast, fidx, nativeOpts);
        } finally {
            NativeMemory.free(nativeOpts);
        }
    }

    public int setFilterSpecDelta(long lzmast, int fidx, long[] opts) {
        long nativeOpts = copyToNativeLongArray(opts);
        try {
            return nativeFunctions.lzma_set_filter_spec_delta(lzmast, fidx, nativeOpts);
        } finally {
            NativeMemory.free(nativeOpts);
        }
    }

    public int setFilterSpecBCJ(long lzmast, int fidx, long[] opts) {
        long nativeOpts = copyToNativeLongArray(opts);
        try {
            return nativeFunctions.lzma_set_filter_spec_bcj(lzmast, fidx, nativeOpts);
        } finally {
            NativeMemory.free(nativeOpts);
        }
    }

    public int encodeFilter(long lzmast, long[] opts) {
        long nativeOpts = copyToNativeLongArray(opts);
        try {
            return nativeFunctions.lzma_encode_filter_spec(lzmast, nativeOpts);
        } finally {
            NativeMemory.free(nativeOpts);
        }
    }

    public int decodeFilter(long filterId, byte[] encodedProps, int len, long[] opts) {
        long nativeEncodedProps = copyToNativeByteArray(encodedProps, len);
        long nativeOpts = copyToNativeLongArray(opts);
        try {
            int result = nativeFunctions.lzma_decode_filter_spec(filterId, nativeEncodedProps, len, nativeOpts);
            NativeMemory.readLongArrayElements(nativeOpts, 0, opts, 0, opts.length);
            return result;
        } finally {
            if (nativeEncodedProps != NativeMemory.NULLPTR) {
                NativeMemory.free(nativeEncodedProps);
            }
            NativeMemory.free(nativeOpts);
        }
    }

    public int lzmaEasyEncoder(long lzmast, long preset, int check) {
        return nativeFunctions.lzma_lzma_easy_encoder(lzmast, (int) preset, check);
    }

    public int lzmaStreamEncoder(long lzmast, int check) {
        return nativeFunctions.lzma_lzma_stream_encoder(lzmast, check);
    }

    public int lzmaAloneEncoderPreset(long lzmast, long preset) {
        return nativeFunctions.lzma_lzma_alone_encoder_preset(lzmast, (int) preset);
    }

    public int lzmaAloneEncoder(long lzmast) {
        return nativeFunctions.lzma_lzma_alone_encoder(lzmast);
    }

    public int lzmaRawEncoder(long lzmast) {
        return nativeFunctions.lzma_lzma_raw_encoder(lzmast);
    }

    public int compress(long lzmast, byte[] data, long len, int iaction, long bufsize) {
        long nativeData = copyToNativeByteArray(data, (int) len);
        try {
            return nativeFunctions.lzma_compress(lzmast, nativeData, len, iaction, bufsize);
        } finally {
            if (nativeData != NativeMemory.NULLPTR) {
                NativeMemory.free(nativeData);
            }
        }
    }

    public int lzmaRawDecoder(long lzmast) {
        return nativeFunctions.lzma_lzma_raw_decoder(lzmast);
    }

    public int lzmaAutoDecoder(long lzmast, long memlimit, long decoderFlags) {
        return nativeFunctions.lzma_lzma_auto_decoder(lzmast, memlimit, (int) decoderFlags);
    }

    public int lzmaStreamDecoder(long lzmast, long memlimit, long decoderFlags) {
        return nativeFunctions.lzma_lzma_stream_decoder(lzmast, memlimit, (int) decoderFlags);
    }

    public int lzmaAloneDecoder(long lzmast, long memlimit) {
        return nativeFunctions.lzma_lzma_alone_decoder(lzmast, memlimit);
    }

    public int decompress(long lzmast, byte[] inputBuffer, long offset, long maxLength, long bufsize, long lzsAvailIn) {
        long nativeInputBuffer = copyToNativeByteArray(inputBuffer);
        try {
            return nativeFunctions.lzma_decompress(lzmast, nativeInputBuffer, offset, maxLength, bufsize, lzsAvailIn);
        } finally {
            if (nativeInputBuffer != NativeMemory.NULLPTR) {
                NativeMemory.free(nativeInputBuffer);
            }
        }
    }
}
